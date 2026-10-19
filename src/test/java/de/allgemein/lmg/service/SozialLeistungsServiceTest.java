package de.allgemein.lmg.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Grenzfälle der centgenauen Betrags-Normierung (NUMBER(10,2)).
 */
class SozialLeistungsServiceTest {

    private static BigDecimal eur(String wert) {
        return new BigDecimal(wert);
    }

    @Test
    void nullUndNullWerdenNormiert() {
        assertEquals(eur("0.00"), SozialLeistungsService.normiereBetrag(null, "Betrag"));
        assertEquals(eur("0.00"), SozialLeistungsService.normiereBetrag(eur("0"), "Betrag"));
    }

    @Test
    void maximaleVorkommastellenSindErlaubt() {
        assertEquals(eur("99999999.99"),
                SozialLeistungsService.normiereBetrag(eur("99999999.99"), "Betrag"));
        // Zusätzliche Nullstellen verändern den Betrag nicht.
        assertEquals(eur("99999999.99"),
                SozialLeistungsService.normiereBetrag(eur("99999999.9900"), "Betrag"));
    }

    @Test
    void subcentbetragAnDerObergrenzeWirdFachlichAbgewiesen() {
        var ausnahme = assertThrows(FachlicheException.class,
                () -> SozialLeistungsService.normiereBetrag(eur("99999999.999"), "Betrag"));
        assertEquals(FachlicheException.VALIDATION, ausnahme.code());
    }

    @Test
    void zuGrosseBetraegeWerdenFachlichAbgewiesen() {
        var ausnahme = assertThrows(FachlicheException.class,
                () -> SozialLeistungsService.normiereBetrag(eur("100000000.00"), "Betrag"));
        assertEquals(FachlicheException.VALIDATION, ausnahme.code());
    }

    @Test
    void negativeBetraegeWerdenAbgewiesen() {
        var ausnahme = assertThrows(FachlicheException.class,
                () -> SozialLeistungsService.normiereBetrag(eur("-0.01"), "Betrag"));
        assertEquals(FachlicheException.VALIDATION, ausnahme.code());
    }

    @Test
    void centgenaueWerteWerdenVerlustfreiNormiertUndSubcentwerteAbgewiesen() {
        assertEquals(eur("10.55"),
                SozialLeistungsService.normiereBetrag(eur("10.5500"), "Betrag"));
        for (String wert : java.util.List.of("10.555", "10.554", "12.345", "0.001")) {
            var ausnahme = assertThrows(FachlicheException.class,
                    () -> SozialLeistungsService.normiereBetrag(eur(wert), "Kaltmiete"));
            assertEquals(FachlicheException.VALIDATION, ausnahme.code());
        }
    }

    // ------------------------------------------------------------------
    // § 44 Abs. 3 SGB XII: Bewilligungszeitraum höchstens zwölf Monate (Regelfall)
    // ------------------------------------------------------------------

    @Test
    void bewilligungszeitraumBisZwoelfMonateIstErlaubt() {
        assertDoesNotThrow(() -> SozialLeistungsService.pruefeBewilligungszeitraum("2026-01", "2026-12"));
        assertDoesNotThrow(() -> SozialLeistungsService.pruefeBewilligungszeitraum("2026-01", "2026-01"));
        // Jahreswechsel mit genau zwölf Monaten bleibt erlaubt
        assertDoesNotThrow(() -> SozialLeistungsService.pruefeBewilligungszeitraum("2025-07", "2026-06"));
    }

    @Test
    void bewilligungszeitraumUeberZwoelfMonateWirdFachlichAbgelehnt() {
        var ausnahme = assertThrows(FachlicheException.class,
                () -> SozialLeistungsService.pruefeBewilligungszeitraum("2026-01", "2027-01"));
        assertEquals(FachlicheException.VALIDATION, ausnahme.code());
    }
}
