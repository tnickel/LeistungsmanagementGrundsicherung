const fs=require('fs'),path=require('path');
const base=__dirname,fixture=JSON.parse(fs.readFileSync(path.join(base,'validator-fixture.json'),'utf8'));
const {answer}=require('../'+'../testzertifikat-werkzeug/llm-evidence.js');
const results=fixture.variants.map(v=>{try{answer(v.content,fixture.ids,fixture.requirements);return {name:v.name,accepted:true};}catch(error){return {name:v.name,accepted:false,error:error.message};}});
fs.writeFileSync(path.join(base,'validator-node-results.json'),JSON.stringify(results,null,2));
console.log(JSON.stringify(results));
