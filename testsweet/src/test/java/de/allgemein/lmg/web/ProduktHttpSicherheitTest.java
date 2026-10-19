package de.allgemein.lmg.web;

import de.allgemein.lmg.model.Zahlung;
import de.allgemein.lmg.model.Bedarfsgemeinschaft;
import de.allgemein.lmg.service.FachlicheException;
import de.allgemein.lmg.service.LeistungsService;
import de.allgemein.tx.ConnectionPool;
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
import java.sql.Connection;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * testsweet-Eigentest gegen den echten LmgServer aus dem gebauten Produkt-JAR.
 * Routing, Authentifizierung, Rollenprüfung und Drucktoken stammen ausschließlich
 * aus dem Produkt. Der Fachservice zeichnet nur erreichbare Schreib-/Leseaufrufe auf;
 * er implementiert ausdrücklich keinen eigenen Berechtigungsfilter. SQL/Oracle und
 * Browser sind hier nicht erforderlich. Das gemeinsame Webpaket eröffnet nur die
 * bestehende paketprivate Testuhr, damit Idle-Grenzen ohne Schlafen prüfbar sind.
 */
class ProduktHttpSicherheitTest {

    private static final Instant START = Instant.parse("2026-10-15T10:00:00Z");
    private final AtomicInteger stornoAufrufe = new AtomicInteger();
    private final AtomicInteger druckAufrufe = new AtomicInteger();
    private final AtomicInteger eingabeAufrufe = new AtomicInteger();
    private final AtomicReference<Zahlung> zahlung = new AtomicReference<>();
    private Benutzerverwaltung benutzer;
    private TaskController tasks;
    private LmgServer server;
    private HttpClient client;
    private String basis;

    @BeforeEach
    void aufbauen() throws Exception {
        benutzer = new Benutzerverwaltung("admin-test:admin:admin;sb-test:sb:sachbearbeiter",
                Duration.ofSeconds(30));
        benutzer.setzeUhrFuerTests(Clock.fixed(START, ZoneOffset.UTC));
        zahlung.set(zahlung("BEREIT", "sb-test"));
        LeistungsService aufzeichnenderService = (LeistungsService) Proxy.newProxyInstance(
                LeistungsService.class.getClassLoader(), new Class<?>[]{LeistungsService.class},
                (proxy, methode, argumente) -> switch (methode.getName()) {
                    case "legeSozialFallAn", "aktualisiereSozialFall" -> {
                        eingabeAufrufe.incrementAndGet();
                        yield argumente[methode.getName().equals("legeSozialFallAn") ? 1 : 0];
                    }
                    case "legeBedarfsgemeinschaftAn" -> {
                        eingabeAufrufe.incrementAndGet();
                        yield new Bedarfsgemeinschaft(1, (String)argumente[0], (String)argumente[1], true,
                                (BigDecimal)argumente[2], null, 1);
                    }
                    case "storniereZahlung" -> {
                        stornoAufrufe.incrementAndGet();
                        assertEquals(7L, argumente[0]);
                        zahlung.set(zahlung("STORNIERT", (String) argumente[1]));
                        yield zahlung.get();
                    }
                    case "generiereBescheidHtml" -> {
                        druckAufrufe.incrementAndGet();
                        assertEquals(2, argumente.length, "Einsicht erhält die Sitzungskennung");
                        assertEquals("sb-test", argumente[1]);
                        yield "<html><body>Bescheid für BG " + argumente[0] + "</body></html>";
                    }
                    case "toString" -> "Aufzeichnender Fachservice ohne Berechtigungsfilter";
                    default -> throw new UnsupportedOperationException(methode.getName());
                });
        tasks = new TaskController(new TxManager(new SqlFreierPool()), 2, 20);
        server = new LmgServer(tasks, aufzeichnenderService, null, benutzer, 0);
        server.start();
        basis = "http://127.0.0.1:" + server.adresse().getPort();
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    @AfterEach
    void abbauen() {
        if (server != null) server.close();
        if (tasks != null) tasks.close();
    }

    @Test
    void unbekannteGeldfelderUndSubcentwerteErreichenDenProduktserviceNicht() throws Exception {
        String sitzung = anmelden("sb-test", "sb");
        for (String field : List.of("kdumiete", "kduMiete", "kduBetriebskosten", "kduHeizung", "einkommenRente",
                "einkommenErwerb", "abzSteuern", "abzSozialversicherung", "abzVersicherungen", "abzErwerbsaufwendungen")) {
            for (String method : List.of("POST", "PUT")) {
                String path = method.equals("POST") ? "/api/bedarfsgemeinschaften/1/sozialfaelle" : "/api/sozialfaelle/1";
                var response = sende(method, path, "{\"regelbedarfsstufe\":1,\"version\":1,\"" + field + "\":12.345}", sitzung);
                assertEquals(400, response.statusCode(), method + " " + field);
                assertEquals("VALIDATION", ((Map<?,?>)Json.parse(response.body())).get("code"));
            }
        }
        for (String field : List.of("startBudget", "vermoegen", "startBuget")) {
            var response = sende("POST", "/api/bedarfsgemeinschaften",
                    "{\"aktenzeichen\":\"SWEET-INPUT\",\"hauptpersonName\":\"Test\",\"" + field + "\":12.345}", sitzung);
            assertEquals(400, response.statusCode(), field);
        }
        assertEquals(0, eingabeAufrufe.get());
    }

    @Test
    void optionaleGeldwerteUndZusatznullstellenBleibenImProduktvertragErlaubt() throws Exception {
        String sitzung = anmelden("sb-test", "sb");
        var response = sende("PUT", "/api/sozialfaelle/1",
                "{\"regelbedarfsstufe\":1,\"version\":1,\"kduMiete\":12.3400,\"einkommenRente\":null}", sitzung);
        assertEquals(200, response.statusCode(), response.body());
        Map<?,?> fall = (Map<?,?>)((Map<?,?>)Json.parse(response.body())).get("sozialfall");
        assertEquals(new BigDecimal("12.34"), fall.get("kduMiete"));
        assertEquals(new BigDecimal("0.00"), fall.get("kduHeizung"));
        assertEquals(new BigDecimal("0.00"), fall.get("einkommenRente"));
        var created = sende("POST", "/api/bedarfsgemeinschaften",
                "{\"aktenzeichen\":\"SWEET-ZERO\",\"hauptpersonName\":\"Test\",\"startBudget\":null}", sitzung);
        assertEquals(200, created.statusCode(), created.body());
        assertEquals(new BigDecimal("0.00"), ((Map<?,?>)((Map<?,?>)Json.parse(created.body()))
                .get("bedarfsgemeinschaft")).get("budgetRest"));
        assertEquals(2, eingabeAufrufe.get());
    }

    @Test
    void sachbearbeiterKannStornoMitKeinerIdSchreibweiseAusloesen() throws Exception {
        String sitzung = anmelden("sb-test", "sb");
        for (String id : List.of("7", "+7", "%2B7", "%D9%A7", "0007", "-7")) {
            for (String ende : List.of("", "/", "//")) {
                HttpResponse<String> antwort = sende("POST", "/api/zahlungen/" + id + "/storno" + ende,
                        "{}", sitzung);
                assertEquals(403, antwort.statusCode(), id + ende);
                assertTrue(antwort.body().contains("BERECHTIGUNG"), antwort.body());
            }
        }
        assertEquals(0, stornoAufrufe.get(), "verbotene Requests dürfen den Fachservice nicht erreichen");
        assertEquals("BEREIT", zahlung.get().status());
        String admin = anmelden("admin-test", "admin");
        assertEquals(200, sende("POST", "/api/zahlungen/7/storno", "{}", admin).statusCode());
        assertEquals(1, stornoAufrufe.get(), "positive Kontrolle: Admin erreicht denselben Fachservice");
        assertEquals("STORNIERT", zahlung.get().status());
        assertEquals("admin-test", zahlung.get().sachbearbeiterId());
    }

    @Test
    void logoutWiderruftUnbenutztenDrucklinkAuchNachErneuterAnmeldung() throws Exception {
        String sitzung = anmelden("sb-test", "sb");
        String druck = druckToken(sitzung, 1);
        assertEquals(200, sende("POST", "/api/abmeldung", "{}", sitzung).statusCode());
        assertEquals(401, sende("GET", "/api/werbin", "", sitzung).statusCode());
        anmelden("sb-test", "sb");
        assertEquals(401, drucken(druck, 1).statusCode());
        assertEquals(0, druckAufrufe.get(), "widerrufene Links laden keine Sozialdaten");
    }

    @Test
    void drucklinkPrueftSitzungsIdleVorAblaufDerSechzigSekunden() throws Exception {
        String druck = druckToken(anmelden("sb-test", "sb"), 1);
        benutzer.setzeUhrFuerTests(Clock.fixed(START.plusSeconds(30), ZoneOffset.UTC));
        assertEquals(401, drucken(druck, 1).statusCode());
        assertEquals(0, druckAufrufe.get());
    }

    @Test
    void drucklinkBleibtEinmaligUndAufSeineBgBeschraenkt() throws Exception {
        String sitzung = anmelden("sb-test", "sb");
        String falschesZiel = druckToken(sitzung, 1);
        assertEquals(401, drucken(falschesZiel, 2).statusCode());
        assertEquals(401, drucken(falschesZiel, 1).statusCode(), "Fehlabruf verbraucht ebenfalls");
        assertEquals(0, druckAufrufe.get());
        String gueltig = druckToken(sitzung, 1);
        HttpResponse<String> druck = drucken(gueltig, 1);
        assertEquals(200, druck.statusCode());
        assertTrue(druck.body().contains("Bescheid für BG 1"));
        assertEquals("no-store", druck.headers().firstValue("Cache-Control").orElse(""));
        assertEquals(401, drucken(gueltig, 1).statusCode());
        assertEquals(1, druckAufrufe.get());
    }

    @Test
    void drucktokenBodyErfordertPositiveGanzzahlUndHeaderSitzung() throws Exception {
        String sitzung = anmelden("sb-test", "sb");
        for (String body : List.of("", "{}", "{\"bgId\":0}", "{\"bgId\":-1}",
                "{\"bgId\":1.5}", "{\"bgId\":\"1\"}")) {
            assertEquals(400, sende("POST", "/api/drucktoken", body, sitzung).statusCode(), body);
        }
        assertEquals(401, sende("POST", "/api/drucktoken?token=" + sitzung,
                "{\"bgId\":1}", null).statusCode());
        assertEquals(0, druckAufrufe.get());
    }

    @Test
    void direkteDruckanfrageNutztNurHeaderSitzungOderDrucklink() throws Exception {
        String sitzung = anmelden("sb-test", "sb");
        String pfad = "/api/bedarfsgemeinschaften/1/bescheid/druck";
        assertEquals(200, sende("GET", pfad, "", sitzung).statusCode());
        assertEquals(401, sende("GET", pfad + "?token=" + sitzung, "", null).statusCode());
        String druck = druckToken(sitzung, 1);
        assertEquals(401, sende("GET", pfad + "?token=" + druck, "", "ungueltig").statusCode());
        assertEquals(200, drucken(druck, 1).statusCode(), "ungültiger Header verbraucht das Querytoken nicht");
        assertEquals(2, druckAufrufe.get());
    }

    @Test
    void produktOberflaecheUndDruckVerweigernEinbettung() throws Exception {
        String sitzung = anmelden("sb-test", "sb");
        for (String pfad : List.of("/", "/app.js", "/nicht-vorhanden", "/api/werbin",
                "/api/bedarfsgemeinschaften/1/bescheid/druck")) {
            HttpResponse<String> antwort = sende("GET", pfad, "", sitzung);
            assertTrue(antwort.headers().firstValue("Content-Security-Policy").orElse("")
                    .contains("frame-ancestors 'none'"), pfad);
        }
    }

    @Test
    void defektesKontensegmentVeroeffentlichtKeinPasswortInAusnahmen() {
        String geheimnis = "SYNTHETISCHER_TESTSWEET_SCHLUESSEL";
        for (String roh : List.of("konto:" + geheimnis, "konto:" + geheimnis + ":admin:extra",
                "konto:x:" + geheimnis)) {
            IllegalArgumentException fehler = assertThrows(IllegalArgumentException.class,
                    () -> new Benutzerverwaltung(roh));
            assertTrue(!fehler.toString().contains(geheimnis), fehler.toString());
        }
    }

    @Test
    void normalisierteDrucklinksBewahrenEinmalTokenMethodenUndZielbindung() throws Exception {
        String sitzung=anmelden("sb-test","sb");
        for(String ende:List.of("","/","//")) {
            String pfad="/api/bedarfsgemeinschaften/1/bescheid/druck"+ende;
            String druck=druckToken(sitzung,1);
            assertEquals(401,sende("POST",pfad+"?token="+druck,"{}",null).statusCode(),"Querytoken gilt nur für GET-Druck");
            assertEquals(405,sende("POST",pfad+"?token="+druck,"{}",sitzung).statusCode(),"Druck bleibt GET-only");
            assertEquals(200,sende("GET",pfad+"?token="+druck,"",null).statusCode(),ende);
            assertEquals(401,sende("GET",pfad+"?token="+druck,"",null).statusCode(),"Einmalverbrauch bleibt bestehen");
            String falsch=druckToken(sitzung,1);
            assertEquals(401,sende("GET","/api/bedarfsgemeinschaften/2/bescheid/druck"+ende+"?token="+falsch,"",null).statusCode());
            assertEquals(401,sende("GET",pfad+"?token="+falsch,"",null).statusCode(),"Falsches Ziel verbraucht den Token");
        }
        String druck=druckToken(sitzung,1);
        assertEquals(401,sende("GET","/api//bedarfsgemeinschaften/1/bescheid/druck?token="+druck,"",null).statusCode());
        assertEquals(200,drucken(druck,1).statusCode(),"Innere Doppelslashes eröffnen keine Druckroute");
        String abgemeldet=druckToken(sitzung,1);
        assertEquals(200,sende("POST","/api/abmeldung","{}",sitzung).statusCode());
        assertEquals(401,sende("GET","/api/bedarfsgemeinschaften/1/bescheid/druck//?token="+abgemeldet,"",null).statusCode());
    }

    @Test
    void vollePhantomSperrmapDrosseltDasNeueEchteZielkontoAuchUeberHttp() throws Exception {
        // Nur die Vorbereitung ist direkt: alle entscheidenden Zielkonto-Requests laufen über HTTP.
        for (int i = 0; i < 1024; i++) {
            String phantom = "phantom-" + i;
            for (int j = 0; j < 5; j++) {
                assertThrows(FachlicheException.class, () -> benutzer.anmelden(phantom, "x"));
            }
        }
        for (int i = 0; i < 5; i++) {
            HttpResponse<String> antwort = sende("POST", "/api/anmeldung",
                    "{\"kennung\":\"sb-test\",\"passwort\":\"falsch\"}", null);
            assertEquals(400, antwort.statusCode());
        }
        HttpResponse<String> gesperrt = sende("POST", "/api/anmeldung",
                "{\"kennung\":\"sb-test\",\"passwort\":\"sb\"}", null);
        assertEquals(400, gesperrt.statusCode());
        assertTrue(gesperrt.body().contains("Zu viele fehlgeschlagene"), gesperrt.body());
        assertTrue(anmelden("admin-test", "admin") != null, "andere echte Konten bleiben verfügbar");
    }

    private static Zahlung zahlung(String status, String sachbearbeiter) {
        return new Zahlung(7, 1, null, "SWEET-HTTP-AUTH", "2026-10", BigDecimal.TEN,
                status, START, sachbearbeiter, "sb-freigabe", null, null, "Testperson");
    }

    private String anmelden(String kennung, String passwort) throws Exception {
        HttpResponse<String> antwort = sende("POST", "/api/anmeldung", Json.write(Map.of(
                "kennung", kennung, "passwort", passwort)), null);
        assertEquals(200, antwort.statusCode(), antwort.body());
        return String.valueOf(((Map<?, ?>) Json.parse(antwort.body())).get("token"));
    }

    private String druckToken(String sitzung, long bgId) throws Exception {
        HttpResponse<String> antwort = sende("POST", "/api/drucktoken", "{\"bgId\":" + bgId + "}", sitzung);
        assertEquals(200, antwort.statusCode(), antwort.body());
        return String.valueOf(((Map<?, ?>) Json.parse(antwort.body())).get("drucktoken"));
    }

    private HttpResponse<String> drucken(String druck, long bgId) throws Exception {
        return sende("GET", "/api/bedarfsgemeinschaften/" + bgId + "/bescheid/druck?token=" + druck,
                "", null);
    }

    private HttpResponse<String> sende(String methode, String pfad, String body, String sitzung) throws Exception {
        HttpRequest.Builder anfrage = HttpRequest.newBuilder(URI.create(basis + pfad))
                .timeout(Duration.ofSeconds(5)).header("Content-Type", "application/json")
                .method(methode, HttpRequest.BodyPublishers.ofString(body));
        if (sitzung != null) anfrage.header("Authorization", "Bearer " + sitzung);
        return client.send(anfrage.build(), HttpResponse.BodyHandlers.ofString());
    }

    /** Erfüllt nur die TxManager-Verbindungsgrenze; kein simuliertes SQL oder Security-Verhalten. */
    private static final class SqlFreierPool implements ConnectionPool {
        @Override
        public Connection getConnection() {
            return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                    new Class<?>[]{Connection.class}, (proxy, methode, argumente) -> switch (methode.getName()) {
                        case "setAutoCommit", "setTransactionIsolation", "commit", "rollback", "close" -> null;
                        case "getAutoCommit", "isClosed" -> false;
                        case "isValid" -> true;
                        case "toString" -> "SQL-freie Testverbindung";
                        default -> throw new UnsupportedOperationException(methode.getName());
                    });
        }

        @Override
        public void release(Connection verbindung) { }

        @Override
        public void close() { }
    }
}
