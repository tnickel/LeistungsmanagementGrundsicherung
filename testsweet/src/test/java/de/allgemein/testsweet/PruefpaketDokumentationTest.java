package de.allgemein.testsweet;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/**
 * regressionstests für die GLM-Nachrechenbarkeit des Prüfpakets:
 * Abwehr-Rechenblöcke (auch bei sekundärem Verstoß), vollständige
 * Einkommens-/Vermögensangaben in den Schritttexten und sichtbare
 * [S#]-Schrittkennungen im Protokoll.
 */
class PruefpaketDokumentationTest {

    @Test void abwehrRechenblockAuchBeiSekundaeremVerstossVollstaendig() {
        for (long seed : List.of(1L, 17L, 42L, 97L)) {
            for (Auftrag a : new SzenarioBibliothek(seed).erzeugeNegativ(500, "SWEET-N-")) {
                if (!a.beschreibung.startsWith("Verstoß-Kombination: ")) continue;
                String kombination = a.beschreibung.substring("Verstoß-Kombination: ".length());
                String primaer = kombination.split(" \\+ ")[0];
                String sekundaer = kombination.contains(" + ") ? kombination.split(" \\+ ")[1] : null;
                boolean abwehrFall = "rente".equals(primaer) || "erwerb".equals(primaer)
                        || "budget".equals(primaer) || "vermoegen".equals(primaer)
                        || "rente".equals(sekundaer) || "erwerb".equals(sekundaer)
                        || "budget".equals(sekundaer);
                if (!abwehrFall) continue;
                assertFalse(a.rechenbloecke.isEmpty(),
                        "Seed " + seed + ", TF " + a.id + " (" + kombination + "): Abwehrbetrag muss dokumentiert sein");
                Rechenblock block = a.rechenbloecke.getFirst();
                assertTrue(block.titel().startsWith("Nachweis: erwartete Abwehr"), block.titel());
                // Der Block trägt genau den erfassten Fall: Verstoß-Einkommen UND Basis-Rente stehen drin.
                var fall = block.faelle().getFirst();
                BigDecimal dokumentiertesEinkommen = fall.einkommenRente().add(fall.einkommenErwerb());
                BigDecimal blockEinkommen = block.ergebnis().anrechenbaresEinkommen()
                        .add(block.ergebnis().faelle().getFirst().freibetragErwerb());
                assertEquals(0, dokumentiertesEinkommen.compareTo(blockEinkommen),
                        "Block muss aus den erfassten Einkommen rechnen: " + dokumentiertesEinkommen + " vs. " + blockEinkommen);
                if ("rente".equals(primaer) || "rente".equals(sekundaer)
                        || "erwerb".equals(primaer) || "erwerb".equals(sekundaer)
                        || "vermoegen".equals(primaer)) {
                    assertTrue(block.ergebnis().zahlbetrag().signum() <= 0,
                            "Seed " + seed + ", TF " + a.id + ": Abwehr verlangt nicht-positiven Zahlbetrag");
                }
            }
        }
    }

    @Test void protokollZeilenTragenSichtbareSchrittkennungen() {
        Auftrag auftrag = new Auftrag(1, "NEG-KOMBINATION", "SWEET-N-0001", "Test, Toni",
                "Verstoß-Kombination: bescheidUmgekehrt + erwerb", List.of(), List.of());
        auftrag.protokolliere(new Auftrag.Schritt(LocalDateTime.of(2026, 10, 14, 13, 40, 34),
                "sb-sim-01", "Vorgang anlegen (gültige Basis, Budget 2000.00 EUR)", "OK — Vorgang 50614"));
        auftrag.protokolliere(new Auftrag.Schritt(LocalDateTime.of(2026, 10, 14, 13, 40, 35),
                "sb-sim-01", "Anordnung mit hohem Erwerbseinkommen (4500.00 EUR)",
                "ERWARTETER FEHLER (KEIN_ANSPRUCH) — kein Anspruch (Zahlbetrag -3.460,50 EUR)"));
        StringBuilder md = new StringBuilder();
        MdBericht.testfall(md, auftrag);
        String text = md.toString();
        assertTrue(text.contains("| [S1] Vorgang anlegen"), text);
        assertTrue(text.contains("| [S2] Anordnung mit hohem Erwerbseinkommen"), text);

        // Die Referenz leitet genau diese Kennungen ab — keine Zählung mehr.
        var requirement = LlmCalculationReference.requirement("X", text);
        List<?> checkIds = (List<?>) requirement.get("checkIds");
        assertTrue(checkIds.containsAll(List.of("S1", "S2")), String.valueOf(checkIds));
        assertTrue(((List<?>) requirement.get("sourceFindings")).isEmpty(),
                "OK/ERWARTETER FEHLER sind keine Befunde: " + requirement.get("sourceFindings"));
    }

    @Test void aelterePaketeOhneKennungBleibenZaehlendGueltig() {
        String alt = "### protokoll\n\n"
                + "2026-10-14 13:40:34 sb-sim-01 | Vorgang anlegen -> OK — Vorgang 1\n"
                + "2026-10-14 13:40:35 sb-sim-01 | Anordnung -> ERWARTETER FEHLER (KEIN_ANSPRUCH) — x\n";
        List<?> checkIds = (List<?>) LlmCalculationReference.requirement("ALT", alt).get("checkIds");
        assertTrue(checkIds.containsAll(List.of("S1", "S2")), String.valueOf(checkIds));
    }

    @Test void jsonInFormatHuelleWirdGeprueftKaputtesJsonWeiterhinAbgewiesen() {
        String valid = "{\"schemaVersion\":2,\"caseIds\":[\"A\"],\"cases\":[{\"caseId\":\"A\","
                + "\"verdict\":\"PASS\",\"findings\":[],\"reasoning\":\"vollständig geprüft\","
                + "\"calculations\":[],\"checks\":[]}]}";
        var requirement = List.<java.util.Map<String, Object>>of(java.util.Map.of(
                "caseId", "A", "calculations", List.of(), "checkIds", List.of()));
        assertEquals(1, LlmAnswerValidator.validate("```json\n" + valid + "\n```", List.of("A"), requirement).size());
        assertEquals(1, LlmAnswerValidator.validate("\n  " + valid + "  \n", List.of("A"), requirement).size());
        assertThrows(IllegalArgumentException.class,
                () -> LlmAnswerValidator.validate("```json\n{\"schemaVersion\":2, \"kaputt\"\n```", List.of("A"), requirement));
    }

    @Test void einkommenStehtImErgebnistextDesErfassungsschritts() {
        // Der Text des Erfassungsschritts entsteht erst im Lauf — hier prüfen wir den
        // Klartext-Helfer, der Rente/Erwerb nur bei Werten über Null anfügt.
        var fall = new de.allgemein.lmg.model.SozialFall(1L, 1L, 1, false, false,
                0, 0, 0, false, new BigDecimal("575"), BigDecimal.ZERO,
                BigDecimal.ZERO, new BigDecimal("380"), new BigDecimal("4500"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null, 1);
        assertEquals(", Rente 380 EUR, Erwerb 4500 EUR", SzenarioBibliothek.einkommenKlartext(fall));
        var ohneEinkommen = new de.allgemein.lmg.model.SozialFall(2L, 1L, 1, false, false,
                0, 0, 0, false, new BigDecimal("400"), BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null, 1);
        assertEquals("", SzenarioBibliothek.einkommenKlartext(ohneEinkommen));
    }
}
