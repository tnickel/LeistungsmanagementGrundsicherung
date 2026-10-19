package de.allgemein.testsweet;

import de.allgemein.lmg.service.SozialLeistungsService;
import de.allgemein.tx.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.lang.reflect.*;
import java.math.BigDecimal;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

/** 1001 eigene BEREIT-Zeilen; echte Oracle-Updates mit gezielt simulierter 19c-Parsergrenze. */
@EnabledIfEnvironmentVariable(named = "TX_URL", matches = ".+")
class KassenMassenlaufDbTest {
    @Test
    void eintausendeinsBereiteZahlungenWerdenVollstaendigUndGenauEinmalUebergeben() throws Exception {
        try (Fixture own = new Fixture()) {
            var limited = new Oracle19cPool(own.pool, false);
            var service = new SozialLeistungsService(new TxManager(limited));
            var result = service.uebergebeBereiteZahlungen(own.actor, own.prefix);
            assertEquals(1001,result.anzahl()); assertEquals(new BigDecimal("10.01"),result.summe());
            assertEquals(List.of(1000,1),limited.executedBlocks,
                    "Die aktuelle Free-Version allein erzwingt die 19c-Parsergrenze nicht");
            own.assertPayments("UEBERGEBEN");
            assertEquals(0,service.uebergebeBereiteZahlungen(own.actor,own.prefix).anzahl());
            assertEquals(List.of(1000,1),limited.executedBlocks,"zweiter Lauf führt kein weiteres Update aus");
        }
    }

    @Test
    void fehlendeZeileImZweitenOracleBlockRolltDieErstenTausendUndDasJournalZurueck() throws Exception {
        try (Fixture own = new Fixture()) {
            var limited = new Oracle19cPool(own.pool, true);
            var service = new SozialLeistungsService(new TxManager(limited));
            TxException error = assertThrows(TxException.class,
                    () -> service.uebergebeBereiteZahlungen(own.actor,own.prefix));
            assertTrue(error.getMessage().contains("gesperrten Menge"),error.getMessage());
            assertEquals(List.of(1000,1),limited.executedBlocks,
                    "erster Block muss tatsächlich ausgeführt sein, bevor der zweite keine Zeile trifft");
            own.assertPayments("BEREIT");
            try (var c=connect(); var st=c.prepareStatement("SELECT COUNT(*) FROM JOURNAL WHERE BG_ID IS NULL AND SACHBEARBEITER_ID = ?")) {
                st.setString(1,own.actor); try(var rs=st.executeQuery()) { assertTrue(rs.next()); assertEquals(0,rs.getInt(1)); }
            }
        }
    }

    /** Echte SQL-Ausführung, nur Parsergrenze und optional die zweite WHERE-Bedingung sind injiziert. */
    private static final class Oracle19cPool implements ConnectionPool {
        final ConnectionPool delegate;
        final boolean emptySecond;
        final AtomicInteger prepared = new AtomicInteger();
        final List<Integer> executedBlocks = new ArrayList<>();
        final Map<Connection,Connection> rawConnections = new IdentityHashMap<>();
        Oracle19cPool(ConnectionPool delegate,boolean emptySecond) { this.delegate=delegate; this.emptySecond=emptySecond; }
        public Connection getConnection() throws SQLException {
            Connection raw=delegate.getConnection();
            Connection wrapped=(Connection) Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{Connection.class},
                    (proxy,method,args) -> {
                        if(method.getName().equals("prepareStatement") && args[0] instanceof String sql
                                && sql.startsWith("UPDATE ZAHLUNG SET ZAHLUNGSSTATUS = 'UEBERGEBEN'")) {
                            int count=(int)sql.chars().filter(c -> c=='?').count();
                            if(count>1000) throw new SQLException("simulierte Oracle-19c-Parsergrenze ORA-01795","42000",1795);
                            int ordinal=prepared.incrementAndGet();
                            String actual=emptySecond && ordinal==2 ? sql+" AND 1 = 0" : sql;
                            PreparedStatement statement=raw.prepareStatement(actual);
                            return Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{PreparedStatement.class},
                                    (p,m,a) -> {
                                        try {
                                            Object result=m.invoke(statement,a);
                                            if(m.getName().equals("executeUpdate")) executedBlocks.add(count);
                                            return result;
                                        } catch(InvocationTargetException e) { throw e.getCause(); }
                                    });
                        }
                        try { return method.invoke(raw,args); }
                        catch(InvocationTargetException e) { throw e.getCause(); }
                    });
            rawConnections.put(wrapped,raw); return wrapped;
        }
        public void release(Connection c) { delegate.release(rawConnections.remove(c)); }
        public void close() { }
    }

    private static final class Fixture implements AutoCloseable {
        final String prefix="SWEET-BLOCK-"+UUID.randomUUID(), actor="sb-block-"+UUID.randomUUID().toString().substring(0,8);
        final SimpleConnectionPool pool;
        final long bgId;
        Fixture() throws Exception {
            pool=new SimpleConnectionPool(System.getenv("TX_URL"),System.getenv("TX_USER"),System.getenv("TX_PASS"),
                    3,Duration.ofSeconds(5),5000,10000);
            var service=new SozialLeistungsService(new TxManager(pool));
            bgId=service.legeBedarfsgemeinschaftAn(prefix,"Synthetischer Blocktest",new BigDecimal("10000"),null,actor).id();
            try (var c=connect();var st=c.prepareStatement(
                    "INSERT INTO ZAHLUNG (ID,BG_ID,VORGANGS_NR,ZAHLUNGSMONAT,AUSZAHLUNGSBETRAG,ZAHLUNGSSTATUS,SACHBEARBEITER_ID,FREIGEGEBEN_VON) "
                            +"VALUES (SEQ_ZAHLUNG.NEXTVAL,?,?,?,0.01,'BEREIT',?,?)")) {
                c.setAutoCommit(false);
                for(int i=0;i<1001;i++) {
                    st.setLong(1,bgId); st.setString(2,prefix+"-"+i);
                    st.setString(3,YearMonth.of(2026,1).plusMonths(i).toString());
                    st.setString(4,actor); st.setString(5,"sb-zweitpruefer"); st.addBatch();
                }
                st.executeBatch();c.commit();
            }
        }
        void assertPayments(String status) throws SQLException {
            try(var c=connect();var st=c.prepareStatement(
                    "SELECT ZAHLUNGSSTATUS,COUNT(*),SUM(AUSZAHLUNGSBETRAG) FROM ZAHLUNG WHERE BG_ID = ? GROUP BY ZAHLUNGSSTATUS")) {
                st.setLong(1,bgId); try(var rs=st.executeQuery()) {
                    assertTrue(rs.next());assertEquals(status,rs.getString(1));assertEquals(1001,rs.getInt(2));
                    assertEquals(0,new BigDecimal("10.01").compareTo(rs.getBigDecimal(3)));assertFalse(rs.next());
                }
            }
        }
        public void close() throws SQLException {
            pool.close();
            try(var c=connect()) {
                c.setAutoCommit(false);
                for(String table:List.of("JOURNAL","ZAHLUNG","BEDARFSGEMEINSCHAFT")) {
                    try(var st=c.prepareStatement("DELETE FROM "+table+" WHERE "+(table.equals("BEDARFSGEMEINSCHAFT")?"ID":"BG_ID")+" = ?")) {
                        st.setLong(1,bgId);st.executeUpdate();
                    }
                }
                try(var st=c.prepareStatement("DELETE FROM JOURNAL WHERE BG_ID IS NULL AND SACHBEARBEITER_ID = ?")) {
                    st.setString(1,actor);st.executeUpdate();
                }
                c.commit();
            }
        }
    }
    private static Connection connect() throws SQLException {
        return DriverManager.getConnection(System.getenv("TX_URL"),System.getenv("TX_USER"),System.getenv("TX_PASS"));
    }
}
