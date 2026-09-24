package de.allgemein.tx;

import java.sql.Connection;
import java.time.Duration;
import java.util.Objects;

/** Optionen einer Transaktion: Isolationsebene, Zeitlimit und Anzahl der Versuche. */
public record TxOptions(int isolation, Duration timeout, int maxAttempts) {

    public static final TxOptions DEFAULT =
            new TxOptions(Connection.TRANSACTION_READ_COMMITTED, Duration.ofSeconds(30), 3);

    public TxOptions {
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("timeout muss positiv sein");
        }
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts muss >= 1 sein");
        }
    }

    public TxOptions withTimeout(Duration newTimeout) {
        return new TxOptions(isolation, newTimeout, maxAttempts);
    }

    public TxOptions withMaxAttempts(int newMaxAttempts) {
        return new TxOptions(isolation, timeout, newMaxAttempts);
    }

    public TxOptions withIsolation(int newIsolation) {
        return new TxOptions(newIsolation, timeout, maxAttempts);
    }
}
