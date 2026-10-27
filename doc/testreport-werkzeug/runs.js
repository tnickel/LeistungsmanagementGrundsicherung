// Sammelt die Simulator-Läufe aus testsweet/logs für den Management-Testreport.
// Bewusst vereinfacht gegenüber der strengen Nachweis-Sammlung des
// Testzertifikats (doc/testzertifikat-werkzeug/certificate-evidence.js):
// Der Report informiert, das Zertifikat weist formal nach.
const fs = require("fs");
const path = require("path");

function json(file) { return JSON.parse(fs.readFileSync(file, "utf8")); }

/** Alle Laufordner → Einträge {modus, kategorie, datum, gesamt, verifiziert,
 *  fehlgeschlagen, erfolgreich, ordner}. Ungültige Läufe bleiben mit
 *  erfolgreich=false sichtbar (ein abgewiesener Lauf ist auch eine Aussage). */
function alleLaeufe(repo) {
  const logs = path.join(repo, "testsweet", "logs");
  if (!fs.existsSync(logs)) return [];
  const eintraege = [];
  for (const name of fs.readdirSync(logs).filter(n => n.startsWith("run-")).sort()) {
    const ordner = path.join(logs, name);
    if (!fs.statSync(ordner).isDirectory()) continue;
    let lifecycle = null, txt = "";
    try { const f = path.join(ordner, "run-state.json"); if (fs.existsSync(f)) lifecycle = json(f); } catch (_) { /* gelesen defensiv */ }
    try { const f = path.join(ordner, "zusammenfassung.txt"); if (fs.existsSync(f)) txt = fs.readFileSync(f, "utf8"); } catch (_) { /* gelesen defensiv */ }
    const modus = /^Modus\s*:\s*(.+)$/m.exec(txt)?.[1]?.trim() || lifecycle?.mode || "?";
    const kategorie = /^Kategorie\s*:\s*(\S+)/m.exec(txt)?.[1] || lifecycle?.category || "?";
    const datum = (/^Zeitraum\s*:\s*(\S+)/m.exec(txt)?.[1] || lifecycle?.startedAt || name.slice(4)).slice(0, 10);
    const gesamt = Number(/^Aufträge\s*:\s*(\d+)/m.exec(txt)?.[1] ?? lifecycle?.totalCases ?? 0);
    const verifiziert = Number(/^verifiziert\s*:\s*(\d+)/m.exec(txt)?.[1] ?? lifecycle?.verifiedCases ?? 0);
    const fehlgeschlagen = Number(/^fehlgeschlagen\s*:\s*(\d+)/m.exec(txt)?.[1] ?? lifecycle?.failedCases ?? 0);
    const erfolgreich = /^Lauf erfolgreich\s*:\s*true/m.test(txt) || lifecycle?.success === true;
    eintraege.push({ modus, kategorie, datum, gesamt, verifiziert, fehlgeschlagen,
      erfolgreich, ordner: "testsweet/logs/" + name });
  }
  return eintraege;
}

/** Je Kategorie (Modus+Kategorie) den jüngsten Lauf; Kategorien in fester Reihenfolge. */
function letzteLaeufe(repo) {
  const reihenfolge = ["Fachschicht|POSITIV", "Fachschicht|NEGATIV", "Fachschicht|ECHT", "Fachschicht|RECHNER",
    "Fachschicht|LAST", "Fachschicht|STRESS", "WEB|POSITIV", "WEB|NEGATIV", "WEB|LAST"];
  const letzte = {};
  for (const lauf of alleLaeufe(repo)) {
    const key = lauf.modus + "|" + lauf.kategorie;
    if (!letzte[key] || lauf.ordner > letzte[key].ordner) letzte[key] = lauf;
  }
  return reihenfolge.map(key => ({ key, lauf: letzte[key] })).filter(e => e.lauf);
}

/** ISO-Datum (2026-10-27) als deutsches Datum (27.10.2026); Unbekanntes bleibt. */
function deutschDatum(iso) {
  const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(String(iso || ""));
  return m ? `${m[3]}.${m[2]}.${m[1]}` : String(iso || "—");
}

/** JUnit-Bestand je Modul: Zähler aus den surefire-Reports (je Datei ein
 *  testsuite-Element), Datum aus dem laufgebundenen Nachweis. */
function junitStand(repo) {
  const stand = {};
  for (const [modul, prefix] of [["Hauptprojekt", ""], ["testsweet", "testsweet/"]]) {
    const berichte = path.join(repo, prefix, "target", "surefire-reports");
    let tests = 0, failures = 0, errors = 0, skipped = 0, gelesen = 0;
    try {
      for (const name of fs.readdirSync(berichte).filter(n => n.endsWith(".xml"))) {
        const roh = fs.readFileSync(path.join(berichte, name), "utf8");
        const attribut = schl => Number(new RegExp('testsuite[^>]*\\b' + schl + '="(\\d+)"').exec(roh)?.[1] ?? 0);
        if (!Number.isFinite(attribut("tests"))) continue;
        tests += attribut("tests"); failures += attribut("failures");
        errors += attribut("errors"); skipped += attribut("skipped"); gelesen++;
      }
    } catch (_) { /* keine Reports vorhanden */ }
    let datum = "—";
    try { datum = deutschDatum(json(path.join(repo, prefix, "target", "test-evidence.json")).startedAt); } catch (_) { /* optional */ }
    stand[modul] = gelesen ? { datum, tests, fehler: failures + errors, skipped } : null;
  }
  return stand;
}

module.exports = { alleLaeufe, letzteLaeufe, junitStand, deutschDatum };
