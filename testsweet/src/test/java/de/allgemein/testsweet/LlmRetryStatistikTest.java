package de.allgemein.testsweet;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class LlmRetryStatistikTest {

    private static Map<String, Object> sendung(int gruppe, int versuch, boolean gueltig) {
        Map<String, Object> eintrag = new LinkedHashMap<>();
        eintrag.put("batch", gruppe);
        eintrag.put("attempt", versuch);
        eintrag.put("responseValidated", gueltig);
        return eintrag;
    }

    @Test void leererStandLiefertNurNullen() {
        Map<String, Integer> statistik = LlmReviewService.wiederholungsStatistik(Map.of());
        assertEquals(0, statistik.get("ersterVersuch"));
        assertEquals(0, statistik.get("eineWiederholung"));
        assertEquals(0, statistik.get("zweiWiederholungen"));
        assertEquals(0, statistik.get("gescheiterteFallgruppen"));
        assertEquals(0, statistik.get("wiederholteSendungen"));
    }

    @Test void verteilungUeberAlleVersuchsstufen() {
        // 2 Gruppen direkt gültig, 1 Gruppe im 2. Versuch, 1 Gruppe im 3. Versuch,
        // 1 Gruppe mit 3 Fehlversuchen gescheitert.
        List<Map<String, Object>> sendungen = new ArrayList<>(List.of(
                sendung(1, 1, true), sendung(2, 1, true),
                sendung(3, 1, false), sendung(3, 2, true),
                sendung(4, 1, false), sendung(4, 2, false), sendung(4, 3, true),
                sendung(5, 1, false), sendung(5, 2, false), sendung(5, 3, false)));
        Map<String, Integer> statistik = LlmReviewService.wiederholungsStatistik(Map.of("attemptChecks", sendungen));
        assertEquals(2, statistik.get("ersterVersuch"));
        assertEquals(1, statistik.get("eineWiederholung"));
        assertEquals(1, statistik.get("zweiWiederholungen"));
        assertEquals(1, statistik.get("gescheiterteFallgruppen"));
        assertEquals(6, statistik.get("wiederholteSendungen"), "Jede formale Fehlantwort zählt einzeln");
    }

    @Test void laufenderStandMitWiederholungIstNochNichtGescheitert() {
        // Während des Laufs: Gruppe 3 hat erst einen Fehlversuch — sie zählt
        // in wiederholteSendungen, aber weder als Fallgruppe noch als gescheitert.
        List<Map<String, Object>> sendungen = List.of(
                sendung(1, 1, true),
                sendung(2, 1, false));
        Map<String, Integer> statistik = LlmReviewService.wiederholungsStatistik(Map.of("attemptChecks", sendungen));
        assertEquals(1, statistik.get("ersterVersuch"));
        assertEquals(0, statistik.get("gescheiterteFallgruppen"));
        assertEquals(1, statistik.get("wiederholteSendungen"));
    }

    @Test void fremdeOderUnvollstaendigeEintraegeWerdenIgnoriert() {
        List<Object> sendungen = List.of(
                "kein Karteieintrag",
                Map.of("batch", 0, "attempt", 1, "responseValidated", true), // Gruppennummer 0 ist unvollständig
                Map.of("batch", 1, "attempt", 2, "responseValidated", true));
        Map<String, Integer> statistik = LlmReviewService.wiederholungsStatistik(Map.of("attemptChecks", sendungen));
        assertEquals(1, statistik.get("eineWiederholung"));
        assertEquals(0, statistik.get("ersterVersuch"));
    }
}
