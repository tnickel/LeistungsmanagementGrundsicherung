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
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

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
 *
 * <p>Alle Schreibaufrufe sind <b>asynchron</b>: Sie legen die fertige Zeile nur in
 * eine Queue und kehren sofort zurück — die Mitarbeiter-Threads blockieren also
 * nie auf der Festplatte. Ein Daemon-Thread ({@code sweet-log-writer}) nimmt die
 * Einträge gebündelt ab (bis zu 200 Zeilen oder 100-ms-Takt), schreibt und flusht
 * sie. {@link #close()} leert die Queue, schreibt den Rest synchron, schließt alle
 * Dateien und beendet den Thread; danach sind weitere Aufrufe bewusst no-op.</p>
 */
public final class SweetLog implements AutoCloseable {

    private static final DateTimeFormatter ZEIT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    /** Schreibauftrag: Ziel-Datei + vollständige, bereits formatierte Zeile. */
    private record Eintrag(BufferedWriter datei, String zeile) { }

    public final Path runVerzeichnis;

    private final BufferedWriter simulation;
    private final BufferedWriter verifikation;
    private final BufferedWriter fehler;
    private final Map<String, BufferedWriter> dateien = new ConcurrentHashMap<>();
    private final Deque<String> ring = new ArrayDeque<>();
    private final Object ringSchloss = new Object();
    private final Object dateiSchloss = new Object();
    private final LinkedBlockingQueue<Eintrag> queue = new LinkedBlockingQueue<>();
    private final Thread writerThread;
    private volatile boolean geschlossen = false;
    /** Erster I/O-Fehler bleibt auch nach einem später erfolgreichen Schreibaufruf bestehen. */
    private volatile IOException schreibfehler;
    private final Object fehlerSchloss = new Object();

    public IOException schreibfehler() { return schreibfehler; }

    public boolean vollstaendig() { return geschlossen && schreibfehler == null; }

    private void merkeSchreibfehler(IOException fehler) {
        boolean erster;
        synchronized (fehlerSchloss) {
            erster = schreibfehler == null;
            if (erster) schreibfehler = fehler;
            else if (schreibfehler != fehler && schreibfehler.getSuppressed().length < 8)
                schreibfehler.addSuppressed(fehler);
        }
        if (erster) System.err.println("Lognachweis unvollständig: " + fehler);
    }

    public SweetLog(Path basisVerzeichnis) throws IOException {
        Files.createDirectories(basisVerzeichnis);
        this.runVerzeichnis = Files.createTempDirectory(basisVerzeichnis, "run-" +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + "-");
        simulation = oeffne("simulation.log");
        verifikation = oeffne("db-verifikation.log");
        fehler = oeffne("fehler.log");
        writerThread = new Thread(this::schreibeSchleife, "sweet-log-writer");
        writerThread.setDaemon(true);
        writerThread.start();
    }

    // ------------------------------------------------------------------
    // Asynchrone Schreib-API
    // ------------------------------------------------------------------

    /** Allgemeines Ereignis (Kanal z. B. SIMULATOR, MITARBEITER, VERIFIKATION). */
    public void ereignis(String kanal, String mitarbeiter, String meldung) {
        reiheEin(simulation, kanal, mitarbeiter, meldung);
    }

    /** Schrittprotokoll eines Auftrags: landet in simulation.log UND der Auftragsdatei. */
    public void auftragSchritt(Auftrag auftrag, Auftrag.Schritt schritt) {
        String kanal = "AUFTRAG " + auftrag.id;
        String text = schritt.aktion() + " :: " + schritt.ergebnis();
        reiheEin(simulation, kanal, schritt.mitarbeiter(), text);
        BufferedWriter datei = dateiFuer("auftrag-" + auftrag.id + "-"
                + dateisicher(auftrag.aktenzeichen) + ".log");
        reiheEin(datei, "SCHRITT", schritt.mitarbeiter(), text);
    }

    /** Ereignis eines Mitarbeiters: simulation.log UND mitarbeiter-<kürzel>.log. */
    public void mitarbeiterEreignis(String kuerzel, String meldung) {
        reiheEin(simulation, "MITARBEITER", kuerzel, meldung);
        BufferedWriter datei = dateiFuer("mitarbeiter-" + dateisicher(kuerzel) + ".log");
        reiheEin(datei, "MITARBEITER", kuerzel, meldung);
    }

    /** Ergebnis einer Datenbank-Erwartung. */
    public void pruefung(Auftrag auftrag, Auftrag.Pruefung pruefung) {
        String zeile = (pruefung.ok() ? "OK   " : "ABW  ")
                + " Auftrag " + auftrag.id + " (" + auftrag.aktenzeichen + "): "
                + pruefung.beschreibung() + " — erwartet [" + pruefung.erwartet()
                + "], ist [" + pruefung.ist() + "]"
                + (pruefung.hinweis() != null ? " — " + pruefung.hinweis() : "");
        reiheRoherEin(verifikation, LocalDateTime.now(), "VERIFIKATION", "", zeile);
        if (!pruefung.ok()) {
            reiheRoherEin(fehler, LocalDateTime.now(), "ABWEICHUNG", "", zeile);
        }
    }

    public void fehler(String kanal, String meldung) {
        reiheRoherEin(fehler, LocalDateTime.now(), kanal, "", meldung);
        reiheEin(simulation, kanal, "", meldung);
    }

    /** Schreibt die Laufzusammenfassung (Kenndaten) in zusammenfassung.txt. */
    public void zusammenfassung(String text) throws IOException {
        Path datei = runVerzeichnis.resolve("zusammenfassung.txt");
        try {
            Files.writeString(datei, text, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            merkeSchreibfehler(e);
            throw e;
        }
        reiheEin(simulation, "SIMULATOR", "", "Zusammenfassung geschrieben: " + datei);
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
    // Intern: Queue, Writer-Thread, close
    // ------------------------------------------------------------------

    /** Formatiert die Zeile mit Aufruf-Zeitstempel, spiegelt sie in den Ringpuffer
     *  und legt sie für den Writer-Thread in die Queue. Nach close: no-op. */
    private void reiheEin(BufferedWriter datei, String kanal, String mitarbeiter, String meldung) {
        if (geschlossen) {
            return;
        }
        reiheRoherEin(datei, LocalDateTime.now(), kanal, mitarbeiter, meldung);
    }

    private synchronized void reiheRoherEin(BufferedWriter datei, LocalDateTime zeit,
            String kanal, String mitarbeiter, String meldung) {
        if (geschlossen) {
            return;
        }
        String zeile = ZEIT.format(zeit) + " [" + kanal + "] "
                + (mitarbeiter == null || mitarbeiter.isEmpty() ? "" : mitarbeiter + ": ")
                + meldung;
        inRing(kanal, mitarbeiter, meldung);
        if (!queue.offer(new Eintrag(datei, zeile))) {
            // Queue ohne Grenze — offer schlägt praktisch nie fehl; zur Sicherheit
            // direkt schreiben, damit kein Logverlust entsteht.
            schreibeDirekt(datei, zeile);
        }
    }

    /** Öffnet (once) und liefert die Protokolldatei zum Namen. */
    private BufferedWriter dateiFuer(String name) {
        BufferedWriter datei = dateien.get(name);
        if (datei != null) {
            return datei;
        }
        synchronized (dateiSchloss) {
            if (geschlossen) return simulation;
            return dateien.computeIfAbsent(name, n -> {
                try {
                    return oeffne(n);
                } catch (IOException e) {
                    merkeSchreibfehler(e);
                    throw new UncheckedIOException2(e);
                }
            });
        }
    }

    /** Writer-Schleife: nimmt Einträge gebündelt ab (bis 200 Zeilen je Takt,
     *  sonst 100-ms-Wartetakt) und schreibt+flusht die berührten Dateien. */
    private void schreibeSchleife() {
        List<Eintrag> batch = new ArrayList<>(200);
        while (!geschlossen || !queue.isEmpty()) {
            try {
                Eintrag erstes = queue.poll(100, TimeUnit.MILLISECONDS);
                if (erstes == null) {
                    continue;
                }
                batch.add(erstes);
                queue.drainTo(batch, 199);
                schreibeBatch(batch);
                batch.clear();
            } catch (InterruptedException erwartetBeiClose) {
                // close() kann den Thread wecken — der Rest gehört close() selbst
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void schreibeBatch(List<Eintrag> batch) {
        Set<BufferedWriter> beruehrt = new HashSet<>();
        for (Eintrag eintrag : batch) {
            schreibeDirekt(eintrag.datei(), eintrag.zeile());
            beruehrt.add(eintrag.datei());
        }
        for (BufferedWriter datei : beruehrt) {
            try {
                datei.flush();
            } catch (IOException e) {
                merkeSchreibfehler(e);
            }
        }
    }

    /** Asynchrone Fehler brechen Worker nicht ab, entwerten aber dauerhaft den Nachweis. */
    private void schreibeDirekt(BufferedWriter datei, String zeile) {
        try {
            datei.write(zeile);
            datei.newLine();
        } catch (IOException e) {
            merkeSchreibfehler(e);
        }
    }

    private BufferedWriter oeffne(String name) throws IOException {
        return Files.newBufferedWriter(runVerzeichnis.resolve(name), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
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

    /**
     * Schließt das Protokoll in jedem Fall sauber: Die Queue wird komplett
     * geleert (der Writer-Thread darf noch laufende Batches beenden), der Rest
     * wird synchron geschrieben und geflusht, dann werden alle Dateien
     * geschlossen und der Thread beendet. Weitere Aufrufe nach close sind
     * bewusst no-op (dasselbe Verhalten wie vorher: schreiben auf geschlossenem
     * Writer würde still verworfen).
     */
    @Override
    public synchronized void close() {
        if (geschlossen) {
            return;
        }
        geschlossen = true;
        boolean interrupted = false;
        while (writerThread.isAlive()) {
            try {
                writerThread.join(1000); // keinen Writer schließen, solange ein Batch ihn noch benutzt
            } catch (InterruptedException e) {
                interrupted = true;
            }
        }
        // Was (bei Timeout/Interrupt) noch in der Queue liegt, synchron wegschreiben
        List<Eintrag> rest = new ArrayList<>(queue.size());
        queue.drainTo(rest);
        if (!rest.isEmpty()) {
            schreibeBatch(rest);
        }
        synchronized (dateiSchloss) {
            for (BufferedWriter datei : dateien.values()) {
                try {
                    datei.close();
                } catch (IOException e) {
                    merkeSchreibfehler(e);
                }
            }
            dateien.clear();
            for (BufferedWriter datei : new BufferedWriter[]{simulation, verifikation, fehler}) {
                try {
                    datei.close();
                } catch (IOException e) {
                    merkeSchreibfehler(e);
                }
            }
        }
        if (interrupted) Thread.currentThread().interrupt();
    }

    /** Kleiner Helfer, um IOException aus Lambda-Konstruktoren zu tragen. */
    private static final class UncheckedIOException2 extends RuntimeException {
        UncheckedIOException2(IOException ursache) {
            super(ursache);
        }
    }
}
