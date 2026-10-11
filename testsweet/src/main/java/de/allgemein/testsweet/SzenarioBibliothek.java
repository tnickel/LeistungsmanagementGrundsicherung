package de.allgemein.testsweet;

import de.allgemein.lmg.calc.Leistungsberechnung;
import de.allgemein.lmg.model.SozialFall;
import de.allgemein.lmg.service.Auszahlungsauftrag;
import de.allgemein.lmg.service.Auszahlungsergebnis;
import de.allgemein.lmg.service.FachlicheException;
import de.allgemein.lmg.service.SozialLeistungsService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Erzeugt den Auftragstopf: Anwenderszenarien mit gestreuten Parametern und der
 * vollständigen Erwartungshaltung an den Datenbank-Endzustand. Die erwarteten
 * Zahlbeträge werden vorab mit derselben puren Berechnung
 * ({@link Leistungsberechnung}, KdU-Prüfung aus wie im Service-Standard)
 * ermittelt, mit der später die Fachschicht rechnet — Abweichungen in der
 * Datenbank können also nur aus der Persistierung/Parallelität entstehen.
 *
 * <p>Mit festem Seed ist jeder Lauf reproduzierbar (gleiche Szenarien in gleicher
 * Reihenfolge); ohne Seed (0) wird ein zufälliger gewählt und protokolliert.</p>
 */
public final class SzenarioBibliothek {

    /** Aktenzeichen-Präfix aller Simulator-Vorgänge (auch fürs Aufräumen). */
    public static final String PREFIX = "SWEET-";

    private static final String[] VORNAMEN = {
            "Anna", "Bernd", "Clara", "Dieter", "Elke", "Frank", "Gisela", "Heinz",
            "Ingrid", "Jens", "Karin", "Ludwig", "Marlene", "Norbert", "Olga", "Paul",
            "Renate", "Stefan", "Ursula", "Volkmar", "Waltraud", "Willi"};
    private static final String[] NACHNAMEN = {
            "Brandt", "Dietrich", "Egger", "Fischer", "Gross", "Hoffmann", "Ismail",
            "Jung", "Krause", "Lindner", "Meyer", "Neumann", "Otto", "Petersen",
            "Quest", "Roth", "Sauber", "Thiel", "Uhl", "Vogt", "Wagner", "Zimmer"};

    private final Random random;

    public SzenarioBibliothek(long seed) {
        this.random = new Random(seed);
    }

    /** Erzeugt {@code anzahl} Aufträge, gleichmäßig über die Szenario-Arten gestreut. */
    public List<Auftrag> erzeuge(int anzahl) {
        List<Auftrag> auftraege = new ArrayList<>(anzahl);
        for (int i = 1; i <= anzahl; i++) {
            int wahl = random.nextInt(100);
            if (wahl < 22) {
                auftraege.add(standardrente(i));
            } else if (wahl < 32) {
                auftraege.add(paar(i));
            } else if (wahl < 42) {
                auftraege.add(mehrbedarf(i));
            } else if (wahl < 52) {
                auftraege.add(familie(i));
            } else if (wahl < 62) {
                auftraege.add(erwerbstaetiger(i));
            } else if (wahl < 72) {
                auftraege.add(vermoegensfall(i));
            } else if (wahl < 79) {
                auftraege.add(doppelklick(i));
            } else if (wahl < 89) {
                auftraege.add(stornoMitKorrektur(i));
            } else if (wahl < 95) {
                auftraege.add(konkurrenz(i));
            } else {
                auftraege.add(kassenlauf(i));
            }
        }
        return auftraege;
    }

    // ------------------------------------------------------------------
    // Szenario-Arten
    // ------------------------------------------------------------------

    private Auftrag standardrente(int nr) {
        int rbs = 1 + random.nextInt(3);
        BigDecimal miete = zufallsBetrag(250, 550, 10);
        BigDecimal betrieb = zufallsBetrag(30, 120, 5);
        BigDecimal heizung = zufallsBetrag(50, 140, 5);
        List<SozialFall> faelle = List.of(fallEntwurf(rbs, false, false, 0, false,
                miete, betrieb, heizung, "0", "0"));
        List<Rechenblock> rechnungen = new ArrayList<>();
        BigDecimal zahlbetrag = berechne(rechnungen, "Anordnung der monatlichen Auszahlung",
                faelle, BigDecimal.ZERO, null,
                "Vier-Augen-Prinzip: Erst die Freigabe einer zweiten Kraft macht die Zahlung "
                        + "wirksam (Status BEREIT); sie wird durch die Kollegin/den Kollegen erteilt.");
        BigDecimal budget = zahlbetrag.add(zufallsBetrag(100, 300, 10));
        rechnungen.replaceAll(b -> b.mitBudget(budget));
        String name = hauptperson();
        String az = aktenzeichen(nr);

        List<SzenarioAktion> aktionen = List.of(
                SzenarioAktion.schritt("Vorgang anlegen", (s, ich, kollege, ctx) -> {
                    var bg = s.legeBedarfsgemeinschaftAn(az, name, budget, LocalDate.now().plusDays(30),
                            "DE89370400440532013000", "MARKDEF1100", name, ich);
                    ctx.put("bgId", bg.id());
                    return "Vorgang " + bg.id() + " mit Budget " + budget.toPlainString() + " EUR";
                }),
                SzenarioAktion.schritt("SozialFall erfassen (RBS " + rbs + ")", (s, ich, kollege, ctx) -> {
                    s.legeSozialFallAn((Long) ctx.get("bgId"), fallEntwurf(rbs, false, false, 0, false,
                            miete, betrieb, heizung, "0", "0"), ich);
                    return "RBS " + rbs + ", KdU " + gesamtKdu(miete, betrieb, heizung) + " EUR";
                }),
                SzenarioAktion.schritt("Auszahlung anordnen (2026-10)", (s, ich, kollege, ctx) -> {
                    var erg = s.ordneAuszahlungAn(new Auszahlungsauftrag((Long) ctx.get("bgId"),
                            UUID.randomUUID().toString(), "2026-10", ich));
                    ctx.put("zahlungId", erg.zahlung().id());
                    return "Zahlung " + erg.zahlung().id() + " über "
                            + erg.zahlung().auszahlungsbetrag().toPlainString() + " EUR (ANGELEGT)";
                }),
                SzenarioAktion.schritt("Freigabe durch Kollegen (Vier-Augen)", (s, ich, kollege, ctx) -> {
                    s.freigebenZahlung((Long) ctx.get("zahlungId"), kollege);
                    return "freigegeben durch " + kollege;
                }));

        Long bg = null;
        return new Auftrag(nr, "STANDARDRENTE", az, name,
                "AlleinstehendeR, RBS " + rbs + ", Anordnung + Vier-Augen-Freigabe",
                aktionen, erwartungenStandard(zahlbetrag, budget, rbs, true))
                .dokumentiere("Ein alleinstehender Mensch beantragt Hilfe zum Lebensunterhalt. "
                        + "Der Sachbearbeiter legt zuerst den Vorgang mit Aktenzeichen, Startbudget "
                        + "und SEPA-Bankverbindung an; danach erfasst er die Person als SozialFall "
                        + "mit Regelbedarfsstufe, Wohnkosten (Kaltmiete, Betriebskosten, Heizung) "
                        + "und Renteneinkommen. Dann ordnet er die monatliche Auszahlung an — sie "
                        + "bleibt zunächst im Status ANGELEGT. Eine zweite, zufällig gewählte Kraft "
                        + "prüft und gibt frei (Vier-Augen-Prinzip), erst damit wird die Zahlung "
                        + "wirksam (BEREIT). Geprüft wird am Ende, ob Auszahlungsbetrag "
                        + "(Regelbedarf + Wohnkosten), Freigabevermerk (Erfasser und Freigebender "
                        + "müssen verschiedene Kürzel tragen) und das geminderte Restbudget exakt "
                        + "in der Datenbank stehen. Ein Fehler wäre etwa ein falscher Betrag, eine "
                        + "fehlende Budgetminderung oder ein doppelter Journaleintrag.", rechnungen);
    }

    private Auftrag paar(int nr) {
        BigDecimal miete = zufallsBetrag(400, 700, 10);
        BigDecimal betrieb = zufallsBetrag(40, 130, 5);
        BigDecimal heizung = zufallsBetrag(60, 150, 5);
        List<SozialFall> faelle = List.of(
                fallEntwurf(2, false, false, 0, false, miete, betrieb, heizung, "0", "0"),
                fallEntwurf(2, false, false, 0, false, BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, "0", "0"));
        List<Rechenblock> rechnungen = new ArrayList<>();
        BigDecimal zahlbetrag = berechne(rechnungen, "Summierung über beide Partner",
                faelle, BigDecimal.ZERO, null,
                "Ehepartner: Jeder erhält seinen Regelbedarf der Stufe 2; die Wohnkosten "
                        + "(KdU) werden nur einmal anerkannt — am ersten Fall.");
        BigDecimal budget = zahlbetrag.add(zufallsBetrag(100, 300, 10));
        rechnungen.replaceAll(b -> b.mitBudget(budget));
        String name = hauptperson();
        String az = aktenzeichen(nr);

        List<SzenarioAktion> aktionen = List.of(
                SzenarioAktion.schritt("Vorgang anlegen (Paar)", (s, ich, kollege, ctx) -> {
                    var bg = s.legeBedarfsgemeinschaftAn(az, name, budget, null, ich);
                    ctx.put("bgId", bg.id());
                    return "Vorgang " + bg.id();
                }),
                SzenarioAktion.schritt("SozialFall Partner 1 + 2 (beide RBS 2)", (s, ich, kollege, ctx) -> {
                    long bgId = (Long) ctx.get("bgId");
                    s.legeSozialFallAn(bgId, fallEntwurf(2, false, false, 0, false,
                            miete, betrieb, heizung, "0", "0"), ich);
                    s.legeSozialFallAn(bgId, fallEntwurf(2, false, false, 0, false,
                            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "0", "0"), ich);
                    return "2 Personen, KdU am ersten Fall";
                }),
                SzenarioAktion.schritt("Auszahlung anordnen", (s, ich, kollege, ctx) -> {
                    var erg = s.ordneAuszahlungAn(new Auszahlungsauftrag((Long) ctx.get("bgId"),
                            UUID.randomUUID().toString(), "2026-10", ich));
                    ctx.put("zahlungId", erg.zahlung().id());
                    return erg.zahlung().auszahlungsbetrag().toPlainString() + " EUR (ANGELEGT)";
                }));
        return new Auftrag(nr, "PAAR", az, name, "Ehepaar, 2 × RBS 2",
                aktionen, erwartungenStandard(zahlbetrag, budget, 0, false))
                .dokumentiere("Ein Ehepaar beantragt gemeinsam Grundsicherung. Der "
                        + "Sachbearbeiter erfasst beide Partner als eigene SozialFälle "
                        + "(jeweils Regelbedarfsstufe 2); die Wohnkosten werden nur einmal "
                        + "— am ersten Fall — hinterlegt, weil die Wohnung beiden dient. "
                        + "Der erwartete Bedarf ist die SUMME beider Regelbedarfe zuzüglich "
                        + "der einmaligen Wohnkosten. Geprüft wird, ob diese Summierung über "
                        + "mehrere Personen einer Bedarfsgemeinschaft korrekt in der Datenbank "
                        + "ankommt und die Zahlung keinen eindeutigen Fall referenziert (bei "
                        + "zwei Personen ist keine eindeutige Zuordnung möglich). Ein Fehler "
                        + "wäre beispielsweise, wenn die Wohnkosten versehentlich doppelt "
                        + "berücksichtigt würden.", rechnungen);
    }

    private Auftrag mehrbedarf(int nr) {
        boolean schwanger = random.nextBoolean();
        boolean warmwasser = random.nextBoolean();
        // mindestens ein Merkmal: wenn die anderen beiden leer ausgefallen sind, Merkzeichen G
        boolean merkzeichenG = random.nextBoolean() || (!schwanger && !warmwasser);
        BigDecimal miete = zufallsBetrag(200, 450, 10);
        BigDecimal betrieb = zufallsBetrag(20, 90, 5);
        BigDecimal heizung = zufallsBetrag(40, 120, 5);
        List<SozialFall> faelle = List.of(fallEntwurf(1, merkzeichenG, schwanger, 0,
                warmwasser, miete, betrieb, heizung, "0", "0"));
        List<Rechenblock> rechnungen = new ArrayList<>();
        BigDecimal zahlbetrag = berechne(rechnungen, "Anordnung mit Mehrbedarfszuschlägen",
                faelle, BigDecimal.ZERO, null,
                "Mehrbedarfe sind Prozentzuschläge auf den Regelbedarf; die Summe der "
                        + "Zuschläge nach § 30 Abs. 1–5 ist auf Höhe des Regelbedarfs gedeckelt "
                        + "(§ 30 Abs. 6). Der Warmwasser-Zuschlag (Abs. 7) gilt zusätzlich.");
        BigDecimal budget = zahlbetrag.add(zufallsBetrag(100, 300, 10));
        rechnungen.replaceAll(b -> b.mitBudget(budget));
        String name = hauptperson();
        String az = aktenzeichen(nr);

        List<SzenarioAktion> aktionen = List.of(
                SzenarioAktion.schritt("Vorgang anlegen", (s, ich, kollege, ctx) -> {
                    var bg = s.legeBedarfsgemeinschaftAn(az, name, budget, null, ich);
                    ctx.put("bgId", bg.id());
                    return "Vorgang " + bg.id();
                }),
                SzenarioAktion.schritt("SozialFall mit Mehrbedarfsmerkmalen", (s, ich, kollege, ctx) -> {
                    s.legeSozialFallAn((Long) ctx.get("bgId"), fallEntwurf(1, merkzeichenG,
                            schwanger, 0, warmwasser, miete, betrieb, heizung, "0", "0"), ich);
                    return merkmale(merkzeichenG, schwanger, warmwasser);
                }),
                SzenarioAktion.schritt("Auszahlung anordnen", (s, ich, kollege, ctx) -> {
                    var erg = s.ordneAuszahlungAn(new Auszahlungsauftrag((Long) ctx.get("bgId"),
                            UUID.randomUUID().toString(), "2026-10", ich));
                    ctx.put("zahlungId", erg.zahlung().id());
                    return erg.zahlung().auszahlungsbetrag().toPlainString() + " EUR";
                }));
        return new Auftrag(nr, "MEHRBEDARF", az, name,
                "§ 30-Mehrbedarfe: " + merkmale(merkzeichenG, schwanger, warmwasser),
                aktionen, erwartungenStandard(zahlbetrag, budget, 1, false))
                .dokumentiere("Eine leistungsberechtigte Person mit Merkzeichen G "
                        + "(erhebliche Gehbehinderung), Schwangerschaft ab der 13. Woche "
                        + "und/oder dezentraler Warmwasserbereitung (Gasdurchlauferhitzer) "
                        + "beantragt Grundsicherung. Diese Merkmale erhöhen den Bedarf über "
                        + "Prozentzuschläge des § 30 SGB XII: jeweils 17 % auf den eigenen "
                        + "Regelbedarf für Merkzeichen G und Schwangerschaft sowie einen "
                        + "kleinen stufenabhängigen Zuschlag für dezentrales Warmwasser. "
                        + "Geprüft wird, ob alle Merkmale in der Datenbank gespeichert werden "
                        + "(Flag-Spalten), die Zuschläge in der richtigen Höhe und "
                        + "Reihenfolge entstehen und die Obergrenze des § 30 Abs. 6 "
                        + "(Zuschlagssumme höchstens der Regelbedarf) niemals überschritten "
                        + "wird. Ein Fehler wäre ein doppelter oder fehlender Zuschlag.", rechnungen);
    }

    private Auftrag familie(int nr) {
        int kinder = 1 + random.nextInt(2);
        BigDecimal miete = zufallsBetrag(450, 750, 10);
        BigDecimal betrieb = zufallsBetrag(50, 140, 5);
        BigDecimal heizung = zufallsBetrag(70, 170, 5);
        List<SozialFall> faelle = new ArrayList<>();
        faelle.add(fallEntwurf(1, false, false, kinder, false, miete, betrieb, heizung, "0", "0"));
        for (int k = 0; k < kinder; k++) {
            faelle.add(fallEntwurf(6, false, false, 0, false,
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "0", "0"));
        }
        List<Rechenblock> rechnungen = new ArrayList<>();
        BigDecimal zahlbetrag = berechne(rechnungen, "Familienberechnung (Mutter + Kinder)",
                faelle, BigDecimal.ZERO, null,
                "Kinder zählen als eigene Mitglieder mit der Stufe 6. Der Alleinerziehenden-"
                        + "Mehrbedarf (§ 30 Abs. 3) bemisst sich immer nach der Regelbedarfsstufe 1 "
                        + "(563,00 EUR), nicht nach der Stufe der Mutter.");
        BigDecimal budget = zahlbetrag.add(zufallsBetrag(100, 400, 10));
        rechnungen.replaceAll(b -> b.mitBudget(budget));
        String name = hauptperson();
        String az = aktenzeichen(nr);

        List<SzenarioAktion> aktionen = List.of(
                SzenarioAktion.schritt("Vorgang anlegen (Familie)", (s, ich, kollege, ctx) -> {
                    var bg = s.legeBedarfsgemeinschaftAn(az, name, budget,
                            LocalDate.now().plusMonths(6), ich);
                    ctx.put("bgId", bg.id());
                    return "Vorgang " + bg.id();
                }),
                SzenarioAktion.schritt("Mutter + " + kinder + " Kind(er) erfassen", (s, ich, kollege, ctx) -> {
                    long bgId = (Long) ctx.get("bgId");
                    s.legeSozialFallAn(bgId, faelle.get(0), ich);
                    for (int k = 1; k < faelle.size(); k++) {
                        s.legeSozialFallAn(bgId, faelle.get(k), ich);
                    }
                    return (1 + kinder) + " Personen (Mutter RBS 1, Kinder RBS 6)";
                }),
                SzenarioAktion.schritt("Auszahlung anordnen", (s, ich, kollege, ctx) -> {
                    var erg = s.ordneAuszahlungAn(new Auszahlungsauftrag((Long) ctx.get("bgId"),
                            UUID.randomUUID().toString(), "2026-10", ich));
                    ctx.put("zahlungId", erg.zahlung().id());
                    return erg.zahlung().auszahlungsbetrag().toPlainString() + " EUR";
                }));
        return new Auftrag(nr, "FAMILIE", az, name,
                "Alleinerziehende mit " + kinder + " Kind(ern) (§ 30 Abs. 3)",
                aktionen, erwartungenStandard(zahlbetrag, budget, 0, false))
                .dokumentiere("Eine alleinerziehende Mutter beantragt für sich und ihre "
                        + kinder + " Kinder. Die Kinder werden als eigene Mitglieder der "
                        + "Bedarfsgemeinschaft erfasst (Regelbedarfsstufe 6, geringerer "
                        + "Regelsatz), die Mutter erhält zusätzlich den "
                        + "Alleinerziehenden-Mehrbedarf: bei einem Kind unter 7 oder zwei bis "
                        + "drei Kindern unter 16 pauschal 36 % der Stufe 1. Die Wiedervorlage "
                        + "wird mit einem künftigen Datum gesetzt. Geprüft wird, ob alle "
                        + "Personen als getrennte Zeilen in der Datenbank liegen, die "
                        + "Kinderzahlen an der Mutter gespeichert sind, das "
                        + "Wiedervorlagedatum exakt übernommen wird und die Gesamtsumme der "
                        + "Familie stimmt. Ein Fehler wäre, wenn Kinderzahlen verloren "
                        + "gingen oder der Mehrbedarf auf der falschen Stufe berechnet würde.",
                        rechnungen);
    }

    private Auftrag erwerbstaetiger(int nr) {
        int rbs = 1 + random.nextInt(2);
        BigDecimal erwerb = zufallsBetrag(300, 1600, 50);
        BigDecimal steuern = zufallsBetrag(0, 200, 10);
        BigDecimal sv = zufallsBetrag(50, 350, 10);
        BigDecimal miete = zufallsBetrag(250, 500, 10);
        BigDecimal betrieb = zufallsBetrag(30, 110, 5);
        BigDecimal heizung = zufallsBetrag(40, 130, 5);
        List<SozialFall> faelle = List.of(fallEntwurf(rbs, false, false, 0, false,
                miete, betrieb, heizung, "0", erwerb.toPlainString(),
                steuern.toPlainString(), sv.toPlainString()));
        List<Rechenblock> rechnungen = new ArrayList<>();
        BigDecimal zahlbetrag = berechne(rechnungen, "Einkommensberechnung nach § 82 SGB XII",
                faelle, BigDecimal.ZERO, null,
                "Vom Brutto-Erwerbseinkommen wird zuerst der Freibetrag abgezogen: 30 % des "
                        + "Bruttos, höchstens aber 50 % der Regelbedarfsstufe 1 = 281,50 EUR "
                        + "(§ 82 Abs. 3). Erst danach wirken die Absetzbeträge des § 82 Abs. 2 "
                        + "(Steuern, Sozialversicherung).");
        BigDecimal budget = zahlbetrag.add(zufallsBetrag(100, 300, 10));
        rechnungen.replaceAll(b -> b.mitBudget(budget));
        String name = hauptperson();
        String az = aktenzeichen(nr);

        List<SzenarioAktion> aktionen = List.of(
                SzenarioAktion.schritt("Vorgang anlegen", (s, ich, kollege, ctx) -> {
                    var bg = s.legeBedarfsgemeinschaftAn(az, name, budget, null, ich);
                    ctx.put("bgId", bg.id());
                    return "Vorgang " + bg.id();
                }),
                SzenarioAktion.schritt("SozialFall mit Erwerbseinkommen", (s, ich, kollege, ctx) -> {
                    s.legeSozialFallAn((Long) ctx.get("bgId"), fallEntwurf(rbs, false, false, 0, false,
                            miete, betrieb, heizung, "0", erwerb.toPlainString(),
                            steuern.toPlainString(), sv.toPlainString()), ich);
                    return "Brutto " + erwerb.toPlainString() + ", Absetzbeträge "
                            + (steuern.add(sv)).toPlainString() + " EUR (§ 82)";
                }),
                SzenarioAktion.schritt("Auszahlung anordnen", (s, ich, kollege, ctx) -> {
                    var erg = s.ordneAuszahlungAn(new Auszahlungsauftrag((Long) ctx.get("bgId"),
                            UUID.randomUUID().toString(), "2026-10", ich));
                    ctx.put("zahlungId", erg.zahlung().id());
                    return erg.zahlung().auszahlungsbetrag().toPlainString() + " EUR";
                }));
        return new Auftrag(nr, "ERWERB", az, name,
                "ErwerbstätigeR: Freibetrag § 82 Abs. 3 + Absetzbeträge Abs. 2",
                aktionen, erwartungenStandard(zahlbetrag, budget, rbs, false))
                .dokumentiere("Eine erwerbstätige Person beantragt Grundsicherung und gibt "
                        + "ihr Brutto-Erwerbseinkommen, gezahlte Steuern und "
                        + "Sozialversicherungsbeiträge an. Die Rechenkette des § 82 SGB XII "
                        + "ist mehrstufig: Erst bildet sich der Bruttobetrag, davon werden "
                        + "30 % als Erwerbsfreibetrag freigestellt — aber maximal 281,50 EUR "
                        + "(50 % der Stufe 1); ERST DANACH werden Steuern und "
                        + "Sozialversicherung als Absetzbeträge abgezogen. Was übrig bleibt, "
                        + "mindert den Bedarf. Geprüft wird die komplette Kette in der "
                        + "Datenbank: das gespeicherte Einkommen, die Absetzbeträge in "
                        + "eigenen Spalten und der daraus folgende korrekte Auszahlungsbetrag. "
                        + "Ein typischer Fehler wäre, die Reihenfolge zu vertauschen — dann "
                        + "käme ein anderer Betrag heraus, den die Verifikation aufdeckt.",
                        rechnungen);
    }

    /** Vermögensfall: je nach Streuwert reguläre Auszahlung oder erwarteter KEIN_ANSPRUCH. */
    private Auftrag vermoegensfall(int nr) {
        BigDecimal vermoegen = zufallsBetrag(8500, 16000, 100);
        BigDecimal miete = zufallsBetrag(150, 350, 10);
        BigDecimal betrieb = BigDecimal.ZERO;
        BigDecimal heizung = zufallsBetrag(0, 80, 5);
        List<SozialFall> faelle = List.of(fallEntwurf(1, false, false, 0, false,
                miete, betrieb, heizung, "0", "0"));
        List<Rechenblock> rechnungen = new ArrayList<>();
        BigDecimal zahlbetrag = berechne(rechnungen, "Vermögensanrechnung (§ 90 SGB XII)",
                faelle, vermoegen, null,
                "Frei bleibt nur das Schonvermögen: 10.000 EUR je erwachsener Person "
                        + "(§ 90 Abs. 2 Nr. 9 i.V.m. § 1 BarbetragsVO). Was darüber hinausgeht, "
                        + "wird wie Einkommen monatlich angerechnet. Übersteigt es den gesamten "
                        + "Bedarf, besteht KEIN Anspruch (§ 43 Abs. 1) — die Anordnung muss "
                        + "dann sauber abgelehnt werden.");
        boolean keinAnspruch = zahlbetrag.signum() <= 0;
        BigDecimal budget = zahlbetrag.max(BigDecimal.ZERO).add(zufallsBetrag(200, 500, 10));
        rechnungen.replaceAll(b -> b.mitBudget(budget));
        String name = hauptperson();
        String az = aktenzeichen(nr);

        List<SzenarioAktion> aktionen = new ArrayList<>();
        aktionen.add(SzenarioAktion.schritt("Vorgang anlegen (Vermögen "
                + vermoegen.toPlainString() + " EUR)", (s, ich, kollege, ctx) -> {
            var bg = s.legeBedarfsgemeinschaftAn(az, name, budget, null,
                    "DE89370400440532013000", "MARKDEF1100", name, vermoegen, ich);
            ctx.put("bgId", bg.id());
            return "Vorgang " + bg.id() + ", Schonvermögensprüfung läuft in der Berechnung";
        }));
        aktionen.add(SzenarioAktion.schritt("SozialFall erfassen", (s, ich, kollege, ctx) -> {
            s.legeSozialFallAn((Long) ctx.get("bgId"), fallEntwurf(1, false, false, 0, false,
                    miete, betrieb, heizung, "0", "0"), ich);
            return "RBS 1";
        }));
        if (keinAnspruch) {
            aktionen.add(SzenarioAktion.schrittMitErwartetemFehler(
                    "Auszahlung anordnen — KEIN_ANSPRUCH erwartet (§ 43/90)",
                    FachlicheException.KEIN_ANSPRUCH,
                    (s, ich, kollege, ctx) -> {
                        s.ordneAuszahlungAn(new Auszahlungsauftrag(
                                (Long) ctx.get("bgId"), UUID.randomUUID().toString(), "2026-10", ich));
                        return "unerwartet erfolgreich — hätte KEIN_ANSPRUCH werfen müssen";
                    }));
        } else {
            aktionen.add(SzenarioAktion.schritt("Auszahlung anordnen", (s, ich, kollege, ctx) -> {
                var erg = s.ordneAuszahlungAn(new Auszahlungsauftrag((Long) ctx.get("bgId"),
                        UUID.randomUUID().toString(), "2026-10", ich));
                ctx.put("zahlungId", erg.zahlung().id());
                return erg.zahlung().auszahlungsbetrag().toPlainString() + " EUR nach Vermögensanrechnung";
            }));
        }

        List<Erwartung> erwartungen = new ArrayList<>();
        erwartungen.add(Erwartung.betrag("Vermögen persistiert (§ 90)",
                "SELECT VERMOEGEN FROM BEDARFSGEMEINSCHAFT WHERE ID = ?",
                ctx -> List.of(ctx.get("bgId")), vermoegen.toPlainString()));
        if (keinAnspruch) {
            erwartungen.add(Erwartung.ganzeZahl("keine Zahlung trotz Anordnung (KEIN_ANSPRUCH)",
                    "SELECT COUNT(*) FROM ZAHLUNG WHERE BG_ID = ?",
                    ctx -> List.of(ctx.get("bgId")), 0));
            erwartungen.add(Erwartung.betrag("Budget unverändert",
                    "SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?",
                    ctx -> List.of(ctx.get("bgId")), budget.toPlainString()));
        } else {
            erwartungen.add(Erwartung.betrag("Zahlbetrag nach Schonvermögensabzug",
                    "SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?",
                    ctx -> List.of(ctx.get("zahlungId")), zahlbetrag.toPlainString()));
            erwartungen.add(Erwartung.betrag("Restbudget",
                    "SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?",
                    ctx -> List.of(ctx.get("bgId")), budget.subtract(zahlbetrag).toPlainString()));
        }
        return new Auftrag(nr, "VERMOEGEN", az, name,
                (keinAnspruch ? "Vermögen über Bedarf — " : "Vermögen im Schonvermögensbereich — ")
                        + "§ 90 Abs. 2 Nr. 9",
                aktionen, erwartungen)
                .dokumentiere("Eine Person mit Sparvermögen beantragt Grundsicherung. "
                        + "Vermögen wird nicht vollständig angerechnet: Frei bleibt das "
                        + "Schonvermögen von 10.000 EUR (je erwachsener Person). Nur der "
                        + "Überschuss wirkt wie Einkommen und mindert den Anspruch. Je nach "
                        + "ausgeloster Vermögenshöhe endet der Fall auf zwei Wegen: "
                        + "(a) es bleibt ein geminderter Zahlbetrag — dann muss genau dieser "
                        + "in der Datenbank stehen und das Budget entsprechend gemindert sein; "
                        + "(b) das verwertbare Vermögen übersteigt den gesamten Bedarf — dann "
                        + "besteht KEIN Anspruch (§ 43 Abs. 1), und die Auszahlungsanordnung "
                        + "muss sauber mit genau diesem Fachfehler abgelehnt werden, ohne "
                        + "eine Zahlung oder Budgetminderung zu hinterlassen. Geprüft wird "
                        + "zusätzlich, dass das Vermögen selbst korrekt in der "
                        + "Vorgangstabelle gespeichert ist.", rechnungen);
    }

    private Auftrag doppelklick(int nr) {
        BigDecimal budget = zufallsBetrag(700, 1200, 50);
        String name = hauptperson();
        String az = aktenzeichen(nr);
        List<Rechenblock> rechnungen = new ArrayList<>();
        BigDecimal zahlbetrag = berechne(rechnungen, "Erwartungswert (RBS 1, ohne KdU)",
                List.of(fallEntwurf(1, false, false, 0, false, BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO, "0", "0")),
                BigDecimal.ZERO, budget,
                "Die Vorgangsnummer (vom System vergebene UUID) macht jeden Auszahlungsauftrag "
                        + "einmalig: Wird derselbe Auftrag wiederholt abgeschickt, erkennt das "
                        + "System „bereits gebucht“ und legt KEINE zweite Zahlung an.");

        List<SzenarioAktion> aktionen = List.of(
                SzenarioAktion.schritt("Vorgang anlegen", (s, ich, kollege, ctx) -> {
                    var bg = s.legeBedarfsgemeinschaftAn(az, name, budget, null, ich);
                    ctx.put("bgId", bg.id());
                    return "Vorgang " + bg.id();
                }),
                SzenarioAktion.schritt("SozialFall (RBS 1, ohne KdU)", (s, ich, kollege, ctx) -> {
                    s.legeSozialFallAn((Long) ctx.get("bgId"), fallEntwurf(1, false, false, 0, false,
                            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "0", "0"), ich);
                    return "erwarteter Zahlbetrag 563,00 EUR";
                }),
                SzenarioAktion.schritt("Auszahlung anordnen", (s, ich, kollege, ctx) -> {
                    String vorgangsNr = UUID.randomUUID().toString();
                    ctx.put("vorgangsNr", vorgangsNr);
                    var erg = s.ordneAuszahlungAn(new Auszahlungsauftrag((Long) ctx.get("bgId"),
                            vorgangsNr, "2026-10", ich));
                    ctx.put("zahlungId", erg.zahlung().id());
                    return "Vorgangsnummer " + abgekuerzt(vorgangsNr);
                }),
                SzenarioAktion.schritt("Doppelklick: gleiche Vorgangsnummer erneut", (s, ich, kollege, ctx) -> {
                    Auszahlungsergebnis wiederholt = s.ordneAuszahlungAn(new Auszahlungsauftrag(
                            (Long) ctx.get("bgId"), (String) ctx.get("vorgangsNr"), "2026-10", ich));
                    if (!wiederholt.bereitsGebucht()) {
                        throw new IllegalStateException("Idempotenz verletzt: Wiederholung wurde als Neubuchung behandelt");
                    }
                    return "korrekt als bereits gebucht erkannt (Zahlung "
                            + wiederholt.zahlung().id() + ")";
                }));
        List<Erwartung> erwartungen = List.of(
                Erwartung.ganzeZahl("genau eine Zahlung trotz Wiederholung",
                        "SELECT COUNT(*) FROM ZAHLUNG WHERE BG_ID = ?",
                        ctx -> List.of(ctx.get("bgId")), 1),
                Erwartung.betrag("Budget nur einmal gemindert",
                        "SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?",
                        ctx -> List.of(ctx.get("bgId")), budget.subtract(zahlbetrag).toPlainString()));
        return new Auftrag(nr, "DOPPELKICK", az, name, "Doppelklick/Netzwerk-Wiederholung (Idempotenz)",
                aktionen, erwartungen)
                .dokumentiere("Diese Situation kennt jede Behörde: Der Mitarbeitende klickt "
                        + "versehentlich doppelt — oder das Netzwerk wiederholt den Request — "
                        + "und derselbe Auszahlungsauftrag erreicht das System zweimal. "
                        + "Schutz ist die Vorgangsnummer: Jeder Anordnung wird beim ersten "
                        + "Abschicken eine eindeutige Nummer mitgegeben; wiederholt sich der "
                        + "Auftrag, erkennt das System „bereits gebucht“ und liefert dieselbe "
                        + "Zahlung zurück. Geprüft wird strikt: genau EINE Zahlung in der "
                        + "Datenbank, EIN Auszahlungsvermerk im Journal und ein nur EINMAL "
                        + "gemindertes Restbudget. Eine doppelte Zahlung wäre der klassische "
                        + "Finanzschaden, den dieser Testfall verhindern helfen soll.",
                        rechnungen);
    }

    private Auftrag stornoMitKorrektur(int nr) {
        BigDecimal mieteAlt = zufallsBetrag(150, 300, 10);
        BigDecimal mieteNeu = mieteAlt.add(zufallsBetrag(30, 120, 10));
        List<SozialFall> faelleAlt = List.of(fallEntwurf(1, false, false, 0, false, mieteAlt,
                BigDecimal.ZERO, BigDecimal.ZERO, "0", "0"));
        List<SozialFall> faelleNeu = List.of(fallEntwurf(1, false, false, 0, false, mieteNeu,
                BigDecimal.ZERO, BigDecimal.ZERO, "0", "0"));
        List<Rechenblock> rechnungen = new ArrayList<>();
        BigDecimal zahlbetragAlt = berechne(rechnungen, "Erstanordnung (Miete "
                + mieteAlt.toPlainString() + " EUR)", faelleAlt, BigDecimal.ZERO, null,
                "Der Storno bucht das geminderte Budget vollständig zurück und gibt den "
                        + "Zahlungsmonat wieder frei.");
        BigDecimal zahlbetragNeu = berechne(rechnungen, "Neuanordnung nach Korrektur (Miete "
                + mieteNeu.toPlainString() + " EUR)", faelleNeu, BigDecimal.ZERO, null,
                "Endbudget = Startbudget − neuer Zahlbetrag: das zurückgebuchte alte Geld "
                        + "fließt wieder hinein.");
        BigDecimal budget = zahlbetragNeu.add(zufallsBetrag(100, 300, 10));
        rechnungen.replaceAll(b -> b.mitBudget(budget));
        String name = hauptperson();
        String az = aktenzeichen(nr);

        List<SzenarioAktion> aktionen = List.of(
                SzenarioAktion.schritt("Vorgang anlegen", (s, ich, kollege, ctx) -> {
                    var bg = s.legeBedarfsgemeinschaftAn(az, name, budget, null, ich);
                    ctx.put("bgId", bg.id());
                    return "Vorgang " + bg.id();
                }),
                SzenarioAktion.schritt("SozialFall erfassen (Miete " + mieteAlt.toPlainString() + ")",
                        (s, ich, kollege, ctx) -> {
                            s.legeSozialFallAn((Long) ctx.get("bgId"),
                                    fallEntwurf(1, false, false, 0, false, mieteAlt,
                                            BigDecimal.ZERO, BigDecimal.ZERO, "0", "0"), ich);
                            return null;
                        }),
                SzenarioAktion.schritt("Erste Auszahlung anordnen", (s, ich, kollege, ctx) -> {
                    var erg = s.ordneAuszahlungAn(new Auszahlungsauftrag((Long) ctx.get("bgId"),
                            UUID.randomUUID().toString(), "2026-09", ich));
                    ctx.put("alteZahlungId", erg.zahlung().id());
                    return erg.zahlung().auszahlungsbetrag().toPlainString() + " EUR";
                }),
                SzenarioAktion.schritt("Freigabe durch Kollegen", (s, ich, kollege, ctx) -> {
                    s.freigebenZahlung((Long) ctx.get("alteZahlungId"), kollege);
                    return "freigegeben durch " + kollege;
                }),
                SzenarioAktion.schritt("Fehler erkannt: Storno", (s, ich, kollege, ctx) -> {
                    s.storniereZahlung((Long) ctx.get("alteZahlungId"), ich);
                    return "Zahlung " + ctx.get("alteZahlungId") + " storniert, Budget zurückgebucht";
                }),
                SzenarioAktion.schritt("Fall korrigieren (Miete auf "
                        + mieteNeu.toPlainString() + ")", (s, ich, kollege, ctx) -> {
                    SozialFall geladen = s.ladeDetail((Long) ctx.get("bgId")).faelle().get(0);
                    SozialFall korrigiert = new SozialFall(geladen.id(), geladen.bgId(),
                            geladen.regelbedarfsstufe(), geladen.merkzeichenG(), geladen.schwanger(),
                            geladen.anzKinderUnter7(), geladen.anzKinderUnter16(),
                            geladen.anzKinderMinderjaehrig(), geladen.warmwasser(),
                            mieteNeu, geladen.kduBetriebskosten(), geladen.kduHeizung(),
                            geladen.einkommenRente(), geladen.einkommenErwerb(),
                            geladen.abzSteuern(), geladen.abzSozialversicherung(),
                            geladen.abzVersicherungen(), geladen.abzErwerbsaufwendungen(),
                            geladen.version());
                    s.aktualisiereSozialFall(korrigiert, geladen.version(), ich);
                    ctx.put("fallId", geladen.id());
                    return "Fall " + geladen.id() + " in Version 2";
                }),
                SzenarioAktion.schritt("Korrigierte Auszahlung für denselben Monat", (s, ich, kollege, ctx) -> {
                    var erg = s.ordneAuszahlungAn(new Auszahlungsauftrag((Long) ctx.get("bgId"),
                            UUID.randomUUID().toString(), "2026-09", ich));
                    ctx.put("neueZahlungId", erg.zahlung().id());
                    return erg.zahlung().auszahlungsbetrag().toPlainString() + " EUR (Monat erneut buchbar)";
                }));
        List<Erwartung> erwartungen = List.of(
                Erwartung.ganzeZahl("zwei Zahlungen (stornierte + neue)",
                        "SELECT COUNT(*) FROM ZAHLUNG WHERE BG_ID = ?",
                        ctx -> List.of(ctx.get("bgId")), 2),
                Erwartung.text("erste Zahlung STORNIERT",
                        "SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?",
                        ctx -> List.of(ctx.get("alteZahlungId")), "STORNIERT"),
                Erwartung.betrag("alte Zahlung unverändert storniert",
                        "SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?",
                        ctx -> List.of(ctx.get("alteZahlungId")), zahlbetragAlt.toPlainString()),
                Erwartung.einsVon("neue Zahlung wirksam (Kassenlauf möglich)",
                        "SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?",
                        ctx -> List.of(ctx.get("neueZahlungId")), "ANGELEGT|BEREIT|UEBERGEBEN"),
                Erwartung.betrag("korrigierter neuer Zahlbetrag",
                        "SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?",
                        ctx -> List.of(ctx.get("neueZahlungId")), zahlbetragNeu.toPlainString()),
                Erwartung.betrag("Budget: Storno zurück + Neuauszahlung gemindert",
                        "SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?",
                        ctx -> List.of(ctx.get("bgId")), budget.subtract(zahlbetragNeu).toPlainString()),
                Erwartung.ganzeZahl("Fall in Version 2 (optimistisches Locking)",
                        "SELECT VERSION FROM SOZIAL_FALL WHERE ID = ?",
                        ctx -> List.of(ctx.get("fallId")), 2));
        return new Auftrag(nr, "STORNO_KORREKTUR", az, name,
                "Storno + fachliche Korrektur + Neuauszahlung im selben Monat",
                aktionen, erwartungen)
                .dokumentiere("Ein Fall mit realistischem Korrekturbedarf: Zuerst wird mit "
                        + "einer (zu niedrigen) Miete angeordnet und freigegeben. Dann fällt "
                        + "der Fehler auf — etwa durch eine Nachmeldung des Vermieters. Der "
                        + "Sachbearbeiter storniert die freigegebene Zahlung: Damit muss das "
                        + "Budget vollständig zurückgebucht und der Zahlungsmonat wieder "
                        + "buchbar werden (eine wirksame Zahlung je Monat — Storno hebt die "
                        + "Sperre auf). Anschließend korrigiert er die Miete am Fall (die "
                        + "Fallfassung steigt auf Version 2) und ordnet für denselben Monat "
                        + "neu an. Geprüft wird: zwei Zahlungszeilen (STORNIERT + neu), der "
                        + "korrigierte neue Betrag, das Endbudget (Startbudget − neuer "
                        + "Zahlbetrag) und die Fallfassung 2. Fehler wären ein verlorenes "
                        + "Budget oder eine blockierte Monatsbuchung.", rechnungen);
    }

    private Auftrag konkurrenz(int nr) {
        BigDecimal mieteAlt = zufallsBetrag(120, 280, 10);
        BigDecimal mieteNeu = mieteAlt.add(zufallsBetrag(20, 150, 10));
        String name = hauptperson();
        String az = aktenzeichen(nr);

        List<SzenarioAktion> aktionen = List.of(
                SzenarioAktion.schritt("Vorgang anlegen", (s, ich, kollege, ctx) -> {
                    var bg = s.legeBedarfsgemeinschaftAn(az, name, zufallsBetrag(900, 1400, 50), null, ich);
                    ctx.put("bgId", bg.id());
                    return "Vorgang " + bg.id();
                }),
                SzenarioAktion.schritt("SozialFall erfassen (Miete " + mieteAlt.toPlainString() + ")",
                        (s, ich, kollege, ctx) -> {
                            SozialFall angelegt = s.legeSozialFallAn((Long) ctx.get("bgId"),
                                    fallEntwurf(1, false, false, 0, false, mieteAlt,
                                            BigDecimal.ZERO, BigDecimal.ZERO, "0", "0"), ich);
                            ctx.put("fallAlt", angelegt);
                            return "Fall " + angelegt.id() + " in Version 1";
                        }),
                SzenarioAktion.schritt("Maskenänderung speichern (Miete auf "
                        + mieteNeu.toPlainString() + ")", (s, ich, kollege, ctx) -> {
                    SozialFall alt = (SozialFall) ctx.get("fallAlt");
                    SozialFall geaendert = new SozialFall(alt.id(), alt.bgId(), alt.regelbedarfsstufe(),
                            alt.merkzeichenG(), alt.schwanger(), alt.anzKinderUnter7(),
                            alt.anzKinderUnter16(), alt.anzKinderMinderjaehrig(), alt.warmwasser(),
                            mieteNeu, alt.kduBetriebskosten(), alt.kduHeizung(), alt.einkommenRente(),
                            alt.einkommenErwerb(), alt.abzSteuern(), alt.abzSozialversicherung(),
                            alt.abzVersicherungen(), alt.abzErwerbsaufwendungen(), alt.version());
                    s.aktualisiereSozialFall(geaendert, alt.version(), ich);
                    ctx.put("fallId", alt.id());
                    return "gespeichert, Version 2";
                }),
                SzenarioAktion.schrittMitErwartetemFehler(
                        "Parallel: veralteter Maskenstand speichern — KONKURRENZ erwartet",
                        FachlicheException.KONKURRENZ,
                        (s, ich, kollege, ctx) -> {
                            SozialFall alt = (SozialFall) ctx.get("fallAlt");
                            s.aktualisiereSozialFall(alt, alt.version(), ich);
                            return "unerwartet erfolgreich — hätte KONKURRENZ werfen müssen";
                        }));
        List<Erwartung> erwartungen = List.of(
                Erwartung.betrag("Korrigierte Miete steht (kein Lost Update)",
                        "SELECT KDU_MIETE FROM SOZIAL_FALL WHERE ID = ?",
                        ctx -> List.of(ctx.get("fallId")), mieteNeu.toPlainString()),
                Erwartung.ganzeZahl("Fall in Version 2",
                        "SELECT VERSION FROM SOZIAL_FALL WHERE ID = ?",
                        ctx -> List.of(ctx.get("fallId")), 2));
        return new Auftrag(nr, "KONKURRENZ", az, name,
                "Zwei Bearbeiter, ein Fall: optimistisches Locking (Lost-Update-Schutz)",
                aktionen, erwartungen)
                                .dokumentiere("Zwei Mitarbeitende öffnen denselben Fall — im echten Leben "
                        + "passiert das, wenn eine Maske lange offen bleibt. Mitarbeiter A "
                        + "speichert seine Korrektur (Miete erhöht): erfolgreich, die "
                        + "Fallfassung steigt auf Version 2. Mitarbeiter B speichert kurz "
                        + "danach seinen inzwischen veralteten Stand noch einmal. Das "
                        + "optimistische Locking (Versionsspalte in der Datenbank) muss B "
                        + "mit dem Fachfehler KONKURRENZ abweisen — sonst würde A's Arbeit "
                        + "heimlich überschrieben („Lost Update“). Dieser erwartete Fehler "
                        + "ist Teil des Testfalls. Geprüft wird am Ende, dass A's Miete in "
                        + "der Datenbank steht und die Fallfassung genau 2 ist.", null);
    }

    private Auftrag kassenlauf(int nr) {
        List<SzenarioAktion> aktionen = List.of(
                SzenarioAktion.schritt("Kassenlauf über alle bereiten Zahlungen", (s, ich, kollege, ctx) -> {
                    var erg = s.uebergebeBereiteZahlungen(ich);
                    ctx.put("kassenAnzahl", erg.anzahl());
                    return erg.anzahl() + " Zahlung(en) über " + erg.summe().toPlainString() + " EUR übergeben";
                }));
        List<Erwartung> erwartungen = List.of(
                // Ein leerer Kassenlauf (noch keine BEREIT-Zahlungen) schreibt bewusst
                // kein Journal (Early-Return im Service) — nur bei Übergabe ≥ 1 muss
                // der vorgangsübergreifende Journaleintrag existieren.
                Erwartung.text("Kassenlauf-Journal korrekt (eintragspflichtig erst bei Übergabe ≥ 1)",
                        "SELECT CASE WHEN ? = 0 THEN 'ok' WHEN (SELECT COUNT(*) FROM JOURNAL "
                                + "WHERE BG_ID IS NULL AND AKTION_TEXT LIKE 'Kassenlauf%' "
                                + "AND SACHBEARBEITER_ID = ?) >= 1 THEN 'ok' ELSE 'fehlt' END "
                                + "FROM DUAL",
                        ctx -> List.of(ctx.get("kassenAnzahl"), ctx.get("mitarbeiter")), "ok"));
        return new Auftrag(nr, "KASSENLAUF", "-", "Kasse",
                "Kassenlauf (BEREIT → UEBERGEBEN), läuft parallel zu Freigaben",
                aktionen, erwartungen)
                .dokumentiere("Die Kasse schließt den Tagesablauf ab: Sie übernimmt alle "
                        + "freigegebenen (BEREIT) Zahlungen und übergibt sie zur Auszahlung "
                        + "(UEBERGEBEN), inklusive Sammelvermerk im Journal. Dieser Auftrag "
                        + "läuft absichtlich PARALLEL zu den anderen Testfällen — während "
                        + "andere Mitarbeitende noch anordnen und freigeben, greift die Kasse "
                        + "gleichzeitig auf denselben Datenbestand zu. Damit werden "
                        + "Zugriffsfolgen (Sperren, Deadlock-Wiederholung) realistisch "
                        + "geprüft. Hinweis: Findet die Kasse keine freigegebene Zahlung vor, "
                        + "übernimmt sie nichts und schreibt bewusst kein Journal — auch das "
                        + "ist korrekt und wird so erwartet.", null);
    }

    // ------------------------------------------------------------------
    // Erwartungsbausteine und Helfer
    // ------------------------------------------------------------------

    /**
     * Berechnet den erwarteten Zahlbetrag vorab (dieselbe reine Berechnung wie in
     * der Fachschicht) und protokolliert dafür einen vollständigen, nachrechenbaren
     * Rechenblock für die PDF-Testdokumentation.
     */
    private BigDecimal berechne(List<Rechenblock> bloecke, String titel,
            List<SozialFall> faelle, BigDecimal vermoegen, BigDecimal budget,
            String... hinweise) {
        var ergebnis = Leistungsberechnung.berechne(0, faelle,
                Leistungsberechnung.KduParameter.AUS, vermoegen);
        bloecke.add(new Rechenblock(titel, faelle, vermoegen, budget, ergebnis, List.of(hinweise)));
        return ergebnis.zahlbetrag();
    }

    /** Abgelaufene Signatur — ersetzt durch {@link #berechne} mit Rechenblock. */
    private BigDecimal zahlbetrag(int rbsIgnoriert, List<SozialFall> faelle, BigDecimal vermoegen) {
        return Leistungsberechnung.berechne(0, faelle,
                Leistungsberechnung.KduParameter.AUS, vermoegen).zahlbetrag();
    }

    /**
     * Erwartungsbausteine der Anordnungs-Szenarien. {@code mitFreigabe} = das
     * Szenario gibt selbst frei (Status BEREIT, ggf. schon vom Kassenlauf übergeben);
     * sonst liegt die Zahlung fachlich korrekt als ANGELEGT in der Warteschleife
     * des Vier-Augen-Prinzips, kann aber durch einen parallelen Kassenlauf bereits
     * übergeben worden sein.
     */
    private List<Erwartung> erwartungenStandard(BigDecimal zahlbetrag, BigDecimal budget,
            int rbs, boolean mitFreigabe) {
        List<Erwartung> e = new ArrayList<>();
        e.add(Erwartung.einsVon(mitFreigabe
                        ? "Zahlung freigegeben (oder bereits übergeben)"
                        : "Zahlung wirksam (ANGELEGT bis UEBERGEBEN je nach Kassenlauf)",
                "SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?",
                ctx -> List.of(ctx.get("zahlungId")),
                mitFreigabe ? "BEREIT|UEBERGEBEN" : "ANGELEGT|BEREIT|UEBERGEBEN"));
        e.add(Erwartung.betrag("Auszahlungsbetrag entspricht der Berechnung",
                "SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?",
                ctx -> List.of(ctx.get("zahlungId")), zahlbetrag.toPlainString()));
        e.add(Erwartung.betrag("Restbudget korrekt gemindert",
                "SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?",
                ctx -> List.of(ctx.get("bgId")), budget.subtract(zahlbetrag).toPlainString()));
        if (rbs > 0) {
            e.add(Erwartung.ganzeZahl("Regelbedarfsstufe persistiert",
                    "SELECT COUNT(*) FROM SOZIAL_FALL WHERE BG_ID = ? AND REGELBEDARFSSTUFE = ?",
                    ctx -> List.of(ctx.get("bgId"), rbs), 1));
        }
        return e;
    }

    /** Vollständiger Fall-Entwurf (id/version vergibt der Service); Absetzbeträge optional. */
    private SozialFall fallEntwurf(int rbs, boolean merkzeichenG, boolean schwanger,
            int kinderU16, boolean warmwasser, BigDecimal miete, BigDecimal betrieb,
            BigDecimal heizung, String rente, String erwerb) {
        return fallEntwurf(rbs, merkzeichenG, schwanger, kinderU16, warmwasser,
                miete, betrieb, heizung, rente, erwerb, "0", "0");
    }

    private SozialFall fallEntwurf(int rbs, boolean merkzeichenG, boolean schwanger,
            int kinderU16, boolean warmwasser, BigDecimal miete, BigDecimal betrieb,
            BigDecimal heizung, String rente, String erwerb, String steuern, String sv) {
        return new SozialFall(0, 0, rbs, merkzeichenG, schwanger, 0, kinderU16,
                Math.max(0, kinderU16), warmwasser, miete, betrieb, heizung,
                new BigDecimal(rente), new BigDecimal(erwerb),
                new BigDecimal(steuern), new BigDecimal(sv),
                BigDecimal.ZERO, BigDecimal.ZERO, 1);
    }
    private BigDecimal zufallsBetrag(int min, int max, int schritt) {
        int schritte = (max - min) / schritt;
        return BigDecimal.valueOf(min + schritt * random.nextInt(schritte + 1), 0)
                .setScale(2);
    }

    private String aktenzeichen(int nr) {
        return String.format("%s%04d", PREFIX, nr);
    }

    private String hauptperson() {
        return NACHNAMEN[random.nextInt(NACHNAMEN.length)] + ", "
                + VORNAMEN[random.nextInt(VORNAMEN.length)];
    }

    private String merkmale(boolean g, boolean schwanger, boolean warmwasser) {
        StringBuilder s = new StringBuilder();
        if (g) {
            s.append("Merkzeichen G (§ 30 Abs. 1), ");
        }
        if (schwanger) {
            s.append("Schwangerschaft (§ 30 Abs. 2), ");
        }
        if (warmwasser) {
            s.append("Warmwasser dezentral (§ 30 Abs. 7)");
        }
        String text = s.toString().replaceAll(", $", "");
        return text.isEmpty() ? "keine" : text;
    }

    private String gesamtKdu(BigDecimal miete, BigDecimal betrieb, BigDecimal heizung) {
        return miete.add(betrieb).add(heizung).toPlainString();
    }

    private String abgekuerzt(String uuid) {
        return uuid.length() <= 12 ? uuid : uuid.substring(0, 12) + "…";
    }
}
