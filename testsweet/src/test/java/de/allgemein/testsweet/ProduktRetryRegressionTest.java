package de.allgemein.testsweet;

import de.allgemein.tx.ConnectionPool;
import de.allgemein.tx.RetryPolicy;
import de.allgemein.tx.TxException;
import de.allgemein.tx.TxManager;
import de.allgemein.tx.TxOptions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Prüft den installierten Produktkern und die tatsächlich eingebundene Oracle-Treiberzuordnung. */
class ProduktRetryRegressionTest {
    @Test
    void ressourcenfehlerWerdenTrotzTatsaechlichemOracleSqlStateNurEinmalVersucht() throws Exception {
        var mapping = Class.forName("oracle.jdbc.driver.DatabaseError").getMethod("ErrorToSQLState", int.class);
        for (int code : new int[]{18, 20, 4031}) {
            String state = (String) mapping.invoke(null, code);
            var pool = new CountingPool();
            var attempts = new AtomicInteger();
            TxException error = assertThrows(TxException.class, () -> new TxManager(pool, new RetryPolicy(1))
                    .execute(TxOptions.DEFAULT, ctx -> {
                        attempts.incrementAndGet();
                        throw new SQLException("synthetischer Ressourcenfehler", state, code);
                    }));
            assertFalse(error.isRetryable(), "ORA=" + code + ", State=" + state);
            assertEquals(1, attempts.get());
            assertEquals(1, pool.rollbacks.get());
            assertEquals(1, pool.releases.get());
            assertEquals(0, pool.commits.get());
        }
        for (String state : new String[]{"61000", "40001"}) {
            assertFalse(RetryPolicy.isRetryable(new SQLException("ohne Vendorcode", state)));
        }
    }

    @Test
    void erlaubteOracleSperrenkonflikteWerdenNachRollbackErfolgreichWiederholt() throws Exception {
        var mapping = Class.forName("oracle.jdbc.driver.DatabaseError").getMethod("ErrorToSQLState", int.class);
        for (int code : new int[]{54, 60, 8177}) {
            String state = (String) mapping.invoke(null, code);
            var pool = new CountingPool();
            var attempts = new AtomicInteger();
            assertEquals("ok", new TxManager(pool, new RetryPolicy(1)).execute(TxOptions.DEFAULT, ctx -> {
                if (attempts.incrementAndGet() == 1) throw new SQLException("synthetischer Sperrenkonflikt", state, code);
                return "ok";
            }));
            assertEquals(2, attempts.get());
            assertEquals(1, pool.rollbacks.get());
            assertEquals(1, pool.commits.get());
            assertEquals(2, pool.releases.get());
        }
    }

    private static final class CountingPool implements ConnectionPool {
        final AtomicInteger commits = new AtomicInteger(), rollbacks = new AtomicInteger(), releases = new AtomicInteger();
        public Connection getConnection() {
            return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Connection.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "setAutoCommit", "setTransactionIsolation", "close" -> null;
                        case "commit" -> { commits.incrementAndGet(); yield null; }
                        case "rollback" -> { rollbacks.incrementAndGet(); yield null; }
                        case "isClosed" -> false;
                        case "toString" -> "TestsweetCountingConnection";
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
        }
        public void release(Connection c) { releases.incrementAndGet(); }
        public void close() { }
    }
}
