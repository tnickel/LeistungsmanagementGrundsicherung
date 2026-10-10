package de.allgemein.lmg.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SepaValidatorTest {

    @Test
    void gueltigeIbanWirdAkzeptiert() {
        // Deutsche Bundesbank Test-IBANs
        assertTrue(SepaValidator.isValidIban("DE89 3704 0044 0532 0130 00"));
        assertTrue(SepaValidator.isValidIban("DE89370400440532013000"));
        // Österreich
        assertTrue(SepaValidator.isValidIban("AT61 1904 3002 3457 3201"));
        // Schweiz
        assertTrue(SepaValidator.isValidIban("CH93 0076 2011 6238 5295 7"));
    }

    @Test
    void ungueltigeIbanWirdAbgelehnt() {
        // Falsche Prüfziffer
        assertFalse(SepaValidator.isValidIban("DE88 3704 0044 0532 0130 00"));
        // Falsche Länge für DE
        assertFalse(SepaValidator.isValidIban("DE89 3704 0044 0532 0130 0"));
        // Ungültige Zeichen
        assertFalse(SepaValidator.isValidIban("DE89 3704 0044 0532 0130 0?"));
        // Null oder leer
        assertFalse(SepaValidator.isValidIban(null));
        assertFalse(SepaValidator.isValidIban("   "));
    }

    @Test
    void gueltigerBicWirdAkzeptiert() {
        assertTrue(SepaValidator.isValidBic("MARKDEF1100"));
        assertTrue(SepaValidator.isValidBic("MARKDEF1"));
        assertTrue(SepaValidator.isValidBic("markdef1100"));
    }

    @Test
    void ungueltigerBicWirdAbgelehnt() {
        assertFalse(SepaValidator.isValidBic("MARKDE")); // Zu kurz
        assertFalse(SepaValidator.isValidBic("MARKDEF1100X")); // Zu lang
        assertFalse(SepaValidator.isValidBic(null));
        assertFalse(SepaValidator.isValidBic("   "));
    }

    @Test
    void normalisierungUndFormatierung() {
        assertEquals("DE89370400440532013000",
                SepaValidator.normalisiereIban(" de89 3704 0044 0532 0130 00 "));
        assertEquals("DE89 3704 0044 0532 0130 00",
                SepaValidator.formatiereIban("DE89370400440532013000"));
    }
}
