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
    // POSITIV — Fachschicht (10 Grundarten, 9 feste Grenzfälle, Kombinationsgenerator)
    // ------------------------------------------------------------------

    private static List<Eintrag> positivFachschicht() {
        return List.of(
                new Eintrag("Standardrente mit Vier-Augen-Kette", "Auftrag 1, sofern im Topf",
                        "Einzelfall mit gestreuter Regelbedarfsstufe 1–3 und Wohnkosten. Nach der Anordnung wird die Selbstfreigabe ausdrücklich abgewehrt; anschließend gibt der Kollege frei. SQL prüft Betrag, Restbudget, Stufe, Freigabevermerk und genau ein Freigabejournal."),
                new Eintrag("Paar in gemeinsamer Wohnung", "Auftrag 2, sofern im Topf",
                        "Zwei Personen der Regelbedarfsstufe 2, Wohnkosten nur am ersten Fall. Geprüft werden die gemeinsame Auszahlung, das Restbudget und die fehlende Freigabe; die Beträge ergeben sich aus den tatsächlich gezogenen Eingaben."),
                new Eintrag("Mehrbedarfe nach § 30 SGB XII", "Auftrag 3, sofern im Topf",
                        "Gestreute Merkmale für Merkzeichen G, Schwangerschaft und Warmwasser. Die dokumentierten Rechenblöcke einschließlich Zwischenwerten werden unabhängig nachgerechnet; SQL prüft den persistierten Betrag, die Stufe, Budget und fehlende Freigabe."),
                new Eintrag("Familie / Alleinerziehende", "Auftrag 4, sofern im Topf",
                        "Alleinerziehende Person und Kinder als eigene SozialFälle. Der konkrete Kinderbestand bestimmt den Mehrbedarf. SQL prüft Auszahlung und Restbudget; die unabhängige Referenz prüft sämtliche dokumentierten Personen und Rechenwerte."),
                new Eintrag("Erwerbstätiger mit Freibetrag und Absetzbeträgen", "Auftrag 5, sofern im Topf",
                        "Gestreutes Erwerbseinkommen mit Absetzbeträgen. Freibetrag, anrechenbares Einkommen und Zahlbetrag werden mit einer unabhängigen BigDecimal-Referenz kontrolliert; SQL prüft Auszahlung, Budget, Stufe und fehlende Freigabe."),
                new Eintrag("Vermögen über dem Schonvermögen", "Auftrag 6, sofern im Topf",
                        "Gestreuter Vermögensbestand führt zur Auszahlung mit Verbrauchsfortschreibung oder zur erwarteten KEIN_ANSPRUCH-Abwehr. Vor und nach jedem Fehlversuch wird der gesamte Fachbestand einschließlich Versionen und Fachjournal verglichen."),
                new Eintrag("Doppelklick / Netzwerk-Wiederholung (Idempotenz)", "Auftrag 7, sofern im Topf",
                        "Derselbe Auftrag wird zweimal mit derselben Vorgangsnummer gesendet. Geprüft werden die Wiederholungsantwort, genau eine Zahlung, genau eine Zahlungsbuchung, nur einmal gemindertes Budget und keine unbeabsichtigte Freigabe."),
                new Eintrag("Storno und korrigierte Neuauszahlung im selben Monat", "Auftrag 8, sofern im Topf",
                        "Storno, Mietkorrektur und Neuanordnung für denselben Monat. Geprüft werden zwei Zahlungszeilen, alte STORNIERT-Zahlung, korrigierter Betrag, Restbudget und Fallversion. Vermögenseinsatz wird zusätzlich in Kombinationsfällen mit Storno geprüft."),
                new Eintrag("Konkurrenz beim Masken speichern (Lost-Update)", "Auftrag 9, sofern im Topf",
                        "Eine gültige Mietkorrektur wird gespeichert; ein veralteter Maskenstand muss danach KONKURRENZ auslösen. SQL prüft neue Miete und Version 2; der vollständige Vorher/Nachher-Vergleich sichert den abgewehrten Versuch ab."),
                new Eintrag("Kassenlauf mit eigener deterministischer Zahlung", "Auftrag 10 sowie jeder 50. Auftrag",
                        "Jeder Kassenauftrag bereitet eine eigene BEREIT-Zahlung vor und übergibt nur diesen Vorgang. Rückgabeanzahl, Summe, Zahlungsidentität, Freigebender und genau ein Journal unter einer eindeutigen Kennung müssen stimmen. Parallele fremde Freigaben verändern die Sollmenge nicht."),
                new Eintrag("Entbindungsmonat und Folgemonat", "Auftrag 11",
                        "Zwei tatsächliche Monatszahlungen mit Bescheid: Schwangerschaftsmehrbedarf gilt im Entbindungsmonat und entfällt danach. Beide Zahlbeträge, Vermögenseinsätze und das Endbudget werden per SQL geprüft."),
                new Eintrag("Vermögensverbrauch im Folgemonat", "Auftrag 12",
                        "10.500 EUR Vermögen bei einer erwachsenen Person: Einsatz 500 EUR nur im ersten Monat, danach null. Zwei Monatszahlungen, Restvermögen und Budget werden separat geprüft."),
                new Eintrag("Alleinerziehend mit vier Kindern", "Auftrag 13", "Feste vier minderjährige Kinder ohne U7-/U16-Merkmal: 48 % der RBS 1; vollständiger Haushalt und Zahlbetrag."),
                new Eintrag("Alleinerziehend mit fünf Kindern", "Auftrag 14", "Feste fünf minderjährige Kinder: 60 % der RBS 1; vollständiger Haushalt und Zahlbetrag."),
                new Eintrag("Alleinerziehend mit sechs Kindern", "Auftrag 15", "Feste sechs minderjährige Kinder: 72 % roh, auf 60 % der RBS 1 gekappt; vollständiger Haushalt und Zahlbetrag."),
                new Eintrag("Mehrbedarfsobergrenze Abs. 6", "Auftrag 16", "Synthetische RBS-2-Merkmalskombination: G, Schwangerschaft, sechs Kinder und dezentrales Warmwasser. Abs. 1–5 werden auf den Regelbedarf gekappt; Abs. 7 kommt hinzu. Technischer Rechenzweig, keine Behauptung juristischer Zulässigkeit aller Merkmale."),
                new Eintrag("Örtlicher zentraler Warmwasserabzug", "Auftrag 17", "Eigene unveränderliche Servicekonfiguration: 10 % der Heizkosten bei zentralem Warmwasser. Parameter und Abzug stehen im Rechenblock; keine globale Konfigurationsmutation."),
                new Eintrag("Gemeinsame KdU-Grenze", "Auftrag 18", "Zwei Personen mit zusammen 1.000 EUR KdU, BG-Grenze 600 EUR. Das Restlimit wird in Personenreihenfolge verteilt und unabhängig nachgerechnet."),
                new Eintrag("Minderjährige ohne Erwachsene", "Auftrag 19", "Produktvertrag: Grundbetrag einmal 10.000 EUR statt 500 EUR. Die unabhängigen Java-, JavaScript- und Python-Referenzen prüfen denselben ausdrücklich dokumentierten Vertrag."),
                new Eintrag("Kombinationsfälle (KOMBINATION) — systematisch gestreut", "Alle übrigen Aufträge ab 20; bei 500 Fällen: 471",
                        "Garantiert verschiedene Haushalte, Merkmale, Einkommen, Vermögen und Abläufe. Jeder numerische Rechenblock wird mit einer unabhängigen Referenz 2026 nachgerechnet. SQL prüft den geplanten Endzustand, Bescheid von/bis und bei Vermögens-Storno die Rückbuchung sowie den Einsatz beider Zahlungen."));
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
                new Eintrag("NEG-KOMBINATION — Verstoß-Kombinationen", "Rest des Topfs",
                        """
                        Der negative Kombinationsgenerator streut Abwehrfälle über vier Gruppen und kombiniert \
                        verträgliche Primär- und Sekundärverstöße: Anlage (ungültige IBAN mit falscher Prüfziffer \
                        oder Länge, BIC mit unzulässiger Länge), Anordnung (hohe Rente, hohes Erwerbseinkommen, \
                        Vermögen über Bedarf, zu kleines Startbudget, Anordnung ohne SozialFall, Zahlungsmonat \
                        außerhalb des erteilten Bescheids), Workflow (zweite Anordnung für denselben Monat, \
                        Selbstfreigabe, doppelte Freigabe, Storno nach Kassenübergabe) und Daten (umgekehrter oder \
                        überlanger Bescheid-Zeitraum, veralteter Maskenstand, Operationen auf unbekannten IDs). \
                        Erwartet werden die jeweiligen Fachfehler — und per SQL der saubere Datenbank-Endzustand: \
                        keine ungewollte Zahlung, Budget nur durch erlaubte Schritte verändert, Bescheid-Bestand \
                        exakt wie fachlich zulässig."""),
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
                        Dann bearbeitet Welle 2 dieselben User wieder. Jeder User hat einen eigenen Vorgang; \
                        eine reproduzierbare Konkurrenz zweier Mitarbeiter um dieselbe Zeile ist damit nicht \
                        nachgewiesen. Nach dem Lauf prüfen die Einzel- und globalen SQL-Abfragen Vorgangsanzahl, \
                        Version 2, exakt eine ANGELEGT-Zahlung je User ohne Freigabevermerk, Zahlungssumme gegen \
                        die erwarteten Anordnungen, vier Journalzeilen je User und nichtnegative Budgets. \
                        Sperrwartezeiten und Retry-Anzahlen werden nicht separat gemessen. Die Budgetkorrektheit \
                        wird je User gegen den Szenario-Sollwert geprüft; Lastberichte fassen die Ergebnisse zusammen."""),
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
