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
        assertEquals(20, eintraege.size(), "10 Grundarten + 9 feste Grenzfälle + 1 Kombinations-Generator");
        assertTrue(eintraege.stream().anyMatch(e -> e.name().contains("Standardrente")));
        assertTrue(eintraege.stream().anyMatch(e -> e.name().contains("Doppelklick")));
        assertTrue(eintraege.stream().anyMatch(e -> e.name().contains("Kassenlauf")));
        assertTrue(eintraege.stream().anyMatch(e -> e.name().contains("Kombinationsfälle")));
    }

    @Test
    void negativFachschichtBeschreibtAlleAbwehrarten() {
        List<TestfallKatalog.Eintrag> eintraege = TestfallKatalog.fuer("NEGATIV", false);
        assertEquals(11, eintraege.size(), "9 Abwehr-Arten + 1 Kombinations-Generator + 1 Prinzip-Eintrag");
        assertTrue(eintraege.stream().allMatch(e -> !e.text().isBlank()));
    }

    @Test
    void stressBleibtBewusstKlein() {
        assertEquals(3, TestfallKatalog.fuer("STRESS", false).size(),
                "Rahmen + Welle 1 + Welle 2 — keine tausend gleichen Auftragsbeschreibungen");
    }

    @Test
    void lastIstEineKurzfassung() {
        assertEquals(1, TestfallKatalog.fuer("LAST", false).size());
    }

    @Test
    void webVariantenLiefertEigeneArten() {
        assertTrue(TestfallKatalog.fuer("POSITIV", true).size() >= 3);
        assertTrue(TestfallKatalog.fuer("NEGATIV", true).size() >= 6);
        assertTrue(TestfallKatalog.fuer("POSITIV", true).stream()
                .allMatch(e -> e.name().startsWith("WEB-") || e.name().startsWith("Web-")));
    }

    @Test
    void alleEintraegeHabenNameUndText() {
        for (String kategorie : List.of("POSITIV", "NEGATIV", "LAST", "STRESS")) {
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
