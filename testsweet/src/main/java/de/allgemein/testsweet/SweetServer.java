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
import java.util.Comparator;
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
final class SweetServer implements AutoCloseable {

    private final Object operationLock = new Object();
    private final Object reportLock = new Object();
    private volatile String reviewedPdf, reviewedMd, reviewedSource, reviewedKey;
    private final Simulator simulator;
    private final HttpServer server;
    private final java.util.concurrent.ExecutorService executor;
    private final LlmReviewService llm;
    private final java.nio.file.Path repository;
    private final java.util.concurrent.atomic.AtomicBoolean certificateBusy = new java.util.concurrent.atomic.AtomicBoolean();

    SweetServer(Simulator simulator, int port) throws IOException {
        this(simulator, port, repositoryRoot());
    }

    SweetServer(Simulator simulator, int port, java.nio.file.Path repository) throws IOException {
        this.simulator = simulator;
        this.repository = repository.toAbsolutePath().normalize();
        this.llm = new LlmReviewService(this.repository);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/", this::bearbeite);
        executor = java.util.concurrent.Executors.newFixedThreadPool(4);
        server.setExecutor(executor);
    }

    void start() {
        server.start();
    }

    private void bearbeite(HttpExchange exchange) throws IOException {
        String pfad = exchange.getRequestURI().getPath();
        try {
            if (!localRequest(exchange)) {
                sendeFehler(exchange, 403, "Nur Anfragen derselben lokalen Oberfläche sind zulässig");
                return;
            }
            switch (pfad) {
                case "/api/status" -> sendeJson(exchange, status());
                case "/api/start" -> {
                    if ("POST".equals(exchange.getRequestMethod())) {
                        synchronized (operationLock) { starteLauf(exchange); }
                    } else {
                        sendeFehler(exchange, 405, "nur POST");
                    }
                }
                case "/api/stop" -> {
                    if (!"POST".equals(exchange.getRequestMethod())) { sendeFehler(exchange, 405, "nur POST"); return; }
                    simulator.stoppe();
                    sendeJson(exchange, Map.of("ok", true, "phase", simulator.phase().name()));
                }
                case "/api/pdf" -> lieferePdf(exchange);
                case "/api/md" -> liefereMd(exchange);
                case "/api/zertifikat" -> {
                    if ("POST".equals(exchange.getRequestMethod())) {
                        erzeugeZertifikat(exchange);
                    } else {
                        sendeFehler(exchange, 405, "nur POST");
                    }
                }
                case "/api/zertifikat/pdf" -> liefereZertifikatPdf(exchange);
                case "/api/auftraege" -> sendeJson(exchange, Map.of("auftraege", auftragsliste()));
                case "/api/testfaelle" -> sendeJson(exchange, testfallKatalog(exchange));
                case "/api/llm/config" -> {
                    if ("GET".equals(exchange.getRequestMethod())) sendeJson(exchange, llm.configuration());
                    else if ("POST".equals(exchange.getRequestMethod())) {
                        llm.saveConfiguration(readJsonBody(exchange));
                        sendeJson(exchange, llm.configuration());
                    } else sendeFehler(exchange, 405, "nur GET oder POST");
                }
                case "/api/llm/test" -> {
                    if ("POST".equals(exchange.getRequestMethod())) sendeJson(exchange, llm.testConnection());
                    else sendeFehler(exchange, 405, "nur POST");
                }
                case "/api/llm/start" -> {
                    if (!"POST".equals(exchange.getRequestMethod())) { sendeFehler(exchange, 405, "nur POST"); return; }
                    synchronized (operationLock) {
                    if (simulator.laufAktiv() || certificateBusy.get()) {
                        sendeFehler(exchange, 409, "Zuerst den Simulator-Lauf abschließen"); return;
                    }
                    Map<?, ?> body = readJsonBody(exchange);
                    sendeJson(exchange, 202, llm.start(zahl(body, "faelle", ((Number) llm.configuration().get("cases")).intValue())));
                    }
                }
                case "/api/llm/status" -> sendeJson(exchange, llm.status());
                case "/api/llm/report/pdf" -> liefereKiNachweis(exchange);
                case "/api/llmpruefung" -> liefereLlmPruefung(exchange);
                case "/api/llmpruefung/paket" -> {
                    if ("POST".equals(exchange.getRequestMethod())) {
                        erzeugePruefpaket(exchange);
                    } else {
                        sendeFehler(exchange, 405, "nur POST");
                    }
                }
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
        } catch (IllegalArgumentException e) {
            sendeFehler(exchange, 400, e.getMessage());
        } catch (IllegalStateException e) {
            sendeFehler(exchange, 409, e.getMessage());
        } catch (Exception e) {
            sendeFehler(exchange, 500, "Vorgang fehlgeschlagen (" + e.getClass().getSimpleName() + ")");
        } finally {
            exchange.close();
        }
    }

    // ------------------------------------------------------------------
    // API-Handler
    // ------------------------------------------------------------------

    private void starteLauf(HttpExchange exchange) throws IOException {
        if (certificateBusy.get() || Boolean.TRUE.equals(llm.status().get("running"))) {
            sendeFehler(exchange, 409, "Die GLM-Prüfung muss vor einem neuen Simulator-Lauf abgeschlossen sein");
            return;
        }
        Map<?, ?> wunsch = readJsonBody(exchange);
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
        antwort.put("md", simulator.letztesMd());
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

    /**
     * Ausführliche Beschreibung der Testfall-Arten der gewählten Kategorie
     * (?kategorie=POSITIV&modus=1|2). Statischer Katalog — vor dem Lauf abrufbar.
     */
    private Map<String, Object> testfallKatalog(HttpExchange exchange) {
        var query = exchange.getRequestURI().getQuery();
        String kategorie = "POSITIV";
        boolean web = false;
        if (query != null) {
            for (String paar : query.split("&")) {
                String[] teile = paar.split("=", 2);
                if (teile.length == 2 && teile[0].equals("kategorie")) {
                    kategorie = java.net.URLDecoder.decode(teile[1], java.nio.charset.StandardCharsets.UTF_8);
                } else if (teile.length == 2 && teile[0].equals("modus")) {
                    web = teile[1].trim().equals("2");
                }
            }
        }
        var eintraege = TestfallKatalog.fuer(kategorie, web).stream()
                .map(e -> Map.of("name", e.name(), "anteil", e.anteil(), "text", e.text()))
                .toList();
        Map<String, Object> antwort = new LinkedHashMap<>();
        antwort.put("kategorie", kategorie.toUpperCase(java.util.Locale.ROOT));
        antwort.put("modus", web ? "2" : "1");
        antwort.put("eintraege", eintraege);
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

    // ------------------------------------------------------------------
    // LLM-Nachrechnung: Status/Prompt/Dateien anzeigen, Prüfpaket erzeugen
    // ------------------------------------------------------------------

    /** Stand der echten GLM-Prüfung und der tatsächlich eingesetzte Prüfauftrag. */
    private void liefereLlmPruefung(HttpExchange exchange) throws IOException {
        Map<String, Object> response = new LinkedHashMap<>(llm.status());
        refreshReviewedReport(response);
        response.put("reportPdf", response.get("reviewId") == null ? null : "/api/llm/report/pdf");
        response.put("configuration", llm.configuration());
        java.nio.file.Path prompt = repository.resolve("doc/testzertifikat-werkzeug/llm-pruefauftrag.txt");
        response.put("prompt", java.nio.file.Files.exists(prompt)
                ? java.nio.file.Files.readString(prompt, StandardCharsets.UTF_8) : null);
        sendeJson(exchange, response);
    }

    private void erzeugePruefpaket(HttpExchange exchange) throws IOException {
        Map<?, ?> body = readJsonBody(exchange);
        sendeJson(exchange, llm.buildPackage(zahl(body, "faelle", ((Number) llm.configuration().get("cases")).intValue())));
    }

    /** Ursprüngliche Prüfdaten bleiben unverändert; der Anhang bekommt eine eigene Ausgabe. */
    private void refreshReviewedReport(Map<String, Object> evidence) throws IOException {
        synchronized (operationLock) {
        if (Boolean.TRUE.equals(evidence.get("running")) || evidence.get("artifactDirectory") == null || simulator.laufAktiv()) return;
        String key = LlmReviewService.sha256(Json.write(evidence).getBytes(StandardCharsets.UTF_8));
        synchronized (reportLock) {
            java.nio.file.Path directory = java.nio.file.Path.of(String.valueOf(evidence.get("artifactDirectory"))).toAbsolutePath().normalize();
            if (!directory.startsWith(repository.resolve("testsweet/logs"))) throw new IOException("Ungültiger Nachweispfad");
            java.nio.file.Path appendix = directory.resolve("testreport-ki-anhang.pdf");
            if (!key.equals(reviewedKey) || !java.nio.file.Files.isRegularFile(appendix)) {
                Testdokumentation.schreibeKiNachweis(appendix, evidence);
                StringBuilder md = new StringBuilder("# KI-Prüfnachweis zum Testreport\n");
                KiPruefAnhang.md(md, evidence);
                java.nio.file.Files.writeString(directory.resolve("testreport-ki-anhang.md"), md, StandardCharsets.UTF_8);
            }
            String source = simulator.letztesMd();
            boolean bound = false;
            if (source != null && evidence.get("sources") instanceof List<?> sources) {
                java.nio.file.Path actual = java.nio.file.Path.of(source).toAbsolutePath().normalize();
                bound = sources.stream().anyMatch(item -> item instanceof Map<?, ?> row
                        && actual.equals(repository.resolve(String.valueOf(row.get("path"))).toAbsolutePath().normalize()));
            }
            if (bound && !simulator.auftraege().isEmpty() && (!key.equals(reviewedKey) || !java.util.Objects.equals(source, reviewedSource))) {
                java.nio.file.Path run = java.nio.file.Path.of(source).toAbsolutePath().getParent();
                java.nio.file.Path pdf = run.resolve("testdokumentation-geprueft.pdf"), md = run.resolve("testdokumentation-geprueft.md");
                Testdokumentation.schreibe(pdf, simulator, evidence);
                MdBericht.schreibe(md, simulator, evidence);
                reviewedPdf = pdf.toString(); reviewedMd = md.toString(); reviewedSource = source;
            }
            reviewedKey = key;
        }
        }
    }

    private void liefereKiNachweis(HttpExchange exchange) throws IOException {
        Map<String, Object> evidence = llm.status();
        refreshReviewedReport(evidence);
        if (evidence.get("artifactDirectory") == null || Boolean.TRUE.equals(evidence.get("running"))) {
            sendeFehler(exchange, 404, "Noch kein abgeschlossener KI-Prüfnachweis vorhanden"); return;
        }
        java.nio.file.Path pdf = java.util.Objects.equals(reviewedSource, simulator.letztesMd()) && reviewedPdf != null
                ? java.nio.file.Path.of(reviewedPdf) : java.nio.file.Path.of(String.valueOf(evidence.get("artifactDirectory"))).resolve("testreport-ki-anhang.pdf");
        byte[] bytes = java.nio.file.Files.readAllBytes(pdf);
        exchange.getResponseHeaders().set("Content-Type", "application/pdf");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) { out.write(bytes); }
    }

    private static java.nio.file.Path repositoryRoot() {
        java.nio.file.Path cwd = java.nio.file.Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        return cwd.getFileName() != null && cwd.getFileName().toString().equalsIgnoreCase("testsweet")
                ? cwd.getParent() : cwd;
    }

    private boolean localRequest(HttpExchange exchange) {
        String host = exchange.getRequestHeaders().getFirst("Host");
        int port = server.getAddress().getPort();
        if (!("127.0.0.1:" + port).equals(host) && !("localhost:" + port).equals(host)) return false;
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        return origin == null || origin.equals("http://" + host);
    }

    private static Map<?, ?> readJsonBody(HttpExchange exchange) throws IOException {
        byte[] bytes = exchange.getRequestBody().readNBytes(1_048_577);
        if (bytes.length > 1_048_576) throw new IllegalArgumentException("Anfrage ist zu groß");
        if (bytes.length == 0) return Map.of();
        Object value = Json.parse(new String(bytes, StandardCharsets.UTF_8));
        if (!(value instanceof Map<?, ?> map)) throw new IllegalArgumentException("JSON-Objekt erwartet");
        return map;
    }

    int port() { return server.getAddress().getPort(); }

    @Override public void close() {
        simulator.stoppe();
        server.stop(0);
        llm.close();
        executor.shutdownNow();
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
        Map<String, Object> evidence = llm.status();
        refreshReviewedReport(evidence);
        String pfad = java.util.Objects.equals(reviewedSource, simulator.letztesMd()) ? reviewedMd : simulator.letztesMd();
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
        Map<String, Object> evidence = llm.status();
        refreshReviewedReport(evidence);
        String pfad = java.util.Objects.equals(reviewedSource, simulator.letztesMd()) && reviewedPdf != null ? reviewedPdf : simulator.letztesPdf();
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

    // ------------------------------------------------------------------
    // Testzertifikat: Generator (node) anstoßen, PDF erzeugen und ausliefern
    // ------------------------------------------------------------------

    /** Ort des Zertifikat-Werkzeugs relativ zum Arbeitsverzeichnis suchen. */
    private java.nio.file.Path werkzeugOrdner() {
        java.nio.file.Path own = repository.resolve("doc/testzertifikat-werkzeug");
        if (java.nio.file.Files.isDirectory(own)) return own;
        for (String basis : new String[] { ".", ".." }) {
            java.nio.file.Path kandidat = java.nio.file.Path.of(basis, "doc", "testzertifikat-werkzeug");
            if (java.nio.file.Files.isDirectory(kandidat)) {
                return kandidat.toAbsolutePath().normalize();
            }
        }
        return null;
    }

    /**
     * Erzeugt das Testzertifikat über das Werkzeug (node generate.js + LibreOffice)
     * und legt die Kopie unter doc/SDN-Leistung-LMG-Testzertifikat.pdf ab. Die
     * Prüfzahlen stehen im Generator-Skript (vor dem Ausstellen aktuell halten).
     */
    private void erzeugeZertifikat(HttpExchange exchange) throws IOException {
        synchronized (operationLock) {
            if (simulator.laufAktiv() || Boolean.TRUE.equals(llm.status().get("running"))) { sendeFehler(exchange, 409, "Lauf oder GLM-Prüfung zuerst abschließen"); return; }
            if (!certificateBusy.compareAndSet(false, true)) { sendeFehler(exchange, 409, "Ein Zertifikat wird bereits erzeugt"); return; }
        }
        try { erzeugeZertifikatIntern(exchange); } finally { certificateBusy.set(false); }
    }

    private void erzeugeZertifikatIntern(HttpExchange exchange) throws IOException {
        java.nio.file.Path werkzeug = werkzeugOrdner();
        if (werkzeug == null) {
            sendeFehler(exchange, 404, "Zertifikat-Werkzeug nicht gefunden "
                    + "(doc/testzertifikat-werkzeug fehlt relativ zum Server-Startverzeichnis)");
            return;
        }
        try {
            Map<String, Object> llmStatus = llm.status();
            if (Boolean.TRUE.equals(llmStatus.get("running"))) {
                sendeFehler(exchange, 409, "Die GLM-Prüfung läuft noch"); return;
            }
            if (!"PASS".equals(llmStatus.get("state"))) {
                sendeFehler(exchange, 409, "Vor der Zertifikatserstellung muss eine vollständige GLM-Prüfung erfolgreich abgeschlossen sein"); return;
            }
            java.nio.file.Path staging = repository.resolve("testsweet/logs/certificate-" + java.util.UUID.randomUUID());
            java.nio.file.Files.createDirectories(staging);
            java.nio.file.Path docx = staging.resolve("testzertifikat.docx");
            runProcess(new ProcessBuilder("node", "generate.js", docx.toString()).directory(werkzeug.toFile()), 90);
            if (!java.nio.file.Files.isRegularFile(docx)) throw new IOException("DOCX wurde nicht erzeugt");
            String soffice = java.nio.file.Files.exists(java.nio.file.Path.of("C:/Program Files/LibreOffice/program/soffice.exe"))
                    ? "C:/Program Files/LibreOffice/program/soffice.exe" : "soffice";
            runProcess(new ProcessBuilder(soffice, "--headless", "--convert-to", "pdf", "--outdir",
                    staging.toString(), docx.toString()).directory(werkzeug.toFile()), 90);
            java.nio.file.Path pdf = staging.resolve("testzertifikat.pdf");
            if (!java.nio.file.Files.isRegularFile(pdf)) throw new IOException("PDF wurde nicht erzeugt");
            java.nio.file.Files.copy(pdf, werkzeug.resolve("testzertifikat.pdf"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            java.nio.file.Files.copy(docx, werkzeug.resolve("testzertifikat.docx"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            java.nio.file.Files.copy(pdf, werkzeug.getParent().resolve("SDN-Leistung-LMG-Testzertifikat.pdf"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            java.nio.file.Files.copy(docx, werkzeug.getParent().resolve("SDN-Leistung-LMG-Testzertifikat.docx"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            // Erhebungsstand (stand.json vom Generator) für den Dialog aufbereiten
            Map<String, Object> antwort = new java.util.HashMap<>();
            antwort.put("ok", true);
            antwort.put("pdf", werkzeug.getParent().resolve("SDN-Leistung-LMG-Testzertifikat.pdf").toString());
            antwort.put("erstellt", java.time.LocalDateTime.now().toString());
            antwort.put("llm", llmStatus);
            java.nio.file.Path standDatei = werkzeug.resolve("stand.json");
            if (java.nio.file.Files.exists(standDatei)) {
                String stand = java.nio.file.Files.readString(standDatei, java.nio.charset.StandardCharsets.UTF_8);
                Object geparst = Json.parse(stand);
                if (geparst instanceof Map<?, ?> standMap) {
                    Object verdict = standMap.get("verdict");
                    Object warnungen = standMap.get("warnungen");
                    if (verdict != null) {
                        antwort.put("verdict", verdict.toString());
                    }
                    if (warnungen instanceof java.util.List<?> liste && !liste.isEmpty()) {
                        antwort.put("warnungen", liste.stream().map(String::valueOf).toList());
                    }
                }
            }
            sendeJson(exchange, antwort);
        } catch (Exception e) {
            sendeFehler(exchange, 500, "Zertifikat konnte nicht erzeugt werden: "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private static void runProcess(ProcessBuilder builder, int timeoutSeconds) throws Exception {
        java.nio.file.Path output = java.nio.file.Files.createTempFile("testsweet-export-", ".log");
        try {
            Process process = builder.redirectErrorStream(true).redirectOutput(output.toFile()).start();
            if (!process.waitFor(timeoutSeconds, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IOException("Dokumenterzeugung hat das Zeitlimit überschritten");
            }
            if (process.exitValue() != 0) throw new IOException("Dokumenterzeugung fehlgeschlagen (Exit " + process.exitValue() + ")");
        } finally { java.nio.file.Files.deleteIfExists(output); }
    }

    /** Liefert das erzeugte Zertifikat-PDF aus (wie /api/pdf für die Testdokumentation). */
    private void liefereZertifikatPdf(HttpExchange exchange) throws IOException {
        java.nio.file.Path werkzeug = werkzeugOrdner();
        java.nio.file.Path datei = werkzeug == null ? null
                : werkzeug.getParent().resolve("SDN-Leistung-LMG-Testzertifikat.pdf");
        if (datei == null || !java.nio.file.Files.exists(datei)) {
            sendeFehler(exchange, 404, "Noch kein Zertifikat vorhanden — zuerst "
                    + "„Testzertifikat erstellen“ klicken oder erstelle-zertifikat.bat ausführen");
            return;
        }
        byte[] bytes = java.nio.file.Files.readAllBytes(datei);
        exchange.getResponseHeaders().set("Content-Type", "application/pdf");
        exchange.getResponseHeaders().set("Content-Disposition",
                "inline; filename=\"testzertifikat.pdf\"");
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
