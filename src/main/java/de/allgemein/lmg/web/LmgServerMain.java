package de.allgemein.lmg.web;

import de.allgemein.lmg.service.LeistungsService;
import de.allgemein.lmg.service.SozialLeistungsService;
import de.allgemein.lmg.service.Systemdiagnose;
import de.allgemein.lmg.web.Benutzerverwaltung;
import de.allgemein.tx.ConnectionPool;
import de.allgemein.tx.SimpleConnectionPool;
import de.allgemein.tx.TaskController;
import de.allgemein.tx.TxManager;
import de.allgemein.tx.UcpConnectionPool;
import de.allgemein.tx.saga.SagaEngine;
import de.allgemein.tx.saga.SagaJournal;

import java.time.Instant;

/**
 * Startpunkt des Leistungsmanagements (SDN.sozial LMG): baut Pool,
 * Transaktionssteuerung und Fachdienste auf und startet die Weboberfläche.
 *
 * <p>Verbindung über die Umgebungsvariablen TX_URL, TX_USER, TX_PASS (wie beim
 * DemoMain); Port optional über LMG_PORT (Standard 8080). Vorher die Skripte
 * ddl/oracle_schema.sql und ddl/lmg_schema.sql (+ lmg_seed.sql) einspielen.
 * Aufruf: {@code mvn compile exec:java -Dexec.mainClass=de.allgemein.lmg.web.LmgServerMain}
 * — danach <a href="http://localhost:8080">http://localhost:8080</a>.</p>
 */
public final class LmgServerMain {

    public static void main(String[] args) throws Exception {
        // JDBC-Treiber explizit laden: im In-Prozess-Lauf (mvn exec:java) hat
        // DriverManager sonst keinen Zugriff auf den Treiber des Projekt-Klassenpfads.
        Class.forName("oracle.jdbc.OracleDriver");

        de.allgemein.lmg.config.LmgConfig config = de.allgemein.lmg.config.LmgConfig.load();
        if (config.auditFile() != null && !config.auditFile().isBlank()) {
            de.allgemein.tx.audit.TxAuditLog.setAuditFilePath(java.nio.file.Paths.get(config.auditFile()));
        }

        try (ConnectionPool pool = erzeugePool(config.poolType(), config.url(), config.user(), config.password(), config.poolSize())) {
            var poolMbean = de.allgemein.tx.monitoring.TxMonitoring.registerConnectionPool(pool);
            TxManager txManager = new TxManager(pool);
            try (TaskController tasks = new TaskController(txManager, config.maxTasks(), config.queueCapacity())) {
                var taskMbean = tasks.registerJmx();
                LeistungsService service = new SozialLeistungsService(txManager);
                SagaEngine sagas = new SagaEngine(txManager, new SagaJournal(pool));
                Systemdiagnose diagnose = new Systemdiagnose(txManager, tasks, sagas, Instant.now());
                Benutzerverwaltung benutzerverwaltung = Benutzerverwaltung.ausUmgebung();
                try (LmgServer server = new LmgServer(tasks, service, diagnose, sagas, benutzerverwaltung, config.port())) {
                    server.start();
                    System.out.println("SDN.sozial — Leistungsmanagement Grundsicherung");
                    System.out.println("Oberfläche: http://localhost:" + config.port() + "/");
                    System.out.println("Verbindungspool: " + pool.getClass().getSimpleName() + " (Größe: " + config.poolSize() + ")");
                    System.out.println("JMX-Monitoring: aktiv (" + taskMbean + ", " + poolMbean + ")");
                    if (config.auditFile() != null && !config.auditFile().isBlank()) {
                        System.out.println("Audit-Log: aktiv (" + config.auditFile() + ")");
                    }
                    System.out.println("Anmeldung: " + benutzerverwaltung.anzahlKonten() + " Konto(s)"
                            + (System.getenv("LMG_BENUTZER") == null && config.benutzer() == null
                                    ? " — STANDARD-DEMO-KONTEN aktiv (admin-01/admin, sb-01/sb-01, "
                                      + "sb-02/sb-02)! Für echte Passwörter LMG_BENUTZER setzen."
                                    : " (aus Konfiguration)"));
                    int wiederanlauf = diagnose.wiederanlauf();
                    System.out.println("Wiederanlauf beim Start: " + wiederanlauf
                            + " halbfertige Saga(s) beendet");
                    System.out.println("Statistik der Task-Steuerung: " + tasks.stats());
                    Thread.currentThread().join();
                } finally {
                    de.allgemein.tx.monitoring.TxMonitoring.unregister(poolMbean);
                }
            }
        }
    }

    private static ConnectionPool erzeugePool(String typ, String url, String user, String pass, int groesse) throws Exception {
        if ("simple".equalsIgnoreCase(typ)) {
            return new SimpleConnectionPool(url, user, pass, groesse);
        }
        return new UcpConnectionPool(url, user, pass, groesse);
    }

    private static String env(String key, String defaultValue) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
