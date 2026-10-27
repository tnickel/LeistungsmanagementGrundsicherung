package de.allgemein.lmg.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Kurz gesagt: Das ist der „Aktenschrumpfal" eines Vorgangs.
 * Eine Bedarfsgemeinschaft ist eine Familie/Haushalt mit eindeutigem Aktenzeichen.
 * Hier stehen nur die wichtigsten Daten drin: Name, Restbudget, Bankverbindung,
 * Vermögen — das Objekt, das zwischen Bildschirm, Berechnung und Datenbank wandert.
 *
 * Bedarfsgemeinschaft: der geführte Vorgang mit eindeutigem Aktenzeichen.
 * BUDGET_REST ist der verfügbare Restanspruch pro Monat und wird bei jeder
 * angeordneten Auszahlung gemindert; VERSION dient dem optimistischen Locking.
 * Trägt die Standard-Bankverbindung (IBAN, BIC, Kontoinhaber) für Auszahlungen
 * sowie das verwertbare Vermögen für die Vermögensanrechnung (§ 90 SGB XII):
 * VERMOEGEN ist das Gesamtvermögen der Bedarfsgemeinschaft; das Schonvermögen
 * (§ 90 Abs. 2 Nr. 9 SGB XII i.V.m. § 1 BarbetragsVO: 10.000 EUR je volljähriger
 * Person zzgl. 500 EUR je überwiegend unterhaltener Person) wird in der Berechnung abgezogen.
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