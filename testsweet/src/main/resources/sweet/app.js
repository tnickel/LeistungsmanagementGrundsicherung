// testsweet — Oberflächenlogik & Interaktivität
"use strict";

function esc(wert) {
    if (wert === null || wert === undefined) return "";
    return String(wert)
        .replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;").replaceAll("'", "&#39;");
}

function $(id) { return document.getElementById(id); }

const STATUS_TEXT = {
    OFFEN: "offen",
    IN_BEARBEITUNG: "in Bearbeitung",
    FERTIG_GESPIELT: "fertig gespielt",
    FEHLGESCHLAGEN: "fehlgeschlagen",
    VERIFIZIERT: "verifiziert",
    VERIFIKATION_FEHLGESCHLAGEN: "Verifikation n.i.O."
};

const DIAGRAMME = {
    simulator_zyklus: {
        src: "/assets/simulator_zyklus.png",
        legende: "Ablauf und Verifikationszyklus des Mitarbeiter-Simulators testsweet (Worker-Threads, Fachschicht & SQL-Verifikation)."
    },
    bedarfsberechnung_sgb12: {
        src: "/assets/bedarfsberechnung_sgb12.png",
        legende: "Gesetzliche Bedarfs- und Leistungsberechnungskaskade nach dem SGB XII (Regelbedarfe, Mehrbedarfe, KdU, Einkommen, Schonvermögen)."
    },
    vier_augen_workflow: {
        src: "/assets/vier_augen_workflow.png",
        legende: "SEPA-Auszahlungsworkflow & Vier-Augen-Freigabe (ANGELEGT → BEREIT → UEBERGEBEN inkl. Selbstfreigabe-Abwehr und Storno)."
    },
    transaktionsarchitektur: {
        src: "/assets/transaktionsarchitektur.png",
        legende: "Schichtenarchitektur der Transaktionssteuerung (TaskController, TxManager, RetryPolicy bei ORA-54/60/8177, Oracle ACID-Kern)."
    }
};

let momentanerStatus = null;
let alleAuftraege = [];
let aktiverFilter = "ALLE";
let suchText = "";
let autoScroll = true;

// ------------------------------------------------------------------
// Initialisierung & Event-Listener
// ------------------------------------------------------------------

window.addEventListener("DOMContentLoaded", () => {
    // Presets
    $("preset-klein").addEventListener("click", () => setzePreset(5, 15, 42, 20));
    $("preset-standard").addEventListener("click", () => setzePreset(10, 60, 42, 20));
    $("preset-gross").addEventListener("click", () => setzePreset(20, 120, 42, 10));

    // Start / Stop
    $("start").addEventListener("click", starteSimulation);
    $("stop").addEventListener("click", stoppeSimulation);

    // Dialog schließen
    $("schliessen").addEventListener("click", () => $("overlay").classList.remove("offen"));
    $("overlay").addEventListener("click", (e) => {
        if (e.target === $("overlay")) $("overlay").classList.remove("offen");
    });

    // Diagramm-Tabs
    document.querySelectorAll(".d-tab").forEach(tab => {
        tab.addEventListener("click", () => {
            document.querySelectorAll(".d-tab").forEach(t => t.classList.remove("aktiv"));
            tab.classList.add("aktiv");
            const diagKey = tab.dataset.diag;
            const d = DIAGRAMME[diagKey];
            if (d) {
                $("diagramm-bild").src = d.src;
                $("diagramm-legende").textContent = d.legende;
            }
        });
    });

    // Suche & Filter
    $("suche").addEventListener("input", (e) => {
        suchText = e.target.value.trim().toLowerCase();
        rendereAuftragsTabelle();
    });

    document.querySelectorAll(".f-pill").forEach(pill => {
        pill.addEventListener("click", () => {
            document.querySelectorAll(".f-pill").forEach(p => p.classList.remove("aktiv"));
            pill.classList.add("aktiv");
            aktiverFilter = pill.dataset.filter;
            rendereAuftragsTabelle();
        });
    });

    // Auto-Scroll Ticker
    $("log-auto-scroll").addEventListener("click", () => {
        autoScroll = !autoScroll;
        $("log-auto-scroll").textContent = "Auto-Scroll: " + (autoScroll ? "Ein" : "Aus");
    });

    // Polling starten (alle 700 ms für reaktives Feedback)
    setInterval(aktualisiere, 700);
    aktualisiere();
});

function setzePreset(ma, auf, seed, denkzeit) {
    $("mitarbeiter").value = ma;
    $("auftraege").value = auf;
    $("seed").value = seed;
    $("denkzeit").value = denkzeit;
}

// ------------------------------------------------------------------
// Start / Stopp
// ------------------------------------------------------------------

async function starteSimulation() {
    const body = {
        modus: "FACHSCHICHT",
        mitarbeiter: Number($("mitarbeiter").value),
        auftraege: Number($("auftraege").value),
        seed: Number($("seed").value),
        denkzeitMs: Number($("denkzeit").value),
        aufraeumen: $("aufraeumen").checked
    };
    try {
        const antwort = await fetch("/api/start", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(body)
        });
        const ergebnis = await antwort.json();
        if (ergebnis.fehler) {
            alert("Start nicht möglich: " + ergebnis.fehler);
        }
    } catch (e) {
        alert("Serverfehler beim Starten: " + e.message);
    }
    aktualisiere();
}

async function stoppeSimulation() {
    await fetch("/api/stop", { method: "POST" });
    aktualisiere();
}

// ------------------------------------------------------------------
// Status & Polling
// ------------------------------------------------------------------

async function aktualisiere() {
    let status;
    try {
        status = await (await fetch("/api/status")).json();
    } catch (e) {
        return;
    }
    momentanerStatus = status;

    // Header Status
    const phaseEl = $("phase");
    $("phase-text").textContent = status.phase;
    phaseEl.className = "phase " + status.phase;
    $("db").textContent = status.db ? status.db.url : "";

    const laeuft = ["AUFRAEUMEN", "LAUF", "VERIFIKATION"].includes(status.phase);
    $("start").disabled = laeuft;
    $("stop").disabled = !laeuft;

    // Kennzahlen
    const k = status.kennzahlen || {};
    const gesamt = Object.values(k).reduce((a, b) => a + b, 0);
    $("k-gesamt").textContent = gesamt;
    $("nav-count").textContent = gesamt;
    $("k-offen").textContent = k.OFFEN || 0;
    $("k-arbeit").textContent = k.IN_BEARBEITUNG || 0;
    $("k-fertig").textContent = k.FERTIG_GESPIELT || 0;
    $("k-verifiziert").textContent = k.VERIFIZIERT || 0;
    $("k-fehler").textContent = k.FEHLGESCHLAGEN || 0;
    $("k-verifikation-nok").textContent = k.VERIFIKATION_FEHLGESCHLAGEN || 0;
    $("k-dauer").textContent = status.laufdauerSekunden + " s";
    $("laufzeit-label").textContent = "Laufzeit: " + status.laufdauerSekunden + " s";

    // Fortschrittsbalken
    const fertig = (k.FERTIG_GESPIELT || 0) + (k.VERIFIZIERT || 0)
        + (k.FEHLGESCHLAGEN || 0) + (k.VERIFIKATION_FEHLGESCHLAGEN || 0);
    const prozent = gesamt > 0 ? Math.round((100 * fertig) / gesamt) : 0;
    $("balken").style.width = prozent + "%";
    $("fortschritt-prozent").textContent = prozent + " %";
    $("fortschritt-label").textContent = status.phase === "BEREIT" ? "Bereit"
        : status.phase === "AUFRAEUMEN" ? "Bereinige Altdaten …"
        : status.phase === "LAUF" ? `Simulation läuft (${fertig} von ${gesamt} erledigt)`
        : status.phase === "VERIFIKATION" ? "SQL-Endzustandsprüfung auf Datenbank …"
        : "Abgeschlossen";

    // PDF / Markdown Banner
    const pdfLeiste = $("pdfleiste");
    const pdfKnopf = $("pdfknopf");
    const pdfHinweis = $("pdfhinweis");
    if (status.pdf) {
        pdfKnopf.href = "/api/pdf";
        pdfLeiste.style.display = "block";
        pdfHinweis.innerHTML = "📄 Generierte Testdokumentation (PDF): "
            + `<a href="/api/pdf" target="_blank">${esc(status.pdf)}</a>`;
    } else {
        pdfLeiste.style.display = "none";
        pdfHinweis.textContent = "";
    }

    if (status.logVerzeichnis) {
        $("logverzeichnis").innerHTML = "📁 Log-Verzeichnis: <code>" + esc(status.logVerzeichnis) + "</code>";
    }

    // Mitarbeiter-Chips
    const chips = $("mitarbeiter-chips");
    if (status.mitarbeiter && status.mitarbeiter.length > 0) {
        chips.innerHTML = status.mitarbeiter.map(m =>
            `<span class="chip ${m.aktiv ? "aktiv" : ""}">`
            + `<strong>${esc(m.kuerzel)}</strong> `
            + `<span class="info">${m.aktiv ? "spielt " + esc(m.aktuellerAuftrag || "Aufgabe") : "fertig (" + m.erledigt + ")"}</span>`
            + `</span>`
        ).join("");
    } else {
        chips.innerHTML = `<span class="hinweis">Noch kein Lauf gestartet</span>`;
    }

    // Log-Ticker
    const log = $("log");
    const warUnten = log.scrollHeight - log.scrollTop - log.clientHeight < 50;
    log.textContent = (status.logs || []).join("\n");
    if (autoScroll && warUnten) {
        log.scrollTop = log.scrollHeight;
    }

    await ladeAuftraege();
}

// ------------------------------------------------------------------
// Auftragsliste & Filter
// ------------------------------------------------------------------

async function ladeAuftraege() {
    let daten;
    try {
        daten = await (await fetch("/api/auftraege")).json();
    } catch (e) {
        return;
    }
    alleAuftraege = daten.auftraege || [];
    rendereAuftragsTabelle();
}

function rendereAuftragsTabelle() {
    let gefiltert = alleAuftraege.filter(a => {
        // Statusfilter
        if (aktiverFilter === "VERIFIZIERT" && a.status !== "VERIFIZIERT") return false;
        if (aktiverFilter === "FEHLER" && a.status !== "FEHLGESCHLAGEN" && a.status !== "VERIFIKATION_FEHLGESCHLAGEN") return false;

        // Volltextsuche
        if (suchText) {
            const haystack = (a.id + " " + a.szenarioTyp + " " + a.aktenzeichen + " "
                + a.hauptperson + " " + (a.bearbeiter || "")).toLowerCase();
            if (!haystack.includes(suchText)) return false;
        }
        return true;
    });

    $("tabelle-filter-info").textContent = `Zeige ${gefiltert.length} von ${alleAuftraege.length} Aufträgen`;

    const koerper = $("auftraege-koerper");
    koerper.innerHTML = gefiltert.map(a => `
        <tr data-id="${a.id}" class="${a.status}">
            <td><strong>#${a.id}</strong></td>
            <td><span class="badge" style="background:#f1f5f9;color:#334155">${esc(a.szenarioTyp)}</span></td>
            <td><code>${esc(a.aktenzeichen)}</code></td>
            <td>${esc(a.hauptperson)}</td>
            <td>${esc(a.bearbeiter || "—")}</td>
            <td><span class="status ${a.status}">${STATUS_TEXT[a.status] || esc(a.status)}</span></td>
            <td>${a.dauerMs} ms</td>
        </tr>`).join("");

    koerper.querySelectorAll("tr").forEach(tr => {
        tr.addEventListener("dblclick", () => oeffneDetail(tr.dataset.id));
    });
}

// ------------------------------------------------------------------
// Detail-Dialog
// ------------------------------------------------------------------

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
    $("d-titel").textContent = "Auftrag #" + a.id + " — " + a.szenarioTyp + " (" + a.aktenzeichen + ")";
    $("d-kopf").innerHTML = `
        <strong>Hauptperson:</strong> ${esc(a.hauptperson)} &nbsp;|&nbsp; 
        <strong>Sachbearbeiter:</strong> ${esc(a.bearbeiter || "—")} &nbsp;|&nbsp; 
        <strong>Vorgangs-ID:</strong> ${esc(a.bgId || "—")} &nbsp;|&nbsp; 
        <strong>Dauer:</strong> ${a.dauerMs} ms &nbsp;|&nbsp; 
        <strong>Status:</strong> <span class="status ${a.status}">${STATUS_TEXT[a.status] || esc(a.status)}</span>
        <div style="margin-top:6px; color:#334155;"><strong>Fachliche Beschreibung:</strong> ${esc(a.beschreibung)}</div>
    `;

    $("d-fehler").innerHTML = a.fehlerText
        ? `<div class="fehlerbox">⚠️ ${esc(a.fehlerText)}</div>` : "";

    $("d-schritte").innerHTML = a.schritte.map(s => {
        const istOk = s.ergebnis.startsWith("OK");
        const istFehler = s.ergebnis.includes("FEHLER") || s.ergebnis.includes("FEHLGESCHLAGEN");
        const farbe = istOk ? "ok" : (istFehler ? "nok" : "");
        return `
            <tr>
                <td><small>${esc(s.zeit.substring(11, 23))}</small></td>
                <td><code>${esc(s.mitarbeiter)}</code></td>
                <td><strong>${esc(s.aktion)}</strong></td>
                <td class="${farbe}">${esc(s.ergebnis)}</td>
            </tr>`;
    }).join("");

    $("d-pruefungen").innerHTML = (a.pruefungen || []).length === 0
        ? '<tr><td colspan="4" class="hinweis">Verifikation steht noch aus (erfolgt unmittelbar nach dem Lauf).</td></tr>'
        : a.pruefungen.map(p => `
            <tr>
                <td>${esc(p.beschreibung)}</td>
                <td><code>${esc(p.erwartet)}</code></td>
                <td><code>${esc(p.ist)}</code></td>
                <td><span class="${p.ok ? "ok" : "nok"}">${p.ok ? "✓ OK" : "✗ Abweichung"}</span></td>
            </tr>`).join("");

    $("overlay").classList.add("offen");
}
