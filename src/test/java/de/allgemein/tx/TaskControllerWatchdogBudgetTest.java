package de.allgemein.tx;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Nagelt die Zeitlimit-Budget-Semantik aus CR-047 fest: Der Wachhund des
 * TaskController misst die GESAMTE Task-Ausführung inklusive aller Retry-Versuche
 * und Backoff-Pausen — die TxContext-Deadline gilt dagegen je Versuch. Ein Task,
 * dessen Wiederholung (ORA-00060 + 200-ms-Backoff) über das enge Task-Zeitlimit
 * hinausläuft, wird vom Wachhund abgebrochen; mit großzügigem Budget committet
 * derselbe Task im zweiten Versuch.
 */
class TaskControllerWatchdogBudgetTest {

    private FakePool pool;
    private TaskController tasks;

    @BeforeEach
    void aufbauen() {
        pool = new FakePool(2);
        tasks = new TaskController(new TxManager(pool), 2, 10);
    }

    @AfterEach
    void abbauen() {
        tasks.close();
    }

    private static SQLException oraDeadlock() {
        return new SQLException("ORA-00060: deadlock detected while waiting for resource", "61000", 60);
    }

    @Test
    void watchdogBrichtTaskWaehrendBackoffDerWiederholungAb() throws Exception {
        // 1. Versuch: ORA-00060 (rollt zurück, 200 ms Backoff) — 2. Versuch würde
        // laufen, aber das Task-Zeitlimit (250 ms) läuft während des Backoffs ab:
        // Gesamt-Budget schlägt Einzelversuch-Deadline.
        AtomicInteger versuche = new AtomicInteger();
        TxOptions eng = new TxOptions(java.sql.Connection.TRANSACTION_READ_COMMITTED,
                Duration.ofMillis(250), 3);
        TxException abbruch = assertThrows(TxException.class, () ->
                tasks.submit("backoff-test", eng, ctx -> {
                    versuche.incrementAndGet();
                    throw oraDeadlock();
                }).result());
        assertTrue(!abbruch.isRetryable() || abbruch.getMessage() != null,
                "Abbruch wird als nicht wiederholbare TxException gemeldet");
        assertEquals(1, tasks.stats().timedOut(), "Der Wachhund hat den Task als Timeout gezählt");
    }

    @Test
    void grosszuegigesBudgetLaesstDieWiederholungCommitten() throws Exception {
        // Derselbe Konflikt, aber 5 s Budget: Der 2. Versuch (nach 200 ms Backoff)
        // läuft durch und committet — das Verhalten ist nur eine Frage des Budgets.
        AtomicInteger versuche = new AtomicInteger();
        TxOptions weit = new TxOptions(java.sql.Connection.TRANSACTION_READ_COMMITTED,
                Duration.ofSeconds(5), 3);
        String ergebnis = tasks.submit("retry-test", weit, ctx -> {
            if (versuche.incrementAndGet() == 1) {
                throw oraDeadlock();
            }
            return "ok";
        }).result();
        assertEquals("ok", ergebnis);
        assertEquals(2, versuche.get(), "Nach dem Backoff committet der zweite Versuch");
        assertEquals(1, pool.commits.get());
    }
}
