package de.allgemein.testsweet;

import de.allgemein.lmg.calc.*;
import de.allgemein.lmg.model.SozialFall;
import de.allgemein.lmg.service.FachlicheException;
import de.allgemein.lmg.web.Json;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/** Produktregressionen ohne gemeinsame Referenzrechnung oder Datenbankabhängigkeit. */
class GrokFachProduktRegressionTest {
    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private SozialFall person(long id, boolean merkmale, int u7, int u16, int minor) {
        return new SozialFall(id, 7, 1, merkmale, merkmale, u7, u16, minor, merkmale,
                new BigDecimal("500.00"), new BigDecimal("70.00"), new BigDecimal("80.00"),
                new BigDecimal("0.00"), new BigDecimal("250.00"), new BigDecimal("10.00"),
                new BigDecimal("5.00"), new BigDecimal("3.00"), new BigDecimal("4.00"), "2026-10", 9);
    }

    @Test
    void mehrereElternOhneKinderzuordnungWerdenNichtDoppeltAbgerechnet() {
        assertThrows(FachlicheException.class, () -> Leistungsberechnung.berechne(7,
                List.of(person(1, false, 1, 2, 4), person(2, false, 1, 2, 4))));
        var eindeutig = Leistungsberechnung.berechne(7,
                List.of(person(1, false, 1, 2, 4), person(2, false, 0, 0, 0)));
        assertEquals(new BigDecimal("270.24"), eindeutig.faelle().getFirst().mehrbedarfAlleinerziehend());
        assertEquals(ZERO, eindeutig.faelle().get(1).mehrbedarfAlleinerziehend());
    }

    @Test
    void widerspruechlicheVolleKinderzaehlerErhoehenNichtStillDenAnspruch() {
        assertThrows(FachlicheException.class,
                () -> Leistungsberechnung.berechneFall(person(1, false, 2, 1, 4)));
        assertThrows(FachlicheException.class,
                () -> Leistungsberechnung.berechneFall(person(1, false, 1, 2, 1)));
    }

    @Test
    void snapshotV3ErhaeltAlleMerkmaleUndRohbeträgeAuchBeiKduKappung() throws Exception {
        var fall = person(1, true, 1, 2, 4);
        var plan = Bewilligungsplan.erstelle(7, List.of(fall),
                new Leistungsberechnung.KduParameter(ZERO, new BigDecimal("600.00")), ZERO,
                "2026-10", "2026-11", monat -> Satzstand.REFERENZ_2026);
        var snapshot = BerechnungsSnapshot.lese(BerechnungsSnapshot.schreibe(plan, "<body>Erteilt</body>"));
        assertEquals(fall, snapshot.faelle().getFirst());
        assertEquals(new BigDecimal("650.00"), snapshot.faelle().getFirst().kduMiete()
                .add(snapshot.faelle().getFirst().kduBetriebskosten()).add(snapshot.faelle().getFirst().kduHeizung()));
        assertEquals(new BigDecimal("600.00"), snapshot.ergebnis().faelle().getFirst().kdu());
        assertFalse(snapshot.monate().get(1).faelle().getFirst().schwanger());
        assertEquals("2026-10", snapshot.monate().get(1).faelle().getFirst().entbindungsmonat());
        assertEquals("<body>Erteilt</body>", snapshot.dokumentHtml());
    }

    @Test
    @SuppressWarnings("unchecked")
    void alteSnapshotsOhneMerkmalsblockBleibenLesbar() {
        var fall = person(1, false, 0, 0, 0);
        var ergebnis = Leistungsberechnung.berechne(7, List.of(fall));
        var json = (Map<String, Object>) Json.parse(BerechnungsSnapshot.schreibe(ergebnis, List.of(fall), ZERO));
        ((Map<String, Object>) ((List<?>) json.get("faelle")).getFirst()).remove("eingaben");
        var snapshot = BerechnungsSnapshot.lese(Json.write(json));
        assertEquals(ergebnis, snapshot.ergebnis());
        assertEquals(1L, snapshot.faelle().getFirst().version());
    }

    @Test
    void volleVermoegensdeckungSimuliertKeinenUnbelegtenVerbrauch() throws Exception {
        var fall = new SozialFall(1, 7, 1, false, false, ZERO, ZERO, ZERO, ZERO, ZERO, 1);
        var plan = Bewilligungsplan.erstelle(7, List.of(fall), Leistungsberechnung.KduParameter.AUS,
                new BigDecimal("10563.00"), "2026-10", "2026-11", monat -> Satzstand.REFERENZ_2026);
        for (var monat : plan.monate()) {
            assertEquals(ZERO, monat.ergebnis().zahlbetrag());
            assertEquals(new BigDecimal("10563.00"), monat.vermoegen());
        }
    }

    @Test
    void bisherigePositiveEinmalfortschreibungBleibtExpliziteModellregel() throws Exception {
        var fall = new SozialFall(1, 7, 1, false, false, ZERO, ZERO, ZERO, ZERO, ZERO, 1);
        var plan = Bewilligungsplan.erstelle(7, List.of(fall), Leistungsberechnung.KduParameter.AUS,
                new BigDecimal("10000.01"), "2026-10", "2026-11", monat -> Satzstand.REFERENZ_2026);
        assertEquals(new BigDecimal("562.99"), plan.ersterMonat().ergebnis().zahlbetrag());
        assertEquals(new BigDecimal("563.00"), plan.monate().get(1).ergebnis().zahlbetrag());
    }
}
