package de.allgemein.lmg.calc;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Regelbedarfsstufen eines Gültigkeitszeitraums (Anlage zu § 28 SGB XII). Die Sätze
 * werden jährlich zum 1. Januar per Verordnung fortgeschrieben — deshalb liegen sie
 * in der Datenbanktabelle SATZSTAND (eine Zeile je Gültigkeitsmonat), nicht als
 * Konstanten im Code. Der Jahreswechsel ist ein INSERT, keine Codeänderung.
 *
 * <p>{@link #REFERENZ_2026} ist die Seed-Referenz (und dient den DB-unabhängigen
 * Kompatibilitätsüberladungen der Berechnung sowie testsweet) — der Produktionspfad
 * (Service, Saga) liest den Stand stets aus der Datenbank zum Bezugsmonat.</p>
 *
 * @param gueltigAb erster Gültigkeitsmonat im Format YYYY-MM
 */
public record Satzstand(
        String gueltigAb,
        BigDecimal rbs1,
        BigDecimal rbs2,
        BigDecimal rbs3,
        BigDecimal rbs4,
        BigDecimal rbs5,
        BigDecimal rbs6) {

    /** Seed- und Testreferenz 2026 (563/506/451/471/390/357 EUR — rechtsgrundlagen.md 1.1). */
    public static final Satzstand REFERENZ_2026 = new Satzstand("2026-01",
            new BigDecimal("563.00"), new BigDecimal("506.00"), new BigDecimal("451.00"),
            new BigDecimal("471.00"), new BigDecimal("390.00"), new BigDecimal("357.00"));

    public Satzstand {
        Objects.requireNonNull(gueltigAb, "gueltigAb");
        BigDecimal[] saetze = {rbs1, rbs2, rbs3, rbs4, rbs5, rbs6};
        for (int i = 0; i < saetze.length; i++) {
            Objects.requireNonNull(saetze[i], "rbs" + (i + 1));
            if (saetze[i].signum() <= 0) {
                throw new IllegalArgumentException("Regelbedarf der Stufe " + (i + 1)
                        + " muss positiv sein: " + saetze[i]);
            }
        }
    }

    /** Betrag der Regelbedarfsstufe (1–6); ungültige Stufen werfen fachlich. */
    public BigDecimal regelbedarf(int stufe) {
        return switch (stufe) {
            case 1 -> rbs1;
            case 2 -> rbs2;
            case 3 -> rbs3;
            case 4 -> rbs4;
            case 5 -> rbs5;
            case 6 -> rbs6;
            default -> throw new de.allgemein.lmg.service.FachlicheException(
                    de.allgemein.lmg.service.FachlicheException.RBS_NICHT_KONFIGURIERT,
                    "Regelbedarfsstufe " + stufe + " existiert nicht (gültig: 1 bis 6)");
        };
    }
}
