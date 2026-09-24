package de.allgemein.lmg.model;

import java.time.Instant;

/**
 * Eintrag im revisionsicheren Buchungsjournal (Wer hat wann was angeordnet?).
 * Die Referenz auf die Bedarfsgemeinschaft ist bewusst nur logisch (kein FK),
 * damit Einträge auch nach Historisierung des Vorgangs erhalten bleiben.
 */
public record Journaleintrag(
        long id,
        Long bgId,
        String aktionText,
        String sachbearbeiterId,
        Instant erstelltAm) {
}
