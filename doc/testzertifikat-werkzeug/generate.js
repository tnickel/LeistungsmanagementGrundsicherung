// Erzeugt auch negative/unvollständige Zertifikate; Erfolg wird ausschließlich aus Belegen abgeleitet.
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
  PASS:"E6F4EA",FAIL:"FCE8E6",UNVERIFIED:"FFF4CC",NA:"EDF2F5"};
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
function summary(layer) {
  const e = layer.evidence;
  if (!e) return "Kein erforderlicher Prüfbeleg vorhanden.";
  if (layer.id.startsWith("JUNIT:")) return `Erfasster Umfang: ${e.passed}/${e.tests} bestanden; ${e.failures} Fehler, ${e.errors} technische Fehler, ${e.skipped} übersprungen.`;
  if (layer.id === "GLM") return `${e.meta?.pruefumfang || "Prüfung unvollständig"}; Modell ${e.meta?.modell || "unbekannt"}; Dateiübergabe ${e.transportVerified ? "vollständig belegt" : "nicht vollständig belegt"}.`;
  if (layer.id === "UNZUGEORDNETE_LAEUFE") return "Laufmetadaten fehlen; ältere erfolgreiche Läufe ergeben keine Freigabe.";
  if (layer.id === "BUILD") return layer.state === "PASS" ? "Alle Pflichtbelege sind an denselben aktuellen Produkt- und Prüferklassenbestand gebunden." : "Buildnachweise fehlen, sind veraltet oder betreffen unterschiedliche Klassenbestände.";
  if (layer.id === "CERTIFICATE_SUITE") return layer.state === "PASS"
    ? `Gesamtlauf ${e.jobId}; gestartet ${e.startedAt}. Beide automatischen Testsuiten, alle sieben Simulator-Kombinationen und die GLM-Stichprobe sind frisch und an diesen Lauf gebunden.`
    : `Gesamtlauf ${e.jobId || "unbekannt"}: ${e.message || "nicht vollständig bestätigt"}. Der Gesamtlauf liefert keinen gültigen Erfolgsnachweis.`;
  return `${e.verifiziert || 0}/${Number.isFinite(e.gesamt) ? e.gesamt : "?"} verifiziert; ${e.ordner}`;
}
const rows = [...evidence.layers,{id:"WEB:STRESS",state:"NA",reasons:[],evidence:null}];
const verdictColor = evidence.verdict === "BESTANDEN" ? P.pass : evidence.verdict === "NICHT BESTANDEN" ? P.fail : P.warn;
const table = new Table({width:{size:100,type:WidthType.PERCENTAGE},borders:{top:{style:BorderStyle.NONE},bottom:{style:BorderStyle.NONE},
  left:{style:BorderStyle.NONE},right:{style:BorderStyle.NONE},insideHorizontal:{style:BorderStyle.SINGLE,size:3,color:"FFFFFF"},insideVertical:{style:BorderStyle.NONE}},
  rows:rows.map(layer => new TableRow({cantSplit:true,children:[new TableCell({shading:{type:ShadingType.CLEAR,fill:P[layer.state]},margins:{top:100,bottom:100,left:150,right:150},
    children:[para("",{spacing:{after:50},children:[text(`${labels[layer.id] || layer.id} - ${stateLabels[layer.state]}`,{bold:true,size:21,color:layer.state === "FAIL" ? P.fail : P.primary})]}),
      para("",{spacing:{after:10,line:245},children:[text(layer.state === "NA" ? "Web-Stress wird nicht unterstützt; kein bestandenes Testergebnis." : summary(layer),{size:18,color:P.secondary})]})]})]}))});
const children = [
  para("TESTZERTIFIKAT",{spacing:{after:100},children:[text("TESTZERTIFIKAT",{bold:true,size:20,color:"136D7D"})]}),
  para("SDN.Leistung LMG",{spacing:{after:100},children:[text("SDN.Leistung LMG",{bold:true,size:38})]}),
  para("Leistungsmanagement Grundsicherung - Berechnung, Bescheid und Auszahlung"),
  para(evidence.scope.label,{children:[text(evidence.scope.label,{bold:true})]}),
  para("Das Urteil umfasst beide automatischen Testsuiten, vier Fachschichtläufe (POSITIV, NEGATIV, LAST, STRESS), drei Browserläufe (POSITIV, NEGATIV, LAST) und die POSITIV-/NEGATIV-GLM-Stichprobe. Die fünf quellenbasierten ECHT-Modellfälle und zwölf RECHNER-Vergleichsfälle gehören nicht zu diesem Zertifikat; sie benötigen eigene Nachweise."),
  para(evidence.verdict,{spacing:{before:100,after:150},children:[text(evidence.verdict,{bold:true,size:32,color:verdictColor})]}),
  para("",{children:[text(`Ausgestellt: ${now} · Zertifikat-ID: ${certificateId}`,{size:18,color:P.secondary})]}),
  para("BESTANDEN gilt ausschließlich für den oben genannten Prüfumfang einschließlich seiner Reporterstellung. Jeder bekannte fachliche Fehler im ausgewerteten Umfang verhindert BESTANDEN. Fehlende, abgebrochene, übersprungene oder veraltete Pflichtnachweise ergeben UNVOLLSTÄNDIG."),
  para("LAST und STRESS prüfen die Funktion bei paralleler Wiederholung beziehungsweise in zwei Bearbeitungswellen. Ihre begrenzten Fallzahlen belegen keine Dauerlast, keine Sättigungsgrenze und keine Produktionskapazität. Das Zertifikatsurteil enthält keinen solchen Kapazitätsnachweis."),
  table,
  para("Code-Reviews sind ergänzende Dokumentation unter doc/codereview/. Ein historischer Reviewabschluss ist kein automatisch bestandener aktueller Testnachweis."),
  para("Prüfbelege und Befunde",{heading:HeadingLevel.HEADING_1,pageBreakBefore:true,children:[text("Prüfbelege und Befunde",{bold:true,size:30})]}),
];
for (const layer of evidence.layers) {
  children.push(heading(`${labels[layer.id] || layer.id}: ${stateLabels[layer.state]}`),para(summary(layer)));
  for (const reason of layer.reasons || []) children.push(para("• " + reason));
  const e = layer.evidence;
  if (layer.id === "BUILD") for (const artifact of e.artifacts) for (const sha256 of artifact.classesSha256) children.push(para(`${artifact.name}: Klassenbestand SHA-256 ${sha256}`));
  if (layer.id.startsWith("JUNIT:") && e) {
    for (const suite of e.suites) {
      children.push(para(`• ${suite.name}: ${suite.tests-suite.failures-suite.errors-suite.skipped}/${suite.tests} bestanden, Fehler ${suite.failures+suite.errors}, übersprungen ${suite.skipped}`));
      for (const c of suite.cases.filter(c=>c.state === "FAIL" || c.state === "ERROR")) children.push(para("",{children:[text(`• ${c.classname}.${c.name}: ${c.state} - ${c.type} ${c.message}`,{bold:true,color:P.fail})]}));
    }
  } else if (layer.id === "GLM" && e) {
    children.push(para("Nachweis: " + e.datei));
    if (e.provisionalFailures?.length) {
      children.push(heading("Ungeklärte Hinweise aus früheren ungültigen Modellantworten"),
        para("Diese Antworten sind formal ungültig und bestätigen keinen Softwarefehler. Die Hinweise wurden durch eine spätere PASS-Antwort nicht geklärt; deshalb bleibt die Gesamtprüfung UNVOLLSTÄNDIG. Logdateien auswerten."));
      for (const observation of e.provisionalFailures) children.push(para("",{children:[text(`${observation.caseId} - Fallgruppe ${observation.batch}, Versuch ${observation.attempt}: ${observation.reason}; Request ${observation.requestFile}; Response ${observation.responseFile}`,{bold:true,color:P.warn})]}));
    }
    for (const c of e.cases || []) {
      const refs=(e.referenceChecks || []).filter(item=>item.caseId === c.caseId);
      const caseState=c.verdict === "FAIL" || refs.some(r=>r.state === "FAIL") ? "FAIL" : c.verdict === "UNVERIFIED" || refs.some(r=>r.state === "UNVERIFIED") || e.provisionalFailures?.some(row=>row.caseId === c.caseId) ? "UNVERIFIED" : c.verdict;
      children.push(heading(`${c.caseId}: ${e.evidenceVerified ? stateLabels[caseState] || caseState : "UNVERIFIZIERTES MODELL-ROHURTEIL " + c.verdict}`));
      for (const finding of c.findings || []) children.push(para("",{children:[text("• " + finding,{bold:true,color:P.fail})]}));
      children.push(para(c.reasoning || "Keine vollständige Rechenbegründung vorhanden."));
      for (const block of c.calculations || []) {
        children.push(para(`Rechenblock ${block.blockId}`,{keepNext:true,children:[text(`Rechenblock ${block.blockId}`,{bold:true})]}));
        for (const person of block.persons || []) children.push(para(`${person.personId}: `+Object.entries(person.values || {}).map(([field,value])=>`${field} = ${value} EUR`).join("; ")));
        children.push(para("Bedarfsgemeinschaft: "+Object.entries(block.household || {}).map(([field,value])=>`${field} = ${value} EUR`).join("; ")));
      }
      for (const check of c.checks || []) children.push(para("",{children:[text(`${check.checkId}: ${e.evidenceVerified ? stateLabels[check.verdict] || check.verdict : "UNVERIFIZIERTES ROHURTEIL " + check.verdict} - ${check.reasoning}`,{color:check.verdict === "FAIL"?P.fail:check.verdict === "UNVERIFIED" || !e.evidenceVerified?P.warn:P.primary,bold:check.verdict === "FAIL"})]}));
      for (const reference of refs) {
        for (const finding of [...(reference.findings || []),...(reference.arithmeticIssues || []),...(reference.missingInputs || []),...(reference.workflowFindings || []),...(reference.workflowUnverified || [])]) children.push(para("",{children:[text("• Unabhängige Referenzprüfung: " + finding,{bold:true,color:reference.state === "FAIL"?P.fail:P.warn})]}));
      }
    }
    for (const finding of e.knownFindings || []) if (!(e.cases || []).some(c=>c.findings?.includes(finding))) children.push(para("• " + finding));
    children.push(para("Die Hashes belegen die lokal protokollierten gesendeten Dokumentbytes und empfangenen API-Antworten. Sie beweisen keine interne Verarbeitung beim Modellanbieter und keine allgemeine Fehlerfreiheit der Software."));
    for (const doc of e.documents || []) children.push(para(`• ${doc.name}: ${doc.bytes} Bytes; SHA-256 ${doc.sha256}`));
  }
}
children.push(heading("Build- und Laufbindung"),para(`Nachweis-Fingerprint (SHA-256): ${evidence.evidenceFingerprint}`),
  para("Die Belege gelten nur für den gebundenen Quell-, Build- und Laufstand. Neue Testläufe oder relevante Dateiänderungen entwerten die Darstellung als aktuelles Zertifikat. Die vollständige Inventarliste liegt neben diesem Dokument in stand.json."),
  heading("Zertifikat erneuern"),
  para("In Abschnitt 7 der testsweet-Oberfläche auf „Mache alle Zertifikatstests“ klicken. Der Ablauf baut und prüft beide Programme, führt alle sieben Simulator-Kombinationen aus, prüft zuletzt die ausgewählten Fälle mit GLM und erstellt bei vollständigem Erfolg automatisch dieses Zertifikat. Fortschrittsbalken zeigen Phasen und tatsächliche Fälle; eine verlässliche Restzeit wird nicht behauptet."),
  para("Bei einer Testabweichung oder einem technischen Fehler stoppt der Ablauf. Die Fehlermeldung nennt den betroffenen Schritt und das Protokoll. Ein Abbruch oder fehlender Pflichtnachweis kann kein grünes Zertifikat erzeugen."),
  heading("Einzelprüfungen manuell ausführen"),
  para("1. Beide Maven-Testsuiten einschließlich der verpflichtenden Node-Prüfer für Zertifikatsbelege, Rechenreferenz, Antwortvertrag und Browser-Skripte mit den benötigten Datenbankprüfungen vollständig ausführen. Übersprungene Prüfungen bei identischem Build gesondert nachholen."),
  para("2. Fachschicht POSITIV, NEGATIV, LAST und STRESS sowie Weboberfläche POSITIV, NEGATIV und LAST vollständig durchführen."),
  para("3. GLM-Prüfung auf den aktuellen vollständigen Quellberichten starten und Befunde auswerten."),
  para("4. Zertifikat neu erstellen. Auch ein negatives oder unvollständiges Ergebnis wird dokumentiert; eine erfolgreiche Dateierzeugung ist keine fachliche Freigabe."),
  heading("Grenzen"),para("Die GLM-Prüfung ist eine Stichprobe der ausgewählten Fälle mit strukturierter Zahlen- und Prüfpunktabdeckung. Sie ersetzt weder eine vollständige Fachrechtsprüfung noch die Kontrolle aller möglichen Datenkonstellationen. Nicht modellierte Rechtsregeln bleiben außerhalb des zertifizierten Umfangs."));
const doc = new Document({creator:"SDN.Leistung LMG",title:"Testzertifikat SDN.Leistung LMG",styles:{default:{document:{run:{font:FONT,size:21},paragraph:{spacing:{line:270}}}}},
  sections:[{properties:{page:{size:{width:11906,height:16838},margin:{top:850,bottom:850,left:1050,right:1050}}},
    footers:{default:new Footer({children:[new Paragraph({alignment:AlignmentType.CENTER,children:[text("SDN.Leistung LMG · ",{size:17,color:P.secondary}),new TextRun({children:[PageNumber.CURRENT],size:17})]})]})},children}]});
const OUT = path.resolve(process.argv[2] || path.join(__dirname,"testzertifikat.docx"));
Packer.toBuffer(doc).then(buffer => {
  fs.mkdirSync(path.dirname(OUT),{recursive:true});
  fs.writeFileSync(OUT,buffer,{flag:"wx"});
  fs.writeFileSync(path.join(path.dirname(OUT),"stand.json"),JSON.stringify({...evidence,certificateId,erhoben_am:now,docxSha256:hash(buffer)},null,2),{flag:"wx"});
  console.log(`Zertifikat erzeugt: ${OUT}; fachliches Urteil: ${evidence.verdict}`);
}).catch(error=>{console.error("Zertifikaterzeugung fehlgeschlagen:",error.message);process.exitCode=1;});
