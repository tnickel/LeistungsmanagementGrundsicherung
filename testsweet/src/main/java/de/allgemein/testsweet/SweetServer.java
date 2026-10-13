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

    /** Protokoll-Kopf (pruefumfang = ..., ergebnis = ...) aus einer Protokolldatei lesen. */
    private static Map<String, String> liesProtokollKopf(java.nio.file.Path datei) {
        Map<String, String> kopf = new java.util.HashMap<>();
        try {
            for (String zeile : java.nio.file.Files.readAllLines(datei, java.nio.charset.StandardCharsets.UTF_8)) {
                java.util.regex.Matcher m = java.util.regex.Pattern
                        .compile("^(\\w+) = (.+)$").matcher(zeile.trim());
                if (m.matches() && kopf.size() < 8) {
                    kopf.put(m.group(1), m.group(2).trim());
                }
            }
        } catch (Exception ignored) {
            // Protokoll bleibt dann ohne Kopfangaben
        }
        return kopf;
    }

    /** GET /api/llmpruefung — Stand der KI-Prüfung, Prompt-Text, mitzugebende Dateien. */
    private void liefereLlmPruefung(HttpExchange exchange) throws IOException {
        java.nio.file.Path werkzeug = werkzeugOrdner();
        Map<String, Object> antwort = new java.util.HashMap<>();
        java.util.List<Map<String, Object>> protokolle = new java.util.ArrayList<>();
        String mdPfad = simulator.letztesMd();
        String mdDatenstand = null;
        if (werkzeug != null && java.nio.file.Files.isDirectory(werkzeug)) {
            java.util.List<java.nio.file.Path> dateien = new java.util.ArrayList<>();
            try (var stream = java.nio.file.Files.list(werkzeug)) {
                stream.filter(p -> {
                    String n = p.getFileName().toString();
                    return n.matches("llm-nachrechnung-\\d{4}-\\d{2}-\\d{2}(-\\S+)?\\.md");
                }).forEach(dateien::add);
            }
            dateien.sort(Comparator.comparing((java.nio.file.Path p) ->
                    p.getFileName().toString().replaceAll(".*?(\\d{4}-\\d{2}-\\d{2}).*", "$1")
                            + String.format("%4d", p.getFileName().toString().length())));
            for (java.nio.file.Path p : dateien) {
                Map<String, Object> eintrag = new java.util.HashMap<>();
                eintrag.put("datei", p.getFileName().toString());
                eintrag.put("ablageort", werkzeug.getParent().getFileName() + "/"
                        + werkzeug.getFileName() + "/" + p.getFileName());
                eintrag.putAll(liesProtokollKopf(p));
                protokolle.add(eintrag);
            }
        }
        antwort.put("protokolle", protokolle);
        if (mdPfad != null) {
            java.nio.file.Path md = java.nio.file.Path.of(mdPfad);
            if (java.nio.file.Files.exists(md)) {
                mdDatenstand = md.getParent().getFileName().toString()
                        .replaceFirst("^run-(\\d{8}).*$", "$1")
                        .replaceFirst("^(\\d{4})(\\d{2})(\\d{2})$", "$1-$2-$3")
                        + " (" + md.getParent().getFileName() + ")";
            }
        }
        antwort.put("md_datenstand", mdDatenstand);
        antwort.put("md_pfad", mdPfad);
        Map<String, Object> dateienMap = new java.util.LinkedHashMap<>();
        if (werkzeug != null && java.nio.file.Files.isDirectory(werkzeug)) {
            String[][] hinzu = {
                    {"Prüfauftrag (Prompt)", "llm-pruefauftrag.txt"},
                    {"Gesetzesgrundlagen", "gesetzesgrundlagen-2026.md"}};
            for (String[] paar : hinzu) {
                java.nio.file.Path p = werkzeug.resolve(paar[1]);
                if (java.nio.file.Files.exists(p)) {
                    dateienMap.put(paar[0], Map.of(
                            "datei", paar[1],
                            "ablageort", "doc/testzertifikat-werkzeug/" + paar[1],
                            "bytes", java.nio.file.Files.size(p)));
                }
            }
            try (var stream = java.nio.file.Files.list(werkzeug)) {
                stream.filter(p -> p.getFileName().toString().startsWith("pruefmenge-"))
                        .forEach(p -> {
                            try {
                                dateienMap.put("Prüfmenge " + p.getFileName(), Map.of(
                                        "datei", p.getFileName().toString(),
                                        "ablageort", "doc/testzertifikat-werkzeug/" + p.getFileName(),
                                        "bytes", java.nio.file.Files.size(p)));
                            } catch (Exception ignored) {
                            }
                        });
            }
        }
        antwort.put("dateien", dateienMap);
        String prompt = null;
        java.nio.file.Path auftrag = werkzeug == null ? null : werkzeug.resolve("llm-pruefauftrag.txt");
        if (auftrag != null && java.nio.file.Files.exists(auftrag)) {
            prompt = java.nio.file.Files.readString(auftrag, java.nio.charset.StandardCharsets.UTF_8);
        }
        antwort.put("prompt", prompt);
        antwort.put("werkzeug_ordner", werkzeug == null ? null
                : "doc/testzertifikat-werkzeug (absolut: " + werkzeug + ")");
        sendeJson(exchange, antwort);
    }

    /**
     * POST /api/llmpruefung/paket (Body {"faelle": N}) — schnürt die drei mitzugebenden
     * Dateien: die Prüfmenge wird aus der neuesten testdokumentation.md erzeugt (Kopf +
     * die ersten N Kombinationsfälle) und im Werkzeugordner abgelegt. Die KI selbst wird
     * bewusst NICHT aus der Anwendung gerufen — das Paket gibt man einer KI, deren
     * Antwort als llm-nachrechnung-<Datum>.md zurückgelegt wird.
     */
    private void erzeugePruefpaket(HttpExchange exchange) throws IOException {
        java.nio.file.Path werkzeug = werkzeugOrdner();
        if (werkzeug == null) {
            sendeFehler(exchange, 404, "Werkzeugordner doc/testzertifikat-werkzeug nicht gefunden");
            return;
        }
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        int faelle = 100;
        try {
            Map<?, ?> wunsch = (Map<?, ?>) Json.parse(body);
            if (wunsch != null && wunsch.get("faelle") instanceof Number n) {
                faelle = Math.max(1, Math.min(500, n.intValue()));
            }
        } catch (Exception ignored) {
            // Standard 100 bleibt
        }
        String mdPfad = letztesMdMitKombination();
        if (mdPfad == null) {
            sendeFehler(exchange, 404, "Noch keine testdokumentation.md mit Kombinationsfällen "
                    + "vorhanden — zuerst einen Positiv-Lauf mit Kombinationsgenerator fahren "
                    + "(kleine Smoke-Läufe ohne Kombinationsfälle reichen nicht als Prüfgrundlage)");
            return;
        }
        String inhalt = java.nio.file.Files.readString(java.nio.file.Path.of(mdPfad), java.nio.charset.StandardCharsets.UTF_8);
        java.util.List<String> bloecke = new java.util.ArrayList<>();
        String kopf;
        int pos = inhalt.indexOf("\n## TF ");
        kopf = pos >= 0 ? inhalt.substring(0, pos + 1) : inhalt;
        String rest = pos >= 0 ? inhalt.substring(pos + 1) : "";
        for (String block : rest.split("(?m)(?=^## TF )")) {
            if (!block.startsWith("## TF ")) {
                continue;
            }
            String[] wort = block.split("\\s+", 4); // ## TF <nr> <TYP> ...
            if (wort.length >= 4 && wort[3].startsWith("KOMBINATION") && bloecke.size() < faelle) {
                bloecke.add(block);
            }
        }
        if (bloecke.isEmpty()) {
            sendeFehler(exchange, 404, "In der neuesten Testdokumentation wurden keine "
                    + "Kombinationsfälle gefunden — bitte einen Positiv-Lauf mit Kombinationsgenerator fahren");
            return;
        }
        String heute = java.time.LocalDate.now().toString();
        int gesamtKombi = (int) java.util.regex.Pattern.compile("(?m)^## TF \\d+ KOMBINATION ")
                .matcher(inhalt).results().count();
        String hinweis = "> Prüfmenge für die LLM-Nachrechnung — erzeugt am " + heute
                + " über die testsweet-GUI (Prüfpaket, " + faelle + " Fälle).\n"
                + "> Grundlage: testdokumentation.md des neuesten Laufs (" + mdPfad + ").\n"
                + "> Kürzung: Lauf- und Rechengrundlagen-Kopf vollständig + die ersten "
                + bloecke.size() + " Kombinationsfälle von insgesamt " + gesamtKombi
                + " (kuratierte Fälle und Kassenläufe sind NICHT enthalten).\n"
                + "> Mitgegeben werden außerdem vollständig: gesetzesgrundlagen-2026.md und "
                + "llm-pruefauftrag.txt (beide im Werkzeugordner doc/testzertifikat-werkzeug/).\n\n";
        java.nio.file.Path ziel = werkzeug.resolve("pruefmenge-positiv-" + bloecke.size() + "faelle.md");
        java.nio.file.Files.writeString(ziel, hinweis + kopf + String.join("", bloecke),
                java.nio.charset.StandardCharsets.UTF_8);
        Map<String, Object> antwort = new java.util.HashMap<>();
        antwort.put("ok", true);
        antwort.put("faelle", bloecke.size());
        antwort.put("pruefmenge", ziel.toString());
        antwort.put("pruefauftrag", werkzeug.resolve("llm-pruefauftrag.txt").toString());
        antwort.put("grundlagen", werkzeug.resolve("gesetzesgrundlagen-2026.md").toString());
        antwort.put("anleitung", "Diese drei Dateien einer KI geben. Ihre Bestätigung als "
                + "llm-nachrechnung-<Datum>.md in doc/testzertifikat-werkzeug/ ablegen — "
                + "Kopfzeilen (pruefumfang/mitgegeben/modell/methode/ergebnis) beibehalten; "
                + "das Zertifikat liest sie automatisch ein.");
        sendeJson(exchange, antwort);
    }

    /**
     * Stößt die unabhängige Nachrechnung an, falls nötig: Vergleicht den Lauf der
     * neuesten Testdokumentation (mit Kombinationsfällen) mit dem Datenstand des
     * neuesten Nachrechnprotokolls. Bei Abweichung (oder ohne Protokoll) läuft
     * nachrechnen_100.py über die ersten 100 Kombinationsfälle und schreibt ein
     * neues Protokoll llm-nachrechnung-<Datum>-auto-<HHMM>.md; bei gleichem Lauf
     * wird nichts erneut gerechnet ("nur wenn neue Testfälle dazukommen").
     */
    private Map<String, Object> aktualisiereNachrechnung(java.nio.file.Path werkzeug) {
        Map<String, Object> status = new java.util.HashMap<>();
        String mdPfad = letztesMdMitKombination();
        if (mdPfad == null) {
            status.put("fehler", "Keine testdokumentation.md mit Kombinationsfällen vorhanden — "
                    + "zuerst einen Positiv-Lauf fahren");
            return status;
        }
        String lauf = java.nio.file.Path.of(mdPfad).getParent().getFileName().toString();
        String mdAbsolut = java.nio.file.Path.of(mdPfad).toAbsolutePath().normalize().toString();
        // neuestes Protokoll + dessen dokumentierten Datenstand lesen
        java.nio.file.Path neuestes = null;
        try (var stream = java.nio.file.Files.list(werkzeug)) {
            java.util.List<java.nio.file.Path> kandidaten = new java.util.ArrayList<>(stream
                    .filter(p -> p.getFileName().toString()
                            .matches("llm-nachrechnung-\\d{4}-\\d{2}-\\d{2}(-\\S+)?\\.md"))
                    .toList());
            kandidaten.sort(Comparator.comparing((java.nio.file.Path p) -> p.getFileName().toString()
                    .replaceAll(".*?(\\d{4}-\\d{2}-\\d{2}).*", "$1")
                    + p.getFileName().toString().replaceAll(".*?\\d{4}-\\d{2}-\\d{2}(-\\S+)?\\.md", "$1")));
            if (!kandidaten.isEmpty()) {
                neuestes = kandidaten.get(kandidaten.size() - 1);
            }
        } catch (Exception ignored) {
            // dann wird neu gerechnet
        }
        if (neuestes != null) {
            Map<String, String> kopf = liesProtokollKopf(neuestes);
            String datenstand = kopf.get("datenstand");
            if (lauf.equals(datenstand)) {
                status.put("nachgerechnet", false);
                status.put("protokoll", neuestes.getFileName().toString());
                status.put("ergebnis", kopf.getOrDefault("ergebnis", ""));
                status.put("lauf", lauf);
                return status;
            }
        }
        // Nachrechnung anstoßen: python nachrechnen_100.py <md> --max 100 --protokoll <ziel>
        String zeit = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HHmm"));
        String heute = java.time.LocalDate.now().toString();
        java.nio.file.Path ziel = werkzeug.resolve("llm-nachrechnung-" + heute + "-auto-" + zeit + ".md");
        try {
            Process rechner = new ProcessBuilder("python", "nachrechnen_100.py", mdAbsolut,
                    "--max", "100", "--protokoll", ziel.toString())
                    .directory(werkzeug.toFile()).redirectErrorStream(true).start();
            String ausgabe = new String(rechner.getInputStream().readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8);
            int code = rechner.waitFor();
            if (code != 0 || !java.nio.file.Files.exists(ziel)) {
                status.put("fehler", "Exit " + code + " — " + ausgabe.trim()
                        + " (python im PATH? Skript nachrechnen_100.py im Werkzeugordner?)");
                return status;
            }
            Map<String, String> kopf = liesProtokollKopf(ziel);
            status.put("nachgerechnet", true);
            status.put("protokoll", ziel.getFileName().toString());
            status.put("ergebnis", kopf.getOrDefault("ergebnis", ""));
            status.put("pruefumfang", kopf.getOrDefault("pruefumfang", ""));
            status.put("lauf", lauf);
        } catch (Exception e) {
            status.put("fehler", e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        return status;
    }

    /**
     * Neueste testdokumentation.md, die Kombinationsfälle enthält — kleine Smoke-Läufe
     * (nur kuratierte Fälle) sollen die Prüfgrundlage nicht verdrängen. Sucht in den
     * Laufordnern absteigend und prüft auf einen „## TF n KOMBINATION“-Block.
     */
    private String letztesMdMitKombination() {
        java.nio.file.Path logs = java.nio.file.Path.of("testsweet", "logs");
        if (!java.nio.file.Files.isDirectory(logs)) {
            logs = java.nio.file.Path.of("..", "testsweet", "logs");
        }
        if (!java.nio.file.Files.isDirectory(logs)) {
            return simulator.letztesMd();
        }
        try (var ordner = java.nio.file.Files.list(logs)) {
            java.util.List<java.nio.file.Path> kandidaten = new java.util.ArrayList<>(ordner.toList());
            kandidaten.sort(Comparator.reverseOrder());
            for (java.nio.file.Path lauf : kandidaten) {
                java.nio.file.Path md = lauf.resolve("testdokumentation.md");
                if (!java.nio.file.Files.exists(md)) {
                    continue;
                }
                String inhalt = java.nio.file.Files.readString(md, java.nio.charset.StandardCharsets.UTF_8);
                if (inhalt.contains(" KOMBINATION SWEET-")) {
                    return md.toString();
                }
            }
        } catch (Exception ignored) {
            // Fallback unten
        }
        return simulator.letztesMd();
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

    // ------------------------------------------------------------------
    // Testzertifikat: Generator (node) anstoßen, PDF erzeugen und ausliefern
    // ------------------------------------------------------------------

    /** Ort des Zertifikat-Werkzeugs relativ zum Arbeitsverzeichnis suchen. */
    private static java.nio.file.Path werkzeugOrdner() {
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
        java.nio.file.Path werkzeug = werkzeugOrdner();
        if (werkzeug == null) {
            sendeFehler(exchange, 404, "Zertifikat-Werkzeug nicht gefunden "
                    + "(doc/testzertifikat-werkzeug fehlt relativ zum Server-Startverzeichnis)");
            return;
        }
        try {
            // Automatische Nachrechnung: Nur wenn der zu prüfende Lauf (neueste
            // Testdokumentation mit Kombinationsfällen) ein ANDERER ist als der im
            // neuesten Protokoll dokumentierte Datenstand — gleicher Lauf bedeutet:
            // Nachrechnung ist noch aktuell und wird nicht erneut ausgeführt.
            Map<String, Object> llmStatus = aktualisiereNachrechnung(werkzeug);
            if (llmStatus.get("fehler") != null) {
                sendeFehler(exchange, 500, "Automatische Nachrechnung fehlgeschlagen: "
                        + llmStatus.get("fehler"));
                return;
            }
            java.nio.file.Path docx = werkzeug.resolve("testzertifikat.docx");
            Process generator = new ProcessBuilder("node", "generate.js", docx.getFileName().toString())
                    .directory(werkzeug.toFile()).redirectErrorStream(true).start();
            String ausgabe = new String(generator.getInputStream().readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8);
            int code = generator.waitFor();
            if (code != 0 || !java.nio.file.Files.exists(docx)) {
                sendeFehler(exchange, 500, "Zertifikat-Generator fehlgeschlagen (Exit " + code
                        + "): " + ausgabe.trim());
                return;
            }
            String soffice = java.nio.file.Files.exists(
                    java.nio.file.Path.of("C:\\Program Files\\LibreOffice\\program\\soffice.exe"))
                    ? "C:\\Program Files\\LibreOffice\\program\\soffice.exe" : "soffice";
            Process export = new ProcessBuilder(soffice, "--headless", "--convert-to", "pdf",
                    "--outdir", ".", docx.getFileName().toString())
                    .directory(werkzeug.toFile()).redirectErrorStream(true).start();
            String exportAusgabe = new String(export.getInputStream().readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8);
            int exportCode = export.waitFor();
            java.nio.file.Path pdf = werkzeug.resolve("testzertifikat.pdf");
            if (exportCode != 0 || !java.nio.file.Files.exists(pdf)) {
                sendeFehler(exchange, 500, "PDF-Export fehlgeschlagen (Exit " + exportCode
                        + "): " + exportAusgabe.trim());
                return;
            }
            java.nio.file.Files.copy(pdf, werkzeug.getParent().resolve("SDN-Leistung-LMG-Testzertifikat.pdf"),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            java.nio.file.Files.copy(docx, werkzeug.getParent().resolve("SDN-Leistung-LMG-Testzertifikat.docx"),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
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
