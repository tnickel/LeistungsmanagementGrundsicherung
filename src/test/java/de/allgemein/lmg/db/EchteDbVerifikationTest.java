package de.allgemein.lmg.db;

import de.allgemein.lmg.service.Auszahlungsauftrag;
import de.allgemein.lmg.service.FachlicheException;
import de.allgemein.lmg.service.SozialLeistungsService;
import de.allgemein.lmg.service.Systemdiagnose;
import de.allgemein.lmg.model.SozialFall;
import de.allgemein.tx.SimpleConnectionPool;
import de.allgemein.tx.TaskController;
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
import java.sql.Statement;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifikation der Fachschicht gegen eine echte Oracle-Datenbank (Roadmap-Etappe 1).
 * Läuft nur, wenn TX_URL gesetzt ist — der normale `mvn test` bleibt also ohne DB grün:
 *
 * <pre>
 *   TX_URL=jdbc:oracle:thin:@//localhost:1521/FREEPDB1 \
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
            st.executeUpdate("DELETE FROM ZAHLUNG WHERE BG_ID IN "
                    + "(SELECT ID FROM BEDARFSGEMEINSCHAFT WHERE AKTENZEICHEN LIKE 'BG-TEST-%')");
            st.executeUpdate("DELETE FROM SOZIAL_FALL WHERE BG_ID IN "
                    + "(SELECT ID FROM BEDARFSGEMEINSCHAFT WHERE AKTENZEICHEN LIKE 'BG-TEST-%')");
            st.executeUpdate("DELETE FROM JOURNAL WHERE BG_ID BETWEEN 9901 AND 9911");
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
        assertEquals("BEREIT", ergebnis.zahlung().status());
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
        assertEquals("BEREIT", zahlung.status());

        var ergebnis = service.uebergebeBereiteZahlungen("admin-01");
        assertTrue(ergebnis.anzahl() >= 1, "mindestens die eigene Zahlung sollte übergeben werden");
        var pruefung = service.letzteZahlungen(50).stream()
                .filter(z -> z.id() == zahlung.id()).findFirst().orElseThrow();
        assertEquals("UEBERGEBEN", pruefung.status());
    }

    private SozialFall neuerFallEntwurf(long bgId) {
        // RBS 1 ohne Mehrbedarf, ohne KdU, ohne Einkommen -> Zahlbetrag exakt 563,00
        return new SozialFall(0, bgId, 1, false, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, 1);
    }
}
