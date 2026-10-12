package de.allgemein.lmg.db;

import de.allgemein.tx.SimpleConnectionPool;
import de.allgemein.tx.TxManager;
import de.allgemein.tx.saga.SagaEngine;
import de.allgemein.tx.saga.SagaJournal;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Echter Prozess-Kill statt synthetischem Crash-Zustand (Testlücke L-13):
 * Ein Kind-JVM-Prozess beginnt eine Saga und schläft im zweiten Schritt; der
 * Test tötet ihn hart (destroyForcibly — kein finally, kein Commit), während
 * der Schritt im Journal als IN_FLIGHT steht. Der Wiederanlauf im Eltern-Prozess
 * muss die Saga daraufhin konservativ kompensieren: Vorgang historisiert
 * (§ 67 SGB X — Nachvollziehbarkeit), Saga-Endzustand COMPENSATED.
 */
@EnabledIfEnvironmentVariable(named = "TX_URL", matches = ".+")
class ProzessKillRecoveryDbTest {

    private static final String AKTENZEICHEN = "BG-KILL-1";
    private static SimpleConnectionPool pool;

    @BeforeAll
    static void aufbauen() throws Exception {
        Class.forName("oracle.jdbc.OracleDriver");
        pool = new SimpleConnectionPool(System.getenv("TX_URL"), System.getenv("TX_USER"),
                System.getenv("TX_PASS"), 4);
        aufraeumen();
    }

    @AfterAll
    static void aufraeumen() throws Exception {
        try (Connection c = DriverManager.getConnection(
                System.getenv("TX_URL"), System.getenv("TX_USER"), System.getenv("TX_PASS"))) {
            c.setAutoCommit(true);
            Statement st = c.createStatement();
            st.executeUpdate("DELETE FROM TX_SAGA_STEP WHERE SAGA_ID IN "
                    + "(SELECT SAGA_ID FROM TX_SAGA WHERE SAGA_TYPE = '" + ProzessKillHelfer.SAGA_TYPE + "')");
            st.executeUpdate("DELETE FROM TX_SAGA WHERE SAGA_TYPE = '" + ProzessKillHelfer.SAGA_TYPE + "'");
            st.executeUpdate("DELETE FROM BEDARFSGEMEINSCHAFT WHERE AKTENZEICHEN = '" + AKTENZEICHEN + "'");
        }
    }

    @Test
    void harterProzessKillHinterlaesstKompensierbarenZustand() throws Exception {
        String classpath = System.getProperty("java.class.path");
        Process kind = new ProcessBuilder(
                System.getProperty("java.home") + java.io.File.separator + "bin"
                        + java.io.File.separator + (de.allgemein.lmg.db.ProzessKillRecoveryDbTest
                                .isWindows() ? "java.exe" : "java"),
                "-cp", classpath,
                ProzessKillHelfer.class.getName(),
                System.getenv("TX_URL"), System.getenv("TX_USER"), System.getenv("TX_PASS"),
                AKTENZEICHEN)
                .redirectErrorStream(true)
                .start();

        // Warten, bis der Kind-Prozess den zweiten Schritt als IN_FLIGHT gemeldet hat
        // (Write-Ahead ist committet, bevor die 30-s-Pause beginnt).
        boolean inFlightGesehen = false;
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline) {
            if (kind.isAlive()) {
                try (Connection c = DriverManager.getConnection(System.getenv("TX_URL"),
                        System.getenv("TX_USER"), System.getenv("TX_PASS"));
                     Statement st = c.createStatement();
                     ResultSet rs = st.executeQuery(
                             "SELECT COUNT(*) FROM TX_SAGA_STEP s JOIN TX_SAGA k ON k.SAGA_ID = s.SAGA_ID "
                                     + "WHERE k.SAGA_TYPE = '" + ProzessKillHelfer.SAGA_TYPE + "' "
                                     + "AND s.STATE = 'IN_FLIGHT'")) {
                    rs.next();
                    if (rs.getInt(1) > 0) {
                        inFlightGesehen = true;
                        break;
                    }
                }
                Thread.sleep(200);
            } else {
                break;
            }
        }
        assertTrue(inFlightGesehen, "Kind-Prozess hat IN_FLIGHT nicht erreicht (Ausgabe prüfen)");

        // Der harte Kill: kein Shutdown-Hook, kein finally — wie ein Absturz.
        kind.destroyForcibly();
        kind.waitFor();

        // Wiederanlauf im Eltern-Prozess: IN_FLIGHT gilt als möglicherweise committet.
        SagaEngine engine = new SagaEngine(new TxManager(pool), new SagaJournal(pool));
        int kompensiert = engine.recover(ProzessKillHelfer.definition());
        assertTrue(kompensiert >= 1, "Die abgestürzte Saga wurde wieder aufgenommen");

        try (Connection c = DriverManager.getConnection(System.getenv("TX_URL"),
                System.getenv("TX_USER"), System.getenv("TX_PASS"));
             Statement st = c.createStatement()) {
            try (ResultSet rs = st.executeQuery("SELECT STATUS_AKTIV FROM BEDARFSGEMEINSCHAFT "
                    + "WHERE AKTENZEICHEN = '" + AKTENZEICHEN + "'")) {
                assertTrue(rs.next(), "Vorgang existiert (§ 67 SGB X: kein Löschen)");
                assertEquals(0, rs.getInt(1), "Kompensation hat den Vorgang historisiert");
            }
            try (ResultSet rs = st.executeQuery("SELECT STATE FROM TX_SAGA WHERE SAGA_TYPE = '"
                    + ProzessKillHelfer.SAGA_TYPE + "'")) {
                assertTrue(rs.next());
                assertEquals("COMPENSATED", rs.getString(1), "Saga saß endet kompensiert");
            }
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }
}
