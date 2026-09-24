package de.allgemein.tx.saga;

import java.sql.Connection;

/** Ein Saga-Schritt bzw. seine Kompensation; läuft jeweils in eigener kurzer DB-Transaktion. */
@FunctionalInterface
public interface SagaAction {
    void run(Connection connection, String payload) throws Exception;
}
