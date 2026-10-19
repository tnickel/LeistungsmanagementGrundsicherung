package de.allgemein.tx;

/**
 * Fataler Fehler während des JDBC-Commits. Bewahrt Error-Semantik und Commitphase,
 * damit ein Task-Aufrufer den unbekannten Ausgang nicht als gewöhnlichen Fehler behandelt.
 */
public final class TxCommitOutcomeUnknownError extends Error {
    public TxCommitOutcomeUnknownError(Error cause) {
        super("Commit-Ausgang unbekannt nach fatalem Fehler; Verarbeitungsstatus prüfen, nicht erneut anordnen",cause);
    }
}
