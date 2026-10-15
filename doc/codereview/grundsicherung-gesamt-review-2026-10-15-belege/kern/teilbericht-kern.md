# Teilbericht Transaktionskern, Saga, Pools und Oracle – 2026-10-15

Prüfgegenstand: Arbeitsbaum zu Commit `3c909c29e22f90a7208f74e835614e1878056eff`, Branch `main`, zuzüglich der im zentralen Ausgangsinventar dokumentierten Änderungen. Der Teilbereich umfasst `src/main/java/de/allgemein/tx/`, zugehörige Kerntests, die beiden DDL-Dateien sowie Start- und Konfigurationsschnittstellen. Es wurden keine Produktdateien, bestehenden Tests, Konfigurationen oder Datenbankdaten geändert. Maven-/Oracle-Läufe wurden dem koordinierenden Review überlassen.

Zuerst erfolgte die eigene Analyse der aktuellen Implementierung, anschließend die Gegenprüfung historischer Befunde. Die isolierte Probe ist mit `javac --release 21` frisch aus den aktuellen Produktquellen kompiliert und unter **JDK 25.0.4.1** ausgeführt. Sie belegt keinen Lauf unter JDK 21 und keine echte Oracle-Transaktion. Für die SQLState-Prüfung wurde jedoch der tatsächlich eingebundene Oracle-Treiber `ojdbc11-23.5.0.24.07.jar` direkt verwendet.

## Bestätigte Befunde

### K-01 – MAJOR: Eine Saga ohne begonnenen Fachschritt blockiert den Wiederanlauf dauerhaft

**Belegsicherheit:** dynamisch belegter Engine-Pfad mit Journal-Mock; vollständiger statischer Pfad bis zum Server-Startabbruch. Ein frischer Oracle-Crashversuch an genau dieser Grenze wurde in diesem Teilreview nicht ausgeführt.

**Fundstellen:** `SagaEngine.java:64–70`, `SagaEngine.java:293–300`; `SagaJournal.java:52–77`, `SagaJournal.java:194–204`; `LmgServerMain.java:81–85`, `LmgServerMain.java:164–171`.

Vorbedingung und Ablauf:

1. `SagaEngine.run()` lässt `journal.start()` Kopf und PENDING-Schritte dauerhaft speichern.
2. Der Prozess bricht vor dem ersten erfolgreich gespeicherten IN_FLIGHT-Marker ab. Dies liegt zwischen zwei getrennten Commits und benötigt keine ungültigen Eingabedaten.
3. Der neue Prozess sieht die unbeanspruchte RUNNING-Saga mit ausschließlich PENDING-Schritten. Nach dem Write-Ahead-Protokoll kann hier noch keine Fachwirkung entstanden sein.
4. `wiederanlauf()` setzt sie dennoch auf FAILED und zählt `failed=1`.
5. FAILED gehört weiterhin zur Menge `unfinished()`. Jeder weitere Wiederanlauf setzt erneut FAILED und liefert wieder einen Fehler.
6. `requireSuccessfulRecovery()` verweigert bei diesem Fehler jeden Start des HTTP-Servers. Die administrative Recovery ist dadurch ebenfalls nicht erreichbar.

Die isolierte Probe führt zweimal Recovery über dieselbe PENDING-Saga aus. Beide Male entsteht `RecoveryReport[completed=0, compensated=0, failed=1, errors=0]`; der Kopf bleibt FAILED. Es gibt keinen automatisch erreichbaren endgültigen Abbruchzustand. Der Startup-Guard selbst ist für wirklich unvollständige Kompensationen richtig; die falsche Klassifikation einer beweisbar wirkungslosen Saga verursacht hier den Fehler.

**Auswirkung:** Ein Absturz in einem regulären Journalfenster verhindert den automatischen Wiederanlauf der gesamten Fachanwendung, bis jemand das Journal manuell behandelt. Deshalb MAJOR; kein belegter Geldverlust und keine Einstufung als BLOCKER.

**Korrekturrichtung:** Eine ausschließlich PENDING gebliebene, vollständig bekannte Saga unter exklusivem Claim in einen terminalen Abbruchzustand überführen, beispielsweise COMPENSATED, einschließlich der vorgesehenen Payload-Löschung. Beschädigte oder unbekannte Schrittzustände müssen davon unterschieden bleiben. Regression: Journal.start-Commit → Prozessabbruch vor erstem IN_FLIGHT → zweimaliger Wiederanlauf → erfolgreicher Startup-Guard.

### K-02 – MAJOR: SQLState 61000 erweitert Retry auf Nicht-Sperrfehler

**Belegsicherheit:** dynamisch belegte Klassifikation mit dem echten Treiber-JAR und frischer TxManager-Probe auf JDBC-Mocks; kein absichtlich herbeigeführter Ressourcenmangel der echten Datenbank.

**Fundstellen:** `RetryPolicy.java:18–19`, `RetryPolicy.java:45–48`; `TxManager.java:60–66`, `TxManager.java:85–98`; `RetryPolicyTest.java:91–97`.

Die geltende Projektregel erlaubt ausschließlich ORA-00054, ORA-00060 und ORA-08177 als Wiederholungsgrund. Der Code akzeptiert zusätzlich jeden SQLState `61000`. Dieser State bezeichnet eine breitere Gruppe technischer Ressourcenfehler. Der aktuelle Oracle-Treiber liefert bei folgenden Vendor-Codes tatsächlich `61000`:

| Vendor-Code | Bedeutung bzw. Einordnung | Projektklassifikation |
|---|---|---|
| 54 | Ressource gesperrt | retry=true, vorgesehen |
| 60 | Deadlock | retry=true, vorgesehen |
| 18 | maximale Sitzungszahl erreicht | retry=true, außerhalb der Freigabeliste |
| 20 | maximale Prozesszahl erreicht | retry=true, außerhalb der Freigabeliste |
| 4031 | Shared-Memory-/Shared-Pool-Mangel | retry=true, außerhalb der Freigabeliste |
| 4020 | Deadlock bei Library-Objekt-Sperre | retry=true, außerhalb der expliziten Freigabeliste |

Die Probe ruft `oracle.jdbc.driver.DatabaseError.ErrorToSQLState(code)` aus dem tatsächlichen JAR auf. Ein anschließend mit diesem State erzeugter ORA-04031 durchläuft `TxManager.execute()` **dreimal**, einschließlich zweier RETRY-Auditereignisse. ORA-08177 funktioniert wegen der Vendor-Code-Prüfung trotz seines tatsächlichen SQLState `72000` weiterhin korrekt.

**Guards-Gegenprüfung:** maxAttempts begrenzt die Wiederholung; jeder Fehlversuch führt vor dem Retry zum Rollback. Fachliche `FachlicheException` bleibt nicht wiederholbar. Es wurde keine Doppelbuchung durch diesen Befund bewiesen. Die konkrete Wirkung sind regelwidrige Wiederholungen, zusätzliche Last bei bereits knappem Datenbankressourcenbestand und verzögerte Fehlerantworten. Der bestehende Test mit Vendor-Code 0 und State 61000 schreibt den zu weiten Fallback sogar fest.

**Korrekturrichtung:** Auf die freigegebenen Oracle-Codes beschränken. Einen SQLState-Fallback nur nach expliziter Änderung des Architekturvertrags und mit enger, belastbarer Semantik zulassen. Negative Regressionen für ORA-00018, ORA-00020 und ORA-04031 ergänzen.

Amtliche Gegenprüfung: [Oracle SQLSTATE-Zuordnungen](https://docs.oracle.com/en/database/oracle/oracle-database/21/lnpcb/error-handling-and-diagnostics.html), [Oracle ORA-04031](https://docs.oracle.com/en/error-help/db/ora-04031/). Die versionsgenaue JDBC-Zuordnung kommt aus der ausgeführten Probe mit dem Projekt-JAR.

## Bestätigtes API-Verhalten und Betriebsgrenzen, getrennt von Anwendungsfehlern

### K-I01 – Wiederholtes finish kann eine fremde aktuelle Ausleihe freigeben

**Fundstellen:** `TxContext.java:49–51`, `TxManager.java:138–147`, `SimpleConnectionPool.java:178–209`.

Die manuelle API dokumentiert begin → commit/rollback → finish; sie verspricht nicht ausdrücklich ein mehrfach erlaubtes finish. Bei Fehlbedienung ist jedoch die behauptete Idempotenz des SimpleConnectionPool begrenzt: `detachConnection()` entfernt die Referenz nicht, und `borrowed` erkennt nur die physische Connection, keine Ausleihgeneration.

Die frische Probe zeigt: Context A beenden und freigeben → Context B leiht dieselbe physische Connection aus → ein verspätetes zweites `finish(A)` → B wird zurückgerollt, B erhält AutoCommit=true, und ein dritter Borrow liefert gleichzeitig genau dieselbe Connection. Der SimplePool ignoriert einen unmittelbar doppelten Release korrekt; die dazwischen erfolgte erneute Ausleihe umgeht diesen Guard.

**Einordnung:** bestätigter Robustheitsmangel der öffentlichen manuellen API unter fehlerhafter Doppel-Freigabe; als MAJOR-Kandidat relevant, falls eine gegen doppelte Freigabe robuste Bibliotheks-API beansprucht wird. Im aktuellen Hauptanwendungscode wurde kein manueller begin/finish-Aufrufer gefunden. Deshalb hier kein bestätigter erreichbarer Web- oder Fachverlust und keine Hochstufung anhand hypothetischer Nutzung. Eine atomare, einmalige Entkopplung im Context oder ausleihgebundene Connection-Proxies würden das abfangen.

Die JDBC-Wirkung von rollback und AutoCommit-Wechsel ist durch [Java 21 Connection](https://docs.oracle.com/en/java/javase/21/docs/api/java.sql/java/sql/Connection.html) festgelegt. Die Probe zeigt die Methodenaufrufe, keine echten SQL-Zeilen.

### K-I02 – Future-Abbruch bedeutet keinen garantierten Transaktionsrollback

**Fundstellen:** `TaskHandle.java:47–54`, `TaskController.java:19–21`, `TaskController.java:111–123`; `TxManager.java:45–47`; `TxContext.java:64–69`.

Die Probe startet eine Arbeit, ruft früh `TaskHandle.cancel()` auf, lässt die Arbeit eine InterruptedException abfangen und vor ihrer Deadline zurückkehren. `cancel=true`, der Future bleibt abgebrochen, die Taskstatistik zählt failed; dennoch ruft TxManager anschließend commit auf. Es gibt vor dem Commit kein dauerhaftes Abbruchsignal außer der verstrichenen Deadline.

Dieses Verhalten entspricht grundsätzlich [Java 21 Future.cancel](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/Future.html): Interrupt ist ein Abbruchversuch, kein garantierter Stopp. Der lokale Javadoc-Satz, die unterbrochene Transaktion werde zurückgerollt, ist deshalb zu stark. Stats beschreiben Future-/Taskzustände, keine verifizierten DB-Endzustände. Der schon dokumentierte Commit-Wettlauf bleibt zusätzlich bestehen. Im aktuellen Hauptcode wurde kein früher explicit-cancel-/resultOrCancelAfter-Aufrufer gefunden; deshalb Betriebs-/Vertragsgrenze, kein zusätzlicher bestätigter Anwendungsfund.

Oracle empfiehlt für JDBC [Statement-Timeout bzw. Statement.cancel und keine Thread.interrupt-Verwendung](https://docs.oracle.com/en/database/oracle/oracle-database/21/jjdbc/JDBC-coding-tips.html). Die aufgerufene 23er-Dokumentationsadresse wurde beim Zugriff auf die 26er-Fassung umgeleitet; für den versionsgebundenen Nachweis wurde deshalb zusätzlich die stabile 21er-Fassung gelesen. Das beweist keinen konkreten Ausfall des eingesetzten 23.5-Treibers; dafür wäre eine gezielte echte Blockier-/Abbruchprobe nötig.

### Weitere belegte Grenzen

- `SimpleConnectionPool.close()` schließt nur derzeit idle Verbindungen und besitzt kein Closed-Flag (`SimpleConnectionPool.java:213–219`). Späte Releases können ihn wieder füllen. Bereits dokumentierte Demo-/Testgrenze; kein neuer Produktfund ohne nachgewiesenen Nutzerpfad nach close.
- Audit rotiert auf einen Vorgänger, besitzt aber keine Signatur, unveränderliche Ablage oder hier nachgewiesene Archivierung. Die Bezeichnung „revisionssicher“ ist durch diese Implementierung allein nicht belegt. Schreibfehler werden gemeldet, verhindern aber keinen Commit. Eine betriebliche Aufbewahrungs-/Integritätslösung bleibt Voraussetzung; pauschale Rechtswidrigkeit wurde nicht abgeleitet.
- Der Error-Zweig `TxManager.java:79` übergibt weiterhin `e.toString()` statt `fehlerBeschreibung(e)` ins Audit. Ein sensitiver Error-Text würde daher unredigiert protokolliert. Kein konkreter aktueller Fachpfad mit einem solchen Error nachgewiesen; offene Datenschutzspur statt bestätigt behauptetem Sozialdatenabfluss.
- Saga-Definitionen sind nach Typ und Schrittindex gebunden, nicht durch Versionshash. Reordering gleich vieler Schritte könnte bei einem künftigen inkompatiblen Upgrade die Kompensation verfälschen. Kein solches aktuelles Upgrade nachgewiesen; Betriebsregel/Upgradegrenze.

## Abdeckungsmatrix

| Prüffeld | Status | Tatsächliche Prüfung und Grenze |
|---|---|---|
| TxManager execute, manuelle API, Commit-/Rollback-/Releasegrenzen | TEILWEISE GEPRÜFT | Sämtliche Pfade statisch verfolgt; manuelle Mehrfachfreigabe dynamisch mit Mock; echte verlorene Commit-Bestätigung in diesem Teilreview nicht neu erzeugt. |
| Retry, Ketten/Zyklen, Backoff, Interrupt | GEPRÜFT für Klassifikation und Implementierung | Code samt aktuellen Tests geprüft; echter Treiber-SQLState und TxManager-Replay frisch geprüft. Keine neue DB-Sperrprobe. |
| Saga Write-Ahead, fachliches DONE, Claims, Recovery | TEILWEISE GEPRÜFT | Alle Engine-/Journalpfade verfolgt; PENDING-Wiederanlauf frisch reproduziert. Bestehende DB-/Prozesskilltests gelesen; deren aktuelle Ausführung gehört zum zentralen Lauf. |
| Kompensationsreihenfolge und Fachguards | TEILWEISE GEPRÜFT | Engine setzt bei fachlichem Kompensationsfehler ältere Schritte fort; für allgemeine Sagas nicht automatisch falsch. Fachliche Wirkung an die zentrale/fachliche Gegenprüfung übergeben. |
| TaskController/TaskHandle, Queue, Statistik, Shutdown | TEILWEISE GEPRÜFT | Submit, Timer, settle-Gate, Queue-Cancel und Shutdown verfolgt; frühes Cancel dynamisch. Keine frische echte hängende JDBC-Abbruchprobe. |
| SimpleConnectionPool | TEILWEISE GEPRÜFT | Borrow-/Release-/Fehlerpfade und Tests geprüft; ABA mit Produktionspool und isoliertem JDBC-Treiber reproduziert. Kein neuer Last-/DB-Neustartlauf. |
| UCP | TEILWEISE GEPRÜFT | Konfiguration, Invalidation, Release, JMX und Close statisch; aktuelle Unit-Tests gelesen. Kein eigener realer UCP-Last-/Abandonedlauf. |
| Audit/JMX | TEILWEISE GEPRÜFT | Redaktion, Rotation, Writer, Listener-/Loggergrenzen, MBean-Instanznamen und Fehlerwerte geprüft. Keine neue Dateisystemfehler-/Integritäts- oder Mehrprozessprobe. |
| Oracle-DDL/Constraints/Indizes/Trigger | TEILWEISE GEPRÜFT | Frische DDL statisch auf Schlüssel, Status-/Formatguards, aktive Monats-Eindeutigkeit und Versions-Trigger geprüft. Kein frischer vollständiger Neuinstallations-/Upgradeversuch. |
| Oracle 19c vs. Free, tatsächliches JDK 21 | NICHT GEPRÜFT in diesem Teilreview | Versionsbehauptungen werden nicht aus früheren Läufen übernommen; zentrale Umgebungs-/Testbelege maßgeblich. |

## Historische Befunde: aktueller Gegencheck

| Historischer Befund | Aktuelles Urteil |
|---|---|
| CR-001 Saga-Payload im Audit | Ursprünglicher Payload-Leak beseitigt: Start protokolliert Typ/Länge, Engine-Exceptionpfade verwenden fehlerBeschreibung. Error-Sonderpfad bleibt separate offene Spur. |
| CR-012/CR-021 Auditrotation/Writer | Rotation und dauerhaft offener Writer vorhanden; synchrone I/O und externe Integrität/Aufbewahrung bleiben. Keine Übernahme der alten unbegrenzten Datei-Behauptung. |
| CR-017 geordneter Shutdown | Hook vorhanden, Server → Tasks → Pool → Audit. Geordnete Ressourcenfreigabe unter hängenden JDBC-Calls bleibt zeitlich/treiberabhängig. |
| CR-018 close ohne finish | Als manuelle API-Voraussetzung explizit dokumentiert; SimplePool-Leak bleibt bei falscher Nutzung, UCP hat Abandoned-Timeout. Kein neuer Befund allein wegen unverändertem Vertrag. |
| CR-019 Kern-Testlücken | TxManager-, UCP-, ParallelRecovery-, Uhr-/Ownership- und Prozesskilltests sind vorhanden. Alte Behauptung „keine Tests“ ist widerlegt. PENDING-Startup und generationenübergreifende Doppel-Freigabe sind dennoch nicht abgedeckt. |
| CR-020 JDBC ohne Zeitgrenzen | Connect-/Read-Timeouts in beiden Pools vorhanden. Tatsächliche Wirkung nicht mit bloßem Vorhandensein gleichgesetzt. |
| CR-022 UCP-Wartezeit/FCF | Wartezeit 30 s explizit; Kommentar verspricht kein konfiguriertes FCF/RAC mehr. |
| CR-044 Monats-CHECKs | In aktueller Frisch-DDL vorhanden. Bestehendes Schema benötigt gesonderten Nachweis. |
| CR-045 angeblich fehlender Saga-FK-Index | Aktuelle DDL enthält separaten Index. Schon der PK (SAGA_ID, IDX) beginnt aber mit SAGA_ID; deshalb folgt „ohne separaten Index zwingend Full Scan“ nicht. Keine Wiederholung dieser historischen Überbehauptung. [Oracle Composite Indexes](https://docs.oracle.com/en/database/oracle/oracle-database/19/cncpt/indexes-and-index-organized-tables.html) |
| CR-046 Poolwarnung | Rate-Limit-Warnung vorhanden. |
| CR-047 Timeoutsemantik | Pro-Versuch- und Gesamtbudget explizit dokumentiert und mit Tests ergänzt. |
| CR-048/CR-049 | clearForTests paketprivat; JMX-Registrierungsfehler werden beim Start getrennt angezeigt. |
| CR-050 Recovery nach Listenerstart | Reihenfolge korrigiert, Fehler verhindern Listenerstart. Gerade dieser inzwischen richtige Guard macht K-01 betriebswirksam. |
| Ältere Claim-/Recovery-Race-Vermutung | Aktueller JournalClaim hält Kopfzeilensperre über den kritischen Lauf; vor/nach Fachaktion wird checkAlive ausgeführt. `FOR UPDATE SKIP LOCKED`-Semantik durch [Oracle 19c SELECT](https://docs.oracle.com/en/database/oracle/oracle-database/19/sqlrf/SELECT.html) bestätigt. Keine bloße Altersfrist mehr als alleiniger Schutz. |

## Reproduktions- und Integritätsbelege

Aus Repositorywurzel: `& doc/codereview/grundsicherung-gesamt-review-2026-10-15-belege/kern/run-probe.ps1`.

- `KernProbe.java`: neue isolierte Probe; keine echte DB.
- `run-probe.ps1`: reproduzierbarer separater javac/java-Lauf ohne Maven und ohne Quelländerungen.
- `probe-output.txt`: frischer erfolgreicher Lauf vom 2026-10-15, 10:06:10 Europe/Berlin, einschließlich drei ORA-04031-Versuchen, zweimal FAILED, ABA und Cancel/Commit.
- `source-hashes.json`: 56 SHA-256-Einträge für die geprüften Kernquellen/-tests, DDL, Build- und Startschnitttstellen. Gegen das zentrale `inventory-before.json` verglichen: **keine Abweichung**. Die abschließende globale Driftprüfung bleibt beim koordinierenden Review.

Die lokale erste Fassung des Reproduktionsskripts besaß einen falschen relativen Quellpfad; javac brach dadurch ohne Lauf ab. Dieser Prüfskriptfehler wurde ausschließlich im neuen Belegordner korrigiert. Der oben genannte abschließende Lauf ist erfolgreich; der Fehlschlag wird nicht als Produktfehler gezählt.
