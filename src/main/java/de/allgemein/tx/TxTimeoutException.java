package de.allgemein.tx;

/**
 * Dieser Fehler heißt: die Datenbankaktion hat ihr Zeitlimit überschritten
 * oder wurde vorher abgebrochen. Vor dem Speichern gilt sie als nicht fertig.
 *
 * Zeitlimit oder Interrupt vor dem Commit dieser Transaktion.
 */
public final class TxTimeoutException extends TxException {
    TxTimeoutException(String message) {
        super(message, null, false, -1);
    }
}