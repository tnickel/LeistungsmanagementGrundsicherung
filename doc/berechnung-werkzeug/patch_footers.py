# Patcht die Seitenzahl-Felder im DOCX:
# - leeres <w:pgNumType/> entfernen (Deckblatt-Sektion, WPS-Kompatibilitaet)
# - Fusszeilen-Feld instrText PAGE -> "PAGE \* ROMAN \* MERGEFORMAT" (Abschnitt 2, TOC)
#                                bzw. "PAGE \* arabic \* MERGEFORMAT" (Abschnitt 3, Hauptteil)
import re, sys, shutil, zipfile, tempfile, os

path = sys.argv[1]
tmpdir = tempfile.mkdtemp()
with zipfile.ZipFile(path) as z:
    z.extractall(tmpdir)

doc_path = os.path.join(tmpdir, "word", "document.xml")
with open(doc_path, encoding="utf-8") as f:
    doc = f.read()

# 1) leere pgNumType entfernen
doc, n_leer = re.subn(r"<w:pgNumType/>", "", doc)

# 2) footerReference je Sektion in Dokumentreihenfolge
refs = re.findall(r'<w:footerReference[^>]*w:type="default"[^>]*r:id="(rId\d+)"', doc)
if not refs:
    refs = re.findall(r'<w:footerReference[^>]*r:id="(rId\d+)"', doc)

rels_path = os.path.join(tmpdir, "word", "_rels", "document.xml.rels")
with open(rels_path, encoding="utf-8") as f:
    rels = f.read()
id2file = dict(re.findall(r'<Relationship[^>]*Id="(rId\d+)"[^>]*Target="(footer\d+\.xml)"', rels))
# Reihenfolge alternativer Attribut-Reihenfolge abfangen
for m in re.finditer(r"<Relationship\b[^>]*/>", rels):
    tag = m.group(0)
    mid = re.search(r'Id="(rId\d+)"', tag)
    mtg = re.search(r'Target="(footer\d+\.xml)"', tag)
    if mid and mtg:
        id2file[mid.group(1)] = mtg.group(1)

def patch_footer(fname, fmt):
    fpath = os.path.join(tmpdir, "word", fname)
    with open(fpath, encoding="utf-8") as f:
        xml = f.read()
    xml2, n = re.subn(
        r"(<w:instrText[^>]*>)\s*PAGE\s*(</w:instrText>)",
        r"\g<1> PAGE \\* " + fmt + r" \\* MERGEFORMAT \g<2>", xml)
    with open(fpath, "w", encoding="utf-8") as f:
        f.write(xml2)
    return n

# Durchlaufende arabische Nummerierung: ALLE Fusszeilen bekommen \* arabic.
ergebnis = []
for r in refs:
    ergebnis.append(("arabic", r, patch_footer(id2file.get(r, ""), "arabic")))

with open(doc_path, "w", encoding="utf-8") as f:
    f.write(doc)

# neu packen
out = path + ".tmp"
with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
    for root, _, files in os.walk(tmpdir):
        for name in files:
            full = os.path.join(root, name)
            z.write(full, os.path.relpath(full, tmpdir))
shutil.move(out, path)
shutil.rmtree(tmpdir)
print("pgNumType entfernt:", n_leer, "| Footer-Patches:", ergebnis)
