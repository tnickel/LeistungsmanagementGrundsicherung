package de.allgemein.tx;

/**
 * Dieser Fehler heißt: man weiß nicht, ob der Auftrag schon gespeichert wurde.
 * Ein Abbruch beweist keinen Rückgängig-Schritt.
 * Deshalb darf man den Auftrag nicht einfach noch einmal anlegen.
 *
 * Der Aufrufer kennt den Ausgang des Workers nicht: abgebrochenes Warten oder
 * verlorene Commit-Quittung. Ein Interrupt ist kein Rollbackbeleg, insbesondere
 * nach inneren, bereits committeten Transaktionen.
 */
public final class TaskOutcomeUnknownException extends TxException {
    public TaskOutcomeUnknownException(String message, Throwable cause) {
        super(message, cause, false, -1);
    }
}