package de.allgemein.lmg.web;

import de.allgemein.lmg.model.Bedarfsgemeinschaft;
import de.allgemein.lmg.model.Journaleintrag;
import de.allgemein.lmg.model.SozialFall;
import de.allgemein.lmg.model.VorgangslisteEintrag;
import de.allgemein.lmg.model.Zahlung;
import de.allgemein.lmg.service.Auszahlungsauftrag;
import de.allgemein.lmg.service.Auszahlungsergebnis;
import de.allgemein.lmg.service.Kassenlaeufergebnis;
import de.allgemein.lmg.service.LeistungsService;
import de.allgemein.lmg.service.Systemdiagnose;
import de.allgemein.lmg.service.Vorgangsdetail;
import de.allgemein.tx.FakePool;
import de.allgemein.tx.TaskController;
import de.allgemein.tx.TxManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * HTTP-Integrationstest der Webschicht (Codereview CR-014/S-14): Die größte
 * Klasse des Projekts war previously ohne jeden HTTP-Test — hier werden
 * Auth-Matrix, Statuscode-Mapping (400/401/403/404/405), Eingabegrenzen
 * (Body-Limit, JSON-Tiefe, Exponenten), Security-Header und die Einmal-
 * Druck-Token gegen einen echten HttpServer (Port 0) mit Service-Fake geprüft.
 */
class LmgServerHttpTest {

    private static final class ServiceFake implements LeistungsService {
        @Override
        public List<VorgangslisteEintrag> listeBedarfsgemeinschaften() {
            return List.of();
        }

        final java.util.List<String> detailAbrufeMitKennung = new java.util.ArrayList<>();

        @Override
        public Vorgangsdetail ladeDetail(long bgId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Vorgangsdetail ladeDetail(long bgId, String sachbearbeiterId) {
            if (sachbearbeiterId != null) {
                detailAbrufeMitKennung.add(sachbearbeiterId);
            }
            Bedarfsgemeinschaft bg = new Bedarfsgemeinschaft(bgId, "BG-HTTP", "Max Mustermann",
                    true, java.math.BigDecimal.TEN, null, 1, null, null, null,
                    java.math.BigDecimal.ZERO);
            return new Vorgangsdetail(bg, List.of(), null, List.of(), List.of(), null);
        }

        @Override
        public Optional<Bedarfsgemeinschaft> findeBedarfsgemeinschaftNachAktenzeichen(String aktenzeichen) {
            return Optional.empty();
        }

        @Override
        public Auszahlungsergebnis ordneAuszahlungAn(Auszahlungsauftrag auftrag) {
            throw new UnsupportedOperationException();
        }

        @Override
        public SozialFall aktualisiereSozialFall(SozialFall geaendert, long erwarteteVersion) {
            throw new UnsupportedOperationException();
        }

        @Override
        public SozialFall aktualisiereSozialFall(SozialFall geaendert, long erwarteteVersion, String sb) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Bedarfsgemeinschaft aktualisiereBedarfsgemeinschaft(
                long id, boolean statusAktiv, LocalDate wiedervorlageAm, long erwarteteVersion) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Bedarfsgemeinschaft aktualisiereBedarfsgemeinschaft(
                long id, boolean statusAktiv, LocalDate wiedervorlageAm,
                String iban, String bic, String kontoinhaber, long erwarteteVersion) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Bedarfsgemeinschaft aktualisiereBedarfsgemeinschaft(
                long id, boolean statusAktiv, LocalDate wiedervorlageAm,
                String iban, String bic, String kontoinhaber, java.math.BigDecimal vermoegen,
                long erwarteteVersion, String sachbearbeiterId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Zahlung> letzteZahlungen(int limit) {
            return List.of(new Zahlung(7, 1, null, "HTTP-TEST-1", "2026-10",
                    java.math.BigDecimal.TEN, "BEREIT", Instant.now(), "sb-x", "sb-y",
                    "DE89370400440532013000", "MARKDEF1100", "Max Mustermann"));
        }

        @Override
        public List<Journaleintrag> letztesJournal(int limit) {
            return List.of();
        }

        @Override
        public Bedarfsgemeinschaft legeBedarfsgemeinschaftAn(String aktenzeichen, String hauptpersonName,
                java.math.BigDecimal startBudget, LocalDate wiedervorlageAm, String sachbearbeiterId) {
            return new Bedarfsgemeinschaft(1, aktenzeichen, hauptpersonName, true, startBudget,
                    wiedervorlageAm, 1, null, null, null, java.math.BigDecimal.ZERO);
        }

        @Override
        public Bedarfsgemeinschaft legeBedarfsgemeinschaftAn(String aktenzeichen, String hauptpersonName,
                java.math.BigDecimal startBudget, LocalDate wiedervorlageAm,
                String iban, String bic, String kontoinhaber, String sachbearbeiterId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Bedarfsgemeinschaft legeBedarfsgemeinschaftAn(String aktenzeichen, String hauptpersonName,
                java.math.BigDecimal startBudget, LocalDate wiedervorlageAm,
                String iban, String bic, String kontoinhaber, java.math.BigDecimal vermoegen,
                String sachbearbeiterId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public SozialFall legeSozialFallAn(long bgId, SozialFall entwurf, String sachbearbeiterId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Zahlung freigebenZahlung(long zahlungId, String freigeberId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Zahlung freigebenZahlung(long zahlungId, String freigeberId, boolean freigeberIstAdmin) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Kassenlaeufergebnis uebergebeBereiteZahlungen(String sachbearbeiterId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Zahlung storniereZahlung(long zahlungId, String sachbearbeiterId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public de.allgemein.lmg.model.Bescheid erteileBescheid(
                long bgId, String bewilligungVon, String bewilligungBis, String sachbearbeiterId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<de.allgemein.lmg.model.Bescheid> letzterBescheid(long bgId) {
            return Optional.empty();
        }

        @Override
        public Optional<de.allgemein.lmg.model.Bescheid> letzterBescheid(long bgId, String sachbearbeiterId) {
            return Optional.empty();
        }

        @Override
        public String generiereBescheidHtml(long bgId) {
            return "<html><body><h1>Bescheid-Test</h1></body></html>";
        }

        @Override
        public String generiereBescheidHtml(long bgId, String sachbearbeiterId) {
            return "<html><body><h1>Bescheid-Test</h1></body></html>";
        }
    }

    private FakePool pool;
    private TaskController tasks;
    private LmgServer server;
    private HttpClient client;
    private String basis;
    private ServiceFake serviceFake;

    @BeforeEach
    void aufbauen() throws IOException {
        pool = new FakePool(4);
        TxManager txManager = new TxManager(pool);
        tasks = new TaskController(txManager, 4, 50);
        Systemdiagnose diagnose = new Systemdiagnose(txManager, tasks, null, Instant.now());
        Benutzerverwaltung benutzerverwaltung = new Benutzerverwaltung(
                "admin-01:admin:admin;sb-01:sb-01:sachbearbeiter", Duration.ofMinutes(60));
        serviceFake = new ServiceFake();
        server = new LmgServer(tasks, serviceFake, diagnose, null, benutzerverwaltung, 0);
        server.start();
        int port = server.adresse().getPort();
        basis = "http://127.0.0.1:" + port;
        client = HttpClient.newHttpClient();
    }

    @AfterEach
    void abbauen() {
        server.close();
        tasks.close();
    }

    private HttpResponse<String> sende(String methode, String pfad, String koerper, String token) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(basis + pfad))
                    .method(methode, HttpRequest.BodyPublishers.ofString(koerper == null ? "" : koerper));
            builder.header("Content-Type", "application/json");
            if (token != null) {
                builder.header("Authorization", "Bearer " + token);
            }
            return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String meldeAn(String kennung, String passwort) {
        HttpResponse<String> antwort = sende("POST", "/api/anmeldung",
                "{\"kennung\":\"" + kennung + "\",\"passwort\":\"" + passwort + "\"}", null);
        assertEquals(200, antwort.statusCode());
        return Json.parse(antwort.body()) instanceof Map<?, ?> map
                ? String.valueOf(map.get("token")) : null;
    }

    @Test
    void ohneTokenGibtEs401() {
        assertEquals(401, sende("GET", "/api/werbin", null, null).statusCode());
    }

    @Test
    void mitTokenGibtEs200UndIdentitaet() {
        String token = meldeAn("sb-01", "sb-01");
        assertNotNull(token);
        HttpResponse<String> antwort = sende("GET", "/api/werbin", null, token);
        assertEquals(200, antwort.statusCode());
        assertTrue(antwort.body().contains("sb-01"));
    }

    @Test
    void falschesPasswortGibt400Fachfehler() {
        assertEquals(400, sende("POST", "/api/anmeldung",
                "{\"kennung\":\"sb-01\",\"passwort\":\"falsch\"}", null).statusCode());
    }

    @Test
    void stornoMitAbschliessendemSchraegstrichUmgehtAdminPflichtNicht() {
        String token = meldeAn("sb-01", "sb-01");
        for (String pfad : List.of("/api/zahlungen/7/storno", "/api/zahlungen/7/storno/",
                "/api/zahlungen/7/storno//", "/api/kassenlauf/", "/api/system/status/")) {
            assertEquals(403, sende("POST", pfad, "{}", token).statusCode(), pfad);
        }
    }

    @Test
    void ueberlangeIdInDruckPfadGibtKein500() {
        String token = meldeAn("sb-01", "sb-01");
        int status = sende("GET", "/api/bedarfsgemeinschaften/99999999999999999999999/bescheid/druck",
                null, token).statusCode();
        assertTrue(status == 400 || status == 404, "erwartet 4xx, war " + status);
    }

    @Test
    void adminEndpunktFuerSachbearbeiterGibt403() {
        String token = meldeAn("sb-01", "sb-01");
        assertEquals(403, sende("GET", "/api/system/status", null, token).statusCode());
    }

    @Test
    void unbekannterPfadGibt404() {
        String token = meldeAn("sb-01", "sb-01");
        assertEquals(404, sende("GET", "/api/gibtsnicht", null, token).statusCode());
    }

    @Test
    void bekannteRouteMitFalscherMethodeGibt405() {
        String token = meldeAn("sb-01", "sb-01");
        HttpResponse<String> antwort = sende("GET", "/api/abmeldung", null, token);
        assertEquals(405, antwort.statusCode());
        assertTrue(antwort.body().contains("POST"), "Meldung nennt die erlaubte Methode");
    }

    @Test
    void muellBodyGibt400() {
        String token = meldeAn("sb-01", "sb-01");
        assertEquals(400, sende("POST", "/api/bedarfsgemeinschaften", "nicht-json", token).statusCode());
    }

    @Test
    void ungueltigesDatumGibt400Nicht500() {
        String token = meldeAn("sb-01", "sb-01");
        HttpResponse<String> antwort = sende("POST", "/api/bedarfsgemeinschaften",
                "{\"aktenzeichen\":\"BG-1\",\"hauptpersonName\":\"M\",\"startBudget\":100,"
                        + "\"wiedervorlageAm\":\"31.02.2026\"}", token);
        assertEquals(400, antwort.statusCode());
        assertTrue(antwort.body().contains("JJJJ-MM-TT"));
    }

    @Test
    void negativerLimitGibt400Nicht500() {
        String token = meldeAn("sb-01", "sb-01");
        assertEquals(400, sende("GET", "/api/zahlungen?limit=-5", null, token).statusCode());
        assertEquals(400, sende("GET", "/api/zahlungen?limit=abc", null, token).statusCode());
    }

    @Test
    void koerperUeberEinemMegabyteGibt400() {
        String token = meldeAn("sb-01", "sb-01");
        String riesig = "{\"x\":\"" + "a".repeat(1_100_000) + "\"}";
        assertEquals(400, sende("POST", "/api/bedarfsgemeinschaften", riesig, token).statusCode());
    }

    @Test
    void jsonTiefenbombeGibt400() {
        String token = meldeAn("sb-01", "sb-01");
        String bombe = "[".repeat(200) + "]".repeat(200);
        assertEquals(400, sende("POST", "/api/bedarfsgemeinschaften", bombe, token).statusCode());
    }

    @Test
    void exponentenBombeGibt400StattOutOfMemory() {
        String token = meldeAn("sb-01", "sb-01");
        assertEquals(400, sende("POST", "/api/bedarfsgemeinschaften",
                "{\"aktenzeichen\":\"B\",\"hauptpersonName\":\"M\",\"startBudget\":1e2000000000}",
                token).statusCode());
    }

    @Test
    void apiAntwortTraegtSicherheitsHeader() {
        HttpResponse<String> antwort = sende("POST", "/api/anmeldung",
                "{\"kennung\":\"sb-01\",\"passwort\":\"sb-01\"}", null);
        assertEquals(200, antwort.statusCode());
        assertEquals("no-store", antwort.headers().firstValue("Cache-Control").orElse(""));
        assertTrue(antwort.headers().firstValue("Content-Security-Policy").orElse("").startsWith("default-src"));
        assertEquals("nosniff", antwort.headers().firstValue("X-Content-Type-Options").orElse(""));
    }

    @Test
    void druckTokenGiltGenauEinmalUndFremdeTokenBleibenDraussen() {
        String token = meldeAn("sb-01", "sb-01");
        HttpResponse<String> tokenAntwort = sende("POST", "/api/drucktoken", "{}", token);
        assertEquals(200, tokenAntwort.statusCode());
        Map<?, ?> body = (Map<?, ?>) Json.parse(tokenAntwort.body());
        String druckToken = String.valueOf(body.get("drucktoken"));

        HttpResponse<String> druck = sende("GET",
                "/api/bedarfsgemeinschaften/1/bescheid/druck?token=" + druckToken, null, null);
        assertEquals(200, druck.statusCode());
        assertTrue(druck.body().contains("Bescheid-Test"));
        assertEquals("no-store", druck.headers().firstValue("Cache-Control").orElse(""),
                "Bescheide dürfen auch nach Verbrauch des Druck-Tokens nicht im Cache bleiben");

        assertEquals(401, sende("GET",
                "/api/bedarfsgemeinschaften/1/bescheid/druck?token=" + druckToken, null, null).statusCode(),
                "One-Time: zweiter Abruf abgewiesen");
        assertEquals(401, sende("GET",
                "/api/bedarfsgemeinschaften/1/bescheid/druck?token=crime", null, null).statusCode(),
                "Fremdes Token abgewiesen");
        assertFalse(druck.headers().firstValue("Content-Security-Policy").orElse("").isEmpty());
        assertEquals("no-store", sende("GET",
                "/api/bedarfsgemeinschaften/1/bescheid/druck?token=ungueltig", null, null)
                .headers().firstValue("Cache-Control").orElse(""));
    }

    @Test
    void abmeldungBeendetSitzung() {
        String token = meldeAn("sb-01", "sb-01");
        assertEquals(200, sende("POST", "/api/abmeldung", "{}", token).statusCode());
        assertEquals(401, sende("GET", "/api/werbin", null, token).statusCode());
    }

    @Test
    void zahlungsUebersichtOhneBankverbindung() {
        // CR-035/Option D: Sammelübersicht liefert keine IBAN/BIC — Bankverbindungs-
        // daten erscheinen nur im protokollierten Einzel-Kontext.
        String token = meldeAn("sb-01", "sb-01");
        HttpResponse<String> antwort = sende("GET", "/api/zahlungen", null, token);
        assertEquals(200, antwort.statusCode());
        assertFalse(antwort.body().contains("DE89"), "Keine IBAN in der Sammelübersicht");
        assertFalse(antwort.body().contains("MARKDEF"), "Kein BIC in der Sammelübersicht");
        assertTrue(antwort.body().contains("Mustermann"), "Empfängername bleibt sichtbar");
    }

    @Test
    void detailAbrufProtokolliertDieEinsicht() {
        // Der Fake-Service merkt sich die kennung-behafteten Aufrufe: Der Webserver
        // muss die Sitzungs-Kennung an ladeDetail weiterreichen, damit das
        // Zugriffsprotokoll (Option D) greift.
        String token = meldeAn("sb-01", "sb-01");
        assertEquals(200, sende("GET", "/api/bedarfsgemeinschaften/1", null, token).statusCode());
        assertTrue(serviceFake.detailAbrufeMitKennung.contains("sb-01"),
                "ladeDetail wurde mit der Kennung der Sitzung aufgerufen");
    }
}
