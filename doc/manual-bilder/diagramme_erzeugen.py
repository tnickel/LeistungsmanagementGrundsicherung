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
            "SepaValidator · BescheidGenerator (Monatsplan) · DAOs", "fach")
    pfeil(ax, 65, 40, 65, 34.5)
    label(ax, 87, 37.2, "Facharbeit läuft als begrenzter Task")

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
    fig, ax = neue_fig("Kurze Transaktion mit Rollback und begrenztem Retry", 6.2)
    box(ax, 33, 56, 64, 8, "Verbindung holen und Transaktion beginnen\nFacharbeit ausführen, monotone Deadline prüfen", "tx", 10)
    pfeil(ax, 65, 56, 65, 51)
    entscheidung(ax, 65, 46, 34, 10, "Facharbeit erfolgreich?")
    pfeil(ax, 82, 46, 99, 46); label(ax, 90, 48, "ja")
    box(ax, 99, 41, 29, 10, "COMMIT\nVerbindung freigeben\nErgebnis zurückgeben", "ok", 9)
    pfeil(ax, 65, 41, 65, 36); label(ax, 69, 38, "nein")
    box(ax, 42, 28, 46, 8, "ROLLBACK\nVerbindung freigeben", "komp", 10)
    pfeil(ax, 65, 28, 65, 23)
    entscheidung(ax, 65, 17, 40, 12, "Sperrkonflikt und\nVersuch übrig?")
    pfeil(ax, 85, 17, 99, 17); label(ax, 92, 19, "nein")
    box(ax, 99, 12, 29, 10, "Abbruch\nFehler zurückgeben\nkein erneuter Versuch", "komp", 9)
    pfeil(ax, 45, 17, 28, 17); label(ax, 36, 19, "ja")
    box(ax, 2, 12, 26, 10, "Backoff mit Jitter\nStandard: 200, 400 ms\nmaximal zwei Pausen", "tx", 8.6)
    pfeil(ax, 15, 22, 15, 60, stil="-"); pfeil(ax, 15, 60, 33, 60)
    speichern(fig, "d02_kurze_transaktion.png")


# ---------------------------------------------------------------- 3 Saga
def d03():
    fig, ax = neue_fig("Antrags-Saga: vier Schritte mit Rückwärtskompensation", 5.6)  # ylim 63
    xs = [2, 34.5, 67, 99.5]
    w = 28
    schritte = [
        ("1 · Vorgang erfassen", "Bedarfsgemeinschaft +\nSozialFall anlegen"),
        ("2 · Bedarfsprüfung", "SGB-XII-Berechnung;\nZahlbetrag ≤ 0 → Abbruch"),
        ("3 · Bescheid erteilen", "Bewilligungsbescheid\nmit Monatsplan anlegen"),
        ("4 · Erstauszahlung", "Zahlung ANGELEGT +\nRestbudget mindern"),
    ]
    komp = [
        "Kompensation:\nEigenen Vorgang historisieren",
        "Kompensation:\nJournaleintrag (nichts\nzu zurückrollen)",
        "Kompensation:\nEigenen Bescheid aufheben\n(AUFGEHOBEN)",
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
    box(ax, 2, 6, 41, 10, "Journal: TX_SAGA + TX_SAGA_STEP\nIN_FLIGHT/DONE werden bestätigt —\n"
        "das Journal überlebt einen Absturz.", "neutral", 8.8)
    box(ax, 44.5, 6, 41, 10, "Zustände der Saga:\nRUNNING → COMPLETED bzw.\nFAILED → COMPENSATED / FAILED", "neutral", 8.8)
    box(ax, 87, 6, 41, 10, "Wiederanlauf beim Serverstart:\nExklusiver Claim; lebende Sagas überspringen.\nFehler bleiben FAILED und prüfbedürftig.", "neutral", 8.3)
    speichern(fig, "d03_saga.png")


# ---------------------------------------------------------------- 4 Zahlungsworkflow
def d04():
    fig, ax = neue_fig("Zahlungsworkflow: Vier-Augen-Prinzip und Statusweg", 5.8)  # ylim 65
    box(ax, 2, 38, 26, 10, "Auszahlung anordnen\n(Sachbearbeiter; eine kurze\nTransaktion mit Idempotenz,\nSEPA- und Budgetprüfung)", "fach", 8.8)
    box(ax, 46, 38, 26, 10, "ANGELEGT\nwartet auf\nVier-Augen-Freigabe", "ent", 9.0, True)
    box(ax, 102, 38, 26, 10, "BEREIT\nfür Kassenlauf\nvorgemerkt", "ok", 9.0, True)
    box(ax, 102, 12, 26, 8, "UEBERGEBEN\nÜbergabe an Kasse", "tx", 9.0, True)
    box(ax, 46, 3, 26, 8, "STORNIERT\nBudget zurückgebucht\nmit Journaleintrag", "komp", 9.0, True)

    pfeil(ax, 28, 44, 46, 44)
    label(ax, 37, 47.2, "immer ANGELEGT")
    pfeil(ax, 72, 44, 104, 44)
    label(ax, 88, 48.4, "Freigabe durch Zweitsachbearbeiter\n(rechnerische Feststellung,\nAdmin-Sonderrolle beachten)")
    pfeil(ax, 117, 38, 117, 20)
    label(ax, 123, 29, "Kasse\n(Admin)", size=8)
    pfeil(ax, 59, 38, 59, 11)
    label(ax, 54.5, 24, "Storno —\nnur Admin")
    pfeil(ax, 104, 40, 72, 7, style="arc3,rad=0.18")
    label(ax, 90, 16.5, "Storno — nur Admin")

    box(ax, 2, 12, 36, 9, "Anordnung mindert Restbudget.\nStorno stellt Budget und\nVermögenseinsatz wieder her.", "neutral", 8.8)
    speichern(fig, "d04_zahlungsworkflow.png")


# ---------------------------------------------------------------- 5 Bedarfsberechnung
def d05():
    fig, ax = neue_fig("Berechnungsfluss im implementierten Modell 2026", 6.4)
    box(ax, 3, 57, 59, 10, "Regelbedarf RBS 1–6 nach SATZSTAND\n563 · 506 · 451 · 471 · 390 · 357 EUR", "fach", 9.4)
    box(ax, 3, 43, 59, 10, "+ G, Schwangerschaft, Alleinerziehende\nAbs. 1–5 zusammen höchstens Regelbedarf;\n+ dezentrales Warmwasser separat", "fach", 9.2)
    box(ax, 3, 29, 59, 10, "+ Kaltmiete + Betriebskosten + Heizung\nzentraler Warmwasserabzug optional;\nBG-Gesamtgrenze optional", "fach", 9.2)
    pfeil(ax, 32, 57, 32, 53); pfeil(ax, 32, 43, 32, 39)
    box(ax, 69, 57, 59, 10, "Rente + Brutto-Erwerbseinkommen\n− min(30 % Erwerb, 281,50 EUR)\n− erfasste Absetzbeträge; mindestens 0", "fach", 9.2)
    box(ax, 69, 39, 59, 12, "Verwertbares Vermögen\nBestand − Schonvermögen, mindestens 0;\nVerbrauch wird je Auszahlung dokumentiert\nund im Folgemonat fortgeschrieben", "fach", 9.2)
    box(ax, 16, 9, 100, 13, "Zahlbetrag = Gesamtbedarf − anrechenbares Einkommen − Vermögenseinsatz\n\nFür eine Zahlung zusätzlich: positiver Anspruch, Restbudget, aktive BG,\neindeutige Bescheiddeckung und passender bewilligter Monatsbetrag", "tx", 9.2)
    pfeil(ax, 32, 29, 32, 22); pfeil(ax, 98, 39, 98, 22)
    speichern(fig, "d05_bedarfsberechnung.png")


# ---------------------------------------------------------------- 6 Anmeldung und Rollen
def d06():
    fig, ax = neue_fig("Anmeldung, Sitzungs-Token und serverseitige Berechtigungsprüfung", 6.2)  # ylim 69
    box(ax, 2, 54, 26, 8, "Anmeldemaske\nKennung + Passwort\n(z. B. admin-01 / admin)", "web", 9.0)
    box(ax, 44, 54, 34, 8, "Benutzerverwaltung prüft\ndas Konto (Umgebung\nLMG_BENUTZER bzw. Standardkonten)", "fach", 9.0)
    box(ax, 96, 54, 32, 8, "Zufalls-Sitzungs-Token (UUID)\nim Serverspeicher;\nBrowser legt ihn im sessionStorage ab", "tx", 8.8)
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
    pfeil(ax, 47, 12.5, 30, 12.5); label(ax, 38.5, 16, "nein oder\nRolle admin", size=8)
    box(ax, 2, 9, 28, 7, "Fachverarbeitung läuft —\ndie Kennung aus der Anmeldung\nlandet revisionssicher im Journal", "ok", 8.8)
    pfeil(ax, 83, 12.5, 94, 12.5); label(ax, 87, 15.6, "Rolle ≠ admin", size=8.2)
    box(ax, 94, 9, 34, 7, "403 BERECHTIGUNG —\n„Diese Funktion erfordert\ndie Admin-Rolle“", "komp", 8.8)
    speichern(fig, "d06_anmeldung_rolen.png")


if __name__ == "__main__":
    d01(); d02(); d03(); d04(); d05(); d06()
