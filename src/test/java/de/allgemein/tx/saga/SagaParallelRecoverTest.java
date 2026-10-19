package de.allgemein.tx.saga;

import de.allgemein.tx.FakePool;
import de.allgemein.tx.TxManager;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Paralleler Wiederanlauf (Codereview-Testlücke L-12): Zwei recover-Läufe über
 * dasselbe Journal — das Claim über „FOR UPDATE SKIP LOCKED“ (hier: atomares
 * Claim-Flag im Journal-Fake) muss garantieren, dass JEDER Schritt höchstens
 * einmal kompensiert wird. Ohne Claim würden beide Threads dieselbe Saga
 * kompensieren und die Kompensationszähler verdoppeln.
 */
class SagaParallelRecoverTest {

    /** In-Memory-Journal mit atomarem Claim (entspricht SKIP LOCKED semantisch). */
    private static final class JournalFake implements TxSagaJournal {
        private static final class Kopf {
            final String sagaId;
            final String type;
            final String payload;
            final Map<Integer, String> steps = new LinkedHashMap<>();
            volatile SagaState state = SagaState.RUNNING;
            final AtomicReference<Boolean> claimed = new AtomicReference<>(Boolean.FALSE);

            Kopf(String sagaId, String type, String payload) {
                this.sagaId = sagaId;
                this.type = type;
                this.payload = payload;
            }
        }

        private final Map<String, Kopf> koepfe = new LinkedHashMap<>();

        void legeUnfertigeSagaAn(String sagaId, int schritte, int inFlightAb) {
            Kopf kopf = new Kopf(sagaId, "PARALLEL_TEST", "{}");
            for (int i = 0; i < schritte; i++) {
                kopf.steps.put(i, i < inFlightAb ? UnfinishedSaga.STEP_DONE
                        : i == inFlightAb ? UnfinishedSaga.STEP_IN_FLIGHT
                        : UnfinishedSaga.STEP_PENDING);
            }
            koepfe.put(sagaId, kopf);
        }

        synchronized List<String> endZustaende() {
            List<String> zustaende = new ArrayList<>();
            for (Kopf kopf : koepfe.values()) {
                zustaende.add(kopf.sagaId + "=" + kopf.state);
            }
            return zustaende;
        }

        @Override
        public void start(String sagaId, String type, String payload, int stepCount) {
            // im Test nicht gebraucht
        }

        @Override
        public void markStepInFlight(String sagaId, int index) {
            // im Test nicht gebraucht
        }

        @Override
        public void stepDone(Connection connection, String sagaId, int index, String name) {
            // im Test nicht gebraucht
        }

        @Override
        public void stepCompensated(Connection connection, String sagaId, int index) {
            synchronized (this) {
                koepfe.get(sagaId).steps.put(index, UnfinishedSaga.STEP_COMPENSATED);
            }
        }

        @Override
        public void markState(String sagaId, SagaState state) {
            synchronized (this) {
                koepfe.get(sagaId).state = state;
            }
        }

        @Override
        public boolean isStepCompensated(String sagaId, int index) {
            synchronized (this) {
                return UnfinishedSaga.STEP_COMPENSATED.equals(koepfe.get(sagaId).steps.get(index));
            }
        }

        @Override
        public synchronized List<UnfinishedSaga> unfinished() {
            List<UnfinishedSaga> offen = new ArrayList<>();
            for (Kopf kopf : koepfe.values()) {
                if (kopf.state == SagaState.COMPLETED || kopf.state == SagaState.COMPENSATED) {
                    continue;
                }
                UnfinishedSaga saga = new UnfinishedSaga(kopf.sagaId, kopf.type, kopf.payload, kopf.state);
                kopf.steps.forEach(saga::putStepState);
                kopf.steps.keySet().forEach(i -> saga.putStepName(i, List.of("eins", "zwei", "drei").get(i)));
                offen.add(saga);
            }
            return offen;
        }

        @Override
        public SagaClaim claimUnfinished(String sagaId, String type) {
            Kopf kopf;
            synchronized (this) {
                kopf = koepfe.get(sagaId);
                if (kopf == null || kopf.state == SagaState.COMPLETED
                        || kopf.state == SagaState.COMPENSATED) {
                    return null;
                }
            }
            // Das atomare CompareAndSet entspricht dem SELECT … FOR UPDATE SKIP LOCKED:
            // Der zweite parallele Lauf erhält null und arbeitet die nächste Saga ab.
            if (!kopf.claimed.compareAndSet(Boolean.FALSE, Boolean.TRUE)) {
                return null;
            }
            UnfinishedSaga snapshot;
            synchronized (this) {
                snapshot = new UnfinishedSaga(kopf.sagaId, kopf.type, kopf.payload, kopf.state);
                kopf.steps.forEach(snapshot::putStepState);
                kopf.steps.keySet().forEach(i -> snapshot.putStepName(i, List.of("eins", "zwei", "drei").get(i)));
            }
            return new SagaClaim() {
                @Override
                public UnfinishedSaga saga() {
                    return snapshot;
                }

                @Override
                public void finish(SagaState endState) {
                    markState(sagaId, endState);
                    kopf.claimed.set(Boolean.FALSE);
                }

                @Override
                public void close() {
                    kopf.claimed.set(Boolean.FALSE);
                }
            };
        }
    }

    @Test
    void zweiParalleleWiederanlaeufeKompensierenJedenSchrittGenauEinmal() throws Exception {
        JournalFake journal = new JournalFake();
        AtomicInteger kompensationen = new AtomicInteger();
        SagaDefinition definition = SagaDefinition.of("PARALLEL_TEST")
                .step("eins", (c, p, id) -> { }, (c, p, id) -> kompensationen.incrementAndGet())
                .step("zwei", (c, p, id) -> { }, (c, p, id) -> kompensationen.incrementAndGet())
                .step("drei", (c, p, id) -> { }, (c, p, id) -> kompensationen.incrementAndGet())
                .build();
        for (int i = 1; i <= 4; i++) {
            // 3 Schritte: 0 und 1 DONE, 2 IN_FLIGHT → Wiederanlauf kompensiert 3, 2, 1
            journal.legeUnfertigeSagaAn("saga-" + i, 3, 2);
        }

        SagaEngine engine = new SagaEngine(new TxManager(new FakePool(4)), journal);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            Future<?> laufA = executor.submit(() -> {
                start.await();
                engine.recoverWithReport(definition);
                return null;
            });
            Future<?> laufB = executor.submit(() -> {
                start.await();
                engine.recoverWithReport(definition);
                return null;
            });
            start.countDown();
            laufA.get(30, TimeUnit.SECONDS);
            laufB.get(30, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        // 4 Sagas × 3 zu kompensierende Schritte (DONE/DONE/IN_FLIGHT) = genau 12 —
        // wäre das Claim kaputt, klängen Kompensationen doppelt (24).
        assertEquals(12, kompensationen.get(),
                "Jeder Schritt wird trotz paralleler Wiederanläufe genau einmal kompensiert");
        for (String zustand : journal.endZustaende()) {
            assertTrue(zustand.endsWith("COMPENSATED"), "Alle Sagas abgeschlossen: " + zustand);
        }
    }
}
