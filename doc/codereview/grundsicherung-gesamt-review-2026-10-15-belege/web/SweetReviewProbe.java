package de.allgemein.testsweet;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.util.*;
public class SweetReviewProbe {
  public static void main(String[] args)throws Exception {
    Path root=Path.of(args[0]).toAbsolutePath(); Files.createDirectories(root.resolve("testsweet"));
    try(var server=new SweetServer(new Simulator(),0,root)) {
      server.start(); var client=HttpClient.newHttpClient();String base="http://127.0.0.1:"+server.port();
      for(String path:List.of("/api/status","/api/auftraege","/api/testfaelle","/api/llm/status","/api/llm/config","/api/llmpruefung","/api/zertifikat/tests/status","/api/start","/api/stop","/api/llm/start","/api/llm/test","/api/zertifikat/tests/start","/api/zertifikat/tests/stop")) {
        String method=List.of("/api/status","/api/auftraege","/api/testfaelle","/api/llm/status","/api/llmpruefung").contains(path)?"DELETE":"GET";
        var res=client.send(HttpRequest.newBuilder(URI.create(base+path)).method(method,HttpRequest.BodyPublishers.noBody()).build(),HttpResponse.BodyHandlers.ofString());
        System.out.println("SWEET method="+method+" path="+path+" status="+res.statusCode()+" cache="+res.headers().firstValue("Cache-Control").orElse("absent")+" csp="+res.headers().firstValue("Content-Security-Policy").orElse("absent")+" nosniff="+res.headers().firstValue("X-Content-Type-Options").orElse("absent"));
      }
      var res=client.send(HttpRequest.newBuilder(URI.create(base+"/api/llm/config")).header("Origin","https://foreign.example").GET().build(),HttpResponse.BodyHandlers.ofString());
      System.out.println("SWEET foreignOriginGet="+res.statusCode());
      for(String path:List.of("/llm.local.properties","/pom.xml","/assets/%2e%2e/llm.local.properties")) {
        res=client.send(HttpRequest.newBuilder(URI.create(base+path)).GET().build(),HttpResponse.BodyHandlers.ofString());
        System.out.println("SWEET publicPath="+path+" status="+res.statusCode());
      }
    }
  }
}
