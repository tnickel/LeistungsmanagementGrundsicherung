package de.allgemein.testsweet;

import de.allgemein.lmg.service.FachlicheException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class MitarbeiterErwarteterFehlerTest {
    @TempDir Path temp;

    @Test void runnerAkzeptiertNurDenTatsaechlichGeworfenenErwartetenCode() throws Exception {
        for (String verhalten : List.of("keinFehler", "falscherCode", "falscherGrund", "richtigerCode")) {
            var simulator = new Simulator(auftrag -> new Fachzustand(java.util.Map.of()));
            try (var log = new SweetLog(temp.resolve(verhalten))) {
                var logFeld = Simulator.class.getDeclaredField("log");
                logFeld.setAccessible(true);
                logFeld.set(simulator, log);
                var aktion = SzenarioAktion.schrittMitErwartetemFehler("Abwehr", "VALIDATION", List.of("erwartete Abwehr"),
                        (service, ich, kollege, ctx) -> {
                            if (verhalten.equals("falscherCode")) {
                                throw new FachlicheException("NICHT_GEFUNDEN", "falscher Fehler");
                            }
                            if (verhalten.equals("richtigerCode")) {
                                throw new FachlicheException("VALIDATION", "erwartete Abwehr");
                            }
                            if (verhalten.equals("falscherGrund")) {
                                throw new FachlicheException("VALIDATION", "Vorgang ist historisiert — völlig andere Abweisung");
                            }
                            return "unerwartet erfolgreich";
                        });
                var auftrag = new Auftrag(1, "NEG-TEST", "SWEET-TEST", "Test", "",
                        List.of(aktion), List.of());
                var mitarbeiter = new Mitarbeiter(simulator, "sb-test", 0);
                var spielen = Mitarbeiter.class.getDeclaredMethod("spiele", Auftrag.class);
                spielen.setAccessible(true);
                spielen.invoke(mitarbeiter, auftrag);
                assertEquals(verhalten.equals("richtigerCode") ? Auftrag.Status.FERTIG_GESPIELT
                        : Auftrag.Status.FEHLGESCHLAGEN, auftrag.status, verhalten);
                assertEquals(verhalten.equals("richtigerCode"),
                        auftrag.schritte().getFirst().ergebnis().startsWith("ERWARTETER FEHLER"));
            }
        }
    }
}
