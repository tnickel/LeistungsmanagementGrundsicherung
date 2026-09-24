package de.allgemein.lmg.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Bedarfsgemeinschaft: der geführte Vorgang mit eindeutigem Aktenzeichen.
 * BUDGET_REST ist der verfügbare Restanspruch pro Monat und wird bei jeder
 * angeordneten Auszahlung gemindert; VERSION dient dem optimistischen Locking.
 */
public record Bedarfsgemeinschaft(
        long id,
        String aktenzeichen,
        String hauptpersonName,
        boolean statusAktiv,
        BigDecimal budgetRest,
        LocalDate wiedervorlageAm,
        long version) {
}
