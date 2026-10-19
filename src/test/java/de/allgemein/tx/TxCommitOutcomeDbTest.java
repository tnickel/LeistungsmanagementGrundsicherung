package de.allgemein.tx;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.*;
import java.time.Duration;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Echter Oracle-Commit; nur Quittungsverlust bzw. ein Interrupt ignorierender Commit werden injiziert. */
@EnabledIfEnvironmentVariable(named="TX_URL",matches=".+")
class TxCommitOutcomeDbTest {
    @Test
    void echterCommitMitVerlorenerQuittungBleibtPersistiertOhneWiederholung() throws Exception {
        try(Fixture own=new Fixture(false)) {
            AtomicInteger work=new AtomicInteger();
            var error=assertThrows(TxCommitOutcomeUnknownException.class,()->own.tx.execute(TxOptions.DEFAULT,ctx->{
                work.incrementAndGet();own.insert(ctx.connection());return "Fachwirkung";
            }));
            assertFalse(error.isRetryable());assertEquals(17002,error.oracleErrorCode());
            assertEquals(1,work.get());assertEquals(1,own.pool.commits.get());
            assertEquals(1,own.effectCount(),"unabhängige Verbindung beweist den echten Commit trotz fehlender Quittung");
        }
    }

    @Test
    void manuelleApiBehauptetNachEchtemCommitUndVerlorenerQuittungKeinenRollback() throws Exception {
        try(Fixture own=new Fixture(false)) {
            TxContext ctx=own.tx.begin(TxOptions.DEFAULT);
            try {
                own.insert(ctx.connection());
                assertThrows(TxCommitOutcomeUnknownException.class,()->own.tx.commit(ctx));
                assertEquals(TxStatus.COMMIT_OUTCOME_UNKNOWN,ctx.status());
                own.tx.rollback(ctx);
                assertEquals(TxStatus.COMMIT_OUTCOME_UNKNOWN,ctx.status());
            } finally {own.tx.finish(ctx);}
            assertEquals(TxStatus.COMMIT_OUTCOME_UNKNOWN,ctx.status());
            assertEquals(1,own.pool.commits.get());assertEquals(1,own.effectCount());
        }
    }

    @Test
    void fatalerFehlerNachEchtemCommitBleibtErrorMitBekannterCommitphaseUndPersistierterWirkung() throws Exception {
        try(Fixture own=new Fixture(false,true)) {
            AtomicInteger work=new AtomicInteger();
            var error=assertThrows(TxCommitOutcomeUnknownError.class,()->own.tx.execute(TxOptions.DEFAULT,ctx->{
                work.incrementAndGet();own.insert(ctx.connection());return "Fachwirkung";
            }));
            assertInstanceOf(AssertionError.class,error.getCause());
            assertEquals(1,work.get());assertEquals(1,own.pool.commits.get());assertEquals(1,own.effectCount());
        }
    }

    @Test
    void wachhundWaehrendInterruptIgnorierendemCommitMeldetUnbekanntAuchWennOracleSpaeterCommittet() throws Exception {
        try(Fixture own=new Fixture(true);var tasks=new TaskController(own.tx,1,1)) {
            var handle=tasks.submit("commit-laeuft",TxOptions.DEFAULT.withTimeout(Duration.ofSeconds(2)),ctx->{
                own.insert(ctx.connection());return "Fachwirkung";
            });
            try {
                assertTrue(own.pool.commitEntered.await(5,TimeUnit.SECONDS),"Wachhund muss im Commitfenster liegen");
                var error=assertThrows(TaskOutcomeUnknownException.class,handle::result);
                assertFalse(error.isRetryable());
                assertEquals(0,own.effectCount(),"echter Commit wird für die Probe noch gezielt zurückgehalten");
            } finally {own.pool.continueCommit.countDown();}
            assertTrue(own.pool.released.await(5,TimeUnit.SECONDS),"Worker muss bounded auslaufen");
            assertEquals(1,own.pool.commits.get());assertEquals(1,own.effectCount());
            assertEquals(1,tasks.stats().timedOut());assertEquals(0,tasks.stats().completed());
        }
    }

    private static Connection connect() throws SQLException {
        var properties=new java.util.Properties();properties.setProperty("user",System.getenv("TX_USER"));
        properties.setProperty("password",System.getenv("TX_PASS"));properties.setProperty("oracle.net.CONNECT_TIMEOUT","5000");
        properties.setProperty("oracle.jdbc.ReadTimeout","10000");
        return DriverManager.getConnection(System.getenv("TX_URL"),properties);
    }
    private static final class Fixture implements AutoCloseable {
        final String marker="COMMIT-OUTCOME-"+UUID.randomUUID();
        final FaultPool pool;final TxManager tx;
        Fixture(boolean blockCommit) throws Exception {this(blockCommit,false);}
        Fixture(boolean blockCommit,boolean fatal) throws Exception {
            pool=new FaultPool(blockCommit,fatal);tx=new TxManager(pool);
        }
        void insert(Connection c) throws SQLException {
            try(var st=c.prepareStatement("INSERT INTO TX_DEMO_ANTRAG (NAME,STATUS,BETRAG) VALUES (?,'NEU',199.90)")) {
                st.setString(1,marker);assertEquals(1,st.executeUpdate());
            }
        }
        int effectCount() throws SQLException {
            try(var c=connect();var st=c.prepareStatement("SELECT COUNT(*) FROM TX_DEMO_ANTRAG WHERE NAME = ?")) {
                st.setString(1,marker);try(var rows=st.executeQuery()) {assertTrue(rows.next());return rows.getInt(1);}
            }
        }
        public void close() throws SQLException {
            pool.continueCommit.countDown();pool.close();
            try(var c=connect();var st=c.prepareStatement("DELETE FROM TX_DEMO_ANTRAG WHERE NAME = ?")) {
                st.setString(1,marker);st.executeUpdate();
            }
        }
    }
    private static final class FaultPool implements ConnectionPool {
        final SimpleConnectionPool delegate=new SimpleConnectionPool(System.getenv("TX_URL"),System.getenv("TX_USER"),
                System.getenv("TX_PASS"),2,Duration.ofSeconds(5),5000,10000);
        final Map<Connection,Connection> rawConnections=new IdentityHashMap<>();
        final AtomicInteger commits=new AtomicInteger();final AtomicBoolean first=new AtomicBoolean(true);
        final CountDownLatch commitEntered=new CountDownLatch(1),continueCommit=new CountDownLatch(1),released=new CountDownLatch(1);
        final boolean blockCommit,fatal;
        FaultPool(boolean blockCommit,boolean fatal) throws SQLException {
            this.blockCommit=blockCommit;this.fatal=fatal;
            // Verbindungsaufbau gehört nicht zum gezielt geprüften Commitfenster.
            Connection one=delegate.getConnection(),two=null;
            try {two=delegate.getConnection();}finally {delegate.release(one);if(two!=null)delegate.release(two);}
        }
        public synchronized Connection getConnection() throws SQLException {
            Connection raw=delegate.getConnection();
            Connection wrapper=(Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class<?>[]{Connection.class},(p,m,a)->{
                boolean interrupted=false;
                try {
                    boolean selected=m.getName().equals("commit")&&first.compareAndSet(true,false);
                    if(selected&&blockCommit) {
                        commitEntered.countDown();long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);
                        while(continueCommit.getCount()!=0&&System.nanoTime()<until) {
                            try {continueCommit.await(50,TimeUnit.MILLISECONDS);}catch(InterruptedException e) {interrupted=true;}
                        }
                        if(continueCommit.getCount()!=0)throw new SQLException("Commitprobe nicht rechtzeitig freigegeben");
                        // Simuliert ausdrücklich einen Treiber, der den Interrupt ignoriert.
                        interrupted|=Thread.interrupted();
                    }
                    Object result=m.invoke(raw,a);
                    if(m.getName().equals("commit"))commits.incrementAndGet();
                    if(selected&&!blockCommit) {
                        if(fatal)throw new AssertionError("synthetischer Error nach echtem Commit");
                        throw new SQLRecoverableException("synthetisch verlorene Commit-Quittung","08006",17002);
                    }
                    return result;
                } catch(InvocationTargetException e) {throw e.getCause();}
                finally {if(interrupted)Thread.currentThread().interrupt();}
            });
            rawConnections.put(wrapper,raw);return wrapper;
        }
        public synchronized void release(Connection wrapper) {
            try {delegate.release(rawConnections.remove(wrapper));}finally {released.countDown();}
        }
        public void close() {delegate.close();}
    }
}
