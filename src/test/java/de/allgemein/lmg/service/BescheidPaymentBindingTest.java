package de.allgemein.lmg.service;

import de.allgemein.lmg.calc.BerechnungsSnapshot;
import de.allgemein.lmg.calc.Bewilligungsplan;
import de.allgemein.lmg.calc.Leistungsberechnung;
import de.allgemein.lmg.calc.Satzstand;
import de.allgemein.lmg.model.Bescheid;
import de.allgemein.lmg.model.SozialFall;
import de.allgemein.lmg.web.Json;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BescheidPaymentBindingTest {
    private final SozialFall person = new SozialFall(8, 7, 1, false, false,
            eur("300"), eur("0"), eur("0"), eur("0"), eur("0"), 1);

    @Test void monthlySnapshotUsesTargetMonthRatherThanScalarFirstMonth() throws Exception {
        String json = BerechnungsSnapshot.schreibe(Bewilligungsplan.erstelle(7, List.of(person),
                Leistungsberechnung.KduParameter.AUS, eur("10500"), "2026-10", "2026-11", m -> Satzstand.REFERENZ_2026));
        Bescheid decision = decision(json, eur("363.00"));
        assertEquals(eur("363.00"), BescheidPaymentBinding.approvedAmount(decision, "2026-10"));
        assertEquals(eur("863.00"), BescheidPaymentBinding.approvedAmount(decision, "2026-11"));
    }

    @Test void genuineV1ContractAndSnapshotlessLegacyAmountRemainReadable() {
        var result = Leistungsberechnung.berechne(7, List.of(person));
        String legacy = BerechnungsSnapshot.schreibe(result, List.of(person), eur("0"));
        assertEquals(eur("863.00"), BescheidPaymentBinding.approvedAmount(decision(legacy, eur("863.00")), "2026-11"));
        assertEquals(eur("800.00"), BescheidPaymentBinding.approvedAmount(decision(null, eur("800.00")), "2026-11"));
    }

    @Test @SuppressWarnings("unchecked") void incompleteDuplicatedAndForeignMonthlyPlansFailClosed() throws Exception {
        String valid = BerechnungsSnapshot.schreibe(Bewilligungsplan.erstelle(7, List.of(person),
                Leistungsberechnung.KduParameter.AUS, eur("0"), "2026-10", "2026-11", m -> Satzstand.REFERENZ_2026));
        for (String corruption : List.of("missing", "duplicate", "foreign", "firstAmount")) {
            var root = (Map<String, Object>) Json.parse(valid);
            var months = (List<Map<String, Object>>) root.get("monate");
            switch (corruption) {
                case "missing" -> months.removeLast();
                case "duplicate" -> months.get(1).put("monat", "2026-10");
                case "foreign" -> months.get(1).put("bgId", 9);
                case "firstAmount" -> months.getFirst().put("zahlbetrag", "1.00");
            }
            var error = assertThrows(FachlicheException.class, () -> BescheidPaymentBinding.approvedAmount(
                    decision(Json.write(root), eur("863.00")), "2026-11"), corruption);
            assertEquals("VALIDATION", error.code());
        }
    }

    @Test void malformedArchivedSnapshotNeverBecomesZeroOrCurrentRecalculation() {
        for (String json : List.of("", "{}", "{\"version\":1}")) {
            assertThrows(FachlicheException.class, () -> BescheidPaymentBinding.approvedAmount(decision(json, eur("863.00")), "2026-10"));
            assertThrows(FachlicheException.class, () -> BescheidPaymentBinding.readSnapshot(decision(json, eur("863.00"))));
        }
    }

    private static Bescheid decision(String snapshot, BigDecimal first) {
        return new Bescheid(1, 7, "BESCH-TEST", LocalDate.of(2026, 10, 1), "2026-10", "2026-11",
                first, "ERTEILT", "sb-test", Instant.EPOCH, snapshot);
    }

    private static BigDecimal eur(String value) { return new BigDecimal(value).setScale(2); }
}
