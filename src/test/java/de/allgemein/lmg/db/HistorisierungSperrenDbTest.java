package de.allgemein.lmg.db;

import de.allgemein.lmg.model.SozialFall;
import de.allgemein.lmg.service.Auszahlungsauftrag;
import de.allgemein.lmg.service.FachlicheException;
import de.allgemein.lmg.service.SozialLeistungsService;
import de.allgemein.tx.ConnectionPool;
import de.allgemein.tx.SimpleConnectionPool;
import de.allgemein.tx.TxManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

/** Echte Oracle-Sperren: Historisierung muss vor konkurrierender Facharbeit sichtbar werden. */
@EnabledIfEnvironmentVariable(named = "TX_URL", matches = ".+")
class HistorisierungSperrenDbTest {
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    /** Der Proxy beobachtet die echte SQL-Ausführung, ohne die getesteten Queries umzuschreiben. */
    private static final class ProbePool implements ConnectionPool {
        private final SimpleConnectionPool delegate = new SimpleConnectionPool(
                System.getenv("TX_URL"), System.getenv("TX_USER"), System.getenv("TX_PASS"), 4);
        private final Map<Connection, Connection> originals = Collections.synchronizedMap(new IdentityHashMap<>());
        private volatile CountDownLatch bgRead;

        @Override
        public Connection getConnection() throws java.sql.SQLException {
            Connection raw = delegate.getConnection();
            Connection proxy = (Connection) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[]{Connection.class}, (p, method, args) -> {
                        try {
                            if (method.getName().equals("prepareStatement") && args[0] instanceof String sql) {
                                PreparedStatement statement = raw.prepareStatement(sql);
                                String query = sql;
                                return Proxy.newProxyInstance(getClass().getClassLoader(),
                                        new Class<?>[]{PreparedStatement.class}, (s, operation, values) -> {
                                            if (operation.getName().equals("executeQuery")
                                                    && query.contains("FROM BEDARFSGEMEINSCHAFT")
                                                    && !query.startsWith("SELECT z.ID") && bgRead != null) {
                                                bgRead.countDown();
                                            }
                                            try {
                                                return operation.invoke(statement, values);
                                            } catch (InvocationTargetException e) {
                                                throw e.getCause();
                                            }
                                        });
                            }
                            return method.invoke(raw, args);
                        } catch (InvocationTargetException e) {
                            throw e.getCause();
                        }
                    });
            originals.put(proxy, raw);
            return proxy;
        }

        @Override public void release(Connection connection) { delegate.release(originals.remove(connection)); }
        @Override public void close() { delegate.close(); }
    }

    private static Connection connect() throws Exception {
        Connection connection = DriverManager.getConnection(
                System.getenv("TX_URL"), System.getenv("TX_USER"), System.getenv("TX_PASS"));
        connection.setAutoCommit(false);
        return connection;
    }

    private static SozialFall draft(long bgId, String rent) {
        return new SozialFall(0, bgId, 1, false, false, new BigDecimal(rent), ZERO, ZERO, ZERO, ZERO, 1);
    }

    private static long createBg(SozialLeistungsService service) {
        return service.legeBedarfsgemeinschaftAn("BG-HSP-" + UUID.randomUUID(), "Sperrenprüfung",
                new BigDecimal("9999.00"), null, "sb-sperre").id();
    }

    private static void historizeUncommitted(Connection connection, long bgId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE BEDARFSGEMEINSCHAFT SET STATUS_AKTIV = 0 WHERE ID = ?")) {
            statement.setLong(1, bgId);
            assertEquals(1, statement.executeUpdate());
        }
    }

    private static void clean(long bgId) throws Exception {
        try (Connection connection = connect()) {
            for (String table : new String[]{"JOURNAL", "ZAHLUNG", "SOZIAL_FALL"}) {
                try (PreparedStatement statement = connection.prepareStatement("DELETE FROM " + table + " WHERE BG_ID = ?")) {
                    statement.setLong(1, bgId);
                    statement.executeUpdate();
                }
            }
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM BEDARFSGEMEINSCHAFT WHERE ID = ?")) {
                statement.setLong(1, bgId);
                statement.executeUpdate();
            }
            connection.commit();
        }
    }

    @Test
    void fallAenderungWartetAufHistorisierungUndWirdDannAbgewiesen() throws Exception {
        try (ProbePool pool = new ProbePool()) {
            SozialLeistungsService service = new SozialLeistungsService(new TxManager(pool));
            long bgId = createBg(service);
            try {
                SozialFall original = service.legeSozialFallAn(bgId, draft(bgId, "300"), "sb-sperre");
                SozialFall changed = new SozialFall(original.id(), bgId, 1, false, false,
                        new BigDecimal("400"), ZERO, ZERO, ZERO, ZERO, original.version());
                try (Connection historian = connect(); var executor = Executors.newSingleThreadExecutor()) {
                    historizeUncommitted(historian, bgId);
                    pool.bgRead = new CountDownLatch(1);
                    var future = executor.submit(() -> service.aktualisiereSozialFall(changed, original.version(), "sb-edit"));
                    try {
                        assertTrue(pool.bgRead.await(5, TimeUnit.SECONDS), "Falländerung hat die BG-Prüfung erreicht");
                        assertThrows(TimeoutException.class, () -> future.get(300, TimeUnit.MILLISECONDS),
                                "Falländerung darf eine laufende Historisierung nicht überholen");
                    } finally {
                        historian.commit();
                    }
                    ExecutionException failure = assertThrows(ExecutionException.class, () -> future.get(10, TimeUnit.SECONDS));
                    assertInstanceOf(FachlicheException.class, failure.getCause());
                    assertTrue(failure.getCause().getMessage().contains("historisiert"));
                }
                var detail = service.ladeDetail(bgId);
                assertFalse(detail.bedarfsgemeinschaft().statusAktiv());
                assertEquals(0, detail.faelle().get(0).kduMiete().compareTo(new BigDecimal("300")));
            } finally { clean(bgId); }
        }
    }

    @Test
    void fallAnlageWartetAufHistorisierungUndWirdDannAbgewiesen() throws Exception {
        try (ProbePool pool = new ProbePool()) {
            SozialLeistungsService service = new SozialLeistungsService(new TxManager(pool));
            long bgId = createBg(service);
            try {
                try (Connection historian = connect(); var executor = Executors.newSingleThreadExecutor()) {
                    historizeUncommitted(historian, bgId);
                    pool.bgRead = new CountDownLatch(1);
                    var future = executor.submit(() -> service.legeSozialFallAn(bgId, draft(bgId, "300"), "sb-edit"));
                    try {
                        assertTrue(pool.bgRead.await(5, TimeUnit.SECONDS));
                        assertThrows(TimeoutException.class, () -> future.get(300, TimeUnit.MILLISECONDS));
                    } finally { historian.commit(); }
                    ExecutionException failure = assertThrows(ExecutionException.class, () -> future.get(10, TimeUnit.SECONDS));
                    assertInstanceOf(FachlicheException.class, failure.getCause());
                }
                assertTrue(service.ladeDetail(bgId).faelle().isEmpty());
            } finally { clean(bgId); }
        }
    }

    @Test
    void kassenlaufUeberholtKeineLaufendeHistorisierung() throws Exception {
        try (ProbePool pool = new ProbePool()) {
            SozialLeistungsService service = new SozialLeistungsService(new TxManager(pool));
            long bgId = createBg(service);
            try {
                service.legeSozialFallAn(bgId, draft(bgId, "300"), "sb-sperre");
                var payment = service.ordneAuszahlungAn(new Auszahlungsauftrag(bgId, "HSP-" + UUID.randomUUID(),
                        "2026-10", "sb-sperre", false, null, null, null)).zahlung();
                service.freigebenZahlung(payment.id(), "sb-zweit");
                String scope = service.ladeDetail(bgId).bedarfsgemeinschaft().aktenzeichen();
                try (Connection historian = connect(); var executor = Executors.newSingleThreadExecutor()) {
                    historizeUncommitted(historian, bgId);
                    pool.bgRead = new CountDownLatch(1);
                    var future = executor.submit(() -> service.uebergebeBereiteZahlungen("admin-test", scope));
                    try {
                        assertTrue(pool.bgRead.await(5, TimeUnit.SECONDS), "Kassenlauf prüft Aktivität unter BG-Sperre");
                        assertThrows(TimeoutException.class, () -> future.get(300, TimeUnit.MILLISECONDS));
                    } finally { historian.commit(); }
                    assertEquals(0, future.get(10, TimeUnit.SECONDS).anzahl());
                }
                var detail = service.ladeDetail(bgId);
                assertEquals("BEREIT", detail.zahlungen().get(0).status());
                assertEquals(0, detail.bedarfsgemeinschaft().budgetRest().compareTo(new BigDecimal("9136")));
            } finally { clean(bgId); }
        }
    }
}
