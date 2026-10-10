package de.allgemein.tx.monitoring;

/**
 * Grundlegende JMX-Management-Schnittstelle für beliebige Verbindungspools.
 */
public interface GenericConnectionPoolMBean {

    /** Klassenname des eingesetzten Pools (z. B. SimpleConnectionPool). */
    String getPoolType();
}
