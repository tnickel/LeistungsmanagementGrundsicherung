# -*- coding: utf-8 -*-
"""Erzeugt die Fluss- und Ablaufdiagramme fuer das Dokument
"So wird ein SozialFall berechnet" (SDN.Leistung LMG).

Stil wie testsweet/generate_diagrams.py (kategorisierte Boxen, Entscheidung-
Rauten, Titelband). Schreibt bilder/*.png und bild_masse.json (Pixelmasse
fuer den DOCX-Generator).

Aufruf: python diagramme.py
"""
import json
import os
import struct
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.patches import FancyBboxPatch, FancyArrowPatch, Polygon

FARBE_TEXT = "#0f172a"
STIL = {
    "titel":   ("#1e3a8a", "#1e3a8a"),   # Dunkelblau (Ergebnis/Formel)
    "web":     ("#eff6ff", "#3b82f6"),   # Hellblau (Erfassung/Sachbearbeiter)
    "fach":    ("#f0fdf4", "#16a34a"),   # Hellgruen (Berechnung)
    "tx":      ("#e0e7ff", "#4338ca"),   # Indigo (Freigabe/Steuerung)
    "db":      ("#fef3c7", "#d97706"),   # Bernstein (Datenbank/Bestand)
    "ent":     ("#ffedd5", "#ea580c"),   # Orange (Entscheidung)
    "komp":    ("#fee2e2", "#dc2626"),   # Rot (kein Anspruch/Kuerzung)
    "ok":      ("#dcfce7", "#15803d"),   # Gruen (Erfolg)
    "neutral": ("#f8fafc", "#64748b"),   # Grau (Werte/Hinweise)
    "kasten":  ("#f1f5f9", "#cbd5e1"),   # Rahmen
}
PFEIL = "#334155"
JA = "#15803d"
NEIN = "#dc2626"

ARBEITSORDNER = os.path.dirname(os.path.abspath(__file__))
BILDER = os.path.join(ARBEITSORDNER, "bilder")


def box(ax, x, y, w, h, text, stil="neutral", size=8.0, bold=False):
    fc, ec = STIL.get(stil, STIL["neutral"])
    r = FancyBboxPatch((x, y), w, h, boxstyle="round,pad=0.3,rounding_size=1.0",
                       linewidth=1.3, facecolor=fc, edgecolor=ec, zorder=2)
    ax.add_patch(r)
    if text:
        col = "#ffffff" if stil == "titel" else FARBE_TEXT
        ax.text(x + w / 2, y + h / 2, text, ha="center", va="center", fontsize=size,
                color=col, zorder=3, fontweight="bold" if bold else "normal",
                linespacing=1.35)


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


def label(ax, x, y, text, farbe=PFEIL, size=7.6, ha="center"):
    ax.text(x, y, text, ha=ha, va="center", fontsize=size, color=farbe, zorder=4,
            linespacing=1.25,
            bbox=dict(boxstyle="round,pad=0.22", fc="white", ec="#cbd5e1", lw=0.75, alpha=0.96))


def entscheidung(ax, cx, cy, w, h, text, size=7.8):
    fc, ec = STIL["ent"]
    ax.add_patch(Polygon([(cx - w / 2, cy), (cx, cy + h / 2), (cx + w / 2, cy), (cx, cy - h / 2)],
                         closed=True, facecolor=fc, edgecolor=ec, linewidth=1.3, zorder=2))
    ax.text(cx, cy, text, ha="center", va="center", fontsize=size,
            color=FARBE_TEXT, zorder=3, linespacing=1.25, fontweight="bold")


def neue_fig(titel, untertitel, hoehe):
    fig, ax = plt.subplots(figsize=(12.0, hoehe), dpi=200)
    ax.set_xlim(0, 130)
    total_y = hoehe * 10.0
    ax.set_ylim(0, total_y)
    ax.axis("off")
    ax.fill_between([0, 130], total_y - 0.2, total_y - 0.5, color="#1e3a8a", zorder=1)
    ax.text(0, total_y - 1.8, titel, ha="left", va="top",
            fontsize=12.5, fontweight="bold", color="#0f172a")
    if untertitel:
        ax.text(0, total_y - 4.2, untertitel, ha="left", va="top",
                fontsize=8.6, color="#475569")
    return fig, ax


def png_masse(pfad):
    with open(pfad, "rb") as f:
        kopf = f.read(24)
    w, h = struct.unpack(">II", kopf[16:24])
    return {"w": int(w), "h": int(h)}


def speichern(fig, name):
    os.makedirs(BILDER, exist_ok=True)
    pfad = os.path.join(BILDER, f"{name}.png")
    fig.savefig(pfad, dpi=200, bbox_inches="tight", pad_inches=0.12, facecolor="white")
    plt.close(fig)
    print(f"Diagramm erzeugt: {pfad}")


# =========================================================================
# 1) Ablaufdiagramm: Vom Antrag bis zur Zahlung
# =========================================================================
def gen_ablauf_gesamt():
    fig, ax = neue_fig("Vom Antrag bis zum Geld auf dem Konto — wer macht was?",
                       "Die sieben Schritte der Sachbearbeitung in SDN.Leistung (Details im Text, Kapitel 2)", 8.2)

    # Obere Reihe (links nach rechts)
    box(ax, 2, 54, 28, 22,
        "1 · Antrag erfassen\nSachbearbeiter A\n\nVorgang anlegen:\nAktenzeichen, Budget,\nBankkonto\n\nSozialFall je Person:\nStufe, Merkmale,\nWohnkosten, Einkommen,\nVermögen",
        "web", 7.8, True)
    pfeil(ax, 30.3, 65, 32.7, 65)
    box(ax, 33, 54, 26, 22,
        "2 · System rechnet\n\nZahlbetrag sofort\nin der Maske sichtbar\n\nrechnet nach der\nKaskade (Kapitel 3)\n\njede Änderung\nrechnet neu",
        "fach", 7.8, True)
    pfeil(ax, 59.3, 65, 61.7, 65)
    box(ax, 62, 54, 27, 22,
        "3 · Bescheid erteilen\n\nZeitraum 1–12 Monate\n\nMonatsplan wird je Monat\ndurchgerechnet und\ngespeichert (Snapshot)\n\namtlicher Druck mit\nRechtsbehelfsbelehrung",
        "db", 7.8, True)
    pfeil(ax, 89.3, 65, 91.7, 65)
    box(ax, 92, 54, 36, 22,
        "4 · Auszahlung anordnen\nSachbearbeiter A\n\nZahlungsmonat und eigene\nVorgangsnummer\n\nIBAN-Prüfung, Budget-Prüfung\n\nStatus: ANGELEGT\n(noch kein Geld weg)",
        "web", 7.8, True)

    # Abgang nach unten
    pfeil(ax, 110, 54, 110, 48.7)
    label(ax, 110, 51.3, "weiter mit Schritt 5", ha="center")

    # Untere Reihe (rechts nach links)
    box(ax, 92, 26, 36, 22,
        "5 · Freigabe\nSachbearbeiter B\n\nVier-Augen-Prinzip:\nFreigebender darf nicht\nder Erfasser sein\n\nStatus: BEREIT",
        "tx", 7.8, True)
    pfeil(ax, 91.7, 37, 89.3, 37)
    box(ax, 62, 26, 27, 22,
        "6 · Kassenlauf (Admin)\n\nalle BEREIT-Zahlungen\nin einem Durchgang\nauf ÜBERGEBEN\n\nSammelvermerk mit Anzahl\nund Summe im Journal",
        "db", 7.8, True)
    pfeil(ax, 61.7, 37, 59.3, 37)
    box(ax, 29, 26, 30, 22,
        "7 · Zahlung geht raus\n\nÜbergabe an Kasse /\nZahlungsdienst\n\njeder Schritt steht\nrevisionssicher im\nBuchungsjournal",
        "ok", 7.8, True)

    # Sicherungen unten
    pfeil(ax, 44, 26, 44, 18.7)
    box(ax, 2, 6, 126, 12.5,
        "Sicherungen unterwegs:\n\n"
        "• Doppelklick ist harmlos — dieselbe Vorgangsnummer ergibt keine zweite Zahlung (Idempotenz)\n"
        "• Storno bis zur Übergabe möglich; Restbudget und etwaiger Vermögenseinsatz werden zurückgebucht\n"
        "• keine Auszahlung ohne zweite Kraft, und mehr als das Restbudget geht nicht raus",
        "neutral", 7.8)

    speichern(fig, "ablauf_antrag_bis_zahlung")


# =========================================================================
# 2) Flussdiagramm: Die Berechnung im Überblick
# =========================================================================
def gen_fluss_gesamt():
    fig, ax = neue_fig("Die Berechnung im Überblick — drei Rechenschritte und eine Formel",
                       "Links: was erfasst wird · Mitte: die drei Schritte · rechts unten: das Ergebnis (Details im Text, Kapitel 3)", 9.0)

    # Links: Eingabedaten
    container(ax, 2, 18, 33, 66, "Was erfasst wird (Eingaben)")
    box(ax, 4, 50, 29, 28,
        "Je Person (SozialFall)\n\n• Regelbedarfsstufe 1–6\n• Merkzeichen G?\n• Schwanger ab 13. SSW?\n• Kinder im Haushalt\n• Wohnkosten: Miete,\n  Betriebskosten, Heizung\n• Einkommen: Rente,\n  Erwerb, Absetzbeträge",
        "web", 7.4)
    box(ax, 4, 20, 29, 26,
        "Je Bedarfsgemeinschaft\n\n• Vermögen gesamt\n• Grenze für Wohnkosten\n  (örtlich, Standard: aus)\n• Standard-Bankkonto\n  (IBAN/BIC)\n• Startbudget",
        "db", 7.4)

    # Mitte: drei Rechenschritte
    box(ax, 41, 69, 47, 13,
        "Schritt A — Bedarf je Person   (Kapitel 4–6)\nRegelbedarf + Mehrbedarfe (§ 30)\n+ anerkannte Wohnkosten (§ 42a/§ 35)\n= Gesamtbedarf; Summe über alle Personen",
        "fach", 7.8, True)
    pfeil(ax, 64.5, 69, 64.5, 63.6)
    label(ax, 77, 66.3, "davon abziehen")
    box(ax, 41, 50, 47, 13,
        "Schritt B — Einkommen je Person   (Kapitel 7)\nRente zählt voll; vom Erwerb bleiben\nAbsetzbeträge und Freibetrag übrig\n= anrechenbares Einkommen (nie unter 0)",
        "tx", 7.8, True)
    pfeil(ax, 64.5, 50, 64.5, 44.6)
    label(ax, 77, 47.3, "davon abziehen")
    box(ax, 41, 31, 47, 13,
        "Schritt C — Vermögen der BG   (Kapitel 8)\nVermögen − Schonvermögen\n(10.000 € je erwachsene Person)\n= verwertbares Vermögen",
        "db", 7.8, True)

    # Eingaben -> Schritt A
    pfeil(ax, 35, 58, 41, 71, style="arc3,rad=-0.15")

    # Formel
    pfeil(ax, 64.5, 31, 64.5, 26.6)
    box(ax, 41, 14, 47, 12,
        "Zahlbetrag (pro Monat) =\nGesamtbedarf − anrechenbares Einkommen\n− verwertbares Vermögen",
        "titel", 8.2, True)

    # Entscheidung
    pfeil(ax, 88, 20, 91.5, 20)
    entscheidung(ax, 99, 20, 15, 10, "Zahlbetrag\n> 0 ?")

    pfeil(ax, 99, 25, 99, 36)
    label(ax, 101.5, 30.5, "ja", farbe=JA)
    box(ax, 90, 36, 38, 11,
        "Bewilligung!\n• Bescheid mit Monatsplan\n• Auszahlung monatlich möglich",
        "ok", 7.8, True)

    pfeil(ax, 91.5, 20, 84, 8, style="arc3,rad=0.18")
    label(ax, 90.5, 14.5, "nein", farbe=NEIN)
    box(ax, 41, 2, 43, 10,
        "KEIN ANSPRUCH — Einkommen und\nVermögen decken den Bedarf;\nkeine Zahlung wird angeordnet",
        "komp", 7.8, True)

    speichern(fig, "fluss_berechnung_gesamt")


# =========================================================================
# 3) Flussdiagramm: Regelbedarf und Mehrbedarfe
# =========================================================================
def gen_fluss_mehrbedarf():
    fig, ax = neue_fig("Schritt 1 + 2: Regelbedarf und Mehrbedarfe (§§ 28, 30 SGB XII)",
                       "Von oben nach unten lesen; jedes „ja“ fügt einen Zuschlag hinzu (Details im Text, Kapitel 4 und 5)", 9.6)

    box(ax, 14, 82, 36, 8,
        "Start: Regelbedarf der Person\nnach Stufe — z. B. Stufe 1: 563,00 €",
        "web", 7.8, True)
    pfeil(ax, 32, 82, 32, 77.2)
    label(ax, 25.5, 79.6, "prüfen:")

    entscheidung(ax, 32, 71, 22, 11, "Merkzeichen G\nvorhanden?")
    pfeil(ax, 43, 71, 58, 71)
    label(ax, 50.5, 73.4, "ja", farbe=JA)
    box(ax, 58, 65, 44, 12,
        "+ 17 % vom eigenen Regelbedarf (§ 30 Abs. 1)\nStufe 1: + 95,71 €  ·  Stufe 2: + 86,02 €\nfür Wege im Freien, die wegen der Behinderung\nnur mit Schwierigkeiten möglich sind",
        "fach", 7.6)
    pfeil(ax, 32, 65.5, 32, 59.7)
    label(ax, 25.5, 62.6, "nein")

    entscheidung(ax, 32, 53.5, 22, 11, "Schwanger,\nab der 13. Woche?")
    pfeil(ax, 43, 53.5, 58, 53.5)
    label(ax, 50.5, 55.9, "ja", farbe=JA)
    box(ax, 58, 47.5, 44, 12,
        "+ 17 % vom eigenen Regelbedarf (§ 30 Abs. 2)\nStufe 1: + 95,71 €\nEntbindungsmonat wird erfasst: ab dem Folgemonat\nentfällt dieser Zuschlag automatisch",
        "fach", 7.6)
    pfeil(ax, 32, 48, 32, 42.2)
    label(ax, 25.5, 45.1, "nein")

    entscheidung(ax, 32, 36, 24, 12, "Alleinerziehend mit\nminderjährigen Kindern?")
    pfeil(ax, 44, 36, 58, 36)
    label(ax, 51, 38.4, "ja", farbe=JA)
    box(ax, 58, 27, 44, 18,
        "Basis ist IMMER die Stufe 1 (563 €):\n• Kind unter 7 oder 2–3 Kinder unter 16:\n  + 36 % = 202,68 €\n• sonst + 12 % je Kind = 67,56 €\n• Höchstgrenze: 60 % = 337,80 € (§ 30 Abs. 3)",
        "fach", 7.6)
    pfeil(ax, 32, 30, 32, 23.7)
    label(ax, 25.5, 26.8, "nein")

    box(ax, 12, 15, 40, 8,
        "Zwischensumme der Zuschläge\n(bisher ohne Warmwasser)",
        "neutral", 7.8, True)
    pfeil(ax, 52, 19, 56.3, 19)

    box(ax, 56.5, 15, 36, 8,
        "Deckel (§ 30 Abs. 6): nie höher als der\neigene Regelbedarf — sonst wird die\nSumme darauf gekappt",
        "neutral", 7.5, True)
    pfeil(ax, 92.5, 19, 96.3, 19)

    box(ax, 96.5, 15, 31.5, 8,
        "zuletzt: bei eigenem Boiler\n+ 0,8 % – 2,3 % je Stufe\n(§ 30 Abs. 7, z. B. Stufe 1: + 12,95 €)",
        "fach", 7.3, True)

    pfeil(ax, 74.5, 15, 74.5, 11.7)
    pfeil(ax, 112, 15, 112, 11.7, stil="-")
    pfeil(ax, 112, 11.7, 108, 11.7)
    box(ax, 36, 3, 76, 8.5,
        "Mehrbedarf gesamt = gekappte Summe der Zuschläge\n+ Warmwasser-Zuschlag (falls im Haushalt ein eigener Boiler steht)",
        "titel", 8.2, True)

    speichern(fig, "fluss_mehrbedarfe")


# =========================================================================
# 4) Flussdiagramm: Wohnkosten (KdU)
# =========================================================================
def gen_fluss_kdu():
    fig, ax = neue_fig("Schritt 3: Wohnkosten anerkennen — Kaltmiete, Betriebskosten, Heizung (§§ 42a, 35 SGB XII)",
                       "Wie aus erfassten Wohnkosten „anerkannte Wohnkosten“ werden (Details im Text, Kapitel 6)", 10.0)

    box(ax, 24, 84, 40, 9,
        "Erfasst je Person:\nKaltmiete + kalte Betriebskosten\n+ Heizkosten",
        "web", 7.8, True)
    pfeil(ax, 44, 84, 44, 81.2)
    label(ax, 46.5, 82.6, "zuerst die Warmwasser-Frage", ha="left")

    entscheidung(ax, 44, 74, 30, 14, "Warmwasser selbst\nerzeugt?\n(eigener Boiler)")
    pfeil(ax, 59, 74, 66, 74)
    label(ax, 62.5, 76.6, "ja — dezentral", farbe=JA)
    box(ax, 66, 68, 44, 12,
        "Kein Abzug von den Heizkosten —\nstattdessen der Warmwasser-Mehrbedarf\naus Kapitel 5 (0,8 % – 2,3 %).\n(Doppelter Abzug würde doppelt kürzen.)",
        "fach", 7.5)

    pfeil(ax, 44, 67, 44, 50.7)
    label(ax, 46.5, 62, "nein — zentral", ha="left")
    box(ax, 20, 41.5, 48, 9,
        "Örtlicher Abzug möglich (Standard: aus):\nein Teil der Heizkosten gilt als Warmwasser-Anteil\nund wird abgezogen (§ 35 Abs. 5)",
        "neutral", 7.5)
    pfeil(ax, 44, 41.5, 44, 36.7)

    box(ax, 20, 27.5, 48, 9,
        "Wohnkosten der ganzen Bedarfsgemeinschaft\n= Summe über alle Personen",
        "db", 7.8, True)

    # dezentraler Zweig laeuft rechts am linken Pfad vorbei und muendet wieder
    pfeil(ax, 88, 68, 88, 32, stil="-")
    pfeil(ax, 88, 32, 68.3, 32)

    pfeil(ax, 44, 27.5, 44, 22.7)

    entscheidung(ax, 44, 14, 34, 16, "Angemessenheitsgrenze\ngesetzt UND überschritten?\n(§ 35 Abs. 7)")
    pfeil(ax, 61, 14, 68, 14)
    label(ax, 64.5, 16.6, "ja", farbe=NEIN)
    box(ax, 68, 7.5, 60, 13,
        "Nur bis zur Grenze anerkennen — der Rest bleibt Eigenanteil.\nDie Grenze gilt für die GANZE Bedarfsgemeinschaft; bei mehreren\nPersonen teilt sie sich in Erfassungs-Reihenfolge auf.\nDer Bescheid kennzeichnet den gekürzten Betrag mit „*“.",
        "komp", 7.5)

    pfeil(ax, 44, 6, 44, 4.4, stil="-")
    pfeil(ax, 44, 4.4, 68, 4.4)
    label(ax, 55, 6.4, "nein")
    box(ax, 68, 0.5, 60, 6,
        "Volle Höhe wird anerkannt — die gesamten\nWohnkosten zählen als Bedarf.",
        "ok", 7.6, True)

    speichern(fig, "fluss_wohnkosten")


# =========================================================================
# 5) Flussdiagramm: Einkommen anrechnen
# =========================================================================
def gen_fluss_einkommen():
    fig, ax = neue_fig("Schritt 4: Einkommen anrechnen — Rente und Erwerb (§ 82 SGB XII)",
                       "Was vom Einkommen einer Person auf die Grundsicherung angerechnet wird (Details im Text, Kapitel 7)", 9.6)

    box(ax, 38, 78, 44, 8,
        "Einkommen der Person:\nRente (zählt voll)\n+ Erwerbseinkommen (brutto)",
        "web", 7.8, True)
    pfeil(ax, 60, 78, 60, 73.2)
    label(ax, 69, 75.6, "minus")

    box(ax, 34, 64, 52, 9,
        "Absetzbeträge (§ 82 Abs. 2):\nSteuern, Pflichtbeiträge zur Sozialversicherung,\nVersicherungen, Ausgaben für die Erwerbstätigkeit",
        "fach", 7.5, True)
    pfeil(ax, 60, 64, 60, 58.7)
    label(ax, 69, 61.3, "minus")

    entscheidung(ax, 60, 50.5, 26, 14, "Wird die Person\naus Erwerb\nbezahlt?")
    pfeil(ax, 73, 50.5, 80, 50.5)
    label(ax, 76.5, 53.1, "ja", farbe=JA)
    box(ax, 80, 44.5, 48, 12,
        "Erwerbsfreibetrag (§ 82 Abs. 3):\n30 % vom Brutto-Verdienst — höchstens\n281,50 € (= 50 % der Stufe 1, 2026)",
        "fach", 7.6, True)
    pfeil(ax, 47, 50.5, 42, 50.5)
    label(ax, 44.5, 53.1, "nein")
    box(ax, 4, 44.5, 38, 12,
        "nein: Freibetrag = 0\n(eine Rente bekommt\nkeinen Freibetrag)",
        "neutral", 7.6, True)

    # beide Zweige laufen zurueck in den Hauptstrang
    pfeil(ax, 104, 44.5, 104, 41.5, stil="-")
    pfeil(ax, 104, 41.5, 62.3, 41.5)
    pfeil(ax, 23, 44.5, 23, 41.5, stil="-")
    pfeil(ax, 23, 41.5, 57.7, 41.5)

    pfeil(ax, 60, 43.5, 60, 37.7)

    entscheidung(ax, 60, 31, 24, 13, "Ergebnis\nunter 0?")
    pfeil(ax, 72, 31, 80, 31)
    label(ax, 76, 33.6, "ja", farbe=NEIN)
    box(ax, 80, 25, 48, 12,
        "auf 0 setzen — das System holt\nnie Geld zurück, das die Person\nnicht hat",
        "komp", 7.6, True)
    pfeil(ax, 104, 25, 104, 22, stil="-")
    pfeil(ax, 104, 22, 62.3, 22)

    pfeil(ax, 60, 24.5, 60, 21.7)
    label(ax, 67, 23.1, "nein")
    box(ax, 30, 12, 60, 9,
        "anrechenbares Einkommen —\nmindert den Zahlbetrag Euro für Euro\n(Summe über alle Personen der BG)",
        "titel", 8.0, True)

    speichern(fig, "fluss_einkommen")


# =========================================================================
# 6) Flussdiagramm: Vermoegen
# =========================================================================
def gen_fluss_vermoegen():
    fig, ax = neue_fig("Schritt 5: Vermögen prüfen — Schonvermögen und Verbrauch (§§ 43, 90 SGB XII)",
                       "Vermögen wird nicht auf einmal genommen, sondern verbraucht sich mit der ersten Auszahlung (Details: Kapitel 8)", 8.8)

    box(ax, 17, 70, 38, 9,
        "Vermögen der Bedarfs-\ngemeinschaft (Sparbuch,\nGirokonto, Festgeld, …)",
        "db", 7.8, True)
    pfeil(ax, 36, 70, 36, 65.2)

    box(ax, 13, 52, 46, 13,
        "Schonvermögen bleibt geschützt (§ 90 Abs. 2 Nr. 9):\n10.000 € je Person in Stufe 1–3\n+ 500 € je Person in Stufe 4–6\nmindestens 10.000 €",
        "fach", 7.6, True)
    pfeil(ax, 36, 52, 36, 42.7)

    entscheidung(ax, 36, 33, 30, 18, "Vermögen größer\nals das\nSchonvermögen?")
    pfeil(ax, 51, 33, 56, 33)
    label(ax, 53.5, 35.6, "ja", farbe=JA)
    box(ax, 56, 27, 36, 12,
        "verwertbares Vermögen\n= der Überschuss — mindert im\nersten Auszahlungsmonat den\nZahlbetrag (§ 43 Abs. 1)",
        "neutral", 7.4, True)
    pfeil(ax, 92, 33, 96, 33)
    box(ax, 96, 22, 32, 17,
        "Mit der ersten Auszahlung\nverbraucht sich der Überschuss:\nder Bestand im System sinkt\nauf das Schonvermögen.\n\nAb dem Folgemonat:\nwieder volle Zahlung\nohne Vermögensabzug",
        "fach", 7.4)

    pfeil(ax, 36, 24, 36, 19.7)
    label(ax, 40, 21.8, "nein")
    box(ax, 10, 8, 52, 11,
        "nein: nichts ist verwertbar — die volle\nLeistung fließt, das Vermögen bleibt\nunberührt",
        "ok", 7.6, True)

    pfeil(ax, 112, 22, 112, 7.2)
    box(ax, 10, 0.5, 118, 6.5,
        "Storno: Wird eine Zahlung mit Vermögenseinsatz storniert, wird genau der dokumentierte Einsatz zurückgebucht —\nder Bestand stimmt danach wieder.",
        "neutral", 7.5)

    speichern(fig, "fluss_vermoegen")


# =========================================================================
# 7) Zeitstrahl: Der Monatsplan
# =========================================================================
def gen_monatsplan():
    fig, ax = neue_fig("Der Bewilligungsplan — der Bescheid rechnet jeden Monat einzeln",
                       "Beispiel: Frau V., Stufe 1, Bedarf 863 €/Monat, Vermögen 10.500 €, Bescheid Januar–Juni 2026 (Details: Kapitel 9)", 6.4)

    box(ax, 24, 50, 82, 7,
        "Bewilligungsbescheid: Zeitraum 2026-01 bis 2026-06 — der gerechnete\nMonatsplan wird im Bescheid gespeichert (Snapshot)",
        "titel", 7.9, True)
    pfeil(ax, 65, 50, 65, 45.7)

    monate = [
        ("Januar", "363,00 €", "− 500,00 €\nVermögens-\neinsatz", "ent"),
        ("Februar", "863,00 €", "Vermögen jetzt\nim Schon-\nvermögen", "ok"),
        ("März", "863,00 €", "wie Februar", "ok"),
        ("April", "863,00 €", "wie Februar", "ok"),
        ("Mai", "863,00 €", "wie Februar", "ok"),
        ("Juni", "863,00 €", "letzter Monat\ndes Bescheids", "ok"),
    ]
    x = 2.5
    for i, (name, betrag, note, stil) in enumerate(monate):
        box(ax, x, 32, 18, 13, f"{name}\n\n{betrag}", stil, 8.0, True)
        box(ax, x - 0.7, 19, 19.4, 9.5, note, "neutral", 6.9)
        pfeil(ax, x + 9, 32, x + 9, 29, stil="-")
        if i < len(monate) - 1:
            pfeil(ax, x + 18.3, 38.5, x + 20.7, 38.5)
        x += 21.4

    box(ax, 2.5, 4, 125, 10,
        "Der Plan kennt Sonderfälle:\n"
        "• Zum Jahreswechsel rechnet er je Monat mit dem dann gültigen Regelbedarf (neue Zeile in der Tabelle SATZSTAND)\n"
        "• Endet eine Schwangerschaft, entfällt der Mehrbedarf ab dem Folgemonat der Entbindung automatisch\n"
        "• Ändert sich die Lebenssituation, kommt ein neuer Bescheid — der alte bleibt als Stand von damals gedruckt nachlesbar",
        "neutral", 7.5)

    speichern(fig, "monatsplan_zeitstrahl")


if __name__ == "__main__":
    gen_ablauf_gesamt()
    gen_fluss_gesamt()
    gen_fluss_mehrbedarf()
    gen_fluss_kdu()
    gen_fluss_einkommen()
    gen_fluss_vermoegen()
    gen_monatsplan()

    masse = {}
    for datei in sorted(os.listdir(BILDER)):
        if datei.endswith(".png"):
            masse[datei] = png_masse(os.path.join(BILDER, datei))
    with open(os.path.join(ARBEITSORDNER, "bild_masse.json"), "w", encoding="utf-8") as f:
        json.dump(masse, f, indent=1)
    print(f"bild_masse.json: {len(masse)} Bilder")
