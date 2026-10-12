package de.allgemein.testsweet;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Ein Auftrag aus dem Pool: eine Szenario-Instanz mit Bearbeitungszustand und
 * vollständigem Protokoll. Der Status wandert OFFEN → IN_BEARBEITUNG →
 * FERTIG_GESPIELT/FEHLGESCHLAGEN und nach der globalen Verifikationsphase
 * → VERIFIZIERT/VERIFIKATION_FEHLGESCHLAGEN.
 */
public final class Auftrag {

    public enum Status {
        OFFEN, IN_BEARBEITUNG, FERTIG_GESPIELT, FEHLGESCHLAGEN, VERIFIZIERT, VERIFIKATION_FEHLGESCHLAGEN
    }

    /** Protokollzeile eines Arbeitsschritts (für GUI-Detail und Auftrags-Logdatei). */
    public record Schritt(LocalDateTime zeit, String mitarbeiter, String aktion, String ergebnis) { }

    /** Ergebnis einer Datenbank-Erwartung nach der Verifikation. */
    public record Pruefung(String beschreibung, String erwartet, String ist, boolean ok, String hinweis) { }

    public final int id;
    public final String szenarioTyp;
    public final String aktenzeichen;
    public final String hauptperson;
    public final String beschreibung;
    public final List<SzenarioAktion> aktionen;
    public final List<Erwartung> erwartungen;

    /** Einfach formulierte Beschreibung fürs PDF („Was wird getestet?"). */
    public volatile String beschreibungEinfach = "";
    /** Nachvollziehbare Rechenwege (Eingaben + vollständige Berechnungsaufschlüsselung). */
    public volatile List<Rechenblock> rechenbloecke = List.of();
    /** Aktionen für Modus 2 (Weboberfläche/Selenium); Modus 1 nutzt {@link #aktionen}. */
    public volatile List<WebAktion> webAktionen = List.of();

    public volatile Status status = Status.OFFEN;
    public volatile String bearbeiter;
    public volatile LocalDateTime startZeit;
    public volatile LocalDateTime endeZeit;
    /** Monotone Stoppuhr (System.nanoTime) für die Laufzeitmessung — unabhängig
     *  von Wanduhr-Umstellungen (NTP/Zeitzone); startZeit/endeZeit bleiben für
     *  die Protokollanzeige in Wanduhrzeit erhalten. */
    public volatile long startNano;
    public volatile long endeNano;
    public volatile Long bgId;
    public volatile String fehlerText;
    /** Kopie des Spiel-Kontexts (bgId, zahlungId …) — Eingabe der Verifikation. */
    public volatile Map<String, Object> kontext = Map.of();

    private final List<Schritt> schritte = new ArrayList<>();
    private final List<Pruefung> pruefungen = new ArrayList<>();

    public Auftrag(int id, String szenarioTyp, String aktenzeichen, String hauptperson,
            String beschreibung, List<SzenarioAktion> aktionen, List<Erwartung> erwartungen) {
        this.id = id;
        this.szenarioTyp = szenarioTyp;
        this.aktenzeichen = aktenzeichen;
        this.hauptperson = hauptperson;
        this.beschreibung = beschreibung;
        this.aktionen = List.copyOf(aktionen);
        this.erwartungen = List.copyOf(erwartungen);
    }

    /** Fluent: einfache Beschreibung + Rechenwege für die PDF-Dokumentation. */
    public Auftrag dokumentiere(String beschreibungEinfach, List<Rechenblock> rechenbloecke) {
        this.beschreibungEinfach = beschreibungEinfach == null ? "" : beschreibungEinfach;
        this.rechenbloecke = rechenbloecke == null ? List.of() : List.copyOf(rechenbloecke);
        return this;
    }

    /** Fluent: UI-Aktionen für den Weboberflächen-Modus. */
    public Auftrag mitWebAktionen(List<WebAktion> webAktionen) {
        this.webAktionen = webAktionen == null ? List.of() : List.copyOf(webAktionen);
        return this;
    }

    public void protokolliere(Schritt schritt) {
        synchronized (schritte) {
            schritte.add(schritt);
        }
    }

    public List<Schritt> schritte() {
        synchronized (schritte) {
            return List.copyOf(schritte);
        }
    }

    public void ergaenzePruefung(Pruefung pruefung) {
        synchronized (pruefungen) {
            pruefungen.add(pruefung);
        }
    }

    public List<Pruefung> pruefungen() {
        synchronized (pruefungen) {
            return List.copyOf(pruefungen);
        }
    }

    public long dauerMs() {
        long von = startNano;
        if (von == 0) {
            return 0;
        }
        long bis = endeNano != 0 ? endeNano : System.nanoTime();
        return Duration.ofNanos(Math.max(0, bis - von)).toMillis();
    }
}
