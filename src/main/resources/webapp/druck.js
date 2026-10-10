"use strict";
// Druck-Schaltfläche des Bescheids; als eigene Datei, damit die CSP ohne 'unsafe-inline' für Skripte auskommt.
document.getElementById("druck-button").addEventListener("click", () => window.print());
