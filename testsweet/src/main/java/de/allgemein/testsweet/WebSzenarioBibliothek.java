package de.allgemein.testsweet;

import de.allgemein.lmg.model.SozialFall;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Auftragstopf für den Weboberflächen-Modus (Modus 2): Jeder Auftrag wird von
 * einem simulierten Sachbearbeiter in einem sichtbaren Chrome-Fenster über die
 * echte SDN.Leistung-Oberfläche durchgespielt (Anmeldung, Neuanlage, Fall,
 * Auszahlung anordnen, Vier-Augen-Freigabe, Storno). Die Erwartungshaltung
 * wird — wie in Modus 1 — nach Laufende per SQL geprüft; da die Weboberfläche
 * die Vorgangs-ID nicht sichtbar macht, greifen die Prüf-SQLs über das
 * eindeutige Aktenzeichen.
 */
public final class WebSzenarioBibliothek {

    private final Random random;

    public WebSzenarioBibliothek(long seed) {
        this.random = new Random(seed);
    }

    public List<Auftrag> erzeuge(int anzahl, String praefix) {
        List<Auftrag> auftraege = new ArrayList<>(anzahl);
        for (int i = 1; i <= anzahl; i++) {
            int wahl = random.nextInt(10);
            if (wahl < 4) {
                auftraege.add(standardrente(i, praefix));
            } else if (wahl < 7) {
                auftraege.add(mehrbedarf(i, praefix));
            } else {
                auftraege.add(storno(i, praefix));
            }
        }
        return auftraege;
    }

    // ------------------------------------------------------------------

    private Auftrag standardrente(int nr, String praefix) {
        return basis(nr, praefix, "WEB-STANDARDRENTE", false, false, "mit Vier-Augen-Freigabe",
                "WEB-STANDARDRENTE", true);
    }

    private Auftrag mehrbedarf(int nr, String praefix) {
        return basis(nr, praefix, "WEB-MEHRBEDARF", random.nextBoolean(), random.nextBoolean(),
                "mit Merkzeichen G und/oder Warmwasser-Zuschlag", "WEB-MEHRBEDARF", true);
    }

    private Auftrag storno(int nr, String praefix) {
        return basis(nr, praefix, "WEB-STORNO", false, false,
                "Anordnung, Freigabe und anschließender Storno", "WEB-STORNO", false);
    }

    private Auftrag basis(int nr, String praefix, String typ, boolean merkzeichenG,
            boolean warmwasser, String variante, String einfachTyp, boolean mitFreigabeEnde) {
        int rbs = 1 + random.nextInt(3);
        BigDecimal miete = BigDecimal.valueOf(250 + 10 * random.nextInt(31)).setScale(2);
        BigDecimal betrieb = BigDecimal.valueOf(30 + 5 * random.nextInt(19)).setScale(2);
        BigDecimal heizung = BigDecimal.valueOf(50 + 5 * random.nextInt(19)).setScale(2);
        List<SozialFall> faelle = List.of(WebHelfer.fallEntwurf(rbs, merkzeichenG, warmwasser,
                miete, betrieb, heizung));
        List<Rechenblock> rechnungen = new ArrayList<>();
        BigDecimal zahlbetrag = WebHelfer.berechne(rechnungen, "Anordnung über die Weboberfläche",
                faelle, BigDecimal.ZERO);
        BigDecimal budget = zahlbetrag.add(BigDecimal.valueOf(100 + 10 * random.nextInt(21)));
        rechnungen.replaceAll(b -> b.mitBudget(budget));
        String aktenzeichen = String.format("%s%04d", praefix, nr);
        String monat = "2026-10";

        List<WebAktion> schritte = new ArrayList<>(List.of(
                WebAktion.wiederholbar("Anmeldung an der Weboberfläche", WebHelfer::anmeldung),
                WebAktion.schritt("Neuen Vorgang anlegen (Aktenzeichen " + aktenzeichen + ")",
                        (d, ctx) -> WebHelfer.vorgangAnlegen(d, ctx, aktenzeichen,
                                "Web, Willi " + nr, budget)),
                WebAktion.schritt("SozialFall erfassen (RBS " + rbs + ", KdU "
                        + miete.add(betrieb).add(heizung).toPlainString() + " EUR)",
                        (d, ctx) -> WebHelfer.fallAnlegen(d, ctx, rbs, merkzeichenG, warmwasser,
                                miete, betrieb, heizung)),
                WebAktion.schritt("Auszahlung für " + monat + " anordnen",
                        (d, ctx) -> WebHelfer.auszahlungAnordnen(d, ctx, monat)),
                WebAktion.wiederholbar("Abmelden und als Kollegin/Kollege anmelden (Vier-Augen)",
                        WebHelfer::kollegenLogin),
                WebAktion.wiederholbar("Vorgang über die Suche finden und öffnen",
                        (d, ctx) -> WebHelfer.vorgangSuchen(d, ctx, aktenzeichen)),
                WebAktion.schritt("Zahlung freigeben (rechnerisch feststellen)",
                        WebHelfer::freigeben)));
        if (!mitFreigabeEnde) {
            schritte.add(WebAktion.schritt("Als Administration wechseln und Storno buchen",
                    (d, ctx) -> WebHelfer.stornoWechsel(d, ctx, aktenzeichen)));
        }

        List<Erwartung> erwartungen = new ArrayList<>();
        erwartungen.add(Erwartung.text(mitFreigabeEnde
                        ? "Zahlung über die Oberfläche freigegeben (BEREIT)"
                        : "Zahlung nach Storno über die Oberfläche (STORNIERT)",
                "SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ZAHLUNGSMONAT = ? AND BG_ID = "
                        + "(SELECT ID FROM BEDARFSGEMEINSCHAFT WHERE AKTENZEICHEN = ?)",
                ctx -> List.of(monat, aktenzeichen),
                mitFreigabeEnde ? "BEREIT" : "STORNIERT"));
        erwartungen.add(Erwartung.betrag("Auszahlungsbetrag entspricht der Berechnung",
                "SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ZAHLUNGSMONAT = ? AND BG_ID = "
                        + "(SELECT ID FROM BEDARFSGEMEINSCHAFT WHERE AKTENZEICHEN = ?)",
                ctx -> List.of(monat, aktenzeichen), zahlbetrag.toPlainString()));
        erwartungen.add(Erwartung.betrag(mitFreigabeEnde
                        ? "Restbudget korrekt gemindert"
                        : "Restbudget nach Storno vollständig zurückgebucht",
                "SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE AKTENZEICHEN = ?",
                ctx -> List.of(aktenzeichen),
                (mitFreigabeEnde ? budget.subtract(zahlbetrag) : budget).toPlainString()));
        erwartungen.add(Erwartung.ganzeZahl("SozialFall mit Stufe persistiert",
                "SELECT COUNT(*) FROM SOZIAL_FALL WHERE REGELBEDARFSSTUFE = ? AND BG_ID = "
                        + "(SELECT ID FROM BEDARFSGEMEINSCHAFT WHERE AKTENZEICHEN = ?)",
                ctx -> List.of(rbs, aktenzeichen), 1));

        return new Auftrag(nr, typ, aktenzeichen, "Web, Willi " + nr,
                "Weboberflächen-Durchspiel " + variante, List.of(), erwartungen)
                .mitWebAktionen(schritte)
                .dokumentiere("Der simulierte Sachbearbeiter bedient die echte SDN.Leistung-"
                        + "Weboberfläche im eigenen sichtbaren Browserfenster: Er meldet sich an, "
                        + "legt den Vorgang und den SozialFall an, ordnet die monatliche Auszahlung "
                        + "an und übergibt per Ab-/Anmeldung an eine zweite Kraft, die die Zahlung "
                        + "freigibt (Vier-Augen-Prinzip). "
                        + (mitFreigabeEnde
                        ? "Geprüft wird, ob Status, Betrag und Budget in der Datenbank stimmen."
                        : "Anschließend übernimmt eine Administration — die Oberfläche bietet "
                        + "den Storno einer freigegebenen (BEREIT-)Zahlung nur der Admin-Rolle "
                        + "an — und storniert die Zahlung. Geprüft wird Status STORNIERT "
                        + "inklusive vollständiger Budget-Rückbuchung.")
                        + " Jeder Schritt wird als Screenshot im Laufprotokoll festgehalten.",
                        rechnungen);
    }

    // ------------------------------------------------------------------
    // NEGATIV (Modus 2): die ABWEHR über die Weboberfläche ist das Ziel —
    // Fehlerdialog mit erwartetem Text plus unveränderter Datenbank-Endzustand.
    // ------------------------------------------------------------------

    /** Erzeugt {@code anzahl} NEGATIV-Web-Aufträge (siehe Klassenkopf der Helfer). */
    public List<Auftrag> erzeugeNegativ(int anzahl, String praefix) {
        List<Auftrag> auftraege = new ArrayList<>(anzahl);
        for (int i = 1; i <= anzahl; i++) {
            int wahl = random.nextInt(70);
            if (wahl < 10) {
                auftraege.add(negLogin(i, praefix));
            } else if (wahl < 20) {
                auftraege.add(negBankverbindung(i, praefix));
            } else if (wahl < 30) {
                auftraege.add(negOhneMonat(i, praefix));
            } else if (wahl < 40) {
                auftraege.add(negKeinAnspruch(i, praefix));
            } else if (wahl < 50) {
                auftraege.add(negBudget(i, praefix));
            } else if (wahl < 60) {
                auftraege.add(negDoppelterMonat(i, praefix));
            } else {
                auftraege.add(negStornoSachbearbeiter(i, praefix));
            }
        }
        return auftraege;
    }

    /** Erzeugt {@code anzahl} LAST-Web-Aufträge: der schlanke UI-Durchlauf. */
    public List<Auftrag> erzeugeLast(int anzahl, String praefix) {
        List<Auftrag> auftraege = new ArrayList<>(anzahl);
        for (int i = 1; i <= anzahl; i++) {
            auftraege.add(lastDurchlauf(i, praefix));
        }
        return auftraege;
    }

    private Auftrag negLogin(int nr, String praefix) {
        String az = String.format("%s%04d", praefix, nr);
        return new Auftrag(nr, "WEB-NEG-LOGIN", az, "—",
                "Anmeldung mit falschem Passwort wird abgewehrt",
                List.of(), List.of(Erwartung.ganzeZahl(
                        "kein Vorgang durch die abgewehrte Anmeldung entstanden",
                        "SELECT COUNT(*) FROM BEDARFSGEMEINSCHAFT WHERE AKTENZEICHEN = ?",
                        ctx -> List.of(az), 0)))
                .mitWebAktionen(List.of(
                        WebAktion.schritt("Anmeldung mit FALSCHEM Passwort versuchen",
                                WebHelfer::anmeldungFehlschlag)))
                .dokumentiere("Hintergrund: Die Anmeldung ist die Tür zum System — alles, was an "
        + "Sicherheit gilt, steht und fällt damit, dass sie nur die richtige Kombination "
        + "aus Kennung und Passwort durchlässt. Ein beliebiges Eintreten müsste jeden "
        + "Datenschutz- und Missbrauchsschutz aushebeln: Sozialdaten sind besonders "
        + "schützenswert, und die Rollen der Sachbearbeiter unterscheiden, wer nur "
        + "erfassen und wer zusätzlich freigeben oder stornieren darf. In diesem "
        + "Testfall meldet sich der simulierte Mitarbeiter mit seiner korrekten "
        + "Kennung, aber einem bewusst FALSCHEN Passwort an der echten Weboberfläche "
        + "an — die häufigste Situation überhaupt: ein Tippfehler, die Caps-Lock-Taste "
        + "oder ein verwechseltes Passwort von einem anderen Dienst. Erwartet wird, "
        + "dass die Oberfläche die Anmeldung klar abwehrt: Es erscheint eine "
        + "Fehlermeldung an der Anmeldezeile, und die Fallübersicht mit den Sozialdaten "
        + "erscheint NICHT — kein Blick, auch kein kurzer, auf geschützte Daten. "
        + "Ergänzend wird geprüft, dass durch den Fehlversuch in der Datenbank nichts "
        + "entstanden ist (abgewehrte Anmeldungen dürfen keinerlei Spuren im "
        + "Fachbestand hinterlassen). Ein Fehler wäre eine erfolgreiche Anmeldung mit "
        + "falschem Passwort — sie würde die Tür für Unbefugte öffnen.",
                        List.of());
    }

    private Auftrag negBankverbindung(int nr, String praefix) {
        String az = String.format("%s%04d", praefix, nr);
        return new Auftrag(nr, "WEB-NEG-BANKVERBINDUNG", az, "—",
                "Vorgang mit ungültiger SEPA-Bankverbindung wird abgewehrt (IBAN und BIC)",
                List.of(), List.of(Erwartung.ganzeZahl(
                        "kein Vorgang mit dem Aktenzeichen angelegt",
                        "SELECT COUNT(*) FROM BEDARFSGEMEINSCHAFT WHERE AKTENZEICHEN = ?",
                        ctx -> List.of(az), 0)))
                .mitWebAktionen(List.of(
                        WebAktion.wiederholbar("Anmeldung an der Weboberfläche", WebHelfer::anmeldung),
                        WebAktion.schritt("Vorgang mit IBAN mit falscher Prüfziffer anlegen",
                                (d, ctx) -> WebHelfer.vorgangAnlegenFehlschlag(d, ctx, az,
                                        "Web, Negativ " + nr, BigDecimal.valueOf(1200),
                                        "DE00370400440532013000", "MARKDEF1100", "IBAN")),
                        WebAktion.schritt("Vorgang mit zu kurzem BIC (7 Zeichen) anlegen",
                                (d, ctx) -> WebHelfer.vorgangAnlegenFehlschlag(d, ctx, az,
                                        "Web, Negativ " + nr, BigDecimal.valueOf(1200),
                                        "DE89370400440532013000", "MARKDEF", "BIC"))))
                .dokumentiere("Hintergrund: Bankdaten tragen eingebaute Selbstprüfungen gegen "
        + "Tippfehler: Die IBAN (internationale Kontonummer) endet auf eine Prüfziffer, "
        + "die rechnerisch aus den übrigen Zeichen folgt — vertippt man sich, geht die "
        + "Rechnung nicht auf (Norm ISO 13616, Modulo-97-Verfahren). Der BIC "
        + "(Bankcode) hat dagegen eine feste Länge von genau 8 oder 11 Zeichen (Norm "
        + "ISO 9362). Diese Prüfungen sind billig und zuverlässig — sie erkennen echte "
        + "Tippfehler, bevor Daten gespeichert und Geld verschickt wird. In diesem "
        + "Testfall bedient der simulierte Sachbearbeiter die echte Weboberfläche und "
        + "versucht zweimal, einen neuen Vorgang mit fehlerhaften Bankdaten anzulegen: "
        + "zuerst mit einer IBAN, deren Prüfziffer nicht stimmt, dann mit einer "
        + "korrekten IBAN, aber einem auf 7 Zeichen gekürzten BIC. Erwartet wird, dass "
        + "die Oberfläche BEIDE Versuche mit einem klar lesbaren Fehlerdialog abwehrt "
        + "(„Ungültige IBAN“ beziehungsweise „Ungültiger BIC“) — wie ein aufmerksamer "
        + "Formularprüfer, der unbrauchbare Felder zurückweist, statt sie wortlos zu "
        + "akzeptieren. Wichtig ist der Doppelbeweis: Nicht nur der Dialog muss "
        + "erscheinen, sondern es darf danach auch wirklich KEIN Vorgang in der "
        + "Datenbank stehen — ein nur optisches Abweisen, das im Hintergrund trotzdem "
        + "speichert, wäre ein schwerer Fehler. Genau das prüft der SQL-Nachweis am "
        + "Laufende über das Aktenzeichen.",
                        List.of());
    }

    private Auftrag negOhneMonat(int nr, String praefix) {
        String az = String.format("%s%04d", praefix, nr);
        int rbs = 1 + random.nextInt(3);
        return new Auftrag(nr, "WEB-NEG-OHNE-MONAT", az, "Web, Negativ " + nr,
                "Anordnung ohne Zahlungsmonat wird abgewehrt",
                List.of(), List.of(
                        Erwartung.ganzeZahl("Vorgang angelegt",
                                "SELECT COUNT(*) FROM BEDARFSGEMEINSCHAFT WHERE AKTENZEICHEN = ?",
                                ctx -> List.of(az), 1),
                        Erwartung.ganzeZahl("keine Zahlung entstanden",
                                "SELECT COUNT(*) FROM ZAHLUNG WHERE BG_ID = (SELECT ID FROM "
                                        + "BEDARFSGEMEINSCHAFT WHERE AKTENZEICHEN = ?)",
                                ctx -> List.of(az), 0)))
                .mitWebAktionen(List.of(
                        WebAktion.wiederholbar("Anmeldung an der Weboberfläche", WebHelfer::anmeldung),
                        WebAktion.schritt("Neuen Vorgang anlegen (Aktenzeichen " + az + ")",
                                (d, ctx) -> WebHelfer.vorgangAnlegen(d, ctx, az,
                                        "Web, Negativ " + nr, BigDecimal.valueOf(1200))),
                        WebAktion.schritt("SozialFall erfassen (RBS " + rbs + ")",
                                (d, ctx) -> WebHelfer.fallAnlegen(d, ctx, rbs, false, false,
                                        BigDecimal.valueOf(400), BigDecimal.valueOf(60),
                                        BigDecimal.valueOf(80))),
                        WebAktion.schritt("Auszahlung OHNE Zahlungsmonat anordnen",
                                WebHelfer::auszahlungOhneMonat)))
                .dokumentiere("Hintergrund: Eine Auszahlungsanordnung braucht zwingend eine "
        + "Aussage darüber, FÜR WELCHEN MONAT sie gilt — der Monat steuert, welche "
        + "Leistung gerade abgerechnet wird, und die Doppelzahlungssperre pro Vorgang "
        + "und Monat baut darauf auf. Fehlt er, weiß hinterher niemand, ob der Betrag "
        + "für Oktober oder November gedacht war; eine still „irgendwo“ abgebuchte "
        + "Zahlung wäre buchhalterisch wertlos. In diesem Testfall legt der simulierte "
        + "Sachbearbeiter Vorgang und SozialFall vollständig korrekt an — alles, was "
        + "folgt, ist also kein Folgefehler schlechter Daten — und klickt dann auf "
        + "„Auszahlung anordnen“, OHNE vorher den Zahlungsmonat zu wählen. Die "
        + "Oberfläche füllt das Monatsfeld zwar hilfsweise mit dem aktuellen Monat "
        + "vor; der Test löscht dieses Vorbelegen bewusst, um die Prüfung selbst auf "
        + "die Probe zu stellen. Erwartet wird der Fehlerdialog „Bitte den "
        + "Zahlungsmonat wählen“ — freundlich, aber bestimmt, wie ein Hinweis am "
        + "Schalter, dass das Formular noch unvollständig ist. Der Doppelbeweis: Es "
        + "darf KEINE Zahlung entstanden sein (keine leere, keine mit Default-Monat), "
        + "während Vorgang und Fall unversehrt bestehen bleiben — der Fehlversuch "
        + "darf die korrekt erfassten Daten nicht beschädigen.",
                        List.of());
    }

    private Auftrag negKeinAnspruch(int nr, String praefix) {
        String az = String.format("%s%04d", praefix, nr);
        int rbs = 1 + random.nextInt(3);
        BigDecimal rente = BigDecimal.valueOf(2500 + 500L * random.nextInt(7)).setScale(2);
        return new Auftrag(nr, "WEB-NEG-KEINANSPRUCH", az, "Web, Negativ " + nr,
                "Anordnung trotz Einkommen über Bedarf wird abgewehrt (§ 43 Abs. 1)",
                List.of(), List.of(
                        Erwartung.ganzeZahl("keine Zahlung entstanden (KEIN_ANSPRUCH abgewehrt)",
                                "SELECT COUNT(*) FROM ZAHLUNG WHERE BG_ID = (SELECT ID FROM "
                                        + "BEDARFSGEMEINSCHAFT WHERE AKTENZEICHEN = ?)",
                                ctx -> List.of(az), 0),
                        Erwartung.ganzeZahl("SozialFall mit Rente persistiert",
                                "SELECT COUNT(*) FROM SOZIAL_FALL WHERE BG_ID = (SELECT ID FROM "
                                        + "BEDARFSGEMEINSCHAFT WHERE AKTENZEICHEN = ?) "
                                        + "AND EINKOMMEN_RENTE = ?",
                                ctx -> List.of(az, rente), 1)))
                .mitWebAktionen(List.of(
                        WebAktion.wiederholbar("Anmeldung an der Weboberfläche", WebHelfer::anmeldung),
                        WebAktion.schritt("Neuen Vorgang anlegen (Aktenzeichen " + az + ")",
                                (d, ctx) -> WebHelfer.vorgangAnlegen(d, ctx, az,
                                        "Web, Negativ " + nr, BigDecimal.valueOf(1200))),
                        WebAktion.schritt("SozialFall mit hoher Rente erfassen",
                                (d, ctx) -> WebHelfer.fallAnlegenMitRente(d, ctx, rbs, false,
                                        false, BigDecimal.valueOf(300), BigDecimal.valueOf(50),
                                        BigDecimal.valueOf(70), rente)),
                        WebAktion.schritt("Auszahlung anordnen — KEIN_ANSPRUCH erwartet",
                                (d, ctx) -> WebHelfer.auszahlungFehlschlag(d, ctx, "2026-10",
                                        true, "kein Anspruch", "KEIN_ANSPRUCH"))))
                .dokumentiere("Hintergrund: Grundsicherung erhält, wer seinen Bedarf "
        + "nicht selbst decken kann — wer etwa eine hohe Rente bezieht, deren "
        + "Einkommen den kompletten monatlichen Bedarf (Regelbedarf plus Wohnkosten) "
        + "übersteigt, hat darauf keinen Anspruch (§ 43 Abs. 1 SGB XII: Einkommen und "
        + "Vermögen sind vorrangig einzusetzen). In diesem Testfall bedient der "
        + "simulierte Sachbearbeiter die echte Weboberfläche und erfasst eine Person "
        + "mit einer hohen Rente von " + rente.toPlainString() + " EUR monatlich — die "
        + "Erfassung selbst ist korrekt und vollständig. Dann ordnet er die Auszahlung "
        + "an. Die Berechnung ergibt einen negativen Zahlbetrag, die Anordnung ist "
        + "damit sachlich unzulässig. Erwartet wird, dass die Weboberfläche den "
        + "Fachfehler KEIN_ANSPRUCH als deutlich lesbaren Fehlerdialog zeigt "
        + "(„Einkommen/Vermögen übersteigen den Gesamtbedarf — kein Anspruch“) — der "
        + "Sachbearbeiter erfährt den Grund und kann die Ablehnung im Gespräch "
        + "begründen. Der Doppelbeweis über die Datenbank: Es darf KEINE Zahlung "
        + "entstanden sein (die Ablehnung hinterlässt keine Buchungsspur), während der "
        + "SozialFall mit der Rente korrekt gespeichert bleibt — die Erfassung war ja "
        + "rechtmäßig, nur der Auszahlungsversuch nicht. Ein Fehler wäre eine trotzdem "
        + "angeordnete Zahlung: Sie würde Geld zusagen, auf das rechtlich kein "
        + "Anspruch besteht.",
                        List.of());
    }

    private Auftrag negBudget(int nr, String praefix) {
        String az = String.format("%s%04d", praefix, nr);
        int rbs = 1 + random.nextInt(3);
        BigDecimal miete = BigDecimal.valueOf(300 + 10 * random.nextInt(21)).setScale(2);
        BigDecimal betrieb = BigDecimal.valueOf(40 + 5 * random.nextInt(15)).setScale(2);
        BigDecimal heizung = BigDecimal.valueOf(60 + 5 * random.nextInt(13)).setScale(2);
        List<SozialFall> faelle = List.of(WebHelfer.fallEntwurf(rbs, false, false,
                miete, betrieb, heizung));
        List<Rechenblock> rechnungen = new ArrayList<>();
        BigDecimal zahlbetrag = WebHelfer.berechne(rechnungen, "Bedarf (Budget bewusst zu klein)",
                faelle, BigDecimal.ZERO);
        BigDecimal budget = zahlbetrag.subtract(BigDecimal.valueOf(20 + 10L * random.nextInt(9)))
                .max(BigDecimal.valueOf(10)).setScale(2);
        rechnungen.replaceAll(b -> b.mitBudget(budget));
        return new Auftrag(nr, "WEB-NEG-BUDGET", az, "Web, Negativ " + nr,
                "Anordnung über dem Startbudget wird abgewehrt (BUDGET_ERSCHOEPFT)",
                List.of(), List.of(
                        Erwartung.ganzeZahl("keine Zahlung entstanden",
                                "SELECT COUNT(*) FROM ZAHLUNG WHERE BG_ID = (SELECT ID FROM "
                                        + "BEDARFSGEMEINSCHAFT WHERE AKTENZEICHEN = ?)",
                                ctx -> List.of(az), 0),
                        Erwartung.betrag("Budget unverändert",
                                "SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE AKTENZEICHEN = ?",
                                ctx -> List.of(az), budget.toPlainString())))
                .mitWebAktionen(List.of(
                        WebAktion.wiederholbar("Anmeldung an der Weboberfläche", WebHelfer::anmeldung),
                        WebAktion.schritt("Neuen Vorgang mit Startbudget " + budget.toPlainString()
                                + " EUR anlegen (bewusst zu klein)",
                                (d, ctx) -> WebHelfer.vorgangAnlegen(d, ctx, az,
                                        "Web, Negativ " + nr, budget)),
                        WebAktion.schritt("SozialFall erfassen (Bedarf "
                                + zahlbetrag.toPlainString() + " EUR)",
                                (d, ctx) -> WebHelfer.fallAnlegen(d, ctx, rbs, false, false,
                                        miete, betrieb, heizung)),
                        WebAktion.schritt("Auszahlung anordnen — BUDGET_ERSCHOEPFT erwartet",
                                (d, ctx) -> WebHelfer.auszahlungFehlschlag(d, ctx, "2026-10",
                                        true, "übersteigt das verfügbare Restbudget", "BUDGET"))))
                .dokumentiere("Hintergrund: Das Startbudget eines Vorgangs ist der Geldtopf, aus "
        + "dem die Auszahlungen dieses Falles bezahlt werden — er kann nie negativ "
        + "werden, denn das System darf nicht mehr versprechen, als der Vorgang zur "
        + "Verfügung hat. In diesem Testfall wird der Vorgang über die echte "
        + "Weboberfläche mit einem bewusst zu knapp bemessenen Startbudget angelegt, "
        + "während die berechnete monatliche Leistung der Person ("
        + zahlbetrag.toPlainString() + " EUR) höher ausfällt. Der Sachbearbeiter "
        + "erfasst alles korrekt und ordnet die Auszahlung an — der Bedarf selbst ist "
        + "richtig berechnet, nur der Topf ist zu klein. Erwartet wird, dass die "
        + "Budgetprüfung die Anordnung mit dem Fachfehler BUDGET_ERSCHOEPFT abwehrt "
        + "und die Oberfläche das als Fehlerdialog zeigt („Der Zahlbetrag … übersteigt "
        + "das verfügbare Restbudget …“): klar in der Sache, nachvollziehbar "
        + "begründet — wie eine Kasse, die eine Abbuchung mangels Deckung ablehnt, "
        + "statt still zu überziehen. Der Doppelbeweis über die Datenbank: Es darf "
        + "KEINE Zahlung entstanden sein, und das Budget darf nicht abgeflossen sein — "
        + "auch kein Teilbetrag, auch nicht ins Negative. Ein Fehler wäre jede über "
        + "das Budget hinausgehende Buchung; sie würde bedeuten, dass die harte "
        + "Budgetgrenze im Web-Weg umgehbar wäre.",
                        rechnungen);
    }

    private Auftrag negDoppelterMonat(int nr, String praefix) {
        String az = String.format("%s%04d", praefix, nr);
        int rbs = 1 + random.nextInt(3);
        BigDecimal miete = BigDecimal.valueOf(250 + 10 * random.nextInt(21)).setScale(2);
        BigDecimal betrieb = BigDecimal.valueOf(30 + 5 * random.nextInt(13)).setScale(2);
        BigDecimal heizung = BigDecimal.valueOf(50 + 5 * random.nextInt(13)).setScale(2);
        List<SozialFall> faelle = List.of(WebHelfer.fallEntwurf(rbs, false, false,
                miete, betrieb, heizung));
        List<Rechenblock> rechnungen = new ArrayList<>();
        BigDecimal zahlbetrag = WebHelfer.berechne(rechnungen, "Erste Anordnung",
                faelle, BigDecimal.ZERO);
        BigDecimal budget = zahlbetrag.add(BigDecimal.valueOf(100 + 10 * random.nextInt(21)));
        rechnungen.replaceAll(b -> b.mitBudget(budget));
        return new Auftrag(nr, "WEB-NEG-DOPPELMONAT", az, "Web, Negativ " + nr,
                "Zweite Anordnung für denselben Monat wird abgewehrt",
                List.of(), List.of(
                        Erwartung.ganzeZahl("genau eine Zahlung je Vorgang und Monat",
                                "SELECT COUNT(*) FROM ZAHLUNG WHERE BG_ID = (SELECT ID FROM "
                                        + "BEDARFSGEMEINSCHAFT WHERE AKTENZEICHEN = ?)",
                                ctx -> List.of(az), 1),
                        Erwartung.betrag("Budget nur einmal gemindert",
                                "SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE AKTENZEICHEN = ?",
                                ctx -> List.of(az), budget.subtract(zahlbetrag).toPlainString())))
                .mitWebAktionen(List.of(
                        WebAktion.wiederholbar("Anmeldung an der Weboberfläche", WebHelfer::anmeldung),
                        WebAktion.schritt("Neuen Vorgang anlegen (Aktenzeichen " + az + ")",
                                (d, ctx) -> WebHelfer.vorgangAnlegen(d, ctx, az,
                                        "Web, Negativ " + nr, budget)),
                        WebAktion.schritt("SozialFall erfassen",
                                (d, ctx) -> WebHelfer.fallAnlegen(d, ctx, rbs, false, false,
                                        miete, betrieb, heizung)),
                        WebAktion.schritt("Erste Auszahlung für 2026-10 anordnen",
                                (d, ctx) -> WebHelfer.auszahlungAnordnen(d, ctx, "2026-10")),
                        WebAktion.schritt("ZWEITE Auszahlung für 2026-10 — VALIDATION erwartet",
                                (d, ctx) -> WebHelfer.auszahlungFehlschlag(d, ctx, "2026-10",
                                        false, "existiert bereits eine nicht stornierte Zahlung"))))
                .dokumentiere("Hintergrund: Pro Vorgang und Monat darf es genau EINE wirksame "
        + "Zahlung geben — sonst erhielte dieselbe Person dieselbe Leistung zweimal. "
        + "Doppelanordnungen entstehen im Alltag leicht: Ein Klick „lädt nicht "
        + "sichtbar“, wird wiederholt; ein zweites Fenster ist noch offen; oder zwei "
        + "Kräfte bearbeiten denselben Fall unabhängig voneinander, ohne es voneinander "
        + "zu wissen. In diesem Testfall läuft der erste Weg über die echte "
        + "Weboberfläche vollständig korrekt: Vorgang anlegen, SozialFall erfassen, "
        + "Auszahlung für Oktober anordnen — die Zahlung steht. Dann ordnet derselbe "
        + "Sachbearbeiter über die Oberfläche ein zweites Mal für Oktober an. Da die "
        + "Oberfläche für jeden Klick eine frische Vorgangsnummer erzeugt, ist das "
        + "gerade NICHT der einfache Doppelklick, den Technik über gleiche Nummern "
        + "erkennt, sondern ein echter zweiter, unabhängiger Antrag — der härteste "
        + "Fall für die Sperre. Erwartet wird der Fehlerdialog der Fachschicht („Für "
        + "2026-10 existiert bereits eine nicht stornierte Zahlung …“). Der "
        + "Doppelbeweis über die Datenbank: Es bleibt GENAU EINE Zahlung für den "
        + "Monat, und das Budget ist nur EINMAL gemindert. Ein Fehler wäre jede zweite "
        + "Zahlung — sie würde die Person doppelt auszahlen und den Vorgang doppelt "
        + "belasten.",
                        rechnungen);
    }

    private Auftrag negStornoSachbearbeiter(int nr, String praefix) {
        String az = String.format("%s%04d", praefix, nr);
        int rbs = 1 + random.nextInt(3);
        BigDecimal miete = BigDecimal.valueOf(250 + 10 * random.nextInt(21)).setScale(2);
        BigDecimal betrieb = BigDecimal.valueOf(30 + 5 * random.nextInt(13)).setScale(2);
        BigDecimal heizung = BigDecimal.valueOf(50 + 5 * random.nextInt(13)).setScale(2);
        List<SozialFall> faelle = List.of(WebHelfer.fallEntwurf(rbs, false, false,
                miete, betrieb, heizung));
        List<Rechenblock> rechnungen = new ArrayList<>();
        BigDecimal zahlbetrag = WebHelfer.berechne(rechnungen, "Anordnung + Freigabe",
                faelle, BigDecimal.ZERO);
        BigDecimal budget = zahlbetrag.add(BigDecimal.valueOf(100 + 10 * random.nextInt(21)));
        rechnungen.replaceAll(b -> b.mitBudget(budget));
        return new Auftrag(nr, "WEB-NEG-STORNO-SB", az, "Web, Negativ " + nr,
                "Storno einer freigegebenen Zahlung ist der Sachbearbeiter-Rolle nicht möglich",
                List.of(), List.of(
                        Erwartung.text("Zahlung bleibt freigegeben (kein Storno durch SB)",
                                "SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ZAHLUNGSMONAT = ? "
                                        + "AND BG_ID = (SELECT ID FROM BEDARFSGEMEINSCHAFT "
                                        + "WHERE AKTENZEICHEN = ?)",
                                ctx -> List.of("2026-10", az), "BEREIT"),
                        Erwartung.betrag("Budget korrekt gemindert",
                                "SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE AKTENZEICHEN = ?",
                                ctx -> List.of(az), budget.subtract(zahlbetrag).toPlainString())))
                .mitWebAktionen(List.of(
                        WebAktion.wiederholbar("Anmeldung an der Weboberfläche", WebHelfer::anmeldung),
                        WebAktion.schritt("Neuen Vorgang anlegen (Aktenzeichen " + az + ")",
                                (d, ctx) -> WebHelfer.vorgangAnlegen(d, ctx, az,
                                        "Web, Negativ " + nr, budget)),
                        WebAktion.schritt("SozialFall erfassen",
                                (d, ctx) -> WebHelfer.fallAnlegen(d, ctx, rbs, false, false,
                                        miete, betrieb, heizung)),
                        WebAktion.schritt("Auszahlung für 2026-10 anordnen",
                                (d, ctx) -> WebHelfer.auszahlungAnordnen(d, ctx, "2026-10")),
                        WebAktion.wiederholbar("Abmelden und als Kollegin/Kollege anmelden (Vier-Augen)",
                                WebHelfer::kollegenLogin),
                        WebAktion.wiederholbar("Vorgang über die Suche finden und öffnen",
                                (d, ctx) -> WebHelfer.vorgangSuchen(d, ctx, az)),
                        WebAktion.schritt("Zahlung freigeben (rechnerisch feststellen)",
                                WebHelfer::freigeben),
                        WebAktion.schritt("Storno-Knopf wird der Sachbearbeiter-Rolle korrekt vorenthalten",
                                WebHelfer::pruefeKeinStornoFuerSachbearbeiter)))
                .dokumentiere("Hintergrund: Eine freigegebene Zahlung (Status BEREIT) "
        + "steht kurz davor, im nächsten Kassenlauf an die Zahlstelle "
        + "übergeben zu werden. Das Zurückholen einer solchen Zahlung ist "
        + "eine besonders folgenreiche Entscheidung — deshalb gilt im "
        + "Projekt die Regel: Der Storno-Knopf für freigegebene Zahlungen "
        + "wird nur der Administrationsrolle angeboten, nicht der "
        + "Sachbearbeiter-Rolle. Das ist eine bewusste Ergänzung zum "
        + "Vier-Augen-Prinzip: Anordnen und freigeben können zwei "
        + "Sachbearbeiterinnen; das Abbrechen kurz vor der Auszahlung "
        + "erfordert eine höhere Befugnis. In diesem Testfall durchläuft "
        + "eine Zahlung zuerst den kompletten regulären Weg über die echte "
        + "Weboberfläche: Anordnung, Rollenwechsel zu einer zweiten Kraft, "
        + "Freigabe — die Zahlung steht auf BEREIT. Dann bleibt dieselbe "
        + "Sachbearbeiter-Kraft im offenen Vorgang und schaut gezielt nach "
        + "dem Storno-Knopf. Erwartet wird, dass die Oberfläche diesen "
        + "Knopf für die Sachbearbeiter-Rolle GAR NICHT anbietet — nicht "
        + "nur gesperrt oder ausgegraut, sondern abwesend: Was nicht da "
        + "ist, kann nicht versehentlich geklickt werden. Der "
        + "Doppelbeweis über die Datenbank: Die Zahlung steht weiterhin "
        + "unverändert auf BEREIT, und das Budget bleibt entsprechend "
        + "gemindert. Ein Fehler wäre ein sichtbarer Storno-Knopf für die "
        + "Sachbearbeiter-Rolle oder ein heimlich veränderter Status — "
        + "beides würde die Rollentrennung aushebeln.", rechnungen);
    }

    // ------------------------------------------------------------------
    // LAST (Modus 2): der schlanke UI-Durchlauf in hoher Stückzahl
    // ------------------------------------------------------------------

    private Auftrag lastDurchlauf(int nr, String praefix) {
        String az = String.format("%s%04d", praefix, nr);
        int rbs = 1 + random.nextInt(3);
        BigDecimal miete = BigDecimal.valueOf(250 + 10 * random.nextInt(31)).setScale(2);
        BigDecimal betrieb = BigDecimal.valueOf(30 + 5 * random.nextInt(19)).setScale(2);
        BigDecimal heizung = BigDecimal.valueOf(50 + 5 * random.nextInt(19)).setScale(2);
        List<SozialFall> faelle = List.of(WebHelfer.fallEntwurf(rbs, false, false,
                miete, betrieb, heizung));
        List<Rechenblock> rechnungen = new ArrayList<>();
        BigDecimal zahlbetrag = WebHelfer.berechne(rechnungen, "Lasttest-Durchlauf",
                faelle, BigDecimal.ZERO);
        BigDecimal budget = zahlbetrag.add(BigDecimal.valueOf(100 + 10 * random.nextInt(21)));
        rechnungen.replaceAll(b -> b.mitBudget(budget));
        return new Auftrag(nr, "WEB-LAST-DURCHLAUF", az, "Web, Last " + nr,
                "Schlanker UI-Durchlauf (Anordnung ohne Freigabe)",
                List.of(), List.of(
                        Erwartung.einsVon("Zahlung wirksam (Vier-Augen-Warteschlange)",
                                "SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ZAHLUNGSMONAT = ? "
                                        + "AND BG_ID = (SELECT ID FROM BEDARFSGEMEINSCHAFT "
                                        + "WHERE AKTENZEICHEN = ?)",
                                ctx -> List.of("2026-10", az), "ANGELEGT|BEREIT|UEBERGEBEN"),
                        Erwartung.betrag("Auszahlungsbetrag entspricht der Berechnung",
                                "SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ZAHLUNGSMONAT = ? "
                                        + "AND BG_ID = (SELECT ID FROM BEDARFSGEMEINSCHAFT "
                                        + "WHERE AKTENZEICHEN = ?)",
                                ctx -> List.of("2026-10", az), zahlbetrag.toPlainString()),
                        Erwartung.betrag("Restbudget korrekt gemindert",
                                "SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE AKTENZEICHEN = ?",
                                ctx -> List.of(az), budget.subtract(zahlbetrag).toPlainString()),
                        Erwartung.ganzeZahl("SozialFall mit Stufe persistiert",
                                "SELECT COUNT(*) FROM SOZIAL_FALL WHERE REGELBEDARFSSTUFE = ? "
                                        + "AND BG_ID = (SELECT ID FROM BEDARFSGEMEINSCHAFT "
                                        + "WHERE AKTENZEICHEN = ?)",
                                ctx -> List.of(rbs, az), 1)))
                .mitWebAktionen(List.of(
                        WebAktion.wiederholbar("Anmeldung an der Weboberfläche", WebHelfer::anmeldung),
                        WebAktion.schritt("Neuen Vorgang anlegen (Aktenzeichen " + az + ")",
                                (d, ctx) -> WebHelfer.vorgangAnlegen(d, ctx, az,
                                        "Web, Last " + nr, budget)),
                        WebAktion.schritt("SozialFall erfassen (RBS " + rbs + ")",
                                (d, ctx) -> WebHelfer.fallAnlegen(d, ctx, rbs, false, false,
                                        miete, betrieb, heizung)),
                        WebAktion.schritt("Auszahlung für 2026-10 anordnen",
                                (d, ctx) -> WebHelfer.auszahlungAnordnen(d, ctx, "2026-10"))))
                .dokumentiere("Schlanker Durchlauf für den Weboberflächen-Lasttest: "
                        + "Anmeldung, Vorgang anlegen, SozialFall erfassen, Auszahlung "
                        + "anordnen — bewusst ohne Vier-Augen-Freigabe und Storno, damit "
                        + "möglichst viele UI-Transaktionen je Zeiteinheit durch Server, "
                        + "Transaktionssteuerung und Datenbank laufen.", rechnungen);
    }
}
