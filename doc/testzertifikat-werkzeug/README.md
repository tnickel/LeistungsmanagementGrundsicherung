# Testzertifikat SDN.Leistung LMG — Werkzeugordner

Stand: 2026-10-13. Das Werkzeug fasst vorhandene Prüfbelege zusammen. Eine erfolgreiche
Dateiübergabe an ein Modell und eine bestandene fachliche Nachprüfung sind getrennte Aussagen.

| Datei | Zweck |
|---|---|
| `generate.js` | Erzeugt das DOCX und `stand.json`; liest aktuelle Laufresultate und strukturierte echte LLM-Nachweise. |
| `llm-evidence.js` | Prüft Quellen, Manifest, SHA-256, gesendete Dokumenttexte, Request-/Response-Belege, Modell und genaue Fallabdeckung. |
| `erstelle-zertifikat.bat` | Erzeugt DOCX, exportiert über LibreOffice als PDF und kopiert die Ausgaben nach `doc/`. |
| `gesetzesgrundlagen-2026.md` | Gesetzesgrundlagen mit Fundstellen; wird bei der echten LLM-Prüfung vollständig als Dokumenttext gesendet. |
| `llm-pruefauftrag.txt` | Fachlicher Prüfauftrag; die Anwendung ergänzt das verbindliche strukturierte Antwortschema. |
| `nachrechnen_100.py` | Optionale deterministische Python-Zweitimplementierung. Kein Modellaufruf; Gesetzesdatei und Prompt werden zur Laufzeit nicht gelesen. |
| `llm-nachrechnung-<Datum>*.md` | Historische Freitext-/Python-Protokolle; gelten nicht als Nachweis einer echten GLM-API-Prüfung. |
| `stand.json` | Automatisch erhobene Lauf-/LLM-Daten und Warnungen der Zertifikatserstellung. |

## Ablauf

1. Hauptprojekt prüfen und installieren, anschließend die eigenen testsweet-Tests ausführen:
   ```powershell
   mvn test
   mvn install -DskipTests
   mvn -f testsweet/pom.xml test
   ```
   Stand der testsweet-Verifikation: **55/55 Tests grün**. Die im Zertifikatsgenerator
   genannte Hauptprojekt-Testzahl und Web-Referenzläufe sind ausdrücklich manuell gepflegt.
2. In testsweet vollständige erfolgreiche POSITIV- und NEGATIV-Läufe erzeugen; LAST/STRESS
   ergänzen die Laufnachweise, liefern aber keine einzelnen Fälle für die LLM-Nachrechnung.
3. Über das Zahnrad der testsweet-GUI Endpunkt, Modell und Zugang konfigurieren.
   Standard: `glm-5.3`, `https://api.z.ai/api/coding/paas/v4`, 180 s je Anfrage,
   16000 Antworttokens, 3 Fälle je Batch. Zulässig sind **2–1000 ausgewählte Fälle** und
   **1–10 Fälle je Batch**. „Verbindung zu GLM testen“ ist ein eigener kleiner echter
   API-Aufruf und keine fachliche Prüfung.
4. Abschnitt 7: Prüfauftrag/Prüfpaket ansehen und **„GLM-5.3-Prüfung starten“**.
   Die Anwendung wählt abwechselnd vollständige Positiv- und Negativfälle einschließlich
   kuratierter Arten aus den neuesten erfolgreichen Berichten. Je Batch werden Prüfauftrag,
   Gesetzesgrundlagen und Fallgruppe als drei benannte Texte im tatsächlichen Request
   gesendet. Nicht verfügbare Fallzahlen führen zum Abbruch, nicht zu einer stillen Kürzung.
5. Ergebnis und Reportanhang prüfen. **Dateiübergabe bestätigt** benötigt vollständige
   Transportbelege; fachliches **PASS** benötigt zusätzlich jede angeforderte Fall-ID,
   prüfbare Einzelbegründungen und keine Befunde/ungeprüften Fälle. Antworten mit falschem
   Modell, fehlenden IDs, abgeschnittenem Abschluss, API-Fehlern oder ungültiger Quellenbindung
   ergeben keine fachliche Freigabe. Die Fallauswahl bleibt eine Stichprobe.
6. **„Testzertifikat erstellen“** oder `erstelle-zertifikat.bat` ausführen. Die GUI verlangt
   zuvor eine gültige abgeschlossene LLM-Prüfung mit `PASS`. Der Generator berücksichtigt
   auch fehlgeschlagene neueste Simulatorläufe und gibt fehlenden, veralteten oder
   widersprüchlichen Belegen kein grünes Gesamturteil. Er startet weder Python noch GLM
   als verdeckten Ersatz für eine fehlende Prüfung.

## Nachweise und Quellenbindung

Je echte LLM-Prüfung entsteht `testsweet/logs/llm-review-<UUID>/` mit:

- `manifest.json`: Review-ID, Modell, Quellenpfade/-läufe/-kategorien, ausgewählte Fall-IDs,
  Dokument- und Batch-Metadaten mit Bytezahlen und SHA-256.
- `source-POSITIV.md` / `source-NEGATIV.md`: unveränderte Snapshots der vollständigen
  Quellberichte; dazu Prüfauftrag, Gesetzesgrundlagen, `pruefmenge.md` und die Batch-Dateien.
- `request-N.json` / `response-N.json`: tatsächliche Anfragekörper ohne Authentifizierungsheader
  und Modellantworten; etwaige Schlüssel-Echos in Antworten werden vor der Ablage bereinigt.
  API-Request-ID, HTTP-Status, Modellkennung und Antwortvalidierung stehen im Transportnachweis.
- `result.json` / `protocol.md`: strukturierte Einzelurteile, Rechenbegründungen/Befunde und
  Gesamtergebnis. Der Generator validiert diese gegen die gespeicherten Antworten.
- `testreport-ki-anhang.pdf` / `.md`: separater Nachweis mit getrenntem Transport-/Fachstatus,
  Dokumentnamen, Hashes, Bytezahlen, Batch-/Review- und API-Request-IDs.

Wenn der gebundene Simulatorlauf noch im Speicher vorliegt, entstehen zusätzlich neue
`testdokumentation-geprueft.pdf` / `.md` im Laufordner. Die ursprünglichen Dateien werden
wegen ihrer Hashbindung nicht verändert. Nach einem Neustart bleibt der eigenständige
Anhang über `/api/llm/report/pdf` verfügbar. Änderungen an Quelldateien, Prüfauftrag oder
Gesetzesgrundlagen sowie neuere erfolgreiche Quellläufe machen einen älteren Nachweis ungültig.

Der API-Schlüssel liegt optional in der Git-ignorierten Datei `testsweet/llm.local.properties`;
alternativ wird er aus `SWEET_LLM_API_KEY` oder `GLM_API_KEY` gelesen. Die Konfigurations-API
gibt ihn nicht zurück. Die lokale Datei ist keine verschlüsselte Schlüsselablage.

## Verifizierte Referenz und historische Abgrenzung

Am **2026-10-13** wurden **6 ausgewählte Positiv-/Negativfälle in 2 echten GLM-5.3-Batches**
geprüft: `PASS`, Dateiübergabe bestätigt. Lokaler, nicht versionierter Nachweis:
`testsweet/logs/llm-review-9f7fb043-be1b-4c33-aae4-4c417fd50da4/`.
Diese Referenz belegt genau die ausgewählten Fälle und den damals gebundenen Quellenstand.

Die ältere Angabe **100 Fälle / 108 Rechenblöcke** beschreibt eine deterministische
Python-Nachrechnung. Eine Modellbezeichnung in ihrem historischen Freitextkopf belegt
keinen GLM-Aufruf. Diese Protokolle werden deshalb vom aktuellen Generator nicht als
echte LLM-Nachprüfung anerkannt.
