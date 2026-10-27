// Lesbare Kennzahlen aus genau einem archivierten Prüflauf je Modul.
// Historische Zähler ändern niemals das strenge aktuelle Gesamturteil.
const evidence = require("../testzertifikat-werkzeug/certificate-evidence");

function recordedJUnit(repo, item) {
  const marker = item?.marker;
  if (!marker || marker.schemaVersion !== 1 || marker.kind !== "JUNIT_RUN"
      || marker.state !== "COMPLETED" || !Array.isArray(marker.reports)
      || !marker.reports.length || !Array.isArray(marker.expectedClasses) || !marker.expectedClasses.length
      || marker.expectedClasses.some(name=>typeof name!=="string" || !name.trim())
      || new Set(marker.expectedClasses).size!==marker.expectedClasses.length) return null;
  try {
    const totals = {tests:0,failures:0,errors:0,skipped:0};
    const names = new Set();
    for (const report of marker.reports) {
      const bytes = evidence.inside(repo, report.path);
      if (evidence.hash(bytes) !== report.sha256) return null;
      const suite = evidence.parseSuite(bytes.toString("utf8"));
      if (names.has(suite.name) || !marker.expectedClasses.includes(suite.name)) return null;
      names.add(suite.name);
      for (const key of Object.keys(totals)) totals[key] += suite[key];
    }
    if (names.size !== new Set(marker.expectedClasses).size) return null;
    return {...totals, executed:totals.tests-totals.skipped,
      passed:totals.tests-totals.skipped-totals.failures-totals.errors,
      date:marker.startedAt, runId:marker.runId, current:item.state === "PASS",
      sourceSha256:marker.sourceSha256 || marker.buildEvidence?.sourceSha256};
  } catch (_) { return null; }
}

function reportMetrics(repo, stand) {
  const modules = Object.entries(stand.junit || {}).map(([name,item]) => ({name,
    recorded:recordedJUnit(repo,item), state:item.state}));
  const totals = {tests:0,executed:0,passed:0,failures:0,errors:0,skipped:0};
  for (const {recorded} of modules) if (recorded)
    for (const key of Object.keys(totals)) totals[key] += recorded[key];
  return {modules, totals, complete:modules.length === 2 && modules.every(x=>x.recorded),
    currentLayers:stand.layers.filter(x=>x.state === "PASS").length,
    requiredLayers:stand.layers.length,
    placeholders:{plannedTests:String(totals.tests),executedTests:String(totals.executed),
      skippedTests:String(totals.skipped),failedTests:String(totals.failures+totals.errors)}};
}
module.exports = {recordedJUnit,reportMetrics};
