// Offline-Prüfung echter API-Belege. Ein Preview ohne started.json ist kein Lauf.
const fs=require("fs"),path=require("path"),crypto=require("crypto");
const {cents,batchRequirements,coverage}=require("./llm-reference");
const hash=data=>crypto.createHash("sha256").update(data).digest("hex");
const named=(name,text)=>`\n=== BEGIN DOCUMENT ${name} ===\n${text}\n=== END DOCUMENT ${name} ===\n`;
const canonical=value=>JSON.stringify(Array.isArray(value)?value.map(v=>JSON.parse(canonical(v))):value && typeof value === "object"?Object.fromEntries(Object.keys(value).sort().map(key=>[key,JSON.parse(canonical(value[key]))])):value);
function readInside(base,name) {
  if (typeof name !== "string" || !name || path.isAbsolute(name)) throw Error("Ungültiger Belegpfad");
  const file=path.resolve(base,name),root=path.resolve(base);
  if (!file.startsWith(root+path.sep) || !fs.statSync(file).isFile() || !fs.realpathSync(file).startsWith(fs.realpathSync(base)+path.sep)) throw Error("Beleg fehlt/verlässt Prüfverzeichnis");
  return fs.readFileSync(file);
}
function sameIds(actual,expected) { return Array.isArray(actual) && Array.isArray(expected) && actual.length===expected.length
  && new Set(actual).size===actual.length && new Set(expected).size===expected.length && actual.every(x=>typeof x === "string" && expected.includes(x)); }
// Wortgetreue Spiegelung von LlmReviewService.korrekturAnweisung (Java): Der
// Korrekturzusatz eines Wiederholungssendens muss exakt rekonstruierbar sein.
function correctionInstruction(fehler,requirements) {
  let text="\n\n=== KORREKTURANWEISUNG ===\n"
    +"Deine letzte Antwort wurde formal verworfen und wird nicht gewertet.\nFehler: "
    +fehler+".\n"
    +"Das checks-Array muss je Fall EXAKT die genannten Kennungen enthalten — jede mit "
    +"eigenem verdict und eigener reasoning, keine fehlende, keine doppelte, keine zusätzliche:\n";
  for (const r of requirements) text+="- "+r.caseId+": "+r.checkIds.join(", ")+"\n";
  text+="Berechne alle Werte erneut selbstständig und gib ausschließlich das vollständige "
    +"korrigierte JSON-Objekt nach Schema 2 aus.";
  return text;
}
function answer(content,expected,requirements) {
  const value=JSON.parse(content);
  if (value.schemaVersion!==2 || !sameIds(value.caseIds,expected) || !Array.isArray(value.cases)
      || !sameIds(value.cases.map(c=>c.caseId),expected) || !Array.isArray(requirements)) throw Error("Unvollständige strukturierte Fallabdeckung (Schema 2 erforderlich)");
  const validVerdict=value=>["PASS","FAIL","UNVERIFIED"].includes(value);
  const nonblank=value=>typeof value === "string" && !!value.trim();
  for (const c of value.cases) {
    if (!validVerdict(c.verdict) || !Array.isArray(c.findings) || !nonblank(c.reasoning) || c.findings.some(f=>!nonblank(f))
        || c.verdict !== "PASS" && c.findings.length===0) throw Error("Ungültige Fallbewertung");
    const req=requirements.find(r=>r.caseId===c.caseId);
    if (!req || !Array.isArray(c.calculations) || !Array.isArray(c.checks)
        || !sameIds(c.calculations.map(b=>b.blockId),req.calculations.map(b=>b.blockId))
        || !sameIds(c.checks.map(check=>check.checkId),req.checkIds)) throw Error("Rechenblock-/Workflowabdeckung fehlt");
    const fields=(actual,expected)=>{
      if (!actual || !sameIds(Object.keys(actual),Object.keys(expected))) throw Error("Numerische Zwischenwerte fehlen/unerwartet");
      for (const value of Object.values(actual)) cents(value);
    };
    for (const block of c.calculations) {
      const expectedBlock=req.calculations.find(b=>b.blockId===block.blockId);
      if (!Array.isArray(block.persons) || !sameIds(block.persons.map(p=>p.personId),expectedBlock.persons.map(p=>p.personId))) throw Error("Personenabdeckung fehlt");
      for (const person of block.persons) fields(person.values,expectedBlock.persons.find(p=>p.personId===person.personId).values);
      fields(block.household,expectedBlock.household);
    }
    for (const check of c.checks) if (!validVerdict(check.verdict) || !nonblank(check.reasoning)) throw Error("Ungültige Einzelprüfung");
  }
  return value.cases;
}
function referenceChecks(cases,requirements) {
  return cases.map(c=>{
    const req=requirements.find(r=>r.caseId===c.caseId),arithmeticIssues=[];
    const compare=(actual,expected,prefix)=>{for (const [key,value] of Object.entries(expected)) if (cents(actual[key])!==cents(value)) arithmeticIssues.push(`${prefix}.${key}: Modell ${actual[key]}, Referenz ${value}`);};
    for (const block of c.calculations) {
      const expected=req.calculations.find(b=>b.blockId===block.blockId);
      for (const person of block.persons) compare(person.values,expected.persons.find(p=>p.personId===person.personId).values,`${block.blockId}.${person.personId}`);
      compare(block.household,expected.household,block.blockId);
    }
    const workflowFindings=[],workflowUnverified=[];
    for (const check of c.checks) if (check.verdict === "FAIL") workflowFindings.push(`${check.checkId}: ${check.reasoning}`);
    else if (check.verdict === "UNVERIFIED") workflowUnverified.push(`${check.checkId}: ${check.reasoning}`);
    if (c.verdict === "PASS" && c.findings.length) workflowFindings.push("PASS widerspricht nichtleerer Befundliste; Befunde bleiben als Fehler erhalten");
    return {caseId:c.caseId,state:req.sourceFindings.length || workflowFindings.length?"FAIL":arithmeticIssues.length || req.missingInputs.length || workflowUnverified.length?"UNVERIFIED":"PASS",
      findings:req.sourceFindings,missingInputs:req.missingInputs,arithmeticIssues,workflowFindings,workflowUnverified};
  });
}
// Beobachtungen ungültiger Antworten sind Warnsignale, keine bestätigten Produktfehler.
function provisionalFailures(content,expected,batch,attempt,requestFile,responseFile) {
  let parsed;try { parsed=JSON.parse(content); } catch (_) { return []; }
  if (!parsed || typeof parsed !== "object" || !Array.isArray(parsed.cases)) return [];
  const observations=[],nonblank=value=>typeof value === "string" && !!value.trim();
  for (const c of parsed.cases) {
    if (!c || typeof c !== "object" || typeof c.caseId !== "string" || !expected.includes(c.caseId)) continue;
    const signals=[];
    if (c.verdict === "FAIL") signals.push("verdict=FAIL");
    if (Array.isArray(c.findings)) for (const finding of c.findings) if (nonblank(finding)) signals.push("Befund: " + finding);
    if (Array.isArray(c.checks)) for (const check of c.checks) if (check && typeof check === "object" && check.verdict === "FAIL") {
      signals.push("Prüfpunkt " + (typeof check.checkId === "string" ? check.checkId : "?") + " meldet FAIL: " + (nonblank(check.reasoning) ? check.reasoning : "Begründung fehlt"));
    }
    if (!signals.length) continue;
    if (nonblank(c.reasoning)) signals.push("Begründung: " + c.reasoning);
    observations.push({caseId:c.caseId,source:"UNVALIDATED_MODEL_RESPONSE",batch,attempt,requestFile,responseFile,reason:signals.join(" | ")});
  }
  return observations;
}
function newestSource(repo,category,buildMatches) {
  const logs=path.join(repo,"testsweet/logs");
  const chronology=require("./run-chronology");
  const candidates=fs.readdirSync(logs).filter(x=>x.startsWith("run-") && fs.statSync(path.join(logs,x)).isDirectory()).map(run=>{
    const directory=path.join(logs,run),lifecycle=path.join(directory,"run-state.json");
    let state=null;if (fs.existsSync(lifecycle)) state=JSON.parse(fs.readFileSync(lifecycle,"utf8"));
    const match=/^run-(\d{4})(\d{2})(\d{2})-(\d{2})(\d{2})(\d{2})/.exec(run);
    const fallback=match?`${match[1]}-${match[2]}-${match[3]}T${match[4]}:${match[5]}:${match[6]}`:"";
    const start=chronology.start(directory,state?.startedAt || fallback);
    return {run,directory,state,time:start.earliest,chronology:start};
  }).sort((a,b)=>b.time-a.time || b.run.localeCompare(a.run));
  for (const {run,directory,state} of candidates) {
    if (state) {
      if (state.category===category && (state.state!=="PASS" || state.success!==true || state.reportsComplete!==true)) throw Error("Neuester Quelllauf ist fehlgeschlagen/unvollständig");
    }
    const relative=`testsweet/logs/${run}/testdokumentation.md`;
    let text;try{text=readInside(repo,relative).toString("utf8");}catch(_){continue;}
    if (new RegExp(`^kategorie = ${category}\\r?$`,"m").test(text)) {
      const current=candidates.find(x=>x.run===run);
      const possible=candidates.filter(other=>other.chronology.latest>=current.chronology.earliest && (other.state?.category===category
        || fs.existsSync(path.join(other.directory,"testdokumentation.md")) && new RegExp(`^kategorie = ${category}\\r?$`,"m").test(fs.readFileSync(path.join(other.directory,"testdokumentation.md"),"utf8"))));
      if (!current.chronology.explicitOffset || possible.length!==1) throw Error("Quelllauf-Zeitstempel oder Reihenfolge ist nicht eindeutig");
      if (!state || !/^automatik_bestanden = true\r?$/m.test(text) || !buildMatches(state.buildEvidence)
          || !Array.isArray(state.reports) || !state.reports.some(report=>report.path === "testdokumentation.md" && report.sha256 === hash(Buffer.from(text,"utf8")))) throw Error("Neuester Quellbericht ist nicht bestanden oder nicht an den aktuellen Build/Report gebunden");
      return relative;
    }
  }
  throw Error(`Keine vollständigen ${category}-Quelldaten`);
}
function verify(directory,repo,options={}) {
  const rawManifest=readInside(directory,"manifest.json"),manifest=JSON.parse(rawManifest);
  const terminal=fs.existsSync(path.join(directory,"result.json"));
  const result=JSON.parse(readInside(directory,terminal?"result.json":"status.json"));
  const started=JSON.parse(readInside(directory,"started.json"));
  if (manifest.schemaVersion!==2 || result.schemaVersion!==2 || manifest.kind!=="LLM_REVIEW" || result.kind!=="LLM_REVIEW"
      || manifest.reviewId!==result.reviewId || manifest.reviewId!==started.reviewId || hash(rawManifest)!==result.manifestSha256
      || result.model!==manifest.model || !Array.isArray(manifest.selectedCaseIds) || manifest.selectedCaseIds.length<2
      || new Set(manifest.selectedCaseIds).size!==manifest.selectedCaseIds.length) throw Error("Manifest-/Startbindung ungültig (historische Schema-1-Belege sind keine aktuelle Freigabe)");
  const buildMatches=options.buildMatches || (evidence=>require("./certificate-evidence").buildValid(repo,evidence));
  if (!buildMatches(manifest.buildEvidence)) throw Error("Getesteter Software-/Buildstand ist veraltet oder nicht gebunden");
  const contractRaw=readInside(directory,"llm-response-contract.json"),contract=JSON.parse(contractRaw);
  if (hash(contractRaw)!==manifest.responseContractSha256 || hash(readInside(repo,"doc/testzertifikat-werkzeug/llm-response-contract.json"))!==manifest.responseContractSha256) throw Error("Antwortvertrag wurde geändert");
  if (!Array.isArray(manifest.sources) || manifest.sources.length!==2 || !sameIds(manifest.sources.map(s=>s.category),["POSITIV","NEGATIV"])) throw Error("Positiv-/Negativquellen fehlen");
  for (const source of manifest.sources) if (hash(readInside(repo,source.path))!==source.sha256
      || hash(readInside(directory,source.snapshotPath))!==source.sha256 || hash(readInside(repo,source.runStatePath))!==source.runStateSha256
      || newestSource(repo,source.category,buildMatches)!==source.path) throw Error("Quellen verändert oder durch neuere Läufe ersetzt");
  if (!Array.isArray(manifest.documents) || manifest.documents.length!==3) throw Error("Prüfdokumente fehlen");
  for (const doc of manifest.documents) {
    const raw=readInside(directory,doc.path);
    if (hash(raw)!==doc.sha256 || raw.length!==doc.bytes || doc.sourcePath && hash(readInside(repo,doc.sourcePath))!==doc.sha256) throw Error("Dokumenthash ungültig");
  }
  const expected=manifest.selectedCaseIds,completed=result.completedCaseIds;
  if (!Array.isArray(completed) || new Set(completed).size!==completed.length || !completed.every(id=>expected.includes(id))) throw Error("Ergebnisfallliste unvollständig/doppelt/unerwartet");
  if (!Array.isArray(manifest.batches) || !manifest.batches.length) throw Error("Fallgruppen fehlen");
  const derived=[];
  for (const batch of manifest.batches) {
    const raw=readInside(directory,batch.name);
    if (hash(raw)!==batch.sha256 || raw.length!==batch.bytes) throw Error("Fallgruppe verändert");
    const req=batchRequirements(raw.toString("utf8"));
    if (!sameIds(req.map(r=>r.caseId),batch.caseIds)) throw Error("Fallgruppen-Inhalt widerspricht Fall-IDs");
    derived.push(...req);
  }
  if (canonical(derived)!==canonical(manifest.caseRequirements)) throw Error("Abdeckungsanforderungen stimmen nicht mit unabhängiger Quelldaten-Nachrechnung überein");
  const actualCases=answer(JSON.stringify({schemaVersion:2,caseIds:completed,cases:result.cases}),completed,derived);
  const full=result.transportVerified===true,checks=result.transportChecks;
  if (!Array.isArray(checks) || checks.length>manifest.batches.length || full && checks.length!==manifest.batches.length) throw Error("Transportbelege unvollständig");
  if (result.totalCases!==expected.length || result.completedCases!==actualCases.length || result.totalBatches!==manifest.batches.length
      || result.completedBatches!==checks.length) throw Error("Laufzähler widersprechen Fall-/Transportabdeckung");
  if (!Array.isArray(result.attemptChecks)) throw Error("Vollständiges Sendeversuchsprotokoll fehlt");
  const observedProvisional=[];
  for (const attempt of result.attemptChecks) {
    let observed=[];
    if (attempt.responseValidated!==true && typeof attempt.responseSha256 === "string") {
      if (!Number.isInteger(attempt.batch) || attempt.batch<1 || attempt.batch>manifest.batches.length
          || !Number.isInteger(attempt.attempt) || attempt.attempt<1 || attempt.attempt>3) throw Error("Ungültige Identität eines Sendeversuchs");
      const raw=readInside(directory,attempt.responseFile);
      if (hash(raw)!==attempt.responseSha256) throw Error("Frühere Modellantwort wurde verändert");
      if (typeof attempt.requestSha256 === "string" && hash(readInside(directory,attempt.requestFile))!==attempt.requestSha256) throw Error("Frühere Anfrage wurde verändert");
      try {
        const response=JSON.parse(raw);
        if (Array.isArray(response.choices) && response.choices.length===1 && typeof response.choices[0]?.message?.content === "string") {
          observed=provisionalFailures(response.choices[0].message.content,manifest.batches[attempt.batch-1].caseIds,attempt.batch,attempt.attempt,attempt.requestFile,attempt.responseFile);
        }
      } catch (_) { /* Unlesbare Antwort ist kein behaupteter fachlicher Fehler. */ }
    }
    if (canonical(observed)!==canonical(attempt.provisionalFailures ?? [])) throw Error("Früherer unverifizierter Modellbefund fehlt/widerspricht Rohantwort");
    observedProvisional.push(...observed);
  }
  if (canonical(observedProvisional)!==canonical(result.provisionalFailures ?? [])) throw Error("Unverifizierte Modellbefunde stimmen nicht mit Sendeversuchen überein");
  if (result.hasUnverifiedFindings!==undefined && result.hasUnverifiedFindings !== !!observedProvisional.length) throw Error("Widersprüchlicher Hinweisstatus früherer Modellbefunde");
  if (observedProvisional.length && (result.calculationVerified===true || result.reviewCoverageVerified===true)) throw Error("Ungeklärter früherer Modellbefund widerspricht fachlicher Freigabe");
  const received=[],fromResponses=[];
  for (let i=0;i<checks.length;i++) {
    const batch=manifest.batches[i],check=checks[i];
    if (check.batch!==i+1 || !sameIds(check.caseIds,batch.caseIds) || check.httpStatus<200 || check.httpStatus>=300 || check.responseValidated!==true
        || check.finishReason!=="stop" || check.requestedModel!==manifest.model || String(check.returnedModel).toLowerCase()!==manifest.model.toLowerCase()) throw Error("API-Übernahme unvollständig");
    const requestRaw=readInside(directory,check.requestFile),responseRaw=readInside(directory,check.responseFile);
    if (hash(requestRaw)!==check.requestSha256 || hash(responseRaw)!==check.responseSha256) throw Error("Request-/Response-Hash ungültig");
    const request=JSON.parse(requestRaw),response=JSON.parse(responseRaw),req=derived.filter(r=>batch.caseIds.includes(r.caseId));
    if (request.model!==manifest.model || request.stream!==false || !Array.isArray(request.messages) || request.messages.length!==2
        || request.messages[0].role!=="system" || request.messages[0].content!==contract.systemMessage || request.messages[1].role!=="user"
        || String(response.model).toLowerCase()!==manifest.model.toLowerCase() || !Array.isArray(response.choices)
        || response.choices.length!==1 || response.choices[0].finish_reason!=="stop") throw Error("Modell/Antwort unvollständig");
    const user=request.messages[1].content,split=user.indexOf("\n\n");
    const envelope=JSON.parse(user.slice(0,split));
    if (canonical(envelope)!==canonical({caseIds:batch.caseIds,requirements:req.map(coverage),instruction:contract.instruction})) throw Error("Gesendeter Auftrag/Fallabdeckung widerspricht Vertrag");
    const names=["llm-pruefauftrag.txt","gesetzesgrundlagen-2026.md",batch.name];
    // Wiederholungssendungen dürfen ausschließlich den exakt rekonstruierbaren
    // Korrekturzusatz des unmittelbar vorherigen Fehlversuchs tragen (Spiegel der
    // Java-Seite in LlmReviewService.korrekturAnweisung).
    let suffix="";
    if (check.korrekturHinweis===true && Number.isInteger(check.attempt)) {
      let previous=null,best=0;
      for (const attempt of result.attemptChecks) {
        if (attempt.batch===check.batch && Number.isInteger(attempt.attempt) && attempt.attempt<check.attempt
            && attempt.error!=null && attempt.attempt>best) { best=attempt.attempt;previous=String(attempt.error); }
      }
      if (previous!==null) suffix=correctionInstruction(previous,req);
    }
    if (user.slice(split+2)!==names.map(name=>named(name,readInside(directory,name).toString("utf8"))).join("")+suffix) throw Error("Gesendete Dokumenttexte stimmen nicht mit Snapshots überein");
    if (!Array.isArray(check.documents) || !sameIds(check.documents.map(d=>d.name),names)) throw Error("Drei gesendete Dokumente nicht belegt");
    for (const doc of check.documents) { const raw=readInside(directory,doc.name);if (hash(raw)!==doc.sha256 || raw.length!==doc.bytes) throw Error("Gesendeter Dokumenthash ungültig"); }
    fromResponses.push(...answer(response.choices[0].message.content,batch.caseIds,req));received.push(...batch.caseIds);
  }
  if (!sameIds(received,full?expected:completed) || canonical([...actualCases].sort((a,b)=>a.caseId.localeCompare(b.caseId)))!==canonical([...fromResponses].sort((a,b)=>a.caseId.localeCompare(b.caseId)))) throw Error("Ergebnis stimmt nicht mit validierten Modellantworten überein");
  const reference=referenceChecks(actualCases,derived);
  if (canonical(reference)!==canonical(result.referenceChecks)) throw Error("Referenzprüfung wurde verändert/widerspricht Nachrechnung");
  const state=actualCases.some(c=>c.verdict==="FAIL") || reference.some(r=>r.state==="FAIL")?"FAIL":actualCases.some(c=>c.verdict==="UNVERIFIED") || reference.some(r=>r.state==="UNVERIFIED") || observedProvisional.length?"UNVERIFIED":"PASS";
  if (full && state!==result.state || result.state==="PASS" && (!full || !terminal)) throw Error("Widersprüchliches Gesamturteil");
  if (result.state === "PASS" && (result.calculationVerified!==true || result.reviewCoverageVerified!==true)) throw Error("Rechen-/Prüfpunktabdeckung nicht vollständig bestätigt");
  return {manifest,result,terminal,provisionalFailures:observedProvisional,state:full && terminal?state:state==="FAIL"?"FAIL":"UNVERIFIED"};
}
function latestEvidence(repo,options={}) {
  const logs=path.join(repo,"testsweet/logs");if (!fs.existsSync(logs)) return null;
  const candidates=fs.readdirSync(logs).filter(x=>x.startsWith("llm-review-")).flatMap(name=>{
    const directory=path.join(logs,name),started=path.join(directory,"started.json"),result=path.join(directory,"result.json");
    if (!fs.existsSync(started) && !fs.existsSync(result)) return []; // Preview ist kein Prüfversuch.
    let time=fs.statSync(fs.existsSync(started)?started:result).mtimeMs;
    try { const value=JSON.parse(fs.readFileSync(fs.existsSync(started)?started:result,"utf8"));const parsed=Date.parse(value.startedAt || value.completedAt);if (Number.isFinite(parsed)) time=parsed; } catch (_) {}
    return [{directory,time}];
  }).sort((a,b)=>b.time-a.time || b.directory.localeCompare(a.directory));
  if (!candidates.length) return null;
  const directory=candidates[0].directory;
  let raw={};try{const parsed=JSON.parse(fs.readFileSync(path.join(directory,fs.existsSync(path.join(directory,"result.json"))?"result.json":"status.json"),"utf8"));if (parsed && typeof parsed === "object") raw=parsed;}catch(_){}
  const rawCases=(Array.isArray(raw.cases)?raw.cases:[]).filter(c=>c && typeof c === "object"),rawReferences=(Array.isArray(raw.referenceChecks)?raw.referenceChecks:[]).filter(c=>c && typeof c === "object");
  const knownFindings=[...rawCases.filter(c=>c.verdict==="FAIL" || Array.isArray(c.findings) && c.findings.length).flatMap(c=>(Array.isArray(c.findings)?c.findings:[]).map(f=>`${c.caseId}: ${f}`)),
    ...rawCases.flatMap(c=>(Array.isArray(c.checks)?c.checks:[]).filter(check=>check?.verdict === "FAIL").map(check=>`${c.caseId}/${check.checkId}: ${check.reasoning}`)),
    ...rawReferences.filter(r=>r.state==="FAIL").flatMap(r=>[...(Array.isArray(r.findings)?r.findings:[]),...(Array.isArray(r.workflowFindings)?r.workflowFindings:[])].map(f=>`${r.caseId}: ${f}`))];
  const common={datei:path.relative(repo,path.join(directory,fs.existsSync(path.join(directory,"result.json"))?"result.json":"status.json")).replace(/\\/g,"/"),
    datum:String(raw.completedAt || raw.startedAt || "").slice(0,10),cases:rawCases,referenceChecks:rawReferences,knownFindings,
    provisionalFailures:(Array.isArray(raw.provisionalFailures)?raw.provisionalFailures:[]).filter(row=>row && row.source === "UNVALIDATED_MODEL_RESPONSE")};
  try {
    const {manifest,result,state,provisionalFailures}=verify(directory,repo,options);
    return {...common,provisionalFailures,evidenceVerified:true,valid:state==="PASS",transportVerified:result.transportVerified===true,state,
      reason:state==="PASS"?"":provisionalFailures.length ? "Ungeklärte Hinweise aus früheren formal ungültigen Modellantworten; Logdateien auswerten. " + (result.message || "") : result.message || "Fachliche Befunde oder unvollständige Prüfung",
      buildEvidence:manifest.buildEvidence,documents:[...new Map([...manifest.documents,...result.transportChecks.flatMap(check=>check.documents)].map(doc=>[doc.name,doc])).values()],meta:{pruefumfang:`${result.completedCaseIds.length}/${manifest.selectedCaseIds.length} vollständige Fälle (Positiv/Negativ; Stichprobe)`,
        modell:manifest.model,ergebnis:state,datenstand:manifest.sources.map(s=>s.runId).join(" / "),methode:"Echte API-Aufrufe; vollständige strukturierte Abdeckung, unabhängige Zahlenreferenz und Dokumenthashes geprüft"}};
  } catch (error) {
    return {...common,evidenceVerified:false,valid:false,transportVerified:false,state:knownFindings.length?"FAIL":"UNVERIFIED",reason:error.message,meta:{modell:raw.model || "unbekannt"}};
  }
}
module.exports={latestEvidence,verify,answer,referenceChecks,provisionalFailures,canonical};
