package de.allgemein.tx.monitoring;

/**
 * JMX-Management-Schnittstelle für den TaskController (CICS-Transaktionsüberwachung).
 * Erlaubt das Abfragen von Laufzeitstatistiken über JConsole, VisualVM oder Monitoring-Tools.
 */
public interface TaskControllerMBean {

    /** Anzahl der aktuell ausgeführten Tasks. */
    long getActiveTasks();

    /** Anzahl der erfolgreich abgeschlossenen Tasks. */
    long getCompletedTasks();

    /** Anzahl der mit fachlichem oder technischem Fehler beendeten Tasks. */
    long getFailedTasks();

    /** Anzahl der durch den Wachhund (Timeout) abgebrochenen Tasks. */
    long getTimedOutTasks();

    /** Anzahl der wegen voller Warteschlange abgewiesenen Tasks. */
    long getRejectedTasks();

    /** Durchschnittliche Ausführungsdauer in Millisekunden. */
    double getAvgDurationMillis();

    /** Aktuelle Anzahl aktiver Worker-Threads im Pool. */
    int getPoolSize();

    /** Anzahl der aktuell wartenden Tasks in der Warteschlange. */
    int getQueueSize();

    /** Verbleibende Kapazität der Warteschlange vor Abweisung. */
    int getQueueRemainingCapacity();
}
