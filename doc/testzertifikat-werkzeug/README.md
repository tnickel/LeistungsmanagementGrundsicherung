# Testzertifikat SDN.Leistung LMG

Das Zertifikat bewertet aktuelle Prüfbelege. Eine erfolgreich erzeugte PDF ist keine
fachliche Freigabe. Das Deckblatt kennt drei Gesamturteile:

- **BESTANDEN:** Alle erforderlichen Schichten sind vollständig, aktuell und bestanden.
- **NICHT BESTANDEN:** Mindestens ein fachlicher, SQL- oder automatischer Testfehler ist bekannt.
- **UNVOLLSTÄNDIG:** Pflichtbelege fehlen, Prüfungen wurden abgebrochen/übersprungen oder
  Quellen, Builds, Protokolle und Exporte sind nicht vollständig gebunden.

Ein bekannter fachlicher Fehler hat Vorrang vor einem späteren technischen Abbruch.
Auch negative und unvollständige Zertifikate werden erzeugt; GUI und Batch verwenden
dieselbe Exportpipeline. Historische Reviewabschlüsse erzeugen keine statischen grünen Haken.
Lesbare Befundsignale bekannter Fälle aus formal ungültigen Modellantworten bleiben
als unverifizierte Hinweise mit Versuch, Fall-ID, Diagnose und Response-Datei erhalten.
Eine spätere PASS-Antwort klärt diese Hinweise nicht automatisch: Das Gesamturteil
bleibt UNVOLLSTÄNDIG. Solche Hinweise sind keine bestätigten Softwarefehler.

## Pflichtumfang

| Schicht | Erforderlich |
|---|---|
| Hauptprojekt-JUnit | Vollständiges Klassen- und Methodeninventar, keine offenen Fehler oder Skips |
| testsweet-JUnit | Vollständiges Klassen- und Methodeninventar, keine offenen Fehler oder Skips |
| Fachschicht | POSITIV, NEGATIV, LAST und STRESS getrennt |
| Weboberfläche | POSITIV, NEGATIV und LAST getrennt |
| Web-STRESS | Nicht unterstützt; ausdrücklich NICHT ANWENDBAR |
| GLM | Aktuelle strukturierte, quellengebundene Fallstichprobe mit vollständiger API-/Zahlen-/Prüfpunktabdeckung |

Je Modus/Kategorie zählt der neueste **begonnene** Simulatorlauf. Ein späterer Fachlauf
verdeckt keinen Webfehler. Ein neu gestarteter RUNNING-/Fehlerlauf verdrängt ältere
Erfolge. Nicht zuordenbare neuere Laufverzeichnisse verhindern eine vollständige
Freigabe, bis die gesamte Pflichtmatrix danach erneut geprüft wurde.

## Ablauf für einen eingefrorenen Softwarestand

1. Hauptprojekt mit Tests bauen und installieren:

   ```powershell
   mvn install
   ```

   Für DB-Prüfungen muss `TX_URL` auf die Testdatenbank zeigen. Im Oracle-Gesamtlauf
   übersprungene Konfigurationsprüfungen anschließend in einer separaten Shell ohne
   `TX_URL`, `TX_USER` und `TX_PASS` nachholen:

   ```powershell
   mvn -Dtest=LmgConfigTest test
   ```

   Die Auswertung vereint Testfallbelege nur bei identischen aktuellen Quell-/Buildständen.
   Ein späterer Skip löscht keinen zuvor erkannten Fehler. Ein neuer begonnener Testlauf
   ohne erfolgreichen Abschluss bleibt unvollständig. `install -DskipTests` ist kein
   Ersatz für den vollständigen Hauptprojektlauf.

2. Eigentests ausführen:

   ```powershell
   mvn -f testsweet/pom.xml test
   node --test doc/testzertifikat-werkzeug/certificate-evidence.test.js
   python -m unittest discover -s doc/testzertifikat-werkzeug -p test_nachrechnen.py
   ```

3. Alle sieben Simulator-Pflichtläufe durchführen. Jeder Lauf benötigt aktuelle
   `run-state.json`, vollständige Protokolle und neue PDF-/Markdown-Dateien mit gültigen Hashes.
4. GLM über das Zahnrad konfigurieren und in Abschnitt 7 starten. Prüfauftrag,
   Gesetzesgrundlagen und vollständige ausgewählte Fallgruppen werden als benannte
   Dokumenttexte gesendet. 2-1000 Fälle, 1-10 Fälle pro Fallgruppe. Formatfehler lösen
   höchstens drei identische Sendeversuche mit jeweils 30 Sekunden Pause aus.
   Fachliche Befunde werden erhalten und nicht durch Wiederholung verdeckt.
5. Das Zertifikat über die GUI oder `erstelle-zertifikat.bat` erstellen. Negative
   Ergebnisse bleiben sichtbar. Bei Befunden die genannten Fall-IDs, Rechenwerte,
   Prüfpunkte und Laufprotokolle auswerten; bei technischen Exportfehlern zusätzlich
   `testsweet/logs/certificate-*/export.log` prüfen.

Code-, Prompt-, Gesetzes-, Test- oder Buildänderungen nach diesen Prüfungen entwerten
den Nachweis als aktuellen Stand. Alte Reports und Zertifikate bleiben historische Dateien.

## Werkzeuge

| Datei | Aufgabe |
|---|---|
| `generate.js` | Erzeugt DOCX und `stand.json` ausschließlich in einem neuen Ausgabeordner; keine Bestandsdateien überschreiben. |
| `certificate-evidence.js` | Gemeinsame Auswertung: JUnit/XML, Methoden-/Klasseninventar, Pflichtmatrix, Lifecycle, Build und Gesamturteil. |
| `llm-evidence.js` | Prüft echte API-Artefakte, Dokumentbytes, Quellen und exakte strukturierte Fallabdeckung. |
| `llm-reference.js` | Unabhängige centgenaue Referenz 2026; leitet Abdeckungsanforderungen erneut aus eingefrorenen Falltexten ab. |
| `llm-response-contract.json` | Gemeinsamer Antwortvertrag für Java und Node. Schema 1 gilt als historisch, nicht als aktuelle Freigabe. |
| `publish-certificate.js` | Gemeinsamer GUI-/Batch-Export mit Exportsperre, frischem Staging, Prozess-/Dateiprüfung und READY-Marker als letztem Veröffentlichungsschritt. |
| `certificate-evidence.test.js` | Kontrollierte Fehler-/Abbruch-/Build-/Modell-/Exportregressionen; keine echten API-Aufrufe. |
| `stand.json` | Ausgestelltes Urteil, Lauf-/Build-/Dateiinventar, Zertifikat-ID und Nachweis-Fingerprint. |
| `current-certificate.json` | READY/GENERATING/ERROR-Publikationsmarker. Ein älteres PDF ohne passenden READY-Marker ist kein aktuelles Zertifikat. |

Gemeinsame CLI-Aufrufe aus dem Projektstamm:

```powershell
node doc/testzertifikat-werkzeug/publish-certificate.js --publish
node doc/testzertifikat-werkzeug/publish-certificate.js --verify
node doc/testzertifikat-werkzeug/certificate-evidence.js
```

`--verify` verweigert veraltete oder veränderte Nachweise und Ausgabedateien.
Ein LibreOffice-/Kopierfehler veröffentlicht kein altes grünes PDF erneut. Der
veröffentlichte Stand und alle Ausgabedateien werden erst nach erfolgreichem Export
als READY markiert. Der Server prüft diese Bindung vor dem aktuellen PDF-Abruf.

## Quellen- und Buildbindung

`tools/evidence-sources.txt` definiert das relevante Quellinventar.
`tools/BuildStamp.java` erzeugt Compilerstempel und beginnt/beendet Maven-Testnachweise.
JUnit-XML werden pro Lauf unter `target/test-evidence-history/` unveränderlich als
Snapshots gebunden. Ein fehlender FINISH-Hook verdeckt vorhandene Surefire-Fehler nicht.
Die lokale installierte Produkt-JAR, geladene Klassen, Prüfercode und Quellinventar
werden mit Hashes verbunden; gleiche Versionsnummern allein reichen nicht.

Je gestartete GLM-Prüfung entsteht `testsweet/logs/llm-review-<UUID>/`:

- `started.json` und atomare `status.json`: Start und Zwischenstände, einschließlich bereits erkannter Befunde.
- `manifest.json`: Quellberichte, Laufstatus, Build, ausgewählte Fall-IDs, Vertrags-/Dokument-/Fallgruppenhashes und Referenzanforderungen.
- `source-POSITIV.md`, `source-NEGATIV.md`, Prüfauftrag, Gesetzesgrundlagen, Vertrag und Fallgruppen: unveränderte Snapshots.
- Request-/Response-Dateien je Sendeversuch: tatsächliche Anfragekörper ohne Authentifizierungsheader; Schlüssel-Echos werden bereinigt.
- `result.json` und `protocol.md`: Einzelurteile, Zahlen, SQL-/Workflowprüfpunkte, unabhängige Referenzbefunde und Abschlussstatus.
- PDF-/Markdown-Anhang: Transport und fachliches Urteil getrennt, inklusive Fall-ID, Abweichung, Rechnung und Dokumenthashes.

Ein Vorschaupaket ohne `started.json` ist kein begonnener Review. Ein neuer Review
ohne Abschluss verdrängt einen älteren PASS. Ein bloßer Begründungstext genügt nicht:
Rechenblöcke, Personen, Zwischenwerte und jeder dokumentierte SQL-/Workflowprüfpunkt
müssen exakt abgedeckt sein. Falsche eigene Modellzahlen ergeben UNVERIFIED;
nachgewiesene Dokument-/Softwareabweichungen oder Modellbefunde ergeben FAIL.
Unverifizierte Rohurteile werden ausdrücklich als solche dargestellt.

Die Hashes belegen die lokal gespeicherten gesendeten Bytes und empfangenen Antworten.
Sie beweisen keine interne Verarbeitung beim Modellanbieter. GLM ist eine dokumentierte
Stichprobe und keine allgemeine Garantie rechtlicher oder technischer Fehlerfreiheit.
Historische Python-/Freitextprotokolle sind keine GLM-API-Nachweise.

Der API-Schlüssel liegt optional in der Git-ignorierten `testsweet/llm.local.properties`
oder in `SWEET_LLM_API_KEY`/`GLM_API_KEY`. Die Konfigurations-API gibt ihn nicht zurück.
