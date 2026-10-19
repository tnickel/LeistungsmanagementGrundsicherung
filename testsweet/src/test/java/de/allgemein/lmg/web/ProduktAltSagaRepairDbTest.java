package de.allgemein.lmg.web;

import de.allgemein.lmg.admin.SagaJournalRepairMain;
import de.allgemein.lmg.service.Systemdiagnose;
import de.allgemein.lmg.saga.AntragsSagaProzess;
import de.allgemein.tx.*;
import de.allgemein.tx.demo.AntragsDemoProzess;
import de.allgemein.tx.saga.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.sql.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

/** Downstreamprüfung des installierten Produkt-JARs; alle Altjournale/Fachmarker gehören eigenen UUIDs. */
@EnabledIfEnvironmentVariable(named="TX_URL",matches=".+")
class ProduktAltSagaRepairDbTest {
    @TempDir Path temporary;
    @Test
    void alteCommitteteWirkungHinterNamenlosemPendingWirdNichtGruenAbgeschlossen() throws Exception {
        try(Fixture own=new Fixture()) {
            own.addCommittedEffect(own.marker);
            AtomicInteger compensation=new AtomicInteger();
            var definition=SagaDefinition.of(own.type).step("heutiger-schritt",(c,p,id)->{},(c,p,id)->compensation.incrementAndGet()).build();
            var report=new SagaEngine(new TxManager(own.pool),own.journal).recoverWithReport(definition);
            assertEquals(1,report.failed());assertEquals(0,report.compensated());assertEquals(0,compensation.get());
            assertThrows(IllegalStateException.class,()->startupGuard(report));
            assertOwnState(own,"FAILED",false);
            assertEquals(1,own.effectCount());
        }
    }
    @Test
    void gemischtesAltjournalMitCommittierterZweiterWirkungErlaubtKeinenGruenenStart() throws Exception {
        for(String firstState:List.of("DONE","COMPENSATED")) for(String pendingName:Arrays.asList(null,"  ")) {
            try(Fixture own=new Fixture(2)) {
                String firstMarker=own.marker+"-first",secondMarker=own.marker+"-second";
                own.addCommittedEffect(firstMarker);own.addCommittedEffect(secondMarker);
                own.journal.markStepInFlight(own.id,0);
                var tx=new TxManager(own.pool);
                tx.execute(TxOptions.DEFAULT,ctx->{own.journal.stepDone(ctx.connection(),own.id,0,"eins");return null;});
                if(firstState.equals("COMPENSATED")) tx.execute(TxOptions.DEFAULT,ctx->{
                    deleteEffect(ctx.connection(),firstMarker);own.journal.stepCompensated(ctx.connection(),own.id,0);return null;
                });
                try(var c=connect();var st=c.prepareStatement("UPDATE TX_SAGA_STEP SET NAME = ? WHERE SAGA_ID = ? AND IDX = 1")) {
                    st.setString(1,pendingName);st.setString(2,own.id);assertEquals(1,st.executeUpdate());
                }
                // Echte, bereits committete zweite Wirkung hinter historisch PENDING:
                // Prüfung aus einer unabhängigen Verbindung, ohne neues SAGA_ID-Demofeld.
                assertEquals(1,own.effectCount(secondMarker));
                assertEquals(firstState.equals("DONE")?1:0,own.effectCount(firstMarker));
                AtomicInteger compensations=new AtomicInteger();
                var definition=SagaDefinition.of(own.type)
                        .step("eins",(c,p,id)->fail("Recovery darf keine Aktion starten"),(c,p,id)->{
                            compensations.incrementAndGet();deleteEffect(c,firstMarker);
                        })
                        .step("zwei",(c,p,id)->fail("Recovery darf keine Aktion starten"),(c,p,id)->{
                            compensations.incrementAndGet();deleteEffect(c,secondMarker);
                        }).build();
                var report=new SagaEngine(tx,own.journal).recoverWithReport(definition);
                assertEquals(1,report.failed());assertEquals(0,report.compensated());assertEquals(0,report.completed());
                assertEquals(0,compensations.get());assertThrows(IllegalStateException.class,()->startupGuard(report));
                assertMixedState(own,firstState,pendingName);
                assertEquals(1,own.effectCount(secondMarker));
                assertEquals(firstState.equals("DONE")?1:0,own.effectCount(firstMarker));
            }
        }
    }
    @Test
    void gesicherterWirkungsloserAltfallWirdOfflineAufgeloestUndBestehtDanachDenStartguard() throws Exception {
        try(Fixture own=new Fixture()) {
            AtomicInteger effects=new AtomicInteger();
            var definition=SagaDefinition.of(own.type)
                    .step("eins",(c,p,id)->effects.incrementAndGet(),(c,p,id)->effects.incrementAndGet())
                    .step("zwei",(c,p,id)->effects.incrementAndGet(),(c,p,id)->effects.incrementAndGet())
                    .step("drei",(c,p,id)->effects.incrementAndGet(),(c,p,id)->effects.incrementAndGet()).build();
            var engine=new SagaEngine(new TxManager(own.pool),own.journal);
            var initial=engine.recoverWithReport(definition);
            assertEquals(1,initial.failed());assertThrows(IllegalStateException.class,()->startupGuard(initial));
            assertEquals(0,own.effectCount());assertEquals(0,effects.get());
            Path snapshot=temporary.resolve("snapshot.json"),proof=temporary.resolve("effects.txt"),output=temporary.resolve("resolved");
            String hash;try(var c=connect()) {hash=SagaJournalRepairMain.inspect(c,own.id,own.type,snapshot);}
            Files.writeString(proof,"Eigene synthetische Fixture: Fachaktionszähler 0; unabhängige Demo-Zeilenprüfung 0.");
            var resolution=new SagaJournalRepairMain.Resolution(snapshot,hash,proof,SagaJournalRepairMain.sha256(Files.readAllBytes(proof)),
                    "testsweet-admin","Synthetischer unabhängiger Nullwirkungsabgleich",true,true,output);
            try(var c=connect()) {SagaJournalRepairMain.resolvePending(c,own.id,own.type,resolution);}
            assertOwnState(own,"COMPENSATED",true);assertEquals(0,startupGuard(engine.recoverWithReport(definition)));
            assertEquals(0,effects.get());assertEquals(0,own.effectCount());
            assertEquals(hash,SagaJournalRepairMain.sha256(Files.readAllBytes(output.resolve("snapshot.json"))));
            assertEquals("COMMITTED",((Map<?,?>)Json.parse(Files.readString(output.resolve("resolution-outcome.json")))).get("state"));
        }
    }
    private static int startupGuard(SagaEngine.RecoveryReport report) {
        return LmgServerMain.requireSuccessfulRecovery(new Systemdiagnose.WiederanlaufErgebnis(Map.of(
                AntragsSagaProzess.definition().type(),report,AntragsDemoProzess.definition().type(),new SagaEngine.RecoveryReport(0,0,0,0))));
    }
    private static void assertOwnState(Fixture own,String state,boolean deleted) throws SQLException {
        try(var c=connect();var st=c.prepareStatement("SELECT s.STATE,s.PAYLOAD,x.STATE,x.NAME FROM TX_SAGA s JOIN TX_SAGA_STEP x ON x.SAGA_ID = s.SAGA_ID WHERE s.SAGA_ID = ? AND s.SAGA_TYPE = ?")) {
            st.setString(1,own.id);st.setString(2,own.type);try(var rows=st.executeQuery()) {
                assertTrue(rows.next());assertEquals(state,rows.getString(1));assertEquals(deleted,rows.getString(2)==null);
                assertEquals("PENDING",rows.getString(3));assertNull(rows.getString(4));assertFalse(rows.next());
            }
        }
    }
    private static void assertMixedState(Fixture own,String firstState,String pendingName) throws SQLException {
        try(var c=connect();var st=c.prepareStatement("SELECT s.STATE,s.PAYLOAD,x.IDX,x.STATE,x.NAME FROM TX_SAGA s JOIN TX_SAGA_STEP x ON x.SAGA_ID = s.SAGA_ID WHERE s.SAGA_ID = ? AND s.SAGA_TYPE = ? ORDER BY x.IDX")) {
            st.setString(1,own.id);st.setString(2,own.type);try(var rows=st.executeQuery()) {
                assertTrue(rows.next());assertEquals("FAILED",rows.getString(1));assertEquals(own.marker+";199.90",rows.getString(2));
                assertEquals(0,rows.getInt(3));assertEquals(firstState,rows.getString(4));assertEquals("eins",rows.getString(5));
                assertTrue(rows.next());assertEquals("FAILED",rows.getString(1));assertEquals(own.marker+";199.90",rows.getString(2));
                assertEquals(1,rows.getInt(3));assertEquals("PENDING",rows.getString(4));assertEquals(pendingName,rows.getString(5));assertFalse(rows.next());
            }
        }
    }
    private static void deleteEffect(Connection c,String marker) throws SQLException {
        try(var st=c.prepareStatement("DELETE FROM TX_DEMO_ANTRAG WHERE NAME = ?")) {st.setString(1,marker);assertEquals(1,st.executeUpdate());}
    }
    private static Connection connect() throws SQLException {
        var p=new Properties();p.setProperty("user",System.getenv("TX_USER"));p.setProperty("password",System.getenv("TX_PASS"));
        p.setProperty("oracle.net.CONNECT_TIMEOUT","5000");p.setProperty("oracle.jdbc.ReadTimeout","10000");return DriverManager.getConnection(System.getenv("TX_URL"),p);
    }
    private static final class Fixture implements AutoCloseable {
        final String id=UUID.randomUUID().toString(),type="SWEET_ALT_"+id,marker="SWEET-ALT-"+id;
        final SimpleConnectionPool pool=new SimpleConnectionPool(System.getenv("TX_URL"),System.getenv("TX_USER"),System.getenv("TX_PASS"),3,Duration.ofSeconds(5),5000,10000);
        final SagaJournal journal=new SagaJournal(pool);
        final Set<String> effectMarkers=new LinkedHashSet<>();
        Fixture() throws SQLException {this(1);}
        Fixture(int steps) throws SQLException {journal.start(id,type,marker+";199.90",steps);journal.markState(id,SagaState.FAILED);}
        void addCommittedEffect(String name) throws SQLException {
            effectMarkers.add(name);
            try(var c=connect();var st=c.prepareStatement("INSERT INTO TX_DEMO_ANTRAG (NAME,STATUS,BETRAG) VALUES (?,'NEU',199.90)")) {
                c.setAutoCommit(false);st.setString(1,name);assertEquals(1,st.executeUpdate());c.commit();
            }
        }
        int effectCount() throws SQLException {return effectCount(marker);}
        int effectCount(String name) throws SQLException {
            try(var c=connect();var st=c.prepareStatement("SELECT COUNT(*) FROM TX_DEMO_ANTRAG WHERE NAME = ?")) {
                st.setString(1,name);try(var rows=st.executeQuery()) {assertTrue(rows.next());return rows.getInt(1);}
            }
        }
        public void close() throws SQLException {
            pool.close();try(var c=connect()) {c.setAutoCommit(false);
                for(String name:effectMarkers)try(var st=c.prepareStatement("DELETE FROM TX_DEMO_ANTRAG WHERE NAME = ?")) {st.setString(1,name);st.executeUpdate();}
                for(String sql:List.of("DELETE FROM TX_SAGA_STEP WHERE SAGA_ID IN (SELECT SAGA_ID FROM TX_SAGA WHERE SAGA_ID = ? AND SAGA_TYPE = ?)","DELETE FROM TX_SAGA WHERE SAGA_ID = ? AND SAGA_TYPE = ?")) {
                    try(var st=c.prepareStatement(sql)) {st.setString(1,id);st.setString(2,type);st.executeUpdate();}
                }c.commit();
            }
        }
    }
}
