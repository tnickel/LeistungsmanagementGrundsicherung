/*
 * Lern-Dokument „Schwierige Codestellen verstehen" — Generator.
 * Erstellt doc/Learning/Schwierige-Codestellen-verstehen.docx:
 * Deckblatt (Rezept R1, Palette DM-1), Inhaltsverzeichnis (röm. Nummerierung),
 * Hauptteil (arab. Nummerierung) mit 11 Codestellen: Fundstelle, Originalcode,
 * Erklärung in einfachen Worten, Schritt-für-Schritt-Lesung, Merksatz.
 *
 * Aufruf:  node generate.js
 * Ausgabe: ../Learning/Schwierige-Codestellen-verstehen.docx
 */
const fs = require("fs");
const path = require("path");

const DOCX = "D:/AntiGravitySoftware/GitWorkspace/LeistungsmanagementGrundsicherung/doc/handbuch-werkzeug/node_modules/docx";
const {
  Document, Packer, Paragraph, TextRun, Table, TableRow, TableCell,
  Header, Footer, PageNumber, NumberFormat, AlignmentType, HeadingLevel,
  WidthType, BorderStyle, ShadingType, SectionType, TableOfContents,
  TableLayoutType, LevelFormat,
} = require(DOCX);

/* ---------------------------------------------------------------- Palette */
// DM-1 (Deep Cyan — Technik) aus dem Design-System des docx-Skills
const PAL = {
  bg: "162235", accent: "37DCF2",
  cover: { titleColor: "FFFFFF", subtitleColor: "B0B8C0", metaColor: "90989F", footerColor: "687078" },
  table: { headerBg: "1B6B7A", headerText: "FFFFFF", accentLine: "1B6B7A", innerLine: "C8DDE2", surface: "EDF3F5" },
};
const CODE_BG = "F2F6F7";
const CODE_TEXT = "1A2B30";
const HEAD_COLOR = "1B6B7A";

const NB = { style: BorderStyle.NONE, size: 0, color: "FFFFFF" };
const noBorders = { top: NB, bottom: NB, left: NB, right: NB };
const allNoBorders = { top: NB, bottom: NB, left: NB, right: NB, insideHorizontal: NB, insideVertical: NB };

/* --------------------------------------------- Cover-Helfer (Design-System) */
function splitTitleLines(title, charsPerLine) {
  if (title.length <= charsPerLine) return [title];
  const breakAfter = new Set([..."，。、；：！？", ..."的与和及之在于为", ..."-_—–·/", ..." \t"]);
  const lines = [];
  let remaining = title;
  while (remaining.length > charsPerLine) {
    let breakAt = -1;
    for (let i = charsPerLine; i >= Math.floor(charsPerLine * 0.6); i--) {
      if (i < remaining.length && breakAfter.has(remaining[i - 1])) { breakAt = i; break; }
    }
    if (breakAt === -1) {
      const limit = Math.min(remaining.length, Math.ceil(charsPerLine * 1.3));
      for (let i = charsPerLine + 1; i < limit; i++) {
        if (breakAfter.has(remaining[i - 1])) { breakAt = i; break; }
      }
    }
    if (breakAt === -1) breakAt = charsPerLine;
    lines.push(remaining.slice(0, breakAt).trim());
    remaining = remaining.slice(breakAt).trim();
  }
  if (remaining) lines.push(remaining);
  if (lines.length > 1 && lines[lines.length - 1].length <= 2) {
    const last = lines.pop();
    lines[lines.length - 1] += last;
  }
  return lines;
}

// Lateinische Zeichen sind etwa halb so breit wie CJK — Faktor 11 statt 20.
function calcTitleLayout(title, maxWidthTwips, preferredPt = 40, minPt = 24) {
  const charWidth = (pt) => pt * 11;
  const charsPerLine = (pt) => Math.floor(maxWidthTwips / charWidth(pt));
  let titlePt = preferredPt;
  let lines;
  while (titlePt >= minPt) {
    const cpl = charsPerLine(titlePt);
    if (cpl < 2) { titlePt -= 2; continue; }
    lines = splitTitleLines(title, cpl);
    if (lines.length <= 3) break;
    titlePt -= 2;
  }
  if (!lines || lines.length > 3) {
    lines = splitTitleLines(title, charsPerLine(minPt));
    titlePt = minPt;
  }
  return { titlePt, titleLines: lines };
}

function calcCoverSpacing(params) {
  const {
    titleLineCount = 1, titlePt = 36, hasSubtitle = false, hasEnglishLabel = false,
    metaLineCount = 0, fixedHeight = 800, pageHeight = 16838, marginTop = 0, marginBottom = 0,
  } = params;
  const SAFETY = 1200;
  const usableHeight = pageHeight - marginTop - marginBottom - SAFETY;
  const titleHeight = titleLineCount * (titlePt * 23 + 200);
  const subtitleHeight = hasSubtitle ? (12 * 23 + 600) : 0;
  const englishLabelHeight = hasEnglishLabel ? (9 * 23 + 600) : 0;
  const metaHeight = metaLineCount * (10 * 23 + 100);
  const implicitParaHeight = 3 * 300;
  const contentHeight = titleHeight + subtitleHeight + englishLabelHeight + metaHeight + fixedHeight + implicitParaHeight;
  const safeRemaining = Math.max(usableHeight - contentHeight, 400);
  const FOOTER_MIN = 800;
  const rawTop = Math.floor(safeRemaining * 0.45);
  const rawBottom = Math.floor(safeRemaining * 0.45);
  const bottomSpacing = Math.max(rawBottom, FOOTER_MIN);
  const topSpacing = Math.max(rawTop - Math.max(0, FOOTER_MIN - rawBottom), 400);
  const midSpacing = Math.max(safeRemaining - topSpacing - bottomSpacing, 0);
  return { topSpacing, midSpacing, bottomSpacing };
}

/* ---------------------------------------------------- Cover: Rezept R1 */
function buildCoverR1(config) {
  const P = config.palette;
  const padL = 1200, padR = 800;
  const availableWidth = 11906 - padL - padR - 300;
  const { titlePt, titleLines } = calcTitleLayout(config.title, availableWidth, 40, 24);
  const titleSize = titlePt * 2;
  const spacing = calcCoverSpacing({
    titleLineCount: titleLines.length, titlePt,
    hasSubtitle: !!config.subtitle, hasEnglishLabel: !!config.englishLabel,
    metaLineCount: (config.metaLines || []).length, fixedHeight: 400,
  });
  const accentLeft = { style: BorderStyle.SINGLE, size: 8, color: P.accent, space: 12 };
  const children = [];
  children.push(new Paragraph({ spacing: { before: spacing.topSpacing } }));
  if (config.englishLabel) {
    children.push(new Paragraph({
      indent: { left: padL, right: padR }, spacing: { after: 500 },
      border: { bottom: { style: BorderStyle.SINGLE, size: 6, color: P.accent, space: 8 } },
      children: [new TextRun({
        text: config.englishLabel.split("").join("  "),
        size: 18, color: P.accent, font: { ascii: "Calibri", eastAsia: "SimHei" }, characterSpacing: 40,
      })],
    }));
  }
  for (let i = 0; i < titleLines.length; i++) {
    children.push(new Paragraph({
      indent: { left: padL },
      spacing: { after: i < titleLines.length - 1 ? 100 : 300, line: Math.ceil(titlePt * 23), lineRule: "atLeast" },
      children: [new TextRun({
        text: titleLines[i], size: titleSize, bold: true,
        color: P.cover.titleColor, font: { eastAsia: "SimHei", ascii: "Arial" },
      })],
    }));
  }
  if (config.subtitle) {
    children.push(new Paragraph({
      indent: { left: padL }, spacing: { after: 800 },
      children: [new TextRun({
        text: config.subtitle, size: 24, color: P.cover.subtitleColor,
        font: { eastAsia: "Microsoft YaHei", ascii: "Arial" },
      })],
    }));
  }
  for (const line of (config.metaLines || [])) {
    children.push(new Paragraph({
      indent: { left: padL + 200 }, spacing: { after: 80 },
      border: { left: accentLeft },
      children: [new TextRun({
        text: line, size: 24, color: P.cover.metaColor,
        font: { eastAsia: "Microsoft YaHei", ascii: "Arial" },
      })],
    }));
  }
  children.push(new Paragraph({ spacing: { before: spacing.bottomSpacing } }));
  children.push(new Paragraph({
    indent: { left: padL, right: padR },
    border: { top: { style: BorderStyle.SINGLE, size: 2, color: P.accent, space: 8 } },
    spacing: { before: 200 },
    children: [
      new TextRun({ text: config.footerLeft || "", size: 16, color: P.cover.footerColor, font: { ascii: "Arial" } }),
      new TextRun({ text: "                                        " }),
      new TextRun({ text: config.footerRight || "", size: 16, color: P.cover.footerColor, font: { ascii: "Arial" } }),
    ],
  }));
  return [new Table({
    width: { size: 100, type: WidthType.PERCENTAGE },
    layout: TableLayoutType.FIXED,
    borders: allNoBorders,
    rows: [new TableRow({
      height: { value: 16838, rule: "exact" },
      children: [new TableCell({
        shading: { type: ShadingType.CLEAR, fill: P.bg }, borders: noBorders,
        children,
      })],
    })],
  })];
}

/* --------------------------------------------------- Bausteine Hauptteil */
function h1(text) {
  return new Paragraph({
    heading: HeadingLevel.HEADING_1,
    keepNext: true,
    spacing: { before: 360, after: 160, line: 312 },
    children: [new TextRun({ text, bold: true, size: 32, color: HEAD_COLOR, font: { ascii: "Calibri", eastAsia: "SimHei" } })],
  });
}
function h2(text) {
  return new Paragraph({
    heading: HeadingLevel.HEADING_2,
    keepNext: true,
    spacing: { before: 280, after: 120, line: 312 },
    children: [new TextRun({ text, bold: true, size: 26, color: HEAD_COLOR, font: { ascii: "Calibri", eastAsia: "SimHei" } })],
  });
}
// Lauftext: Teile als { t: "...", b: true, c: "Färbung", m: "Consolas" } für Hervorhebungen
function p(parts, opts = {}) {
  const arr = Array.isArray(parts) ? parts : [{ t: parts }];
  return new Paragraph({
    alignment: AlignmentType.JUSTIFIED,
    spacing: { after: 120, line: 312 },
    ...opts,
    children: arr.map(r => new TextRun({
      text: r.t, bold: !!r.b, italics: !!r.i,
      size: r.s || 22, color: r.c || "000000",
      font: r.m ? { ascii: "Consolas", eastAsia: "Consolas" } : { ascii: "Calibri", eastAsia: "Microsoft YaHei" },
    })),
  });
}
function label(text) { // Zwischen-Überschrift auf H3-Niveau, aber ohne TOC-Eintrag
  return new Paragraph({
    keepNext: true,
    spacing: { before: 200, after: 80, line: 312 },
    children: [new TextRun({ text, bold: true, size: 23, color: "0F3A42", font: { ascii: "Calibri", eastAsia: "SimHei" } })],
  });
}
function fundstelle(datei, klasse, zeilen, sterne, methode) {
  const runs = [
    new TextRun({ text: "Fundstelle:  ", bold: true, size: 20, color: HEAD_COLOR, font: { ascii: "Calibri" } }),
    new TextRun({ text: datei, bold: true, size: 20, color: "333333", font: { ascii: "Consolas", eastAsia: "Consolas" } }),
    new TextRun({ text: "   ·   Klasse: " + klasse, size: 20, color: "333333", font: { ascii: "Calibri" } }),
  ];
  if (methode) {
    runs.push(new TextRun({ text: "   ·   Methode: " + methode, size: 20, color: "333333", font: { ascii: "Calibri" } }));
  }
  runs.push(new TextRun({ text: "   ·   Zeilen: " + zeilen, size: 20, color: "333333", font: { ascii: "Calibri" } }));
  runs.push(new TextRun({ text: "   ·   Schwierigkeit: " + sterne, size: 20, color: "B26A00", font: { ascii: "Calibri" } }));
  return new Paragraph({ keepNext: true, spacing: { before: 160, after: 60, line: 312 }, children: runs });
}
function codeBlock(lines) {
  const codeParas = lines.map(ln => new Paragraph({
    spacing: { line: 220, lineRule: "atLeast", before: 0, after: 0 },
    children: [new TextRun({
      text: ln.length ? ln : " ",
      size: 17, color: CODE_TEXT, font: { ascii: "Consolas", eastAsia: "Consolas" },
    })],
  }));
  return new Table({
    width: { size: 100, type: WidthType.PERCENTAGE },
    layout: TableLayoutType.FIXED,
    borders: {
      top: { style: BorderStyle.SINGLE, size: 2, color: PAL.table.innerLine },
      bottom: { style: BorderStyle.SINGLE, size: 2, color: PAL.table.innerLine },
      left: { style: BorderStyle.SINGLE, size: 12, color: HEAD_COLOR },
      right: { style: BorderStyle.SINGLE, size: 2, color: PAL.table.innerLine },
      insideHorizontal: NB, insideVertical: NB,
    },
    rows: [new TableRow({
      // kurze Blöcke bleiben zusammen; lange dürfen seitenumbruchend splitten
      cantSplit: lines.length <= 16,
      children: [new TableCell({
        shading: { type: ShadingType.CLEAR, fill: CODE_BG },
        margins: { top: 100, bottom: 100, left: 160, right: 160 },
        width: { size: 100, type: WidthType.PERCENTAGE },
        children: codeParas,
      })],
    })],
  });
}
function abstand() { return new Paragraph({ spacing: { after: 40, line: 240 }, children: [] }); }
function numItem(ref, text) {
  return new Paragraph({
    numbering: { reference: ref, level: 0 },
    alignment: AlignmentType.JUSTIFIED,
    spacing: { after: 60, line: 312 },
    children: [new TextRun({ text, size: 22, color: "000000", font: { ascii: "Calibri", eastAsia: "Microsoft YaHei" } })],
  });
}
function merke(textParts) {
  const arr = Array.isArray(textParts) ? textParts : [{ t: textParts }];
  return new Paragraph({
    alignment: AlignmentType.JUSTIFIED,
    spacing: { before: 120, after: 200, line: 312 },
    shading: { type: ShadingType.CLEAR, fill: PAL.table.surface },
    border: { left: { style: BorderStyle.SINGLE, size: 12, color: HEAD_COLOR, space: 8 } },
    indent: { left: 160, right: 120 },
    children: [
      new TextRun({ text: "Merke:  ", bold: true, size: 22, color: HEAD_COLOR, font: { ascii: "Calibri" } }),
      ...arr.map(r => new TextRun({
        text: r.t, bold: !!r.b, italics: !!r.i, size: 22, color: r.c || "000000",
        font: r.m ? { ascii: "Consolas", eastAsia: "Consolas" } : { ascii: "Calibri", eastAsia: "Microsoft YaHei" },
      })),
    ],
  });
}

/* ---------------------------------------------------------- Codeauszüge */
const C = {
  bescheidStream: [
    `int index = 1;`,
    `for (Leistungsberechnung.FallBerechnung fb : berechnung.faelle()) {`,
    `    SozialFall fall = faelle.stream().filter(f -> f.id() == fb.fallId()).findFirst().orElse(null);`,
    `    tabelle.append("<tr><td>Person ").append(index++).append(" (RBS ")`,
    `            .append(fall == null ? "–" : fall.regelbedarfsstufe()).append(")</td>");`,
  ],
  sozialFallRecord: [
    `public record SozialFall(`,
    `        long id,`,
    `        long bgId,`,
    `        int regelbedarfsstufe,`,
    `        boolean merkzeichenG,`,
    `        boolean schwanger,`,
    `        int anzKinderUnter7,`,
    `        int anzKinderUnter16,`,
    `        int anzKinderMinderjaehrig,`,
    `        boolean warmwasser,`,
    `        BigDecimal kduMiete,`,
    `        ...`, // ← wird unten ersetzt (siehe Hinweis im Skriptkopf: vollständige Liste)
    `        String entbindungsmonat,`,
    `        long version) {`,
  ],
  optionalA: [
    `return txManager.execute(TxOptions.DEFAULT, ctx -> {`,
    `    Bedarfsgemeinschaft bg = bgDao.finde(ctx, bgId)`,
    `            .orElseThrow(() -> new FachlicheException(FachlicheException.NICHT_GEFUNDEN,`,
    `                    "Bedarfsgemeinschaft " + bgId + " ist nicht vorhanden"));`,
    `    List<SozialFall> faelle = fallDao.listeFuerBg(ctx, bgId);`,
    `    de.allgemein.lmg.model.Bescheid bescheid = bescheidDao.letzteFuerBg(ctx, bgId, 1)`,
    `            .stream().findFirst().orElse(null);`,
  ],
  optionalB: [
    `return fallDao.finde(ctx, geaendert.id()).orElseThrow();`,
  ],
  optionalC: [
    `Bedarfsgemeinschaft bestehende = bgDao.finde(ctx, id).orElse(null);`,
    `if (bestehende == null) {`,
    `    throw new FachlicheException(FachlicheException.NICHT_GEFUNDEN,`,
  ],
  kassenlauf: [
    `java.util.Map<Long, Boolean> aktiv = new java.util.HashMap<>();`,
    `for (long bgId : bereite.stream().map(Zahlung::bgId).distinct().sorted().toList()) {`,
    `    aktiv.put(bgId, bgDao.lockForUpdate(ctx, bgId)`,
    `            .map(Bedarfsgemeinschaft::statusAktiv).orElse(false));`,
    `}`,
    `bereite = bereite.stream().filter(z -> aktiv.get(z.bgId())).toList();`,
    `if (bereite.isEmpty()) {`,
    `    return new Kassenlaeufergebnis(0, BigDecimal.ZERO.setScale(2));`,
    `}`,
    `BigDecimal summe = bereite.stream()`,
    `        .map(Zahlung::auszahlungsbetrag)`,
    `        .reduce(BigDecimal.ZERO, BigDecimal::add);`,
    `List<Long> ids = bereite.stream().map(Zahlung::id).toList();`,
  ],
  txWork: [
    `package de.allgemein.tx;`,
    ``,
    `/** Arbeitseinheit, die innerhalb einer Transaktion ausgeführt wird. */`,
    `@FunctionalInterface`,
    `public interface TxWork<T> {`,
    `    T run(TxContext ctx) throws Exception;`,
    `}`,
  ],
  txManagerExecute: [
    `public <T> T execute(TxOptions options, TxWork<T> work) {`,
    `    TxException lastFailure = null;`,
    `    int maxAttempts = options.maxAttempts();`,
    `    String txId = java.util.UUID.randomUUID().toString();`,
    `    long startNanos = System.nanoTime();`,
    `    for (int attempt = 1; attempt <= maxAttempts; attempt++) {`,
    `        Connection raw = null;`,
    `        try {`,
    `            raw = pool.getConnection();`,
    `            raw.setTransactionIsolation(options.isolation());`,
    `            TxContext ctx = new TxContext(raw, options.timeout());`,
    `            T result = work.run(ctx);`,
  ],
  catch1: [
    `} catch (TxException e) {`,
    `    rollbackQuietly(raw);`,
    `    lastFailure = e;`,
    `    if (!e.isRetryable() || attempt == maxAttempts) {`,
    `        long durationMillis = (System.nanoTime() - startNanos) / 1_000_000;`,
    `        de.allgemein.tx.audit.TxAuditLog.recordTxRollback(txId, attempt, durationMillis,`,
    `                de.allgemein.tx.audit.TxAuditLog.fehlerBeschreibung(e));`,
    `        throw e;`,
    `    }`,
    `} catch (SQLException e) {`,
    `    rollbackQuietly(raw);`,
    `    lastFailure = TxException.from(e);`,
  ],
  catch2: [
    `    if (!lastFailure.isRetryable() || attempt == maxAttempts) {`,
    `        ... // Protokollieren wie oben, dann:`,
    `        throw lastFailure;`,
    `    }`,
    `} catch (Exception e) {`,
    `    rollbackQuietly(raw);`,
    `    ... // Protokollieren, dann:`,
    `    throw TxException.nonRetryable("Transaktion abgebrochen: " + e.getMessage(), e);`,
  ],
  catch3: [
    `} catch (Error e) {`,
    `    // OOM/StackOverflow o. ä.: Transaktion zurückrollen und den Error durchreichen.`,
    `    // Wichtig: die Pools dürfen beim release() nie implizit committen`,
    `    // (sie rollen vor setAutoCommit(true) selbst zurück).`,
    `    rollbackQuietly(raw);`,
    `    ... // Protokollieren, dann:`,
    `    throw e;`,
    `} finally {`,
    `    if (raw != null) {`,
    `        pool.release(raw);`,
    `    }`,
    `}`,
  ],
  catch4: [
    `// Nicht erreichbar: die Schleife endet immer mit return oder throw`,
    `// (maxAttempts >= 1 wird von TxOptions erzwungen).`,
    `throw new AssertionError("unreachable");`,
  ],
  loggerLambda: [
    `final int tried = attempt;`,
    `final TxException failure = lastFailure;`,
    `LOG.log(Level.WARNING,`,
    `        () -> "Transaktionsversuch " + tried + "/" + maxAttempts`,
    `                + " fehlgeschlagen, Wiederholung folgt: "`,
    `                + de.allgemein.tx.audit.TxAuditLog.fehlerBeschreibung(failure));`,
  ],
  retryPolicy: [
    `/** Wartet vor dem nächsten Versuch: Backoff verdoppelt sich pro Fehlversuch, plus Zufallsanteil. */`,
    `public void sleepBeforeAttempt(int failedAttempt) {`,
    `    long backoff = initialBackoffMillis << Math.min(failedAttempt - 1, 10);`,
    `    long jitter = ThreadLocalRandom.current().nextLong(backoff / 2 + 1);`,
    `    try {`,
    `        Thread.sleep(backoff + jitter);`,
    `    } catch (InterruptedException e) {`,
    `        Thread.currentThread().interrupt();`,
    `        throw TxException.nonRetryable("Warten auf Wiederholversuch unterbrochen", e);`,
    `    }`,
    `}`,
  ],
  poolTryAcquire: [
    `try {`,
    `    if (!permits.tryAcquire(borrowTimeout.toMillis(), TimeUnit.MILLISECONDS)) {`,
    `        warneErschoepfung();`,
    `        throw new SQLException("Pool erschöpft: innerhalb von " + borrowTimeout.toMillis()`,
    `                + " ms keine freie Verbindung bekommen (maxSize erreicht)");`,
    `    }`,
    `} catch (InterruptedException e) {`,
    `    Thread.currentThread().interrupt();`,
    `    throw new SQLException("Warten auf freie Verbindung unterbrochen", e);`,
    `}`,
  ],
  poolInterrupted: [
    `// Interrupt-Rest konsumieren: Nach einem Abbruch (Timeout/cancel)`,
    `// steht das Interrupt-Flag des Worker-Threads; bliebe es gesetzt,`,
    `// erbte der nächste Task auf diesem Worker das Flag und schlägt`,
    `// spontan fehl (z. B. sofortige InterruptedException im sleep).`,
    `Thread.interrupted();`,
  ],
  poolFelder: [
    `private final BlockingQueue<Connection> idle;`,
    `private final Semaphore permits;`,
    `/** Momentan ausgeliehene Verbindungen — Grundlage für das idempotente release(). */`,
    `private final Set<Connection> borrowed = ConcurrentHashMap.newKeySet();`,
    ``,
    `/** Rate-Begrenzung der Erschöpfungs-Warnung (CR-046): höchstens eine pro 10 s. */`,
    `private final java.util.concurrent.atomic.AtomicLong letzteWarnungNano =`,
    `        new java.util.concurrent.atomic.AtomicLong();`,
  ],
  poolGetConnection: [
    `boolean success = false;`,
    `try {`,
    `    // Gepoolte Verbindungen validieren: Nach einem DB-Neustart können tote`,
    `    // Verbindungen im Pool liegen — sie werden still geschlossen und übersprungen.`,
    `    Connection candidate;`,
    `    while ((candidate = idle.poll()) != null) {`,
    `        if (istGueltig(candidate)) {`,
    `            borrowed.add(candidate);`,
    `            success = true;`,
    `            return candidate;`,
    `        }`,
    `        closeQuietly(candidate);`,
    `    }`,
    `    // Keine brauchbare gepoolte Verbindung: neue physische Verbindung öffnen`,
    `    java.util.Properties props = new java.util.Properties();`,
    `    props.setProperty("user", user);`,
    `    props.setProperty("password", password);`,
    `    props.setProperty("oracle.net.CONNECT_TIMEOUT", String.valueOf(connectTimeoutMs));`,
    `    props.setProperty("oracle.jdbc.ReadTimeout", String.valueOf(readTimeoutMs));`,
    `    Connection frisch = DriverManager.getConnection(url, props);`,
    `    if (!istGueltig(frisch)) {`,
    `        closeQuietly(frisch);`,
    `        throw new SQLException("Neu aufgebaute Verbindung besteht die Gültigkeitsprüfung nicht: " + url);`,
    `    }`,
    `    borrowed.add(frisch);`,
    `    success = true;`,
    `    return frisch;`,
    `} finally {`,
    `    if (!success) {`,
    `        permits.release();`,
    `    }`,
    `}`,
  ],
  poolRelease: [
    `if (!borrowed.remove(connection)) {`,
    `    // Doppel-Release oder fremde Verbindung: ignoriert BEWUSST ohne`,
    `    // Permit-Freigabe, sonst würde ein Doppel-Release maxSize überhöhen`,
    `    // und dieselbe Verbindung läge zweimal in der Queue.`,
    `    LOG.warning("release() ignoriert: Verbindung ist nicht (mehr) ausgeliehen");`,
    `    return;`,
    `}`,
    `try {`,
    `    if (connection.isClosed()) {`,
    `        return;`,
    `    }`,
    `    if (!connection.getAutoCommit()) {`,
    `        // JDBC-Spezifikation: setAutoCommit(true) committet eine offene`,
    `        // Transaktion implizit. Erst zurückrollen (quietly), dann in den`,
    `        // Autocommit-Modus schalten — release() darf keinen Teilstand`,
    `        // festschreiben (z. B. wenn der TxManager einen Error durchreicht).`,
    `        rollbackQuietly(connection);`,
    `        connection.setAutoCommit(true);`,
    `    }`,
  ],
  poolCas: [
    `private void warneErschoepfung() {`,
    `    long jetzt = System.nanoTime();`,
    `    long letzte = letzteWarnungNano.get();`,
    `    if (jetzt - letzte > 10_000_000_000L && letzteWarnungNano.compareAndSet(letzte, jetzt)) {`,
    `        LOG.warning("Verbindungspool erschöpft — ...");`,
    `    }`,
    `}`,
  ],
  taskInfo: [
    `final UUID id;`,
    `final String name;`,
    `final Instant submittedAt;`,
    `private volatile boolean timedOut;`,
    `/** Abrechnungs-Gate: garantiert, dass das Task-Ende (Zähler + running-Map) genau einmal passiert. */`,
    `private final AtomicBoolean abgerechnet = new AtomicBoolean(false);`,
  ],
  taskInfoSettle: [
    `/**`,
    ` * Merkt die Abrechnung des Task-Endes an. Liefert {@code true} genau beim`,
    ` * ersten Aufruf (compareAndSet) — Callable-Wrapper, Wachhund und Abbruch über`,
    ` * das Handle konkurrieren darum; wer verliert, hat nichts mehr abzurechnen`,
    ` * und darf insbesondere keinen Zähler doppelt inkrementieren.`,
    ` */`,
    `boolean settle() {`,
    `    return abgerechnet.compareAndSet(false, true);`,
    `}`,
  ],
  txContextVolatile: [
    `public final class TxContext implements AutoCloseable {`,
    ``,
    `    private final Connection connection;`,
    `    private final Instant deadline;`,
    `    private volatile TxStatus status = TxStatus.ACTIVE;`,
  ],
  cancelOnTimeout: [
    `private void cancelOnTimeout(FutureTask<?> future, TaskInfo info) {`,
    `    if (future.isDone()) {`,
    `        return;`,
    `    }`,
    `    info.markTimedOut();`,
    `    if (future.cancel(true) && info.settle()) {`,
    `        running.remove(info.id);`,
    `        timedOut.increment();`,
    `    }`,
    `}`,
  ],
  sagaSwitch: [
    `switch (wiederanlauf(saga, definition, claim)) {`,
    `    case COMPLETED -> completed++;`,
    `    case COMPENSATED -> compensated++;`,
    `    case FAILED -> failed++;`,
    `    default -> errors++;`,
    `}`,
  ],
  multiCatch: [
    `private boolean istGueltig(Connection connection) {`,
    `    try {`,
    `        return !connection.isClosed() && connection.isValid(VALIDIERUNGS_TIMEOUT_SEKUNDEN);`,
    `    } catch (SQLException | RuntimeException e) {`,
    `        LOG.fine(() -> "Verbindung besteht die Gültigkeitsprüfung nicht, wird verworfen: " + e);`,
    `        return false;`,
    `    }`,
    `}`,
  ],
};
// SozialFall-Record: vollständige Komponentenliste (Zeilen 24–43)
C.sozialFallRecord = [
  `public record SozialFall(`,
  `        long id,`,
  `        long bgId,`,
  `        int regelbedarfsstufe,`,
  `        boolean merkzeichenG,`,
  `        boolean schwanger,`,
  `        int anzKinderUnter7,`,
  `        int anzKinderUnter16,`,
  `        int anzKinderMinderjaehrig,`,
  `        boolean warmwasser,`,
  `        BigDecimal kduMiete,`,
  `        BigDecimal kduBetriebskosten,`,
  `        BigDecimal kduHeizung,`,
  `        BigDecimal einkommenRente,`,
  `        BigDecimal einkommenErwerb,`,
  `        BigDecimal abzSteuern,`,
  `        BigDecimal abzSozialversicherung,`,
  `        BigDecimal abzVersicherungen,`,
  `        BigDecimal abzErwerbsaufwendungen,`,
  `        String entbindungsmonat,`,
  `        long version) {`,
];

/* ------------------------------------------------------ Tabellen-Baustein */
function zelle(text, opts = {}) {
  return new TableCell({
    width: { size: opts.w, type: WidthType.PERCENTAGE },
    shading: { type: ShadingType.CLEAR, fill: opts.fill || "FFFFFF" },
    margins: { top: 60, bottom: 60, left: 100, right: 100 },
    children: [new Paragraph({
      alignment: opts.center ? AlignmentType.CENTER : AlignmentType.LEFT,
      spacing: { line: 276 },
      children: [new TextRun({
        text, bold: !!opts.b, size: opts.s || 20,
        color: opts.c || "000000",
        font: opts.m ? { ascii: "Consolas", eastAsia: "Consolas" } : { ascii: "Calibri", eastAsia: "Microsoft YaHei" },
      })],
    })],
  });
}
function uebersichtsTabelle() {
  const rows = [
    ["Nr.", "Thema", "Klasse(n)", "Zeilen", "Grad"],
    ["1", "Stream-Suche mit filter/findFirst/orElse", "BescheidGenerator", "294–298", "★"],
    ["2", "Records statt Klassen", "SozialFall", "24–43", "★"],
    ["3", "Optional: drei Varianten", "SozialLeistungsService", "121–126, 178, 221–224", "★★"],
    ["4", "Stream-Pipeline mit BigDecimal", "SozialLeistungsService", "530–542", "★★"],
    ["5", "Code als Argument (Funktions-Interface, Generics)", "TxWork, TxManager", "1–7 / 34–45", "★★★"],
    ["6", "Vierfache catch-Kaskade inkl. catch (Error)", "TxManager", "52–96", "★★"],
    ["7", "Interrupt-Behandlung", "RetryPolicy, SimpleConnectionPool, TaskController", "47–58 / 111–120 / 234–244", "★★★"],
    ["8", "Verbindungspool: Semaphore, Queue, finally-Freigabe", "SimpleConnectionPool", "56–63, 121–159, 178–196", "★★★★"],
    ["9", "compareAndSet, volatile, settle-Gate", "TaskInfo, TxContext, SimpleConnectionPool, TaskController", "17–22 / 43–51 / 16–20 / 256–265", "★★★★"],
    ["10", "Lazy-Logging mit Lambda (effectively final)", "TxManager", "86–91", "★★"],
    ["11", "Switch-Pfeil-Syntax und Multi-Catch", "SagaEngine, SimpleConnectionPool", "189–194 / 227–234", "★"],
  ];
  const w = [7, 30, 28, 23, 12];
  return new Table({
    width: { size: 100, type: WidthType.PERCENTAGE },
    layout: TableLayoutType.FIXED,
    borders: {
      top: { style: BorderStyle.SINGLE, size: 4, color: PAL.table.accentLine },
      bottom: { style: BorderStyle.SINGLE, size: 4, color: PAL.table.accentLine },
      left: NB, right: NB,
      insideHorizontal: { style: BorderStyle.SINGLE, size: 2, color: PAL.table.innerLine },
      insideVertical: NB,
    },
    rows: rows.map((r, i) => new TableRow({
      tableHeader: i === 0,
      cantSplit: true,
      children: r.map((text, j) => zelle(text, {
        w: w[j], fill: i === 0 ? PAL.table.headerBg : (i % 2 === 0 ? PAL.table.surface : "FFFFFF"),
        b: i === 0, c: i === 0 ? PAL.table.headerText : "000000",
        center: j === 0 || j === 4, m: j === 2 || j === 3, s: i === 0 ? 20 : 19,
      })),
    })),
  });
}

/* --------------------------------------------------------------- Inhalt */
const body = [];

/* ---- Kapitel 1 */
body.push(h1("1. So nutzt du dieses Dokument"));
body.push(p([
  { t: "Dieses Dokument sammelt die Stellen im LMG-Code, an denen man mit Java-Grundkenntnissen am ehesten hängen bleibt, und erklärt sie in einfachen Worten. Zu jeder Stelle gehören vier Dinge: die exakte " },
  { t: "Fundstelle", b: true },
  { t: " (Datei, Klasse und Zeilennummern), der " },
  { t: "Originalcode", b: true },
  { t: " (unverändert aus dem Projekt, auch mit seinen deutschen Kommentaren), eine " },
  { t: "Schritt-für-Schritt-Lesung", b: true },
  { t: " und ein " },
  { t: "Merksatz", b: true },
  { t: " für typische Stolperfallen." },
]));
body.push(p([
  { t: "Der Einstieg ist die Schnellübersicht in Kapitel 2 — dort steht jede Stelle mit Schwierigkeitsgrad (Sterne). Die Reihenfolge der Kapitel folgt dem Aufbau des Projekts, nicht dem Schwierigkeitsgrad; eine empfohlene Lese-Reihenfolge steht in Kapitel 7." },
]));
body.push(p([
  { t: "Kontext des Projekts: Java 21, bewusst keine Frameworks, Oracle-Datenbank. Es gibt zwei Paketwelten: " },
  { t: "de.allgemein.lmg", m: true },
  { t: " ist die Fachschicht (SozialFälle, Zahlungen, Bescheide) und " },
  { t: "de.allgemein.tx", m: true },
  { t: " ist der Transaktionskern (Verbindungsmanagement, Task-Steuerung, Wiederholung). Die Fachschicht-Klassen sind meist leichter lesbar; die härtesten Konstrukte stehen im Transaktionskern." },
]));
body.push(p([
  { t: "Zeilennummern beziehen sich auf den Projektstand vom 13. Oktober 2026 (Version 0.5.8, Commit 598fda0). Wenn sich Dateien später ändern, können die Zeilen um einige Positionen wandern — die Klassen- und Methodennamen bleiben die verlässlichen Anker." },
]));
body.push(p([
  { t: "Zur Ehrlichkeit bei den Codeblöcken: Die meisten Auszüge sind 1:1 aus dem Projekt übernommen. Ein paar sehr lange Stellen (Protokoll-Aufrufe an TxAuditLog, Null-Prüfungen, lange Meldungstexte) sind für die Lesbarkeit leicht gekürzt; solche Stellen sind im Code mit „…“ markiert und in der Fundstelle benannt. Wer es zeichengenau braucht, öffnet die angegebene Datei an den genannten Zeilen." },
]));

/* ---- Kapitel 2 */
body.push(h1("2. Schnellübersicht über alle Codestellen"));
body.push(p([
  { t: "Elf Stellen aus neun Klassen. „Grad“ = Schwierigkeit in Sternen (★ = Sprachmittel, schnell gelernt; ★★★★ = Nebenläufigkeit, braucht Ruhe)." },
]));
body.push(uebersichtsTabelle());
body.push(abstand());

/* ---- Kapitel 3 */
body.push(h1("3. Die Fachschicht: Records, Optional und Streams"));

body.push(h2("3.1 Das Original: eine Stream-Suche im Bescheid-Druck"));
body.push(fundstelle("BescheidGenerator.java", "BescheidGenerator", "294–298", "★", "berechnungsTabelle"));
body.push(codeBlock(C.bescheidStream));
body.push(label("Was macht der Code?"));
body.push(p([
  { t: "Im Bescheid-Druck wird für jede berechnete Person eine Tabellenzeile gebaut. Im Schleifenkörper liegt das Rechenergebnis einer Person (" },
  { t: "fb", m: true },
  { t: ") vor — aber der Druck braucht zusätzlich die Stammdaten dieser Person, das " },
  { t: "SozialFall", m: true },
  { t: "-Objekt. Die Verknüpfung zwischen beiden ist die ID. Die gesuchte Zeile bedeutet in einem Satz: " },
  { t: "„Suche in der Liste den SozialFall mit der Nummer fb.fallId() — gefunden gibt ihn zurück, nicht gefunden gib null.“", b: true },
]));
body.push(label("Schritt für Schritt"));
body.push(numItem("steps31", "faelle.stream() — die Liste aller SozialFälle wird zu einem Fließband, auf dem jeder Fall nacheinander ansehen werden kann."));
body.push(numItem("steps31", "filter(f -> f.id() == fb.fallId()) — ein Prüfer läuft über das Band und lässt nur Fälle durch, deren id() gleich fb.fallId() ist. f ist der jeweilige Fall, der Name ist frei gewählt."));
body.push(numItem("steps31", "findFirst() — nimm den ersten durchgelassenen Fall. Da auch nichts durchgelassen werden kann, kommt kein direktes Objekt zurück, sondern eine Schachtel (Optional), die leer sein darf."));
body.push(numItem("steps31", "orElse(null) — Schachtel öffnen; ist sie leer, gib stattdessen null zurück."));
body.push(numItem("steps31", "Das == bei f.id() == fb.fallId() ist hier korrekt, weil long-Werte verglichen werden (Zahlen). Bei String-IDs müsste equals() her!"));
body.push(merke([
  { t: "Direkt in der nächsten Zeile steckt die Folgeprüfung: " },
  { t: "fall == null ? \"–\" : fall.regelbedarfsstufe()", m: true },
  { t: " — wird kein Fall gefunden, steht im Druck ein Strich statt einer Stufe. orElse(null) und die anschließende null-Prüfung gehören immer zusammen; ohne die Prüfung droht die NullPointerException." },
]));

body.push(h2("3.2 Records statt Klassen — warum es f.id() heißt"));
body.push(fundstelle("SozialFall.java", "SozialFall (record)", "24–43", "★", "Record-Deklaration"));
body.push(codeBlock(C.sozialFallRecord));
body.push(label("Was macht der Code?"));
body.push(p([
  { t: "Ein " },
  { t: "record", b: true },
  { t: " ist eine Kurzfassung einer Klasse für unveränderliche Datenbehälter. Die Parameterliste im Kopf " },
  { t: "ist", b: true },
  { t: " die Feldliste — mehr muss man nicht schreiben. Der Compiler erzeugt automatisch: einen Konstruktor mit allen Feldern, Zugriffsmethoden mit dem Feldnamen (id(), bgId(), schwanger(), …), equals(), hashCode() und toString()." },
]));
body.push(p([
  { t: "Das erklärt die Frage aus der Einführung: Es heißt " },
  { t: "f.id()", m: true },
  { t: " und nicht f.getId(), weil Records ihre Zugriffsmethoden direkt nach den Feldern benennen. Wer vergeblich nach einem Getter sucht: Die Methode existiert trotzdem — nur ohne das get-Präfix." },
]));
body.push(label("Schritt für Schritt"));
body.push(numItem("steps32", "public record SozialFall(...) — Klassendeklaration als Record; die Klasse ist final und ihre Felder sind final (unveränderlich)."));
body.push(numItem("steps32", "Jede Zeile der Parameterliste ist ein Feld mit Typ: long id, int regelbedarfsstufe, boolean schwanger, …"));
body.push(numItem("steps32", "Zugriff über den Feldnamen als Methode: fall.regelbedarfsstufe() liefert ein int, fall.entbindungsmonat() einen String."));
body.push(numItem("steps32", "Werte ändern heißt: neues Objekt bauen. Das schützt vor unabsichtlichen Zustandsänderungen mitten in einer Berechnung."));
body.push(merke([
  { t: "Das letzte Feld " },
  { t: "long version", m: true },
  { t: " dient dem optimistischen Locking (Versionszähler der Datenbankzeile) — dazu passt, dass Records ideal für solche unveränderlichen Abbilder von DB-Zeilen sind." },
]));

body.push(h2("3.3 Optional: drei Varianten desselben Musters"));
body.push(fundstelle("SozialLeistungsService.java", "SozialLeistungsService", "121–126, 178 und 221–224", "★★", "ladeDetail / aendereSozialFall / …"));
body.push(codeBlock(C.optionalA));
body.push(p([{ t: "Zusätzlich zwei Kurzformen aus derselben Klasse — Zeile 178:" }], { spacing: { after: 60, line: 312 } }));
body.push(codeBlock(C.optionalB));
body.push(p([{ t: "und die Zeilen 221–223:" }], { spacing: { after: 60, line: 312 } }));
body.push(codeBlock(C.optionalC));
body.push(label("Was macht der Code?"));
body.push(p([
  { t: "Alle drei Stellen beantworten dieselbe Frage — „was, wenn das Gesuchte nicht existiert?“ — auf drei verschiedene Arten:" },
]));
body.push(numItem("steps33", "orElseThrow(() -> new FachlicheException(...)) — Zeilen 121–122: Fehlt die Bedarfsgemeinschaft, wird ein fachlicher Fehler mit klarer deutscher Meldung geworfen. Das Argument ist ein Mini-Lieferant (Supplier): Die Ausnahme wird erst gebaut, wenn sie wirklich gebraucht wird."));
body.push(numItem("steps33", "orElseThrow() ohne Argument — Zeile 178: wirft automatisch eine NoSuchElementException. Kurz, aber mit generischer Meldung — im Projekt nur dort verwendet, wo das Fehlen programmatisch unmöglich sein sollte."));
body.push(numItem("steps33", "orElse(null) + eigene if-Prüfung — Zeilen 221–223: hier ist „nicht gefunden“ ein normaler, erwarteter Ablauf (z. B. Anlage einer neuen BG), deshalb null und eine saubere Fachprüfung."));
body.push(merke([
  { t: "Faustregel im Projekt: Fehlt ein Objekt unerwartet → orElseThrow mit FachlicheException (sprechende Meldung). Ist Leere ein normaler Fall → orElse(null) mit if. Nie beide Male durcheinander, sonst endet ein normaler Ablauf als Systemfehler." },
]));

body.push(h2("3.4 Die Kassenlauf-Pipeline: Streams mit Methodenreferenzen und BigDecimal"));
body.push(fundstelle("SozialLeistungsService.java", "SozialLeistungsService", "530–542", "★★", "Kassenlauf / Uebergabe"));
body.push(codeBlock(C.kassenlauf));
body.push(label("Was macht der Code?"));
body.push(p([
  { t: "Beim Kassenlauf werden fertige Zahlungen an die Zahlstelle übergeben. Vorher prüft die Methode je Bedarfsgemeinschaft unter Datenbank-Sperre, ob sie noch aktiv ist; nur aktive Zahlungen dürfen übergeben werden. Der Block ist die dichteste Stream-Stelle der Fachschicht und zeigt drei Werkzeuge auf einmal." },
]));
body.push(label("Schritt für Schritt"));
body.push(numItem("steps34", "Zahlung::bgId — Doppelkolon ist eine Methodenreferenz, Abkürzung für z -> z.bgId(). Die erste Zeile sammelt also alle BG-Nummern der fertigen Zahlungen (ohne Doppelte, sortiert)."));
body.push(numItem("steps34", "lockForUpdate(...) sperrt die BG-Zeile in der Datenbank (SELECT … FOR UPDATE), .map(Bedarfsgemeinschaft::statusAktiv) holt deren Aktiv-Status, .orElse(false) behandelt „nicht gefunden“ als inaktiv."));
body.push(numItem("steps34", "Warum die Umwege über die Map aktiv? Weil man Datenbank-Sperren nicht mitten in einem filter-Lambda setzen will: erst sammeln, dann filtern — bereite.stream().filter(z -> aktiv.get(z.bgId())) behält nur Zahlungen aktiver BGs."));
body.push(numItem("steps34", "reduce(BigDecimal.ZERO, BigDecimal::add) bildet die Summe: Startwert 0, dann wird jeder Betrag addiert. BigDecimal::add ist wieder eine Methodenreferenz, diesmal auf eine Methode des Betrags selbst."));
body.push(numItem("steps34", "Die IDs der gefilterten Zahlungen (Zeile 542) gehen an uebergebeGesperrte(...) — es wird genau die gesperrte und geprüfte Menge übergeben, Anzahl und Summe bleiben konsistent."));
body.push(merke([
  { t: "Geld wird im ganzen Projekt mit " },
  { t: "BigDecimal", m: true },
  { t: " gerechnet, nie mit double — double produziert Rundungsfehler (0,1 + 0,2 ist nicht genau 0,3). BigDecimal vergleicht man mit compareTo(), nicht mit equals(), weil 2.50 und 2.5 via equals() unterschiedlich sind. setScale(2) erzwingt zwei Nachkommastellen." },
]));

/* ---- Kapitel 4 */
body.push(h1("4. Der Transaktionskern: Lambdas, Generics und Fehlerbehandlung"));

body.push(h2("4.1 Code als Argument: TxWork und die generische execute-Methode"));
body.push(fundstelle("TxWork.java (ganzes Interface) und TxManager.java", "TxWork / TxManager", "1–7 und 34–45", "★★★", "execute"));
body.push(codeBlock(C.txWork));
body.push(codeBlock(C.txManagerExecute));
body.push(label("Was macht der Code?"));
body.push(p([
  { t: "TxWork ist ein " },
  { t: "Funktions-Interface", b: true },
  { t: ": ein Interface mit genau einer Methode. Nur deshalb kann man an seiner Stelle ein Lambda übergeben. Der TxManager führt eine Arbeitseinheit als Oracle-Transaktion aus: Verbindung leihen, Transaktion beginnen, die Arbeit des Aufrufers laufen lassen, bei Erfolg committen — und bei jedem Fehler zurückrollen (Sperren-Konflikte wiederholt er sogar mehrfach)." },
]));
body.push(p([
  { t: "Der Aufrufer schreibt also etwa: txManager.execute(TxOptions.DEFAULT, ctx -> { … return ergebnis; }). Das Lambda ist kein Objekt im klassischen Sinn, sondern ein " },
  { t: "Paket aus Code", b: true },
  { t: ", das der Manager später aufruft — dabei hält es den Transaktionskontext ctx bereit (darüber laufen alle Datenbankzugriffe)." },
]));
body.push(label("Schritt für Schritt"));
body.push(numItem("steps41", "public <T> T execute(...) — das <T> vor dem Rückgabetyp erklärt: „Diese Methode funktioniert für jeden Ergebnistyp“. Beim Aufruf wird T automatisch auf den Typ des Lambda-Ergebnisses festgelegt."));
body.push(numItem("steps41", "T run(TxContext ctx) throws Exception — die throws-Klausel erlaubt checked Exceptions IM Lambda, ohne eigenes try/catch. Der TxManager fängt und behandelt sie."));
body.push(numItem("steps41", "Im Manager läuft die Arbeit in einer Schleife (maxAttempts): work.run(ctx) ist der Aufruf des übergebenen Codes."));
body.push(numItem("steps41", "ctx.checkDeadline() und raw.commit() — nach erfolgreicher Arbeit wird das Zeitlimit geprüft und erst dann committet. Die Reihenfolge ist wichtig: erst prüfen, dann festschreiben."));
body.push(merke([
  { t: "Stößt du beim Lesen auf ctx -> {, gehört alles bis zum schließenden }); in die Transaktion — und der Code KANN mehrfach laufen (Wiederholung bei Sperren-Konflikten!). Deshalb gehören keine Nebenwirkungen mit Außenwirkung hinein (z. B. E-Mails senden), nur Datenbankarbeit." },
]));

body.push(h2("4.2 Die vierfache catch-Kaskade — und das catch (Error)"));
body.push(fundstelle("TxManager.java", "TxManager", "52–96", "★★", "execute (Fortsetzung)"));
body.push(codeBlock(C.catch1));
body.push(p([
  { t: "Es folgen der allgemeine und der schwerste Fall — Zeilen 63–85 (auszugsweise fortgeführt):" },
], { spacing: { after: 60, line: 312 } }));
body.push(codeBlock(C.catch2));
body.push(codeBlock(C.catch3));
body.push(codeBlock(C.catch4));
body.push(label("Was macht der Code?"));
body.push(p([
  { t: "Vier Fang-Blöcke, von speziell nach allgemein geordnet — die Reihenfolge ist zwingend (allgemein zuerst wäre ein Compilerfehler):" },
]));
body.push(numItem("steps42", "catch (TxException) — der eigene Transaktionsfehler; er weiß selbst, ob ein Wiederholversuch sinnvoll ist (isRetryable()). Wenn ja und Versuche übrig sind, läuft die Schleife weiter."));
body.push(numItem("steps42", "catch (SQLException) — Rohfehler der Datenbank; wird über TxException.from(e) verpackt und gleich behandelt."));
body.push(numItem("steps42", "catch (Exception) — alles sonstige Programmatische (z. B. fachliche Validierung): kein Retry, sofort nonRetryable."));
body.push(numItem("steps42", "catch (Error) — VM-Katastrophen wie OutOfMemoryError. Das sieht man fast nie; hier ist es bewusst, denn ohne Rollback bliebe die Transaktion offen und das release() könnte sie implizit committen (siehe Kapitel 5.2)."));
body.push(p([
  { t: "Das " },
  { t: "finally", b: true },
  { t: " gibt die geliehene Verbindung in jedem Fall an den Pool zurück — ob Erfolg, Fachfehler oder Absturz. Und Zeile 96 (throw new AssertionError(\"unreachable\")): Der Compiler verlangt am Methodenende ein return oder throw, obwohl die Schleife logisch nie dorthin kommt (jeder Durchlauf endet früher). Die Zeile beruhigt nur den Compiler." },
]));
body.push(merke([
  { t: "Merksatz für catch-Kaskaden: erst das Spezielle, dann das Allgemeine — und bei allem, was eine Ressource geliehen hat (hier: die DB-Verbindung), ein finally, das sie zurückgibt." },
]));

body.push(h2("4.3 Lazy-Logging mit Lambda — und „effectively final“"));
body.push(fundstelle("TxManager.java", "TxManager", "86–91", "★★", "execute (Wiederholungs-Warnung)"));
body.push(codeBlock(C.loggerLambda));
body.push(label("Was macht der Code?"));
body.push(p([
  { t: "Der Logger-Aufruf bekommt kein fertiges Textstück, sondern eine Lieferfunktion (() -> Text). Der Text wird erst dann zusammengebaut, wenn die Logzeile wirklich ausgegeben wird — schaltet jemand WARNING ab, kostet der Aufruf fast nichts. Das Lambda ist eine Supplier<String>, kein „Callback“ im Sinne von „später bei Ereignis“." },
]));
body.push(label("Schritt für Schritt"));
body.push(numItem("steps43", "() -> \"...\" — leere Parameterliste, dann der aufzubauende Text. Typ: Supplier<String> („liefert einen String“)."));
body.push(numItem("steps43", "final int tried = attempt; — Lambdas dürfen nur unveränderliche Variablen einfangen („effectively final“). Die Schleifenvariablen attempt und lastFailure ändern sich, deshalb werden sie vorher in finale Kopien umgefüllt."));
body.push(merke([
  { t: "Fehlermeldung „local variables referenced from a lambda expression must be final or effectively final“ hat genau diese Ursache — Abhilfe ist das Umkopieren in eine final-Variable, wie hier in Zeilen 86–87 demonstriert." },
]));

/* ---- Kapitel 5 */
body.push(h1("5. Nebenläufigkeit: Threads, Interrupts und atomare Operationen"));

body.push(h2("5.1 Interrupt-Behandlung — die drei Varianten im Projekt"));
body.push(fundstelle("RetryPolicy.java / SimpleConnectionPool.java / TaskController.java", "RetryPolicy / SimpleConnectionPool / TaskController", "47–58 / 111–120 / 234–244", "★★★", "sleepBeforeAttempt / getConnection / call()-finally"));
body.push(codeBlock(C.retryPolicy));
body.push(codeBlock(C.poolTryAcquire));
body.push(codeBlock(C.poolInterrupted));
body.push(label("Was macht der Code?"));
body.push(p([
  { t: "Ein " },
  { t: "Interrupt", b: true },
  { t: " ist kein Abbruch-Befehl, sondern ein Flag am Thread. Der Wachhund des TaskControllers setzt es, wenn ein Task sein Zeitlimit überschreitet; schlapfende Stellen (Thread.sleep, Warten auf Pool-Verbindung) spüren das als InterruptedException — und das Flag wird dabei automatisch gelöscht." },
]));
body.push(label("Schritt für Schritt"));
body.push(numItem("steps51", "Variante 1 (RetryPolicy, Pool): Beim Fangen der InterruptedException das Flag mit Thread.currentThread().interrupt() wieder setzen und die Situation mit einer eigenen Ausnahme beenden. Warum wieder setzen? Höherliegender Code (z. B. der TaskController) soll sehen können, dass unterbrochen wurde."));
body.push(numItem("steps51", "Variante 2 (TaskController): Thread.interrupted() ist eine andere Methode: Sie liefert den Flag-Wert UND löscht ihn. Am Ende jedes Tasks wird so ein überlebender Interrupt-Rest weggeräumt — sonst würde der NÄCHSTE Task auf demselben Worker-Thread das Flag erben und spontan fehlen."));
body.push(numItem("steps51", "Der Backoff selbst: initialBackoffMillis << Math.min(failedAttempt - 1, 10) verdoppelt die Wartezeit pro Fehlversuch (Bit-Shift = mal 2 hoch n), der Zufallsanteil (jitter) verhindert, dass alle blockierten Tasks im gleichen Takt erneut losschlagen."));
body.push(merke([
  { t: "Drei Regeln: (1) Interrupt-Flag prüfen mit isInterrupted(). (2) InterruptedException fangen → Flag wieder setzen, wenn man selbst nicht dafür zuständig ist. (3) Flag gezielt löschen mit Thread.interrupted(), wenn man Aufräumer ist. Der Kommentar an TaskController.java:239–243 erklärt das übrigens selbst — das Projekt kommentiert seine Fallen." },
]));

body.push(h2("5.2 Der Verbindungspool: Semaphore, Queue und das success-Flag"));
body.push(fundstelle("SimpleConnectionPool.java", "SimpleConnectionPool", "56–63, 121–159 und 178–196", "★★★★", "Felder / getConnection / release"));
body.push(codeBlock(C.poolFelder));
body.push(codeBlock(C.poolGetConnection));
body.push(codeBlock(C.poolRelease));
body.push(label("Was macht der Code?"));
body.push(p([
  { t: "Der Pool verwaltet die teuren Oracle-Verbindungen. Drei Spezialwerkzeuge arbeiten zusammen:" },
]));
body.push(numItem("steps52", "Semaphore permits — ein Zähler für freie Plätze, Startwert maxSize. tryAcquire(timeout) zieht einen Platz ab oder gibt nach 30 Sekunden auf („Pool erschöpft“) — statt endlos zu warten."));
body.push(numItem("steps52", "BlockingQueue idle — threadsichere Warteschlange der freien Verbindungen; poll() nimmt eine heraus, offer() legt eine zurück."));
body.push(numItem("steps52", "Set borrowed — merkt, welche Verbindungen gerade ausgeliehen sind. Grundlage für das idempotente release(): Ein Doppel-Release wird erkannt und bewusst ignoriert, sonst würde ein zweites release() den Platz-Zähler fälschlich erhöhen (maxSize wäre ausgehebelt)."));
body.push(p([
  { t: "Das Herzstück ist das " },
  { t: "success-Flag", b: true },
  { t: " in getConnection: Nur wenn am Ende KEINE Verbindung an einen Aufrufer ging, gibt das finally das Permit zurück. Ging eine Verbindung raus, behält der Aufrufer das Permit bis zu seinem release(). Würde man das Permit immer freigeben, könnten sich plötzlich mehr Verbindungen leihen als maxSize erlaubt." },
]));
body.push(p([
  { t: "Die zweite Falle steht in release(): " },
  { t: "connection.setAutoCommit(true)", m: true },
  { t: " committet laut JDBC-Spezifikation eine noch offene Transaktion! Deshalb rollt der Pool vorher erst zurück — eine Verbindung, die nach einem Absturz zurückkommt, darf keine Teiländerungen festschreiben." },
]));
body.push(merke([
  { t: "Merksatz: Ressourcen-Zähler (Permit) und Ressource (Verbindung) sind zwei Dinge. Wer die Ressource nicht bekommen hat, muss den Zählerstand zurückgeben — und wer die Ressource zurückgibt, gibt den Zähler genau EINMAL zurück (borrowed.remove als Schranke)." },
]));

body.push(h2("5.3 compareAndSet, volatile und das settle-Gate"));
body.push(fundstelle("SimpleConnectionPool.java / TaskInfo.java / TxContext.java / TaskController.java", "mehrere", "163–170 / 17–22 + 43–51 / 16–20 / 256–265", "★★★★", "warneErschoepfung / TaskInfo / TxContext / cancelOnTimeout"));
body.push(codeBlock(C.poolCas));
body.push(codeBlock(C.taskInfo));
body.push(codeBlock(C.taskInfoSettle));
body.push(codeBlock(C.txContextVolatile));
body.push(codeBlock(C.cancelOnTimeout));
body.push(label("Was macht der Code?"));
body.push(p([
  { t: "compareAndSet(erwartet, neu) ist ein atomarer Einzel-Schritt: „Wenn der aktuelle Wert noch der erwartete ist, setze neu — und melde true.“ Wenn zwei Threads gleichzeitig probieren, gewinnt genau einer. Im Pool wird damit die Erschöpfungs-Warnung auf eine Meldung pro 10 Sekunden gedrosselt: Viele Threads kommen gleichzeitig, aber nur der eine, der den Tausch gewinnt, schreibt die Warnung." },
]));
body.push(p([
  { t: "Das " },
  { t: "settle()-Gate", b: true },
  { t: " nutzt denselben Mechanismus für ein „genau einmal“: Beim Ende eines Tasks konkurrieren drei Stellen um die Abrechnung der Statistik — der Task selbst, der Wachhund (Zeitlimit) und ein Abbruch über das Handle. settle() liefert true nur dem Ersten; die Verlierer zählen nichts und räumen nichts. Ohne dieses Gate würden Zähler wie completed/timedOut gelegentlich doppelt hochlaufen." },
]));
body.push(p([
  { t: "volatile", b: true },
  { t: " ist die leichtere Schwester: Es garantiert nur, dass Änderungen an EINEM Feld von allen Threads sofort sichtbar sind (kein veralteter CPU-Cache). Es macht Operationen NICHT atomar — für „genau einmal“ braucht es deshalb das AtomicBoolean." },
]));
body.push(label("Schritt für Schritt"));
body.push(numItem("steps53", "future.cancel(true) im Wachhund bricht den Task per Interrupt ab (true = „unterbreche, falls er läuft“) und liefert true, wenn genau dieser Abbruch das Ende herbeiführt."));
body.push(numItem("steps53", "info.markTimedOut() setzt ein volatile-Flag, das später den Zähler entscheidet: timedOut statt failed."));
body.push(numItem("steps53", "info.settle() entscheidet, wer die Abrechnung bekommt — Task, Wachhund oder Handle, genau einer."));
body.push(merke([
  { t: "Faustregel: volatile für einfache Status-Flags, AtomicBoolean/AtomicLong für „genau einmal“-Entscheidungen und Zähler über Threads. Beides ersetzt keine richtige Sperre, wenn mehrere Felder zusammen geändert werden müssen." },
]));

/* ---- Kapitel 6 */
body.push(h1("6. Kurzkapitel: Moderne Syntax"));

body.push(h2("6.1 Switch mit Pfeil-Syntax"));
body.push(fundstelle("SagaEngine.java", "SagaEngine", "189–194", "★", "Wiederanlauf"));
body.push(codeBlock(C.sagaSwitch));
body.push(label("Was macht der Code?"));
body.push(p([
  { t: "Beim Wiederanlauf nach einem Server-Neustart wird jede hängende Saga abgeschlossen, kompensiert oder als fehlgeschlagen markiert — je Ergebnis läuft ein anderer Zähler hoch. Der moderne Switch („Arrow-Form“) hat kein break und kein Durchfallen: case COMPLETED -> completed++; macht genau eines. Der Compiler prüft bei enum-Werten die Vollständigkeit, deshalb ist hier das default nur Sicherheitsnetz für künftige Fälle. Ein solcher Switch kann auch als Ausdruck einen Wert liefern (int x = switch (e) { case A -> 1; ... };)." },
]));

body.push(h2("6.2 Multi-Catch: zwei Fehlertypen, ein Block"));
body.push(fundstelle("SimpleConnectionPool.java", "SimpleConnectionPool", "227–234", "★", "istGueltig"));
body.push(codeBlock(C.multiCatch));
body.push(label("Was macht der Code?"));
body.push(p([
  { t: "catch (SQLException | RuntimeException e) fängt zwei Typen mit einem Block. e hat dann den gemeinsamen Ober-Typ (hier Exception). Hintergrund: Beim Validieren einer Verbindung kann der Treiber selbst kaputt sein und sogar Laufzeitausnahmen werfen — die Gültigkeitsprüfung darf aber nie eine solche durchrutschen lassen, sonst bekommt ein Aufrufer eine unbenutzbare Verbindung. Also: beides fangen, Verbindung verwerfen (Rückgabe false)." },
]));
body.push(merke([
  { t: "Multi-Catch spart Dopplung für identische Behandlung. Brauchen die Typen verschiedene Behandlung, bleibt es bei getrennten catch-Blöcken (wie in Kapitel 4.2)." },
]));

/* ---- Kapitel 7 */
body.push(h1("7. Empfohlene Reihenfolge und nächste Schritte"));
body.push(p([
  { t: "Wer alles der Reihe nach liest, stößt sich an den schwersten Stellen früh. Besser ist diese Staffelung:" },
]));
body.push(numItem("steps7", "Stelle 1 und 2 (Stream-Suche, Records) — reine Sprachmittel, sofort im Alltag nützlich."));
body.push(numItem("steps7", "Stelle 11 (Switch-Pfeil, Multi-Catch) — kurzes Syntaxkapitel, ebenfalls sofort anwendbar."));
body.push(numItem("steps7", "Stellen 3, 4 und 10 (Optional-Varianten, Kassenlauf-Pipeline, Lazy-Logging) — die Stream- und Lambda-Idiome der Fachschicht."));
body.push(numItem("steps7", "Stellen 5 und 6 (TxWork, catch-Kaskade) — das Drehkreuz des Transaktionskerns verstehen; danach erschließt sich die Signatur vieler Service-Methoden."));
body.push(numItem("steps7", "Stelle 7 (Interrupts) — in Ruhe lesen, am besten mit Kapitel 4.2 im Hinterkopf (der Wachhund bricht genau diese execute-Aufrufe ab)."));
body.push(numItem("steps7", "Stellen 8 und 9 (Pool, compareAndSet/settle) — die härteste Kost; hier lohnt es sich, zweimal zu lesen und die Kommentare im Code mitzunehmen."));
body.push(p([
  { t: "Drei Tipps für das Weiterlesen: Erstens kommentiert das Projekt seine Fallen ausführlich auf Deutsch — oft mit CR-Nummern aus dem Codereview (Bericht: doc/codereview/). Zweitens sind die Tests Anwendungsbeispiele: TxManagerTest zeigt, wie man execute mit Lambdas benutzt; LmgServerHttpTest zeigt die HTTP-Seite. Drittens gibt der Klassen- und Architekturüberblick (doc/Klassen-und-Architekturueberblick.docx) den Zusammenhang aller Klassen und einen Lernpfad." },
]));
body.push(p([
  { t: "Bewusst nicht abgedeckt (Stoff für ein Folgedokument): der JSON-Parser (de.allgemein.lmg.web.Json, Rekursion über Zeichen), der Saga-Zustandsautomat (SagaEngine mit Journal-Tabellen und Kompensation) und der Oracle-UCP-Pool. Auch die testsweet-Klassen (Mitarbeiter-Simulator) folgen eigenen Mustern (Selenium, Szenarien) und verdienen ein eigenes Lern-Dokument." },
]));

/* ---------------------------------------------------------- Dokument */
const pgSize = { width: 11906, height: 16838 };
const pgMargin = { top: 1440, bottom: 1440, left: 1701, right: 1417 };

function pageNumFooter() {
  return new Footer({
    children: [new Paragraph({
      alignment: AlignmentType.CENTER,
      children: [new TextRun({ children: [PageNumber.CURRENT], size: 18, color: "888888", font: { ascii: "Calibri" } })],
    })],
  });
}
function kopfzeile() {
  return new Header({
    children: [new Paragraph({
      alignment: AlignmentType.RIGHT,
      border: { bottom: { style: BorderStyle.SINGLE, size: 2, color: PAL.table.innerLine, space: 4 } },
      children: [new TextRun({
        text: "Schwierige Codestellen verstehen · SDN.Leistung LMG",
        size: 16, color: "999999", font: { ascii: "Calibri" },
      })],
    })],
  });
}

const numberingRefs = ["steps31", "steps32", "steps33", "steps34", "steps41", "steps42", "steps43", "steps51", "steps52", "steps53", "steps7"];
const doc = new Document({
  creator: "SDN.Leistung LMG — learning-werkzeug",
  title: "Schwierige Codestellen verstehen",
  description: "Java-Lernführer: 11 Codestellen mit Fundstelle, Code und Erklärung",
  styles: {
    default: {
      document: {
        run: { font: { ascii: "Calibri", eastAsia: "Microsoft YaHei" }, size: 22, color: "000000" },
        paragraph: { spacing: { line: 312 } },
      },
      heading1: {
        run: { font: { ascii: "Calibri", eastAsia: "SimHei" }, size: 32, bold: true, color: HEAD_COLOR },
        paragraph: { spacing: { before: 360, after: 160, line: 312 }, outlineLevel: 0 },
      },
      heading2: {
        run: { font: { ascii: "Calibri", eastAsia: "SimHei" }, size: 26, bold: true, color: HEAD_COLOR },
        paragraph: { spacing: { before: 280, after: 120, line: 312 }, outlineLevel: 1 },
      },
    },
  },
  numbering: {
    config: numberingRefs.map(ref => ({
      reference: ref,
      levels: [{
        level: 0, format: LevelFormat.DECIMAL, text: "%1.", alignment: AlignmentType.LEFT,
        style: { paragraph: { indent: { left: 500, hanging: 300 } } },
      }],
    })),
  },
  sections: [
    { // Abschnitt 1: Deckblatt (Rand 0, keine Fußzeile)
      properties: { page: { size: pgSize, margin: { top: 0, bottom: 0, left: 0, right: 0 } } },
      children: buildCoverR1({
        title: "Schwierige Codestellen verstehen",
        subtitle: "Java-Lernführer: 11 Codestellen mit Fundstelle, Code und Erklärung",
        englishLabel: "JAVA LEARNING GUIDE",
        metaLines: [
          "Projekt: SDN.Leistung — Leistungsmanagement Grundsicherung",
          "Umfang: 11 Codestellen aus 9 Klassen (Java 21, Oracle)",
          "Stand: 13. Oktober 2026 · Version 0.5.8 · doc/Learning",
        ],
        footerLeft: "SDN.Leistung LMG · learning-werkzeug",
        footerRight: "2026-10-13",
        palette: PAL,
      }),
    },
    { // Abschnitt 2: Inhaltsverzeichnis (röm. Seitenzahlen)
      properties: {
        type: SectionType.NEXT_PAGE,
        page: { size: pgSize, margin: pgMargin, pageNumbers: { start: 1, formatType: NumberFormat.UPPER_ROMAN } },
      },
      footers: { default: pageNumFooter() },
      children: [
        new Paragraph({
          alignment: AlignmentType.CENTER,
          spacing: { before: 480, after: 360 },
          children: [new TextRun({ text: "Inhaltsverzeichnis", bold: true, size: 32, color: HEAD_COLOR, font: { ascii: "Calibri", eastAsia: "SimHei" } })],
        }),
        new TableOfContents("Inhaltsverzeichnis", { hyperlink: true, headingStyleRange: "1-2" }),
        new Paragraph({
          spacing: { before: 200 },
          children: [new TextRun({
            text: "Hinweis: Das Inhaltsverzeichnis wird über Word-Feldfunktionen erzeugt. Nach Änderungen am Dokument: Rechtsklick auf das Inhaltsverzeichnis → „Felder aktualisieren“ → „Gesamtes Verzeichnis aktualisieren“.",
            italics: true, size: 18, color: "888888", font: { ascii: "Calibri" },
          })],
        }),
      ],
    },
    { // Abschnitt 3: Hauptteil (arabische Seitenzahlen ab 1)
      properties: {
        type: SectionType.NEXT_PAGE,
        page: { size: pgSize, margin: pgMargin, pageNumbers: { start: 1, formatType: NumberFormat.DECIMAL } },
      },
      headers: { default: kopfzeile() },
      footers: { default: pageNumFooter() },
      children: body,
    },
  ],
});

const outPath = path.join(__dirname, "..", "Learning", "Schwierige-Codestellen-verstehen.docx");
fs.mkdirSync(path.dirname(outPath), { recursive: true });
Packer.toBuffer(doc).then(buf => {
  fs.writeFileSync(outPath, buf);
  console.log("Geschrieben: " + outPath + " (" + buf.length + " Bytes)");
}).catch(e => { console.error(e); process.exit(1); });
