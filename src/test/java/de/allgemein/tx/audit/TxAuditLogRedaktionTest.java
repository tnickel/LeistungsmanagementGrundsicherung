package de.allgemein.tx.audit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Datenschutz-Regression des Audit-Logs (Codereview CR-001): Saga-Payloads
 * enthalten Sozialdaten (Name, IBAN, Einkommen) und dürfen weder in den
 * Ringpuffer noch in die Audit-Datei gelangen (§ 35 SGB I / Art. 9 DSGVO).
 */
class TxAuditLogRedaktionTest {

    @Test
    void sagaStartOhneSozialdatenImRingpuffer(@TempDir Path tempDir) throws Exception {
        TxAuditLog.clearForTests();
        TxAuditLog.setAuditFilePath(tempDir.resolve("audit.log"));
        String payload = "{\"aktenzeichen\":\"BG-1\",\"hauptpersonName\":\"Max Mustermann\","
                + "\"iban\":\"DE89370400440532013000\",\"einkommenRente\":1200}";
        TxAuditLog.recordSagaStart("saga-1", "SGB_XII_ANTRAGSVERARBEITUNG", payload);
        List<TxAuditEvent> events = TxAuditLog.getRecentEvents(10);
        assertFalse(events.isEmpty());
        TxAuditEvent event = events.get(0);
        assertFalse(String.valueOf(event.details()).contains("DE89"), "IBAN darf nicht im Audit stehen");
        assertFalse(String.valueOf(event.details()).contains("Mustermann"), "Name darf nicht im Audit stehen");
        assertFalse(String.valueOf(event.details()).contains("1200"), "Einkommen darf nicht im Audit stehen");
        assertTrue(String.valueOf(event.details()).contains("payloadLaenge="), "Stattdessen Längenangabe");
        TxAuditLog.setAuditFilePath(null);
    }

    @Test
    void sagaStartOhneSozialdatenInDerDatei(@TempDir Path tempDir) throws Exception {
        TxAuditLog.clearForTests();
        Path datei = tempDir.resolve("audit_datei.log");
        TxAuditLog.setAuditFilePath(datei);
        TxAuditLog.recordSagaStart("saga-2", "SGB_XII_ANTRAGSVERARBEITUNG",
                "{\"hauptpersonName\":\"Erika Musterfrau\",\"iban\":\"DE89370400440532013000\"}");
        String inhalt = Files.readString(datei);
        assertFalse(inhalt.contains("DE89"));
        assertFalse(inhalt.contains("Musterfrau"));
        TxAuditLog.setAuditFilePath(null);
    }

    @Test
    void ringpufferWirdAufTausendEintraegeGetrimmt(@TempDir Path tempDir) {
        TxAuditLog.clearForTests();
        TxAuditLog.setAuditFilePath(tempDir.resolve("audit_trim.log"));
        for (int i = 0; i < 1250; i++) {
            TxAuditLog.recordTxCommit("tx-" + i, 1, 1, "test");
        }
        List<TxAuditEvent> events = TxAuditLog.getRecentEvents(2000);
        assertTrue(events.size() <= 1000, "Ringpuffer höchstens 1000 Einträge, erhalten: " + events.size());
        TxAuditLog.setAuditFilePath(null);
    }
}
