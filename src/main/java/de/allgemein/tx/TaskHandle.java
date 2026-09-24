package de.allgemein.tx;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Handle auf einen eingereichten Task: Ergebnis abholen, abbrechen, Status prüfen. */
public final class TaskHandle<T> {

    private final UUID id;
    private final String name;
    private final Future<T> future;

    TaskHandle(UUID id, String name, Future<T> future) {
        this.id = id;
        this.name = name;
        this.future = future;
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public boolean isDone() {
        return future.isDone();
    }

    /** Bricht den Task ab; die unterbrochene Transaktion rollt die Datenbank zurück. */
    public boolean cancel() {
        return future.cancel(true);
    }

    /** Blockiert, bis der Task fertig ist. Fachliche Laufzeitfehler kommen typsicher durch. */
    public T result() {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw TxException.nonRetryable("Warten auf Task '" + name + "' unterbrochen", e);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException laufzeitfehler) {
                throw laufzeitfehler;
            }
            throw TxException.nonRetryable("Task '" + name + "' fehlgeschlagen: " + e.getMessage(), e);
        }
    }

    /** Blockiert höchstens die angegebene Zeit; danach wird der Task abgebrochen. */
    public T resultOrCancelAfter(Duration timeout) {
        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw TxException.nonRetryable("Warten auf Task '" + name + "' unterbrochen", e);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException laufzeitfehler) {
                throw laufzeitfehler;
            }
            throw TxException.nonRetryable("Task '" + name + "' fehlgeschlagen: " + e.getMessage(), e);
        } catch (TimeoutException e) {
            cancel();
            throw TxException.nonRetryable("Task '" + name + "' zu langsam, wurde abgebrochen: " + e.getMessage(), e);
        }
    }
}
