const fs=require('fs'),path=require('path');
const repo=path.resolve(__dirname,'../../..');
const evidence=require(path.join(repo,'doc/testzertifikat-werkzeug/certificate-evidence.js')).collect(repo);
fs.writeFileSync(path.join(__dirname,'certificate-evidence-current.json'),JSON.stringify(evidence,null,2));
fs.writeFileSync(path.join(__dirname,'certificate-layer-summary.json'),JSON.stringify({verdict:evidence.verdict,layers:evidence.layers.map(({id,state,reasons})=>({id,state,reasons}))},null,2));
console.log(JSON.stringify({verdict:evidence.verdict,layers:evidence.layers.map(({id,state})=>({id,state}))}));
