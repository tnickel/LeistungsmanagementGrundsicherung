package de.allgemein.lmg.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Bewilligungsbescheid nach § 44 SGB XII für Grundsicherungsleistungen nach SGB XII.
 * Trägt Bewilligungszeitraum, Zahlbetrag des ersten Monats und Status (ERTEILT / AUFGEHOBEN).
 * BERECHNUNG hält den versionierten JSON-Snapshot des Berechnungsergebnisses aus dem
 * Erteilungszeitpunkt fest — der Nachdruck zeigt ausschließlich diesen Stand, auch
 * wenn die zugrunde liegenden SozialFälle später geändert werden. Snapshot-Version 2
 * enthält die verbindlichen Einzelbeträge aller Bewilligungsmonate.
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
        Instant erstelltAm,
        String berechnung) {

    /** Abwärtskompatibler Konstruktor ohne Berechnungs-Snapshot (frühere Versionen). */
    public Bescheid(
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
        this(id, bgId, bescheidNr, bescheidDatum, bewilligungVon, bewilligungBis,
                zahlbetrag, status, erstelltVon, erstelltAm, null);
    }
}
