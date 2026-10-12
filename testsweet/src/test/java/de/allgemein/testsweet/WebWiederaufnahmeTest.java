package de.allgemein.testsweet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openqa.selenium.NoSuchSessionException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class WebWiederaufnahmeTest {
    @TempDir Path temp;

    private Simulator simulator(SweetLog log) throws Exception {
        Simulator simulator = new Simulator();
        var field = Simulator.class.getDeclaredField("log");
        field.setAccessible(true);
        field.set(simulator, log);
        return simulator;
    }

    @Test void todNachBestaetigterAnlageSetztSucheFortOhneDoppelteAnlage() throws Exception {
        try (SweetLog log = new SweetLog(temp)) {
            Simulator simulator = simulator(log);
            WebMitarbeiter mitarbeiter = new WebMitarbeiter(simulator, "sb-01", "geheim", 0, 1);
            AtomicInteger anlagen = new AtomicInteger(), suchen = new AtomicInteger(), neustarts = new AtomicInteger();
            Auftrag auftrag = new Auftrag(1, "TEST", "SWEET-1", "Test", "", List.of(), List.of())
                    .mitWebAktionen(List.of(
                            WebAktion.schritt("Anlage", (d, ctx) -> {
                                assertEquals(1, anlagen.incrementAndGet(), "doppelte Persistenz");
                                ctx.put("vorgangErstellt", true);
                                ctx.put("sitzungKennung", "sb-02");
                                return "angelegt";
                            }),
                            WebAktion.wiederholbar("Suche", (d, ctx) -> {
                                if (suchen.incrementAndGet() == 1) throw new NoSuchSessionException("Session weg");
                                assertEquals(true, ctx.get("vorgangErstellt"));
                                return "gefunden";
                            })));
            mitarbeiter.spieleAuftrag(auftrag, () -> { neustarts.incrementAndGet(); return null; },
                    (d, ctx) -> assertEquals("sb-02", ctx.get("sitzungKennung")));
            assertEquals(Auftrag.Status.FERTIG_GESPIELT, auftrag.status);
            assertEquals(1, anlagen.get());
            assertEquals(1, neustarts.get());
            assertEquals(true, auftrag.kontext.get("vorgangErstellt"));
            assertFalse(auftrag.kontext.containsKey("passwort"));
        }
    }

    @Test void unklarerCommitWirdNichtWiederholtUndFolgeschrittNichtAusgefuehrt() throws Exception {
        try (SweetLog log = new SweetLog(temp)) {
            WebMitarbeiter mitarbeiter = new WebMitarbeiter(simulator(log), "sb-01", "geheim", 0, 1);
            AtomicInteger schreibversuche = new AtomicInteger(), neustarts = new AtomicInteger();
            Auftrag auftrag = new Auftrag(1, "TEST", "SWEET-1", "Test", "", List.of(), List.of())
                    .mitWebAktionen(List.of(WebAktion.schritt("Auszahlung", (d, ctx) -> {
                        schreibversuche.incrementAndGet();
                        throw new NoSuchSessionException("Antwort nach Commit verloren");
                    }), WebAktion.schritt("Darf nicht laufen", (d, ctx) -> { fail(); return ""; })));
            mitarbeiter.spieleAuftrag(auftrag, () -> { neustarts.incrementAndGet(); return null; },
                    (d, ctx) -> fail("unklarer Schreibschritt darf nicht fortgesetzt werden"));
            assertEquals(1, schreibversuche.get());
            assertEquals(1, neustarts.get());
            assertEquals(Auftrag.Status.FEHLGESCHLAGEN, auftrag.status);
            assertTrue(auftrag.fehlerText.contains("Commit-Ausgang unklar"));
        }
    }

    @Test void zweiterSitzungstodBeendetAuftragOhneNeustartschleife() throws Exception {
        try (SweetLog log = new SweetLog(temp)) {
            WebMitarbeiter mitarbeiter = new WebMitarbeiter(simulator(log), "sb-01", "geheim", 0, 1);
            AtomicInteger versuche = new AtomicInteger(), neustarts = new AtomicInteger();
            Auftrag auftrag = new Auftrag(1, "TEST", "SWEET-1", "Test", "", List.of(), List.of())
                    .mitWebAktionen(List.of(WebAktion.wiederholbar("Suche", (d, ctx) -> {
                        versuche.incrementAndGet();
                        throw new NoSuchSessionException("weiterhin Session weg");
                    })));
            mitarbeiter.spieleAuftrag(auftrag, () -> { neustarts.incrementAndGet(); return null; }, (d, ctx) -> {});
            assertEquals(2, versuche.get());
            assertEquals(1, neustarts.get());
            assertEquals(Auftrag.Status.FEHLGESCHLAGEN, auftrag.status);
        }
    }
}
