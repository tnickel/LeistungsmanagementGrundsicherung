package de.allgemein.lmg.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Grenzfälle der Betrags-Normierung (NUMBER(10,2)): Die Prüfung muss NACH der
 * Rundung erfolgen — 99.999.999,999 würde sonst durch die Prüfung schlüpfen,
 * auf 100.000.000,00 aufrunden und erst in der DB mit ORA-01438 scheitern.
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
        // rundet auf den erlaubten Höchstwert, nicht darüber
        assertEquals(eur("99999999.99"),
                SozialLeistungsService.normiereBetrag(eur("99999999.994"), "Betrag"));
    }

    @Test
    void rundungUeberDieGrenzeWirdFachlichAbgewiesen() {
        // 99.999.999,999 → HALF_UP → 100.000.000,00 = 9 Vorkommastellen → VALIDATION
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
    void nachkommastellenWerdenAufZweiNormiert() {
        assertEquals(eur("10.56"),
                SozialLeistungsService.normiereBetrag(eur("10.555"), "Betrag"));
        assertEquals(eur("10.55"),
                SozialLeistungsService.normiereBetrag(eur("10.554"), "Betrag"));
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
