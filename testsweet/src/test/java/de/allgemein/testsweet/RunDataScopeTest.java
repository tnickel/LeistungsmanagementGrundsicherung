package de.allgemein.testsweet;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RunDataScopeTest {
    @Test void gleicherSeedErzeugtJeSimulatorEigenenNamensraumUndErhaeltStressZuordnung() throws Exception {
        Simulator first = new Simulator(), second = new Simulator();
        var generate = Simulator.class.getDeclaredMethod("erzeugeTopf", Simulator.LaufKonfiguration.class, long.class, boolean.class);
        generate.setAccessible(true);
        for (var category : Simulator.Kategorie.values()) {
            var config = new Simulator.LaufKonfiguration(2, 3, 42, 0, false, category);
            @SuppressWarnings("unchecked") List<Auftrag> a = (List<Auftrag>) generate.invoke(first, config, 42, false);
            @SuppressWarnings("unchecked") List<Auftrag> b = (List<Auftrag>) generate.invoke(second, config, 42, false);
            assertTrue(a.stream().allMatch(order -> order.aktenzeichen.startsWith(first.laufDatenPraefix())));
            assertTrue(b.stream().allMatch(order -> order.aktenzeichen.startsWith(second.laufDatenPraefix())));
            assertTrue(a.stream().allMatch(order -> order.aktenzeichen.length() <= 50));
            assertTrue(a.stream().noneMatch(order -> b.stream().anyMatch(other -> other.aktenzeichen.equals(order.aktenzeichen))));
            if (category == Simulator.Kategorie.STRESS) {
                for (int i = 0; i < 3; i++) assertEquals(a.get(i).aktenzeichen, a.get(i + 3).aktenzeichen);
            }
        }
    }

    @Test void bereinigungHatKeineWildcardOderGlobalenJournalfilter() throws Exception {
        RunDataScope scope = RunDataScope.create();
        List<String> queries = new ArrayList<>(), bound = new ArrayList<>();
        Connection connection = (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getAutoCommit" -> false;
                    case "prepareStatement" -> {
                        queries.add((String) args[0]);
                        yield Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(), new Class<?>[]{PreparedStatement.class},
                                (p, m, a) -> switch (m.getName()) {
                                    case "setString" -> { bound.add((String) a[1]); yield null; }
                                    case "executeUpdate" -> 1;
                                    case "close" -> null;
                                    default -> throw new UnsupportedOperationException(m.getName());
                                });
                    }
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        assertEquals(1, scope.delete(connection));
        assertEquals(5, queries.size());
        assertTrue(queries.stream().allMatch(sql -> sql.contains(RunDataScope.OWNED_ROWS)));
        assertTrue(queries.stream().noneMatch(sql -> sql.contains(" LIKE ") || sql.contains("BG_ID IS NULL")));
        assertEquals(10, bound.size());
        assertTrue(bound.stream().allMatch(scope.prefix()::equals));
    }

    @Test void unsicherePraefixeUndAutocommitWerdenVorJederLoeschungAbgewiesen() {
        for (String id : List.of("SWEET-", "%", "0000000000000000000000000000000_", "' OR 1=1"))
            assertThrows(IllegalArgumentException.class, () -> new RunDataScope(id));
        Connection connection = (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    assertEquals("getAutoCommit", method.getName(), "Vor Abweisung darf kein SQL laufen");
                    return true;
                });
        assertThrows(IllegalStateException.class, () -> RunDataScope.create().delete(connection));
    }
}
