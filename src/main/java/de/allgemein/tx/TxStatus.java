package de.allgemein.tx;

/** Lebenszyklus einer von der Transaktionssteuerung geführten Transaktion. */
public enum TxStatus {
    ACTIVE,
    COMMITTED,
    ROLLED_BACK,
    ABORTED
}
