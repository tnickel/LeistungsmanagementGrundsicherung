package de.allgemein.testsweet;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Die Kategorien ECHT und RECHNER sichern belegte Fremdwerte ab: Gesetz
 * (Anlage § 28 SGB XII), Gerichtsurteile (BSG B 8 SO 4/24 R u. a.),
 * kommunale KdU-Richtlinie (Landkreis Altenburger Land, 27.05.2026) und der
 * externe Online-Rechner steuerschroeder.de (Rechtsstand 03.07.2026,
 * abgerufen 27.10.2026 — doc/belege/echt-und-rechner-faelle-2026-10-27.md).
 *
 * <p>Diese Tests halten die extern belegten Sollwerte als Konstanten fest:
 * Ändert jemand die Bedarfsberechnung, werden sie rot — genau der gewollte
 * Bruch des Erwartungswert-Zirkelschlusses („selbst ausgedacht, selbst
 * ausgerechnet").</p>
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
    void echteFaelleRechnenExaktWieDieExternenBelege() {
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
        // Bedarf 1.542 weit über → Abwehr wie im Urteil (kein Anspruch)
        var kfz = topf.get(3).rechenbloecke.get(0);
        assertEquals(new BigDecimal("-9458.00"), kfz.ergebnis().zahlbetrag());
        assertEquals(new BigDecimal("11000.00"), kfz.ergebnis().verwertbaresVermoegen());

        // ECHT 5 (kommunale Obergrenze): KdU 700 auf 590 gekappt, Grenze im Block dokumentiert
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
            // Der externe Rechnerwert steht fest im Test; die Referenzrechnung
            // (dieselbe Klasse wie das Produkt) muss ihn exakt liefern.
            assertEquals(new BigDecimal(RECHNER_SOLLWERTE.get(i)),
                    auftrag.rechenbloecke.get(0).ergebnis().zahlbetrag(),
                    "RECHNER-Fall " + (i + 1) + " weicht vom externen Rechnerergebnis ab");
        }
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
