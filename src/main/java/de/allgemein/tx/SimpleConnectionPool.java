package de.allgemein.tx;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Semaphore;

/**
 * Minimaler Verbindungspool für Demo und Übergangsphase. Für die Produktion besser
 * HikariCP oder Oracle UCP einsetzen — die Steuerung hängt nur an dieser schmalen
 * Schnittstelle (getConnection/release) und kann den Pool daher leicht tauschen.
 */
public final class SimpleConnectionPool implements AutoCloseable {

    private final String url;
    private final String user;
    private final String password;
    private final BlockingQueue<Connection> idle;
    private final Semaphore permits;

    public SimpleConnectionPool(String url, String user, String password, int maxSize) {
        this.url = url;
        this.user = user;
        this.password = password;
        this.idle = new ArrayBlockingQueue<>(maxSize);
        this.permits = new Semaphore(maxSize);
    }

    public Connection getConnection() throws SQLException {
        permits.acquireUninterruptibly();
        boolean success = false;
        try {
            Connection connection = idle.poll();
            while (connection != null && connection.isClosed()) {
                connection = idle.poll();
            }
            if (connection == null) {
                connection = DriverManager.getConnection(url, user, password);
            }
            success = true;
            return connection;
        } finally {
            if (!success) {
                permits.release();
            }
        }
    }

    public void release(Connection connection) {
        try {
            if (connection.isClosed()) {
                return;
            }
            if (!connection.getAutoCommit()) {
                connection.setAutoCommit(true);
            }
            if (!idle.offer(connection)) {
                closeQuietly(connection);
            }
        } catch (SQLException e) {
            closeQuietly(connection);
        } finally {
            // Das Permit wird in jedem Pfad genau einmal zurückgegeben — auch wenn
            // die Verbindung defekt ist und verworfen wird, sonst leert der Pool
            // und getConnection blockt ewig.
            permits.release();
        }
    }

    @Override
    public void close() {
        List<Connection> remaining = new ArrayList<>();
        idle.drainTo(remaining);
        for (Connection connection : remaining) {
            closeQuietly(connection);
        }
    }

    private void closeQuietly(Connection connection) {
        try {
            connection.close();
        } catch (SQLException ignored) {
            // nichts zu holen, Verbindung ist bereits weg
        }
    }
}
