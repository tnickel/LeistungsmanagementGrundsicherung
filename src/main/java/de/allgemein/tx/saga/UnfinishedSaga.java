package de.allgemein.tx.saga;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Kurz gesagt: Das ist der Aktenbericht einer steckengebliebenen Saga.
 * Er sagt: Wie heißt sie, was war der Auftrag, und wie weit ist sie gekommen —
 * pro Schritt ein Zustand. Genau diese Informationen braucht der Wiederanlauf,
 * um zu entscheiden, was noch rückgängig gemacht werden muss.
 *
 * Unvollendete Saga aus dem Journal — Grundlage für den Wiederanlauf (Recovery).
 * Ein Schritt im Zustand IN_FLIGHT gilt als „möglicherweise committet" und wird
 * beim Wiederanlauf konservativ mit kompensiert (idempotente Kompensationen
 * vorausgesetzt).
 */
public final class UnfinishedSaga {

    /** Zustände eines Schritts im Journal (TX_SAGA_STEP.STATE). */
    public static final String STEP_PENDING = "PENDING";
    public static final String STEP_IN_FLIGHT = "IN_FLIGHT";
    public static final String STEP_DONE = "DONE";
    public static final String STEP_COMPENSATED = "COMPENSATED";

    private final String sagaId;
    private final String type;
    private final String payload;
    private final SagaState state;
    private final Map<Integer, String> stepStates = new LinkedHashMap<>();
    private final Map<Integer, String> stepNames = new LinkedHashMap<>();

    UnfinishedSaga(String sagaId, String type, String payload, SagaState state) {
        this.sagaId = sagaId;
        this.type = type;
        this.payload = payload;
        this.state = state;
    }

    public String sagaId() {
        return sagaId;
    }

    public String type() {
        return type;
    }

    public String payload() {
        return payload;
    }

    public SagaState state() {
        return state;
    }

    public Map<Integer, String> stepStates() {
        return Collections.unmodifiableMap(stepStates);
    }

    void putStepState(int index, String state) {
        stepStates.put(index, state);
    }

    void putStepName(int index, String name) {
        stepNames.put(index, name);
    }

    public String stepName(int index) {
        return stepNames.get(index);
    }

    public boolean isStepDone(int index) {
        return STEP_DONE.equals(stepStates.get(index));
    }

    public boolean isStepInFlight(int index) {
        return STEP_IN_FLIGHT.equals(stepStates.get(index));
    }

    /**
     * Der Schritt könnte committet gewesen sein (DONE oder IN_FLIGHT) — beim
     * Wiederanlauf muss er daher mit kompensiert werden.
     */
    public boolean isStepPossiblyCommitted(int index) {
        return isStepDone(index) || isStepInFlight(index);
    }

    public boolean isStepCompensated(int index) {
        return STEP_COMPENSATED.equals(stepStates.get(index));
    }
}