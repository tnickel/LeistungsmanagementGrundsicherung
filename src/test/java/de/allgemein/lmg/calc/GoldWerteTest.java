package de.allgemein.lmg.calc;

import de.allgemein.lmg.model.SozialFall;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Goldene Handwerte gegen die Bedarfsberechnung — die Gegenmaßnahme zum
 * Zirkelschluss (Codereview CR-011): Alle Soll-Beträge stammen aus
 * doc/regressionstests.md und wurden dort unabhängig von der Implementierung
 * handgerechnet. Ein Fehler in Leistungsberechnung (falscher Prozentsatz,
 * Rundung, Deckelung) erzeugt hier rot, auch wenn testsweet mit derselben
 * Klasse rechnet und dort grün bliebe.
 */
class GoldWerteTest {

    private static BigDecimal eur(String text) {
        return new BigDecimal(text);
    }

    /** doc/regressionstests.md Fall 1: RBS 1 + KdU 490,00 = 1.053,00 EUR. */
    @Test
    void fall1_Standardrente() {
        SozialFall fall = new SozialFall(1, 1, 1, false, false, eur("350.00"), eur("60.00"),
                eur("80.00"), eur("0.00"), eur("0.00"), 1L);
        var ergebnis = Leistungsberechnung.berechne(1, List.of(fall));
        assertEquals(0, ergebnis.zahlbetrag().compareTo(eur("1053.00")), ergebnis.zahlbetrag().toPlainString());
    }

    /** Fall 2: Ehepaar 2 × RBS 2 + KdU 650,00 = 1.662,00 EUR. */
    @Test
    void fall2_Paar() {
        SozialFall a = new SozialFall(1, 1, 2, false, false, eur("650.00"), eur("0.00"),
                eur("0.00"), eur("0.00"), eur("0.00"), 1L);
        SozialFall b = new SozialFall(2, 1, 2, false, false, eur("0.00"), eur("0.00"),
                eur("0.00"), eur("0.00"), eur("0.00"), 1L);
        assertEquals(0, Leistungsberechnung.berechne(1, List.of(a, b)).zahlbetrag()
                .compareTo(eur("1662.00")));
    }

    /** Fall 3: G (95,71) + schwanger (95,71) + dezentrales Warmwasser (12,95) über RBS 1 = 767,37 EUR. */
    @Test
    void fall3_Mehrbedarfe() {
        SozialFall fall = new SozialFall(1, 1, 1, true, true, 0, 0, true,
                eur("0.00"), eur("0.00"), eur("0.00"), eur("0.00"), eur("0.00"),
                eur("0.00"), eur("0.00"), eur("0.00"), eur("0.00"), 1L);
        var ergebnis = Leistungsberechnung.berechne(1, List.of(fall));
        assertEquals(0, ergebnis.zahlbetrag().compareTo(eur("767.37")), ergebnis.zahlbetrag().toPlainString());
    }

    /** Fall 4: alleinerziehende Mutter (36 % RBS 1 = 202,68) + KdU 710 + 2 × RBS 6 = 2.189,68 EUR. */
    @Test
    void fall4_Familie() {
        SozialFall mutter = new SozialFall(1, 1, 1, false, false, 0, 2, 2, false,
                eur("500.00"), eur("90.00"), eur("120.00"), eur("0.00"), eur("0.00"),
                eur("0.00"), eur("0.00"), eur("0.00"), eur("0.00"), 1L);
        SozialFall kind1 = new SozialFall(2, 1, 6, false, false, eur("0.00"), eur("0.00"),
                eur("0.00"), eur("0.00"), eur("0.00"), 1L);
        SozialFall kind2 = new SozialFall(3, 1, 6, false, false, eur("0.00"), eur("0.00"),
                eur("0.00"), eur("0.00"), eur("0.00"), 1L);
        var ergebnis = Leistungsberechnung.berechne(1, List.of(mutter, kind1, kind2));
        assertEquals(0, ergebnis.zahlbetrag().compareTo(eur("2189.68")), ergebnis.zahlbetrag().toPlainString());
    }

    /**
     * Fall 5: Bedarf 1.123,00 − anrechenbar 568,50 = 554,50.
     * Anrechenbar: 1.200 Erwerb − 350 Absetzungen (§ 82 Abs. 2) − 281,50 gekappter Freibetrag.
     */
    @Test
    void fall5_ErwerbMitKappung() {
        SozialFall fall = new SozialFall(1, 1, 1, false, false, 0, 0, false,
                eur("400.00"), eur("70.00"), eur("90.00"), eur("0.00"), eur("1200.00"),
                eur("150.00"), eur("200.00"), eur("0.00"), eur("0.00"), 1L);
        var ergebnis = Leistungsberechnung.berechne(1, List.of(fall));
        assertEquals(0, ergebnis.anrechenbaresEinkommen().compareTo(eur("568.50")),
                ergebnis.anrechenbaresEinkommen().toPlainString());
        assertEquals(0, ergebnis.zahlbetrag().compareTo(eur("554.50")), ergebnis.zahlbetrag().toPlainString());
    }

    /** Fall 6: Vermögen 10.500 → verwertbar 500 über Schonvermögen (§ 90 Abs. 2 Nr. 9): 863 − 500 = 363,00. */
    @Test
    void fall6_Vermoegen() {
        SozialFall fall = new SozialFall(1, 1, 1, false, false, eur("300.00"), eur("0.00"),
                eur("0.00"), eur("0.00"), eur("0.00"), 1L);
        var ergebnis = Leistungsberechnung.berechne(1, List.of(fall),
                Leistungsberechnung.KduParameter.AUS, eur("10500.00"));
        assertEquals(0, ergebnis.zahlbetrag().compareTo(eur("363.00")), ergebnis.zahlbetrag().toPlainString());
    }

    /** Fall 8 (korrigierte Miete): RBS 1 + 250,00 KdU = 813,00 EUR. */
    @Test
    void fall8_Korrektur() {
        SozialFall fall = new SozialFall(1, 1, 1, false, false, eur("250.00"), eur("0.00"),
                eur("0.00"), eur("0.00"), eur("0.00"), 1L);
        assertEquals(0, Leistungsberechnung.berechne(1, List.of(fall)).zahlbetrag()
                .compareTo(eur("813.00")));
    }

    /** Fall 10: RBS 1 + 180,00 KdU = 743,00 EUR. */
    @Test
    void fall10_Lebenszyklus() {
        SozialFall fall = new SozialFall(1, 1, 1, false, false, eur("180.00"), eur("0.00"),
                eur("0.00"), eur("0.00"), eur("0.00"), 1L);
        assertEquals(0, Leistungsberechnung.berechne(1, List.of(fall)).zahlbetrag()
                .compareTo(eur("743.00")));
    }

    /** § 30 Einzel-Mehrbedarfe (handgerechnet): Basis des Alleinerziehenden-Satzes ist IMMER die RBS 1. */
    @Test
    void mehrbedarfsdeckel_Abs6() {
        // Stufe 6 (357,00), ein minderjähriges Kind: G 60,69 + Schwangere 60,69
        // + Alleinerziehende 12 % der RBS 1 (563,00) = 67,56 → Summe 188,94
        // (unter dem Deckel des Regelbedarfs der Stufe 6)
        SozialFall fall = new SozialFall(1, 1, 6, true, true, 0, 1, false,
                eur("0.00"), eur("0.00"), eur("0.00"), eur("0.00"), eur("0.00"),
                eur("0.00"), eur("0.00"), eur("0.00"), eur("0.00"), 1L);
        var fb = Leistungsberechnung.berechneFall(fall);
        assertEquals(0, fb.mehrbedarf().compareTo(eur("188.94")), fb.mehrbedarf().toPlainString());
    }
}
