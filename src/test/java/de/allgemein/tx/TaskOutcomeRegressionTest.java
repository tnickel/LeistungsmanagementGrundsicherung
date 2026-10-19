package de.allgemein.tx;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class TaskOutcomeRegressionTest {
    @Test
    void wachhundNachInneremCommitMeldetUnbekanntenAusgangStattRollback() throws Exception {
        FakePool pool = new FakePool(2);
        TxManager tx = new TxManager(pool);
        CountDownLatch committed = new CountDownLatch(1), release = new CountDownLatch(1);
        try (TaskController tasks = new TaskController(tx, 1, 2)) {
            var handle = tasks.submit("innerer-commit", TxOptions.DEFAULT.withTimeout(Duration.ofMillis(200)), ctx -> {
                tx.execute(TxOptions.DEFAULT, inner -> "persistiert");
                committed.countDown();
                boolean interrupted = false;
                while (true) {
                    try { if (release.await(2, TimeUnit.SECONDS)) break; }
                    catch (InterruptedException e) { interrupted = true; }
                }
                if (interrupted) Thread.currentThread().interrupt();
                return "fertig";
            });
            try {
                assertTrue(committed.await(2, TimeUnit.SECONDS));
                var error = assertThrows(TaskOutcomeUnknownException.class, handle::result);
                assertFalse(error.isRetryable());
                assertTrue(error.getMessage().contains("Ausgang unbekannt"));
                assertTrue(error.getMessage().contains("nicht erneut anordnen"));
                assertEquals(1, pool.commits.get(), "innerer Commit bleibt wirksam");
            } finally { release.countDown(); }
        }
        assertEquals(1, pool.commits.get(), "unterbrochene äußere Transaktion darf nicht committen");
        assertEquals(1, pool.rollbacks.get());
    }

    @Test
    void aeussererDeadlineFehlerWirdAlsUnbekannterTaskAusgangUebersetzt() {
        try (TaskController tasks = new TaskController(new TxManager(new FakePool(1)), 1, 1)) {
            var handle = tasks.submit("deadline", TxOptions.DEFAULT,
                    ctx -> { throw TxException.timeout("Testdeadline"); });
            assertThrows(TaskOutcomeUnknownException.class, handle::result);
        }
    }
}
