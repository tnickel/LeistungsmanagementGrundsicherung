package de.allgemein.testsweet;

import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Simuliert gezielt das historische Demo-Schema ohne SAGA_ID, keine Produktrechnung. */
class SagaFixtureSchemaRegressionTest {
    @Test
    void journalCleanupBeruehrtDemoOhneRegistrierteFachwirkungNicht() throws Exception {
        var legacy=new LegacySchema();
        String id=UUID.randomUUID().toString(),type="SWEET_FIXTURE_"+id;
        ProduktSagaJournalRegressionDbTest.cleanupOwnRows(legacy.connection(),id,type,null);
        assertEquals(2,legacy.executed.size());
        for(var call:legacy.executed) {
            assertFalse(call.sql().contains("TX_DEMO_ANTRAG"));
            assertEquals(List.of(id,type),call.parameters(),"Journalcleanup bleibt auf ID und Typ begrenzt");
        }
    }

    @Test
    void eigeneDemoFixtureFunktioniertOhneSagaSpalteUndLaesstFremdeNamenUnberuehrt() throws Exception {
        var legacy=new LegacySchema();
        String id=UUID.randomUUID().toString(),type="SWEET_FIXTURE_"+id;
        String ownName="SWEET-NAME-GUARD-"+id,foreign="FREMDER-DEMO-BESTAND";
        legacy.effects.put(foreign,1);
        Connection connection=legacy.connection();
        ProduktSagaJournalRegressionDbTest.insertOwnEffect(connection,ownName);
        assertEquals(1,ProduktSagaJournalRegressionDbTest.countOwnEffects(connection,ownName));
        ProduktSagaJournalRegressionDbTest.cleanupOwnRows(connection,id,type,ownName);
        assertEquals(0,ProduktSagaJournalRegressionDbTest.countOwnEffects(connection,ownName));
        assertEquals(Map.of(foreign,1),legacy.effects,"Cleanup löscht nur den zuvor registrierten UUID-Namen");
        for(var call:legacy.executed) {
            if(call.sql().contains("TX_DEMO_ANTRAG")) {
                assertFalse(call.sql().contains("SAGA_ID"),"historisches Demo-Schema hat diese Spalte nicht");
                assertEquals(List.of(ownName),call.parameters());
                if(call.sql().startsWith("DELETE")) assertTrue(call.sql().contains("WHERE NAME = ?"));
            }
        }
    }

    @Test
    void kompatibilitaetVerschlucktKeineEchtenCleanupFehler() {
        var legacy=new LegacySchema();
        legacy.failJournal=true;
        SQLException error=assertThrows(SQLException.class,()->ProduktSagaJournalRegressionDbTest.cleanupOwnRows(
                legacy.connection(),UUID.randomUUID().toString(),"SWEET_FIXTURE",null));
        assertEquals(942,error.getErrorCode());
        assertTrue(legacy.executed.isEmpty(),"Nach einem unbekannten Cleanupfehler nicht still weitermachen");
    }

    private record Call(String sql,List<String> parameters) { }
    private static final class LegacySchema {
        final Map<String,Integer> effects=new HashMap<>();
        final List<Call> executed=new ArrayList<>();
        boolean failJournal;
        Connection connection() {
            return (Connection)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{Connection.class},
                    (p,m,a)->switch(m.getName()) {
                        case "prepareStatement"->statement((String)a[0]);
                        case "close","commit","rollback","setAutoCommit"->null;
                        default->throw new UnsupportedOperationException(m.getName());
                    });
        }
        private PreparedStatement statement(String sql) throws SQLException {
            if(sql.contains("TX_DEMO_ANTRAG") && sql.contains("SAGA_ID"))
                throw new SQLException("ORA-00904: SAGA_ID invalid identifier","42000",904);
            if(failJournal && sql.contains("TX_SAGA_STEP"))
                throw new SQLException("ORA-00942: synthetischer anderer Cleanupfehler","42000",942);
            var values=new ArrayList<String>(Collections.nCopies((int)sql.chars().filter(c->c=='?').count(),null));
            return (PreparedStatement)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{PreparedStatement.class},
                    (p,m,a)->switch(m.getName()) {
                        case "setString"->{values.set((Integer)a[0]-1,(String)a[1]);yield null;}
                        case "executeUpdate"->{
                            executed.add(new Call(sql,List.copyOf(values)));
                            if(sql.startsWith("INSERT INTO TX_DEMO_ANTRAG")) effects.merge(values.getFirst(),1,Integer::sum);
                            else if(sql.startsWith("DELETE FROM TX_DEMO_ANTRAG")) effects.remove(values.getFirst());
                            yield 1;
                        }
                        case "executeQuery"->{
                            executed.add(new Call(sql,List.copyOf(values)));
                            if(!sql.startsWith("SELECT COUNT(*) FROM TX_DEMO_ANTRAG WHERE NAME = ?"))
                                throw new UnsupportedOperationException(sql);
                            yield countRows(effects.getOrDefault(values.getFirst(),0));
                        }
                        case "close"->null;
                        default->throw new UnsupportedOperationException(m.getName());
                    });
        }
        private ResultSet countRows(int count) {
            var first=new java.util.concurrent.atomic.AtomicBoolean(true);
            return (ResultSet)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{ResultSet.class},
                    (p,m,a)->switch(m.getName()) {
                        case "next"->first.getAndSet(false);
                        case "getInt"->count;
                        case "close"->null;
                        default->throw new UnsupportedOperationException(m.getName());
                    });
        }
    }
}
