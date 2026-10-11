package de.allgemein.testsweet;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Strukturiertes Protokoll eines Simulator-Laufs — die Rohlage für die manuelle
 * Auswertung mit dem Nutzer. Je Lauf ein Verzeichnis {@code logs/run-<Zeitstempel>/}
 * mit:
 * <ul>
 *   <li>{@code simulation.log} — alle Ereignisse chronologisch (Kanal, Mitarbeiter, Meldung)</li>
 *   <li>{@code mitarbeiter-<kürzel>.log} — je simuliertem Sachbearbeiter</li>
 *   <li>{@code auftrag-<id>-<aktenzeichen>.log} — je Auftrag (Schrittprotokoll)</li>
 *   <li>{@code db-verifikation.log} — Erwartung/Ist je Prüfung</li>
 *   <li>{@code fehler.log} — nur Fehler und Abweichungen</li>
 *   <li>{@code zusammenfassung.txt} — Kenndaten des Laufs</li>
 * </ul>
 * Zusätzlich hält {@link SweetLog} einen Ringpuffer für den Live-Ticker der GUI.
 */
public final class SweetLog implements AutoCloseable {

    private static final DateTimeFormatter ZEIT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    public final Path runVerzeichnis;

    private final BufferedWriter simulation;
    private final BufferedWriter verifikation;
    private final BufferedWriter fehler;
    private final Map<String, BufferedWriter> dateien = new ConcurrentHashMap<>();
    private final Deque<String> ring = new ArrayDeque<>();
    private final Object ringSchloss = new Object();

    public SweetLog(Path basisVerzeichnis) throws IOException {
        this.runVerzeichnis = basisVerzeichnis.resolve("run-" +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")));
        Files.createDirectories(runVerzeichnis);
        simulation = oeffne("simulation.log");
        verifikation = oeffne("db-verifikation.log");
        fehler = oeffne("fehler.log");
    }

    /** Allgemeines Ereignis (Kanal z. B. SIMULATOR, MITARBEITER, VERIFIKATION). */
    public synchronized void ereignis(String kanal, String mitarbeiter, String meldung) {
        schreibe(simulation, kanal, mitarbeiter, meldung);
        inRing(kanal, mitarbeiter, meldung);
    }

    /** Schrittprotokoll eines Auftrags: landet in simulation.log UND der Auftragsdatei. */
    public synchronized void auftragSchritt(Auftrag auftrag, Auftrag.Schritt schritt) {
        String kanal = "AUFTRAG " + auftrag.id;
        schreibe(simulation, kanal, schritt.mitarbeiter(),
                schritt.aktion() + " :: " + schritt.ergebnis());
        inRing(kanal, schritt.mitarbeiter(), schritt.aktion() + " :: " + schritt.ergebnis());
        BufferedWriter datei = dateien.computeIfAbsent(
                "auftrag-" + auftrag.id + "-" + dateisicher(auftrag.aktenzeichen) + ".log",
                name -> {
                    try {
                        return oeffne(name);
                    } catch (IOException e) {
                        throw new UncheckedIOException2(e);
                    }
                });
        schreibe(datei, "SCHRITT", schritt.mitarbeiter(),
                schritt.aktion() + " :: " + schritt.ergebnis());
    }

    /** Ereignis eines Mitarbeiters: simulation.log UND mitarbeiter-<kürzel>.log. */
    public synchronized void mitarbeiterEreignis(String kuerzel, String meldung) {
        schreibe(simulation, "MITARBEITER", kuerzel, meldung);
        inRing("MITARBEITER", kuerzel, meldung);
        BufferedWriter datei = dateien.computeIfAbsent(
                "mitarbeiter-" + dateisicher(kuerzel) + ".log",
                name -> {
                    try {
                        return oeffne(name);
                    } catch (IOException e) {
                        throw new UncheckedIOException2(e);
                    }
                });
        schreibe(datei, "MITARBEITER", kuerzel, meldung);
    }

    /** Ergebnis einer Datenbank-Erwartung. */
    public synchronized void pruefung(Auftrag auftrag, Auftrag.Pruefung pruefung) {
        String zeile = (pruefung.ok() ? "OK   " : "ABW  ")
                + " Auftrag " + auftrag.id + " (" + auftrag.aktenzeichen + "): "
                + pruefung.beschreibung() + " — erwartet [" + pruefung.erwartet()
                + "], ist [" + pruefung.ist() + "]"
                + (pruefung.hinweis() != null ? " — " + pruefung.hinweis() : "");
        schreibeRoher(verifikation, LocalDateTime.now(), "VERIFIKATION", "", zeile);
        inRing("VERIFIKATION", "", zeile);
        if (!pruefung.ok()) {
            schreibeRoher(fehler, LocalDateTime.now(), "ABWEICHUNG", "", zeile);
        }
    }

    public synchronized void fehler(String kanal, String meldung) {
        schreibeRoher(fehler, LocalDateTime.now(), kanal, "", meldung);
        schreibe(simulation, kanal, "", meldung);
        inRing(kanal, "", meldung);
    }

    /** Schreibt die Laufzusammenfassung (Kenndaten) in zusammenfassung.txt. */
    public synchronized void zusammenfassung(String text) throws IOException {
        Path datei = runVerzeichnis.resolve("zusammenfassung.txt");
        Files.writeString(datei, text, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        schreibe(simulation, "SIMULATOR", "", "Zusammenfassung geschrieben: " + datei);
    }

    /** Letzte Logzeilen für den Live-Ticker der GUI. */
    public java.util.List<String> letzteZeilen(int anzahl) {
        synchronized (ringSchloss) {
            java.util.List<String> kopie = new java.util.ArrayList<>(ring);
            int von = Math.max(0, kopie.size() - anzahl);
            return kopie.subList(von, kopie.size());
        }
    }

    // ------------------------------------------------------------------

    private BufferedWriter oeffne(String name) throws IOException {
        return Files.newBufferedWriter(runVerzeichnis.resolve(name), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private void schreibe(BufferedWriter datei, String kanal, String mitarbeiter, String meldung) {
        schreibeRoher(datei, LocalDateTime.now(), kanal, mitarbeiter, meldung);
    }

    private void schreibeRoher(BufferedWriter datei, LocalDateTime zeit,
            String kanal, String mitarbeiter, String meldung) {
        try {
            datei.write(ZEIT.format(zeit) + " [" + kanal + "] "
                    + (mitarbeiter == null || mitarbeiter.isEmpty() ? "" : mitarbeiter + ": ")
                    + meldung);
            datei.newLine();
            datei.flush();
        } catch (IOException e) {
            // Logverlust darf den Simulator nicht abreißen lassen; auf stderr melden
            System.err.println("Logfehler: " + e);
        }
    }

    private void inRing(String kanal, String mitarbeiter, String meldung) {
        synchronized (ringSchloss) {
            ring.addLast(ZEIT.format(LocalDateTime.now()) + " [" + kanal + "] "
                    + (mitarbeiter == null || mitarbeiter.isEmpty() ? "" : mitarbeiter + ": ")
                    + meldung);
            while (ring.size() > 500) {
                ring.removeFirst();
            }
        }
    }

    private static String dateisicher(String text) {
        return text.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    @Override
    public synchronized void close() {
        for (BufferedWriter datei : dateien.values()) {
            try {
                datei.close();
            } catch (IOException ignored) {
                // beim Aufräumen egal
            }
        }
        dateien.clear();
        for (BufferedWriter datei : new BufferedWriter[]{simulation, verifikation, fehler}) {
            try {
                datei.close();
            } catch (IOException ignored) {
                // beim Aufräumen egal
            }
        }
    }

    /** Kleiner Helfer, um IOException aus Lambda-Konstruktoren zu tragen. */
    private static final class UncheckedIOException2 extends RuntimeException {
        UncheckedIOException2(IOException ursache) {
            super(ursache);
        }
    }
}
