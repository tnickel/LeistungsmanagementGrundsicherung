package de.allgemein.lmg.calc;

import de.allgemein.lmg.model.SozialFall;
import de.allgemein.lmg.service.FachlicheException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Prüft die SGB-XII-Regeln mit den Referenzwerten 2026 (Stufe 1: 563 EUR,
 * Stufe 2: 506 EUR, Merkzeichen G: 17 %, Erwerbsfreibetrag: 30 %, gekappt
 * bei 281,50 EUR). Diese Werte stammen aus der Blueprint-Spezifikation.
 */
class LeistungsberechnungTest {

    private static BigDecimal eur(String wert) {
        return new BigDecimal(wert);
    }

    private static SozialFall fall(int rbs, boolean merkzeichenG, String miete, String heizung,
                                   String rente, String erwerb) {
        return new SozialFall(1L, 1L, rbs, merkzeichenG, eur(miete), eur(heizung),
                eur(rente), eur(erwerb), 1L);
    }

    @Test
    void rbs1MitMerkzeichenG_ergibtMehrbedarf95Komma71() {
        var ergebnis = Leistungsberechnung.berechneFall(
                fall(1, true, "450.00", "90.00", "0", "0"));
        assertEquals(eur("563.00"), ergebnis.regelbedarf());
        assertEquals(eur("95.71"), ergebnis.mehrbedarf());
        assertEquals(eur("540.00"), ergebnis.kdu());
        assertEquals(eur("1198.71"), ergebnis.gesamtbedarf());
        assertEquals(eur("1198.71"), ergebnis.zahlbetrag());
    }

    @Test
    void renteWirdVollAngerechnet() {
        var ergebnis = Leistungsberechnung.berechneFall(
                fall(2, false, "620.00", "110.00", "400.00", "0"));
        assertEquals(eur("1236.00"), ergebnis.gesamtbedarf());
        assertEquals(eur("400.00"), ergebnis.anrechenbaresEinkommen());
        assertEquals(eur("836.00"), ergebnis.zahlbetrag());
    }

    @Test
    void erwerbsfreibetragWirdBei281Komma50Gekappt() {
        var ergebnis = Leistungsberechnung.berechneFall(
                fall(1, false, "380.00", "80.00", "0", "1500.00"));
        assertEquals(eur("281.50"), ergebnis.freibetragErwerb());
        assertEquals(eur("1218.50"), ergebnis.anrechenbaresEinkommen());
        assertEquals(eur("-195.50"), ergebnis.zahlbetrag());
    }

    @Test
    void erwerbsfreibetragBleibtUnterDerKappungsgrenze() {
        var ergebnis = Leistungsberechnung.berechneFall(
                fall(1, false, "400.00", "85.00", "0", "800.00"));
        assertEquals(eur("240.00"), ergebnis.freibetragErwerb());
        assertEquals(eur("560.00"), ergebnis.anrechenbaresEinkommen());
        assertEquals(eur("488.00"), ergebnis.zahlbetrag());
    }

    @Test
    void freibetragRundetHandelsueblich() {
        // 30 % von 333,33 = 99,999 -> 100,00 (HALF_UP)
        var ergebnis = Leistungsberechnung.berechneFall(
                fall(1, false, "0", "0", "0", "333.33"));
        assertEquals(eur("100.00"), ergebnis.freibetragErwerb());
    }

    @Test
    void mehrbedarfBeiRbs2Ist86Komma02() {
        var ergebnis = Leistungsberechnung.berechneFall(
                fall(2, true, "500.00", "100.00", "0", "0"));
        assertEquals(eur("86.02"), ergebnis.mehrbedarf());
        assertEquals(eur("1192.02"), ergebnis.gesamtbedarf());
    }

    @Test
    void zahlbetragWirdUeberSozialFaelleSummiert() {
        var fallA = new SozialFall(5L, 5L, 2, true, eur("500.00"), eur("100.00"), eur("0"), eur("0"), 1L);
        var fallB = new SozialFall(6L, 5L, 2, false, eur("0"), eur("0"), eur("0"), eur("0"), 1L);
        var ergebnis = Leistungsberechnung.berechne(5L, List.of(fallA, fallB));
        assertEquals(eur("1698.02"), ergebnis.gesamtbedarf());
        assertEquals(2, ergebnis.faelle().size());
        assertEquals(eur("1698.02"), ergebnis.zahlbetrag());
    }

    @Test
    void regelbedarfsstufen3Bis6_habenKorrekteSaetze2026() {
        assertEquals(eur("451.00"), Leistungsberechnung.berechneFall(fall(3, false, "0", "0", "0", "0")).regelbedarf());
        assertEquals(eur("471.00"), Leistungsberechnung.berechneFall(fall(4, false, "0", "0", "0", "0")).regelbedarf());
        assertEquals(eur("390.00"), Leistungsberechnung.berechneFall(fall(5, false, "0", "0", "0", "0")).regelbedarf());
        assertEquals(eur("357.00"), Leistungsberechnung.berechneFall(fall(6, false, "0", "0", "0", "0")).regelbedarf());
    }

    @Test
    void unbekannteRegelbedarfsstufeBrichtFachlichAb() {
        var ausnahme = assertThrows(FachlicheException.class,
                () -> Leistungsberechnung.berechneFall(fall(7, false, "0", "0", "0", "0")));
        assertEquals(FachlicheException.RBS_NICHT_KONFIGURIERT, ausnahme.code());
    }

    @Test
    void dezentraleWarmwassererzeugungRBS1_ergibt12Komma95() {
        var f = new SozialFall(1L, 1L, 1, false, true, eur("0"), eur("0"), eur("0"), eur("0"), eur("0"), 1L);
        var ergebnis = Leistungsberechnung.berechneFall(f);
        assertEquals(eur("12.95"), ergebnis.mehrbedarfWarmwasser());
        assertEquals(eur("0.00"), ergebnis.mehrbedarfG());
        assertEquals(eur("12.95"), ergebnis.mehrbedarf());
        assertEquals(eur("575.95"), ergebnis.gesamtbedarf());
    }

    @Test
    void dezentraleWarmwassererzeugungRBS2_ergibt11Komma64() {
        var f = new SozialFall(1L, 1L, 2, false, true, eur("0"), eur("0"), eur("0"), eur("0"), eur("0"), 1L);
        var ergebnis = Leistungsberechnung.berechneFall(f);
        assertEquals(eur("11.64"), ergebnis.mehrbedarfWarmwasser());
        assertEquals(eur("517.64"), ergebnis.gesamtbedarf());
    }

    @Test
    void kombinationMerkzeichenG_undWarmwasser_addiertBeideMehrbedarfe() {
        var f = new SozialFall(1L, 1L, 1, true, true, eur("0"), eur("0"), eur("0"), eur("0"), eur("0"), 1L);
        var ergebnis = Leistungsberechnung.berechneFall(f);
        assertEquals(eur("95.71"), ergebnis.mehrbedarfG());
        assertEquals(eur("12.95"), ergebnis.mehrbedarfWarmwasser());
        assertEquals(eur("108.66"), ergebnis.mehrbedarf());
        assertEquals(eur("671.66"), ergebnis.gesamtbedarf());
    }

    @Test
    void kduDreiteilung_summiertMieteBetriebskostenHeizung() {
        var f = new SozialFall(1L, 1L, 1, false, false, eur("450.00"), eur("70.00"), eur("90.00"), eur("0"), eur("0"), 1L);
        var ergebnis = Leistungsberechnung.berechneFall(f);
        assertEquals(eur("450.00"), ergebnis.kduMiete());
        assertEquals(eur("70.00"), ergebnis.kduBetriebskosten());
        assertEquals(eur("90.00"), ergebnis.kduHeizung());
        assertEquals(eur("610.00"), ergebnis.kdu());
        assertEquals(eur("1173.00"), ergebnis.gesamtbedarf());
    }

    @Test
    void ohneSozialFallGibtEsKeineBerechnung() {
        var ausnahme = assertThrows(FachlicheException.class,
                () -> Leistungsberechnung.berechne(1L, List.of()));
        assertEquals(FachlicheException.VALIDATION, ausnahme.code());
    }
}
