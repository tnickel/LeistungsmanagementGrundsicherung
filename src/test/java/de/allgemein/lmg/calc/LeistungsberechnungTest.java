package de.allgemein.lmg.calc;

import de.allgemein.lmg.model.SozialFall;
import de.allgemein.lmg.service.FachlicheException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    // ------------------------------------------------------------------
    // § 30 Abs. 7 SGB XII: Warmwasser-Mehrbedarf stufenabhängig (seit 2024)
    // ------------------------------------------------------------------

    @Test
    void warmwasserStufe4_ergibt1Komma4Prozent() {
        // 471,00 × 1,4 % = 6,59 EUR
        var f = vollerFall(4, false, false, 0, 0, true, "0", "0", "0", "0", "0");
        assertEquals(eur("6.59"), Leistungsberechnung.berechneFall(f).mehrbedarfWarmwasser());
    }

    @Test
    void warmwasserStufe5_ergibt1Komma2Prozent() {
        // 390,00 × 1,2 % = 4,68 EUR
        var f = vollerFall(5, false, false, 0, 0, true, "0", "0", "0", "0", "0");
        assertEquals(eur("4.68"), Leistungsberechnung.berechneFall(f).mehrbedarfWarmwasser());
    }

    @Test
    void warmwasserStufe6_ergibt0Komma8Prozent() {
        // 357,00 × 0,8 % = 2,86 EUR
        var f = vollerFall(6, false, false, 0, 0, true, "0", "0", "0", "0", "0");
        assertEquals(eur("2.86"), Leistungsberechnung.berechneFall(f).mehrbedarfWarmwasser());
    }

    @Test
    void warmwasserStufe3_gesetzOhneSatz_ergibtNull() {
        var f = vollerFall(3, false, false, 0, 0, true, "0", "0", "0", "0", "0");
        assertEquals(eur("0.00"), Leistungsberechnung.berechneFall(f).mehrbedarfWarmwasser());
    }

    // ------------------------------------------------------------------
    // § 30 Abs. 2 SGB XII: Schwangeren-Mehrbedarf (17 % ab 12. SSW)
    // ------------------------------------------------------------------

    @Test
    void schwangereRbs1_ergibt95Komma71() {
        var f = vollerFall(1, false, true, 0, 0, false, "0", "0", "0", "0", "0");
        var ergebnis = Leistungsberechnung.berechneFall(f);
        assertEquals(eur("95.71"), ergebnis.mehrbedarfSchwangere());
        assertEquals(eur("95.71"), ergebnis.mehrbedarf());
    }

    // ------------------------------------------------------------------
    // § 30 Abs. 3 SGB XII: Alleinerziehenden-Mehrbedarf (36 % / 12 %, Deckel 60 % RBS 1)
    // ------------------------------------------------------------------

    @Test
    void alleinerziehendKindUnter7_ergibt36Prozent() {
        // 563,00 × 36 % = 202,68 EUR
        var f = vollerFall(1, false, false, 1, 1, false, "0", "0", "0", "0", "0");
        assertEquals(eur("202.68"), Leistungsberechnung.berechneFall(f).mehrbedarfAlleinerziehend());
    }

    @Test
    void alleinerziehendVierKinderUnter16_ergibt12ProzentJeKind() {
        // 563,00 × (4 × 12 %) = 270,24 EUR (Konstellation 2–3 Kinder nicht erfüllt)
        var f = vollerFall(1, false, false, 0, 4, false, "0", "0", "0", "0", "0");
        assertEquals(eur("270.24"), Leistungsberechnung.berechneFall(f).mehrbedarfAlleinerziehend());
    }

    @Test
    void alleinerziehendDeckel60ProzentDerRbs1() {
        // 5 Kinder × 12 % = 60 % — am Deckel (337,80 EUR); 8 Kinder × 12 % = 96 % gekappt
        var amDeckel = vollerFall(1, false, false, 0, 5, false, "0", "0", "0", "0", "0");
        assertEquals(eur("337.80"), Leistungsberechnung.berechneFall(amDeckel).mehrbedarfAlleinerziehend());
        var ueberDeckel = vollerFall(1, false, false, 0, 8, false, "0", "0", "0", "0", "0");
        assertEquals(eur("337.80"), Leistungsberechnung.berechneFall(ueberDeckel).mehrbedarfAlleinerziehend());
    }

    // ------------------------------------------------------------------
    // § 30 Abs. 6 SGB XII: Obergrenze — Mehrbedarfe höchstens der Regelbedarf
    // ------------------------------------------------------------------

    @Test
    void mehrbedarfeWerdenAufRegelbedarfGekappt() {
        // G (95,71) + Schwangere (95,71) + Alleinerziehend 36 % (202,68) + Warmwasser (12,95)
        // = 407,05 < 563 — nicht gekappt; mit 8 Kindern (337,80) = 542,47 < 563; erst
        // extreme Konstellationen kappen. Hier prüfen wir die Kappung über den Deckel:
        // G + Schwangere + Alleinerziehend(8 Kinder, am Deckel 337,80) + Warmwasser = 542,47.
        var f = vollerFall(1, true, true, 0, 8, true, "0", "0", "0", "0", "0");
        var ergebnis = Leistungsberechnung.berechneFall(f);
        assertEquals(eur("542.17"), ergebnis.mehrbedarf());
        // Stufe 6: G 60,69 + Schwangere 60,69 + AE am Deckel 337,80 + Warmwasser 2,86
        // = 462,04 > 357,00 → Kappung auf den Regelbedarf
        var klein = vollerFall(6, true, true, 0, 8, true, "0", "0", "0", "0", "0");
        assertEquals(eur("357.00"), Leistungsberechnung.berechneFall(klein).mehrbedarf());
    }

    // ------------------------------------------------------------------
    // § 82 Abs. 2 SGB XII: Absetzbeträge (Steuern, SV, Versicherungen, Erwerbsaufwendungen)
    // ------------------------------------------------------------------

    @Test
    void absetzbetaetragMindertAnrechenbaresEinkommen() {
        // Erwerb 800: Freibetrag 240, Abzüge 100 + 50 + 20 + 10 = 180 → 800 − 240 − 180 = 380
        var f = vollerFall(1, false, false, 0, 0, false, "0", "0", "0", "0", "800.00",
                "100.00", "50.00", "20.00", "10.00");
        var ergebnis = Leistungsberechnung.berechneFall(f);
        assertEquals(eur("180.00"), ergebnis.absetzbetaetrag());
        assertEquals(eur("380.00"), ergebnis.anrechenbaresEinkommen());
    }

    @Test
    void absetzbetaetragFuehrtNichtZuNegativemAnrechenbarenEinkommen() {
        // Abzüge (500) übersteigen Einkommen nach Freibetrag (800 − 240 = 560) nicht —
        // hier: Abzüge 600 > 560 → anrechenbar bleibt 0
        var f = vollerFall(1, false, false, 0, 0, false, "0", "0", "0", "0", "800.00",
                "600.00", "0", "0", "0");
        assertEquals(eur("0.00"), Leistungsberechnung.berechneFall(f).anrechenbaresEinkommen());
    }

    // ------------------------------------------------------------------
    // § 42a i.V.m. § 35 SGB XII: KdU-Warmwasser-Abzug und Angemessenheitsgrenze
    // ------------------------------------------------------------------

    @Test
    void warmwasserAbzugVonHeizkostenMitOrtlicherQuote() {
        // Heizung 100 EUR, örtlicher Warmwasseranteil 10 % → KdU-Seite −10 EUR;
        // der Warmwasser-Mehrbedarf (12,95) bleibt daneben (§ 30 Abs. 7)
        var f = vollerFall(1, false, false, 0, 0, true, "400", "0", "100", "0", "0");
        var ergebnis = Leistungsberechnung.berechneFall(f,
                new Leistungsberechnung.KduParameter(new BigDecimal("0.10"), BigDecimal.ZERO));
        assertEquals(eur("10.00"), ergebnis.kduWarmwasserAbzug());
        assertEquals(eur("490.00"), ergebnis.kdu());
    }

    @Test
    void kduUeberAngemessenheitsgrenzeWirdGekapptUndGekennzeichnet() {
        var f = vollerFall(1, false, false, 0, 0, false, "500", "70", "80", "0", "0");
        var ergebnis = Leistungsberechnung.berechneFall(f,
                new Leistungsberechnung.KduParameter(BigDecimal.ZERO, new BigDecimal("400.00")));
        assertTrue(ergebnis.kduUnangemessen());
        assertEquals(eur("400.00"), ergebnis.kdu());
    }

    @Test
    void kduUnterGrenzeBleibtUnangetastet() {
        var f = vollerFall(1, false, false, 0, 0, false, "300", "50", "50", "0", "0");
        var ergebnis = Leistungsberechnung.berechneFall(f,
                new Leistungsberechnung.KduParameter(BigDecimal.ZERO, new BigDecimal("400.00")));
        assertFalse(ergebnis.kduUnangemessen());
        assertEquals(eur("400.00"), ergebnis.kdu());
    }

    // ------------------------------------------------------------------
    // § 43 Abs. 3, § 90 SGB XII: Vermögensanrechnung mit Schonvermögen
    // ------------------------------------------------------------------

    @Test
    void verwertbaresVermoegenUeberSchonvermoegenMindertZahlbetrag() {
        // Bedarf 563,00; Vermögen 10.500 − 10.000 Schonvermögen = 500 → Zahlbetrag 63,00
        var f = vollerFall(1, false, false, 0, 0, false, "0", "0", "0", "0", "0");
        var ergebnis = Leistungsberechnung.berechne(1L, List.of(f),
                Leistungsberechnung.KduParameter.AUS, eur("10500.00"), 1);
        assertEquals(eur("500.00"), ergebnis.verwertbaresVermoegen());
        assertEquals(eur("63.00"), ergebnis.zahlbetrag());
    }

    @Test
    void schoenvermoegenJeWeiteremMitglied() {
        // 2 Mitglieder: Schonvermögen 10.000 + 750 = 10.750; Vermögen 11.000 → 250 verwertbar
        var f = vollerFall(1, false, false, 0, 0, false, "0", "0", "0", "0", "0");
        var ergebnis = Leistungsberechnung.berechne(1L, List.of(f),
                Leistungsberechnung.KduParameter.AUS, eur("11000.00"), 2);
        assertEquals(eur("250.00"), ergebnis.verwertbaresVermoegen());
    }

    @Test
    void vermoegenUnterSchonvermoegenWirdNichtAngerechnet() {
        var f = vollerFall(1, false, false, 0, 0, false, "0", "0", "0", "0", "0");
        var ergebnis = Leistungsberechnung.berechne(1L, List.of(f),
                Leistungsberechnung.KduParameter.AUS, eur("9000.00"), 1);
        assertEquals(eur("0.00"), ergebnis.verwertbaresVermoegen());
        assertEquals(eur("563.00"), ergebnis.zahlbetrag());
    }

    // ------------------------------------------------------------------
    // Hilfskonstruktion des vollen SozialFall (alle neuen Felder)
    // ------------------------------------------------------------------

    private static SozialFall vollerFall(int rbs, boolean merkzeichenG, boolean schwanger,
            int kinderU7, int kinderU16, boolean warmwasser,
            String miete, String betriebskosten, String heizung, String rente, String erwerb) {
        return vollerFall(rbs, merkzeichenG, schwanger, kinderU7, kinderU16, warmwasser,
                miete, betriebskosten, heizung, rente, erwerb, "0", "0", "0", "0");
    }

    private static SozialFall vollerFall(int rbs, boolean merkzeichenG, boolean schwanger,
            int kinderU7, int kinderU16, boolean warmwasser,
            String miete, String betriebskosten, String heizung, String rente, String erwerb,
            String abzSteuern, String abzSv, String abzVers, String abzErwerb) {
        return new SozialFall(1L, 1L, rbs, merkzeichenG, schwanger, kinderU7, kinderU16,
                warmwasser, eur(miete), eur(betriebskosten), eur(heizung),
                eur(rente), eur(erwerb), eur(abzSteuern), eur(abzSv), eur(abzVers), eur(abzErwerb), 1L);
    }
}
