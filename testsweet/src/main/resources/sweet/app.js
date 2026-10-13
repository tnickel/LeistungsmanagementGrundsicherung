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
                    if (ergebnis.llm) {
                        meldung += "\n\nNachrechnung (100 Kombinationsfälle, unabhängige Zweitimplementierung): "
                            + (ergebnis.llm.nachgerechnet
                                ? "ERNEUT AUSGEFÜHRT für Lauf " + ergebnis.llm.lauf
                                  + (ergebnis.llm.ergebnis ? "\n  " + ergebnis.llm.ergebnis : "")
                                  + "\n  Protokoll: " + ergebnis.llm.protokoll
                                : "aktuell (Lauf " + ergebnis.llm.lauf + " bereits nachgerechnet, Protokoll "
                                  + (ergebnis.llm.protokoll || "") + ")");
                    }
                    if (ergebnis.warnungen && ergebnis.warnungen.length) {
                        meldung += "\n\nHINWEISE vor einer belastbaren Ausstellung:";
                        for (const w of ergebnis.warnungen) meldung += "\n• " + w;
                        meldung += "\n\n(LLM-Nachrechnung erneuern: testdokumentation.md + gesetzesgrundlagen-2026.md "
                            + "+ llm-pruefauftrag.txt aus doc/testzertifikat-werkzeug einer KI geben; "
                            + "Protokoll dort als llm-nachrechnung-<Datum>.md ablegen.)";
                    }
                    alert(meldung);
                }
            } catch (e) {
                alert("Serverfehler beim Erstellen: " + e.message);
            } finally {
                zertKnopf.disabled = false;
                zertKnopf.textContent = "🏅 Testzertifikat erstellen";
            }
        });
    }

    // LLM-Nachrechnung & Testzertifikat (Abschnitt 7): Status anzeigen,
    // Prüfpaket erzeugen, Prompt einsehen — einmalig registriert.
    const llmAktualisieren = $("llm-aktualisieren");
    if (llmAktualisieren) {
        llmAktualisieren.addEventListener("click", ladeLlmPruefung);
        const paketKnopf = $("llm-paket-knopf");
        if (paketKnopf) {
            paketKnopf.addEventListener("click", async () => {
                paketKnopf.disabled = true;
                paketKnopf.textContent = "🤖 Prüfpaket wird erstellt …";
                try {
                    const anzahl = Math.min(500, Math.max(1, Math.floor(Number($("llm-faelle").value) || 100)));
                    const antwort = await fetch("/api/llmpruefung/paket", {
                        method: "POST",
                        headers: { "Content-Type": "application/json" },
                        body: JSON.stringify({ faelle: anzahl })
                    });
                    const erg = await antwort.json();
                    const ziel = $("llm-paket-ergebnis");
                    if (erg.fehler) {
                        ziel.textContent = "Paket konnte nicht erstellt werden: " + erg.fehler;
                    } else {
                        ziel.innerHTML =
                            "<strong>Prüfpaket bereit — diese drei Dateien einer KI geben:</strong><br>" +
                            `1. Prüfmenge (${erg.faelle} Fälle): <code>${esc(erg.pruefmenge)}</code><br>` +
                            `2. Gesetzesgrundlagen: <code>${esc(erg.grundlagen)}</code><br>` +
                            `3. Prüfauftrag: <code>${esc(erg.pruefauftrag)}</code><br>` +
                            "<em>" + esc(erg.anleitung) + "</em>";
                    }
                } catch (e) {
                    alert("Serverfehler beim Erstellen des Prüfpakets: " + e.message);
                } finally {
                    paketKnopf.disabled = false;
                    paketKnopf.textContent = "🤖 Prüfpaket erstellen";
                    ladeLlmPruefung();
                }
            });
        }
        ladeLlmPruefung();
    }

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
        : status.phase === "FEHLGESCHLAGEN" ? globalePruefText(status)
        : "Abgeschlossen";

    // Fehlgeschlagene globale Prüfungen konkret benennen (statt nur Banner)
    function globalePruefText(status) {
        const pruefungen = (status.globalePruefungen || []).filter((p) => !p.ok);
        if (pruefungen.length === 0) return "Globale Datenbank-Verifikation fehlgeschlagen";
        return "Globale Verifikation fehlgeschlagen: " + pruefungen.map((p) =>
            `${p.beschreibung} (erwartet ${p.erwartet}, ist ${p.ist})`).join("; ");
    }

    // PDF / Markdown Banner
    const pdfLeiste = $("pdfleiste");
    const pdfKnopf = $("pdfknopf");
    const pdfHinweis = $("pdfhinweis");
    if (status.pdf) {
        pdfKnopf.href = "/api/pdf";
        pdfLeiste.style.display = "block";
        pdfHinweis.innerHTML = "📄 Generierte Testdokumentation (PDF): "
            + `<a href="/api/pdf" target="_blank">${esc(status.pdf)}</a>`
            + "<br>{ } Markdown für KI-Review (maschinenlesbar, zum Nachrechnen geben): "
            + `<a href="/api/md" target="_blank">${esc(status.md || "/api/md")}</a>`;
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

// ------------------------------------------------------------------
// LLM-Nachrechnung & Testzertifikat (Abschnitt 7)
// ------------------------------------------------------------------

/** Stand der KI-Prüfung laden und darstellen: Protokolle (mit Ablageort),
 *  MD-Datenstand mit Alter-Warnung, Prompt und mitgegebene Dateien. */
async function ladeLlmPruefung() {
    const statusZiel = $("llm-status");
    const promptZiel = $("llm-prompt");
    const dateiZiel = $("llm-dateien");
    if (!statusZiel) return;
    try {
        const daten = await (await fetch("/api/llmpruefung")).json();
        const zeilen = [];
        const protokolle = daten.protokolle || [];
        if (protokolle.length === 0) {
            zeilen.push("⚠ Noch kein Nachrechnprotokoll abgelegt — Prüfpaket erstellen, einer KI geben, "
                + "Antwort als llm-nachrechnung-&lt;Datum&gt;.md in doc/testzertifikat-werkzeug/ ablegen.");
        } else {
            const neuestes = protokolle[protokolle.length - 1];
            zeilen.push(`✓ Neuestes Protokoll: <code>${esc(neuestes.datei)}</code> `
                + `(Ablageort: <code>${esc(neuestes.ablageort || "doc/testzertifikat-werkzeug")}</code>)`);
            if (neuestes.ergebnis) zeilen.push(`&nbsp;&nbsp;Ergebnis: ${esc(neuestes.ergebnis)}`);
            if (neuestes.pruefumfang) zeilen.push(`&nbsp;&nbsp;Umfang: ${esc(neuestes.pruefumfang)}`);
            if (neuestes.modell) zeilen.push(`&nbsp;&nbsp;Modell: ${esc(neuestes.modell)}`);
            if (protokolle.length > 1) {
                zeilen.push(`&nbsp;&nbsp;(insgesamt ${protokolle.length} Protokolle im Ordner)`);
            }
        }
        if (daten.md_datenstand) {
            zeilen.push(`Testdokumentation (Datenstand): ${esc(daten.md_datenstand)}`);
            const neuestes = protokolle[protokolle.length - 1];
            if (neuestes && neuestes.datei) {
                const llmDatum = (neuestes.datei.match(/\d{4}-\d{2}-\d{2}/) || [""])[0];
                const mdDatum = (daten.md_datenstand.match(/\d{4}-\d{2}-\d{2}/) || [""])[0];
                if (llmDatum && mdDatum && mdDatum > llmDatum) {
                    zeilen.push(`⚠ Die Testdokumentation (${mdDatum}) ist NEUER als die Nachrechnung (${llmDatum}) `
                        + "— vor einer belastbaren Zertifikats-Ausstellung erneuern.");
                }
            }
        }
        statusZiel.innerHTML = zeilen.map(z => `<div>${z}</div>`).join("");
        if (promptZiel && daten.prompt) promptZiel.textContent = daten.prompt;
        if (dateiZiel) {
            const eintraege = Object.entries(daten.dateien || {});
            dateiZiel.innerHTML = eintraege.length === 0
                ? "<em>Noch keine Prüfpaket-Dateien vorhanden.</em>"
                : "<table class='tabelle'><tbody>" + eintraege.map(([label, d]) =>
                    `<tr><td>${esc(label)}</td><td><code>${esc(d.datei)}</code></td>` +
                    `<td><code>${esc(d.ablageort)}</code></td><td>${(d.bytes / 1024).toFixed(0)} KB</td></tr>`).join("")
                  + "</tbody></table>";
        }
    } catch (e) {
        statusZiel.textContent = "Status konnte nicht geladen werden: " + e.message;
    }
}
