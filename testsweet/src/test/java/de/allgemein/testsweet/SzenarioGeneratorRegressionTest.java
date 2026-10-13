package de.allgemein.testsweet;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SzenarioGeneratorRegressionTest {
    @Test void budgetNegativfaelleSindAuchBeiKleinemAnspruchWirklichZuKlein() {
        for (long seed : List.of(17L, 37L, 42L, 65L, 85L, 97L)) {
            var auftraege = new SzenarioBibliothek(seed).erzeugeNegativ(500, "SWEET-N-");
            assertEquals(500, auftraege.size());
            for (var a : auftraege) {
                if (!a.beschreibung.startsWith("Verstoß-Kombination: budget")) continue;
                for (var r : a.rechenbloecke) {
                    assertTrue(r.budget().signum() >= 0, "negatives Budget");
                    assertTrue(r.budget().compareTo(r.ergebnis().zahlbetrag()) < 0,
                            "Seed " + seed + ", TF " + a.id + ": Budget muss unter Anspruch liegen");
                }
            }
        }
    }

    @Test void sepaKombinationenWiederholenKeineTatsaechlichGetestetenBankdaten() {
        var auftraege = new SzenarioBibliothek(42).erzeugeNegativ(500, "SWEET-N-");
        var bankdaten = new HashSet<List<String>>();
        for (var a : auftraege) {
            if (!a.beschreibung.startsWith("SEPA-Anlage abgewehrt")) continue;
            var eingaben = a.aktionen.stream().map(SzenarioAktion::name).toList();
            assertTrue(bankdaten.add(eingaben), "Wiederholte SEPA-Konstellation bei TF " + a.id);
        }
        assertEquals(9, bankdaten.size(), "Alle neun tatsächlichen IBAN/BIC-Paare, danach andere Fachfälle");
        assertEquals(500, auftraege.size(), "Eindeutigkeit darf den gesamten Topf nicht verkürzen");
    }

    @Test void ueberlangeBescheideHabenDieBehaupteteChronologischeLaenge() throws Exception {
        int geprueft = 0;
        for (var a : new SzenarioBibliothek(42).erzeugeNegativ(500, "SWEET-N-")) {
            if (!a.szenarioTyp.equals("NEG-KOMBINATION")) continue;
            for (var aktion : a.aktionen) {
                if (!aktion.name().startsWith("Bescheid über ")) continue;
                int behaupteteLaenge = Integer.parseInt(aktion.name().split(" ")[2]);
                var monate = new ArrayList<YearMonth>();
                // Die Lambda-Captures sind die tatsächlich an den Service übergebenen Grenzen.
                for (var feld : aktion.funktion().getClass().getDeclaredFields()) {
                    feld.setAccessible(true);
                    Object wert = feld.get(aktion.funktion());
                    if (wert instanceof String s && s.matches("\\d{4}-\\d{2}")) {
                        monate.add(YearMonth.parse(s));
                    }
                }
                assertEquals(2, monate.size());
                assertFalse(monate.get(0).isAfter(monate.get(1)));
                assertEquals(behaupteteLaenge, ChronoUnit.MONTHS.between(monate.get(0), monate.get(1)) + 1);
                assertTrue(behaupteteLaenge > 12);
                geprueft++;
            }
        }
        assertTrue(geprueft > 0);
    }

    @Test void ohneFreigabeschrittSindNurAngelegteZahlungenOhneFreigabevermerkZulaessig() {
        var alle = new ArrayList<Auftrag>(new SzenarioBibliothek(42).erzeugeLast(20, "SWEET-L-"));
        alle.addAll(new SzenarioBibliothek(42).erzeugeStress(20, "SWEET-S-"));
        var v = new Verifikator("", "", "");
        for (var a : alle) {
            if (a.szenarioTyp.equals("STRESS-ANLEGEN")) continue;
            var status = a.erwartungen.stream().filter(e -> e.sql().startsWith("SELECT ZAHLUNGSSTATUS"))
                    .findFirst().orElseThrow();
            assertTrue(v.vergleiche(status, "ANGELEGT"));
            assertFalse(v.vergleiche(status, "BEREIT"));
            assertFalse(v.vergleiche(status, "UEBERGEBEN"));
            assertTrue(a.erwartungen.stream().anyMatch(e -> e.sql().contains("FREIGEGEBEN_VON IS NULL")));
        }
    }
}
