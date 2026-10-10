package de.allgemein.tx;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests für die Task-Steuerung ohne echte Datenbank (der TxManager erhält einen
 * Stub-Pool mit Fake-Verbindungen). Im Fokus: Wachhund bricht lange Tasks ab und
 * rechnet sie genau einmal ab, keine Leaks in der running-Map, geordnetes
 * close() und das Räumen des Interrupt-Flags auf dem Worker-Thread.
 */
class TaskControllerTest {

    /** Stub-Pool: TxManager bekommt immer eine frische, harmlose Fake-Verbindung. */
    private static final ConnectionPool STUB_POOL = new ConnectionPool() {
        @Override
        public Connection getConnection() {
            return (Connection) Proxy.newProxyInstance(
                    TaskControllerTest.class.getClassLoader(),
                    new Class<?>[] {Connection.class},
                    (proxy, methode, args) -> switch (methode.getName()) {
                        case "isClosed" -> false;
                        case "isValid" -> true;
                        case "getAutoCommit" -> true;
                        default -> null;
                    });
        }

        @Override
        public void release(Connection connection) {
            // nichts zu tun — Fake-Verbindung
        }

        @Override
        public void close() {
            // nichts zu tun
        }
    };

    @Test
    void wachundBrichtLangenTaskAb() throws Exception {
        try (TaskController controller = new TaskController(new TxManager(STUB_POOL), 2, 10)) {
            CountDownLatch laufend = new CountDownLatch(1);
            TaskHandle<String> handle = controller.submit("langlaufend",
                    TxOptions.DEFAULT.withTimeout(Duration.ofMillis(300)),
                    ctx -> {
                        laufend.countDown();
                        Thread.sleep(5_000);
                        return "zu spaet";
                    });
            assertTrue(laufend.await(5, TimeUnit.SECONDS), "Task muss gestartet sein");

            TxException fehler = assertThrows(TxException.class, handle::result);
            assertTrue(fehler.getMessage().contains("abgebrochen"),
                    "Meldung sollte den Abbruch nennen: " + fehler.getMessage());

            // Statistik: genau ein Timeout, kein fehlgeschlagener Task, kein Leak
            assertTrue(warteAuf(() -> controller.stats().timedOut() == 1), "timedOut muss 1 werden");
            assertTrue(warteAuf(() -> controller.stats().active() == 0), "running-Map muss leer sein");
            TaskStats stats = controller.stats();
            assertEquals(1, stats.timedOut());
            assertEquals(0, stats.failed());
            assertEquals(0, stats.completed());
        }
    }

    @Test
    void warteschlangeVollWirftTxException() throws Exception {
        try (TaskController controller = new TaskController(new TxManager(STUB_POOL), 1, 1)) {
            CountDownLatch freigabe = new CountDownLatch(1);
            TaskHandle<String> laeufer = controller.submit("laeufer", TxOptions.DEFAULT, ctx -> {
                freigabe.await();
                return "laeufer-ok";
            });
            TaskHandle<String> warteschlangenTask =
                    controller.submit("warteschlange", TxOptions.DEFAULT, ctx -> "warteschlange-ok");

            // Queue (Kapazität 1) ist belegt: der dritte Submit wird abgelehnt
            TxException fehler = assertThrows(TxException.class,
                    () -> controller.submit("abgelehnt", TxOptions.DEFAULT, ctx -> "abgelehnt-ok"));
            assertTrue(fehler.getMessage().contains("Warteschlange voll") || fehler.getMessage().contains("abgelehnt"),
                    "Meldung sollte die volle Warteschlange nennen: " + fehler.getMessage());
            assertEquals(1, controller.stats().rejected());

            freigabe.countDown();
            assertEquals("laeufer-ok", laeufer.result());
            assertEquals("warteschlange-ok", warteschlangenTask.result());

            // der abgelehnte Task darf nicht in der running-Map hängen bleiben
            assertTrue(warteAuf(() -> controller.stats().active() == 0), "running-Map muss leer sein");
        }
    }

    /**
     * Abbruch eines Queue-Wartenden (sein Task-Wrapper läuft nie, also auch kein
     * finally): die Steuerung muss ihn trotzdem aus der running-Map nehmen und
     * genau einmal abrechnen — danach muss ein Folge-Task normal laufen.
     */
    @Test
    void nachTimeoutAufraeumen() throws Exception {
        try (TaskController controller = new TaskController(new TxManager(STUB_POOL), 1, 2)) {
            CountDownLatch freigabe = new CountDownLatch(1);
            TaskHandle<String> laeufer = controller.submit("laeufer", TxOptions.DEFAULT, ctx -> {
                freigabe.await();
                return "laeufer-ok";
            });
            TaskHandle<String> wartender = controller.submit("wartender", TxOptions.DEFAULT, ctx -> "wartender-ok");

            assertTrue(wartender.cancel(), "Queue-Wartender muss abbrechbar sein");
            assertEquals(1, controller.stats().failed(), "abgebrochener Wartender zählt genau einmal als failed");

            freigabe.countDown();
            assertEquals("laeufer-ok", laeufer.result());

            assertTrue(warteAuf(() -> controller.stats().active() == 0), "running-Map muss leer sein (kein Leak)");
            assertEquals(0, controller.stats().active());

            TaskHandle<String> nachfolger = controller.submit("nachfolger", TxOptions.DEFAULT, ctx -> "nachfolger-ok");
            assertEquals("nachfolger-ok", nachfolger.result());

            TaskStats stats = controller.stats();
            assertEquals(2, stats.completed(), "laeufer + nachfolger");
            assertEquals(1, stats.failed(), "wartender");
            assertEquals(0, stats.timedOut());
        }
    }

    @Test
    void closeBeendetTasksGeordnet() throws Exception {
        TaskController controller = new TaskController(new TxManager(STUB_POOL), 2, 10);
        TaskHandle<String> handle = controller.submit("kurzlaufend", TxOptions.DEFAULT, ctx -> {
            Thread.sleep(400);
            return "fertig";
        });

        controller.close(); // wartet auf den laufenden Task (awaitTermination)

        assertEquals("fertig", handle.result(), "close() muss den laufenden Task auslaufen lassen");
        TaskStats stats = controller.stats();
        assertEquals(1, stats.completed());
        assertEquals(0, stats.active());
    }

    @Test
    void interruptFlagWirdGeraeumt() throws Exception {
        // genau ein Worker-Thread: Abbruch-Task und Folge-Task laufen deterministisch auf demselben
        try (TaskController controller = new TaskController(new TxManager(STUB_POOL), 1, 5)) {
            AtomicReference<String> threadTask1 = new AtomicReference<>();
            CountDownLatch gestartet = new CountDownLatch(1);
            TaskHandle<String> abbruch = controller.submit("abbruch",
                    TxOptions.DEFAULT.withTimeout(Duration.ofMillis(200)),
                    ctx -> {
                        threadTask1.set(Thread.currentThread().getName());
                        gestartet.countDown();
                        Thread.sleep(3_000);
                        return "nie";
                    });
            assertTrue(gestartet.await(5, TimeUnit.SECONDS), "Task muss gestartet sein");
            assertThrows(TxException.class, abbruch::result); // Timeout-Abbruch per Interrupt

            AtomicReference<String> threadTask2 = new AtomicReference<>();
            TaskHandle<String> nachfolger = controller.submit("nachfolger", TxOptions.DEFAULT, ctx -> {
                threadTask2.set(Thread.currentThread().getName());
                Thread.sleep(100); // interrupt-sensitiv: mit Rest-Flag würde das sofort scheitern
                return "folge-ok";
            });

            assertEquals("folge-ok", nachfolger.result(),
                    "Rest-Interrupt-Flag darf den Folge-Task nicht stören");
            assertSame(threadTask1.get(), threadTask2.get(),
                    "beide Tasks müssen für den Beweis auf demselben Worker-Thread laufen");
        }
    }

    /** Wartet bis zu 5 Sekunden auf eine Bedingung (Statistik läuft asynchron nach). */
    private static boolean warteAuf(BooleanSupplier bedingung) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000;
        while (!bedingung.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                return false;
            }
            Thread.sleep(10);
        }
        return true;
    }
}
