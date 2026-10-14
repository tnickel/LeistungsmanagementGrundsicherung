// Gemeinsame, konservative Auswertung für Zertifikat und Aktualitätsprüfung.
const fs = require("fs");
const path = require("path");
const crypto = require("crypto");
const xml = require("../handbuch-werkzeug/node_modules/xml-js");
const hash = value => crypto.createHash("sha256").update(value).digest("hex");
const MATRIX = ["FACH:POSITIV", "FACH:NEGATIV", "FACH:LAST", "FACH:STRESS", "WEB:POSITIV", "WEB:NEGATIV", "WEB:LAST"];
const array = value => value === undefined ? [] : Array.isArray(value) ? value : [value];
function inside(repo, relative) {
  if (typeof relative !== "string" || !relative || path.isAbsolute(relative)) throw Error("Ungültiger Nachweispfad");
  const resolved = path.resolve(repo, relative);
  if (!resolved.startsWith(path.resolve(repo) + path.sep)) throw Error("Nachweispfad verlässt Projekt");
  const real = fs.realpathSync(resolved), base = fs.realpathSync(repo);
  if (!real.startsWith(base + path.sep) || !fs.statSync(real).isFile()) throw Error("Nachweis fehlt/verlässt Projekt");
  return fs.readFileSync(real);
}
function walk(root, accept = () => true) {
  if (!fs.existsSync(root)) return [];
  return fs.readdirSync(root, {withFileTypes:true}).flatMap(entry => {
    const file = path.join(root, entry.name);
    if (entry.isSymbolicLink()) throw Error("Symbolische Verknüpfung im Buildbestand");
    return entry.isDirectory() ? walk(file, accept) : entry.isFile() && accept(file) ? [file] : [];
  }).sort();
}
function buildValid(repo, evidence) {
  if (!evidence || evidence.schemaVersion !== 1 || !Array.isArray(evidence.sourceInventory) || !evidence.sourceInventory.length
      || !Array.isArray(evidence.artifacts) || !evidence.artifacts.length || evidence.sourceArtifactsMatch !== true) return false;
  try {
    const current = sources(repo);
    if (JSON.stringify(current) !== JSON.stringify(evidence.sourceInventory.map(file=>({path:file.path,sha256:file.sha256})))
        || digest(current) !== evidence.sourceSha256) return false;
    return evidence.artifacts.every(artifact => {
      if (!artifact.sourceMatch || !["product","testsweet"].includes(artifact.name) || !["jar","classes"].includes(artifact.kind)
          || !artifact.classesSha256 || typeof artifact.path !== "string") return false;
      const file = path.isAbsolute(artifact.path) ? artifact.path : path.resolve(repo,artifact.path);
      const actual = artifact.kind === "jar" ? hash(fs.readFileSync(file)) : classHash(file);
      return actual === artifact.sha256 && (artifact.kind !== "classes" || actual === artifact.classesSha256);
    });
  } catch (_) { return false; }
}
function digest(inventory) { return hash(inventory.map(file=>`${file.path}\0${file.sha256}\n`).join("")); }
function sources(repo) {
  const rules = inside(repo,"tools/evidence-sources.txt").toString("utf8").split(/\r?\n/), entries = new Map();
  for (const raw of rules) {
    const line = raw.trim(); if (!line || line.startsWith("#")) continue;
    const match = /^(dir|file|tool):(.+)$/.exec(line); if (!match) throw Error("Ungültige Quellregel");
    const root = path.resolve(repo,match[2]); if (!root.startsWith(path.resolve(repo)+path.sep)) throw Error("Quellregel verlässt Projekt");
    if (!fs.existsSync(root)) continue;
    const files = fs.statSync(root).isFile() ? [root] : walk(root);
    for (const file of files) {
      const relative = path.relative(repo,file).replace(/\\/g,"/"), name = path.basename(file);
      if (relative.includes("/__pycache__/") || relative.endsWith(".pyc")) continue;
      if (match[1] === "tool" && !/\.(js|py|bat)$/.test(name) && !["llm-pruefauftrag.txt","gesetzesgrundlagen-2026.md","llm-response-contract.json"].includes(name)) continue;
      entries.set(relative,{path:relative,sha256:hash(inside(repo,relative))});
    }
  }
  return [...entries.keys()].sort().map(key=>entries.get(key));
}
function classHash(directory) {
  return digest(walk(directory).map(file=>({path:path.relative(directory,file).replace(/\\/g,"/"),sha256:hash(fs.readFileSync(file))}))
    .filter(file=>file.path !== "META-INF/MANIFEST.MF" && !file.path.startsWith("META-INF/maven/") && !/^META-INF\/(?:product|testsweet)-build\.(?:json|properties)$/.test(file.path))
    .sort((a,b)=>a.path < b.path ? -1 : a.path > b.path ? 1 : 0));
}
function modeOf(value) {
  if (value === "FACH" || /^1|fach/i.test(String(value))) return "FACH";
  if (value === "WEB" || /^2|web/i.test(String(value))) return "WEB";
  return null;
}
function json(file) { return JSON.parse(fs.readFileSync(file, "utf8")); }
function logEvidenceFiles(repo) {
  const logs=path.join(repo,"testsweet/logs");
  if (!fs.existsSync(logs)) return [];
  const files=[];
  for (const entry of fs.readdirSync(logs,{withFileTypes:true})) {
    if (!entry.isDirectory() || entry.isSymbolicLink()) continue;
    const directory=path.join(logs,entry.name);
    const run=entry.name.startsWith("run-"),review=entry.name.startsWith("llm-review-");
    if (!run && !(review && (fs.existsSync(path.join(directory,"started.json")) || fs.existsSync(path.join(directory,"result.json"))))) continue;
    // Nur echte Laufbelege direkt in ihrem eigenen Verzeichnis. Chrome lädt in
    // browser-profile/** weitere manifest.json nach, ohne einen Test zu verändern.
    const names=run?["run-state.json","zusammenfassung.txt"]:["started.json","status.json","manifest.json","result.json"];
    for (const name of names) if (fs.existsSync(path.join(directory,name))) files.push(path.join(directory,name));
  }
  return files.sort();
}
function latestRuns(repo) {
  const logs = path.join(repo, "testsweet/logs"), selected = {}, unknown = [], grouped = {};
  const chronology = require("./run-chronology");
  if (!fs.existsSync(logs)) return {selected, unknown};
  for (const name of fs.readdirSync(logs).filter(name => name.startsWith("run-"))) {
    const directory = path.join(logs,name);
    if (!fs.statSync(directory).isDirectory()) continue;
    const summaryFile = path.join(directory,"zusammenfassung.txt");
    const statusFile = path.join(directory,"run-state.json");
    let lifecycle = null, txt = "", error = "";
    try { if (fs.existsSync(statusFile)) lifecycle = json(statusFile); } catch (e) { error = "Laufstatus unlesbar: " + e.message; }
    try { if (fs.existsSync(summaryFile)) txt = fs.readFileSync(summaryFile,"utf8"); } catch (e) { error = "Zusammenfassung unlesbar: " + e.message; }
    const mode = modeOf(lifecycle?.mode || /^Modus\s*:\s*(.+)$/m.exec(txt)?.[1]);
    const category = lifecycle?.category || /^Kategorie\s*:\s*(\S+)/m.exec(txt)?.[1];
    const startedAt = lifecycle?.startedAt || /^Zeitraum\s*:\s*(\S+)/m.exec(txt)?.[1] || name.slice(4);
    const start = chronology.start(directory,startedAt),time=start.earliest;
    const total = lifecycle?.totalCases ?? Number(/^Aufträge\s*:\s*(\d+)/m.exec(txt)?.[1]);
    const verified = lifecycle?.verifiedCases ?? Number(/^verifiziert\s*:\s*(\d+)/m.exec(txt)?.[1]);
    const failed = lifecycle?.failedCases ?? Number(/^fehlgeschlagen\s*:\s*(\d+)/m.exec(txt)?.[1]);
    const verificationErrors = lifecycle?.verificationErrors ?? Number(/^Verifik\.-fehler\s*:\s*(\d+)/m.exec(txt)?.[1]);
    const knownFailure = lifecycle?.state === "FAIL" || lifecycle?.state === "FEHLGESCHLAGEN" || failed > 0 || verificationErrors > 0;
    const terminal = ["PASS", "FAIL", "ERROR", "ABORTED", "COMPLETED", "FERTIG", "FEHLGESCHLAGEN", "ABGEBROCHEN"].includes(lifecycle?.state);
    let state = knownFailure ? "FAIL" : "UNVERIFIED";
    const reasons = [];
    if (!start.explicitOffset) reasons.push("Laufstart ohne eindeutigen Zeitzonenoffset (historischer Nachweis)");
    if (!lifecycle) reasons.push("Persistenter Lauf-/Buildnachweis fehlt (historischer Lauf)");
    if (lifecycle && (lifecycle.schemaVersion !== 1 || lifecycle.kind !== "SIMULATOR_RUN" || lifecycle.runId !== name)) reasons.push("Laufidentität oder Nachweisschema ist ungültig");
    if (!terminal) reasons.push("Neuester gestarteter Lauf ist nicht abgeschlossen");
    if (!Number.isInteger(total) || total <= 0 || verified !== total) reasons.push("Fallzahl unvollständig/ungültig");
    if (!buildValid(repo,lifecycle?.buildEvidence)) reasons.push("Getesteter Build ist nicht aktuell oder nicht vollständig gebunden");
    let reportsValid = false;
    try {
      const reports = lifecycle?.reports;
      reportsValid = Array.isArray(reports) && reports.length === 2 && new Set(reports.map(r=>r.path)).size === 2
        && reports.some(r=>r.path.endsWith(".pdf")) && reports.some(r=>r.path.endsWith(".md"))
        && reports.every(report=>hash(inside(directory,report.path))===report.sha256);
    } catch (_) { reportsValid=false; }
    if (!reportsValid) reasons.push("Neue PDF-/Markdown-Nachweise fehlen oder ihre Hashes stimmen nicht");
    if (lifecycle && (lifecycle.logComplete !== true || (lifecycle.evidenceErrors || []).length)) reasons.push("Protokollierung/Nachweiserstellung ist nicht vollständig");
    if (error) reasons.push(error);
    if (lifecycle && !knownFailure && terminal && ["PASS", "COMPLETED", "FERTIG"].includes(lifecycle.state)
        && lifecycle.success === true && total > 0 && verified === total && failed === 0 && verificationErrors === 0
        && buildValid(repo,lifecycle.buildEvidence) && lifecycle.reportsComplete === true && lifecycle.logComplete === true
        && reportsValid && !(lifecycle.evidenceErrors || []).length && !error && lifecycle.schemaVersion === 1
        && lifecycle.kind === "SIMULATOR_RUN" && lifecycle.runId === name && start.explicitOffset) state = "PASS";
    if (knownFailure) reasons.unshift("Fach-/SQL- oder Verifikationsfehler im aktuellen Lauf");
    if (lifecycle && ["ERROR","ABORTED","ABGEBROCHEN"].includes(lifecycle.state)) reasons.unshift("Technischer Fehler/Abbruch: " + (lifecycle.message || lifecycle.state));
    const entry = {state,mode,category,startedAt,time,gesamt:total,verifiziert:verified,erfolgreich:state === "PASS",
      ordner:`testsweet/logs/${name}`,zeit:String(startedAt).slice(0,10),reasons,lifecycle,chronology:start};
    if (!mode || !MATRIX.includes(`${mode}:${category}`)) { if (`${mode}:${category}` !== "WEB:STRESS") unknown.push(entry); continue; }
    const key = `${mode}:${category}`;
    (grouped[key] ||= []).push(entry);
  }
  for (const [key,entries] of Object.entries(grouped)) {
    const possible=chronology.candidates(entries).sort((a,b)=>(b.state === "FAIL")-(a.state === "FAIL") || b.time-a.time || b.ordner.localeCompare(a.ordner));
    selected[key]=possible[0];
    if (possible.length>1) {
      if (selected[key].state === "PASS") { selected[key].state="UNVERIFIED";selected[key].erfolgreich=false; }
      selected[key].reasons.push("Laufreihenfolge nicht eindeutig: "+possible.map(x=>x.ordner).join(", "));
    }
  }
  return {selected,unknown};
}
function parseSuite(raw) {
  if (/<!DOCTYPE|<!ENTITY/i.test(raw)) throw Error("DTD/Entities in Testbeleg nicht erlaubt");
  const suite = xml.xml2js(raw,{compact:true}).testsuite;
  if (!suite?._attributes) throw Error("JUnit testsuite fehlt");
  const a = suite._attributes, counts = {};
  for (const key of ["tests","failures","errors","skipped"]) {
    counts[key] = Number(a[key]);
    if (!Number.isInteger(counts[key]) || counts[key] < 0) throw Error("JUnit Zähler ungültig");
  }
  const cases = array(suite.testcase);
  const actual = {tests:cases.length,failures:cases.filter(c => c.failure !== undefined).length,
    errors:cases.filter(c => c.error !== undefined).length,skipped:cases.filter(c => c.skipped !== undefined).length};
  if (!a.name || Object.keys(counts).some(k => counts[k] !== actual[k])) throw Error("JUnit Zähler widersprechen Einzeltests");
  if (new Set(cases.map(c => `${c._attributes?.classname}:${c._attributes?.name}`)).size !== cases.length) throw Error("Doppelte JUnit Einzeltests");
  return {name:a.name,...counts,cases:cases.map(c=>{
    const failure=array(c.failure ?? c.error)[0];
    return {name:c._attributes.name,classname:c._attributes.classname || a.name,
      state:c.failure !== undefined ? "FAIL" : c.error !== undefined ? "ERROR" : c.skipped !== undefined ? "SKIPPED" : "PASS",
      message:failure ? String(failure._attributes?.message || failure._text || "").trim() : "",
      type:failure?._attributes?.type || ""};
  })};
}
function junit(repo, module) {
  const prefix = module === "main" ? "" : "testsweet/";
  const root = path.join(repo,prefix), markerFile = path.join(root,"target/test-evidence.json");
  const reportsDir = path.join(root,"target/surefire-reports");
  let marker = null; const reasons = [], byCase = new Map(), suiteNames = new Set(), totals = {tests:0,failures:0,errors:0,skipped:0};
  try { marker = json(markerFile); } catch (_) { reasons.push("Laufgebundener JUnit-Nachweis fehlt"); }
  const expectedMethods = new Map();
  const expected = walk(path.join(root,"src/test/java"),file => file.endsWith(".java")).flatMap(file => {
    const source = fs.readFileSync(file,"utf8").replace(/\/\*[\s\S]*?\*\//g,"").replace(/\/\/[^\r\n]*/g,"");
    if (!/^(?:Test.*|.*(?:Test|Tests|TestCase))$/.test(path.basename(file,".java"))) return [];
    const pkg = /^package\s+([\w.]+)\s*;/m.exec(source)?.[1];
    const name = pkg ? `${pkg}.${path.basename(file,".java")}` : path.basename(file,".java");
    expectedMethods.set(name,[...source.matchAll(/@(?:Test|ParameterizedTest|RepeatedTest|TestFactory|TestTemplate)\b[\s\S]*?\bvoid\s+(\w+)\s*\(/g)].map(match=>match[1]));
    return [name];
  });
  const history = path.join(root,"target/test-evidence-history");
  const markers = fs.existsSync(history) ? fs.readdirSync(history).filter(name=>name.endsWith(".json")).flatMap(name=>{
    try { return [json(path.join(history,name))]; } catch (_) { reasons.push("Unlesbarer JUnit-Laufnachweis: " + name);return []; }
  }) : [];
  if (marker && !markers.some(item=>item.runId === marker.runId)) markers.push(marker);
  markers.sort((a,b)=>Date.parse(a.startedAt)-Date.parse(b.startedAt));
  let parseFailure = false;const unfinished=[],currentSource=digest(sources(repo));
  const addSuite = (suite,time,completed=false,failuresOnly=false) => {
    suiteNames.add(suite.name);
    for (const item of suite.cases) {
      if (failuresOnly && !["FAIL","ERROR"].includes(item.state)) continue;
      const id = `${suite.name}:${item.classname}:${item.name}`, previous = byCase.get(id);
      // Ein späterer Skip beweist keinen Fix eines zuvor fehlgeschlagenen Tests.
      if (!previous || time >= previous.time && item.state !== "SKIPPED") byCase.set(id,{...item,suite:suite.name,time,completed});
    }
  };
  for (const run of markers) {
    if (run.schemaVersion !== 1 || run.kind !== "JUNIT_RUN" || run.module !== module) continue;
    const sameSource=(run.sourceSha256 || run.buildEvidence?.sourceSha256) === currentSource;
    if (run.state !== "COMPLETED" && sameSource) unfinished.push(run);
    if (!["COMPLETED","INTERRUPTED","UNVERIFIED"].includes(run.state) || !buildValid(repo,run.buildEvidence)) continue;
    if (!Array.isArray(run.expectedClasses) || expected.some(name=>!run.expectedClasses.includes(name)) || run.expectedClasses.some(name=>!expected.includes(name))) { reasons.push("JUnit-Testinventar des Laufs stimmt nicht mit Quellen überein");continue; }
    for (const report of run.reports || []) try {
      const raw = inside(repo,report.path);
      if (hash(raw) !== report.sha256) throw Error("JUnit-XML-Hash stimmt nicht");
      addSuite(parseSuite(raw.toString("utf8")),Date.parse(run.startedAt),run.state === "COMPLETED",run.state === "UNVERIFIED");
    } catch (e) { parseFailure=true;reasons.push(report.path + ": " + e.message); }
  }
  // Nach Surefire-Fehler erreicht Maven den FINISH-Hook nicht. Neu geschriebene
  // XML-Fehler dieses begonnenen Laufs müssen trotzdem das Urteil rot setzen.
  let liveFailure = false;
  if (!marker || marker.state !== "COMPLETED") for (const name of fs.existsSync(reportsDir) ? fs.readdirSync(reportsDir).filter(name=>/^TEST-.*\.xml$/.test(name)) : []) try {
    const file = path.join(reportsDir,name), raw = fs.readFileSync(file), prior = marker?.previousReports?.find(item=>item.name === name);
    const signature = Math.trunc(fs.statSync(file).mtimeMs) + ":" + hash(raw);
    if (prior?.signature === signature) continue;
    const suite = parseSuite(raw.toString("utf8"));
    if (suite.failures + suite.errors > 0) { liveFailure=true; addSuite(suite,Date.parse(marker?.startedAt || new Date().toISOString())); }
  } catch (e) { parseFailure=true;reasons.push(name + ": " + e.message); }
  const suites = [...suiteNames].sort().map(name=>{
    const cases = [...byCase.values()].filter(item=>item.suite === name);
    return {name,tests:cases.length,failures:cases.filter(c=>c.state === "FAIL").length,errors:cases.filter(c=>c.state === "ERROR").length,skipped:cases.filter(c=>c.state === "SKIPPED").length,cases};
  });
  for (const suite of suites) for (const key of Object.keys(totals)) totals[key] += suite[key];
  if (!expected.length || expected.some(name=>!suiteNames.has(name)) || [...suiteNames].some(name=>!expected.includes(name))) reasons.push("JUnit-Testklasseninventar ist unvollständig/unerwartet");
  for (const [suite,methods] of expectedMethods) for (const method of methods) {
    if (![...byCase.values()].some(item=>item.suite === suite && (item.name === method || item.name.startsWith(method+"(") || item.name.startsWith(method+"[")))) reasons.push(`JUnit-Testmethode fehlt: ${suite}.${method}`);
  }
  for (const run of unfinished) {
    const after=Date.parse(run.startedAt);
    const covered=expected.every(suite=>{
      const newer=[...byCase.values()].filter(item=>item.suite === suite && item.completed && item.time>after && item.state !== "SKIPPED");
      return newer.length && (expectedMethods.get(suite) || []).every(method=>newer.some(item=>item.name === method || item.name.startsWith(method+"(") || item.name.startsWith(method+"[")));
    });
    if (!covered) reasons.push(`Früherer unvollständiger JUnit-Lauf ${run.runId}: Pflichtumfang danach nicht vollständig erneut ausgeführt`);
  }
  if (!marker || marker.schemaVersion !== 1 || marker.kind !== "JUNIT_RUN" || marker.module !== module || marker.state !== "COMPLETED"
      || !marker.completedAt || !marker.startedAt || Date.parse(marker.completedAt) < Date.parse(marker.startedAt)) reasons.push("JUnit-Lauf ist nicht vollständig abgeschlossen");
  if (!buildValid(repo,marker?.buildEvidence)) reasons.push("JUnit-Buildbindung fehlt oder ist veraltet");
  if (totals.skipped) reasons.push(`${totals.skipped} Tests übersprungen; erforderlicher Umfang nicht vollständig geprüft`);
  const knownFailure = liveFailure || totals.failures + totals.errors > 0 || marker?.exitCode > 0;
  const state = knownFailure ? "FAIL" : !reasons.length && !parseFailure && totals.tests > 0 ? "PASS" : "UNVERIFIED";
  return {module,state,...totals,passed:totals.tests-totals.failures-totals.errors-totals.skipped,suites,reasons,marker};
}
// Die Ein-Klick-Prüfung beginnt vor Maven und darf deshalb nicht hinter noch
// vorhandenen Einzelbelegen verschwinden. UI-Fortschritt und Exportmetadaten sind
// kein Prüfbeleg; FINALIZING/SUCCESS teilen denselben stabilen Belegzustand.
function certificateSuites(repo) {
  const logs=path.join(repo,"testsweet/logs"), entries=[];
  if (!fs.existsSync(logs)) return {selected:null,inventory:[]};
  for (const name of fs.readdirSync(logs).filter(name=>name.startsWith("certificate-suite-"))) {
    const directory=path.join(logs,name);
    if (!fs.statSync(directory).isDirectory()) continue;
    const file=path.join(directory,"status.json");let status={},error="";
    try {
      status=json(file);
      if (!status || typeof status!=="object" || Array.isArray(status)) throw Error("Ungültiger Status");
    } catch (_) { status={};error="Suite-Status fehlt oder ist unlesbar"; }
    const chronology=require("./run-chronology").start(directory,status.startedAt);
    const ready=["FINALIZING","SUCCESS"].includes(status.state);
    const stable={schemaVersion:status.schemaVersion,kind:status.kind,jobId:status.jobId,startedAt:status.startedAt,
      sourceSha256:status.sourceSha256,state:ready?"EVIDENCE_READY":status.state,failureKind:ready?undefined:status.failureKind,
      runDirectories:status.runDirectories,glmReviewId:status.glmReviewId,evidenceIds:status.evidenceIds,error};
    entries.push({directory:name,path:`testsweet/logs/${name}/status.json`,status,stable,error,chronology,time:chronology.earliest});
  }
  const latest=require("./run-chronology").candidates(entries).sort((a,b)=>b.time-a.time || b.directory.localeCompare(a.directory));
  const selected=latest[0] || null;
  if (selected && latest.length>1) selected.error="Reihenfolge der Zertifikats-Suiten ist nicht eindeutig";
  return {selected,inventory:entries.map(entry=>({path:entry.path,normalization:"CERTIFICATE_SUITE_STABLE_V1",sha256:hash(JSON.stringify(entry.stable))})).sort((a,b)=>a.path.localeCompare(b.path))};
}
function suiteLayer(repo,suite,runs,automatic,llm,currentSource) {
  const {status,error,chronology,directory}=suite,reasons=[];
  if (error) reasons.push(error);
  if (status.schemaVersion!==1 || status.kind!=="CERTIFICATE_SUITE" || `certificate-suite-${status.jobId}`!==directory)
    reasons.push("Identität/Schema der Zertifikats-Suite ist ungültig");
  if (!chronology.explicitOffset) reasons.push("Suite-Start ist nicht eindeutig datiert");
  if (status.sourceSha256!==currentSource) reasons.push("Quellen wurden seit Start der Zertifikats-Suite geändert");
  const ready=["FINALIZING","SUCCESS"].includes(status.state);
  if (!ready) reasons.push(["FAILED","ERROR","CANCELLED","ABORTED"].includes(status.state)?"Zertifikats-Suite fehlgeschlagen oder abgebrochen":"Zertifikats-Suite ist noch nicht vollständig abgeschlossen");
  const fresh=value=>typeof value==="string" && /(?:Z|[+-]\d\d:\d\d)$/.test(value) && Number.isFinite(Date.parse(value)) && Date.parse(value)>=chronology.latest;
  for (const item of automatic) {
    if (!fresh(item.marker?.startedAt)) reasons.push(`JUnit ${item.module}: aktueller Lauf wurde nicht in dieser Suite gestartet`);
    if (!item.suites.length || item.suites.some(suite=>suite.cases.some(test=>!test.completed || test.time<chronology.latest)))
      reasons.push(`JUnit ${item.module}: nicht alle Einzeltests wurden in dieser Suite vollständig ausgeführt`);
  }
  for (const id of MATRIX) if (!fresh(runs.selected[id]?.startedAt)) reasons.push(`${id}: aktueller Pflichtlauf wurde nicht in dieser Suite gestartet`);
  let glmStart=null,glmId=null;
  try {
    if (llm?.datei) {
      const directory=path.dirname(llm.datei),started=JSON.parse(inside(repo,directory+"/started.json"));
      glmStart=started.startedAt;glmId=started.reviewId;
    }
  } catch (_) { /* Fehlende GLM-Bindung bleibt unvollständig. */ }
  if (!fresh(glmStart)) reasons.push("GLM-Prüfung wurde nicht in dieser Suite gestartet");
  if (ready) {
    const bound=Array.isArray(status.runDirectories)?status.runDirectories.filter(run=>run && typeof run==="object"):[];
    if (bound.length!==MATRIX.length || new Set(bound.map(run=>`${run.mode}:${run.category}`)).size!==MATRIX.length)
      reasons.push("Gebundene Pflichtlauf-Matrix der Zertifikats-Suite ist unvollständig");
    for (const id of MATRIX) {
      const run=bound.find(run=>`${run.mode}:${run.category}`===id);
      const relative=typeof run?.directory==="string"?path.relative(repo,path.resolve(repo,run.directory)).replace(/\\/g,"/"):null;
      if (relative!==runs.selected[id]?.ordner) reasons.push(`${id}: ausgewählter Lauf stimmt nicht mit der Suite-Matrix überein`);
    }
    if (!status.glmReviewId || status.glmReviewId!==glmId) reasons.push("Gebundene GLM-Prüfung der Zertifikats-Suite stimmt nicht überein");
  }
  if (status.evidenceIds) {
    for (const item of automatic) if (status.evidenceIds.junit?.[item.module]!==item.marker?.runId) reasons.push(`JUnit ${item.module}: Lauf gehört nicht zum gebundenen Suite-Nachweis`);
    for (const id of MATRIX) if (status.evidenceIds.runs?.[id]!==runs.selected[id]?.lifecycle?.runId) reasons.push(`${id}: Lauf gehört nicht zum gebundenen Suite-Nachweis`);
    if (status.evidenceIds.glmReviewId!==glmId) reasons.push("GLM-Prüfung gehört nicht zum gebundenen Suite-Nachweis");
  }
  // Nur bestätigte Testbefunde sind fachlich rot. Ein gescheiterter Prozess oder
  // fehlender Buildnachweis liefert einen unvollständigen technischen Nachweis.
  const knownFailure=status.state==="FAIL" || status.state==="FAILED" && status.failureKind==="TEST_FAILURE";
  return {id:"CERTIFICATE_SUITE",state:knownFailure?"FAIL":ready && !reasons.length?"PASS":"UNVERIFIED",reasons,evidence:status};
}
function collect(repo) {
  const runs = latestRuns(repo), automatic = [junit(repo,"main"),junit(repo,"testsweet")];
  const llm = require("./llm-evidence").latestEvidence(repo);
  const layers = automatic.map(item => ({id:`JUNIT:${item.module}`,state:item.state,reasons:item.reasons,evidence:item}));
  for (const id of MATRIX) layers.push({id,state:runs.selected[id]?.state || "UNVERIFIED",reasons:runs.selected[id]?.reasons || ["Pflichtlauf fehlt"],evidence:runs.selected[id] || null});
  const blockingUnknown = runs.unknown.filter(run=>MATRIX.some(id=>!runs.selected[id] || runs.selected[id].time <= run.time));
  if (blockingUnknown.length) layers.push({id:"UNZUGEORDNETE_LAEUFE",state:"UNVERIFIED",reasons:blockingUnknown.map(run => run.ordner + ": Laufmetadaten fehlen/ungültig; Pflichtmatrix danach vollständig erneut prüfen"),evidence:blockingUnknown});
  layers.push({id:"GLM",state:llm?.state === "FAIL" || llm?.knownFindings?.length ? "FAIL" : llm?.valid ? "PASS" : "UNVERIFIED",reasons:llm?.reason ? [llm.reason] : llm ? [] : ["Echte GLM-Prüfung fehlt"],evidence:llm});
  const captures=[...automatic.map(item=>item.marker?.buildEvidence),...MATRIX.map(id=>runs.selected[id]?.lifecycle?.buildEvidence),llm?.buildEvidence];
  const artifactIds=new Map(),buildReasons=[];
  for (const [index,capture] of captures.entries()) {
    if (!buildValid(repo,capture) || !capture.artifacts.some(a=>a.name === "product") || index>0 && !capture.artifacts.some(a=>a.name === "testsweet")) {
      buildReasons.push("Pflichtbeleg ohne vollständigen aktuellen Produkt-/Prüferbuild");continue;
    }
    for (const artifact of capture.artifacts) {
      if (!artifactIds.has(artifact.name)) artifactIds.set(artifact.name,new Set());
      artifactIds.get(artifact.name).add(artifact.classesSha256);
    }
  }
  for (const [name,ids] of artifactIds) if (ids.size>1) buildReasons.push(`Unterschiedliche getestete Klassenbestände für ${name}: ${[...ids].join(" / ")}`);
  layers.push({id:"BUILD",state:buildReasons.length?"UNVERIFIED":"PASS",reasons:[...new Set(buildReasons)],evidence:{artifacts:[...artifactIds].map(([name,ids])=>({name,classesSha256:[...ids]}))}});
  const suites=certificateSuites(repo),sourceInventory=sources(repo);
  if (suites.selected) layers.push(suiteLayer(repo,suites.selected,runs,automatic,llm,digest(sourceInventory)));
  const verdict = layers.some(layer=>layer.state === "FAIL") ? "NICHT BESTANDEN" : layers.every(layer=>layer.state === "PASS") ? "BESTANDEN" : "UNVOLLSTÄNDIG";
  const inventory = [];
  for (const file of ["pom.xml","testsweet/pom.xml",...walk(path.join(repo,"src")),...walk(path.join(repo,"testsweet/src")),
    ...logEvidenceFiles(repo),
    ...walk(path.join(repo,"target/surefire-reports"),file=>file.endsWith(".xml")),...walk(path.join(repo,"testsweet/target/surefire-reports"),file=>file.endsWith(".xml")),
    ...walk(path.join(repo,"target/test-evidence-history")),...walk(path.join(repo,"testsweet/target/test-evidence-history")),
    "target/test-evidence.json","testsweet/target/test-evidence.json"]) {
    const absolute = path.isAbsolute(file) ? file : path.join(repo,file);
    if (fs.existsSync(absolute)) inventory.push({path:path.relative(repo,absolute).replace(/\\/g,"/"),sha256:hash(fs.readFileSync(absolute))});
  }
  inventory.sort((a,b)=>a.path.localeCompare(b.path));
  const fingerprint = hash(JSON.stringify({inventory,sourceInventory,suiteEvidence:suites.inventory,verdict,states:layers.map(layer=>({id:layer.id,state:layer.state,reasons:layer.reasons}))}));
  return {schemaVersion:2,verdict,layers,laeufe:runs.selected,llm,automatic,notApplicable:["WEB:STRESS"],
    warnungen:layers.filter(layer=>layer.state !== "PASS").flatMap(layer=>[`${layer.id}: ${layer.state}`,...layer.reasons]),
    evidenceFingerprint:fingerprint,evidenceFiles:inventory,sourceInventory,suiteEvidence:suites.inventory};
}
if (require.main === module) {
  try { console.log(JSON.stringify(collect(path.resolve(process.argv[2] || path.join(__dirname,"../.."))),null,2)); }
  catch (error) { console.error(error.stack); process.exitCode = 1; }
}
module.exports = {collect,latestRuns,junit,parseSuite,buildValid,hash,inside,walk,sources,digest,classHash,logEvidenceFiles,certificateSuites,suiteLayer,MATRIX};
