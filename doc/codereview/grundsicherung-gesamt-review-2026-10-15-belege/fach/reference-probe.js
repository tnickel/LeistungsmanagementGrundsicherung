const fs=require('node:fs');
const path=require('node:path');
const ref=require(path.resolve('doc/testzertifikat-werkzeug/llm-reference.js'));
const proben=process.argv.slice(2).map(file=>{
  const r=ref.requirement('SYNTHETISCHE_REVIEWPROBE',fs.readFileSync(file,'utf8'));
  return {datei:path.basename(file),arithmetischeBeanstandungen:r.sourceFindings,
    fehlendeEingaben:r.missingInputs,berechnung:r.calculations.map(c=>c.household)};
});
process.stdout.write(JSON.stringify({referenz:'JavaScript llm-reference',synthetisch:true,proben}));
