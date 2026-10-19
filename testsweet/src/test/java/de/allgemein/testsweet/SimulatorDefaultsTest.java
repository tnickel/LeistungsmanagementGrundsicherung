package de.allgemein.testsweet;

import de.allgemein.lmg.web.Json;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/** Standardumfänge an den echten CLI-/HTTP-/Konfigurations- und Sampler-Eintrittspunkten. */
class SimulatorDefaultsTest {
    @TempDir Path root;

    @Test
    void cliUndHttpStartenStandardmaessigHundertFaelleJeKategorie() {
        for (boolean web : List.of(false, true)) {
            var config = SweetMain.laufKonfiguration(new String[]{web ? "web" : "run"}, web);
            assertEquals(100, config.auftraege());
            assertEquals(Simulator.Kategorie.POSITIV, config.kategorie());
        }
        for (var category : List.of(Simulator.Kategorie.POSITIV, Simulator.Kategorie.NEGATIV)) {
            assertEquals(100, SweetServer.laufKonfiguration(Map.of(), category).auftraege());
        }
        assertEquals(17, SweetMain.laufKonfiguration(new String[]{"run", "3", "17"}, false).auftraege());
        assertEquals(29, SweetServer.laufKonfiguration(Map.of("auftraege", 29), Simulator.Kategorie.POSITIV).auftraege());
    }

    @Test
    void llmKonfigurationUndEchterHttpEndpunktLiefernZweihundertAlsStandard() throws Exception {
        assertEquals(200, new LlmConfig(root, Map.of()).read().cases());
        try (var server = new SweetServer(new Simulator(), 0, root);
             var client = HttpClient.newHttpClient()) {
            server.start();
            var response = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port()
                    + "/api/llm/config")).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
            assertEquals(200, ((Number) ((Map<?, ?>) Json.parse(response.body())).get("cases")).intValue());
        }
        var config = new LlmConfig(root, Map.of());
        config.save(Map.of("cases", 18));
        assertEquals(18, config.read().cases(), "Ein ausdrücklich gewählter Umfang bleibt erhalten");
    }

    @Test
    void zertifikatsmatrixErhoehtWebAufHundertUndBehaeltBreiteFachlaeufe() {
        assertEquals(7, CertificateSuiteWorker.JOBS.size());
        for (var category : List.of(Simulator.Kategorie.POSITIV, Simulator.Kategorie.NEGATIV)) {
            var web = CertificateSuiteWorker.JOBS.stream().filter(j -> j.mode().equals("WEB") && j.category() == category).findFirst().orElseThrow();
            var fach = CertificateSuiteWorker.JOBS.stream().filter(j -> j.mode().equals("FACH") && j.category() == category).findFirst().orElseThrow();
            assertEquals(100, web.orders());
            assertEquals(500, fach.orders(), "Fünfhunderterabdeckung wird nicht verkleinert");
        }
    }

    @Test
    void samplerauswahlIstGenauHundertPositivPlusHundertNegativInStratumReihenfolge() {
        List<String> positive = IntStream.rangeClosed(1, 110).mapToObj(i -> "P" + i).toList();
        List<String> negative = IntStream.rangeClosed(1, 170).mapToObj(i -> "N" + i).toList();
        var sample = LlmReviewService.balancedSample(positive, negative, 200);
        assertEquals(200, sample.size());
        assertEquals(100, sample.stream().filter(c -> c.startsWith("P")).count());
        assertEquals(100, sample.stream().filter(c -> c.startsWith("N")).count());
        assertEquals(List.of("P1", "N1", "P2", "N2"), sample.subList(0, 4));
        assertEquals(List.of("P100", "N100"), sample.subList(198, 200));
    }

    @Test
    void fehlendeKategoriequoteWirdTrotzAusreichenderGesamtzahlAbgelehnt() {
        var shortGroup = IntStream.range(0, 99).boxed().toList();
        var longGroup = IntStream.range(0, 201).boxed().toList();
        for (var groups : List.of(List.of(shortGroup, longGroup), List.of(longGroup, shortGroup))) {
            var error = assertThrows(IllegalArgumentException.class,
                    () -> LlmReviewService.balancedSample(groups.get(0), groups.get(1), 200));
            assertTrue(error.getMessage().contains("100 Positivfälle und 100 Negativfälle"));
        }
        assertEquals(List.of("P1", "N1", "P2", "N2", "P3"),
                LlmReviewService.balancedSample(List.of("P1", "P2", "P3"), List.of("N1", "N2"), 5));
    }
}
