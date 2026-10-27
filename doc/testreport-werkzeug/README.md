# Testreport-Werkzeug

Erzeugt den **Management-Testreport** als PDF und DOCX unter `doc/SDN-Leistung-LMG-Testreport.*`.
Der Report erläutert Prüfverfahren und zeigt ausschließlich den tatsächlich belegten Stand.
Die Schlussseite ist eine **Gesamtbewertung des erklärten Prüfumfangs**, kein zweites formales Testzertifikat.
Seitenzahl und Inhaltsverzeichnis entstehen aus dem tatsächlichen Satz; es gibt keine feste 12-Seiten-Zusage.

## Aufruf und Aktualität

- GUI: **„Testreport (PDF) erstellen“**, `POST /api/testreport`; Abruf über `GET /api/testreport/pdf`.
- CLI: `node publish-report.js --publish <repo>`.
- Prüfung: `node publish-report.js --verify <repo>`.

`--verify` verlangt Manifestversion 2, passende PDF-/DOCX-Hashes und einen unveränderten Eingabebestand.
Es liefert `{state:"READY",reportId,pdfSha256,verdict}`; die HTTP-Schicht muss den Hash mit den tatsächlich
zurückgegebenen PDF-Bytes vergleichen. Ein neuer Lauf, geänderte JUnit-/GLM-Belege, Quellen, Klassen/JARs,
Reportwerkzeug oder Fachbelege entwerten die alte Momentaufnahme. Die Reportausgaben und ihr eigenes Manifest
gehören nicht zu den Eingaben. Historische Marker ohne Eingabebindung erfordern einen neuen Export.
Ein Eingabestand wird vor Erhebung und bis nach dem Export kontrolliert; Änderungen brechen den Export ab.

Der Export verwendet eine eigene Prozesssperre und veröffentlicht den Manifestmarker zuletzt.
Nach einer normalen Ausnahme werden Staging und Officeprofil aufgeräumt. Ein nachweislich beendeter
Exportprozess blockiert den nächsten Export nicht dauerhaft. Nicht lesbare Sperren, fehlende Rechte
oder eine unterbrochene Sperrprüfung werden mit einer konkreten Fehlermeldung zur manuellen Prüfung
abgewiesen; eine lebende, nur langsame Exportprozess wird nicht als verwaist behandelt.

## Erklärter Umfang und Urteil

- Fachschicht: POSITIV, NEGATIV, ECHT, RECHNER, LAST, STRESS.
- Weboberfläche: POSITIV, NEGATIV, LAST.
- Laufgebundene JUnit-Prüfung beider Mavenmodule.
- Aktuelle, vollständig belegte GLM-Stichprobe aus POSITIV/NEGATIV; ECHT/RECHNER sind darin nicht enthalten.

**BESTANDEN** verlangt PASS auf allen zwölf erklärten Ebenen. Bekannte Fehler haben Vorrang und ergeben
**NICHT BESTANDEN**; fehlende, laufende, abgebrochene oder veraltete Pflichtnachweise ergeben
**UNVOLLSTÄNDIG**. JUnit verwendet dieselbe strikte Auswertung wie das gesonderte Zertifikatwerkzeug:
laufgebundene XML-Dateien, Testinventar und Buildbindung; übersprungene Pflichtprüfungen sind kein PASS.

Der jüngste Simulatorlauf wird aus eindeutigen Zeitstempeln mit Offset bestimmt, nicht aus dem zufälligen
Verzeichnissuffix. Bei zeitlicher Mehrdeutigkeit bleibt ein bekannter Fehler sichtbar; ein mehrdeutiger PASS
ist nicht vollständig bestätigt. Fach-/SQL-Fehler mit null erfolgreichen Verifikationen bleiben Fehler.
Defekte/unzugeordnete aktuelle Läufe werden sichtbar; eindeutig ältere unzugeordnete Versuche können durch
eine später vollständig erneuerte Pflichtmatrix abgelöst werden.

## Grenzen und Quellen

ECHT bezeichnet quellenbasierte Modellfälle mit offengelegten Ergänzungen, keine vollständige Prüfung
realer Leistungsbescheide. Beim KdU-Gesamtdeckel und bei Vermögensprivilegierungen gelten die dokumentierten
Modellgrenzen. RECHNER hat zwölf feste Ergebnissperren; die neue externe Review-Stichprobe belegt die Fälle
1, 6 und 12. Die ursprüngliche Zwölf-Fälle-Erhebung ist nicht für jeden Fall durch Eingabe-/Ergebnisbelege
nachgewiesen. Referenzteilwerte und unabhängig festgelegte Endbeträge werden getrennt beschrieben.

Das formale, beleggebundene Zertifikat bleibt `doc/testzertifikat-werkzeug`. Ein grüner Managementreport
ersetzt es nicht. Die Kapiteltexte beschreiben Verfahren; sie bescheinigen keine nicht belegte Durchführung.

## Abhängigkeiten und Regressionen

- `docx`, `xml-js` und `jszip` aus `../handbuch-werkzeug/node_modules`.
- LibreOffice (`soffice.com` oder `soffice`) für DOCX → PDF.
- `pdftotext` für die Kapitelstartseiten. Alle Anker müssen eindeutig und geordnet vorhanden sein;
  der zweite Satz muss dieselben Kapitelstartseiten liefern, sonst wird nicht veröffentlicht.
- `node --test doc/testreport-werkzeug/report-tools.test.js` aus dem Repository-Stamm prüft
  Ergebnisurteil, Chronologie, Lifecycle, JUnit-Bindung, Input-/PDF-Integrität, TOC- und Prozessfehlerpfade.
