package de.allgemein.lmg.db;

import de.allgemein.tx.*;
import de.allgemein.tx.saga.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.nio.file.*;
import java.sql.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

/** Echter Kind-JVM-Kill; laufisolierte Kennungen und Prüfung ausschließlich der eigenen Saga. */
@EnabledIfEnvironmentVariable(named="TX_URL",matches=".+")
class ProzessKillRecoveryDbTest {
    @Test
    void zweiGleichzeitigeKillSagasBleibenBeiRecoveryUndCleanupGetrennt() throws Exception {
        try(Fixture first=new Fixture();Fixture second=new Fixture()) {
            first.startChild();second.startChild();
            first.awaitOwnInFlight();second.awaitOwnInFlight();
            assertNotEquals(first.sagaId,second.sagaId);assertNotEquals(first.bgId,second.bgId);
            first.stopChild();first.recoverUntilOwnTerminal();first.assertBgStatus(0);
            assertTrue(second.child.isAlive(),"Recovery darf den anderen eigenen Testprozess nicht abbrechen");
            second.assertBgStatus(1);assertEquals("RUNNING",second.headState());
            first.close();
            second.assertBgStatus(1);assertEquals("RUNNING",second.headState());
            second.stopChild();second.recoverUntilOwnTerminal();second.assertBgStatus(0);
        }
    }

    @Test
    void harterProzessKillHinterlaesstKompensierbarenZustand() throws Exception {
        try(Fixture own=new Fixture()) {
            own.startChild();
            own.awaitOwnInFlight();
            own.assertBgStatus(1);
            own.stopChild();
            own.recoverUntilOwnTerminal();
            own.assertBgStatus(0);
            assertEquals("COMPENSATED",own.headState());
        }
    }

    private static Connection connect() throws SQLException {
        var properties=new Properties();
        properties.setProperty("user",System.getenv("TX_USER"));properties.setProperty("password",System.getenv("TX_PASS"));
        properties.setProperty("oracle.net.CONNECT_TIMEOUT","5000");properties.setProperty("oracle.jdbc.ReadTimeout","10000");
        return DriverManager.getConnection(System.getenv("TX_URL"),properties);
    }
    private static final class Fixture implements AutoCloseable {
        final String marker=UUID.randomUUID().toString();
        final String aktenzeichen="BG-KILL-"+marker,type="KILL_TEST_"+marker;
        final SimpleConnectionPool pool=new SimpleConnectionPool(System.getenv("TX_URL"),System.getenv("TX_USER"),
                System.getenv("TX_PASS"),4,Duration.ofSeconds(5),5000,10000);
        Process child;
        Path output;
        String sagaId;
        Long bgId;
        boolean closed;
        void startChild() throws Exception {
            Files.createDirectories(Path.of("target"));
            output=Path.of("target","kill-recovery-"+marker+".log").toAbsolutePath();
            String java=Path.of(System.getProperty("java.home"),"bin",
                    System.getProperty("os.name","").toLowerCase(Locale.ROOT).contains("win")?"java.exe":"java").toString();
            // Zugangsdaten bleiben in der geerbten Umgebung, nie in der Prozessliste.
            child=new ProcessBuilder(java,"-cp",System.getProperty("java.class.path"),ProzessKillHelfer.class.getName(),
                    aktenzeichen,type).redirectErrorStream(true).redirectOutput(output.toFile()).start();
        }
        void awaitOwnInFlight() throws Exception {
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(30);
            while(System.nanoTime()<deadline) {
                try(var c=connect();var st=c.prepareStatement(
                        "SELECT s.SAGA_ID,b.ID FROM TX_SAGA s JOIN TX_SAGA_STEP x ON x.SAGA_ID = s.SAGA_ID "
                                +"JOIN BEDARFSGEMEINSCHAFT b ON b.AKTENZEICHEN = ? "
                                +"WHERE s.SAGA_TYPE = ? AND s.PAYLOAD = ? AND x.IDX = 1 AND x.STATE = 'IN_FLIGHT' "
                                +"AND EXISTS (SELECT 1 FROM TX_SAGA_STEP done WHERE done.SAGA_ID = s.SAGA_ID AND done.IDX = 0 AND done.STATE = 'DONE')")) {
                    st.setString(1,aktenzeichen);st.setString(2,type);st.setString(3,aktenzeichen);
                    try(var rows=st.executeQuery()) {
                        if(rows.next()) {
                            sagaId=rows.getString(1);bgId=rows.getLong(2);assertFalse(rows.next());return;
                        }
                    }
                }
                assertTrue(child.isAlive(),"Kind vor eigenem IN_FLIGHT beendet; Ausgabe: "+output);
                Thread.sleep(100);
            }
            fail("Eigener zweiter IN_FLIGHT-Schritt nicht rechtzeitig erreicht; Ausgabe: "+output);
        }
        void stopChild() throws Exception {
            if(child!=null && child.isAlive()) {
                child.destroyForcibly();
                assertTrue(child.waitFor(10,TimeUnit.SECONDS),"eigener Kindprozess endet nicht; Ausgabe: "+output);
            }
        }
        void recoverUntilOwnTerminal() throws Exception {
            assertNotNull(sagaId,"Recovery braucht die zuvor registrierte eigene Saga-ID");
            var engine=new SagaEngine(new TxManager(pool),new SagaJournal(pool));
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(30);
            do {
                // Der UUID-Typ enthält nur die eigene Saga. SKIP LOCKED kann die
                // alte Session nach dem Prozessende kurz noch als lebend sehen.
                var report=engine.recoverWithReport(ProzessKillHelfer.definition(type));
                assertEquals(0,report.fehlerhaft(),"technischen Recoveryfehler nicht als Session-Wartefenster wiederholen");
                String state=headState();
                if("COMPENSATED".equals(state)) return;
                assertEquals("RUNNING",state,"unerwarteter Zustand der eigenen Saga");
                Thread.sleep(100);
            } while(System.nanoTime()<deadline);
            fail("Eigene Saga nach Kind-Kill nicht terminal; ID="+sagaId+", Ausgabe: "+output);
        }
        String headState() throws SQLException {
            try(var c=connect();var st=c.prepareStatement("SELECT STATE FROM TX_SAGA WHERE SAGA_ID = ? AND SAGA_TYPE = ?")) {
                st.setString(1,sagaId);st.setString(2,type);
                try(var rows=st.executeQuery()) {assertTrue(rows.next(),"eigener Saga-Kopf fehlt");return rows.getString(1);}
            }
        }
        void assertBgStatus(int status) throws SQLException {
            assertNotNull(bgId);
            try(var c=connect();var st=c.prepareStatement("SELECT STATUS_AKTIV FROM BEDARFSGEMEINSCHAFT WHERE ID = ? AND AKTENZEICHEN = ?")) {
                st.setLong(1,bgId);st.setString(2,aktenzeichen);
                try(var rows=st.executeQuery()) {assertTrue(rows.next(),"eigener Vorgang fehlt");assertEquals(status,rows.getInt(1));}
            }
        }
        private void registerCreatedRows(Connection c) throws SQLException {
            if(sagaId==null) try(var st=c.prepareStatement("SELECT SAGA_ID FROM TX_SAGA WHERE SAGA_TYPE = ? AND PAYLOAD = ?")) {
                st.setString(1,type);st.setString(2,aktenzeichen);
                try(var rows=st.executeQuery()) {if(rows.next()) {sagaId=rows.getString(1);assertFalse(rows.next());}}
            }
            if(bgId==null) try(var st=c.prepareStatement("SELECT ID FROM BEDARFSGEMEINSCHAFT WHERE AKTENZEICHEN = ? AND HAUPTPERSON_NAME = 'Kill Test'")) {
                st.setString(1,aktenzeichen);try(var rows=st.executeQuery()) {if(rows.next()) {bgId=rows.getLong(1);assertFalse(rows.next());}}
            }
        }
        public void close() throws Exception {
            if(closed)return;
            try {
                stopChild();
                try(var c=connect()) {
                    c.setAutoCommit(false);registerCreatedRows(c);
                    if(sagaId!=null) {
                        for(String sql:List.of("DELETE FROM TX_SAGA_STEP WHERE SAGA_ID = ? AND EXISTS (SELECT 1 FROM TX_SAGA s WHERE s.SAGA_ID = TX_SAGA_STEP.SAGA_ID AND s.SAGA_TYPE = ?)",
                                "DELETE FROM TX_SAGA WHERE SAGA_ID = ? AND SAGA_TYPE = ?")) {
                            try(var st=c.prepareStatement(sql)) {st.setQueryTimeout(10);st.setString(1,sagaId);st.setString(2,type);st.executeUpdate();}
                        }
                    }
                    if(bgId!=null) try(var st=c.prepareStatement("DELETE FROM BEDARFSGEMEINSCHAFT WHERE ID = ? AND AKTENZEICHEN = ?")) {
                        st.setQueryTimeout(10);st.setLong(1,bgId);st.setString(2,aktenzeichen);st.executeUpdate();
                    }
                    c.commit();
                }
                closed=true;
            } finally {pool.close();}
        }
    }
}
