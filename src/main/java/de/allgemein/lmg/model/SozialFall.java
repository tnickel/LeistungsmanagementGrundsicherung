package de.allgemein.lmg.model;

import java.math.BigDecimal;

/**
 * SozialFall: ein Leistungsberechtigter innerhalb einer Bedarfsgemeinschaft.
 * Trägt alle Parameter der Bedarfs- und Einkommensermittlung (§§ 27a, 30, 82 SGB XII).
 * Die VERSION-Spalte schützt Maskenbearbeitung gegen "Lost Updates" mehrerer
 * Sachbearbeiter (optimistisches Locking).
 *
 * <p>Mehrbedarfsmerkmale (§ 30 SGB XII): Merkzeichen G (Abs. 1), Schwangerschaft
 * ab der 13. Schwangerschaftswoche — nach Vollendung der zwölften Woche (Abs. 2),
 * Alleinerziehende mit Kindern im Haushalt (Abs. 3), dezentrale Warmwassererzeugung
 * (Abs. 7). Absetzbeträge zur
 * Einkommensbereinigung nach § 82 Abs. 2 SGB XII (Steuern, Sozialversicherung,
 * gesetzlich vorgeschriebene/angemessene Versicherungen, Erwerbsaufwendungen)
 * werden vor dem Erwerbsfreibetrag des § 82 Abs. 3 abgezogen.</p>
 *
 * <p>Der Schwangeren-Mehrbedarf läuft nach § 30 Abs. 2 nur bis zum Ende des
 * Entbindungsmonats: {@code entbindungsmonat} (Format YYYY-MM, optional) beendet
     * ihn fachlich — in Bezugsmonaten danach wird nur dieser Mehrbedarf nicht mehr
     * berücksichtigt; die übrige Leistung bleibt berechenbar.</p>
 */
public record SozialFall(
        long id,
        long bgId,
        int regelbedarfsstufe,
        boolean merkzeichenG,
        boolean schwanger,
        int anzKinderUnter7,
        int anzKinderUnter16,
        int anzKinderMinderjaehrig,
        boolean warmwasser,
        BigDecimal kduMiete,
        BigDecimal kduBetriebskosten,
        BigDecimal kduHeizung,
        BigDecimal einkommenRente,
        BigDecimal einkommenErwerb,
        BigDecimal abzSteuern,
        BigDecimal abzSozialversicherung,
        BigDecimal abzVersicherungen,
        BigDecimal abzErwerbsaufwendungen,
        String entbindungsmonat,
        long version) {

    /**
     * Abwärtskompatibler Konstruktor ohne eigene Zählung minderjähriger Kinder
     * und ohne Entbindungsmonat.
     */
    public SozialFall(
            long id,
            long bgId,
            int regelbedarfsstufe,
            boolean merkzeichenG,
            boolean schwanger,
            int anzKinderUnter7,
            int anzKinderUnter16,
            boolean warmwasser,
            BigDecimal kduMiete,
            BigDecimal kduBetriebskosten,
            BigDecimal kduHeizung,
            BigDecimal einkommenRente,
            BigDecimal einkommenErwerb,
            BigDecimal abzSteuern,
            BigDecimal abzSozialversicherung,
            BigDecimal abzVersicherungen,
            BigDecimal abzErwerbsaufwendungen,
            long version) {
        this(id, bgId, regelbedarfsstufe, merkzeichenG, schwanger, anzKinderUnter7, Math.max(anzKinderUnter16, anzKinderUnter7),
                Math.max(anzKinderUnter7, anzKinderUnter16), warmwasser,
                kduMiete, kduBetriebskosten, kduHeizung, einkommenRente, einkommenErwerb,
                abzSteuern, abzSozialversicherung, abzVersicherungen, abzErwerbsaufwendungen,
                null, version);
    }

    /** Abwärtskompatibler Konstruktor ohne Entbindungsmonat (alter kanonischer Stand). */
    public SozialFall(
            long id,
            long bgId,
            int regelbedarfsstufe,
            boolean merkzeichenG,
            boolean schwanger,
            int anzKinderUnter7,
            int anzKinderUnter16,
            int anzKinderMinderjaehrig,
            boolean warmwasser,
            BigDecimal kduMiete,
            BigDecimal kduBetriebskosten,
            BigDecimal kduHeizung,
            BigDecimal einkommenRente,
            BigDecimal einkommenErwerb,
            BigDecimal abzSteuern,
            BigDecimal abzSozialversicherung,
            BigDecimal abzVersicherungen,
            BigDecimal abzErwerbsaufwendungen,
            long version) {
        this(id, bgId, regelbedarfsstufe, merkzeichenG, schwanger, anzKinderUnter7, anzKinderUnter16,
                anzKinderMinderjaehrig, warmwasser,
                kduMiete, kduBetriebskosten, kduHeizung, einkommenRente, einkommenErwerb,
                abzSteuern, abzSozialversicherung, abzVersicherungen, abzErwerbsaufwendungen,
                null, version);
    }

    /** Abwärtskompatibler Konstruktor: Alter Stand ohne Mehrbedarfe/Absetzbeträge. */
    public SozialFall(
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
        this(id, bgId, regelbedarfsstufe, merkzeichenG, false, 0, 0, warmwasser,
                kduMiete, kduBetriebskosten, kduHeizung, einkommenRente, einkommenErwerb,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, version);
    }

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

    /** Summe der Absetzbeträge nach § 82 Abs. 2 SGB XII. */
    public BigDecimal absetzbetaetrag() {
        return abzSteuern.add(abzSozialversicherung).add(abzVersicherungen).add(abzErwerbsaufwendungen);
    }
}
