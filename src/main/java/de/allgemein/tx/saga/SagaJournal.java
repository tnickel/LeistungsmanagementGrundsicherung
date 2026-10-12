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
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Journal für lange Geschäftsvorgänge (Saga-Muster) auf Oracle-Tabellen
 * (TX_SAGA/TX_SAGA_STEP). Funktional entspricht das dem CICS-Journal: Es sichert
 * die Unit of Work über mehrere einzelne Datenbanktransaktionen hinweg.
 *
 * <p>Schreibregeln (alle Updates sind mit Zustands-Guards versehen — ein verlorenes
 * Race ändert nichts mehr, sondern wirft oder ist idempotent kein Op):</p>
 * <ul>
 *   <li>Kopf + Schritte werden atomar angelegt (RUNNING / PENDING).</li>
 *   <li>Write-Ahead: PENDING → IN_FLIGHT in eigener kurzer Transaktion, BEVOR der
 *       Fachschritt läuft. IN_FLIGHT bedeutet „möglicherweise committet" und wird
 *       beim Wiederanlauf konservativ kompensiert.</li>
 *   <li>IN_FLIGHT → DONE und DONE/IN_FLIGHT → COMPENSATED werden über die
 *       {@code Connection}-Überladungen IN der Fachtransaktion geschrieben —
 *       Journal-Eintrag und Fachwirkung committen atomar.</li>
 * </ul>
 */
public final class SagaJournal implements TxSagaJournal {

    private static final Logger LOG = Logger.getLogger(SagaJournal.class.getName());

    /** Unvollendete Zustände, die der Wiederanlauf bearbeitet. */
    private static final String[] UNFINISHED_STATES = {
            SagaState.RUNNING.name(), SagaState.FAILED.name(), SagaState.COMPENSATING.name()};

    private final ConnectionPool pool;

    public SagaJournal(ConnectionPool pool) {
        this.pool = pool;
    }

    // ------------------------------------------------------------------
    // Schreiben
    // ------------------------------------------------------------------

    @Override
    public void start(String sagaId, String type, String payload, int stepCount) throws SQLException {
        // Kopf und alle Schritte atomar anlegen: ein Absturz dazwischen könnte sonst
        // eine RUNNING-Saga ohne Schritte hinterlassen.
        tx(connection -> {
            try (PreparedStatement stmt = connection.prepareStatement(
                    "INSERT INTO TX_SAGA (SAGA_ID, SAGA_TYPE, STATE, PAYLOAD) VALUES (?, ?, ?, ?)")) {
                stmt.setString(1, sagaId);
                stmt.setString(2, type);
                stmt.setString(3, SagaState.RUNNING.name());
                stmt.setString(4, payload);
                stmt.executeUpdate();
            }
            try (PreparedStatement stmt = connection.prepareStatement(
                    "INSERT INTO TX_SAGA_STEP (SAGA_ID, IDX, STATE) VALUES (?, ?, 'PENDING')")) {
                for (int i = 0; i < stepCount; i++) {
                    stmt.setString(1, sagaId);
                    stmt.setInt(2, i);
                    stmt.addBatch();
                }
                stmt.executeBatch();
            }
        });
    }

    @Override
    public void markStepInFlight(String sagaId, int index) throws SQLException {
        // Write-Ahead in eigener kurzer Transaktion; Guard: nur PENDING → IN_FLIGHT.
        tx(connection -> {
            int geaendert = execUpdate(connection,
                    "UPDATE TX_SAGA_STEP SET STATE = 'IN_FLIGHT', UPDATED_AT = SYSTIMESTAMP "
                            + "WHERE SAGA_ID = ? AND IDX = ? AND STATE = 'PENDING'",
                    stmt -> {
                        stmt.setString(1, sagaId);
                        stmt.setInt(2, index);
                    });
            if (geaendert != 1) {
                throw new SQLException("Write-Ahead für Schritt " + index + " der Saga " + sagaId
                        + " fehlgeschlagen: Schritt ist nicht mehr PENDING (aktuell: "
                        + leseSchrittState(connection, sagaId, index) + ")");
            }
        });
    }

    @Override
    public void stepDone(Connection connection, String sagaId, int index, String name) throws SQLException {
        // Läuft IN der Fachtransaktion (atomar mit deren Commit); Guard: nur IN_FLIGHT → DONE.
        int geaendert = execUpdate(connection,
                "UPDATE TX_SAGA_STEP SET STATE = 'DONE', NAME = ?, UPDATED_AT = SYSTIMESTAMP "
                        + "WHERE SAGA_ID = ? AND IDX = ? AND STATE = 'IN_FLIGHT'",
                stmt -> {
                    stmt.setString(1, name);
                    stmt.setString(2, sagaId);
                    stmt.setInt(3, index);
                });
        if (geaendert != 1) {
            throw new SQLException("Journal-Eintrag für Schritt " + index + " der Saga " + sagaId
                    + " fehlgeschlagen: Schritt ist nicht IN_FLIGHT (aktuell: "
                    + leseSchrittState(connection, sagaId, index) + ") — die Fachtransaktion wird zurückgerollt");
        }
    }

    @Override
    public void stepCompensated(Connection connection, String sagaId, int index) throws SQLException {
        // Läuft IN der Kompensationstransaktion; Guard: nur DONE/IN_FLIGHT → COMPENSATED.
        int geaendert = execUpdate(connection,
                "UPDATE TX_SAGA_STEP SET STATE = 'COMPENSATED', UPDATED_AT = SYSTIMESTAMP "
                        + "WHERE SAGA_ID = ? AND IDX = ? AND STATE IN ('DONE', 'IN_FLIGHT')",
                stmt -> {
                    stmt.setString(1, sagaId);
                    stmt.setInt(2, index);
                });
        if (geaendert != 1) {
            throw new SQLException("Kompensations-Eintrag für Schritt " + index + " der Saga " + sagaId
                    + " fehlgeschlagen: unerwarteter Schritt-Zustand (aktuell: "
                    + leseSchrittState(connection, sagaId, index) + ") — die Kompensation wird zurückgerollt");
        }
    }

    @Override
    public void markState(String sagaId, SagaState state) throws SQLException {
        String[] vorgaenger = vorgaengerVon(state);
        if (vorgaenger.length == 0) {
            throw new SQLException("Zustand " + state + " kann nicht als Ziel gesetzt werden");
        }
        tx(connection -> {
            int geaendert = execUpdate(connection,
                    "UPDATE TX_SAGA SET STATE = ?, " + payloadLoeschen(state) + "UPDATED_AT = SYSTIMESTAMP "
                            + "WHERE SAGA_ID = ? AND STATE IN (" + platzhalter(vorgaenger) + ")",
                    stmt -> {
                        stmt.setString(1, state.name());
                        stmt.setString(2, sagaId);
                        for (int i = 0; i < vorgaenger.length; i++) {
                            stmt.setString(3 + i, vorgaenger[i]);
                        }
                    });
            if (geaendert == 0) {
                String aktuell = leseSagaState(connection, sagaId);
                if (!state.name().equals(aktuell)) {
                    throw new SQLException("Statuswechsel '" + aktuell + "' → " + state.name()
                            + " für Saga " + sagaId + " nicht erlaubt");
                }
                // bereits im Zielzustand — idempotent, kein Fehler
            }
        });
    }

    // ------------------------------------------------------------------
    // Lesen
    // ------------------------------------------------------------------

    @Override
    public boolean isStepCompensated(String sagaId, int index) throws SQLException {
        Connection connection = pool.getConnection();
        try {
            return UnfinishedSaga.STEP_COMPENSATED.equals(leseSchrittState(connection, sagaId, index));
        } finally {
            pool.release(connection);
        }
    }

    @Override
    public boolean isStepDone(String sagaId, int index) throws SQLException {
        Connection connection = pool.getConnection();
        try {
            return UnfinishedSaga.STEP_DONE.equals(leseSchrittState(connection, sagaId, index));
        } finally {
            pool.release(connection);
        }
    }

    @Override
    public List<UnfinishedSaga> unfinished() throws SQLException {
        return unfinished(java.time.Duration.ZERO);
    }

    @Override
    public List<UnfinishedSaga> unfinished(java.time.Duration mindestInaktivitaet) throws SQLException {
        boolean mitFrist = mindestInaktivitaet != null && !mindestInaktivitaet.isZero()
                && !mindestInaktivitaet.isNegative();
        String frist = mitFrist ? """
                  AND s.UPDATED_AT <= SYSTIMESTAMP - NUMTODSINTERVAL(?, 'SECOND')
                  AND NOT EXISTS (SELECT 1 FROM TX_SAGA_STEP x WHERE x.SAGA_ID = s.SAGA_ID
                                  AND x.UPDATED_AT > SYSTIMESTAMP - NUMTODSINTERVAL(?, 'SECOND'))
                """ : "";
        String sql = """
                SELECT s.SAGA_ID, s.SAGA_TYPE, s.PAYLOAD, s.STATE, st.IDX, st.STATE AS STEP_STATE
                FROM TX_SAGA s
                LEFT JOIN TX_SAGA_STEP st ON st.SAGA_ID = s.SAGA_ID
                WHERE s.STATE IN ('RUNNING', 'FAILED', 'COMPENSATING')
                """ + frist + """
                ORDER BY s.SAGA_ID, st.IDX
                """;
        Connection connection = pool.getConnection();
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            if (mitFrist) {
                stmt.setLong(1, mindestInaktivitaet.getSeconds());
                stmt.setLong(2, mindestInaktivitaet.getSeconds());
            }
            return leseUnfinished(stmt);
        } finally {
            pool.release(connection);
        }
    }

    private List<UnfinishedSaga> leseUnfinished(PreparedStatement stmt) throws SQLException {
        try (ResultSet rs = stmt.executeQuery()) {
            Map<String, UnfinishedSaga> byId = new LinkedHashMap<>();
            List<String> ignoriert = new ArrayList<>();
            while (rs.next()) {
                final String sagaId = rs.getString(1);
                final String type = rs.getString(2);
                final String payload = rs.getString(3);
                final String stateText = rs.getString(4);
                // Robustes Mapping (M4): ein unbekannter Zustand legt den Wiederanlauf
                // nicht still — diese Saga wird protokolliert und übersprungen.
                SagaState state;
                try {
                    state = SagaState.valueOf(stateText);
                } catch (IllegalArgumentException e) {
                    if (!ignoriert.contains(sagaId)) {
                        ignoriert.add(sagaId);
                        LOG.log(Level.SEVERE, "Saga " + sagaId + " hat unbekannten Zustand '"
                                + stateText + "' und wird beim Wiederanlauf übersprungen", e);
                    }
                    continue;
                }
                if (ignoriert.contains(sagaId)) {
                    continue; // Schritt-Zeilen einer bereits übersprungenen Saga
                }
                UnfinishedSaga saga = byId.computeIfAbsent(
                        sagaId, id -> new UnfinishedSaga(id, type, payload, state));
                int index = rs.getInt(5);
                String stepState = rs.getString(6);
                if (!rs.wasNull()) {
                    saga.putStepState(index, stepState);
                }
            }
            return new ArrayList<>(byId.values());
        }
    }

    // ------------------------------------------------------------------
    // Wiederanlauf: exklusiver Anspruch (FOR UPDATE SKIP LOCKED)
    // ------------------------------------------------------------------

    @Override
    public SagaClaim claimUnfinished(String sagaId, String type) throws SQLException {
        Connection connection = pool.getConnection();
        SagaClaim claim = null;
        try {
            connection.setAutoCommit(false);
            String payload = null;
            SagaState state = null;
            String headSql = """
                    SELECT PAYLOAD, STATE FROM TX_SAGA
                    WHERE SAGA_ID = ? AND SAGA_TYPE = ?
                      AND STATE IN ('RUNNING', 'FAILED', 'COMPENSATING')
                    FOR UPDATE SKIP LOCKED
                    """;
            try (PreparedStatement stmt = connection.prepareStatement(headSql)) {
                stmt.setString(1, sagaId);
                stmt.setString(2, type);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        payload = rs.getString(1);
                        try {
                            state = SagaState.valueOf(rs.getString(2));
                        } catch (IllegalArgumentException e) {
                            LOG.log(Level.SEVERE, "Saga " + sagaId + " hat unbekannten Zustand '"
                                    + rs.getString(2) + "' und wird übersprungen", e);
                        }
                    }
                }
            }
            if (state == null) {
                // nicht vorhanden, bereits abgeschlossen oder von einem parallelen
                // Wiederanlauf gesperrt (SKIP LOCKED) — nichts zu tun
                connection.rollback();
                return null;
            }
            UnfinishedSaga saga = new UnfinishedSaga(sagaId, type, payload, state);
            try (PreparedStatement stmt = connection.prepareStatement(
                    "SELECT IDX, STATE FROM TX_SAGA_STEP WHERE SAGA_ID = ? ORDER BY IDX")) {
                stmt.setString(1, sagaId);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        saga.putStepState(rs.getInt(1), rs.getString(2));
                    }
                }
            }
            claim = new JournalClaim(connection, saga);
            return claim;
        } finally {
            if (claim == null) {
                try {
                    connection.rollback();
                } catch (SQLException ignored) {
                    // Verbindung ist ggf. bereits hinüber; der Pool verwirft sie
                }
                pool.release(connection);
            }
        }
    }

    /** Verbindungshaltender Anspruch: gibt die Sperre erst mit Commit/Rollback frei. */
    private final class JournalClaim implements SagaClaim {

        private final Connection connection;
        private final UnfinishedSaga saga;
        private boolean beendet = false;

        JournalClaim(Connection connection, UnfinishedSaga saga) {
            this.connection = connection;
            this.saga = saga;
        }

        @Override
        public UnfinishedSaga saga() {
            return saga;
        }

        @Override
        public void finish(SagaState endState) throws SQLException {
            if (beendet) {
                return;
            }
            try (PreparedStatement stmt = connection.prepareStatement(
                    "UPDATE TX_SAGA SET STATE = ?, " + payloadLoeschen(endState)
                            + "UPDATED_AT = SYSTIMESTAMP WHERE SAGA_ID = ?")) {
                stmt.setString(1, endState.name());
                stmt.setString(2, saga.sagaId());
                stmt.executeUpdate();
            }
            connection.commit();
            beendet = true;
            pool.release(connection);
        }

        @Override
        public void close() {
            if (beendet) {
                return;
            }
            beendet = true;
            try {
                connection.rollback();
            } catch (SQLException e) {
                LOG.log(Level.FINE, () -> "Rollback des Saga-Claims fehlgeschlagen: " + e.getMessage());
            }
            pool.release(connection);
        }
    }

    // ------------------------------------------------------------------
    // Hilfsmethoden
    // ------------------------------------------------------------------

    /**
     * Datensparsamkeit (§ 67c SGB X / Art. 5 DSGVO): Der Payload enthält Sozialdaten und wird
     * nur für den Wiederanlauf gebraucht. Mit dem Endzustand COMPLETED/COMPENSATED entfällt der
     * Zweck — der Payload wird gelöscht. FAILED behält ihn (manuelle Klärung/Wiederanlauf).
     */
    private static String payloadLoeschen(SagaState state) {
        return state == SagaState.COMPLETED || state == SagaState.COMPENSATED ? "PAYLOAD = NULL, " : "";
    }

    /** Erlaubte Vorgänger-Zustände für einen Zielzustand (Guard der Kopf-Updates). */
    private static String[] vorgaengerVon(SagaState state) {
        return switch (state) {
            // Abschluss nur aus RUNNING; der Wiederanlauf zieht die Markierung idempotent nach
            case COMPLETED -> new String[]{SagaState.RUNNING.name()};
            // Abbruch-/Kompensations-Zustände aus jedem laufenden Zustand (auch idempotent)
            case FAILED, COMPENSATING, COMPENSATED -> UNFINISHED_STATES;
            // RUNNING wird nie als Ziel geschrieben
            case RUNNING -> new String[0];
        };
    }

    private static String platzhalter(String[] werte) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < werte.length; i++) {
            sb.append(i == 0 ? "?" : ", ?");
        }
        return sb.toString();
    }

    private String leseSchrittState(String sagaId, int index) throws SQLException {
        Connection connection = pool.getConnection();
        try {
            return leseSchrittState(connection, sagaId, index);
        } finally {
            pool.release(connection);
        }
    }

    private String leseSchrittState(Connection connection, String sagaId, int index) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT STATE FROM TX_SAGA_STEP WHERE SAGA_ID = ? AND IDX = ?")) {
            stmt.setString(1, sagaId);
            stmt.setInt(2, index);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getString(1) : "<keine Zeile>";
            }
        }
    }

    private String leseSagaState(Connection connection, String sagaId) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT STATE FROM TX_SAGA WHERE SAGA_ID = ?")) {
            stmt.setString(1, sagaId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getString(1) : "<keine Zeile>";
            }
        }
    }

    private interface JournalTx {
        void run(Connection connection) throws SQLException;
    }

    private interface JournalFunktion<T> {
        T apply(Connection connection) throws SQLException;
    }

    /** Führt die Arbeit in einer kurzen eigenen Transaktion aus (Commit/Rollback/Release). */
    private void tx(JournalTx work) throws SQLException {
        txReturn(connection -> {
            work.run(connection);
            return null;
        });
    }

    private <T> T txReturn(JournalFunktion<T> work) throws SQLException {
        Connection connection = pool.getConnection();
        try {
            connection.setAutoCommit(false);
            T result = work.apply(connection);
            connection.commit();
            return result;
        } catch (SQLException e) {
            try {
                connection.rollback();
            } catch (SQLException ignored) {
                // nichts zu holen; der Pool verwirft die Verbindung bei Bedarf
            }
            throw e;
        } finally {
            pool.release(connection);
        }
    }

    private int execUpdate(Connection connection, String sql, Binder binder) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            binder.bind(stmt);
            return stmt.executeUpdate();
        }
    }

    @FunctionalInterface
    private interface Binder {
        void bind(PreparedStatement stmt) throws SQLException;
    }
}
