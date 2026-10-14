// generate.js — baut das Benutzerhandbuch (SDN.Leistung LMG) als DOCX
"use strict";
const {
  Document, Packer, Paragraph, TextRun, Header, Footer, PageNumber, NumberFormat,
  AlignmentType, HeadingLevel, SectionType, TableOfContents, PageBreak, BorderStyle,
  TabStopType, LeaderType,
} = require("./runtime").dependency("docx");
const fs = require("fs");
const path = require("path");
const { P, FONT, FONT_HEAD, buildCoverR1, h1, abs, hinweis, teilSeite } = require("./lib");
const teil1 = require("./teil1");
const teil2 = require("./teil2");

const AUSGABE = path.join(__dirname, "..", "SDN-Leistung-LMG-Benutzerhandbuch.docx");
const tocPath = path.join(__dirname, "inhaltsverzeichnis.json");
const toc = fs.existsSync(tocPath) ? JSON.parse(fs.readFileSync(tocPath, "utf8")) : [];

// ---------------------------------------------------------------- Kopf-/Fußzeilen
function kopfzeile() {
  return new Header({
    children: [new Paragraph({
      alignment: AlignmentType.RIGHT,
      border: { bottom: { style: BorderStyle.SINGLE, size: 2, color: P.accent, space: 4 } },
      spacing: { after: 0 },
      children: [new TextRun({
        text: "SDN.Leistung LMG — Benutzerhandbuch · Version 0.5.8",
        size: 16, color: P.secondary, font: FONT,
      })],
    })],
  });
}

function fusszeile() {
  return new Footer({
    children: [new Paragraph({
      alignment: AlignmentType.CENTER,
      children: [new TextRun({ children: [PageNumber.CURRENT], size: 18, color: P.secondary, font: FONT })],
    })],
  });
}

// ---------------------------------------------------------------- Vorwort (Inhaltsverzeichnis-Seite)
const vorwort = [
  new Paragraph({
    spacing: { before: 200, after: 300 },
    children: [new TextRun({ text: "Über dieses Handbuch", bold: true, size: 30, color: P.primary, font: FONT_HEAD })],
  }),
  abs("Dieses Handbuch beschreibt die Fachanwendung SDN.Leistung Leistungsmanagement Grundsicherung in zwei Teilen: Teil I ist die Anwenderdokumentation für die sachbearbeitende Ebene — von der Anmeldung über die Fallbearbeitung bis zu Bescheid, Vier-Augen-Freigabe und Kassenlauf. Teil II ist die technische Dokumentation: Architektur, Transaktionssteuerung, Saga-Muster, Datenmodell, Sicherheit und Konfiguration."),
  abs("Dieses Handbuch beschreibt Version 0.5.8 einschließlich der Review-Nachbesserungen vom 13. Oktober 2026. Ältere Abbildungen bleiben als ausdrücklich gekennzeichnete Orientierung erhalten. Verbindlich für Bedienung und Grenzen ist der aktualisierte Text. Kapitel 21 erklärt testsweet, die GLM-Anbindung und die Belege des Testzertifikats."),
  new Paragraph({
    pageBreakBefore: true,
    spacing: { before: 360, after: 240 },
    children: [new TextRun({ text: "Inhaltsverzeichnis", bold: true, size: 30, color: P.primary, font: FONT_HEAD })],
  }),
  ...toc.map(entry => new Paragraph({
    spacing: { after: 95, line: 276 },
    tabStops: [{ type: TabStopType.RIGHT, position: 8788, leader: LeaderType.DOT }],
    children: [new TextRun({ text: entry.title, size: 22, font: FONT }),
      new TextRun({ text: `\t${entry.page}`, size: 22, font: FONT })],
  })),
];

// ---------------------------------------------------------------- Dokument
const seitenFormat = { width: 11906, height: 16838 };
const rand = { top: 1440, bottom: 1440, left: 1701, right: 1417 };

const dokument = new Document({
  creator: "SDN.Leistung Projektteam",
  title: "SDN.Leistung LMG — Benutzerhandbuch",
  description: "Benutzerhandbuch und technische Dokumentation des Leistungsmanagements Grundsicherung (SGB XII)",
  styles: {
    default: {
      document: {
        run: { font: FONT, size: 22, color: "000000" },
        paragraph: { spacing: { line: 312 } },
      },
      heading1: {
        run: { font: FONT_HEAD, size: 32, bold: true, color: P.primary },
        paragraph: { spacing: { before: 360, after: 200, line: 380 }, outlineLevel: 0 },
      },
      heading2: {
        run: { font: FONT_HEAD, size: 27, bold: true, color: P.primary },
        paragraph: { spacing: { before: 280, after: 140, line: 340 }, outlineLevel: 1 },
      },
      heading3: {
        run: { font: FONT_HEAD, size: 24, bold: true, color: "000000" },
        paragraph: { spacing: { before: 220, after: 100, line: 312 }, outlineLevel: 2 },
      },
    },
  },
  sections: [
    // Abschnitt 1: Deckblatt — ohne Seitenzahl, ohne Kopf-/Fußzeile
    {
      properties: {
        page: { size: seitenFormat, margin: rand },
      },
      children: buildCoverR1({
        title: "SDN.Leistung Benutzerhandbuch",
        subtitle: "Leistungsmanagement Grundsicherung (SGB XII) — Anwenderdokumentation und technische Dokumentation",
        englishLabel: "BENUTZERHANDBUCH",
        metaLines: [
          "Version 0.5.8 · Stand: 13. Oktober 2026",
          "Modul: Leistungsmanagement Grundsicherung (SDN.Leistung LMG)",
          "Berechnungsmodell: SGB XII · Verwaltungsverfahren: SGB X",
          "Zielgruppen: Sachbearbeitung, Administration, Fachbetreuung",
        ],
        footerLeft: "SDN.Leistung — Leistungsmanagement Grundsicherung",
        footerRight: "Oktober 2026",
        palette: P,
      }),
    },
    // Abschnitt 2: Vorwort + Inhaltsverzeichnis — römische Seitenzahlen
    {
      properties: {
        type: SectionType.NEXT_PAGE,
        page: { size: seitenFormat, margin: rand, pageNumbers: { start: 1, formatType: NumberFormat.UPPER_ROMAN } },
      },
      headers: { default: kopfzeile() },
      footers: { default: fusszeile() },
      children: vorwort,
    },
    // Abschnitt 3: Hauptteil — arabische Seitenzahlen ab 1
    {
      properties: {
        type: SectionType.NEXT_PAGE,
        page: { size: seitenFormat, margin: rand, pageNumbers: { start: 1, formatType: NumberFormat.DECIMAL } },
      },
      headers: { default: kopfzeile() },
      footers: { default: fusszeile() },
      children: [
        ...teilSeite(
          "TEIL I",
          "Anwenderdokumentation",
          "Die Fachanwendung aus der Sicht der Sachbearbeitung: Anmeldung, Fallbearbeitung, Zahlbarmachung, Bescheid und Betrieb",
        ),
        ...teil1,
        ...teilSeite(
          "TEIL II",
          "Technische Dokumentation",
          "Architektur, Transaktionssteuerung, Saga-Muster, Datenmodell, Sicherheit und Betrieb",
        ),
        ...teil2,
      ],
    },
  ],
});

// ---------------------------------------------------------------- Dokument erzeugen
Packer.toBuffer(dokument).then((puffer) => {
  fs.writeFileSync(AUSGABE, puffer);
  console.log("geschrieben:", AUSGABE, "(" + Math.round(puffer.length / 1024) + " KB)");
});
