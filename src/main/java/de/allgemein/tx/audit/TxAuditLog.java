package de.allgemein.tx.audit;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
 * in eine Audit-Logdatei auf Platte geschrieben.</p>
 */
public final class TxAuditLog {

    private static final Logger LOG = Logger.getLogger("de.allgemein.tx.audit");
    private static final int MAX_RING_BUFFER = 1000;
    private static final AtomicLong ID_GENERATOR = new AtomicLong(1);

    private static final ConcurrentLinkedDeque<TxAuditEvent> RING_BUFFER = new ConcurrentLinkedDeque<>();
    private static final List<TxAuditListener> LISTENERS = new CopyOnWriteArrayList<>();
    private static volatile Path auditFilePath = null;
    private static final Object FILE_LOCK = new Object();

    private TxAuditLog() {
    }

    /** Konfiguriert den Pfad zur Audit-Logdatei (null deaktiviert Datei-Logging). */
    public static void setAuditFilePath(Path path) {
        auditFilePath = path;
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

    public static void clearForTests() {
        RING_BUFFER.clear();
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

    public static void recordSagaStart(String sagaId, String sagaType, String payload) {
        record(new TxAuditEvent(ID_GENERATOR.getAndIncrement(), Instant.now(), Thread.currentThread().getName(),
                "SAGA", "START", sagaId, 0, -1, "type=" + sagaType + " payload=" + payload, null));
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
        // 1. Ringpuffer (neueste zuerst)
        RING_BUFFER.addFirst(event);
        while (RING_BUFFER.size() > MAX_RING_BUFFER) {
            RING_BUFFER.pollLast();
        }

        // 2. Logger
        String line = event.toLogLine();
        LOG.info(line);

        // 3. Optionaler Dateiausschrieb
        Path path = auditFilePath;
        if (path != null) {
            synchronized (FILE_LOCK) {
                try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.WRITE)) {
                    writer.write(line);
                    writer.newLine();
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
}
