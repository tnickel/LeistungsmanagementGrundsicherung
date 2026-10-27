package de.allgemein.tx;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Das ist nur der Rahmen für den Verbindungspool, keine fertige Klasse.
 * Ein Pool hält fertige Datenbankverbindungen bereit.
 * Eine Aktion leiht sich eine Verbindung und gibt sie danach zurück.
 * So muss nicht bei jedem Zugriff eine neue Verbindung aufgebaut werden.
 *
 * Die Fachklasse sieht nur diese drei Methoden: holen, zurückgeben, schließen.
 * Welcher Pool dahintersteckt, ist austauschbar:
 * {@link SimpleConnectionPool} für die Demo oder {@link UcpConnectionPool}
 * für den Oracle-Pool im Betrieb.
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