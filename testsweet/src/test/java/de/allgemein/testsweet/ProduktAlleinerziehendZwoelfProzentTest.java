package de.allgemein.testsweet;

import de.allgemein.lmg.calc.*;
import de.allgemein.lmg.model.SozialFall;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.math.BigDecimal;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Installiertes Produkt: Mehrbedarf, BG-Summe und persistierbarer Monatsplan ohne Nr.-1-Konstellation. */
class ProduktAlleinerziehendZwoelfProzentTest {
    @ParameterizedTest(name = "U16={0}, Minderjährige={1}: Mehrbedarf {2}, BG {3} EUR")
    @CsvSource({"1,2,135.12,1640.12", "0,2,135.12,1640.12", "0,3,202.68,2178.68",
            "0,4,270.24,2717.24", "0,5,337.80,3255.80", "0,6,337.80,3726.80"})
    void zwoelfProzentAlternativeGehtOhnePauschale36ProzentInDenMonatsplanEin(
            int unter16, int minors, String expectedSupplement, String expectedTotal) throws Exception {
        var zero = new BigDecimal("0.00");
        var people = new ArrayList<SozialFall>();
        people.add(new SozialFall(1, 7, 1, false, false, 0, unter16, minors, false,
                zero, zero, zero, zero, zero, zero, zero, zero, zero, null, 1));
        for (int i = 0; i < minors; i++) people.add(new SozialFall(i + 2, 7, 4, false, false,
                zero, zero, zero, zero, zero, 1));
        // Handwerte: 563 + Anzahl × 471 + Mehrbedarf; keine KdU, Einkommen oder Vermögen.
        var plan = Bewilligungsplan.erstelle(7, people, Leistungsberechnung.KduParameter.AUS,
                zero, "2026-10", "2026-11", month -> Satzstand.REFERENZ_2026);
        var snapshot = BerechnungsSnapshot.lese(BerechnungsSnapshot.schreibe(plan));
        for (var month : snapshot.monate()) {
            assertEquals(new BigDecimal(expectedSupplement), month.ergebnis().faelle().getFirst().mehrbedarfAlleinerziehend());
            assertEquals(new BigDecimal(expectedTotal), month.ergebnis().zahlbetrag());
        }
    }
}
