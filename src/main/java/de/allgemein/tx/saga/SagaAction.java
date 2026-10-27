package de.allgemein.tx.saga;

import java.sql.Connection;

/**
 * Kurz gesagt: Das ist die Arbeitsanweisung für einen Saga-Schritt.
 * Jede Saga-Aktion sieht so aus: Sie bekommt die Datenbankverbindung, die
 * Eingabedaten und die Saga-Nummer — und erledigt ihren Teil.
 *
 * Ein Saga-Schritt bzw. seine Kompensation; läuft jeweils in eigener kurzer DB-Transaktion.
 * Neben dem Payload erhält die Aktion die {@code sagaId} — darüber kann sie fachliche
 * Korrelationen deterministisch ableiten (z. B. VORGANGS_NR der Saga-Zahlung), sodass
 * eine Kompensation später gezielt NUR die eigenen Daten dieser Saga erfasst.
 */
@FunctionalInterface
public interface SagaAction {
    void run(Connection connection, String payload, String sagaId) throws Exception;
}