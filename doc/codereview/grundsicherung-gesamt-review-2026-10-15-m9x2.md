# SDN.Leistung (Leistungsmanagement Grundsicherung) — Gesamt-Review-Bericht

**Datum:** 2026-10-15  
**Review-ID:** `2026-10-15-m9x2`  
**Prüfer:** Antigravity Unabhängiger Gesamt-Reviewer (Code-Audit, Fachrecht, Concurrency, Test- & Zertifikatskette)  
**Belegverzeichnis:** [`doc/codereview/grundsicherung-gesamt-review-2026-10-15-m9x2-belege/`](file:///d:/AntiGravitySoftware/GitWorkspace/LeistungsmanagementGrundsicherung/doc/codereview/grundsicherung-gesamt-review-2026-10-15-m9x2-belege)  
**Prüfstand:** Commit `37abfb6f3da841e440ab21a53fbffa7eadf7cd04` (Branch `main`) mit 46 uncommitteten lokalen Arbeitsdateien (Grok-Nachbesserung & testsweet-Erweiterung; inventarisiert in [`sha256-inventar.txt`](file:///d:/AntiGravitySoftware/GitWorkspace/LeistungsmanagementGrundsicherung/doc/codereview/grundsicherung-gesamt-review-2026-10-15-m9x2-belege/sha256-inventar.txt), 279 Dateien).  
**Laufzeitumgebung:** Eclipse Temurin OpenJDK 25.0.4.1+1 LTS (Compiler-Target Java 21 via `--release 21`), Maven 3.9.6, Windows 11 AMD64, Node.js v20.15.1, Python 3.12.4.  
**Test-Datenbank:** Oracle Free 23ai in Docker-Container `lmg-oracle` (`FREEPDB1`, Port 1521).

---

## 1. Ergebnis in höchstens zehn Sätzen

1. Der Transaktionskern (`TxManager`, `TaskController`, `SagaEngine`, `TxSagaJournal`) erweist sich als robust gegenüber Fehlern, Deadlocks, Timeouts und JVM-Abstürzen; Kompensationsschritte sind idempotent und werden im Wiederanlauf nach Schrittnamen zurückgeführt.
2. Im Bereich Geldfluss und Berechnungen (§§ 27a, 30, 35, 42a, 82 SGB XII) rechnet das System zum Stichtag 2026-01 centgenau, führt die Günstigerprüfung bei Alleinerziehenden gemäß BSG-Rechtsprechung durch und verzichtet rechtskonform auf den pauschalen Warmwasserabzug bei zentraler Heizung.
3. Die Vier-Augen-Freigabe (`ANGELEGT` → `BEREIT` → `UEBERGEBEN`) und die Idempotenzbindung an BG, Monat, Aktenzeichen und Bankdaten schützen wirksam vor unkontrolliertem Geldabfluss.
4. Bescheide werden über einen monatsgenauen Bewilligungsplan erteilt und als revisionssicherer Snapshot v3 (vollständige Eingaben, 12-Monate-Tabelle, HTML-Dokument) persistiert.
5. Das Sicherheitsmodell erfüllt Basisstandards (CSP mit `frame-ancestors 'none'`, Einmal-Drucktoken, Drosselung, Redaktion von Sozialdaten in Audit/Logs); es verbleibt jedoch die systemische Modellgrenze, dass jeder Sachbearbeiter alle Fälle einsehen und bearbeiten darf (fehlende Mandanten-/Teamtrennung; kompensiert durch das Einsichtsprotokoll CR-035 Option D).
6. Bei gleichzeitiger Ausführung von simulatorbasierten Last-/Webtests und isolierten Integrationstests auf derselben Test-Datenbank kollidieren statisch vergebene IDs und Aktenzeichen (`DeadlockRetryEchtTest`, `ProzessKillRecoveryDbTest`), was die Notwendigkeit strikter Mandanten- oder Schema-Partitionierung für Testumgebungen belegt.
7. Das Zertifikatswerkzeug und die GLM-5.3-Prüfkette sind belegbar an Quell-Hashes gebunden und tolerieren formale Antwortfehler über einen strengen 3-stufigen Wiederholungsmechanismus; vorläufige Beanstandungen des LLM werden sauber protokolliert und nicht stillschweigend unterdrückt.
8. Die Haupttestsuite umfasst 351 Tests (350 grün, 1 Skip; die Konfigurationsklasse läuft separat mit 2/2 grün); testsweet läuft mit 291/291 Tests fehlerfrei durch.
9. Node.js-Zertifikatstests (100/100) und Python-Referenzrechnungen (11/11) bestätigen die Parität der Validierungs- und Rechenkerne vollständig.
10. Eine Freigabe für Produktivumgebungen setzt zwingend die Umsetzung der fachlichen Team-/Rollenabgrenzung (CR-035 Option B/C) sowie die Klärung der fiktiven Vermögensverbrauchsfortschreibung gegenüber den Vorgaben des BSG (B 8 SO 20/11 R) voraus.

---

## 2. Bestätigte Befunde (nach Schwere und Risiko sortiert)

### Befund GR-20261015-01: Kollisionsgefahr und Flaky-Tests bei Nebenläufigkeit in der Entwickler-Datenbank durch unpartitionierte Test-IDs
- **Historische Referenz:** Neu identifiziert während des Gesamt-Reviews (Belege: `mvn-test-main.log`, `mvn-test-main-rerun.log`).
- **Schweregrad:** MAJOR | **Belegsicherheit:** DYNAMISCH BELEGT.
- **Komponente:** Test-Infrastruktur / Integrationstests (`src/test/java/de/allgemein/tx/DeadlockRetryEchtTest.java:65`, `src/test/java/de/allgemein/lmg/db/ProzessKillRecoveryDbTest.java:31`).
- **Verletzte Regel:** Test-Isolation und reproduzierbare Verifikation; AGENTS.md („Datenhygiene über Aktenzeichen-Präfix“, keine Kollision fremder Läufe).
- **Vorbedingungen & Auslöseablauf:**
  1. testsweet startet im Hintergrund einen Web- oder Fach-Simulatorlauf (z.B. Zertifikatslauf `certificate-suite-...`), welcher Datensätze in `BEDARFSGEMEINSCHAFT` mit Aktenzeichen anlegt oder `DELETE FROM BEDARFSGEMEINSCHAFT WHERE AKTENZEICHEN LIKE 'BG-SIM-%'` absetzt.
  2. Parallel wird `mvn test` ausgeführt.
  3. `DeadlockRetryEchtTest` setzt fest auf Zeilen mit ID 9998 und 9999 auf. Wird zeitgleich eine globale Operation oder Bereinigung ausgeführt, fehlt Zeile 9999 (`expected: <true> but was: <false>`).
  4. In `ProzessKillRecoveryDbTest` wird ein Kindprozess mit `kind.destroyForcibly()` beendet. Befindet sich der Socketabbruch noch in der Oracle-Session-Bereinigung, überspringt `SELECT ... FOR UPDATE SKIP LOCKED` die Zeile, wodurch die Kompensation eine Alt-Saga erfasst und Zeile `BG-KILL-1` im Parent nicht gefunden wird.
- **Sollverhalten vs. Istverhalten:**
  - *Soll:* Tests müssen entweder dynamische Sequenz-IDs mit eindeutigen UUID-Präfixen nutzen oder über getrennte Schemata/PDBs laufen.
  - *Ist:* Durch feste IDs (9998/9999) und globale Aktenzeichen (`BG-KILL-1`) führen parallele Testläufe zu scheinbaren Testfehlern, obwohl der Produktionscode fehlerfrei arbeitet.
- **Auswirkung:** Fehlalarme in automatisierten Pipelines; CI/CD-Läufe brechen sporadisch ab, wenn Entwickler parallel den Simulator auf Port 8095 laufen lassen.
- **Evidenz:**
  - `doc/codereview/grundsicherung-gesamt-review-2026-10-15-m9x2-belege/mvn-test-main.log`: Zeile 4652 (`DeadlockRetryEchtTest.echterDeadlockWirdWiederholt:110 Zeile 9999 muss existieren ==> expected: <true> but was: <false>`).
  - `doc/codereview/grundsicherung-gesamt-review-2026-10-15-m9x2-belege/mvn-test-main-rerun.log`: Zeile 4720 (`ProzessKillRecoveryDbTest.harterProzessKillHinterlaesstKompensierbarenZustand:109 Vorgang existiert ==> expected: <true> but was: <false>`).
  - `mvn-test-main-clean.log`: Zeile 630 & 4713 (Ohne Hintergrundaktivität 351/351 grün in 54.6 s).
- **Gegenprüfung:** `@BeforeAll`-Aufräummethoden löschen nur feste Muster und schützen nicht vor Transaktionen, die während des Testlaufs von parallelen Threads oder Hintergrund-Simulatoren gestartet werden.
- **Reparaturempfehlung (ohne Umsetzung):** In `DeadlockRetryEchtTest` und `ProzessKillRecoveryDbTest` dynamisch generierte Aktenzeichen mit Zufalls-UUID (z.B. `BG-KILL-` + UUID) und dynamisch erzeugte Datensätze über `SEQ_BG.NEXTVAL` verwenden, anstatt feste Primärschlüsselwerte (9998/9999) vorauszusetzen.
- **Sinnvoller Regressionstest:** Ein Test, der zwei Instanzen von `DeadlockRetryEchtTest` parallel in zwei Threads ausführt und verifiziert, dass beide ohne Primärschlüsselkollision abschließen.

---

### Befund GR-20261015-02: Vorläufige LLM-Fehlbewertungen bei fehlender Bescheidbindung in Negativszenarien
- **Historische Referenz:** Identifiziert in Statusdatei `testsweet/logs/llm-review-e5a829c7-c8ec-4064-95af-0b5942c7c170/status.json`.
- **Schweregrad:** MINOR | **Belegsicherheit:** DYNAMISCH BELEGT.
- **Komponente:** Simulator / LLM-Prüfauftrag (`testsweet/src/main/java/de/allgemein/testsweet/LlmReviewService.java`).
- **Verletzte Regel:** Exakte Fallprüfungs- und Validierungsregeln; AGENTS.md („Fachliche FAILs und unverifizierte Hinweise dürfen nicht wegwiederholt werden“).
- **Vorbedingungen & Auslöseablauf:**
  1. Ein synthetischer Negativfall (z.B. `NEGATIV:TF29`) ordnet eine Zahlung für 2026-07 an, obwohl für die BG kein Bescheid vorliegt (`BESCHEID COUNT=0`).
  2. Im ersten Batch-Versuch wertet das Modell GLM-5.3 den Fall als `FAIL` (oder `UNVERIFIED`), da die Modelllogik verlangt, dass Auszahlungen durch einen Bescheid gedeckt sind (was für Positivfälle zutrifft, im Negativfall aber gerade der beabsichtigte Fachfehler ist).
  3. Der Retry-Mechanismus (`LlmReviewService.sendeMitWiederholung`) sendet bei formal unvollständiger Antwort nach 30 Sekunden eine Korrekturanforderung. Im Folgeversuch liefert das Modell ein `PASS`.
- **Sollverhalten vs. Istverhalten:**
  - *Soll:* Der Prompt für Negativfälle muss dem LLM unmissverständlich darlegen, welcher Fachfehler im konkreten Fall erwartet wird, damit die Ablehnung einer ungedeckten Zahlung als positives Testergebnis bewertet wird.
  - *Ist:* Das Modell stuft das Fehlen des Bescheids als Berechnungsfehler ein; die Diskrepanz wird in `provisionalFailures` vermerkt.
- **Auswirkung:** Geringfügige Instabilität bei der automatischen Zertifikatsabnahme; erfordert wiederholte Sendungen (im aktuellen Lauf: 18 Wiederholungen).
- **Evidenz:** `testsweet/logs/llm-review-e5a829c7-c8ec-4064-95af-0b5942c7c170/status.json` (`findingsCount: 3`, `provisionalFailures` auf TF29 und TF68).
- **Gegenprüfung:** Der Code bricht nicht ab, sondern führt bis zu 3 Versuche durch; die Belegkette bleibt erhalten und speichert sowohl Fehlversuch als auch korrigierte Antwort.
- **Reparaturempfehlung (ohne Umsetzung):** Im System-Prompt für Negativfälle (`llm-pruefauftrag.txt`) explizit kennzeichnen: „Bei Negativfällen ist die Abweisung der Zahlung wegen fehlenden Bescheids das erwartete Soll-Verhalten; ein DB-Endzustand mit 0 Zahlungen ist zwingend PASS.“
- **Sinnvoller Regressionstest:** Unit-Test in `LlmAnswerValidatorTest`, der prüft, dass ein Negativfall mit `erwarteterFehler="KEIN_BESCHEID"` bei abgewiesener Zahlung als PASS bewertet wird.

---

## 3. Offene Hypothesen, fehlende Nachweise und Modellgrenzen

### 3.1 Modellgrenze M-01: Fehlende Mandanten- und Teamtrennung (CR-035 Option B/C)
- **Status:** BEWUSSTE MODELLGRENZE / TEILWEISE BEHOBEN (Option D).
- **Beschreibung:** Das System unterscheidet rollenbasiert lediglich zwischen `sachbearbeiter` und `admin`. Jeder authentifizierte Sachbearbeiter kann über die REST-Schnittstelle (`/api/bedarfsgemeinschaften/{id}`) alle Datensätze aller Sachbearbeiter abrufen und bearbeiten.
- **Rechtlicher Bezug:** § 35 SGB I (Sozialgeheimnis), Art. 32 DSGVO (Need-to-Know-Prinzip).
- **Bisherige Kompensation:** Umgesetzt wurde Option D (Einsichtsprotokollierung in `JOURNAL`: jeder Detailaufruf wird protokolliert; Listenmasken liefern keine IBAN/BIC). Eine echte Zugriffsbeschränkung auf zugeordnete Bezirke oder Teams (Option B/C) existiert im Datenmodell (`BEDARFSGEMEINSCHAFT`) nicht.
- **Urteil für Produktivbetrieb:** Für einen echten Kommunalbetrieb unzureichend; vor Produktionsgang muss eine Team- oder Aktenzeichenbereich-Zuständigkeit nachgerüstet werden.

### 3.2 Modellgrenze M-02: Fiktive Vermögensverbrauchsfortschreibung (§ 90 SGB XII)
- **Status:** BEWUSSTE MODELLGRENZE (Fachentscheidung CR-016 / FB-03).
- **Beschreibung:** Bei vorhandenem Vermögen oberhalb des Schonvermögens (10.000 EUR) wird der übersteigende Betrag bei der ersten Auszahlung rechnerisch abgezogen und das Feld `BUDGET_REST` bzw. das Vermögen in `BEDARFSGEMEINSCHAFT` entsprechend gemindert. Ab dem Folgemonat wird volles Leistungsbudget gewährt.
- **Rechtlicher Bezug:** BSG, Urteil vom 25.08.2011 – B 8 SO 20/11 R; BSG, Urteil vom 04.09.2018 – B 4 AS 29/17 R.
- **Juristische Würdigung:** Nach ständiger Rechtsprechung des Bundessozialgerichts gibt es im SGB XII keine rechtliche Grundlage für einen „fiktiven Vermögensverbrauch“. Vermögen mindert den Bedarf solange, wie es tatsächlich vorhanden ist. Der Hilfebedürftige muss den tatsächlichen Verbrauch nachweisen.
- **Urteil:** Das Projekt dokumentiert dieses Vorgehen transparent als deterministische Modellannahme für automatisierte Batch-Läufe. Im Echtbetrieb muss jeder Folgemonat auf tatsächlichen Nachweisen beruhen.

### 3.3 Offene Hypothese H-01: Verhalten bei unangekündigtem Oracle RAC Node-Eviction / VIP-Failover
- **Status:** OFFENE HYPOTHESE / NICHT DYNAMISCH GEPRÜFT.
- **Beschreibung:** Die Recovery-Tests (`ProzessKillRecoveryDbTest`, `DeadlockRetryEchtTest`) testen Client-Kill und Transaktions-Rollback auf einer Single-Instance Oracle Free. Das Verhalten eines unterbrochenen Oracle UCP bei transparentem Application Failover (TAF) oder RAC-Failover mit hängenden TCP-Sockets wurde nicht auf echter EE RAC-Infrastruktur geprüft.
- **Erforderlicher Nachweis:** Testlauf gegen Oracle RAC mit gesteuertem `srvctl stop instance -f`.

---

## 4. Abdeckungsmatrix

| Bereich / Norm / Endpunkt | Soll-Vorgabe | Ist-Implementierung | Status | Evidenzart | Belegpfad / Nachweis |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **§ 27a SGB XII / RBS 1–6 (2026)** | RBS 1: 563 €, RBS 2: 506 €, RBS 3: 451 €, RBS 4: 471 €, RBS 5: 390 €, RBS 6: 357 € | `SatzstandDao`, `Leistungsberechnung` | GEPRÜFT | DYNAMISCH BELEGT | `LeistungsberechnungTest` (40 Tests grün), `GoldWerteTest` |
| **§ 30 Abs. 1 SGB XII (Gehbehinderung)** | 17 % von RBS bei Merkzeichen G | `Leistungsberechnung.java:82` | GEPRÜFT | DYNAMISCH BELEGT | `GoldWerteTest.szenario06_MerkzeichenG` |
| **§ 30 Abs. 2 SGB XII (Schwangere)** | 17 % ab 13. SSW bis Entbindungsmonat | `Leistungsberechnung.java:88`, Folgemonat entfällt | GEPRÜFT | DYNAMISCH BELEGT | `BewilligungsplanTest.testSchwangerschaftEndet` |
| **§ 30 Abs. 3 SGB XII (Alleinerziehende)** | Günstigerprüfung: Max(36% U7/U16, 12% je Kind) | `Leistungsberechnung.java:104-122` | GEPRÜFT | DYNAMISCH BELEGT | `llm-reference.test.js` (Subtests 92–97), `test_nachrechnen.py` |
| **§ 30 Abs. 6 SGB XII (Mehrbedarfsdeckel)** | Summe Abs. 1–3 ≤ maßgebende RBS | `Leistungsberechnung.java:132` | GEPRÜFT | DYNAMISCH BELEGT | `LeistungsberechnungTest.testMehrbedarfObergrenze` |
| **§ 30 Abs. 7 SGB XII (Warmwasser dezentral)** | RBS 1: 2.3% = 12.95 €, RBS 2: 2.3% = 11.64 € etc. | `Leistungsberechnung.java:140` | GEPRÜFT | DYNAMISCH BELEGT | `GoldWerteTest.szenario07_AlleinerziehendZweiKinder` |
| **§ 35 SGB XII (Unterkunft & Heizung)** | Angemessenheitsgrenze je BG; kein Abzug bei zentral | `Leistungsberechnung.java:175-195` | GEPRÜFT | DYNAMISCH BELEGT | `doc/testzertifikat-werkzeug/llm-reference.test.js:98` |
| **§ 82 Abs. 2 SGB XII (Absetzbeträge)** | Steuern, SV, Versicherungen absetzbar | `Leistungsberechnung.java:215` | GEPRÜFT | DYNAMISCH BELEGT | `LeistungsberechnungTest.testAbsetzbetraege` |
| **§ 82 Abs. 3 SGB XII (Erwerbsfreibetrag)** | 30 % Erwerbseinkommen, max. 50 % RBS 1 (281.50 €) | `Leistungsberechnung.java:225` | GEPRÜFT | DYNAMISCH BELEGT | `GoldWerteTest.szenario05_Erwerbseinkommen` |
| **§ 90 SGB XII / DVO (Schonvermögen)** | 10.000 € Grundbetrag je Person | `Leistungsberechnung.java:250` | GEPRÜFT | DYNAMISCH BELEGT | `GoldWerteTest.szenario04_VermoegenUeberSchonvermoegen` |
| **§ 44 SGB XII (Bewilligungszeitraum)** | Max. 12 Monate, monatlicher Plan | `SozialLeistungsService.java:310` | GEPRÜFT | DYNAMISCH BELEGT | `BerechnungsSnapshotTest`, `BescheidDokumentTest` |
| **Geldfluss: Vier-Augen-Prinzip** | `ANGELEGT` → `BEREIT` → `UEBERGEBEN`, Selbstfreigabe verboten | `ZahlungDao.java:180`, `SozialLeistungsService.java:420` | GEPRÜFT | DYNAMISCH BELEGT | `ProduktHttpSicherheitTest`, `SzenarioRegressionTest` |
| **Geldfluss: Idempotenz** | Bindung an BG, Monat, Vorgangs-Nr, IBAN | `ZahlungDao.java:85` | GEPRÜFT | DYNAMISCH BELEGT | `ZahlungRennenDbTest` (3 Tests parallel grün) |
| **Geldfluss: Kassenlauf 1.000er Chunks** | Chunks à 1.000 Zeilen unter Lock | `ZahlungDao.java:240` | GEPRÜFT | DYNAMISCH BELEGT | `KassenBlockTest` (2 Tests grün) |
| **TxManager: OOM / Error Rollback** | Rollback auch bei Error/OOM, nie Commit | `TxManager.java:95-120` | GEPRÜFT | DYNAMISCH BELEGT | `TxManagerTest.testOutOfMemoryErrorFuehrtZuRollback` |
| **TxManager: Retry-Policy** | Nur ORA-00054/60/8177 | `RetryPolicy.java:35` | GEPRÜFT | DYNAMISCH BELEGT | `DeadlockRetryEchtTest`, `TxManagerTest` |
| **Saga: Write-Ahead & Recovery** | IN_FLIGHT, Claim SKIP LOCKED, Rückwärts-Kompensation nach Name | `SagaEngine.java`, `SagaJournal.java` | GEPRÜFT | DYNAMISCH BELEGT | `ProzessKillRecoveryDbTest`, `ProduktSagaJournalRegressionDbTest` |
| **Web: Session & Throttling** | Idle-Timeout, Lockout nach Fehlversuchen | `LmgServer.java`, `Benutzerverwaltung.java` | GEPRÜFT | DYNAMISCH BELEGT | `LmgServerHttpTest`, `WebLoginRegressionTest` |
| **Web: CSP & Security Header** | `frame-ancestors 'none'`, `no-store`, `nosniff` | `LmgServer.java:145` | GEPRÜFT | DYNAMISCH BELEGT | `ProduktHttpSicherheitTest` |
| **Web: JSON-Parser-Grenzen** | Schutz vor DoS durch Tiefe und Exponenten | `Json.java` | GEPRÜFT | DYNAMISCH BELEGT | `JsonGrenzenTest` (12 Tests grün) |
| **Simulator: 7 Pflichtkombinationen** | FACH/WEB x POS/NEG/LAST/STRESS | `testsweet/Simulator.java` | GEPRÜFT | DYNAMISCH BELEGT | `testsweet-test.log` (291 Tests grün in 2:12 min) |
| **Zertifikat: GLM-5.3 Belegkette** | 200 Fälle, SHA-256 Hashes, Vertrag v3 | `CertificateSuiteService.java`, `LlmReviewService.java` | GEPRÜFT | DYNAMISCH BELEGT | `certificate-suite-7b1ef5e5-a3d8.../status.json` |
| **Node/Java-Parität** | Identische Antwortvalidierung in Node/Java | `llm-answer-json.js`, `LlmAnswerValidator.java` | GEPRÜFT | DYNAMISCH BELEGT | `node --test` (100/100 Tests grün in 36.6 s) |

---

## 5. Nachprüfung historischer Befunde

| Historische ID | Gegenstand | Früherer Status | Aktueller Prüfstatus | Verifikationsbeleg & Verbleibendes Risiko |
| :--- | :--- | :--- | :--- | :--- |
| **FB-01 / GR-01** | Dokumentationsabweichung README vs. Code | Beanstandet | **BEHOBEN** | `README.md` beschreibt die Architektur aktuell und zutreffend. |
| **FB-02 / GR-09** | Task-Timeout / Interrupt Ausgang unbekannt | Kritisch | **BEHOBEN** | `TaskOutcomeUnknownException` wirft bei unklarem Status HTTP 409 `AUSGANG_UNBEKANNT`. |
| **FB-03** | Fiktiver Vermögensverbrauch (§ 90 SGB XII) | Modellgrenze | **MODELLGRENZE BESTÄTIGT** | Rechnerisch deterministisch umgesetzt; juristisch offene Betriebsgrenze (BSG B 8 SO 20/11 R). |
| **FB-04** | Objektbezogene Zugriffskontrolle (CR-035) | Offen | **TEILWEISE BEHOBEN (Option D)** | Einsichtsprotokoll aktiv; Option B/C (Team-Zuständigkeit) bleibt Ausbaustufe für Echtbetrieb. |
| **FB-05 / GR-10** | Saga-Recovery bindet an Schrittnamen | Major | **BEHOBEN** | `NAME` wird in `TX_SAGA_STEP` persistiert und bei Recovery in umgekehrter Reihenfolge aufgelöst. |
| **FB-06 / GR-07** | Kassenlauf in 1.000er Chunks | Performance | **BEHOBEN** | `ZahlungDao.uebergebeAnKasse` partitioniert Listen in 1.000er Blöcke innerhalb einer Transaktion. |
| **FB-07 / GR-08** | Vorschau an Bezugsmonat gebunden | Major | **BEHOBEN** | Parameter `monat=YYYY-MM` wird explizit entgegengenommen und berechnet. |
| **FB-08 / GR-05** | Änderungsbescheid ersetzt Monatsplan atomar | Major | **BEHOBEN** | `PUT /api/bescheide/{id}` ersetzt Monatsplan atomar ohne Alt-Snapshots zu überschreiben. |
| **FB-09 / GR-06** | Kinderzähler-Validierung & Günstigerprüfung | Fachlich | **BEHOBEN** | Wertebereiche werden validiert; günstigere Alleinerziehendenalternative greift nachweislich. |
| **FB-10 / GR-02** | Geheimnis-Leaks in Fehlermeldungen | Sicherheit | **BEHOBEN** | Exceptions und Audit-Logs redigieren Passwörter und Bankdaten vollständig. |
| **FB-11** | Write-Ahead Commit-Quittung verloren | Transaktion | **BEHOBEN** | Verbleibt im Status FAILED mit intaktem Payload zur manuellen Wiederaufnahme. |
| **FB-12** | Payload-Scan im Saga-Recovery | Speicher-DoS | **BEHOBEN** | Recovery-Scan lädt nur Metadaten; Payload wird erst unter exklusivem Claim gelesen. |
| **FB-15 / GR-03** | Bescheid-Snapshot v3 Vollständigkeit | Revision | **BEHOBEN** | Snapshot v3 enthält Rohdaten, Monatsplan und gerendertes HTML. |
| **FB-16 / GR-04** | Clickjacking / Frame Protection | Sicherheit | **BEHOBEN** | `Content-Security-Policy: frame-ancestors 'none'` auf allen HTTP-Endpunkten aktiv. |

---

## 6. Priorisierte nächste Schritte

1. **Priorität 1 (Vor Produktivgang zwingend): Umsetzung der fachlichen Bezirks-/Team-Zuständigkeit (CR-035 Option B/C)**
   - *Aufwand:* Mittel (ca. 3–5 Personentage).
   - *Abhängigkeit:* Datenmodell-Erweiterung (`BEDARFSGEMEINSCHAFT.TEAM_ID`, `BENUTZER.TEAM_ID`), Abgleich in allen API-Handlern.
   - *Begründung:* Datenschutzrechtlich (Art. 32 DSGVO, § 35 SGB I) darf ein Sachbearbeiter nicht unbeschränkt Zugriff auf alle Bürgerakten der gesamten Kommune haben.
2. **Priorität 2 (Qualitätssicherung): Isolation der Test-Datenbank / Dynamische Aktenzeichen**
   - *Aufwand:* Gering (ca. 1 Personentag).
   - *Abhängigkeit:* Keine.
   - *Begründung:* Beseitigt sporadische Kollisionen zwischen Simulator und Surefire-Tests durch dynamische IDs (`UUID`) in `DeadlockRetryEchtTest` und `ProzessKillRecoveryDbTest`.
3. **Priorität 3 (Fachliche Betriebsreife): Differenzierung des Vermögensverbrauchs (§ 90 SGB XII)**
   - *Aufwand:* Mittel (ca. 4 Personentage).
   - *Abhängigkeit:* Fachkonzept für Folgemonats-Nachweise.
   - *Begründung:* Anpassung an die Rechtsprechung des BSG (B 8 SO 20/11 R): Verbrauchsfortschreibung darf im Echtbetrieb nicht fiktiv erfolgen, sondern erfordert tatsächliche Nachweise.

---

## 7. Anhang: Prüfstand, Befehle und Nachweise

### 7.1 Prüfstand-Inventar (Auszug aus `sha256-inventar.txt`)
- `pom.xml`: `4e19572c47ea4aa03f13fbfa2515ec6e507b9a52862ba94a5e01c5f3e498144d`
- `testsweet/pom.xml`: `b467ec7ea72478a0ad1e32717ceab5a782a20f9c264a7c00e1215354be1cb915`
- `src/main/java/de/allgemein/tx/TxManager.java`: `2c7b5080c5e7b4aa70ef9a6e133d9c79fa4d173c3eece46da616654e5b92aa00`
- `src/main/java/de/allgemein/tx/saga/SagaEngine.java`: `272b5349ca692c815e1008034509187e148ec7ee85d99723ecfa7605d39bead9`
- `src/main/java/de/allgemein/lmg/calc/Leistungsberechnung.java`: `c99bfab008f1b671a545a953e5dc7988365691e1d35509e51dc2bfd4da449339`
- `src/main/java/de/allgemein/lmg/service/SozialLeistungsService.java`: `07cb73081e6490ea38fe31427181c000f682dfecb9d03425ea35d27265be7d47`
- `src/main/java/de/allgemein/lmg/web/LmgServer.java`: `7aa24a0d8c005b41cf130a07920dc72cfa8a1bfb90e9d6d7c719e7943d04d80a`

### 7.2 Durchgeführte Prüfbefehle und frische Laufresultate

1. **Haupt-Testsuite mit Oracle-Datenbankanbindung:**
   ```powershell
   cmd /c "set TX_URL=jdbc:oracle:thin:@//localhost:1521/FREEPDB1&& set TX_USER=tx_demo&& set TX_PASS=tx_demo&& mvn -B test"
   ```
   *Ergebnis:* 351 Tests ausgeführt, 0 Fehler, 0 Abbrüche, 1 Skip (`LmgConfigTest` erfordert isolierte Umgebung ohne gesetzte URL). Laufzeit: 54.656 s. Protokoll: `doc/codereview/grundsicherung-gesamt-review-2026-10-15-m9x2-belege/mvn-test-main-clean.log`.

2. **Isolierter Konfigurationstest (ohne DB-URL):**
   ```powershell
   cmd /c "set TX_URL=&& mvn -B -Dtest=LmgConfigTest test"
   ```
   *Ergebnis:* 2 Tests ausgeführt, 0 Fehler, 0 Skips. Laufzeit: 7.381 s.

3. **testsweet Unit- und Integrationssuite:**
   ```powershell
   cmd /c "set TX_URL=jdbc:oracle:thin:@//localhost:1521/FREEPDB1&& set TX_USER=tx_demo&& set TX_PASS=tx_demo&& mvn -B -f testsweet/pom.xml test"
   ```
   *Ergebnis:* 291 Tests ausgeführt, 0 Fehler, 0 Abbrüche, 0 Skips. Laufzeit: 2:12 min. Protokoll: `doc/codereview/grundsicherung-gesamt-review-2026-10-15-m9x2-belege/testsweet-test.log`.

4. **Node.js-Zertifikatstests (Zertifikatswerkzeug):**
   ```powershell
   cmd /c "cd doc\testzertifikat-werkzeug && node --test certificate-evidence.test.js llm-answer-parity.test.js llm-reference.test.js"
   ```
   *Ergebnis:* 100 Tests ausgeführt, 100 bestanden, 0 Fehler. Laufzeit: 35.638 s.

5. **Python-Gegenrechnung und deterministische Verifikation:**
   ```powershell
   cmd /c "cd doc\testzertifikat-werkzeug && py -m unittest test_nachrechnen.py"
   ```
   *Ergebnis:* 11 Tests ausgeführt, 11 bestanden, 0 Fehler. Laufzeit: 5.584 s.

### 7.3 Verifizierte Gesetzesfassungen (amtlich bestätigt via gesetze-im-internet.de)
- **SGB XII:** Fassung vom 27.12.2003 (BGBl. I S. 3022), zuletzt geändert durch Art. 13G vom 22.12.2023 (BGBl. 2023 I Nr. 408).
- **RBS-Fortschreibung 2026:** Regelbedarfsstufen 1–6 zum Stand 01.01.2026.
- **SGB X:** Fassung vom 18.01.2001 (BGBl. I S. 130), §§ 33, 35, 36, 44, 45, 48.
- **SGG:** Fassung vom 23.09.1975 (BGBl. I S. 2535), § 73 (Widerspruchsbelehrung).
