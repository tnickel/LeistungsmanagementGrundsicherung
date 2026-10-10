# -*- coding: utf-8 -*-
"""Patched Fußzeilen-Feldcodes (ROMAN/arabic) und entfernt leere pgNumType-Elemente.

WPS-kompatible Nachbearbeitung laut docx-Skill (references/toc.md).
Aufruf: python fuszeilen_patchen.py <docx>
"""
import re
import shutil
import sys
import zipfile
from pathlib import Path

ZIEL = Path(sys.argv[1])
ARBEIT = ZIEL.with_suffix(".patch-tmp.docx")

with zipfile.ZipFile(ZIEL, "r") as quelle:
    eintraege = {name: quelle.read(name) for name in quelle.namelist()}

# Fußzeilen identifizieren und Feldcodes formatieren.
# Reihenfolge der Sektionen im Dokument: Deckblatt (kein Footer),
# Vorwort/Inhaltsverzeichnis (ROMAN), Hauptteil (arabic).
footer_namen = sorted(n for n in eintraege if re.match(r"word/footer\d+\.xml", n))
dokument_xml = eintraege["word/document.xml"].decode("utf-8")

# Zuordnung: welche Footer-Referenz gehört zu welcher Sektion?
# Sektion 2 (römisch) referenziert die zuerst genannte rId -> footerN.xml;
# wir lesen die Reihenfolge aus document.xml (footerReference-Reihenfolge).
referenzen = re.findall(r'<w:footerReference[^>]*r:id="(rId\d+)"', dokument_xml)
relationen = eintraege["word/_rels/document.xml.rels"].decode("utf-8")
rid_zu_datei = dict(re.findall(r'Id="(rId\d+)"[^>]*Target="(footer\d+\.xml)"', relationen))

fuss_reihenfolge = [rid_zu_datei.get(rid) for rid in referenzen if rid in rid_zu_datei]
format_plan = {}
if len(fuss_reihenfolge) >= 1:
    format_plan["word/" + fuss_reihenfolge[0]] = "ROMAN"
for name in fuss_reihenfolge[1:]:
    format_plan.setdefault("word/" + name, "arabic")

for name, format_switch in format_plan.items():
    if name not in eintraege:
        continue
    xml = eintraege[name].decode("utf-8")
    xml_neu = re.sub(
        r"(<w:instrText[^>]*>)\s*PAGE\s*(</w:instrText>)",
        lambda m: m.group(1) + f" PAGE \\* {format_switch} \\* MERGEFORMAT " + m.group(2),
        xml,
    )
    if xml_neu != xml:
        eintraege[name] = xml_neu.encode("utf-8")
        print(f"gepatcht: {name} -> PAGE \\* {format_switch}")

# Leere pgNumType-Elemente aus document.xml entfernen (WPS-Kompatibilität).
dokument_xml_neu = dokument_xml.replace("<w:pgNumType/>", "")
if dokument_xml_neu != dokument_xml:
    print("entfernt: leere <w:pgNumType/>-Elemente")
eintraege["word/document.xml"] = dokument_xml_neu.encode("utf-8")

with zipfile.ZipFile(ARBEIT, "w", zipfile.ZIP_DEFLATED) as ziel:
    for name, daten in eintraege.items():
        ziel.writestr(name, daten)

shutil.move(ARBEIT, ZIEL)
print("fertig:", ZIEL)
