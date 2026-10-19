package de.allgemein.tx.saga;

import java.util.Objects;

/** Ein Schritt einer Saga zusammen mit der Kompensation, die ihn rückgängig macht. */
public record SagaStep(String name, SagaAction action, SagaAction compensation) {

    public SagaStep {
        Objects.requireNonNull(name, "name");
        if (name.isBlank() || name.codePointCount(0, name.length()) > 100) {
            throw new IllegalArgumentException("Schrittname muss 1 bis 100 Zeichen enthalten");
        }
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(compensation, "compensation");
    }
}
