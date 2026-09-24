package de.allgemein.tx;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

/**
 * Task-Steuerung im Stil eines Transaktionsmonitors: Jede Arbeit wird als Task
 * eingereicht, die Anzahl paralleler Tasks ist begrenzt, ein Wachhund bricht Tasks
 * nach Ablauf des Zeitlimits ab (das Interrupt die laufende Transaktion rollt),
 * und die Statistik erfasst Durchlauf und Fehler. Deckt die CICS-Kernthemen
 * Task-Control und Monitoring für unseren Zweck ab.
 */
public final class TaskController implements AutoCloseable {

    private final TxManager txManager;
    private final ThreadPoolExecutor executor;
    private final ScheduledExecutorService watchdog;
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
        this.watchdog = Executors.newSingleThreadScheduledExecutor(TaskController::newWatchdogThread);
    }

    /** Reicht eine Transaktion als Task ein; die Ausführung beginnt sofort oder in der Warteschlange. */
    public <T> TaskHandle<T> submit(String name, TxOptions options, TxWork<T> work) {
        UUID id = UUID.randomUUID();
        TaskInfo info = new TaskInfo(id, name, Instant.now());
        running.put(id, info);

        FutureTask<T> future = new FutureTask<>(() -> {
            long start = System.nanoTime();
            try {
                T result = txManager.execute(options, work);
                completed.increment();
                return result;
            } catch (RuntimeException e) {
                if (info.timedOut()) {
                    timedOut.increment();
                } else {
                    failed.increment();
                }
                throw e;
            } finally {
                totalDurationNanos.add(System.nanoTime() - start);
                running.remove(id);
            }
        });

        try {
            executor.execute(future);
        } catch (RejectedExecutionException e) {
            running.remove(id);
            rejected.increment();
            throw TxException.nonRetryable(
                    "Task '" + name + "' abgelehnt: Warteschlange voll oder Steuerung bereits beendet", e);
        }

        watchdog.schedule(() -> {
            if (!future.isDone()) {
                info.markTimedOut();
                future.cancel(true);
            }
        }, options.timeout().toMillis(), TimeUnit.MILLISECONDS);

        return new TaskHandle<>(id, name, future);
    }

    public TaskStats stats() {
        long finished = completed.sum() + failed.sum() + timedOut.sum();
        double avgMillis = finished == 0 ? 0.0 : totalDurationNanos.sum() / (double) finished / 1_000_000.0;
        return new TaskStats(running.size(), completed.sum(), failed.sum(), timedOut.sum(), rejected.sum(), avgMillis);
    }

    @Override
    public void close() {
        executor.shutdownNow();
        watchdog.shutdownNow();
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
}
