package de.allgemein.lmg.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Bedarfsgemeinschaft: der geführte Vorgang mit eindeutigem Aktenzeichen.
 * BUDGET_REST ist der verfügbare Restanspruch pro Monat und wird bei jeder
 * angeordneten Auszahlung gemindert; VERSION dient dem optimistischen Locking.
 * Trägt die Standard-Bankverbindung (IBAN, BIC, Kontoinhaber) für Auszahlungen
 * sowie das verwertbare Vermögen für die Vermögensanrechnung (§ 90 SGB XII):
 * VERMOEGEN ist das Gesamtvermögen der Bedarfsgemeinschaft; das Schonvermögen
 * (§ 90 Abs. 2 Nr. 14 SGB XII: 10.000 EUR für die leistungsberechtigte Person
 * zzgl. 750 EUR je weiterem Mitglied) wird in der Berechnung abgezogen.
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
        String kontoinhaber,
        BigDecimal vermoegen) {

    /** Abwärtskompatibler Konstruktor ohne Bankverbindung und Vermögen. */
    public Bedarfsgemeinschaft(
            long id,
            String aktenzeichen,
            String hauptpersonName,
            boolean statusAktiv,
            BigDecimal budgetRest,
            LocalDate wiedervorlageAm,
            long version) {
        this(id, aktenzeichen, hauptpersonName, statusAktiv, budgetRest, wiedervorlageAm,
                version, null, null, null, BigDecimal.ZERO.setScale(2));
    }

    /** Abwärtskompatibler Konstruktor ohne Vermögen. */
    public Bedarfsgemeinschaft(
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
        this(id, aktenzeichen, hauptpersonName, statusAktiv, budgetRest, wiedervorlageAm,
                version, iban, bic, kontoinhaber, BigDecimal.ZERO.setScale(2));
    }
}
