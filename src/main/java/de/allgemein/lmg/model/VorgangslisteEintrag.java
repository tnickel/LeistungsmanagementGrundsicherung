package de.allgemein.lmg.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Kurz gesagt: Das ist eine Zeile in der Fallübersichts-Liste.
 * Alles, was die Übersicht über einen Vorgang anzeigen soll: Aktenzeichen,
 * Name, Restbudget und eine Vorschau des berechneten Zahlbetrags.
 *
 * Zeile der Fallübersicht: Bedarfsgemeinschaft plus Vorschau des Zahlbetrags
 * (berechnet über alle SozialFälle; null, wenn die Berechnung nicht möglich ist).
 */
public record VorgangslisteEintrag(
        long id,
        String aktenzeichen,
        String hauptpersonName,
        boolean statusAktiv,
        BigDecimal budgetRest,
        LocalDate wiedervorlageAm,
        BigDecimal zahlbetragVorschau,
        String berechnungsmonat) {

    /** Kompatibler Konstruktor für ältere Aufrufer ohne dokumentierten Monatsbezug. */
    public VorgangslisteEintrag(long id, String aktenzeichen, String hauptpersonName,
            boolean statusAktiv, BigDecimal budgetRest, LocalDate wiedervorlageAm,
            BigDecimal zahlbetragVorschau) {
        this(id, aktenzeichen, hauptpersonName, statusAktiv, budgetRest, wiedervorlageAm,
                zahlbetragVorschau, null);
    }
}