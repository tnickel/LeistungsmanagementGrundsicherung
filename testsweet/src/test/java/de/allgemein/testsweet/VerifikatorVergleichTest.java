package de.allgemein.testsweet;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class VerifikatorVergleichTest {
    private final Verifikator verifikator = new Verifikator("", "", "");

    @Test void ganzzahlvergleichVerwirftKeineNachkommastellen() {
        var erwartung = Erwartung.ganzeZahl("Version", "", c -> List.of(), 2);
        assertTrue(verifikator.vergleiche(erwartung, "2"));
        assertTrue(verifikator.vergleiche(erwartung, "2.00"));
        assertFalse(verifikator.vergleiche(erwartung, "2.9"));
        assertFalse(verifikator.vergleiche(erwartung, "1.99999999999999999999"));
        assertFalse(verifikator.vergleiche(erwartung, "<keine Zeile>"));
    }

    @Test void betraegeBleibenSkalenunabhaengigAberNumerischExakt() {
        var erwartung = Erwartung.betrag("Betrag", "", c -> List.of(), "6.00");
        assertTrue(verifikator.vergleiche(erwartung, "6"));
        assertFalse(verifikator.vergleiche(erwartung, "6.01"));
    }
}
