package de.allgemein.lmg.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Angeordnete Auszahlung. Die VORGANGS_NR (UUID aus dem Frontend) macht den
 * Buchungsvorgang idempotent: derselbe Request kann nie doppelt buchen.
 * Status: ANGELEGT / BEREIT / UEBERGEBEN / STORNIERT.
 * Trägt Erfasser, Freigebenden (Vier-Augen-Prinzip) und SEPA-Zahlungsempfängerdaten.
 */
public record Zahlung(
        long id,
        long bgId,
        Long fallId,
        String vorgangsNr,
        String zahlungsmonat,
        BigDecimal auszahlungsbetrag,
        String status,
        Instant erstelltAm,
        String sachbearbeiterId,
        String freigegebenVon,
        String empfaengerIban,
        String empfaengerBic,
        String empfaengerName) {

    /** Abwärtskompatibler Hilfskonstruktor. */
    public Zahlung(
            long id,
            long bgId,
            Long fallId,
            String vorgangsNr,
            String zahlungsmonat,
            BigDecimal auszahlungsbetrag,
            String status,
            Instant erstelltAm) {
        this(id, bgId, fallId, vorgangsNr, zahlungsmonat, auszahlungsbetrag, status, erstelltAm,
                null, null, null, null, null);
    }
}
