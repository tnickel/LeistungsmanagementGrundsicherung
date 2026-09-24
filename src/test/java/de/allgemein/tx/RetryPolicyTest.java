package de.allgemein.tx;

import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetryPolicyTest {

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
        SQLException second = new SQLException("dann", "61000", 0);
        first.setNextException(second);
        assertTrue(RetryPolicy.isRetryable(first));
    }

    @Test
    void nullSqlStateIstUnschädlich() {
        assertFalse(RetryPolicy.isRetryable(new SQLException("ohne State", null, 1)));
    }
}
