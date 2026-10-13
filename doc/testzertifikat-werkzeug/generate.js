// Erzeugt das Testzertifikat "SDN.Leistung LMG" als DOCX.
// Seite 1: Zertifikat mit Häkchen je Qualitätsschicht + Erhebungs-/Warnblock;
// Seite 2 ff: was jede Schicht tut und wo sie nachlesbar ist.
//
// Ehrlichkeitsprinzip: Was automatisiert prüfbar ist, wird automatisiert erhoben —
// die neuesten testsweet-Laufzahlen einschließlich Fehlern und strukturierte echte LLM-Prüfbelege.
// Die mvn-Testzahl und historische Webreferenzen sind als manuell gepflegt markiert.
// LLM-Nachweise werden ausschließlich über strukturierte echte API-Artefakte mit
// vollständiger Fallabdeckung und SHA-256-Bindungen ausgewertet.
// Aufruf: node generate.js [ausgabe.docx]  — schreibt zusätzlich stand.json.
const path = require("path");
const fs = require("fs");
const {
  Document, Packer, Paragraph, TextRun, Table, TableRow, TableCell,
  PageNumber, AlignmentType, HeadingLevel, WidthType, BorderStyle,
  ShadingType, SectionType, TableLayoutType, Footer, LevelFormat,
} = require("../handbuch-werkzeug/node_modules/docx");

const WERKZEUG = __dirname;
const REPO = path.resolve(WERKZEUG, "..", "..");
const P = {
  bg: "162235", accent: "37DCF2", gruen: "1E8E3E", gruenHell: "E6F4EA",
  aufDunkel: "F1F5F9", aufDunkelNeben: "CBD5E1", aufDunkelGruen: "86EFAC", aufDunkelWarn: "FDE68A",
  warn: "B45309", warnHell: "FEF3C7", rot: "B3261E",
  primary: "0E2A33", h2: "1B6B7A", body: "000000", secondary: "5A6B73",
  table: { headerBg: "1B6B7A", headerText: "FFFFFF", accentLine: "1B6B7A", innerLine: "C8DDE2", surface: "EDF3F5" },
};
const FONT = { ascii: "Calibri", hAnsi: "Calibri" };
const FONT_ARIAL = { ascii: "Arial", hAnsi: "Arial" };

// ─── Automatische Ist-Erhebung ───────────────────────────────────────────────
function liesLaufzahlen() {
  const logs = path.join(REPO, "testsweet", "logs");
  const laeufe = {};
  if (!fs.existsSync(logs)) return laeufe;
  for (const ordner of fs.readdirSync(logs).sort()) {
    const datei = path.join(logs, ordner, "zusammenfassung.txt");
    if (!fs.existsSync(datei)) continue;
    const txt = fs.readFileSync(datei, "utf8");
    const kategorie = /^Kategorie\s*:\s*(\S+)/m.exec(txt)?.[1];
    const gesamt = /^Aufträge\s*:\s*(\d+)/m.exec(txt)?.[1];
    const verifiziert = /^verifiziert\s*:\s*(\d+)/m.exec(txt)?.[1];
    const erfolgreich = /^Lauf erfolgreich\s*:\s*true/m.test(txt);
    const zeit = /^Zeitraum\s*:\s*(\S+)/m.exec(txt)?.[1] || "";
    if (!kategorie) continue;

    // Neuere fehlgeschlagene Läufe dürfen ältere grüne Ergebnisse nicht verdecken.
    const bisher = laeufe[kategorie];
    const kandidat = {
      verifiziert: Number(verifiziert), gesamt: Number(gesamt), erfolgreich,
      modus: /^Modus\s*:\s*(.+)$/m.exec(txt)?.[1]?.trim() || "nicht angegeben",
      ordner: "testsweet/logs/" + ordner, zeit: zeit.slice(0, 10),
    };
    if (!bisher || kandidat.ordner > bisher.ordner) {
      laeufe[kategorie] = kandidat;
    }
  }
  return laeufe;
}

function liesLlmProtokoll() {
  return require("./llm-evidence").latestEvidence(REPO);
}

function liesMdDatenstand(laeufe) {
  const logs = path.join(REPO, "testsweet", "logs");
  if (!fs.existsSync(logs)) return null;
  for (const ordner of fs.readdirSync(logs).sort().reverse()) {
    if (fs.existsSync(path.join(logs, ordner, "testdokumentation.md"))) {
      return { ordner: "testsweet/logs/" + ordner, datum: ordner.replace(/^run-(\d{8}).*$/, "$1").replace(/^(\d{4})(\d{2})(\d{2})$/, "$1-$2-$3") };
    }
  }
  return null;
}

const LAEUFE = liesLaufzahlen();
const LLM = liesLlmProtokoll();
const MD = liesMdDatenstand(LAEUFE);
const HEUTE = new Date().toISOString().slice(0, 10);

const warnungen = [];
function laufZeile(kat, label) {
  const l = LAEUFE[kat];
  if (!l) {
    warnungen.push(`Für die Kategorie ${label} liegt kein erfolgreicher Lauf unter testsweet/logs/ vor (Referenzlauf fahren).`);
    return { haken: "!", farbe: P.warn, flaeche: P.warnHell,
      titel: `testsweet ${label} — kein Lauf gefunden`, zeile: "Automatisch erhoben: kein erfolgreicher Lauf in testsweet/logs/ vorhanden" };
  }
  if (!l.erfolgreich || !Number.isFinite(l.gesamt) || l.verifiziert !== l.gesamt) {
    warnungen.push(`Neuester Lauf ${label} ist fehlgeschlagen oder unvollständig: ${l.ordner}`);
    return {haken:"!",farbe:P.warn,flaeche:P.warnHell,titel:`testsweet ${label} — neuester Lauf nicht bestanden`,zeile:l.ordner};
  }
  return { haken: "✓", farbe: P.gruen, flaeche: P.gruenHell,
    titel: `testsweet ${label} — ${l.verifiziert}/${l.gesamt} verifiziert`,
    zeile: `Automatisch erhoben am ${HEUTE} aus ${l.ordner} (Lauf vom ${l.zeit}; Modus ${l.modus})` };
}

// Manuell zu pflegende Prüfergebnisse (nicht automatisch erhebbar):
const MVN_TESTS = "249/249 geprüft (Oracle-Lauf plus isolierter Konfigurationstest)";
const MVN_STAND = "2026-10-13";

const pos = laufZeile("POSITIV", "Positivfälle");
const neg = laufZeile("NEGATIV", "Negativfälle");
const last = laufZeile("LAST", "Lasttest");
const stress = laufZeile("STRESS", "Stresstest");

let llm;
if (!LLM) {
  warnungen.push("Keine gültige strukturierte echte LLM-Prüfung unter testsweet/logs/llm-review-*/result.json. Historische Freitext- und Python-Protokolle gelten nicht als Modellnachweis.");
  llm = { haken: "!", farbe: P.warn, flaeche: P.warnHell,
    titel: "LLM-Nachrechnung der Testdokumentation — kein Protokoll vorhanden",
    zeile: "Echte LLM-Prüfung in der testsweet-Oberfläche starten; Zugang und Modell konfigurieren" };
 } else if (!LLM.valid) {
  warnungen.push(`Echte LLM-Prüfung nicht bestanden oder nicht mehr gültig: ${LLM.reason} (${LLM.datei})`);
  llm = {haken:"!",farbe:P.warn,flaeche:P.warnHell,
    titel:`LLM-Nachprüfung — ${LLM.state}`,zeile:LLM.reason};
} else {
  const m = LLM.meta || {};
  // Kompakte Darstellung für die Zertifikatsseite (Details auf den Erklärseiten):
  // Ergebnis auf die Kernaussage kürzen, Unterlagen nur als Namen nennen.
  const kurz = (text, teile) => String(text || "")
      .split("·").slice(0, teile).join("·")
      .replace(/\s*\(.*?\)\s*/g, " ").replace(/\s+/g, " ").trim();
  const unterlagen = m.mitgegeben
    ? m.mitgegeben.split("·").map(s => s.trim().replace(/\s*\(.*?\)\s*/g, "").split(" ")[0])
        .filter(Boolean).join(", ")
    : "siehe Protokoll";
  llm = { haken: "✓", farbe: P.gruen, flaeche: P.gruenHell,
    titel: `LLM-Nachrechnung — ${kurz(m.ergebnis, 2)} (${kurz(m.modell, 1) || "LLM"}, ${LLM.datum})`,
    zeile: `Geprüft: ${kurz(m.pruefumfang, 1)} · Unterlagen: ${unterlagen}`
      + (m.datenstand ? ` · Datenstand: ${m.datenstand}` : "") };
}
// Aktualität wird im Belegprüfer über konkrete Quelldateien und SHA-256 geprüft.


const PRUEFSCHICHTEN = [
  { haken: "✓", farbe: P.gruen, flaeche: P.gruenHell,
    titel: `Unit-Tests der Fach- und Kernschicht — ${MVN_TESTS} grün (manuell gepflegt, Stand ${MVN_STAND})`,
    zeile: "Version 0.5.8 mit aktuellen Arbeitskopie-Änderungen; 248 bestanden mit Oracle, 1 dort übersprungen und ohne TX_URL separat bestanden",
    detail: "Die gesamte Berechnungs- und Transaktionslogik ist ohne Fenstereingaben testbar: TxManager (Rollback auch bei OOM, Retry bei ORA-00054/60/08177 inkl. echtem Deadlock-Test), SagaEngine mit Journal und Wiederanlauf, TaskController mit Wachhund, jeden §-30-Zuschlag mit Deckel, die KdU-Dreiteilung, § 82-Kette mit Freibetragskappung, Vermögensanrechnung und -verbrauch, HTTP-Schicht (401/403/404/405/400-Matrix), JSON-Grenzen, Einsichtsprotokoll. Diese Zahl kann die Anwendung nicht selbst erheben (ein Testlauf dauert zu lange für den Knopfdruck) — sie ist im Generator markiert und nach jedem mvn test zu pflegen. Nachlesbar: Maven-Konsole; Testklassen unter src/test/java." },
  { haken: "✓", farbe: P.gruen, flaeche: P.gruenHell,
    titel: "Goldwerte — 9 von Hand gerechnete Referenzfälle",
    zeile: "GoldWerteTest; Werte aus doc/regressionstests.md handgerechnet und eingefroren",
    detail: "Gegenmaßnahme zum Zirkelschluss: Diese neun Soll-Beträge wurden unabhängig vom Code auf Papier gerechnet und als Test fixiert (u. a. Standardrente 1.053,00 €; Paar 1.662,00 €; Mehrbedarfsbündel 767,37 €; Familie 2.189,68 €; Erwerb mit gekapptem Freibetrag 554,50 €; Vermögen 363,00 €; §-30-Abs.-6-Deckel 188,94 €). Verbiegt jemand die Formel in Leistungsberechnung, fallen diese Tests auf, obwohl Erwartung und Fachschicht sonst denselben Code nutzen. Nachlesbar: src/test/java/.../GoldWerteTest.java." },
  { haken: "✓", farbe: P.gruen, flaeche: P.gruenHell,
    titel: "Szenario-Regression — 10 Nutzer-Szenarien mit SQL-Endzustandsprüfung",
    zeile: "SzenarioRegressionTest (mit TX_URL); Testfälle dokumentiert in doc/regressionstests.md",
    detail: "Zehn echte Nutzer-Durchgänge mit mehreren Sachbearbeitern (Erfassung, Vier-Augen-Freigabe, Doppelklick, Storno + korrigierte Neuauszahlung im selben Monat, Vermögens-, Erwerbs- und Mehrbedarfsfälle, Bescheid mit Snapshot, Konkurrenz auf derselben Maske, Kassenlauf, Historisierung). Verifiziert wird je Szenario der persistierte Endzustand per SQL direkt auf den Oracle-Tabellen: Beträge, Status, Sachbearbeiter-Zuordnungen, Journal-Chronologie. Nachlesbar: doc/regressionstests.md." },
  { ...pos,
    detail: "Der Mitarbeiter-Simulator lässt parallele Sachbearbeiter auf die Fachschicht los (SozialLeistungsService → TxManager → Oracle). Positiv-Topf: 10 kuratierte Arten + Kombinationsfälle (Haushaltsform, Stufen, Merkmale inkl. Entbindungsmonat, Einkommen, Vermögen, Weg: Bescheid/Freigabe/Doppelklick/Storno-Korrektur); Erwartung mit derselben Berechnung, Endzustand per SQL geprüft. Diese Zeile wird automatisch aus dem neuesten erfolgreichen Lauf erhoben — die je-Fall-Nachweise (PDF/MD mit Rechenwegen) liegen im genannten Laufordner." },
  { ...neg,
    detail: "Erwartete Fachfehler als Szenario-Bestandteil: hohe Rente/Erwerb/Vermögen (KEIN_ANSPRUCH), zu kleines Budget, Doppelanordnung je Monat, Selbstfreigabe, Doppelfreigabe, Storno nach Kassenübergabe, ungültige IBAN/BIC, unsinnige Bescheid-Zeiträume, unbekannte IDs, Zahlung außerhalb des Bewilligungsbescheids, veralteter Maskenstand. Zusätzlich per SQL bewiesen: Die Datenbank bleibt unverändert. Automatisch aus dem neuesten erfolgreichen Lauf erhoben." },
  { haken: "✓", farbe: P.gruen, flaeche: P.gruenHell,
    titel: "testsweet über die Weboberfläche (Selenium) — 60/60 + 60/60 verifiziert (manuell gepflegt)",
    zeile: "Modus 2 mit echten Chrome-Fenstern gegen die SDN.Leistung-Oberfläche (Referenzläufe 2026-10-11/12)",
    detail: "Dasselbe Fachziel wie Modus 1, aber der Weg führt durch die echte Benutzeroberfläche: Anmeldung, Vorgang anlegen, Fall erfassen, Zahlungsmonat setzen, Vier-Augen-Freigabe durch einen Kollegen, Storno über die Admin-Rolle — je Schritt mit Screenshot. Negativ-Varianten beweisen die Abwehr auch auf UI-Seite (falsches Passwort, ungültige Bankdaten, Anordnung ohne Monat). Vollständige SQL-Gegenprüfung des DB-Endzustands inklusive Journal-Chronologie. Nachlesbar: doc/testsweet-handbuch.pdf, Laufordner unter testsweet/logs/." },
  { ...last,
    detail: "Schlanke Durchläufe in hoher Stückzahl (Durchsatz, Sperrwartezeiten, keine Verifikationsfehler unter Dauerlast). Die Zeile wird automatisch aus dem neuesten erfolgreichen LAST-Lauf erhoben (lastreport.pdf/.md im Laufordner). Fehlt ein Lauf: Referenzlauf fahren, dann erscheint er hier automatisch." },
  { ...stress,
    detail: "Zwei Wellen über dieselben User mit Phasenbarriere: Welle 2 beweist den Lost-Update-Schutz (alle Fälle exakt Version 2) und korrekte Budgetführung unter Konkurrenz; SQL-Endprüfung über den Gesamtbestand. Automatisch aus dem neuesten erfolgreichen STRESS-Lauf erhoben." },
  { ...llm,
    detail: `Die Nachprüfung verwendet echte konfigurierte Modellaufrufe. Je Fallgruppe werden der vollständige Prüfauftrag, vollständige Gesetzesgrundlagen und vollständige ausgewählte Fälle übertragen. Positiv- und Negativfälle einschließlich kuratierter Arten werden ausgewählt. Zertifiziert wird nur PASS mit exakter Fallabdeckung, Modellübereinstimmung, vollständigen Antworten und gültigen SHA-256-Bindungen von Eingaben, Request, Response und Ergebnis. Artefakte: ${LLM ? LLM.datei : "keine"}. Dateiübergabe und fachliches Ergebnis sind getrennte Nachweise. Ein erfolgreicher Transport allein bestätigt keine Berechnung. Historische Python-Protokolle zählen ausdrücklich nicht als LLM-Prüfung.` },
  { haken: "✓", farbe: P.gruen, flaeche: P.gruenHell,
    titel: "Code-Reviews durch unterschiedliche KIs — wiederholt, gegengeprüft, behoben",
    zeile: "Allumfassendes Review 2026-10-12 (75 Befunde → alle behoben) + Gegenprüfungen + zweite Fremd-Härtung",
    detail: "Der Code wurde wiederholt von KI-Instanzen unabhängig gelesen und kritisiert — bewusst mit unterschiedlichen KIs, damit nicht dieselbe Blindstelle durchgeht:",
    detailListe: [
      { titel: "Allumfassendes Codereview", text: "Strukturierter Prüfauftrag (Prüffelder A–J, 48 Verdachtsspuren, 25 Testlücken; doc/codereview/codereviewPrompt.txt). Die 75 Befunde wurden bis v0.5.7 behoben." },
      { titel: "Gegenprüfung", text: "Die Fixes zu v0.5.8 wurden erneut geprüft (Nachbesserungsbericht)." },
      { titel: "Weitere Härtung", text: "Body-Cache, Audit-Redaktion, Saga-Korrelation und Bescheidmonat-Prüfung wurden zusätzlich gehärtet." },
      { titel: "testsweet-Review", text: "Am 2026-10-13 durchgeführt, mit dokumentierten Fehlerkorrekturen." },
    ],
    detailQuelle: "Nachlesbar: Berichte unter doc/codereview/." },
];

const allesBestanden = warnungen.length === 0;

// ─── Bausteine ───────────────────────────────────────────────────────────────
const zertifikat = [
  new Paragraph({ spacing: { before: 200, after: 0 },
    children: [new TextRun({ text: "TESTZERTIFIKAT", size: 20, color: P.accent, font: FONT, characterSpacing: 60, bold: true })] }),
  new Paragraph({ spacing: { after: 60 },
    children: [new TextRun({ text: "Fachschicht SDN.Leistung LMG", size: 40, bold: true, color: "FFFFFF", font: FONT_ARIAL })] }),
  new Paragraph({ spacing: { after: 120 },
    border: { bottom: { style: BorderStyle.SINGLE, size: 6, color: P.accent, space: 8 } },
    children: [new TextRun({ text: "Leistungsmanagement Grundsicherung (SGB XII) — Berechnung, Bescheid und Auszahlung", size: 22, color: "B0B8C0", font: FONT })] }),
  new Paragraph({ spacing: { before: 100, after: 30 },
    children: [
      new TextRun({ text: "Gegenstand: ", bold: true, size: 22, color: "FFFFFF", font: FONT }),
      new TextRun({ text: "Nachweise zu Berechnung, Bescheiderteilung und Auszahlungskette aus zehn Qualitätsschichten; aktuelle Laufresultate und strukturierte Belege echter LLM-Aufrufe werden automatisch geprüft.", size: 21, color: P.aufDunkel, font: FONT }),
    ] }),
  new Paragraph({ spacing: { before: 90, after: 100 },
    children: [
      new TextRun({ text: allesBestanden ? "Prüfungsverdict: BESTANDEN — vorliegende Nachweise gültig; LLM-Prüfung ist eine Stichprobe."
                                          : `Prüfungsverdict: BEDINGT — ${warnungen.length} ${warnungen.length === 1 ? "Hinweis" : "Hinweise"}, siehe unten.`,
        bold: true, size: 24, color: allesBestanden ? P.aufDunkelGruen : P.aufDunkelWarn, font: FONT }),
    ] }),
];

const hakenTabelle = new Table({
  width: { size: 100, type: WidthType.PERCENTAGE },
  layout: TableLayoutType.FIXED,
  borders: {
    top: { style: BorderStyle.SINGLE, size: 4, color: P.table.accentLine },
    bottom: { style: BorderStyle.SINGLE, size: 4, color: P.table.accentLine },
    left: { style: BorderStyle.NONE }, right: { style: BorderStyle.NONE },
    insideHorizontal: { style: BorderStyle.SINGLE, size: 1, color: P.table.innerLine },
    insideVertical: { style: BorderStyle.NONE },
  },
  rows: PRUEFSCHICHTEN.map(s => new TableRow({
    cantSplit: true,
    children: [
      new TableCell({
        width: { size: 6, type: WidthType.PERCENTAGE },
        margins: { top: 40, bottom: 40, left: 120, right: 60 },
        shading: { type: ShadingType.CLEAR, fill: s.flaeche },
        children: [new Paragraph({ alignment: AlignmentType.CENTER,
          children: [new TextRun({ text: s.haken, bold: true, size: 30, color: s.farbe, font: FONT_ARIAL })] })],
      }),
      new TableCell({
        width: { size: 94, type: WidthType.PERCENTAGE },
        margins: { top: 40, bottom: 40, left: 120, right: 120 },
        shading: { type: ShadingType.CLEAR, fill: s.flaeche },
        children: [
          new Paragraph({ spacing: { after: 40, line: 276 },
            children: [new TextRun({ text: s.titel, bold: true, size: 22, color: P.primary, font: FONT })] }),
          new Paragraph({ spacing: { line: 248 },
            children: [new TextRun({ text: s.zeile, size: 18, color: P.secondary, font: FONT })] }),
        ],
      }),
    ],
  })),
});

const erhebungsBlock = [
  new Paragraph({ spacing: { before: 120, after: 20, line: 252 },
    children: [new TextRun({ text: `Erhebungsstand (automatisch): erhoben am ${HEUTE} aus testsweet/logs (je Kategorie der neueste Lauf einschließlich Fehlern). LLM-Nachrechnung: ${LLM ? LLM.datum + " (" + LLM.datei + ")" : "keine"}. MD-Datenstand: ${MD ? MD.datum + " (" + MD.ordner + ")" : "keiner"}. Manuell gepflegt: mvn-Testzahl ${MVN_TESTS} (Stand ${MVN_STAND}), Web-Modus-Referenzläufe.`, size: 17, color: P.aufDunkelNeben, font: FONT })] }),
];
if (warnungen.length > 0) {
  erhebungsBlock.push(new Paragraph({ spacing: { before: 80, after: 20 },
    children: [new TextRun({ text: "Hinweise vor einer belastbaren Ausstellung:", bold: true, size: 20, color: P.aufDunkelWarn, font: FONT })] }));
  for (const w of warnungen) {
    erhebungsBlock.push(new Paragraph({ spacing: { after: 30, line: 264 },
      children: [new TextRun({ text: "• " + w, size: 18, color: P.aufDunkelWarn, font: FONT })] }));
  }
}

const fuss = [
  new Paragraph({ spacing: { before: 140 },
    border: { top: { style: BorderStyle.SINGLE, size: 2, color: P.table.innerLine, space: 8 } },
    children: [
      new TextRun({ text: "Referenzjahr 2026 · Rechtsquellenstand: doc/rechtsgrundlagen.md (2026-10-10) · Ausgestellt am " + HEUTE, size: 18, color: P.aufDunkelNeben, font: FONT }),
    ] }),
];

// ─── Detailseiten ────────────────────────────────────────────────────────────
function h1(text, ersteSeite) {
  return new Paragraph({
    heading: HeadingLevel.HEADING_1,
    pageBreakBefore: ersteSeite ? undefined : true,
    keepNext: true,
    spacing: { before: ersteSeite ? 120 : 200, after: 240, line: 312 },
    border: { bottom: { style: BorderStyle.SINGLE, size: 6, color: P.h2, space: 6 } },
    children: [new TextRun({ text, bold: true, size: 32, color: P.primary, font: FONT_ARIAL })] });
}
function h2(text) {
  return new Paragraph({
    heading: HeadingLevel.HEADING_2, keepNext: true,
    spacing: { before: 190, after: 90, line: 288 },
    children: [new TextRun({ text, bold: true, size: 25, color: P.h2, font: FONT_ARIAL })] });
}
function para(runs, opts = {}) {
  return new Paragraph({
    alignment: AlignmentType.LEFT,
    spacing: { line: 280, after: opts.after !== undefined ? opts.after : 80, before: opts.before || 0 },
    children: runs.map(r => new TextRun({ text: r.t, bold: !!r.b, italics: !!r.i, size: 22,
      color: r.warn ? P.warn : (r.b ? P.primary : P.body), font: FONT })),
  });
}
function listenpunkt(reference, titel, text) {
  return new Paragraph({
    numbering: { reference, level: 0 },
    alignment: AlignmentType.LEFT,
    keepLines: true,
    spacing: { line: 280, after: 100 },
    children: [
      new TextRun({ text: titel + ": ", bold: true, size: 22, color: P.primary, font: FONT }),
      new TextRun({ text, size: 22, color: P.body, font: FONT }),
    ],
  });
}

const details = [
  h1("Die zehn Qualitätsschichten im Einzelnen", true),
  para([{ t: "Zehn Prüfungen mit bewusst unterschiedlicher Fehlerabdeckung: Formelfehler (Goldwerte, LLM-Nachrechnung), Zusammenspiel mit Datenbank und Parallelität (Regression, testsweet, Last/Stress), Bedienoberfläche (Modus 2), Transaktionssteuerung (Unit-Tests) und Schwächen, die Tests nicht sehen (Reviews durch unterschiedliche KIs)." }]),
  para([{ t: "Nachweisprinzip: ", b: true }, { t: "Die neuesten Laufresultate werden einschließlich Fehlern berücksichtigt. Die LLM-Schicht benötigt strukturierte echte API-Belege mit exakter Fallabdeckung und unveränderten Quelldateien, Prüfauftrag und Gesetzesgrundlagen. Fehlende, abgebrochene, veraltete oder abweichende Nachweise erhalten keine Freigabe. Die mvn-Testzahl und Web-Modus-Referenzläufe bleiben ausdrücklich manuell gepflegt." }]),
];
PRUEFSCHICHTEN.forEach((s, i) => {
  details.push(h2(`${i + 1}. ${s.titel}`));
  details.push(para([{ t: s.zeile, i: true }], { after: 100 }));
  details.push(para([{ t: s.detail }]));
  if (s.detailListe) {
    for (const punkt of s.detailListe) details.push(listenpunkt("reviews", punkt.titel, punkt.text));
    details.push(para([{ t: s.detailQuelle }]));
  }
});
details.push(h2("Wie das Zertifikat erneuert wird"));
details.push(para([{ t: "Das Zertifikat wird in dieser Reihenfolge erneuert:" }]));
details.push(listenpunkt("erneuerung", "Testläufe fahren", "Die testsweet-GUI oder start.bat nutzen. Der Generator bewertet die neuesten Läufe je Kategorie einschließlich Fehlern."));
details.push(listenpunkt("erneuerung", "mvn test ausführen", "Im Hauptprojekt starten; Testzahl und Stand oben im Generator (MVN_TESTS/MVN_STAND) aktualisieren."));
details.push(listenpunkt("erneuerung", "LLM-Prüfung starten", "In testsweet konfigurieren und ausführen. Der Generator prüft strukturierte Request-/Response-Belege und Quelldatei-Hashes."));
details.push(listenpunkt("erneuerung", "Zertifikat erstellen", "Den Knopf in der GUI oder erstelle-zertifikat.bat nutzen. Bei Warnungen zuerst die Schritte 1–3 nachziehen."));
details.push(h2("Grenzen des Zertifikats"));
details.push(para([
  { t: "Die LLM-Nachrechnung ist eine dokumentierte Stichprobe, keine Vollprüfung aller Fälle und Regeläste. Die vollständigen je-Fall-Rechenwege stehen in den Markdown-Laufberichten. Nicht modellierte Fachrechtsregeln (Karenzzeit § 35, §§ 82 Abs. 4/5, § 43 Abs. 2, § 44 Abs. 2) sind kein Zertifikatsgegenstand. Web-Modus- und mvn-Zahlen sind als manuell gepflegt markiert." }]));

// ─── Dokument ────────────────────────────────────────────────────────────────
const doc = new Document({
  creator: "SDN.Leistung LMG",
  title: "Testzertifikat SDN.Leistung LMG",
  numbering: { config: [
    { reference: "reviews", levels: [{ level: 0, format: LevelFormat.DECIMAL, text: "%1.", alignment: AlignmentType.LEFT,
      style: { paragraph: { indent: { left: 620, hanging: 360 } } } }] },
    { reference: "erneuerung", levels: [{ level: 0, format: LevelFormat.DECIMAL, text: "%1.", alignment: AlignmentType.LEFT,
      style: { paragraph: { indent: { left: 620, hanging: 360 } } } }] },
  ] },
  styles: { default: { document: {
    run: { font: FONT, size: 22, color: P.body },
    paragraph: { spacing: { line: 312 } },
  } } },
  sections: [
    { // Seite 1: Zertifikat auf dunklem Grund
      properties: { page: { size: { width: 11906, height: 16838 }, margin: { top: 620, bottom: 520, left: 1134, right: 1134 } } },
      children: [new Table({
        width: { size: 100, type: WidthType.PERCENTAGE },
        layout: TableLayoutType.FIXED,
        borders: { top: { style: BorderStyle.NONE }, bottom: { style: BorderStyle.NONE },
          left: { style: BorderStyle.NONE }, right: { style: BorderStyle.NONE },
          insideHorizontal: { style: BorderStyle.NONE }, insideVertical: { style: BorderStyle.NONE } },
        rows: [new TableRow({
          children: [new TableCell({
            shading: { type: ShadingType.CLEAR, fill: P.bg }, borders: {
              top: { style: BorderStyle.NONE }, bottom: { style: BorderStyle.NONE },
              left: { style: BorderStyle.SINGLE, size: 12, color: P.accent },
              right: { style: BorderStyle.NONE } },
            margins: { top: 180, bottom: 180, left: 560, right: 560 },
            children: [
              ...zertifikat,
              new Paragraph({ spacing: { after: 100 },
                children: [new TextRun({ text: "Geprüfte Qualitätsschichten", bold: true, size: 22, color: "FFFFFF", font: FONT })] }),
              hakenTabelle,
              ...erhebungsBlock,
              ...fuss,
            ],
          })],
        })],
      })],
    },
    { // Seite 2 ff: Erklärungen
      properties: { type: SectionType.NEXT_PAGE,
        page: { size: { width: 11906, height: 16838 }, margin: { top: 1200, bottom: 1200, left: 1440, right: 1300 },
          pageNumbers: { start: 2 } } },
      footers: { default: new Footer({
        children: [new Paragraph({ alignment: AlignmentType.CENTER,
          children: [new TextRun({ children: [PageNumber.CURRENT], size: 18, color: "888888", font: FONT })] })] }) },
      children: details,
    },
  ],
});

const OUT = process.argv[2] || path.join(__dirname, "testzertifikat.docx");
Packer.toBuffer(doc).then(buf => {
  fs.writeFileSync(OUT, buf);
  // Erhebungsstand für GUI/Server-Dialog (gleiche Quelle wie das Dokument)
  fs.writeFileSync(path.join(WERKZEUG, "stand.json"), JSON.stringify({
    erhoben_am: HEUTE,
    verdict: allesBestanden ? "BESTANDEN" : "BEDINGT",
    laeufe: LAEUFE,
    llm: LLM,
    md_datenstand: MD,
    manuell: { mvn_tests: MVN_TESTS, mvn_stand: MVN_STAND },
    warnungen,
  }, null, 1));
  console.log("OK geschrieben:", OUT, buf.length, "Bytes; verdict:", allesBestanden ? "BESTANDEN" : `BEDINGT (${warnungen.length} ${warnungen.length === 1 ? "Hinweis" : "Hinweise"})`);
}).catch(e => { console.error("FEHLER:", e); process.exit(1); });
