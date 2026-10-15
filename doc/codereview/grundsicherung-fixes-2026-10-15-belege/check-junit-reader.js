const fs = require('fs'), path = require('path');
const repo = path.resolve(__dirname, '../../..');
const {junit} = require(path.join(repo, 'doc/testzertifikat-werkzeug/certificate-evidence.js'));
const checks = ['main','testsweet'].map(module => {
  const result = junit(repo, module);
  return {module, state:result.state, reasons:result.reasons};
});
fs.writeFileSync(path.join(__dirname, 'junit-reader-early-check.json'), JSON.stringify(checks, null, 2));
console.log(JSON.stringify(checks));
if (checks.some(check => check.state !== 'PASS')) process.exitCode = 1;
