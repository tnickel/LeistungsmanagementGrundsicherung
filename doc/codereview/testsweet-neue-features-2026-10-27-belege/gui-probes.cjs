"use strict";
// Review-Gegenproben am unveränderten app.js, ohne Browser oder Netzaufruf.
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const assert = require('node:assert/strict');
const repo = path.resolve(__dirname, '../../..');
const source = fs.readFileSync(path.join(repo,'testsweet/src/main/resources/sweet/app.js'),'utf8');
const elements = new Map();
function element(id) {
  if (!elements.has(id)) elements.set(id, {disabled:false,hidden:false,style:{},textContent:'',
    addEventListener(name,cb){this[name]=cb;}});
  return elements.get(id);
}
let resolveFetch;
const context = vm.createContext({window:{addEventListener(){}},document:{getElementById:element},
  console,alert(){},setInterval(){},fetch:()=>new Promise(resolve=>{resolveFetch=resolve;})});
vm.runInContext(source,context,{filename:'sweet/app.js'});
vm.runInContext('zertifikatSuiteStatus={state:"RUNNING",running:true};zertifikatSuiteStatusBekannt=true;',context);
element('testreportlink').style.display='inline-block';
vm.runInContext('sperreZertifikatSuiteBedienung()',context);
assert.equal(element('testreportknopf').disabled,false);
assert.equal(element('testreportlink').style.display,'inline-block');
const lockResult={suiteRunning:true,reportButtonDisabled:element('testreportknopf').disabled,
  reportLinkDisplay:element('testreportlink').style.display};
// Derselbe produktive Registrierungsblock; nur die übrige Seiteninitialisierung entfällt.
const begin=source.indexOf('    const reportKnopf = $("testreportknopf");');
const end=source.indexOf('    initialisiereLlm();',begin);
assert.ok(begin>0&&end>begin);
vm.runInContext(source.slice(begin,end),context);
vm.runInContext('zertifikatSuiteStatus={state:"IDLE",running:false};',context);
(async()=>{
  const pending=element('testreportknopf').click();
  assert.equal(element('testreportknopf').disabled,true);
  vm.runInContext('zertifikatSuiteStatus={state:"RUNNING",running:true};sperreZertifikatSuiteBedienung();',context);
  resolveFetch({json:async()=>({ok:true,pdf:'controlled-fixture.pdf',laeufe:1,davonErfolgreich:1})});
  await pending;
  assert.equal(element('testreportknopf').disabled,false);
  assert.equal(element('testreportlink').style.display,'inline-block');
  const inputs=[
    'https://example.invalid/"onmouseover="alert(1)',
    "https://example.invalid/'><img src=x onerror=alert(1)>",
    'https://example.invalid/&quot; onclick=alert(1)',
    'https://example.invalid/) <script>alert(1)</script>',
    'https://example.invalid/a\n<img src=x onerror=alert(1)>',
    'https://example.invalid/?x=1&y=2'
  ];
  const html=inputs.map(input=>{context.fixture=input;return vm.runInContext(
    `esc(fixture).replaceAll("\\n","<br>").replace(/(https?:\\/\\/[^\\s<]+)/g,'<a href="$1" target="_blank" rel="noopener">$1</a>')`,context);});
  console.log(JSON.stringify({lockResult,completionDuringSuite:{reportButtonDisabled:element('testreportknopf').disabled,
    reportLinkDisplay:element('testreportlink').style.display},linkification:inputs.map((input,i)=>({input,html:html[i]}))},null,2));
})().catch(error=>{console.error(error);process.exitCode=1;});
