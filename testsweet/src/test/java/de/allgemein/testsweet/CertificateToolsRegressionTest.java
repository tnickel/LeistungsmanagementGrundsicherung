package de.allgemein.testsweet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/** Bindet die unabhängigen Zertifikats- und Antwortprüfer an die verpflichtende Maven-Suite. */
class CertificateToolsRegressionTest {
    @TempDir Path temporary;

    @Test
    void certificateEvidenceReferenceAndAnswerParityMustPassWithoutSkippedChecks() throws Exception {
        Path root = BuildEvidence.currentRoot();
        List<String> command = new ArrayList<>(List.of("node", "--test", "--test-reporter=tap"));
        for (String name : List.of("testzertifikat-werkzeug/certificate-evidence.test.js", "testzertifikat-werkzeug/llm-reference.test.js",
                "testzertifikat-werkzeug/llm-answer-parity.test.js", "testreport-werkzeug/report-tools.test.js")) {
            Path script = root.resolve("doc").resolve(name);
            assertTrue(Files.isRegularFile(script), "Verpflichtender Zertifikatsprüfer fehlt: " + name);
            command.add(script.toString());
        }
        Path output = temporary.resolve("certificate-tools.tap");
        Process process = new ProcessBuilder(command).directory(root.toFile())
                .redirectErrorStream(true).redirectOutput(output.toFile()).start();
        try {
            assertTrue(process.waitFor(180, TimeUnit.SECONDS),
                    "Zertifikatsprüfer haben das Zeitlimit überschritten");
            String tap = Files.readString(output, StandardCharsets.UTF_8);
            assertEquals(0, process.exitValue(), tap);
            int tests = count(tap, "tests");
            assertTrue(tests > 0, "Kein ausgeführter Node-Prüffall belegt");
            assertEquals(tests, count(tap, "pass"), tap);
            for (String category : List.of("fail", "cancelled", "skipped", "todo")) {
                assertEquals(0, count(tap, category), tap);
            }
        } finally {
            if (process.isAlive()) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
            }
        }
    }

    private static int count(String tap, String category) {
        var match = Pattern.compile("^# " + category + " (\\d+)\\s*$", Pattern.MULTILINE).matcher(tap);
        assertTrue(match.find(), "Node-Prüfzusammenfassung fehlt: " + category);
        return Integer.parseInt(match.group(1));
    }
}
