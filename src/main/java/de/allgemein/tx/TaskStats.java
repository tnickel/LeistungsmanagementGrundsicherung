package de.allgemein.tx;

/**
 * Das ist eine Momentaufnahme der Auftragslage.
 * Sie zeigt, wie viele Aufträge gerade laufen, fertig sind, fehlschlugen,
 * zu lange dauerten oder gar nicht angenommen wurden.
 *
 * Momentaufnahme der Task-Statistik (Vorbild: CICS-Statistiken).
 */
public record TaskStats(
        long active,
        long completed,
        long failed,
        long timedOut,
        long rejected,
        double avgDurationMillis) {
}