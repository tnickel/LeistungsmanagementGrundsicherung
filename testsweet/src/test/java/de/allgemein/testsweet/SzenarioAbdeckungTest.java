package de.allgemein.testsweet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openqa.selenium.TimeoutException;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class SzenarioAbdeckungTest {
    @TempDir Path temp;

    @Test void alleGeneriertenRechenbloeckeBestehenDieUnabhaengigeReferenz() {
        List<Auftrag> all = new ArrayList<>(new SzenarioBibliothek(42).erzeuge(500));
        all.addAll(new SzenarioBibliothek(42).erzeugeNegativ(500, "SWEET-N-"));
        all.addAll(new SzenarioBibliothek(42).erzeugeStress(3, "SWEET-S-"));
        all.addAll(new SzenarioBibliothek(42).erzeugeLast(3, "SWEET-L-"));
        all.addAll(new WebSzenarioBibliothek(42).erzeuge(30, "SWEET-W-"));
        for (Auftrag order : all) if (!order.rechenbloecke.isEmpty()) {
            var result = Verifikator.pruefeRechnung(order);
            assertTrue(result.ok(), () -> order.aktenzeichen + " " + order.szenarioTyp + ": " + result.hinweis());
        }
    }

    @Test void kaputterProduktSollwertFaelltInUnabhaengigerNachrechnungAuf() {
        Auftrag order = new SzenarioBibliothek(42).erzeuge(1).getFirst();
        String text = MdBericht.rechenbloecke(order.rechenbloecke);
        assertTrue(LlmCalculationReference.verifyBlocks(text).isEmpty());
        String mutated = text.replaceFirst("(?m)^p1_regelbedarf\\s*=\\s*[0-9.]+", "p1_regelbedarf=999999.00");
        assertNotEquals(text, mutated);
        assertFalse(LlmCalculationReference.verifyBlocks(mutated).isEmpty());
    }

    @Test void fruehereFalscheMehrbedarfUndWarmwasserSollwerteFallenInNachrechnungAuf() {
        var cases = new SzenarioBibliothek(42).erzeuge(19);
        for (int index : List.of(12, 13, 14)) {
            String text = MdBericht.rechenbloecke(cases.get(index).rechenbloecke);
            String wrong = text.replaceFirst("(?m)^p1_mehrbedarf_alleinerziehend\\s*=\\s*[0-9.]+", "p1_mehrbedarf_alleinerziehend=202.68");
            assertNotEquals(text, wrong);
            assertTrue(LlmCalculationReference.verifyBlocks(wrong).stream().anyMatch(e -> e.contains("mehrbedarf_alleinerziehend")));
        }
        String text = MdBericht.rechenbloecke(cases.get(16).rechenbloecke);
        String wrong = text.replaceFirst("(?m)^p1_kdu_warmwasser_abzug\\s*=\\s*[0-9.]+", "p1_kdu_warmwasser_abzug=20.00");
        assertNotEquals(text, wrong);
        assertTrue(LlmCalculationReference.verifyBlocks(wrong).stream().anyMatch(e -> e.contains("kdu_warmwasser_abzug")));
    }

    @Test void festeGrenzfaelleSindSeedunabhaengigUndHabenHandgerechneteMonatswerte() {
        var cases = new SzenarioBibliothek(42).erzeuge(19);
        var other = new SzenarioBibliothek(97).erzeuge(19);
        for (int i = 10; i < 19; i++) assertEquals(cases.get(i).rechenbloecke,other.get(i).rechenbloecke,"Grenzfall " + (i+1));
        var delivery = cases.get(10).rechenbloecke;
        assertEquals("95.71",delivery.get(0).ergebnis().faelle().getFirst().mehrbedarfSchwangere().toPlainString());
        assertEquals("0.00",delivery.get(1).ergebnis().faelle().getFirst().mehrbedarfSchwangere().toPlainString());
        var assets = cases.get(11).rechenbloecke;
        assertEquals("363.00",assets.get(0).ergebnis().zahlbetrag().toPlainString());
        assertEquals("863.00",assets.get(1).ergebnis().zahlbetrag().toPlainString());
        assertEquals("500.00",assets.get(0).ergebnis().verwertbaresVermoegen().toPlainString());
        assertEquals("0.00",assets.get(1).ergebnis().verwertbaresVermoegen().toPlainString());
        for (int i = 12; i <= 14; i++) assertEquals(i == 12 ? "270.24" : "337.80",cases.get(i).rechenbloecke.getFirst().ergebnis().faelle().getFirst().mehrbedarfAlleinerziehend().toPlainString());
        var capped = cases.get(15).rechenbloecke.getFirst().ergebnis().faelle().getFirst();
        assertEquals("517.64",capped.mehrbedarf().toPlainString());assertEquals("1323.64",capped.gesamtbedarf().toPlainString());
        var heating = cases.get(16).rechenbloecke.getFirst().ergebnis().faelle().getFirst();
        assertEquals("0.00",heating.kduWarmwasserAbzug().toPlainString());assertEquals("500.00",heating.kdu().toPlainString());
        assertEquals("1063.00",heating.zahlbetrag().toPlainString());
        var shared = cases.get(17).rechenbloecke.getFirst().ergebnis();
        assertEquals("500.00",shared.faelle().get(0).kdu().toPlainString());assertEquals("100.00",shared.faelle().get(1).kdu().toPlainString());
        assertEquals("1612.00",shared.zahlbetrag().toPlainString());
        var minor = cases.get(18).rechenbloecke.getFirst();
        assertEquals("10000.00",minor.schonvermoegen().toPlainString());assertEquals("657.00",minor.ergebnis().zahlbetrag().toPlainString());
        assertTrue(cases.subList(10,19).stream().allMatch(o -> Verifikator.pruefeRechnung(o).ok()));
        assertTrue(cases.get(10).aktionen.stream().anyMatch(a -> a.name().contains("Bescheid")));
        assertTrue(cases.get(11).erwartungen.stream().filter(e -> e.sql().contains("VERMOEGENSEINSATZ")).count() == 2);
    }

    @Test void reparierteGrenzfaellePruefenJungeKinderUndFesteSqlSollwerteStattProduktSollwerte() {
        var cases = new SzenarioBibliothek(42).erzeuge(19);
        int[][] counts = {{1,2,4},{0,3,5},{1,3,6}};
        String[] expected = {"2822.24","3312.80","3750.80"};
        for (int i = 0; i < counts.length; i++) {
            var order = cases.get(12 + i);
            var parent = order.rechenbloecke.getFirst().faelle().getFirst();
            assertEquals(counts[i][0],parent.anzKinderUnter7());
            assertEquals(counts[i][1],parent.anzKinderUnter16());
            assertEquals(counts[i][2],parent.anzKinderMinderjaehrig());
            assertEquals(expected[i],order.rechenbloecke.getFirst().ergebnis().zahlbetrag().toPlainString());
            var sql = order.erwartungen.stream().filter(e -> e.sql().startsWith("SELECT AUSZAHLUNGSBETRAG")).findFirst().orElseThrow();
            assertEquals(expected[i],sql.erwartet());
        }
        var central = cases.get(16);
        assertEquals("0.20",central.rechenbloecke.getFirst().kduParameter().warmwasserAbzugHeizungProzent().toPlainString());
        assertEquals("1063.00",central.erwartungen.stream().filter(e -> e.sql().startsWith("SELECT AUSZAHLUNGSBETRAG")).findFirst().orElseThrow().erwartet());
    }

    @Test void kduServicesRemainImmutableAndSeparateAcrossParallelScenarioParameters() throws Exception {
        var pool=(de.allgemein.tx.ConnectionPool)java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{de.allgemein.tx.ConnectionPool.class},(p,m,a)->{throw new AssertionError("Keine DB in Konfigurationstest");});
        var manager=new de.allgemein.tx.TxManager(pool);Simulator simulator=new Simulator();
        var base=new de.allgemein.lmg.service.SozialLeistungsService(manager);set(simulator,"service",base);set(simulator,"fachTxManager",manager);
        var cases=new SzenarioBibliothek(42).erzeuge(19);
        var heating=simulator.service(cases.get(16));
        var limit=simulator.service(cases.get(17));
        assertNotSame(base,heating);assertNotSame(heating,limit);assertSame(base,simulator.service(cases.get(0)));assertSame(heating,simulator.service(cases.get(16)));
        var parameter=de.allgemein.lmg.service.SozialLeistungsService.class.getDeclaredField("kduParameter");parameter.setAccessible(true);
        assertEquals(cases.get(16).rechenbloecke.getFirst().kduParameter(),parameter.get(heating));
        assertEquals(cases.get(17).rechenbloecke.getFirst().kduParameter(),parameter.get(limit));
        assertEquals(de.allgemein.lmg.calc.Leistungsberechnung.KduParameter.AUS,parameter.get(base));
    }

    @Test void geplanterVorgangsbestandIstVonErfolgUndKontextUnabhaengig() {
        var positive = new SzenarioBibliothek(42).erzeuge(500);
        assertEquals(500, positive.stream().mapToInt(o -> o.erwarteteVorgaenge).sum());
        assertEquals(11, positive.stream().filter(o -> o.szenarioTyp.equals("KASSENLAUF")).count());
        assertEquals(471, positive.stream().filter(o -> o.szenarioTyp.equals("KOMBINATION")).count());
        var stress = new SzenarioBibliothek(42).erzeugeStress(3, "SWEET-S-");
        assertEquals(6, stress.size(), "Die Konfiguration zählt User, nicht Transaktionsblöcke; drei User sind zulässig");
        assertEquals(3, stress.stream().mapToInt(o -> o.erwarteteVorgaenge).sum());
        assertTrue(positive.stream().allMatch(o -> o.kontext.isEmpty() && o.bgId == null));
        var negative = new SzenarioBibliothek(42).erzeugeNegativ(9, "SWEET-N-");
        assertEquals(0, negative.stream().filter(o -> o.szenarioTyp.equals("NEG-BANKVERBINDUNG")).findFirst().orElseThrow().erwarteteVorgaenge);
        assertEquals(8, negative.stream().mapToInt(o -> o.erwarteteVorgaenge).sum());
    }

    @Test void generiertVermoegensStornoUndPrueftBeideEinsaetzeUndBescheidZeitraum() {
        var positive = new SzenarioBibliothek(42).erzeuge(500);
        assertTrue(positive.stream().anyMatch(o -> o.rechenbloecke.stream().anyMatch(b -> b.ergebnis().verwertbaresVermoegen().signum() > 0)
                && o.erwartungen.stream().anyMatch(e -> e.beschreibung().equals("Neuzahlung setzt zurückgebuchtes Vermögen erneut ein"))));
        for (Auftrag order : positive) for (Erwartung expectation : order.erwartungen) {
            if (expectation.beschreibung().equals("genau ein erteilter Bewilligungsbescheid")) {
                assertTrue(expectation.sql().contains("BEWILLIGUNG_VON=?"));
                assertTrue(expectation.sql().contains("BEWILLIGUNG_BIS=?"));
            }
        }
    }

    @Test void loginAbwehrAkzeptiertNurKontrolliertePasswortpruefungOhneToken() {
        assertDoesNotThrow(() -> WebHelfer.pruefeAnmeldeAbweisung("400", "VALIDATION", "Kennung oder Passwort ist falsch", null));
        for (String status : List.of("0", "401", "403", "429", "500", "503")) {
            assertThrows(IllegalStateException.class, () -> WebHelfer.pruefeAnmeldeAbweisung(status, "VALIDATION", "Kennung oder Passwort ist falsch", null));
        }
        assertThrows(IllegalStateException.class, () -> WebHelfer.pruefeAnmeldeAbweisung("400", "VALIDATION", "Datenbank nicht erreichbar", null));
        assertThrows(IllegalStateException.class, () -> WebHelfer.pruefeAnmeldeAbweisung("400", "VALIDATION", "Kennung oder Passwort ist falsch", "new-token"));
    }

    @Test void allgemeinerRendererTimeoutIstKeinSessiontodUndWirdNichtWiederholt() throws Exception {
        try (var log = new SweetLog(temp)) {
            Simulator simulator = new Simulator(); set(simulator, "log", log);
            WorkerIdentityFixture.prepare(simulator, "sb-test");
            AtomicInteger restarts = new AtomicInteger();
            Auftrag order = new Auftrag(1, "WEB", "SWEET-TEST", "", "", List.of(), List.of())
                    .mitWebAktionen(List.of(WebAktion.wiederholbar("Navigation", (d, c) -> {
                        throw new TimeoutException("timed out receiving message from renderer");
                    })));
            new WebMitarbeiter(simulator, "sb-test", "", 0, 1, 0)
                    .spieleAuftrag(order, () -> { restarts.incrementAndGet(); return null; }, (d, c) -> { });
            assertEquals(0, restarts.get());
            assertEquals(Auftrag.Status.FEHLGESCHLAGEN, order.status);
        }
    }

    @Test void workerErrorBrichtStressSofortAbUndLoestDieWellenbarriere() throws Exception {
        try (var log = new SweetLog(temp)) {
            Simulator simulator = new Simulator();
            WorkerIdentityFixture.prepare(simulator, "sb-test");
            Auftrag first = new Auftrag(1, "STRESS-ANLEGEN", "SWEET-TEST", "", "", List.of(
                    SzenarioAktion.schritt("Fataler Fehler", (s, i, k, c) -> { throw new AssertionError("CONTROLLED_WORKER_CRASH"); })), List.of());
            Auftrag second = new Auftrag(2, "STRESS-BEARBEITEN", "SWEET-TEST", "", "", List.of(), List.of());
            set(simulator, "log", log); set(simulator, "auftraege", List.of(first, second));
            set(simulator, "kategorieLetzter", Simulator.Kategorie.STRESS);
            var init = Simulator.class.getDeclaredMethod("initialisiereStressBarriere"); init.setAccessible(true); init.invoke(simulator);
            assertTimeoutPreemptively(Duration.ofSeconds(2), () -> new Mitarbeiter(simulator, "sb-test", 0).run());
            assertEquals(Auftrag.Status.FEHLGESCHLAGEN, first.status);
            assertTrue(first.fehlerText.contains("CONTROLLED_WORKER_CRASH"));
            assertTrue(simulator.abgebrochen());
            assertNull(simulator.nimmAuftrag(), "Keine Bearbeitungswelle nach Workerabsturz");
        }
    }

    private static void set(Object object, String name, Object value) throws Exception {
        var field = object.getClass().getDeclaredField(name); field.setAccessible(true); field.set(object, value);
    }
}
