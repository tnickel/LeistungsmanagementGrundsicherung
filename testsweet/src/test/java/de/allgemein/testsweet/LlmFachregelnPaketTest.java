package de.allgemein.testsweet;

import com.sun.net.httpserver.HttpServer;
import de.allgemein.lmg.web.Json;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Prüft die tatsächliche Dokumentübergabe mit produktiven Generatorfällen.
 * Provider und Laufnachweise sind lokale Dokumentationsfixtures, keine DB-/GLM-Nachweise.
 */
class LlmFachregelnPaketTest {
    @TempDir Path root;

    @Test
    void paketUndTatsaechlicherRequestEnthaltenBescheidpfadeUndJournalherleitungVollstaendig() throws Exception {
        Map<String, Map<String, Object>> requirements = createSources();
        List<String> requests = new CopyOnWriteArrayList<>();
        List<Throwable> handlerFailures = new CopyOnWriteArrayList<>();
        HttpServer provider = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        provider.createContext("/v4/chat/completions", exchange -> {
            try {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                requests.add(body);
                Map<?, ?> request = (Map<?, ?>) Json.parse(body);
                String content = (String) ((Map<?, ?>) ((List<?>) request.get("messages")).getLast()).get("content");
                Map<?, ?> envelope = (Map<?, ?>) Json.parse(content.substring(0, content.indexOf("\n\n")));
                List<?> ids = (List<?>) envelope.get("caseIds");
                var cases = new ArrayList<Map<String, Object>>();
                for (Object id : ids) {
                    Map<String, Object> requirement = requirements.get(id.toString());
                    assertNotNull(requirement, "Keine Dokumentationsfixture für " + id
                            + "; vorhanden: " + requirements.keySet());
                    var result = new LinkedHashMap<String, Object>();
                    result.put("caseId", id); result.put("verdict", "PASS"); result.put("findings", List.of());
                    result.put("reasoning", "Lokale Transportfixture; kein unabhängiges fachliches Prüfurteil");
                    result.put("calculations", requirement.get("calculations"));
                    result.put("checks", ((List<?>) requirement.get("checkIds")).stream().map(check ->
                            Map.of("checkId", check, "verdict", "PASS", "reasoning", "Lokale Transportfixture")).toList());
                    cases.add(result);
                }
                byte[] reply = Json.write(Map.of("model", "glm-5.3", "choices", List.of(Map.of(
                        "finish_reason", "stop", "message", Map.of("content", Json.write(Map.of(
                                "schemaVersion", 2, "caseIds", ids, "cases", cases))))))).getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, reply.length);
                exchange.getResponseBody().write(reply);
            } catch (Throwable failure) {
                handlerFailures.add(failure);
                byte[] diagnostic = Json.write(Map.of("fixtureError", failure.toString())).getBytes(StandardCharsets.UTF_8);
                try {
                    exchange.sendResponseHeaders(500, diagnostic.length);
                    exchange.getResponseBody().write(diagnostic);
                } catch (java.io.IOException responseFailure) { failure.addSuppressed(responseFailure); }
            } finally { exchange.close(); }
        });
        provider.start();
        try (var service = documentationService()) {
            service.saveConfiguration(Map.of("baseUrl", "http://127.0.0.1:" + provider.getAddress().getPort() + "/v4",
                    "apiKey", "local-documentation-fixture", "batchSize", 4));
            var packageResult = service.buildPackage(4);
            assertEquals(requirements.keySet(), new HashSet<>((List<?>) packageResult.get("selectedCaseIds")),
                    "Fixture-IDs müssen exakt die gerenderten und ausgewählten Fall-IDs abbilden");
            Path directory = Path.of((String) packageResult.get("directory"));
            String law = Files.readString(repoFile("doc/testzertifikat-werkzeug/gesetzesgrundlagen-2026.md"));
            String prompt = Files.readString(repoFile("doc/testzertifikat-werkzeug/llm-pruefauftrag.txt"));
            assertEquals(law, Files.readString(directory.resolve("gesetzesgrundlagen-2026.md")));
            assertEquals(prompt, Files.readString(directory.resolve("llm-pruefauftrag.txt")));
            assertFachregeln(law, prompt, Files.readString(directory.resolve("pruefmenge.md")));
            service.start(4);
            Map<String, Object> status = finished(service);
            assertTrue(handlerFailures.isEmpty(), () -> "Lokaler Provider-Stub ist fehlgeschlagen: " + handlerFailures);
            assertEquals("PASS", status.get("state"), status.toString());
            assertEquals(1, requests.size(), "Vollständige lokale Antwort braucht keine Wiederholung");
            var request = (Map<?, ?>) Json.parse(requests.getFirst());
            String transmitted = (String) ((Map<?, ?>) ((List<?>) request.get("messages")).getLast()).get("content");
            assertTrue(transmitted.contains(namedDocument("gesetzesgrundlagen-2026.md", law)));
            assertTrue(transmitted.contains(namedDocument("llm-pruefauftrag.txt", prompt)));
            assertTrue(transmitted.contains("NEGATIV:run-20261015-100001:TF29"));
            assertTrue(transmitted.contains("NEGATIV:run-20261015-100001:TF68"));
            assertTrue(transmitted.contains("[S4] Auszahlung anordnen (2026-07) -> OK"));
            assertTrue(transmitted.contains("[S4] Parallel: veralteter Maskenstand speichern"));
            assertTrue(transmitted.contains("[S5] Anordnung über das zu kleine Startbudget"));
            assertTrue(transmitted.contains("1 + 1 + 1 + 0 + 0 = 3"));
        } finally { provider.stop(0); }
    }

    @Test
    void tf68JournalzahlIstAusGeneratoraktionenUndUebergebenenRegelnHerleitbar() throws Exception {
        var order = negativeCases().get(1);
        assertEquals(68, order.id);
        assertEquals(5, order.aktionen.size());
        List<Integer> contributions = order.aktionen.stream().map(action -> {
            if (action.erwarteterFehler() != null) return 0;
            if (action.name().startsWith("Vorgang anlegen") || action.name().startsWith("SozialFall erfassen")
                    || action.name().startsWith("Miete auf")) return 1;
            throw new AssertionError("Unbekannte Fachwirkung im überprüften Fall: " + action.name());
        }).toList();
        assertEquals(List.of(1, 1, 1, 0, 0), contributions);
        assertEquals("KONKURRENZ", order.aktionen.get(3).erwarteterFehler());
        assertEquals("BUDGET_ERSCHOEPFT", order.aktionen.get(4).erwarteterFehler());
        long independentlyDerived = contributions.stream().mapToLong(Integer::longValue).sum();
        var journal = order.erwartungen.stream().filter(e -> e.sql().equals(
                "SELECT COUNT(*) FROM JOURNAL WHERE BG_ID = ?")).findFirst().orElseThrow();
        assertEquals(3, independentlyDerived);
        assertEquals(independentlyDerived, Long.parseLong(journal.erwartet()));
        String law = Files.readString(repoFile("doc/testzertifikat-werkzeug/gesetzesgrundlagen-2026.md"));
        assertTrue(law.contains("| Vorgang anlegen | 1 |"));
        assertTrue(law.contains("| SozialFall anlegen | 1 je angelegtem SozialFall |"));
        assertTrue(law.contains("| SozialFall ändern, auch Mietänderung | 1 |"));
        assertTrue(law.contains("Beitrag 0"));
        assertTrue(law.contains("BG_ID IS NULL"));
        assertTrue(law.contains("Einsichts-Audits"));
    }

    @Test
    void negativberichtBeziehtUnveraendertAufJedenAbwehrschrittUndBewahrtErfolgreicheBasisaktionen() throws Exception {
        var order = negativeCases().getFirst();
        assertEquals(29, order.id);
        var report = render(List.of(order), "NEGATIV");
        assertTrue(report.contains("je erwartungsgemäß abgewehrtem Schritt gegenüber dessen unmittelbarem Vorzustand"));
        assertTrue(report.contains("Erfolgreiche Basisaktionen können Zahlungen, Bescheide und Budgetänderungen hinterlassen"));
        assertFalse(report.contains("Datenbank-Endzustand unverändert geblieben ist (keine Zahlung, Budget unangetastet)"));
        assertTrue(report.contains("[S3] Bescheid mit 'von' nach 'bis'"));
        assertTrue(report.contains("[S4] Auszahlung anordnen (2026-07) -> OK"));
        var decisionCount = order.erwartungen.stream().filter(e -> e.sql().equals(
                "SELECT COUNT(*) FROM BESCHEID WHERE BG_ID = ?")).findFirst().orElseThrow();
        var paymentCount = order.erwartungen.stream().filter(e -> e.sql().equals(
                "SELECT COUNT(*) FROM ZAHLUNG WHERE BG_ID = ?")).findFirst().orElseThrow();
        assertEquals("0", decisionCount.erwartet());
        assertEquals("1", paymentCount.erwartet());
        assertTrue(order.aktionen.stream().noneMatch(a -> "KEIN_BESCHEID".equals(a.erwarteterFehler())));
    }

    private static void assertFachregeln(String law, String prompt, String sample) {
        assertTrue(law.contains("**Ohne bisherigen Bescheid** (`BESCHEID COUNT=0`"));
        assertTrue(law.contains("**Sobald mindestens ein Bescheid existiert**"));
        assertTrue(law.contains("jeder Status zählt"));
        assertTrue(law.contains("Ein verworfener Bescheidversuch legt keine Bescheidzeile an"));
        assertTrue(law.contains("begründet allein ein PASS"));
        assertTrue(prompt.contains("Kein Datenbankzählwert und keine erwartete Abwehr erzwingt für sich ein PASS"));
        assertTrue(prompt.contains("bleibt die Journalherleitung UNVERIFIED"));
        assertTrue(sample.contains("## TF 29 NEG-KOMBINATION"));
        assertTrue(sample.contains("## TF 68 NEG-KOMBINATION"));
    }

    private Map<String, Map<String, Object>> createSources() throws Exception {
        Path tool = root.resolve("doc/testzertifikat-werkzeug"); Files.createDirectories(tool);
        for (String file : List.of("llm-pruefauftrag.txt", "gesetzesgrundlagen-2026.md", "llm-response-contract.json"))
            Files.copy(repoFile("doc/testzertifikat-werkzeug/" + file), tool.resolve(file));
        var byId = new LinkedHashMap<String, Map<String, Object>>();
        for (String category : List.of("POSITIV", "NEGATIV")) {
            List<Auftrag> orders = category.equals("POSITIV") ? new SzenarioBibliothek(20261014L).erzeuge(2) : negativeCases();
            String runId = "run-20261015-" + (category.equals("POSITIV") ? "100000" : "100001");
            Path run = root.resolve("testsweet/logs/" + runId); Files.createDirectories(run);
            String text = render(orders, category);
            Files.writeString(run.resolve("testdokumentation.md"), text);
            Files.writeString(run.resolve("run-state.json"), Json.write(Map.of("schemaVersion", 1, "kind", "SIMULATOR_RUN",
                    "state", "PASS", "category", category, "mode", "FACHSCHICHT", "success", true, "reportsComplete", true,
                    "startedAt", category.equals("POSITIV") ? "2026-10-15T10:00:00Z" : "2026-10-15T10:00:01Z",
                    "buildEvidence", Map.of("fixture", "documented-fachregeln"),
                    "reports", List.of(Map.of("path", "testdokumentation.md", "sha256", LlmReviewService.sha256(text.getBytes(StandardCharsets.UTF_8)))))));
            for (var order : orders) {
                StringBuilder one = new StringBuilder(); MdBericht.testfall(one, order);
                var heading = java.util.regex.Pattern.compile("(?m)^## TF (\\d+) ").matcher(one);
                assertTrue(heading.find(), "Gerenderte Fallnummer fehlt");
                String id = category + ":" + runId + ":TF" + heading.group(1);
                var requirement = LlmCalculationReference.requirement(id, one.toString());
                assertTrue(((List<?>) requirement.get("sourceFindings")).isEmpty(),
                        () -> "Ungültige Dokumentationsfixture " + id + ": " + requirement.get("sourceFindings"));
                assertTrue(((List<?>) requirement.get("missingInputs")).isEmpty(),
                        () -> "Unvollständige Dokumentationsfixture " + id + ": " + requirement.get("missingInputs"));
                byId.put(id, requirement);
            }
        }
        return byId;
    }

    private static List<Auftrag> negativeCases() {
        var generated = new SzenarioBibliothek(20261014L).erzeugeNegativ(100, "SWEET-N-");
        return List.of(generated.get(28), generated.get(67));
    }

    private String render(List<Auftrag> orders, String category) throws Exception {
        for (var order : orders) {
            order.status = Auftrag.Status.VERIFIZIERT;
            order.bgId = 1000L + order.id;
            order.bearbeiter = "sb-document-fixture";
            order.kontext = Map.of("bgId", order.bgId, "fallId", 2000L + order.id,
                    "zahlungId", 3000L + order.id, "kollege", "sb-fixture-zweit");
            if (order.schritte().isEmpty()) for (var action : order.aktionen) {
                String outcome = action.erwarteterFehler() == null ? "OK — Dokumentationsfixture"
                        : "ERWARTETER FEHLER (" + action.erwarteterFehler() + ") — Dokumentationsfixture";
                order.protokolliere(new Auftrag.Schritt(LocalDateTime.of(2026, 10, 15, 10, 0),
                        order.bearbeiter, action.name(), outcome));
            }
            if (order.pruefungen().isEmpty()) for (var expected : order.erwartungen) {
                // TEXT_IN enthält zulässige Alternativen, keinen möglichen konkreten DB-Wert.
                // Der erste zulässige Status beschreibt hier den Ablauf ohne fremden Kassenlauf.
                String actual = expected.art() == Erwartung.Art.TEXT_IN
                        ? expected.erwartet().split("\\|", -1)[0] : expected.erwartet();
                order.ergaenzePruefung(new Auftrag.Pruefung(expected.beschreibung(), expected.erwartet(), actual,
                        true, "Lokale Dokumentationsfixture; keine SQL-Ausführung", expected.sql(), expected.parameter().apply(order.kontext)));
            }
        }
        var simulator = new Simulator();
        set(simulator, "auftraege", orders); set(simulator, "phase", Simulator.Phase.FERTIG);
        set(simulator, "kategorieLetzter", Simulator.Kategorie.valueOf(category));
        set(simulator, "laufStart", LocalDateTime.of(2026, 10, 15, 10, 0));
        Path report = Files.createTempFile(root, "documented-" + category, ".md");
        MdBericht.schreibe(report, simulator);
        return Files.readString(report);
    }

    private LlmReviewService documentationService() {
        return new LlmReviewService(root, Map.of(), Duration.ofMillis(1), new LlmReviewService.BuildVerifier() {
            public Map<String, Object> capture(Path ignored) { return Map.of("fixture", "documented-fachregeln"); }
            public boolean matches(Map<?, ?> evidence, Path ignored) { return "documented-fachregeln".equals(evidence.get("fixture")); }
        });
    }

    private static String namedDocument(String name, String text) {
        return "\n=== BEGIN DOCUMENT " + name + " ===\n" + text + "\n=== END DOCUMENT " + name + " ===\n";
    }

    private static Path repoFile(String relative) {
        for (Path candidate : List.of(Path.of(relative), Path.of("..").resolve(relative)))
            if (Files.isRegularFile(candidate)) return candidate;
        throw new AssertionError("Projektdatei fehlt: " + relative);
    }

    private static void set(Object target, String field, Object value) throws ReflectiveOperationException {
        var member = target.getClass().getDeclaredField(field); member.setAccessible(true); member.set(target, value);
    }

    private static Map<String, Object> finished(LlmReviewService service) throws InterruptedException {
        long end = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        while (System.nanoTime() < end) {
            var status = service.status();
            if (!Boolean.TRUE.equals(status.get("running"))) return status;
            Thread.sleep(10);
        }
        throw new AssertionError("Lokale Dokumentationsfixture wurde nicht beendet");
    }
}
