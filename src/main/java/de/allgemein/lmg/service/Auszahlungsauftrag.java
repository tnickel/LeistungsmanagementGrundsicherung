package de.allgemein.lmg.service;

/**
 * Anordnung einer Auszahlung durch den Sachbearbeiter ("Auszahlung anordnen"-Button).
 * Die Vorgangsnummer wird im Frontend erzeugt (UUID) und macht den Vorgang idempotent.
 */
public record Auszahlungsauftrag(
        long bgId,
        String vorgangsNr,
        String zahlungsmonat,
        String sachbearbeiterId) {
}
