// Generiert das Dokument "So wird ein SozialFall berechnet" (SDN.Leistung LMG) als DOCX.
// Aufruf: node generate.js <ausgabe.docx>
// Stil und Bausteine wie testsweet-werkzeug/generate.js (Hausstil des Projekts).
const path = require("path");
const fs = require("fs");
const {
  Document, Packer, Paragraph, TextRun, Table, TableRow, TableCell,
  Header, Footer, PageNumber, NumberFormat, AlignmentType, HeadingLevel,
  WidthType, BorderStyle, ShadingType, SectionType, TableOfContents,
  LevelFormat, TableLayoutType, ImageRun,
} = require("D:/AntiGravitySoftware/GitWorkspace/LeistungsmanagementGrundsicherung/doc/handbuch-werkzeug/node_modules/docx");

// ─── Palette (wie testsweet-Handbuch / Klassenueberblick) ───────────────────
const P = {
  bg: "162235", accent: "37DCF2",
  cover: { titleColor: "FFFFFF", subtitleColor: "B0B8C0", metaColor: "90989F", footerColor: "687078" },
  table: { headerBg: "1B6B7A", headerText: "FFFFFF", accentLine: "1B6B7A", innerLine: "C8DDE2", surface: "EDF3F5" },
  primary: "0E2A33", h2: "1B6B7A", body: "000000", secondary: "5A6B73", code: "0B4A56",
};
const FONT_BODY = { ascii: "Calibri", hAnsi: "Calibri" };
const FONT_CODE = { ascii: "Consolas", hAnsi: "Consolas" };

// ─── Bausteine ───────────────────────────────────────────────────────────────
function seg(segs) {
  if (typeof segs === "string") segs = [segs];
  return segs.map(s => {
    if (typeof s === "string") return new TextRun({ text: s, size: 22, color: P.body, font: FONT_BODY });
    if (s.c !== undefined) return new TextRun({ text: s.c, size: 20, color: P.code, font: FONT_CODE });
    if (s.b !== undefined) return new TextRun({ text: s.b, bold: true, size: 22, color: P.primary, font: FONT_BODY });
    if (s.i !== undefined) return new TextRun({ text: s.i, italics: true, size: 22, color: P.body, font: FONT_BODY });
    return new TextRun({ text: String(s), size: 22, color: P.body, font: FONT_BODY });
  });
}
function para(segs, opts = {}) {
  return new Paragraph({
    alignment: AlignmentType.JUSTIFIED,
    spacing: { line: 312, after: opts.after !== undefined ? opts.after : 120, before: opts.before || 0 },
    keepNext: opts.keepNext || undefined,
    children: seg(segs),
  });
}
function h1(text, neueSeite) {
  return new Paragraph({
    heading: HeadingLevel.HEADING_1,
    pageBreakBefore: neueSeite || undefined,
    keepNext: true,
    spacing: { before: neueSeite ? 120 : 400, after: 240, line: 312 },
    border: { bottom: { style: BorderStyle.SINGLE, size: 6, color: P.h2, space: 6 } },
    children: [new TextRun({ text, bold: true, size: 32, color: P.primary, font: { ascii: "Arial", hAnsi: "Arial" } })],
  });
}
function h2(text) {
  return new Paragraph({
    heading: HeadingLevel.HEADING_2,
    keepNext: true,
    spacing: { before: 320, after: 140, line: 312 },
    children: [new TextRun({ text, bold: true, size: 26, color: P.h2, font: { ascii: "Arial", hAnsi: "Arial" } })],
  });
}
function bullet(segs) {
  return new Paragraph({
    bullet: { level: 0 },
    alignment: AlignmentType.JUSTIFIED,
    spacing: { line: 312, after: 80 },
    children: seg(segs),
  });
}
function numList(reference) {
  return (segs) => new Paragraph({
    numbering: { reference, level: 0 },
    alignment: AlignmentType.JUSTIFIED,
    spacing: { line: 312, after: 100 },
    children: seg(segs),
  });
}
function tabelle(headers, rows, widths, opts = {}) {
  const headerRow = new TableRow({
    tableHeader: true, cantSplit: true,
    children: headers.map((t, i) => new TableCell({
      children: [new Paragraph({ spacing: { line: 276 }, children: [new TextRun({ text: t, bold: true, size: 20, color: P.table.headerText, font: FONT_BODY })] })],
      shading: { type: ShadingType.CLEAR, fill: P.table.headerBg },
      margins: { top: 70, bottom: 70, left: 120, right: 120 },
      width: { size: widths[i], type: WidthType.PERCENTAGE },
    })),
  });
  const dataRows = rows.map((r, ri) => new TableRow({
    cantSplit: true,
    children: r.map((t, i) => new TableCell({
      children: [new Paragraph({ spacing: { line: 276 }, children: [new TextRun({ text: t, size: 20, color: P.body, font: FONT_BODY })] })],
      shading: opts.zebra && ri % 2 === 0 ? { type: ShadingType.CLEAR, fill: P.table.surface } : undefined,
      margins: { top: 60, bottom: 60, left: 120, right: 120 },
      width: { size: widths[i], type: WidthType.PERCENTAGE },
    })),
  }));
  return new Table({
    width: { size: 100, type: WidthType.PERCENTAGE },
    borders: {
      top: { style: BorderStyle.SINGLE, size: 4, color: P.table.accentLine },
      bottom: { style: BorderStyle.SINGLE, size: 4, color: P.table.accentLine },
      left: { style: BorderStyle.NONE }, right: { style: BorderStyle.NONE },
      insideHorizontal: { style: BorderStyle.SINGLE, size: 1, color: P.table.innerLine },
      insideVertical: { style: BorderStyle.NONE },
    },
    rows: [headerRow, ...dataRows],
  });
}
function tabellenTitel(text) {
  return new Paragraph({
    keepNext: true, spacing: { before: 160, after: 80, line: 312 },
    children: [new TextRun({ text, bold: true, size: 20, color: P.secondary, font: FONT_BODY })],
  });
}
// Abbildung: Bild zentriert + Unterschrift (Pixelmasse aus bild_masse.json)
const BILD_ORDNER = path.join(__dirname, "bilder") + "/";
const BILD_MASSE = JSON.parse(fs.readFileSync(path.join(__dirname, "bild_masse.json"), "utf8"));
function abbildung(datei, breite, unterschrift) {
  const m = BILD_MASSE[datei];
  const hoehe = Math.round(breite * m.h / m.w);
  return [
    new Paragraph({
      alignment: AlignmentType.CENTER,
      keepNext: true,
      spacing: { before: 200, after: 60 },
      children: [new ImageRun({
        data: fs.readFileSync(BILD_ORDNER + datei),
        transformation: { width: breite, height: hoehe },
        type: "png",
      })],
    }),
    new Paragraph({
      alignment: AlignmentType.CENTER,
      spacing: { after: 160 },
      children: [new TextRun({ text: unterschrift, size: 18, color: P.secondary, italics: true, font: FONT_BODY })],
    }),
  ];
}

// ─── Deckblatt (Rezept R1 aus dem Klassenueberblick) ────────────────────────
const NB = { style: BorderStyle.NONE, size: 0, color: "FFFFFF" };
const noBorders = { top: NB, bottom: NB, left: NB, right: NB };
const allNoBorders = { top: NB, bottom: NB, left: NB, right: NB, insideHorizontal: NB, insideVertical: NB };
function splitTitleLines(title, charsPerLine) {
  if (title.length <= charsPerLine) return [title];
  const breakAfter = new Set([..."-_/ ", "\t", ",", ".", ";"]);
  const lines = [];
  let remaining = title;
  while (remaining.length > charsPerLine) {
    let breakAt = -1;
    for (let i = charsPerLine; i >= Math.floor(charsPerLine * 0.6); i--) {
      if (i < remaining.length && breakAfter.has(remaining[i - 1])) { breakAt = i; break; }
    }
    if (breakAt === -1) breakAt = charsPerLine;
    lines.push(remaining.slice(0, breakAt).trim());
    remaining = remaining.slice(breakAt).trim();
  }
  if (remaining) lines.push(remaining);
  if (lines.length > 1 && lines[lines.length - 1].length <= 2) {
    const last = lines.pop();
    lines[lines.length - 1] += " " + last;
  }
  return lines;
}
function calcTitleLayout(title, maxWidthTwips, preferredPt = 40, minPt = 24) {
  const charsPerLine = pt => Math.floor(maxWidthTwips / (pt * 12));
  let titlePt = preferredPt, lines;
  while (titlePt >= minPt) {
    const cpl = charsPerLine(titlePt);
    if (cpl < 2) { titlePt -= 2; continue; }
    lines = splitTitleLines(title, cpl);
    if (lines.length <= 3) break;
    titlePt -= 2;
  }
  if (!lines || lines.length > 3) { lines = splitTitleLines(title, charsPerLine(minPt)); titlePt = minPt; }
  return { titlePt, titleLines: lines };
}
function buildCoverR1(config) {
  const C = config.palette;
  const padL = 1200, padR = 800;
  const availableWidth = 11906 - padL - padR - 300;
  const { titlePt, titleLines } = calcTitleLayout(config.title, availableWidth, 40, 24);
  const titleSize = titlePt * 2;
  const SAFETY = 1700, pageHeight = 16838;
  const contentHeight = titleLines.length * (titlePt * 23 + 200) + (config.subtitle ? 876 : 0)
    + (config.englishLabel ? 807 : 0) + (config.metaLines || []).length * 330 + 400 + 900;
  const safeRemaining = Math.max(pageHeight - SAFETY - contentHeight, 400);
  const bottomSpacing = Math.max(Math.floor(safeRemaining * 0.45), 800);
  const topSpacing = Math.max(safeRemaining - bottomSpacing, 400);
  const accentLeft = { style: BorderStyle.SINGLE, size: 8, color: C.accent, space: 12 };
  const children = [];
  children.push(new Paragraph({ spacing: { before: topSpacing } }));
  if (config.englishLabel) {
    children.push(new Paragraph({
      indent: { left: padL, right: padR }, spacing: { after: 500 },
      border: { bottom: { style: BorderStyle.SINGLE, size: 6, color: C.accent, space: 8 } },
      children: [new TextRun({ text: config.englishLabel.split("").join(" "), size: 18, color: C.accent, font: FONT_BODY, characterSpacing: 40 })],
    }));
  }
  for (let i = 0; i < titleLines.length; i++) {
    children.push(new Paragraph({
      indent: { left: padL },
      spacing: { after: i < titleLines.length - 1 ? 100 : 300, line: Math.ceil(titlePt * 23), lineRule: "atLeast" },
      children: [new TextRun({ text: titleLines[i], size: titleSize, bold: true, color: C.cover.titleColor, font: { ascii: "Arial", hAnsi: "Arial" } })],
    }));
  }
  if (config.subtitle) {
    children.push(new Paragraph({
      indent: { left: padL }, spacing: { after: 800 },
      children: [new TextRun({ text: config.subtitle, size: 24, color: C.cover.subtitleColor, font: FONT_BODY })],
    }));
  }
  for (const line of (config.metaLines || [])) {
    children.push(new Paragraph({
      indent: { left: padL + 200 }, spacing: { after: 80 },
      border: { left: accentLeft },
      children: [new TextRun({ text: line, size: 24, color: C.cover.metaColor, font: FONT_BODY })],
    }));
  }
  children.push(new Paragraph({ spacing: { before: bottomSpacing } }));
  children.push(new Paragraph({
    indent: { left: padL, right: padR },
    border: { top: { style: BorderStyle.SINGLE, size: 2, color: C.accent, space: 8 } },
    spacing: { before: 200 },
    children: [
      new TextRun({ text: config.footerLeft || "", size: 16, color: C.cover.footerColor, font: FONT_BODY }),
      new TextRun({ text: "                                                                  ", size: 16 }),
      new TextRun({ text: config.footerRight || "", size: 16, color: C.cover.footerColor, font: FONT_BODY }),
    ],
  }));
  return [new Table({
    width: { size: 100, type: WidthType.PERCENTAGE },
    layout: TableLayoutType.FIXED,
    borders: allNoBorders,
    rows: [new TableRow({
      height: { value: 16838, rule: "exact" },
      children: [new TableCell({
        shading: { type: ShadingType.CLEAR, fill: C.bg }, borders: noBorders, verticalAlign: "top",
        children,
      })],
    })],
  })];
}

// ─── Inhalt ──────────────────────────────────────────────────────────────────
const schritt = numList("schritte");
const schrittB = numList("schritteB");
const schrittC = numList("schritteC");

const inhalt = [
  // ══ 1 ══
  h1("1 Worum geht es?"),
  para(["Die Grundsicherung im Alter und bei Erwerbsminderung ist eine Sozialleistung nach dem ", { b: "Sozialgesetzbuch XII (SGB XII)" }, ": Wer nicht genug Rente oder Einkommen hat, um zu leben, bekommt vom Sozialamt einen monatlichen Betrag. Dieses Dokument erklärt — so einfach wie möglich und so genau wie nötig —, wie die Fachanwendung ", { b: "SDN.Leistung" }, " diesen Betrag berechnet. Alle Beträge und Regeln entsprechen dem Stand 2026 (Version 0.5.8 des Projekts)."]),
  para(["Zwei Begriffe tragen das ganze Thema und sollten zuerst sitzen:"], { keepNext: true }),
  tabellenTitel("Tabelle 1: Die Grundbegriffe"),
  tabelle(
    ["Begriff", "Was er bedeutet"],
    [
      ["Bedarfsgemeinschaft", "Der Haushalt als Ganzes — der „Vorgang“ im System. Sie hat ein Aktenzeichen, ein Vermögen, ein Bankkonto und ein monatliches Budget. Eine Bedarfsgemeinschaft kann aus einer Person allein bestehen oder aus mehreren (z. B. Ehepaar, Mutter mit zwei Kindern)."],
      ["SozialFall", "Eine Person innerhalb der Bedarfsgemeinschaft. Hier werden alles erfasst, was die Berechnung braucht: die Regelbedarfsstufe, Merkmale (Merkzeichen G, Schwangerschaft, Kinder), die Wohnkosten und das Einkommen."],
      ["Zahlbetrag", "Das Ergebnis: der Betrag, den die Bedarfsgemeinschaft für einen Monat bekommt. Er gilt immer für die ganze Bedarfsgemeinschaft, nicht für eine einzelne Person."],
      ["Bescheid", "Der schriftliche Verwaltungsakt, der den Zahlbetrag für einen Zeitraum (höchstens 12 Monate) feststellt. Das System speichert in ihm den kompletten Rechenweg zum Zeitpunkt der Erteilung — der spätere Druck zeigt genau diesen Stand."],
    ],
    [24, 76], { zebra: true }
  ),
  para(["Die ganze Berechnung — so kompliziert sie im Detail werden mag — folgt einer einzigen Grundformel:"], { before: 120, keepNext: true }),
  para([{ b: "Zahlbetrag  =  Bedarf  −  Einkommen  −  verwertbares Vermögen" }], { after: 160 }),
  para(["Der Rest dieses Dokuments füllt die drei Teile dieser Formel mit Leben: Was zählt zum Bedarf (Kapitel 4 bis 6), was passiert mit Einkommen (Kapitel 7) und Vermögen (Kapitel 8), und wie wird daraus ein Bescheid und schließlich eine Zahlung (Kapitel 2, 9 und 11)? Kapitel 10 rechnet fünf echte Fälle Schritt für Schritt durch."]),

  // ══ 2 ══
  h1("2 Vom Antrag bis zur Zahlung — wer macht was?"),
  para(["Bevor es um einzelne Beträge geht, der große Bogen: Welche Schritte durchläuft ein Antrag im System, und wer ist dabei beteiligt? Drei Rollen spielen mit: ein ", { b: "Sachbearbeiter" }, ", der den Fall erfährt und bearbeitet, eine ", { b: "zweite Person" }, ", die jede Auszahlung freigibt (Vier-Augen-Prinzip), und ein ", { b: "Administrator" }, ", der die freigegebenen Zahlungen im Kassenlauf an die Kasse übergibt. Jeder dieser Schritte wird im Buchungsjournal festgehalten — mit Kennung, Zeitpunkt und Betrag."], { keepNext: true }),
  ...abbildung("ablauf_antrag_bis_zahlung.png", 540, "Abbildung 1: Ablaufdiagramm — die sieben Schritte vom Antrag bis zur ausgezahlten Leistung"),
  para([{ b: "So liest man das Bild: " }, "Oben die Erfassungs- und Bescheidphase, unten die Auszahlungsphase. Wichtig ist die Trennung bei Schritt 4: Das Anordnen einer Auszahlung schickt noch ", { i: "kein" }, " Geld — die Zahlung liegt im Status ", { c: "ANGELEGT" }, " und wird erst nach der Freigabe durch eine zweite Person (", { c: "BEREIT" }, ") und dem Kassenlauf (", { c: "UEBERGEBEN" }, ") tatsächlich überwiesen. So ist das Vier-Augen-Prinzip im System erzwungen, nicht nur vereinbart."]),
  para(["Noch zwei Garantien unterwegs: Ein Doppelklick auf „Auszahlung anordnen“ ist harmlos, weil jede Zahlung eine eindeutige Vorgangsnummer trägt — dieselbe Nummer ergibt nie eine zweite Zahlung. Und eine versehentlich angeordnete Zahlung lässt sich bis zur Übergabe stornieren; dann kommt das Restbudget und ein etwaiger Vermögenseinsatz zurück (Details in Kapitel 11)."]),

  // ══ 3 ══
  h1("3 Die Berechnung im Überblick"),
  para(["Die monatliche Berechnung selbst besteht aus drei Rechenschritten und einer Formel — einmal pro Person, dann summiert über die Bedarfsgemeinschaft:"], { keepNext: true }),
  ...abbildung("fluss_berechnung_gesamt.png", 540, "Abbildung 2: Flussdiagramm — die drei Rechenschritte A, B, C und die Formel zum Zahlbetrag"),
  para([{ b: "Schritt A" }, " baut den Bedarf einer Person auf: der Grundbetrag (Regelbedarf), eventuelle Zuschläge (Mehrbedarfe) und die anerkannten Wohnkosten. ", { b: "Schritt B" }, " stellt fest, wie viel vom Einkommen der Person auf die Grundsicherung angerechnet wird — Rente zählt vollständig, vom Arbeitslohn bleibt ein Teil übrig. ", { b: "Schritt C" }, " prüft das Vermögen der Bedarfsgemeinschaft: Nur was über einem geschützten Grundbetrag (Schonvermögen) liegt, mindert die Leistung."], { before: 60 }),
  para(["Die drei Schritte sind bewusst getrennt: Jeder Schritt ist ein eigenes Kapitel in diesem Dokument (4–8), jede Regel ist in der reinen Rechenklasse ", { c: "Leistungsberechnung" }, " umgesetzt und separat getestet. Und am Ende steht immer die Prüfung: Ist der Zahlbetrag positiv, wird bewilligt — ist er null oder negativ, decken Einkommen und Vermögen den Lebensunterhalt, und es gibt keinen Anspruch."]),

  // ══ 4 ══
  h1("4 Schritt 1: Der Regelbedarf — der Grundbetrag"),
  para(["Jede Person bekommt einen Grundbetrag nach ihrer ", { b: "Regelbedarfsstufe" }, ". Welcher Stufe eine Person entspricht, sagt die Anlage zu § 28 SGB XII — die Stufe 1 ist für alleinstehende Erwachsene, die Stufen 2 und 3 für Partner bzw. weitere Erwachsene im Haushalt und die Stufen 4 bis 6 für Kinder und Jugendliche. Der Sachbearbeiter erfasst die Stufe beim SozialFall; die Geldbeträge je Stufe und Jahr stehen in der Datenbank (Tabelle ", { c: "SATZSTAND" }, "). Ein Jahreswechsel ist dadurch nur eine neue Tabellenzeile — keine Codeänderung."], { keepNext: true }),
  tabellenTitel("Tabelle 2: Regelbedarfsstufen 2026 (Anlage zu § 28 SGB XII)"),
  tabelle(
    ["Stufe", "2026", "Typische Person"],
    [
      ["1", "563,00 EUR", "alleinstehende erwachsene Person"],
      ["2", "506,00 EUR", "Ehegatte/Lebenspartner (je Person im Paar)"],
      ["3", "451,00 EUR", "erwachsene Person ohne eigene Wohnung im Haushalt"],
      ["4", "471,00 EUR", "Jugendliche von 14 bis 17 Jahren"],
      ["5", "390,00 EUR", "Kinder von 6 bis 13 Jahren"],
      ["6", "357,00 EUR", "Kinder bis zur Vollendung des 6. Lebensjahres"],
    ],
    [12, 22, 66], { zebra: true }
  ),
  para([{ b: "Beispiel: " }, "Frau B. lebt allein (Stufe 1). Ihr Regelbedarf ist 563,00 EUR — das ist der Startwert, zu dem im nächsten Schritt Zuschläge und Wohnkosten kommen."], { before: 120 }),

  // ══ 5 ══
  h1("5 Schritt 2: Mehrbedarfe — die Zuschläge (§ 30 SGB XII)"),
  para(["Manche Menschen haben höhere Bedürfnisse: Eine gehbehinderte Person braucht für Wege ein Taxi, eine Schwangere braucht mehr Nahrung, eine alleinerziehende Mutter trägt allein die Verantwortung für mehrere Kinder. Dafür gibt es ", { b: "Mehrbedarfe" }, " — Zuschläge, die als Prozentsatz berechnet werden. Das Flussdiagramm zeigt die drei Prüfungen in Erfassungsreihenfolge:"], { keepNext: true }),
  ...abbildung("fluss_mehrbedarfe.png", 540, "Abbildung 3: Flussdiagramm — die Mehrbedarfe mit ihren Prozentsätzen, Deckeln und dem Warmwasser-Zuschlag"),
  para(["Alle Sätze im Überblick (2026):"], { before: 60, keepNext: true }),
  tabellenTitel("Tabelle 3: Die Mehrbedarfe im Einzelnen"),
  tabelle(
    ["Mehrbedarf", "Satz", "2026 in EUR", "Bedingung"],
    [
      ["Merkzeichen G (§ 30 Abs. 1)", "17 % des eigenen Regelbedarfs", "Stufe 1: 95,71", "erhebliche Gehbehinderung (Merkzeichen G)"],
      ["Schwangerschaft (§ 30 Abs. 2)", "17 % des eigenen Regelbedarfs", "Stufe 1: 95,71", "ab der 13. Schwangerschaftswoche; endet automatisch mit dem Folgemonat der Entbindung"],
      ["Alleinerziehend (§ 30 Abs. 3)", "36 % der Stufe 1 oder 12 % je Kind", "202,68 / 67,56 je Kind", "Kind unter 7 oder 2–3 Kinder unter 16 → 36 %; sonst 12 % je minderjährigem Kind; Basis ist immer die Stufe 1"],
      ["Höchstgrenze dazu (§ 30 Abs. 3)", "60 % der Stufe 1", "337,80", "der Alleinerziehenden-Zuschlag wird nie höher"],
      ["Eigener Deckel (§ 30 Abs. 6)", "100 % des Regelbedarfs", "Stufe 1: 563,00", "die Summe der drei Zuschläge oben ist nie höher als der eigene Regelbedarf"],
      ["Warmwasser, eigener Boiler (§ 30 Abs. 7)", "0,8 % – 2,3 % je Stufe", "Stufe 1: 12,95", "nur bei dezentraler Warmwassererzeugung; zählt NICHT in den Deckel"],
    ],
    [24, 20, 17, 39], { zebra: true }
  ),
  para([{ b: "Zwei fallen gern unter den Tisch. " }, "Erstens: Der Alleinerziehenden-Zuschlag rechnet ", { i: "immer" }, " mit der Stufe 1 als Basis — auch wenn die Mutter selbst in einer niedrigeren Stufe steht; die 36 % sind also immer 202,68 EUR. Zweitens: Der Warmwasser-Zuschlag für einen eigenen Boiler liegt ", { i: "außerhalb" }, " des Deckels — § 30 Abs. 6 begrenzt nur die Zuschläge nach Abs. 1 bis 5."], { before: 120 }),
  para([{ b: "Beispiel (Weiterfall 3 unten): " }, "Eine schwangere Frau mit Merkzeichen G in Stufe 1 bekommt 95,71 + 95,71 = 191,42 EUR Zuschlag; die Summe bleibt unter ihrem Regelbedarf (563 EUR), der Deckel greift also nicht. Der Warmwasser-Zuschlag käme noch obendrauf."]),

  // ══ 6 ══
  h1("6 Schritt 3: Wohnkosten — die KdU (§§ 42a, 35 SGB XII)"),
  para(["„KdU“ steht für ", { b: "Kosten der Unterkunft und Heizung" }, ": Kaltmiete, kalte Betriebskosten (Müll, Wasser, Treppenhausstrom …) und Heizkosten. Sie werden je Person erfasst und für die Bedarfsgemeinschaft summiert. Grundsätzlich gilt: Die tatsächliche Höhe wird anerkannt — aber nur, soweit sie ", { i: "angemessen" }, " ist. Zwei Fragen entscheiden:"], { keepNext: true }),
  ...abbildung("fluss_wohnkosten.png", 530, "Abbildung 4: Flussdiagramm — von den erfassten Wohnkosten bis zur anerkannten Höhe"),
  para([{ b: "Frage 1 — Wie wird das Warmwasser erzeugt? " }, "Steht im Haushalt ein eigener Boiler oder Durchlauferhitzer (dezentrale Erzeugung), ist das Warmwasser ", { i: "nicht" }, " Teil der Heizkosten — dafür gibt es den Warmwasser-Mehrbedarf aus Kapitel 5. Wird das Warmwasser zentral mit der Heizung erzeugt, steckt es in den Heizkosten; hier kann die Kommune örtlich einen Abzug festlegen (im System Standard: aus). Der Abzug und der Mehrbedarf schließen sich bewusst gegenseitig aus — sonst würde die Person doppelt gekürzt."], { before: 60 }),
  para([{ b: "Frage 2 — Ist die Miete angemessen? " }, "Die Kommune kann eine ", { b: "Angemessenheitsgrenze" }, " festlegen (im System ein Konfigurationswert). Sie gilt für die ", { i: "ganze" }, " Bedarfsgemeinschaft, nicht je Person. Wird sie überschritten, wird nur bis zur Grenze anerkannt; bei mehreren Personen füllt die erste erfasste Person die Grenze zuerst. Der Rest bleibt Eigenanteil — und im Bescheid wird der gekürzte Betrag mit einem „*“ gekennzeichnet, damit nachvollziehbar bleibt, dass hier ein Vorbehalt wirkt."]),
  para([{ i: "Bewusste Vereinfachung: " }, "Die Karenzzeit (ein Jahr Übergangsfrist bei unangemessener Wohnung) und die Absenkungsfrist des § 35 SGB XII sind nicht modelliert — sie stehen in der Doku unter „bekannte Grenzen“."]),

  // ══ 7 ══
  h1("7 Schritt 4: Einkommen anrechnen (§ 82 SGB XII)"),
  para(["Was die Person selbst einnimmt, wird auf die Grundsicherung angerechnet — Euro für Euro. Aber nicht das volle Bruttoeinkommen: Vorher werden legitime Abgaben abgezogen und ein Teil des Arbeitslohns bleibt geschützt. Die Kette im Bild:"], { keepNext: true }),
  ...abbildung("fluss_einkommen.png", 540, "Abbildung 5: Flussdiagramm — von Rente und Brutto-Verdienst zum anrechenbaren Einkommen"),
  para(["Die Regeln im Einzelnen:"], { before: 60, keepNext: true }),
  schritt([{ b: "Rente zählt vollständig. " }, "Betriebliche und gesetzliche Renten sowie ähnliche Einkünfte werden ohne Freibetrag voll angerechnet — sie sind das, wovon die Grundsicherung eigentlich leben soll."]),
  schritt([{ b: "Absetzbeträge zuerst (§ 82 Abs. 2). " }, "Vom Einkommen werden abgezogen: Steuern, Pflichtbeiträge zur Sozialversicherung, angemessene Versicherungsbeiträge und Ausgaben, die mit dem Erzielen des Einkommens verbunden sind (z. B. Fahrtkosten). Diese Beträge erfasst der Sachbearbeiter je Person."]),
  schritt([{ b: "Erwerbsfreibetrag (§ 82 Abs. 3). " }, "Wer aus Arbeit bezahlt wird, behält 30 % seines Brutto-Verdienstes — höchstens 281,50 EUR (die Hälfte der Stufe 1, 2026). Der Freibetrag soll Anreize schaffen: Arbeiten lohnt sich auch im Leistungsbezug."]),
  schritt([{ b: "Niemals unter null. " }, "Das Ergebnis wird auf mindestens 0 EUR begrenzt — das System zieht nie Geld ab, das die Person nicht hat. Eine Einkommensquelle kann die Grundsicherung auf null drücken, aber nie zu einer Rückzahlung führen."]),
  para(["Das Ergebnis heißt ", { b: "anrechenbares Einkommen" }, " und wird über alle Personen der Bedarfsgemeinschaft summiert. Es mindert den Zahlbetrag des Schritts C — nicht direkt den Bedarf: Die Berechnung stellt also immer erst den vollen Bedarf fest und kürzt dann."], { before: 60 }),

  // ══ 8 ══
  h1("8 Schritt 5: Vermögen prüfen (§§ 43, 90 SGB XII)"),
  para(["Bevor Leistungen fließen, ist das Vermögen der Bedarfsgemeinschaft dran — Sparbuch, Girokonto, Festgeld und ähnliches. Auch hier gilt: nicht alles, sondern nur der Teil, der über dem ", { b: "Schonvermögen" }, " liegt. Und dieses Vermögen wird nicht einfach weggenommen, sondern verbraucht:"], { keepNext: true }),
  ...abbildung("fluss_vermoegen.png", 540, "Abbildung 6: Flussdiagramm — Schonvermögen, verwertbares Vermögen und der Verbrauch über die Monate"),
  tabellenTitel("Tabelle 4: Das Schonvermögen (§ 90 Abs. 2 Nr. 9 SGB XII i. V. m. Barbetragsverordnung)"),
  tabelle(
    ["Person", "Geschützter Betrag"],
    [
      ["Person in Regelbedarfsstufe 1–3 (erwachsene Leistungsbezieher)", "10.000,00 EUR je Person"],
      ["Person in Regelbedarfsstufe 4–6 (überwiegend unterhaltene Person)", "500,00 EUR je Person"],
      ["Untergrenze, wenn keine erwachsene Person in Stufe 1–3 vorliegt", "10.000,00 EUR einmalig"],
    ],
    [64, 36], { zebra: true }
  ),
  para([{ b: "Der Verbrauch — der wichtigste Punkt dieses Kapitels: " }, "Der Überschuss über dem Schonvermögen mindert die Leistung ", { i: "nur im ersten Auszahlungsmonat" }, ". Mit der ersten Auszahlung verbraucht er sich: Der gespeicherte Vermögensbestand sinkt auf das Schonvermögen, und genau dieser Einsatz wird je Zahlung dokumentiert (Datenbankfeld ", { c: "VERMOEGENSEINSATZ" }, "). Ab dem Folgemonat fließt die volle Leistung. Wird die Zahlung storniert, wird genau der dokumentierte Einsatz zurückgebucht — der Bestand stimmt danach wieder."], { before: 120 }),
  para([{ b: "Beispiel: " }, "Frau V. hat 10.500 EUR auf dem Sparbuch und lebt allein. Schonvermögen: 10.000 EUR. Verwertbar: 500 EUR. Im Januar bekommt sie 500 EUR weniger, und ihr Sparbuchbestand sinkt im System auf 10.000 EUR — ab Februar ist der Fall vermögensneutral. Das komplette Halbjahr steht in Kapitel 9."]),

  // ══ 9 ══
  h1("9 Der Monatsplan — der Bescheid rechnet jeden Monat einzeln"),
  para(["Ein Bescheid gilt für einen Zeitraum von einem bis zwölf Monaten. Statt einen einzigen Betrag festzunageln, rechnet das System ", { b: "jeden Monat einzeln" }, " durch und speichert den kompletten Plan im Bescheid. So können bekannte Änderungen während des Zeitraums automatisch stimmen:"], { keepNext: true }),
  ...abbildung("monatsplan_zeitstrahl.png", 540, "Abbildung 7: Zeitstrahl — der Bewilligungsplan am Beispiel von Frau V. aus Kapitel 8"),
  para(["Drei Sonderfälle steckt der Monatsplan selbst:"], { before: 60, keepNext: true }),
  schrittB([{ b: "Satzwechsel zum Jahreswechsel: " }, "Für jeden Monat wird der dann gültige Satzstand aus der Datenbank gelesen — ändert der Gesetzgeber zum 1. Januar die Regelbedarfsstufen, stellt der Plan die Folgemonate automatisch auf die neuen Werte (ohne eine Änderung des Bescheids)."]),
  schrittB([{ b: "Schwangerschaftsende: " }, "Ist der Entbindungsmonat erfasst, entfällt der Schwangerschafts-Mehrbedarf ab dem Folgemonat automatisch — die Falldaten bleiben erhalten, nur der Plan rechnet ab da ohne diesen Zuschlag."]),
  schrittB([{ b: "Vermögensverbrauch: " }, "Wie in Kapitel 8 — der Vermögensabzug steht nur in dem Monat, in dem die erste Auszahlung erfolgt; danach rechnet der Plan ohne ihn."]),
  para(["Wichtig zur Abgrenzung: Der Plan deckt nur ", { i: "vorhersehbare" }, " Änderungen ab. Meldet die Person etwas Neues — etwa eine Erbschaft, einen Job oder einen Umzug —, kommt ein neuer Bescheid. Und der gedruckte Bescheid zeigt immer den ", { i: "gespeicherten" }, " Stand von der Erteilung, nicht die aktuelle Datenlage: Nachvollziehbarkeit vor Aktualität."], { before: 60 }),

  // ══ 10 ══
  h1("10 Durchgerechnete Beispiele"),
  para(["Fünf Fälle, Schritt für Schritt — dieselben Werte stehen als „Goldwerte“ in den automatischen Tests des Projekts (", { c: "GoldWerteTest" }, ") und wurden handschriftlich nachgerechnet. Alle Beträge in EUR, Stand 2026."]),
  h2("10.1 Frau B. — alleinstehend, ohne Einkommen"),
  tabellenTitel("Tabelle 5: Beispiel A — Standardfall"),
  tabelle(
    ["Schritt", "Rechnung", "Ergebnis"],
    [
      ["Regelbedarf (Stufe 1)", "—", "563,00"],
      ["+ Wohnkosten (Kaltmiete 350, Betriebskosten 60, Heizung 80)", "350 + 60 + 80", "490,00"],
      ["= Zahlbetrag (kein Einkommen, kein Vermögen)", "563,00 + 490,00", "1.053,00"],
    ],
    [56, 26, 18], { zebra: true }
  ),
  h2("10.2 Herr und Frau P. — Ehepaar in gemeinsamer Wohnung"),
  tabellenTitel("Tabelle 6: Beispiel B — Paar"),
  tabelle(
    ["Schritt", "Rechnung", "Ergebnis"],
    [
      ["Regelbedarf, je Person (Stufe 2)", "2 × 506,00", "1.012,00"],
      ["+ Wohnkosten der Bedarfsgemeinschaft", "—", "650,00"],
      ["= Zahlbetrag", "1.012,00 + 650,00", "1.662,00"],
    ],
    [56, 26, 18], { zebra: true }
  ),
  para(["Man beachte: Die Wohnkosten stehen einmal im Vorgang und werden nicht auf die Personen aufgeteilt — der Zahlbetrag gilt für die ganze Bedarfsgemeinschaft."], { before: 60 }),
  h2("10.3 Frau S. — alleinerziehend mit zwei Kindern"),
  tabellenTitel("Tabelle 7: Beispiel C — Alleinerziehende (Basis des Zuschlags ist immer die Stufe 1)"),
  tabelle(
    ["Schritt", "Rechnung", "Ergebnis"],
    [
      ["Mutter: Regelbedarf (Stufe 1)", "—", "563,00"],
      ["Mutter: + Alleinerziehenden-Zuschlag (2 Kinder unter 16 → 36 % der Stufe 1)", "0,36 × 563,00", "202,68"],
      ["Mutter: + Wohnkosten (Miete 500, Betriebskosten 90, Heizung 120)", "500 + 90 + 120", "710,00"],
      ["Mutter gesamt", "563,00 + 202,68 + 710,00", "1.475,68"],
      ["Kind 1 (Stufe 6) — ohne eigene Wohnkosten", "—", "357,00"],
      ["Kind 2 (Stufe 6)", "—", "357,00"],
      ["= Zahlbetrag der Bedarfsgemeinschaft", "1.475,68 + 357,00 + 357,00", "2.189,68"],
    ],
    [56, 26, 18], { zebra: true }
  ),
  h2("10.4 Herr E. — RBS 1 mit Berufstätigkeit (Erwerbsfreibetrag)"),
  tabellenTitel("Tabelle 8: Beispiel D — Erwerbstätiger mit Absetzbeträgen und Freibetrag"),
  tabelle(
    ["Schritt", "Rechnung", "Ergebnis"],
    [
      ["Regelbedarf (Stufe 1)", "—", "563,00"],
      ["+ Wohnkosten (Miete 400, Betriebskosten 70, Heizung 90)", "400 + 70 + 90", "560,00"],
      ["= Gesamtbedarf", "563,00 + 560,00", "1.123,00"],
      ["Brutto-Erwerbseinkommen", "—", "1.200,00"],
      ["− Absetzbeträge (Steuern 150, Sozialversicherung 200)", "150 + 200", "350,00"],
      ["− Erwerbsfreibetrag: 30 % von 1.200 = 360, aber höchstens 281,50", "min(360,00; 281,50)", "281,50"],
      ["= anrechenbares Einkommen", "1.200 − 350 − 281,50", "568,50"],
      ["= Zahlbetrag", "1.123,00 − 568,50", "554,50"],
    ],
    [56, 26, 18], { zebra: true }
  ),
  h2("10.5 Frau V. — mit Vermögen über dem Schonvermögen"),
  tabellenTitel("Tabelle 9: Beispiel E — Vermögensverbrauch über den Bewilligungszeitraum"),
  tabelle(
    ["Schritt", "Rechnung", "Ergebnis"],
    [
      ["Bedarf (Stufe 1 + Wohnkosten 300)", "563,00 + 300,00", "863,00"],
      ["Vermögen 10.500 − Schonvermögen 10.000 = verwertbar", "10.500 − 10.000", "500,00"],
      ["Januar: Zahlbetrag (Vermögen mindert)", "863,00 − 500,00", "363,00"],
      ["Nach der Januar-Auszahlung: Bestand auf Schonvermögen", "10.500 − 500", "10.000,00"],
      ["Februar bis Juni: Zahlbetrag je Monat (kein Abzug mehr)", "—", "863,00"],
    ],
    [56, 26, 18], { zebra: true }
  ),
  para(["Genau dieser Verlauf ist der Zeitstrahl in Abbildung 7. Würde Frau V. die Januar-Zahlung stornieren lassen, kämen die 500 EUR Vermögenseinsatz zurück — der Bestand stünde wieder bei 10.500 EUR."], { before: 60 }),

  // ══ 11 ══
  h1("11 Schutzregeln beim Zahlen"),
  para(["Zwischen dem fertigen Bescheid und dem Geld auf dem Konto liegen mehrere Schutzregeln. Sie sind in Kapitel 2 im Ablaufdiagramm eingezeichnet und werden hier in Kurzform zusammengestellt:"], { keepNext: true }),
  tabellenTitel("Tabelle 10: Die Schutzregeln der Auszahlung"),
  tabelle(
    ["Regel", "Was sie bewirkt"],
    [
      ["Vier-Augen-Prinzip", "Eine Zahlung wird immer ANGELEGT und muss durch eine zweite Person freigegeben werden (BEREIT). Die Freigabe durch die eigene erfassende Person weist das System ab — rechnerisch, nicht auf Vertrauen."],
      ["Idempotenz", "Jede Anordnung trägt eine eindeutige Vorgangsnummer. Wird dieselbe Nummer erneut geschickt (Doppelklick, wiederholter Request), kommt die vorhandene Zahlung zurück — es passiert nichts."],
      ["Budgetführung", "Die Bedarfsgemeinschaft hat ein monatliches Restbudget. Eine Auszahlung mindert es; über das Budget hinaus geht nichts raus. Storno bucht den Betrag zurück."],
      ["Sperren und Konkurrenz", "Buchen mehrere Sachbearbeiter gleichzeitig, ordnet die Datenbank die Zugriffe („SELECT … FOR UPDATE“). Technische Sperrkonflikte wiederholt die Transaktionssteuerung automatisch; gleichzeitige Masken-Änderungen bekommen eine Klartext-Meldung statt stillen Datenverlust."],
      ["Storno bis zur Übergabe", "Jede noch nicht übergebene Zahlung kann storniert werden (Status STORNIERT) — inklusive Rückbuchung von Restbudget und Vermögenseinsatz. Nach dem Kassenlauf geht nichts mehr zurück."],
      ["Revisionssicheres Journal", "Jeder Schritt — Anordnung, Freigabe, Übergabe, Storno, auch jede Einsicht in vollständige Falldaten — steht mit Kennung und Zeitpunkt im Journal."],
      ["SEPA-Prüfung", "IBAN und BIC werden mit der Modulo-97-Prüfziffer validiert (ISO 13616), bevor eine Zahlung angelegt wird."],
    ],
    [24, 76], { zebra: true }
  ),

  // ══ 12 ══
  h1("12 Wo rechnet das System das? — Verweise und Grenzen"),
  para(["Wer tiefer einsteigen will, findet jede in diesem Dokument beschriebene Regel an genau einer Stelle im Code — und jede davon in automatischen Tests. Die wichtigsten Adressen:"], { keepNext: true }),
  tabellenTitel("Tabelle 11: Rechenregeln, Code und Tests"),
  tabelle(
    ["Thema", "Code", "Test / Doku"],
    [
      ["Regelbedarf, Mehrbedarfe, KdU, Einkommen, Vermögen", "de.allgemein.lmg.calc.Leistungsberechnung", "LeistungsberechnungTest, GoldWerteTest"],
      ["Monatsplan (Satzwechsel, Entbindung, Vermögensverbrauch)", "de.allgemein.lmg.calc.Bewilligungsplan", "SzenarioRegressionTest, doc/regressionstests.md"],
      ["Regelbedarfsstufen je Jahr", "Tabelle SATZSTAND, SatzstandDao", "SatzstandDbTest; ddl/lmg_seed.sql"],
      ["Vermögenseinsatz und Rückbuchung", "SozialLeistungsService (Auszahlung, Storno)", "VermoegensfortschriebDbTest"],
      ["Bescheid mit gespeichertem Rechenweg", "BescheidDao, BescheidGenerator, BerechnungsSnapshot", "doc/leistungsmanagement.md"],
      ["Rechtsgrundlagen aller Beträge und Normen", "—", "doc/rechtsgrundlagen.md (amtlich verifiziert 2026-10-10)"],
    ],
    [30, 36, 34], { zebra: true }
  ),
  para([{ b: "Bewusste Vereinfachungen " }, "damit niemand Überraschungen erlebt — diese Regeln des SGB XII sind ", { i: "nicht" }, " automatisiert (Stand 0.5.8, alles dokumentiert):"], { before: 120, keepNext: true }),
  bullet(["Karenzzeit und Absenkungsfrist bei unangemessenen Wohnkosten (§ 35 Abs. 1 Satz 2–9, Abs. 3 Satz 2–5 SGB XII)"]),
  bullet(["Sonderfreibeträge für zusätzliche Altersvorsorge und Aufwandsentschädigungen (§ 82 Abs. 4, 5 SGB XII)"]),
  bullet(["Kapitalerträge bis 26 EUR jährlich und der Unterhaltsrückgriff oberhalb von 100.000 EUR Jahreseinkommen (§ 43 Abs. 2, § 94 Abs. 1a SGB XII)"]),
  bullet(["Nachzahlungen und Aufrechnung — berechnet wird immer der aktuelle Monat, kein Rückwirkungsautomatismus (§ 44 Abs. 2 SGB XII)"]),
  bullet(["Warmwasser-Abzug und Angemessenheitsgrenze sind örtliche Parameter und im Standard ausgeschaltet"]),
  para(["Damit schließt sich der Kreis: Die Grundformel aus Kapitel 1 — ", { b: "Zahlbetrag = Bedarf − Einkommen − verwertbares Vermögen" }, " — ist in der Anwendung keine Abkürzung, sondern der wörtliche Bauplan: Schritt A, B und C erzeugen genau diese drei Größen, der Monatsplan wiederholt sie je Bescheidmonat, und die Auszahlungskette sorgt dafür, dass am Ende genau dieser Betrag — und kein anderer — ankommt."]),
];

// ─── Kopf-/Fußzeilen ─────────────────────────────────────────────────────────
function seitenFooter() {
  return new Footer({
    children: [new Paragraph({
      alignment: AlignmentType.CENTER,
      children: [new TextRun({ children: [PageNumber.CURRENT], size: 18, color: "888888", font: FONT_BODY })],
    })],
  });
}
function kopfzeile() {
  return new Header({
    children: [new Paragraph({
      alignment: AlignmentType.RIGHT,
      border: { bottom: { style: BorderStyle.SINGLE, size: 2, color: "C8DDE2", space: 4 } },
      children: [new TextRun({ text: "So wird ein SozialFall berechnet · SDN.Leistung LMG", size: 16, color: "999999", font: FONT_BODY })],
    })],
  });
}

// ─── Dokument ────────────────────────────────────────────────────────────────
const doc = new Document({
  creator: "SDN.Leistung LMG",
  title: "So wird ein SozialFall berechnet",
  description: "Die Grundsicherungs-Berechnung (SGB XII) in SDN.Leistung — einfach erklärt mit Fluss- und Ablaufdiagrammen",
  styles: {
    default: {
      document: {
        run: { font: FONT_BODY, size: 22, color: P.body },
        paragraph: { spacing: { line: 312 } },
      },
      heading1: {
        run: { font: { ascii: "Arial", hAnsi: "Arial" }, size: 32, bold: true, color: P.primary },
        paragraph: { spacing: { before: 400, after: 200, line: 312 }, outlineLevel: 0 },
      },
      heading2: {
        run: { font: { ascii: "Arial", hAnsi: "Arial" }, size: 26, bold: true, color: P.h2 },
        paragraph: { spacing: { before: 300, after: 140, line: 312 }, outlineLevel: 1 },
      },
    },
  },
  numbering: {
    config: [
      { reference: "schritte", levels: [{ level: 0, format: LevelFormat.DECIMAL, text: "%1.", alignment: AlignmentType.LEFT, style: { paragraph: { indent: { left: 620, hanging: 360 } } } }] },
      { reference: "schritteB", levels: [{ level: 0, format: LevelFormat.DECIMAL, text: "%1.", alignment: AlignmentType.LEFT, style: { paragraph: { indent: { left: 620, hanging: 360 } } } }] },
      { reference: "schritteC", levels: [{ level: 0, format: LevelFormat.DECIMAL, text: "%1.", alignment: AlignmentType.LEFT, style: { paragraph: { indent: { left: 620, hanging: 360 } } } }] },
    ],
  },
  sections: [
    { // 1. Deckblatt
      properties: {
        page: { size: { width: 11906, height: 16838 }, margin: { top: 0, bottom: 0, left: 0, right: 0 } },
      },
      children: buildCoverR1({
        title: "So wird ein SozialFall berechnet",
        subtitle: "Die Grundsicherungs-Berechnung (SGB XII) in SDN.Leistung — einfach erklärt, mit Fluss- und Ablaufdiagrammen",
        englishLabel: "DIE BERECHNUNG EINFACH ERKLÄRT",
        metaLines: [
          "Projekt Leistungsmanagement Grundsicherung (SGB XII)",
          "Vom Antrag über den Bescheid bis zur Auszahlung",
          "Alle Beträge: Referenzjahr 2026 (Version 0.5.8)",
          "Stand: 13. Oktober 2026",
        ],
        footerLeft: "SDN.Leistung LMG",
        footerRight: "Oktober 2026",
        palette: P,
      }),
    },
    { // 2. Inhaltsverzeichnis
      properties: {
        type: SectionType.NEXT_PAGE,
        page: {
          size: { width: 11906, height: 16838 },
          margin: { top: 1440, bottom: 1440, left: 1701, right: 1417 },
          pageNumbers: { start: 2, formatType: NumberFormat.DECIMAL },
        },
      },
      footers: { default: seitenFooter() },
      children: [
        new Paragraph({
          spacing: { before: 240, after: 120 },
          border: { bottom: { style: BorderStyle.SINGLE, size: 6, color: P.h2, space: 6 } },
          children: [new TextRun({ text: "Inhaltsverzeichnis", bold: true, size: 32, color: P.primary, font: { ascii: "Arial", hAnsi: "Arial" } })],
        }),
        new TableOfContents("Inhaltsverzeichnis", { hyperlink: true, headingStyleRange: "1-2" }),
        new Paragraph({
          spacing: { before: 200 },
          children: [new TextRun({ text: "Hinweis: Dieses Inhaltsverzeichnis wird über Feldfunktionen erzeugt. Nach Änderungen am Dokument bitte mit Rechtsklick auf das Verzeichnis und Felder aktualisieren wählen, damit die Seitenzahlen stimmen.", italics: true, size: 18, color: "888888", font: FONT_BODY })],
        }),
      ],
    },
    { // 3. Hauptteil
      properties: {
        type: SectionType.NEXT_PAGE,
        page: {
          size: { width: 11906, height: 16838 },
          margin: { top: 1440, bottom: 1440, left: 1701, right: 1417 },
          pageNumbers: { formatType: NumberFormat.DECIMAL },
        },
      },
      headers: { default: kopfzeile() },
      footers: { default: seitenFooter() },
      children: inhalt,
    },
  ],
});

const OUT = process.argv[2] || path.join(__dirname, "sozialfall-berechnung.docx");
Packer.toBuffer(doc).then(buf => {
  fs.writeFileSync(OUT, buf);
  console.log("OK geschrieben:", OUT, buf.length, "Bytes");
}).catch(e => { console.error("FEHLER:", e); process.exit(1); });
