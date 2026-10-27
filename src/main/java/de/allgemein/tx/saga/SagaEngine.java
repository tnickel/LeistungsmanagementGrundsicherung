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
 * Kurz gesagt: Das ist der Ablaufleiter der Sagas.
 * Er geht die Schritte einer Saga der Reihe nach durch und bucht jeden Schritt
 * ins Journal. Läuft einer schief, lässt er rückwärts alle bisherigen Schritte
 * wieder rückgängig machen (kompensieren). Nach einem Absturz räumt er beim
 * Wiederanlauf die halbfertigen Vorgänge auf.
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
    private volatile java.time.Duration mindestInaktivitaet = java.time.Duration.ZERO;

    public SagaEngine(TxManager txManager, TxSagaJournal journal) {
        this.txManager = txManager;
        this.journal = journal;
    }

    /**
     * Schutz lebender Sagas im laufenden Betrieb: Der Wiederanlauf fasst nur Sagas an, deren
     * letzte Journal-Aktivität mindestens so lange zurückliegt. Zwischen den Schritt-
     * Transaktionen hält der normale Lauf einen exklusiven Kopf-Claim. Dadurch werden
     * lebende Sagas auch von anderen Instanzen zuverlässig übersprungen. Diese zusätzliche
     * Frist verzögert die Übernahme verwaister Sagas; Standard {@code ZERO} erlaubt den
     * sofortigen Wiederanlauf nach einem Absturz.
     */
    public void setzeMindestInaktivitaet(java.time.Duration dauer) {
        this.mindestInaktivitaet = dauer == null || dauer.isNegative() ? java.time.Duration.ZERO : dauer;
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
        record(() -> journal.start(sagaId, definition.type(), payload, steps.stream().map(SagaStep::name).toList()));
        de.allgemein.tx.audit.TxAuditLog.recordSagaStart(sagaId, definition.type(), payload);

        // Derselbe Kopf-Claim wie beim Recovery bleibt über ALLE Fachschritte erhalten.
        // Ein zweiter Server kann daher selbst mit Schutzfrist ZERO keine lebende Saga
        // kompensieren. Vor diesem Claim wurde noch keine Fachwirkung ausgeführt.
        SagaClaim claim = beanspruche(sagaId, definition.type());
        try {
            return runClaimed(definition, payload, sagaId, steps, claim);
        } finally {
            try (InterruptCleanup cleanup = new InterruptCleanup()) {
                claim.close();
            }
        }
    }

    private String runClaimed(SagaDefinition definition, String payload, String sagaId,
            List<SagaStep> steps, SagaClaim liveClaim) {

        int doneCount = 0;
        boolean imFlug = false;
        boolean writeAheadUncertain = false;
        try {
            for (SagaStep step : steps) {
                final int index = doneCount;
                record(liveClaim::checkAlive);
                // (a) Write-Ahead: Schritt als begonnen markieren, BEVOR die Fachtransaktion läuft.
                writeAheadUncertain = true;
                record(() -> journal.markStepInFlight(sagaId, index));
                writeAheadUncertain = false;
                imFlug = true;
                // (b) Fachschritt und Journal-Eintrag in derselben Transaktion (atomarer Commit).
                TxWork<Void> work = ctx -> {
                    liveClaim.checkAlive();
                    step.action().run(ctx.connection(), payload, sagaId);
                    journal.stepDone(ctx.connection(), sagaId, index, step.name());
                    liveClaim.checkAlive();
                    return null;
                };
                txManager.execute(TxOptions.DEFAULT, work);
                imFlug = false;
                de.allgemein.tx.audit.TxAuditLog.recordSagaStep(sagaId, index, step.name());
                doneCount++;
            }
        } catch (TxException e) {
            try (InterruptCleanup cleanup = new InterruptCleanup()) {
                try {
                    // FAILED muss dauerhaft sein, bevor kompensiert wird. Sonst könnte
                    // Recovery nach einem Crash alle DONE-Schritte fälschlich abschließen.
                    liveClaim.finish(SagaState.FAILED);
                } catch (SQLException journalError) {
                    e.addSuppressed(journalError);
                    LOG.log(Level.SEVERE, "FAILED-Markierung für Saga " + sagaId
                            + " fehlgeschlagen; Kompensation benötigt einen neuen exklusiven Claim: "
                            + de.allgemein.tx.audit.TxAuditLog.fehlerBeschreibung(journalError));
                } finally {
                    cleanup.clear();
                    liveClaim.close();
                }
                if (writeAheadUncertain) {
                    // Die Commit-Quittung des Write-Ahead kann verloren gegangen sein.
                    // FAILED behält Payload und IN_FLIGHT für den exklusiven Wiederanlauf.
                    // Die Fachaktion dieses Schritts wurde noch nicht gestartet.
                    throw e;
                }
                SagaClaim compensationClaim;
                try {
                    cleanup.clear();
                    compensationClaim = journal.claimUnfinished(sagaId, definition.type());
                    if (compensationClaim == null) {
                        // Ein Recovery hat den freigegebenen FAILED-Kopf übernommen oder
                        // schon beendet. Keine zweite, parallele Kompensation starten.
                        LOG.warning("Saga " + sagaId + ": Kompensation an Wiederanlauf übergeben");
                        throw e;
                    }
                } catch (SQLException journalError) {
                    e.addSuppressed(journalError);
                    LOG.log(Level.SEVERE, "Kompensations-Claim für Saga " + sagaId
                            + " fehlgeschlagen; Wiederanlauf muss übernehmen: "
                            + de.allgemein.tx.audit.TxAuditLog.fehlerBeschreibung(journalError));
                    throw e;
                }
                try {
                    de.allgemein.tx.audit.TxAuditLog.recordSagaCompensating(sagaId,
                            de.allgemein.tx.audit.TxAuditLog.fehlerBeschreibung(e));
                    cleanup.clear();
                    int zuKompensieren = doneCount;
                    if (imFlug && doneCount < steps.size() && schrittCommittet(sagaId, doneCount)) {
                        zuKompensieren = doneCount + 1;
                    }
                    SagaState state = compensate(definition, payload, sagaId, zuKompensieren,
                            compensationClaim, cleanup);
                    cleanup.clear();
                    compensationClaim.finish(state);
                } catch (SQLException journalError) {
                    e.addSuppressed(journalError);
                    LOG.log(Level.SEVERE, "Saga " + sagaId + ": Kompensationsabschluss fehlgeschlagen: "
                            + de.allgemein.tx.audit.TxAuditLog.fehlerBeschreibung(journalError));
                } finally {
                    cleanup.clear();
                    compensationClaim.close();
                }
                de.allgemein.tx.audit.TxAuditLog.recordSagaFailed(sagaId,
                        de.allgemein.tx.audit.TxAuditLog.fehlerBeschreibung(e));
            }
            throw e;
        }
        // Alle Schritte erfolgreich: Die COMPLETED-Markierung läuft bewusst NACH dem
        // Kompensations-try — ihr Fehler darf KEINE Fehl-Kompensation der fachlich
        // erfolgreichen Saga auslösen (Fund H4).
        try {
            liveClaim.finish(SagaState.COMPLETED);
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Saga " + sagaId + " fachlich abgeschlossen, aber die COMPLETED-"
                    + "Markierung schlug fehl: " + de.allgemein.tx.audit.TxAuditLog.fehlerBeschreibung(e)
                    + " — der Wiederanlauf klärt den Abschluss.");
            throw TxException.nonRetryable("Saga-Journal-Fehler beim Abschließen der Saga " + sagaId
                    + " (alle Fachschritte sind committet): " + e.getMessage(), e);
        }
        de.allgemein.tx.audit.TxAuditLog.recordSagaCompleted(sagaId, definition.type());
        return sagaId;
    }

    private SagaClaim beanspruche(String sagaId, String type) {
        try {
            SagaClaim claim = journal.claimUnfinished(sagaId, type);
            if (claim == null) {
                throw TxException.nonRetryable("Saga " + sagaId
                        + " bereits vom Wiederanlauf übernommen; kein Fachschritt ausgeführt", null);
            }
            return claim;
        } catch (SQLException e) {
            throw TxException.nonRetryable("Saga-Claim fehlgeschlagen für " + sagaId, e);
        }
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
            kandidaten = journal.unfinished(mindestInaktivitaet);
        } catch (SQLException e) {
            // Journal nicht lesbar: blind weiterlaufen wäre falsch — abbrechen,
            // aber nicht werfen (Fund M3: der Serverstart darf nicht gefährdet werden).
            LOG.log(Level.SEVERE, "Saga-Journal nicht lesbar für Typ " + definition.type()
                    + ": " + de.allgemein.tx.audit.TxAuditLog.fehlerBeschreibung(e)
                    + " — Wiederanlauf für diesen Typ abgebrochen.");
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
                        + de.allgemein.tx.audit.TxAuditLog.fehlerBeschreibung(e)
                        + " — wird übersprungen, der nächste Wiederanlauf versucht sie erneut.");
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
                        + saga.type() + ") fehlgeschlagen: " + de.allgemein.tx.audit.TxAuditLog.fehlerBeschreibung(e)
                        + " — der nächste Wiederanlauf versucht sie erneut.");
                // claim.close() über try-with-resources: Rollback, Kopf unverändert
            }
        }
        return new RecoveryReport(completed, compensated, failed, errors);
    }

    /** Verarbeitet eine beanspruchte Saga; liefert den erreichten Endzustand. */
    private SagaState wiederanlauf(UnfinishedSaga saga, SagaDefinition definition, SagaClaim claim)
            throws SQLException {
        List<SagaStep> steps = definition.steps();

        // Historische Engines committeten die Fachaktion vor dem separaten stepDone.
        // Daher kann auch neben DONE/COMPENSATED ein namenloses PENDING eine noch
        // vorhandene Wirkung verbergen. Vor JEDEM automatischen Abschluss und vor
        // jeder Fachkompensation zur manuellen Wirkungsprüfung anhalten.
        boolean historischUngeklaert = saga.stepStates().entrySet().stream()
                .anyMatch(entry -> UnfinishedSaga.STEP_PENDING.equals(entry.getValue())
                        && (saga.stepName(entry.getKey()) == null
                        || saga.stepName(entry.getKey()).isBlank()));
        if (historischUngeklaert) {
            LOG.severe("Saga " + saga.sagaId()
                    + ": PENDING ohne stabilen Namen; historische Fachwirkung muss manuell geprüft werden");
            claim.finish(SagaState.FAILED);
            return SagaState.FAILED;
        }

        // Fund H4: fachlich fertige Saga nur noch abschließen — die COMPLETED-Markierung
        // wird idempotent nachgezogen, wenn alle Schritte DONE sind (Absturzfenster zwischen
        // letztem Schritt-Commit und Markierung). Keine Kompensation!
        if (saga.state() == SagaState.RUNNING && alleSchritteDone(saga)) {
            claim.finish(SagaState.COMPLETED);
            return SagaState.COMPLETED;
        }

        if (alleSchrittePending(saga, steps)) {
            // Nur bereits bei PENDING gespeicherte Namen belegen den neuen
            // atomaren Start-/Write-Ahead-Vertrag. Historische namenlose PENDINGs
            // können eine Fachwirkung vor dem damaligen separaten stepDone verbergen.
            // Unter dem exklusiven Claim terminal beenden, auch für alte FAILED-Köpfe.
            claim.finish(SagaState.COMPENSATED);
            return SagaState.COMPENSATED;
        }

        boolean hatBegonnen = saga.stepStates().values().stream()
                .anyMatch(s -> UnfinishedSaga.STEP_DONE.equals(s)
                        || UnfinishedSaga.STEP_IN_FLIGHT.equals(s)
                        || UnfinishedSaga.STEP_COMPENSATED.equals(s));
        if (!hatBegonnen) {
            // Keine belegte Fachwirkung, aber auch keine vollständige bekannte
            // PENDING-Liste: beschädigte/geänderte Journale nicht grün abschließen.
            claim.finish(SagaState.FAILED);
            return SagaState.FAILED;
        }

        // Fund K1c: rückwärts kompensieren; IN_FLIGHT gilt konservativ als
        // „möglicherweise committet" und wird mit kompensiert (idempotente Kompensationen).
        // Bereits COMPENSATED markierte Schritte (früherer Absturz) werden übersprungen.
        boolean vollstaendig = true;
        java.util.Map<String, SagaStep> byName = new java.util.HashMap<>();
        for (SagaStep step : steps) byName.put(step.name(), step);
        // Die ursprüngliche Reihenfolge ist im Journal gespeichert. Die aktuelle
        // Definition darf neue Schritte enthalten oder anders sortiert sein.
        for (int i : saga.stepStates().keySet().stream().sorted(java.util.Comparator.reverseOrder()).toList()) {
            if (!saga.isStepPossiblyCommitted(i)) {
                continue; // PENDING (nie gelaufen) oder COMPENSATED (schon erledigt)
            }
            if (saga.isStepCompensated(i)) {
                continue; // nach früherem Absturz bereits erledigt — idempotent weiter
            }
            final int index = i;
            String name = saga.stepName(i);
            SagaStep step = name == null ? null : byName.get(name);
            if (step == null) {
                LOG.severe("Saga " + saga.sagaId() + ": stabiler Schrittname für Index " + i
                        + " fehlt oder ist unbekannt; keine Kompensation über aktuellen Index");
                vollstaendig = false;
                continue;
            }
            try {
                txManager.execute(TxOptions.DEFAULT, ctx -> {
                    claim.checkAlive();
                    step.compensation().run(ctx.connection(), saga.payload(), saga.sagaId());
                    journal.stepCompensated(ctx.connection(), saga.sagaId(), index);
                    claim.checkAlive();
                    return null;
                });
            } catch (Exception e) {
                vollstaendig = false;
                LOG.log(Level.SEVERE, "Kompensation von Schritt '" + step.name() + "' (Saga "
                        + saga.sagaId() + ") im Wiederanlauf fehlgeschlagen: "
                        + de.allgemein.tx.audit.TxAuditLog.fehlerBeschreibung(e)
                        + " — ältere Schritte werden trotzdem kompensiert.");
            }
        }
        SagaState endState = vollstaendig ? SagaState.COMPENSATED : SagaState.FAILED;
        claim.finish(endState);
        return endState;
    }

    private static boolean alleSchritteDone(UnfinishedSaga saga) {
        if (saga.stepStates().isEmpty()) {
            return false;
        }
        // Kopf und vollständige ursprüngliche Schrittliste werden atomar angelegt.
        // Neue Definitionsschritte dürfen einen bereits vollständig committeten
        // alten Ablauf nicht nachträglich zur Kompensation machen.
        for (int i = 0; i < saga.stepStates().size(); i++) {
            if (!saga.isStepDone(i)) {
                return false;
            }
        }
        return true;
    }

    private static boolean alleSchrittePending(UnfinishedSaga saga, List<SagaStep> steps) {
        if (steps.isEmpty() || saga.stepStates().size() != steps.size()) return false;
        for (int i = 0; i < steps.size(); i++) {
            if (!UnfinishedSaga.STEP_PENDING.equals(saga.stepStates().get(i))) return false;
            if (saga.stepName(i) == null || saga.stepName(i).isBlank()) return false;
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
    private SagaState compensate(SagaDefinition definition, String payload, String sagaId, int doneCount,
            SagaClaim claim, InterruptCleanup cleanup) {
        boolean vollstaendig = true;
        for (int i = doneCount - 1; i >= 0; i--) {
            final int index = i;
            final SagaStep step = definition.steps().get(i);
            try {
                cleanup.clear();
                if (journal.isStepCompensated(sagaId, index)) {
                    continue; // nach einem Absturz bereits erledigt
                }
            } catch (SQLException e) {
                // Fund H2: Der Journal-Zustand ist unbekannt — einfach weitermachen würde
                // die Kompensations-Reihenfolge brechen. Abbrechen: Die Saga bleibt FAILED,
                // der nächste Wiederanlauf setzt tiefer an.
                vollstaendig = false;
                LOG.log(Level.SEVERE, "Journal nicht lesbar bei Kompensation von Schritt " + index
                        + " (Saga " + sagaId + "): " + de.allgemein.tx.audit.TxAuditLog.fehlerBeschreibung(e)
                        + " — Kompensation wird abgebrochen.");
                break;
            }
            try {
                cleanup.clear();
                txManager.execute(TxOptions.DEFAULT, ctx -> {
                    claim.checkAlive();
                    step.compensation().run(ctx.connection(), payload, sagaId);
                    journal.stepCompensated(ctx.connection(), sagaId, index);
                    claim.checkAlive();
                    return null;
                });
            } catch (Exception e) {
                vollstaendig = false;
                LOG.log(Level.SEVERE,
                        "Kompensation von Schritt '" + step.name() + "' (Saga " + sagaId
                                + ") fehlgeschlagen: " + de.allgemein.tx.audit.TxAuditLog.fehlerBeschreibung(e)
                                + " — ältere Schritte werden trotzdem kompensiert.");
            }
        }
        return vollstaendig ? SagaState.COMPENSATED : SagaState.FAILED;
    }

    /** Bewahrt Abbruchsignale über die notwendige, weiterhin zeitlich begrenzte Bereinigung. */
    private static final class InterruptCleanup implements AutoCloseable {
        private boolean interrupted;

        InterruptCleanup() {
            clear();
        }

        void clear() {
            interrupted |= Thread.interrupted();
        }

        @Override
        public void close() {
            clear();
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /** Klärt einen mehrdeutigen Commit über das Journal; bei Lesefehler konservativ „committet". */
    private boolean schrittCommittet(String sagaId, int index) {
        try {
            return journal.isStepDone(sagaId, index);
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "Journal nicht lesbar zur Klärung von Schritt " + index + " der Saga "
                    + sagaId + " — Schritt wird vorsorglich mit kompensiert: "
                    + de.allgemein.tx.audit.TxAuditLog.fehlerBeschreibung(e));
            return true;
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
