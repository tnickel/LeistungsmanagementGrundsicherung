// generate.js — baut das Benutzerhandbuch (SDN.Leistung LMG) als DOCX
"use strict";
const {
  Document, Packer, Paragraph, TextRun, Header, Footer, PageNumber, NumberFormat,
  AlignmentType, HeadingLevel, SectionType, TableOfContents, PageBreak, BorderStyle,
} = require("docx");
const fs = require("fs");
const path = require("path");
const { P, FONT, FONT_HEAD, buildCoverR1, h1, abs, hinweis, teilSeite } = require("./lib");
const teil1 = require("./teil1");
const teil2 = require("./teil2");

const AUSGABE = path.join(__dirname, "..", "SDN-Leistung-LMG-Benutzerhandbuch.docx");

// ---------------------------------------------------------------- Kopf-/Fußzeilen
function kopfzeile() {
  return new Header({
    children: [new Paragraph({
      alignment: AlignmentType.RIGHT,
      border: { bottom: { style: BorderStyle.SINGLE, size: 2, color: P.accent, space: 4 } },
      spacing: { after: 0 },
      children: [new TextRun({
        text: "SDN.Leistung LMG — Benutzerhandbuch · Version 0.5.7",
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
  abs("Alle Screenshots entstanden an der laufenden Anwendung gegen eine echte Oracle-Datenbank; alle Fachwerte entsprechen dem Referenzjahr 2026 und dem Softwarestand Version 0.5.3."),
  hinweis("Inhaltsverzeichnis aktualisieren", "Das Inhaltsverzeichnis ist als Word-Feld angelegt. Nach dem Öffnen bitte mit Rechtsklick auf das Verzeichnis „Felder aktualisieren“ wählen, damit die Seitennummern endgültig stimmen."),
  new Paragraph({
    alignment: AlignmentType.CENTER,
    spacing: { before: 360, after: 240 },
    children: [new TextRun({ text: "Inhaltsverzeichnis", bold: true, size: 30, color: P.primary, font: FONT_HEAD })],
  }),
  new TableOfContents("Inhaltsverzeichnis", { hyperlink: true, headingStyleRange: "1-3" }),
  new Paragraph({
    spacing: { before: 200 },
    children: [new TextRun({
      text: "Hinweis: Dieses Inhaltsverzeichnis wird über Feldcodes erzeugt. Nach Änderungen bitte mit Rechtsklick „Felder aktualisieren“ wählen.",
      italics: true, size: 18, color: "888888", font: FONT,
    })],
  }),
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
        run: { font: FONT_HEAD, size: 24, bold: true, color: "1F3A4D" },
        paragraph: { spacing: { before: 220, after: 100, line: 312 }, outlineLevel: 2 },
      },
    },
  },
  sections: [
    // Abschnitt 1: Deckblatt — ohne Seitenzahl, ohne Kopf-/Fußzeile
    {
      properties: {
        page: { size: seitenFormat, margin: { top: 0, bottom: 0, left: 0, right: 0 } },
      },
      children: buildCoverR1({
        title: "SDN.Leistung Benutzerhandbuch",
        subtitle: "Leistungsmanagement Grundsicherung (SGB XII) — Anwenderdokumentation und technische Dokumentation",
        englishLabel: "BENUTZERHANDBUCH",
        metaLines: [
          "Version 0.5.3 · Stand: 10. Oktober 2026",
          "Modul: Leistungsmanagement Grundsicherung (SDN.Leistung LMG)",
          "Rechtsgrundlage: SGB XII · Bescheidverfahren nach § 37 SGB X",
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
