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
    },
    stresstest_zwei_wellen: {
        src: "/assets/stresstest_zwei_wellen.png",
        legende: "Stresstest-Szenario: Welle 1 (2000 User anlegen), Phasenbarriere, Welle 2 (wiederfinden, Miete ändern, Auszahlung anordnen) — Oracle-Kern und SQL-Endprüfung."
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
    // Presets (je Testfallkategorie unterschiedliche Vorgaben)
    renderePresets();
    $("preset-klein").addEventListener("click", () => wendePresetAn("klein"));
    $("preset-standard").addEventListener("click", () => wendePresetAn("standard"));
    $("preset-gross").addEventListener("click", () => wendePresetAn("gross"));

    // Testzertifikat: Knopf stößt den Generator an (einmalig registriert); der
    // Öffnen-Link erscheint erst nach der ersten Erstellung in dieser Sitzung.
    const zertKnopf = $("zertifikatknopf");
    if (zertKnopf) {
        zertKnopf.addEventListener("click", async () => {
            if (zertifikatSuiteGesperrt()) return;
            entwerteSuiteNachManuellerAktion();
            zertKnopf.disabled = true;
            zertKnopf.textContent = "🏅 Zertifikat wird erstellt …";
            try {
                const antwort = await fetch("/api/zertifikat", { method: "POST" });
                const ergebnis = await antwort.json();
                if (ergebnis.fehler) {
                    alert("Zertifikat konnte nicht erstellt werden: " + ergebnis.fehler);
                } else {
                    const zertLink = $("zertifikatlink");
                    if (zertLink) zertLink.style.display = "inline-block";
                    let meldung = "Testzertifikat erstellt und nach doc/ kopiert:\n" + ergebnis.pdf;
                    if (ergebnis.verdict) {
                        meldung += "\n\nVerdict: " + ergebnis.verdict;
                    }
                    if (ergebnis.llm) meldung += "\nGLM-Prüfung: " + (ergebnis.llm.state || "unbekannt")
                        + " · " + (ergebnis.llm.completedCases || 0) + " Fälle";
                    if (ergebnis.warnungen && ergebnis.warnungen.length) {
                        meldung += "\n\nHINWEISE vor einer belastbaren Ausstellung:";
                        for (const w of ergebnis.warnungen) meldung += "\n• " + w;
                        meldung += "\n\nGLM-Prüfung in Abschnitt 7 aktualisieren.";
                    }
                    alert(meldung);
                }
            } catch (e) {
                alert("Serverfehler beim Erstellen: " + e.message);
            } finally {
                zertKnopf.disabled = false;
                zertKnopf.textContent = "🏅 Testzertifikat erstellen";
                sperreZertifikatSuiteBedienung();
            }
        });
    }

    initialisiereLlm();
    initialisiereZertifikatSuite();

    // Testfallkategorie-Reiter (zwischen Moduswahl und Laufkonfiguration)
    document.querySelectorAll("#kategorie-abschnitt .d-tab").forEach(tab => {
        tab.addEventListener("click", () => {
            document.querySelectorAll("#kategorie-abschnitt .d-tab").forEach(t => t.classList.remove("aktiv"));
            tab.classList.add("aktiv");
            gewaehlteKategorie = tab.dataset.kategorie;
            renderePresets();
            const defaults = KATEGORIE_PRESETS[gewaehlteKategorie].standard;
            setzePreset(defaults.ma, defaults.auf, defaults.seed, defaults.denkzeit);
        });
    });

    // Testfall-Beschreibung: Modal mit der ausführlichen Katalogliste der Kategorie.
    // Stresstest/Last liefern bewusst wenige Einträge (Rahmen + Wellen statt
    // tausender gleicher Aufträge).
    $("testfall-info").addEventListener("click", async () => {
        const overlayFenster = $("testfall-overlay");
        const liste = $("testfall-liste");
        const modusWert = gewaehlterModus === "WEB" ? "2" : "1";
        $("testfall-titel").textContent = "Testfälle im Detail — "
            + (gewaehlteKategorie === "POSITIV" ? "Positivfälle"
                : gewaehlteKategorie === "NEGATIV" ? "Negativfälle"
                : gewaehlteKategorie === "LAST" ? "Lasttest" : "Stresstest")
            + (modusWert === "2" ? " (Weboberfläche)" : " (Fachschicht)");
        liste.innerHTML = '<p class="leise">Lade Katalog …</p>';
        overlayFenster.classList.add("offen");
        try {
            const antwort = await fetch(`/api/testfaelle?kategorie=${encodeURIComponent(gewaehlteKategorie)}&modus=${modusWert}`);
            const daten = await antwort.json();
            liste.innerHTML = (daten.eintraege || []).map(e =>
                '<div class="testfall-eintrag">'
                + '<div class="testfall-name">' + esc(e.name)
                + (e.anteil ? ' <span class="testfall-anteil">' + esc(e.anteil) + '</span>' : '')
                + '</div>'
                + '<div class="testfall-text">' + esc(e.text) + '</div>'
                + '</div>').join("");
        } catch (fehler) {
            liste.innerHTML = '<p class="leise">Katalog konnte nicht geladen werden: '
                + esc(String(fehler)) + '</p>';
        }
    });
    $("testfall-schliessen").addEventListener("click", () => $("testfall-overlay").classList.remove("offen"));
    $("testfall-overlay").addEventListener("click", (e) => {
        if (e.target === $("testfall-overlay")) $("testfall-overlay").classList.remove("offen");
    });

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

    // Modus-Umschaltung (Modus 1 Fachschicht vs. Modus 2 Web/Selenium)
    $("modus1").addEventListener("click", () => {
        $("modus1").classList.add("ausgewaehlt");
        $("modus2").classList.remove("ausgewaehlt");
        gewaehlterModus = "FACHSCHICHT";
    });
    $("modus2").addEventListener("click", () => {
        $("modus2").classList.add("ausgewaehlt");
        $("modus1").classList.remove("ausgewaehlt");
        gewaehlterModus = "WEB";
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

let gewaehlterModus = "FACHSCHICHT";
let gewaehlteKategorie = "POSITIV";

const KATEGORIE_HINWEISE = {
    POSITIV: "Positivfälle: Erfolgswege mit vollständiger Prüfung",
    NEGATIV: "Negativfälle: abgewehrte Fachfehler mit unverändertem Datenbank-Endzustand",
    LAST: "Lasttest: schlanke Massendurchläufe — Report nur als Gesamtreport",
    STRESS: "Stresstest: Aufträge = User-Zahl; je User 2 Tests (anlegen + nachbearbeiten) — nur Fachschicht-Modus, Gesamtreport"
};

const KATEGORIE_PRESETS = {
    POSITIV: {
        klein: { label: "Schnell (5 MA / 15 Fälle)", ma: 5, auf: 15, seed: 42, denkzeit: 20 },
        standard: { label: "Standard (10 MA / 60 Fälle)", ma: 10, auf: 60, seed: 42, denkzeit: 20 },
        gross: { label: "Breite 500 (20 MA / 500 Kombinationen)", ma: 20, auf: 500, seed: 42, denkzeit: 5 }
    },
    NEGATIV: {
        klein: { label: "Schnell (5 MA / 15 Fälle)", ma: 5, auf: 15, seed: 42, denkzeit: 20 },
        standard: { label: "Standard (10 MA / 60 Fälle)", ma: 10, auf: 60, seed: 42, denkzeit: 20 },
        gross: { label: "Breite 500 (20 MA / 500 Kombinationen)", ma: 20, auf: 500, seed: 42, denkzeit: 5 }
    },
    LAST: {
        klein: { label: "Lasttest klein (10 MA / 120 Fälle)", ma: 10, auf: 120, seed: 42, denkzeit: 0 },
        standard: { label: "Lasttest (20 MA / 600 Fälle)", ma: 20, auf: 600, seed: 42, denkzeit: 0 },
        gross: { label: "Dauerlast (30 MA / 1200 Fälle)", ma: 30, auf: 1200, seed: 42, denkzeit: 0 }
    },
    STRESS: {
        klein: { label: "Stress klein (10 MA / 500 User)", ma: 10, auf: 500, seed: 42, denkzeit: 0 },
        standard: { label: "Stresstest (20 MA / 2000 User × 2)", ma: 20, auf: 2000, seed: 42, denkzeit: 0 },
        gross: { label: "Dauerstress (30 MA / 3000 User × 2)", ma: 30, auf: 3000, seed: 42, denkzeit: 0 }
    }
};

function wendePresetAn(name) {
    const p = KATEGORIE_PRESETS[gewaehlteKategorie][name];
    setzePreset(p.ma, p.auf, p.seed, p.denkzeit);
}

/** Zulässige Auftrags-Obergrenze je Kategorie (gleiche Werte wie
 *  Simulator.auftraegeLimit im Java-Code): 500 Positiv-/Negativ-Kombinationen,
 *  1200 Lastdurchläufe, 3000 Stress-User. */
const KATEGORIE_LIMITEN = {
    POSITIV: 500,
    NEGATIV: 500,
    LAST: 1200,
    STRESS: 3000
};

/** Preset-Knöpfe je Kategorie beschriften (klein/standard/gross) und das
 *  Aufträge-Feld auf die Kategorie-Grenze setzen (min/max + Hinweistext). */
function renderePresets() {
    const defs = KATEGORIE_PRESETS[gewaehlteKategorie];
    $("preset-klein").textContent = defs.klein.label;
    $("preset-standard").textContent = defs.standard.label;
    $("preset-gross").textContent = defs.gross.label;
    const hinweis = $("kategorie-hinweis");
    if (hinweis) hinweis.textContent = KATEGORIE_HINWEISE[gewaehlteKategorie];
    const limit = KATEGORIE_LIMITEN[gewaehlteKategorie] || 500;
    const feld = $("auftraege");
    feld.min = 1;
    feld.max = limit;
    const range = $("auftraege-range");
    if (range) range.textContent = "1–" + limit;
    const wert = Math.floor(Number(feld.value));
    if (!Number.isFinite(wert) || wert < 1) {
        feld.value = 1;
    } else if (wert > limit) {
        feld.value = limit;
    }
}

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
    if (zertifikatSuiteGesperrt()) return;
    if (gewaehlteKategorie === "STRESS" && gewaehlterModus === "WEB") {
        alert("Der Stresstest (User anlegen + nachbearbeiten) ist für den Fachschicht-Modus "
            + "vorgesehen — bitte Modus 1 (Fachschicht) wählen.");
        return;
    }
    const limit = KATEGORIE_LIMITEN[gewaehlteKategorie] || 500;
    const auftraege = Math.floor(Number($("auftraege").value));
    if (!Number.isFinite(auftraege) || auftraege < 1 || auftraege > limit) {
        alert("Die Auftragszahl muss zwischen 1 und " + limit + " liegen (Kategorie "
            + gewaehlteKategorie + ") — der Testfallbestand ist bewusst begrenzt.");
        $("auftraege").value = Math.min(Math.max(isFinite(auftraege) ? auftraege : 1, 1), limit);
        return;
    }
    const body = {
        modus: gewaehlterModus,
        kategorie: gewaehlteKategorie,
        mitarbeiter: Number($("mitarbeiter").value),
        auftraege: auftraege,
        seed: Number($("seed").value),
        denkzeitMs: Number($("denkzeit").value),
        aufraeumen: $("aufraeumen").checked
    };
    entwerteSuiteNachManuellerAktion();
    try {
        const antwort = await fetch("/api/start", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(body)
        });
        const ergebnis = await antwort.json();
        if (ergebnis.fehler) {
            if (ergebnis.fehlerart === "DB_NICHT_ERREICHBAR") {
                zeigePruefFehler("db-start-" + Date.now(),
                    "Datenbank nicht erreichbar – Testlauf nicht gestartet",
                    ergebnis.fehler,
                    "Kein Laufverzeichnis — es wurde kein Lauf gestartet",
                    {kind: "db"});
            } else {
                alert("Start nicht möglich: " + ergebnis.fehler);
            }
        }
    } catch (e) {
        alert("Serverfehler beim Starten: " + e.message);
    }
    aktualisiere();
}

async function stoppeSimulation() {
    if (zertifikatSuiteGesperrt()) return;
    await fetch("/api/stop", { method: "POST" });
    aktualisiere();
}

// ------------------------------------------------------------------
// Status & Polling
// ------------------------------------------------------------------

async function aktualisiere() {
    let status;
    try {
        status = await llmRequest("/api/status");
    } catch (e) {
        $("phase-text").textContent = "Status nicht verfügbar";
        $("phase").className = "phase FEHLGESCHLAGEN";
        $("pdfleiste").style.display = "none";
        $("zertifikatlink").style.display = "none";
        $("review-warning").hidden = false;
        $("review-warning").className = "review-error";
        $("review-warning").textContent = "Aktueller Laufstatus ist nicht bestätigt: " + e.message;
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
    sperreZertifikatSuiteBedienung();

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
        : status.phase === "FEHLGESCHLAGEN" ? (status.letzteFehlermeldung || globalePruefText(status))
        : status.phase === "ABGEBROCHEN" ? "Abgebrochen – Prüfung unvollständig"
        : status.phase === "FERTIG" ? "Automatischer Lauf abgeschlossen" : status.phase;

    // Fehlgeschlagene globale Prüfungen konkret benennen (statt nur Banner)
    function globalePruefText(status) {
        const pruefungen = (status.globalePruefungen || []).filter((p) => !p.ok);
        if (pruefungen.length === 0) return "Globale Datenbank-Verifikation fehlgeschlagen";
        return "Globale Verifikation fehlgeschlagen: " + pruefungen.map((p) =>
            `${p.beschreibung} (erwartet ${p.erwartet}, ist ${p.ist})`).join("; ");
    }

    const review = status.review || {};
    const warning = $("review-warning");
    warning.hidden = !["FERTIG","FEHLGESCHLAGEN","ABGEBROCHEN"].includes(status.phase);
    warning.className = review.state === "PASS" && status.nachweiseVollstaendig ? "review-success" : "review-error";
    warning.textContent = (review.label || "UNVOLLSTÄNDIG") + " · Automatik: " + (review.automatedState || "UNVERIFIED")
        + " · KI: " + (review.llmState || "UNVERIFIED")
        + ((review.errors || []).length ? "\n" + review.errors.join("\n") : "");
    if (zertifikatSuiteAnsichtAktiv()) warning.hidden = true;
    if (laeuft) $("zertifikatlink").style.display = "none";
    zeigeSimulatorFehler(status);

    // PDF / Markdown Banner
    const pdfLeiste = $("pdfleiste");
    const pdfKnopf = $("pdfknopf");
    const pdfHinweis = $("pdfhinweis");
    if (status.pdf && !zertifikatSuiteAnsichtAktiv()) {
        pdfKnopf.href = "/api/pdf";
        pdfLeiste.style.display = "block";
        pdfHinweis.style.display = "block";
        pdfHinweis.innerHTML = "📄 Generierte Testdokumentation (PDF): "
            + `<a href="/api/pdf" target="_blank">${esc(status.pdf)}</a>`
            + "<br>{ } Markdown für KI-Review (maschinenlesbar, zum Nachrechnen geben): "
            + `<a href="/api/md" target="_blank">${esc(status.md || "/api/md")}</a>`;
    } else {
        pdfLeiste.style.display = "none";
        pdfHinweis.style.display = "none";
        pdfHinweis.textContent = "";
        if (zertifikatSuiteAnsichtAktiv()) $("zertifikatlink").style.display = "none";
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
    log.textContent = (status.logs || []).join("\n") || "— noch keine Logzeilen vorhanden —";
    const suiteLaueft = zertifikatSuiteStatus && zertifikatSuiteStatus.running;
    $("log-quelle").textContent = status.logQuelle === "live" ? "Live — laufender Test"
        : suiteLaueft ? "Zertifikatssuite läuft — " + (zertifikatSuiteStatus.phaseLabel || "Phase aktiv")
            + (status.logQuelle === "letzter-lauf" ? " · Protokoll des neuesten Laufs" : "")
        : status.logQuelle === "letzter-lauf" ? "Ende des letzten Laufs (kein Test aktiv)"
        : "kein Lauf vorhanden";
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

// ------------------------------------------------------------------
// LLM-Nachrechnung & Testzertifikat (Abschnitt 7)
// ------------------------------------------------------------------

// GLM-Konfiguration und echte Prüfung. API-Schlüssel bleibt ausschließlich im Eingabefeld.
let llmPolling = null;
let llmPackage = null;
let llmStatusRequestId = 0;
const llmRetryPopupShown = new Set();

async function llmRequest(path, body) {
    const response = await fetch(path, body === undefined ? {} : {
        method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body)
    });
    const result = await response.json();
    if (!response.ok || result.fehler || result.ok === false) throw new Error(result.fehler || result.message || "Anfrage fehlgeschlagen");
    return result;
}

function fillLlmConfig(config) {
    $("llm-base-url").value = config.baseUrl || "";
    $("llm-model").value = config.model || "glm-5.3";
    $("llm-timeout").value = config.timeoutSeconds || 300;
    $("llm-max-tokens").value = config.maxTokens || 16384;
    $("llm-batch-size").value = config.batchSize || 3;
    $("llm-cases").value = config.cases || 100;
    $("llm-faelle").value = config.cases || 100;
    $("llm-api-key").value = "";
    $("llm-clear-key").checked = false;
    $("llm-key-status").textContent = config.apiKeyConfigured ? "API-Schlüssel vorhanden." : "Noch kein API-Schlüssel vorhanden.";
}

function initialisiereLlm() {
    $("llm-retry-close").addEventListener("click", () => $("llm-retry-dialog").close());
    $("llm-config-open").addEventListener("click", async () => {
        if (zertifikatSuiteGesperrt()) return;
        const dialog = $("llm-config-dialog");
        $("llm-config-message").textContent = "Konfiguration wird geladen …";
        dialog.showModal();
        try { fillLlmConfig(await llmRequest("/api/llm/config")); $("llm-config-message").textContent = ""; }
        catch (error) { $("llm-config-message").textContent = error.message; }
    });
    $("llm-config-close").addEventListener("click", () => $("llm-config-dialog").close());
    $("llm-config-dialog").addEventListener("close", () => { $("llm-api-key").value = ""; });
    $("llm-config-form").addEventListener("submit", async event => {
        event.preventDefault();
        if (zertifikatSuiteGesperrt()) return;
        const button = $("llm-config-save"); button.disabled = true;
        try {
            const config = await llmRequest("/api/llm/config", {
                baseUrl: $("llm-base-url").value.trim(), model: $("llm-model").value.trim(),
                apiKey: $("llm-api-key").value, clearApiKey: $("llm-clear-key").checked,
                timeoutSeconds: Number($("llm-timeout").value), maxTokens: Number($("llm-max-tokens").value),
                batchSize: Number($("llm-batch-size").value), cases: Number($("llm-cases").value)
            });
            entwerteSuiteNachManuellerAktion();
            fillLlmConfig(config); $("llm-config-message").textContent = "Konfiguration gespeichert.";
            await ladeLlmPruefung();
        } catch (error) { $("llm-config-message").textContent = error.message; }
        finally { button.disabled = false; sperreZertifikatSuiteBedienung(); }
    });
    $("llm-config-test").addEventListener("click", async () => {
        if (zertifikatSuiteGesperrt()) return;
        const button = $("llm-config-test"); button.disabled = true;
        $("llm-config-message").textContent = "Gespeicherte Konfiguration wird mit einem echten Modellaufruf geprüft …";
        try {
            const result = await llmRequest("/api/llm/test", {});
            $("llm-config-message").textContent = result.message || "GLM-Verbindung erfolgreich.";
        } catch (error) { $("llm-config-message").textContent = error.message; }
        finally { button.disabled = false; sperreZertifikatSuiteBedienung(); }
    });
    $("llm-aktualisieren").addEventListener("click", ladeLlmPruefung);
    $("llm-config-prompt").addEventListener("click", async () => {
        // Rein lesend: holt den tatsächlich eingesetzten Prüfauftrag frisch vom Server.
        const button = $("llm-config-prompt"); button.disabled = true;
        $("llm-config-message").textContent = "Prüfauftrag wird geladen …";
        try {
            const data = await llmRequest("/api/llmpruefung");
            $("llm-config-prompt-text").textContent = data.prompt || "(Kein Prüfauftrag hinterlegt.)";
            $("llm-config-prompt-bereich").open = true;
            $("llm-config-message").textContent = "";
        } catch (error) { $("llm-config-message").textContent = error.message; }
        finally { button.disabled = false; }
    });
    $("llm-paket-knopf").addEventListener("click", async () => {
        if (zertifikatSuiteGesperrt()) return;
        const button = $("llm-paket-knopf"); button.disabled = true;
        try {
            llmPackage = await llmRequest("/api/llmpruefung/paket", {faelle: Number($("llm-faelle").value)});
            $("llm-paket-ergebnis").textContent = "Prüfpaket: " + llmPackage.faelle + " Fälle · " + llmPackage.directory;
            renderLlmFiles(llmPackage);
        } catch (error) { $("llm-paket-ergebnis").textContent = error.message; }
        finally { button.disabled = false; sperreZertifikatSuiteBedienung(); }
    });
    $("llm-start-knopf").addEventListener("click", async () => {
        if (zertifikatSuiteGesperrt()) return;
        entwerteSuiteNachManuellerAktion();
        const button = $("llm-start-knopf"); button.disabled = true;
        llmStatusRequestId++;
        try {
            await llmRequest("/api/llm/start", {faelle: Number($("llm-faelle").value)});
            llmPackage = null;
            await ladeLlmPruefung();
        } catch (error) { $("llm-paket-ergebnis").textContent = error.message; button.disabled = false; sperreZertifikatSuiteBedienung(); }
    });
    ladeLlmPruefung();
}

function renderLlmFiles(data) {
    const files = data.documents || data.files || [];
    const rows = Array.isArray(files) ? files : Object.values(files);
    const fallback = [data.pruefmenge, data.grundlagen, data.pruefauftrag].filter(Boolean);
    $("llm-dateien").innerHTML = rows.length
        ? rows.map(file => `<div><strong>${esc(file.name || file.path || "Datei")}</strong><br><code>${esc(file.path || "")}</code><br>SHA-256: <code>${esc(file.sha256 || "")}</code></div>`).join("")
        : fallback.map(file => `<div><code>${esc(file)}</code></div>`).join("") || "Noch kein Prüfpaket erstellt.";
}

function renderLlmFortschritt(data) {
    const container = $("llm-fortschritt");
    const progress = $("llm-progress");
    const label = $("llm-progress-label");
    const percent = $("llm-progress-prozent");
    const details = $("llm-progress-details");
    const total = Number(data.totalCases);
    const state = data.state || "IDLE";

    container.hidden = state === "IDLE" || (state !== "PREPARING" && !(total > 0));
    if (container.hidden) return;
    container.classList.toggle("llm-fortschritt-fertig", state === "PASS");
    container.classList.toggle("llm-fortschritt-fehler", ["FAIL", "UNVERIFIED", "ERROR"].includes(state));

    if (state === "PREPARING") {
        progress.removeAttribute("value");
        label.textContent = "Prüfdateien werden vorbereitet";
        percent.textContent = "…";
        details.textContent = "Die Fallzahl wird ermittelt.";
        progress.textContent = "Prüfdateien werden vorbereitet";
        return;
    }

    const completed = Math.max(0, Math.min(total, Number(data.completedCases) || 0));
    const percentage = Math.round(completed / total * 100);
    const batches = Math.max(0, Number(data.completedBatches) || 0);
    const batchTotal = Math.max(0, Number(data.totalBatches) || 0);
    progress.max = total;
    progress.value = completed;
    label.textContent = `${completed} von ${total} Fällen vollständig beantwortet`;
    percent.textContent = `${percentage} %`;
    details.textContent = batchTotal > 0 ? `${batches} von ${batchTotal} Fallgruppen abgeschlossen` : "";
    progress.textContent = `${percentage} %`;
}

function eindeutigeFehlermeldungen(messages) {
    return [...new Set(messages.filter(Boolean).flatMap(message => String(message).split(/\r?\n/))
        .map(line => line.trim()).filter(Boolean))].join("\n");
}

function buildNachweisFehlt(message) {
    return /Build-\/Quellstand nicht nachgewiesen|Buildnachweis[^\n]*(?:fehlt|veraltet)|Build wurde seit Prozessstart ersetzt|Buildnachweis nicht lesbar/i.test(message);
}

function programmNachweisFehlt(message) {
    return /Build-\/Quellstand nicht nachgewiesen|Build wurde seit Prozessstart ersetzt|Buildnachweis nicht lesbar/i.test(message);
}

function kompaktZeitZuDeutsch(kompakt) {
    // "20261015-013136" -> "15.10.2026 um 01:31:36 Uhr" — für den Vergleich im Nachweis-Dialog.
    const m = /^(\d{4})(\d{2})(\d{2})-(\d{2})(\d{2})(\d{2})$/.exec(kompakt || "");
    return m ? `${m[3]}.${m[2]}.${m[1]} um ${m[4]}:${m[5]}:${m[6]} Uhr` : null;
}

function zeigeSimulatorFehler(status) {
    if (zertifikatSuiteAnsichtAktiv()) return;
    const review = status.review || {};
    if (review.state !== "FAIL" && !["FEHLGESCHLAGEN", "ABGEBROCHEN"].includes(status.phase)
        && !(status.nachweisFehler || []).length) return;
    const message = eindeutigeFehlermeldungen([status.letzteFehlermeldung,
        ...(status.nachweisFehler || []), ...(review.errors || [])]);
    const counts = status.kennzahlen || {};
    const failure = review.state === "FAIL" || (counts.FEHLGESCHLAGEN || 0) > 0
        || (counts.VERIFIKATION_FEHLGESCHLAGEN || 0) > 0
        || (status.globalePruefungen || []).some(check => !check.ok);
    const build = programmNachweisFehlt(message);
    const source = !build && buildNachweisFehlt(message);
    const kind = build ? "build" : failure ? "failure" : source ? "source"
        : status.dbNichtErreichbar ? "db" : status.phase === "ABGEBROCHEN" ? "aborted" : "technical";
    const title = build ? (failure ? "Testfehler und ungültiger Programmstand" : "Technischer Nachweisfehler: Programmstand passt nicht")
        : failure ? "Testfehler festgestellt – NICHT BESTANDEN" : source ? "Technischer Nachweisfehler: KI-Eingangsbelege veraltet"
        : status.dbNichtErreichbar ? "Datenbankverbindung verloren – Testlauf abgebrochen"
        : status.phase === "ABGEBROCHEN" ? "Testlauf abgebrochen – Nachweis unvollständig" : "Technischer Fehler – Testlauf nicht bestätigt";
    const optionen = {kind, failure};
    if (kind === "source") {
        // Vergleichbare Zeitangaben: wann der Lauf gespielt wurde vs. wann die
        // aktuellen Programmdateien gebaut wurden — dann sieht man die Lücke.
        const lauf = /run-(\d{8}-\d{6})/.exec(message);
        const laufZeit = lauf ? kompaktZeitZuDeutsch(lauf[1]) : null;
        const buildZeit = status.buildZeit ? new Date(status.buildZeit).toLocaleString("de-DE") : null;
        if (laufZeit && buildZeit) {
            optionen.help = {...pruefFehlerHilfe("source", failure),
                summary: `Was ist veraltet? GLM soll Unterlagen von Simulator-Läufen nachrechnen. `
                    + `Der neueste verfügbare Lauf (${lauf[0]}) wurde am ${laufZeit} gespielt. `
                    + `Die aktuellen Programmdateien wurden aber erst am ${buildZeit} gebaut — `
                    + `dazwischen liegt der Neubau. Läufe von vor dem Neubau dürfen nicht mehr `
                    + `verwendet werden, sonst belegt das Zertifikat einen alten Stand. `
                    + `Deshalb wurde die GLM-Prüfung vor dem ersten Senden abgebrochen — es wurde kein Fall geprüft. `
                    + `Schnellster Weg: den grünen Knopf „Mache alle Zertifikatstests“ drücken.`};
        }
    }
    zeigePruefFehler("sim-" + status.logVerzeichnis + "-" + status.phase + "-" + kind,
        title, message, status.logVerzeichnis || "Laufverzeichnis in der Testübersicht prüfen",
        optionen);
}

function pruefFehlerHilfe(kind, failure) {
    const technical = "Der Lauf liefert keinen gültigen Erfolgsnachweis. Das ist keine reine Information. Ein grünes Gesamtzertifikat ist damit nicht möglich.";
    if (kind === "build") return {
        severity: failure ? "TESTFEHLER UND TECHNISCHER NACHWEISFEHLER · Aktion erforderlich" : "TECHNISCHER NACHWEISFEHLER · Aktion erforderlich",
        summary: "Programmstand und Quelldateien sind nicht gemeinsam nachgewiesen. Starten Sie die Zertifikatstests; der Ablauf baut und prüft den aktuellen Stand.",
        impact: failure ? technical + " Zusätzlich liegen Testabweichungen vor; auch diese müssen ausgewertet werden."
            : "Kein gültiges Zertifikat. Die Meldung allein belegt keinen Rechenfehler und keinen GLM-Ausfall.",
        steps: [
            "Testsweet mit testsweet\\stop.bat beenden. Die vorhandenen Laufprotokolle behalten.",
            "Im Hauptordner des Projekts mvn clean install ausführen, damit das Hauptprogramm neu gebaut und installiert wird. Bei einem Buildfehler zuerst dessen Fehlermeldung auswerten.",
            "Anschließend testsweet\\start.bat starten und die Browserseite neu laden. Der Neustart lädt den neu gebauten Stand.",
            "Die betroffenen Simulatorläufe erneut ausführen. Vor einer neuen GLM-Prüfung müssen aktuelle Positiv- und Negativläufe vorliegen; die alten Belege werden nicht nachträglich gültig.",
            "Für ein vollständiges Zertifikat beide automatischen Testsuiten mit der Testdatenbank ausführen: mvn test im Hauptordner und mvn -f testsweet/pom.xml test. Außerdem alle sieben unterstützten Simulator-Kombinationen ausführen, dann GLM prüfen lassen und das Zertifikat neu erstellen.",
            "Falls dieselbe Meldung nach Neubuild und Neustart wiederkommt: nicht weiter wiederholen. Die technischen Details und das unten genannte Laufprotokoll durch die Entwicklung auswerten lassen."
        ]
    };
    if (kind === "source") return {
        severity: "VERALTETE ODER FEHLENDE KI-EINGANGSBELEGE · Aktion erforderlich",
        summary: "Was ist veraltet? GLM soll Unterlagen von Simulator-Läufen nachrechnen. Die neuesten verfügbaren Läufe wurden aber mit einer ÄLTEREN Programmversion gespielt — seitdem wurde testsweet neu gebaut und neu gestartet (z. B. durch Fehlerbehebungen). GLM darf nur Unterlagen des genau jetzt geladenen Programms prüfen, sonst belegt das Zertifikat einen alten Stand. Deshalb wurde die GLM-Prüfung vor dem ersten Senden abgebrochen — es wurde kein Fall geprüft. Schnellster Weg: den grünen Knopf „Mache alle Zertifikatstests“ drücken.",
        impact: "Kein gültiges Zertifikat. Daraus folgt weder ein GLM-Ausfall noch ein Fehler der Fachanwendung — es fehlen nur frische Simulator-Läufe zum aktuellen Programmstand.",
        steps: ["WEG 1 — Schritte einzeln: Zuerst EINEN neuen Positiv-Lauf starten (Abschnitt 1: Modus 1, Kategorie Positivfälle, dann „Simulation starten“).",
            "Danach EINEN neuen Negativ-Lauf starten (Kategorie Negativfälle). Beide müssen erfolgreich durchlaufen — sie erzeugen die frischen Unterlagen für GLM.",
            "Erst dann in Abschnitt 7 die GLM-Prüfung starten. WEG 2 — alles auf einmal: Den grünen Knopf „Mache alle Zertifikatstests“ drücken; er baut das Programm, spielt automatisch alle benötigten Läufe, lässt GLM prüfen und erstellt das Zertifikat in der richtigen Reihenfolge.",
            "Für ein vollständiges Zertifikat müssen zusätzlich beide automatischen Testsuiten (mvn test und mvn -f testsweet/pom.xml test, mit Testdatenbank) sowie alle sieben Simulator-Kombinationen aktuell belegt sein — der Gesamtlauf erledigt das mit.",
            "Bleibt der Hinweis trotz frischer Läufe bestehen, die technischen Details und das Laufprotokoll durch die Entwicklung auswerten lassen."]
    };
    if (kind === "failure") return {
        severity: "TESTABWEICHUNG · Aktion erforderlich",
        summary: "Mindestens eine Prüfung hat eine Abweichung festgestellt. Der Lauf ist NICHT BESTANDEN.",
        impact: "Die betroffenen Fälle und ihre Soll-/Ist-Werte müssen geprüft werden. Ein erneuter Lauf allein behebt den Befund nicht.",
        steps: ["Die Befunde in den technischen Details und im Testreport öffnen; bei einer GLM-Prüfung auch die Modellantwort und Gegenrechnung prüfen.",
            "Die Ursache anhand der Fall-ID und des Laufprotokolls untersuchen und beheben lassen. Protokolle aufbewahren.",
            "Danach die betroffenen Tests und gegebenenfalls GLM erneut ausführen. Erst aus gültigen neuen Nachweisen ein Zertifikat erstellen."]
    };
    if (kind === "retry") return {
        severity: "TECHNISCHER FEHLER DER KI-PRÜFUNG · Aktion erforderlich",
        summary: "3 Versuche fehlgeschlagen. Die KI-Prüfung wurde abgebrochen, weil keine gültige Antwort bestätigt werden konnte.",
        impact: technical + " Daraus folgt noch kein bestätigter Fehler der geprüften Software.",
        steps: ["Das unten genannte protocol.md auswerten lassen; darin stehen die drei Sendeversuche, Fehlerursachen und Verweise auf Requests und Antworten.",
            "Je nach protokollierter Ursache Modellantwort, Verbindung oder Konfiguration prüfen. Zugangsdaten nicht ungeschützt weitergeben.",
            "Nach Klärung die GLM-Prüfung erneut starten. Weitere Simulatorläufe sind nötig, wenn deren Quellen oder Nachweise veraltet sind."]
    };
    if (kind === "db") return {
        severity: "DATENBANK NICHT ERREICHBAR · Aktion erforderlich",
        summary: "Die Testdatenbank ist nicht erreichbar — der Docker-Container „lmg-oracle“ läuft wahrscheinlich nicht. Ohne Datenbank kann kein Testlauf ausgeführt oder nachgewiesen werden.",
        impact: "Es handelt sich um eine Umgebungsstörung, nicht um einen Fehler der Fachanwendung. Es wurde kein Lauf ausgeführt bzw. der laufende Test wurde abgebrochen.",
        steps: ["Docker Desktop öffnen und den Container „lmg-oracle“ starten — oder im Projektordner start.bat ausführen, das den Container bei Bedarf selbst hochfährt.",
            "Etwa 20–30 Sekunden warten, bis die Datenbank bereit ist (das erste Hochfahren dauert länger).",
            "Erneut auf „Simulation starten“ klicken. Kommt die Meldung trotz laufendem Container wieder, die technische Ursache in den Details auswerten lassen."]
    };
    if (kind === "aborted") return {
        severity: "ABBRUCH · Prüfung unvollständig",
        summary: "Der Testlauf wurde vorzeitig beendet. Noch offene Prüfungen sind nicht bestanden, sondern ungeprüft.",
        impact: technical,
        steps: ["Wenn Sie selbst abgebrochen haben: den gewünschten Testlauf neu starten, sobald die Umgebung bereit ist.",
            "Bei unerwartetem Abbruch zuerst die technischen Details und das Laufprotokoll auswerten lassen.",
            "Danach den vollständigen betroffenen Lauf wiederholen; für das Zertifikat fehlende Pflichtnachweise nachholen."]
    };
    return {
        severity: kind === "unverified" ? "PRÜFNACHWEIS UNVOLLSTÄNDIG · Aktion erforderlich" : "TECHNISCHER FEHLER · Aktion erforderlich",
        summary: kind === "unverified" ? "Die Prüfung ist nicht vollständig oder unabhängig bestätigt. Ein unbestätigtes Modell-PASS reicht nicht aus."
            : "Die Prüfung konnte technisch nicht ordnungsgemäß abgeschlossen oder nachgewiesen werden. Die genaue Ursache steht in den technischen Details.",
        impact: technical + " Ein fachlicher Fehler ist durch diese Meldung allein noch nicht bestätigt.",
        steps: ["Die technischen Details und das unten genannte Protokoll auswerten lassen. Ohne diese Diagnose lässt sich die Ursache nicht sicher bestimmen.",
            "Die protokollierte Ursache beheben: beispielsweise fehlende Nachweise, eine ungültige Modellantwort oder eine Verbindungsstörung.",
            "Anschließend die betroffene Prüfung erneut ausführen. Bei geänderten Quellen auch die Simulatorbelege erneuern; danach das Zertifikat neu erstellen."]
    };
}

function zeigePruefFehler(key, title, message, log, options = {}) {
    // Die neue Dialogfassung wird auch für bereits quittierte alte Meldungen angezeigt.
    const storageKey = "testsweet-prueffehler-v3-" + key;
    if (llmRetryPopupShown.has(storageKey)) return;
    try { if (sessionStorage.getItem(storageKey)) return; } catch (_) { }
    const dialog = $("llm-retry-dialog");
    if (dialog.open) return;
    const help = options.help || pruefFehlerHilfe(options.kind || "technical", options.failure);
    $("llm-retry-auto").hidden = options.auto === false || !["build", "source"].includes(options.kind) || options.failure;
    $("llm-retry-suite-start").disabled = zertifikatSuiteGesperrt();
    dialog.classList.toggle("prueffehler-abbruch", options.kind === "aborted");
    $("llm-retry-title").textContent = title;
    $("llm-retry-severity").textContent = help.severity;
    $("llm-retry-summary").textContent = help.summary;
    $("llm-retry-impact").textContent = help.impact;
    $("llm-retry-steps").replaceChildren();
    for (const step of help.steps) {
        const item = document.createElement("li");
        item.textContent = step;
        $("llm-retry-steps").appendChild(item);
    }
    $("llm-retry-details").open = false;
    $("llm-retry-message").textContent = eindeutigeFehlermeldungen([message]) || "Keine weitere technische Ursache übermittelt. Laufprotokoll auswerten lassen.";
    $("llm-retry-log").textContent = log;
    dialog.showModal();
    llmRetryPopupShown.add(storageKey);
    try { sessionStorage.setItem(storageKey,"1"); } catch (_) { }
}

function zeigeLlmRetryFehler(data) {
    if (zertifikatSuiteAnsichtAktiv()) return;
    const failure = data.hasFailures === true || data.caseVerdict === "FAIL" || data.state === "FAIL";
    if (!data.reviewId || (!failure && !["ERROR","UNVERIFIED"].includes(data.state))) return;
    const findings = (data.findings || []).map(f => typeof f === "string" ? f : (f.caseId || "Fall") + ": " + (f.description || f.message || JSON.stringify(f)));
    const build = programmNachweisFehlt(data.message || "");
    const source = !build && buildNachweisFehlt(data.message || "");
    const kind = failure ? "failure" : build ? "build" : source ? "source" : data.retryExhausted ? "retry" : data.state === "UNVERIFIED" ? "unverified" : "technical";
    const title = failure ? "GLM hat Fehler festgestellt – NICHT BESTANDEN" : build ? "Technischer Nachweisfehler: Programmstand passt nicht"
        : source ? "Technischer Nachweisfehler: GLM-Eingangsbelege veraltet"
        : data.retryExhausted ? "GLM-Prüfung abgebrochen: 3 Versuche fehlgeschlagen" : "GLM-Prüfung technisch fehlgeschlagen / unvollständig";
    const reason = data.retryExhausted ? "3 Versuche fehlgeschlagen. Bitte das Logfile auswerten." : (data.message || "Prüfung ist nicht vollständig bestätigt.");
    zeigePruefFehler(data.reviewId + "-" + (failure ? "FAIL" : data.state), title,
        [reason,...findings].join("\n"), data.artifactDirectory ? data.artifactDirectory + "\\protocol.md" : "Laufverzeichnis in Abschnitt 7 prüfen", {kind, failure});
}

async function ladeLlmPruefung() {
    if (zertifikatSuiteAnsichtAktiv()) return;
    const requestId = ++llmStatusRequestId;
    try {
        const data = await llmRequest("/api/llmpruefung");
        if (requestId !== llmStatusRequestId || zertifikatSuiteAnsichtAktiv()) return;
        const labels = {IDLE:"Bereit",PREPARING:"Prüfpaket wird vorbereitet",RUNNING:"GLM prüft",PASS:"Geprüfte Fälle bestanden",FAIL:"Abweichungen gefunden",UNVERIFIED:"Prüfung unvollständig",ERROR:"Technischer Fehler"};
        const target = $("llm-status");
        const accepted = data.state === "PASS" && data.transportVerified && data.calculationVerified && data.reviewCoverageVerified && !data.hasFailures;
        const knownFailure = data.hasFailures || data.caseVerdict === "FAIL" || data.state === "FAIL";
        target.className = accepted ? "ok" : knownFailure || ["PASS","FAIL","UNVERIFIED","ERROR"].includes(data.state) ? "nok" : "";
        const lines = [knownFailure ? "Fehler festgestellt – NICHT BESTANDEN" : data.state === "PASS" && !accepted ? "Prüfung unvollständig" : labels[data.state] || data.state || "Bereit", data.message || ""];
        if (data.totalCases) lines.push(`${data.completedCases || 0}/${data.totalCases} Fälle · ${data.completedBatches || 0}/${data.totalBatches || 0} Anfragen`);
        const sendStatistik = retryStatistikText(data.retryStatistik,
            Array.isArray(data.findings) ? data.findings.length : undefined);
        if (sendStatistik) lines.push(sendStatistik);
        if (data.model) lines.push("Modell: " + data.model);
        if (data.artifactDirectory) lines.push("Nachweise: " + data.artifactDirectory);
        target.textContent = lines.filter(Boolean).join(" · ");
        renderLlmFortschritt({...data,state: knownFailure ? "FAIL" : data.state === "PASS" && !accepted ? "UNVERIFIED" : data.state});
        const findings = $("llm-findings");
        findings.replaceChildren();
        for (const finding of data.findings || []) {
            const row = document.createElement("li");
            row.textContent = typeof finding === "string" ? finding : (finding.caseId || "Fall") + ": " + (finding.description || finding.message || JSON.stringify(finding)) + (finding.reasoning ? "\nRechnung: " + finding.reasoning : "");
            findings.appendChild(row);
        }
        findings.hidden = findings.children.length === 0;
        if (data.running) $("zertifikatlink").style.display = "none";
        zeigeLlmRetryFehler({...data,state: data.state === "PASS" && !accepted ? "UNVERIFIED" : data.state});
        const transport = $("llm-transport-check");
        transport.textContent = data.transportVerified ? "✓ Dateiübergabe geprüft: alle Prüfdokumente vollständig gesendet, SHA-256 und API-Antworten validiert." : "Dateiübergabe: noch nicht vollständig bestätigt.";
        transport.className = data.transportVerified ? "ok" : "hinweis";
        $("llm-report-link").style.display = data.reportPdf && !data.running ? "inline-block" : "none";
        $("llm-prompt").textContent = data.prompt
            || "(Prüfauftrag wurde noch nicht geladen — „↻ Aktualisieren“ klicken oder Seite neu laden. "
            + "Die Datei liegt im Projekt unter doc/testzertifikat-werkzeug/llm-pruefauftrag.txt.)";
        if (data.documents || data.files || data.pruefmenge) renderLlmFiles(data);
        else if (llmPackage) renderLlmFiles(llmPackage);
        llmEinzellaufAktiv = Boolean(data.running);
        $("llm-start-knopf").disabled = llmEinzellaufAktiv;
        sperreZertifikatSuiteBedienung();
        if (data.running && !llmPolling) llmPolling = setInterval(ladeLlmPruefung, 3000);
        if (!data.running && llmPolling) { clearInterval(llmPolling); llmPolling = null; }
    } catch (error) {
        if (requestId === llmStatusRequestId) {
            $("llm-status").className = "nok";
            $("llm-status").textContent = "GLM-Status nicht bestätigt: " + error.message;
            $("llm-transport-check").className = "nok";
            $("llm-transport-check").textContent = "Dateiübergabe kann aktuell nicht bestätigt werden.";
            $("llm-report-link").style.display = "none";
            $("zertifikatlink").style.display = "none";
        }
    }
}

// Der Server führt den gesamten Ablauf aus. Polling ermittelt ihn auch nach
// einem Reload; Browserzustand und alte PASS-Antworten sind kein Zertifikatsbeleg.
let zertifikatSuiteStatus = null;
let zertifikatSuiteStatusBekannt = true;
let zertifikatSuiteStartet = false;
let zertifikatSuiteStoppt = false;
let zertifikatSuiteAnfrage = 0;
let zertifikatSuitePollAktiv = false;
let zertifikatSuiteBedienungWarGesperrt = false;
let llmEinzellaufAktiv = false;
let zertifikatSuiteManuellErsetzt = false;

function zertifikatSuiteAnsichtAktiv() {
    return zertifikatSuiteGesperrt() || Boolean(zertifikatSuiteStatus && zertifikatSuiteStatus.state !== "IDLE" && !zertifikatSuiteManuellErsetzt);
}

function entwerteSuiteNachManuellerAktion() {
    zertifikatSuiteManuellErsetzt = true;
    ++llmStatusRequestId;
    $("zertifikat-tests-pdf").hidden = true;
    $("zertifikatlink").style.display = "none";
    if (zertifikatSuiteStatus && zertifikatSuiteStatus.state !== "IDLE") {
        $("zertifikat-tests-status").className = "hinweis";
        $("zertifikat-tests-status").textContent = "Letzter Gesamtlauf durch manuelle Aktion ergänzt. Aktuelle Gültigkeit des Zertifikats muss neu geprüft werden.";
    }
}

function zertifikatSuiteGesperrt() {
    return !zertifikatSuiteStatusBekannt || zertifikatSuiteStartet
        || zertifikatSuiteStoppt || Boolean(zertifikatSuiteStatus && zertifikatSuiteStatus.running);
}

function sperreZertifikatSuiteBedienung() {
    if (zertifikatSuiteAnsichtAktiv()) {
        $("pdfleiste").style.display = "none";
        $("pdfhinweis").style.display = "none";
        $("pdfhinweis").textContent = "";
        $("zertifikatlink").style.display = "none";
    }
    const locked = zertifikatSuiteGesperrt();
    const ids = ["start", "stop", "zertifikatknopf", "llm-start-knopf", "llm-paket-knopf",
        "llm-config-open", "llm-config-save", "llm-config-test", "llm-faelle"];
    if (locked) {
        for (const id of ids) if ($(id)) $(id).disabled = true;
    } else if (zertifikatSuiteBedienungWarGesperrt) {
        for (const id of ids) if ($(id)) $(id).disabled = false;
        const simRunning = momentanerStatus && ["AUFRAEUMEN", "LAUF", "VERIFIKATION"].includes(momentanerStatus.phase);
        $("start").disabled = Boolean(simRunning);
        $("stop").disabled = !simRunning;
        $("llm-start-knopf").disabled = llmEinzellaufAktiv;
    }
    zertifikatSuiteBedienungWarGesperrt = locked;
    const manualRunning = llmEinzellaufAktiv || momentanerStatus && ["AUFRAEUMEN", "LAUF", "VERIFIKATION"].includes(momentanerStatus.phase);
    for (const id of ["zertifikat-tests-start", "llm-retry-suite-start"]) {
        if ($(id)) $(id).disabled = locked || Boolean(manualRunning);
    }
    if ($("zertifikat-tests-stop")) $("zertifikat-tests-stop").disabled = !zertifikatSuiteStatusBekannt
        || zertifikatSuiteStoppt || !(zertifikatSuiteStatus && zertifikatSuiteStatus.running);
}

function initialisiereZertifikatSuite() {
    $("zertifikat-tests-start").addEventListener("click", starteZertifikatSuite);
    $("llm-retry-suite-start").addEventListener("click", starteZertifikatSuite);
    $("zertifikat-tests-stop").addEventListener("click", stoppeZertifikatSuite);
    zertifikatSuiteStatusBekannt = false;
    sperreZertifikatSuiteBedienung();
    ladeZertifikatSuite();
    // Absichtlich auch in IDLE pollen: Ein anderer Tab kann den Ablauf starten.
    setInterval(ladeZertifikatSuite, 2000);
}

function sichereZertifikatSuiteAntwort(data) {
    if (!data || !["IDLE", "RUNNING", "SUCCESS", "FAILED", "CANCELLED"].includes(data.state)
        || typeof data.running !== "boolean" || (data.state === "RUNNING") !== data.running) {
        throw new Error("Ungültige Statusantwort des Zertifikatsablaufs");
    }
    return data;
}

function retryStatistikText(rs, befundeZahl) {
    // rs: {ersterVersuch, eineWiederholung, zweiWiederholungen, gescheiterteFallgruppen, wiederholteSendungen}
    if (!rs) return "";
    const gruppen = (rs.ersterVersuch || 0) + (rs.eineWiederholung || 0) + (rs.zweiWiederholungen || 0) + (rs.gescheiterteFallgruppen || 0);
    if (gruppen === 0 && (rs.wiederholteSendungen || 0) === 0) return "";
    let text = `GLM-Sendeversuche: ${gruppen} Fallgruppe(n) — ${rs.ersterVersuch || 0}× direkt gültig · `
        + `${rs.eineWiederholung || 0}× nach 1 Wiederholung · ${rs.zweiWiederholungen || 0}× nach 2 Wiederholungen · `
        + `${rs.gescheiterteFallgruppen || 0}× nach 3 Versuchen gescheitert · `
        + `${rs.wiederholteSendungen || 0} formale Modellantwort(en) mussten wiederholt werden`;
    if (Number.isInteger(befundeZahl)) text += ` · ${befundeZahl} fachliche Abweichung(en) vom Modell gemeldet`;
    return text;
}

function renderZertifikatSuite(data) {
    zertifikatSuiteStatus = data;
    zertifikatSuiteStatusBekannt = true;
    const accepted = data.state === "SUCCESS" && data.verdict === "BESTANDEN" && data.certificateCurrent === true && !zertifikatSuiteManuellErsetzt;
    const bad = ["FAILED", "CANCELLED"].includes(data.state) || data.state === "SUCCESS" && !accepted;
    const labels = {IDLE:"Bereit. Ein Klick führt alle Zertifikatsprüfungen aus.", RUNNING:"Zertifikatstests laufen",
        SUCCESS: accepted ? "Alle Zertifikatstests bestanden. Das Zertifikat ist erstellt." : "Zertifikat nicht aktuell bestätigt.",
        FAILED:"Zertifikatstests fehlgeschlagen. Kein Erfolgszertifikat.", CANCELLED:"Ablauf abgebrochen. Die Prüfung ist unvollständig."};
    const target = $("zertifikat-tests-status");
    target.textContent = zertifikatSuiteManuellErsetzt ? "Letzter Gesamtlauf durch manuelle Aktion ergänzt. Aktuelle Gültigkeit des Zertifikats muss neu geprüft werden." : labels[data.state] + (data.message ? " " + data.message : "");
    target.className = accepted ? "ok" : zertifikatSuiteManuellErsetzt ? "hinweis" : bad ? "nok" : "";
    $("zertifikat-tests-pdf").hidden = !accepted;
    if (!accepted && data.state !== "IDLE") $("zertifikatlink").style.display = "none";
    $("zertifikat-tests-fortschritt").hidden = data.state === "IDLE";
    const completed = Math.max(0, Number(data.completedPhases || data.completedSteps) || 0);
    const total = Math.max(0, Number(data.totalPhases || data.totalSteps) || 0);
    const pct = total > 0 ? Math.floor(Math.min(completed, total) / total * 100) : 0;
    const progress = $("zertifikat-tests-progress");
    progress.max = total || 1;
    progress.value = Math.min(completed, total);
    $("zertifikat-tests-prozent").textContent = `${pct} % der Phasen`;
    $("zertifikat-tests-phase").textContent = data.phaseLabel || "Ablauf wird vorbereitet";
    const elapsed = Math.max(0, Math.floor(Number(data.elapsedSeconds) || 0));
    $("zertifikat-tests-details").textContent = `${completed}/${total || "?"} Phasen abgeschlossen · Laufzeit ${Math.floor(elapsed / 60)} min ${elapsed % 60} s. Keine verlässliche Restzeitschätzung.`;
    const items = Math.max(0, Number(data.completedItems || data.phaseCompleted) || 0);
    const itemTotal = Math.max(0, Number(data.totalItems || data.phaseTotal) || 0);
    $("zertifikat-tests-teilfortschritt").hidden = !data.running;
    const sub = $("zertifikat-tests-phase-progress");
    if (itemTotal > 0) {
        sub.max = itemTotal; sub.value = Math.min(items, itemTotal);
        $("zertifikat-tests-phase-details").textContent = `${items}/${itemTotal} Fälle in der aktuellen Phase`;
    } else {
        sub.removeAttribute("value");
        $("zertifikat-tests-phase-details").textContent = "Aktuelle Phase läuft; genauer Teilfortschritt wird nicht gemeldet.";
    }
    const statistik = retryStatistikText(data.retryStatistik || (data.llmSummary && data.llmSummary.retryStatistik),
        Array.isArray(data.llmSummary && data.llmSummary.findings) ? data.llmSummary.findings.length
            : Number.isInteger(data.findingsCount) ? data.findingsCount : undefined);
    const statistikEl = $("zertifikat-tests-glm-statistik");
    statistikEl.hidden = !statistik;
    if (statistik) statistikEl.textContent = statistik;
    const befunde = (data.llmSummary && data.llmSummary.findings) || [];
    const befundeBereich = $("zertifikat-tests-befunde");
    befundeBereich.hidden = befunde.length === 0;
    if (befunde.length) {
        $("zertifikat-tests-befunde-anzahl").textContent = befunde.length;
        const liste = $("zertifikat-tests-befunde-liste");
        liste.replaceChildren();
        for (const finding of befunde) {
            const eintrag = document.createElement("div");
            eintrag.style.margin = "6px 0";
            const text = typeof finding === "string" ? finding
                : (finding.caseId || "Fall") + " :: " + (finding.description || finding.message || JSON.stringify(finding));
            const trenn = text.indexOf(" :: ");
            if (trenn > 0) {
                const kennung = document.createElement("strong");
                kennung.textContent = text.slice(0, trenn);
                eintrag.appendChild(kennung);
                eintrag.appendChild(document.createElement("br"));
                eintrag.appendChild(document.createTextNode(text.slice(trenn + 4)));
            } else {
                eintrag.textContent = text;
            }
            liste.appendChild(eintrag);
        }
    }
    sperreZertifikatSuiteBedienung();
    if (zertifikatSuiteAnsichtAktiv()) {
        ++llmStatusRequestId;
        $("review-warning").hidden = true;
        $("llm-status").textContent = data.running ? "GLM folgt nach den automatischen Tests und Simulatorläufen." : data.llmSummary && data.llmSummary.state ? "GLM im Gesamtlauf: " + data.llmSummary.state : "GLM-Nachweis siehe Ergebnis des Gesamtablaufs.";
        $("llm-status").className = accepted ? "ok" : bad ? "nok" : "";
        $("llm-findings").hidden = true;
        $("llm-fortschritt").hidden = true;
        $("llm-report-link").style.display = "none";
        $("llm-transport-check").textContent = "Dateiübergabe und fachliches GLM-Urteil werden im Zertifikat des Gesamtablaufs ausgewiesen.";
        $("llm-transport-check").className = "hinweis";
    }
    if (data.running && $("llm-retry-dialog").open) $("llm-retry-dialog").close();
    if (bad && !zertifikatSuiteManuellErsetzt) {
        const failed = data.state === "FAILED";
        const testFailure = failed && data.failureKind === "TEST_FAILURE";
        const outdated = data.state === "SUCCESS" && !accepted;
        zeigePruefFehler("suite-" + (data.suiteId || data.jobId || "unbekannt") + "-" + data.state,
            testFailure ? "Tests haben Fehler gefunden" : failed ? "Technischer Fehler im Zertifikatsablauf" : outdated ? "Zertifikat nicht mehr aktuell" : "Zertifikatsprüfung unvollständig", data.message || labels[data.state],
            data.logPath || data.artifactDirectory || "Serverprotokoll prüfen", {auto:outdated,
                kind: outdated ? "source" : data.state === "CANCELLED" ? "aborted" : "technical",
                help:{severity: testFailure ? "TESTFEHLER · NICHT BESTANDEN" : failed ? "TECHNISCHER FEHLER · Kein Erfolgszertifikat" : "UNVOLLSTÄNDIG · Kein Erfolgszertifikat",
                    summary: outdated ? "Die bisherigen Nachweise sind nicht mehr aktuell. Klicken Sie auf „Mache alle Zertifikatstests“."
                        : data.state === "CANCELLED" ? "Der Ablauf wurde abgebrochen. Es wurde kein Erfolgszertifikat erstellt."
                        : testFailure ? "Mindestens ein Test hat eine Abweichung gefunden. Der Gesamtlauf ist NICHT BESTANDEN."
                        : "Der Ablauf wurde ohne Erfolgszertifikat beendet.",
                    impact: outdated ? "Der neue Ablauf prüft den aktuellen Stand und erstellt anschließend das Zertifikat."
                        : "Lassen Sie die Ursache anhand des unten genannten Protokolls prüfen. Erst nach der Behebung erneut starten.",
                    steps:[data.message || "Keine weitere Ursache übermittelt. Protokoll auswerten lassen."]}});
    }
}

function zertifikatSuiteStatusFehler(error) {
    zertifikatSuiteStatusBekannt = false;
    $("zertifikat-tests-status").className = "nok";
    $("zertifikat-tests-status").textContent = "Status nicht erreichbar. Erfolg und Abbruch sind nicht bestätigt; Verbindung zum Server prüfen. " + error.message;
    $("zertifikat-tests-pdf").hidden = true;
    $("zertifikatlink").style.display = "none";
    sperreZertifikatSuiteBedienung();
}

async function ladeZertifikatSuite() {
    if (zertifikatSuitePollAktiv || zertifikatSuiteStartet || zertifikatSuiteStoppt) return;
    zertifikatSuitePollAktiv = true;
    const requestId = ++zertifikatSuiteAnfrage;
    try {
        const data = sichereZertifikatSuiteAntwort(await llmRequest("/api/zertifikat/tests/status"));
        if (requestId === zertifikatSuiteAnfrage) renderZertifikatSuite(data);
    } catch (error) {
        if (requestId === zertifikatSuiteAnfrage) zertifikatSuiteStatusFehler(error);
    } finally { zertifikatSuitePollAktiv = false; }
}

async function starteZertifikatSuite() {
    if (zertifikatSuiteGesperrt() || $("zertifikat-tests-start").disabled) return;
    zertifikatSuiteStartet = true;
    zertifikatSuiteManuellErsetzt = false;
    ++llmStatusRequestId;
    ++zertifikatSuiteAnfrage; // Eine ältere IDLE-/SUCCESS-Antwort darf diesen Start nicht überschreiben.
    sperreZertifikatSuiteBedienung();
    $("zertifikat-tests-pdf").hidden = true;
    $("zertifikatlink").style.display = "none";
    $("zertifikat-tests-status").textContent = "Zertifikatsablauf wird gestartet …";
    $("zertifikat-tests-status").className = "";
    if ($("llm-retry-dialog").open) $("llm-retry-dialog").close();
    try {
        renderZertifikatSuite(sichereZertifikatSuiteAntwort(await llmRequest("/api/zertifikat/tests/start", {faelle:Number($("llm-faelle").value)})));
    } catch (error) { zertifikatSuiteStatusFehler(error); }
    finally { zertifikatSuiteStartet = false; sperreZertifikatSuiteBedienung(); }
}

async function stoppeZertifikatSuite() {
    if (!zertifikatSuiteStatusBekannt || !zertifikatSuiteStatus || !zertifikatSuiteStatus.running || zertifikatSuiteStoppt) return;
    zertifikatSuiteStoppt = true;
    ++zertifikatSuiteAnfrage;
    sperreZertifikatSuiteBedienung();
    $("zertifikat-tests-status").textContent = "Abbruch angefordert. Warte auf das sichere Ende der laufenden Phase …";
    try {
        renderZertifikatSuite(sichereZertifikatSuiteAntwort(await llmRequest("/api/zertifikat/tests/stop", {})));
    } catch (error) { zertifikatSuiteStatusFehler(error); }
    finally { zertifikatSuiteStoppt = false; sperreZertifikatSuiteBedienung(); }
}
