# Patcht Format-Grundlagen ins DOCX:
# - settings.xml: <w:autoHyphenation/> (automatische Silbentrennung)
# - styles.xml:   Sprache de-DE in den Dokument-Standards (docDefaults)
import re, sys, shutil, zipfile, tempfile, os

path = sys.argv[1]
tmpdir = tempfile.mkdtemp()
with zipfile.ZipFile(path) as z:
    z.extractall(tmpdir)

# ── settings.xml ──
sp = os.path.join(tmpdir, "word", "settings.xml")
s = open(sp, encoding="utf-8").read()
if "<w:autoHyphenation" not in s:
    if "<w:defaultTabStop" in s:
        s = re.sub(r'(<w:defaultTabStop[^/>]*/>)', r'\1<w:autoHyphenation/>', s, count=1)
    elif "<w:updateFields" in s:
        s = s.replace("<w:updateFields", "<w:autoHyphenation/><w:updateFields", 1)
    elif "<w:compat>" in s:
        s = s.replace("<w:compat>", "<w:autoHyphenation/><w:compat>", 1)
    else:
        s = re.sub(r'(<w:settings[^>]*>)', r'\1<w:autoHyphenation/>', s, count=1)
    open(sp, "w", encoding="utf-8").write(s)
    print("autoHyphenation eingefuegt")
else:
    print("autoHyphenation bereits vorhanden")

# ── styles.xml: Sprache de-DE in rPrDefault ──
yp = os.path.join(tmpdir, "word", "styles.xml")
y = open(yp, encoding="utf-8").read()
m = re.search(r"<w:rPrDefault>\s*<w:rPr>(.*?)</w:rPr>", y, re.S)
if m and "<w:lang" not in m.group(1):
    inner = m.group(1)
    neu = inner + '<w:lang w:val="de-DE" w:eastAsia="de-DE"/>'
    y = y[:m.start(1)] + neu + y[m.end(1):]
    open(yp, "w", encoding="utf-8").write(y)
    print("Sprache de-DE in docDefaults gesetzt")
else:
    print("rPrDefault/lang:", "bereits vorhanden" if m else "rPrDefault nicht gefunden")

out = path + ".tmp"
with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
    for root, _, files in os.walk(tmpdir):
        for name in files:
            full = os.path.join(root, name)
            z.write(full, os.path.relpath(full, tmpdir))
shutil.move(out, path)
shutil.rmtree(tmpdir)
print("Format-Patch fertig:", path)
