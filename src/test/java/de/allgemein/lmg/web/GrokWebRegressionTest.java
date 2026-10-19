package de.allgemein.lmg.web;

import de.allgemein.lmg.model.Bedarfsgemeinschaft;
import de.allgemein.lmg.model.Bescheid;
import de.allgemein.lmg.model.VorgangslisteEintrag;
import de.allgemein.lmg.model.Zahlung;
import de.allgemein.lmg.service.Auszahlungsergebnis;
import de.allgemein.lmg.service.LeistungsService;
import de.allgemein.lmg.service.Vorgangsdetail;
import de.allgemein.tx.FakePool;
import de.allgemein.tx.TaskController;
import de.allgemein.tx.TaskOutcomeUnknownException;
import de.allgemein.tx.TxManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Echter HTTP-Rand; Fachlogik und JDBC-Abbruch werden in ihren eigenen Tests geprüft. */
class GrokWebRegressionTest {
    private TaskController tasks;
    private LmgServer server;
    private HttpClient client;
    private String base;
    private String token;
    private final AtomicReference<List<Object>> argumente = new AtomicReference<>();
    private final AtomicReference<Auszahlungsergebnis> auszahlungAntwort = new AtomicReference<>();

    @BeforeEach
    void start() throws Exception {
        LeistungsService service = (LeistungsService) Proxy.newProxyInstance(
                LeistungsService.class.getClassLoader(), new Class<?>[]{LeistungsService.class},
                (proxy, methode, args) -> switch (methode.getName()) {
                    case "ordneAuszahlungAn" -> {
                        if(auszahlungAntwort.get()!=null)yield auszahlungAntwort.get();
                        throw new TaskOutcomeUnknownException("Synthetischer Task-Abbruch nach möglicher Fachwirkung", null);
                    }
                    case "listeBedarfsgemeinschaften" -> {
                        argumente.set(args==null?List.of():List.of(args));
                        yield List.of(new VorgangslisteEintrag(7,"BG-LISTE","Testperson",true,
                                BigDecimal.TEN,null,BigDecimal.ONE,args==null?"2026-10":(String)args[0]));
                    }
                    case "ladeDetail" -> {
                        argumente.set(List.of(args));
                        var bg = new Bedarfsgemeinschaft((Long) args[0], "BG-HTTP", "Testperson",
                                true, BigDecimal.TEN, null, 1);
                        yield new Vorgangsdetail(bg, List.of(), null, List.of(), List.of(), null,
                                args.length == 3 ? (String) args[2] : null);
                    }
                    case "aendereBescheid" -> {
                        argumente.set(List.of(args));
                        yield new Bescheid(2, (Long) args[0], "B-NEU", LocalDate.of(2026, 10, 15),
                                (String) args[1], (String) args[2], BigDecimal.TEN, "ERTEILT",
                                (String) args[3], Instant.parse("2026-10-15T10:00:00Z"));
                    }
                    case "generiereBescheidHtml" -> "<html><body>Test</body></html>";
                    default -> throw new UnsupportedOperationException(methode.getName());
                });
        tasks = new TaskController(new TxManager(new FakePool(4)), 2, 20);
        server = new LmgServer(tasks, service, null,
                new Benutzerverwaltung("sb-test:pass:sachbearbeiter"), 0);
        server.start();
        base = "http://127.0.0.1:" + server.adresse().getPort();
        client = HttpClient.newHttpClient();
        var anmeldung = sende("POST", "/api/anmeldung", "{\"kennung\":\"sb-test\",\"passwort\":\"pass\"}");
        assertEquals(200, anmeldung.statusCode());
        token = String.valueOf(((Map<?, ?>) Json.parse(anmeldung.body())).get("token"));
    }

    @AfterEach
    void close() {
        if (server != null) server.close();
        if (tasks != null) tasks.close();
    }

    @Test
    void unbekannterTaskAusgangFordertKeineNeueAnordnung() throws Exception {
        var antwort = sende("POST", "/api/auszahlung",
                "{\"bgId\":1,\"vorgangsNr\":\"HTTP-GROK-1\",\"zahlungsmonat\":\"2026-10\"}");
        assertEquals(409, antwort.statusCode());
        assertTrue(antwort.body().contains("AUSGANG_UNBEKANNT"));
        assertTrue(antwort.body().contains("Status neu laden"));
        assertFalse(antwort.body().contains("bitte erneut versuchen"));
    }

    @Test
    void detailUebergibtGewaehltenMonatUndSitzungskennung() throws Exception {
        var antwort = sende("GET", "/api/bedarfsgemeinschaften/7?monat=2027-01", "");
        assertEquals(200, antwort.statusCode());
        assertEquals(List.of(7L, "sb-test", "2027-01"), argumente.get());
        assertEquals("2027-01", ((Map<?, ?>) Json.parse(antwort.body())).get("berechnungsmonat"));
    }

    @Test
    void detailVerweigertUngueltigenOderMehrdeutigenMonatVorFachaufruf() throws Exception {
        for (String query : List.of("monat=", "monat=2026-13", "monat=0000-01", "monat=2026-1",
                "monat=2026-01&monat=2027-01", "monat", "monat=2026-01&%6Donat=2027-01")) {
            argumente.set(null);
            assertEquals(400, sende("GET", "/api/bedarfsgemeinschaften/7?" + query, "").statusCode(), query);
            assertEquals(400, sende("GET", "/api/bedarfsgemeinschaften?" + query, "").statusCode(), query);
            assertEquals(null, argumente.get(), "Ungültige Eingaben erreichen keine Fachmethode");
        }
    }

    @Test
    void listeUebergibtGewaehltenMonatUndLiefertIhnJeVorschau() throws Exception {
        var antwort=sende("GET","/api/bedarfsgemeinschaften?monat=2027-01","");
        assertEquals(200,antwort.statusCode());assertEquals(List.of("2027-01"),argumente.get());
        var liste=(List<?>)((Map<?,?>)Json.parse(antwort.body())).get("eintraege");
        assertEquals("2027-01",((Map<?,?>)liste.getFirst()).get("berechnungsmonat"));
    }

    @Test
    void auszahlungsMappingBeherrschtDenFormalErlaubtenNullVorgang() throws Exception {
        // Der aktuelle Produktservice liefert stets eine BG; die öffentliche Record erzwingt das nicht.
        var zahlung=new Zahlung(1,1,null,"HTTP-NULL-BG","2026-10",BigDecimal.TEN,"ANGELEGT",
                Instant.EPOCH,"sb-test",null,null,null,"Testperson");
        auszahlungAntwort.set(new Auszahlungsergebnis(zahlung,null,true));
        var antwort=sende("POST","/api/auszahlung","{\"bgId\":1,\"vorgangsNr\":\"HTTP-NULL-BG\",\"zahlungsmonat\":\"2026-10\"}");
        assertEquals(200,antwort.statusCode());
        var body=(Map<?,?>)Json.parse(antwort.body());
        assertTrue(body.containsKey("bedarfsgemeinschaft"));assertEquals(null,body.get("bedarfsgemeinschaft"));
        assertEquals(Boolean.TRUE,body.get("bereitsGebucht"));
    }

    @Test
    void aenderungsbescheidUebergibtZeitraumUndKennungUeberPut() throws Exception {
        var antwort = sende("PUT", "/api/bedarfsgemeinschaften/7/bescheid",
                "{\"bewilligungVon\":\"2026-11\",\"bewilligungBis\":\"2027-09\",\"sachbearbeiterId\":\"admin\"}");
        assertEquals(200, antwort.statusCode(), antwort.body());
        assertEquals(List.of(7L, "2026-11", "2027-09", "sb-test"), argumente.get());
        assertTrue(antwort.body().contains("Änderungsbescheid"));
    }

    @Test
    void alleDarstellbarenAntwortenVerweigernFrames() throws Exception {
        for (String pfad : List.of("/", "/app.js", "/unbekannt", "/api/werbin",
                "/api/bedarfsgemeinschaften/7/bescheid/druck")) {
            var antwort = sende("GET", pfad, "");
            assertTrue(antwort.headers().firstValue("Content-Security-Policy").orElse("")
                    .contains("frame-ancestors 'none'"), pfad);
        }
    }

    private HttpResponse<String> sende(String methode, String pfad, String body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create(base + pfad)).header("Content-Type", "application/json")
                .method(methode, HttpRequest.BodyPublishers.ofString(body));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
