"use strict";
const assert = require("node:assert/strict");
const {test} = require("node:test");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");

// Produktdatei vollständig ausführen; nur DOM und HTTP sind kontrollierte Testgrenzen.
const source = fs.readFileSync(path.resolve(__dirname, "../../../../src/main/resources/webapp/app.js"), "utf8");
function browser(storage = new Map()) {
  const elements = new Map(), requests = [], responses = [];
  let sequence = 0;
  function element(id) {
    if (elements.has(id)) return elements.get(id);
    const classes = new Set(), listeners = new Map();
    const value = {id, value:"", checked:false, disabled:false, textContent:"", innerHTML:"", style:{}, dataset:{}, children:[],
      classList:{toggle(name,on){if(on)classes.add(name);else classes.delete(name);},add(name){classes.add(name);},
        remove(name){classes.delete(name);},contains(name){return classes.has(name);}},
      addEventListener(name, listener){listeners.set(name, listener);},
      querySelector(selector){return element(id + ":" + selector);},
      appendChild(child){this.children.push(child);},
      async fire(name){return listeners.get(name)?.({target:this});}};
    elements.set(id, value);
    return value;
  }
  const context = vm.createContext({
    document:{getElementById:element, querySelector:element, querySelectorAll:()=>[],
      createElement:tag=>element("new-" + tag + "-" + (++sequence))},
    sessionStorage:{getItem:key=>storage.get(key) ?? null, setItem:(key,value)=>storage.set(key, value), removeItem:key=>storage.delete(key)},
    crypto:{randomUUID:()=>"synthetic-vorgang-" + (++sequence)},
    fetch:async (url, options)=>{
      requests.push({url, options});
      assert.ok(responses.length, "Ungeplanter HTTP-Aufruf " + url);
      const response = responses.shift();
      assert.equal(url, response.url);
      if (response.error) throw response.error;
      if (response.promise) return response.promise;
      return {status:response.status, ok:response.status >= 200 && response.status < 300,
        json:async()=>response.body};
    },
    window:{open(){}}, confirm:()=>true, console,
  });
  vm.runInContext(source, context, {filename:"webapp/app.js"});
  function fixture(bgId=7) {
    const data = {bedarfsgemeinschaft:{id:bgId, aktenzeichen:"BG-SYNTHETISCH", hauptpersonName:"Testperson", statusAktiv:true,
      budgetRest:1000, vermoegen:0, version:1, iban:null, bic:null, kontoinhaber:null},
      faelle:[], zahlungen:[], journal:[], bescheid:null, berechnungsmonat:"2026-10"};
    context.fixture = data;
    vm.runInContext("detail = fixture; angemeldet = {kennung:'sb-test', rolle:'sachbearbeiter'}", context);
    element("zahlungsmonat").value = "2026-10";
    return data;
  }
  fixture();
  return {context, element, storage, requests, fixture,
    response(url, body, status=200){responses.push({url, body, status});},
    error(url){responses.push({url, error:new TypeError("Synthetischer Verbindungsabbruch")});},
    deferred(url,promise){responses.push({url,promise});},
    invoke(expression){return vm.runInContext(expression, context);},
  };
}

test("unklarer Ausgang lädt nur lesend neu und behält die Vorgangsnummer bis zur sicheren Antwort", async()=>{
  const ui=browser();
  ui.response("/api/auszahlung", {code:"AUSGANG_UNBEKANNT", message:"Status neu laden; nicht erneut anordnen"}, 409);
  ui.response("/api/bedarfsgemeinschaften/7?monat=2026-10", ui.fixture());
  await ui.element("btn-auszahlung").fire("click");
  assert.deepEqual(ui.requests.map(r=>r.options.method || "GET"), ["POST", "GET"]);
  const original=JSON.parse(ui.requests[0].options.body).vorgangsNr;
  assert.equal(ui.storage.get("lmgAuszahlungsauftrag:sb-test:7:2026-10"), original);
  assert.match(ui.element("dialog-text").textContent, /Status neu laden/);
  ui.response("/api/auszahlung", {bereitsGebucht:true, zahlung:{erstelltAm:"2026-10-15T10:00:00Z"}});
  ui.response("/api/bedarfsgemeinschaften/7?monat=2026-10", ui.fixture());
  await ui.element("btn-auszahlung").fire("click");
  assert.equal(JSON.parse(ui.requests[2].options.body).vorgangsNr, original);
  assert.equal(ui.storage.has("lmgAuszahlungsauftrag:sb-test:7:2026-10"), false);
});

test("Verbindungsabbruch und Browser-Reload erzeugen keine neue Vorgangsnummer", async()=>{
  const ui=browser();
  ui.error("/api/auszahlung");
  ui.response("/api/bedarfsgemeinschaften/7?monat=2026-10", ui.fixture());
  await ui.element("btn-auszahlung").fire("click");
  const original=JSON.parse(ui.requests[0].options.body).vorgangsNr;
  const reloaded=browser(ui.storage);
  reloaded.response("/api/auszahlung", {code:"VALIDATION", message:"Probe beendet"}, 400);
  await reloaded.element("btn-auszahlung").fire("click");
  assert.equal(JSON.parse(reloaded.requests[0].options.body).vorgangsNr, original);
  assert.equal(reloaded.storage.size, 0);
});

test("andere Bedarfsgemeinschaft oder anderer Monat erhält eine eigene Auftragsnummer", async()=>{
  const ui=browser();
  const a=ui.invoke("auszahlungsVorgangsnummer(auszahlungsAuftragsschluessel(7, '2026-10'))");
  const b=ui.invoke("auszahlungsVorgangsnummer(auszahlungsAuftragsschluessel(8, '2026-10'))");
  const c=ui.invoke("auszahlungsVorgangsnummer(auszahlungsAuftragsschluessel(7, '2026-11'))");
  assert.equal(new Set([a,b,c]).size, 3);
});

test("Monatswechsel lädt ausdrücklich die neue fachliche Vorschau", async()=>{
  const ui=browser();
  ui.element("zahlungsmonat").value="2027-01";
  const fixture=ui.fixture(); fixture.berechnungsmonat="2027-01";
  ui.element("zahlungsmonat").value="2027-01";
  ui.response("/api/bedarfsgemeinschaften/7?monat=2027-01", fixture);
  await ui.element("zahlungsmonat").fire("change");
  assert.equal(ui.requests.length, 1);
  assert.equal(ui.requests[0].options.method, undefined);
});

test("Änderungsbescheid sendet den ausgewählten Wirkungszeitraum per PUT", async()=>{
  const ui=browser();
  ui.element("bescheid-von").value="2026-11";
  ui.element("bescheid-bis").value="2027-09";
  ui.response("/api/bedarfsgemeinschaften/7/bescheid", {hinweis:"Geändert"});
  ui.response("/api/bedarfsgemeinschaften/7?monat=2026-10", ui.fixture());
  await ui.element("btn-bescheid-aendern").fire("click");
  assert.equal(ui.requests[0].options.method, "PUT");
  assert.deepEqual(JSON.parse(ui.requests[0].options.body), {bewilligungVon:"2026-11", bewilligungBis:"2027-09"});
});

test("unbekannter Saga-Ausgang behauptet keine abgeschlossene Kompensation", async()=>{
  const ui=browser();
  ui.response("/api/saga/antrag", {code:"AUSGANG_UNBEKANNT", message:"Verarbeitungsstatus ungeklärt"}, 409);
  ui.response("/api/bedarfsgemeinschaften?monat=2026-10", {eintraege:[]});
  ui.response("/api/zahlungen?limit=50", {zahlungen:[]});
  await ui.element("btn-saga-starten").fire("click");
  assert.equal(ui.element("dialog-titel").textContent, "Antragsstatus ungeklärt");
  assert.doesNotMatch(ui.element("dialog-text").textContent, /wurden.*kompensiert/);
  assert.equal(ui.requests.filter(r=>r.options.method === "POST").length, 1);
});

test("Übersichtsauswahl bindet Listenbeträge und anschließende Vorgangsmaske an denselben Monat",async()=>{
  const ui=browser(),detail=ui.fixture();detail.berechnungsmonat="2027-01";
  ui.element("uebersicht-monat").value="2027-01";
  ui.response("/api/bedarfsgemeinschaften?monat=2027-01",{eintraege:[{id:7,aktenzeichen:"BG-SYNTHETISCH",
    hauptpersonName:"Testperson",statusAktiv:true,budgetRest:1000,zahlbetragVorschau:563,berechnungsmonat:"2027-01"}]});
  ui.response("/api/zahlungen?limit=50",{zahlungen:[]});
  await ui.element("uebersicht-monat").fire("change");
  assert.equal(ui.element("zahlungsmonat").value,"2027-01");
  assert.equal(ui.element("vorschau-monat").textContent,"2027-01");
  assert.match(ui.element("tabelle-bgs:tbody").children[0].innerHTML,/2027-01/);
  ui.response("/api/bedarfsgemeinschaften/7?monat=2027-01",detail);
  await ui.invoke("oeffneVorgang(7)");
  assert.equal(ui.requests.at(-1).url,"/api/bedarfsgemeinschaften/7?monat=2027-01");
});

test("verspätete Vorschau des alten Monats überschreibt den neuen Monatsstand nicht",async()=>{
  const ui=browser();
  let resolve;
  ui.deferred("/api/bedarfsgemeinschaften?monat=2026-10",new Promise(done=>{resolve=done;}));
  ui.response("/api/zahlungen?limit=50",{zahlungen:[]});
  const old=ui.invoke("ladeUebersicht()");
  ui.element("zahlungsmonat").value="2027-01";
  ui.response("/api/bedarfsgemeinschaften?monat=2027-01",{eintraege:[]});
  ui.response("/api/zahlungen?limit=50",{zahlungen:[]});
  await ui.invoke("ladeUebersicht()");
  resolve({status:200,ok:true,json:async()=>({eintraege:[]})});await old;
  assert.equal(ui.element("vorschau-monat").textContent,"2027-01");
  assert.equal(ui.element("uebersicht-monat").value,"2027-01");
});
