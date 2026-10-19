package de.allgemein.lmg.calc;

import de.allgemein.lmg.model.SozialFall;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Handwerte für die 12-Prozent-Alternative ohne eine erfüllte 36-Prozent-Konstellation. */
class AlleinerziehendZwoelfProzentTest {
    @ParameterizedTest(name = "U16={0}, minderjährig={1}: Mehrbedarf {2} EUR")
    @CsvSource({"1,2,135.12", "0,2,135.12", "0,3,202.68", "0,4,270.24", "0,5,337.80", "0,6,337.80"})
    void mehrereKinderOhneU7OderZweiBisDreiU16ErhaltenZwoelfProzentJeKind(int unter16, int minors, String expected) {
        // Stufe 1 = 563 EUR: 2/3/4 Kinder → 24/36/48 %, ab 5 Kindern Deckel 60 %.
        var zero = new BigDecimal("0.00");
        var parent = new SozialFall(1, 7, 1, false, false, 0, unter16, minors, false,
                zero, zero, zero, zero, zero, zero, zero, zero, zero, null, 1);
        assertEquals(new BigDecimal(expected), Leistungsberechnung.berechneFall(parent).mehrbedarfAlleinerziehend());
    }
}
