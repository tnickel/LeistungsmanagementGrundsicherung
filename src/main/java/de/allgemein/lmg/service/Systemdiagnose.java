package de.allgemein.lmg.service;

import de.allgemein.tx.TaskController;
import de.allgemein.tx.TaskStats;
import de.allgemein.tx.TxContext;
import de.allgemein.tx.TxException;
import de.allgemein.tx.TxManager;
import de.allgemein.tx.TxOptions;
import de.allgemein.tx.demo.AntragsDemoProzess;
import de.allgemein.tx.saga.SagaEngine;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;

/**
 * Systemdiagnose für den Adminbereich: prüft die Datenbank (Erreichbarkeit,
 * Produktkennung, Antwortzeit, Bestandszahlen), liest die Statistik der
 * Task-Steuerung und stößt bei Bedarf den Wiederanlauf halbfertiger Sagas an.
 * Die Prüfung läuft als kurze Transaktion mit hartem Zeitlimit und genau
 * einem Versuch — ein DB-Ausfall soll schnell und ohne Retry melden.
 */
public final class Systemdiagnose {

    private static final Duration PRUEF_TIMEOUT = Duration.ofSeconds(5);

    private final TxManager txManager;
    private final TaskController tasks;
    private final SagaEngine sagas;
    private final Instant serverStart;

    public Systemdiagnose(TxManager txManager, TaskController tasks, SagaEngine sagas, Instant serverStart) {
        this.txManager = txManager;
        this.tasks = tasks;
        this.sagas = sagas;
        this.serverStart = serverStart;
    }

    /** Führt den DB- und Komponenten-Check durch; DB-Probleme werden gemeldet, nicht geworfen. */
    public Systemstatus status() {
        long start = System.nanoTime();
        Systemstatus basis;
        try {
            basis = txManager.execute(TxOptions.DEFAULT
                    .withTimeout(PRUEF_TIMEOUT).withMaxAttempts(1), this::leseDbStatus);
        } catch (TxException e) {
            basis = new Systemstatus(
                    false, e.getMessage(), null, null, 0, null,
                    -1, -1, -1, -1, -1,
                    0, 0, 0, 0, 0, 0.0,
                    null, null, 0);
        }
        TaskStats stats = tasks.stats();
        return new Systemstatus(
                basis.dbErreichbar(), basis.dbFehler(), basis.dbProdukt(), basis.dbVersion(),
                verstrichen(start), basis.dbZeitpunkt(),
                basis.anzahlVorgaenge(), basis.anzahlSozialFaelle(), basis.anzahlZahlungen(),
                basis.anzahlOffeneSagas(), basis.anzahlAbgebrocheneSagas(),
                stats.active(), stats.completed(), stats.failed(), stats.timedOut(), stats.rejected(),
                stats.avgDurationMillis(),
                System.getProperty("java.version"),
                serverStart.toString(),
                Duration.between(serverStart, Instant.now()).toSeconds());
    }

    /** Wiederanlauf: beendet halbfertige Sagas der Demo-Antragsverarbeitung; liefert die Anzahl. */
    public int wiederanlauf() {
        return sagas.recover(AntragsDemoProzess.definition());
    }

    private Systemstatus leseDbStatus(TxContext ctx) throws Exception {
        java.sql.DatabaseMetaData meta = ctx.connection().getMetaData();
        String produkt = meta.getDatabaseProductName();
        String version = meta.getDatabaseProductVersion();
        String dbZeit = abfrage(ctx, "SELECT TO_CHAR(SYSTIMESTAMP, 'YYYY-MM-DD HH24:MI:SS') FROM DUAL");
        long vorgaenge = zahl(ctx, "SELECT COUNT(*) FROM BEDARFSGEMEINSCHAFT");
        long faelle = zahl(ctx, "SELECT COUNT(*) FROM SOZIAL_FALL");
        long zahlungen = zahl(ctx, "SELECT COUNT(*) FROM ZAHLUNG");
        long offeneSagas = zahl(ctx, "SELECT COUNT(*) FROM TX_SAGA "
                + "WHERE STATE IN ('RUNNING', 'COMPENSATING')");
        long abgebrocheneSagas = zahl(ctx, "SELECT COUNT(*) FROM TX_SAGA WHERE STATE = 'FAILED'");
        return new Systemstatus(true, null, produkt, version, 0, dbZeit,
                vorgaenge, faelle, zahlungen, offeneSagas, abgebrocheneSagas,
                0, 0, 0, 0, 0, 0.0,
                null, null, 0);
    }

    private String abfrage(TxContext ctx, String sql) throws SQLException {
        try (PreparedStatement stmt = ctx.connection().prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            rs.next();
            return rs.getString(1);
        }
    }

    private long zahl(TxContext ctx, String sql) throws SQLException {
        try (PreparedStatement stmt = ctx.connection().prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private static long verstrichen(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
