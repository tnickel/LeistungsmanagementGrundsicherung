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
import java.lang.reflect.*;
import java.nio.file.*;
import java.sql.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

/** Eigene UUID-Altjournale, echte Oracle-Sperren und derselbe Guard wie beim eigenständigen Serverstart. */
@EnabledIfEnvironmentVariable(named="TX_URL",matches=".+")
class AltSagaRepairDbTest {
    @TempDir Path temporary;

    @Test
    void altesTeiljournalBlockiertStartBisGesicherterWirkungspruefungUndKontrolliertemAbschluss() throws Exception {
        try(Fixture own=new Fixture()) {
            AtomicInteger effects=new AtomicInteger();
            var definition=SagaDefinition.of(own.type)
                    .step("eins",(c,p,id)->effects.incrementAndGet(),(c,p,id)->effects.incrementAndGet())
                    .step("zwei",(c,p,id)->effects.incrementAndGet(),(c,p,id)->effects.incrementAndGet())
                    .step("drei",(c,p,id)->effects.incrementAndGet(),(c,p,id)->effects.incrementAndGet()).build();
            var engine=new SagaEngine(new TxManager(own.pool),own.journal);
            var failed=engine.recoverWithReport(definition);
            assertEquals(1,failed.failed());assertThrows(IllegalStateException.class,()->startupGuard(failed));
            assertEquals(0,effects.get(),"unbekanntes Teiljournal darf keine Indexkompensation auslösen");
            // Die kontrollierte synthetische Fixture hat nie eine Aktion ausgeführt.
            // Zusätzlich werden ihre potenziellen Demo-Fachzeilen unabhängig per SQL geprüft.
            try(var c=connect();var st=c.prepareStatement("SELECT COUNT(*) FROM TX_DEMO_ANTRAG WHERE NAME = ?")) {
                st.setString(1,own.marker);try(var rows=st.executeQuery()) {assertTrue(rows.next());assertEquals(0,rows.getInt(1));}
            }
            var proof=proof(own);
            try(var c=connect()) {SagaJournalRepairMain.resolvePending(c,own.id,own.type,proof);}
            assertState(own,"COMPENSATED",null,"PENDING");
            assertEquals(0,startupGuard(engine.recoverWithReport(definition)));
            assertEquals(0,effects.get());
            assertTrue(Files.isRegularFile(proof.output().resolve("snapshot.json")));
            assertTrue(Files.isRegularFile(proof.output().resolve("resolution-intent.json")));
            assertEquals("COMMITTED",outcomeState(proof.output().resolve("resolution-outcome.json")));
        }
    }

    @Test
    void vorWriteAheadCommitteteDemowirkungHinterNamenlosemPendingBleibtZurKlaerungErhalten() throws Exception {
        try(Fixture own=new Fixture()) {
            own.addCommittedEffect(own.marker);
            AtomicInteger compensation=new AtomicInteger();
            // Gleiche Schrittzahl wie im Journal: der alte Count-Guard allein
            // hätte dies fälschlich als wirkungslos terminal abgeschlossen.
            var definition=SagaDefinition.of(own.type).step("neuer-schritt",(c,p,id)->{},(c,p,id)->compensation.incrementAndGet()).build();
            var report=new SagaEngine(new TxManager(own.pool),own.journal).recoverWithReport(definition);
            assertEquals(1,report.failed());assertEquals(0,report.compensated());assertEquals(0,compensation.get());
            assertThrows(IllegalStateException.class,()->startupGuard(report));
            assertState(own,"FAILED",own.marker+";199.90","PENDING");
            try(var c=connect();var st=c.prepareStatement("SELECT COUNT(*) FROM TX_DEMO_ANTRAG WHERE NAME = ?")) {
                st.setString(1,own.marker);try(var rows=st.executeQuery()) {assertTrue(rows.next());assertEquals(1,rows.getInt(1));}
            }
        }
    }

    @Test
    void gemischtesAltjournalMitCommittierterZweiterWirkungBleibtVorJederTeilkompensationFailed() throws Exception {
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
                // Reproduziert das historische Fenster: zweite Fachaktion committet,
                // ihr separates stepDone fehlt. Unabhängige Verbindung beweist die Wirkung.
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
                assertEquals(0,compensations.get(),"auch bekannte ältere Schritte erst nach Wirkungsprüfung anfassen");
                assertThrows(IllegalStateException.class,()->startupGuard(report));
                assertMixedState(own,firstState,pendingName);
                assertEquals(1,own.effectCount(secondMarker));
                assertEquals(firstState.equals("DONE")?1:0,own.effectCount(firstMarker));
            }
        }
    }

    @Test
    void inFlightUndDoneOhneBekannteNamenBleibenMitUnveraendertemJournalAbgewiesen() throws Exception {
        for(String state:List.of("IN_FLIGHT","DONE")) try(Fixture own=new Fixture()) {
            own.journal.markStepInFlight(own.id,0);
            if(state.equals("DONE")) {
                new TxManager(own.pool).execute(TxOptions.DEFAULT,ctx->{own.journal.stepDone(ctx.connection(),own.id,0,"alter-schritt");return null;});
                try(var c=connect();var st=c.prepareStatement("UPDATE TX_SAGA_STEP SET NAME = NULL WHERE SAGA_ID = ?")) {st.setString(1,own.id);st.executeUpdate();}
            }
            var proof=proof(own);
            try(var c=connect()) {assertThrows(IllegalStateException.class,()->SagaJournalRepairMain.resolvePending(c,own.id,own.type,proof));}
            assertSnapshotUnchanged(own,proof.snapshotSha256());assertFalse(Files.exists(proof.output()));
        }
    }

    @Test
    void veraenderterSnapshotOderBelegHashLaesstDasJournalUnveraendert() throws Exception {
        try(Fixture own=new Fixture()) {
            var proof=proof(own);
            Files.writeString(proof.evidence(),"nachträglich verändert");
            try(var c=connect()) {assertThrows(java.io.IOException.class,()->SagaJournalRepairMain.resolvePending(c,own.id,own.type,proof));}
            assertSnapshotUnchanged(own,proof.snapshotSha256());
            Files.writeString(proof.evidence(),"synthetischer unabhängiger Nullwirkungsnachweis");
            var currentProof=new SagaJournalRepairMain.Resolution(proof.snapshot(),proof.snapshotSha256(),proof.evidence(),
                    SagaJournalRepairMain.sha256(Files.readAllBytes(proof.evidence())),"test-admin","Synthetischer Wirkungsabgleich",true,true,proof.output());
            try(var c=connect();var st=c.prepareStatement("UPDATE TX_SAGA SET PAYLOAD = ? WHERE SAGA_ID = ? AND SAGA_TYPE = ?")) {
                st.setString(1,"geänderter eigener Payload");st.setString(2,own.id);st.setString(3,own.type);assertEquals(1,st.executeUpdate());
            }
            String before;
            try(var c=connect()) {before=SagaJournalRepairMain.inspect(c,own.id,own.type,temporary.resolve(UUID.randomUUID()+".json"));}
            try(var c=connect()) {assertThrows(IllegalStateException.class,()->SagaJournalRepairMain.resolvePending(c,own.id,own.type,currentProof));}
            assertSnapshotUnchanged(own,before);assertFalse(Files.exists(proof.output()));
        }
    }

    @Test
    void lebenderClaimVerhindertOfflineAufloesungOhneDatenaenderung() throws Exception {
        try(Fixture own=new Fixture()) {
            var proof=proof(own);
            try(var claim=own.journal.claimUnfinished(own.id,own.type);var c=connect()) {
                assertNotNull(claim);
                SQLException locked=assertThrows(SQLException.class,()->SagaJournalRepairMain.resolvePending(c,own.id,own.type,proof));
                assertEquals(54,locked.getErrorCode());
                assertState(own,"FAILED",own.marker+";199.90","PENDING");
            }
            assertSnapshotUnchanged(own,proof.snapshotSha256());assertFalse(Files.exists(proof.output()));
        }
    }

    @Test
    void snapshotUndAufloesungsverzeichnisWerdenNieUeberschrieben() throws Exception {
        try(Fixture own=new Fixture()) {
            var proof=proof(own);
            try(var c=connect()) {assertThrows(FileAlreadyExistsException.class,
                    ()->SagaJournalRepairMain.inspect(c,own.id,own.type,proof.snapshot()));}
            assertEquals(proof.snapshotSha256(),SagaJournalRepairMain.sha256(Files.readAllBytes(proof.snapshot())));
            Files.createDirectory(proof.output());Files.writeString(proof.output().resolve("existing.txt"),"erhalten");
            try(var c=connect()) {assertThrows(FileAlreadyExistsException.class,
                    ()->SagaJournalRepairMain.resolvePending(c,own.id,own.type,proof));}
            assertEquals("erhalten",Files.readString(proof.output().resolve("existing.txt")));
            assertSnapshotUnchanged(own,proof.snapshotSha256());
        }
    }

    @Test
    void verloreneCommitQuittungWirdNieAlsErfolgreicheAufloesungAusgegeben() throws Exception {
        try(Fixture own=new Fixture();var raw=connect()) {
            var proof=proof(own);AtomicBoolean once=new AtomicBoolean();
            Connection losingAck=(Connection)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{Connection.class},(p,m,a)->{
                try {
                    Object value=m.invoke(raw,a);
                    if(m.getName().equals("commit")&&once.compareAndSet(false,true))throw new SQLException("synthetisch verlorene Commit-Quittung");
                    return value;
                } catch(InvocationTargetException e) {throw e.getCause();}
            });
            assertThrows(SagaJournalRepairMain.CommitOutcomeUnknownException.class,
                    ()->SagaJournalRepairMain.resolvePending(losingAck,own.id,own.type,proof));
            assertState(own,"COMPENSATED",null,"PENDING");
            assertEquals("COMMIT_OUTCOME_UNKNOWN",outcomeState(proof.output().resolve("resolution-outcome.json")));
            assertTrue(Files.isRegularFile(proof.output().resolve("resolution-intent.json")));
        }
    }

    private SagaJournalRepairMain.Resolution proof(Fixture own) throws Exception {
        Path snapshot=temporary.resolve(UUID.randomUUID()+".json"),evidence=temporary.resolve(UUID.randomUUID()+".txt");
        String hash;try(var c=connect()) {hash=SagaJournalRepairMain.inspect(c,own.id,own.type,snapshot);}
        Files.writeString(evidence,"synthetischer unabhängiger Nullwirkungsnachweis");
        return new SagaJournalRepairMain.Resolution(snapshot,hash,evidence,SagaJournalRepairMain.sha256(Files.readAllBytes(evidence)),
                "test-admin","Synthetischer Wirkungsabgleich",true,true,temporary.resolve("resolution-"+UUID.randomUUID()));
    }
    private void assertSnapshotUnchanged(Fixture own,String expected) throws Exception {
        try(var c=connect()) {assertEquals(expected,SagaJournalRepairMain.inspect(c,own.id,own.type,temporary.resolve(UUID.randomUUID()+".json")));}
    }
    private static int startupGuard(SagaEngine.RecoveryReport report) {
        return LmgServerMain.requireSuccessfulRecovery(new Systemdiagnose.WiederanlaufErgebnis(Map.of(
                AntragsSagaProzess.definition().type(),report,AntragsDemoProzess.definition().type(),new SagaEngine.RecoveryReport(0,0,0,0))));
    }
    private static void assertState(Fixture own,String state,String payload,String step) throws SQLException {
        try(var c=connect();var st=c.prepareStatement("SELECT s.STATE,s.PAYLOAD,x.STATE FROM TX_SAGA s JOIN TX_SAGA_STEP x ON x.SAGA_ID = s.SAGA_ID WHERE s.SAGA_ID = ? AND s.SAGA_TYPE = ?")) {
            st.setString(1,own.id);st.setString(2,own.type);try(var rows=st.executeQuery()) {
                assertTrue(rows.next());assertEquals(state,rows.getString(1));assertEquals(payload,rows.getString(2));assertEquals(step,rows.getString(3));assertFalse(rows.next());
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
        p.setProperty("oracle.net.CONNECT_TIMEOUT","5000");p.setProperty("oracle.jdbc.ReadTimeout","10000");
        return DriverManager.getConnection(System.getenv("TX_URL"),p);
    }
    private static final class Fixture implements AutoCloseable {
        final String id=UUID.randomUUID().toString(),type="ALT_REPAIR_"+id,marker="ALT-"+id;
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
    private static Object outcomeState(Path file) throws Exception {return ((Map<?,?>)Json.parse(Files.readString(file))).get("state");}
}
