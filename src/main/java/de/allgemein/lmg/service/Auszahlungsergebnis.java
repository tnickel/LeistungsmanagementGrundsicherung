package de.allgemein.lmg.service;

import de.allgemein.lmg.model.Bedarfsgemeinschaft;
import de.allgemein.lmg.model.Zahlung;

/**
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
