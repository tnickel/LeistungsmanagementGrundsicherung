package de.allgemein.testsweet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class GlobaleVerifikationTest {
    @TempDir Path temp;
    private final RunDataScope scope = RunDataScope.create();

    @Test void fehlendeFreigabeMachtAuftragLaufUndMarkdownRot() throws Exception {
        AtomicInteger nummer = new AtomicInteger();
        List<Auftrag.Pruefung> pruefungen = GlobaleVerifikation.pruefe(
                () -> countConnection(nummer.getAndIncrement() == 0 ? 1 : 2), 1, List.of(), scope);
        assertTrue(pruefungen.get(0).ok());
        assertFalse(pruefungen.get(1).ok());
        assertEquals("2", pruefungen.get(1).ist());
        try (SweetLog log = new SweetLog(temp.resolve("logs"))) {
            Simulator simulator = new Simulator();
            Auftrag auftrag = new Auftrag(1, "TEST", "SWEET-1", "Test", "", List.of(), List.of());
            auftrag.status = Auftrag.Status.VERIFIZIERT;
            setze(simulator, "auftraege", List.of(auftrag));
            setze(simulator, "log", log);
            simulator.uebernehmeGlobalePruefungen(pruefungen);
            assertEquals(Auftrag.Status.VERIFIKATION_FEHLGESCHLAGEN, auftrag.status);
            assertEquals(Simulator.Phase.FEHLGESCHLAGEN, simulator.phase());
            assertFalse(simulator.laufErfolgreich());
            assertNotNull(simulator.letzteFehlermeldung());
            Path md = temp.resolve("report.md");
            MdBericht.schreibe(md, simulator);
            String text = Files.readString(md);
            assertTrue(text.contains("lauf_erfolgreich = false"));
            assertTrue(text.contains("ergebnis.verifikation_nio = 1"));
            assertTrue(text.contains("keine Übergabe ohne Freigabevermerk"));
        }
    }

    @Test void sqlAusfallIstEineFehlgeschlagenePruefungAuchOhneAuftraege() {
        var pruefungen = GlobaleVerifikation.pruefe(() -> { throw new SQLException("DB offline"); }, 0, List.of(), scope);
        assertEquals(2, pruefungen.size());
        assertTrue(pruefungen.stream().noneMatch(Auftrag.Pruefung::ok));
        assertTrue(pruefungen.stream().allMatch(p -> p.hinweis().contains("DB offline")));
        Simulator simulator = new Simulator();
        simulator.uebernehmeGlobalePruefungen(pruefungen);
        assertEquals(Simulator.Phase.FEHLGESCHLAGEN, simulator.phase());
        assertFalse(simulator.laufErfolgreich());
    }

    @Test void fehlerfreierBestandLaesstAlleGlobalenPruefungenBestehen() {
        var pruefungen = GlobaleVerifikation.pruefe(() -> countConnection(0), 0, List.of(), scope);
        assertTrue(pruefungen.stream().allMatch(Auftrag.Pruefung::ok));
    }

    private static void setze(Object ziel, String feld, Object wert) throws Exception {
        var f = ziel.getClass().getDeclaredField(feld);
        f.setAccessible(true);
        f.set(ziel, wert);
    }

    private static Connection countConnection(long anzahl) {
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
                (p, m, a) -> switch (m.getName()) {
                    case "prepareStatement" -> Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(),
                            new Class<?>[]{PreparedStatement.class}, (sp, sm, sa) -> switch (sm.getName()) {
                                case "executeQuery" -> {
                                    AtomicInteger zeile = new AtomicInteger();
                                    yield Proxy.newProxyInstance(ResultSet.class.getClassLoader(), new Class<?>[]{ResultSet.class},
                                            (rp, rm, ra) -> switch (rm.getName()) {
                                                case "next" -> zeile.getAndIncrement() == 0;
                                                case "getLong" -> anzahl;
                                                case "wasNull" -> false;
                                                case "close" -> null;
                                                default -> throw new UnsupportedOperationException(rm.getName());
                                            });
                                }
                                case "close", "setString" -> null;
                                default -> throw new UnsupportedOperationException(sm.getName());
                            });
                    case "close" -> null;
                    default -> throw new UnsupportedOperationException(m.getName());
                });
    }
}
