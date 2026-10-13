// Generiert das testsweet-Handbuch (SDN.Leistung LMG) als DOCX.
// Aufruf: node generate.js <ausgabe.docx>
// Teil I: Die Anwendung (allgemein, mit Screenshots)
// Teil II: Technische Architektur (Stack, Klassen, Ablaufdiagramme)
const path = require("path");
const fs = require("fs");
const {
  Document, Packer, Paragraph, TextRun, Table, TableRow, TableCell,
  Header, Footer, PageNumber, NumberFormat, AlignmentType, HeadingLevel,
  WidthType, BorderStyle, ShadingType, SectionType, TableOfContents,
  LevelFormat, TableLayoutType, ImageRun,
} = require("D:/AntiGravitySoftware/GitWorkspace/LeistungsmanagementGrundsicherung/doc/handbuch-werkzeug/node_modules/docx");

// ─── Palette (wie Klassen- und Architekturueberblick) ───────────────────────
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
  const SAFETY = 1200, pageHeight = 16838;
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

// ─── Inhalt: Teil I — Die Anwendung ──────────────────────────────────────────
const schritt = numList("ablauf");
const kap1 = [
  h1("1 testsweet in Kürze"),
  para([{ b: "testsweet" }, " ist ein Mitarbeiter-Simulator und Integrationstest für die Fachanwendung SDN.Leistung (Leistungsmanagement Grundsicherung, SGB XII). Der Name ist Programm: Der Simulator stellt ", { i: "N simulierte Sachbearbeiter" }, " ein, die parallel einen gemeinsamen Topf von Anwenderszenarien abarbeiten — Anlage von Vorgängen, Auszahlungen anordnen, Vier-Augen-Freigaben, Storni — und danach wird auf der Oracle-Datenbank geprüft, ob auch wirklich das Richtige angekommen ist."]),
  para(["Wichtig zur Einordnung: Das ist ", { b: "kein Unit-Test" }, " und nicht Teil von ", { c: "mvn test" }, " im Hauptprojekt. testsweet ist ein eigenständiges Werkzeug, das den ganzen Stack von der Fachschicht bis zur Datenbank (oder im Modus 2 sogar bis zur echten Weboberfläche im Browser) in Bewegung setzt. Die Auswertung erfolgt bewusst manuell und gemeinsam — der Simulator liefert das Rohmaterial: Kenndaten, Protokolle, Screenshots, eine PDF-Testdokumentation und eine maschinenlesbare Markdown-Fassung."]),
  para(["Der Simulator kennt ", { b: "zwei Betriebsarten" }, ": Im ", { b: "Modus 1" }, " (Fachschicht) rufen die simulierten Sachbearbeiter den Produktionscode direkt auf (", { c: "SozialLeistungsService" }, " → ", { c: "TxManager" }, " → Oracle) und prüfen damit Sperren, Deadlock-Wiederholung, Budgetführung, Vier-Augen-Prinzip und Lost-Update-Schutz. Im ", { b: "Modus 2" }, " (Weboberfläche) steuert jeder simulierte Sachbearbeiter ein eigenes sichtbares Chrome-Fenster über Selenium und bedient die echte SDN.Leistung-Oberfläche wie ein Mensch — inklusive Screenshots je Schritt."]),
  para(["Und er kennt ", { b: "vier Testfallkategorien" }, ": Positivfälle (Erfolgswege mit vollständiger Prüfung), Negativfälle (erwartete Fachfehler mit unverändertem Datenbank-Endzustand), Lasttest (schlanke Massendurchläufe mit Aggregate-Report) und Stresstest (zwei Wellen über dieselben Bestände mit Phasenbarriere). Alles läuft lokal: Die Weboberfläche des Simulators lauscht ausschließlich auf 127.0.0.1, Port 8095."]),

  h1("2 Voraussetzungen und Start"),
  h2("2.1 Was einmalig vorbereitet sein muss"),
  bullet([{ c: "JDK 21" }, " und ", { c: "Maven" }, " — dieselbe Umgebung wie für das Hauptprojekt."]),
  bullet([{ b: "Oracle-Datenbank" }, ": die Entwickler-Datenbank im Docker-Container ", { c: "lmg-oracle" }, " (Start über ", { c: "start.bat" }, " im Hauptprojekt). Der Zugang läuft über ", { c: "TX_URL" }, ", ", { c: "TX_USER" }, ", ", { c: "TX_PASS" }, "; Standard ist ", { c: "jdbc:oracle:thin:@//localhost:1521/FREEPDB1" }, " mit tx_demo/tx_demo."]),
  bullet([{ b: "Nach Änderungen am Hauptprojekt" }, ": einmal ", { c: "mvn install" }, " im Hauptverzeichnis ausführen, damit der Simulator die aktuelle Version der Fachschicht (", { c: "transaktionssteuerung" }, ") benutzt."]),
  bullet([{ b: "Nur für Modus 2" }, ": ein installiertes Google Chrome; der passende ChromeDriver wird über WebDriverManager automatisch beschafft."]),
  h2("2.2 Die Oberfläche starten (empfohlen)"),
  para(["Unter Windows genügt ", { c: "testsweet\\start.bat" }, " — das Fenster bleibt als Konsole offen und der Browser öffnet die Oberfläche automatisch unter ", { c: "http://127.0.0.1:8095/" }, ". Alternativ plattformunabhängig: ", { c: "mvn -f testsweet/pom.xml compile exec:java" }, ". Ein anderer Port lässt sich über die Umgebungsvariable ", { c: "SWEET_PORT" }, " setzen."]),
  h2("2.3 Ohne Oberfläche: Batch-Läufe"),
  para(["Für automatisierte Abnahmen gibt es zwei Kommandozeilen-Varianten. ", { b: "Fachschicht (Modus 1)" }, ": ", { c: "testsweet\\start.bat run <mitarbeiter> <auftraege> [seed] [denkzeitMs] [kategorie]" }, " — zum Beispiel ", { c: "start.bat run 10 60 42 0" }, " für zehn parallele Mitarbeiter, 60 Aufträge, Seed 42, Positivfälle. ", { b: "Weboberfläche (Modus 2)" }, ": ", { c: "start.bat web <mitarbeiter> <auftraege> [seed] [denkzeitMs] [kategorie]" }, " — startet bei Bedarf automatisch den SDN.Leistung-Server auf Port 8080, ordnet die Chrome-Fenster nebeneinander an und spielt die Vorgänge durch. Batch-Läufe beenden sich mit ", { b: "Exitcode 1" }, ", wenn der Lauf oder eine globale Prüfung fehlschlägt — so kann man sie in Skripte und CI einhängen."]),

  h1("3 Die Oberfläche im Überblick"),
  para(["Die Oberfläche ist ein einziges langes Dashboard mit fünf Sprungmarken in der Reiterleiste: ", { b: "Konfiguration" }, ", ", { b: "Kenndaten" }, ", ", { b: "Architektur & Diagramme" }, ", ", { b: "Auftragsliste" }, " und ", { b: "Live-Protokoll" }, ". Ganz oben stehen Titel, der Status-Indikator des Laufs (LÄUFT, FERTIG, FEHLGESCHLAGEN) und die Kurzform der Datenbank-Verbindung.", ""], { keepNext: true }),
  ...abbildung("gui_konfiguration.png", 540, "Abbildung 1: Konfigurationsbereich — Statuszeile, Testdokumentation-Banner, Moduswahl, Testfallkategorie und Laufkonfiguration"),
  para([{ b: "Der auffälligste Bereich ist das grüne " }, { b: "Testdokumentation-Banner" }, " über der Konfiguration: Nach jedem Lauf bietet es die erzeugte Prüfdocumentation direkt an — links der Knopf ", { i: "PDF-Testdokumentation öffnen" }, " (Deckblatt, Fachflussdiagramme, Zwischenrechenwege und SQL-Nachweise je Testfall), daneben ", { i: "Markdown für KI-Review" }, " (dieselben Daten maschinenlesbar, inklusive der vergebenen Datenbank-IDs und Prüf-SQLs)."], { before: 60 }),
  para(["Darunter liegen die drei Konfigurations-Abschnitte: ", { b: "1. Testmodus wählen" }, " (zwei Karten: Modus 1 Fachschicht, Modus 2 Weboberfläche/Selenium), ", { b: "Testfallkategorie wählen" }, " (vier Knöpfe, siehe Kapitel 6) und ", { b: "2. Simulationslauf konfigurieren" }, " (Mitarbeiter, Aufträge, Seed, Denkzeit, Aufräum-Checkbox plus Schnell-Presets). Die Abschnitte ", { b: "3. Kenndaten des Laufs" }, " bis ", { b: "6. Live-Transaktionsprotokoll" }, " füllen sich ab dem ersten Lauf."]),
  para([{ i: "Ein Hinweis zur Erwartung: Die Kenndaten zeigen immer nur den letzten Lauf " }, { i: "dieser" }, { i: " GUI-Sitzung (live aus dem Speicher) — keine Tagesübersicht über alle Läufe. Die vollständige Geschichte liegt im Log-Verzeichnis jedes Laufs." }]),

  h1("4 Einen Testlauf fahren"),
  para(["Ein Lauf ist bewusst ein Dreiklick-Erlebnis — Modus wählen, Kategorie wählen, Starten:"], { keepNext: true }),
  schritt([{ b: "Modus wählen: " }, "Karte ", { b: "Modus 1 (Fachschicht)" }, " für Transaktionstests direkt am Produktionscode, oder ", { b: "Modus 2 (Weboberfläche/Selenium)" }, " für den Durchlauf durch die echte Benutzeroberfläche mit sichtbaren Chrome-Fenstern. Die gewählte Karte ist blau umrandet."]),
  schritt([{ b: "Kategorie wählen: " }, "Einer der vier Knöpfe — ", { b: "Positivfälle" }, ", ", { b: "Negativfälle" }, ", ", { b: "Lasttest" }, " oder ", { b: "Stresstest" }, ". Das Beschriftungsfeld neben der Überschrift erklärt die gewählte Kategorie; der Knopf ", { i: "Testfälle im Detail" }, " öffnet einen Dialog mit der vollständigen, verständlich geschriebenen Beschreibung jedes Szenario-Typs samt erwartetem Datenbank-Endzustand."]),
  schritt([{ b: "Konfigurieren: " }, { b: "Mitarbeiter" }, " = Anzahl paralleler simulierter Sachbearbeiter. ", { b: "Aufträge" }, " = Größe des Auftragstopfs (beim Stresstest: die Anzahl der User, je User laufen zwei Tests). ", { b: "Seed" }, " — 0 bedeutet zufällig, ein fester Seed erzeugt dieselben Szenarien wieder und macht einen Lauf reproduzierbar. ", { b: "Denkzeit je Schritt" }, " (ms) bremst die simulierten Mitarbeiter ab, sinnvoll vor allem im Modus 2. Die Checkbox ", { i: "Altdaten (SWEET-*) vorab aufräumen" }, " (Standard: an) entfernt Reste früherer Läufe vor dem Start. Die Presets rechts setzen bewährte Kombinationen — die Auswahl passt sich der Kategorie an."]),
  schritt([{ b: "Starten und beobachten: " }, { i: "Simulation starten" }, " beginnt den Lauf; der Fortschrittsbalken und das Live-Protokoll zeigen den Fortgang, ", { i: "Stoppen" }, " bricht kontrolliert ab. Im Modus 2 öffnen sich zusätzlich die Chrome-Fenster der simulierten Sachbearbeiter im Bildschirmraster."]),
  schritt([{ b: "Ergebnis entgegennehmen: " }, "Am Ende springt das Testdokumentation-Banner an (PDF/Markdown), die Kenndaten zeigen den frischen Lauf, und im Ordner ", { c: "testsweet/logs/run-<Zeitstempel>/" }, " liegt das vollständige Material."]),
  ...abbildung("gui_testfaelle_detail.png", 540, "Abbildung 2: Der Dialog „Testfälle im Detail“ — jeder Szenario-Typ mit Erklärung und erwartetem Datenbank-Endzustand"),

  h1("5 Die Ergebnisse lesen"),
  h2("5.1 Kenndaten des Laufs"),
  para(["Die Kenndaten-Karten fassen den Lauf in acht Zahlen zusammen: Aufträge gesamt, offen, in Bearbeitung, fertig gespielt, ", { b: "verifiziert (DB Soll = Ist)" }, ", beim Spielen fehlgeschlagen, Verifikation nicht in Ordnung, und die Dauer. Zwei Karten werden gern verwechselt — sie prüfen aber völlig Verschiedenes: ", { b: "Beim Spielen fehlgeschlagen" }, " heißt, der Durchlauf des simulierten Sacharbeiters ist abgebrochen (etwa ein eingefrorenes Browser-Fenster im Modus 2); die Datenbank kann trotzdem korrekt sein. ", { b: "Verifikation nicht in Ordnung" }, " heißt, der Datenbank-Endzustand weicht von der Erwartung ab — das ist die ernste Meldung."], { keepNext: true }),
  ...abbildung("gui_kenndaten.png", 540, "Abbildung 3: Kenndaten-Karten und simulierte Sachbearbeiter-Threads, dahinter die Diagramm-Galerie"),
  para(["Darunter zeigt jede Zeile einen ", { b: "simulierten Sachbearbeiter-Thread" }, " (sb-sim-01, sb-sim-02, …) mit seinem Zustand und der Anzahl abgeschlossener Aufträge — so erkennt man schnell, ob die Arbeit gleichmäßig verteilt war oder ein Thread hing."]),
  h2("5.2 Auftragsliste und Live-Protokoll"),
  para(["Die ", { b: "Auftragsliste" }, " führt jeden Auftrag mit Nummer, Szenario-Typ, Aktenzeichen (SWEET-…), Hauptperson, Bearbeiter, Status und Dauer; das Suchfeld filtert nach Aktenzeichen, Name oder Szenario, die Knöpfe ", { i: "Alle" }, ", ", { i: "Nur Verifiziert" }, " und ", { i: "Auffälligkeiten" }, " schneiden die Menge zu. Ein Doppelklick auf eine Zeile öffnet die vollständige Schritt- und Rechenchronologie des Auftrags — inklusive der Zwischenwerte der Bedarfsberechnung."], { keepNext: true }),
  ...abbildung("gui_auftragsliste.png", 540, "Abbildung 4: Auftragsliste mit Suche, Filtern und dem Sprung zum Log-Verzeichnis des Laufs"),
  para(["Das ", { b: "Live-Transaktionsprotokoll" }, " streamt während des Laufs jedes Ereignis in ein dunkles Konsolenfeld (mit umschaltbarem Auto-Scroll). Nach dem Lauf ist die Box leer — alles steht dann in den Logdateien des Lauf-Verzeichnisses."], { keepNext: true }),
  ...abbildung("gui_liveprotokoll.png", 540, "Abbildung 5: Live-Protokoll — während des Laufs streamt hier das Ereignislog; danach lebt es nur noch in den Logdateien"),
  h2("5.3 Das Log-Verzeichnis — der eigentliche Zweck"),
  para(["Je Lauf entsteht ein Ordner ", { c: "testsweet/logs/run-<Zeitstempel>/" }, " mit dem vollständigen Material für die gemeinsame Auswertung:"], { keepNext: true }),
  tabellenTitel("Tabelle 1: Dateien im Lauf-Verzeichnis"),
  tabelle(
    ["Datei", "Inhalt"],
    [
      ["simulation.log", "alle Ereignisse chronologisch (Millisekunden, Kanal, Mitarbeiter)"],
      ["mitarbeiter-<kürzel>.log", "alles eines simulierten Sachbearbeiters"],
      ["auftrag-<id>-<aktenzeichen>.log", "Schrittprotokoll eines einzelnen Auftrags"],
      ["db-verifikation.log", "Erwartung vs. Ist je Prüfung (OK/Abweichung)"],
      ["fehler.log", "nur Fehler und Abweichungen"],
      ["zusammenfassung.txt", "Kenndaten des Laufs (Modus, Mitarbeiter, Verifikationsquote)"],
      ["screenshots/", "nur Modus 2: Screenshot je UI-Schritt (tf-NN-schritt-MM.png)"],
      ["testdokumentation.pdf / .md", "Positiv/Negativ: vollständiger Prüfbericht je Fall (PDF für Menschen, Markdown zur KI-Nachprüfung)"],
      ["lastreport.pdf / .md", "Last/Stress: gekürzter Gesamtreport mit Aggregaten statt Einzelfällen"],
    ],
    [38, 62], { zebra: true }
  ),

  h1("6 Die vier Testfallkategorien"),
  para(["Die Kategorie bestimmt, welche Szenarien in den Auftragstopf wandern, welches Aktenzeichen-Präfix die Daten erhalten (dadurch lassen sich die Mengen in der Datenbank auseinanderhalten) und wie der Bericht aussieht:"], { keepNext: true }),
  tabellenTitel("Tabelle 2: Kategorien im Überblick"),
  tabelle(
    ["Kategorie", "Modus 1 (Fachschicht)", "Modus 2 (Web)", "Bericht"],
    [
      ["Positivfälle", "SWEET- · 10 Szenario-Arten", "SWEET-W- · Web-Varianten", "testdokumentation.pdf/.md je Fall"],
      ["Negativfälle", "SWEET-N- · 9 Abwehr-Arten", "SWEET-NW- · 7 Web-Arten", "testdokumentation.pdf/.md je Fall"],
      ["Lasttest", "SWEET-L- · Massendurchläufe", "SWEET-LW-", "lastreport.pdf/.md (nur Aggregate)"],
      ["Stresstest", "SWEET-S- · 2 Wellen", "— (bewusst nur Fachschicht)", "lastreport.pdf/.md (nur Aggregate)"],
    ],
    [20, 32, 24, 24], { zebra: true }
  ),
  h2("6.1 Positivfälle — die Erfolgswege"),
  para(["Zehn Szenario-Arten decken das fachliche Herz des Systems ab: Standardrente (die Vier-Augen-Kette), Paar in gemeinsamer Wohnung, Mehrbedarf nach § 30, Familie/Alleinerziehende, Erwerbstätiger mit Freibeträgen (§ 82), Vermögen mit Schonvermögen (§ 90, inklusive einer Variante mit erwartetem KEIN_ANSPRUCH), Doppelklick (Idempotenz), Storno-Korrektur (Storno und Neuauszahlung im selben Monat), Konkurrenz (erwarteter Lost-Update-Schutz zweier gleichzeitiger Bearbeiter) und Kassenlauf (läuft parallel zu Freigaben). Im Modus 2 kommen Web-Varianten hinzu (WEB-STANDARDRENTE, WEB-MEHRBEDARF, WEB-STORNO). Das Besondere an der Prüfung: Die ", { b: "Erwartungshaltung" }, " — Beträge, Status, Restbudget, Versionen — wird vor dem Lauf mit derselben reinen Berechnung (", { c: "Leistungsberechnung" }, ") ermittelt, die auch die Fachschicht nutzt. Der Simulator rechnet also nicht mit einer zweiten, abweichenden Logik."]),
  h2("6.2 Negativfälle — die Abwehrseiten"),
  para(["Neun Fachschicht-Arten prüfen, dass das System Unzulässiges zuverlässig ablehnt: Rente über Bedarf (§ 43 Abs. 1), Startbudget unter dem Zahlbetrag, Doppelanordnung je Monat, Selbstfreigabe (Vier-Augen-Verletzung), Freigabe einer bereits BEREIT-Zahlung, Storno nach Kassenübergabe, ungültige IBAN-Prüfziffer, umgekehrter oder überlanger Bescheid-Zeitraum (§ 44 Abs. 3) und Operationen auf nicht existierenden IDs. Der erwartete Fachfehler ist Teil des Szenarios — und zusätzlich wird geprüft, dass die Datenbank ", { i: "unverändert" }, " bleibt: keine Zahlung, kein Bescheid, Budget unangetastet. Die sieben Web-Arten machen dasselbe auf der Oberfläche (falsches Passwort, ungültige Bankverbindung, Anordnung ohne Zahlungsmonat, …) und bewiesen unter anderem, dass der Storno-Knopf einer freigegebenen Zahlung der Sachbearbeiter-Rolle gar nicht angeboten wird."]),
  h2("6.3 Lasttest — viele Fälle, kurzer Bericht"),
  para(["Der Lasttest fährt schlanke Durchläufe (Vorgang + SozialFall + Anordnung, ohne Freigabe und Storno) in hoher Stückzahl. Weil bei 600+ Fällen eine Einzelfall-Dokumentation ausufern würde, schreibt der Simulator bewusst nur einen ", { b: "Gesamtreport" }, " mit Aggregaten: Kennzahlen, Durchsatz (Aufträge/min), Dauer je Auftrag (min/Ø/max), die Summe der verifizierten Auszahlungen und die Verteilung je Szenario-Typ und Sachbearbeiter. Die Einzelnachweise stehen vollständig in den Protokolldateien des Lauf-Verzeichnisses. Referenzlauf: 600 Fälle mit 20 Mitarbeitern in rund 3 Sekunden gegen die Docker-Oracle."]),
  h2("6.4 Stresstest — zwei Wellen über dieselben User"),
  para(["Der Stresstest läuft bewusst nur im Fachschicht-Modus (GUI und Schnittstelle wehren Modus 2 ab) und arbeitet in ", { b: "zwei Wellen über dieselben Bestände" }, ": Welle 1 legt je User Vorgang und SozialFall an — noch ohne Zahlung. Erst wenn Welle 1 vollständig abgeschlossen ist (Phasenbarriere), startet Welle 2: Sie findet die User über das Aktenzeichen wieder, ändert die Miete (optimistisches Locking, Version 1 → 2) und ordnet die Auszahlung mit dem Betrag der ", { i: "neuen" }, " Miete an. Das GUI-Feld ", { b: "Aufträge" }, " ist hier die User-Zahl — je User laufen zwei Tests, das Standard-Preset 20 Mitarbeiter / 2000 User ergibt also 4000 Transaktionsblöcke. Referenzlauf: 4000/4000 verifiziert in 13 Sekunden; der Datenbank-Endzustand wurde per SQL bestätigt (2000 Vorgänge, alle Fälle exakt Version 2, 2000 Zahlungen ANGELEGT, Summe deckungsgleich mit dem Gesamtreport)."]),

  h1("7 Regeln für den sicheren Umgang"),
  bullet([{ b: "Nur lokal betreiben: " }, "Die Oberfläche lauscht ausschließlich auf 127.0.0.1, hat keine Anmeldung und erlaubt Löschungen (Aufräumen). Sie ist ein Entwicklungswerkzeug — niemals fernzugänglich betreiben."]),
  bullet([{ b: "Datenhygiene: " }, "Alle Vorgänge tragen das Aktenzeichen-Präfix ", { c: "SWEET-" }, " (je Kategorie mit Zusatz, siehe Tabelle 2). Das Aufräumen vor dem Lauf entfernt nur diese Reste — komplett, mit Kindtabellen zuerst. Der Seed-Bestand der Fachdatenbank bleibt unberührt."]),
  bullet([{ b: "Nur gegen die Test-Datenbank: " }, "Die Szenarien buchen echte Zahlungen, Storni und Historisierungen im angeschlossenen Schema. Die Entwickler-Docker-Oracle ist der richtige Ort; gegen Bestands- oder Produktivdaten gehört der Simulator nicht."]),
  bullet([{ b: "Globale Prüfungen zählen: " }, "Zu jedem Lauf gehören globale Datenbankprüfungen. Eine Abweichung oder ein SQL-Fehler dort setzt den Lauf auf FEHLGESCHLAGEN — auch wenn einzelne Aufträge grün waren — und verhindert ein erfolgreiches Ergebnis in GUI, PDF und Markdown."]),
  bullet([{ b: "Modus 2 und Chrome: " }, "Jedes Selenium-Fenster bekommt ein zufälliges, registriertes Chrome-Profil unter ", { c: "testsweet/logs/browser-profile/" }, ". Das Aufräumskript beendet nur Chrome mit exakt passendem registrierten Profilpfad; fremde Browserfenster werden nicht angetastet. Nach einem Sitzungstod werden bestätigte Arbeitsschritte nicht blind wiederholt — bei unklarem Commit-Ausgang eines Schreibschritts schlägt der Auftrag mit Hinweis fehl."]),
];

// ─── Inhalt: Teil II — Technische Architektur ────────────────────────────────
const kap2 = [
  h1("8 Architekturüberblick", true),
  para(["Technisch ist testsweet ein kleines, bewusst framework-freies Java-Programm (22 Klassen, rund 7.800 Zeilen im Hauptteil, dazu fünf Testklassen), das zwei Rollen hat: Es ist ", { b: "Webserver" }, " für die Dashboard-Oberfläche und ", { b: "Orchestrierung" }, " für die simulierten Mitarbeiter. Das folgende Bild zeigt die Komponenten und ihre Leitungen:"], { keepNext: true }),
  ...abbildung("diagramm_klassen.png", 540, "Abbildung 6: Klassen und Zusammenspiel — vom Browser über SweetServer und Simulator zu den Workern, darunter Protokoll, Verifikation und Berichte"),
  para([{ b: "So liest man das Bild: " }, "Der solide Fluss oben ist der Weg eines Laufs: Der Browser spricht die Endpunkte ", { c: "/api/start" }, ", ", { c: "/api/status" }, ", ", { c: "/api/testfaelle" }, ", ", { c: "/api/auftraege" }, ", ", { c: "/api/pdf" }, ", ", { c: "/api/md" }, " und ", { c: "/api/stop" }, " an — alle auf dem JDK-HttpServer von ", { c: "SweetServer" }, ". Der ", { c: "Simulator" }, " baut aus der ", { c: "SzenarioBibliothek" }, " den Auftragstopf (mit Seed reproduzierbar) und füttert damit N Worker-Threads: ", { c: "Mitarbeiter" }, " im Modus 1 direkt auf die Fachschicht, ", { c: "WebMitarbeiter" }, " im Modus 2 über Selenium in eigene Chrome-Fenster. Beide landen zwangsläufig in derselben Oracle-Datenbank — genau dort soll geprüft werden."], { before: 60 }),
  para(["Der gestrichelte Fluss unten ist der Nachweis: ", { c: "SweetLog" }, " schreibt je Lauf ein Verzeichnis mit Protokollen; ", { c: "Verifikator" }, " und ", { c: "GlobaleVerifikation" }, " prüfen Soll gegen Ist per SQL; ", { c: "MdBericht" }, ", ", { c: "Testdokumentation" }, ", ", { c: "SweetPdf" }, " und ", { c: "LastBericht" }, " erzeugen daraus Markdown und PDF — genau die Dateien, die das GUI-Banner anbietet."]),

  h1("9 Der Technologiestack"),
  para(["testsweet folgt derselben Philosophie wie das Hauptprojekt: bewusst keine Frameworks, alles nachlesbar. Die Abhängigkeiten in ", { c: "testsweet/pom.xml" }, " sind überschaubar:"], { keepNext: true }),
  tabellenTitel("Tabelle 3: Bausteine und ihre Rolle"),
  tabelle(
    ["Baustein", "Version", "Rolle im Simulator"],
    [
      ["Java", "21", "Laufzeit und Sprache; Thread-Parallelität über die Standardbibliothek"],
      ["transaktionssteuerung", "0.5.8", "das getestete Hauptprojekt: Fachschicht, TxManager, DAOs — Modus 1 ruft es direkt auf"],
      ["Oracle JDBC (ojdbc11)", "via Hauptprojekt", "Datenbankzugang für die Szenarien und die SQL-Verifikation"],
      ["selenium-java", "4.27.0", "Modus 2: Steuerung der sichtbaren Chrome-Fenster (Anmeldung, Masken, Freigabe, Storno)"],
      ["WebDriverManager", "5.9.0", "beschafft den passenden ChromeDriver automatisch und hält einen Ersatztreiber bereit"],
      ["JDK HttpServer", "JDK-Bestandteil", "die Dashboard-Oberfläche auf Port 8095 — ohne Web-Framework"],
      ["JUnit Jupiter", "5.10.2", "nur für die eigenen Tests des Simulators (siehe Kapitel 12)"],
      ["SweetPdf (Eigenbau)", "—", "PDF-1.4-Erzeugung ohne Drittbibliothek; bindet Grafiken als native XObjects (DCTDecode)"],
      ["Python + matplotlib", "Werkzeugkette", "erzeugt die Fachflussdiagramme (testsweet/generate_diagrams.py, 200 dpi)"],
    ],
    [26, 18, 56], { zebra: true }
  ),
  para(["Zwei Details stechen heraus. Erstens: Der Simulator testet ", { i: "Produktionscode mit Produktionscode" }, " — Modus 1 hängt an denselben Klassen wie die Fachanwendung, nicht an einer Kopie. Zweitens ist selbst der PDF-Export eine Eigenentwicklung (", { c: "SweetPdf" }, "), damit der Bericht ohne schwergewichtige Bibliotheken aus einem einzigen Prozess fällt. Die Chrome-Fenster des Modus 2 laufen mit verkleinerter Darstellung (", { c: "--force-device-scale-factor=0.8" }, "), damit die Seiten auch im kleinen Rasterfenster vollständig sichtbar sind."]),

  h1("10 Die Klassen"),
  para(["Die 22 Hauptklassen im Paket ", { c: "de.allgemein.testsweet" }, " verteilen die Verantwortung auf klare Rollen — von der Szenario-Definition über die Ausführung bis zu Protokoll und Bericht:"], { keepNext: true }),
  tabellenTitel("Tabelle 4: Alle Hauptklassen (Zeilenstand 12.10.2026)"),
  tabelle(
    ["Klasse", "Zeilen", "Rolle"],
    [
      ["SzenarioBibliothek", "1752", "die Szenario-Arten des Modus 1 samt Erwartungshaltung (Beträge, Status, Versionen)"],
      ["Simulator", "934", "Orchestrierung: Auftragstopf, Worker-Threads, Phasenbarriere (Stress), globale Prüfungen"],
      ["SweetPdf", "738", "eigenentwickelter PDF-Generator (PDF 1.4) für Testdokumentation und Lastreport"],
      ["Testdokumentation", "592", "baut den Positiv-/Negativ-Bericht: je Fall Rechenweg, Erwartung/Ist-SQL, Ablauf"],
      ["WebSzenarioBibliothek", "577", "die Web-Szenario-Arten des Modus 2 (inkl. WEB-NEG-Varianten)"],
      ["WebHelfer", "541", "Selenium-Werkzeuge: Klicks, Wartezeiten, Screenshots, Wiederaufnahme nach Sitzungstod"],
      ["SweetServer", "401", "HTTP-Oberfläche auf Port 8095 (JDK HttpServer) samt /api-Endpunkten"],
      ["LastBericht", "367", "der gekürzte Aggregate-Report für Last- und Stresstest"],
      ["SweetLog", "300", "Lauf-Protokollierung: run-Verzeichnis, je Mitarbeiter/Auftrag, Verifikations- und Fehlerlog"],
      ["TestfallKatalog", "294", "Katalog der Testfallbeschreibungen für den Dialog „Testfälle im Detail“"],
      ["WebMitarbeiter", "268", "ein simulierter Sachbearbeiter im Modus 2 — fährt sein eigenes Chrome-Fenster"],
      ["MdBericht", "262", "erzeugt die maschinenlesbare Markdown-Fassung der Berichte"],
      ["Mitarbeiter", "138", "ein simulierter Sachbearbeiter im Modus 1 — ruft die Fachschicht direkt auf"],
      ["Auftrag", "116", "Wertobjekt: Szenario, Aktenzeichen, Hauptperson, Erwartung und Status"],
      ["SweetMain", "115", "Einstiegspunkt: CLI-Auswertung (run/web) oder Start der GUI"],
      ["Verifikator", "110", "Soll/Ist-Prüfung je Auftrag per SQL (Beträge, Status, Budget, Versionen)"],
      ["BrowserProzesse", "109", "Chrome-Prozess- und Profil-Verwaltung samt sicherem Aufräumen"],
      ["GlobaleVerifikation", "49", "globale Datenbankprüfungen zum Laufresultat (Abweichung → FEHLGESCHLAGEN)"],
      ["Erwartung", "48", "Wertobjekt: der erwartete Datenbank-Endzustand eines Auftrags"],
      ["Rechenblock", "42", "Wertobjekt: Zwischenwerte der Bedarfsberechnung für Dokumentation und Doppelklick-Ansicht"],
      ["SzenarioAktion", "38", "eine Aktion eines Fachschicht-Szenarios (Schritt im Auftrag)"],
      ["WebAktion", "32", "eine Aktion eines Web-Szenarios (UI-Schritt im Chrome-Fenster)"],
    ],
    [26, 12, 62], { zebra: true }
  ),
  h2("10.1 Die wichtigsten Klassen im Detail"),
  para([{ c: "SzenarioBibliothek" }, " und ", { c: "WebSzenarioBibliothek" }, " sind das fachliche Herz: Jedes Szenario trägt seine Erwartungshaltung in sich — die Beträge, Status und Versionen, die am Ende in der Datenbank stehen müssen. Die Erwartung wird vorab mit derselben ", { c: "Leistungsberechnung" }, " gerechnet, die auch die Fachschicht verwendet; deshalb kann ein Verifikationsfehler nur zwei Ursachen haben: rechnet die Fachschicht falsch, oder wurde nicht das gebucht, was gerechnet wurde."]),
  para([{ c: "Simulator" }, " ist der Dirigent: Er mischt den Auftragstopf (Seed!), startet die Worker-Threads, setzt beim Stresstest die Phasenbarriere zwischen Anlegen und Bearbeiten, lässt den Kassenlauf bewusst parallel zu den Freigaben laufen, sammelt die Ergebnisse und fährt die globale Verifikation. ", { c: "Mitarbeiter" }, " und ", { c: "WebMitarbeiter" }, " sind die Arbeiter — einer kürzer, einer mit Browser: Beide ziehen sich Aufträge aus dem Pool, spielen sie Schritt für Schritt (", { c: "SzenarioAktion" }, " bzw. ", { c: "WebAktion" }, ") und schreiben ihre Spuren über ", { c: "SweetLog" }, "."]),
  para([{ c: "Verifikator" }, " und ", { c: "GlobaleVerifikation" }, " sind das Rückgrat des Nachweises: Ersterer prüft je Auftrag den erwarteten Endzustand per SQL (auch negativ: nichts darf entstanden sein), Letztere zusätzlich den Gesamtbestand des Laufs. Ihre Ergebnisse fließen in GUI, ", { c: "zusammenfassung.txt" }, " und beide Berichtsketten. Die Berichtskette selbst ist dreigeteilt: ", { c: "Testdokumentation" }, " baut den Inhalt (Rechenweg, Erwartung/Ist-SQL je Fall), ", { c: "MdBericht" }, " schreibt ihn als Markdown und ", { c: "SweetPdf" }, " als PDF; ", { c: "LastBericht" }, " erzeugt die schlanke Aggregate-Fassung für Last und Stress."]),
  para([{ c: "WebHelfer" }, " und ", { c: "BrowserProzesse" }, " kapseln die Sprödigkeit echter Browser: stabile Klick-Hilfen (Element sichtbar machen, per JavaScript klicken), Screenshots je Schritt, Treiber-Neustart mit Ersatztreiber, und das saubere Aufräumen, das ausschließlich Chrome-Prozesse mit registriertem Profilpfad beendet. ", { c: "SweetServer" }, " schließlich ist der dünne Web-Faden, der alles zusammenbringt — CRUD für Status und Auftragsliste, Streaming des Live-Protokolls und die Download-Endpunkte für die Berichte."]),

  h1("11 Abläufe im Detail"),
  para(["Die folgenden Fachflussdiagramme erzeugt ", { c: "testsweet/generate_diagrams.py" }, " automatisch (200 dpi); dieselben Grafiken stecken auch in der Testdokumentation und in der GUI-Galerie."], { keepNext: true }),
  ...abbildung("simulator_zyklus.png", 540, "Abbildung 7: Ablauf und Testzyklus des Mitarbeiter-Simulators"),
  para([{ b: "So liest man das Bild: " }, "Von links nach rechts: Start und Konfiguration (N Sachbearbeiter, M Aufträge im Pool, Seed und Denkzeit, Altdaten-Aufräumen), der gemeinsame Auftragstopf mit den Szenario-Arten, die parallelen Worker-Threads, und rechts das Ziel — Fachschicht und Datenbank. Der Rücklauf unten schließt den Zyklus: Verifikation, Protokolle, Berichte."]),
  ...abbildung("bedarfsberechnung_sgb12.png", 470, "Abbildung 8: Die Berechnungskaskade nach SGB XII — die Grundlage jeder Szenario-Erwartung"),
  para(["Dieses Bild erklärt, woher die erwarteten Beträge der Szenarien kommen: dieselbe Kaskade von der Regelbedarfsstufe über Mehrbedarfe, Kosten der Unterkunft, Absetzbeträge und Vermögen bis zum Zahlbetrag. Weil Simulator und Fachschicht dieselbe ", { c: "Leistungsberechnung" }, " nutzen, ist die Erwartung keine Sekundär-Implementierung, sondern ein Gegencheck derselben Rechtslogik."]),
  ...abbildung("vier_augen_workflow.png", 500, "Abbildung 9: Auszahlungs-Workflow mit Vier-Augen-Freigabe und Storno"),
  para(["Der Vier-Augen-Workflow ist der meistgeprüfte Weg im Simulator: Anordnen (ANGELEGT), Freigabe durch eine zweite Person (BEREIT), Übergabe im Kassenlauf (UEBERGEBEN) — und als Abzweig das Storno mit Restbudget- und Vermögenseinsatz-Rückbuchung. Genau diese Kette spielen die Positiv-Szenarien Standardrente und die Web-Varianten durch; die Negativ-Varianten prüfen die Abwehrseiten (Selbstfreigabe, Doppel-Freigabe, Storno nach Kasse)."]),
  ...abbildung("transaktionsarchitektur.png", 520, "Abbildung 10: Transaktionssteuerung und Oracle-ACID-Kern"),
  para(["Der Stresstest richtet sich gezielt gegen diese Schicht: 4000 Transaktionsblöcke mit Sperren auf dieselben Vorgänge, Optimistic-Locking-Wechsel (Version 1 → 2) und Budgetführung unter Konkurrenz. Die Retry-Logik bei Sperrenkonflikten (ORA-00054/60/8177) läuft dabei im getesteten ", { c: "TxManager" }, " — der Simulator überspringt sie nicht."]),
  ...abbildung("stresstest_zwei_wellen.png", 520, "Abbildung 11: Das Zwei-Wellen-Schema des Stresstests mit Phasenbarriere"),
  para([{ b: "So liest man das Bild: " }, "Welle 1 legt die Bestände an (Vorgang + SozialFall, noch ohne Zahlung), die Phasenbarriere sorgt dafür, dass Welle 2 erst startet, wenn Welle 1 vollständig durch ist — sonst wäre die Änderung der Miete in Welle 2 ein Wettrennen mit der Anlage. Welle 2 beweist dann den Kern des Lost-Update-Schutzes:lesen unter Sperrung, Änderung mit Versionsprüfung, Anordnung nach neuer Miete."]),

  h1("12 Die eigenen Tests des Simulators und bekannte Grenzen"),
  para(["Der Simulator testet zwar andere, hat aber auch fünf eigene Testklassen (", { c: "src/test/java/de/allgemein/testsweet/" }, "), die über ", { c: "mvn -f testsweet/pom.xml test" }, " laufen: ", { c: "GlobaleVerifikationTest" }, " (globale Prüfungen setzen den Lauf korrekt auf FEHLGESCHLAGEN), ", { c: "WebWiederaufnahmeTest" }, " (Sitzungstod im Modus 2: bestätigte Schritte werden nicht wiederholt), ", { c: "TestfallKatalogTest" }, " (der Katalog der Testfallbeschreibungen ist vollständig und passt zu den Szenarien), ", { c: "MdBerichtFilterTest" }, " (die Markdown-Erzeugung filtert korrekt je Kategorie) und ", { c: "BrowserProzesseTest" }, " (das Aufräumen berührt nur registrierte Chrome-Profile)."]),
  para([{ b: "Bekannte Grenzen, bewusst dokumentiert: " }, "Die Storno-Spurenprüfung ist keine vollständige Rollback-Bilanz eines mehrstufigen Auftrags. Die GUI-Kenndaten zeigen nur den letzten Lauf der eigenen Sitzung. Der Stresstest läuft bewusst nur im Fachschicht-Modus — ein Selenium-Stresstest würde vor allem den Browser-Stack testen, nicht die Fachlogik. Und die Berichte für Positiv/Negativ dokumentieren jeden Fall einzeln, weshalb Last und Stress bewusst nur Aggregate schreiben."]),
  para(["Damit ist der Bogen geschlossen: testsweet fährt die Anwendung so, wie sie später benutzt wird — mit echten Abläufen, echten Sperren und echten Browsern — und liefert für jede Behauptung einen nachprüfbaren Beweis: als SQL, als Protokollzeile, als Screenshot oder als Seite im PDF-Bericht."]),
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
      children: [new TextRun({ text: "testsweet-Handbuch · Mitarbeiter-Simulator SDN.Leistung LMG", size: 16, color: "999999", font: FONT_BODY })],
    })],
  });
}

// ─── Dokument ────────────────────────────────────────────────────────────────
const doc = new Document({
  creator: "SDN.Leistung LMG",
  title: "testsweet-Handbuch",
  description: "Anwendung und technische Architektur des Mitarbeiter-Simulators testsweet",
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
      { reference: "ablauf", levels: [{ level: 0, format: LevelFormat.DECIMAL, text: "%1.", alignment: AlignmentType.LEFT, style: { paragraph: { indent: { left: 620, hanging: 360 } } } }] },
    ],
  },
  sections: [
    { // 1. Deckblatt
      properties: {
        page: { size: { width: 11906, height: 16838 }, margin: { top: 0, bottom: 0, left: 0, right: 0 } },
      },
      children: buildCoverR1({
        title: "testsweet-Handbuch",
        subtitle: "Mitarbeiter-Simulator & Integrationstest für SDN.Leistung — die Anwendung verstehen und technisch durchdringen",
        englishLabel: "USER & TECHNICAL HANDBOOK",
        metaLines: [
          "Projekt Leistungsmanagement Grundsicherung (SGB XII)",
          "Teil I: Die Anwendung — Bedienung, Kategorien, Ergebnisse",
          "Teil II: Technische Architektur — Stack, Klassen, Abläufe",
          "Stand: 12. Oktober 2026 (Version 0.5.8)",
        ],
        footerLeft: "SDN.Leistung LMG · testsweet",
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
      children: [...kap1, ...kap2],
    },
  ],
});

const OUT = process.argv[2] || path.join(__dirname, "testsweet-handbuch.docx");
Packer.toBuffer(doc).then(buf => {
  fs.writeFileSync(OUT, buf);
  console.log("OK geschrieben:", OUT, buf.length, "Bytes");
}).catch(e => { console.error("FEHLER:", e); process.exit(1); });
