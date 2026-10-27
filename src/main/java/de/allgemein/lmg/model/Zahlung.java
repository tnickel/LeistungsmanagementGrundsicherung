package de.allgemein.lmg.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Kurz gesagt: Das ist eine angeordnete Auszahlung als Objekt.
 * Sie hält fest: Wer bekommt wie viel für welchen Monat, wer hat sie angeordnet,
 * wer hat sie freigegeben — und in welchem Stand sie gerade ist (angelegt, bereit,
 * übergeben, storniert).
 *
 * <p>{@code vermoegenseinsatz} dokumentiert den bei der Anordnung verbrauchten
 * verwertbaren Vermögensanteil (§ 90 SGB XII, Verbrauchsfortschreibung): Das
 * Storno bucht genau diesen Betrag neben dem Restbudget auch ins Vermögen zurück.</p>
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
        String empfaengerName,
        BigDecimal vermoegenseinsatz) {

    /** Abwärtskompatibler Konstruktor ohne Vermögenseinsatz (Einsatz 0). */
    public Zahlung(
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
        this(id, bgId, fallId, vorgangsNr, zahlungsmonat, auszahlungsbetrag, status, erstelltAm,
                sachbearbeiterId, freigegebenVon, empfaengerIban, empfaengerBic, empfaengerName,
                BigDecimal.ZERO.setScale(2));
    }

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