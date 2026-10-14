package de.allgemein.tx.saga;

import java.sql.SQLException;

/**
 * Exklusiver Anspruch auf eine unvollendete Saga während der Ausführung oder des Wiederanlaufs
 * ({@link TxSagaJournal#claimUnfinished}): Die Kopfzeile in TX_SAGA ist
 * mit {@code SELECT … FOR UPDATE SKIP LOCKED} gesperrt — ein paralleler
 * Wiederanlauf erhält diese Saga nicht und arbeitet die nächste ab. Auch der normale
 * Lauf hält diesen Anspruch über seine Schritttransaktionen hinweg. Dafür ist neben
 * der Fachtransaktion eine eigene Pool-Verbindung bis zum Abschluss erforderlich.
 *
 * <p>Der Anspruch endet genau einmal: {@link #finish} schreibt den Endzustand
 * und committet (gibt die Sperre frei), {@link #close()} verwirft offene
 * Änderungen per Rollback. Beide geben die Verbindung an den Pool zurück.</p>
 */
public interface SagaClaim extends AutoCloseable {

    /** Die beanspruchte Saga inklusive Schritt-Zuständen zum Zeitpunkt des Claims. */
    UnfinishedSaga saga();

    /** Prüft die gehaltene Sperre und hält eine länger entliehene Claim-Verbindung aktiv. */
    default void checkAlive() throws SQLException {
        // Ein reines In-Memory-Journal benötigt keinen JDBC-Heartbeat.
    }

    /** Setzt den Endzustand der Saga im Kopf und hebt den Anspruch auf (Commit). */
    void finish(SagaState endState) throws SQLException;

    /** Hebt den Anspruch auf, ohne den Kopf zu verändern (Rollback). */
    @Override
    void close();
}
