package de.allgemein.testsweet;

import com.sun.net.httpserver.HttpServer;
import de.allgemein.lmg.web.Json;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiFunction;
import static org.junit.jupiter.api.Assertions.*;

class LlmReviewServiceTest {
    @TempDir Path root;

    @Test void configurationNeverReturnsKeyAndPreservesItOnEmptySave() throws Exception {
        LlmConfig config = new LlmConfig(root, Map.of());
        config.save(Map.of("apiKey", "test-secret", "model", "glm-5.3"));
        config.save(Map.of("apiKey", "", "cases", 4));
        assertEquals("test-secret", config.read().apiKey());
        assertFalse(Json.write(config.read().publicView()).contains("test-secret"));
        assertEquals(true, config.read().publicView().get("apiKeyConfigured"));
        LlmConfig env = new LlmConfig(root, Map.of("GLM_API_KEY", "env-secret", "SWEET_LLM_API_KEY", "preferred-secret"));
        assertEquals("preferred-secret", env.read().apiKey());
        config.save(Map.of("clearApiKey", true));
        assertEquals(false, config.read().publicView().get("apiKeyConfigured"));
        assertThrows(IllegalArgumentException.class, () -> config.save(Map.of("baseUrl", "https://user:secret@example.com/v1")));
    }

    @Test void packageContainsWholeCuratedAndNegativeCasesAndDoesNotSilentlyShrink() throws Exception {
        fixtures();
        try (LlmReviewService service = new LlmReviewService(root, Map.of())) {
            Map<String, Object> result = service.buildPackage(4);
            String sample = Files.readString(Path.of((String) result.get("pruefmenge")));
            assertTrue(sample.contains("CURATED-POSITIVE-END"));
            assertTrue(sample.contains("CURATED-NEGATIVE-END"));
            assertTrue(sample.contains("COMBINATION-POSITIVE-END"));
            assertTrue(sample.contains("COMBINATION-NEGATIVE-END"));
            assertEquals(4, ((List<?>) result.get("selectedCaseIds")).size());
            Path directory = Path.of((String) result.get("directory"));
            assertArrayEquals(Files.readAllBytes(root.resolve("testsweet/logs/run-20261013-100000/testdokumentation.md")),
                    Files.readAllBytes(directory.resolve("source-POSITIV.md")));
            assertThrows(IllegalArgumentException.class, () -> service.buildPackage(5));
        }
    }

    @Test void realHttpRequestContainsExactDocumentsAndSurvivesRestart() throws Exception {
        fixtures();
        List<String> bodies = new CopyOnWriteArrayList<>();
        HttpServer server = api(bodies, (ids, body) -> response(ids, "PASS", "glm-5.3", "stop"));
        try (LlmReviewService service = configured(server)) {
            service.start(4);
            Map<String, Object> status = finished(service);
            assertEquals("PASS", status.get("state"), status.toString());
            assertEquals(true, status.get("transportVerified"));
            assertEquals(4, bodies.size());
            Path directory = Path.of((String) status.get("artifactDirectory"));
            for (int i = 0; i < bodies.size(); i++) {
                String serialized = Files.readString(directory.resolve("request-" + (i + 1) + "-attempt-1.json"));
                assertEquals(bodies.get(i), serialized);
                assertFalse(serialized.contains("local-test-key"));
                Map<?, ?> request = (Map<?, ?>) Json.parse(serialized);
                String user = (String) ((Map<?, ?>) ((List<?>) request.get("messages")).get(1)).get("content");
                assertTrue(user.contains("VOLLSTÄNDIGER GESETZESTEXT\nENDE GESETZ"));
                assertTrue(user.contains("VOLLSTÄNDIGER PRÜFAUFTRAG\nENDE PROMPT"));
            }
            try (LlmReviewService restored = new LlmReviewService(root, Map.of())) {
                assertEquals("PASS", restored.status().get("state"), restored.status().toString());
            }
            assertCertificateEvidence(true);
            Files.writeString(root.resolve("doc/testzertifikat-werkzeug/llm-pruefauftrag.txt"), "geändert");
            assertEquals("UNVERIFIED", service.status().get("state"));
            assertCertificateEvidence(false);
        } finally { server.stop(0); }
    }

    @Test void findingsKeepTransportGreenButNeverProducePass() throws Exception {
        fixtures();
        HttpServer server = api(new CopyOnWriteArrayList<>(), (ids, body) -> response(ids, "FAIL", "glm-5.3", "stop"));
        try (LlmReviewService service = configured(server)) {
            service.start(2);
            Map<String, Object> status = finished(service);
            assertEquals("FAIL", status.get("state"));
            assertEquals(true, status.get("transportVerified"));
            assertCertificateEvidence(false);
            Path resultFile = Path.of((String) status.get("artifactDirectory")).resolve("result.json");
            Map<String, Object> result = mutableJson(Files.readString(resultFile));
            result.put("state", "PASS");
            List<Map<String, Object>> cases = new ArrayList<>();
            for (Object row : (List<?>) result.get("cases")) {
                Map<String, Object> c = new java.util.LinkedHashMap<>((Map<String, Object>) row);
                c.put("verdict", "PASS"); c.put("findings", List.of()); cases.add(c);
            }
            result.put("cases", cases);
            Files.writeString(resultFile, Json.write(result));
            try (LlmReviewService restored = new LlmReviewService(root, Map.of())) {
                assertEquals("UNVERIFIED", restored.status().get("state"));
            }
            assertCertificateEvidence(false);
        } finally { server.stop(0); }
    }

    @Test void missingCasesTruncationAndWrongModelFailClosed() throws Exception {
        for (String scenario : List.of("missing", "truncated", "model")) {
            fixtures();
            List<String> bodies = new CopyOnWriteArrayList<>();
            HttpServer server = api(bodies, (ids, body) -> response(
                    scenario.equals("missing") ? List.of() : ids, "PASS",
                    scenario.equals("model") ? "other-model" : "glm-5.3", scenario.equals("truncated") ? "length" : "stop"));
            try (LlmReviewService service = configured(server)) {
                service.start(2);
                Map<String, Object> status = finished(service);
                assertEquals("ERROR", status.get("state"), scenario + status);
                assertEquals(false, status.get("transportVerified"));
                assertEquals(true, status.get("retryExhausted"));
                assertEquals(0, ((Number) status.get("completedCases")).intValue());
                assertEquals(0, ((Number) status.get("completedBatches")).intValue());
                assertEquals(0, ((List<?>) status.get("transportChecks")).size());
                assertEquals(3, ((List<?>) status.get("attemptChecks")).size());
                assertEquals(3, bodies.size());
                assertEquals(bodies.get(0), bodies.get(1));
                assertEquals(bodies.get(1), bodies.get(2));
                Path directory = Path.of((String) status.get("artifactDirectory"));
                String protocol = Files.readString(directory.resolve("protocol.md"));
                assertTrue(protocol.contains("3 Versuche fehlgeschlagen"));
                for (int attempt = 1; attempt <= 3; attempt++) {
                    assertTrue(Files.isRegularFile(directory.resolve("request-1-attempt-" + attempt + ".json")));
                    assertTrue(Files.isRegularFile(directory.resolve("response-1-attempt-" + attempt + ".json")));
                    assertTrue(protocol.contains("response-1-attempt-" + attempt + ".json"));
                }
            } finally { server.stop(0); }
        }
    }

    @Test void invalidCaseIdsAreResentWithWholeIdenticalRequestAndCanRecover() throws Exception {
        fixtures();
        List<String> bodies = new CopyOnWriteArrayList<>();
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        HttpServer server = api(bodies, (ids, body) -> response(calls.incrementAndGet() <= 2 ? List.of() : ids,
                "PASS", "glm-5.3", "stop"));
        try (LlmReviewService service = configured(server)) {
            service.start(2);
            Map<String, Object> status = finished(service);
            assertEquals("PASS", status.get("state"), status.toString());
            assertEquals(true, status.get("transportVerified"));
            assertEquals(4, bodies.size()); // erster Batch dreimal, zweiter Batch einmal
            assertEquals(bodies.get(0), bodies.get(1));
            assertEquals(bodies.get(1), bodies.get(2));
            Path directory = Path.of((String) status.get("artifactDirectory"));
            assertEquals(2, ((List<?>) status.get("transportChecks")).size());
            assertEquals(4, ((List<?>) status.get("attemptChecks")).size());
            assertEquals(false, ((Map<?, ?>) ((List<?>) status.get("attemptChecks")).get(0)).get("responseValidated"));
            assertEquals(false, ((Map<?, ?>) ((List<?>) status.get("attemptChecks")).get(1)).get("responseValidated"));
            assertEquals(2, ((Number) status.get("completedCases")).intValue());
            String protocol = Files.readString(directory.resolve("protocol.md"));
            assertTrue(protocol.contains("Fall-ID-Liste unvollständig"));
            try (LlmReviewService restored = new LlmReviewService(root, Map.of())) {
                assertEquals("PASS", restored.status().get("state"), restored.status().toString());
            }
        } finally { server.stop(0); }
    }

    @Test void connectionTestUsesActualModelRequest() throws Exception {
        List<String> requests = new CopyOnWriteArrayList<>();
        HttpServer server = api(requests, (ids, body) -> Json.write(Map.of("model", "glm-5.3", "id", "connection-id",
                "choices", List.of(Map.of("finish_reason", "stop", "message", Map.of("content", "SWEET_LLM_OK"))))));
        try (LlmReviewService service = configured(server)) {
            assertEquals(true, service.testConnection().get("ok"));
            assertEquals(1, requests.size());
            assertTrue(requests.getFirst().contains("SWEET_LLM_OK"));
        } finally { server.stop(0); }
    }

    @Test void duplicateAndUnverifiedAnswersCannotBecomePass() {
        String id = "POSITIV:run-1:TF1";
        assertThrows(IllegalArgumentException.class, () -> LlmReviewService.validateAnswer(
                Json.write(Map.of("caseIds", List.of(id,id), "cases", List.of())), List.of(id)));
        assertThrows(IllegalArgumentException.class, () -> LlmReviewService.validateAnswer(
                Json.write(Map.of("caseIds", List.of(id), "cases", List.of(Map.of("caseId",id,"verdict","PASS","findings",List.of("ungeprüft"),"reasoning","Details")))), List.of(id)));
        assertEquals("UNVERIFIED", LlmReviewService.validateAnswer(Json.write(Map.of("caseIds",List.of(id),
                "cases",List.of(Map.of("caseId",id,"verdict","UNVERIFIED","findings",List.of("Daten fehlen"),"reasoning","Eingaben unvollständig")))), List.of(id)).getFirst().get("verdict"));
    }

    @Test void slowResponseBodyIsBoundedByConfiguredDeadline() throws Exception {
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v4/chat/completions", exchange -> {
            exchange.getRequestBody().readAllBytes();
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().write('{'); exchange.getResponseBody().flush();
            try { release.await(15, java.util.concurrent.TimeUnit.SECONDS); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            exchange.close();
        });
        server.start();
        try (LlmReviewService service = configured(server)) {
            service.saveConfiguration(Map.of("timeoutSeconds", 5));
            long started = System.nanoTime();
            Map<String, Object> result = service.testConnection();
            assertEquals(false, result.get("ok"));
            assertTrue(String.valueOf(result.get("message")).contains("Zeitüberschreitung"));
            assertTrue(java.time.Duration.ofNanos(System.nanoTime() - started).toSeconds() < 10);
        } finally { release.countDown(); server.stop(0); }
    }

    @Test void providerKeyEchoNeverAppearsInPublicStatusOrEvidence() throws Exception {
        fixtures();
        HttpServer server = api(new CopyOnWriteArrayList<>(), (ids, body) -> response(ids, "PASS", "glm-5.3", "stop")
                .replace("local-provider-request", "local-test-key"));
        try (LlmReviewService service = configured(server)) {
            service.start(2);
            Map<String, Object> status = finished(service);
            assertEquals("PASS", status.get("state"), status.toString());
            assertFalse(Json.write(status).contains("local-test-key"));
            try (var paths = Files.list(Path.of((String) status.get("artifactDirectory")))) {
                for (Path file : paths.filter(Files::isRegularFile).toList()) {
                    assertFalse(Files.readString(file).contains("local-test-key"), file.toString());
                }
            }
        } finally { server.stop(0); }
    }

    private void fixtures() throws Exception {
        Path tool = root.resolve("doc/testzertifikat-werkzeug"); Files.createDirectories(tool);
        Files.writeString(tool.resolve("llm-pruefauftrag.txt"), "VOLLSTÄNDIGER PRÜFAUFTRAG\nENDE PROMPT");
        Files.writeString(tool.resolve("gesetzesgrundlagen-2026.md"), "VOLLSTÄNDIGER GESETZESTEXT\nENDE GESETZ");
        for (String category : List.of("POSITIV", "NEGATIV")) {
            Path run = root.resolve("testsweet/logs/run-20261013-" + (category.equals("POSITIV") ? "100000" : "100001"));
            Files.createDirectories(run);
            String suffix = category.equals("POSITIV") ? "POSITIVE" : "NEGATIVE";
            Files.writeString(run.resolve("testdokumentation.md"), "# testsweet Lauf\nlauf_erfolgreich = true\nkategorie = " + category
                    + "\n## rechengrundlagen_2026\nVollständiger Kopf\n## TF 1 KURATIERT SWEET-0001\nCURATED-" + suffix
                    + "-END\n## TF 2 KOMBINATION SWEET-0002\nCOMBINATION-" + suffix + "-END\n");
        }
    }
    private LlmReviewService configured(HttpServer server) {
        LlmReviewService service = new LlmReviewService(root, Map.of(), java.time.Duration.ofMillis(10));
        service.saveConfiguration(Map.of("baseUrl", "http://127.0.0.1:" + server.getAddress().getPort() + "/v4", "apiKey", "local-test-key", "batchSize",1));
        return service;
    }
    private static HttpServer api(List<String> bodies, BiFunction<List<String>,String,String> reply) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/v4/chat/completions", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8); bodies.add(body);
            assertEquals("Bearer local-test-key",exchange.getRequestHeaders().getFirst("Authorization"));
            Map<?, ?> request = (Map<?, ?>) Json.parse(body);
            List<?> messages = (List<?>) request.get("messages");
            String content = (String) ((Map<?, ?>) messages.getLast()).get("content");
            List<String> ids = List.of();
            if (content.startsWith("{")) ids = ((List<?>) ((Map<?, ?>) Json.parse(content.substring(0,content.indexOf("\n\n")))).get("caseIds")).stream().map(String.class::cast).toList();
            byte[] bytes = reply.apply(ids,body).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type","application/json"); exchange.sendResponseHeaders(200,bytes.length);
            exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.start(); return server;
    }
    private static String response(List<String> ids, String verdict, String model, String finish) {
        List<Map<String,Object>> cases = ids.stream().map(id -> Map.<String,Object>of("caseId",id,"verdict",verdict,
                "findings",verdict.equals("PASS") ? List.of() : List.of("konkreter Rechenfehler"),
                "reasoning","563.00 + 400.00 - 100.00 = 863.00; Budget und Workflow separat geprüft")).toList();
        return Json.write(Map.of("model",model,"id","local-provider-request","choices",List.of(Map.of("finish_reason",finish,
                "message",Map.of("content",Json.write(Map.of("caseIds",ids,"cases",cases)))))));
    }
    private static Map<String,Object> mutableJson(String text) { return new java.util.LinkedHashMap<>((Map<String,Object>)Json.parse(text)); }
    private void assertCertificateEvidence(boolean valid) throws Exception {
        Path helper = Path.of("doc/testzertifikat-werkzeug/llm-evidence.js");
        if (!Files.isRegularFile(helper)) helper = Path.of("../doc/testzertifikat-werkzeug/llm-evidence.js");
        assertTrue(Files.isRegularFile(helper));
        Process process;
        try {
            process = new ProcessBuilder("node", "-e", "const e=require(process.argv[1]).latestEvidence(process.argv[2]);process.exit(e&&e.valid===JSON.parse(process.argv[3])?0:1)",
                    helper.toAbsolutePath().normalize().toString(), root.toString(), String.valueOf(valid)).redirectErrorStream(true).start();
        } catch (java.io.IOException unavailable) {
            // Der Java-Backendtest bleibt auch auf Rechnern ohne Zertifikat-Node lauffähig.
            return;
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(process.waitFor(15, java.util.concurrent.TimeUnit.SECONDS));
        assertEquals(0, process.exitValue(), output);
    }
    private static Map<String,Object> finished(LlmReviewService service) throws Exception {
        long deadline = System.nanoTime()+java.time.Duration.ofSeconds(10).toNanos();
        while (System.nanoTime()<deadline) {
            Map<String,Object> status=service.status();
            if (!Boolean.TRUE.equals(status.get("running"))) return status;
            Thread.sleep(20);
        }
        fail("LLM-Testlauf nicht beendet: "+service.status()); return Map.of();
    }
}
