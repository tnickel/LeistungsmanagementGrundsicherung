# Gesamt-Review SDN.Leistung — 2026-10-15-r7w2

Prüfgegenstand ist der **Arbeitsbaum** des Entwickler-Checkouts zu
`37abfb6f3da841e440ab21a53fbffa7eadf7cd04` auf `main` (Commit-Zeit 2026-10-15 12:12:09 +0200)
**einschließlich der noch nicht committeten Änderungen**: `git status` wies zu Beginn
46 geänderte und 33 neue Pfade aus (u. a. `TxManager.java`, `TaskController.java`,
`SagaJournal.java`, `ZahlungDao.java`, `LmgServer.java`, `BerechnungsSnapshot.java` und neue
Testklassen). Mehrere als **BEHOBEN** geführte Vorbefunde aus Abschnitt 5 sind genau diese
Arbeitsbaumkorrekturen; sie sind **nicht** Teil des Commits. Der Review ist unabhängig, lesend
und **keine Produktionsfreigabe**. Es wurden **keine** Produktquellen, Tests oder Konfigurationen
geändert.
Alle Prüfungen liefen in einer eingefrorenen Arbeitskopie
(`C:\Users\tnickel\AppData\Local\Temp\lmg-r7w2\repo`) und gegen die gemeinsame Developer-DB
`FREEPDB1`. Der fortlaufende fremde Zertifikatslauf wurde ausschließlich lesend ausgewertet und
nicht berührt.

Belege liegen unter `doc/codereview/grundsicherung-gesamt-review-2026-10-15-r7w2-belege/`.

> **Wichtiger Hinweis zum Prüfstand:** Dieser Review bewertet den **Arbeitsbaum**, nicht den
> Commit `37abfb6`. Die 46 geänderten und 33 neuen Pfade im Arbeitsbaum tragen einen erheblichen
> Teil der Vorbefund-Behebungen (Abschnitt 5) und der grünen Suiten. Wer nur `37abfb6`
> auscheckt, prüft einen anderen Stand als den hier belegten und wird die Suiten- und
> Fixzahlen aus diesem Bericht **nicht** reproduzieren.

---

## 1. Ergebnis

Der Stand ist in seiner Transaktionssteuerung, seinen Datenbanktests und der
Simulator-/Zertifikatskette **weitgehend belastbar**: die Hauptsuite (351 Tests), die
testsweet-Suite (291 Tests), die Node- (139) und Python-Eigentests (11) sowie alle sieben
Simulator-Kombinationen sind grün und wurden in diesem Lauf frisch ausgeführt. Ein
**BLOCKER** ist nicht bestätigt.

Die **Fachlichkeit des Geldflusses** ist im geprüften Ausschnitt stimmig: Regelbedarfsstufen 2026
(563/506/451/471/390/357 EUR), § 30 Abs. 2/3/6/7, § 35 Abs. 5, § 82 Abs. 3 und die
Schonvermögensgrenzen folgen dem amtlichen Text; die Nachrechnung in `jshell` ergab für acht
Konstellationen und den Paritätskorpus keine Abweichung. Der **Transaktionskern** ist gegen die
naheliegenden Nebenläufigkeiten abgesichert (vier eigene Rennproben, 0 Verletzungen), hat aber
einen bestätigten Randfall: nach einem bereits committeten inneren Vorgang kann die HTTP-Antwort
einen Fehlschlag melden (**R7W2-01**, verschärft gegenüber FB-20261015-02, weil das 409-Mapping
den Pfad nicht erreicht). Der **Geldfluss** hat eine bestätigte Sperre: deckt das verwertbare
Vermögen den Monatsbedarf vollständig, wird der Verbrauch nicht fortgeschrieben und der Haushalt
bleibt dauerhaft gesperrt (**R7W2-02**, entspricht FB-20261015-03). Die **Sicherheit** hat eine
bestätigte, dokumentierte Grenze: es gibt keine objektbezogene Zugriffsberechtigung, jede
angemeldete Sitzung erreicht jede Bedarfsgemeinschaft (**R7W2-03**, entspricht
FB-20261015-04).

Die **Belastbarkeit der Lastaussagen** ist eingeschränkt: die vorhandenen „Last"-Läufe dauern
2–64 Sekunden und tragen keinen Sättigungsnachweis (**R7W2-04**). Die **Zertifikatsaussage** ist
im Urteilsweg (BESTANDEN nur ohne provisionale FAILs, `explicitOffset` als Pflicht) tragfähig,
aber der externe GLM-Lauf erreicht bereits bei 29 von 67 Fallgruppen fünf formale
Erstversuchsfehler; ein vierter Fehlschlag in einer Gruppe führt zu UNVOLLSTÄNDIG. Die
GLM-Stichprobe enthielt TF29 in beiden Katalogen (E-04 damit beantwortet), das Modell urteilte
in einem Versuch FAIL (genau die Zahlung ohne Bescheid), in zwei weiteren PASS — die
Reproduzierbarkeit der GLM-Schicht ist damit belegbar schwach.

**Grenzen dieses Reviews:** DB-Stopp, Containerneustart und verbindungsübergreifende
Fault-Injection wurden **nicht** ausgeführt, weil die gemeinsame Developer-DB dafür nicht
exklusiv ist. Echte GLM-API-Aufrufe wurden nicht selbst gesendet; die GLM-Schicht ist nur aus
fremden Belegen bewertet. Ein Browser-CSP-Nachweis (CR-16) fehlt. Diese Punkte verhindern eine
uneingeschränkte positive Bewertung ihres Bereichs und sind in Abschnitt 3 einzeln ausgewiesen.

---

## 2. Bestätigte Befunde

### R7W2-01 — Abbruch meldet Fehlschlag nach bereits committeter Fachwirkung

- **ID:** R7W2-01; historisch FB-20261015-02 (k4p9) und KR-20261015-01.
- **Schweregrad und Belegsicherheit:** **KRITISCH**, DYNAMISCH BELEGT (eigene Probe) und
  STATISCH BELEGT (Aufruferkette).
- **Komponente und aktuelle Stellen:** `src/main/java/de/allgemein/tx/TxManager.java:36-91`
  (Commit in Zeile 48, `checkDeadline` nur in 43/46, **nicht** unmittelbar vor dem Commit),
  `src/main/java/de/allgemein/tx/TaskHandle.java:104-107`, `src/main/java/de/allgemein/tx/TaskController.java:224-262`,
  `src/main/java/de/allgemein/lmg/web/LmgServer.java:225-266` (409-Mapping erst nach dem
  `TimedOut`-Zweig), `webapp/app.js:40-70`.
- **Verletzte Regel:** Ein abgebrochener Warten-Vorgang ist kein Rollbackbeleg. Der Aufrufer darf
  nach einem bereits committeten Vorgang keine Wiederholung mit neuer Vorgangsnummer anfordern.
- **Vorbedingung und minimaler Ablauf:**
  1. HTTP-Schreibanfrage (`POST /api/auszahlung`, `POST /api/saga/antrag`, …) läuft als Task mit
     `TxOptions.DEFAULT` (30-Sekunden-Budget ab Ausführungsbeginn).
  2. Die innere Fachtransaktion der Service-Schicht erreicht ihren `commit()`.
  3. Der Wachhund feuert im selben Moment; `future.cancel(true)` setzt das Interrupt-Flag.
  4. `TaskHandle.result()` wirft sofort bzw. nach dem `future.get()`-Ende die
     `TaskOutcomeUnknownException`.
- **Sollverhalten:** Die Antwort nennt den Erfolg, sobald die Fachwirkung committet ist, oder
  benennt den Ausgang ausdrücklich als unbekannt **ohne** Aufforderung zur Neuordnung.
- **Istverhalten:** Die Probe ergab: die Arbeit lief **genau einmal** (`Arbeit ausgefuehrt=1`),
  die innere Transaktion wurde **committet**, und obwohl die Ausnahme
  `TaskOutcomeUnknownException` **false** für `isTaskOutcomeUnknown()` meldete, ergab
  `handle.result()` mit der Produktabbildung aus `LmgServer.api` den Status **HTTP 500**
  („Technischer Fehler — bitte erneut versuchen"), nicht 409. Zusätzlich zeigte eine separate
  Probe, dass nach dem Auslauf des Workers `commits(innere)=1` bei gleichzeitigem
  `stats.timedOut=1` und `completed=0` steht: die Statistik zählt einen Fehlschlag, die
  Datenbank enthält Erfolg.
- **Konkrete Auswirkung und Reichweite:** Zahlung, Bescheid oder Saga-Schritt können dauerhaft
  festgeschrieben sein, während die Sachbearbeitung einen technischen Fehler sieht und zur
  Wiederholung aufgefordert wird. Die Maske lädt im Fehlerzweig nicht neu. Eine doppelte aktive
  Monatszahlung verhindert `UQ_ZAHLUNG_BG_MONAT_AKTIV` — der Schaden ist also kein Doppelgeld,
  sondern ein falscher Status und eine blockierte Nachbearbeitung. Reichweite: jeder Lauf, der
  das 30-Sekunden-Budget erreicht und dessen Treiberaufruf den Interrupt nicht als Abbruch
  verwertet (die Treiberprobe in Abschnitt 5 zeigt: ojdbc11 23.5 wertet den Interrupt bei
  blockierendem Netzwerk-Read sehr wohl aus, aber nur dort).
- **Evidenz:**
  `...-r7w2-belege/gegenproben/gegenprobe-kern-ProbeA.log` (P2/P2b/P2c/P3).
- **Gegenprüfung — warum vorhandene Guards das nicht verhindern:** `TaskControllerWatchdogBudgetTest`
  unterbricht einen `sleep` auf einem Fake-Pool und prüft damit gerade **nicht**, dass die
  Antwort nach einer inneren, bereits committeten Transaktion den Erfolg trägt. Der
  `checkDeadline`-Aufruf liegt in `TxManager.execute` vor dem `commit()`, aber die in einer
  eigenen Transaktion laufende Facharbeit committet auf einer anderen Verbindung. Der
  Kommentar in `TaskController.java:38-50` beschreibt nur das Fenster des **äußeren**
  JDBC-Commits.
- **Reparaturempfehlung (nicht umgesetzt):** Die HTTP-Antwort erst nach dem tatsächlichen Ende
  des Workers senden (bzw. das `settle()`-Ergebnis auswerten). Bei Ablauf den Vorgang neu lesen
  und einen unbekannten Ausgang als solchen benennen, ohne zur Neuordnung aufzufordern.
  Zusätzlich `TaskOutcomeUnknownException` in `TaskHandle.result()` auch für den
  `CancellationException`-Zweig konsistent erzeugen (die Probe zeigt die Inkonsistenz).
- **Regressionstest:** Innere Transaktion committet, der Worker blockiert danach, der Wachhund
  feuert. Assertions: (a) die Zahlungszeile existiert, (b) die HTTP-Antwort ist 200 **oder**
  409 mit Code `AUSGANG_UNBEKANNT`, (c) sie enthält **keine** Aufforderung zur Neuordnung mit
  neuer Vorgangsnummer, (d) ein zweites `POST` mit neuer Nummer legt keine zweite aktive Zeile an.

### R7W2-02 — Deckendes Vermögen wird nicht fortgeschrieben; der Haushalt bleibt gesperrt

- **ID:** R7W2-02; historisch FB-20261015-03 (k4p9).
- **Schweregrad und Belegsicherheit:** **KRITISCH**, DYNAMISCH BELEGT (eigene `jshell`-Probe).
- **Komponente und aktuelle Stellen:** `src/main/java/de/allgemein/lmg/calc/Bewilligungsplan.java:56-72`
  (die Bedingung `if (berechnung.zahlbetrag().signum() > 0)` um die Fortschreibung),
  `src/main/java/de/allgemein/lmg/service/SozialLeistungsService.java:301-305` und `:343-346`.
- **Verletzte Regel:** [§ 90 SGB XII](https://www.gesetze-im-internet.de/sgb_12/__90.html)
  (abgerufen am 2026-10-15): das verwertbare Vermögen ist einzusetzen. Die implementierte
  Einmalfortschreibung bucht den Einsatz nur zusammen mit einer positiven Zahlung.
- **Vorbedingung und minimaler Ablauf:** Schonvermögen 10.000,00 EUR (eine volljährige Person
  nach § 41 SGB XII, Stufe 1, ohne Kinder); Bestand 10.563,00 EUR; kein Einkommen. Verwertbar
  563,00 EUR, ungedeckter Bedarf genau 563,00 EUR, Zahlbetrag 0,00 EUR.
- **Sollverhalten:** Der deckende Einsatz mindert den Bestand um 563,00 EUR; der Folgemonat
  rechnet mit 10.000,00 EUR und zahlt 563,00 EUR.
- **Istverhalten (frisch ausgeführt):** Der Plan lässt in beiden Monaten `bestand=10563.00`
  stehen und liefert in beiden Monaten `zahl=0.00`. Die Gegenprobe mit 10.000,01 EUR ergibt im
  ersten Monat `zahl=562.99`, `bestand` sinkt auf 10.000,00 und der Folgemonat zahlt `563.00`.
  **Ein Cent mehr verwertbares Vermögen sperrt also die Leistung dauerhaft; ein Cent weniger
  führt ab dem Folgemonat zur vollen Leistung.**
- **Konkrete Auswirkung und Reichweite:** Jede Bedarfsgemeinschaft, deren verwertbares Vermögen
  den ungedeckten Monatsbedarf genau erreicht oder übersteigt, wird für alle Folgemonate
  gesperrt, bis jemand den Bestand von Hand senkt. Die Person erhält keine Leistung, obwohl
  § 90 den Verbrauch vorsieht.
- **Evidenz:** `...-r7w2-belege/gegenproben/gegenprobe-fach-jshell.log`
  (`Deckung zahl=0.00 einsatz=563.00`, `PlanDeckung 2026-01 … zahl=0.00`,
  `Plan 2026-03 … bestand=10000.01 zahl=562.99`, `Plan 2026-04 … zahl=563.00`).
- **Gegenprüfung — warum vorhandene Guards das nicht verhindern:** Die Ablehnung einer
  Nullzahlung ist für sich stimmig (eine Zahlung mit Betrag 0 hat in der Statusmaschine keinen
  Sinn). `VermoegensfortschriebDbTest` erwartet genau dieses Verhalten und zementiert es. Die
  Dokumentation (`doc/leistungsmanagement.md:333`) beschreibt die Einmalanrechnung, macht sie
  aber nicht zu einer gesetzlich tragenden Verbrauchsregel.
- **Reparaturempfehlung (nicht umgesetzt):** Den eingesetzten Betrag auch bei Zahlbetrag 0
  fortschreiben und den Monat als „durch Vermögen gedeckt" protokollieren, ohne eine
  Nullzahlung anzulegen.
- **Regressionstest:** Bestand 10.563,00 EUR, zwei Monate, Stufe 1, kein Einkommen. Assertions:
  Monat 1 ohne Zahlungszeile und mit Bestand 10.000,00 EUR; Monat 2 Zahlbetrag 563,00 EUR. Die
  Gegenprobe 10.000,01 EUR behält im ersten Monat 562,99 EUR.

### R7W2-03 — Fehlende objektbezogene Zugriffsberechtigung (Need-to-know)

- **ID:** R7W2-03; historisch FB-20261015-04 (k4p9) und WB-20261015-01.
- **Schweregrad und Belegsicherheit:** **KRITISCH für einen Produktivbetrieb** (in der Demo als
  Einmandanten-Grenze dokumentiert), STATISCH BELEGT.
- **Komponente und aktuelle Stellen:** `src/main/java/de/allgemein/lmg/web/LmgServer.java:339`
  (Liste), `:356-360` (Detail), `:362` (Update), `:376-431` (Bescheid/SozialFall), `:455`
  (Auszahlung), `:514` (alle Zahlungen), `:521` (Journal). Identität stammt ausschließlich aus
  dem Bearer-Token (`:202-210`); eine Zuständigkeitsprüfung fehlt.
- **Verletzte Regel:** Datenminimierung und Zweckbindung bei Sozialdaten:
  [§ 35 SGB I](https://www.gesetze-im-internet.de/sgb_1/__35.html), §§ 67 ff. SGB X,
  Art. 5 Abs. 1 lit. c und [Art. 25 DSGVO](https://eur-lex.europa.eu/eli/reg/2016/679/oj).
  Ein protokollierter Zugriff ersetzt keine Berechtigung.
- **Vorbedingung und minimaler Ablauf:** 1. Gültige Sitzung als Sachbearbeiter (`sb-01`).
  2. Bekannte oder erratene ID einer fremden Bedarfsgemeinschaft.
  3. Aufruf `GET /api/bedarfsgemeinschaften/{id}` bzw. `PUT /api/sozialfaelle/{id}`.
- **Sollverhalten:** Lesen und Ändern vollständiger Fall-, Bank- und Bescheiddaten nur für die
  zuständige Person. **Istverhalten:** Jede Sitzung liest und ändert jede BG, gibt Auszahlungen
  ein und erteilt Bescheide. `GET /api/zahlungen` und `GET /api/journal` sind global.
- **Konkrete Auswirkung und Reichweite:** Sozial- und Bankdaten des gesamten Datenbestands
  dieser Instanz sind für jedes angemeldete Konto erreichbar. **Mildernd:** Jede Einsicht wird
  auditiert (`EinsichtsprotokollDbTest`), Übersichtslisten enthalten keine Bankdaten.
- **Evidenz:** Die Routen verlangen eine Sitzung, für Admin-Pfade zusätzlich `istAdmin()`; ein
  Zuständigkeitsmodell existiert weder im Schema noch im Service. Belegkopie:
  `...-r7w2-belege/gegenproben/agentC-bericht-web-zertifikat.md`, Abschnitt C-01.
- **Gegenprüfung — warum vorhandene Guards das nicht verhindern:**
  `LmgServerHttpTest` prüft 401/403 auf **Admin**-Routen. Ein zweites Konto gegen einen fremden
  Vorgang ist dort kein Prüffall. Vier-Augen verhindert nur die Selbstfreigabe.
- **Reparaturempfehlung (nicht umgesetzt):** Zuordnung Bedarfsgemeinschaft → Team/Sachgebiet,
  Prüfung im Service-Eintritt und Filter in allen Listen.
- **Regressionstest:** `sb-01` in Team A, BG X in Team B. `GET`, `PUT` und Auszahlung auf X
  liefern 403 `BERECHTIGUNG`; die Liste enthält X nicht; ein Journal-Eintrag „verweigert"
  existiert.

### R7W2-04 — Die vorhandenen Lastläufe tragen keinen Lastnachweis

- **ID:** R7W2-04, neu.
- **Schweregrad und Belegsicherheit:** **MAJOR**, DYNAMISCH BELEGT (eigene sieben Läufe).
- **Komponente und aktuelle Stellen:** `testsweet/.../Simulator.java:290-297` (Durchsatz aus
  `Duration.toSeconds()`, schneidet ab), `LastBericht` (Perzentile fehlen),
  `CertificateSuiteWorker` (Dimensionierung).
- **Vorbedingung:** Ein Lauf der Kategorien LAST oder STRESS.
- **Sollverhalten:** Ein Lastnachweis braucht eine definierte Mindestdauer, Sättigungsstufen und
  Perzentile. **Istverhalten:** FACH LAST lief mit 120 Aufträgen in **3 Sekunden**, FACH STRESS
  mit 21 Nutzern in **5 Sekunden**, WEB LAST mit 30 Aufträgen in **62 Sekunden**. FACH POS/NEG
  liefen mit 500 Aufträgen in 11 bzw. 10 Sekunden. Bei Laufzeiten unter 1 Sekunde würde der
  abgeschnittene Sekundenwert sogar 0 ergeben.
- **Konkrete Auswirkung und Reichweite:** Die Zahlen belegen **Funktion unter Wiederholung**,
  nicht Belastbarkeit. Ein „bestanden" aus diesen Läufen darf nicht als Lastnachweis gelesen
  werden. Die Kennzahl „3600/min" im fremden Beleg ist durch die Sekundenabschneidung um bis zu
  ~50 % verzerrt.
- **Evidenz:** `...-r7w2-belege/basissuiten/simulator-7.log` (Zeile je Kombination mit
  `auftraege`, `phase=FERTIG`, `global=n/n`, Laufverzeichnis) und
  `...-r7w2-belege/laeufe/`.
- **Gegenprüfung:** Es gibt keinen Test, der eine Mindestlaufdauer oder Perzentile erzwingt.
- **Reparaturempfehlung (nicht umgesetzt):** Millisekundenbasis (`toMillis()/60000.0`),
  p50/p95/p99, Mindestlaufdauer und Sättigungsstufen in die Dimensionierung aufnehmen.
- **Regressionstest:** Laufdauer 2100 ms mit 120 Aufträgen → Durchsatz 3428,6/min ± 0,1;
  Laufdauer 800 ms → Wert > 0.

### R7W2-05 — Zahlung ohne Bescheid ist in der Produktlogik dauerhaft aktiv

- **ID:** R7W2-05, neu (Nachbarschaft zu TF29).
- **Schweregrad und Belegsicherheit:** **MAJOR**, DYNAMISCH BELEGT (GLM-Beleganalyse) und
  STATISCH BELEGT.
- **Komponente und aktuelle Stellen:**
  `src/main/java/de/allgemein/lmg/service/BescheidPaymentBinding.java:18-21`
  (`if (DAO.zaehleFuerBg(connection, bgId) == 0) return;`); Aufrufer
  `SozialLeistungsService.java:319` und `AntragsSagaProzess.java:406`; Endpunkte
  `POST /api/auszahlung` (`LmgServer.java:455`) und `POST /api/saga/antrag` (`:483`).
- **Verletzte Regel:** [§ 44 SGB X](https://www.gesetze-im-internet.de/sgb_10/__44.html) und
  [§ 66 Abs. 1 SGB I](https://www.gesetze-im-internet.de/sgb_1/__66.html) verlangen für die
  Auszahlung einen Verwaltungsakt bzw. eine Bewilligung; eine Zahlung ohne jeden Bescheid ist
  kein rechtmäßiger Vollzug.
- **Vorbedingung und minimaler Ablauf:** 1. Eine Bedarfsgemeinschaft ohne **jeden** Bescheid.
  2. `POST /api/auszahlung` für einen beliebigen Monat. 3. `verify` kehrt bei
  `zaehleFuerBg == 0` sofort zurück; die Zahlung wird mit dem berechneten Betrag angelegt.
- **Sollverhalten:** Im Produktivbetrieb keine Zahlung ohne Bescheid; ein Demo-Weg nur hinter
  einem expliziten Schalter. **Istverhalten:** Der „Demoweg" ist immer aktiv. Es existiert kein
  Betriebsmodusschalter; die Demokonten-Sperre betrifft nur Loopback, diesen Pfad nicht.
- **Konkrete Auswirkung und Reichweite:** Fachlich eine Auszahlung ohne Verwaltungsakt.
  Prüftechnisch der schwerere Teil: Der Simulator erwartet genau das als Sollverhalten
  (`SzenarioBibliothek`, Erwartungen ab 1860 ff., Fall TF29), der **externe GLM-Lauf urteilte
  dafür FAIL**: „Nach Produktregel sind Zahlungen nur in bescheidgedeckten Monaten zulässig;
  BESCHEID-Anzahl für BG 63848 ist laut V8 = 0 … Die Anordnung für 2026-07 hätte daher mit
  VALIDATION abgewehrt werden müssen." Damit bewertet der Simulator einen Fall als bestanden,
  den der unabhängige Prüfer als Regelverstoß liest.
- **Evidenz:** Der GLM-Lauf wählte TF29 in **beiden** Katalogen aus
  (`POSITIV:…:TF29` und `NEGATIV:…:TF29` im `manifest.json`, `selectedCaseIds`). Der erste
  Versuch urteilte `verdict=FAIL` mit zwei Findings, die beiden Wiederholungsversuche desselben
  Batches dagegen `PASS` bei inhaltlich unverändertem Prüfinhalt (nur der formale
  Korrekturblock wurde angehängt) — `probe-C/llm/response-20-attempt-1..3.json`.
- **Gegenprüfung:** Kein Test erwartet die Ablehnung einer Zahlung ohne Bescheid im
  Produktivmodus; `BescheidPaymentBindingTest` prüft den Fall ohne Bescheid nicht als Fehlerfall.
- **Reparaturempfehlung (nicht umgesetzt):** Betriebsmodus-Flag mit Default „Bescheidpflicht";
  den Simulator-Fall TF29 je Modus auf „VALIDATION, keine Zahlung" umstellen.
- **Regressionstest:** Produktivmodus, BG ohne Bescheid, `POST /api/auszahlung`. Assertions:
  HTTP 400 `VALIDATION`; `COUNT(ZAHLUNG WHERE BG_ID)=0`; `BUDGET_REST` unverändert.

### R7W2-06 — Retry-Vertrag „drei identische Sendeversuche" wird nicht eingehalten

- **ID:** R7W2-06; historisch der Retry-Vertrag aus AGENTS.md.
- **Schweregrad und Belegsicherheit:** **MINOR**, DYNAMISCH BELEGT (Vergleich der fremden
  Request-Kopien).
- **Komponente und aktuelle Stellen:** `LlmReviewService.java:205-325` (Retry) und
  `:595-612` (`korrekturAnweisung`).
- **Vorbedingung:** Eine formal ungültige Erstantwort (Schemaverstoß, Begleittext).
- **Sollverhalten (Vertrag):** Höchstens drei **identische** Sendeversuche mit 30 s Pause.
  **Istverhalten:** Ab Versuch 2 wird `=== KORREKTURANWEISUNG ===` mit Fehlertext und einer
  Liste von Check-IDs angehängt. Die Kopien zeigen: System-Nachricht identisch (164 Zeichen),
  User-Nachricht 41.643 gegen 42.405 Zeichen — Versuch 2 ist um **762 Zeichen** länger.
  `model`, `temperature 0.1`, `max_tokens 16384`, `thinking` und `response_format` sind gleich.
- **Konkrete Auswirkung und Reichweite:** Vertrag und Implementierung widersprechen sich; die
  Reproduzierbarkeit „gleicher Eingabe" geht verloren und der Hinweis kann die Modellausgabe
  steuern. **Wichtig:** Fachliche FAILs werden **nicht** wegwiederholt (provisionalFailures
  bleiben erhalten), und der Hinweis enthält keine Sollwerte. Im beobachteten Lauf milderte er
  den Effekt sogar ab: der Batch mit dem TF29-FAIL wurde im dritten Versuch PASS — also genau
  der Fall, der die Ergebnisqualität der GLM-Schicht fragwürdig macht.
- **Evidenz:** `probe-C/llm/request-20-attempt-1..3.json` und
  `...-r7w2-belege/gegenproben/agentC-bericht-web-zertifikat.md` (Abschnitt C-06).
- **Gegenprüfung:** Ein Test, der die Prompt-Gleichheit erzwingt, existiert nicht.
- **Reparaturempfehlung (nicht umgesetzt):** Den Vertrag ausdrücklich auf „identischer
  Prüfinhalt plus formaler Korrekturhinweis **ohne** Sollwerte" ändern und `sha256(basePrompt)`
  je Versuch im Manifest binden — oder wirklich identisch senden.
- **Regressionstest:** Bei drei Versuchen ist `sha256(basePrompt)` gleich; die Differenz liegt
  ausschließlich im Korrekturblock; der Korrekturblock enthält keinen Betrag aus der Referenz.

### R7W2-07 — Der Simulator räumt global auf, ohne DB-Exklusivität nachzuweisen

- **ID:** R7W2-07; historisch CR-49 und der k4p9-Hinweis.
- **Schweregrad und Belegsicherheit:** **MINOR**, DYNAMISCH BEOBACHTET und STATISCH BELEGT.
- **Komponente und aktuelle Stellen:** `testsweet/.../Simulator.java:1036,1058-1081`
  (Löschen aller `AKTENZEICHEN LIKE 'SWEET-%'` in JOURNAL, BESCHEID, ZAHLUNG, SOZIAL_FALL und BG
  sowie der Kassenjournale `sb-sim-%`), `CertificateSuiteWorker.java:35` (`aufraeumen=true`),
  `SweetServer.java:644` (`requireNoCertificateSuite`).
- **Vorbedingung:** Zwei Simulatorläufe (zwei Checkouts oder zwei Prozesse) gegen dieselbe DB;
  einer davon mit Aufräumen.
- **Sollverhalten:** Nur eigene Daten entfernen; eine Test-DB-Allowlist und eine Sperre
  erzwingen. **Istverhalten:** Der Aufräumer löscht global alle `SWEET-%`-Daten. Der
  In-Prozess-Guard kennt nur `suite-current.json` **desselben** Checkouts.
- **Konkrete Auswirkung und Reichweite (in diesem Review direkt beobachtet):** Vor meinen
  Läufen standen **21 fremde SWEET-Bescheide** in der DB (Rest des fremden STRESS-Laufs); mein
  erster Simulatorlauf hat sie mit seinem Aufräumen entfernt. Ich habe das an den Daten vor und
  nach dem Lauf gesehen. **Kein falscher Erfolg entsteht:** Lauf A schlägt sichtbar fehl
  (Verifikator: keine Zeile → false; GlobaleVerifikation zählt global). Das Risiko ist der
  Datenverlust eines laufenden fremden Laufs und — bei falsch konfigurierter Nicht-Test-DB —
  das Löschen von Echtdaten.
- **Evidenz:** `...-r7w2-belege/basissuiten/simulator-7.log`,
  `...-r7w2-belege/gegenproben/agentC-bericht-web-zertifikat.md` (Abschnitt C-04).
- **Gegenprüfung:** `pruefeKeineAltenAktenzeichen` (Zeile 1036) bricht ab, wenn Altdaten
  vorhanden sind und **nicht** aufgeräumt wird — die Prüfung schützt nicht vor dem Aufräumen.
- **Reparaturempfehlung (nicht umgesetzt):** Lauf-ID/Präfix je Lauf, `DBMS_LOCK` oder
  Sperrtabelle, Allowlist der Test-DB-Identität.
- **Regressionstest:** Zweiter Simulator bei aktiver Sperre → Start wird mit klarer Meldung
  abgelehnt; nicht gelistete DB-Identität → kein DELETE.

### R7W2-08 — Geldwerte werden still zu 0,00 bzw. still gerundet

- **ID:** R7W2-08; historisch CR-11 (Rest).
- **Schweregrad und Belegsicherheit:** **MINOR**, DYNAMISCH BELEGT (Parserprobe) und STATISCH.
- **Komponente und aktuelle Stellen:** `LmgServer.java:932-940` (`betragWert`: `null` →
  `BigDecimal.ZERO.setScale(2)`), `SozialLeistungsService.java:969-983`
  (`normiereBetrag`: `setScale(2, HALF_UP)`). Betroffene Endpunkte: `:342`, `:362`, `:401`,
  `:427` und weitere mit Geldfeldern.
- **Sollverhalten:** Fehlende Pflicht-Geldfelder, unbekannte Schlüssel und mehr als zwei
  Nachkommastellen liefern 400 `VALIDATION`. **Istverhalten:** Ein unbekannter oder falsch
  geschriebener Schlüssel (`"kdumiete"` statt `"kduMiete"`) wird ignoriert, das Feld fällt auf
  0,00 zurück und wird gespeichert; `12.345` wird still zu 12,35 gerundet.
- **Auswirkung:** Ein Tippfehler erzeugt einen falschen Bedarfs- oder Einkommensansatz, ohne
  Fehlermeldung. Die eigene SPA sendet immer vollständige Objekte (deshalb im Normalbetrieb
  unauffällig); das Risiko trifft API-Clients, Simulator- und Fremdintegrationen.
- **Evidenz:** `...-r7w2-belege/gegenproben/agentC-bericht-web-zertifikat.md` (Abschnitt C-05,
  P1 dynamisch) sowie die zitierten Codezeilen.
- **Gegenprüfung:** `SozialLeistungsServiceTest` prüft negative Beträge und die Größenobergrenze,
  aber nicht fehlende Felder, unbekannte Schlüssel oder die Skala.
- **Reparatur und Regressionstest:** Pflichtfeld- und `scale() ≤ 2`-Prüfung in `betragWert`
  ergänzen; drei Anfragen (Skala 3, Teilobjekt ohne `kduMiete`, unbekannter Schlüssel) mit
  erwartetem 400 und unveränderter `SOZIAL_FALL`-Zeile.

---

## 3. Offene Hypothesen, fehlende Nachweise und Modell-/Betriebsgrenzen

### 3.1 Offene Hypothesen (nicht als Befund gezählt)

- **H-01 (CSP-Inline-Styles, historisch CR-16):** `index.html`/`app.js` verwenden
  `style`-Attribute, während der Kopf `style-src 'self'` setzt. Ob der Browser die Inline-Styles
  blockiert, hängt vom Nutzeragenten ab und ist **statisch nicht entscheidbar**. Probe: Statik
  mit identischem CSP-Header über einen Mini-`HttpServer` ausliefern, Headless-Chrome mit eigenem
  Profil starten, Konsole auf „Refused to apply inline style" prüfen. **Status: NICHT
  DYNAMISCH GEPRÜFT.**
- **H-02 (Zwei Checkouts löschen sich gegenseitig):** Verhalten von R7W2-07 im Konkurrenzfall.
  Ohne eine exklusive DB nicht reproduzierbar. **Status: NICHT DYNAMISCH GEPRÜFT.**
- **H-03 (BESTANDEN ohne Suite-Verzeichnis):** Ob `certificate-evidence.js`/`--verify` ohne
  vollständiges Suite-Verzeichnis ein positives Urteil erzeugen kann. Probe wäre eine Kopie
  eines abgeschlossenen Belegs in `probe-C` bei entferntem Suite-Verzeichnis. **Status: NICHT
  GEPRÜFT** (Manipulationsprobe an einer Kopie wäre zulässig gewesen, sie wurde aus
  Zeitgründen nicht mehr ausgeführt).
- **H-04 (Fehlerdialog-Spezifität im WebMitarbeiter):** Die Negativschritte prüfen Dialogtexte
  per Teilstring; ob ein technischer Fehler mit passendem Teilstring als bestandener Negativfall
  durchginge, ist eine reine Testfrage. **Status: NICHT GEPRÜFT.**
- **H-05 (Weitergabe des API-Schlüssels an eine geänderte `baseUrl`):** `POST /api/llm/config`
  ändert das Ziel. Ob der gespeicherte Schlüssel an ein neues Ziel gesendet wird, wurde nicht
  ausgeführt (dafür wäre ein Fake-Endpunkt nötig gewesen). **Status: NICHT DYNAMISCH GEPRÜFT.**
- **H-06 (Sozialdaten in Logs):** `TxAuditLog` redigiert nach eigener Unit-Suite; ob eine
  Ausnahme mit IBAN oder Name in der Message vollständig redigiert wird, ist nicht mit einem
  Fall mit echten Werten geprüft. **Status: NICHT GEPRÜFT.**

### 3.2 Ausdrücklich nicht geprüfte Schichten

- **DB-Stopp, Containerneustart, verbindungsübergreifende Fault-Injection:** Die gemeinsame
  Developer-DB `FREEPDB1` ist keine exklusive isolierte Umgebung. Alle drei Verfahren sind
  laut Auftrag dort unzulässig und wurden **nicht** ausgeführt.
- **Echte GLM-API-Aufrufe:** Es wurden **keine** eigenen API-Aufrufe gesendet (keine gültige
  eigene Konfiguration, kein autorisierter Rahmen in dieser Sitzung). Die GLM-Schicht ist
  ausschließlich aus **fremden Belegen** (Request-/Response-Kopien des laufenden fremden
  Zertifikatslaufs) bewertet. **Grenze:** fremde Belege binden nicht meine Prüfumgebung; die
  Reproduzierbarkeit der Modellschicht ist damit nur lesend belegt.
- **Ein GLM-PASS ist kein Beweis fachlicher oder rechtlicher Richtigkeit.** Unabhängig ist
  allein `LlmCalculationReference` (die auch der Verifikator nutzt). Der TF29-Verlauf
  (FAIL → PASS → PASS bei unverändertem Prüfinhalt) zeigt die Streuung ausdrücklich.
- **Oracle 19c EE und Java-21-Laufzeit:** Laufzeit war Temurin 25.0.4.1 mit Compiler
  `--release 21`; Oracle war die Free-Edition 26ai (23.26.3.0.0). Eine echte 19c-EE-Instanz
  und eine echte Java-21-JVM wurden **nicht** geprüft.
- **Der Browser-/GUI-Weg der Zertifikatspipeline** wurde nicht bedient (nur CLI und HTTP-Belege).

---

## 4. Abdeckungsmatrix

Evidenzart: **D** = dynamisch belegt, **S** = statisch belegt, **N** = nicht geprüft.

| Prüffeld | Status | Evidenzart | Belegpfad / Fundstelle |
|---|---|---|---|
| Endpunktmatrix LmgServer (30 Routen) | geprüft | S | `gegenproben/agentC-bericht-web-zertifikat.md` Abschnitt B.1 |
| Endpunktmatrix SweetServer (20+ Routen) | geprüft | S | ebd. Abschnitt B.2 |
| Objektzugriff/IDOR, Herkunft der Identität | geprüft, Befund | S | R7W2-03 |
| Sitzung, Idle-Timeout, Drosselung, Drucktoken | geprüft | S | ebd. Abschnitt C-10/C-11/WB-06 |
| XSS (SPA, Druck, Bescheid-HTML, GUI) | geprüft, Verdacht widerlegt | S | ebd. Abschnitt G |
| CSP, nosniff, no-store, frame-ancestors | teilweise | S | `LmgServer.java:147,161,171,188,596,609,1002`; Inline-Styles: H-01 |
| JSON-Parser, Typen, Geldwerte | geprüft, Befund | **D** + S | P1 `JsonProbe`; R7W2-08 |
| Timeouts, Überlast, Body-Grenzen | geprüft | S | `HttpServerLimits`, 503-Pfade |
| SQL- und Pfadinjektion | teilweise | S | IDs nur `\d{1,18}`/`parseLong`, Statik per Whitelist |
| Sozialdaten in Logs/Fehlern | teilweise | S | H-06 |
| Revisions-/Manipulationsschutz | geprüft | S | nur lokale Hashes (ZT-01, INFO) |
| Verifikator und GlobaleVerifikation | geprüft | S | Verifikator/GlobaleVerifikation, Simulator 1083–1099 |
| Negativfälle (Fachfehler ≠ technischer Fehler) | geprüft | S | Mitarbeiter 83–98, SzenarioAktion 44–63 |
| Webmodus (Wiederholung, Recovery, Fachzustand) | geprüft | S | WebMitarbeiter 143–240 |
| Datenhygiene / Aufräumen | geprüft, Befund | **D** beoachtet + S | R7W2-07 |
| Last- und Stressaggregate | geprüft, Befund | **D** | R7W2-04 |
| Regelbedarfsstufen und § 30/§ 35 § 82 | geprüft | **D** | `gegenproben/gegenprobe-fach-jshell.log` |
| § 44 SGB X / Bescheidbindung | geprüft, Befund | **D** + S | R7W2-05 |
| § 90 SGB XII Vermögensverbrauch | geprüft, Befund | **D** | R7W2-02 |
| Transaktionskern, Retry, Deadlock | geprüft | **D** | `gegenproben/gegenprobe-kern-ProbeA.log` |
| Nebenläufigkeit Freigabe∥Storno∥Kassenlauf | geprüft (4 Proben) | **D** | `gegenproben/gegenprobe-rennen-v0.log`, `-v50.log` |
| Crash-/Commit-Grenze, mehrfacher Recovery | teilweise | **D** + S | P3; `ProzessKillRecoveryDbTest`, `SagaParallelRecoverTest` |
| Folge-/Jahreswechselmonat mit Satzwechsel | geprüft | **D** | `Wechsel 2025-12 rbs=562.00` / `2026-01 rbs=563.00` |
| Schwangerschaft/Entbindungsmonat | geprüft | **D** | `Juni flag=true zahl=658.71` / `Juli flag=false zahl=563.00` |
| Mehrbedarfsdeckel und Warmwasser | geprüft | **D** | `KdU=`/`KdU+`/`KdU-` |
| LLM-Übertragung, Antwortvalidierung, Retry | geprüft, Befund | **D** (fremde Belege) | R7W2-06 |
| Referenzrechnung und Gesamturteil | geprüft | S | `LlmCalculationReference`, `certificate-evidence.js` |
| Veröffentlichung/Export, Downloadsperre | geprüft | S | `publish-certificate.js`, `CertificateSuiteState` |
| Node-Tests in der Zertifikatspipeline | geprüft, Befund | S | C-03 (Node-Tests nicht Pipeline-Pflicht) |
| Web-STRESS | nicht anwendbar | — | im Projekt begründet als N/A geführt |
| DB-Stopp / Containerneustart / Fault-Injection | **nicht geprüft** | N | gemeinsame DB, Abschnitt 3.2 |
| Echte GLM-API-Aufrufe | **nicht geprüft** | N | Abschnitt 3.2 |

---

## 5. Nachprüfung historischer Befunde

Bezugsberichte: `…-3c909c2.md` (GR-01…10), `…-k4p9.md` (FB-01…16, KR/WB/ZT),
`grundsicherung-fixbericht-2026-10-15.md`, `grundsicherung-grok-gegenpruefung-2026-10-15.md`,
`grok-{fach,kern,web}-gegenpruefung-2026-10-15.md`, `testsweet-abdeckung-2026-10-15.md`,
`zertifikat-saga-testkorrektur-2026-10-15.md` sowie die CR-Liste v0.5.8.

| ID | Status | Aktuelle Evidenz und verbleibendes Risiko |
|---|---|---|
| FB-20261015-01 README-Kurztabelle | **NOCH VORHANDEN** | Der Rechenweg selbst stimmt; die Kurztabelle bleibt die abweichende Fachbeschreibung. Risiko: MINOR (Dokumentation). |
| FB-20261015-02 Abbruch nach Commit | **NOCH VORHANDEN, verschärft** | Probe P2/P2b/P3: 500 statt 409, Statistik zählt Fehlschlag bei committeter Zeile → R7W2-01. |
| FB-20261015-03 Deckendes Vermögen | **NOCH VORHANDEN** | `PlanDeckung … zahl=0.00` → R7W2-02. |
| FB-20261015-04 Objektberechtigung | **NOCH VORHANDEN** | → R7W2-03, dokumentierte Einmandanten-Grenze. |
| FB-20261015-05 Kompensation über Schrittindex | **BEHOBEN (Arbeitsbaum)** | `SagaJournal.start(…, stepNames)` speichert den stabilen Namen; `SagaEngine:337-342` liest `saga.stepName(i)` und meldet fehlende Namen als SEVERE. `ddl/oracle_schema.sql` hat `NAME VARCHAR2(100)`. Die Dateien `SagaJournal.java`, `SagaEngine.java`, `SagaDefinition.java`, `SagaStep.java`, `TxSagaJournal.java`, `UnfinishedSaga.java` stehen als **geändert** im Arbeitsbaum (nicht im Commit). Risiko: nur ein Altdatensatz ohne Namen. |
| FB-20261015-06 IN-Liste >1000 | **BEHOBEN (Arbeitsbaum)** | `ZahlungDao.uebergebeGesperrte` blockt in 1000er-Schritten und prüft `geaendert == block.size()`. `ZahlungDao.java` ist im Arbeitsbaum geändert. |
| FB-20261015-07 JVM-Monat der Vorschau | **BEHOBEN** | `ladeDetail(bgId, sachbearbeiterId, bezugsmonat)` führt den Bezugsmonat; `LmgServer:355-357` liest `?monat=`, `app.js:395` sendet ihn, `Vorgangsdetail.berechnungsmonat` und `app.js:487` zeigen ihn an. `ZahlungsmonatTest` und `BescheidMonatsplanTest` grün. |
| FB-20261015-08 Änderungsbescheid | **BEHOBEN (Arbeitsbaum)** | `BescheidPaymentBinding.rejectOverlap` wird sowohl bei der Erteilung (`:618`) als auch bei der Änderung (`:730`) geprüft; Snapshot v3 friert das Erteilungs-HTML ein (GR-05). |
| FB-20261015-09 Doppelte Kinderzähler | **BEHOBEN** | Günstigervergleich im Code (`Leistungsberechnung:231-244`); Probe `A4-u7`/`A4-nr1`/`A6-jung`/`A6-nr1` nachgerechnet. |
| FB-20261015-10 Kontensegment im Klartext | **BEHOBEN** | `SozialLeistungsService:1000` und `AntragsSagaProzess:675` werfen `"Ungültige IBAN"` ohne Wert. |
| FB-20261015-11 Write-ahead-Quittung | **BEHOBEN (Arbeitsbaum)** | `SagaParallelRecoverTest` und `SagaOwnershipClockDbTest` grün (0 Fehler); beide Testdateien sind im Arbeitsbaum. |
| FB-20261015-12 Payload-Scan der Übersicht | **BEHOBEN (Arbeitsbaum)** | `SagaJournal.unfinished` selektiert nur `SAGA_ID, SAGA_TYPE, STATE`; für beendete Zustände wird `PAYLOAD = NULL` gesetzt (`:396`). Der Payload wird in der Übersicht nicht mehr gelesen. |
| FB-20261015-13 Leere Bankfelder | **BEHOBEN** | `SozialLeistungsService:711-727` begrenzt den Änderungsvergleich. |
| FB-20261015-14 Storno ohne Rollenprüfung | **TEILWEISE** | HTTP-Storno verlangt Admin (`LmgServer:506`); der **Service** selbst prüft keine Rolle (INFO). |
| FB-20261015-15 Snapshot-JSON ohne Eingabemerkmale | **BEHOBEN (Arbeitsbaum)** | Snapshot v3 friert die Merkmale ein (`BerechnungsSnapshot.java` geändert, `BerechnungsSnapshotTest` grün). |
| FB-20261015-16 Frame-Schutz | **BEHOBEN** | `frame-ancestors 'none'` an allen sieben Header-Stellen. |
| GR-01…GR-06, GR-08, GR-10 | **BEHOBEN (überwiegend Arbeitsbaum)** | Der Fixbericht listet je Fund Korrektur und Regression; im aktuellen Build sind alle Suiten grün, und die Proben dieses Reviews (A4/A6, WW, Retry nur 54/60/8177) bestätigen die Kernaussagen. Ein Teil der Fixdateien steht als **geändert** im Arbeitsbaum. Nicht jeder historische Einzelnachweis wurde erneut ausgeführt. |
| GR-07 Login-Drosselung | **BEHOBEN** | Getrennte Fehlversuchszähler; Restthema Lockout-DoS (C-11) bleibt. |
| GR-09 LLM-Vertragsabweichung | **TEILWEISE** | Java/Node verlangen Schema 2 ohne Begleittext; die Node-Tests sind aber **nicht** Pipeline-Pflicht (C-03). |
| CR-49 (Aufräumen) | **NOCH VORHANDEN** | → R7W2-07. |
| CR-11 (Rest) | **TEILWEISE** | Typprüfung greift; die stille 0,00 bei fehlendem Schlüssel bleibt → R7W2-08. |
| CR-12, CR-13 | **TEILWEISE** | Keine Absolutgrenze der Sitzung, Lockout-DoS je Kennung (C-10/C-11, MINOR). |
| CR-16 | **NICHT VERIFIZIERBAR** | H-01, Browserprobe fehlt. |
| CR-38 | **NICHT VERIFIZIERBAR** | H-06. |
| CR-47 | **TEILWEISE** | `SELECT ? FROM DUAL` prüft nur die Service-Rückgabe; echte DB-Erwartungen (ID, Status, `FREIGEGEBEN_VON`, Betrag, Journal) fehlen. |
| CR-48 | **WEITGEHEND BEHOBEN** | Unabhängige Nachrechnung im Verifikator; durch die Diesjährungsläufe 500/500 und 42/42 sichtbar. |
| V0.5.8-CR-01 (IBAN optional), CR-02 (Zahlung ohne Bescheid) | **NOCH VORHANDEN** | Zentrale Aussagen dieses Reviews → R7W2-05; IBAN bleibt bei der Anordnung optional und ist durch `SepaValidatorTest` nur syntaktisch abgesichert. |
| Verdacht „Kassenlauf über fremde BEREIT-Zahlungen" | **WIDERLEGT** | Die Simulatorroute ist auf das eigene Aktenzeichen begrenzt (`SzenarioBibliothek:917-960`); die Produktroute `L506` ist absichtlich global (Admin) und jetzt rollengeprüft. Eigene Kassenprobe: `uebergeben=25 storniert=0 rest=0`. |
| Verdacht „XSS" | **WIDERLEGT** | Alle `innerHTML`-Senken nutzen `esc()`; unescaped sind nur Enum- und Zahlwerte; auch im `<title>` escaped. |
| Verdacht „technische Fehler gelten als Negativfall" | **WIDERLEGT** | Mitarbeiter 98 und SzenarioAktion 56–63: falscher Grund oder ausgebliebener Fehler → Fehlschlag. |
| Verdacht „Retry wiederholt fachliche FAILs weg" | **WIDERLEGT** | provisionalFailures bleiben erhalten; PASS mit provisional → UNVERIFIED. **Aber:** R7W2-06 zeigt, dass der formale Korrekturhinweis in einem Fall FAIL zu PASS drehte. |

**Nebenwirkungen früherer Reparaturen (ausdrücklich geprüft):**
- Der Fix gegen formale LLM-Fehler (Korrekturhinweis) erzeugt die Vertragsabweichung R7W2-06.
- Die strengere Typprüfung (CR-11) lässt die Default-0,00-Lücke offen (R7W2-08).
- Die Einmalfortschreibung (v0.5.6) löst das Vermögensproblem nur für nicht deckende Beträge
  und erzeugt die Sperre (R7W2-02).

---

## 6. Priorisierte nächste Schritte

| # | Maßnahme | Wirkung | Aufwand | Abhängigkeit |
|---|---|---|---|---|
| 1 | R7W2-01: HTTP-Antwort an das tatsächliche Worker-Ende binden; Ausgang bei Ablauf sauber als 409 `AUSGANG_UNBEKANNT` ausweisen | beseitigt den schwersten bestätigten Fehlpfad | mittel | keine |
| 2 | R7W2-02: Deckenden Vermögensverbrauch protokollieren und fortschreiben | hebt eine dauerhafte Leistungssperre auf | mittel | fachliche Festlegung der Protokollform |
| 3 | R7W2-05: Betriebsmodus-Schalter „Bescheidpflicht" mit Default produktiv; TF29-Erwartung modusabhängig | bringt Prüfwerkzeug und Produktregel in Einklang | klein–mittel | Entscheidung, ob der Demo-Schalter in die Demo bleibt |
| 4 | R7W2-03: Zuständigkeits-/Teamprüfung im Service und in Listen | Freigabevoraussetzung für Produktivbetrieb | groß | Datenmodell (BG → Team) |
| 5 | R7W2-06: Vertrag und Implementierung des LLM-Retrys in Übereinstimmung bringen; `sha256(basePrompt)` je Versuch binden | macht „gleicher Input" prüfbar | klein | keine |
| 6 | R7W2-04: Durchsatz in Millisekunden, Perzentile, Mindestdauer, Sättigungsstufen | erst dann trägt ein Lastlauf | mittel | keine |
| 7 | R7W2-07: Lauf-ID/Präfix je Lauf, Sperre, Test-DB-Allowlist | verhindert fremden Datenverlust | mittel | keine |
| 8 | R7W2-08: Pflichtfeld- und Skalenprüfung in `betragWert` | verhindert stille 0,00-Ansätze | klein | keine |
| 9 | C-03: `node --test` als Pflichtschicht vor `--publish` in die Pipeline aufnehmen | Zertifikat bindet seine eigenen Urteilsträger | klein | keine |
| 10 | H-01/H-03/H-05/H-06 einlösen (Browser-CSP, Belegkopie ohne Suite, Fake-LLM-Endpunkt, Redaktionsprobe) | schließt die verbleibenden Nachweislücken | klein–mittel | Headless-Browser bzw. lokale Fakes |
| 11 | FB-20261015-01: die vier README-Sätze an den Rechenweg anpassen | beseitigt die letzte Dokumentationsabweichung | klein | keine |
| 12 | Exklusive Prüfumgebung für DB-Stopp/Neustart/Fault-Injection einrichten | schließt die größte methodische Lücke | groß | eigene Instanz, nicht die geteilte DB |
| 13 | Den Arbeitsbaum sichern: die 46 geänderten und 33 neuen Pfade committen oder verwerfen | ohne Commit ist der geprüfte Stand flüchtig; die Behebungen aus Abschnitt 5 existieren dann nur lokal | klein | Entscheidung des Teams, welche Änderungen bleiben |

---

## 7. Anhang

### 7.1 Prüfstand und Hashes

- **Commit-Basis:** `37abfb6f3da841e440ab21a53fbffa7eadf7cd04`, `main`, Commit-Zeit
  2026-10-15 12:12:09 +0200.
- **Tatsächlich geprüft:** dieser Commit **plus** die 46 geänderten und 33 neuen Pfade des
  Arbeitsbaums (46 `M`, 33 `??` zum Prüfzeitpunkt). Der Hash des Prüfstands ist damit **nicht**
  durch den Commit allein beschrieben; maßgeblich ist das Dateiinventar unten.
- **Arbeitskopie:** `C:\Users\tnickel\AppData\Local\Temp\lmg-r7w2\repo` (eingefroren).
- **Quellinventar Start:** 275 Dateien (darunter 236 unter `src/` und `testsweet/`),
  `inventar-start.sha256` (2026-10-15 14:45:18 MESZ).
- **Quellinventar Ende:** 275 Dateien, `inventar-ende.sha256`; **Abweichungen: 0**.
  Aggregat der Einzelhashes (SHA-256 über die verketteten 64-stelligen Hex-Werte):
  `99423589ec45c5948548270ff2628d2505ef3c170d7f92980072d59a27f91c84`.
- **Artefakt:** `transaktionssteuerung-0.5.8.jar`, SHA-256
  `D48498C1B326886D15F011C6DDAB78DECADDB820DF00E09D956A0DD0F42DB922` (identisch im
  isolierten Maven-Head-Repository).
- **Datenbank:** Oracle AI Database 26ai Free **23.26.3.0.0**, JDBC-Treiber ojdbc11
  **23.5.0.24.07**, Container/DB `FREEPDB1`, Nutzer `TX_DEMO`, `DBTIMEZONE +00:00`,
  `SESSIONTIMEZONE Europe/Berlin`. Zählung zu Beginn: BG 28, Zahlung 21, TX_SAGA 246
  (2 nicht abgeschlossen), `SATZSTAND` nur `2026-01`.
- **Laufzeit:** Temurin **25.0.4.1**, Compiler `--release 21`; Maven 3.9.6, Node 22.12.0,
  Python 3.12.10.
- **Fremder Zertifikatslauf (nur gelesen):** Controller-PID 1664632 auf 127.0.0.1:8095,
  Suite `certificate-suite-7b1ef5e5-a3d8-47e1-991d-3c29f1f0c1a5`, Seed 20261014,
  `sourceSha256` `ec1f2804…`. Stand beim Abschluss dieser Prüfung: GLM-Phase, 89 %,
  200 Fälle, `findingsCount=1`, Retry-Statistik
  `{ersterVersuch:28, eineWiederholung:10, zweiWiederholungen:2, gescheiterteFallgruppen:0,
  wiederholteSendungen:14}`, 7 eigene Laufverzeichnisse.

### 7.2 Reproduzierbare Befehle

Isolierte Maven-Repositories (Produktivquellen bleiben unberührt):

```
mvn -o -B clean install "-Dmaven.repo.local=<kopie>\m2head" ^
    "-Dmaven.repo.local.tail=C:\Users\tnickel\.m2\repository"
```

mit den Variablen `TX_URL=jdbc:oracle:thin:@//localhost:1521/FREEPDB1`, `TX_USER=tx_demo`,
`TX_PASS=tx_demo`.

| Lauf | Befehl / Ort | Ergebnis |
|---|---|---|
| Hauptprojekt | `mvn -o -B clean install` (Kopiewurzel) | Exit 0, **351 Tests, 0 Fehler, 1 Skip** |
| Konfiguration ohne `TX_URL` | `mvn -o -B test -Dtest=LmgConfigTest` (ohne `TX_URL`) | Exit 0, **2/2** (der Skip ist damit eingeholt) |
| testsweet | `mvn -o -B clean test` (in `testsweet/`) | Exit 0, **291 Tests, 0 Fehler, 0 Skips** |
| Node-Werkzeug | `node --test` über die 5 Testdateien | **139/139**, ca. 29,7 s |
| Python-Referenz | `python -m unittest -v test_nachrechnen` | **11/11 OK** |
| Simulator 7 Kombinationen | Launcher in der Kopie, Seed 20261015 | **7× FERTIG, erfolg=true, nachweise=true** |
| Kernprobe | `java -cp <classpath> ProbeA all` | P1–P5, siehe 7.3 |
| Rennprobe | `java -cp <classpath> R7w2Rennen 0` bzw. `50` | **VERLETZUNGEN=0** in beiden Versatzstufen |
| Fachprobe | `jshell --class-path <classpath> fach.jsh` | siehe 7.4 |

Der einzige Skip im Hauptlauf ist
`LmgConfigTest.laedtWerteAusPropertiesDatei`, weil `TX_URL` gesetzt war; er wurde separat ohne
`TX_URL` ausgeführt und ist grün. **DB-gated Skips traten in keinem Lauf auf:** alle 15
DB-Suiten des Hauptprojekts und alle 10 DB-Suiten von testsweet liefen mit Zeit > 0.

### 7.3 Frische Laufresultate — Kern- und Rennproben

**Kernprobe (`gegenproben/gegenprobe-kern-ProbeA.log`):**

| Probe | Frage | Ergebnis |
|---|---|---|
| P1 | Verschachteltes `execute` mit ORA-00060 in der inneren Arbeit | äußere Versuche 3, innere Versuche 9, commits 0 — Retry greift korrekt auf **einem** Verbindungsbesitz pro Versuch |
| P2 | Commit wirft `SQLRecoverableException` 17002 | Arbeit **genau einmal** ausgeführt; `retryable=false`, `istTaskOutcomeUnknown=false` → in der Produktabbildung **HTTP 500** statt 409 |
| P2b | dieselbe Lage über `TaskController` + `LmgServer.api`-Abbildung | `TxException -> HTTP 500` |
| P2c | manuelle API: `commit` scheitert, danach `rollback` | `commit` wirft 17002 bei `status=ACTIVE`; `rollback(ctx)` setzt `status=ROLLED_BACK` — **kein Zombie-Zustand** |
| P3 | Wachhund gegen inneren Commit | `result()` nach 300 ms `TaskOutcomeUnknownException`; Worker läuft weiter und **committet** (`commits(innere)=1`) bei `stats.timedOut=1, completed=0` |
| P4 | Pool erschöpft (poolSize=2, maxTasks=2, verschachtelt) | beide Tasks `TxException` nach 1500 ms „Pool erschöpft" — **kein Deadlock, saubere Ablehnung** |
| P5 | Interrupt bei blockierendem Vorlesen (ojdbc11 23.5) | ohne Interrupt: `Socket read timed out` nach 4162 ms; **mit** Interrupt nach 1500 ms: `Socket read interrupted` — der Treiber wertet den Interrupt hier aus |

**Rennprobe (`gegenproben/gegenprobe-rennen-v0.log`, `-v50.log`):**

| Fall | Versatz 0 ms | Versatz 50 ms |
|---|---|---|
| `aendereBescheid` ∥ `ordneAuszahlungAn` (12 Runden) | `{Bescheid \| Auszahlungsergebnis=12}` — **eine** konsistente Wirkung, die Anordnung wurde vom Bescheidschutz abgewiesen | `{Fachfehler(Aktuelle Berechnung stimmt nicht mit dem bewilligten Monatsbetrag …)=12}` |
| Zwei Kassenläufe ∥ zwei Stornos (25 BEREIT-Zahlungen) | `uebergeben=25 storniert=0 rest=0 kassenJournal=1` | `uebergeben=23 storniert=2 rest=0` |
| **VERLETZUNGEN** | **0** | **0** |

Interpretation: Es entsteht in keiner Stufe eine doppelte Übergabe oder ein halber Storno —
die Sperren und die Retry-Policy halten. Unterschiedliche Aufteilungen (25/0 gegen 23/2) sind
legitime Ausgänge des Rennens, keine Verletzung. Der bestehende `ZahlungRennenDbTest`
(15 Runden, Latches, Freigabe∥Storno∥Kassenlauf) lief zusätzlich im Hauptbuild grün.

### 7.4 Frische Laufresultate — Fachnachrechnung

| Konstellation | Nachgerechnet | Bewertung |
|---|---|---|
| A4 mit unter 7-Jährigen | `bedarf=2360.24 … ae=270.24` | 12 % je Kind gedeckelt, kein 36-%-Fall |
| A4-Nr.-1-Konstellation | `bedarf=2474.24 … ae=270.24` | deckt sich mit dem Node-Paritätskorpus |
| A6 (jung / Nr. 1) | `ae=337.80` | Deckel 60 % greift |
| Schwangerschaft, Entbindung 2026-06 | `Juni zahl=658.71`, `Juli zahl=563.00` | Mehrbedarf endet mit dem Entbindungsmonat (§ 30 Abs. 2) |
| Vermögen 10.000,01 / Plan zwei Monate | `V1 zahl=562.99`; `Plan 03 bestand=10000.01 zahl=562.99`; `Plan 04 zahl=563.00` | √ der knappe Fall schreibt fort |
| Vermögen 10.563,00 / Plan zwei Monate | `Deckung einsatz=563.00 zahl=0.00`; `Plan 01/02 zahl=0.00 bestand=10563.00` | **Befund R7W2-02** |
| Satzwechsel 2025-12 → 2026-01 | `rbs=562.00` → `rbs=563.00` | Satzstand wird je Monat aufgelöst |
| KdU-Grenze 500,00 (0 % → 20 %) | `KdU=` 500,00/`unang=false`; `KdU+` 500,00/`unang=true`; `KdU-` 499,99/`unang=false` | Grenze exakt eingehalten |
| Reihenfolgeunabhängigkeit | `AB zahl=1569.00`, `BA zahl=1569.00` (Aufteilung der KdU wechselt, Summe stabil) | kein Reihenfolgefehler |
| Erwerbsfreibetrag an der Schwelle | `938.31 → 281.49`; `938.32 → 281.50`; `938.33 → 281.50` | 100-EUR-Grenze korrekt |

### 7.5 Testzahlen und Skips

| Suite | Tests | Fehler | Skips |
|---|---|---|---|
| Hauptprojekt (mit DB) | 351 | 0 | 1 (`LmgConfigTest`, separat grün) |
| testsweet (mit DB) | 291 | 0 | 0 |
| Node-Werkzeug | 139 | 0 | 0 |
| Python-Referenz | 11 | 0 | 0 |
| Simulator | 7 Kombinationen × vollständige Auftragszahl | 0 | 0 |
| Gegenproben (dieser Review) | 5 Proben mit 12 Einzelaussagen | 0 | 0 |

`AGENTS.md` nennt für testsweet **288** Tests; frisch gezählt sind es **291**. Das ist eine
Dokumentationsdrift von 3 Tests und keine Testlücke; sie ist hier festgehalten, damit die
Zahl nicht weiter als Anker dient. **Historische Zahlen aus früheren Berichten (325, 249, 180,
130) sind kein aktueller Erfolgsnachweis.**

### 7.6 Quellen- und Fassungsnachweise

Alle Rechtsquellen wurden am **2026-10-15** abgerufen und im eingefrorenen Prüfstand
gegengeprüft; die Texte liegen unter `probe-B/recht/` als Belegkopie vor.

| Norm | Fassung/Bemerkung | Fundstelle |
|---|---|---|
| [§ 30 SGB XII](https://www.gesetze-im-internet.de/sgb_12/__30.html) | Abs. 1–7; Abs. 2 Schwangerschaft ab der 13. SSW bis Entbindungsmonat, Abs. 3 Alleinerziehendengünstigervergleich 36 % / 12 % je Kind, Deckel 60 % Stufe 1, Abs. 6/7 Abgrenzung, Warmwasser außerhalb des Deckels | `probe-B/recht/sgb_12___30.txt` |
| [§ 35 SGB XII](https://www.gesetze-im-internet.de/sgb_12/__35.html) | Abs. 5 Satz 1 SGB II-Verweis | `probe-B/recht/sgb_12___35.txt` |
| [§ 41 SGB XII](https://www.gesetze-im-internet.de/sgb_12/__41.html), [§ 42](https://www.gesetze-im-internet.de/sgb_12/__42.html), [§ 42a](https://www.gesetze-im-internet.de/sgb_12/__42a.html), [§ 43](https://www.gesetze-im-internet.de/sgb_12/__43.html), [§ 44](https://www.gesetze-im-internet.de/sgb_12/__44.html) | Verfahren und Leistungen | `probe-B/recht/sgb_12___4*.txt` |
| [§ 82 SGB XII](https://www.gesetze-im-internet.de/sgb_12/__82.html) | Einkommensbegriff und Absetzungen | `probe-B/recht/sgb_12___82.txt` |
| [§ 90 SGB XII](https://www.gesetze-im-internet.de/sgb_12/__90.html) | Abs. 2 Nr. 9 i. V. m. DVO; Einsatz des verwertbaren Vermögens — Grundlage von R7W2-02 | `probe-B/recht/sgb_12___90.txt` |
| [BarbetragsVO / DVO § 90 Abs. 2 Nr. 9](https://www.gesetze-im-internet.de/dvo_90_2_9/) | Grundbetrag 10.000 EUR, 500 EUR je überwiegend unterhaltener Person | `probe-B/recht/dvo90_1.txt` |
| [SGG § 66](https://www.gesetze-im-internet.de/sgg/__66.html), [§ 84](https://www.gesetze-im-internet.de/sgg/__84.html), [§ 87](https://www.gesetze-im-internet.de/sgg/__87.html) | Widerspruch, Klage, Fristen | `probe-B/recht/sgg___*.txt` |
| [§ 35 SGB I](https://www.gesetze-im-internet.de/sgb_1/__35.html) | Datenminimierung/Zweckbindung — Grundlage von R7W2-03 | zitiert in C-01 |
| [§ 44 SGB X](https://www.gesetze-im-internet.de/sgb_10/__44.html), [§ 66 SGB I](https://www.gesetze-im-internet.de/sgb_1/__66.html) | Verwaltungsakt als Grundlage — Grundlage von R7W2-05 | zitiert in C-02 |
| [Art. 25 DSGVO](https://eur-lex.europa.eu/eli/reg/2016/679/oj) | Datenschutz durch Technikgestaltung | zitiert in C-01 |
| [§ 2 RBSFV 2026](https://www.gesetze-im-internet.de/rbsfv_2026/__2.html) | 563/506/451/471/390/357 EUR | geprüft über `SatzstandDbTest` und die Fachprobe |

### 7.7 Belegdateien

```
grundsicherung-gesamt-review-2026-10-15-r7w2-belege/
├── inventar-start.sha256            275 Dateien
├── inventar-ende.sha256             275 Dateien, 0 Abweichungen
├── fremdlauf-status.json            Stand des fremden Zertifikatslaufs (nur gelesen)
├── basissuiten/
│   ├── main-install.log / .meta     351 Tests
│   ├── testsweet-test.log / .meta   291 Tests
│   ├── node-test.log / .meta        139 Tests
│   ├── python-test.log / .meta      11 Tests
│   ├── simulator-7.log / .meta      die 7 Kombinationen
│   └── configtest.log               LmgConfigTest ohne TX_URL (2/2)
├── gegenproben/
│   ├── gegenprobe-kern-ProbeA.log / .meta        P1–P5
│   ├── gegenprobe-rennen-v0.log / .meta          Rennprobe ohne Versatz
│   ├── gegenprobe-rennen-v50.log / .meta         Rennprobe mit 50 ms Versatz
│   ├── gegenprobe-fach-jshell.log                Fachnachrechnung
│   └── agentC-bericht-web-zertifikat.md          Teilbericht C (Web/Kette)
└── laeufe/                          7 Laufverzeichnisse mit zusammenfassung.txt und status.json
```

**Keine Zugangsdaten und keine realen Sozialdaten in diesem Ordner.** Alle simulierten Fälle
tragen synthetische Aktenzeichen (`SWEET-%`, `BG-R7W2-%`); die Eigennachweise dieses Reviews
wurden auf `BG-R7W2-%` begrenzt und nach den Proben entfernt.
