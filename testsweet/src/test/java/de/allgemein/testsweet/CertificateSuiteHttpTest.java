package de.allgemein.testsweet;

import de.allgemein.lmg.web.Json;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

/** HTTP-Vertrag und Laufsperren mit kontrolliertem Controller; startet keine externen Tests. */
class CertificateSuiteHttpTest {
    @TempDir Path root;

    @Test void startsAsynchronouslyAndRejectsConcurrentManualMutationsAndOldReports() throws Exception {
        FakeSuite suite=new FakeSuite(root);
        try(SweetServer server=new SweetServer(new Simulator(),0,root,suite);HttpClient client=HttpClient.newHttpClient()) {
            server.start();
            var started=send(client,server,"POST","/api/zertifikat/tests/start","{\"faelle\":18}");
            assertEquals(202,started.statusCode());assertEquals(18,suite.requestedCases);
            assertEquals("RUNNING",((Map<?,?>)Json.parse(started.body())).get("state"));
            assertEquals(409,send(client,server,"POST","/api/zertifikat/tests/start","{}").statusCode());
            for(String route:new String[]{"/api/start","/api/stop","/api/llm/start","/api/llm/config","/api/llm/test","/api/llmpruefung/paket","/api/zertifikat"}) {
                var response=send(client,server,"POST",route,"{}");
                assertEquals(409,response.statusCode(),route+": "+response.body());
            }
            for(String route:new String[]{"/api/pdf","/api/md","/api/llm/report/pdf","/api/zertifikat/pdf"}) {
                var response=send(client,server,"GET",route,null);
                assertEquals(409,response.statusCode(),route);assertEquals("no-store",response.headers().firstValue("Cache-Control").orElseThrow());
            }
            var status=send(client,server,"GET","/api/zertifikat/tests/status",null);
            assertEquals(200,status.statusCode());assertEquals("no-store",status.headers().firstValue("Cache-Control").orElseThrow());
            assertTrue(status.body().contains("controlled-job"));
            assertEquals(200,send(client,server,"POST","/api/zertifikat/tests/stop","{}").statusCode());
            assertFalse(suite.running());assertEquals(1,suite.cancelled);
        }
    }

    @Test void validatesCountsAndMethodsBeforeStartingAnything() throws Exception {
        FakeSuite suite=new FakeSuite(root);
        try(SweetServer server=new SweetServer(new Simulator(),0,root,suite);HttpClient client=HttpClient.newHttpClient()) {
            server.start();
            for(String value:new String[]{"1","-1","1001","2.5","2147483648","\"wrong\""})
                assertEquals(400,send(client,server,"POST","/api/zertifikat/tests/start","{\"faelle\":"+value+"}").statusCode(),value);
            assertEquals(400,send(client,server,"POST","/api/zertifikat/tests/start","[]").statusCode());
            assertEquals(405,send(client,server,"GET","/api/zertifikat/tests/start",null).statusCode());
            assertEquals(405,send(client,server,"GET","/api/zertifikat/tests/stop",null).statusCode());
            assertEquals(405,send(client,server,"POST","/api/zertifikat/tests/status","{}").statusCode());
            assertEquals(0,suite.started);
            assertEquals(202,send(client,server,"POST","/api/zertifikat/tests/start","{}").statusCode());
            assertEquals(0,suite.requestedCases); // 0 übernimmt die gespeicherte Prüffallzahl.
        }
    }

    @Test void existingSimulatorLlmAndExportActivityCannotBeOvertaken() throws Exception {
        FakeSuite suite=new FakeSuite(root);Simulator simulator=new Simulator();
        try(SweetServer server=new SweetServer(simulator,0,root,suite);HttpClient client=HttpClient.newHttpClient()) {
            server.start();
            AtomicBoolean active=(AtomicBoolean)field(simulator,"laufAktiv");active.set(true);
            assertEquals(409,send(client,server,"POST","/api/zertifikat/tests/start","{}").statusCode());active.set(false);
            LlmReviewService llm=(LlmReviewService)field(server,"llm");
            set(llm,"progress",Map.of("state","RUNNING","running",true));
            assertEquals(409,send(client,server,"POST","/api/zertifikat/tests/start","{}").statusCode());
            set(llm,"progress",Map.of("state","IDLE","running",false));
            AtomicBoolean exporting=(AtomicBoolean)field(server,"certificateBusy");exporting.set(true);
            assertEquals(409,send(client,server,"POST","/api/zertifikat/tests/start","{}").statusCode());exporting.set(false);
            assertEquals(0,suite.started);
        }
    }

    @Test void foreignBrowserOriginCannotStartOrCancelTheSuite() throws Exception {
        FakeSuite suite=new FakeSuite(root);
        try(SweetServer server=new SweetServer(new Simulator(),0,root,suite);HttpClient client=HttpClient.newHttpClient()) {
            server.start();
            for(String route:new String[]{"/api/zertifikat/tests/start","/api/zertifikat/tests/stop"}) {
                HttpRequest request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+server.port()+route))
                    .header("Origin","https://foreign.invalid").POST(HttpRequest.BodyPublishers.ofString("{}")).build();
                assertEquals(403,client.send(request,HttpResponse.BodyHandlers.ofString()).statusCode());
            }
            assertEquals(0,suite.started);assertEquals(0,suite.cancelled);
        }
    }

    @Test void llmDokumenteSindenWieDerPromptPerKlickEinsehbar() throws Exception {
        FakeSuite suite=new FakeSuite(root);
        Path tool=root.resolve("doc/testzertifikat-werkzeug");
        java.nio.file.Files.createDirectories(tool);
        java.nio.file.Files.writeString(tool.resolve("gesetzesgrundlagen-2026.md"),
                "# TEST-GESETZE 2026\n\n§ 42a Prüfnorm\n", java.nio.charset.StandardCharsets.UTF_8);
        java.nio.file.Files.writeString(tool.resolve("llm-pruefauftrag.txt"),
                "TEST-PRÜFAUFTRAG Version 5\n", java.nio.charset.StandardCharsets.UTF_8);
        try(SweetServer server=new SweetServer(new Simulator(),0,root,suite);HttpClient client=HttpClient.newHttpClient()) {
            server.start();
            var auftrag=send(client,server,"GET","/api/llm/pruefauftrag",null);
            assertEquals(200,auftrag.statusCode());
            assertTrue(auftrag.body().contains("TEST-PRÜFAUFTRAG"));
            assertEquals("no-store",auftrag.headers().firstValue("Cache-Control").orElseThrow());
            assertEquals(405,send(client,server,"POST","/api/llm/pruefauftrag","{}").statusCode());
            var grundlagen=send(client,server,"GET","/api/llm/grundlagen",null);
            assertEquals(200,grundlagen.statusCode());
            assertTrue(grundlagen.body().contains("TEST-GESETZE"));
            assertEquals("no-store",grundlagen.headers().firstValue("Cache-Control").orElseThrow());
            assertEquals(405,send(client,server,"POST","/api/llm/grundlagen","{}").statusCode());
            assertEquals(404,send(client,server,"GET","/api/llm/pruefmenge",null).statusCode());
            Path review=root.resolve("testsweet/logs/llm-review-testfall");
            java.nio.file.Files.createDirectories(review);
            java.nio.file.Files.writeString(review.resolve("pruefmenge.md"),
                    "# Prüfmenge TESTFALL\ngeforderte_pruefpunkte = V1, S1\n", java.nio.charset.StandardCharsets.UTF_8);
            var pruefmenge=send(client,server,"GET","/api/llm/pruefmenge",null);
            assertEquals(200,pruefmenge.statusCode());
            assertTrue(pruefmenge.body().contains("Prüfmenge TESTFALL"));
            assertEquals("no-store",pruefmenge.headers().firstValue("Cache-Control").orElseThrow());
            assertEquals(405,send(client,server,"POST","/api/llm/pruefmenge","{}").statusCode());
        }
    }

    @Test void serverShutdownClosesItsSuiteController() throws Exception {
        FakeSuite suite=new FakeSuite(root);
        SweetServer server=new SweetServer(new Simulator(),0,root,suite);server.start();server.close();
        assertTrue(suite.closed);
    }

    private static HttpResponse<String> send(HttpClient client,SweetServer server,String method,String path,String body) throws Exception {
        var builder=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+server.port()+path)).timeout(Duration.ofSeconds(8));
        if(body!=null)builder.header("Content-Type","application/json");
        return client.send(builder.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }
    private static Object field(Object target,String name) throws Exception {var f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
    private static void set(Object target,String name,Object value) throws Exception {var f=target.getClass().getDeclaredField(name);f.setAccessible(true);f.set(target,value);}
    private static final class FakeSuite extends CertificateSuiteService {
        boolean busy,closed;int requestedCases,started,cancelled;
        FakeSuite(Path root){super(root);}
        @Override public synchronized Map<String,Object> start(int count){if(busy)throw new IllegalStateException("Zertifikatslauf läuft bereits");requestedCases=count;started++;busy=true;return status();}
        @Override public Map<String,Object> status(){return Map.of("state",busy?"RUNNING":"CANCELLED","running",busy,"jobId","controlled-job","phase","MAIN_TESTS","completedPhases",0,"totalPhases",13,"percent",0);}
        @Override public boolean running(){return busy;}
        @Override public synchronized Map<String,Object> cancel(){cancelled++;busy=false;return status();}
        @Override public void close(){closed=true;busy=false;}
    }
}
