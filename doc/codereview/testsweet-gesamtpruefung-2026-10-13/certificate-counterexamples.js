// Reproduzierbare Read-only-Gegenproben zur Zertifikatsaggregation.
// Aufruf aus dem Repository: node doc/codereview/testsweet-gesamtpruefung-2026-10-13/certificate-counterexamples.js
// Produktcode wird aus dem Repository gelesen, aber ausschließlich mit einer
// virtuellen Dateisystemansicht ausgeführt. Keine echten Nachweise werden verändert.
// Die Ausgabe certificate-counterexamples.json entsteht nur neben diesem Skript.
const fs = require("fs");
const path = require("path");
const vm = require("vm");
const assert = require("assert/strict");

const repo = path.resolve(process.argv[2] || path.join(__dirname, "..", "..", ".."));
const generator = path.join(repo, "doc/testzertifikat-werkzeug/generate.js");
const generatorSource = fs.readFileSync(generator, "utf8");
const sectionMarker = "// ─── Bausteine";
assert(generatorSource.includes(sectionMarker), "Generator-Abschnittsmarke fehlt");
const aggregationSource = generatorSource.split(sectionMarker)[0];

function summary(category, mode, success, count = 1) {
  return `Kategorie: ${category}\nModus: ${mode}\nAufträge: ${count}\nverifiziert: ${success ? count : 0}\nLauf erfolgreich: ${success}\nZeitraum: 2026-10-13\n`;
}

function certificateWith(extraRuns) {
  const runs = {};
  for (const category of ["POSITIV", "NEGATIV", "LAST", "STRESS"]) {
    runs[`run-20261013-120000-${category}`] = summary(category, "Fachschicht", true);
  }
  Object.assign(runs, extraRuns);
  const logs = path.join(repo, "testsweet", "logs");
  const fakeFs = {
    existsSync(file) {
      if (file === logs) return true;
      const run = path.basename(path.dirname(file));
      return path.basename(file) === "zusammenfassung.txt" && typeof runs[run] === "string";
    },
    readdirSync() { return Object.keys(runs); },
    readFileSync(file) {
      const value = runs[path.basename(path.dirname(file))];
      assert.equal(typeof value, "string", "Nur vorhandene virtuelle Zusammenfassungen dürfen gelesen werden");
      return value;
    },
  };
  const context = {
    __dirname: path.dirname(generator),
    process,
    console,
    require(name) {
      if (name === "fs") return fakeFs;
      if (name === "./llm-evidence") {
        // Für diese Aggregationsprobe gilt der LLM-Vorgängernachweis als gültig.
        return { latestEvidence: () => ({ valid: true, datum: "2026-10-13", meta: {} }) };
      }
      if (name.includes("docx")) return {}; // Dokumentausgabe wird nicht ausgeführt.
      return require(name);
    },
  };
  vm.createContext(context);
  vm.runInContext(aggregationSource + ";globalThis.reviewResult={allesBestanden,warnungen,LAEUFE,rows:PRUEFSCHICHTEN.map(r=>({haken:r.haken,titel:r.titel}))};", context);
  // JSON-Kopie entfernt Cross-Context-Prototypen, damit Assertions reproduzierbar sind.
  return JSON.parse(JSON.stringify(context.reviewResult));
}

const tests = [];
const mixedModes = certificateWith({
  "run-20261013-110000-WEBFAIL": summary("POSITIV", "Weboberfläche (Selenium)", false),
});
assert.equal(mixedModes.allesBestanden, true);
assert.deepEqual(mixedModes.warnungen, []);
assert.equal(mixedModes.LAEUFE.POSITIV.modus, "Fachschicht");
tests.push({
  id: "failed-web-hidden-by-later-successful-fachschicht",
  input: "WEB/POSITIV 11:00 fehlgeschlagen; FACH/POSITIV 12:00 erfolgreich; übrige Kategorien erfolgreich; gültiger LLM-Nachweis",
  actualVerdict: "BESTANDEN",
  warnings: mixedModes.warnungen,
  selectedPositiveRun: mixedModes.LAEUFE.POSITIV,
  webRow: mixedModes.rows.find(row => row.titel.includes("Weboberfläche")),
  expectedForStrictCertification: "Fehlerhafter WEB/POSITIV-Lauf muss separat sichtbar bleiben und ein grünes Gesamturteil verhindern.",
});

const incompleteRun = certificateWith({ "run-20261013-130000-interrupted": null });
assert.equal(incompleteRun.allesBestanden, true);
assert.deepEqual(incompleteRun.warnungen, []);
tests.push({
  id: "newest-run-without-summary-ignored",
  input: "Erfolgreiche ältere Läufe; neuester run-Ordner ohne zusammenfassung.txt (z. B. Ausnahmeabbruch); gültiger LLM-Nachweis",
  actualVerdict: "BESTANDEN",
  warnings: incompleteRun.warnungen,
  expectedForStrictCertification: "Ein gestarteter, abgebrochener Lauf benötigt einen auswertbaren Status; ältere Erfolge dürfen ihn nicht verdecken.",
});

const zeroCases = certificateWith({ "run-20261013-130000-POSITIV": summary("POSITIV", "Fachschicht", true, 0) });
assert.equal(zeroCases.allesBestanden, true);
assert.equal(zeroCases.LAEUFE.POSITIV.gesamt, 0);
tests.push({
  id: "zero-of-zero-accepted-as-passed",
  input: "Neueste POSITIV-Zusammenfassung behauptet Lauf erfolgreich:true bei Aufträge:0 und verifiziert:0",
  actualVerdict: "BESTANDEN",
  selectedPositiveRun: zeroCases.LAEUFE.POSITIV,
  expectedForStrictCertification: "Ein leerer Prüfumfang darf keine bestandene Prüfschicht liefern.",
  scope: "Robustheitsprobe für widersprüchliche Eingabebelege; reguläre GUI erzwingt positive Auftragszahlen.",
});

tests.push({
  id: "automatic-test-results-never-read",
  input: "Virtuelles Dateisystem enthält keinerlei Surefire-/JUnit-Belege",
  unitRow: mixedModes.rows[0],
  goldValuesRow: mixedModes.rows[1],
  scenarioRow: mixedModes.rows[2],
  ownSimulatorTestsCovered: mixedModes.rows.some(row => /testsweet.*(?:JUnit|Unit-Tests|Eigentests)/i.test(row.titel)),
  actualVerdict: mixedModes.allesBestanden ? "BESTANDEN" : "BEDINGT",
  expectedForStrictCertification: "Aktuelle Ergebnisse aus Hauptprojekt und testsweet inklusive Fehlern/Skips müssen nachgewiesen sein.",
});

const evidenceFile = path.join(repo, "doc/testzertifikat-werkzeug/llm-evidence.js");
const evidenceLogs = path.join(repo, "testsweet", "logs");
const evidenceContext = {
  module: { exports: {} },
  require(name) {
    if (name === "fs") return {
      existsSync(file) {
        return file === evidenceLogs || file === path.join(evidenceLogs, "llm-review-old-pass", "result.json");
      },
      readdirSync() { return ["llm-review-old-pass", "llm-review-new-interrupted"]; },
      statSync() { return { mtimeMs: 1 }; },
    };
    return require(name);
  },
};
vm.createContext(evidenceContext);
// Nur Kandidatenauswahl isolieren. Der Vorgänger-PASS wird als bereits korrekt
// validiert vorausgesetzt; die produktive Auswahlfunktion bleibt unverändert.
vm.runInContext(fs.readFileSync(evidenceFile, "utf8") + '\nverify=()=>({manifest:{selectedCaseIds:["P","N"],model:"glm-5.3",sources:[]},result:{state:"PASS",completedAt:"2026-10-13"}});', evidenceContext);
const selectedEvidence = JSON.parse(JSON.stringify(evidenceContext.module.exports.latestEvidence(repo)));
assert.equal(selectedEvidence.valid, true);
assert(selectedEvidence.datei.includes("llm-review-old-pass"));
tests.push({
  id: "new-interrupted-glm-review-hidden-by-old-pass",
  input: "Korrekt validierbarer alter PASS mit result.json; neuer gestarteter Review ohne result.json, etwa nach Prozessabbruch",
  actual: selectedEvidence,
  expectedForStrictCertification: "Neuer gestarteter, nicht abgeschlossener Review muss UNVOLLSTÄNDIG/ABGEBROCHEN bleiben.",
  scope: "Isoliert ausschließlich Kandidatenauswahl. verify() ist in diesem VM-Kontext als gültiger Vorgänger-PASS ersetzt. Für Vorschaupakete und gestartete Reviews fehlt ein persistenter Unterscheidungsmarker.",
});

const output = {
  method: "Produktiver Aggregations-/Auswahlcode in isolierter Node-VM mit virtuellem Dateisystem; keine echten Zertifikate oder Nachweise erzeugt/verändert.",
  repository: repo,
  generatedAt: new Date().toISOString(),
  allCounterexamplesReproduced: true,
  count: tests.length,
  tests,
};
const outputPath = path.join(__dirname, "certificate-counterexamples.json");
fs.writeFileSync(outputPath, JSON.stringify(output, null, 2) + "\n");
console.log(JSON.stringify({ outputPath, allCounterexamplesReproduced: true, count: tests.length, ids: tests.map(test => test.id) }, null, 2));
