package de.allgemein.testsweet;

import de.allgemein.lmg.config.LmgConfig;
import de.allgemein.lmg.calc.Leistungsberechnung;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
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
 * optional eigene frühere Läufe aufräumen → Auftragstopf erzeugen → N Mitarbeiter-Threads parallel
 * arbeiten den Pool ab → wenn alle fertig sind, verifiziert der {@link Verifikator}
 * die Erwartungshaltung jedes Auftrags direkt gegen die Datenbank. Alles wird über
 * den {@link SweetLog} protokolliert; die Auswertung der Logfiles erfolgt bewusst
 * manuell gemeinsam mit dem Nutzer.
 */
public final class Simulator {

    /** Testfallkategorie des Laufs: positive Standardszenarien, abgewehrte
     *  Fachfehler (NEGATIV), belegte echte Fälle (ECHT), mit einem externen
     *  Online-Rechner abgeglichene Fälle (RECHNER) oder schlanker Massenbetrieb
     *  für den Lasttest. */
    public enum Kategorie { POSITIV, NEGATIV, ECHT, RECHNER, LAST, STRESS }

    /** Zulässige Auftrags-Obergrenze je Kategorie: Der Testfallbestand ist bewusst
     *  begrenzt — 500 Positiv- und 500 Negativ-Kombinationen (kuratierte Arten +
     *  Kombinationsgenerator), die 5 belegten echten Fälle, die 12 extern
     *  abgeglichenen Rechner-Fälle, 1200 schlanke Lastdurchläufe und 3000
     *  Stress-User (je User zwei Wellen = 6000 Transaktionsblöcke). */
    public static int auftraegeLimit(Kategorie kategorie) {
        return switch (kategorie) {
            case POSITIV, NEGATIV -> 500;
            case ECHT -> 5;
            case RECHNER -> 12;
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
    /** Fachschicht-Identitäten sind Testdaten, keine angemeldeten Benutzerkonten. */
    private volatile List<String> fachFreigabeKennungen = List.of();
    private volatile LaufKonfiguration konfiguration;
    private volatile SweetLog log;
    private volatile List<Auftrag.Pruefung> globalePruefungen = List.of();
    private volatile SimpleConnectionPool pool;
    private volatile SozialLeistungsService service;
    private volatile TxManager fachTxManager;
    private final java.util.concurrent.ConcurrentHashMap<Leistungsberechnung.KduParameter, SozialLeistungsService> fachServices = new java.util.concurrent.ConcurrentHashMap<>();
    private volatile boolean abgebrochen;
    private volatile LocalDateTime laufStart;
    private volatile LocalDateTime laufEnde;
    private volatile Instant laufStartInstant;
    private volatile Instant laufEndeInstant;
    /** Monotone Stoppuhr (System.nanoTime) — unabhängig von Wanduhr-Verstellungen;
     *  laufStart/laufEnde bleiben als Wanduhrzeit für die Protokolle erhalten. */
    private volatile long laufStartNano;
    private volatile long laufEndeNano;
    private volatile RunDataScope laufDaten = RunDataScope.create();
    /** Ausschließlich in dieser Instanz erzeugte Kennungen; fremde Logs sind keine Besitzbelege. */
    private final List<RunDataScope> eigeneFruehereLaeufe = new ArrayList<>();
    private final AtomicInteger naechsterAuftrag = new AtomicInteger();
    private volatile String letzteFehlermeldung;
    /** true, wenn der Lauf an einem DB-Verbindungsproblem scheiterte (für den Klartext-Dialog). */
    private volatile boolean dbNichtErreichbar;
    private volatile String letztesLogVerzeichnis;
    private volatile String letztesPdf;
    private volatile String letztesMd;
    private final List<String> nachweisFehler = new java.util.concurrent.CopyOnWriteArrayList<>();
    private volatile boolean reportsComplete;
    private volatile Map<String, Object> buildEvidence = Map.of();
    private volatile long letzterZwischenstandNano;
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
    /** Browserbesitz des laufenden Webdurchgangs; die Worker-Liste bleibt für die Anzeige erhalten. */
    private volatile boolean webCleanupPending;
    private final Runnable browserCleanup;
    private final java.util.concurrent.ConcurrentHashMap<String, String> webKonten =
            new java.util.concurrent.ConcurrentHashMap<>();
    /** Administrationskonto für Rollenwechsel-Abläufe (Storno freigegebener Zahlungen). */
    private volatile String webAdminKennung = "admin-01";
    private volatile String webAdminPasswort = "admin";
    private ConnectionPool webPool;
    private TaskController webTasks;
    private LmgServer webServer;
    private volatile ExecutorService laufExecutor;
    @FunctionalInterface interface ZustandsLeser { Fachzustand lese(Auftrag auftrag) throws Exception; }
    private final ZustandsLeser zustandsLeser;

    public Simulator() {
        this(null);
    }

    Simulator(ZustandsLeser zustandsLeser) {
        this(zustandsLeser, WebMitarbeiter::raeumeVerwaisteFensterAuf);
    }

    Simulator(ZustandsLeser zustandsLeser, Runnable browserCleanup) {
        this.zustandsLeser = zustandsLeser;
        this.browserCleanup = java.util.Objects.requireNonNull(browserCleanup);
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

    public boolean dbNichtErreichbar() {
        return dbNichtErreichbar;
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
        if (reportsComplete && letztesPdf != null && Files.isRegularFile(Path.of(letztesPdf))) {
            return letztesPdf;
        }
        if (laufStart != null) return null;
        return neuesteDatei("testdokumentation.pdf");
    }

    /** Maschinenlesbare Markdown-Fassung des letzten Laufs (lazy wie das PDF). */
    public String letztesMd() {
        if (reportsComplete && letztesMd != null && Files.isRegularFile(Path.of(letztesMd))) {
            return letztesMd;
        }
        if (laufStart != null) return null;
        return neuesteDatei("testdokumentation.md");
    }

    /** Nur der neueste begonnene Lauf ist aktuell, auch wenn dessen Export fehlt. */
    private String neuesteDatei(String dateiname) {
        try {
            Path logs = PathLogBasis();
            if (!Files.isDirectory(logs)) return null;
            Path latest = null;
            RunChronology.Start latestStart = null;
            List<Path> candidates = new ArrayList<>();
            try (var runs = Files.list(logs)) {
                for (Path run : runs.filter(p -> Files.isDirectory(p)
                        && p.getFileName().toString().startsWith("run-")).toList()) {
                    candidates.add(run);
                    var start = RunChronology.start(run);
                    if (latest == null || start.earliest().isAfter(latestStart.earliest())) { latest = run; latestStart = start; }
                }
            }
            if (latest == null || !latestStart.explicitOffset()) return null;
            for (Path run : candidates) if (!run.equals(latest)
                    && !RunChronology.start(run).latest().isBefore(latestStart.earliest())) return null;
            if (latest == null || !Files.isRegularFile(latest.resolve("run-state.json"))) return null;
            Object parsed = Json.parse(Files.readString(latest.resolve("run-state.json")));
            if (!(parsed instanceof Map<?, ?> state) || !Boolean.TRUE.equals(state.get("reportsComplete"))
                    || !(state.get("reports") instanceof List<?> reports)) return null;
            String suffix = dateiname.endsWith(".pdf") ? ".pdf" : ".md";
            for (Object entry : reports) {
                if (!(entry instanceof Map<?, ?> report) || !(report.get("path") instanceof String relative)
                        || !relative.endsWith(suffix)) continue;
                Path file = latest.resolve(relative).normalize();
                if (file.getParent().equals(latest) && Files.isRegularFile(file)
                        && sha256(file).equals(report.get("sha256"))) return file.toString();
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
        return laufdauerMillis() / 1000;
    }

    public long laufdauerMillis() {
        long von = laufStartNano;
        if (von == 0) {
            return 0;
        }
        long bis = laufEndeNano != 0 ? laufEndeNano : System.nanoTime();
        return Duration.ofNanos(Math.max(0, bis - von)).toMillis();
    }

    public String laufDatenPraefix() { return laufDaten.prefix(); }

    SozialLeistungsService service() {
        return service;
    }

    /** Örtliche Parameter eines Testfalls sind an eine eigene unveränderliche Serviceinstanz gebunden. */
    SozialLeistungsService service(Auftrag auftrag) {
        var parameters = auftrag.rechenbloecke.stream().map(Rechenblock::kduParameter).distinct().toList();
        if (parameters.size() > 1) throw new IllegalStateException("KdU-Konfiguration darf innerhalb eines Auftrags nicht wechseln");
        var parameter = parameters.isEmpty() ? Leistungsberechnung.KduParameter.AUS : parameters.getFirst();
        if (parameter.equals(Leistungsberechnung.KduParameter.AUS)) return service;
        if (fachTxManager == null) throw new IllegalStateException("Fach-Service für örtliche KdU-Parameter fehlt");
        return fachServices.computeIfAbsent(parameter, p -> new SozialLeistungsService(fachTxManager, p, null));
    }

    boolean abgebrochen() {
        return abgebrochen;
    }

    public List<String> nachweisFehler() {
        List<String> result = new ArrayList<>(nachweisFehler);
        SweetLog current = log;
        if (current != null && current.schreibfehler() != null && result.stream().noneMatch(s -> s.startsWith("Protokoll")))
            result.add("Protokoll unvollständig: " + current.schreibfehler().getMessage());
        return List.copyOf(result);
    }

    public boolean nachweiseVollstaendig() { return reportsComplete && nachweisFehler().isEmpty(); }

    Fachzustand sichereFachzustand(Auftrag auftrag) throws Exception {
        if (zustandsLeser != null) return zustandsLeser.lese(auftrag);
        ConnectionPool verbindungen = pruefpool();
        Connection connection = verbindungen.getConnection();
        try {
            return Fachzustand.lese(connection, auftrag.aktenzeichen);
        } finally {
            verbindungen.release(connection);
        }
    }

    /**
     * Liest den bereits committeten Fachzustand vor dem nächsten mutierenden Worker-Schritt.
     * Alle Erwartungen teilen einen Oracle-Lesesnapshot; Sollwerte kommen aus dem Szenario,
     * Istwerte ausschließlich aus Tabellen, nicht aus der vorherigen Serviceantwort.
     */
    void pruefeZwischenstand(Auftrag auftrag, List<Erwartung> erwartungen,
            Map<String, Object> kontext) throws Exception {
        ConnectionPool verbindungen = pruefpool();
        Connection connection = verbindungen.getConnection();
        try {
            if (!connection.getAutoCommit()) connection.rollback();
            connection.setAutoCommit(false);
            try (var statement = connection.createStatement()) {
                statement.execute("SET TRANSACTION READ ONLY");
            }
            var verifier = new Verifikator(dbUrl, dbUser, dbPass);
            boolean ok = true;
            for (Erwartung erwartung : erwartungen) {
                var pruefung = verifier.pruefe(connection, kontext, erwartung);
                auftrag.ergaenzePruefung(pruefung);
                if (log != null) log.pruefung(auftrag, pruefung);
                ok &= pruefung.ok();
            }
            if (!ok) throw new IllegalStateException("SQL-Zwischenstandsprüfung fehlgeschlagen; keine weitere Fachmutation");
        } finally {
            try { connection.rollback(); }
            finally { verbindungen.release(connection); }
        }
    }

    private ConnectionPool pruefpool() {
        ConnectionPool verbindungen = pool != null ? pool : webPool;
        if (verbindungen == null) throw new IllegalStateException("Kein Prüfverbindungspool für den laufenden Simulator verfügbar");
        return verbindungen;
    }

    private int verifiziereAktuellenLauf() throws Exception {
        ConnectionPool verbindungen = pruefpool();
        Connection connection = verbindungen.getConnection();
        try {
            return new Verifikator(dbUrl, dbUser, dbPass).verifiziereAlle(connection, auftraege, log);
        } finally {
            verbindungen.release(connection);
        }
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
            setzeLaufZurueck();
            fuehreGesichertAus(konfig, false);
        } finally {
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
        setzeLaufZurueck();
        Thread laufThread = new Thread(() -> {
            try {
                fuehreGesichertAus(konfig, false);
            } catch (Exception e) {
                // Status, Fehler und terminaler Laufnachweis wurden bereits gesichert.
            } finally {
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
        webMitarbeiter.forEach(WebMitarbeiter::schliesse);
        // Browserfenster-Aufräumen nur nach Web-Läufen: Fachschicht-Läufe haben
        // nie eigene Chrome-Profile, und das Aufräumskript kostet mit offenen
        // Chrome-Prozessen des Nutzers mehrere Sekunden — ein Worker-Absturz
        // (STRESS-Abbruch, Stop-Knopf) darf daran nicht hängen. Der bewusste
        // Cleanup-Knopf (/api/cleanup-prozesse) räumt weiterhin immer.
        if (webCleanupPending) browserCleanup.run();
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
        return andererFreigeber(List.copyOf(webKonten.keySet()), ausser);
    }

    /** Unterscheidet die geprüfte Identitätstrennung von der Anmeldung am Webserver. */
    public String vierAugenPruefumfang() {
        return "WEB".equals(modusLetzter)
                ? "Getrennte am Webserver angemeldete Konten; automatisierter Kontowechsel, kein Nachweis zweier natürlicher Personen"
                : "Fachschicht: verschiedene vorbereitete simulierte Bearbeiterkennungen; keine Anmeldung oder Rollenprüfung und kein Nachweis zweier natürlicher Personen";
    }

    public List<String> fachFreigabeKennungen() { return fachFreigabeKennungen; }

    /** Ein Worker kann beide Rollen nacheinander ausführen; die Workerzahl bleibt unverändert. */
    void initialisiereFachKennungen(int anzahlMitarbeiter) {
        if (anzahlMitarbeiter < 1 || anzahlMitarbeiter > 50)
            throw new IllegalArgumentException("Fachschicht benötigt 1 bis 50 Mitarbeiter");
        fachFreigabeKennungen = java.util.stream.IntStream.rangeClosed(1, Math.max(2, anzahlMitarbeiter))
                .mapToObj(i -> "sb-sim-" + String.format("%02d", i)).toList();
    }

    private static String andererFreigeber(List<String> kennungen, String ausser) {
        if (ausser == null || !kennungen.contains(ausser))
            throw new IllegalStateException("Bearbeiter gehört nicht zu den vorbereiteten Laufidentitäten: " + ausser);
        var andere = kennungen.stream().filter(kennung -> !kennung.equals(ausser)).toList();
        if (andere.isEmpty())
            throw new IllegalStateException("Vier-Augen-Prüfung benötigt eine zweite vorbereitete Kennung");
        return andere.get(ThreadLocalRandom.current().nextInt(andere.size()));
    }

    /** Startet einen Weboberflächen-Lauf synchron im aufrufenden Thread (für CLI). */
    public void laufWebSynchron(LaufKonfiguration konfig) throws Exception {
        pruefeKonfiguration(konfig.kategorie(), konfig.mitarbeiter(), konfig.auftraege());
        if (webUnzulaessig(konfig.kategorie())) throw new IllegalStateException(webNurFachschichtMeldung());
        if (!laufAktiv.compareAndSet(false, true)) {
            throw new IllegalStateException("Es läuft bereits ein Simulator-Lauf");
        }
        try {
            this.konfiguration = konfig;
            setzeLaufZurueck();
            fuehreGesichertAus(konfig, true);
        } finally {
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
        // STRESS/ECHT/RECHNER gibt es nur im Fachschicht-Modus — VOR dem Anlegen
        // von Log/Server/Treiber-Fenster abweisen (siehe auch Guard am Anfang
        // von laufWebAusfuehren)
        if (webUnzulaessig(konfig.kategorie())) {
            laufAktiv.set(false);
            throw new IllegalStateException(webNurFachschichtMeldung());
        }
        this.konfiguration = konfig;
        setzeLaufZurueck();
        Thread laufThread = new Thread(() -> {
            try {
                fuehreGesichertAus(konfig, true);
            } catch (Exception e) {
                // Status, Fehler und terminaler Laufnachweis wurden bereits gesichert.
            } finally {
                laufAktiv.set(false);
            }
        }, "testsweet-web-lauf");
        laufThread.setDaemon(true);
        laufThread.start();
    }

    private static boolean webUnzulaessig(Kategorie kategorie) {
        return kategorie == Kategorie.STRESS || kategorie == Kategorie.ECHT
                || kategorie == Kategorie.RECHNER;
    }

    private static String webNurFachschichtMeldung() {
        return "Stresstest, echte Fälle und Rechner-Fälle sind für den Fachschicht-Modus "
                + "vorgesehen — für die Weboberfläche die Kategorien POSITIV, NEGATIV oder LAST nutzen";
    }

    private void laufWebAusfuehren(LaufKonfiguration konfig) throws Exception {
        // STRESS/ECHT/RECHNER+WEB-Guard als ALLERERSTE Anweisung — bevor irgendetwas
        // angefasst wird (SweetLog, eingebetteter Server, WebDriverManager, Fenster).
        if (webUnzulaessig(konfig.kategorie())) {
            throw new IllegalStateException(webNurFachschichtMeldung());
        }
        webCleanupPending = true;
        phase = Phase.BEREIT;
        auftraege = List.of();
        modusLetzter = "WEB";
        kategorieLetzter = konfig.kategorie();
        laufStartInstant = Instant.now();
        laufStart = LocalDateTime.ofInstant(laufStartInstant, java.time.ZoneId.systemDefault());
        laufStartNano = System.nanoTime();
        laufEnde = null;
        laufEndeNano = 0;
        globalePruefungen = List.of();
        log = new SweetLog(PathLogBasis());
        letztesLogVerzeichnis = log.runVerzeichnis.toString();
        sichereLaufbeginn();
        log.ereignis("SIMULATOR", "", "Web-Lauf gestartet: " + konfig.mitarbeiter()
                + " sichtbare Browserfenster, " + konfig.auftraege() + " Aufträge, Seed "
                + konfig.seed() + ", Oberfläche " + WebHelfer.basisUrl);

        LmgConfig webConfig = LmgConfig.load();
        boolean extern = WebHelfer.expliziteBasisUrl() != null;
        WebHelfer.basisUrl = webZiel(webConfig.port(), WebHelfer.expliziteBasisUrl());
        boolean eingebettet = !extern && starteEingebettetenLmgServer(konfig.mitarbeiter(), webConfig);
        if (!eingebettet && Boolean.getBoolean("sweet.requireEmbedded")) {
            throw new IllegalStateException("Zertifikatstests benötigen einen eigenen frisch gebauten Webserver. Der Testport "
                    + webConfig.port() + " ist belegt oder ein externes Webziel ist konfiguriert. Zertifikatslauf erneut starten; bei Wiederholung Protokoll auswerten.");
        }
        if (!eingebettet) {
            webKonten.clear();
            webKonten.put("sb-01", "sb-01");
            webKonten.put("sb-02", "sb-02");
            webAdminKennung = "admin-01";
            webAdminPasswort = "admin";
            pruefeExternesWebziel(); // vor Cleanup, Browserstart und fachlichen Schreibzugriffen
            webPool = new SimpleConnectionPool(dbUrl, dbUser, dbPass, konfig.mitarbeiter() + 2);
            log.ereignis("SIMULATOR", "", "Externes Webziel und SQL-Datenbank abgeglichen: "
                    + WebHelfer.basisUrl);
        }

        // Fenster eines früheren Laufs schließen — inklusive verwaister Fenster aus
        // gestorbenen Server-Prozessen — bevor neue geöffnet werden
        webMitarbeiter.forEach(WebMitarbeiter::schliesse);
        browserCleanup.run();
        pruefeDatenbank();
        if (konfig.aufraeumen()) {
            phase = Phase.AUFRAEUMEN;
            log.ereignis("SIMULATOR", "", "Eigene frühere Läufe aufgeräumt: "
                    + raeumeAltdatenAuf() + " Vorgänge entfernt; fremde Laufdaten bleiben erhalten");
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
            finishWebCleanup();
        }

        // Verifikation wie in Modus 1: Erwartungshaltung per SQL gegen die Datenbank
        if (abgebrochen) {
            phase = Phase.ABGEBROCHEN;
            log.ereignis("SIMULATOR", "", "Web-Lauf abgebrochen — keine Verifikation");
        } else {
            phase = Phase.VERIFIKATION;
            int verifiziert = verifiziereAktuellenLauf();
            globalerKonsistenzcheck();
            verifiziert = (int) auftraege.stream().filter(a -> a.status == Auftrag.Status.VERIFIZIERT).count();
            aktualisiereAbschlussphase();
            log.ereignis("SIMULATOR", "", "Web-Verifikation abgeschlossen: " + verifiziert
                    + " von " + auftraege.size() + " Aufträgen vollständig verifiziert");
        }

        beendeLauf();
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
        laufStartInstant = Instant.now();
        laufStart = LocalDateTime.ofInstant(laufStartInstant, java.time.ZoneId.systemDefault());
        laufStartNano = System.nanoTime();
        laufEnde = null;
        laufEndeNano = 0;
        globalePruefungen = List.of();
        log = new SweetLog(PathLogBasis());
        letztesLogVerzeichnis = log.runVerzeichnis.toString();
        sichereLaufbeginn();
        log.ereignis("SIMULATOR", "", "Lauf gestartet: " + konfig.mitarbeiter() + " Mitarbeiter, "
                + konfig.auftraege() + " Aufträge, Seed " + konfig.seed()
                + ", Denkzeit " + konfig.denkzeitMs() + " ms, DB " + dbUrl);

        // Datenbank erreichbar?
        pruefeDatenbank();
        // Phase 1: nur zuvor von dieser Simulatorinstanz erzeugte Läufe aufräumen
        if (konfig.aufraeumen()) {
            phase = Phase.AUFRAEUMEN;
            int entfernt = raeumeAltdatenAuf();
            log.ereignis("SIMULATOR", "", "Eigene frühere Läufe aufgeräumt: " + entfernt
                    + " Vorgänge entfernt; fremde Laufdaten bleiben erhalten");
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
        fachTxManager = new TxManager(pool);
        fachServices.clear();
        service = new SozialLeistungsService(fachTxManager);

        initialisiereFachKennungen(konfig.mitarbeiter());
        mitarbeiter = new ArrayList<>();
        for (int i = 1; i <= konfig.mitarbeiter(); i++) {
            mitarbeiter.add(new Mitarbeiter(this, fachFreigabeKennungen.get(i - 1), konfig.denkzeitMs()));
        }
        log.ereignis("SIMULATOR", "", "Freigabeidentitäten " + fachFreigabeKennungen
                + "; " + vierAugenPruefumfang());

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
            int verifiziert = verifiziereAktuellenLauf();
            globalerKonsistenzcheck();
            verifiziert = (int) auftraege.stream().filter(a -> a.status == Auftrag.Status.VERIFIZIERT).count();
            aktualisiereAbschlussphase();
            log.ereignis("SIMULATOR", "", "Verifikation abgeschlossen: " + verifiziert + " von "
                    + gespielt + " erfolgreich gespielten Aufträgen vollständig verifiziert");
        }

        beendeLauf();
    }

    /** Zufälliger Namensraum je Lauf; Modus/Kategorie folgen hinter der Laufkennung. */
    private List<Auftrag> erzeugeTopf(LaufKonfiguration konfig, long seed, boolean web) {
        String praefix = laufDaten.prefix() + switch (konfig.kategorie()) {
            case POSITIV -> web ? "W-" : "";
            case NEGATIV -> web ? "NW-" : "N-";
            case ECHT -> "E-"; // nur Fachschicht (Guard in SweetServer/starteWeb)
            case RECHNER -> "R-"; // nur Fachschicht (Guard in SweetServer/starteWeb)
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
            case ECHT -> new SzenarioBibliothek(seed).erzeugeEcht(konfig.auftraege(), praefix);
            case RECHNER -> new SzenarioBibliothek(seed).erzeugeRechner(konfig.auftraege(), praefix);
            case LAST -> !web
                    ? new SzenarioBibliothek(seed).erzeugeLast(konfig.auftraege(), praefix)
                    : new WebSzenarioBibliothek(seed).erzeugeLast(konfig.auftraege(), praefix);
            case STRESS -> new SzenarioBibliothek(seed).erzeugeStress(konfig.auftraege(), praefix);
        };
    }

    /** Zwei vollständige frische Dateien erzeugen; erst danach als aktuellen Report veröffentlichen. */
    private void schreibeTestdokumentation() {
        reportsComplete = false;
        letztesPdf = null;
        letztesMd = null;
        try {
            boolean massentest = kategorieLetzter == Kategorie.LAST || kategorieLetzter == Kategorie.STRESS;
            Path staging = Files.createTempDirectory(log.runVerzeichnis, ".report-staging-");
            String name = massentest ? "lastreport" : "testdokumentation";
            Path pdf = staging.resolve(name + ".pdf"), md = staging.resolve(name + ".md");
            if (massentest) LastBericht.schreibe(pdf, md, this);
            else {
                Testdokumentation.schreibe(pdf, this);
                MdBericht.schreibe(md, this);
            }
            if (!Files.isRegularFile(pdf) || Files.size(pdf) == 0 || !Files.isRegularFile(md) || Files.size(md) == 0)
                throw new java.io.IOException("PDF oder Markdown wurde nicht vollständig erzeugt");
            Path publishedPdf = log.runVerzeichnis.resolve(pdf.getFileName());
            Path publishedMd = log.runVerzeichnis.resolve(md.getFileName());
            Files.move(pdf, publishedPdf, StandardCopyOption.REPLACE_EXISTING);
            Files.move(md, publishedMd, StandardCopyOption.REPLACE_EXISTING);
            Files.delete(staging);
            letztesPdf = publishedPdf.toString();
            letztesMd = publishedMd.toString();
            reportsComplete = true;
        } catch (Exception e) {
            merkeNachweisfehler("Testdokumentation konnte nicht geschrieben werden: "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private void setzeLaufZurueck() {
        if (!auftraege.isEmpty()) eigeneFruehereLaeufe.add(laufDaten);
        laufDaten = RunDataScope.create();
        abgebrochen = false;
        dbNichtErreichbar = false;
        letzteFehlermeldung = null;
        letztesPdf = null;
        letztesMd = null;
        letztesLogVerzeichnis = null;
        reportsComplete = false;
        nachweisFehler.clear();
        buildEvidence = Map.of();
        fachFreigabeKennungen = List.of();
        letzterZwischenstandNano = 0;
        laufStartInstant = Instant.now();
        laufStart = LocalDateTime.ofInstant(laufStartInstant, java.time.ZoneId.systemDefault());
        laufStartNano = System.nanoTime();
        laufEnde = null;
        laufEndeInstant = null;
        laufEndeNano = 0;
        phase = Phase.BEREIT;
    }

    private void fuehreGesichertAus(LaufKonfiguration konfig, boolean web) throws Exception {
        try {
            if (web) laufWebAusfuehren(konfig); else laufAusfuehren(konfig);
        } catch (Exception e) {
            beendeAbgebrochenenLauf(e);
            throw e;
        } finally {
            // Auch ein Error darf den persistenten Zustand nicht auf einem alten PASS belassen.
            if (laufEnde == null) beendeAbgebrochenenLauf(new IllegalStateException("Lauf unerwartet beendet"));
            schliesseRessourcen();
            schliesseWebServer();
            if (log != null) { log.close(); merkeLogfehler(); log = null; }
        }
    }

    private void sichereLaufbeginn() throws Exception {
        // Der Start steht vor DB-Zugriff und Build-Ermittlung dauerhaft fest.
        schreibeLaufstatus("RUNNING");
        buildEvidence = BuildEvidence.capture(BuildEvidence.currentRoot());
        if (!Boolean.TRUE.equals(buildEvidence.get("sourceArtifactsMatch"))) {
            merkeNachweisfehler("Build-/Quellstand nicht nachgewiesen: geladenes Produkt- oder Testartefakt passt nicht zu den aktuellen Quellen");
        }
        schreibeLaufstatus("RUNNING");
    }

    private void beendeAbgebrochenenLauf(Exception cause) {
        abgebrochen = true;
        phase = Phase.ABGEBROCHEN;
        dbNichtErreichbar = DbErreichbarkeit.istVerbindungsproblem(cause);
        letzteFehlermeldung = cause.getClass().getSimpleName() + ": " + cause.getMessage();
        if (log != null) log.fehler("SIMULATOR", "Lauf abgebrochen: " + letzteFehlermeldung);
        beendeLauf();
    }

    private void beendeLauf() {
        laufEndeInstant = Instant.now();
        laufEnde = LocalDateTime.ofInstant(laufEndeInstant, java.time.ZoneId.systemDefault());
        laufEndeNano = System.nanoTime();
        if (log == null) { merkeNachweisfehler("Laufprotokoll konnte nicht angelegt werden"); return; }
        log.ereignis("SIMULATOR", "", "Lauf beendet in " + laufdauerSekunden() + " s; Logabschluss vor Reportexport");
        log.close(); // Queue, Flush UND Close sind vor einem veröffentlichten Erfolgsurteil bestätigt.
        merkeLogfehler();
        schreibeTestdokumentation();
        try {
            log.zusammenfassung(zusammenfassungsText());
        } catch (Exception e) {
            merkeNachweisfehler("Zusammenfassung konnte nicht geschrieben werden: " + e.getMessage());
            // Bereits erzeugte Reportdateien könnten ein positives Urteil enthalten: nicht ausliefern.
            reportsComplete = false;
            letztesPdf = null;
            letztesMd = null;
        }
        sichereLaufstatus(terminalerLaufstatus());
    }

    private void merkeLogfehler() {
        if (log != null && log.schreibfehler() != null)
            merkeNachweisfehler("Protokoll unvollständig: " + log.schreibfehler().getMessage());
    }

    private void merkeNachweisfehler(String message) {
        if (!nachweisFehler.contains(message)) nachweisFehler.add(message);
        letzteFehlermeldung = message;
        if (phase == Phase.FERTIG) phase = Phase.FEHLGESCHLAGEN;
        System.err.println(message);
    }

    private String terminalerLaufstatus() {
        boolean knownFailure = auftraege.stream().anyMatch(a -> a.status == Auftrag.Status.FEHLGESCHLAGEN
                || a.status == Auftrag.Status.VERIFIKATION_FEHLGESCHLAGEN)
                || globalePruefungen.stream().anyMatch(p -> !p.ok());
        if (knownFailure) return "FAIL";
        if (abgebrochen || phase == Phase.ABGEBROCHEN) return "ABORTED";
        if (!nachweiseVollstaendig() || !laufErfolgreich()) return "UNVERIFIED";
        return "PASS";
    }

    private void sichereLaufstatus(String state) {
        try { schreibeLaufstatus(state); }
        catch (Exception e) {
            merkeNachweisfehler("Laufstatus konnte nicht gespeichert werden: " + e.getMessage());
            reportsComplete = false;
            letztesPdf = null;
            letztesMd = null;
        }
    }

    private synchronized void schreibeLaufstatus(String state) throws Exception {
        if (log == null) throw new java.io.IOException("Kein Laufverzeichnis");
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("schemaVersion", 1);
        status.put("kind", "SIMULATOR_RUN");
        status.put("runId", log.runVerzeichnis.getFileName().toString());
        status.put("state", state);
        status.put("mode", modusLetzter);
        status.put("category", kategorieLetzter.name());
        status.put("fourEyesScope", vierAugenPruefumfang());
        status.put("approvalIdentities", "WEB".equals(modusLetzter)
                ? webKonten.keySet().stream().sorted().toList() : fachFreigabeKennungen);
        status.put("dataNamespace", laufDaten.prefix());
        status.put("durationMs", laufdauerMillis());
        if (kategorieLetzter == Kategorie.LAST || kategorieLetzter == Kategorie.STRESS) {
            status.put("performanceEvidence", "FUNCTIONAL_PARALLEL_RUN");
            status.put("performanceScope", RunMetrics.SCOPE);
            status.put("capacityEvidence", false);
        }
        if (laufStartInstant == null) throw new IllegalStateException("Eindeutiger Laufstart fehlt");
        status.put("startedAt", laufStartInstant.toString());
        status.put("completedAt", laufEndeInstant == null ? null : laufEndeInstant.toString());
        status.put("totalCases", auftraege.isEmpty() ? konfiguration.auftraege()
                * (kategorieLetzter == Kategorie.STRESS ? 2 : 1) : auftraege.size());
        status.put("completedCases", auftraege.stream().filter(a -> a.endeZeit != null).count());
        status.put("verifiedCases", auftraege.stream().filter(a -> a.status == Auftrag.Status.VERIFIZIERT).count());
        status.put("failedCases", auftraege.stream().filter(a -> a.status == Auftrag.Status.FEHLGESCHLAGEN).count());
        status.put("verificationErrors", auftraege.stream().filter(a -> a.status == Auftrag.Status.VERIFIKATION_FEHLGESCHLAGEN).count()
                + globalePruefungen.stream().filter(p -> !p.ok()).count());
        status.put("success", "PASS".equals(state) && laufErfolgreich());
        status.put("reportsComplete", reportsComplete);
        status.put("logComplete", log.vollstaendig());
        status.put("evidenceErrors", nachweisFehler());
        status.put("message", letzteFehlermeldung);
        status.put("buildEvidence", buildEvidence);
        List<Map<String, String>> reports = new ArrayList<>();
        if (reportsComplete) {
            for (String filename : List.of(letztesPdf, letztesMd)) {
                Path file = Path.of(filename);
                reports.add(Map.of("path", file.getFileName().toString(), "sha256", sha256(file)));
            }
        }
        status.put("reports", reports);
        Path pending = log.runVerzeichnis.resolve("run-state.json.tmp");
        Files.writeString(pending, Json.write(status), StandardCharsets.UTF_8);
        try { Files.move(pending, log.runVerzeichnis.resolve("run-state.json"),
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(pending, log.runVerzeichnis.resolve("run-state.json"), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String sha256(Path file) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
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

    /** Bereinigt nur frühere Laufkennungen, die diese Simulatorinstanz selbst erzeugt hat. */
    private int raeumeAltdatenAuf() throws Exception {
        if (eigeneFruehereLaeufe.isEmpty()) return 0;
        try (Connection c = DriverManager.getConnection(dbUrl, dbUser, dbPass)) {
            c.setAutoCommit(false);
            try {
                int entfernt = 0;
                for (RunDataScope scope : eigeneFruehereLaeufe) entfernt += scope.delete(c);
                c.commit();
                eigeneFruehereLaeufe.clear();
                return entfernt;
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        }
    }

    public List<Auftrag.Pruefung> globalePruefungen() { return globalePruefungen; }

    public boolean laufErfolgreich() {
        return phase == Phase.FERTIG && !auftraege.isEmpty() && nachweisFehler().isEmpty()
                && auftraege.stream().allMatch(a -> a.status == Auftrag.Status.VERIFIZIERT)
                && globalePruefungen.stream().allMatch(Auftrag.Pruefung::ok);
    }

    /** Einzelfälle und globale Prüfungen bestimmen gemeinsam den Laufstatus. */
    void aktualisiereAbschlussphase() {
        phase = abgebrochen ? Phase.ABGEBROCHEN
                : !auftraege.isEmpty()
                && nachweisFehler().isEmpty()
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
        long erwartete = auftraege.stream().mapToLong(a -> a.erwarteteVorgaenge).sum();
        uebernehmeGlobalePruefungen(GlobaleVerifikation.pruefe(
                () -> DriverManager.getConnection(dbUrl, dbUser, dbPass), erwartete, auftraege, laufDaten));
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
        sb.append("Dauer           : ").append(laufdauerMillis()).append(" ms\n");
        sb.append("Datennamensraum : ").append(laufDaten.prefix()).append('\n');
        if (kategorieLetzter == Kategorie.LAST || kategorieLetzter == Kategorie.STRESS)
            sb.append("Prüfaussage     : ").append(RunMetrics.SCOPE).append('\n');
        sb.append("Datenbank       : ").append(dbUrl).append('\n');
        sb.append("Modus           : ").append("WEB".equals(modusLetzter)
                ? "Weboberfläche (Selenium)" : "Fachschicht").append('\n');
        sb.append("Kategorie       : ").append(kategorieLetzter).append('\n');
        sb.append("Mitarbeiter     : ").append("WEB".equals(modusLetzter)
                ? webMitarbeiter.size() : mitarbeiter.size()).append('\n');
        sb.append("Vier-Augen-Scope: ").append(vierAugenPruefumfang()).append('\n');
        if (!"WEB".equals(modusLetzter))
            sb.append("Freigabeidentitäten: ").append(fachFreigabeKennungen).append('\n');
        sb.append("Aufträge        : ").append(auftraege.size()).append('\n');
        sb.append("fehlgeschlagen  : ").append(fehler).append('\n');
        sb.append("verifiziert     : ").append(verifiziert).append('\n');
        sb.append("Verifik.-fehler : ").append(verifikationFehler).append('\n');
        sb.append("Summe verifizierter Auszahlungen: ").append(summe.toPlainString()).append(" EUR\n");
        sb.append("Lauf erfolgreich: ").append(laufErfolgreich()).append('\n');
        sb.append("Reportdateien vollständig: ").append(reportsComplete).append('\n');
        sb.append("Nachweisfehler   : ").append(nachweisFehler()).append('\n');
        sb.append("Globale Prüfungen: ").append(globalePruefungen).append('\n');
        sb.append("Abgebrochen     : ").append(abgebrochen).append('\n');
        return sb.toString();
    }

    private void schliesseRessourcen() {
        finishWebCleanup();
        if (pool != null) {
            pool.close();
            pool = null;
        }
        if (webPool != null) {
            webPool.close();
            webPool = null;
        }
    }

    private void finishWebCleanup() {
        if (!webCleanupPending) return;
        webMitarbeiter.forEach(WebMitarbeiter::schliesse);
        browserCleanup.run();
        webCleanupPending = false;
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
            if (abgebrochen) return null;
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
        // Zwischenstände begrenzen die verlorene Fortschrittsanzeige bei Prozessabbruch;
        // gleichzeitige Worker schreiben maximal einmal je Sekunde.
        synchronized (this) {
            long now = System.nanoTime();
            if (log != null && konfiguration != null && now - letzterZwischenstandNano >= TimeUnit.SECONDS.toNanos(1)) {
                letzterZwischenstandNano = now;
                sichereLaufstatus("RUNNING");
            }
        }
    }

    void meldeWorkerAbsturz(Auftrag auftrag, Throwable failure) {
        String message = "Worker abgebrochen: " + failure.getClass().getSimpleName() + ": " + failure.getMessage();
        if (auftrag != null) {
            auftrag.fehlerText = message;
            auftrag.endeZeit = LocalDateTime.now();
            auftrag.endeNano = System.nanoTime();
            auftrag.status = Auftrag.Status.FEHLGESCHLAGEN;
            auftragSpielenBeendet(auftrag);
        } else merkeNachweisfehler(message);
        letzteFehlermeldung = message;
        if (DbErreichbarkeit.istVerbindungsproblem(failure)) dbNichtErreichbar = true;
        if (log != null) log.fehler("WORKER", message);
        stoppe(); // Wartende Stress-Worker verlassen die Phasenbarriere sofort.
    }

    String zufallsKollege(String ausser) {
        return andererFreigeber(fachFreigabeKennungen, ausser);
    }

    /** Logbasis: immer testsweet/logs — egal ob vom Projektroot oder aus testsweet gestartet. */
    java.nio.file.Path logBasis() {
        return PathLogBasis();
    }

    private java.nio.file.Path PathLogBasis() {
        java.nio.file.Path arbeitsverzeichnis = java.nio.file.Path.of(System.getProperty("user.dir"));
        if (arbeitsverzeichnis.getFileName() != null
                && arbeitsverzeichnis.getFileName().toString().equalsIgnoreCase("testsweet")) {
            return arbeitsverzeichnis.resolve("logs");
        }
        return arbeitsverzeichnis.resolve("testsweet").resolve("logs");
    }
}
