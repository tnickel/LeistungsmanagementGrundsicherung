package de.allgemein.lmg.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Formatregel für den Abrechnungszeitraum YYYY-MM. */
class ZahlungsmonatTest {

    @Test
    void gueltigeMonateWerdenAkzeptiert() {
        assertTrue(Zahlungsmonat.isValid("2026-01"));
        assertTrue(Zahlungsmonat.isValid("2026-09"));
        assertTrue(Zahlungsmonat.isValid("2026-12"));
        assertTrue(Zahlungsmonat.isValid(Zahlungsmonat.aktuellerMonat()));
    }

    @Test
    void ungueltigeAngabenWerdenAbgelehnt() {
        assertFalse(Zahlungsmonat.isValid(null));
        assertFalse(Zahlungsmonat.isValid(""));
        assertFalse(Zahlungsmonat.isValid("2026-13"));
        assertFalse(Zahlungsmonat.isValid("2026-00"));
        assertFalse(Zahlungsmonat.isValid("2026-9"));
        assertFalse(Zahlungsmonat.isValid("26-09"));
        assertFalse(Zahlungsmonat.isValid("2026/09"));
    }
}
