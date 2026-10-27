package de.allgemein.tx;

/**
 * Das ist die eigentliche Facharbeit, die in einer Datenbankaktion laufen soll.
 * Der Aufrufer übergibt nur dieses Stück Arbeit.
 * Start, Speichern und Zurücknehmen übernimmt die Transaktionssteuerung.
 *
 * Arbeitseinheit, die innerhalb einer Transaktion ausgeführt wird.
 */
@FunctionalInterface
public interface TxWork<T> {
    T run(TxContext ctx) throws Exception;
}