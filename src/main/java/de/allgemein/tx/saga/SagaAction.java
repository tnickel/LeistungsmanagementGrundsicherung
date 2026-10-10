package de.allgemein.tx.saga;

import java.sql.Connection;

/**
 * Ein Saga-Schritt bzw. seine Kompensation; läuft jeweils in eigener kurzer DB-Transaktion.
 * Neben dem Payload erhält die Aktion die {@code sagaId} — darüber kann sie fachliche
 * Korrelationen deterministisch ableiten (z. B. VORGANGS_NR der Saga-Zahlung), sodass
 * eine Kompensation später gezielt NUR die eigenen Daten dieser Saga erfasst.
 */
@FunctionalInterface
public interface SagaAction {
    void run(Connection connection, String payload, String sagaId) throws Exception;
}
