package de.allgemein.tx.audit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TxAuditLogTest {

    @BeforeEach
    @AfterEach
    void cleanup() {
        TxAuditLog.clearForTests();
        TxAuditLog.setAuditFilePath(null);
    }

    @Test
    void zeichnetTxEreignisseImRingpufferAuf() {
        TxAuditLog.recordTxCommit("tx-1", 1, 45, "Test-Commit");
        TxAuditLog.recordTxRetry("tx-2", 1, "Deadlock ORA-00060");
        TxAuditLog.recordTxRollback("tx-2", 2, 80, "Timeout");

        List<TxAuditEvent> events = TxAuditLog.getRecentEvents(10);
        assertEquals(3, events.size());

        // Neueste zuerst
        assertEquals("ROLLBACK", events.get(0).eventType());
        assertEquals("RETRY", events.get(1).eventType());
        assertEquals("COMMIT", events.get(2).eventType());
        assertTrue(TxAuditLog.getRecentEvents(0).isEmpty());
        assertTrue(TxAuditLog.getRecentEvents(-1).isEmpty());
    }

    @Test
    void threadNameKannKeineZweiteAuditzeileEinschleusen() {
        String original = Thread.currentThread().getName();
        try {
            Thread.currentThread().setName("worker\nTX.COMMIT id=gefälscht");
            TxAuditLog.recordTxCommit("echt", 1, 1, null);
            assertFalse(TxAuditLog.getRecentEvents(1).getFirst().toLogLine().contains("\n"));
        } finally {
            Thread.currentThread().setName(original);
        }
    }

    @Test
    void benachrichtigtRegistrierteListener() {
        AtomicInteger count = new AtomicInteger();
        TxAuditListener listener = event -> count.incrementAndGet();

        TxAuditLog.addListener(listener);
        try {
            TxAuditLog.recordSagaStart("saga-1", "AntragsProzess", "p1");
            TxAuditLog.recordSagaStep("saga-1", 0, "BedarfPruefen");
            TxAuditLog.recordSagaCompleted("saga-1", "AntragsProzess");
            assertEquals(3, count.get());
        } finally {
            TxAuditLog.removeListener(listener);
        }
    }

    @Test
    void auditZeileNimmtKeineFremdenZeilenumbruecheAuf(@TempDir Path tempDir) throws IOException {
        Path logFile = tempDir.resolve("audit.log");
        TxAuditLog.setAuditFilePath(logFile);

        TxAuditLog.recordSagaFailed("saga-x", "Schritt\n2025-01-01 TX.COMMIT id=gefaelscht \"x\"");

        List<String> zeilen = Files.readAllLines(logFile);
        assertEquals(1, zeilen.size(), "ein Ereignis = genau eine Zeile (Log-Injection ausgeschlossen)");
        assertFalse(zeilen.get(0).contains("\n"));
    }

    @Test
    void fehlerBeschreibungEnthaeltKeineMeldungMitSozialdaten() {
        Exception fehler = new IllegalStateException("IBAN DE89370400440532013000 von Max Mustermann");
        String text = TxAuditLog.fehlerBeschreibung(fehler);
        assertTrue(text.contains("IllegalStateException"));
        assertFalse(text.contains("Mustermann"));
        assertFalse(text.contains("DE89"));
    }

    @Test
    void schreibtInAuditDatei(@TempDir Path tempDir) throws IOException {
        Path logFile = tempDir.resolve("audit.log");
        TxAuditLog.setAuditFilePath(logFile);

        TxAuditLog.recordTxCommit("tx-file-1", 1, 15, "Datei-Test");
        TxAuditLog.recordSagaFailed("saga-file-1", "Schritt 2 fehlgeschlagen");

        assertTrue(Files.exists(logFile));
        List<String> zeilen = Files.readAllLines(logFile);
        assertEquals(2, zeilen.size());
        assertTrue(zeilen.get(0).contains("TX.COMMIT"));
        assertTrue(zeilen.get(0).contains("tx-file-1"));
        assertTrue(zeilen.get(1).contains("SAGA.FAILED"));
        assertTrue(zeilen.get(1).contains("saga-file-1"));
    }
}
