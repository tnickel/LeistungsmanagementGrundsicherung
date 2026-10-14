package de.allgemein.lmg.db;

import de.allgemein.lmg.calc.BerechnungsSnapshot;
import de.allgemein.lmg.calc.Leistungsberechnung;
import de.allgemein.lmg.saga.AntragsSagaProzess;
import de.allgemein.lmg.service.Auszahlungsauftrag;
import de.allgemein.lmg.service.FachlicheException;
import de.allgemein.lmg.service.SozialLeistungsService;
import de.allgemein.tx.ConnectionPool;
import de.allgemein.tx.TxManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Fachliche Review-Regression auf echter Oracle mit vollständigem Rollback je Test.
 * Der Testpool unterdrückt Commit/Rollback an den Service-Grenzen, damit auch globale
 * Satzstände ausschließlich in dieser Verbindung sichtbar sind. Transaktions- und
 * Sperrverhalten prüfen die dafür vorgesehenen eigenständigen Integrationstests.
 */
@EnabledIfEnvironmentVariable(named = "TX_URL", matches = ".+")
class FachlicheNachpruefungDbTest {
    private Connection connection;
    private SozialLeistungsService service;

    @BeforeEach
    void aufbauen() throws Exception {
        Class.forName("oracle.jdbc.OracleDriver");
        connection = DriverManager.getConnection(System.getenv("TX_URL"),
                System.getenv("TX_USER"), System.getenv("TX_PASS"));
        connection.setAutoCommit(false);
        Connection rollbackOnly = (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                    if (method.getName().equals("commit") || method.getName().equals("rollback")
                            || method.getName().equals("close") || method.getName().equals("setTransactionIsolation")) {
                        return null;
                    }
                    try { return method.invoke(connection, args); }
                    catch (InvocationTargetException e) { throw e.getCause(); }
                });
        service = new SozialLeistungsService(new TxManager(new ConnectionPool() {
            @Override public Connection getConnection() { return rollbackOnly; }
            @Override public void release(Connection c) { }
            @Override public void close() { }
        }));
        AntragsSagaProzess.setzeKduParameter(Leistungsberechnung.KduParameter.AUS);
    }

    @AfterEach
    void zurueckrollen() throws Exception {
        if (connection != null) {
            try { connection.rollback(); } finally { connection.close(); }
        }
    }

    @Test
    void serviceBewilligtSchwangerschaftNurBisEntbindungsmonatUndDruckBleibtUnveraendert() throws Exception {
        Vorgang vorgang = anlegen("0");
        update("UPDATE SOZIAL_FALL SET FLAG_SCHWANGER=1, ENTBINDUNGSMONAT='2026-10' WHERE BG_ID=" + vorgang.id());
        var bescheid = service.erteileBescheid(vorgang.id(), "2026-10", "2026-11", "sb-review");
        var snapshot = BerechnungsSnapshot.lese(bescheid.berechnung());
        assertEquals(eur("958.71"), snapshot.monate().get(0).ergebnis().zahlbetrag());
        assertEquals(eur("863.00"), snapshot.monate().get(1).ergebnis().zahlbetrag());
        assertEquals(eur("958.71"), auszahlen(vorgang.id(), "2026-10").zahlung().auszahlungsbetrag());
        assertEquals(eur("863.00"), auszahlen(vorgang.id(), "2026-11").zahlung().auszahlungsbetrag());
        String html = service.generiereBescheidHtml(vorgang.id());
        assertTrue(html.contains("2026-10</td><td class='num'><strong>958,71 EUR"));
        assertTrue(html.contains("2026-11</td><td class='num'><strong>863,00 EUR"));
        var fall = service.ladeDetail(vorgang.id()).faelle().get(0);
        var geaendert = new de.allgemein.lmg.model.SozialFall(fall.id(), vorgang.id(), 1, false, false,
                eur("600.00"), eur("0.00"), eur("0.00"), eur("100.00"), eur("0.00"), fall.version());
        service.aktualisiereSozialFall(geaendert, fall.version(), "sb-review");
        assertEquals(html, service.generiereBescheidHtml(vorgang.id()), "Nachdruck muss den Monats-Snapshot verwenden");
        assertEquals(bescheid.berechnung(), service.letzterBescheid(vorgang.id()).orElseThrow().berechnung());
    }

    @Test
    void serviceBewilligtVermoegenseinsatzNurEinmal() throws Exception {
        Vorgang vorgang = anlegen("10500");
        var bescheid = service.erteileBescheid(vorgang.id(), "2026-10", "2026-11", "sb-review");
        var snapshot = BerechnungsSnapshot.lese(bescheid.berechnung());
        assertEquals(eur("363.00"), bescheid.zahlbetrag());
        assertEquals(eur("363.00"), snapshot.monate().get(0).ergebnis().zahlbetrag());
        assertEquals(eur("863.00"), snapshot.monate().get(1).ergebnis().zahlbetrag());
        assertEquals(eur("10500.00"), betrag("SELECT VERMOEGEN FROM BEDARFSGEMEINSCHAFT WHERE ID=" + vorgang.id()),
                "Eine Bescheiderteilung darf den Bestand nur simulieren");
        assertEquals(eur("363.00"), auszahlen(vorgang.id(), "2026-10").zahlung().auszahlungsbetrag());
        assertEquals(eur("863.00"), auszahlen(vorgang.id(), "2026-11").zahlung().auszahlungsbetrag());
        String html = service.generiereBescheidHtml(vorgang.id());
        assertTrue(html.contains("2026-10</td><td class='num'><strong>363,00 EUR"));
        assertTrue(html.contains("2026-11</td><td class='num'><strong>863,00 EUR"));
    }

    @Test
    void sagaEntferntAbgelaufenenSchwangerenMehrbedarfInAllenSchritten() throws Exception {
        Vorgang vorgang = anlegen("0");
        update("UPDATE SOZIAL_FALL SET FLAG_SCHWANGER=1, ENTBINDUNGSMONAT='2026-09' WHERE BG_ID=" + vorgang.id());
        AntragsSagaProzess.bedarfspruefung(connection, vorgang.payload(), vorgang.sagaId());
        AntragsSagaProzess.bescheidErteilen(connection, vorgang.payload(), vorgang.sagaId());
        AntragsSagaProzess.erstauszahlungAnweisen(connection, vorgang.payload(), vorgang.sagaId());
        assertEquals(eur("863.00"), betrag("SELECT ZAHLBETRAG FROM BESCHEID WHERE BG_ID=" + vorgang.id()));
        assertEquals(eur("863.00"), betrag("SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE BG_ID=" + vorgang.id()));
    }

    @Test
    void sagaHistorisierungSperrtJedenFolgeschrittOhneWeitereMutation() throws Exception {
        for (int schritt = 2; schritt <= 4; schritt++) {
            Vorgang vorgang = anlegen("0");
            update("UPDATE BEDARFSGEMEINSCHAFT SET STATUS_AKTIV=0 WHERE ID=" + vorgang.id());
            int zuPruefen = schritt;
            var fehler = assertThrows(FachlicheException.class, () -> ausfuehren(zuPruefen, vorgang));
            assertEquals(FachlicheException.VALIDATION, fehler.code());
            assertEquals(0, anzahl("BESCHEID", vorgang.id()));
            assertEquals(0, anzahl("ZAHLUNG", vorgang.id()));
            // nur der Journal-Marker aus Schritt 1 — die gesperrten Folgeschritte fügen nichts hinzu
            assertEquals(1, anzahl("JOURNAL", vorgang.id()));
            assertEquals(eur("10000.00"), betrag("SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID=" + vorgang.id()));
        }
    }

    @Test
    void sagaPrueftAnspruchNachZwischenzeitlicherFallkorrekturErneut() throws Exception {
        for (int schritt = 3; schritt <= 4; schritt++) {
            Vorgang vorgang = anlegen("0");
            AntragsSagaProzess.bedarfspruefung(connection, vorgang.payload(), vorgang.sagaId());
            if (schritt == 4) AntragsSagaProzess.bescheidErteilen(connection, vorgang.payload(), vorgang.sagaId());
            long bescheideVorher = anzahl("BESCHEID", vorgang.id());
            long journalVorher = anzahl("JOURNAL", vorgang.id());
            update("UPDATE SOZIAL_FALL SET EINKOMMEN_RENTE=2000 WHERE BG_ID=" + vorgang.id());
            int zuPruefen = schritt;
            var fehler = assertThrows(FachlicheException.class, () -> ausfuehren(zuPruefen, vorgang));
            assertEquals(FachlicheException.KEIN_ANSPRUCH, fehler.code());
            assertEquals(bescheideVorher, anzahl("BESCHEID", vorgang.id()));
            assertEquals(journalVorher, anzahl("JOURNAL", vorgang.id()));
            assertEquals(0, anzahl("ZAHLUNG", vorgang.id()));
            assertEquals(eur("10000.00"), betrag("SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID=" + vorgang.id()));
        }
    }

    @Test
    void sagaZahlungVerwendetKorrigierteBankdatenAusDemBestand() throws Exception {
        Vorgang vorgang = anlegen("0");
        update("UPDATE BEDARFSGEMEINSCHAFT SET IBAN='DE12500105170648489890', BIC='INGDDEFFXXX', "
                + "KONTOINHABER='Neues Konto' WHERE ID=" + vorgang.id());
        AntragsSagaProzess.erstauszahlungAnweisen(connection, vorgang.payload(), vorgang.sagaId());
        try (Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery(
                "SELECT EMPFAENGER_IBAN,EMPFAENGER_BIC,EMPFAENGER_NAME FROM ZAHLUNG WHERE BG_ID=" + vorgang.id())) {
            assertTrue(rs.next());
            assertEquals("DE12500105170648489890", rs.getString(1));
            assertEquals("INGDDEFFXXX", rs.getString(2));
            assertEquals("Neues Konto", rs.getString(3));
        }
    }

    @Test
    void zukuenftigerSatzstandAendertAktuelleVorschauNicht() throws Exception {
        Vorgang vorgang = anlegen("0");
        BigDecimal vorher = service.ladeDetail(vorgang.id()).berechnung().zahlbetrag();
        // Keine andere Verbindung sieht diesen Satz; AfterEach rollt ihn zwingend zurück.
        update("INSERT INTO SATZSTAND (GUELTIG_AB,RBS_1,RBS_2,RBS_3,RBS_4,RBS_5,RBS_6) "
                + "VALUES ('2099-12',600,540,480,500,415,380)");
        assertEquals(eur("863.00"), vorher);
        assertEquals(vorher, service.ladeDetail(vorgang.id()).berechnung().zahlbetrag());
    }

    @Test
    void aktiveBewilligungenDuerfenSichNichtUeberschneidenAberAngrenzen() throws Exception {
        Vorgang vorgang = anlegen("0");
        service.erteileBescheid(vorgang.id(), "2026-10", "2026-11", "sb-review");
        var error = assertThrows(FachlicheException.class,
                () -> service.erteileBescheid(vorgang.id(), "2026-11", "2026-12", "sb-review"));
        assertEquals("VALIDATION", error.code());
        assertEquals(1, anzahl("BESCHEID", vorgang.id()));
        service.erteileBescheid(vorgang.id(), "2026-12", "2026-12", "sb-review");
        assertEquals(2, anzahl("BESCHEID", vorgang.id()));
    }

    @Test
    void geaenderteFallwerteSperrenZuHoheUndZuNiedrigeZahlungenVorAllenMutationen() throws Exception {
        for (int initialIncome : new int[]{0, 100}) {
            Vorgang vorgang = anlegen("0");
            update("UPDATE SOZIAL_FALL SET EINKOMMEN_RENTE=" + initialIncome + " WHERE BG_ID=" + vorgang.id());
            service.erteileBescheid(vorgang.id(), "2026-10", "2026-10", "sb-review");
            update("UPDATE SOZIAL_FALL SET EINKOMMEN_RENTE=" + (100 - initialIncome) + " WHERE BG_ID=" + vorgang.id());
            long journalBefore = anzahl("JOURNAL", vorgang.id());
            var error = assertThrows(FachlicheException.class, () -> auszahlen(vorgang.id(), "2026-10"));
            assertEquals("VALIDATION", error.code());
            assertTrue(error.getMessage().contains("Monatsbetrag"));
            assertEquals(0, anzahl("ZAHLUNG", vorgang.id()));
            assertEquals(journalBefore, anzahl("JOURNAL", vorgang.id()));
            assertEquals(eur("10000.00"), betrag("SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID=" + vorgang.id()));
        }
    }

    @Test
    void vorgezogenerMonatUndVermoegensrueckgabeNachStornoVerdeckenKeinePlanabweichung() throws Exception {
        Vorgang vorgang = anlegen("10500");
        service.erteileBescheid(vorgang.id(), "2026-10", "2026-12", "sb-review");
        assertThrows(FachlicheException.class, () -> auszahlen(vorgang.id(), "2026-11"));
        assertEquals(eur("10500.00"), betrag("SELECT VERMOEGEN FROM BEDARFSGEMEINSCHAFT WHERE ID=" + vorgang.id()));
        var october = auszahlen(vorgang.id(), "2026-10");
        assertEquals(eur("863.00"), auszahlen(vorgang.id(), "2026-11").zahlung().auszahlungsbetrag());
        service.storniereZahlung(october.zahlung().id(), "sb-review");
        BigDecimal budget = betrag("SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID=" + vorgang.id());
        assertThrows(FachlicheException.class, () -> auszahlen(vorgang.id(), "2026-12"));
        assertEquals(2, anzahl("ZAHLUNG", vorgang.id()));
        assertEquals(budget, betrag("SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID=" + vorgang.id()));
        assertEquals(eur("10500.00"), betrag("SELECT VERMOEGEN FROM BEDARFSGEMEINSCHAFT WHERE ID=" + vorgang.id()));
    }

    @Test
    void mehrdeutigeAltbewilligungenUndBeschaedigterSnapshotSindKeinZahlungsnachweis() throws Exception {
        Vorgang vorgang = anlegen("0");
        var decision = service.erteileBescheid(vorgang.id(), "2026-10", "2026-10", "sb-review");
        update("INSERT INTO BESCHEID (ID,BG_ID,BESCHEID_NR,BESCHEID_DATUM,BEWILLIGUNG_VON,BEWILLIGUNG_BIS,"
                + "ZAHLBETRAG,STATUS,ERSTELLT_VON,BERECHNUNG) SELECT SEQ_BESCHEID.NEXTVAL,BG_ID,BESCHEID_NR||'-ALT',"
                + "BESCHEID_DATUM,BEWILLIGUNG_VON,BEWILLIGUNG_BIS,ZAHLBETRAG,STATUS,ERSTELLT_VON,BERECHNUNG"
                + " FROM BESCHEID WHERE ID=" + decision.id());
        var ambiguous = assertThrows(FachlicheException.class, () -> auszahlen(vorgang.id(), "2026-10"));
        assertTrue(ambiguous.getMessage().contains("eindeutiger"));
        update("UPDATE BESCHEID SET STATUS='AUFGEHOBEN' WHERE BG_ID=" + vorgang.id() + " AND ID<>" + decision.id());
        // Druck wählt den jüngsten Archivbescheid, Zahlungsbindung das ERTEILT-Original.
        // Beide tatsächlich ausgewählten Snapshots müssen für diese Korruptionsprüfung defekt sein.
        update("UPDATE BESCHEID SET BERECHNUNG='{\"version\":1}' WHERE BG_ID=" + vorgang.id());
        assertThrows(FachlicheException.class, () -> auszahlen(vorgang.id(), "2026-10"));
        assertThrows(FachlicheException.class, () -> service.generiereBescheidHtml(vorgang.id()));
        assertEquals(0, anzahl("ZAHLUNG", vorgang.id()));
        assertEquals(eur("10000.00"), betrag("SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID=" + vorgang.id()));
    }

    @Test
    void sagaZahlungIstAnEigenenBewilligtenMonatswertGebunden() throws Exception {
        Vorgang vorgang = anlegen("0");
        AntragsSagaProzess.bescheidErteilen(connection, vorgang.payload(), vorgang.sagaId());
        update("UPDATE SOZIAL_FALL SET EINKOMMEN_RENTE=100 WHERE BG_ID=" + vorgang.id());
        assertThrows(FachlicheException.class,
                () -> AntragsSagaProzess.erstauszahlungAnweisen(connection, vorgang.payload(), vorgang.sagaId()));
        assertEquals(0, anzahl("ZAHLUNG", vorgang.id()));
        assertEquals(eur("10000.00"), betrag("SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID=" + vorgang.id()));
    }

    @Test
    void sagaKompensiertNurDenEigenenBescheidUndIstIdempotent() throws Exception {
        Vorgang vorgang = anlegen("0");
        AntragsSagaProzess.bescheidErteilen(connection, vorgang.payload(), vorgang.sagaId());
        var manual = service.erteileBescheid(vorgang.id(), "2026-11", "2026-11", "sb-manual");
        AntragsSagaProzess.bescheidKompensieren(connection, vorgang.payload(), vorgang.sagaId());
        AntragsSagaProzess.bescheidKompensieren(connection, vorgang.payload(), vorgang.sagaId());
        assertEquals(1, betrag("SELECT COUNT(*) FROM BESCHEID WHERE BG_ID=" + vorgang.id() + " AND STATUS='AUFGEHOBEN'").intValueExact());
        assertEquals(1, betrag("SELECT COUNT(*) FROM BESCHEID WHERE ID=" + manual.id() + " AND STATUS='ERTEILT'").intValueExact());
    }

    @Test
    void altSagaOhneBescheidmarkerDarfKeineVollstaendigeKompensationBehaupten() throws Exception {
        Vorgang vorgang = anlegen("0");
        // IN_FLIGHT ohne committeten Bescheid ist eine zulässige idempotente No-Operation.
        AntragsSagaProzess.bescheidKompensieren(connection, vorgang.payload(), vorgang.sagaId());
        AntragsSagaProzess.bescheidErteilen(connection, vorgang.payload(), vorgang.sagaId());
        update("DELETE FROM JOURNAL WHERE BG_ID=" + vorgang.id() + " AND AKTION_TEXT LIKE '%: Bescheid-ID %'");
        var error = assertThrows(FachlicheException.class,
                () -> AntragsSagaProzess.bescheidKompensieren(connection, vorgang.payload(), vorgang.sagaId()));
        assertEquals("SAGA_BESCHEID_ZUORDNUNG", error.code());
        assertEquals(1, betrag("SELECT COUNT(*) FROM BESCHEID WHERE BG_ID=" + vorgang.id() + " AND STATUS='ERTEILT'").intValueExact());
    }

    private record Vorgang(long id, String payload, String sagaId) { }

    private Vorgang anlegen(String vermoegen) throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 12);
        String az = "BG-NACHPR-" + suffix;
        String payload = """
                {"aktenzeichen":"%s","hauptpersonName":"Review Testperson","regelbedarfsstufe":1,
                 "zahlungsmonat":"2026-10","bewilligungBis":"2026-10","startBudget":10000,
                 "vermoegen":%s,"kduMiete":300,"iban":"DE89370400440532013000","bic":"COBADEFFXXX"}
                """.formatted(az, vermoegen);
        String sagaId = "review-" + suffix;
        AntragsSagaProzess.vorgangErfassen(connection, payload, sagaId);
        return new Vorgang(betrag("SELECT ID FROM BEDARFSGEMEINSCHAFT WHERE AKTENZEICHEN='" + az + "'").longValueExact(),
                payload, sagaId);
    }

    private void ausfuehren(int schritt, Vorgang vorgang) throws Exception {
        switch (schritt) {
            case 2 -> AntragsSagaProzess.bedarfspruefung(connection, vorgang.payload(), vorgang.sagaId());
            case 3 -> AntragsSagaProzess.bescheidErteilen(connection, vorgang.payload(), vorgang.sagaId());
            case 4 -> AntragsSagaProzess.erstauszahlungAnweisen(connection, vorgang.payload(), vorgang.sagaId());
            default -> throw new IllegalArgumentException("Ungültiger Testschritt");
        }
    }

    private de.allgemein.lmg.service.Auszahlungsergebnis auszahlen(long id, String monat) {
        return service.ordneAuszahlungAn(new Auszahlungsauftrag(id, "NACHPR-" + UUID.randomUUID(),
                monat, "sb-review", false, null, null, null));
    }

    private static BigDecimal eur(String betrag) { return new BigDecimal(betrag); }

    private void update(String sql) throws Exception {
        try (Statement statement = connection.createStatement()) { statement.executeUpdate(sql); }
    }

    private BigDecimal betrag(String sql) throws Exception {
        try (Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery(sql)) {
            assertTrue(rs.next());
            return rs.getBigDecimal(1).setScale(2);
        }
    }

    private long anzahl(String tabelle, long bgId) throws Exception {
        return betrag("SELECT COUNT(*) FROM " + tabelle + " WHERE BG_ID=" + bgId).longValueExact();
    }
}
