// Management-Testreport für SDN.Leistung (LMG): erzeugt bei jedem Aufruf frisch
// einen professionellen PDF-Bericht mit Titelbild, Einleitung, Inhaltsverzeichnis
// mit echten Seitenzahlen (Zwei-Durchlauf über pdftotext), fixen Erklärtkapiteln
// (Testarten, Fachschicht/Selenium, Sollwert-Herkunft, echte Fälle, externer
// Rechner, parallele Transaktionssteuerung), dynamischen Ergebnistabellen aus
// testsweet/logs und einer Gesamtbewertung des erklärten Prüfumfangs.
//
// Aufruf:  node publish-report.js --publish <repo>   (erzeugt und veröffentlicht)
//          node publish-report.js --verify <repo>    (prüft Aktualität der PDF)
// Der Report informiert das Management; der formale Nachweis bleibt das
// Testzertifikat (doc/testzertifikat-werkzeug) mit seinen strengen Belegen.
const fs = require("fs");
const path = require("path");
const os = require("os");
const crypto = require("crypto");
const {spawnSync} = require("child_process");
const {pathToFileURL} = require("url");
const {Packer} = require("./report-library").docx;
const runs = require("./runs");
const {KAPITEL,baueDokument,ergebnisTabelle,kapitelFazit,zertifikatsSeite,stateLabel}=require("./report-layout");

function soffice() {
  return fs.existsSync("C:/Program Files/LibreOffice/program/soffice.com")
    ? "C:/Program Files/LibreOffice/program/soffice.com" : "soffice";
}
function cleanTemporary(directory, prefix) {
  const resolved=path.resolve(directory),base=path.resolve(os.tmpdir())+path.sep;
  if (!resolved.startsWith(base) || !path.basename(resolved).startsWith(prefix)) throw Error("Ungültiges temporäres Cleanupziel");
  fs.rmSync(resolved,{recursive:true,force:true});
}
function nachPdf(docxPfad, ausgabeOrdner) {
  const profil=fs.mkdtempSync(path.join(os.tmpdir(),"report-office-"));
  try {
    const result=spawnSync(soffice(),["-env:UserInstallation="+pathToFileURL(profil).href,"--headless","--convert-to","pdf","--outdir",ausgabeOrdner,docxPfad],{encoding:"utf8",timeout:120000});
    if (result.error || result.status!==0) throw Error("soffice fehlgeschlagen: "+(result.error?.message || result.stderr || result.stdout));
    const pdf=path.join(ausgabeOrdner,path.basename(docxPfad,".docx")+".pdf");
    if (!fs.existsSync(pdf) || fs.readFileSync(pdf).subarray(0,5).toString("ascii")!=="%PDF-") throw Error("PDF fehlt");
    return pdf;
  } finally { cleanTemporary(profil,"report-office-"); }
}
// Nur Kapitelüberschriften nach Titel/Verzeichnis; erlaubte Einrückung durch Satz.
const SUCHER=[/^[ \t]*Einleitung\b/m,/^[ \t]*1\s+Testarten\b/m,/^[ \t]*2\s+Zwei\b/m,/^[ \t]*3\s+Herkunft\b/m,
  /^[ \t]*4\s+Quellenbasierte\b/m,/^[ \t]*5\s+Unabh/m,/^[ \t]*6\s+Transaktionssteuerung\b/m,/^[ \t]*7\s+Ergebnis/m,/^[ \t]*8\s+Fazit\b/m,/^[ \t]*Gesamtbewertung des Pr/m];
function chapterPages(raw) {
  const pages=raw.split("\f");
  const selected=SUCHER.map((anchor,index)=>{
    const hits=[];
    for(let page=2;page<pages.length;page++) {
      const matches=[...pages[page].matchAll(new RegExp(anchor.source,"gm"))];
      for(const match of matches)hits.push({page:page+1,offset:match.index});
    }
    if(hits.length!==1)throw Error("Kapitelanker fehlt oder ist mehrdeutig: "+KAPITEL[index]);
    return hits[0];
  });
  if(selected.some((hit,index)=>!Number.isInteger(hit.page) || hit.page<3 || index>0 &&
      (hit.page<selected[index-1].page || hit.page===selected[index-1].page && hit.offset<=selected[index-1].offset)))
    throw Error("Kapitelreihenfolge/Seitenzahlen ungültig");
  return selected.map(hit=>hit.page);
}
function pdfDetails(pdf) {
  const result=spawnSync("pdftotext",["-layout",pdf,"-"],{encoding:"utf8",timeout:60000});
  if(result.error || result.status!==0)throw Error("pdftotext fehlgeschlagen: "+(result.error?.message || result.stderr || ""));
  const pages=result.stdout.split("\f"),count=pages.length-(pages.at(-1).trim()===""?1:0);
  return {chapters:chapterPages(result.stdout),count};
}
function seitenErmitteln(pdf) { return pdfDetails(pdf).chapters; }
function hash(file){return crypto.createHash("sha256").update(fs.readFileSync(file)).digest("hex");}
function assertInputs(repo,fingerprint){if(runs.inputFingerprint(repo)!==fingerprint)throw Error("Prüfeingaben wurden während des Exports verändert — neu erstellen");}
function reportPaths(repo){
  return {manifest:path.join(repo,"doc/testreport-werkzeug/stand-report.json"),pdf:path.join(repo,"doc/SDN-Leistung-LMG-Testreport.pdf"),docx:path.join(repo,"doc/SDN-Leistung-LMG-Testreport.docx")};
}
function atomicCopy(source,target,id){
  const temporary=target+"."+id+".tmp";
  try{fs.copyFileSync(source,temporary);fs.renameSync(temporary,target);}finally{if(fs.existsSync(temporary))fs.unlinkSync(temporary);}
}
function acquirePublishLock(file) {
  const token=crypto.randomUUID();
  const openOwned=()=>{
    const descriptor=fs.openSync(file,"wx");
    try{fs.writeFileSync(descriptor,JSON.stringify({pid:process.pid,token}));return {descriptor,token};}
    catch(error){fs.closeSync(descriptor);fs.unlinkSync(file);throw error;}
  };
  try { return openOwned(); }
  catch (error) {
    if(error.code!=="EEXIST")throw error;
    // Nur ein Prozess darf eine verwaiste Sperre prüfen und entfernen. Ohne
    // dieses Gate könnten zwei Wiederanläufe die neue Sperre des anderen löschen.
    const recovery=file+".recovery";
    let gate;try{gate=fs.openSync(recovery,"wx");}catch(_){throw Error("Report-Exportsperre wird gerade geprüft; bei dauerhaftem Fehler manuell prüfen: "+recovery);}
    try{
      if(!fs.existsSync(file))return openOwned();
      let previous;try{previous=JSON.parse(fs.readFileSync(file,"utf8"));}catch(_){throw Error("Unlesbare Report-Exportsperre; manuell prüfen: "+file);}
      if(!Number.isInteger(previous.pid) || previous.pid<=0 || typeof previous.token!=="string")throw Error("Ungültige Report-Exportsperre; manuell prüfen: "+file);
      try{process.kill(previous.pid,0);throw Error("Ein Reportexport läuft bereits (PID "+previous.pid+")");}
      catch(probe){if(probe.code!=="ESRCH")throw probe;}
      fs.unlinkSync(file);return openOwned();
    }finally{fs.closeSync(gate);fs.unlinkSync(recovery);}
  }
}
function releasePublishLock(file,owned) {
  fs.closeSync(owned.descriptor);
  if(fs.existsSync(file) && JSON.parse(fs.readFileSync(file,"utf8")).token===owned.token)fs.unlinkSync(file);
}
async function veroeffentliche(repo) {
  const paths=reportPaths(repo),lock=path.join(repo,"doc/testreport-werkzeug/publish.lock");
  const owned=acquirePublishLock(lock);
  let staging;
  try{
    const inventory=runs.inputInventory(repo),fingerprint=crypto.createHash("sha256").update(JSON.stringify(inventory)).digest("hex"),stand=runs.reportStand(repo);
    assertInputs(repo,fingerprint);
    const datum=new Date().toLocaleDateString("de-DE",{timeZone:"Europe/Berlin",year:"numeric",month:"2-digit",day:"2-digit"}),reportId=crypto.randomUUID().slice(0,8).toUpperCase();
    staging=fs.mkdtempSync(path.join(os.tmpdir(),"testreport-"));
    const first=path.join(staging,"pass1.docx");
    fs.writeFileSync(first,await Packer.toBuffer(baueDokument(repo,datum,reportId,null,stand)));
    const pages=seitenErmitteln(nachPdf(first,staging));
    const second=path.join(staging,"SDN-Leistung-LMG-Testreport.docx");
    fs.writeFileSync(second,await Packer.toBuffer(baueDokument(repo,datum,reportId,pages,stand)));
    const pdf=nachPdf(second,staging),details=pdfDetails(pdf),finalPages=details.chapters;
    if(JSON.stringify(pages)!==JSON.stringify(finalPages))throw Error("Kapitelstartseiten haben sich im zweiten Durchlauf verändert — Veröffentlichung abgebrochen");
    assertInputs(repo,fingerprint);
    const marker={schemaVersion:2,reportId,erzeugtAm:new Date().toISOString(),verdict:stand.verdict,inputFingerprint:fingerprint,inputInventory:inventory,docxSha256:hash(second),pdfSha256:hash(pdf)};
    atomicCopy(second,paths.docx,reportId);atomicCopy(pdf,paths.pdf,reportId);
    assertInputs(repo,fingerprint);
    const markerFile=paths.manifest+"."+reportId+".tmp";
    try{fs.writeFileSync(markerFile,JSON.stringify(marker,null,2));fs.renameSync(markerFile,paths.manifest);}finally{if(fs.existsSync(markerFile))fs.unlinkSync(markerFile);}
    const count=stand.letzte.length,good=stand.letzte.filter(x=>x.lauf.erfolgreich).length;
    const result={state:"READY",reportId,pdf:paths.pdf,pdfSha256:marker.pdfSha256,verdict:stand.verdict,laeufe:count,davonErfolgreich:good,seiten:details.count};
    process.stdout.write(JSON.stringify(result)+"\n");return result;
  }finally{
    try{releasePublishLock(lock,owned);}finally{if(staging)cleanTemporary(staging,"testreport-");}
  }
}
function pruefe(repo) {
  const paths=reportPaths(repo);
  if(!fs.existsSync(paths.manifest) || !fs.existsSync(paths.pdf) || !fs.existsSync(paths.docx))throw Error("Kein vollständig veröffentlichter Report vorhanden");
  const marker=JSON.parse(fs.readFileSync(paths.manifest,"utf8"));
  if(marker.schemaVersion!==2 || !marker.reportId || !Array.isArray(marker.inputInventory) || !marker.inputFingerprint)throw Error("Report ohne gültige Eingabebindung — neu erstellen");
  if(hash(paths.pdf)!==marker.pdfSha256 || hash(paths.docx)!==marker.docxSha256)throw Error("Reportdateien stimmen nicht mit dem veröffentlichten Stand überein");
  assertInputs(repo,marker.inputFingerprint);
  return {state:"READY",reportId:marker.reportId,pdfSha256:marker.pdfSha256,verdict:marker.verdict};
}
if(require.main===module){
  const command=process.argv[2],repo=path.resolve(process.argv[3] || path.join(__dirname,"../.."));
  if(command==="--publish")veroeffentliche(repo).catch(error=>{console.error(error.message);process.exitCode=1;});
  else if(command==="--verify"){try{process.stdout.write(JSON.stringify(pruefe(repo))+"\n");}catch(error){console.error(error.message);process.exitCode=1;}}
  else{console.error("Aufruf: node publish-report.js --publish|--verify <repo>");process.exitCode=2;}
}
module.exports={baueDokument,ergebnisTabelle,kapitelFazit,zertifikatsSeite,chapterPages,seitenErmitteln,assertInputs,pruefe,veroeffentliche,stateLabel};
