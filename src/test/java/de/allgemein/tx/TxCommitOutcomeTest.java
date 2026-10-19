package de.allgemein.tx;

import de.allgemein.tx.audit.TxAuditEvent;
import de.allgemein.tx.audit.TxAuditListener;
import de.allgemein.tx.audit.TxAuditLog;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLRecoverableException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Klassifikation der Commitphase; echte persistierte Wirkungen prüft TxCommitOutcomeDbTest. */
class TxCommitOutcomeTest {
    @Test
    void verloreneCommitQuittungWirdAuchBeiRetryVendorcodeNieWiederholtOderAlsRollbackAuditiert() {
        for(Throwable cause:List.of(new SQLRecoverableException("synthetisch verlorene Quittung","08006",17002),
                new SQLException("synthetischer Commitfehler","61000",60),new IllegalStateException("Treiberfehler"))) {
            try(var pool=new CommitPool(()->{throw cause;})) {
                AtomicInteger work=new AtomicInteger();
                List<TxAuditEvent> events=new ArrayList<>();
                Thread caller=Thread.currentThread();
                TxAuditListener listener=event->{if(Thread.currentThread()==caller)events.add(event);};
                TxAuditLog.addListener(listener);
                try {
                    var error=assertThrows(TxCommitOutcomeUnknownException.class,
                            ()->new TxManager(pool).execute(TxOptions.DEFAULT,ctx->work.incrementAndGet()));
                    assertSame(cause,error.getCause());assertFalse(error.isRetryable());
                    assertEquals(cause instanceof SQLException sql?sql.getErrorCode():-1,error.oracleErrorCode());
                    assertEquals(1,work.get());assertEquals(1,pool.delegate.commits.get());
                    assertEquals(1,pool.delegate.releases.get());
                    assertEquals(List.of("OUTCOME_UNKNOWN"),events.stream().map(TxAuditEvent::eventType).toList());
                    assertFalse(events.getFirst().error().contains(cause.getMessage()),"Audit enthält keine Treibermeldung");
                } finally {TxAuditLog.removeListener(listener);}
            }
        }
    }

    @Test
    void manuellerUnbekannterCommitBleibtAuchNachRollbackCloseUndFinishUnbekannt() throws Exception {
        try(var pool=new CommitPool(()->{throw new SQLRecoverableException("Quittung verloren","08006",17002);})) {
            var tx=new TxManager(pool);var ctx=tx.begin(TxOptions.DEFAULT);
            try {
                assertThrows(TxCommitOutcomeUnknownException.class,()->tx.commit(ctx));
                assertEquals(TxStatus.COMMIT_OUTCOME_UNKNOWN,ctx.status());
                assertThrows(IllegalStateException.class,ctx::connection,"keine weitere Facharbeit auf unklarem Kontext");
                tx.rollback(ctx);assertEquals(TxStatus.COMMIT_OUTCOME_UNKNOWN,ctx.status());
                ctx.close();assertEquals(TxStatus.COMMIT_OUTCOME_UNKNOWN,ctx.status());
            } finally {tx.finish(ctx);}
            assertEquals(TxStatus.COMMIT_OUTCOME_UNKNOWN,ctx.status());
            assertEquals(1,pool.delegate.commits.get());assertEquals(1,pool.delegate.releases.get());
            assertTrue(pool.delegate.rollbacks.get()>0,"best effort Aufräumen ist erlaubt, kein Rollbackbeleg");
        }
    }

    @Test
    void taskErgebnisUndBegrenztesWartenUebersetzenCommitfehlerInUnbekanntenAusgang() {
        for(boolean bounded:List.of(false,true)) {
            try(var pool=new CommitPool(()->{throw new SQLRecoverableException("Quittung verloren","08006",17002);});
                var tasks=new TaskController(new TxManager(pool),1,1)) {
                var handle=tasks.submit("commit-quittung",TxOptions.DEFAULT,ctx->"Fachwirkung");
                var error=assertThrows(TaskOutcomeUnknownException.class,
                        ()->{if(bounded)handle.resultOrCancelAfter(Duration.ofSeconds(2));else handle.result();});
                assertInstanceOf(TxCommitOutcomeUnknownException.class,error.getCause());
                assertFalse(error.isRetryable());assertTrue(error.getMessage().contains("Ausgang unbekannt"));
                assertTrue(error.getMessage().contains("nicht erneut anordnen"));
                assertEquals(1,pool.delegate.commits.get());
            }
        }
    }

    @Test
    void interruptNachBestaetigtemJdbcCommitDarfDenErfolgNichtInRollbackUmdeuten() {
        try(var pool=new CommitPool(()->Thread.currentThread().interrupt())) {
            try {
                assertEquals("bestaetigt",new TxManager(pool).execute(TxOptions.DEFAULT,ctx->"bestaetigt"));
                assertTrue(Thread.currentThread().isInterrupted());
                assertEquals(1,pool.delegate.commits.get());assertEquals(0,pool.delegate.rollbacks.get());
            } finally {Thread.interrupted();}
        }
    }

    @Test
    void errorWaehrendCommitBleibtErrorUndWirdNichtAlsRollbackBelegt() {
        AssertionError cause=new AssertionError("synthetischer Treiber-Error");
        List<TxAuditEvent> events=new ArrayList<>();Thread caller=Thread.currentThread();
        TxAuditListener listener=event->{if(Thread.currentThread()==caller)events.add(event);};
        TxAuditLog.addListener(listener);
        try(var pool=new CommitPool(()->{throw cause;})) {
            var error=assertThrows(TxCommitOutcomeUnknownError.class,
                    ()->new TxManager(pool).execute(TxOptions.DEFAULT,ctx->"Fachwirkung"));
            assertSame(cause,error.getCause());
            assertEquals(List.of("OUTCOME_UNKNOWN"),events.stream().map(TxAuditEvent::eventType).toList());
            assertEquals(1,pool.delegate.commits.get());assertEquals(1,pool.delegate.releases.get());
        } finally {TxAuditLog.removeListener(listener);}
    }

    @Test
    void fatalerCommitfehlerErreichtBeideTaskErgebniswegeAlsUnbekannterAusgang() {
        for(boolean bounded:List.of(false,true)) {
            AssertionError cause=new AssertionError("synthetischer Commit-Error");
            try(var pool=new CommitPool(()->{throw cause;});var tasks=new TaskController(new TxManager(pool),1,1)) {
                var handle=tasks.submit("fataler-commit",TxOptions.DEFAULT,ctx->"Fachwirkung");
                var error=assertThrows(TaskOutcomeUnknownException.class,
                        ()->{if(bounded)handle.resultOrCancelAfter(Duration.ofSeconds(2));else handle.result();});
                var marker=assertInstanceOf(TxCommitOutcomeUnknownError.class,error.getCause());
                assertSame(cause,marker.getCause());assertFalse(error.isRetryable());
                assertEquals(1,pool.delegate.commits.get());
            }
        }
    }

    @Test
    void errorVorDemCommitBleibtUnmarkierterErrorUndKeinUnbekannterCommit() {
        AssertionError cause=new AssertionError("synthetischer Fachaktions-Error");
        try(var pool=new CommitPool(()->fail("Commit darf nicht erreicht werden"))) {
            assertSame(cause,assertThrows(AssertionError.class,
                    ()->new TxManager(pool).execute(TxOptions.DEFAULT,ctx->{throw cause;})));
            assertEquals(0,pool.delegate.commits.get());assertEquals(1,pool.delegate.rollbacks.get());
            try(var tasks=new TaskController(new TxManager(pool),1,1)) {
                var handle=tasks.submit("error-vor-commit",TxOptions.DEFAULT,ctx->{throw cause;});
                var taskError=assertThrows(TxException.class,handle::result);
                assertFalse(taskError instanceof TaskOutcomeUnknownException);
                assertSame(cause,taskError.getCause().getCause());
            }
            assertEquals(0,pool.delegate.commits.get());
        }
    }

    @FunctionalInterface private interface AfterCommit {void run() throws Throwable;}
    private static final class CommitPool implements ConnectionPool {
        final FakePool delegate=new FakePool(1);
        final Map<Connection,Connection> rawConnections=new IdentityHashMap<>();
        final AfterCommit afterCommit;
        CommitPool(AfterCommit afterCommit) {this.afterCommit=afterCommit;}
        public synchronized Connection getConnection() throws SQLException {
            Connection raw=delegate.getConnection();
            Connection wrapper=(Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class<?>[]{Connection.class},(p,m,a)->{
                try {
                    Object result=m.invoke(raw,a);
                    if(m.getName().equals("commit"))afterCommit.run();
                    return result;
                } catch(InvocationTargetException e) {throw e.getCause();}
            });
            rawConnections.put(wrapper,raw);return wrapper;
        }
        public synchronized void release(Connection wrapper) {delegate.release(rawConnections.remove(wrapper));}
        public void close() {delegate.close();}
    }
}
