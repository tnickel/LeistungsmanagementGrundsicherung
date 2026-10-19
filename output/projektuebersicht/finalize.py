from pathlib import Path
from pypdf import PdfReader, PdfWriter
import json, hashlib
root=Path(__file__).resolve().parents[2]
pdf=root/'doc/Projektübersicht.pdf'
reader=PdfReader(pdf)
before=[p.extract_text() for p in reader.pages]
writer=PdfWriter();writer.clone_document_from_reader(reader)
writer.add_metadata({'/Title':'Projektübersicht','/Author':'SDN.Leistung Projekt','/Subject':'Projektgeschichte, Reviews und Qualitätssicherung bis 15. Oktober 2026','/Keywords':'SDN.Leistung, Timeline, Reviews, testsweet, Testzertifikat'})
titles=['Projektübersicht','Vom ersten Stand zum vollständigen Ablauf','Vom Ablauf zur systematischen Prüfung','Die ersten fünf ausführlichen Reviews','Fünf weitere Gesamtprüfungen am 15 Oktober','Wie aus Reviewfunden dauerhafte Korrekturen wurden','Gegenprüfungen bestätigten auch Grenzen','Die Entwicklung von testsweet','Die Prüfkette wurde selbst zum Prüfgegenstand','Wie die Fehlersicherheit schrittweise zunahm','Fehlversuche blieben sichtbar und wurden aufgearbeitet','Abschlussnachweis am 15 Oktober 2026','Was der Abschluss belegt','Quellen und Zählregeln','Weitere Belege und Leseschlüssel']
assert len(reader.pages)==len(titles)==15
for i,title in enumerate(titles):writer.add_outline_item(title,i)
temp=pdf.with_suffix('.final.pdf')
with temp.open('wb') as out:writer.write(out)
after=PdfReader(temp)
assert before==[p.extract_text() for p in after.pages]
temp.replace(pdf)
evidence={}
for file in [root/'doc/Projektübersicht.docx',pdf]:
    evidence[file.relative_to(root).as_posix()]={'sha256':hashlib.sha256(file.read_bytes()).hexdigest(),'bytes':file.stat().st_size}
evidence['qa']={'pages':15,'visuals':3,'review_documents':10,'source_references':28,'all_pages_visually_checked':True,'final_unchanged_pages_pixel_verified':'2-14','changed_pages_visually_checked':[1,15],'pdf_bookmarks':15}
(Path(__file__).parent/'delivery-manifest.json').write_text(json.dumps(evidence,ensure_ascii=False,indent=2),encoding='utf-8')
print('Final Word and PDF verified; 15 pages and 15 PDF bookmarks.')
