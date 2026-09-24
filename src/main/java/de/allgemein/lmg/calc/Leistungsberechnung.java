package de.allgemein.lmg.calc;

import de.allgemein.lmg.model.SozialFall;
import de.allgemein.lmg.service.FachlicheException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Deterministische Berechnung der Grundsicherung im Alter / bei Erwerbsminderung
 * (SGB XII) für das Referenzjahr 2026:
 *
 * <pre>
 *   Gesamtbedarf   = Regelbedarf (RBS) + Mehrbedarf (17 % bei Merkzeichen G) + KdU (Miete + Heizung)
 *   Freibetrag     = min(30 % Brutto-Erwerbseinkommen, 50 % der Regelbedarfsstufe 1)   -- § 82 Abs. 3
 *   Anrechenbar    = Renteneinkünfte (voll) + Erwerbseinkommen - Freibetrag
 *   Zahlbetrag     = Gesamtbedarf - Anrechenbares Einkommen   (je Bedarfsgemeinschaft summiert)
 * </pre>
 *
 * Die Methode ist reine Funktion ohne Datenbankzugriff — damit vollständig unit-testbar.
 * Werte sind Beträge in EUR mit zwei Nachkommastellen.
 */
public final class Leistungsberechnung {

    /** Regelbedarfsstufen 2026; Stufen 3-6 sind im Demo-Umfang nicht hinterlegt. */
    private static final Map<Integer, BigDecimal> REGELBEDARF_2026 = Map.of(
            1, new BigDecimal("563.00"),
            2, new BigDecimal("506.00"));

    /** Mehrbedarf Merkzeichen G (§ 30 Abs. 1 SGB XII): 17 % des maßgeblichen Regelbedarfs. */
    public static final BigDecimal MEHRBEDARF_G_ANTEIL = new BigDecimal("0.17");

    /** Erwerbsfreibetrag: 30 % des Brutto-Erwerbseinkommens. */
    public static final BigDecimal ERWERBSFREIBETRAG_ANTEIL = new BigDecimal("0.30");

    /** … aber hart gedeckelt auf 50 % der Regelbedarfsstufe 1 (2026: 281,50 EUR). */
    public static final BigDecimal ERWERBSFREIBETRAG_KAPPUNG_RBS1 = new BigDecimal("0.50");

    private static final int SCALE = 2;

    private Leistungsberechnung() {
    }

    /** Summierte Berechnung über alle SozialFälle einer Bedarfsgemeinschaft. */
    public static Berechnungsergebnis berechne(long bgId, List<SozialFall> faelle) {
        if (faelle.isEmpty()) {
            throw new FachlicheException(FachlicheException.VALIDATION,
                    "Die Bedarfsgemeinschaft " + bgId + " hat keinen SozialFall");
        }
        BigDecimal gesamtbedarf = BigDecimal.ZERO;
        BigDecimal anrechenbarGesamt = BigDecimal.ZERO;
        List<FallBerechnung> einzeln = new ArrayList<>();
        for (SozialFall fall : faelle) {
            FallBerechnung berechnung = berechneFall(fall);
            einzeln.add(berechnung);
            gesamtbedarf = gesamtbedarf.add(berechnung.gesamtbedarf());
            anrechenbarGesamt = anrechenbarGesamt.add(berechnung.anrechenbaresEinkommen());
        }
        BigDecimal zahlbetrag = gesamtbedarf.subtract(anrechenbarGesamt);
        return new Berechnungsergebnis(bgId, List.copyOf(einzeln), gesamtbedarf, anrechenbarGesamt, zahlbetrag);
    }

    /** Berechnung für einen einzelnen SozialFall (Details in der Maske Bedarfsermittlung). */
    public static FallBerechnung berechneFall(SozialFall fall) {
        BigDecimal regelbedarf = regelbedarf(fall.regelbedarfsstufe());
        BigDecimal mehrbedarf = fall.merkzeichenG()
                ? regelbedarf.multiply(MEHRBEDARF_G_ANTEIL).setScale(SCALE, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(SCALE);
        BigDecimal kdu = fall.kduMiete().add(fall.kduHeizung());
        BigDecimal gesamtbedarf = regelbedarf.add(mehrbedarf).add(kdu);

        BigDecimal freibetrag = erwerbsfreibetrag(fall.einkommenErwerb());
        BigDecimal anrechenbar = fall.einkommenRente()
                .add(fall.einkommenErwerb())
                .subtract(freibetrag)
                .max(BigDecimal.ZERO);
        BigDecimal zahlbetrag = gesamtbedarf.subtract(anrechenbar);
        return new FallBerechnung(fall.id(), regelbedarf, mehrbedarf, kdu, gesamtbedarf,
                freibetrag, anrechenbar, zahlbetrag);
    }

    private static BigDecimal regelbedarf(int rbs) {
        BigDecimal betrag = REGELBEDARF_2026.get(rbs);
        if (betrag == null) {
            throw new FachlicheException(FachlicheException.RBS_NICHT_KONFIGURIERT,
                    "Regelbedarfsstufe " + rbs + " ist für das Referenzjahr 2026 nicht hinterlegt "
                            + "(konfiguriert sind Stufe 1 und 2)");
        }
        return betrag;
    }

    private static BigDecimal erwerbsfreibetrag(BigDecimal erwerbseinkommen) {
        BigDecimal kappung = REGELBEDARF_2026.get(1).multiply(ERWERBSFREIBETRAG_KAPPUNG_RBS1)
                .setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal anteilig = erwerbseinkommen.multiply(ERWERBSFREIBETRAG_ANTEIL)
                .setScale(SCALE, RoundingMode.HALF_UP);
        return anteilig.min(kappung);
    }

    /** Ergebnis für einen SozialFall; alle Beträge EUR mit zwei Nachkommastellen. */
    public record FallBerechnung(
            long fallId,
            BigDecimal regelbedarf,
            BigDecimal mehrbedarf,
            BigDecimal kdu,
            BigDecimal gesamtbedarf,
            BigDecimal freibetragErwerb,
            BigDecimal anrechenbaresEinkommen,
            BigDecimal zahlbetrag) {
    }

    /** Ergebnis je Bedarfsgemeinschaft: Summen über alle SozialFälle plus endgültiger Zahlbetrag. */
    public record Berechnungsergebnis(
            long bgId,
            List<FallBerechnung> faelle,
            BigDecimal gesamtbedarf,
            BigDecimal anrechenbaresEinkommen,
            BigDecimal zahlbetrag) {
    }
}
