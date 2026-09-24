package de.allgemein.tx;

import java.time.Instant;
import java.util.UUID;

/** Laufzeitinformation zu einem eingereichten Task (Vorbild: CICS-Task im Monitoring). */
final class TaskInfo {

    final UUID id;
    final String name;
    final Instant submittedAt;
    private volatile boolean timedOut;

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
}
