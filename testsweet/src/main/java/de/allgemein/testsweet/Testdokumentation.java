package de.allgemein.testsweet;

import de.allgemein.lmg.calc.Leistungsberechnung;
import de.allgemein.lmg.model.SozialFall;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Automatische Testdokumentation eines Simulator-Laufs als hochprofessionelles PDF:
 * - Repräsentatives Deckblatt mit Titelgrafik, Kennzahlen-Badge und Laufmetadaten
 * - Ausführliche Einleitung mit Fachflussdiagrammen (Architektur, SGB-XII-Rechenkaskade, Vier-Augen-Workflow)
 * - Inhaltsverzeichnis mit exakter Trocken-Pagination
 * - Zusammenfassung mit Simulator-Zyklusdiagramm und KPI-Auswertung
 * - Je Einzelfall vollständiger Nachweis: Fachbeschreibung, Schritt-für-Schritt-Rechenweg,
 *   Erwartung vs. Ist (SQL-Prüfung) und chronologischer Protokollausschnitt.
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
        String zeitstempel = simulator.laufStart().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));
        inhalt.setzeKopfzeile("SDN.Leistung LMG — Automatische Testdokumentation (Simulator-Lauf " + zeitstempel + ")");
        inhalt.setzeFusszeile("testsweet — automatische Testdokumentation (" + zeitstempel + ")");

        zusammenfassung(inhalt, simulator);
        for (Auftrag auftrag : auftraege) {
            einzelfall(inhalt, auftrag);
        }
        Map<String, Integer> markenSeiten = inhalt.seitenDerMarken();

        // Kopf zweimal bauen: erst ohne Seitenzahlen (zur Offset-Bestimmung),
        // dann mit echten Zahlen — Paginierung bleibt exakt deckungsgleich.
        SweetPdf messKopf = new SweetPdf();
        messKopf.setzeKopfzeile("SDN.Leistung LMG — Automatische Testdokumentation");
        messKopf.setzeFusszeile("testsweet — automatische Testdokumentation");
        deckblatt(messKopf, simulator);
        einleitung(messKopf, simulator);
        inhaltsverzeichnis(messKopf, auftraege, null, 0);
        int offset = messKopf.seitenAnzahl();

        SweetPdf kopf = new SweetPdf();
        kopf.setzeKopfzeile("SDN.Leistung LMG — Automatische Testdokumentation");
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
    // Deckblatt mit Titelgrafik & Management-Zusammenfassung
    // ==================================================================

    private static void deckblatt(SweetPdf pdf, Simulator simulator) {
        pdf.text("SDN.Leistung · LEISTUNGSMANAGEMENT GRUNDSICHERUNG (SGB XII)", SweetPdf.DUNKELBLAU, 10.5f);
        pdf.fettZeile("Qualitäts- und Transaktionssicherung: Automatisierter Last- und Integrationstest", SweetPdf.GRAU, 8.5f);
        pdf.leereZeile(3f);

        // Titelbild einbinden (zentriert, 480 pt breit, proportional, ohne überlagernden Bildtitel)
        pdf.bildZentriert("titelbild.jpg", 480f, 0f, null);
        pdf.leereZeile(6f);

        pdf.fettZeile("Automatische Testdokumentation", SweetPdf.BLAU, 20f);
        boolean istWeb = "WEB".equalsIgnoreCase(simulator.letzterModus());
        String modusTitel = istWeb
                ? "Mitarbeiter-Simulator „testsweet“ — Modus 2: Weboberfläche & Selenium-Automatisierung"
                : "Mitarbeiter-Simulator „testsweet“ — Modus 1: Fachschicht- und Transaktionstest";
        pdf.text(modusTitel, SweetPdf.SCHWARZ, 10.5f);
        pdf.leereZeile(3f);
        pdf.linie();
        pdf.leereZeile(4f);

        pdf.paar("Simulationslauf", simulator.laufStart().format(TAG) + " bis "
                + (simulator.laufEnde() == null ? "—" : simulator.laufEnde().format(TAG))
                + " (" + simulator.laufdauerSekunden() + " s)");
        pdf.paar("Datenbank", simulator.dbUrl + " (Benutzer: " + simulator.dbUser + ")");
        if (simulator.konfiguration() != null) {
            pdf.paar("Konfiguration", simulator.konfiguration().mitarbeiter() + " simulierte "
                    + "Sachbearbeiter, " + simulator.konfiguration().auftraege()
                    + " Aufträge, Seed " + simulator.konfiguration().seed()
                    + ", Denkzeit " + simulator.konfiguration().denkzeitMs() + " ms je Schritt");
        }
        pdf.paar("Testgegenstand", "Transaktionssteuerung und Fachschicht unter Parallelität: "
                + "Sperren, Deadlock-Retry (ORA-00054/60/8177), Budget-Führung, Vier-Augen-Prinzip, "
                + "optimistisches Locking (Lost-Update-Schutz), Idempotenz.");
        pdf.leereZeile(5f);

        int verifiziert = 0;
        int nok = 0;
        int fehl = 0;
        for (Auftrag a : simulator.auftraege()) {
            verifiziert += a.status == Auftrag.Status.VERIFIZIERT ? 1 : 0;
            nok += a.status == Auftrag.Status.VERIFIKATION_FEHLGESCHLAGEN ? 1 : 0;
            fehl += a.status == Auftrag.Status.FEHLGESCHLAGEN ? 1 : 0;
        }

        String ergebnisText = "GESAMTERGEBNIS: " + verifiziert + " von " + simulator.auftraege().size()
                + " Testfällen vollständig verifiziert"
                + (nok + fehl == 0 ? " — keine Abweichungen (100 % Konformität)." : " — "
                + (nok + fehl) + " Auffälligkeit(en), siehe Zusammenfassung.");
        pdf.kasten(ergebnisText, true, SweetPdf.WEISS,
                nok + fehl == 0 ? SweetPdf.FUELL_GRUEN : SweetPdf.FUELL_ROT, 10.5f);
        pdf.leereZeile(6f);

        pdf.text("Dieses Dokument wurde vollautomatisch aus dem Simulator-Lauf erzeugt. "
                + "Es umfasst die methodische Einleitung, Rechengrundlagen 2026, Fachflussdiagramme, "
                + "die Gesamtzusammenfassung sowie jeden einzelnen Testfall mit lückenlosem "
                + "Zwischenrechenweg und SQL-Verifikation gegen den Datenbank-Endzustand.", SweetPdf.GRAU, 8.5f);
        pdf.seitenUmbruch();
    }

    // ==================================================================
    // Einleitung mit Fachflussdiagrammen
    // ==================================================================

    private static void einleitung(SweetPdf pdf, Simulator simulator) {
        // Seite 2: Einleitung & Architektur
        pdf.ueberschrift("1. Einleitung & Testverfahren");
        pdf.abschnittsTitel("Zweck dieses Dokuments");
        pdf.text("Der Mitarbeiter-Simulator „testsweet“ prüft, ob die Fachschicht des "
                + "Leistungsmanagements Grundsicherung (SDN.Leistung LMG) auch dann korrekt "
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
                + "revisionssicheren Nachweis und als Grundlage für die manuelle Fehlersuche.");
        pdf.leereZeile(3f);

        pdf.abschnittsTitel("Architektur der Transaktionssteuerung (Ersatz TP-Monitor)");
        pdf.bildZentriert("transaktionsarchitektur.jpg", 460f, 0f,
                "Abbildung 1: Architektur der Transaktionssteuerung (TaskController, TxManager, RetryPolicy, Oracle ACID)");
        pdf.leereZeile(3f);

        pdf.abschnittsTitel("Wie ein Testfall aufgebaut ist");
        pdf.text("Jeder Testfall folgt einem einheitlichen 6-stufigen Prüfmuster:");
        pdf.text("1. Ergebnisleiste — farbig: grün = vollständig verifiziert, rot = Auffälligkeit.");
        pdf.text("2. „Was wird getestet?“ — Ausgangssituation, fachlicher Hintergrund und Testziel.");
        pdf.text("3. Eckdaten — Hauptperson, bearbeitender Sachbearbeiter, Vorgangs-ID (BG-ID), Dauer.");
        pdf.text("4. Erwartete Berechnung — Schritt-für-Schritt mit jedem Zwischenwert und gesetzlicher Norm (§).");
        pdf.text("5. Erwarteter Datenbank-Endzustand — direkte SQL-Verifikation jeder Erwartung gegen den Ist-Wert.");
        pdf.text("6. Protokollausschnitt — chronologische Ausführungsschritte mit Zeitstempel und Ergebnis.");
        pdf.seitenUmbruch();

        // Seite 3: Rechengrundlagen SGB XII mit Berechnungsflussdiagramm
        pdf.ueberschrift("2. Gesetzliche Rechengrundlagen (SGB XII, Stand 2026)");
        pdf.abschnittsTitel("Berechnungskaskade für Bedarfe, Einkommen und Vermögen");
        pdf.bildZentriert("bedarfsberechnung_sgb12.jpg", 425f, 0f,
                "Abbildung 2: Gesetzliche Bedarfs- und Leistungsberechnungskaskade nach dem SGB XII (Stand 2026)");
        pdf.leereZeile(3f);

        pdf.fettZeile("Regelbedarfsstufen (Anlage zu § 28 SGB XII):", SweetPdf.BLAU, 9.5f);
        pdf.text("Stufe 1: 563,00 EUR (Alleinstehende)  •  Stufe 2: 506,00 EUR (Partner)  •  Stufe 3: 451,00 EUR (Erwachsene im Haushalt)  •  "
                + "Stufe 4: 471,00 EUR (14–17 Jahre)  •  Stufe 5: 390,00 EUR (6–13 Jahre)  •  Stufe 6: 357,00 EUR (0–5 Jahre)");
        pdf.leereZeile(2f);

        pdf.fettZeile("Mehrbedarfe (§ 30 SGB XII):", SweetPdf.BLAU, 9.5f);
        pdf.text("• Merkzeichen G: 17 % des Regelbedarfs (Abs. 1)  •  Schwangerschaft ab 13. SSW: 17 % (Abs. 2)\n"
                + "• Alleinerziehende: 36 % der Stufe 1 bei 1 Kind < 7 oder 2–3 Kindern < 16, sonst 12 % je minderjährigem Kind; Obergrenze 60 % der Stufe 1 = 337,80 EUR (Abs. 3)\n"
                + "• Obergrenze: Summe Abs. 1–5 darf Regelbedarf nicht übersteigen (Abs. 6)\n"
                + "• Dezentrales Warmwasser: 2,3 % (Stufe 1–2), 1,4 % (Stufe 4), 1,2 % (Stufe 5), 0,8 % (Stufe 6); gilt zusätzlich zur Obergrenze (Abs. 7)");
        pdf.leereZeile(2f);

        pdf.fettZeile("Kosten der Unterkunft (§ 42a SGB XII) & Einkommensbereinigung (§ 82 SGB XII):", SweetPdf.BLAU, 9.5f);
        pdf.text("• KdU: Kaltmiete + Betriebskosten + Heizung werden bedarfsgemeinschaftsweit anerkannt.\n"
                + "• Einkommen: Erwerbsfreibetrag 30 % des Brutto (max. 50 % Stufe 1 = 281,50 EUR, § 82 Abs. 3). "
                + "Erst danach wirken Absetzbeträge (Steuern, Sozialversicherung, Versicherungen, § 82 Abs. 2).\n"
                + "• Schonvermögen: 10.000 EUR je erwachsener Person, 500 EUR je unterhaltener Person (§ 90 Abs. 2 Nr. 9 i.V.m. BarbetragsVO). "
                + "Nur der Überschuss wird bedarfsmindernd angerechnet.");
        pdf.seitenUmbruch();

        // Seite 4: Vier-Augen-Prinzip & Workflow
        pdf.ueberschrift("3. Auszahlungs-Workflow & Vier-Augen-Freigabe");
        pdf.abschnittsTitel("Lebenszyklus einer Auszahlung und Revisionssicherheit");
        pdf.bildZentriert("vier_augen_workflow.jpg", 440f, 0f,
                "Abbildung 3: Vier-Augen-Freigabe, SEPA-Prüfung, Kassenlauf und Storno-Rückbuchung");
        pdf.leereZeile(3f);

        pdf.fettZeile("Vier-Augen-Prinzip und Selbstfreigabe-Abwehr:", SweetPdf.BLAU, 9.5f);
        pdf.text("Jede Auszahlung beginnt im Status ANGELEGT. Die Freigabe durch denselben Sachbearbeiter "
                + "wird serverseitig strikt abgewehrt (Fachfehler 403 / Selbstfreigabe unzulässig). Erst die "
                + "kollegiale rechnerische Feststellung durch eine zweite Fachkraft überführt die Zahlung in den "
                + "Status BEREIT. Der anschließende Kassenlauf setzt bereite Zahlungen geschlossen auf UEBERGEBEN.");
        pdf.leereZeile(2f);

        pdf.fettZeile("Budgetführung, Idempotenz und Storno-Korrektur:", SweetPdf.BLAU, 9.5f);
        pdf.text("• Budget: Jede Anordnung mindert das Restbudget der Bedarfsgemeinschaft unter Datenbanksperre (SELECT … FOR UPDATE).\n"
                + "• Idempotenz: Eindeutige Vorgangsnummern verhindern Doppelbuchungen bei versehentlichem Doppelklick.\n"
                + "• Storno: Bei Korrekturbedarf bucht der Storno das geminderte Budget vollständig zurück und gibt den Abrechnungsmonat wieder frei.");
        pdf.seitenUmbruch();
    }

    // ==================================================================
    // Inhaltsverzeichnis
    // ==================================================================

    private static void inhaltsverzeichnis(SweetPdf pdf, List<Auftrag> auftraege,
            Map<String, Integer> markenSeiten, int offset) {
        pdf.ueberschrift("Inhaltsverzeichnis");
        pdf.leereZeile(3f);
        pdf.tocZeile("1. Einleitung & Testverfahren (Architektur)", 2, SweetPdf.SCHWARZ);
        pdf.tocZeile("2. Gesetzliche Rechengrundlagen 2026 (Berechnungskaskade)", 3, SweetPdf.SCHWARZ);
        pdf.tocZeile("3. Auszahlungs-Workflow & Vier-Augen-Freigabe", 4, SweetPdf.SCHWARZ);
        pdf.tocZeile("4. Inhaltsverzeichnis", 5, SweetPdf.SCHWARZ);
        pdf.tocZeile("5. Zusammenfassung & Ergebnis in Zahlen", offset, SweetPdf.SCHWARZ);
        for (Auftrag a : auftraege) {
            Integer seite = markenSeiten == null ? null : markenSeiten.get("auftrag-" + a.id);
            pdf.tocZeile("Testfall " + String.format("%02d", a.id) + " — " + a.szenarioTyp
                            + " (" + a.aktenzeichen + ")",
                    seite == null ? 0 : offset + seite - 1,
                    a.status == Auftrag.Status.VERIFIZIERT ? SweetPdf.SCHWARZ : SweetPdf.ROT);
        }
        pdf.leereZeile(4f);
        pdf.text("Rote Einträge: Testfall mit Abweichung oder beim Spielen fehlgeschlagen.", SweetPdf.GRAU, 8.5f);
        pdf.seitenUmbruch();
    }

    // ==================================================================
    // Zusammenfassung
    // ==================================================================

    private static void zusammenfassung(SweetPdf pdf, Simulator simulator) {
        List<Auftrag> auftraege = simulator.auftraege();
        pdf.ueberschrift("Zusammenfassung der Testergebnisse");
        boolean istWeb = "WEB".equalsIgnoreCase(simulator.letzterModus());
        pdf.paar("Modus", istWeb
                ? "2 — Weboberfläche & Selenium (Interaktive Browsersteuerung der SDN.Leistung-Oberfläche)"
                : "1 — Fachschicht/Transaktionstest (Aufrufe direkt über den SozialLeistungsService der Fachschicht)");
        pdf.paar("Ablauf", istWeb
                ? "Die simulierten Sachbearbeiter bedienen parallele Chrome-Fenster über die Weboberfläche; "
                + "nach dem Lauf wird der Endzustand per SQL direkt gegen die Datenbank verifiziert."
                : "Die simulierten Sachbearbeiter nehmen nacheinander Aufträge aus einem gemeinsamen Pool und "
                + "spielen sie parallel; nach dem Lauf wird die Erwartungshaltung per SQL direkt gegen die Datenbank geprüft.");
        pdf.leereZeile(3f);

        pdf.abschnittsTitel("Ablauf und Verifikationszyklus des Simulators");
        pdf.bildZentriert("simulator_zyklus.jpg", 435f, 0f,
                "Abbildung 4: Ablauf und Verifikationszyklus des Mitarbeiter-Simulators testsweet");
        pdf.leereZeile(3f);

        pdf.abschnittsTitel("Ergebnis in Zahlen");
        int verifiziert = 0;
        int verifikationNok = 0;
        int fehlgeschlagen = 0;
        for (Auftrag a : auftraege) {
            verifiziert += a.status == Auftrag.Status.VERIFIZIERT ? 1 : 0;
            verifikationNok += a.status == Auftrag.Status.VERIFIKATION_FEHLGESCHLAGEN ? 1 : 0;
            fehlgeschlagen += a.status == Auftrag.Status.FEHLGESCHLAGEN ? 1 : 0;
        }
        pdf.text("Aufträge gesamt: " + auftraege.size(), SweetPdf.SCHWARZ, 9.5f);
        pdf.text("Vollständig verifiziert (Datenbank-Endzustand exakt wie erwartet): "
                + verifiziert, verifiziert == auftraege.size() ? SweetPdf.GRUEN : SweetPdf.BLAU, 9.5f);
        pdf.text("Verifikation mit Abweichungen: " + verifikationNok,
                verifikationNok == 0 ? SweetPdf.GRUEN : SweetPdf.ROT, 9.5f);
        pdf.text("Beim Spielen fehlgeschlagen: " + fehlgeschlagen,
                fehlgeschlagen == 0 ? SweetPdf.GRUEN : SweetPdf.ROT, 9.5f);
        pdf.leereZeile(3f);

        pdf.abschnittsTitel("Verteilung der Testfallarten");
        Map<String, Integer> verteilung = new java.util.LinkedHashMap<>();
        for (Auftrag a : auftraege) {
            verteilung.merge(a.szenarioTyp, 1, Integer::sum);
        }
        StringBuilder zeile = new StringBuilder();
        for (Map.Entry<String, Integer> e : verteilung.entrySet()) {
            zeile.append(e.getKey()).append(" x").append(e.getValue()).append("   ");
        }
        pdf.text(zeile.toString().trim(), SweetPdf.SCHWARZ, 9.5f);

        if (verifikationNok > 0 || fehlgeschlagen > 0) {
            pdf.leereZeile(3f);
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
                            SweetPdf.ROT, 9.0f);
                }
            }
        }
    }

    // ==================================================================
    // Einzelfall
    // ==================================================================

    private static void einzelfall(SweetPdf pdf, Auftrag a) {
        pdf.seitenUmbruch();
        pdf.marke("auftrag-" + a.id);
        pdf.kasten("TESTFALL " + String.format("%02d", a.id) + " — " + a.szenarioTyp
                + "   (" + a.aktenzeichen + ")", true, SweetPdf.WEISS, SweetPdf.FUELL_BLAU, 12f);
        pdf.kasten(a.status == Auftrag.Status.VERIFIZIERT
                ? "ERGEBNIS: VERIFIZIERT — Datenbank-Endzustand vollständig wie erwartet"
                : statusText(a).replace("ERGEBNIS: ", ""), true, SweetPdf.WEISS,
                a.status == Auftrag.Status.VERIFIZIERT ? SweetPdf.FUELL_GRUEN : SweetPdf.FUELL_ROT,
                10f);

        pdf.kasten("Was wird getestet? (einfach beschrieben)", true, SweetPdf.BLAU,
                SweetPdf.FUELL_HELLBLAU, 10f);
        pdf.text(a.beschreibungEinfach == null || a.beschreibungEinfach.isBlank()
                ? a.beschreibung : a.beschreibungEinfach, SweetPdf.SCHWARZ, 9.2f);

        pdf.kasten("Eckdaten des Testfalls", true, SweetPdf.BLAU, SweetPdf.FUELL_HELLBLAU, 10f);
        pdf.paar("Fachlicher Ablauf", a.beschreibung);
        pdf.paar("Hauptperson", a.hauptperson);
        pdf.paar("Bearbeitet von", a.bearbeiter != null ? a.bearbeiter : "—");
        pdf.paar("Vorgang (BG-ID)", a.bgId != null ? String.valueOf(a.bgId) : "—");
        pdf.paar("Bearbeitungsdauer", a.dauerMs() + " ms");
        pdf.paar("Erwartete Dauer des Falls",
                (a.webAktionen.isEmpty() ? a.aktionen.size() : a.webAktionen.size())
                        + " Arbeitsschritte (Modus: "
                        + (a.webAktionen.isEmpty() ? "Fachschicht" : "Weboberfläche") + ")");

        // ------------------------------------------------------------
        // Rechenweg
        // ------------------------------------------------------------
        if (!a.rechenbloecke.isEmpty()) {
            pdf.kasten("Erwartete Berechnung — Schritt für Schritt nachrechenbar", true,
                    SweetPdf.BLAU, SweetPdf.FUELL_HELLBLAU, 10f);
            for (Rechenblock block : a.rechenbloecke) {
                pdf.fettZeile(block.titel(), SweetPdf.BLAU, 9.8f);
                rechneBlock(pdf, block);
                pdf.leereZeile(2f);
            }
        }

        // Bei sehr umfangreichen Fällen (mehrere Rechenblöcke oder viele Schritte)
        // gezielt vor dem Datenbank-Nachweis umbrechen, um verwaiste Endzeilen zu verhindern
        if (a.rechenbloecke.size() > 1 || a.schritte().size() > 5) {
            pdf.seitenUmbruch();
            pdf.fettZeile("TESTFALL " + String.format("%02d", a.id) + " — " + a.szenarioTyp
                    + " (Fortsetzung: Nachweis & Ablauf)", SweetPdf.BLAU, 10.5f);
            pdf.linie();
        }

        // ------------------------------------------------------------
        // Erwartung vs. Ist
        // ------------------------------------------------------------
        pdf.kasten("Erwarteter Datenbank-Endzustand und Nachweis", true, SweetPdf.BLAU,
                SweetPdf.FUELL_HELLBLAU, 10f);
        if (a.pruefungen().isEmpty()) {
            pdf.text("Keine Verifikation durchgeführt"
                    + (a.status == Auftrag.Status.FEHLGESCHLAGEN
                    ? " — der Testfall scheiterte bereits beim Spielen." : "."),
                    SweetPdf.GRAU, 9.0f);
        }
        for (Auftrag.Pruefung p : a.pruefungen()) {
            pdf.text((p.ok() ? "[OK]     " : "[n.i.O.] ") + p.beschreibung(),
                    p.ok() ? SweetPdf.GRUEN : SweetPdf.ROT, 9.0f);
            pdf.text("         erwartet: " + p.erwartet() + "   |   Ist (DB): " + p.ist(),
                    SweetPdf.GRAU, 8.5f);
        }

        if (a.fehlerText != null) {
            pdf.kasten("Fehler", true, SweetPdf.WEISS, SweetPdf.FUELL_ROT, 10f);
            pdf.text(a.fehlerText, SweetPdf.ROT, 9.0f);
        }

        pdf.kasten("Protokollausschnitt (Ablauf des Testfalls)", true, SweetPdf.BLAU,
                SweetPdf.FUELL_HELLBLAU, 10f);
        for (Auftrag.Schritt s : a.schritte()) {
            pdf.text(s.zeit().format(ZEIT) + "  " + s.mitarbeiter() + ": " + s.aktion(),
                    SweetPdf.SCHWARZ, 8.5f);
            String ergebnis = s.ergebnis();
            int ergebnisFarbe = ergebnis.startsWith("FEHLGESCHLAGEN") ? SweetPdf.ROT
                    : ergebnis.startsWith("ERWARTETER FEHLER") ? SweetPdf.BLAU
                    : ergebnis.startsWith("ABGEBROCHEN") ? SweetPdf.ROT
                    : SweetPdf.GRUEN;
            pdf.text("    -> " + ergebnis, ergebnisFarbe, 8.5f);
        }
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
                    ? " (mit Mehrbedarfsmerkmalen)" : ""), SweetPdf.SCHWARZ, 9.5f);
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
                pdf.text("Hinweis: Die Summe der Zuschläge nach § 30 Abs. 1–5 erreicht die "
                        + "Obergrenze des § 30 Abs. 6 (höchstens der Regelbedarf).",
                        SweetPdf.GRAU, 8.5f);
            }
            schritt(pdf, "+ Kosten der Unterkunft: Miete " + euro(fall.kduMiete())
                    + " + Betriebskosten " + euro(fall.kduBetriebskosten())
                    + " + Heizung " + euro(fall.kduHeizung())
                    + " (§ 42a SGB XII; Angemessenheitsprüfung deaktiviert)", f.kdu());
            schritt(pdf, "= Gesamtbedarf Person " + (i + 1), f.gesamtbedarf());

            if (fall.einkommenRente().signum() > 0 || fall.einkommenErwerb().signum() > 0
                    || fall.absetzbetaetrag().signum() > 0) {
                pdf.rohZeile("    Einkommensberechnung (§ 82 SGB XII):", SweetPdf.SCHWARZ, 9.0f);
                schritt(pdf, "    Einnahmen: Rente " + euro(fall.einkommenRente())
                        + " + Erwerbseinkommen " + euro(fall.einkommenErwerb()),
                        fall.einkommenRente().add(fall.einkommenErwerb()));
                schritt(pdf, "    – Erwerbsfreibetrag: 30 % von "
                        + euro(fall.einkommenErwerb()) + " = "
                        + euro(fall.einkommenErwerb().multiply(new BigDecimal("0.30"))
                                .setScale(2, java.math.RoundingMode.HALF_UP))
                        + ", höchstens 281,50 (= 50 % der Stufe 1) (§ 82 Abs. 3)",
                        f.freibetragErwerb());
                if (f.freibetragErwerb().compareTo(new BigDecimal("281.50")) == 0
                        && fall.einkommenErwerb().multiply(new BigDecimal("0.30"))
                        .compareTo(new BigDecimal("281.50")) > 0) {
                    pdf.text("    Hinweis: 30 % des Bruttos übersteigen die Obergrenze — der "
                            + "Freibetrag wird auf 281,50 EUR gekappt.", SweetPdf.GRAU, 8.5f);
                }
                if (fall.absetzbetaetrag().signum() > 0) {
                    schritt(pdf, "    – Absetzbeträge: Steuern " + euro(fall.abzSteuern())
                            + " + Sozialversicherung " + euro(fall.abzSozialversicherung())
                            + " (§ 82 Abs. 2)", fall.absetzbetaetrag());
                }
                schritt(pdf, "    = anrechenbares Einkommen", f.anrechenbaresEinkommen());
                schritt(pdf, "    Zwischenergebnis Person " + (i + 1)
                        + " (Bedarf – Einkommen)", f.zahlbetrag());
            }
        }

        pdf.fettZeile("Bedarfsgemeinschaft insgesamt:", SweetPdf.SCHWARZ, 9.5f);
        schritt(pdf, "Gesamtbedarf (Summe aller Personen)",
                block.ergebnis().gesamtbedarf());
        schritt(pdf, "– anrechenbares Einkommen aller Personen",
                block.ergebnis().anrechenbaresEinkommen());
        if (block.vermoegen().signum() > 0) {
            schritt(pdf, "– verwertbares Vermögen: Vermögen " + euro(block.vermoegen())
                            + " – Schonvermögen " + euro(block.schonvermoegen())
                            + " (10.000 EUR je erwachsener Person, § 90 Abs. 2 Nr. 9 i.V.m. "
                            + "§ 1 BarbetragsVO)",
                    block.ergebnis().verwertbaresVermoegen());
        }
        schritt(pdf, "= ZAHLBETRAG (erwartete Auszahlungssumme)", block.ergebnis().zahlbetrag());
        if (block.budget() != null) {
            schritt(pdf, "Budgetführung: Startbudget " + euro(block.budget())
                            + " – Zahlbetrag " + euro(block.ergebnis().zahlbetrag())
                            + " = erwartetes Restbudget",
                    block.budget().subtract(block.ergebnis().zahlbetrag()));
        }
        if (block.ergebnis().zahlbetrag().signum() <= 0) {
            pdf.text("Hinweis: Der Zahlbetrag ist null oder negativ — Einkommen/Vermögen "
                    + "decken den Bedarf vollständig. Die Auszahlungsanordnung muss mit dem "
                    + "Fachfehler KEIN_ANSPRUCH abgelehnt werden (§ 43 Abs. 1 SGB XII); es "
                    + "darf keine Zahlung und keine Budgetminderung entstehen.",
                    SweetPdf.BLAU, 8.5f);
        }
        for (String hinweis : block.zusatzHinweise()) {
            pdf.text("Hinweis: " + hinweis, SweetPdf.GRAU, 8.5f);
        }
    }

    private static void schritt(SweetPdf pdf, String beschreibung, BigDecimal wert) {
        int maxZeichen = 96;
        String ganz = "    " + beschreibung + " = " + euro(wert) + " EUR";
        if (ganz.length() <= maxZeichen) {
            pdf.rohZeile(ganz, SweetPdf.SCHWARZ, 9.0f);
            return;
        }
        String rest = ganz;
        boolean ersteZeile = true;
        while (rest.length() > maxZeichen) {
            int schnitt = rest.lastIndexOf(' ', maxZeichen);
            if (schnitt <= 0) {
                schnitt = maxZeichen;
            }
            pdf.rohZeile(ersteZeile ? rest.substring(0, schnitt) : "        " + rest.substring(0, schnitt),
                    SweetPdf.SCHWARZ, 9.0f);
            rest = rest.substring(schnitt).strip();
            ersteZeile = false;
            maxZeichen = 88;
        }
        pdf.rohZeile(ersteZeile ? rest : "        " + rest, SweetPdf.SCHWARZ, 9.0f);
    }

    private static String alleinerziehendErklaerung(SozialFall fall,
            Leistungsberechnung.FallBerechnung f) {
        int kinderU16 = Math.max(fall.anzKinderUnter16(), fall.anzKinderUnter7());
        if (fall.anzKinderUnter7() >= 1) {
            return "36 % der Stufe 1 (Kind unter 7 im Haushalt), also 0,36 × 563,00";
        }
        if (kinderU16 >= 2 && kinderU16 <= 3) {
            return "36 % der Stufe 1 (2–3 Kinder unter 16), also 0,36 × 563,00";
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
