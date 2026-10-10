package de.allgemein.tx;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Schnittstelle für Verbindungspools der Transaktionssteuerung.
 * Erlaubt den nahtlosen Tausch zwischen {@link SimpleConnectionPool} (schlanker Demo-Pool),
 * {@link UcpConnectionPool} (Oracle Universal Connection Pool für Produktion) oder Drittanbieter-Pools.
 */
public interface ConnectionPool extends AutoCloseable {

    /**
     * Entleiht eine einsatzbereite Verbindung aus dem Pool.
     */
    Connection getConnection() throws SQLException;

    /**
     * Gibt eine Verbindung an den Pool zurück.
     */
    void release(Connection connection);

    /**
     * Schließt den Pool und alle verwalteten physischen Verbindungen.
     */
    @Override
    void close();
}
