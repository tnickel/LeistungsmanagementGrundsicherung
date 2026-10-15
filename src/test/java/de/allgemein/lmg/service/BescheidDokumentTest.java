package de.allgemein.lmg.service;

import de.allgemein.lmg.calc.*;
import de.allgemein.lmg.model.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BescheidDokumentTest {
    @Test void v3DruckBewahrtBankBehoerdeUndBelehrungTrotzHeutigerAenderungen() throws Exception {
        var zero = new BigDecimal("0.00");
        var fall = new SozialFall(1, 7, 1, false, false, zero, zero, zero, zero, zero, 1);
        var plan = Bewilligungsplan.erstelle(7, List.of(fall), Leistungsberechnung.KduParameter.AUS,
                zero, "2026-10", "2026-11", monat -> Satzstand.REFERENZ_2026);
        var bg = new Bedarfsgemeinschaft(7, "BG-DOK", "Originalperson", true, new BigDecimal("5000"),
                null, 1, "DE89370400440532013000", "COBADEFFXXX", "Originalkonto", zero);
        var original = new BescheidGenerator.Rechtsbehelf("WIDERSPRUCH", "Alte Widerspruchsstelle", "Altes Gericht", "Alter Ort", "Altes Amt");
        String snapshot = BescheidDokument.snapshot(plan, bg, 1, "B-DOK", LocalDate.of(2026,10,15),
                "2026-10", "2026-11", "sb-dok", original);
        var gelesen = BerechnungsSnapshot.lese(snapshot);
        assertEquals(2, gelesen.monate().size());
        assertNotNull(gelesen.dokumentHtml());
        var bescheid = new Bescheid(1, 7, "B-DOK", LocalDate.of(2026,10,15), "2026-10", "2026-11",
                new BigDecimal("563.00"), "ERTEILT", "sb-dok", Instant.EPOCH, snapshot);
        var heute = new Bedarfsgemeinschaft(7, "BG-NEU", "Neue Person", true, zero, null, 2,
                "DE12500105170648489890", "INGDDEFFXXX", "Neues Konto", zero);
        String html = BescheidGenerator.generiereHtml(heute, List.of(), null, bescheid,
                new BescheidGenerator.Rechtsbehelf("KLAGE", "Neue Stelle", "Neues Gericht"));
        assertEquals(gelesen.dokumentHtml(), html);
        assertTrue(html.contains("Originalkonto"));
        assertTrue(html.contains("Altes Amt"));
        assertTrue(html.contains("§ 36a Abs. 2 SGB I"));
        assertFalse(html.contains("für die elektronische Form § 65a"));
        assertFalse(html.contains("Neues Konto"));
        assertFalse(html.contains("Neues Gericht"));
    }

    @Test void v3OhneErteilungsdokumentWirdNichtAlsGueltigerSnapshotGelesen() {
        assertThrows(IllegalArgumentException.class, () -> BerechnungsSnapshot.lese("{\"version\":3,\"monate\":[]}"));
    }

    @Test void alteDokumenteWerdenSichtbarAlsErgaenzterAltstandGekennzeichnet() {
        assertTrue(BescheidDokument.kennzeichneAltstand("<html><body>Inhalt</body></html>")
                .contains("Bankdaten, Behördenangaben und Rechtsbehelfsbelehrung werden aus dem heutigen Datenstand"));
    }

    @Test void bruchzahlenUndUeberlaeufeSindKeineSnapshotVersionen() {
        for (String version : List.of("3.9", "4294967299", "9223372036854775809")) {
            assertThrows(IllegalArgumentException.class,
                    () -> BerechnungsSnapshot.lese("{\"version\":" + version + "}"));
        }
    }

    @Test void druckWeistFalscheBgUndLueckenhaftenMonatsplanAb() throws Exception {
        var zero = new BigDecimal("0.00");
        var fall = new SozialFall(1, 7, 1, false, false, zero, zero, zero, zero, zero, 1);
        var plan = Bewilligungsplan.erstelle(7, List.of(fall), Leistungsberechnung.KduParameter.AUS,
                zero, "2026-10", "2026-11", monat -> Satzstand.REFERENZ_2026);
        String snapshot = BerechnungsSnapshot.schreibe(plan, "<html><body>Archiv</body></html>");
        for (String kaputt : List.of(snapshot.replace("\"bgId\":7", "\"bgId\":8"),
                snapshot.replace("\"monat\":\"2026-11\"", "\"monat\":\"2026-12\""))) {
            var bescheid = new Bescheid(1, 7, "B-DOK", LocalDate.of(2026,10,15), "2026-10", "2026-11",
                    new BigDecimal("563.00"), "ERTEILT", "sb-dok", Instant.EPOCH, kaputt);
            assertThrows(FachlicheException.class,
                    () -> BescheidGenerator.generiereHtml(null, List.of(), null, bescheid, null));
        }
    }
}
