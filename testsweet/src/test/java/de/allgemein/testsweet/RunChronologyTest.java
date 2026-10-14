package de.allgemein.testsweet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import static org.junit.jupiter.api.Assertions.*;

class RunChronologyTest {
    @TempDir Path temp;
    @Test void winterTimeOffsetsPreserveActualOrderOfOlderPassAndNewerFailure() {
        var old = RunChronology.start(temp,"2026-10-25T02:55:00+02:00",ZoneId.of("Europe/Berlin"));
        var latest = RunChronology.start(temp,"2026-10-25T02:05:00+01:00",ZoneId.of("Europe/Berlin"));
        assertTrue(old.explicitOffset()); assertTrue(latest.explicitOffset());
        assertEquals(Instant.parse("2026-10-25T01:05:00Z"),latest.earliest());
        assertTrue(latest.earliest().isAfter(old.latest()));
    }
    @Test void offsetlessLegacyStartRetainsBothPossibleInstantsAndCannotBeVerified() {
        var old = RunChronology.start(temp,"2026-10-25T02:55:00",ZoneId.of("Europe/Berlin"));
        var latest = RunChronology.start(temp,"2026-10-25T02:05:00",ZoneId.of("Europe/Berlin"));
        assertFalse(old.explicitOffset()); assertFalse(latest.explicitOffset());
        assertEquals(Instant.parse("2026-10-25T00:05:00Z"),latest.earliest());
        assertEquals(Instant.parse("2026-10-25T01:05:00Z"),latest.latest());
        assertFalse(latest.latest().isBefore(old.earliest()),"Zeitintervalle überlappen: keine sichere Reihenfolge");
    }
}
