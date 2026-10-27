package de.allgemein.tx;

import java.sql.SQLException;

/**
 * Dieser Fehler entsteht, wenn das Speichern gestartet wurde, die Bestätigung aber fehlt.
 * Die Daten können schon in der Datenbank stehen oder auch nicht.
 * Deshalb wird nicht automatisch noch einmal gespeichert.
 *
 * Der JDBC-Commit wurde aufgerufen, sein Ausgang aber nicht bestätigt. Ein späterer
 * Rollback beweist nicht, dass der vorherige Commit die Datenbank nicht erreicht hat.
 * Die Fachwirkung muss anhand ihrer bestehenden Korrelation nachgelesen werden.
 */
public final class TxCommitOutcomeUnknownException extends TxException {
    public TxCommitOutcomeUnknownException(Throwable cause) {
        super("Commit-Ausgang unbekannt; Verarbeitungsstatus prüfen, nicht erneut anordnen",
                cause, false, cause instanceof SQLException sql ? sql.getErrorCode() : -1);
    }
}