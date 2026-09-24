package de.allgemein.tx.saga;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Feste Abfolge von Schritten eines Geschäftsvorgangs (z. B. Antrag → Bescheid → Auszahlung). */
public final class SagaDefinition {

    private final String type;
    private final List<SagaStep> steps;

    private SagaDefinition(String type, List<SagaStep> steps) {
        this.type = type;
        this.steps = steps;
    }

    public String type() {
        return type;
    }

    public List<SagaStep> steps() {
        return steps;
    }

    public static Builder of(String type) {
        return new Builder(type);
    }

    public static final class Builder {

        private final String type;
        private final List<SagaStep> steps = new ArrayList<>();

        private Builder(String type) {
            this.type = Objects.requireNonNull(type, "type");
        }

        public Builder step(String name, SagaAction action, SagaAction compensation) {
            steps.add(new SagaStep(name, action, compensation));
            return this;
        }

        public SagaDefinition build() {
            if (steps.isEmpty()) {
                throw new IllegalStateException("Eine Saga braucht mindestens einen Schritt");
            }
            return new SagaDefinition(type, List.copyOf(steps));
        }
    }
}
