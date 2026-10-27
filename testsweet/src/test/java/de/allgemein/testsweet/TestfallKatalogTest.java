package de.allgemein.testsweet;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

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

    @Test
    void neueKategorienBeschreibenNachweisumfangUndModellgrenzen() {
        String echt = TestfallKatalog.fuer("ECHT", false).stream()
                .map(TestfallKatalog.Eintrag::text).collect(java.util.stream.Collectors.joining("\n"));
        String rechner = TestfallKatalog.fuer("RECHNER", false).stream()
                .map(TestfallKatalog.Eintrag::text).collect(java.util.stream.Collectors.joining("\n"));
        for (String text : List.of(echt, rechner)) {
            assertTrue(text.contains("Produktberechnung"));
            assertTrue(text.contains("Zertifikatssuite"));
            assertTrue(text.contains("GLM"));
        }
        assertTrue(echt.contains("Nichtprüfungsgrenzen"));
        assertTrue(echt.toLowerCase(java.util.Locale.ROOT).contains("keine vollständige richtlinienprüfung"));
        assertTrue(echt.contains("Heutiger Kfz-Schutz"));
        assertTrue(echt.contains("nicht modelliert"));
        assertFalse(echt.contains("jede Änderung"));
        assertFalse(echt.contains("nicht öffentlich"));
        assertTrue(rechner.contains("nicht vollständig belegt"));
        assertTrue(rechner.contains("1, 6 und 12"));
        var values = new SzenarioBibliothek(42).erzeugeRechner(12, "SWEET-KATALOG-R-").stream()
                .map(a -> a.erwartungen.stream()
                        .filter(e -> e.sql().startsWith("SELECT AUSZAHLUNGSBETRAG ")).findFirst().orElseThrow())
                .map(e -> new java.math.BigDecimal(e.erwartet())).sorted().toList();
        assertEquals(new java.math.BigDecimal("227.00"), values.getFirst());
        assertEquals(new java.math.BigDecimal("1427.00"), values.getLast());
        assertTrue(rechner.contains("227,00 bis 1.427,00"));
    }
}
