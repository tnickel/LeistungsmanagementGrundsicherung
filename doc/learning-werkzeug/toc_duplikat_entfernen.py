# -*- coding: utf-8 -*-
"""
Entfernt das bekannte _Toc1-Hyperlink-Duplikat, das add_toc_placeholders.py
als ersten Eintrag ins Inhaltsverzeichnis einfügt (Hyperlink ohne Seitenzahl,
direkt hinter dem TOC-Feldanfang). Lektion aus der Klassenüberblick-Generierung:
dieses Duplikat ist nach jedem TOC-Platzhalterlauf zu löschen.

Aufruf: python toc_duplikat_entfernen.py <pfad-zur-docx>
"""
import re
import shutil
import sys
import zipfile
from pathlib import Path


def main(argv):
    if len(argv) != 2:
        print("Aufruf: python toc_duplikat_entfernen.py <docx>")
        return 2
    docx_path = Path(argv[1])
    if not docx_path.exists():
        print("Datei nicht gefunden: " + str(docx_path))
        return 2

    with zipfile.ZipFile(docx_path, "r") as z:
        xml = z.read("word/document.xml").decode("utf-8")

    # Der Duplikat-Eintrag: erster Hyperlink im TOC-Feld, Anker _Toc1, enthält
    # ein PAGEREF-Feld ohne Ergebnis (begin/instrText/separate/end ohne Text).
    muster = re.compile(
        r'<w:hyperlink[^>]*w:anchor="_Toc1"[^>]*>.*?PAGEREF _Toc1.*?</w:hyperlink>',
        re.DOTALL,
    )
    funde = muster.findall(xml)
    if len(funde) != 1:
        print("Erwartet genau 1 _Toc1-Hyperlink-Duplikat, gefunden: %d — nichts geändert." % len(funde))
        return 1
    if "fldCharType=\"separate\"" not in xml[: xml.find(funde[0])][-400:]:
        print("Duplikat liegt nicht hinter dem TOC-separate — abbruch, nichts geändert.")
        return 1

    bereinigt = muster.sub("", xml, count=1)

    tmp = docx_path.with_suffix(".tmp.docx")
    with zipfile.ZipFile(docx_path, "r") as quelle, zipfile.ZipFile(tmp, "w", zipfile.ZIP_DEFLATED) as ziel:
        for eintrag in quelle.infolist():
            if eintrag.filename == "word/document.xml":
                ziel.writestr(eintrag, bereinigt)
            else:
                ziel.writestr(eintrag, quelle.read(eintrag.filename))
    shutil.move(str(tmp), str(docx_path))
    print("Entfernt: _Toc1-Hyperlink-Duplikat im Inhaltsverzeichnis von " + str(docx_path))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
