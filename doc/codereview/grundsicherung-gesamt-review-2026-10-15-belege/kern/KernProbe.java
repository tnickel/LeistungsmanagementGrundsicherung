package de.allgemein.tx.saga;

import de.allgemein.tx.*;
import java.lang.reflect.Proxy;
import java.sql.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.logging.Logger;

/** Isolierte Reviewprobe; keine echte DB, keine Änderung bestehender Tests. */
public final class KernProbe {
    static final List<String> calls = new ArrayList<>();
    static boolean auto = true;
    static boolean closed;
    static Connection connection() {
        return (Connection) Proxy.newProxyInstance(KernProbe.class.getClassLoader(),
            new Class<?>[]{Connection.class}, (proxy, method, args) -> switch(method.getName()) {
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                case "toString" -> "ReviewMockConnection";
                case "isClosed" -> closed;
                case "isValid" -> !closed;
                case "getAutoCommit" -> auto;
                case "setAutoCommit" -> { calls.add("autocommit="+args[0]); auto=(boolean)args[0]; yield null; }
                case "rollback", "commit" -> { calls.add(method.getName()); yield null; }
                case "setTransactionIsolation" -> null;
                case "close" -> { closed=true; yield null; }
                default -> throw new UnsupportedOperationException(method.getName());
            });
    }
    static final class DriverMock implements Driver {
        public Connection connect(String url, Properties props) { return acceptsURL(url) ? connection() : null; }
        public boolean acceptsURL(String url) { return url.startsWith("jdbc:review-kern:"); }
        public DriverPropertyInfo[] getPropertyInfo(String u,Properties p) { return new DriverPropertyInfo[0]; }
        public int getMajorVersion(){return 1;} public int getMinorVersion(){return 0;}
        public boolean jdbcCompliant(){return false;}
        public Logger getParentLogger(){return Logger.getLogger("review-kern");}
    }
    static final class JournalMock implements TxSagaJournal {
        SagaState state = SagaState.RUNNING;
        public void start(String id,String type,String payload,int count){}
        public void markStepInFlight(String id,int i){}
        public void stepDone(Connection c,String id,int i,String n){}
        public void stepCompensated(Connection c,String id,int i){}
        public void markState(String id,SagaState value){state=value;}
        public boolean isStepCompensated(String id,int i){return false;}
        public List<UnfinishedSaga> unfinished(){return List.of(snapshot());}
        UnfinishedSaga snapshot() {
            var saga = new UnfinishedSaga("review-pending","REVIEW","{}",state);
            saga.putStepState(0,UnfinishedSaga.STEP_PENDING);
            return saga;
        }
        public SagaClaim claimUnfinished(String id,String type) {
            return new SagaClaim() {
                public UnfinishedSaga saga(){return snapshot();}
                public void checkAlive(){}
                public void finish(SagaState value){state=value;}
                public void close(){}
            };
        }
    }
    public static void main(String[] args) throws Exception {
        System.out.println("runtime="+Runtime.version());
        var oracleMapping=Class.forName("oracle.jdbc.driver.DatabaseError")
            .getMethod("ErrorToSQLState",int.class);
        for(int code:new int[]{54,60,8177,18,4031,1,4020,20}) {
            String state=(String)oracleMapping.invoke(null,code);
            System.out.println("RETRY: ORA-"+code+"; actualDriverSQLState="+state+
                "; projectRetry="+RetryPolicy.isRetryable(new SQLException("probe",state,code)));
        }
        Driver driver = new DriverMock();
        DriverManager.registerDriver(driver);
        try {
            try(var pool=new SimpleConnectionPool("jdbc:review-kern:retry",null,null,1)) {
                AtomicInteger attempts=new AtomicInteger();
                String actualState=(String)oracleMapping.invoke(null,4031);
                try {
                    new TxManager(pool,new RetryPolicy(1)).execute(TxOptions.DEFAULT,ctx->{
                        attempts.incrementAndGet();
                        throw new SQLException("simulierter Shared-Pool-Mangel",actualState,4031);
                    });
                } catch(TxException expected) {
                    System.out.println("RETRY-EXECUTE: ORA-4031; attempts="+attempts+"; retryable="+expected.isRetryable());
                    if(attempts.get()!=3) throw new AssertionError("Retry-Probe unerwartet");
                }
            }
            closed=false; auto=true;
            JournalMock journal = new JournalMock();
            try (var pool=new SimpleConnectionPool("jdbc:review-kern:pending",null,null,1)) {
                SagaEngine engine = new SagaEngine(new TxManager(pool),journal);
                SagaDefinition definition=SagaDefinition.of("REVIEW")
                    .step("niemals begonnen",(c,p,id)->{},(c,p,id)->{}).build();
                var first=engine.recoverWithReport(definition);
                var second=engine.recoverWithReport(definition);
                System.out.println("PENDING: first="+first+"; second="+second+"; state="+journal.state);
                if(first.failed()!=1 || second.failed()!=1 || journal.state!=SagaState.FAILED)
                    throw new AssertionError("Pending-Probe unerwartet");
            }
            closed=false; auto=true;
            try (var pool=new SimpleConnectionPool("jdbc:review-kern:aba",null,null,1)) {
                TxManager manager=new TxManager(pool);
                TxContext first=manager.begin(TxOptions.DEFAULT);
                Connection firstRaw=first.connection();
                manager.finish(first);
                TxContext second=manager.begin(TxOptions.DEFAULT);
                calls.clear();
                manager.finish(first);
                boolean stolen=second.connection().getAutoCommit();
                Connection third=pool.getConnection();
                System.out.println("ABA: samePhysical="+(firstRaw==second.connection())+
                    "; activeSecondAutoCommit="+stolen+"; thirdIsActiveSecond="+(third==second.connection())+
                    "; staleFinishCalls="+calls);
                if(!stolen || third!=second.connection()) throw new AssertionError("ABA-Probe unerwartet");
                pool.release(third);
                manager.finish(second);
            }
            closed=false; auto=true; calls.clear();
            try(var pool=new SimpleConnectionPool("jdbc:review-kern:cancel",null,null,1);
                var tasks=new TaskController(new TxManager(pool),1,2)) {
                CountDownLatch entered=new CountDownLatch(1), continued=new CountDownLatch(1);
                var task=tasks.submit("review-cancel",TxOptions.DEFAULT,ctx->{
                    entered.countDown();
                    try{Thread.sleep(10000);}catch(InterruptedException ignored){}
                    continued.countDown(); return "work-finished";
                });
                if(!entered.await(3,TimeUnit.SECONDS)) throw new AssertionError("Start fehlte");
                boolean cancelled=task.cancel();
                continued.await(3,TimeUnit.SECONDS);
                tasks.close();
                System.out.println("CANCEL: cancelled="+cancelled+"; done="+task.isDone()+
                    "; calls="+calls+"; stats="+tasks.stats());
                if(!calls.contains("commit")) throw new AssertionError("Cancel-Probe unerwartet");
            }
        } finally { DriverManager.deregisterDriver(driver); }
    }
}
