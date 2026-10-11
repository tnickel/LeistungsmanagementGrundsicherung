package de.allgemein.testsweet;

import de.allgemein.lmg.calc.Leistungsberechnung;
import de.allgemein.lmg.model.SozialFall;

import java.math.BigDecimal;
import java.util.List;

/**
 * Nachvollziehbarer Rechenweg eines Auftrags für die PDF-Testdokumentation:
 * alle Eingaben (Fälle, Vermögen, Budget) plus das vollständige
 * {@link Leistungsberechnung.Berechnungsergebnis} mit seinen je-Fall-Aufschlüsselungen —
 * daraus stellt die Testdokumentation jede Rechenoperation inklusive Norm-Hinweis
 * so dar, dass man den erwarteten Zahlbetrag und das erwartete Restbudget
 * vollständig von Hand nachrechnen kann.
 *
 * <p>Aufträge mit mehreren Rechnungen (z. B. Storno + korrigierte Neuauszahlung)
 * tragen mehrere Blöcke mit aussagekräftigem Titel.</p>
 */
public record Rechenblock(
        String titel,
        List<SozialFall> faelle,
        BigDecimal vermoegen,
        BigDecimal budget,
        Leistungsberechnung.Berechnungsergebnis ergebnis,
        List<String> zusatzHinweise) {

    public Rechenblock {
        faelle = List.copyOf(faelle);
        zusatzHinweise = zusatzHinweise == null ? List.of() : List.copyOf(zusatzHinweise);
    }

    /** Schonvermögen der BG in EUR (§ 90 Abs. 2 Nr. 9 i.V.m. § 1 BarbetragsVO). */
    public BigDecimal schonvermoegen() {
        return Leistungsberechnung.schonvermoegen(faelle);
    }

    /** Kopie mit gesetztem Budget (das Budget steht erst nach der Berechnung fest). */
    public Rechenblock mitBudget(BigDecimal neuesBudget) {
        return new Rechenblock(titel, faelle, vermoegen, neuesBudget, ergebnis, zusatzHinweise);
    }
}
