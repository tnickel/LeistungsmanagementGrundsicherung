# Architektur der Transaktionssteuerung

*Stand: 2026-09-24, Version 0.2.0*

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
│  SimpleConnectionPool (Platzhalter → HikariCP/UCP)   │
└──────────────────────────────────────────────────────┘
        │
        ▼
   Oracle DBMS  ── ACID, Sperrverwaltung, Redo-Logging,
                   Recovery (kommt aus der Datenbank!)
```

## Bausteine

| Klasse | Verantwortung |
|---|---|
| `tx.TxManager` | Kern: führt `TxWork` in einer Oracle-Transaktion aus. Commit bei Erfolg, Rollback bei jedem Fehler, Retry nur bei wiederholbaren DB-Konflikten. Zusätzlich manuelle API: `begin`/`commit`/`rollback`/`finish`. |
| `tx.TxContext` | Repräsentiert eine laufende Transaktion: Verbindung, Zeitlimit (Deadline), Status, Savepoints. `checkDeadline()` rufen Fachschritte zwischen Teilarbeiten auf. |
| `tx.TxOptions` | Wertobjekt: Isolationsebene (Standard READ COMMITTED), Timeout (Standard 30 s), max. Versuche (Standard 3). |
| `tx.RetryPolicy` | Klassifiziert `SQLException`: ORA-00054/00060/08177 bzw. SQLState 40001/61000 sind wiederholbar. Exponentieller Backoff mit Zufallsanteil. |
| `tx.TaskController` | Task-Steuerung: fester Thread-Pool + begrenzte Warteschlange, Timeout-Wachhund (`cancel(true)` → Interrupt → JDBC wirft → Rollback), Statistik (`TaskStats`). |
| `tx.TaskHandle` | Ergebnis-Handle pro Task: `result()`, `resultOrCancelAfter()`, `cancel()`. |
| `tx.SimpleConnectionPool` | Minimaler Pool (Semaphor + Warteschlange). Bewusst austauschbar: HikariCP/UCP in Produktion. |
| `saga.SagaEngine` | Führt `SagaDefinition` als Kette kurzer Transaktionen aus; kompensiert bei Fehlern rückwärts; `recover()` beendet halbfertige Vorgänge nach Absturz/Neustart. |
| `saga.SagaJournal` | DAO für die Journal-Tabellen `TX_SAGA`/`TX_SAGA_STEP`. Jede Journal-Schreiboperation committet sofort (getrennt von den Fachtransaktionen). |
| `saga.SagaDefinition/Step` | Deklarative Vorgangsbeschreibung: benannte Schritte mit Aktion + Kompensation. |
| `demo.DemoMain` | Lauffähiges Beispiel (Antrag → Bescheid → Auszahlung) inkl. Fehlerfall und parallelen Tasks. |

### Fachschicht Leistungsmanagement (`de.allgemein.lmg`, Details: [leistungsmanagement.md](leistungsmanagement.md))

| Baustein | Verantwortung |
|---|---|
| `lmg.calc.Leistungsberechnung` | Reine SGB-XII-Berechnung (Regelbedarf 2026, Mehrbedarf 17 % Merkzeichen G, Erwerbsfreibetrag min(30 %, 281,50 EUR)), je SozialFall und summiert je Bedarfsgemeinschaft. |
| `lmg.service.SozialLeistungsService` | Fachdienste als kurze Transaktionen: Zahlungs-Workflow (Idempotenz → FOR-UPDATE-Sperre → Budget-Prüfung → Zahlung BEREIT → Restbudget mindern → Journal) und Masken-Speicherung mit optimistischem Locking (`UPDATE … WHERE VERSION = ?`). |
| `lmg.dao.*` | DAOs auf der Verbindung des `TxContext`, ausschließlich PreparedStatements; keine eigenen Connections. |
| `lmg.web.LmgServer/LmgServerMain` | JSON-API + Oberfläche auf dem JDK-HttpServer; jede Anfrage läuft als Task über den `TaskController`. |
| `lmg.web.Json` | Schlanker JSON-Writer/Parser ohne externe Bibliothek. |
| `webapp/` (Ressourcen) | Single-Page-Oberfläche (SDN.sozial-Stil): Widget-Dashboard, Vorgangsmaske, Zahlbarmachung, Journal. |

## Ablauf einer kurzen Transaktion (`TxManager.execute`)

1. Verbindung aus dem Pool holen, `autoCommit=false`, Isolation setzen.
2. `TxWork` ausführen (das Fach-SQL).
3. Deadline prüfen — nach Zeitlimit wird nicht mehr committet.
4. `COMMIT` bei Erfolg; bei jeder Exception `ROLLBACK`.
5. Exception-Klassifikation: wiederholbar (ORA-00054/00060/08177) → Backoff und
   neuer Versuch (bis `TxOptions.maxAttempts`), sonst `TxException` sofort.
6. Verbindung in jedem Fall zurück in den Pool (Auto-Commit wird zurückgesetzt).

## Ablauf eines Saga-Vorgangs (`SagaEngine.run`)

1. Journal-Eintrag anlegen: `TX_SAGA` (Status RUNNING) + je Schritt eine Zeile in
   `TX_SAGA_STEP` (Status PENDING) — **eigener Commit**, damit der Eintrag einen
   Absturz überlebt.
2. Je Schritt: Aktion in eigener kurzer Transaktion (`TxManager.execute`),
   danach Journal-Update auf DONE (eigener Commit).
3. Fehler in einem Schritt: Saga auf FAILED, dann **rückwärts kompensieren** —
   jede Kompensation wieder in eigener Transaktion, Journal auf COMPENSATED.
4. Alles geschafft: Saga auf COMPLETED.
5. **Wiederanlauf** (`recover`): beim Neustart alle Sagas in RUNNING/FAILED/
   COMPENSATING lesen, bereits DONE-Schritte kompensieren; bereits COMPENSATED
   Schritte überspringen. Deshalb müssen Kompensationen **idempotent** sein.

## Datenmodell (`ddl/oracle_schema.sql`)

| Tabelle | Zweck |
|---|---|
| `TX_SAGA` | Kopf je Vorgang: SAGA_ID (UUID), Typ, Status, Payload, Zeitstempel |
| `TX_SAGA_STEP` | Je Schritt: Index, Name, Status (PENDING/DONE/COMPENSATED) |
| `TX_DEMO_ANTRAG`, `TX_DEMO_ZAHLUNG` | Demo-Tabellen für das Beispiel |
| `BEDARFSGEMEINSCHAFT`, `SOZIAL_FALL`, `ZAHLUNG`, `JOURNAL` | Fachschema des Leistungsmanagements (`ddl/lmg_schema.sql`, Seed `ddl/lmg_seed.sql`) |

Status-Automatik Saga: `RUNNING → COMPLETED`, `RUNNING → FAILED → COMPENSATING →
COMPENSATED` (bzw. zurück zu FAILED, wenn eine Kompensation fehlschlägt).

## Architekturregeln

1. **Kurze Transaktionen, lange Sagas.** Atomar Requiredes gehört in eine
   `TxManager.execute`-Transaktion; Minuten-dauernde bzw. interaktive Abläufe
   werden zu Saga-Schritten zerlegt.
2. **Wiederholbar heißt ganz wiederholen** — nur Sperren-Konflikte; fachliche
   Fehler (z. B. Prüfbedingung) brechen ohne Retry ab.
3. **Kompensationen idempotent** (zustandsabhängige SQL-Updates, siehe Demo).
4. **Kein XA im Kern.** Mehrere Datenbanken in einer logischen Einheit erst bei
   echtem Bedarf (dann Oracle-XA/Two-Phase-Commit).
5. **Pool-Abstraktion halten:** alles hängt nur an `getConnection`/`release`.

## Bekannte Grenzen von v0.1.0

- `SimpleConnectionPool` ohne Validierung/Leak-Erkennung — Produktion: HikariCP.
- Task-Wachhund bricht über Thread-Interrupt ab; sehr lange blockierende
  Native-Aufrufe (nicht JDBC) reagieren darauf nicht.
- Monitoring ist eine In-Memory-Statistik; Anbindung an JMX/Prometheus steht aus.
- Die Saga-Journal-Zeilen bleiben dauerhaft erhalten (Audit-Basis); eine
  Archivierungsstrategie ist noch nicht gebaut.
