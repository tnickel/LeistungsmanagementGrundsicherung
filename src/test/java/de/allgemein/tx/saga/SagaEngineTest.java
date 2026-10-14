package de.allgemein.tx.saga;

import de.allgemein.tx.ConnectionPool;
import de.allgemein.tx.TxException;
import de.allgemein.tx.TxManager;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit-Tests der {@link SagaEngine} OHNE Datenbank: Das Journal wird über das
 * {@link TxSagaJournal}-Interface durch eine In-Memory-Umsetzung mit denselben
 * Guards ersetzt, die Verbindungen des {@link TxManager} durch JDBC-Proxys.
 *
 * <p>Geprüfte Kernregeln:</p>
 * <ul>
 *   <li>Write-Ahead: jeder Schritt wird vor der Ausführung IN_FLIGHT, der Erfolg
 *       wird atomar in derselben Transaktion vermerkt (Fund K1).</li>
 *   <li>Fachfehler: rückwärts kompensieren, genau einmal, Endstatus COMPENSATED (nicht FAILED).</li>
 *   <li>Kompensationsfehler: ältere Schritte laufen trotzdem, Endstatus FAILED.</li>
 *   <li>Wiederanlauf: IN_FLIGHT gilt als „möglicherweise committet" und wird kompensiert;
 *       zweiter Wiederanlauf ist idempotent.</li>
 *   <li>Journal-Lesefehler bei der Kompensation: abbrechen statt still überspringen (Fund H2).</li>
 *   <li>COMPLETED-Markierungsfehler: KEINE Fehl-Kompensation (Fund H4).</li>
 * </ul>
 */
class SagaEngineTest {

    @Test
    void interruptImRetryBackoffVerhindertDieKompensationNichtUndBleibtErhalten() {
        FakeJournal journal = new FakeJournal();
        var pool = new de.allgemein.tx.FakePool(3);
        SagaEngine engine = new SagaEngine(new TxManager(pool), journal);
        AtomicInteger effect = new AtomicInteger();
        SagaDefinition definition = SagaDefinition.of("INTERRUPT")
                .step("committet", (c, p, id) -> effect.incrementAndGet(),
                        (c, p, id) -> effect.decrementAndGet())
                .step("abgebrochen", (c, p, id) -> {
                    Thread.currentThread().interrupt();
                    throw new SQLException("deadlock", "61000", 60);
                }, (c, p, id) -> { })
                .build();
        try {
            TxException error = assertThrows(TxException.class, () -> engine.run(definition, "{}"));
            assertTrue(error.getCause() instanceof InterruptedException);
            assertEquals(0, effect.get(), "bereits committeter Schritt muss rückgängig werden");
            assertEquals(SagaState.COMPENSATED.name(), journal.zustand(journal.einzigeSagaId()));
            assertTrue(Thread.currentThread().isInterrupted(), "Abbruchsignal muss erhalten bleiben");
            assertTrue(journal.beansprucht.isEmpty(), "Claim auch mit Interrupt freigeben");
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void futureBleibtAbgebrochenAuchWennSagaDanachErfolgreichKompensiert() throws Exception {
        FakeJournal journal = new FakeJournal();
        var pool = new de.allgemein.tx.FakePool(3);
        TxManager tx = new TxManager(pool, new de.allgemein.tx.RetryPolicy(5_000));
        SagaEngine engine = new SagaEngine(tx, journal);
        AtomicInteger effect = new AtomicInteger();
        CountDownLatch compensated = new CountDownLatch(1);
        SagaDefinition definition = SagaDefinition.of("WATCHDOG")
                .step("eins", (c, p, id) -> effect.incrementAndGet(), (c, p, id) -> {
                    effect.decrementAndGet();
                    compensated.countDown();
                })
                .step("konflikt", (c, p, id) -> { throw new SQLException("deadlock", "61000", 60); },
                        (c, p, id) -> { })
                .build();
        try (var tasks = new de.allgemein.tx.TaskController(tx, 1, 2)) {
            var handle = tasks.submit("Saga", de.allgemein.tx.TxOptions.DEFAULT.withTimeout(Duration.ofMillis(200)),
                    ctx -> engine.run(definition, "{}"));
            assertThrows(TxException.class, handle::result);
            assertTrue(compensated.await(5, TimeUnit.SECONDS));
            assertTrue(handle.isDone());
            assertThrows(TxException.class, handle::result, "Kompensation macht den Future nicht erfolgreich");
            assertEquals(0, effect.get());
            assertEquals(1, tasks.stats().timedOut());
        }
        assertEquals(SagaState.COMPENSATED.name(), journal.zustand(journal.einzigeSagaId()));
    }

    @Test
    void zweiterServerUeberspringtLebendeSagaAuchOhneSchutzfrist() throws Exception {
        FakeJournal journal = new FakeJournal();
        var tx = new TxManager(new de.allgemein.tx.FakePool(4));
        SagaEngine live = new SagaEngine(tx, journal);
        SagaEngine otherServer = new SagaEngine(tx, journal);
        AtomicInteger effect = new AtomicInteger();
        CountDownLatch betweenSteps = new CountDownLatch(1);
        CountDownLatch continueRun = new CountDownLatch(1);
        SagaDefinition definition = SagaDefinition.of("LIVE")
                .step("eins", (c, p, id) -> effect.incrementAndGet(), (c, p, id) -> effect.decrementAndGet())
                .step("zwei", (c, p, id) -> {
                    betweenSteps.countDown();
                    assertTrue(continueRun.await(5, TimeUnit.SECONDS));
                }, (c, p, id) -> { })
                .build();
        var worker = Executors.newSingleThreadExecutor();
        try {
            var result = worker.submit(() -> live.run(definition, "{}"));
            assertTrue(betweenSteps.await(5, TimeUnit.SECONDS));
            assertEquals(0, otherServer.recoverWithReport(definition).behandelt());
            assertEquals(1, effect.get(), "lebende Fachwirkung darf nicht kompensiert werden");
            continueRun.countDown();
            String id = result.get(5, TimeUnit.SECONDS);
            assertEquals(SagaState.COMPLETED.name(), journal.zustand(id));
        } finally {
            continueRun.countDown();
            worker.shutdownNow();
        }
    }

    @Test
    void fehlgeschlageneFailedMarkierungVerhindertKompensationMitNeuemClaimNicht() {
        FakeJournal journal = new FakeJournal();
        journal.failedMarkierungWirft = true;
        SchrittVerhalten behavior = new SchrittVerhalten(2);
        behavior.fachfehlerBei = 1;
        var engine = new SagaEngine(new TxManager(new FakePool()), journal);
        TxException failure = assertThrows(TxException.class, () -> engine.run(definition("T", behavior), "{}"));
        assertEquals(1, failure.getSuppressed().length);
        assertEquals(List.of("action:0", "comp:0"), behavior.verlauf);
        assertEquals(SagaState.COMPENSATED.name(), journal.zustand(journal.einzigeSagaId()));
        assertTrue(journal.beansprucht.isEmpty());
    }

    @Test
    void recoveryDarfFailedSagaUebernehmenOhneDoppelteKompensation() {
        FakeJournal journal = new FakeJournal();
        SchrittVerhalten behavior = new SchrittVerhalten(2);
        behavior.fachfehlerBei = 1;
        SagaDefinition definition = definition("T", behavior);
        var tx = new TxManager(new FakePool());
        var engine = new SagaEngine(tx, journal);
        var recovery = new SagaEngine(tx, journal);
        journal.afterFailedRelease = () -> assertEquals(1, recovery.recoverWithReport(definition).compensated());
        assertThrows(TxException.class, () -> engine.run(definition, "{}"));
        assertEquals(1, behavior.verlauf.stream().filter("comp:0"::equals).count());
        assertEquals(SagaState.COMPENSATED.name(), journal.zustand(journal.einzigeSagaId()));
        assertTrue(journal.beansprucht.isEmpty());
    }

    // ==================================================================
    // Testfälle
    // ==================================================================

    /** (a) Erfolgreiche Saga: je Schritt IN_FLIGHT → DONE, Endstatus COMPLETED. */
    /**
     * NEU-06: Scheitert der Commit eines Schritts mehrdeutig (Journal sagt DONE, der Aufrufer
     * sieht einen Fehler), muss genau dieser Schritt mit kompensiert werden.
     */
    @Test
    void mehrdeutigerCommitKompensiertAuchDenFraglichenSchritt() {
        SchrittVerhalten verhalten = new SchrittVerhalten(3);
        FakeJournal journal = new FakeJournal();
        journal.stepDoneWirftNachCommitBei = 1;
        SagaEngine engine = new SagaEngine(new TxManager(new FakePool()), journal);

        assertThrows(TxException.class, () -> engine.run(definition("T", verhalten), "{}"));

        assertEquals(List.of("action:0", "action:1", "comp:1", "comp:0"), verhalten.verlauf);
        assertEquals(SagaState.COMPENSATED.name(), journal.zustand(journal.einzigeSagaId()));
    }

    @Test
    void erfolgreicheSagaMarkiertJedenSchrittInFlightUndDone() {
        SchrittVerhalten verhalten = new SchrittVerhalten(3);
        FakeJournal journal = new FakeJournal();
        SagaEngine engine = new SagaEngine(new TxManager(new FakePool()), journal);

        String sagaId = engine.run(definition("T", verhalten), "{}");

        assertNotNull(sagaId);
        assertEquals(SagaState.COMPLETED.name(), journal.zustand(sagaId));
        assertEquals(List.of("action:0", "action:1", "action:2"), verhalten.verlauf);
        assertEquals(List.of(
                "start", "inflight:0", "done:0", "inflight:1", "done:1", "inflight:2", "done:2",
                "state:COMPLETED"), journal.protokoll);
        journal.schrittZustaende(sagaId).values().forEach(
                zustand -> assertEquals(UnfinishedSaga.STEP_DONE, zustand));
    }

    /**
     * (b) Fachfehler in Schritt 3 von 4: Kompensation rückwärts 2 → 1, genau einmal,
     * Endstatus COMPENSATED (nicht FAILED), alle kompensierbaren Schritte COMPENSATED.
     */
    @Test
    void fachfehlerKompensiertRueckwaertsGenauEinmalUndEndetCompensated() {
        SchrittVerhalten verhalten = new SchrittVerhalten(4);
        verhalten.fachfehlerBei = 2; // Schritt 3 von 4 (Index 2) scheitert fachlich
        FakeJournal journal = new FakeJournal();
        SagaEngine engine = new SagaEngine(new TxManager(new FakePool()), journal);

        assertThrows(TxException.class, () -> engine.run(definition("T", verhalten), "{}"));

        String sagaId = journal.einzigeSagaId();
        assertEquals(SagaState.COMPENSATED.name(), journal.zustand(sagaId),
                "erfolgreiche Kompensation muss COMPENSATED sein, nicht FAILED");
        // Kompensation rückwärts und genau einmal:
        assertEquals(List.of("action:0", "action:1", "comp:1", "comp:0"), verhalten.verlauf);
        // Schritt-Zustände: 0/1 kompensiert; 2 IN_FLIGHT (Fachtransaktion zurückgerollt); 3 PENDING
        Map<Integer, String> zustaende = journal.schrittZustaende(sagaId);
        assertEquals(UnfinishedSaga.STEP_COMPENSATED, zustaende.get(0));
        assertEquals(UnfinishedSaga.STEP_COMPENSATED, zustaende.get(1));
        assertEquals(UnfinishedSaga.STEP_IN_FLIGHT, zustaende.get(2),
                "der gescheiterte Schritt bleibt IN_FLIGHT — seine Fachtransaktion rollte zurück");
        assertEquals(UnfinishedSaga.STEP_PENDING, zustaende.get(3));
    }

    /** (c) Kompensationsfehler in Schritt 2: Endstatus FAILED, Schritt 1 trotzdem kompensiert. */
    @Test
    void kompensationsfehlerEndetFailedAberLaesstAeltereSchritteKompensiert() {
        SchrittVerhalten verhalten = new SchrittVerhalten(4);
        verhalten.fachfehlerBei = 3;   // Fachfehler im letzten Schritt
        verhalten.kompensationsfehlerBei = 1; // Kompensation von Schritt 2 (Index 1) scheitert
        FakeJournal journal = new FakeJournal();
        SagaEngine engine = new SagaEngine(new TxManager(new FakePool()), journal);

        assertThrows(TxException.class, () -> engine.run(definition("T", verhalten), "{}"));

        String sagaId = journal.einzigeSagaId();
        assertEquals(SagaState.FAILED.name(), journal.zustand(sagaId));
        // Schritt 3 (Index 2) wird noch kompensiert, Schritt 2 (Index 1) scheitert,
        // Schritt 1 (Index 0) läuft trotzdem:
        assertEquals(List.of("action:0", "action:1", "action:2", "comp:2", "comp:0"), verhalten.verlauf);
        Map<Integer, String> zustaende = journal.schrittZustaende(sagaId);
        assertEquals(UnfinishedSaga.STEP_COMPENSATED, zustaende.get(0));
        assertEquals(UnfinishedSaga.STEP_DONE, zustaende.get(1),
                "gescheiterte Kompensation bleibt DONE (Transaktion zurückgerollt)");
        assertEquals(UnfinishedSaga.STEP_COMPENSATED, zustaende.get(2));
    }

    /**
     * (d) Wiederanlauf: Schritte 0–2 DONE, Schritt 3 IN_FLIGHT, Saga RUNNING →
     * IN_FLIGHT gilt als möglicherweise committet und WIRD kompensiert (3 → 2 → 1 → 0),
     * Endstatus COMPENSATED; zweiter Wiederanlauf bleibt ohne Aktion.
     */
    @Test
    void wiederanlaufKompensiertInFlightKonservativUndIstIdempotent() {
        SchrittVerhalten verhalten = new SchrittVerhalten(4);
        FakeJournal journal = new FakeJournal();
        Map<Integer, String> zustaende = new LinkedHashMap<>();
        zustaende.put(0, UnfinishedSaga.STEP_DONE);
        zustaende.put(1, UnfinishedSaga.STEP_DONE);
        zustaende.put(2, UnfinishedSaga.STEP_DONE);
        zustaende.put(3, UnfinishedSaga.STEP_IN_FLIGHT);
        journal.prefuelle("saga-crash", "T", "{}", SagaState.RUNNING, zustaende);
        SagaEngine engine = new SagaEngine(new TxManager(new FakePool()), journal);

        assertEquals(1, engine.recover(definition("T", verhalten)));

        assertEquals(SagaState.COMPENSATED.name(), journal.zustand("saga-crash"));
        // rückwärts, inklusive des IN_FLIGHT-Schritts 3:
        assertEquals(List.of("comp:3", "comp:2", "comp:1", "comp:0"), verhalten.verlauf);
        journal.schrittZustaende("saga-crash").values().forEach(
                zustand -> assertEquals(UnfinishedSaga.STEP_COMPENSATED, zustand));

        // zweiter Wiederanlauf: keine Aktion mehr (idempotent)
        int laengeNachErstemWiederanlauf = verhalten.verlauf.size();
        assertEquals(0, engine.recover(definition("T", verhalten)));
        assertEquals(laengeNachErstemWiederanlauf, verhalten.verlauf.size(),
                "zweiter Wiederanlauf darf nichts mehr kompensieren");
    }

    /** (e) Journal-Lesefehler bei isStepCompensated: Kompensation bricht ab, kein Skip. */
    @Test
    void journalLesefehlerBrichtKompensationAbStattZuUeberspringen() {
        SchrittVerhalten verhalten = new SchrittVerhalten(4);
        verhalten.fachfehlerBei = 2;
        FakeJournal journal = new FakeJournal();
        journal.isStepCompensatedWirft = true;
        SagaEngine engine = new SagaEngine(new TxManager(new FakePool()), journal);

        assertThrows(TxException.class, () -> engine.run(definition("T", verhalten), "{}"));

        String sagaId = journal.einzigeSagaId();
        assertEquals(SagaState.FAILED.name(), journal.zustand(sagaId));
        assertTrue(verhalten.verlauf.stream().noneMatch(eintrag -> eintrag.startsWith("comp:")),
                "bei Journal-Lesefehler darf KEIN Schritt kompensiert werden (Reihenfolge-Bruch)");
        Map<Integer, String> zustaende = journal.schrittZustaende(sagaId);
        assertEquals(UnfinishedSaga.STEP_DONE, zustaende.get(0));
        assertEquals(UnfinishedSaga.STEP_DONE, zustaende.get(1));
    }

    /** (f) Fehler der COMPLETED-Markierung: KEINE Kompensation, Exception nach außen, RUNNING bleibt. */
    @Test
    void completedMarkierungsfehlerLoestKeineFehlKompensationAus() {
        SchrittVerhalten verhalten = new SchrittVerhalten(3);
        FakeJournal journal = new FakeJournal();
        journal.completedMarkierungWirft = true;
        SagaEngine engine = new SagaEngine(new TxManager(new FakePool()), journal);

        assertThrows(TxException.class, () -> engine.run(definition("T", verhalten), "{}"));

        String sagaId = journal.einzigeSagaId();
        assertEquals(SagaState.RUNNING.name(), journal.zustand(sagaId),
                "die Saga bleibt RUNNING — der nächste Wiederanlauf zieht COMPLETED nach");
        assertTrue(verhalten.verlauf.stream().noneMatch(eintrag -> eintrag.startsWith("comp:")),
                "fachlich erfolgreiche Saga darf NICHT kompensiert werden");
        journal.schrittZustaende(sagaId).values().forEach(
                zustand -> assertEquals(UnfinishedSaga.STEP_DONE, zustand));
    }

    /** (g) Wiederanlauf zieht COMPLETED für fachlich fertige Sagas idempotent nach (Fund H4). */
    @Test
    void wiederanlaufZiehtCompletedFuerFachlichFertigeSagaNach() {
        SchrittVerhalten verhalten = new SchrittVerhalten(3);
        FakeJournal journal = new FakeJournal();
        Map<Integer, String> zustaende = new LinkedHashMap<>();
        zustaende.put(0, UnfinishedSaga.STEP_DONE);
        zustaende.put(1, UnfinishedSaga.STEP_DONE);
        zustaende.put(2, UnfinishedSaga.STEP_DONE);
        journal.prefuelle("saga-fast-fertig", "T", "{}", SagaState.RUNNING, zustaende);
        SagaEngine engine = new SagaEngine(new TxManager(new FakePool()), journal);

        assertEquals(1, engine.recover(definition("T", verhalten)));

        assertEquals(SagaState.COMPLETED.name(), journal.zustand("saga-fast-fertig"));
        assertTrue(verhalten.verlauf.isEmpty(),
                "eine fachlich fertige Saga darf beim Wiederanlauf nicht kompensiert werden");
    }

    /**
     * (h) Wiederanlauf wirft nicht und zählt je Saga: eine scheiternde Saga (FAILED) hält
     * die Bearbeitung der übrigen nicht auf (Fund M3).
     */
    @Test
    void wiederanlaufZaehltFehlerJeSagaUndBlockiertDieAnderenNicht() {
        SchrittVerhalten verhalten = new SchrittVerhalten(2);
        verhalten.kompensationsfehlerBei = 1;
        FakeJournal journal = new FakeJournal();
        // saga-a: Lauf war fachlich gescheitert (Kopf FAILED), Kompensation von
        // Schritt 2 (Index 1) scheitert auch — Saga bleibt FAILED
        Map<Integer, String> a = new LinkedHashMap<>();
        a.put(0, UnfinishedSaga.STEP_DONE);
        a.put(1, UnfinishedSaga.STEP_DONE);
        journal.prefuelle("saga-a", "T", "{}", SagaState.FAILED, a);
        // saga-b: nach Absturz RUNNING, nur 1 Schritt DONE — Kompensation gelingt
        Map<Integer, String> b = new LinkedHashMap<>();
        b.put(0, UnfinishedSaga.STEP_DONE);
        journal.prefuelle("saga-b", "T", "{}", SagaState.RUNNING, b);
        SagaEngine engine = new SagaEngine(new TxManager(new FakePool()), journal);

        SagaEngine.RecoveryReport report = engine.recoverWithReport(definition("T", verhalten));

        assertEquals(2, report.behandelt(), "beide Sagas müssen behandelt werden");
        assertEquals(1, report.failed(), "saga-a endet FAILED");
        assertEquals(1, report.compensated(), "saga-b endet COMPENSATED");
        assertEquals(SagaState.FAILED.name(), journal.zustand("saga-a"));
        assertEquals(SagaState.COMPENSATED.name(), journal.zustand("saga-b"));
    }

    // ==================================================================
    // Test-Infrastruktur
    // ==================================================================

    /** Verhalten der Testschritte: protokolliert Aktionen, lässt gezielt Schritte scheitern. */
    private static final class SchrittVerhalten {
        final int anzahl;
        int fachfehlerBei = -1;
        int kompensationsfehlerBei = -1;
        final List<String> verlauf = new ArrayList<>();

        SchrittVerhalten(int anzahl) {
            this.anzahl = anzahl;
        }
    }

    private static SagaDefinition definition(String typ, SchrittVerhalten verhalten) {
        SagaDefinition.Builder builder = SagaDefinition.of(typ);
        for (int i = 0; i < verhalten.anzahl; i++) {
            final int index = i;
            builder.step("Schritt " + index,
                    (connection, payload, sagaId) -> {
                        if (index == verhalten.fachfehlerBei) {
                            throw new IllegalStateException("Fachfehler in Schritt " + index);
                        }
                        verhalten.verlauf.add("action:" + index);
                    },
                    (connection, payload, sagaId) -> {
                        if (index == verhalten.kompensationsfehlerBei) {
                            throw new IllegalStateException("Kompensationsfehler in Schritt " + index);
                        }
                        verhalten.verlauf.add("comp:" + index);
                    });
        }
        return builder.build();
    }

    /** In-Memory-Journal mit denselben Guards wie die Oracle-Umsetzung. */
    private static final class FakeJournal implements TxSagaJournal {

        final Map<String, String> koepfe = new LinkedHashMap<>();          // sagaId → STATE
        final Map<String, String> typen = new HashMap<>();
        final Map<String, String> payloads = new HashMap<>();
        final Map<String, Map<Integer, String>> schritte = new LinkedHashMap<>();
        final List<String> protokoll = new ArrayList<>();
        final Set<String> beansprucht = new HashSet<>();
        boolean completedMarkierungWirft;
        boolean failedMarkierungWirft;
        Runnable afterFailedRelease;
        boolean isStepCompensatedWirft;
        /** Simuliert einen mehrdeutigen Commit: Schritt gilt als DONE, der Aufrufer sieht aber einen Fehler. */
        int stepDoneWirftNachCommitBei = -1;

        String zustand(String sagaId) {
            return koepfe.get(sagaId);
        }

        Map<Integer, String> schrittZustaende(String sagaId) {
            return schritte.get(sagaId);
        }

        String einzigeSagaId() {
            assertEquals(1, koepfe.size(), "Test erwartet genau eine Saga im Journal");
            return koepfe.keySet().iterator().next();
        }

        /** Befüllt das Journal wie nach einem Absturz mitten im Vorgang. */
        void prefuelle(String sagaId, String typ, String payload, SagaState anfangsZustand,
                Map<Integer, String> zustaende) {
            koepfe.put(sagaId, anfangsZustand.name());
            typen.put(sagaId, typ);
            payloads.put(sagaId, payload);
            schritte.put(sagaId, new LinkedHashMap<>(zustaende));
        }

        @Override
        public void start(String sagaId, String type, String payload, int stepCount) throws SQLException {
            koepfe.put(sagaId, SagaState.RUNNING.name());
            typen.put(sagaId, type);
            payloads.put(sagaId, payload);
            Map<Integer, String> map = new LinkedHashMap<>();
            for (int i = 0; i < stepCount; i++) {
                map.put(i, UnfinishedSaga.STEP_PENDING);
            }
            schritte.put(sagaId, map);
            protokoll.add("start");
        }

        @Override
        public void markStepInFlight(String sagaId, int index) throws SQLException {
            Map<Integer, String> map = schritte.get(sagaId);
            if (map == null || !UnfinishedSaga.STEP_PENDING.equals(map.get(index))) {
                throw new SQLException("Guard verletzt: Schritt " + index + " ist nicht PENDING");
            }
            map.put(index, UnfinishedSaga.STEP_IN_FLIGHT);
            protokoll.add("inflight:" + index);
        }

        @Override
        public void stepDone(Connection connection, String sagaId, int index, String name) throws SQLException {
            Map<Integer, String> map = schritte.get(sagaId);
            if (map == null || !UnfinishedSaga.STEP_IN_FLIGHT.equals(map.get(index))) {
                throw new SQLException("Guard verletzt: Schritt " + index + " ist nicht IN_FLIGHT");
            }
            map.put(index, UnfinishedSaga.STEP_DONE);
            protokoll.add("done:" + index);
            if (index == stepDoneWirftNachCommitBei) {
                throw new SQLException("Verbindungsabbruch beim Commit (mehrdeutig)");
            }
        }

        @Override
        public boolean isStepDone(String sagaId, int index) {
            Map<Integer, String> map = schritte.get(sagaId);
            return map != null && UnfinishedSaga.STEP_DONE.equals(map.get(index));
        }

        @Override
        public void stepCompensated(Connection connection, String sagaId, int index) throws SQLException {
            Map<Integer, String> map = schritte.get(sagaId);
            String aktuell = map == null ? null : map.get(index);
            if (!UnfinishedSaga.STEP_DONE.equals(aktuell) && !UnfinishedSaga.STEP_IN_FLIGHT.equals(aktuell)) {
                throw new SQLException("Guard verletzt: Schritt " + index + " ist '" + aktuell + "'");
            }
            map.put(index, UnfinishedSaga.STEP_COMPENSATED);
            protokoll.add("compensated:" + index);
        }

        @Override
        public void markState(String sagaId, SagaState state) throws SQLException {
            if (state == SagaState.COMPLETED && completedMarkierungWirft) {
                throw new SQLException("COMPLETED-Markierung simuliert fehlerhaft");
            }
            if (state == SagaState.FAILED && failedMarkierungWirft) {
                throw new SQLException("FAILED-Markierung simuliert fehlgeschlagen");
            }
            String aktuell = koepfe.get(sagaId);
            boolean erlaubt = aktuell == null || aktuell.equals(state.name())
                    || switch (state) {
                        case FAILED, COMPENSATING, COMPENSATED -> List.of(
                                SagaState.RUNNING.name(), SagaState.FAILED.name(),
                                SagaState.COMPENSATING.name()).contains(aktuell);
                        case COMPLETED -> SagaState.RUNNING.name().equals(aktuell);
                        case RUNNING -> false;
                    };
            if (!erlaubt) {
                throw new SQLException("Statuswechsel '" + aktuell + "' → " + state + " nicht erlaubt");
            }
            koepfe.put(sagaId, state.name());
            protokoll.add("state:" + state);
        }

        @Override
        public boolean isStepCompensated(String sagaId, int index) throws SQLException {
            if (isStepCompensatedWirft) {
                throw new SQLException("Journal-Lesefehler simuliert");
            }
            Map<Integer, String> map = schritte.get(sagaId);
            return map != null && UnfinishedSaga.STEP_COMPENSATED.equals(map.get(index));
        }

        @Override
        public List<UnfinishedSaga> unfinished() throws SQLException {
            List<UnfinishedSaga> ergebnis = new ArrayList<>();
            for (Map.Entry<String, String> kopf : koepfe.entrySet()) {
                SagaState state;
                try {
                    state = SagaState.valueOf(kopf.getValue());
                } catch (IllegalArgumentException e) {
                    continue; // robust wie die Oracle-Umsetzung (Fund M4)
                }
                if (state == SagaState.RUNNING || state == SagaState.FAILED
                        || state == SagaState.COMPENSATING) {
                    UnfinishedSaga saga = new UnfinishedSaga(
                            kopf.getKey(), typen.get(kopf.getKey()), payloads.get(kopf.getKey()), state);
                    schritte.get(kopf.getKey()).forEach(saga::putStepState);
                    ergebnis.add(saga);
                }
            }
            return ergebnis;
        }

        @Override
        public synchronized SagaClaim claimUnfinished(String sagaId, String type) throws SQLException {
            String zustand = koepfe.get(sagaId);
            if (zustand == null || !type.equals(typen.get(sagaId)) || beansprucht.contains(sagaId)) {
                return null;
            }
            SagaState state;
            try {
                state = SagaState.valueOf(zustand);
            } catch (IllegalArgumentException e) {
                return null;
            }
            if (state != SagaState.RUNNING && state != SagaState.FAILED
                    && state != SagaState.COMPENSATING) {
                return null;
            }
            beansprucht.add(sagaId);
            UnfinishedSaga saga = new UnfinishedSaga(sagaId, type, payloads.get(sagaId), state);
            schritte.get(sagaId).forEach(saga::putStepState);
            return new SagaClaim() {
                private boolean ended;
                @Override
                public UnfinishedSaga saga() {
                    return saga;
                }

                @Override
                public void finish(SagaState endState) throws SQLException {
                    if (ended) return;
                    markState(sagaId, endState);
                    beansprucht.remove(sagaId);
                    ended = true;
                    if (endState == SagaState.FAILED && afterFailedRelease != null) {
                        Runnable callback = afterFailedRelease;
                        afterFailedRelease = null;
                        callback.run();
                    }
                }

                @Override
                public void close() {
                    if (!ended) {
                        ended = true;
                        beansprucht.remove(sagaId);
                    }
                }
            };
        }
    }

    /** Pool, der JDBC-Proxy-Verbindungen ohne echte Datenbank liefert. */
    private static final class FakePool implements ConnectionPool {

        @Override
        public Connection getConnection() {
            return (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(),
                    new Class<?>[]{Connection.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "setAutoCommit", "setTransactionIsolation", "commit", "rollback", "close"
                                -> null;
                        case "isClosed" -> false;
                        case "toString" -> "FakeConnection";
                        case "hashCode" -> System.identityHashCode(proxy);
                        default -> throw new UnsupportedOperationException(
                                "FakeConnection unterstützt '" + method.getName() + "' nicht");
                    });
        }

        @Override
        public void release(Connection connection) {
            // keine echte Verbindung — nichts freizugeben
        }

        @Override
        public void close() {
            // nichts zu schließen
        }
    }
}
