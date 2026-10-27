package de.allgemein.testsweet;

import de.allgemein.lmg.web.Json;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static org.junit.jupiter.api.Assertions.*;

/** Exporte und Downloads mit kontrolliertem Prozessrunner; keine DB, Browser oder Fremdprovider. */
class TestreportHttpTest {
    @TempDir Path root;
    private String originalUrl;

    @BeforeEach void fixtureUrl() {
        originalUrl=System.getProperty("tx.url");System.setProperty("tx.url","jdbc:fixture:report-http");
    }
    @AfterEach void restoreUrl() {
        if(originalUrl==null)System.clearProperty("tx.url");else System.setProperty("tx.url",originalUrl);
    }

    @Test void exportReservesItsInputsAgainstManualMutationsAndOtherExports() throws Exception {
        prepare();FakeSuite suite=new FakeSuite(root);CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        SweetServer.DocumentProcess publisher=(builder,timeout)->{
            assertEquals(300,timeout);entered.countDown();
            if(!release.await(10,TimeUnit.SECONDS))throw new IOException("Fixture wurde nicht freigegeben");
            return Json.write(Map.of("state","READY","reportId","CONTROLLED","laeufe",9,"davonErfolgreich",9));
        };
        try(SweetServer server=new SweetServer(new Simulator(),0,root,suite,publisher);HttpClient client=HttpClient.newHttpClient()) {
            server.start();var publishing=client.sendAsync(request(server,"POST","/api/testreport"),HttpResponse.BodyHandlers.ofString());
            try {
                assertTrue(entered.await(5,TimeUnit.SECONDS));
                for(String route:new String[]{"/api/start","/api/stop","/api/llm/start","/api/llm/config","/api/llm/test",
                        "/api/llmpruefung/paket","/api/zertifikat/tests/start","/api/zertifikat","/api/testreport"})
                    assertEquals(409,send(client,server,"POST",route).statusCode(),route);
                assertEquals(409,send(client,server,"GET","/api/testreport/pdf").statusCode());
                var status=(Map<?,?>)Json.parse(send(client,server,"GET","/api/status").body());
                assertEquals(true,status.get("documentExportRunning"));assertEquals(0,suite.started);
            } finally {release.countDown();}
            assertEquals(200,publishing.get(5,TimeUnit.SECONDS).statusCode());
            assertFalse(busy(server).get());
        }
    }

    @Test void existingActivitiesBlockBothReportGenerationAndOldDownload() throws Exception {
        prepare();Simulator simulator=new Simulator();FakeSuite suite=new FakeSuite(root);AtomicInteger invoked=new AtomicInteger();
        try(SweetServer server=new SweetServer(simulator,0,root,suite,(b,t)->{invoked.incrementAndGet();return "{}";});HttpClient client=HttpClient.newHttpClient()) {
            server.start();
            AtomicBoolean active=(AtomicBoolean)field(simulator,"laufAktiv");active.set(true);
            assertBlocked(client,server);active.set(false);
            set(field(server,"llm"),"progress",Map.of("state","RUNNING","running",true));
            assertBlocked(client,server);set(field(server,"llm"),"progress",Map.of("state","IDLE","running",false));
            AtomicBoolean certificate=(AtomicBoolean)field(server,"certificateBusy");certificate.set(true);
            assertBlocked(client,server);certificate.set(false);
            suite.running=true;assertBlocked(client,server);suite.running=false;
            assertEquals(0,invoked.get());
        }
    }

    @Test void missingToolAndProcessFailuresAlwaysReleaseTheExportReservation() throws Exception {
        AtomicInteger calls=new AtomicInteger();
        try(SweetServer server=new SweetServer(new Simulator(),0,root,new FakeSuite(root),(b,t)->{
            int call=calls.incrementAndGet();
            if(call==1)throw new IOException("Kontrollierter Node-Startfehler");
            if(call==2)return "{\"state\":\"INCOMPLETE\"}";
            return "{\"state\":\"READY\",\"reportId\":\"RECOVERED\"}";
        });HttpClient client=HttpClient.newHttpClient()) {
            server.start();assertEquals(405,send(client,server,"GET","/api/testreport").statusCode());
            assertEquals(404,send(client,server,"POST","/api/testreport").statusCode());assertFalse(busy(server).get());
            prepare();
            for(int attempt=0;attempt<2;attempt++){
                assertEquals(500,send(client,server,"POST","/api/testreport").statusCode());assertFalse(busy(server).get());
            }
            assertEquals(200,send(client,server,"POST","/api/testreport").statusCode());assertFalse(busy(server).get());
        }
    }

    @Test void downloadRejectsReplacedBytesMissingProofAndWrongMethods() throws Exception {
        prepare();byte[] original="%PDF-CONTROLLED".getBytes(StandardCharsets.UTF_8);Path pdf=pdf();Files.write(pdf,original);
        AtomicBoolean replace=new AtomicBoolean();AtomicReference<String> expected=new AtomicReference<>(LlmReviewService.sha256(original));
        try(SweetServer server=new SweetServer(new Simulator(),0,root,new FakeSuite(root),(b,t)->{
            assertTrue(b.command().contains("--verify"));assertEquals(30,t);
            if(replace.get())Files.writeString(pdf,"%PDF-REPLACED-AFTER-VERIFY");
            return Json.write(Map.of("state","READY","pdfSha256",expected.get()));
        });HttpClient client=HttpClient.newHttpClient()) {
            server.start();
            var download=send(client,server,"GET","/api/testreport/pdf");assertEquals(200,download.statusCode());
            assertEquals(new String(original,StandardCharsets.UTF_8),download.body());
            assertEquals("application/pdf",download.headers().firstValue("Content-Type").orElseThrow());
            assertEquals("no-store",download.headers().firstValue("Cache-Control").orElseThrow());
            assertTrue(download.headers().firstValue("Content-Disposition").orElseThrow().startsWith("inline;"));
            assertEquals(405,send(client,server,"POST","/api/testreport/pdf").statusCode());
            replace.set(true);assertEquals(409,send(client,server,"GET","/api/testreport/pdf").statusCode());assertFalse(busy(server).get());
            replace.set(false);expected.set("");assertEquals(409,send(client,server,"GET","/api/testreport/pdf").statusCode());
            expected.set(LlmReviewService.sha256(Files.readAllBytes(pdf)));assertEquals(200,send(client,server,"GET","/api/testreport/pdf").statusCode());
        }
    }

    @Test void actualProcessRunnerReportsNonzeroExitAndKillsTimedOutHelper() throws Exception {
        var failed=assertThrows(IOException.class,()->SweetServer.runProcess(new ProcessBuilder("node","-e","process.exit(7)"),5));
        assertTrue(failed.getMessage().contains("Exit 7"));
        Path pid=root.resolve("helper.pid");
        var timeout=assertThrows(IOException.class,()->SweetServer.runProcess(new ProcessBuilder("node","-e",
                "require('fs').writeFileSync(process.argv[1],String(process.pid));setInterval(()=>{},1000)",pid.toString()),1));
        assertTrue(timeout.getMessage().contains("Zeitlimit"));
        if(Files.exists(pid))assertFalse(ProcessHandle.of(Long.parseLong(Files.readString(pid))).map(ProcessHandle::isAlive).orElse(false));
    }

    private void prepare() throws Exception {Files.createDirectories(root.resolve("doc/testreport-werkzeug"));Files.writeString(pdf(),"%PDF-FIXTURE");}
    private Path pdf(){return root.resolve("doc/SDN-Leistung-LMG-Testreport.pdf");}
    private static void assertBlocked(HttpClient client,SweetServer server) throws Exception {
        assertEquals(409,send(client,server,"POST","/api/testreport").statusCode());
        assertEquals(409,send(client,server,"GET","/api/testreport/pdf").statusCode());
    }
    private static HttpRequest request(SweetServer server,String method,String route){
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+server.port()+route)).timeout(Duration.ofSeconds(8))
                .method(method,method.equals("POST")?HttpRequest.BodyPublishers.ofString("{}"):HttpRequest.BodyPublishers.noBody()).build();
    }
    private static HttpResponse<String> send(HttpClient client,SweetServer server,String method,String route) throws Exception {
        return client.send(request(server,method,route),HttpResponse.BodyHandlers.ofString());
    }
    private static AtomicBoolean busy(SweetServer server) throws Exception {return (AtomicBoolean)field(server,"reportBusy");}
    private static Object field(Object target,String name) throws Exception {var f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
    private static void set(Object target,String name,Object value) throws Exception {var f=target.getClass().getDeclaredField(name);f.setAccessible(true);f.set(target,value);}
    private static final class FakeSuite extends CertificateSuiteService {
        boolean running;int started;
        FakeSuite(Path root){super(root);}
        @Override public boolean running(){return running;}
        @Override public synchronized Map<String,Object> start(int n){started++;running=true;return Map.of("state","RUNNING","running",true);}
        @Override public void close(){running=false;}
    }
}
