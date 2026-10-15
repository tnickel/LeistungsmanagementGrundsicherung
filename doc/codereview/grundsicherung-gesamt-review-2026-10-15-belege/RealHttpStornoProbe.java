import de.allgemein.lmg.web.*;
import de.allgemein.lmg.service.*;
import de.allgemein.tx.*;
import java.net.*;
import java.net.http.*;
import java.sql.*;
import java.util.*;
import java.nio.file.*;

/** Echte HTTP-/Service-/Oracle-Kette ausschließlich im eigenen Reviewschema. */
class RealHttpStornoProbe {
  public static void main(String[] args) throws Exception {
    String url=System.getenv("TX_URL"),user=System.getenv("TX_USER"),pass=System.getenv("TX_PASS");
    if(!"REVIEW_20261015".equals(user))throw new IllegalStateException("Nur eigenes Reviewschema erlaubt");
    long bg=0,payment=0;
    List<Map<String,Object>> results=new ArrayList<>();
    try(Connection c=DriverManager.getConnection(url,user,pass)) {
      try(var s=c.createStatement();var r=s.executeQuery("SELECT SEQ_BG.NEXTVAL, SEQ_ZAHLUNG.NEXTVAL FROM DUAL")){r.next();bg=r.getLong(1);payment=r.getLong(2);}
      try(var s=c.prepareStatement("INSERT INTO BEDARFSGEMEINSCHAFT(ID,AKTENZEICHEN,HAUPTPERSON_NAME,BUDGET_REST) VALUES(?,?,?,90)")){s.setLong(1,bg);s.setString(2,"REVIEW-AUTH-"+UUID.randomUUID());s.setString(3,"Synthetic Review");s.executeUpdate();}
      try(var s=c.prepareStatement("INSERT INTO ZAHLUNG(ID,BG_ID,VORGANGS_NR,ZAHLUNGSMONAT,AUSZAHLUNGSBETRAG,ZAHLUNGSSTATUS,SACHBEARBEITER_ID,FREIGEGEBEN_VON) VALUES(?,?,?,'2026-10',10,'BEREIT','other','second')")){s.setLong(1,payment);s.setLong(2,bg);s.setString(3,"REVIEW-AUTH-"+UUID.randomUUID());s.executeUpdate();}
      try(var pool=new SimpleConnectionPool(url,user,pass,8);var tasks=new TaskController(new TxManager(pool),2,8)) {
        var users=new Benutzerverwaltung("review-sb:synthetic:sachbearbeiter");
        var service=new SozialLeistungsService(new TxManager(pool));
        try(var server=new LmgServer(tasks,service,null,users,0)) {
          server.start();String base="http://127.0.0.1:"+server.adresse().getPort();String token=users.anmelden("review-sb","synthetic");var client=HttpClient.newHttpClient();
          for(String id:List.of(Long.toString(payment),"+"+payment)) {
            String route="/api/zahlungen/"+id+"/storno";
            var response=client.send(HttpRequest.newBuilder(URI.create(base+route)).header("Authorization","Bearer "+token).POST(HttpRequest.BodyPublishers.noBody()).build(),HttpResponse.BodyHandlers.ofString());
            results.add(Map.of("route",route,"httpStatus",response.statusCode()));
          }
        }
      }
      try(var s=c.prepareStatement("SELECT z.ZAHLUNGSSTATUS,b.BUDGET_REST FROM ZAHLUNG z JOIN BEDARFSGEMEINSCHAFT b ON b.ID=z.BG_ID WHERE z.ID=?")){s.setLong(1,payment);try(var r=s.executeQuery()){r.next();results.add(Map.of("persistedStatus",r.getString(1),"persistedBudgetRest",r.getBigDecimal(2).toPlainString()));}}
      String result=Json.write(Map.of("schema",user,"realOracle",true,"results",results));Files.writeString(Path.of(args[0]),result);System.out.println(result);
    } finally {
      if(bg!=0)try(Connection c=DriverManager.getConnection(url,user,pass)){
        c.setAutoCommit(false);for(String table:List.of("JOURNAL","ZAHLUNG")){try(var s=c.prepareStatement("DELETE FROM "+table+" WHERE BG_ID=?")){s.setLong(1,bg);s.executeUpdate();}}
        try(var s=c.prepareStatement("DELETE FROM BEDARFSGEMEINSCHAFT WHERE ID=?")){s.setLong(1,bg);s.executeUpdate();}c.commit();
      }
    }
  }
}
