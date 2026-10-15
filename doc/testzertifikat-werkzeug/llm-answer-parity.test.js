const test=require("node:test"),assert=require("node:assert/strict"),fs=require("fs"),path=require("path");
const llm=require("./llm-evidence");
const corpus=JSON.parse(fs.readFileSync(path.join(__dirname,"llm-answer-parity-corpus.json"),"utf8"));
for (const row of corpus.fixtures) test("Antwortparität: "+row.name,()=>{
  const requirements=row.requirements || corpus.requirements;
  if (!row.accepted) assert.throws(()=>llm.answer(row.content,corpus.expectedCaseIds,requirements));
  else {
    const cases=llm.answer(row.content,corpus.expectedCaseIds,requirements),reference=llm.referenceChecks(cases,requirements);
    const state=cases.some(c=>c.verdict==="FAIL") || reference.some(r=>r.state==="FAIL")?"FAIL"
      :cases.some(c=>c.verdict==="UNVERIFIED") || reference.some(r=>r.state==="UNVERIFIED")?"UNVERIFIED":"PASS";
    assert.equal(state,row.state);
  }
});
