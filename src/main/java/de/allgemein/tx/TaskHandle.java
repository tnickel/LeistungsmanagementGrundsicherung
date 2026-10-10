package de.allgemein.tx;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Handle auf einen eingereichten Task: Ergebnis abholen, abbrechen, Status prüfen. */
public final class TaskHandle<T> {

    private final UUID id;
    private final String name;
    private final Future<T> future;
    /**
     * Aufräum-Aktion für den Abbruch-Fall, dass der Task dadurch nie zur Ausführung
     * kommt (noch in der Warteschlange): der Controller entfernt ihn dann aus der
     * running-Map und rechnet ihn genau einmal ab. Darf {@code null} sein.
     */
    private final Runnable onAbandoned;

    TaskHandle(UUID id, String name, Future<T> future) {
        this(id, name, future, null);
    }

    TaskHandle(UUID id, String name, Future<T> future, Runnable onAbandoned) {
        this.id = id;
        this.name = name;
        this.future = future;
        this.onAbandoned = onAbandoned;
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

    /**
     * Bricht den Task ab; die unterbrochene Transaktion rollt die Datenbank zurück.
     * Lief der Task noch nicht (Warteschlange), läuft sein Aufräum-Code nie — deshalb
     * wird hier zusätzlich {@code onAbandoned} ausgeführt, damit die Steuerung den
     * Task trotzdem aus ihrer Statistik und running-Map nimmt.
     */
    public boolean cancel() {
        boolean abgebrochen = future.cancel(true);
        if (abgebrochen && onAbandoned != null) {
            onAbandoned.run();
        }
        return abgebrochen;
    }

    /** Blockiert, bis der Task fertig ist. Fachliche Laufzeitfehler kommen typsicher durch. */
    public T result() {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw TxException.nonRetryable("Warten auf Task '" + name + "' unterbrochen", e);
        } catch (CancellationException e) {
            // Abbruch durch Wachhund (Timeout) oder cancel(): als klar benannte,
            // nicht wiederholbare Ausnahme melden statt roher CancellationException
            // (deren Message ist null und würde beim Aufrufer als „Technischer
            // Fehler: null“ durchschlagen).
            throw TxException.nonRetryable("Task abgebrochen (Timeout oder Abbruch): " + name, e);
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
        } catch (CancellationException e) {
            throw TxException.nonRetryable("Task abgebrochen (Timeout oder Abbruch): " + name, e);
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
