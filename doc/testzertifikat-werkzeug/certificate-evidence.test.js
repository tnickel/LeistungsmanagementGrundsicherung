const test=require("node:test"),assert=require("node:assert/strict"),fs=require("fs"),os=require("os"),path=require("path");
const {collect,latestRuns,junit,parseSuite,buildValid,hash,sources,digest,classHash,MATRIX}=require("./certificate-evidence");
const llm=require("./llm-evidence"),reference=require("./llm-reference"),{publish,current}=require("./publish-certificate");
const contract=JSON.parse(fs.readFileSync(path.join(__dirname,"llm-response-contract.json"),"utf8"));
function write(repo,file,value) {const dest=path.join(repo,file);fs.mkdirSync(path.dirname(dest),{recursive:true});fs.writeFileSync(dest,typeof value === "string" || Buffer.isBuffer(value)?value:JSON.stringify(value));return dest;}
function fixture(t,nested="") {
  const owned=fs.mkdtempSync(path.join(os.tmpdir(),"lmg-certificate-regression-")),repo=nested?path.join(owned,nested):owned;
  t.after(()=>{assert(path.basename(owned).startsWith("lmg-certificate-regression-"));fs.rmSync(owned,{recursive:true,force:true});});
  write(repo,"tools/evidence-sources.txt","dir:src\ndir:testsweet/src\ndir:tools\nfile:pom.xml\nfile:testsweet/pom.xml\ntool:doc/testzertifikat-werkzeug\n");
  write(repo,"pom.xml","<project/>");write(repo,"testsweet/pom.xml","<project/>");
  write(repo,"src/test/java/example/GoldWerteTest.java","package example; public class GoldWerteTest { @Test void one() {} }");
  write(repo,"testsweet/src/test/java/example/SimulatorTest.java","package example; public class SimulatorTest { @Test void one() {} }");
  write(repo,"src/main/java/Product.java","class Product {}");write(repo,"testsweet/src/main/java/Simulator.java","class Simulator {}");
  write(repo,"target/classes/Product.class","compiled-product");write(repo,"testsweet/target/classes/Simulator.class","compiled-simulator");
  write(repo,"doc/testzertifikat-werkzeug/llm-response-contract.json",contract);
  write(repo,"doc/testzertifikat-werkzeug/llm-pruefauftrag.txt","Prüfauftrag");write(repo,"doc/testzertifikat-werkzeug/gesetzesgrundlagen-2026.md","Gesetze");
  return repo;
}
function build(repo,module="testsweet") {
  const sourceInventory=sources(repo),names=module==="main"?["product"]:["product","testsweet"];
  return {schemaVersion:1,sourceInventory,sourceSha256:digest(sourceInventory),sourceArtifactsMatch:true,
    artifacts:names.map(name=>{const directory=path.join(repo,name==="product"?"target/classes":"testsweet/target/classes"),sha=classHash(directory);return {name,kind:"classes",path:directory,sha256:sha,classesSha256:sha,sourceMatch:true};})};
}
function xmlSuite(name,state="PASS",caseName="one") {
  const fail=state==="FAIL"?1:0,error=state==="ERROR"?1:0,skip=state==="SKIPPED"?1:0;
  return `<testsuite name="${name}" tests="1" failures="${fail}" errors="${error}" skipped="${skip}"><testcase classname="${name}" name="${caseName}">${fail?"<failure>broken</failure>":error?"<error>crashed</error>":skip?"<skipped/>":""}</testcase></testsuite>`;
}
function junitRun(repo,module,id,state="PASS",time="2026-10-13T12:00:00Z",caseName="one") {
  const prefix=module==="main"?"":"testsweet/",name=module==="main"?"example.GoldWerteTest":"example.SimulatorTest";
  const file=`${prefix}target/test-evidence-history/${id}/TEST-${name}.xml`,raw=xmlSuite(name,state,caseName);
  write(repo,file,raw);
  const marker={schemaVersion:1,kind:"JUNIT_RUN",module,runId:id,state:"COMPLETED",startedAt:time,completedAt:time,
    expectedClasses:[name],buildEvidence:build(repo,module),reports:[{path:file,sha256:hash(raw)}]};
  write(repo,`${prefix}target/test-evidence-history/${id}.json`,marker);write(repo,`${prefix}target/test-evidence.json`,marker);
  return marker;
}
function simulator(repo,id,key,state="PASS",time="2026-10-13T12:00:00Z") {
  const [mode,category]=key.split(":"),folder=`testsweet/logs/run-${id}`,md=`# testsweet Lauf\nautomatik_bestanden = ${state==="PASS"}\nlauf_erfolgreich = false\nkategorie = ${category}\n## TF1: Workflow\nV1: OK Workflow geprüft\n  erwartet = 1 | ist = 1\n  sql = SELECT COUNT(*) FROM TEST_DATA\n  params = []\n`;
  write(repo,folder+"/testdokumentation.md",md);write(repo,folder+"/testdokumentation.pdf","%PDF-1.7\n"+"x".repeat(200));
  const lifecycle={schemaVersion:1,kind:"SIMULATOR_RUN",runId:`run-${id}`,mode,category,state,startedAt:time,completedAt:state==="RUNNING"?null:time,
    success:state==="PASS",totalCases:1,completedCases:1,verifiedCases:state==="PASS"?1:0,failedCases:state==="FAIL"?1:0,verificationErrors:0,
    logComplete:true,reportsComplete:true,evidenceErrors:[],buildEvidence:build(repo),
    reports:["testdokumentation.md","testdokumentation.pdf"].map(file=>({path:file,sha256:hash(fs.readFileSync(path.join(repo,folder,file)))}))};
  write(repo,folder+"/run-state.json",lifecycle);return {folder,lifecycle,md};
}
function glm(repo,id="one",verdict="PASS",startedAt="2026-10-13T13:00:00Z") {
  const directory=`testsweet/logs/llm-review-${id}`,cases=[],sourceList=[];
  for (const [category,run] of [["POSITIV","20261013-120000-P"],["NEGATIV","20261013-120001-N"]]) {
    const {folder,md}=simulator(repo,run,"FACH:"+category,"PASS","2026-10-13T12:30:00Z"),caseId=`${category}:run-${run}:TF1`;
    write(repo,`${directory}/source-${category}.md`,md);
    sourceList.push({category,path:folder+"/testdokumentation.md",sha256:hash(md),snapshotPath:`source-${category}.md`,runId:`run-${run}`,
      runStatePath:folder+"/run-state.json",runStateSha256:hash(fs.readFileSync(path.join(repo,folder,"run-state.json")))});
    cases.push({caseId,verdict,findings:verdict==="PASS"?[]:["CONTROLLED_FINDING"],reasoning:"Workflow unabhängig geprüft",calculations:[],checks:[{checkId:"V1",verdict,reasoning:"SQL-Ergebnis geprüft"}]});
  }
  const ids=cases.map(c=>c.caseId),batchText=ids.map(caseId=>`# Fall-ID: ${caseId}\n\n## TF1: Workflow\nV1: OK Workflow geprüft\n  erwartet = 1 | ist = 1\n  sql = SELECT COUNT(*) FROM TEST_DATA\n  params = []\n`).join("\n"),req=reference.batchRequirements(batchText);
  const docs=[["llm-pruefauftrag.txt","Prüfauftrag"],["gesetzesgrundlagen-2026.md","Gesetze"],["pruefmenge.md",batchText]],batchName="batch-1.md";
  for (const [name,text] of [...docs,[batchName,batchText]]) write(repo,directory+"/"+name,text);
  write(repo,directory+"/llm-response-contract.json",contract);
  const manifest={schemaVersion:2,kind:"LLM_REVIEW",reviewId:id,model:"glm-5.3",selectedCaseIds:ids,buildEvidence:build(repo),sources:sourceList,
    responseContractSha256:hash(fs.readFileSync(path.join(repo,directory,"llm-response-contract.json"))),caseRequirements:req,
    documents:docs.map(([name,text])=>({name,path:name,sha256:hash(text),bytes:Buffer.byteLength(text),sourcePath:name==="pruefmenge.md"?"":"doc/testzertifikat-werkzeug/"+name})),
    batches:[{name:batchName,sha256:hash(batchText),bytes:Buffer.byteLength(batchText),caseIds:ids}]};
  const envelope={caseIds:ids,requirements:req.map(reference.coverage),instruction:contract.instruction};
  const named=(name,text)=>`\n=== BEGIN DOCUMENT ${name} ===\n${text}\n=== END DOCUMENT ${name} ===\n`;
  const request={model:manifest.model,stream:false,messages:[{role:"system",content:contract.systemMessage},{role:"user",content:JSON.stringify(envelope)+"\n\n"+[[docs[0][0],docs[0][1]],[docs[1][0],docs[1][1]],[batchName,batchText]].map(([name,text])=>named(name,text)).join("")}]};
  const response={model:manifest.model,choices:[{finish_reason:"stop",message:{content:JSON.stringify({schemaVersion:2,caseIds:ids,cases})}}]};
  write(repo,directory+"/manifest.json",manifest);write(repo,directory+"/request-1.json",request);write(repo,directory+"/response-1.json",response);
  write(repo,directory+"/started.json",{schemaVersion:2,kind:"LLM_REVIEW_START",reviewId:id,startedAt});
  const result={schemaVersion:2,kind:"LLM_REVIEW",reviewId:id,model:manifest.model,state:verdict,startedAt,completedAt:startedAt,
    manifestSha256:hash(fs.readFileSync(path.join(repo,directory,"manifest.json"))),completedCaseIds:ids,cases,referenceChecks:llm.referenceChecks(cases,req),transportVerified:true,
    totalCases:ids.length,completedCases:ids.length,totalBatches:1,completedBatches:1,calculationVerified:true,reviewCoverageVerified:true,
    transportChecks:[{batch:1,caseIds:ids,httpStatus:200,responseValidated:true,finishReason:"stop",requestedModel:manifest.model,returnedModel:manifest.model,
      requestFile:"request-1.json",responseFile:"response-1.json",requestSha256:hash(fs.readFileSync(path.join(repo,directory,"request-1.json"))),responseSha256:hash(fs.readFileSync(path.join(repo,directory,"response-1.json"))),
      documents:[docs[0],docs[1],[batchName,batchText]].map(([name,text])=>({name,sha256:hash(text),bytes:Buffer.byteLength(text)}))}]};
  result.attemptChecks=result.transportChecks.map(check=>({...check,attempt:1}));result.provisionalFailures=[];result.hasUnverifiedFindings=false;
  write(repo,directory+"/result.json",result);return {directory:path.join(repo,directory),manifest,result};
}
test("JUnit XML widersprüchliche Zähler und doppelte Testfälle werden abgewiesen",()=>{
  assert.throws(()=>parseSuite('<testsuite name="x" tests="2" failures="0" errors="0" skipped="0"><testcase name="one"/></testsuite>'));
  assert.throws(()=>parseSuite('<testsuite name="x" tests="2" failures="0" errors="0" skipped="0"><testcase name="one"/><testcase name="one"/></testsuite>'));
});
test("Fehlende JUnit-Läufe sind unvollständig; keine statisch grünen Tests",t=>{const repo=fixture(t);assert.equal(junit(repo,"main").state,"UNVERIFIED");assert.notEqual(collect(repo).verdict,"BESTANDEN");});
test("Winterzeit: älterer PASS mit +02 kann neueren FAIL mit +01 nicht verdecken",t=>{
  const repo=fixture(t);simulator(repo,"20261025-025500-old-pass","FACH:POSITIV","PASS","2026-10-25T02:55:00+02:00");
  simulator(repo,"20261025-020500-new-fail","FACH:POSITIV","FAIL","2026-10-25T02:05:00+01:00");
  const chosen=latestRuns(repo).selected["FACH:POSITIV"];assert.equal(chosen.state,"FAIL");assert.match(chosen.ordner,/new-fail$/);
});
test("Mehrdeutige lokale Altzeitstempel bleiben unbestätigt und bewahren bekannten FAIL",t=>{
  const repo=fixture(t),before=process.env.TZ;process.env.TZ="Europe/Berlin";
  try {
    simulator(repo,"20261025-025500-old-pass","FACH:POSITIV","PASS","2026-10-25T02:55:00");
    simulator(repo,"20261025-020500-new-fail","FACH:POSITIV","FAIL","2026-10-25T02:05:00");
    assert.equal(latestRuns(repo).selected["FACH:POSITIV"].state,"FAIL");
    simulator(repo,"20261026-120000-fixed","FACH:POSITIV","PASS","2026-10-26T12:00:00Z");
    assert.equal(latestRuns(repo).selected["FACH:POSITIV"].state,"PASS","Eindeutig neuer Korrekturlauf löst historischen Konflikt auf");
  } finally {if (before===undefined) delete process.env.TZ;else process.env.TZ=before;}
});
function referenceInput(person,level,ww=false) {
  return `p${person}_eingaben: rbs=${level}, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=${ww}, kdu_miete=350.00, kdu_betrieb=50.00, kdu_heizung=100.00, rente=0.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00\n`;
}
test("Unabhängige Referenz: reiner Minderjährigenhaushalt erhält Grundbetrag einmal",()=>{
  const req=reference.requirement("GOLD", "block: Minderjährige\nzahlungsmonat = 2026-10\nvermoegen = 10500.00\nbudget_start = -\n"+referenceInput(1,4)+referenceInput(2,6));
  const bg=req.calculations[0].household;assert.equal(bg.schonvermoegen,"10000.00");assert.equal(bg.bg_verwertbares_vermoegen,"500.00");assert.equal(bg.bg_zahlbetrag,"1328.00");
});
test("Unabhängige Referenz: KdU-Abzug mit drei Dezimalstellen und gemeinsamer Grenze",()=>{
  const req=reference.requirement("GOLD", "block: KdU\nzahlungsmonat = 2026-10\nvermoegen = 0.00\nbudget_start = -\nkdu_warmwasser_abzug_anteil = 0.075\nkdu_bg_grenze = 600.00\n"+referenceInput(1,2)+referenceInput(2,2,true));
  const block=req.calculations[0];assert.equal(block.persons[0].values.kdu_warmwasser_abzug,"7.50");assert.equal(block.persons[0].values.kdu,"492.50");assert.equal(block.persons[1].values.kdu_warmwasser_abzug,"0.00");assert.equal(block.persons[1].values.kdu,"107.50");assert.equal(block.household.bg_zahlbetrag,"1623.64");
  assert.equal(reference.requirement("BAD","block: KdU\nvermoegen = 0.00\nbudget_start = -\nkdu_bg_grenze = 600.00\n"+referenceInput(1,1)).calculations.length,0);
});
test("Aktuelle vollständige JUnit-XML werden gelesen; spätere Fehler dominieren",t=>{
  const repo=fixture(t);junitRun(repo,"main","first");assert.equal(junit(repo,"main").state,"PASS");
  junitRun(repo,"main","second","FAIL","2026-10-13T12:01:00Z");assert.equal(junit(repo,"main").state,"FAIL");assert.equal(junit(repo,"main").suites[0].cases[0].message,"broken");assert.equal(collect(repo).verdict,"NICHT BESTANDEN");
});
test("Ein neuer begonnener JUnit-Lauf verdrängt alte Freigaben und wertet Livefehler",t=>{
  const repo=fixture(t);const marker=junitRun(repo,"main","first");
  write(repo,"target/test-evidence.json",{...marker,state:"RUNNING",runId:"new",startedAt:"2026-10-13T12:01:00Z",previousReports:[]});
  assert.equal(junit(repo,"main").state,"UNVERIFIED");write(repo,"target/surefire-reports/TEST-example.GoldWerteTest.xml",xmlSuite("example.GoldWerteTest","ERROR"));assert.equal(junit(repo,"main").state,"FAIL");
});
test("Skips sind unvollständig und können mit demselben Build gezielt nachgeholt werden",t=>{
  const repo=fixture(t);junitRun(repo,"main","first","SKIPPED");assert.equal(junit(repo,"main").state,"UNVERIFIED");
  junitRun(repo,"main","second","PASS","2026-10-13T12:01:00Z");assert.equal(junit(repo,"main").state,"PASS");
  junitRun(repo,"main","third","FAIL","2026-10-13T12:02:00Z");junitRun(repo,"main","fourth","SKIPPED","2026-10-13T12:03:00Z");assert.equal(junit(repo,"main").state,"FAIL");
});
test("Quelländerung, neue Testklasse und veraltete Klassen entwerten Buildnachweise",t=>{
  const repo=fixture(t),b=build(repo);assert.equal(buildValid(repo,b),true);write(repo,"target/classes/Product.class","different");assert.equal(buildValid(repo,b),false);
  junitRun(repo,"main","first");write(repo,"src/test/java/example/NewTest.java","package example; class NewTest { @Test void required() {} }");assert.equal(junit(repo,"main").state,"UNVERIFIED");
});
test("Modi bleiben getrennt; späterer FACH-PASS verdeckt WEB-FAIL nicht",t=>{
  const repo=fixture(t);simulator(repo,"20261013-110000","WEB:POSITIV","FAIL","2026-10-13T11:00:00Z");simulator(repo,"20261013-120000","FACH:POSITIV");
  const runs=latestRuns(repo).selected;assert.equal(runs["WEB:POSITIV"].state,"FAIL");assert.equal(runs["FACH:POSITIV"].state,"PASS");assert.equal(collect(repo).verdict,"NICHT BESTANDEN");
});
test("Neuester Simulatorabbruch, leere Fallzahl und beschädigter Export erhalten kein PASS",t=>{
  const repo=fixture(t);simulator(repo,"20261013-110000","FACH:POSITIV");const run=simulator(repo,"20261013-120000","FACH:POSITIV","RUNNING");
  assert.equal(latestRuns(repo).selected["FACH:POSITIV"].state,"UNVERIFIED");
  write(repo,run.folder+"/run-state.json",{...run.lifecycle,state:"PASS",success:true,totalCases:0,verifiedCases:0});assert.equal(latestRuns(repo).selected["FACH:POSITIV"].state,"UNVERIFIED");
  const valid=simulator(repo,"20261013-130000","FACH:POSITIV","PASS","2026-10-13T13:00:00Z");write(repo,valid.folder+"/testdokumentation.pdf","broken");assert.equal(latestRuns(repo).selected["FACH:POSITIV"].state,"UNVERIFIED");
});
test("Volle Pflichtmatrix einschließlich WEB-LAST wird verlangt, WEB-STRESS ist N/A",t=>{
  const repo=fixture(t);for(const [index,key] of MATRIX.entries()) simulator(repo,"20261013-12000"+index,key);
  const data=collect(repo);assert.equal(data.layers.filter(layer=>MATRIX.includes(layer.id)).every(layer=>layer.state==="PASS"),true);assert.deepEqual(data.notApplicable,["WEB:STRESS"]);
});
test("Echte Schema2-Belegstruktur bindet API, Dokumenttexte, Rechenabdeckung und Ergebnis",t=>{const repo=fixture(t);glm(repo);const result=llm.latestEvidence(repo);assert.equal(result.valid,true,result.reason);assert.equal(result.evidenceVerified,true);assert.equal(result.transportVerified,true);});
test("Fachlicher GLM-Befund bleibt FAIL und enthält konkrete Fall-ID",t=>{const repo=fixture(t);glm(repo,"failed","FAIL");const result=llm.latestEvidence(repo);assert.equal(result.state,"FAIL");assert.match(result.knownFindings[0],/TF1.*CONTROLLED_FINDING/);assert.equal(collect(repo).verdict,"NICHT BESTANDEN");});
test("Neuer gestarteter Review ohne Abschluss verdrängt alten PASS; Preview zählt nicht",t=>{
  const repo=fixture(t);glm(repo);write(repo,"testsweet/logs/llm-review-preview/manifest.json",{});assert.equal(llm.latestEvidence(repo).valid,true);
  write(repo,"testsweet/logs/llm-review-new/started.json",{reviewId:"new",startedAt:"2026-10-13T14:00:00Z"});assert.equal(llm.latestEvidence(repo).valid,false);assert.equal(llm.latestEvidence(repo).state,"UNVERIFIED");
});
test("Unterbrochener GLM-Lauf bewahrt bereits bekannte fachliche Fehler",t=>{
  const repo=fixture(t);glm(repo);write(repo,"testsweet/logs/llm-review-new/started.json",{reviewId:"new",startedAt:"2026-10-13T14:00:00Z"});
  write(repo,"testsweet/logs/llm-review-new/status.json",{state:"ERROR",cases:[{caseId:"C1",verdict:"FAIL",findings:["Betrag falsch"]}]});const result=llm.latestEvidence(repo);assert.equal(result.state,"FAIL");assert.match(result.knownFindings[0],/Betrag falsch/);
});
test("Nur Freitext ist keine Nachrechnung und unvollständige Personenwerte werden abgelehnt",()=>{
  assert.throws(()=>llm.answer(JSON.stringify({schemaVersion:2,caseIds:["C1"],cases:[{caseId:"C1",verdict:"PASS",findings:[],reasoning:"Nur Sollwerte übernommen"}]}),["C1"],[{caseId:"C1",calculations:[],checkIds:[]}]),/abdeckung/);
  const req=[{caseId:"C1",calculations:[{blockId:"B1",persons:[{personId:"p1",values:{regelbedarf:"563.00",kdu:"400.00"}}],household:{bg_zahlbetrag:"963.00"}}],checkIds:[],sourceFindings:[],missingInputs:[]}];
  const c={caseId:"C1",verdict:"PASS",findings:[],reasoning:"Gerechnet",calculations:[{blockId:"B1",persons:[{personId:"p1",values:{regelbedarf:"563.00"}}],household:{bg_zahlbetrag:"963.00"}}],checks:[]};
  assert.throws(()=>llm.answer(JSON.stringify({schemaVersion:2,caseIds:["C1"],cases:[c]}),["C1"],req),/Zwischenwerte/);
  c.calculations[0].persons[0].values.kdu="401.00";const checked=llm.answer(JSON.stringify({schemaVersion:2,caseIds:["C1"],cases:[c]}),["C1"],req);assert.equal(llm.referenceChecks(checked,req)[0].state,"UNVERIFIED");
});
test("Geänderte API-Antwort oder neuester Quellfehler verhindert GLM-Freigabe",t=>{
  const repo=fixture(t),review=glm(repo);write(repo,path.relative(repo,path.join(review.directory,"response-1.json")),"{}");assert.equal(llm.latestEvidence(repo).valid,false);assert.equal(llm.latestEvidence(repo).evidenceVerified,false);
  const next=glm(repo,"second");simulator(repo,"20261013-150000","FACH:POSITIV","FAIL","2026-10-13T15:00:00Z");assert.equal(llm.latestEvidence(repo).valid,false);
});
test("Alter grüner PDF-Bestand wird bei LibreOffice-Fehler nicht erneut veröffentlicht",t=>{
  const repo=fixture(t);write(repo,"doc/SDN-Leistung-LMG-Testzertifikat.pdf","OLD GREEN");let calls=0;
  assert.throws(()=>publish(repo,{run(_command,args){if(++calls===1){const docx=args[1];write(repo,path.relative(repo,docx),"PK fake docx");const data=collect(repo);write(repo,path.relative(repo,path.join(path.dirname(docx),"stand.json")),{...data,docxSha256:hash("PK fake docx"),certificateId:"new"});}else throw Error("LibreOffice failure");}}),/LibreOffice/);
  assert.equal(fs.readFileSync(path.join(repo,"doc/SDN-Leistung-LMG-Testzertifikat.pdf"),"utf8"),"OLD GREEN");assert.throws(()=>current(repo),/Kein aktuell/);
});
test("Neue negative Ausgabe darf veröffentlicht werden; neue Belege entwerten Abruf",t=>{
  const repo=fixture(t);let calls=0;
  const marker=publish(repo,{run(_command,args){if(++calls===1){const docx=args[1];write(repo,path.relative(repo,docx),"PK fake docx");write(repo,path.relative(repo,path.join(path.dirname(docx),"stand.json")),{...collect(repo),docxSha256:hash("PK fake docx"),certificateId:"negative"});}else {const directory=args[args.indexOf("--outdir")+1];write(repo,path.relative(repo,path.join(directory,"testzertifikat.pdf")),"%PDF-1.7\n"+"x".repeat(200));}}});
  assert.equal(marker.verdict,"UNVOLLSTÄNDIG");assert.equal(current(repo).state,"READY");simulator(repo,"20261013-140000","FACH:POSITIV","FAIL");assert.throws(()=>current(repo),/älteren|veränderten/);
});
test("Nur vollständige aktuelle Belege aller Schichten ergeben BESTANDEN",t=>{
  const repo=fixture(t);for (const [index,key] of MATRIX.entries()) if (!["FACH:POSITIV","FACH:NEGATIV"].includes(key)) simulator(repo,"20261013-10000"+index,key);
  glm(repo);junitRun(repo,"main","main");junitRun(repo,"testsweet","own");const evidence=collect(repo);
  assert.equal(evidence.verdict,"BESTANDEN",evidence.warnungen.join("\n"));
  const subset=path.join(repo,"testsweet/target/test-evidence-history/own/TEST-example.SimulatorTest.xml");fs.writeFileSync(subset,"<broken>");assert.notEqual(collect(repo).verdict,"BESTANDEN");
});
test("Fehlende einzelne Testmethode verhindert Freigabe auch bei vorhandenem Klassentest",t=>{
  const repo=fixture(t);write(repo,"src/test/java/example/GoldWerteTest.java","package example; class GoldWerteTest { @Test void one() {} @Test void two() {} }");
  junitRun(repo,"main","subset");const result=junit(repo,"main");assert.equal(result.state,"UNVERIFIED");assert(result.reasons.some(r=>r.includes("two")));
});
test("Widersprüchliches äußeres PASS kann innere Fehler oder Befunde nicht wegwiederholen",()=>{
  const req=[{caseId:"C1",calculations:[],checkIds:["V1"],sourceFindings:[],missingInputs:[]}];
  const c={caseId:"C1",verdict:"PASS",findings:[],reasoning:"Widersprüchliche Zusammenfassung",calculations:[],checks:[{checkId:"V1",verdict:"FAIL",reasoning:"Betrag falsch"}]};
  const cases=llm.answer(JSON.stringify({schemaVersion:2,caseIds:["C1"],cases:[c]}),["C1"],req);assert.equal(llm.referenceChecks(cases,req)[0].state,"FAIL");
  c.findings=["Expliziter Softwarefehler"];c.checks[0].verdict="PASS";assert.equal(llm.referenceChecks(llm.answer(JSON.stringify({schemaVersion:2,caseIds:["C1"],cases:[c]}),["C1"],req),req)[0].state,"FAIL");
});
test("Kopierfehler veröffentlicht keine Teilmenge als aktuelles Zertifikat",t=>{
  const repo=fixture(t);fs.mkdirSync(path.join(repo,"doc/SDN-Leistung-LMG-Testzertifikat.pdf"),{recursive:true});let calls=0;
  assert.throws(()=>publish(repo,{run(_command,args){if(++calls===1){const docx=args[1];write(repo,path.relative(repo,docx),"PK docx");write(repo,path.relative(repo,path.join(path.dirname(docx),"stand.json")),{...collect(repo),docxSha256:hash("PK docx"),certificateId:"copy-fail"});}else write(repo,path.relative(repo,path.join(args[args.indexOf("--outdir")+1],"testzertifikat.pdf")),"%PDF-1.7\n"+"x".repeat(200));}}));
  assert.throws(()=>current(repo),/Kein aktuell/);assert.equal(fs.existsSync(path.join(repo,"doc/testzertifikat-werkzeug/current-certificate.lock")),false);
});
test("GUI und Batch können nicht parallel in denselben Zertifikatsbestand publizieren",t=>{
  const repo=fixture(t);write(repo,"doc/testzertifikat-werkzeug/current-certificate.lock",{pid:process.pid,id:"active"});assert.throws(()=>publish(repo),/bereits erzeugt/);
});
test("Laufzeitressourcen sind an den Build gebunden; Metadaten bleiben ausgenommen",t=>{
  const repo=fixture(t),b=build(repo);write(repo,"target/classes/META-INF/product-build.json","compiler metadata");assert.equal(buildValid(repo,b),true);
  write(repo,"target/classes/web/app.js","changed runtime code");assert.equal(buildValid(repo,b),false);
});
test("Unterschiedlicher Produktbytecode bei gleichen Quellen verhindert Gesamtfreigabe",t=>{
  const repo=fixture(t);for (const [index,key] of MATRIX.entries()) if (!["FACH:POSITIV","FACH:NEGATIV"].includes(key)) simulator(repo,"20261013-10000"+index,key);
  glm(repo);const main=junitRun(repo,"main","main");junitRun(repo,"testsweet","own");assert.equal(collect(repo).verdict,"BESTANDEN");
  write(repo,"target/alternate-product/Product.class","different binary same source");const directory=path.join(repo,"target/alternate-product"),sha=classHash(directory);
  main.buildEvidence.artifacts[0]={name:"product",kind:"classes",path:directory,sourceMatch:true,sha256:sha,classesSha256:sha};write(repo,"target/test-evidence.json",main);
  const result=collect(repo);assert.equal(result.verdict,"UNVOLLSTÄNDIG");assert(result.layers.find(l=>l.id==="BUILD").reasons.some(r=>r.includes("Unterschiedliche")));
});
test("Unveränderte alte Live-XML mit fractionalmtime gelten nicht als neuer JUnit-Fehler",t=>{
  const repo=fixture(t),marker=junitRun(repo,"main","completed"),relative="target/surefire-reports/TEST-example.GoldWerteTest.xml";
  const file=write(repo,relative,xmlSuite("example.GoldWerteTest","FAIL")),seconds=1_790_000_000.123456;
  fs.utimesSync(file,seconds,seconds);const mtime=fs.statSync(file).mtimeMs;
  assert.notEqual(mtime,Math.trunc(mtime),"Fixture benötigt einen Dateizeitstempel mit Millisekundenbruchteil");
  write(repo,"target/test-evidence.json",{...marker,state:"RUNNING",runId:"new",startedAt:"2026-10-13T12:01:00Z",
    previousReports:[{name:path.basename(file),signature:Math.trunc(mtime)+":"+hash(fs.readFileSync(file))}]});
  assert.equal(junit(repo,"main").state,"UNVERIFIED");assert.equal(junit(repo,"main").failures,0);
});
function earlierRawFailure(repo,review) {
  const requestFile="request-1-attempt-1.json",responseFile="response-1-attempt-1.json";
  const request=fs.readFileSync(path.join(review.directory,"request-1.json")),response=JSON.parse(fs.readFileSync(path.join(review.directory,"response-1.json"),"utf8"));
  const content=JSON.parse(response.choices[0].message.content);
  content.cases[0].verdict="FAIL";content.cases[0].findings=[];content.cases[0].reasoning="RAW_FAIL_DIAGNOSE_42: Zahlbetrag könnte fehlerhaft sein";
  response.choices[0].message.content=JSON.stringify(content);
  write(repo,path.relative(repo,path.join(review.directory,requestFile)),request);write(repo,path.relative(repo,path.join(review.directory,responseFile)),response);
  const observed=llm.provisionalFailures(response.choices[0].message.content,review.manifest.batches[0].caseIds,1,1,requestFile,responseFile);
  review.result.attemptChecks.unshift({batch:1,attempt:1,requestFile,responseFile,requestSha256:hash(request),responseSha256:hash(fs.readFileSync(path.join(review.directory,responseFile))),responseValidated:false,provisionalFailures:observed});
  review.result.attemptChecks[1].attempt=2;review.result.provisionalFailures=observed;review.result.hasUnverifiedFindings=true;
  review.result.state="UNVERIFIED";review.result.calculationVerified=false;review.result.reviewCoverageVerified=false;
  write(repo,path.relative(repo,path.join(review.directory,"result.json")),review.result);
}
test("Formatungültiges frühes FAIL bleibt nach späterem PASS unverifiziert mit Diagnose sichtbar",t=>{
  const repo=fixture(t),review=glm(repo);earlierRawFailure(repo,review);
  const evidence=llm.latestEvidence(repo);assert.equal(evidence.state,"UNVERIFIED",evidence.reason);assert.equal(evidence.evidenceVerified,true,evidence.reason);
  assert.equal(evidence.transportVerified,true);assert.match(JSON.stringify(evidence.provisionalFailures),/RAW_FAIL_DIAGNOSE_42/);
  assert.equal(evidence.knownFindings.some(f=>f.includes("RAW_FAIL_DIAGNOSE_42")),false);assert.match(evidence.reason,/Ungeklärte/);
  assert.equal(collect(repo).verdict,"UNVOLLSTÄNDIG");
});
test("Entfernte oder verfälschte frühere Modellbefunde verhindern jede Freigabe",t=>{
  const repo=fixture(t),review=glm(repo);earlierRawFailure(repo,review);
  review.result.provisionalFailures=[];write(repo,path.relative(repo,path.join(review.directory,"result.json")),review.result);
  assert.equal(llm.latestEvidence(repo).evidenceVerified,false);
  review.result.provisionalFailures=review.result.attemptChecks[0].provisionalFailures;
  write(repo,path.relative(repo,path.join(review.directory,"result.json")),review.result);assert.equal(llm.latestEvidence(repo).evidenceVerified,true);
  write(repo,path.relative(repo,path.join(review.directory,"response-1-attempt-1.json")),"{}");
  assert.equal(llm.latestEvidence(repo).evidenceVerified,false);assert.equal(llm.latestEvidence(repo).valid,false);assert.match(llm.latestEvidence(repo).reason,/Frühere Modellantwort/);
});
test("Provisorische Modellhinweise benötigen lesbares JSON und angeforderte Fall-IDs",()=>{
  assert.deepEqual(llm.provisionalFailures("{broken FAIL",["C1"],1,1,"request","response"),[]);
  assert.deepEqual(llm.provisionalFailures(JSON.stringify({cases:[{caseId:"foreign",verdict:"FAIL"}]}),["C1"],1,1,"request","response"),[]);
  const rows=llm.provisionalFailures(JSON.stringify({cases:[{caseId:"C1",verdict:"PASS",findings:["Ungeklärter Hinweis"],checks:[{checkId:"V1",verdict:"FAIL",reasoning:"Kontrollierte Abweichung"}]}]}),["C1"],2,3,"request","response");
  assert.equal(rows.length,1);assert.match(rows[0].reason,/Befund: Ungeklärter Hinweis \| Prüfpunkt V1 meldet FAIL: Kontrollierte Abweichung/);
});
test("Langer Projektpfad nutzt je Export ein frisches kurzes externes LibreOffice-Profil",t=>{
  const repo=fixture(t,"project-"+"x".repeat(140)),profiles=[];let calls=0;
  const run=(_command,args)=>{
    if (++calls%2===1) {
      const docx=args[1];write(repo,path.relative(repo,docx),"PK docx");write(repo,path.relative(repo,path.join(path.dirname(docx),"stand.json")),{...collect(repo),docxSha256:hash("PK docx"),certificateId:"long-project"});
    } else {
      const profile=require("url").fileURLToPath(args.find(arg=>arg.startsWith("-env:UserInstallation=")).slice("-env:UserInstallation=".length));
      assert(profile.startsWith(path.resolve(os.tmpdir())+path.sep));assert(path.basename(profile).startsWith("lmg-lo-"));assert(!profile.startsWith(repo+path.sep));
      assert(profile.length<repo.length);assert(fs.statSync(profile).isDirectory());profiles.push(profile);
      write(repo,path.relative(repo,path.join(args[args.indexOf("--outdir")+1],"testzertifikat.pdf")),"%PDF-1.7\n"+"x".repeat(200));
    }
  };
  publish(repo,{run});publish(repo,{run});assert.equal(new Set(profiles).size,2);assert(profiles.every(profile=>!fs.existsSync(profile)));assert.equal(current(repo).state,"READY");
});
test("Browserkomponenten, Exporte und reine LLM-Vorschauen verändern keine Prüfbelegbindung",t=>{
  const repo=fixture(t);for (const [index,key] of MATRIX.entries()) if (!["FACH:POSITIV","FACH:NEGATIV"].includes(key)) simulator(repo,"20261013-10000"+index,key);
  const review=glm(repo);junitRun(repo,"main","main");junitRun(repo,"testsweet","own");const before=collect(repo);assert.equal(before.verdict,"BESTANDEN");
  write(repo,"testsweet/logs/browser-profile/session/CaptchaProviders/version/manifest.json",{version:1});
  write(repo,"testsweet/logs/browser-profile/session/FirstPartySets/version/manifest.json",{version:1});
  write(repo,"testsweet/logs/certificate-output/manifest.json",{generated:true});
  write(repo,"testsweet/logs/llm-review-preview/manifest.json",{preview:true});
  assert.equal(collect(repo).evidenceFingerprint,before.evidenceFingerprint);
  write(repo,"testsweet/logs/browser-profile/session/CaptchaProviders/version/manifest.json",{version:2});
  assert.equal(collect(repo).evidenceFingerprint,before.evidenceFingerprint);
  review.result.message="Neuer protokollierter Prüfhinweis";write(repo,path.relative(repo,path.join(review.directory,"result.json")),review.result);
  assert.notEqual(collect(repo).evidenceFingerprint,before.evidenceFingerprint,"Echte Review-Nachweise bleiben an ihren Inhalt gebunden");
});
test("Ein tatsächlicher neuer LLM-Start entwertet das vorherige Zertifikat weiterhin",t=>{
  const repo=fixture(t);glm(repo);const before=collect(repo).evidenceFingerprint;
  write(repo,"testsweet/logs/llm-review-later/started.json",{reviewId:"later",startedAt:"2026-10-13T18:00:00Z"});
  const after=collect(repo);assert.notEqual(after.evidenceFingerprint,before);assert.equal(after.layers.find(layer=>layer.id === "GLM").state,"UNVERIFIED");
});
function twoClassHistory(repo,id,entries,state,time) {
  const reports=[];
  for (const [name,status] of entries) {
    const file=`target/test-evidence-history/${id}/TEST-${name}.xml`,raw=xmlSuite(name,status);write(repo,file,raw);reports.push({path:file,sha256:hash(raw)});
  }
  const marker={schemaVersion:1,kind:"JUNIT_RUN",module:"main",runId:id,state,startedAt:time,completedAt:time,
    expectedClasses:["example.GoldWerteTest","example.OtherTest"],buildEvidence:build(repo,"main"),reports};
  marker.sourceSha256=marker.buildEvidence.sourceSha256;
  write(repo,`target/test-evidence-history/${id}.json`,marker);write(repo,"target/test-evidence.json",marker);return marker;
}
test("Archivierter fehlgeschlagener Mavenlauf bleibt nach fremdem Teiltest sichtbar",t=>{
  const repo=fixture(t);write(repo,"src/test/java/example/OtherTest.java","package example; class OtherTest { @Test void one() {} }");
  twoClassHistory(repo,"old-full",[["example.GoldWerteTest","PASS"],["example.OtherTest","PASS"]],"COMPLETED","2026-10-13T11:00:00Z");
  twoClassHistory(repo,"failed",[["example.GoldWerteTest","FAIL"]],"INTERRUPTED","2026-10-13T12:00:00Z");
  twoClassHistory(repo,"other-only",[["example.OtherTest","PASS"]],"COMPLETED","2026-10-13T12:01:00Z");
  assert.equal(junit(repo,"main").state,"FAIL");assert.equal(junit(repo,"main").failures,1);
  twoClassHistory(repo,"corrected",[["example.GoldWerteTest","PASS"]],"COMPLETED","2026-10-13T12:02:00Z");assert.equal(junit(repo,"main").state,"PASS");
});
test("Abbruch ohne XML bleibt nach Teilerfolg unvollständig bis gesamter Umfang erneut geprüft ist",t=>{
  const repo=fixture(t);write(repo,"src/test/java/example/OtherTest.java","package example; class OtherTest { @Test void one() {} }");
  twoClassHistory(repo,"old-full",[["example.GoldWerteTest","PASS"],["example.OtherTest","PASS"]],"COMPLETED","2026-10-13T11:00:00Z");
  twoClassHistory(repo,"crashed",[],"UNVERIFIED","2026-10-13T12:00:00Z");
  twoClassHistory(repo,"partial",[["example.OtherTest","PASS"]],"COMPLETED","2026-10-13T12:01:00Z");
  assert.equal(junit(repo,"main").state,"UNVERIFIED");assert(junit(repo,"main").reasons.some(reason=>reason.includes("crashed")));
  twoClassHistory(repo,"remaining",[["example.GoldWerteTest","PASS"]],"COMPLETED","2026-10-13T12:02:00Z");assert.equal(junit(repo,"main").state,"PASS");
});
test("Echter JDK-Laufwriter archiviert fehlgeschlagene XML vor dem nächsten Teillauf",t=>{
  const repo=fixture(t);write(repo,"src/test/java/example/OtherTest.java","package example; class OtherTest { @Test void one() {} }");
  write(repo,"tools/BuildStamp.java",fs.readFileSync(path.resolve(__dirname,"../../tools/BuildStamp.java")));
  const launcher=path.join(repo,"target/writer-launcher"),cp=require("child_process");
  const compile=cp.spawnSync("javac",["-encoding","UTF-8","-d",launcher,path.join(repo,"tools/BuildStamp.java")],{encoding:"utf8",windowsHide:true,timeout:30000});
  assert.equal(compile.status,0,compile.stderr || compile.error?.message);
  const invoke=action=>{const result=cp.spawnSync("java",["-cp",launcher,"BuildStamp",action,repo,"main"],{encoding:"utf8",windowsHide:true,timeout:30000});assert.equal(result.status,0,result.stderr || result.error?.message);};
  invoke("stamp");twoClassHistory(repo,"old-full",[["example.GoldWerteTest","PASS"],["example.OtherTest","PASS"]],"COMPLETED","2000-01-01T00:00:00Z");
  invoke("test-begin");write(repo,"target/surefire-reports/TEST-example.GoldWerteTest.xml",xmlSuite("example.GoldWerteTest","FAIL"));
  invoke("test-begin");write(repo,"target/surefire-reports/TEST-example.OtherTest.xml",xmlSuite("example.OtherTest","PASS"));invoke("test-finish");
  const history=fs.readdirSync(path.join(repo,"target/test-evidence-history")).filter(name=>name.endsWith(".json")).map(name=>JSON.parse(fs.readFileSync(path.join(repo,"target/test-evidence-history",name),"utf8")));
  assert(history.some(run=>run.state === "INTERRUPTED" && run.reports.some(report=>report.path.includes("GoldWerteTest"))));assert.equal(junit(repo,"main").state,"FAIL");
  invoke("test-begin");write(repo,"target/surefire-reports/TEST-example.GoldWerteTest.xml",xmlSuite("example.GoldWerteTest","PASS"));invoke("test-finish");assert.equal(junit(repo,"main").state,"PASS");
});

function greenSuiteEvidence(repo) {
  for (const [index,key] of MATRIX.entries()) if (!["FACH:POSITIV","FACH:NEGATIV"].includes(key)) simulator(repo,"20261013-12000"+index,key);
  glm(repo);junitRun(repo,"main","suite-main");junitRun(repo,"testsweet","suite-own");
}
function suiteStatus(repo,id="one",state="RUNNING",extra={}) {
  const status={schemaVersion:1,kind:"CERTIFICATE_SUITE",jobId:id,startedAt:"2026-10-13T11:00:00Z",sourceSha256:digest(sources(repo)),state,...extra};
  if (["FINALIZING","SUCCESS"].includes(state)) {
    status.runDirectories=MATRIX.map(key=>{const [mode,category]=key.split(":");return {mode,category,directory:latestRuns(repo).selected[key]?.ordner};});
    status.glmReviewId="one";
  }
  write(repo,`testsweet/logs/certificate-suite-${id}/status.json`,status);return status;
}
test("Zertifikats-Suite sperrt alte grüne Belege unmittelbar vor dem ersten Mavenlauf",t=>{
  const repo=fixture(t);greenSuiteEvidence(repo);assert.equal(collect(repo).verdict,"BESTANDEN");
  const before=collect(repo).evidenceFingerprint;suiteStatus(repo,"new","RUNNING",{startedAt:"2026-10-13T14:00:00Z"});
  const result=collect(repo);assert.equal(result.verdict,"UNVOLLSTÄNDIG");assert.notEqual(result.evidenceFingerprint,before);
  assert(result.layers.find(layer=>layer.id==="CERTIFICATE_SUITE").reasons.some(reason=>reason.includes("JUnit main")));
});
test("Zertifikats-Suite unterscheidet bestätigten Testfehler von technischem Fehler und Abbruch",t=>{
  const repo=fixture(t);greenSuiteEvidence(repo);
  suiteStatus(repo,"new","FAILED",{failureKind:"TEST_FAILURE"});assert.equal(collect(repo).verdict,"NICHT BESTANDEN");
  suiteStatus(repo,"new","FAILED",{failureKind:"TECHNICAL"});assert.equal(collect(repo).verdict,"UNVOLLSTÄNDIG");
  suiteStatus(repo,"new","CANCELLED");assert.equal(collect(repo).verdict,"UNVOLLSTÄNDIG");
});
test("Zertifikats-Suite FINALIZING und SUCCESS binden denselben unveränderten Exportnachweis",t=>{
  const repo=fixture(t);greenSuiteEvidence(repo);suiteStatus(repo,"new","FINALIZING",{progress:99,phase:"PDF"});
  const ready=collect(repo);assert.equal(ready.verdict,"BESTANDEN",ready.warnungen.join("\n"));
  suiteStatus(repo,"new","SUCCESS",{progress:100,phase:"FINISHED",completedAt:"2026-10-13T14:01:00Z",certificateId:"published"});
  const success=collect(repo);assert.equal(success.verdict,"BESTANDEN");assert.equal(success.evidenceFingerprint,ready.evidenceFingerprint);
  assert.deepEqual(success.sourceInventory,ready.sourceInventory,"Suite-Laufdateien sind keine Quellen");
});
test("Zertifikats-Suite erlaubt keine erfundene Finalfreigabe mit alten Pflichtläufen",t=>{
  const repo=fixture(t);greenSuiteEvidence(repo);suiteStatus(repo,"new","SUCCESS",{startedAt:"2026-10-13T12:15:00Z"});
  const result=collect(repo);assert.equal(result.verdict,"UNVOLLSTÄNDIG");
  assert(result.layers.find(layer=>layer.id==="CERTIFICATE_SUITE").reasons.some(reason=>reason.includes("WEB:LAST")));
});
test("Zertifikats-Suite Quellwechsel und falsche gebundene Lauf-IDs verhindern Freigabe",t=>{
  const repo=fixture(t);greenSuiteEvidence(repo);suiteStatus(repo,"new","FINALIZING",{sourceSha256:"0".repeat(64)});
  assert.equal(collect(repo).layers.find(layer=>layer.id==="CERTIFICATE_SUITE").state,"UNVERIFIED");
  suiteStatus(repo,"new","FINALIZING",{evidenceIds:{junit:{main:"other",testsweet:"other"},runs:{},glmReviewId:"foreign"}});
  assert.equal(collect(repo).verdict,"UNVOLLSTÄNDIG");
});
test("Zertifikats-Suite fehlender oder beschädigter neuer Status verdeckt keinen Start",t=>{
  const repo=fixture(t);greenSuiteEvidence(repo);suiteStatus(repo,"old","SUCCESS");
  const file=write(repo,"testsweet/logs/certificate-suite-broken/status.json","{broken");
  assert.equal(collect(repo).verdict,"UNVOLLSTÄNDIG");fs.unlinkSync(file);assert.equal(collect(repo).verdict,"UNVOLLSTÄNDIG");
});
test("Zertifikats-Suite historischer Fehler wird nur durch einen neueren vollständigen Lauf ersetzt",t=>{
  const repo=fixture(t);greenSuiteEvidence(repo);suiteStatus(repo,"old","FAILED",{startedAt:"2026-10-13T10:00:00Z",failureKind:"TEST_FAILURE"});
  suiteStatus(repo,"new","FINALIZING");assert.equal(collect(repo).verdict,"BESTANDEN");
  suiteStatus(repo,"later","RUNNING",{startedAt:"2026-10-13T14:00:00Z"});assert.equal(collect(repo).verdict,"UNVOLLSTÄNDIG");
});
test("Zertifikats-Suite erlaubt kein JUnit-Carryover alter Einzeltests nach einem neuen Teillauf",t=>{
  const repo=fixture(t);greenSuiteEvidence(repo);write(repo,"src/test/java/example/OtherTest.java","package example; class OtherTest { @Test void one() {} }");
  // Belege nach Quelländerung neu binden, aber eine JUnit-Klasse liegt vor Suite-Start.
  greenSuiteEvidence(repo);
  fs.rmSync(path.join(repo,"target/test-evidence-history"),{recursive:true});
  twoClassHistory(repo,"old-full",[["example.GoldWerteTest","PASS"],["example.OtherTest","PASS"]],"COMPLETED","2026-10-13T10:00:00Z");
  twoClassHistory(repo,"new-partial",[["example.GoldWerteTest","PASS"]],"COMPLETED","2026-10-13T12:01:00Z");
  assert.equal(junit(repo,"main").state,"PASS");suiteStatus(repo,"new","FINALIZING");
  const result=collect(repo);assert.equal(result.verdict,"UNVOLLSTÄNDIG");assert(result.layers.find(layer=>layer.id==="CERTIFICATE_SUITE").reasons.some(reason=>reason.includes("Einzeltests")));
});
