/**
 * Die Transaktionssteuerung (Ersatz für einen TP-Monitor wie CICS).
 *
 * <p>Dieses Paket ist die technische Steuerungsschicht zwischen Fachschicht und
 * Oracle. Oracle selbst liefert den transaktionalen Kern (ACID, Sperrverwaltung,
 * Redo-Logging, Recovery). Gesteuert wird hier nur das Drumherum:</p>
 *
 * <ul>
 *   <li><b>{@link de.allgemein.tx.TxManager}</b> — die Schaltstelle: leiht eine
 *       Verbindung, lässt die Facharbeit laufen und speichert am Ende alles oder
 *       nimmt alles zurück. Eine Aktion ist unteilbar: entweder alles wird
 *       gespeichert oder nichts.</li>
 *   <li><b>{@link de.allgemein.tx.TaskController}</b> — die Auftragsteuerung:
 *       nimmt Aufträge an, lässt nur eine begrenzte Zahl gleichzeitig laufen,
 *       bricht zu lange laufende Aufträge ab und zählt, was passiert ist.</li>
 *   <li><b>{@link de.allgemein.tx.ConnectionPool}</b> — der Rahmen für die
 *       Verbindungspools. {@link de.allgemein.tx.SimpleConnectionPool} für Demo
 *       und Übergang, {@link de.allgemein.tx.UcpConnectionPool} für den
 *       Oracle-Betrieb. Beide verhalten sich für die Fachschicht gleich.</li>
 *   <li><b>{@link de.allgemein.tx.RetryPolicy}</b> — sagt, wann ein neuer Versuch
 *       erlaubt ist. Nur kurze Sperrkonflikte (ORA-00054, ORA-00060, ORA-08177)
 *       werden wiederholt; fachliche Fehler niemals.</li>
 *   <li><b>Fehlertypen</b> — {@link de.allgemein.tx.TxException} und ihre
 *       Sonderfälle. Wichtig ist die Regel „Ausgang unbekannt“: Fehlt die
 *       Bestätigung eines Speichers, darf nichts blind wiederholt werden,
 *       weil die Wirkung schon in der Datenbank stehen kann.</li>
 * </ul>
 *
 * <p>Ablauf einer Fachaktion: Die Fachschicht ruft {@code TxManager.execute}
 * mit einer Arbeitseinheit ({@link de.allgemein.tx.TxWork}) auf. Der Manager
 * leiht eine Verbindung, die Arbeit bekommt sie als
 * {@link de.allgemein.tx.TxContext} und arbeitet darin. Am Ende kommt Commit
 * oder Rollback, und die Verbindung geht zurück in den Pool. Läuft alles über
 * die Weboberfläche, kommt zusätzlich die Auftragsteuerung dazwischen: jede
 * HTTP-Anfrage ist ein Task mit Schlange, Zeitlimit-Wachhund und Statistik.</p>
 *
 * <p>Unterpakete: {@link de.allgemein.tx.saga} für langlaufende Vorgänge mit
 * Kompensation, {@link de.allgemein.tx.audit} für das revisionssichere
 * Transaktionsprotokoll und {@link de.allgemein.tx.monitoring} für JMX-Monitoring.</p>
 */
package de.allgemein.tx;
