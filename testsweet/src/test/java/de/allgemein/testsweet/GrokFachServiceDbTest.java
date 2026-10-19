package de.allgemein.testsweet;

import de.allgemein.lmg.calc.BerechnungsSnapshot;
import de.allgemein.lmg.model.Bescheid;
import de.allgemein.lmg.model.SozialFall;
import de.allgemein.lmg.service.*;
import de.allgemein.lmg.web.Json;
import de.allgemein.tx.SimpleConnectionPool;
import de.allgemein.tx.TxManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.sql.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Downstream-Prüfung des installierten Produkts: echte Serviceaufrufe, Oracle-Commit
 * und davon unabhängiges SQL. Jeder Test besitzt ausschließlich seine UUID-BG.
 */
@EnabledIfEnvironmentVariable(named = "TX_URL", matches = ".+")
class GrokFachServiceDbTest {
    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private static final String IBAN_ORIGINAL = "DE89370400440532013000";
    private static final String IBAN_NEU = "DE12500105170648489890";
    private static final String ACTOR = "sb-sweet-grok";
    private String url;
    private String user;
    private String password;
    private SimpleConnectionPool pool;
    private SozialLeistungsService service;
    private long bgId;
    private SozialFall person;

    @BeforeEach
    void createOwnedCase() throws Exception {
        url = System.getenv("TX_URL");
        user = System.getenv().getOrDefault("TX_USER", "tx_demo");
        password = System.getenv().getOrDefault("TX_PASS", "tx_demo");
        Class.forName("oracle.jdbc.OracleDriver");
        pool = new SimpleConnectionPool(url, user, password, 4);
        service = new SozialLeistungsService(new TxManager(pool));
        bgId = service.legeBedarfsgemeinschaftAn("SWEET-GROK-" + UUID.randomUUID(),
                "Synthetische Downstream-Person", new BigDecimal("10000.00"), null,
                IBAN_ORIGINAL, "COBADEFFXXX", "Originalkonto", ZERO, ACTOR).id();
        person = service.legeSozialFallAn(bgId, input(0, false, ZERO, 0, 0, 0, null, 1), ACTOR);
    }

    @AfterEach
    void removeOnlyOwnedCase() throws Exception {
        try {
            if (bgId != 0) try (var c = connection()) {
                c.setAutoCommit(false);
                for (String table : List.of("JOURNAL", "BESCHEID", "ZAHLUNG", "SOZIAL_FALL")) {
                    try (var s = c.prepareStatement("DELETE FROM " + table + " WHERE BG_ID=?")) {
                        s.setLong(1, bgId); s.executeUpdate();
                    }
                }
                try (var s = c.prepareStatement("DELETE FROM BEDARFSGEMEINSCHAFT WHERE ID=?")) {
                    s.setLong(1, bgId); s.executeUpdate();
                }
                c.commit();
            }
        } finally { if (pool != null) pool.close(); }
    }

    @Test
    void bezugsmonatStimmtInServiceVorschauBescheidUndPersistierterZahlungUeberein() throws Exception {
        person = service.aktualisiereSozialFall(input(person.id(), true, ZERO, 0, 0, 0,
                "2026-06", person.version()), person.version(), ACTOR);
        var bescheid = service.erteileBescheid(bgId, "2026-06", "2026-07", ACTOR);
        var vorher = state();
        var juni = service.ladeDetail(bgId, null, "2026-06");
        var juli = service.ladeDetail(bgId, null, "2026-07");
        assertEquals("2026-06", juni.berechnungsmonat());
        assertEquals("2026-07", juli.berechnungsmonat());
        assertMoney("658.71", juni.berechnung().zahlbetrag());
        assertMoney("563.00", juli.berechnung().zahlbetrag());
        assertEquals(vorher, state(), "Reine Monatsvorschau verändert keine Tabelle");
        var snapshot = BerechnungsSnapshot.lese(savedDecision(bescheid.id()).snapshot());
        assertEquals(person, snapshot.monate().getFirst().faelle().getFirst());
        assertTrue(snapshot.monate().getFirst().faelle().getFirst().schwanger());
        assertFalse(snapshot.monate().get(1).faelle().getFirst().schwanger());
        assertEquals("2026-06", snapshot.monate().get(1).faelle().getFirst().entbindungsmonat());
        var zahlung = payment("2026-06").zahlung();
        assertMoney("658.71", number("SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID=?", zahlung.id()));
        assertMoney("658.71", BescheidPaymentBinding.approvedAmount(bescheid, "2026-06"));
        rejectWithoutWrites(() -> service.ladeDetail(bgId, null, "2026-13"));
    }

    @Test
    void listeUndMaskeNennenDenselbenBezugsmonatOhneDenExplizitenBuchungsmonatZuVeraendern() throws Exception {
        person = service.aktualisiereSozialFall(input(person.id(), true, ZERO, 0, 0, 0,
                "2026-06", person.version()), person.version(), ACTOR);
        var july = java.time.Clock.fixed(java.time.Instant.parse("2026-07-01T10:00:00Z"),
                java.time.ZoneId.of("Europe/Berlin"));
        var timed = new SozialLeistungsService(new TxManager(pool),
                de.allgemein.lmg.calc.Leistungsberechnung.KduParameter.AUS, null, july);
        var before = state();
        var defaultRows = timed.listeBedarfsgemeinschaften();
        assertTrue(defaultRows.stream().allMatch(row -> "2026-07".equals(row.berechnungsmonat())));
        var current = defaultRows.stream().filter(row -> row.id() == bgId).findFirst().orElseThrow();
        assertMoney("563.00", current.zahlbetragVorschau());
        var selected = timed.listeBedarfsgemeinschaften("2026-06").stream()
                .filter(row -> row.id() == bgId).findFirst().orElseThrow();
        var detail = timed.ladeDetail(bgId, null, "2026-06");
        assertEquals("2026-06", selected.berechnungsmonat());
        assertEquals(detail.berechnungsmonat(), selected.berechnungsmonat());
        assertMoney("658.71", selected.zahlbetragVorschau());
        assertEquals(0, detail.berechnung().zahlbetrag().compareTo(selected.zahlbetragVorschau()));
        assertEquals(before, state(), "Listen- und Detailvorschauen verändern keine Fachtabellen");
        rejectWithoutWrites(() -> timed.listeBedarfsgemeinschaften("2026-13"));
        var payment = timed.ordneAuszahlungAn(new Auszahlungsauftrag(bgId, "SWEET-LIST-" + UUID.randomUUID(),
                "2026-06", ACTOR, false, null, null, null)).zahlung();
        assertMoney("658.71", number("SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID=?", payment.id()));
        assertEquals("2026-06", payment.zahlungsmonat(), "Juli-Vorschauuhr verschiebt keine Juni-Anordnung");
    }

    @Test
    void nullanordnungUndVorschauVerbrauchenKeinVermoegenOhneTatsaechlicheBestandsaenderung() throws Exception {
        setAssets("10563.00");
        var vorher = state();
        for (String month : List.of("2026-10", "2026-11")) {
            assertMoney("0.00", service.ladeDetail(bgId, null, month).berechnung().zahlbetrag());
            var error = rejectWithoutWrites(() -> payment(month));
            assertEquals(FachlicheException.KEIN_ANSPRUCH, error.code());
        }
        assertEquals(vorher, state());
        assertMoney("10563.00", number("SELECT VERMOEGEN FROM BEDARFSGEMEINSCHAFT WHERE ID=?", bgId));
        setAssets("10000.00");
        var zahlung = payment("2026-11").zahlung();
        assertMoney("563.00", number("SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID=?", zahlung.id()));
        assertMoney("0.00", number("SELECT VERMOEGENSEINSATZ FROM ZAHLUNG WHERE ID=?", zahlung.id()));
    }

    @Test
    void aenderungsbescheidBewahrtAltplanUndHistorischesDokumentAuchNachWeiterenStammdatenwechseln() throws Exception {
        var alt = service.erteileBescheid(bgId, "2026-10", "2026-12", ACTOR);
        String altHtml = service.generiereBescheidHtml(bgId);
        var oktober = payment("2026-10").zahlung();
        person = service.aktualisiereSozialFall(input(person.id(), false, new BigDecimal("200.00"),
                0, 0, 0, null, person.version()), person.version(), ACTOR);
        rejectWithoutWrites(() -> payment("2026-11"));
        var neu = service.aendereBescheid(bgId, "2026-11", "2026-12", ACTOR);
        var alterDatensatz = savedDecision(alt.id());
        var neuerDatensatz = savedDecision(neu.id());
        assertEquals("AUFGEHOBEN", alterDatensatz.status());
        assertEquals(alt.berechnung(), alterDatensatz.snapshot());
        assertEquals(altHtml, BerechnungsSnapshot.lese(alterDatensatz.snapshot()).dokumentHtml());
        assertEquals("ERTEILT", neuerDatensatz.status());
        var originalPlan = BerechnungsSnapshot.lese(alt.berechnung());
        var neuerPlan = BerechnungsSnapshot.lese(neuerDatensatz.snapshot());
        assertEquals(originalPlan.monate().getFirst(), neuerPlan.monate().getFirst());
        assertMoney("563.00", neuerPlan.monate().getFirst().ergebnis().zahlbetrag());
        assertMoney("363.00", neuerPlan.monate().get(1).ergebnis().zahlbetrag());
        assertMoney("563.00", number("SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID=?", oktober.id()));
        var november = payment("2026-11").zahlung();
        assertMoney("363.00", number("SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID=?", november.id()));
        String neuesHtml = service.generiereBescheidHtml(bgId);
        assertTrue(neuesHtml.contains("Änderungsbescheid"));
        assertTrue(neuesHtml.contains(alt.bescheidNr()));
        changeBank();
        assertEquals(neuesHtml, service.generiereBescheidHtml(bgId));
        assertEquals(neuerDatensatz, savedDecision(neu.id()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"10000.01", "10500.00"})
    void offenerPraefixVermoegenseinsatzBlockiertAenderungBisZurTatsaechlichenAnordnung(String assets) throws Exception {
        setAssets(assets);
        var alt = service.erteileBescheid(bgId, "2026-10", "2026-12", ACTOR);
        var error = rejectWithoutWrites(() -> service.aendereBescheid(bgId, "2026-11", "2026-12", ACTOR));
        assertTrue(error.getMessage().contains("Vermögenseinsatz"));
        assertEquals("ERTEILT", savedDecision(alt.id()).status());
        var oktober = payment("2026-10").zahlung();
        var expected = new BigDecimal("563.00").subtract(new BigDecimal(assets).subtract(new BigDecimal("10000.00")));
        assertEquals(0, expected.compareTo(number("SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID=?", oktober.id())));
        assertMoney("10000.00", number("SELECT VERMOEGEN FROM BEDARFSGEMEINSCHAFT WHERE ID=?", bgId));
        var neu = service.aendereBescheid(bgId, "2026-11", "2026-12", ACTOR);
        assertMoney("563.00", BescheidPaymentBinding.approvedAmount(neu, "2026-11"));
        assertMoney("563.00", payment("2026-11").zahlung().auszahlungsbetrag());
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 3})
    @SuppressWarnings("unchecked")
    void legacyPraefixOhneEingabenBleibtUnveraendertUndWirdNichtAlsVollstaendigErfunden(int version) throws Exception {
        var alt = service.erteileBescheid(bgId, "2026-10", "2026-12", ACTOR);
        var document = (Map<String, Object>) Json.parse(alt.berechnung());
        document.put("version", version);
        if (version == 2) document.remove("dokumentHtml");
        for (Object month : (List<?>) document.get("monate")) {
            for (Object entry : (List<?>) ((Map<?, ?>) month).get("faelle")) {
                ((Map<?, ?>) entry).remove("eingaben");
            }
        }
        String legacy = Json.write(document);
        try (var c = connection(); var s = c.prepareStatement("UPDATE BESCHEID SET BERECHNUNG=? WHERE ID=?")) {
            s.setString(1, legacy); s.setLong(2, alt.id()); assertEquals(1, s.executeUpdate());
        }
        var error = rejectWithoutWrites(() -> service.aendereBescheid(bgId, "2026-11", "2026-12", ACTOR));
        assertTrue(error.getMessage().contains("historische Angaben erfinden"));
        assertEquals(legacy, savedDecision(alt.id()).snapshot());
        assertMoney("563.00", payment("2026-10").zahlung().auszahlungsbetrag());
    }

    @Test
    void fehlerNachBegonnenerAufhebungRolltBescheidJournalUndFinanzdatenGemeinsamZurueck() throws Exception {
        var alt = service.erteileBescheid(bgId, "2026-10", "2026-12", ACTOR);
        // Beschädigter Altbestand: Überschneidung nur im beizubehaltenden Monat.
        // Im Produktpfad wird sie nach dem UPDATE AUFGEHOBEN erkannt.
        try (var c = connection(); var s = c.prepareStatement(
                "INSERT INTO BESCHEID (ID,BG_ID,BESCHEID_NR,BESCHEID_DATUM,BEWILLIGUNG_VON,"
                        + "BEWILLIGUNG_BIS,ZAHLBETRAG,STATUS,ERSTELLT_VON,ERSTELLT_AM) "
                        + "VALUES (SEQ_BESCHEID.NEXTVAL,?,'SWEET-GROK-DUP-'||SEQ_BESCHEID.CURRVAL,"
                        + "SYSDATE,'2026-10','2026-10',563,'ERTEILT','sb-sweet-grok',SYSTIMESTAMP)")) {
            s.setLong(1, bgId); assertEquals(1, s.executeUpdate());
        }
        rejectWithoutWrites(() -> service.aendereBescheid(bgId, "2026-11", "2026-12", ACTOR));
        assertEquals(new SavedDecision("ERTEILT", alt.berechnung()), savedDecision(alt.id()));
    }

    @Test
    void bestehendeAnordnungImAenderungsabschnittVerhindertStilleNeufestsetzung() throws Exception {
        var alt = service.erteileBescheid(bgId, "2026-10", "2026-12", ACTOR);
        var november = payment("2026-11").zahlung();
        var error = rejectWithoutWrites(() -> service.aendereBescheid(bgId, "2026-11", "2026-12", ACTOR));
        assertTrue(error.getMessage().contains("Zahlungsanordnungen"));
        assertEquals(new SavedDecision("ERTEILT", alt.berechnung()), savedDecision(alt.id()));
        assertMoney("563.00", number("SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID=?", november.id()));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void replayOhneBankoverrideBehaeltOriginalauftragAuchNachStammdatenwechsel(boolean expliziterErstauftrag) throws Exception {
        String number = "SWEET-GROK-REPLAY-" + UUID.randomUUID();
        var initial = new Auszahlungsauftrag(bgId, number, "2026-10", ACTOR, false,
                expliziterErstauftrag ? IBAN_ORIGINAL : null, expliziterErstauftrag ? "COBADEFFXXX" : null,
                expliziterErstauftrag ? "Originalkonto" : null);
        var original = service.ordneAuszahlungAn(initial).zahlung();
        changeBank();
        var ohneOverride = service.ordneAuszahlungAn(new Auszahlungsauftrag(bgId, number, "2026-10",
                "sb-sweet-replay", false, null, " ", null));
        assertTrue(ohneOverride.bereitsGebucht());
        assertEquals(original, ohneOverride.zahlung());
        var gleicheExpliziteBank = service.ordneAuszahlungAn(new Auszahlungsauftrag(bgId, number,
                "2026-10", "sb-sweet-replay", false, "de89 3704 0044 0532 0130 00", "cobadeffxxx", "Originalkonto"));
        assertTrue(gleicheExpliziteBank.bereitsGebucht());
        assertEquals(original, gleicheExpliziteBank.zahlung());
        rejectWithoutWrites(() -> service.ordneAuszahlungAn(new Auszahlungsauftrag(bgId, number,
                "2026-10", "sb-sweet-replay", false, IBAN_NEU, null, null)));
        assertMoney("1", number("SELECT COUNT(*) FROM ZAHLUNG WHERE BG_ID=?", bgId));
        assertMoney("9437.00", number("SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID=?", bgId));
        try (var c = connection(); var s = c.prepareStatement("SELECT EMPFAENGER_IBAN FROM ZAHLUNG WHERE ID=?")) {
            s.setLong(1, original.id()); try (var rows = s.executeQuery()) {
                assertTrue(rows.next()); assertEquals(IBAN_ORIGINAL, rows.getString(1));
            }
        }
    }

    @Test
    void widerspruechlicheKinderUndMehrfachzuordnungWerdenVorPersistenzUndBuchungAbgewiesen() throws Exception {
        person = service.aktualisiereSozialFall(input(person.id(), false, ZERO, 1, 2, 4, null,
                person.version()), person.version(), ACTOR);
        var second = service.legeSozialFallAn(bgId, input(0, false, ZERO, 0, 0, 0, null, 1), ACTOR);
        rejectWithoutWrites(() -> service.aktualisiereSozialFall(input(person.id(), false, ZERO,
                2, 1, 4, null, person.version()), person.version(), ACTOR));
        rejectWithoutWrites(() -> service.aktualisiereSozialFall(input(second.id(), false, ZERO,
                1, 2, 4, null, second.version()), second.version(), ACTOR));
        assertMoney("1396.24", payment("2026-10").zahlung().auszahlungsbetrag());
        assertMoney("2", number("SELECT COUNT(*) FROM SOZIAL_FALL WHERE BG_ID=?", bgId));
    }

    private SozialFall input(long id, boolean pregnant, BigDecimal pension, int u7, int u16,
            int minors, String dueMonth, long version) {
        return new SozialFall(id, bgId, 1, false, pregnant, u7, u16, minors, false,
                ZERO, ZERO, ZERO, pension, ZERO, ZERO, ZERO, ZERO, ZERO, dueMonth, version);
    }

    private Auszahlungsergebnis payment(String month) {
        return service.ordneAuszahlungAn(new Auszahlungsauftrag(bgId, "SWEET-GROK-" + UUID.randomUUID(),
                month, ACTOR, false, null, null, null));
    }

    private void setAssets(String assets) {
        var bg = service.ladeDetail(bgId).bedarfsgemeinschaft();
        service.aktualisiereBedarfsgemeinschaft(bgId, true, null, bg.iban(), bg.bic(), bg.kontoinhaber(),
                new BigDecimal(assets), bg.version(), ACTOR);
    }

    private void changeBank() {
        var bg = service.ladeDetail(bgId).bedarfsgemeinschaft();
        service.aktualisiereBedarfsgemeinschaft(bgId, true, null, IBAN_NEU, "INGDDEFFXXX", "Neukonto", bg.version());
    }

    private Connection connection() throws SQLException { return DriverManager.getConnection(url, user, password); }

    private FachlicheException rejectWithoutWrites(Executable action) throws Exception {
        var before = state();
        var error = assertThrows(FachlicheException.class, action);
        assertEquals(before, state(), "Abgewiesene Aktion muss sämtliche eigenen Tabellenzeilen unverändert lassen");
        return error;
    }

    private BigDecimal number(String sql, long id) throws SQLException {
        try (var c = connection(); var s = c.prepareStatement(sql)) {
            s.setLong(1, id); try (var rows = s.executeQuery()) {
                assertTrue(rows.next()); return rows.getBigDecimal(1);
            }
        }
    }

    private record SavedDecision(String status, String snapshot) { }

    private SavedDecision savedDecision(long id) throws SQLException {
        try (var c = connection(); var s = c.prepareStatement("SELECT STATUS,BERECHNUNG FROM BESCHEID WHERE ID=?")) {
            s.setLong(1, id); try (var rows = s.executeQuery()) {
                assertTrue(rows.next()); return new SavedDecision(rows.getString(1), rows.getString(2));
            }
        }
    }

    private record State(List<List<String>> bg, List<List<String>> persons, List<List<String>> payments,
                         List<List<String>> decisions, List<List<String>> journal) { }

    private State state() throws SQLException {
        try (var c = connection()) {
            c.setAutoCommit(false);
            try (var statement = c.createStatement()) { statement.execute("SET TRANSACTION READ ONLY"); }
            var result = new State(rows(c, "BEDARFSGEMEINSCHAFT", "ID"), rows(c, "SOZIAL_FALL", "BG_ID"),
                    rows(c, "ZAHLUNG", "BG_ID"), rows(c, "BESCHEID", "BG_ID"), rows(c, "JOURNAL", "BG_ID"));
            c.rollback(); return result;
        }
    }

    private List<List<String>> rows(Connection c, String table, String key) throws SQLException {
        try (var statement = c.prepareStatement("SELECT * FROM " + table + " WHERE " + key + "=? ORDER BY ID")) {
            statement.setLong(1, bgId);
            try (var rows = statement.executeQuery()) {
                var values = new ArrayList<List<String>>();
                int count = rows.getMetaData().getColumnCount();
                while (rows.next()) {
                    var row = new ArrayList<String>();
                    for (int column = 1; column <= count; column++) row.add(rows.getString(column));
                    values.add(Collections.unmodifiableList(row));
                }
                return List.copyOf(values);
            }
        }
    }

    private static void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }
}
