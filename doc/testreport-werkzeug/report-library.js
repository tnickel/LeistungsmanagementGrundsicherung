// Für die Artefakterstellung kann der gebündelte Workspace-Modulpfad vorgegeben werden.
// Der normale Projektbetrieb verwendet weiterhin seine installierte docx-Abhängigkeit.
const path = require("path");
function load(name) {
  const directory = process.env.LMG_REPORT_NODE_MODULES;
  return directory ? require(path.join(directory,name)) : require("../handbuch-werkzeug/node_modules/"+name);
}
module.exports = {docx:load("docx")};
