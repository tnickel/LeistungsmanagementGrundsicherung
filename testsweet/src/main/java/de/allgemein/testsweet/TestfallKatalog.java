package de.allgemein.testsweet;

import java.util.List;
import java.util.Locale;

/**
 * Statische, ausführliche Beschreibung der Testfall-Arten je Kategorie — die
 * Ergänzung zum Laufbericht: Bevor ein Lauf startet, beschreibt dieser Katalog,
 * was die einzelnen Szenario-Arten tun, welche Fachregeln sie prüfen und was am
 * Ende in der Datenbank stehen muss. Der Umfang bleibt pro Kategorie klein:
 * Beim Stresstest werden die beiden Wellen und der Rahmen beschrieben, nicht
 * jeder einzelne der tausende Aufträge (die wiederholen je Wellen-Typ dasselbe).
 */
final class TestfallKatalog {

    /** Ein Katalog-Eintrag: Szenario-Art, Gewichtung/Range, ausführliche Beschreibung. */
    record Eintrag(String name, String anteil, String text) {
    }

    private TestfallKatalog() {
    }

    /** Katalog je Kategorie; {@code web} schaltet auf die Selenium-Varianten um. */
    static List<Eintrag> fuer(String kategorie, boolean web) {
        return switch (kategorie.toUpperCase(Locale.ROOT)) {
            case "POSITIV" -> web ? positivWeb() : positivFachschicht();
            case "NEGATIV" -> web ? negativWeb() : negativFachschicht();
            case "LAST" -> last();
            case "STRESS" -> stress();
            default -> List.of(new Eintrag("Unbekannte Kategorie", "",
                    "Für diese Kategorie ist kein Katalog hinterlegt."));
        };
    }

    // ------------------------------------------------------------------
    // POSITIV — Fachschicht (10 Szenario-Arten, gewichtet)
    // ------------------------------------------------------------------

    private static List<Eintrag> positivFachschicht() {
        return List.of(
                new Eintrag("Standardrente mit Vier-Augen-Kette", "ca. 22 % der Aufträge",
                        """
                        Der Grunddurchlauf des Systems: Anlage eines Vorgangs (Budget 1.200 EUR) mit einem \
                        SozialFall der Regelbedarfsstufe 1 und Kosten der Unterkunft (Kaltmiete 350, Betriebskosten 60, \
                        Heizung 80 = 490 EUR). Anordnung der Auszahlung für den laufenden Monat durch den Erfasser, \
                        dann versucht derselbe Erfasser die eigene Freigabe — das muss mit dem Fachfehler BERECHTIGUNG \
                        abgewehrt werden (Vier-Augen-Prinzip). Erst der Kollege gibt frei (ANGELEGT → BEREIT). \
                        Erwarteter DB-Endzustand: genau eine Zahlung mit 1.053,00 EUR (563 Regelbedarf + 490 KdU), \
                        Status BEREIT, SACHBEARBEITER_ID = Erfasser, FREIGEGEBEN_VON = Kollege, Restbudget 147,00 EUR, \
                        Journaleintrag der Freigabe zugeordnet."""),
                new Eintrag("Paar in gemeinsamer Wohnung", "ca. 10 %",
                        """
                        Zwei SozialFälle (je Regelbedarfsstufe 2 = 506,00 EUR) in einem Vorgang; die Kosten der \
                        Unterkunft (650,00 EUR) stehen am ersten Fall und gelten vorgangsweit. Erwartet: Zahlbetrag \
                        1.662,00 EUR (506 + 506 + 650), FALL_ID der Zahlung bleibt NULL, weil bei zwei Personen kein \
                        eindeutiger Fall zugeordnet werden kann; Restbudget und Journaleinträge nur den eigenen \
                        Bearbeitern zugeordnet."""),
                new Eintrag("Mehrbedarfe nach § 30 SGB XII", "ca. 10 %",
                        """
                        Einzelfall mit Merkzeichen-G- und/oder Schwangerschafts-Mehrbedarf (je 17 % der Regelbedarfs-\
                        stufe) und/oder dezentraler Warmwassererzeugung (stufenabhängig 2,3 bis 0,8 %; Stufe 3 ohne \
                        Satz). Die Summe der Mehrbedarfe nach Abs. 1–5 darf die Regelbedarfsstufe nie übersteigen \
                        (§ 30 Abs. 6), der Warmwasser-Anteil liegt außerhalb dieses Deckels. Erwartet: Zahlbetrag = \
                        Regelbedarf + Mehrbedarfe, persistierte Merkmals-Flags am SozialFall, Betrag exakt wie \
                        vorab berechnet."""),
                new Eintrag("Familie / Alleinerziehende", "ca. 10 %",
                        """
                        Mutter (Regelbedarfsstufe 1) mit zwei Kindern unter 16 als eigene SozialFälle der Stufe 6. \
                        Der Alleinerziehenden-Mehrbedarf wird mit 36 % der Regelbedarfsstufe 1 angesetzt (zwei bis drei \
                        Kinder unter 16, § 30 Abs. 3 Nr. 1), die Kinder zählen als eigene Fälle. Erwartet: drei \
                        SozialFall-Zeilen, Zahlbetrag 2.189,68 EUR im Referenzfall, FALL_ID NULL, Wiedervorlagedatum \
                        am Vorgang persistiert."""),
                new Eintrag("Erwerbstätiger mit Freibetrag und Absetzbeträgen", "ca. 10 %",
                        """
                        Erwerbseinkommen (z. B. 1.200 EUR brutto) wird nach § 82 SGB XII bereinigt: erst die \
                        Absetzbeträge nach Abs. 2 (Steuern, Sozialversicherung, Versicherungen, Erwerbsaufwendungen), \
                        dann der Erwerbsfreibetrag nach Abs. 3 (30 % des Bruttos, gedeckelt auf 50 % der \
                        Regelbedarfsstufe 1 = 281,50 EUR). Erwartet: anrechenbares Einkommen exakt nach dieser \
                        Reihenfolge, Zahlbetrag = Bedarf − anrechenbar, persistierte Absetz-Felder."""),
                new Eintrag("Vermögen über dem Schonvermögen", "ca. 10 % (mit Zufallszweig)",
                        """
                        Vorgang mit deklariertem Vermögen. Liegt es über dem Schonvermögen (10.000 EUR je erwachsener \
                        Person, § 90 Abs. 2 Nr. 9 SGB XII), wird der übersteigende Teil auf den Bedarf angerechnet und \
                        mit der Auszahlung verbraucht (Verbrauchsfortschreibung: der Bestand sinkt auf das \
                        Schonvermögen). Ein Zufallszweig legt das Vermögen so hoch, dass Einkommen/Vermögen den Bedarf \
                        übersteigen — dann muss die Anordnung mit KEIN_ANSPRUCH abgewiesen werden und die Datenbank \
                        unverändert bleiben (keine Zahlung, Budget unangetastet)."""),
                new Eintrag("Doppelklick / Netzwerk-Wiederholung (Idempotenz)", "ca. 7 %",
                        """
                        Derselbe Auszahlungsauftrag wird zweimal mit identischer VORGANGS_NR gesendet — wie bei einem \
                        Doppelklick oder einem Netzwerk-Timeout mit Wiederholung. Erwartet: der zweite Aufruf erkennt \
                        die bereits gebuchte Zahlung (Antwort „bereits gebucht“, dieselbe Zahlungs-ID), die Datenbank \
                        enthält genau EINE Zahlung, genau einen Journaleintrag und das Restbudget ist nur EINMAL \
                        gemindert."""),
                new Eintrag("Storno und korrigierte Neuauszahlung im selben Monat", "ca. 10 %",
                        """
                        Anordnung und Freigabe für einen Monat, dann Storno (ANGELEGT/BEREIT → STORNIERT) mit \
                        vollständiger Budget-Rückbuchung und Vermögens-Rückbuchung des dokumentierten Einsatzes. Danach \
                        Korrektur des Falls (Miete ändern, optimistisches Locking hebt die Version an) und erneute \
                        Anordnung für denselben Monat — zulässig, weil der Storno den Monat freigibt. Erwartet: zwei \
                        Zahlungszeilen (alte STORNIERT mit altem Betrag, neue mit korrigiertem), Budget konsistent, \
                        Fall-Version erhöht."""),
                new Eintrag("Konkurrenz beim Masken speichern (Lost-Update)", "ca. 6 %",
                        """
                        Zwei Sachbearbeiter öffnen denselben Fall. Der erste speichert eine Mietkorrektur erfolgreich \
                        (Version 1 → 2), der zweite sendet danach mit dem veralteten Maskenstand (Version 1). Erwartet: \
                        der zweite Speicherversuch wird mit dem Fachfehler KONKURRENZ abgewiesen, die Korrektur des \
                        ersten Bearbeiters bleibt unangetastet (KDU_MIETE = neuer Wert, VERSION = 2) — kein \
                        Lost Update."""),
                new Eintrag("Kassenlauf parallel zu Freigaben", "ca. 6 %",
                        """
                        Der Kassenläufer läuft bewusst parallel zu laufenden Freigaben: Er sperrt die BEREIT-Zahlungen, \
                        übergibt exakt diese gesperrte Menge (ANGELEGT-Beginner und Freigaben, die währenddessen \
                        committen, gehören zum nächsten Lauf). Erwartet: Anzahl == Summe der Übergabe konsistent, \
                        Status UEBERGEBEN nur mit gesetztem FREIGEGEBEN_VON, kein Zahlungszustand „kaputt“."""));
    }

    // ------------------------------------------------------------------
    // POSITIV — Web (Selenium)
    // ------------------------------------------------------------------

    private static List<Eintrag> positivWeb() {
        return List.of(
                new Eintrag("Web-Standardrente mit Kollegen-Freigabe", "ca. 40 % der Aufträge",
                        """
                        Derselbe Grunddurchlauf wie in der Fachschicht, aber vollständig über die Weboberfläche: \
                        Anmeldung im Browser, Vorgangsmaske ausfüllen, SozialFall erfassen, Auszahlung anordnen, \
                        abmelden; dann meldet sich der Kollegen-Browser an, öffnet den Vorgang über die Suche, gibt \
                        frei. Jeder Schritt erzeugt einen Screenshot im Laufordner. Erwartet: identischer DB-Endzustand \
                        wie im Fachschicht-Standard (Betrag, Budget, FREIGEGEBEN_VON) — der Beweis, dass Oberfläche \
                        und Fachschicht dasselbe System bedienen."""),
                new Eintrag("Web-Mehrbedarfsfall", "ca. 30 %",
                        """
                        Erfassung eines Falls mit Mehrbedarfs-Merkmalen über die Oberfläche (Checkboxen Merkzeichen G, \
                        Schwangerschaft, dezentrales Warmwasser; type=month-Felder werden wegen einer \
                        Chrome-Besonderheit per JavaScript gesetzt). Erwartet: Flags und Beträge wie in der \
                        Fachschicht-Variante, Masken-Werte korrekt persistiert."""),
                new Eintrag("Web-Storno über Admin-Rolle", "ca. 30 %",
                        """
                        Der Erfasser ordnet an und meldet sich ab; der Admin meldet sich im selben Fenster an (das \
                        Storno ist Admin-pflichtig), findet den Vorgang über die Suche und storniert über den Knopf in \
                        der Zahlungstabelle. Erwartet: Status STORNIERT sichtbar im Chip der Oberfläche und in der \
                        Datenbank, Restbudget zurückgebucht."""));
    }

    // ------------------------------------------------------------------
    // NEGATIV — Fachschicht (9 Abwehr-Arten)
    // ------------------------------------------------------------------

    private static List<Eintrag> negativFachschicht() {
        return List.of(
                new Eintrag("NEG-KEINANSPRUCH — Einkommen übersteigt Bedarf", "erwarteter Fehler: KEIN_ANSPRUCH",
                        """
                        Fall mit so hohem Einkommen/Vermögen, dass der Zahlbetrag null oder negativ wäre. Erwartet: \
                        Anordnung wird abgewiesen, es entsteht KEINE Zahlung, Budget und Fall bleiben unverändert."""),
                new Eintrag("NEG-BUDGET — Restbudget reicht nicht", "erwarteter Fehler: BUDGET_ERSCHOEPFT",
                        """
                        Bewilligter Bedarf übersteigt das Restbudget des Vorgangs. Erwartet: Abweisung, keine Zahlung, \
                        Budget unverändert (der CHECK BUDGET_REST >= 0 in der Datenbank wäre die letzte \
                        Verteidigungslinie — die greift nie, weil der Service vorprüft)."""),
                new Eintrag("NEG-DOPPELMONAT — zweiter Auftrag für denselben Monat",
                        "erwarteter Fehler: VALIDATION",
                        """
                        Für denselben Vorgang und denselben Monat wird eine zweite Anordnung mit anderer \
                        Vorgangsnummer versucht (sequenziell). Erwartet: Abweisung wegen der Monatsregel; genau eine \
                        wirksame Zahlung bleibt bestehen — Datenbankebene sichert das zusätzlich über den \
                        function-based Unique-Index UQ_ZAHLUNG_BG_MONAT_AKTIV."""),
                new Eintrag("NEG-SELBSTFREIGABE — Erfasser gibt sich selbst frei",
                        "erwarteter Fehler: BERECHTIGUNG",
                        """
                        Der Erfasser versucht die Freigabe seiner eigenen Anordnung. Erwartet: Abwehr mit \
                        BERECHTIGUNG, FREIGEGEBEN_VON bleibt NULL, Status bleibt ANGELEGT."""),
                new Eintrag("NEG-DOPPELFREIGABE — zweite Freigabe einer freigegebenen Zahlung",
                        "erwarteter Fehler: VALIDATION",
                        """
                        Eine bereits BEREIT-Zahlung soll erneut freigegeben werden. Erwartet: Abweisung (nur \
                        ANGELEGT-Zahlungen sind freigebbar), Status und Freigabevermerk unverändert."""),
                new Eintrag("NEG-STORNO-KASSE — Storno nach Kassenübergabe", "erwarteter Fehler: VALIDATION",
                        """
                        Eine bereits an die Kasse übergebene (UEBERGEBEN) Zahlung soll storniert werden. Erwartet: \
                        Abweisung — übergebenes Geld kann die Software nicht zurückholen; Status bleibt UEBERGEBEN, \
                        Budget unverändert."""),
                new Eintrag("NEG-BANKVERBINDUNG — ungültige IBAN/BIC", "erwarteter Fehler: VALIDATION",
                        """
                        Anordnung mit einer IBAN mit falscher Modulo-97-Prüfziffer bzw. einem zu kurzen BIC. Erwartet: \
                        Abweisung durch den SepaValidator, keine Zahlung entsteht."""),
                new Eintrag("NEG-BESCHEID-ZEITRAUM — Bewilligungszeitraum unzulässig",
                        "erwarteter Fehler: VALIDATION",
                        """
                        Bescheiderteilung mit invertiertem Zeitraum (bis vor von) oder mit 13 Monaten. Erwartet: \
                        Abweisung nach § 44 Abs. 3 SGB XII (Regelfall zwölf Monate), kein BESCHEID-Eintrag."""),
                new Eintrag("NEG-NICHTGEFUNDEN — Operation auf nicht vorhandener ID",
                        "erwarteter Fehler: NICHT_GEFUNDEN",
                        """
                        Freigabe/Storno/Detail auf eine sicher nicht existierende ID (z. B. −999). Erwartet: Abweisung \
                        mit NICHT_GEFUNDEN, keine Nebeneffekte."""),
                new Eintrag("Prinzip aller Negativfälle: unveränderter DB-Endzustand", "",
                        """
                        Nach jedem Negativ-Szenario wird nicht nur der Fehlercode geprüft, sondern per SQL der \
                        komplette Endzustand: keine neue Zahlung, Budget und Vermögen exakt wie vor dem Versuch, keine \
                        zusätzlichen Journal-Einträge mit Fachwirkung. Ein Negativtest gilt nur als bestanden, wenn \
                        die Abwehr „sauber“ war."""));
    }

    private static List<Eintrag> negativWeb() {
        return List.of(
                new Eintrag("WEB-NEG-LOGIN — falsche Anmeldedaten", "erwartet: Fehlerdialog in der Oberfläche",
                        """
                        Anmeldung mit falschem Passwort im Browser. Erwartet: Fehlerdialog „Kennung oder Passwort ist \
                        falsch“, kein Sitzungs-Token, keine Navigation in die Übersicht."""),
                new Eintrag("WEB-NEG-OHNE-MONAT — Auszahlung ohne Zahlungsmonat",
                        "erwartet: Fehlerdialog in der Oberfläche",
                        """
                        Anordnung mit leerem type=month-Feld. Erwartet: VALIDATION-Fehlerdialog, keine Zahlung in der \
                        Datenbank."""),
                new Eintrag("WEB-NEG-STORNO-SB — Storno-Knopf für Sachbearbeiter vorenthalten",
                        "erwartet: Knopf fehlt / 403",
                        """
                        Ein Sachbearbeiter (nicht Admin) sieht in der Zahlungstabelle für Admin-pflichtige Stornos \
                        keinen Knopf; ein direkter API-Aufruf würde mit 403 BERECHTIGUNG abgewiesen. Erwartet: UI und \
                        Server Regeln decken sich."""),
                new Eintrag("WEB-NEG-BUDGET — Budget überschritten über die Oberfläche",
                        "erwartet: Fehlerdialog BUDGET_ERSCHOEPFT",
                        """
                        Anordnung oberhalb des Restbudgets über die Maske. Erwartet: verständlicher Fehlerdialog, \
                        Datenbank unverändert."""),
                new Eintrag("WEB-NEG-DOPPELMONAT — zweiter Monat über die Maske",
                        "erwartet: Fehlerdialog VALIDATION",
                        """
                        Zweite Anordnung für denselben Monat über die Oberfläche. Erwartet: Abweisung mit \
                        Monatsregel-Meldung."""),
                new Eintrag("WEB-NEG-IBAN — ungültige Bankverbindung", "erwartet: Fehlerdialog VALIDATION",
                        """
                        IBAN mit falscher Prüfziffer in der Maske. Erwartet: SEPA-Fehlerdialog, keine Zahlung."""),
                new Eintrag("Prinzip aller Web-Negativfälle", "",
                        """
                        Bewiesen wird der Fehlerdialog als Screenshot im Laufordner UND der unveränderte \
                        Datenbank-Endzustand per SQL — die Oberfläche darf den Fehler melden, aber nie halb buchen."""));
    }

    // ------------------------------------------------------------------
    // LAST — schlanke Massendurchläufe (Kurzfassung, ein Eintrag)
    // ------------------------------------------------------------------

    private static List<Eintrag> last() {
        return List.of(new Eintrag("Lasttest — schlanker Massendurchlauf", "alle Aufträge",
                """
                Jeder Auftrag durchläuft die schlanke Kette Vorgang anlegen → SozialFall erfassen → Auszahlung \
                anordnen (Status ANGELEGT, bewusst ohne Freigabe und ohne Bescheid), mit kurzer Denkzeit und \
                zufälligen Beträgen. Geprüft wird je Auftrag der DB-Endzustand (Zahlung ANGELEGT mit dem \
                vorab berechneten Betrag, Restbudget passend, Fall angelegt) — der Bericht ist bewusst ein \
                gekürzter Gesamtbericht (lastreport) mit Aggregaten wie Durchsatz und maximale Sperrwartezeit \
                statt Einzelfall-Seiten. Ziel der Kategorie: Durchsatz und Stabilität unter paralleler Last, \
                nicht neue Fachpfade."""));
    }

    // ------------------------------------------------------------------
    // STRESS — zwei Wellen + Rahmen (bewusst 3 Einträge statt tausender Aufträge)
    // ------------------------------------------------------------------

    private static List<Eintrag> stress() {
        return List.of(
                new Eintrag("Stresstest — Rahmen und Prüfkonzept (gilt für alle Aufträge)",
                        "N User × 2 Wellen = 2 × N Aufträge",
                        """
                        Nur Fachschicht (Selenium läuft bewusst nicht): In Welle 1 legen alle Mitarbeiter parallel \
                        N Vorgänge an, die Phasenbarriere hält Welle 2 an, bis Welle 1 vollständig committet ist. \
                        Dann bearbeitet Welle 2 dieselben User wieder. Ziel ist die Belastungsprobe von Sperrverhalten, \
                        Verbindungspool und Wiederholungslogik unter hoher Parallelität (Referenz: 20 Mitarbeiter / \
                        2000 User in rund 13 Sekunden, 0 Retries). Nach dem Lauf verifiziert eine unabhängige \
                        SQL-Endprüfung über ALLE Vorgänge: N aktive Vorgänge, ALLE Fälle genau in Version 2 \
                        (Mietkorrektur durch Welle 2), N Zahlungen ANGELEGT für den Bezugsmonat, Summe der Zahlungen \
                        deckungsgleich mit der Summe der Budget-Minderungen, 4 Journaleinträge je User, kein negatives \
                        Budget. Ein Bruchteil dieser Prüfungen würde bei massenhaft gleichen Aufträgen nichts \
                        beweisen — deshalb die globale Aggregatprüfung statt Einzelfall-Seiten im Bericht."""),
                new Eintrag("Welle 1 — STRESS-ANLEGEN: Vorgang und Fall neu anlegen",
                        "N Aufträge (jeder Mitarbeiter bearbeitet einen Teil)",
                        """
                        Jeder Auftrag der ersten Welle legt einen eindeutigen Vorgang (Aktenzeichen SWEET-S-…) mit \
                        einem SozialFall (Regelbedarfsstufe 1, zufällige KdU) an — bewusst ohne Zahlung. Geprüft wird \
                        nach dem Lauf nur die Existenz und Grundkonsistenz der angelegten Daten (COUNT je Aktenzeichen \
                        = 1, Fall angelegt). Die eigentliche Belastung: alle Worker gleichzeitig auf INSERTs gegen \
                        dieselben Tabellen und Sequences."""),
                new Eintrag("Welle 2 — STRESS-BEARBEITEN: wiederfinden, korrigieren, anordnen",
                        "N Aufträge (dieselben User wie Welle 1)",
                        """
                        Jeder Auftrag der zweiten Welle findet den Vorgang aus Welle 1 über das Aktenzeichen wieder \
                        (neue Service-Methode findeBedarfsgemeinschaftNachAktenzeichen), ändert die Miete über \
                        optimistisches Locking (Fall-Version 1 → 2) und ordnet die Auszahlung an. Erwartet je Auftrag: \
                        Zahlung im Status ANGELEGT mit dem NACH der Korrektur berechneten Betrag, Budget-Rest = \
                        Startbudget − Zahlbetrag, SozialFall exakt Version 2. Die Phasenbarriere stellt sicher, dass \
                        Welle 2 nur startet, wenn alle Anlagen von Welle 1 committed sind."""));
    }
}
