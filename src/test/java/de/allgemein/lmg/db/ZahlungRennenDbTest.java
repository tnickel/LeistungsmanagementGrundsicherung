package de.allgemein.lmg.db;

import de.allgemein.lmg.model.SozialFall;
import de.allgemein.lmg.service.Auszahlungsauftrag;
import de.allgemein.lmg.service.FachlicheException;
import de.allgemein.lmg.service.SozialLeistungsService;
import de.allgemein.tx.SimpleConnectionPool;
import de.allgemein.tx.TxManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Echte Rennbedingungen im Fachschema (Testlücken L-16/L-19): Drei Threads auf
 * dieselbe Zahlung (Freigabe ∥ Storno ∥ Kassenlauf), zwei parallele Stornos und
 * zwei parallele Anordnungen für denselben Monat. Verifiziert werden die
 * INVARIANTEN (nicht wer gewinnt): gültiger Endstatus, Budget-Konsistenz,
 * „keine Übergabe ohne Freigabe", genau eine wirksame Zahlung je Monat.
 */
@EnabledIfEnvironmentVariable(named = "TX_URL", matches = ".+")
class ZahlungRennenDbTest {

    private static final BigDecimal BUDGET = new BigDecimal("900.00");
    private static final BigDecimal BEDARF = new BigDecimal("863.00"); // RBS 1 + 300 KdU

    private static SimpleConnectionPool pool;
    private static SozialLeistungsService service;

    @BeforeAll
    static void aufbauen() throws Exception {
        Class.forName("oracle.jdbc.OracleDriver");
        pool = new SimpleConnectionPool(System.getenv("TX_URL"), System.getenv("TX_USER"),
                System.getenv("TX_PASS"), 6);
        service = new SozialLeistungsService(new TxManager(pool));
        aufraeumen();
    }

    @AfterAll
    static void aufraeumen() throws Exception {
        try (Connection c = DriverManager.getConnection(
                System.getenv("TX_URL"), System.getenv("TX_USER"), System.getenv("TX_PASS"))) {
            c.setAutoCommit(true);
            Statement st = c.createStatement();
            st.executeUpdate("DELETE FROM JOURNAL WHERE BG_ID IN "
                    + "(SELECT ID FROM BEDARFSGEMEINSCHAFT WHERE AKTENZEICHEN LIKE 'BG-RENN-%')");
            st.executeUpdate("DELETE FROM ZAHLUNG WHERE BG_ID IN "
                    + "(SELECT ID FROM BEDARFSGEMEINSCHAFT WHERE AKTENZEICHEN LIKE 'BG-RENN-%')");
            st.executeUpdate("DELETE FROM SOZIAL_FALL WHERE BG_ID IN "
                    + "(SELECT ID FROM BEDARFSGEMEINSCHAFT WHERE AKTENZEICHEN LIKE 'BG-RENN-%')");
            st.executeUpdate("DELETE FROM BEDARFSGEMEINSCHAFT WHERE AKTENZEICHEN LIKE 'BG-RENN-%'");
        }
    }

    /** Legt BG + Fall (RBS 1, Miete 300) an und ordnet für den Monat an; liefert die Zahlungs-ID. */
    private static long angelegtUndAngeordnet(String aktenzeichen, String monat, String vorgangsNr)
            throws Exception {
        var bg = service.legeBedarfsgemeinschaftAn(aktenzeichen, "Renate Rennen",
                BUDGET, null, "sb-renn");
        SozialFall fall = new SozialFall(1, bg.id(), 1, false, false,
                new BigDecimal("300.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, 1L);
        service.legeSozialFallAn(bg.id(), fall, "sb-renn");
        var ergebnis = service.ordneAuszahlungAn(new Auszahlungsauftrag(
                bg.id(), vorgangsNr, monat, "sb-renn", false, null, null, null));
        return ergebnis.zahlung().id();
    }

    /** Startet alle Callables möglichst gleichzeitig und wartet auf das Ende. */
    private static List<Object> gleichzeitig(List<Callable<Object>> arbeit) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(arbeit.size());
        try {
            CountDownLatch start = new CountDownLatch(1);
            java.util.List<Future<Object>> futures = new java.util.ArrayList<>();
            for (Callable<Object> callable : arbeit) {
                futures.add(executor.submit(() -> {
                    start.await();
                    try {
                        return callable.call();
                    } catch (Exception e) {
                        return e;
                    }
                }));
            }
            start.countDown();
            List<Object> ergebnisse = new java.util.ArrayList<>();
            for (Future<Object> future : futures) {
                ergebnisse.add(future.get(60, TimeUnit.SECONDS));
            }
            return ergebnisse;
        } finally {
            executor.shutdownNow();
        }
    }

    private static long anzahlErgebnisse(List<Object> ergebnisse, Class<?> typ) {
        return ergebnisse.stream().filter(typ::isInstance).count();
    }

    private static long anzahlFachfehler(List<Object> ergebnisse) {
        return ergebnisse.stream().filter(FachlicheException.class::isInstance).count();
    }

    @Test
    void stornoFreigabeUndKassenlaufRennenGleichzeitig() throws Exception {
        AtomicInteger beobachteteEndstatusse = new AtomicInteger();
        for (int i = 1; i <= 15; i++) {
            String az = "BG-RENN-A-" + i;
            long zahlungId = angelegtUndAngeordnet(az, "2026-11", "RENN-A-" + i);
            gleichzeitig(List.of(
                    (Callable<Object>) () -> service.freigebenZahlung(zahlungId, "sb-zweit"),
                    (Callable<Object>) () -> service.storniereZahlung(zahlungId, "admin-renn"),
                    (Callable<Object>) () -> service.uebergebeBereiteZahlungen("admin-kasse", az)));

            try (Connection c = verbindung(); Statement st = c.createStatement()) {
                String status = text(st, "SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = " + zahlungId);
                assertTrue(List.of("ANGELEGT", "BEREIT", "STORNIERT", "UEBERGEBEN").contains(status),
                        "Gültiger Endstatus, erhalten: " + status);
                if ("UEBERGEBEN".equals(status)) {
                    String freigebender = text(st,
                            "SELECT FREIGEGEBEN_VON FROM ZAHLUNG WHERE ID = " + zahlungId);
                    assertTrue(freigebender != null && !freigebender.isBlank(),
                            "Keine Übergabe ohne vorherige Freigabe");
                }
                BigDecimal erwartet = "STORNIERT".equals(status) ? BUDGET : BUDGET.subtract(BEDARF);
                BigDecimal ist = betrag(st,
                        "SELECT b.BUDGET_REST FROM BEDARFSGEMEINSCHAFT b JOIN ZAHLUNG z ON z.BG_ID = b.ID "
                                + "WHERE z.ID = " + zahlungId);
                assertTrue(erwartet.compareTo(ist) == 0,
                        "Budget-Invariante: erwartet " + erwartet + ", erhalten " + ist);
                beobachteteEndstatusse.incrementAndGet();
            }
        }
        assertTrue(beobachteteEndstatusse.get() == 15, "Alle 15 Runden konsistent");
    }

    @Test
    void zweiParalleleStornosGewinnenGenauEinmal() throws Exception {
        String az = "BG-RENN-B-1";
        long zahlungId = angelegtUndAngeordnet(az, "2026-11", "RENN-B-1");
        service.freigebenZahlung(zahlungId, "sb-zweit");
        List<Object> ergebnisse = gleichzeitig(List.of(
                (Callable<Object>) () -> service.storniereZahlung(zahlungId, "admin-a"),
                (Callable<Object>) () -> service.storniereZahlung(zahlungId, "admin-b")));
        assertEquals(1, anzahlErgebnisse(ergebnisse, de.allgemein.lmg.model.Zahlung.class),
                "Genau ein Storno gewinnt");
        assertEquals(1, anzahlFachfehler(ergebnisse), "Der Verlierer erhält einen Fachfehler");
        try (Connection c = verbindung(); Statement st = c.createStatement()) {
            assertEquals("STORNIERT", text(st, "SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = " + zahlungId));
            BigDecimal budget = betrag(st, "SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT "
                    + "WHERE AKTENZEICHEN = '" + az + "'");
            assertTrue(BUDGET.compareTo(budget) == 0, "Rückbuchung genau einmal: " + budget);
        }
    }

    @Test
    void zweiParalleleAnordnungenDesselbenMonatsErlaubenGenauEineZahlung() throws Exception {
        String az = "BG-RENN-C-1";
        var bg = service.legeBedarfsgemeinschaftAn(az, "Carla Conflict", BUDGET, null, "sb-renn");
        SozialFall fall = new SozialFall(1, bg.id(), 1, false, false,
                new BigDecimal("300.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, 1L);
        service.legeSozialFallAn(bg.id(), fall, "sb-renn");
        List<Object> ergebnisse = gleichzeitig(List.of(
                (Callable<Object>) () -> service.ordneAuszahlungAn(new Auszahlungsauftrag(
                        bg.id(), "RENN-C-1", "2026-11", "sb-renn", false, null, null, null)),
                (Callable<Object>) () -> service.ordneAuszahlungAn(new Auszahlungsauftrag(
                        bg.id(), "RENN-C-2", "2026-11", "sb-renn", false, null, null, null))));
        assertEquals(1, anzahlErgebnisse(ergebnisse,
                        de.allgemein.lmg.service.Auszahlungsergebnis.class),
                "Genau eine Anordnung gewinnt den Monat");
        assertEquals(1, anzahlFachfehler(ergebnisse),
                "Die zweite erhält den Fachfehler Monatsregel");
        try (Connection c = verbindung(); Statement st = c.createStatement()) {
            int aktive = (int) zahl(st, "SELECT COUNT(*) FROM ZAHLUNG z JOIN BEDARFSGEMEINSCHAFT b "
                    + "ON z.BG_ID = b.ID WHERE b.AKTENZEICHEN = '" + az
                    + "' AND z.ZAHLUNGSSTATUS <> 'STORNIERT'");
            assertEquals(1, aktive, "Genau eine wirksame Zahlung je BG und Monat");
            BigDecimal budget = betrag(st, "SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT "
                    + "WHERE AKTENZEICHEN = '" + az + "'");
            assertTrue(BUDGET.subtract(BEDARF).compareTo(budget) == 0,
                    "Budget genau einmal gemindert: " + budget);
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

    private static String text(Statement st, String sql) throws Exception {
        try (ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getString(1);
        }
    }

    private static BigDecimal betrag(Statement st, String sql) throws Exception {
        try (ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getBigDecimal(1);
        }
    }

    private static long zahl(Statement st, String sql) throws Exception {
        try (ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }
}
