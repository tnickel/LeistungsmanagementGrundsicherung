package de.allgemein.lmg.db;

import de.allgemein.lmg.calc.BerechnungsSnapshot;
import de.allgemein.lmg.model.SozialFall;
import de.allgemein.lmg.service.*;
import de.allgemein.tx.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.math.BigDecimal;
import java.sql.*;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Neue fachliche Pfade aus der kritischen Gegenprüfung, ausschließlich eigene UUID-Daten. */
@EnabledIfEnvironmentVariable(named = "TX_URL", matches = ".+")
class GrokFachRegressionDbTest {
    private SimpleConnectionPool pool;
    private SozialLeistungsService service;
    private long bgId;
    private SozialFall fall;
    private static final BigDecimal ZERO = new BigDecimal("0.00");

    @BeforeEach
    void create() throws Exception {
        pool = new SimpleConnectionPool(System.getenv("TX_URL"), System.getenv("TX_USER"),
                System.getenv("TX_PASS"), 4);
        service = new SozialLeistungsService(new TxManager(pool));
        bgId = service.legeBedarfsgemeinschaftAn("BG-GROK-" + UUID.randomUUID(), "Synthetischer Fall",
                new BigDecimal("10000.00"), null, "DE89370400440532013000", "COBADEFFXXX",
                "Testkonto", ZERO, "sb-grok").id();
        fall = service.legeSozialFallAn(bgId, person(0, false, ZERO, 0, 0, 0, null, 1), "sb-grok");
    }

    @AfterEach
    void clean() throws Exception {
        try (var connection = connection()) {
            connection.setAutoCommit(false);
            for (var table : List.of("JOURNAL", "BESCHEID", "ZAHLUNG", "SOZIAL_FALL")) {
                try (var statement = connection.prepareStatement("DELETE FROM " + table + " WHERE BG_ID=?")) {
                    statement.setLong(1, bgId); statement.executeUpdate();
                }
            }
            try (var statement = connection.prepareStatement("DELETE FROM BEDARFSGEMEINSCHAFT WHERE ID=?")) {
                statement.setLong(1, bgId); statement.executeUpdate();
            }
            connection.commit();
        } finally { if (pool != null) pool.close(); }
    }

    @Test
    void vorschauIstAnExplizitenBezugsmonatGebunden() {
        service.aktualisiereSozialFall(person(fall.id(), true, ZERO, 0, 0, 0, "2026-06", fall.version()),
                fall.version(), "sb-grok");
        var juni = service.ladeDetail(bgId, null, "2026-06");
        var juli = service.ladeDetail(bgId, null, "2026-07");
        assertEquals("2026-06", juni.berechnungsmonat());
        assertEquals(new BigDecimal("658.71"), juni.berechnung().zahlbetrag());
        assertEquals(new BigDecimal("563.00"), juli.berechnung().zahlbetrag());
        assertEquals(juni.berechnung().zahlbetrag(), payment("2026-06").zahlung().auszahlungsbetrag());
        assertThrows(FachlicheException.class, () -> service.ladeDetail(bgId, null, "2026-13"));
    }

    @Test
    void vorgangslisteKennzeichnetDenTatsaechlichBerechnetenMonatAuchBeiStellbarerUhr() {
        service.aktualisiereSozialFall(person(fall.id(), true, ZERO, 0, 0, 0, "2026-06", fall.version()),
                fall.version(), "sb-grok");
        var july = java.time.Clock.fixed(java.time.Instant.parse("2026-07-01T10:00:00Z"),
                java.time.ZoneId.of("Europe/Berlin"));
        var timed = new SozialLeistungsService(new TxManager(pool),
                de.allgemein.lmg.calc.Leistungsberechnung.KduParameter.AUS, null, july);
        var current = timed.listeBedarfsgemeinschaften().stream().filter(row -> row.id() == bgId).findFirst().orElseThrow();
        assertEquals("2026-07", current.berechnungsmonat());
        assertEquals(new BigDecimal("563.00"), current.zahlbetragVorschau());
        var june = timed.listeBedarfsgemeinschaften("2026-06").stream()
                .filter(row -> row.id() == bgId).findFirst().orElseThrow();
        assertEquals("2026-06", june.berechnungsmonat());
        assertEquals(new BigDecimal("658.71"), june.zahlbetragVorschau());
        assertEquals(timed.ladeDetail(bgId).berechnung().zahlbetrag(), current.zahlbetragVorschau());
        assertThrows(FachlicheException.class, () -> timed.listeBedarfsgemeinschaften("2026-13"));
    }

    @Test
    void deckendesVermoegenWirdNichtDurchAbgelehnteAnordnungFiktivVerbraucht() throws Exception {
        setAssets(new BigDecimal("10563.00"));
        BigDecimal journalBefore = scalar("SELECT COUNT(*) FROM JOURNAL WHERE BG_ID=?");
        for (var monat : List.of("2026-10", "2026-11")) {
            var error = assertThrows(FachlicheException.class, () -> payment(monat));
            assertEquals(FachlicheException.KEIN_ANSPRUCH, error.code());
        }
        assertEquals(0, scalar("SELECT COUNT(*) FROM ZAHLUNG WHERE BG_ID=?").intValue());
        assertEquals(journalBefore, scalar("SELECT COUNT(*) FROM JOURNAL WHERE BG_ID=?"));
        var bg = service.ladeDetail(bgId).bedarfsgemeinschaft();
        assertEquals(0, new BigDecimal("10563.00").compareTo(bg.vermoegen()));
        assertEquals(0, new BigDecimal("10000.00").compareTo(bg.budgetRest()));
        // Tatsächlich erfasster Verbrauch, keine Simulation und kein fehlgeschlagener POST.
        setAssets(new BigDecimal("10000.00"));
        assertEquals(new BigDecimal("563.00"), payment("2026-11").zahlung().auszahlungsbetrag());
    }

    @Test
    void aenderungsbescheidErhaeltAlteMonateUndErmoeglichtNeuenZahlbetrag() throws Exception {
        var alt = service.erteileBescheid(bgId, "2026-10", "2026-12", "sb-grok");
        payment("2026-10");
        service.aktualisiereSozialFall(person(fall.id(), false, new BigDecimal("200.00"),
                0, 0, 0, null, fall.version()), fall.version(), "sb-grok");
        assertThrows(FachlicheException.class, () -> payment("2026-11"));
        var neu = service.aendereBescheid(bgId, "2026-11", "2026-12", "sb-grok");
        assertEquals("2026-10", neu.bewilligungVon());
        assertEquals(new BigDecimal("563.00"), BescheidPaymentBinding.approvedAmount(neu, "2026-10"));
        assertEquals(new BigDecimal("363.00"), BescheidPaymentBinding.approvedAmount(neu, "2026-11"));
        assertEquals(new BigDecimal("363.00"), payment("2026-11").zahlung().auszahlungsbetrag());
        assertTrue(service.generiereBescheidHtml(bgId).contains("Änderungsbescheid"));
        assertTrue(service.generiereBescheidHtml(bgId).contains(alt.bescheidNr()));
        try (var c = connection(); var s = c.prepareStatement("SELECT STATUS,BERECHNUNG FROM BESCHEID WHERE ID=?")) {
            s.setLong(1, alt.id()); try (var rows = s.executeQuery()) {
                assertTrue(rows.next()); assertEquals("AUFGEHOBEN", rows.getString(1));
                assertEquals(alt.berechnung(), rows.getString(2), "Historischer Snapshot bleibt unverändert");
            }
        }
        assertEquals(BerechnungsSnapshot.lese(alt.berechnung()).monate().getFirst(),
                BerechnungsSnapshot.lese(neu.berechnung()).monate().getFirst());
    }

    @Test
    void fehlerImAenderungszeitraumLaesstAltenBescheidUnveraendert() throws Exception {
        var alt = service.erteileBescheid(bgId, "2026-10", "2026-12", "sb-grok");
        payment("2026-11");
        assertThrows(FachlicheException.class,
                () -> service.aendereBescheid(bgId, "2026-11", "2026-12", "sb-grok"));
        assertEquals(alt, service.letzterBescheid(bgId).orElseThrow());
        assertEquals(1, scalar("SELECT COUNT(*) FROM BESCHEID WHERE BG_ID=?").intValue());
    }

    @Test
    void nochNichtAngeordneterVermoegenseinsatzImAltpraefixWirdNichtDoppeltProjiziert() throws Exception {
        setAssets(new BigDecimal("10000.01"));
        var alt = service.erteileBescheid(bgId, "2026-10", "2026-12", "sb-grok");
        BigDecimal journalVorher = scalar("SELECT COUNT(*) FROM JOURNAL WHERE BG_ID=?");
        var error = assertThrows(FachlicheException.class,
                () -> service.aendereBescheid(bgId, "2026-11", "2026-12", "sb-grok"));
        assertTrue(error.getMessage().contains("Vermögenseinsatz"));
        assertEquals(alt, service.letzterBescheid(bgId).orElseThrow());
        assertEquals(1, scalar("SELECT COUNT(*) FROM BESCHEID WHERE BG_ID=?").intValue());
        assertEquals(journalVorher, scalar("SELECT COUNT(*) FROM JOURNAL WHERE BG_ID=?"));
        assertEquals(new BigDecimal("562.99"), payment("2026-10").zahlung().auszahlungsbetrag());
        var neu = service.aendereBescheid(bgId, "2026-11", "2026-12", "sb-grok");
        assertEquals(new BigDecimal("562.99"), BescheidPaymentBinding.approvedAmount(neu, "2026-10"));
        assertEquals(new BigDecimal("563.00"), BescheidPaymentBinding.approvedAmount(neu, "2026-11"));
        assertEquals(new BigDecimal("563.00"), payment("2026-11").zahlung().auszahlungsbetrag());
    }

    @Test
    @SuppressWarnings("unchecked")
    void altmonateOhneGespeicherteMerkmaleWerdenNichtAlsVollstaendigeEingabenErfunden() throws Exception {
        var alt = service.erteileBescheid(bgId, "2026-10", "2026-12", "sb-grok");
        var json = (java.util.Map<String, Object>) de.allgemein.lmg.web.Json.parse(alt.berechnung());
        for (var monat : (List<?>) json.get("monate")) {
            for (var person : (List<?>) ((java.util.Map<?, ?>) monat).get("faelle")) {
                ((java.util.Map<?, ?>) person).remove("eingaben");
            }
        }
        String legacy = de.allgemein.lmg.web.Json.write(json);
        try (var c = connection(); var statement = c.prepareStatement("UPDATE BESCHEID SET BERECHNUNG=? WHERE ID=?")) {
            statement.setString(1, legacy); statement.setLong(2, alt.id()); statement.executeUpdate();
        }
        var error = assertThrows(FachlicheException.class,
                () -> service.aendereBescheid(bgId, "2026-11", "2026-12", "sb-grok"));
        assertTrue(error.getMessage().contains("historische Angaben erfinden"));
        var erhalten = service.letzterBescheid(bgId).orElseThrow();
        assertEquals("ERTEILT", erhalten.status());
        assertEquals(legacy, erhalten.berechnung());
        assertEquals(1, scalar("SELECT COUNT(*) FROM BESCHEID WHERE BG_ID=?").intValue());
    }

    @Test
    void fehlgeschlagenerNeuerBescheidRolltDieBereitsAusgefuehrteAufhebungZurueck() throws Exception {
        var alt = service.erteileBescheid(bgId, "2026-10", "2026-12", "sb-grok");
        // Inkonsistenter Altbestand überlappt nur das beizubehaltende Präfix. Der Fehler
        // wird erst nach UPDATE AUFGEHOBEN erkannt und muss dieses UPDATE zurückrollen.
        try (var c = connection(); var statement = c.prepareStatement(
                "INSERT INTO BESCHEID (ID,BG_ID,BESCHEID_NR,BESCHEID_DATUM,BEWILLIGUNG_VON,"
                        + "BEWILLIGUNG_BIS,ZAHLBETRAG,STATUS,ERSTELLT_VON,ERSTELLT_AM) "
                        + "VALUES (SEQ_BESCHEID.NEXTVAL,?,'GROK-DUP-'||SEQ_BESCHEID.CURRVAL,SYSDATE,"
                        + "'2026-10','2026-10',563,'ERTEILT','sb-grok',SYSTIMESTAMP)")) {
            statement.setLong(1, bgId); statement.executeUpdate();
        }
        assertThrows(FachlicheException.class,
                () -> service.aendereBescheid(bgId, "2026-11", "2026-12", "sb-grok"));
        try (var c = connection(); var statement = c.prepareStatement("SELECT STATUS,BERECHNUNG FROM BESCHEID WHERE ID=?")) {
            statement.setLong(1, alt.id()); try (var rows = statement.executeQuery()) {
                assertTrue(rows.next()); assertEquals("ERTEILT", rows.getString(1));
                assertEquals(alt.berechnung(), rows.getString(2));
            }
        }
        assertEquals(2, scalar("SELECT COUNT(*) FROM BESCHEID WHERE BG_ID=?").intValue());
    }

    @Test
    void mehrfacheOderWiderspruechlicheKinderzuordnungWirdAbgelehnt() throws Exception {
        fall = service.aktualisiereSozialFall(person(fall.id(), false, ZERO, 1, 1, 4, null,
                fall.version()), fall.version(), "sb-grok");
        assertThrows(FachlicheException.class, () -> service.legeSozialFallAn(bgId,
                person(0, false, ZERO, 1, 1, 4, null, 1), "sb-grok"));
        assertThrows(FachlicheException.class, () -> service.aktualisiereSozialFall(
                person(fall.id(), false, ZERO, 2, 1, 4, null, fall.version()), fall.version(), "sb-grok"));
        assertEquals(1, scalar("SELECT COUNT(*) FROM SOZIAL_FALL WHERE BG_ID=?").intValue());
        assertEquals(new BigDecimal("270.24"), service.ladeDetail(bgId, null, "2026-10")
                .berechnung().faelle().getFirst().mehrbedarfAlleinerziehend());
    }

    @Test
    void replayOhneOptionaleOverridesBehaeltOriginalbankAuchNachStammdatenwechsel() {
        var auftrag = new Auszahlungsauftrag(bgId, "GROK-REPLAY-" + bgId, "2026-10", "sb-grok",
                false, null, null, null);
        var original = service.ordneAuszahlungAn(auftrag);
        var bg = original.bedarfsgemeinschaft();
        service.aktualisiereBedarfsgemeinschaft(bgId, true, null, "DE12500105170648489890",
                "INGDDEFFXXX", "Neukonto", bg.version());
        var erneut = service.ordneAuszahlungAn(auftrag);
        assertTrue(erneut.bereitsGebucht());
        assertEquals(original.zahlung(), erneut.zahlung());
        assertThrows(FachlicheException.class, () -> service.ordneAuszahlungAn(new Auszahlungsauftrag(
                bgId, auftrag.vorgangsNr(), "2026-10", "sb-grok", false,
                "DE12500105170648489890", null, null)));
    }

    private SozialFall person(long id, boolean schwanger, BigDecimal rente, int u7, int u16,
            int minor, String ende, long version) {
        return new SozialFall(id, bgId, 1, false, schwanger, u7, u16, minor, false,
                ZERO, ZERO, ZERO, rente, ZERO, ZERO, ZERO, ZERO, ZERO, ende, version);
    }
    private Auszahlungsergebnis payment(String monat) {
        return service.ordneAuszahlungAn(new Auszahlungsauftrag(bgId, "GROK-" + UUID.randomUUID(),
                monat, "sb-grok", false, null, null, null));
    }
    private void setAssets(BigDecimal amount) {
        var bg = service.ladeDetail(bgId).bedarfsgemeinschaft();
        service.aktualisiereBedarfsgemeinschaft(bgId, true, null, bg.iban(), bg.bic(),
                bg.kontoinhaber(), amount, bg.version(), "sb-grok");
    }
    private Connection connection() throws SQLException {
        return DriverManager.getConnection(System.getenv("TX_URL"), System.getenv("TX_USER"), System.getenv("TX_PASS"));
    }
    private BigDecimal scalar(String sql) throws SQLException {
        try (var connection = connection(); var statement = connection.prepareStatement(sql)) {
            statement.setLong(1, bgId); try (var rows = statement.executeQuery()) {
                assertTrue(rows.next()); return rows.getBigDecimal(1);
            }
        }
    }
}
