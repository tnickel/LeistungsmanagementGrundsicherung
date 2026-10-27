package de.allgemein.lmg.service;

import de.allgemein.lmg.model.Bedarfsgemeinschaft;
import de.allgemein.lmg.model.Zahlung;

/**
 * Kurz gesagt: Das ist die Quittung nach einer Auszahlungsanordnung.
 * Sie zeigt: die gebuchte Zahlung, den neuen Stand des Vorgangs — und ob
 * die Zahlung jetzt frisch gebucht wurde oder schon da war (Doppelklick-Falle).
 *
 * Ergebnis einer Auszahlungsanordnung: die gebuchte (oder bei Wiederholung
 * bereits existierende) Zahlung samt neuem Stand der Bedarfsgemeinschaft.
 * {@code bereitsGebucht} = true bedeutet: Vorgangsnummer war schon vorhanden,
 * es wurde nichts erneut gebucht (Idempotenz).
 */
public record Auszahlungsergebnis(
        Zahlung zahlung,
        Bedarfsgemeinschaft bedarfsgemeinschaft,
        boolean bereitsGebucht) {
}