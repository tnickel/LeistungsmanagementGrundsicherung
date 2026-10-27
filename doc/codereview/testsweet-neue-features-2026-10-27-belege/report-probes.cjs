const fs=require('fs'),path=require('path'),os=require('os'),vm=require('vm'),{createRequire}=require('module');
const repo=process.cwd(),sourceFile=path.join(repo,'doc/testreport-werkzeug/publish-report.js');
const source=fs.readFileSync(sourceFile,'utf8'),localRequire=createRequire(sourceFile),runs=localRequire('./runs');
function load(fakeSpawn) {
 const captured=[];const actualDocx=localRequire('../handbuch-werkzeug/node_modules/docx');
 const docx={...actualDocx,TextRun:new Proxy(actualDocx.TextRun,{construct(target,args){if(args[0]?.text)captured.push(args[0].text);return Reflect.construct(target,args);}})};
 const out=[];const c={module:{exports:{}},exports:{},__dirname:path.dirname(sourceFile),require:(name)=>name==='child_process'&&fakeSpawn?{spawnSync:fakeSpawn}:name==='../handbuch-werkzeug/node_modules/docx'?docx:localRequire(name),process:{stdout:{write:s=>out.push(s)},argv:[]},console};
 vm.runInNewContext(source+'\nmodule.exports.test={pruefe,seitenErmitteln,zertifikatsSeite,ergebnisTabelle};',c,{filename:sourceFile});
 return {f:c.module.exports.test,captured,out};
}
const temp=fs.mkdtempSync(path.join(os.tmpdir(),'testsweet-report-probe-'));
function state(name,data){const dir=path.join(temp,'testsweet/logs',name);fs.mkdirSync(dir,{recursive:true});fs.writeFileSync(path.join(dir,'run-state.json'),JSON.stringify(data));}
const report={};
try {
 state('run-20261027-120000-999',{mode:'Fachschicht',category:'ECHT',startedAt:'2026-10-27T12:00:00.100Z',totalCases:5,verifiedCases:5,failedCases:0,success:true,state:'PASS'});
 fs.mkdirSync(path.join(temp,'target/surefire-reports'),{recursive:true});
 fs.writeFileSync(path.join(temp,'target/surefire-reports/TEST-fail.xml'),'<testsuite tests="1" failures="1" errors="0" skipped="0"></testsuite>');
 fs.writeFileSync(path.join(temp,'target/test-evidence.json'),JSON.stringify({state:'RUNNING',startedAt:'2026-10-27T12:00:00Z'}));
 const c=load();c.f.zertifikatsSeite('27.10.2026','PROBE',temp);
 report.missingCoverageAndFailedJunit={runs:runs.letzteLaeufe(temp),junit:runs.junitStand(temp),certificate:c.captured.filter(x=>/Gesamturteil|Hiermit|Von 1/.test(x))};
 state('run-20261027-120000-111',{mode:'Fachschicht',category:'ECHT',startedAt:'2026-10-27T12:00:00.900Z',totalCases:5,verifiedCases:0,failedCases:5,success:false,state:'FAIL'});
 report.sameSecondOrder={actualNewer:'run-20261027-120000-111',selected:runs.letzteLaeufe(temp)[0].lauf.ordner};
 state('run-20261027-120001-123',{mode:'Fachschicht',category:'ECHT',startedAt:'2026-10-27T12:00:01Z',totalCases:5,verifiedCases:0,failedCases:5,success:false,state:'FAIL',verificationErrors:5});
 const c2=load();c2.f.ergebnisTabelle(temp);report.realFailureClassifiedAsAbort=c2.captured.filter(x=>/ABGEBROCHEN|FEHLGESCHLAGEN/.test(x));
 fs.writeFileSync(path.join(temp,'target/surefire-reports/TEST-fail.xml'),'<testsuite tests="10" failures="0" errors="0" skipped="0"></testsuite>');
 fs.writeFileSync(path.join(temp,'target/surefire-reports/TEST-stale.xml'),'<testsuite tests="30" failures="0" errors="0" skipped="0"></testsuite>');
 report.mixedSurefire=runs.junitStand(temp);
 fs.mkdirSync(path.join(temp,'doc'),{recursive:true});fs.copyFileSync(path.join(repo,'doc/SDN-Leistung-LMG-Testreport.pdf'),path.join(temp,'doc/SDN-Leistung-LMG-Testreport.pdf'));
 const c3=load();c3.f.pruefe(temp);report.verifyAfterNewFailedRun=c3.out;
 const empty=load(()=>({status:0,stdout:'title\fcontents\fchapter without expected heading\f',stderr:''}));
 report.missingTocAnchors=empty.f.seitenErmitteln('unused.pdf');
 const headings=['Einleitung','1 Testarten','2 Zwei','3 Herkunft','4 Echte','5 Unabh','6 Transaktionssteuerung','7 Ergebnis','8 Fazit','Testzertifikat'];
 const moved=load(()=>({status:0,stdout:'title\fcontents\fextra page\f'+headings.join('\f')+'\f',stderr:''}));
 report.shiftedHeadingPages=moved.f.seitenErmitteln('unused.pdf');
 const missing=load(()=>({status:null,error:{code:'ENOENT'},stdout:null,stderr:null}));
 try{missing.f.seitenErmitteln('unused.pdf')}catch(e){report.missingPdftotext=e.message;}
 console.log(JSON.stringify(report,null,2));
}finally{
 const cleanupTarget=path.resolve(temp),tempRoot=path.resolve(os.tmpdir())+path.sep;
 if(!cleanupTarget.startsWith(tempRoot))throw Error('Cleanupziel liegt nicht im Tempverzeichnis');
 fs.rmSync(cleanupTarget,{recursive:true,force:true});
}
