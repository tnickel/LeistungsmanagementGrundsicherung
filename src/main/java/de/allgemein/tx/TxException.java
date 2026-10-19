package de.allgemein.tx;

import java.sql.SQLException;

/**
 * Fehler der Transaktionssteuerung. {@code retryable} sagt, ob ein erneuter Versuch
 * der ganzen Transaktion sinnvoll sein kann (z. B. Deadlock), oder nicht (z. B.
 * Verletzung einer Prüfbedingung).
 */
public class TxException extends RuntimeException {

    private final boolean retryable;
    private final int oracleErrorCode;

    public TxException(String message) {
        this(message, null, false, -1);
    }

    public TxException(String message, Throwable cause, boolean retryable, int oracleErrorCode) {
        super(message, cause);
        this.retryable = retryable;
        this.oracleErrorCode = oracleErrorCode;
    }

    public static TxException from(SQLException e) {
        return new TxException(
                "Datenbankfehler ORA-" + String.format("%05d", e.getErrorCode()) + ": " + e.getMessage(),
                e, RetryPolicy.isRetryable(e), e.getErrorCode());
    }

    public static TxException timeout(String message) {
        return new TxTimeoutException(message);
    }

    public static TxException nonRetryable(String message, Throwable cause) {
        return new TxException(message, cause, false, -1);
    }

    public boolean isRetryable() {
        return retryable;
    }

    public int oracleErrorCode() {
        return oracleErrorCode;
    }
}
