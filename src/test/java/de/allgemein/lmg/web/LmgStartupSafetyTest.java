package de.allgemein.lmg.web;

import de.allgemein.lmg.config.LmgConfig;
import de.allgemein.lmg.saga.AntragsSagaProzess;
import de.allgemein.lmg.service.Systemdiagnose;
import de.allgemein.tx.demo.AntragsDemoProzess;
import de.allgemein.tx.saga.SagaEngine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Startfehler dürfen weder einen grünen Banner noch offene Server-/Poolressourcen hinterlassen. */
class LmgStartupSafetyTest {
    @Test void recoveryErrorsAndFailedCompensationsCannotPassStartup() {
        for (var bad : List.of(new SagaEngine.RecoveryReport(0, 0, 0, 1),
                new SagaEngine.RecoveryReport(0, 0, 1, 0))) {
            var reports = Map.of(AntragsSagaProzess.definition().type(), bad,
                    AntragsDemoProzess.definition().type(), new SagaEngine.RecoveryReport(0, 0, 0, 0));
            assertThrows(IllegalStateException.class, () -> LmgServerMain.requireSuccessfulRecovery(
                    new Systemdiagnose.WiederanlaufErgebnis(reports)));
        }
        assertThrows(IllegalStateException.class, () -> LmgServerMain.requireSuccessfulRecovery(
                new Systemdiagnose.WiederanlaufErgebnis(Map.of())));
        assertEquals(3, LmgServerMain.requireSuccessfulRecovery(new Systemdiagnose.WiederanlaufErgebnis(Map.of(
                AntragsSagaProzess.definition().type(), new SagaEngine.RecoveryReport(1, 2, 0, 0),
                AntragsDemoProzess.definition().type(), new SagaEngine.RecoveryReport(0, 0, 0, 0)))));
    }

    @Test void actualMainStopsAndClosesPoolWhenRecoveryScanFails() throws Exception { runChild("main", "-"); }

    @Test void utf8BomDoesNotHideTheFirstConfigurationKey(@TempDir Path dir) throws Exception {
        var file = dir.resolve("bom.properties");
        Files.writeString(file, "\uFEFFlmg.pool.type=simple\nlmg.behoerde.ort=Böblingen\n", StandardCharsets.UTF_8);
        runChild("bom", file.toString());
    }

    @Test void malformedPropertiesCannotFallBackToPartiallyReadValues(@TempDir Path dir) throws Exception {
        var file = dir.resolve("malformed.properties");
        Files.writeString(file, "lmg.pool.size=2\nlmg.benutzer=custom:secret:admin\nbroken=\\uNOTHEX\n");
        runChild("invalid-file", file.toString());
        var invalidUtf8 = dir.resolve("invalid-utf8.properties");
        Files.write(invalidUtf8, new byte[]{'l', 'm', 'g', '.', 'p', 'o', 'r', 't', '=', (byte) 0xC3, (byte) 0x28});
        runChild("invalid-file", invalidUtf8.toString());
    }

    @Test void unreadableConfigurationAndUnknownPoolTypeAbort(@TempDir Path dir) throws Exception {
        runChild("invalid-file", dir.toString()); // vorhandenes Verzeichnis ist keine lesbare Properties-Datei
        var file = dir.resolve("pool-typo.properties");
        Files.writeString(file, "lmg.pool.type=ucpp\n");
        runChild("invalid-value", file.toString());
        Files.writeString(file, "lmg.tasks.max=2147483648\n");
        runChild("invalid-value", file.toString());
    }

    private static void runChild(String mode, String path) throws Exception {
        var process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")),
                Probe.class.getName(), mode, path).redirectErrorStream(true);
        process.environment().keySet().removeIf(key -> key.startsWith("LMG_") || key.startsWith("TX_"));
        var child = process.start();
        try {
            assertTrue(child.waitFor(15, TimeUnit.SECONDS), "Startfehlerprüfung hängt");
            String output = new String(child.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertEquals(0, child.exitValue(), output);
            assertTrue(output.contains("STARTUP_PROBE_OK"), output);
        } finally { if (child.isAlive()) child.destroyForcibly(); }
    }

    public static final class Probe {
        public static void main(String[] args) throws Exception {
            switch (args[0]) {
                case "bom" -> {
                    var config = LmgConfig.load(args[1]);
                    if (!config.poolType().equals("simple") || !config.rechtsbehelf().ort().equals("Böblingen")) {
                        throw new AssertionError("BOM/UTF-8-Konfiguration wurde ignoriert");
                    }
                }
                case "invalid-file" -> {
                    try { LmgConfig.load(args[1]); throw new AssertionError("Defekte Datei lieferte Defaults"); }
                    catch (IllegalStateException expected) { }
                }
                case "invalid-value" -> {
                    try { LmgConfig.load(args[1]); throw new AssertionError("Ungültige Konfiguration akzeptiert"); }
                    catch (IllegalArgumentException expected) { }
                }
                case "main" -> mainWithFailedRecovery();
                default -> throw new AssertionError(args[0]);
            }
            System.out.println("STARTUP_PROBE_OK");
        }

        private static void mainWithFailedRecovery() throws Exception {
            var opened = new AtomicInteger();
            var closed = new AtomicInteger();
            Driver driver = new Driver() {
                @Override public Connection connect(String url, Properties info) throws SQLException {
                    if (!acceptsURL(url)) return null;
                    opened.incrementAndGet();
                    boolean[] state = {true, false}; // autoCommit, closed
                    return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
                            (proxy, method, args) -> switch (method.getName()) {
                                case "hashCode" -> System.identityHashCode(proxy);
                                case "equals" -> proxy == args[0];
                                case "toString" -> "ReviewConnection";
                                case "prepareStatement" -> throw new SQLException("Synthetic recovery scan failure", "08006", 17002);
                                case "getAutoCommit" -> state[0];
                                case "setAutoCommit" -> { state[0] = (Boolean) args[0]; yield null; }
                                case "isClosed" -> state[1];
                                case "close" -> { if (!state[1]) closed.incrementAndGet(); state[1] = true; yield null; }
                                case "isValid" -> !state[1];
                                case "getTransactionIsolation" -> Connection.TRANSACTION_READ_COMMITTED;
                                case "unwrap" -> null;
                                case "isWrapperFor" -> false;
                                default -> null;
                            });
                }
                @Override public boolean acceptsURL(String url) { return url.startsWith("jdbc:review:"); }
                @Override public java.sql.DriverPropertyInfo[] getPropertyInfo(String u, Properties p) { return new java.sql.DriverPropertyInfo[0]; }
                @Override public int getMajorVersion() { return 1; }
                @Override public int getMinorVersion() { return 0; }
                @Override public boolean jdbcCompliant() { return false; }
                @Override public java.util.logging.Logger getParentLogger() { return java.util.logging.Logger.getGlobal(); }
            };
            DriverManager.registerDriver(driver);
            int port;
            try (var reserve = new java.net.ServerSocket(0, 0, java.net.InetAddress.getLoopbackAddress())) { port = reserve.getLocalPort(); }
            System.setProperty("tx.url", "jdbc:review:startup");
            System.setProperty("lmg.pool.type", "simple");
            System.setProperty("lmg.pool.size", "3");
            System.setProperty("lmg.tasks.max", "1");
            System.setProperty("lmg.port", Integer.toString(port));
            System.setProperty("lmg.audit.file", Files.createTempFile("startup-audit-", ".log").toString());
            var banner = new java.io.ByteArrayOutputStream();
            var original = System.out;
            try {
                System.setOut(new java.io.PrintStream(banner, true, StandardCharsets.UTF_8));
                try { LmgServerMain.main(new String[0]); throw new AssertionError("Main startete trotz fehlgeschlagenem Recovery"); }
                catch (IllegalStateException expected) {
                    if (!expected.getMessage().contains("Wiederanlauf")) throw expected;
                }
            } finally { System.setOut(original); DriverManager.deregisterDriver(driver); }
            if (opened.get() == 0 || closed.get() != opened.get()) throw new AssertionError("Poolressourcen nicht geschlossen");
            if (banner.toString(StandardCharsets.UTF_8).contains("Oberfläche:")) throw new AssertionError("Falscher Erfolgsbanner");
            try (var rebound = new java.net.ServerSocket(port, 0, java.net.InetAddress.getLoopbackAddress())) { }
        }
    }
}
