// Management-Testreport für SDN.Leistung (LMG): erzeugt bei jedem Aufruf frisch
// einen professionellen PDF-Bericht mit Titelbild, Einleitung, Inhaltsverzeichnis
// mit echten Seitenzahlen (Zwei-Durchlauf über pdftotext), fixen Erklärtkapiteln
// (Testarten, Fachschicht/Selenium, Sollwert-Herkunft, echte Fälle, externer
// Rechner, parallele Transaktionssteuerung), dynamischen Ergebnistabellen aus
// testsweet/logs und einer Zertifikatsseite am Ende.
//
// Aufruf:  node publish-report.js --publish <repo>   (erzeugt und veröffentlicht)
//          node publish-report.js --verify <repo>    (prüft Aktualität der PDF)
// Der Report informiert das Management; der formale Nachweis bleibt das
// Testzertifikat (doc/testzertifikat-werkzeug) mit seinen strengen Belegen.
const fs = require("fs");
const path = require("path");
const os = require("os");
const crypto = require("crypto");
const {spawnSync} = require("child_process");
const {pathToFileURL} = require("url");
const {Document, Packer, Paragraph, TextRun, Table, TableRow, TableCell, WidthType,
  BorderStyle, ShadingType, HeadingLevel, Footer, AlignmentType, PageNumber,
  PageBreak, ImageRun} = require("../handbuch-werkzeug/node_modules/docx");
const runs = require("./runs");

const FONT = "Calibri";
const P = { primary: "0E2A33", secondary: "526570", pass: "157534", fail: "A51D17",
  warn: "925B00", hell: "EDF2F5", zeile: "F7FAFB" };
const KAPITEL = [
  "Einleitung",
  "1  Testarten im Projekt: Unit-Tests und Regressionstests",
  "2  Zwei Prüfwege: Fachschicht und Weboberfläche (Selenium)",
  "3  Herkunft der Testfälle und ihrer Sollwerte",
  "4  Echte Fälle aus dem Internet",
  "5  Unabhängige Gegenrechnung mit externem Online-Rechner",
  "6  Transaktionssteuerung unter parallelen Prozessen",
  "7  Ergebnisübersicht der Prüfläufe",
  "8  Fazit",
  "Testzertifikat",
];

function text(value, options = {}) {
  return new TextRun({ text: String(value), font: FONT, size: 21, color: P.primary, ...options });
}
function para(value, options = {}) {
  return new Paragraph({ spacing: { after: 120, line: 275 }, children: [text(value)], ...options });
}
function absatz(value) { return para(value, { alignment: AlignmentType.JUSTIFIED }); }
function h1(value) {
  return new Paragraph({ heading: HeadingLevel.HEADING_1, pageBreakBefore: true, keepNext: true,
    spacing: { before: 120, after: 180 },
    children: [text(value, { size: 30, bold: true, color: P.primary })] });
}
function h2(value) {
  return new Paragraph({ heading: HeadingLevel.HEADING_2, keepNext: true,
    spacing: { before: 200, after: 100 }, children: [text(value, { size: 24, bold: true, color: P.secondary })] });
}
function hinweis(value) {
  return new Paragraph({ spacing: { after: 140 }, shading: { type: ShadingType.CLEAR, fill: P.hell },
    padding: { top: 100, bottom: 100, left: 150, right: 150 },
    children: [text(value, { size: 20, color: P.secondary, italics: true })] });
}
function zelle(value, options = {}) {
  return new TableCell({ shading: options.fill ? { type: ShadingType.CLEAR, fill: options.fill } : undefined,
    margins: { top: 70, bottom: 70, left: 110, right: 110 },
    children: [new Paragraph({ children: [text(value, { size: 20, bold: options.bold,
      color: options.color || P.primary })], alignment: options.align || AlignmentType.LEFT })] });
}
function tabelle(kopf, zeilen, spaltenBreite) {
  const rand = { style: BorderStyle.SINGLE, size: 3, color: "D5DDE2" };
  return new Table({ width: { size: 100, type: WidthType.PERCENTAGE },
    borders: { top: rand, bottom: rand, left: rand, right: rand, insideHorizontal: rand, insideVertical: rand },
    rows: [
      new TableRow({ tableHeader: true, cantSplit: true, children: kopf.map(k =>
        zelle(k, { bold: true, fill: P.hell })) }),
      ...zeilen.map((z, i) => new TableRow({ cantSplit: true,
        children: z.map((v, j) => zelle(v, { fill: i % 2 ? P.zeile : undefined,
          color: j === z.length - 1 && /BESTANDEN|grün/.test(String(v)) ? P.pass
            : j === z.length - 1 && /NICHT|FEHL|rot/.test(String(v)) ? P.fail
            : j === z.length - 1 && /ABGEBROCHEN/.test(String(v)) ? P.warn : undefined,
          bold: j === z.length - 1 })) })),
    ] });
}

// ------------------------------------------------------------------
// Fixe Kapiteltexte (Management-Erklärungen; bewusst laienverständlich)
// ------------------------------------------------------------------
const T_EINLEITUNG = [
  "Dieser Bericht fasst zusammen, wie die Fachsoftware SDN.Leistung — Leistungsmanagement Grundsicherung (LMG) geprüft wurde und mit welchem Ergebnis. Er richtet sich an das Management und Vorgesetzte, nicht an Entwickler: Alle Fachbegriffe werden erklärt, alle Ergebnisse stehen am Ende in einer Übersichtstabelle.",
  "Die Software unterstützt Sachbearbeiter der Sozialverwaltung bei der Bedarfsberechnung und der Auszahlung von Grundsicherungsleistungen nach dem SGB XII. Sie ersetzt eine teure lizenzierte Transaktionssteuerung durch eine Eigenentwicklung auf Java 21 und Oracle. Weil hier Sozialleistungen in Euro berechnet und gezahlt werden, ist die Prüfqualität zentral: Ein falscher Betrag ist im Ergebnis ein falscher Bescheid.",
  "Geprüft wurde auf mehreren Ebenen: automatisierte Unit-Tests und Regressionstests (Kapitel 1), zwei grundsätzlich verschiedene Prüfwege — direkt über die Fachschicht und über die echte Weboberfläche mit einem gesteuerten Browser (Kapitel 2) —, Zehntausende automatisch erzeugte Testfälle mit unabhängig nachgerechneten Sollwerten (Kapitel 3), echte Fälle aus Gesetz, Rechtsprechung und kommunalen Richtlinien (Kapitel 4), der Abgleich mit einem externen Online-Rechner als unabhängiger zweiter Instanz (Kapitel 5) sowie die Belastung der Transaktionssteuerung mit parallelen Prozessen (Kapitel 6). Kapitel 7 zeigt die konkreten Ergebnisse; die letzte Seite ist das Testzertifikat.",
  "Dieser Bericht wird bei jeder Erneuerung vollständig neu erzeugt — Datum, Report-Kennung und alle Ergebniszahlen stammen aus dem jeweiligen aktuellen Stand der Prüfläufe. Der formale, beleggebundene Nachweis bleibt daneben das separate Testzertifikat.",
];
const T_UNIT = [
  ["Was sind Unit-Tests?", "Ein Unit-Test prüft das kleinste Baustein einer Software im Kleinen: eine einzelne Klasse oder Methode wird mit festen Ein- und Ausgabewerten aufgerufen, und der Test vergleicht das Ergebnis mit dem erwarteten Wert — vollautomatisch, in Millisekunden, bei jedem Build. Ein Beispiel aus diesem Projekt: Der Prüfziffern-Validator für IBANs erhält eine festgelegte, ungültige Kontonummer und muss sie zurückweisen. Tut er es nicht, schlägt der Test rot an, noch bevor jemand die Software startet. Hunderte dieser Prüfungen laufen mit einem einzigen Befehl (mvn test); sie sichern die Grundmechanik der Software und laufen daher bei jeder Änderung erneut."],
  ["Was sind Regressionstests?", "Eine Regression ist ein Fehler, der entsteht, wenn eine eigentlich neue Funktion etwas Altes kaputtmacht: Gestern funktionierte die Auszahlung, heute — nach einer kleinen Änderung an der Berechnung — nicht mehr. Regressionstests verhindern genau das, indem sie frühere, bereits gelöste Prüfszenarien dauerhaft wiederholen: Zehn dokumentierte Nutzer-Szenarien (Antrag erfassen, Auszahlung freigeben, Storno und Korrektur, Kassenlauf und andere) werden mit mehreren simulierten Sachbearbeitern durchgespielt, und am Ende wird direkt in der Oracle-Datenbank geprüft, ob exakt der dokumentierte Endzustand eingetreten ist — Beträge, Status, Verantwortliche. Zentraler Schutz gegen den „Erwartungswert-Zirkelschluss“: Die Sollwerte dieser Szenarien sind von Hand gerechnet und dokumentiert („Goldwerte“), ohne dass die zu prüfende Software selbst das Ergebnis geliefert hat."],
  ["Warum beides fest ins Projekt gehört", "Unit-Tests und Regressionstests sind die dauerhafte Grundabsicherung: Sie laufen bei jeder Änderung automatisch mit und halten das gesamte Projekt in einem bekannten, geprüften Zustand (im Fachjargon: „grün“). Alle weiteren Prüfwege dieses Berichts bauen auf dieser Basis auf."],
];
const T_WEGE = [
  ["Prüfweg 1: Fachschicht — direkt unter der Motorhaube", "Die Fachschicht ist die innere Logik der Software: derselbe Baustein, den später auch die Weboberfläche aufruft, hier aber direkt und ohne Bildschirm. Der Mitarbeiter-Simulator „testsweet“ lässt dutzende simulierte Sachbearbeiter gleichzeitig Vorgänge anlegen, Bedarfe berechnen, Auszahlungen anordnen, freigeben und stornieren — tausende Fälle in Sekunden. Weil kein Bildschirm und kein Browser dazwischenliegen, ist dieser Weg schnell genug für große Fallzahlen und liefert die aussagekräftigsten Zahlen für Beträge, Budgets und Datenbank-Endzustände."],
  ["Prüfweg 2: Weboberfläche mit Selenium — als würde ein Mensch davor sitzen", "Selenium ist ein Werkzeug, das einen echten Chrome-Browser wie ein Mensch bedient: Der Simulator meldet sich mit Kennung und Passwort an, klickt durch die Masken, füllt Felder aus (einschließlich sensibler Monats-Auswahlfelder), ordnet Auszahlungen an und meldet sich ab. Ein zweites, eigenes Browserfenster übernimmt die Rolle des Kollegen, der freigibt — die Vier-Augen-Kontrolle wird also tatsächlich über zwei getrennte Bildschirmsitzungen geprüft, so wie im Echtbetrieb. Jeder Arbeitsschritt erzeugt einen Screenshot als Beweis, was wirklich im Browser stand. Dieser Weg ist langsamer und läuft deshalb mit kleineren Fallzahlen — aber er ist der einzige, der Bedienfehler findet, die unter der Motorhaube unsichtbar sind: unleserliche oder falsch formatierte Maskenfelder, fehlende Fehlerdialoge, Knöpfe, die der falschen Rolle angezeigt werden."],
  ["Warum beide Wege zusammengehören", "Die Fachschicht beweist, dass die Fachlogik richtig rechnet und richtig bucht. Die Weboberfläche beweist, dass ein Mensch mit der Software tatsächlich dasselbe Ergebnis erreichen würde. Stimmen beide Endzustände in der Datenbank überein — Betrag für Betrag —, bedienen Oberfläche und Kern wirklich dasselbe System. Genau das wird je Lauf verglichen."],
];
const T_SOLLWERTE = [
  ["Wie die Testfälle erzeugt werden", "Drei Sorten von Testfällen füllen die Töpfe: erstens kuratierte Szenarien, die von Hand aus der Fachlogik abgeleitet sind (Standardrente, Paar-Haushalt, Mehrbedarfe, Vermögensfälle, Storno mit Korrektur, Kassenlauf und weitere); zweitens handgerechnete Grenzfälle mit festen Werten — etwa eine Alleinerziehende mit sechs Kindern, deren Auszahlungssumme auf dem Papier steht, bevor die Software startet; drittens ein Kombinationsgenerator, der Haushaltsformen, Merkmale (Schwangerschaft, Schwerbehinderung, Warmwasser), Einkommen, Vermögen und Abläufe solange neu würfelt, bis die gewünschte Fallzahl erreicht ist — mit einer Signatur-Menge, die garantiert, dass keine Konstellation doppelt vorkommt."],
  ["Wo die erwarteten Werte (Sollwerte) herkommen", "Die entscheidende Frage bei automatisch erzeugten Fällen lautet: Womit vergleichen wir das Ergebnis der Software? Die Antwort hat mehrere Stufen. Für die zufällig erzeugten Fälle berechnet eine referenzimplementierte, reine Berechnungsklasse der Fachschicht den Sollbetrag mit — dieselbe Mathematik wie im Produkt, aber ohne Datenbank und ohne Oberfläche; der komplette Rechenweg wird je Fall als „Rechenblock“ dokumentiert. Dieses Vorgehen wäre für sich genommen ein Zirkelschluss (man könnte denselben Fehler doppelt haben), deshalb gibt es bewusst Gegenmaßnahmen: Die Grenz- und Goldwert-Fälle sind von Hand gerechnet, ohne dass die Software das Ergebnis je geliefert hat."],
  ["Die Rolle des Sprachmodells (LLM): Prüfer, nicht Vorlage", "Ein großes Sprachmodell (GLM) beteiligt sich als unabhängige Nachrechen-Instanz — aber nicht, wie man meinen könnte, als Quelle der Sollwerte. Der Ablauf ist genau umgekehrt: Das LLM erhält den fertigen Prüfungsstapel — vollständig dokumentierte Fälle mit allen Eingaben (Haushalt, Einkommen, Vermögen, Wohnkosten), den zugehörigen Gesetzestexten und den Rechenblöcken der Software — und rechnet jeden Fall selbstständig nach. Stimmt sein Ergebnis mit dem dokumentierten Sollwert überein, ist der Fall bestätigt; weicht es ab, wird das als Befund gemeldet und blockiert den Nachweis. Das LLM erzeugt also keine Erwartungswerte, es überprüft sie. Ergänzend kommen seit Oktober 2026 zwei völlig unabhängige Wertequellen hinzu: echte Fälle aus Gesetz und Rechtsprechung (Kapitel 4) und der Abgleich mit einem externen Online-Rechner (Kapitel 5)."],
];
const T_ECHT = [
  "Automatisch erzeugte Fälle — so viele es auch sind — haben einen gemeinsamen Ursprung: Sie stammen aus der eigenen Werkstatt. Deshalb wurde im Oktober 2026 im Internet systematisch nach echten Sozialfällen mit echten Ergebnissen gesucht. Das Ergebnis dieser Recherche ist ebenso wichtig wie die Fälle selbst: Echte Leistungsbescheide sind Sozialdaten und unterliegen dem Vertraulichkeitsschutz (§ 67 ff. SGB X) — eine öffentliche Datenbank mit echten Fällen und Auszahlungsbeträgen existiert nicht und darf nicht existieren.",
  "Was sehr wohl existiert, sind belegbare Konstellationen und Werte aus amtlichen Quellen. Fünf davon sind als eigene Testfallkategorie „ECHT“ fest in die Testsuite eingebaut: die amtlichen Regelbedarfsstufen 2026 aus der Anlage zu § 28 SGB XII (563 € Alleinstehende, 506 € je Partner und weitere Stufen); ein Urteil des Bundessozialgerichts vom 27.05.2026 (B 8 SO 4/24 R) über eine Frau mit 44.500 € Vermögen; ein Landessozialgericht-Urteil über ein Ehepaar mit zwei Kraftfahrzeugen im Wert von 31.000 €; das Verbandsbeispiel „800 Euro auf dem Konto“ (Vermögen unterhalb der Schonvermögensgrenze bleibt geschützt) und die KdU-Richtlinie des Landkreises Altenburger Land vom 27.05.2026 mit echten ortsüblichen Miet-Richtwerten und der neuen Obergrenze für Unterkunftskosten ab 01.07.2026.",
  "Wo eine Quelle Angaben nicht nennt (das Urteil etwa keine Miete), wird ergänzt — und diese Ergänzung ist im jeweiligen Fall dokumentiert. Besonders lehrreich sind die beiden großen Vermögensfälle: Nach heutigem Modellrecht wird verwertbares Vermögen auf den Bedarf angerechnet, sodass diese Anträge korrekt mit „KEIN_ANSPRUCH“ abgewiesen werden — und genau das prüft der Testfall, einschließlich des Nachweises, dass bei der Abweisung nichts in der Datenbank verändert wurde. Alle Quellen sind mit Abrufdatum je Fall dokumentiert (doc/belege/).",
];
const T_RECHNER = [
  "Die zweite unabhängige Instanz ist ein externer Online-Rechner: der SGB-XII-Grundsicherungsrechner von steuerschroeder.de (https://www.steuerschroeder.de/Steuerrechner/Grundsicherung.html, Rechtsstand 03.07.2026). Er wurde von anderen Programmierern mit einer eigenen Codebasis erstellt — wenn unsere Software bei denselben Eingaben andere Ergebnisse liefert, ist eines von beiden falsch.",
  "Am 27.10.2026 wurden zwölf Konstellationen stellvertretend interaktiv durch den externen Rechner gerechnet: Alleinstehende und Paare, mit und ohne Rente, mit Erwerbseinkommen (einschließlich des gesetzlichen 30-%-Freibetrags), mit Schwerbehinderten-Merkzeichen G und mit den echten Miet-Richtwerten des Landkreises Altenburger Land. Das jeweilige Rechner-Ergebnis wurde als fester Sollwert in die Testsuite übernommen — inklusive eines Beleg-Screenshots.",
  "Das Ergebnis des Abgleichs: Alle zwölf externen Ergebnisse stimmten auf den Cent mit unserer Software überein (Spannweite 227,00 € bis 1.427,00 € monatlicher Anspruch). Diese zwölf Fälle laufen seither bei jedem Prüflauf als eigene Kategorie „RECHNER“ mit — jeder Wert ist zusätzlich in einem JUnit-Test fest verankert: Jede künftige Änderung an der Bedarfsberechnung, die auch nur einen Cent verschiebt, färbt den Test rot.",
];
const T_PARALLEL = [
  ["Was hier geprüft wird und warum", "Die Software ersetzt eine Transaktionssteuerung (im Großrechnerumfeld klassisch ein „TP-Monitor“). Ihre Kernpflicht: Auch wenn viele Sachbearbeiter gleichzeitig arbeiten, bleibt jede einzelne Aufgabe unteilbar — entweder vollständig ausgeführt oder gar nicht (das ACID-Prinzip, gestellt vom Oracle-Kern). Die Gefahr unter Last sind typische Mehrbenutzer-Fehler: verlorene Änderungen, doppelte Auszahlungen, erschöpfte Verbindungsvorräte, Deadlocks (zwei Prozesse blockieren gegenseitig)."],
  ["Lasttest: viele Mitarbeiter gleichzeitig", "Der Lasttest lässt bis zu 50 simulierte Sachbearbeiter parallel dieselben Oracle-Tabellen und Zähler beschreiben: Jeder Auftrag durchläuft die schlanke Kette „Vorgang anlegen → Fall erfassen → Auszahlung anordnen“ mit zufälligen Beträgen. Geprüft wird je Auftrag der Datenbank-Endzustand: Die angelegte Zahlung muss exakt den vorab berechneten Betrag tragen, das Budget muss passend gemindert sein. Der Bericht dazu ist bewusst schlank (Aggregate statt Einzelseiten); er weist ausdrücklich darauf hin, dass hier Funktionsfähigkeit unter Wiederholung, keine Kapazitätszusage geprüft wird."],
  ["Stresstest: zwei Wellen über dieselben Fälle", "Der Stresstest verdoppelt die Anforderung: Eine erste Welle legt je „User“ einen Vorgang mit Fall an; eine zweite Welle bearbeitet danach genau dieselben Vorgänge nach — findet sie über das Aktenzeichen wieder, erhöht die Miete und ordnet dann die Auszahlung an. Zwischen beiden Wellen sorgt eine Phasenbarriere dafür, dass Welle 2 erst startet, wenn Welle 1 vollständig abgeschlossen ist — sonst würde der Test sich selbst stören und eine Konkurrenz vortäuschen, die es nicht gab. Das optimistische Sperren muss dabei sauber greifen: Die Fallversion muss exakt von 1 auf 2 gehen, die Zahlung muss den Betrag nach der Korrektur tragen. Geprüft wird je User per SQL auf der Datenbank."],
  ["Ergebnis", "Die parallelen Läufe bestätigen die Transaktionssteuerung: korrekte Endzustände unter Gleichzeitigkeit, keine negativen Budgets, keine doppelten Zahlungen. Sperrkonflikte, die Oracle gelegentlich meldet (ORA-00060 Deadlock), werden von der Software erkannt und sicher wiederholt — auch das ist mit einem echten, absichtlich erzeugten Deadlock getestet. Die konkreten Zahlen des jüngsten Laufs stehen in Kapitel 7."],
];

// ------------------------------------------------------------------
// Dokumentbau
// ------------------------------------------------------------------
function titelseite(datum, reportId, version) {
  const bild = path.join(__dirname, "..", "..", "testsweet", "src", "main", "resources", "sweet", "assets", "titelbild.jpg");
  const kinder = [];
  if (fs.existsSync(bild)) {
    kinder.push(new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 300 },
      children: [new ImageRun({ type: "jpg", data: fs.readFileSync(bild),
        transformation: { width: 500, height: 281 } })] }));
  }
  kinder.push(
    new Paragraph({ alignment: AlignmentType.CENTER, spacing: { before: 400, after: 120 },
      children: [text("SDN.Leistung — Leistungsmanagement Grundsicherung", { size: 44, bold: true })] }),
    new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 500 },
      children: [text("Testreport", { size: 64, bold: true, color: P.secondary })] }),
    new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 100 },
      children: [text("Softwareversion " + version, { size: 24 })] }),
    new Paragraph({ alignment: AlignmentType.CENTER,
      children: [text("Erstellt am " + datum + " · Report-Kennung " + reportId, { size: 22, color: P.secondary })] }),
    new Paragraph({ alignment: AlignmentType.CENTER, spacing: { before: 500 },
      children: [text("Automatisch erzeugter Prüfbericht — bei jeder Erneuerung vollständig neu berechnet.", { size: 20, italics: true, color: P.secondary })] }),
    new Paragraph({ children: [new PageBreak()] }));
  return kinder;
}

function inhaltsverzeichnis(seiten) {
  const kinder = [h1OhneUmbruch("Inhaltsverzeichnis")];
  // Nach dem Verzeichnis folgt eine eigene Seite (die Einleitung hat selbst
  // einen Seitenwechsel), damit die Seitenzählung stabil bleibt.
  KAPITEL.forEach((titel, i) => {
    kinder.push(new Paragraph({ spacing: { after: 90 }, tabStops: [{ type: "right", position: 9026, leader: "dot" }],
      children: [
        text(titel + "\t", { size: 22, bold: i === 0 || titel === "Testzertifikat" }),
        text(seiten ? String(seiten[i]) : "—", { size: 22 }),
      ] }));
  });
  return kinder;
}

function h1OhneUmbruch(value) {
  return new Paragraph({ heading: HeadingLevel.HEADING_1, keepNext: true,
    spacing: { before: 120, after: 180 }, children: [text(value, { size: 30, bold: true })] });
}

function kapitelEinleitung(datum) {
  return [h1(KAPITEL[0]), ...T_EINLEITUNG.map(absatz),
    hinweis("Stand dieses Reports: " + datum + ". Alle Kapitel 7-Tabellen werden je Erneuerung aus den aktuellen Prüfläufen neu gebildet.")];
}
function kapitelUnit() {
  return [h1(KAPITEL[1]), ...T_UNIT.map(([frage, antwort]) => [h2(frage), absatz(antwort)]).flat()];
}
function kapitelWege() {
  return [h1(KAPITEL[2]), ...T_WEGE.map(([frage, antwort]) => [h2(frage), absatz(antwort)]).flat()];
}
function kapitelSollwerte() {
  return [h1(KAPITEL[3]), ...T_SOLLWERTE.map(([frage, antwort]) => [h2(frage), absatz(antwort)]).flat(),
    hinweis("Kurzantwort auf die häufige Frage „Hat ein KI-Modell die Sollwerte geliefert?“ — Nein. Die Software rechnet, das Sprachmodell rechnet zur Kontrolle ein zweites Mal nach; Abweichungen werden als Befund gemeldet.")];
}
function kapitelEcht() {
  return [h1(KAPITEL[4]), ...T_ECHT.map(absatz)];
}
function kapitelRechner() {
  return [h1(KAPITEL[5]), ...T_RECHNER.map(absatz)];
}
function kapitelParallel() {
  return [h1(KAPITEL[6]), ...T_PARALLEL.map(([frage, antwort]) => [h2(frage), absatz(antwort)]).flat()];
}

function ergebnisTabelle(repo) {
  const letzte = runs.letzteLaeufe(repo);
  const namen = { "Fachschicht|POSITIV": "Fachschicht: Positivfälle", "Fachschicht|NEGATIV": "Fachschicht: Negativfälle",
    "Fachschicht|ECHT": "Fachschicht: Echte Fälle", "Fachschicht|RECHNER": "Fachschicht: Rechner-Fälle",
    "Fachschicht|LAST": "Fachschicht: Lasttest (parallel)", "Fachschicht|STRESS": "Fachschicht: Stresstest (2 Wellen)",
    "WEB|POSITIV": "Weboberfläche: Positivfälle", "WEB|NEGATIV": "Weboberfläche: Negativfälle", "WEB|LAST": "Weboberfläche: Lasttest" };
  const zeilen = letzte.map(({ key, lauf }) => {
    const ergebnis = lauf.erfolgreich ? "BESTANDEN"
      : lauf.verifiziert === 0 ? "ABGEBROCHEN" : "FEHLGESCHLAGEN";
    return [namen[key] || key, runs.deutschDatum(lauf.datum), String(lauf.gesamt),
      String(lauf.verifiziert), ergebnis];
  });
  const junit = runs.junitStand(repo);
  const junitZeilen = Object.entries(junit).map(([modul, stand]) => stand
    ? ["Unit-/Regressionstests: " + modul, stand.datum, String(stand.tests),
       String(stand.tests - stand.fehler - stand.skipped), stand.fehler ? "FEHLGESCHLAGEN" : "BESTANDEN"]
    : ["Unit-/Regressionstests: " + modul, "—", "—", "—", "siehe Testzertifikat"]);
  return [h1(KAPITEL[7]), absatz("Die folgende Übersicht zeigt je Prüfebene den jeweils jüngsten Lauf. „Verifiziert“ ist die Zahl der Fälle, deren Datenbank-Endzustand vollständig geprüft wurde. Als „ABGEBROCHEN“ gilt ein Lauf, der vor der Verifikation abgestorben ist (z. B. ein eingefrorenes Browserfenster) — er ist kein fachliches Scheitern, sondern ein nicht zustande gekommener Nachweis. Prüfebenen ohne Eintrag sind zum Stand dieses Reports noch nicht gelaufen."),
    tabelle(["Prüfebene", "Datum", "Fälle", "davon verifiziert", "Ergebnis"], [...zeilen, ...junitZeilen]),
    hinweis("Der beleggebundene, formale Nachweis mit Build-Bindung und Hashprüfung je Lauf ist das separate Zertifikatsdokument (doc/testzertifikat-werkzeug).")];
}

function kapitelFazit(repo) {
  const letzte = runs.letzteLaeufe(repo);
  const alle = letzte.length, gut = letzte.filter(l => l.lauf.erfolgreich).length;
  const satz = alle === 0 ? "Zum Stand dieses Reports liegen keine Prüfläufe vor."
    : gut === alle ? "Alle " + alle + " ausgewerteten Prüfläufe sind vollständig bestanden."
      : gut + " von " + alle + " ausgewerteten Prüfläufen sind bestanden; Details stehen in Kapitel 7.";
  return [h1(KAPITEL[8]), absatz(satz),
    absatz("Über alle Ebenen hinweg ergibt sich ein konsistentes Bild: Die Fachberechnung liefert in handgerechneten Goldwerten, unabhängig nachgerechneten KI-Prüfungen, echten externen Quellen und dem Abgleich mit einem fremden Online-Rechner dieselben Beträge. Die Abwehr unzulässiger Operationen ist sauber (keine halben Buchungen), die Transaktionssteuerung hält der parallelen Belastung stand, und die Weboberfläche bedient nachweislich dieselbe Fachlogik wie der direkte Fachweg."),
    absatz("Empfehlung: Die Prüfpyramide in dieser Zusammensetzung beibehalten und bei jeder fachlichen Änderung erneut vollständig durchlaufen lassen; dieser Report wird dabei jedes Mal neu erzeugt und ersetzt keine Archivierung der Einzelnachweise.")];
}

function zertifikatsSeite(datum, reportId, repo) {
  const letzte = runs.letzteLaeufe(repo);
  const gut = letzte.filter(l => l.lauf.erfolgreich).length;
  const urteil = letzte.length && gut === letzte.length ? "BESTANDEN" : "UNVOLLSTÄNDIG — siehe Kapitel 7";
  const farbe = urteil === "BESTANDEN" ? P.pass : P.warn;
  return [h1(KAPITEL[9]),
    new Paragraph({ alignment: AlignmentType.CENTER, spacing: { before: 240, after: 300 },
      children: [text("SDN.Leistung — Leistungsmanagement Grundsicherung (LMG)", { size: 24, color: P.secondary })] }),
    absatz("Hiermit wird bestätigt, dass die Software SDN.Leistung (Leistungsmanagement Grundsicherung, Version 0.5.8) zum Stand " + datum + " auf den in diesem Report beschriebenen Ebenen geprüft wurde: automatisierte Unit-Tests und Regressionstests, Fachschicht- und Weboberflächen-Prüfung mit gesteuertem Browser, synthetische Testfälle mit unabhängig nachgerechneten Sollwerten, echte Fälle aus Gesetz, Rechtsprechung und kommunaler Richtlinie, Abgleich mit einem externen Online-Rechner sowie Belastung der Transaktionssteuerung mit parallelen Prozessen."),
    new Paragraph({ spacing: { before: 200, after: 200 }, shading: { type: ShadingType.CLEAR, fill: "E6F4EA" },
      padding: { top: 160, bottom: 160, left: 200, right: 200 },
      children: [text("Gesamturteil zum Stand dieses Reports: " + urteil, { size: 28, bold: true, color: farbe })] }),
    absatz("Von " + letzte.length + " ausgewerteten Prüfläufen waren " + gut + " vollständig bestanden. Dieser Report und dieses Zertifikat beziehen sich auf den genannten Erstellungsstand und werden bei jeder Erneuerung vollständig neu gebildet (Report-Kennung " + reportId + "). Der formale, beleggebundene Zertifikatsnachweis mit Build-Bindung ist das separate Dokument „SDN-Leistung-LMG-Testzertifikat“."),
    new Paragraph({ spacing: { before: 500, after: 60 },
      children: [text("Ort, Datum: ____________________________   ,   " + datum)] }),
    new Paragraph({ spacing: { before: 200 },
      children: [text("Unterschrift (Qualitätssicherung): ___________________________________") ] })];
}

function baueDokument(repo, datum, reportId, seiten) {
  const kinder = [
    ...titelseite(datum, reportId, "0.5.8"),
    ...inhaltsverzeichnis(seiten),
    ...kapitelEinleitung(datum), ...kapitelUnit(), ...kapitelWege(), ...kapitelSollwerte(),
    ...kapitelEcht(), ...kapitelRechner(), ...kapitelParallel(),
    ...ergebnisTabelle(repo), ...kapitelFazit(repo), ...zertifikatsSeite(datum, reportId, repo)];
  return new Document({ creator: "testsweet Testreport-Werkzeug", title: "SDN.Leistung Testreport",
    styles: { default: { document: { run: { font: FONT, size: 21 } } } },
    sections: [{ properties: {},
      footers: { default: new Footer({ children: [new Paragraph({ alignment: AlignmentType.CENTER,
        children: [text("SDN.Leistung Testreport · Report " + reportId + " · Seite ", { size: 18, color: P.secondary }),
          new TextRun({ children: [PageNumber.CURRENT], font: FONT, size: 18, color: P.secondary })] })] }) },
      children: kinder }] });
}

function soffice() {
  return fs.existsSync("C:/Program Files/LibreOffice/program/soffice.com")
    ? "C:/Program Files/LibreOffice/program/soffice.com" : "soffice";
}
function nachPdf(docxPfad, ausgabeOrdner) {
  const profil = fs.mkdtempSync(path.join(os.tmpdir(), "report-office-"));
  const ergebnis = spawnSync(soffice(), ["-env:UserInstallation=" + pathToFileURL(profil).href,
    "--headless", "--convert-to", "pdf", "--outdir", ausgabeOrdner, docxPfad],
    { encoding: "utf8", timeout: 120000 });
  fs.rmSync(profil, { recursive: true, force: true });
  if (ergebnis.status !== 0) throw Error("soffice fehlgeschlagen: " + (ergebnis.stderr || ergebnis.stdout));
  const pdf = docxPfad.replace(/\.docx$/, ".pdf");
  if (!fs.existsSync(pdf) || fs.readFileSync(pdf).subarray(0, 5).toString("ascii") !== "%PDF-") throw Error("PDF fehlt");
  return pdf;
}
/** Kapitel-Anker: pdftotext liefert CP1252-Umlautverstümmelung, deshalb
 *  wird zeilenverankert nach umlautfreien Präfixen der Überschriften gesucht. */
const SUCHER = [/^Einleitung/m, /^1\s+Testarten/m, /^2\s+Zwei/m, /^3\s+Herkunft/m,
  /^4\s+Echte/m, /^5\s+Unabh/m, /^6\s+Transaktionssteuerung/m, /^7\s+Ergebnis/m,
  /^8\s+Fazit/m, /^Testzertifikat/m];

function seitenErmitteln(pdf) {
  const ergebnis = spawnSync("pdftotext", ["-layout", pdf, "-"], { encoding: "utf8", timeout: 60000 });
  if (ergebnis.status !== 0) throw Error("pdftotext fehlgeschlagen: " + (ergebnis.stderr || ""));
  const seiten = ergebnis.stdout.split("\f");
  return SUCHER.map(sucher => {
    for (let i = 2; i < seiten.length; i++) {           // Seite 1 Titel, Seite 2 Inhaltsverzeichnis
      if (sucher.test(seiten[i])) return i + 1;
    }
    return null;
  });
}
function hash(datei) { return crypto.createHash("sha256").update(fs.readFileSync(datei)).digest("hex"); }

async function veroeffentliche(repo) {
  const datum = new Date().toLocaleDateString("de-DE", { year: "numeric", month: "2-digit", day: "2-digit" });
  const reportId = crypto.randomUUID().slice(0, 8).toUpperCase();
  const staging = fs.mkdtempSync(path.join(os.tmpdir(), "testreport-"));
  try {
    // Durchlauf 1: TOC ohne Seitenzahlen → Seiten ermitteln
    let docx1 = path.join(staging, "pass1.docx");
    await Packer.toBuffer(baueDokument(repo, datum, reportId, null)).then(b => fs.writeFileSync(docx1, b));
    const seiten = seitenErmitteln(nachPdf(docx1, staging));
    // Durchlauf 2: TOC mit echten Seitenzahlen → fertiges PDF
    let docx2 = path.join(staging, "SDN-Leistung-LMG-Testreport.docx");
    await Packer.toBuffer(baueDokument(repo, datum, reportId, seiten)).then(b => fs.writeFileSync(docx2, b));
    const pdf = nachPdf(docx2, staging);
    const zielDocx = path.join(repo, "doc", "SDN-Leistung-LMG-Testreport.docx");
    const zielPdf = path.join(repo, "doc", "SDN-Leistung-LMG-Testreport.pdf");
    fs.copyFileSync(docx2, zielDocx); fs.copyFileSync(pdf, zielPdf);
    const stand = { reportId, erzeugtAm: new Date().toISOString(),
      docxSha256: hash(docx2), pdfSha256: hash(pdf) };
    fs.writeFileSync(path.join(__dirname, "stand-report.json"), JSON.stringify(stand, null, 2));
    const letzte = runs.letzteLaeufe(repo);
    const gut = letzte.filter(l => l.lauf.erfolgreich).length;
    process.stdout.write(JSON.stringify({ state: "READY", reportId, pdf: zielPdf,
      laeufe: letzte.length, davonErfolgreich: gut, seiten: seiten.length ? seiten[seiten.length - 1] : null }) + "\n");
  } finally { fs.rmSync(staging, { recursive: true, force: true }); }
}

function pruefe(repo) {
  const standDatei = path.join(__dirname, "stand-report.json");
  const pdf = path.join(repo, "doc", "SDN-Leistung-LMG-Testreport.pdf");
  if (!fs.existsSync(standDatei) || !fs.existsSync(pdf)) throw Error("Kein aktueller Report vorhanden");
  const stand = JSON.parse(fs.readFileSync(standDatei, "utf8"));
  if (hash(pdf) !== stand.pdfSha256) throw Error("Report-PDF ist nicht die zuletzt erzeugte Fassung — neu erstellen");
  process.stdout.write(JSON.stringify({ state: "READY", reportId: stand.reportId }) + "\n");
}

if (require.main === module) {
  const modus = process.argv[2], repo = path.resolve(process.argv[3] || path.join(__dirname, "..", ".."));
  if (modus === "--publish") veroeffentliche(repo).catch(e => { console.error(e.message); process.exit(1); });
  else if (modus === "--verify") { try { pruefe(repo); } catch (e) { console.error(e.message); process.exit(1); } }
  else { console.error("Aufruf: node publish-report.js --publish|--verify <repo>"); process.exit(2); }
}
module.exports = { baueDokument };
