package de.allgemein.tx.audit;

/**
 * Kurz gesagt: Der Rahmen (Schnittstelle) für einen Logbuch-Empfänger.
 * Wer Audit-Einträge haben möchte — Datei, Bildschirm, Überwachungssystem —
 * meldet sich mit dieser einen Methode an.
 *
 * Schnittstelle für Empfänger von Audit-Ereignissen (z. B. File-Logger, SIEM, Ring-Puffer).
 */
@FunctionalInterface
public interface TxAuditListener {
    void onEvent(TxAuditEvent event);
}