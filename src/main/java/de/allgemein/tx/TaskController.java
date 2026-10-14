package de.allgemein.tx;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.logging.Logger;

/**
 * Task-Steuerung im Stil eines Transaktionsmonitors: Jede Arbeit wird als Task
 * eingereicht, die Anzahl paralleler Tasks ist begrenzt, ein Wachhund bricht Tasks
 * nach Ablauf des Zeitlimits ab (das Interrupt die laufende Transaktion rollt),
 * und die Statistik erfasst Durchlauf und Fehler. Deckt die CICS-Kernthemen
 * Task-Control und Monitoring für unseren Zweck ab.
 *
 * <p><b>Zeitlimit:</b> Der Wachhund-Timer wird erst gestartet, wenn der Task
 * tatsächlich zur Ausführung kommt — die Wartezeit in der Warteschlange zählt
 * bewusst nicht auf das Task-Zeitlimit, sonst würden harmlose Tasks beim ersten
 * Lastberg abgeschossen, obwohl sie selbst schnell laufen. <b>Budget-Semantik
 * (CR-047):</b> Das Zeitlimit gilt für die <em>gesamte</em> Task-Ausführung
 * inklusive aller Wiederholungsversuche des TxManager und deren Backoff-Pausen;
 * die {@link TxContext}-Deadline dagegen gilt pro Versuch. Ein Task mit
 * Sperren-Konflikten (ORA-00054/60/8177) kann daher vom Wachhund abgebrochen
 * werden, obwohl jeder Einzelversuch deutlich unter dem Zeitlimit blieb — wer
 * Wiederholungen mit Backoff erlaubt, sollte das Task-Zeitlimit entsprechend
 * großzügig wählen (Faustregel: timeout &ge; maxAttempts × Einzellaufzeit).</p>
 *
 * <p><b>Bekanntes Restfenster (ehrlich dokumentiert):</b> Der Abbruch geschieht
 * durch {@code future.cancel(true)}, also per Interrupt. Trifft der Interrupt den
 * Worker genau im Commit-Fenster — der JDBC-Commit ist bereits an den
 * Datenbankserver abgeschickt, der Interrupt macht sich erst danach bemerkbar —,
 * kann das Commit auf der Datenbank durchgehen, während der Aufrufer einen Abbruch
 * sieht. Ein Client, der daraufhin die Anfrage wiederholt, könnte dieselbe
 * Buchung doppelt auslösen. Dieses Fenster ist das klassische TP-Monitor-Problem
 * („in-doubt transaction“) und lässt sich clientseitig nicht schließen; vollständig
 * lösbar wäre es nur mit Transaktions-IDs und Recovery-Abfrage beim
 * Datenbankserver — bewusst außerhalb des Scopes dieser Steuerung. Praktische
 * Milderung: Der Wachhund greift erst nach Ablauf des Zeitlimits, und
 * {@link TaskHandle#result()} meldet Abbrüche als nicht wiederholbare
 * {@link TxException}, sodass der TxManager sie nicht automatisch erneut versucht.</p>
 */
public final class TaskController implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(TaskController.class.getName());

    /** Wie lange close() laufende Tasks maximal auslaufen lässt, bevor unterbrochen wird. */
    private static final int DRAIN_TIMEOUT_SEKUNDEN = 10;

    private final TxManager txManager;
    private final ThreadPoolExecutor executor;
    private final ScheduledThreadPoolExecutor watchdog;
    private final ConcurrentMap<UUID, TaskInfo> running = new ConcurrentHashMap<>();
    private final AtomicInteger workerCounter = new AtomicInteger();
    private final LongAdder completed = new LongAdder();
    private final LongAdder failed = new LongAdder();
    private final LongAdder timedOut = new LongAdder();
    private final LongAdder rejected = new LongAdder();
    private final LongAdder totalDurationNanos = new LongAdder();

    public TaskController(TxManager txManager, int maxParallelTasks, int queueCapacity) {
        this.txManager = txManager;
        this.executor = new ThreadPoolExecutor(
                maxParallelTasks, maxParallelTasks, 60, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                this::newWorkerThread,
                new ThreadPoolExecutor.AbortPolicy());
        this.executor.allowCoreThreadTimeOut(true);
        this.watchdog = new ScheduledThreadPoolExecutor(1, TaskController::newWatchdogThread);
        // Abgeschlossene Kurzläufer dürfen ihre Timer nicht bis zur ursprünglichen
        // Deadline in der Queue halten (Durchsatz × Timeout wäre sonst der Speicherbedarf).
        this.watchdog.setRemoveOnCancelPolicy(true);
    }

    /**
     * Reicht eine Transaktion als Task ein; die Ausführung beginnt sofort oder in
     * der Warteschlange. Das Zeitlimit beginnt erst mit dem Ausführungsbeginn
     * (siehe Klassen-Dokumentation zum Restfenster beim Abbruch).
     */
    public <T> TaskHandle<T> submit(String name, TxOptions options, TxWork<T> work) {
        UUID id = UUID.randomUUID();
        TaskInfo info = new TaskInfo(id, name, Instant.now());
        running.put(id, info);

        TaskExecution<T> task = new TaskExecution<>(options, work, info);
        FutureTask<T> future = new FutureTask<>(task) {
            @Override
            protected void done() {
                if (isCancelled()) {
                    executor.remove(this);
                }
                if (isCancelled() && info.settle()) {
                    running.remove(id);
                    if (info.timedOut()) {
                        timedOut.increment();
                    } else {
                        failed.increment();
                    }
                }
            }
        };
        task.future = future;

        try {
            executor.execute(future);
        } catch (RejectedExecutionException e) {
            running.remove(id);
            rejected.increment();
            throw TxException.nonRetryable(
                    "Task '" + name + "' abgelehnt: Warteschlange voll oder Steuerung bereits beendet", e);
        }

        return new TaskHandle<>(id, name, future);
    }

    public TaskStats stats() {
        long finished = completed.sum() + failed.sum() + timedOut.sum();
        double avgMillis = finished == 0 ? 0.0 : totalDurationNanos.sum() / (double) finished / 1_000_000.0;
        return new TaskStats(running.size(), completed.sum(), failed.sum(), timedOut.sum(), rejected.sum(), avgMillis);
    }

    public int poolSize() {
        return executor.getPoolSize();
    }

    public int queueSize() {
        return executor.getQueue().size();
    }

    public int queueRemainingCapacity() {
        return executor.getQueue().remainingCapacity();
    }

    private volatile javax.management.ObjectName jmxObjectName;

    public synchronized javax.management.ObjectName registerJmx() {
        if (this.jmxObjectName == null) {
            this.jmxObjectName = de.allgemein.tx.monitoring.TxMonitoring.registerTaskController(this);
        }
        return this.jmxObjectName;
    }

    /**
     * Beendet die Steuerung geordnet:
     * <ol>
     *   <li>{@code shutdown()} — keine neuen Tasks mehr, laufende und bereits in
     *       der Warteschlange stehende Tasks dürfen auslaufen (Commit/Rollback
     *       fertig stellen);</li>
     *   <li>bis zu {@value #DRAIN_TIMEOUT_SEKUNDEN} Sekunden warten;</li>
     *   <li>erst dann {@code shutdownNow()} — verbleibende Tasks werden
     *       unterbrochen (die laufende Transaktion rollt zurück) und nochmals
     *       kurz gewartet;</li>
     *   <li>zuletzt den Wachhund hart stoppen.</li>
     * </ol>
     */
    @Override
    public void close() {
        if (jmxObjectName != null) {
            de.allgemein.tx.monitoring.TxMonitoring.unregister(jmxObjectName);
            jmxObjectName = null;
        }
        executor.shutdown();
        try {
            if (!executor.awaitTermination(DRAIN_TIMEOUT_SEKUNDEN, TimeUnit.SECONDS)) {
                cancelQueued(executor.shutdownNow());
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    LOG.warning("Task-Steuerung: Worker auch nach Unterbrechung noch aktiv");
                }
            }
        } catch (InterruptedException e) {
            cancelQueued(executor.shutdownNow());
            Thread.currentThread().interrupt();
        } finally {
            watchdog.shutdownNow();
        }
    }

    /** shutdownNow entfernt Wartende, führt aber weder run() noch cancel() für sie aus. */
    private static void cancelQueued(java.util.List<Runnable> queued) {
        for (Runnable task : queued) {
            if (task instanceof java.util.concurrent.Future<?> future) {
                future.cancel(false);
            }
        }
    }

    private Thread newWorkerThread(Runnable runnable) {
        Thread thread = new Thread(runnable, "tx-task-" + workerCounter.incrementAndGet());
        return thread;
    }

    private static Thread newWatchdogThread(Runnable runnable) {
        Thread thread = new Thread(runnable, "tx-task-watchdog");
        thread.setDaemon(true);
        return thread;
    }

    /**
     * Führt die Arbeit im Worker-Thread aus. Startet den Wachhund-Timer erst mit
     * dem tatsächlichen Ausführungsbeginn, rechnet das Task-Ende über das
     * settle()-Gate der TaskInfo genau einmal ab (Konkurrenten: Wachhund und
     * Abbruch über das Handle) und räumt am Ende Interrupt-Reste vom Worker-Thread.
     */
    private final class TaskExecution<T> implements Callable<T> {

        private final TxOptions options;
        private final TxWork<T> work;
        private final TaskInfo info;
        /** Wird im submit gesetzt, bevor der Task beim Executor eingereicht wird. */
        private volatile FutureTask<T> future;

        TaskExecution(TxOptions options, TxWork<T> work, TaskInfo info) {
            this.options = options;
            this.work = work;
            this.info = info;
        }

        @Override
        public T call() {
            long start = System.nanoTime();
            ScheduledFuture<?> timer = null;
            try {
                timer = watchdog.schedule(
                        () -> cancelOnTimeout(future, info),
                        options.timeout().toMillis(), TimeUnit.MILLISECONDS);
                T result = txManager.execute(options, work);
                if (info.settle()) {
                    completed.increment();
                    totalDurationNanos.add(System.nanoTime() - start);
                }
                return result;
            } catch (RuntimeException | Error e) {
                if (info.settle()) {
                    if (info.timedOut()) {
                        timedOut.increment();
                    } else {
                        failed.increment();
                    }
                    totalDurationNanos.add(System.nanoTime() - start);
                }
                throw e;
            } finally {
                if (timer != null) {
                    timer.cancel(false);
                }
                running.remove(info.id);
                // Interrupt-Rest konsumieren: Nach einem Abbruch (Timeout/cancel)
                // steht das Interrupt-Flag des Worker-Threads; bliebe es gesetzt,
                // erbte der nächste Task auf diesem Worker das Flag und schlüge
                // spontan fehl (z. B. sofortige InterruptedException im sleep).
                Thread.interrupted();
            }
        }
    }

    /**
     * Wachhund-Aktion: bricht den Task nach Ablauf des Zeitlimits per Interrupt ab
     * und räumt auf, falls der Task-Wrapper sein eigenes finally nicht (mehr)
     * erreicht — insbesondere, wenn der Task noch nie gestartet wurde oder der
     * Interrupt ihn mitten im Aufräumen erwischt. Das settle()-Gate stellt
     * sicher, dass Zähler und running-Map selbst bei Rennen mit dem Wrapper
     * genau einmal gepflegt werden.
     */
    private void cancelOnTimeout(FutureTask<?> future, TaskInfo info) {
        if (future.isDone()) {
            return;
        }
        info.markTimedOut();
        if (future.cancel(true) && info.settle()) {
            running.remove(info.id);
            timedOut.increment();
        }
    }
}
