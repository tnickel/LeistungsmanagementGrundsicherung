package de.allgemein.tx;

/** Zeitlimit oder Interrupt vor dem Commit dieser Transaktion. */
public final class TxTimeoutException extends TxException {
    TxTimeoutException(String message) {
        super(message, null, false, -1);
    }
}
