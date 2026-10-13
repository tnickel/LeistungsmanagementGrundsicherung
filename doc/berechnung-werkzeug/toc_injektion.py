# Ersetzt den Inhalt des TOC-Felds (sdtContent) durch selbst gebaute,
# einspaltige Eintraege: Text links, Punktfulle, Seitenzahl rechts an
# Tabstopp 8788 Twips (= Satzspiegelbreite). Keine Formatvorlagen,
# alles Direktformatierung — dadurch kein Zeilenumbruch wie beim
# Platzhalter-Skript und kein _Toc1-Duplikat-Artefakt mehr.
# Aufruf: python3 toc_injektion.py dokument.docx eintraege.json
import re, sys, json, zipfile, tempfile, shutil, os

docx, entries_json = sys.argv[1], sys.argv[2]
entries = json.load(open(entries_json, encoding="utf-8"))

tmp = tempfile.mkdtemp()
with zipfile.ZipFile(docx) as z:
    z.extractall(tmp)
p = os.path.join(tmp, "word", "document.xml")
xml = open(p, encoding="utf-8").read()

# bestehende TOC-Feldanweisung uebernehmen (enthaltet \o-Bereich und \h)
m = re.search(r"<w:instrText[^>]*>\s*(TOC[^<]*)</w:instrText>", xml)
assert m, "TOC-Feldanweisung nicht gefunden"
instr = m.group(1).strip()

FONT = '<w:rFonts w:ascii="Calibri" w:hAnsi="Calibri"/>'
def entry_xml(e):
    lvl, text, page = int(e["level"]), e["text"], str(e["page"])
    esc = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    tab = '<w:tabs><w:tab w:val="right" w:leader="dot" w:pos="8788"/></w:tabs>'
    if lvl == 1:
        ppr = f'<w:pPr>{tab}<w:spacing w:before="240" w:after="60" w:line="276" w:lineRule="auto"/><w:ind w:left="0"/></w:pPr>'
        rpr = f'<w:rPr>{FONT}<w:b/><w:color w:val="0E2A33"/><w:sz w:val="23"/><w:szCs w:val="23"/></w:rPr>'
    else:
        ppr = f'<w:pPr>{tab}<w:spacing w:before="40" w:after="40" w:line="264" w:lineRule="auto"/><w:ind w:left="400"/></w:pPr>'
        rpr = f'<w:rPr>{FONT}<w:color w:val="1A1A1A"/><w:sz w:val="21"/><w:szCs w:val="21"/></w:rPr>'
    return (f"<w:p>{ppr}"
            f'<w:r>{rpr}<w:t xml:space="preserve">{esc}</w:t></w:r>'
            f"<w:r>{rpr}<w:tab/></w:r>"
            f"<w:r>{rpr}<w:t>{page}</w:t></w:r></w:p>")

feld = ('<w:p><w:pPr><w:spacing w:after="80" w:line="264" w:lineRule="auto"/></w:pPr>'
        '<w:r><w:fldChar w:fldCharType="begin" w:dirty="true"/></w:r>'
        f'<w:r><w:instrText xml:space="preserve"> {instr} </w:instrText></w:r>'
        '<w:r><w:fldChar w:fldCharType="separate"/></w:r></w:p>'
        + "".join(entry_xml(e) for e in entries)
        + '<w:p><w:pPr><w:spacing w:line="264" w:lineRule="auto"/></w:pPr>'
        '<w:r><w:fldChar w:fldCharType="end"/></w:r></w:p>')

ms = re.search(r"<w:sdtContent>(.*?)</w:sdtContent>", xml, re.S)
assert ms and "TOC" in ms.group(1), "TOC-sdtContent nicht gefunden"
xml = xml[:ms.start(1)] + feld + xml[ms.end(1):]
open(p, "w", encoding="utf-8").write(xml)

out = docx + ".tmp"
with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
    for root, _, files in os.walk(tmp):
        for name in files:
            full = os.path.join(root, name)
            z.write(full, os.path.relpath(full, tmp))
shutil.move(out, docx)
shutil.rmtree(tmp)
print(f"TOC injiziert: {len(entries)} Eintraege, Feld: {instr!r}")
