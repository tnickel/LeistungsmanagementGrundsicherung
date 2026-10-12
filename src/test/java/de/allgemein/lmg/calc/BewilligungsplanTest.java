package de.allgemein.lmg.calc;

import de.allgemein.lmg.model.SozialFall;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class BewilligungsplanTest {
    private static final BigDecimal ZERO = new BigDecimal("0.00");

    private SozialFall fall(String ende, BigDecimal rente) {
        return new SozialFall(1, 7, 1, false, true, 0, 0, 0, false,
                new BigDecimal("300.00"), ZERO, ZERO, rente, ZERO,
                ZERO, ZERO, ZERO, ZERO, ende, 1);
    }

    @Test
    void verbrauchSchwangerschaftsendeUndJahreswechselWerdenJeMonatBerechnet() throws Exception {
        SozialFall fall = fall("2026-12", ZERO);
        var saetze2027 = new Satzstand("2027-01", new BigDecimal("600.00"),
                new BigDecimal("540.00"), new BigDecimal("480.00"), new BigDecimal("500.00"),
                new BigDecimal("415.00"), new BigDecimal("380.00"));
        var plan = Bewilligungsplan.erstelle(7, List.of(fall), Leistungsberechnung.KduParameter.AUS,
                new BigDecimal("10500.00"), "2026-12", "2027-02",
                monat -> monat.compareTo("2027-01") < 0 ? Satzstand.REFERENZ_2026 : saetze2027);
        // Dezember: 563 + 95,71 + 300 - 500. Januar: 600 + 300, Schwangerschaft beendet.
        assertEquals(new BigDecimal("458.71"), plan.monate().get(0).ergebnis().zahlbetrag());
        assertEquals(new BigDecimal("900.00"), plan.monate().get(1).ergebnis().zahlbetrag());
        assertEquals(new BigDecimal("900.00"), plan.monate().get(2).ergebnis().zahlbetrag());
        assertEquals(new BigDecimal("10500.00"), plan.ersterMonat().vermoegen());
        assertEquals(new BigDecimal("10000.00"), plan.monate().get(1).vermoegen());
        assertTrue(fall.schwanger(), "Die Monatsprojektion darf gespeicherte Merkmale nicht ändern");
        assertFalse(plan.monate().get(1).faelle().get(0).schwanger());
    }

    @Test
    void unbekannterEntbindungsmonatBeendetMehrbedarfNicht() throws Exception {
        var plan = Bewilligungsplan.erstelle(7, List.of(fall(null, ZERO)), Leistungsberechnung.KduParameter.AUS,
                ZERO, "2026-10", "2026-11", monat -> Satzstand.REFERENZ_2026);
        assertEquals(new BigDecimal("958.71"), plan.monate().get(0).ergebnis().zahlbetrag());
        assertEquals(new BigDecimal("958.71"), plan.monate().get(1).ergebnis().zahlbetrag());
    }

    @Test
    void endeDerSchwangerschaftBeendetNichtDenGrundanspruch() throws Exception {
        var plan = Bewilligungsplan.erstelle(7, List.of(fall("2026-09", ZERO)), Leistungsberechnung.KduParameter.AUS,
                ZERO, "2026-10", "2026-10", monat -> Satzstand.REFERENZ_2026);
        assertEquals(new BigDecimal("863.00"), plan.ersterMonat().ergebnis().zahlbetrag());
    }

    @Test
    void keinNegativerBewilligungsbetragUndKeinFiktiverEinsatzOhneZahlung() throws Exception {
        var plan = Bewilligungsplan.erstelle(7, List.of(fall("2026-10", ZERO)), Leistungsberechnung.KduParameter.AUS,
                new BigDecimal("12000.00"), "2026-10", "2026-11", monat -> Satzstand.REFERENZ_2026);
        assertEquals(ZERO, plan.ersterMonat().ergebnis().zahlbetrag());
        assertEquals(ZERO, plan.monate().get(1).ergebnis().zahlbetrag());
        assertEquals(new BigDecimal("12000.00"), plan.monate().get(1).vermoegen());
    }

    @Test
    void mehrbedarfKannEndenOhneNegativenFolgebetrag() throws Exception {
        var plan = Bewilligungsplan.erstelle(7, List.of(fall("2026-10", new BigDecimal("900.00"))),
                Leistungsberechnung.KduParameter.AUS, ZERO, "2026-10", "2026-11", monat -> Satzstand.REFERENZ_2026);
        assertEquals(new BigDecimal("58.71"), plan.ersterMonat().ergebnis().zahlbetrag());
        assertEquals(ZERO, plan.monate().get(1).ergebnis().zahlbetrag());
    }
}
