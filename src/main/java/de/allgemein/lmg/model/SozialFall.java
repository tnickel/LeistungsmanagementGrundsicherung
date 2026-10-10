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
        boolean warmwasser,
        BigDecimal kduMiete,
        BigDecimal kduBetriebskosten,
        BigDecimal kduHeizung,
        BigDecimal einkommenRente,
        BigDecimal einkommenErwerb,
        long version) {

    /** Abwärtskompatibler Hilfskonstruktor ohne Warmwasser und kalte Betriebskosten. */
    public SozialFall(
            long id,
            long bgId,
            int regelbedarfsstufe,
            boolean merkzeichenG,
            BigDecimal kduMiete,
            BigDecimal kduHeizung,
            BigDecimal einkommenRente,
            BigDecimal einkommenErwerb,
            long version) {
        this(id, bgId, regelbedarfsstufe, merkzeichenG, false, kduMiete,
                BigDecimal.ZERO.setScale(2), kduHeizung, einkommenRente, einkommenErwerb, version);
    }
}
