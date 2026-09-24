package de.allgemein.lmg.model;

import java.math.BigDecimal;

/**
 * SozialFall: ein Leistungsberechtigter innerhalb einer Bedarfsgemeinschaft.
 * Trägt alle Parameter der Bedarfs- und Einkommensermittlung (§§ 27a, 82 SGB XII).
 * Die VERSION-Spalte schützt Maskenbearbeitung gegen "Lost Updates" mehrerer
 * Sachbearbeiter (optimistisches Locking).
 */
public record SozialFall(
        long id,
        long bgId,
        int regelbedarfsstufe,
        boolean merkzeichenG,
        BigDecimal kduMiete,
        BigDecimal kduHeizung,
        BigDecimal einkommenRente,
        BigDecimal einkommenErwerb,
        long version) {
}
