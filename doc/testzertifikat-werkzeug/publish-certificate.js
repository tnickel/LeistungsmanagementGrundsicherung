// Ein gemeinsamer Exportweg für Batch und GUI: neue Ausgaben im frischen Staging,
// Veröffentlichung erst nach erfolgreichen Prozessen und unveränderten Prüfbelegen.
const fs = require("fs");
const path = require("path");
const crypto = require("crypto");
const os = require("os");
const {spawnSync} = require("child_process");
const {pathToFileURL} = require("url");
const {isDeepStrictEqual} = require("util");
const {collect,hash,inside} = require("./certificate-evidence");
function atomicJson(file,value) {
  fs.mkdirSync(path.dirname(file),{recursive:true});
  const temp = file + "." + crypto.randomUUID() + ".tmp";
  fs.writeFileSync(temp,JSON.stringify(value,null,2),{flag:"wx"});
  fs.renameSync(temp,file);
}
function current(repo) {
  const file = path.join(repo,"doc/testzertifikat-werkzeug/current-certificate.json");
  const marker = JSON.parse(fs.readFileSync(file,"utf8"));
  if (marker.state !== "READY" || marker.schemaVersion !== 2) throw Error("Kein aktuell erfolgreich veröffentlichtes Zertifikat vorhanden");
  const evidence = collect(repo);
  if (marker.evidenceFingerprint !== evidence.evidenceFingerprint) throw Error("Zertifikat gehört zu älteren oder veränderten Prüfbelegen; neu erstellen");
  if (!isDeepStrictEqual(marker.scope,evidence.scope)) throw Error("Zertifikatsumfang fehlt oder wurde verändert; neu erstellen");
  if (marker.verdict !== evidence.verdict) throw Error("Zertifikatsurteil widerspricht den aktuellen Prüfbelegen; neu erstellen");
  for (const output of marker.files) if (hash(inside(repo,output.path)) !== output.sha256) throw Error("Zertifikatsdatei fehlt oder wurde verändert");
  return marker;
}
function publish(repo,options={}) {
  const tool = path.join(repo,"doc/testzertifikat-werkzeug"), id = crypto.randomUUID();
  const staging = path.join(repo,"testsweet/logs","certificate-" + id);
  const markerFile = path.join(tool,"current-certificate.json");
  const lockFile = path.join(tool,"current-certificate.lock");
  let officeProfile=null,officeTimedOut=false;
  fs.mkdirSync(tool,{recursive:true});
  if (fs.existsSync(lockFile)) {
    try {
      const owner = JSON.parse(fs.readFileSync(lockFile,"utf8"));
      try { process.kill(owner.pid,0); } catch (error) { if (error.code === "ESRCH") fs.unlinkSync(lockFile); }
    } catch (_) { /* Unlesbare Sperre bleibt konservativ gesperrt. */ }
  }
  let lock;
  try { lock=fs.openSync(lockFile,"wx");fs.writeFileSync(lock,JSON.stringify({pid:process.pid,id})); }
  catch (_) {
    if (lock !== undefined) { fs.closeSync(lock);fs.unlinkSync(lockFile); }
    throw Error("Ein Zertifikat wird bereits erzeugt oder die Exportsperre konnte nicht geschrieben werden");
  }
  const run = options.run || ((command,args) => {
    const result = spawnSync(command,args,{cwd:tool,encoding:"utf8",timeout:180000,windowsHide:true});
    fs.appendFileSync(path.join(staging,"export.log"),`${path.basename(command)}: Exit ${result.status}\n${result.stdout || ""}\n${result.stderr || ""}\n`);
    if (result.error || result.status !== 0) {
      const failure=Error(`${path.basename(command)} fehlgeschlagen: ${result.error?.message || "Exit " + result.status}`);
      failure.exportProcessTimedOut=result.error?.code === "ETIMEDOUT";
      throw failure;
    }
  });
  try {
    atomicJson(markerFile,{schemaVersion:2,state:"GENERATING",certificateId:id});
    fs.mkdirSync(path.dirname(staging),{recursive:true});
    fs.mkdirSync(staging,{recursive:false});
    const docx = path.join(staging,"testzertifikat.docx"), pdf = path.join(staging,"testzertifikat.pdf");
    run(process.execPath,[path.join(tool,"generate.js"),docx]);
    if (!fs.statSync(docx).isFile() || !fs.statSync(docx).size) throw Error("Neue DOCX fehlt oder ist leer");
    const standPath = path.join(staging,"stand.json"), stand = JSON.parse(fs.readFileSync(standPath,"utf8"));
    if (stand.schemaVersion !== 2 || hash(fs.readFileSync(docx)) !== stand.docxSha256) throw Error("DOCX gehört nicht zum neuen Nachweisstand");
    const soffice = options.soffice || (fs.existsSync("C:/Program Files/LibreOffice/program/soffice.com") ? "C:/Program Files/LibreOffice/program/soffice.com" : "soffice");
    // LibreOffice erzeugt tiefe Profil-Unterverzeichnisse. Unter einem langen
    // Projekt-/Stagingpfad kann der Windows-Export mit 0xC0000409 abstürzen.
    officeProfile=fs.mkdtempSync(path.join(os.tmpdir(),"lmg-lo-"));
    fs.appendFileSync(path.join(staging,"export.log"),`LibreOffice-Profil (exklusiv für diesen Export): ${officeProfile}\n`);
    try {
      run(soffice,["-env:UserInstallation=" + pathToFileURL(officeProfile).href,
        "--headless","--convert-to","pdf","--outdir",staging,docx]);
    } catch (error) { officeTimedOut=error.exportProcessTimedOut===true;throw error; }
    if (!fs.statSync(pdf).isFile() || fs.statSync(pdf).size < 100 || fs.readFileSync(pdf).subarray(0,5).toString("ascii") !== "%PDF-") throw Error("Neue gültige PDF fehlt");
    const latest = collect(repo);
    if (stand.evidenceFingerprint !== latest.evidenceFingerprint) throw Error("Prüfbelege haben sich während der Zertifikatserstellung geändert");
    if (!isDeepStrictEqual(stand.scope,latest.scope) || stand.verdict !== latest.verdict)
      throw Error("Zertifikatsumfang oder Urteil widerspricht den aktuellen Prüfbelegen");
    const outputs = [
      [docx,path.join(tool,"testzertifikat.docx")], [pdf,path.join(tool,"testzertifikat.pdf")],
      [docx,path.join(repo,"doc/SDN-Leistung-LMG-Testzertifikat.docx")], [pdf,path.join(repo,"doc/SDN-Leistung-LMG-Testzertifikat.pdf")],
      [standPath,path.join(tool,"stand.json")],
    ];
    // Der READY-Marker wird zuletzt geschrieben; teilweise ersetzte Dateien sind
    // bei jedem Kopier-/Renamefehler ausdrücklich kein aktueller Nachweis.
    const files = [];
    for (const [source,target] of outputs) {
      const temp = target + "." + id + ".tmp";
      fs.copyFileSync(source,temp,fs.constants.COPYFILE_EXCL);
      fs.renameSync(temp,target);
      files.push({path:path.relative(repo,target).replace(/\\/g,"/"),sha256:hash(fs.readFileSync(target))});
    }
    const marker = {schemaVersion:2,state:"READY",certificateId:stand.certificateId,verdict:stand.verdict,scope:stand.scope,
      evidenceFingerprint:stand.evidenceFingerprint,publishedAt:new Date().toISOString(),
      staging:path.relative(repo,staging).replace(/\\/g,"/"),files};
    atomicJson(markerFile,marker);
    return marker;
  } catch (error) {
    atomicJson(markerFile,{schemaVersion:2,state:"ERROR",certificateId:id,message:error.message,staging:path.relative(repo,staging).replace(/\\/g,"/")});
    throw error;
  } finally {
    if (officeProfile && !officeTimedOut) {
      // Ausschließlich das selbst angelegte, eindeutig benannte Temp-Profil
      // entfernen. Nach einem Timeout könnte ein nativer Kindprozess es noch nutzen.
      const tempRoot=path.resolve(os.tmpdir()),owned=path.resolve(officeProfile);
      if (owned.startsWith(tempRoot+path.sep) && path.basename(owned).startsWith("lmg-lo-")) {
        try { fs.rmSync(owned,{recursive:true,force:true}); }
        catch (error) { fs.appendFileSync(path.join(staging,"export.log"),`Temp-Profil konnte nicht entfernt werden: ${owned}; ${error.message}\n`); }
      }
    }
    fs.closeSync(lock);
    fs.unlinkSync(lockFile);
  }
}
if (require.main === module) {
  try {
    const repo = path.resolve(process.argv[3] || path.join(__dirname,"../.."));
    const result = process.argv[2] === "--verify" ? current(repo) : process.argv[2] === "--publish" ? publish(repo) : null;
    if (!result) throw Error("Aufruf: node publish-certificate.js --publish|--verify [Projektpfad]");
    console.log(JSON.stringify(result));
  } catch (error) { console.error("Zertifikatexport fehlgeschlagen:",error.message);process.exitCode=1; }
}
module.exports = {publish,current,atomicJson};
