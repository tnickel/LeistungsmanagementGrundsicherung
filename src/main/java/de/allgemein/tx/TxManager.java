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
        this.pool = java.util.Objects.requireNonNull(pool, "pool");
        this.retryPolicy = java.util.Objects.requireNonNull(retryPolicy, "retryPolicy");
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
                    de.allgemein.tx.audit.TxAuditLog.recordTxRollback(txId, attempt, durationMillis, de.allgemein.tx.audit.TxAuditLog.fehlerBeschreibung(e));
                    throw e;
                }
            } catch (SQLException e) {
                rollbackQuietly(raw);
                lastFailure = TxException.from(e);
                if (!lastFailure.isRetryable() || attempt == maxAttempts) {
                    long durationMillis = (System.nanoTime() - startNanos) / 1_000_000;
                    de.allgemein.tx.audit.TxAuditLog.recordTxRollback(txId, attempt, durationMillis, de.allgemein.tx.audit.TxAuditLog.fehlerBeschreibung(lastFailure));
                    throw lastFailure;
                }
            } catch (Exception e) {
                rollbackQuietly(raw);
                long durationMillis = (System.nanoTime() - startNanos) / 1_000_000;
                de.allgemein.tx.audit.TxAuditLog.recordTxRollback(txId, attempt, durationMillis, de.allgemein.tx.audit.TxAuditLog.fehlerBeschreibung(e));
                throw TxException.nonRetryable("Transaktion abgebrochen: " + e.getMessage(), e);
            } catch (Error e) {
                // OOM/StackOverflow o. ä.: Transaktion zurückrollen und den Error durchreichen.
                // Wichtig: die Pools dürfen beim release() nie implizit committen
                // (sie rollen vor setAutoCommit(true) selbst zurück).
                rollbackQuietly(raw);
                long durationMillis = (System.nanoTime() - startNanos) / 1_000_000;
                de.allgemein.tx.audit.TxAuditLog.recordTxRollback(txId, attempt, durationMillis, e.toString());
                throw e;
            } finally {
                if (raw != null) {
                    pool.release(raw);
                }
            }
            final int tried = attempt;
            final TxException failure = lastFailure;
            LOG.log(Level.WARNING,
                    () -> "Transaktionsversuch " + tried + "/" + maxAttempts
                            + " fehlgeschlagen, Wiederholung folgt: " + de.allgemein.tx.audit.TxAuditLog.fehlerBeschreibung(failure));
            de.allgemein.tx.audit.TxAuditLog.recordTxRetry(txId, attempt, de.allgemein.tx.audit.TxAuditLog.fehlerBeschreibung(failure));
            retryPolicy.sleepBeforeAttempt(attempt);
        }
        // Nicht erreichbar: die Schleife endet immer mit return oder throw
        // (maxAttempts >= 1 wird von TxOptions erzwungen).
        throw new AssertionError("unreachable");
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
        if (ctx.status() != TxStatus.ACTIVE) {
            return; // bereits committet oder abgebrochen — nichts zu tun
        }
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
        // Verbindung VOR close() aus dem Kontext lösen: close() beendet die
        // Transaktion und connection() würde danach eine IllegalStateException werfen.
        Connection raw = ctx.detachConnection();
        ctx.close();
        pool.release(raw);
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
