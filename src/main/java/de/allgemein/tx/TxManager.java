package de.allgemein.tx;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Das ist die Schaltstelle für eine Datenbankaktion.
 * Sie leiht eine Verbindung, lässt die Facharbeit laufen und speichert am Ende alles
 * oder nimmt alles zurück. Bei kurzen Sperrkonflikten versucht sie es noch einmal.
 * Fehlt die Speicherbestätigung, wird nicht geraten und nicht wiederholt.
 *
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
     * Führt die Arbeit in einer eigenen Transaktion aus: Commit bei Erfolg,
     * Rollback bei Fehlern vor dem Commit. Wiederholbare Datenbankkonflikte werden erneut versucht.
     * Eine fehlende Commit-Quittung bleibt ausdrücklich unbekannt und wird nie wiederholt.
     */
    public <T> T execute(TxOptions options, TxWork<T> work) {
        TxException lastFailure = null;
        int maxAttempts = options.maxAttempts();
        String txId = java.util.UUID.randomUUID().toString();
        long startNanos = System.nanoTime();
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            Connection raw = null;
            TxContext ctx = null;
            try {
                raw = pool.getConnection();
                raw.setTransactionIsolation(options.isolation());
                ctx = new TxContext(raw, options.timeout());
                ctx.checkDeadline();
                T result = work.run(ctx);
                commit(ctx);
                long durationMillis = (System.nanoTime() - startNanos) / 1_000_000;
                de.allgemein.tx.audit.TxAuditLog.recordTxCommit(txId, attempt, durationMillis, null);
                return result;
            } catch (TxCommitOutcomeUnknownException e) {
                // Best effort räumt eine eventuell noch offene Transaktion auf.
                // Es klärt NICHT den Ausgang des bereits versuchten Commits.
                rollbackQuietly(raw);
                de.allgemein.tx.audit.TxAuditLog.recordTxOutcomeUnknown(txId, attempt,
                        (System.nanoTime() - startNanos) / 1_000_000,
                        de.allgemein.tx.audit.TxAuditLog.fehlerBeschreibung(e));
                throw e;
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
                if (ctx != null && ctx.status() == TxStatus.COMMITTED) throw e;
                rollbackQuietly(raw);
                long durationMillis = (System.nanoTime() - startNanos) / 1_000_000;
                if (e instanceof TxCommitOutcomeUnknownError
                        || ctx != null && ctx.status() == TxStatus.COMMIT_OUTCOME_UNKNOWN) {
                    de.allgemein.tx.audit.TxAuditLog.recordTxOutcomeUnknown(txId, attempt, durationMillis,
                            de.allgemein.tx.audit.TxAuditLog.fehlerBeschreibung(e));
                } else de.allgemein.tx.audit.TxAuditLog.recordTxRollback(txId, attempt, durationMillis, e.toString());
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
            try {
                retryPolicy.sleepBeforeAttempt(attempt);
            } catch (TxException interrupted) {
                de.allgemein.tx.audit.TxAuditLog.recordTxRollback(txId, attempt,
                        (System.nanoTime() - startNanos) / 1_000_000,
                        de.allgemein.tx.audit.TxAuditLog.fehlerBeschreibung(interrupted));
                throw interrupted;
            }
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
        } catch (SQLException | RuntimeException | Error e) {
            pool.release(raw);
            throw e;
        }
    }

    /** Fehlende JDBC-Commitbestätigung beendet den Kontext mit unbekanntem Ausgang. */
    public void commit(TxContext ctx) throws SQLException {
        ctx.checkDeadline();
        Connection raw = ctx.connection();
        try {
            raw.commit();
        } catch (SQLException | RuntimeException e) {
            ctx.markCommitOutcomeUnknown();
            throw new TxCommitOutcomeUnknownException(e);
        } catch (Error e) {
            ctx.markCommitOutcomeUnknown();
            throw new TxCommitOutcomeUnknownError(e);
        }
        ctx.markCommitted();
    }

    public void rollback(TxContext ctx) {
        if (ctx.status() == TxStatus.COMMIT_OUTCOME_UNKNOWN) {
            rollbackQuietly(ctx.detachConnection());
            return; // vorheriger Commit bleibt trotz Aufräumversuch ungeklärt
        }
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
        try {
            ctx.close();
        } finally {
            pool.release(raw);
        }
    }

    private void rollbackQuietly(Connection raw) {
        if (raw == null) {
            return;
        }
        try {
            raw.rollback();
        } catch (SQLException | RuntimeException e) {
            LOG.log(Level.FINE, () -> "Rollback fehlgeschlagen: "
                    + de.allgemein.tx.audit.TxAuditLog.fehlerBeschreibung(e));
        }
    }
}