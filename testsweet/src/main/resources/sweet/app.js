// testsweet — Oberflächenlogik. Alle Serverdaten werden über esc() abgesichert
// (dieselbe Regel wie im LMG-Frontend), bevor sie ins DOM interpoliert werden.
"use strict";

function esc(wert) {
    if (wert === null || wert === undefined) return "";
    return String(wert)
        .replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;").replaceAll("'", "&#39;");
}

function $(id) { return document.getElementById(id); }

const STATUS_TEXT = {
    OFFEN: "offen", IN_BEARBEITUNG: "in Bearbeitung", FERTIG_GESPIELT: "fertig gespielt",
    FEHLGESCHLAGEN: "fehlgeschlagen", VERIFIZIERT: "verifiziert",
    VERIFIKATION_FEHLGESCHLAGEN: "Verifikation n.i.O."
};

let momentanerStatus = null;
let timer = null;

// ------------------------------------------------------------------
// Start/Stopp
// ------------------------------------------------------------------

$("start").addEventListener("click", async () => {
    const body = {
        modus: "FACHSCHICHT",
        mitarbeiter: Number($("mitarbeiter").value),
        auftraege: Number($("auftraege").value),
        seed: Number($("seed").value),
        denkzeitMs: Number($("denkzeit").value),
        aufraeumen: $("aufraeumen").checked
    };
    const antwort = await fetch("/api/start", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body)
    });
    const ergebnis = await antwort.json();
    if (ergebnis.fehler) {
        alert("Start nicht möglich: " + ergebnis.fehler);
    }
    pollSofort();
});

$("stop").addEventListener("click", async () => {
    await fetch("/api/stop", { method: "POST" });
    pollSofort();
});

$("modus1").addEventListener("click", () => {
    // Modus 1 ist der aktive Modus; Klick bestätigt nur die Auswahl
    document.getElementById("modus1").classList.add("ausgewaehlt");
});

$("schliessen").addEventListener("click", () => $("overlay").classList.remove("offen"));
$("overlay").addEventListener("click", (e) => {
    if (e.target === $("overlay")) $("overlay").classList.remove("offen");
});

// ------------------------------------------------------------------
// Status-Polling
// ------------------------------------------------------------------

function pollSofort() { aktualisiere(); }

async function aktualisiere() {
    let status;
    try {
        status = await (await fetch("/api/status")).json();
    } catch (e) {
        return; // Server kurzzeitig nicht erreichbar — nächster Ticker versuchts wieder
    }
    momentanerStatus = status;

    $("phase").textContent = status.phase;
    $("db").textContent = status.db.url;

    const laeuft = ["AUFRAEUMEN", "LAUF", "VERIFIKATION"].includes(status.phase);
    $("start").disabled = laeuft;
    $("stop").disabled = !laeuft;

    const k = status.kennzahlen || {};
    const gesamt = Object.values(k).reduce((a, b) => a + b, 0);
    $("k-gesamt").textContent = gesamt;
    $("k-offen").textContent = k.OFFEN || 0;
    $("k-arbeit").textContent = k.IN_BEARBEITUNG || 0;
    $("k-fertig").textContent = k.FERTIG_GESPIELT || 0;
    $("k-verifiziert").textContent = k.VERIFIZIERT || 0;
    $("k-fehler").textContent = k.FEHLGESCHLAGEN || 0;
    $("k-verifikation-nok").textContent = k.VERIFIKATION_FEHLGESCHLAGEN || 0;
    $("k-dauer").textContent = status.laufdauerSekunden + " s";

    const abgeschlossen = (k.FERTIG_GESPIELT || 0) + (k.FEHLGESCHLAGEN || 0)
        + (k.VERIFIZIERT || 0) + (k.VERIFIKATION_FEHLGESCHLAGEN || 0);
    $("balken").style.width = gesamt > 0 ? (100 * abgeschlossen / gesamt) + "%" : "0";
    $("laufinfo").textContent = status.phase === "BEREIT"
        ? "Bereit — Konfiguration wählen und Simulation starten."
        : (status.logVerzeichnis ? "Logverzeichnis dieses Laufs: " + status.logVerzeichnis : "");

    const pdfLink = $("pdfhinweis");
    const pdfLeiste = $("pdfleiste");
    const pdfKnopf = $("pdfknopf");
    if (status.pdf) {
        // Auslieferung über den Simulator-Server (file:// blockiert der Browser);
        // ohne target="_blank": der eingebettete Browser öffnet keine neuen Fenster
        pdfKnopf.href = "/api/pdf";
        pdfLeiste.style.display = "block";
        pdfLink.innerHTML = "Automatische Testdokumentation (PDF, Zusammenfassung + Einzelfälle): "
            + `<a href="/api/pdf">${esc(status.pdf)}</a>`;
    } else {
        pdfLeiste.style.display = "none";
        pdfLink.textContent = "";
    }

    // Mitarbeiter-Chips
    const chips = $("mitarbeiter-chips");
    if (status.mitarbeiter && status.mitarbeiter.length > 0) {
        chips.innerHTML = status.mitarbeiter.map(m =>
            `<span class="chip ${m.aktiv ? "aktiv" : ""}">${esc(m.kuerzel)} `
            + `<span class="info">— ${m.aktiv ? esc(m.aktuellerAuftrag || "sucht Aufgabe") : "fertig, " + m.erledigt + " Aufträge"}</span></span>`
        ).join("");
    }

    // Log-Ticker
    const log = $("log");
    const unten = log.scrollHeight - log.scrollTop - log.clientHeight < 40;
    log.textContent = (status.logs || []).join("\n");
    if (unten) log.scrollTop = log.scrollHeight;

    if (status.letzteFehlermeldung) {
        $("laufinfo").textContent += " — Letzter Lauffehler: " + status.letzteFehlermeldung;
    }

    await ladeAuftraege();
}

async function ladeAuftraege() {
    let daten;
    try {
        daten = await (await fetch("/api/auftraege")).json();
    } catch (e) {
        return;
    }
    const koerper = $("auftraege-koerper");
    koerper.innerHTML = (daten.auftraege || []).map(a => `
        <tr data-id="${a.id}" class="${a.status}">
            <td>${a.id}</td>
            <td>${esc(a.szenarioTyp)}</td>
            <td>${esc(a.aktenzeichen)}</td>
            <td>${esc(a.hauptperson)}</td>
            <td>${esc(a.bearbeiter || "")}</td>
            <td><span class="status ${a.status}">${STATUS_TEXT[a.status] || esc(a.status)}</span></td>
            <td>${a.dauerMs} ms</td>
        </tr>`).join("");
    koerper.querySelectorAll("tr").forEach(tr => {
        tr.addEventListener("dblclick", () => oeffneDetail(tr.dataset.id));
    });
}

async function oeffneDetail(id) {
    let a;
    try {
        a = await (await fetch("/api/auftrag/" + id)).json();
    } catch (e) {
        return;
    }
    if (a.fehler) {
        alert(a.fehler);
        return;
    }
    $("d-titel").textContent = "Auftrag " + a.id + " — " + a.szenarioTyp
        + " (" + a.aktenzeichen + ")";
    $("d-kopf").textContent = a.hauptperson + " · " + a.beschreibung
        + " · Bearbeiter: " + (a.bearbeiter || "—") + " · Vorgang: " + (a.bgId || "—")
        + " · " + a.dauerMs + " ms · Status: " + (STATUS_TEXT[a.status] || a.status);
    $("d-fehler").innerHTML = a.fehlerText
        ? `<div class="fehlerbox">${esc(a.fehlerText)}</div>` : "";

    $("d-schritte").innerHTML = a.schritte.map(s => `
        <tr>
            <td>${esc(s.zeit)}</td>
            <td>${esc(s.mitarbeiter)}</td>
            <td>${esc(s.aktion)}</td>
            <td>${esc(s.ergebnis)}</td>
        </tr>`).join("");

    $("d-pruefungen").innerHTML = (a.pruefungen || []).length === 0
        ? '<tr><td colspan="4" class="hinweis">Noch nicht verifiziert (Verifikation läuft nach dem Laufende).</td></tr>'
        : a.pruefungen.map(p => `
        <tr>
            <td>${esc(p.beschreibung)}</td>
            <td>${esc(p.erwartet)}</td>
            <td>${esc(p.ist)}</td>
            <td class="${p.ok ? "ok" : "nok"}">${p.ok ? "OK" : "n.i.O."}</td>
        </tr>`).join("");

    $("overlay").classList.add("offen");
}

// Ticker starten
timer = setInterval(aktualisiere, 1000);
pollSofort();
