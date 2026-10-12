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
import java.util.concurrent.ThreadLocalRandom;

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

    volatile WebDriver treiber;
    volatile String aktuellerAuftrag;
    volatile int erledigt;
    volatile boolean aktiv;

    WebMitarbeiter(Simulator simulator, String kuerzel, String passwort,
            int fensterIndex, int fensterAnzahl) {
        this.simulator = simulator;
        this.kuerzel = kuerzel;
        this.passwort = passwort;
        this.fensterIndex = fensterIndex;
        this.fensterAnzahl = fensterAnzahl;
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
            }
        } catch (Exception e) {
            simulator.log().fehler("WEB " + kuerzel, "Fenster konnte nicht gestartet werden: "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
        } finally {
            aktiv = false;
        }
    }

    /** Erzeugt das sichtbare Chrome-Fenster und platziert es im Raster. */
    private WebDriver erzeugeFenster() {
        ChromeOptions optionen = new ChromeOptions();
        // Skalierungsfaktor verkleinert Schrift und Layout dauerhaft, damit die
        // Seiteninhalte auch in den kleinen Rasterfenstern vollständig Platz finden
        optionen.addArguments("--lang=de", "--window-size=900,700",
                "--force-device-scale-factor=0.8");
        optionen.setExperimentalOption("excludeSwitches", List.of("enable-automation"));
        optionen.setExperimentalOption("useAutomationExtension", false);
        WebDriver d = new ChromeDriver(optionen);
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
    }

    /**
     * Spielanordnung mit EINMALIGEM Treiber-Neustart: Stirbt der Chrome-Treiber
     * mitten im Auftrag (Absturz, geschlossenes Fenster), wird das Fenster neu
     * erzeugt und der Auftrag vollständig wiederholt. Ein zweiter Treibertod
     * innerhalb desselben Auftrags lässt ihn regulär FEHLGESCHLAGEN enden.
     */
    private void spieleAuftrag(Auftrag auftrag) {
        Exception treiberTod = spiele(auftrag);
        if (treiberTod == null) {
            return;
        }
        schliesse();
        try {
            treiber = erzeugeFenster();
        } catch (Exception fensterFehler) {
            simulator.log().fehler("WEB " + kuerzel, "Fenster-Neustart fehlgeschlagen (Auftrag "
                    + auftrag.id + "): " + fensterFehler.getClass().getSimpleName()
                    + ": " + fensterFehler.getMessage());
            return;
        }
        simulator.log().mitarbeiterEreignis(kuerzel, "Treiber-Neustart nach "
                + treiberTod.getClass().getSimpleName() + " — Auftrag " + auftrag.id
                + " wird einmalig komplett wiederholt");
        auftrag.fehlerText = null; // der Wiederholungsversuch darf neu bewertet werden
        Exception erneut = spiele(auftrag);
        if (erneut != null) {
            simulator.log().fehler("WEB " + kuerzel, "Auftrag " + auftrag.id
                    + " auch nach Treiber-Neustart gescheitert — bleibt FEHLGESCHLAGEN");
        }
    }

    /** Spielt alle UI-Schritte eines Auftrags durch und protokolliert jeden Schritt.
     *  Rückgabe: Ursache, wenn der Treiber während des Auftrags gestorben ist
     *  (null sonst) — der Aufrufer darf dann genau einmal neu starten. */
    private Exception spiele(Auftrag auftrag) {
        aktuellerAuftrag = "#" + auftrag.id + " " + auftrag.aktenzeichen;
        auftrag.status = Auftrag.Status.IN_BEARBEITUNG;
        auftrag.bearbeiter = kuerzel;
        auftrag.startZeit = LocalDateTime.now();
        auftrag.startNano = System.nanoTime();
        Map<String, Object> kontext = new HashMap<>();
        kontext.put("mitarbeiter", kuerzel);
        kontext.put("passwort", passwort);
        String kollege = simulator.zufallsWebKollege(kuerzel);
        kontext.put("kollege", kollege);
        kontext.put("kollegePasswort", simulator.webPasswort(kollege));
        kontext.put("adminKonto", simulator.webAdminKennung());
        kontext.put("adminPasswort", simulator.webAdminPasswort());

        simulator.log().mitarbeiterEreignis(kuerzel, "nimmt Web-Auftrag " + auftrag.id + " ("
                + auftrag.szenarioTyp + ", " + auftrag.aktenzeichen + ") — Kollege: " + kollege);

        boolean fehlgeschlagen = false;
        Exception toterTreiber = null;
        int schrittNr = 0;
        for (WebAktion aktion : auftrag.webAktionen) {
            schrittNr++;
            if (simulator.abgebrochen()) {
                protokolliere(auftrag, schrittNr, aktion.name(), "ABGEBROCHEN (Lauf gestoppt)", null);
                fehlgeschlagen = true;
                break;
            }
            String bild = null;
            try {
                String detail = aktion.funktion().ausfuehren(treiber, kontext);
                // Passwörter bewusst NICHT in den Auftrag spiegeln (CR-002): der
                // Kontext des Auftrags wird später in Berichte geschrieben; die
                // WebHelfer-Schritte lesen die Passwörter weiterhin aus der
                // lokalen Map. Der Marker (vorgangErstellt …) überlebt auch so
                // einen Worker-Tod.
                auftrag.kontext = kontextOhnePasswoerter(kontext);
                bild = screenshot(auftrag, schrittNr);
                protokolliere(auftrag, schrittNr, aktion.name(),
                        "OK" + (detail == null || detail.isBlank() ? "" : " — " + detail), bild);
            } catch (Exception e) {
                bild = screenshot(auftrag, schrittNr);
                if (istToterTreiber(e)) {
                    protokolliere(auftrag, schrittNr, aktion.name(),
                            "FEHLGESCHLAGEN (" + e.getClass().getSimpleName() + ") — "
                                    + e.getMessage() + " — Treiber gestorben, Neustart geplant", bild);
                    auftrag.fehlerText = e.getClass().getSimpleName() + " in UI-Schritt «"
                            + aktion.name() + "» (Treiber gestorben): " + e.getMessage();
                    simulator.log().fehler("WEB " + kuerzel, "Auftrag " + auftrag.id + " ("
                            + auftrag.aktenzeichen + "): Treiber gestorben — "
                            + auftrag.fehlerText);
                    toterTreiber = e;
                    fehlgeschlagen = true;
                    break;
                }
                protokolliere(auftrag, schrittNr, aktion.name(),
                        "FEHLGESCHLAGEN (" + e.getClass().getSimpleName() + ") — "
                                + e.getMessage(), bild);
                auftrag.fehlerText = e.getClass().getSimpleName() + " in UI-Schritt «"
                        + aktion.name() + "»: " + e.getMessage();
                simulator.log().fehler("WEB " + kuerzel, "Auftrag " + auftrag.id + " ("
                        + auftrag.aktenzeichen + ") fehlgeschlagen: " + auftrag.fehlerText
                        + " — Screenshot: " + bild);
                fehlgeschlagen = true;
                break;
            }
        }

        auftrag.kontext = kontextOhnePasswoerter(kontext);
        auftrag.endeZeit = LocalDateTime.now();
        auftrag.endeNano = System.nanoTime();
        auftrag.status = fehlgeschlagen ? Auftrag.Status.FEHLGESCHLAGEN
                : Auftrag.Status.FERTIG_GESPIELT;
        if (!fehlgeschlagen) {
            erledigt++;
        }
        aktuellerAuftrag = null;
        simulator.log().mitarbeiterEreignis(kuerzel, "Web-Auftrag " + auftrag.id
                + " abgeschlossen: " + auftrag.status + " (" + auftrag.dauerMs() + " ms)");
        return toterTreiber;
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

    /**
     * Räumt verwaiste Selenium-Fenster auf — aber NUR die der eigenen
     * Java-Prozesskette: Zuerst wird die eigene PID-Kette über Win32_Process
     * (ParentProcessId) bis zur Wurzel verfolgt, dann werden rekursiv alle
     * Kind-Prozesse dieser Kette gesammelt und daraus ausschließlich
     * chromedriver.exe und deren Automation-Chrome-Fenster (Erkennung über
     * Selenium-Parameter) beendet. Fremde Chrome-Fenster anderer Nutzer und
     * Prozesse bleiben unberührt. Der Skript-Body ist identisch zu
     * testsweet/raeume-fenster.ps1.
     */
    static void raeumeVerwaisteFensterAuf() {
        try {
            ProcessBuilder pb = new ProcessBuilder("powershell", "-NoProfile",
                    "-ExecutionPolicy", "Bypass", "-Command", RAEUME_SKRIPT);
            pb.start().waitFor(20, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception ignored) {
            // Ohne PowerShell: wenigstens die chromedriver-Prozesse der EIGENEN
            // Prozesskette beenden (Descendants des aktuellen Prozesses statt
            // global — fremde Treiber bleiben unberührt)
            ProcessHandle aktuelleKette = ProcessHandle.current();
            aktuelleKette.descendants()
                    .filter(p -> p.info().command()
                            .map(c -> c.toLowerCase().endsWith("chromedriver.exe")).orElse(false))
                    .forEach(p -> {
                        try {
                            p.destroy();
                        } catch (Exception ignoredToo) {
                            // nichts weiter zu tun
                        }
                    });
        }
    }

    /** PowerShell-Skript zum kettenbegrenzten Aufräumen (identisch zu raeume-fenster.ps1). */
    private static final String RAEUME_SKRIPT = """
            $prozesse = @()
            try { $prozesse = Get-CimInstance Win32_Process -ErrorAction Stop } catch {}
            if (@($prozesse).Count -eq 0) {
                Write-Output 'Prozessliste nicht ermittelbar — es wurde nichts beendet'
                exit 0
            }
            # Eigene PID-Kette nach oben verfolgen (Abbruch bei Zyklus oder Wurzel)
            $kette = New-Object System.Collections.Generic.HashSet[uint32]
            $aktuell = [uint32]$PID
            while ($aktuell -and $aktuell -ne 0 -and -not $kette.Contains($aktuell)) {
                [void]$kette.Add($aktuell)
                $eintrag = $prozesse | Where-Object { $_.ProcessId -eq $aktuell } | Select-Object -First 1
                if ($null -eq $eintrag) { break }
                $aktuell = [uint32]$eintrag.ParentProcessId
            }
            # Alle Kind-Prozesse der Kette rekursiv sammeln (chromedriver, chrome darunter)
            $eigene = New-Object System.Collections.Generic.HashSet[uint32]
            $warteschlange = [System.Collections.Generic.Queue[uint32]]::new()
            foreach ($id in $kette) { $warteschlange.Enqueue($id) }
            while ($warteschlange.Count -gt 0) {
                $id = $warteschlange.Dequeue()
                foreach ($p in ($prozesse | Where-Object { $_.ParentProcessId -eq $id })) {
                    if (-not $eigene.Contains([uint32]$p.ProcessId)) {
                        [void]$eigene.Add([uint32]$p.ProcessId)
                        $warteschlange.Enqueue([uint32]$p.ProcessId)
                    }
                }
            }
            # Nur eigene chromedriver-/Automation-Chrome-Prozesse beenden
            $ziele = $prozesse | Where-Object {
                ($_.ProcessId -ne $PID) -and $eigene.Contains([uint32]$_.ProcessId) -and (
                    $_.Name -eq 'chromedriver.exe' -or
                    ($_.Name -eq 'chrome.exe' -and ($_.CommandLine -like '*--remote-debugging-pipe*' -or $_.CommandLine -like '*--test-type*')))
            }
            if (@($ziele).Count -eq 0) {
                Write-Output 'Keine verwaisten Fenster der eigenen Prozesskette gefunden'
            } else {
                foreach ($z in $ziele) { Stop-Process -Id $z.ProcessId -Force -ErrorAction SilentlyContinue }
                Write-Output ('Aufgeraeumt: ' + @($ziele).Count + ' Prozess(e) der eigenen Kette beendet')
            }
            """;

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
            // beim Aufräumen egal
        } finally {
            treiber = null;
        }
    }
}
