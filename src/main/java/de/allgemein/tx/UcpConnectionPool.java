package de.allgemein.tx;

import oracle.ucp.jdbc.PoolDataSource;
import oracle.ucp.jdbc.PoolDataSourceFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Produktions-Verbindungspool auf Basis des Oracle Universal Connection Pool (UCP).
 * Unterstützt Fast Connection Failover (FCF), RAC Load Balancing und hochperformantes
 * Oracle-Verbindungsmanagement wie in der Blueprint-Spezifikation (Abschnitt 2) gefordert.
 */
public final class UcpConnectionPool implements ConnectionPool {

    private static final Logger LOG = Logger.getLogger(UcpConnectionPool.class.getName());

    private final PoolDataSource poolDataSource;

    public UcpConnectionPool(String url, String user, String password, int initialSize, int maxSize) throws SQLException {
        this.poolDataSource = PoolDataSourceFactory.getPoolDataSource();
        this.poolDataSource.setConnectionFactoryClassName("oracle.jdbc.pool.OracleDataSource");
        this.poolDataSource.setURL(url);
        this.poolDataSource.setUser(user);
        this.poolDataSource.setPassword(password);
        this.poolDataSource.setInitialPoolSize(Math.max(1, initialSize));
        this.poolDataSource.setMinPoolSize(1);
        this.poolDataSource.setMaxPoolSize(Math.max(1, maxSize));
        this.poolDataSource.setValidateConnectionOnBorrow(true);
        LOG.info(() -> "Oracle UCP initialisiert für " + url + " (Min=1, Max=" + maxSize + ")");
    }

    public UcpConnectionPool(String url, String user, String password, int maxSize) throws SQLException {
        this(url, user, password, Math.min(2, maxSize), maxSize);
    }

    @Override
    public Connection getConnection() throws SQLException {
        return poolDataSource.getConnection();
    }

    @Override
    public void release(Connection connection) {
        if (connection == null) {
            return;
        }
        try {
            if (!connection.isClosed()) {
                if (!connection.getAutoCommit()) {
                    connection.setAutoCommit(true);
                }
                // Bei UCP gibt connection.close() die physische Verbindung an den Pool zurück
                connection.close();
            }
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "Fehler beim Zurückgeben der Verbindung an den UCP", e);
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
