package de.allgemein.tx;

/**
 * Der Aufrufer kennt den Ausgang des Workers nicht: abgebrochenes Warten oder
 * verlorene Commit-Quittung. Ein Interrupt ist kein Rollbackbeleg, insbesondere
 * nach inneren, bereits committeten Transaktionen.
 */
public final class TaskOutcomeUnknownException extends TxException {
    public TaskOutcomeUnknownException(String message, Throwable cause) {
        super(message, cause, false, -1);
    }
}
