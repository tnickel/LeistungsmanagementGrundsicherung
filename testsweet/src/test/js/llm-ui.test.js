"use strict";
const assert = require("node:assert/strict");
const {test} = require("node:test");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");

// Führt die gesamte produktive Browserdatei aus. DOMContentLoaded wird bewusst
// erst registriert: Netzwerk und Uhren gehören je Gegenprobe kontrolliert dem Test.
const source = fs.readFileSync(path.resolve(__dirname,"../../main/resources/sweet/app.js"),"utf8");
function browser() {
  const elements = new Map(), storage = new Map(), callbacks = new Map(), responses = [];
  function element(id) {
    if (elements.has(id)) return elements.get(id);
    const classes = new Set();
    const value = {id,textContent:"",innerHTML:"",style:{},children:[],hidden:false,disabled:false,open:false,shown:0,
      className:"",value:0,max:100,
      classList:{toggle(name,on){if(on)classes.add(name);else classes.delete(name);},add(name){classes.add(name);},remove(name){classes.delete(name);},contains(name){return classes.has(name);}},
      replaceChildren(){this.children=[];},appendChild(child){this.children.push(child);},
      removeAttribute(name){delete this[name];},showModal(){this.open=true;this.shown++;},close(){this.open=false;},
      listeners:new Map(),addEventListener(name,callback){this.listeners.set(name,callback);},querySelectorAll(){return [];}};
    elements.set(id,value);return value;
  }
  const context = vm.createContext({
    window:{addEventListener(name,callback){callbacks.set(name,callback);}},
    document:{getElementById:element,createElement:tag=>({tag,textContent:"",innerHTML:""})},
    sessionStorage:{getItem:key=>storage.get(key) || null,setItem:(key,value)=>storage.set(key,value)},
    fetch:async (url,options)=>{assert.ok(responses.length,"Ungeplanter HTTP-Aufruf: "+url);const queued=responses.shift();assert.equal(url,queued.url);return typeof queued.response === "function" ? queued.response(options) : queued.response;},
    setInterval:()=>1,clearInterval:()=>{},console,alert:()=>{},
  });
  vm.runInContext(source,context,{filename:"sweet/app.js"});
  assert.equal(typeof callbacks.get("DOMContentLoaded"),"function");
  return {element,context,storage,
    installReport(){
      const begin=source.indexOf('    const reportKnopf = $("testreportknopf");');
      const end=source.indexOf('    initialisiereLlm();',begin);
      assert.ok(begin>0&&end>begin);vm.runInContext(source.slice(begin,end),context);
    },
    report(){return element("testreportknopf").listeners.get("click")();},
    simulator(data){context.simulatorFixture=data;return vm.runInContext("zeigeSimulatorFehler(simulatorFixture)",context);},
    response(data,ok=true,url="/api/llmpruefung"){responses.push({url,response:{ok,json:async()=>data}});},
    deferred(promise,url="/api/llmpruefung"){responses.push({url,response:()=>promise});},
    poll(){return vm.runInContext("ladeLlmPruefung()",context);},
    suite(data){context.suiteFixture=data;return vm.runInContext("renderZertifikatSuite(suiteFixture)",context);},
    suitePoll(){return vm.runInContext("ladeZertifikatSuite()",context);},
    suiteStart(){return vm.runInContext("starteZertifikatSuite()",context);},
    suiteStop(){return vm.runInContext("stoppeZertifikatSuite()",context);},
    refreshSimulator(){return vm.runInContext("aktualisiere()",context);},
  };
}
const status = (extra={}) => ({reviewId:"review-1",state:"PASS",running:false,model:"glm-5.3",caseVerdict:"PASS",
  transportVerified:true,calculationVerified:true,reviewCoverageVerified:true,totalCases:100,completedCases:100,
  totalBatches:34,completedBatches:34,hasFailures:false,findings:[],reportPdf:"/api/llm/report/pdf",
  artifactDirectory:"D:\\testsweet\\logs\\llm-review-1",...extra});

test("fachlicher FAIL öffnet ohne ausgeschöpfte Retries einen auffälligen Dialog mit Fall und Befund",async()=>{
  const ui=browser();
  ui.response(status({state:"FAIL",caseVerdict:"FAIL",hasFailures:true,retryExhausted:false,
    findings:[{caseId:"NEGATIV:run-42:TF9",description:"Budget wurde trotz abgewiesener Zahlung gemindert",reasoning:"1000.00 EUR vorher, 137.00 EUR nachher"}]}));
  await ui.poll();
  assert.equal(ui.element("llm-retry-dialog").shown,1);
  assert.match(ui.element("llm-retry-title").textContent,/NICHT BESTANDEN/);
  assert.match(ui.element("llm-retry-message").textContent,/NEGATIV:run-42:TF9.*Budget wurde/);
  assert.match(ui.element("llm-retry-log").textContent,/llm-review-1\\protocol\.md$/);
  assert.equal(ui.element("llm-status").className,"nok");
  assert.equal(ui.element("llm-transport-check").className,"ok"); // Transport ist getrennt vom Fachurteil.
});

test("dreimalige Formatfehler nennen Versuchsanzahl und Logauswertung bei tatsächlichem Teilfortschritt",async()=>{
  const ui=browser();
  ui.response(status({state:"ERROR",caseVerdict:"UNVERIFIED",retryExhausted:true,transportVerified:false,
    calculationVerified:false,reviewCoverageVerified:false,completedCases:9,completedBatches:3,message:"Fall-ID-Liste unvollständig"}));
  await ui.poll();
  assert.equal(ui.element("llm-retry-dialog").shown,1);
  assert.match(ui.element("llm-retry-message").textContent,/3 Versuche fehlgeschlagen.*Logfile auswerten/s);
  assert.equal(ui.element("llm-progress").value,9);
  assert.equal(ui.element("llm-progress-prozent").textContent,"9 %");
  assert.equal(ui.element("llm-transport-check").className,"hinweis");
});

test("UNVERIFIED erzeugt einen Dialog und keine grüne Fachbestätigung",async()=>{
  const ui=browser();
  ui.response(status({state:"UNVERIFIED",caseVerdict:"UNVERIFIED",calculationVerified:false,
    findings:[{caseId:"POSITIV:run-42:TF3",description:"Zahlungsmonat fehlt, Schwangerschaftsende nicht prüfbar"}]}));
  await ui.poll();
  assert.equal(ui.element("llm-retry-dialog").shown,1);
  assert.match(ui.element("llm-retry-title").textContent,/unvollständig/);
  assert.equal(ui.element("llm-status").className,"nok");
  assert.match(ui.element("llm-retry-message").textContent,/Schwangerschaftsende nicht prüfbar/);
});

test("bekannter fachlicher Befund bleibt bei späterem technischem ERROR sichtbar",async()=>{
  const ui=browser();
  ui.response(status({state:"ERROR",caseVerdict:"FAIL",hasFailures:true,transportVerified:false,
    findings:[{caseId:"TF10",description:"Auszahlungsbetrag falsch",reasoning:"Referenz 863.00; Ist 864.00"}],message:"HTTP 503"}));
  await ui.poll();
  assert.match(ui.element("llm-retry-title").textContent,/NICHT BESTANDEN/);
  assert.match(ui.element("llm-retry-message").textContent,/HTTP 503.*TF10: Auszahlungsbetrag falsch/s);
  assert.match(ui.element("llm-findings").children[0].textContent,/Referenz 863\.00; Ist 864\.00/);
});

test("Befunddetails werden als Text einschließlich Rechenbegründung ausgegeben",async()=>{
  const ui=browser();
  ui.response(status({state:"FAIL",caseVerdict:"FAIL",hasFailures:true,
    findings:[{caseId:"TF<script>",description:"<img src=x onerror=alert(1)> Zahlbetrag falsch",reasoning:"563.00 + 400.00 - 100.00 = 863.00"}]}));
  await ui.poll();
  const list=ui.element("llm-findings");
  assert.equal(list.hidden,false);assert.equal(list.children.length,1);
  assert.match(list.children[0].textContent,/TF<script>.*<img/);
  assert.match(list.children[0].textContent,/Rechnung: 563\.00 \+ 400\.00 - 100\.00 = 863\.00/);
  assert.equal(list.children[0].innerHTML,"");
});

test("fehlgeschlagene Statusabfrage nimmt vorherige grüne Bestätigung und Links zurück",async()=>{
  const ui=browser();
  ui.response(status());await ui.poll();
  assert.equal(ui.element("llm-status").className,"ok");
  assert.equal(ui.element("llm-transport-check").className,"ok");
  assert.equal(ui.element("llm-report-link").style.display,"inline-block");
  ui.element("zertifikatlink").style.display="inline-block";
  ui.response({fehler:"Nachweisdatei nicht lesbar"},false);await ui.poll();
  assert.equal(ui.element("llm-status").className,"nok");
  assert.equal(ui.element("llm-transport-check").className,"nok");
  assert.match(ui.element("llm-transport-check").textContent,/nicht bestätigt/);
  assert.equal(ui.element("llm-report-link").style.display,"none");
  assert.equal(ui.element("zertifikatlink").style.display,"none");
});

test("verspätete alte PASS-Antwort kann neueren FAIL nicht überschreiben",async()=>{
  const ui=browser();let completeOld;
  ui.deferred(new Promise(resolve=>{completeOld=resolve;}));
  const old=ui.poll();
  ui.response(status({state:"FAIL",caseVerdict:"FAIL",hasFailures:true,findings:[{caseId:"TF1",description:"Neuer Befund"}]}));
  await ui.poll();
  completeOld({ok:true,json:async()=>status()});await old;
  assert.equal(ui.element("llm-status").className,"nok");
  assert.match(ui.element("llm-status").textContent,/NICHT BESTANDEN/);
  assert.match(ui.element("llm-findings").children[0].textContent,/Neuer Befund/);
});

test("derselbe Befund öffnet je Review nur einmal, ein neuer Review erneut",async()=>{
  const ui=browser(),failure=status({state:"FAIL",caseVerdict:"FAIL",hasFailures:true,findings:[{caseId:"TF1",description:"Fehler"}]});
  ui.response(failure);await ui.poll();ui.element("llm-retry-dialog").close();
  ui.response(failure);await ui.poll();assert.equal(ui.element("llm-retry-dialog").shown,1);
  ui.response({...failure,reviewId:"review-2"});await ui.poll();assert.equal(ui.element("llm-retry-dialog").shown,2);
});

test("PASS ohne bestätigte Nachrechnung bleibt sichtbar unvollständig",async()=>{
  const ui=browser();ui.response(status({calculationVerified:false}));await ui.poll();
  assert.equal(ui.element("llm-status").className,"nok");
  assert.match(ui.element("llm-status").textContent,/unvollständig/);
  assert.equal(ui.element("llm-retry-dialog").shown,1);
});

const staleBuild = "Build-/Quellstand nicht nachgewiesen: geladenes Produkt- oder Testartefakt passt nicht zu den aktuellen Quellen";
const simulatorStatus = (extra={}) => ({phase:"FEHLGESCHLAGEN",logVerzeichnis:"D:\\testsweet\\logs\\run-42",
  kennzahlen:{VERIFIZIERT:60},globalePruefungen:[{ok:true}],nachweisFehler:[staleBuild],letzteFehlermeldung:staleBuild,
  review:{state:"UNVERIFIED",automatedState:"UNVERIFIED",llmState:"ERROR",errors:[staleBuild,
    "Automatische Fach-/SQL-Prüfung ist wegen fehlender oder ungültiger Nachweise nicht bestätigt.",
    "KI-Prüfung: ERROR. Buildnachweis der POSITIV-Quelle fehlt oder ist veraltet: run-41"]},...extra});

test("Buildsperre erklärt Fehlerart und Wiederanlauf, statt einen Rechenfehler zu behaupten",()=>{
  const ui=browser();ui.simulator(simulatorStatus());
  assert.match(ui.element("llm-retry-title").textContent,/Technischer Nachweisfehler/);
  assert.match(ui.element("llm-retry-severity").textContent,/Aktion erforderlich/);
  assert.match(ui.element("llm-retry-summary").textContent,/Programmstand und Quelldateien/s);
  assert.match(ui.element("llm-retry-impact").textContent,/keinen Rechenfehler und keinen GLM-Ausfall/);
  assert.equal(ui.element("llm-retry-auto").hidden,false);
  const steps=ui.element("llm-retry-steps").children.map(c=>c.textContent).join("\n");
  assert.match(steps,/stop\.bat.*mvn clean install.*start\.bat.*Positiv- und Negativ.*beide automatischen Testsuiten.*alle sieben.*GLM.*Zertifikat/s);
  assert.match(steps,/Falls dieselbe Meldung.*Laufprotokoll.*Entwicklung/s);
  assert.equal(ui.element("llm-retry-message").textContent.split(staleBuild).length-1,1);
  assert.equal(ui.element("llm-retry-details").open,false);
  assert.equal(ui.element("llm-retry-log").textContent,"D:\\testsweet\\logs\\run-42");
});

test("zusätzlicher Testfehler wird durch den Buildhinweis nicht verharmlost",()=>{
  const ui=browser();ui.simulator(simulatorStatus({kennzahlen:{VERIFIKATION_FEHLGESCHLAGEN:1},
    review:{state:"FAIL",errors:[staleBuild,"TF1: Betrag ist 99 statt 100"]}}));
  assert.match(ui.element("llm-retry-title").textContent,/Testfehler und/);
  assert.match(ui.element("llm-retry-impact").textContent,/Zusätzlich liegen Testabweichungen/);
  assert.doesNotMatch(ui.element("llm-retry-impact").textContent,/keinen Rechenfehler/);
  assert.match(ui.element("llm-retry-message").textContent,/TF1: Betrag ist 99 statt 100/);
});

test("SQL-Abweichung bekommt fachliche Untersuchung statt pauschalen Neustart",()=>{
  const ui=browser();ui.simulator(simulatorStatus({nachweisFehler:[],letzteFehlermeldung:"TF7: Zahlung fehlt",
    review:{state:"FAIL",errors:["Automatische Fach-/SQL-Prüfung hat Fehler festgestellt."]}}));
  assert.match(ui.element("llm-retry-title").textContent,/NICHT BESTANDEN/);
  const steps=ui.element("llm-retry-steps").children.map(c=>c.textContent).join("\n");
  assert.match(steps,/Soll-\/Ist|Befunde/);
  assert.match(steps,/Ursache anhand der Fall-ID/);
  assert.doesNotMatch(steps,/mvn clean install/);
});

test("Abbruch wird als ungeprüft erklärt, erfolgreiche Läufe öffnen keinen Fehlerdialog",()=>{
  const ui=browser();ui.simulator(simulatorStatus({phase:"ABGEBROCHEN",nachweisFehler:[],letzteFehlermeldung:"Vom Benutzer gestoppt",
    review:{state:"UNVERIFIED",errors:[]}}));
  assert.match(ui.element("llm-retry-severity").textContent,/ABBRUCH/);
  assert.match(ui.element("llm-retry-summary").textContent,/ungeprüft/);
  assert.equal(ui.element("llm-retry-dialog").classList.contains("prueffehler-abbruch"),true);
  const good=browser();good.simulator(simulatorStatus({phase:"FERTIG",nachweisFehler:[],review:{state:"PASS"}}));
  assert.equal(good.element("llm-retry-dialog").shown,0);
});

test("GLM mit veralteter Quelle nennt Neubuild, echte Retry-Erschöpfung die drei Versuche",async()=>{
  const stale=browser();stale.response(status({state:"ERROR",caseVerdict:"UNVERIFIED",
    buildZeit:"2026-10-15T02:20:00Z",
    message:"Buildnachweis der POSITIV-Quelle fehlt oder ist veraltet: run-20261015-013136-797689601207190729"}));
  await stale.poll();
  assert.match(stale.element("llm-retry-title").textContent,/Eingangsbelege veraltet/);
  // Beide Dialog-Pfade (GLM- und Simulator-Status) nennen die vergleichbaren Zeiten.
  assert.match(stale.element("llm-retry-summary").textContent,/15\.10\.2026 um 01:31:36 Uhr/);
  assert.match(stale.element("llm-retry-summary").textContent,/erst am 15\.10\.2026/);
  assert.match(stale.element("llm-retry-steps").children[0].textContent,/Positiv-Lauf starten/);
  assert.match(stale.element("llm-retry-impact").textContent,/weder ein GLM-Ausfall.*Fehler der Fachanwendung/s);
  assert.doesNotMatch(stale.element("llm-retry-steps").children.map(c=>c.textContent).join("\n"),/mvn clean install/);
  const mitZeit=browser();mitZeit.simulator(simulatorStatus({buildZeit:"2026-10-15T02:20:00Z",
    nachweisFehler:[],
    letzteFehlermeldung:null,
    review:{state:"UNVERIFIED",automatedState:"UNVERIFIED",llmState:"ERROR",errors:["KI-Prüfung: ERROR. Buildnachweis der POSITIV-Quelle fehlt oder ist veraltet: run-20261015-013136"]}}));
  await mitZeit.poll();
  // Laufzeit kommt unverändert aus der Lauf-ID; die Bauzeit wird zeitzonengerecht formatiert.
  assert.match(mitZeit.element("llm-retry-summary").textContent,/15\.10\.2026 um 01:31:36 Uhr/);
  assert.match(mitZeit.element("llm-retry-summary").textContent,/erst am 15\.10\.2026/);
  const retry=browser();retry.response(status({state:"ERROR",caseVerdict:"UNVERIFIED",retryExhausted:true,message:"Fall-ID-Liste unvollständig"}));
  await retry.poll();
  assert.match(retry.element("llm-retry-summary").textContent,/3 Versuche fehlgeschlagen/);
  assert.match(retry.element("llm-retry-steps").children[0].textContent,/protocol\.md.*drei Sendeversuche/s);
});

test("Fehlertexte und Handlungsanweisungen werden ausschließlich als Text gerendert",()=>{
  const ui=browser();ui.simulator(simulatorStatus({letzteFehlermeldung:"<img src=x onerror=alert(1)>",nachweisFehler:[],review:{state:"FAIL",errors:[]}}));
  assert.match(ui.element("llm-retry-message").textContent,/<img/);
  assert.equal(ui.element("llm-retry-message").innerHTML,"");
  assert.ok(ui.element("llm-retry-steps").children.every(c=>c.innerHTML === ""));
});

test("neue Fassung zeigt die Anleitung trotz alter Quittierung einmal pro Befund",()=>{
  const ui=browser();ui.storage.set("testsweet-prueffehler-sim-D:\\testsweet\\logs\\run-42-FEHLGESCHLAGEN","1");
  ui.simulator(simulatorStatus());ui.element("llm-retry-dialog").close();ui.simulator(simulatorStatus());
  assert.equal(ui.element("llm-retry-dialog").shown,1);
});

test("GUI- und Batchstart durchlaufen die Compilerstempel-Phase vor Java",()=>{
  const launcher=fs.readFileSync(path.resolve(__dirname,"../../../start.bat"),"utf8");
  assert.equal((launcher.match(/process-classes exec:java/g)||[]).length,2);
  assert.doesNotMatch(launcher,/compile exec:java/);
});

const suiteStatus = (extra={}) => ({suiteId:"suite-1",jobId:"suite-1",state:"RUNNING",running:true,
  phase:"FACH_POSITIV",phaseLabel:"Fachschicht: Positivfälle",completedPhases:4,totalPhases:13,
  completedItems:47,totalItems:500,elapsedSeconds:127,logPath:"D:\\testsweet\\logs\\suite-1\\suite.log",...extra});

test("Gesamtlauf zeigt Phasen, tatsächliche Fälle und Laufzeit und sperrt manuelle Aktionen",()=>{
  const ui=browser();ui.suite(suiteStatus());
  assert.equal(ui.element("zertifikat-tests-progress").value,4);
  assert.equal(ui.element("zertifikat-tests-progress").max,13);
  assert.match(ui.element("zertifikat-tests-prozent").textContent,/30 % der Phasen/);
  assert.match(ui.element("zertifikat-tests-details").textContent,/4\/13.*2 min 7 s.*Keine verlässliche Restzeit/);
  assert.equal(ui.element("zertifikat-tests-phase-progress").value,47);
  assert.equal(ui.element("zertifikat-tests-phase-progress").max,500);
  for(const id of ["start","stop","llm-start-knopf","llm-config-open","llm-config-save","zertifikatknopf","zertifikat-tests-start"]) assert.equal(ui.element(id).disabled,true,id);
  assert.equal(ui.element("zertifikat-tests-stop").disabled,false);
});

test("Mavenphase erfindet keinen Testfortschritt",()=>{
  const ui=browser();ui.suite(suiteStatus({phase:"MAIN_TESTS",phaseLabel:"Hauptprogrammtests",completedItems:0,totalItems:0}));
  assert.equal(ui.element("zertifikat-tests-phase-progress").value,undefined);
  assert.match(ui.element("zertifikat-tests-phase-details").textContent,/genauer Teilfortschritt wird nicht gemeldet/);
});

test("Reload fragt laufenden Serverablauf ab, ohne erneut einen Start auszulösen",async()=>{
  const ui=browser();ui.response(suiteStatus(),true,"/api/zertifikat/tests/status");await ui.suitePoll();
  assert.match(ui.element("zertifikat-tests-status").textContent,/laufen/);
  assert.equal(ui.element("zertifikat-tests-start").disabled,true);
  assert.equal(ui.element("zertifikat-tests-stop").disabled,false);
});

test("Mehrfachklick sendet genau einen Start und gibt keinen alten Zertifikatslink frei",async()=>{
  const ui=browser();ui.suite(suiteStatus({state:"IDLE",running:false}));ui.element("llm-faelle").value=18;
  let resolve;ui.deferred(new Promise(done=>{resolve=done;}),"/api/zertifikat/tests/start");
  const first=ui.suiteStart();await ui.suiteStart();
  assert.equal(ui.element("zertifikat-tests-start").disabled,true);
  assert.equal(ui.element("zertifikat-tests-pdf").hidden,true);
  resolve({ok:true,json:async()=>suiteStatus()});await first;
  assert.equal(ui.element("zertifikat-tests-stop").disabled,false);
});

test("SUCCESS braucht aktuellen BESTANDEN-Nachweis für grüne Anzeige und PDF",()=>{
  const ui=browser();ui.suite(suiteStatus({state:"SUCCESS",running:false,verdict:"BESTANDEN",certificateCurrent:true,completedPhases:13}));
  assert.equal(ui.element("zertifikat-tests-status").className,"ok");
  assert.equal(ui.element("zertifikat-tests-pdf").hidden,false);
  ui.suite(suiteStatus({state:"SUCCESS",running:false,verdict:"BESTANDEN",certificateCurrent:false}));
  assert.equal(ui.element("zertifikat-tests-status").className,"nok");
  assert.equal(ui.element("zertifikat-tests-pdf").hidden,true);
  const unknown=browser();unknown.suite(suiteStatus({state:"SUCCESS",running:false}));
  assert.equal(unknown.element("zertifikat-tests-status").className,"nok");
});

test("Statusabruffehler entzieht Erfolg und lässt Einzelaktionen bis zur Klärung gesperrt",async()=>{
  const ui=browser();ui.suite(suiteStatus({state:"SUCCESS",running:false,verdict:"BESTANDEN",certificateCurrent:true}));
  ui.response({fehler:"Keine Verbindung"},false,"/api/zertifikat/tests/status");await ui.suitePoll();
  assert.equal(ui.element("zertifikat-tests-status").className,"nok");
  assert.equal(ui.element("zertifikat-tests-pdf").hidden,true);
  for(const id of ["start","llm-start-knopf","llm-config-open","zertifikat-tests-start"]) assert.equal(ui.element(id).disabled,true);
  ui.response(suiteStatus({state:"IDLE",running:false}),true,"/api/zertifikat/tests/status");await ui.suitePoll();
  assert.equal(ui.element("zertifikat-tests-start").disabled,false);
});

test("Abbruch ist keine erfolgreiche Prüfung und Popup enthält knappe Aktion und Log",async()=>{
  const ui=browser();ui.suite(suiteStatus());
  ui.response(suiteStatus({state:"CANCELLED",running:false,message:"Vom Benutzer abgebrochen"}),true,"/api/zertifikat/tests/stop");
  await ui.suiteStop();
  assert.match(ui.element("zertifikat-tests-status").textContent,/abgebrochen.*unvollständig/i);
  assert.equal(ui.element("zertifikat-tests-pdf").hidden,true);
  assert.equal(ui.element("zertifikat-tests-stop").disabled,true);
  assert.match(ui.element("llm-retry-summary").textContent,/kein Erfolgszertifikat/);
  assert.match(ui.element("llm-retry-log").textContent,/suite\.log/);
});

test("Suitefehler und veraltete Eingangsbelege haben direkte kurze Handlung statt primärer Anleitung",()=>{
  const ui=browser();ui.simulator(simulatorStatus({nachweisFehler:[],letzteFehlermeldung:"Buildnachweis der POSITIV-Quelle fehlt oder ist veraltet: run-41",
    review:{state:"UNVERIFIED",errors:[]}}));
  assert.equal(ui.element("llm-retry-auto").hidden,false);
  assert.match(ui.element("llm-retry-summary").textContent,/Mache alle Zertifikatstests/);
  assert.equal(ui.element("llm-retry-details").open,false);
  const failed=browser();failed.suite(suiteStatus({state:"FAILED",running:false,message:"TF<script>: Zahlung falsch"}));
  assert.equal(failed.element("llm-retry-auto").hidden,true);
  assert.match(failed.element("llm-retry-impact").textContent,/Protokoll.*Behebung/);
  assert.equal(failed.element("llm-retry-message").innerHTML,"");
});

test("Alte GLM-Antwort und manuelle Simulatorbefunde überschreiben Suiteerfolg nicht",async()=>{
  const ui=browser();let resolve;ui.deferred(new Promise(done=>{resolve=done;}));
  const old=ui.poll();
  ui.suite(suiteStatus({state:"SUCCESS",running:false,verdict:"BESTANDEN",certificateCurrent:true,llmSummary:{state:"PASS"}}));
  resolve({ok:true,json:async()=>status({state:"ERROR",message:"Alte Eingangsbelege"})});await old;
  ui.simulator(simulatorStatus());
  assert.equal(ui.element("llm-retry-dialog").shown,0);
  assert.equal(ui.element("zertifikat-tests-status").className,"ok");
  assert.match(ui.element("llm-status").textContent,/Gesamtlauf: PASS/);
});

test("Manueller neuer Lauf entwertet die grüne Suiteanzeige und erlaubt eigene Diagnosen",()=>{
  const ui=browser();const success=suiteStatus({state:"SUCCESS",running:false,verdict:"BESTANDEN",certificateCurrent:true});ui.suite(success);
  vm.runInContext("entwerteSuiteNachManuellerAktion()",ui.context);ui.suite(success);
  assert.equal(ui.element("zertifikat-tests-pdf").hidden,true);
  assert.notEqual(ui.element("zertifikat-tests-status").className,"ok");
  ui.simulator(simulatorStatus());assert.equal(ui.element("llm-retry-dialog").shown,1);
});

test("HTML setzt Automatikbutton exakt und verschiebt lange Anleitung in Details",()=>{
  const html=fs.readFileSync(path.resolve(__dirname,"../../main/resources/sweet/index.html"),"utf8");
  assert.equal((html.match(/>Mache alle Zertifikatstests<\/button>/g)||[]).length,2);
  assert.match(html,/<details id="llm-retry-details">[\s\S]*?<ol id="llm-retry-steps">/);
  assert.match(html,/<progress id="zertifikat-tests-progress"/);
});

test("Testabweichung und technischer Ablaufdefekt werden im Suitepopup unterschieden",()=>{
  const testFailure=browser();testFailure.suite(suiteStatus({state:"FAILED",running:false,failureKind:"TEST_FAILURE",message:"TF4: Betrag falsch"}));
  assert.match(testFailure.element("llm-retry-severity").textContent,/TESTFEHLER.*NICHT BESTANDEN/);
  assert.match(testFailure.element("llm-retry-title").textContent,/Tests haben Fehler gefunden/);
  const technical=browser();technical.suite(suiteStatus({state:"FAILED",running:false,failureKind:"TECHNICAL",message:"Maven nicht gefunden"}));
  assert.match(technical.element("llm-retry-title").textContent,/Technischer Fehler/);
  assert.doesNotMatch(technical.element("llm-retry-summary").textContent,/Abweichung gefunden/);
});

test("Verspäteter Einzelstatus zeigt während und nach dem Gesamtlauf keine alten PDF-Erfolge",async()=>{
  const ui=browser();
  ui.element("pdfleiste").style.display="block";
  ui.element("pdfhinweis").style.display="block";
  ui.element("zertifikatlink").style.display="inline-block";
  let resolve;ui.deferred(new Promise(done=>{resolve=done;}),"/api/status");
  const old=ui.refreshSimulator();
  ui.suite(suiteStatus());
  for(const id of ["pdfleiste","pdfhinweis","zertifikatlink"]) assert.equal(ui.element(id).style.display,"none",id);
  ui.response({auftraege:[]},true,"/api/auftraege");
  const singleStatus={phase:"FERTIG",pdf:"veralteter-einzellauf.pdf",md:"veraltet.md",laufdauerSekunden:1,kennzahlen:{VERIFIZIERT:60},review:{state:"PASS"},nachweiseVollstaendig:true};
  resolve({ok:true,json:async()=>singleStatus});await old;
  for(const id of ["pdfleiste","pdfhinweis","zertifikatlink"]) assert.equal(ui.element(id).style.display,"none",id);
  ui.suite(suiteStatus({state:"SUCCESS",running:false,verdict:"BESTANDEN",certificateCurrent:true}));
  ui.response(singleStatus,true,"/api/status");ui.response({auftraege:[]},true,"/api/auftraege");await ui.refreshSimulator();
  for(const id of ["pdfleiste","pdfhinweis","zertifikatlink"]) assert.equal(ui.element(id).style.display,"none",id);
  assert.equal(ui.element("zertifikat-tests-pdf").hidden,false);
  vm.runInContext("entwerteSuiteNachManuellerAktion()",ui.context);
  ui.response(singleStatus,true,"/api/status");ui.response({auftraege:[]},true,"/api/auftraege");await ui.refreshSimulator();
  assert.equal(ui.element("pdfleiste").style.display,"block");
  assert.equal(ui.element("pdfhinweis").style.display,"block");
  assert.equal(ui.element("zertifikat-tests-pdf").hidden,true);
});

test("GLM-Konfigurationsfallback setzt 200 Fälle und bewahrt ausdrücklich gewählten Umfang",()=>{
  const ui=browser();
  vm.runInContext("fillLlmConfig({})",ui.context);
  assert.equal(ui.element("llm-cases").value,200);
  assert.equal(ui.element("llm-faelle").value,200);
  vm.runInContext("fillLlmConfig({cases:18})",ui.context);
  assert.equal(ui.element("llm-cases").value,18);
  assert.equal(ui.element("llm-faelle").value,18);
});

test("Standardpreset liefert 100 Positiv- und 100 Negativfälle und erhält die 500er-Breite",()=>{
  const ui=browser();
  for(const category of ["POSITIV","NEGATIV"]){
    vm.runInContext(`gewaehlteKategorie='${category}';wendePresetAn('standard')`,ui.context);
    assert.equal(ui.element("auftraege").value,100,category);
    vm.runInContext("wendePresetAn('gross')",ui.context);
    assert.equal(ui.element("auftraege").value,500,category);
  }
});

test("statisches Formular und Erklärung stimmen mit dem Standard 100 plus 100 überein",()=>{
  const html=fs.readFileSync(path.resolve(__dirname,"../../main/resources/sweet/index.html"),"utf8");
  assert.match(html,/<input[^>]*id="auftraege"[^>]*value="100"/);
  assert.match(html,/<input[^>]*id="llm-faelle"[^>]*value="200"/);
  assert.match(html,/<input[^>]*id="llm-cases"[^>]*value="200"/);
  assert.match(html,/200 Prüffällen genau 100 Positivfälle/);
  assert.doesNotMatch(html,/50 guten und 50 schlechten/);
});

test("Reportknopf und alter Reportlink bleiben bei laufender oder unbekannter Suite gesperrt",()=>{
  const ui=browser();ui.element("testreportlink").style.display="inline-block";
  ui.suite(suiteStatus());
  assert.equal(ui.element("testreportknopf").disabled,true);
  assert.equal(ui.element("testreportlink").style.display,"none");
  vm.runInContext("zertifikatSuiteStatusBekannt=false;sperreZertifikatSuiteBedienung()",ui.context);
  assert.equal(ui.element("testreportknopf").disabled,true);
  ui.suite(suiteStatus({state:"IDLE",running:false}));
  assert.equal(ui.element("testreportknopf").disabled,false);
});

test("Reportexport sperrt Doppelklick und neue Läufe und gibt nach Erfolg wieder frei",async()=>{
  const ui=browser();ui.installReport();ui.suite(suiteStatus({state:"IDLE",running:false}));
  ui.element("testreportlink").style.display="inline-block";
  let resolve;ui.deferred(new Promise(done=>{resolve=done;}),"/api/testreport");
  const pending=ui.report();
  for(const id of ["testreportknopf","start","zertifikatknopf","zertifikat-tests-start","llm-start-knopf"])
    assert.equal(ui.element(id).disabled,true,id);
  assert.equal(ui.element("testreportlink").style.display,"none");
  await ui.report(); // Ein zweiter Fetch wäre ungeplant und würde diese Probe rot machen.
  resolve({ok:true,json:async()=>({ok:true,pdf:"fixture.pdf",reportId:"ID",laeufe:9,davonErfolgreich:9})});
  await pending;
  assert.equal(ui.element("testreportknopf").disabled,false);
  assert.equal(ui.element("testreportlink").style.display,"inline-block");
});

test("Späte Reportantwort aktiviert während neuer Suite weder Knopf noch alten Link",async()=>{
  const ui=browser();ui.installReport();ui.suite(suiteStatus({state:"IDLE",running:false}));
  let resolve;ui.deferred(new Promise(done=>{resolve=done;}),"/api/testreport");
  const pending=ui.report();ui.suite(suiteStatus());
  resolve({ok:true,json:async()=>({ok:true,pdf:"fixture.pdf"})});await pending;
  assert.equal(ui.element("testreportknopf").disabled,true);
  assert.equal(ui.element("testreportlink").style.display,"none");
});

test("Fehlgeschlagener Reportexport entwertet den alten Link und entsperrt die Bedienung",async()=>{
  const ui=browser();ui.installReport();ui.suite(suiteStatus({state:"IDLE",running:false}));
  ui.element("testreportlink").style.display="inline-block";
  ui.response({fehler:"kontrollierter Exportfehler"},false,"/api/testreport");await ui.report();
  assert.equal(ui.element("testreportknopf").disabled,false);
  assert.equal(ui.element("testreportlink").style.display,"none");
});

test("Serverstatus eines Exports aus anderem Tab sperrt Report und Starts",()=>{
  const ui=browser();
  vm.runInContext('momentanerStatus={phase:"BEREIT",documentExportRunning:true};sperreZertifikatSuiteBedienung()',ui.context);
  for(const id of ["testreportknopf","start","zertifikat-tests-start"])
    assert.equal(ui.element(id).disabled,true,id);
});

test("Kurz veralteter Busy-Poll versteckt einen frisch erstellten Report nur bis zur Entsperrung",async()=>{
  const ui=browser();ui.installReport();ui.suite(suiteStatus({state:"IDLE",running:false}));
  let resolve;ui.deferred(new Promise(done=>{resolve=done;}),"/api/testreport");const pending=ui.report();
  vm.runInContext('momentanerStatus={phase:"BEREIT",documentExportRunning:true}',ui.context);
  resolve({ok:true,json:async()=>({ok:true,pdf:"fixture.pdf"})});await pending;
  assert.equal(ui.element("testreportlink").style.display,"none");
  vm.runInContext('momentanerStatus={phase:"BEREIT",documentExportRunning:false};sperreZertifikatSuiteBedienung()',ui.context);
  assert.equal(ui.element("testreportlink").style.display,"inline-block");
  assert.equal(ui.element("testreportknopf").disabled,false);
});
