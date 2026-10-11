package de.allgemein.lmg.config;

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * Externalisierte Konfiguration für das Leistungsmanagement (SDN.Leistung LMG):
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
    private final String kduWarmwasserAbzugProzent;
    private final String kduAngemessenheitsgrenze;
    private final String rechtsbehelfModus;
    private final String rechtsbehelfBehoerde;
    private final String rechtsbehelfSozialgericht;
    private final String behoerdeOrt;
    private final String behoerdeAnschrift;
    private final int sessionTimeoutMinuten;

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
        this.kduWarmwasserAbzugProzent = resolve(props, "lmg.kdu.warmwasserabzug", "LMG_KDU_WARMWASSERABZUG", "0");
        this.kduAngemessenheitsgrenze = resolve(props, "lmg.kdu.angemessenheitsgrenze", "LMG_KDU_ANGEMESSENHEITSGRENZE", "0");
        this.rechtsbehelfModus = resolve(props, "lmg.rechtsbehelf.modus", "LMG_RECHTSBEHELFSMODUS", "WIDERSPRUCH");
        this.rechtsbehelfBehoerde = resolve(props, "lmg.rechtsbehelf.behoerde", "LMG_RECHTSBEHELFSBEHOERDE", null);
        this.rechtsbehelfSozialgericht = resolve(props, "lmg.rechtsbehelf.sozialgericht", "LMG_RECHTSBEHELFSGERICHT", null);
        this.behoerdeOrt = resolve(props, "lmg.behoerde.ort", "LMG_BEHOERDE_ORT", null);
        this.behoerdeAnschrift = resolve(props, "lmg.behoerde.anschrift", "LMG_BEHOERDE_ANSCHRIFT", null);
        this.sessionTimeoutMinuten = Integer.parseInt(resolve(props, "lmg.session.timeout.minuten", "LMG_SESSION_TIMEOUT", "60"));

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
        if (sessionTimeoutMinuten < 1) {
            throw new IllegalArgumentException("Session-Timeout muss mindestens 1 Minute sein: " + sessionTimeoutMinuten);
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

    /** KdU-Parameter der Bedarfsberechnung (§ 42a i.V.m. § 35 SGB XII); 0 = Prüfung deaktiviert. */
    public de.allgemein.lmg.calc.Leistungsberechnung.KduParameter kduParameter() {
        try {
            return new de.allgemein.lmg.calc.Leistungsberechnung.KduParameter(
                    new java.math.BigDecimal(kduWarmwasserAbzugProzent),
                    new java.math.BigDecimal(kduAngemessenheitsgrenze));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "KdU-Parameter müssen Zahlen sein (warmwasserabzug='" + kduWarmwasserAbzugProzent
                            + "', angemessenheitsgrenze='" + kduAngemessenheitsgrenze + "')", e);
        }
    }

    /** Konfiguration der Bescheid-Rechtsbehelfsbelehrung (§ 36 SGB X, träger-/landesrechtlich). */
    public de.allgemein.lmg.service.BescheidGenerator.Rechtsbehelf rechtsbehelf() {
        return new de.allgemein.lmg.service.BescheidGenerator.Rechtsbehelf(
                rechtsbehelfModus, rechtsbehelfBehoerde, rechtsbehelfSozialgericht, behoerdeOrt, behoerdeAnschrift);
    }

    /** Idle-Timeout angemeldeter Sitzungen in Minuten (0 ist ungültig, Minimum 1). */
    public int sessionTimeoutMinuten() { return sessionTimeoutMinuten; }
}
