package de.allgemein.testsweet;

import de.allgemein.lmg.calc.Leistungsberechnung;
import de.allgemein.lmg.model.SozialFall;
import de.allgemein.lmg.service.FachlicheException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Feste Gegenanker für die quellenbasierten Modell- und Rechnervergleichsfälle.
 * Quellen belegen Bausteine, keine vollständige Rechtsprüfung aller Fälle.
 * Externe Browserbelege liegen für RECHNER 1/6/12 vor; die ursprüngliche
 * Zwölf-Fälle-Erhebung ist unvollständig belegt (siehe Belegverzeichnis).
 *
 * <p>Separat festgehaltene Konstanten kontrollieren sowohl Produkt-Rechenblöcke
 * als auch tatsächlich verwendete SQL-Erwartungen. Abweichungen dieser
 * Ergebnisse werden erkannt; nicht jede beliebige Berechnungsänderung.</p>
 */
class EchtRechnerFallTest {

    private static final List<String> RECHNER_SOLLWERTE = List.of(
            "978.00", "478.00", "583.00", "1063.00", "1073.71", "268.71",
            "558.00", "768.00", "1427.00", "727.00", "227.00", "1077.00");

    @Test
    void echtTopfEnthaeltFuenfBelegteFaelleInFesterReihenfolge() {
        List<Auftrag> topf = new SzenarioBibliothek(42).erzeugeEcht(5, "SWEET-TEST-E-");
        assertEquals(5, topf.size());
        assertEquals(List.of("ECHT-ANLAGE-KDU", "ECHT-BSG-VERMOEGEN", "ECHT-SCHONVERMOEGEN-800",
                        "ECHT-LSG-KFZ-PAAR", "ECHT-KDU-DECKEL"),
                topf.stream().map(a -> a.szenarioTyp).toList());
        assertTrue(topf.stream().allMatch(a -> a.aktenzeichen.startsWith("SWEET-TEST-E-")));
        assertTrue(topf.stream().allMatch(a -> !a.rechenbloecke.isEmpty()));
        assertTrue(topf.stream().allMatch(a -> !a.erwartungen.isEmpty()));
    }

    @Test
    void echtTopfBegrenztAufAnzahl() {
        assertEquals(2, new SzenarioBibliothek(42).erzeugeEcht(2, "SWEET-TEST-E-").size());
        assertEquals(5, new SzenarioBibliothek(42).erzeugeEcht(9, "SWEET-TEST-E-").size());
    }

    @Test
    void echteModellfaelleRechnenWieDieSeparatenHandwerte() {
        List<Auftrag> topf = new SzenarioBibliothek(42).erzeugeEcht(5, "SWEET-TEST-E-");

        // ECHT 1: 506 + 506 + 320 + 100 + 110 (Anlage SGB XII + Altenburger Richtwerte)
        var anlage = topf.get(0).rechenbloecke.get(0);
        assertEquals(new BigDecimal("1542.00"), anlage.ergebnis().zahlbetrag());

        // ECHT 2 (BSG 44.500 EUR): verwertbares Vermögen 34.500 deckt den
        // Restbedarf weit über → Abwehr, Zahlbetrag negativ dokumentiert
        var bsg = topf.get(1).rechenbloecke.get(0);
        assertEquals(new BigDecimal("-34377.00"), bsg.ergebnis().zahlbetrag());
        assertEquals(new BigDecimal("34500.00"), bsg.ergebnis().verwertbaresVermoegen());

        // ECHT 3 (800 EUR unter dem Schonvermögen): voller Bedarf, kein Einsatz
        var b800 = topf.get(2).rechenbloecke.get(0);
        assertEquals(new BigDecimal("1023.00"), b800.ergebnis().zahlbetrag());
        assertEquals(new BigDecimal("0.00"), b800.ergebnis().verwertbaresVermoegen());

        // ECHT 4 (zwei Kfz 31.000 EUR, Paar): verwertbar 11.000 deckt den
        // Bedarf 1.542 weit über → Abwehr im Modell; heutiger Kfz-Schutz ungeprüft
        var kfz = topf.get(3).rechenbloecke.get(0);
        assertEquals(new BigDecimal("-9458.00"), kfz.ergebnis().zahlbetrag());
        assertEquals(new BigDecimal("11000.00"), kfz.ergebnis().verwertbaresVermoegen());

        // ECHT 5: gewählter Modell-Gesamtdeckel, keine vollständige Richtlinienprüfung
        var deckel = topf.get(4).rechenbloecke.get(0);
        assertEquals(new BigDecimal("1153.00"), deckel.ergebnis().zahlbetrag());
        assertEquals(new BigDecimal("590.00"), deckel.kduParameter().angemessenheitsgrenze());
    }

    @Test
    void rechnerTopfTragtDieZwoelfExternenErgebnisseAlsSollwert() {
        List<Auftrag> topf = new SzenarioBibliothek(42).erzeugeRechner(12, "SWEET-TEST-R-");
        assertEquals(12, topf.size());
        assertEquals(5, Simulator.auftraegeLimit(Simulator.Kategorie.ECHT));
        assertEquals(12, Simulator.auftraegeLimit(Simulator.Kategorie.RECHNER));
        for (int i = 0; i < 12; i++) {
            Auftrag auftrag = topf.get(i);
            assertEquals("RECHNER-" + (i + 1), auftrag.szenarioTyp);
            assertTrue(auftrag.aktenzeichen.startsWith("SWEET-TEST-R-"));
            assertEquals(1, auftrag.rechenbloecke.size(), "Fall " + (i + 1) + ": genau ein Monat");
            // Separater fester Vergleichswert: sowohl Rechenblock als auch
            // konkrete SQL-Erwartung müssen ihn liefern (keine reine Referenztautologie).
            assertEquals(new BigDecimal(RECHNER_SOLLWERTE.get(i)),
                    auftrag.rechenbloecke.get(0).ergebnis().zahlbetrag(),
                    "RECHNER-Fall " + (i + 1) + " weicht vom festen Vergleichswert ab");
            assertMoneyExpectation(auftrag, "AUSZAHLUNGSBETRAG", RECHNER_SOLLWERTE.get(i));
            assertMoneyExpectation(auftrag, "VERMOEGENSEINSATZ", "0.00");
            assertMoneyExpectation(auftrag, "VERMOEGEN", "0.00");
            assertMoneyExpectation(auftrag, "BUDGET_REST", "100.00");
            assertEquals(new BigDecimal(RECHNER_SOLLWERTE.get(i)).add(new BigDecimal("100.00")),
                    auftrag.rechenbloecke.getFirst().budget());
            var payment = moneyExpectation(auftrag, "AUSZAHLUNGSBETRAG");
            assertEquals(List.of(17L, "2026-10"), payment.parameter().apply(Map.of("bgId", 17L)));
        }
    }

    @Test
    void echteErfolgsfaelleVerankernDieTatsaechlichenSqlSollwerte() {
        var cases = new SzenarioBibliothek(42).erzeugeEcht(5, "SWEET-TEST-E-");
        for (int index : List.of(0, 2, 4)) {
            var expected = Map.of(0, "1542.00", 2, "1023.00", 4, "1153.00");
            var auftrag = cases.get(index);
            assertMoneyExpectation(auftrag, "AUSZAHLUNGSBETRAG", expected.get(index));
            assertMoneyExpectation(auftrag, "VERMOEGENSEINSATZ", "0.00");
            assertMoneyExpectation(auftrag, "VERMOEGEN", index == 2 ? "800.00" : "0.00");
            assertMoneyExpectation(auftrag, "BUDGET_REST", "100.00");
            String month = index == 4 ? "2026-07" : "2026-10";
            assertEquals(List.of(17L, month), moneyExpectation(auftrag, "AUSZAHLUNGSBETRAG")
                    .parameter().apply(Map.of("bgId", 17L)));
        }
    }

    @Test
    void vermoegensAbwehrVerankertFehlerUndUnveraendertenZustand() {
        var cases = new SzenarioBibliothek(42).erzeugeEcht(5, "SWEET-TEST-E-");
        for (int index : List.of(1, 3)) {
            var auftrag = cases.get(index);
            var guarded = auftrag.aktionen.stream().filter(a -> a.erwarteterFehler() != null).toList();
            assertEquals(1, guarded.size());
            assertEquals(FachlicheException.KEIN_ANSPRUCH, guarded.getFirst().erwarteterFehler());
            assertEquals(List.of("Einkommen/Vermögen übersteigen den Gesamtbedarf"),
                    guarded.getFirst().erwarteteFehlergruende());
            assertMoneyExpectation(auftrag, "BUDGET_REST", "1000.00");
            assertMoneyExpectation(auftrag, "VERMOEGEN", index == 1 ? "44500.00" : "31000.00");
            var noPayment = auftrag.erwartungen.stream()
                    .filter(e -> e.sql().equals("SELECT COUNT(*) FROM ZAHLUNG WHERE BG_ID = ?")).toList();
            assertEquals(1, noPayment.size());
            assertEquals("0", noPayment.getFirst().erwartet());
            assertTrue(auftrag.beschreibungEinfach.contains("nicht modelliert"));
        }
    }

    @Test
    void gesamtdeckelIstKeineKomponentenOderEinzelfallPruefung() {
        var block = new SzenarioBibliothek(42).erzeugeEcht(5, "SWEET-TEST-E-").get(4)
                .rechenbloecke.getFirst();
        var input = new SozialFall(0, 0, 1, false, false, 0, 0, false,
                new BigDecimal("300"), new BigDecimal("90"), new BigDecimal("300"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, 0);
        var capped = Leistungsberechnung.berechne(0, List.of(input), block.kduParameter(), BigDecimal.ZERO);
        var uncapped = Leistungsberechnung.berechne(0, List.of(input),
                Leistungsberechnung.KduParameter.AUS, BigDecimal.ZERO);
        assertEquals(new BigDecimal("1153.00"), capped.zahlbetrag());
        assertEquals(new BigDecimal("1253.00"), uncapped.zahlbetrag());
        assertNotEquals(capped.zahlbetrag(), uncapped.zahlbetrag());
        // Ob höhere Heizkosten anzuerkennen sind, verlangt eine gesonderte
        // Fachentscheidung. Dieser Test behauptet keinen rechtlichen Sollbescheid.
        assertTrue(block.zusatzHinweise().stream().anyMatch(h -> h.contains("Einzelfallentscheidungen")));
    }

    @Test
    void alleTeiltopfeErhaltenIdentitaetReihenfolgeUndPraefix() {
        var completeEcht = new SzenarioBibliothek(42).erzeugeEcht(5, "SWEET-TEST-E-");
        var completeRechner = new SzenarioBibliothek(42).erzeugeRechner(12, "SWEET-TEST-R-");
        for (int count = 1; count <= 5; count++) {
            var partial = new SzenarioBibliothek(91).erzeugeEcht(count, "SWEET-TEST-E-");
            assertEquals(completeEcht.subList(0, count).stream().map(a -> a.szenarioTyp).toList(),
                    partial.stream().map(a -> a.szenarioTyp).toList());
            assertTrue(partial.stream().allMatch(a -> a.aktenzeichen.startsWith("SWEET-TEST-E-")));
        }
        for (int count = 1; count <= 12; count++) {
            var partial = new SzenarioBibliothek(91).erzeugeRechner(count, "SWEET-TEST-R-");
            assertEquals(completeRechner.subList(0, count).stream().map(a -> a.szenarioTyp).toList(),
                    partial.stream().map(a -> a.szenarioTyp).toList());
            for (int i = 0; i < count; i++)
                assertMoneyExpectation(partial.get(i), "AUSZAHLUNGSBETRAG", RECHNER_SOLLWERTE.get(i));
            assertTrue(partial.stream().allMatch(a -> a.aktenzeichen.startsWith("SWEET-TEST-R-")));
        }
    }

    private static Erwartung moneyExpectation(Auftrag auftrag, String column) {
        var matching = auftrag.erwartungen.stream()
                .filter(e -> e.sql().startsWith("SELECT " + column + " ") && e.art() == Erwartung.Art.BETRAG)
                .toList();
        assertEquals(1, matching.size(), auftrag.szenarioTyp + ": genau eine SQL-Erwartung für " + column);
        return matching.getFirst();
    }

    private static void assertMoneyExpectation(Auftrag auftrag, String column, String expected) {
        assertEquals(new BigDecimal(expected), new BigDecimal(moneyExpectation(auftrag, column).erwartet()),
                auftrag.szenarioTyp + ": SQL-Sollwert " + column);
    }

    @Test
    void rechnerTopfBegrenztAufAnzahl() {
        assertEquals(4, new SzenarioBibliothek(42).erzeugeRechner(4, "SWEET-TEST-R-").size());
        assertEquals(12, new SzenarioBibliothek(42).erzeugeRechner(99, "SWEET-TEST-R-").size());
    }

    @Test
    void katalogBeschreibtBeideNeuenKategorien() {
        assertEquals(7, TestfallKatalog.fuer("ECHT", false).size(), "Vorwort + 6 Einträge");
        assertTrue(TestfallKatalog.fuer("ECHT", false).stream()
                .anyMatch(e -> e.name().contains("BSG")));
        assertEquals(3, TestfallKatalog.fuer("RECHNER", false).size(), "Vorwort + 2 Einträge");
        // Der verwendete Online-Rechner muss im Katalog vollständig verlinkt sein.
        assertTrue(TestfallKatalog.fuer("RECHNER", false).stream()
                .anyMatch(e -> e.text().contains(
                        "https://www.steuerschroeder.de/Steuerrechner/Grundsicherung.html")));
    }
}
