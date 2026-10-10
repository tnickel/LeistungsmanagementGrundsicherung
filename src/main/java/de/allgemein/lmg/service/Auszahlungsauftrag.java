package de.allgemein.lmg.service;

/**
 * Anordnung einer Auszahlung durch den Sachbearbeiter ("Auszahlung anordnen"-Button).
 * Die Vorgangsnummer wird im Frontend erzeugt (UUID) und macht den Vorgang idempotent.
 */
public record Auszahlungsauftrag(
        long bgId,
        String vorgangsNr,
        String zahlungsmonat,
        String sachbearbeiterId,
        boolean vierAugenErforderlich,
        String empfaengerIban,
        String empfaengerBic,
        String empfaengerName) {

    /** Abwärtskompatibler Konstruktor ohne Vier-Augen-Pflicht und ohne abweichende Bankverbindung. */
    public Auszahlungsauftrag(long bgId, String vorgangsNr, String zahlungsmonat, String sachbearbeiterId) {
        this(bgId, vorgangsNr, zahlungsmonat, sachbearbeiterId, false, null, null, null);
    }
}
