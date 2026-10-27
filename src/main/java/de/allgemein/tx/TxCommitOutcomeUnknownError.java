package de.allgemein.tx;

/**
 * Dieser schwere Fehler entsteht, wenn beim Speichern etwas Hartes schiefgeht.
 * Man weiß dann nicht, ob die Datenbank den Stand schon übernommen hat.
 * Der Vorgang muss geprüft werden und darf nicht blind wiederholt werden.
 *
 * Fataler Fehler während des JDBC-Commits. Bewahrt Error-Semantik und Commitphase,
 * damit ein Task-Aufrufer den unbekannten Ausgang nicht als gewöhnlichen Fehler behandelt.
 */
public final class TxCommitOutcomeUnknownError extends Error {
    public TxCommitOutcomeUnknownError(Error cause) {
        super("Commit-Ausgang unbekannt nach fatalem Fehler; Verarbeitungsstatus prüfen, nicht erneut anordnen",cause);
    }
}