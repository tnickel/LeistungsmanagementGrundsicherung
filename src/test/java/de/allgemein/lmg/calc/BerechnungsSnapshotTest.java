package de.allgemein.lmg.calc;

import de.allgemein.lmg.model.SozialFall;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Roundtrip des Berechnungs-Snapshots: Das serialisierte Berechnungsergebnis einer
 * Bescheiderteilung muss verlustfrei (inkl. BigDecimal-Skala) zurücklesbar sein —
 * darauf baut der revisionssichere Nachdruck des Bescheids.
 */
class BerechnungsSnapshotTest {

    private static BigDecimal eur(String wert) {
        return new BigDecimal(wert);
    }

    @Test
    void roundTripErhaeltErgebnisUndEingabenExakt() {
        // Fall: RBS 1, schwanger, 1 Kind unter 7, Erwerb 250 €, Absetzbeträge 15 €,
        // KdU 500/70/80 (über der Grenze 600), Vermögen 10.500 €
        SozialFall fall = new SozialFall(8L, 7L, 1, false, true, 1, 1, 1, false,
                eur("500.00"), eur("70.00"), eur("80.00"), eur("0.00"), eur("250.00"),
                eur("10.00"), eur("5.00"), eur("0.00"), eur("0.00"), 3L);
        var ergebnis = Leistungsberechnung.berechne(7L, List.of(fall),
                new Leistungsberechnung.KduParameter(BigDecimal.ZERO, new BigDecimal("600.00")),
                eur("10500.00"));

        String json = BerechnungsSnapshot.schreibe(ergebnis, List.of(fall), eur("10500.00"));
        BerechnungsSnapshot.Gelesen gelesen = BerechnungsSnapshot.lese(json);

        assertEquals(ergebnis, gelesen.ergebnis(), "Berechnungsergebnis muss exakt (inkl. Skala) überleben");
        assertEquals(eur("10500.00"), gelesen.vermoegen());
        assertEquals(fall, gelesen.faelle().get(0), "Alle Eingabemerkmale und Rohbeträge bleiben erhalten");
    }

    @Test
    void mehrereFaelleUeberlebenDenRoundtrip() {
        var a = new SozialFall(1L, 7L, 1, true, false, 0, 0, 0, true,
                eur("400.00"), eur("0.00"), eur("90.00"), eur("0.00"), eur("0.00"),
                eur("0.00"), eur("0.00"), eur("0.00"), eur("0.00"), 1L);
        var b = new SozialFall(2L, 7L, 2, false, false, 0, 0, 0, false,
                eur("300.00"), eur("0.00"), eur("60.00"), eur("400.00"), eur("0.00"),
                eur("0.00"), eur("0.00"), eur("0.00"), eur("0.00"), 1L);
        var ergebnis = Leistungsberechnung.berechne(7L, List.of(a, b));
        BerechnungsSnapshot.Gelesen gelesen =
                BerechnungsSnapshot.lese(BerechnungsSnapshot.schreibe(ergebnis, List.of(a, b), BigDecimal.ZERO));
        assertEquals(ergebnis, gelesen.ergebnis());
        assertEquals(2, gelesen.faelle().size());
        assertEquals(2, gelesen.faelle().get(1).regelbedarfsstufe());
        assertEquals(eur("400.00"), gelesen.faelle().get(1).einkommenRente());
    }

    @Test
    void fremdeSnapshotVersionWirdAbgelehnt() {
        assertThrows(IllegalArgumentException.class,
                () -> BerechnungsSnapshot.lese("{\"version\":99}"));
    }

    @Test
    void monatsplanUeberlebtSnapshotMitAllenBerechnungenUndVermoegensstaenden() throws Exception {
        SozialFall fall = new SozialFall(8, 7, 1, false, false,
                eur("300.00"), eur("0.00"), eur("0.00"), eur("0.00"), eur("0.00"), 1);
        var plan = Bewilligungsplan.erstelle(7, List.of(fall), Leistungsberechnung.KduParameter.AUS,
                eur("10500.00"), "2026-10", "2026-12", monat -> Satzstand.REFERENZ_2026);
        var gelesen = BerechnungsSnapshot.lese(BerechnungsSnapshot.schreibe(plan));
        assertEquals(3, gelesen.monate().size());
        for (int i = 0; i < plan.monate().size(); i++) {
            assertEquals(plan.monate().get(i).monat(), gelesen.monate().get(i).monat());
            assertEquals(plan.monate().get(i).ergebnis(), gelesen.monate().get(i).ergebnis());
            assertEquals(plan.monate().get(i).vermoegen(), gelesen.monate().get(i).vermoegen());
        }
        assertEquals(eur("363.00"), gelesen.ergebnis().zahlbetrag());
        assertEquals(eur("863.00"), gelesen.monate().get(1).ergebnis().zahlbetrag());
    }
}
