// Konservative Management-Auswertung: Status und Herkunft bleiben getrennt.
const fs = require("fs");
const path = require("path");
const crypto = require("crypto");
const evidence = require("../testzertifikat-werkzeug/certificate-evidence");
const chronology = require("../testzertifikat-werkzeug/run-chronology");
const MATRIX = ["Fachschicht|POSITIV", "Fachschicht|NEGATIV", "Fachschicht|ECHT", "Fachschicht|RECHNER",
  "Fachschicht|LAST", "Fachschicht|STRESS", "WEB|POSITIV", "WEB|NEGATIV", "WEB|LAST"];
const json = file => JSON.parse(fs.readFileSync(file, "utf8"));
const hash = value => crypto.createHash("sha256").update(value).digest("hex");
function mode(value) {
  if (/^(?:FACHSCHICHT|FACH|1)$/i.test(String(value))) return "Fachschicht";
  if (/^(?:WEB|2)$/i.test(String(value))) return "WEB";
  return "?";
}
function buildChecker(repo) {
  const cache = new Map();
  return build => {
    if (!build) return false;
    const key = hash(JSON.stringify(build));
    if (!cache.has(key)) cache.set(key, evidence.buildValid(repo, build));
    return cache.get(key);
  };
}
/** Fehler bleiben auch bei unvollständiger/veralteter Evidenz sichtbar. */
function alleLaeufe(repo, options = {}) {
  const logs = path.join(repo, "testsweet/logs"), rows = [];
  if (!fs.existsSync(logs)) return rows;
  const buildMatches = options.buildMatches || buildChecker(repo);
  for (const item of fs.readdirSync(logs, {withFileTypes:true}).filter(x => x.name.startsWith("run-")).sort((a,b)=>a.name.localeCompare(b.name))) {
    if (!item.isDirectory() || item.isSymbolicLink()) continue;
    const directory = path.join(logs, item.name), reasons = [];
    let lifecycle = null, txt = "";
    try { lifecycle = json(path.join(directory, "run-state.json")); } catch (_) { reasons.push("Laufstatus fehlt oder ist unlesbar"); }
    try { txt = fs.readFileSync(path.join(directory, "zusammenfassung.txt"), "utf8"); } catch (_) { /* Status bleibt maßgeblich. */ }
    const modus = mode(lifecycle?.mode || /^Modus\s*:\s*(.+)$/m.exec(txt)?.[1]?.trim());
    const kategorie = lifecycle?.category || /^Kategorie\s*:\s*(\S+)/m.exec(txt)?.[1] || "?";
    const startedAt = lifecycle?.startedAt || /^Zeitraum\s*:\s*(\S+)/m.exec(txt)?.[1] || item.name.slice(4);
    const start = chronology.start(directory, startedAt);
    const gesamt = lifecycle?.totalCases ?? Number(/^Aufträge\s*:\s*(\d+)/m.exec(txt)?.[1]);
    const verifiziert = lifecycle?.verifiedCases ?? Number(/^verifiziert\s*:\s*(\d+)/m.exec(txt)?.[1]);
    const fehlgeschlagen = lifecycle?.failedCases ?? Number(/^fehlgeschlagen\s*:\s*(\d+)/m.exec(txt)?.[1]);
    const verificationErrors = lifecycle?.verificationErrors ?? Number(/^Verifik\.-fehler\s*:\s*(\d+)/m.exec(txt)?.[1]);
    const knownFailure = ["FAIL","FEHLGESCHLAGEN"].includes(lifecycle?.state) || fehlgeschlagen > 0 || verificationErrors > 0;
    const terminal = ["PASS","FAIL","ERROR","ABORTED","COMPLETED","FERTIG","FEHLGESCHLAGEN","ABGEBROCHEN"].includes(lifecycle?.state);
    if (!start.explicitOffset) reasons.push("Laufstart ohne eindeutigen Offset");
    if (lifecycle?.schemaVersion !== 1 || lifecycle?.kind !== "SIMULATOR_RUN" || lifecycle?.runId !== item.name) reasons.push("Laufidentität/Nachweisschema fehlt oder ist ungültig");
    if (!terminal) reasons.push("Lauf ist nicht abgeschlossen");
    if (![gesamt,verifiziert,fehlgeschlagen,verificationErrors].every(Number.isInteger) || gesamt <= 0 || verifiziert !== gesamt || fehlgeschlagen < 0 || verificationErrors < 0) reasons.push("Fallzahlen sind unvollständig/ungültig");
    if (!buildMatches(lifecycle?.buildEvidence)) reasons.push("Getesteter Build ist nicht aktuell gebunden");
    let reportsValid = false;
    try {
      const reports = lifecycle?.reports;
      reportsValid = Array.isArray(reports) && reports.length === 2 && new Set(reports.map(x=>x.path)).size === 2
        && reports.some(x=>x.path.endsWith(".pdf")) && reports.some(x=>x.path.endsWith(".md"))
        && reports.every(x=>hash(evidence.inside(directory,x.path))===x.sha256);
    } catch (_) { /* Unvollständiger Beleg wird nicht grün. */ }
    if (!reportsValid || lifecycle?.reportsComplete !== true) reasons.push("PDF-/Markdown-Nachweise fehlen oder Hash stimmt nicht");
    if (lifecycle?.logComplete !== true || !Array.isArray(lifecycle?.evidenceErrors) || lifecycle.evidenceErrors.length) reasons.push("Protokollierung/Nachweiserstellung unvollständig");
    let state = knownFailure ? "FAIL" : ["ABORTED","ABGEBROCHEN","ERROR"].includes(lifecycle?.state) ? "ABORTED" : !terminal ? "RUNNING" : "UNVERIFIED";
    if (!knownFailure && ["PASS","COMPLETED","FERTIG"].includes(lifecycle?.state) && lifecycle.success === true
        && fehlgeschlagen === 0 && verificationErrors === 0 && !reasons.length) state = "PASS";
    if (knownFailure) reasons.unshift("Bekannter Fach-/SQL- oder Verifikationsfehler");
    rows.push({modus,kategorie,startedAt,datum:String(startedAt).slice(0,10),gesamt,verifiziert,fehlgeschlagen,
      state,erfolgreich:state === "PASS",reasons,ordner:"testsweet/logs/"+item.name,chronology:start,lifecycle});
  }
  return rows;
}
/** Mehrdeutige Zeiten werden konservativ behandelt; ein bekannter Fehler gewinnt. */
function letzteLaeufe(repo, options = {}) {
  const grouped = new Map();
  for (const row of alleLaeufe(repo,options)) {
    const key = row.modus+"|"+row.kategorie;
    if (!grouped.has(key)) grouped.set(key,[]);
    grouped.get(key).push(row);
  }
  const result = [];
  for (const [key,rows] of grouped) {
    const possible = chronology.candidates(rows).sort((a,b)=>(b.state === "FAIL")-(a.state === "FAIL") || b.chronology.earliest-a.chronology.earliest || b.ordner.localeCompare(a.ordner));
    const row = possible[0];
    if (possible.length > 1) {
      row.reasons.push("Laufreihenfolge nicht eindeutig: "+possible.map(x=>x.ordner).join(", "));
      if (row.state === "PASS") { row.state="UNVERIFIED";row.erfolgreich=false; }
    }
    result.push({key,lauf:row});
  }
  return result.sort((a,b)=>{
    const ai=MATRIX.indexOf(a.key),bi=MATRIX.indexOf(b.key);
    return (ai<0?MATRIX.length:ai)-(bi<0?MATRIX.length:bi) || a.key.localeCompare(b.key);
  });
}
function deutschDatum(iso) {
  const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(String(iso || ""));
  return m ? `${m[3]}.${m[2]}.${m[1]}` : String(iso || "—");
}
/** Dieselbe laufgebundene JUnit-Prüfung wie beim formalen Zertifikat. */
function junitStand(repo) {
  return Object.fromEntries([["Hauptprojekt","main"],["testsweet","testsweet"]].map(([name,module])=>{
    const result=evidence.junit(repo,module);
    return [name,{...result,datum:deutschDatum(result.marker?.startedAt),fehler:result.failures+result.errors}];
  }));
}
function bewertung(layers) {
  return layers.some(x=>x.state === "FAIL") ? "NICHT BESTANDEN" : layers.length > 0 && layers.every(x=>x.state === "PASS") ? "BESTANDEN" : "UNVOLLSTÄNDIG";
}
function reportStand(repo) {
  const letzte=letzteLaeufe(repo),junit=junitStand(repo),llm=require("../testzertifikat-werkzeug/llm-evidence").latestEvidence(repo);
  const layers=MATRIX.map(id=>{
    const entry=letzte.find(x=>x.key===id)?.lauf;
    return {id,state:entry?.state || "UNVERIFIED",reasons:entry?.reasons || ["Pflichtlauf fehlt"],evidence:entry || null};
  });
  for (const [name,item] of Object.entries(junit)) layers.push({id:"JUnit: "+name,state:item.state,reasons:item.reasons,evidence:item});
  layers.push({id:"GLM: Positiv-/Negativstichprobe",state:llm?.state === "FAIL" || llm?.knownFindings?.length ? "FAIL" : llm?.valid ? "PASS" : "UNVERIFIED",
    reasons:llm?.reason ? [llm.reason] : llm?.valid ? [] : ["Aktueller vollständiger GLM-Nachweis fehlt"],evidence:llm});
  const unknown=letzte.filter(x=>!MATRIX.includes(x.key));
  const blockingUnknown=unknown.filter(x=>MATRIX.some(id=>{
    const selected=letzte.find(item=>item.key===id)?.lauf;
    return !selected || x.lauf.chronology.latest>=selected.chronology.earliest;
  }));
  if (blockingUnknown.length) layers.push({id:"Unzugeordnete Läufe",state:blockingUnknown.some(x=>x.lauf.state==="FAIL")?"FAIL":"UNVERIFIED",reasons:blockingUnknown.map(x=>x.lauf.ordner+": Kategorie/Modus unklar; danach vollständige Pflichtmatrix neu prüfen"),evidence:null});
  return {letzte,junit,llm,layers,unknown,verdict:bewertung(layers)};
}
/** Vollständige Inventur der Eingaben; Ausgaben/Exportmarker gehören nicht dazu. */
function inputInventory(repo) {
  const entries=new Map(),artifacts=new Map(),add=file=>{
    const relative=path.relative(repo,file).replace(/\\/g,"/");
    const raw=evidence.inside(repo,relative);
    entries.set(relative,{path:relative,sha256:hash(raw)});
    if (file.endsWith(".json")) try {
      for (const artifact of JSON.parse(raw).buildEvidence?.artifacts || []) {
        if (typeof artifact.path === "string" && ["jar","classes"].includes(artifact.kind)) {
          const absolute=path.resolve(repo,artifact.path);
          artifacts.set(absolute,{...artifact,path:absolute});
        }
      }
    } catch (_) { /* Unlesbare Belege sind trotzdem bytegenau gebunden. */ }
  };
  for (const item of evidence.sources(repo)) entries.set(item.path,item);
  const folders=["src/main","src/test","testsweet/src/main","testsweet/src/test","target/classes","testsweet/target/classes",
    "target/surefire-reports","testsweet/target/surefire-reports","target/test-evidence-history","testsweet/target/test-evidence-history",
    "doc/belege","doc/testreport-werkzeug","doc/testzertifikat-werkzeug"];
  for (const folder of folders) for (const file of evidence.walk(path.join(repo,folder))) {
    const relative=path.relative(repo,file).replace(/\\/g,"/");
    if (/(^|\/)node_modules\//.test(relative) || /(?:^|\/)(?:stand(?:-report)?|current-certificate)\.json$/.test(relative)
        || /(?:^|\/)publish\.lock(?:\.recovery)?$/.test(relative)
        || /\.(?:pdf|docx|tmp|lock|log)$/.test(relative) && !relative.startsWith("doc/belege/")) continue;
    add(file);
  }
  for (const relative of ["target/test-evidence.json","testsweet/target/test-evidence.json","doc/testdaten-echte-faelle-recherche-2026-10-27.md"]) {
    if (fs.existsSync(path.join(repo,relative))) add(path.join(repo,relative));
  }
  const logs=path.join(repo,"testsweet/logs");
  if (fs.existsSync(logs)) for (const entry of fs.readdirSync(logs,{withFileTypes:true})) {
    if (!entry.isDirectory() || entry.isSymbolicLink()) continue;
    const directory=path.join(logs,entry.name);
    if (entry.name.startsWith("run-")) {
      for (const name of ["run-state.json","zusammenfassung.txt","testdokumentation.md","testdokumentation.pdf"]) if(fs.existsSync(path.join(directory,name))) add(path.join(directory,name));
    } else if (entry.name.startsWith("llm-review-")) {
      for (const file of evidence.walk(directory)) add(file);
    } else if (entry.name.startsWith("certificate-suite-") && fs.existsSync(path.join(directory,"status.json"))) add(path.join(directory,"status.json"));
  }
  // Auch außerhalb target liegende, tatsächlich getestete Maven-JARs binden.
  for (const artifact of artifacts.values()) {
    let signature="MISSING";
    try { signature=artifact.kind === "classes" ? evidence.classHash(artifact.path) : hash(fs.readFileSync(artifact.path)); } catch (_) { /* Fehlender Build bleibt erkennbar. */ }
    const key="@artifact:"+artifact.name+":"+hash(artifact.path);
    entries.set(key,{path:key,sha256:signature});
  }
  return [...entries.values()].sort((a,b)=>a.path.localeCompare(b.path));
}
function inputFingerprint(repo) { return hash(JSON.stringify(inputInventory(repo))); }
module.exports={MATRIX,alleLaeufe,letzteLaeufe,junitStand,deutschDatum,bewertung,reportStand,inputInventory,inputFingerprint};
