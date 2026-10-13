package de.allgemein.testsweet;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Maschinenlesbare Datenfassung des Simulator-Laufs als Markdown ({@code
 * testdokumentation.md} im Laufverzeichnis) — bewusst ohne Fließtext und ohne
 * Inhaltsverzeichnis: nur die Daten, die eine andere KI zum unabhängigen
 * Nachrechnen und Nachprüfen braucht — Rechengrundlagen als Zahlen, je
 * Testfall die Eingaben, alle Zwischenwerte, das Endergebnis, die vergebenen
 * Datenbank-IDs, die Prüf-SQLs mit konkreten Parametern gegen die Ist-Werte
 * sowie das Schrittprotokoll in Kurzform.
 */
final class MdBericht {

    private static final DateTimeFormatter ZEIT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private MdBericht() {
    }

    static void schreibe(Path ziel, Simulator simulator) throws IOException {
        schreibe(ziel, simulator, Map.of());
    }

    static void schreibe(Path ziel, Simulator simulator, Map<String, Object> evidence) throws IOException {
        StringBuilder md = new StringBuilder();
        kopf(md, simulator);
        grundlagen(md);
        for (Auftrag a : simulator.auftraege()) {
            testfall(md, a);
        }
        KiPruefAnhang.md(md, evidence);
        Files.writeString(ziel, md.toString(), StandardCharsets.UTF_8);
        SweetLog log = simulator.log();
        if (log != null) log.ereignis("SIMULATOR", "",
                "Maschinenlesbare Datenfassung geschrieben: " + ziel);
    }

    // ==================================================================

    private static void kopf(StringBuilder md, Simulator simulator) {
        md.append("# testsweet Lauf\n\n");
        kv(md, "lauf_erfolgreich", String.valueOf(simulator.laufErfolgreich()));
        for (Auftrag.Pruefung p : simulator.globalePruefungen()) {
            kv(md, "globale_pruefung", p.toString());
        }
        kv(md, "produkt", "SDN.Leistung LMG");
        boolean istWeb = "WEB".equalsIgnoreCase(simulator.letzterModus());
        kv(md, "modus", istWeb ? "2-web (Weboberfläche/Selenium)" : "1-fachschicht (SozialLeistungsService direkt)");
        kv(md, "kategorie", String.valueOf(simulator.letzteKategorie()));
        kv(md, "start", String.valueOf(simulator.laufStart()));
        kv(md, "ende", String.valueOf(simulator.laufEnde()));
        kv(md, "dauer_s", String.valueOf(simulator.laufdauerSekunden()));
        kv(md, "db", simulator.dbUrl);
        kv(md, "db_user", simulator.dbUser);
        if (simulator.konfiguration() != null) {
            kv(md, "mitarbeiter", String.valueOf(simulator.konfiguration().mitarbeiter()));
            kv(md, "auftraege", String.valueOf(simulator.konfiguration().auftraege()));
            kv(md, "seed", String.valueOf(simulator.konfiguration().seed()));
            kv(md, "denkzeit_ms", String.valueOf(simulator.konfiguration().denkzeitMs()));
        }
        int verifiziert = 0;
        int nok = 0;
        int fehl = 0;
        for (Auftrag a : simulator.auftraege()) {
            verifiziert += a.status == Auftrag.Status.VERIFIZIERT ? 1 : 0;
            nok += a.status == Auftrag.Status.VERIFIKATION_FEHLGESCHLAGEN ? 1 : 0;
            fehl += a.status == Auftrag.Status.FEHLGESCHLAGEN ? 1 : 0;
        }
        kv(md, "ergebnis.verifiziert", String.valueOf(verifiziert));
        kv(md, "ergebnis.verifikation_nio", String.valueOf(nok));
        kv(md, "ergebnis.fehlgeschlagen", String.valueOf(fehl));
        kv(md, "ergebnis.gesamt", String.valueOf(simulator.auftraege().size()));
        md.append("hinweis: Soll-Beträge werden mit der Produktionsberechnung (Leistungsberechnung) "
                + "ermittelt; unabhängige Goldwerte-Absicherung: GoldWerteTest im Hauptprojekt "
                + "(Werte aus doc/regressionstests.md).\n");
        md.append('\n');
        pruefanleitung(md);
    }

    /**
     * Selbst-Instruktion für die Weitergabe an eine prüfende KI: Die Datei soll
     * ohne weitere Erklärung aus sich heraus verständlich prüfbar sein — der
     * Nutzer kann sie als Ganzes kopieren und zur Überprüfung vorlegen.
     */
    private static void pruefanleitung(StringBuilder md) {
        md.append("## pruefanleitung_fur_ki\n\n");
        md.append("Diese Datei ist das maschinenlesbare Protokoll eines testsweet-Laufs der "
                + "Fachanwendung SDN.Leistung (Grundsicherung im Alter/bei Erwerbsminderung, "
                + "SGB XII). Bitte unabhängig überprüfen:\n\n");
        md.append("1. BETRÄGE NACHRECHNEN (Positivfälle): Je Testfall stehen unter '### rechenwege' "
                + "die Eingaben je Person (p1_eingaben, p2_eingaben, ...) und JEDE Zwischengröße "
                + "(regelbedarf, mehrbedarfe, kdu, anrechenbar, bg_zahlbetrag, "
                + "budget_rest_hypothetisch). Das hypothetische Restbudget beschreibt nur "
                + "Startbudget minus Anspruch, keine tatsächliche Buchung. Ein explizites "
                + "budget_rest_erwartet bzw. die SQL-Erwartung beschreibt den Buchungsendzustand; "
                + "bei Abweisung bleibt das Budget unverändert, Storno gibt den Betrag zurück. "
                + "Rechne mit den Formeln aus '## rechengrundlagen_2026' "
                + "jeden Zwischenwert und den Zahlbetrag selbst nach und melde jede Abweichung.\n");
        md.append("2. ERWARTET/GEGEN IST HALTEN: Unter '### verifikation' steht je Prüfung V1..Vn "
                + "das prüfende SQL mit konkreten Parametern sowie 'erwartet' und 'ist'. Prüfe, "
                + "ob die erwarteten Werte fachlich aus den Eingaben folgen und ob ist = erwartet "
                + "ist (NIO-Zeilen sind Fehler).\n");
        md.append("3. NEGATIVFÄLLE PLAUSIBILISIEREN: Schritte mit 'ERWARTETER FEHLER (CODE)' "
                + "müssen fachlich zwingend abgewehrt werden (z. B. KEIN_ANSPRUCH wenn "
                + "Einkommen/Vermögen den Bedarf übersteigt, VALIDATION bei Doppelanordnung je "
                + "Monat oder Zahlungsmonat außerhalb des Bewilligungszeitraums, BERECHTIGUNG bei "
                + "Selbstfreigabe, KONKURRENZ bei veraltetem Maskenstand, BUDGET_ERSCHOEPFT bei "
                + "zu kleinem Startbudget). Prüfe, dass der Datenbank-Endzustand unverändert "
                + "geblieben ist (keine Zahlung, Budget unangetastet).\n");
        md.append("4. GRENZEN: Last-/Stresstests erzeugen nur Aggregate (kein Einzelfall-Nachrechnen); "
                + "Web-Läufe (modus 2-web) nutzen dieselbe Datenstruktur, dokumentieren aber "
                + "UI-Schritte. Globale Prüfungen stehen im Kopf als globale_pruefung-Zeilen.\n");
        md.append("Das verbindliche Antwortschema steht im übergeordneten Prüfauftrag. "
                + "Dieser Datenanhang legt kein abweichendes Antwortformat fest.\n\n");
    }

    private static void grundlagen(StringBuilder md) {
        md.append("## rechengrundlagen_2026\n\n");
        md.append("rbs_eur = {1: 563.00, 2: 506.00, 3: 451.00, 4: 471.00, 5: 390.00, 6: 357.00}  "
                + "(Anlage zu § 28 SGB XII)\n");
        md.append("mehrbedarf_g = 0.17 * regelbedarf  (§ 30 Abs. 1)\n");
        md.append("mehrbedarf_schwanger = 0.17 * regelbedarf  (§ 30 Abs. 2, ab 13. SSW; "
                + "entfaellt automatisch, wenn entbindungsmonat gesetzt und "
                + "zahlungsmonat > entbindungsmonat)\n");
        md.append("mehrbedarf_alleinerziehend = 0.36 * rbs1 bei kind_u7>=1 oder 2<=kinder_u16<=3, "
                + "sonst 0.12 * rbs1 je minderjaehrigem kind; deckel 0.60 * rbs1 = 337.80  (§ 30 Abs. 3)\n");
        md.append("mehrbedarf_obergrenze = summe(abs1..5) <= regelbedarf  (§ 30 Abs. 6)\n");
        md.append("mehrbedarf_warmwasser_anteil = {1: 0.023, 2: 0.023, 3: 0.0, 4: 0.014, "
                + "5: 0.012, 6: 0.008}  (§ 30 Abs. 7, zusaetzlich zu Abs. 6)\n");
        md.append("kdu = kaltmiete + betriebskosten + heizung  (§ 42a; angemessenheitspruefung AUS)\n");
        md.append("erwerbsfreibetrag = min(0.30 * erwerb_brutto, 0.50 * rbs1 = 281.50)  (§ 82 Abs. 3)\n");
        md.append("anrechenbar = rente + erwerb - freibetrag - absetzbetaetrag >= 0  "
                + "(absetzbetraege: steuern, sv, versicherungen, erwerbsaufwendungen; § 82 Abs. 2)\n");
        md.append("schonvermoegen = 10000.00 je volljaehriger person (rbs 1-3) + 500.00 je "
                + "weiterer person  (§ 90 Abs. 2 Nr. 9 i.V.m. § 1 BarbetragsVO)\n");
        md.append("zahlbetrag = gesamtbedarf - anrechenbares_einkommen - "
                + "max(vermoegen - schonvermoegen, 0)  (<= 0 => KEIN_ANSPRUCH, § 43 Abs. 1)\n");
        md.append("rundung = HALF_UP auf 2 nachkommastellen; waehrung EUR\n");
        md.append("zahlstatus = ANGELEGT (wartet auf freigabe) -> BEREIT -> UEBERGEBEN; "
                + "STORNIERT gibt monat frei\n\n");
    }

    private static void testfall(StringBuilder md, Auftrag a) {
        md.append("## TF ").append(String.format("%02d", a.id)).append(' ')
                .append(a.szenarioTyp).append(' ').append(a.aktenzeichen).append('\n');
        kv(md, "status", a.status.name());
        if (a.fehlerText != null) {
            kv(md, "fehler", a.fehlerText);
        }
        String fachfehler = erwarteteFachfehler(a);
        if (!fachfehler.isEmpty()) {
            kv(md, "erwartete_fachfehler", fachfehler);
        }
        kv(md, "hauptperson", a.hauptperson);
        kv(md, "bearbeiter", a.bearbeiter != null ? a.bearbeiter : "-");
        kv(md, "bg_id", a.bgId != null ? String.valueOf(a.bgId) : "-");
        kv(md, "dauer_ms", String.valueOf(a.dauerMs()));
        if (!a.kontext.isEmpty()) {
            kv(md, "ids", kontextZeile(a.kontext));
        }
        md.append('\n');

        if (!a.rechenbloecke.isEmpty()) {
            md.append("### rechenwege\n\n");
            for (Rechenblock block : a.rechenbloecke) {
                block(md, block);
            }
        } else {
            md.append("### rechenwege\n\nkeine (testfall prueft ablauf-/sperrlogik, "
                    + "keine betragsberechnung)\n\n");
        }

        md.append("### verifikation\n\n");
        if (a.pruefungen().isEmpty()) {
            md.append("keine\n\n");
        } else {
            int i = 1;
            for (Auftrag.Pruefung p : a.pruefungen()) {
                md.append("V").append(i++).append(": ")
                        .append(p.ok() ? "OK" : "NIO").append(" | ")
                        .append(p.beschreibung()).append('\n');
                md.append("    sql    = ").append(sqlFuer(a, p)).append('\n');
                md.append("    params = ").append(paramsFuer(a, p)).append('\n');
                md.append("    erwartet = ").append(p.erwartet())
                        .append(" | ist = ").append(p.ist()).append('\n');
            }
            md.append('\n');
        }

        md.append("### protokoll\n\n");
        for (Auftrag.Schritt s : a.schritte()) {
            md.append(s.zeit().format(ZEIT)).append(' ').append(s.mitarbeiter())
                    .append(" | ").append(s.aktion()).append(" -> ").append(s.ergebnis())
                    .append('\n');
        }
        md.append('\n');
    }

    private static void block(StringBuilder md, Rechenblock block) {
        md.append("block: ").append(block.titel()).append('\n');
        if (block.zahlungsmonat() != null) {
            kv(md, "zahlungsmonat", block.zahlungsmonat());
        }
        kv(md, "vermoegen", z(block.vermoegen()));
        kv(md, "schonvermoegen", z(block.schonvermoegen()));
        kv(md, "budget_start", block.budget() == null ? "-" : z(block.budget()));
        for (int i = 0; i < block.faelle().size(); i++) {
            var fall = block.faelle().get(i);
            var f = block.ergebnis().faelle().get(i);
            md.append("p").append(i + 1).append("_eingaben: rbs=").append(fall.regelbedarfsstufe())
                    .append(", merkzeichen_g=").append(fall.merkzeichenG())
                    .append(", schwanger=").append(fall.schwanger())
                    .append(", entbindungsmonat=")
                    .append(fall.entbindungsmonat() == null ? "-" : fall.entbindungsmonat())
                    .append(", kinder_u7=").append(fall.anzKinderUnter7())
                    .append(", kinder_u16=").append(fall.anzKinderUnter16())
                    .append(", kinder_minderjaehrig=").append(fall.anzKinderMinderjaehrig())
                    .append(", warmwasser=").append(fall.warmwasser())
                    .append(", kdu_miete=").append(z(fall.kduMiete()))
                    .append(", kdu_betrieb=").append(z(fall.kduBetriebskosten()))
                    .append(", kdu_heizung=").append(z(fall.kduHeizung()))
                    .append(", rente=").append(z(fall.einkommenRente()))
                    .append(", erwerb=").append(z(fall.einkommenErwerb()))
                    .append(", abz_steuern=").append(z(fall.abzSteuern()))
                    .append(", abz_sv=").append(z(fall.abzSozialversicherung()))
                    .append(", abz_vers=").append(z(fall.abzVersicherungen()))
                    .append(", abz_erwerb=").append(z(fall.abzErwerbsaufwendungen()))
                    .append('\n');
            md.append("p").append(i + 1).append("_regelbedarf=").append(z(f.regelbedarf())).append('\n');
            md.append("p").append(i + 1).append("_mehrbedarf_g=").append(z(f.mehrbedarfG())).append('\n');
            md.append("p").append(i + 1).append("_mehrbedarf_schwanger=").append(z(f.mehrbedarfSchwangere())).append('\n');
            md.append("p").append(i + 1).append("_mehrbedarf_alleinerziehend=")
                    .append(z(f.mehrbedarfAlleinerziehend())).append('\n');
            md.append("p").append(i + 1).append("_mehrbedarf_warmwasser=")
                    .append(z(f.mehrbedarfWarmwasser())).append('\n');
            md.append("p").append(i + 1).append("_kdu=").append(z(f.kdu())).append('\n');
            md.append("p").append(i + 1).append("_gesamtbedarf=").append(z(f.gesamtbedarf())).append('\n');
            md.append("p").append(i + 1).append("_freibetrag_erwerb=").append(z(f.freibetragErwerb())).append('\n');
            md.append("p").append(i + 1).append("_absetzbetaetrag=").append(z(f.absetzbetaetrag())).append('\n');
            md.append("p").append(i + 1).append("_anrechenbar=").append(z(f.anrechenbaresEinkommen())).append('\n');
            md.append("p").append(i + 1).append("_zahlbetrag=").append(z(f.zahlbetrag())).append('\n');
        }
        md.append("bg_gesamtbedarf=").append(z(block.ergebnis().gesamtbedarf())).append('\n');
        md.append("bg_anrechenbar=").append(z(block.ergebnis().anrechenbaresEinkommen())).append('\n');
        md.append("bg_verwertbares_vermoegen=")
                .append(z(block.ergebnis().verwertbaresVermoegen())).append('\n');
        md.append("bg_zahlbetrag=").append(z(block.ergebnis().zahlbetrag())).append('\n');
        if (block.budget() != null) {
            md.append("budget_rest_hypothetisch=")
                    .append(z(block.budget().subtract(block.ergebnis().zahlbetrag()))).append('\n');
        }
        if (block.budgetRestExpected() != null) {
            md.append("budget_rest_erwartet=").append(z(block.budgetRestExpected())).append('\n');
        }
        md.append('\n');
    }

    // ==================================================================

    private static void kv(StringBuilder md, String schluessel, String wert) {
        md.append(schluessel).append(" = ").append(wert).append('\n');
    }

    /**
     * Einzeilige Aufzählung der Kontext-Schlüssel=Wert-Paare für die ids-Zeile.
     * Schlüssel, die (case-insensitive) „passwort" enthalten, werden bewusst
     * ausgelassen — in der maschinenlesbaren Doku dürfen niemals Passwörter
     * stehen (auch nicht als Nebeneffekt künftiger Kontext-Erweiterungen).
     */
    static String kontextZeile(Map<String, Object> kontext) {
        StringBuilder ids = new StringBuilder();
        kontext.forEach((k, v) -> {
            if (k != null && k.toLowerCase(java.util.Locale.ROOT).contains("passwort")) {
                return; // Passwörter nie ausschreiben
            }
            if (ids.length() > 0) {
                ids.append(", ");
            }
            ids.append(k).append('=').append(v);
        });
        return ids.toString();
    }

    private static String sqlFuer(Auftrag a, Auftrag.Pruefung p) {
        for (Erwartung e : a.erwartungen) {
            if (e.beschreibung().equals(p.beschreibung())) {
                return e.sql().replaceAll("\\s+", " ").trim();
            }
        }
        return "?";
    }

    private static String paramsFuer(Auftrag a, Auftrag.Pruefung p) {
        for (Erwartung e : a.erwartungen) {
            if (e.beschreibung().equals(p.beschreibung())) {
                try {
                    List<Object> werte = e.parameter().apply(a.kontext);
                    return werte.toString();
                } catch (Exception ex) {
                    return "?";
                }
            }
        }
        return "?";
    }

    private static String z(BigDecimal betrag) {
        return String.format(java.util.Locale.ROOT, "%.2f",
                betrag == null ? BigDecimal.ZERO : betrag);
    }

    /**
     * Aggregiert die erwarteten Fachfehler-Codes aus dem Schrittprotokoll
     * („ERWARTETER FEHLER (CODE) — …") zu einer Zeile je Testfall — damit eine
     * prüfende KI die Abwehr-Erwartung schon im Testfall-Kopf sieht.
     */
    private static String erwarteteFachfehler(Auftrag a) {
        java.util.LinkedHashSet<String> codes = new java.util.LinkedHashSet<>();
        java.util.regex.Pattern muster = java.util.regex.Pattern
                .compile("ERWARTETER FEHLER \\(([A-Z_]+)\\)");
        for (Auftrag.Schritt s : a.schritte()) {
            java.util.regex.Matcher m = muster.matcher(s.ergebnis());
            if (m.find()) {
                codes.add(m.group(1));
            }
        }
        return String.join("+", codes);
    }
}
