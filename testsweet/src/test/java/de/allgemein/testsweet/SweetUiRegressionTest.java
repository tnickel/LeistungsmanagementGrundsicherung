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
        runBrowserChecks("llm-ui.test.js");
    }

    @Test void productBrowserKeepsPaymentIdentityAndReloadsUnknownOutcomesReadOnly() throws Exception {
        runBrowserChecks("produkt-ui-review.test.js");
    }

    private void runBrowserChecks(String filename) throws Exception {
        Path script=Path.of("src/test/js", filename);
        if(!Files.isRegularFile(script))script=Path.of("testsweet/src/test/js", filename);
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
