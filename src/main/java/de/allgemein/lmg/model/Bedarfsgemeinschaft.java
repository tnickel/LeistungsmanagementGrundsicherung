package de.allgemein.lmg.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Bedarfsgemeinschaft: der geführte Vorgang mit eindeutigem Aktenzeichen.
 * BUDGET_REST ist der verfügbare Restanspruch pro Monat und wird bei jeder
 * angeordneten Auszahlung gemindert; VERSION dient dem optimistischen Locking.
 * Trägt die Standard-Bankverbindung (IBAN, BIC, Kontoinhaber) für Auszahlungen.
 */
public record Bedarfsgemeinschaft(
        long id,
        String aktenzeichen,
        String hauptpersonName,
        boolean statusAktiv,
        BigDecimal budgetRest,
        LocalDate wiedervorlageAm,
        long version,
        String iban,
        String bic,
        String kontoinhaber) {

    /** Abwärtskompatibler Hilfskonstruktor ohne Bankverbindung. */
    public Bedarfsgemeinschaft(
            long id,
            String aktenzeichen,
            String hauptpersonName,
            boolean statusAktiv,
            BigDecimal budgetRest,
            LocalDate wiedervorlageAm,
            long version) {
        this(id, aktenzeichen, hauptpersonName, statusAktiv, budgetRest, wiedervorlageAm, version, null, null, null);
    }
}
