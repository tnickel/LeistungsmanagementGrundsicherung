package de.allgemein.testsweet;

import java.io.IOException;
import java.nio.file.Path;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Gesamtreport für LAST-Läufe — bewusst GEKÜRZT: Statt einer Einzelfall-Seite
 * je Testfall (bei 600+ Fällen würde die Dokumentation ausufern) enthält der
 * Bericht nur Aggregate: Kennzahlen, Dauer-Statistik, Verteilungen je
 * Szenario-Typ und je Sachbearbeiter. Die Einzelnachweise je Auftrag stehen
 * vollständig in den Protokolldateien des Lauf-Verzeichnisses
 * (simulation.log, auftrag-*.log, db-verifikation.log).
 */
final class LastBericht {

    private static final DateTimeFormatter TAG =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private LastBericht() {
    }

    static void schreibe(Path pdf, Path md, Simulator simulator) throws IOException {
        schreibePdf(pdf, simulator);
        schreibeMd(md, simulator);
    }

    // ------------------------------------------------------------------
    // Aggregate (gleiche Basis für PDF und MD)
    // ------------------------------------------------------------------

    record Aggregate(int gesamt, int verifiziert, int verifikationNio, int fehlgeschlagen,
                     BigDecimal summe, long dauerMinMs, long dauerAvgMs, long dauerMaxMs,
                     int abgeschlossen, long dauerP50Ms, long dauerP95Ms, long dauerP99Ms,
                     Map<String, TypZeile> jeTyp, Map<String, MaZeile> jeMitarbeiter) { }

    record TypZeile(int anzahl, int verifiziert, int nio, int fehlgeschlagen, long avgMs) { }

    record MaZeile(int erledigt, long avgMs) { }

    static Aggregate aggregate(Simulator simulator) {
        List<Auftrag> auftraege = simulator.auftraege();
        int verifiziert = 0;
        int nio = 0;
        int fehl = 0;
        BigDecimal summe = BigDecimal.ZERO;
        long minMs = Long.MAX_VALUE;
        long maxMs = 0;
        long sumMs = 0;
        int gezahlt = 0;
        Map<String, List<Auftrag>> jeTyp = auftraege.stream()
                .collect(Collectors.groupingBy(a -> a.szenarioTyp, LinkedHashMap::new,
                        Collectors.toList()));
        Map<String, List<Auftrag>> jeMa = auftraege.stream()
                .filter(a -> a.bearbeiter != null)
                .collect(Collectors.groupingBy(a -> a.bearbeiter, LinkedHashMap::new,
                        Collectors.toList()));
        for (Auftrag a : auftraege) {
            verifiziert += a.status == Auftrag.Status.VERIFIZIERT ? 1 : 0;
            nio += a.status == Auftrag.Status.VERIFIKATION_FEHLGESCHLAGEN ? 1 : 0;
            fehl += a.status == Auftrag.Status.FEHLGESCHLAGEN ? 1 : 0;
            if (a.status == Auftrag.Status.VERIFIZIERT) summe = summe.add(a.pruefungen().stream()
                    .filter(p -> p.beschreibung().startsWith("Auszahlungsbetrag"))
                    .filter(p -> p.ok())
                    .map(p -> new BigDecimal(p.ist()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add));
            long ms = a.dauerMs();
            if (a.status != Auftrag.Status.OFFEN && a.status != Auftrag.Status.IN_BEARBEITUNG) {
                minMs = Math.min(minMs, ms);
                maxMs = Math.max(maxMs, ms);
                sumMs += ms;
                gezahlt++;
            }
        }
        Map<String, TypZeile> typZeilen = new LinkedHashMap<>();
        jeTyp.forEach((typ, liste) -> typZeilen.put(typ, new TypZeile(liste.size(),
                (int) liste.stream().filter(a -> a.status == Auftrag.Status.VERIFIZIERT).count(),
                (int) liste.stream().filter(a -> a.status == Auftrag.Status.VERIFIKATION_FEHLGESCHLAGEN).count(),
                (int) liste.stream().filter(a -> a.status == Auftrag.Status.FEHLGESCHLAGEN).count(),
                (long) liste.stream().filter(a -> a.status != Auftrag.Status.OFFEN && a.status != Auftrag.Status.IN_BEARBEITUNG).mapToLong(Auftrag::dauerMs).average().orElse(0))));
        Map<String, MaZeile> maZeilen = new LinkedHashMap<>();
        jeMa.forEach((kuerzel, liste) -> maZeilen.put(kuerzel, new MaZeile((int)liste.stream().filter(a -> a.status != Auftrag.Status.OFFEN && a.status != Auftrag.Status.IN_BEARBEITUNG).count(),
                (long) liste.stream().filter(a -> a.status != Auftrag.Status.OFFEN && a.status != Auftrag.Status.IN_BEARBEITUNG).mapToLong(Auftrag::dauerMs).average().orElse(0))));
        long[] durations = auftraege.stream().filter(a -> a.endeNano != 0 && a.startNano != 0)
                .mapToLong(Auftrag::dauerMs).toArray();
        return new Aggregate(auftraege.size(), verifiziert, nio, fehl,
                summe.setScale(2),
                gezahlt > 0 ? minMs : 0, gezahlt > 0 ? sumMs / gezahlt : 0, maxMs,
                durations.length, RunMetrics.percentile(durations, 50),
                RunMetrics.percentile(durations, 95), RunMetrics.percentile(durations, 99),
                typZeilen, maZeilen);
    }

    // ------------------------------------------------------------------
    // PDF: Gesamtreport (1–2 Seiten, ohne Einzelfälle)
    // ------------------------------------------------------------------

    private static void schreibePdf(Path ziel, Simulator simulator) throws IOException {
        Aggregate agg = aggregate(simulator);
        SweetPdf inhalt = new SweetPdf();
        String zeitstempel = simulator.laufStart().format(TAG);
        inhalt.fettZeile("Automatischer Last-/Stresslauf erfolgreich: " + simulator.laufErfolgreich(),
                simulator.laufErfolgreich() ? SweetPdf.GRUEN : SweetPdf.ROT, 10f);
        inhalt.text(RunMetrics.SCOPE, SweetPdf.GRAU, 9f);
        for (Auftrag.Pruefung pruefung : simulator.globalePruefungen()) {
            inhalt.text(pruefung.beschreibung() + ": erwartet " + pruefung.erwartet() + ", ist "
                    + pruefung.ist() + (pruefung.hinweis() == null ? "" : " — " + pruefung.hinweis()),
                    pruefung.ok() ? SweetPdf.GRUEN : SweetPdf.ROT, 9f);
        }

        inhalt.setzeKopfzeile("SDN.Leistung LMG — Lasttest-Gesamtreport (Lauf " + zeitstempel + ")");
        inhalt.setzeFusszeile("testsweet — Lasttest-Gesamtreport (" + zeitstempel + ")");

        inhalt.text("SDN.Leistung · LEISTUNGSMANAGEMENT GRUNDSICHERUNG (SGB XII)",
                SweetPdf.DUNKELBLAU, 10.5f);
        boolean stress = simulator.letzteKategorie() == Simulator.Kategorie.STRESS;
        inhalt.fettZeile((stress ? "STRESSTEST" : "LASTTEST")
                        + " — Gesamtreport (gekürzte Dokumentation ohne Einzelfälle)",
                SweetPdf.GRAU, 9f);
        inhalt.leereZeile(4f);
        inhalt.fettZeile(stress
                ? "Stresstest: zwei Wellen über dieselben User"
                : "Lasttest gegen Transaktionssteuerung und Datenbank",
                SweetPdf.BLAU, 17f);
        if (stress) {
            inhalt.text("Zweck dieses Stresstests ist die Funktionsprüfung der "
                    + "Transaktionssteuerung unter realistischer Massenarbeit: Nicht ein "
                    + "Einzelvorgang wird probiert, sondern dieselben " + (agg.gesamt() / 2) + " User werden von "
                    + simulator.konfiguration().mitarbeiter() + " parallelen Sachbearbeiter-Strängen zuerst angelegt und — in einer "
                    + "zweiten, scharf getrennten Welle — wieder aufgenommen und verändert. "
                    + "Je User entstehen so zwei vollwertige Transaktionsblöcke, insgesamt "
                    + agg.gesamt() + ". Jeder User gehört einem eigenen Vorgang; eine konkurrierende "
                    + "Bearbeitung desselben Vorgangs wird hier nicht erzwungen.", SweetPdf.SCHWARZ, 9.5f);
            inhalt.leereZeile(2f);
            inhalt.fettZeile("Welle 1 — STRESS-ANLEGEN (" + (agg.gesamt() / 2) + " User)", SweetPdf.DUNKELBLAU, 10f);
            inhalt.text("Für jeden User legt ein simulierter Sachbearbeiter den Vorgang mit "
                    + "Aktenzeichen, Startbudget und SEPA-Bankverbindung an und erfasst den "
                    + "SozialFall mit Regelbedarfsstufe und Wohnkosten (Kaltmiete, "
                    + "Betriebskosten, Heizung). Jeder Schritt ist eine eigene kurze "
                    + "Transaktion über den TxManager: Fachschritte laufen in eigenen "
                    + "Transaktionen; die SQL-Endverifikation prüft die Endzustände der "
                    + "erfolgreichen Aufträge. Bewusst wird noch KEINE Zahlung angeordnet — die "
                    + "angelegten Daten stehen als unveränderter Ausgangszustand für die "
                    + "zweite Welle bereit.", SweetPdf.SCHWARZ, 9.5f);
            inhalt.leereZeile(2f);
            inhalt.fettZeile("Phasenbarriere — saubere Trennung der Wellen", SweetPdf.DUNKELBLAU, 10f);
            inhalt.text("Zwischen den Wellen wacht eine Barriere im Simulator: Die "
                    + "Nachbearbeitung eines Users startet erst, wenn der letzte Anlage-"
                    + "Auftrag beendet ist. Ob die Anlage erfolgreich war, zeigen die "
                    + "Einzelergebnisse; eine fehlgeschlagene Anlage wird nicht durch die Barriere repariert. "
                    + "Die regulär angelegten Daten sind dann persistent — genau wie eine Behörde am Folgetag "
                    + "Akten wieder vorlegt, die am Vortag abgeschlossen wurden. Ohne diese "
                    + "Barriere könnte eine Nachbearbeitung einen noch nicht existierenden "
                    + "User treffen; die Trennung macht den Ablauf reproduzierbar und die "
                    + "Erwartungen eindeutig.", SweetPdf.SCHWARZ, 9.5f);
            inhalt.leereZeile(2f);
            inhalt.fettZeile("Welle 2 — STRESS-BEARBEITEN (" + (agg.gesamt() / 2) + " Nacharbeiten)", SweetPdf.DUNKELBLAU, 10f);
            inhalt.text("Jeder User wird über sein eindeutiges Aktenzeichen wieder gefunden "
                    + "(indizierter Zugriff auf die Vorgangstabelle). Dann erhöht der "
                    + "Sachbearbeiter die Miete: Das optimistische Locking über die VERSION-"
                    + "Spalte muss jeden Fall exakt von Version 1 auf Version 2 heben — wäre "
                    + "zeitgleich eine zweite Kraft mit veraltetem Maskenstand am Werk, "
                    + "würde die Fachschicht das Speichern mit dem Fachfehler KONKURRENZ "
                    + "abwehren (Lost-Update-Schutz). Anschließend ordnet er die monatliche "
                    + "Auszahlung an: Der Bedarf wird mit der NEUEN Miete neu berechnet, das "
                    + "verfügbare Restbudget unter Zeilensperre geprüft und gemindert, und "
                    + "die Zahlung landet — Vier-Augen-Prinzip — in der Warteschlange mit "
                    + "Status ANGELEGT.", SweetPdf.SCHWARZ, 9.5f);
            inhalt.leereZeile(2f);
            inhalt.fettZeile("Was hier unter Stress bewiesen wird", SweetPdf.DUNKELBLAU, 10f);
            inhalt.text("Die Worker verarbeiten verschiedene Vorgänge parallel. "
                    + "Einzelzeiten allein belegen weder Sperrwartezeiten noch Deadlocks; "
                    + "hierfür wären gezielte Konkurrenztests und entsprechende Messdaten nötig. "
                    + "Geprüft wird danach je Auftrag der Datenbank-Endzustand per "
                    + "SQL: User existiert dauerhaft, Fall exakt in Version 2, Auszahlung mit "
                    + "dem Betrag nach neuer Miete, Restbudget korrekt gemindert. Dieser "
                    + "Bericht fasst die Einzelergebnisse bewusst nur als Aggregate zusammen "
                    + "(Kennzahlen, Durchsatz, Dauer je Auftrag, Verteilung je Szenario-Typ "
                    + "und Sachbearbeiter); die vollständigen Nachweise je Auftrag stehen in "
                    + "den Protokolldateien des Lauf-Verzeichnisses.", SweetPdf.SCHWARZ, 9.5f);
            inhalt.leereZeile(4f);
            inhalt.bildZentriert("stresstest_zwei_wellen.png", 470f);
        } else {
            inhalt.text("Die Kernfrage des Lasttests lautet: Funktioniert das System auch "
                    + "dann, wenn nicht eine einzige Sachbearbeiterin einen einzigen Antrag "
                    + "bearbeitet, sondern viele Kräfte gleichzeitig Hunderte von Fällen? "
                    + "Vergleichbar ist das mit einem Belastungstag im Amt: Alle Schalter "
                    + "sind offen, alle bedienen gleichzeitig — und am Ende muss trotzdem "
                    + "jede einzelne Mappe vollständig und korrekt sein. Genau diese "
                    + "Sicherheit stellt der Lasttest unter Beweis.", SweetPdf.SCHWARZ, 9.5f);
            inhalt.leereZeile(2f);
            inhalt.fettZeile("Was der Simulator in jedem Testfall tut", SweetPdf.DUNKELBLAU, 10f);
            inhalt.text("Jeder Testfall ist der komplette Alltagsschritt eines Falls, "
                    + "durchgespielt von einem simulierten Sachbearbeiter: Er legt einen "
                    + "neuen Vorgang an (Aktenzeichen, Startbudget, Bankverbindung), erfasst "
                    + "die Person als SozialFall (Regelbedarfsstufe und Wohnkosten wie "
                    + "Kaltmiete, Betriebskosten und Heizung) und ordnet die monatliche "
                    + "Auszahlung an. Jeder dieser Schritte ist eine eigene kleine Buchung "
                    + "— eine Transaktion: Sie gelingt vollständig, oder sie wird "
                    + "vollständig zurückgenommen, wie ein Formular, das man bei einem "
                    + "Fehler komplett neu ausfüllt, statt es halb ausgefüllt abzuheften. "
                    + "Ein späterer fehlgeschlagener Fachschritt darf frühere, bereits "
                    + "committete Schritte bestehen lassen; die Laufprüfung meldet diesen "
                    + "Auftrag als fehlgeschlagen.", SweetPdf.SCHWARZ, 9.5f);
            inhalt.leereZeile(2f);
            inhalt.fettZeile("Warum die Fälle bewusst schlank sind", SweetPdf.DUNKELBLAU, 10f);
            inhalt.text("Die Durchläufe verzichten absichtlich auf Freigabe, Storno und "
                    + "Bescheid — nicht, weil das unwichtig wäre (dafür gibt es die "
                    + "Positiv- und Negativtestfälle mit eigenen Prüfberichten), sondern "
                    + "damit in kurzer Zeit möglichst viele Buchungen durch System und "
                    + "Datenbank laufen. Der Lasttest misst die Masse, nicht die Vielfalt: "
                    + "Die gemessenen Zeiten dieses Laufs stehen in den Kennzahlen; sie "
                    + "umfassen auch Simulator-, Protokoll- und gegebenenfalls Browserarbeit.", SweetPdf.SCHWARZ, 9.5f);
            inhalt.leereZeile(2f);
            inhalt.fettZeile("Was dabei unter Belastung steht", SweetPdf.DUNKELBLAU, 10f);
            inhalt.text(simulator.konfiguration().mitarbeiter() + " Arbeitsstränge greifen gleichzeitig auf dieselbe Datenbank "
                    + "zu. Bildlich muss sich das System wie ein gut organisiertes Amt "
                    + "verhalten: Wenn zwei Kräfte zeitgleich denselben Topf anfassen "
                    + "wollen, muss die Transaktionssteuerung Konkurrenz behandeln. "
                    + "Dieser Lasttopf bearbeitet verschiedene Vorgänge und beweist solche "
                    + "Konfliktfälle daher nicht. Zusätzlich schreibt die Steuerung für jede Buchung "
                    + "Verwaltungsdaten mit (eindeutige Nummern, Versionsschutz gegen "
                    + "verlorene Änderungen, ein revisionssicheres Journal). All das "
                    + "kostet Zeit — und genau diese Zeit messen die Kennzahlen dieses "
                    + "Berichts.", SweetPdf.SCHWARZ, 9.5f);
            inhalt.leereZeile(2f);
            inhalt.fettZeile("Wie das Ergebnis geprüft wird", SweetPdf.DUNKELBLAU, 10f);
            inhalt.text("Nach dem Lauf prüft ein automatischer Nachweis für JEDEN Fall "
                    + "direkt in der Datenbank: Stimmt der Auszahlungsbetrag auf den Cent "
                    + "mit der Berechnung? Wurde das verfügbare Budget genau um diesen "
                    + "Betrag gemindert — nicht mehr, nicht weniger? Steht der SozialFall "
                    + "mit der richtigen Regelbedarfsstufe? Erst wenn alle Fragen eines "
                    + "Falls mit Ja beantwortet sind, gilt er als verifiziert. Dieser "
                    + "Bericht fasst die Einzelergebnisse bewusst zusammen (wie viele "
                    + "Fälle stimmten, wie schnell war das System); die vollständigen "
                    + "Nachweise je Fall stehen in den Protokolldateien des Lauf-"
                    + "Verzeichnisses. Bestanden hat der Lasttest, wenn weder beim Spielen "
                    + "noch bei der Prüfung auch nur ein einziger Fall fehlschlägt.",
                    SweetPdf.SCHWARZ, 9.5f);
        }
        inhalt.leereZeile(4f);
        inhalt.linie();

        inhalt.ueberschrift("1. Lauf");
        inhalt.paar("Zeitraum", simulator.laufStart().format(TAG) + " bis "
                + (simulator.laufEnde() == null ? "—" : simulator.laufEnde().format(TAG))
                + " (" + simulator.laufdauerMillis() + " ms)");
        inhalt.paar("Datennamensraum", simulator.laufDatenPraefix());
        inhalt.paar("Datenbank", simulator.dbUrl + " (Benutzer: " + simulator.dbUser + ")");
        if (simulator.konfiguration() != null) {
            inhalt.paar("Konfiguration", simulator.konfiguration().mitarbeiter()
                    + " Sachbearbeiter, " + simulator.konfiguration().auftraege()
                    + " Aufträge, Seed " + simulator.konfiguration().seed()
                    + ", Denkzeit " + simulator.konfiguration().denkzeitMs() + " ms");
        }
        inhalt.paar("Modus", "WEB".equalsIgnoreCase(simulator.letzterModus())
                ? "Weboberfläche (Selenium)" : "Fachschicht (Transaktionstest)");
        inhalt.paar("Vier-Augen-Prüfumfang", simulator.vierAugenPruefumfang());
        double durchsatz = RunMetrics.perMinute(agg.abgeschlossen(), simulator.laufdauerMillis());
        inhalt.paar("Durchsatz des Gesamtlaufs", String.format("%.1f abgeschlossene Aufträge je Minute", durchsatz));
        inhalt.text("Messfenster: Laufstart bis Ende der SQL-Verifikation, einschließlich Vorbereitung; "
                + "keine isolierte Servicekapazität. Auftragsdauer: Worker-Bearbeitung ohne Wartezeit im Topf.",
                SweetPdf.GRAU, 8.5f);

        inhalt.ueberschrift("2. Kennzahlen");
        inhalt.kasten("Aufträge gesamt: " + agg.gesamt()
                        + "  —  verifiziert (DB Soll = Ist): " + agg.verifiziert()
                        + "  —  Verifikation n.i.O.: " + agg.verifikationNio()
                        + "  —  beim Spielen fehlgeschlagen: " + agg.fehlgeschlagen(),
                false, SweetPdf.WEISS,
                simulator.laufErfolgreich() ? SweetPdf.FUELL_GRUEN : SweetPdf.FUELL_ROT,
                10f);
        inhalt.leereZeile(2f);
        inhalt.paar("Summe verifizierter Auszahlungen", agg.summe().toPlainString() + " EUR");
        inhalt.paar("Dauer je Auftrag (min / Ø / max)",
                agg.dauerMinMs() + " ms / " + agg.dauerAvgMs() + " ms / " + agg.dauerMaxMs() + " ms");
        inhalt.paar("Dauer je abgeschlossenem Auftrag (p50 / p95 / p99)",
                agg.dauerP50Ms() + " ms / " + agg.dauerP95Ms() + " ms / " + agg.dauerP99Ms()
                        + " ms (nearest-rank, n=" + agg.abgeschlossen() + ")");

        inhalt.ueberschrift("3. Verteilung je Szenario-Typ");
        agg.jeTyp().forEach((typ, zeile) -> inhalt.text(String.format(
                "%-22s %4d Aufträge   verifiziert %4d   n.i.O. %2d   fehlgeschlagen %2d   Ø %5d ms",
                typ, zeile.anzahl(), zeile.verifiziert(), zeile.nio(), zeile.fehlgeschlagen(),
                zeile.avgMs()), SweetPdf.SCHWARZ, 9f));

        inhalt.ueberschrift("4. Verteilung je Sachbearbeiter");
        agg.jeMitarbeiter().forEach((kuerzel, zeile) -> inhalt.text(String.format(
                "%-12s %4d erledigt   Ø %5d ms je Auftrag", kuerzel,
                zeile.erledigt(), zeile.avgMs()), SweetPdf.SCHWARZ, 9f));

        inhalt.leereZeile(4f);
        inhalt.text("Hinweis: Die Einzelfälle sind in diesem Bericht bewusst nicht "
                + "ausgeschrieben. Der vollständige Nachweis je Auftrag steht in den "
                + "Protokolldateien des Lauf-Verzeichnisses (simulation.log, "
                + "auftrag-<Nr>-<Aktenzeichen>.log, db-verifikation.log, fehler.log) sowie "
                + "im maschinenlesbaren Gegenstück dieses Reports (lastreport.md).",
                SweetPdf.GRAU, 8.5f);

        int seiten = inhalt.schreiben(ziel);
        simulator.log().ereignis("SIMULATOR", "", "Lasttest-Gesamtreport geschrieben: "
                + ziel + " (" + seiten + " Seiten)");
    }

    // ------------------------------------------------------------------
    // MD: maschinenlesbares Gegenstück (reine Aggregate)
    // ------------------------------------------------------------------

    private static void schreibeMd(Path ziel, Simulator simulator) throws IOException {
        Aggregate agg = aggregate(simulator);
        StringBuilder md = new StringBuilder();
        md.append(simulator.letzteKategorie() == Simulator.Kategorie.STRESS
                ? "# stresstest_gesamtreport\n\n" : "# lasttest_gesamtreport\n\n");
        md.append("# lauf\n\n");
        kv(md, "automatik_bestanden", String.valueOf(simulator.laufErfolgreich()));
        kv(md, "pruefumfang", "Automatischer Last-/Stresslauf; KI-Stichprobe und Gesamtzertifikat werden separat bewertet");
        kv(md, "performance_evidence", "FUNCTIONAL_PARALLEL_RUN");
        kv(md, "capacity_evidence", "false");
        kv(md, "performance_scope", RunMetrics.SCOPE);
        kv(md, "measurement_window", "Laufstart bis Ende SQL-Verifikation einschließlich Vorbereitung; keine isolierte Servicekapazität");
        simulator.globalePruefungen().forEach(p -> kv(md, "globale_pruefung", p.toString()));
        kv(md, "kategorie", String.valueOf(simulator.letzteKategorie()));
        kv(md, "modus", "WEB".equalsIgnoreCase(simulator.letzterModus()) ? "WEB" : "FACHSCHICHT");
        kv(md, "vier_augen_pruefumfang", simulator.vierAugenPruefumfang());
        kv(md, "lauf_start", String.valueOf(simulator.laufStart()));
        kv(md, "lauf_ende", String.valueOf(simulator.laufEnde()));
        kv(md, "dauer_s", String.valueOf(simulator.laufdauerSekunden()));
        kv(md, "dauer_ms", String.valueOf(simulator.laufdauerMillis()));
        kv(md, "data_namespace", simulator.laufDatenPraefix());
        if (simulator.konfiguration() != null) {
            kv(md, "mitarbeiter", String.valueOf(simulator.konfiguration().mitarbeiter()));
            kv(md, "auftraege", String.valueOf(simulator.konfiguration().auftraege()));
            kv(md, "seed", String.valueOf(simulator.konfiguration().seed()));
            kv(md, "denkzeit_ms", String.valueOf(simulator.konfiguration().denkzeitMs()));
        }
        kv(md, "db", simulator.dbUrl);

        md.append("\n# ergebnis\n\n");
        kv(md, "gesamt", String.valueOf(agg.gesamt()));
        kv(md, "verifiziert", String.valueOf(agg.verifiziert()));
        kv(md, "verifikation_nio", String.valueOf(agg.verifikationNio()));
        kv(md, "fehlgeschlagen", String.valueOf(agg.fehlgeschlagen()));
        kv(md, "summe_verifizierter_auszahlungen_eur", agg.summe().toPlainString());
        kv(md, "dauer_ms_min", String.valueOf(agg.dauerMinMs()));
        kv(md, "dauer_ms_avg", String.valueOf(agg.dauerAvgMs()));
        kv(md, "dauer_ms_max", String.valueOf(agg.dauerMaxMs()));
        kv(md, "abgeschlossen", String.valueOf(agg.abgeschlossen()));
        kv(md, "dauer_ms_p50", String.valueOf(agg.dauerP50Ms()));
        kv(md, "dauer_ms_p95", String.valueOf(agg.dauerP95Ms()));
        kv(md, "dauer_ms_p99", String.valueOf(agg.dauerP99Ms()));
        kv(md, "percentile_method", "nearest-rank; abgeschlossene Aufträge, Worker-Bearbeitung ohne Topfwartezeit");
        double durchsatz = RunMetrics.perMinute(agg.abgeschlossen(), simulator.laufdauerMillis());
        kv(md, "durchsatz_auftraege_pro_min", String.format(java.util.Locale.ROOT, "%.1f", durchsatz));

        md.append("\n# je_szenario_typ\n\n");
        agg.jeTyp().forEach((typ, zeile) -> md.append(typ).append(" = anzahl ")
                .append(zeile.anzahl()).append(" | verifiziert ").append(zeile.verifiziert())
                .append(" | nio ").append(zeile.nio()).append(" | fehlgeschlagen ")
                .append(zeile.fehlgeschlagen()).append(" | avg_ms ").append(zeile.avgMs())
                .append('\n'));

        md.append("\n# je_mitarbeiter\n\n");
        agg.jeMitarbeiter().forEach((kuerzel, zeile) -> md.append(kuerzel).append(" = erledigt ")
                .append(zeile.erledigt()).append(" | avg_ms ").append(zeile.avgMs())
                .append('\n'));

        md.append("\n# hinweis\n\n");
        md.append("dokumentation = gekürzt (nur Aggregate); Einzelnachweise in den "
                + "Protokolldateien des Lauf-Verzeichnisses (simulation.log, auftrag-*.log, "
                + "db-verifikation.log, fehler.log)\n");

        java.nio.file.Files.writeString(ziel, md.toString());
        simulator.log().ereignis("SIMULATOR", "", "Maschinenlesbarer Lastreport geschrieben: " + ziel);
    }

    private static void kv(StringBuilder md, String schluessel, String wert) {
        md.append(schluessel).append(" = ").append(wert == null || wert.isBlank()
                ? "-" : wert).append('\n');
    }
}
