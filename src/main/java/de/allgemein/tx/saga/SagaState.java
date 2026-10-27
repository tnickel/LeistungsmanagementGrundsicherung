package de.allgemein.tx.saga;

/**
 * Kurz gesagt: Das sind die möglichen Stände einer Saga.
 * Läuft sie noch? Ist sie fertig? Ist sie gescheitert? Wird sie gerade
 * rückgängig gemacht oder wurde das schon abgeschlossen?
 *
 * Lebenszyklus eines Saga-Geschäftsvorgangs, dauerhaft im Journal festgehalten.
 */
public enum SagaState {
    RUNNING,
    COMPLETED,
    FAILED,
    COMPENSATING,
    COMPENSATED
}