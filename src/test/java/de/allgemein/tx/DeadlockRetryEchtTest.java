package de.allgemein.tx;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifikation des Deadlock-Retries (Roadmap Etappe 1) gegen eine echte Oracle-Datenbank.
 * <p>
 * Zwei parallele Threads fordern Zeilensperren (SELECT ... FOR UPDATE) in umgekehrter
 * Reihenfolge an:
 * <ul>
 *   <li>Thread 1 sperrt Zeile A, wartet kurz und will Zeile B sperren.</li>
 *   <li>Thread 2 sperrt Zeile B, wartet kurz und will Zeile A sperren.</li>
 * </ul>
 * Oracle erkennt die Kreuzsperre zuverlässig und bricht eine Transaktion mit ORA-00060
 * (deadlock detected) ab. Die {@link RetryPolicy} fängt ORA-00060 ab, führt ein Rollback
 * aus und startet automatisch einen zweiten Versuch, der nach Abschluss des Konkurrenten
 * erfolgreich committet.
 */
@EnabledIfEnvironmentVariable(named = "TX_URL", matches = ".+")
class DeadlockRetryEchtTest {

    private static ConnectionPool pool;
    private static TxManager txManager;

    private static final long ROW_A = 9998L;
    private static final long ROW_B = 9999L;

    @BeforeAll
    static void aufbauen() throws Exception {
        Class.forName("oracle.jdbc.OracleDriver");
        String url = System.getenv("TX_URL");
        String user = System.getenv("TX_USER");
        String pass = System.getenv("TX_PASS");

        pool = new SimpleConnectionPool(url, user, pass, 4);
        txManager = new TxManager(pool);

        aufraeumen();
        legeTestzeileAn(ROW_A, "BG-DEADLOCK-A", "100.00");
        legeTestzeileAn(ROW_B, "BG-DEADLOCK-B", "200.00");
    }

    @AfterAll
    static void aufraeumen() throws Exception {
        try (Connection c = DriverManager.getConnection(
                System.getenv("TX_URL"), System.getenv("TX_USER"), System.getenv("TX_PASS"))) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                st.executeUpdate("DELETE FROM BEDARFSGEMEINSCHAFT WHERE ID IN (" + ROW_A + ", " + ROW_B + ")");
            }
            c.commit();
        }
        if (pool != null) {
            pool.close();
        }
    }

    private static void legeTestzeileAn(long id, String aktenzeichen, String budget) throws Exception {
        try (Connection c = DriverManager.getConnection(
                System.getenv("TX_URL"), System.getenv("TX_USER"), System.getenv("TX_PASS"))) {
            c.setAutoCommit(false);
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO BEDARFSGEMEINSCHAFT (ID, AKTENZEICHEN, HAUPTPERSON_NAME, STATUS_AKTIV, BUDGET_REST) "
                            + "VALUES (?, ?, 'Deadlock Test', 1, ?)")) {
                ps.setLong(1, id);
                ps.setString(2, aktenzeichen);
                ps.setBigDecimal(3, new java.math.BigDecimal(budget));
                ps.executeUpdate();
            }
            c.commit();
        }
    }

    @Test
    void echterDeadlockWirdErkanntUndImZweitenVersuchCommittet() throws Exception {
        CountDownLatch aGesperrt = new CountDownLatch(1);
        CountDownLatch bGesperrt = new CountDownLatch(1);

        AtomicInteger versuche1 = new AtomicInteger(0);
        AtomicInteger versuche2 = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> task1 = executor.submit(() -> {
                txManager.execute(TxOptions.DEFAULT, ctx -> {
                    int versuch = versuche1.incrementAndGet();
                    Connection conn = ctx.connection();
                    long sid = ermittleSid(conn);
                    System.out.println("[T1] Versuch " + versuch + " auf Session " + sid);
                    if (versuch == 1) {
                        sperreZeile(conn, ROW_A);
                        System.out.println("[T1] ROW_A gesperrt, signalisiere T2");
                        aGesperrt.countDown();
                        boolean ok = bGesperrt.await(15, TimeUnit.SECONDS);
                        System.out.println("[T1] bGesperrt empfangen (ok=" + ok + "), versuche ROW_B zu sperren...");
                        sperreZeile(conn, ROW_B);
                        System.out.println("[T1] ROW_B gesperrt!");
                    } else {
                        System.out.println("[T1] Wiederholversuch sperrt ROW_A und ROW_B");
                        sperreZeile(conn, ROW_A);
                        sperreZeile(conn, ROW_B);
                    }
                    aendereBudget(conn, ROW_A, "111.00");
                    return null;
                });
                return true;
            });

            Future<Boolean> task2 = executor.submit(() -> {
                txManager.execute(TxOptions.DEFAULT, ctx -> {
                    int versuch = versuche2.incrementAndGet();
                    Connection conn = ctx.connection();
                    long sid = ermittleSid(conn);
                    System.out.println("[T2] Versuch " + versuch + " auf Session " + sid);
                    if (versuch == 1) {
                        boolean ok = aGesperrt.await(15, TimeUnit.SECONDS);
                        System.out.println("[T2] aGesperrt empfangen (ok=" + ok + "), sperre ROW_B...");
                        sperreZeile(conn, ROW_B);
                        System.out.println("[T2] ROW_B gesperrt, signalisiere T1");
                        bGesperrt.countDown();
                        System.out.println("[T2] versuche ROW_A zu sperren (Kreuzsperre!)...");
                        sperreZeile(conn, ROW_A);
                        System.out.println("[T2] ROW_A gesperrt!");
                    } else {
                        System.out.println("[T2] Wiederholversuch sperrt ROW_B und ROW_A");
                        sperreZeile(conn, ROW_B);
                        sperreZeile(conn, ROW_A);
                    }
                    aendereBudget(conn, ROW_B, "222.00");
                    return null;
                });
                return true;
            });

            // Beide Tasks müssen dank automatischem Retry erfolgreich beenden
            assertTrue(task1.get(60, TimeUnit.SECONDS), "Task 1 erfolgreich");
            assertTrue(task2.get(60, TimeUnit.SECONDS), "Task 2 erfolgreich");

            // Genau einer der beiden Threads wurde von Oracle als Deadlock-Opfer gewählt und
            // hat 2 Versuche benötigt; der andere hat 1 Versuch benötigt. Gesamtversuche = 3.
            assertEquals(3, versuche1.get() + versuche2.get(),
                    "Ein Thread muss ORA-00060 erlitten haben und im 2. Versuch committet haben");

        } finally {
            executor.shutdownNow();
        }
    }

    private static void sperreZeile(Connection conn, long id) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT ID, BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ? FOR UPDATE")) {
            ps.setLong(1, id);
            try (var rs = ps.executeQuery()) {
                assertTrue(rs.next(), "Zeile " + id + " muss existieren");
            }
        }
    }

    private static void aendereBudget(Connection conn, long id, String neuerWert) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE BEDARFSGEMEINSCHAFT SET BUDGET_REST = ? WHERE ID = ?")) {
            ps.setBigDecimal(1, new java.math.BigDecimal(neuerWert));
            ps.setLong(2, id);
            ps.executeUpdate();
        }
    }

    private static long ermittleSid(Connection conn) throws Exception {
        try (Statement st = conn.createStatement();
             var rs = st.executeQuery("SELECT SYS_CONTEXT('USERENV', 'SID') FROM DUAL")) {
            rs.next();
            return rs.getLong(1);
        }
    }
}
