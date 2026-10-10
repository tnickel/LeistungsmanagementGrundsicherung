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

    @Test
    void extremjahreWerdenAbgewiesen() {
        // Fachlich unsinnige Jahre außerhalb des Fensters 1900–2099
        assertFalse(Zahlungsmonat.isValid("0000-01"));
        assertFalse(Zahlungsmonat.isValid("1899-12"));
        assertFalse(Zahlungsmonat.isValid("2100-01"));
        assertFalse(Zahlungsmonat.isValid("9999-12"));
    }

    @Test
    void jahresfensterGrenzenWerdenAkzeptiert() {
        assertTrue(Zahlungsmonat.isValid("1900-01"));
        assertTrue(Zahlungsmonat.isValid("2099-12"));
    }
}
