package de.allgemein.lmg.db;

import de.allgemein.lmg.model.SozialFall;
import de.allgemein.lmg.saga.AntragsSagaProzess;
import de.allgemein.lmg.service.Auszahlungsauftrag;
import de.allgemein.lmg.service.FachlicheException;
import de.allgemein.lmg.service.SozialLeistungsService;
import de.allgemein.lmg.web.Json;
import de.allgemein.tx.SimpleConnectionPool;
import de.allgemein.tx.TxManager;
import de.allgemein.tx.saga.SagaEngine;
import de.allgemein.tx.saga.SagaJournal;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DB-Regression der Codereview-Umsetzung v0.5.5 (nur mit TX_URL):
 * <ul>
 *   <li>CR-003: Die Saga rechnet auf dem Datenbestand inklusive Vermögen —
 *       Bescheid und Erstauszahlung wie im manuellen Buchungsweg.</li>
 *   <li>CR-008: Der Schwangeren-Mehrbedarf endet mit dem Entbindungsmonat (§ 30 Abs. 2).</li>
 *   <li>CR-009: Historisierte Vorgänge bleiben zahlbar gesperrt; Reaktivierung
 *       über die Maske wird verweigert; der Kassenlauf überspringt sie.</li>
 * </ul>
 * Aktenzeichen-Präfix BG-CR- für die Datenhygiene.
 */
@EnabledIfEnvironmentVariable(named = "TX_URL", matches = ".+")
class CodereviewRegressionDbTest {

    private static SimpleConnectionPool pool;
    private static SozialLeistungsService service;
    private static SagaEngine sagas;

    @BeforeAll
    static void aufbauen() throws Exception {
        Class.forName("oracle.jdbc.OracleDriver");
        pool = new SimpleConnectionPool(System.getenv("TX_URL"), System.getenv("TX_USER"),
                System.getenv("TX_PASS"), 4);
        TxManager txManager = new TxManager(pool);
        service = new SozialLeistungsService(txManager);
        sagas = new SagaEngine(txManager, new SagaJournal(pool));
        AntragsSagaProzess.setzeKduParameter(de.allgemein.lmg.calc.Leistungsberechnung.KduParameter.AUS);
        aufraeumen();
    }

    @AfterAll
    static void aufraeumen() throws Exception {
        try (Connection c = DriverManager.getConnection(
                System.getenv("TX_URL"), System.getenv("TX_USER"), System.getenv("TX_PASS"))) {
            c.setAutoCommit(false);
            Statement st = c.createStatement();
            st.executeUpdate("DELETE FROM JOURNAL WHERE BG_ID IN "
                    + "(SELECT ID FROM BEDARFSGEMEINSCHAFT WHERE AKTENZEICHEN LIKE 'BG-CR-%')");
            st.executeUpdate("DELETE FROM BESCHEID WHERE BG_ID IN "
                    + "(SELECT ID FROM BEDARFSGEMEINSCHAFT WHERE AKTENZEICHEN LIKE 'BG-CR-%')");
            st.executeUpdate("DELETE FROM ZAHLUNG WHERE BG_ID IN "
                    + "(SELECT ID FROM BEDARFSGEMEINSCHAFT WHERE AKTENZEICHEN LIKE 'BG-CR-%')");
            st.executeUpdate("DELETE FROM SOZIAL_FALL WHERE BG_ID IN "
                    + "(SELECT ID FROM BEDARFSGEMEINSCHAFT WHERE AKTENZEICHEN LIKE 'BG-CR-%')");
            st.executeUpdate("DELETE FROM BEDARFSGEMEINSCHAFT WHERE AKTENZEICHEN LIKE 'BG-CR-%'");
            c.commit();
        }
    }

    private String sagaPayload(String aktenzeichen, String vermoegen, String monat) {
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) Json.parse("""
                {"aktenzeichen":"%s","hauptpersonName":"Carlina Check","iban":null,"bic":null,
                 "regelbedarfsstufe":1,"zahlungsmonat":"%s","bewilligungBis":"%s",
                 "startBudget":900.00,"vermoegen":%s,"kduMiete":300.00,
                 "kduBetriebskosten":0,"kduHeizung":0,"einkommenRente":0,"einkommenErwerb":0}
                """.formatted(aktenzeichen, monat, monat, vermoegen));
        AntragsSagaProzess.validierePayload(payload);
        return Json.stringify(payload);
    }

    /** CR-003: Saga-Bescheid und -Zahlung mindern den Zahlbetrag um verwertbares Vermögen (§ 90). */
    @Test
    void sagaBeruecksichtigtVermoegen() throws Exception {
        String aktenzeichen = "BG-CR-VERM";
        sagas.run(AntragsSagaProzess.definition(), sagaPayload(aktenzeichen, "10500.00", "2026-10"));
        long bgId = bgId(aktenzeichen);
        // Bedarf 863,00 (563 + 300 KdU) − verwertbares Vermögen 500,00 = 363,00
        try (Connection c = verbindung()) {
            assertEquals(0, betrag(c, "SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE BG_ID = " + bgId)
                    .compareTo(new BigDecimal("363.00")), "Saga-Zahlbetrag um Vermögen gemindert");
            assertEquals("ANGELEGT", text(c, "SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE BG_ID = " + bgId));
            assertEquals(0, betrag(c, "SELECT ZAHLBETRAG FROM BESCHEID WHERE BG_ID = " + bgId)
                    .compareTo(new BigDecimal("363.00")), "Saga-Bescheid identisch zum Buchungsweg");
            long fallId = zahl(c, "SELECT ID FROM SOZIAL_FALL WHERE BG_ID = " + bgId);
            assertEquals(fallId, zahl(c, "SELECT FALL_ID FROM ZAHLUNG WHERE BG_ID = " + bgId),
                    "FALL_ID der einzigen Person gesetzt (vorher Konstante 1)");
            // Verbrauchsfortschreibung (§ 90, CR-015): Einsatz dokumentiert, Bestand gemindert
            assertEquals(0, betrag(c, "SELECT VERMOEGENSEINSATZ FROM ZAHLUNG WHERE BG_ID = " + bgId)
                    .compareTo(new BigDecimal("500.00")), "Einsatz an der Zahlung dokumentiert");
            assertEquals(0, betrag(c, "SELECT VERMOEGEN FROM BEDARFSGEMEINSCHAFT WHERE ID = " + bgId)
                    .compareTo(new BigDecimal("10000.00")), "Bestand auf Schonvermögen fortgeschrieben");
        }
    }

    /** CR-008: Nach Entbindung endet nur der Mehrbedarf, nicht der Grundanspruch. */
    @Test
    void entbindungsmonatBeendetSchwangerenMehrbedarf() throws Exception {
        var bg = service.legeBedarfsgemeinschaftAn("BG-CR-SCHW", "Clara Check",
                new BigDecimal("900.00"), null, "sb-test");
        SozialFall fall = new SozialFall(1, bg.id(), 1, false, true, 0, 0, 0, false,
                new BigDecimal("0.00"), new BigDecimal("0.00"), new BigDecimal("0.00"),
                new BigDecimal("0.00"), new BigDecimal("0.00"),
                new BigDecimal("0.00"), new BigDecimal("0.00"), new BigDecimal("0.00"),
                new BigDecimal("0.00"), "2026-01", 1L);
        service.legeSozialFallAn(bg.id(), fall, "sb-test");
        var ergebnis = service.ordneAuszahlungAn(new Auszahlungsauftrag(
                bg.id(), "CR-SCHW-1", "2026-03", "sb-test", false, null, null, null));
        assertEquals(0, ergebnis.zahlung().auszahlungsbetrag().compareTo(new BigDecimal("563.00")));
    }

    /** CR-009: Freigabe und Kassenlauf sind für historisierte Vorgänge gesperrt. */
    @Test
    void historisierteBgBleibtZahlbarGesperrt() throws Exception {
        var bg = service.legeBedarfsgemeinschaftAn("BG-CR-HIST", "Heinrich Check",
                new BigDecimal("900.00"), null, "sb-test");
        SozialFall fall = new SozialFall(1, bg.id(), 1, false, false,
                new BigDecimal("300.00"), new BigDecimal("0.00"), new BigDecimal("0.00"),
                new BigDecimal("0.00"), new BigDecimal("0.00"), 1L);
        service.legeSozialFallAn(bg.id(), fall, "sb-test");
        var ergebnis = service.ordneAuszahlungAn(new Auszahlungsauftrag(
                bg.id(), "CR-HIST-1", "2026-10", "sb-test", false, null, null, null));
        long zahlungId = ergebnis.zahlung().id();

        // Historisierung (1 → 0) ist erlaubt … (Budget-Minderung hat die VERSION erhöht)
        long versionNachBuchung = service.ladeDetail(bg.id()).bedarfsgemeinschaft().version();
        service.aktualisiereBedarfsgemeinschaft(bg.id(), false, null, versionNachBuchung);
        // … die Freigabe danach nicht mehr:
        FachlicheException freigabe = assertThrows(FachlicheException.class,
                () -> service.freigebenZahlung(zahlungId, "sb-zweit"));
        assertTrue(freigabe.getMessage().contains("historisiert"), freigabe.getMessage());
        // … die Reaktivierung über die Maske ebenso wenig:
        var geladen = service.ladeDetail(bg.id()).bedarfsgemeinschaft();
        FachlicheException reaktivierung = assertThrows(FachlicheException.class,
                () -> service.aktualisiereBedarfsgemeinschaft(bg.id(), true, null, geladen.version()));
        assertTrue(reaktivierung.getMessage().contains("reaktiviert"), reaktivierung.getMessage());
    }

    /** CR-009: Der Kassenlauf übernimmt keine BEREIT-Zahlungen historisierter Vorgänge. */
    @Test
    void kassenlaufUeberspringtHistorisierte() throws Exception {
        var bg = service.legeBedarfsgemeinschaftAn("BG-CR-KASSE", "Karin Check",
                new BigDecimal("900.00"), null, "sb-test");
        SozialFall fall = new SozialFall(1, bg.id(), 1, false, false,
                new BigDecimal("300.00"), new BigDecimal("0.00"), new BigDecimal("0.00"),
                new BigDecimal("0.00"), new BigDecimal("0.00"), 1L);
        service.legeSozialFallAn(bg.id(), fall, "sb-test");
        var ergebnis = service.ordneAuszahlungAn(new Auszahlungsauftrag(
                bg.id(), "CR-KASSE-1", "2026-10", "sb-test", false, null, null, null));
        service.freigebenZahlung(ergebnis.zahlung().id(), "sb-zweit");
        long versionNachBuchung = service.ladeDetail(bg.id()).bedarfsgemeinschaft().version();
        service.aktualisiereBedarfsgemeinschaft(bg.id(), false, null, versionNachBuchung);
        var fremd = service.legeBedarfsgemeinschaftAn("BG-CR-FREMDE-KASSE", "Fremde vorbereitete Zahlung",
                new BigDecimal("900.00"), null, "sb-test");
        service.legeSozialFallAn(fremd.id(), new SozialFall(1, fremd.id(), 1, false, false,
                new BigDecimal("300.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 1L), "sb-test");
        var fremdeZahlung = service.ordneAuszahlungAn(new Auszahlungsauftrag(fremd.id(), "CR-FREMDE-KASSE",
                "2026-10", "sb-test")).zahlung();
        service.freigebenZahlung(fremdeZahlung.id(), "sb-zweit");
        var kassenlauf = service.uebergebeBereiteZahlungen("sb-kasse", bg.aktenzeichen());
        assertEquals(0, kassenlauf.anzahl(), "Historisierte BEREIT-Zahlung wird nicht übergeben");
        assertEquals("BEREIT", service.ladeDetail(fremd.id()).zahlungen().getFirst().status(),
                "Fremde vorbereitete Zahlung bleibt unverändert");
        assertEquals("BEREIT", text(verbindung(),
                "SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = " + ergebnis.zahlung().id()));
    }

    // ------------------------------------------------------------------
    // Nachbesserung v0.5.9: F-01, F-02, F-04
    // ------------------------------------------------------------------

    private long bgMitFall(String aktenzeichen) {
        var bg = service.legeBedarfsgemeinschaftAn(aktenzeichen, "Nora Nachprüfung",
                new BigDecimal("5000.00"), null, "sb-test");
        SozialFall fall = new SozialFall(1, bg.id(), 1, false, false,
                new BigDecimal("300.00"), new BigDecimal("0.00"), new BigDecimal("0.00"),
                new BigDecimal("0.00"), new BigDecimal("0.00"), 1L);
        service.legeSozialFallAn(bg.id(), fall, "sb-test");
        return bg.id();
    }

    /** F-01: Mit vorhandenem Bescheid ist nur ein vom Bescheid gedeckter Monat zahlbar. */
    @Test
    void zahlungAusserhalbDesBewilligungszeitraumsWirdAbgelehnt() {
        long bgId = bgMitFall("BG-CR-ZEITRAUM");
        service.erteileBescheid(bgId, "2026-10", "2026-10", "sb-test");
        FachlicheException ausserhalb = assertThrows(FachlicheException.class,
                () -> service.ordneAuszahlungAn(new Auszahlungsauftrag(
                        bgId, "CR-ZR-2", "2026-12", "sb-test", false, null, null, null)));
        assertEquals(FachlicheException.VALIDATION, ausserhalb.code());
        var gedeckt = service.ordneAuszahlungAn(new Auszahlungsauftrag(
                bgId, "CR-ZR-1", "2026-10", "sb-test", false, null, null, null));
        assertEquals("ANGELEGT", gedeckt.zahlung().status());
    }

    /** F-02: Eine stornierte Zahlung wird durch Wiederholung nicht stillschweigend „erneut“ gebucht. */
    @Test
    void wiederholungEinerStorniertenZahlungWirdAbgelehnt() {
        long bgId = bgMitFall("BG-CR-STORNOREPLAY");
        var angelegt = service.ordneAuszahlungAn(new Auszahlungsauftrag(
                bgId, "CR-SR-1", "2026-10", "sb-test", false, null, null, null));
        service.storniereZahlung(angelegt.zahlung().id(), "sb-admin");
        FachlicheException fehler = assertThrows(FachlicheException.class,
                () -> service.ordneAuszahlungAn(new Auszahlungsauftrag(
                        bgId, "CR-SR-1", "2026-10", "sb-test", false, null, null, null)));
        assertEquals(FachlicheException.VALIDATION, fehler.code());
    }

    /** F-04: Scheitert Schritt 1 an einem fremden Aktenzeichen, bleibt der fremde Vorgang unberührt. */
    @Test
    void sagaMitFremdemAktenzeichenHistorisiertDenFremdenVorgangNicht() throws Exception {
        long fremd = bgMitFall("BG-CR-FREMD");
        assertThrows(RuntimeException.class,
                () -> sagas.run(AntragsSagaProzess.definition(), sagaPayload("BG-CR-FREMD", "0", "2026-10")));
        try (Connection c = verbindung()) {
            assertEquals(1, zahl(c, "SELECT STATUS_AKTIV FROM BEDARFSGEMEINSCHAFT WHERE ID = " + fremd),
                    "fremder Vorgang bleibt aktiv — Kompensation greift nur auf eigene Anlage");
        }
    }

    // ------------------------------------------------------------------
    // Helfer
    // ------------------------------------------------------------------

    private static Connection verbindung() throws Exception {
        Connection c = DriverManager.getConnection(
                System.getenv("TX_URL"), System.getenv("TX_USER"), System.getenv("TX_PASS"));
        c.setAutoCommit(true);
        return c;
    }

    private static long bgId(String aktenzeichen) throws Exception {
        try (Connection c = verbindung();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT ID FROM BEDARFSGEMEINSCHAFT WHERE AKTENZEICHEN = ?")) {
            ps.setString(1, aktenzeichen);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private static BigDecimal betrag(Connection c, String sql) throws Exception {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getBigDecimal(1);
        }
    }

    private static String text(Connection c, String sql) throws Exception {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getString(1);
        }
    }

    private static long zahl(Connection c, String sql) throws Exception {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }
}
