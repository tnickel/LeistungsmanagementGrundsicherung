package de.allgemein.testsweet;

import de.allgemein.lmg.service.FachlicheException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class NegativZustandsTest {
    @TempDir Path temp;

    @Test void budgetVermoegenZahlungsbetragFallversionUndFachjournalSindUnveraenderlich() {
        for (String field : List.of("BUDGET_REST", "VERMOEGEN", "AUSZAHLUNGSBETRAG", "VERSION", "AKTION_TEXT")) {
            String table = field.equals("AUSZAHLUNGSBETRAG") ? "ZAHLUNG"
                    : field.equals("VERSION") ? "SOZIAL_FALL" : field.equals("AKTION_TEXT") ? "JOURNAL" : "BEDARFSGEMEINSCHAFT";
            Fachzustand before = state(table, Map.of("ID", "1", field, "original"));
            Fachzustand mutated = state(table, Map.of("ID", "1", field, "mutiert"));
            assertThrows(IllegalStateException.class, () -> before.verlangeUnveraendert(mutated), field);
        }
    }

    @Test void neueEinsichtIstErlaubtAberAenderungAlterAuditsOderFachbuchungNicht() {
        var original = Map.of("ID", "1", "AKTION_TEXT", "Einsicht: Vorgang durch sb-01");
        Fachzustand before = state("JOURNAL", original);
        assertDoesNotThrow(() -> before.verlangeUnveraendert(new Fachzustand(Map.of("JOURNAL", List.of(original,
                Map.of("ID", "2", "AKTION_TEXT", "Einsicht: Vorgang durch sb-02"))))));
        assertThrows(IllegalStateException.class, () -> before.verlangeUnveraendert(state("JOURNAL",
                Map.of("ID", "1", "AKTION_TEXT", "Einsicht: manipuliert"))));
        assertThrows(IllegalStateException.class, () -> before.verlangeUnveraendert(new Fachzustand(Map.of("JOURNAL", List.of(original,
                Map.of("ID", "2", "AKTION_TEXT", "Auszahlung angeordnet"))))));
        assertThrows(IllegalStateException.class, () -> before.verlangeUnveraendert(new Fachzustand(Map.of("JOURNAL", List.of()))));
    }

    @Test void passenderFachfehlerMitVorherigerMutationWirdVomMitarbeiterAbgelehnt() throws Exception {
        AtomicInteger reads = new AtomicInteger();
        Simulator simulator = mutierenderSimulator(reads);
        try (SweetLog log = new SweetLog(temp)) {
            setLog(simulator, log);
            Auftrag order = new Auftrag(1, "NEG-TEST", "SWEET-TEST", "", "", List.of(
                    SzenarioAktion.schrittMitErwartetemFehler("Abwehr", "VALIDATION", (s, i, k, c) -> {
                        throw new FachlicheException("VALIDATION", "gewünschter Fehlerdialog");
                    })), List.of());
            var method = Mitarbeiter.class.getDeclaredMethod("spiele", Auftrag.class);
            method.setAccessible(true);
            method.invoke(new Mitarbeiter(simulator, "sb-test", 0), order);
            assertEquals(Auftrag.Status.FEHLGESCHLAGEN, order.status);
            assertTrue(order.fehlerText.contains("Fachbestand verändert"));
            assertEquals(2, reads.get());
            assertFalse(order.pruefungen().getFirst().ok());
        }
    }

    @Test void sichtbarerUiFehlerdialogMitMutationWirdEbenfallsAbgelehnt() throws Exception {
        AtomicInteger reads = new AtomicInteger();
        Simulator simulator = mutierenderSimulator(reads);
        try (SweetLog log = new SweetLog(temp)) {
            setLog(simulator, log);
            Auftrag order = new Auftrag(1, "WEB-NEG-TEST", "SWEET-TEST", "", "", List.of(), List.of())
                    .mitWebAktionen(List.of(WebAktion.abweisung("Abgewehrte Anordnung", (d, c) -> "erwarteter Fehlerdialog")));
            new WebMitarbeiter(simulator, "sb-test", "test", 0, 1, 0)
                    .spieleAuftrag(order, () -> null, (d, c) -> { });
            assertEquals(Auftrag.Status.FEHLGESCHLAGEN, order.status);
            assertEquals(2, reads.get());
            assertFalse(order.pruefungen().getFirst().ok());
        }
    }

    @Test void alleWebNegativartenMarkierenIhreAbwehrUndKeineVorbereitung() {
        var orders = new WebSzenarioBibliothek(42).erzeugeNegativ(500, "SWEET-NW-");
        assertEquals(7, orders.stream().map(o -> o.szenarioTyp).distinct().count());
        for (Auftrag order : orders) {
            assertTrue(order.webAktionen.stream().anyMatch(WebAktion::abweisungErwartet), order.szenarioTyp);
            assertTrue(order.webAktionen.stream().filter(WebAktion::abweisungErwartet).noneMatch(WebAktion::wiederholbar));
        }
    }

    private Simulator mutierenderSimulator(AtomicInteger reads) {
        return new Simulator(order -> state("BEDARFSGEMEINSCHAFT", Map.of("ID", "1", "BUDGET_REST",
                reads.getAndIncrement() == 0 ? "1200" : "1100")));
    }
    private static Fachzustand state(String table, Map<String, String> row) {
        return new Fachzustand(Map.of(table, List.of(row)));
    }
    private static void setLog(Simulator simulator, SweetLog log) throws Exception {
        var field = Simulator.class.getDeclaredField("log"); field.setAccessible(true); field.set(simulator, log);
    }
}
