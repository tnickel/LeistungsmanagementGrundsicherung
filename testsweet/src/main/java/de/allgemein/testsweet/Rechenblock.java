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
        String zahlungsmonat,
        List<SozialFall> faelle,
        BigDecimal vermoegen,
        BigDecimal budget,
        Leistungsberechnung.Berechnungsergebnis ergebnis,
        List<String> zusatzHinweise,
        BigDecimal budgetRestExpected,
        Leistungsberechnung.KduParameter kduParameter) {

    public Rechenblock {
        faelle = List.copyOf(faelle);
        zusatzHinweise = zusatzHinweise == null ? List.of() : List.copyOf(zusatzHinweise);
        kduParameter = java.util.Objects.requireNonNull(kduParameter, "kduParameter");
    }

    public Rechenblock(String titel, String zahlungsmonat, List<SozialFall> faelle,
            BigDecimal vermoegen, BigDecimal budget, Leistungsberechnung.Berechnungsergebnis ergebnis,
            List<String> zusatzHinweise, BigDecimal budgetRestExpected) {
        this(titel, zahlungsmonat, faelle, vermoegen, budget, ergebnis, zusatzHinweise,
                budgetRestExpected, Leistungsberechnung.KduParameter.AUS);
    }

    public Rechenblock(String titel, String zahlungsmonat, List<SozialFall> faelle,
            BigDecimal vermoegen, BigDecimal budget,
            Leistungsberechnung.Berechnungsergebnis ergebnis, List<String> zusatzHinweise) {
        this(titel, zahlungsmonat, faelle, vermoegen, budget, ergebnis, zusatzHinweise, null);
    }

    /** Abwärtskompatibler Konstruktor ohne Zahlungsmonat (kuratierte Szenarien). */
    public Rechenblock(String titel, List<SozialFall> faelle, BigDecimal vermoegen,
            BigDecimal budget, Leistungsberechnung.Berechnungsergebnis ergebnis,
            List<String> zusatzHinweise) {
        this(titel, null, faelle, vermoegen, budget, ergebnis, zusatzHinweise, null);
    }

    /** Schonvermögen der BG in EUR (§ 90 Abs. 2 Nr. 9 i.V.m. § 1 BarbetragsVO). */
    public BigDecimal schonvermoegen() {
        return Leistungsberechnung.schonvermoegen(faelle);
    }

    /** Kopie mit gesetztem Budget (das Budget steht erst nach der Berechnung fest). */
    public Rechenblock mitBudget(BigDecimal neuesBudget) {
        return new Rechenblock(titel, zahlungsmonat, faelle, vermoegen, neuesBudget, ergebnis,
                zusatzHinweise, budgetRestExpected, kduParameter);
    }

    /** Expliziter Buchungsendzustand; der rechnerische Anspruch allein beweist keine Buchung. */
    public Rechenblock mitBudgetRestExpected(BigDecimal expected) {
        return new Rechenblock(titel, zahlungsmonat, faelle, vermoegen, budget, ergebnis,
                zusatzHinweise, expected, kduParameter);
    }
}
