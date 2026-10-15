package de.allgemein.lmg.web;
import de.allgemein.lmg.saga.AntragsSagaProzess;
import de.allgemein.lmg.service.Systemdiagnose;
import de.allgemein.tx.*;
import de.allgemein.tx.saga.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.nio.file.*;

/** Echter persistierter Zustand nach start-Commit, vor erstem IN_FLIGHT; kein Prozesskill. */
public class PendingRecoveryOracleProbe {
 public static void main(String[] args) throws Exception {
  String url=System.getenv("TX_URL"),user=System.getenv("TX_USER"),pass=System.getenv("TX_PASS"),id=UUID.randomUUID().toString();
  if(!"REVIEW_20261015".equals(user))throw new IllegalStateException("Nur eigenes Reviewschema");
  List<Map<String,Object>> attempts=new ArrayList<>();
  try(var pool=new SimpleConnectionPool(url,user,pass,8);var tasks=new TaskController(new TxManager(pool),2,8)) {
   var journal=new SagaJournal(pool);var engine=new SagaEngine(new TxManager(pool),journal);
   var definition=AntragsSagaProzess.definition();journal.start(id,definition.type(),"{}",definition.steps().size());
   var diagnosis=new Systemdiagnose(new TxManager(pool),tasks,engine,Instant.now());
   for(int n=1;n<=2;n++) {
    var recovery=diagnosis.wiederanlaufDetails();boolean blocked=false;try{LmgServerMain.requireSuccessfulRecovery(recovery);}catch(IllegalStateException expected){blocked=true;}
    try(Connection c=DriverManager.getConnection(url,user,pass);var s=c.prepareStatement("SELECT STATE FROM TX_SAGA WHERE SAGA_ID=?")) {
     s.setString(1,id);try(var r=s.executeQuery()){r.next();attempts.add(Map.of("attempt",n,"failedRecoveries",recovery.fehlerhaft(),"persistedSagaState",r.getString(1),"startupBlocked",blocked));}
    }
   }
   String result=Json.write(Map.of("realOracle",true,"stateConstructedAtRealJournalCommit",true,"processKill",false,"attempts",attempts));
   Files.writeString(Path.of(args[0]),result);System.out.println(result);
  } finally {
   try(Connection c=DriverManager.getConnection(url,user,pass)){c.setAutoCommit(false);for(String table:List.of("TX_SAGA_STEP","TX_SAGA")){try(var s=c.prepareStatement("DELETE FROM "+table+" WHERE SAGA_ID=?")){s.setString(1,id);s.executeUpdate();}}c.commit();}
  }
 }
}
