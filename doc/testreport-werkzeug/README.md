# Testreport-Werkzeug

Erzeugt den **Management-Testreport** als PDF (`doc/SDN-Leistung-LMG-Testreport.pdf`):
Titelseite mit Titelbild, Inhaltsverzeichnis mit echten Seitenzahlen (Zwei-Durchlauf
über pdftotext), fixe Erklärtkapitel (Unit-/Regressionstests, Fachschicht vs.
Selenium, Sollwert-Herkunft inkl. LLM-Rolle, echte Fälle, externer Online-Rechner,
parallele Transaktionssteuerung), dynamische Ergebnistabellen aus `testsweet/logs`
und den surefire-Reports sowie eine Zertifikatsseite am Ende.

## Aufruf

- Über die testsweet-GUI: Knopf **„📊 Testreport (PDF) erstellen"** (Endpunkt
  `POST /api/testreport`, PDF-Abruf `GET /api/testreport/pdf`).
- Direkt: `node publish-report.js --publish <repo>` (erzeugen und veröffentlichen)
  bzw. `node publish-report.js --verify <repo>` (prüft, ob die PDF die zuletzt
  erzeugte Fassung ist — veraltete Reports werden abgewiesen).

## Abgrenzung zum Testzertifikat

Der Report **informiert** (Management-Sicht, jedes Mal frisch, vereinfachte
Laufauswertung über `runs.js`); das **formale, beleggebundene Zertifikat** mit
Build-Bindung und Hashprüfung je Lauf bleibt das separate Werkzeug
`doc/testzertifikat-werkzeug` und wird hier nicht angetastet.

## Abhängigkeiten

- `docx` (npm) aus `../handbuch-werkzeug/node_modules`
- LibreOffice (`soffice.com`) für DOCX → PDF
- `pdftotext` für die Seitenzahl-Ermittlung des Inhaltsverzeichnisses
