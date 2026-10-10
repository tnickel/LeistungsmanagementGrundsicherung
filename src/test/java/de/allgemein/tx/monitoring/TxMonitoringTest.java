package de.allgemein.tx.monitoring;

import de.allgemein.tx.SimpleConnectionPool;
import de.allgemein.tx.TaskController;
import de.allgemein.tx.TxManager;
import de.allgemein.tx.TxOptions;
import de.allgemein.tx.UcpConnectionPool;
import org.junit.jupiter.api.Test;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TxMonitoringTest {

    private final MBeanServer server = ManagementFactory.getPlatformMBeanServer();

    @Test
    void taskControllerWirdRegistriertUndLiefertStatistikenUeberJmx() throws Exception {
        var stubPool = new de.allgemein.tx.ConnectionPool() {
            @Override
            public java.sql.Connection getConnection() {
                return (java.sql.Connection) java.lang.reflect.Proxy.newProxyInstance(
                        getClass().getClassLoader(),
                        new Class<?>[]{java.sql.Connection.class},
                        (proxy, method, args) -> {
                            if ("getAutoCommit".equals(method.getName())) return false;
                            if ("isClosed".equals(method.getName())) return false;
                            return null;
                        });
            }
            @Override public void release(java.sql.Connection connection) {}
            @Override public void close() {}
        };
        var txManager = new TxManager(stubPool);

        try (TaskController controller = new TaskController(txManager, 2, 10)) {
            ObjectName name = controller.registerJmx();
            assertNotNull(name);
            assertTrue(server.isRegistered(name));

            // Vor der Ausführung: initial 0
            assertEquals(0L, server.getAttribute(name, "ActiveTasks"));
            assertEquals(0L, server.getAttribute(name, "CompletedTasks"));
            assertEquals(0L, server.getAttribute(name, "FailedTasks"));
            assertEquals(10, server.getAttribute(name, "QueueRemainingCapacity"));

            // Task ausführen (reine Berechnung ohne DB-Aufruf)
            var handle = controller.submit("test-task", TxOptions.DEFAULT, ctx -> "erfolg");
            assertEquals("erfolg", handle.result());

            // Nach der Ausführung: 1 CompletedTask
            assertEquals(1L, server.getAttribute(name, "CompletedTasks"));
            assertEquals(0L, server.getAttribute(name, "ActiveTasks"));
        }

        // Nach close(): MBean automatisch deregistriert
        ObjectName expectedName = new ObjectName(TxMonitoring.OBJECT_NAME_TASK_CONTROLLER);
        assertFalse(server.isRegistered(expectedName), "TaskController MBean sollte nach close() deregistriert sein");
    }

    @Test
    void ucpPoolWirdRegistriertUndLiefertMetrikenUeberJmx() throws Exception {
        try (UcpConnectionPool ucp = new UcpConnectionPool("jdbc:oracle:thin:@//localhost:1521/dummy", "u", "p", 1, 5)) {
            ObjectName name = ucp.registerJmx();
            assertNotNull(name);
            assertTrue(server.isRegistered(name));

            assertEquals(1, server.getAttribute(name, "InitialPoolSize"));
            assertEquals(1, server.getAttribute(name, "MinPoolSize"));
            assertEquals(5, server.getAttribute(name, "MaxPoolSize"));
            assertEquals("u", server.getAttribute(name, "User"));
            assertEquals("jdbc:oracle:thin:@//localhost:1521/dummy", server.getAttribute(name, "Url"));
        }

        ObjectName expectedName = new ObjectName(TxMonitoring.OBJECT_NAME_UCP_POOL);
        assertFalse(server.isRegistered(expectedName), "UCP MBean sollte nach close() deregistriert sein");
    }

    @Test
    void genericPoolWirdUeberTxMonitoringRegistriert() throws Exception {
        var pool = new SimpleConnectionPool("jdbc:dummy", "u", "p", 2);
        ObjectName name = TxMonitoring.registerConnectionPool(pool);
        assertNotNull(name);
        assertTrue(server.isRegistered(name));

        assertEquals("SimpleConnectionPool", server.getAttribute(name, "PoolType"));

        TxMonitoring.unregister(name);
        assertFalse(server.isRegistered(name));
    }
}
