package de.allgemein.testsweet;

import de.allgemein.lmg.service.FachlicheException;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import static org.junit.jupiter.api.Assertions.*;

class SzenarioAktionTest {
    @Test void fehlenderFachfehlerIstEinTestfehlerAuchOhneDatenveraenderung() {
        var aktion = SzenarioAktion.schrittMitErwartetemFehler("Unbekannte ID", "NICHT_GEFUNDEN",
                (s, ich, kollege, ctx) -> "unerwartet erfolgreich");
        var e = assertThrows(IllegalStateException.class,
                () -> aktion.execute(null, "a", "b", new HashMap<>()));
        assertTrue(e.getMessage().contains("NICHT_GEFUNDEN"));
    }

    @Test void echterFachfehlerBleibtFuerDieCodepruefungDesRunnersErhalten() {
        var fehler = new FachlicheException("VALIDATION", "bewusste Abweisung");
        var aktion = SzenarioAktion.schrittMitErwartetemFehler("Negativ", "VALIDATION",
                (s, ich, kollege, ctx) -> { throw fehler; });
        assertSame(fehler, assertThrows(FachlicheException.class,
                () -> aktion.execute(null, "a", "b", new HashMap<>())));
    }

    @Test void normalerSchrittDarfErfolgreichZurueckkehren() throws Exception {
        var aktion = SzenarioAktion.schritt("Positiv", (s, ich, kollege, ctx) -> "gebucht");
        assertEquals("gebucht", aktion.execute(null, "a", "b", new HashMap<>()));
    }
}
