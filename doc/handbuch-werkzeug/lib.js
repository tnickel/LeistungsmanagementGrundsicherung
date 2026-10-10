// lib.js — Bausteine, Palette und Deckblatt für das SDN.sozial-Benutzerhandbuch
"use strict";
const {
  Paragraph, TextRun, Table, TableRow, TableCell, ImageRun, PageBreak,
  AlignmentType, HeadingLevel, WidthType, BorderStyle, ShadingType,
  TableLayoutType, VerticalAlign,
} = require("docx");
const fs = require("fs");
const path = require("path");
const { imageSize } = require("image-size");

// ---------------------------------------------------------------- Palette (DS-1 Deep Sea)
const P = {
  bg: "0B1C2C", accent: "529286",
  primary: "0B1C2C", body: "000000", secondary: "5B6B7D",
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
    children: [new TextRun({ text, bold: true, size: 24, color: "1F3A4D", font: FONT_HEAD })],
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
    alignment: AlignmentType.JUSTIFIED,
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
    alignment: AlignmentType.JUSTIFIED,
    spacing: { after: 60, line: 312 },
    children: runs,
  });
}

/** Nummerierter Schritt (forlaufend je Liste). */
function schritt(nr, teile) {
  const runs = (Array.isArray(teile) ? teile : [{ t: teile }]).map((teil) =>
    new TextRun({ text: teil.t, bold: !!teil.fett, size: 22, color: P.body, font: FONT }));
  return new Paragraph({
    alignment: AlignmentType.JUSTIFIED,
    spacing: { after: 60, line: 312 },
    indent: { left: 360, hanging: 360 },
    children: [new TextRun({ text: nr + ".  ", bold: true, size: 22, color: P.accent, font: FONT }), ...runs],
  });
}

/** Hinweisbox (Rand links in Akzentfarbe). */
function hinweis(titel, text) {
  return new Table({
    width: { size: 100, type: WidthType.PERCENTAGE },
    borders: allNoBorders,
    rows: [new TableRow({
      cantSplit: true,
      children: [new TableCell({
        shading: { type: ShadingType.CLEAR, fill: "F0F5F3" },
        borders: { top: NB, bottom: NB, right: NB,
          left: { style: BorderStyle.SINGLE, size: 18, color: P.accent } },
        margins: { top: 120, bottom: 120, left: 200, right: 200 },
        children: [
          new Paragraph({
            spacing: { after: 40, line: 300 },
            children: [new TextRun({ text: titel, bold: true, size: 21, color: "2E5E54", font: FONT })],
          }),
          new Paragraph({
            alignment: AlignmentType.JUSTIFIED,
            spacing: { line: 300 },
            children: [new TextRun({ text, size: 21, color: "1F3A4D", font: FONT })],
          }),
        ],
      })],
    })],
  });
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
  const zellen = (inhalt, kopf, index) => new TableCell({
    width: { size: spalten[index], type: WidthType.PERCENTAGE },
    margins: { top: 70, bottom: 70, left: 110, right: 110 },
    shading: kopf
      ? { type: ShadingType.CLEAR, fill: P.table.headerBg }
      : undefined,
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
    borders: {
      top: { style: BorderStyle.SINGLE, size: 4, color: P.accent },
      bottom: { style: BorderStyle.SINGLE, size: 4, color: P.accent },
      left: NB, right: NB,
      insideHorizontal: { style: BorderStyle.SINGLE, size: 2, color: P.table.innerLine },
      insideVertical: NB,
    },
    rows: [
      new TableRow({ tableHeader: true, cantSplit: true, children: ueberschriften.map((u, i) => zellen(u, true, i)) }),
      ...zeilen.map((z) => new TableRow({ cantSplit: true, children: z.map((zelle, i) => zellen(zelle, false, i)) })),
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
      children: [new TextRun({ text: `Abbildung ${abbildungsNr} — ${unterschrift}`, size: 19, color: P.secondary, italics: true, font: FONT })],
    }),
  ];
}

// ---------------------------------------------------------------- Teil-Markenseite
function teilSeite(kennung, titel, unterzeile) {
  return [
    new Paragraph({
      spacing: { before: 3200, after: 120 },
      alignment: AlignmentType.LEFT,
      border: { left: { style: BorderStyle.SINGLE, size: 24, color: P.accent, space: 16 } },
      indent: { left: 240 },
      children: [new TextRun({ text: kennung, bold: true, size: 26, color: P.accent, font: FONT_HEAD })],
    }),
    new Paragraph({
      spacing: { after: 200, line: 700, lineRule: "atLeast" },
      border: { left: { style: BorderStyle.SINGLE, size: 24, color: P.accent, space: 16 } },
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
  const CP = config.palette;
  const padL = 1200, padR = 800;
  const verfuegbareBreite = 11906 - padL - padR - 300;
  const { titlePt, titleLines } = calcTitleLayout(config.title, verfuegbareBreite, 40, 24);
  const titleSize = titlePt * 2;
  const spacing = calcCoverSpacing({
    titleLineCount: titleLines.length, titlePt,
    hasSubtitle: !!config.subtitle, hasEnglishLabel: !!config.englishLabel,
    metaLineCount: (config.metaLines || []).length,
    fixedHeight: 400,
  });
  const accentLeft = { style: BorderStyle.SINGLE, size: 8, color: CP.accent, space: 12 };
  const kinder = [];

  kinder.push(new Paragraph({ spacing: { before: spacing.topSpacing } }));

  if (config.englishLabel) {
    kinder.push(new Paragraph({
      indent: { left: padL, right: padR }, spacing: { after: 500 },
      border: { bottom: { style: BorderStyle.SINGLE, size: 6, color: CP.accent, space: 8 } },
      children: [new TextRun({
        text: config.englishLabel.split("").join("  "),
        size: 20, color: CP.accent, font: { ascii: "Arial", eastAsia: "Arial" }, characterSpacing: 40,
      })],
    }));
  }

  for (let i = 0; i < titleLines.length; i++) {
    kinder.push(new Paragraph({
      indent: { left: padL },
      spacing: { after: i < titleLines.length - 1 ? 100 : 300, line: Math.ceil(titlePt * 23), lineRule: "atLeast" },
      children: [new TextRun({
        text: titleLines[i], size: titleSize, bold: true,
        color: CP.cover.titleColor, font: { ascii: "Arial", eastAsia: "Arial" },
      })],
    }));
  }

  if (config.subtitle) {
    kinder.push(new Paragraph({
      indent: { left: padL, right: padR }, spacing: { after: 800, line: 340, lineRule: "atLeast" },
      children: [new TextRun({
        text: config.subtitle, size: 24, color: CP.cover.subtitleColor,
        font: { ascii: "Arial", eastAsia: "Arial" },
      })],
    }));
  }

  for (const zeile of (config.metaLines || [])) {
    kinder.push(new Paragraph({
      indent: { left: padL + 200 }, spacing: { after: 80 },
      border: { left: accentLeft },
      children: [new TextRun({
        text: zeile, size: 22, color: CP.cover.metaColor,
        font: { ascii: "Arial", eastAsia: "Arial" },
      })],
    }));
  }

  kinder.push(new Paragraph({ spacing: { before: spacing.bottomSpacing } }));

  kinder.push(new Paragraph({
    indent: { left: padL, right: padR },
    border: { top: { style: BorderStyle.SINGLE, size: 2, color: CP.accent, space: 8 } },
    spacing: { before: 200 },
    children: [
      new TextRun({ text: config.footerLeft || "", size: 16, color: CP.cover.footerColor, font: { ascii: "Arial" } }),
      new TextRun({ text: "                                        " }),
      new TextRun({ text: config.footerRight || "", size: 16, color: CP.cover.footerColor, font: { ascii: "Arial" } }),
    ],
  }));

  return [new Table({
    width: { size: 100, type: WidthType.PERCENTAGE },
    layout: TableLayoutType.FIXED,
    borders: allNoBorders,
    rows: [new TableRow({
      height: { value: 16838, rule: "exact" },
      children: [new TableCell({
        shading: { type: ShadingType.CLEAR, fill: CP.bg }, borders: noBorders,
        children: kinder,
      })],
    })],
  })];
}

module.exports = {
  P, FONT, FONT_HEAD, NB, noBorders, allNoBorders,
  h1, h2, h3, abs, punkt, schritt, hinweis, tabellenTitel, tabelle,
  abbild, teilSeite, buildCoverR1,
};
