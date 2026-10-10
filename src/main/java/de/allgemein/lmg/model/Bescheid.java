package de.allgemein.lmg.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Bewilligungsbescheid nach § 37 SGB X für Grundsicherungsleistungen nach SGB XII.
 * Trägt Bewilligungszeitraum, monatlichen Zahlbetrag und Status (ERTEILT / AUFGEHOBEN).
 */
public record Bescheid(
        long id,
        long bgId,
        String bescheidNr,
        LocalDate bescheidDatum,
        String bewilligungVon,
        String bewilligungBis,
        BigDecimal zahlbetrag,
        String status,
        String erstelltVon,
        Instant erstelltAm) {
}
