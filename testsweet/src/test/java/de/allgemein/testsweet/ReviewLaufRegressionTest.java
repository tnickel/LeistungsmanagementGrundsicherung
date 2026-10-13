package de.allgemein.testsweet;

import de.allgemein.lmg.calc.Leistungsberechnung;
import de.allgemein.lmg.web.Benutzerverwaltung;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Regressionen für Laufnachweise, Webkonfiguration und geordneten Abbruch. */
class ReviewLaufRegressionTest {
    @TempDir Path temp;

    @Test void schnelleFolgelogsBehaltenBeideNachweise() throws Exception {
        Path first;
        try (SweetLog log = new SweetLog(temp)) {
            first = log.runVerzeichnis;
            log.ereignis("TEST", "", "erster-nachweis");
        }
        try (SweetLog log = new SweetLog(temp)) {
            assertNotEquals(first, log.runVerzeichnis);
            log.ereignis("TEST", "", "zweiter-nachweis");
        }
        assertTrue(Files.readString(first.resolve("simulation.log")).contains("erster-nachweis"));
    }

    @Test void einzelnerWebarbeiterHatEinWirklichesZweitesKonto() {
        Simulator simulator = new Simulator();
        Benutzerverwaltung users = new Benutzerverwaltung(simulator.initialisiereWebKonten(1));
        String colleague = simulator.zufallsWebKollege("sb-web-01");
        assertNotEquals("sb-web-01", colleague);
        assertNotNull(users.anmelden(colleague, simulator.webPasswort(colleague)));
    }

    @Test void webzielVerwendetKonfiguriertenPortUndExplizitesExternziel() {
        assertEquals("http://127.0.0.1:18080/", Simulator.webZiel(18080, null));
        assertEquals("https://example.invalid/lmg/", Simulator.webZiel(18080, "https://example.invalid/lmg"));
        assertThrows(IllegalArgumentException.class, () -> Simulator.webZiel(8080, "file:///tmp/lmg"));
        assertThrows(IllegalArgumentException.class, () -> Simulator.webZiel(8080, "http://user:secret@localhost/"));
    }

    @Test void fremdesSchemaOderFehlendeIdentitaetWerdenAbgewiesen() {
        Map<String, String> identity = Map.of("databaseName", "FREE", "containerName", "FREEPDB1",
                "schema", "REVIEW", "serviceName", "freepdb1");
        assertDoesNotThrow(() -> Simulator.pruefeDatabaseIdentity(identity, identity));
        assertThrows(IllegalStateException.class, () -> Simulator.pruefeDatabaseIdentity(identity,
                Map.of("databaseName", "FREE", "containerName", "FREEPDB1", "schema", "OTHER", "serviceName", "freepdb1")));
        assertThrows(IllegalStateException.class, () -> Simulator.pruefeDatabaseIdentity(identity, Map.of()));
    }

    @Test void einzelfehlerMachtDieAbschlussphaseRot() throws Exception {
        Simulator simulator = new Simulator();
        Auftrag order = order();
        order.status = Auftrag.Status.FEHLGESCHLAGEN;
        set(simulator, "auftraege", List.of(order));
        set(simulator, "globalePruefungen", List.of(new Auftrag.Pruefung("global", "0", "0", true, null)));
        simulator.aktualisiereAbschlussphase();
        assertEquals(Simulator.Phase.FEHLGESCHLAGEN, simulator.phase());
        assertFalse(simulator.laufErfolgreich());
        order.status = Auftrag.Status.VERIFIZIERT;
        simulator.aktualisiereAbschlussphase();
        assertEquals(Simulator.Phase.FERTIG, simulator.phase());
        assertTrue(simulator.laufErfolgreich());
    }

    @Test void abgebrochenerOffenerLaufHatKeinGruenesDeckblatt() throws Exception {
        Simulator simulator = new Simulator();
        set(simulator, "auftraege", List.of(order()));
        set(simulator, "laufStart", LocalDateTime.now());
        set(simulator, "phase", Simulator.Phase.ABGEBROCHEN);
        SweetPdf pdf = new SweetPdf();
        var cover = Testdokumentation.class.getDeclaredMethod("deckblatt", SweetPdf.class, Simulator.class);
        cover.setAccessible(true);
        cover.invoke(null, pdf, simulator);
        String result = pdf.eintraegeKopie().stream().map(Object::toString)
                .filter(s -> s.contains("GESAMTERGEBNIS")).findFirst().orElseThrow();
        assertTrue(result.contains("NICHT BESTANDEN / UNVOLLSTÄNDIG"));
        assertTrue(result.contains("fuellung=" + SweetPdf.FUELL_ROT));
        assertFalse(result.contains("100 %"));
    }

    @Test void budgetAbwehrExportiertHypotheseUndBuchungsendzustandGetrennt() throws Exception {
        var cases = List.of(WebHelfer.fallEntwurf(1, false, false,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
        var result = Leistungsberechnung.berechne(0, cases, Leistungsberechnung.KduParameter.AUS, BigDecimal.ZERO);
        Rechenblock block = new Rechenblock("Abgewehrte Anordnung", cases, BigDecimal.ZERO,
                new BigDecimal("100.00"), result, List.of()).mitBudgetRestExpected(new BigDecimal("100.00"));
        Auftrag order = order().dokumentiere("Budget zu klein", List.of(block));
        Simulator simulator = new Simulator();
        try (SweetLog log = new SweetLog(temp.resolve("logs"))) {
            set(simulator, "auftraege", List.of(order));
            set(simulator, "log", log);
            Path md = temp.resolve("budget.md");
            MdBericht.schreibe(md, simulator);
            String text = Files.readString(md);
            assertTrue(text.contains("budget_rest_hypothetisch=-463.00"));
            assertTrue(text.contains("budget_rest_erwartet=100.00"));
            assertFalse(text.contains("budget_rest_erwartet=-463.00"));
        }
    }

    @Test void webDenkzeitWirdZwischenDenAktionenAngewendet() throws Exception {
        Simulator simulator = new Simulator();
        try (SweetLog log = new SweetLog(temp.resolve("logs"))) {
            set(simulator, "log", log);
            AtomicInteger pauses = new AtomicInteger();
            WebMitarbeiter worker = new WebMitarbeiter(simulator, "sb-01", "test", 0, 1, 200);
            Auftrag order = order().mitWebAktionen(List.of(
                    WebAktion.schritt("erstens", (d, c) -> "ok"),
                    WebAktion.schritt("zweitens", (d, c) -> "ok")));
            worker.spieleAuftrag(order, () -> null, (d, c) -> { }, millis -> {
                assertEquals(200, millis);
                pauses.incrementAndGet();
            });
            assertEquals(1, pauses.get());
            assertEquals(Auftrag.Status.FERTIG_GESPIELT, order.status);
        }
    }

    @Test void abbruchWartetAufDenWirklichBeendetenRestworker() throws Exception {
        var executor = Executors.newSingleThreadExecutor();
        CountDownLatch started = new CountDownLatch(1);
        AtomicBoolean release = new AtomicBoolean(), ended = new AtomicBoolean();
        executor.submit(() -> {
            started.countDown();
            while (!release.get()) {
                try { Thread.sleep(1); } catch (InterruptedException ignored) { }
            }
            ended.set(true);
        });
        try {
            assertTrue(started.await(5, TimeUnit.SECONDS));
            executor.shutdown();
            assertFalse(Simulator.beendeExecutor(executor, 0, () -> release.set(true)));
            assertTrue(ended.get());
            assertTrue(executor.isTerminated());
        } finally {
            release.set(true);
            executor.shutdownNow();
        }
    }

    @Test void webLastPrueftGenauAngelegtOhneAutomatischeFreigabe() {
        var order = new WebSzenarioBibliothek(42).erzeugeLast(1, "SWEET-LW-").get(0);
        assertEquals("ANGELEGT", order.erwartungen.get(0).erwartet());
    }

    @Test void nachtraeglicherKiAnhangBrauchtKeinOffenesLogUndLaesstOriginalUnveraendert() throws Exception {
        Simulator simulator = new Simulator();
        set(simulator, "laufStart", LocalDateTime.now());
        set(simulator, "auftraege", List.of(order()));
        Path original = temp.resolve("testdokumentation.md");
        MdBericht.schreibe(original, simulator);
        byte[] originalBytes = Files.readAllBytes(original);
        Map<String, Object> evidence = Map.of("reviewId", "review-regression", "state", "UNVERIFIED",
                "transportVerified", false, "totalCases", 1, "completedCases", 0);
        Path checkedMd = temp.resolve("testdokumentation-geprueft.md");
        Path checkedPdf = temp.resolve("testdokumentation-geprueft.pdf");
        MdBericht.schreibe(checkedMd, simulator, evidence);
        Testdokumentation.schreibe(checkedPdf, simulator, evidence);
        assertArrayEquals(originalBytes, Files.readAllBytes(original));
        assertTrue(Files.readString(checkedMd).contains("review-regression"));
        assertTrue(Files.size(checkedPdf) > 0);
    }

    private static Auftrag order() {
        return new Auftrag(1, "TEST", "SWEET-REVIEW-1", "Test", "", List.of(), List.of());
    }

    private static void set(Object object, String name, Object value) throws Exception {
        var field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(object, value);
    }
}
