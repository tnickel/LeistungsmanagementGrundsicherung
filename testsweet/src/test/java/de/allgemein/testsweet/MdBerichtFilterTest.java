package de.allgemein.testsweet;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prüft den Passwort-Filter der maschinenlesbaren Dokumentation (CR-002):
 * In der ids-Zeile von testdokumentation.md dürfen weder Passwort-SCHLÜSSEL
 * noch Passwort-WERTE auftauchen; alle fachlichen Kontext-Einträge
 * (bgId, mitarbeiter, kollege …) müssen erhalten bleiben.
 */
class MdBerichtFilterTest {

    @Test
    void passwortSchluesselUndWerteWerdenAusgeschlossen() {
        Auftrag auftrag = new Auftrag(1, "STANDARDRENTE", "SWEET-0001", "Test, Toni",
                "Passwort-Leak-Prüfung", List.of(), List.of());
        Map<String, Object> kontext = new HashMap<>();
        kontext.put("bgId", 4711L);
        kontext.put("zahlungId", 99L);
        kontext.put("mitarbeiter", "sb-sim-01");
        kontext.put("kollege", "sb-sim-02");
        kontext.put("passwort", "geheim-1");
        kontext.put("kollegePasswort", "geheim-2");
        kontext.put("adminPasswort", "geheim-3");
        kontext.put("AdminPasswortCase", "geheim-4");
        auftrag.kontext = Map.copyOf(kontext);

        String zeile = MdBericht.kontextZeile(auftrag.kontext);

        // Weder Schlüssel noch Werte dürfen auftauchen (Groß-/Kleinschreibung egal)
        assertFalse(zeile.toLowerCase().contains("passwort"));
        assertFalse(zeile.contains("geheim"));
        // Fachliche Einträge bleiben vollständig erhalten
        assertTrue(zeile.contains("bgId=4711"));
        assertTrue(zeile.contains("zahlungId=99"));
        assertTrue(zeile.contains("mitarbeiter=sb-sim-01"));
        assertTrue(zeile.contains("kollege=sb-sim-02"));
    }

    @Test
    void kontextOhnePasswoerterBleibtVollstaendig() {
        Map<String, Object> kontext = new HashMap<>();
        kontext.put("bgId", 8L);
        kontext.put("mitarbeiter", "sb-web-01");
        kontext.put("vorgangErstellt", true);

        String zeile = MdBericht.kontextZeile(kontext);

        assertTrue(zeile.contains("bgId=8"));
        assertTrue(zeile.contains("mitarbeiter=sb-web-01"));
        assertTrue(zeile.contains("vorgangErstellt=true"));
        assertFalse(zeile.isBlank());
    }
}
