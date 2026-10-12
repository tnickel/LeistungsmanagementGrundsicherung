package de.allgemein.lmg.calc;

import de.allgemein.lmg.model.Bedarfsgemeinschaft;
import de.allgemein.lmg.model.SozialFall;
import de.allgemein.lmg.service.FachlicheException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Deterministische Berechnung der Grundsicherung im Alter / bei Erwerbsminderung
 * (SGB XII) für das Referenzjahr 2026:
 *
 * <pre>
 *   Regelbedarf     = Betrag der Regelbedarfsstufe (Anlage zu § 28 SGB XII)
 *   Mehrbedarfe § 30:
 *     Merkzeichen G        Abs. 1:  17 % des Regelbedarfs
 *     Schwangerschaft      Abs. 2:  17 % des Regelbedarfs (ab der 13. SSW — Erfassung als Flag)
 *     Alleinerziehend      Abs. 3:  36 % der RBS 1 (Kind &lt; 7 J. oder 2–3 Kinder &lt; 16 J.),
 *                                    sonst 12 % je minderjährigem Kind; Deckel 60 % der RBS 1
 *     Obergrenze           Abs. 6:  Summe der Mehrbedarfe nach Abs. 1–5 höchstens der Regelbedarf
 *     Warmwasser (dezentral) Abs. 7: stufenabhängig 2,3 % / 2,3 % / – / 1,4 % / 1,2 % / 0,8 %
 *                                    (Stufen 1–6; für Stufe 3 sieht das Gesetz keinen Satz vor);
 *                                    nicht von der Obergrenze nach Abs. 6 erfasst
 *   KdU § 42a i.V.m. § 35:  Kaltmiete + kalte Betriebskosten + Heizkosten; die Angemessenheits-
 *                                    grenze gilt für die Summe der Bedarfsgemeinschaft
 *                                    gem. § 35 Abs. 7 (Karenzzeit/Absenkungsfrist sind nicht
 *                                    abgebildet); der Überschuss wird nicht anerkannt und
 *                                    gekennzeichnet (Übernahme unter Vorbehalt). Optionaler
 *                                    örtlicher Heizkosten-Abzug nur bei zentralem Warmwasser
 *                                    (Default 0 = aus)
 *   Einkommen § 82:
 *     Absetzbeträge        Abs. 2:  Steuern, Sozialversicherung, Versicherungen, Erwerbsaufwendungen
 *     Erwerbsfreibetrag    Abs. 3:  min(30 % Brutto-Erwerbseinkommen, 50 % der RBS 1)
 *     Anrechenbar          = Rente + Erwerb − Freibetrag − Absetzbeträge (mindestens 0)
 *   Vermögen § 43 Abs. 1, § 90:
 *     Verwertbar           = Vermögen − Schonvermögen (10.000 EUR je volljähriger Person,
 *                            500 EUR je weiterer unterhaltener Person; § 90 Abs. 2 Nr. 9
 *                            SGB XII i.V.m. § 1 BarbetragsVO)
 *   Zahlbetrag       = Gesamtbedarf − anrechenbares Einkommen − verwertbares Vermögen
 *                      (je Bedarfsgemeinschaft summiert; ≤ 0 bedeutet KEIN_ANSPRUCH)
 * </pre>
 *
 * Die Methode ist reine Funktion ohne Datenbankzugriff — damit vollständig unit-testbar.
 * Werte sind Beträge in EUR mit zwei Nachkommastellen.
 */
public final class Leistungsberechnung {

    /**
     * Regelbedarfsstufen stehen in der Datenbanktabelle SATZSTAND (je Jahr gepflegt,
     * Anlage zu § 28 SGB XII). Der Produktionspfad übergibt den zum Bezugsmonat
     * gültigen {@link Satzstand}; die Kompatibilitätsüberladungen unten nutzen die
     * Seed-Referenz 2026 ({@link Satzstand#REFERENZ_2026}).
     */

    /** Mehrbedarf Merkzeichen G (§ 30 Abs. 1 SGB XII): 17 % des maßgeblichen Regelbedarfs. */
    public static final BigDecimal MEHRBEDARF_G_ANTEIL = new BigDecimal("0.17");

    /** Mehrbedarf Schwangerschaft (§ 30 Abs. 2 SGB XII): 17 % ab der 13. Schwangerschaftswoche („nach der zwölften"). */
    public static final BigDecimal MEHRBEDARF_SCHWANGERE_ANTEIL = new BigDecimal("0.17");

    /** Mehrbedarf Alleinerziehende (§ 30 Abs. 3 SGB XII): 36 % bei Kind unter 7 bzw. 2–3 Kindern unter 16. */
    public static final BigDecimal MEHRBEDARF_ALLEINERZIEHEND_36 = new BigDecimal("0.36");

    /** Mehrbedarf Alleinerziehende (§ 30 Abs. 3 SGB XII): 12 % je weiterem Kind unter 16. */
    public static final BigDecimal MEHRBEDARF_ALLEINERZIEHEND_JE_KIND = new BigDecimal("0.12");

    /** Obergrenze Alleinerziehenden-Mehrbedarf (§ 30 Abs. 3 SGB XII): 60 % der Regelbedarfsstufe 1. */
    public static final BigDecimal MEHRBEDARF_ALLEINERZIEHEND_DECKEL_RBS1 = new BigDecimal("0.60");

    /**
     * Mehrbedarf dezentrale Warmwassererzeugung (§ 30 Abs. 7 SGB XII) je Regelbedarfsstufe:
     * Stufen 1/2 = 2,3 %, Stufe 3 ohne gesetzlichen Satz (0), Stufen 4/5/6 = 1,4/1,2/0,8 %.
     */
    private static final Map<Integer, BigDecimal> MEHRBEDARF_WARMWASSER_ANTEILE = Map.of(
            1, new BigDecimal("0.023"),
            2, new BigDecimal("0.023"),
            3, BigDecimal.ZERO,
            4, new BigDecimal("0.014"),
            5, new BigDecimal("0.012"),
            6, new BigDecimal("0.008"));

    /** Erwerbsfreibetrag: 30 % des Brutto-Erwerbseinkommens (§ 82 Abs. 3 SGB XII). */
    public static final BigDecimal ERWERBSFREIBETRAG_ANTEIL = new BigDecimal("0.30");

    /** … aber hart gedeckelt auf 50 % der Regelbedarfsstufe 1 (2026: 281,50 EUR). */
    public static final BigDecimal ERWERBSFREIBETRAG_KAPPUNG_RBS1 = new BigDecimal("0.50");

    /** Schonvermögen (§ 90 Abs. 2 Nr. 9 SGB XII, § 1 BarbetragsVO): je volljähriger leistungsberechtigter Person. */
    public static final BigDecimal SCHONVERMOEGEN_GRUNDBETRAG = new BigDecimal("10000.00");

    /** Schonvermögen (§ 1 BarbetragsVO): je weiterer, überwiegend unterhaltener Person. */
    public static final BigDecimal SCHONVERMOEGEN_UNTERHALTENE_PERSON = new BigDecimal("500.00");

    private static final int SCALE = 2;

    private Leistungsberechnung() {
    }

    /**
     * Summierte Berechnung über alle SozialFälle einer Bedarfsgemeinschaft;
     * die KdU-Parameter (Warmwasser-Abzug, Angemessenheitsgrenze) sind deaktiviert.
     * Nutzt die Seed-Referenz 2026 — Produktionsaufrufe übergeben den DB-Satzstand.
     */
    public static Berechnungsergebnis berechne(long bgId, List<SozialFall> faelle) {
        return berechne(bgId, faelle, KduParameter.AUS, BigDecimal.ZERO, Satzstand.REFERENZ_2026);
    }

    /**
     * Kompatibilitätsüberladung mit der Seed-Referenz 2026 (testsweet, Tests);
     * Produktionscode ruft die Satzstand-Variante mit dem DB-Stand des Bezugsmonats.
     */
    public static Berechnungsergebnis berechne(
            long bgId, List<SozialFall> faelle, KduParameter kduParameter, BigDecimal vermoegen) {
        return berechne(bgId, faelle, kduParameter, vermoegen, Satzstand.REFERENZ_2026);
    }

    /**
     * Summierte Berechnung über alle SozialFälle einer Bedarfsgemeinschaft inklusive
     * Vermögensanrechnung (§ 43 Abs. 1, § 90 SGB XII). Die Angemessenheitsgrenze gilt für die
     * Summe der KdU der gesamten Bedarfsgemeinschaft (Gesamtangemessenheitsgrenze, § 35 Abs. 7).
     *
     * @param kduParameter örtliche Parameter der KdU-Prüfung (§ 42a SGB XII)
     * @param vermoegen    Gesamtvermögen der Bedarfsgemeinschaft in EUR
     * @param satzstand    zum Bezugsmonat gültige Regelbedarfsstufen (Tabelle SATZSTAND)
     */
    public static Berechnungsergebnis berechne(
            long bgId, List<SozialFall> faelle, KduParameter kduParameter, BigDecimal vermoegen,
            Satzstand satzstand) {
        if (faelle.isEmpty()) {
            throw new FachlicheException(FachlicheException.VALIDATION,
                    "Die Bedarfsgemeinschaft " + bgId + " hat keinen SozialFall");
        }
        BigDecimal gesamtbedarf = BigDecimal.ZERO;
        BigDecimal anrechenbarGesamt = BigDecimal.ZERO;
        List<FallBerechnung> einzeln = new ArrayList<>();
        BigDecimal kduRest = kduParameter.angemessenheitsgrenze().signum() > 0
                ? kduParameter.angemessenheitsgrenze() : null;
        for (SozialFall fall : faelle) {
            FallBerechnung berechnung = berechneFall(fall, kduParameter, kduRest, satzstand);
            einzeln.add(berechnung);
            if (kduRest != null) {
                kduRest = kduRest.subtract(berechnung.kdu());
            }
            gesamtbedarf = gesamtbedarf.add(berechnung.gesamtbedarf());
            anrechenbarGesamt = anrechenbarGesamt.add(berechnung.anrechenbaresEinkommen());
        }
        BigDecimal verwertbaresVermoegen = verwertbaresVermoegen(vermoegen, faelle);
        BigDecimal zahlbetrag = gesamtbedarf.subtract(anrechenbarGesamt)
                .subtract(verwertbaresVermoegen);
        return new Berechnungsergebnis(bgId, List.copyOf(einzeln), gesamtbedarf,
                anrechenbarGesamt, verwertbaresVermoegen, zahlbetrag);
    }

    /** Berechnung für einen einzelnen SozialFall (Details in der Maske Bedarfsermittlung). */
    public static FallBerechnung berechneFall(SozialFall fall) {
        return berechneFall(fall, KduParameter.AUS);
    }

    static FallBerechnung berechneFall(SozialFall fall, KduParameter kduParameter) {
        return berechneFall(fall, kduParameter, Satzstand.REFERENZ_2026);
    }

    static FallBerechnung berechneFall(SozialFall fall, KduParameter kduParameter, Satzstand satzstand) {
        BigDecimal grenze = kduParameter.angemessenheitsgrenze();
        return berechneFall(fall, kduParameter, grenze.signum() > 0 ? grenze : null, satzstand);
    }

    /** @param kduRest noch anerkennbare KdU der Bedarfsgemeinschaft; {@code null} = keine Grenze */
    private static FallBerechnung berechneFall(
            SozialFall fall, KduParameter kduParameter, BigDecimal kduRest, Satzstand satzstand) {
        BigDecimal regelbedarf = satzstand.regelbedarf(fall.regelbedarfsstufe());

        BigDecimal mehrbedarfG = fall.merkzeichenG()
                ? regelbedarf.multiply(MEHRBEDARF_G_ANTEIL).setScale(SCALE, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(SCALE);
        BigDecimal mehrbedarfSchwangere = fall.schwanger()
                ? regelbedarf.multiply(MEHRBEDARF_SCHWANGERE_ANTEIL).setScale(SCALE, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(SCALE);
        BigDecimal mehrbedarfAlleinerziehend = mehrbedarfAlleinerziehend(fall, satzstand);
        BigDecimal mehrbedarfWarmwasser = fall.warmwasser()
                ? regelbedarf.multiply(MEHRBEDARF_WARMWASSER_ANTEILE.get(fall.regelbedarfsstufe()))
                        .setScale(SCALE, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(SCALE);
        // § 30 Abs. 6 SGB XII deckelt nur die Mehrbedarfe nach Abs. 1 bis 5;
        // der Mehrbedarf für dezentrale Warmwassererzeugung (Abs. 7) kommt darüber hinaus.
        BigDecimal mehrbedarfAbs1bis5 = mehrbedarfG.add(mehrbedarfSchwangere)
                .add(mehrbedarfAlleinerziehend).min(regelbedarf);
        BigDecimal mehrbedarf = mehrbedarfAbs1bis5.add(mehrbedarfWarmwasser);

        BigDecimal kduRoh = fall.kduMiete().add(fall.kduBetriebskosten()).add(fall.kduHeizung());
        // Optionaler örtlicher Abzug nur bei zentraler Warmwasserbereitung: nur dort stecken
        // Warmwasserkosten in den Heizkosten (§ 35 Abs. 5). Bei dezentraler Erzeugung gilt
        // stattdessen der Mehrbedarf nach § 30 Abs. 7 — ein Abzug wäre eine Doppelkürzung.
        BigDecimal warmwasserAbzugKdu = !fall.warmwasser()
                ? fall.kduHeizung().multiply(kduParameter.warmwasserAbzugHeizungProzent())
                        .setScale(SCALE, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(SCALE);
        BigDecimal kduOhneWarmwasser = kduRoh.subtract(warmwasserAbzugKdu).max(BigDecimal.ZERO)
                .setScale(SCALE, RoundingMode.HALF_UP);
        // § 42a i.V.m. § 35 SGB XII: unangemessene Aufwendungen nur bis zur Grenze der
        // Bedarfsgemeinschaft; der Überschuss wird nicht als Bedarf anerkannt (Vorbehalt).
        BigDecimal kdu = kduRest == null
                ? kduOhneWarmwasser
                : kduOhneWarmwasser.min(kduRest.max(BigDecimal.ZERO));
        boolean kduUnangemessen = kdu.compareTo(kduOhneWarmwasser) < 0;

        BigDecimal gesamtbedarf = regelbedarf.add(mehrbedarf).add(kdu);

        BigDecimal freibetrag = erwerbsfreibetrag(fall.einkommenErwerb(), satzstand);
        BigDecimal anrechenbar = fall.einkommenRente()
                .add(fall.einkommenErwerb())
                .subtract(freibetrag)
                .subtract(fall.absetzbetaetrag())
                .max(BigDecimal.ZERO)
                .setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal zahlbetrag = gesamtbedarf.subtract(anrechenbar);
        return new FallBerechnung(fall.id(), regelbedarf, mehrbedarfG, mehrbedarfSchwangere,
                mehrbedarfAlleinerziehend, mehrbedarfWarmwasser, mehrbedarf, warmwasserAbzugKdu,
                fall.kduMiete(), fall.kduBetriebskosten(), fall.kduHeizung(), kdu, kduUnangemessen,
                gesamtbedarf, freibetrag, fall.absetzbetaetrag(), anrechenbar, zahlbetrag);
    }

    /**
     * Alleinerziehenden-Mehrbedarf (§ 30 Abs. 3 SGB XII), Bemessungsgrundlage ist stets die
     * Regelbedarfsstufe 1: bei Kind unter 7 oder zwei/drei Kindern unter 16 einmal 36 %
     * (Nr. 1), sonst 12 % je minderjährigem Kind (Nr. 2); gedeckelt auf 60 % der RBS 1.
     */
    private static BigDecimal mehrbedarfAlleinerziehend(SozialFall fall, Satzstand satzstand) {
        int kinderU16 = Math.max(fall.anzKinderUnter16(), fall.anzKinderUnter7());
        int minderjaehrig = Math.max(fall.anzKinderMinderjaehrig(), kinderU16);
        if (minderjaehrig == 0) {
            return BigDecimal.ZERO.setScale(SCALE);
        }
        boolean nr1 = fall.anzKinderUnter7() >= 1 || (kinderU16 >= 2 && kinderU16 <= 3);
        BigDecimal anteil = nr1
                ? MEHRBEDARF_ALLEINERZIEHEND_36
                : MEHRBEDARF_ALLEINERZIEHEND_JE_KIND.multiply(BigDecimal.valueOf(minderjaehrig));
        BigDecimal rbs1 = satzstand.regelbedarf(1);
        BigDecimal betrag = rbs1.multiply(anteil).setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal deckel = rbs1.multiply(MEHRBEDARF_ALLEINERZIEHEND_DECKEL_RBS1)
                .setScale(SCALE, RoundingMode.HALF_UP);
        return betrag.min(deckel);
    }

    /**
     * Schonvermögen der Bedarfsgemeinschaft (§ 90 Abs. 2 Nr. 9 SGB XII i.V.m. § 1
     * Barbetragsverordnung): 10.000 EUR je volljähriger leistungsberechtigter Person
     * (Regelbedarfsstufen 1 bis 3) und 500 EUR je weiterer, überwiegend unterhaltener Person
     * (Regelbedarfsstufen 4 bis 6). Ohne Erwachsene gilt der Grundbetrag einmal.
     */
    public static BigDecimal schonvermoegen(List<SozialFall> faelle) {
        long erwachsene = faelle.stream().filter(f -> f.regelbedarfsstufe() <= 3).count();
        long unterhaltene = faelle.size() - erwachsene;
        BigDecimal summe = SCHONVERMOEGEN_GRUNDBETRAG.multiply(BigDecimal.valueOf(erwachsene))
                .add(SCHONVERMOEGEN_UNTERHALTENE_PERSON.multiply(BigDecimal.valueOf(unterhaltene)));
        if (erwachsene == 0) {
            summe = SCHONVERMOEGEN_GRUNDBETRAG;
        }
        return summe.setScale(SCALE, RoundingMode.HALF_UP);
    }

    /** Verwertbares Vermögen: Vermögen abzüglich Schonvermögen der Bedarfsgemeinschaft. */
    static BigDecimal verwertbaresVermoegen(BigDecimal vermoegen, List<SozialFall> faelle) {
        return vermoegen.subtract(schonvermoegen(faelle))
                .max(BigDecimal.ZERO)
                .setScale(SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal erwerbsfreibetrag(BigDecimal erwerbseinkommen, Satzstand satzstand) {
        BigDecimal kappung = satzstand.regelbedarf(1).multiply(ERWERBSFREIBETRAG_KAPPUNG_RBS1)
                .setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal anteilig = erwerbseinkommen.multiply(ERWERBSFREIBETRAG_ANTEIL)
                .setScale(SCALE, RoundingMode.HALF_UP);
        return anteilig.min(kappung);
    }

    /** Örtliche Parameter der KdU-Prüfung (§ 42a i.V.m. § 35 SGB XII); Default: deaktiviert. */
    public record KduParameter(
            BigDecimal warmwasserAbzugHeizungProzent,
            BigDecimal angemessenheitsgrenze) {

        /** Prüfung deaktiviert: kein Warmwasser-Abzug, keine Angemessenheitsgrenze. */
        public static final KduParameter AUS =
                new KduParameter(BigDecimal.ZERO, BigDecimal.ZERO);

        public KduParameter {
            java.util.Objects.requireNonNull(warmwasserAbzugHeizungProzent, "warmwasserAbzugHeizungProzent");
            java.util.Objects.requireNonNull(angemessenheitsgrenze, "angemessenheitsgrenze");
            if (warmwasserAbzugHeizungProzent.signum() < 0
                    || warmwasserAbzugHeizungProzent.compareTo(BigDecimal.ONE) > 0) {
                throw new IllegalArgumentException(
                        "warmwasserAbzugHeizungProzent muss zwischen 0 und 1 liegen: "
                                + warmwasserAbzugHeizungProzent);
            }
            if (angemessenheitsgrenze.signum() < 0) {
                throw new IllegalArgumentException(
                        "angemessenheitsgrenze darf nicht negativ sein: " + angemessenheitsgrenze);
            }
        }
    }

    /** Ergebnis für einen SozialFall; alle Beträge EUR mit zwei Nachkommastellen. */
    public record FallBerechnung(
            long fallId,
            BigDecimal regelbedarf,
            BigDecimal mehrbedarfG,
            BigDecimal mehrbedarfSchwangere,
            BigDecimal mehrbedarfAlleinerziehend,
            BigDecimal mehrbedarfWarmwasser,
            BigDecimal mehrbedarf,
            BigDecimal kduWarmwasserAbzug,
            BigDecimal kduMiete,
            BigDecimal kduBetriebskosten,
            BigDecimal kduHeizung,
            BigDecimal kdu,
            boolean kduUnangemessen,
            BigDecimal gesamtbedarf,
            BigDecimal freibetragErwerb,
            BigDecimal absetzbetaetrag,
            BigDecimal anrechenbaresEinkommen,
            BigDecimal zahlbetrag) {

        /** Abwärtskompatibler Konstruktor für die bisherige aufgeschlüsselte Signatur. */
        public FallBerechnung(
                long fallId,
                BigDecimal regelbedarf,
                BigDecimal mehrbedarfG,
                BigDecimal mehrbedarfWarmwasser,
                BigDecimal mehrbedarf,
                BigDecimal kduMiete,
                BigDecimal kduBetriebskosten,
                BigDecimal kduHeizung,
                BigDecimal kdu,
                BigDecimal gesamtbedarf,
                BigDecimal freibetragErwerb,
                BigDecimal anrechenbaresEinkommen,
                BigDecimal zahlbetrag) {
            this(fallId, regelbedarf, mehrbedarfG, BigDecimal.ZERO, BigDecimal.ZERO,
                    mehrbedarfWarmwasser, mehrbedarf, BigDecimal.ZERO,
                    kduMiete, kduBetriebskosten, kduHeizung, kdu, false,
                    gesamtbedarf, freibetragErwerb, BigDecimal.ZERO.setScale(2),
                    anrechenbaresEinkommen, zahlbetrag);
        }

        /** Abwärtskompatibler Konstruktor für die ursprüngliche 8-stellige Signatur. */
        public FallBerechnung(
                long fallId,
                BigDecimal regelbedarf,
                BigDecimal mehrbedarf,
                BigDecimal kdu,
                BigDecimal gesamtbedarf,
                BigDecimal freibetragErwerb,
                BigDecimal anrechenbaresEinkommen,
                BigDecimal zahlbetrag) {
            this(fallId, regelbedarf, mehrbedarf, BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2),
                    BigDecimal.ZERO.setScale(2), mehrbedarf, BigDecimal.ZERO.setScale(2),
                    kdu, BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2), kdu, false,
                    gesamtbedarf, freibetragErwerb, BigDecimal.ZERO.setScale(2),
                    anrechenbaresEinkommen, zahlbetrag);
        }
    }

    /** Ergebnis je Bedarfsgemeinschaft: Summen über alle SozialFälle plus endgültiger Zahlbetrag. */
    public record Berechnungsergebnis(
            long bgId,
            List<FallBerechnung> faelle,
            BigDecimal gesamtbedarf,
            BigDecimal anrechenbaresEinkommen,
            BigDecimal verwertbaresVermoegen,
            BigDecimal zahlbetrag) {

        /** Abwärtskompatibler Konstruktor ohne Vermögensanrechnung. */
        public Berechnungsergebnis(
                long bgId,
                List<FallBerechnung> faelle,
                BigDecimal gesamtbedarf,
                BigDecimal anrechenbaresEinkommen,
                BigDecimal zahlbetrag) {
            this(bgId, faelle, gesamtbedarf, anrechenbaresEinkommen,
                    BigDecimal.ZERO.setScale(2), zahlbetrag);
        }
    }
}
