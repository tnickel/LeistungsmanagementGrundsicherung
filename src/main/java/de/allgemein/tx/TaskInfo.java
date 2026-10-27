package de.allgemein.tx;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Das ist die interne Karteikarte eines laufenden Auftrags.
 * Sie merkt sich Nummer, Namen, Startzeit und ob das Zeitlimit überschritten wurde.
 * Die Steuerung braucht das, damit jeder Auftrag nur einmal abgerechnet wird.
 *
 * Laufzeitinformation zu einem eingereichten Task (Vorbild: CICS-Task im Monitoring).
 */
final class TaskInfo {

    final UUID id;
    final String name;
    final Instant submittedAt;
    private volatile boolean timedOut;
    /** Abrechnungs-Gate: garantiert, dass das Task-Ende (Zähler + running-Map) genau einmal passiert. */
    private final AtomicBoolean abgerechnet = new AtomicBoolean(false);

    TaskInfo(UUID id, String name, Instant submittedAt) {
        this.id = id;
        this.name = name;
        this.submittedAt = submittedAt;
    }

    boolean timedOut() {
        return timedOut;
    }

    void markTimedOut() {
        this.timedOut = true;
    }

    /**
     * Merkt die Abrechnung des Task-Endes an. Liefert {@code true} genau beim
     * ersten Aufruf (compareAndSet) — Callable-Wrapper, Wachhund und Abbruch über
     * das Handle konkurrieren darum; wer verliert, hat nichts mehr abzurechnen
     * und darf insbesondere keinen Zähler doppelt inkrementieren.
     */
    boolean settle() {
        return abgerechnet.compareAndSet(false, true);
    }
}