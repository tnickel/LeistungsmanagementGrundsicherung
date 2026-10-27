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

    /** Vorwort-Eintrag je Kategorie: Wozu braucht man die Kategorie, wie
     *  werden die Fälle erzeugt und wie werden sie geprüft. */
    private static Eintrag vorwort(String text) {
        return new Eintrag("Vorwort — Wozu, Erzeugung, Prüfung", "bitte zuerst lesen", text);
    }

    /** Katalog je Kategorie; {@code web} schaltet auf die Selenium-Varianten um. */
    static List<Eintrag> fuer(String kategorie, boolean web) {
        return switch (kategorie.toUpperCase(Locale.ROOT)) {
            case "POSITIV" -> web ? positivWeb() : positivFachschicht();
            case "NEGATIV" -> web ? negativWeb() : negativFachschicht();
            case "ECHT" -> echt();
            case "RECHNER" -> rechner();
            case "LAST" -> last();
            case "STRESS" -> stress();
            default -> List.of(new Eintrag("Unbekannte Kategorie", "",
                    "Für diese Kategorie ist kein Katalog hinterlegt."));
        };
    }

    // ------------------------------------------------------------------
    // ECHT — belegte echte Fälle aus Gesetz, Rechtsprechung, kommunaler
    // Richtlinie und Verbandsbeispielen (nur Fachschicht, 5 Fälle)
    // ------------------------------------------------------------------

    private static List<Eintrag> echt() {
        return List.of(
                vorwort("""
                        Wozu braucht man echte Fälle? Die Kategorie ECHT ergänzt synthetische Abläufe um
                        quellenbasierte Modellfälle. Veröffentlicht sind einzelne Beträge oder Regeln,
                        keine vollständigen geprüften Bescheide für die hier kombinierten Eingaben.
                        Wie werden die Fälle erzeugt? Fünf feste Konstellationen verbinden amtliche
                        Regelbedarfe, kommunale Richtwerte und Verbands-/Rechtsprechungsbelege mit
                        ausdrücklich ergänzten Eingaben. Die Vermögensfälle prüfen pauschale Modellwerte;
                        individuelle Privilegierungen und historische Übergangsregeln sind nicht modelliert.
                        Wie wird geprüft? Zahlbetrag, Vermögenseinsatz und Endvermögen der Erfolgsfälle
                        sind feste SQL-Sollwerte. Die dokumentierten Rechenblöcke verwenden dieselbe
                        Produktberechnung; eine getrennte arithmetische Referenz prüft diese zusätzlich.
                        Sie ersetzt keine Prüfung ausgelassener Fachregeln. Die Abwehrfälle
                        müssen KEIN_ANSPRUCH liefern und den Fachbestand unverändert lassen. JUnit
                        vergleicht die konkreten Ergebnisse und SQL-Sollwerte zusätzlich mit festen
                        Konstanten; nur Änderungen mit Wirkung auf diese Verträge werden erkannt.
                        ECHT gehört nicht zur automatischen Zertifikatssuite und nicht zur GLM-Auswahl."""),
                new Eintrag("ECHT-ANLAGE-KDU — amtliche Stufen + kommunale Richtwerte", "Auftrag 1",
                        """
                        Paar mit je RBS 2 = 506 EUR und KdU 320 + 100 + 110 EUR: handgerechnet
                        506 + 506 + 530 = 1.542,00 EUR. Eingangswerte aus der amtlichen Anlage:
                        https://www.gesetze-im-internet.de/sgb_12/anlage.html
                        Kommunale Richtwerte aus der Altenburger Richtlinienanlage (Beschluss 27.05.2026):
                        https://sessionnet.altenburgerland.de/bi/getfile.php?id=52833&type=do
                        Der zusammengesetzte Modellfall ist kein öffentlich geprüfter Einzelbescheid."""),
                new Eintrag("ECHT-BSG-VERMOEGEN — Modellvariante zu B 8 SO 4/24 R", "Auftrag 2",
                        """
                        Der Sekundärbericht zum BSG-Verfahren nennt 44.500 EUR Gesamtvermögen:
                        https://sozialrat.org/bsg-urteil-grundsicherung-alter-27-mai-2026-vermoegensuebergangsregel/
                        Rente 900 EUR ist frei ergänzt; KdU 260/90/110 folgt Altenburger Richtwerten.
                        Im pauschalen Modell: Bedarf 1.023 minus Rente 900 = 123 EUR Restbedarf;
                        Vermögenseinsatz 44.500 minus 10.000 = 34.500 EUR → KEIN_ANSPRUCH.
                        Keine Zahlung, Budget und Vermögen unverändert. Historischer Übergangstatbestand
                        und individuelle Privilegierungen sind nicht modelliert; 34.500 EUR ist kein
                        unabhängig bestätigter heutiger Verwertbarkeitswert. Amtlicher Volltext ungeprüft."""),
                new Eintrag("ECHT-SCHONVERMOEGEN-800 — Verbandsbaustein", "Auftrag 3",
                        """
                        Der Lebenshilfe-Ratgeber nennt das Beispiel „A hat 800 Euro auf dem Konto“:
                        https://www.lebenshilfe.de/informieren/arbeiten/grundsicherung-im-alter-und-bei-erwerbsminderung
                        Das Vermögen liegt unter dem Geldfreibetrag von 10.000 EUR. Mit ergänzten KdU
                        260 + 90 + 110 ergibt sich 563 + 460 = 1.023,00 EUR. Feste SQL-Sollwerte:
                        Zahlbetrag 1.023,00, Vermögenseinsatz 0,00, Endvermögen 800,00 EUR.
                        Der Ratgeber belegt den Vermögensbaustein, nicht den gesamten Modellbescheid."""),
                new Eintrag("ECHT-LSG-KFZ-PAAR — historischer Gesamtwert als Modellvariante", "Auftrag 4",
                        """
                        LSG Hamburg, 03.04.2020, L 4 SO 46/18, über den Lebenshilfe-Ratgeber dokumentiert:
                        https://www.lebenshilfe.de/informieren/arbeiten/grundsicherung-im-alter-und-bei-erwerbsminderung
                        Historischer Kfz-Gesamtwert 31.000 EUR. Die synthetische Variante behandelt die
                        Summe pauschal als Vermögen: 31.000 − 2 × 10.000 = 11.000 EUR gegenüber Bedarf
                        1.542 EUR → technische KEIN_ANSPRUCH-Abwehr ohne Bestandsänderung.
                        Heutiger Kfz-Schutz (§ 90 Abs. 2 Nr. 10), Einzelwerte und Angemessenheit sind
                        nicht modelliert. Eine heutige rechtliche Abweisung ist dadurch nicht bewiesen."""),
                new Eintrag("ECHT-KDU-DECKEL — gewählter Modell-Gesamtdeckel", "Auftrag 5",
                        """
                        Die Altenburger Richtlinie nennt ab 01.07.2026 für eine Person 390 EUR
                        Nettokaltmietdeckel; 90/110 EUR für Betrieb/Heizung sind Nichtprüfungsgrenzen
                        mit möglicher Einzelfallprüfung. Quelle:
                        https://sessionnet.altenburgerland.de/bi/getfile.php?id=52833&type=do
                        Hier wird daraus ein Modell-Gesamtdeckel 390 + 90 + 110 = 590 EUR gewählt:
                        min(500 + 90 + 110, 590) + 563 = 1.153,00 EUR im Juli 2026.
                        Keine vollständige Richtlinienprüfung: Der Gesamtdeckel ersetzt weder eine
                        komponentenweise Entscheidung noch eine Einzelfallprüfung höherer Kosten."""),
                new Eintrag("Prinzip aller ECHT-Fälle: feste Erwartungen und offene Modellgrenzen", "",
                        """
                        Feste Gesamtergebnisse und Teilwert-Erwartungen der Erfolgsfälle sind von den
                        produktberechneten Rechenblöcken getrennt. Quellen, freie Ergänzungen und Grenzen
                        stehen in doc/belege/echt-und-rechner-faelle-2026-10-27.md. In den recherchierten
                        Quellen wurde kein geeigneter vollständiger öffentlicher Fallbestand gefunden.
                        Daraus folgt weder dessen generelle Nichtexistenz noch ein pauschales Verbot
                        anonymisierter Veröffentlichungen. Diese fünf Modellfälle sind eigenständige
                        Fachschicht-Läufe; automatische Zertifikatssuite und GLM wählen sie nicht aus."""));
    }

    // ------------------------------------------------------------------
    // RECHNER — zwölf feste Vergleichsfälle, externe Stichprobe 1/6/12 belegt
    // ------------------------------------------------------------------

    private static List<Eintrag> rechner() {
        return List.of(
                vorwort("""
                        Wozu braucht man Rechner-Fälle? Feste, handrechenbare Endbeträge ergänzen die
                        Produktreferenz. Ein belegter externer Abgleich ist eine zusätzliche Gegenprobe;
                        er bestätigt nur die tatsächlich dokumentierten Eingaben und Ergebnisse.
                        Wie werden die Fälle erzeugt? Zwölf feste Single-/Paar-Konstellationen mit Rente,
                        Erwerbseinkommen und Merkzeichen G. Die ursprüngliche Zwölf-Fälle-Erhebung ist
                        nicht vollständig belegt. Eigene Browserbelege vom 27.10.2026 bestätigen Fälle
                        1, 6 und 12. Der Rechner (Rechtsstand 03.07.2026) ist erreichbar unter:
                        https://www.steuerschroeder.de/Steuerrechner/Grundsicherung.html
                        Wie wird geprüft? Feste SQL-Zahlbeträge 227,00 bis 1.427,00 EUR sowie feste
                        Vermögenswerte werden zusätzlich gegen separate JUnit-Konstanten geprüft.
                        Rechenblöcke stammen aus der Produktberechnung. Kein vollständiger externer
                        Zwölf-Fälle-Nachweis; RECHNER ist nicht Teil der Zertifikatssuite oder GLM-Auswahl."""),
                new Eintrag("RECHNER-Fälle 1–12 — feste Vergleichswerte", "alle Aufträge",
                        """
                        Acht Alleinstehenden- und vier Paar-Konstellationen prüfen feste Endbeträge
                        zwischen 227,00 und 1.427,00 EUR. Die unabhängige Handrechnung aller zwölf und
                        die externe Browserstichprobe 1/6/12 stehen im Review vom 27.10.2026.
                        Eingaben, Herkunft und Evidenzstatus je Fall:
                        doc/belege/echt-und-rechner-faelle-2026-10-27.md
                        Der alte als Fall-12-Ergebnis bezeichnete Screenshot zeigt kein Ergebnis und
                        gilt nicht als Vergleichsnachweis. Der feste Datenbestand bleibt reproduzierbar."""),
                new Eintrag("Bewusste Eingrenzungen", "",
                        """
                        Betriebskosten 0,00 EUR, zentrales Warmwasser, keine Absetzbeträge und kein
                        Vermögen. Kein § 82a-Grundrentenfreibetrag, keine zusätzliche Altersvorsorge
                        nach § 82 Abs. 4 und keine Witwenrenten-Sonderregeln. Die Übereinstimmung gilt
                        für diese Schnittmenge, nicht für die vollständige Rechtsanwendung.
                        Der externe Rechner ist eine Überschlagsrechnung und kein Bescheid-Ersatz.
                        Schwangerschaft, Entbindungsmonat und KdU-Modellparameter haben andere Tests."""));
    }

    // ------------------------------------------------------------------
    // POSITIV — Fachschicht (10 Grundarten, 9 feste Grenzfälle, Kombinationsgenerator)
    // ------------------------------------------------------------------

    private static List<Eintrag> positivFachschicht() {
        return List.of(
                vorwort("""
                        Wozu braucht man Positivfälle? Sie beweisen, dass der Normalbetrieb funktioniert: \
                        vom Anlegen des Vorgangs über die SGB-XII-Bedarfsberechnung bis zur Auszahlung, die korrekt \
                        in der Vier-Augen-Warteschlange landet. Erst wenn der Normalfall grün ist, haben Abwehr- und \
                        Lasttests überhaupt eine Basis — ein System, dessen Erfolgswege fehlschlagen, muss man nicht \
                        auf Fehlerfälle hin prüfen.
                        Wie werden die Fälle erzeugt? In drei Schichten: Zehn kuratierte Szenario-Arten (Aufträge 1–10) \
                        decken die Grundabdeckung ab, neun feste Grenzfälle (Aufträge 11–19, z. B. Entbindungsmonat, \
                        Alleinerziehende mit vier bis sechs Kindern, Mehrbedarfsobergrenze) sind handbereitet und \
                        unabhängig vom Zufall, und ab Auftrag 20 füllt ein Kombinationsgenerator den Topf mit \
                        garantiert verschiedenen Konstellationen (Signatur-Menge gegen Wiederholungen: Haushaltsform, \
                        Merkmale, Einkommen, Vermögen, Ablauf). Der Seed macht alles reproduzierbar; jeder 50. Auftrag \
                        ist ein Kassenlauf.
                        Wie wird geprüft? Der Simulator spielt die Schritte; die Produktberechnung liefert \
                        die dokumentierten Rechenblöcke. Eine getrennte arithmetische Referenz prüft die Blöcke; \
                        SQL prüft direkt auf den Oracle-Tabellen den Endzustand — \
                        Zahlbetrag, Status, Restbudget, Freigabevermerk, Journal. Am Laufende zusätzlich globale \
                        Prüfungen, z. B. „keine Übergabe ohne Freigabe“. Unabhängige Goldwerttests und eine \
                        gesonderte GLM-Stichprobe ergänzen die Prüfung. \
                        Jeder Rechenweg steht in der PDF-/Markdown-Testdokumentation."""),
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
                new Eintrag("Alleinerziehend mit vier Kindern", "Auftrag 13", "Vier minderjährige Kinder, davon ein U7- und zwei U16-Kinder: Vergleich 36 % gegen 48 % der RBS 1 ergibt 270,24 EUR. Altersgruppen Stufe 6/5/4/4; handgerechnete BG-Auszahlung 2.822,24 EUR per SQL."),
                new Eintrag("Alleinerziehend mit fünf Kindern", "Auftrag 14", "Fünf minderjährige Kinder, davon drei U16 und kein U7: 60 % schlagen die ebenfalls erfüllte 36-Prozent-Alternative. Stufen 5/5/5/4/4; handgerechnete BG-Auszahlung 3.312,80 EUR per SQL."),
                new Eintrag("Alleinerziehend mit sechs Kindern", "Auftrag 15", "Sechs minderjährige Kinder, davon ein U7- und drei U16-Kinder: 72 % roh, günstiger als 36 %, auf 60 % gekappt. Stufen 6/5/5/4/4/4; handgerechnete BG-Auszahlung 3.750,80 EUR per SQL."),
                new Eintrag("Mehrbedarfsobergrenze Abs. 6", "Auftrag 16", "Synthetische RBS-2-Merkmalskombination: G, Schwangerschaft, sechs Kinder und dezentrales Warmwasser. Abs. 1–5 werden auf den Regelbedarf gekappt; Abs. 7 kommt hinzu. Technischer Rechenzweig, keine Behauptung juristischer Zulässigkeit aller Merkmale."),
                new Eintrag("Zentrale Warmwasserkosten vollständig anerkennen", "Auftrag 17", "Altparameter 20 % bleibt einlesbar und wirkungslos. Angemessene Wohnkosten 350 + 50 + 100 EUR sind vollständig Heiz-/Unterkunftsbedarf (§ 35 Abs. 5); kein dezentraler Mehrbedarf. Handgerechnete Auszahlung 1.063 EUR per SQL; eigene unveränderliche Servicekonfiguration."),
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
                vorwort("""
                        Wozu braucht man Web-Positivfälle? Sie beweisen, dass Weboberfläche und Fachschicht exakt \
                        dasselbe System bedienen: Selenium steuert echte Chrome-Fenster wie ein Mensch (anmelden, \
                        Vorgangsmaske ausfüllen, Auszahlung anordnen, Kollegen-Browser gibt frei). Fehler im \
                        HTML-Escaping, in Feldformaten (z. B. type=month) oder in der Rollensteuerung sieht die \
                        Fachschicht nie — nur der echte Browser-Abgleich findet sie.
                        Wie werden die Fälle erzeugt? Drei kuratierte Arten in Zufallsverteilung: der Standardfall \
                        mit Kollegen-Freigabe, ein Mehrbedarfsfall mit Merkmals-Checkboxen und der Admin-Storno mit \
                        Rollenwechsel.
                        Wie wird geprüft? Jeder Schritt erzeugt einen Screenshot im Laufordner (Beweis, was wirklich \
                        im Browser stand), und SQL prüft danach denselben Datenbank-Endzustand wie in der Fachschicht: \
                        identischer Betrag, korrektes Restbudget, FREIGEGEBEN_VON durch den Kollegen."""),
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
                vorwort("""
                        Wozu braucht man Negativfälle? Für die Abwehrfähigkeit: Die Software muss unzulässige \
                        Operationen kontrolliert zurückweisen, statt sie halb zu buchen. Im Sozialleistungsbetrieb \
                        ist ein System, das bei Fehlern Daten verfälscht (halbe Zahlung, gemindertes Budget ohne \
                        Zahlung), unbrauchbar — jede Abwehr muss nachweisbar sauber sein.
                        Wie werden die Fälle erzeugt? Neun kuratierte Abwehrarten (Aufträge 1–9) decken die wichtigsten \
                        Fehlerklassen ab — von „Einkommen über Bedarf“ über Doppelanordnung je Monat und Selbstfreigabe \
                        bis zu ungültigen IBANs; ab Auftrag 10 kombiniert der negative Kombinationsgenerator verträgliche \
                        Verstöße aus vier Gruppen (Anlage, Anordnung, Workflow, Daten) mit garantiert verschiedener \
                        Signatur.
                        Wie wird geprüft? Der erwartete Fachfehler ist Teil des Szenarios. Nach jedem Fehlversuch \
                        vergleicht der Simulator den kompletten Fachbestand vorher/nachher (SHA-256 über alle \
                        Fachtabellen): keine neue Zahlung, Budget und Vermögen exakt unverändert, keine unerwünschten \
                        Journale. Erlaubte Vorbereitungen (z. B. die erste Zahlung vor der Doppelanordnung) bleiben \
                        ausdrücklich bestehen."""),
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
                vorwort("""
                        Wozu braucht man Web-Negativfälle? Die Oberfläche muss dieselben Regeln durchsetzen wie der \
                        Server: Ein Fehlversuch erzeugt einen verständlichen Dialog — und niemals eine halbe Buchung \
                        in der Datenbank. Hier werden UI-spezifische Risiken geprüft (Pflichtfelder, Rollenverteilung, \
                        Sitzungs-Token), die in der Fachschicht unsichtbar sind.
                        Wie werden die Fälle erzeugt? Sieben kuratierte Arten: falsches Passwort, fehlender \
                        Zahlungsmonat, für Sachbearbeiter vorenthaltener Storno-Knopf sowie Budgetüberschreitung, \
                        Doppelmonat und ungültige IBAN über die Maske.
                        Wie wird geprüft? Der Fehlerdialog wird als Screenshot im Laufordner belegt UND der \
                        Datenbank-Endzustand per SQL als unverändert nachgewiesen — die Oberfläche darf melden, \
                        aber nie halb buchen."""),
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
        return List.of(
                vorwort("""
                        Wozu braucht man den Lasttest? Funktionsprüfung unter paralleler Wiederholung: Viele Mitarbeiter \
                        laufen gleichzeitig gegen dieselben Oracle-Tabellen und Sequenzen. Das deckt Fehler auf, die im \
                        Einzelfalltest unsichtbar sind — Verbindungslecks, Pool-Erschöpfung, Budget-Racen. Ausdrücklich \
                        KEIN Kapazitäts- oder Dauerlastnachweis: Mindestdauer und Sättigungsstufen sind nicht definiert.
                        Wie werden die Fälle erzeugt? Als schlanke Einheitskette (Vorgang anlegen → SozialFall erfassen \
                        → Auszahlung anordnen, Status ANGELEGT) mit zufälligen Beträgen, kurzer Denkzeit, bewusst ohne \
                        Freigaben und Bescheide — maximaler Durchsatz bei vollem Fachweg.
                        Wie wird geprüft? Je Auftrag per SQL: Zahlung ANGELEGT mit dem vorab berechneten Betrag, \
                        Restbudget passend, Fall angelegt. Der Bericht ist bewusst gekürzt (Aggregate, Durchsatz und \
                        p50/p95/p99 statt Einzelseiten); Millisekundenwerte sind Laufkennzahlen, keine Zertifikate."""),
                new Eintrag("Lasttest — schlanker Massendurchlauf", "alle Aufträge",
                """
                Jeder Auftrag durchläuft die schlanke Kette Vorgang anlegen → SozialFall erfassen → Auszahlung \
                anordnen (Status ANGELEGT, bewusst ohne Freigabe und ohne Bescheid), mit kurzer Denkzeit und \
                zufälligen Beträgen. Geprüft wird je Auftrag der DB-Endzustand (Zahlung ANGELEGT mit dem \
                vorab berechneten Betrag, Restbudget passend, Fall angelegt) — der Bericht ist bewusst ein \
                gekürzter Gesamtbericht (lastreport) mit Durchsatz auf Millisekundenbasis und p50/p95/p99 \
                der Auftragsdauer statt Einzelfall-Seiten. Ziel ist die Funktionsprüfung unter paralleler \
                Wiederholung. Mindestlastdauer, Sättigungsstufen, Sperrwartezeiten und Retry-Anzahlen werden \
                nicht gemessen; ein grüner Lauf ist deshalb kein Kapazitäts- oder Dauerlastnachweis."""));
    }

    // ------------------------------------------------------------------
    // STRESS — zwei Wellen + Rahmen (bewusst 3 Einträge statt tausender Aufträge)
    // ------------------------------------------------------------------

    private static List<Eintrag> stress() {
        return List.of(
                vorwort("""
                        Wozu braucht man den Stresstest? Er prüft das Nachbearbeiten desselben Datensatzes unter Last: \
                        Anders als beim Lasttest greifen hier ZWEI Wellen auf dieselben User zu — Welle 2 findet jeden \
                        Vorgang aus Welle 1 wieder, ändert die Miete (das optimistische Locking muss die Fallversion \
                        sauber auf 2 heben) und ordnet dann die Auszahlung an. Die Phasenbarriere hält Welle 2 an, bis \
                        Welle 1 vollständig committet ist — sonst würde sich der Lauf selbst stören. Selenium läuft \
                        bewusst nicht (nur Fachschicht).
                        Wie werden die Fälle erzeugt? Aus N Usern werden 2×N Aufträge: Welle 1 (STRESS-ANLEGEN) legt \
                        je User Vorgang und SozialFall an, Welle 2 (STRESS-BEARBEITEN) ändert und zahlt. Regelbedarfs- \
                        stufen und Wohnkosten sind zufällig gestreut, die Mieterhöhung deterministisch — so ist jeder \
                        Sollbetrag vorab berechenbar.
                        Wie wird geprüft? Nach dem Lauf per SQL je User: genau ein Vorgang, SozialFall exakt Version 2, \
                        genau eine ANGELEGT-Zahlung mit dem NACH der Korrektur berechneten Betrag, korrektes Restbudget, \
                        vier Journalzeilen, keine negativen Budgets; die Lastberichte fassen zusammen. Wie beim Lasttest \
                        gilt: kein Kapazitäts- oder Dauerlastnachweis."""),
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
                        Mindestlastdauer und Sättigungsstufen sind nicht definiert; ein grüner Lauf ist kein \
                        Kapazitäts- oder Dauerlastnachweis. Sperrwartezeiten und Retry-Anzahlen werden nicht separat gemessen. Die Budgetkorrektheit \
                        wird je User gegen den Szenario-Sollwert geprüft; Lastberichte fassen die Ergebnisse zusammen."""),
                new Eintrag("Welle 1 — STRESS-ANLEGEN: Vorgang und Fall neu anlegen",
                        "N Aufträge (jeder Mitarbeiter bearbeitet einen Teil)",
                        """
                        Jeder Auftrag der ersten Welle legt einen eindeutigen Vorgang (Aktenzeichen SWEET-<Laufkennung>-S-…) mit \
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
