# Benutzerhandbuch erzeugen

Der Generator aktualisiert das bestehende Handbuch als
`doc/SDN-Leistung-LMG-Benutzerhandbuch.docx`. Die Gliederung mit Anwenderteil und
technischem Teil bleibt erhalten; Kapitel 21 beschreibt testsweet, GLM und Zertifikat.
Die zugehörige PDF wird aus derselben DOCX gerendert und visuell geprüft.

## Abhängigkeiten

Node: `npm ci` in diesem Verzeichnis. `generate.js` verwendet `docx` und `image-size`.
In Codex wird `CODEX_DOCUMENT_NODE_MODULES` auf das vom Dependency-Loader gelieferte
Paketverzeichnis gesetzt und der dortige Node-/Python-Runtime verwendet.
Der explizite Paketpfad hat Vorrang vor einem lokalen `node_modules`.

Python: `pypdf` für Kapitel-Seitenzahlen, `matplotlib` für die Diagrammquellen.
LibreOffice und Poppler werden für DOCX→PDF→PNG benötigt.

## Ablauf

1. Texte in `teil1.js`, `teil2.js`, `testsweet.js` bearbeiten.
2. Bei Diagrammänderungen `diagramme_erzeugen.py` im Verzeichnis `doc/manual-bilder`
   ausführen. Historische Screenshots sind im Dokument ausdrücklich gekennzeichnet.
3. `node generate.js` ausführen, dann
   `python fuszeilen_patchen.py ../SDN-Leistung-LMG-Benutzerhandbuch.docx`.
4. Die DOCX mit dem Dokumentenrenderer als PDF und vollständige Seitenbilder rendern.
5. `python inhaltsverzeichnis_erzeugen.py <gerenderte-PDF>` erzeugt die tatsächlichen
   arabischen Kapitel-Seitenzahlen in `inhaltsverzeichnis.json`.
6. Erneut bauen und rendern; Seitenzahlen so lange abgleichen, bis sie unverändert
   bleiben. Das Inhaltsverzeichnis besitzt gedruckte Werte und benötigt keine
   manuelle Word-Feldaktualisierung, um in der PDF sichtbar zu sein.
7. Jede Seite visuell prüfen. Abschließend die geprüfte PDF nach
   `doc/SDN-Leistung-LMG-Benutzerhandbuch.pdf` kopieren.

Testzahlen werden nicht aus alten Handbuchständen übernommen. Die aktuellen
quellengebundenen Prüfnachweise und das Zertifikat sind dafür maßgeblich.
