package de.allgemein.tx;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.time.Duration;
import java.time.Instant;

/**
 * Steuert genau eine laufende Oracle-Transaktion (Vorbild: CICS-Task mit SYNCPOINT).
 * Die Verbindung gehört zu diesem Zeitpunkt dem Aufrufer; der Pool holt sie
 * nach Abschluss über {@code release()} zurück.
 */
public final class TxContext implements AutoCloseable {

    private final Connection connection;
    private final Instant deadline;
    private volatile TxStatus status = TxStatus.ACTIVE;

    TxContext(Connection connection, Duration timeout) throws SQLException {
        connection.setAutoCommit(false);
        this.connection = connection;
        this.deadline = Instant.now().plus(timeout);
    }

    public Connection connection() {
        if (status != TxStatus.ACTIVE) {
            throw new IllegalStateException("Transaktion bereits beendet: " + status);
        }
        return connection;
    }

    /**
     * Löst die Verbindung ohne Statusprüfung aus dem Kontext — ausschließlich für
     * {@link TxManager#finish(TxContext)}, das die Verbindung nach dem Beenden der
     * Transaktion an den Pool zurückgeben muss (connection() würde nach close()
     * zu Recht eine IllegalStateException werfen).
     */
    Connection detachConnection() {
        return connection;
    }

    public TxStatus status() {
        return status;
    }

    public Duration remaining() {
        return Duration.between(Instant.now(), deadline);
    }

    /** Zwischen den Schritten aufrufen: bricht ab, sobald das Zeitlimit überschritten ist. */
    public void checkDeadline() {
        if (remaining().isNegative()) {
            throw TxException.timeout("Zeitlimit der Transaktion überschritten");
        }
    }

    /** Markierung innerhalb der Transaktion, zu der teilweise zurückgerollt werden kann. */
    public Savepoint savepoint() throws SQLException {
        return connection().setSavepoint();
    }

    public void rollbackTo(Savepoint savepoint) throws SQLException {
        connection().rollback(savepoint);
    }

    void markCommitted() {
        status = TxStatus.COMMITTED;
    }

    void markRolledBack() {
        status = TxStatus.ROLLED_BACK;
    }

    /**
     * Sicherheitsnetz: eine vergessene offene Transaktion wird zurückgerollt.
     *
     * <p><b>Achtung (CR-018):</b> {@code close()} gibt die Verbindung NICHT an den Pool
     * zurück — das leistet ausschließlich {@link TxManager#finish(TxContext)}. Wer
     * try-with-resources auf dem Kontext nutzt, MUSS zusätzlich {@code finish()} aufrufen,
     * sonst bleibt die Verbindung dauerhaft ausgeliehen (der Pool läuft ohne
     * Leak-Erkennung leer).</p>
     */
    @Override
    public void close() {
        if (status == TxStatus.ACTIVE) {
            try {
                connection.rollback();
            } catch (SQLException ignored) {
                // Verbindung ist vermutlich eh hinüber; der Pool wirft sie beim Freigeben weg
            }
            status = TxStatus.ABORTED;
        }
    }
}
