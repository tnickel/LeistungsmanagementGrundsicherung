package de.allgemein.tx.saga;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Unvollendete Saga aus dem Journal — Grundlage für den Wiederanlauf (Recovery). */
public final class UnfinishedSaga {

    private final String sagaId;
    private final String type;
    private final String payload;
    private final SagaState state;
    private final Map<Integer, String> stepStates = new LinkedHashMap<>();

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

    public boolean isStepDone(int index) {
        return "DONE".equals(stepStates.get(index));
    }

    public boolean isStepCompensated(int index) {
        return "COMPENSATED".equals(stepStates.get(index));
    }
}
