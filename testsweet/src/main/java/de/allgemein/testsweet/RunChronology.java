package de.allgemein.testsweet;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Map;

/** Eindeutige Laufreihenfolge; lokale Altzeitstempel sind kein aktueller Nachweis. */
final class RunChronology {
    record Start(Instant earliest, Instant latest, boolean explicitOffset) { }

    static Start start(Path directory, Object value) {
        return start(directory, value, ZoneId.systemDefault());
    }

    static Start start(Path directory, Object value, ZoneId zone) {
        if (value instanceof String text) {
            try {
                Instant instant = OffsetDateTime.parse(text).toInstant();
                return new Start(instant, instant, true);
            } catch (RuntimeException legacy) {
                try {
                    LocalDateTime local = LocalDateTime.parse(text);
                    var offsets = zone.getRules().getValidOffsets(local);
                    if (!offsets.isEmpty()) {
                        var instants = offsets.stream().map(local::toInstant).sorted().toList();
                        return new Start(instants.getFirst(), instants.getLast(), false);
                    }
                } catch (RuntimeException invalid) { /* Kein rekonstruierbarer Zeitstempel. */ }
            }
        }
        try {
            Instant fallback = Files.readAttributes(directory, java.nio.file.attribute.BasicFileAttributes.class).creationTime().toInstant();
            return new Start(fallback, fallback, false);
        } catch (Exception unavailable) { return new Start(Instant.MIN, Instant.MAX, false); }
    }

    static Start start(Path directory) {
        try {
            var state = (Map<?, ?>) de.allgemein.lmg.web.Json.parse(Files.readString(directory.resolve("run-state.json")));
            return start(directory, state.get("startedAt"));
        } catch (Exception invalid) { return start(directory, null); }
    }

    private RunChronology() { }
}
