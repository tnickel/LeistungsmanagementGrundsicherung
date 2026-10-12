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
        if (!"FACHSCHICHT".equals(modus) && !"WEB".equals(modus)) {
            sendeFehler(exchange, 400, "Unbekannter Modus: " + modus);
            return;
        }
        Simulator.Kategorie kategorie;
        try {
            kategorie = Simulator.Kategorie.valueOf(text(wunsch, "kategorie", "POSITIV"));
        } catch (IllegalArgumentException e) {
            sendeFehler(exchange, 400, "Unbekannte Kategorie: " + text(wunsch, "kategorie", ""));
            return;
        }
        if ("WEB".equals(modus) && kategorie == Simulator.Kategorie.STRESS) {
            sendeFehler(exchange, 400, "Der Stresstest (User anlegen + nachbearbeiten) ist für "
                    + "den Fachschicht-Modus vorgesehen — bitte Modus 1 wählen");
            return;
        }
        Simulator.LaufKonfiguration konfig = new Simulator.LaufKonfiguration(
                zahl(wunsch, "mitarbeiter", 10),
                zahl(wunsch, "auftraege", 60),
                zahl(wunsch, "seed", 42),
                zahl(wunsch, "denkzeitMs", 120),
                Boolean.TRUE.equals(wunsch.get("aufraeumen")),
                kategorie);
        try {
            if ("WEB".equals(modus)) {
                simulator.starteWeb(konfig);
            } else {
                simulator.starte(konfig);
            }
            sendeJson(exchange, Map.of("ok", true));
        } catch (IllegalStateException e) {
            sendeFehler(exchange, 409, e.getMessage());
        }
    }

    private Map<String, Object> status() {
        Map<String, Object> antwort = new LinkedHashMap<>();
        antwort.put("phase", simulator.phase().name());
        antwort.put("laufErfolgreich", simulator.laufErfolgreich());
        antwort.put("globalePruefungen", simulator.globalePruefungen().stream().map(p -> Map.of(
                "beschreibung", p.beschreibung(), "erwartet", p.erwartet(), "ist", p.ist(),
                "ok", p.ok(), "hinweis", p.hinweis() == null ? "" : p.hinweis())).toList());
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
        if ("WEB".equals(simulator.letzterModus())) {
            for (WebMitarbeiter m : simulator.webMitarbeiterListe()) {
                Map<String, Object> eintrag = new LinkedHashMap<>();
                eintrag.put("kuerzel", m.kuerzel + " (Browserfenster)");
                eintrag.put("aktuellerAuftrag", m.aktuellerAuftrag);
                eintrag.put("erledigt", m.erledigt);
                eintrag.put("aktiv", m.aktiv);
                leute.add(eintrag);
            }
        } else {
            for (Mitarbeiter m : simulator.mitarbeiterListe()) {
                Map<String, Object> eintrag = new LinkedHashMap<>();
                eintrag.put("kuerzel", m.kuerzel);
                eintrag.put("aktuellerAuftrag", m.aktuellerAuftrag);
                eintrag.put("erledigt", m.erledigt);
                eintrag.put("aktiv", m.aktiv);
                leute.add(eintrag);
            }
        }
        antwort.put("mitarbeiter", leute);
        antwort.put("modus", simulator.letzterModus());

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
        sendeJson(exchange, 200, antwort);
    }

    private void sendeJson(HttpExchange exchange, int code, Object antwort) throws IOException {
        byte[] bytes = Json.write(antwort).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private void sendeFehler(HttpExchange exchange, int code, String meldung) throws IOException {
        // Wirklich den übergebenen Statuscode senden (z. B. 400/404/409/500) —
        // die GUI liest die Meldung aus dem JSON-Body (ergebnis.fehler) und
        // wertet dafür response.json() aus, das auch bei Nicht-200 funktioniert.
        sendeJson(exchange, code, Map.of("ok", false, "fehler", String.valueOf(meldung)));
    }

    private void statischeDatei(HttpExchange exchange, String pfad) throws IOException {
        // Path-Traversal abwehren: Pfade mit ".."-Segmenten sind nie gewollt —
        // weder für die Classpath-Ressource noch für die Dateisystem-Fallbacks.
        if (pfad.contains("..")) {
            sendeFehler(exchange, 404, "nicht gefunden: " + pfad);
            return;
        }
        String resource = pfad.equals("/") ? "/sweet/index.html" : "/sweet" + pfad;
        String typ = resource.endsWith(".html") ? "text/html; charset=utf-8"
                : resource.endsWith(".js") ? "text/javascript; charset=utf-8"
                : resource.endsWith(".css") ? "text/css; charset=utf-8"
                : resource.endsWith(".png") ? "image/png"
                : resource.endsWith(".jpg") || resource.endsWith(".jpeg") ? "image/jpeg"
                : resource.endsWith(".svg") ? "image/svg+xml"
                : "application/octet-stream";
        byte[] bytes = null;
        try (InputStream in = getClass().getResourceAsStream(resource)) {
            if (in != null) {
                bytes = in.readAllBytes();
            }
        }
        if (bytes == null) {
            // Fallback auf Dateisystem (Entwicklungsumgebung) — jeder Kandidat
            // wird normalisiert und muss unterhalb des testsweet-Projekt-
            // verzeichnisses bleiben, sonst droht Path-Traversal.
            java.nio.file.Path basis = java.nio.file.Path.of("testsweet").toAbsolutePath().normalize();
            java.nio.file.Path[] kandidaten = {
                    java.nio.file.Path.of("testsweet/src/main/resources" + resource),
                    java.nio.file.Path.of("testsweet" + pfad)
            };
            for (java.nio.file.Path kandidat : kandidaten) {
                java.nio.file.Path normalisiert = kandidat.toAbsolutePath().normalize();
                if (!normalisiert.startsWith(basis)) {
                    continue; // Zugriff außerhalb des Projektverzeichnisses verweigern
                }
                if (java.nio.file.Files.isRegularFile(normalisiert)) {
                    bytes = java.nio.file.Files.readAllBytes(normalisiert);
                    break;
                }
            }
        }
        if (bytes == null) {
            sendeFehler(exchange, 404, "nicht gefunden: " + pfad);
            return;
        }
        exchange.getResponseHeaders().set("Content-Type", typ);
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
