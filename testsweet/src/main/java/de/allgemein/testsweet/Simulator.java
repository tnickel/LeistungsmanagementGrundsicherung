package de.allgemein.testsweet;

import de.allgemein.lmg.config.LmgConfig;
import de.allgemein.lmg.service.LeistungsService;
import de.allgemein.lmg.service.SozialLeistungsService;
import de.allgemein.lmg.service.Systemdiagnose;
import de.allgemein.lmg.web.Benutzerverwaltung;
import de.allgemein.lmg.web.LmgServer;
import de.allgemein.lmg.web.Json;
import de.allgemein.tx.ConnectionPool;
import de.allgemein.tx.SimpleConnectionPool;
import de.allgemein.tx.TaskController;
import de.allgemein.tx.TxManager;
import de.allgemein.tx.saga.SagaEngine;
import de.allgemein.tx.saga.SagaJournal;

import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.LinkedHashMap;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Orchestriert einen Simulator-Lauf (Modus 1: Transaktionstest über die Fachschicht):
 * optional Altdaten aufräumen → Auftragstopf erzeugen → N Mitarbeiter-Threads parallel
 * arbeiten den Pool ab → wenn alle fertig sind, verifiziert der {@link Verifikator}
 * die Erwartungshaltung jedes Auftrags direkt gegen die Datenbank. Alles wird über
 * den {@link SweetLog} protokolliert; die Auswertung der Logfiles erfolgt bewusst
 * manuell gemeinsam mit dem Nutzer.
 */
public final class Simulator {

    /** Testfallkategorie des Laufs: positive Standardszenarien, abgewehrte
     *  Fachfehler (NEGATIV) oder schlanker Massenbetrieb für den Lasttest. */
    public enum Kategorie { POSITIV, NEGATIV, LAST, STRESS }

    /** Zulässige Auftrags-Obergrenze je Kategorie: Der Testfallbestand ist bewusst
     *  begrenzt — 500 Positiv- und 500 Negativ-Kombinationen (kuratierte Arten +
     *  Kombinationsgenerator), 1200 schlanke Lastdurchläufe und 3000 Stress-User
     *  (je User zwei Wellen = 6000 Transaktionsblöcke). */
    public static int auftraegeLimit(Kategorie kategorie) {
        return switch (kategorie) {
            case POSITIV, NEGATIV -> 500;
            case LAST -> 1200;
            case STRESS -> 3000;
        };
    }

    /** Wirft eine klare Meldung, wenn Mitarbeiter oder Aufträge außerhalb der
     *  zulässigen Spanne liegen — vor jedem Laufstart geprüft (GUI, CLI). */
    public static void pruefeKonfiguration(Kategorie kategorie, int mitarbeiter, int auftraege) {
        if (mitarbeiter < 1 || mitarbeiter > 50) {
            throw new IllegalStateException(
                    "Mitarbeiter muss zwischen 1 und 50 liegen (" + mitarbeiter + ")");
        }
        int limit = auftraegeLimit(kategorie);
        if (auftraege < 1 || auftraege > limit) {
            throw new IllegalStateException("Die Auftragszahl muss zwischen 1 und " + limit
                    + " liegen für Kategorie " + kategorie + " (" + auftraege
                    + " eingegeben) — der Testfallbestand ist bewusst begrenzt");
        }
    }

    /** Laufparameter aus der GUI; Kategorie standardmäßig POSITIV. */
    public record LaufKonfiguration(
            int mitarbeiter, int auftraege, long seed, long denkzeitMs, boolean aufraeumen,
            Kategorie kategorie) {
        public LaufKonfiguration {
            if (kategorie == null) {
                kategorie = Kategorie.POSITIV;
            }
            if (denkzeitMs < 0) throw new IllegalArgumentException("Denkzeit darf nicht negativ sein");
        }
    }

    public enum Phase { BEREIT, AUFRAEUMEN, LAUF, VERIFIKATION, FERTIG, FEHLGESCHLAGEN, ABGEBROCHEN }

    public final String dbUrl;
    public final String dbUser;
    public final String dbPass;

    private volatile Phase phase = Phase.BEREIT;
    private volatile List<Auftrag> auftraege = List.of();
    private volatile List<Mitarbeiter> mitarbeiter = List.of();
    private volatile LaufKonfiguration konfiguration;
    private volatile SweetLog log;
    private volatile List<Auftrag.Pruefung> globalePruefungen = List.of();
    private volatile SimpleConnectionPool pool;
    private volatile SozialLeistungsService service;
    private volatile boolean abgebrochen;
    private volatile LocalDateTime laufStart;
    private volatile LocalDateTime laufEnde;
    /** Monotone Stoppuhr (System.nanoTime) — unabhängig von Wanduhr-Verstellungen;
     *  laufStart/laufEnde bleiben als Wanduhrzeit für die Protokolle erhalten. */
    private volatile long laufStartNano;
    private volatile long laufEndeNano;
    private final AtomicInteger naechsterAuftrag = new AtomicInteger();
    private volatile String letzteFehlermeldung;
    private volatile String letztesLogVerzeichnis;
    private volatile String letztesPdf;
    private volatile String letztesMd;
    private volatile String modusLetzter = "FACHSCHICHT";
    private volatile Kategorie kategorieLetzter = Kategorie.POSITIV;
    /** Harte Laufsperre: zuschlagen VOR dem Thread-Start — die Phase wird im Lauf
     *  erst Sekunden später gesetzt, das Fenster ließ einen zweiten Lauf zu. */
    private final java.util.concurrent.atomic.AtomicBoolean laufAktiv =
            new java.util.concurrent.atomic.AtomicBoolean(false);
    /** STRESS-Phasenbarriere: Index des ersten BEARBEITEN-Auftrags (-1 = kein
     *  Stresstest); Welle 2 wartet, bis alle ANLEGEN-Aufträge abgeschlossen sind. */
    private volatile int stressPhase1Ende = -1;
    private volatile CountDownLatch stressPhase1Latch;
    private volatile List<WebMitarbeiter> webMitarbeiter = List.of();
    private final java.util.concurrent.ConcurrentHashMap<String, String> webKonten =
            new java.util.concurrent.ConcurrentHashMap<>();
    /** Administrationskonto für Rollenwechsel-Abläufe (Storno freigegebener Zahlungen). */
    private volatile String webAdminKennung = "admin-01";
    private volatile String webAdminPasswort = "admin";
    private ConnectionPool webPool;
    private TaskController webTasks;
    private LmgServer webServer;
    private volatile ExecutorService laufExecutor;

    public Simulator() {
        this.dbUrl = System.getProperty("tx.url", umgebung("TX_URL", "jdbc:oracle:thin:@//localhost:1521/FREEPDB1"));
        this.dbUser = System.getProperty("tx.user", umgebung("TX_USER", "tx_demo"));
        this.dbPass = System.getProperty("tx.pass", umgebung("TX_PASS", "tx_demo"));
        try {
            // Treiber explizit registrieren (SPI-Autoregistrierung ist im
            // exec:java-Kontext nicht zuverlässig; gleiche Praxis wie in den DB-Tests)
            Class.forName("oracle.jdbc.OracleDriver");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Oracle-Treiber (ojdbc11) fehlt im Classpath", e);
        }
    }

    private static String umgebung(String name, String standard) {
        String wert = System.getenv(name);
        return wert == null || wert.isBlank() ? standard : wert;
    }

    // ------------------------------------------------------------------
    // Zustandsabfragen für GUI/API
    // ------------------------------------------------------------------

    public Phase phase() {
        return phase;
    }

    boolean laufAktiv() { return laufAktiv.get(); }

    public List<Auftrag> auftraege() {
        return auftraege;
    }

    /** Simulierte Mitarbeiter des aktuellen/letzten Laufs (leer vor dem ersten Lauf). */
    public List<Mitarbeiter> mitarbeiterListe() {
        return mitarbeiter;
    }

    public LaufKonfiguration konfiguration() {
        return konfiguration;
    }

    public String letzteFehlermeldung() {
        return letzteFehlermeldung;
    }

    /** Logverzeichnis des letzten Laufs (bleibt nach Laufende für die Auswertung sichtbar). */
    public String letztesLogVerzeichnis() {
        return letztesLogVerzeichnis;
    }

    public LocalDateTime laufStart() {
        return laufStart;
    }

    public LocalDateTime laufEnde() {
        return laufEnde;
    }

    /** PDF-Testdokumentation des letzten Laufs — nach Serverstart lazy aus dem
     *  neuesten Laufverzeichnis ermittelt, damit der PDF-Button auch ohne neuen
     *  Lauf funktioniert. */
    public String letztesPdf() {
        if (letztesPdf != null) {
            return letztesPdf;
        }
        return neuesteDatei("testdokumentation.pdf");
    }

    /** Maschinenlesbare Markdown-Fassung des letzten Laufs (lazy wie das PDF). */
    public String letztesMd() {
        if (letztesMd != null) {
            return letztesMd;
        }
        return neuesteDatei("testdokumentation.md");
    }

    /** Pfad der neuesten Datei dieses Namens über alle Laufverzeichnisse. */
    private String neuesteDatei(String dateiname) {
        try {
            java.nio.file.Path logs = PathLogBasis();
            if (java.nio.file.Files.isDirectory(logs)) {
                java.nio.file.Path neuestes = null;
                try (var runs = java.nio.file.Files.list(logs)) {
                    for (java.nio.file.Path run : runs.filter(p -> p.getFileName().toString()
                            .startsWith("run-")).toList()) {
                        java.nio.file.Path kandidat = run.resolve(dateiname);
                        if (java.nio.file.Files.exists(kandidat)
                                && (neuestes == null || kandidat.compareTo(neuestes) > 0)) {
                            neuestes = kandidat;
                        }
                    }
                }
                if (neuestes != null) {
                    return neuestes.toString();
                }
            }
        } catch (Exception e) {
            // nichts gefunden — Aufrufer behandelt null
        }
        return null;
    }

    public SweetLog log() {
        SweetLog aktuellesLog = log;
        return aktuellesLog;
    }

    public long laufdauerSekunden() {
        long von = laufStartNano;
        if (von == 0) {
            return 0;
        }
        long bis = laufEndeNano != 0 ? laufEndeNano : System.nanoTime();
        return Duration.ofNanos(Math.max(0, bis - von)).toSeconds();
    }

    SozialLeistungsService service() {
        return service;
    }

    boolean abgebrochen() {
        return abgebrochen;
    }

    // ------------------------------------------------------------------
    // Start / Stopp
    // ------------------------------------------------------------------

    /** Startet einen Lauf synchron im aufrufenden Thread (für CLI/Batch-Betrieb). */
    public void laufSynchron(LaufKonfiguration konfig) throws Exception {
        pruefeKonfiguration(konfig.kategorie(), konfig.mitarbeiter(), konfig.auftraege());
        if (!laufAktiv.compareAndSet(false, true)) {
            throw new IllegalStateException("Es läuft bereits ein Simulator-Lauf");
        }
        try {
            this.konfiguration = konfig;
            this.abgebrochen = false;
            this.letzteFehlermeldung = null;
            laufAusfuehren(konfig);
        } finally {
            if (log != null) {
                log.close(); // auch im Fehlerpfad: Logdateien sauber schließen
                log = null;
            }
            laufAktiv.set(false);
        }
    }

    /** Startet einen Lauf in einem Hintergrund-Thread; kehrt sofort zurück. */
    public synchronized void starte(LaufKonfiguration konfig) {
        pruefeKonfiguration(konfig.kategorie(), konfig.mitarbeiter(), konfig.auftraege());
        if (!laufAktiv.compareAndSet(false, true)) {
            throw new IllegalStateException("Es läuft bereits ein Simulator-Lauf (Phase "
                    + phase + ") — bitte das Ende abwarten");
        }
        this.konfiguration = konfig;
        this.abgebrochen = false;
        this.letzteFehlermeldung = null;
        Thread laufThread = new Thread(() -> {
            try {
                laufAusfuehren(konfig);
            } catch (Exception e) {
                letzteFehlermeldung = e.getClass().getSimpleName() + ": " + e.getMessage();
                phase = Phase.ABGEBROCHEN;
                if (log != null) {
                    log.fehler("SIMULATOR", "Lauf abgebrochen: " + letzteFehlermeldung);
                }
                schliesseRessourcen();
            } finally {
                if (log != null) {
                    log.close(); // in jedem Pfad schließen — auch nach Abbruch
                    log = null;
                }
                laufAktiv.set(false);
            }
        }, "testsweet-lauf");
        laufThread.setDaemon(true);
        laufThread.start();
    }

    /** Bricht den Lauf ab: Mitarbeiter beenden den aktuellen Auftrag nicht mehr weiter. */
    public void stoppe() {
        abgebrochen = true;
        ExecutorService executor = laufExecutor;
        if (executor != null) executor.shutdownNow();
        else webMitarbeiter.forEach(WebMitarbeiter::schliesse);
    }

    // ------------------------------------------------------------------
    // Modus 2: Weboberflächen-Lauf (Selenium steuert sichtbare Chrome-Fenster)
    // ------------------------------------------------------------------

    public String letzterModus() {
        return modusLetzter;
    }

    /** Kategorie des letzten/aktuellen Laufs (POSITIV, NEGATIV, LAST). */
    public Kategorie letzteKategorie() {
        return kategorieLetzter;
    }

    public List<WebMitarbeiter> webMitarbeiterListe() {
        return webMitarbeiter;
    }

    /** Passwort eines Web-Kontos (Konten werden beim Web-Lauf-Start festgelegt). */
    public String webPasswort(String kennung) {
        return webKonten.getOrDefault(kennung, "sb-01");
    }

    /** Administrationskonto für Rollenwechsel (Storno freigegebener Zahlungen). */
    public String webAdminKennung() {
        return webAdminKennung;
    }

    public String webAdminPasswort() {
        return webAdminPasswort;
    }

    /** Zufälliger ANDERER Kollege für die Vier-Augen-Freigabe über die Oberfläche. */
    public String zufallsWebKollege(String ausser) {
        if (webKonten.size() <= 1) {
            return "sb-02"; // Einzelgänger: fester Ersatzkollege
        }
        String kollege;
        do {
            kollege = webKonten.keySet().stream()
                    .skip(ThreadLocalRandom.current().nextInt(webKonten.size())).findFirst()
                    .orElse("sb-02");
        } while (kollege.equals(ausser));
        return kollege;
    }

    /** Startet einen Weboberflächen-Lauf synchron im aufrufenden Thread (für CLI). */
    public void laufWebSynchron(LaufKonfiguration konfig) throws Exception {
        pruefeKonfiguration(konfig.kategorie(), konfig.mitarbeiter(), konfig.auftraege());
        if (!laufAktiv.compareAndSet(false, true)) {
            throw new IllegalStateException("Es läuft bereits ein Simulator-Lauf");
        }
        try {
            this.konfiguration = konfig;
            this.abgebrochen = false;
            this.letzteFehlermeldung = null;
            laufWebAusfuehren(konfig);
        } finally {
            schliesseWebServer();
            if (log != null) {
                log.close(); // auch im Fehlerpfad: Logdateien sauber schließen
                log = null;
            }
            laufAktiv.set(false);
        }
    }

    /** Startet einen Weboberflächen-Lauf in einem Hintergrund-Thread. */
    public synchronized void starteWeb(LaufKonfiguration konfig) {
        pruefeKonfiguration(konfig.kategorie(), konfig.mitarbeiter(), konfig.auftraege());
        if (!laufAktiv.compareAndSet(false, true)) {
            throw new IllegalStateException("Es läuft bereits ein Simulator-Lauf (Phase "
                    + phase + ") — bitte das Ende abwarten");
        }
        // STRESS gibt es nur im Fachschicht-Modus — VOR dem Anlegen von Log/Server/
        // Treiber-Fenster abweisen (siehe auch Guard am Anfang von laufWebAusfuehren)
        if (konfig.kategorie() == Kategorie.STRESS) {
            laufAktiv.set(false);
            throw new IllegalStateException(webStressMeldung());
        }
        this.konfiguration = konfig;
        this.abgebrochen = false;
        this.letzteFehlermeldung = null;
        Thread laufThread = new Thread(() -> {
            try {
                laufWebAusfuehren(konfig);
            } catch (Exception e) {
                letzteFehlermeldung = e.getClass().getSimpleName() + ": " + e.getMessage();
                phase = Phase.ABGEBROCHEN;
                if (log != null) {
                    log.fehler("SIMULATOR", "Web-Lauf abgebrochen: " + letzteFehlermeldung);
                }
                schliesseWebServer();
            } finally {
                if (log != null) {
                    log.close(); // in jedem Pfad schließen — auch nach Abbruch
                    log = null;
                }
                laufAktiv.set(false);
            }
        }, "testsweet-web-lauf");
        laufThread.setDaemon(true);
        laufThread.start();
    }

    private static String webStressMeldung() {
        return "Der Stresstest (User anlegen + nachbearbeiten) ist für den Fachschicht-Modus "
                + "vorgesehen — für die Weboberfläche die Kategorie LAST nutzen";
    }

    private void laufWebAusfuehren(LaufKonfiguration konfig) throws Exception {
        // STRESS+WEB-Guard als ALLERERSTE Anweisung — bevor irgendetwas angefasst
        // (SweetLog, eingebetteter Server, WebDriverManager, Fenster) wird.
        if (konfig.kategorie() == Kategorie.STRESS) {
            throw new IllegalStateException(webStressMeldung());
        }
        phase = Phase.BEREIT;
        auftraege = List.of();
        modusLetzter = "WEB";
        kategorieLetzter = konfig.kategorie();
        laufStart = LocalDateTime.now();
        laufStartNano = System.nanoTime();
        laufEnde = null;
        laufEndeNano = 0;
        globalePruefungen = List.of();
        log = new SweetLog(PathLogBasis());
        letztesLogVerzeichnis = log.runVerzeichnis.toString();
        log.ereignis("SIMULATOR", "", "Web-Lauf gestartet: " + konfig.mitarbeiter()
                + " sichtbare Browserfenster, " + konfig.auftraege() + " Aufträge, Seed "
                + konfig.seed() + ", Oberfläche " + WebHelfer.basisUrl);

        LmgConfig webConfig = LmgConfig.load();
        boolean extern = WebHelfer.expliziteBasisUrl() != null;
        WebHelfer.basisUrl = webZiel(webConfig.port(), WebHelfer.expliziteBasisUrl());
        boolean eingebettet = !extern && starteEingebettetenLmgServer(konfig.mitarbeiter(), webConfig);
        if (!eingebettet) {
            webKonten.clear();
            webKonten.put("sb-01", "sb-01");
            webKonten.put("sb-02", "sb-02");
            webAdminKennung = "admin-01";
            webAdminPasswort = "admin";
            pruefeExternesWebziel(); // vor Cleanup, Browserstart und fachlichen Schreibzugriffen
            log.ereignis("SIMULATOR", "", "Externes Webziel und SQL-Datenbank abgeglichen: "
                    + WebHelfer.basisUrl);
        }

        // Fenster eines früheren Laufs schließen — inklusive verwaister Fenster aus
        // gestorbenen Server-Prozessen — bevor neue geöffnet werden
        webMitarbeiter.forEach(WebMitarbeiter::schliesse);
        WebMitarbeiter.raeumeVerwaisteFensterAuf();
        pruefeDatenbank();
        pruefeKeineAltenAktenzeichen(konfig);
        if (konfig.aufraeumen()) {
            phase = Phase.AUFRAEUMEN;
            log.ereignis("SIMULATOR", "", "Altdaten aufgeräumt: "
                    + raeumeAltdatenAuf() + " SWEET-Vorgänge entfernt");
        }

        WebMitarbeiter.bereiteTreiberVor();
        long seed = konfig.seed() != 0 ? konfig.seed() : ThreadLocalRandom.current().nextLong();
        auftraege = erzeugeTopf(konfig, seed, true);
        initialisiereStressBarriere();
        naechsterAuftrag.set(0);

        webMitarbeiter = new ArrayList<>();
        List<String> kennungen = new ArrayList<>(webKonten.keySet());
        for (int i = 0; i < konfig.mitarbeiter(); i++) {
            String kennung = eingebettet
                    ? String.format("sb-web-%02d", i + 1)
                    : kennungen.get(i % kennungen.size());
            webMitarbeiter.add(new WebMitarbeiter(this, kennung, webPasswort(kennung),
                    i, konfig.mitarbeiter(), konfig.denkzeitMs()));
        }
        log.ereignis("SIMULATOR", "", "Chrome-Fenster werden geöffnet und nebeneinander "
                + "angeordnet — die Oberfläche läuft unter " + WebHelfer.basisUrl);

        phase = Phase.LAUF;
        ExecutorService executor = Executors.newFixedThreadPool(konfig.mitarbeiter());
        laufExecutor = executor;
        try {
            for (WebMitarbeiter m : webMitarbeiter) {
                executor.submit(m);
            }
            executor.shutdown();
            boolean fertig = beendeExecutor(executor, TimeUnit.MINUTES.toMillis(30), () -> {
                abgebrochen = true;
                webMitarbeiter.forEach(WebMitarbeiter::schliesse);
            });
            if (!fertig) {
                log.fehler("SIMULATOR", "Laufzeit-Limit (30 min) überschritten — Zwangsabbruch");
                executor.shutdownNow();
                abgebrochen = true;
            }
        } finally {
            if (!executor.isTerminated()) {
                abgebrochen = true;
                beendeExecutor(executor, 0, () -> webMitarbeiter.forEach(WebMitarbeiter::schliesse));
            }
            laufExecutor = null;
            if (abgebrochen) webMitarbeiter.forEach(WebMitarbeiter::schliesse);
            schliesseWebServer();
        }

        // Verifikation wie in Modus 1: Erwartungshaltung per SQL gegen die Datenbank
        if (abgebrochen) {
            phase = Phase.ABGEBROCHEN;
            log.ereignis("SIMULATOR", "", "Web-Lauf abgebrochen — keine Verifikation");
        } else {
            phase = Phase.VERIFIKATION;
            int verifiziert = new Verifikator(dbUrl, dbUser, dbPass).verifiziereAlle(auftraege, log);
            globalerKonsistenzcheck();
            verifiziert = (int) auftraege.stream().filter(a -> a.status == Auftrag.Status.VERIFIZIERT).count();
            aktualisiereAbschlussphase();
            log.ereignis("SIMULATOR", "", "Web-Verifikation abgeschlossen: " + verifiziert
                    + " von " + auftraege.size() + " Aufträgen vollständig verifiziert");
        }

        laufEnde = LocalDateTime.now();
        laufEndeNano = System.nanoTime();
        schreibeTestdokumentation();
        log.zusammenfassung(zusammenfassungsText());
        log.ereignis("SIMULATOR", "", "Web-Lauf beendet in " + laufdauerSekunden()
                + " s — Browserfenster bleiben zur Ansicht offen (bis zum nächsten Lauf/Stopp).");
    }

    /**
     * Startet den SDN.Leistung-Server im eigenen Prozess (Port 8080) mit eigenen
     * Sim-Konten sb-web-NN — nur wenn der Port frei ist. Rückgabe: true = eingebettet.
     */
    private boolean starteEingebettetenLmgServer(int anzahlMitarbeiter, LmgConfig config) throws Exception {
        try (ServerSocket test = new ServerSocket(config.port())) {
            // Port frei — wir starten selbst
        } catch (Exception belegt) {
            return false;
        }
        String konten = initialisiereWebKonten(anzahlMitarbeiter);
        Class.forName("oracle.jdbc.OracleDriver");
        webPool = new SimpleConnectionPool(dbUrl, dbUser, dbPass,
                Math.max(8, anzahlMitarbeiter + 2));
        TxManager txManager = new TxManager(webPool);
        webTasks = new TaskController(txManager, config.maxTasks(), config.queueCapacity());
        LeistungsService service = new SozialLeistungsService(txManager, config.kduParameter(),
                config.rechtsbehelf());
        SagaEngine sagas = new SagaEngine(txManager, new SagaJournal(webPool));
        Systemdiagnose diagnose = new Systemdiagnose(txManager, webTasks, sagas, Instant.now());
        Benutzerverwaltung benutzerverwaltung = new Benutzerverwaltung(konten,
                Duration.ofMinutes(config.sessionTimeoutMinuten()));
        webServer = new LmgServer(webTasks, service, diagnose, sagas, benutzerverwaltung,
                java.net.InetAddress.getByName("127.0.0.1"), config.port());
        webServer.start();
        log.ereignis("SIMULATOR", "", "Eingebetteter SDN.Leistung-Server gestartet auf Port "
                + config.port() + " mit " + anzahlMitarbeiter + " Sim-Konten (sb-web-01…)");
        return true;
    }

    /** Ein separates Freigabekonto ist auch für ein einziges Browserfenster nötig. */
    String initialisiereWebKonten(int anzahlMitarbeiter) {
        StringBuilder konten = new StringBuilder();
        webKonten.clear();
        for (int i = 1; i <= Math.max(2, anzahlMitarbeiter); i++) {
            String kennung = String.format("sb-web-%02d", i);
            if (konten.length() > 0) konten.append(';');
            konten.append(kennung).append(":sim:sachbearbeiter");
            webKonten.put(kennung, "sim");
        }
        konten.append(";web-admin:sim:admin");
        webAdminKennung = "web-admin";
        webAdminPasswort = "sim";
        return konten.toString();
    }

    private void schliesseWebServer() {
        if (webServer != null) {
            try {
                webServer.close();
            } catch (Exception ignored) {
                // Aufräumen
            }
            webServer = null;
        }
        if (webTasks != null) {
            try {
                webTasks.close();
            } catch (Exception ignored) {
                // Aufräumen
            }
            webTasks = null;
        }
        if (webPool != null) {
            try {
                webPool.close();
            } catch (Exception ignored) {
                // Aufräumen
            }
            webPool = null;
        }
    }

    // ------------------------------------------------------------------
    // Lauf-Ausführung (Hintergrundthread)
    // ------------------------------------------------------------------

    private void laufAusfuehren(LaufKonfiguration konfig) throws Exception {
        phase = Phase.BEREIT;
        auftraege = List.of();
        modusLetzter = "FACHSCHICHT";
        kategorieLetzter = konfig.kategorie();
        laufStart = LocalDateTime.now();
        laufStartNano = System.nanoTime();
        laufEnde = null;
        laufEndeNano = 0;
        globalePruefungen = List.of();
        log = new SweetLog(PathLogBasis());
        letztesLogVerzeichnis = log.runVerzeichnis.toString();
        log.ereignis("SIMULATOR", "", "Lauf gestartet: " + konfig.mitarbeiter() + " Mitarbeiter, "
                + konfig.auftraege() + " Aufträge, Seed " + konfig.seed()
                + ", Denkzeit " + konfig.denkzeitMs() + " ms, DB " + dbUrl);

        // Datenbank erreichbar?
        pruefeDatenbank();
        pruefeKeineAltenAktenzeichen(konfig);

        // Phase 1: optional Altdaten aufräumen
        if (konfig.aufraeumen()) {
            phase = Phase.AUFRAEUMEN;
            int entfernt = raeumeAltdatenAuf();
            log.ereignis("SIMULATOR", "", "Altdaten aufgeräumt: " + entfernt + " SWEET-Vorgänge entfernt");
        }

        // Phase 2: Auftragstopf + Mitarbeiter
        long seed = konfig.seed() != 0 ? konfig.seed() : ThreadLocalRandom.current().nextLong();
        if (konfig.seed() == 0) {
            log.ereignis("SIMULATOR", "", "Zufälliger Seed gewählt: " + seed
                    + " (für Reproduktion ins nächste Laufkonfig-Feld übernehmen)");
        }
        auftraege = erzeugeTopf(konfig, seed, false);
        initialisiereStressBarriere();
        naechsterAuftrag.set(0);

        pool = new SimpleConnectionPool(dbUrl, dbUser, dbPass, konfig.mitarbeiter() + 2);
        service = new SozialLeistungsService(new TxManager(pool));

        mitarbeiter = new ArrayList<>();
        for (int i = 1; i <= konfig.mitarbeiter(); i++) {
            mitarbeiter.add(new Mitarbeiter(this, "sb-sim-" + String.format("%02d", i), konfig.denkzeitMs()));
        }

        phase = Phase.LAUF;
        ExecutorService executor = Executors.newFixedThreadPool(konfig.mitarbeiter());
        laufExecutor = executor;
        try {
            for (Mitarbeiter m : mitarbeiter) {
                executor.submit(m);
            }
            executor.shutdown();
            boolean fertig = beendeExecutor(executor, TimeUnit.MINUTES.toMillis(30), () -> abgebrochen = true);
            if (!fertig) {
                log.fehler("SIMULATOR", "Laufzeit-Limit (30 min) überschritten — Zwangsabbruch");
                executor.shutdownNow();
                abgebrochen = true;
            }
        } finally {
            if (!executor.isTerminated()) {
                abgebrochen = true;
                beendeExecutor(executor, 0, () -> { });
            }
            laufExecutor = null;
            schliesseRessourcen();
        }

        // Phase 3: Verifikation (nur bei regulärem Ende)
        if (abgebrochen) {
            phase = Phase.ABGEBROCHEN;
            log.ereignis("SIMULATOR", "", "Lauf abgebrochen — keine Verifikation, Datenbestand kann inkonsistent sein");
        } else {
            phase = Phase.VERIFIKATION;
            log.ereignis("SIMULATOR", "", "Alle Mitarbeiter fertig — verifiziere Erwartungshaltung gegen die Datenbank …");
            int gespielt = (int) auftraege.stream()
                    .filter(a -> a.status == Auftrag.Status.FERTIG_GESPIELT).count();
            int verifiziert = new Verifikator(dbUrl, dbUser, dbPass)
                    .verifiziereAlle(auftraege, log);
            globalerKonsistenzcheck();
            verifiziert = (int) auftraege.stream().filter(a -> a.status == Auftrag.Status.VERIFIZIERT).count();
            aktualisiereAbschlussphase();
            log.ereignis("SIMULATOR", "", "Verifikation abgeschlossen: " + verifiziert + " von "
                    + gespielt + " erfolgreich gespielten Aufträgen vollständig verifiziert");
        }

        laufEnde = LocalDateTime.now();
        laufEndeNano = System.nanoTime();
        schreibeTestdokumentation();
        log.zusammenfassung(zusammenfassungsText());
        log.ereignis("SIMULATOR", "", "Lauf beendet in " + laufdauerSekunden() + " s — Logdateien: "
                + log.runVerzeichnis);
    }

    /** Auftragstopf je Modus und Kategorie; das Aktenzeichen-Präfix unterscheidet
     *  die Kategorien (POSITIV: SWEET- / SWEET-W-, NEGATIV: SWEET-N- / SWEET-NW-,
     *  LAST: SWEET-L- / SWEET-LW-) und wird im Bericht ausgewiesen. */
    private List<Auftrag> erzeugeTopf(LaufKonfiguration konfig, long seed, boolean web) {
        String praefix = SzenarioBibliothek.PREFIX + switch (konfig.kategorie()) {
            case POSITIV -> web ? "W-" : "";
            case NEGATIV -> web ? "NW-" : "N-";
            case LAST -> web ? "LW-" : "L-";
            case STRESS -> "S-"; // nur Fachschicht (Guard in laufWebAusfuehren/starteWeb)
        };
        return switch (konfig.kategorie()) {
            case POSITIV -> !web
                    ? new SzenarioBibliothek(seed).erzeuge(konfig.auftraege(), praefix)
                    : new WebSzenarioBibliothek(seed).erzeuge(konfig.auftraege(), praefix);
            case NEGATIV -> !web
                    ? new SzenarioBibliothek(seed).erzeugeNegativ(konfig.auftraege(), praefix)
                    : new WebSzenarioBibliothek(seed).erzeugeNegativ(konfig.auftraege(), praefix);
            case LAST -> !web
                    ? new SzenarioBibliothek(seed).erzeugeLast(konfig.auftraege(), praefix)
                    : new WebSzenarioBibliothek(seed).erzeugeLast(konfig.auftraege(), praefix);
            case STRESS -> new SzenarioBibliothek(seed).erzeugeStress(konfig.auftraege(), praefix);
        };
    }

    /** PDF/MD-Testdokumentation — Fehler brechen den Lauf nicht. LAST-Läufe
     *  erhalten bewusst nur einen gekürzten Gesamtreport statt Einzelfall-Seiten. */
    private void schreibeTestdokumentation() {
        try {
            if (kategorieLetzter == Kategorie.LAST || kategorieLetzter == Kategorie.STRESS) {
                java.nio.file.Path pdfPfad = log.runVerzeichnis.resolve("lastreport.pdf");
                java.nio.file.Path mdPfad = log.runVerzeichnis.resolve("lastreport.md");
                LastBericht.schreibe(pdfPfad, mdPfad, this);
                letztesPdf = pdfPfad.toString();
                letztesMd = mdPfad.toString();
                return;
            }
            java.nio.file.Path pdfPfad = log.runVerzeichnis.resolve("testdokumentation.pdf");
            Testdokumentation.schreibe(pdfPfad, this);
            letztesPdf = pdfPfad.toString();
            java.nio.file.Path mdPfad = log.runVerzeichnis.resolve("testdokumentation.md");
            MdBericht.schreibe(mdPfad, this);
            letztesMd = mdPfad.toString();
        } catch (Exception e) {
            log.fehler("SIMULATOR", "Testdokumentation konnte nicht geschrieben werden: "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private void pruefeDatenbank() throws Exception {
        try (Connection c = DriverManager.getConnection(dbUrl, dbUser, dbPass);
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT 1 FROM DUAL")) {
            if (!rs.next()) {
                throw new IllegalStateException("Datenbank-Testquery leer");
            }
        }
        log.ereignis("SIMULATOR", "", "Datenbankverbindung ok (" + dbUrl + ")");
    }

    /**
     * Duplikat-Schutz vor dem Laufstart: Liegen noch SWEET-Vorgänge eines früheren
     * Laufs in der Datenbank und ist Aufräumen nicht aktiviert, würde der neue Lauf
     * dieselben Aktenzeichen wieder anlegen und mitten im Betrieb an der Eindeutig-
     * keitsregel scheitern — der Lauf wird deshalb vorab mit klarer Meldung
     * abgebrochen (das finally der Aufrufer schließt das Log).
     */
    private void pruefeKeineAltenAktenzeichen(LaufKonfiguration konfig) throws Exception {
        if (konfig.aufraeumen()) {
            return; // es wird ohnehin automatisch geräumt
        }
        try (Connection c = DriverManager.getConnection(dbUrl, dbUser, dbPass);
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM BEDARFSGEMEINSCHAFT "
                     + "WHERE AKTENZEICHEN LIKE '" + SzenarioBibliothek.PREFIX + "%'")) {
            rs.next();
            int alte = rs.getInt(1);
            if (alte > 0) {
                throw new IllegalStateException("In der Datenbank liegen noch " + alte
                        + " Vorgänge früherer Läufe (Aktenzeichen " + SzenarioBibliothek.PREFIX
                        + "…). Ein neuer Lauf würde dieselben Aktenzeichen erneut anlegen und "
                        + "an der Eindeutigkeitsregel scheitern. Bitte das Aufräumen aktivieren "
                        + "(Option „Altdaten aufräumen“) oder die Datenbank säubern und den "
                        + "Lauf neu starten.");
            }
        }
    }

    /** Löscht Vorgänge früherer Läufe (SWEET-%); Rückgabe: Anzahl gelöschter Vorgänge. */
    private int raeumeAltdatenAuf() throws Exception {
        try (Connection c = DriverManager.getConnection(dbUrl, dbUser, dbPass)) {
            c.setAutoCommit(false);
            int entfernt = 0;
            try (Statement st = c.createStatement()) {
                String praefix = " WHERE BG_ID IN (SELECT ID FROM BEDARFSGEMEINSCHAFT "
                        + "WHERE AKTENZEICHEN LIKE '" + SzenarioBibliothek.PREFIX + "%')";
                st.executeUpdate("DELETE FROM JOURNAL" + praefix);
                st.executeUpdate("DELETE FROM BESCHEID" + praefix);
                st.executeUpdate("DELETE FROM ZAHLUNG" + praefix);
                st.executeUpdate("DELETE FROM SOZIAL_FALL" + praefix);
                entfernt = st.executeUpdate("DELETE FROM BEDARFSGEMEINSCHAFT WHERE AKTENZEICHEN "
                        + "LIKE '" + SzenarioBibliothek.PREFIX + "%'");
                // vorgangsübergreifende Kassenlauf-Journale früherer Läufe
                st.executeUpdate("DELETE FROM JOURNAL WHERE BG_ID IS NULL "
                        + "AND AKTION_TEXT LIKE 'Kassenlauf%' AND SACHBEARBEITER_ID LIKE 'sb-sim-%'");
                c.commit();
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
            return entfernt;
        }
    }

    public List<Auftrag.Pruefung> globalePruefungen() { return globalePruefungen; }

    public boolean laufErfolgreich() {
        return phase == Phase.FERTIG && auftraege.stream().allMatch(a -> a.status == Auftrag.Status.VERIFIZIERT)
                && globalePruefungen.stream().allMatch(Auftrag.Pruefung::ok);
    }

    /** Einzelfälle und globale Prüfungen bestimmen gemeinsam den Laufstatus. */
    void aktualisiereAbschlussphase() {
        phase = abgebrochen ? Phase.ABGEBROCHEN
                : !auftraege.isEmpty()
                && auftraege.stream().allMatch(a -> a.status == Auftrag.Status.VERIFIZIERT)
                && globalePruefungen.stream().allMatch(Auftrag.Pruefung::ok)
                ? Phase.FERTIG : Phase.FEHLGESCHLAGEN;
    }

    /** Gibt die Laufsperre erst frei, wenn auch unterbrochene Worker wirklich beendet sind. */
    static boolean beendeExecutor(ExecutorService executor, long timeoutMs, Runnable abbrechen) {
        boolean interrupted = false;
        boolean regular = false;
        try {
            try {
                regular = executor.awaitTermination(timeoutMs, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                interrupted = true;
            }
            if (!regular) {
                executor.shutdownNow();
                abbrechen.run();
                while (!executor.isTerminated()) {
                    try {
                        executor.awaitTermination(1, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        interrupted = true;
                    }
                }
            }
            return regular;
        } finally {
            if (interrupted) Thread.currentThread().interrupt();
        }
    }

    static String webZiel(int port, String explicitUrl) {
        URI uri = URI.create(explicitUrl == null ? "http://127.0.0.1:" + port + "/" : explicitUrl.trim());
        if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null
                || uri.getFragment() != null) {
            throw new IllegalArgumentException("Webziel muss eine HTTP(S)-Basis-URL ohne Zugangsdaten/Query sein");
        }
        String value = uri.toString();
        return value.endsWith("/") ? value : value + "/";
    }

    private void pruefeExternesWebziel() throws Exception {
        Map<String, String> expected;
        try (Connection c = DriverManager.getConnection(dbUrl, dbUser, dbPass)) {
            expected = databaseIdentity(c);
        }
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        URI base = URI.create(WebHelfer.basisUrl);
        String token = null;
        try {
            var login = client.send(HttpRequest.newBuilder(base.resolve("api/anmeldung"))
                    .timeout(Duration.ofSeconds(15)).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(Json.write(Map.of(
                            "kennung", webAdminKennung, "passwort", webAdminPasswort)))).build(),
                    HttpResponse.BodyHandlers.ofString());
            if (login.statusCode() != 200) throw new IllegalStateException(
                    "Webziel-Prüfung: Admin-Anmeldung fehlgeschlagen (HTTP " + login.statusCode() + ")");
            Object parsed = Json.parse(login.body());
            if (!(parsed instanceof Map<?, ?> object) || !(object.get("token") instanceof String value)) {
                throw new IllegalStateException("Webziel-Prüfung: Anmeldetoken fehlt");
            }
            token = value;
            var identity = client.send(HttpRequest.newBuilder(base.resolve("api/system/db-identity"))
                    .timeout(Duration.ofSeconds(15)).header("Authorization", "Bearer " + token).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (identity.statusCode() != 200) throw new IllegalStateException(
                    "Webziel-Prüfung: DB-Identitätsendpunkt fehlt/ist unzugänglich (HTTP "
                            + identity.statusCode() + "); kein mutierender Testlauf");
            pruefeDatabaseIdentity(expected, Json.parse(identity.body()));
        } finally {
            if (token != null) {
                try {
                    client.send(HttpRequest.newBuilder(base.resolve("api/abmeldung"))
                            .timeout(Duration.ofSeconds(5)).header("Authorization", "Bearer " + token)
                            .POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.discarding());
                } catch (Exception ignored) { /* kurzlebige Prüfsitzung läuft sonst regulär ab */ }
            }
        }
    }

    static Map<String, String> databaseIdentity(Connection c) throws Exception {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(
                "SELECT SYS_CONTEXT('USERENV','DB_NAME'), SYS_CONTEXT('USERENV','CON_NAME'), "
                        + "SYS_CONTEXT('USERENV','CURRENT_SCHEMA'), SYS_CONTEXT('USERENV','SERVICE_NAME') FROM DUAL")) {
            if (!rs.next()) throw new IllegalStateException("Datenbankidentität fehlt");
            return Map.of("databaseName", rs.getString(1), "containerName", rs.getString(2),
                    "schema", rs.getString(3), "serviceName", rs.getString(4));
        }
    }

    static void pruefeDatabaseIdentity(Map<String, String> expected, Object actual) {
        if (!(actual instanceof Map<?, ?> map) || !expected.equals(map)) {
            throw new IllegalStateException("Webziel und SQL-Verifikation verwenden unterschiedliche Datenbankidentitäten");
        }
    }

    private void globalerKonsistenzcheck() {
        long erwartete = kategorieLetzter == Kategorie.STRESS ? (long) Math.ceil(auftraege.size() / 2.0)
                : "WEB".equals(modusLetzter) ? auftraege.stream().filter(a -> Boolean.TRUE.equals(
                        a.kontext.get("vorgangErstellt"))).count()
                : auftraege.stream().filter(a -> a.bgId != null).count();
        uebernehmeGlobalePruefungen(GlobaleVerifikation.pruefe(
                () -> DriverManager.getConnection(dbUrl, dbUser, dbPass), erwartete, auftraege));
    }

    void uebernehmeGlobalePruefungen(List<Auftrag.Pruefung> pruefungen) {
        globalePruefungen = List.copyOf(pruefungen);
        boolean fehler = pruefungen.stream().anyMatch(p -> !p.ok());
        long anzahlFehler = pruefungen.stream().filter(p -> !p.ok()).count();
        Auftrag.Pruefung laufPruefung = new Auftrag.Pruefung("Globale Laufverifikation", "0 Fehler",
                anzahlFehler + " Fehler", !fehler, "Einzelheiten stehen in den globalen Laufprüfungen");
        for (Auftrag auftrag : auftraege) {
            // Ein Laufresultat je Auftrag; keine quadratische Vervielfachung
            // der Einzelprüfungen bei großen Fehlerwellen.
            auftrag.ergaenzePruefung(laufPruefung);
            if (fehler && auftrag.status == Auftrag.Status.VERIFIZIERT) {
                auftrag.status = Auftrag.Status.VERIFIKATION_FEHLGESCHLAGEN;
            }
        }
        for (Auftrag.Pruefung pruefung : pruefungen) {
            if (log != null) log.ereignis("VERIFIKATION", "", pruefung.toString());
        }
        if (fehler) {
            phase = Phase.FEHLGESCHLAGEN;
            letzteFehlermeldung = "Globale Datenbank-Verifikation fehlgeschlagen: " + pruefungen.stream()
                    .filter(p -> !p.ok()).map(p -> p.beschreibung() + " (" + p.ist() + ")")
                    .collect(java.util.stream.Collectors.joining("; "));
            if (log != null) log.fehler("VERIFIKATION", letzteFehlermeldung);
        }
    }

    private String zusammenfassungsText() {
        int fehler = (int) auftraege.stream().filter(a -> a.status == Auftrag.Status.FEHLGESCHLAGEN).count();
        int verifiziert = (int) auftraege.stream().filter(a -> a.status == Auftrag.Status.VERIFIZIERT).count();
        int verifikationFehler = (int) auftraege.stream()
                .filter(a -> a.status == Auftrag.Status.VERIFIKATION_FEHLGESCHLAGEN).count();
        BigDecimal summe = auftraege.stream()
                .filter(a -> a.status == Auftrag.Status.VERIFIZIERT)
                .map(Auftrag::pruefungen).flatMap(List::stream)
                .filter(p -> p.beschreibung().startsWith("Auszahlungsbetrag"))
                .map(p -> new BigDecimal(p.ist()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        StringBuilder sb = new StringBuilder("testsweet — Laufzusammenfassung\n");
        sb.append("Zeitraum        : ").append(laufStart).append(" bis ").append(laufEnde).append('\n');
        sb.append("Dauer           : ").append(laufdauerSekunden()).append(" s\n");
        sb.append("Datenbank       : ").append(dbUrl).append('\n');
        sb.append("Modus           : ").append("WEB".equals(modusLetzter)
                ? "Weboberfläche (Selenium)" : "Fachschicht").append('\n');
        sb.append("Kategorie       : ").append(kategorieLetzter).append('\n');
        sb.append("Mitarbeiter     : ").append("WEB".equals(modusLetzter)
                ? webMitarbeiter.size() : mitarbeiter.size()).append('\n');
        sb.append("Aufträge        : ").append(auftraege.size()).append('\n');
        sb.append("fehlgeschlagen  : ").append(fehler).append('\n');
        sb.append("verifiziert     : ").append(verifiziert).append('\n');
        sb.append("Verifik.-fehler : ").append(verifikationFehler).append('\n');
        sb.append("Summe verifizierter Auszahlungen: ").append(summe.toPlainString()).append(" EUR\n");
        sb.append("Lauf erfolgreich: ").append(laufErfolgreich()).append('\n');
        sb.append("Globale Prüfungen: ").append(globalePruefungen).append('\n');
        sb.append("Abgebrochen     : ").append(abgebrochen).append('\n');
        return sb.toString();
    }

    private void schliesseRessourcen() {
        if (pool != null) {
            pool.close();
            pool = null;
        }
    }

    // ------------------------------------------------------------------
    // Aufrufe aus den Mitarbeiter-Threads
    // ------------------------------------------------------------------

    /** Nächsten offenen Auftrag atomar entnehmen (Pool-Prinzip); null = leer.
     *  STRESS-Phasenbarriere: Aufträge hinter dem Phasenende (Nachbearbeitungen)
     *  werden erst ausgegeben, wenn alle Anlagen der Welle 1 abgeschlossen sind —
     *  gewartet wird im 250-ms-Takt auf dem CountdownLatch stattBusy-Waiting. */
    Auftrag nimmAuftrag() {
        while (true) {
            int index = naechsterAuftrag.get();
            if (index >= auftraege.size()) {
                return null;
            }
            CountDownLatch barriere = stressPhase1Latch;
            if (index >= stressPhase1Ende && barriere != null && barriere.getCount() > 0) {
                if (abgebrochen) {
                    return null;
                }
                try {
                    barriere.await(250, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return null;
                }
                continue;
            }
            if (naechsterAuftrag.compareAndSet(index, index + 1)) {
                return auftraege.get(index);
            }
        }
    }

    /** Setzt die Phasenbarriere für STRESS-Läufe (halbe Liste = Welle 1). */
    private void initialisiereStressBarriere() {
        if (kategorieLetzter == Kategorie.STRESS && auftraege.size() >= 2) {
            stressPhase1Ende = auftraege.size() / 2;
            stressPhase1Latch = new CountDownLatch(stressPhase1Ende);
        } else {
            stressPhase1Ende = -1;
            stressPhase1Latch = null;
        }
    }

    /** Meldet den Abschluss eines Spiel-Auftrags (Hook für die STRESS-Barriere). */
    void auftragSpielenBeendet(Auftrag auftrag) {
        CountDownLatch barriere = stressPhase1Latch;
        if (barriere != null && auftrag.id <= stressPhase1Ende) {
            barriere.countDown();
        }
    }

    String zufallsKollege(String ausser) {
        List<Mitarbeiter> liste = mitarbeiter;
        if (liste.size() <= 1) {
            return "sb-sim-ersatz"; // Einzelgänger: fester Ersatzkollege für Vier-Augen
        }
        String kollege;
        do {
            kollege = liste.get(ThreadLocalRandom.current().nextInt(liste.size())).kuerzel;
        } while (kollege.equals(ausser));
        return kollege;
    }

    /** Logbasis: immer testsweet/logs — egal ob vom Projektroot oder aus testsweet gestartet. */
    private java.nio.file.Path PathLogBasis() {
        java.nio.file.Path arbeitsverzeichnis = java.nio.file.Path.of(System.getProperty("user.dir"));
        if (arbeitsverzeichnis.getFileName() != null
                && arbeitsverzeichnis.getFileName().toString().equalsIgnoreCase("testsweet")) {
            return arbeitsverzeichnis.resolve("logs");
        }
        return arbeitsverzeichnis.resolve("testsweet").resolve("logs");
    }
}
