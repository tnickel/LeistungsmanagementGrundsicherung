package de.allgemein.testsweet;

import de.allgemein.lmg.config.LmgConfig;
import de.allgemein.lmg.service.LeistungsService;
import de.allgemein.lmg.service.SozialLeistungsService;
import de.allgemein.lmg.service.Systemdiagnose;
import de.allgemein.lmg.web.Benutzerverwaltung;
import de.allgemein.lmg.web.LmgServer;
import de.allgemein.tx.ConnectionPool;
import de.allgemein.tx.SimpleConnectionPool;
import de.allgemein.tx.TaskController;
import de.allgemein.tx.TxManager;
import de.allgemein.tx.saga.SagaEngine;
import de.allgemein.tx.saga.SagaJournal;

import java.net.ServerSocket;
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

    /** Laufparameter aus der GUI. */
    public record LaufKonfiguration(
            int mitarbeiter, int auftraege, long seed, long denkzeitMs, boolean aufraeumen) { }

    public enum Phase { BEREIT, AUFRAEUMEN, LAUF, VERIFIKATION, FERTIG, ABGEBROCHEN }

    public final String dbUrl;
    public final String dbUser;
    public final String dbPass;

    private volatile Phase phase = Phase.BEREIT;
    private volatile List<Auftrag> auftraege = List.of();
    private volatile List<Mitarbeiter> mitarbeiter = List.of();
    private volatile LaufKonfiguration konfiguration;
    private volatile SweetLog log;
    private volatile SimpleConnectionPool pool;
    private volatile SozialLeistungsService service;
    private volatile boolean abgebrochen;
    private volatile LocalDateTime laufStart;
    private volatile LocalDateTime laufEnde;
    private final AtomicInteger naechsterAuftrag = new AtomicInteger();
    private volatile CountDownLatch mitarbeiterLatch;
    private volatile String letzteFehlermeldung;
    private volatile String letztesLogVerzeichnis;
    private volatile String letztesPdf;
    private volatile String letztesMd;
    private volatile String modusLetzter = "FACHSCHICHT";
    private volatile List<WebMitarbeiter> webMitarbeiter = List.of();
    private final java.util.concurrent.ConcurrentHashMap<String, String> webKonten =
            new java.util.concurrent.ConcurrentHashMap<>();
    /** Administrationskonto für Rollenwechsel-Abläufe (Storno freigegebener Zahlungen). */
    private volatile String webAdminKennung = "admin-01";
    private volatile String webAdminPasswort = "admin";
    private ConnectionPool webPool;
    private TaskController webTasks;
    private LmgServer webServer;

    public Simulator() {
        this.dbUrl = umgebung("TX_URL", "jdbc:oracle:thin:@//localhost:1521/FREEPDB1");
        this.dbUser = umgebung("TX_USER", "tx_demo");
        this.dbPass = umgebung("TX_PASS", "tx_demo");
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
        LocalDateTime von = laufStart;
        if (von == null) {
            return 0;
        }
        LocalDateTime bis = laufEnde != null ? laufEnde : LocalDateTime.now();
        return Duration.between(von, bis).toSeconds();
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
        this.konfiguration = konfig;
        this.abgebrochen = false;
        this.letzteFehlermeldung = null;
        laufAusfuehren(konfig);
    }

    /** Startet einen Lauf in einem Hintergrund-Thread; kehrt sofort zurück. */
    public synchronized void starte(LaufKonfiguration konfig) {
        if (phase == Phase.AUFRAEUMEN || phase == Phase.LAUF || phase == Phase.VERIFIKATION) {
            throw new IllegalStateException("Es läuft bereits ein Simulator-Lauf (Phase " + phase + ")");
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
            }
        }, "testsweet-lauf");
        laufThread.setDaemon(true);
        laufThread.start();
    }

    /** Bricht den Lauf ab: Mitarbeiter beenden den aktuellen Auftrag nicht mehr weiter. */
    public void stoppe() {
        abgebrochen = true;
    }

    // ------------------------------------------------------------------
    // Modus 2: Weboberflächen-Lauf (Selenium steuert sichtbare Chrome-Fenster)
    // ------------------------------------------------------------------

    public String letzterModus() {
        return modusLetzter;
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
        this.konfiguration = konfig;
        this.abgebrochen = false;
        this.letzteFehlermeldung = null;
        laufWebAusfuehren(konfig);
    }

    /** Startet einen Weboberflächen-Lauf in einem Hintergrund-Thread. */
    public synchronized void starteWeb(LaufKonfiguration konfig) {
        if (phase == Phase.AUFRAEUMEN || phase == Phase.LAUF || phase == Phase.VERIFIKATION) {
            throw new IllegalStateException("Es läuft bereits ein Simulator-Lauf (Phase " + phase + ")");
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
            }
        }, "testsweet-web-lauf");
        laufThread.setDaemon(true);
        laufThread.start();
    }

    private void laufWebAusfuehren(LaufKonfiguration konfig) throws Exception {
        modusLetzter = "WEB";
        laufStart = LocalDateTime.now();
        laufEnde = null;
        log = new SweetLog(PathLogBasis());
        letztesLogVerzeichnis = log.runVerzeichnis.toString();
        log.ereignis("SIMULATOR", "", "Web-Lauf gestartet: " + konfig.mitarbeiter()
                + " sichtbare Browserfenster, " + konfig.auftraege() + " Aufträge, Seed "
                + konfig.seed() + ", Oberfläche " + WebHelfer.basisUrl);

        // Fenster eines früheren Laufs schließen — inklusive verwaister Fenster aus
        // gestorbenen Server-Prozessen — bevor neue geöffnet werden
        webMitarbeiter.forEach(WebMitarbeiter::schliesse);
        WebMitarbeiter.raeumeVerwaisteFensterAuf();
        pruefeDatenbank();
        if (konfig.aufraeumen()) {
            phase = Phase.AUFRAEUMEN;
            log.ereignis("SIMULATOR", "", "Altdaten aufgeräumt: "
                    + raeumeAltdatenAuf() + " SWEET-Vorgänge entfernt");
        }

        // LMG-Server einbetten (eigene Sim-Konten) oder laufenden externen nutzen
        boolean eingebettet = starteEingebettetenLmgServer(konfig.mitarbeiter());
        if (!eingebettet) {
            webKonten.clear();
            webKonten.put("sb-01", "sb-01");
            webKonten.put("sb-02", "sb-02");
            webAdminKennung = "admin-01";
            webAdminPasswort = "admin";
            log.ereignis("SIMULATOR", "", "Port 8080 ist belegt — es wird der bereits laufende "
                    + "SDN.Leistung-Server genutzt (Standard-Konten sb-01/sb-02, Admin admin-01). "
                    + "Für eigene Sim-Konten den externen Server beenden.");
        }

        WebMitarbeiter.bereiteTreiberVor();
        long seed = konfig.seed() != 0 ? konfig.seed() : ThreadLocalRandom.current().nextLong();
        auftraege = new WebSzenarioBibliothek(seed).erzeuge(konfig.auftraege(),
                SzenarioBibliothek.PREFIX + "W-");
        naechsterAuftrag.set(0);

        webMitarbeiter = new ArrayList<>();
        List<String> kennungen = new ArrayList<>(webKonten.keySet());
        for (int i = 0; i < konfig.mitarbeiter(); i++) {
            String kennung = eingebettet
                    ? String.format("sb-web-%02d", i + 1)
                    : kennungen.get(i % kennungen.size());
            webMitarbeiter.add(new WebMitarbeiter(this, kennung, webPasswort(kennung),
                    i, konfig.mitarbeiter()));
        }
        mitarbeiterLatch = new CountDownLatch(webMitarbeiter.size());
        log.ereignis("SIMULATOR", "", "Chrome-Fenster werden geöffnet und nebeneinander "
                + "angeordnet — die Oberfläche läuft unter " + WebHelfer.basisUrl);

        phase = Phase.LAUF;
        ExecutorService executor = Executors.newFixedThreadPool(konfig.mitarbeiter());
        try {
            for (WebMitarbeiter m : webMitarbeiter) {
                executor.submit(m);
            }
            executor.shutdown();
            boolean fertig = executor.awaitTermination(30, TimeUnit.MINUTES);
            if (!fertig) {
                log.fehler("SIMULATOR", "Laufzeit-Limit (30 min) überschritten — Zwangsabbruch");
                executor.shutdownNow();
                abgebrochen = true;
            }
        } finally {
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
            phase = Phase.FERTIG;
            log.ereignis("SIMULATOR", "", "Web-Verifikation abgeschlossen: " + verifiziert
                    + " von " + auftraege.size() + " Aufträgen vollständig verifiziert");
        }

        laufEnde = LocalDateTime.now();
        schreibeTestdokumentation();
        log.zusammenfassung(zusammenfassungsText());
        log.ereignis("SIMULATOR", "", "Web-Lauf beendet in " + laufdauerSekunden()
                + " s — Browserfenster bleiben zur Ansicht offen (bis zum nächsten Lauf/Stopp).");
        log.close();
        log = null;
    }

    /**
     * Startet den SDN.Leistung-Server im eigenen Prozess (Port 8080) mit eigenen
     * Sim-Konten sb-web-NN — nur wenn der Port frei ist. Rückgabe: true = eingebettet.
     */
    private boolean starteEingebettetenLmgServer(int anzahlMitarbeiter) throws Exception {
        try (ServerSocket test = new ServerSocket(8080)) {
            // Port frei — wir starten selbst
        } catch (Exception belegt) {
            return false;
        }
        StringBuilder konten = new StringBuilder();
        webKonten.clear();
        for (int i = 1; i <= anzahlMitarbeiter; i++) {
            String kennung = String.format("sb-web-%02d", i);
            if (konten.length() > 0) {
                konten.append(';');
            }
            konten.append(kennung).append(":sim:sachbearbeiter");
            webKonten.put(kennung, "sim");
        }
        // Administrationskonto für Rollenwechsel-Abläufe (Storno freigegebener
        // Zahlungen erfordert die Admin-Rolle) — bewusst NICHT im Kollegen-Pool
        konten.append(";web-admin:sim:admin");
        webAdminKennung = "web-admin";
        webAdminPasswort = "sim";
        // Konten über die Konfigurationskaskade (System-Property lmg.benutzer)
        System.setProperty("lmg.benutzer", konten.toString());
        LmgConfig config = LmgConfig.load();
        System.clearProperty("lmg.benutzer");
        Class.forName("oracle.jdbc.OracleDriver");
        webPool = new SimpleConnectionPool(config.url(), config.user(), config.password(),
                Math.max(8, anzahlMitarbeiter + 2));
        TxManager txManager = new TxManager(webPool);
        webTasks = new TaskController(txManager, config.maxTasks(), config.queueCapacity());
        LeistungsService service = new SozialLeistungsService(txManager, config.kduParameter(),
                config.rechtsbehelf());
        SagaEngine sagas = new SagaEngine(txManager, new SagaJournal(webPool));
        Systemdiagnose diagnose = new Systemdiagnose(txManager, webTasks, sagas, Instant.now());
        Benutzerverwaltung benutzerverwaltung = new Benutzerverwaltung(konten.toString(),
                Duration.ofMinutes(config.sessionTimeoutMinuten()));
        webServer = new LmgServer(webTasks, service, diagnose, sagas, benutzerverwaltung,
                config.port());
        webServer.start();
        log.ereignis("SIMULATOR", "", "Eingebetteter SDN.Leistung-Server gestartet auf Port "
                + config.port() + " mit " + anzahlMitarbeiter + " Sim-Konten (sb-web-01…)");
        return true;
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
        modusLetzter = "FACHSCHICHT";
        laufStart = LocalDateTime.now();
        laufEnde = null;
        log = new SweetLog(PathLogBasis());
        letztesLogVerzeichnis = log.runVerzeichnis.toString();
        log.ereignis("SIMULATOR", "", "Lauf gestartet: " + konfig.mitarbeiter() + " Mitarbeiter, "
                + konfig.auftraege() + " Aufträge, Seed " + konfig.seed()
                + ", Denkzeit " + konfig.denkzeitMs() + " ms, DB " + dbUrl);

        // Datenbank erreichbar?
        pruefeDatenbank();

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
        auftraege = new SzenarioBibliothek(seed).erzeuge(konfig.auftraege());
        naechsterAuftrag.set(0);

        pool = new SimpleConnectionPool(dbUrl, dbUser, dbPass, konfig.mitarbeiter() + 2);
        service = new SozialLeistungsService(new TxManager(pool));

        mitarbeiter = new ArrayList<>();
        for (int i = 1; i <= konfig.mitarbeiter(); i++) {
            mitarbeiter.add(new Mitarbeiter(this, "sb-sim-" + String.format("%02d", i), konfig.denkzeitMs()));
        }
        mitarbeiterLatch = new CountDownLatch(mitarbeiter.size());

        phase = Phase.LAUF;
        ExecutorService executor = Executors.newFixedThreadPool(konfig.mitarbeiter());
        try {
            for (Mitarbeiter m : mitarbeiter) {
                executor.submit(m);
            }
            executor.shutdown();
            boolean fertig = executor.awaitTermination(30, TimeUnit.MINUTES);
            if (!fertig) {
                log.fehler("SIMULATOR", "Laufzeit-Limit (30 min) überschritten — Zwangsabbruch");
                executor.shutdownNow();
                abgebrochen = true;
            }
        } finally {
            schliesseRessourcen();
        }

        // Phase 3: Verifikation (nur bei regulärem Ende)
        if (abgebrochen) {
            phase = Phase.ABGEBROCHEN;
            log.ereignis("SIMULATOR", "", "Lauf abgebrochen — keine Verifikation, Datenbestand kann inkonsistent sein");
        } else {
            phase = Phase.VERIFIKATION;
            log.ereignis("SIMULATOR", "", "Alle Mitarbeiter fertig — verifiziere Erwartungshaltung gegen die Datenbank …");
            int verifiziert = new Verifikator(dbUrl, dbUser, dbPass)
                    .verifiziereAlle(auftraege, log);
            int gespielt = (int) auftraege.stream()
                    .filter(a -> a.status == Auftrag.Status.FERTIG_GESPIELT).count();
            globalerKonsistenzcheck();
            phase = Phase.FERTIG;
            log.ereignis("SIMULATOR", "", "Verifikation abgeschlossen: " + verifiziert + " von "
                    + gespielt + " erfolgreich gespielten Aufträgen vollständig verifiziert");
        }

        laufEnde = LocalDateTime.now();
        schreibeTestdokumentation();
        log.zusammenfassung(zusammenfassungsText());
        log.ereignis("SIMULATOR", "", "Lauf beendet in " + laufdauerSekunden() + " s — Logdateien: "
                + log.runVerzeichnis);
        log.close();
        log = null;
    }

    /** PDF-Testdokumentation (Zusammenfassung + Einzelfälle) — Fehler brechen den Lauf nicht. */
    private void schreibeTestdokumentation() {
        try {
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

    /** Globale Zusatzprüfung: Anzahl SWEET-Vorgänge passt zu den Aufträgen mit bgId. */
    private void globalerKonsistenzcheck() {
        try (Connection c = DriverManager.getConnection(dbUrl, dbUser, dbPass);
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM BEDARFSGEMEINSCHAFT "
                     + "WHERE AKTENZEICHEN LIKE '" + SzenarioBibliothek.PREFIX + "%'")) {
            rs.next();
            long vorhandene = rs.getLong(1);
            long erwartete = auftraege.stream().filter(a -> a.bgId != null).count();
            String text = "Globaler Konsistenzcheck: " + vorhandene + " SWEET-Vorgänge in der DB, "
                    + erwartete + " erwartet";
            log.ereignis("VERIFIKATION", "", text + (vorhandene == erwartete ? " — OK" : " — ABWEICHUNG!"));
            if (vorhandene != erwartete) {
                log.fehler("VERIFIKATION", text);
            }
        } catch (Exception e) {
            log.fehler("VERIFIKATION", "Globaler Konsistenzcheck gescheitert: " + e.getMessage());
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
        sb.append("Mitarbeiter     : ").append("WEB".equals(modusLetzter)
                ? webMitarbeiter.size() : mitarbeiter.size()).append('\n');
        sb.append("Aufträge        : ").append(auftraege.size()).append('\n');
        sb.append("fehlgeschlagen  : ").append(fehler).append('\n');
        sb.append("verifiziert     : ").append(verifiziert).append('\n');
        sb.append("Verifik.-fehler : ").append(verifikationFehler).append('\n');
        sb.append("Summe verifizierter Auszahlungen: ").append(summe.toPlainString()).append(" EUR\n");
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

    /** Nächsten offenen Auftrag atomar entnehmen (Pool-Prinzip); null = leer. */
    Auftrag nimmAuftrag() {
        while (true) {
            int index = naechsterAuftrag.get();
            if (index >= auftraege.size()) {
                return null;
            }
            if (naechsterAuftrag.compareAndSet(index, index + 1)) {
                return auftraege.get(index);
            }
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

    void mitarbeiterBeendet(Mitarbeiter m) {
        CountDownLatch latch = mitarbeiterLatch;
        if (latch != null) {
            latch.countDown();
        }
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
