package de.allgemein.tx;

import java.sql.SQLException;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Entscheidet, welche Datenbankfehler einen Wiederholversuch der ganzen Transaktion
 * rechtfertigen, und federt zwischen den Versuchen zurück (exponentieller Backoff).
 * Wiederholbar sind vor allem Sperren-Konflikte:
 * ORA-00054 (Ressource belegt), ORA-00060 (Deadlock), ORA-08177 (Serialisierbarkeitskonflikt).
 */
public final class RetryPolicy {

    private static final Set<Integer> RETRYABLE_ORA_CODES = Set.of(54, 60, 8177);
    private static final Set<String> RETRYABLE_SQL_STATES = Set.of("40001", "61000");

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

    /** Durchsucht die Oracle-Fehlerkette, ob irgendein Fehler wiederholbar ist. */
    public static boolean isRetryable(SQLException e) {
        SQLException current = e;
        while (current != null) {
            if (RETRYABLE_ORA_CODES.contains(current.getErrorCode())) {
                return true;
            }
            String sqlState = current.getSQLState();
            if (sqlState != null && RETRYABLE_SQL_STATES.contains(sqlState)) {
                return true;
            }
            current = current.getNextException();
        }
        return false;
    }

    /** Wartet vor dem nächsten Versuch: Backoff verdoppelt sich pro Fehlversuch, plus Zufallsanteil. */
    public void sleepBeforeAttempt(int failedAttempt) {
        long backoff = initialBackoffMillis << Math.min(failedAttempt - 1, 10);
        long jitter = ThreadLocalRandom.current().nextLong(backoff / 2 + 1);
        try {
            Thread.sleep(backoff + jitter);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw TxException.nonRetryable("Warten auf Wiederholversuch unterbrochen", e);
        }
    }
}
