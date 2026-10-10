package de.allgemein.tx.monitoring;

import de.allgemein.tx.ConnectionPool;

import java.util.Objects;

/**
 * Standard-MBean-Adapter für allgemeine Verbindungspools.
 */
public final class GenericConnectionPoolAdapter implements GenericConnectionPoolMBean {

    private final ConnectionPool pool;

    public GenericConnectionPoolAdapter(ConnectionPool pool) {
        this.pool = Objects.requireNonNull(pool, "pool");
    }

    @Override
    public String getPoolType() {
        return pool.getClass().getSimpleName();
    }
}
