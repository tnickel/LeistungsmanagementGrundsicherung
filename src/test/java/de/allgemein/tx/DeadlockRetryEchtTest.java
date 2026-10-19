package de.allgemein.tx;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.math.BigDecimal;
import java.sql.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

/** Echte Oracle-Kreuzsperren; jede Ausführung besitzt eigene Sequenz-IDs und UUID-Aktenzeichen. */
@EnabledIfEnvironmentVariable(named="TX_URL",matches=".+")
class DeadlockRetryEchtTest {
    @Test
    void echterDeadlockWirdErkanntUndImZweitenVersuchCommittet() throws Exception {
        try(Fixture own=new Fixture()) { runDeadlock(own);own.assertBudgets(); }
    }

    @Test
    void zweiParalleleDeadlockFixturesBleibenAuchBeimAufraeumenGetrennt() throws Exception {
        try(Fixture first=new Fixture();Fixture second=new Fixture()) {
            assertEquals(4,Set.of(first.rowA,first.rowB,second.rowA,second.rowB).size());
            var parallel=Executors.newFixedThreadPool(2);
            try {
                var a=parallel.submit(()->{runDeadlock(first);return null;});
                var b=parallel.submit(()->{runDeadlock(second);return null;});
                a.get(70,TimeUnit.SECONDS);b.get(70,TimeUnit.SECONDS);
            } finally { stop(parallel); }
            first.assertBudgets();second.assertBudgets();
            first.close();
            assertEquals(0,first.countOwnRows());
            second.assertBudgets();
        }
    }

    private static void runDeadlock(Fixture own) throws Exception {
        CountDownLatch aLocked=new CountDownLatch(1),bLocked=new CountDownLatch(1);
        AtomicInteger attemptsA=new AtomicInteger(),attemptsB=new AtomicInteger();
        var workers=Executors.newFixedThreadPool(2);
        try {
            var a=workers.submit(()->own.tx.execute(TxOptions.DEFAULT,ctx->{
                int attempt=attemptsA.incrementAndGet();
                lock(ctx.connection(),own.rowA);
                if(attempt==1) {
                    aLocked.countDown();assertTrue(bLocked.await(15,TimeUnit.SECONDS),"zweite eigene Sperre fehlt");
                }
                lock(ctx.connection(),own.rowB);
                updateBudget(ctx.connection(),own.rowA,"111.00");return null;
            }));
            var b=workers.submit(()->own.tx.execute(TxOptions.DEFAULT,ctx->{
                int attempt=attemptsB.incrementAndGet();
                if(attempt==1) assertTrue(aLocked.await(15,TimeUnit.SECONDS),"erste eigene Sperre fehlt");
                lock(ctx.connection(),own.rowB);
                if(attempt==1) bLocked.countDown();
                lock(ctx.connection(),own.rowA);
                updateBudget(ctx.connection(),own.rowB,"222.00");return null;
            }));
            a.get(60,TimeUnit.SECONDS);b.get(60,TimeUnit.SECONDS);
            assertEquals(3,attemptsA.get()+attemptsB.get(),"genau ein Oracle-Deadlock und ein erfolgreicher Retry");
        } finally { stop(workers); }
    }

    private static void stop(ExecutorService executor) throws InterruptedException {
        executor.shutdownNow();
        assertTrue(executor.awaitTermination(15,TimeUnit.SECONDS),"eigene Deadlock-Worker müssen vor Cleanup beendet sein");
    }
    private static void lock(Connection c,long id) throws SQLException {
        try(var st=c.prepareStatement("SELECT ID FROM BEDARFSGEMEINSCHAFT WHERE ID = ? FOR UPDATE")) {
            st.setLong(1,id);try(var rows=st.executeQuery()) {assertTrue(rows.next(),"eigene Zeile "+id+" fehlt");}
        }
    }
    private static void updateBudget(Connection c,long id,String budget) throws SQLException {
        try(var st=c.prepareStatement("UPDATE BEDARFSGEMEINSCHAFT SET BUDGET_REST = ? WHERE ID = ?")) {
            st.setBigDecimal(1,new BigDecimal(budget));st.setLong(2,id);assertEquals(1,st.executeUpdate());
        }
    }
    private static Connection connect() throws SQLException {
        var properties=new Properties();
        properties.setProperty("user",System.getenv("TX_USER"));properties.setProperty("password",System.getenv("TX_PASS"));
        properties.setProperty("oracle.net.CONNECT_TIMEOUT","5000");properties.setProperty("oracle.jdbc.ReadTimeout","10000");
        return DriverManager.getConnection(System.getenv("TX_URL"),properties);
    }
    private static final class Fixture implements AutoCloseable {
        final String prefix="BG-DL-"+UUID.randomUUID();
        final long rowA,rowB;
        final SimpleConnectionPool pool;
        final TxManager tx;
        boolean closed;
        Fixture() throws Exception {
            Class.forName("oracle.jdbc.OracleDriver");
            try(var c=connect()) {
                c.setAutoCommit(false);
                rowA=insert(c,prefix+"-A","100.00");rowB=insert(c,prefix+"-B","200.00");
                c.commit();
            }
            pool=new SimpleConnectionPool(System.getenv("TX_URL"),System.getenv("TX_USER"),System.getenv("TX_PASS"),
                    4,Duration.ofSeconds(5),5000,10000);
            tx=new TxManager(pool);
        }
        private static long insert(Connection c,String az,String budget) throws SQLException {
            long id;
            try(var st=c.prepareStatement("SELECT SEQ_BG.NEXTVAL FROM DUAL");var rows=st.executeQuery()) {
                assertTrue(rows.next());id=rows.getLong(1);
            }
            try(var st=c.prepareStatement("INSERT INTO BEDARFSGEMEINSCHAFT (ID,AKTENZEICHEN,HAUPTPERSON_NAME,STATUS_AKTIV,BUDGET_REST) VALUES (?,?,'Deadlock Test',1,?)")) {
                st.setLong(1,id);st.setString(2,az);st.setBigDecimal(3,new BigDecimal(budget));assertEquals(1,st.executeUpdate());
            }
            return id;
        }
        void assertBudgets() throws SQLException {
            for(var entry:Map.of(rowA,"111.00",rowB,"222.00").entrySet()) {
                try(var c=connect();var st=c.prepareStatement("SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ? AND AKTENZEICHEN = ?")) {
                    st.setLong(1,entry.getKey());st.setString(2,prefix+(entry.getKey()==rowA?"-A":"-B"));
                    try(var rows=st.executeQuery()) {assertTrue(rows.next());assertEquals(0,new BigDecimal(entry.getValue()).compareTo(rows.getBigDecimal(1)));}
                }
            }
        }
        int countOwnRows() throws SQLException {
            try(var c=connect();var st=c.prepareStatement("SELECT COUNT(*) FROM BEDARFSGEMEINSCHAFT WHERE (ID = ? AND AKTENZEICHEN = ?) OR (ID = ? AND AKTENZEICHEN = ?)")) {
                bind(st);try(var rows=st.executeQuery()) {assertTrue(rows.next());return rows.getInt(1);}
            }
        }
        private void bind(PreparedStatement st) throws SQLException {
            st.setLong(1,rowA);st.setString(2,prefix+"-A");st.setLong(3,rowB);st.setString(4,prefix+"-B");
        }
        public void close() throws SQLException {
            if(closed)return;
            pool.close();
            try(var c=connect();var st=c.prepareStatement("DELETE FROM BEDARFSGEMEINSCHAFT WHERE (ID = ? AND AKTENZEICHEN = ?) OR (ID = ? AND AKTENZEICHEN = ?)")) {
                c.setAutoCommit(false);st.setQueryTimeout(10);bind(st);st.executeUpdate();c.commit();
            }
            closed=true;
        }
    }
}
