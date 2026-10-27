package de.allgemein.testsweet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class SweetMainCommandTest {
    @TempDir Path temp;

    @Test void documentedCommandsRemainAcceptedWithoutStartingAnything() {
        for (String[] args : List.of(new String[0], new String[]{"run"}, new String[]{"WEB"},
                new String[]{"run-web", "2", "100", "42", "0", "POSITIV"},
                new String[]{"certificate-worker", "fixture"}))
            assertDoesNotThrow(() -> SweetMain.validateCommand(args));
    }

    @Test void unknownOrTrailingCliArgumentsExitTwoBeforeAnyGuiOrRunStarts() throws Exception {
        String cp = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        // Ein fehlerhafter GUI-Fallback würde am belegten Port abbrechen, ohne ein Browserfenster zu öffnen.
        try (ServerSocket occupied = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            int number = 0;
            for (List<String> args : List.of(List.of("rnu"), List.of("--unknown"),
                    List.of("run", "1", "1", "42", "0", "POSITIV", "unexpected"),
                    List.of("certificate-worker"))) {
                var command = new ArrayList<>(List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                        "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8",
                        "-cp", cp, SweetMain.class.getName()));
                command.addAll(args);
                Path output = temp.resolve("command-" + ++number + ".log");
                ProcessBuilder builder = new ProcessBuilder(command).directory(temp.toFile())
                        .redirectErrorStream(true).redirectOutput(output.toFile());
                builder.environment().put("SWEET_PORT", String.valueOf(occupied.getLocalPort()));
                Process process = builder.start();
                try {
                    assertTrue(process.waitFor(20, TimeUnit.SECONDS), "CLI blieb trotz ungültigem Aufruf aktiv");
                    String log = Files.readString(output);
                    assertEquals(2, process.exitValue(), log);
                    assertTrue(log.contains("Ungültige Konfiguration"), log);
                    if (!args.get(0).equals("certificate-worker")) assertTrue(log.contains("Aufruf:"), log);
                    assertFalse(log.contains("Simulator läuft auf"), log);
                    assertFalse(log.contains("Starte Batch-Lauf"), log);
                    assertFalse(Files.exists(temp.resolve("testsweet/logs")));
                } finally {
                    if (process.isAlive()) { process.destroyForcibly(); process.waitFor(5, TimeUnit.SECONDS); }
                }
            }
        }
    }
}
