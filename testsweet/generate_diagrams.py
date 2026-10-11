# -*- coding: utf-8 -*-
"""Erzeugt die Fach- und Ablaufdiagramme für die testsweet-Testdokumentation.

Aufruf: python testsweet/generate_diagrams.py
Speicherort der Bilder: testsweet/src/main/resources/sweet/assets/ und testsweet/assets/
"""
import os
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.patches import FancyBboxPatch, FancyArrowPatch, Polygon
from PIL import Image

FARBE_TEXT = "#0f172a"
STIL = {
    "titel":   ("#1e3a8a", "#1e3a8a"),   # Dunkelblau
    "web":     ("#eff6ff", "#3b82f6"),   # Hellblau
    "fach":    ("#f0fdf4", "#16a34a"),   # Hellgrün
    "tx":      ("#e0e7ff", "#4338ca"),   # Indigo
    "db":      ("#fef3c7", "#d97706"),   # Bernstein / Gold
    "ent":     ("#ffedd5", "#ea580c"),   # Orange (Entscheidung)
    "komp":    ("#fee2e2", "#dc2626"),   # Rot (Fehler / Storno)
    "ok":      ("#dcfce7", "#15803d"),   # Grün (Erfolg / Verifiziert)
    "neutral": ("#f8fafc", "#64748b"),   # Grau
    "kasten":  ("#f1f5f9", "#cbd5e1"),   # Rahmen
}
PFEIL = "#334155"


def box(ax, x, y, w, h, text, stil="neutral", size=8.5, bold=False, text_color=None):
    fc, ec = STIL.get(stil, STIL["neutral"])
    r = FancyBboxPatch((x, y), w, h, boxstyle="round,pad=0.3,rounding_size=1.0",
                       linewidth=1.3, facecolor=fc, edgecolor=ec, zorder=2)
    ax.add_patch(r)
    if text:
        col = text_color if text_color else ("#ffffff" if stil == "titel" else FARBE_TEXT)
        ax.text(x + w / 2, y + h / 2, text, ha="center", va="center", fontsize=size,
                color=col, zorder=3,
                fontweight="bold" if bold else "normal", linespacing=1.3)


def container(ax, x, y, w, h, titel, stil="kasten"):
    fc, ec = STIL.get(stil, STIL["kasten"])
    r = FancyBboxPatch((x, y), w, h, boxstyle="round,pad=0.35,rounding_size=1.2",
                       linewidth=1.1, facecolor=fc, edgecolor=ec, linestyle="--", zorder=1)
    ax.add_patch(r)
    if titel:
        ax.text(x + 2.0, y + h - 1.8, titel, ha="left", va="center", fontsize=8.4,
                fontweight="bold", color="#475569", zorder=2)


def pfeil(ax, x1, y1, x2, y2, farbe=PFEIL, stil="-|>", style="arc3,rad=0", lw=1.4):
    ax.add_patch(FancyArrowPatch((x1, y1), (x2, y2), arrowstyle=stil, mutation_scale=12,
                                 linewidth=lw, color=farbe, connectionstyle=style, zorder=3))


def label(ax, x, y, text, farbe=PFEIL, size=7.8, ha="center"):
    ax.text(x, y, text, ha=ha, va="center", fontsize=size, color=farbe, zorder=4,
            linespacing=1.25,
            bbox=dict(boxstyle="round,pad=0.22", fc="white", ec="#cbd5e1", lw=0.75, alpha=0.96))


def entscheidung(ax, cx, cy, w, h, text, size=8.4):
    fc, ec = STIL["ent"]
    ax.add_patch(Polygon([(cx - w / 2, cy), (cx, cy + h / 2), (cx + w / 2, cy), (cx, cy - h / 2)],
                         closed=True, facecolor=fc, edgecolor=ec, linewidth=1.3, zorder=2))
    ax.text(cx, cy, text, ha="center", va="center", fontsize=size,
            color=FARBE_TEXT, zorder=3, linespacing=1.2, fontweight="bold")


def neue_fig(titel, untertitel, hoehe):
    fig, ax = plt.subplots(figsize=(12.0, hoehe), dpi=200)
    ax.set_xlim(0, 130)
    total_y = hoehe * 10.0
    ax.set_ylim(0, total_y)
    ax.axis("off")
    # Akzentleiste
    ax.fill_between([0, 130], total_y - 0.2, total_y - 0.5, color="#1e3a8a", zorder=1)
    ax.text(0, total_y - 1.8, titel, ha="left", va="top",
            fontsize=12.5, fontweight="bold", color="#0f172a")
    if untertitel:
        ax.text(0, total_y - 4.2, untertitel, ha="left", va="top",
                fontsize=8.6, color="#475569")
    return fig, ax


def speichern(fig, dateiname_basis, zielordner):
    os.makedirs(zielordner, exist_ok=True)
    png_pfad = os.path.join(zielordner, f"{dateiname_basis}.png")
    jpg_pfad = os.path.join(zielordner, f"{dateiname_basis}.jpg")
    fig.savefig(png_pfad, dpi=200, bbox_inches="tight", pad_inches=0.12, facecolor="white")
    plt.close(fig)

    img = Image.open(png_pfad).convert("RGB")
    img.save(jpg_pfad, "JPEG", quality=95, optimize=True)

    # Auch im Haupt-Assets-Ordner spiegeln
    haupt_assets = os.path.join(os.path.dirname(__file__), "assets")
    os.makedirs(haupt_assets, exist_ok=True)
    img.save(os.path.join(haupt_assets, f"{dateiname_basis}.png"), "PNG")
    img.save(os.path.join(haupt_assets, f"{dateiname_basis}.jpg"), "JPEG", quality=95, optimize=True)
    print(f"Diagramm erzeugt: {png_pfad}")


# -------------------------------------------------------------------------
# 1. Simulator-Zyklus: Architektur und Ablauf
# -------------------------------------------------------------------------
def gen_simulator_zyklus(zielordner):
    fig, ax = neue_fig("Ablauf & Testzyklus des Mitarbeiter-Simulators testsweet",
                       "Parallele Sachbearbeiter-Simulation, Transaktionsausführung auf Oracle und automatischer Nachweis", 7.6)
    
    # 1. Konfiguration & Start
    box(ax, 2, 41, 25, 22, "1. Start & Konfig\n\n• N Sachbearbeiter\n• M Aufträge (Pool)\n• Seed & Denkzeit\n• Altdaten-Cleanup", "web", 8.4, True)
    pfeil(ax, 27, 52, 32, 52)
    
    # 2. Auftragstopf
    container(ax, 32, 36, 29, 29, "2. Gemeinsamer Auftragspool", "kasten")
    box(ax, 34, 55, 25, 6.4, "Standardrente (Vier-Augen)\nPaar · Mehrbedarfe (§ 30)", "neutral", 7.6)
    box(ax, 34, 46.5, 25, 6.4, "Erwerb (§ 82) · Familie\nVermögen (§ 90) · Idempotenz", "neutral", 7.6)
    box(ax, 34, 38.5, 25, 6.0, "Storno/Korrektur · Konkurrenz", "neutral", 7.6)
    pfeil(ax, 61, 52, 66, 52)
    label(ax, 63.5, 54.5, "atomar")

    # 3. Parallele Worker-Threads
    container(ax, 66, 36, 30, 29, "3. Parallele Worker-Threads", "tx")
    box(ax, 68, 55, 26, 6.4, "Sachbearbeiter sb-sim-01\nzieht Auftrag & spielt Schritte", "tx", 7.8)
    box(ax, 68, 46.5, 26, 6.4, "Sachbearbeiter sb-sim-02 … 09\narbeitet parallel im selben Pool", "tx", 7.8)
    box(ax, 68, 38.5, 26, 6.0, "sb-sim-10 / Kassenlauf (parallel)", "tx", 7.8)
    pfeil(ax, 96, 52, 101, 52)

    # 4. Fachschicht & DB
    container(ax, 101, 36, 27, 29, "4. Fachschicht & DB", "db")
    box(ax, 103, 55, 23, 6.4, "SozialLeistungsService\n+ Leistungsberechnung", "fach", 7.8)
    box(ax, 103, 46.5, 23, 6.4, "TxManager (ACID)\nRetry bei ORA-54/60/8177", "tx", 7.8)
    box(ax, 103, 38.5, 23, 6.0, "Oracle DBMS (Sperren)", "db", 8.0, True)

    # Übergang nach unten: Nach Laufende
    pfeil(ax, 114, 36, 114, 27, stil="-")
    pfeil(ax, 114, 27, 96, 27)
    label(ax, 114, 31.5, "Pool leer\n(Laufende)", ha="center")

    # 5. SQL Endzustands-Verifikation
    container(ax, 38, 7, 58, 23, "5. Revisionssichere SQL-Verifikation (Erwartung vs. Ist)", "kasten")
    box(ax, 40, 19, 54, 7.5, "Vollständige Prüfung je Auftrag direkt auf den Datenbanktabellen:\n• Vorgangsdaten, BG-Zustand, Personen & Kinder\n• Bewilligter Zahlbetrag & gemindertes Restbudget", "neutral", 8.0)
    box(ax, 40, 9.5, 54, 8.0, "• Statusfolge (ANGELEGT → BEREIT → UEBERGEBEN)\n• Vier-Augen-Freigabe (Erfasser ≠ Freigeber)\n• Versionierung (Versionsspalte & Trigger gegen Lost Updates)", "neutral", 8.0)

    pfeil(ax, 38, 18, 28, 18)
    
    # 6. Berichterstellung
    container(ax, 2, 7, 26, 23, "6. Testdokumentation", "ok")
    box(ax, 4, 19, 22, 7.5, "Automatische PDF\n• Deckblatt & Kennzahlen\n• Rechenwege je Fall", "ok", 8.0, True)
    box(ax, 4, 9.5, 22, 8.0, "Markdown (.md)\n• Revisionssichere SQLs\n• DB-Primärschlüssel (IDs)", "neutral", 7.8)

    speichern(fig, "simulator_zyklus", zielordner)


# -------------------------------------------------------------------------
# 2. SGB XII Bedarfsberechnungs-Kaskade
# -------------------------------------------------------------------------
def gen_bedarfsberechnung(zielordner):
    fig, ax = neue_fig("Berechnungskaskade nach dem SGB XII (SDN.Leistung LMG)",
                       "Schrittweise Bedarfs- und Einkommensermittlung für Einzelfälle und Bedarfsgemeinschaften (Stand 2026)", 8.0)

    # Spalte links: Bedarfe
    container(ax, 2, 30, 61, 41, "1. Bedarfsermittlung (je Person & BG)", "fach")
    box(ax, 4, 59.5, 57, 7.8, "Regelbedarf (Anlage zu § 28 SGB XII)\nStufe 1: 563 € · Stufe 2: 506 € · Stufe 3: 451 €\nStufe 4: 471 € · Stufe 5: 390 € · Stufe 6: 357 €", "neutral", 8.0)
    pfeil(ax, 32.5, 59.5, 32.5, 56)
    
    box(ax, 4, 45.5, 57, 10.5, "Gesetzliche Mehrbedarfe (§ 30 SGB XII)\n• Merkzeichen G: 17 % des Regelbedarfs (Abs. 1)\n• Schwangerschaft ab 13. SSW: 17 % (Abs. 2)\n• Alleinerziehende: 12 % / 36 % der Stufe 1 (Abs. 3)\n• Dezentrales Warmwasser: 0,8 % – 2,3 % (Abs. 7)\nDeckelung: Summe Abs. 1–5 ≤ eigener Regelbedarf (Abs. 6)", "fach", 7.6)
    pfeil(ax, 32.5, 45.5, 32.5, 42)
    
    box(ax, 4, 32.5, 57, 9.5, "Kosten der Unterkunft und Heizung (§ 42a SGB XII)\n• Kaltmiete + kalte Betriebskosten + Heizkosten\n• Aufteilung auf Mitglieder der Bedarfsgemeinschaft\n• Angemessenheitsgrenzen je Haushaltsgröße (§ 35)", "neutral", 8.0)

    # Spalte rechts: Einkommen & Vermögen
    container(ax, 67, 30, 61, 41, "2. Anrechnung von Einkommen & Vermögen", "tx")
    box(ax, 69, 59.5, 57, 7.8, "Erwerbseinkommen & Rente (§ 82 SGB XII)\n• Renteneinkünfte werden voll angerechnet\n• Brutto-Erwerbseinkommen bildet Berechnungsgrundlage", "neutral", 8.0)
    pfeil(ax, 97.5, 59.5, 97.5, 56)
    
    box(ax, 69, 45.5, 57, 10.5, "Freibeträge & Absetzbeträge (§ 82 Abs. 2 & 3)\n• Erwerbsfreibetrag: 30 % des Brutto (max. 50 % RBS 1 = 281,50 €)\n• Absetzbeträge: Steuern, Pflichtbeiträge zur Sozial-\n  versicherung, Versicherungen und Werbungskosten", "tx", 7.8)
    pfeil(ax, 97.5, 45.5, 97.5, 42)
    
    box(ax, 69, 32.5, 57, 9.5, "Vermögensprüfung (§ 90 SGB XII i.V.m. BarbetragsVO)\n• Schonvermögen: 10.000 € je erwachsener Person (RBS 1–3)\n• 500 € je überwiegend unterhaltener Person\n• Nur der Überschuss mindert den Leistungsanspruch", "db", 7.8)

    # Zusammenführung unten: Gesamtberechnung
    pfeil(ax, 32.5, 30, 32.5, 25, stil="-")
    pfeil(ax, 32.5, 25, 46, 25)
    pfeil(ax, 97.5, 30, 97.5, 25, stil="-")
    pfeil(ax, 97.5, 25, 84, 25)

    box(ax, 36, 19.5, 58, 10.5, "Zahlbetrag der Bedarfsgemeinschaft:\nGesamtbedarf (Summe aller Mitglieder)\n− anrechenbares Einkommen − anrechenbares Vermögen", "titel", 8.8, True)
    
    pfeil(ax, 65, 19.5, 65, 15)
    entscheidung(ax, 65, 11, 26, 7.5, "Zahlbetrag > 0 ?")
    
    pfeil(ax, 78, 11, 95, 11)
    label(ax, 86, 13.0, "ja")
    box(ax, 95, 6.5, 33, 8.5, "Bewilligung & Auszahlung:\n• Anordnung im Status ANGELEGT\n• Budgetprüfung gegen Restbudget", "ok", 8.0, True)

    pfeil(ax, 52, 11, 35, 11)
    label(ax, 44, 13.0, "nein")
    box(ax, 2, 6.5, 33, 8.5, "Kein Anspruch (§ 43 Abs. 1):\n• Fachfehler KEIN_ANSPRUCH\n• Keine Zahlung, Budget unberührt", "komp", 8.0, True)

    speichern(fig, "bedarfsberechnung_sgb12", zielordner)


# -------------------------------------------------------------------------
# 3. Vier-Augen-Prinzip & SEPA Workflow
# -------------------------------------------------------------------------
def gen_vier_augen_workflow(zielordner):
    fig, ax = neue_fig("Auszahlungs-Workflow & Vier-Augen-Freigabe",
                       "Lebenszyklus einer Auszahlung: Von der Anordnung bis zur Kassenübergabe inkl. Storno", 7.4)

    # Schritt 1
    box(ax, 2, 43, 26, 14, "1. Anordnung erfassen\n\n• Sachbearbeiter A\n• SEPA Modulo 97 Check\n• Budget-Vorabprüfung\n• Status: ANGELEGT", "web", 8.2, True)
    pfeil(ax, 28, 50, 42, 50)
    label(ax, 35, 52.5, "angelegt")

    # Schritt 2: Vier-Augen Prüfung
    container(ax, 42, 35, 46, 26, "2. Vier-Augen-Prüfung (Freigabe)", "tx")
    entscheidung(ax, 65, 49, 28, 9.0, "Selbstfreigabe?\n(Erfasser == Freigeber)")
    
    # Selbstfreigabe abgewehrt
    pfeil(ax, 65, 44.5, 65, 30)
    label(ax, 70, 37.5, "ja: ABWEHR", farbe="#dc2626")
    box(ax, 48, 20, 34, 9.0, "Abweisung: 403 / Fachfehler\n„Vier-Augen-Prinzip verletzt:\nSelbstfreigabe unzulässig“", "komp", 7.8)

    # Freigabe durch Kollegen
    pfeil(ax, 79, 49, 98, 49)
    label(ax, 88.5, 51.5, "nein: Kollege B")

    # Schritt 3: BEREIT
    box(ax, 98, 43, 30, 14, "3. Freigabe wirksam\n\n• Status: BEREIT\n• Rechnerische Feststellung\n• Für Kassenlauf vorgemerkt", "ok", 8.2, True)

    # Kassenlauf
    pfeil(ax, 113, 43, 113, 22)
    label(ax, 113, 33, "Kassenlauf\n(Sammelübergabe)", ha="center")
    
    box(ax, 98, 8, 30, 14, "4. Kasse übergeben\n\n• Status: UEBERGEBEN\n• Sammelvermerk im Journal\n• SEPA-XML / Kassenexport", "db", 8.2, True)

    # Storno-Zweig: Sauberer Container der beide Boxen umschließt
    container(ax, 2, 4, 38, 29, "Korrektur & Storno-Workflow", "kasten")
    box(ax, 4, 18.5, 34, 7.8, "Storno durch Sachbearbeiter\n(Status ANGELEGT oder BEREIT)", "neutral", 7.8)
    pfeil(ax, 21, 18.5, 21, 13.5)
    box(ax, 4, 6.5, 34, 7.8, "STORNIERT: Restbudget zurückgebucht\nZahlungsmonat frei für Neubuchung", "komp", 7.8, True)

    # Pfeil von ANGELEGT zum Storno
    pfeil(ax, 15, 43, 15, 27, farbe="#dc2626")
    
    # Pfeil von BEREIT zum Storno
    pfeil(ax, 98, 46, 88, 46, stil="-", farbe="#dc2626")
    pfeil(ax, 88, 46, 88, 33, stil="-", farbe="#dc2626")
    pfeil(ax, 88, 33, 40, 33, stil="-", farbe="#dc2626")
    pfeil(ax, 40, 33, 40, 23, farbe="#dc2626")
    label(ax, 64, 34.5, "Korrektur nach Freigabe", farbe="#dc2626", size=7.6)

    speichern(fig, "vier_augen_workflow", zielordner)


# -------------------------------------------------------------------------
# 4. Transaktionsarchitektur
# -------------------------------------------------------------------------
def gen_transaktionsarchitektur(zielordner):
    fig, ax = neue_fig("Architektur der Transaktionssteuerung (Ersatz TP-Monitor)",
                       "Schichtenarchitektur: Java 21 TxManager, TaskController, SagaEngine und Oracle ACID Kern", 7.0)

    # Schicht 1: Zugang
    box(ax, 10, 52, 110, 8.5, "Zugangsschicht: testsweet Simulator (Port 8095) & SDN.Leistung Web GUI (Port 8080)\nN parallele Arbeitsstränge / HTTP REST-API mit Bearer-Token", "web", 8.6, True)
    pfeil(ax, 65, 52, 65, 46)

    # Schicht 2: TaskController
    container(ax, 6, 32, 118, 15.5, "TaskController (Aufgabensteuerung & Stabilität)", "tx")
    box(ax, 10, 34, 34, 9.5, "Begrenzte Parallelität\nSemaphore & Warteschlange\n(Schutz vor Überlast)", "neutral", 8.0)
    box(ax, 48, 34, 34, 9.5, "Watchdog-Wachhund\nInterrupt bei Zeitüberschreitung\n(Sichert Rollback bei Hängern)", "tx", 8.0)
    box(ax, 86, 34, 34, 9.5, "JMX-Monitoring\nAktive Tasks, Durchsatz,\nSettle-Gate gegen Leaks", "neutral", 8.0)
    pfeil(ax, 65, 32, 65, 26)

    # Schicht 3: Transaktionskern
    container(ax, 6, 14, 118, 15.5, "TxManager & SagaEngine (Transaktionsführung)", "fach")
    box(ax, 10, 16, 34, 9.5, "TxManager.execute\nKurze Transaktionen mit\nCommit / Rollback-Garantie", "fach", 8.0, True)
    box(ax, 48, 16, 34, 9.5, "RetryPolicy (Deadlock)\nAuto-Retry bei ORA-00054,\nORA-00060, ORA-08177", "tx", 8.0, True)
    box(ax, 86, 16, 34, 9.5, "SagaEngine & Journal\nTX_SAGA / TX_SAGA_STEP\nIdempotente Kompensation", "neutral", 8.0)
    pfeil(ax, 65, 14, 65, 8.5)

    # Schicht 4: Oracle DBMS
    box(ax, 10, 1.5, 110, 8.5, "Oracle Database 23ai / 19c (Der transaktionale Kern)\nACID · FOR UPDATE SKIP LOCKED · Redo-Logging · Trigger & Versionierung gegen Lost Updates", "db", 8.8, True)

    speichern(fig, "transaktionsarchitektur", zielordner)




# -------------------------------------------------------------------------
# 5. Stresstest: zwei Wellen über dieselben User
# -------------------------------------------------------------------------
def gen_stresstest_zwei_wellen(zielordner):
    fig, ax = neue_fig("Stresstest-Szenario: Zwei Wellen über dieselben User",
                       "2000 User anlegen → Phasenbarriere → 2000 Nachbearbeitungen = 4000 Transaktionsblöcke unter Parallelität", 10.2)
    # y-Reichweite: 0 .. 102; Titel/Untertitel belegen oben ~101..94

    # ---------------- Welle 1 (oben links) ----------------
    container(ax, 4, 66, 58, 25, "WELLE 1 — STRESS-ANLEGEN (2000 User)")
    box(ax, 8, 73.5, 25, 10, "Vorgang anlegen\nAktenzeichen + Budget\n+ SEPA-Daten", "web", 7.9)
    box(ax, 36, 73.5, 22, 10, "SozialFall erfassen\nRBS + KdU + Miete\nFall-Version 1", "fach", 7.9)
    pfeil(ax, 33, 78.5, 36, 78.5)
    label(ax, 33, 69.5, "20 Stränge parallel · Commit je Schritt · noch keine Zahlung", size=7.4)

    # ---------------- Phasenbarriere (Mitte) ----------------
    ax.plot([2, 64], [62, 62], color="#4338ca", lw=1.4, ls=(0, (6, 4)), zorder=2)
    box(ax, 6, 53, 54, 7, "PHASENBARRIERE\nWelle 2 erst, wenn ALLE 2000 Anlagen committet sind", "tx", 8.0, True)
    label(ax, 33, 50, "Referenzlauf: letzte Anlage 22:02:36,892 · erste Nacharbeit 22:02:36,894", size=7.2)

    # ---------------- Welle 2 (unten links) ----------------
    container(ax, 4, 19, 58, 26, "WELLE 2 — STRESS-BEARBEITEN (2000 Nacharbeiten)")
    box(ax, 6.5, 23, 16.5, 11.5, "Wiederfinden\nper Aktenzeichen\n(Index-Lookup)", "neutral", 7.6)
    box(ax, 25.5, 23, 16.5, 11.5, "Miete ändern\noptimist. Locking\nVERSION 1 → 2", "tx", 7.6, True)
    box(ax, 44, 23, 16, 11.5, "Auszahlung\nanordnen\n(neue Miete)\n→ ANGELEGT", "fach", 7.6, True)
    pfeil(ax, 23, 28.7, 25.5, 28.7)
    pfeil(ax, 42, 28.7, 44, 28.7)
    label(ax, 33, 21.3, "zweiter Transaktionsblock je User", size=7.2)

    # ---------------- Oracle-Kern (rechts durchgehend) ----------------
    container(ax, 66, 19, 60, 72, "Oracle Database — der transaktionale Kern unter Stress")
    box(ax, 70, 76, 52, 9, "2000 Vorgangs- und Fallzeilen entstehen (Welle 1)\nACID-Commit je Schritt · Rollback im Fehlerfall", "db", 7.8)
    box(ax, 70, 60, 52, 12, "Zeilensperren FOR UPDATE — 20 Threads buchen parallel:\nWartezeit statt Chaos: max. Sperrwarte 1,19 s\nim Referenzlauf, 0 Deadlock-Retries nötig", "db", 7.7)
    box(ax, 70, 44, 52, 12.5, "VERSION-Spalte + Trigger — genau EINE Nachbearbeitung\nje User: alle 2000 Fälle am Ende in Version 2\n(kein Lost Update, keine Doppelanordnung)", "db", 7.7)
    box(ax, 70, 28, 52, 11, "Vier-Augen-Warteschlange — 2000 Zahlungen\nim Status ANGELEGT: keine Auszahlung ohne\nzweite Kraft", "db", 7.8)
    pfeil(ax, 62, 79, 70, 80)
    pfeil(ax, 62, 28.7, 70, 49)

    # ---------------- SQL-Verifikation (unten, volle Breite) ----------------
    box(ax, 4, 4, 122, 11.5,
        "SQL-ENDPRÜFUNG NACH DEM LAUF (je Auftrag + Aggregate im Gesamtreport)\n"
        "2000 Vorgänge aktiv · alle 2000 Fälle exakt Version 2 · 2000 Zahlungen ANGELEGT für 2026-10\n"
        "Beträge nach neuer Miete · Restbudget je User exakt gemindert · 4 Journaleinträge je User",
        "ok", 8.2, True)
    pfeil(ax, 95, 19, 95, 15.7)
    pfeil(ax, 33, 19, 33, 15.7)

    speichern(fig, "stresstest_zwei_wellen", zielordner)

if __name__ == "__main__":
    ziel = os.path.join(os.path.dirname(__file__), "src", "main", "resources", "sweet", "assets")
    print(f"Erzeuge Diagramme in: {ziel}")
    gen_simulator_zyklus(ziel)
    gen_bedarfsberechnung(ziel)
    gen_vier_augen_workflow(ziel)
    gen_transaktionsarchitektur(ziel)
    gen_stresstest_zwei_wellen(ziel)
    print("Alle 5 Diagramme erfolgreich generiert.")
