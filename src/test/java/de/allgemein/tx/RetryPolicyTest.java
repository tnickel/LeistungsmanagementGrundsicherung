package de.allgemein.tx;

import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class RetryPolicyTest {

    @Test
    void findetOracleKonfliktAuchUnterVerschachtelterUrsache() {
        SQLException wrapper = new SQLException("Treiber-Wrapper", "72000", 0,
                new IllegalStateException("Wrapper", new SQLException("busy", "72000", 54)));
        assertTrue(RetryPolicy.isRetryable(wrapper));
        assertFalse(RetryPolicy.isRetryable(new SQLException("Wrapper", new SQLException("unique", "23000", 1))));
        assertFalse(RetryPolicy.isRetryable(null));
    }

    @Test
    void zyklischeNextUndCauseKettenBeendenDieKlassifikation() {
        SQLException first = new SQLException("erst", "72000", 0);
        SQLException second = new SQLException("zweiter", "72000", 1);
        first.setNextException(second);
        second.setNextException(first);
        first.initCause(second);
        second.initCause(first);
        assertTimeoutPreemptively(Duration.ofSeconds(1), () -> assertFalse(RetryPolicy.isRetryable(first)));
    }

    @Test
    void backoffIstAuchMitMaximalemStartwertUndVersuchUeberlaufsicherBegrenzt() {
        RetryPolicy policy = new RetryPolicy(Long.MAX_VALUE);
        for (int attempt : new int[]{1, 2, 11, Integer.MAX_VALUE}) {
            assertEquals(30_000, policy.backoffMillis(attempt));
            for (int i = 0; i < 20; i++) {
                long delay = policy.delayMillis(attempt);
                assertTrue(delay >= 30_000 && delay <= 45_000);
            }
        }
        assertEquals(200, new RetryPolicy().backoffMillis(1));
        assertEquals(400, new RetryPolicy().backoffMillis(2));
        assertThrows(IllegalArgumentException.class, () -> policy.delayMillis(0));
        assertThrows(IllegalArgumentException.class, () -> policy.delayMillis(-1));
    }

    @Test
    void grosserBackoffBewahrtUnterbrechungsursacheUndFlag() {
        try {
            Thread.currentThread().interrupt();
            TxException failure = assertThrows(TxException.class,
                    () -> new RetryPolicy(Long.MAX_VALUE).sleepBeforeAttempt(Integer.MAX_VALUE));
            assertFalse(failure.isRetryable());
            assertTrue(failure.getCause() instanceof InterruptedException);
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void deadlockIstWiederholbar() {
        assertTrue(RetryPolicy.isRetryable(new SQLException("deadlock detected", "72000", 60)));
    }

    @Test
    void ressourceBelegtIstWiederholbar() {
        assertTrue(RetryPolicy.isRetryable(new SQLException("resource busy", "61000", 54)));
    }

    @Test
    void serialisierbarkeitskonfliktIstWiederholbar() {
        assertTrue(RetryPolicy.isRetryable(new SQLException("cannot serialize access", "72000", 8177)));
    }

    @Test
    void duplikatsfehlerIstNichtWiederholbar() {
        assertFalse(RetryPolicy.isRetryable(new SQLException("unique constraint violated", "23000", 1)));
    }

    @Test
    void verketteteFehlerWerdenDurchsucht() throws Exception {
        SQLException first = new SQLException("erst", "23000", 1);
        SQLException second = new SQLException("dann", "61000", 60);
        first.setNextException(second);
        assertTrue(RetryPolicy.isRetryable(first));
    }

    @Test
    void nullSqlStateIstUnschädlich() {
        assertFalse(RetryPolicy.isRetryable(new SQLException("ohne State", null, 1)));
    }

    @Test
    void sqlStateAlleinUndOracleRessourcenfehlerSindNichtWiederholbar() {
        for (String state : new String[]{"61000", "40001"}) {
            for (int code : new int[]{0, 18, 20, 4031}) {
                assertFalse(RetryPolicy.isRetryable(new SQLException("Ressourcenfehler", state, code)),
                        "SQLState=" + state + ", ORA=" + code);
            }
        }
    }
}
