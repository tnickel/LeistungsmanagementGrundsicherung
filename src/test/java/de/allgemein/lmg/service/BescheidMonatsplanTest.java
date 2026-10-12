package de.allgemein.lmg.service;

import de.allgemein.lmg.calc.*;
import de.allgemein.lmg.model.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class BescheidMonatsplanTest {
    @Test
    void verfuegungUndBerechnungsaufstellungNutzenGespeicherteMonate() throws Exception {
        BigDecimal zero = new BigDecimal("0.00");
        var fall = new SozialFall(1, 7, 1, false, true, 0, 0, 0, false,
                new BigDecimal("300.00"), zero, zero, zero, zero, zero, zero, zero, zero, "2026-10", 1);
        var plan = Bewilligungsplan.erstelle(7, List.of(fall), Leistungsberechnung.KduParameter.AUS,
                new BigDecimal("10500.00"), "2026-10", "2026-11", monat -> Satzstand.REFERENZ_2026);
        var bescheid = new Bescheid(1, 7, "B-REVIEW", LocalDate.of(2026, 10, 12),
                "2026-10", "2026-11", new BigDecimal("458.71"), "ERTEILT", "sb-01", Instant.EPOCH,
                BerechnungsSnapshot.schreibe(plan));
        var bg = new Bedarfsgemeinschaft(7, "BG-REVIEW", "Testperson", true,
                new BigDecimal("5000.00"), null, 1, null, null, null, new BigDecimal("10500.00"));
        // Ein späterer aktueller Datenstand darf die im Snapshot fixierten Monate nicht ändern.
        var fremdeBerechnung = Leistungsberechnung.berechne(7, List.of(fall));
        String html = BescheidGenerator.generiereHtml(bg, List.of(fall), fremdeBerechnung, bescheid);
        assertTrue(html.contains("verbindlichen Monatsbeträgen"));
        assertTrue(html.contains("2026-10</td><td class='num'><strong>458,71 EUR"));
        assertTrue(html.contains("2026-11</td><td class='num'><strong>863,00 EUR"));
        assertFalse(html.contains("in Höhe von monatlich"));
        String november = html.substring(html.indexOf("<h3>Berechnung für 2026-11"));
        november = november.substring(0, november.indexOf("</table>"));
        assertFalse(november.contains("95,71"), "November darf keinen Schwangerschaftsmehrbedarf enthalten");
        assertFalse(november.contains("500,00"), "November darf keinen erneuten Vermögenseinsatz enthalten");
        assertTrue(november.contains("863,00"));
    }
}
