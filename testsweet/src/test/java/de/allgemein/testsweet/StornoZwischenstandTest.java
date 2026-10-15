package de.allgemein.testsweet;

import de.allgemein.tx.ConnectionPool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class StornoZwischenstandTest {
    @TempDir Path temp;

    @Test void generatorPrueftStornoZwischenstandVorMietekorrekturMitEchtenTabellenUndVorabSollwerten() {
        int checked = 0;
        for (Auftrag order : new SzenarioBibliothek(42).erzeuge(500)) {
            for (int i = 0; i < order.aktionen.size(); i++) {
                if (!order.aktionen.get(i).name().equals("Anordnung sofort stornieren (Status ANGELEGT)")) continue;
                checked++;
                var afterCancel = order.aktionen.get(i + 1);
                assertEquals("Persistierten Zwischenstand nach Storno prüfen", afterCancel.name());
                assertEquals(2, afterCancel.zwischenpruefungen().size());
                assertTrue(order.aktionen.get(i + 2).name().startsWith("Miete auf "));
                var block = order.rechenbloecke.getFirst();
                var assets = afterCancel.zwischenpruefungen().get(0);
                var budget = afterCancel.zwischenpruefungen().get(1);
                assertEquals("SELECT VERMOEGEN FROM BEDARFSGEMEINSCHAFT WHERE ID = ?", assets.sql());
                assertEquals("SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?", budget.sql());
                assertEquals(0, block.vermoegen().compareTo(new BigDecimal(assets.erwartet())));
                assertEquals(0, block.budget().compareTo(new BigDecimal(budget.erwartet())));
                assertEquals(List.of(123L), assets.parameter().apply(Map.of("bgId",123L)));
                assertEquals(List.of(123L), budget.parameter().apply(Map.of("bgId",123L)));
                assertFalse(order.erwartungen.stream().anyMatch(e -> e.beschreibung().contains("unmittelbar nach Storno")),
                        "Zwischenstand muss im passenden Arbeitsschritt statt erst nach Neuauszahlung geprüft werden");
            }
        }
        assertTrue(checked > 0);
    }

    @Test void zweiEchteSqlLesungenTeilenEinenSnapshotUndProtokollierenIdSollUndIst() throws Exception {
        Database database = new Database(false, "10500.00", "2000.00");
        Simulator simulator = simulator(database);
        Auftrag order = order(List.of());
        simulator.pruefeZwischenstand(order, expectations(), Map.of("bgId", 123L));
        assertEquals(2, order.pruefungen().size());
        assertTrue(order.pruefungen().stream().allMatch(Auftrag.Pruefung::ok));
        assertTrue(order.pruefungen().stream().allMatch(p -> p.parameter().equals(List.of(123L))));
        assertEquals("10500.00", order.pruefungen().get(0).ist());
        assertEquals("2000.00", order.pruefungen().get(1).ist());
        assertEquals(1, database.readOnlyTransactions);
        assertEquals(1, database.borrows);
        assertEquals(1, database.releases);
        assertEquals(1, database.rollbacks);
        assertEquals(List.of(123L, 123L), database.parameters);
    }

    @Test void falscherPersistierterWertOderSqlFehlerVerhindertJedeWeitereFachmutation() throws Exception {
        for (boolean sqlError : List.of(false, true)) {
            Database database = new Database(sqlError, "10500.00", "1999.99");
            Simulator simulator = simulator(database);
            AtomicInteger mutations = new AtomicInteger();
            Auftrag order = order(List.of(
                    SzenarioAktion.schritt("Bereits committeter Storno", (s, own, other, ctx) -> { ctx.put("bgId",123L); return null; }),
                    SzenarioAktion.pruefung("Zwischenstand", expectations()),
                    SzenarioAktion.schritt("Weitere Mutation", (s, own, other, ctx) -> { mutations.incrementAndGet(); return null; })));
            try (SweetLog log = new SweetLog(temp.resolve(Boolean.toString(sqlError)))) {
                set(simulator,"log",log);
                var method = Mitarbeiter.class.getDeclaredMethod("spiele",Auftrag.class);
                method.setAccessible(true);
                method.invoke(new Mitarbeiter(simulator,"sb-test",0),order);
            }
            assertEquals(0, mutations.get());
            assertEquals(Auftrag.Status.FEHLGESCHLAGEN,order.status);
            assertTrue(order.pruefungen().stream().anyMatch(p -> !p.ok()));
            assertEquals(1,database.releases);
            assertEquals(1,database.rollbacks);
            if (sqlError) assertTrue(order.pruefungen().stream().anyMatch(p -> "<SQL-Fehler>".equals(p.ist())));
        }
    }

    private Simulator simulator(Database database) throws Exception {
        Simulator simulator = new Simulator();
        // Der gemeinsame Prüfpfad akzeptiert das ConnectionPool-Interface über webPool.
        set(simulator,"webPool",new ConnectionPool() {
            public Connection getConnection() { database.borrows++; return database.connection; }
            public void release(Connection connection) { assertSame(database.connection,connection); database.releases++; }
            public void close() { fail("Kein Schließen des Prüfverbindungspools"); }
        });
        return simulator;
    }
    private static Auftrag order(List<SzenarioAktion> actions) {
        return new Auftrag(1,"TEST","SWEET-STORNO-READ","","",actions,List.of());
    }
    private static List<Erwartung> expectations() {
        return List.of(
                Erwartung.betrag("Vermögen nach Storno","SELECT VERMOEGEN FROM BEDARFSGEMEINSCHAFT WHERE ID = ?", c -> List.of(c.get("bgId")),"10500.00"),
                Erwartung.betrag("Budget nach Storno","SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?", c -> List.of(c.get("bgId")),"2000.00"));
    }
    private static void set(Object target,String field,Object value) throws Exception {
        var declared = target.getClass().getDeclaredField(field); declared.setAccessible(true); declared.set(target,value);
    }

    private static final class Database {
        final boolean sqlError;
        final String assets, budget;
        int readOnlyTransactions, borrows, releases, rollbacks;
        final List<Object> parameters = new ArrayList<>();
        final Connection connection;
        Database(boolean sqlError,String assets,String budget) {
            this.sqlError=sqlError; this.assets=assets; this.budget=budget;
            connection=proxy(Connection.class,(object,method,args) -> switch(method.getName()) {
                case "getAutoCommit" -> true;
                case "setAutoCommit" -> null;
                case "rollback" -> { rollbacks++; yield null; }
                case "createStatement" -> proxy(Statement.class,(s,m,a) -> switch(m.getName()) {
                    case "execute" -> { assertEquals("SET TRANSACTION READ ONLY",a[0]); readOnlyTransactions++; yield true; }
                    case "close" -> null;
                    default -> throw new UnsupportedOperationException(m.getName());
                });
                case "prepareStatement" -> {
                    if (sqlError) throw new SQLException("CONTROLLED_INTERMEDIATE_READ_ERROR");
                    String sql=(String)args[0];
                    assertTrue(sql.matches("SELECT (VERMOEGEN|BUDGET_REST) FROM BEDARFSGEMEINSCHAFT WHERE ID = \\?"));
                    yield proxy(PreparedStatement.class,(s,m,a) -> switch(m.getName()) {
                        case "setObject" -> { assertEquals(1,a[0]); parameters.add(a[1]); yield null; }
                        case "executeQuery" -> {
                            AtomicInteger row = new AtomicInteger();
                            yield proxy(ResultSet.class,(r,rm,ra) -> switch(rm.getName()) {
                                case "next" -> row.incrementAndGet()==1;
                                case "getObject" -> sql.contains("VERMOEGEN") ? assets : budget;
                                case "close" -> null;
                                default -> throw new UnsupportedOperationException(rm.getName());
                            });
                        }
                        case "close" -> null;
                        default -> throw new UnsupportedOperationException(m.getName());
                    });
                }
                default -> throw new UnsupportedOperationException(method.getName());
            });
        }
    }
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }
}
