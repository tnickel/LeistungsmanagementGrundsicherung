package de.allgemein.tx;

import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regressionstests für den Pool ohne echte Datenbank: über einen Fake-Treiber
 * (jdbc:fakre://…) werden steuerbare Verbindungen erzeugt. Getestet werden die
 * Sicherheitsregeln des Pools: Permit-Haushalt, idempotentes release(), kein
 * impliziter Commit, Validierung beim Entleihen und das Borrow-Timeout
 * (aufgefallen beim ersten Lauf gegen echte Oracle, siehe Roadmap Etappe 1).
 */
class SimpleConnectionPoolTest {

    private static final String FAKE_URL = "jdbc:fakre://demo";

    /** Alle je erzeugten Fake-Verbindungen, adressierbar über die Proxy-Instanz. */
    private static final Map<Connection, FakeVerbindung> ALLE = new ConcurrentHashMap<>();

    /** Einmalig registrierter Täuschungs-Treiber (DriverManager fragt ihn für jdbc:fakre). */
    private static final FakeDriver TREIBER = new FakeDriver();

    /**
     * Täuschungs-Treiber: erzeugt pro connect() eine neue, steuerbare Fake-Verbindung
     * und trägt sie in {@link #ALLE} ein.
     */
    static class FakeDriver implements Driver {

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
            FakeVerbindung verbindung = new FakeVerbindung();
            ALLE.put(verbindung.proxy, verbindung);
            return verbindung.proxy;
        }

        @Override public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) { return new DriverPropertyInfo[0]; }
        @Override public int getMajorVersion() { return 1; }
        @Override public int getMinorVersion() { return 0; }
        @Override public boolean jdbcCompliant() { return true; }
        @Override public Logger getParentLogger() { return Logger.getLogger("fakre"); }
    }

    /** Fake-Verbindung mit beobachtbarem und steuerbarem Zustand. */
    static final class FakeVerbindung {
        /** physisch geschlossen (close() aufgerufen oder isClosed meldet true). */
        final AtomicBoolean geschlossen = new AtomicBoolean(false);
        /** logisch tot: nicht geschlossen, aber isValid false (DB-Restart-Simulation). */
        final AtomicBoolean tot = new AtomicBoolean(false);
        /** isClosed() soll SQLException werfen (kaputte Verbindung). */
        final AtomicBoolean isClosedWirft = new AtomicBoolean(false);
        /** Autocommit-Modus (TxContext schaltet beim Start auf false). */
        final AtomicBoolean autoCommit = new AtomicBoolean(true);
        final AtomicBoolean rollbackWirft = new AtomicBoolean(false);
        /** Reihenfolge der transaktionsrelevanten Aufrufe (rollback/setAutoCommit/commit). */
        final List<String> protokoll = new CopyOnWriteArrayList<>();

        final Connection proxy = (Connection) Proxy.newProxyInstance(
                SimpleConnectionPoolTest.class.getClassLoader(),
                new Class<?>[] {Connection.class},
                (InvocationHandler) (proxy, methode, args) -> {
                    String name = methode.getName();
                    if ("hashCode".equals(name)) {
                        return System.identityHashCode(proxy); // Identity-Semantik wie Objekte
                    }
                    if ("equals".equals(name)) {
                        return proxy == args[0];
                    }
                    if ("toString".equals(name)) {
                        return "FakeVerbindung#" + System.identityHashCode(proxy);
                    }
                    if ("isClosed".equals(name)) {
                        if (isClosedWirft.get()) {
                            throw new SQLException("Verbindung hinüber (isClosed wirft)");
                        }
                        return geschlossen.get();
                    }
                    if ("isValid".equals(name)) {
                        return !geschlossen.get() && !tot.get();
                    }
                    if ("getAutoCommit".equals(name)) {
                        return autoCommit.get();
                    }
                    if ("setAutoCommit".equals(name)) {
                        protokoll.add("setAutoCommit(" + args[0] + ")");
                        autoCommit.set((Boolean) args[0]);
                        return null;
                    }
                    if ("rollback".equals(name)) {
                        protokoll.add("rollback");
                        if (rollbackWirft.get()) throw new SQLException("Rollback fehlgeschlagen");
                        return null;
                    }
                    if ("commit".equals(name)) {
                        protokoll.add("commit");
                        return null;
                    }
                    if ("close".equals(name)) {
                        geschlossen.set(true);
                        return null;
                    }
                    return null;
                });
    }

    @Test
    void fehlgeschlagenesRollbackDarfNiemalsAutocommitEinschalten() throws Exception {
        try (SimpleConnectionPool pool = new SimpleConnectionPool(FAKE_URL, "u", "p", 1)) {
            Connection c = pool.getConnection();
            FakeVerbindung fake = ALLE.get(c);
            c.setAutoCommit(false);
            fake.protokoll.clear();
            fake.rollbackWirft.set(true);
            pool.release(c);
            assertEquals(List.of("rollback"), fake.protokoll);
            assertTrue(fake.geschlossen.get(), "defekte Transaktion samt Verbindung verwerfen");
            Connection replacement = pool.getConnection();
            assertNotSame(c, replacement);
            pool.release(replacement);
        }
    }

    @Test
    void wiederholtesAusleihenUndZurueckgebenBlockiertNicht() throws Exception {
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
        try (SimpleConnectionPool pool = new SimpleConnectionPool(FAKE_URL, "u", "p", 1)) {
            Connection conn = pool.getConnection();
            FakeVerbindung fv = ALLE.get(conn);
            assertNotNull(fv, "Fake-Verbindung muss im Treiber registriert sein");
            fv.geschlossen.set(true); // stirbt zwischen Ausleihe und Rückgabe
            pool.release(conn);       // wird still verworfen, Permit kommt zurück

            // nächstes Ausleihen darf nicht blockieren und liefert eine neue Verbindung
            Connection zweite = pool.getConnection();
            assertNotSame(conn, zweite, "geschlossene Verbindung darf nicht erneut ausgeliefert werden");
            assertFalse(ALLE.get(zweite).geschlossen.get());
            pool.release(zweite);
        }
    }

    @Test
    void doppeltesReleaseIstUnschaedlich() throws Exception {
        // kleines Borrow-Timeout, damit ein (fehlerhafter) Permit-Gewinn schnell sichtbar wird
        try (SimpleConnectionPool pool = new SimpleConnectionPool(FAKE_URL, "u", "p", 1, Duration.ofMillis(300))) {
            Connection conn = pool.getConnection();
            pool.release(conn);
            pool.release(conn); // Doppel-Release: nur Protokoll, kein zweites Permit

            // Kein Permit-Gewinn: eine Verbindung ist ausgeliehen, die parallele
            // Ausleihe muss ins Borrow-Timeout laufen (maxSize bleibt 1).
            Connection gehalten = pool.getConnection();
            AtomicReference<SQLException> fehler = new AtomicReference<>();
            CountDownLatch fertig = new CountDownLatch(1);
            Thread zweiter = new Thread(() -> {
                try {
                    pool.getConnection();
                } catch (SQLException e) {
                    fehler.set(e);
                } finally {
                    fertig.countDown();
                }
            });
            zweiter.start();
            assertTrue(fertig.await(5, TimeUnit.SECONDS), "paralleles getConnection muss mit Timeout enden");
            assertNotNull(fehler.get(), "Doppel-Release darf kein Permit zusätzlich freigeben");
            assertTrue(fehler.get().getMessage().contains("erschöpft"),
                    "Meldung sollte Pool-Erschöpfung nennen: " + fehler.get().getMessage());
            zweiter.join(1000);

            // danach funktioniert borrow/release normal weiter
            pool.release(gehalten);
            Connection wieder = pool.getConnection();
            assertSame(gehalten, wieder, "freigegebene Verbindung kommt aus dem Idle-Pool zurück");
            pool.release(wieder);
        }
    }

    @Test
    void borrowTimeoutBeiErschoepfung() throws Exception {
        try (SimpleConnectionPool pool = new SimpleConnectionPool(FAKE_URL, "u", "p", 1, Duration.ofMillis(500))) {
            Connection conn = pool.getConnection(); // das einzige Permit belegen

            long start = System.nanoTime();
            SQLException fehler = assertThrows(SQLException.class, pool::getConnection);
            long dauerMs = (System.nanoTime() - start) / 1_000_000;

            assertTrue(dauerMs >= 350, "getConnection muss annähernd das Borrow-Timeout warten, war " + dauerMs + " ms");
            assertTrue(fehler.getMessage().contains("erschöpft"),
                    "Meldung sollte Pool-Erschöpfung nennen: " + fehler.getMessage());

            // nach der Freigabe klappt das Ausleihen wieder (sofort, aus dem Idle-Pool)
            pool.release(conn);
            Connection wieder = pool.getConnection();
            assertSame(conn, wieder);
            pool.release(wieder);
        }
    }

    @Test
    void toteVerbindungWirdBeimEntleihenVerworfen() throws Exception {
        try (SimpleConnectionPool pool = new SimpleConnectionPool(FAKE_URL, "u", "p", 2)) {
            Connection conn1 = pool.getConnection();
            FakeVerbindung fv1 = ALLE.get(conn1);
            fv1.tot.set(true); // DB-Restart-Simulation: nicht geschlossen, aber ungültig
            pool.release(conn1); // landet (noch) im Idle-Pool, Validierung erfolgt erst beim Entleihen

            Connection conn2 = pool.getConnection(); // muss fv1 verwerfen und neu öffnen
            assertNotSame(conn1, conn2, "tote Verbindung darf nicht ausgeliefert werden");
            assertTrue(fv1.geschlossen.get(), "tote Verbindung muss beim Verwerfen geschlossen werden");
            assertFalse(ALLE.get(conn2).geschlossen.get(), "neue Verbindung muss gültig sein");
            pool.release(conn2);
        }
    }

    @Test
    void verbindungMitWerfendemIsClosedWirdStillVerworfen() throws Exception {
        try (SimpleConnectionPool pool = new SimpleConnectionPool(FAKE_URL, "u", "p", 2)) {
            Connection conn1 = pool.getConnection();
            FakeVerbindung fv1 = ALLE.get(conn1);
            pool.release(conn1);        // regulär zurück in den Idle-Pool
            fv1.isClosedWirft.set(true); // nun bricht schon die Zustandsabfrage

            // getConnection darf an der werfenden Verbindung nicht sterben und auch
            // keine Referenz verlieren: still schließen, weiterpollen, neu öffnen
            Connection conn2 = pool.getConnection();
            assertNotSame(conn1, conn2);
            assertTrue(fv1.geschlossen.get(), "werfende Verbindung muss still geschlossen werden");
            pool.release(conn2);
        }
    }

    @Test
    void releaseRolltOffeneTransaktionZurueckStattZuCommitten() throws Exception {
        try (SimpleConnectionPool pool = new SimpleConnectionPool(FAKE_URL, "u", "p", 1)) {
            Connection conn = pool.getConnection();
            FakeVerbindung fv = ALLE.get(conn);
            fv.autoCommit.set(false); // offene Transaktion, wie der TxContext sie hinterlässt

            pool.release(conn);

            List<String> aktionen = fv.protokoll;
            assertTrue(aktionen.contains("rollback"),
                    "release() muss eine offene Transaktion zurückrollen: " + aktionen);
            assertTrue(aktionen.contains("setAutoCommit(true)"),
                    "release() muss den Autocommit-Modus wiederherstellen: " + aktionen);
            assertFalse(aktionen.contains("commit"),
                    "release() darf niemals implizit committen: " + aktionen);
            assertTrue(aktionen.indexOf("rollback") < aktionen.indexOf("setAutoCommit(true)"),
                    "rollback muss VOR setAutoCommit(true) laufen: " + aktionen);
        }
    }
}
