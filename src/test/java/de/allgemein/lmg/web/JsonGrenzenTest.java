package de.allgemein.lmg.web;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Härtungstests des eigenen JSON-Parsers (Codereview CR-006): Grenzen, die vor
 * dem Fix nicht getestet waren — Exponenten-/Ziffern-Bomben (OutOfMemoryError),
 * Tiefenbombe und präzise Zahl-Rundreise.
 */
class JsonGrenzenTest {

    @Test
    void exponentJenseits999WirdAbgewiesen() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> Json.parse("{\"startBudget\":1e2000000000}"));
        assertTrue(e.getMessage().contains("Exponent"));
    }

    @Test
    void negativerRiesenExponentWirdAbgewiesen() {
        assertThrows(IllegalArgumentException.class, () -> Json.parse("[1e-100000]"));
    }

    @Test
    void exponentInnerhalbDerGrenzeBleibtLesbar() {
        Object wert = Json.parse("[1e18]");
        assertEquals(java.util.List.of(new BigDecimal("1E+18")), wert);
    }

    @Test
    void zahlMitMehrAls64ZiffernWirdAbgewiesen() {
        StringBuilder zahl = new StringBuilder("1");
        zahl.repeat("2345678901", 7); // 71 Ziffern
        assertThrows(IllegalArgumentException.class, () -> Json.parse("[" + zahl + "]"));
    }

    @Test
    void tiefenbombeUeber128EbenenWirdAbgewiesen() {
        StringBuilder bombe = new StringBuilder();
        bombe.repeat("[", 130);
        bombe.repeat("]", 130);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> Json.parse(bombe.toString()));
        assertTrue(e.getMessage().contains("tief"));
    }

    @Test
    void untermierteEscapeFolgeWirdAbgewiesen() {
        assertThrows(IllegalArgumentException.class, () -> Json.parse("\"\\u12\""));
    }

    @Test
    void betragBleibtBigDecimalExakt() {
        Object wert = Json.parse("{\"b\":1053.00}");
        assertEquals(new BigDecimal("1053.00"), ((java.util.Map<?, ?>) wert).get("b"));
    }
}
