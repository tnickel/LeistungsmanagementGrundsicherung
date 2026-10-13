package de.allgemein.testsweet;

import de.allgemein.lmg.web.Json;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class SweetServerLlmHttpTest {
    @TempDir Path root;

    @Test void credentialsRemainServerSideAndForeignOriginCannotChangeThem() throws Exception {
        try (SweetServer server = new SweetServer(new Simulator(), 0, root);
             HttpClient client = HttpClient.newHttpClient()) {
            server.start();
            URI uri = URI.create("http://127.0.0.1:" + server.port() + "/api/llm/config");
            var saved = client.send(HttpRequest.newBuilder(uri).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"apiKey\":\"secret-for-http-test\",\"cases\":6}")).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, saved.statusCode());
            assertFalse(saved.body().contains("secret-for-http-test"));
            assertEquals(Boolean.TRUE, ((java.util.Map<?, ?>)Json.parse(saved.body())).get("apiKeyConfigured"));
            var rejected = client.send(HttpRequest.newBuilder(uri).header("Origin", "https://foreign.example")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"clearApiKey\":true}")).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(403, rejected.statusCode());
            var read = client.send(HttpRequest.newBuilder(uri).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, read.statusCode());
            assertFalse(read.body().contains("secret-for-http-test"));
            assertEquals(Boolean.TRUE, ((java.util.Map<?, ?>)Json.parse(read.body())).get("apiKeyConfigured"));
        }
    }

    @Test void mutatingEndpointsRejectGetAndInvalidConfigurationDoesNotPersist() throws Exception {
        try (SweetServer server = new SweetServer(new Simulator(), 0, root);
             HttpClient client = HttpClient.newHttpClient()) {
            server.start();
            String base = "http://127.0.0.1:" + server.port();
            for (String path : new String[]{"/api/llm/start", "/api/llm/test", "/api/stop"}) {
                assertEquals(405, client.send(HttpRequest.newBuilder(URI.create(base + path)).GET().build(),
                        HttpResponse.BodyHandlers.ofString()).statusCode(), path);
            }
            var bad = client.send(HttpRequest.newBuilder(URI.create(base + "/api/llm/config"))
                    .POST(HttpRequest.BodyPublishers.ofString("{\"baseUrl\":\"http://external.example\"}")).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(400, bad.statusCode());
            var good = client.send(HttpRequest.newBuilder(URI.create(base + "/api/llm/config")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertTrue(good.body().contains("https://api.z.ai/"));
        }
    }
}
