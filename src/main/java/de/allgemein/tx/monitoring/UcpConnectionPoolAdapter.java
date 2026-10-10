package de.allgemein.tx.monitoring;

import de.allgemein.tx.UcpConnectionPool;
import oracle.ucp.jdbc.PoolDataSource;

import java.sql.SQLException;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Standard-MBean-Adapter für den {@link UcpConnectionPool}.
 */
public final class UcpConnectionPoolAdapter implements UcpConnectionPoolMBean {

    private static final Logger LOG = Logger.getLogger(UcpConnectionPoolAdapter.class.getName());

    private final UcpConnectionPool pool;

    public UcpConnectionPoolAdapter(UcpConnectionPool pool) {
        this.pool = Objects.requireNonNull(pool, "pool");
    }

    @Override
    public int getBorrowedConnections() {
        try {
            return pool.getPoolDataSource().getBorrowedConnectionsCount();
        } catch (SQLException e) {
            LOG.log(Level.FINE, "Konnte borrowedConnections nicht ermitteln", e);
            return -1;
        }
    }

    @Override
    public int getAvailableConnections() {
        try {
            return pool.getPoolDataSource().getAvailableConnectionsCount();
        } catch (SQLException e) {
            LOG.log(Level.FINE, "Konnte availableConnections nicht ermitteln", e);
            return -1;
        }
    }

    @Override
    public int getTotalConnections() {
        int borrowed = getBorrowedConnections();
        int available = getAvailableConnections();
        if (borrowed < 0 || available < 0) {
            return -1;
        }
        return borrowed + available;
    }

    @Override
    public int getMinPoolSize() {
        return pool.getPoolDataSource().getMinPoolSize();
    }

    @Override
    public int getMaxPoolSize() {
        return pool.getPoolDataSource().getMaxPoolSize();
    }

    @Override
    public int getInitialPoolSize() {
        return pool.getPoolDataSource().getInitialPoolSize();
    }

    @Override
    public String getConnectionPoolName() {
        return pool.getPoolDataSource().getConnectionPoolName();
    }

    @Override
    public String getUrl() {
        return pool.getPoolDataSource().getURL();
    }

    @Override
    public String getUser() {
        return pool.getPoolDataSource().getUser();
    }
}
