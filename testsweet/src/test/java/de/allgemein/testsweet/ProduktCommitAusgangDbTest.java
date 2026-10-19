package de.allgemein.testsweet;

import de.allgemein.lmg.model.SozialFall;
import de.allgemein.lmg.service.SozialLeistungsService;
import de.allgemein.lmg.web.Benutzerverwaltung;
import de.allgemein.lmg.web.Json;
import de.allgemein.lmg.web.LmgServer;
import de.allgemein.tx.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.*;
import java.sql.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Produkt-JAR, Produkt-HTTP und echte Zahlung; ausschließlich die Commit-Quittung geht synthetisch verloren. */
@EnabledIfEnvironmentVariable(named="TX_URL",matches=".+")
class ProduktCommitAusgangDbTest {
    @Test
    void verloreneQuittungNachEchtemServiceCommitLiefert409UndReplayBleibtEindeutig() throws Exception {
        assertHttpUnknownCommit(false);
    }

    @Test
    void fatalerFehlerNachEchtemServiceCommitLiefert409UndReplayBleibtEindeutig() throws Exception {
        assertHttpUnknownCommit(true);
    }

    private static void assertHttpUnknownCommit(boolean fatal) throws Exception {
        try(Fixture own=new Fixture(fatal);var tasks=new TaskController(own.normalTx,2,10);
            var server=new LmgServer(tasks,own.service,null,new Benutzerverwaltung("sb-commit:testpass:sachbearbeiter"),0)) {
            server.start();String base="http://127.0.0.1:"+server.adresse().getPort();
            var client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
            var login=send(client,base,"/api/anmeldung",Map.of("kennung","sb-commit","passwort","testpass"),null);
            assertEquals(200,login.statusCode(),login.body());
            String token=String.valueOf(((Map<?,?>)Json.parse(login.body())).get("token"));
            String number=UUID.randomUUID().toString(),different=UUID.randomUUID().toString();
            var order=Map.<String,Object>of("bgId",own.bgId,"vorgangsNr",number,"zahlungsmonat","2026-10");
            var unknown=send(client,base,"/api/auszahlung",order,token);
            assertEquals(1,own.faultPool.lostAcknowledgements.get(),"Fehler erst NACH realem innerem Commit");
            assertEquals(1,own.faultPool.commits.get(),"Commitfehler darf keine Wiederholung der Facharbeit auslösen");
            assertEquals(409,unknown.statusCode(),unknown.body());
            assertTrue(unknown.body().contains("AUSGANG_UNBEKANNT"),unknown.body());
            assertTrue(unknown.body().contains("Status neu laden"),unknown.body());
            assertFalse(unknown.body().contains("bitte erneut versuchen"),unknown.body());
            own.assertState(number,1,new BigDecimal("9437.00"));

            var replay=send(client,base,"/api/auszahlung",order,token);
            assertEquals(200,replay.statusCode(),replay.body());
            assertEquals(Boolean.TRUE,((Map<?,?>)Json.parse(replay.body())).get("bereitsGebucht"));
            var second=send(client,base,"/api/auszahlung",Map.of("bgId",own.bgId,
                    "vorgangsNr",different,"zahlungsmonat","2026-10"),token);
            assertEquals(400,second.statusCode(),second.body());
            assertTrue(second.body().contains("bereits eine nicht stornierte Zahlung"),second.body());
            own.assertState(number,1,new BigDecimal("9437.00"));
            try(var c=connect();var st=c.prepareStatement("SELECT COUNT(*) FROM ZAHLUNG WHERE VORGANGS_NR = ?")) {
                st.setString(1,different);try(var rows=st.executeQuery()) {assertTrue(rows.next());assertEquals(0,rows.getInt(1));}
            }
        }
    }

    @Test
    void manuellesRollbackUndFinishBehaltenUnbekanntenAusgangNachEchtemOracleCommit() throws Exception {
        try(Fixture own=new Fixture()) {
            TxContext ctx=own.faultTx.begin(TxOptions.DEFAULT);
            try {
                try(var st=ctx.connection().prepareStatement("UPDATE BEDARFSGEMEINSCHAFT SET BUDGET_REST = BUDGET_REST - 1 WHERE ID = ?")) {
                    st.setLong(1,own.bgId);assertEquals(1,st.executeUpdate());
                }
                var error=assertThrows(TxCommitOutcomeUnknownException.class,()->own.faultTx.commit(ctx));
                assertFalse(error.isRetryable());assertEquals(TxStatus.COMMIT_OUTCOME_UNKNOWN,ctx.status());
                own.faultTx.rollback(ctx);assertEquals(TxStatus.COMMIT_OUTCOME_UNKNOWN,ctx.status());
            } finally {own.faultTx.finish(ctx);}
            assertEquals(TxStatus.COMMIT_OUTCOME_UNKNOWN,ctx.status());
            assertEquals(1,own.faultPool.lostAcknowledgements.get());
            own.assertState(null,0,new BigDecimal("9999.00"));
        }
    }

    private static HttpResponse<String> send(HttpClient client,String base,String path,Map<String,?> body,String token) throws Exception {
        var request=HttpRequest.newBuilder(URI.create(base+path)).timeout(Duration.ofSeconds(15))
                .header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(Json.write(body)));
        if(token!=null)request.header("Authorization","Bearer "+token);
        return client.send(request.build(),HttpResponse.BodyHandlers.ofString());
    }
    private static Connection connect() throws SQLException {
        var properties=new Properties();properties.setProperty("user",System.getenv("TX_USER"));properties.setProperty("password",System.getenv("TX_PASS"));
        properties.setProperty("oracle.net.CONNECT_TIMEOUT","5000");properties.setProperty("oracle.jdbc.ReadTimeout","10000");
        return DriverManager.getConnection(System.getenv("TX_URL"),properties);
    }
    private static final class Fixture implements AutoCloseable {
        final SimpleConnectionPool pool;
        final TxManager normalTx,faultTx;final AckLossPool faultPool;
        final SozialLeistungsService service;final long bgId;
        Fixture() throws Exception {this(false);}
        Fixture(boolean fatal) throws Exception {
            pool=new SimpleConnectionPool(System.getenv("TX_URL"),System.getenv("TX_USER"),System.getenv("TX_PASS"),
                    5,Duration.ofSeconds(5),5000,10000);
            List<Connection> warm=new ArrayList<>();
            try {for(int i=0;i<5;i++)warm.add(pool.getConnection());}finally {warm.forEach(pool::release);}
            normalTx=new TxManager(pool);var setup=new SozialLeistungsService(normalTx);
            bgId=setup.legeBedarfsgemeinschaftAn("SWEET-COMMIT-"+UUID.randomUUID(),"Synthetische Commitprüfung",
                    new BigDecimal("10000.00"),null,"DE89370400440532013000","COBADEFFXXX","Synthetischer Empfänger","sb-commit").id();
            var zero=BigDecimal.ZERO;
            setup.legeSozialFallAn(bgId,new SozialFall(0,bgId,1,false,false,zero,zero,zero,zero,zero,1),"sb-commit");
            setup.erteileBescheid(bgId,"2026-10","2026-10","sb-commit");
            faultPool=new AckLossPool(pool,fatal);faultTx=new TxManager(faultPool);service=new SozialLeistungsService(faultTx);
        }
        void assertState(String number,int count,BigDecimal budget) throws SQLException {
            try(var c=connect();var st=c.prepareStatement("SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?")) {
                st.setLong(1,bgId);try(var rows=st.executeQuery()) {assertTrue(rows.next());assertEquals(0,budget.compareTo(rows.getBigDecimal(1)));}
                try(var payments=c.prepareStatement("SELECT VORGANGS_NR,AUSZAHLUNGSBETRAG,ZAHLUNGSSTATUS FROM ZAHLUNG WHERE BG_ID = ?")) {
                    payments.setLong(1,bgId);try(var rows=payments.executeQuery()) {
                        int found=0;while(rows.next()) {
                            found++;assertEquals(number,rows.getString(1));assertEquals(0,new BigDecimal("563.00").compareTo(rows.getBigDecimal(2)));
                            assertEquals("ANGELEGT",rows.getString(3));
                        }assertEquals(count,found);
                    }
                }
            }
        }
        public void close() throws SQLException {
            pool.close();try(var c=connect()) {c.setAutoCommit(false);
                for(String table:List.of("JOURNAL","BESCHEID","ZAHLUNG","SOZIAL_FALL","BEDARFSGEMEINSCHAFT")) {
                    try(var st=c.prepareStatement("DELETE FROM "+table+" WHERE "+(table.equals("BEDARFSGEMEINSCHAFT")?"ID":"BG_ID")+" = ?")) {
                        st.setLong(1,bgId);st.executeUpdate();
                    }
                }c.commit();
            }
        }
    }
    private static final class AckLossPool implements ConnectionPool {
        final ConnectionPool delegate;final Map<Connection,Connection> rawConnections=new IdentityHashMap<>();
        final AtomicBoolean first=new AtomicBoolean(true);final AtomicInteger commits=new AtomicInteger(),lostAcknowledgements=new AtomicInteger();
        final boolean fatal;
        AckLossPool(ConnectionPool delegate,boolean fatal) {this.delegate=delegate;this.fatal=fatal;}
        public synchronized Connection getConnection() throws SQLException {
            Connection raw=delegate.getConnection();
            Connection wrapper=(Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class<?>[]{Connection.class},(p,m,a)->{
                try {
                    Object result=m.invoke(raw,a);
                    if(m.getName().equals("commit")) {
                        commits.incrementAndGet();
                        if(first.compareAndSet(true,false)) {
                            lostAcknowledgements.incrementAndGet();
                            if(fatal)throw new AssertionError("synthetischer Error nach echtem Service-Commit");
                            throw new SQLRecoverableException("synthetisch verlorene Commit-Quittung","08006",17002);
                        }
                    }
                    return result;
                } catch(InvocationTargetException e) {throw e.getCause();}
            });
            rawConnections.put(wrapper,raw);return wrapper;
        }
        public synchronized void release(Connection wrapper) {delegate.release(rawConnections.remove(wrapper));}
        public void close() {delegate.close();}
    }
}
