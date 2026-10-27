"use strict";
const {test}=require("node:test"),assert=require("node:assert/strict"),fs=require("node:fs"),path=require("node:path"),os=require("node:os");
const runs=require("./runs"),report=require("./publish-report"),evidence=require("../testzertifikat-werkzeug/certificate-evidence");
const {Packer}=require("./report-library").docx,JSZip=require("../handbuch-werkzeug/node_modules/jszip");
const {recordedJUnit,reportMetrics}=require("./report-summary");
function write(repo,name,value){const file=path.join(repo,name);fs.mkdirSync(path.dirname(file),{recursive:true});fs.writeFileSync(file,typeof value==="string"?value:JSON.stringify(value));return file;}
function fixture(t){
  const root=fs.mkdtempSync(path.join(os.tmpdir(),"lmg-report-regression-"));
  t.after(()=>{const resolved=path.resolve(root);assert(resolved.startsWith(path.resolve(os.tmpdir())+path.sep));assert(path.basename(resolved).startsWith("lmg-report-regression-"));fs.rmSync(resolved,{recursive:true,force:true});});
  write(root,"tools/evidence-sources.txt","dir:src\ndir:testsweet/src\nfile:pom.xml\nfile:testsweet/pom.xml\n");
  write(root,"pom.xml","<project/>");write(root,"testsweet/pom.xml","<project/>");
  write(root,"src/test/java/example/GoldTest.java","package example; class GoldTest { @Test void one() {} }");
  write(root,"testsweet/src/test/java/example/SweetTest.java","package example; class SweetTest { @Test void one() {} }");
  write(root,"target/classes/Product.class","product");write(root,"testsweet/target/classes/Sweet.class","sweet");
  write(root,"doc/testreport-werkzeug/README.md","Report contract");
  return root;
}
function build(repo,module="testsweet"){
  const sourceInventory=evidence.sources(repo);
  return {schemaVersion:1,sourceInventory,sourceSha256:evidence.digest(sourceInventory),sourceArtifactsMatch:true,
    artifacts:(module==="main"?["product"]:["product","testsweet"]).map(name=>{
      const dir=path.join(repo,name==="product"?"target/classes":"testsweet/target/classes"),sha=evidence.classHash(dir);
      return {name,kind:"classes",path:dir,sha256:sha,classesSha256:sha,sourceMatch:true};
    })};
}
function run(repo,id,key="Fachschicht|ECHT",state="PASS",startedAt="2026-10-27T12:00:00Z"){
  const [mode,category]=key.split("|"),directory="testsweet/logs/run-"+id;
  write(repo,directory+"/testdokumentation.pdf","%PDF-CONTROLLED");write(repo,directory+"/testdokumentation.md","# Controlled SQL evidence");
  const data={schemaVersion:1,kind:"SIMULATOR_RUN",runId:"run-"+id,mode,category,state,startedAt,completedAt:state==="RUNNING"?null:startedAt,
    success:state==="PASS",totalCases:5,verifiedCases:state==="PASS"?5:0,failedCases:state==="FAIL"?5:0,verificationErrors:0,
    reportsComplete:true,logComplete:true,evidenceErrors:[],buildEvidence:build(repo),reports:["testdokumentation.pdf","testdokumentation.md"].map(file=>({path:file,sha256:evidence.hash(fs.readFileSync(path.join(repo,directory,file)))}))};
  write(repo,directory+"/run-state.json",data);return data;
}
function junit(repo,module="main",state="PASS",lifecycle="COMPLETED"){
  const prefix=module==="main"?"":"testsweet/",name=module==="main"?"example.GoldTest":"example.SweetTest",file=prefix+"target/test-evidence-history/current/TEST-"+name+".xml";
  const raw=`<testsuite name="${name}" tests="1" failures="${state==="FAIL"?1:0}" errors="0" skipped="${state==="SKIPPED"?1:0}"><testcase classname="${name}" name="one">${state==="FAIL"?"<failure>controlled</failure>":state==="SKIPPED"?"<skipped/>":""}</testcase></testsuite>`;
  write(repo,file,raw);
  const marker={schemaVersion:1,kind:"JUNIT_RUN",module,runId:"controlled-"+module,state:lifecycle,startedAt:"2026-10-27T12:01:00Z",completedAt:lifecycle==="COMPLETED"?"2026-10-27T12:01:01Z":null,
    expectedClasses:[name],buildEvidence:build(repo,module),reports:[{path:file,sha256:evidence.hash(raw)}]};
  write(repo,prefix+"target/test-evidence-history/current.json",marker);write(repo,prefix+"target/test-evidence.json",marker);return marker;
}
function published(repo){
  write(repo,"doc/SDN-Leistung-LMG-Testreport.pdf","%PDF-CONTROLLED");write(repo,"doc/SDN-Leistung-LMG-Testreport.docx","DOCX-CONTROLLED");
  const marker={schemaVersion:2,reportId:"CONTROLLED",inputInventory:runs.inputInventory(repo),inputFingerprint:runs.inputFingerprint(repo),
    pdfSha256:evidence.hash("%PDF-CONTROLLED"),docxSha256:evidence.hash("DOCX-CONTROLLED")};
  write(repo,"doc/testreport-werkzeug/stand-report.json",marker);return marker;
}
test("ein ECHT-PASS ersetzt keine fehlende Matrix und JUnit-FAIL hat Vorrang",t=>{
  const repo=fixture(t);run(repo,"20261027-120000-pass");junit(repo,"main","FAIL");
  const result=runs.reportStand(repo);assert.equal(result.verdict,"NICHT BESTANDEN");
  assert.equal(result.layers.length,12);assert.equal(result.layers.find(x=>x.id==="WEB|POSITIV").state,"UNVERIFIED");
  assert.equal(result.layers.find(x=>x.id.startsWith("GLM:")).state,"UNVERIFIED");
});
test("BESTANDEN verlangt ausschließlich vollständig positive Ebenen",()=>{
  const all=runs.MATRIX.map(id=>({id,state:"PASS"})).concat([{id:"JUnit main",state:"PASS"},{id:"JUnit sweet",state:"PASS"},{id:"GLM",state:"PASS"}]);
  assert.equal(runs.bewertung(all),"BESTANDEN");
  for(const state of ["UNVERIFIED","RUNNING","ABORTED"]){assert.equal(runs.bewertung([...all,{state}]),"UNVOLLSTÄNDIG");}
  assert.equal(runs.bewertung([...all,{state:"UNVERIFIED"},{state:"FAIL"}]),"NICHT BESTANDEN");assert.equal(runs.bewertung([]),"UNVOLLSTÄNDIG");
});
test("jüngerer FAIL derselben Sekunde gewinnt vor größerem Zufallssuffix",t=>{
  const repo=fixture(t);run(repo,"20261027-120000-999","Fachschicht|ECHT","PASS","2026-10-27T12:00:00.100Z");
  run(repo,"20261027-120000-111","Fachschicht|ECHT","FAIL","2026-10-27T12:00:00.900Z");
  const chosen=runs.letzteLaeufe(repo)[0].lauf;assert.equal(chosen.state,"FAIL");assert.match(chosen.ordner,/111$/);
});
test("gleiche Zeit bleibt mehrdeutig und bewahrt bekannte Fehler",t=>{
  const repo=fixture(t);run(repo,"20261027-120000-a");run(repo,"20261027-120000-b");
  assert.equal(runs.letzteLaeufe(repo)[0].lauf.state,"UNVERIFIED");
  run(repo,"20261027-120000-c","Fachschicht|ECHT","FAIL");assert.equal(runs.letzteLaeufe(repo)[0].lauf.state,"FAIL");
});
test("null erfolgreiche Verifikationen verharmlosen FAIL nicht zum Abbruch",t=>{
  const repo=fixture(t);run(repo,"20261027-120000-fail","Fachschicht|ECHT","FAIL");
  assert.equal(runs.letzteLaeufe(repo)[0].lauf.state,"FAIL");assert.equal(report.stateLabel("FAIL"),"FEHLGESCHLAGEN");
});
test("Status hat Vorrang vor alter grüner Textzusammenfassung",t=>{
  const repo=fixture(t);run(repo,"20261027-120000-fail","FACHSCHICHT|ECHT","FAIL");
  write(repo,"testsweet/logs/run-20261027-120000-fail/zusammenfassung.txt","Lauf erfolgreich : true\nverifiziert : 5");
  const chosen=runs.letzteLaeufe(repo)[0].lauf;assert.equal(chosen.modus,"Fachschicht");assert.equal(chosen.state,"FAIL");
});
test("Abbruch und laufender Versuch bleiben getrennt",t=>{
  const repo=fixture(t);run(repo,"20261027-120000-abort","Fachschicht|ECHT","ABORTED");run(repo,"20261027-120001-running","Fachschicht|RECHNER","RUNNING","2026-10-27T12:00:01Z");
  assert.deepEqual(runs.letzteLaeufe(repo).map(x=>x.lauf.state),["ABORTED","RUNNING"]);
});
test("defekter unzugeordneter Lauf verschwindet nicht aus dem Report",t=>{
  const repo=fixture(t);write(repo,"testsweet/logs/run-broken/run-state.json","{broken");
  const result=runs.reportStand(repo);assert(result.layers.some(x=>x.id==="Unzugeordnete Läufe"));assert.notEqual(result.verdict,"BESTANDEN");
});
test("JUnit nutzt laufgebundene Reports statt alte target XML zu summieren",t=>{
  const repo=fixture(t);junit(repo);write(repo,"target/surefire-reports/TEST-stale.xml",'<testsuite tests="30" failures="0" errors="0" skipped="0"/>');
  const result=runs.junitStand(repo).Hauptprojekt;assert.equal(result.tests,1);assert.equal(result.state,"PASS");
});
test("laufendes oder übersprungenes JUnit ist nicht BESTANDEN",t=>{
  const repo=fixture(t);junit(repo,"main","PASS","RUNNING");assert.equal(runs.junitStand(repo).Hauptprojekt.state,"UNVERIFIED");
  junit(repo,"main","SKIPPED");const result=runs.junitStand(repo).Hauptprojekt;assert.equal(result.state,"UNVERIFIED");assert.equal(result.skipped,1);
});
test("widersprüchliche XML-Zähler werden abgewiesen",t=>{
  const repo=fixture(t),marker=junit(repo);const file=marker.reports[0].path;
  write(repo,file,'<testsuite name="example.GoldTest" tests="10" failures="0" errors="0" skipped="0"><testcase name="one"/></testsuite>');
  marker.reports[0].sha256=evidence.hash(fs.readFileSync(path.join(repo,file)));
  write(repo,"target/test-evidence-history/current.json",marker);write(repo,"target/test-evidence.json",marker);
  assert.equal(runs.junitStand(repo).Hauptprojekt.state,"UNVERIFIED");
});
test("Quelländerung und veränderte Lauf-PDF verhindern PASS",t=>{
  const repo=fixture(t);run(repo,"20261027-120000-pass");write(repo,"src/test/java/example/GoldTest.java","changed");
  assert.equal(runs.letzteLaeufe(repo)[0].lauf.state,"UNVERIFIED");
  write(repo,"testsweet/logs/run-20261027-120000-pass/testdokumentation.pdf","modified");assert.equal(runs.letzteLaeufe(repo)[0].lauf.erfolgreich,false);
});
test("Verify bindet PDF, DOCX und aktuelle Eingaben und liefert den PDF-Hash",t=>{
  const repo=fixture(t),marker=published(repo);assert.equal(report.pruefe(repo).pdfSha256,marker.pdfSha256);
  run(repo,"20261027-120000-newfail","Fachschicht|ECHT","FAIL");assert.throws(()=>report.pruefe(repo),/eingaben/i);
});
test("Verify weist veränderte Datei und alten Hashmarker ohne Inputbindung ab",t=>{
  const repo=fixture(t);published(repo);write(repo,"doc/SDN-Leistung-LMG-Testreport.pdf","changed");assert.throws(()=>report.pruefe(repo),/Reportdateien/);
  write(repo,"doc/testreport-werkzeug/stand-report.json",{pdfSha256:evidence.hash("changed")});assert.throws(()=>report.pruefe(repo),/Eingabebindung/);
});
test("eigenes Werkzeug und Fachbelege sind gebunden, Ausgaben nicht",t=>{
  const repo=fixture(t);const before=runs.inputFingerprint(repo);
  write(repo,"doc/SDN-Leistung-LMG-Testreport.pdf","new output");write(repo,"doc/testreport-werkzeug/stand-report.json",{});assert.equal(runs.inputFingerprint(repo),before);
  write(repo,"doc/testreport-werkzeug/README.md","changed contract");assert.notEqual(runs.inputFingerprint(repo),before);
  const second=runs.inputFingerprint(repo);write(repo,"doc/belege/source.png","changed evidence");assert.notEqual(runs.inputFingerprint(repo),second);
});
test("Exportguard erkennt Eingabeänderung während des Exports",t=>{
  const repo=fixture(t),before=runs.inputFingerprint(repo);write(repo,"testsweet/logs/run-new/run-state.json",{});assert.throws(()=>report.assertInputs(repo,before),/während des Exports/);
});
const headings=["Einleitung","1 Testarten","2 Zwei","3 Herkunft","4 Quellenbasierte","5 Unabhängige","6 Transaktionssteuerung","7 Ergebnisübersicht","8 Fazit","Gesamtbewertung des Prüfumfangs"];
test("TOC akzeptiert eingerückte und verschobene Kapitel",()=>{
  const raw="title\fcontents\fextra\f"+headings.map(x=>"  "+x).join("\f");assert.deepEqual(report.chapterPages(raw),[4,5,6,7,8,9,10,11,12,13]);
});
test("TOC erlaubt fließende Kapitel auf derselben Seite und prüft ihre Textreihenfolge",()=>{
  assert.deepEqual(report.chapterPages("title\fcontents\f"+headings.join("\n")),headings.map(()=>3));
  const swapped=[...headings];[swapped[3],swapped[4]]=[swapped[4],swapped[3]];
  assert.throws(()=>report.chapterPages("title\fcontents\f"+swapped.join("\n")),/reihenfolge/i);
  assert.throws(()=>report.chapterPages("title\fcontents\f"+headings.concat(headings[0]).join("\n")),/mehrdeutig/);
});
test("TOC verweigert fehlende, doppelte oder vertauschte Anker",()=>{
  assert.throws(()=>report.chapterPages("title\fcontents\fno headings"),/Kapitelanker/);
  assert.throws(()=>report.chapterPages("title\fcontents\f"+headings.concat(headings[0]).join("\f")),/mehrdeutig/);
  const swapped=[...headings];[swapped[0],swapped[1]]=[swapped[1],swapped[0]];assert.throws(()=>report.chapterPages("title\fcontents\f"+swapped.join("\f")),/reihenfolge/i);
});
test("DOCX stellt bekannte Fehler und fehlende Ebenen ohne positive Pauschalbestätigung dar",async t=>{
  const repo=fixture(t);run(repo,"20261027-120000-pass");junit(repo,"main","FAIL");
  const doc=report.baueDokument(repo,"27.10.2026","CONTROLLED",[3,4,5,6,7,8,9,10,11,12]);
  const zip=await JSZip.loadAsync(await Packer.toBuffer(doc)),xml=await zip.file("word/document.xml").async("string");
  assert.match(xml,/Gesamtbewertung: NICHT BESTANDEN/);assert.match(xml,/UNVOLLSTÄNDIG/);assert.match(xml,/FEHLGESCHLAGEN/);
  assert.doesNotMatch(xml,/Hiermit wird bestätigt|Über alle Ebenen hinweg ergibt sich ein konsistentes Bild|Zehntausende/);
  assert.equal((xml.match(/<w:drawing>/g)||[]).length,4);
  assert.match(xml,/Zufallsgenerator/);assert.match(xml,/keine vollständige Prüfung/);
});

test("archivierte Laufzahlen bleiben nach Quellenänderung lesbar ohne aktuellen PASS",t=>{
  const repo=fixture(t);junit(repo,"main");junit(repo,"testsweet","SKIPPED");
  write(repo,"src/test/java/example/GoldTest.java","package example; class GoldTest { @Test void changed() {} }");
  const stand=runs.reportStand(repo),metrics=reportMetrics(repo,stand);
  assert.equal(stand.verdict,"UNVOLLSTÄNDIG");assert.equal(metrics.complete,true);
  assert.deepEqual(metrics.totals,{tests:2,executed:1,passed:1,failures:0,errors:0,skipped:1});
  assert(metrics.modules.every(x=>x.recorded.current===false));
});
test("aktueller JUnit-Prüfstand zählt Voll- und Nachholbelege ohne Doppelzählung",t=>{
  const repo=fixture(t);
  write(repo,"src/test/java/example/ConfigTest.java","package example; class ConfigTest { @Test void configured() {} }");
  junit(repo,"testsweet");
  const classes=["example.GoldTest","example.ConfigTest"];
  function archive(id,startedAt,suites){
    const reports=suites.map(([name,method,skipped])=>{
      const raw=`<testsuite name="${name}" tests="1" failures="0" errors="0" skipped="${skipped?1:0}"><testcase classname="${name}" name="${method}">${skipped?"<skipped/>":""}</testcase></testsuite>`;
      const file=`target/test-evidence-history/${id}/TEST-${name}.xml`;
      write(repo,file,raw);return {path:file,sha256:evidence.hash(raw)};
    });
    const marker={schemaVersion:1,kind:"JUNIT_RUN",module:"main",runId:id,state:"COMPLETED",startedAt,
      completedAt:startedAt.replace(":00Z",":01Z"),expectedClasses:classes,buildEvidence:build(repo,"main"),reports};
    write(repo,`target/test-evidence-history/${id}.json`,marker);
    write(repo,"target/test-evidence.json",marker);return marker;
  }
  archive("full","2026-10-26T12:00:00Z",[[classes[0],"one",false],[classes[1],"configured",true]]);
  const focused=archive("configuration-followup","2026-10-27T12:00:00Z",[[classes[1],"configured",false]]);
  const strict=evidence.junit(repo,"main");
  assert.equal(strict.state,"PASS");assert.equal(strict.tests,2);assert.equal(strict.skipped,0);
  assert.equal(strict.marker.reports.length,1,"Der letzte Teillauf allein enthält nicht das Gesamtinventar");
  const recorded=recordedJUnit(repo,strict);
  assert.equal(recorded.tests,2);assert.equal(recorded.executed,2);assert.equal(recorded.passed,2);
  assert.equal(recorded.evidenceKind,"CURRENT_INVENTORY");assert.equal(recorded.current,true);
  assert.equal(recorded.date,focused.startedAt);assert.equal(recorded.dateKind,"LATEST_RUN_START");
  assert.equal(recorded.runId,null,"Der zusammengesetzte Umfang darf keine einzelne Lauf-ID vortäuschen");
  assert.equal(recorded.latestRunId,focused.runId);
  const metrics=reportMetrics(repo,runs.reportStand(repo));
  assert.equal(metrics.complete,true);assert.equal(metrics.totals.tests,3);assert.equal(metrics.totals.executed,3);
  write(repo,focused.reports[0].path,"<testsuite/>");
  const broken=evidence.junit(repo,"main");
  assert.notEqual(broken.state,"PASS");assert.equal(recordedJUnit(repo,broken),null);
});
test("archivierte Zähler verlangen vollständige unveränderte XML-Belege",t=>{
  const repo=fixture(t),marker=junit(repo),item={marker,state:"UNVERIFIED"};
  assert.equal(recordedJUnit(repo,item).tests,1);
  marker.reports.push({...marker.reports[0]});assert.equal(recordedJUnit(repo,item),null);marker.reports.pop();
  write(repo,marker.reports[0].path,"<testsuite/>");assert.equal(recordedJUnit(repo,item),null);
  assert.equal(recordedJUnit(repo,{marker:{...marker,state:"RUNNING"}}),null);
  assert.equal(recordedJUnit(repo,{}),null);
});
test("fehlender Modulbeleg wird in der Einleitung nicht als vollständiger Gesamtumfang ausgegeben",async t=>{
  const repo=fixture(t);junit(repo,"main");
  const zip=await JSZip.loadAsync(await Packer.toBuffer(report.baueDokument(repo,"27.10.2026","CONTROLLED",null)));
  const xml=await zip.file("word/document.xml").async("string");
  assert.match(xml,/Für einen vollständigen gemeinsamen Zähler fehlen archivierte Laufbelege/);
  assert.doesNotMatch(xml,/insgesamt 1 registrierte Prüffälle/);
  assert.doesNotMatch(xml,/JUnit-Testmethode fehlt:/);
  assert.match(xml,/erwartete Methoden fehlt ein aktuell gültiger Einzelbeleg/);
});
test("GLM-Fallzahl bleibt bei fachlichem FAIL sichtbar und ist ohne Beleg keine Null",async t=>{
  const repo=fixture(t),{Document}=require("./report-library").docx;
  async function tableText(evidence){
    const stand={junit:{},layers:[{id:"GLM: Stichprobe",state:"FAIL",reasons:[],evidence}]};
    const zip=await JSZip.loadAsync(await Packer.toBuffer(new Document({sections:[{children:report.ergebnisTabelle(repo,stand)}]})));
    const xml=await zip.file("word/document.xml").async("string");
    return [...xml.matchAll(/<w:t(?:\s[^>]*)?>(.*?)<\/w:t>/g)].map(x=>x[1]);
  }
  const verified=await tableText({valid:false,evidenceVerified:true,meta:{pruefumfang:"2/2 vollständige Fälle (Stichprobe)"}});
  assert.equal(verified.filter(x=>x==="2").length,2);assert(verified.includes("FEHLGESCHLAGEN"));
  const missing=await tableText({valid:false,evidenceVerified:false,cases:[{},{}]});
  assert.equal(missing.filter(x=>x==="nicht belegt").length,2);assert(!missing.includes("0"));
});

// Prozessfehler und Publisher-Transaktion mit deterministischem externen Satz.
function publisherWith(spawn){
  const vm=require("node:vm"),{createRequire}=require("node:module"),file=path.join(__dirname,"publish-report.js"),local=createRequire(file);
  const context={module:{exports:{}},exports:{},__dirname,process:{pid:process.pid,kill:process.kill.bind(process),argv:[],stdout:{write(){}}},console,
    require:name=>name==="child_process"?{spawnSync:spawn}:local(name)};
  vm.runInNewContext(fs.readFileSync(file,"utf8"),context,{filename:file});return context.module.exports;
}
function fakeOffice(secondShift=false){
  let extraction=0;
  return (program,args)=>{
    if(program==="pdftotext"){
      extraction++;return {status:0,stdout:"title\fcontents\f"+(secondShift&&extraction===2?"inserted\f":"")+headings.join("\f"),stderr:""};
    }
    const output=args[args.indexOf("--outdir")+1],input=args.at(-1);
    fs.writeFileSync(path.join(output,path.basename(input,".docx")+".pdf"),"%PDF-CONTROLLED");
    return {status:0,stdout:"",stderr:""};
  };
}
test("fehlendes pdftotext oder soffice bleibt ein Exportfehler",t=>{
  const repo=fixture(t),missing=publisherWith(()=>({status:null,error:new Error("ENOENT controlled"),stdout:null,stderr:null}));
  assert.throws(()=>missing.seitenErmitteln(path.join(repo,"missing.pdf")),/pdftotext.*ENOENT/);
});
test("Publisher veröffentlicht gebundene Momentaufnahme und Verify bestätigt ihre Bytes",async t=>{
  const repo=fixture(t),tool=publisherWith(fakeOffice());
  const result=await tool.veroeffentliche(repo);
  assert.equal(result.state,"READY");assert.equal(result.verdict,"UNVOLLSTÄNDIG");
  const verified=tool.pruefe(repo);assert.equal(verified.pdfSha256,evidence.hash(fs.readFileSync(path.join(repo,"doc/SDN-Leistung-LMG-Testreport.pdf"))));
  assert.equal(fs.existsSync(path.join(repo,"doc/testreport-werkzeug/publish.lock")),false);
});
test("geänderte Kapitelstartseiten im zweiten Satz verhindern Veröffentlichung",async t=>{
  const repo=fixture(t);published(repo);const before=fs.readFileSync(path.join(repo,"doc/SDN-Leistung-LMG-Testreport.pdf"));
  const tool=publisherWith(fakeOffice(true));await assert.rejects(tool.veroeffentliche(repo),/zweiten Durchlauf/);
  assert.deepEqual(fs.readFileSync(path.join(repo,"doc/SDN-Leistung-LMG-Testreport.pdf")),before);
  assert.equal(fs.existsSync(path.join(repo,"doc/testreport-werkzeug/publish.lock")),false);
});
test("fehlendes Office lässt Altdateien bestehen und setzt die Exportsperre zurück",async t=>{
  const repo=fixture(t);published(repo);const before=fs.readFileSync(path.join(repo,"doc/SDN-Leistung-LMG-Testreport.pdf"));
  const tool=publisherWith(()=>({status:null,error:new Error("ENOENT soffice controlled"),stdout:null,stderr:null}));
  await assert.rejects(tool.veroeffentliche(repo),/soffice.*ENOENT/);
  assert.deepEqual(fs.readFileSync(path.join(repo,"doc/SDN-Leistung-LMG-Testreport.pdf")),before);
  assert.equal(fs.existsSync(path.join(repo,"doc/testreport-werkzeug/publish.lock")),false);
});

test("lebender Export bleibt gesperrt, abgebrochener Prozess hinterlässt keine dauerhafte Sperre",async t=>{
  const repo=fixture(t),tool=publisherWith(fakeOffice()),lock="doc/testreport-werkzeug/publish.lock";
  write(repo,lock,{pid:process.pid,token:"controlled-live"});
  await assert.rejects(tool.veroeffentliche(repo),/läuft bereits/);
  const completed=require("node:child_process").spawnSync(process.execPath,["-e","process.stdout.write(String(process.pid))"],{encoding:"utf8"});
  assert.equal(completed.status,0);write(repo,lock,{pid:Number(completed.stdout),token:"controlled-ended"});
  assert.equal((await tool.veroeffentliche(repo)).state,"READY");assert.equal(fs.existsSync(path.join(repo,lock)),false);
});
