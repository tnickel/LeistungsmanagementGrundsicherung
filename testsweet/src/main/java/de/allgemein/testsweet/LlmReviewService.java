package de.allgemein.testsweet;

import de.allgemein.lmg.web.Json;
import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Echte LLM-Nachprüfung mit vollständigen Dokumenten, strenger Antwortprüfung und Belegen. */
public final class LlmReviewService implements AutoCloseable {
    private static final Pattern CASE = Pattern.compile("(?m)^## TF (\\d+) ([^\\r\\n]+)\\r?$");
    private static final int MAX_DOCUMENT_BYTES = 64 * 1024 * 1024;
    private static final int MAX_SEND_ATTEMPTS = 3;
    private final Path root;
    private final LlmConfig configuration;
    private final Duration retryDelay;
    private final BuildVerifier buildVerifier;
    private volatile Map<?, ?> responseContract;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "testsweet-llm-review"); t.setDaemon(true); return t;
    });
    private volatile Map<String, Object> progress = Map.of("state", "IDLE", "running", false,
            "message", "Noch keine LLM-Prüfung in dieser Sitzung gestartet");
    private volatile boolean running;
    private boolean closed;
    private volatile Path latestDirectory;

    public LlmReviewService(Path repoRoot) { this(repoRoot, System.getenv()); }
    LlmReviewService(Path repoRoot, Map<String, String> environment) {
        this(repoRoot, environment, Duration.ofSeconds(30));
    }
    LlmReviewService(Path repoRoot, Map<String, String> environment, Duration retryDelay) {
        this(repoRoot, environment, retryDelay, new BuildVerifier() {
            public Map<String, Object> capture(Path root) { return BuildEvidence.capture(root); }
            public boolean matches(Map<?, ?> evidence, Path root) { return BuildEvidence.matches(evidence, root); }
        });
    }
    LlmReviewService(Path repoRoot, Map<String, String> environment, Duration retryDelay, BuildVerifier verifier) {
        root = repoRoot.toAbsolutePath().normalize();
        configuration = new LlmConfig(root, environment);
        buildVerifier = verifier;
        if (retryDelay.isNegative()) throw new IllegalArgumentException("Wartezeit darf nicht negativ sein");
        this.retryDelay = retryDelay;
        restoreLatest();
    }

    public Map<String, Object> configuration() {
        try { return configuration.read().publicView(); }
        catch (IOException e) { throw new IllegalStateException("LLM-Konfiguration kann nicht gelesen werden", e); }
    }

    public synchronized void saveConfiguration(Map<?, ?> values) {
        if (running) throw new IllegalStateException("Konfiguration erst nach Ende der laufenden LLM-Prüfung ändern");
        try { configuration.save(values); }
        catch (IOException e) { throw new IllegalStateException("LLM-Konfiguration kann nicht gespeichert werden", e); }
    }

    public Map<String, Object> testConnection() {
        try {
            LlmConfig.Settings settings = configuration.read();
            requireKey(settings);
            ApiReply reply = request(settings, List.of(Map.of("role", "user", "content",
                    "Verbindungstest. Antworte ausschließlich mit diesem JSON-Objekt: {\"status\":\"SWEET_LLM_OK\"}.")), 2048);
            Object answer = Json.parse(reply.content());
            boolean ok = "stop".equals(reply.finishReason()) && settings.model().equalsIgnoreCase(reply.model())
                    && Map.of("status", "SWEET_LLM_OK").equals(answer);
            return Map.of("ok", ok, "model", settings.model(), "returnedModel", reply.model(),
                    "message", ok ? "Echte Modellantwort empfangen" : "Modell antwortete, aber Verbindungstest war unvollständig oder unerwartet");
        } catch (Exception e) {
            return Map.of("ok", false, "message", safeError(e));
        }
    }

    public synchronized Map<String, Object> start(int maxCases) {
        if (closed) throw new IllegalStateException("LLM-Dienst ist beendet");
        if (running) throw new IllegalStateException("Eine LLM-Prüfung läuft bereits");
        LlmConfig.Settings settings;
        try { settings = configuration.read(); } catch (IOException e) { throw new IllegalStateException("Konfiguration nicht lesbar", e); }
        requireKey(settings);
        checkCount(maxCases);
        String id = UUID.randomUUID().toString();
        Path directory = root.resolve("testsweet/logs/llm-review-" + id);
        Map<String, Object> initial = new LinkedHashMap<>();
        initial.put("schemaVersion", 2); initial.put("kind", "LLM_REVIEW_START"); initial.put("reviewId", id);
        initial.put("startedAt", Instant.now().toString()); initial.put("model", settings.model()); initial.put("maxCases", maxCases);
        try { Files.createDirectories(directory); writeNew(directory.resolve("started.json"), Json.write(initial)); }
        catch (IOException e) { throw new IllegalStateException("LLM-Start kann nicht dauerhaft gespeichert werden", e); }
        running = true;
        latestDirectory = directory;
        initial.put("kind", "LLM_REVIEW"); initial.put("state", "PREPARING"); initial.put("running", true);
        initial.put("message", "Prüfdateien werden vollständig eingefroren"); initial.put("completedCases", 0);
        initial.put("totalCases", maxCases); initial.put("transportVerified", false); initial.put("artifactDirectory", directory.toString());
        initial.put("cases", List.of()); initial.put("findings", List.of()); initial.put("caseVerdict", "UNVERIFIED");
        progress = Map.copyOf(initial);
        try { writeAtomic(directory.resolve("status.json"), Json.write(initial)); }
        catch (IOException e) { running = false; throw new IllegalStateException("LLM-Startstatus kann nicht gespeichert werden", e); }
        executor.submit(() -> execute(settings, maxCases, id, directory));
        return status();
    }

    public Map<String, Object> status() {
        if (!Boolean.TRUE.equals(progress.get("running")) && latestDirectory != null && Boolean.TRUE.equals(progress.get("transportVerified"))) {
            try { validateStored(latestDirectory); }
            catch (Exception e) {
                Map<String, Object> stale = new LinkedHashMap<>(progress);
                stale.put("state", "UNVERIFIED"); stale.put("message", "Prüfbelege sind unvollständig oder passen nicht mehr zu Quelldateien/Prüfauftrag");
                stale.put("transportVerified", false); stale.put("integrityError", safeError(e)); progress = Map.copyOf(stale);
            }
        }
        Map<String, Object> stand = new LinkedHashMap<>(progress);
        stand.put("retryStatistik", wiederholungsStatistik(stand));
        return stand;
    }

    /**
     * Wiederholungsstatistik aus den protokollierten Sendeversuchen: Je
     * Fallgruppe zählt der Versuch, der eine gültige Antwort brachte (1. Versuch,
     * 1 Wiederholung, 2 Wiederholungen); Fallgruppen, deren drei Sendeversuche
     * alle fehlschlugen, zählen als gescheitert. Daneben steht die Gesamtzahl
     * der formal ungültigen Antworten, die ein erneutes vollständiges Senden
     * erforderten. Gültig auch während des Laufs — es wird nur der bereits
     * protokollierte Stand ausgewertet.
     */
    public static Map<String, Integer> wiederholungsStatistik(Map<String, Object> stand) {
        int ersterVersuch = 0, eineWiederholung = 0, zweiWiederholungen = 0;
        int gescheiterteFallgruppen = 0, wiederholteSendungen = 0;
        Map<Integer, Integer> gueltigerVersuch = new LinkedHashMap<>();
        Map<Integer, Integer> letzterFehlversuch = new LinkedHashMap<>();
        for (Object eintragObj : listeOderLeer(stand.get("attemptChecks"))) {
            if (!(eintragObj instanceof Map<?, ?> eintrag)) continue;
            int gruppe = zahl(eintrag.get("batch")), versuch = zahl(eintrag.get("attempt"));
            if (gruppe <= 0 || versuch <= 0) continue;
            if (Boolean.TRUE.equals(eintrag.get("responseValidated"))) gueltigerVersuch.merge(gruppe, versuch, Math::min);
            else { wiederholteSendungen++; letzterFehlversuch.merge(gruppe, versuch, Math::max); }
        }
        for (Integer versuch : gueltigerVersuch.values()) {
            if (versuch == 1) ersterVersuch++;
            else if (versuch == 2) eineWiederholung++;
            else zweiWiederholungen++;
        }
        for (Map.Entry<Integer, Integer> gruppe : letzterFehlversuch.entrySet()) {
            if (!gueltigerVersuch.containsKey(gruppe.getKey()) && gruppe.getValue() >= 3) gescheiterteFallgruppen++;
        }
        Map<String, Integer> statistik = new LinkedHashMap<>();
        statistik.put("ersterVersuch", ersterVersuch);
        statistik.put("eineWiederholung", eineWiederholung);
        statistik.put("zweiWiederholungen", zweiWiederholungen);
        statistik.put("gescheiterteFallgruppen", gescheiterteFallgruppen);
        statistik.put("wiederholteSendungen", wiederholteSendungen);
        return statistik;
    }

    private static List<?> listeOderLeer(Object wert) {
        return wert instanceof List<?> liste ? liste : List.of();
    }

    private static int zahl(Object wert) {
        return wert instanceof Number n ? n.intValue() : 0;
    }

    public Map<String, Object> buildPackage(int maxCases) {
        try {
            ReviewPackage p = prepare(configuration.read(), maxCases, UUID.randomUUID().toString(), null);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("ok", true); out.put("reviewId", p.id()); out.put("directory", p.directory().toString());
            out.put("manifest", p.directory().resolve("manifest.json").toString());
            out.put("pruefmenge", p.directory().resolve("pruefmenge.md").toString());
            out.put("grundlagen", p.directory().resolve("gesetzesgrundlagen-2026.md").toString());
            out.put("pruefauftrag", p.directory().resolve("llm-pruefauftrag.txt").toString());
            out.put("faelle", p.cases().size()); out.put("selectedCaseIds", p.cases().stream().map(ReviewCase::id).toList());
            return out;
        } catch (IOException e) { throw new IllegalStateException("Prüfpaket kann nicht erstellt werden", e); }
    }

    private void execute(LlmConfig.Settings settings, int maxCases, String reviewId, Path directory) {
        ReviewPackage p = null;
        List<Map<String, Object>> reviewed = new ArrayList<>();
        List<Map<String, Object>> transportChecks = new ArrayList<>();
        List<Map<String, Object>> attemptChecks = new ArrayList<>();
        int completedBatches = 0;
        try {
            p = prepare(settings, maxCases, reviewId, directory);
            publish(p, settings, "RUNNING", reviewed.size(), completedBatches, "LLM prüft die erste Fallgruppe");
            for (int n = 0; n < p.batches().size(); n++) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                Batch batch = p.batches().get(n);
                List<Map<String, Object>> requirements = requirements(batch.cases());
                String user = userMessage(batch.cases().stream().map(ReviewCase::id).toList(), p.prompt(), p.law(), batch.name(), batch.text(), requirements);
                if (user.getBytes(StandardCharsets.UTF_8).length > 2 * 1024 * 1024) {
                    throw new IllegalStateException("Fallgruppe überschreitet 2 MiB; kleinere batchSize wählen. Es wurde nichts gekürzt.");
                }
                List<Map<String, String>> messages = List.of(Map.of("role", "system", "content", contractString("systemMessage")),
                        Map.of("role", "user", "content", user));
                boolean korrekturHinweis = false;
                for (int attempt = 1; attempt <= MAX_SEND_ATTEMPTS; attempt++) {
                    String suffix = (n + 1) + "-attempt-" + attempt + ".json";
                    Path requestFile = p.directory().resolve("request-" + suffix);
                    Path responseFile = p.directory().resolve("response-" + suffix);
                    Map<String, Object> transport = new LinkedHashMap<>();
                    transport.put("batch", n + 1); transport.put("attempt", attempt);
                    transport.put("requestFile", requestFile.getFileName().toString());
                    transport.put("responseFile", responseFile.getFileName().toString());
                    transport.put("requestedModel", settings.model()); transport.put("responseValidated", false);
                    transport.put("caseIds", batch.cases().stream().map(ReviewCase::id).toList());
                    if (korrekturHinweis) transport.put("korrekturHinweis", true);
                    transport.put("documents", List.of(sentDocument("llm-pruefauftrag.txt", p.prompt()),
                            sentDocument("gesetzesgrundlagen-2026.md", p.law()), sentDocument(batch.name(), batch.text())));
                    List<Map<String,Object>> observed = List.of();
                    try {
                        ApiReply reply = request(settings, messages, settings.maxTokens(), requestFile);
                        observed = provisionalFailures(reply.content(), batch.cases().stream().map(ReviewCase::id).toList(), n + 1, attempt,
                                requestFile.getFileName().toString(), responseFile.getFileName().toString());
                        transport.put("requestSha256", reply.requestSha256());
                        transport.put("responseSha256", hash(reply.raw()));
                        transport.put("httpStatus", reply.httpStatus()); transport.put("requestId", reply.requestId());
                        transport.put("returnedModel", reply.model()); transport.put("finishReason", reply.finishReason());
                        if (!"stop".equals(reply.finishReason())) {
                            throw new InvalidModelResponseException("Unvollständige Modellantwort (finish_reason=" + reply.finishReason() + ")");
                        }
                        if (!settings.model().equalsIgnoreCase(reply.model())) {
                            throw new InvalidModelResponseException("Antwortmodell weicht vom angeforderten Modell ab");
                        }
                        List<Map<String, Object>> checked;
                        try { checked = LlmAnswerValidator.validate(reply.content(), batch.cases().stream().map(ReviewCase::id).toList(), requirements); }
                        catch (IllegalArgumentException invalid) { throw new InvalidModelResponseException(invalid.getMessage()); }
                        transport.put("responseValidated", true);
                        attemptChecks.add(transport);
                        transportChecks.add(transport);
                        reviewed.addAll(checked);
                        completedBatches++;
                        publish(p, settings, "RUNNING", reviewed.size(), completedBatches,
                                "Fallgruppe " + completedBatches + "/" + p.batches().size() + " vollständig beantwortet");
                        addTransportStatus(transportChecks, attemptChecks, false);
                        checkpoint(p, settings, reviewed, transportChecks, attemptChecks, "RUNNING", completedBatches, false, String.valueOf(progress.get("message")), false);
                        break;
                    } catch (Exception error) {
                        if (error instanceof InterruptedException interrupted) throw interrupted;
                        addArtifactHashes(transport, requestFile, responseFile);
                        transport.put("error", safeError(error));
                        if (error instanceof InvalidModelResponseException && !observed.isEmpty()) transport.put("provisionalFailures", observed);
                        attemptChecks.add(transport);
                        addTransportStatus(transportChecks, attemptChecks, false);
                        checkpoint(p, settings, reviewed, transportChecks, attemptChecks, "RUNNING", completedBatches, false, safeError(error), false);
                        if (!(error instanceof InvalidModelResponseException)) throw error;
                        if (attempt == MAX_SEND_ATTEMPTS) {
                            throw new RetryExhaustedException("3 Versuche fehlgeschlagen bei Fallgruppe " + (n + 1)
                                    + "/" + p.batches().size() + " (" + safeError(error) + "); Logdateien auswerten: " + p.directory());
                        }
                        // Zielkorrektur statt identischem Voll-Resend: Bei niedriger Temperatur
                        // scheitert die identische Wiederholung identisch. Der erneuten
                        // Anfrage wird die konkrete Mängelliste und die geforderte
                        // Check-Abdeckung je Fall mitgegeben (Nachweis im Versuchsprotokoll).
                        messages = List.of(Map.of("role", "system", "content", contractString("systemMessage")),
                                Map.of("role", "user", "content", user + korrekturAnweisung(safeError(error), requirements)));
                        korrekturHinweis = true;
                        publish(p, settings, "RUNNING", reviewed.size(), completedBatches,
                                "Fallgruppe " + (n + 1) + "/" + p.batches().size() + ": Versuch " + attempt
                                        + "/" + MAX_SEND_ATTEMPTS + " ungültig (" + safeError(error) + "); gezielte Korrekturanforderung in 30 Sekunden");
                        addTransportStatus(transportChecks, attemptChecks, false);
                        Thread.sleep(retryDelay);
                    }
                }
            }
            String verdict = LlmAnswerValidator.overall(reviewed, LlmAnswerValidator.referenceChecks(reviewed, requirements(p.cases())));
            if (verdict.equals("PASS") && !collectProvisional(attemptChecks).isEmpty()) verdict = "UNVERIFIED";
            finish(p, settings, reviewed, transportChecks, attemptChecks, verdict, completedBatches, false,
                    verdict.equals("PASS") ? "Alle ausgewählten Fälle vollständig vom Modell geprüft" : "LLM-Prüfung meldet Befunde oder ungeprüfte Fälle");
        } catch (Exception e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            String error = safeError(e);
            if (p != null) {
                try { finish(p, settings, reviewed, transportChecks, attemptChecks, "ERROR", completedBatches,
                        e instanceof RetryExhaustedException, error); }
                catch (IOException ignored) {
                    Map<String, Object> fallback = new LinkedHashMap<>(snapshot(p, settings, "ERROR", reviewed.size(), completedBatches,
                            error + "; Ergebnisablage fehlgeschlagen"));
                    fallback.put("transportVerified", false); fallback.put("attemptChecks", List.copyOf(attemptChecks));
                    addFindings(fallback, reviewed, requirements(p.cases()));
                    if (e instanceof RetryExhaustedException) fallback.put("retryExhausted", true);
                    progress = Map.copyOf(fallback);
                }
            } else {
                Map<String, Object> failed = new LinkedHashMap<>(progress);
                failed.put("state", "ERROR"); failed.put("running", false); failed.put("message", error); failed.put("transportVerified", false);
                failed.put("completedAt", Instant.now().toString()); progress = Map.copyOf(failed);
                try { writeAtomic(directory.resolve("status.json"), Json.write(failed)); writeAtomic(directory.resolve("result.json"), Json.write(failed));
                    writeAtomic(directory.resolve("protocol.md"), "# LLM-Prüfung abgebrochen\n\nPrüfung: " + reviewId + "\n\n" + error + "\n"); }
                catch (IOException ignored) { }
            }
        } finally { synchronized (this) { running = false; } }
    }

    private void finish(ReviewPackage p, LlmConfig.Settings settings, List<Map<String, Object>> reviewed,
                        List<Map<String, Object>> transportChecks, List<Map<String, Object>> attemptChecks,
                        String state, int completedBatches, boolean retryExhausted, String message) throws IOException {
        StringBuilder protocol = new StringBuilder("# Echte LLM-Nachprüfung\n\nPrüfung: ").append(p.id())
                .append("\nModell (angefordert): ").append(settings.model()).append("\nErgebnis: ").append(state)
                .append("\nFälle: ").append(reviewed.size()).append('/').append(p.cases().size())
                .append("\n\nManifest, Requests und API-Antworten liegen im selben Verzeichnis; etwaige Schlüssel-Echos sind bereinigt und die gespeicherten Bytes mit SHA-256 gebunden.\n");
        protocol.append("\n## Sendeversuche\n");
        for (Map<String, Object> attempt : attemptChecks) {
            protocol.append("\n- Fallgruppe ").append(attempt.get("batch"))
                    .append(", Versuch ").append(attempt.get("attempt"))
                    .append(Boolean.TRUE.equals(attempt.get("responseValidated")) ? ": gültig" : ": fehlgeschlagen")
                    .append(Boolean.TRUE.equals(attempt.get("korrekturHinweis")) ? " (mit Korrekturzusatz gesendet)" : "")
                    .append("; Request: ").append(attempt.get("requestFile"))
                    .append("; Response: ").append(attempt.get("responseFile"));
            if (attempt.containsKey("error")) protocol.append("; Grund: ").append(attempt.get("error"));
            protocol.append('\n');
        }
        if (retryExhausted) protocol.append("\n3 Versuche fehlgeschlagen. Gesamttest abgebrochen; Request-/Response-Dateien und result.json auswerten.\n");
        if (!collectProvisional(attemptChecks).isEmpty()) protocol.append("\n## Unverifizierte frühere Modellbefunde\n\n")
                .append("Diese Hinweise stammen aus formal ungültigen Antworten; sie sind keine gesicherten Softwarefehler. Spätere PASS-Antworten entkräften sie nicht.\n\n")
                .append(Json.write(collectProvisional(attemptChecks))).append('\n');
        for (Map<String, Object> c : reviewed) protocol.append("\n## ").append(c.get("caseId")).append("\n")
                .append(c.get("verdict")).append("\n\n").append(c.get("reasoning")).append("\n\nBefunde: ").append(Json.write(c.get("findings"))).append('\n');
        protocol.append("\n## Unabhängige arithmetische Referenz 2026\n\n")
                .append(Json.write(LlmAnswerValidator.referenceChecks(reviewed, requirements(p.cases())))).append('\n');
        writeAtomic(p.directory().resolve("protocol.md"), redact(protocol.toString(), settings.apiKey()));
        checkpoint(p, settings, reviewed, transportChecks, attemptChecks, state, completedBatches, retryExhausted, message, true);
        latestDirectory = p.directory();
        if (Boolean.TRUE.equals(progress.get("transportVerified"))) {
            try { validateStored(p.directory()); }
            catch (Exception invalid) {
                Map<String, Object> terminal = new LinkedHashMap<>(progress);
                terminal.put("state", "UNVERIFIED");
                terminal.put("message", "Prüfbelege passen nicht mehr vollständig zu den Quelldateien; keine Freigabe");
                terminal.put("transportVerified", false); terminal.put("integrityError", safeError(invalid));
                progress = Map.copyOf(terminal);
            }
        }
    }

    private void checkpoint(ReviewPackage p, LlmConfig.Settings settings, List<Map<String, Object>> reviewed,
                            List<Map<String, Object>> transport, List<Map<String, Object>> attempts, String state,
                            int batches, boolean retryExhausted, String message, boolean terminal) throws IOException {
        Map<String, Object> result = snapshot(p, settings, state, reviewed.size(), batches, message);
        result.put("schemaVersion", 2); result.put("kind", "LLM_REVIEW"); result.put("updatedAt", Instant.now().toString());
        if (terminal) result.put("completedAt", Instant.now().toString());
        Map<?, ?> start = (Map<?, ?>) Json.parse(readDocument(p.directory().resolve("started.json")));
        result.put("startedAt", start.get("startedAt"));
        result.put("manifestSha256", sha256(Files.readAllBytes(p.directory().resolve("manifest.json"))));
        result.put("completedCaseIds", reviewed.stream().map(c -> c.get("caseId")).toList());
        result.put("cases", List.copyOf(reviewed));
        result.put("transportVerified", terminal && batches == p.batches().size() && transport.size() == p.batches().size()
                && transport.stream().allMatch(t -> Boolean.TRUE.equals(t.get("responseValidated"))));
        result.put("transportChecks", transport.stream().map(Map::copyOf).toList()); result.put("attemptChecks", attempts.stream().map(Map::copyOf).toList());
        addFindings(result, reviewed, requirements(p.cases()));
        if (retryExhausted) result.put("retryExhausted", true);
        String text = redact(Json.write(result), settings.apiKey());
        writeAtomic(p.directory().resolve("status.json"), text);
        if (terminal) writeAtomic(p.directory().resolve("result.json"), text);
        if (terminal && Boolean.TRUE.equals(result.get("transportVerified"))) {
            try { validateStored(p.directory()); }
            catch (Exception invalid) {
                result.put("state", "UNVERIFIED"); result.put("transportVerified", false);
                result.put("message", "Prüfbelege passen nicht mehr zu Quelldateien oder Build; keine Freigabe");
                result.put("integrityError", safeError(invalid));
                text = redact(Json.write(result), settings.apiKey());
                writeAtomic(p.directory().resolve("status.json"), text); writeAtomic(p.directory().resolve("result.json"), text);
            }
        }
        progress = Map.copyOf((Map<String, Object>) Json.parse(text));
    }

    private static void addFindings(Map<String, Object> out, List<Map<String, Object>> cases, List<Map<String, Object>> requirements) {
        List<Map<String, Object>> reference = LlmAnswerValidator.referenceChecks(cases, requirements);
        List<Map<String, Object>> findings = new ArrayList<>();
        for (Map<String, Object> c : cases) for (Object f : (List<?>) c.get("findings"))
            findings.add(Map.of("caseId", c.get("caseId"), "source", "LLM", "description", f, "reasoning", c.get("reasoning")));
        for (Map<String, Object> c : reference) for (String field : List.of("findings", "missingInputs", "arithmeticIssues", "workflowFindings", "workflowUnverified"))
            for (Object f : (List<?>) c.get(field)) findings.add(Map.of("caseId", c.get("caseId"), "source", "REFERENCE_2026", "description", f, "reasoning", field));
        List<Map<String,Object>> provisional = collectProvisional(out.get("attemptChecks") instanceof List<?> attempts
                ? (List<Map<String,Object>>) attempts : List.of());
        for (Map<String,Object> observation : provisional) findings.add(Map.of("caseId", observation.get("caseId"),
                "source", "UNVALIDATED_MODEL_RESPONSE", "description", "Unverifizierter früherer Modellbefund: " + observation.get("reason"),
                "reasoning", "Fallgruppe " + observation.get("batch") + ", Versuch " + observation.get("attempt") + "; " + observation.get("responseFile")));
        out.put("referenceChecks", reference); out.put("findings", findings);
        out.put("provisionalFailures", provisional); out.put("hasUnverifiedFindings", !provisional.isEmpty());
        out.put("caseVerdict", cases.isEmpty() ? "UNVERIFIED" : LlmAnswerValidator.overall(cases, reference));
        out.put("hasFindings", !findings.isEmpty());
        out.put("hasFailures", "FAIL".equals(out.get("caseVerdict")));
        boolean complete = !cases.isEmpty() && out.get("totalCases") instanceof Number total && total.intValue() == cases.size();
        out.put("reviewCoverageVerified", complete && provisional.isEmpty() && reference.stream().allMatch(c -> ((List<?>)c.get("missingInputs")).isEmpty()));
        out.put("calculationVerified", complete && provisional.isEmpty() && reference.stream().allMatch(c -> "PASS".equals(c.get("state"))));
    }

    private void publish(ReviewPackage p, LlmConfig.Settings s, String state, int complete, int batches, String message) {
        Map<String, Object> next = snapshot(p, s, state, complete, batches, message);
        for (String key : List.of("cases", "findings", "caseVerdict", "hasFindings", "hasFailures", "referenceChecks", "reviewCoverageVerified", "calculationVerified", "provisionalFailures", "hasUnverifiedFindings"))
            if (progress.containsKey(key)) next.put(key, progress.get(key));
        progress = Map.copyOf(next);
    }
    private Map<String, Object> snapshot(ReviewPackage p, LlmConfig.Settings s, String state, int complete, int batches, String message) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("state", state); out.put("running", state.equals("RUNNING") || state.equals("PREPARING"));
        out.put("reviewId", p.id()); out.put("model", s.model()); out.put("totalCases", p.cases().size());
        out.put("completedCases", complete); out.put("totalBatches", p.batches().size()); out.put("completedBatches", batches);
        out.put("message", message); out.put("artifactDirectory", p.directory().toString());
        try {
            Map<?, ?> manifest = (Map<?, ?>) Json.parse(Files.readString(p.directory().resolve("manifest.json")));
            out.put("documents", manifest.get("documents")); out.put("sources", manifest.get("sources"));
            out.put("selectedCaseIds", manifest.get("selectedCaseIds"));
            out.put("buildEvidence", manifest.get("buildEvidence"));
        } catch (IOException ignored) { }
        return out;
    }

    private void addTransportStatus(List<Map<String, Object>> checks, List<Map<String, Object>> attempts, boolean verified) {
        Map<String, Object> out = new LinkedHashMap<>(progress);
        out.put("transportVerified", verified); out.put("transportChecks", checks.stream().map(Map::copyOf).toList());
        out.put("attemptChecks", attempts.stream().map(Map::copyOf).toList()); progress = Map.copyOf(out);
    }

    private static void addArtifactHashes(Map<String, Object> check, Path request, Path response) throws IOException {
        if (Files.isRegularFile(request)) check.put("requestSha256", sha256(Files.readAllBytes(request)));
        if (Files.isRegularFile(response)) check.put("responseSha256", sha256(Files.readAllBytes(response)));
    }

    private ReviewPackage prepare(LlmConfig.Settings settings, int maxCases, String id, Path startedDirectory) throws IOException {
        checkCount(maxCases);
        Path tool = root.resolve("doc/testzertifikat-werkzeug");
        Path promptPath = tool.resolve("llm-pruefauftrag.txt"), lawPath = tool.resolve("gesetzesgrundlagen-2026.md");
        String prompt = readDocument(promptPath), law = readDocument(lawPath);
        Path contractPath = tool.resolve("llm-response-contract.json");
        String contractText = readDocument(contractPath);
        responseContract = (Map<?, ?>) Json.parse(contractText);
        if (!LlmAnswerValidator.schemaVersion2(responseContract.get("schemaVersion")))
            throw new IllegalArgumentException("Antwortvertrag Version 2 fehlt");
        Source positive = newestSource("POSITIV"), negative = newestSource("NEGATIV");
        List<ReviewCase> positiveOrder = new ArrayList<>(positive.cases());
        positiveOrder.sort(Comparator.comparingInt(c -> Pattern.compile("(?m)^## TF ?(?:11|12|13|14|15|16|17|18|19)(?:[: ]).*?(?:ENTBINDUNG-FOLGEMONAT|VERMOEGEN-FOLGEMONAT|ALLEINERZIEHEND-[456]KINDER|MEHRBEDARF-ABS6|KDU-ZENTRAL|KDU-BG-GRENZE|MINDERJAEHRIG-OHNE-ERWACHSENE)").matcher(c.text()).find() ? 0 : 1));
        List<ReviewCase> selected = new ArrayList<>();
        for (int i = 0; selected.size() < maxCases && (i < positiveOrder.size() || i < negative.cases().size()); i++) {
            if (i < positiveOrder.size()) selected.add(positiveOrder.get(i));
            if (selected.size() < maxCases && i < negative.cases().size()) selected.add(negative.cases().get(i));
        }
        if (selected.size() != maxCases) throw new IllegalArgumentException("Nur " + selected.size() + " Fälle in erfolgreichen Positiv-/Negativberichten vorhanden; angefordert: " + maxCases + ". Keine stille Kürzung.");
        Path directory = startedDirectory == null ? root.resolve("testsweet/logs/llm-review-" + id) : startedDirectory;
        Files.createDirectories(directory);
        String sample = sampleDocument(selected, positive, negative);
        writeNew(directory.resolve("llm-pruefauftrag.txt"), prompt);
        writeNew(directory.resolve("gesetzesgrundlagen-2026.md"), law);
        writeNew(directory.resolve("pruefmenge.md"), sample);
        writeNew(directory.resolve("source-POSITIV.md"), positive.text());
        writeNew(directory.resolve("source-NEGATIV.md"), negative.text());
        writeNew(directory.resolve("llm-response-contract.json"), contractText);
        List<Batch> batches = new ArrayList<>();
        List<Map<String, Object>> batchManifest = new ArrayList<>();
        for (int start = 0; start < selected.size(); start += settings.batchSize()) {
            List<ReviewCase> cases = List.copyOf(selected.subList(start, Math.min(selected.size(), start + settings.batchSize())));
            String name = "pruefmenge-batch-" + (batches.size() + 1) + ".md";
            String content = sampleDocument(cases, positive, negative);
            writeNew(directory.resolve(name), content);
            batches.add(new Batch(name, content, cases));
            batchManifest.add(Map.of("name", name, "sha256", hash(content), "bytes", content.getBytes(StandardCharsets.UTF_8).length,
                    "caseIds", cases.stream().map(ReviewCase::id).toList()));
        }
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("schemaVersion", 2); manifest.put("kind", "LLM_REVIEW"); manifest.put("reviewId", id);
        manifest.put("createdAt", Instant.now().toString()); manifest.put("model", settings.model());
        manifest.put("baseUrl", settings.baseUrl()); manifest.put("promptVersion", hash(prompt));
        manifest.put("responseContractSha256", hash(contractText)); manifest.put("caseRequirements", requirements(selected));
        manifest.put("buildEvidence", buildVerifier.capture(root));
        manifest.put("referenceScope", "Unabhängige arithmetische Referenz 2026; keine externe Rechtsverifikation. Workflow und SQL je dokumentiertem V-/S-Prüfpunkt durch Modell.");
        manifest.put("sources", List.of(sourceMeta(positive), sourceMeta(negative)));
        manifest.put("documents", List.of(documentMeta("llm-pruefauftrag.txt", prompt, relative(promptPath)),
                documentMeta("gesetzesgrundlagen-2026.md", law, relative(lawPath)), documentMeta("pruefmenge.md", sample, "")));
        manifest.put("selectedCaseIds", selected.stream().map(ReviewCase::id).toList());
        manifest.put("availableCases", Map.of("POSITIV", positive.cases().size(), "NEGATIV", negative.cases().size()));
        manifest.put("selection", "Stratifizierte Stichprobe: feste Fach-Grenzfälle TF11–TF19 zuerst, sofern vorhanden; anschließend weitere Fälle in Berichtreihenfolge, abwechselnd POSITIV/NEGATIV. Vollständige Fälle, keine Vollprüfung.");
        manifest.put("batches", batchManifest);
        writeNew(directory.resolve("manifest.json"), Json.write(manifest));
        return new ReviewPackage(id, directory, prompt, law, List.copyOf(selected), List.copyOf(batches));
    }

    private Source newestSource(String category) throws IOException {
        Path logs = root.resolve("testsweet/logs");
        if (Files.isDirectory(logs)) try (var stream = Files.list(logs)) {
            List<Path> runs = stream.filter(Files::isDirectory).filter(p -> p.getFileName().toString().startsWith("run-"))
                    .sorted(Comparator.comparing((Path p) -> RunChronology.start(p).earliest()).thenComparing(Path::toString).reversed()).toList();
            for (Path run : runs) {
                Path file = run.resolve("testdokumentation.md");
                Path lifecycle = run.resolve("run-state.json");
                Map<?, ?> state = Map.of();
                if (Files.isRegularFile(lifecycle)) {
                    state = (Map<?, ?>) Json.parse(readDocument(lifecycle));
                    if (category.equals(state.get("category")) && (!"PASS".equals(state.get("state"))
                            || !Boolean.TRUE.equals(state.get("success")) || !Boolean.TRUE.equals(state.get("reportsComplete"))))
                        throw new IllegalArgumentException("Neuester " + category + "-Lauf ist fehlgeschlagen oder unvollständig: " + run.getFileName());
                }
                if (!Files.isRegularFile(file)) continue;
                String text = readDocument(file);
                if (!Pattern.compile("(?m)^kategorie = " + category + "\\r?$").matcher(text).find()) continue;
                var chronology = RunChronology.start(run);
                if (!chronology.explicitOffset()) throw new IllegalArgumentException("Quelllauf hat keinen eindeutigen Zeitstempel mit Offset: " + run.getFileName());
                for (Path other : runs) {
                    if (other.equals(run) || RunChronology.start(other).latest().isBefore(chronology.earliest())) continue;
                    Path otherState = other.resolve("run-state.json"), otherText = other.resolve("testdokumentation.md");
                    boolean sameCategory = Files.isRegularFile(otherState)
                            && category.equals(((Map<?, ?>) Json.parse(readDocument(otherState))).get("category"));
                    if (!sameCategory && Files.isRegularFile(otherText)) sameCategory = Pattern.compile("(?m)^kategorie = " + category + "\\r?$").matcher(readDocument(otherText)).find();
                    if (sameCategory) throw new IllegalArgumentException("Quelllauf-Reihenfolge ist nicht eindeutig: " + run.getFileName() + " / " + other.getFileName());
                }
                if (!Files.isRegularFile(lifecycle)) throw new IllegalArgumentException("Neuester " + category + "-Bericht besitzt keinen aktuellen Lauf-/Buildnachweis: " + run.getFileName());
                if (!Pattern.compile("(?m)^automatik_bestanden = true\\r?$").matcher(text).find())
                    throw new IllegalArgumentException("Neuester " + category + "-Bericht meldet Fehler: " + run.getFileName());
                if (!(state.get("buildEvidence") instanceof Map<?, ?> evidence) || !buildVerifier.matches(evidence, root))
                    throw new IllegalArgumentException("Buildnachweis der " + category + "-Quelle fehlt oder ist veraltet: " + run.getFileName());
                if (!(state.get("reports") instanceof List<?> reports) || reports.stream().noneMatch(value -> value instanceof Map<?, ?> report
                        && "testdokumentation.md".equals(report.get("path")) && hash(text).equals(report.get("sha256"))))
                    throw new IllegalArgumentException("Quelldokument stimmt nicht mit abgeschlossenem Laufnachweis überein: " + run.getFileName());
                List<ReviewCase> cases = new ArrayList<>();
                Matcher matcher = CASE.matcher(text);
                List<Integer> starts = new ArrayList<>(); List<String> numbers = new ArrayList<>();
                while (matcher.find()) { starts.add(matcher.start()); numbers.add(matcher.group(1)); }
                if (starts.isEmpty()) continue;
                Set<String> unique = new HashSet<>();
                String runId = run.getFileName().toString();
                for (int i = 0; i < starts.size(); i++) {
                    String id = category + ":" + runId + ":TF" + numbers.get(i);
                    if (!unique.add(id)) throw new IllegalArgumentException("Doppelte Fallnummer im Prüfbericht " + relative(file));
                    cases.add(new ReviewCase(id, category, text.substring(starts.get(i), i + 1 < starts.size() ? starts.get(i + 1) : text.length())));
                }
                return new Source(file, runId, category, text, text.substring(0, starts.getFirst()), List.copyOf(cases));
            }
        }
        throw new IllegalArgumentException("Kein erfolgreicher vollständiger " + category + "-Bericht gefunden");
    }

    private String sampleDocument(List<ReviewCase> cases, Source positive, Source negative) {
        StringBuilder text = new StringBuilder("# Prüfmenge: vollständige ausgewählte Fälle\n\nAuswahl: ")
                .append(cases.size()).append(" Fälle; keine Kürzung innerhalb eines Falls.\n");
        for (Source source : List.of(positive, negative)) {
            text.append("\n# Vollständiger Laufkopf ").append(source.category()).append(" / ").append(source.runId())
                    .append("\nQuelle: ").append(relative(source.path())).append("\n\n").append(source.header());
        }
        for (ReviewCase c : cases) {
            // Die Pflicht-Prüfpunkte stehen sichtbar IM Falltext: Das Modell soll die
            // geforderte Check-Abdeckung nicht aus dem Umschlag ableiten müssen.
            List<?> pruefpunkte = (List<?>) LlmCalculationReference.requirement(c.id(), c.text()).get("checkIds");
            text.append("\n# Fall-ID: ").append(c.id()).append("\ngeforderte_pruefpunkte = ")
                    .append(pruefpunkte.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(", ")))
                    .append("\n\n").append(c.text());
        }
        return text.toString();
    }

    /** Präzise Mängel- und Abdeckungsrückmeldung für den erneuten Sendeversuch. */
    static String korrekturAnweisung(String fehler, List<Map<String, Object>> requirements) {
        StringBuilder anweisung = new StringBuilder("\n\n=== KORREKTURANWEISUNG ===\n")
                .append("Deine letzte Antwort wurde formal verworfen und wird nicht gewertet.\nFehler: ")
                .append(fehler).append(".\n")
                .append("Das checks-Array muss je Fall EXAKT die genannten Kennungen enthalten — jede mit ")
                .append("eigenem verdict und eigener reasoning, keine fehlende, keine doppelte, keine zusätzliche:\n");
        for (Map<String, Object> r : requirements) {
            anweisung.append("- ").append(r.get("caseId")).append(": ")
                    .append(((List<?>) r.get("checkIds")).stream().map(String::valueOf)
                            .collect(java.util.stream.Collectors.joining(", ")))
                    .append('\n');
        }
        anweisung.append("Berechne alle Werte erneut selbstständig und gib ausschließlich das vollständige ")
                .append("korrigierte JSON-Objekt nach Schema 2 aus.");
        return anweisung.toString();
    }

    private Map<String, Object> sourceMeta(Source source) throws IOException {
        Path lifecycle = source.path().resolveSibling("run-state.json");
        Map<?, ?> state = (Map<?, ?>) Json.parse(readDocument(lifecycle));
        return Map.of("path", relative(source.path()), "sha256", hash(source.text()), "runId", source.runId(), "category", source.category(),
                "snapshotPath", "source-" + source.category() + ".md", "mode", state.get("mode"),
                "runStatePath", relative(lifecycle), "runStateSha256", sha256(Files.readAllBytes(lifecycle)));
    }
    private static Map<String, Object> documentMeta(String name, String text, String sourcePath) {
        return Map.of("name", name, "path", name, "sha256", hash(text), "sourcePath", sourcePath,
                "bytes", text.getBytes(StandardCharsets.UTF_8).length);
    }

    private static Map<String, Object> sentDocument(String name, String text) {
        return Map.of("name", name, "sha256", hash(text), "bytes", text.getBytes(StandardCharsets.UTF_8).length);
    }

    static List<Map<String, Object>> validateAnswer(String content, List<String> expected) {
        // Ohne quellengebundene Abdeckungsanforderungen ist keine Bestätigung zulässig.
        return LlmAnswerValidator.validate(content, expected, List.of());
    }
    /** Keine Fachfreigabe: bewahrt lesbare Warnsignale aus ungültigen Antworten bekannter Fälle. */
    static List<Map<String,Object>> provisionalFailures(String content, List<String> expected, int batch, int attempt,
                                                       String requestFile, String responseFile) {
        Object parsed;
        try { parsed = Json.parse(content); } catch (RuntimeException invalid) { return List.of(); }
        if (!(parsed instanceof Map<?,?> answer) || !(answer.get("cases") instanceof List<?> cases)) return List.of();
        List<Map<String,Object>> observations = new ArrayList<>();
        for (Object value : cases) {
            if (!(value instanceof Map<?,?> c) || !(c.get("caseId") instanceof String id) || !expected.contains(id)) continue;
            List<String> signals = new ArrayList<>();
            if ("FAIL".equals(c.get("verdict"))) signals.add("verdict=FAIL");
            if (c.get("findings") instanceof List<?> findings) for (Object finding : findings)
                if (finding instanceof String text && !text.isBlank()) signals.add("Befund: " + text);
            if (c.get("checks") instanceof List<?> checks) for (Object checkValue : checks)
                if (checkValue instanceof Map<?,?> check && "FAIL".equals(check.get("verdict")))
                    signals.add("Prüfpunkt " + (check.get("checkId") instanceof String checkId ? checkId : "?")
                            + " meldet FAIL: " + (check.get("reasoning") instanceof String reason && !reason.isBlank() ? reason : "Begründung fehlt"));
            if (signals.isEmpty()) continue;
            if (c.get("reasoning") instanceof String reason && !reason.isBlank()) signals.add("Begründung: " + reason);
            Map<String,Object> observation = new LinkedHashMap<>();
            observation.put("caseId", id); observation.put("source", "UNVALIDATED_MODEL_RESPONSE"); observation.put("batch", batch);
            observation.put("attempt", attempt); observation.put("requestFile", requestFile); observation.put("responseFile", responseFile);
            observation.put("reason", String.join(" | ", signals)); observations.add(observation);
        }
        return List.copyOf(observations);
    }
    private static List<Map<String,Object>> collectProvisional(List<Map<String,Object>> attempts) {
        List<Map<String,Object>> provisional = new ArrayList<>();
        for (Map<String,Object> attempt : attempts) if (attempt.get("provisionalFailures") instanceof List<?> rows)
            for (Object row : rows) if (row instanceof Map<?,?> observation) {
                Map<String,Object> copy = new LinkedHashMap<>(); observation.forEach((k,v)->copy.put(String.valueOf(k),v)); provisional.add(copy);
            }
        return List.copyOf(provisional);
    }
    private static List<Map<String, Object>> requirements(List<ReviewCase> cases) {
        return cases.stream().map(c -> LlmCalculationReference.requirement(c.id(), c.text())).toList();
    }
    private String contractString(String key) {
        if (responseContract == null || !(responseContract.get(key) instanceof String value) || value.isBlank())
            throw new IllegalStateException("Antwortvertrag unvollständig: " + key);
        return value;
    }

    private ApiReply request(LlmConfig.Settings s, List<Map<String, String>> messages, int maxTokens) throws IOException, InterruptedException {
        return request(s, messages, maxTokens, null);
    }
    private ApiReply request(LlmConfig.Settings s, List<Map<String, String>> messages, int maxTokens, Path requestFile) throws IOException, InterruptedException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", s.model()); body.put("messages", messages); body.put("stream", false);
        body.put("temperature", 0.1); body.put("max_tokens", maxTokens); body.put("thinking", Map.of("type", "disabled"));
        body.put("response_format", Map.of("type", "json_object"));
        String serialized = Json.write(body);
        if (requestFile != null) writeNew(requestFile, serialized);
        HttpRequest request = HttpRequest.newBuilder(URI.create(s.baseUrl() + "/chat/completions"))
                .timeout(Duration.ofSeconds(s.timeoutSeconds())).header("Authorization", "Bearer " + s.apiKey())
                .header("Content-Type", "application/json; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(serialized, StandardCharsets.UTF_8)).build();
        CompletableFuture<HttpResponse<byte[]>> pending = client.sendAsync(request, info -> new LimitedBodySubscriber());
        HttpResponse<byte[]> response;
        try { response = pending.get(s.timeoutSeconds(), TimeUnit.SECONDS); }
        catch (TimeoutException e) { pending.cancel(true); throw new java.net.http.HttpTimeoutException("LLM-Zeitüberschreitung"); }
        catch (InterruptedException e) { pending.cancel(true); throw e; }
        catch (ExecutionException e) {
            if (e.getCause() instanceof java.net.http.HttpTimeoutException timeout) throw timeout;
            throw new IOException("LLM-Verbindung oder Antwortübernahme fehlgeschlagen");
        }
        byte[] bytes = response.body();
        String raw = redact(new String(bytes, StandardCharsets.UTF_8), s.apiKey());
        if (requestFile != null) writeNew(requestFile.resolveSibling(requestFile.getFileName().toString().replace("request-", "response-")), redact(raw, s.apiKey()));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("LLM-HTTP " + response.statusCode() + "; Basisadresse, Modell, Zugang und Kontingent prüfen");
        }
        try {
            Map<?, ?> object = (Map<?, ?>) Json.parse(raw);
            List<?> choices = (List<?>) object.get("choices");
            if (choices == null || choices.size() != 1) throw new IllegalArgumentException();
            Map<?, ?> choice = (Map<?, ?>) choices.getFirst(), message = (Map<?, ?>) choice.get("message");
            String content = (String) message.get("content");
            if (content == null || content.isBlank()) throw new IllegalArgumentException();
            String model = object.get("model") instanceof String value ? value : "nicht angegeben";
            String requestId = object.get("id") instanceof String value ? value : response.headers().firstValue("x-request-id").orElse("nicht angegeben");
            return new ApiReply(content, String.valueOf(choice.get("finish_reason")), model, raw, hash(serialized), response.statusCode(), requestId);
        } catch (RuntimeException e) { throw new InvalidModelResponseException("LLM-Antwort ohne eindeutigen vollständigen Nachrichteninhalt"); }
    }

    private static void requireKey(LlmConfig.Settings settings) {
        if (settings.apiKey().isBlank()) throw new IllegalStateException("Kein LLM-API-Schlüssel konfiguriert");
    }
    private static void checkCount(int n) {
        if (n < 2 || n > 1000) throw new IllegalArgumentException("Fallzahl muss zwischen 2 und 1000 liegen; mindestens ein Positiv- und ein Negativfall");
    }
    private String userMessage(List<String> ids, String prompt, String law, String batchName, String batchText,
                               List<Map<String, Object>> requirements) {
        String documents = namedDocument("llm-pruefauftrag.txt", prompt)
                + namedDocument("gesetzesgrundlagen-2026.md", law) + namedDocument(batchName, batchText);
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("caseIds", ids);
        envelope.put("requirements", requirements.stream().map(LlmCalculationReference::coverage).toList());
        envelope.put("instruction", contractString("instruction"));
        return Json.write(envelope) + "\n\n" + documents;
    }

    private static String namedDocument(String name, String text) {
        return "\n=== BEGIN DOCUMENT " + name + " ===\n" + text + "\n=== END DOCUMENT " + name + " ===\n";
    }
    private static String readDocument(Path path) throws IOException {
        if (!Files.isRegularFile(path) || Files.size(path) > MAX_DOCUMENT_BYTES) throw new IOException("Prüfdokument fehlt oder überschreitet 64 MiB: " + path.getFileName());
        String text = Files.readString(path, StandardCharsets.UTF_8);
        if (text.isBlank()) throw new IOException("Leeres Prüfdokument: " + path.getFileName());
        return text;
    }
    private String relative(Path path) { return root.relativize(path.toAbsolutePath().normalize()).toString().replace('\\', '/'); }
    private static void writeNew(Path path, String text) throws IOException {
        Files.writeString(path, text, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }
    private static void writeAtomic(Path path, String text) throws IOException {
        Path temporary = Files.createTempFile(path.getParent(), ".review-state-", ".tmp");
        try {
            Files.writeString(temporary, text, StandardCharsets.UTF_8);
            CertificateSuiteState.boundedReplace(temporary, path);
        } finally { Files.deleteIfExists(temporary); }
    }
    static String sha256(byte[] bytes) {
        try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private static String hash(String text) { return sha256(text.getBytes(StandardCharsets.UTF_8)); }
    private static String redact(String text, String key) { return key.isEmpty() ? text : text.replace(key, "[API-SCHLUESSEL ENTFERNT]"); }

    private void restoreLatest() {
        Path logs = root.resolve("testsweet/logs");
        if (!Files.isDirectory(logs)) return;
        Map<String, Object> out = new LinkedHashMap<>();
        try (var stream = Files.list(logs)) {
            Path latest = null; String latestTime = "";
            for (Path directory : stream.filter(Files::isDirectory)
                    .filter(p -> p.getFileName().toString().startsWith("llm-review-")).toList()) {
                Path startFile = directory.resolve("started.json"), resultFile = directory.resolve("result.json");
                if (!Files.isRegularFile(startFile) && !Files.isRegularFile(resultFile)) continue; // Ein Vorschaupaket ist kein Testlauf.
                String time;
                if (Files.isRegularFile(startFile)) {
                    try { time = String.valueOf(((Map<?, ?>) Json.parse(readDocument(startFile))).get("startedAt")); }
                    catch (Exception invalid) { time = Files.getLastModifiedTime(startFile).toInstant().toString(); }
                } else time = Files.getLastModifiedTime(resultFile).toInstant().toString();
                if (time.compareTo(latestTime) > 0) { latest = directory; latestTime = time; }
            }
            if (latest == null) return;
            latestDirectory = latest; out.put("artifactDirectory", latest.toString());
            Path resultFile = latest.resolve("result.json"), statusFile = latest.resolve("status.json");
            Path stateFile = Files.isRegularFile(resultFile) ? resultFile : statusFile;
            if (!Files.isRegularFile(stateFile)) throw new IOException("Begonnene Prüfung ohne gespeicherten Zwischenstand");
            Map<?, ?> result = (Map<?, ?>) Json.parse(readDocument(stateFile));
            result.forEach((k,v) -> out.put(String.valueOf(k),v));
            out.put("running", false); out.put("artifactDirectory", latest.toString());
            if (!Files.isRegularFile(resultFile) || "RUNNING".equals(result.get("state")) || "PREPARING".equals(result.get("state"))) {
                out.put("state", "ERROR"); out.put("message", "Begonnene LLM-Prüfung wurde unterbrochen; Logdateien auswerten");
                out.put("transportVerified", false);
            }
            validateStored(latest);
            Map<?, ?> manifest = (Map<?, ?>) Json.parse(readDocument(latest.resolve("manifest.json")));
            addFindings(out, (List<Map<String,Object>>)result.get("cases"), (List<Map<String,Object>>)manifest.get("caseRequirements"));
            progress = Map.copyOf(out);
        } catch (Exception e) {
            out.put("state", "UNVERIFIED"); out.put("running", false); out.put("transportVerified", false);
            out.put("message", "Letzte begonnene LLM-Prüfung ist unvollständig oder nicht mehr verifizierbar; Logdateien auswerten");
            out.put("integrityError", safeError(e)); progress = Map.copyOf(out);
        }
    }

    private static List<ReviewCase> batchCases(String text) {
        Matcher matcher = Pattern.compile("(?m)^# Fall-ID: ([^\\r\\n]+)\\r?$").matcher(text);
        List<String> ids = new ArrayList<>(); List<Integer> starts = new ArrayList<>();
        while (matcher.find()) { ids.add(matcher.group(1)); starts.add(matcher.end()); }
        List<ReviewCase> cases = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) cases.add(new ReviewCase(ids.get(i), ids.get(i).split(":", 2)[0],
                text.substring(starts.get(i), i + 1 < starts.size() ? text.lastIndexOf("# Fall-ID:", starts.get(i + 1)) : text.length()).stripLeading()));
        return cases;
    }

    /** Fehlermeldung des letzten gescheiterten Versuchs derselben Fallgruppe vor dem Erfolg. */
    private static String letzterFehlversuch(List<Map<String, Object>> attempts, int batch, int vorVersuch) {
        String fehler = null;
        int beste = 0;
        for (Map<String, Object> attempt : attempts) {
            if (!(attempt.get("batch") instanceof Number b) || b.intValue() != batch
                    || !(attempt.get("attempt") instanceof Number a) || a.intValue() >= vorVersuch
                    || attempt.get("error") == null || a.intValue() <= beste) continue;
            beste = a.intValue();
            fehler = String.valueOf(attempt.get("error"));
        }
        return fehler;
    }

    private void validateStored(Path directory) throws IOException {
        String manifestText = readDocument(directory.resolve("manifest.json"));
        Map<?, ?> manifest = (Map<?, ?>) Json.parse(manifestText);
        boolean terminal = Files.isRegularFile(directory.resolve("result.json"));
        Map<?, ?> result = (Map<?, ?>) Json.parse(readDocument(directory.resolve(terminal ? "result.json" : "status.json")));
        if (!LlmAnswerValidator.schemaVersion2(manifest.get("schemaVersion"))
                || !LlmAnswerValidator.schemaVersion2(result.get("schemaVersion"))
                || !Files.isRegularFile(directory.resolve("started.json"))
                || !"LLM_REVIEW".equals(manifest.get("kind")) || !"LLM_REVIEW".equals(result.get("kind"))
                || !manifest.get("reviewId").equals(result.get("reviewId")) || !manifest.get("model").equals(result.get("model"))
                || !hash(manifestText).equals(result.get("manifestSha256"))) throw new IOException("Manifestbindung ungültig");
        List<?> ids = (List<?>) manifest.get("selectedCaseIds");
        List<String> expected = ids.stream().map(String.class::cast).toList();
        Map<?, ?> start = (Map<?, ?>) Json.parse(readDocument(directory.resolve("started.json")));
        if (!manifest.get("reviewId").equals(start.get("reviewId")) || !manifest.get("model").equals(start.get("model"))) throw new IOException("Startnachweis passt nicht zur Prüfung");
        List<Map<String, Object>> allRequirements = (List<Map<String, Object>>) manifest.get("caseRequirements");
        responseContract = (Map<?, ?>) Json.parse(readDocument(directory.resolve("llm-response-contract.json")));
        if (!LlmAnswerValidator.schemaVersion2(responseContract.get("schemaVersion"))) throw new IOException("Antwortvertrag Version 2 fehlt");
        verifyHash(directory, "llm-response-contract.json", String.valueOf(manifest.get("responseContractSha256")));
        verifyHash(root, "doc/testzertifikat-werkzeug/llm-response-contract.json", String.valueOf(manifest.get("responseContractSha256")));
        if (!(manifest.get("buildEvidence") instanceof Map<?, ?> evidence) || !buildVerifier.matches(evidence, root)) throw new IOException("Getesteter Software-/Buildstand hat sich geändert oder ist nicht belegt");
        List<String> completedIds = ((List<?>) result.get("completedCaseIds")).stream().map(String.class::cast).toList();
        if (!expected.containsAll(completedIds) || new HashSet<>(completedIds).size() != completedIds.size()) throw new IOException("Ergebnis enthält unerwartete/doppelte Fälle");
        LlmAnswerValidator.validate(Json.write(Map.of("schemaVersion", 2, "caseIds", completedIds, "cases", result.get("cases"))), completedIds, allRequirements);
        for (Object value : (List<?>) manifest.get("sources")) {
            Map<?, ?> source = (Map<?, ?>) value;
            verifyHash(root, String.valueOf(source.get("path")), String.valueOf(source.get("sha256")));
            verifyHash(directory, String.valueOf(source.get("snapshotPath")), String.valueOf(source.get("sha256")));
            verifyHash(root, String.valueOf(source.get("runStatePath")), String.valueOf(source.get("runStateSha256")));
            Source newest = newestSource(String.valueOf(source.get("category")));
            if (!relative(newest.path()).equals(source.get("path"))) throw new IOException("Neuere Prüfdaten vorhanden");
        }
        for (Object value : (List<?>) manifest.get("documents")) {
            Map<?, ?> doc = (Map<?, ?>) value;
            verifyHash(directory, String.valueOf(doc.get("path")), String.valueOf(doc.get("sha256")));
            if (doc.get("sourcePath") instanceof String original && !original.isBlank()) verifyHash(root, original, String.valueOf(doc.get("sha256")));
        }
        List<?> checks = (List<?>) result.get("transportChecks"), batches = (List<?>) manifest.get("batches");
        boolean full = Boolean.TRUE.equals(result.get("transportVerified"));
        if (checks.size() > batches.size() || full && checks.size() != batches.size()) throw new IOException("Dateiübergabe nicht vollständig belegt");
        List<String> received = new ArrayList<>();
        List<Map<String, Object>> responseCases = new ArrayList<>();
        List<Map<String, Object>> derivedRequirements = new ArrayList<>();
        for (Object value : batches) {
            Map<?, ?> batch = (Map<?, ?>) value;
            verifyHash(directory, String.valueOf(batch.get("name")), String.valueOf(batch.get("sha256")));
            derivedRequirements.addAll(requirements(batchCases(readDocument(directory.resolve(String.valueOf(batch.get("name")))))));
        }
        if (!derivedRequirements.equals(allRequirements)) throw new IOException("Abdeckungsanforderungen stimmen nicht mit Quelldaten überein");
        List<Map<String,Object>> attempts = (List<Map<String,Object>>)result.get("attemptChecks");
        List<Map<String,Object>> observedProvisional = new ArrayList<>();
        for (Map<String,Object> attempt : attempts) {
            List<Map<String,Object>> observed = List.of();
            if (!Boolean.TRUE.equals(attempt.get("responseValidated")) && attempt.get("responseSha256") instanceof String responseHash) {
                if (!(attempt.get("batch") instanceof Number batchNumber) || batchNumber.intValue() < 1 || batchNumber.intValue() > batches.size()
                        || !(attempt.get("attempt") instanceof Number attemptNumber) || attemptNumber.intValue() < 1 || attemptNumber.intValue() > MAX_SEND_ATTEMPTS)
                    throw new IOException("Ungültige Identität eines Sendeversuchs");
                String responseFile = String.valueOf(attempt.get("responseFile")), requestFile = String.valueOf(attempt.get("requestFile"));
                verifyHash(directory,responseFile,responseHash);
                if (attempt.get("requestSha256") instanceof String requestHash) verifyHash(directory,requestFile,requestHash);
                List<String> requested = ((List<?>)((Map<?,?>)batches.get(batchNumber.intValue()-1)).get("caseIds")).stream().map(String.class::cast).toList();
                try {
                    Map<?,?> raw = (Map<?,?>)Json.parse(readDocument(directory.resolve(responseFile)));
                    List<?> choices = (List<?>)raw.get("choices");
                    if (choices != null && choices.size() == 1 && ((Map<?,?>)((Map<?,?>)choices.getFirst()).get("message")).get("content") instanceof String content)
                        observed = provisionalFailures(content,requested,batchNumber.intValue(),attemptNumber.intValue(),requestFile,responseFile);
                } catch (RuntimeException ignored) { /* Nicht lesbares JSON ist ausdrücklich kein behaupteter Fachbefund. */ }
            }
            Object declared = attempt.get("provisionalFailures") == null ? List.of() : attempt.get("provisionalFailures");
            if (!Json.parse(Json.write(observed)).equals(declared)) throw new IOException("Frühere unverifizierte Modellbefunde fehlen oder widersprechen Rohantwort");
            observedProvisional.addAll(observed);
        }
        Object declaredProvisional = result.get("provisionalFailures") == null ? List.of() : result.get("provisionalFailures");
        if (!Json.parse(Json.write(observedProvisional)).equals(declaredProvisional)) throw new IOException("Unverifizierte Modellbefunde stimmen nicht mit Sendeversuchen überein");
        for (int n = 0; n < checks.size(); n++) {
            Map<?, ?> check = (Map<?, ?>) checks.get(n), batch = (Map<?, ?>) batches.get(n);
            verifyHash(directory, String.valueOf(check.get("requestFile")), String.valueOf(check.get("requestSha256")));
            verifyHash(directory, String.valueOf(check.get("responseFile")), String.valueOf(check.get("responseSha256")));
            verifyHash(directory, String.valueOf(batch.get("name")), String.valueOf(batch.get("sha256")));
            if (!Boolean.TRUE.equals(check.get("responseValidated")) || !"stop".equals(check.get("finishReason"))
                    || !(check.get("httpStatus") instanceof Number httpStatus) || httpStatus.intValue() < 200 || httpStatus.intValue() >= 300
                    || !manifest.get("model").equals(check.get("requestedModel"))
                    || !String.valueOf(manifest.get("model")).equalsIgnoreCase(String.valueOf(check.get("returnedModel")))) throw new IOException("Modellantwort nicht vollständig belegt");
            Map<?, ?> request = (Map<?, ?>) Json.parse(readDocument(directory.resolve(String.valueOf(check.get("requestFile")))));
            if (!manifest.get("model").equals(request.get("model")) || !Boolean.FALSE.equals(request.get("stream"))) throw new IOException("Angefordertes Modell stimmt nicht überein");
            List<String> requestedIds = ((List<?>) batch.get("caseIds")).stream().map(String.class::cast).toList();
            List<Map<String, Object>> batchRequirements = allRequirements.stream().filter(r -> requestedIds.contains(r.get("caseId"))).toList();
            List<?> messages = (List<?>) request.get("messages");
            String expectedUser = userMessage(requestedIds, readDocument(directory.resolve("llm-pruefauftrag.txt")),
                    readDocument(directory.resolve("gesetzesgrundlagen-2026.md")), String.valueOf(batch.get("name")),
                    readDocument(directory.resolve(String.valueOf(batch.get("name")))), batchRequirements);
            String gesendet = String.valueOf(((Map<?, ?>) messages.getLast()).get("content"));
            boolean auftragGilt = expectedUser.equals(gesendet);
            // Wiederholungssendungen dürfen ausschließlich den exakt rekonstruierbaren
            // Korrekturzusatz tragen: gleicher Auftrag plus Mängel- und Abdeckungsliste
            // des unmittelbar vorherigen Fehlversuchs derselben Fallgruppe.
            if (!auftragGilt && Boolean.TRUE.equals(check.get("korrekturHinweis"))
                    && check.get("attempt") instanceof Number versuch) {
                String vorherigerFehler = letzterFehlversuch(attempts, n + 1, versuch.intValue());
                if (vorherigerFehler != null
                        && (expectedUser + korrekturAnweisung(vorherigerFehler, batchRequirements)).equals(gesendet)) {
                    auftragGilt = true;
                }
            }
            if (messages.size() != 2 || !"system".equals(((Map<?, ?>) messages.getFirst()).get("role"))
                    || !contractString("systemMessage").equals(((Map<?, ?>) messages.getFirst()).get("content"))
                    || !"user".equals(((Map<?, ?>) messages.getLast()).get("role"))
                    || !auftragGilt) {
                throw new IOException("Gesendeter Auftrag stimmt nicht mit eingefrorenen Dokumenten/Fall-IDs überein");
            }
            StringBuilder user = new StringBuilder();
            for (Object row : (List<?>) request.get("messages")) {
                Map<?, ?> m = (Map<?, ?>) row;
                if ("user".equals(m.get("role"))) user.append(m.get("content"));
            }
            List<?> sent = (List<?>) check.get("documents");
            Set<String> names = new HashSet<>();
            for (Object row : sent) {
                Map<?, ?> doc = (Map<?, ?>) row; String name = String.valueOf(doc.get("name"));
                String text = readDocument(directory.resolve(name));
                verifyHash(directory, name, String.valueOf(doc.get("sha256")));
                if (!names.add(name) || !user.toString().contains(namedDocument(name, text).stripLeading())
                        || !(doc.get("bytes") instanceof Number count) || count.longValue() != text.getBytes(StandardCharsets.UTF_8).length) {
                    throw new IOException("Gesendete Dokumente stimmen nicht mit Snapshots überein");
                }
            }
            if (!names.equals(Set.of("llm-pruefauftrag.txt", "gesetzesgrundlagen-2026.md", String.valueOf(batch.get("name"))))) throw new IOException("Drei Dokumente nicht belegt");
            Map<?, ?> response = (Map<?, ?>) Json.parse(readDocument(directory.resolve(String.valueOf(check.get("responseFile")))));
            List<?> choices = (List<?>) response.get("choices");
            if (choices.size() != 1 || !String.valueOf(manifest.get("model")).equalsIgnoreCase(String.valueOf(response.get("model")))) throw new IOException("Antwortmodell ungültig");
            Map<?, ?> choice = (Map<?, ?>) choices.getFirst();
            if (!"stop".equals(choice.get("finish_reason"))) throw new IOException("Unvollständige Modellantwort");
            List<String> batchIds = ((List<?>) batch.get("caseIds")).stream().map(String.class::cast).toList();
            responseCases.addAll(LlmAnswerValidator.validate((String) ((Map<?, ?>) choice.get("message")).get("content"), batchIds, batchRequirements));
            received.addAll(batchIds);
        }
        List<String> requiredReceived = full ? expected : completedIds;
        if (received.size() != requiredReceived.size() || !new HashSet<>(received).equals(new HashSet<>(requiredReceived))) throw new IOException("Fallabdeckung ungültig");
        Map<String, Object> rawCases = new LinkedHashMap<>(), storedCases = new LinkedHashMap<>();
        for (Map<String, Object> c : responseCases) rawCases.put(String.valueOf(c.get("caseId")), c);
        for (Object row : (List<?>) result.get("cases")) {
            Map<?, ?> c = (Map<?, ?>) row; storedCases.put(String.valueOf(c.get("caseId")), c);
        }
        if (!rawCases.equals(storedCases)) throw new IOException("Gespeicherte Fallbewertungen widersprechen Modellantworten");
        List<Map<String, Object>> reference = LlmAnswerValidator.referenceChecks(responseCases, allRequirements);
        if (!reference.equals(result.get("referenceChecks"))) throw new IOException("Gespeicherte Referenzprüfung widerspricht Nachrechnung");
        String derived = LlmAnswerValidator.overall(responseCases, reference);
        if (derived.equals("PASS") && !observedProvisional.isEmpty()) derived = "UNVERIFIED";
        if (full && !derived.equals(result.get("state"))) throw new IOException("Widersprüchliches Gesamtergebnis");
    }

    private static void verifyHash(Path base, String name, String expected) throws IOException {
        Path path = base.resolve(name).normalize();
        if (!path.startsWith(base) || !Files.isRegularFile(path) || !path.toRealPath().startsWith(base.toRealPath())
                || !sha256(Files.readAllBytes(path)).equals(expected)) {
            throw new IOException("Prüfdatei fehlt oder Hash weicht ab");
        }
    }

    /** Begrenzte Antwortaufnahme; die Deadline umfasst auch einen stockenden Body. */
    private static final class LimitedBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> result = new CompletableFuture<>();
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private Flow.Subscription subscription;
        @Override public CompletionStage<byte[]> getBody() { return result; }
        @Override public void onSubscribe(Flow.Subscription s) { subscription = s; s.request(1); }
        @Override public void onNext(List<ByteBuffer> buffers) {
            for (ByteBuffer buffer : buffers) {
                if ((long) bytes.size() + buffer.remaining() > 8L * 1024 * 1024) {
                    subscription.cancel(); result.completeExceptionally(new IOException("Modellantwort überschreitet 8 MiB")); return;
                }
                byte[] chunk = new byte[buffer.remaining()]; buffer.get(chunk); bytes.writeBytes(chunk);
            }
            subscription.request(1);
        }
        @Override public void onError(Throwable error) { result.completeExceptionally(error); }
        @Override public void onComplete() { result.complete(bytes.toByteArray()); }
    }
    private static String safeError(Exception e) {
        if (e instanceof java.net.http.HttpTimeoutException) return "LLM-Zeitüberschreitung; es wurde nichts bestätigt";
        if (e instanceof java.net.ConnectException) return "LLM-Verbindung konnte nicht hergestellt werden";
        if (e instanceof InterruptedException) return "LLM-Prüfung unterbrochen; es wurde nichts bestätigt";
        // Eigene Validierungs-/HTTP-Texte enthalten keine Provider-Antwort oder Zugangsdaten.
        if (e instanceof IllegalArgumentException || e instanceof IllegalStateException || e instanceof IOException) {
            return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        }
        return "LLM-Prüfung fehlgeschlagen: " + e.getClass().getSimpleName();
    }
    @Override public synchronized void close() { closed = true; executor.shutdownNow(); client.close(); }

    interface BuildVerifier {
        Map<String, Object> capture(Path root);
        boolean matches(Map<?, ?> evidence, Path root);
    }

    private record ReviewCase(String id, String category, String text) { }
    private record Source(Path path, String runId, String category, String text, String header, List<ReviewCase> cases) { }
    private record Batch(String name, String text, List<ReviewCase> cases) { }
    private record ReviewPackage(String id, Path directory, String prompt, String law, List<ReviewCase> cases, List<Batch> batches) { }
    private record ApiReply(String content, String finishReason, String model, String raw, String requestSha256, int httpStatus, String requestId) { }
    private static final class InvalidModelResponseException extends IOException {
        private InvalidModelResponseException(String message) { super(message); }
    }
    private static final class RetryExhaustedException extends IllegalStateException {
        private RetryExhaustedException(String message) { super(message); }
    }
}
