// Management-Testreport für SDN.Leistung (LMG): erzeugt bei jedem Aufruf frisch
// einen professionellen PDF-Bericht mit Titelbild, Einleitung, Inhaltsverzeichnis
// mit echten Seitenzahlen (Zwei-Durchlauf über pdftotext), fixen Erklärtkapiteln
// (Testarten, Fachschicht/Selenium, Sollwert-Herkunft, echte Fälle, externer
// Rechner, parallele Transaktionssteuerung), dynamischen Ergebnistabellen aus
// testsweet/logs und einer Gesamtbewertung des erklärten Prüfumfangs.
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
  "Gesamtbewertung des Prüfumfangs",
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
          color: j === z.length - 1 && /^(?:BESTANDEN|grün)$/.test(String(v)) ? P.pass
            : j === z.length - 1 && /NICHT|FEHL|rot/.test(String(v)) ? P.fail
            : j === z.length - 1 && /ABGEBROCHEN/.test(String(v)) ? P.warn : undefined,
          bold: j === z.length - 1 })) })),
    ] });
}

// ------------------------------------------------------------------
// Fixe Kapiteltexte (Management-Erklärungen; bewusst laienverständlich)
// ------------------------------------------------------------------
const T_EINLEITUNG = [
  "Dieser Managementreport erläutert die Prüfverfahren der Fachsoftware SDN.Leistung — Leistungsmanagement Grundsicherung (LMG). Er zeigt den beim Export vorgefundenen und auf Aktualität geprüften Nachweisstand. Eine Methodenbeschreibung belegt allein noch keine erfolgreiche Durchführung.",
  "Der erklärte Umfang umfasst neun Simulator-Kombinationen: Fachschicht mit POSITIV, NEGATIV, ECHT, RECHNER, LAST und STRESS sowie Weboberfläche mit POSITIV, NEGATIV und LAST. Hinzu kommen die laufgebundenen JUnit-Prüfungen beider Module und die GLM-Stichprobe aus Positiv- und Negativfällen. Fehlende Ebenen bleiben ausdrücklich unvollständig.",
  "Die Software unterstützt Berechnung und Auszahlung von Grundsicherungsleistungen nach SGB XII mit Java und Oracle. Die folgenden Kapitel beschreiben, welche Kontrollen vorgesehen sind. Kapitel 7 und die Gesamtbewertung entscheiden anhand tatsächlicher Belege, welche davon für den aktuellen Stand nachgewiesen sind.",
  "Der Report ist eine datierte Momentaufnahme. Sein Abruf prüft PDF-Integrität und den unveränderten Eingabebestand. Der gesonderte formale Zertifikatsnachweis bleibt das Dokument SDN-Leistung-LMG-Testzertifikat.",
];
const T_UNIT = [
  ["Was sind Unit-Tests?", "Ein Unit-Test vergleicht einen Softwarebaustein mit festgelegten Erwartungen, etwa gültige und ungültige IBANs oder handgerechnete Beträge. Maven führt diese Prüfungen aus. Ein übersprungener, noch laufender oder veralteter Test ist kein bestandener aktueller Nachweis."],
  ["Was sind Regressionstests?", "Regressionstests wiederholen festgelegte Abläufe, damit frühere Fehler nicht erneut auftreten. Zu den Verfahren gehören zehn dokumentierte Nutzer-Szenarien mit SQL-Prüfung persistierter Endzustände. Separate Goldwerttests vergleichen ausgewählte Rechnungen mit handgerechneten Konstanten; sie machen nicht jeden generierten Sollwert unabhängig."],
  ["Wie ihr Stand bewertet wird", "Die Ergebnistabelle verwendet laufgebundene XML-Belege, Klassen- und Methodeninventar sowie Quellen- und Buildbindung. Alte lose XML-Dateien werden nicht als ein neuer Gesamtlauf addiert. Fehler erhalten Vorrang vor fehlender Vollständigkeit; übersprungene Pflichtprüfungen verhindern BESTANDEN."],
];
const T_WEGE = [
  ["Prüfweg Fachschicht", "Simulierte Sachbearbeiter rufen die Fachservices direkt auf und prüfen anschließend Datenbank-Endzustände. Kategorie, Anzahl und Grad der Parallelität werden für jeden Lauf festgelegt. Laufzeiten und Durchsatz sind Messwerte dieses Laufs und keine allgemeine Kapazitätszusage."],
  ["Prüfweg Weboberfläche", "Selenium bedient die tatsächliche Weboberfläche im Chrome-Browser. Dazu gehören Anmeldung, Formulare, Auszahlungs- und Freigabeschritte mit getrennten Kennungen. SQL prüft die Ergebnisse. Screenshots ergänzen die Ablaufbelege; sie ersetzen keine eigenständige Prüfung der Bedienbarkeit."],
  ["Aussage beider Wege", "Beide Wege prüfen unterschiedliche Zugänge zur Fachlogik. Ob die vorgesehenen Abläufe auf beiden Wegen aktuell erfolgreich waren, zeigt erst Kapitel 7. ECHT, RECHNER und STRESS sind ausschließlich für die Fachschicht vorgesehen."],
];
const T_SOLLWERTE = [
  ["Wie die Testfälle entstehen", "Die Töpfe kombinieren kuratierte Abläufe, feste Grenzfälle und generierte Konstellationen. Eine Signatur-Menge verhindert Wiederholungen innerhalb des jeweiligen generierten Topfs. Wiederholte Läufe sind keine zusätzliche Zahl unterschiedlicher fachlich geprüfter Fälle."],
  ["Wo Sollwerte herkommen", "Viele Rechenblöcke und technische Erwartungen stammen aus derselben Berechnungsklasse wie das Produkt. Sie prüfen vor allem Persistenz und Workflow. Separate Handkonstanten und externe Gegenproben begrenzen gemeinsame Rechenfehler für ausgewählte Ergebnisse. Bei ECHT und RECHNER sind feste Zahlbeträge hinterlegt; Vermögenseinsatz und Endvermögen sind teilweise weiter aus der Produktreferenz abgeleitet."],
  ["Rolle des Sprachmodells", "GLM kann fertige Positiv- und Negativfälle mit Eingaben, Rechenwegen und Gesetzesunterlagen nachprüfen. Die Auswertung verlangt tatsächliche API-Belege, strukturierte Fallabdeckung und geprüfte Dokumenthashes. Kapitel 7 nennt den aktuellen Zustand. ECHT und RECHNER gehören nicht zu dieser GLM-Stichprobe. Ein fehlender oder veralteter GLM-Nachweis bestätigt keine Fachrechnung."],
];
const T_ECHT = [
  "ECHT bezeichnet fünf quellenbasierte Modellkonstellationen. Bei der dokumentierten Recherche wurde kein geeigneter vollständiger öffentlicher Fallbestand gefunden. Daraus folgt weder die Nichtexistenz aller anonymisierten Fälle noch ein pauschales Veröffentlichungsverbot.",
  "Die Fälle verwenden Regelbedarfsstufen, einen historischen BSG-Vermögenswert von 44.500 EUR, einen historischen Kfz-Gesamtwert von 31.000 EUR, ein Verbandsbeispiel mit 800 EUR sowie örtliche Unterkunftswerte. Ergänzte Rente und Wohnkosten sind Modellannahmen. Die Fälle sind keine vollständige Nachprüfung damaliger Leistungsbescheide.",
  "Die Vermögensabwehr prüft die Produktformel und unveränderte Fachzustände. Zusätzliche heutige Privilegierungen einzelner Vermögensarten, insbesondere angemessener Kfz, werden dabei nicht vollständig abgebildet. Der KdU-Fall verwendet einen aus Einzelwerten abgeleiteten Gesamtdeckel von 590 EUR; er belegt keine vollständige komponentenweise Anwendung der kommunalen Richtlinie. Betriebs- und Heizwerte sind dort Prüfschwellen mit Einzelfallprüfung.",
];
const T_RECHNER = [
  "Die Kategorie RECHNER enthält zwölf feste Zahlbetragserwartungen für eine begrenzte Schnittmenge zum SGB-XII-Rechner unter https://www.steuerschroeder.de/Steuerrechner/Grundsicherung.html. Betriebskosten sind null, Warmwasser zentral, Vermögen null; Sonderregeln wie Grundrentenfreibetrag und zusätzliche Altersvorsorge sind nicht Gegenstand des Vergleichs.",
  "Die ursprüngliche Behauptung zwölf externer Aufrufe ist nicht für jeden Fall durch Eingabe-/Ergebnisbelege gesichert. Die Review-Gegenprobe vom 27.10.2026 bestätigt die Fälle 1, 6 und 12 mit 978,00 EUR, 268,71 EUR und 1.077,00 EUR. Diese neue Stichprobe ist von der früheren Erhebung getrennt dokumentiert.",
  "Alle zwölf festen Erwartungen reichen von 227,00 bis 1.427,00 EUR. JUnit prüft die Berechnung gegen diese Konstanten; ein gesondert gestarteter RECHNER-Simulatorlauf prüft die SQL-Ergebnisse. Die Kategorie läuft nicht automatisch bei jedem Simulatorlauf und ist nicht Teil der GLM-Stichprobe. Änderungen werden nur erkannt, soweit sie kontrollierte Ergebnisse oder Verträge betreffen.",
];
const T_PARALLEL = [
  ["Was geprüft werden soll", "Parallele Abläufe können verlorene Änderungen, doppelte Zahlungen, Sperrkonflikte oder Poolengpässe offenlegen. Oracle stellt die transaktionalen Datenbankmechanismen bereit. Ein unklarer Commit-Ausgang erfordert Statusklärung; eine Zeitüberschreitung beweist keinen bereits erfolgten Rollback."],
  ["Lasttest", "Viele Mitarbeiter wiederholen eine schlanke Fachkette. SQL vergleicht Betrag und Budget mit den Szenarioerwartungen. Der aggregierte Bericht beschreibt diesen Lauf; daraus folgt keine allgemeine Kapazitätsgrenze."],
  ["Stresstest", "Zwei Wellen bearbeiten dieselben Vorgänge mit einer Phasenbarriere dazwischen. Die zweite Welle findet die Daten wieder, ändert Miete und ordnet die Zahlung an. SQL kontrolliert Endzustand und Versionen."],
  ["Deadlock und aktueller Nachweis", "Ein eigener Oracle-Test erzeugt gekreuzte Sperren und prüft die Wiederholung. Seine Existenz belegt keine aktuelle Ausführung: Er benötigt eine konfigurierte Datenbank. Ob die benötigten aktuellen JUnit- und Simulatorbelege vorliegen, entscheidet die folgende Übersicht."],
];

// ------------------------------------------------------------------
// Dokumentbau
// ------------------------------------------------------------------
function titelseite(repo, datum, reportId, version) {
  const bild = path.join(repo, "testsweet", "src", "main", "resources", "sweet", "assets", "titelbild.jpg");
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
        text(titel + "\t", { size: 22, bold: i === 0 || titel === KAPITEL[9] }),
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

function stateLabel(state) {
  return ({PASS:"BESTANDEN",FAIL:"FEHLGESCHLAGEN",ABORTED:"ABGEBROCHEN",RUNNING:"LÄUFT",UNVERIFIED:"UNVOLLSTÄNDIG"})[state] || "UNVOLLSTÄNDIG";
}
function ergebnisTabelle(repo, stand = runs.reportStand(repo)) {
  const rows = stand.layers.map(layer=>{
    const item=layer.evidence, isJunit=layer.id.startsWith("JUnit:"), isGlm=layer.id.startsWith("GLM:");
    const total=isJunit?item?.tests:isGlm?item?.cases?.length:item?.gesamt;
    const verified=isJunit?item?.passed:isGlm?item?.valid?item.cases.length:0:item?.verifiziert;
    const date=isJunit?item?.marker?.startedAt:isGlm?item?.datum:item?.startedAt;
    return [layer.id.replace("|",": "),runs.deutschDatum(date),Number.isInteger(total)?String(total):"—",Number.isInteger(verified)?String(verified):"—",stateLabel(layer.state)];
  });
  return [h1(KAPITEL[7]),
    absatz("Der erklärte Umfang steht vollständig in der Tabelle, auch wenn ein Nachweis fehlt. Bekannte Fach-/SQL-/Testfehler heißen FEHLGESCHLAGEN. Abbruch, laufende Prüfung, fehlende Buildbindung und fehlende Evidenz sind eigenständige unvollständige Zustände."),
    tabelle(["Prüfebene", "Datum", "Fälle", "verifiziert", "Ergebnis"],rows),
    ...stand.layers.filter(layer=>layer.state!=="PASS").map(layer=>hinweis(layer.id+": "+[...new Set(layer.reasons)].join("; "))),
    hinweis("GLM prüft eine Positiv-/Negativstichprobe; ECHT/RECHNER haben eigene SQL-Läufe. Dieser Managementreport ersetzt nicht das gesonderte formale Zertifikatsdokument.")];
}
function kapitelFazit(repo, stand = runs.reportStand(repo)) {
  const good=stand.layers.filter(x=>x.state==="PASS").length;
  return [h1(KAPITEL[8]),absatz("Gesamtbewertung: "+stand.verdict+". Von "+stand.layers.length+" erklärten Prüfebenen sind "+good+" aktuell vollständig belegt."),
    absatz(stand.verdict==="BESTANDEN" ? "Alle in Kapitel 7 erklärten Ebenen erfüllen die dort geprüften Nachweisanforderungen. Die Aussage gilt für die dokumentierten Szenarien und Modellgrenzen." : "Eine vollständige positive Bestätigung ist mit diesem Nachweisstand nicht möglich. Die nicht bestandenen oder unvollständigen Ebenen und ihre Gründe stehen in Kapitel 7."),
    absatz("Vor einer Freigabe sind bekannte Fehler zu klären und fehlende aktuelle Prüfungen nachzuholen. Eine erfolgreiche Wiederholung ersetzt ältere Ergebnisse nur bei eindeutig späterer Laufzeit und gültigen Belegen.")];
}
function zertifikatsSeite(datum, reportId, repo, stand = runs.reportStand(repo)) {
  const color=stand.verdict==="BESTANDEN"?P.pass:stand.verdict==="NICHT BESTANDEN"?P.fail:P.warn;
  return [h1(KAPITEL[9]),absatz("Diese Seite fasst den erklärten Prüfumfang aus Kapitel 7 zum Erstellungsdatum "+datum+" zusammen. Sie bestätigt keine zusätzliche Prüfung und keine über die dort ausgewiesenen Belege hinausgehende Fachabdeckung."),
    new Paragraph({spacing:{before:200,after:200},children:[text("Gesamtbewertung: "+stand.verdict,{size:28,bold:true,color})]}),
    absatz("Report-Kennung "+reportId+". Nur vollständig aktuelle PASS-Ebenen tragen eine positive Gesamtbewertung; bekannte Fehler haben Vorrang vor Unvollständigkeit. Die Eingaben und PDF-Bytes sind beim Abruf erneut zu prüfen."),
    hinweis("Der formale, beleggebundene Zertifikatsnachweis ist ausschließlich das separate Dokument SDN-Leistung-LMG-Testzertifikat. Die Kategorien ECHT/RECHNER belegen quellenbasierte Modellfälle und feste Ergebnissperren; sie ersetzen keine vollständige Prüfung realer Bescheide.")];
}

function baueDokument(repo, datum, reportId, seiten, stand = runs.reportStand(repo)) {
  const kinder = [
    ...titelseite(repo, datum, reportId, "0.5.8"),
    ...inhaltsverzeichnis(seiten),
    ...kapitelEinleitung(datum), ...kapitelUnit(), ...kapitelWege(), ...kapitelSollwerte(),
    ...kapitelEcht(), ...kapitelRechner(), ...kapitelParallel(),
    ...ergebnisTabelle(repo, stand), ...kapitelFazit(repo, stand), ...zertifikatsSeite(datum, reportId, repo, stand)];
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
function cleanTemporary(directory, prefix) {
  const resolved=path.resolve(directory),base=path.resolve(os.tmpdir())+path.sep;
  if (!resolved.startsWith(base) || !path.basename(resolved).startsWith(prefix)) throw Error("Ungültiges temporäres Cleanupziel");
  fs.rmSync(resolved,{recursive:true,force:true});
}
function nachPdf(docxPfad, ausgabeOrdner) {
  const profil=fs.mkdtempSync(path.join(os.tmpdir(),"report-office-"));
  try {
    const result=spawnSync(soffice(),["-env:UserInstallation="+pathToFileURL(profil).href,"--headless","--convert-to","pdf","--outdir",ausgabeOrdner,docxPfad],{encoding:"utf8",timeout:120000});
    if (result.error || result.status!==0) throw Error("soffice fehlgeschlagen: "+(result.error?.message || result.stderr || result.stdout));
    const pdf=path.join(ausgabeOrdner,path.basename(docxPfad,".docx")+".pdf");
    if (!fs.existsSync(pdf) || fs.readFileSync(pdf).subarray(0,5).toString("ascii")!=="%PDF-") throw Error("PDF fehlt");
    return pdf;
  } finally { cleanTemporary(profil,"report-office-"); }
}
// Nur Kapitelüberschriften nach Titel/Verzeichnis; erlaubte Einrückung durch Satz.
const SUCHER=[/^[ \t]*Einleitung\b/m,/^[ \t]*1\s+Testarten\b/m,/^[ \t]*2\s+Zwei\b/m,/^[ \t]*3\s+Herkunft\b/m,
  /^[ \t]*4\s+Echte\b/m,/^[ \t]*5\s+Unabh/m,/^[ \t]*6\s+Transaktionssteuerung\b/m,/^[ \t]*7\s+Ergebnis/m,/^[ \t]*8\s+Fazit\b/m,/^[ \t]*Gesamtbewertung des Pr/m];
function chapterPages(raw) {
  const pages=raw.split("\f");
  const selected=SUCHER.map((anchor,index)=>{
    const hits=[];
    for(let page=2;page<pages.length;page++) if(anchor.test(pages[page]))hits.push(page+1);
    if(hits.length!==1)throw Error("Kapitelanker fehlt oder ist mehrdeutig: "+KAPITEL[index]);
    return hits[0];
  });
  if(selected.some((page,index)=>!Number.isInteger(page) || page<3 || index>0 && page<=selected[index-1])) throw Error("Kapitelreihenfolge/Seitenzahlen ungültig");
  return selected;
}
function seitenErmitteln(pdf) {
  const result=spawnSync("pdftotext",["-layout",pdf,"-"],{encoding:"utf8",timeout:60000});
  if(result.error || result.status!==0)throw Error("pdftotext fehlgeschlagen: "+(result.error?.message || result.stderr || ""));
  return chapterPages(result.stdout);
}
function hash(file){return crypto.createHash("sha256").update(fs.readFileSync(file)).digest("hex");}
function assertInputs(repo,fingerprint){if(runs.inputFingerprint(repo)!==fingerprint)throw Error("Prüfeingaben wurden während des Exports verändert — neu erstellen");}
function reportPaths(repo){
  return {manifest:path.join(repo,"doc/testreport-werkzeug/stand-report.json"),pdf:path.join(repo,"doc/SDN-Leistung-LMG-Testreport.pdf"),docx:path.join(repo,"doc/SDN-Leistung-LMG-Testreport.docx")};
}
function atomicCopy(source,target,id){
  const temporary=target+"."+id+".tmp";
  try{fs.copyFileSync(source,temporary);fs.renameSync(temporary,target);}finally{if(fs.existsSync(temporary))fs.unlinkSync(temporary);}
}
function acquirePublishLock(file) {
  const token=crypto.randomUUID();
  const openOwned=()=>{
    const descriptor=fs.openSync(file,"wx");
    try{fs.writeFileSync(descriptor,JSON.stringify({pid:process.pid,token}));return {descriptor,token};}
    catch(error){fs.closeSync(descriptor);fs.unlinkSync(file);throw error;}
  };
  try { return openOwned(); }
  catch (error) {
    if(error.code!=="EEXIST")throw error;
    // Nur ein Prozess darf eine verwaiste Sperre prüfen und entfernen. Ohne
    // dieses Gate könnten zwei Wiederanläufe die neue Sperre des anderen löschen.
    const recovery=file+".recovery";
    let gate;try{gate=fs.openSync(recovery,"wx");}catch(_){throw Error("Report-Exportsperre wird gerade geprüft; bei dauerhaftem Fehler manuell prüfen: "+recovery);}
    try{
      if(!fs.existsSync(file))return openOwned();
      let previous;try{previous=JSON.parse(fs.readFileSync(file,"utf8"));}catch(_){throw Error("Unlesbare Report-Exportsperre; manuell prüfen: "+file);}
      if(!Number.isInteger(previous.pid) || previous.pid<=0 || typeof previous.token!=="string")throw Error("Ungültige Report-Exportsperre; manuell prüfen: "+file);
      try{process.kill(previous.pid,0);throw Error("Ein Reportexport läuft bereits (PID "+previous.pid+")");}
      catch(probe){if(probe.code!=="ESRCH")throw probe;}
      fs.unlinkSync(file);return openOwned();
    }finally{fs.closeSync(gate);fs.unlinkSync(recovery);}
  }
}
function releasePublishLock(file,owned) {
  fs.closeSync(owned.descriptor);
  if(fs.existsSync(file) && JSON.parse(fs.readFileSync(file,"utf8")).token===owned.token)fs.unlinkSync(file);
}
async function veroeffentliche(repo) {
  const paths=reportPaths(repo),lock=path.join(repo,"doc/testreport-werkzeug/publish.lock");
  const owned=acquirePublishLock(lock);
  let staging;
  try{
    const inventory=runs.inputInventory(repo),fingerprint=crypto.createHash("sha256").update(JSON.stringify(inventory)).digest("hex"),stand=runs.reportStand(repo);
    assertInputs(repo,fingerprint);
    const datum=new Date().toLocaleDateString("de-DE",{timeZone:"Europe/Berlin",year:"numeric",month:"2-digit",day:"2-digit"}),reportId=crypto.randomUUID().slice(0,8).toUpperCase();
    staging=fs.mkdtempSync(path.join(os.tmpdir(),"testreport-"));
    const first=path.join(staging,"pass1.docx");
    fs.writeFileSync(first,await Packer.toBuffer(baueDokument(repo,datum,reportId,null,stand)));
    const pages=seitenErmitteln(nachPdf(first,staging));
    const second=path.join(staging,"SDN-Leistung-LMG-Testreport.docx");
    fs.writeFileSync(second,await Packer.toBuffer(baueDokument(repo,datum,reportId,pages,stand)));
    const pdf=nachPdf(second,staging),finalPages=seitenErmitteln(pdf);
    if(JSON.stringify(pages)!==JSON.stringify(finalPages))throw Error("Kapitelstartseiten haben sich im zweiten Durchlauf verändert — Veröffentlichung abgebrochen");
    assertInputs(repo,fingerprint);
    const marker={schemaVersion:2,reportId,erzeugtAm:new Date().toISOString(),verdict:stand.verdict,inputFingerprint:fingerprint,inputInventory:inventory,docxSha256:hash(second),pdfSha256:hash(pdf)};
    atomicCopy(second,paths.docx,reportId);atomicCopy(pdf,paths.pdf,reportId);
    assertInputs(repo,fingerprint);
    const markerFile=paths.manifest+"."+reportId+".tmp";
    try{fs.writeFileSync(markerFile,JSON.stringify(marker,null,2));fs.renameSync(markerFile,paths.manifest);}finally{if(fs.existsSync(markerFile))fs.unlinkSync(markerFile);}
    const count=stand.letzte.length,good=stand.letzte.filter(x=>x.lauf.erfolgreich).length;
    const result={state:"READY",reportId,pdf:paths.pdf,pdfSha256:marker.pdfSha256,verdict:stand.verdict,laeufe:count,davonErfolgreich:good,seiten:finalPages.at(-1)};
    process.stdout.write(JSON.stringify(result)+"\n");return result;
  }finally{
    try{releasePublishLock(lock,owned);}finally{if(staging)cleanTemporary(staging,"testreport-");}
  }
}
function pruefe(repo) {
  const paths=reportPaths(repo);
  if(!fs.existsSync(paths.manifest) || !fs.existsSync(paths.pdf) || !fs.existsSync(paths.docx))throw Error("Kein vollständig veröffentlichter Report vorhanden");
  const marker=JSON.parse(fs.readFileSync(paths.manifest,"utf8"));
  if(marker.schemaVersion!==2 || !marker.reportId || !Array.isArray(marker.inputInventory) || !marker.inputFingerprint)throw Error("Report ohne gültige Eingabebindung — neu erstellen");
  if(hash(paths.pdf)!==marker.pdfSha256 || hash(paths.docx)!==marker.docxSha256)throw Error("Reportdateien stimmen nicht mit dem veröffentlichten Stand überein");
  assertInputs(repo,marker.inputFingerprint);
  return {state:"READY",reportId:marker.reportId,pdfSha256:marker.pdfSha256,verdict:marker.verdict};
}
if(require.main===module){
  const command=process.argv[2],repo=path.resolve(process.argv[3] || path.join(__dirname,"../.."));
  if(command==="--publish")veroeffentliche(repo).catch(error=>{console.error(error.message);process.exitCode=1;});
  else if(command==="--verify"){try{process.stdout.write(JSON.stringify(pruefe(repo))+"\n");}catch(error){console.error(error.message);process.exitCode=1;}}
  else{console.error("Aufruf: node publish-report.js --publish|--verify <repo>");process.exitCode=2;}
}
module.exports={baueDokument,ergebnisTabelle,kapitelFazit,zertifikatsSeite,chapterPages,seitenErmitteln,assertInputs,pruefe,veroeffentliche,stateLabel};
