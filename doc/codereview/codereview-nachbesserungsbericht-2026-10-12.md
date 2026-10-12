# Nachbesserungsbericht zur Gegenprüfung der Codereview-Fixes

Datum: 2026-10-12 · Zielversion: **0.5.8** · Verifikation: Windows, Java 21, Docker-Oracle Free.

## 1. Auftrag und Vergleichsstand

Die bisherigen Berichte `codereview-bericht-2026-10-12.md` und
`codereview-fixbericht-2026-10-12.md` waren Prüfmaterial. Ihre Aussagen wurden nicht
als Nachweis für die Korrektheit der Implementierung übernommen. Nach der Gegenprüfung
hat der Benutzer ausdrücklich die Behebung der gefundenen Probleme, zusätzliche Tests
und diesen Bericht zur unabhängigen Nachprüfung beauftragt.

Vergleichsbasis ist Commit `88566f9b49226c3ad23dd74360c8ca295c3ef9d0` (v0.5.7).
Die Änderungen werden auf ausdrücklichen Folgeauftrag des Benutzers einschließlich
dieses Berichts committet und gepusht. Nach dem Commit zeigt
`git diff 88566f9b49226c3ad23dd74360c8ca295c3ef9d0 HEAD` den gesamten Änderungssatz
einschließlich neuer Produktions- und Testdateien. Die nachfolgenden N-Nummern gehören zu dieser
Nachprüfung und ersetzen nicht die CR-Nummern des ursprünglichen Berichts.

## 2. Befunde und Korrekturen

| Nr. | Fehler vor der Korrektur | Umsetzung und maßgeblicher Prüfort |
|---|---|---|
| N01 | Wiederholung einer bestehenden Auszahlungs-Vorgangsnummer ignorierte abweichende BG/Monat und lieferte vollständige fremde Zahlungs-/Bankdaten ohne Einsichtsprotokoll. | `SozialLeistungsService.wiederholeAuszahlung` wird von beiden Idempotenzpfaden verwendet. BG, Monat sowie ausdrücklich angegebene normalisierte Bankdaten müssen zur gespeicherten Zahlung passen, sonst fachliche Abweisung. Erlaubter Abruf protokolliert Einsicht. `EinsichtsprotokollRegressionDbTest` prüft Service und echte HTTP-Aufrufe. |
| N02 | Saga rechnete Schwangerschaftsmehrbedarf auch nach bekanntem Entbindungsmonat weiter; der Service wies bisher sogar die gesamte Leistung ab. | Gemeinsame Monatsprojektion `Bewilligungsplan.faelleFuerMonat`: Nur der Schwangerschaftsmehrbedarf entfällt ab dem Folgemonat. Service-Auszahlung, Vorschau und Saga-Schritte verwenden diese Projektion. Falldaten werden dadurch nicht überschrieben. Tests prüfen 958,71 EUR im Entbindungsmonat und 863,00 EUR danach, bei 563 EUR Regelbedarf und 300 EUR KdU. |
| N03 | Mehrmonatiger Bescheid schrieb einen konstanten Monatsbetrag fest, obwohl Vermögenseinsatz, Schwangerschaftsende oder ein bekannter Satzwechsel die Folgemonate änderten. | `Bewilligungsplan.erstelle` berechnet jeden Monat mit gültigem Satzstand und simuliert einmaligen Vermögenseinsatz. Service und Saga speichern Snapshot v2. Druck, HTTP-API und Oberfläche zeigen dessen Monatsbeträge. Vermögen wird bei Bescheiderteilung nicht tatsächlich verbraucht. Calc-, Druck-, HTTP- und Oracle-Tests sichern das ab. |
| N04 | testsweet ordnete Browser über gemeinsame Ahnenprozesse zu und konnte fremde Selenium-Sitzungen beenden. | Neue `BrowserProzesse`: zufälliges registriertes Profil pro eigenem Fenster. Java-Auswahl und `raeume-fenster.ps1` vergleichen vollständigen Profilpfad; keine Ahnenheuristik. Windows liest Chrome-Kommandozeilen über CIM. `BrowserProzesseTest` prüft fremde Prozesse, ähnliche Pfade und Leerzeichen. |
| N05 | Saga-Folgeschritte verarbeiteten inzwischen historisierte Vorgänge weiter. | `AntragsSagaProzess.lockActiveBg` sperrt BG und prüft Aktivität vor Bedarfsprüfung, Bescheiderteilung und Zahlung. Anspruch wird vor Bescheid/Zahlung erneut geprüft; Zahlung verwendet aktuelle Bankdaten aus dem gesperrten Bestand. `FachlicheNachpruefungDbTest` prüft alle drei Schritte ohne weitere Mutation bei Historisierung. |
| N06 | Einsichts-Dedup wurde vor Commit im Speicher gesetzt; Rollback/Retry konnte den nächsten erfolgreichen Zugriff verdecken. Wiederholungen verschoben zudem das Zeitfenster. | Speicher-Cache entfernt. Unter BG-Sperre wird das persistierte Journal für gleiche BG, Kennung und Aktion der letzten 60 Sekunden geprüft. Prüfung und Eintrag gehören zur selben Transaktion. Das Fenster bezieht sich auf den letzten gespeicherten Eintrag. Tests injizieren Commitfehler und Retry und prüfen parallele Service-Instanzen. |
| N07 | Falländerung prüfte Aktivität ohne BG-Sperre und konnte eine konkurrierende Historisierung überholen. | Falländerung und Fallanlage verwenden BG-`FOR UPDATE`. Zusätzlich prüft der Kassenlauf die Aktivität nach Zahlungssperre erneut unter bis zum Commit gehaltenen BG-Sperren; nur aktive Vorgänge gehen in Menge und Summe ein. `HistorisierungSperrenDbTest` prüft echte konkurrierende Oracle-Verbindungen für alle drei Wege. |
| N08 | Globale Simulator-Prüffehler wurden nur geloggt; Berichte konnten trotzdem Erfolg melden. | Neue `GlobaleVerifikation` liefert Ergebnisobjekte, auch bei SQL-Ausfall. `Simulator` übernimmt sie in Laufphase und Auftragstatus. GUI, PDF, Markdown und CLI-Ergebnis berücksichtigen sie. Eine aggregierte Laufprüfung je Auftrag vermeidet quadratischen Speicherbedarf. `GlobaleVerifikationTest` prüft Abweichung, SQL-Ausfall und Erfolg. |
| N09 | Treiber-Neustart spielte einen bereits teilweise persistierten Auftrag von vorn und verlor bestätigten Kontext. | `WebMitarbeiter` behält Kontext und Schrittposition. Nur ausdrücklich sichere Login-/Suchaktionen sind einmal wiederholbar. Bei Sitzungsverlust während eines Schreibschritts wird wegen unklarem Commit-Ausgang nachvollziehbar abgebrochen; keine automatische Doppelanlage. Neuer Treiber steht für Folgeaufträge bereit. `WebWiederaufnahmeTest` prüft drei Fehlerabläufe mit kontrolliertem Treiber. |
| N10 | Vorschau benutzte den neuesten Satzstand, auch wenn er erst künftig galt. | `berechnungDefensiv` löst `YearMonth.now()` über `findeFuerMonat` auf. Ein nur künftig geltender Satzstand ändert die aktuelle Vorschau nicht. Oracle-Regression setzt einen Zukunftsstand innerhalb einer zurückgerollten Testtransaktion. |
| N11 | Erfolgreiche Bescheid-Druckantwort fehlte `Cache-Control: no-store`. | Header in der erfolgreichen HTML-Druckantwort ergänzt; `LmgServerHttpTest` prüft ihn mit und ohne Snapshot. |

## 3. Technische Entscheidungen für die Gegenprüfung

### Monatsplan und Rückwärtskompatibilität

- `BESCHEID.BERECHNUNG` bleibt CLOB; keine DDL-Migration erforderlich.
- Snapshot v1 bleibt lesbar. Neue Service-/Saga-Bescheide verwenden v2 mit
  `monate`, monatlicher Berechnung und angesetztem Vermögensbestand.
- `BESCHEID.ZAHLBETRAG` und das bisherige API-Feld `zahlbetrag` enthalten aus
  Kompatibilitätsgründen den Startmonatsbetrag. Bei v2 sind die Monatswerte im
  Snapshot verbindlich; API zusätzlich `monatsbetraege` und `zahlbetragVariabel`.
- Der Druck verwendet auch bei späterer Falländerung die gespeicherten Monatswerte
  und zeigt monatliche Berechnungsgrundlagen und Begründungen.
- Ohne bekannten Entbindungsmonat bleibt das Schwangerschaftskennzeichen wirksam.
  Ein bekannter Monat wird einschließlich dieses Monats berücksichtigt.
- Der Plan simuliert Vermögensverbrauch nur bei positiver Auszahlung. Reale
  Bestandsänderung bleibt Teil der Auszahlungstransaktion. Nichtpositive Folgebeträge
  erscheinen mit 0,00 EUR; ein Startmonat ohne Anspruch führt weiterhin zur Abweisung.
- Der kombinierte unabhängige Rechenfall im Unit-Test ergibt 458,71 EUR im Dezember
  (563 + 95,71 + 300 - 500) und 900,00 EUR ab Januar bei synthetischem Regelbedarf
  600 EUR und beendetem Schwangerschaftsmehrbedarf. Das ist ein Testsatz, keine
  Behauptung über gesetzliche Beträge 2027.

### Einsicht und Sperren

Die bestehende Option D bleibt bestehen: Es wurde kein Zuständigkeits-/Teammodell
eingeführt. Eine andere berechtigte Kennung darf denselben konsistenten Auftrag
erneut abrufen, der Abruf ist nun protokolliert. Parameterbindung verhindert das
stille Uminterpretieren einer Vorgangsnummer; sie ersetzt keine fachliche Zuständigkeit.

Die Journalprüfung verwendet `CAST(SYSTIMESTAMP AS TIMESTAMP) - INTERVAL '60' SECOND`
zum Vergleich mit `JOURNAL.ERSTELLT_AM` (TIMESTAMP). Der explizite Cast verhindert
die im Oracle-Test beobachtete implizite Zeitzonen-Konvertierung beim Vergleich.
Kein nichttransaktionaler Cache muss nach Rollback wieder repariert werden.

Die neuen Sperrtests halten eine Historisierung auf einer zweiten Verbindung
uncommittet. Der geprüfte Service muss nach beobachtetem Sperrversuch zunächst
blockieren und nach Commit der Historisierung abweisen bzw. keine Zahlung übergeben.
Die Kassenabfrage wird ausschließlich im Test auf den eigenen Testvorgang begrenzt,
damit keine fremden Zahlungen durch diesen Test verändert werden.

### Simulator

Die Prozessauswahl arbeitet ausschließlich auf registrierten eigenen Chrome-Profilen.
Die Windows-Argumenterkennung unterstützt ungequotete Pfade, gequotete Werte und ein
vollständig gequotetes `--user-data-dir=...`-Argument. Die Skript-Syntax wurde geparst,
die drei Argumentformen zusätzlich synthetisch geprüft; keine realen Prozesse beendet.

Treiber-Neustart bietet keine allgemeine Exactly-once-Garantie für beliebige
UI-Schreibvorgänge. Bei unbekanntem Commit-Ausgang ist ein sichtbar fehlgeschlagener
Auftrag das gewählte Ergebnis. Bestätigte Schreibschritte werden nicht wiederholt.

## 4. Neue und erweiterte Tests

**33 neue Tests:** 25 im Hauptprojekt, acht in testsweet.

| Testklasse | Neu | Zweck |
|---|---:|---|
| `BewilligungsplanTest` | 5 | Kombination Vermögen/Satzwechsel/Schwangerschaftsende, unbekanntes Ende, Grundanspruch, Nullbeträge |
| `BerechnungsSnapshotTest` | 1 | v2-Roundtrip einschließlich aller Monate |
| `BescheidMonatsplanTest` | 1 | Verfügung, Monatsbeträge und Berechnungsaufstellung aus Snapshot |
| `BescheidMonatsplanHttpTest` | 2 | Echte HTTP-GET/POST-Antworten mit Monatsliste und Altbescheid-Kompatibilität |
| `FachlicheNachpruefungDbTest` | 7 | Service-/Saga-Monatsverhalten, unveränderlicher Druck, Vermögensplan, Historisierung, Anspruchsverlust, Bankbestand, Zukunftssatz |
| `EinsichtsprotokollRegressionDbTest` | 6 | Commitfehler, Retry, festes Fenster, Instanzkonkurrenz, Replay-Parameter, HTTP-Audit |
| `HistorisierungSperrenDbTest` | 3 | Echte BG-Sperren bei Falländerung, Fallanlage und Kassenlauf |
| `BrowserProzesseTest` | 2 | Eigene/fremde Prozesszuordnung, exakte Pfade |
| `WebWiederaufnahmeTest` | 3 | Kontext nach Anlage, unklarer Commit, begrenzte Neustarts |
| `GlobaleVerifikationTest` | 3 | Fehlende Freigabe, SQL-Ausfall, erfolgreicher Lauf |

Bestehende `CodereviewRegressionDbTest`-Erwartung wurde fachlich korrigiert:
Nach Entbindungsende bleibt der Grundanspruch erhalten. Bestehende HTTP-Tests prüfen
zusätzlich den Druck-Cache-Header. Unabhängige Betragsassertionen ergänzen die
Simulator-Sollwerte, die teilweise dieselbe Produktionsberechnung verwenden.

`FachlicheNachpruefungDbTest` arbeitet auf echter Oracle-Verbindung mit abschließendem
Rollback; ein Test-Proxy unterdrückt Zwischen-Commits zur Isolation, insbesondere
globaler Zukunftssatzstände. Diese Klasse beweist SQL-/Berechnungsverhalten, nicht
Commit-Dauerhaftigkeit oder Sperrkonkurrenz. Dafür sind die getrennten Audit- und
Historisierungstests mit echten Transaktionsgrenzen zuständig.

## 5. Ausgeführte Verifikation

Der Hauptlauf `mvn clean test` gegen Docker-Oracle ergab **238 Tests, 0 Fehler,
0 Fehlversuche, 1 übersprungen**. Übersprungen war ausschließlich die Prüfung der
Konfigurationsdatei bei gesetztem `TX_URL`. Anschließend wurde `LmgConfigTest` ohne
DB-Umgebungsvariablen ausgeführt: **2/2 bestanden**, einschließlich des übersprungenen
Falls. Damit wurden alle 238 unterschiedlichen Haupttests erfolgreich ausgeführt.

Nach `mvn install -DskipTests` der Version 0.5.8 bestand testsweet `clean test` mit
**10/10**. Zusammen: **248 unterschiedliche Tests bestanden**. Der Install-Schritt
wiederholt die Tests nicht; er stellt das tatsächlich geänderte Hauptprojekt für
die separat gebaute Simulator-Abhängigkeit bereit.

Alle vier Fachschicht-Batchläufe meldeten `lauf_erfolgreich = true`, keine
fehlgeschlagenen Verifikationen und Exitcode 0. Auch die gespeicherten globalen
Prüfungen (Vorgangszahl und keine Übergabe ohne Freigabe) waren erfolgreich.

| Kategorie | Ergebnis | Lokaler Markdown-Nachweis unter `testsweet/logs/` |
|---|---|---|
| POSITIV | 24/24 verifiziert | `run-20261012-115051/testdokumentation.md` |
| NEGATIV | 12/12 erwartete Abweisungen verifiziert | `run-20261012-115138/testdokumentation.md` |
| LAST | 120/120 verifiziert | `run-20261012-115214/lastreport.md` |
| STRESS | 40/40 verifiziert, zwei Wellen über 20 Vorgänge | `run-20261012-115450/lastreport.md` |

Das sind zusätzlich **196 geprüfte Simulator-Ergebnisse**. Die entsprechenden
PDF-Berichte liegen jeweils daneben. Diese Laufartefakte sind lokal und Git-ignoriert.
Konsolenprotokolle liegen in `%TEMP%/lmg-nachpruefung-*.log`; die JUnit-XML-Ergebnisse
in `target/surefire-reports/` und `testsweet/target/surefire-reports/`.
Nach dem ergänzenden Konfigurationslauf ergeben die Hauptprojekt-XML-Dateien zusammen
238 Tests, 0 Failures, 0 Errors, 0 Skipped; sie enthalten somit Ergebnisse zweier
Aufrufe. Beide JavaScript-Dateien bestanden `node --check`, das PowerShell-Skript
die Syntaxprüfung und `git diff --check` meldete keine Fehler.

### Reproduktionsbefehle (PowerShell, Repository-Wurzel)

```powershell
$env:TX_URL = 'jdbc:oracle:thin:@//localhost:1521/FREEPDB1'
$env:TX_USER = 'tx_demo'
$env:TX_PASS = 'tx_demo'
mvn clean test
mvn install -DskipTests
mvn -f testsweet/pom.xml clean test
mvn -f testsweet/pom.xml compile exec:java '-Dexec.args=run 8 24 42 0 POSITIV'
mvn -f testsweet/pom.xml compile exec:java '-Dexec.args=run 4 12 42 0 NEGATIV'
mvn -f testsweet/pom.xml compile exec:java '-Dexec.args=run 10 120 42 0 LAST'
mvn -f testsweet/pom.xml compile exec:java '-Dexec.args=run 5 20 42 0 STRESS'
Remove-Item Env:TX_URL,Env:TX_USER,Env:TX_PASS -ErrorAction SilentlyContinue
mvn '-Dtest=LmgConfigTest' test
node --check src/main/resources/webapp/app.js
node --check testsweet/src/main/resources/sweet/app.js
git diff --check
```

Die Simulator-Befehle räumen vor jedem Lauf vorhandene `SWEET-`-Testdaten auf;
deshalb nacheinander und ausschließlich auf der Entwicklungsdatenbank ausführen.
STRESS mit 20 Ausgangsaufträgen erzeugt zwei Wellen und damit 40 Ergebnisse.

### Prüfgrenzen und offene Punkte

- Kein Selenium-End-to-End-Lauf mit echten Browserabstürzen und kein realer
  Prozessbeendigungstest. Die Wiederaufnahme ist mit kontrollierten Treibern geprüft.
- Kein erneuter Oracle-19c-EE-Lauf; diese Nachprüfung lief auf Docker-Oracle Free.
- Alte v1-Bescheide werden nicht rückwirkend umgeschrieben. Bereits erteilte
  fehlerhafte mehrmonatige Altbescheide brauchen eine gesonderte fachliche Prüfung.
- Später bekannt werdende Satz-/Falländerungen lösen weiterhin keinen automatischen
  Änderungsbescheid aus. Zahlungen werden aus dem aktuellen Bestand berechnet;
  Abgleich und Änderungsverfahren zu schon erteilten Bescheiden bleiben offen.
- Zuständigkeits-/Teamfilter (Option B/C) bleiben offen. Option D protokolliert
  Zugriffe, sie führt kein solches Berechtigungsmodell ein.
- Globale Storno-Spurenprüfung ist keine vollständige Rollbackbilanz mehrschrittiger
  Aufträge. Unregistrierte alte Browserfenster werden nicht automatisch beendet.
- Maven `exec:java` meldet nach Simulator-Läufen weiter auslaufende Oracle-Hilfsthreads
  (Diagnostic.CLOCK/InterruptTimer/OJDBC-WORKER). Das ist separat vom fachlichen
  Prüfergebnis zu bewerten und wurde hier nicht behoben.

## 6. Konkreter Nachprüfauftrag für die andere KI

1. Arbeitsverzeichnis gegen obigen Basis-Commit prüfen, einschließlich ungetrackter
   neuer Dateien. Nicht allein den Erfolgstext dieses Berichts übernehmen.
2. Replay mit anderer BG/anderem Monat/anderer expliziter IBAN muss abweisen;
   identischer erlaubter Replay muss ein persistiertes Einsichtsprotokoll erzeugen.
3. Commitfehler/Retry/zweite Service-Instanz dürfen weder Auditverlust noch
   dauerhafte Unterdrückung über das feste 60-Sekunden-Fenster verursachen.
4. Für Service und Saga Ende der Schwangerschaft, einmaligen Vermögenseinsatz und
   bekannten Satzwechsel prüfen. Druck/API müssen die gespeicherten Monatsbeträge
   nach späterer Falländerung weiter ausgeben. Erteilung darf kein Vermögen abbuchen.
5. Historisierung vor Saga-Schritt 2/3/4 und konkurrierend zur Falländerung/-anlage
   oder Kassenübergabe prüfen; abgewiesene Vorgänge dürfen keine Fachmutation erhalten.
6. Sicheren UI-Schritt nach bestätigter Anlage gezielt abbrechen: keine zweite
   Anlage, Kontext bleibt erhalten. Schreibschritt mit unbekanntem Commit darf
   nicht automatisch wiederholt werden. Fremdes Chrome-Profil darf nie ausgewählt werden.
7. Globale SQL-Abweichung und Verbindungsfehler injizieren: Lauf/Markdown dürfen
   keinen Erfolg melden; PDF/GUI/CLI müssen denselben Laufstatus verwenden.
8. Die genannten Grenzen getrennt bewerten; aus grünen Tests folgt weder ein
   vollständiges Fachrechts-Attest noch eine vollständige Produktionsfreigabe.
