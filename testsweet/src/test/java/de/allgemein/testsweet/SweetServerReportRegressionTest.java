package de.allgemein.testsweet;

import de.allgemein.lmg.web.Json;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/** HTTP-Gegenproben für alte Freigaben und Eingabeablehnung; benötigt keine Datenbank/API. */
class SweetServerReportRegressionTest {
    @TempDir Path root;

    @Test void runningReviewNeverServesPreviousGreenReviewedReport() throws Exception {
        Simulator simulator=preparedReports();
        try(SweetServer server=new SweetServer(simulator,0,root);HttpClient client=HttpClient.newHttpClient()) {
            Map<String,Object> running=Map.of("state","RUNNING","running",true,"reviewId","new-review",
                    "artifactDirectory",root.resolve("testsweet/logs/llm-review-new").toString(),"completedCases",0,"totalCases",2);
            set(llm(server),"progress",running);
            seedOldReviewedReport(server,simulator,running);
            server.start();
            var pdf=get(client,server,"/api/pdf");
            assertEquals(200,pdf.statusCode());assertEquals("%PDF-CURRENT-UNVERIFIED",pdf.body());
            assertFalse(pdf.body().contains("OLD-BESTANDEN"));assertEquals("no-store",pdf.headers().firstValue("Cache-Control").orElseThrow());
            var md=get(client,server,"/api/md");
            assertEquals(200,md.statusCode());assertEquals("gesamtergebnis = UNVERIFIED",md.body());
            assertEquals("no-store",md.headers().firstValue("Cache-Control").orElseThrow());
            assertEquals(404,get(client,server,"/api/llm/report/pdf").statusCode());
        }
    }

    @Test void actualNewReviewStartInvalidatesEarlierReviewedOutputBeforePreparationCanFail() throws Exception {
        Simulator simulator=preparedReports();
        try(SweetServer server=new SweetServer(simulator,0,root);HttpClient client=HttpClient.newHttpClient()) {
            Map<String,Object> old=Map.of("state","PASS","running",false,"reviewId","old-review");
            seedOldReviewedReport(server,simulator,old);
            llm(server).saveConfiguration(Map.of("apiKey","controlled-unused-key","baseUrl","http://127.0.0.1:1/v4"));
            server.start();
            var started=post(client,server,"/api/llm/start","{\"faelle\":2}");
            assertEquals(202,started.statusCode(),started.body());
            // Der Prüfprompt fehlt absichtlich: Vorbereitung scheitert vor jedem Provider-Aufruf.
            var pdf=get(client,server,"/api/pdf");
            assertEquals(200,pdf.statusCode(),pdf.body());
            assertEquals("%PDF-CURRENT-UNVERIFIED",pdf.body());assertFalse(pdf.body().contains("OLD-BESTANDEN"));
            assertNull(field(server,"reviewedPdf"));assertNull(field(server,"reviewedMd"));
        }
    }

    @Test void fractionalAndOversizedInputsAreRejectedWithoutStartingAnyRun() throws Exception {
        Simulator simulator=new Simulator();
        try(SweetServer server=new SweetServer(simulator,0,root);HttpClient client=HttpClient.newHttpClient()) {
            server.start();
            for(String key:new String[]{"mitarbeiter","auftraege","seed","denkzeitMs"}) {
                Map<String,Object> input=new LinkedHashMap<>(Map.of("mitarbeiter",1,"auftraege",2,"seed",42,"denkzeitMs",0));
                input.put(key,new java.math.BigDecimal("1.5"));
                var response=post(client,server,"/api/start",Json.write(input));
                assertEquals(400,response.statusCode(),key+": "+response.body());
                assertTrue(response.body().contains("Ungültige Ganzzahl"));
            }
            for(String value:new String[]{"2.5","2147483648","\"not-a-number\""}) {
                assertEquals(400,post(client,server,"/api/llm/start","{\"faelle\":"+value+"}").statusCode());
                assertEquals(400,post(client,server,"/api/llmpruefung/paket","{\"faelle\":"+value+"}").statusCode());
            }
            assertEquals(Simulator.Phase.BEREIT,simulator.phase());assertNull(simulator.laufStart());
            assertEquals("IDLE",llm(server).status().get("state"));
            assertFalse(Files.exists(root.resolve("testsweet/logs")));
        }
    }

    @Test void uiAndApiResponsesCannotBeCachedAsOldEvidence() throws Exception {
        try(SweetServer server=new SweetServer(new Simulator(),0,root);HttpClient client=HttpClient.newHttpClient()) {
            server.start();
            for(String path:new String[]{"/","/app.js","/style.css","/api/status","/api/llm/status","/api/llm/config"}) {
                var response=get(client,server,path);assertEquals(200,response.statusCode(),path);
                assertEquals("no-store",response.headers().firstValue("Cache-Control").orElse("<fehlt>"),path);
            }
        }
    }

    @Test void privateConfigurationAndLogFilesAreNeverStaticDownloads() throws Exception {
        Path privateConfig=root.resolve("testsweet/llm.local.properties");Files.createDirectories(privateConfig.getParent());
        Files.writeString(privateConfig,"apiKey=controlled-private-canary");
        try(SweetServer server=new SweetServer(new Simulator(),0,root);HttpClient client=HttpClient.newHttpClient()) {
            server.start();
            for(String path:new String[]{"/llm.local.properties","/pom.xml","/logs/result.json","/assets/../llm.local.properties","/assets/%2e%2e/llm.local.properties"}) {
                var response=get(client,server,path);
                assertEquals(404,response.statusCode(),path);
                assertFalse(response.body().contains("controlled-private-canary"));
            }
            assertEquals(200,get(client,server,"/app.js").statusCode());
        }
    }

    private Simulator preparedReports() throws Exception {
        Path run=root.resolve("testsweet/logs/run-current");Files.createDirectories(run);
        Path pdf=run.resolve("testdokumentation.pdf"),md=run.resolve("testdokumentation.md");
        Files.writeString(pdf,"%PDF-CURRENT-UNVERIFIED");Files.writeString(md,"gesamtergebnis = UNVERIFIED");
        Simulator simulator=new Simulator();set(simulator,"laufStart",LocalDateTime.now());set(simulator,"reportsComplete",true);
        set(simulator,"letztesPdf",pdf.toString());set(simulator,"letztesMd",md.toString());return simulator;
    }
    private void seedOldReviewedReport(SweetServer server,Simulator simulator,Map<String,Object> evidence) throws Exception {
        Path pdf=root.resolve("old-reviewed.pdf"),md=root.resolve("old-reviewed.md");
        Files.writeString(pdf,"%PDF-OLD-BESTANDEN");Files.writeString(md,"gesamtergebnis = PASS OLD-BESTANDEN");
        set(server,"reviewedPdf",pdf.toString());set(server,"reviewedMd",md.toString());set(server,"reviewedSource",simulator.letztesMd());
        set(server,"reviewedKey",LlmReviewService.sha256(Json.write(evidence).getBytes(StandardCharsets.UTF_8)));
    }
    private static LlmReviewService llm(SweetServer server) throws Exception {return (LlmReviewService)field(server,"llm");}
    private static Object field(Object target,String name) throws Exception {var field=target.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(target);}
    private static void set(Object target,String name,Object value) throws Exception {var field=target.getClass().getDeclaredField(name);field.setAccessible(true);field.set(target,value);}
    private static HttpResponse<String> get(HttpClient client,SweetServer server,String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+server.port()+path)).timeout(Duration.ofSeconds(10)).GET().build(),HttpResponse.BodyHandlers.ofString());
    }
    private static HttpResponse<String> post(HttpClient client,SweetServer server,String path,String body) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+server.port()+path)).timeout(Duration.ofSeconds(10))
                .header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }
}
