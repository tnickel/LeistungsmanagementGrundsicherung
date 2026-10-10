package de.allgemein.lmg.config;

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * Externalisierte Konfiguration für das Leistungsmanagement (SDN.sozial LMG):
 * Lädt Einstellungen mit Prioritätskaskade (12-Factor-Prinzip):
 * <ol>
 *   <li>System-Properties (z. B. {@code -Dtx.url=...})</li>
 *   <li>Umgebungsvariablen (z. B. {@code TX_URL}, {@code LMG_PORT})</li>
 *   <li>Konfigurationsdatei {@code lmg.properties} (im Arbeitsverzeichnis oder Klassenpfad)</li>
 *   <li>Standardwerte</li>
 * </ol>
 */
public final class LmgConfig {

    private static final Logger LOG = Logger.getLogger(LmgConfig.class.getName());

    private final String url;
    private final String user;
    private final String password;
    private final int port;
    private final String poolType;
    private final int poolSize;
    private final int maxTasks;
    private final int queueCapacity;
    private final String auditFile;
    private final String benutzer;

    private LmgConfig(Properties props) {
        this.url = resolve(props, "tx.url", "TX_URL", "jdbc:oracle:thin:@//localhost:1921/o19c1p1");
        this.user = resolve(props, "tx.user", "TX_USER", "tx_demo");
        this.password = resolve(props, "tx.pass", "TX_PASS", "tx_demo");
        this.port = Integer.parseInt(resolve(props, "lmg.port", "LMG_PORT", "8080"));
        this.poolType = resolve(props, "lmg.pool.type", "LMG_POOL_TYP", "ucp");
        this.poolSize = Integer.parseInt(resolve(props, "lmg.pool.size", "LMG_POOL_GROESSE", "8"));
        this.maxTasks = Integer.parseInt(resolve(props, "lmg.tasks.max", "LMG_TASKS", "8"));
        this.queueCapacity = Integer.parseInt(resolve(props, "lmg.tasks.queue", "LMG_WARTESCHLANGE", "100"));
        this.auditFile = resolve(props, "lmg.audit.file", "LMG_AUDIT_FILE", "lmg_audit.log");
        this.benutzer = resolve(props, "lmg.benutzer", "LMG_BENUTZER", null);

        validate();
    }

    public static LmgConfig load() {
        return load("lmg.properties");
    }

    public static LmgConfig load(String fileName) {
        Properties fileProps = new Properties();
        Path p = Paths.get(fileName);
        if (Files.exists(p)) {
            try (InputStream in = new FileInputStream(p.toFile())) {
                fileProps.load(in);
                LOG.info(() -> "Konfiguration geladen aus Datei: " + p.toAbsolutePath());
            } catch (Exception e) {
                LOG.warning(() -> "Konnte Konfigurationsdatei nicht lesen: " + fileName + " (" + e.getMessage() + ")");
            }
        } else {
            try (InputStream in = LmgConfig.class.getClassLoader().getResourceAsStream(fileName)) {
                if (in != null) {
                    fileProps.load(in);
                    LOG.info(() -> "Konfiguration geladen aus Klassenpfad: " + fileName);
                }
            } catch (Exception e) {
                // optional
            }
        }
        return new LmgConfig(fileProps);
    }

    private void validate() {
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Ungültiger Port: " + port);
        }
        if (poolSize < 1) {
            throw new IllegalArgumentException("Poolgröße muss mindestens 1 sein: " + poolSize);
        }
        if (maxTasks < 1) {
            throw new IllegalArgumentException("Max-Tasks muss mindestens 1 sein: " + maxTasks);
        }
        if (queueCapacity < 1) {
            throw new IllegalArgumentException("Warteschlange muss mindestens 1 sein: " + queueCapacity);
        }
    }

    private static String resolve(Properties props, String propKey, String envKey, String fallback) {
        String sysVal = System.getProperty(propKey);
        if (sysVal != null && !sysVal.isBlank()) {
            return sysVal.trim();
        }
        String envVal = System.getenv(envKey);
        if (envVal != null && !envVal.isBlank()) {
            return envVal.trim();
        }
        String fileVal = props.getProperty(propKey);
        if (fileVal != null && !fileVal.isBlank()) {
            return fileVal.trim();
        }
        return fallback;
    }

    public String url() { return url; }
    public String user() { return user; }
    public String password() { return password; }
    public int port() { return port; }
    public String poolType() { return poolType; }
    public int poolSize() { return poolSize; }
    public int maxTasks() { return maxTasks; }
    public int queueCapacity() { return queueCapacity; }
    public String auditFile() { return auditFile; }
    public String benutzer() { return benutzer; }
}
