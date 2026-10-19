package de.allgemein.testsweet;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/** Lokale Konfiguration; Zugangsschlüssel erscheinen nie in der öffentlichen Sicht. */
final class LlmConfig {
    static final String DEFAULT_URL = "https://api.z.ai/api/coding/paas/v4";
    private final Path file;
    private final Map<String, String> environment;

    record Settings(String baseUrl, String model, String apiKey, int timeoutSeconds,
                    int maxTokens, int batchSize, int cases) {
        Map<String, Object> publicView() {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("baseUrl", baseUrl); out.put("model", model);
            out.put("apiKeyConfigured", !apiKey.isBlank());
            out.put("timeoutSeconds", timeoutSeconds); out.put("maxTokens", maxTokens);
            out.put("batchSize", batchSize); out.put("cases", cases);
            return out;
        }
    }

    LlmConfig(Path repoRoot, Map<String, String> environment) {
        this.file = repoRoot.resolve("testsweet/llm.local.properties");
        this.environment = Map.copyOf(environment);
    }

    synchronized Settings read() throws IOException { return settings(load(), true); }

    synchronized void save(Map<?, ?> values) throws IOException {
        Properties p = load();
        for (String name : new String[]{"baseUrl", "model", "timeoutSeconds", "maxTokens", "batchSize", "cases"}) {
            if (values.containsKey(name)) p.setProperty(name, String.valueOf(values.get(name)).trim());
        }
        if (Boolean.TRUE.equals(values.get("clearApiKey"))) p.remove("apiKey");
        if (values.get("apiKey") instanceof String key && !key.isBlank()) p.setProperty("apiKey", key.trim());
        settings(p, false); // Erst vollständig validieren, dann atomar ersetzen.
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), ".llm-config-", ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                p.store(writer, "Lokale LLM-Konfiguration; nicht versionieren");
            }
            try { Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally { Files.deleteIfExists(temporary); }
    }

    private Properties load() throws IOException {
        Properties p = new Properties();
        if (Files.exists(file)) try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { p.load(r); }
        return p;
    }

    private Settings settings(Properties p, boolean overrides) {
        String baseUrl = p.getProperty("baseUrl", DEFAULT_URL).trim().replaceAll("/+$", "");
        URI uri;
        try { uri = URI.create(baseUrl); } catch (RuntimeException e) { throw new IllegalArgumentException("Ungültige LLM-Basisadresse"); }
        boolean local = "127.0.0.1".equals(uri.getHost()) || "localhost".equals(uri.getHost()) || "[::1]".equals(uri.getHost());
        if (uri.getHost() == null || uri.getRawUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                || !("https".equalsIgnoreCase(uri.getScheme()) || (local && "http".equalsIgnoreCase(uri.getScheme())))) {
            throw new IllegalArgumentException("LLM-Basisadresse muss HTTPS ohne Zugangsdaten verwenden (HTTP nur lokal)");
        }
        String model = p.getProperty("model", "glm-5.3").trim();
        if (model.isBlank() || model.length() > 120 || model.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Ungültiger Modellname");
        }
        String key = p.getProperty("apiKey", "").trim();
        if (overrides) {
            for (String name : new String[]{"SWEET_LLM_API_KEY", "GLM_API_KEY"}) {
                String candidate = environment.get(name);
                if (candidate != null && !candidate.isBlank()) { key = candidate.trim(); break; }
            }
        }
        if (key.length() > 8192 || key.chars().anyMatch(Character::isISOControl)) throw new IllegalArgumentException("Ungültiger API-Schlüssel");
        return new Settings(baseUrl, model, key, integer(p,"timeoutSeconds",180,5,900),
                integer(p,"maxTokens",16000,1024,131072), integer(p,"batchSize",3,1,10),
                integer(p,"cases",SimulatorDefaults.LLM_CASES,2,1000));
    }

    private static int integer(Properties p, String name, int fallback, int min, int max) {
        try {
            int n = Integer.parseInt(p.getProperty(name, String.valueOf(fallback)));
            if (n >= min && n <= max) return n;
        } catch (NumberFormatException ignored) { }
        throw new IllegalArgumentException(name + " muss zwischen " + min + " und " + max + " liegen");
    }
}
