// lib.js — Bausteine, Palette und Deckblatt für das SDN.Leistung-Benutzerhandbuch
"use strict";
const {
  Paragraph, TextRun, Table, TableRow, TableCell, ImageRun, PageBreak,
  AlignmentType, HeadingLevel, WidthType, BorderStyle, ShadingType,
  TableLayoutType, VerticalAlign,
} = require("./runtime").dependency("docx");
const fs = require("fs");
const path = require("path");
const { imageSize } = require("./runtime").dependency("image-size");

// ---------------------------------------------------------------- Palette (DS-1 Deep Sea)
const P = {
  bg: "0B1C2C", accent: "529286",
  primary: "000000", body: "000000", secondary: "404040",
  cover: { titleColor: "FFFFFF", subtitleColor: "B0B8C0", metaColor: "90989F", footerColor: "687078" },
  table: { headerBg: "529286", headerText: "FFFFFF", innerLine: "BECFCC", surface: "E8ECEB" },
};

const FONT = { ascii: "Times New Roman", eastAsia: "Times New Roman" };
const FONT_HEAD = { ascii: "Times New Roman", eastAsia: "Times New Roman" };

const NB = { style: BorderStyle.NONE, size: 0, color: "FFFFFF" };
const noBorders = { top: NB, bottom: NB, left: NB, right: NB };
const allNoBorders = { top: NB, bottom: NB, left: NB, right: NB, insideHorizontal: NB, insideVertical: NB };

// ---------------------------------------------------------------- Text-Bausteine
function h1(text) {
  return new Paragraph({
    heading: HeadingLevel.HEADING_1,
    keepNext: true,
    spacing: { before: 360, after: 200, line: 380, lineRule: "atLeast" },
    children: [new TextRun({ text, bold: true, size: 32, color: P.primary, font: FONT_HEAD })],
  });
}
function h2(text) {
  return new Paragraph({
    heading: HeadingLevel.HEADING_2,
    keepNext: true,
    spacing: { before: 280, after: 140, line: 340, lineRule: "atLeast" },
    children: [new TextRun({ text, bold: true, size: 27, color: P.primary, font: FONT_HEAD })],
  });
}
function h3(text) {
  return new Paragraph({
    heading: HeadingLevel.HEADING_3,
    keepNext: true,
    spacing: { before: 220, after: 100, line: 312 },
      children: [new TextRun({ text, bold: true, size: 24, color: "000000", font: FONT_HEAD })],
  });
}

/** Absatz. teile: String oder Array aus {t, fett, kursiv, code}. */
function abs(teile, optionen = {}) {
  const runs = (Array.isArray(teile) ? teile : [{ t: teile }]).map((teil) =>
    new TextRun({
      text: teil.t,
      bold: !!teil.fett,
      italics: !!teil.kursiv,
      size: optionen.size || 22,
      color: teil.farbe || P.body,
      font: FONT,
    }));
  return new Paragraph({
    alignment: AlignmentType.LEFT,
    spacing: { after: optionen.after ?? 120, line: 312 },
    keepNext: optionen.keepNext,
    children: runs,
  });
}

/** Aufzählungspunkt. */
function punkt(teile, ebene = 0) {
  const runs = (Array.isArray(teile) ? teile : [{ t: teile }]).map((teil) =>
    new TextRun({ text: teil.t, bold: !!teil.fett, italics: !!teil.kursiv, size: 22, color: P.body, font: FONT }));
  return new Paragraph({
    bullet: { level: ebene },
    alignment: AlignmentType.LEFT,
    spacing: { after: 60, line: 312 },
    children: runs,
  });
}

/** Nummerierter Schritt (forlaufend je Liste). */
function schritt(nr, teile) {
  const runs = (Array.isArray(teile) ? teile : [{ t: teile }]).map((teil) =>
    new TextRun({ text: teil.t, bold: !!teil.fett, size: 22, color: P.body, font: FONT }));
  return new Paragraph({
    alignment: AlignmentType.LEFT,
    spacing: { after: 60, line: 312 },
    indent: { left: 360, hanging: 360 },
    children: [new TextRun({ text: nr + ".  ", bold: true, size: 22, color: P.accent, font: FONT }), ...runs],
  });
}

/** Hinweisbox (Rand links in Akzentfarbe). */
function hinweis(titel, text) {
  return abs([{ t: `${titel}. `, fett: true }, { t: text }], { after: 180 });
}

// ---------------------------------------------------------------- Tabellen
let tabellenNr = 0;
function tabellenTitel(text) {
  tabellenNr += 1;
  return new Paragraph({
    keepNext: true,
    spacing: { before: 160, after: 80 },
    children: [new TextRun({ text: `Tabelle ${tabellenNr} — ${text}`, bold: true, size: 20, color: P.secondary, font: FONT })],
  });
}

/** Datenzeile: Array von Strings oder {t, fett}. spalten: relative Breiten in %. */
function tabelle(ueberschriften, zeilen, spalten) {
  const zellen = (inhalt, kopf, index, row = 0) => new TableCell({
    width: { size: Math.round(8788 * spalten[index] / 100), type: WidthType.DXA },
    margins: { top: 100, bottom: 100, left: 130, right: 130 },
    shading: kopf
      ? { type: ShadingType.CLEAR, fill: "243748" }
      : { type: ShadingType.CLEAR, fill: row % 2 ? "F3F5F7" : "FFFFFF" },
    verticalAlign: VerticalAlign.CENTER,
    children: [new Paragraph({
      spacing: { line: 276 },
      children: [new TextRun({
        text: typeof inhalt === "string" ? inhalt : inhalt.t,
        bold: kopf || (typeof inhalt === "object" && inhalt.fett),
        size: 20,
        color: kopf ? P.table.headerText : P.body,
        font: FONT,
      })],
    })],
  });
  return new Table({
    width: { size: 100, type: WidthType.PERCENTAGE },
    layout: TableLayoutType.FIXED,
    columnWidths: spalten.map(width => Math.round(8788 * width / 100)),
    borders: {
      top: { style: BorderStyle.SINGLE, size: 4, color: "D9D9D9" },
      bottom: { style: BorderStyle.SINGLE, size: 4, color: "D9D9D9" },
      left: { style: BorderStyle.SINGLE, size: 4, color: "D9D9D9" },
      right: { style: BorderStyle.SINGLE, size: 4, color: "D9D9D9" },
      insideHorizontal: { style: BorderStyle.SINGLE, size: 4, color: "D9D9D9" },
      insideVertical: { style: BorderStyle.SINGLE, size: 4, color: "D9D9D9" },
    },
    rows: [
      new TableRow({ tableHeader: true, cantSplit: true, children: ueberschriften.map((u, i) => zellen(u, true, i)) }),
      ...zeilen.map((z, row) => new TableRow({ cantSplit: true, children: z.map((zelle, i) => zellen(zelle, false, i, row)) })),
    ],
  });
}

// ---------------------------------------------------------------- Abbildungen
const BILD_ORDNER = path.join(__dirname, "..", "manual-bilder");
let abbildungsNr = 0;

/** Screenshot/Diagramm einbetten mit Unterschrift. breite in % der Textbreite (Standard 96). */
function abbild(dateiName, unterschrift, anteil = 0.96) {
  const datei = path.join(BILD_ORDNER, dateiName);
  const puffer = fs.readFileSync(datei);
  const masse = imageSize(puffer);
  abbildungsNr += 1;
  const maxWidthPx = 585; // Textbreite 8788 twips ≈ 585 px bei 96 dpi
  const breite = Math.round(maxWidthPx * anteil);
  const hoehe = Math.round(breite * (masse.height / masse.width));
  return [
    new Paragraph({
      alignment: AlignmentType.CENTER,
      keepNext: true,
      spacing: { before: 160, after: 60 },
      children: [new ImageRun({ data: puffer, transformation: { width: breite, height: hoehe }, type: "png" })],
    }),
    new Paragraph({
      alignment: AlignmentType.CENTER,
      spacing: { after: 200 },
      children: [new TextRun({ text: `Abbildung ${abbildungsNr} — ${unterschrift}${dateiName.startsWith("d0") ? ". Diagrammstand 13. Oktober 2026." : ". Historischer Screenshot zur Orientierung; aktueller Ablauf siehe Text."}`, size: 19, color: P.secondary, italics: true, font: FONT })],
    }),
  ];
}

// ---------------------------------------------------------------- Teil-Markenseite
function teilSeite(kennung, titel, unterzeile) {
  return [
    new Paragraph({
      pageBreakBefore: kennung === "TEIL II",
      keepNext: true,
      spacing: { before: 3200, after: 120 },
      alignment: AlignmentType.LEFT,
      indent: { left: 240 },
      children: [new TextRun({ text: kennung, bold: true, size: 26, color: "000000", font: FONT_HEAD })],
    }),
    new Paragraph({
      keepNext: true,
      spacing: { after: 200, line: 700, lineRule: "atLeast" },
      indent: { left: 240 },
      children: [new TextRun({ text: titel, bold: true, size: 52, color: P.primary, font: FONT_HEAD })],
    }),
    new Paragraph({
      indent: { left: 240 },
      children: [
        new TextRun({ text: unterzeile, size: 24, color: P.secondary, italics: true, font: FONT }),
        new PageBreak(),
      ],
    }),
  ];
}

// ---------------------------------------------------------------- Deckblatt (Rezept R1)
function splitTitleLinesLatein(titel, zeichenProZeile) {
  if (titel.length <= zeichenProZeile) return [titel];
  const worte = titel.split(" ");
  const zeilen = [];
  let aktuell = "";
  for (const wort of worte) {
    if ((aktuell + " " + wort).trim().length > zeichenProZeile && aktuell) {
      zeilen.push(aktuell.trim());
      aktuell = wort;
    } else {
      aktuell = (aktuell + " " + wort).trim();
    }
  }
  if (aktuell) zeilen.push(aktuell);
  if (zeilen.length > 1 && zeilen[zeilen.length - 1].length <= 3) {
    const letzte = zeilen.pop();
    zeilen[zeilen.length - 1] += " " + letzte;
  }
  return zeilen;
}

function calcTitleLayout(titel, verfuegbareTwips, bevorzugtPt = 40, minPt = 24) {
  // Lateinische Schrift: Zeichenbreite ≈ pt × 11 twips (halbe CJK-Breite)
  const zeichenProZeile = (pt) => Math.floor(verfuegbareTwips / (pt * 11));
  let titlePt = bevorzugtPt;
  let zeilen;
  while (titlePt >= minPt) {
    zeilen = splitTitleLinesLatein(titel, zeichenProZeile(titlePt));
    if (zeilen.length <= 3) break;
    titlePt -= 2;
  }
  if (!zeilen || zeilen.length > 3) {
    zeilen = splitTitleLinesLatein(titel, zeichenProZeile(minPt));
    titlePt = minPt;
  }
  return { titlePt, titleLines: zeilen };
}

function calcCoverSpacing(params) {
  const {
    titleLineCount = 1, titlePt = 36, hasSubtitle = false,
    hasEnglishLabel = false, metaLineCount = 0,
    fixedHeight = 800, pageHeight = 16838,
    marginTop = 0, marginBottom = 0,
  } = params;
  const SICHERHEIT = 1200;
  const nutzbar = pageHeight - marginTop - marginBottom - SICHERHEIT;
  const titelHoehe = titleLineCount * (titlePt * 23 + 200);
  const untertitelHoehe = hasSubtitle ? (12 * 23 + 600) : 0;
  const labelHoehe = hasEnglishLabel ? (9 * 23 + 600) : 0;
  const metaHoehe = metaLineCount * (10 * 23 + 100);
  const implizit = 3 * 300;
  const inhalt = titelHoehe + untertitelHoehe + labelHoehe + metaHoehe + fixedHeight + implizit;
  const rest = Math.max(nutzbar - inhalt, 400);
  const FUSS_MIN = 800;
  const rohOben = Math.floor(rest * 0.45);
  const rohUnten = Math.floor(rest * 0.45);
  const bottomSpacing = Math.max(rohUnten, FUSS_MIN);
  const topSpacing = Math.max(rohOben - Math.max(0, FUSS_MIN - rohUnten), 400);
  return { topSpacing, bottomSpacing };
}

function buildCoverR1(config) {
  return [
    new Paragraph({ style: "Title", spacing: { before: 2000, after: 320 },
      children: [new TextRun({ text: "SDN Leistung LMG Benutzerhandbuch", bold: true,
        size: 52, color: "000000", font: FONT_HEAD })] }),
    abs("Leistungsmanagement Grundsicherung nach SGB XII", { size: 28, after: 220 }),
    abs("Anwenderdokumentation und technische Dokumentation", { size: 25, after: 650 }),
    ...config.metaLines.map(text => abs(text, { after: 150 })),
    abs("Dieses Handbuch erklärt Fallbearbeitung, Bescheide, Zahlungen und Betrieb. Es beschreibt außerdem, wie testsweet Ergebnisse prüft und welche Nachweise ein Testzertifikat tatsächlich liefert.", { after: 400 }),
    abs("Herausgeber: SDN.Leistung Projektteam", { size: 20 }),
  ];
}

module.exports = {
  P, FONT, FONT_HEAD, NB, noBorders, allNoBorders,
  h1, h2, h3, abs, punkt, schritt, hinweis, tabellenTitel, tabelle,
  abbild, teilSeite, buildCoverR1,
};
