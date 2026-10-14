"""Ermittelt Kapitel-Seitenzahlen aus dem gerenderten Handbuch; ohne Word-Feldabhängigkeit."""
import json
import re
import sys
from pathlib import Path
from pypdf import PdfReader

base = Path(__file__).resolve().parent
titles = []
for file in ['teil1.js', 'teil2.js']:
    source = (base / file).read_text(encoding='utf-8')
    if file == 'teil2.js':
        source = source.replace('inhalt.push(...require("./testsweet"));', (base / 'testsweet.js').read_text(encoding='utf-8'))
    titles.extend(re.findall(r'h1\("([^"\n]+)"\)', source))
reader = PdfReader(sys.argv[1])
pages = reader.pages
texts = [page.extract_text() for page in pages]
def norm(text):
    return re.sub(r'\s+', '', text).casefold()
entries = []
for title in titles:
    found = []
    for text in texts:
        # Das Inhaltsverzeichnis selbst enthält die Titel ebenfalls; nur arabische Hauptseiten nutzen.
        lines = text.strip().splitlines()
        footer = lines[1].strip() if len(lines) > 1 and lines[0].startswith('SDN.Leistung LMG') else ''
        if footer.isdecimal() and norm(title) in norm(text):
            found.append(int(footer))
    if not found:
        raise ValueError('Kapitel im PDF nicht gefunden: ' + title)
    entries.append({'title': title, 'page': min(found)})
(base / 'inhaltsverzeichnis.json').write_text(json.dumps(entries, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
print(f'{len(entries)} Kapitel-Seitenzahlen ermittelt')
