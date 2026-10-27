package de.allgemein.tx.monitoring;

import de.allgemein.tx.ConnectionPool;
import de.allgemein.tx.TaskController;
import de.allgemein.tx.UcpConnectionPool;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.StandardMBean;
import java.lang.management.ManagementFactory;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Kurz gesagt: Das ist der Anmeldehelfer für die Überwachung.
 * Er meldet Pool und Task-Steuerung an die JVM-Verwaltungsstelle (JMX) an,
 * damit man im Betrieb von außen — etwa mit JConsole — nachsehen kann,
 * wie viele Aufträge laufen und wie es um den Verbindungspool steht.
 *
 * Zentrale Registrierung und Verwaltung von JMX-MBeans für die Transaktionssteuerung:
 * <ul>
 *   <li>TaskController: Überwachung von Parallelität, Durchlauf, Fehlern, Timeouts</li>
 *   <li>UcpConnectionPool: Oracle UCP Metriken (aktive/freie Verbindungen, Grenzen)</li>
 *   <li>GenericConnectionPool: Pool-Typ für sonstige Verbindungspools</li>
 * </ul>
 */
public final class TxMonitoring {

    private static final Logger LOG = Logger.getLogger(TxMonitoring.class.getName());

    public static final String JMX_DOMAIN = "de.allgemein.tx";
    public static final String OBJECT_NAME_TASK_CONTROLLER = JMX_DOMAIN + ":type=TaskController";
    public static final String OBJECT_NAME_UCP_POOL = JMX_DOMAIN + ":type=ConnectionPool,name=Ucp";
    public static final String OBJECT_NAME_GENERIC_POOL = JMX_DOMAIN + ":type=ConnectionPool,name=Default";

    private TxMonitoring() {
    }

    /** Registriert den TaskController als JMX-Standard-MBean. */
    public static ObjectName registerTaskController(TaskController controller) {
        return register(OBJECT_NAME_TASK_CONTROLLER, () ->
                new StandardMBean(new TaskControllerAdapter(controller), TaskControllerMBean.class));
    }

    /** Registriert den UcpConnectionPool als JMX-Standard-MBean. */
    public static ObjectName registerUcpPool(UcpConnectionPool pool) {
        return register(OBJECT_NAME_UCP_POOL, () ->
                new StandardMBean(new UcpConnectionPoolAdapter(pool), UcpConnectionPoolMBean.class));
    }

    /** Registriert einen ConnectionPool; erkennt UcpConnectionPool automatisch. */
    public static ObjectName registerConnectionPool(ConnectionPool pool) {
        if (pool instanceof UcpConnectionPool ucp) {
            return registerUcpPool(ucp);
        }
        return register(OBJECT_NAME_GENERIC_POOL, () ->
                new StandardMBean(new GenericConnectionPoolAdapter(pool), GenericConnectionPoolMBean.class));
    }

    /** Hebt die Registrierung einer MBean auf. */
    public static synchronized void unregister(ObjectName objectName) {
        if (objectName == null) {
            return;
        }
        try {
            MBeanServer server = ManagementFactory.getPlatformMBeanServer();
            if (server.isRegistered(objectName)) {
                server.unregisterMBean(objectName);
                LOG.info(() -> "JMX-MBean deregistriert: " + objectName);
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Konnte MBean nicht deregistrieren: " + objectName, e);
        }
    }

    private static synchronized ObjectName register(String nameStr, MBeanSupplier supplier) {
        try {
            MBeanServer server = ManagementFactory.getPlatformMBeanServer();
            ObjectName objectName = new ObjectName(nameStr);
            if (server.isRegistered(objectName)) {
                // Eine zweite Steuerung darf die MBean der ersten weder ersetzen
                // noch später beim close() versehentlich deren Nachfolger abmelden.
                objectName = new ObjectName(nameStr + ",instance=" + java.util.UUID.randomUUID());
            }
            server.registerMBean(supplier.get(), objectName);
            LOG.info("JMX-MBean erfolgreich registriert: " + objectName);
            return objectName;
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Fehler bei der JMX-Registrierung von " + nameStr, e);
            return null;
        }
    }

    @FunctionalInterface
    private interface MBeanSupplier {
        Object get() throws Exception;
    }
}