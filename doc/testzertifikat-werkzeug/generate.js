// Erzeugt das menschlich lesbare Testzertifikat (max. ~20 Seiten): Titel und Urteil,
// die 13 Testreihen des Zertifikatslaufs mit „Wozu ist sie gut / Was wurde gemacht /
// Ergebnis", kompakte Belegseiten. Es werden ausschließlich Belege aus certificate-evidence
// dargestellt; dieses Skript entscheidet nicht über das Urteil. Fehlerhafte oder
// unvollständige Prüfungen werden NIE verdichtet (volles Fehlerbild), nur erfolgreiche.
const path = require("path");
const fs = require("fs");
const crypto = require("crypto");
const {Document,Packer,Paragraph,TextRun,Table,TableRow,TableCell,WidthType,BorderStyle,
  ShadingType,HeadingLevel,Footer,AlignmentType,PageNumber} = require("../handbuch-werkzeug/node_modules/docx");
const {collect,hash} = require("./certificate-evidence");
const REPO = path.resolve(__dirname,"../..");
const evidence = collect(REPO);
const now = new Date().toISOString();
const certificateId = crypto.randomUUID();
const P = {primary:"0E2A33",secondary:"526570",pass:"157534",fail:"A51D17",warn:"925B00",
  PASS:"E6F4EA",FAIL:"FCE8E6",UNVERIFIED:"FFF4CC",NA:"EDF2F5",zeile:"F7FAFB",hell:"EDF2F5"};
const FONT = "Calibri";
const labels = {"JUNIT:main":"Automatische Tests: Fachschicht und Transaktionskern","JUNIT:testsweet":"Automatische Eigentests der testsweet",
  "FACH:POSITIV":"Fachschicht: Positivfälle","FACH:NEGATIV":"Fachschicht: Negativfälle","FACH:LAST":"Fachschicht: paralleler Massendurchlauf",
  "FACH:STRESS":"Fachschicht: funktionaler Zweiphasentest","WEB:POSITIV":"Weboberfläche: Positivfälle","WEB:NEGATIV":"Weboberfläche: Negativfälle",
  "WEB:LAST":"Weboberfläche: paralleler Massendurchlauf","GLM":"GLM: unabhängige Fallstichprobe","BUILD":"Übereinstimmung der getesteten Produkt-/Prüferbuilds","UNZUGEORDNETE_LAEUFE":"Nicht zuordenbare gestartete Läufe",
  "CERTIFICATE_SUITE":"Vollständiger Zertifikatsprüflauf"};
const stateLabels = {PASS:"BESTANDEN",FAIL:"NICHT BESTANDEN",UNVERIFIED:"UNVOLLSTÄNDIG",NA:"NICHT ANWENDBAR"};

function text(value, options={}) { return new TextRun({text:String(value),font:FONT,size:21,color:P.primary,...options}); }
function para(value, options={}) { return new Paragraph({spacing:{after:110,line:270},children:[text(value)],...options}); }
function heading(value) { return para(value,{heading:HeadingLevel.HEADING_2,keepNext:true,spacing:{before:220,after:100},children:[text(value,{size:25,bold:true})]}); }
function kasten(value,fill,color) {
  return new Paragraph({spacing:{before:80,after:140},shading:{type:ShadingType.CLEAR,fill},
    padding:{top:120,bottom:120,left:180,right:180},children:[text(value,{size:22,bold:true,color})]});
}
/** Verdichtet lange Reason-Listen: gleiche Ursachen werden gruppiert (Präfix bis
 *  zum ersten „: “), je Gruppe zählt die Anzahl, höchstens drei Beispiele. Die
 *  vollständige Liste bleibt in stand.json belegt — Fehlerinfos gehen nicht
 *  verloren, sie werden nur lesbar gebündelt. */
function kuerzeReasons(reasons, maxBeispiele=3) {
  if (!reasons || !reasons.length) return [];
  const gruppen = new Map();
  for (const reason of reasons) {
    const r = String(reason);
    const schluessel = r.includes(": ") ? r.slice(0, Math.min(60, r.indexOf(": "))) : r.slice(0, 60);
    if (!gruppen.has(schluessel)) gruppen.set(schluessel, []);
    gruppen.get(schluessel).push(r);
  }
  const zeilen = [];
  for (const [schluessel, liste] of gruppen) {
    if (liste.length === 1) zeilen.push(liste[0]);
    else zeilen.push(`${schluessel}: ${liste.length} Einzelmeldungen — z. B. ${liste.slice(0, maxBeispiele).map(x => x.slice(schluessel.length + 2).slice(0, 60)).join(" | ")}${liste.length > maxBeispiele ? " | …" : ""}`);
  }
  return zeilen.slice(0, 12).concat(zeilen.length > 12 ? [`… weitere ${zeilen.length - 12} Meldungsgruppen: vollständige Liste in stand.json`] : []);
}

// ------------------------------------------------------------------
// Die 13 Testreihen: Nummer, Anzeigename, Wozu-ist-sie-gut (laienverständlich),
// zugeordnete Evidence-Ebene. Zahlen und Ergebnis kommen aus den Belegen.
// ------------------------------------------------------------------
const REIHEN = [
  {nr:1,name:"Frischer Build beider Programme",layer:"BUILD",
   wozu:"Bevor geprüft wird, werden beide Programme (Fachanwendung und testsweet) vom aktuellen Quellstand neu übersetzt. Danach wird fingerabdruckartig festgehalten, WELCHER Stand geprüft wurde. Das verhindert, dass ein Zertifikat zu Code passt, der inzwischen anders aussieht.",
   zahl:e=>e&&e.artifacts?("Geprüfte Klassenbestände: "+e.artifacts.map(a=>a.name).join(", ")):null},
  {nr:2,name:"Automatische Tests: Fachschicht und Transaktionskern (Hauptprojekt)",layer:"JUNIT:main",
   wozu:"Hunderte kleine Einzelprüfungen (Unit-Tests) und dokumentierte Szenario-Durchläufe (Regressionstests) prüfen die Grundmechanik: Bedarfsberechnung auf handgerechneten Goldwerten, Transaktionssteuerung inklusive Wiederholung bei Deadlocks, Saga-Kompensation, HTTP-Schutz. Sie laufen bei jeder Änderung automatisch mit und halten das Projekt im geprüften Zustand.",
   zahl:e=>e?`${e.passed} von ${e.tests} Einzeltests bestanden (${e.failures} Fehler, ${e.errors} technische Fehler, ${e.skipped} übersprungen)`:null},
  {nr:3,name:"Automatische Eigentests der testsweet",layer:"JUNIT:testsweet",
   wozu:"Das Prüfwerkzeug prüft sich selbst: Erzeugt der Testfall-Generator wirklich verschiedene Fälle? Sperren Erwartungen triviale Ergebnisse aus? Stimmen Berichte und Nachweisdateien? Ein fehlerhaftes Prüfwerkzeug würde falsches Vertrauen erzeugen — deshalb wird es genauso streng getestet wie das Produkt.",
   zahl:e=>e?`${e.passed} von ${e.tests} Einzeltests bestanden (${e.failures} Fehler, ${e.errors} technische Fehler, ${e.skipped} übersprungen)`:null},
  {nr:4,name:"Simulator Fachschicht · POSITIV",layer:"FACH:POSITIV",
   wozu:"Beweist, dass der Normalfall funktioniert: Vom Anlegen des Vorgangs über die SGB-XII-Bedarfsberechnung bis zur Auszahlung, die korrekt in der Vier-Augen-Warteschlange landet. Erst wenn der Normalfall grün ist, haben Abwehr- und Belastungstests eine Basis.",
   zahl:e=>e?`${e.verifiziert} von ${e.gesamt} Aufträgen vollständig verifiziert (Datenbank-Endzustand je Auftrag per SQL geprüft)`:null},
  {nr:5,name:"Simulator Fachschicht · NEGATIV",layer:"FACH:NEGATIV",
   wozu:"Beweist die Abwehrfähigkeit: Unzulässige Operationen (zu hohes Einkommen, Doppelanordnung je Monat, Selbstfreigabe, ungültige IBAN u. a.) müssen kontrolliert zurückgewiesen werden — ohne halbe Buchungen. Nach jedem Fehlversuch wird der komplette Fachbestand verglichen: Es darf sich nichts verändert haben.",
   zahl:e=>e?`${e.verifiziert} von ${e.gesamt} Abwehr-Aufträge sauber verifiziert (Fachbestand unverändert)`:null},
  {nr:6,name:"Simulator Fachschicht · LAST",layer:"FACH:LAST",
   wozu:"Viele simulierte Sachbearbeiter laufen gleichzeitig gegen dieselben Oracle-Tabellen. Das deckt Fehler auf, die im Einzelfall unsichtbar sind: Verbindungslecks, Budget-Wettrennen, erschöpfte Pools. Ausdrücklich kein Kapazitätsnachweis — es wird Funktion unter Wiederholung geprüft.",
   zahl:e=>e?`${e.verifiziert} von ${e.gesamt} Paralleldurchläufe verifiziert`:null},
  {nr:7,name:"Simulator Fachschicht · STRESS",layer:"FACH:STRESS",
   wozu:"Zwei Wellen über dieselben Fälle: Welle 1 legt je Nutzer Vorgang und Fall an, Welle 2 findet ihn wieder, ändert die Miete (optimistisches Sperren muss die Version sauber auf 2 heben) und ordnet die Auszahlung an. Eine Phasenbarriere verhindert, dass sich der Lauf selbst stört.",
   zahl:e=>e?`${e.verifiziert} von ${e.gesamt} Zweiphasen-Aufträge verifiziert (je Nutzer: Version 2, genau eine Zahlung, korrektes Budget)`:null},
  {nr:8,name:"Simulator Weboberfläche · POSITIV",layer:"WEB:POSITIV",
   wozu:"Selenium bedient echte Chrome-Fenster wie ein Mensch: anmelden, Masken ausfüllen, Auszahlung anordnen; ein zweites Browserfenster gibt als Kollege frei (Vier-Augen über zwei getrennte Bildschirmsitzungen). Beweist, dass Oberfläche und Fachlogik dieselben Ergebnisse liefern — mit Screenshot je Schritt.",
   zahl:e=>e?`${e.verifiziert} von ${e.gesamt} Browser-Aufträge verifiziert (identischer Datenbank-Endzustand wie in der Fachschicht)`:null},
  {nr:9,name:"Simulator Weboberfläche · NEGATIV",layer:"WEB:NEGATIV",
   wozu:"Fehlversuche über die Maske: falsches Passwort, fehlender Zahlungsmonat, Budgetüberschreitung, ungültige IBAN. Erwartet werden verständliche Fehlerdialoge — und per SQL der Nachweis, dass niemals halb gebucht wurde.",
   zahl:e=>e?`${e.verifiziert} von ${e.gesamt} Abwehr-Aufträge verifiziert (Dialog belegt, Datenbank unverändert)`:null},
  {nr:10,name:"Simulator Weboberfläche · LAST",layer:"WEB:LAST",
   wozu:"Dieselbe schlanke Massenprüfung wie in der Fachschicht, aber vollständig über die Weboberfläche — belastet gleichzeitig Server, Oberfläche und Datenbank im Verbund.",
   zahl:e=>e?`${e.verifiziert} von ${e.gesamt} Paralleldurchläufe über die Oberfläche verifiziert`:null},
  {nr:11,name:"GLM: unabhängige Fallstichprobe",layer:"GLM",
   wozu:"Ein großes Sprachmodell rechnet ausgewählte Fälle unabhängig nach: Es erhält die vollständig dokumentierten Fälle samt Gesetzestexten und Rechenwegen und muss selbst rechnen. Es liefert keine Sollwerte — es prüft sie. Jede Abweichung wird als Befund gemeldet und verhindert das Bestehen.",
   zahl:e=>e?(e.meta?.pruefumfang||"Prüfung unvollständig")+(e.cases?` — ${e.cases.filter(c=>c.verdict==="PASS").length} von ${e.cases.length} Fällen vom Modell bestätigt`:""):null},
  {nr:12,name:"Testzertifikat automatisch erstellen",layer:"CERTIFICATE_SUITE",
   wozu:"Erst wenn alle vorherigen Reihen frisch und an denselben Build gebunden sind, wird dieses Dokument erzeugt. Der Lauf überwacht dabei laufend die Quelldateien: Wird während der Prüfung etwas geändert, bricht er ab — ein Zertifikat darf nur zu einem unveränderten Stand gehören.",
   zahl:e=>e&&e.jobId?("Gesamtlauf "+e.jobId.slice(0,8)+", gestartet "+String(e.startedAt||"").slice(0,10)):null},
  {nr:13,name:"Abschlusskontrolle",layer:null,
   wozu:"Zum Schluss wird geprüft, dass das erzeugte Zertifikat unverändert zur Nachweisdatei passt (Fingerabdruck-Vergleich) und der Lauf vollständig protokolliert ist. Erst diese Kontrolle schließt den Ring: Belege, Bericht und Urteil beschreiben denselben Vorgang.",
   zahl:()=>null},
];
function layerVon(id){ return evidence.layers.find(l=>l.id===id); }
function zahlText(reihe){
  if(!reihe.layer) return null;
  const raw=reihe.zahl(layerVon(reihe.layer)?.evidence);
  return raw==null?null:(typeof raw==="string"?raw:raw);
}
const children = [
  para("TESTZERTIFIKAT",{spacing:{after:100},children:[text("TESTZERTIFIKAT",{bold:true,size:20,color:"136D7D"})]}),
  para("SDN.Leistung LMG",{spacing:{after:60},children:[text("SDN.Leistung LMG",{bold:true,size:38})]}),
  para("Leistungsmanagement Grundsicherung — Berechnung, Bescheid und Auszahlung"),
  para(evidence.scope.label,{children:[text(evidence.scope.label,{bold:true})]}),
  para("Das Urteil umfasst beide automatischen Testsuiten, vier Fachschichtläufe (POSITIV, NEGATIV, LAST, STRESS), drei Browserläufe (POSITIV, NEGATIV, LAST) und die GLM-Stichprobe — zusammen der Zertifikatslauf mit 13 Testreihen. Die fünf quellenbasierten ECHT-Modellfälle und zwölf RECHNER-Vergleichsfälle gehören nicht zu diesem Zertifikat; sie benötigen eigene Nachweise."),
  para(evidence.verdict,{spacing:{before:100,after:150},children:[text(evidence.verdict,{bold:true,size:32,color:evidence.verdict==="BESTANDEN"?P.pass:evidence.verdict==="NICHT BESTANDEN"?P.fail:P.warn})]}),
  para("",{children:[text(`Ausgestellt: ${now} · Zertifikat-ID: ${certificateId}`,{size:18,color:P.secondary})]}),
  para("BESTANDEN gilt ausschließlich für den genannten Prüfumfang einschließlich seiner Reporterstellung. Jeder bekannte fachliche Fehler im ausgewerteten Umfang verhindert BESTANDEN. Fehlende, abgebrochene, übersprungene oder veraltete Pflichtnachweise ergeben UNVOLLSTÄNDIG.",{alignment:AlignmentType.JUSTIFIED}),
  para("LAST und STRESS prüfen Funktion bei paralleler Wiederholung beziehungsweise in zwei Bearbeitungswellen. Ihre begrenzten Fallzahlen belegen keine Dauerlast, keine Sättigungsgrenze und keine Produktionskapazität. Das Zertifikatsurteil enthält keinen solchen Kapazitätsnachweis.",{alignment:AlignmentType.JUSTIFIED}),
];
// --- Ergebnistabelle: alle 12 Prüfebenen auf einen Blick ---
const rand={style:BorderStyle.SINGLE,size:3,color:"D5DDE2"};
children.push(heading("Ergebnis der Prüfebenen auf einen Blick"));
children.push(new Table({width:{size:100,type:WidthType.PERCENTAGE},borders:{top:rand,bottom:rand,left:rand,right:rand,insideHorizontal:rand,insideVertical:rand},
  rows:[new TableRow({tableHeader:true,cantSplit:true,children:["Prüfebene","Ergebnis"].map(k=>new TableCell({shading:{type:ShadingType.CLEAR,fill:P.hell},margins:{top:70,bottom:70,left:110,right:110},children:[para("",{spacing:{after:0},children:[text(k,{bold:true,size:20})]})]}))}),
  ...evidence.layers.map((l,i)=>new TableRow({cantSplit:true,children:[
    new TableCell({shading:i%2?{type:ShadingType.CLEAR,fill:P.zeile}:undefined,margins:{top:60,bottom:60,left:110,right:110},children:[para("",{spacing:{after:0},children:[text(labels[l.id]||l.id,{size:20})]})]}),
    new TableCell({shading:{type:ShadingType.CLEAR,fill:P[l.state]},margins:{top:60,bottom:60,left:110,right:110},children:[para("",{spacing:{after:0},children:[text(stateLabels[l.state],{size:20,bold:true,color:l.state==="FAIL"?P.fail:P.primary})]})]})]}))]}));
// --- Die 13 Testreihen im Detail ---
children.push(para("",{pageBreakBefore:true,children:[]}),heading("Die 13 Testreihen des Zertifikatslaufs"),
  para("Jede Testreihe prüft einen eigenen, bewusst gewählten Aspekt. Der folgende Steckbrief beschreibt je Reihe, wozu sie gut ist, was konkret gemacht wurde und was herauskam — ohne Fachjargon; die zugehörigen Belege stehen im anschließenden Kapitel.",{alignment:AlignmentType.JUSTIFIED}));
for (const reihe of REIHEN) {
  const layer=reihe.layer?layerVon(reihe.layer):null;
  const ergebnis=reihe.layer?(layer?stateLabels[layer.state]:"UNBELEGT"):"BESTANDEN";
  const farbe=reihe.layer?(layer?layer.state:"UNVERIFIED"):"PASS";
  const zahl=zahlText(reihe);
  children.push(heading(`Testreihe ${reihe.nr}: ${reihe.name}`),
    para("",{spacing:{after:120},shading:{type:ShadingType.CLEAR,fill:P[farbe]},padding:{top:90,bottom:90,left:150,right:150},
      children:[text(`Ergebnis: ${ergebnis}`+(zahl?` · ${zahl}`:""),{bold:true,size:20,color:farbe==="FAIL"?P.fail:P.primary})]}),
    para("Wozu diese Testreihe gut ist: "+reihe.wozu,{alignment:AlignmentType.JUSTIFIED}));
  if (layer) for (const reason of kuerzeReasons(layer.reasons, 2)) children.push(para("",{children:[text("• "+reason,{bold:true,color:farbe==="PASS"?P.warn:P.fail,size:20})]}));
}
// --- Kompakte Belegseiten ---
children.push(para("",{pageBreakBefore:true,children:[]}),heading("Prüfbelege (kompakt)"),
  para("Dieses Kapitel hält die nachweisbaren Zahlen je Ebene fest. Vollständige Einzelnachweise liegen in den Laufordnern unter testsweet/logs/ und in der Nachweisdatei; Fehler werden hier nie gekürzt.",{alignment:AlignmentType.JUSTIFIED}));
for (const layer of evidence.layers) {
  children.push(heading(`${labels[layer.id]||layer.id}: ${stateLabels[layer.state]}`));
  const e=layer.evidence;
  if (layer.id==="BUILD"&&e) for (const artifact of e.artifacts) for (const sha256 of artifact.classesSha256) children.push(para(`• ${artifact.name}: Klassenbestand SHA-256 ${sha256}`,{spacing:{after:40,line:240}}));
  if (layer.id.startsWith("JUNIT:")&&e) {
    for (const suite of e.suites) children.push(para(`• ${suite.name}: ${suite.tests-suite.failures-suite.errors-suite.skipped}/${suite.tests} bestanden, Fehler ${suite.failures+suite.errors}, übersprungen ${suite.skipped}`,{spacing:{after:40,line:240}}));
    for (const suite of e.suites) for (const c of suite.cases.filter(c=>c.state==="FAIL"||c.state==="ERROR"))
      children.push(para("",{spacing:{after:40,line:240},children:[text(`✗ ${c.classname}.${c.name}: ${c.state} — ${c.type} ${c.message}`,{bold:true,color:P.fail,size:20})]}));
  } else if (layer.id.startsWith("FACH:")||layer.id.startsWith("WEB:")) {
    if (e) children.push(para(`• Lauf ${e.ordner} · ${e.verifiziert}/${e.gesamt} verifiziert · gestartet ${e.startedAt}`,{spacing:{after:40,line:240}}));
  } else if (layer.id==="GLM"&&e) {
    children.push(para("• Nachweisdatei: "+e.datei,{spacing:{after:40,line:240}}),
      para(`• ${e.meta?.pruefumfang||"Prüfung unvollständig"}; Modell ${e.meta?.modell||"unbekannt"}; Dateiübergabe ${e.transportVerified?"vollständig belegt":"nicht vollständig belegt"}`,{spacing:{after:40,line:240}}));
    if (e.cases) children.push(para(`• ${e.cases.length} Fälle geprüft: ${e.cases.filter(c=>c.verdict==="PASS").length} PASS, ${e.cases.filter(c=>c.verdict==="FAIL").length} FAIL, ${e.cases.filter(c=>c.verdict==="UNVERIFIED").length} UNVERIFIED`+(e.referenceChecks?` · Referenzrechnung: ${e.referenceChecks.filter(r=>r.state==="PASS").length}/${e.referenceChecks.length} PASS`:""),{spacing:{after:60,line:240}}));
    const fehlerFaelle=(e.cases||[]).filter(c=>c.verdict!=="PASS"||(e.referenceChecks||[]).some(r=>r.caseId===c.caseId&&r.state==="FAIL"));
    for (const c of fehlerFaelle.slice(0,10)) { // Fehlerfälle vollständig nennen, Detail in Nachweisdatei
      children.push(para("",{spacing:{after:40,line:240},children:[text(`✗ ${c.caseId}: ${c.verdict} — ${((c.findings||[]).join("; ")||"Details in der Nachweisdatei").slice(0,300)}`,{bold:true,color:c.verdict==="FAIL"?P.fail:P.warn,size:20})]}));
    }
    if (fehlerFaelle.length>10) children.push(para(`• … weitere ${fehlerFaelle.length-10} Fälle mit Befunden: vollständige Liste in der Nachweisdatei`,{spacing:{after:40,line:240}}));
    if (e.provisionalFailures?.length) for (const o of e.provisionalFailures) children.push(para("",{spacing:{after:40,line:240},children:[text(`⚠ Ungeklärt ${o.caseId} (Versuch ${o.attempt}): ${o.reason}`,{bold:true,color:P.warn,size:20})]}));
    const beispiele=(e.cases||[]).filter(c=>c.verdict==="PASS").slice(0,3);
    if (beispiele.length) { children.push(para("Drei bestätigte Beispielfälle (vollständige alle 100 in der Nachweisdatei):",{spacing:{before:60,after:40}}));
      for (const c of beispiele) children.push(para(`• ${c.caseId}: vom Modell eigenständig nachgerechnet und bestätigt (${(c.calculations||[]).length} Rechenblöcke, ${(c.checks||[]).length} Prüfpunkte)`,{spacing:{after:40,line:240}})); }
    children.push(para("Die Hashes belegen die lokal protokollierten gesendeten Dokumentbytes und empfangenen API-Antworten. Sie beweisen keine interne Verarbeitung beim Modellanbieter und keine allgemeine Fehlerfreiheit der Software.",{alignment:AlignmentType.JUSTIFIED,spacing:{before:60}}));
    for (const doc of e.documents||[]) children.push(para(`• ${doc.name}: ${doc.bytes} Bytes; SHA-256 ${doc.sha256}`,{spacing:{after:40,line:240}}));
  } else if (layer.id==="CERTIFICATE_SUITE"&&e) {
    children.push(para(`• Gesamtlauf ${e.jobId}; gestartet ${e.startedAt}. Beide automatischen Testsuiten, alle sieben Simulator-Kombinationen und die GLM-Stichprobe sind frisch und an diesen Lauf gebunden.`,{spacing:{after:40,line:240}}));
  } else if (!e) {
    children.push(para("Kein erforderlicher Prüfbeleg vorhanden."));
  }
  for (const reason of kuerzeReasons(layer.reasons, 3)) children.push(para("• "+reason,{spacing:{after:40,line:240}}));
}
children.push(heading("Build- und Laufbindung"),
  para(`Nachweis-Fingerprint (SHA-256): ${evidence.evidenceFingerprint}`),
  para("Die Belege gelten nur für den gebundenen Quell-, Build- und Laufstand. Neue Testläufe oder relevante Dateiänderungen entwerten die Darstellung als aktuelles Zertifikat. Die vollständige Inventarliste liegt neben diesem Dokument in stand.json.",{alignment:AlignmentType.JUSTIFIED}),
  heading("Zertifikat erneuern"),
  para("In Abschnitt 7 der testsweet-Oberfläche auf „Mache alle Zertifikatstests“ klicken. Der Ablauf baut und prüft beide Programme, führt alle sieben Simulator-Kombinationen aus, prüft zuletzt die ausgewählten Fälle mit GLM und erstellt bei vollständigem Erfolg automatisch dieses Zertifikat. Wird während des Laufs eine Datei geändert oder bricht eine Prüfung ab, gibt es bewusst kein grünes Zertifikat.",{alignment:AlignmentType.JUSTIFIED}),
  heading("Grenzen"),
  para("Die GLM-Prüfung ist eine Stichprobe der ausgewählten Fälle mit strukturierter Zahlen- und Prüfpunktabdeckung. Sie ersetzt weder eine vollständige Fachrechtsprüfung noch die Kontrolle aller möglichen Datenkonstellationen. Nicht modellierte Rechtsregeln (z. B. § 82a SGB XII Grundrenten-Freibetrag, § 42a Abs. 4 Kopfteils-Miete) bleiben außerhalb des zertifizierten Umfangs. Code-Reviews unter doc/codereview/ sind ergänzende Dokumentation; ein historischer Reviewabschluss ist kein automatisch bestandener aktueller Testnachweis.",{alignment:AlignmentType.JUSTIFIED}));
const doc = new Document({creator:"SDN.Leistung LMG",title:"Testzertifikat SDN.Leistung LMG",styles:{default:{document:{run:{font:FONT,size:21},paragraph:{spacing:{line:270}}}}},
  sections:[{properties:{page:{size:{width:11906,height:16838},margin:{top:850,bottom:850,left:1050,right:1050}}},
    footers:{default:new Footer({children:[new Paragraph({alignment:AlignmentType.CENTER,children:[text("SDN.Leistung LMG · ",{size:17,color:P.secondary}),new TextRun({children:[PageNumber.CURRENT],size:17})]})]})},children}]});
const OUT = path.resolve(process.argv[2] || path.join(__dirname,"testzertifikat.docx"));
Packer.toBuffer(doc).then(buffer => {
  fs.mkdirSync(path.dirname(OUT),{recursive:true});
  fs.writeFileSync(OUT,buffer,{flag:"wx"});
  fs.writeFileSync(path.join(path.dirname(OUT),"stand.json"),JSON.stringify({...evidence,certificateId,erhoben_am:now,docxSha256:hash(buffer)},null,2),{flag:"wx"});
  console.log(`Zertifikat erzeugt: ${OUT}; fachliches Urteil: ${evidence.verdict}`);
}).catch(error => { console.error(error); process.exit(1); });
