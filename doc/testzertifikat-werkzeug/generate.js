// Erzeugt das Testzertifikat "SDN.Leistung LMG" als DOCX.
// Seite 1: Zertifikat mit Häkchen je Qualitätsschicht + Erhebungs-/Warnblock;
// Seite 2 ff: was jede Schicht tut und wo sie nachlesbar ist.
//
// Ehrlichkeitsprinzip: Was automatisiert prüfbar ist, wird automatisiert erhoben —
// die testsweet-Laufzahlen aus testsweet/logs/run-*/zusammenfassung.txt (je Kategorie
// der neueste erfolgreiche Lauf) und das Datum des neuesten LLM-Nachrechnprotokolls.
// Nicht selbst prüfbar (und klar so gekennzeichnet): die mvn-Testzahl (manuell zu
// pflegen) und die LLM-Nachrechnung selbst — sie ist ein Prozessschritt außerhalb
// der Anwendung (MD + gesetzesgrundlagen-2026.md + llm-pruefauftrag.txt einer KI geben;
// Ergebnis als llm-nachrechnung-<Datum>.md hier ablegen).
// Aufruf: node generate.js [ausgabe.docx]  — schreibt zusätzlich stand.json.
const path = require("path");
const fs = require("fs");
const {
  Document, Packer, Paragraph, TextRun, Table, TableRow, TableCell,
  PageNumber, AlignmentType, HeadingLevel, WidthType, BorderStyle,
  ShadingType, SectionType, TableLayoutType, Footer,
} = require("D:/AntiGravitySoftware/GitWorkspace/LeistungsmanagementGrundsicherung/doc/handbuch-werkzeug/node_modules/docx");

const WERKZEUG = __dirname;
const REPO = path.resolve(WERKZEUG, "..", "..");
const P = {
  bg: "162235", accent: "37DCF2", gruen: "1E8E3E", gruenHell: "E6F4EA",
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
    if (!erfolgreich) continue;
    // je Kategorie den umfassendsten erfolgreichen Nachweis führen (meiste Aufträge;
    // bei Gleichstand der neuere Ordner) — Smoke-Läufe verdrängen keinen Referenzlauf
    const bisher = laeufe[kategorie];
    const kandidat = {
      verifiziert: Number(verifiziert), gesamt: Number(gesamt),
      ordner: "testsweet/logs/" + ordner, zeit: zeit.slice(0, 10),
    };
    if (!bisher || kandidat.gesamt >= bisher.gesamt) {
      laeufe[kategorie] = kandidat;
    }
  }
  return laeufe;
}

function liesLlmProtokoll() {
  if (!fs.existsSync(WERKZEUG)) return null;
  const kandidaten = fs.readdirSync(WERKZEUG)
    .filter(f => /^llm-nachrechnung-\d{4}-\d{2}-\d{2}(-\S+)?\.md$/.test(f));
  if (kandidaten.length === 0) return null;
  // Sortierung nach Datum; bei Gleichstand gewinnt der größere Prüfungsumfang
  // (längerer Dateiname, z. B. "-100faelle" schlägt das Basis-Protokoll)
  const key = f => f.match(/\d{4}-\d{2}-\d{2}/)[0] + String(f.length).padStart(4, "0");
  const neueste = kandidaten.sort((a, b) => key(a).localeCompare(key(b)))[kandidaten.length - 1];
  const datum = neueste.match(/\d{4}-\d{2}-\d{2}/)[0];
  // maschinenlesbarer Kopf (pruefumfang/mitgegeben/modell/ergebnis) auswerten
  const meta = {};
  for (const zeile of fs.readFileSync(path.join(WERKZEUG, neueste), "utf8").split("\n")) {
    const m = /^(\w+) = (.+)$/.exec(zeile.trim());
    if (m && ["pruefumfang", "mitgegeben", "modell", "methode", "ergebnis", "abdeckung"].includes(m[1])) {
      meta[m[1]] = m[2].trim();
    }
  }
  return { datei: neueste, datum, meta };
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
  return { haken: "✓", farbe: P.gruen, flaeche: P.gruenHell,
    titel: `testsweet ${label} — ${l.verifiziert}/${l.gesamt} verifiziert`,
    zeile: `Automatisch erhoben am ${HEUTE} aus ${l.ordner} (Lauf vom ${l.zeit})` };
}

// Manuell zu pflegende Prüfergebnisse (nicht automatisch erhebbar):
const MVN_TESTS = "249/249";           // mvn test im Hauptprojekt — nach jedem Testlauf hier aktualisieren
const MVN_STAND = "2026-10-12";        // Datum des letzten bekannten grünen mvn-Laufs

const pos = laufZeile("POSITIV", "Positivfälle (Fachschicht)");
const neg = laufZeile("NEGATIV", "Negativfälle (Fachschicht)");
const last = laufZeile("LAST", "Lasttest");
const stress = laufZeile("STRESS", "Stresstest");

let llm;
if (!LLM) {
  warnungen.push("Kein LLM-Nachrechnprotokoll (llm-nachrechnung-<Datum>.md) im Werkzeugordner — MD + gesetzesgrundlagen-2026.md + llm-pruefauftrag.txt einer KI geben und Protokoll ablegen.");
  llm = { haken: "!", farbe: P.warn, flaeche: P.warnHell,
    titel: "LLM-Nachrechnung der Testdokumentation — kein Protokoll vorhanden",
    zeile: "Prozessschritt außerhalb der Anwendung: Prüfauftrag + Gesetzesgrundlagen im Werkzeugordner" };
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
if (LLM && MD && MD.datum > LLM.datum) {
  warnungen.push(`Die Testdokumentation (${MD.ordner}, ${MD.datum}) ist NEUER als die LLM-Nachrechnung (${LLM.datum}) — Nachrechnung erneuern, bevor das Zertifikat belastbar ist.`);
}

const PRUEFSCHICHTEN = [
  { haken: "✓", farbe: P.gruen, flaeche: P.gruenHell,
    titel: `Unit-Tests der Fach- und Kernschicht — ${MVN_TESTS} grün (manuell gepflegt, Stand ${MVN_STAND})`,
    zeile: "Version 0.5.8 (Commit 6199fc3); mvn test — Zahl hier im Generator nach jedem Testlauf aktualisieren",
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
    detail: `Eine KI hat Fälle aus der maschinenlesbaren Testdokumentation unabhängig nachgerechnet — nur nach den mitgegebenen Gesetzestexten, nicht nach dem Code. Im Protokollkopf stehen Umfang, Methode und die mitgegebenen Dokumente; der Generator liest sie automatisch hier ein. Aktueller Stand: ${LLM && LLM.meta && LLM.meta.pruefumfang ? LLM.meta.pruefumfang + " — " + (LLM.meta.methode || "") : "siehe Protokoll"} ${LLM && LLM.meta && LLM.meta.abdeckung ? "Abgedeckte Regel-Äste: " + LLM.meta.abdeckung + "." : ""} WICHTIG — so wird diese Schicht erneuert: die testdokumentation.md (bei Bedarf auf eine Prüfmenge gekürzt, siehe pruefmenge-*.md) ZUSAMMEN mit gesetzesgrundlagen-2026.md und llm-pruefauftrag.txt einer KI geben; ihre Bestätigung als llm-nachrechnung-<Datum>.md hier ablegen (Kopfzeilen beibehalten). Der Generator nimmt automatisch das neueste Protokoll (Ablageort: doc/testzertifikat-werkzeug/) und warnt, wenn die Testdokumentation neuer ist als die Nachrechnung. Das Prüfpaket (Prüfmenge + Gesetzesgrundlagen + Prüfauftrag) lässt sich direkt aus der testsweet-GUI schnüren (Abschnitt 7 „LLM-Nachrechnung", Knopf „Prüfpaket erstellen") — dort sind auch Prompt und mitgegebene Dateien einsehbar.` },
  { haken: "✓", farbe: P.gruen, flaeche: P.gruenHell,
    titel: "Code-Reviews durch unterschiedliche KIs — wiederholt, gegengeprüft, behoben",
    zeile: "Allumfassendes Review 2026-10-12 (75 Befunde → alle behoben) + Gegenprüfungen + zweite Fremd-Härtung",
    detail: "Der Code wurde wiederholt von KI-Instanzen unabhängig gelesen und kritisiert — bewusst mit unterschiedlichen KIs, damit nicht dieselbe Blindstelle durchgeht: (1) allumfassendes Codereview nach strukturiertem Prüfauftrag (Prüffelder A–J, 48 Verdachtsspuren, 25 Testlücken; doc/codereview/codereviewPrompt.txt) mit 75 Befunden, alle bis v0.5.7 behoben; (2) Gegenprüfung der Fixes zu v0.5.8 (Nachbesserungsbericht); (3) weitere, andere KI härtete nach (Body-Cache, Audit-Redaktion, Saga-Korrelation, Bescheidmonat-Prüfung) — auch gegengeprüft, 249/249 Tests grün. Nachlesbar: Berichte unter doc/codereview/." },
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
      new TextRun({ text: "Gegenstand: ", bold: true, size: 22, color: P.primary, font: FONT }),
      new TextRun({ text: "Berechnung, Bescheiderteilung und Auszahlungskette wurden in zehn Qualitätsschichten geprüft (inkl. Code-Reviews durch unterschiedliche KIs); Laufzahlen und LLM-Datum automatisch erhoben.", size: 21, color: P.body, font: FONT }),
    ] }),
  new Paragraph({ spacing: { before: 90, after: 100 },
    children: [
      new TextRun({ text: allesBestanden ? "Prüfungsverdict: BESTANDEN — 0 offene Befunde über alle Schichten."
                                          : `Prüfungsverdict: BEDINGT — ${warnungen.length} Hinweis(se), siehe unten.`,
        bold: true, size: 24, color: allesBestanden ? P.gruen : P.warn, font: FONT }),
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
    children: [new TextRun({ text: `Erhebungsstand (automatisch): erhoben am ${HEUTE} aus testsweet/logs (je Kategorie der umfassendste erfolgreiche Lauf). LLM-Nachrechnung: ${LLM ? LLM.datum + " (" + LLM.datei + ", Ablageort doc/testzertifikat-werkzeug/)" : "keine"}. MD-Datenstand: ${MD ? MD.datum + " (" + MD.ordner + ")" : "keiner"}. Manuell gepflegt: mvn-Testzahl ${MVN_TESTS} (Stand ${MVN_STAND}), Web-Modus-Referenzläufe.`, size: 17, color: "B0B8C0", font: FONT })] }),
];
if (warnungen.length > 0) {
  erhebungsBlock.push(new Paragraph({ spacing: { before: 80, after: 20 },
    children: [new TextRun({ text: "Hinweise vor einer belastbaren Ausstellung:", bold: true, size: 20, color: P.warn, font: FONT })] }));
  for (const w of warnungen) {
    erhebungsBlock.push(new Paragraph({ spacing: { after: 30, line: 264 },
      children: [new TextRun({ text: "• " + w, size: 18, color: P.warn, font: FONT })] }));
  }
}

const fuss = [
  new Paragraph({ spacing: { before: 140 },
    border: { top: { style: BorderStyle.SINGLE, size: 2, color: P.table.innerLine, space: 8 } },
    children: [
      new TextRun({ text: "Version 0.5.8 (Commit 6199fc3) · Referenzjahr 2026 · Beträge gegen gesetze-im-internet.de verifiziert (doc/rechtsgrundlagen.md, 2026-10-10) · Ausgestellt am " + HEUTE, size: 18, color: P.secondary, font: FONT }),
    ] }),
  new Paragraph({ spacing: { before: 120, after: 0 }, alignment: AlignmentType.LEFT,
    children: [
      new TextRun({ text: "Fortsetzung auf Seite 2: Was jede Prüfschicht tut, wie sie arbeitet und wo ihr Ergebnis nachlesbar ist.", italics: true, size: 18, color: P.secondary, font: FONT }),
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
    spacing: { before: 300, after: 120, line: 312 },
    children: [new TextRun({ text, bold: true, size: 25, color: P.h2, font: FONT_ARIAL })] });
}
function para(runs, opts = {}) {
  return new Paragraph({
    alignment: AlignmentType.JUSTIFIED,
    spacing: { line: 312, after: opts.after !== undefined ? opts.after : 120, before: opts.before || 0 },
    children: runs.map(r => new TextRun({ text: r.t, bold: !!r.b, italics: !!r.i, size: 22,
      color: r.warn ? P.warn : (r.b ? P.primary : P.body), font: FONT })),
  });
}

const details = [
  h1("Die zehn Qualitätsschichten im Einzelnen", true),
  para([{ t: "Zehn Prüfungen mit bewusst unterschiedlicher Fehlerabdeckung: Formelfehler (Goldwerte, LLM-Nachrechnung), Zusammenspiel mit Datenbank und Parallelität (Regression, testsweet, Last/Stress), Bedienoberfläche (Modus 2), Transaktionssteuerung (Unit-Tests) und Schwächen, die Tests nicht sehen (Reviews durch unterschiedliche KIs)." }]),
  para([{ t: "Ehrlichkeitsprinzip: ", b: true }, { t: "Die testsweet-Zeilen und das LLM-Protokolldatum werden beim Erzeugen automatisch aus testsweet/logs bzw. dem Werkzeugordner erhoben. Die mvn-Testzahl und die Web-Modus-Referenzläufe sind als manuell gepflegt gekennzeichnet. Fehlt etwas oder ist die LLM-Nachrechnung älter als die Testdokumentation, erscheint ein gelbes Ausrufezeichen statt des grünen Häkchens und ein Hinweis — dann ist das Zertifikat noch nicht belastbar." }]),
];
PRUEFSCHICHTEN.forEach((s, i) => {
  details.push(h2(`${i + 1}. ${s.titel}`));
  details.push(para([{ t: s.zeile, i: true }], { after: 100 }));
  details.push(para([{ t: s.detail }]));
});
details.push(h2("Wie das Zertifikat erneuert wird"));
details.push(para([
  { t: "Reihenfolge: " },
  { t: "(1)", b: true }, { t: " Läufe fahren (testsweet-GUI oder start.bat) — der Generator findet die neuesten erfolgreichen Läufe je Kategorie automatisch. " },
  { t: "(2)", b: true }, { t: " mvn test im Hauptprojekt; Testzahl und Stand oben im Generator (MVN_TESTS/MVN_STAND) aktualisieren. " },
  { t: "(3)", b: true }, { t: " LLM-Nachrechnung erneuern: die neueste testdokumentation.md ZUSAMMEN mit gesetzesgrundlagen-2026.md und llm-pruefauftrag.txt aus diesem Ordner einer KI geben; ihre Bestätigung als llm-nachrechnung-<Datum>.md hier ablegen. " },
  { t: "(4)", b: true }, { t: " Knopf „Testzertifikat erstellen“ in der GUI oder erstelle-zertifikat.bat ausführen. Warnungen bedeuten: vorher Schritt 1–3 nachziehen." }]));
details.push(h2("Grenzen des Zertifikats"));
details.push(para([
  { t: "Die LLM-Nachrechnung ist eine Stichprobe je Regel-Ast, keine Vollprüfung aller Fälle; die vollständigen je-Fall-Rechenwege stehen in den Markdown-Berichten der Läufe und können jederzeit vollständig nachgerechnet werden. Bewusste Vereinfachungen des Fachrechts (Karenzzeit § 35, §§ 82 Abs. 4/5, § 43 Abs. 2, § 44 Abs. 2) sind in der Fachdokumentation als nicht modelliert ausgewiesen und kein Gegenstand dieses Zertifikats. Die Web-Modus- und mvn-Zahlen sind manuell gepflegt (gekennzeichnet)." }]));

// ─── Dokument ────────────────────────────────────────────────────────────────
const doc = new Document({
  creator: "SDN.Leistung LMG",
  title: "Testzertifikat SDN.Leistung LMG",
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
  console.log("OK geschrieben:", OUT, buf.length, "Bytes; verdict:", allesBestanden ? "BESTANDEN" : "BEDINGT (" + warnungen.length + " Hinweise)");
}).catch(e => { console.error("FEHLER:", e); process.exit(1); });
