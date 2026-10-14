package de.allgemein.testsweet;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class KiPruefAnhangTest {
    @Test void bestaetigterTransportBleibtVonFachfehlerGetrennt() {
        Map<String, Object> evidence = evidence();
        assertTrue(KiPruefAnhang.transportVerified(evidence));
        assertFalse(KiPruefAnhang.fachlichBestanden(evidence));
        StringBuilder md = new StringBuilder();
        KiPruefAnhang.md(md, evidence);
        assertTrue(md.toString().contains("dateiuebergabe_geprueft = true"));
        assertTrue(md.toString().contains("fachpruefung_bestanden = false"));
        assertTrue(md.toString().contains("Gesendete Datei = pruefmenge.md | Bytes = 4711 | SHA-256 = abc123"));
        assertTrue(md.toString().contains("API-Request-ID = request-id-1"));
    }

    @Test void passOhneVollstaendigeAbdeckungIstKeinFachpruefErfolg() {
        Map<String, Object> evidence = evidence();
        evidence.put("state", "PASS");
        evidence.put("caseVerdict", "PASS");
        evidence.put("completedCases", 1);
        assertFalse(KiPruefAnhang.fachlichBestanden(evidence));
        evidence.put("completedCases", 2);
        assertTrue(KiPruefAnhang.fachlichBestanden(evidence));
        evidence.remove("calculationVerified");
        assertFalse(KiPruefAnhang.fachlichBestanden(evidence));
    }

    @Test void ohneTransportnachweisBleibtDateiuebergabeAusstehend() {
        assertFalse(KiPruefAnhang.transportVerified(Map.of()));
        assertFalse(KiPruefAnhang.fachlichBestanden(Map.of()));
        Map<String, Object> evidence = evidence();
        evidence.put("transportChecks", List.of());
        assertFalse(KiPruefAnhang.transportVerified(evidence));
    }

    private static Map<String, Object> evidence() {
        Map<String, Object> evidence = new HashMap<>();
        evidence.put("reviewId", "review-1");
        evidence.put("model", "test-model");
        evidence.put("state", "FAIL");
        evidence.put("totalCases", 2);
        evidence.put("completedCases", 2);
        evidence.put("transportVerified", true);
        evidence.put("calculationVerified", true);
        evidence.put("reviewCoverageVerified", true);
        evidence.put("transportChecks", List.of(Map.of("batch", 1, "requestId", "request-id-1",
                "responseValidated", true, "documents", List.of(Map.of("name", "pruefmenge.md", "sha256", "abc123", "bytes", 4711)))));
        return evidence;
    }
}
