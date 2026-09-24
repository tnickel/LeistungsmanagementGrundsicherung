package de.allgemein.tx;

/** Momentaufnahme der Task-Statistik (Vorbild: CICS-Statistiken). */
public record TaskStats(
        long active,
        long completed,
        long failed,
        long timedOut,
        long rejected,
        double avgDurationMillis) {
}
