package de.allgemein.tx;

/** Lebenszyklus einer von der Transaktionssteuerung geführten Transaktion. */
public enum TxStatus {
    ACTIVE,
    COMMITTED,
    COMMIT_OUTCOME_UNKNOWN,
    ROLLED_BACK,
    ABORTED
}
