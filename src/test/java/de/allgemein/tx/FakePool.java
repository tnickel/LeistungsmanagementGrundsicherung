package de.allgemein.tx;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Test-Pool mit dynamischen Connection-Proxies: zählt Commits, Rollbacks und
 * Releases, damit TxManager-Verhalten (Retry-Schleife, Error-Rollback, manuelle
 * API) ohne Datenbank assertbar ist. Kein echtes SQL — TxWork-Ergebnisse kommen
 * aus dem Test selbst.
 */
public final class FakePool implements ConnectionPool {

    public final AtomicInteger neueVerbindungen = new AtomicInteger();
    public final AtomicInteger commits = new AtomicInteger();
    public final AtomicInteger rollbacks = new AtomicInteger();
    public final AtomicInteger releases = new AtomicInteger();

    private final Semaphore permits;
    private final long borrowTimeoutMs;

    public FakePool(int maxSize) {
        this.permits = new Semaphore(maxSize);
        this.borrowTimeoutMs = 500;
    }

    @Override
    public Connection getConnection() throws SQLException {
        boolean bekommen;
        try {
            bekommen = permits.tryAcquire(borrowTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SQLException("unterbrochen", e);
        }
        if (!bekommen) {
            throw new SQLException("Pool erschöpft (Test)");
        }
        neueVerbindungen.incrementAndGet();
        return verbindung();
    }

    @Override
    public void release(Connection connection) {
        if (connection != null) {
            releases.incrementAndGet();
            permits.release();
        }
    }

    @Override
    public void close() {
        // nichts zu tun
    }

    private Connection verbindung() {
        InvocationHandler handler = new InvocationHandler() {
            private boolean autoCommit = true;

            @Override
            public Object invoke(Object proxy, Method method, Object[] args) {
                String name = method.getName();
                if ("commit".equals(name)) {
                    commits.incrementAndGet();
                    return null;
                }
                if ("rollback".equals(name)) {
                    rollbacks.incrementAndGet();
                    return null;
                }
                if ("setAutoCommit".equals(name)) {
                    autoCommit = (Boolean) args[0];
                    return null;
                }
                if ("getAutoCommit".equals(name)) {
                    return autoCommit;
                }
                if ("isClosed".equals(name)) {
                    return false;
                }
                if ("isValid".equals(name)) {
                    return true;
                }
                if ("close".equals(name)) {
                    return null;
                }
                Class<?> typ = method.getReturnType();
                if (typ == boolean.class) {
                    return false;
                }
                if (typ.isPrimitive()) {
                    return 0;
                }
                return null;
            }
        };
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(), new Class<?>[]{Connection.class}, handler);
    }
}
