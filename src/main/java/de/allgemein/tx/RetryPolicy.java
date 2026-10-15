package de.allgemein.tx;

import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Entscheidet, welche Datenbankfehler einen Wiederholversuch der ganzen Transaktion
 * rechtfertigen, und federt zwischen den Versuchen zurück (exponentieller Backoff).
 * Wiederholbar sind ausschließlich die folgenden Oracle-Sperrenkonflikte:
 * ORA-00054 (Ressource belegt), ORA-00060 (Deadlock), ORA-08177 (Serialisierbarkeitskonflikt).
 */
public final class RetryPolicy {

    private static final Set<Integer> RETRYABLE_ORA_CODES = Set.of(54, 60, 8177);
    /** Basis höchstens 30 s; einschließlich Jitter höchstens 45 s pro Pause. */
    static final long MAX_BACKOFF_MILLIS = 30_000;

    private final long initialBackoffMillis;

    public RetryPolicy() {
        this(200);
    }

    public RetryPolicy(long initialBackoffMillis) {
        if (initialBackoffMillis < 1) {
            throw new IllegalArgumentException("initialBackoffMillis muss >= 1 sein");
        }
        this.initialBackoffMillis = initialBackoffMillis;
    }

    /** Durchsucht NextException und Ursachen zyklensicher nach bekannten Sperrenkonflikten. */
    public static boolean isRetryable(SQLException e) {
        if (e == null) return false;
        var pending = new ArrayDeque<Throwable>();
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        pending.add(e);
        while (!pending.isEmpty()) {
            Throwable current = pending.removeFirst();
            if (!visited.add(current)) continue;
            if (current instanceof SQLException sql) {
                // SQLState 61000 umfasst auch Ressourcenfehler (u. a. ORA-04031).
                // Nur der konkrete Vendorcode erfüllt den engen Retry-Vertrag.
                if (RETRYABLE_ORA_CODES.contains(sql.getErrorCode())) {
                    return true;
                }
                if (sql.getNextException() != null) pending.addLast(sql.getNextException());
            }
            if (current.getCause() != null) pending.addLast(current.getCause());
        }
        return false;
    }

    long backoffMillis(int failedAttempt) {
        if (failedAttempt < 1) {
            throw new IllegalArgumentException("failedAttempt muss >= 1 sein");
        }
        long base = Math.min(initialBackoffMillis, MAX_BACKOFF_MILLIS);
        long multiplier = 1L << Math.min(failedAttempt - 1, 10);
        return base > MAX_BACKOFF_MILLIS / multiplier ? MAX_BACKOFF_MILLIS : base * multiplier;
    }

    long delayMillis(int failedAttempt) {
        long backoff = backoffMillis(failedAttempt);
        return backoff + ThreadLocalRandom.current().nextLong(backoff / 2 + 1);
    }

    /** Wartet vor dem nächsten Versuch: Backoff verdoppelt sich pro Fehlversuch, plus Zufallsanteil. */
    public void sleepBeforeAttempt(int failedAttempt) {
        long delay = delayMillis(failedAttempt);
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw TxException.nonRetryable("Warten auf Wiederholversuch unterbrochen", e);
        }
    }
}
