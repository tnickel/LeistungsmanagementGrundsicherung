package de.allgemein.testsweet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RunMetricsTest {
    @TempDir Path temp;

    @Test void durchsatzVerwendetMillisekundenStattAbgeschnitteneSekunden() {
        assertEquals(3428.5714, RunMetrics.perMinute(120, 2100), 0.0001);
        assertEquals(9000, RunMetrics.perMinute(120, 800));
        assertEquals(0, RunMetrics.perMinute(0, 0));
        assertThrows(IllegalArgumentException.class, () -> RunMetrics.perMinute(1, -1));
    }

    @Test void perzentileVerwendenNearestRankAuchBeiKleinenStichproben() {
        long[] values = {900, 10, 30, 50, 20};
        assertEquals(30, RunMetrics.percentile(values, 50));
        assertEquals(900, RunMetrics.percentile(values, 95));
        assertEquals(900, RunMetrics.percentile(values, 99));
        assertArrayEquals(new long[]{900, 10, 30, 50, 20}, values);
        assertEquals(0, RunMetrics.percentile(new long[0], 95));
        assertThrows(IllegalArgumentException.class, () -> RunMetrics.percentile(values, 0));
    }

    @Test void echterLastreportZeigtExaktenDurchsatzPerzentileUndKeinenKapazitaetsnachweis() throws Exception {
        Simulator simulator = new Simulator();
        List<Auftrag> orders = new ArrayList<>();
        for (int i = 1; i <= 120; i++) {
            Auftrag order = new Auftrag(i, "LAST", "SWEET-METRIC-" + i, "Test", "", List.of(), List.of());
            order.status = Auftrag.Status.VERIFIZIERT;
            order.startNano = 1;
            order.endeNano = 1 + i * 100_000_000L;
            orders.add(order);
        }
        // Ein noch offener Auftrag darf weder Durchsatz noch Perzentile verbessern.
        orders.add(new Auftrag(121, "LAST", "SWEET-METRIC-OFFEN", "Test", "", List.of(), List.of()));
        set(simulator, "auftraege", orders);
        set(simulator, "laufStartNano", 1L);
        set(simulator, "laufEndeNano", 2_100_000_001L);
        set(simulator, "laufStart", LocalDateTime.of(2026, 10, 15, 12, 0));
        set(simulator, "laufEnde", LocalDateTime.of(2026, 10, 15, 12, 0, 2));
        set(simulator, "konfiguration", new Simulator.LaufKonfiguration(10, 121, 42, 0, false, Simulator.Kategorie.LAST));
        set(simulator, "kategorieLetzter", Simulator.Kategorie.LAST);
        try (SweetLog log = new SweetLog(temp.resolve("logs"))) {
            set(simulator, "log", log);
            Path md = temp.resolve("lastreport.md"), pdf = temp.resolve("lastreport.pdf");
            LastBericht.schreibe(pdf, md, simulator);
            String text = Files.readString(md);
            assertTrue(text.contains("dauer_ms = 2100"), text);
            assertTrue(text.contains("durchsatz_auftraege_pro_min = 3428.6"), text);
            assertTrue(text.contains("abgeschlossen = 120"), text);
            assertTrue(text.contains("dauer_ms_p50 = 6000"), text);
            assertTrue(text.contains("dauer_ms_p95 = 11400"), text);
            assertTrue(text.contains("dauer_ms_p99 = 11900"), text);
            assertTrue(text.contains("capacity_evidence = false"), text);
            assertTrue(text.contains(RunMetrics.SCOPE), text);
            assertTrue(Files.size(pdf) > 0);
        }
    }

    private static void set(Object target, String name, Object value) throws Exception {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
