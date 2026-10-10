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
 *     Schwangerschaft      Abs. 2:  17 % des Regelbedarfs (ab 12. SSW — Erfassung als Flag)
 *     Alleinerziehend      Abs. 3:  36 % (Kind &lt; 7 J. oder 2–3 Kinder &lt; 16 J. im Haushalt),
 *                                    sonst 12 % je Kind &lt; 16 J.; Deckel 60 % der RBS 1
 *     Warmwasser (dezentral) Abs. 7: stufenabhängig 2,3 % / 2,3 % / – / 1,4 % / 1,2 % / 0,8 %
 *                                    (Stufen 1–6; für Stufe 3 sieht das Gesetz keinen Satz vor)
 *     Obergrenze           Abs. 6:  Summe der Mehrbedarfe höchstens der Regelbedarf selbst
 *   KdU § 42a i.V.m. § 35:  Kaltmiete + kalte Betriebskosten + Heizkosten; bei dezentraler
 *                                    Warmwassererzeugung örtlicher Abzug von den Heizkosten
 *                                    (konfigurierbar, Default 0 = aus); unangemessene Aufwendungen
 *                                    werden nur bis zur Angemessenheitsgrenze anerkannt und
 *                                    im Ergebnis gekennzeichnet (Übernahme unter Vorbehalt)
 *   Einkommen § 82:
 *     Absetzbeträge        Abs. 2:  Steuern, Sozialversicherung, Versicherungen, Erwerbsaufwendungen
 *     Erwerbsfreibetrag    Abs. 3:  min(30 % Brutto-Erwerbseinkommen, 50 % der RBS 1)
 *     Anrechenbar          = Rente + Erwerb − Freibetrag − Absetzbeträge (mindestens 0)
 *   Vermögen § 43 Abs. 3, § 90:
 *     Verwertbar           = Vermögen − Schonvermögen (10.000 EUR + 750 EUR je weiterem
 *                            Mitglied der Bedarfsgemeinschaft; § 90 Abs. 2 Nr. 14 SGB XII)
 *   Zahlbetrag       = Gesamtbedarf − anrechenbares Einkommen − verwertbares Vermögen
 *                      (je Bedarfsgemeinschaft summiert; ≤ 0 bedeutet KEIN_ANSPRUCH)
 * </pre>
 *
 * Die Methode ist reine Funktion ohne Datenbankzugriff — damit vollständig unit-testbar.
 * Werte sind Beträge in EUR mit zwei Nachkommastellen.
 */
public final class Leistungsberechnung {

    /** Regelbedarfsstufen 2026 (Stufen 1 bis 6 nach Regelbedarfsstufen-Fortschreibungsverordnung). */
    private static final Map<Integer, BigDecimal> REGELBEDARF_2026 = Map.of(
            1, new BigDecimal("563.00"),
            2, new BigDecimal("506.00"),
            3, new BigDecimal("451.00"),
            4, new BigDecimal("471.00"),
            5, new BigDecimal("390.00"),
            6, new BigDecimal("357.00"));

    /** Mehrbedarf Merkzeichen G (§ 30 Abs. 1 SGB XII): 17 % des maßgeblichen Regelbedarfs. */
    public static final BigDecimal MEHRBEDARF_G_ANTEIL = new BigDecimal("0.17");

    /** Mehrbedarf Schwangerschaft (§ 30 Abs. 2 SGB XII): 17 % ab der 12. Schwangerschaftswoche. */
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

    /** Schonvermögen (§ 90 Abs. 2 Nr. 14 SGB XII): Grundbetrag der leistungsberechtigten Person. */
    public static final BigDecimal SCHONVERMOEGEN_GRUNDBETRAG = new BigDecimal("10000.00");

    /** Schonvermögen (§ 90 Abs. 2 Nr. 14 SGB XII): je weiterem Mitglied der Bedarfsgemeinschaft. */
    public static final BigDecimal SCHONVERMOEGEN_JE_MITGLIED = new BigDecimal("750.00");

    private static final int SCALE = 2;

    private Leistungsberechnung() {
    }

    /**
     * Summierte Berechnung über alle SozialFälle einer Bedarfsgemeinschaft;
     * die KdU-Parameter (Warmwasser-Abzug, Angemessenheitsgrenze) sind deaktiviert.
     */
    public static Berechnungsergebnis berechne(long bgId, List<SozialFall> faelle) {
        return berechne(bgId, faelle, KduParameter.AUS, BigDecimal.ZERO, 1);
    }

    /**
     * Summierte Berechnung über alle SozialFälle einer Bedarfsgemeinschaft inklusive
     * Vermögensanrechnung (§ 43 Abs. 3, § 90 SGB XII).
     *
     * @param kduParameter   örtliche Parameter der KdU-Prüfung (§ 42a SGB XII)
     * @param vermoegen      Gesamtvermögen der Bedarfsgemeinschaft in EUR
     * @param anzahlMitglieder Anzahl Mitglieder der Bedarfsgemeinschaft (für das Schonvermögen)
     */
    public static Berechnungsergebnis berechne(
            long bgId, List<SozialFall> faelle, KduParameter kduParameter,
            BigDecimal vermoegen, int anzahlMitglieder) {
        if (faelle.isEmpty()) {
            throw new FachlicheException(FachlicheException.VALIDATION,
                    "Die Bedarfsgemeinschaft " + bgId + " hat keinen SozialFall");
        }
        BigDecimal gesamtbedarf = BigDecimal.ZERO;
        BigDecimal anrechenbarGesamt = BigDecimal.ZERO;
        List<FallBerechnung> einzeln = new ArrayList<>();
        for (SozialFall fall : faelle) {
            FallBerechnung berechnung = berechneFall(fall, kduParameter);
            einzeln.add(berechnung);
            gesamtbedarf = gesamtbedarf.add(berechnung.gesamtbedarf());
            anrechenbarGesamt = anrechenbarGesamt.add(berechnung.anrechenbaresEinkommen());
        }
        BigDecimal verwertbaresVermoegen = verwertbaresVermoegen(vermoegen, anzahlMitglieder);
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
        BigDecimal regelbedarf = regelbedarf(fall.regelbedarfsstufe());

        BigDecimal mehrbedarfG = fall.merkzeichenG()
                ? regelbedarf.multiply(MEHRBEDARF_G_ANTEIL).setScale(SCALE, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(SCALE);
        BigDecimal mehrbedarfSchwangere = fall.schwanger()
                ? regelbedarf.multiply(MEHRBEDARF_SCHWANGERE_ANTEIL).setScale(SCALE, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(SCALE);
        BigDecimal mehrbedarfAlleinerziehend = mehrbedarfAlleinerziehend(fall, regelbedarf);
        BigDecimal mehrbedarfWarmwasser = fall.warmwasser()
                ? regelbedarf.multiply(MEHRBEDARF_WARMWASSER_ANTEILE.get(fall.regelbedarfsstufe()))
                        .setScale(SCALE, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(SCALE);
        BigDecimal mehrbedarfRoeh = mehrbedarfG.add(mehrbedarfSchwangere)
                .add(mehrbedarfAlleinerziehend).add(mehrbedarfWarmwasser);
        // § 30 Abs. 6 SGB XII: Mehrbedarfe übersteigen nicht den Regelbedarf selbst.
        BigDecimal mehrbedarf = mehrbedarfRoeh.min(regelbedarf);

        BigDecimal kduRoh = fall.kduMiete().add(fall.kduBetriebskosten()).add(fall.kduHeizung());
        BigDecimal warmwasserAbzugKdu = fall.warmwasser()
                ? fall.kduHeizung().multiply(kduParameter.warmwasserAbzugHeizungProzent())
                        .setScale(SCALE, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(SCALE);
        BigDecimal kduOhneWarmwasser = kduRoh.subtract(warmwasserAbzugKdu).max(BigDecimal.ZERO);
        boolean kduUnangemessen = kduParameter.angemessenheitsgrenze().signum() > 0
                && kduOhneWarmwasser.compareTo(kduParameter.angemessenheitsgrenze()) > 0;
        // § 42a i.V.m. § 35 SGB XII: unangemessene Aufwendungen nur bis zur Grenze;
        // der Überschuss wird unter Vorbehalt der Anpassung nicht als Bedarf anerkannt.
        BigDecimal kdu = kduUnangemessen ? kduParameter.angemessenheitsgrenze() : kduOhneWarmwasser;

        BigDecimal gesamtbedarf = regelbedarf.add(mehrbedarf).add(kdu);

        BigDecimal freibetrag = erwerbsfreibetrag(fall.einkommenErwerb());
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
     * Alleinerziehenden-Mehrbedarf (§ 30 Abs. 3 SGB XII). Vereinfachung: bei erfüllter
     * Konstellation (Kind unter 7 oder zwei/drei Kinder unter 16 im Haushalt) einmal 36 %,
     * sonst 12 % je Kind unter 16 — ohne Kumulation beider Sätze; gedeckelt auf 60 % der RBS 1.
     */
    private static BigDecimal mehrbedarfAlleinerziehend(SozialFall fall, BigDecimal regelbedarf) {
        int kinderU16 = Math.max(fall.anzKinderUnter16(), fall.anzKinderUnter7());
        if (kinderU16 == 0) {
            return BigDecimal.ZERO.setScale(SCALE);
        }
        boolean konstellation36 = fall.anzKinderUnter7() >= 1 || (kinderU16 >= 2 && kinderU16 <= 3);
        BigDecimal anteil = konstellation36
                ? MEHRBEDARF_ALLEINERZIEHEND_36
                : MEHRBEDARF_ALLEINERZIEHEND_JE_KIND.multiply(BigDecimal.valueOf(kinderU16));
        BigDecimal betrag = regelbedarf.multiply(anteil).setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal deckel = REGELBEDARF_2026.get(1)
                .multiply(MEHRBEDARF_ALLEINERZIEHEND_DECKEL_RBS1)
                .setScale(SCALE, RoundingMode.HALF_UP);
        return betrag.min(deckel);
    }

    /** Verwertbares Vermögen: Vermögen abzüglich Schonvermögen (§ 90 Abs. 2 Nr. 14 SGB XII). */
    static BigDecimal verwertbaresVermoegen(BigDecimal vermoegen, int anzahlMitglieder) {
        BigDecimal schonvermoegen = SCHONVERMOEGEN_GRUNDBETRAG
                .add(SCHONVERMOEGEN_JE_MITGLIED.multiply(
                        BigDecimal.valueOf(Math.max(0, anzahlMitglieder - 1))));
        return vermoegen.subtract(schonvermoegen)
                .max(BigDecimal.ZERO)
                .setScale(SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal regelbedarf(int rbs) {
        BigDecimal betrag = REGELBEDARF_2026.get(rbs);
        if (betrag == null) {
            throw new FachlicheException(FachlicheException.RBS_NICHT_KONFIGURIERT,
                    "Regelbedarfsstufe " + rbs + " ist für das Referenzjahr 2026 nicht hinterlegt "
                            + "(konfiguriert sind die Stufen 1 bis 6)");
        }
        return betrag;
    }

    private static BigDecimal erwerbsfreibetrag(BigDecimal erwerbseinkommen) {
        BigDecimal kappung = REGELBEDARF_2026.get(1).multiply(ERWERBSFREIBETRAG_KAPPUNG_RBS1)
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
