package de.allgemein.lmg.db;

import de.allgemein.tx.SimpleConnectionPool;
import de.allgemein.tx.TxManager;
import de.allgemein.tx.saga.SagaDefinition;
import de.allgemein.tx.saga.SagaEngine;
import de.allgemein.tx.saga.SagaJournal;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Helfer für den echten Prozess-Kill-Test (Testlücke L-13): Diese Klasse wird
 * als EIGENER JVM-Prozess gestartet, beginnt eine Zwei-Schritt-Saga und schläft
 * dann im zweiten Schritt — der Eltern-Prozess tötet sie hart mitten im
 * IN_FLIGHT-Zustand (keinfinally, kein Cleanup, wie ein Stromausfall).
 *
 * <pre>
 *   java -cp &lt;test-classpath&gt; de.allgemein.lmg.db.ProzessKillHelfer AKTENZEICHEN SAGA_TYPE
 *   Datenbankzugang ausschließlich über geerbte TX_URL/TX_USER/TX_PASS.
 * </pre>
 */
public final class ProzessKillHelfer {

    private ProzessKillHelfer() {
    }

    /** Dieselbe Definition wie der Kind-Prozess — der Eltern-Test recovert damit. */
    public static SagaDefinition definition(String sagaType) {
        return SagaDefinition.of(sagaType)
                .step("vorgang anlegen", ProzessKillHelfer::vorgangAnlegen,
                        ProzessKillHelfer::vorgangHistorisieren)
                .step("langlaufend", ProzessKillHelfer::langlaufend,
                        ProzessKillHelfer::langlaufendKompensieren)
                .build();
    }

    static void vorgangAnlegen(Connection c, String payload, String sagaId) throws SQLException {
        try (PreparedStatement stmt = c.prepareStatement(
                "INSERT INTO BEDARFSGEMEINSCHAFT (ID, AKTENZEICHEN, HAUPTPERSON_NAME, STATUS_AKTIV, "
                        + "BUDGET_REST) VALUES (SEQ_BG.NEXTVAL, ?, 'Kill Test', 1, 900.00)")) {
            stmt.setString(1, payload);
            stmt.executeUpdate();
        }
    }

    static void vorgangHistorisieren(Connection c, String payload, String sagaId) throws SQLException {
        try (PreparedStatement stmt = c.prepareStatement(
                "UPDATE BEDARFSGEMEINSCHAFT SET STATUS_AKTIV = 0 WHERE AKTENZEICHEN = ? AND STATUS_AKTIV = 1")) {
            stmt.setString(1, payload);
            stmt.executeUpdate();
        }
    }

    static void langlaufend(Connection c, String payload, String sagaId) throws SQLException {
        // IN_FLIGHT ist geschrieben (Write-Ahead), wenn wir hier ankommen. Der
        // Eltern-Prozess killt uns während dieser Wartezeit hart.
        try {
            Thread.sleep(120_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        throw new SQLException("langlaufender Schritt beendet (sollte nie erreicht werden)");
    }

    static void langlaufendKompensieren(Connection c, String payload, String sagaId) {
        // dokumentierende Kompensation ohne Fachwirkung
    }

    public static void main(String[] args) throws Exception {
        if(args.length!=2) throw new IllegalArgumentException("Aktenzeichen und laufisolierter Saga-Typ erforderlich");
        Class.forName("oracle.jdbc.OracleDriver");
        try (SimpleConnectionPool pool = new SimpleConnectionPool(System.getenv("TX_URL"),System.getenv("TX_USER"),
                System.getenv("TX_PASS"),2,java.time.Duration.ofSeconds(5),5000,10000)) {
            SagaEngine engine = new SagaEngine(new TxManager(pool), new SagaJournal(pool));
            System.out.println("KILL-START " + args[0]);
            System.out.flush();
            engine.run(definition(args[1]),args[0]);
        }
    }
}
