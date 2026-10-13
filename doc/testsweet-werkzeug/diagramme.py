# Zeichnet das Klassen-/Komponentendiagramm fuer das testsweet-Handbuch
# (Stil wie der Klassen- und Architekturueberblick) und schreibt bild_masse.json
# mit den Pixelmassen ALLER Bilder im Ordner bilder/ fuer den DOCX-Generator.
import glob
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.patches import FancyBboxPatch, FancyArrowPatch

DUNKEL = "#0E2A33"
TEAL   = "#1B6B7A"
HELL   = "#E4F0F2"
GRAU   = "#5A6B73"
PAPIER = "#FFFFFF"
plt.rcParams.update({"font.family": "DejaVu Sans", "font.size": 11})

def neu(b, h):
    fig, ax = plt.subplots(figsize=(b/200, h/200), dpi=200)
    ax.set_xlim(0, 100); ax.set_ylim(0, 100*h/b)
    ax.set_position([0, 0, 1, 1])
    ax.axis("off"); fig.patch.set_facecolor(PAPIER)
    return fig, ax

def kasten(ax, x, y, b, h, text, unter=None, fc=HELL, ec=TEAL, fs=11, ufs=8.5):
    ax.add_patch(FancyBboxPatch((x-b/2, y-h/2), b, h,
        boxstyle="round,pad=0.6,rounding_size=1.2",
        facecolor=fc, edgecolor=ec, linewidth=1.6, zorder=3))
    if unter:
        ax.text(x, y + h*0.22, text, ha="center", va="center", fontsize=fs,
                color=DUNKEL, fontweight="bold", zorder=4)
        ax.text(x, y - h*0.20, unter, ha="center", va="center", fontsize=ufs,
                color=GRAU, zorder=4, linespacing=1.3)
    else:
        ax.text(x, y, text, ha="center", va="center", fontsize=fs, color=DUNKEL,
                fontweight="bold", zorder=4)

def pfeil(ax, p1, p2, text=None, tc=TEAL, stil="-", fs=9, dx=0, dy=1.2,
          text_pos=0.5, ha="center", curv=0.0, lw=1.5):
    ax.add_patch(FancyArrowPatch(p1, p2, arrowstyle="-|>", mutation_scale=15,
        color=tc, linewidth=lw, linestyle=stil, zorder=2,
        connectionstyle=f"arc3,rad={curv}"))
    if text:
        mx = p1[0] + (p2[0]-p1[0])*text_pos + dx
        my = p1[1] + (p2[1]-p1[1])*text_pos + dy
        ax.text(mx, my, text, ha=ha, va="center", fontsize=fs, color=tc,
                fontstyle="italic", zorder=4,
                bbox=dict(facecolor=PAPIER, edgecolor="none", pad=1.0))

# ══════════════════════════════════════════════════════════════════════
# Klassen und Zusammenspiel (SweetServer → Simulator → Worker → Oracle,
# darunter Protokoll, Verifikation, Berichte)
# ══════════════════════════════════════════════════════════════════════
fig, ax = neu(1500, 950)
Y0 = 47
kasten(ax, 10, Y0, 16, 11, "Browser", unter="Anwender\n127.0.0.1:8095")
kasten(ax, 29.5, Y0, 18, 11, "SweetServer", unter="JDK HttpServer\nPort 8095 · /api/*")
kasten(ax, 50, Y0, 19, 11, "Simulator", unter="Auftragstopf · Seed\nPhasenbarriere · Threads")
kasten(ax, 72.5, Y0+7, 21, 11, "Mitarbeiter", unter="Modus 1:\nFachschicht direkt")
kasten(ax, 72.5, Y0-8, 21, 11, "WebMitarbeiter", unter="Modus 2: Selenium\n+ Chrome-Fenster")
kasten(ax, 92, Y0, 12, 11, "Oracle", unter="ACID,\nSperren")
kasten(ax, 78, 14, 22, 10, "Verifikator", unter="Soll/Ist je Auftrag (SQL)", fc="#DCEAED")
kasten(ax, 49.5, 14, 19, 10, "SweetLog", unter="logs/run-<Zeit>/", fc="#DCEAED")
kasten(ax, 22, 14, 26, 10, "Berichte", unter="MdBericht · Testdokumentation\nSweetPdf · LastBericht", fc="#DCEAED")

pfeil(ax, (18.7, Y0+1), (19.9, Y0+1))
pfeil(ax, (39.1, Y0+1), (39.9, Y0+1))
pfeil(ax, (60.1, Y0+3.5), (61.4, Y0+7.5), curv=-0.1)
pfeil(ax, (60.1, Y0-2.5), (61.4, Y0-6.5), curv=0.1)
pfeil(ax, (83, Y0+5), (85.4, Y0+2), curv=-0.1)
pfeil(ax, (83, Y0-6), (85.4, Y0-2.5), curv=0.1)
pfeil(ax, (92, Y0-5.7), (84, 19.6), stil=(0, (4, 3)), curv=-0.2,
      text="Soll/Ist per SQL", text_pos=0.7, dy=1.6)
pfeil(ax, (49.5, Y0-5.7), (49.5, 19.6), stil=(0, (4, 3)), text="Protokolle", dy=0, dx=7.2)
pfeil(ax, (66.4, 21.6), (24, 20.2), stil=(0, (4, 3)),
      text="Ergebnisse + Prüfstatus", text_pos=0.45, dy=2.2)
pfeil(ax, (39.3, 11.5), (35.7, 11.5), stil=(0, (4, 3)))
pfeil(ax, (15, 19.4), (9.5, 40.3), stil=(0, (4, 3)), curv=0.18,
      text="PDF/MD im\nGUI-Banner", text_pos=0.5, dx=8.8, dy=-1.2, fs=8.5)
ax.text(50, 3.2, "Alle Komponenten im Paket de.allgemein.testsweet — 22 Klassen, rund 7.800 Zeilen Hauptcode, dazu 5 Testklassen.",
        ha="center", va="center", fontsize=8.5, color=GRAU, fontstyle="italic")
fig.savefig("bilder/diagramm_klassen.png", dpi=200, bbox_inches="tight",
            facecolor=PAPIER, pad_inches=0.12)
plt.close(fig)
print("bilder/diagramm_klassen.png geschrieben")

# ─── Pixelmasse aller Bilder fuer generate.js ──────────────────────────
import json
from PIL import Image
masse = {}
for f in sorted(glob.glob("bilder/*.png")):
    name = f.replace("\\", "/").split("/")[-1]
    with Image.open(f) as im:
        masse[name] = {"w": im.width, "h": im.height}
json.dump(masse, open("bild_masse.json", "w"), indent=1)
print("bild_masse.json:", len(masse), "Bilder")
