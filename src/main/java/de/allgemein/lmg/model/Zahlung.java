package de.allgemein.lmg.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Angeordnete Auszahlung. Die VORGANGS_NR (UUID aus dem Frontend) macht den
 * Buchungsvorgang idempotent: derselbe Request kann nie doppelt buchen.
 * Status: BEREIT / UEBERGEBEN / STORNIERT.
 */
public record Zahlung(
        long id,
        long bgId,
        Long fallId,
        String vorgangsNr,
        String zahlungsmonat,
        BigDecimal auszahlungsbetrag,
        String status,
        Instant erstelltAm) {
}
