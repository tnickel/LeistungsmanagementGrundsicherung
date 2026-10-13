package de.allgemein.testsweet;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.Point;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Ein simulierter Sachbearbeiter des Weboberflächen-Modus: arbeitet in einem
 * eigenen, SICHTBAREN Chrome-Fenster, das über Selenium die echte SDN.Leistung-
 * Oberfläche bedient. Die Fenster werden in einem Raster nebeneinander angeordnet,
 * sodass man allen Mitarbeitern gleichzeitig bei der Arbeit zusehen kann.
 * Nach dem Lauf bleiben die Fenster offen (bis zum nächsten Lauf oder Stopp),
 * jeder Schritt wird zusätzlich als Screenshot im Laufprotokoll festgehalten.
 */
final class WebMitarbeiter implements Runnable {

    final String kuerzel;
    final String passwort;
    private final Simulator simulator;
    private final int fensterIndex;
    private final int fensterAnzahl;
    private final long denkzeitMs;

    volatile WebDriver treiber;
    volatile String aktuellerAuftrag;
    volatile int erledigt;
    volatile boolean aktiv;

    WebMitarbeiter(Simulator simulator, String kuerzel, String passwort,
            int fensterIndex, int fensterAnzahl) {
        this(simulator, kuerzel, passwort, fensterIndex, fensterAnzahl, 0);
    }

    WebMitarbeiter(Simulator simulator, String kuerzel, String passwort,
            int fensterIndex, int fensterAnzahl, long denkzeitMs) {
        this.simulator = simulator;
        this.kuerzel = kuerzel;
        this.passwort = passwort;
        this.fensterIndex = fensterIndex;
        this.fensterAnzahl = fensterAnzahl;
        this.denkzeitMs = denkzeitMs;
    }

    @Override
    public void run() {
        aktiv = true;
        try {
            treiber = erzeugeFenster();
            while (!simulator.abgebrochen()) {
                Auftrag auftrag = simulator.nimmAuftrag();
                if (auftrag == null) {
                    break;
                }
                spieleAuftrag(auftrag);
                if (treiber == null) {
                    // Neustart gescheitert: Dieser Worker nimmt keine weiteren
                    // Aufträge mehr an (keine NullPointerException-Kette);
                    // der Topf versorgt den Rest mit den übrigen Fenstern.
                    simulator.log().fehler("WEB " + kuerzel,
                            "Treiber-Neustart gescheitert — Fenster wird zurückgezogen");
                    break;
                }
            }
        } catch (Exception e) {
            simulator.log().fehler("WEB " + kuerzel, "Fenster konnte nicht gestartet werden: "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
        } finally {
            aktiv = false;
            if (treiber == null) {
                BrowserProzesse.loescheProfil(fensterProfil);
                fensterProfil = null;
            }
        }
    }

    /** true, wenn dieser Worker keinen lauffähigen Treiber mehr hat. */
    boolean treiberTot() {
        return treiber == null;
    }

    /** Profil-Verzeichnis des aktuellen Fensters — wird beim Schließen gelöscht. */
    private java.nio.file.Path fensterProfil;

    /** Erzeugt das sichtbare Chrome-Fenster und platziert es im Raster. */
    private WebDriver erzeugeFenster() throws IOException {
        ChromeOptions optionen = new ChromeOptions();
        fensterProfil = BrowserProzesse.neuesProfil();
        optionen.addArguments("--user-data-dir=" + fensterProfil);
        // Skalierungsfaktor verkleinert Schrift und Layout dauerhaft, damit die
        // Seiteninhalte auch in den kleinen Rasterfenstern vollständig Platz finden
        optionen.addArguments("--lang=de", "--window-size=900,700",
                "--force-device-scale-factor=0.8");
        optionen.setExperimentalOption("excludeSwitches", List.of("enable-automation"));
        optionen.setExperimentalOption("useAutomationExtension", false);
        WebDriver d = new ChromeDriver(optionen);
        try {
        // Bildschirm-Raster: Spalten × Zeilen, Aufgabe unten 48 px für die Taskleiste
        java.awt.Dimension bild = java.awt.Toolkit.getDefaultToolkit().getScreenSize();
        int spalten = (int) Math.min(Math.ceil(Math.sqrt(fensterAnzahl)),
                Math.max(1, bild.width / 440));
        int zeilen = (int) Math.ceil((double) fensterAnzahl / spalten);
        int breite = Math.max(320, bild.width / spalten);
        int hoehe = Math.max(240, (bild.height - 48) / zeilen);
        int spalte = fensterIndex % spalten;
        int reihe = fensterIndex / spalten;
        d.manage().window().setPosition(new Point(spalte * breite, reihe * hoehe));
        d.manage().window().setSize(new Dimension(breite, hoehe));
        return d;
        } catch (RuntimeException failure) {
            try { d.quit(); } catch (RuntimeException cleanup) { failure.addSuppressed(cleanup); }
            BrowserProzesse.loescheProfil(fensterProfil);
            fensterProfil = null;
            throw failure;
        }
    }

    @FunctionalInterface
    interface Neustart { WebDriver erzeuge() throws Exception; }
    @FunctionalInterface
    interface Wiederherstellung { void ausfuehren(WebDriver treiber, Map<String, Object> kontext) throws Exception; }
    @FunctionalInterface
    interface Denkpause { void warten(long millis) throws InterruptedException; }

    private void spieleAuftrag(Auftrag auftrag) {
        spieleAuftrag(auftrag, this::erzeugeFenster, WebHelfer::sitzungWiederherstellen);
    }

    /** Ein bestätigter Schritt wird nie erneut ausgeführt. Bei unklarem Commit-
     * Ausgang eines Schreibschritts scheitert der Auftrag; der neue Treiber steht
     * für Folgeaufträge bereit. Nur ausdrücklich wiederholbare Schritte laufen erneut. */
    void spieleAuftrag(Auftrag auftrag, Neustart neustart, Wiederherstellung wiederherstellung) {
        spieleAuftrag(auftrag, neustart, wiederherstellung, Thread::sleep);
    }

    void spieleAuftrag(Auftrag auftrag, Neustart neustart, Wiederherstellung wiederherstellung,
            Denkpause pause) {
        aktuellerAuftrag = "#" + auftrag.id + " " + auftrag.aktenzeichen;
        auftrag.status = Auftrag.Status.IN_BEARBEITUNG;
        auftrag.bearbeiter = kuerzel;
        auftrag.startZeit = LocalDateTime.now();
        auftrag.startNano = System.nanoTime();
        auftrag.endeNano = 0;
        Map<String, Object> kontext = new HashMap<>();
        kontext.put("mitarbeiter", kuerzel);
        kontext.put("passwort", passwort);
        String kollege = simulator.zufallsWebKollege(kuerzel);
        kontext.put("kollege", kollege);
        kontext.put("kollegePasswort", simulator.webPasswort(kollege));
        kontext.put("adminKonto", simulator.webAdminKennung());
        kontext.put("adminPasswort", simulator.webAdminPasswort());
        boolean fehlgeschlagen = false;
        boolean neuGestartet = false;
        for (int index = 0; index < auftrag.webAktionen.size(); index++) {
            WebAktion aktion = auftrag.webAktionen.get(index);
            if (simulator.abgebrochen()) {
                fehlgeschlagen = true;
                auftrag.fehlerText = "Lauf gestoppt";
                break;
            }
            try {
                if (index > 0 && denkzeitMs > 0) pause.warten(denkzeitMs);
                if (simulator.abgebrochen()) throw new InterruptedException("Lauf gestoppt");
                String detail = aktion.funktion().ausfuehren(treiber, kontext);
                auftrag.kontext = kontextOhnePasswoerter(kontext);
                protokolliere(auftrag, index + 1, aktion.name(), "OK — " + detail,
                        screenshot(auftrag, index + 1));
            } catch (Exception e) {
                if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                if (istToterTreiber(e) && !neuGestartet) {
                    neuGestartet = true;
                    schliesse();
                    try {
                        treiber = neustart.erzeuge();
                        if (aktion.wiederholbar()) {
                            wiederherstellung.ausfuehren(treiber, kontext);
                            protokolliere(auftrag, index + 1, aktion.name(),
                                    "Treiber neu gestartet; bestätigte Schritte bleiben erhalten", null);
                            index--; // nur den unterbrochenen sicheren Schritt wiederholen
                            continue;
                        }
                    } catch (Exception restartFehler) {
                        e.addSuppressed(restartFehler);
                    }
                }
                fehlgeschlagen = true;
                auftrag.fehlerText = e.getClass().getSimpleName() + " in UI-Schritt «" + aktion.name()
                        + "»: " + e.getMessage()
                        + (istToterTreiber(e) && !aktion.wiederholbar()
                        ? " — Commit-Ausgang unklar; Schreibschritt wird nicht wiederholt" : "");
                protokolliere(auftrag, index + 1, aktion.name(), "FEHLGESCHLAGEN — " + auftrag.fehlerText,
                        screenshot(auftrag, index + 1));
                simulator.log().fehler("WEB " + kuerzel, auftrag.fehlerText);
                break;
            }
        }
        auftrag.kontext = kontextOhnePasswoerter(kontext);
        auftrag.endeZeit = LocalDateTime.now();
        auftrag.endeNano = System.nanoTime();
        auftrag.status = fehlgeschlagen ? Auftrag.Status.FEHLGESCHLAGEN : Auftrag.Status.FERTIG_GESPIELT;
        if (!fehlgeschlagen) erledigt++;
        aktuellerAuftrag = null;
    }

    /** Kopie des Kontexts ohne Passwort-Schlüssel — Passwörter dürfen nie in
     *  Auftragskontext, Berichte (testdokumentation.md) oder die GUI gelangen. */
    private static Map<String, Object> kontextOhnePasswoerter(Map<String, Object> kontext) {
        Map<String, Object> kopie = new HashMap<>();
        kontext.forEach((schluessel, wert) -> {
            if (!schluessel.toLowerCase(java.util.Locale.ROOT).contains("passwort")) {
                kopie.put(schluessel, wert);
            }
        });
        return Map.copyOf(kopie);
    }

    /** Erkennt Ausnahmen, die auf einen gestorbenen Chrome-Treiber hindeuten —
     *  konservativ: nur klare Signale (Session weg, Fenster weg oder typische
     *  chromedriver-Abschiedsmeldungen). UnreachableBrowserException gibt es in
     *  Selenium 4 nicht mehr als Typ; dessen Meldung fängt der Text-Check mit. */
    private static boolean istToterTreiber(Throwable e) {
        if (e instanceof org.openqa.selenium.NoSuchSessionException
                || e instanceof org.openqa.selenium.NoSuchWindowException) {
            return true;
        }
        String meldung = e.getMessage();
        if (meldung != null) {
            String t = meldung.toLowerCase(java.util.Locale.ROOT);
            return t.contains("session deleted") || t.contains("invalid session id")
                    || t.contains("timed out receiving message")
                    || t.contains("unreachable browser");
        }
        return false;
    }

    private void protokolliere(Auftrag auftrag, int schrittNr, String aktion, String ergebnis,
            String bildDatei) {
        Auftrag.Schritt schritt = new Auftrag.Schritt(LocalDateTime.now(), kuerzel,
                aktion + (bildDatei == null ? "" : "  [Bild: " + bildDatei + "]"), ergebnis);
        auftrag.protokolliere(schritt);
        simulator.log().auftragSchritt(auftrag, schritt);
    }

    /** Screenshot des Fensters — landet im Laufprotokoll unter screenshots/. */
    private String screenshot(Auftrag auftrag, int schrittNr) {
        try {
            Path verzeichnis = simulator.log().runVerzeichnis.resolve("screenshots");
            Files.createDirectories(verzeichnis);
            String name = String.format("tf-%02d-schritt-%02d.png", auftrag.id, schrittNr);
            Path ziel = verzeichnis.resolve(name);
            Files.copy(((TakesScreenshot) treiber).getScreenshotAs(OutputType.FILE).toPath(), ziel,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return name;
        } catch (IOException | RuntimeException e) {
            return null; // Screenshot darf den Lauf nie bremsen
        }
    }

    /** Schließt ausschließlich Chrome-Fenster mit registriertem eigenen Profil. */
    static void raeumeVerwaisteFensterAuf() {
        BrowserProzesse.raeumeVerwaisteFensterAuf();
    }

    /** Einmalige Treiber-Vorbereitung (lädt den passenden ChromeDriver). */
    static synchronized void bereiteTreiberVor() {
        WebDriverManager.chromedriver().setup();
    }

    /** Schließt dieses Fenster (beim Start eines neuen Laufs oder beim Stopp). */
    void schliesse() {
        try {
            if (treiber != null) {
                treiber.quit();
            }
        } catch (RuntimeException ignored) {
        }
        // Profil-Verzeichnis dieses Fensters entfernen — sonst akkumulieren die
        // Chrome-Profile (mehrere MB je Fenster und je Treiber-Neustart) unbegrenzt.
        BrowserProzesse.loescheProfil(fensterProfil);
        fensterProfil = null;
        treiber = null;
    }
}
