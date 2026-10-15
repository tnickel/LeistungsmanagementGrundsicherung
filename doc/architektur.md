# Architektur der Transaktionssteuerung

*Stand: 2026-10-13, Version 0.5.8 einschließlich Review-Nachbesserungen*

## Schichtenbild

```
Fachschicht: Leistungsmanagement Grundsicherung (SDN.Leistung LMG)
   Masken (SozialFall, Bedarfsermittlung, Zahlbarmachung) + JSON-API
        │  ordneAuszahlungAn / Masken-Speicherung / Abfragen
        ▼
┌──────────────────────────────────────────────────────┐
│  Transaktionssteuerung (dieses Projekt)              │
│                                                      │
│  TaskController ── begrenzt parallele Tasks,         │
│       │             Wachhund bricht zu langsame ab   │
│       ▼                                              │
│  TxManager ────── Begin/Commit/Rollback,             │
│       │             RetryPolicy (ORA-54/60/8177)     │
│       ▼                                              │
│  SagaEngine ───── lange Vorgänge in Schritten,       │
│       │             Journal TX_SAGA / TX_SAGA_STEP   │
│       ▼                                              │
│  ConnectionPool ── SimpleConnectionPool (Demo) oder  │
│                    Oracle UCP (Produktions-Default)  │
└──────────────────────────────────────────────────────┘
        │                                   ▲
        ▼                                   │
   Oracle DBMS  ── ACID, Sperrverwaltung,   │ TxMonitoring (JMX),
                   Redo-Logging, Recovery   │ TxAuditLog (revisionssicheres
   (kommt aus der Datenbank!)               │ Audit-Log, Ringpuffer + Datei)
```

## Bausteine

| Klasse | Verantwortung |
|---|---|
| `tx.TxManager` | Kern: führt `TxWork` in einer Oracle-Transaktion aus. Commit bei Erfolg, Rollback bei jedem Fehler **inkl. Error (OOM/StackOverflow — wird durchgereicht, nie implizit committet)**, Retry nur bei wiederholbaren DB-Konflikten. Manuelle API: `begin`/`commit`/`rollback`/`finish`. |
| `tx.TxContext` | Repräsentiert eine laufende Transaktion: Verbindung, Zeitlimit (Deadline), Status, Savepoints. `checkDeadline()` rufen Fachschritte zwischen Teilarbeiten auf. |
| `tx.TxOptions` | Wertobjekt: Isolationsebene (Standard READ COMMITTED), Timeout (Standard 30 s), max. Versuche (Standard 3). |
| `tx.RetryPolicy` | Klassifiziert `SQLException`: ORA-00054/00060/08177 bzw. SQLState 40001/61000 sind wiederholbar. Exponentieller Backoff mit Zufallsanteil. |
| `tx.TaskController` | Task-Steuerung: fester Thread-Pool + begrenzte Warteschlange, Timeout-Wachhund (misst ab Ausführungsbeginn; Queue-Warte zählt nicht), geordneter Shutdown (`shutdown` + Drain + `shutdownNow`), Interrupt-Flag-Räumung, Statistik (`TaskStats`). Abgerechnet wird jeder Task über ein zentrales `settle()`-Gate (AtomicBoolean) — kein Leak in der Running-Map, keine Doppelzählung. |
| `tx.TaskHandle` | Ergebnis-Handle pro Task: `result()`, `resultOrCancelAfter()`, `cancel()`. Abbruch wird als `TxException` typisiert gemeldet. |
| `tx.ConnectionPool` | Schnittstelle: `getConnection`/`release` — alle Bausteine hängen nur daran. |
| `tx.SimpleConnectionPool` | Minimaler Pool für Tests/Demo: Validierung beim Entleihen (`isValid`), idempotentes Release, Borrow-Timeout (Standard 30 s); rollt beim Release grundsätzlich zurück, bevor `autoCommit=true` gesetzt wird. |
| `tx.UcpConnectionPool` | Produktionspool (Oracle UCP, `validateConnectionOnBorrow`), Default über `lmg.pool.type=ucp`. |
| `saga.SagaEngine` | Führt `SagaDefinition` als Kette kurzer Transaktionen aus; hält auch im normalen Lauf einen exklusiven Kopf-Claim über die Fachschritte; kompensiert bei Fehlern rückwärts. `recover()` übernimmt halbfertige Vorgänge **aller registrierten Saga-Typen** mit `FOR UPDATE SKIP LOCKED`; bereits beanspruchte laufende Sagas werden übersprungen. |
| `saga.SagaJournal` | DAO für die Journal-Tabellen `TX_SAGA`/`TX_SAGA_STEP`; Journal-Schreibakte mit Zustands-Guards und geprüften Update-Zählern. |
| `saga.SagaDefinition/Step` | Deklarative Vorgangsbeschreibung: benannte Schritte mit Aktion + Kompensation. |
| `tx.audit.TxAuditLog` | Revisionssicheres Audit (Commit/Rollback/Retry/Timeout/Saga-Ereignisse), Ringpuffer, Listener, optionale Datei. |
| `tx.monitoring.TxMonitoring` | Zentrale JMX-Registrierung (TaskController-, UCP- und generische Pool-MBeans). |
| `demo.DemoMain` | Lauffähiges Beispiel (Antrag → Bescheid → Auszahlung) inkl. Fehlerfall und parallelen Tasks. |

### Fachschicht Leistungsmanagement (`de.allgemein.lmg`, Details: [leistungsmanagement.md](leistungsmanagement.md))

| Baustein | Verantwortung |
|---|---|
| `lmg.calc.Leistungsberechnung` | Reine SGB-XII-Berechnung (Regelbedarfsstufen 2026, Mehrbedarfe § 30 Abs. 1/2/3/7 mit Obergrenze Abs. 6, KdU § 42a mit Angemessenheitsgrenze einschließlich zentraler Warmwasserkosten, Absetzbeträge § 82 Abs. 2, Erwerbsfreibetrag § 82 Abs. 3, Vermögensanrechnung § 43 Abs. 1/§ 90 Abs. 2 Nr. 9), je SozialFall und summiert je Bedarfsgemeinschaft. |
| `lmg.service.SozialLeistungsService` | Fachdienste als kurze Transaktionen: Zahlungsweg (Idempotenz → FOR-UPDATE-Sperre → Budget-Prüfung → Zahlung immer `ANGELEGT` → Restbudget mindern → Journal; Vier-Augen serverseitig erzwungen), Freigabe/Kassenlauf/Storno (guarded Statuswechsel), Masken-Speicherung mit optimistischem Locking. |
| `lmg.service.BescheidPaymentBinding` | Prüft unter BG-Sperre eindeutige Monatsdeckung und exakte Betragsübereinstimmung mit dem gespeicherten Bewilligungsplan. Weist neue Überschneidungen aktiver Bewilligungen ab; ersetzt keine fachliche Entscheidung über Änderungsbescheide. |
| `lmg.service.BescheidGenerator` | Amtlicher, druckfertiger Bewilligungsbescheid (§ 44 SGB XII): Verfügungssatz, Bedarfsaufschlüsselung mit KdU-Vorbehalt, fallbezogene Begründung (§ 35 SGB X), konfigurierbare Rechtsbehelfsbelehrung (§ 36 SGB X). |
| `lmg.service.SepaValidator` | IBAN/BIC-Validierung nach ISO 13616 (Modulo-97). |
| `lmg.saga.AntragsSagaProzess` | 4-stufige Fach-Saga Antrag → Bedarfsprüfung → Bescheid → Erstauszahlung mit idempotenter Rückwärtskompensation (Budget-Rückbuchung, Historisierung statt Löschung). |
| `lmg.dao.*` | DAOs auf der Verbindung des `TxContext`, ausschließlich PreparedStatements; keine eigenen Connections. |
| `lmg.config.LmgConfig` | Externalisierte Konfiguration (System-Properties > Umgebungsvariablen > `lmg.properties` > Defaults), inkl. KdU-, Rechtsbehelf- und Session-Parameter. |
| `lmg.web.LmgServer/LmgServerMain` | JSON-API + Oberfläche auf dem JDK-HttpServer; jede Anfrage (auch der Bescheid-Druck) läuft als Task über den `TaskController`; bindet per Default **Loopback** (`lmg.host` konfigurierbar; Demo-Standardkonten werden bei Nicht-Loopback verweigert); CSP-/no-store-Header, Body-Limit, Token nur aus dem Authorization-Header (Druck-Link: Einmal-Kurzzeit-Token über `POST /api/drucktoken`). |
| `lmg.web.HttpServerLimits` | Setzt und validiert begrenzte JVM-weite JDK-HttpServer-Properties vor dem ersten `HttpServer.create`; spätere Änderungen erfordern einen JVM-Neustart. |
| `lmg.web.Benutzerverwaltung` | Anmeldung mit Rollen; Sitzungs-Token (SecureRandom/UUID) mit Idle-Timeout, Anmelde-Drosselung, constant-time-Passwortvergleich. |
| `lmg.web.Json` | Schlanker JSON-Writer/Parser ohne externe Bibliothek (Tiefenlimit, vollständige Escape-Prüfung). |
| `webapp/` (Ressourcen) | Single-Page-Oberfläche (SDN.Leistung-Stil): Widget-Dashboard, Vorgangsmaske, Zahlbarmachung, Journal — HTML-Escaping für alle Serverdaten. |

## Ablauf einer kurzen Transaktion (`TxManager.execute`)

1. Verbindung aus dem Pool holen, `autoCommit=false`, Isolation setzen.
2. `TxWork` ausführen (das Fach-SQL).
3. Deadline prüfen — nach Zeitlimit wird nicht mehr committet.
4. `COMMIT` bei Erfolg; bei jeder Exception `ROLLBACK`; bei `Error` (OOM/StackOverflow)
   ebenfalls Rollback, dann wird der Error durchgereicht.
5. Exception-Klassifikation: wiederholbar (ORA-00054/00060/08177) → Backoff und
   neuer Versuch (bis `TxOptions.maxAttempts`), sonst `TxException` sofort.
6. Verbindung in jedem Fall zurück in den Pool — die Pools rollen vor dem
   Zurücksetzen von `autoCommit` grundsätzlich zurück (kein implizites Commit
   möglich, JDBC-Spez-Konsequenz von `setAutoCommit(true)`).

## Ablauf eines Saga-Vorgangs (`SagaEngine.run`)

1. Journal-Kopf anlegen: `TX_SAGA` (Status RUNNING) + je Schritt eine Zeile in
   `TX_SAGA_STEP` (Status PENDING) — eigener Commit, damit der Eintrag einen
   Absturz überlebt. Anschließend wird die Kopfzeile über eine eigene Verbindung
   exklusiv beansprucht; vor dieser Übernahme entsteht noch keine Fachwirkung.
2. Je Schritt: **Write-Ahead** — Schritt im Journal auf `IN_FLIGHT` setzen
   (eigener Commit), dann Aktion + `DONE`-Markierung **in derselben
   Transaktion** committen. Ein Crash dazwischen erkennt der Wiederanlauf am
   `IN_FLIGHT`-Zustand und behandelt den Schritt konservativ als
   möglicherweise ausgeführt (Kompensation ist idempotent). Die Kopf-Sperre bleibt
   währenddessen erhalten; `checkAlive()` prüft ihre Verbindung vor und nach jeder
   Fachaktion. Kopfstatuswechsel laufen über den Claim, Schrittmarker über eigene
   kurze Transaktionen.
3. Fehler in einem Schritt: Saga auf FAILED, dann **rückwärts kompensieren** —
   jede Kompensation in eigener Transaktion, Journal auf COMPENSATED; schlägt
   eine Kompensation fehl, endet die Saga FAILED und der nächste Wiederanlauf
   setzt tiefer an (kein stiller Reihenfolge-Bruch). FAILED wird vor der erneuten
   exklusiven Übernahme zur Kompensation dauerhaft festgeschrieben. Hat eine andere
   Recovery die Saga inzwischen übernommen, startet keine zweite parallele
   Kompensation. Ein vorhandenes Interrupt-Flag wird für die notwendige Bereinigung
   kontrolliert aufgehoben und anschließend wiederhergestellt.
4. Alles geschafft: Saga auf COMPLETED (ein Fehler hierbei löst **keine**
   Kompensation aus — der Wiederanlauf schließt später ab).
5. **Wiederanlauf** (`recover`): beim Neustart alle Sagas **aller registrierten
   Definitionen** in RUNNING/FAILED/COMPENSATING lesen, je Saga mit
   `FOR UPDATE SKIP LOCKED` claimen (parallel-sicher), DONE- und
   IN_FLIGHT-Schritte kompensieren, COMPENSATED-Schritte überspringen —
   deshalb müssen Kompensationen idempotent sein. Der Start-Wiederanlauf verwendet
   keine Altersfrist; der administrative Wiederanlauf berücksichtigt fünf Minuten
   Inaktivität. Die exklusive Kopf-Sperre schützt aktive Sagas auch beim Start und
   bei mehreren Prozessen unabhängig von der Altersfrist.

Ein über HTTP ausgeführter Saga-Task kann gleichzeitig drei Poolverbindungen
belegen: die äußere Task-Transaktion, den Kopf-Claim und die Fach-/Journaltransaktion.
Der Standard beträgt deshalb 24 Verbindungen bei acht Tasks. Der Start warnt bei
`poolSize < 3 × maxTasks`; zusätzliche unabhängige DB-Arbeit benötigt weitere Reserve.

## Datenmodell (`ddl/oracle_schema.sql`, `ddl/lmg_schema.sql`)

| Tabelle | Zweck |
|---|---|
| `TX_SAGA` | Kopf je Vorgang: SAGA_ID (UUID), Typ, Status (mit CHECK), Payload, Zeitstempel |
| `TX_SAGA_STEP` | Je Schritt: Index, Name, Status (PENDING/IN_FLIGHT/DONE/COMPENSATED, mit CHECK) |
| `TX_DEMO_ANTRAG`, `TX_DEMO_ZAHLUNG` | Demo-Tabellen für das Beispiel |
| `BEDARFSGEMEINSCHAFT` | Vorgang: Aktenzeichen (unique), Restbudget, Vermögen (§ 90), Bankverbindung, VERSION |
| `SOZIAL_FALL` | Person: RBS, Mehrbedarfsmerkmale (G/Schwanger/Kinder/Warmwasser), KdU-Dreiteilung, Einkommen, Absetzbeträge (§ 82 Abs. 2), VERSION |
| `ZAHLUNG` | Auszahlung: VORGANGS_NR unique (Idempotenz), Statusautomat ANGELEGT/BEREIT/UEBERGEBEN/STORNIERT |
| `BESCHEID` | Bewilligungsbescheid (§ 44 SGB XII): Nr (unique), Bescheiddatum, Zeitraum YYYY-MM |
| `JOURNAL` | Revisionssicheres Journal (bewusst ohne FK — überlebt Historisierung) |

Status-Automatik Saga: `RUNNING → COMPLETED`, `RUNNING → FAILED → COMPENSATING →
COMPENSATED` (bzw. zurück zu FAILED, wenn eine Kompensation fehlschlägt).

## Architekturregeln

1. **Kurze Transaktionen, lange Sagas.** Atomar Requiredes gehört in eine
   `TxManager.execute`-Transaktion; Minuten-dauernde bzw. interaktive Abläufe
   werden zu Saga-Schritten zerlegt.
2. **Wiederholbar heißt ganz wiederholen** — nur Sperren-Konflikte; fachliche
   Fehler (z. B. Prüfbedingung) brechen ohne Retry ab.
3. **Kompensationen idempotent** (zustandsabhängige SQL-Updates mit Guard und
   geprüfter Update-Zählung).
4. **Kein XA im Kern.** Mehrere Datenbanken in einer logischen Einheit erst bei
   echtem Bedarf (dann Oracle-XA/Two-Phase-Commit).
5. **Pool-Abstraktion halten:** alles hängt nur an `getConnection`/`release`.
6. **Statuswechsel nur guarded:** kritische Updates prüfen den Quell-Zustand
   (`WHERE … AND STATUS = …`) und werten die Update-Zählung aus — Races mit
   parallelen Kassenläufen/Freigaben werden erkannt statt blind überschrieben.

## Bekannte Grenzen (Stand v0.5.8)

- Watchdog-Abbruch im Commit-Fenster: Das klassische TP-Monitor-Restfenster
  (Commit durch, Abbruch dem Aufrufer gemeldet) ist nicht vollständig
  eliminierbar; dokumentiert im `TaskController`-Javadoc, Auflösung nur über
  TX-IDs + Recovery-Abfrage beim DB-Server.
- `SimpleConnectionPool` bewusst minimal (Demo/Tests); Produktion läuft über
  Oracle UCP (`validateConnectionOnBorrow`, seit v0.5.5 mit explizitem
  Connection-Wait-Timeout und JDBC-Connect-/Read-Timeouts).
- Passwörter als Klartext-Konfiguration, Sitzungen im Speicher — Produktivweg
  ist LDAP/AD hinter HTTPS (siehe Doku Leistungsmanagement).
- Audit-Log rotiert bei 10 MB auf einen Vorgänger (`.1`); eine mehrstufige
  Archivierungsstrategie für historisierte Vorgänge und alte Journal-Einträge
  steht noch aus. Audit-Zeilen enthalten bewusst keine Sozialdaten (Payload-
  Redaktion, v0.5.5).
- Anmelde-Drosselung und Session-Timeout sind einfach (in-memory) — für
  horizontale Skalierung wäre ein verteilter Sitzungsspeicher nötig.
- Getestete Oracle-Mindestversion: 19c (FOR UPDATE SKIP LOCKED des
  Wiederanlaufs ist seit 11g verfügbar; verifiziert gegen 19c EE und 23ai Free).
- Kein Need-to-know-Filter auf den Listen-Endpunkten: jeder angemeldete
  Sachbearbeiter sieht alle Vorgänge (Einmandanten-Innenanwendung). Seit v0.5.7
  gilt Option D: Übersichten ohne Bankverbindungsdaten, jede Detail-Einsicht mit
  Kennung im Journal protokolliert (60-s-Zusammenfassung); ein Zuständigkeits-/
  Teammodell (Option B/C) bleibt Ausbaustufe.
  Seit v0.5.8 erfolgt die Zusammenfassung ausschließlich über persistierte
  Journalzeilen unter BG-Sperre; Rollbacks/Retry und mehrere Serviceinstanzen
  können keinen falschen Dedup-Zustand mehr erzeugen. Auszahlungs-Replays prüfen
  BG/Monat und explizite Bankdaten und protokollieren die erneute Dateneinsicht.

## Nachbesserung nach Gegenprüfung v0.5.8 (historischer Zwischenstand 2026-10-12)

Die folgenden Punkte beschreiben den damaligen Zwischenstand. Die weiter unten
aufgeführten Nachbesserungen vom 2026-10-13 ersetzen insbesondere die damalige
Pooldimensionierung, die alleinige Altersfrist als Schutz laufender Sagas, den
HTTP-Executor-Shutdown und die genannten Grenzen der Simulator-Endpunkte.

- **Web:** Pfad-Normalisierung (abschließende `/`) vor Rollenprüfung; Request-Body
  wird einmal gelesen und bei ORA-08177-Retry wiederverwendet; Überlast
  (abgelehnte Task-Einreichung) liefert 503 `UEBERLAST` mit `Retry-After`;
  Standard-Poolgröße 16 (Warnung beim Start, wenn `poolSize < 2 × maxTasks`);
  JSON-Parser: Tiefenlimit korrekt, `\u`-Escapes strikt hexadezimal;
  Anmelde-Drosselung: Schlüssel auf 64 Zeichen begrenzt, aktive Sperren werden
  nie verdrängt, abgelaufene Sitzungen werden periodisch aufgeräumt.
- **Audit-Log:** Fehlerzeilen enthalten nur Klassenname und ORA-Code, nie die
  Meldung; Anführungszeichen, Backslashes und Steuerzeichen werden maskiert.
- **Saga:** mehrdeutiger Commit-Ausgang (Exception nach Commit) wird über
  `isStepDone` aufgelöst und der fragliche Schritt konservativ kompensiert;
  Payload wird bei COMPLETED/COMPENSATED auf NULL gesetzt; Wiederanlauf fasst
  nur Sagas an, die ≥ 5 Minuten inaktiv sind (laufende Sagas bleiben unberührt);
  Antrags-Saga kompensiert nur selbst angelegte Vorgänge (Journal-Marker
  `Antrags-Saga <id>: Vorgang <az> erfasst`) und prüft IBAN/BIC.
- **Fachlogik:** Zahlung nur in vom Bescheid gedeckten Monaten (sobald die BG
  mindestens einen Bescheid hat); stornierte Zahlungen werden nicht
  wiederholt gebucht.

Zusätzliche bekannte Grenzen:

- Auszahlungen für BG **ohne** jeden Bescheid bleiben zulässig (Demo/Simulator);
  produktiv wäre Bescheidpflicht zu erzwingen.
- Vor dem Upgrade begonnene Antrags-Sagas besitzen keinen Journal-Marker; ihre
  BG-bezogenen Schritte werden bei Recovery nicht kompensiert (manuelle Prüfung).
- Der Druck-Token ist nicht an die Sitzung gebunden (60 s, einmalig).
- `close()` des Servers beendet den Task-Executor nicht; `SimpleConnectionPool`
  hat kein Closed-Flag (nur Demo/Tests).
- Einzelne Fachvereinfachungen (Alleinerziehenden-Merkmal, Vermögen ohne
  Zahlung, IBAN-/Vermögensänderung an historisierter BG) sind nicht modelliert.
- testsweet: Simulator-Hilfsendpunkte sind nur für lokale Nutzung gedacht
  (kein Origin-/Host-Check).

## Monatsbezogene Bescheide seit v0.5.8

`Bewilligungsplan` berechnet jeden Bewilligungsmonat mit seinem Satzstand und
bekannten Schwangerschaftsende. Der Vermögenseinsatz wird für Folgezeiträume
simuliert; die Bescheiderteilung verändert den Bestand nicht. Snapshot-Version 2
speichert die Monatsberechnungen in `BESCHEID.BERECHNUNG` (keine DDL-Migration).
`BESCHEID.ZAHLBETRAG` bleibt der Anfangsmonatsbetrag. API, Maske und Druck zeigen
die Einzelbeträge; Version-1-Snapshots bleiben lesbar und werden nicht verändert.
Nachträgliche Fachänderungen benötigen weiterhin eine gesonderte verfahrensrechtliche
Abstimmung (siehe `rechtsgrundlagen.md`, Abschnitt 4 Nr. 7).

## Review-Nachbesserungen vom 2026-10-13

### Bewilligungsbindung und sichere Archivbehandlung

Sobald zu einer BG irgendein Bescheid existiert, muss genau ein `ERTEILT`-Bescheid
den Zahlungsmonat abdecken. `BescheidPaymentBinding` vergleicht die aktuelle Rechnung
mit **dem gespeicherten Betrag dieses Monats**, nicht pauschal mit dem ersten
Monatsbetrag in `BESCHEID.ZAHLBETRAG`. Version 2 verlangt einen vollständigen,
chronologisch passenden Monatsplan derselben BG. Version 1 verwendet den konstanten
Snapshotbetrag; bei snapshotlosen Altbescheiden gilt ausschließlich der gespeicherte
Bewilligungsbetrag. Die Zahlung wird bei Abweichung oder mehrdeutigen Altbescheiden
vor Zahlungseintrag, Budgetminderung und Vermögensverbrauch mit `VALIDATION` abgewiesen.
Ein neuer `ERTEILT`-Bescheid darf keinen aktiven Bewilligungszeitraum überschneiden;
angrenzende Zeiträume bleiben möglich. Es erfolgt keine automatische Aufhebung.

Das ist eine konservative technische Sperre, kein Änderungsbescheidverfahren.
Eine Änderung von Einkommen, Vermögen, Satzständen oder Fällen kann deshalb eine
manuelle fachliche Klärung erfordern. Dasselbe gilt, wenn eine vorgezogene Zahlung
oder ein Storno den Vermögensbestand gegenüber dem Monatsplan verändert. Ein
vollständiger Aufhebungs-/Änderungsworkflow ist weiterhin nicht implementiert.
BG ohne jeden Bescheid behalten den ausdrücklich vorgesehenen Demo-Buchungsweg.

Gültige v1-Snapshots bleiben lesbar. Beschädigte Snapshots werden nicht mit null
ergänzt oder aus heutigen Falldaten rekonstruiert: der Druck liefert einen Fachfehler,
die API kennzeichnet den Snapshot als `INVALID` mit Warnung. Nur der bereits bestehende
Fallback für **fehlende** Alt-Snapshots erzeugt eine deutlich gekennzeichnete aktuelle
Aufstellung; diese wird dadurch nicht zu einem historischen Erteilungs-Snapshot.

### Korrelation der Saga-Kompensation

Die Antrags-Saga schreibt mit dem Bescheid atomar den Journal-Marker
`Antrags-Saga <id>: Bescheid-ID <bescheidId>`. Die Kompensation hebt nur die so
zugeordneten Bescheide derselben sagaeigenen BG auf; manuelle weitere Bescheide
werden nicht pauschal mit aufgehoben. Fehlt bei einer Alt-Saga dieser ID-Marker und
existiert noch ein `ERTEILT`-Bescheid, bricht die Kompensation mit
`SAGA_BESCHEID_ZUORDNUNG` ab: `FAILED` und manuelle Klärung statt eines unbelegten
`COMPENSATED`. Ohne vorhandenen Bescheid bleibt der nicht committete
`IN_FLIGHT`-Schritt eine zulässige No-Operation. Noch ältere Sagas ohne
Vorgangs-Anlagemarker benötigen ebenfalls manuelle Prüfung.

### Zeitbasis des Saga-Journals

Die vorhandenen `TIMESTAMP`-Spalten speichern den aus `SYSTIMESTAMP` übernommenen
Wanduhrwert des DB-Servers ohne Zeitzoneninformation. Der Altersfilter verwendet
deshalb auf beiden Seiten dieselbe DB-Zeitbasis:
`CAST(SYSTIMESTAMP AS TIMESTAMP) - NUMTODSINTERVAL(?, 'SECOND')`. Es erfolgt keine
implizite Umrechnung über die JVM- oder Session-Zone und kein Wechsel zu UTC.
Die bisherigen auf derselben DB-Zeitbasis erzeugten Werte werden nicht nachträglich
umgedeutet. Ein Wechsel der DB-Server-Zone oder Wanduhrsprünge kann den Altersfilter
weiterhin beeinflussen; die exklusive Kopf-Sperre bleibt der Schutz aktiver Sagas.
Fremde Legacy-Zeitwerte dürfen ohne Herkunftsnachweis keiner Zone zugeordnet werden.

### Begrenzte HTTP-Anfragen und strikte Eingaben

`HttpServerLimits.configure()` läuft in den Server-Startpfaden vor dem ersten
JDK-`HttpServer.create`. Die Properties gelten JVM-weit, werden vom JDK einmalig
eingelesen und gehören nicht zur `LmgConfig`-Datei-/Umgebungskaskade:

| JVM-Property (`-D…`) | Standard | Zulässiger Bereich |
|---|---:|---:|
| `sun.net.httpserver.maxReqTime` | 30 Sekunden | 1–300 Sekunden |
| `sun.net.httpserver.maxRspTime` | 120 Sekunden | 1–600 Sekunden |
| `sun.net.httpserver.idleInterval` | 30 Sekunden | 1–300 Sekunden |
| `jdk.httpserver.maxConnections` | 256 | 16–4096 |

Die Sekundenangaben für Request und Response entsprechen der Umrechnung in
[OpenJDK 21 `ServerImpl.getTimeMillis`](https://github.com/openjdk/jdk21u/blob/master/src/jdk.httpserver/share/classes/sun/net/httpserver/ServerImpl.java).
Die Timer haben eine JDK-abhängige Prüfgranularität; es ist kein millisekundengenauer
Abbruch zugesichert. Ungültige, unbegrenzte oder nach Initialisierung geänderte Werte
verhindern den Start statt still auf unbeschränkte Laufzeiten zurückzufallen.

Die API verlangt passende JSON-Typen. Ganzzahlen werden mit exakter 64-/32-Bit-Prüfung
gelesen; Bruchteile und Überläufe werden nicht abgeschnitten. Optionale boolesche oder
numerische Felder dürfen bei fehlender Angabe ihren dokumentierten Default behalten,
falsche Typen werden jedoch mit HTTP 400 `VALIDATION` abgewiesen. Auch der Bescheid-Druck
meldet eine volle Task-Warteschlange mit HTTP 503 `UEBERLAST` und `Retry-After: 2`.
Technische HTTP- und Diagnosefehler enthalten nur Fehlerklassen/ORA-Codes, keine
Exception-Texte oder rohen Requestpfade mit möglichen Sozialdaten. `LmgServer.close()`
beendet zusätzlich den eigenen HTTP-Executor; die Task-Steuerung wird separat geschlossen.

Statische Dateien unterstützen `GET` und `HEAD`; `HEAD` liefert dieselben
Metadaten ohne Antwortkörper. Schreibmethoden werden mit HTTP 405 und
`Allow: GET, HEAD` abgewiesen.

### Startabbruch bei Konfigurations- oder Wiederanlauffehlern

Eine vorhandene Konfigurationsdatei wird vollständig als UTF-8 gelesen; ein
anfängliches UTF-8-BOM ist zulässig. Unlesbare Dateien, fehlerhafte UTF-8-Bytes und
ungültige Properties-Escapes brechen den Start ab. Bereits gelesene Teilwerte und
Defaults ersetzen keine defekte Datei. Eine tatsächlich fehlende optionale Datei
kann weiterhin durch Klassenpfadkonfiguration beziehungsweise Defaults ersetzt
werden. Der Pooltyp erlaubt nur `ucp` oder `simple`; Zahlwerte müssen in ihren
zulässigen Integerbereich passen. Properties-Dateien behalten die üblichen
Escape-Regeln: Windows-Pfade mit `/` oder doppeltem Backslash schreiben.

Der Serverstart wertet die Recovery-Berichte aller registrierten Saga-Typen aus.
Technische Scanfehler und nicht abgeschlossene Kompensationen verhindern den Start
mit einer klaren Fehlermeldung. Der HTTP-Listener und sein Executor werden erst nach
erfolgreichem Wiederanlauf angelegt; vorhandene Pool-/Taskressourcen werden bei
Startfehlern geschlossen. Ein fehlgeschlagener Scan wird weder als „0 offene Sagas“
noch durch einen normalen Erfolgsbanner dargestellt. Fehlerhafte Alt-Sagas benötigen
vor einem erneuten Start eine manuelle Prüfung des Journals und ihrer Fachwirkung.

### Persistierte Rückgaben der Zahlungs- und Bescheid-DAOs

Nach einem INSERT lesen `ZahlungDao` und `BescheidDao` die gespeicherte Zeile über
ihren vorhandenen Mapper zurück. Die unmittelbare Rückgabe enthält damit denselben
Datenbank-Zeitstempel und dieselben Daten wie ein anschließendes Lesen. Ein separat
erzeugtes `Instant.now()` ersetzt den gespeicherten Wert nicht mehr. Geldwerte werden
ohne weitere Rundung auf zwei Nachkommastellen dargestellt; Oracle kann bei vollen
Eurobeträgen die Nullstellen im JDBC-Wert weglassen. Zwei Oracle-Regressionen prüfen
Zeitstempel, Null-Mapping, vorgegebenes Bescheiddatum und Cent-Skalierung.

Das ändert weder die Zeitbasis bestehender TIMESTAMP-Spalten noch interpretiert es
historische Werte nachträglich als UTC. Eine solche Migration benötigt einen
belegbaren Ursprung der gespeicherten Zeitwerte.

## Gesamt-Review-Korrekturen vom 15.10.2026 (v0.5.8)

- Admin-Prüfung und Zahlungsrouter verwenden dieselbe Pfadstruktur; die Stornoaktion prüft die Rolle zusätzlich direkt vor dem Serviceaufruf.
- Fehlversuche konfigurierter Konten haben eigene Zähler. Eine volle Liste unbekannter Kennungen verdrängt deren Sperren nicht.
- Einmal-Drucktoken sind an Herkunftssitzung und Ziel-BG gebunden. Logout, Sitzungsablauf und die 60-Sekunden-Grenze entwerten sie. POST `/api/drucktoken` erwartet `{"bgId":123}`.
- Snapshot v3 speichert den vollständigen Bescheid als HTML zusammen mit dem Monatsplan in `BESCHEID.BERECHNUNG`. Service und Saga erzeugen ihn unter BG-Sperre in der Erteilungstransaktion. Nachdruck verwendet den gespeicherten Dokumentstand einschließlich Bankdaten, Behörde und Belehrung. V1/V2 bleiben lesbar, kennzeichnen aber die Ergänzung heutiger Dokumentdaten. Ganzzahlen und Bescheid-/BG-/Monatsplan-Bindung werden strikt geprüft. Eine neue DDL-Migration ist nicht erforderlich.
- Die elektronische Widerspruchsform verweist auf § 84 Abs. 1 SGG i.V.m. § 36a Abs. 2/2a SGB I und § 9a Abs. 5 OZG; § 65a SGG bleibt der gerichtlichen Kommunikation zugeordnet.
- Eine unter Claim gesperrte PENDING-Saga ohne ausgeführten Schritt wird terminal COMPENSATED abgeschlossen, der Payload gelöscht. Widersprüchliche Schrittlisten bleiben FAILED und erfordern Klärung.
- RetryPolicy erlaubt ausschließlich die Oracle-Codes 54, 60 und 8177. SQLState 61000 allein ist keine Wiederholungsfreigabe.
- Produkt und unabhängige Referenzen vergleichen die günstigere Alleinerziehendenalternative. Zentrale Warmwasserkosten bleiben in den Heizkosten; der alte Abzugsparameter ist wirkungslos.
- Java und Node akzeptieren beim LLM-Antwortvertrag nur ein vollständiges JSON-Objekt mit exakt Schema 2. Ein gemeinsamer Rohbytes-Korpus prüft u.a. Codeblöcke, doppelte Schlüssel, Unicode und Dezimalpräzision.

Die Korrekturen ändern bereits erteilte Bescheide nicht rückwirkend. Unzutreffende Altbescheide benötigen eine fachliche Korrektur; fehlende historische Dokumentdaten lassen sich aus alten Berechnungssnapshots nicht rekonstruieren.
