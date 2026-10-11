# -*- coding: utf-8 -*-
"""Erzeugt die Flussdiagramme für das SDN.Leistung-Benutzerhandbuch (PNG, 160 dpi).

Aufruf:  python diagramme_erzeugen.py   (im Ordner doc/manual-bilder)
Koordinatensystem: x 0-130, y individuell; 1 Einheit ≙ 6,4 pt.
"""
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.patches import FancyBboxPatch, FancyArrowPatch, Polygon

FARBE_TEXT = "#1e293b"
STIL = {
    "web":   ("#eef2f7", "#475569"),
    "fach":  ("#e6f4f1", "#0f766e"),
    "tx":    ("#e3ecfb", "#1d4ed8"),
    "db":    ("#fdf3e3", "#b45309"),
    "ent":   ("#fff2df", "#c2410c"),
    "komp":  ("#fdeaea", "#b91c1c"),
    "ok":    ("#e7f6ec", "#15803d"),
    "neutral": ("#f1f5f9", "#64748b"),
}
PFEIL = "#334155"


def box(ax, x, y, w, h, text, stil="neutral", size=9.5, bold=False):
    fc, ec = STIL.get(stil, STIL["neutral"])
    r = FancyBboxPatch((x, y), w, h, boxstyle="round,pad=0.3,rounding_size=1.0",
                       linewidth=1.4, facecolor=fc, edgecolor=ec, zorder=2)
    ax.add_patch(r)
    if text:
        ax.text(x + w / 2, y + h / 2, text, ha="center", va="center", fontsize=size,
                color=FARBE_TEXT, zorder=3,
                fontweight="bold" if bold else "normal", linespacing=1.4)


def schicht(ax, x, y, w, h, titel, zeilen, stil):
    """Rahmen mit fett gedrucktem Titel und Inhaltszeilen."""
    fc, ec = STIL.get(stil, STIL["neutral"])
    ax.add_patch(FancyBboxPatch((x, y), w, h, boxstyle="round,pad=0.3,rounding_size=1.0",
                                linewidth=1.5, facecolor=fc, edgecolor=ec, zorder=2))
    ax.text(x + w / 2, y + h - 1.6, titel, ha="center", va="center", fontsize=10.5,
            fontweight="bold", color=FARBE_TEXT, zorder=3)
    ax.text(x + w / 2, y + (h - 3.2) / 2, zeilen, ha="center", va="center", fontsize=9.3,
            color=FARBE_TEXT, zorder=3, linespacing=1.5)


def pfeil(ax, x1, y1, x2, y2, farbe=PFEIL, stil="-|>", style="arc3,rad=0", lw=1.4):
    ax.add_patch(FancyArrowPatch((x1, y1), (x2, y2), arrowstyle=stil, mutation_scale=14,
                                 linewidth=lw, color=farbe, connectionstyle=style, zorder=1))


def label(ax, x, y, text, farbe=PFEIL, size=8.3, ha="center"):
    ax.text(x, y, text, ha=ha, va="center", fontsize=size, color=farbe, zorder=4,
            linespacing=1.35,
            bbox=dict(boxstyle="round,pad=0.22", fc="white", ec="none", alpha=0.92))


def entscheidung(ax, cx, cy, w, h, text, size=9.3):
    fc, ec = STIL["ent"]
    ax.add_patch(Polygon([(cx - w / 2, cy), (cx, cy + h / 2), (cx + w / 2, cy), (cx, cy - h / 2)],
                         closed=True, facecolor=fc, edgecolor=ec, linewidth=1.4, zorder=2))
    ax.text(cx, cy, text, ha="center", va="center", fontsize=size,
            color=FARBE_TEXT, zorder=3, linespacing=1.3)


def neue_fig(titel, hoehe):
    fig, ax = plt.subplots(figsize=(11.6, hoehe))
    ax.set_xlim(0, 130)
    ax.set_ylim(0, hoehe * 11.2)
    ax.axis("off")
    ax.text(0, hoehe * 11.2 - 0.5, titel, ha="left", va="top",
            fontsize=14.5, fontweight="bold", color=FARBE_TEXT)
    return fig, ax


def speichern(fig, name):
    fig.savefig(name, dpi=160, bbox_inches="tight", facecolor="white")
    plt.close(fig)
    print("erzeugt:", name)


# ---------------------------------------------------------------- 1 Architektur
def d01():
    fig, ax = neue_fig("Bausteine und Schichten der Lösung", 7.6)   # ylim 85
    box(ax, 35, 74, 60, 7, "Browser — SDN.Leistung Oberfläche\n(HTML/CSS/JavaScript, Widget-Dashboard)", "web", 10)
    pfeil(ax, 65, 74, 65, 68.5)
    label(ax, 78, 71.2, "HTTP/JSON · Bearer-Token je Anfrage")

    schicht(ax, 12, 57, 106, 11.5, "Webschicht — JDK-HttpServer (Port 8080)",
            "LmgServer (REST-API, Berechtigungsprüfung je Anfrage) · Json (eigener Parser/Writer)\n"
            "Benutzerverwaltung (Konten, Rollen admin/sachbearbeiter, Sitzungs-Token im Speicher)", "web")
    pfeil(ax, 65, 57, 65, 51.5)

    schicht(ax, 12, 40, 106, 11.5, "Fachschicht — Leistungsmanagement Grundsicherung (SGB XII)",
            "SozialLeistungsService (Auszahlung, Freigabe, Kassenlauf, Storno) · Leistungsberechnung (rein)\n"
            "SepaValidator (ISO 13616, Modulo 97) · BescheidGenerator (§ 37 SGB X) · DAOs (PreparedStatements)", "fach")
    pfeil(ax, 65, 40, 65, 34.5)
    label(ax, 87, 37.2, "jede API-Anfrage läuft als Task")

    schicht(ax, 12, 21, 106, 13.5, "Transaktionssteuerung (Kernbibliothek de.allgemein.tx)",
            "TaskController (begrenzte Parallelität, Warteschlange, Zeitlimit-Wachhund, Statistik)\n"
            "TxManager (Begin/Commit/Rollback) mit RetryPolicy (ORA-00054/00060/08177) · SagaEngine\n"
            "mit Journal TX_SAGA / TX_SAGA_STEP · UcpConnectionPool · TxAuditLog · JMX-Monitoring", "tx")
    pfeil(ax, 65, 21, 65, 15.5)
    label(ax, 73, 18.2, "JDBC")

    box(ax, 30, 7, 70, 8, "Oracle DBMS trägt den transaktionalen Kern:\nACID · Sperrverwaltung · Redo-Logging · Recovery", "db", 10)
    label(ax, 65, 3.2, "Die Eigenentwicklung steuert nur — die Datenbank garantiert die Atomarität.", size=9)
    speichern(fig, "d01_architektur.png")


# ---------------------------------------------------------------- 2 kurze Transaktion
def d02():
    fig, ax = neue_fig("Ablauf einer kurzen Transaktion (TxManager.execute)", 7.2)  # ylim 81
    cx, rx = 38, 96

    box(ax, cx - 22, 69, 44, 5.5, "Aufruf: TxManager.execute(facharbeit)", "tx", 10, True)
    pfeil(ax, cx, 69, cx, 67.5)
    box(ax, cx - 22, 62, 44, 5.5, "Verbindung aus dem Pool holen,\nautoCommit aus, Isolation setzen", "neutral", 9.3)
    pfeil(ax, cx, 62, cx, 57.5)
    box(ax, cx - 22, 52, 44, 5.5, "Facharbeit ausführen\n(SQL auf der Transaktionsverbindung)", "fach", 9.3)
    pfeil(ax, cx, 52, cx, 47.5)
    entscheidung(ax, cx, 43, 30, 9, "Exception\naufgetreten?")
    pfeil(ax, cx, 38.5, cx, 33.5)
    label(ax, cx - 4.5, 36, "nein")
    box(ax, cx - 22, 28, 44, 5.5, "COMMIT — Änderungen sind\nrevisionssicher übernommen", "ok", 9.8, True)
    pfeil(ax, cx, 28, cx, 23.5)
    box(ax, cx - 22, 18, 44, 5.5, "Verbindung in jedem Fall zurück\nin den Pool (Auto-Commit zurückgesetzt)", "neutral", 9.3)

    # Fehlerpfad
    pfeil(ax, cx + 15, 43, rx - 19, 43)
    label(ax, cx + 19, 44.6, "ja")
    box(ax, rx - 19, 55, 38, 5.5, "ROLLBACK — nichts bleibt\nhalb gebucht (atomar)", "komp", 9.3)
    pfeil(ax, rx, 55, rx, 50.5)
    entscheidung(ax, rx, 46, 40, 9, "Wiederholbar?\nORA-00054 · 00060 · 08177")
    pfeil(ax, rx, 41.5, rx, 36.5)
    label(ax, rx + 4, 39, "ja")
    box(ax, rx - 19, 31, 38, 5.5, "Backoff: 200 → 400 → 800 ms\n(je + Zufallsanteil bis 50 %)", "tx", 9.3)
    pfeil(ax, rx, 31, rx, 27.5)
    label(ax, rx + 4.5, 29.2, "ja (bis 3 Versuche)")
    box(ax, rx - 19, 22, 38, 5.5, "ganze Transaktion erneut\nausführen (Wiederholung)", "tx", 9.3)

    pfeil(ax, rx - 19, 24.8, 66, 24.8, stil="-")
    pfeil(ax, 66, 24.8, 66, 49, stil="-")
    pfeil(ax, 66, 49, 16, 49, stil="-")
    pfeil(ax, 16, 49, 16, 52)
    label(ax, 73, 39.5, "Transaktion\nerneut versuchen", farbe="#1d4ed8", size=8)

    pfeil(ax, rx + 20, 46, 124, 46, stil="-")
    pfeil(ax, 124, 46, 124, 24.7, stil="-")
    pfeil(ax, 124, 24.7, rx + 19, 24.7)
    label(ax, 112, 47.8, "nein")
    box(ax, rx - 19, 12, 38, 5.5, "Abbruch: TxException mit Ursache\nan den Aufrufer (kein Retry)", "komp", 9.3)
    pfeil(ax, rx, 22, rx, 17.5)
    label(ax, rx + 3.5, 19.8, "nach 3. Fehlversuch oder Fachfehler")
    speichern(fig, "d02_kurze_transaktion.png")


# ---------------------------------------------------------------- 3 Saga
def d03():
    fig, ax = neue_fig("Antrags-Saga: vier Schritte mit Rückwärtskompensation", 5.6)  # ylim 63
    xs = [2, 34.5, 67, 99.5]
    w = 28
    schritte = [
        ("1 · Vorgang erfassen", "Bedarfsgemeinschaft +\nSozialFall anlegen"),
        ("2 · Bedarfsprüfung", "SGB-XII-Berechnung;\nZahlbetrag ≤ 0 → Abbruch"),
        ("3 · Bescheid erteilen", "Bewilligungsbescheid\n(§ 37 SGB X) anlegen"),
        ("4 · Erstauszahlung", "Zahlung BEREIT +\nRestbudget mindern"),
    ]
    komp = [
        "Kompensation:\nVorgang löschen",
        "Kompensation:\nJournaleintrag (nichts\nzu zurückrollen)",
        "Kompensation:\nBescheid aufheben\n(AUFGEHOBEN)",
        "Kompensation:\nZahlung stornieren,\nBudget zurückbuchen",
    ]
    for i, (t, u) in enumerate(schritte):
        box(ax, xs[i], 45, w, 9, f"{t}\n{u}", "tx", 9.2)
        box(ax, xs[i], 27, w, 8.5, komp[i], "komp", 9.0)
        pfeil(ax, xs[i] + w / 2, 45, xs[i] + w / 2, 35.5, farbe="#b91c1c")
        label(ax, xs[i] + w / 2 + 6.5, 40.2, "bei Fehler", farbe="#b91c1c", size=8)
        if i < 3:
            pfeil(ax, xs[i] + w, 49.5, xs[i + 1], 49.5, farbe="#15803d")
            label(ax, xs[i] + w + 2.3, 52.3, "DONE", farbe="#15803d", size=7.6)

    label(ax, 65, 56.6, "Jeder Schritt läuft als eigene kurze Transaktion (DONE) und wird sofort im Saga-Journal vermerkt.",
          farbe="#15803d", size=8.8)

    pfeil(ax, 127.5, 22, 2.5, 22, farbe="#b91c1c", lw=1.7)
    label(ax, 65, 24.2, "Fehler in einem Schritt → Saga FAILED, danach Rückwärtskompensation "
                        "der bereits gelaufenen Schritte in umgekehrter Reihenfolge", farbe="#b91c1c", size=8.8)
    box(ax, 2, 6, 41, 10, "Journal: TX_SAGA + TX_SAGA_STEP\nJede Schreiboperation committet sofort —\n"
        "das Journal überlebt einen Absturz.", "neutral", 8.8)
    box(ax, 44.5, 6, 41, 10, "Zustände der Saga:\nRUNNING → COMPLETED bzw.\nFAILED → COMPENSATING → COMPENSATED", "neutral", 8.8)
    box(ax, 87, 6, 41, 10, "Wiederanlauf recover() beim Serverstart:\nHalb fertige Sagas werden sauber beendet —\nKompensationen sind idempotent.", "neutral", 8.8)
    speichern(fig, "d03_saga.png")


# ---------------------------------------------------------------- 4 Zahlungsworkflow
def d04():
    fig, ax = neue_fig("Zahlungsworkflow: Vier-Augen-Prinzip und Statusweg", 5.8)  # ylim 65
    box(ax, 2, 38, 26, 10, "Auszahlung anordnen\n(Sachbearbeiter; eine kurze\nTransaktion mit Idempotenz,\nSEPA- und Budgetprüfung)", "fach", 8.8)
    box(ax, 46, 38, 26, 10, "ANGELEGT\n„angelegt“ — erfasst,\nwartet auf Freigabe durch\nden Zweitsachbearbeiter", "ent", 9.0, True)
    box(ax, 104, 38, 26, 10, "BEREIT\n„bereit“ — für den\nKassenlauf vorgemerkt", "ok", 9.0, True)
    box(ax, 104, 12, 26, 8, "ÜBERGEBEN\nfachliche Übergabe an\nKasse/Zahlungsdienst", "tx", 9.0, True)
    box(ax, 46, 3, 26, 8, "STORNIERT\nRestbudget zurückgebucht\n(Journaleintrag)", "komp", 9.0, True)

    pfeil(ax, 28, 44, 46, 44)
    label(ax, 37, 47.2, "Vier-Augen aktiv\n(Standard)")
    pfeil(ax, 72, 44, 104, 44)
    label(ax, 88, 48.4, "Freigabe durch Zweitsachbearbeiter\n(rechnerische Feststellung,\nSelbstfreigabe wird abgewehrt)")
    pfeil(ax, 28, 40.5, 104, 40.5, style="arc3,rad=0.09")
    label(ax, 66, 31.2, "ohne Vier-Augen —\nAusnahme nur für die Kennung „admin“")
    pfeil(ax, 117, 38, 117, 20)
    label(ax, 121.5, 29, "Kassenlauf (nur Admin):\neine Transaktion setzt alle\nBEREIT-Zahlungen auf ÜBERGEBEN", ha="left")
    pfeil(ax, 59, 38, 59, 11)
    label(ax, 54.5, 24, "Storno —\njeder Bearbeiter")
    pfeil(ax, 104, 40, 72, 7, style="arc3,rad=0.18")
    label(ax, 90, 16.5, "Storno — nur Admin")

    box(ax, 2, 12, 36, 9, "Bei jeder Anordnung mindert der\nZahlbetrag das Restbudget (BUDGET_REST);\nStorno bucht den Betrag zurück.", "neutral", 8.8)
    speichern(fig, "d04_zahlungsworkflow.png")


# ---------------------------------------------------------------- 5 Bedarfsberechnung
def d05():
    fig, ax = neue_fig("Berechnungsfluss Bedarfs- und Einkommensermittlung (SGB XII, Referenzjahr 2026)", 6.4)  # ylim 72
    box(ax, 4, 60, 52, 6.5, "Regelbedarf je Regelbedarfsstufe (RBS 1–6)\n563,00 · 506,00 · 451,00 · 471,00 · 390,00 · 357,00 EUR", "fach", 9.0)
    box(ax, 4, 51, 52, 6.5, "+ Mehrbedarf Merkzeichen G: 17 % des Regelbedarfs\n(§ 30 Abs. 1 SGB XII) — z. B. 95,71 EUR bei RBS 1", "fach", 9.0)
    box(ax, 4, 42, 52, 6.5, "+ dezentrale Warmwassererzeugung: stufenabhängig\n2,3 / 1,4 / 1,2 / 0,8 % je RBS (§ 30 Abs. 7)", "fach", 9.0)
    box(ax, 4, 33, 52, 6.5, "+ Kosten der Unterkunft und Heizung (KdU):\nKaltmiete + Betriebskosten + Heizung, BG-weit summiert", "fach", 9.0)
    pfeil(ax, 30, 60, 30, 57.5); pfeil(ax, 30, 51, 30, 48.5); pfeil(ax, 30, 42, 30, 39.5)
    pfeil(ax, 30, 33, 30, 26.5)

    box(ax, 74, 60, 52, 6.5, "Einkommen: Rente wird voll angerechnet,\nBrutto-Erwerbseinkommen für die Freibetragsprüfung", "fach", 9.0)
    box(ax, 74, 51, 52, 6.5, "− Erwerbsfreibetrag: min(30 % des Brutto,\n281,50 EUR = 50 % der RBS 1) nach § 82 Abs. 3 SGB XII", "fach", 9.0)
    box(ax, 74, 42, 52, 6.5, "= anrechenbares Einkommen\n(mindestens 0 EUR)", "fach", 9.0)
    pfeil(ax, 100, 60, 100, 57.5); pfeil(ax, 100, 51, 100, 48.5)
    pfeil(ax, 100, 42, 100, 26.5)

    box(ax, 30, 19, 70, 7.5, "Zahlbetrag der Bedarfsgemeinschaft =\nGesamtbedarf (Summe aller SozialFälle)\n− anrechenbares Einkommen", "tx", 9.4, True)
    pfeil(ax, 30, 26.5, 30, 22.7, stil="-"); pfeil(ax, 30, 22.7, 34, 22.7)
    pfeil(ax, 100, 26.5, 100, 22.7, stil="-"); pfeil(ax, 100, 22.7, 96, 22.7)
    pfeil(ax, 65, 19, 65, 14)
    entscheidung(ax, 65, 10, 30, 8, "Zahlbetrag > 0?")
    pfeil(ax, 80, 10, 96, 10); label(ax, 88, 11.8, "ja")
    box(ax, 96, 6.5, 32, 7, "Auszahlung möglich —\nBudgetprüfung gegen\ndas Restbudget", "ok", 9.0)
    pfeil(ax, 50, 10, 34, 10); label(ax, 42, 11.8, "nein")
    box(ax, 2, 6.5, 32, 7, "KEIN_ANSPRUCH —\nfachlicher Hinweis,\nkeine Auszahlung", "komp", 9.0)
    speichern(fig, "d05_bedarfsberechnung.png")


# ---------------------------------------------------------------- 6 Anmeldung und Rollen
def d06():
    fig, ax = neue_fig("Anmeldung, Sitzungs-Token und serverseitige Berechtigungsprüfung", 6.2)  # ylim 69
    box(ax, 2, 54, 26, 8, "Anmeldemaske\nKennung + Passwort\n(z. B. admin-01 / admin)", "web", 9.0)
    box(ax, 44, 54, 34, 8, "Benutzerverwaltung prüft\ndas Konto (Umgebung\nLMG_BENUTZER bzw. Standardkonten)", "fach", 9.0)
    box(ax, 96, 54, 32, 8, "Zufalls-Sitzungs-Token (UUID)\nim Serverspeicher;\nBrowser legt ihn im localStorage ab", "tx", 8.8)
    pfeil(ax, 28, 58, 44, 58); label(ax, 36, 60.2, "POST /api/anmeldung", size=8)
    pfeil(ax, 78, 58, 96, 58); label(ax, 87, 60.2, "Erfolg", size=8.4)
    pfeil(ax, 61, 54, 61, 49, stil="-"); pfeil(ax, 61, 49, 15, 49, stil="-"); pfeil(ax, 15, 49, 15, 54)
    label(ax, 38, 47.2, "Fehlschlag: „Kennung oder Passwort ist falsch“")

    box(ax, 20, 37, 90, 7, "Jede weitere API-Anfrage sendet „Authorization: Bearer <Token>“ —\n"
                           "die Webschicht prüft das Token vor der Fachverarbeitung", "neutral", 9.3)
    pfeil(ax, 65, 37, 65, 32)
    entscheidung(ax, 65, 27.5, 26, 9, "Token gültig?")
    pfeil(ax, 78, 27.5, 94, 27.5); label(ax, 86, 29.3, "nein")
    box(ax, 94, 24, 34, 7, "401 ANMELDUNG —\n„Bitte anmelden“,\nOberfläche zeigt die Login-Maske", "komp", 8.8)
    pfeil(ax, 65, 23, 65, 18); label(ax, 61, 20.5, "ja")
    entscheidung(ax, 65, 12.5, 36, 11, "Admin-Endpunkt?\n(Systemstatus,\nKassenlauf, Storno)")
    pfeil(ax, 47, 12.5, 30, 12.5); label(ax, 38.5, 14.3, "nein")
    box(ax, 2, 9, 28, 7, "Fachverarbeitung läuft —\ndie Kennung aus der Anmeldung\nlandet revisionssicher im Journal", "ok", 8.8)
    pfeil(ax, 83, 12.5, 94, 12.5); label(ax, 87, 15.6, "Rolle ≠ admin", size=8.2)
    box(ax, 94, 9, 34, 7, "403 BERECHTIGUNG —\n„Diese Funktion erfordert\ndie Admin-Rolle“", "komp", 8.8)
    speichern(fig, "d06_anmeldung_rolen.png")


if __name__ == "__main__":
    d01(); d02(); d03(); d04(); d05(); d06()
