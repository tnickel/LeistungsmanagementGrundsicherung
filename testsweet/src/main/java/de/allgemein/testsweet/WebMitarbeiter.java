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
                spiele(auftrag);
            }
        } catch (Exception e) {
            simulator.log().fehler("WEB " + kuerzel, "Fenster konnte nicht gestartet werden: "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
        } finally {
            aktiv = false;
            simulator.mitarbeiterBeendet(null);
        }
    }

    /** Erzeugt das sichtbare Chrome-Fenster und platziert es im Raster. */
    private WebDriver erzeugeFenster() {
        ChromeOptions optionen = new ChromeOptions();
        optionen.addArguments("--lang=de", "--window-size=900,700");
        optionen.setExperimentalOption("excludeSwitches", List.of("enable-automation"));
        optionen.setExperimentalOption("useAutomationExtension", false);
        WebDriver d = new ChromeDriver(optionen);
        // Bildschirm-Raster: Spalten × Zeilen, Aufgabe unten 48 px für die Taskleiste
        java.awt.Dimension bild = java.awt.Toolkit.getDefaultToolkit().getScreenSize();
        int spalten = (int) Math.ceil(Math.sqrt(fensterAnzahl));
        int zeilen = (int) Math.ceil((double) fensterAnzahl / spalten);
        int breite = Math.max(320, bild.width / spalten);
        int hoehe = Math.max(240, (bild.height - 48) / zeilen);
        int spalte = fensterIndex % spalten;
        int reihe = fensterIndex / spalten;
        d.manage().window().setPosition(new Point(spalte * breite, reihe * hoehe));
        d.manage().window().setSize(new Dimension(breite, hoehe));
        return d;
    }

    /** Spielt alle UI-Schritte eines Auftrags durch und protokolliert jeden Schritt. */
    private void spiele(Auftrag auftrag) {
        aktuellerAuftrag = "#" + auftrag.id + " " + auftrag.aktenzeichen;
        auftrag.status = Auftrag.Status.IN_BEARBEITUNG;
        auftrag.bearbeiter = kuerzel;
        auftrag.startZeit = LocalDateTime.now();
        Map<String, Object> kontext = new HashMap<>();
        kontext.put("mitarbeiter", kuerzel);
        kontext.put("passwort", passwort);
        String kollege = simulator.zufallsWebKollege(kuerzel);
        kontext.put("kollege", kollege);
        kontext.put("kollegePasswort", simulator.webPasswort(kollege));

        simulator.log().mitarbeiterEreignis(kuerzel, "nimmt Web-Auftrag " + auftrag.id + " ("
                + auftrag.szenarioTyp + ", " + auftrag.aktenzeichen + ") — Kollege: " + kollege);

        boolean fehlgeschlagen = false;
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
                bild = screenshot(auftrag, schrittNr);
                protokolliere(auftrag, schrittNr, aktion.name(),
                        "OK" + (detail == null || detail.isBlank() ? "" : " — " + detail), bild);
            } catch (Exception e) {
                bild = screenshot(auftrag, schrittNr);
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

        auftrag.kontext = Map.copyOf(kontext);
        auftrag.endeZeit = LocalDateTime.now();
        auftrag.status = fehlgeschlagen ? Auftrag.Status.FEHLGESCHLAGEN
                : Auftrag.Status.FERTIG_GESPIELT;
        if (!fehlgeschlagen) {
            erledigt++;
        }
        aktuellerAuftrag = null;
        simulator.log().mitarbeiterEreignis(kuerzel, "Web-Auftrag " + auftrag.id
                + " abgeschlossen: " + auftrag.status + " (" + auftrag.dauerMs() + " ms)");
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
