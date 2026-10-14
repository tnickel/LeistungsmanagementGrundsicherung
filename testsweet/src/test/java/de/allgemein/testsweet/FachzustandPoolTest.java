package de.allgemein.testsweet;

import de.allgemein.tx.ConnectionPool;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class FachzustandPoolTest {
    @Test void tausendSnapshotsVerwendenDieselbePoolverbindungUndGebenJedeAusleiheZurueck() throws Exception {
        DatabaseProbe database = new DatabaseProbe();
        AtomicInteger borrows = new AtomicInteger(), releases = new AtomicInteger();
        ConnectionPool pool = pool(database.connection, borrows, releases);
        Simulator simulator = new Simulator();
        var field = Simulator.class.getDeclaredField("webPool"); field.setAccessible(true); field.set(simulator, pool);
        Auftrag order = new Auftrag(1, "NEG-TEST", "SWEET-POOL", "", "", List.of(), List.of());
        for (int i = 0; i < 1000; i++) simulator.sichereFachzustand(order);
        assertEquals(1000, borrows.get());
        assertEquals(1000, releases.get());
        assertEquals(1000, database.rollbacks.get());
        assertEquals(1000, database.readOnlyTransactions.get());
        assertEquals(5000, database.selects.get());
        assertEquals(0, database.physicalCloses.get(), "Snapshots dürfen keine Poolverbindung physisch schließen");
    }

    @Test void lesefehlerRolltZurueckUndGibtPoolverbindungOhneWiederholungFrei() throws Exception {
        DatabaseProbe database = new DatabaseProbe();
        AtomicInteger borrows = new AtomicInteger(), releases = new AtomicInteger();
        Simulator simulator = new Simulator();
        var field = Simulator.class.getDeclaredField("webPool"); field.setAccessible(true);
        field.set(simulator, pool(database.connection, borrows, releases));
        Auftrag order = new Auftrag(1, "NEG-TEST", "SWEET-POOL", "", "", List.of(), List.of());
        database.failSelect.set(true);
        assertThrows(SQLException.class, () -> simulator.sichereFachzustand(order));
        assertEquals(1, borrows.get());
        assertEquals(1, releases.get());
        assertEquals(1, database.rollbacks.get());
        assertEquals(0, database.physicalCloses.get());
        database.failSelect.set(false);
        assertDoesNotThrow(() -> simulator.sichereFachzustand(order));
        assertEquals(2, releases.get());
    }

    private static ConnectionPool pool(Connection connection, AtomicInteger borrows, AtomicInteger releases) {
        return new ConnectionPool() {
            private boolean borrowed;
            public synchronized Connection getConnection() {
                assertFalse(borrowed, "Vorherige Ausleihe nicht zurückgegeben");
                borrowed = true; borrows.incrementAndGet(); return connection;
            }
            public synchronized void release(Connection returned) {
                assertSame(connection, returned); assertTrue(borrowed);
                borrowed = false; releases.incrementAndGet();
            }
            public void close() { fail("Simulator-Snapshot darf den Pool nicht schließen"); }
        };
    }

    private static final class DatabaseProbe {
        final AtomicInteger rollbacks = new AtomicInteger(), readOnlyTransactions = new AtomicInteger(),
                selects = new AtomicInteger(), physicalCloses = new AtomicInteger();
        final AtomicBoolean failSelect = new AtomicBoolean();
        final ResultSetMetaData metadata = proxy(ResultSetMetaData.class, (o, m, a) -> switch(m.getName()) {
            case "getColumnCount" -> 0;
            default -> throw new UnsupportedOperationException(m.getName());
        });
        final Connection connection = proxy(Connection.class, (object, method, args) -> switch (method.getName()) {
            case "setAutoCommit" -> null;
            case "rollback" -> { rollbacks.incrementAndGet(); yield null; }
            case "close" -> { physicalCloses.incrementAndGet(); yield null; }
            case "createStatement" -> proxy(Statement.class, (o, m, a) -> switch(m.getName()) {
                case "execute" -> { assertEquals("SET TRANSACTION READ ONLY", a[0]); readOnlyTransactions.incrementAndGet(); yield true; }
                case "close" -> null;
                default -> throw new UnsupportedOperationException(m.getName());
            });
            case "prepareStatement" -> {
                if (failSelect.get()) throw new SQLException("CONTROLLED_SNAPSHOT_READ_FAILURE");
                selects.incrementAndGet();
                yield proxy(PreparedStatement.class, (o, m, a) -> switch(m.getName()) {
                    case "setString", "close" -> null;
                    case "executeQuery" -> proxy(ResultSet.class, (r, rm, ra) -> switch(rm.getName()) {
                        case "getMetaData" -> metadata;
                        case "next" -> false;
                        case "close" -> null;
                        default -> throw new UnsupportedOperationException(rm.getName());
                    });
                    default -> throw new UnsupportedOperationException(m.getName());
                });
            }
            default -> throw new UnsupportedOperationException(method.getName());
        });
    }

    private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }
}
