# Architektur der Transaktionssteuerung

*Stand: 2026-10-10, Version 0.5.2*

## Schichtenbild

```
Fachschicht: Leistungsmanagement Grundsicherung (SDN.sozial LMG)
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
| `saga.SagaEngine` | Führt `SagaDefinition` als Kette kurzer Transaktionen aus; kompensiert bei Fehlern rückwärts; `recover()` beendet halbfertige Vorgänge **aller registrierten Saga-Typen** nach Absturz/Neustart (claimt jede Saga mit `FOR UPDATE SKIP LOCKED` gegen parallele Wiederanläufe). |
| `saga.SagaJournal` | DAO für die Journal-Tabellen `TX_SAGA`/`TX_SAGA_STEP`; Journal-Schreibakte mit Zustands-Guards und geprüften Update-Zählern. |
| `saga.SagaDefinition/Step` | Deklarative Vorgangsbeschreibung: benannte Schritte mit Aktion + Kompensation. |
| `tx.audit.TxAuditLog` | Revisionssicheres Audit (Commit/Rollback/Retry/Timeout/Saga-Ereignisse), Ringpuffer, Listener, optionale Datei. |
| `tx.monitoring.TxMonitoring` | Zentrale JMX-Registrierung (TaskController-, UCP- und generische Pool-MBeans). |
| `demo.DemoMain` | Lauffähiges Beispiel (Antrag → Bescheid → Auszahlung) inkl. Fehlerfall und parallelen Tasks. |

### Fachschicht Leistungsmanagement (`de.allgemein.lmg`, Details: [leistungsmanagement.md](leistungsmanagement.md))

| Baustein | Verantwortung |
|---|---|
| `lmg.calc.Leistungsberechnung` | Reine SGB-XII-Berechnung (Regelbedarfsstufen 2026, Mehrbedarfe § 30 Abs. 1/2/3/7 mit Obergrenze Abs. 6, KdU § 42a mit Angemessenheitsgrenze/Warmwasser-Abzug, Absetzbeträge § 82 Abs. 2, Erwerbsfreibetrag § 82 Abs. 3, Vermögensanrechnung § 43 Abs. 1/§ 90 Abs. 2 Nr. 9), je SozialFall und summiert je Bedarfsgemeinschaft. |
| `lmg.service.SozialLeistungsService` | Fachdienste als kurze Transaktionen: Zahlungsweg (Idempotenz → FOR-UPDATE-Sperre → Budget-Prüfung → Zahlung immer `ANGELEGT` → Restbudget mindern → Journal; Vier-Augen serverseitig erzwungen), Freigabe/Kassenlauf/Storno (guarded Statuswechsel), Masken-Speicherung mit optimistischem Locking. |
| `lmg.service.BescheidGenerator` | Amtlicher, druckfertiger Bewilligungsbescheid (§ 44 SGB XII): Verfügungssatz, Bedarfsaufschlüsselung mit KdU-Vorbehalt, fallbezogene Begründung (§ 35 SGB X), konfigurierbare Rechtsbehelfsbelehrung (§ 36 SGB X). |
| `lmg.service.SepaValidator` | IBAN/BIC-Validierung nach ISO 13616 (Modulo-97). |
| `lmg.saga.AntragsSagaProzess` | 4-stufige Fach-Saga Antrag → Bedarfsprüfung → Bescheid → Erstauszahlung mit idempotenter Rückwärtskompensation (Budget-Rückbuchung, Historisierung statt Löschung). |
| `lmg.dao.*` | DAOs auf der Verbindung des `TxContext`, ausschließlich PreparedStatements; keine eigenen Connections. |
| `lmg.config.LmgConfig` | Externalisierte Konfiguration (System-Properties > Umgebungsvariablen > `lmg.properties` > Defaults), inkl. KdU-, Rechtsbehelf- und Session-Parameter. |
| `lmg.web.LmgServer/LmgServerMain` | JSON-API + Oberfläche auf dem JDK-HttpServer; jede Anfrage (auch der Bescheid-Druck) läuft als Task über den `TaskController`; CSP-Header, Body-Limit, Token nur aus dem Authorization-Header (Ausnahme: Druck-Link). |
| `lmg.web.Benutzerverwaltung` | Anmeldung mit Rollen; Sitzungs-Token (SecureRandom/UUID) mit Idle-Timeout, Anmelde-Drosselung, constant-time-Passwortvergleich. |
| `lmg.web.Json` | Schlanker JSON-Writer/Parser ohne externe Bibliothek (Tiefenlimit, vollständige Escape-Prüfung). |
| `webapp/` (Ressourcen) | Single-Page-Oberfläche (SDN.sozial-Stil): Widget-Dashboard, Vorgangsmaske, Zahlbarmachung, Journal — HTML-Escaping für alle Serverdaten. |

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
   Absturz überlebt.
2. Je Schritt: **Write-Ahead** — Schritt im Journal auf `IN_FLIGHT` setzen
   (eigener Commit), dann Aktion + `DONE`-Markierung **in derselben
   Transaktion** committen. Ein Crash dazwischen erkennt der Wiederanlauf am
   `IN_FLIGHT`-Zustand und behandelt den Schritt konservativ als
   möglicherweise ausgeführt (Kompensation ist idempotent).
3. Fehler in einem Schritt: Saga auf FAILED, dann **rückwärts kompensieren** —
   jede Kompensation in eigener Transaktion, Journal auf COMPENSATED; schlägt
   eine Kompensation fehl, endet die Saga FAILED und der nächste Wiederanlauf
   setzt tiefer an (kein stiller Reihenfolge-Bruch).
4. Alles geschafft: Saga auf COMPLETED (ein Fehler hierbei löst **keine**
   Kompensation aus — der Wiederanlauf schließt später ab).
5. **Wiederanlauf** (`recover`): beim Neustart alle Sagas **aller registrierten
   Definitionen** in RUNNING/FAILED/COMPENSATING lesen, je Saga mit
   `FOR UPDATE SKIP LOCKED` claimen (parallel-sicher), DONE- und
   IN_FLIGHT-Schritte kompensieren, COMPENSATED-Schritte überspringen —
   deshalb müssen Kompensationen idempotent sein.

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

## Bekannte Grenzen (Stand v0.5.0)

- Watchdog-Abbruch im Commit-Fenster: Das klassische TP-Monitor-Restfenster
  (Commit durch, Abbruch dem Aufrufer gemeldet) ist nicht vollständig
  eliminierbar; dokumentiert im `TaskController`-Javadoc, Auflösung nur über
  TX-IDs + Recovery-Abfrage beim DB-Server.
- `SimpleConnectionPool` bewusst minimal (Demo/Tests); Produktion läuft über
  Oracle UCP (`validateConnectionOnBorrow`).
- Passwörter als Klartext-Konfiguration, Sitzungen im Speicher — Produktivweg
  ist LDAP/AD hinter HTTPS (siehe Doku Leistungsmanagement).
- Audit-Log ohne Rotation; Archivierungsstrategie für historisierte Vorgänge
  und Journal-Einträge steht noch aus.
- Anmelde-Drosselung und Session-Timeout sind einfach (in-memory) — für
  horizontale Skalierung wäre ein verteilter Sitzungsspeiler nötig.
