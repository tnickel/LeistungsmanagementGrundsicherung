package de.allgemein.tx;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class TxContextDeadlineTest {

    @Test
    void monotonesBudgetLaeuftExaktAnDerGrenzeAb() throws Exception {
        AtomicLong nanos = new AtomicLong(100);
        var pool = new FakePool(1);
        var connection = pool.getConnection();
        try (var ctx = new TxContext(connection, Duration.ofSeconds(10), nanos::get)) {
            assertEquals(Duration.ofSeconds(10), ctx.remaining());
            nanos.addAndGet(Duration.ofSeconds(9).toNanos());
            assertEquals(Duration.ofSeconds(1), ctx.remaining());
            ctx.checkDeadline();
            nanos.addAndGet(Duration.ofSeconds(1).toNanos());
            assertEquals(Duration.ZERO, ctx.remaining());
            assertThrows(TxException.class, ctx::checkDeadline);
            nanos.incrementAndGet();
            assertEquals(Duration.ofNanos(-1), ctx.remaining());
        } finally {
            pool.release(connection);
        }
    }

    @Test
    void nanoTimeZaehlerueberlaufVerlaengertDasBudgetNicht() throws Exception {
        AtomicLong nanos = new AtomicLong(Long.MAX_VALUE - 5);
        var pool = new FakePool(1);
        var connection = pool.getConnection();
        try (var ctx = new TxContext(connection, Duration.ofNanos(20), nanos::get)) {
            nanos.set(Long.MIN_VALUE + 4); // zehn Nanosekunden später, trotz Vorzeichenwechsel
            assertEquals(Duration.ofNanos(10), ctx.remaining());
            ctx.checkDeadline();
            nanos.addAndGet(10);
            assertThrows(TxException.class, ctx::checkDeadline);
        } finally {
            pool.release(connection);
        }
    }

    @Test
    void langeGueltigeDurationErfordertKeineUeberlaufendeNanosekundenkonversion() throws Exception {
        AtomicLong nanos = new AtomicLong();
        var pool = new FakePool(1);
        var connection = pool.getConnection();
        Duration longBudget = Duration.ofSeconds(Long.MAX_VALUE);
        try (var ctx = new TxContext(connection, longBudget, nanos::get)) {
            nanos.addAndGet(1_000_000_000);
            assertEquals(longBudget.minusSeconds(1), ctx.remaining());
            ctx.checkDeadline();
        } finally {
            pool.release(connection);
        }
    }
}
