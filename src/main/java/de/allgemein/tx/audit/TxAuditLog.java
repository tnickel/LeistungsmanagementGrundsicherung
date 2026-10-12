package de.allgemein.tx.audit;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Revisionssicheres Audit-Log für Transaktions- und Saga-Ereignisse (Commit, Rollback,
 * Retry, Timeout, Saga-Schritte und Kompensationen).
 *
 * <p>Ereignisse werden über einen strukturierten Logger ausgegeben, in einem thread-sicheren
 * In-Memory-Ringpuffer (letzte 1.000 Einträge) für UI/Diagnose gehalten und optional
 * in eine Audit-Logdatei auf Platte geschrieben. Die Datei wird bei Überschreiten von
 * {@value #MAX_DATEIGROESSE_BYTES} Bytes auf {@code <name>.1} rotiert (ein Vorgänger).</p>
 *
 * <p><b>Datenschutz:</b> Es werden bewusst keine Nutzdaten (Payloads mit Sozialdaten)
 * protokolliert — Saga-Starts nur mit Typ und Payload-Länge; die Korrelation läuft
 * über die Saga-ID bzw. Transaktions-ID.</p>
 */
public final class TxAuditLog {

    private static final Logger LOG = Logger.getLogger("de.allgemein.tx.audit");
    private static final int MAX_RING_BUFFER = 1000;

    /** Rotationsschwelle der Audit-Datei (10 MB); ein Vorgänger wird als .1 behalten. */
    static final long MAX_DATEIGROESSE_BYTES = 10L * 1024 * 1024;

    private static final AtomicLong ID_GENERATOR = new AtomicLong(1);
    private static final AtomicLong RING_ANZAHL = new AtomicLong();
    private static final Object RING_LOCK = new Object();

    private static final ConcurrentLinkedDeque<TxAuditEvent> RING_BUFFER = new ConcurrentLinkedDeque<>();
    private static final List<TxAuditListener> LISTENERS = new CopyOnWriteArrayList<>();
    private static volatile Path auditFilePath = null;
    private static final Object FILE_LOCK = new Object();

    /** Dauerhaft geöffneter Writer (nur unter {@link #FILE_LOCK} berührt). */
    private static BufferedWriter dateiWriter = null;
    private static long geschriebenBytes = 0;

    private TxAuditLog() {
    }

    /**
     * Konfiguriert den Pfad zur Audit-Logdatei (null deaktiviert Datei-Logging).
     * Kontrakt: zur Initialisierung bzw. zum Umschalten vor Laufbeginn aufrufen —
     * ein Wechsel schließt den bisherigen Writer und startet bei Byte 0.
     */
    public static void setAuditFilePath(Path path) {
        synchronized (FILE_LOCK) {
            schliesseWriter();
            auditFilePath = path;
            geschriebenBytes = 0;
        }
        if (path != null) {
            try {
                if (path.getParent() != null) {
                    Files.createDirectories(path.getParent());
                }
                LOG.info(() -> "Audit-Logdatei konfiguriert: " + path.toAbsolutePath());
            } catch (IOException e) {
                LOG.log(Level.WARNING, "Konnte Verzeichnis für Audit-Datei nicht anlegen", e);
            }
        }
    }

    /** Schließt die Audit-Datei (z. B. im geordneten Shutdown); weitere Events öffnen sie erneut. */
    public static void closeFile() {
        synchronized (FILE_LOCK) {
            schliesseWriter();
        }
    }

    public static void addListener(TxAuditListener listener) {
        LISTENERS.add(listener);
    }

    public static void removeListener(TxAuditListener listener) {
        LISTENERS.remove(listener);
    }

    /** Liefert die neuesten Ereignisse (maximal bis zum Limit). */
    public static List<TxAuditEvent> getRecentEvents(int limit) {
        List<TxAuditEvent> result = new ArrayList<>();
        int count = 0;
        for (TxAuditEvent event : RING_BUFFER) {
            result.add(event);
            if (++count >= limit) {
                break;
            }
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * Datenschutzkonforme Fehlerbeschreibung für Audit-Ereignisse: nur Exception-Typ und
     * ggf. ORA-Code, nie die Meldung — sie kann Eingabewerte, Aktenzeichen oder andere
     * Sozialdaten enthalten (Details stehen mit Stacktrace im technischen Server-Log).
     */
    public static String fehlerBeschreibung(Throwable fehler) {
        if (fehler == null) {
            return null;
        }
        String ora = null;
        Throwable aktuell = fehler;
        for (int i = 0; aktuell != null && i < 8; i++, aktuell = aktuell.getCause()) {
            if (aktuell instanceof java.sql.SQLException sql) {
                ora = " ORA-" + String.format("%05d", sql.getErrorCode());
                break;
            }
        }
        return fehler.getClass().getSimpleName() + (ora == null ? "" : ora);
    }

    static void clearForTests() {
        synchronized (FILE_LOCK) {
            schliesseWriter();
            geschriebenBytes = 0;
        }
        RING_BUFFER.clear();
        RING_ANZAHL.set(0);
    }

    // ------------------------------------------------------------------
    // Transaktions-Ereignisse
    // ------------------------------------------------------------------

    public static void recordTxCommit(String txId, int attempt, long durationMillis, String details) {
        record(new TxAuditEvent(ID_GENERATOR.getAndIncrement(), Instant.now(), Thread.currentThread().getName(),
                "TX", "COMMIT", txId, attempt, durationMillis, details, null));
    }

    public static void recordTxRollback(String txId, int attempt, long durationMillis, String error) {
        record(new TxAuditEvent(ID_GENERATOR.getAndIncrement(), Instant.now(), Thread.currentThread().getName(),
                "TX", "ROLLBACK", txId, attempt, durationMillis, null, error));
    }

    public static void recordTxRetry(String txId, int attempt, String reason) {
        record(new TxAuditEvent(ID_GENERATOR.getAndIncrement(), Instant.now(), Thread.currentThread().getName(),
                "TX", "RETRY", txId, attempt, -1, reason, null));
    }

    // ------------------------------------------------------------------
    // Saga-Ereignisse
    // ------------------------------------------------------------------

    /**
     * Protokolliert den Saga-Start ohne Nutzdaten: Der Payload kann Sozialdaten
     * (Name, IBAN, Einkommen) enthalten und darf weder in Log, Datei noch
     * Ringpuffer erscheinen (§ 35 SGB I / Art. 9 DSGVO). Korrelation über sagaId.
     */
    public static void recordSagaStart(String sagaId, String sagaType, String payload) {
        int laenge = payload == null ? 0 : payload.length();
        record(new TxAuditEvent(ID_GENERATOR.getAndIncrement(), Instant.now(), Thread.currentThread().getName(),
                "SAGA", "START", sagaId, 0, -1, "type=" + sagaType + " payloadLaenge=" + laenge, null));
    }

    public static void recordSagaStep(String sagaId, int stepIndex, String stepName) {
        record(new TxAuditEvent(ID_GENERATOR.getAndIncrement(), Instant.now(), Thread.currentThread().getName(),
                "SAGA", "STEP_DONE", sagaId, stepIndex, -1, "step=" + stepName, null));
    }

    public static void recordSagaCompleted(String sagaId, String sagaType) {
        record(new TxAuditEvent(ID_GENERATOR.getAndIncrement(), Instant.now(), Thread.currentThread().getName(),
                "SAGA", "COMPLETED", sagaId, 0, -1, "type=" + sagaType, null));
    }

    public static void recordSagaCompensating(String sagaId, String reason) {
        record(new TxAuditEvent(ID_GENERATOR.getAndIncrement(), Instant.now(), Thread.currentThread().getName(),
                "SAGA", "COMPENSATING", sagaId, 0, -1, reason, null));
    }

    public static void recordSagaFailed(String sagaId, String error) {
        record(new TxAuditEvent(ID_GENERATOR.getAndIncrement(), Instant.now(), Thread.currentThread().getName(),
                "SAGA", "FAILED", sagaId, 0, -1, null, error));
    }

    // ------------------------------------------------------------------
    // Interne Verarbeitung
    // ------------------------------------------------------------------

    private static void record(TxAuditEvent event) {
        // 1. Ringpuffer (neueste zuerst; Trim über Zähler statt size())
        synchronized (RING_LOCK) {
            RING_BUFFER.addFirst(event);
            RING_ANZAHL.incrementAndGet();
            while (RING_ANZAHL.get() > MAX_RING_BUFFER) {
                RING_BUFFER.pollLast();
                RING_ANZAHL.decrementAndGet();
            }
        }

        // 2. Logger
        String line = event.toLogLine();
        LOG.info(line);

        // 3. Optionaler Dateiausschrieb (dauerhaft offener Writer, Rotation nach Größe)
        Path path = auditFilePath;
        if (path != null) {
            synchronized (FILE_LOCK) {
                try {
                    schreibeZeile(path, line);
                } catch (IOException e) {
                    LOG.log(Level.WARNING, "Konnte Audit-Ereignis nicht in Datei schreiben", e);
                }
            }
        }

        // 4. Externe Listener
        for (TxAuditListener listener : LISTENERS) {
            try {
                listener.onEvent(event);
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Fehler in TxAuditListener", e);
            }
        }
    }

    private static void schreibeZeile(Path path, String line) throws IOException {
        if (geschriebenBytes >= MAX_DATEIGROESSE_BYTES) {
            rotiere(path);
        }
        if (dateiWriter == null) {
            dateiWriter = Files.newBufferedWriter(path, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.WRITE);
            geschriebenBytes = Files.exists(path) ? Files.size(path) : 0L;
        }
        dateiWriter.write(line);
        dateiWriter.newLine();
        dateiWriter.flush();
        geschriebenBytes += line.length() + 1L; // UTF-8-Näherung, für die Rotation ausreichend
    }

    private static void rotiere(Path path) throws IOException {
        schliesseWriter();
        Path vorgaenger = path.resolveSibling(path.getFileName() + ".1");
        try {
            Files.deleteIfExists(vorgaenger);
            if (Files.exists(path)) {
                Files.move(path, vorgaenger, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Audit-Rotation fehlgeschlagen — es wird weiter angehängt", e);
        }
        geschriebenBytes = 0;
    }

    private static void schliesseWriter() {
        if (dateiWriter != null) {
            try {
                dateiWriter.close();
            } catch (IOException e) {
                LOG.log(Level.WARNING, "Konnte Audit-Datei nicht schließen", e);
            }
            dateiWriter = null;
        }
    }
}
