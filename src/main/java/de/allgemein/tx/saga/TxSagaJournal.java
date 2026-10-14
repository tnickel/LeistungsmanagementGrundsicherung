package de.allgemein.tx.saga;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Journal-Zugriff der {@link SagaEngine}: hält den Lebenszyklus eines
 * Geschäftsvorgangs (Saga) dauerhaft fest. Die Engine hängt nur an diesem
 * Interface — im Test kann das Journal dadurch durch eine In-Memory-Umsetzung
 * ersetzt werden.
 *
 * <p>Schreibregeln (Write-Ahead, Fund K1): Vor der Ausführung eines Fachschritts
 * wird dieser mit {@link #markStepInFlight} dauerhaft als begonnen markiert
 * (eigener kurzer Commit). Der Erfolg des Schritts wird mit
 * {@link #stepDone(Connection, String, int, String)} IN derselben Transaktion
 * wie der Fachschritt vermerkt — Journal-Eintrag und Fachwirkung committen
 * atomar. Ein Absturz kann seither keinen committeten Schritt mehr verbergen:
 * {@code IN_FLIGHT} gilt beim Wiederanlauf konservativ als „möglicherweise
 * committet" und wird mit kompensiert.</p>
 */
public interface TxSagaJournal {

    /** Legt Kopf (RUNNING) und alle Schritte (PENDING) der Saga an — atomar in einer Transaktion. */
    void start(String sagaId, String type, String payload, int stepCount) throws SQLException;

    /**
     * Write-Ahead: markiert den Schritt als begonnen (PENDING → IN_FLIGHT), bevor die
     * Fachtransaktion startet. Wirft, wenn der Schritt nicht mehr PENDING ist (Guard).
     */
    void markStepInFlight(String sagaId, int index) throws SQLException;

    /**
     * Vermerkt den Erfolg eines Schritts — muss IN derselben Transaktion wie der
     * Fachschritt aufgerufen werden (Connection der laufenden Fachtransaktion).
     * Guard: nur IN_FLIGHT → DONE; bei anderem Zustand wirft die Methode und rollt
     * damit die Fachtransaktion mit zurück.
     */
    void stepDone(Connection connection, String sagaId, int index, String name) throws SQLException;

    /**
     * Vermerkt die Kompensation eines Schritts — muss IN derselben Transaktion wie
     * die Fach-Kompensation aufgerufen werden. Guard: nur DONE/IN_FLIGHT → COMPENSATED.
     */
    void stepCompensated(Connection connection, String sagaId, int index) throws SQLException;

    /**
     * Setzt den End-/Zwischenzustand des Saga-Kopfs (eigene kurze Transaktion).
     * Guard: nur erlaubte Vorgänger-Zustände; bereits erreichter Zielzustand ist
     * idempotent (kein Fehler).
     */
    void markState(String sagaId, SagaState state) throws SQLException;

    /** Liest, ob ein Schritt bereits kompensiert wurde (eigene kurze Lesetransaktion). */
    boolean isStepCompensated(String sagaId, int index) throws SQLException;

    /**
     * Liest, ob ein Schritt im Journal als DONE geführt wird (eigene kurze Lesetransaktion).
     * Dient der Klärung eines mehrdeutigen Commits: Hat die Fachtransaktion trotz Fehlermeldung
     * committet, steht der Schritt auf DONE und muss mit kompensiert werden. Implementierungen
     * ohne Lesezugriff liefern konservativ {@code false}.
     */
    default boolean isStepDone(String sagaId, int index) throws SQLException {
        return false;
    }

    /** Alle unvollendeten Sagas (RUNNING/FAILED/COMPENSATING) — ohne Sperre, nur Information. */
    List<UnfinishedSaga> unfinished() throws SQLException;

    /**
     * Wie {@link #unfinished()}, aber nur Sagas, deren letzte Journal-Aktivität (Kopf oder
     * Schritt) mindestens {@code mindestInaktivitaet} zurückliegt. Lebende Sagas sind zusätzlich
     * über ihren exklusiven Claim geschützt. Die Nutzdaten (Payload) werden hier nicht
     * geladen; sie liefert erst {@link #claimUnfinished}.
     */
    default List<UnfinishedSaga> unfinished(java.time.Duration mindestInaktivitaet) throws SQLException {
        return unfinished();
    }

    /**
     * Beansprucht eine konkrete unvollendete Saga exklusiv für den Wiederanlauf: Die
     * Kopfzeile wird mit {@code SELECT … FOR UPDATE SKIP LOCKED} gesperrt — ein paralleler
     * Wiederanlauf (zweiter Server) erhält dafür {@code null} und arbeitet die nächste ab.
     * Liefert ebenfalls {@code null}, wenn die Saga inzwischen abgeschlossen wurde. Der
     * Anspruch endet mit {@link SagaClaim#finish} (Commit) oder {@link SagaClaim#close()}
     * (Rollback).
     */
    SagaClaim claimUnfinished(String sagaId, String type) throws SQLException;
}
