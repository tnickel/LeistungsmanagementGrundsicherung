package de.allgemein.lmg.web;

import de.allgemein.lmg.saga.AntragsSagaProzess;
import de.allgemein.lmg.service.Systemdiagnose;
import de.allgemein.tx.SimpleConnectionPool;
import de.allgemein.tx.TxManager;
import de.allgemein.tx.demo.AntragsDemoProzess;
import de.allgemein.tx.saga.SagaDefinition;
import de.allgemein.tx.saga.SagaEngine;
import de.allgemein.tx.saga.SagaJournal;
import de.allgemein.tx.saga.SagaState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Echter Produkt-JAR/Oracle-Journalzustand nach start-Commit und vor erstem IN_FLIGHT. */
@EnabledIfEnvironmentVariable(named = "TX_URL", matches = ".+")
class ProduktPendingRecoveryDbTest {
    @Test
    void pendingRecoveryIstTerminalPayloadfreiIdempotentUndPassiertDenStartschutz() throws Exception {
        String id = UUID.randomUUID().toString(), type = "SWEET_PENDING_" + UUID.randomUUID();
        var effects = new AtomicInteger();
        SagaDefinition definition = SagaDefinition.of(type)
                .step("eins", (c, p, saga) -> effects.incrementAndGet(), (c, p, saga) -> effects.incrementAndGet())
                .step("zwei", (c, p, saga) -> effects.incrementAndGet(), (c, p, saga) -> effects.incrementAndGet()).build();
        try (var pool = new SimpleConnectionPool(System.getenv("TX_URL"), System.getenv("TX_USER"),
                System.getenv("TX_PASS"), 3, Duration.ofSeconds(5), 5_000, 10_000)) {
            var journal = new SagaJournal(pool);
            var engine = new SagaEngine(new TxManager(pool), journal);
            try {
                journal.start(id, type, "{\"synthetisch\":true}", definition.steps().stream().map(s -> s.name()).toList());
                // Auch bereits vom alten Recovery fälschlich FAILED markierte Köpfe reparieren.
                for (SagaState initial : new SagaState[]{SagaState.RUNNING, SagaState.FAILED}) {
                    if (initial == SagaState.FAILED) {
                        resetOwnHead(id, type);
                        journal.markState(id, SagaState.FAILED);
                    }
                    assertOwnState(id, "PENDING", initial.name(), false);
                    var report = engine.recoverWithReport(definition);
                    assertEquals(1, report.compensated());
                    assertEquals(0, report.fehlerhaft());
                    assertEquals(0, effects.get(), "weder Aktion noch Kompensation ausführen");
                    assertOwnState(id, "PENDING", "COMPENSATED", true);
                    assertEquals(1, startupGuard(report));
                    var second = engine.recoverWithReport(definition);
                    assertEquals(0, second.behandelt());
                    assertEquals(0, startupGuard(second));
                }
            } finally { cleanupOwn(id, type); }
        }
    }

    private static int startupGuard(SagaEngine.RecoveryReport report) {
        // Der isolierte Typ verhindert Recovery fremder Sagas. Den resultierenden
        // echten RecoveryReport prüft derselbe Guard wie beim Produktstart.
        return LmgServerMain.requireSuccessfulRecovery(new Systemdiagnose.WiederanlaufErgebnis(Map.of(
                AntragsSagaProzess.definition().type(), report,
                AntragsDemoProzess.definition().type(), new SagaEngine.RecoveryReport(0, 0, 0, 0))));
    }
    private static Connection connect() throws Exception {
        var properties = new java.util.Properties();
        properties.setProperty("user", System.getenv("TX_USER"));
        properties.setProperty("password", System.getenv("TX_PASS"));
        properties.setProperty("oracle.net.CONNECT_TIMEOUT", "5000");
        properties.setProperty("oracle.jdbc.ReadTimeout", "10000");
        return DriverManager.getConnection(System.getenv("TX_URL"), properties);
    }
    private static void assertOwnState(String id, String step, String head, boolean payloadDeleted) throws Exception {
        try (var c = connect(); var s = c.prepareStatement("SELECT STATE, PAYLOAD FROM TX_SAGA WHERE SAGA_ID = ?")) {
            s.setString(1, id);
            try (var rows = s.executeQuery()) {
                assertTrue(rows.next());assertEquals(head, rows.getString(1));
                assertEquals(payloadDeleted, rows.getString(2) == null);assertFalse(rows.next());
            }
            try (var p = c.prepareStatement("SELECT IDX, STATE FROM TX_SAGA_STEP WHERE SAGA_ID = ? ORDER BY IDX")) {
                p.setString(1, id);
                try (var rows = p.executeQuery()) {
                    for (int i = 0; i < 2; i++) {assertTrue(rows.next());assertEquals(i, rows.getInt(1));assertEquals(step, rows.getString(2));}
                    assertFalse(rows.next());
                }
            }
        }
    }
    private static void resetOwnHead(String id, String type) throws Exception {
        try (var c = connect(); var p = c.prepareStatement(
                "UPDATE TX_SAGA SET STATE = 'RUNNING', PAYLOAD = '{}' WHERE SAGA_ID = ? AND SAGA_TYPE = ?")) {
            p.setString(1, id);p.setString(2, type);assertEquals(1, p.executeUpdate());
        }
    }
    private static void cleanupOwn(String id, String type) throws Exception {
        try (var c = connect()) {
            c.setAutoCommit(false);
            try (var p = c.prepareStatement("DELETE FROM TX_SAGA_STEP WHERE SAGA_ID IN (SELECT SAGA_ID FROM TX_SAGA WHERE SAGA_ID = ? AND SAGA_TYPE = ?)")) {
                p.setString(1, id);p.setString(2, type);p.executeUpdate();
            }
            try (var p = c.prepareStatement("DELETE FROM TX_SAGA WHERE SAGA_ID = ? AND SAGA_TYPE = ?")) {
                p.setString(1, id);p.setString(2, type);p.executeUpdate();
            }
            c.commit();
        }
    }
}
