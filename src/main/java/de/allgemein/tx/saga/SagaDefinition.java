package de.allgemein.tx.saga;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Kurz gesagt: Das ist der Bauplan einer Saga.
 * Er hält fest, wie der Vorgang heißt und welche Schritte es gibt — jeder
 * Schritt mit seinem Namen, seiner Aktion und der Gegen-Aktion zum Rückgängigmachen.
 * Der Ablaufleiter (SagaEngine) arbeitet später genau diesen Bauplan ab.
 *
 * Feste Abfolge von Schritten eines Geschäftsvorgangs. Schrittnamen sind stabile
 * Recovery-Schlüssel und müssen innerhalb des Typs eindeutig bleiben. Ändert sich
 * die Bedeutung einer Wirkung/Kompensation inkompatibel, braucht sie einen neuen
 * Namen beziehungsweise einen neuen Saga-Typ; alte Kompensationen bleiben für
 * offene Journale verfügbar.
 */
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
            if (steps.stream().anyMatch(step -> step.name().equals(name))) {
                throw new IllegalArgumentException("Schrittnamen müssen innerhalb einer Saga eindeutig sein");
            }
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