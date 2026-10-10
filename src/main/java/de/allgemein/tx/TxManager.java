package de.allgemein.tx;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Kern der Transaktionssteuerung: führt eine Arbeitseinheit als Oracle-Transaktion aus.
 * Oracle selbst liefert ACID, Sperrverwaltung, Redo-Logging und Recovery — gesteuert
 * werden hier nur Begin/Commit/Rollback, das Zeitlimit und die Wiederholung bei
 * Sperren-Konflikten.
 */
public final class TxManager {

    private static final Logger LOG = Logger.getLogger(TxManager.class.getName());

    private final ConnectionPool pool;
    private final RetryPolicy retryPolicy;

    public TxManager(ConnectionPool pool) {
        this(pool, new RetryPolicy());
    }

    public TxManager(ConnectionPool pool, RetryPolicy retryPolicy) {
        this.pool = pool;
        this.retryPolicy = retryPolicy;
    }

    /**
     * Führt die Arbeit in einer eigenen Transaktion aus: Commit bei Erfolg, Rollback
     * bei jedem Fehler; wiederholbare Datenbankkonflikte werden erneut versucht.
     */
    public <T> T execute(TxOptions options, TxWork<T> work) {
        TxException lastFailure = null;
        int maxAttempts = options.maxAttempts();
        String txId = java.util.UUID.randomUUID().toString();
        long startNanos = System.nanoTime();
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            Connection raw = null;
            try {
                raw = pool.getConnection();
                raw.setTransactionIsolation(options.isolation());
                TxContext ctx = new TxContext(raw, options.timeout());
                T result = work.run(ctx);
                ctx.checkDeadline();
                raw.commit();
                ctx.markCommitted();
                long durationMillis = (System.nanoTime() - startNanos) / 1_000_000;
                de.allgemein.tx.audit.TxAuditLog.recordTxCommit(txId, attempt, durationMillis, null);
                return result;
            } catch (TxException e) {
                rollbackQuietly(raw);
                lastFailure = e;
                if (!e.isRetryable() || attempt == maxAttempts) {
                    long durationMillis = (System.nanoTime() - startNanos) / 1_000_000;
                    de.allgemein.tx.audit.TxAuditLog.recordTxRollback(txId, attempt, durationMillis, e.getMessage());
                    throw e;
                }
            } catch (SQLException e) {
                rollbackQuietly(raw);
                lastFailure = TxException.from(e);
                if (!lastFailure.isRetryable() || attempt == maxAttempts) {
                    long durationMillis = (System.nanoTime() - startNanos) / 1_000_000;
                    de.allgemein.tx.audit.TxAuditLog.recordTxRollback(txId, attempt, durationMillis, lastFailure.getMessage());
                    throw lastFailure;
                }
            } catch (Exception e) {
                rollbackQuietly(raw);
                long durationMillis = (System.nanoTime() - startNanos) / 1_000_000;
                de.allgemein.tx.audit.TxAuditLog.recordTxRollback(txId, attempt, durationMillis, e.getMessage());
                throw TxException.nonRetryable("Transaktion abgebrochen: " + e.getMessage(), e);
            } finally {
                if (raw != null) {
                    pool.release(raw);
                }
            }
            final int tried = attempt;
            final TxException failure = lastFailure;
            LOG.log(Level.WARNING,
                    () -> "Transaktionsversuch " + tried + "/" + maxAttempts
                            + " fehlgeschlagen, Wiederholung folgt: " + failure.getMessage());
            de.allgemein.tx.audit.TxAuditLog.recordTxRetry(txId, attempt, failure.getMessage());
            retryPolicy.sleepBeforeAttempt(attempt);
        }
        long durationMillis = (System.nanoTime() - startNanos) / 1_000_000;
        de.allgemein.tx.audit.TxAuditLog.recordTxRollback(txId, maxAttempts, durationMillis,
                lastFailure != null ? lastFailure.getMessage() : "unbekannt");
        throw lastFailure;
    }

    /** Beginnt eine manuell gesteuerte Transaktion; danach commit()/rollback() und finish(). */
    public TxContext begin(TxOptions options) throws SQLException {
        Connection raw = pool.getConnection();
        try {
            raw.setTransactionIsolation(options.isolation());
            return new TxContext(raw, options.timeout());
        } catch (SQLException e) {
            pool.release(raw);
            throw e;
        }
    }

    public void commit(TxContext ctx) throws SQLException {
        ctx.checkDeadline();
        ctx.connection().commit();
        ctx.markCommitted();
    }

    public void rollback(TxContext ctx) {
        try {
            ctx.connection().rollback();
        } catch (SQLException e) {
            LOG.log(Level.WARNING, () -> "Rollback fehlgeschlagen: " + e.getMessage());
        } finally {
            ctx.markRolledBack();
        }
    }

    /** Gibt die Verbindung nach einer manuellen Transaktion in den Pool zurück. */
    public void finish(TxContext ctx) {
        ctx.close();
        pool.release(ctx.connection());
    }

    private void rollbackQuietly(Connection raw) {
        if (raw == null) {
            return;
        }
        try {
            raw.rollback();
        } catch (SQLException e) {
            LOG.log(Level.FINE, () -> "Rollback fehlgeschlagen: " + e.getMessage());
        }
    }
}
