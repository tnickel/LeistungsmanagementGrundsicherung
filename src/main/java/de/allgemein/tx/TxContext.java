package de.allgemein.tx;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.time.Duration;
import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * Steuert genau eine laufende Oracle-Transaktion (Vorbild: CICS-Task mit SYNCPOINT).
 * Die Verbindung gehört zu diesem Zeitpunkt dem Aufrufer; der Pool holt sie
 * nach Abschluss über {@code release()} zurück.
 */
public final class TxContext implements AutoCloseable {

    private final Connection connection;
    private final Duration timeout;
    private final LongSupplier nanoTime;
    private final long startedAtNanos;
    private volatile TxStatus status = TxStatus.ACTIVE;

    TxContext(Connection connection, Duration timeout) throws SQLException {
        this(connection, timeout, System::nanoTime);
    }

    /** Stellbare monotone Uhr für deterministische Grenztests, ohne Änderung der Systemzeit. */
    TxContext(Connection connection, Duration timeout, LongSupplier nanoTime) throws SQLException {
        this.timeout = Objects.requireNonNull(timeout, "timeout");
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
        connection.setAutoCommit(false);
        this.connection = connection;
        this.startedAtNanos = nanoTime.getAsLong();
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
        // Differenzbildung funktioniert auch über einen nanoTime-Zählerüberlauf.
        // Die Wanduhr (NTP, manuelle Zeitkorrektur) beeinflusst das Budget nicht.
        return timeout.minusNanos(nanoTime.getAsLong() - startedAtNanos);
    }

    /** Zwischen den Schritten aufrufen: bricht ab, sobald das Zeitlimit überschritten ist. */
    public void checkDeadline() {
        if (Thread.currentThread().isInterrupted()) {
            throw TxException.timeout("Transaktion vor Commit unterbrochen");
        }
        Duration left = remaining();
        if (left.isNegative() || left.isZero()) {
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

    void markCommitOutcomeUnknown() {
        status = TxStatus.COMMIT_OUTCOME_UNKNOWN;
    }

    /**
     * Sicherheitsnetz: eine vergessene offene Transaktion wird zurückgerollt.
     * Bei unbekanntem Commit-Ausgang ist dies nur Aufräumen; UNKNOWN bleibt erhalten.
     *
     * <p><b>Achtung (CR-018):</b> {@code close()} gibt die Verbindung NICHT an den Pool
     * zurück — das leistet ausschließlich {@link TxManager#finish(TxContext)}. Wer
     * try-with-resources auf dem Kontext nutzt, MUSS zusätzlich {@code finish()} aufrufen,
     * sonst bleibt die Verbindung dauerhaft ausgeliehen (der Pool läuft ohne
     * Leak-Erkennung leer).</p>
     */
    @Override
    public void close() {
        if (status == TxStatus.ACTIVE || status == TxStatus.COMMIT_OUTCOME_UNKNOWN) {
            try {
                connection.rollback();
            } catch (SQLException ignored) {
                // Verbindung ist vermutlich eh hinüber; der Pool wirft sie beim Freigeben weg
            }
            // Aufräumen kann eine noch offene Transaktion beenden, aber einen
            // möglicherweise schon ausgeführten Commit nicht rückgängig machen.
            if (status == TxStatus.ACTIVE) status = TxStatus.ABORTED;
        }
    }
}
