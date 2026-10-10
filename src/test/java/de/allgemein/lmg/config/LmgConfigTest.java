package de.allgemein.lmg.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class LmgConfigTest {

    @Test
    void laedtStandardwerteWennKeineDateiExistiert() {
        LmgConfig config = LmgConfig.load("nicht-vorhanden.properties");
        assertEquals(8080, config.port());
        assertEquals("ucp", config.poolType());
        assertEquals(8, config.poolSize());
        assertEquals(8, config.maxTasks());
        assertEquals(100, config.queueCapacity());
        assertNotNull(config.url());
        assertNotNull(config.user());
    }

    /**
     * Testet die DATEI-Stufe der Konfigurationskaskade. Die Kaskade gibt
     * Umgebungsvariablen (TX_URL, LMG_PORT, …) Vorrang vor der Datei — ist im
     * Testlauf eine dieser Variablen gesetzt (echte-Oracle-Testlauf mit TX_URL),
     * kann die Datei-Stufe nicht isoliert geprüft werden. Der Test überspringt
     * sich dann, statt rot zu werden (Isolation statt Fehler).
     */
    @Test
    @DisabledIfEnvironmentVariable(named = "TX_URL", matches = ".+",
            disabledReason = "TX_URL gesetzt — Datei-Stufe der Kaskade nicht isoliert prüfbar")
    void laedtWerteAusPropertiesDatei(@TempDir Path tempDir) throws IOException {
        Path propFile = tempDir.resolve("test-lmg.properties");
        Files.writeString(propFile, """
                tx.url=jdbc:oracle:thin:@//10.0.0.1:1521/pdb1
                tx.user=custom_user
                tx.pass=custom_pass
                lmg.port=9090
                lmg.pool.type=simple
                lmg.pool.size=12
                lmg.tasks.max=16
                lmg.tasks.queue=250
                lmg.audit.file=custom_audit.log
                """);

        LmgConfig config = LmgConfig.load(propFile.toString());
        assertEquals("jdbc:oracle:thin:@//10.0.0.1:1521/pdb1", config.url());
        assertEquals("custom_user", config.user());
        assertEquals("custom_pass", config.password());
        assertEquals(9090, config.port());
        assertEquals("simple", config.poolType());
        assertEquals(12, config.poolSize());
        assertEquals(16, config.maxTasks());
        assertEquals(250, config.queueCapacity());
        assertEquals("custom_audit.log", config.auditFile());
    }
}
