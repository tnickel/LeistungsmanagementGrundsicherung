package de.allgemein.lmg.web;

import de.allgemein.lmg.service.LeistungsService;
import de.allgemein.lmg.service.SozialLeistungsService;
import de.allgemein.lmg.service.Systemdiagnose;
import de.allgemein.tx.ConnectionPool;
import de.allgemein.tx.SimpleConnectionPool;
import de.allgemein.tx.TaskController;
import de.allgemein.tx.TxManager;
import de.allgemein.tx.UcpConnectionPool;
import de.allgemein.tx.saga.SagaEngine;
import de.allgemein.tx.saga.SagaJournal;

import java.net.InetAddress;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;

/**
 * Kurz gesagt: Das ist der Startknopf der Anwendung.
 * Diese Klasse wird gestartet (main-Methode) und baut alles zusammen:
 * Konfiguration lesen, Verbindungspool aufbauen, Fachdienste erzeugen,
 * Weboberfläche starten. Danach läuft der Server und wartet auf Anfragen.
 *
 * <p>Verbindung über die Umgebungsvariablen TX_URL, TX_USER, TX_PASS (wie beim
 * DemoMain); Port optional über LMG_PORT (Standard 8080), Bind-Adresse über
 * LMG_HOST (Standard 127.0.0.1 — bewusst KEIN Lauschen auf allen Interfaces;
 * Demo-Standardkonten werden bei Nicht-Loopback-Bindung verweigert). Vorher die
 * Skripte ddl/oracle_schema.sql und ddl/lmg_schema.sql (+ lmg_seed.sql) einspielen.
 * Aufruf: {@code mvn compile exec:java -Dexec.mainClass=de.allgemein.lmg.web.LmgServerMain}
 * — danach <a href="http://localhost:8080">http://localhost:8080</a>.</p>
 */
public final class LmgServerMain {

    public static void main(String[] args) throws Exception {
        HttpServerLimits.configure();
        // JDBC-Treiber explizit laden: im In-Prozess-Lauf (mvn exec:java) hat
        // DriverManager sonst keinen Zugriff auf den Treiber des Projekt-Klassenpfads.
        Class.forName("oracle.jdbc.OracleDriver");

        de.allgemein.lmg.config.LmgConfig config = de.allgemein.lmg.config.LmgConfig.load();
        if (config.auditFile() != null && !config.auditFile().isBlank()) {
            de.allgemein.tx.audit.TxAuditLog.setAuditFilePath(java.nio.file.Paths.get(config.auditFile()));
        }
        InetAddress bindAdresse = InetAddress.getByName(config.host());
        boolean demoKonten = config.benutzer() == null || config.benutzer().isBlank();
        if (demoKonten && !bindAdresse.isLoopbackAddress()) {
            // CR-004: Die bekannten Demo-Konten dürfen niemals an einem Netzwerk-Interface
            // mit Sozialdaten antworten — bewusster harter Abbruch statt stiller Betrieb.
            throw new IllegalStateException("Standard-Demo-Konten sind bei Nicht-Loopback-Bindung ("
                    + config.host() + ") verweigert: bitte lmg.benutzer/LMG_BENUTZER mit echten "
                    + "Konten setzen oder lmg.host auf 127.0.0.1 belassen.");
        }

        ConnectionPool pool = null;
        TaskController tasks = null;
        LmgServer server = null;
        var jmx = new java.util.concurrent.atomic.AtomicReference<javax.management.ObjectName>();
        var poolJmx = new java.util.concurrent.atomic.AtomicReference<javax.management.ObjectName>();
        CountDownLatch beendet = new CountDownLatch(1);
        try {
            pool = erzeugePool(config);
            if (config.poolSize() < 3 * config.maxTasks()) {
                // Ein HTTP-Saga-Task hält Fachtx, Live-Claim und Saga-Journalverbindung.
                System.err.println("WARNUNG: lmg.poolSize (" + config.poolSize() + ") < 3 x lmg.maxTasks ("
                        + config.maxTasks() + ") — unter Last drohen Pool-Timeouts/Verklemmungen.");
            }
            TxManager txManager = new TxManager(pool);
            tasks = new TaskController(txManager, config.maxTasks(), config.queueCapacity());
            // KdU-Parameter (§ 42a SGB XII) und Rechtsbehelfsbelehrung (§ 36 SGB X)
            // aus der Konfiguration; Vier-Augen wird serverseitig erzwungen
            LeistungsService service = new SozialLeistungsService(
                    txManager, config.kduParameter(), config.rechtsbehelf());
            de.allgemein.lmg.saga.AntragsSagaProzess.setzeKduParameter(config.kduParameter());
            SagaEngine sagas = new SagaEngine(txManager, new SagaJournal(pool));
            Systemdiagnose diagnose = new Systemdiagnose(txManager, tasks, sagas, Instant.now());
            // Benutzerkonten über die volle Konfigurationskaskade:
            // System-Property lmg.benutzer > LMG_BENUTZER > lmg.properties > Demo-Konten
            Benutzerverwaltung benutzerverwaltung = new Benutzerverwaltung(
                    demoKonten ? Benutzerverwaltung.STANDARD_KONTEN : config.benutzer(),
                    java.time.Duration.ofMinutes(config.sessionTimeoutMinuten()));
            // Wiederanlauf VOR dem server.start() (CR-050): keine neuen Anfragen, während
            // halbfertige Sagas kompensiert werden.
            int wiederanlauf = requireSuccessfulRecovery(diagnose.wiederanlaufDetails());
            // Ab jetzt laufen Sagas im Betrieb: der Admin-Wiederanlauf darf nur "verwaiste" übernehmen
            sagas.setzeMindestInaktivitaet(java.time.Duration.ofMinutes(5));
            // Erst nach erfolgreichem Recovery einen HTTP-Listener/Executor anlegen.
            server = new LmgServer(tasks, service, diagnose, sagas, benutzerverwaltung, bindAdresse, config.port());

            // Geordneter Shutdown (CR-017): Drain-Logik des TaskController läuft jetzt auch
            // bei SIGTERM/SIGINT/Ctrl+C — vorher war sie über den nie erreichten Ende-von-main
            // faktisch toter Code.
            final LmgServer serverFuerHook = server;
            final TaskController tasksFuerHook = tasks;
            final ConnectionPool poolFuerHook = pool;
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    serverFuerHook.close();
                } catch (RuntimeException e) {
                    // Server stoppen darf den Shutdown nicht abbrechen
                }
                tasksFuerHook.close();
                poolFuerHook.close();
                de.allgemein.tx.audit.TxAuditLog.closeFile();
                beendet.countDown();
            }, "lmg-shutdown"));

            poolJmx.set(de.allgemein.tx.monitoring.TxMonitoring.registerConnectionPool(pool));
            jmx.set(tasks.registerJmx());
            server.start();
            System.out.println("SDN.Leistung — Leistungsmanagement Grundsicherung (v0.5.8)");
            System.out.println("Oberfläche: http://" + config.host() + ":" + config.port() + "/");
            System.out.println("Verbindungspool: " + pool.getClass().getSimpleName()
                    + " (Größe: " + config.poolSize() + ")");
            if (jmx.get() != null && poolJmx.get() != null) {
                System.out.println("JMX-Monitoring: aktiv (" + jmx.get() + ", " + poolJmx.get() + ")");
            } else {
                System.out.println("JMX-Monitoring: NICHT registriert (Registrierung fehlgeschlagen — "
                        + "Details im Log)");
            }
            if (config.auditFile() != null && !config.auditFile().isBlank()) {
                System.out.println("Audit-Log: aktiv (" + config.auditFile() + ", Rotation bei 10 MB)");
            }
            System.out.println("Anmeldung: " + benutzerverwaltung.anzahlKonten() + " Konto(s)"
                    + (demoKonten
                            ? " — STANDARD-DEMO-KONTEN aktiv (admin-01/admin, sb-01/sb-01, "
                              + "sb-02/sb-02)! Für echte Passwörter lmg.benutzer/LMG_BENUTZER setzen."
                            : " (aus Konfiguration)"));
            System.out.println("Sitzungs-Idle-Timeout: " + config.sessionTimeoutMinuten() + " Minuten");
            System.out.println("Wiederanlauf beim Start: " + wiederanlauf
                    + " halbfertige Saga(s) beendet");
            System.out.println("Statistik der Task-Steuerung: " + tasks.stats());
            beendet.await();
        } finally {
            // Fehlerpfad vor dem Hook (oder fehlgeschlagener Hook): trotzdem geordnet schließen.
            if (server != null) {
                try {
                    server.close();
                } catch (RuntimeException ignoriert) {
                    // Shutdown bereits angestoßen
                }
            }
            if (tasks != null) {
                tasks.close();
            }
            if (pool != null) {
                pool.close();
            }
            de.allgemein.tx.audit.TxAuditLog.closeFile();
            if (poolJmx.get() != null) {
                de.allgemein.tx.monitoring.TxMonitoring.unregister(poolJmx.get());
            }
        }
    }

    private static ConnectionPool erzeugePool(de.allgemein.lmg.config.LmgConfig config) throws Exception {
        if ("simple".equalsIgnoreCase(config.poolType())) {
            return new SimpleConnectionPool(config.url(), config.user(), config.password(), config.poolSize(),
                    java.time.Duration.ofSeconds(30), config.dbConnectTimeoutMs(), config.dbReadTimeoutMs());
        }
        return new UcpConnectionPool(config.url(), config.user(), config.password(),
                Math.min(2, config.poolSize()), config.poolSize(),
                config.dbConnectTimeoutMs(), config.dbReadTimeoutMs());
    }

    /** Ein fehlgeschlagener Scan oder eine unvollständige Kompensation ist kein grüner Serverstart. */
    static int requireSuccessfulRecovery(Systemdiagnose.WiederanlaufErgebnis recovery) {
        var requiredTypes = java.util.Set.of(de.allgemein.lmg.saga.AntragsSagaProzess.definition().type(),
                de.allgemein.tx.demo.AntragsDemoProzess.definition().type());
        if (recovery == null || !recovery.jeTyp().keySet().containsAll(requiredTypes) || recovery.fehlerhaft() != 0) {
            throw new IllegalStateException("Wiederanlauf beim Start fehlgeschlagen oder unvollständig; "
                    + "Server bleibt gestoppt. Saga-Journal und technische Diagnose manuell prüfen");
        }
        return recovery.insgesamt();
    }
}