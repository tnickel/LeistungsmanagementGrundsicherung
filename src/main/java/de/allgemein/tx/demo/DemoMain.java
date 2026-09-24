package de.allgemein.tx.demo;

import de.allgemein.tx.SimpleConnectionPool;
import de.allgemein.tx.TaskController;
import de.allgemein.tx.TaskHandle;
import de.allgemein.tx.TxException;
import de.allgemein.tx.TxManager;
import de.allgemein.tx.TxOptions;
import de.allgemein.tx.saga.SagaDefinition;
import de.allgemein.tx.saga.SagaEngine;
import de.allgemein.tx.saga.SagaJournal;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Beispiel aus der Sozialleistungsverarbeitung: Antrag → Bescheid → Auszahlung als Saga,
 * dazu parallele Tasks über die Task-Steuerung. Verbindung über die Umgebungsvariablen
 * TX_URL, TX_USER, TX_PASS konfigurierbar; Schema siehe ddl/oracle_schema.sql.
 */
public final class DemoMain {

    public static void main(String[] args) throws Exception {
        // JDBC-Treiber explizit laden: im In-Prozess-Lauf (mvn exec:java) hat
        // DriverManager sonst keinen Zugriff auf den Treiber des Projekt-Klassenpfads.
        Class.forName("oracle.jdbc.OracleDriver");
        String url = env("TX_URL", "jdbc:oracle:thin:@//localhost:1521/XEPDB1");
        String user = env("TX_USER", "tx_demo");
        String password = env("TX_PASS", "tx_demo");

        try (SimpleConnectionPool pool = new SimpleConnectionPool(url, user, password, 8)) {
            TxManager txManager = new TxManager(pool);
            SagaJournal journal = new SagaJournal(pool);
            SagaEngine sagas = new SagaEngine(txManager, journal);

            SagaDefinition antragsprozess = AntragsDemoProzess.definition();

            System.out.println("Wiederanlauf: " + sagas.recover(antragsprozess) + " halbfertige Saga(s) beendet");

            System.out.println("\n=== 1. Erfolgreiche Saga ===");
            String sagaId = sagas.run(antragsprozess, "Meier;199.90");
            System.out.println("Saga " + sagaId + " abgeschlossen:");
            dumpAntrag(url, user, password, "Meier");

            System.out.println("\n=== 2. Fehlgeschlagene Saga mit Kompensation ===");
            try {
                sagas.run(antragsprozess, "Schulz;999999.99");
            } catch (TxException e) {
                System.out.println("Erwarteter Abbruch: " + e.getMessage());
            }
            System.out.println("Bestand danach:");
            dumpAntrag(url, user, password, "Schulz");

            System.out.println("\n=== 3. Parallele Tasks über die Task-Steuerung ===");
            try (TaskController tasks = new TaskController(txManager, 4, 100)) {
                List<TaskHandle<String>> handles = new ArrayList<>();
                for (int i = 1; i <= 6; i++) {
                    final int n = i;
                    handles.add(tasks.submit("Sachbearbeitung-" + n, TxOptions.DEFAULT, ctx -> {
                        ctx.checkDeadline();
                        Thread.sleep(ThreadLocalRandom.current().nextLong(50, 200));
                        return "Ergebnis von Task " + n;
                    }));
                }
                for (TaskHandle<String> handle : handles) {
                    System.out.println("  " + handle.result());
                }
                System.out.println("Statistik: " + tasks.stats());
            }
        }
    }

    private static void dumpAntrag(String url, String user, String password, String name) {
        try (Connection c = DriverManager.getConnection(url, user, password);
             PreparedStatement stmt = c.prepareStatement(
                     "SELECT ID, NAME, STATUS, BETRAG FROM TX_DEMO_ANTRAG WHERE NAME = ?")) {
            stmt.setString(1, name);
            try (ResultSet rs = stmt.executeQuery()) {
                boolean found = false;
                while (rs.next()) {
                    System.out.println("  Antrag " + rs.getLong(1) + ": " + rs.getString(2)
                            + ", Status=" + rs.getString(3) + ", Betrag=" + rs.getBigDecimal(4));
                    found = true;
                }
                if (!found) {
                    System.out.println("  Kein Antrag für '" + name + "' vorhanden (vollständig kompensiert)");
                }
            }
        } catch (SQLException e) {
            System.out.println("  (Abfrage fehlgeschlagen: " + e.getMessage() + ")");
        }
    }

    private static String env(String key, String defaultValue) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
