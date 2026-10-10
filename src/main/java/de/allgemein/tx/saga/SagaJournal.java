package de.allgemein.tx.saga;

import de.allgemein.tx.ConnectionPool;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Journal für lange Geschäftsvorgänge (Saga-Muster): Jeder abgeschlossene Schritt wird
 * nach seinem Commit dauerhaft festgehalten, damit nach einem Absturz weitergearbeitet
 * oder kompensiert werden kann. Funktional entspricht das dem CICS-Journal: Es sichert
 * die Unit of Work über mehrere einzelne Datenbanktransaktionen hinweg.
 */
public final class SagaJournal {

    private final ConnectionPool pool;

    public SagaJournal(ConnectionPool pool) {
        this.pool = pool;
    }

    public void start(String sagaId, String type, String payload, int stepCount) throws SQLException {
        exec("INSERT INTO TX_SAGA (SAGA_ID, SAGA_TYPE, STATE, PAYLOAD) VALUES (?, ?, ?, ?)", stmt -> {
            stmt.setString(1, sagaId);
            stmt.setString(2, type);
            stmt.setString(3, SagaState.RUNNING.name());
            stmt.setString(4, payload);
        });
        for (int i = 0; i < stepCount; i++) {
            final int index = i;
            exec("INSERT INTO TX_SAGA_STEP (SAGA_ID, IDX, STATE) VALUES (?, ?, 'PENDING')", stmt -> {
                stmt.setString(1, sagaId);
                stmt.setInt(2, index);
            });
        }
    }

    public void stepDone(String sagaId, int index, String name) throws SQLException {
        exec("UPDATE TX_SAGA_STEP SET STATE = 'DONE', NAME = ?, UPDATED_AT = SYSTIMESTAMP WHERE SAGA_ID = ? AND IDX = ?",
                stmt -> {
                    stmt.setString(1, name);
                    stmt.setString(2, sagaId);
                    stmt.setInt(3, index);
                });
    }

    public void stepCompensated(String sagaId, int index) throws SQLException {
        exec("UPDATE TX_SAGA_STEP SET STATE = 'COMPENSATED', UPDATED_AT = SYSTIMESTAMP WHERE SAGA_ID = ? AND IDX = ?",
                stmt -> {
                    stmt.setString(1, sagaId);
                    stmt.setInt(2, index);
                });
    }

    public void markState(String sagaId, SagaState state) throws SQLException {
        exec("UPDATE TX_SAGA SET STATE = ?, UPDATED_AT = SYSTIMESTAMP WHERE SAGA_ID = ?", stmt -> {
            stmt.setString(1, state.name());
            stmt.setString(2, sagaId);
        });
    }

    public boolean isStepCompensated(String sagaId, int index) throws SQLException {
        Connection connection = pool.getConnection();
        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT STATE FROM TX_SAGA_STEP WHERE SAGA_ID = ? AND IDX = ?")) {
            stmt.setString(1, sagaId);
            stmt.setInt(2, index);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() && "COMPENSATED".equals(rs.getString(1));
            }
        } finally {
            pool.release(connection);
        }
    }

    /** Alle nicht abgeschlossenen Sagas, gruppiert je Vorgang — Basis für den Wiederanlauf. */
    public List<UnfinishedSaga> unfinished() throws SQLException {
        String sql = """
                SELECT s.SAGA_ID, s.SAGA_TYPE, s.PAYLOAD, s.STATE, st.IDX, st.STATE AS STEP_STATE
                FROM TX_SAGA s
                LEFT JOIN TX_SAGA_STEP st ON st.SAGA_ID = s.SAGA_ID
                WHERE s.STATE IN ('RUNNING', 'FAILED', 'COMPENSATING')
                ORDER BY s.SAGA_ID, st.IDX
                """;
        Connection connection = pool.getConnection();
        try (PreparedStatement stmt = connection.prepareStatement(sql); ResultSet rs = stmt.executeQuery()) {
            Map<String, UnfinishedSaga> byId = new LinkedHashMap<>();
            while (rs.next()) {
                final String sagaId = rs.getString(1);
                final String type = rs.getString(2);
                final String payload = rs.getString(3);
                final SagaState state = SagaState.valueOf(rs.getString(4));
                UnfinishedSaga saga = byId.computeIfAbsent(
                        sagaId,
                        id -> new UnfinishedSaga(id, type, payload, state));
                int index = rs.getInt(5);
                String stepState = rs.getString(6);
                if (!rs.wasNull()) {
                    saga.putStepState(index, stepState);
                }
            }
            return new ArrayList<>(byId.values());
        } finally {
            pool.release(connection);
        }
    }

    private void exec(String sql, Binder binder) throws SQLException {
        Connection connection = pool.getConnection();
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            binder.bind(stmt);
            stmt.executeUpdate();
        } finally {
            pool.release(connection);
        }
    }

    @FunctionalInterface
    private interface Binder {
        void bind(PreparedStatement stmt) throws SQLException;
    }
}
