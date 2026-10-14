package de.allgemein.testsweet;

import de.allgemein.lmg.web.Json;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class NachweisIntegritaetTest {
    @TempDir Path temp;

    @Test void skalarVerlangtGenauEineZeileAuchBeiPassenderErsterZeile() throws Exception {
        assertFalse(pruefeSkalar(List.of()).ok());
        assertTrue(pruefeSkalar(List.of("42")).ok());
        var duplicate = pruefeSkalar(List.of("42", "42"));
        assertFalse(duplicate.ok());
        assertEquals("<mehrere Zeilen>", duplicate.ist());
    }

    private Auftrag.Pruefung pruefeSkalar(List<String> rows) throws Exception {
        AtomicInteger cursor = new AtomicInteger(-1);
        ResultSet result = proxy(ResultSet.class, (object, method, args) -> switch (method.getName()) {
            case "next" -> cursor.incrementAndGet() < rows.size();
            case "getObject" -> rows.get(cursor.get());
            case "close" -> null;
            default -> throw new UnsupportedOperationException(method.getName());
        });
        PreparedStatement statement = proxy(PreparedStatement.class, (object, method, args) -> switch (method.getName()) {
            case "executeQuery" -> result;
            case "close" -> null;
            default -> throw new UnsupportedOperationException(method.getName());
        });
        Connection connection = proxy(Connection.class, (object, method, args) -> switch (method.getName()) {
            case "prepareStatement" -> statement;
            default -> throw new UnsupportedOperationException(method.getName());
        });
        var method = Verifikator.class.getDeclaredMethod("pruefe", Connection.class, Auftrag.class, Erwartung.class);
        method.setAccessible(true);
        return (Auftrag.Pruefung) method.invoke(new Verifikator("", "", ""), connection, order(),
                Erwartung.text("Wert", "irrelevant", context -> List.of(), "42"));
    }

    @Test void asynchronerSchreibverlustBleibtNachCloseAlsFehlerBestehen() throws Exception {
        SweetLog log = new SweetLog(temp);
        var field = SweetLog.class.getDeclaredField("simulation");
        field.setAccessible(true);
        ((BufferedWriter) field.get(log)).close();
        log.ereignis("TEST", "", "Dieser Nachweis geht verloren");
        log.close();
        assertNotNull(log.schreibfehler());
        assertFalse(log.vollstaendig());
        var first = log.schreibfehler();
        log.close();
        assertSame(first, log.schreibfehler());
    }

    @Test void fehlerErstBeimDateiabschlussEntwertetDenLauf() throws Exception {
        Simulator simulator = new Simulator();
        SweetLog log = new SweetLog(temp);
        var field = SweetLog.class.getDeclaredField("dateien");
        field.setAccessible(true);
        @SuppressWarnings("unchecked") var files = (Map<String, BufferedWriter>) field.get(log);
        files.put("close-failure", new BufferedWriter(new Writer() {
            public void write(char[] chars, int offset, int length) { }
            public void flush() { }
            public void close() throws IOException { throw new IOException("CONTROLLED_CLOSE_FAILURE"); }
        }));
        set(simulator, "log", log);
        set(simulator, "auftraege", List.of(order()));
        set(simulator, "phase", Simulator.Phase.FERTIG);
        assertTrue(simulator.laufErfolgreich());
        log.close();
        assertFalse(simulator.laufErfolgreich());
        assertTrue(simulator.nachweisFehler().stream().anyMatch(s -> s.contains("CONTROLLED_CLOSE_FAILURE")));
    }

    @Test void exportfehlerNachGruenemVorgaengerLiefertKeinenAltenReport() throws Exception {
        Simulator simulator = new Simulator();
        try (SweetLog log = new SweetLog(temp)) {
            Path old = temp.resolve("alter-gruener-report.pdf");
            Files.writeString(old, "historisch");
            set(simulator, "letztesPdf", old.toString());
            set(simulator, "reportsComplete", true);
            set(simulator, "log", log);
            set(simulator, "auftraege", List.of(order()));
            set(simulator, "phase", Simulator.Phase.FERTIG);
            set(simulator, "laufStart", LocalDateTime.now());
            Files.createDirectory(log.runVerzeichnis.resolve("testdokumentation.pdf"));
            Files.writeString(log.runVerzeichnis.resolve("testdokumentation.pdf/blocker"), "verhindert Ersetzen");
            var method = Simulator.class.getDeclaredMethod("schreibeTestdokumentation");
            method.setAccessible(true);
            method.invoke(simulator);
            assertFalse(simulator.laufErfolgreich());
            assertFalse(simulator.nachweiseVollstaendig());
            assertNull(simulator.letztesPdf());
            assertNull(simulator.letztesMd());
            assertFalse(simulator.nachweisFehler().isEmpty());
        }
    }

    @Test void startupFehlerHatEinenTerminalenNachweisStattAltemPass() throws Exception {
        String oldDirectory = System.getProperty("user.dir"), oldUrl = System.getProperty("tx.url");
        try {
            System.setProperty("user.dir", temp.toString());
            System.setProperty("tx.url", "jdbc:controlled-startup-failure:");
            Simulator simulator = new Simulator();
            assertThrows(Exception.class, () -> simulator.laufSynchron(new Simulator.LaufKonfiguration(
                    1, 1, 42, 0, false, Simulator.Kategorie.POSITIV)));
            assertFalse(simulator.laufErfolgreich());
            Path marker = Path.of(simulator.letztesLogVerzeichnis()).resolve("run-state.json");
            assertTrue(Files.isRegularFile(marker));
            var state = (Map<?, ?>) Json.parse(Files.readString(marker));
            assertEquals("ABORTED", state.get("state"));
            assertEquals(false, state.get("success"));
            assertNotNull(state.get("completedAt"));
            assertEquals("FACHSCHICHT", state.get("mode"));
        } finally {
            System.setProperty("user.dir", oldDirectory);
            if (oldUrl == null) System.clearProperty("tx.url"); else System.setProperty("tx.url", oldUrl);
        }
    }

    @Test void neustartFaelltBeiNeuemUnvollstaendigenLaufNichtAufAltesPdfZurueck() throws Exception {
        String oldDirectory = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", temp.toString());
            Path logs = temp.resolve("testsweet/logs"), old = logs.resolve("run-old"), latest = logs.resolve("run-new");
            Files.createDirectories(old);
            Path pdf = old.resolve("testdokumentation.pdf");
            Files.writeString(pdf, "historisch");
            Files.writeString(old.resolve("run-state.json"), Json.write(Map.of("startedAt", "2026-01-01T12:00:00",
                    "reportsComplete", true, "reports", List.of(Map.of("path", "testdokumentation.pdf", "sha256", "irrelevant")))));
            Files.createDirectories(latest);
            Files.writeString(latest.resolve("run-state.json"), Json.write(Map.of("startedAt", "2026-01-02T12:00:00",
                    "state", "RUNNING", "reportsComplete", false)));
            Simulator simulator = new Simulator();
            assertNull(simulator.letztesPdf());
            assertNull(simulator.letztesMd());
        } finally { System.setProperty("user.dir", oldDirectory); }
    }

    private static Auftrag order() {
        Auftrag order = new Auftrag(1, "TEST", "SWEET-TEST", "Test", "", List.of(), List.of());
        order.status = Auftrag.Status.VERIFIZIERT;
        return order;
    }
    private static void set(Object object, String name, Object value) throws Exception {
        var field = object.getClass().getDeclaredField(name); field.setAccessible(true); field.set(object, value);
    }
    private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }
}
