# Gegenprüfung des Antigravity-Berichts m9x2

Gegenstand ist der eingefügte Bericht und seine unveränderte Repository-Kopie
`grundsicherung-gesamt-review-2026-10-15-m9x2.md`, geprüft gegen den aktuellen
Arbeitsstand auf `main`, Basiscommit `37abfb6`. Frühere lokale Reparaturen bleiben
erhalten. Fremde Testzahlen und pauschale Freigabeaussagen werden nicht als eigene
Nachweise übernommen.

## Bewertung der Befunde

| Gegenstand | Gegenprüfung | Konsequenz |
|---|---|---|
| GR-20261015-01: feste Testidentitäten | Berechtigt: feste BG-IDs, Aktenzeichen und ein gemeinsamer Saga-Typ ermöglichen Kollisionen zwischen Testprozessen. Der Prozess-Kill-Test konnte zudem eine fremde IN_FLIGHT-Saga als Bereitschaft des eigenen Kindes missverstehen. | Sequenz-IDs, UUID-Aktenzeichen und eigene Saga-Korrelation; gezielte Bereinigung und begrenztes Warten auf die Freigabe nach dem Kill. |
| Behauptete Simulator-Ursache in GR-01 | Nicht belegt: Die Fehlerlogs zeigen fehlende Zeilen, identifizieren aber keinen löschenden Prozess. Der Simulator bereinigt `SWEET-%`, nicht das behauptete `BG-SIM-%`; das erfasst die bisherigen `BG-DEADLOCK`-/`BG-KILL`-Aktenzeichen nicht. Ein noch gesperrter Saga-Kopf allein erklärt keine verschwundene bereits committete BG. | Die tatsächlichen Isolationslücken werden behoben, ohne eine unbelegte Ursache als erwiesen darzustellen. |
| GR-20261015-02: fehlender Bescheid | Die vorgeschlagene Reparatur ist falsch. `KEIN_BESCHEID` ist kein existierender Fachfehler. Ohne bisherigen Bescheid erlaubt das Produkt seinen dokumentierten Demopfad; sobald einer existiert, greifen Deckung und Monatsbetrag. TF29 durfte daher die Zahlung erzeugen. | Kein erfundener Fehlercode und kein pauschales PASS bei null Zahlungen. Die übertragenen Produktregeln werden eindeutig formuliert. |
| TF68: drei Journalzeilen bei fünf Schritten | Berechtigte Nachweislücke: Die Zahl ist korrekt, die Zuordnung fehlte im übertragenen Material. Vorgang, Person und erfolgreiche Mietänderung schreiben je eine Zeile; beide abgewehrten Schritte schreiben keine. | Die Prüfdokumente erläutern Journalwirkungen und den unveränderten Zustand je abgewehrtem Schritt. Erfolgreiche Basisaktionen eines Negativfalls dürfen Daten verändern. |
| Zertifikatsabbruch nach 200 GLM-Fällen | Kein erfolgreicher GLM-Nachweis: `7b1ef5e5-a3d8-47e1-991d-3c29f1f0c1a5` endete UNVERIFIED. Die Produkt-JAR wurde während der Prüfung ersetzt. 259 Quellen blieben identisch; auch 256 weitere Belegprüfungen ergaben keine Abweichung. | Die Prüf-Runtime wird laufgebunden kopiert. Änderungen der weiterhin benötigten Originalnachweise führen früh und mit konkreter Ursache zum Abbruch. Die Hashbindung wird nicht gelockert. |

Der frühe TF29-FAIL stammte aus einer formal ungültigen Modellantwort, die eine
korrekt übertragene Bedingung ignorierte. Die spätere gültige Antwort ist PASS.
Der frühere Hinweis bleibt ausdrücklich als unbestätigt erhalten. Der zusätzliche
`REFERENCE_2026.workflowUnverified`-Eintrag bei TF68 übernimmt dieselbe
Modellbewertung und ist kein zweiter unabhängiger Befund.

## Grenzen und sachliche Fehler im Fremdbericht

- Tests beweisen die konkret ausgeführten Abläufe; 100 Node- und elf Python-Tests
  bestätigen keine vollständige Parität oder allgemeine Fehlerfreiheit.
- Der Antwortvertrag ist Version 2, nicht die in der Matrix behauptete Version 3.
  Das Bescheid-Snapshotformat v3 ist ein anderer Vertrag.
- Der zitierte Zertifikatslauf hat kein grünes Gesamtzertifikat erzeugt. Die
  1.392 Simulatorfälle waren bestanden; die GLM-Prüfung blieb unverifiziert.
- Die aktuelle Entwicklerdatenbank ist Oracle AI Database 26ai Free 23.26.3.0.0;
  die Bezeichnung 23ai beschreibt nicht den hier verwendeten aktuellen Stand.
- Die Zwölfmonatsgrenze ist eine Produktvalidierung. Das Gesetz nennt zwölf Monate
  als Regelfall, keine ausnahmslose Höchstfrist:
  [§ 44 Abs. 3 SGB XII](https://www.gesetze-im-internet.de/sgb_12/__44.html).
- § 73 SGG betrifft die Prozessvertretung; die Widerspruchsfrist steht in
  [§ 84 SGG](https://www.gesetze-im-internet.de/sgg/__84.html), siehe auch
  [§ 73 SGG](https://www.gesetze-im-internet.de/sgg/__73.html).
- Die Angabe eines letzten SGB-XII-Änderungsstands von 2023 ist überholt. Die
  [amtliche Gesamtausgabe](https://www.gesetze-im-internet.de/sgb_12/BJNR302300003.html)
  nennt bei dieser Gegenprüfung eine Änderung vom 24.07.2026. Daraus folgt keine
  pauschale Bestätigung, dass sämtliche Fachpfade gegen jede Änderung geprüft sind.
- Die Daten der beiden Vermögensurteile sind im Bericht falsch: B 8 SO 20/11 R
  stammt vom **20.09.2012**, B 4 AS 29/17 R vom **25.04.2018**. Die offizielle
  [BSG-Entscheidung von 2018, Rn. 19–20](https://www.bsg.bund.de/SharedDocs/Downloads/DE/Entscheidungen/2018/2018_04_25_B_04_AS_29_17_R.pdf?__blob=publicationFile&v=2)
  verweist ausdrücklich auf das Urteil von 2012 und berücksichtigt tatsächlich
  vorhandenes verwertbares Vermögen. Die automatische Verbrauchsfortschreibung
  des Demomodells ersetzt keinen tatsächlichen Verbrauchsnachweis. Dies ist im
  maßgeblichen Rechtsquellenverzeichnis jetzt ausdrücklich abgegrenzt.
- Objektbezogene Zuständigkeiten und eine nachweisgebundene Vermögenshistorie
  bleiben bekannte Erweiterungen des Fachmodells. Ein Zugriffsprotokoll ersetzt
  keine Zugriffsbeschränkung. UUIDs in Tests führen weder Teamrechte noch eine
  rechtlich abgesicherte Vermögenshistorie ein. Es erfolgt keine Produktivfreigabe.

## Eigene Verifikation

Endgültige zentrale Prüfung am **15.10.2026, 17:36–17:40 Uhr Europe/Berlin** auf
dem eigenen Schema `AG_FIX_20261015` in Docker `lmg-oracle`, PDB `FREEPDB1`, mit
einem eigenen Maven-Repository. Oracle AI Database 26ai Free 23.26.3.0.0,
Temurin 25.0.4.1, Compilerziel 21. Vor den Builds war kein Zertifikatslauf aktiv.
Das bestehende Developer-Schema und die Produkt-JAR im normalen Maven-Repository
wurden nicht zurückgesetzt beziehungsweise ersetzt.

| Prüfung | Eigenes Ergebnis |
|---|---|
| Hauptprojekt `clean install` mit Oracle | **353 Tests**, keine Fehler, 1 vorgesehener Skip |
| Konfiguration ohne DB-Umgebung | **2/2** bestanden |
| testsweet `clean test` mit Oracle | **301/301**, keine Skips |
| Node-Gesamtprüfung | **139/139** |
| Python-Referenztests | **11/11** |
| Tatsächlicher Runtime-Helfer und frische Probe-JVM | **75 Classpath-Einträge** vollständig kopiert und gehasht; Produkt und testsweet tatsächlich aus der Laufkopie geladen, ursprüngliche JUnit-Guards gültig |
| Unveränderte unabhängige JUnit-Leser | Hauptprojekt PASS, testsweet PASS |
| Fachsimulator aus der Runtime-Kopie | **100/100 POSITIV + 100/100 NEGATIV**; keine Verifikationsfehler |
| Lokales Prüfpaket mit neuen Dokumenten | Genau **100 positive + 100 negative Fälle**, 200 eindeutige IDs; Journalherleitung enthalten |

Zusätzliche JUnit-Ausführungen gegenüber dem letzten 351/291-Stand: **2 im
Hauptprojekt**, **10 in testsweet**. Sie prüfen gleichzeitige Deadlockpaare und
Prozessabstürze, getrenntes Cleanup, tatsächliche Request-Dokumente, CodeSources
der kopierten Runtime, geänderte Originale, Start-/Manifestbindung und die
Belegprüfung bis nach dem Exporter-Ende.

Die erste eigene Prüfung fand zwei nacheinander korrigierte Fehler in der neuen
lokalen Provider-Testfixture: unaufgefüllte Fall-IDs sowie eine Sollalternative
als angeblichen Istwert. Produktvalidator und FAIL-Assertions wurden nicht
aufgeweicht. Die unabhängige Code-Gegenprüfung fand zusätzlich das fehlende
Guardfenster am Exportende. Diese Korrekturen wurden vor der abschließenden
Gesamtprüfung eingebaut; Fehlversuche bleiben unter `first-attempt/` und
`second-attempt/` im lokalen Belegverzeichnis erhalten.

Gemeinsamer Quellenfingerabdruck aller endgültigen Java- und Simulatornachweise:
`0a51074d40ecac9837d5452601474f91c3b5080bd9d46787022a6f4275876d72`.
**262 Quelldateien nachgehasht, 0 Abweichungen**. Auch archivierte JUnit-XML,
Runtime-Kopien und Simulatorberichte wurden anhand ihrer Hashes geprüft.
Kompakter Nachweis:
[verification-summary.json](antigravity-gegenpruefung-2026-10-15-belege/verification-summary.json).
Skripte und vollständige lokale Logs: `tmp/review-antigravity-fixes-20261015/`.

Die Nachkontrolle fand keine eigenen Kill-/Deadlock-Fixtures und null aktive
Sitzungen. Danach wurden ausschließlich das eigene Schema und seine temporäre
Zugangsdaten-Datei entfernt. Die für die Belegprüfung benötigten Runtime-Dateien
und das eigene Maven-Repository bleiben erhalten.

**Grenzen:** Kein neuer externer GLM-Aufruf, kein vollständiger neuer Sieben-Modi-
Zertifikatslauf und kein neues grünes Gesamtzertifikat. Die lokalen Provider-
Antworten prüfen Transport und Validierung, ersetzen kein unabhängiges Modellurteil.
Oracle 19c EE und eine Java-21-Laufzeit wurden nicht ausgeführt. Parallel ersetzte
Original-JUnit-/Buildnachweise entwerten einen Zertifikatslauf weiterhin korrekt;
die neue Runtime schützt dessen Klassen und ermöglicht einen frühen Abbruch.
Eine bereits laufende testsweet-GUI muss für die neue Steuerungslogik neu gestartet
werden. Änderungen sind lokal, noch nicht committet oder gepusht.
