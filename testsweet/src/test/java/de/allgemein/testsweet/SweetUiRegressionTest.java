package de.allgemein.testsweet;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

/** Prüft die vollständige produktive Browserdatei über kontrollierte API-/DOM-Gegenproben. */
class SweetUiRegressionTest {
    @Test void completeBrowserScriptFailsClosedForFindingsMissingEvidenceAndStaleResponses() throws Exception {
        Path script=Path.of("src/test/js/llm-ui.test.js");
        if(!Files.isRegularFile(script))script=Path.of("testsweet/src/test/js/llm-ui.test.js");
        assertTrue(Files.isRegularFile(script));
        Path output=Files.createTempFile("sweet-ui-regression-", ".log");
        Process process=null;
        try {
            process=new ProcessBuilder("node","--test",script.toAbsolutePath().toString()).redirectErrorStream(true).redirectOutput(output.toFile()).start();
            assertTrue(process.waitFor(30,TimeUnit.SECONDS),"Browser-Gegenproben haben das Zeitlimit überschritten");
            assertEquals(0,process.exitValue(),Files.readString(output,StandardCharsets.UTF_8));
        } finally {
            if(process!=null&&process.isAlive()){process.destroyForcibly();process.waitFor(5,TimeUnit.SECONDS);}
            Files.deleteIfExists(output);
        }
    }
}
