package de.allgemein.testsweet;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import de.allgemein.lmg.web.Json;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lokale Weboberfläche des Simulators (nur für den localhost bestimmt, keine
 * Anmeldung — reines Entwicklungswerkzeug). Liefert die statische Seite aus
 * den Ressourcen und eine kleine JSON-API:
 *
 * <pre>
 *   GET  /api/status          Phase, Konfiguration, Kennzahlen, Mitarbeiter, Logticker
 *   POST /api/start           Lauf starten (Body: JSON mit Konfiguration)
 *   POST /api/stop            Lauf abbrechen
 *   GET  /api/auftraege       Auftragsliste (kompakt)
 *   GET  /api/auftrag/{id}    Auftrag im Detail: Schritte + Verifikationsprüfungen
 * </pre>
 */
final class SweetServer {

    private final Simulator simulator;
    private final HttpServer server;

    SweetServer(Simulator simulator, int port) throws IOException {
        this.simulator = simulator;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/", this::bearbeite);
        server.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(4));
    }

    void start() {
        server.start();
    }

    private void bearbeite(HttpExchange exchange) throws IOException {
        String pfad = exchange.getRequestURI().getPath();
        try {
            switch (pfad) {
                case "/api/status" -> sendeJson(exchange, status());
                case "/api/start" -> {
                    if ("POST".equals(exchange.getRequestMethod())) {
                        starteLauf(exchange);
                    } else {
                        sendeFehler(exchange, 405, "nur POST");
                    }
                }
                case "/api/stop" -> {
                    simulator.stoppe();
                    sendeJson(exchange, Map.of("ok", true, "phase", simulator.phase().name()));
                }
                case "/api/pdf" -> lieferePdf(exchange);
                case "/api/md" -> liefereMd(exchange);
                case "/api/auftraege" -> sendeJson(exchange, Map.of("auftraege", auftragsliste()));
                default -> {
                    if (pfad.startsWith("/api/auftrag/")) {
                        auftragDetail(exchange, pfad.substring("/api/auftrag/".length()));
                    } else if ("GET".equals(exchange.getRequestMethod())) {
                        statischeDatei(exchange, pfad);
                    } else {
                        sendeFehler(exchange, 404, "unbekannt: " + pfad);
                    }
                }
            }
        } catch (Exception e) {
            sendeFehler(exchange, 500, e.getClass().getSimpleName() + ": " + e.getMessage());
        } finally {
            exchange.close();
        }
    }

    // ------------------------------------------------------------------
    // API-Handler
    // ------------------------------------------------------------------

    private void starteLauf(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<?, ?> wunsch = (Map<?, ?>) Json.parse(body);
        if (wunsch == null) {
            sendeFehler(exchange, 400, "kein JSON-Body");
            return;
        }
        String modus = text(wunsch, "modus", "FACHSCHICHT");
        if (!"FACHSCHICHT".equals(modus)) {
            sendeFehler(exchange, 400,
                    "Modus " + modus + " ist noch nicht implementiert (Schritt 2: Selenium)");
            return;
        }
        Simulator.LaufKonfiguration konfig = new Simulator.LaufKonfiguration(
                zahl(wunsch, "mitarbeiter", 10),
                zahl(wunsch, "auftraege", 60),
                zahl(wunsch, "seed", 42),
                zahl(wunsch, "denkzeitMs", 120),
                Boolean.TRUE.equals(wunsch.get("aufraeumen")));
        try {
            simulator.starte(konfig);
            sendeJson(exchange, Map.of("ok", true));
        } catch (IllegalStateException e) {
            sendeFehler(exchange, 409, e.getMessage());
        }
    }

    private Map<String, Object> status() {
        Map<String, Object> antwort = new LinkedHashMap<>();
        antwort.put("phase", simulator.phase().name());
        antwort.put("db", Map.of("url", simulator.dbUrl, "user", simulator.dbUser));
        antwort.put("laufdauerSekunden", simulator.laufdauerSekunden());
        antwort.put("letzteFehlermeldung", simulator.letzteFehlermeldung());
        antwort.put("pdf", simulator.letztesPdf());
        antwort.put("konfiguration", simulator.konfiguration() == null
                ? null
                : Map.of("mitarbeiter", simulator.konfiguration().mitarbeiter(),
                        "auftraege", simulator.konfiguration().auftraege(),
                        "seed", simulator.konfiguration().seed(),
                        "denkzeitMs", simulator.konfiguration().denkzeitMs()));

        Map<String, Long> kennzahlen = new LinkedHashMap<>();
        for (Auftrag.Status status : Auftrag.Status.values()) {
            kennzahlen.put(status.name(), simulator.auftraege().stream()
                    .filter(a -> a.status == status).count());
        }
        antwort.put("kennzahlen", kennzahlen);

        List<Map<String, Object>> leute = new ArrayList<>();
        for (Mitarbeiter m : simulator.mitarbeiterListe()) {
            Map<String, Object> eintrag = new LinkedHashMap<>();
            eintrag.put("kuerzel", m.kuerzel);
            eintrag.put("aktuellerAuftrag", m.aktuellerAuftrag);
            eintrag.put("erledigt", m.erledigt);
            eintrag.put("aktiv", m.aktiv);
            leute.add(eintrag);
        }
        antwort.put("mitarbeiter", leute);

        SweetLog log = simulator.log();
        antwort.put("logs", log == null ? List.of() : log.letzteZeilen(120));
        antwort.put("logVerzeichnis", log == null ? simulator.letztesLogVerzeichnis()
                : log.runVerzeichnis.toString());
        return antwort;
    }

    private List<Map<String, Object>> auftragsliste() {
        List<Map<String, Object>> liste = new ArrayList<>();
        for (Auftrag a : simulator.auftraege()) {
            Map<String, Object> eintrag = new LinkedHashMap<>();
            eintrag.put("id", a.id);
            eintrag.put("szenarioTyp", a.szenarioTyp);
            eintrag.put("aktenzeichen", a.aktenzeichen);
            eintrag.put("hauptperson", a.hauptperson);
            eintrag.put("beschreibung", a.beschreibung);
            eintrag.put("status", a.status.name());
            eintrag.put("bearbeiter", a.bearbeiter);
            eintrag.put("dauerMs", a.dauerMs());
            eintrag.put("fehlerText", a.fehlerText);
            liste.add(eintrag);
        }
        return liste;
    }

    private void auftragDetail(HttpExchange exchange, String idText) throws IOException {
        int id;
        try {
            id = Integer.parseInt(idText);
        } catch (NumberFormatException e) {
            sendeFehler(exchange, 400, "Auftrags-ID muss numerisch sein");
            return;
        }
        Auftrag auftrag = simulator.auftraege().stream()
                .filter(a -> a.id == id).findFirst().orElse(null);
        if (auftrag == null) {
            sendeFehler(exchange, 404, "Auftrag " + id + " nicht gefunden");
            return;
        }
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("id", auftrag.id);
        detail.put("szenarioTyp", auftrag.szenarioTyp);
        detail.put("aktenzeichen", auftrag.aktenzeichen);
        detail.put("hauptperson", auftrag.hauptperson);
        detail.put("beschreibung", auftrag.beschreibung);
        detail.put("status", auftrag.status.name());
        detail.put("bearbeiter", auftrag.bearbeiter);
        detail.put("bgId", auftrag.bgId);
        detail.put("fehlerText", auftrag.fehlerText);
        detail.put("dauerMs", auftrag.dauerMs());
        detail.put("schritte", auftrag.schritte().stream()
                .map(s -> Map.of("zeit", s.zeit().toString(), "mitarbeiter", s.mitarbeiter(),
                        "aktion", s.aktion(), "ergebnis", s.ergebnis()))
                .toList());
        detail.put("pruefungen", auftrag.pruefungen().stream()
                .map(p -> Map.of("beschreibung", p.beschreibung(), "erwartet", p.erwartet(),
                        "ist", String.valueOf(p.ist()), "ok", p.ok(),
                        "hinweis", String.valueOf(p.hinweis())))
                .toList());
        sendeJson(exchange, detail);
    }

    // ------------------------------------------------------------------
    // Hilfsfunktionen
    // ------------------------------------------------------------------

    /** Maschinenlesbare Markdown-Fassung (für Nachprüfung durch KI/Dritte). */
    private void liefereMd(HttpExchange exchange) throws IOException {
        String pfad = simulator.letztesMd();
        if (pfad == null) {
            sendeFehler(exchange, 404, "Noch keine Markdown-Fassung vorhanden — zuerst einen Lauf starten");
            return;
        }
        byte[] bytes = java.nio.file.Files.readAllBytes(java.nio.file.Path.of(pfad));
        exchange.getResponseHeaders().set("Content-Type", "text/markdown; charset=utf-8");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    /**
     * Liefert die Testdokumentation des letzten Laufs über HTTP aus — der
     * eingebettete Browser blockiert file://-Links, daher dieser Umweg.
     */
    private void lieferePdf(HttpExchange exchange) throws IOException {
        String pfad = simulator.letztesPdf();
        if (pfad == null) {
            sendeFehler(exchange, 404, "Noch keine Testdokumentation vorhanden — zuerst einen Lauf starten");
            return;
        }
        java.nio.file.Path datei = java.nio.file.Path.of(pfad);
        if (!java.nio.file.Files.exists(datei)) {
            sendeFehler(exchange, 404, "Testdokumentation fehlt auf der Festplatte: " + pfad);
            return;
        }
        byte[] bytes = java.nio.file.Files.readAllBytes(datei);
        exchange.getResponseHeaders().set("Content-Type", "application/pdf");
        exchange.getResponseHeaders().set("Content-Disposition",
                "inline; filename=\"testdokumentation.pdf\"");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static String text(Map<?, ?> map, String schluessel, String standard) {
        Object wert = map.get(schluessel);
        return wert == null ? standard : String.valueOf(wert);
    }

    private static int zahl(Map<?, ?> map, String schluessel, int standard) {
        Object wert = map.get(schluessel);
        if (wert instanceof Number n) {
            return n.intValue();
        }
        try {
            return wert == null ? standard : Integer.parseInt(String.valueOf(wert));
        } catch (NumberFormatException e) {
            return standard;
        }
    }

    private void sendeJson(HttpExchange exchange, Object antwort) throws IOException {
        byte[] bytes = Json.write(antwort).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private void sendeFehler(HttpExchange exchange, int code, String meldung) throws IOException {
        sendeJson(exchange, Map.of("ok", false, "fehler", String.valueOf(meldung)));
        // Statuscode bewusst 200 lassen? Nein — Meldung transportieren:
        // (Wir nutzen bewusst 200 + ok=false, damit die GUI die Meldung anzeigen kann.)
    }

    private void statischeDatei(HttpExchange exchange, String pfad) throws IOException {
        String resource = pfad.equals("/") ? "/sweet/index.html" : "/sweet" + pfad;
        String typ = resource.endsWith(".html") ? "text/html; charset=utf-8"
                : resource.endsWith(".js") ? "text/javascript; charset=utf-8"
                : resource.endsWith(".css") ? "text/css; charset=utf-8"
                : "application/octet-stream";
        try (InputStream in = getClass().getResourceAsStream(resource)) {
            if (in == null) {
                sendeFehler(exchange, 404, "nicht gefunden: " + pfad);
                return;
            }
            byte[] bytes = in.readAllBytes();
            exchange.getResponseHeaders().set("Content-Type", typ);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        }
    }
}
