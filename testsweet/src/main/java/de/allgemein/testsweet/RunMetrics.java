package de.allgemein.testsweet;

import java.util.Arrays;

/** Kennzahlen eines begrenzten Parallel-Szenariolaufs, kein Kapazitätsnachweis. */
final class RunMetrics {
    static final String SCOPE = "Funktionaler Paralleltest mit begrenztem Auftragstopf; "
            + "keine Mindestlastdauer und keine Sättigungsstufen, daher kein Kapazitäts- oder Dauerlastnachweis";

    private RunMetrics() { }

    static double perMinute(long completed, long durationMs) {
        if (completed < 0 || durationMs < 0) throw new IllegalArgumentException("Negative Laufkennzahl");
        return durationMs == 0 ? 0 : completed * 60_000.0 / durationMs;
    }

    /** Nearest-rank; nur tatsächlich abgeschlossene Aufträge werden übergeben. */
    static long percentile(long[] durationsMs, int percentile) {
        if (percentile < 1 || percentile > 100) throw new IllegalArgumentException("Ungültiges Perzentil");
        long[] sorted = durationsMs.clone();
        if (Arrays.stream(sorted).anyMatch(value -> value < 0)) throw new IllegalArgumentException("Negative Dauer");
        if (sorted.length == 0) return 0;
        Arrays.sort(sorted);
        int rank = (int) Math.ceil(sorted.length * percentile / 100.0);
        return sorted[rank - 1];
    }
}
