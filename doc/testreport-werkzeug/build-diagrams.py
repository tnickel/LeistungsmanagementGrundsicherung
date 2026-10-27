"""Erzeugt reproduzierbare Methodenbilder als SVG und hochauflösendes PNG.

Ausführen mit der gebündelten Python-Laufzeit. Die Bilder enthalten keine
Laufzahlen und bescheinigen keine tatsächlich durchgeführten Prüfungen.
"""
from __future__ import annotations

from dataclasses import dataclass
from html import escape
from pathlib import Path
import math

from PIL import Image, ImageDraw, ImageFont

OUTPUT = Path(__file__).resolve().parent / "diagram-assets"
SCALE = 2
WIDTH = 1600
INK = "#101820"
MUTED = "#40515D"
TEAL = "#17656B"
NAVY = "#294956"
PALE = "#EDF5F5"
LIGHT = "#F5F7F8"
LINE = "#C7D7DC"
GOLD = "#A77823"
PALE_GOLD = "#FBF5E8"
REGULAR = Path("C:/Windows/Fonts/arial.ttf")
BOLD = Path("C:/Windows/Fonts/arialbd.ttf")


@dataclass
class Canvas:
    height: int
    title: str
    description: str

    def __post_init__(self):
        self.image = Image.new("RGB", (WIDTH * SCALE, self.height * SCALE), "white")
        self.draw = ImageDraw.Draw(self.image)
        self.elements = []
        self.text(60, 55, self.title, 43, bold=True, fill=INK)

    def font(self, size, bold=False):
        return ImageFont.truetype(str(BOLD if bold else REGULAR), size * SCALE)

    def text(self, x, y, value, size=32, bold=False, fill=INK, anchor="left", gap=1.3):
        """y ist die vertikale Mitte der ersten Zeile, keine Grundlinie."""
        if size < 32:
            raise ValueError("Text muss bei 16,3 cm Bildbreite mindestens 9 pt ergeben.")
        lines = value.split("\n")
        for index, line in enumerate(lines):
            line_y = y + index * size * gap
            text_width = self.font(size, bold).getlength(line) / SCALE
            left = x - text_width / 2 if anchor == "middle" else x
            if left < 0 or left + text_width > WIDTH:
                raise ValueError(f"Text liegt außerhalb der Bildfläche: {line}")
            self.draw.text((x * SCALE, line_y * SCALE), line, font=self.font(size, bold),
                           fill=fill, anchor="mm" if anchor == "middle" else "lm")
            self.elements.append(
                f'<text x="{x}" y="{line_y:.2f}" font-family="Arial, sans-serif" '
                f'font-size="{size}" font-weight="{700 if bold else 400}" fill="{fill}" '
                f'text-anchor="{anchor if anchor != "left" else "start"}" '
                f'dominant-baseline="central">{escape(line)}</text>'
            )

    def rect(self, x, y, w, h, fill=LIGHT, outline=LINE, radius=16, stroke=2):
        xy = tuple(round(v * SCALE) for v in (x, y, x + w, y + h))
        self.draw.rounded_rectangle(xy, radius=radius * SCALE, fill=fill,
                                    outline=outline, width=stroke * SCALE)
        self.elements.append(
            f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{radius}" '
            f'fill="{fill}" stroke="{outline}" stroke-width="{stroke}"/>'
        )

    def line(self, points, fill=TEAL, width=4, arrow=False):
        flat = [(round(x * SCALE), round(y * SCALE)) for x, y in points]
        self.draw.line(flat, fill=fill, width=width * SCALE, joint="curve")
        self.elements.append(
            f'<polyline points="{" ".join(f"{x},{y}" for x,y in points)}" '
            f'fill="none" stroke="{fill}" stroke-width="{width}" stroke-linejoin="round"/>'
        )
        if arrow:
            (x0, y0), (x1, y1) = points[-2:]
            angle = math.atan2(y1 - y0, x1 - x0)
            length, half_width = 15, 8
            back = (x1 - length * math.cos(angle), y1 - length * math.sin(angle))
            tri = [(x1, y1),
                   (back[0] + half_width * math.sin(angle), back[1] - half_width * math.cos(angle)),
                   (back[0] - half_width * math.sin(angle), back[1] + half_width * math.cos(angle))]
            self.draw.polygon([(round(x * SCALE), round(y * SCALE)) for x, y in tri], fill=fill)
            self.elements.append(f'<polygon points="{" ".join(f"{x:.2f},{y:.2f}" for x,y in tri)}" fill="{fill}"/>')

    def number(self, x, y, value, color=TEAL):
        radius = 29
        self.draw.ellipse(((x - radius) * SCALE, (y - radius) * SCALE,
                           (x + radius) * SCALE, (y + radius) * SCALE), fill=color)
        self.elements.append(f'<circle cx="{x}" cy="{y}" r="{radius}" fill="{color}"/>')
        self.text(x, y, value, 32, bold=True, fill="white", anchor="middle")

    def save(self, stem):
        OUTPUT.mkdir(parents=True, exist_ok=True)
        self.image.save(OUTPUT / (stem + ".png"), dpi=(300, 300), optimize=True)
        body = "\n".join(self.elements)
        svg = (f'<svg xmlns="http://www.w3.org/2000/svg" width="{WIDTH}" height="{self.height}" '
               f'viewBox="0 0 {WIDTH} {self.height}" role="img" aria-labelledby="title desc">\n'
               f'<title id="title">{escape(self.title)}</title>\n'
               f'<desc id="desc">{escape(self.description)}</desc>\n'
               f'<rect width="100%" height="100%" fill="white"/>\n{body}\n</svg>\n')
        (OUTPUT / (stem + ".svg")).write_text(svg, encoding="utf-8")
        print(f"{stem}: SVG {WIDTH}x{self.height}; PNG {WIDTH*SCALE}x{self.height*SCALE}")


def test_layers():
    c = Canvas(830, "Wie sich die Prüfungen ergänzen",
               "Einzelne Bausteine, vollständige Abläufe und Browserbedienung prüfen verschiedene Fehler. "
               "Bei den Abläufen wird zusätzlich der gespeicherte Datenbankzustand nachgeprüft.")
    c.text(60, 112, "Jede Ebene beantwortet eine andere Frage.", 32, fill=MUTED)
    rows = [
        (170, "1", "Einzelne Bausteine prüfen", "Unit Tests: Stimmt ein einzelner Baustein?\nZum Beispiel Berechnung oder Bankprüfung."),
        (360, "2", "Ganze Arbeitsabläufe prüfen", "Ablauftests: Anlegen, Freigeben und Stornieren.\nArbeiten die Bausteine richtig zusammen?"),
        (550, "3", "Die echte Oberfläche bedienen", "Browsertests: Anmeldung, Formulare und Klicks.\nFunktioniert der Weg durch die Oberfläche?")
    ]
    for y, n, heading, body in rows:
        c.rect(60, y, 940, 160, PALE if n == "1" else LIGHT)
        c.number(110, y + 44, n)
        c.text(164, y + 42, heading, 32, bold=True)
        c.text(164, y + 87, body, 32, fill=MUTED, gap=1.24)
    c.rect(1110, 360, 430, 350, PALE, TEAL)
    c.text(1325, 409, "SQL Kontrolle", 33, bold=True, anchor="middle")
    c.text(1325, 480, "Datenbank direkt\nnachprüfen", 32, bold=True, anchor="middle")
    c.text(1325, 600, "Was wurde tatsächlich\ngespeichert?", 32, fill=MUTED, anchor="middle")
    c.line([(1000, 440), (1090, 440)], arrow=True)
    c.line([(1000, 630), (1090, 630)], arrow=True)
    c.text(60, 780, "Die Ebenen prüfen verschiedene Fehler und ergänzen sich.", 32, fill=MUTED)
    c.save("01-pruefebenen")


def case_origins():
    c = Canvas(850, "Woher die Testfälle kommen",
               "Vier Arten von Eingaben führen zu ausführbaren Szenarien: mit Startwert erzeugte Kombinationen, "
               "feste Grenzfälle, quellenbasierte Modellfälle und feste Vergleichswerte eines externen Rechners.")
    c.text(60, 113, "Vier Herkunftsarten liefern unterschiedliche Prüfanlässe.", 32, fill=MUTED)
    x_values = [60, 450, 840, 1230]
    nodes = [
        ("Erzeugte\nKombinationen", "Zufallsauswahl\nmit Startwert\nwiederholbar"),
        ("Feste\nGrenzfälle", "Gewählte Grenzen\nund berechnete\nHandwerte"),
        ("Quellenbasierte\nModellfälle", "Öffentliche Werte\nund offengelegte\nErgänzungen"),
        ("Externe\nVergleichswerte", "Feste Ergebnisse\naus einem\nInternetrechner")
    ]
    for i, (x, (heading, body)) in enumerate(zip(x_values, nodes)):
        c.rect(x, 170, 310, 315, PALE if i < 2 else PALE_GOLD,
               TEAL if i < 2 else GOLD)
        c.text(x + 155, 220, heading, 32, bold=True, anchor="middle", gap=1.2)
        c.text(x + 155, 330, body, 32, fill=MUTED, anchor="middle", gap=1.3)
        c.line([(x + 155, 485), (x + 155, 515)], arrow=True)
    c.line([(215, 515), (1385, 515)])
    c.line([(280, 515), (280, 584)], arrow=True)
    stages = [(60, "Fall ausführen", "Eingaben und\nArbeitsschritte"),
              (610, "Datenbank auslesen", "Gespeichertes\nErgebnis"),
              (1160, "Erwartung prüfen", "Soll und Ist\nvergleichen")]
    for x, heading, body in stages:
        c.rect(x, 590, 380, 150, LIGHT)
        c.text(x + 190, 628, heading, 32, bold=True, anchor="middle")
        c.text(x + 190, 670, body, 32, fill=MUTED, anchor="middle", gap=1.25)
    c.line([(440, 657), (590, 657)], arrow=True)
    c.line([(990, 657), (1140, 657)], arrow=True)
    c.text(60, 800, "Modellfälle nutzen öffentliche Quellen und offengelegte Ergänzungen.", 32, fill=MUTED)
    c.save("02-fallentstehung")


def test_process():
    c = Canvas(770, "So wird ein Testfall geprüft",
               "Ein Fall besitzt Eingaben und Erwartungen. Er läuft entweder direkt über die Fachschicht "
               "oder im echten Browser. Danach werden gespeicherte Daten mit den Erwartungen verglichen und berichtet.")
    c.text(60, 112, "Erwartung festlegen, den Fall ausführen und den gespeicherten Zustand vergleichen.", 32, fill=MUTED)
    c.rect(60, 300, 280, 210, LIGHT)
    c.text(200, 346, "Eingaben und", 32, bold=True, anchor="middle")
    c.text(200, 386, "Erwartungen", 32, bold=True, anchor="middle")
    c.text(200, 440, "Haushalt, Werte,\nArbeitsschritte", 32, fill=MUTED, anchor="middle", gap=1.2)
    c.rect(430, 175, 360, 180, PALE, TEAL)
    c.text(610, 219, "Direkter Fachweg", 32, bold=True, anchor="middle")
    c.text(610, 278, "Simulator ruft\ndie Fachservices auf.", 32, fill=MUTED, anchor="middle")
    c.rect(430, 460, 360, 180, PALE, TEAL)
    c.text(610, 505, "Echter Browser", 32, bold=True, anchor="middle")
    c.text(610, 564, "Chrome bedient\ndie Oberfläche.", 32, fill=MUTED, anchor="middle")
    c.line([(340, 402), (380, 402), (380, 259), (410, 259)], arrow=True)
    c.line([(340, 402), (380, 402), (380, 545), (410, 545)], arrow=True)
    c.rect(890, 300, 300, 210, PALE_GOLD, GOLD)
    c.text(1040, 346, "SQL Kontrolle", 32, bold=True, anchor="middle")
    c.text(1040, 412, "Datenbank\ndirekt nachprüfen", 32, anchor="middle")
    c.line([(790, 259), (840, 259), (840, 402), (870, 402)], arrow=True)
    c.line([(790, 545), (840, 545), (840, 402), (870, 402)], arrow=True)
    c.rect(1280, 300, 260, 210, LIGHT)
    c.text(1410, 346, "Testbericht", 32, bold=True, anchor="middle")
    c.text(1410, 402, "Bestanden,\nFehler oder\nunvollständig", 32, fill=MUTED, anchor="middle", gap=1.2)
    c.line([(1190, 402), (1260, 402)], arrow=True)
    c.text(60, 709, "Der Test vergleicht Beträge, Status und weitere gespeicherte Werte mit der Erwartung.", 32, fill=MUTED)
    c.save("03-pruefablauf")


def stress_waves():
    c = Canvas(950, "Zwei Wellen mit einer gemeinsamen Wartephase",
               "Mehrere simulierte Sachbearbeiter legen in Welle eins Vorgänge an. "
               "Erst wenn alle fertig sind, startet Welle zwei mit Wiederfinden, Ändern und Anordnen. "
               "Die Datenbank wird danach direkt nachgeprüft.")
    c.text(60, 113, "Dieselben Kennungen bearbeiten dieselben Vorgänge erneut.", 32, fill=MUTED)
    c.text(60, 179, "Welle 1", 35, bold=True)
    c.text(980, 179, "Welle 2", 35, bold=True)
    c.rect(710, 215, 180, 490, PALE_GOLD, GOLD)
    c.text(800, 264, "Warten", 32, bold=True, anchor="middle")
    c.text(800, 355, "Welle 1\nist für alle\nbeendet.\nDann folgt\nWelle 2.", 32, fill=MUTED, anchor="middle", gap=1.6)
    for i, letter in enumerate("ABC"):
        y = 235 + i * 160
        c.rect(60, y, 550, 130, PALE, TEAL)
        c.number(106, y + 65, letter)
        c.text(158, y + 65, "Vorgänge anlegen", 32, bold=True)
        c.line([(610, y + 65), (690, y + 65)], arrow=True)
        c.rect(980, y, 560, 130, PALE, TEAL)
        c.number(1026, y + 65, letter)
        c.text(1076, y + 25, "Erneut öffnen", 32, bold=True)
        c.text(1076, y + 64, "Miete ändern,\nZahlung anordnen", 32, fill=MUTED, gap=1.2)
        c.line([(890, y + 65), (960, y + 65)], arrow=True)
        c.line([(1540, y + 65), (1570, y + 65)])
    c.rect(340, 775, 920, 90, LIGHT)
    c.text(800, 820, "SQL Kontrolle: Datenbank direkt nachprüfen", 32, bold=True, anchor="middle")
    c.line([(1570, 300), (1570, 735), (800, 735), (800, 765)], arrow=True)
    c.text(60, 913, "Die Ergebnisse gelten für diesen Ablauf und diese Testumgebung.", 32, fill=MUTED)
    c.save("04-stress-zwei-wellen")


if __name__ == "__main__":
    test_layers()
    case_origins()
    test_process()
    stress_waves()
