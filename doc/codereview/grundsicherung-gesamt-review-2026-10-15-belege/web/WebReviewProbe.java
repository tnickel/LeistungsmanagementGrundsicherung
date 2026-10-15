package de.allgemein.lmg.web;
import de.allgemein.tx.*;
import de.allgemein.lmg.service.*;
import de.allgemein.lmg.model.*;
import java.lang.reflect.*;
import java.sql.*;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.math.*;
import java.util.*;
import java.util.concurrent.atomic.*;

public class WebReviewProbe {
  static Object primitive(Class<?> type) {
    if (type == boolean.class) return false;
    if (type == int.class) return 0;
    if (type == long.class) return 0L;
    if (type == double.class) return 0d;
    return null;
  }
  public static void main(String[] args) throws Exception {
    var users = new Benutzerverwaltung("sb:secret:sachbearbeiter;adm:secret:admin");
    users.setzeUhrFuerTests(Clock.fixed(Instant.parse("2026-10-15T10:00:00Z"), ZoneOffset.UTC));
    String session = users.anmelden("sb", "secret");
    String print = users.erzeugeDruckToken(users.zuToken(session).orElseThrow());
    users.abmelden(session);
    System.out.println("LOGOUT sessionValid="+users.zuToken(session).isPresent()+" issuedPrintTokenValid="+users.verbraucheDruckToken(print).isPresent());
    var saturated = new Benutzerverwaltung("target:secret:sachbearbeiter");
    saturated.setzeUhrFuerTests(Clock.fixed(Instant.parse("2026-10-15T10:00:00Z"), ZoneOffset.UTC));
    for (int i=0;i<1024;i++) for (int j=0;j<5;j++) try { saturated.anmelden("phantom"+i,"wrong"); } catch (FachlicheException expected) {}
    int blocked=0, wrong=0;
    for(int i=0;i<30;i++)try{saturated.anmelden("target","wrong");}catch(FachlicheException e){ if(e.getMessage().startsWith("Zu viele")) blocked++; else wrong++; }
    System.out.println("THROTTLE_SATURATION wrongPasswordAnswers="+wrong+" throttledAnswers="+blocked);
    AtomicInteger calls = new AtomicInteger();
    LeistungsService service = (LeistungsService) java.lang.reflect.Proxy.newProxyInstance(WebReviewProbe.class.getClassLoader(), new Class<?>[]{LeistungsService.class}, (p,m,a)->{
      if(m.getName().equals("storniereZahlung")) {
        calls.incrementAndGet();
        return new Zahlung((long)a[0],99,null,"REVIEW-SYNTHETIC","2026-10",BigDecimal.TEN,"STORNIERT",Instant.parse("2026-10-15T10:00:00Z"),String.valueOf(a[1]),null,null,null,"Synthetic Person");
      }
      if(m.getName().equals("generiereBescheidHtml"))return "<html><body>Synthetic print</body></html>";
      throw new UnsupportedOperationException(m.getName());
    });
    ConnectionPool pool = new ConnectionPool(){
      public Connection getConnection(){return (Connection)java.lang.reflect.Proxy.newProxyInstance(WebReviewProbe.class.getClassLoader(),new Class<?>[]{Connection.class},(p,m,a)->primitive(m.getReturnType()));}
      public void release(Connection c){}
      public void close(){}
    };
    try(var tasks=new TaskController(new TxManager(pool),2,8);var server=new LmgServer(tasks,service,null,users,0)) {
      server.start(); String base="http://127.0.0.1:"+server.adresse().getPort();
      var client=HttpClient.newHttpClient(); String sb=users.anmelden("sb","secret");
      for(String path:List.of("/api/zahlungen/7/storno","/api/zahlungen/+7/storno","/api/zahlungen/%2B7/storno","/api/zahlungen/%EF%BC%97/storno")) {
        var res=client.send(HttpRequest.newBuilder(URI.create(base+path)).header("Authorization","Bearer "+sb).POST(HttpRequest.BodyPublishers.noBody()).build(),HttpResponse.BodyHandlers.ofString());
        System.out.println("STORNO path="+path+" status="+res.statusCode()+" calls="+calls.get()+" body="+res.body());
      }
      String pt=users.erzeugeDruckToken(users.zuToken(sb).orElseThrow()); users.abmelden(sb);
      for(int i=0;i<2;i++) {
        var res=client.send(HttpRequest.newBuilder(URI.create(base+"/api/bedarfsgemeinschaften/99/bescheid/druck?token="+pt)).GET().build(),HttpResponse.BodyHandlers.ofString());
        System.out.println("PRINT_AFTER_LOGOUT attempt="+(i+1)+" status="+res.statusCode()+" cache="+res.headers().firstValue("Cache-Control").orElse("absent"));
      }
    }
    de.allgemein.tx.audit.TxAuditLog.closeFile();
  }
}
