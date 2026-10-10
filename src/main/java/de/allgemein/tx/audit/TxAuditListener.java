package de.allgemein.tx.audit;

/**
 * Schnittstelle für Empfänger von Audit-Ereignissen (z. B. File-Logger, SIEM, Ring-Puffer).
 */
@FunctionalInterface
public interface TxAuditListener {
    void onEvent(TxAuditEvent event);
}
