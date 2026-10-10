package de.allgemein.tx.monitoring;

/**
 * JMX-Management-Schnittstelle für den Oracle Universal Connection Pool (UCP).
 * Liefert Poolmetriken (ausgeliehene Verbindungen, freie Verbindungen, Grenzen).
 */
public interface UcpConnectionPoolMBean {

    /** Anzahl der aktuell ausgeliehenen / aktiven Verbindungen. */
    int getBorrowedConnections();

    /** Anzahl der aktuell im Pool bereitstehenden freien Verbindungen. */
    int getAvailableConnections();

    /** Gesamtanzahl existierender Verbindungen (ausgeliehen + verfügbar). */
    int getTotalConnections();

    /** Konfigurierte minimale Poolgröße. */
    int getMinPoolSize();

    /** Konfigurierte maximale Poolgröße. */
    int getMaxPoolSize();

    /** Konfigurierte initiale Poolgröße. */
    int getInitialPoolSize();

    /** Name des UCP-Verbindungspools. */
    String getConnectionPoolName();

    /** JDBC-Verbindungs-URL. */
    String getUrl();

    /** Datenbankbenutzer. */
    String getUser();
}
