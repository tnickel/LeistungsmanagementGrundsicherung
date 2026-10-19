package de.allgemein.testsweet;

import de.allgemein.tx.*;
import de.allgemein.tx.saga.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.lang.reflect.*;
import java.sql.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

/** Prüft den installierten Produktkern mit echten Oracle-Journalzeilen. */
@EnabledIfEnvironmentVariable(named = "TX_URL", matches = ".+")
class ProduktSagaJournalRegressionDbTest {
    @Test
    void namenloseOderUmbenannteInFlightWirkungBleibtFailedMitPayloadStattIndexkompensation() throws Exception {
        for (boolean legacyMissingName : List.of(true,false)) {
            String id=UUID.randomUUID().toString(),type="SWEET_UNKNOWN_"+id;
            AtomicInteger wrong=new AtomicInteger();
            try(var pool=pool()) {
                var journal=new SagaJournal(pool);
                try {
                    if(legacyMissingName) journal.start(id,type,"synthetischer-payload",1);
                    else journal.start(id,type,"synthetischer-payload",List.of("alte-wirkung"));
                    journal.markStepInFlight(id,0);
                    var changed=SagaDefinition.of(type)
                            .step("neue-wirkung",(c,p,s)->{},(c,p,s)->wrong.incrementAndGet()).build();
                    var engine=new SagaEngine(new TxManager(pool),journal);
                    for(int attempt=0;attempt<2;attempt++) {
                        var report=engine.recoverWithReport(changed);
                        assertEquals(1,report.failed()); assertEquals(0,report.errors());
                        assertEquals(0,wrong.get(),"aktueller Index ist kein Beleg für die alte Wirkung");
                        assertState(id,"FAILED","IN_FLIGHT",false);
                    }
                    try(var claim=journal.claimUnfinished(id,type)) {
                        assertNotNull(claim);
                        assertEquals("synthetischer-payload",claim.saga().payload());
                        assertEquals(legacyMissingName?null:"alte-wirkung",claim.saga().stepName(0));
                    }
                } finally { cleanup(id,type); }
            }
        }
    }

    @Test
    void vollstaendigDoneGeschriebeneAlteSagaWirdNachErweiterungNurCompleted() throws Exception {
        String id=UUID.randomUUID().toString(),type="SWEET_DONE_"+id;
        AtomicInteger compensation=new AtomicInteger();
        try(var pool=pool()) {
            var journal=new SagaJournal(pool);
            try {
                journal.start(id,type,"synthetischer-payload",List.of("bestaetigt"));
                journal.markStepInFlight(id,0);
                new TxManager(pool).execute(TxOptions.DEFAULT,ctx->{
                    journal.stepDone(ctx.connection(),id,0,"bestaetigt");return null;
                });
                var changed=SagaDefinition.of(type)
                        .step("vorgeschaltet",(c,p,s)->{},(c,p,s)->compensation.incrementAndGet())
                        .step("bestaetigt",(c,p,s)->{},(c,p,s)->compensation.incrementAndGet()).build();
                var engine=new SagaEngine(new TxManager(pool),journal);
                var report=engine.recoverWithReport(changed);
                assertEquals(1,report.completed());assertEquals(0,report.fehlerhaft());
                assertEquals(0,compensation.get(),"neue Definition macht einen alten vollständigen Commit nicht rückgängig");
                assertState(id,"COMPLETED","DONE",true);
                assertEquals(0,engine.recoverWithReport(changed).behandelt());
            } finally { cleanup(id,type); }
        }
    }

    @Test
    void geaenderterNameBeimDoneSchreibenWirdMitDerFachtransaktionZurueckgerollt() throws Exception {
        String id=UUID.randomUUID().toString(),type="SWEET_BIND_"+id;
        String ownEffectName="SWEET-NAME-GUARD-"+id;
        AtomicBoolean guardReached=new AtomicBoolean();
        try(var pool=pool()) {
            var journal=new SagaJournal(pool);
            try {
                journal.start(id,type,"synthetischer-payload",List.of("urspruenglich"));
                journal.markStepInFlight(id,0);
                TxException failure=assertThrows(TxException.class,()->new TxManager(pool).execute(TxOptions.DEFAULT,ctx->{
                    insertOwnEffect(ctx.connection(),ownEffectName);
                    guardReached.set(true);
                    journal.stepDone(ctx.connection(),id,0,"falsch-umbenannt");return null;
                }));
                assertTrue(guardReached.get(),"Fixture-INSERT muss erfolgreich sein, bevor der Namensguard läuft");
                assertTrue(failure.getMessage().contains("Journal-Eintrag"),
                        "Erwartet ist der Namensguard, kein Fixture-/Schemafehler: "+failure.getMessage());
                assertState(id,"RUNNING","IN_FLIGHT",false);
                try(var c=connect()) { assertEquals(0,countOwnEffects(c,ownEffectName)); }
                try(var claim=journal.claimUnfinished(id,type)) {
                    assertEquals("urspruenglich",claim.saga().stepName(0));
                }
            } finally { cleanup(id,type,ownEffectName); }
        }
    }

    @Test
    void namensbindungUndPayloadfreierVorscanBleibenNachDefinitionswechselWirksam() throws Exception {
        String id = UUID.randomUUID().toString(), type = "SWEET_NAMES_" + id;
        AtomicInteger wrong = new AtomicInteger(), right = new AtomicInteger();
        try (var pool = pool()) {
            var journal = new SagaJournal(pool);
            try {
                journal.start(id, type, "synthetischer-payload", List.of("alte-wirkung", "offen"));
                var candidate = journal.unfinished().stream().filter(s -> id.equals(s.sagaId())).findFirst().orElseThrow();
                assertNull(candidate.payload(), "Vorscan darf Nutzdaten nicht laden");
                assertTrue(candidate.stepStates().isEmpty(), "Schritte werden erst unter Claim geladen");
                try (var claim = journal.claimUnfinished(id, type)) {
                    assertEquals("synthetischer-payload", claim.saga().payload());
                    assertEquals("alte-wirkung", claim.saga().stepName(0));
                    assertEquals("offen", claim.saga().stepName(1));
                }
                journal.markStepInFlight(id, 0);
                new TxManager(pool).execute(TxOptions.DEFAULT, ctx -> {
                    journal.stepDone(ctx.connection(), id, 0, "alte-wirkung"); return null;
                });
                var changed = SagaDefinition.of(type)
                        .step("vorgeschaltet", (c,p,s) -> {}, (c,p,s) -> wrong.incrementAndGet())
                        .step("offen", (c,p,s) -> {}, (c,p,s) -> wrong.incrementAndGet())
                        .step("alte-wirkung", (c,p,s) -> {}, (c,p,s) -> right.incrementAndGet()).build();
                var report = new SagaEngine(new TxManager(pool), journal).recoverWithReport(changed);
                assertEquals(1, report.compensated()); assertEquals(0, report.fehlerhaft());
                assertEquals(0, wrong.get()); assertEquals(1, right.get());
                assertState(id, "COMPENSATED", "COMPENSATED", true);
            } finally { cleanup(id, type); }
        }
    }

    @Test
    void serverCommitOhneWriteAheadQuittungBleibtBisRecoveryPayloadgebunden() throws Exception {
        String type = "SWEET_ACK_" + UUID.randomUUID();
        AtomicInteger actions = new AtomicInteger(), compensations = new AtomicInteger();
        AtomicInteger commits = new AtomicInteger();
        AtomicReference<String> id = new AtomicReference<>();
        try (var delegate = pool()) {
            // Genau die zweite Journal-Transaktion ist das erste Write-Ahead.
            // Der echte Oracle-Commit läuft durch; erst seine Client-Quittung wird ersetzt.
            var connections = Collections.synchronizedMap(new IdentityHashMap<Connection, Connection>());
            ConnectionPool losingAck = new ConnectionPool() {
                public Connection getConnection() throws SQLException {
                    Connection raw = delegate.getConnection();
                    Connection proxy = (Connection) Proxy.newProxyInstance(getClass().getClassLoader(),
                            new Class<?>[]{Connection.class}, (p,m,a) -> {
                                try {
                                    Object value = m.invoke(raw, a);
                                    if (m.getName().equals("commit") && commits.incrementAndGet() == 2)
                                        throw new SQLException("synthetisch verlorene Commit-Quittung");
                                    return value;
                                } catch (InvocationTargetException e) { throw e.getCause(); }
                            });
                    connections.put(proxy, raw); return proxy;
                }
                public void release(Connection c) { delegate.release(connections.remove(c)); }
                public void close() { }
            };
            var journal = new SagaJournal(losingAck);
            var engine = new SagaEngine(new TxManager(losingAck), journal);
            var definition = SagaDefinition.of(type)
                    .step("wirkung", (c,p,s) -> actions.incrementAndGet(), (c,p,s) -> compensations.incrementAndGet()).build();
            try {
                assertThrows(TxException.class, () -> engine.run(definition, "synthetischer-payload"));
                try (var c = connect(); var st = c.prepareStatement("SELECT SAGA_ID FROM TX_SAGA WHERE SAGA_TYPE = ?")) {
                    st.setString(1, type); try (var rs = st.executeQuery()) { assertTrue(rs.next()); id.set(rs.getString(1)); }
                }
                assertEquals(0, actions.get(), "Write-Ahead-Fehler liegt vor der Fachaktion");
                assertState(id.get(), "FAILED", "IN_FLIGHT", false);
                assertEquals(1, engine.recoverWithReport(definition).compensated());
                assertEquals(1, compensations.get(), "idempotente konservative Kompensation");
                assertState(id.get(), "COMPENSATED", "COMPENSATED", true);
            } finally { if (id.get() != null) cleanup(id.get(), type); }
        }
    }

    private static SimpleConnectionPool pool() throws SQLException {
        return new SimpleConnectionPool(System.getenv("TX_URL"), System.getenv("TX_USER"), System.getenv("TX_PASS"),
                4, Duration.ofSeconds(5), 5000, 10000);
    }
    private static Connection connect() throws SQLException {
        return DriverManager.getConnection(System.getenv("TX_URL"), System.getenv("TX_USER"), System.getenv("TX_PASS"));
    }
    private static void assertState(String id, String head, String step, boolean payloadNull) throws SQLException {
        try (var c = connect(); var st = c.prepareStatement(
                "SELECT s.STATE, s.PAYLOAD, x.STATE FROM TX_SAGA s JOIN TX_SAGA_STEP x ON x.SAGA_ID = s.SAGA_ID WHERE s.SAGA_ID = ? AND x.IDX = 0")) {
            st.setString(1, id); try (var rs = st.executeQuery()) {
                assertTrue(rs.next()); assertEquals(head, rs.getString(1));
                assertEquals(payloadNull, rs.getString(2) == null); assertEquals(step, rs.getString(3));
            }
        }
    }
    private static void cleanup(String id, String type) throws SQLException {
        cleanup(id,type,null);
    }
    private static void cleanup(String id,String type,String ownEffectName) throws SQLException {
        try (var c = connect()) {
            c.setAutoCommit(false);
            cleanupOwnRows(c,id,type,ownEffectName);
            c.commit();
        }
    }

    // Nur dieser Fixture-Schritt braucht eine Demo-Zeile. NAME ist auch im
    // historischen Demo-Schema vorhanden; der eindeutige UUID-Name wird vor dem
    // INSERT registriert und ausschließlich als exakter Parameter verwendet.
    static void insertOwnEffect(Connection c,String ownEffectName) throws SQLException {
        try(var st=c.prepareStatement("INSERT INTO TX_DEMO_ANTRAG (NAME,STATUS,BETRAG) VALUES (?,'NEU',1)")) {
            st.setString(1,ownEffectName);assertEquals(1,st.executeUpdate());
        }
    }
    static int countOwnEffects(Connection c,String ownEffectName) throws SQLException {
        try(var st=c.prepareStatement("SELECT COUNT(*) FROM TX_DEMO_ANTRAG WHERE NAME = ?")) {
            st.setString(1,ownEffectName);
            try(var rs=st.executeQuery()) { assertTrue(rs.next());return rs.getInt(1); }
        }
    }
    static void cleanupOwnRows(Connection c,String id,String type,String ownEffectName) throws SQLException {
        if(ownEffectName!=null) {
            try(var st=c.prepareStatement("DELETE FROM TX_DEMO_ANTRAG WHERE NAME = ?")) {
                st.setString(1,ownEffectName);st.executeUpdate();
            }
        }
        for(String sql:List.of("DELETE FROM TX_SAGA_STEP WHERE SAGA_ID IN (SELECT SAGA_ID FROM TX_SAGA WHERE SAGA_ID = ? AND SAGA_TYPE = ?)",
                "DELETE FROM TX_SAGA WHERE SAGA_ID = ? AND SAGA_TYPE = ?")) {
            try(var st=c.prepareStatement(sql)) { st.setString(1,id);st.setString(2,type);st.executeUpdate(); }
        }
    }
}
