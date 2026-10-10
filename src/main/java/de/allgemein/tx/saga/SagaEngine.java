package de.allgemein.tx.saga;

import de.allgemein.tx.TxException;
import de.allgemein.tx.TxManager;
import de.allgemein.tx.TxOptions;
import de.allgemein.tx.TxWork;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Führt lange Geschäftsvorgänge als Kette kurzer Datenbanktransaktionen aus (Saga-Muster):
 * Jeder Schritt committet für sich und wird im Journal festgehalten. Scheitert ein Schritt,
 * werden alle bisherigen Schritte rückwärts kompensiert. Nach einem Absturz beendet
 * {@link #recover(SagaDefinition)} halbfertige Vorgänge sauber.
 */
public final class SagaEngine {

    private static final Logger LOG = Logger.getLogger(SagaEngine.class.getName());

    private final TxManager txManager;
    private final SagaJournal journal;

    public SagaEngine(TxManager txManager, SagaJournal journal) {
        this.txManager = txManager;
        this.journal = journal;
    }

    /** Führt die Saga vollständig aus; bei Fehlern wird rückwärts kompensiert und TxException geworfen. */
    public String run(SagaDefinition definition, String payload) {
        String sagaId = UUID.randomUUID().toString();
        List<SagaStep> steps = definition.steps();
        try {
            journal.start(sagaId, definition.type(), payload, steps.size());
            de.allgemein.tx.audit.TxAuditLog.recordSagaStart(sagaId, definition.type(), payload);
        } catch (SQLException e) {
            throw TxException.nonRetryable("Saga-Journal nicht beschreibbar: " + e.getMessage(), e);
        }

        int doneCount = 0;
        try {
            for (SagaStep step : steps) {
                final int index = doneCount;
                TxWork<Void> work = ctx -> {
                    step.action().run(ctx.connection(), payload);
                    return null;
                };
                txManager.execute(TxOptions.DEFAULT, work);
                record(() -> journal.stepDone(sagaId, index, step.name()));
                de.allgemein.tx.audit.TxAuditLog.recordSagaStep(sagaId, index, step.name());
                doneCount++;
            }
            record(() -> journal.markState(sagaId, SagaState.COMPLETED));
            de.allgemein.tx.audit.TxAuditLog.recordSagaCompleted(sagaId, definition.type());
            return sagaId;
        } catch (TxException e) {
            record(() -> journal.markState(sagaId, SagaState.FAILED));
            de.allgemein.tx.audit.TxAuditLog.recordSagaCompensating(sagaId, e.getMessage());
            compensate(definition, payload, sagaId, doneCount);
            de.allgemein.tx.audit.TxAuditLog.recordSagaFailed(sagaId, e.getMessage());
            throw e;
        }
    }

    /**
     * Wiederanlauf nach Absturz oder Neustart: kompensiert alle halbfertigen Sagas des
     * gegebenen Typs. Rückgabe: Anzahl behandelter Vorgänge.
     */
    public int recover(SagaDefinition definition) {
        List<UnfinishedSaga> unfinished;
        try {
            unfinished = journal.unfinished();
        } catch (SQLException e) {
            throw TxException.nonRetryable("Saga-Journal nicht lesbar: " + e.getMessage(), e);
        }
        int recovered = 0;
        for (UnfinishedSaga saga : unfinished) {
            if (!saga.type().equals(definition.type())) {
                continue;
            }
            int maxDoneIndex = -1;
            for (var entry : saga.stepStates().entrySet()) {
                if ("DONE".equals(entry.getValue()) && entry.getKey() > maxDoneIndex) {
                    maxDoneIndex = entry.getKey();
                }
            }
            if (maxDoneIndex < 0) {
                // Der erste Schritt wurde nie committet — nichts zu kompensieren
                record(() -> journal.markState(saga.sagaId(), SagaState.FAILED));
                continue;
            }
            compensate(definition, saga.payload(), saga.sagaId(), maxDoneIndex + 1);
            recovered++;
        }
        return recovered;
    }

    /** Rollt die Schritte [0, doneCount) in umgekehrter Reihenfolge zurück; sprengt nicht bei Einzelfehlern. */
    private void compensate(SagaDefinition definition, String payload, String sagaId, int doneCount) {
        record(() -> journal.markState(sagaId, SagaState.COMPENSATING));
        boolean allCompensated = true;
        for (int i = doneCount - 1; i >= 0; i--) {
            final int index = i;
            final SagaStep step = definition.steps().get(i);
            try {
                if (journal.isStepCompensated(sagaId, index)) {
                    continue; // nach einem Absturz bereits erledigt
                }
            } catch (SQLException e) {
                allCompensated = false;
                LOG.log(Level.SEVERE, "Journal-Zugriff bei Kompensation fehlgeschlagen: " + e.getMessage());
                continue;
            }
            try {
                TxWork<Void> work = ctx -> {
                    step.compensation().run(ctx.connection(), payload);
                    return null;
                };
                txManager.execute(TxOptions.DEFAULT, work);
                record(() -> journal.stepCompensated(sagaId, index));
            } catch (Exception e) {
                allCompensated = false;
                LOG.log(Level.SEVERE,
                        "Kompensation von Schritt '" + step.name() + "' (Saga " + sagaId + ") fehlgeschlagen: " + e.getMessage(),
                        e);
            }
        }
        SagaState endState = allCompensated ? SagaState.COMPENSATED : SagaState.FAILED;
        record(() -> journal.markState(sagaId, endState));
    }

    private void record(JournalCall call) {
        try {
            call.run();
        } catch (SQLException e) {
            throw TxException.nonRetryable("Saga-Journal-Fehler: " + e.getMessage(), e);
        }
    }

    @FunctionalInterface
    private interface JournalCall {
        void run() throws SQLException;
    }
}
