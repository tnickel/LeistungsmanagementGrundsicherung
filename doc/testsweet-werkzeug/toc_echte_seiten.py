# Ermittelt echte Seitenzahlen aller H1/H2-Ueberschriften aus dem gerenderten PDF
# und erzeugt die Eintrags-JSON fuer add_toc_placeholders.py --entries.
# Aufruf: python3 toc_echte_seiten.py dokument.docx pdf/verzeichnis praefix
#   pdf/verzeichnis enthaelt praefix-NN.txt (pdftotext-Ausgabe je Seite)
import re, sys, os, json, glob, subprocess

docx, pdfdir = sys.argv[1], sys.argv[2]
praefix = sys.argv[3] if len(sys.argv) > 3 else "seite"

# 1) H1/H2-UEberschriften in Dokumentreihenfolge aus document.xml lesen
tmpdoc = os.path.join(os.path.dirname(os.path.abspath(__file__)), "x2")
if os.path.isdir(tmpdoc):
    shutil.rmtree(tmpdoc) if False else None
import zipfile, tempfile, shutil
tmpdoc = tempfile.mkdtemp()
with zipfile.ZipFile(docx) as z:
    z.extract("word/document.xml", tmpdoc)
xml = open(os.path.join(tmpdoc, "word", "document.xml"), encoding="utf-8").read()

def norm(t):
    # PDF-Textschicht liefert fuer Umlaute/Gedankenstriche defekte Zeichen:
    # nur ASCII-Buchstaben/Ziffern vergleichen, alles andere verwerfen.
    return re.sub(r"[^A-Za-z0-9]+", "", t)

headings = []
for pm in re.finditer(r"<w:p\b.*?</w:p>", xml, re.S):
    p = pm.group(0)
    sm = re.search(r'<w:pStyle w:val="(Heading[12])"/>', p)
    if not sm:
        continue
    text = "".join(re.findall(r"<w:t[^>]*>([^<]*)</w:t>", p))
    if not text.strip():
        continue
    headings.append({"level": 1 if sm.group(1) == "Heading1" else 2, "text": text})

# 2) Seitenzahltexte je PDF-Seite lesen
seiten = {}
for f in sorted(glob.glob(os.path.join(pdfdir, praefix + "-*.txt"))):
    n = int(re.search(r"-(\d+)\.txt$", f).group(1))
    seiten[n] = open(f, encoding="utf-8", errors="replace").read()
norm_seiten = {n: norm(t) for n, t in seiten.items()}
max_seite = max(seiten)

# 3) letzte TOC-Seite = Seite mit dem Aktualisierungs-Hinweis
hinweis = norm("Feldfunktionen erzeugt")
toc_ende = max((n for n, t in norm_seiten.items() if hinweis in t), default=1)

# 4) H1/H2 im Hauptteil suchen (ab toc_ende+1, monoton steigend)
cursor = toc_ende + 1
eintraege, fehlend = [], []
for h in headings:
    nadel = norm(h["text"])
    gefunden = None
    for n in range(cursor, max_seite + 1):
        if nadel in norm_seiten[n]:
            gefunden = n
            break
    if gefunden is None:
        fehlend.append(h["text"])
        continue
    cursor = gefunden  # gleiche Seite fuer Folgende erlaubt
    eintraege.append({"level": h["level"], "text": h["text"], "page": str(gefunden)})

json.dump(eintraege, open(os.path.join(os.path.dirname(docx), "toc_eintraege.json"), "w", encoding="utf-8"), ensure_ascii=False)
print("TOC-Seiten:", sorted(n for n in seiten if n <= toc_ende), "| Kopfseiten:", len(eintraege), "| fehlend:", fehlend)
for e in eintraege:
    print(f"  L{e['level']} S.{e['page']:>2}  {e['text']}")
