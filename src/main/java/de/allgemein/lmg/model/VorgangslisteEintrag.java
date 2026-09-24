package de.allgemein.lmg.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
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
        BigDecimal zahlbetragVorschau) {
}
