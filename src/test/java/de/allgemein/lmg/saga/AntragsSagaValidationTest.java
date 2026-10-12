package de.allgemein.lmg.saga;

import de.allgemein.lmg.service.FachlicheException;
import de.allgemein.lmg.web.Json;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reine Prüfungs-Tests der Saga-Payload-Validierung (Codereview CR-007/CR-043):
 * Zwölf-Monate-Regel des § 44 Abs. 3 SGB XII, Feldlängen passend zur DDL und
 * Betragsgrenzen — bisher war der Saga-Weg der ungeschützte zweite Bescheid-Pfad.
 */
class AntragsSagaValidationTest {

    @SuppressWarnings("unchecked")
    private Map<String, Object> gueltigerPayload() {
        return (Map<String, Object>) Json.parse("""
                {"aktenzeichen":"BG-VAL-1","hauptpersonName":"Max Mustermann","iban":null,"bic":null,
                 "regelbedarfsstufe":1,"zahlungsmonat":"2026-10","bewilligungBis":"2026-10",
                 "startBudget":900.00,"kduMiete":300.00,"kduBetriebskosten":0,"kduHeizung":0,
                 "einkommenRente":0,"einkommenErwerb":0}
                """);
    }

    @Test
    void zwoelfMonateSindErlaubt() {
        Map<String, Object> payload = gueltigerPayload();
        payload.put("zahlungsmonat", "2026-01");
        payload.put("bewilligungBis", "2026-12");
        assertDoesNotThrow(() -> AntragsSagaProzess.validierePayload(payload));
    }

    @Test
    void dreizehnMonateWerdenAbgewiesen() {
        Map<String, Object> payload = gueltigerPayload();
        payload.put("zahlungsmonat", "2026-01");
        payload.put("bewilligungBis", "2027-01");
        FachlicheException e = assertThrows(FachlicheException.class,
                () -> AntragsSagaProzess.validierePayload(payload));
        assertTrue(e.getMessage().contains("§ 44 Abs. 3"), e.getMessage());
    }

    @Test
    void jahresgrenzeInnerhalbZwoelfMonateIstErlaubt() {
        Map<String, Object> payload = gueltigerPayload();
        payload.put("zahlungsmonat", "2026-07");
        payload.put("bewilligungBis", "2027-06");
        assertDoesNotThrow(() -> AntragsSagaProzess.validierePayload(payload));
    }

    @Test
    void bewilligungBisVorMonatWirdAbgewiesen() {
        Map<String, Object> payload = gueltigerPayload();
        payload.put("zahlungsmonat", "2026-10");
        payload.put("bewilligungBis", "2026-09");
        assertThrows(FachlicheException.class, () -> AntragsSagaProzess.validierePayload(payload));
    }

    @Test
    void ueberlangesAktenzeichenWirdAbgewiesen() {
        Map<String, Object> payload = gueltigerPayload();
        payload.put("aktenzeichen", "A".repeat(51));
        FachlicheException e = assertThrows(FachlicheException.class,
                () -> AntragsSagaProzess.validierePayload(payload));
        assertTrue(e.getMessage().contains("zu lang"));
    }

    @Test
    void ungueltigerZahlungsmonatWirdAbgewiesen() {
        Map<String, Object> payload = gueltigerPayload();
        payload.put("zahlungsmonat", "2026-1");
        assertThrows(FachlicheException.class, () -> AntragsSagaProzess.validierePayload(payload));
    }

    @Test
    void negativerBetragWirdAbgewiesen() {
        Map<String, Object> payload = gueltigerPayload();
        payload.put("startBudget", -1);
        assertThrows(FachlicheException.class, () -> AntragsSagaProzess.validierePayload(payload));
    }

    @Test
    void vermoegenIstOptionalAberPruefbar() {
        Map<String, Object> payload = gueltigerPayload();
        payload.put("vermoegen", 10500.00);
        assertDoesNotThrow(() -> AntragsSagaProzess.validierePayload(payload));
        payload.put("vermoegen", -5);
        assertThrows(FachlicheException.class, () -> AntragsSagaProzess.validierePayload(payload));
    }
}
