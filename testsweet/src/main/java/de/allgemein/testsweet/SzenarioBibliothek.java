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

    /** Aktenzeichen-Präfix des aktuellen Topfs (POSITIV: SWEET-, NEGATIV: SWEET-N-,
     *  LAST: SWEET-L-); wird je erzeuge-Aufruf gesetzt, vor dem Lauf konstant. */
    private String praefix = PREFIX;

    public SzenarioBibliothek(long seed) {
        this.random = new Random(seed);
    }

    /** Erzeugt {@code anzahl} Aufträge, gleichmäßig über die Szenario-Arten gestreut. */
    public List<Auftrag> erzeuge(int anzahl) {
        return erzeuge(anzahl, PREFIX);
    }

    /** Wie {@link #erzeuge(int)}, aber mit eigenem Aktenzeichen-Präfix. */
    public List<Auftrag> erzeuge(int anzahl, String praefix) {
        this.praefix = praefix;
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

    /**
     * Erzeugt {@code anzahl} NEGATIV-Aufträge: abgewehrte Fachfehler sind hier das
     * erwartete Ergebnis (schrittMitErwartetemFehler), geprüft wird der unveränderte
     * Datenbank-Endzustand (keine Zahlung, unverändertes Budget, keine Bescheide …).
     */
    public List<Auftrag> erzeugeNegativ(int anzahl, String praefix) {
        this.praefix = praefix;
        List<Auftrag> auftraege = new ArrayList<>(anzahl);
        for (int i = 1; i <= anzahl; i++) {
            int wahl = random.nextInt(90);
            if (wahl < 12) {
                auftraege.add(negKeinAnspruch(i));
            } else if (wahl < 24) {
                auftraege.add(negBudget(i));
            } else if (wahl < 36) {
                auftraege.add(negDoppelterMonat(i));
            } else if (wahl < 45) {
                auftraege.add(negSelbstfreigabe(i));
            } else if (wahl < 54) {
                auftraege.add(negDoppelfreigabe(i));
            } else if (wahl < 63) {
                auftraege.add(negStornoNachUebergabe(i));
            } else if (wahl < 72) {
                auftraege.add(negUngueltigeBankverbindung(i));
            } else if (wahl < 81) {
                auftraege.add(negBescheidZeitraum(i));
            } else if (wahl < 90) {
                auftraege.add(negNichtGefunden(i));
            }
        }
        return auftraege;
    }

    /**
     * Erzeugt {@code anzahl} LAST-Aufträge: der schlanke Standardweg (Vorgang +
     * SozialFall + Anordnung) in hoher Stückzahl, um Transaktionssteuerung,
     * Sperren und die Datenbank unter Last zu setzen. Erwartungshaltung wie bei
     * den Positivfällen (Zahlbetrag, Budget); Freigabe entfällt bewusst.
     */
    /**
     * Erzeugt den STRESS-Topf als ZWEI Wellen über dieselben User: Die ersten
     * {@code anzahl} Aufträge (STRESS-ANLEGEN) legen je einen User an (Vorgang +
     * SozialFall, noch ohne Zahlung); die folgenden {@code anzahl} Aufträge
     * (STRESS-BEARBEITEN) finden ihren User über das Aktenzeichen wieder, ändern
     * die Miete (optimistisches Locking) und ordnen die Auszahlung an. Insgesamt
     * 2×{@code anzahl} Transaktionsblöcke — der Simulator gibt Welle 2 erst frei,
     * wenn Welle 1 vollständig abgearbeitet ist (Phasenbarriere).
     */
    public List<Auftrag> erzeugeStress(int anzahl, String praefix) {
        this.praefix = praefix;
        List<Auftrag> welle1 = new ArrayList<>(anzahl);
        List<Auftrag> welle2 = new ArrayList<>(anzahl);
        for (int i = 1; i <= anzahl; i++) {
            int rbs = 1 + random.nextInt(3);
            BigDecimal miete = BigDecimal.valueOf(250 + 10 * random.nextInt(31)).setScale(2);
            BigDecimal betrieb = BigDecimal.valueOf(30 + 5 * random.nextInt(19)).setScale(2);
            BigDecimal heizung = BigDecimal.valueOf(50 + 5 * random.nextInt(19)).setScale(2);
            BigDecimal mieteNeu = miete.add(BigDecimal.valueOf(10L * (1 + random.nextInt(10))))
                    .setScale(2);
            String name = hauptperson();
            String az = aktenzeichen(i);
            String monat = "2026-10";

            List<SozialFall> faelleNeu = List.of(fallEntwurf(rbs, false, false, 0, false,
                    mieteNeu, betrieb, heizung, "0", "0"));
            List<Rechenblock> rechnungenNeu = new ArrayList<>();
            BigDecimal betragNeu = berechne(rechnungenNeu,
                    "Nachbearbeitung: Miete " + miete.toPlainString() + " → "
                            + mieteNeu.toPlainString() + " EUR",
                    faelleNeu, BigDecimal.ZERO, null);
            BigDecimal budget = betragNeu.add(BigDecimal.valueOf(100 + 10 * random.nextInt(21)));
            rechnungenNeu.replaceAll(b -> b.mitBudget(budget));

            // ---------------- Welle 1: User anlegen ----------------
            List<SzenarioAktion> anlegen = List.of(
                    SzenarioAktion.schritt("Vorgang anlegen (User " + i + ")", (s2, ich, kollege, ctx) -> {
                        var bg = s2.legeBedarfsgemeinschaftAn(az, name, budget, null, ich);
                        ctx.put("bgId", bg.id());
                        return "Vorgang " + bg.id();
                    }),
                    SzenarioAktion.schritt("SozialFall erfassen (RBS " + rbs + ", Miete "
                            + miete.toPlainString() + ")", (s2, ich, kollege, ctx) -> {
                        s2.legeSozialFallAn((Long) ctx.get("bgId"), fallEntwurf(rbs, false,
                                false, 0, false, miete, betrieb, heizung, "0", "0"), ich);
                        return "Fall gespeichert (Version 1)";
                    }));
            // Welle-1-Erwartungen prüfen bewusst nur, was auch im ENDEzustand gilt:
            // Version/Zahlung ändern sich legitim durch Welle 2 — deren Prüfung
            // gehört zum STRESS-BEARBEITEN-Auftrag (gleicher User, Endzustand).
            List<Erwartung> erwartungenAnlegen = List.of(
                    Erwartung.ganzeZahl("User-Vorgang dauerhaft angelegt",
                            "SELECT COUNT(*) FROM BEDARFSGEMEINSCHAFT WHERE AKTENZEICHEN = ?",
                            ctx -> List.of(az), 1),
                    Erwartung.ganzeZahl("SozialFall dauerhaft angelegt",
                            "SELECT COUNT(*) FROM SOZIAL_FALL WHERE BG_ID = (SELECT ID FROM "
                                    + "BEDARFSGEMEINSCHAFT WHERE AKTENZEICHEN = ?)",
                            ctx -> List.of(az), 1));
            welle1.add(new Auftrag(i, "STRESS-ANLEGEN", az, name,
                    "Welle 1: User anlegen (Vorgang + SozialFall)", anlegen, erwartungenAnlegen)
                    .dokumentiere("Erste Welle des Stresstests: Der Sachbearbeiter legt den "
                            + "User an — Vorgang mit Startbudget und SEPA-Daten sowie den "
                            + "SozialFall mit Regelbedarfsstufe und Wohnkosten. Bewusst NOCH "
                            + "ohne Auszahlung: Die zweite Welle nimmt denselben User wieder "
                            + "auf und ändert ihn. Geprüft wird, dass der User mit Fall in "
                            + "Version 1 und unverändertem Budget in der Datenbank steht.",
                            rechnungenNeu));

            // ---------------- Welle 2: Nachbearbeitung ----------------
            List<SzenarioAktion> bearbeiten = List.of(
                    SzenarioAktion.schritt("Vorgang über Aktenzeichen wiederfinden",
                            (s2, ich, kollege, ctx) -> {
                                var bg = s2.findeBedarfsgemeinschaftNachAktenzeichen(az)
                                        .orElseThrow(() -> new java.util.NoSuchElementException(az));
                                ctx.put("bgId", bg.id());
                                return "Vorgang " + bg.id() + " (" + az + ") gefunden";
                            }),
                    SzenarioAktion.schritt("Miete auf " + mieteNeu.toPlainString()
                            + " ändern (optimistisches Locking)", (s2, ich, kollege, ctx) -> {
                                var detail = s2.ladeDetail((Long) ctx.get("bgId"));
                                SozialFall alt = detail.faelle().get(0);
                                SozialFall geaendert = new SozialFall(alt.id(), alt.bgId(),
                                        alt.regelbedarfsstufe(), alt.merkzeichenG(), alt.schwanger(),
                                        alt.anzKinderUnter7(), alt.anzKinderUnter16(),
                                        alt.anzKinderMinderjaehrig(), alt.warmwasser(),
                                        mieteNeu, alt.kduBetriebskosten(), alt.kduHeizung(),
                                        alt.einkommenRente(), alt.einkommenErwerb(),
                                        alt.abzSteuern(), alt.abzSozialversicherung(),
                                        alt.abzVersicherungen(), alt.abzErwerbsaufwendungen(),
                                        alt.version());
                                s2.aktualisiereSozialFall(geaendert, alt.version(), ich);
                                return "Fall " + alt.id() + " auf Version 2 gebracht";
                            }),
                    SzenarioAktion.schritt("Auszahlung anordnen (" + monat + ")",
                            (s2, ich, kollege, ctx) -> {
                                var erg = s2.ordneAuszahlungAn(new Auszahlungsauftrag(
                                        (Long) ctx.get("bgId"), UUID.randomUUID().toString(),
                                        monat, ich));
                                ctx.put("zahlungId", erg.zahlung().id());
                                return erg.zahlung().auszahlungsbetrag().toPlainString()
                                        + " EUR (ANGELEGT, nach neuer Miete)";
                            }));
            List<Erwartung> erwartungenBearbeiten = List.of(
                    Erwartung.einsVon("Zahlung wirksam (Vier-Augen-Warteschlange)",
                            "SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ZAHLUNGSMONAT = ? AND BG_ID = "
                                    + "(SELECT ID FROM BEDARFSGEMEINSCHAFT WHERE AKTENZEICHEN = ?)",
                            ctx -> List.of(monat, az), "ANGELEGT|BEREIT|UEBERGEBEN"),
                    Erwartung.betrag("Auszahlungsbetrag nach geänderter Miete",
                            "SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ZAHLUNGSMONAT = ? AND BG_ID = "
                                    + "(SELECT ID FROM BEDARFSGEMEINSCHAFT WHERE AKTENZEICHEN = ?)",
                            ctx -> List.of(monat, az), betragNeu.toPlainString()),
                    Erwartung.betrag("Restbudget korrekt gemindert",
                            "SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE AKTENZEICHEN = ?",
                            ctx -> List.of(az), budget.subtract(betragNeu).toPlainString()),
                    Erwartung.ganzeZahl("SozialFall in Version 2 (Nachbearbeitung dokumentiert)",
                            "SELECT VERSION FROM SOZIAL_FALL WHERE BG_ID = (SELECT ID FROM "
                                    + "BEDARFSGEMEINSCHAFT WHERE AKTENZEICHEN = ?)",
                            ctx -> List.of(az), 2));
            welle2.add(new Auftrag(anzahl + i, "STRESS-BEARBEITEN", az, name,
                    "Welle 2: Nachbearbeitung (Miete ändern + Anordnung)", bearbeiten,
                    erwartungenBearbeiten)
                    .dokumentiere("Zweite Welle des Stresstests auf demselben User: Der "
                            + "Sachbearbeiter findet den Vorgang über das Aktenzeichen wieder, "
                            + "erhöht die Miete (das optimistische Locking muss die Version "
                            + "sauber auf 2 heben) und ordnet die Auszahlung an — der Betrag "
                            + "muss sich nach der NEUEN Miete berechnen. Geprüft wird der "
                            + "vollständige Datenbank-Endzustand: Zahlung mit neuem Betrag, "
                            + "gemindertes Restbudget und Fall-Version 2.", rechnungenNeu));
        }
        List<Auftrag> alle = new ArrayList<>(welle1);
        alle.addAll(welle2);
        return alle;
    }

    public List<Auftrag> erzeugeLast(int anzahl, String praefix) {
        this.praefix = praefix;
        List<Auftrag> auftraege = new ArrayList<>(anzahl);
        for (int i = 1; i <= anzahl; i++) {
            auftraege.add(lastDurchlauf(i));
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
                        + "(§ 90 Abs. 2 Nr. 9 i.V.m. § 1 der zugehörigen Durchführungsverordnung). "
                        + "Was darüber hinausgeht, "
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
    // ------------------------------------------------------------------
    // NEGATIV-Szenarien: abgewehrte Fachfehler als erwartetes Ergebnis.
    // Geprüft wird jeweils, dass die Datenbank unverändert bleibt (keine
    // Zahlung, kein Bescheid, Budget nicht angetastet) — der Fehler darf
    // keine Spuren hinterlassen.
    // ------------------------------------------------------------------

    /** Hohe Rente übersteigt den Bedarf → KEIN_ANSPRUCH (§ 43 Abs. 1). */
    private Auftrag negKeinAnspruch(int nr) {
        int rbs = 1 + random.nextInt(3);
        BigDecimal miete = zufallsBetrag(150, 350, 10);
        BigDecimal betrieb = zufallsBetrag(20, 80, 5);
        BigDecimal heizung = zufallsBetrag(30, 100, 5);
        BigDecimal rente = BigDecimal.valueOf(2500 + 500L * random.nextInt(7)).setScale(2);
        List<SozialFall> faelle = List.of(fallEntwurf(rbs, false, false, 0, false,
                miete, betrieb, heizung, rente.toPlainString(), "0"));
        List<Rechenblock> rechnungen = new ArrayList<>();
        berechne(rechnungen, "Nachweis KEIN_ANSPRUCH: Einkommen übersteigt den Bedarf",
                faelle, BigDecimal.ZERO, null,
                "Die Rente übersteigt den Gesamtbedarf deutlich — der Zahlbetrag fällt "
                        + "negativ aus, die Anordnung muss mit KEIN_ANSPRUCH abgelehnt "
                        + "werden (§ 43 Abs. 1 SGB XII), ohne eine Zahlung zu hinterlassen.");
        BigDecimal budget = BigDecimal.valueOf(1000).setScale(2);
        String name = hauptperson();
        String az = aktenzeichen(nr);

        List<SzenarioAktion> aktionen = List.of(
                SzenarioAktion.schritt("Vorgang anlegen", (s, ich, kollege, ctx) -> {
                    var bg = s.legeBedarfsgemeinschaftAn(az, name, budget, null, ich);
                    ctx.put("bgId", bg.id());
                    return "Vorgang " + bg.id();
                }),
                SzenarioAktion.schritt("SozialFall mit hoher Rente ("
                                + rente.toPlainString() + " EUR)", (s, ich, kollege, ctx) -> {
                    s.legeSozialFallAn((Long) ctx.get("bgId"), fallEntwurf(rbs, false, false,
                            0, false, miete, betrieb, heizung, rente.toPlainString(), "0"), ich);
                    return "Rente " + rente.toPlainString() + " EUR monatlich";
                }),
                SzenarioAktion.schrittMitErwartetemFehler(
                        "Auszahlung anordnen — KEIN_ANSPRUCH erwartet (§ 43 Abs. 1)",
                        FachlicheException.KEIN_ANSPRUCH,
                        (s, ich, kollege, ctx) -> {
                            s.ordneAuszahlungAn(new Auszahlungsauftrag((Long) ctx.get("bgId"),
                                    UUID.randomUUID().toString(), "2026-10", ich));
                            return "unerwartet erfolgreich — hätte KEIN_ANSPRUCH werfen müssen";
                        }));

        List<Erwartung> erwartungen = List.of(
                Erwartung.ganzeZahl("keine Zahlung trotz Anordnung (KEIN_ANSPRUCH abgewehrt)",
                        "SELECT COUNT(*) FROM ZAHLUNG WHERE BG_ID = ?",
                        ctx -> List.of(ctx.get("bgId")), 0),
                Erwartung.betrag("Budget unverändert",
                        "SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?",
                        ctx -> List.of(ctx.get("bgId")), budget.toPlainString()));
        return new Auftrag(nr, "NEG-KEINANSPRUCH", az, name,
                "Rente über Bedarf — Anordnung abgewehrt", aktionen, erwartungen)
                .dokumentiere("Hintergrund: Die Grundsicherung ist eine Hilfe für Menschen, "
                        + "die ihren Lebensunterhalt nicht selbst bestreiten können. Wer ausreichend "
                        + "eigenes Einkommen hat, hat keinen Anspruch darauf — das Geld des Staates "
                        + "ist für diejenigen da, die es wirklich brauchen. In diesem Testfall "
                        + "beantragt eine Person mit einer hohen Rente Grundsicherung: Die Rente "
                        + "liegt deutlich über dem kompletten monatlichen Bedarf (Regelbedarf plus "
                        + "Wohnkosten). Der simulierte Sachbearbeiter erfasst die Person sauber mit "
                        + "allen Daten und ordnet dann die Auszahlung an — so, wie es ein "
                        + "unbedachter Arbeitsalltag mit sich bringen könnte. Erwartet wird, dass "
                        + "die Fachschicht diese Anordnung mit dem Fachfehler KEIN_ANSPRUCH "
                        + "zurückweist (rechtliche Grundlage: § 43 Abs. 1 SGB XII — Einkommen und "
                        + "Vermögen sind einzusetzen, bevor der Staat zahlt). Die Ablehnung ist "
                        + "kein Fehler im System, sondern das gewünschte Ergebnis: wie ein "
                        + "Sachbearbeiter, der einen Antrag höflich aber bestimmt ablehnt, weil "
                        + "die Einkommensunterlagen eindeutig sind. Geprüft wird deshalb der "
                        + "Datenbank-Endzustand mit gleicher Sorgfalt: Es darf KEINE Zahlung "
                        + "entstanden sein, und das Startbudget des Vorgangs muss unverändert "
                        + "sein — die Ablehnung darf keinerlei Spuren in der Buchhaltung "
                        + "hinterlassen. Ein Fehler wäre eine dennoch angelegte Zahlung oder eine "
                        + "irrtümlich geminderte Budgetsumme; beides hieße, das System hätte Geld "
                        + "zugesagt, auf das kein Anspruch besteht.", rechnungen);
    }

    /** Startbudget kleiner als der Zahlbetrag → BUDGET_ERSCHOEPFT. */
    private Auftrag negBudget(int nr) {
        int rbs = 1 + random.nextInt(3);
        BigDecimal miete = zufallsBetrag(300, 550, 10);
        BigDecimal betrieb = zufallsBetrag(30, 100, 5);
        BigDecimal heizung = zufallsBetrag(50, 130, 5);
        List<SozialFall> faelle = List.of(fallEntwurf(rbs, false, false, 0, false,
                miete, betrieb, heizung, "0", "0"));
        List<Rechenblock> rechnungen = new ArrayList<>();
        BigDecimal zahlbetrag = berechne(rechnungen,
                "Nachweis BUDGET_ERSCHOEPFT: Bedarf übersteigt das Startbudget",
                faelle, BigDecimal.ZERO, null,
                "Das Startbudget ist bewusst kleiner als der berechnete Zahlbetrag — "
                        + "die Budgetprüfung muss die Anordnung mit BUDGET_ERSCHOEPFT abwehren.");
        BigDecimal budget = zahlbetrag.subtract(BigDecimal.valueOf(20 + 10L * random.nextInt(9)))
                .max(BigDecimal.valueOf(10)).setScale(2);
        String name = hauptperson();
        String az = aktenzeichen(nr);

        List<SzenarioAktion> aktionen = List.of(
                SzenarioAktion.schritt("Vorgang anlegen (Startbudget " + budget.toPlainString()
                        + " EUR — bewusst zu klein)", (s, ich, kollege, ctx) -> {
                    var bg = s.legeBedarfsgemeinschaftAn(az, name, budget, null, ich);
                    ctx.put("bgId", bg.id());
                    return "Vorgang " + bg.id() + " mit Budget " + budget.toPlainString() + " EUR";
                }),
                SzenarioAktion.schritt("SozialFall erfassen (Bedarf "
                        + zahlbetrag.toPlainString() + " EUR)", (s, ich, kollege, ctx) -> {
                    s.legeSozialFallAn((Long) ctx.get("bgId"), fallEntwurf(rbs, false, false,
                            0, false, miete, betrieb, heizung, "0", "0"), ich);
                    return "Zahlbetrag " + zahlbetrag.toPlainString() + " EUR";
                }),
                SzenarioAktion.schrittMitErwartetemFehler(
                        "Auszahlung anordnen — BUDGET_ERSCHOEPFT erwartet",
                        FachlicheException.BUDGET_ERSCHOEPFT,
                        (s, ich, kollege, ctx) -> {
                            s.ordneAuszahlungAn(new Auszahlungsauftrag((Long) ctx.get("bgId"),
                                    UUID.randomUUID().toString(), "2026-10", ich));
                            return "unerwartet erfolgreich — hätte BUDGET_ERSCHOEPFT werfen müssen";
                        }));

        List<Erwartung> erwartungen = List.of(
                Erwartung.ganzeZahl("keine Zahlung (Budgetprüfung abgewehrt)",
                        "SELECT COUNT(*) FROM ZAHLUNG WHERE BG_ID = ?",
                        ctx -> List.of(ctx.get("bgId")), 0),
                Erwartung.betrag("Budget unverändert",
                        "SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?",
                        ctx -> List.of(ctx.get("bgId")), budget.toPlainString()));
        return new Auftrag(nr, "NEG-BUDGET", az, name,
                "Startbudget unter Zahlbetrag — Anordnung abgewehrt", aktionen, erwartungen)
                .dokumentiere("Hintergrund: Jeder Vorgang hat ein verfügbares Budget — "
                        + "vergleichbar mit dem bewilligten Geldtopf einer coststellen-"
                        + "verantwortlichen Stelle, aus dem die Auszahlungen dieses Falles "
                        + "bezahlt werden. Was im Topf ist, kann ausgezahlt werden; mehr nicht. "
                        + "In diesem Testfall wird ein Vorgang bewusst mit einem zu klein "
                        + "bemessenen Startbudget angelegt, während die berechnete monatliche "
                        + "Leistung der Person (Regelbedarf plus Wohnkosten) größer ist als "
                        + "dieses Budget. Der simulierte Sachbearbeiter erfasst den Fall korrekt "
                        + "und ordnet die Auszahlung an — der Bedarf selbst ist ja richtig "
                        + "berechnet, nur der Topf ist zu klein. Erwartet wird, dass die "
                        + "Budgetprüfung der Fachschicht die Anordnung mit dem Fachfehler "
                        + "BUDGET_ERSCHOEPFT abwehrt: Das System soll sich verhalten wie eine "
                        + "Kasse, die eine Abbuchung ablehnt, weil das Konto nicht gedeckt ist — "
                        + "besser eine klare Fehlermeldung als eine stille Überziehung. Geprüft "
                        + "wird der Datenbank-Endzustand: Es darf KEINE Zahlung entstanden sein, "
                        + "und das Budget darf nicht angetastet worden sein (auch keine "
                        + "Teilzahlung oder negative Restsumme). Ein Fehler wäre jede Form von "
                        + "Überziehung — sie würde bedeuten, dass das System mehr Geld "
                        + "verspricht, als der Vorgang je hatte.", rechnungen);
    }

    /** Zweite Anordnung für denselben Monat (neue Vorgangsnummer) → VALIDATION. */
    private Auftrag negDoppelterMonat(int nr) {
        int rbs = 1 + random.nextInt(3);
        BigDecimal miete = zufallsBetrag(250, 500, 10);
        BigDecimal betrieb = zufallsBetrag(30, 90, 5);
        BigDecimal heizung = zufallsBetrag(50, 120, 5);
        List<SozialFall> faelle = List.of(fallEntwurf(rbs, false, false, 0, false,
                miete, betrieb, heizung, "0", "0"));
        List<Rechenblock> rechnungen = new ArrayList<>();
        BigDecimal zahlbetrag = berechne(rechnungen, "Anordnung (erster Monat)",
                faelle, BigDecimal.ZERO, null);
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
                SzenarioAktion.schritt("SozialFall erfassen", (s, ich, kollege, ctx) -> {
                    s.legeSozialFallAn((Long) ctx.get("bgId"), fallEntwurf(rbs, false, false,
                            0, false, miete, betrieb, heizung, "0", "0"), ich);
                    return "RBS " + rbs;
                }),
                SzenarioAktion.schritt("Erste Auszahlung anordnen (2026-10)", (s, ich, kollege, ctx) -> {
                    var erg = s.ordneAuszahlungAn(new Auszahlungsauftrag((Long) ctx.get("bgId"),
                            UUID.randomUUID().toString(), "2026-10", ich));
                    ctx.put("zahlungId", erg.zahlung().id());
                    return "Zahlung " + erg.zahlung().id() + " über "
                            + erg.zahlung().auszahlungsbetrag().toPlainString() + " EUR";
                }),
                SzenarioAktion.schrittMitErwartetemFehler(
                        "Zweite Anordnung für 2026-10 — VALIDATION erwartet (Doppelzahlung)",
                        FachlicheException.VALIDATION,
                        (s, ich, kollege, ctx) -> {
                            s.ordneAuszahlungAn(new Auszahlungsauftrag((Long) ctx.get("bgId"),
                                    UUID.randomUUID().toString(), "2026-10", ich));
                            return "unerwartet erfolgreich — Doppelzahlung wäre durchgerutscht";
                        }));

        List<Erwartung> erwartungen = List.of(
                Erwartung.ganzeZahl("genau eine Zahlung je Vorgang und Monat",
                        "SELECT COUNT(*) FROM ZAHLUNG WHERE BG_ID = ?",
                        ctx -> List.of(ctx.get("bgId")), 1),
                Erwartung.betrag("Betrag der ersten Zahlung unverändert",
                        "SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?",
                        ctx -> List.of(ctx.get("zahlungId")), zahlbetrag.toPlainString()),
                Erwartung.betrag("Budget nur einmal gemindert",
                        "SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?",
                        ctx -> List.of(ctx.get("bgId")), budget.subtract(zahlbetrag).toPlainString()));
        return new Auftrag(nr, "NEG-DOPPELMONAT", az, name,
                "Doppelte Anordnung je Monat abgewehrt", aktionen, erwartungen)
                .dokumentiere("Hintergrund: Pro Vorgang und Monat darf es genau EINE "
                        + "wirksame Zahlung geben — sonst bekäme dieselbe Person denselben "
                        + "Leistungsbeitrag zweimal ausbezahlt. Doppelzahlungen sind der "
                        + "Klassiker behördlicher Fehlbuchungen: Sie entstehen meist nicht "
                        + "durch Betrug, sondern durch Alltagssituationen wie einen "
                        + "Doppelklick, einen abgebrochenen Dialog, der wiederholt wird, oder "
                        + "zwei Kräfte, die denselben Fall unabhängig voneinander bearbeiten. "
                        + "Genau so ein Fall wird hier nachgebaut: Zuerst ordnet der Sachbearbeiter "
                        + "die Auszahlung für Oktober ganz regulär an — sie landet korrekt in "
                        + "der Vier-Augen-Warteschlange. Dann wird ein zweiter Anordnungsversuch "
                        + "für denselben Monat gestartet, diesmal mit einer NEUEN "
                        + "Vorgangsnummer: Damit ist es gerade KEIN einfacher Doppelklick, "
                        + "den die Technik über dieselbe Nummer erkennen würde, sondern ein "
                        + "echter zweiter, unabhängiger Antrag — der schwerste Fall für die "
                        + "Prüfung. Erwartet wird, dass die Fachschicht auch diesen mit dem "
                        + "Fachfehler VALIDATION abwehrt (für den Monat existiert bereits eine "
                        + "nicht stornierte Zahlung). Geprüft wird der Datenbank-Endzustand: "
                        + "Es bleibt GENAU EINE Zahlung mit dem ursprünglichen Betrag, und das "
                        + "Budget ist nur einmal gemindert. Ein Fehler wäre jede zweite "
                        + "Zahlung für denselben Monat — sie würde die Person doppelt "
                        + "auszahlen und das Budget doppelt belasten.", rechnungen);
    }

    /** Erfasser gibt die eigene Zahlung frei → BERECHTIGUNG (Vier-Augen). */
    private Auftrag negSelbstfreigabe(int nr) {
        int rbs = 1 + random.nextInt(3);
        BigDecimal miete = zufallsBetrag(250, 500, 10);
        BigDecimal betrieb = zufallsBetrag(30, 90, 5);
        BigDecimal heizung = zufallsBetrag(50, 120, 5);
        List<SozialFall> faelle = List.of(fallEntwurf(rbs, false, false, 0, false,
                miete, betrieb, heizung, "0", "0"));
        List<Rechenblock> rechnungen = new ArrayList<>();
        BigDecimal zahlbetrag = berechne(rechnungen, "Anordnung (Vier-Augen-Warteschlange)",
                faelle, BigDecimal.ZERO, null);
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
                SzenarioAktion.schritt("SozialFall erfassen", (s, ich, kollege, ctx) -> {
                    s.legeSozialFallAn((Long) ctx.get("bgId"), fallEntwurf(rbs, false, false,
                            0, false, miete, betrieb, heizung, "0", "0"), ich);
                    return "RBS " + rbs;
                }),
                SzenarioAktion.schritt("Auszahlung anordnen", (s, ich, kollege, ctx) -> {
                    var erg = s.ordneAuszahlungAn(new Auszahlungsauftrag((Long) ctx.get("bgId"),
                            UUID.randomUUID().toString(), "2026-10", ich));
                    ctx.put("zahlungId", erg.zahlung().id());
                    return "Zahlung " + erg.zahlung().id() + " (ANGELEGT)";
                }),
                SzenarioAktion.schrittMitErwartetemFehler(
                        "Selbstfreigabe durch den Erfasser — BERECHTIGUNG erwartet",
                        FachlicheException.BERECHTIGUNG,
                        (s, ich, kollege, ctx) -> {
                            s.freigebenZahlung((Long) ctx.get("zahlungId"), ich);
                            return "unerwartet erfolgreich — Selbstfreigabe wäre durchgerutscht";
                        }));

        List<Erwartung> erwartungen = List.of(
                Erwartung.einsVon("Zahlung weiterhin angelegt (nicht freigegeben)",
                        "SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?",
                        ctx -> List.of(ctx.get("zahlungId")), "ANGELEGT"),
                Erwartung.text("kein Freigabevermerk eingetragen",
                        "SELECT COUNT(*) FROM ZAHLUNG WHERE ID = ? AND FREIGEGEBEN_VON IS NULL",
                        ctx -> List.of(ctx.get("zahlungId")), "1"));
        return new Auftrag(nr, "NEG-SELBSTFREIGABE", az, name,
                "Selbstfreigabe (Vier-Augen-Verletzung) abgewehrt", aktionen, erwartungen)
                .dokumentiere("Hintergrund: Das Vier-Augen-Prinzip ist eine der ältesten "
                        + "Sicherheitsregeln der Verwaltung: Keine wichtige Entscheidung darf "
                        + "von einer einzigen Person allein getroffen werden. Wer beantragt oder "
                        + "anordnet, darf nicht selbst freigeben — eine zweite Kraft muss "
                        + "unabhängig prüfen. Der Sinn: Flüchtigkeitsfehler werden entdeckt, "
                        + "bevor Geld fließt, und bewusste Manipulation einer einzelnen Person "
                        + "wird unmöglich. In diesem Testfall wird die Versuchungssituation "
                        + "direkt nachgebaut: Der Sachbearbeiter ordnet die Auszahlung an und "
                        + "versucht dann, sie im selben Atemzug SELBST freizugeben — etwa weil "
                        + "gerade kein Kollege erreichbar ist oder aus Bequemlichkeit. "
                        + "Erwartet wird, dass die Fachschicht das mit dem Fachfehler "
                        + "BERECHTIGUNG abwehrt; die Meldung nennt den Grund klar (Erfasser "
                        + "und Freigebender dürfen nicht identisch sein). Wichtig ist, dass "
                        + "diese Prüfung nicht nur auf der Oberfläche stattfindet, sondern "
                        + "serverseitig erzwungen wird — keine Rolle, kein Knopf und kein "
                        + "Weg daran vorbei. Geprüft wird der Datenbank-Endzustand: Die Zahlung "
                        + "bleibt im Status ANGELEGT (wartet also weiter auf eine echte zweite "
                        + "Kraft) und trägt KEINEN Freigabevermerk. Ein Fehler wäre eine "
                        + "durchgekommene Selbstfreigabe — sie hätte das Vier-Augen-Prinzip "
                        + "ausgehebelt und die Kontrollwirkung beseitigt.", rechnungen);
    }

    /** Freigabe einer bereits freigegebenen Zahlung → VALIDATION. */
    private Auftrag negDoppelfreigabe(int nr) {
        int rbs = 1 + random.nextInt(3);
        BigDecimal miete = zufallsBetrag(250, 500, 10);
        BigDecimal betrieb = zufallsBetrag(30, 90, 5);
        BigDecimal heizung = zufallsBetrag(50, 120, 5);
        List<SozialFall> faelle = List.of(fallEntwurf(rbs, false, false, 0, false,
                miete, betrieb, heizung, "0", "0"));
        List<Rechenblock> rechnungen = new ArrayList<>();
        BigDecimal zahlbetrag = berechne(rechnungen, "Anordnung + Freigabe",
                faelle, BigDecimal.ZERO, null);
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
                SzenarioAktion.schritt("SozialFall erfassen", (s, ich, kollege, ctx) -> {
                    s.legeSozialFallAn((Long) ctx.get("bgId"), fallEntwurf(rbs, false, false,
                            0, false, miete, betrieb, heizung, "0", "0"), ich);
                    return "RBS " + rbs;
                }),
                SzenarioAktion.schritt("Auszahlung anordnen", (s, ich, kollege, ctx) -> {
                    var erg = s.ordneAuszahlungAn(new Auszahlungsauftrag((Long) ctx.get("bgId"),
                            UUID.randomUUID().toString(), "2026-10", ich));
                    ctx.put("zahlungId", erg.zahlung().id());
                    return "Zahlung " + erg.zahlung().id() + " (ANGELEGT)";
                }),
                SzenarioAktion.schritt("Freigabe durch Kollegen (Vier-Augen)", (s, ich, kollege, ctx) -> {
                    s.freigebenZahlung((Long) ctx.get("zahlungId"), kollege);
                    return "freigegeben durch " + kollege;
                }),
                SzenarioAktion.schrittMitErwartetemFehler(
                        "Zweite Freigabe — VALIDATION erwartet (nur ANGELEGT ist freigebbar)",
                        FachlicheException.VALIDATION,
                        (s, ich, kollege, ctx) -> {
                            s.freigebenZahlung((Long) ctx.get("zahlungId"), kollege);
                            return "unerwartet erfolgreich — Doppel Freigabe wäre durchgerutscht";
                        }));

        List<Erwartung> erwartungen = List.of(
                Erwartung.einsVon("Zahlung BEREIT (oder bereits zur Kasse übergeben)",
                        "SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?",
                        ctx -> List.of(ctx.get("zahlungId")), "BEREIT|UEBERGEBEN"),
                Erwartung.betrag("Budget korrekt gemindert",
                        "SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?",
                        ctx -> List.of(ctx.get("bgId")), budget.subtract(zahlbetrag).toPlainString()));
        return new Auftrag(nr, "NEG-DOPPELFREIGABE", az, name,
                "Doppelte Freigabe abgewehrt", aktionen, erwartungen)
                .dokumentiere("Hintergrund: Eine Zahlung durchläuft eine feste "
                        + "Zustandskette — angelegt, freigegeben, an die Kasse übergeben — "
                        + "und jeder Übergang darf nur einmal stattfinden. Wiederholte "
                        + "Bearbeitung desselben Schritts wäre wie ein Stempel, der zweimal "
                        + "auf dasselbe Dokument gedrückt wird: Es entsteht Unordnung, und im "
                        + "schlimmsten Fall zählt etwas doppelt. In diesem Testfall läuft "
                        + "alles zunächst korrekt: Die Auszahlung wird angeordnet (ANGELEGT) "
                        + "und von einer zweiten Kraft ordnungsgemäß freigegeben (BEREIT) — "
                        + "genau wie es das Vier-Augen-Prinzip verlangt. Dann versucht "
                        + "derselbe Kollege, NOCHMALS zu freigeben; im Arbeitsalltag "
                        + "geschieht das durch Vergessen (Wurde der Fall schon erledigt?), "
                        + "Doppelklick oder ein veraltetes Fenster, das noch offen ist. "
                        + "Erwartet wird der Fachfehler VALIDATION mit dem klaren Hinweis, "
                        + "dass nur angelegte Zahlungen freigegeben werden können. Geprüft "
                        + "wird der Datenbank-Endzustand: Die Zahlung bleibt wirksam und "
                        + "eindeutig im Status BEREIT (oder wurde zwischenzeitlich regulär an "
                        + "die Kasse übergeben), das Budget ist EXAKT EINMAL gemindert, und es "
                        + "gibt keinen zweiten Freigabevermerk. Ein Fehler wäre ein "
                        + "Doppelbuchungseffekt, ein übersprungener Status oder ein zweiter "
                        + "Eintrag im Freigabefeld — alles Anzeichen, dass die Zustandskette "
                        + "nicht dicht ist.", rechnungen);
    }

    /** Storno nach Kassenübergabe → VALIDATION; Budget darf nicht zurückgebucht werden. */
    private Auftrag negStornoNachUebergabe(int nr) {
        int rbs = 1 + random.nextInt(3);
        BigDecimal miete = zufallsBetrag(250, 500, 10);
        BigDecimal betrieb = zufallsBetrag(30, 90, 5);
        BigDecimal heizung = zufallsBetrag(50, 120, 5);
        List<SozialFall> faelle = List.of(fallEntwurf(rbs, false, false, 0, false,
                miete, betrieb, heizung, "0", "0"));
        List<Rechenblock> rechnungen = new ArrayList<>();
        BigDecimal zahlbetrag = berechne(rechnungen, "Anordnung, Freigabe, Kassenübergabe",
                faelle, BigDecimal.ZERO, null);
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
                SzenarioAktion.schritt("SozialFall erfassen", (s, ich, kollege, ctx) -> {
                    s.legeSozialFallAn((Long) ctx.get("bgId"), fallEntwurf(rbs, false, false,
                            0, false, miete, betrieb, heizung, "0", "0"), ich);
                    return "RBS " + rbs;
                }),
                SzenarioAktion.schritt("Auszahlung anordnen", (s, ich, kollege, ctx) -> {
                    var erg = s.ordneAuszahlungAn(new Auszahlungsauftrag((Long) ctx.get("bgId"),
                            UUID.randomUUID().toString(), "2026-10", ich));
                    ctx.put("zahlungId", erg.zahlung().id());
                    return "Zahlung " + erg.zahlung().id() + " (ANGELEGT)";
                }),
                SzenarioAktion.schritt("Freigabe durch Kollegen (Vier-Augen)", (s, ich, kollege, ctx) -> {
                    s.freigebenZahlung((Long) ctx.get("zahlungId"), kollege);
                    return "freigegeben durch " + kollege;
                }),
                SzenarioAktion.schritt("Kassenlauf: Zahlung an die Kasse übergeben", (s, ich, kollege, ctx) -> {
                    var kasse = s.uebergebeBereiteZahlungen(ich);
                    return "Kassenlauf: " + kasse.anzahl() + " Zahlungen über "
                            + kasse.summe().toPlainString() + " EUR (UEBERGEBEN)";
                }),
                SzenarioAktion.schrittMitErwartetemFehler(
                        "Storno nach Kassenübergabe — VALIDATION erwartet",
                        FachlicheException.VALIDATION,
                        (s, ich, kollege, ctx) -> {
                            s.storniereZahlung((Long) ctx.get("zahlungId"), ich);
                            return "unerwartet erfolgreich — Storno nach Übergabe wäre durchgerutscht";
                        }));

        List<Erwartung> erwartungen = List.of(
                Erwartung.text("Zahlung bleibt an die Kasse übergeben",
                        "SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?",
                        ctx -> List.of(ctx.get("zahlungId")), "UEBERGEBEN"),
                Erwartung.betrag("Budget NICHT zurückgebucht (Storno abgewehrt)",
                        "SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?",
                        ctx -> List.of(ctx.get("bgId")), budget.subtract(zahlbetrag).toPlainString()));
        return new Auftrag(nr, "NEG-STORNO-KASSE", az, name,
                "Storno nach Kassenübergabe abgewehrt", aktionen, erwartungen)
                .dokumentiere("Hintergrund: Der Kassenlauf ist die Übergabestelle an die "
                        + "Zahlstelle — vergleichbar mit dem Moment, in dem ein Überweisungs-"
                        + "stapel unterschrieben und zur Bank gebracht wird. Was übergeben "
                        + "ist, ist aus dem Buchungssystem hinaus und beim Zahlweg; ein "
                        + "einfaches Storno im Fachsystem kann es nicht mehr zurückholen. "
                        + "Würde das System ein solches Storno trotzdem durchführen, wäre das "
                        + "Geld plötzlich doppelt verfügbar: einmal auf dem Weg zum Empfänger "
                        + "und gleichzeitig wieder als Budget im Vorgang. In diesem Testfall "
                        + "durchläuft eine Zahlung zunächst den kompletten korrekten Weg: "
                        + "Anordnung (ANGELEGT), Vier-Augen-Freigabe durch einen Kollegen "
                        + "(BEREIT) und Übergabe im Kassenlauf (UEBERGEBEN). Erst DANACH "
                        + "versucht der Sachbearbeiter den Storno — eine Situation, die "
                        + "realistisch ist, wenn sich eine Entscheidung zu spät ändert. "
                        + "Erwartet wird der Fachfehler VALIDATION: Nur angelegte oder "
                        + "freigegebene Zahlungen dürfen storniert werden. Eine Rückholung "
                        + "aus der Kasse würde einen eigenen, bewusst getrennten Weg "
                        + "erfordern. Geprüft wird der Datenbank-Endzustand auf beiden "
                        + "Seiten: Die Zahlung steht weiterhin auf UEBERGEBEN, UND das "
                        + "Restbudget wurde NICHT erhöht — beide Hälften des doppelten "
                        + "Geldes dürfen nicht entstanden sein. Ein Fehler wäre jeder der "
                        + "beiden Effekte allein schon; zusammen wären sie eine doppelte "
                        + "Verfügung über dasselbe Geld.", rechnungen);
    }

    /** Ungültige SEPA-Bankverbindung bei der Vorgangsanlage → VALIDATION (zweifach:
     *  erst IBAN mit falscher Modulo-97-Prüfziffer, dann gültige IBAN mit auf 7 Zeichen
     *  gekürztem BIC — ISO 9362 erlaubt nur 8 oder 11 Zeichen). */
    private Auftrag negUngueltigeBankverbindung(int nr) {
        BigDecimal budget = zufallsBetrag(900, 1400, 50);
        String name = hauptperson();
        String az = aktenzeichen(nr);

        List<SzenarioAktion> aktionen = List.of(
                SzenarioAktion.schrittMitErwartetemFehler(
                        "Vorgang anlegen mit IBAN mit falscher Prüfziffer — VALIDATION erwartet",
                        FachlicheException.VALIDATION,
                        (s, ich, kollege, ctx) -> {
                            s.legeBedarfsgemeinschaftAn(az, name, budget, null,
                                    "DE00370400440532013000", "MARKDEF1100", name, ich);
                            return "unerwartet erfolgreich — ungültige IBAN wäre durchgerutscht";
                        }),
                SzenarioAktion.schrittMitErwartetemFehler(
                        "Vorgang anlegen mit zu kurzem BIC (7 Zeichen) — VALIDATION erwartet",
                        FachlicheException.VALIDATION,
                        (s, ich, kollege, ctx) -> {
                            s.legeBedarfsgemeinschaftAn(az, name, budget, null,
                                    "DE89370400440532013000", "MARKDEF", name, ich);
                            return "unerwartet erfolgreich — zu kurzer BIC wäre durchgerutscht";
                        }));

        List<Erwartung> erwartungen = List.of(
                Erwartung.ganzeZahl("kein Vorgang mit dem fehlerhaften Aktenzeichen angelegt",
                        "SELECT COUNT(*) FROM BEDARFSGEMEINSCHAFT WHERE AKTENZEICHEN = ?",
                        ctx -> List.of(az), 0));
        return new Auftrag(nr, "NEG-BANKVERBINDUNG", az, name,
                "Ungültige SEPA-Daten abgewehrt (IBAN-Prüfziffer, BIC-Länge)", aktionen, erwartungen)
                .dokumentiere("Hintergrund: Eine Auszahlung nützt der Person nur, wenn das "
                        + "Geld tatsächlich ankommen kann. Bankverbindungen tragen deshalb "
                        + "eingebaute Selbstprüfungen: Die IBAN (internationale Kontonummer) "
                        + "endet auf eine Prüfziffer, die aus den übrigen Zeichen berechnet "
                        + "wird — tippt man eine Ziffer falsch, passt die Rechnung nicht mehr, "
                        + "und die Nummer ist als ungültig erkennbar (Norm ISO 13616, "
                        + "Modulo-97-Verfahren). Der BIC (Bankcode) wiederum hat eine feste "
                        + "Länge von genau 8 oder 11 Zeichen (Norm ISO 9362). Beides sind "
                        + "reine Formalprüfungen — sie sagen nicht, ob das Konto existiert, "
                        + "aber sie erkennen Tippfehler zuverlässig, bevor Daten verschickt "
                        + "werden. In diesem Testfall werden beim Anlegen eines Vorgangs zwei "
                        + "bewusst fehlerhafte Bankverbindungen nacheinander versucht: zuerst "
                        + "eine IBAN mit falscher Prüfziffer, dann eine zwar gültige IBAN, "
                        + "kombiniert mit einem auf 7 Zeichen gekürzten BIC. Erwartet wird, "
                        + "dass die SEPA-Validierung BEIDE Anlagen mit dem Fachfehler "
                        + "VALIDATION abwehrt — wie ein Formularprüfer, der unvollständige "
                        + "Bankdaten zurückgibt, statt sie zu akzeptieren. Geprüft wird der "
                        + "Datenbank-Endzustand: Es darf GAR KEIN Vorgang mit diesem "
                        + "Aktenzeichen entstanden sein, denn der Fehler liegt ja in den "
                        + "Kerndaten. Ein Fehler wäre ein trotzdem angelegter Vorgang mit "
                        + "einer nicht überweisungsfähigen Bankverbindung — jede spätere "
                        + "Auszahlung würde ins Leere laufen oder zurückkommen.",
                        List.of());
    }

    /** Bescheid mit ungültigem Bewilligungszeitraum → VALIDATION (zweifach). */
    private Auftrag negBescheidZeitraum(int nr) {
        int rbs = 1 + random.nextInt(3);
        BigDecimal miete = zufallsBetrag(250, 500, 10);
        BigDecimal betrieb = zufallsBetrag(30, 90, 5);
        BigDecimal heizung = zufallsBetrag(50, 120, 5);
        List<SozialFall> faelle = List.of(fallEntwurf(rbs, false, false, 0, false,
                miete, betrieb, heizung, "0", "0"));
        BigDecimal budget = zufallsBetrag(900, 1400, 50);
        String name = hauptperson();
        String az = aktenzeichen(nr);

        List<SzenarioAktion> aktionen = List.of(
                SzenarioAktion.schritt("Vorgang anlegen", (s, ich, kollege, ctx) -> {
                    var bg = s.legeBedarfsgemeinschaftAn(az, name, budget, null, ich);
                    ctx.put("bgId", bg.id());
                    return "Vorgang " + bg.id();
                }),
                SzenarioAktion.schritt("SozialFall erfassen", (s, ich, kollege, ctx) -> {
                    s.legeSozialFallAn((Long) ctx.get("bgId"), fallEntwurf(rbs, false, false,
                            0, false, miete, betrieb, heizung, "0", "0"), ich);
                    return "RBS " + rbs;
                }),
                SzenarioAktion.schrittMitErwartetemFehler(
                        "Bescheid mit 'von' nach 'bis' — VALIDATION erwartet",
                        FachlicheException.VALIDATION,
                        (s, ich, kollege, ctx) -> {
                            s.erteileBescheid((Long) ctx.get("bgId"), "2026-11", "2026-10", ich);
                            return "unerwartet erfolgreich — umgekehrter Zeitraum wäre durchgerutscht";
                        }),
                SzenarioAktion.schrittMitErwartetemFehler(
                        "Bescheid über 13 Monate — VALIDATION erwartet (§ 44 Abs. 3: max. 12)",
                        FachlicheException.VALIDATION,
                        (s, ich, kollege, ctx) -> {
                            s.erteileBescheid((Long) ctx.get("bgId"), "2026-10", "2027-10", ich);
                            return "unerwartet erfolgreich — Zeitraum über 12 Monate wäre durchgerutscht";
                        }));

        List<Erwartung> erwartungen = List.of(
                Erwartung.ganzeZahl("kein Bescheid erteilt",
                        "SELECT COUNT(*) FROM BESCHEID WHERE BG_ID = ?",
                        ctx -> List.of(ctx.get("bgId")), 0));
        return new Auftrag(nr, "NEG-BESCHEID-ZEITRAUM", az, name,
                "Ungültige Bewilligungszeiträume abgewehrt", aktionen, erwartungen)
                .dokumentiere("Hintergrund: Ein Bewilligungsbescheid ist die offizielle "
                        + "Zusage, für welchen Zeitraum und in welcher Höhe Leistung gewährt "
                        + "wird — für die Person das Dokument, auf das sie sich verlassen "
                        + "darf. Deshalb muss der Bewilligungszeitraum in sich stimmig sein: "
                        + "Er hat ein Beginndatum (Monat 'von') und ein Endedatum (Monat "
                        + "'bis'), und offensichtlich muss 'von' vor 'bis' liegen. Zusätzlich "
                        + "ist die Laufzeit begrenzt: Das Gesetz (§ 44 Abs. 3 SGB XII) sieht "
                        + "Bewilligungen in der Regel für zwölf Monate vor, und das Projekt "
                        + "setzt dies als feste Obergrenze um — längerfristige Zusagen "
                        + "erforderten regelmäßig eine neue Prüfung der Verhältnisse, "
                        + "gerade weil Einkommen, Miete oder Familienlage sich ändern können. "
                        + "In diesem Testfall wird für einen ordnungsgemäß angelegten Vorgang "
                        + "zweimal ein unzulässiger Bescheid versucht: zuerst mit umgekehrter "
                        + "Reihenfolge (Beginn November 2026, Ende Oktober 2026 — der Bescheid "
                        + "würde rückwärts laufen), dann mit einer Laufzeit von 13 Monaten, "
                        + "also einen Monat über der Grenze. Erwartet wird jeweils der "
                        + "Fachfehler VALIDATION mit einer verständlichen Begründung. Geprüft "
                        + "wird der Datenbank-Endzustand: Am Ende darf KEIN erteilter Bescheid "
                        + "zu diesem Vorgang existieren — beide Fehlversuche dürfen keinerlei "
                        + "Urkunde hinterlassen. Ein Fehler wäre ein abgespeicherter Bescheid "
                        + "mit unsinnigem Zeitraum: Er wäre rechtsfehlerlich, könnten "
                        + "Ansprüche über zu lange Zeiträume vortäuschen und die Person in "
                        + "falsche Sicherheit wiegen.", List.of());
    }

    /** Operationen auf nicht existierenden Objekten → NICHT_GEFUNDEN. */
    private Auftrag negNichtGefunden(int nr) {
        BigDecimal budget = zufallsBetrag(900, 1400, 50);
        String name = hauptperson();
        String az = aktenzeichen(nr);

        List<SzenarioAktion> aktionen = List.of(
                SzenarioAktion.schritt("Vorgang anlegen (echter Vorgang als Kontrast)", (s, ich, kollege, ctx) -> {
                    var bg = s.legeBedarfsgemeinschaftAn(az, name, budget, null, ich);
                    ctx.put("bgId", bg.id());
                    return "Vorgang " + bg.id();
                }),
                SzenarioAktion.schrittMitErwartetemFehler(
                        "Freigabe einer nicht existierenden Zahlung — NICHT_GEFUNDEN erwartet",
                        FachlicheException.NICHT_GEFUNDEN,
                        (s, ich, kollege, ctx) -> {
                            s.freigebenZahlung(-999L, kollege);
                            return "unerwartet erfolgreich";
                        }),
                SzenarioAktion.schrittMitErwartetemFehler(
                        "Storno einer nicht existierenden Zahlung — NICHT_GEFUNDEN erwartet",
                        FachlicheException.NICHT_GEFUNDEN,
                        (s, ich, kollege, ctx) -> {
                            s.storniereZahlung(-999L, ich);
                            return "unerwartet erfolgreich";
                        }),
                SzenarioAktion.schrittMitErwartetemFehler(
                        "Auszahlung auf einen nicht existierenden Vorgang — NICHT_GEFUNDEN erwartet",
                        FachlicheException.NICHT_GEFUNDEN,
                        (s, ich, kollege, ctx) -> {
                            s.ordneAuszahlungAn(new Auszahlungsauftrag(-999L,
                                    UUID.randomUUID().toString(), "2026-10", ich));
                            return "unerwartet erfolgreich";
                        }));

        List<Erwartung> erwartungen = List.of(
                Erwartung.ganzeZahl("echter Vorgang unverändert vorhanden",
                        "SELECT COUNT(*) FROM BEDARFSGEMEINSCHAFT WHERE AKTENZEICHEN = ?",
                        ctx -> List.of(az), 1),
                Erwartung.ganzeZahl("keine Zahlung durch die Fehlversuche entstanden",
                        "SELECT COUNT(*) FROM ZAHLUNG WHERE BG_ID = (SELECT ID FROM "
                                + "BEDARFSGEMEINSCHAFT WHERE AKTENZEICHEN = ?)",
                        ctx -> List.of(az), 0));
        return new Auftrag(nr, "NEG-NICHTGEFUNDEN", az, name,
                "Operationen auf unbekannten Objekten abgewehrt", aktionen, erwartungen)
                .dokumentiere("Hintergrund: Ein gut gebautes System muss auch mit Anfragen "
                        + "umgehen, die ins Leere laufen — etwa wenn eine Seite aus einem "
                        + "veralteten Browserfenster heraus bedient wird, dessen Daten längst "
                        + "gelöscht oder archiviert sind, oder wenn ein Auftrag stirbt, "
                        + "während ein anderer damit arbeitet. Die zwei gefährlichen "
                        + "Fehlreaktionen wären: stilles Erfolgen (das System tut so, als "
                        + "hätte die Aktion geklappt — die Oberfläche zeigt Erfolg, in "
                        + "Wirklichkeit passierte nichts) oder ein roher Absturz mit "
                        + "Technikfehler. Beides verunsichert die Nutzer und kann echte "
                        + "Schäden verbergen. In diesem Testfall werden drei Operationen auf "
                        + "bewusst nicht existierende Objekte losgeschickt, alle mit der "
                        + "ID −999, die kein echter Datensatz tragen kann: die Freigabe "
                        + "einer Zahlung, die es nicht gibt, der Storno einer Zahlung, die "
                        + "es nicht gibt, und eine Auszahlungsanordnung für einen Vorgang, "
                        + "den es nicht gibt. Erwartet wird jeweils der Fachfehler "
                        + "NICHT_GEFUNDEN — eine klare, sachliche Meldung statt stillen "
                        + "Erfolgs oder Absturz. Zum Kontrast und als Nebenbeweis wird "
                        + "zuvor ein echter Vorgang angelegt. Geprüft wird der Datenbank-"
                        + "Endzustand: Der echte Vorgang bleibt unberührt vorhanden, und "
                        + "durch die drei Fehlversuche ist KEINE Zahlung entstanden. Ein "
                        + "Fehler wäre jede der drei Operationen mit Erfolgsmeldung — sie "
                        + "wäre gelogen — oder ein Absturz, der die Sitzung beschädigt.",
                        List.of());
    }

    // ------------------------------------------------------------------
    // LAST-Szenarien: der schlanke Standardweg in hoher Stückzahl
    // ------------------------------------------------------------------

    private Auftrag lastDurchlauf(int nr) {
        int rbs = 1 + random.nextInt(3);
        BigDecimal miete = BigDecimal.valueOf(250 + 10 * random.nextInt(31)).setScale(2);
        BigDecimal betrieb = BigDecimal.valueOf(30 + 5 * random.nextInt(19)).setScale(2);
        BigDecimal heizung = BigDecimal.valueOf(50 + 5 * random.nextInt(19)).setScale(2);
        List<SozialFall> faelle = List.of(fallEntwurf(rbs, false, false, 0, false,
                miete, betrieb, heizung, "0", "0"));
        List<Rechenblock> rechnungen = new ArrayList<>();
        BigDecimal zahlbetrag = berechne(rechnungen, "Lasttest-Durchlauf",
                faelle, BigDecimal.ZERO, null);
        BigDecimal budget = zahlbetrag.add(BigDecimal.valueOf(100 + 10 * random.nextInt(21)));
        rechnungen.replaceAll(b -> b.mitBudget(budget));
        String name = hauptperson();
        String az = aktenzeichen(nr);

        List<SzenarioAktion> aktionen = List.of(
                SzenarioAktion.schritt("Vorgang anlegen", (s, ich, kollege, ctx) -> {
                    var bg = s.legeBedarfsgemeinschaftAn(az, name, budget, null, ich);
                    ctx.put("bgId", bg.id());
                    return "Vorgang " + bg.id();
                }),
                SzenarioAktion.schritt("SozialFall erfassen", (s, ich, kollege, ctx) -> {
                    s.legeSozialFallAn((Long) ctx.get("bgId"), fallEntwurf(rbs, false, false,
                            0, false, miete, betrieb, heizung, "0", "0"), ich);
                    return "RBS " + rbs + ", KdU " + gesamtKdu(miete, betrieb, heizung) + " EUR";
                }),
                SzenarioAktion.schritt("Auszahlung anordnen (2026-10)", (s, ich, kollege, ctx) -> {
                    var erg = s.ordneAuszahlungAn(new Auszahlungsauftrag((Long) ctx.get("bgId"),
                            UUID.randomUUID().toString(), "2026-10", ich));
                    ctx.put("zahlungId", erg.zahlung().id());
                    return erg.zahlung().auszahlungsbetrag().toPlainString() + " EUR (ANGELEGT)";
                }));
        return new Auftrag(nr, "LAST-DURCHLAUF", az, name,
                "Schlanker Lasttest-Durchlauf (Anordnung ohne Freigabe)",
                aktionen, erwartungenStandard(zahlbetrag, budget, rbs, false))
                .dokumentiere("Schlanker Durchlauf für den Lasttest: Vorgang anlegen, "
                        + "SozialFall erfassen, monatliche Auszahlung anordnen — bewusst "
                        + "ohne Vier-Augen-Freigabe, damit möglichst viele Transaktionen je "
                        + "Zeiteinheit durch die Transaktionssteuerung und die Datenbank "
                        + "laufen. Geprüft wird der Datenbank-Endzustand wie bei den "
                        + "Positivfällen (Betrag, Restbudget, Fall, Status in der "
                        + "Vier-Augen-Warteschlange).", rechnungen);
    }

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
        return String.format("%s%04d", praefix, nr);
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
