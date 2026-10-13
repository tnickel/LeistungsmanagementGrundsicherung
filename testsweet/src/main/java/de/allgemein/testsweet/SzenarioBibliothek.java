package de.allgemein.testsweet;

import de.allgemein.lmg.calc.Bewilligungsplan;
import de.allgemein.lmg.calc.Leistungsberechnung;
import de.allgemein.lmg.model.SozialFall;
import de.allgemein.lmg.service.Auszahlungsauftrag;
import de.allgemein.lmg.service.Auszahlungsergebnis;
import de.allgemein.lmg.service.FachlicheException;
import de.allgemein.lmg.service.SozialLeistungsService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
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

    /** Bereits gezogene Parameter-Kombinationen des aktuellen Topfs — garantiert,
     *  dass jeder KOMBINATION-Auftrag eine wirklich andere Konstellation prüft. */
    private final Set<String> kombiSignaturen = new HashSet<>();

    /** Aktenzeichen-Präfix des aktuellen Topfs (POSITIV: SWEET-, NEGATIV: SWEET-N-,
     *  LAST: SWEET-L-); wird je erzeuge-Aufruf gesetzt, vor dem Lauf konstant. */
    private String praefix = PREFIX;

    public SzenarioBibliothek(long seed) {
        this.random = new Random(seed);
    }

    /** Erzeugt {@code anzahl} Aufträge: Jede der zehn kuratierten Szenario-Arten
     *  mindestens einmal (solange der Topf reicht), danach Auffüllung mit
     *  garantiert verschiedenen Kombinationsfällen (siehe {@link #kombinationPositiv}).
     *  Alle 50 Aufträge läuft einmal der Kassenlauf parallel mit. */
    public List<Auftrag> erzeuge(int anzahl) {
        return erzeuge(anzahl, PREFIX);
    }

    /** Wie {@link #erzeuge(int)}, aber mit eigenem Aktenzeichen-Präfix. */
    public List<Auftrag> erzeuge(int anzahl, String praefix) {
        this.praefix = praefix;
        kombiSignaturen.clear();
        List<Auftrag> auftraege = new ArrayList<>(anzahl);
        // Round-Robin über die kuratierten Arten: gleiche Grundabdeckung für jeden Topf
        for (int i = 1; i <= anzahl; i++) {
            if (i <= 10) {
                auftraege.add(switch (i) {
                    case 1 -> standardrente(i);
                    case 2 -> paar(i);
                    case 3 -> mehrbedarf(i);
                    case 4 -> familie(i);
                    case 5 -> erwerbstaetiger(i);
                    case 6 -> vermoegensfall(i);
                    case 7 -> doppelklick(i);
                    case 8 -> stornoMitKorrektur(i);
                    case 9 -> konkurrenz(i);
                    default -> kassenlauf(i);
                });
            } else if (i % 50 == 0) {
                auftraege.add(kassenlauf(i));
            } else {
                auftraege.add(kombinationPositiv(i));
            }
        }
        return auftraege;
    }

    /**
     * Erzeugt {@code anzahl} NEGATIV-Aufträge: Jede der neun kuratierten Abwehr-Arten
     * mindestens einmal, danach Auffüllung mit garantiert verschiedenen Verstoß-
     * Kombinationen (siehe {@link #kombinationNegativ}). Abgewehrte Fachfehler sind
     * das erwartete Ergebnis (schrittMitErwartetemFehler), geprüft wird der
     * unveränderte Datenbank-Endzustand (keine Zahlung, unverändertes Budget …).
     */
    public List<Auftrag> erzeugeNegativ(int anzahl, String praefix) {
        this.praefix = praefix;
        kombiSignaturen.clear();
        List<Auftrag> auftraege = new ArrayList<>(anzahl);
        for (int i = 1; i <= anzahl; i++) {
            if (i <= 9) {
                auftraege.add(switch (i) {
                    case 1 -> negKeinAnspruch(i);
                    case 2 -> negBudget(i);
                    case 3 -> negDoppelterMonat(i);
                    case 4 -> negSelbstfreigabe(i);
                    case 5 -> negDoppelfreigabe(i);
                    case 6 -> negStornoNachUebergabe(i);
                    case 7 -> negUngueltigeBankverbindung(i);
                    case 8 -> negBescheidZeitraum(i);
                    default -> negNichtGefunden(i);
                });
            } else {
                auftraege.add(kombinationNegativ(i));
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
                            ctx -> List.of(monat, az), "ANGELEGT"),
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
                            ctx -> List.of(az), 2),
                    erwartungNichtFreigegeben("zahlungId"));
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
        LocalDate wiedervorlage = LocalDate.now().plusMonths(6);

        List<SzenarioAktion> aktionen = List.of(
                SzenarioAktion.schritt("Vorgang anlegen (Familie)", (s, ich, kollege, ctx) -> {
                    var bg = s.legeBedarfsgemeinschaftAn(az, name, budget, wiedervorlage, ich);
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
        List<Erwartung> erwartungen = new ArrayList<>(
                erwartungenStandard(zahlbetrag, budget, 0, false));
        erwartungen.add(Erwartung.ganzeZahl("Wiedervorlagedatum exakt wie gesetzt",
                "SELECT COUNT(*) FROM BEDARFSGEMEINSCHAFT WHERE ID = ? AND WIEDERVORLAGE_AM = ?",
                ctx -> List.of(ctx.get("bgId"), java.sql.Date.valueOf(wiedervorlage)), 1));
        return new Auftrag(nr, "FAMILIE", az, name,
                "Alleinerziehende mit " + kinder + " Kind(ern) (§ 30 Abs. 3)",
                aktionen, erwartungen)
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
        rechnungen.replaceAll(b -> b.mitBudget(budget)
                .mitBudgetRestExpected(keinAnspruch ? budget : budget.subtract(zahlbetrag)));
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
        // Verbrauchsfortschreibung (§ 90 SGB XII, seit v0.5.6): Bei erfolgreicher
        // Auszahlung verbraucht sich der über dem Schonvermögen liegende Anteil —
        // danach liegt der Rest im Schonvermögen und wird nicht erneut abgezogen.
        BigDecimal vermoegenseinsatz = vermoegen
                .subtract(Leistungsberechnung.schonvermoegen(faelle)).max(BigDecimal.ZERO);
        BigDecimal vermoegenNachLauf = keinAnspruch ? vermoegen : vermoegen.subtract(vermoegenseinsatz);
        erwartungen.add(Erwartung.betrag("Vermögen nach Verbrauchsfortschreibung (§ 90)",
                "SELECT VERMOEGEN FROM BEDARFSGEMEINSCHAFT WHERE ID = ?",
                ctx -> List.of(ctx.get("bgId")), vermoegenNachLauf.toPlainString()));
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
            erwartungen.add(erwartungNichtFreigegeben("zahlungId"));
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
                Erwartung.ganzeZahl("genau ein Auszahlungsjournal-Eintrag (keine Doppelbuchung)",
                        "SELECT COUNT(*) FROM JOURNAL WHERE BG_ID = ? "
                                + "AND AKTION_TEXT LIKE 'Auszahlung über%'",
                        ctx -> List.of(ctx.get("bgId")), 1),
                Erwartung.betrag("Budget nur einmal gemindert",
                        "SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?",
                        ctx -> List.of(ctx.get("bgId")), budget.subtract(zahlbetrag).toPlainString()),
                erwartungNichtFreigegeben("zahlungId"));
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
        rechnungen.replaceAll(b -> b.mitBudget(budget)
                .mitBudgetRestExpected(budget.subtract(zahlbetragNeu)));
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
                SzenarioAktion.schritt("Angelegte Zahlung stornieren (Budget zurückbuchen)", (s, ich, kollege, ctx) -> {
                    s.storniereZahlung((Long) ctx.get("alteZahlungId"), ich);
                    return "ANGELEGT → STORNIERT — Budget zurückgebucht";
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
                Erwartung.text("neue Zahlung wartet auf Vier-Augen-Freigabe",
                        "SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?",
                        ctx -> List.of(ctx.get("neueZahlungId")), "ANGELEGT"),
                Erwartung.betrag("korrigierter neuer Zahlbetrag",
                        "SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?",
                        ctx -> List.of(ctx.get("neueZahlungId")), zahlbetragNeu.toPlainString()),
                Erwartung.betrag("Budget: Storno zurück + Neuauszahlung gemindert",
                        "SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?",
                        ctx -> List.of(ctx.get("bgId")), budget.subtract(zahlbetragNeu).toPlainString()),
                Erwartung.ganzeZahl("Fall in Version 2 (optimistisches Locking)",
                        "SELECT VERSION FROM SOZIAL_FALL WHERE ID = ?",
                        ctx -> List.of(ctx.get("fallId")), 2),
                erwartungNichtFreigegeben("neueZahlungId"));
        return new Auftrag(nr, "STORNO_KORREKTUR", az, name,
                "Storno + fachliche Korrektur + Neuauszahlung im selben Monat",
                aktionen, erwartungen)
                .dokumentiere("Ein Fall mit realistischem Korrekturbedarf: Zuerst wird mit "
                        + "einer (zu niedrigen) Miete angeordnet. Vor der Freigabe fällt "
                        + "der Fehler auf — etwa durch eine Nachmeldung des Vermieters. Der "
                        + "Sachbearbeiter storniert die angelegte Zahlung: Damit muss das "
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
        String kassenPraefix = praefix;
        List<SzenarioAktion> aktionen = List.of(
                SzenarioAktion.schritt("Kassenlauf über alle bereiten Zahlungen", (s, ich, kollege, ctx) -> {
                    var erg = s.uebergebeBereiteZahlungen(ich, kassenPraefix);
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
    // KOMBINATIONS-Szenarien: systematisch gestreute Haushalte und Verstöße.
    // Jeder Auftrag zieht eine andere Konstellation (Signatur-Menge); die
    // Erwartungshaltung entsteht wie immer mit derselben reinen Berechnung,
    // die auch die Fachschicht nutzt — inkl. Entbindungsmonat-Regel
    // (Bewilligungsplan.faelleFuerMonat) und Vermögensverbrauch (§ 90).
    // ------------------------------------------------------------------

    /** Zahlungsmonate mit SATZSTAND-Belag (Seed 2026-01) — Feb..Nov lässt dem
     *  Bewilligungszeitraum Platz nach beiden Seiten. */
    private static final String[] MONATE_2026 = {
            "2026-01", "2026-02", "2026-03", "2026-04", "2026-05", "2026-06",
            "2026-07", "2026-08", "2026-09", "2026-10", "2026-11", "2026-12"};

    /** Positiver Kombinationsfall: zufälliger Haushalt (Single/Paar/Alleinerziehend/
     *  Paar mit Kindern), gestreute Merkmale, Einkommen, Vermögen und ein zufälliger
     *  Weg (Bescheid, Freigabe, Doppelklick-Idempotenz, Storno+Neu-Anordnung).
     *  Wiederholte Konstellationen werden über die Signatur ausgeschlossen. */
    private Auftrag kombinationPositiv(int nr) {
        for (int versuch = 0; versuch < 5000; versuch++) {
            // ---------------- Haushalt ziehen ----------------
            int variante = random.nextInt(4); // 0 Single, 1 Paar, 2 Alleinerziehend, 3 Paar+Kinder
            int monatIdx = 1 + random.nextInt(10);
            String monat = MONATE_2026[monatIdx];
            BigDecimal miete = zufallsBetrag(200, 750, 10);
            BigDecimal betrieb = zufallsBetrag(20, 140, 5);
            BigDecimal heizung = zufallsBetrag(30, 170, 5);
            // KdU-Split nur bei Zwei-Personen-Haushalten (Service summiert über die Fälle)
            boolean kduSplit = (variante == 1 || variante == 3) && random.nextBoolean();

            List<SozialFall> faelle = new ArrayList<>();
            String haushalt;
            if (variante == 0) {
                haushalt = "Single";
                faelle.add(kombiFall(1, monatIdx, miete, betrieb, heizung, 0, 0));
            } else if (variante == 1) {
                haushalt = "Paar (2 × RBS 2)";
                faelle.add(kombiFall(2, monatIdx, kduSplit ? miete : miete,
                        kduSplit ? BigDecimal.ZERO : betrieb,
                        kduSplit ? BigDecimal.ZERO : heizung, 0, 0));
                faelle.add(kombiFall(2, monatIdx, BigDecimal.ZERO,
                        kduSplit ? betrieb : BigDecimal.ZERO,
                        kduSplit ? heizung : BigDecimal.ZERO, 0, 0));
            } else if (variante == 2) {
                int kinder = 1 + random.nextInt(3);
                int u7 = random.nextInt(Math.min(2, kinder) + 1);
                haushalt = "Alleinerziehend mit " + kinder + " Kind(ern)";
                faelle.add(kombiFall(1, monatIdx, miete, betrieb, heizung, kinder, u7));
                for (int k = 0; k < kinder; k++) {
                    faelle.add(kombiFall(4 + random.nextInt(3), monatIdx,
                            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0, 0));
                }
            } else {
                int kinder = 1 + random.nextInt(2);
                haushalt = "Paar mit " + kinder + " Kind(ern)";
                faelle.add(kombiFall(2, monatIdx, kduSplit ? miete : miete,
                        kduSplit ? BigDecimal.ZERO : betrieb,
                        kduSplit ? BigDecimal.ZERO : heizung, 0, 0));
                faelle.add(kombiFall(2, monatIdx, BigDecimal.ZERO,
                        kduSplit ? betrieb : BigDecimal.ZERO,
                        kduSplit ? heizung : BigDecimal.ZERO, 0, 0));
                for (int k = 0; k < kinder; k++) {
                    faelle.add(kombiFall(4 + random.nextInt(3), monatIdx,
                            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0, 0));
                }
            }

            // ---------------- Erwartung rechnen (Entbindungsmonat beachten!) ----------------
            List<SozialFall> wirksam = Bewilligungsplan.faelleFuerMonat(faelle, monat);
            var ohneVermoegen = Leistungsberechnung.berechne(0, wirksam,
                    Leistungsberechnung.KduParameter.AUS, BigDecimal.ZERO);
            if (ohneVermoegen.zahlbetrag().signum() <= 0) {
                continue; // Einkommen deckt den Bedarf — für einen Positivfall neu ziehen
            }

            // ---------------- Vermögen (nur so hoch, dass ein Restanspruch bleibt) ----------------
            BigDecimal vermoegen = BigDecimal.ZERO;
            boolean mitVermoegen = random.nextInt(100) < 45;
            if (mitVermoegen) {
                BigDecimal schon = Leistungsberechnung.schonvermoegen(wirksam);
                BigDecimal spielraum = ohneVermoegen.zahlbetrag().min(BigDecimal.valueOf(6000));
                if (spielraum.compareTo(BigDecimal.ONE) < 0) {
                    continue;
                }
                vermoegen = schon.add(zufallsBetrag(0, spielraum.intValue() - 1, 50));
            }
            var ergebnis = Leistungsberechnung.berechne(0, wirksam,
                    Leistungsberechnung.KduParameter.AUS, vermoegen);
            BigDecimal zahlbetrag = ergebnis.zahlbetrag();
            BigDecimal verwertbar = ergebnis.verwertbaresVermoegen();

            // ---------------- Weg ziehen ----------------
            boolean mitStornoNeu = verwertbar.signum() == 0 && random.nextInt(100) < 15;
            boolean mitDoppelklick = !mitStornoNeu && random.nextInt(100) < 20;
            boolean mitFreigabe = random.nextInt(100) < 45;
            boolean mitBescheid = random.nextInt(100) < 35;
            boolean mitWiedervorlage = random.nextInt(100) < 20;
            BigDecimal mieteNeu = BigDecimal.ZERO;
            BigDecimal zahlbetragNeu = BigDecimal.ZERO;
            List<SozialFall> wirksamNeu = null;
            if (mitStornoNeu) {
                mieteNeu = miete.add(zufallsBetrag(30, 150, 10));
                SozialFall erster = faelle.get(0);
                List<SozialFall> faelleNeu = new ArrayList<>(faelle);
                faelleNeu.set(0, new SozialFall(erster.id(), erster.bgId(),
                        erster.regelbedarfsstufe(), erster.merkzeichenG(), erster.schwanger(),
                        erster.anzKinderUnter7(), erster.anzKinderUnter16(),
                        erster.anzKinderMinderjaehrig(), erster.warmwasser(), mieteNeu,
                        erster.kduBetriebskosten(), erster.kduHeizung(), erster.einkommenRente(),
                        erster.einkommenErwerb(), erster.abzSteuern(), erster.abzSozialversicherung(),
                        erster.abzVersicherungen(), erster.abzErwerbsaufwendungen(),
                        erster.entbindungsmonat(), erster.version()));
                wirksamNeu = Bewilligungsplan.faelleFuerMonat(faelleNeu, monat);
                zahlbetragNeu = Leistungsberechnung.berechne(0, wirksamNeu,
                        Leistungsberechnung.KduParameter.AUS, vermoegen).zahlbetrag();
            }
            BigDecimal budget = (mitStornoNeu ? zahlbetragNeu : zahlbetrag)
                    .add(zufallsBetrag(50, 400, 10));
            LocalDate wiedervorlage = mitWiedervorlage
                    ? LocalDate.now().plusDays(20 + random.nextInt(200)) : null;
            int bescheidVonIdx = 0;
            int bescheidBisIdx = 0;
            if (mitBescheid) {
                int a = random.nextInt(Math.min(3, monatIdx) + 1);
                int bRest = 11 - a - (monatIdx - a);
                int b = random.nextInt(Math.max(0, Math.min(bRest, 11 - monatIdx)) + 1);
                bescheidVonIdx = monatIdx - a;
                bescheidBisIdx = monatIdx + b;
            }

            // ---------------- Eindeutigkeit der Konstellation ----------------
            String sig = "POSITIV|" + monat + "|" + vermoegen + "|" + budget
                    + "|" + mitStornoNeu + "|" + mitDoppelklick + "|" + (mitFreigabe && !mitStornoNeu)
                    + "|" + (mitBescheid ? bescheidVonIdx + "/" + bescheidBisIdx : "-")
                    + "|" + wiedervorlage + "|" + (mitStornoNeu ? mieteNeu : "-")
                    + "|" + faelle.stream().map(SzenarioBibliothek::fallSignatur).toList();
            if (!kombiSignaturen.add(sig)) {
                continue;
            }

            // ---------------- Auftrag bauen ----------------
            String name = hauptperson();
            String az = aktenzeichen(nr);
            boolean schwangerschaftEntfiel = faelle.stream().anyMatch(SozialFall::schwanger)
                    && wirksam.stream().noneMatch(SozialFall::schwanger);
            List<Rechenblock> rechnungen = new ArrayList<>();
            rechnungen.add(new Rechenblock("Kombinationsfall " + nr + ": " + haushalt
                    + (schwangerschaftEntfiel
                            ? " (Schwangerschafts-Mehrbedarf entfällt im Zahlungsmonat)" : ""),
                    monat, wirksam, vermoegen, budget, ergebnis, List.of(
                    "Erwartungswert mit derselben Berechnung wie die Fachschicht — je Person Regelbedarf, "
                            + "Mehrbedarfe (§ 30), Wohnkosten und Einkommen (§ 82), dazu das Vermögen der "
                            + "Bedarfsgemeinschaft (§§ 43, 90).")));
            if (mitStornoNeu) {
                rechnungen.add(new Rechenblock("Neuanordnung nach Mietekorrektur (Miete "
                        + miete.toPlainString() + " → " + mieteNeu.toPlainString() + ")", monat,
                        wirksamNeu, vermoegen, budget, Leistungsberechnung.berechne(0, wirksamNeu,
                                Leistungsberechnung.KduParameter.AUS, vermoegen),
                        List.of("Nach Storno und Mietekorrektur gilt der neu berechnete Zahlbetrag; "
                                + "das zurückgebuchte Budget fließt wieder ein.")));
            }

            List<SzenarioAktion> aktionen = new ArrayList<>();
            BigDecimal vermoegenAnlage = vermoegen;
            LocalDate wiedervorlageFinal = wiedervorlage;
            aktionen.add(SzenarioAktion.schritt("Vorgang anlegen (" + haushalt + ", Budget "
                    + budget.toPlainString() + " EUR" + (vermoegenAnlage.signum() > 0
                    ? ", Vermögen " + vermoegenAnlage.toPlainString() + " EUR" : "") + ")",
                    (s, ich, kollege, ctx) -> {
                        var bg = vermoegenAnlage.signum() > 0
                                ? s.legeBedarfsgemeinschaftAn(az, name, budget, wiedervorlageFinal,
                                        "DE89370400440532013000", "MARKDEF1100", name, vermoegenAnlage, ich)
                                : s.legeBedarfsgemeinschaftAn(az, name, budget, wiedervorlageFinal, ich);
                        ctx.put("bgId", bg.id());
                        return "Vorgang " + bg.id();
                    }));
            aktionen.add(SzenarioAktion.schritt(faelle.size() + " SozialFall/Fälle erfassen",
                    (s, ich, kollege, ctx) -> {
                        for (SozialFall f : faelle) {
                            s.legeSozialFallAn((Long) ctx.get("bgId"), f, ich);
                        }
                        return haushalt + ", KdU " + gesamtKdu(miete, betrieb, heizung) + " EUR";
                    }));
            String bescheidVon = MONATE_2026[bescheidVonIdx];
            String bescheidBis = MONATE_2026[bescheidBisIdx];
            if (mitBescheid) {
                aktionen.add(SzenarioAktion.schritt("Bewilligungsbescheid erteilen ("
                        + bescheidVon + " .. " + bescheidBis + ")", (s, ich, kollege, ctx) -> {
                    var bescheid = s.erteileBescheid((Long) ctx.get("bgId"), bescheidVon, bescheidBis, ich);
                    return "Bescheid " + bescheid.bescheidNr() + " mit Monatsplan gespeichert";
                }));
            }
            String monatFinal = monat;
            aktionen.add(SzenarioAktion.schritt("Auszahlung anordnen (" + monatFinal + ")",
                    (s, ich, kollege, ctx) -> {
                        String vorgangsNr = UUID.randomUUID().toString();
                        ctx.put("vorgangsNr", vorgangsNr);
                        var erg = s.ordneAuszahlungAn(new Auszahlungsauftrag((Long) ctx.get("bgId"),
                                vorgangsNr, monatFinal, ich));
                        ctx.put("zahlungId", erg.zahlung().id());
                        ctx.put("letzteZahlungId", erg.zahlung().id());
                        return erg.zahlung().auszahlungsbetrag().toPlainString() + " EUR (ANGELEGT)";
                    }));
            if (mitDoppelklick) {
                aktionen.add(SzenarioAktion.schritt("Doppelklick: gleiche Vorgangsnummer erneut",
                        (s, ich, kollege, ctx) -> {
                            Auszahlungsergebnis wiederholt = s.ordneAuszahlungAn(new Auszahlungsauftrag(
                                    (Long) ctx.get("bgId"), (String) ctx.get("vorgangsNr"), monatFinal, ich));
                            if (!wiederholt.bereitsGebucht()) {
                                throw new IllegalStateException("Idempotenz verletzt");
                            }
                            return "korrekt als bereits gebucht erkannt";
                        }));
            }
            if (mitFreigabe && !mitStornoNeu) {
                aktionen.add(SzenarioAktion.schritt("Freigabe durch Kollegen (Vier-Augen)",
                        (s, ich, kollege, ctx) -> {
                            s.freigebenZahlung((Long) ctx.get("zahlungId"), kollege);
                            return "freigegeben durch " + kollege;
                        }));
            }
            BigDecimal mieteNeuFinal = mieteNeu;
            if (mitStornoNeu) {
                aktionen.add(SzenarioAktion.schritt("Anordnung sofort stornieren (Status ANGELEGT)",
                        (s, ich, kollege, ctx) -> {
                            s.storniereZahlung((Long) ctx.get("zahlungId"), ich);
                            return "storniert — Monat wieder frei";
                        }));
                aktionen.add(SzenarioAktion.schritt("Miete auf " + mieteNeuFinal.toPlainString()
                        + " korrigieren", (s, ich, kollege, ctx) -> {
                    SozialFall geladen = s.ladeDetail((Long) ctx.get("bgId")).faelle().get(0);
                    SozialFall korrigiert = new SozialFall(geladen.id(), geladen.bgId(),
                            geladen.regelbedarfsstufe(), geladen.merkzeichenG(), geladen.schwanger(),
                            geladen.anzKinderUnter7(), geladen.anzKinderUnter16(),
                            geladen.anzKinderMinderjaehrig(), geladen.warmwasser(),
                            mieteNeuFinal, geladen.kduBetriebskosten(), geladen.kduHeizung(),
                            geladen.einkommenRente(), geladen.einkommenErwerb(),
                            geladen.abzSteuern(), geladen.abzSozialversicherung(),
                            geladen.abzVersicherungen(), geladen.abzErwerbsaufwendungen(),
                            geladen.entbindungsmonat(), geladen.version());
                    s.aktualisiereSozialFall(korrigiert, geladen.version(), ich);
                    ctx.put("fallId", geladen.id());
                    return "Fall " + geladen.id() + " in Version 2";
                }));
                aktionen.add(SzenarioAktion.schritt("Korrigierte Auszahlung für denselben Monat",
                        (s, ich, kollege, ctx) -> {
                            var erg = s.ordneAuszahlungAn(new Auszahlungsauftrag((Long) ctx.get("bgId"),
                                    UUID.randomUUID().toString(), monatFinal, ich));
                            ctx.put("letzteZahlungId", erg.zahlung().id());
                            return erg.zahlung().auszahlungsbetrag().toPlainString() + " EUR";
                        }));
            }

            // ---------------- Erwartungen ----------------
            List<Erwartung> erwartungen = new ArrayList<>();
            BigDecimal finalerZahlbetrag = mitStornoNeu ? zahlbetragNeu : zahlbetrag;
            rechnungen.replaceAll(b -> b.mitBudgetRestExpected(budget.subtract(finalerZahlbetrag)));
            erwartungen.add(Erwartung.ganzeZahl("alle Personen als eigene SozialFall-Zeilen",
                    "SELECT COUNT(*) FROM SOZIAL_FALL WHERE BG_ID = ?",
                    ctx -> List.of(ctx.get("bgId")), faelle.size()));
            erwartungen.add(Erwartung.einsVon(mitFreigabe && !mitStornoNeu
                            ? "finale Zahlung freigegeben (oder bereits übergeben)"
                            : "finale Zahlung wartet auf Vier-Augen-Freigabe",
                    "SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?",
                    ctx -> List.of(ctx.get("letzteZahlungId")),
                    mitFreigabe && !mitStornoNeu ? "BEREIT|UEBERGEBEN" : "ANGELEGT"));
            if (!mitFreigabe || mitStornoNeu) {
                erwartungen.add(erwartungNichtFreigegeben("letzteZahlungId"));
            }
            erwartungen.add(Erwartung.betrag("Auszahlungsbetrag entspricht der Berechnung",
                    "SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?",
                    ctx -> List.of(ctx.get("letzteZahlungId")), finalerZahlbetrag.toPlainString()));
            erwartungen.add(Erwartung.betrag("Restbudget korrekt gemindert",
                    "SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?",
                    ctx -> List.of(ctx.get("bgId")), budget.subtract(finalerZahlbetrag).toPlainString()));
            if (mitDoppelklick) {
                erwartungen.add(Erwartung.ganzeZahl("genau eine Zahlung trotz Wiederholung",
                        "SELECT COUNT(*) FROM ZAHLUNG WHERE BG_ID = ?",
                        ctx -> List.of(ctx.get("bgId")), 1));
            }
            if (mitStornoNeu) {
                erwartungen.add(Erwartung.ganzeZahl("zwei Zahlungen (stornierte + korrigierte)",
                        "SELECT COUNT(*) FROM ZAHLUNG WHERE BG_ID = ?",
                        ctx -> List.of(ctx.get("bgId")), 2));
                erwartungen.add(Erwartung.text("erste Zahlung STORNIERT",
                        "SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?",
                        ctx -> List.of(ctx.get("zahlungId")), "STORNIERT"));
                erwartungen.add(Erwartung.ganzeZahl("Fall in Version 2 (optimistisches Locking)",
                        "SELECT VERSION FROM SOZIAL_FALL WHERE ID = ?",
                        ctx -> List.of(ctx.get("fallId")), 2));
            }
            if (mitFreigabe && !mitStornoNeu) {
                erwartungen.add(Erwartung.text("Freigabevermerk nennt den Kollegen (Vier-Augen)",
                        "SELECT CASE WHEN FREIGEGEBEN_VON = ? THEN 'ok' ELSE 'abweichend' END "
                                + "FROM ZAHLUNG WHERE ID = ?",
                        ctx -> List.of(ctx.get("kollege"), ctx.get("letzteZahlungId")), "ok"));
            }
            if (mitBescheid) {
                erwartungen.add(Erwartung.ganzeZahl("genau ein erteilter Bewilligungsbescheid",
                        "SELECT COUNT(*) FROM BESCHEID WHERE BG_ID = ?",
                        ctx -> List.of(ctx.get("bgId")), 1));
            }
            if (verwertbar.signum() > 0) {
                erwartungen.add(Erwartung.betrag("Vermögen nach Verbrauchsfortschreibung (§ 90)",
                        "SELECT VERMOEGEN FROM BEDARFSGEMEINSCHAFT WHERE ID = ?",
                        ctx -> List.of(ctx.get("bgId")), vermoegen.subtract(verwertbar).toPlainString()));
                erwartungen.add(Erwartung.betrag("dokumentierter Vermögenseinsatz an der Zahlung",
                        "SELECT VERMOEGENSEINSATZ FROM ZAHLUNG WHERE ID = ?",
                        ctx -> List.of(ctx.get("letzteZahlungId")), verwertbar.toPlainString()));
            }
            if (mitWiedervorlage) {
                LocalDate erwartetDatum = wiedervorlage;
                erwartungen.add(Erwartung.ganzeZahl("Wiedervorlagedatum exakt wie gesetzt",
                        "SELECT COUNT(*) FROM BEDARFSGEMEINSCHAFT WHERE ID = ? AND WIEDERVORLAGE_AM = ?",
                        ctx -> List.of(ctx.get("bgId"), java.sql.Date.valueOf(erwartetDatum)), 1));
            }

            return new Auftrag(nr, "KOMBINATION", az, name,
                    kombiBeschreibung(haushalt, monat, vermoegen, mitBescheid, bescheidVon,
                            bescheidBis, mitFreigabe, mitDoppelklick, mitStornoNeu, verwertbar),
                    aktionen, erwartungen)
                    .dokumentiere("Ein systematisch gestreuter Kombinationsfall: Haushalt ("
                            + haushalt + "), Zahlungsmonat " + monat + ", Vermögen "
                            + vermoegen.toPlainString() + " EUR; Weg: "
                            + (mitBescheid ? "Bescheid (" + bescheidVon + " .. " + bescheidBis + "), " : "")
                            + "Anordnung" + (mitDoppelklick ? ", Doppelklick-Wiederholung (Idempotenz)" : "")
                            + (mitFreigabe && !mitStornoNeu ? ", Vier-Augen-Freigabe" : "")
                            + (mitStornoNeu ? ", Storno (ANGELEGT) + Mietekorrektur + Neu-Anordnung" : "")
                            + ". Die Erwartung wird mit derselben Berechnung ermittelt, die auch die "
                            + "Fachschicht verwendet — einschließlich der Entbindungsmonat-Regel und der "
                            + "Vermögensverbrauchsfortschreibung. Geprüft wird der vollständige "
                            + "Datenbank-Endzustand: Personen, Betrag, Status, Budget, Bescheid- und "
                            + "Vermögensstand.", rechnungen);
        }
        throw new IllegalStateException(
                "Keine weitere eindeutige Positiv-Kombination gefunden (Parameterraum erschöpft)");
    }

    /** Ein SozialFall-Entwurf mit gestreuten Merkmalen, Einkommen und KdU-Anteilen. */
    private SozialFall kombiFall(int rbs, int monatIdx, BigDecimal miete, BigDecimal betrieb,
            BigDecimal heizung, int kinderU16, int kinderU7) {
        boolean g = random.nextInt(100) < 30;
        boolean schwanger = rbs <= 2 && random.nextInt(100) < 25;
        boolean warmwasser = random.nextInt(100) < 35;
        String entbindungsmonat = null;
        if (schwanger && random.nextInt(100) < 50) {
            // Hälfte der Schwangerschaften endet VOR dem Zahlungsmonat: Mehrbedarf entfällt
            int idx = Math.max(0, monatIdx - 1 - random.nextInt(3));
            entbindungsmonat = MONATE_2026[idx];
        } else if (schwanger) {
            int idx = Math.min(11, monatIdx + 1 + random.nextInt(6));
            entbindungsmonat = MONATE_2026[idx];
        }
        BigDecimal rente = random.nextInt(100) < 60 ? zufallsBetrag(100, 1100, 10) : BigDecimal.ZERO;
        BigDecimal erwerb = BigDecimal.ZERO;
        BigDecimal steuern = BigDecimal.ZERO;
        BigDecimal sv = BigDecimal.ZERO;
        BigDecimal versicherungen = BigDecimal.ZERO;
        BigDecimal erwerbsaufwendungen = BigDecimal.ZERO;
        if (random.nextInt(100) < 40) {
            erwerb = zufallsBetrag(100, 1400, 10);
            steuern = zufallsBetrag(0, 200, 10);
            sv = zufallsBetrag(50, 350, 10);
            versicherungen = zufallsBetrag(0, 80, 5);
            erwerbsaufwendungen = zufallsBetrag(0, 100, 10);
        }
        return new SozialFall(0, 0, rbs, g, schwanger, kinderU7, kinderU16,
                Math.max(kinderU16, kinderU7), warmwasser, miete, betrieb, heizung,
                rente, erwerb, steuern, sv, versicherungen, erwerbsaufwendungen,
                entbindungsmonat, 1);
    }

    private String kombiBeschreibung(String haushalt, String monat, BigDecimal vermoegen,
            boolean mitBescheid, String bescheidVon, String bescheidBis, boolean mitFreigabe,
            boolean mitDoppelklick, boolean mitStornoNeu, BigDecimal verwertbar) {
        StringBuilder s = new StringBuilder(haushalt).append(", Zahlmonat ").append(monat);
        if (vermoegen.signum() > 0) {
            s.append(", Vermögen ").append(vermoegen.toPlainString()).append(" EUR (verwertbar ")
                    .append(verwertbar.toPlainString()).append(")");
        }
        if (mitBescheid) {
            s.append(", Bescheid ").append(bescheidVon).append("..").append(bescheidBis);
        }
        if (mitDoppelklick) {
            s.append(", Doppelklick");
        }
        if (mitFreigabe && !mitStornoNeu) {
            s.append(", Freigabe");
        }
        if (mitStornoNeu) {
            s.append(", Storno+Korrektur");
        }
        return s.toString();
    }

    /**
     * Negativer Kombinationsfall: zufällige, gültige Basis (Vorgang + Fall) und
     * darauf gesetzte Verstöße aus vier Gruppen — Anlage (SEPA), Anordnung
     * (Anspruch/Budget/Monat/Bescheid-Deckung), Workflow (Vier-Augen, Zustandskette)
     * und Daten (Bescheid-Zeitraum, Konkurrenz, unbekannte Objekte). Primärverstoß
     * und — falls verträglich — ein zweiter Verstoß aus einer anderen Gruppe;
     * die Signatur-Menge stellt sicher, dass jeder Auftrag eine andere Kombination
     * prüft. Erwartet werden die jeweiligen Fachfehler plus unveränderter
     * Datenbank-Endzustand.
     */
    private Auftrag kombinationNegativ(int nr) {
        String kassenPraefix = praefix;
        for (int versuch = 0; versuch < 5000; versuch++) {
            int rbs = 1 + random.nextInt(3);
            BigDecimal miete = zufallsBetrag(200, 600, 10);
            BigDecimal betrieb = zufallsBetrag(20, 120, 5);
            BigDecimal heizung = zufallsBetrag(30, 150, 5);
            BigDecimal rente = random.nextInt(100) < 50 ? zufallsBetrag(100, 900, 10) : BigDecimal.ZERO;
            // Unverstoßene Basis für Erwartungswerte (Budget, WORKFLOW-Beträge)
            List<SozialFall> basisFaelle = List.of(fallEntwurf(rbs, false, false, 0, false,
                    miete, betrieb, heizung, rente.toPlainString(), "0"));
            BigDecimal zahlbetrag = Leistungsberechnung.berechne(0, basisFaelle,
                    Leistungsberechnung.KduParameter.AUS, BigDecimal.ZERO).zahlbetrag();
            if (zahlbetrag.signum() <= 0) {
                continue;
            }

            // ---------------- Verstöße ziehen ----------------
            int gruppenWahl = random.nextInt(100);
            String gruppe = gruppenWahl < 10 ? "ANLAGE"
                    : gruppenWahl < 50 ? "ANORDNUNG"
                    : gruppenWahl < 80 ? "WORKFLOW" : "DATEN";
            String primaer = switch (gruppe) {
                case "ANLAGE" -> random.nextBoolean() ? "iban" : "bic";
                case "ANORDNUNG" -> List.of("rente", "erwerb", "vermoegen", "budget",
                        "ohneFall", "ausserhalbBescheid").get(random.nextInt(6));
                case "WORKFLOW" -> List.of("doppelt", "selbst", "doppelfreigabe", "stornoKasse")
                        .get(random.nextInt(4));
                default -> List.of("bescheidUmgekehrt", "bescheidZuLang", "konkurrenz", "nichtGefunden")
                        .get(random.nextInt(4));
            };
            String sekundaer = null;
            boolean mitSekundaer = !gruppe.equals("ANLAGE") && !"ohneFall".equals(primaer)
                    && random.nextInt(100) < 60;
            if (mitSekundaer) {
                sekundaer = switch (gruppe) {
                    case "ANORDNUNG" -> List.of("bescheidUmgekehrt", "bescheidZuLang",
                            "nichtGefunden", "konkurrenz").get(random.nextInt(4));
                    case "WORKFLOW" -> List.of("bescheidUmgekehrt", "bescheidZuLang")
                            .get(random.nextInt(2));
                    default -> List.of("rente", "erwerb", "budget").get(random.nextInt(3));
                };
            }

            // Der ERFASSTE Fall trägt die Verstoß-Einkommen (die unverstoßene Basis
            // bleibt Maßstab für Budget- und Betragserwartungen).
            BigDecimal hoheRente = BigDecimal.valueOf(2500 + 500L * random.nextInt(7)).setScale(2);
            BigDecimal hoherErwerb = BigDecimal.valueOf(4000 + 500L * random.nextInt(9)).setScale(2);
            String renteErfasst = ("rente".equals(primaer) || "rente".equals(sekundaer))
                    ? hoheRente.toPlainString() : rente.toPlainString();
            String erwerbErfasst = ("erwerb".equals(primaer) || "erwerb".equals(sekundaer))
                    ? hoherErwerb.toPlainString() : "0";
            List<SozialFall> faelle = List.of(fallEntwurf(rbs, false, false, 0, false,
                    miete, betrieb, heizung, renteErfasst, erwerbErfasst));

            // Bei ausserhalbBescheid muss der Verstoßmonat NACH dem gedeckten
            // Zeitraum 2026-01..2026-06 liegen, sonst wäre die Anordnung zulässig.
            String monat = "ausserhalbBescheid".equals(primaer)
                    ? MONATE_2026[6 + random.nextInt(6)]
                    : MONATE_2026[1 + random.nextInt(10)];
            BigDecimal budget = "budget".equals(primaer) || "budget".equals(sekundaer)
                    ? zahlbetrag.subtract(zufallsBetrag(10, 90, 10)).max(BigDecimal.ZERO).setScale(2)
                    : BigDecimal.valueOf(2000).setScale(2);
            BigDecimal hohesVermoegen = BigDecimal.valueOf(16000 + 1000L * random.nextInt(15)).setScale(2);
            BigDecimal mieteNeu = miete.add(zufallsBetrag(30, 150, 10));
            // Positive IDs können bei langem Betrieb existieren; Sequences vergeben nur positive IDs.
            long fakeId = List.of(-999L, -1L, -2L).get(random.nextInt(3));
            String kaputteIban = List.of("DE00370400440532013000", "DE8937040044053201300",
                    "DE89370400440532013001").get(random.nextInt(3));
            String kaputterBic = List.of("MARKDEF", "MARKDEF11", "MARKDEF1100X").get(random.nextInt(3));
            int bescheidLaenge = 13 + random.nextInt(12);
            String bescheidVonFalsch = "2026-01";
            String bescheidBisFalsch = YearMonth.parse(bescheidVonFalsch)
                    .plusMonths(bescheidLaenge - 1L).toString();

            List<String> verstoesse = new ArrayList<>();
            verstoesse.add(primaer);
            if (sekundaer != null) verstoesse.add(sekundaer);
            verstoesse.sort(String::compareTo);
            boolean mitAnordnung = "ANORDNUNG".equals(gruppe) || "WORKFLOW".equals(gruppe)
                    || sekundaerHauptfaellig(sekundaer) || verstoesse.contains("nichtGefunden");
            String sig = "ANLAGE".equals(gruppe)
                    ? "ANLAGE|" + budget + "|" + kaputteIban + "|" + kaputterBic
                    : "NEGATIV|" + verstoesse + "|" + budget
                            + "|" + ("ohneFall".equals(primaer) ? "-" : fallSignatur(faelle.get(0)))
                            + "|" + ("vermoegen".equals(primaer) ? hohesVermoegen : BigDecimal.ZERO)
                            + "|" + (mitAnordnung ? monat : "-")
                            + "|" + (verstoesse.contains("konkurrenz") ? mieteNeu : "-")
                            + "|" + (verstoesse.contains("nichtGefunden") ? fakeId : "-")
                            + "|" + (verstoesse.contains("bescheidZuLang")
                                    ? bescheidVonFalsch + "/" + bescheidBisFalsch : "-");
            if (!kombiSignaturen.add(sig)) {
                continue;
            }

            // ---------------- Schritte und Erwartungen bauen ----------------
            String name = hauptperson();
            String az = aktenzeichen(nr);
            List<SzenarioAktion> aktionen = new ArrayList<>();
            List<Erwartung> erwartungen = new ArrayList<>();
            List<Rechenblock> rechnungen = new ArrayList<>();
            boolean bescheidGueltigVorhanden = "ausserhalbBescheid".equals(primaer);

            if ("ANLAGE".equals(gruppe)) {
                aktionen.add(SzenarioAktion.schrittMitErwartetemFehler(
                        "Vorgang anlegen mit ungültiger IBAN (" + kaputteIban + ") — VALIDATION erwartet",
                        FachlicheException.VALIDATION,
                        (s, ich, kollege, ctx) -> {
                            s.legeBedarfsgemeinschaftAn(az, name, budget, null,
                                    kaputteIban, "MARKDEF1100", name, ich);
                            return "unerwartet erfolgreich — ungültige IBAN wäre durchgerutscht";
                        }));
                aktionen.add(SzenarioAktion.schrittMitErwartetemFehler(
                        "Vorgang anlegen mit ungültigem BIC (" + kaputterBic + ") — VALIDATION erwartet",
                        FachlicheException.VALIDATION,
                        (s, ich, kollege, ctx) -> {
                            s.legeBedarfsgemeinschaftAn(az, name, budget, null,
                                    "DE89370400440532013000", kaputterBic, name, ich);
                            return "unerwartet erfolgreich — ungültiger BIC wäre durchgerutscht";
                        }));
                erwartungen.add(Erwartung.ganzeZahl("kein Vorgang mit fehlerhaften SEPA-Daten angelegt",
                        "SELECT COUNT(*) FROM BEDARFSGEMEINSCHAFT WHERE AKTENZEICHEN = ?",
                        ctx -> List.of(az), 0));
                return new Auftrag(nr, "NEG-KOMBINATION", az, name,
                        "SEPA-Anlage abgewehrt (IBAN/BIC-Variante " + kaputteIban.length()
                                + "/" + kaputterBic.length() + " Zeichen)",
                        aktionen, erwartungen)
                        .dokumentiere("Kombinierter Abwehrfall der Gruppe Anlage: Für den neuen "
                                + "Vorgang werden zwei bewusst unbrauchbare Bankverbindungen "
                                + "versucht — eine IBAN mit gestörter Prüfziffer oder Länge und "
                                + "ein BIC mit unzulässiger Länge (erlaubt sind genau 8 oder 11 "
                                + "Zeichen, ISO 9362). Beide Anlagen müssen mit VALIDATION "
                                + "abgewehrt werden; am Ende darf kein Vorgang unter diesem "
                                + "Aktenzeichen existieren, denn die Fehler stecken in den "
                                + "Kerndaten der Anlage selbst.", List.of());
            }

            // ---- Basis: Vorgang (ohne Fall bei ohneFall) + Fall ----
            BigDecimal vermoegenAnlage = "vermoegen".equals(primaer) ? hohesVermoegen : BigDecimal.ZERO;
            BigDecimal budgetFinal = budget;
            aktionen.add(SzenarioAktion.schritt("Vorgang anlegen (gültige Basis, Budget "
                    + budget.toPlainString() + " EUR)", (s, ich, kollege, ctx) -> {
                var bg = vermoegenAnlage.signum() > 0
                        ? s.legeBedarfsgemeinschaftAn(az, name, budgetFinal, null,
                                "DE89370400440532013000", "MARKDEF1100", name, vermoegenAnlage, ich)
                        : s.legeBedarfsgemeinschaftAn(az, name, budgetFinal, null, ich);
                ctx.put("bgId", bg.id());
                return "Vorgang " + bg.id();
            }));
            if (!"ohneFall".equals(primaer)) {
                aktionen.add(SzenarioAktion.schritt("SozialFall erfassen (RBS " + rbs + ")",
                        (s, ich, kollege, ctx) -> {
                            s.legeSozialFallAn((Long) ctx.get("bgId"), faelle.get(0), ich);
                            return "RBS " + rbs + ", KdU " + gesamtKdu(miete, betrieb, heizung) + " EUR";
                        }));
            }

            // ---- DATEN-Verstöße (primär oder sekundär) ----
            if ("bescheidUmgekehrt".equals(primaer) || "bescheidUmgekehrt".equals(sekundaer)) {
                aktionen.add(SzenarioAktion.schrittMitErwartetemFehler(
                        "Bescheid mit 'von' nach 'bis' — VALIDATION erwartet",
                        FachlicheException.VALIDATION,
                        (s, ich, kollege, ctx) -> {
                            s.erteileBescheid((Long) ctx.get("bgId"), "2026-11", "2026-10", ich);
                            return "unerwartet erfolgreich — umgekehrter Zeitraum wäre durchgerutscht";
                        }));
            }
            if ("bescheidZuLang".equals(primaer) || "bescheidZuLang".equals(sekundaer)) {
                aktionen.add(SzenarioAktion.schrittMitErwartetemFehler(
                        "Bescheid über " + bescheidLaenge + " Monate — VALIDATION erwartet (max. 12)",
                        FachlicheException.VALIDATION,
                        (s, ich, kollege, ctx) -> {
                            s.erteileBescheid((Long) ctx.get("bgId"), bescheidVonFalsch,
                                    bescheidBisFalsch, ich);
                            return "unerwartet erfolgreich — überlanger Zeitraum wäre durchgerutscht";
                        }));
            }
            if ("konkurrenz".equals(primaer) || "konkurrenz".equals(sekundaer)) {
                aktionen.add(SzenarioAktion.schritt("Miete auf " + mieteNeu.toPlainString()
                        + " ändern (optimistisches Locking)", (s, ich, kollege, ctx) -> {
                            SozialFall geladen = s.ladeDetail((Long) ctx.get("bgId")).faelle().get(0);
                            SozialFall geaendert = new SozialFall(geladen.id(), geladen.bgId(),
                                    geladen.regelbedarfsstufe(), geladen.merkzeichenG(),
                                    geladen.schwanger(), geladen.anzKinderUnter7(),
                                    geladen.anzKinderUnter16(), geladen.anzKinderMinderjaehrig(),
                                    geladen.warmwasser(), mieteNeu, geladen.kduBetriebskosten(),
                                    geladen.kduHeizung(), geladen.einkommenRente(),
                                    geladen.einkommenErwerb(), geladen.abzSteuern(),
                                    geladen.abzSozialversicherung(), geladen.abzVersicherungen(),
                                    geladen.abzErwerbsaufwendungen(), geladen.entbindungsmonat(),
                                    geladen.version());
                            s.aktualisiereSozialFall(geaendert, geladen.version(), ich);
                            ctx.put("fallId", geladen.id());
                            ctx.put("stale", geaendert);
                            return "gespeichert, Version 2";
                        }));
                aktionen.add(SzenarioAktion.schrittMitErwartetemFehler(
                        "Parallel: veralteter Maskenstand speichern — KONKURRENZ erwartet",
                        FachlicheException.KONKURRENZ,
                        (s, ich, kollege, ctx) -> {
                            SozialFall stale = (SozialFall) ctx.get("stale");
                            s.aktualisiereSozialFall(stale, stale.version(), ich);
                            return "unerwartet erfolgreich — hätte KONKURRENZ werfen müssen";
                        }));
            }
            if ("nichtGefunden".equals(primaer) || "nichtGefunden".equals(sekundaer)) {
                aktionen.add(SzenarioAktion.schrittMitErwartetemFehler(
                        "Freigabe der nicht existierenden Zahlung " + fakeId
                                + " — NICHT_GEFUNDEN erwartet",
                        FachlicheException.NICHT_GEFUNDEN,
                        (s, ich, kollege, ctx) -> {
                            s.freigebenZahlung(fakeId, kollege);
                            return "unerwartet erfolgreich";
                        }));
                aktionen.add(SzenarioAktion.schrittMitErwartetemFehler(
                        "Anordnung auf den nicht existierenden Vorgang " + fakeId
                                + " — NICHT_GEFUNDEN erwartet",
                        FachlicheException.NICHT_GEFUNDEN,
                        (s, ich, kollege, ctx) -> {
                            s.ordneAuszahlungAn(new Auszahlungsauftrag(fakeId,
                                    UUID.randomUUID().toString(), monat, ich));
                            return "unerwartet erfolgreich";
                        }));
            }

            // ---- ANORDNUNG / WORKFLOW-Kette ----
            BigDecimal zahlbetragFinal = zahlbetrag;
            if ("ausserhalbBescheid".equals(primaer)) {
                aktionen.add(SzenarioAktion.schritt("Gültigen Bescheid erteilen (2026-01 .. 2026-06)",
                        (s, ich, kollege, ctx) -> {
                            var b = s.erteileBescheid((Long) ctx.get("bgId"), "2026-01", "2026-06", ich);
                            return "Bescheid " + b.bescheidNr() + " erteilt";
                        }));
                aktionen.add(SzenarioAktion.schrittMitErwartetemFehler(
                        "Anordnung für " + monat + " außerhalb des Bewilligungszeitraums — "
                                + "VALIDATION erwartet",
                        FachlicheException.VALIDATION,
                        (s, ich, kollege, ctx) -> {
                            s.ordneAuszahlungAn(new Auszahlungsauftrag((Long) ctx.get("bgId"),
                                    UUID.randomUUID().toString(), monat, ich));
                            return "unerwartet erfolgreich — Zahlung außerhalb des Bescheids wäre durchgerutscht";
                        }));
            } else if ("ANORDNUNG".equals(gruppe) || (sekundaer != null && sekundaerHauptfaellig(sekundaer))) {
                String fehlerSchritt;
                String fehlerCode;
                if ("rente".equals(primaer) || "rente".equals(sekundaer)) {
                    fehlerSchritt = "Anordnung mit hoher Rente (" + hoheRente.toPlainString()
                            + " EUR) — KEIN_ANSPRUCH erwartet (§ 43 Abs. 1)";
                    fehlerCode = FachlicheException.KEIN_ANSPRUCH;
                } else if ("erwerb".equals(primaer) || "erwerb".equals(sekundaer)) {
                    fehlerSchritt = "Anordnung mit hohem Erwerbseinkommen (" + hoherErwerb.toPlainString()
                            + " EUR) — KEIN_ANSPRUCH erwartet (§ 43 Abs. 1)";
                    fehlerCode = FachlicheException.KEIN_ANSPRUCH;
                } else if ("vermoegen".equals(primaer)) {
                    fehlerSchritt = "Anordnung mit Vermögen " + hohesVermoegen.toPlainString()
                            + " EUR — KEIN_ANSPRUCH erwartet (§§ 43, 90)";
                    fehlerCode = FachlicheException.KEIN_ANSPRUCH;
                } else if ("budget".equals(primaer) || "budget".equals(sekundaer)) {
                    fehlerSchritt = "Anordnung über das zu kleine Startbudget ("
                            + budget.toPlainString() + " EUR) — BUDGET_ERSCHOEPFT erwartet";
                    fehlerCode = FachlicheException.BUDGET_ERSCHOEPFT;
                } else if ("ohneFall".equals(primaer)) {
                    fehlerSchritt = "Anordnung ohne jeden SozialFall — VALIDATION erwartet";
                    fehlerCode = FachlicheException.VALIDATION;
                } else {
                    fehlerSchritt = null;
                    fehlerCode = null;
                }
                if (fehlerSchritt != null) {
                    aktionen.add(SzenarioAktion.schrittMitErwartetemFehler(fehlerSchritt, fehlerCode,
                            (s, ich, kollege, ctx) -> {
                                s.ordneAuszahlungAn(new Auszahlungsauftrag((Long) ctx.get("bgId"),
                                        UUID.randomUUID().toString(), monat, ich));
                                return "unerwartet erfolgreich — hätte abgewehrt werden müssen";
                            }));
                }
            }
            if ("WORKFLOW".equals(gruppe)) {
                aktionen.add(SzenarioAktion.schritt("Auszahlung anordnen (" + monat + ")",
                        (s, ich, kollege, ctx) -> {
                            var erg = s.ordneAuszahlungAn(new Auszahlungsauftrag((Long) ctx.get("bgId"),
                                    UUID.randomUUID().toString(), monat, ich));
                            ctx.put("zahlungId", erg.zahlung().id());
                            return erg.zahlung().auszahlungsbetrag().toPlainString() + " EUR (ANGELEGT)";
                        }));
                switch (primaer) {
                    case "doppelt" -> aktionen.add(SzenarioAktion.schrittMitErwartetemFehler(
                            "Zweite Anordnung für " + monat + " (neue Vorgangsnummer) — VALIDATION erwartet",
                            FachlicheException.VALIDATION,
                            (s, ich, kollege, ctx) -> {
                                s.ordneAuszahlungAn(new Auszahlungsauftrag((Long) ctx.get("bgId"),
                                        UUID.randomUUID().toString(), monat, ich));
                                return "unerwartet erfolgreich — Doppelzahlung wäre durchgerutscht";
                            }));
                    case "selbst" -> aktionen.add(SzenarioAktion.schrittMitErwartetemFehler(
                            "Selbstfreigabe durch den Erfasser — BERECHTIGUNG erwartet",
                            FachlicheException.BERECHTIGUNG,
                            (s, ich, kollege, ctx) -> {
                                s.freigebenZahlung((Long) ctx.get("zahlungId"), ich);
                                return "unerwartet erfolgreich — Selbstfreigabe wäre durchgerutscht";
                            }));
                    case "doppelfreigabe" -> {
                        aktionen.add(SzenarioAktion.schritt("Freigabe durch Kollegen (Vier-Augen)",
                                (s, ich, kollege, ctx) -> {
                                    s.freigebenZahlung((Long) ctx.get("zahlungId"), kollege);
                                    return "freigegeben durch " + kollege;
                                }));
                        aktionen.add(SzenarioAktion.schrittMitErwartetemFehler(
                                "Zweite Freigabe — VALIDATION erwartet",
                                FachlicheException.VALIDATION,
                                (s, ich, kollege, ctx) -> {
                                    s.freigebenZahlung((Long) ctx.get("zahlungId"), kollege);
                                    return "unerwartet erfolgreich — Doppel-Freigabe wäre durchgerutscht";
                                }));
                    }
                    default -> { // stornoKasse
                        aktionen.add(SzenarioAktion.schritt("Freigabe + Kassenlauf (UEBERGEBEN)",
                                (s, ich, kollege, ctx) -> {
                                    s.freigebenZahlung((Long) ctx.get("zahlungId"), kollege);
                                    var kasse = s.uebergebeBereiteZahlungen(ich, kassenPraefix);
                                    return "Kassenlauf: " + kasse.anzahl() + " Zahlung(en) über "
                                            + kasse.summe().toPlainString() + " EUR";
                                }));
                        aktionen.add(SzenarioAktion.schrittMitErwartetemFehler(
                                "Storno nach Kassenübergabe — VALIDATION erwartet",
                                FachlicheException.VALIDATION,
                                (s, ich, kollege, ctx) -> {
                                    s.storniereZahlung((Long) ctx.get("zahlungId"), ich);
                                    return "unerwartet erfolgreich — Storno nach Übergabe wäre durchgerutscht";
                                }));
                    }
                }
            }

            // ---- Erwartungen (konsolidiert über alle Verstöße) ----
            if ("WORKFLOW".equals(gruppe)) {
                erwartungen.add(Erwartung.ganzeZahl("genau die eine (erfolgreiche) Zahlung",
                        "SELECT COUNT(*) FROM ZAHLUNG WHERE BG_ID = ?",
                        ctx -> List.of(ctx.get("bgId")), 1));
                erwartungen.add(Erwartung.betrag("Auszahlungsbetrag unverändert korrekt",
                        "SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?",
                        ctx -> List.of(ctx.get("zahlungId")), zahlbetragFinal.toPlainString()));
                erwartungen.add(Erwartung.einsVon("Status je nach Verstoßstelle im Workflow",
                        "SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?",
                        ctx -> List.of(ctx.get("zahlungId")),
                        switch (primaer) {
                            case "selbst", "doppelt" -> "ANGELEGT";
                            case "stornoKasse" -> "UEBERGEBEN";
                            default -> "BEREIT|UEBERGEBEN"; // doppelfreigabe
                        }));
                erwartungen.add(Erwartung.betrag("Budget genau einmal gemindert",
                        "SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?",
                        ctx -> List.of(ctx.get("bgId")), budget.subtract(zahlbetragFinal).toPlainString()));
                if ("selbst".equals(primaer) || "doppelt".equals(primaer)) {
                    erwartungen.add(erwartungNichtFreigegeben("zahlungId"));
                } else {
                    erwartungen.add(Erwartung.text("Freigabevermerk unverändert beim Kollegen",
                            "SELECT CASE WHEN FREIGEGEBEN_VON = ? THEN 'ok' ELSE 'abweichend' END "
                                    + "FROM ZAHLUNG WHERE ID = ?",
                            ctx -> List.of(ctx.get("kollege"), ctx.get("zahlungId")), "ok"));
                }
            } else {
                erwartungen.add(Erwartung.ganzeZahl("keine Zahlung entstanden",
                        "SELECT COUNT(*) FROM ZAHLUNG WHERE BG_ID = ?",
                        ctx -> List.of(ctx.get("bgId")), 0));
                erwartungen.add(Erwartung.betrag("Budget unverändert",
                        "SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?",
                        ctx -> List.of(ctx.get("bgId")), budget.toPlainString()));
            }
            int bescheideAmEnde = bescheidGueltigVorhanden ? 1 : 0;
            erwartungen.add(Erwartung.ganzeZahl("Bescheid-Bestand wie erwartet",
                    "SELECT COUNT(*) FROM BESCHEID WHERE BG_ID = ?",
                    ctx -> List.of(ctx.get("bgId")), bescheideAmEnde));
            erwartungen.add(Erwartung.betrag("Vermögen durch abgewehrte Schritte unverändert",
                    "SELECT VERMOEGEN FROM BEDARFSGEMEINSCHAFT WHERE ID = ?",
                    ctx -> List.of(ctx.get("bgId")), vermoegenAnlage.toPlainString()));
            long journalAnzahl = 1 + ("ohneFall".equals(primaer) ? 0 : 1)
                    + (verstoesse.contains("konkurrenz") ? 1 : 0) + bescheideAmEnde
                    + ("WORKFLOW".equals(gruppe) ? 1 : 0)
                    + ("doppelfreigabe".equals(primaer) || "stornoKasse".equals(primaer) ? 1 : 0);
            erwartungen.add(Erwartung.ganzeZahl("Keine zusätzlichen Fachjournale durch die Abwehr",
                    "SELECT COUNT(*) FROM JOURNAL WHERE BG_ID = ?",
                    ctx -> List.of(ctx.get("bgId")), journalAnzahl));
            if ("konkurrenz".equals(primaer) || "konkurrenz".equals(sekundaer)) {
                erwartungen.add(Erwartung.betrag("korrigierte Miete steht (kein Lost Update)",
                        "SELECT KDU_MIETE FROM SOZIAL_FALL WHERE ID = ?",
                        ctx -> List.of(ctx.get("fallId")), mieteNeu.toPlainString()));
                erwartungen.add(Erwartung.ganzeZahl("Fall in Version 2",
                        "SELECT VERSION FROM SOZIAL_FALL WHERE ID = ?",
                        ctx -> List.of(ctx.get("fallId")), 2));
            }
            if ("nichtGefunden".equals(primaer) || "nichtGefunden".equals(sekundaer)) {
                erwartungen.add(Erwartung.ganzeZahl("echter Vorgang unangetastet vorhanden",
                        "SELECT COUNT(*) FROM BEDARFSGEMEINSCHAFT WHERE AKTENZEICHEN = ?",
                        ctx -> List.of(az), 1));
            }

            if ("rente".equals(primaer) || "erwerb".equals(primaer) || "budget".equals(primaer)
                    || "vermoegen".equals(primaer)) {
                berechne(rechnungen, "Nachweis: erwartete Abwehr für " + primaer,
                        faelle, "vermoegen".equals(primaer) ? hohesVermoegen : BigDecimal.ZERO,
                        budget,
                        "Die Abwehr ist fachlich begründet: " + switch (primaer) {
                            case "rente" -> "Die Rente übersteigt den Gesamtbedarf (§ 43 Abs. 1).";
                            case "erwerb" -> "Das Erwerbseinkommen übersteigt nach Freibetrag den Bedarf.";
                            case "budget" -> "Das Startbudget liegt unter dem Zahlbetrag.";
                            default -> "Das verwertbare Vermögen übersteigt den Bedarf (§§ 43, 90).";
                        });
            }

            BigDecimal endbudget = "WORKFLOW".equals(gruppe) ? budget.subtract(zahlbetragFinal) : budget;
            rechnungen.replaceAll(b -> b.mitBudgetRestExpected(endbudget));
            return new Auftrag(nr, "NEG-KOMBINATION", az, name,
                    "Verstoß-Kombination: " + primaer + (sekundaer != null ? " + " + sekundaer : ""),
                    aktionen, erwartungen)
                    .dokumentiere("Kombinierter Abwehrfall: Auf einer gültigen Basis (Vorgang"
                            + ("ohneFall".equals(primaer) ? " — bewusst OHNE SozialFall" : " und SozialFall")
                            + ") wird der Verstoß '" + primaer + "'"
                            + (sekundaer != null ? " zusammen mit '" + sekundaer + "'" : "")
                            + " provoziert. Erwartet werden die jeweiligen Fachfehler mit klaren "
                            + "Meldungen — und ein Datenbank-Endzustand ohne unerwünschte Nebenwirkungen: "
                            + "keine ungewollte Zahlung, Budget nur durch erlaubte Schritte verändert, "
                            + "Bescheid-Bestand exakt wie fachlich zulässig. Jede Kombination verstößt "
                            + "gegen eine andere Regel — gemeinsam decken die Fälle die Abwehrseiten "
                            + "des Systems flächig ab.", rechnungen);
        }
        throw new IllegalStateException(
                "Keine weitere eindeutige Negativ-Kombination gefunden (Parameterraum erschöpft)");
    }

    /** Sekundär-Verstoß, der selbst eine (fehlgeschlagene) Anordnung auslöst. */
    private boolean sekundaerHauptfaellig(String sekundaer) {
        return "rente".equals(sekundaer) || "erwerb".equals(sekundaer) || "budget".equals(sekundaer);
    }

    /** Nur tatsächlich fachliche Falldaten, keine IDs oder verworfenen Zufallswerte. */
    private static String fallSignatur(SozialFall f) {
        return List.of(f.regelbedarfsstufe(), f.merkzeichenG(), f.schwanger(),
                String.valueOf(f.entbindungsmonat()), f.warmwasser(), f.anzKinderUnter7(),
                f.anzKinderUnter16(), f.anzKinderMinderjaehrig(), f.kduMiete(),
                f.kduBetriebskosten(), f.kduHeizung(), f.einkommenRente(), f.einkommenErwerb(),
                f.abzSteuern(), f.abzSozialversicherung(), f.abzVersicherungen(),
                f.abzErwerbsaufwendungen()).toString();
    }



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
        rechnungen.replaceAll(b -> b.mitBudget(budget).mitBudgetRestExpected(budget));
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
        rechnungen.replaceAll(b -> b.mitBudget(budget).mitBudgetRestExpected(budget));
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
                        ctx -> List.of(ctx.get("bgId")), budget.subtract(zahlbetrag).toPlainString()),
                erwartungNichtFreigegeben("zahlungId"));
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
        String kassenPraefix = praefix;
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
                    var kasse = s.uebergebeBereiteZahlungen(ich, kassenPraefix);
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

    /**
     * Erwartungsbausteine der Anordnungs-Szenarien. {@code mitFreigabe} = das
     * Szenario gibt selbst frei (Status BEREIT, ggf. schon vom Kassenlauf übergeben);
     * sonst bleibt die Zahlung als ANGELEGT ohne Freigabevermerk in der Warteschleife.
     * Der Kassenlauf selbst erteilt keine Freigabe.
     */
    private List<Erwartung> erwartungenStandard(BigDecimal zahlbetrag, BigDecimal budget,
            int rbs, boolean mitFreigabe) {
        List<Erwartung> e = new ArrayList<>();
        e.add(Erwartung.einsVon(mitFreigabe
                        ? "Zahlung freigegeben (oder bereits übergeben)"
                        : "Zahlung wartet auf Vier-Augen-Freigabe",
                "SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?",
                ctx -> List.of(ctx.get("zahlungId")),
                mitFreigabe ? "BEREIT|UEBERGEBEN" : "ANGELEGT"));
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
        if (mitFreigabe) {
            // Vier-Augen-Beweis: Der Freigabevermerk muss genau den Kollegen aus
            // dem Kontext nennen (Vergleich komplett in SQL — Parameter wie beim
            // Negativ-Pendant NEG-SELBSTFREIGABE, das auf FREIGEGEBEN_VON IS NULL prüft).
            e.add(Erwartung.text("Freigabevermerk nennt den Kollegen (Vier-Augen)",
                    "SELECT CASE WHEN FREIGEGEBEN_VON = ? THEN 'ok' ELSE 'abweichend' END "
                            + "FROM ZAHLUNG WHERE ID = ?",
                    ctx -> List.of(ctx.get("kollege"), ctx.get("zahlungId")), "ok"));
        } else {
            e.add(erwartungNichtFreigegeben("zahlungId"));
        }
        return e;
    }

    private static Erwartung erwartungNichtFreigegeben(String idKey) {
        return Erwartung.ganzeZahl("Keine Freigabe ohne Freigabeschritt",
                "SELECT COUNT(*) FROM ZAHLUNG WHERE ID = ? AND ZAHLUNGSSTATUS = 'ANGELEGT' "
                        + "AND FREIGEGEBEN_VON IS NULL",
                ctx -> List.of(ctx.get(idKey)), 1);
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
