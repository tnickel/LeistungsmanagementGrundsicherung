/**
 * Revisionssicheres Transaktionsprotokoll (Audit-Log).
 *
 * <p>Dieses Paket schreibt für jede Transaktion mit, was technisch passiert ist:
 * Commit, Rollback, Wiederholversuch oder ein unbekannter Ausgang. Das ist keine
 * Fachhistorie, sondern ein technisches Protokoll für Prüfungen und Analyse.</p>
 *
 * <ul>
 *   <li><b>{@link de.allgemein.tx.audit.TxAuditLog}</b> — der Schreiber. Der
 *       {@code TxManager} ruft ihn bei jedem Ende einer Transaktion auf. Kann in
 *       eine Datei schreiben (mit Rotation) und behält Einträge im Speicher als
 *       letzten Fallback.</li>
 *   <li><b>{@link de.allgemein.tx.audit.TxAuditEvent}</b> — der einzelne
 *       Protokolleintrag mit Zeit, Transaktionskennung, Versuch und Ergebnis.</li>
 *   <li><b>{@link de.allgemein.tx.audit.TxAuditListener}</b> — der Anschluss für
 *       eigene Zuhörer, die Ereignisse weiterverarbeiten wollen.</li>
 * </ul>
 */
package de.allgemein.tx.audit;
