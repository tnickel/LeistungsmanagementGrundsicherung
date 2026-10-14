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
        try (LlmReviewService service = serviceForTests()) {
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

    @Test void stratifiedPackageIncludesAllNineFixedEdgesBeforeOrdinaryPositiveCases() throws Exception {
        fixtures();
        var fixed=new SzenarioBibliothek(42).erzeuge(19);
        Path positive=root.resolve("testsweet/logs/run-20261013-100000/testdokumentation.md"),negative=root.resolve("testsweet/logs/run-20261013-100001/testdokumentation.md");
        StringBuilder p=new StringBuilder(Files.readString(positive)),n=new StringBuilder(Files.readString(negative));
        for (var order : fixed.subList(10,19)) p.append("\n## TF ").append(order.id).append(' ').append(order.szenarioTyp).append(" SWEET-EDGE\n").append(calculationFixture());
        for (int id=3;id<=9;id++) n.append("\n## TF ").append(id).append(" NEGATIV SWEET-NEG\n").append(calculationFixture());
        Files.writeString(positive,p);Files.writeString(negative,n);refreshReportHash(positive);refreshReportHash(negative);
        try(var service=serviceForTests()) {
            var ids=(List<?>)service.buildPackage(18).get("selectedCaseIds");
            for(int id=11;id<=19;id++){int expected=id;assertTrue(ids.stream().anyMatch(value->value.toString().startsWith("POSITIV:")&&value.toString().endsWith(":TF"+expected)),ids.toString());}
            assertFalse(ids.stream().anyMatch(value->value.toString().startsWith("POSITIV:")&&value.toString().endsWith(":TF1")));
        }
    }

    @Test void oldOffsetlessSourceCannotBeUsedAsAConfirmedCurrentReviewSource() throws Exception {
        fixtures();Path source=root.resolve("testsweet/logs/run-20261013-100000/testdokumentation.md");
        var marker=mutableJson(Files.readString(source.resolveSibling("run-state.json")));marker.put("startedAt","2026-10-25T02:55:00");
        Files.writeString(source.resolveSibling("run-state.json"),Json.write(marker));
        try(var service=serviceForTests()){assertThrows(IllegalArgumentException.class,()->service.buildPackage(2));}
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
                assertEquals(Map.of("type", "json_object"), request.get("response_format"));
                assertEquals(Map.of("type", "disabled"), request.get("thinking"));
                String user = (String) ((Map<?, ?>) ((List<?>) request.get("messages")).get(1)).get("content");
                assertTrue(user.contains("VOLLSTÄNDIGER GESETZESTEXT\nENDE GESETZ"));
                assertTrue(user.contains("VOLLSTÄNDIGER PRÜFAUFTRAG\nENDE PROMPT"));
            }
            try (LlmReviewService restored = serviceForTests()) {
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
            try (LlmReviewService restored = serviceForTests()) {
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
                // Wiederholungen tragen den exakt dokumentierten Korrekturzusatz: bei
                // gleicher Mängellage ist Versuch 3 identisch zu Versuch 2, aber der
                // Erstversuch bleibt unverändert dokumentiert.
                assertFalse(bodies.get(0).equals(bodies.get(1)), scenario);
                assertEquals(bodies.get(1), bodies.get(2), scenario);
                assertTrue(bodies.get(1).contains("KORREKTURANWEISUNG"), scenario);
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

    @Test void invalidCaseIdsAreResentWithTargetedCorrectionAndCanRecover() throws Exception {
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
            // Erstversuch ohne Zusatz; Wiederholungen mit dem Korrekturzusatz inklusive
            // Mängelliste und geforderter Check-Abdeckung je Fall.
            assertFalse(bodies.get(0).contains("KORREKTURANWEISUNG"));
            assertTrue(bodies.get(1).contains("KORREKTURANWEISUNG"));
            assertTrue(bodies.get(1).contains("Fall-ID-Liste unvollständig"));
            assertEquals(bodies.get(1), bodies.get(2)); // gleiche Mängellage, gleicher Zusatz
            Path directory = Path.of((String) status.get("artifactDirectory"));
            assertEquals(2, ((List<?>) status.get("transportChecks")).size());
            assertEquals(4, ((List<?>) status.get("attemptChecks")).size());
            assertEquals(false, ((Map<?, ?>) ((List<?>) status.get("attemptChecks")).get(0)).get("responseValidated"));
            assertEquals(false, ((Map<?, ?>) ((List<?>) status.get("attemptChecks")).get(1)).get("responseValidated"));
            assertEquals(false, ((Map<?, ?>) ((List<?>) status.get("attemptChecks")).get(0)).containsKey("korrekturHinweis"));
            assertEquals(true, ((Map<?, ?>) ((List<?>) status.get("attemptChecks")).get(1)).get("korrekturHinweis"));
            assertEquals(2, ((Number) status.get("completedCases")).intValue());
            String protocol = Files.readString(directory.resolve("protocol.md"));
            assertTrue(protocol.contains("Fall-ID-Liste unvollständig"));
            assertTrue(protocol.contains("mit Korrekturzusatz gesendet"));
            try (LlmReviewService restored = serviceForTests()) {
                assertEquals("PASS", restored.status().get("state"), restored.status().toString());
            }
        } finally { server.stop(0); }
    }

    @Test void connectionTestUsesActualModelRequest() throws Exception {
        List<String> requests = new CopyOnWriteArrayList<>();
        HttpServer server = api(requests, (ids, body) -> Json.write(Map.of("model", "glm-5.3", "id", "connection-id",
                "choices", List.of(Map.of("finish_reason", "stop", "message", Map.of("content", Json.write(Map.of("status", "SWEET_LLM_OK"))))))));
        try (LlmReviewService service = configured(server)) {
            assertEquals(true, service.testConnection().get("ok"));
            assertEquals(1, requests.size());
            assertTrue(requests.getFirst().contains("SWEET_LLM_OK"));
            Map<?, ?> request = (Map<?, ?>) Json.parse(requests.getFirst());
            assertEquals(Map.of("type", "json_object"), request.get("response_format"));
            assertEquals(Map.of("type", "disabled"), request.get("thinking"));
        } finally { server.stop(0); }
    }

    @Test void connectionTestRejectsPlaintextWrongMarkerAndAdditionalFields() throws Exception {
        for (String answer : List.of("SWEET_LLM_OK", Json.write(Map.of("status", "OTHER")),
                Json.write(Map.of("status", "SWEET_LLM_OK", "extra", true)))) {
            HttpServer server = api(new CopyOnWriteArrayList<>(), (ids, body) -> Json.write(Map.of(
                    "model", "glm-5.3", "choices", List.of(Map.of("finish_reason", "stop", "message", Map.of("content", answer))))));
            try (LlmReviewService service = configured(server)) {
                assertEquals(false, service.testConnection().get("ok"), answer);
            } finally { server.stop(0); }
        }
    }

    @Test void duplicateAndUnverifiedAnswersCannotBecomePass() {
        String id = "POSITIV:run-1:TF1";
        assertThrows(IllegalArgumentException.class, () -> LlmReviewService.validateAnswer(
                Json.write(Map.of("caseIds", List.of(id,id), "cases", List.of())), List.of(id)));
        assertThrows(IllegalArgumentException.class, () -> LlmReviewService.validateAnswer(
                Json.write(Map.of("caseIds", List.of(id), "cases", List.of(Map.of("caseId",id,"verdict","PASS","findings",List.of("ungeprüft"),"reasoning","Details")))), List.of(id)));
        assertThrows(IllegalArgumentException.class, () -> LlmReviewService.validateAnswer(Json.write(Map.of("caseIds",List.of(id),
                "cases",List.of(Map.of("caseId",id,"verdict","UNVERIFIED","findings",List.of("Daten fehlen"),"reasoning","Eingaben unvollständig")))), List.of(id)));

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

    @Test void findingsSurviveLaterTechnicalFailureAndInterruptedRestart() throws Exception {
        fixtures();
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        List<String> requests = new CopyOnWriteArrayList<>();
        HttpServer server = api(requests, (ids, body) -> calls.incrementAndGet() == 1
                ? response(ids, "FAIL", "glm-5.3", "stop") : "{\"error\":\"provider failure\"}");
        try (LlmReviewService service = configured(server)) {
            service.start(2);
            Map<String,Object> status = finished(service);
            assertEquals("ERROR", status.get("state"));
            assertEquals("FAIL", status.get("caseVerdict")); assertEquals(true, status.get("hasFailures"));
            assertTrue(Json.write(status.get("findings")).contains("konkreter Rechenfehler"));
            assertEquals(1, ((Number) status.get("completedCases")).intValue());
            assertEquals(4, requests.size()); // Ein gültiger FAIL wird nicht erneut angefragt.
            Path directory = Path.of((String) status.get("artifactDirectory"));
            Map<String,Object> interrupted = mutableJson(Files.readString(directory.resolve("status.json")));
            interrupted.put("state", "RUNNING"); Files.writeString(directory.resolve("status.json"), Json.write(interrupted));
            Files.delete(directory.resolve("result.json"));
            try (LlmReviewService restored = serviceForTests()) {
                assertEquals("ERROR", restored.status().get("state"), restored.status().toString());
                assertEquals("FAIL", restored.status().get("caseVerdict"));
                assertTrue(Json.write(restored.status().get("findings")).contains("konkreter Rechenfehler"));
            }
        } finally { server.stop(0); }
    }

    @Test void newestBegunRunSupersedesOlderPassButPreviewDoesNot() throws Exception {
        fixtures();
        HttpServer server = api(new CopyOnWriteArrayList<>(), (ids,body) -> response(ids,"PASS","glm-5.3","stop"));
        try (LlmReviewService service = configured(server)) {
            service.start(2); assertEquals("PASS", finished(service).get("state"));
            service.buildPackage(2);
            try (LlmReviewService restored = serviceForTests()) { assertEquals("PASS", restored.status().get("state")); }
            Path started = root.resolve("testsweet/logs/llm-review-interrupted-before-preparation"); Files.createDirectories(started);
            Files.writeString(started.resolve("started.json"), Json.write(Map.of("schemaVersion",2,"kind","LLM_REVIEW_START",
                    "reviewId","interrupted-before-preparation","startedAt",java.time.Instant.now().plusSeconds(1).toString(),"model","glm-5.3")));
            try (LlmReviewService restored = serviceForTests()) {
                assertEquals("UNVERIFIED", restored.status().get("state"));
                assertEquals(false, restored.status().get("transportVerified"));
                assertEquals(started.toString(), restored.status().get("artifactDirectory"));
            }
        } finally { server.stop(0); }
    }

    @Test void failTransportIsRevalidatedAndFindingsRemainVisible() throws Exception {
        fixtures();
        HttpServer server = api(new CopyOnWriteArrayList<>(), (ids,body) -> response(ids,"FAIL","glm-5.3","stop"));
        try (LlmReviewService service = configured(server)) {
            service.start(2); Map<String,Object> status = finished(service);
            assertEquals(true, status.get("transportVerified"));
            Files.delete(Path.of((String) status.get("artifactDirectory")).resolve("response-1-attempt-1.json"));
            Map<String,Object> damaged = service.status();
            assertEquals(false, damaged.get("transportVerified")); assertEquals("UNVERIFIED", damaged.get("state"));
            assertEquals(true, damaged.get("hasFailures"));
            assertTrue(Json.write(damaged.get("findings")).contains("konkreter Rechenfehler"));
        } finally { server.stop(0); }
    }

    @Test void hollowPassAndMissingBlockPersonCheckOrNumberAreRejected() throws Exception {
        for (String scenario : List.of("hollow", "block", "person", "check", "number")) {
            fixtures(); List<String> bodies = new CopyOnWriteArrayList<>();
            HttpServer server = api(bodies, (ids,body) -> {
                Map<String,Object> outer = mutableJson(response(ids,"PASS","glm-5.3","stop"));
                Map<String,Object> message = (Map<String,Object>) ((Map<?,?>)((List<?>)outer.get("choices")).getFirst()).get("message");
                Map<String,Object> answer = mutableJson((String)message.get("content"));
                Map<String,Object> c = (Map<String,Object>)((List<?>)answer.get("cases")).getFirst();
                List<?> blocks = (List<?>)c.get("calculations"); Map<String,Object> b = (Map<String,Object>)blocks.getFirst();
                if (scenario.equals("hollow")) { c.remove("calculations"); c.put("reasoning","Nicht nachgerechnet. Nur Sollwerte übernommen."); }
                if (scenario.equals("block")) c.put("calculations",List.of());
                if (scenario.equals("person")) b.put("persons",List.of());
                if (scenario.equals("check")) c.put("checks",List.of());
                if (scenario.equals("number")) ((Map<?,?>)((Map<?,?>)((List<?>)b.get("persons")).getFirst()).get("values")).remove("mehrbedarf_g");
                message.put("content",Json.write(answer)); return Json.write(outer);
            });
            try (LlmReviewService service = configured(server)) {
                service.start(2); Map<String,Object> status = finished(service);
                assertEquals("ERROR", status.get("state"), scenario + status);
                assertEquals(true, status.get("retryExhausted")); assertEquals(3,bodies.size());
            } finally { server.stop(0); }
        }
    }

    @Test void copiedWrongDocumentValueCannotProducePassAndCorrectModelFindingIsNotRetried() throws Exception {
        fixtures();
        Path source = root.resolve("testsweet/logs/run-20261013-100000/testdokumentation.md");
        Files.writeString(source, Files.readString(source).replace("bg_zahlbetrag=863.00","bg_zahlbetrag=864.00"));
        refreshReportHash(source);
        List<String> bodies = new CopyOnWriteArrayList<>();
        HttpServer server = api(bodies, (ids,body) -> response(ids,"PASS","glm-5.3","stop"));
        try (LlmReviewService service = configured(server)) {
            service.start(2); Map<String,Object> status = finished(service);
            assertEquals("FAIL", status.get("state"), status.toString());
            assertEquals(true, status.get("transportVerified")); assertEquals(2,bodies.size());
            assertTrue(Json.write(status.get("findings")).contains("Referenz 863.00 EUR, Dokument 864.00 EUR"));
        } finally { server.stop(0); }
    }

    @Test void incorrectModelArithmeticRemainsUnverifiedRatherThanGreen() throws Exception {
        fixtures();
        HttpServer server = api(new CopyOnWriteArrayList<>(), (ids,body) -> response(ids,"PASS","glm-5.3","stop")
                .replace("863.00", "864.00"));
        try (LlmReviewService service = configured(server)) {
            service.start(2); Map<String,Object> status = finished(service);
            assertEquals("UNVERIFIED", status.get("state"), status.toString());
            assertEquals(true, status.get("transportVerified"));
            assertTrue(Json.write(status.get("referenceChecks")).contains("Modell 864.00, Referenz 863.00"));
        } finally { server.stop(0); }
    }

    @Test void contradictoryPassCannotHideCheckFailureOrConcreteFindings() throws Exception {
        for (String scenario : List.of("checkFail","checkUnverified","findings")) {
            fixtures(); List<String> requests = new CopyOnWriteArrayList<>();
            HttpServer server = api(requests, (ids,body) -> {
                Map<String,Object> outer = mutableJson(response(ids,"PASS","glm-5.3","stop"));
                Map<String,Object> message = (Map<String,Object>)((Map<?,?>)((List<?>)outer.get("choices")).getFirst()).get("message");
                Map<String,Object> answer = mutableJson((String)message.get("content"));
                Map<String,Object> c = (Map<String,Object>)((List<?>)answer.get("cases")).getFirst();
                if (scenario.equals("findings")) c.put("findings",List.of("CONTROLLED_FINDING_123"));
                else {
                    Map<String,Object> check = (Map<String,Object>)((List<?>)c.get("checks")).getFirst();
                    check.put("verdict",scenario.equals("checkFail") ? "FAIL" : "UNVERIFIED"); check.put("reasoning","CONTROLLED_FINDING_123");
                }
                message.put("content",Json.write(answer)); return Json.write(outer);
            });
            try (LlmReviewService service = configured(server)) {
                service.start(2); Map<String,Object> status = finished(service);
                assertEquals(scenario.equals("checkUnverified") ? "UNVERIFIED" : "FAIL", status.get("state"),status.toString());
                assertEquals(2,requests.size()); // Eindeutige Befunde werden nicht wegwiederholt.
                assertTrue(Json.write(status.get("findings")).contains("CONTROLLED_FINDING_123"));
            } finally { server.stop(0); }
        }
    }

    @Test void newerIncompleteSimulatorSourceAndChangedSourceReportPreventOldPassReuse() throws Exception {
        fixtures();
        try (LlmReviewService service = serviceForTests()) {
            assertTrue(Boolean.TRUE.equals(service.buildPackage(2).get("ok")));
            Path source = root.resolve("testsweet/logs/run-20261013-100000/testdokumentation.md");
            Files.writeString(source, Files.readString(source) + "Changed after run\n");
            assertThrows(IllegalArgumentException.class, () -> service.buildPackage(2));
            refreshReportHash(source);
            Path aborted = root.resolve("testsweet/logs/run-20261013-200000"); Files.createDirectories(aborted);
            Files.writeString(aborted.resolve("run-state.json"),Json.write(Map.of("category","POSITIV","mode","FACHSCHICHT","state","RUNNING","success",false,"reportsComplete",false)));
            assertThrows(IllegalArgumentException.class, () -> service.buildPackage(2));
        }
    }

    @Test void rawFailFromInvalidFirstAnswerSurvivesLaterPassWithoutClaimingConfirmedSoftwareFailure() throws Exception {
        fixtures(); List<String> requests=new CopyOnWriteArrayList<>(); var calls=new java.util.concurrent.atomic.AtomicInteger();
        HttpServer server=api(requests,(ids,body)->{
            if(calls.incrementAndGet()!=1)return response(ids,"PASS","glm-5.3","stop");
            Map<String,Object> outer=mutableJson(response(ids,"FAIL","glm-5.3","stop"));
            Map<String,Object> message=(Map<String,Object>)((Map<?,?>)((List<?>)outer.get("choices")).getFirst()).get("message");
            Map<String,Object> answer=mutableJson((String)message.get("content"));
            Map<String,Object> c=(Map<String,Object>)((List<?>)answer.get("cases")).getFirst();
            c.put("findings",List.of());c.put("reasoning","RAW_FAIL_DIAGNOSE_42: Zahlbetrag könnte fehlerhaft sein");
            message.put("content",Json.write(answer));return Json.write(outer);
        });
        try(LlmReviewService service=configured(server)) {
            service.start(2);Map<String,Object> status=finished(service);
            assertEquals("UNVERIFIED",status.get("state"),status.toString());
            assertEquals(true,status.get("transportVerified"));assertEquals(false,status.get("calculationVerified"));
            assertEquals(false,status.get("reviewCoverageVerified"));assertEquals(false,status.get("hasFailures"));
            assertEquals(true,status.get("hasUnverifiedFindings"));assertEquals(3,requests.size());
            List<?> provisional=(List<?>)status.get("provisionalFailures");assertEquals(1,provisional.size());
            Map<?,?> observed=(Map<?,?>)provisional.getFirst();assertEquals("UNVALIDATED_MODEL_RESPONSE",observed.get("source"));
            assertTrue(observed.get("caseId").toString().startsWith("POSITIV:"));assertTrue(observed.get("reason").toString().contains("RAW_FAIL_DIAGNOSE_42"));
            assertTrue(Json.write(status.get("findings")).contains("Unverifizierter früherer Modellbefund"));
            Path directory=Path.of((String)status.get("artifactDirectory"));
            assertTrue(Files.readString(directory.resolve("protocol.md")).contains("RAW_FAIL_DIAGNOSE_42"));
            assertTrue(((List<?>)status.get("cases")).stream().allMatch(c->"PASS".equals(((Map<?,?>)c).get("verdict"))));
            try(LlmReviewService restored=serviceForTests()) {
                assertEquals("UNVERIFIED",restored.status().get("state"));
                assertTrue(Json.write(restored.status().get("provisionalFailures")).contains("RAW_FAIL_DIAGNOSE_42"));
            }
            assertCertificateEvidence(false);
            assertCertificateProvisionalWarning();
            Files.writeString(directory.resolve("response-1-attempt-1.json"),"{}");
            assertEquals(false,service.status().get("transportVerified"));
        } finally {server.stop(0);}
    }

    @Test void provisionalObservationsUseOnlyReadableJsonAndRequestedCaseIds() {
        String known="POSITIV:run-42:TF1";
        assertTrue(LlmReviewService.provisionalFailures("{broken FAIL",List.of(known),1,1,"request.json","response.json").isEmpty());
        assertTrue(LlmReviewService.provisionalFailures(Json.write(Map.of("cases",List.of(Map.of("caseId","foreign-case","verdict","FAIL")))),
                List.of(known),1,1,"request.json","response.json").isEmpty());
        String content=Json.write(Map.of("cases",List.of(Map.of("caseId",known,"verdict","PASS","findings",List.of("Konkreter früherer Befund"),
                "checks",List.of(Map.of("checkId","V1","verdict","FAIL","reasoning","Kontrollierte Abweichung"))))));
        var observations=LlmReviewService.provisionalFailures(content,List.of(known),1,2,"request.json","response.json");
        assertEquals(1,observations.size());assertTrue(observations.getFirst().get("reason").toString().contains("Befund: Konkreter früherer Befund"));
        assertTrue(observations.getFirst().get("reason").toString().contains("Prüfpunkt V1 meldet FAIL: Kontrollierte Abweichung"));
    }

    private void fixtures() throws Exception {
        Path tool = root.resolve("doc/testzertifikat-werkzeug"); Files.createDirectories(tool);
        Files.writeString(tool.resolve("llm-pruefauftrag.txt"), "VOLLSTÄNDIGER PRÜFAUFTRAG\nENDE PROMPT");
        Files.writeString(tool.resolve("gesetzesgrundlagen-2026.md"), "VOLLSTÄNDIGER GESETZESTEXT\nENDE GESETZ");
        Path contract = Path.of("../doc/testzertifikat-werkzeug/llm-response-contract.json");
        if (!Files.isRegularFile(contract)) contract = Path.of("doc/testzertifikat-werkzeug/llm-response-contract.json");
        Files.copy(contract, tool.resolve("llm-response-contract.json"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        for (String category : List.of("POSITIV", "NEGATIV")) {
            Path run = root.resolve("testsweet/logs/run-20261013-" + (category.equals("POSITIV") ? "100000" : "100001"));
            Files.createDirectories(run);
            Files.writeString(run.resolve("run-state.json"), Json.write(Map.of("schemaVersion",1,"kind","SIMULATOR_RUN",
                    "state","PASS","category",category,"mode","FACHSCHICHT","success",true,"reportsComplete",true,
                    "startedAt", category.equals("POSITIV") ? "2026-10-13T10:00:00Z" : "2026-10-13T10:00:01Z",
                    "buildEvidence",Map.of("fixture","isolated-http-transport"))));
            String suffix = category.equals("POSITIV") ? "POSITIVE" : "NEGATIVE";
            Files.writeString(run.resolve("testdokumentation.md"), "# testsweet Lauf\nautomatik_bestanden = true\nlauf_erfolgreich = false\nkategorie = " + category
                    + "\n## rechengrundlagen_2026\nVollständiger Kopf\n## TF 1 KURATIERT SWEET-0001\nCURATED-" + suffix
                    + "-END\n" + calculationFixture() + "\n## TF 2 KOMBINATION SWEET-0002\nCOMBINATION-" + suffix + "-END\n" + calculationFixture());
            refreshReportHash(run.resolve("testdokumentation.md"));
        }
    }
    private LlmReviewService configured(HttpServer server) {
        LlmReviewService service = serviceForTests();
        service.saveConfiguration(Map.of("baseUrl", "http://127.0.0.1:" + server.getAddress().getPort() + "/v4", "apiKey", "local-test-key", "batchSize",1));
        return service;
    }
    private static void refreshReportHash(Path source) throws Exception {
        Path stateFile = source.resolveSibling("run-state.json"); Map<String,Object> state = mutableJson(Files.readString(stateFile));
        state.put("reports",List.of(Map.of("path","testdokumentation.md","sha256",LlmReviewService.sha256(Files.readAllBytes(source)))));
        Files.writeString(stateFile,Json.write(state));
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
        List<Map<String,Object>> cases = ids.stream().map(id -> {
            Map<String,Object> c = new java.util.LinkedHashMap<>();
            c.put("caseId",id); c.put("verdict",verdict);
            c.put("findings",verdict.equals("PASS") ? List.of() : List.of("konkreter Rechenfehler"));
            c.put("reasoning","563.00 + 400.00 - 100.00 = 863.00; Budget und Workflow separat geprüft");
            c.put("calculations", List.of(Map.of("blockId","B1", "persons", List.of(Map.of("personId","p1","values",goldPerson())),
                    "household", Map.of("schonvermoegen","10000.00","bg_gesamtbedarf","963.00","bg_anrechenbar","100.00",
                    "bg_verwertbares_vermoegen","0.00","bg_zahlbetrag","863.00","budget_rest_hypothetisch","137.00"))));
            c.put("checks",List.of("V1","V2","S1").stream().map(idCheck -> Map.of("checkId",idCheck,"verdict","PASS","reasoning","Erwartung und Ist anhand vollständiger Ablaufdaten geprüft")).toList());
            return c;
        }).toList();
        return Json.write(Map.of("model",model,"id","local-provider-request","choices",List.of(Map.of("finish_reason",finish,
                "message",Map.of("content",Json.write(Map.of("schemaVersion",2,"caseIds",ids,"cases",cases)))))));
    }
    private static Map<String,String> goldPerson() {
        Map<String,String> values = new java.util.LinkedHashMap<>();
        values.put("regelbedarf","563.00"); values.put("mehrbedarf_g","0.00"); values.put("mehrbedarf_schwanger","0.00");
        values.put("mehrbedarf_alleinerziehend","0.00"); values.put("mehrbedarf_warmwasser","0.00"); values.put("kdu","400.00");
        values.put("gesamtbedarf","963.00"); values.put("freibetrag_erwerb","0.00"); values.put("absetzbetaetrag","0.00");
        values.put("anrechenbar","100.00"); values.put("zahlbetrag","863.00"); values.put("kdu_warmwasser_abzug","0.00"); return values;
    }
    private static String calculationFixture() {
        StringBuilder text = new StringBuilder("block: Goldwert Einzelperson\nzahlungsmonat = 2026-10\nvermoegen = 0.00\nschonvermoegen = 10000.00\nbudget_start = 1000.00\n"
                + "p1_eingaben: rbs=1, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=300.00, kdu_betrieb=50.00, kdu_heizung=50.00, rente=100.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00\n");
        goldPerson().forEach((k,v) -> text.append("p1_").append(k).append('=').append(v).append('\n'));
        return text + "bg_gesamtbedarf=963.00\nbg_anrechenbar=100.00\nbg_verwertbares_vermoegen=0.00\nbg_zahlbetrag=863.00\nbudget_rest_hypothetisch=137.00\n"
                + "### verifikation\nV1: OK | Zahlbetrag\n    sql = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?\n    params = [1]\n    erwartet = 863.00 | ist = 863.00\nV2: OK | Budget\n    sql = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?\n    params = [1]\n    erwartet = 137.00 | ist = 137.00\n"
                + "### protokoll\n2026-10-13 10:00:00.000 sb-test | Auszahlung anordnen -> OK — 863.00 EUR\n";
    }
    private LlmReviewService serviceForTests() {
        return new LlmReviewService(root, Map.of(), java.time.Duration.ofMillis(10), new LlmReviewService.BuildVerifier() {
            public Map<String,Object> capture(Path ignored) { return Map.of("fixture", "isolated-http-transport"); }
            public boolean matches(Map<?,?> evidence, Path ignored) { return "isolated-http-transport".equals(evidence.get("fixture")); }
        });
    }
    private static Map<String,Object> mutableJson(String text) { return new java.util.LinkedHashMap<>((Map<String,Object>)Json.parse(text)); }
    private void assertCertificateEvidence(boolean valid) throws Exception {
        Path helper = Path.of("doc/testzertifikat-werkzeug/llm-evidence.js");
        if (!Files.isRegularFile(helper)) helper = Path.of("../doc/testzertifikat-werkzeug/llm-evidence.js");
        assertTrue(Files.isRegularFile(helper));
        Process process;
        try {
            process = new ProcessBuilder("node", "-e", "const e=require(process.argv[1]).latestEvidence(process.argv[2],{buildMatches:()=>true});if(!e||e.valid!==JSON.parse(process.argv[3])){console.log(JSON.stringify(e));process.exit(1)}",
                    helper.toAbsolutePath().normalize().toString(), root.toString(), String.valueOf(valid)).redirectErrorStream(true).start();
        } catch (java.io.IOException unavailable) {
            // Der Java-Backendtest bleibt auch auf Rechnern ohne Zertifikat-Node lauffähig.
            return;
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(process.waitFor(15, java.util.concurrent.TimeUnit.SECONDS));
        assertEquals(0, process.exitValue(), output);
    }
    private void assertCertificateProvisionalWarning() throws Exception {
        Path helper=Path.of("../doc/testzertifikat-werkzeug/llm-evidence.js");
        if(!Files.isRegularFile(helper))helper=Path.of("doc/testzertifikat-werkzeug/llm-evidence.js");
        Process process=new ProcessBuilder("node","-e",
                "const e=require(process.argv[1]).latestEvidence(process.argv[2],{buildMatches:()=>true});if(e?.state!=='UNVERIFIED'||e.valid!==false||e.transportVerified!==true||!(e.provisionalFailures||[]).length||!JSON.stringify(e).includes('RAW_FAIL_DIAGNOSE_42')||(e.knownFindings||[]).some(f=>JSON.stringify(f).includes('RAW_FAIL_DIAGNOSE_42'))){console.log(JSON.stringify(e));process.exit(1)}",
                helper.toAbsolutePath().normalize().toString(),root.toString()).redirectErrorStream(true).start();
        assertTrue(process.waitFor(15,java.util.concurrent.TimeUnit.SECONDS));
        assertEquals(0,process.exitValue(),new String(process.getInputStream().readAllBytes(),StandardCharsets.UTF_8));
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
