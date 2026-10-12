package de.allgemein.tx;

import de.allgemein.lmg.service.FachlicheException;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verhaltens-Tests des TxManager mit Fake-Pool (Codereview CR-019): die Retry-
 * Schleife, der Error-Rollback und die manuelle API waren bislang nur durch
 * Review, nicht durch Regression gesichert.
 */
class TxManagerTest {

    private static SQLException oraDeadlock() {
        // SQLState 61000 = Oracle-Deadlock-Bereich; ORA-00060 im Klartext
        return new SQLException("ORA-00060: deadlock detected while waiting for resource", "61000");
    }

    @Test
    void retryBeiDeadlockCommittetImZweitenVersuch() throws Exception {
        FakePool pool = new FakePool(1);
        TxManager manager = new TxManager(pool);
        int[] aufrufe = {0};
        String ergebnis = manager.execute(TxOptions.DEFAULT, ctx -> {
            if (++aufrufe[0] == 1) {
                throw oraDeadlock();
            }
            return "ok";
        });
        assertEquals("ok", ergebnis);
        assertEquals(2, aufrufe[0]);
        assertEquals(1, pool.commits.get(), "Genau ein Commit nach dem erfolgreichen Versuch");
        assertEquals(1, pool.rollbacks.get(), "Der Fehlversuch wurde zurückgerollt");
    }

    @Test
    void maxAttemptsErschoepftWirftNachDreiRollbacks() throws Exception {
        FakePool pool = new FakePool(1);
        TxManager manager = new TxManager(pool);
        TxException fehler = assertThrows(TxException.class,
                () -> manager.execute(TxOptions.DEFAULT, ctx -> {
                    throw oraDeadlock();
                }));
        assertTrue(fehler.isRetryable());
        assertEquals(3, pool.rollbacks.get(), "Default maxAttempts = 3");
        assertEquals(0, pool.commits.get());
    }

    @Test
    void fachfehlerWirdNieWiederholt() throws Exception {
        FakePool pool = new FakePool(1);
        TxManager manager = new TxManager(pool);
        int[] aufrufe = {0};
        assertThrows(FachlicheException.class, () -> manager.execute(TxOptions.DEFAULT, ctx -> {
            aufrufe[0]++;
            throw new FachlicheException(FachlicheException.VALIDATION, "Fachfehler");
        }));
        assertEquals(1, aufrufe[0], "Fachliche Fehler brechen sofort ab (AGENTS-Regel 2)");
        assertEquals(1, pool.rollbacks.get());
    }

    @Test
    void errorFuehrtZuRollbackUndWirdDurchgereicht() throws Exception {
        FakePool pool = new FakePool(1);
        TxManager manager = new TxManager(pool);
        assertThrows(OutOfMemoryError.class, () -> manager.execute(TxOptions.DEFAULT, ctx -> {
            throw new OutOfMemoryError("simuliert");
        }));
        assertEquals(1, pool.rollbacks.get(), "Auch Errors rollen zurück — nie impliziter Commit");
        assertEquals(1, pool.releases.get(), "Verbindung wird auch nach Error genau einmal freigegeben");
    }

    @Test
    void manuelleApiCommitUndFinishGibtVerbindungZurueck() throws Exception {
        FakePool pool = new FakePool(1);
        TxManager manager = new TxManager(pool);
        TxContext ctx = manager.begin(TxOptions.DEFAULT);
        ctx.connection().createStatement();
        manager.commit(ctx);
        manager.finish(ctx);
        assertEquals(1, pool.commits.get());
        assertEquals(1, pool.releases.get());
        assertEquals(TxStatus.COMMITTED, ctx.status());
    }

    @Test
    void closeOhneFinishLaesstDenPoolLeerlaufen() throws Exception {
        // CR-018: TxContext.close() rollt zurück, gibt die Verbindung aber NICHT zurück —
        // genau dieses Idiom (try-with-resources ohne finish) muss sichtbar erschöpfen.
        FakePool pool = new FakePool(1);
        TxManager manager = new TxManager(pool);
        TxContext ctx = manager.begin(TxOptions.DEFAULT);
        try (ctx) {
            ctx.connection().createStatement();
        }
        assertEquals(TxStatus.ABORTED, ctx.status());
        assertEquals(0, pool.releases.get(), "close() allein gibt keine Verbindung zurück");
        assertThrows(SQLException.class, () -> manager.begin(TxOptions.DEFAULT),
                "Zweites begin() muss am leeren Pool scheitern");
    }

    @Test
    void rollbackNachCommitIstNoOp() throws Exception {
        FakePool pool = new FakePool(1);
        TxManager manager = new TxManager(pool);
        TxContext ctx = manager.begin(TxOptions.DEFAULT);
        manager.commit(ctx);
        manager.rollback(ctx);
        assertEquals(1, pool.commits.get());
        assertEquals(0, pool.rollbacks.get(), "Rollback nach Commit ändert nichts mehr");
        manager.finish(ctx);
    }
}
