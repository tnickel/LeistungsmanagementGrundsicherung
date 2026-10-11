package de.allgemein.testsweet;

import de.allgemein.lmg.calc.Leistungsberechnung;
import de.allgemein.lmg.model.SozialFall;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Automatische Testdokumentation eines Simulator-Laufs als PDF:
 * Deckblatt mit Laufdaten, Inhaltsverzeichnis mit Seitenzahlen, zusammenfassende
 * Ergebnisübersicht und danach je Einzelfall der vollständige Nachweis — in
 * einfachen Worten beschrieben, mit Eingaben, Schritt-für-Schritt-Rechenweg
 * (jede Rechenoperation mit Zwischenwert und Norm-Hinweis, vollständig von Hand
 * nachrechenbar), erwartetem Datenbank-Endzustand gegen Ist-Wert und dem
 * Protokollausschnitt des Ablaufs.
 */
final class Testdokumentation {

    private static final DateTimeFormatter ZEIT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss.SSS");
    private static final DateTimeFormatter TAG =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private Testdokumentation() {
    }

    static void schreibe(Path ziel, Simulator simulator) throws IOException {
        List<Auftrag> auftraege = simulator.auftraege();

        // ------------------------------------------------------------
        // Teil 2 zuerst bauen (Zusammenfassung + Einzelfälle mit Marken),
        // danach Teil 1 (Deckblatt + Einleitung + Inhaltsverzeichnis) mit
        // den echten Seitenzahlen davor setzen.
        // ------------------------------------------------------------
        SweetPdf inhalt = new SweetPdf();
        inhalt.setzeFusszeile("testsweet — automatische Testdokumentation (Simulator-Lauf "
                + simulator.laufStart().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")) + ")");
        zusammenfassung(inhalt, simulator);
        for (Auftrag auftrag : auftraege) {
            einzelfall(inhalt, auftrag);
        }
        Map<String, Integer> markenSeiten = inhalt.seitenDerMarken();

        // Kopf zweimal bauen: erst ohne Seitenzahlen (nur um die Anzahl der
        // Kopfseiten zu messen), dann mit echten Zahlen — die Zeilenzahl und damit
        // die Paginierung bleibt identisch, die Einzelfall-Seiten verschieben sich
        // um genau diesen Offset.
        SweetPdf messKopf = new SweetPdf();
        messKopf.setzeFusszeile("testsweet — automatische Testdokumentation");
        deckblatt(messKopf, simulator);
        einleitung(messKopf, simulator);
        inhaltsverzeichnis(messKopf, auftraege, null, 0);
        int offset = messKopf.seitenAnzahl();

        SweetPdf kopf = new SweetPdf();
        kopf.setzeFusszeile("testsweet — automatische Testdokumentation");
        deckblatt(kopf, simulator);
        einleitung(kopf, simulator);
        inhaltsverzeichnis(kopf, auftraege, markenSeiten, offset);

        inhalt.uebernehmeVorn(kopf.eintraegeKopie());
        int seiten = inhalt.schreiben(ziel);
        simulator.log().ereignis("SIMULATOR", "",
                "Testdokumentation geschrieben: " + ziel + " (" + seiten + " Seiten)");
    }

    // ==================================================================
    // Deckblatt
    // ==================================================================

    private static void deckblatt(SweetPdf pdf, Simulator simulator) {
        pdf.leereZeile();
        pdf.leereZeile();
        pdf.leereZeile();
        pdf.text("SDN.sozial — Leistungsmanagement Grundsicherung (LMG)", SweetPdf.GRAU, 12);
        pdf.leereZeile();
        pdf.text("Automatische Testdokumentation", SweetPdf.BLAU, 26);
        pdf.leereZeile();
        pdf.text("Mitarbeiter-Simulator \u201etestsweet\u201c", SweetPdf.SCHWARZ, 14);
        pdf.text("Modus 1: Fachschicht-/Transaktionstest", SweetPdf.SCHWARZ, 14);
        pdf.leereZeile();
        pdf.linie();
        pdf.leereZeile();
        pdf.paar("Lauf", simulator.laufStart().format(TAG) + " bis "
                + (simulator.laufEnde() == null ? "\u2014" : simulator.laufEnde().format(TAG))
                + " (" + simulator.laufdauerSekunden() + " s)");
        pdf.paar("Datenbank", simulator.dbUrl + " (Benutzer " + simulator.dbUser + ")");
        if (simulator.konfiguration() != null) {
            pdf.paar("Konfiguration", simulator.konfiguration().mitarbeiter() + " simulierte "
                    + "Sachbearbeiter, " + simulator.konfiguration().auftraege()
                    + " Aufträge, Seed " + simulator.konfiguration().seed()
                    + ", Denkzeit " + simulator.konfiguration().denkzeitMs() + " ms je Schritt");
        }
        pdf.paar("Testgegenstand", "Transaktionssteuerung und Fachschicht unter paralleler "
                + "Bearbeitung: Sperren, Deadlock-Wiederholung, Budget-Führung, "
                + "Vier-Augen-Prinzip, optimistisches Locking, Idempotenz");
        pdf.leereZeile();
        int verifiziert = 0;
        int nok = 0;
        int fehl = 0;
        for (Auftrag a : simulator.auftraege()) {
            verifiziert += a.status == Auftrag.Status.VERIFIZIERT ? 1 : 0;
            nok += a.status == Auftrag.Status.VERIFIKATION_FEHLGESCHLAGEN ? 1 : 0;
            fehl += a.status == Auftrag.Status.FEHLGESCHLAGEN ? 1 : 0;
        }
        pdf.text("Gesamtergebnis: " + verifiziert + " von " + simulator.auftraege().size()
                        + " Testfällen vollständig verifiziert"
                        + (nok + fehl == 0 ? " — keine Abweichungen." : " — "
                        + (nok + fehl) + " Auffälligkeit(en), siehe Zusammenfassung."),
                nok + fehl == 0 ? SweetPdf.GRUEN : SweetPdf.ROT, 13);
        pdf.leereZeile();
        pdf.leereZeile();
        pdf.text("Dieses Dokument wurde automatisch aus dem Simulator-Lauf erzeugt. "
                + "Es beginnt mit einer Einleitung (Aufbau, Lesart, Rechengrundlagen), "
                + "einer Zusammenfassung der Ergebnisse und dokumentiert danach jeden "
                + "einzelnen Testfall so, dass er ohne Werkzeuge nachgerechnet und "
                + "nachvollzogen werden kann.", SweetPdf.GRAU, 10);
        pdf.seitenUmbruch();
    }

    // ==================================================================
    // Einleitung (ausführlich)
    // ==================================================================

    private static void einleitung(SweetPdf pdf, Simulator simulator) {
        pdf.ueberschrift("Einleitung");
        pdf.abschnittsTitel("Zweck dieses Dokuments");
        pdf.text("Der Mitarbeiter-Simulator \u201etestsweet\u201c prüft, ob die Fachschicht des "
                + "Leistungsmanagements Grundsicherung (SDN.sozial LMG) auch dann korrekt "
                + "arbeitet, wenn mehrere Sachbearbeiter GLEICHZEITIG Vorgänge anlegen, Fälle "
                + "ändern, Bescheide erteilen, Auszahlungen anordnen, freigeben, stornieren "
                + "und historisieren. Jeder simulierte Sachbearbeiter ist ein eigener "
                + "Ausführungsstrang mit eigenem Kürzel; er nimmt sich — wie im Arbeitsleben — "
                + "eine Aufgabe nach der anderen aus einem gemeinsamen Auftragspool, bis dieser "
                + "leer ist. Im Modus 1 rufen die Sachbearbeiter dazu direkt die Fachschicht "
                + "auf (SozialLeistungsService über die Transaktionssteuerung TxManager auf "
                + "der Oracle-Datenbank) — der gleiche Weg, den auch die Weboberfläche "
                + "serverseitig nimmt.");
        pdf.text("Nach dem Lauf prüft der Simulator für jeden Auftrag die Erwartungshaltung "
                + "direkt per SQL gegen die Datenbank: Stimmen Beträge, Statuswerte, "
                + "Budgets, Fassungen (Versionen) und Zuordnungen genau so, wie es das "
                + "Fachrecht verlangt? Dieses Dokument hält das Ergebnis fest — als "
                + "Nachweis und als Grundlage für die manuelle Fehlersuche in den "
                + "Logdateien des Laufs.");
        pdf.abschnittsTitel("Wie ein Testfall aufgebaut ist");
        pdf.text("Jeder Testfall beginnt auf einer neuen Seite und folgt immer demselben Aufbau:", SweetPdf.SCHWARZ, 10);
        pdf.text("1. Ergebnisleiste — farbig: grün = vollständig verifiziert, rot = Auffälligkeit.", SweetPdf.SCHWARZ, 10);
        pdf.text("2. \u201eWas wird getestet?\u201c — der Testfall in einfachen Worten: die "
                + "Ausgangssituation, der Ablauf und warum der Fall so konstruiert ist.", SweetPdf.SCHWARZ, 10);
        pdf.text("3. Eckdaten — Hauptperson, bearbeitender Sachbearbeiter, Vorgangs-ID, Dauer.", SweetPdf.SCHWARZ, 10);
        pdf.text("4. Erwartete Berechnung — Schritt für Schritt mit JEDEM Zwischenwert und "
                + "der jeweiligen Norm (§). Wer will, kann jeden Betrag mit dem "
                + "Taschenrechner nachvollziehen; alle Rechengrundlagen stehen unten "
                + "in dieser Einleitung.", SweetPdf.SCHWARZ, 10);
        pdf.text("5. Erwarteter Datenbank-Endzustand gegen Ist-Wert — jede Erwartung mit "
                + "OK/n.i.O. und dem tatsächlichen Wert aus der Datenbank.", SweetPdf.SCHWARZ, 10);
        pdf.text("6. Protokollausschnitt — welcher Sachbearbeiter wann welchen Schritt mit "
                + "welchem Ergebnis ausgeführt hat (inklusive erwarteter Fachfehler, "
                + "z. B. KONKURRENZ beim veralteten Speichern).", SweetPdf.SCHWARZ, 10);
        pdf.abschnittsTitel("Rechengrundlagen 2026 (für alle Testfälle dieses Dokuments)");
        pdf.fettZeile("Regelbedarfsstufen (Anlage zu § 28 SGB XII):", SweetPdf.BLAU, 10);
        pdf.text("    Stufe 1: 563,00 EUR   Stufe 2: 506,00 EUR   Stufe 3: 451,00 EUR   "
                + "Stufe 4: 471,00 EUR   Stufe 5: 390,00 EUR   Stufe 6: 357,00 EUR", SweetPdf.SCHWARZ, 10);
        pdf.fettZeile("Mehrbedarfe (§ 30 SGB XII):", SweetPdf.BLAU, 10);
        pdf.text("    Merkzeichen G: 17 % des eigenen Regelbedarfs (Abs. 1). "
                + "Schwangerschaft ab der 13. Woche: 17 % (Abs. 2). "
                + "Alleinerziehende: 36 % der STUFE 1 bei einem Kind unter 7 oder 2\u20133 "
                + "Kindern unter 16, sonst 12 % je minderjährigem Kind; Deckel 60 % der "
                + "Stufe 1 = 337,80 EUR (Abs. 3). Die Summe der Zuschläge nach Abs. 1\u20135 "
                + "darf den eigenen Regelbedarf nicht übersteigen (Abs. 6). Warmwasser "
                + "dezentral: 2,3 % (Stufen 1\u20132), 1,4 % (Stufe 4), 1,2 % (Stufe 5), "
                + "0,8 % (Stufe 6), Stufe 3 ohne Satz — gilt zusätzlich zu Abs. 6 (Abs. 7).",
                SweetPdf.SCHWARZ, 10);
        pdf.fettZeile("Kosten der Unterkunft (§ 42a SGB XII):", SweetPdf.BLAU, 10);
        pdf.text("    Anerkannt werden Kaltmiete + kalte Betriebskosten + Heizkosten. Die "
                + "örtliche Angemessenheitsprüfung ist in diesem Lauf deaktiviert "
                + "(standardunabhängige Prüfung); ein Warmwasserabzug entfällt, da die "
                + "Fälle ohne zentrale Warmwasserbereitung erfasst sind.", SweetPdf.SCHWARZ, 10);
        pdf.fettZeile("Einkommen (§ 82 SGB XII):", SweetPdf.BLAU, 10);
        pdf.text("    Vom Brutto-Erwerbseinkommen wird ein Freibetrag von 30 % abgezogen, "
                + "höchstens jedoch 50 % der Regelbedarfsstufe 1 = 281,50 EUR (Abs. 3). "
                + "ERST DANACH wirken die Absetzbeträge: Steuern, Sozialversicherung, "
                + "Versicherungen, Erwerbsaufwendungen (Abs. 2).", SweetPdf.SCHWARZ, 10);
        pdf.fettZeile("Vermögen (§ 90 Abs. 2 Nr. 9 SGB XII i.V.m. § 1 BarbetragsVO):", SweetPdf.BLAU, 10);
        pdf.text("    Schonvermögen: 10.000 EUR je erwachsener leistungsberechtigter Person "
                + "(Regelbedarfsstufen 1\u20133) und 500 EUR je weiterer, überwiegend "
                + "unterhaltener Person. Nur der Überschuss wird wie Einkommen "
                + "angerechnet; übersteigt er den gesamten Bedarf, besteht kein "
                + "Anspruch (§ 43 Abs. 1).", SweetPdf.SCHWARZ, 10);
        pdf.fettZeile("Vier-Augen-Prinzip:", SweetPdf.BLAU, 10);
        pdf.text("    Jede Auszahlung beginnt im Status ANGELEGT und wird erst durch die "
                + "Freigabe EINER ANDEREN Kraft wirksam (BEREIT); die Kasse überführt "
                + "freigegebene Zahlungen nach UEBERGEBEN. Der Simulator lässt die Freigabe "
                + "daher immer durch ein zufällig gewähltes Kollegen-Kürzel erteilen.",
                SweetPdf.SCHWARZ, 10);
        pdf.leereZeile();
        pdf.text("Alle Beträge in diesem Dokument sind in Euro mit zwei Nachkommastellen "
                + "angegeben; Prozentwerte werden kaufmännisch auf zwei Nachkommastellen "
                + "gerundet (HALF_UP).", SweetPdf.GRAU, 9.5f);
        pdf.seitenUmbruch();
    }

    // ==================================================================
    // Inhaltsverzeichnis
    // ==================================================================

    private static void inhaltsverzeichnis(SweetPdf pdf, List<Auftrag> auftraege,
            Map<String, Integer> markenSeiten, int offset) {
        pdf.ueberschrift("Inhaltsverzeichnis");
        pdf.leereZeile();
        pdf.tocZeile("Einleitung und Rechengrundlagen", 2, SweetPdf.SCHWARZ);
        pdf.tocZeile("Zusammenfassung und Ergebnis in Zahlen", offset, SweetPdf.SCHWARZ);
        for (Auftrag a : auftraege) {
            Integer seite = markenSeiten == null ? null : markenSeiten.get("auftrag-" + a.id);
            pdf.tocZeile("Testfall " + String.format("%02d", a.id) + " — " + a.szenarioTyp
                            + " (" + a.aktenzeichen + ")",
                    seite == null ? 0 : offset + seite - 1,
                    a.status == Auftrag.Status.VERIFIZIERT ? SweetPdf.SCHWARZ : SweetPdf.ROT);
        }
        pdf.leereZeile();
        pdf.text("Rote Einträge: Testfall mit Abweichung oder fehlgeschlagen.", SweetPdf.GRAU, 9);
        pdf.seitenUmbruch();
    }

    // ==================================================================
    // Zusammenfassung
    // ==================================================================

    private static void zusammenfassung(SweetPdf pdf, Simulator simulator) {
        List<Auftrag> auftraege = simulator.auftraege();
        pdf.ueberschrift("Zusammenfassung");
        pdf.paar("Modus", "1 — Fachschicht/Transaktionstest (Aufrufe direkt über den "
                + "SozialLeistungsService der Fachschicht)");
        pdf.paar("Ablauf", "Die simulierten Sachbearbeiter nehmen nacheinander Aufträge aus "
                + "einem gemeinsamen Pool und spielen sie parallel; erst wenn der Pool leer "
                + "ist, wird die Erwartungshaltung jedes Auftrags per SQL direkt gegen die "
                + "Datenbank geprüft.");
        pdf.leereZeile();
        pdf.abschnittsTitel("Ergebnis in Zahlen");
        int verifiziert = 0;
        int verifikationNok = 0;
        int fehlgeschlagen = 0;
        for (Auftrag a : auftraege) {
            verifiziert += a.status == Auftrag.Status.VERIFIZIERT ? 1 : 0;
            verifikationNok += a.status == Auftrag.Status.VERIFIKATION_FEHLGESCHLAGEN ? 1 : 0;
            fehlgeschlagen += a.status == Auftrag.Status.FEHLGESCHLAGEN ? 1 : 0;
        }
        pdf.text("Aufträge gesamt: " + auftraege.size(), SweetPdf.SCHWARZ, 10);
        pdf.text("Vollständig verifiziert (Datenbank-Endzustand exakt wie erwartet): "
                + verifiziert, verifiziert == auftraege.size() ? SweetPdf.GRUEN : SweetPdf.BLAU, 10);
        pdf.text("Verifikation mit Abweichungen: " + verifikationNok,
                verifikationNok == 0 ? SweetPdf.GRUEN : SweetPdf.ROT, 10);
        pdf.text("Beim Spielen fehlgeschlagen: " + fehlgeschlagen,
                fehlgeschlagen == 0 ? SweetPdf.GRUEN : SweetPdf.ROT, 10);
        pdf.leereZeile();
        pdf.abschnittsTitel("Verteilung der Testfallarten");
        Map<String, Integer> verteilung = new java.util.LinkedHashMap<>();
        for (Auftrag a : auftraege) {
            verteilung.merge(a.szenarioTyp, 1, Integer::sum);
        }
        StringBuilder zeile = new StringBuilder();
        for (Map.Entry<String, Integer> e : verteilung.entrySet()) {
            zeile.append(e.getKey()).append(" x").append(e.getValue()).append("   ");
        }
        pdf.text(zeile.toString().trim(), SweetPdf.SCHWARZ, 10);

        if (verifikationNok > 0 || fehlgeschlagen > 0) {
            pdf.leereZeile();
            pdf.abschnittsTitel("Auffällige Fälle (Detail ab Seite des jeweiligen Testfalls)");
            for (Auftrag a : auftraege) {
                if (a.status == Auftrag.Status.VERIFIKATION_FEHLGESCHLAGEN
                        || a.status == Auftrag.Status.FEHLGESCHLAGEN) {
                    StringBuilder abweichungen = new StringBuilder();
                    for (Auftrag.Pruefung p : a.pruefungen()) {
                        if (!p.ok()) {
                            abweichungen.append(abweichungen.length() == 0 ? "" : "; ")
                                    .append(p.beschreibung()).append(" — erwartet [")
                                    .append(p.erwartet()).append("], ist [").append(p.ist()).append(']');
                        }
                    }
                    pdf.text("Testfall " + a.id + " (" + a.aktenzeichen + ", " + a.szenarioTyp
                            + "): " + (a.fehlerText != null ? a.fehlerText : abweichungen),
                            SweetPdf.ROT, 9.5f);
                }
            }
        }
        // bewusst KEIN abschließender Seitenumbruch: Der erste Testfall beginnt
        // selbst mit einem Umbruch — ein doppelter erzeugte eine Leerseite.
    }

    // ==================================================================
    // Einzelfall
    // ==================================================================

    private static void einzelfall(SweetPdf pdf, Auftrag a) {
        pdf.seitenUmbruch();
        pdf.marke("auftrag-" + a.id);
        int farbe = a.status == Auftrag.Status.VERIFIZIERT ? SweetPdf.GRUEN
                : a.status == Auftrag.Status.FEHLGESCHLAGEN
                || a.status == Auftrag.Status.VERIFIKATION_FEHLGESCHLAGEN ? SweetPdf.ROT
                : SweetPdf.BLAU;
        pdf.kasten("TESTFALL " + String.format("%02d", a.id) + " — " + a.szenarioTyp
                + "   (" + a.aktenzeichen + ")", true, SweetPdf.WEISS, SweetPdf.FUELL_BLAU, 13);
        pdf.kasten(a.status == Auftrag.Status.VERIFIZIERT
                ? "ERGEBNIS: VERIFIZIERT — Datenbank-Endzustand vollständig wie erwartet"
                : statusText(a).replace("ERGEBNIS: ", ""), true, SweetPdf.WEISS,
                a.status == Auftrag.Status.VERIFIZIERT ? SweetPdf.FUELL_GRUEN : SweetPdf.FUELL_ROT,
                10.5f);

        pdf.kasten("Was wird getestet? (einfach beschrieben)", true, SweetPdf.BLAU,
                SweetPdf.FUELL_HELLBLAU, 11);
        pdf.text(a.beschreibungEinfach == null || a.beschreibungEinfach.isBlank()
                ? a.beschreibung : a.beschreibungEinfach, SweetPdf.SCHWARZ, 10);

        pdf.kasten("Eckdaten des Testfalls", true, SweetPdf.BLAU, SweetPdf.FUELL_HELLBLAU, 11);
        pdf.paar("Fachlicher Ablauf", a.beschreibung);
        pdf.paar("Hauptperson", a.hauptperson);
        pdf.paar("Bearbeitet von", a.bearbeiter != null ? a.bearbeiter : "\u2014");
        pdf.paar("Vorgang (BG-ID)", a.bgId != null ? String.valueOf(a.bgId) : "\u2014");
        pdf.paar("Bearbeitungsdauer", a.dauerMs() + " ms");
        pdf.paar("Erwartete Dauer des Falls", a.aktionen.size() + " Arbeitsschritte");

        // ------------------------------------------------------------
        // Rechenweg
        // ------------------------------------------------------------
        if (!a.rechenbloecke.isEmpty()) {
            pdf.kasten("Erwartete Berechnung — Schritt für Schritt nachrechenbar", true,
                    SweetPdf.BLAU, SweetPdf.FUELL_HELLBLAU, 11);
            for (Rechenblock block : a.rechenbloecke) {
                pdf.fettZeile(block.titel(), SweetPdf.BLAU, 10.5f);
                rechneBlock(pdf, block);
                pdf.leereZeile();
            }
        }

        // ------------------------------------------------------------
        // Erwartung vs. Ist
        // ------------------------------------------------------------
        pdf.kasten("Erwarteter Datenbank-Endzustand und Nachweis", true, SweetPdf.BLAU,
                SweetPdf.FUELL_HELLBLAU, 11);
        if (a.pruefungen().isEmpty()) {
            pdf.text("Keine Verifikation durchgeführt"
                    + (a.status == Auftrag.Status.FEHLGESCHLAGEN
                    ? " — der Testfall scheiterte bereits beim Spielen." : "."),
                    SweetPdf.GRAU, 9.5f);
        }
        for (Auftrag.Pruefung p : a.pruefungen()) {
            pdf.text((p.ok() ? "[OK]     " : "[n.i.O.] ") + p.beschreibung(),
                    p.ok() ? SweetPdf.GRUEN : SweetPdf.ROT, 9.5f);
            pdf.text("         erwartet: " + p.erwartet() + "   |   Ist (DB): " + p.ist(),
                    SweetPdf.GRAU, 9);
        }

        if (a.fehlerText != null) {
            pdf.kasten("Fehler", true, SweetPdf.WEISS, SweetPdf.FUELL_ROT, 11);
            pdf.text(a.fehlerText, SweetPdf.ROT, 9.5f);
        }

        pdf.kasten("Protokollausschnitt (Ablauf des Testfalls)", true, SweetPdf.BLAU,
                SweetPdf.FUELL_HELLBLAU, 11);
        for (Auftrag.Schritt s : a.schritte()) {
            pdf.text(s.zeit().format(ZEIT) + "  " + s.mitarbeiter() + ": " + s.aktion(),
                    SweetPdf.SCHWARZ, 9);
            String ergebnis = s.ergebnis();
            int ergebnisFarbe = ergebnis.startsWith("FEHLGESCHLAGEN") ? SweetPdf.ROT
                    : ergebnis.startsWith("ERWARTETER FEHLER") ? SweetPdf.BLAU
                    : ergebnis.startsWith("ABGEBROCHEN") ? SweetPdf.ROT
                    : SweetPdf.GRUEN;
            pdf.text("    -> " + ergebnis, ergebnisFarbe, 9);
        }
        // Kein zusätzlicher Abschluss-Block: Die Titelleiste des nächsten Testfalls
        // (eigene Seite) trennt eindeutig — eine alleinstehende "Ende"-Zeile würde
        // gelegentlich als verwaiste Seite übrig bleiben.
    }

    // ==================================================================
    // Rechenweg-Aufbereitung (vollständig nachrechenbar)
    // ==================================================================

    private static void rechneBlock(SweetPdf pdf, Rechenblock block) {
        List<Leistungsberechnung.FallBerechnung> einzeln = block.ergebnis().faelle();
        for (int i = 0; i < block.faelle().size(); i++) {
            SozialFall fall = block.faelle().get(i);
            Leistungsberechnung.FallBerechnung f = einzeln.get(i);
            pdf.fettZeile("Person " + (i + 1) + " — Regelbedarfsstufe " + fall.regelbedarfsstufe()
                    + (fall.merkzeichenG() || fall.schwanger() || fall.warmwasser()
                    || fall.anzKinderMinderjaehrig() > 0
                    ? " (mit Mehrbedarfsmerkmalen)" : ""), SweetPdf.SCHWARZ, 10);
            schritt(pdf, "Regelbedarf der Stufe " + fall.regelbedarfsstufe()
                    + " (Anlage zu § 28 SGB XII)", f.regelbedarf());
            if (f.mehrbedarfG().signum() > 0) {
                schritt(pdf, "+ Mehrbedarf Merkzeichen G: 17 % von " + euro(f.regelbedarf())
                        + " (§ 30 Abs. 1)", f.mehrbedarfG());
            }
            if (f.mehrbedarfSchwangere().signum() > 0) {
                schritt(pdf, "+ Mehrbedarf Schwangerschaft: 17 % von " + euro(f.regelbedarf())
                        + " (§ 30 Abs. 2, ab der 13. Schwangerschaftswoche)", f.mehrbedarfSchwangere());
            }
            if (f.mehrbedarfAlleinerziehend().signum() > 0) {
                schritt(pdf, "+ Mehrbedarf Alleinerziehende: "
                        + alleinerziehendErklaerung(fall, f) + " (§ 30 Abs. 3)",
                        f.mehrbedarfAlleinerziehend());
            }
            if (f.mehrbedarfWarmwasser().signum() > 0) {
                schritt(pdf, "+ Mehrbedarf Warmwasser dezentral: "
                        + warmwasserSatz(fall.regelbedarfsstufe()) + " von "
                        + euro(f.regelbedarf()) + " (§ 30 Abs. 7, gilt zusätzlich zur "
                        + "Obergrenze des Abs. 6)", f.mehrbedarfWarmwasser());
            }
            if (f.mehrbedarfG().add(f.mehrbedarfSchwangere()).add(f.mehrbedarfAlleinerziehend())
                    .compareTo(f.regelbedarf()) >= 0) {
                pdf.text("Hinweis: Die Summe der Zuschläge nach § 30 Abs. 1\u20135 erreicht die "
                        + "Obergrenze des § 30 Abs. 6 (höchstens der Regelbedarf).",
                        SweetPdf.GRAU, 9);
            }
            schritt(pdf, "+ Kosten der Unterkunft: Miete " + euro(fall.kduMiete())
                    + " + Betriebskosten " + euro(fall.kduBetriebskosten())
                    + " + Heizung " + euro(fall.kduHeizung())
                    + " (§ 42a SGB XII; Angemessenheitsprüfung deaktiviert)", f.kdu());
            schritt(pdf, "= Gesamtbedarf Person " + (i + 1), f.gesamtbedarf());

            if (fall.einkommenRente().signum() > 0 || fall.einkommenErwerb().signum() > 0
                    || fall.absetzbetaetrag().signum() > 0) {
                pdf.rohZeile("    Einkommensberechnung (§ 82 SGB XII):", SweetPdf.SCHWARZ, 9.5f);
                schritt(pdf, "    Einnahmen: Rente " + euro(fall.einkommenRente())
                        + " + Erwerbseinkommen " + euro(fall.einkommenErwerb()),
                        fall.einkommenRente().add(fall.einkommenErwerb()));
                schritt(pdf, "    \u2013 Erwerbsfreibetrag: 30 % von "
                        + euro(fall.einkommenErwerb()) + " = "
                        + euro(fall.einkommenErwerb().multiply(new BigDecimal("0.30"))
                                .setScale(2, java.math.RoundingMode.HALF_UP))
                        + ", höchstens 281,50 (= 50 % der Stufe 1) (§ 82 Abs. 3)",
                        f.freibetragErwerb());
                if (f.freibetragErwerb().compareTo(new BigDecimal("281.50")) == 0
                        && fall.einkommenErwerb().multiply(new BigDecimal("0.30"))
                        .compareTo(new BigDecimal("281.50")) > 0) {
                    pdf.text("    Hinweis: 30 % des Bruttos übersteigen die Obergrenze — der "
                            + "Freibetrag wird auf 281,50 EUR gekappt.", SweetPdf.GRAU, 9);
                }
                if (fall.absetzbetaetrag().signum() > 0) {
                    schritt(pdf, "    \u2013 Absetzbeträge: Steuern " + euro(fall.abzSteuern())
                            + " + Sozialversicherung " + euro(fall.abzSozialversicherung())
                            + " (§ 82 Abs. 2)", fall.absetzbetaetrag());
                }
                schritt(pdf, "    = anrechenbares Einkommen", f.anrechenbaresEinkommen());
                schritt(pdf, "    Zwischenergebnis Person " + (i + 1)
                        + " (Bedarf \u2013 Einkommen)", f.zahlbetrag());
            }
        }

        pdf.fettZeile("Bedarfsgemeinschaft insgesamt:", SweetPdf.SCHWARZ, 10);
        schritt(pdf, "Gesamtbedarf (Summe aller Personen)",
                block.ergebnis().gesamtbedarf());
        schritt(pdf, "\u2013 anrechenbares Einkommen aller Personen",
                block.ergebnis().anrechenbaresEinkommen());
        if (block.vermoegen().signum() > 0) {
            schritt(pdf, "\u2013 verwertbares Vermögen: Vermögen " + euro(block.vermoegen())
                            + " \u2013 Schonvermögen " + euro(block.schonvermoegen())
                            + " (10.000 EUR je erwachsener Person, § 90 Abs. 2 Nr. 9 i.V.m. "
                            + "§ 1 BarbetragsVO)",
                    block.ergebnis().verwertbaresVermoegen());
        }
        schritt(pdf, "= ZAHLBETRAG (erwartete Auszahlungssumme)", block.ergebnis().zahlbetrag());
        if (block.budget() != null) {
            schritt(pdf, "Budgetführung: Startbudget " + euro(block.budget())
                            + " \u2013 Zahlbetrag " + euro(block.ergebnis().zahlbetrag())
                            + " = erwartetes Restbudget",
                    block.budget().subtract(block.ergebnis().zahlbetrag()));
        }
        if (block.ergebnis().zahlbetrag().signum() <= 0) {
            pdf.text("Hinweis: Der Zahlbetrag ist null oder negativ — Einkommen/Vermögen "
                    + "decken den Bedarf vollständig. Die Auszahlungsanordnung muss mit dem "
                    + "Fachfehler KEIN_ANSPRUCH abgelehnt werden (§ 43 Abs. 1 SGB XII); es "
                    + "darf keine Zahlung und keine Budgetminderung entstehen.",
                    SweetPdf.BLAU, 9);
        }
        for (String hinweis : block.zusatzHinweise()) {
            pdf.text("Hinweis: " + hinweis, SweetPdf.GRAU, 9);
        }
    }

    private static void schritt(SweetPdf pdf, String beschreibung, BigDecimal wert) {
        // Umbruch statt Überlauf: lange KdU-/Einkommenszeilen passen sonst nicht auf die Seite
        int maxZeichen = 96;
        String ganz = "    " + beschreibung + " = " + euro(wert) + " EUR";
        if (ganz.length() <= maxZeichen) {
            pdf.rohZeile(ganz, SweetPdf.SCHWARZ, 9.5f);
            return;
        }
        // Wortumbruch mit hängendem Einzug: nie mitten im Wort schneiden
        String rest = ganz;
        boolean ersteZeile = true;
        while (rest.length() > maxZeichen) {
            int schnitt = rest.lastIndexOf(' ', maxZeichen);
            if (schnitt <= 0) {
                schnitt = maxZeichen;
            }
            pdf.rohZeile(ersteZeile ? rest.substring(0, schnitt) : "        " + rest.substring(0, schnitt),
                    SweetPdf.SCHWARZ, 9.5f);
            rest = rest.substring(schnitt).strip();
            ersteZeile = false;
            maxZeichen = 88;
        }
        pdf.rohZeile(ersteZeile ? rest : "        " + rest, SweetPdf.SCHWARZ, 9.5f);
    }

    private static String alleinerziehendErklaerung(SozialFall fall,
            Leistungsberechnung.FallBerechnung f) {
        int kinderU16 = Math.max(fall.anzKinderUnter16(), fall.anzKinderUnter7());
        if (fall.anzKinderUnter7() >= 1) {
            return "36 % der Stufe 1 (Kind unter 7 im Haushalt), also 0,36 × 563,00";
        }
        if (kinderU16 >= 2 && kinderU16 <= 3) {
            return "36 % der Stufe 1 (2\u20133 Kinder unter 16), also 0,36 × 563,00";
        }
        return "12 % der Stufe 1 je minderjährigem Kind (" + fall.anzKinderMinderjaehrig()
                + " × 0,12 × 563,00)";
    }

    private static String warmwasserSatz(int rbs) {
        return switch (rbs) {
            case 1, 2 -> "2,3 %";
            case 3 -> "0 % (Stufe 3 hat keinen gesetzlichen Satz)";
            case 4 -> "1,4 %";
            case 5 -> "1,2 %";
            default -> "0,8 %";
        };
    }

    private static String euro(BigDecimal betrag) {
        return String.format(Locale.GERMAN, "%,.2f", betrag == null ? BigDecimal.ZERO : betrag);
    }

    private static String statusText(Auftrag a) {
        return switch (a.status) {
            case OFFEN -> "offen (nicht bearbeitet)";
            case IN_BEARBEITUNG -> "in Bearbeitung";
            case FERTIG_GESPIELT -> "fertig gespielt (Verifikation ausstehend)";
            case FEHLGESCHLAGEN -> "ERGEBNIS: FEHLGESCHLAGEN — der Testfall brach beim Spielen ab";
            case VERIFIKATION_FEHLGESCHLAGEN ->
                    "ERGEBNIS: VERIFIKATION N.I.O. — die Datenbank weicht von der Erwartung ab";
            default -> "";
        };
    }
}
