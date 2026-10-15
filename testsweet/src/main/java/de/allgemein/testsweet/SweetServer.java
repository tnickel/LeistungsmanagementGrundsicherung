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
    private final CertificateSuiteService certificateSuite;
    private final java.util.concurrent.atomic.AtomicBoolean certificateBusy = new java.util.concurrent.atomic.AtomicBoolean();

    SweetServer(Simulator simulator, int port) throws IOException {
        this(simulator, port, repositoryRoot());
    }

    SweetServer(Simulator simulator, int port, java.nio.file.Path repository) throws IOException {
        this(simulator, port, repository, new CertificateSuiteService(repository, simulator));
    }

    SweetServer(Simulator simulator, int port, java.nio.file.Path repository, CertificateSuiteService certificateSuite) throws IOException {
        BuildEvidence.initialize();
        this.simulator = simulator;
        this.repository = repository.toAbsolutePath().normalize();
        this.certificateSuite = certificateSuite;
        this.llm = new LlmReviewService(this.repository);
        de.allgemein.lmg.web.HttpServerLimits.configure();
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
            if (certificateSuite.running() && List.of("/api/pdf", "/api/md", "/api/llm/report/pdf", "/api/zertifikat/pdf").contains(pfad)) {
                sendeFehler(exchange, 409, "Alle Zertifikatstests laufen. Frühere Berichte sind bis zum Abschluss kein aktueller Gesamtnachweis.");
                return;
            }
            switch (pfad) {
                case "/api/zertifikat/tests/status" -> {
                    if (!"GET".equals(exchange.getRequestMethod())) { sendeFehler(exchange, 405, "nur GET"); return; }
                    sendeJson(exchange, certificateSuite.status());
                }
                case "/api/zertifikat/tests/glm-protokoll" -> {
                    if (!"GET".equals(exchange.getRequestMethod())) { sendeFehler(exchange, 405, "nur GET"); return; }
                    glmProtokoll(exchange);
                }
                case "/api/llm/grundlagen" -> {
                    if (!"GET".equals(exchange.getRequestMethod())) { sendeFehler(exchange, 405, "nur GET"); return; }
                    java.nio.file.Path datei = repository.resolve("doc/testzertifikat-werkzeug/gesetzesgrundlagen-2026.md");
                    if (!java.nio.file.Files.isRegularFile(datei)) {
                        sendeFehler(exchange, 404, "Gesetzesgrundlagen fehlen (doc/testzertifikat-werkzeug/gesetzesgrundlagen-2026.md)");
                        return;
                    }
                    sendeTextDatei(exchange, datei);
                }
                case "/api/llm/pruefauftrag" -> {
                    if (!"GET".equals(exchange.getRequestMethod())) { sendeFehler(exchange, 405, "nur GET"); return; }
                    java.nio.file.Path datei = repository.resolve("doc/testzertifikat-werkzeug/llm-pruefauftrag.txt");
                    if (!java.nio.file.Files.isRegularFile(datei)) {
                        sendeFehler(exchange, 404, "Prüfauftrag fehlt (doc/testzertifikat-werkzeug/llm-pruefauftrag.txt)");
                        return;
                    }
                    sendeTextDatei(exchange, datei);
                }
                case "/api/llm/pruefmenge" -> {
                    if (!"GET".equals(exchange.getRequestMethod())) { sendeFehler(exchange, 405, "nur GET"); return; }
                    llmPruefmenge(exchange);
                }
                case "/api/zertifikat/tests/start" -> {
                    if (!"POST".equals(exchange.getRequestMethod())) { sendeFehler(exchange, 405, "nur POST"); return; }
                    synchronized (operationLock) {
                        if (simulator.laufAktiv() || Boolean.TRUE.equals(llm.status().get("running")) || certificateBusy.get()) {
                            sendeFehler(exchange, 409, "Zuerst den laufenden Test oder Export abschließen oder abbrechen."); return;
                        }
                        Map<?,?> body = readJsonBody(exchange);
                        int cases = zahl(body, "faelle", 0);
                        if (cases != 0 && (cases < 2 || cases > 1000)) throw new IllegalArgumentException("Prüffälle müssen zwischen 2 und 1000 liegen");
                        Map<String,Object> started = certificateSuite.start(cases);
                        synchronized (reportLock) { reviewedPdf = null; reviewedMd = null; reviewedSource = null; reviewedKey = null; }
                        sendeJson(exchange, 202, started);
                    }
                }
                case "/api/zertifikat/tests/stop" -> {
                    if (!"POST".equals(exchange.getRequestMethod())) { sendeFehler(exchange, 405, "nur POST"); return; }
                    synchronized (operationLock) { sendeJson(exchange, certificateSuite.cancel()); }
                }
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
                    synchronized (operationLock) {
                        requireNoCertificateSuite();
                        simulator.stoppe();
                        sendeJson(exchange, Map.of("ok", true, "phase", simulator.phase().name()));
                    }
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
                        synchronized (operationLock) {
                            requireNoCertificateSuite();
                            llm.saveConfiguration(readJsonBody(exchange));
                            sendeJson(exchange, llm.configuration());
                        }
                    } else sendeFehler(exchange, 405, "nur GET oder POST");
                }
                case "/api/llm/test" -> {
                    if ("POST".equals(exchange.getRequestMethod())) {
                        synchronized (operationLock) { requireNoCertificateSuite(); sendeJson(exchange, llm.testConnection()); }
                    }
                    else sendeFehler(exchange, 405, "nur POST");
                }
                case "/api/llm/start" -> {
                    if (!"POST".equals(exchange.getRequestMethod())) { sendeFehler(exchange, 405, "nur POST"); return; }
                    synchronized (operationLock) {
                    requireNoCertificateSuite();
                    if (simulator.laufAktiv() || certificateBusy.get()) {
                        sendeFehler(exchange, 409, "Zuerst den Simulator-Lauf abschließen"); return;
                    }
                    Map<?, ?> body = readJsonBody(exchange);
                    Map<String,Object> started = llm.start(zahl(body, "faelle", ((Number) llm.configuration().get("cases")).intValue()));
                    synchronized (reportLock) { reviewedPdf = null; reviewedMd = null; reviewedSource = null; reviewedKey = null; }
                    sendeJson(exchange, 202, started);
                    }
                }
                case "/api/llm/status" -> sendeJson(exchange, llm.status());
                case "/api/llm/report/pdf" -> liefereKiNachweis(exchange);
                case "/api/llmpruefung" -> liefereLlmPruefung(exchange);
                case "/api/llmpruefung/paket" -> {
                    if ("POST".equals(exchange.getRequestMethod())) {
                        synchronized (operationLock) { requireNoCertificateSuite(); erzeugePruefpaket(exchange); }
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
        requireNoCertificateSuite();
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
        // Vorabprüfung: Bei einer nicht erreichbaren Datenbank wird gar kein
        // Lauf angelegt (kein Laufverzeichnis, kein Abbruch-Nachweis), sondern
        // eine klare Meldung mit Startanleitung zurückgegeben.
        DbErreichbarkeit.Ergebnis db = DbErreichbarkeit.pruefe(simulator.dbUrl, simulator.dbUser, simulator.dbPass);
        if (!db.erreichbar()) {
            sendeJson(exchange, 503, Map.of("ok", false, "fehler", db.hinweis(),
                    "fehlerart", "DB_NICHT_ERREICHBAR"));
            return;
        }
        try {
            if ("WEB".equals(modus)) {
                simulator.starteWeb(konfig);
            } else {
                simulator.starte(konfig);
            }
            synchronized (reportLock) { reviewedPdf = null; reviewedMd = null; reviewedSource = null; reviewedKey = null; }
            sendeJson(exchange, Map.of("ok", true));
        } catch (IllegalStateException e) {
            sendeFehler(exchange, 409, e.getMessage());
        }
    }

    /** Zeilenende des simulation.log des letzten Laufs (Rückgriff, wenn kein Lauf aktiv ist). */
    private java.util.List<String> leseLetzteLogzeilen(int anzahl) {
        // Immer der neueste Lauf gewinnt — auch Läufe der Zertifikatssuite, die in
        // eigenen Prozessen laufen; das Verzeichnis des letzten GUI-Laufs ist nur
        // Rückfallebene, wenn noch kein Laufverzeichnis existiert.
        java.nio.file.Path verzeichnis = neuestesLaufverzeichnis(simulator.logBasis());
        if (verzeichnis == null) {
            String letztes = simulator.letztesLogVerzeichnis();
            if (letztes != null) {
                java.nio.file.Path kandidat = java.nio.file.Path.of(letztes);
                if (java.nio.file.Files.isRegularFile(kandidat.resolve("simulation.log"))) verzeichnis = kandidat;
            }
        }
        if (verzeichnis == null) return List.of();
        java.nio.file.Path datei = verzeichnis.resolve("simulation.log");
        try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(datei.toFile(), "r")) {
            long groesse = raf.length();
            long von = Math.max(0, groesse - 64 * 1024); // nur das Dateiende lesen
            raf.seek(von);
            byte[] rest = new byte[(int) (groesse - von)];
            raf.readFully(rest);
            java.util.List<String> zeilen = new ArrayList<>();
            for (String zeile : new String(rest, java.nio.charset.StandardCharsets.UTF_8).split("\n")) {
                String text = zeile.endsWith("\r") ? zeile.substring(0, zeile.length() - 1) : zeile;
                if (!text.isBlank()) zeilen.add(text);
            }
            if (von > 0 && !zeilen.isEmpty()) zeilen.remove(0); // angebrochene erste Zeile verwerfen
            return zeilen.size() <= anzahl ? zeilen : new ArrayList<>(zeilen.subList(zeilen.size() - anzahl, zeilen.size()));
        } catch (Exception e) {
            return List.of();
        }
    }

    /** Neuestes run-<Zeitstempel>-Verzeichnis mit simulation.log (lexikalisch = chronologisch). */
    private static java.nio.file.Path neuestesLaufverzeichnis(java.nio.file.Path basis) {
        if (basis == null || !java.nio.file.Files.isDirectory(basis)) return null;
        try (java.util.stream.Stream<java.nio.file.Path> liste = java.nio.file.Files.list(basis)) {
            return liste.filter(java.nio.file.Files::isDirectory)
                    .filter(d -> d.getFileName().toString().startsWith("run-"))
                    .filter(d -> java.nio.file.Files.isRegularFile(d.resolve("simulation.log")))
                    .max(java.util.Comparator.comparing(d -> d.getFileName().toString()))
                    .orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    /** protocol.md der GLM-Prüfung aus dem Zertifikatslauf — ausschließlich aus dem
     *  von der Suite selbst protokollierten Verzeichnis unter testsweet/logs gelesen
     *  (kein freier Dateizugriff über Parameter). */
    private void glmProtokoll(HttpExchange exchange) throws IOException {
        Map<String, Object> suite = certificateSuite.status();
        Map<?, ?> summary = suite.get("llmSummary") instanceof Map<?, ?> s ? s : null;
        String verzeichnis = summary == null ? null : String.valueOf(summary.get("artifactDirectory"));
        if (verzeichnis == null || verzeichnis.isBlank() || "null".equals(verzeichnis)) {
            sendeFehler(exchange, 404, "Keine GLM-Prüfung im Zertifikatslauf vorhanden");
            return;
        }
        java.nio.file.Path basis = repository.resolve("testsweet").resolve("logs").toAbsolutePath().normalize();
        java.nio.file.Path protokollVerzeichnis = java.nio.file.Path.of(verzeichnis).toAbsolutePath().normalize();
        if (!protokollVerzeichnis.startsWith(basis)) {
            sendeFehler(exchange, 404, "Ungültiges Protokollverzeichnis");
            return;
        }
        java.nio.file.Path protokoll = protokollVerzeichnis.resolve("protocol.md");
        if (!java.nio.file.Files.isRegularFile(protokoll)) {
            sendeFehler(exchange, 404, "Kein GLM-Protokoll (protocol.md) vorhanden");
            return;
        }
        sendeTextDatei(exchange, protokoll);
    }

    /** pruefmenge.md des aktuellen/letzten GLM-Prüfpakets — dieselben Falldokumente,
     *  die dem Modell als drittes Dokument übergeben werden. Kein freier Pfadzugriff:
     *  geliefert wird ausschließlich aus einem llm-review-Verzeichnis unter testsweet/logs. */
    private void llmPruefmenge(HttpExchange exchange) throws IOException {
        java.nio.file.Path basis = repository.resolve("testsweet").resolve("logs").toAbsolutePath().normalize();
        java.nio.file.Path kandidat = null;
        String verzeichnis = String.valueOf(llm.status().get("artifactDirectory"));
        if (verzeichnis != null && !verzeichnis.isBlank() && !"null".equals(verzeichnis)) {
            java.nio.file.Path geprüft = java.nio.file.Path.of(verzeichnis).toAbsolutePath().normalize();
            if (geprüft.startsWith(basis) && java.nio.file.Files.isRegularFile(geprüft.resolve("pruefmenge.md"))) {
                kandidat = geprüft.resolve("pruefmenge.md");
            }
        }
        if (kandidat == null && java.nio.file.Files.isDirectory(basis)) {
            try (var liste = java.nio.file.Files.list(basis)) {
                kandidat = liste.filter(java.nio.file.Files::isDirectory)
                        .filter(d -> d.getFileName().toString().startsWith("llm-review-"))
                        .map(d -> d.resolve("pruefmenge.md"))
                        .filter(java.nio.file.Files::isRegularFile)
                        .max(java.util.Comparator.comparing(d -> d.getParent().getFileName().toString()))
                        .orElse(null);
            }
        }
        if (kandidat == null) {
            sendeFehler(exchange, 404, "Noch kein Prüfpaket vorhanden — zuerst „Prüfpaket ansehen“ oder eine GLM-Prüfung starten");
            return;
        }
        sendeTextDatei(exchange, kandidat);
    }

    private void sendeTextDatei(HttpExchange exchange, java.nio.file.Path datei) throws IOException {
        byte[] inhalt = java.nio.file.Files.readAllBytes(datei);
        exchange.getResponseHeaders().set("Content-Type", "text/markdown; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(200, inhalt.length);
        try (java.io.OutputStream out = exchange.getResponseBody()) {
            out.write(inhalt);
        }
    }

    private volatile java.time.Instant buildZeitCache;
    private volatile long buildZeitGelesen;

    /** Zeitpunkt der neuesten kompilierten Programmdatei — Vergleichsanker für „veraltet“. */
    private java.time.Instant neuesteBuildzeit() {
        java.time.Instant zwischengespeichert = buildZeitCache;
        if (zwischengespeichert != null && System.nanoTime() - buildZeitGelesen < 5_000_000_000L) return zwischengespeichert;
        java.time.Instant neueste = null;
        java.nio.file.Path klassen = repository.resolve("testsweet").resolve("target").resolve("classes");
        if (java.nio.file.Files.isDirectory(klassen)) {
            try (var stream = java.nio.file.Files.walk(klassen)) {
                neueste = stream.filter(java.nio.file.Files::isRegularFile)
                        .map(p -> {
                            try { return java.nio.file.Files.getLastModifiedTime(p).toInstant(); }
                            catch (Exception e) { return null; }
                        })
                        .filter(java.util.Objects::nonNull)
                        .max(java.time.Instant::compareTo).orElse(null);
            } catch (Exception e) { /* Anzeigefeld bleibt dann leer */ }
        }
        buildZeitCache = neueste;
        buildZeitGelesen = System.nanoTime();
        return neueste;
    }

    private Map<String, Object> status() {
        Map<String, Object> antwort = new LinkedHashMap<>();
        antwort.put("phase", simulator.phase().name());
        Map<String, Object> evidence = llm.status();
        ReviewOutcome outcome = ReviewOutcome.evaluate(simulator, evidence);
        antwort.put("laufErfolgreich", "PASS".equals(outcome.state()) && simulator.nachweiseVollstaendig());
        antwort.put("automatikBestanden", simulator.laufErfolgreich());
        antwort.put("review", outcome.asMap());
        antwort.put("nachweiseVollstaendig", simulator.nachweiseVollstaendig());
        antwort.put("nachweisFehler", simulator.nachweisFehler());
        antwort.put("globalePruefungen", simulator.globalePruefungen().stream().map(p -> Map.of(
                "beschreibung", p.beschreibung(), "erwartet", p.erwartet(), "ist", p.ist(),
                "ok", p.ok(), "hinweis", p.hinweis() == null ? "" : p.hinweis())).toList());
        antwort.put("db", Map.of("url", simulator.dbUrl, "user", simulator.dbUser));
        antwort.put("laufdauerSekunden", simulator.laufdauerSekunden());
        antwort.put("letzteFehlermeldung", simulator.letzteFehlermeldung());
        antwort.put("dbNichtErreichbar", simulator.dbNichtErreichbar());
        java.time.Instant buildZeit = neuesteBuildzeit();
        antwort.put("buildZeit", buildZeit == null ? null : buildZeit.toString());
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
        if (log != null) {
            antwort.put("logs", log.letzteZeilen(120));
            antwort.put("logQuelle", "live");
        } else {
            // Kein Lauf aktiv (nach Laufende oder Serverneustart ist das Live-Log
            // weg): das Ende des letzten Laufs anzeigen, statt einer leeren Box.
            java.util.List<String> letzte = leseLetzteLogzeilen(120);
            antwort.put("logs", letzte);
            antwort.put("logQuelle", letzte.isEmpty() ? null : "letzter-lauf");
        }
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

    private void requireNoCertificateSuite() {
        if (certificateSuite.running()) throw new IllegalStateException("Alle Zertifikatstests laufen. Bitte den Gesamtlauf abschließen oder über „Zertifikatslauf abbrechen“ beenden.");
    }

    @Override public void close() {
        certificateSuite.close();
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
        String pfad = reviewedReportCurrent(evidence) && reviewedMd != null ? reviewedMd : simulator.letztesMd();
        if (pfad == null) {
            sendeFehler(exchange, 404, "Noch keine Markdown-Fassung vorhanden — zuerst einen Lauf starten");
            return;
        }
        byte[] bytes = java.nio.file.Files.readAllBytes(java.nio.file.Path.of(pfad));
        exchange.getResponseHeaders().set("Content-Type", "text/markdown; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
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
        String pfad = reviewedReportCurrent(evidence) && reviewedPdf != null ? reviewedPdf : simulator.letztesPdf();
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
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
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

    private boolean reviewedReportCurrent(Map<String,Object> evidence) {
        return !Boolean.TRUE.equals(evidence.get("running"))
                && java.util.Objects.equals(reviewedSource,simulator.letztesMd())
                && java.util.Objects.equals(reviewedKey,LlmReviewService.sha256(Json.write(evidence).getBytes(StandardCharsets.UTF_8)));
    }

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
     * Gemeinsamer, atomar veröffentlichender Exportweg für GUI und Batch.
     * Auch negative und unvollständige Ergebnisse müssen dokumentierbar bleiben.
     */
    private void erzeugeZertifikat(HttpExchange exchange) throws IOException {
        synchronized (operationLock) {
            requireNoCertificateSuite();
            if (simulator.laufAktiv() || Boolean.TRUE.equals(llm.status().get("running"))) { sendeFehler(exchange, 409, "Lauf oder GLM-Prüfung zuerst abschließen"); return; }
            if (!certificateBusy.compareAndSet(false, true)) { sendeFehler(exchange, 409, "Ein Zertifikat wird bereits erzeugt"); return; }
        }
        try { erzeugeZertifikatIntern(exchange); } finally { certificateBusy.set(false); }
    }

    private void erzeugeZertifikatIntern(HttpExchange exchange) throws IOException {
        java.nio.file.Path werkzeug = werkzeugOrdner();
        if (werkzeug == null) { sendeFehler(exchange, 404, "Zertifikat-Werkzeug fehlt"); return; }
        try {
            String output = runProcess(new ProcessBuilder("node", "publish-certificate.js", "--publish", repository.toString())
                    .directory(werkzeug.toFile()), 390);
            Object parsed = Json.parse(output.trim());
            if (!(parsed instanceof Map<?,?> marker) || !"READY".equals(marker.get("state")))
                throw new IOException("Zertifikat wurde nicht vollständig veröffentlicht");
            Map<String,Object> response = new LinkedHashMap<>();
            response.put("ok",true);
            response.put("pdf", repository.resolve("doc/SDN-Leistung-LMG-Testzertifikat.pdf").toString());
            response.put("verdict",marker.get("verdict"));
            response.put("llm",llm.status());
            Object stand = Json.parse(java.nio.file.Files.readString(werkzeug.resolve("stand.json"), StandardCharsets.UTF_8));
            if (stand instanceof Map<?,?> values) response.put("warnungen",values.get("warnungen"));
            sendeJson(exchange,response);
        } catch (Exception error) {
            sendeFehler(exchange,500,"Zertifikat konnte nicht veröffentlicht werden: " + error.getMessage());
        }
    }

    private static String runProcess(ProcessBuilder builder, int timeoutSeconds) throws Exception {
        java.nio.file.Path output = java.nio.file.Files.createTempFile("testsweet-export-", ".log");
        Process process = null;
        try {
            process = builder.redirectErrorStream(true).redirectOutput(output.toFile()).start();
            if (!process.waitFor(timeoutSeconds, java.util.concurrent.TimeUnit.SECONDS)) {
                throw new IOException("Dokumenterzeugung hat das Zeitlimit überschritten");
            }
            String result = java.nio.file.Files.readString(output, StandardCharsets.UTF_8);
            if (process.exitValue() != 0) throw new IOException("Dokumenterzeugung fehlgeschlagen (Exit " + process.exitValue() + "): " + result.trim());
            return result;
        } finally {
            if (process != null && process.isAlive()) {
                List<ProcessHandle> children = process.descendants().toList();
                for (ProcessHandle child : children.reversed()) child.destroyForcibly();
                process.destroyForcibly();
                process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            }
            try { java.nio.file.Files.deleteIfExists(output); } catch (IOException ignored) { /* Diagnose bleibt erhalten. */ }
        }
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
        if (certificateBusy.get()) { sendeFehler(exchange,409,"Zertifikat wird gerade neu erstellt"); return; }
        try {
            runProcess(new ProcessBuilder("node", "publish-certificate.js", "--verify", repository.toString())
                    .directory(werkzeug.toFile()), 30);
        } catch (Exception error) {
            sendeFehler(exchange,409,"Zertifikat ist kein aktueller Nachweis. Neu erstellen. " + error.getMessage()); return;
        }
        byte[] bytes = java.nio.file.Files.readAllBytes(datei);
        exchange.getResponseHeaders().set("Content-Type", "application/pdf");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
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
        try {
            return wert == null ? standard : new java.math.BigDecimal(String.valueOf(wert)).intValueExact();
        } catch (NumberFormatException | ArithmeticException e) {
            throw new IllegalArgumentException("Ungültige Ganzzahl für " + schluessel);
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
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        // Path-Traversal abwehren: Pfade mit ".."-Segmenten sind nie gewollt —
        // weder für die Classpath-Ressource noch für die Dateisystem-Fallbacks.
        if (pfad.contains("..") || !(pfad.equals("/") || pfad.equals("/index.html") || pfad.equals("/app.js")
                || pfad.equals("/style.css") || pfad.matches("/assets/[A-Za-z0-9_./-]+\\.(png|jpg|jpeg|svg)"))) {
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
            // Ausschließlich öffentliche Ressourcen. Ein Fallback auf den ganzen
            // Projektordner würde unter anderem llm.local.properties offenlegen.
            java.nio.file.Path publicRoot = repository.resolve("testsweet/src/main/resources/sweet");
            java.util.List<java.nio.file.Path> roots = new ArrayList<>(List.of(publicRoot));
            if (pfad.startsWith("/assets/")) roots.add(repository.resolve("testsweet"));
            for (java.nio.file.Path base : roots) {
                java.nio.file.Path candidate = base.resolve(pfad.equals("/") ? "index.html" : pfad.substring(1)).normalize();
                if (!candidate.startsWith(base) || !java.nio.file.Files.isRegularFile(candidate)) continue;
                java.nio.file.Path realBase = base.toRealPath(), realFile = candidate.toRealPath();
                if (!realFile.startsWith(realBase)) continue;
                bytes = java.nio.file.Files.readAllBytes(realFile);
                break;
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
