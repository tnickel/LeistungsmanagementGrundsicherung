package de.allgemein.tx.saga;

import de.allgemein.tx.ConnectionPool;
import de.allgemein.tx.SimpleConnectionPool;
import de.allgemein.tx.TxException;
import de.allgemein.tx.TxManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Echte Oracle-Sperren und Zeitvergleiche; ausschließlich eindeutig eigene Saga-Zeilen. */
@EnabledIfEnvironmentVariable(named = "TX_URL", matches = ".+")
class SagaOwnershipClockDbTest {

    @Test
    void frischeUndAlteSagasWerdenUnabhaengigVonDerSessionZeitzoneErkannt() throws Exception {
        for (String zone : new String[]{"+14:00", "-12:00", "+00:00"}) {
            String type = "CLOCK_" + UUID.randomUUID();
            String id = UUID.randomUUID().toString();
            try (ConnectionPool pool = withZone(newPool(3), zone)) {
                SagaJournal journal = new SagaJournal(pool);
                try (Fixture fixture = new Fixture(type)) {
                    journal.start(id, type, "{}", 1);
                    assertFalse(contains(journal, id), "frisch ist nie fünf Minuten alt; Zone=" + zone);
                    age(id, true);
                    assertTrue(contains(journal, id), "alte Kopf- und Schrittzeile; Zone=" + zone);
                    journal.markStepInFlight(id, 0);
                    assertFalse(contains(journal, id), "frischer Schritt schützt alten Kopf; Zone=" + zone);
                }
            }
        }
    }

    @Test
    void zweiteEngineKompensiertLebendeOracleSagaNichtUndHeadUpdatesBlockierenNicht() throws Exception {
        String type = "LIVE_" + UUID.randomUUID();
        CountDownLatch firstCommitted = new CountDownLatch(1);
        CountDownLatch continueRun = new CountDownLatch(1);
        var worker = Executors.newSingleThreadExecutor();
        try (ConnectionPool pool = newPool(5);
             Fixture fixture = new Fixture(type, worker, continueRun)) {
            var tx = new TxManager(pool);
            SagaEngine firstServer = new SagaEngine(tx, new SagaJournal(pool));
            SagaEngine secondServer = new SagaEngine(tx, new SagaJournal(pool));
            SagaDefinition definition = SagaDefinition.of(type)
                    .step("persistieren", (c, p, id) -> insertEffect(c, id),
                            (c, p, id) -> deleteEffect(c, id))
                    .step("pausieren", (c, p, id) -> {
                        firstCommitted.countDown();
                        if (!continueRun.await(10, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("Testfreigabe fehlt");
                        }
                    }, (c, p, id) -> { })
                    .build();
            var running = worker.submit(() -> firstServer.run(definition, "{}"));
            if (!firstCommitted.await(10, TimeUnit.SECONDS)) {
                running.get(1, TimeUnit.SECONDS); // Primärfehler des Workers statt bloßem Latch-Timeout zeigen
                fail("Erster Fachschritt wurde nicht abgeschlossen");
            }
            assertEquals(1, countEffects(type));
            var recovery = secondServer.recoverWithReport(definition);
            assertEquals(0, recovery.behandelt());
            assertEquals(0, recovery.errors());
            assertEquals(1, countEffects(type), "keine Kompensation während aktiver Saga");
            continueRun.countDown();
            String id = running.get(10, TimeUnit.SECONDS);
            assertEquals("COMPLETED", headState(id));
            assertEquals(1, countEffects(type));
        }
    }

    @Test
    void failedCommitUndKompensationsabschlussVerwendenDenEigenenClaimOhneSelflock() throws Exception {
        String type = "FAIL_" + UUID.randomUUID();
        try (ConnectionPool pool = newPool(4); Fixture fixture = new Fixture(type)) {
            SagaEngine engine = new SagaEngine(new TxManager(pool), new SagaJournal(pool));
            SagaDefinition definition = SagaDefinition.of(type)
                    .step("persistieren", (c, p, id) -> insertEffect(c, id), (c, p, id) -> deleteEffect(c, id))
                    .step("ablehnen", (c, p, id) -> { throw new IllegalStateException("gezielte Abweisung"); },
                            (c, p, id) -> { })
                    .build();
            assertTimeout(Duration.ofSeconds(10),
                    () -> assertThrows(TxException.class, () -> engine.run(definition, "{}")));
            assertEquals(0, countEffects(type));
            try (Connection c = connect(); PreparedStatement p = c.prepareStatement(
                    "SELECT STATE FROM TX_SAGA WHERE SAGA_TYPE = ?")) {
                p.setString(1, type);
                try (var rows = p.executeQuery()) {
                    assertTrue(rows.next());
                    assertEquals("COMPENSATED", rows.getString(1));
                    assertFalse(rows.next());
                }
            }
        }
    }

    private static boolean contains(SagaJournal journal, String id) throws SQLException {
        return journal.unfinished(Duration.ofMinutes(5)).stream().anyMatch(s -> id.equals(s.sagaId()));
    }

    private static ConnectionPool newPool(int size) throws SQLException {
        return new SimpleConnectionPool(System.getenv("TX_URL"), System.getenv("TX_USER"),
                System.getenv("TX_PASS"), size, Duration.ofSeconds(5), 5_000, 10_000);
    }

    private static Connection connect() throws SQLException {
        var properties = new java.util.Properties();
        properties.setProperty("user", System.getenv("TX_USER"));
        properties.setProperty("password", System.getenv("TX_PASS"));
        properties.setProperty("oracle.net.CONNECT_TIMEOUT", "5000");
        properties.setProperty("oracle.jdbc.ReadTimeout", "10000");
        return DriverManager.getConnection(System.getenv("TX_URL"), properties);
    }

    private static ConnectionPool withZone(ConnectionPool delegate, String zone) {
        return new ConnectionPool() {
            public Connection getConnection() throws SQLException {
                Connection c = delegate.getConnection();
                try (var statement = c.createStatement()) {
                    statement.execute("ALTER SESSION SET TIME_ZONE = '" + zone + "'");
                    return c;
                } catch (SQLException e) {
                    delegate.release(c);
                    throw e;
                }
            }
            public void release(Connection c) { delegate.release(c); }
            public void close() { delegate.close(); }
        };
    }

    private static void age(String id, boolean steps) throws SQLException {
        try (Connection c = connect()) {
            c.setAutoCommit(false);
            for (String table : steps ? new String[]{"TX_SAGA", "TX_SAGA_STEP"} : new String[]{"TX_SAGA"}) {
                try (PreparedStatement p = c.prepareStatement("UPDATE " + table
                        + " SET UPDATED_AT = SYSTIMESTAMP - INTERVAL '10' MINUTE WHERE SAGA_ID = ?")) {
                    p.setString(1, id);
                    p.executeUpdate();
                }
            }
            c.commit();
        }
    }

    private static void insertEffect(Connection c, String id) throws SQLException {
        try (PreparedStatement p = c.prepareStatement(
                "INSERT INTO TX_DEMO_ANTRAG (NAME, STATUS, BETRAG) VALUES (?, 'NEU', 1)")) {
            p.setString(1, "KERNEL-" + id);
            p.executeUpdate();
        }
    }

    private static void deleteEffect(Connection c, String id) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("DELETE FROM TX_DEMO_ANTRAG WHERE NAME = ?")) {
            p.setString(1, "KERNEL-" + id);
            p.executeUpdate();
        }
    }

    private static int countEffects(String type) throws SQLException {
        try (Connection c = connect(); PreparedStatement p = c.prepareStatement(
                "SELECT COUNT(*) FROM TX_DEMO_ANTRAG WHERE NAME IN (SELECT 'KERNEL-' || SAGA_ID FROM TX_SAGA WHERE SAGA_TYPE = ?)")) {
            p.setString(1, type);
            try (var rows = p.executeQuery()) { assertTrue(rows.next()); return rows.getInt(1); }
        }
    }

    private static String headState(String id) throws SQLException {
        try (Connection c = connect(); PreparedStatement p = c.prepareStatement("SELECT STATE FROM TX_SAGA WHERE SAGA_ID = ?")) {
            p.setString(1, id);
            try (var rows = p.executeQuery()) { assertTrue(rows.next()); return rows.getString(1); }
        }
    }

    private static void cleanup(String type) throws SQLException {
        try (Connection c = connect()) {
            c.setAutoCommit(false);
            for (String sql : new String[]{
                    "DELETE FROM TX_DEMO_ANTRAG WHERE NAME IN (SELECT 'KERNEL-' || SAGA_ID FROM TX_SAGA WHERE SAGA_TYPE = ?)",
                    "DELETE FROM TX_SAGA_STEP WHERE SAGA_ID IN (SELECT SAGA_ID FROM TX_SAGA WHERE SAGA_TYPE = ?)",
                    "DELETE FROM TX_SAGA WHERE SAGA_TYPE = ?"}) {
                try (PreparedStatement p = c.prepareStatement(sql)) {
                    p.setString(1, type);
                    p.executeUpdate();
                }
            }
            c.commit();
        }
    }

    /** try-with-resources erhält bei Cleanupfehlern den ursprünglichen Testfehler. */
    private static final class Fixture implements AutoCloseable {
        private final String type;
        private final java.util.concurrent.ExecutorService worker;
        private final CountDownLatch continueRun;

        Fixture(String type) { this(type, null, null); }

        Fixture(String type, java.util.concurrent.ExecutorService worker, CountDownLatch continueRun) {
            this.type = type;
            this.worker = worker;
            this.continueRun = continueRun;
        }

        @Override
        public void close() throws Exception {
            if (continueRun != null) continueRun.countDown();
            if (worker != null) {
                worker.shutdownNow();
                if (!worker.awaitTermination(15, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Testworker noch aktiv; eigene Saga-Daten bleiben zur Diagnose erhalten");
                }
            }
            cleanup(type);
        }
    }
}
