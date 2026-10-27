/**
 * Langlaufende Vorgänge als Saga mit Kompensation.
 *
 * <p>Eine normale Transaktion ist kurz: wenige Sekunden, dann Commit oder Rollback.
 * Ein Antragsvorgang dauert aber mehrere Schritte: Vorgang anlegen, Fall prüfen,
 * Bescheid erteilen, Zahlung anordnen. Das passt nicht in eine einzige
 * Datenbanktransaktion.</p>
 *
 * <p>Eine Saga zerlegt so einen Vorgang in einzelne Schritte. Jeder Schritt ist
 * eine kurze Transaktion. Schlägt ein späterer Schritt fehl, laufen die vorherigen
 * Schritte rückwärts wieder heraus — jede mit einer eigenen Kompensation. Eine
 * Kompensation ist nicht das Rückgängigmachen der Datenbank, sondern ein
 * fachlicher Ausgleich, zum Beispiel ein Storno.</p>
 *
 * <p>Die Bausteine:</p>
 * <ul>
 *   <li><b>{@link de.allgemein.tx.saga.SagaDefinition}</b> — der Bauplan:
 *       Liste der Schritte mit ihren Aktionen und Kompensationen.</li>
 *   <li><b>{@link de.allgemein.tx.saga.SagaEngine}</b> — der Fahrer: läuft die
 *       Schritte ab, führt bei einem Fehler die Kompensationen aus und nimmt
 *       nach einem Absturz unterbrochene Sagas wieder auf.</li>
 *   <li><b>{@link de.allgemein.tx.saga.SagaJournal}</b> / <b>TxSagaJournal</b> —
 *       das Fahrtenbuch in der Datenbank: welchen Stand hat welche Saga?
 *       Es macht den Wiederanlauf nach einem Absturz überhaupt möglich.</li>
 *   <li><b>{@link de.allgemein.tx.saga.SagaState}</b>, <b>SagaStep</b>,
 *       <b>SagaClaim</b>, <b>UnfinishedSaga</b> — Datenbehälter für Stand,
 *       Einzelschritt, Zustandsübernahme und wiederaufzunehmende Vorgänge.</li>
 * </ul>
 *
 * <p>Wichtig: Kompensationen sind idempotent. Nach einem Absturz dürfen sie
 * erneut angestoßen werden, ohne dass doppelt passiert.</p>
 */
package de.allgemein.tx.saga;
