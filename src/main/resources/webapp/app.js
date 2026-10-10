/* SDN.sozial — Leistungsmanagement Grundsicherung
   Bedienlogik der Oberfläche: Fallübersicht mit Widgets, Vorgangsmasken
   (Bedarfsermittlung, Einkommensbereinigung, Zahlbarmachung), Buchungsjournal.
   Bewusst Vanilla-JavaScript, Kommunikation über die JSON-API. */

"use strict";

const $ = (id) => document.getElementById(id);
const euro = new Intl.NumberFormat("de-DE", { style: "currency", currency: "EUR" });

let eintraege = [];
let detail = null;
let suchbegriff = "";
let token = localStorage.getItem("lmgToken") || null;
let angemeldet = null;

// ---------------------------------------------------------------- Hilfsfunktionen

async function api(pfad, optionen) {
  const kopf = optionen?.headers ? { ...optionen.headers } : {};
  if (token) kopf["Authorization"] = "Bearer " + token;
  const antwort = await fetch(pfad, { ...optionen, headers: kopf });
  let koerper = {};
  try { koerper = await antwort.json(); } catch (e) { /* leerer Körper zulassen */ }
  if (antwort.status === 401) {
    token = null;
    localStorage.removeItem("lmgToken");
    angemeldet = null;
    aktualisiereSidebar();
    zeigeView("login");
    const fehler = new Error("Nicht angemeldet");
    fehler.code = "ANMELDUNG";
    throw fehler;
  }
  if (!antwort.ok) {
    const fehler = new Error(koerper.message || antwort.statusText);
    fehler.code = koerper.code;
    throw fehler;
  }
  return koerper;
}

function istAdmin() {
  return angemeldet !== null && angemeldet.rolle === "admin";
}

function aktualisiereSidebar() {
  const zeile = $("sidebar-benutzer");
  const abmelden = $("btn-abmelden");
  if (angemeldet) {
    const rollenName = angemeldet.rolle === "admin" ? "Admin" : "Sachbearbeiter";
    zeile.textContent = `Angemeldet: ${angemeldet.kennung} (${rollenName})`;
    abmelden.classList.remove("hidden");
  } else {
    zeile.textContent = "nicht angemeldet";
    abmelden.classList.add("hidden");
  }
  document.querySelector("[data-view='admin']").classList.toggle("hidden", !istAdmin());
}

function zeigeAnmeldung() {
  $("login-kennung").value = "";
  $("login-passwort").value = "";
  $("login-fehler").textContent = "";
  zeigeView("login");
}

function zeigeDialog(titel, text, istFehler) {
  const rahmen = $("dialog-rahmen");
  $("dialog-titel").textContent = titel;
  $("dialog-text").textContent = text;
  $("dialog-rahmen").querySelector(".dialog").classList.toggle("fehler", !!istFehler);
  rahmen.classList.remove("hidden");
}

function schliesseDialog() {
  $("dialog-rahmen").classList.add("hidden");
}

function betrag(wert) {
  return wert === null || wert === undefined ? "–" : euro.format(wert);
}

function zeitpunkt(iso) {
  if (!iso) return "–";
  return new Date(iso).toLocaleString("de-DE", { dateStyle: "medium", timeStyle: "short" });
}

function statusChip(klasse, text) {
  return `<span class="chip ${klasse}">${text}</span>`;
}

function zahlungsChip(status) {
  if (status === "ANGELEGT") return statusChip("angelegt", "angelegt");
  if (status === "BEREIT") return statusChip("bereit", "bereit");
  if (status === "UEBERGEBEN") return statusChip("uebergeben", "übergeben");
  if (status === "STORNIERT") return statusChip("storniert", "storniert");
  return status;
}

// ---------------------------------------------------------------- Navigation

function zeigeView(name) {
  for (const view of document.querySelectorAll(".view")) view.classList.add("hidden");
  $("view-" + name).classList.remove("hidden");
  for (const knopf of document.querySelectorAll(".nav-item")) {
    knopf.classList.toggle("active", knopf.dataset.view === name
      || (name === "vorgang" && knopf.dataset.view === "uebersicht"));
  }
}

// ---------------------------------------------------------------- Anmeldung

$("btn-login").addEventListener("click", async () => {
  const kennung = $("login-kennung").value.trim();
  const passwort = $("login-passwort").value;
  if (!kennung || !passwort) {
    $("login-fehler").textContent = "Bitte Kennung und Passwort eingeben.";
    return;
  }
  try {
    const ergebnis = await api("/api/anmeldung", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ kennung, passwort }),
    });
    token = ergebnis.token;
    localStorage.setItem("lmgToken", token);
    angemeldet = { kennung: ergebnis.kennung, rolle: ergebnis.rolle };
    aktualisiereSidebar();
    $("login-fehler").textContent = "";
    ladeUebersicht();
  } catch (fehler) {
    $("login-fehler").textContent = fehler.message;
  }
});

$("btn-abmelden").addEventListener("click", async () => {
  try { await api("/api/abmeldung", { method: "POST" }); } catch (e) { /* Sitzung ist eh weg */ }
  token = null;
  localStorage.removeItem("lmgToken");
  angemeldet = null;
  aktualisiereSidebar();
  zeigeAnmeldung();
});

document.querySelectorAll(".nav-item").forEach((knopf) =>
  knopf.addEventListener("click", () => {
    if (knopf.dataset.view === "uebersicht") { ladeUebersicht(); }
    else if (knopf.dataset.view === "journal") { ladeJournal(); }
    else if (knopf.dataset.view === "admin") { ladeSystemstatus(); }
    else if (knopf.dataset.view === "funktionen") { zeigeView("funktionen"); }
  }));

$("btn-zurueck").addEventListener("click", ladeUebersicht);
$("dialog-ok").addEventListener("click", schliesseDialog);
$("suchfeld").addEventListener("input", (e) => { suchbegriff = e.target.value.trim().toLowerCase(); rendereBgTabelle(); });

// ---------------------------------------------------------------- Neuanlage

$("btn-neu-vorgang").addEventListener("click", () => {
  $("karte-neu-vorgang").classList.toggle("hidden");
});

$("btn-vorgang-speichern").addEventListener("click", async () => {
  try {
    const ergebnis = await api("/api/bedarfsgemeinschaften", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        aktenzeichen: $("neu-aktenzeichen").value.trim(),
        hauptpersonName: $("neu-name").value.trim(),
        startBudget: Number($("neu-budget").value || 0),
        wiedervorlageAm: $("neu-wiedervorlage").value || null,
        iban: $("neu-iban").value.trim() || null,
        bic: $("neu-bic").value.trim() || null,
        kontoinhaber: $("neu-kontoinhaber").value.trim() || null,
      }),
    });
    $("karte-neu-vorgang").classList.add("hidden");
    await ladeUebersicht();
    await oeffneVorgang(ergebnis.bedarfsgemeinschaft.id);
  } catch (fehler) {
    zeigeDialog("Vorgang konnte nicht angelegt werden", fehler.message, true);
  }
});

$("btn-saga-antrag").addEventListener("click", () => {
  const karte = $("karte-saga-antrag");
  karte.classList.toggle("hidden");
  if (!karte.classList.contains("hidden")) {
    $("karte-neu-vorgang").classList.add("hidden");
    $("saga-aktenzeichen").value = "BG-SAGA-" + Math.floor(1000 + Math.random() * 9000);
    $("saga-monat").value = new Date().toISOString().slice(0, 7);
  }
});

$("btn-saga-starten").addEventListener("click", async () => {
  const knopf = $("btn-saga-starten");
  knopf.disabled = true;
  knopf.textContent = "Saga läuft …";
  try {
    const payload = {
      aktenzeichen: $("saga-aktenzeichen").value.trim(),
      hauptpersonName: $("saga-name").value.trim(),
      startBudget: Number($("saga-budget").value || 0),
      zahlungsmonat: $("saga-monat").value,
      iban: $("saga-iban").value.trim() || null,
      bic: $("saga-bic").value.trim() || null,
      regelbedarfsstufe: Number($("saga-rbs").value),
      kduMiete: Number($("saga-miete").value || 0),
      kduBetriebskosten: Number($("saga-betrieb").value || 0),
      kduHeizung: Number($("saga-heizung").value || 0),
      einkommenRente: Number($("saga-rente").value || 0),
      einkommenErwerb: Number($("saga-erwerb").value || 0)
    };
    const res = await api("/api/saga/antrag", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    });
    zeigeDialog("Antrags-Saga erfolgreich", `${res.hinweis} (Saga-ID: ${res.sagaId})`);
    $("karte-saga-antrag").classList.add("hidden");
    await ladeUebersicht();
  } catch (fehler) {
    zeigeDialog("Antrags-Saga fehlgeschlagen",
      `Fehler: ${fehler.message}. Falls Schritte bereits ausgeführt wurden, wurden sie durch die Saga-Engine automatisch rückwärts kompensiert.`, true);
  } finally {
    knopf.disabled = false;
    knopf.textContent = "⚡ Antrags-Saga starten";
  }
});

$("btn-neu-fall").addEventListener("click", () => {
  $("fall-neu").classList.toggle("hidden");
});

$("btn-fall-speichern").addEventListener("click", async () => {
  try {
    const ergebnis = await api("/api/bedarfsgemeinschaften/" + detail.bedarfsgemeinschaft.id + "/sozialfaelle", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        regelbedarfsstufe: Number($("neu-fall-rbs").value),
        merkzeichenG: $("neu-fall-g").checked,
        warmwasser: $("neu-fall-warmwasser").checked,
        kduMiete: Number($("neu-fall-miete").value || 0),
        kduBetriebskosten: Number($("neu-fall-betriebskosten").value || 0),
        kduHeizung: Number($("neu-fall-heizung").value || 0),
        einkommenRente: Number($("neu-fall-rente").value || 0),
        einkommenErwerb: Number($("neu-fall-erwerb").value || 0),
      }),
    });
    await oeffneVorgang(ergebnis.sozialfall.bgId);
    zeigeDialog("SozialFall angelegt",
      "SozialFall #" + ergebnis.sozialfall.id + " wurde angelegt und in die Berechnung aufgenommen.");
  } catch (fehler) {
    zeigeDialog("SozialFall konnte nicht angelegt werden", fehler.message, true);
  }
});

// ---------------------------------------------------------------- Fallübersicht

async function ladeUebersicht() {
  zeigeView("uebersicht");
  try {
    const [bgAntwort, zahlAntwort] = await Promise.all([
      api("/api/bedarfsgemeinschaften"),
      api("/api/zahlungen?limit=50"),
    ]);
    eintraege = bgAntwort.eintraege;
    rendereWidgets(zahlAntwort.zahlungen);
    rendereBgTabelle();
  } catch (fehler) {
    zeigeDialog("Fehler", "Fallübersicht konnte nicht geladen werden: " + fehler.message, true);
  }
}

function rendereWidgets(zahlungen) {
  const aktive = eintraege.filter((e) => e.statusAktiv);
  $("widget-aktive").textContent = aktive.length;

  const vorschau = aktive.reduce((summe, e) =>
    e.zahlbetragVorschau !== null ? summe + Number(e.zahlbetragVorschau) : summe, 0);
  $("widget-vorschau").textContent = euro.format(vorschau);

  const seit30Tagen = Date.now() - 30 * 24 * 3600 * 1000;
  $("widget-zahlungen").textContent = zahlungen.filter(
    (z) => z.status === "BEREIT" && new Date(z.erstelltAm).getTime() >= seit30Tagen).length;

  const heute = new Date().toISOString().slice(0, 10);
  $("widget-fristen").textContent = eintraege.filter(
    (e) => e.wiedervorlageAm && e.wiedervorlageAm >= heute).length;
}

function rendereBgTabelle() {
  const koerper = $("tabelle-bgs").querySelector("tbody");
  koerper.innerHTML = "";
  const gefiltert = eintraege.filter((e) =>
    !suchbegriff
    || e.aktenzeichen.toLowerCase().includes(suchbegriff)
    || e.hauptpersonName.toLowerCase().includes(suchbegriff));
  for (const eintrag of gefiltert) {
    const zeile = document.createElement("tr");
    zeile.className = "klickbar" + (eintrag.statusAktiv ? "" : " historisch");
    zeile.innerHTML = `
      <td>${eintrag.aktenzeichen}</td>
      <td>${eintrag.hauptpersonName}</td>
      <td>${eintrag.statusAktiv ? statusChip("aktiv", "aktiv") : statusChip("historisch", "historisiert")}</td>
      <td class="zahl">${betrag(eintrag.budgetRest)}</td>
      <td class="zahl">${eintrag.zahlbetragVorschau === null ? "nicht berechenbar" : betrag(eintrag.zahlbetragVorschau)}</td>
      <td>${eintrag.wiedervorlageAm ? new Date(eintrag.wiedervorlageAm).toLocaleDateString("de-DE") : "–"}</td>
      <td><button class="linkartig">Öffnen</button></td>`;
    zeile.addEventListener("click", () => oeffneVorgang(eintrag.id));
    koerper.appendChild(zeile);
  }
  if (gefiltert.length === 0) {
    koerper.innerHTML = `<tr><td colspan="7" class="hinweis">Keine Vorgänge gefunden.</td></tr>`;
  }
}

// ---------------------------------------------------------------- Vorgangsmaske

async function oeffneVorgang(bgId) {
  try {
    detail = await api("/api/bedarfsgemeinschaften/" + bgId);
    zeigeView("vorgang");
    rendereVorgang();
  } catch (fehler) {
    zeigeDialog("Fehler", "Vorgang konnte nicht geladen werden: " + fehler.message, true);
  }
}

function rendereVorgang() {
  const bg = detail.bedarfsgemeinschaft;
  $("vorgang-titel").textContent = `Vorgang ${bg.aktenzeichen} — ${bg.hauptpersonName}`;
  const statusChipElement = $("vorgang-status");
  statusChipElement.className = "chip " + (bg.statusAktiv ? "aktiv" : "historisch");
  statusChipElement.textContent = bg.statusAktiv ? "aktiv" : "historisiert";

  $("kopf-aktenzeichen").textContent = bg.aktenzeichen;
  $("kopf-name").textContent = bg.hauptpersonName;
  $("kopf-budget").textContent = betrag(bg.budgetRest);
  $("kopf-version").textContent = bg.version;
  $("kopf-status").value = bg.statusAktiv ? "1" : "0";
  $("kopf-wiedervorlage").value = bg.wiedervorlageAm || "";
  $("kopf-iban").value = bg.iban || "";
  $("kopf-bic").value = bg.bic || "";
  $("kopf-kontoinhaber").value = bg.kontoinhaber || "";

  $("auszahlung-empfaenger").value = bg.kontoinhaber || bg.hauptpersonName || "";
  $("auszahlung-iban").value = bg.iban || "";
  $("auszahlung-bic").value = bg.bic || "";

  rendereBerechnung();
  rendereFallMasken();
  rendereBescheid(detail.bescheid);
  rendereZahlungen(detail.zahlungen);
  rendereJournalListe("liste-journal-vorgang", detail.journal);
}

function rendereBescheid(bescheid) {
  const box = $("bescheid-status-text");
  const druckBtn = $("btn-bescheid-drucken");
  if (bescheid && bescheid.id) {
    box.innerHTML = `<strong>Bescheid erteilt:</strong> ${bescheid.bescheidNr} · Datum: ${bescheid.bescheidDatum || '–'} · Zeitraum: <strong>${bescheid.bewilligungVon} bis ${bescheid.bewilligungBis}</strong> · Monatlicher Zahlbetrag: <strong>${betrag(bescheid.zahlbetrag)}</strong> (${bescheid.status}) · Bearbeiter: ${bescheid.erstelltVon}`;
    druckBtn.classList.remove("hidden");
    $("bescheid-von").value = bescheid.bewilligungVon || "";
    $("bescheid-bis").value = bescheid.bewilligungBis || "";
  } else {
    box.textContent = "Kein Bewilligungsbescheid vorhanden. Geben Sie den Bewilligungszeitraum an und klicken Sie auf 'Bescheid erteilen'.";
    druckBtn.classList.add("hidden");
    const aktMonat = new Date().toISOString().slice(0, 7);
    const d = new Date();
    d.setFullYear(d.getFullYear() + 1);
    const folgeJahr = d.toISOString().slice(0, 7);
    $("bescheid-von").value = aktMonat;
    $("bescheid-bis").value = folgeJahr;
  }
}

function rendereBerechnung() {
  const koerper = $("tabelle-bedarf").querySelector("tbody");
  koerper.innerHTML = "";
  const hinweis = $("berechnung-hinweis");
  $("gesamtbedarf").textContent = betrag(detail.gesamtbedarf);
  $("anrechenbar").textContent = betrag(detail.anrechenbaresEinkommen);
  $("zahlbetrag").textContent = betrag(detail.zahlbetrag);

  // Die API liefert die Berechnung flach: Gesamtwerte auf Vorgangs- und
  // Fallwerte je SozialFall; ein fehlender Regelbedarf zeigt eine nicht
  // konfigurierte Regelbedarfsstufe an.
  const berechnet = detail.faelle && detail.faelle.length > 0
    && detail.faelle.every((fall) => typeof fall.regelbedarf === "number");
  if (!berechnet) {
    if (!detail.faelle || detail.faelle.length === 0) {
      hinweis.textContent = "Dieser Vorgang enthält noch keinen SozialFall. Bitte legen Sie unten mindestens einen SozialFall an.";
      hinweis.style.color = "var(--farbe-text-gedaempft)";
    } else {
      hinweis.textContent = "Berechnung nicht möglich: eine Regelbedarfsstufe dieses Vorgangs ist nicht konfiguriert (gültig sind Stufen 1 bis 6).";
      hinweis.style.color = "var(--farbe-gefahren)";
    }
    return;
  }
  hinweis.textContent = "";
  for (const fall of detail.faelle) {
    const zeile = document.createElement("tr");
    const mbTitel = `Merkzeichen G: ${betrag(fall.mehrbedarfG)} | Dezentrales Warmwasser: ${betrag(fall.mehrbedarfWarmwasser)}`;
    const kduTitel = `Kaltmiete: ${betrag(fall.kduMiete)} | Kalte Betriebskosten: ${betrag(fall.kduBetriebskosten)} | Heizung: ${betrag(fall.kduHeizung)}`;
    zeile.innerHTML = `
      <td>SozialFall #${fall.id}</td>
      <td class="zahl">${betrag(fall.regelbedarf)}</td>
      <td class="zahl" title="${mbTitel}">${betrag(fall.mehrbedarf)}</td>
      <td class="zahl" title="${kduTitel}">${betrag(fall.kdu)}</td>
      <td class="zahl">${betrag(fall.gesamtbedarfFall)}</td>
      <td class="zahl">${betrag(fall.freibetragErwerb)}</td>
      <td class="zahl">${betrag(fall.anrechenbaresEinkommenFall)}</td>
      <td class="zahl">${betrag(fall.zahlbetragFall)}</td>`;
    koerper.appendChild(zeile);
  }
}

function rendereFallMasken() {
  const bereich = $("fall-masken");
  bereich.innerHTML = "";
  for (const fall of detail.faelle) {
    const maske = document.createElement("div");
    maske.className = "fall-maske";
    maske.id = "fall-" + fall.id;
    maske.innerHTML = `
      <h3>SozialFall #${fall.id} <span class="fall-version">Version ${fall.version}</span></h3>
      <div class="maske-gitter">
        <label>Regelbedarfsstufe
          <select data-feld="regelbedarfsstufe">
            ${[1, 2, 3, 4, 5, 6].map((s) =>
              `<option value="${s}" ${fall.regelbedarfsstufe === s ? "selected" : ""}>Stufe ${s}</option>`).join("")}
          </select>
        </label>
        <label>Kaltmiete (EUR)
          <input data-feld="kduMiete" type="number" step="0.01" min="0" value="${fall.kduMiete}">
        </label>
        <label>Kalte Betriebskosten (EUR)
          <input data-feld="kduBetriebskosten" type="number" step="0.01" min="0" value="${fall.kduBetriebskosten}">
        </label>
        <label>Heizkosten (EUR)
          <input data-feld="kduHeizung" type="number" step="0.01" min="0" value="${fall.kduHeizung}">
        </label>
        <label>Renteneinkünfte (EUR, voll anrechenbar)
          <input data-feld="einkommenRente" type="number" step="0.01" min="0" value="${fall.einkommenRente}">
        </label>
        <label>Brutto-Erwerbseinkommen (EUR)
          <input data-feld="einkommenErwerb" type="number" step="0.01" min="0" value="${fall.einkommenErwerb}">
        </label>
      </div>
      <label class="checkbox-zeile">
        <input data-feld="merkzeichenG" type="checkbox" ${fall.merkzeichenG ? "checked" : ""}>
        <span>Merkzeichen G — Mehrbedarf 17&nbsp;% (§ 30 Abs. 1 SGB XII)</span>
      </label>
      <label class="checkbox-zeile">
        <input data-feld="warmwasser" type="checkbox" ${fall.warmwasser ? "checked" : ""}>
        <span>Dezentrale Warmwassererzeugung — Mehrbedarf 2,3&nbsp;% (§ 30 Abs. 7 SGB XII)</span>
      </label>
      <div style="margin-top:12px">
        <button class="primaer">Speichern</button>
      </div>`;

    maske.querySelector("button").addEventListener("click", () => speichereFall(fall, maske));
    bereich.appendChild(maske);
  }
}

async function speichereFall(fall, maske) {
  const wert = (feld) => maske.querySelector(`[data-feld="${feld}"]`);
  const koerper = {
    bgId: fall.bgId,
    regelbedarfsstufe: Number(wert("regelbedarfsstufe").value),
    merkzeichenG: wert("merkzeichenG").checked,
    warmwasser: wert("warmwasser").checked,
    kduMiete: Number(wert("kduMiete").value || 0),
    kduBetriebskosten: Number(wert("kduBetriebskosten").value || 0),
    kduHeizung: Number(wert("kduHeizung").value || 0),
    einkommenRente: Number(wert("einkommenRente").value || 0),
    einkommenErwerb: Number(wert("einkommenErwerb").value || 0),
    version: fall.version,
  };
  const knopf = maske.querySelector("button");
  knopf.disabled = true;
  try {
    await api("/api/sozialfaelle/" + fall.id, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(koerper),
    });
    await oeffneVorgang(fall.bgId);
  } catch (fehler) {
    if (fehler.code === "KONKURRENZ") {
      zeigeDialog("Änderung durch anderen Bearbeiter",
        "Der SozialFall wurde zwischenzeitlich von einem anderen Sachbearbeiter geändert, "
        + "damit Ihr Stand nicht überschrieben wurde (optimistisches Locking). "
        + "Die Ansicht wird neu geladen — bitte Änderungen erneut prüfen.", false);
      await oeffneVorgang(fall.bgId);
    } else {
      zeigeDialog("Speichern nicht möglich", fehler.message, true);
    }
  } finally {
    knopf.disabled = false;
  }
}

$("btn-kopf").addEventListener("click", async () => {
  const bg = detail.bedarfsgemeinschaft;
  try {
    await api("/api/bedarfsgemeinschaften/" + bg.id, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        statusAktiv: $("kopf-status").value === "1",
        wiedervorlageAm: $("kopf-wiedervorlage").value || null,
        iban: $("kopf-iban").value.trim() || null,
        bic: $("kopf-bic").value.trim() || null,
        kontoinhaber: $("kopf-kontoinhaber").value.trim() || null,
        version: bg.version,
      }),
    });
    await oeffneVorgang(bg.id);
  } catch (fehler) {
    if (fehler.code === "KONKURRENZ") {
      zeigeDialog("Änderung durch anderen Bearbeiter",
        "Der Vorgang wurde zwischenzeitlich geändert, Ihr Stand wurde nicht überschrieben. "
        + "Die Ansicht wird neu geladen.", false);
      await oeffneVorgang(bg.id);
    } else {
      zeigeDialog("Speichern nicht möglich", fehler.message, true);
    }
  }
});

// ---------------------------------------------------------------- Zahlbarmachung

$("btn-auszahlung").addEventListener("click", async () => {
  const knopf = $("btn-auszahlung");
  const bg = detail.bedarfsgemeinschaft;
  const monat = $("zahlungsmonat").value;
  if (!monat) {
    zeigeDialog("Angabe fehlt", "Bitte den Zahlungsmonat wählen.", true);
    return;
  }
  // Button sofort deaktivieren: verhindert Doppelbuchungen durch Doppelklick
  knopf.disabled = true;
  knopf.textContent = "Anordnung läuft …";
  try {
    const ergebnis = await api("/api/auszahlung", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        bgId: bg.id,
        vorgangsNr: crypto.randomUUID(),
        zahlungsmonat: monat,
        vierAugenErforderlich: $("auszahlung-vieraugen").checked,
        empfaengerIban: $("auszahlung-iban").value.trim() || null,
        empfaengerBic: $("auszahlung-bic").value.trim() || null,
        empfaengerName: $("auszahlung-empfaenger").value.trim() || null,
      }),
    });
    if (ergebnis.bereitsGebucht) {
      zeigeDialog("Bereits gebucht",
        "Diese Vorgangsnummer war bereits vorhanden — es wurde nichts erneut gebucht (Idempotenz). "
        + "Buchung vom " + zeitpunkt(ergebnis.zahlung.erstelltAm) + ".");
    } else {
      const statusText = ergebnis.zahlung.status === "ANGELEGT"
        ? "wurde erfasst (Status „angelegt“, wartet auf Vier-Augen-Freigabe)"
        : "wurde mit Status „bereit“ gebucht";
      zeigeDialog("Auszahlung angeordnet",
        "Zahlung über " + betrag(ergebnis.zahlung.auszahlungsbetrag) + " für "
        + ergebnis.zahlung.zahlungsmonat + " " + statusText + ". "
        + "Neues Restbudget: " + betrag(ergebnis.bedarfsgemeinschaft.budgetRest) + ".");
    }
    await oeffneVorgang(bg.id);
  } catch (fehler) {
    zeigeDialog("Auszahlung nicht möglich", fehler.message, true);
  } finally {
    knopf.disabled = false;
    knopf.textContent = "Auszahlung anordnen";
  }
});

// ---------------------------------------------------------------- Bewilligungsbescheid

$("btn-bescheid-erteilen").addEventListener("click", async () => {
  const von = $("bescheid-von").value;
  const bis = $("bescheid-bis").value;
  if (!von || !bis) {
    zeigeDialog("Angabe fehlt", "Bitte Bewilligungszeitraum (von und bis) angeben.", true);
    return;
  }
  const knopf = $("btn-bescheid-erteilen");
  knopf.disabled = true;
  knopf.textContent = "Wird erteilt …";
  try {
    const res = await api(`/api/bedarfsgemeinschaften/${detail.bedarfsgemeinschaft.id}/bescheid`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ bewilligungVon: von, bewilligungBis: bis })
    });
    zeigeDialog("Bescheid erteilt", res.hinweis || "Bewilligungsbescheid erfolgreich erteilt.");
    await oeffneVorgang(detail.bedarfsgemeinschaft.id);
  } catch (fehler) {
    zeigeDialog("Bescheiderteilung fehlgeschlagen", fehler.message, true);
  } finally {
    knopf.disabled = false;
    knopf.textContent = "Bescheid erteilen";
  }
});

$("btn-bescheid-drucken").addEventListener("click", () => {
  if (!detail || !detail.bedarfsgemeinschaft) return;
  const url = `/api/bedarfsgemeinschaften/${detail.bedarfsgemeinschaft.id}/bescheid/druck?token=${encodeURIComponent(token || "")}`;
  window.open(url, "_blank");
});

function rendereZahlungen(zahlungen) {
  const koerper = $("tabelle-zahlungen").querySelector("tbody");
  koerper.innerHTML = "";
  for (const z of zahlungen) {
    const zeile = document.createElement("tr");
    const freigabeMoeglich = z.status === "ANGELEGT";
    const stornoMoeglich = (istAdmin() && z.status === "BEREIT") || z.status === "ANGELEGT";
    const empfText = (z.empfaengerName || "–") + (z.empfaengerIban ? `<br><small style="color:var(--farbe-text-leise)">${z.empfaengerIban}</small>` : "");
    zeile.innerHTML = `
      <td>${z.zahlungsmonat}</td>
      <td class="zahl">${betrag(z.auszahlungsbetrag)}</td>
      <td>${zahlungsChip(z.status)}</td>
      <td>${empfText}</td>
      <td>${z.sachbearbeiterId || "–"}</td>
      <td>${z.freigegebenVon || "–"}</td>
      <td class="hinweis">${z.vorgangsNr}</td>
      <td>${zeitpunkt(z.erstelltAm)}</td>
      <td>
        ${freigabeMoeglich ? `<button class="primaer freigeben" data-id="${z.id}" style="padding:4px 8px;font-size:12px;margin-right:4px;">Freigeben</button>` : ""}
        ${stornoMoeglich ? `<button class="sekundaer storno" data-id="${z.id}" style="padding:4px 8px;font-size:12px;">Storno</button>` : ""}
      </td>`;
    if (freigabeMoeglich) {
      zeile.querySelector("button.freigeben").addEventListener("click", () => freigeben(z));
    }
    if (stornoMoeglich) {
      zeile.querySelector("button.storno").addEventListener("click", () => storniere(z));
    }
    koerper.appendChild(zeile);
  }
  if (zahlungen.length === 0) {
    koerper.innerHTML = `<tr><td colspan="9" class="hinweis">Noch keine Auszahlungen angeordnet.</td></tr>`;
  }
}

async function freigeben(zahlung) {
  if (!confirm(`Zahlung über ${betrag(zahlung.auszahlungsbetrag)} (${zahlung.zahlungsmonat}) ` +
      "freigeben (rechnerisch feststellen)? Status wechselt zu BEREIT.")) {
    return;
  }
  try {
    const ergebnis = await api("/api/zahlungen/" + zahlung.id + "/freigabe", { method: "POST" });
    zeigeDialog("Zahlung freigegeben",
      "Die Zahlung über " + betrag(ergebnis.zahlung.auszahlungsbetrag) + " wurde freigegeben und ist bereit für den Kassenlauf.");
    await oeffneVorgang(zahlung.bgId);
  } catch (fehler) {
    zeigeDialog("Freigabe nicht möglich", fehler.message, true);
  }
}

async function storniere(zahlung) {
  if (!confirm(`Auszahlung über ${betrag(zahlung.auszahlungsbetrag)} (${zahlung.zahlungsmonat}) ` +
      "stornieren? Das Restbudget wird zurückgebucht.")) {
    return;
  }
  try {
    const ergebnis = await api("/api/zahlungen/" + zahlung.id + "/storno", { method: "POST" });
    zeigeDialog("Storno gebucht",
      ergebnis.hinweis + " Neue Zahlungssumme im Vorgang: "
      + betrag(ergebnis.zahlung.auszahlungsbetrag) + " (Status " + ergebnis.zahlung.status + ").");
    await oeffneVorgang(zahlung.bgId);
  } catch (fehler) {
    zeigeDialog("Storno nicht möglich", fehler.message, true);
  }
}

$("btn-kassenlauf").addEventListener("click", async () => {
  if (!confirm("Kassenlauf ausführen? Alle Zahlungen mit Status BEREIT werden auf ÜBERGEBEN gesetzt.")) {
    return;
  }
  const knopf = $("btn-kassenlauf");
  knopf.disabled = true;
  try {
    const ergebnis = await api("/api/kassenlauf", { method: "POST" });
    zeigeDialog("Kassenlauf",
      ergebnis.hinweis + (ergebnis.anzahl > 0
        ? " Summe: " + betrag(ergebnis.summe) + "."
        : ""));
    ladeSystemstatus();
  } catch (fehler) {
    zeigeDialog("Kassenlauf fehlgeschlagen", fehler.message, true);
  } finally {
    knopf.disabled = false;
  }
});

// ---------------------------------------------------------------- Journal

async function ladeJournal() {
  zeigeView("journal");
  try {
    const antwort = await api("/api/journal?limit=50");
    const koerper = $("tabelle-journal").querySelector("tbody");
    koerper.innerHTML = "";
    for (const eintrag of antwort.eintraege) {
      const zeile = document.createElement("tr");
      zeile.innerHTML = `
        <td>${zeitpunkt(eintrag.erstelltAm)}</td>
        <td>${eintrag.bgId === null ? "–" : "#" + eintrag.bgId}</td>
        <td>${eintrag.aktionText}</td>
        <td>${eintrag.sachbearbeiterId}</td>`;
      koerper.appendChild(zeile);
    }
    if (antwort.eintraege.length === 0) {
      koerper.innerHTML = `<tr><td colspan="4" class="hinweis">Keine Journaleinträge vorhanden.</td></tr>`;
    }
  } catch (fehler) {
    zeigeDialog("Fehler", "Journal konnte nicht geladen werden: " + fehler.message, true);
  }
}

function rendereJournalListe(id, eintraege) {
  const liste = $(id);
  liste.innerHTML = "";
  for (const eintrag of eintraege) {
    const punkt = document.createElement("li");
    punkt.innerHTML = `${eintrag.aktionText}
      <div class="journal-zeit">${zeitpunkt(eintrag.erstelltAm)} · ${eintrag.sachbearbeiterId}</div>`;
    liste.appendChild(punkt);
  }
  if (eintraege.length === 0) {
    liste.innerHTML = `<li class="hinweis">Keine Einträge.</li>`;
  }
}

// ---------------------------------------------------------------- Adminbereich

async function ladeSystemstatus() {
  zeigeView("admin");
  const koerper = $("tabelle-status").querySelector("tbody");
  koerper.innerHTML = `<tr><td colspan="3" class="hinweis">Prüfung läuft …</td></tr>`;
  $("admin-laufzeit").innerHTML = `<div class="hinweis">Prüfung läuft …</div>`;
  try {
    const status = await api("/api/system/status");
    rendereSystemstatus(status);
  } catch (fehler) {
    koerper.innerHTML = `<tr><td colspan="3">Statusprüfung fehlgeschlagen: ${fehler.message}</td></tr>`;
    $("admin-laufzeit").innerHTML = `<div class="hinweis">—</div>`;
  }
}

function statusZeile(name, ok, details) {
  return `<tr>
    <td>${name}</td>
    <td>${ok ? statusChip("aktiv", "OK") : statusChip("storniert", "Störung")}</td>
    <td class="hinweis">${details}</td>
  </tr>`;
}

function rendereSystemstatus(s) {
  const allesOk = s.dbErreichbar;
  const dbDetails = allesOk
    ? `${s.dbProdukt || "Datenbank"} · Antwortzeit ${s.dbAntwortMillis} ms · DB-Zeit ${s.dbZeitpunkt}`
    : (s.dbFehler || "nicht erreichbar");
  const schemaOk = s.dbErreichbar && s.anzahlVorgaenge >= 0;
  const schemaDetails = schemaOk
    ? `Vorgänge: ${s.anzahlVorgaenge} · SozialFälle: ${s.anzahlSozialFaelle} · Zahlungen: ${s.anzahlZahlungen}`
      + (s.anzahlVorgaenge === 0 ? " — leer, bitte ddl/lmg_seed.sql einspielen" : "")
    : "ohne Datenbank keine Prüfung";
  const sagasOk = s.dbErreichbar && s.anzahlOffeneSagas === 0;
  const sagasDetails = !s.dbErreichbar
    ? "ohne Datenbank keine Prüfung"
    : `${s.anzahlOffeneSagas} offen, ${s.anzahlAbgebrocheneSagas} abgebrochen`
      + (s.anzahlOffeneSagas > 0 ? " — Wiederanlauf ausführen empfohlen" : " · kein Wiederanlauf nötig");

  $("tabelle-status").querySelector("tbody").innerHTML = [
    statusZeile("Datenbank (Oracle)", s.dbErreichbar, dbDetails),
    statusZeile("Fachschema (Tabellen/Seed)", schemaOk, schemaDetails),
    statusZeile("Transaktionssteuerung (TxManager/Retry)", true, "aktiv — Commit/Rollback, Wiederholung bei ORA-54/60/8177"),
    statusZeile("Task-Steuerung (Wachhund, Parallelität)", true,
      `aktiv · ${s.tasksAktiv} laufend · ${s.tasksErfolgreich} erfolgreich · ${s.tasksFehlgeschlagen} fehlgeschlagen · ` +
      `${s.tasksAbgebrochen} abgebrochen · ${s.tasksAbgelehnt} abgelehnt · Ø ${Math.round(s.tasksDurchschnittMillis)} ms`),
    statusZeile("Wiederanlauf (Saga-Journal)", sagasOk, sagasDetails),
  ].join("");

  $("admin-laufzeit").innerHTML = `
    <div>Java: <strong>${s.javaVersion}</strong></div>
    <div>Server gestartet: <strong>${zeitpunkt(s.serverStart)}</strong></div>
    <div>Laufzeit: <strong>${Math.floor(s.serverLaufzeitSekunden / 60)} min ${s.serverLaufzeitSekunden % 60} s</strong></div>
    <div class="hinweis" style="margin-top:6px">${allesOk
      ? "Datenbank und Komponenten: alles gestartet."
      : "Störung erkannt — Details in der Prüftabelle."}</div>`;
}

$("btn-status").addEventListener("click", ladeSystemstatus);

$("btn-wiederanlauf").addEventListener("click", async () => {
  const knopf = $("btn-wiederanlauf");
  knopf.disabled = true;
  try {
    const ergebnis = await api("/api/system/wiederanlauf", { method: "POST" });
    zeigeDialog("Wiederanlauf",
      ergebnis.hinweis + (ergebnis.kompensiert > 0
        ? " Die Kompensationen wurden idempotent ausgeführt."
        : ""));
    ladeSystemstatus();
  } catch (fehler) {
    zeigeDialog("Wiederanlauf fehlgeschlagen", fehler.message, true);
  } finally {
    knopf.disabled = false;
  }
});

// ---------------------------------------------------------------- Start

$("zahlungsmonat").value = new Date().toISOString().slice(0, 7);
aktualisiereSidebar();

(async function start() {
  if (!token) {
    zeigeAnmeldung();
    return;
  }
  try {
    angemeldet = await api("/api/werbin");
    aktualisiereSidebar();
    ladeUebersicht();
  } catch (fehler) {
    // 401 behandelt api() bereits (Login-Ansicht); alles andere zeigt einen Hinweis
    if (fehler.code !== "ANMELDUNG") {
      zeigeAnmeldung();
    }
  }
})();
