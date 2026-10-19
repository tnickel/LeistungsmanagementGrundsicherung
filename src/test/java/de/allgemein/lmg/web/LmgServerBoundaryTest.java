package de.allgemein.lmg.web;

import de.allgemein.lmg.model.SozialFall;
import de.allgemein.lmg.model.Bedarfsgemeinschaft;
import de.allgemein.lmg.service.LeistungsService;
import de.allgemein.lmg.service.Systemdiagnose;
import de.allgemein.tx.FakePool;
import de.allgemein.tx.TaskController;
import de.allgemein.tx.TxException;
import de.allgemein.tx.TxManager;
import de.allgemein.tx.TxOptions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

/** Echte HTTP-Grenzen: Fehler müssen vor jedem Service-Aufruf abgefangen werden. */
class LmgServerBoundaryTest {
    private LmgServer server;
    private TaskController tasks;
    private String base;
    private String token;
    private final AtomicInteger calls = new AtomicInteger();
    private boolean failPrint;

    @BeforeEach void start() throws Exception {
        var tx = new TxManager(new FakePool(8));
        tasks = new TaskController(tx, 1, 1);
        var users = new Benutzerverwaltung("sb:pass:sachbearbeiter", Duration.ofMinutes(5));
        LeistungsService service = (LeistungsService) Proxy.newProxyInstance(
                LeistungsService.class.getClassLoader(), new Class<?>[]{LeistungsService.class}, (p, method, args) -> {
                    calls.incrementAndGet();
                    return switch (method.getName()) {
                        case "legeSozialFallAn" -> args[1];
                        case "aktualisiereSozialFall" -> args[0];
                        case "legeBedarfsgemeinschaftAn" -> new Bedarfsgemeinschaft(1, (String)args[0],
                                (String)args[1], true, (java.math.BigDecimal)args[2], null, 1);
                        case "generiereBescheidHtml" -> {
                            if (failPrint) throw new TxException("Geheimname DE89370400440532013000",
                                    new java.sql.SQLException("Geheime Falldaten", "23000", 12899), false, 1);
                            yield "<html>Bewilligung</html>";
                        }
                        default -> throw new UnsupportedOperationException(method.getName());
                    };
                });
        server = new LmgServer(tasks, service, new Systemdiagnose(tx, tasks, null, Instant.now()), users, 0);
        server.start();
        base = "http://127.0.0.1:" + server.adresse().getPort();
        var login = send("POST", "/api/anmeldung", "{\"kennung\":\"sb\",\"passwort\":\"pass\"}", null);
        assertEquals(200, login.statusCode());
        token = String.valueOf(((Map<?, ?>) Json.parse(login.body())).get("token"));
    }

    @AfterEach void stop() { if (server != null) server.close(); if (tasks != null) tasks.close(); }

    @Test void invalidFinancialTypesAndIntegersNeverReachService() throws Exception {
        var invalid = List.<Map.Entry<String, Object>>of(
                Map.entry("regelbedarfsstufe", new java.math.BigDecimal("1.5")),
                Map.entry("regelbedarfsstufe", 4294967297L),
                Map.entry("regelbedarfsstufe", "1"),
                Map.entry("anzKinderUnter7", 4294967296L),
                Map.entry("anzKinderUnter16", new java.math.BigDecimal("2.7")),
                Map.entry("anzKinderMinderjaehrig", "4"),
                Map.entry("schwanger", "ja"), Map.entry("merkzeichenG", 1),
                Map.entry("warmwasser", "true"), Map.entry("kduMiete", "100"));
        for (var field : invalid) {
            Map<String, Object> body = new LinkedHashMap<>(Map.of("regelbedarfsstufe", 1, "version", 1));
            body.put(field.getKey(), field.getValue());
            for (String method : List.of("POST", "PUT")) {
                String path = method.equals("POST") ? "/api/bedarfsgemeinschaften/1/sozialfaelle" : "/api/sozialfaelle/1";
                var response = send(method, path, Json.write(body), token);
                assertEquals(400, response.statusCode(), method + " " + field);
                assertEquals("VALIDATION", ((Map<?, ?>) Json.parse(response.body())).get("code"));
            }
        }
        assertEquals(0, calls.get(), "Kein ungültiger Wert darf geschrieben werden");
    }

    @Test void fractionalAndOverflowingLongIdentifiersAreRejected() throws Exception {
        for (String value : List.of("1.5", "9223372036854775808", "18446744073709551617")) {
            assertEquals(400, send("POST", "/api/auszahlung",
                    "{\"bgId\":" + value + ",\"vorgangsNr\":\"id\",\"zahlungsmonat\":\"2026-10\"}", token).statusCode());
            assertEquals(400, send("PUT", "/api/sozialfaelle/1",
                    "{\"regelbedarfsstufe\":1,\"version\":" + value + "}", token).statusCode());
        }
        assertEquals(0, calls.get());
    }

    @Test void omittedOptionalFieldsAndExactLongVersionRemainSupported() throws Exception {
        var response = send("PUT", "/api/sozialfaelle/1",
                "{\"regelbedarfsstufe\":1,\"version\":9223372036854775807}", token);
        assertEquals(200, response.statusCode());
        Map<?, ?> row = (Map<?, ?>) ((Map<?, ?>) Json.parse(response.body())).get("sozialfall");
        assertEquals(Long.MAX_VALUE, ((java.math.BigDecimal) row.get("version")).longValueExact());
        assertEquals(false, row.get("schwanger"));
        assertEquals(1, calls.get());
    }

    @Test void unknownFieldsAreRejectedOnEveryDataWritingRouteBeforeCallingService() throws Exception {
        for (String route : List.of("POST /api/bedarfsgemeinschaften", "PUT /api/bedarfsgemeinschaften/1",
                "POST /api/bedarfsgemeinschaften/1/sozialfaelle", "PUT /api/sozialfaelle/1",
                "POST /api/bedarfsgemeinschaften/1/bescheid", "PUT /api/bedarfsgemeinschaften/1/bescheid",
                "POST /api/auszahlung")) {
            String[] parts = route.split(" ");
            var response = send(parts[0], parts[1], "{\"kdumiete\":100}", token);
            assertEquals(400, response.statusCode(), route);
            assertEquals("VALIDATION", ((Map<?,?>)Json.parse(response.body())).get("code"));
            assertTrue(response.body().contains("unbekanntes Feld"), response.body());
        }
        assertEquals(0, calls.get());
    }

    @Test void allFinancialFieldsRejectSubcentValuesBeforeCallingService() throws Exception {
        for (String field : List.of("kduMiete", "kduBetriebskosten", "kduHeizung", "einkommenRente",
                "einkommenErwerb", "abzSteuern", "abzSozialversicherung", "abzVersicherungen", "abzErwerbsaufwendungen")) {
            for (String method : List.of("POST", "PUT")) {
                String path = method.equals("POST") ? "/api/bedarfsgemeinschaften/1/sozialfaelle" : "/api/sozialfaelle/1";
                var response = send(method, path, "{\"regelbedarfsstufe\":1,\"version\":1,\"" + field + "\":12.345}", token);
                assertEquals(400, response.statusCode(), method + " " + field);
                assertEquals("VALIDATION", ((Map<?,?>)Json.parse(response.body())).get("code"));
            }
        }
        assertEquals(400, send("POST", "/api/bedarfsgemeinschaften",
                "{\"aktenzeichen\":\"BG-EXACT\",\"hauptpersonName\":\"Test\",\"startBudget\":12.345}", token).statusCode());
        assertEquals(400, send("PUT", "/api/bedarfsgemeinschaften/1",
                "{\"statusAktiv\":true,\"version\":1,\"vermoegen\":12.345}", token).statusCode());
        assertEquals(0, calls.get());
    }

    @Test void optionalZeroAndLosslesslyNormalizableMoneyRemainSupported() throws Exception {
        var response = send("PUT", "/api/sozialfaelle/1",
                "{\"regelbedarfsstufe\":1,\"version\":1,\"kduMiete\":12.3400,\"einkommenRente\":null}", token);
        assertEquals(200, response.statusCode(), response.body());
        Map<?,?> row = (Map<?,?>)((Map<?,?>)Json.parse(response.body())).get("sozialfall");
        assertEquals(new java.math.BigDecimal("12.34"), row.get("kduMiete"));
        assertEquals(new java.math.BigDecimal("0.00"), row.get("kduHeizung"));
        assertEquals(new java.math.BigDecimal("0.00"), row.get("einkommenRente"));
        for (String extra : List.of("", ",\"startBudget\":null")) {
            var created = send("POST", "/api/bedarfsgemeinschaften",
                    "{\"aktenzeichen\":\"BG-ZERO\",\"hauptpersonName\":\"Test\"" + extra + "}", token);
            assertEquals(200, created.statusCode(), created.body());
            assertEquals(new java.math.BigDecimal("0.00"), ((Map<?,?>)((Map<?,?>)Json.parse(created.body()))
                    .get("bedarfsgemeinschaft")).get("budgetRest"));
        }
        assertEquals(3, calls.get());
    }

    @Test void printRejectsOverloadWith503AndRetryAfter() throws Exception {
        CountDownLatch running = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        var first = tasks.submit("occupied", TxOptions.DEFAULT, ctx -> {
            running.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            return null;
        });
        assertTrue(running.await(2, TimeUnit.SECONDS));
        var queued = tasks.submit("queued", TxOptions.DEFAULT, ctx -> null);
        try {
            var response = send("GET", "/api/bedarfsgemeinschaften/1/bescheid/druck", null, token);
            assertEquals(503, response.statusCode());
            assertEquals("2", response.headers().firstValue("Retry-After").orElseThrow());
            assertEquals("UEBERLAST", ((Map<?, ?>) Json.parse(response.body())).get("code"));
            assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
            assertEquals(0, calls.get());
        } finally { release.countDown(); first.result(); queued.result(); }
    }

    @Test void technicalPrintErrorsDoNotLogSocialDataOrThrowable() throws Exception {
        failPrint = true;
        var records = new ArrayList<LogRecord>();
        Handler capture = new Handler() {
            @Override public void publish(LogRecord record) { records.add(record); }
            @Override public void flush() { }
            @Override public void close() { }
        };
        Logger logger = Logger.getLogger(LmgServer.class.getName());
        logger.addHandler(capture);
        try {
            var response = send("GET", "/api/bedarfsgemeinschaften/1/bescheid/druck", null, token);
            assertEquals(500, response.statusCode());
            assertFalse(records.isEmpty());
            for (var record : records) {
                assertNull(record.getThrown());
                assertFalse(record.getMessage().contains("Geheim"));
                assertFalse(record.getMessage().contains("DE893"));
            }
        } finally { logger.removeHandler(capture); }
    }

    @Test void staticFilesRejectWritingMethodsWithAllowHeader() throws Exception {
        for (String method : List.of("POST", "PUT", "DELETE")) {
            var response = send(method, "/", "{}", null);
            assertEquals(405, response.statusCode());
            assertEquals("GET, HEAD", response.headers().firstValue("Allow").orElseThrow());
        }
        assertEquals(0, calls.get());
    }

    @Test void staticHeadHasSameLengthAsGetAndNeverReturnsBody() throws Exception {
        var get = send("GET", "/", null, null);
        var head = send("HEAD", "/", null, null);
        assertEquals(200, get.statusCode());
        assertEquals(200, head.statusCode());
        assertEquals(get.headers().firstValue("Content-Length"), head.headers().firstValue("Content-Length"));
        assertEquals("", head.body());
        var missing = send("HEAD", "/missing", null, null);
        assertEquals(404, missing.statusCode());
        assertEquals("", missing.body());
    }

    private HttpResponse<String> send(String method, String path, String body, String token) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(5))
                .method(method, HttpRequest.BodyPublishers.ofString(body == null ? "" : body));
        if (token != null) request.header("Authorization", "Bearer " + token);
        return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
