package de.allgemein.tx.saga;

/** Lebenszyklus eines Saga-Geschäftsvorgangs, dauerhaft im Journal festgehalten. */
public enum SagaState {
    RUNNING,
    COMPLETED,
    FAILED,
    COMPENSATING,
    COMPENSATED
}
