package de.allgemein.tx;

/**
 * Das sind die möglichen Stände einer Datenbankaktion.
 * Sie ist entweder noch offen, gespeichert, zurückgenommen, abgebrochen
 * oder der Speicherausgang ist unbekannt.
 *
 * Lebenszyklus einer von der Transaktionssteuerung geführten Transaktion.
 */
public enum TxStatus {
    ACTIVE,
    COMMITTED,
    COMMIT_OUTCOME_UNKNOWN,
    ROLLED_BACK,
    ABORTED
}