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
 *
 * <p>Ablauf eines Schritts (Write-Ahead-Prinzip, Fund K1):</p>
 * <ol>
 *   <li>Journal: Schritt PENDING → IN_FLIGHT (eigener kurzer Commit).</li>
 *   <li>Fachschritt + Journal IN_FLIGHT → DONE in EINER Transaktion — die
 *       Markierung kann nicht hinter dem Commit des Fachschritts zurückbleiben.</li>
 * </ol>
 * <p>Ein Absturz kann damit keinen committeten Schritt mehr verbergen: Der Wiederanlauf
 * behandelt IN_FLIGHT konservativ als „möglicherweise committet" und kompensiert mit
 * (deshalb MÜSSEN alle Kompensationen idempotent sein).</p>
 */
public final class SagaEngine {

    private static final Logger LOG = Logger.getLogger(SagaEngine.class.getName());

    private final TxManager txManager;
    private final TxSagaJournal journal;

    public SagaEngine(TxManager txManager, TxSagaJournal journal) {
        this.txManager = txManager;
        this.journal = journal;
    }

    /**
     * Führt die Saga vollständig aus; bei Fehlern wird rückwärts kompensiert und TxException
     * geworfen. Schlägt lediglich die abschließende COMPLETED-Markierung fehl, wird NICHT
     * kompensiert (die Fachwirkung ist vollständig): Die Saga bleibt RUNNING und der nächste
     * Wiederanlauf zieht die Markierung idempotent nach.
     */
    public String run(SagaDefinition definition, String payload) {
        String sagaId = UUID.randomUUID().toString();
        List<SagaStep> steps = definition.steps();
        record(() -> journal.start(sagaId, definition.type(), payload, steps.size()));
        de.allgemein.tx.audit.TxAuditLog.recordSagaStart(sagaId, definition.type(), payload);

        int doneCount = 0;
        try {
            for (SagaStep step : steps) {
                final int index = doneCount;
                // (a) Write-Ahead: Schritt als begonnen markieren, BEVOR die Fachtransaktion läuft.
                record(() -> journal.markStepInFlight(sagaId, index));
                // (b) Fachschritt und Journal-Eintrag in derselben Transaktion (atomarer Commit).
                TxWork<Void> work = ctx -> {
                    step.action().run(ctx.connection(), payload, sagaId);
                    journal.stepDone(ctx.connection(), sagaId, index, step.name());
                    return null;
                };
                txManager.execute(TxOptions.DEFAULT, work);
                de.allgemein.tx.audit.TxAuditLog.recordSagaStep(sagaId, index, step.name());
                doneCount++;
            }
        } catch (TxException e) {
            // Fachfehler oder Write-Ahead-Fehler: rückwärts kompensieren, dann Exception
            // weiterreichen. Die FAILED-Markierung darf die Kompensation NIE verhindern
            // (Fund H4/M6): ihr Scheitern wird nur protokolliert.
            markiereStill(sagaId, SagaState.FAILED, "FAILED");
            de.allgemein.tx.audit.TxAuditLog.recordSagaCompensating(sagaId, e.getMessage());
            compensate(definition, payload, sagaId, doneCount);
            de.allgemein.tx.audit.TxAuditLog.recordSagaFailed(sagaId, e.getMessage());
            throw e;
        }
        // Alle Schritte erfolgreich: Die COMPLETED-Markierung läuft bewusst NACH dem
        // Kompensations-try — ihr Fehler darf KEINE Fehl-Kompensation der fachlich
        // erfolgreichen Saga auslösen (Fund H4).
        try {
            journal.markState(sagaId, SagaState.COMPLETED);
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Saga " + sagaId + " fachlich abgeschlossen, aber die COMPLETED-"
                    + "Markierung schlug fehl: " + e.getMessage()
                    + " — die Saga bleibt RUNNING; der nächste Wiederanlauf schließt sie ab.", e);
            throw TxException.nonRetryable("Saga-Journal-Fehler beim Abschließen der Saga " + sagaId
                    + " (alle Fachschritte sind committet): " + e.getMessage(), e);
        }
        de.allgemein.tx.audit.TxAuditLog.recordSagaCompleted(sagaId, definition.type());
        return sagaId;
    }

    // ------------------------------------------------------------------
    // Wiederanlauf (Recovery)
    // ------------------------------------------------------------------

    /** Ergebnis eines Wiederanlaufs: Anzahl behandler Sagas je Endzustand. */
    public record RecoveryReport(int completed, int compensated, int failed, int errors) {

        /** Gesamtzahl behandelter (d. h. in einen Endzustand überführter) Sagas. */
        public int behandelt() {
            return completed + compensated + failed;
        }

        /** Problematische Fälle: unvollständig Kompensierte und technische Fehler. */
        public int fehlerhaft() {
            return failed + errors;
        }
    }

    /**
     * Wiederanlauf nach Absturz oder Neustart: kompensiert alle halbfertigen Sagas des
     * gegebenen Typs und schließt fachlich fertige Sagas ab. Rückgabe: Anzahl behandelter
     * Vorgänge. Wirft nie — Fehler je Saga werden gezählt und protokolliert (Fund M3).
     */
    public int recover(SagaDefinition definition) {
        return recoverWithReport(definition).behandelt();
    }

    /**
     * Wie {@link #recover(SagaDefinition)}, aber mit Fehlerzählung je Endzustand.
     *
     * <p>Ablauf: Erst eine ungesperrte Übersicht ({@link TxSagaJournal#unfinished()}),
     * dann wird jede Saga einzeln mit {@code SELECT … FOR UPDATE SKIP LOCKED} beansprucht
     * (Fund H1): Ein paralleler Wiederanlauf — z. B. zweiter Server — erhält die gesperrte
     * Saga nicht und arbeitet die nächste ab; „lebende" Sagas sind durch die Kopf-Sperre
     * während der Bearbeitung geschützt. Ein Fehler EINER Saga bricht den Wiederanlauf
     * nicht ab (Fund M3).</p>
     */
    public RecoveryReport recoverWithReport(SagaDefinition definition) {
        int completed = 0;
        int compensated = 0;
        int failed = 0;
        int errors = 0;
        List<UnfinishedSaga> kandidaten;
        try {
            kandidaten = journal.unfinished();
        } catch (SQLException e) {
            // Journal nicht lesbar: blind weiterlaufen wäre falsch — abbrechen,
            // aber nicht werfen (Fund M3: der Serverstart darf nicht gefährdet werden).
            LOG.log(Level.SEVERE, "Saga-Journal nicht lesbar für Typ " + definition.type()
                    + ": " + e.getMessage() + " — Wiederanlauf für diesen Typ abgebrochen.", e);
            return new RecoveryReport(0, 0, 0, 1);
        }
        for (UnfinishedSaga kandidat : kandidaten) {
            if (!definition.type().equals(kandidat.type())) {
                continue;
            }
            SagaClaim claim;
            try {
                claim = journal.claimUnfinished(kandidat.sagaId(), definition.type());
            } catch (SQLException e) {
                errors++;
                LOG.log(Level.SEVERE, "Anspruch auf Saga " + kandidat.sagaId() + " fehlgeschlagen: "
                        + e.getMessage() + " — wird übersprungen, der nächste Wiederanlauf versucht sie erneut.", e);
                continue;
            }
            if (claim == null) {
                continue; // inzwischen abgeschlossen oder von parallelem Wiederanlauf gesperrt
            }
            UnfinishedSaga saga = claim.saga();
            try (claim) {
                switch (wiederanlauf(saga, definition, claim)) {
                    case COMPLETED -> completed++;
                    case COMPENSATED -> compensated++;
                    case FAILED -> failed++;
                    default -> errors++;
                }
            } catch (Exception e) {
                errors++;
                LOG.log(Level.SEVERE, "Wiederanlauf der Saga " + saga.sagaId() + " (Typ "
                        + saga.type() + ") fehlgeschlagen: " + e.getMessage()
                        + " — Saga bleibt unverändert, der nächste Wiederanlauf versucht sie erneut.", e);
                // claim.close() über try-with-resources: Rollback, Kopf unverändert
            }
        }
        return new RecoveryReport(completed, compensated, failed, errors);
    }

    /** Verarbeitet eine beanspruchte Saga; liefert den erreichten Endzustand. */
    private SagaState wiederanlauf(UnfinishedSaga saga, SagaDefinition definition, SagaClaim claim)
            throws SQLException {
        List<SagaStep> steps = definition.steps();

        // Fund H4: fachlich fertige Saga nur noch abschließen — die COMPLETED-Markierung
        // wird idempotent nachgezogen, wenn alle Schritte DONE sind (Absturzfenster zwischen
        // letztem Schritt-Commit und Markierung). Keine Kompensation!
        if (saga.state() == SagaState.RUNNING && alleSchritteDone(saga, steps)) {
            claim.finish(SagaState.COMPLETED);
            return SagaState.COMPLETED;
        }

        boolean hatBegonnen = saga.stepStates().values().stream()
                .anyMatch(s -> UnfinishedSaga.STEP_DONE.equals(s)
                        || UnfinishedSaga.STEP_IN_FLIGHT.equals(s)
                        || UnfinishedSaga.STEP_COMPENSATED.equals(s));
        if (!hatBegonnen) {
            // Der erste Schritt wurde nie committet — nichts zu kompensieren.
            claim.finish(SagaState.FAILED);
            return SagaState.FAILED;
        }

        // Fund K1c: rückwärts kompensieren; IN_FLIGHT gilt konservativ als
        // „möglicherweise committet" und wird mit kompensiert (idempotente Kompensationen).
        // Bereits COMPENSATED markierte Schritte (früherer Absturz) werden übersprungen.
        boolean vollstaendig = true;
        for (int i = steps.size() - 1; i >= 0; i--) {
            if (!saga.isStepPossiblyCommitted(i)) {
                continue; // PENDING (nie gelaufen) oder COMPENSATED (schon erledigt)
            }
            if (saga.isStepCompensated(i)) {
                continue; // nach früherem Absturz bereits erledigt — idempotent weiter
            }
            final int index = i;
            SagaStep step = steps.get(i);
            try {
                txManager.execute(TxOptions.DEFAULT, ctx -> {
                    step.compensation().run(ctx.connection(), saga.payload(), saga.sagaId());
                    journal.stepCompensated(ctx.connection(), saga.sagaId(), index);
                    return null;
                });
            } catch (Exception e) {
                vollstaendig = false;
                LOG.log(Level.SEVERE, "Kompensation von Schritt '" + step.name() + "' (Saga "
                        + saga.sagaId() + ") im Wiederanlauf fehlgeschlagen: " + e.getMessage()
                        + " — ältere Schritte werden trotzdem kompensiert.", e);
            }
        }
        // Journal kann mehr Schritte haben als die aktuelle Definition (Definition geändert):
        // diese sind nicht kompensierbar — konservativ als unvollständig werten.
        for (var entry : saga.stepStates().entrySet()) {
            if (entry.getKey() >= steps.size() && saga.isStepPossiblyCommitted(entry.getKey())) {
                LOG.log(Level.SEVERE, "Saga " + saga.sagaId() + " hat Schritt " + entry.getKey()
                        + " (" + entry.getValue() + "), den die Definition nicht mehr kennt"
                        + " — Saga wird als FAILED markiert.");
                vollstaendig = false;
            }
        }
        SagaState endState = vollstaendig ? SagaState.COMPENSATED : SagaState.FAILED;
        claim.finish(endState);
        return endState;
    }

    private static boolean alleSchritteDone(UnfinishedSaga saga, List<SagaStep> steps) {
        if (saga.stepStates().size() != steps.size() || steps.isEmpty()) {
            return false;
        }
        for (int i = 0; i < steps.size(); i++) {
            if (!saga.isStepDone(i)) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------
    // Kompensation (Laufzeit)
    // ------------------------------------------------------------------

    /**
     * Rollt die Schritte [0, doneCount) in umgekehrter Reihenfolge zurück. Kompensationen
     * sind idempotent; bereits kompensierte Schritte werden übersprungen. Ein Lesefehler im
     * Journal bricht die Schleife ab (kein stiller Reihenfolge-Bruch, Fund H2), ein
     * fachlicher Kompensationsfehler lässt ältere Schritte trotzdem laufen.
     */
    private void compensate(SagaDefinition definition, String payload, String sagaId, int doneCount) {
        markiereStill(sagaId, SagaState.COMPENSATING, "COMPENSATING");
        boolean vollstaendig = true;
        for (int i = doneCount - 1; i >= 0; i--) {
            final int index = i;
            final SagaStep step = definition.steps().get(i);
            try {
                if (journal.isStepCompensated(sagaId, index)) {
                    continue; // nach einem Absturz bereits erledigt
                }
            } catch (SQLException e) {
                // Fund H2: Der Journal-Zustand ist unbekannt — einfach weitermachen würde
                // die Kompensations-Reihenfolge brechen. Abbrechen: Die Saga bleibt FAILED,
                // der nächste Wiederanlauf setzt tiefer an.
                vollstaendig = false;
                LOG.log(Level.SEVERE, "Journal nicht lesbar bei Kompensation von Schritt " + index
                        + " (Saga " + sagaId + "): " + e.getMessage()
                        + " — Kompensation wird abgebrochen.", e);
                break;
            }
            try {
                txManager.execute(TxOptions.DEFAULT, ctx -> {
                    step.compensation().run(ctx.connection(), payload, sagaId);
                    journal.stepCompensated(ctx.connection(), sagaId, index);
                    return null;
                });
            } catch (Exception e) {
                vollstaendig = false;
                LOG.log(Level.SEVERE,
                        "Kompensation von Schritt '" + step.name() + "' (Saga " + sagaId
                                + ") fehlgeschlagen: " + e.getMessage()
                                + " — ältere Schritte werden trotzdem kompensiert.", e);
            }
        }
        markiereStill(sagaId, vollstaendig ? SagaState.COMPENSATED : SagaState.FAILED,
                vollstaendig ? "COMPENSATED" : "FAILED");
    }

    /**
     * Setzt einen Kopf-Zustand, ohne bei Fehler zu werfen: Journal-Markierungen dürfen die
     * Kompensation niemals verhindern oder verschlucken (Fund H4/M6). Ein bleibender
     * Zwischenzustand (z. B. RUNNING statt FAILED) ist unkritisch — der Wiederanlauf
     * behandelt RUNNING, FAILED und COMPENSATING gleichermaßen.
     */
    private void markiereStill(String sagaId, SagaState state, String bezeichnung) {
        try {
            journal.markState(sagaId, state);
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Journal-Markierung " + bezeichnung + " für Saga " + sagaId
                    + " fehlgeschlagen (wird ignoriert): " + e.getMessage(), e);
        }
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
