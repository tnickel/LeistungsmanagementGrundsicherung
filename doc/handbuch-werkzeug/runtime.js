"use strict";
const path = require("path");
// Der Dokumentenlauf kann die vom Codex-Host bereitgestellten Pakete explizit wählen.
// Ohne diese Variable bleibt der Generator nach npm ci lokal ausführbar.
exports.dependency = name => process.env.CODEX_DOCUMENT_NODE_MODULES
  ? require(path.join(process.env.CODEX_DOCUMENT_NODE_MODULES, name))
  : require(name);
