package de.allgemein.tx;

import oracle.ucp.jdbc.PoolDataSource;
import oracle.ucp.jdbc.PoolDataSourceFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Produktions-Verbindungspool auf Basis des Oracle Universal Connection Pool (UCP):
 * Validierung beim Entleihen, begrenztes Warten auf freie Verbindungen und
 * JDBC-Connect-/Read-Timeouts gegen hängende Aufrufe. Fast Connection Failover (FCF)
 * und RAC-Load-Balancing sind NICHT konfiguriert (dafür wären ONS/FCF-Parameter
 * erforderlich) — der Pool bedient eine Single-Instance-/EE-Standardumgebung.
 */
public final class UcpConnectionPool implements ConnectionPool {

    private static final Logger LOG = Logger.getLogger(UcpConnectionPool.class.getName());

    private final PoolDataSource poolDataSource;

    public UcpConnectionPool(String url, String user, String password, int initialSize, int maxSize) throws SQLException {
        this(url, user, password, initialSize, maxSize, 15_000, 120_000);
    }

    /**
     * @param connectTimeoutMs TCP-Aufbau-Timeout für neue Verbindungen (oracle.net.CONNECT_TIMEOUT)
     * @param readTimeoutMs    Lese-Timeout für JDBC-Aufrufe (oracle.jdbc.ReadTimeout) —
     *                         begrenzt hängende Calls auch ohne TaskController-Wachhund
     */
    public UcpConnectionPool(String url, String user, String password, int initialSize, int maxSize,
            int connectTimeoutMs, int readTimeoutMs) throws SQLException {
        this.poolDataSource = PoolDataSourceFactory.getPoolDataSource();
        this.poolDataSource.setConnectionFactoryClassName("oracle.jdbc.pool.OracleDataSource");
        this.poolDataSource.setURL(url);
        this.poolDataSource.setUser(user);
        this.poolDataSource.setPassword(password);
        this.poolDataSource.setInitialPoolSize(Math.max(1, initialSize));
        this.poolDataSource.setMinPoolSize(1);
        this.poolDataSource.setMaxPoolSize(Math.max(1, maxSize));
        this.poolDataSource.setValidateConnectionOnBorrow(true);
        // Explizit statt UCP-Default (3 s): analog zum SimpleConnectionPool-Borrow-Timeout
        this.poolDataSource.setConnectionWaitTimeout(30);
        // Verbindungen, die nach dem Ausleihen nie zurückgegeben werden (z. B. TxContext.close()
        // ohne finish()), gelten nach 5 Minuten als „abandoned“ und kehren zum Pool zurück
        this.poolDataSource.setAbandonedConnectionTimeout(300);
        java.util.Properties dbProps = new java.util.Properties();
        dbProps.setProperty("oracle.net.CONNECT_TIMEOUT", String.valueOf(Math.max(100, connectTimeoutMs)));
        dbProps.setProperty("oracle.jdbc.ReadTimeout", String.valueOf(Math.max(100, readTimeoutMs)));
        this.poolDataSource.setConnectionProperties(dbProps);
        LOG.info(() -> "Oracle UCP initialisiert für " + url + " (Min=1, Max=" + maxSize
                + ", WaitTimeout=30s, ReadTimeout=" + readTimeoutMs + "ms)");
    }

    public UcpConnectionPool(String url, String user, String password, int maxSize) throws SQLException {
        this(url, user, password, Math.min(2, maxSize), maxSize);
    }

    @Override
    public Connection getConnection() throws SQLException {
        return poolDataSource.getConnection();
    }

    /**
     * Gibt die Verbindung an den UCP zurück. Zwei Sicherheitsregeln:
     * <ul>
     *   <li><b>Kein impliziter Commit:</b> JDBC sieht vor, dass
     *       {@code setAutoCommit(true)} eine offene Transaktion committet. Deshalb
     *       wird vor dem Umschalten grundsätzlich {@code rollback()} ausgeführt
     *       (quietly) — auch wenn der TxManager nach einem {@code Error} (OOM o. ä.)
     *       abbricht, darf kein Teilstand festgeschrieben werden.</li>
     *   <li><b>Kein Leak im Fehlerpfad:</b> Wirft {@code isClosed()}/
     *       {@code getAutoCommit()} bereits eine {@code SQLException} (tote
     *       Verbindung nach DB-Neustart), wird {@code close()} trotzdem versucht —
     *       sonst bliebe die Verbindung im UCP dauerhaft als „borrowed“ stehen und
     *       der Pool liefe leer.</li>
     * </ul>
     */
    @Override
    public void release(Connection connection) {
        if (connection == null) {
            return;
        }
        try {
            if (!connection.isClosed()) {
                if (!connection.getAutoCommit()) {
                    rollbackQuietly(connection);
                    connection.setAutoCommit(true);
                }
                // Bei UCP gibt connection.close() die physische Verbindung an den Pool zurück
                connection.close();
            }
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "Fehler beim Zurückgeben der Verbindung an den UCP, Verbindung wird geschlossen", e);
            closeQuietly(connection);
        }
    }

    private void rollbackQuietly(Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException e) {
            LOG.log(Level.FINE, "Rollback vor dem Zurückstellen fehlgeschlagen: " + e.getMessage(), e);
        }
    }

    private void closeQuietly(Connection connection) {
        try {
            connection.close();
        } catch (SQLException | RuntimeException ignored) {
            // nichts zu holen, Verbindung ist bereits weg — Hauptsache, sie hängt
            // nicht mehr als „borrowed“ im UCP
        }
    }

    private volatile javax.management.ObjectName jmxObjectName;

    public synchronized javax.management.ObjectName registerJmx() {
        if (this.jmxObjectName == null) {
            this.jmxObjectName = de.allgemein.tx.monitoring.TxMonitoring.registerUcpPool(this);
        }
        return this.jmxObjectName;
    }

    @Override
    public void close() {
        LOG.info("Oracle UCP wird geschlossen.");
        if (jmxObjectName != null) {
            de.allgemein.tx.monitoring.TxMonitoring.unregister(jmxObjectName);
            jmxObjectName = null;
        }
        try {
            String poolName = poolDataSource.getConnectionPoolName();
            if (poolName != null) {
                // Achtung: destroyConnectionPool wirkt über den JVM-weiten UCP-Singleton-
                // Manager — bei mehreren Pool-Instanzen (z. B. parallele Tests) wird der
                // Pool dieses Namens global entfernt.
                oracle.ucp.admin.UniversalConnectionPoolManager mgr =
                        oracle.ucp.admin.UniversalConnectionPoolManagerImpl.getUniversalConnectionPoolManager();
                mgr.destroyConnectionPool(poolName);
            }
        } catch (Exception e) {
            LOG.log(Level.FINE, "Hinweis beim Beenden des Oracle UCP Pools", e);
        }
    }

    /**
     * Liefert die zugrunde liegende PoolDataSource für erweiterte Metriken/JMX.
     */
    public PoolDataSource getPoolDataSource() {
        return poolDataSource;
    }
}
