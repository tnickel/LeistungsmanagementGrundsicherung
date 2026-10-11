package de.allgemein.lmg.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import de.allgemein.lmg.calc.Leistungsberechnung;
import de.allgemein.lmg.model.Bedarfsgemeinschaft;
import de.allgemein.lmg.model.Journaleintrag;
import de.allgemein.lmg.model.SozialFall;
import de.allgemein.lmg.model.VorgangslisteEintrag;
import de.allgemein.lmg.model.Zahlung;
import de.allgemein.lmg.service.Auszahlungsauftrag;
import de.allgemein.lmg.service.FachlicheException;
import de.allgemein.lmg.service.Kassenlaeufergebnis;
import de.allgemein.lmg.service.LeistungsService;
import de.allgemein.lmg.service.Systemdiagnose;
import de.allgemein.lmg.service.Systemstatus;
import de.allgemein.lmg.service.Vorgangsdetail;
import de.allgemein.lmg.model.Bescheid;
import de.allgemein.lmg.saga.AntragsSagaProzess;
import de.allgemein.tx.TaskController;
import de.allgemein.tx.TaskHandle;
import de.allgemein.tx.TxException;
import de.allgemein.tx.TxOptions;
import de.allgemein.tx.saga.SagaEngine;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * Webschicht des Leistungsmanagements: stellt die JSON-API und die Oberfläche
 * (SDN.Leistung-Stil) bereit. Jede API-Anfrage läuft als Task über die
 * {@link TaskController}-Steuerung — begrenzte Parallelität, Wachhund und
 * Statistik der Transaktionssteuerung gelten damit auch für den Webbetrieb.
 * Bewusst der in der JDK enthaltene HttpServer statt eines Servlet-Containers:
 * keine zusätzliche Abhängigkeit, gleiche Endpunkte.
 */
public final class LmgServer implements AutoCloseable {

    private final HttpServer server;
    private final TaskController tasks;
    private final LeistungsService service;
    private final Systemdiagnose diagnose;
    private final SagaEngine sagas;
    private final Benutzerverwaltung benutzerverwaltung;

    public LmgServer(TaskController tasks, LeistungsService service, Systemdiagnose diagnose,
                     Benutzerverwaltung benutzerverwaltung, int port) throws IOException {
        this(tasks, service, diagnose, null, benutzerverwaltung, port);
    }

    public LmgServer(TaskController tasks, LeistungsService service, Systemdiagnose diagnose,
                     SagaEngine sagas, Benutzerverwaltung benutzerverwaltung, int port) throws IOException {
        this.tasks = tasks;
        this.service = service;
        this.diagnose = diagnose;
        this.sagas = sagas;
        this.benutzerverwaltung = benutzerverwaltung;
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        this.server.createContext("/", this::verarbeite);
        this.server.setExecutor(Executors.newFixedThreadPool(16, this::neuerHttpThread));
    }

    public void start() {
        server.start();
    }

    @Override
    public void close() {
        server.stop(0);
    }

    /** Port-Startadresse für die Konsolenausgabe. */
    public InetSocketAddress adresse() {
        return server.getAddress();
    }

    // ------------------------------------------------------------------
    // Routing
    // ------------------------------------------------------------------

    private void verarbeite(HttpExchange exchange) throws IOException {
        String pfad = exchange.getRequestURI().getPath();
        String methode = exchange.getRequestMethod();
        try {
            if (pfad.startsWith("/api/")) {
                if (pfad.matches("/api/bedarfsgemeinschaften/\\d+/bescheid/druck") && "GET".equals(methode)) {
                    bescheidDruck(exchange, pfad);
                } else {
                    api(exchange, methode, pfad);
                }
            } else {
                statisch(exchange, pfad);
            }
        } catch (Exception e) {
            // Keine internen Fehlerdetails (SQL/Oracle-Meldungen) nach außen — nur ins Server-Log
            java.util.logging.Logger.getLogger(LmgServer.class.getName())
                    .log(java.util.logging.Level.SEVERE, "Unerwarteter Fehler bei " + methode + " " + pfad, e);
            antworte(exchange, 500, Map.of("code", "TECHNISCH",
                    "message", "Technischer Fehler — bitte die Systemdiagnose prüfen "
                            + "(Details stehen im Server-Log)"));
        } finally {
            exchange.close();
        }
    }

    private void bescheidDruck(HttpExchange exchange, String pfad) throws IOException {
        // Einziger Endpunkt, der den Token aus dem Query-String akzeptiert:
        // window.open kann keinen Authorization-Header setzen. Alle anderen
        // Endpunkte lesen den Token ausschließlich aus dem Header.
        Benutzer benutzer = benutzerAusHeaderOderQuery(exchange);
        if (benutzer == null) {
            byte[] msg = "Nicht autorisiert. Bitte anmelden.".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
            exchange.getResponseHeaders().set("Content-Security-Policy", "default-src 'none'");
            exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
            exchange.sendResponseHeaders(401, msg.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(msg);
            }
            return;
        }
        String[] teile = pfad.split("/");
        long bgId = Long.parseLong(teile[3]);
        try {
            // Auch der Druck läuft als Task: Wachhund + begrenzte Parallelität greifen
            String html = tasks.submit("GET " + pfad, TxOptions.DEFAULT, ctx -> service.generiereBescheidHtml(bgId))
                    .result();
            byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            exchange.getResponseHeaders().set("Content-Security-Policy",
                    "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'");
            exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        } catch (FachlicheException e) {
            byte[] bytes = ("Fehler: " + e.getMessage()).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
            exchange.getResponseHeaders().set("Content-Security-Policy", "default-src 'none'");
            exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
            exchange.sendResponseHeaders(400, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        } catch (Exception e) {
            java.util.logging.Logger.getLogger(LmgServer.class.getName())
                    .log(java.util.logging.Level.SEVERE, "Fehler beim Bescheid-Druck", e);
            byte[] bytes = "Technischer Fehler — Details im Server-Log".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
            exchange.getResponseHeaders().set("Content-Security-Policy", "default-src 'none'");
            exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
            exchange.sendResponseHeaders(500, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        }
    }

    private void api(HttpExchange exchange, String methode, String pfad) throws IOException {
        try {
            // Anmeldung: alles außer /api/anmeldung braucht ein gültiges Sitzungs-Token
            // aus dem Authorization-Header (Query-Token nur beim Bescheid-Druck).
            Benutzer benutzer = null;
            if (!pfad.equals("/api/anmeldung")) {
                Benutzer gefunden = benutzerAusHeader(exchange);
                if (gefunden == null) {
                    antworte(exchange, 401, Map.of("code", "ANMELDUNG",
                            "message", "Nicht angemeldet — bitte Kennung und Passwort eingeben"));
                    return;
                }
                if (brauchtAdmin(pfad) && !gefunden.istAdmin()) {
                    antworte(exchange, 403, Map.of("code", "BERECHTIGUNG",
                            "message", "Diese Funktion erfordert die Admin-Rolle"));
                    return;
                }
                benutzer = gefunden;
            }
            final Benutzer angemeldet = benutzer;
            // Jede Anfrage ist ein Task der Transaktionssteuerung: begrenzte
            // Parallelität + Zeitlimit-Wachhund decken die Last von Tausenden
            // gleichzeitiger Sachbearbeiter ab.
            TaskHandle<Map<String, Object>> handle = tasks.submit(
                    methode + " " + pfad, TxOptions.DEFAULT,
                    ctx -> bearbeiteApi(exchange, methode, pfad, angemeldet));
            antworte(exchange, 200, handle.result());
        } catch (FachlicheException e) {
            int status = FachlicheException.NICHT_GEFUNDEN.equals(e.code()) ? 404
                    : FachlicheException.BERECHTIGUNG.equals(e.code()) ? 403 : 400;
            antworte(exchange, status, Map.of("code", e.code(), "message", String.valueOf(e.getMessage())));
        } catch (TxException e) {
            // Oracle-/Infrastrukturdetails nicht nach außen geben, nur ins Log
            java.util.logging.Logger.getLogger(LmgServer.class.getName())
                    .log(java.util.logging.Level.WARNING, "Tx-Fehler bei " + methode + " " + pfad, e);
            antworte(exchange, 500, Map.of("code", "TECHNISCH",
                    "message", "Technischer Fehler — bitte erneut versuchen "
                            + "(Details stehen im Server-Log)"));
        } catch (IllegalArgumentException e) {
            antworte(exchange, 400, Map.of("code", FachlicheException.VALIDATION,
                    "message", String.valueOf(e.getMessage())));
        }
    }

    /** Token ausschließlich aus dem Authorization-Header — für alle API-Endpunkte. */
    private Benutzer benutzerAusHeader(HttpExchange exchange) {
        String token = tokenAus(exchange.getRequestHeaders().getFirst("Authorization"));
        return benutzerverwaltung.zuToken(token).orElse(null);
    }

    /** Header vor Query-String (nur für den Druck-Link, s. bescheidDruck). */
    private Benutzer benutzerAusHeaderOderQuery(HttpExchange exchange) {
        String token = tokenAus(exchange.getRequestHeaders().getFirst("Authorization"));
        if (token == null && exchange.getRequestURI().getQuery() != null) {
            for (String part : exchange.getRequestURI().getQuery().split("&")) {
                if (part.startsWith("token=")) {
                    token = part.substring("token=".length());
                    break;
                }
            }
        }
        return benutzerverwaltung.zuToken(token).orElse(null);
    }

    private static String tokenAus(String header) {
        if (header == null || !header.startsWith("Bearer ")) {
            return null;
        }
        return header.substring("Bearer ".length()).trim();
    }

    /** Adminpflicht: Systemdiagnose/Aktionen, Kassenlauf und Storno. */
    private static boolean brauchtAdmin(String pfad) {
        return pfad.startsWith("/api/system/")
                || pfad.equals("/api/kassenlauf")
                || pfad.matches("/api/zahlungen/\\d+/storno");
    }

    private Map<String, Object> bearbeiteApi(HttpExchange exchange, String methode, String pfad,
                                             Benutzer angemeldet) throws Exception {
        String[] teile = pfad.split("/");
        // /api/bedarfsgemeinschaften[/{id}][/sozialfaelle] | /api/sozialfaelle/{id} | /api/auszahlung
        // | /api/zahlungen[/{id}/storno] | /api/kassenlauf | /api/system/* | /api/anmeldung | /api/werbin
        if (pfad.equals("/api/anmeldung") && "POST".equals(methode)) {
            Map<String, Object> body = leseKoerper(exchange);
            String token = benutzerverwaltung.anmelden(
                    textWert(body.get("kennung"), "Kennung"),
                    textWert(body.get("passwort"), "Passwort"));
            Benutzer b = benutzerverwaltung.zuToken(token).orElseThrow();
            return Map.of("token", token, "kennung", b.kennung(), "rolle", b.rolle());
        }
        if (pfad.equals("/api/abmeldung") && "POST".equals(methode)) {
            benutzerverwaltung.abmelden(tokenAus(exchange.getRequestHeaders().getFirst("Authorization")));
            return Map.of("ok", true);
        }
        if (pfad.equals("/api/werbin") && "GET".equals(methode)) {
            return Map.of("kennung", angemeldet.kennung(), "rolle", angemeldet.rolle());
        }
        if (pfad.equals("/api/bedarfsgemeinschaften") && "GET".equals(methode)) {
            return Map.of("eintraege", listeMap(service.listeBedarfsgemeinschaften()));
        }
        if (pfad.equals("/api/bedarfsgemeinschaften") && "POST".equals(methode)) {
            Map<String, Object> body = leseKoerper(exchange);
            Bedarfsgemeinschaft bg = service.legeBedarfsgemeinschaftAn(
                    textWert(body.get("aktenzeichen"), "Aktenzeichen"),
                    textWert(body.get("hauptpersonName"), "Hauptperson"),
                    betragWert(body.get("startBudget")),
                    datumWert(body.get("wiedervorlageAm")),
                    textWertOptional(body.get("iban")),
                    textWertOptional(body.get("bic")),
                    textWertOptional(body.get("kontoinhaber")),
                    body.containsKey("vermoegen") ? betragWert(body.get("vermoegen")) : null,
                    angemeldet.kennung());
            return Map.of("bedarfsgemeinschaft", bgMap(bg));
        }
        if (teile.length == 4 && teile[2].equals("bedarfsgemeinschaften") && "GET".equals(methode)) {
            return detailMap(service.ladeDetail(Long.parseLong(teile[3])));
        }
        if (teile.length == 4 && teile[2].equals("bedarfsgemeinschaften") && "PUT".equals(methode)) {
            Map<String, Object> body = leseKoerper(exchange);
            Bedarfsgemeinschaft bg = service.aktualisiereBedarfsgemeinschaft(
                    Long.parseLong(teile[3]),
                    boolWert(body.get("statusAktiv"), "statusAktiv"),
                    datumWert(body.get("wiedervorlageAm")),
                    textWertOptional(body.get("iban")),
                    textWertOptional(body.get("bic")),
                    textWertOptional(body.get("kontoinhaber")),
                    body.containsKey("vermoegen") ? betragWert(body.get("vermoegen")) : null,
                    zahlWert(body.get("version"), "version"),
                    angemeldet.kennung());
            return Map.of("bedarfsgemeinschaft", bgMap(bg));
        }
        if (teile.length == 5 && teile[2].equals("bedarfsgemeinschaften")
                && teile[4].equals("bescheid") && "GET".equals(methode)) {
            long bgId = Long.parseLong(teile[3]);
            Bescheid b = service.letzterBescheid(bgId).orElse(null);
            return Map.of("bescheid", b != null ? bescheidMap(b) : Map.of());
        }
        if (teile.length == 5 && teile[2].equals("bedarfsgemeinschaften")
                && teile[4].equals("bescheid") && "POST".equals(methode)) {
            Map<String, Object> body = leseKoerper(exchange);
            long bgId = Long.parseLong(teile[3]);
            String von = textWert(body.get("bewilligungVon"), "Bewilligung von");
            String bis = textWert(body.get("bewilligungBis"), "Bewilligung bis");
            Bescheid b = service.erteileBescheid(bgId, von, bis, angemeldet.kennung());
            return Map.of("bescheid", bescheidMap(b),
                    "hinweis", "Bescheid " + b.bescheidNr() + " über " + b.zahlbetrag() + " EUR erteilt.");
        }
        if (teile.length == 5 && teile[2].equals("bedarfsgemeinschaften")
                && teile[4].equals("sozialfaelle") && "POST".equals(methode)) {
            Map<String, Object> body = leseKoerper(exchange);
            long bgId = Long.parseLong(teile[3]);
            SozialFall entwurf = new SozialFall(0, bgId,
                    (int) zahlWert(body.get("regelbedarfsstufe"), "regelbedarfsstufe"),
                    boolWertDefensiv(body.get("merkzeichenG")),
                    boolWertDefensiv(body.get("schwanger")),
                    (int) zahlWertDefensiv(body.get("anzKinderUnter7")),
                    (int) zahlWertDefensiv(body.get("anzKinderUnter16")),
                    (int) zahlWertDefensiv(body.get("anzKinderMinderjaehrig")),
                    boolWertDefensiv(body.get("warmwasser")),
                    betragWert(body.get("kduMiete")),
                    betragWert(body.get("kduBetriebskosten")),
                    betragWert(body.get("kduHeizung")),
                    betragWert(body.get("einkommenRente")),
                    betragWert(body.get("einkommenErwerb")),
                    betragWert(body.get("abzSteuern")),
                    betragWert(body.get("abzSozialversicherung")),
                    betragWert(body.get("abzVersicherungen")),
                    betragWert(body.get("abzErwerbsaufwendungen")),
                    1);
            SozialFall gespeichert = service.legeSozialFallAn(bgId, entwurf, angemeldet.kennung());
            return Map.of("sozialfall", fallMap(gespeichert, null));
        }
        if (teile.length == 4 && teile[2].equals("sozialfaelle") && "PUT".equals(methode)) {
            Map<String, Object> body = leseKoerper(exchange);
            long fallId = Long.parseLong(teile[3]);
            // bgId bewusst aus dem Bestand (Service lädt den Fall) — der Request-Body
            // steuert weder Vorgangszuordnung noch Journal-Eintrag
            SozialFall geaendert = new SozialFall(fallId, 0,
                    (int) zahlWert(body.get("regelbedarfsstufe"), "regelbedarfsstufe"),
                    boolWertDefensiv(body.get("merkzeichenG")),
                    boolWertDefensiv(body.get("schwanger")),
                    (int) zahlWertDefensiv(body.get("anzKinderUnter7")),
                    (int) zahlWertDefensiv(body.get("anzKinderUnter16")),
                    (int) zahlWertDefensiv(body.get("anzKinderMinderjaehrig")),
                    boolWertDefensiv(body.get("warmwasser")),
                    betragWert(body.get("kduMiete")),
                    betragWert(body.get("kduBetriebskosten")),
                    betragWert(body.get("kduHeizung")),
                    betragWert(body.get("einkommenRente")),
                    betragWert(body.get("einkommenErwerb")),
                    betragWert(body.get("abzSteuern")),
                    betragWert(body.get("abzSozialversicherung")),
                    betragWert(body.get("abzVersicherungen")),
                    betragWert(body.get("abzErwerbsaufwendungen")),
                    zahlWert(body.get("version"), "version"));
            SozialFall gespeichert = service.aktualisiereSozialFall(
                    geaendert, geaendert.version(), angemeldet.kennung());
            return Map.of("sozialfall", fallMap(gespeichert, null));
        }
        if (pfad.equals("/api/auszahlung") && "POST".equals(methode)) {
            Map<String, Object> body = leseKoerper(exchange);
            // Sachbearbeiter kommt aus der Anmeldung, niemals aus dem Anfragekörper
            var ergebnis = service.ordneAuszahlungAn(new Auszahlungsauftrag(
                    zahlWert(body.get("bgId"), "bgId"),
                    textWert(body.get("vorgangsNr"), "Vorgangsnummer"),
                    textWert(body.get("zahlungsmonat"), "Zahlungsmonat"),
                    angemeldet.kennung(),
                    boolWertDefensiv(body.get("vierAugenErforderlich")),
                    textWertOptional(body.get("empfaengerIban")),
                    textWertOptional(body.get("empfaengerBic")),
                    textWertOptional(body.get("empfaengerName"))));
            return Map.of(
                    "zahlung", zahlungMap(ergebnis.zahlung()),
                    "bedarfsgemeinschaft", ergebnis.bedarfsgemeinschaft() == null
                            ? null : bgMap(ergebnis.bedarfsgemeinschaft()),
                    "bereitsGebucht", ergebnis.bereitsGebucht());
        }
        if (pfad.equals("/api/kassenlauf") && "POST".equals(methode)) {
            Kassenlaeufergebnis ergebnis = service.uebergebeBereiteZahlungen(angemeldet.kennung());
            return Map.of("anzahl", ergebnis.anzahl(), "summe", ergebnis.summe(),
                    "hinweis", ergebnis.anzahl() == 0
                            ? "Keine bereitgestellten Zahlungen vorhanden."
                            : ergebnis.anzahl() + " Zahlungen auf ÜBERGEBEN gesetzt.");
        }
        if (pfad.equals("/api/saga/antrag") && "POST".equals(methode)) {
            if (sagas == null) {
                throw new FachlicheException(FachlicheException.NICHT_GEFUNDEN, "Saga-Engine nicht verfügbar");
            }
            Map<String, Object> body = leseKoerper(exchange);
            body.put("sachbearbeiterId", angemeldet.kennung());
            AntragsSagaProzess.validierePayload(body);
            String jsonPayload = Json.stringify(body);
            String sagaId = sagas.run(AntragsSagaProzess.definition(), jsonPayload);
            return Map.of("sagaId", sagaId, "status", "ERFOLGREICH",
                    "hinweis", "End-to-End-Antrag erfolgreich verarbeitet (Vorgang, Prüfung, Bescheid, Erstauszahlung).");
        }
        if (teile.length == 5 && teile[2].equals("zahlungen") && teile[4].equals("freigabe")
                && "POST".equals(methode)) {
            // Rolleninformation (statt Kennungs-Sonderfall): nur echte Admins dürfen
            // ausnahmsweise die eigene Anordnung rechnerisch feststellen
            Zahlung freigegeben = service.freigebenZahlung(
                    Long.parseLong(teile[3]), angemeldet.kennung(), angemeldet.istAdmin());
            return Map.of("zahlung", zahlungMap(freigegeben),
                    "hinweis", "Zahlung freigegeben (Status: BEREIT, rechnerisch festgestellt).");
        }
        if (teile.length == 5 && teile[2].equals("zahlungen") && teile[4].equals("storno")
                && "POST".equals(methode)) {
            Zahlung storniert = service.storniereZahlung(Long.parseLong(teile[3]), angemeldet.kennung());
            return Map.of("zahlung", zahlungMap(storniert),
                    "hinweis", "Zahlung storniert, Restbudget erhöht.");
        }
        if (pfad.equals("/api/zahlungen") && "GET".equals(methode)) {
            return Map.of("zahlungen", service.letzteZahlungen(limit(exchange, 20))
                    .stream().map(LmgServer::zahlungMap).toList());
        }
        if (pfad.equals("/api/journal") && "GET".equals(methode)) {
            return Map.of("eintraege", service.letztesJournal(limit(exchange, 20))
                    .stream().map(LmgServer::journalMap).toList());
        }
        if (pfad.equals("/api/system/status") && "GET".equals(methode)) {
            return statusMap(diagnose.status());
        }
        if (pfad.equals("/api/system/wiederanlauf") && "POST".equals(methode)) {
            int kompensiert = diagnose.wiederanlauf();
            return Map.of("kompensiert", kompensiert,
                    "hinweis", kompensiert == 0
                            ? "Keine halbfertigen Vorgänge der Demo-Antragsverarbeitung vorhanden."
                            : kompensiert + " halbfertige Vorgänge beendet.");
        }
        throw new FachlicheException(FachlicheException.NICHT_GEFUNDEN,
                "Unbekannter Endpunkt oder nicht unterstützte Methode: " + methode + " " + pfad);
    }

    // ------------------------------------------------------------------
    // Statische Oberfläche (Single-Page-Application aus dem Klassenpfad)
    // ------------------------------------------------------------------

    private void statisch(HttpExchange exchange, String pfad) throws IOException {
        String resource = switch (pfad) {
            case "/", "/index.html" -> "/webapp/index.html";
            case "/app.js" -> "/webapp/app.js";
            case "/druck.js" -> "/webapp/druck.js";
            case "/style.css" -> "/webapp/style.css";
            default -> null;
        };
        if (resource == null || getClass().getResource(resource) == null) {
            byte[] text = "Nicht gefunden".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
            exchange.sendResponseHeaders(404, text.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(text);
            }
            return;
        }
        String typ = resource.endsWith(".html") ? "text/html; charset=utf-8"
                : resource.endsWith(".css") ? "text/css; charset=utf-8"
                : "application/javascript; charset=utf-8";
        exchange.getResponseHeaders().set("Content-Type", typ);
        exchange.getResponseHeaders().set("Content-Security-Policy", "default-src 'self'");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        // try-with-resources: sonst bleibt das Datei-Handle offen und blockiert
        // auf Windows z. B. den Maven-Ressourcen-Kopiervorgang
        byte[] inhalt;
        try (var eingabe = getClass().getResourceAsStream(resource)) {
            inhalt = eingabe.readAllBytes();
        }
        exchange.getResponseHeaders().set("Content-Type", typ);
        exchange.sendResponseHeaders(200, inhalt.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(inhalt);
        }
    }

    // ------------------------------------------------------------------
    // Abbildung der Fachobjekte auf JSON-Maps
    // ------------------------------------------------------------------

    private static Map<String, Object> bgMap(Bedarfsgemeinschaft bg) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", bg.id());
        map.put("aktenzeichen", bg.aktenzeichen());
        map.put("hauptpersonName", bg.hauptpersonName());
        map.put("statusAktiv", bg.statusAktiv());
        map.put("budgetRest", bg.budgetRest());
        map.put("vermoegen", bg.vermoegen());
        map.put("wiedervorlageAm", bg.wiedervorlageAm());
        map.put("version", bg.version());
        map.put("iban", bg.iban());
        map.put("bic", bg.bic());
        map.put("kontoinhaber", bg.kontoinhaber());
        return map;
    }

    private List<Map<String, Object>> listeMap(List<VorgangslisteEintrag> eintraege) {
        List<Map<String, Object>> liste = new ArrayList<>();
        for (VorgangslisteEintrag e : eintraege) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", e.id());
            map.put("aktenzeichen", e.aktenzeichen());
            map.put("hauptpersonName", e.hauptpersonName());
            map.put("statusAktiv", e.statusAktiv());
            map.put("budgetRest", e.budgetRest());
            map.put("wiedervorlageAm", e.wiedervorlageAm());
            map.put("zahlbetragVorschau", e.zahlbetragVorschau());
            liste.add(map);
        }
        return liste;
    }

    private Map<String, Object> detailMap(Vorgangsdetail detail) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("bedarfsgemeinschaft", bgMap(detail.bedarfsgemeinschaft()));
        List<Map<String, Object>> faelle = new ArrayList<>();
        if (detail.berechnung() != null) {
            for (Leistungsberechnung.FallBerechnung fb : detail.berechnung().faelle()) {
                SozialFall zugehoerig = detail.faelle().stream()
                        .filter(f -> f.id() == fb.fallId()).findFirst().orElse(null);
                faelle.add(fallMap(zugehoerig, fb));
            }
        } else {
            for (SozialFall f : detail.faelle()) {
                faelle.add(fallMap(f, null));
            }
        }
        map.put("faelle", faelle);
        map.put("gesamtbedarf", detail.berechnung() == null ? null : detail.berechnung().gesamtbedarf());
        map.put("anrechenbaresEinkommen", detail.berechnung() == null
                ? null : detail.berechnung().anrechenbaresEinkommen());
        map.put("verwertbaresVermoegen", detail.berechnung() == null
                ? null : detail.berechnung().verwertbaresVermoegen());
        map.put("zahlbetrag", detail.berechnung() == null ? null : detail.berechnung().zahlbetrag());
        map.put("zahlungen", detail.zahlungen().stream().map(LmgServer::zahlungMap).toList());
        map.put("journal", detail.journal().stream().map(LmgServer::journalMap).toList());
        map.put("bescheid", detail.bescheid() == null ? null : bescheidMap(detail.bescheid()));
        return map;
    }

    private static Map<String, Object> fallMap(SozialFall fall, Leistungsberechnung.FallBerechnung berechnung) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", fall.id());
        map.put("bgId", fall.bgId());
        map.put("regelbedarfsstufe", fall.regelbedarfsstufe());
        map.put("merkzeichenG", fall.merkzeichenG());
        map.put("schwanger", fall.schwanger());
        map.put("anzKinderUnter7", fall.anzKinderUnter7());
        map.put("anzKinderUnter16", fall.anzKinderUnter16());
        map.put("anzKinderMinderjaehrig", fall.anzKinderMinderjaehrig());
        map.put("warmwasser", fall.warmwasser());
        map.put("kduMiete", fall.kduMiete());
        map.put("kduBetriebskosten", fall.kduBetriebskosten());
        map.put("kduHeizung", fall.kduHeizung());
        map.put("einkommenRente", fall.einkommenRente());
        map.put("einkommenErwerb", fall.einkommenErwerb());
        map.put("abzSteuern", fall.abzSteuern());
        map.put("abzSozialversicherung", fall.abzSozialversicherung());
        map.put("abzVersicherungen", fall.abzVersicherungen());
        map.put("abzErwerbsaufwendungen", fall.abzErwerbsaufwendungen());
        map.put("version", fall.version());
        if (berechnung != null) {
            map.put("regelbedarf", berechnung.regelbedarf());
            map.put("mehrbedarfG", berechnung.mehrbedarfG());
            map.put("mehrbedarfSchwangere", berechnung.mehrbedarfSchwangere());
            map.put("mehrbedarfAlleinerziehend", berechnung.mehrbedarfAlleinerziehend());
            map.put("mehrbedarfWarmwasser", berechnung.mehrbedarfWarmwasser());
            map.put("mehrbedarf", berechnung.mehrbedarf());
            map.put("kdu", berechnung.kdu());
            map.put("kduUnangemessen", berechnung.kduUnangemessen());
            map.put("gesamtbedarfFall", berechnung.gesamtbedarf());
            map.put("freibetragErwerb", berechnung.freibetragErwerb());
            map.put("absetzbetaetrag", berechnung.absetzbetaetrag());
            map.put("anrechenbaresEinkommenFall", berechnung.anrechenbaresEinkommen());
            map.put("zahlbetragFall", berechnung.zahlbetrag());
        }
        return map;
    }

    private static Map<String, Object> statusMap(Systemstatus s) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("dbErreichbar", s.dbErreichbar());
        map.put("dbFehler", s.dbFehler());
        map.put("dbProdukt", s.dbProdukt());
        map.put("dbVersion", s.dbVersion());
        map.put("dbAntwortMillis", s.dbAntwortMillis());
        map.put("dbZeitpunkt", s.dbZeitpunkt());
        map.put("anzahlVorgaenge", s.anzahlVorgaenge());
        map.put("anzahlSozialFaelle", s.anzahlSozialFaelle());
        map.put("anzahlZahlungen", s.anzahlZahlungen());
        map.put("anzahlOffeneSagas", s.anzahlOffeneSagas());
        map.put("anzahlAbgebrocheneSagas", s.anzahlAbgebrocheneSagas());
        map.put("tasksAktiv", s.tasksAktiv());
        map.put("tasksErfolgreich", s.tasksErfolgreich());
        map.put("tasksFehlgeschlagen", s.tasksFehlgeschlagen());
        map.put("tasksAbgebrochen", s.tasksAbgebrochen());
        map.put("tasksAbgelehnt", s.tasksAbgelehnt());
        map.put("tasksDurchschnittMillis", s.tasksDurchschnittMillis());
        map.put("javaVersion", s.javaVersion());
        map.put("serverStart", s.serverStart());
        map.put("serverLaufzeitSekunden", s.serverLaufzeitSekunden());
        return map;
    }

    private static Map<String, Object> zahlungMap(Zahlung z) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", z.id());
        map.put("bgId", z.bgId());
        map.put("fallId", z.fallId());
        map.put("vorgangsNr", z.vorgangsNr());
        map.put("zahlungsmonat", z.zahlungsmonat());
        map.put("auszahlungsbetrag", z.auszahlungsbetrag());
        map.put("status", z.status());
        map.put("erstelltAm", z.erstelltAm());
        map.put("sachbearbeiterId", z.sachbearbeiterId());
        map.put("freigegebenVon", z.freigegebenVon());
        map.put("empfaengerIban", z.empfaengerIban());
        map.put("empfaengerBic", z.empfaengerBic());
        map.put("empfaengerName", z.empfaengerName());
        return map;
    }

    private static Map<String, Object> journalMap(Journaleintrag j) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", j.id());
        map.put("bgId", j.bgId());
        map.put("aktionText", j.aktionText());
        map.put("sachbearbeiterId", j.sachbearbeiterId());
        map.put("erstelltAm", j.erstelltAm());
        return map;
    }

    private static Map<String, Object> bescheidMap(Bescheid b) {
        if (b == null) return null;
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", b.id());
        map.put("bgId", b.bgId());
        map.put("bescheidNr", b.bescheidNr());
        map.put("bescheidDatum", b.bescheidDatum() != null ? b.bescheidDatum().toString() : null);
        map.put("bewilligungVon", b.bewilligungVon());
        map.put("bewilligungBis", b.bewilligungBis());
        map.put("zahlbetrag", b.zahlbetrag());
        map.put("status", b.status());
        map.put("erstelltVon", b.erstelltVon());
        map.put("erstelltAm", b.erstelltAm() != null ? b.erstelltAm().toString() : null);
        return map;
    }

    // ------------------------------------------------------------------
    // Eingabe-/Ausgabehilfen
    // ------------------------------------------------------------------

    /** Maximale Größe des Anfragekörpers (Schutz vor Speicher-Exhaustion). */
    private static final int MAX_BODY_BYTES = 1_000_000;

    private Map<String, Object> leseKoerper(HttpExchange exchange) throws IOException {
        byte[] roh = exchange.getRequestBody().readNBytes(MAX_BODY_BYTES + 1);
        if (roh.length > MAX_BODY_BYTES) {
            throw new IllegalArgumentException("Anfragekörper zu groß (maximal 1 MB)");
        }
        String text = new String(roh, StandardCharsets.UTF_8);
        Object geparst = Json.parse(text);
        if (!(geparst instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("Anfragekörper muss ein JSON-Objekt sein");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> ergebnis = (Map<String, Object>) map;
        return ergebnis;
    }

    private static String textWert(Object wert, String feldname) {
        if (wert == null) {
            throw new IllegalArgumentException(feldname + " fehlt im Anfragekörper");
        }
        return String.valueOf(wert);
    }

    private static String textWertOptional(Object wert) {
        if (wert == null || String.valueOf(wert).isBlank()) {
            return null;
        }
        return String.valueOf(wert).trim();
    }

    private static long zahlWert(Object wert, String feldname) {
        if (!(wert instanceof Number zahl)) {
            throw new IllegalArgumentException(feldname + " muss eine Zahl sein");
        }
        return zahl.longValue();
    }

    private static boolean boolWert(Object wert, String feldname) {
        if (!(wert instanceof Boolean b)) {
            throw new IllegalArgumentException(feldname + " muss true/false sein");
        }
        return b;
    }

    private static boolean boolWertDefensiv(Object wert) {
        if (wert == null) {
            return false;
        }
        if (wert instanceof Boolean b) {
            return b;
        }
        return "true".equalsIgnoreCase(String.valueOf(wert));
    }

    private static long zahlWertDefensiv(Object wert) {
        if (wert == null) {
            return 0;
        }
        if (wert instanceof Number zahl) {
            return zahl.longValue();
        }
        return 0;
    }

    private static BigDecimal betragWert(Object wert) {
        if (wert == null) {
            return BigDecimal.ZERO.setScale(2);
        }
        if (!(wert instanceof BigDecimal betrag)) {
            throw new IllegalArgumentException("Beträge müssen Zahlen sein");
        }
        return betrag;
    }

    private static LocalDate datumWert(Object wert) {
        if (wert == null || String.valueOf(wert).isBlank()) {
            return null;
        }
        return LocalDate.parse(String.valueOf(wert));
    }

    private static int limit(HttpExchange exchange, int standard) {
        String query = exchange.getRequestURI().getRawQuery();
        if (query == null) {
            return standard;
        }
        for (String paar : query.split("&")) {
            String[] teile = paar.split("=", 2);
            if (teile.length == 2 && teile[0].equals("limit")) {
                return Integer.parseInt(URLDecoder.decode(teile[1], StandardCharsets.UTF_8));
            }
        }
        return standard;
    }

    private void antworte(HttpExchange exchange, int status, Map<String, Object> koerper) throws IOException {
        byte[] inhalt = Json.write(koerper).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Content-Security-Policy", "default-src 'self'");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.sendResponseHeaders(status, inhalt.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(inhalt);
        }
    }

    private Thread neuerHttpThread(Runnable runnable) {
        Thread thread = new Thread(runnable, "lmg-http");
        thread.setDaemon(true);
        return thread;
    }
}
