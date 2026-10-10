package de.allgemein.lmg.service;

import de.allgemein.lmg.calc.Leistungsberechnung;
import de.allgemein.lmg.model.Bedarfsgemeinschaft;
import de.allgemein.lmg.model.Bescheid;
import de.allgemein.lmg.model.SozialFall;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * XSS-Regression für die amtliche Druckansicht: Sämtliche Nutzendaten — auch die
 * zusammengesetzte Bescheidnummer mit Aktenzeichen im &lt;title&gt;-Element — müssen
 * HTML-escaped ausgeliefert werden (</title><script>… darf nicht durchbrechen).
 */
class BescheidGeneratorTest {

    private static BigDecimal eur(String wert) {
        return new BigDecimal(wert);
    }

    @Test
    void bescheidnummerMitTitleBreakoutScriptWirdUeberallEscaped() {
        String boesesAktenzeichen = "BG-</title><script>alert('xss')</script>";
        String boeseNr = "BESCH-" + boesesAktenzeichen + "-2026-1";
        String html = generiere(boeseNr, boesesAktenzeichen);

        // Das Rohtag darf nirgends auftauchen — auch nicht im <title>
        assertFalse(html.contains("<script>alert('xss')</script>"),
                "Unescapetes Script gefunden");
        // Stattdessen die entschärfte Entität
        assertTrue(html.contains("&lt;script&gt;"), "Escapte Variante fehlt");
    }

    @Test
    void hauptpersonNameWirdEscaped() {
        String name = "<img src=x onerror=alert(1)>";
        // Name fließt über die Bedarfsgemeinschaft in Empfänger/Kontoinhaber-Fallback
        Bedarfsgemeinschaft bg = new Bedarfsgemeinschaft(1L, "BG-1", name, true,
                eur("1500.00"), null, 1L, null, null, null, BigDecimal.ZERO);
        String mitName = BescheidGenerator.generiereHtml(bg,
                List.of(fall()), berechnung(), bescheid("BESCH-X"));
        assertFalse(mitName.contains("<img src=x"), "Unescapeter Name gefunden");
        assertTrue(mitName.contains("&lt;img src=x"));
    }

    @Test
    void bescheinhaeltPflichtbestandteile() {
        String html = generiere("BESCH-2026-1", "BG-2026-0001");
        assertTrue(html.contains("Bewilligungsbescheid"));
        assertTrue(html.contains("Datum des Bescheids"));
        assertTrue(html.contains("Rechtsbehelfsbelehrung"));
        assertTrue(html.contains("Widerspruch"));
        assertTrue(html.contains("§ 84 Abs. 1"), "Fundstelle der Widerspruchsfrist fehlt");
        assertTrue(html.contains("§ 87 Abs. 2 SGG"), "Klagefrist nach Widerspruchsbescheid fehlt");
        assertTrue(html.contains("Regelbedarfsstufe 1"), "Regelbedarfsstufe fehlt in der Begründung");
        assertTrue(html.contains("Einkommen (§ 82 SGB XII)"), "Einkommen fehlt in der Begründung");
        assertFalse(html.contains("onclick"), "Inline-Eventhandler widerspricht der CSP");
        assertFalse(html.contains("Musterstadt"), "Hartcodierter Ort");
    }

    @Test
    void ortAnschriftUndGerichtKommenAusDerKonfiguration() {
        Bedarfsgemeinschaft bg = new Bedarfsgemeinschaft(1L, "BG-1", "Meier, Anna", true,
                eur("1500.00"), null, 1L, null, null, null, BigDecimal.ZERO);
        String html = BescheidGenerator.generiereHtml(bg, List.of(fall()), berechnung(), bescheid("B-1"),
                new BescheidGenerator.Rechtsbehelf("WIDERSPRUCH", "Bezirksregierung Nord", "Sozialgericht Nord",
                        "Nordstadt", "Amt Nord, Hauptstraße 1, 11111 Nordstadt"));
        assertTrue(html.contains("Nordstadt, den "));
        assertTrue(html.contains("Amt Nord, Hauptstraße 1, 11111 Nordstadt"));
        assertTrue(html.contains("Bezirksregierung Nord"));
        assertTrue(html.contains("Sozialgericht Nord"));
    }

    @Test
    void klagemodusNenntKeinVorverfahren() {
        Bedarfsgemeinschaft bg = new Bedarfsgemeinschaft(1L, "BG-1", "Meier, Anna", true,
                eur("1500.00"), null, 1L, null, null, null, BigDecimal.ZERO);
        String html = BescheidGenerator.generiereHtml(bg, List.of(fall()), berechnung(), bescheid("B-1"),
                new BescheidGenerator.Rechtsbehelf("KLAGE", null, "Sozialgericht Süd"));
        assertTrue(html.contains("Klage bei Sozialgericht Süd"));
        assertTrue(html.contains("§ 87 Abs. 1 SGG"));
        assertFalse(html.contains("Widerspruch eingelegt"));
    }


    // ------------------------------------------------------------------
    // Aufbau
    // ------------------------------------------------------------------

    private String generiere(String bescheidNr, String aktenzeichen) {
        Bedarfsgemeinschaft bg = new Bedarfsgemeinschaft(1L, aktenzeichen, "Meier, Anna", true,
                eur("1500.00"), null, 1L, null, null, null, BigDecimal.ZERO);
        return BescheidGenerator.generiereHtml(bg, List.of(fall()), berechnung(), bescheid(bescheidNr));
    }

    private SozialFall fall() {
        return new SozialFall(1L, 1L, 1, false, false, 0, 0, false,
                eur("450.00"), eur("0.00"), eur("90.00"), eur("0.00"), eur("0.00"),
                eur("0.00"), eur("0.00"), eur("0.00"), eur("0.00"), 1L);
    }

    private Leistungsberechnung.Berechnungsergebnis berechnung() {
        return Leistungsberechnung.berechne(1L, List.of(fall()));
    }

    private Bescheid bescheid(String nummer) {
        return new Bescheid(1L, 1L, nummer, LocalDate.now(),
                "2026-01", "2026-12", eur("1153.00"), "ERTEILT", "sb-01", Instant.now());
    }
}
