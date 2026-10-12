package de.allgemein.lmg.web;

import de.allgemein.lmg.calc.BerechnungsSnapshot;
import de.allgemein.lmg.calc.Bewilligungsplan;
import de.allgemein.lmg.calc.Leistungsberechnung;
import de.allgemein.lmg.calc.Satzstand;
import de.allgemein.lmg.model.Bescheid;
import de.allgemein.lmg.model.SozialFall;
import de.allgemein.lmg.service.LeistungsService;
import de.allgemein.lmg.service.Systemdiagnose;
import de.allgemein.tx.FakePool;
import de.allgemein.tx.TaskController;
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
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** Prüft das HTTP-Ausgabeformat eines gespeicherten Monatsplans mit echtem HttpServer. */
class BescheidMonatsplanHttpTest {
    private LmgServer server;
    private TaskController tasks;
    private String basis;
    private Bescheid bescheid;
    private final HttpClient client = HttpClient.newHttpClient();

    @BeforeEach
    void aufbauen() throws Exception {
        var tx = new TxManager(new FakePool(2));
        tasks = new TaskController(tx, 2, 20);
        LeistungsService service = (LeistungsService) Proxy.newProxyInstance(
                LeistungsService.class.getClassLoader(), new Class<?>[]{LeistungsService.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "letzterBescheid" -> Optional.of(bescheid);
                    case "erteileBescheid" -> bescheid;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        var benutzer = new Benutzerverwaltung("sb-review:review:sachbearbeiter", Duration.ofMinutes(60));
        server = new LmgServer(tasks, service, new Systemdiagnose(tx, tasks, null, Instant.now()), null, benutzer, 0);
        server.start();
        basis = "http://127.0.0.1:" + server.adresse().getPort();
    }

    @AfterEach
    void abbauen() {
        if (server != null) server.close();
        if (tasks != null) tasks.close();
    }

    @Test
    void apiLiefertStartmonatsbetragUndVerbindlicheVariableMonatsbetraege() throws Exception {
        BigDecimal zero = new BigDecimal("0.00");
        var fall = new SozialFall(1, 7, 1, false, false,
                new BigDecimal("300.00"), zero, zero, zero, zero, 1);
        var plan = Bewilligungsplan.erstelle(7, List.of(fall), Leistungsberechnung.KduParameter.AUS,
                new BigDecimal("10500.00"), "2026-10", "2026-11", monat -> Satzstand.REFERENZ_2026);
        bescheid = new Bescheid(1, 7, "B-HTTP-MONATE", LocalDate.of(2026, 10, 12),
                "2026-10", "2026-11", new BigDecimal("363.00"), "ERTEILT", "sb-review", Instant.EPOCH,
                BerechnungsSnapshot.schreibe(plan));
        String token = anmelden();
        for (String methode : List.of("GET", "POST")) {
            var antwort = sende(methode, "/api/bedarfsgemeinschaften/7/bescheid",
                    "{\"bewilligungVon\":\"2026-10\",\"bewilligungBis\":\"2026-11\"}", token);
            assertEquals(200, antwort.statusCode());
            Map<?, ?> daten = (Map<?, ?>) ((Map<?, ?>) Json.parse(antwort.body())).get("bescheid");
            assertEquals(0, new BigDecimal(daten.get("zahlbetrag").toString()).compareTo(new BigDecimal("363.00")));
            assertEquals(Boolean.TRUE, daten.get("zahlbetragVariabel"));
            List<?> monate = (List<?>) daten.get("monatsbetraege");
            assertEquals(2, monate.size());
            assertEquals("2026-10", ((Map<?, ?>) monate.get(0)).get("monat"));
            assertEquals("2026-11", ((Map<?, ?>) monate.get(1)).get("monat"));
            assertEquals(0, new BigDecimal(((Map<?, ?>) monate.get(0)).get("zahlbetrag").toString())
                    .compareTo(new BigDecimal("363.00")));
            assertEquals(0, new BigDecimal(((Map<?, ?>) monate.get(1)).get("zahlbetrag").toString())
                    .compareTo(new BigDecimal("863.00")));
        }
    }

    @Test
    void apiBleibtFuerAlteBescheideOhneMonatsplanKompatibel() throws Exception {
        bescheid = new Bescheid(1, 7, "B-HTTP-ALT", LocalDate.of(2026, 10, 12),
                "2026-10", "2026-11", new BigDecimal("863.00"), "ERTEILT", "sb-review", Instant.EPOCH);
        var antwort = sende("GET", "/api/bedarfsgemeinschaften/7/bescheid", null, anmelden());
        assertEquals(200, antwort.statusCode());
        Map<?, ?> daten = (Map<?, ?>) ((Map<?, ?>) Json.parse(antwort.body())).get("bescheid");
        assertEquals(Boolean.FALSE, daten.get("zahlbetragVariabel"));
        assertEquals(List.of(), daten.get("monatsbetraege"));
        assertEquals(0, new BigDecimal(daten.get("zahlbetrag").toString()).compareTo(new BigDecimal("863.00")));
    }

    private String anmelden() throws Exception {
        var antwort = sende("POST", "/api/anmeldung", "{\"kennung\":\"sb-review\",\"passwort\":\"review\"}", null);
        assertEquals(200, antwort.statusCode());
        return ((Map<?, ?>) Json.parse(antwort.body())).get("token").toString();
    }

    private HttpResponse<String> sende(String methode, String pfad, String body, String token) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(basis + pfad)).header("Content-Type", "application/json")
                .method(methode, HttpRequest.BodyPublishers.ofString(body == null ? "" : body));
        if (token != null) request.header("Authorization", "Bearer " + token);
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
