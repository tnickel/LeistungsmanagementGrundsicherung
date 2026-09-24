package de.allgemein.tx.saga;

import java.util.Objects;

/** Ein Schritt einer Saga zusammen mit der Kompensation, die ihn rückgängig macht. */
public record SagaStep(String name, SagaAction action, SagaAction compensation) {

    public SagaStep {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(compensation, "compensation");
    }
}
