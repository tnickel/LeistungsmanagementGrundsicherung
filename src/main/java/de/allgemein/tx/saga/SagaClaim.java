package de.allgemein.tx.saga;

import java.sql.SQLException;

/**
 * Exklusiver Anspruch auf eine unvollendete Saga während des Wiederlaufs
 * ({@link TxSagaJournal#claimUnfinished}): Die Kopfzeile in TX_SAGA ist
 * mit {@code SELECT … FOR UPDATE SKIP LOCKED} gesperrt — ein paralleler
 * Wiederanlauf erhält diese Saga nicht und arbeitet die nächste ab.
 *
 * <p>Der Anspruch endet genau einmal: {@link #finish} schreibt den Endzustand
 * und committet (gibt die Sperre frei), {@link #close()} verwirft offene
 * Änderungen per Rollback. Beide geben die Verbindung an den Pool zurück.</p>
 */
public interface SagaClaim extends AutoCloseable {

    /** Die beanspruchte Saga inklusive Schritt-Zuständen zum Zeitpunkt des Claims. */
    UnfinishedSaga saga();

    /** Setzt den Endzustand der Saga im Kopf und hebt den Anspruch auf (Commit). */
    void finish(SagaState endState) throws SQLException;

    /** Hebt den Anspruch auf, ohne den Kopf zu verändern (Rollback). */
    @Override
    void close();
}
