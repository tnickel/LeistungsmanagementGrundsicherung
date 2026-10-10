package de.allgemein.lmg.db;

import de.allgemein.lmg.service.Auszahlungsauftrag;
import de.allgemein.lmg.service.FachlicheException;
import de.allgemein.lmg.service.SozialLeistungsService;
import de.allgemein.lmg.service.Systemdiagnose;
import de.allgemein.lmg.model.SozialFall;
import de.allgemein.tx.SimpleConnectionPool;
import de.allgemein.tx.TaskController;
import de.allgemein.lmg.model.Bescheid;
import de.allgemein.lmg.saga.AntragsSagaProzess;
import de.allgemein.lmg.web.Json;
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
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifikation der Fachschicht gegen eine echte Oracle-Datenbank (Roadmap-Etappe 1).
 * Läuft nur, wenn TX_URL gesetzt ist — der normale `mvn test` bleibt also ohne DB grün:
 *
 * <pre>
 *   TX_URL=jdbc:oracle:thin:@//localhost:1921/o19c1p1 \
 *   TX_USER=tx_demo TX_PASS=tx_demo \
 *   mvn test -Dtest=EchteDbVerifikationTest
 * </pre>
 *
 * Je Testfall wird ein eigener Vorgang (IDs 99xx) angelegt und aufgeräumt;
 * der Seed-Datenbestand bleibt unverändert. Zahlbetrag je Fall:
 * 563,00 (RBS 1) + 95,71 (Merkzeichen G) + 150,00 (KdU) = 808,71 EUR.
 */
@EnabledIfEnvironmentVariable(named = "TX_URL", matches = ".+")
class EchteDbVerifikationTest {

    private static SimpleConnectionPool pool;
    private static SozialLeistungsService service;

    @BeforeAll
    static void aufbauen() throws Exception {
        Class.forName("oracle.jdbc.OracleDriver");
        String url = System.getenv("TX_URL");
        String user = System.getenv("TX_USER");
        String pass = System.getenv("TX_PASS");
        pool = new SimpleConnectionPool(url, user, pass, 4);
        service = new SozialLeistungsService(new TxManager(pool));
        aufraeumen();
        legeVorgangAn(9901L, "900.00"); // ausreichend Budget für erfolgreiche Zahlung
        legeVorgangAn(9902L, "900.00"); // Idempotenz
        legeVorgangAn(9903L, "800.00"); // Budget zu knapp -> BUDGET_ERSCHOEPFT
        legeVorgangAn(9904L, "900.00"); // optimistisches Locking
        legeVorgangAn(9907L, "2000.00"); // Monatsprüfung
    }

    @AfterAll
    static void aufraeumen() throws Exception {
        // Aufräumen nach Aktenzeichen-Muster: die angelegten Vorgänge bekommen ihre
        // IDs aus SEQ_BG (1000+), daher ist ein fester ID-Bereich nicht ausreichend
        try (Connection c = DriverManager.getConnection(
                System.getenv("TX_URL"), System.getenv("TX_USER"), System.getenv("TX_PASS"))) {
            c.setAutoCommit(false);
            Statement st = c.createStatement();
            st.executeUpdate("DELETE FROM JOURNAL WHERE BG_ID IN "
                    + "(SELECT ID FROM BEDARFSGEMEINSCHAFT WHERE AKTENZEICHEN LIKE 'BG-TEST-%')");
            st.executeUpdate("DELETE FROM BESCHEID WHERE BG_ID IN "
                    + "(SELECT ID FROM BEDARFSGEMEINSCHAFT WHERE AKTENZEICHEN LIKE 'BG-TEST-%')");
            st.executeUpdate("DELETE FROM ZAHLUNG WHERE BG_ID IN "
                    + "(SELECT ID FROM BEDARFSGEMEINSCHAFT WHERE AKTENZEICHEN LIKE 'BG-TEST-%')");
            st.executeUpdate("DELETE FROM SOZIAL_FALL WHERE BG_ID IN "
                    + "(SELECT ID FROM BEDARFSGEMEINSCHAFT WHERE AKTENZEICHEN LIKE 'BG-TEST-%')");
            st.executeUpdate("DELETE FROM JOURNAL WHERE BG_ID BETWEEN 9901 AND 9911");
            st.executeUpdate("DELETE FROM BESCHEID WHERE BG_ID BETWEEN 9901 AND 9911");
            st.executeUpdate("DELETE FROM ZAHLUNG WHERE BG_ID BETWEEN 9901 AND 9911");
            st.executeUpdate("DELETE FROM SOZIAL_FALL WHERE BG_ID BETWEEN 9901 AND 9911");
            st.executeUpdate("DELETE FROM BEDARFSGEMEINSCHAFT WHERE ID BETWEEN 9901 AND 9911 "
                    + "OR AKTENZEICHEN LIKE 'BG-TEST-%'");
            c.commit();
            st.close();
        }
        if (pool != null) {
            pool.close();
        }
    }

    private static void legeVorgangAn(long id, String budget) throws Exception {
        try (Connection c = DriverManager.getConnection(
                System.getenv("TX_URL"), System.getenv("TX_USER"), System.getenv("TX_PASS"))) {
            c.setAutoCommit(false);
            PreparedStatement bg = c.prepareStatement(
                    "INSERT INTO BEDARFSGEMEINSCHAFT (ID, AKTENZEICHEN, HAUPTPERSON_NAME, STATUS_AKTIV, BUDGET_REST) "
                            + "VALUES (?, ?, 'Verifikation, Testfall', 1, ?)");
            PreparedStatement fall = c.prepareStatement(
                    "INSERT INTO SOZIAL_FALL (ID, BG_ID, REGELBEDARFSSTUFE, FLAG_MERKZEICHEN_G, "
                            + "KDU_MIETE, KDU_HEIZUNG, EINKOMMEN_RENTE, EINKOMMEN_ERWERB) "
                            + "VALUES (?, ?, 1, 1, 100, 50, 0, 0)");
            bg.setLong(1, id);
            bg.setString(2, "BG-TEST-" + id);
            bg.setBigDecimal(3, new BigDecimal(budget));
            bg.executeUpdate();
            fall.setLong(1, 9900L + (id % 100));
            fall.setLong(2, id);
            fall.executeUpdate();
            c.commit();
            bg.close();
            fall.close();
        }
    }

    @Test
    void zahlungWirdGebuchtUndBudgetGemindert() {
        var ergebnis = service.ordneAuszahlungAn(
                new Auszahlungsauftrag(9901L, UUID.randomUUID().toString(), "2026-09", "sb-test"));
        assertEquals(new BigDecimal("808.71"), ergebnis.zahlung().auszahlungsbetrag());
        assertEquals(0, new BigDecimal("91.29").compareTo(ergebnis.bedarfsgemeinschaft().budgetRest()));
        // Vier-Augen-Prinzip ist serverseitig erzwungen: jede Zahlung startet ANGELEGT
        // und wird erst durch freigebenZahlung (zweiter Bearbeiter) BEREIT.
        assertEquals("ANGELEGT", ergebnis.zahlung().status());
    }

    @Test
    void gleicheVorgangsnummerWirdNichtDoppeltGebucht() {
        String vorgangsNr = UUID.randomUUID().toString();
        var erste = service.ordneAuszahlungAn(
                new Auszahlungsauftrag(9902L, vorgangsNr, "2026-09", "sb-test"));
        var zweite = service.ordneAuszahlungAn(
                new Auszahlungsauftrag(9902L, vorgangsNr, "2026-09", "sb-andere"));
        assertTrue(zweite.bereitsGebucht());
        assertEquals(erste.zahlung().id(), zweite.zahlung().id());
    }

    @Test
    void zweiteZahlungFuerDenSelbenMonatWirdAbgelehnt() {
        service.ordneAuszahlungAn(
                new Auszahlungsauftrag(9907L, UUID.randomUUID().toString(), "2026-09", "sb-test"));
        var ex = assertThrows(FachlicheException.class, () -> service.ordneAuszahlungAn(
                new Auszahlungsauftrag(9907L, UUID.randomUUID().toString(), "2026-09", "sb-test")));
        assertEquals(FachlicheException.VALIDATION, ex.code());
        // anderer Monat bleibt möglich
        var folgemonat = service.ordneAuszahlungAn(
                new Auszahlungsauftrag(9907L, UUID.randomUUID().toString(), "2026-10", "sb-test"));
        assertEquals("ANGELEGT", folgemonat.zahlung().status());
    }

    @Test
    void zahlbetragUeberBudgetFuehrtZuFachlichemAbbruch() {
        // Budget 800,00 reicht nicht für 808,71
        var ausnahme = assertThrows(FachlicheException.class, () -> service.ordneAuszahlungAn(
                new Auszahlungsauftrag(9903L, UUID.randomUUID().toString(), "2026-09", "sb-test")));
        assertEquals(FachlicheException.BUDGET_ERSCHOEPFT, ausnahme.code());
    }

    @Test
    void maskenSpeichernMitAlterVersionFuehrtZuKonkurrenz() {
        var detail = service.ladeDetail(9904L);
        long version = detail.faelle().get(0).version();
        service.aktualisiereSozialFall(detail.faelle().get(0), version); // erster Speicher klappt
        var ausnahme = assertThrows(FachlicheException.class,
                () -> service.aktualisiereSozialFall(detail.faelle().get(0), version));
        assertEquals(FachlicheException.KONKURRENZ, ausnahme.code());
    }

    @Test
    void systemdiagnoseMeldetDatenbankUndBestand() {
        var txManager = new TxManager(pool);
        try (TaskController tasks = new TaskController(txManager, 1, 1)) {
            var diagnose = new Systemdiagnose(txManager, tasks,
                    new SagaEngine(txManager, new SagaJournal(pool)), Instant.now());
            var status = diagnose.status();
            assertTrue(status.dbErreichbar(), "DB sollte erreichbar sein: " + status.dbFehler());
            assertTrue(status.anzahlVorgaenge() >= 4, "Seed- plus Testvorgänge erwartet");
            assertTrue(status.dbAntwortMillis() >= 0);
            assertTrue(status.dbZeitpunkt() != null && status.dbZeitpunkt().length() == 19);
        }
    }

    // ------------------------------------------------------------------
    // Betriebsfunktionen: Neuanlage, Storno, Kassenlauf
    // ------------------------------------------------------------------

    private static final BigDecimal ZAHLBETRAG_RBS1 = new BigDecimal("563.00");

    @Test
    void vorgangUndSozialFallAnlegenAuszahlenStornieren() {
        var bg = service.legeBedarfsgemeinschaftAn("BG-TEST-9905-" + System.nanoTime() % 100000,
                "Ablauf, Testfall", new BigDecimal("700.00"), null, "sb-test");
        var fall = service.legeSozialFallAn(bg.id(), neuerFallEntwurf(bg.id()), "sb-test");

        var zahlung = service.ordneAuszahlungAn(new Auszahlungsauftrag(bg.id(),
                UUID.randomUUID().toString(), "2026-10", "sb-test")).zahlung();
        assertEquals(ZAHLBETRAG_RBS1, zahlung.auszahlungsbetrag());

        var storniert = service.storniereZahlung(zahlung.id(), "sb-test");
        assertEquals("STORNIERT", storniert.status());
        var detail = service.ladeDetail(bg.id());
        assertEquals(0, new BigDecimal("700.00").compareTo(detail.bedarfsgemeinschaft().budgetRest()));
    }

    @Test
    void kassenlaufSetztBereiteZahlungenAufUebergeben() {
        var bg = service.legeBedarfsgemeinschaftAn("BG-TEST-9906-" + System.nanoTime() % 100000,
                "Kassenlauf, Testfall", new BigDecimal("700.00"), null, "sb-test");
        service.legeSozialFallAn(bg.id(), neuerFallEntwurf(bg.id()), "sb-test");
        var zahlung = service.ordneAuszahlungAn(new Auszahlungsauftrag(bg.id(),
                UUID.randomUUID().toString(), "2026-10", "sb-test")).zahlung();
        // Vier-Augen-Prinzip: Anordnung erzeugt ANGELEGT, die Freigabe erfolgt durch
        // einen zweiten Bearbeiter — erst danach nimmt der Kassenlauf die Zahlung.
        assertEquals("ANGELEGT", zahlung.status());
        var freigegeben = service.freigebenZahlung(zahlung.id(), "sb-freigabe");
        assertEquals("BEREIT", freigegeben.status());

        var ergebnis = service.uebergebeBereiteZahlungen("admin-01");
        assertTrue(ergebnis.anzahl() >= 1, "mindestens die eigene Zahlung sollte übergeben werden");
        var pruefung = service.letzteZahlungen(50).stream()
                .filter(z -> z.id() == zahlung.id()).findFirst().orElseThrow();
        assertEquals("UEBERGEBEN", pruefung.status());
    }

    @Test
    void vierAugenPrinzipVerhindertSelbstfreigabeUndErmoeglichtZweitpruefung() {
        var bg = service.legeBedarfsgemeinschaftAn("BG-TEST-4AUGEN-" + System.nanoTime() % 100000,
                "VierAugen, Max", new BigDecimal("1000.00"), null,
                "DE89370400440532013000", "MARKDEF1100", "Max VierAugen", "sb-erfasser");
        service.legeSozialFallAn(bg.id(), neuerFallEntwurf(bg.id()), "sb-erfasser");

        // Zahlung mit Vier-Augen-Pflicht anordnen -> Status ANGELEGT
        var auftrag = new Auszahlungsauftrag(bg.id(), UUID.randomUUID().toString(),
                "2026-11", "sb-erfasser", true, null, null, null);
        var ergebnis = service.ordneAuszahlungAn(auftrag);
        var zahlung = ergebnis.zahlung();
        assertEquals("ANGELEGT", zahlung.status());
        assertEquals("sb-erfasser", zahlung.sachbearbeiterId());
        assertEquals("DE89370400440532013000", zahlung.empfaengerIban());

        // Selbstfreigabe durch denselben Sachbearbeiter wird abgewehrt
        var ex = assertThrows(FachlicheException.class,
                () -> service.freigebenZahlung(zahlung.id(), "sb-erfasser"));
        assertEquals(FachlicheException.BERECHTIGUNG, ex.code());

        // Zweitprüfung / Freigabe durch Kollegin sb-pruefer
        var freigegeben = service.freigebenZahlung(zahlung.id(), "sb-pruefer");
        assertEquals("BEREIT", freigegeben.status());
        assertEquals("sb-pruefer", freigegeben.freigegebenVon());
    }

    @Test
    void ungueltigeIbanWirdBeimAuszahlenAbgelehnt() {
        var bg = service.legeBedarfsgemeinschaftAn("BG-TEST-IBAN-" + System.nanoTime() % 100000,
                "Iban, Falsch", new BigDecimal("1000.00"), null, "sb-test");
        service.legeSozialFallAn(bg.id(), neuerFallEntwurf(bg.id()), "sb-test");

        var auftrag = new Auszahlungsauftrag(bg.id(), UUID.randomUUID().toString(),
                "2026-11", "sb-test", false, "DE00000000000000000000", null, null);
        var ex = assertThrows(FachlicheException.class, () -> service.ordneAuszahlungAn(auftrag));
        assertEquals(FachlicheException.VALIDATION, ex.code());
    }

    @Test
    void bescheidErteilenUndDruckenHtmlVerifizieren() {
        String az = "BG-TEST-BESCH-" + System.nanoTime() % 100000;
        var bg = service.legeBedarfsgemeinschaftAn(az, "Bescheidempfaenger, Bernd",
                new BigDecimal("2000.00"), null,
                "DE89370400440532013000", "MARKDEF1100", "Bescheidempfaenger, Bernd", "sb-erfasser");

        // Fall mit RBS 1 (563 €) + KdU (400 Kalt + 80 Betrieb + 70 Heizung = 550) = 1.113,00 €
        SozialFall fall = new SozialFall(0, bg.id(), 1, false, false,
                new BigDecimal("400.00"), new BigDecimal("80.00"), new BigDecimal("70.00"),
                BigDecimal.ZERO, BigDecimal.ZERO, 1);
        service.legeSozialFallAn(bg.id(), fall, "sb-erfasser");

        // Bescheid erteilen
        Bescheid bescheid = service.erteileBescheid(bg.id(), "2026-01", "2026-12", "sb-pruefer");
        assertEquals("ERTEILT", bescheid.status());
        assertEquals(new BigDecimal("1113.00"), bescheid.zahlbetrag());
        assertEquals("2026-01", bescheid.bewilligungVon());
        assertEquals("2026-12", bescheid.bewilligungBis());
        assertTrue(bescheid.bescheidNr().contains(az));

        // Letzten Bescheid über Service laden
        var geladen = service.letzterBescheid(bg.id());
        assertTrue(geladen.isPresent());
        assertEquals(bescheid.id(), geladen.get().id());

        // HTML-Druckansicht generieren und prüfen
        String html = service.generiereBescheidHtml(bg.id());
        assertTrue(html.contains("Bewilligungsbescheid"), "HTML muss Bescheidtitel enthalten");
        assertTrue(html.contains("§ 37 Abs. 2 SGB X"), "HTML muss die Bekanntgabefiktion § 37 Abs. 2 SGB X nennen");
        assertTrue(html.contains("§ 35 SGB X"), "HTML muss Begründung nach § 35 SGB X enthalten");
        assertTrue(html.contains("§ 36 SGB X"), "HTML muss Rechtsbehelfsbelehrung nach § 36 SGB X enthalten");
        assertTrue(html.contains("1.113,00"), "HTML muss Zahlbetrag 1.113,00 enthalten");
        assertTrue(html.contains(az), "HTML muss Aktenzeichen enthalten");
        assertTrue(html.contains("DE89 3704 0044 0532 0130 00"), "HTML muss formatierte IBAN enthalten");
        assertTrue(html.contains("@media print"), "HTML muss Druck-Stylesheet enthalten");
    }

    @Test
    void bescheidDruckZeigtSnapshotAuchNachFallaenderung() {
        String az = "BG-TEST-SNAP-" + System.nanoTime() % 100000;
        var bg = service.legeBedarfsgemeinschaftAn(az, "Snapshot, Sigrid",
                new BigDecimal("2000.00"), null, null, null, null, "sb-erfasser");

        // RBS 1 (563 €) + KdU 300 € = 863,00 € Bedarf; Rente 300 € → Zahlbetrag 563,00 €
        SozialFall fall = new SozialFall(0, bg.id(), 1, false, false,
                new BigDecimal("300.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("300.00"), BigDecimal.ZERO, 1);
        service.legeSozialFallAn(bg.id(), fall, "sb-erfasser");

        Bescheid bescheid = service.erteileBescheid(bg.id(), "2026-01", "2026-12", "sb-pruefer");
        assertEquals(new BigDecimal("563.00"), bescheid.zahlbetrag());
        assertNotNull(bescheid.berechnung(), "Berechnungs-Snapshot muss mitgeschrieben werden");

        // Fall NACH Erteilung ändern (Rente 300 → 700): der Nachdruck bleibt beim
        // Erteilungsstand — kein Mischverhältnis aus altem Zahlbetrag und neuen Zeilen
        SozialFall geladen = service.ladeDetail(bg.id()).faelle().get(0);
        SozialFall geaendert = new SozialFall(geladen.id(), geladen.bgId(), geladen.regelbedarfsstufe(),
                geladen.merkzeichenG(), geladen.schwanger(), geladen.anzKinderUnter7(),
                geladen.anzKinderUnter16(), geladen.anzKinderMinderjaehrig(), geladen.warmwasser(),
                geladen.kduMiete(), geladen.kduBetriebskosten(), geladen.kduHeizung(),
                new BigDecimal("700.00"), geladen.einkommenErwerb(),
                geladen.abzSteuern(), geladen.abzSozialversicherung(), geladen.abzVersicherungen(),
                geladen.abzErwerbsaufwendungen(), geladen.version());
        service.aktualisiereSozialFall(geaendert, geladen.version(), "sb-aenderer");

        String html = service.generiereBescheidHtml(bg.id());
        assertTrue(html.contains("Rente 300,00"), "Snapshot-Rente aus dem Erteilungszeitpunkt muss bleiben");
        assertFalse(html.contains("Rente 700,00"), "Geänderte Rente darf im Nachdruck nicht auftauchen");
        assertFalse(html.contains("kein Berechnungs-Snapshot"), "Snapshot vorhanden, kein Warnhinweis nötig");
        assertTrue(html.contains("563,00"), "Bewilligter Zahlbetrag muss im Dokument stehen");
    }

    @Test
    void antragsSagaE2EErfolgreichUndKompensation() throws Exception {
        TxManager txManager = new TxManager(pool);
        SagaEngine sagas = new SagaEngine(txManager, new SagaJournal(pool));

        // 1. Erfolgsfall: 4 Schritte (Vorgang erfassen -> Bedarfsprüfung -> Bescheid -> Erstauszahlung)
        String azErfolg = "BG-TEST-SAGA-OK-" + System.nanoTime() % 100000;
        String payloadErfolg = erstelleSagaPayload(azErfolg, "Saga-Erfolg, Erwin",
                new BigDecimal("5000.00"), "2026-10", "DE89370400440532013000", "MARKDEF1100",
                1, new BigDecimal("450.00"), new BigDecimal("90.00"), new BigDecimal("80.00"),
                BigDecimal.ZERO, BigDecimal.ZERO);

        String sagaId = sagas.run(AntragsSagaProzess.definition(), payloadErfolg);
        assertTrue(sagaId != null && !sagaId.isBlank(), "Saga-ID muss vergeben sein");

        // Verifizieren, dass BG, Fall, Bescheid und Zahlung angelegt wurden
        var bgErfolg = service.listeBedarfsgemeinschaften().stream()
                .filter(b -> b.aktenzeichen().equals(azErfolg))
                .findFirst().orElseThrow();
        var detail = service.ladeDetail(bgErfolg.id());
        assertEquals(1, detail.faelle().size());
        assertTrue(detail.bescheid() != null, "Bescheid muss erteilt sein");
        assertEquals("ERTEILT", detail.bescheid().status());
        assertEquals(1, detail.zahlungen().size(), "Erstauszahlung muss angelegt sein");
        // Vier-Augen-Prinzip gilt auch im Saga-Weg: Die Zahlung wartet als ANGELEGT auf
        // die Freigabe durch einen zweiten Sachbearbeiter (FREIGEGEBEN_VON ist leer).
        assertEquals("ANGELEGT", detail.zahlungen().get(0).status());
        assertEquals(0, new BigDecimal("1183.00").compareTo(detail.zahlungen().get(0).auszahlungsbetrag()));

        // 2. Kompensationsfall: Hohes Einkommen (Rente 5000 €) übersteigt Gesamtbedarf
        // Schritt 1 committet -> Schritt 2 wirft Fehler -> Schritt 1 wird rückwärts kompensiert
        String azFehler = "BG-TEST-SAGA-ERR-" + System.nanoTime() % 100000;
        String payloadFehler = erstelleSagaPayload(azFehler, "Saga-Fehler, Fiona",
                new BigDecimal("5000.00"), "2026-10", "DE89370400440532013000", "MARKDEF1100",
                1, new BigDecimal("450.00"), new BigDecimal("90.00"), new BigDecimal("80.00"),
                new BigDecimal("5000.00"), BigDecimal.ZERO);

        assertThrows(Exception.class, () -> sagas.run(AntragsSagaProzess.definition(), payloadFehler));

        // Nach Kompensation ist der Vorgang HISTORISIERT (§ 67 SGB X): Sozialdaten
        // bleiben revisionssicher erhalten und FKs von Bescheid/Zahlung intakt —
        // gelöscht wird nichts, der Vorgang wird nur inaktiv gesetzt.
        try (Connection c = verbindung();
             PreparedStatement stmt = c.prepareStatement(
                     "SELECT STATUS_AKTIV FROM BEDARFSGEMEINSCHAFT WHERE AKTENZEICHEN = ?")) {
            stmt.setString(1, azFehler);
            try (ResultSet rs = stmt.executeQuery()) {
                assertTrue(rs.next(), "Vorgang muss nach Kompensation (historisiert) existieren");
                assertEquals(0, rs.getInt(1), "Vorgang muss STATUS_AKTIV = 0 haben");
            }
        }
    }

    // ------------------------------------------------------------------
    // Wiederanlauf (Recover) der Fach-Saga nach simuliertem Absturz
    // ------------------------------------------------------------------

    /**
     * Stellt einen Absturz mitten im 4. Schritt der Fach-Saga per JDBC nach:
     * Journal-Kopf RUNNING, Schritte 0–2 DONE, Schritt 3 (Erstauszahlung) IN_FLIGHT —
     * der Fachschritt war „möglicherweise committet": Zahlung existiert (ANGELEGT),
     * das Restbudget ist gemindert. Der Wiederanlauf über ALLE Definitionen
     * (Systemdiagnose) muss die Saga konservativ kompensieren: Zahlung storniert,
     * Restbudget zurückgebucht, Bescheid aufgehoben, Vorgang historisiert,
     * Journal-Endstatus COMPENSATED.
     */
    @Test
    void recoverDerFachSagaStorniertBuchtBudgetZurueckUndHistorisiert() throws Exception {
        TxManager txManager = new TxManager(pool);
        String sagaId = UUID.randomUUID().toString();
        String az = "BG-TEST-RECOVER-" + System.nanoTime() % 100000;
        BigDecimal zahlbetrag = new BigDecimal("1183.00"); // RBS 1 + KdU 450/90/80
        BigDecimal startBudget = new BigDecimal("5000.00");
        String vorgangsNr = "SAGA-" + sagaId;
        String payload = erstelleSagaPayload(az, "Recover, Rita", startBudget,
                "2026-10", "DE89370400440532013000", "MARKDEF1100", 1,
                new BigDecimal("450.00"), new BigDecimal("90.00"), new BigDecimal("80.00"),
                BigDecimal.ZERO, BigDecimal.ZERO);
        long bgId;
        try (Connection c = verbindung()) {
            c.setAutoCommit(false);
            bgId = naechsteId(c, "SEQ_BG");
            long fallId = naechsteId(c, "SEQ_FALL");
            long bescheidId = naechsteId(c, "SEQ_BESCHEID");
            long zahlungId = naechsteId(c, "SEQ_ZAHLUNG");

            // Journal: Kopf RUNNING, Schritte 0-2 DONE, Schritt 3 IN_FLIGHT (Write-Ahead)
            try (PreparedStatement saga = c.prepareStatement(
                    "INSERT INTO TX_SAGA (SAGA_ID, SAGA_TYPE, STATE, PAYLOAD) VALUES (?, ?, 'RUNNING', ?)");
                 PreparedStatement schritt = c.prepareStatement(
                         "INSERT INTO TX_SAGA_STEP (SAGA_ID, IDX, NAME, STATE) VALUES (?, ?, ?, ?)")) {
                saga.setString(1, sagaId);
                saga.setString(2, AntragsSagaProzess.SAGA_TYPE);
                saga.setString(3, payload);
                saga.executeUpdate();
                String[] namen = {"Vorgang erfassen", "Bedarfspruefung", "Bescheid erteilen",
                        "Erstauszahlung anweisen"};
                String[] zustaende = {"DONE", "DONE", "DONE", "IN_FLIGHT"};
                for (int i = 0; i < 4; i++) {
                    schritt.setString(1, sagaId);
                    schritt.setInt(2, i);
                    schritt.setString(3, namen[i]);
                    schritt.setString(4, zustaende[i]);
                    schritt.executeUpdate();
                }
            }

            // Fachdaten: Vorgang aktiv, Budget bereits gemindert, Bescheid erteilt,
            // Zahlung der Saga im Status ANGELEGT (Write-Ahead-Schritt „möglicherweise committet")
            try (PreparedStatement bg = c.prepareStatement(
                    "INSERT INTO BEDARFSGEMEINSCHAFT (ID, AKTENZEICHEN, HAUPTPERSON_NAME, STATUS_AKTIV, "
                            + "BUDGET_REST, IBAN, BIC, KONTOINHABER) VALUES (?, ?, ?, 1, ?, ?, ?, ?)");
                 PreparedStatement fall = c.prepareStatement(
                         "INSERT INTO SOZIAL_FALL (ID, BG_ID, REGELBEDARFSSTUFE, KDU_MIETE, "
                                 + "KDU_BETRIEBSKOSTEN, KDU_HEIZUNG) VALUES (?, ?, 1, 450, 90, 80)");
                 PreparedStatement bescheid = c.prepareStatement(
                         "INSERT INTO BESCHEID (ID, BG_ID, BESCHEID_NR, BEWILLIGUNG_VON, BEWILLIGUNG_BIS, "
                                 + "ZAHLBETRAG, STATUS, ERSTELLT_VON) VALUES (?, ?, ?, '2026-10', '2026-10', ?, 'ERTEILT', 'saga-engine')");
                 PreparedStatement zahlung = c.prepareStatement(
                         "INSERT INTO ZAHLUNG (ID, BG_ID, VORGANGS_NR, ZAHLUNGSMONAT, AUSZAHLUNGSBETRAG, "
                                 + "ZAHLUNGSSTATUS, SACHBEARBEITER_ID, EMPFAENGER_IBAN, EMPFAENGER_BIC, EMPFAENGER_NAME) "
                                 + "VALUES (?, ?, ?, '2026-10', ?, 'ANGELEGT', 'saga-sb', ?, ?, ?)")) {
                bg.setLong(1, bgId);
                bg.setString(2, az);
                bg.setString(3, "Recover, Rita");
                bg.setBigDecimal(4, startBudget.subtract(zahlbetrag));
                bg.setString(5, "DE89370400440532013000");
                bg.setString(6, "MARKDEF1100");
                bg.setString(7, "Recover, Rita");
                bg.executeUpdate();
                fall.setLong(1, fallId);
                fall.setLong(2, bgId);
                fall.executeUpdate();
                bescheid.setLong(1, bescheidId);
                bescheid.setLong(2, bgId);
                bescheid.setString(3, "BESCH-" + az);
                bescheid.setBigDecimal(4, zahlbetrag);
                bescheid.executeUpdate();
                zahlung.setLong(1, zahlungId);
                zahlung.setLong(2, bgId);
                zahlung.setString(3, vorgangsNr);
                zahlung.setBigDecimal(4, zahlbetrag);
                zahlung.setString(5, "DE89370400440532013000");
                zahlung.setString(6, "MARKDEF1100");
                zahlung.setString(7, "Recover, Rita");
                zahlung.executeUpdate();
            }
            c.commit();
        }

        try {
            // Wiederanlauf über ALLE bekannten Definitionen (Fach-Saga + Demo, Fund K4)
            try (TaskController tasks = new TaskController(txManager, 1, 1)) {
                Systemdiagnose diagnose = new Systemdiagnose(txManager, tasks,
                        new SagaEngine(txManager, new SagaJournal(pool)), Instant.now());
                var ergebnis = diagnose.wiederanlaufDetails();
                assertTrue(ergebnis.insgesamt() >= 1,
                        "mindestens die Test-Saga muss behandelt werden (je Typ: " + ergebnis.jeTyp() + ")");
            }

            // Fachliche Prüfungen nach der Kompensation
            try (Connection c = verbindung()) {
                assertEquals("COMPENSATED", abfrageText(c,
                        "SELECT STATE FROM TX_SAGA WHERE SAGA_ID = ?", sagaId),
                        "Saga muss im Journal mit COMPENSATED abgeschlossen sein");
                assertEquals("4", abfrageText(c,
                        "SELECT COUNT(*) FROM TX_SAGA_STEP WHERE SAGA_ID = ? AND STATE = 'COMPENSATED'",
                        sagaId), "alle 4 Schritte müssen kompensiert sein (inkl. IN_FLIGHT-Schritt 3)");
                assertEquals("STORNIERT", abfrageText(c,
                        "SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE VORGANGS_NR = ?", vorgangsNr),
                        "die Zahlung der Saga muss storniert sein");
                assertEquals(0, new BigDecimal("5000.00").compareTo(new BigDecimal(abfrageText(c,
                        "SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?", String.valueOf(bgId)))),
                        "Restbudget muss vollständig zurückgebucht sein");
                assertEquals("AUFGEHOBEN", abfrageText(c,
                        "SELECT STATUS FROM BESCHEID WHERE BG_ID = ?", String.valueOf(bgId)),
                        "Bescheid muss aufgehoben sein");
                assertEquals("0", abfrageText(c,
                        "SELECT STATUS_AKTIV FROM BEDARFSGEMEINSCHAFT WHERE ID = ?", String.valueOf(bgId)),
                        "Vorgang muss historisiert (STATUS_AKTIV = 0) sein — nicht gelöscht (§ 67 SGB X)");
            }
        } finally {
            // Testdaten aufräumen (Journal + Fachdaten dieses einen Vorgangs)
            try (Connection c = verbindung()) {
                c.setAutoCommit(false);
                try (Statement st = c.createStatement()) {
                    st.executeUpdate("DELETE FROM TX_SAGA_STEP WHERE SAGA_ID = '" + sagaId.replace("'", "''") + "'");
                    st.executeUpdate("DELETE FROM TX_SAGA WHERE SAGA_ID = '" + sagaId.replace("'", "''") + "'");
                    st.executeUpdate("DELETE FROM JOURNAL WHERE BG_ID = " + bgId);
                    st.executeUpdate("DELETE FROM BESCHEID WHERE BG_ID = " + bgId);
                    st.executeUpdate("DELETE FROM ZAHLUNG WHERE BG_ID = " + bgId);
                    st.executeUpdate("DELETE FROM SOZIAL_FALL WHERE BG_ID = " + bgId);
                    st.executeUpdate("DELETE FROM BEDARFSGEMEINSCHAFT WHERE ID = " + bgId);
                }
                c.commit();
            }
        }
    }

    private static Connection verbindung() throws Exception {
        Connection c = DriverManager.getConnection(
                System.getenv("TX_URL"), System.getenv("TX_USER"), System.getenv("TX_PASS"));
        c.setAutoCommit(true);
        return c;
    }

    private static long naechsteId(Connection c, String sequence) throws Exception {
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT " + sequence + ".NEXTVAL FROM DUAL")) {
            rs.next();
            return rs.getLong(1);
        }
    }

    /** Liest einen einzelnen Text-Wert (erster Parameter als String eingebunden). */
    private static String abfrageText(Connection c, String sql, String... parameter) throws Exception {
        try (PreparedStatement stmt = c.prepareStatement(sql)) {
            for (int i = 0; i < parameter.length; i++) {
                stmt.setString(i + 1, parameter[i]);
            }
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    private String erstelleSagaPayload(String aktenzeichen, String name, BigDecimal budget,
                                      String monat, String iban, String bic,
                                      int rbs, BigDecimal miete, BigDecimal betrieb,
                                      BigDecimal heizung, BigDecimal rente, BigDecimal erwerb) {
        Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("aktenzeichen", aktenzeichen);
        map.put("hauptpersonName", name);
        map.put("startBudget", budget);
        map.put("zahlungsmonat", monat);
        map.put("iban", iban);
        map.put("bic", bic);
        map.put("regelbedarfsstufe", rbs);
        map.put("kduMiete", miete);
        map.put("kduBetriebskosten", betrieb);
        map.put("kduHeizung", heizung);
        map.put("einkommenRente", rente);
        map.put("einkommenErwerb", erwerb);
        map.put("sachbearbeiterId", "saga-sb");
        return Json.stringify(map);
    }

    private SozialFall neuerFallEntwurf(long bgId) {
        // RBS 1 ohne Mehrbedarf, ohne KdU, ohne Einkommen -> Zahlbetrag exakt 563,00
        return new SozialFall(0, bgId, 1, false, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, 1);
    }
}
