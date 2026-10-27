package de.allgemein.testsweet;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Der Testfall-Katalog muss für jede Kategorie und jeden Modus Einträge mit
 * Namen und ausführlichem Text liefern — und bewusst KLEIN beim Stresstest
 * (Rahmen + zwei Wellen statt tausender gleicher Aufträge).
 */
class TestfallKatalogTest {

    @Test
    void positivFachschichtBeschreibtAlleZehnArten() {
        List<TestfallKatalog.Eintrag> eintraege = TestfallKatalog.fuer("POSITIV", false);
        assertEquals(21, eintraege.size(),
                "Vorwort + 10 Grundarten + 9 feste Grenzfälle + 1 Kombinations-Generator");
        assertTrue(eintraege.stream().anyMatch(e -> e.name().contains("Standardrente")));
        assertTrue(eintraege.stream().anyMatch(e -> e.name().contains("Doppelklick")));
        assertTrue(eintraege.stream().anyMatch(e -> e.name().contains("Kassenlauf")));
        assertTrue(eintraege.stream().anyMatch(e -> e.name().contains("Kombinationsfälle")));
    }

    @Test
    void negativFachschichtBeschreibtAlleAbwehrarten() {
        List<TestfallKatalog.Eintrag> eintraege = TestfallKatalog.fuer("NEGATIV", false);
        assertEquals(12, eintraege.size(),
                "Vorwort + 9 Abwehr-Arten + 1 Kombinations-Generator + 1 Prinzip-Eintrag");
        assertTrue(eintraege.stream().allMatch(e -> !e.text().isBlank()));
    }

    @Test
    void stressBleibtBewusstKlein() {
        assertEquals(4, TestfallKatalog.fuer("STRESS", false).size(),
                "Vorwort + Rahmen + Welle 1 + Welle 2 — keine tausend gleichen Auftragsbeschreibungen");
    }

    @Test
    void lastIstEineKurzfassung() {
        assertEquals(2, TestfallKatalog.fuer("LAST", false).size(), "Vorwort + 1 Kurzfassung");
    }

    @Test
    void jedeKategorieBeginntMitVorwortZuZweckErzeugungUndPruefung() {
        for (String kategorie : List.of("POSITIV", "NEGATIV", "ECHT", "RECHNER", "LAST", "STRESS")) {
            for (boolean web : List.of(false, true)) {
                var eintraege = TestfallKatalog.fuer(kategorie, web);
                var erste = eintraege.getFirst();
                assertTrue(erste.name().startsWith("Vorwort"),
                        kategorie + "/" + web + ": erster Eintrag ist kein Vorwort");
                for (String frage : List.of("Wozu braucht man", "Wie werden die Fälle erzeugt?",
                        "Wie wird geprüft?")) {
                    assertTrue(erste.text().contains(frage),
                            kategorie + "/" + web + ": Vorwort beantwortet nicht: " + frage);
                }
            }
        }
    }

    @Test
    void webVariantenLiefertEigeneArten() {
        assertTrue(TestfallKatalog.fuer("POSITIV", true).size() >= 3);
        assertTrue(TestfallKatalog.fuer("NEGATIV", true).size() >= 6);
        assertTrue(TestfallKatalog.fuer("POSITIV", true).stream()
                .filter(e -> !e.name().startsWith("Vorwort"))
                .allMatch(e -> e.name().startsWith("WEB-") || e.name().startsWith("Web-")));
    }

    @Test
    void alleEintraegeHabenNameUndText() {
        for (String kategorie : List.of("POSITIV", "NEGATIV", "ECHT", "RECHNER", "LAST", "STRESS")) {
            for (boolean web : List.of(false, true)) {
                for (TestfallKatalog.Eintrag e : TestfallKatalog.fuer(kategorie, web)) {
                    assertTrue(!e.name().isBlank(), kategorie + "/" + web + ": Name fehlt");
                    assertTrue(e.text().length() >= 60,
                            kategorie + "/" + web + ": '" + e.name() + "' Text zu knapp für „ausführlich“");
                }
            }
        }
    }
}
