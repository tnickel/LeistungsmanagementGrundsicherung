package de.allgemein.tx;

import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression für den Permit-Leak im Pool: release() muss das Permit in jedem
 * Pfad zurückgeben, sonst blockt getConnection nach maxSize Ausleihen ewig
 * (aufgefallen beim ersten Lauf gegen echte Oracle, siehe Roadmap Etappe 1).
 */
class SimpleConnectionPoolTest {

    private static final String FAKE_URL = "jdbc:fakre://demo";

    /** Täuschungs-Treiber: liefert immer dieselbe offene Fake-Verbindung. */
    static class FakeDriver implements Driver {
        final AtomicBoolean geschlossen = new AtomicBoolean(false);

        FakeDriver() {
            try {
                java.sql.DriverManager.registerDriver(this);
            } catch (SQLException e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public boolean acceptsURL(String url) {
            return url.startsWith("jdbc:fakre");
        }

        @Override
        public Connection connect(String url, Properties info) {
            if (!acceptsURL(url)) {
                return null;
            }
            return (Connection) Proxy.newProxyInstance(
                    SimpleConnectionPool.class.getClassLoader(),
                    new Class<?>[] {Connection.class},
                    (InvocationHandler) (proxy, methode, args) -> switch (methode.getName()) {
                        case "isClosed" -> geschlossen.get();
                        case "getAutoCommit" -> true;
                        case "close" -> null;
                        default -> null;
                    });
        }

        @Override public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) { return new DriverPropertyInfo[0]; }
        @Override public int getMajorVersion() { return 1; }
        @Override public int getMinorVersion() { return 0; }
        @Override public boolean jdbcCompliant() { return true; }
        @Override public Logger getParentLogger() { return Logger.getLogger("fakre"); }
    }

    @Test
    void wiederholtesAusleihenUndZurueckgebenBlockiertNicht() throws Exception {
        FakeDriver treiber = new FakeDriver();
        try (SimpleConnectionPool pool = new SimpleConnectionPool(FAKE_URL, "u", "p", 3)) {
            CountDownLatch fertig = new CountDownLatch(1);
            Thread nutzer = new Thread(() -> {
                try {
                    // deutlich mehr Zyklen als maxSize — mit Permit-Leak würde der
                    // zweite Durchlauf ewig blockieren
                    for (int i = 0; i < 10; i++) {
                        pool.release(pool.getConnection());
                    }
                } catch (SQLException e) {
                    // egal — blockieren ist der Fehlerfall, nicht die Exception
                } finally {
                    fertig.countDown();
                }
            });
            nutzer.start();
            assertTrue(fertig.await(10, TimeUnit.SECONDS),
                    "getConnection/release hat blockiert — Permit wird im Pool nicht zurückgegeben");
            nutzer.join(1000);
        }
    }

    @Test
    void geschlosseneVerbindungWirdVerworfenUndPermitZurueckgegeben() throws Exception {
        FakeDriver treiber = new FakeDriver();
        treiber.geschlossen.set(true);
        try (SimpleConnectionPool pool = new SimpleConnectionPool(FAKE_URL, "u", "p", 1)) {
            CountDownLatch fertig = new CountDownLatch(1);
            Thread nutzer = new Thread(() -> {
                try {
                    pool.release(pool.getConnection());
                    pool.getConnection(); // Permit muss wieder verfügbar sein
                } catch (SQLException e) {
                    // egal
                } finally {
                    fertig.countDown();
                }
            });
            nutzer.start();
            assertTrue(fertig.await(10, TimeUnit.SECONDS),
                    "release einer geschlossenen Verbindung hat das Permit nicht zurückgegeben");
            nutzer.join(1000);
        }
    }
}
