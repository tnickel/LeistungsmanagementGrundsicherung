package de.allgemein.testsweet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Betriebsskripte mit fiktiven Prozessen und gemocktem Docker; kein echter Stopp. */
@EnabledOnOs(OS.WINDOWS)
class OperatingScriptsRegressionTest {
    @Test void processIdentityAndDatabaseReadinessBoundaries() throws Exception {
        Path root = BuildEvidence.currentRoot();
        Path output = Files.createTempFile("lmg-operating-tests-", ".txt");
        Process process = null;
        try {
            process = new ProcessBuilder("powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass",
                    "-File", root.resolve("tools/tests/operating-scripts.test.ps1").toString(),
                    "-ProjectRoot", root.toString()).redirectErrorStream(true).redirectOutput(output.toFile()).start();
            assertTrue(process.waitFor(30, TimeUnit.SECONDS), "Betriebsskript-Regression hat das Zeitlimit erreicht");
            String result = Files.readString(output, StandardCharsets.UTF_8);
            assertEquals(0, process.exitValue(), result);
            assertTrue(result.contains("PASS:"), result);
        } finally {
            if (process != null && process.isAlive()) process.destroyForcibly();
            Files.deleteIfExists(output);
        }
    }
}
