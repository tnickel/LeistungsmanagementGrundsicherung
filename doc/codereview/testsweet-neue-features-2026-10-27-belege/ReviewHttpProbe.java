package de.allgemein.testsweet;

import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Sichere Gegenprobe: eigener Loopback-Port, Temp-Repository und Fake-Suite.
 * Keine Simulatorläufe, Browserstarts, echten Exporte oder Provider-Aufrufe. */
public final class ReviewHttpProbe {
  static Object field(Object target,String name) throws Exception {
    var f=target.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(target);
  }
  static final class FakeSuite extends CertificateSuiteService {
    boolean busy; int started;
    FakeSuite(Path root){super(root);}
    @Override public boolean running(){return busy;}
    @Override public synchronized Map<String,Object> start(int n){busy=true;started++;return status();}
    @Override public Map<String,Object> status(){return Map.of("state",busy?"RUNNING":"IDLE","running",busy);}
    @Override public void close(){busy=false;}
  }
  static HttpResponse<String> request(HttpClient client,SweetServer server,String method,String path) throws Exception {
    return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+server.port()+path))
      .timeout(Duration.ofSeconds(15)).method(method,method.equals("POST")?HttpRequest.BodyPublishers.ofString("{}"):HttpRequest.BodyPublishers.noBody()).build(),HttpResponse.BodyHandlers.ofString());
  }
  static void expect(int expected,HttpResponse<String> response,String label){
    if(response.statusCode()!=expected)throw new AssertionError(label+": "+response.statusCode()+" "+response.body());
    System.out.println(label+"="+response.statusCode());
  }
  public static void main(String[] args) throws Exception {
    Path root=Files.createTempDirectory("testsweet-http-review-");
    try {
      Path tool=Files.createDirectories(root.resolve("doc/testreport-werkzeug"));
      Files.writeString(tool.resolve("publish-report.js"),
        "if(process.argv[2]==='--publish') console.log(JSON.stringify({state:'READY',reportId:'FIXTURE',laeufe:1,davonErfolgreich:1})); else console.log(JSON.stringify({state:'READY'}));");
      Files.writeString(root.resolve("doc/SDN-Leistung-LMG-Testreport.pdf"),"%PDF-CONTROLLED-FIXTURE");
      FakeSuite suite=new FakeSuite(root); Simulator simulator=new Simulator();
      try(SweetServer server=new SweetServer(simulator,0,root,suite);HttpClient client=HttpClient.newHttpClient()) {
        server.start();
        expect(405,request(client,server,"GET","/api/testreport"),"POST endpoint rejects GET");
        AtomicBoolean exporting=(AtomicBoolean)field(server,"reportBusy");
        exporting.set(true);
        expect(409,request(client,server,"POST","/api/testreport"),"duplicate publication guarded");
        expect(409,request(client,server,"GET","/api/testreport/pdf"),"PDF blocked while publishing");
        // Der Fake-Gesamtlauf erzeugt keine Prüf-JVM; nur der vorhandene DB-Verbindungscheck ist echt.
        var suiteStart=request(client,server,"POST","/api/zertifikat/tests/start");
        System.out.println("suite start during report="+suiteStart.statusCode()+"; fake starts="+suite.started);
        if(suiteStart.statusCode()!=202 && suiteStart.statusCode()!=503) throw new AssertionError(suiteStart.body());
        exporting.set(false);suite.busy=true;
        expect(409,request(client,server,"POST","/api/testreport"),"publication blocked during suite");
        var download=request(client,server,"GET","/api/testreport/pdf");
        expect(200,download,"old report served during suite");
        if(!download.body().equals("%PDF-CONTROLLED-FIXTURE"))throw new AssertionError("wrong bytes");
        if(!download.headers().firstValue("Cache-Control").orElse("").equals("no-store"))throw new AssertionError("cache header");
        suite.busy=false;
        var llm=(LlmReviewService)field(server,"llm");
        var progress=llm.getClass().getDeclaredField("progress");progress.setAccessible(true);
        progress.set(llm,Map.of("state","RUNNING","running",true));
        expect(200,request(client,server,"POST","/api/testreport"),"publication allowed during GLM");
      }
    } finally {
      try(var files=Files.walk(root)){for(Path p:files.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}
    }
  }
}
