# Roadmap: Transaktionssteuerung

*Stand: 2026-09-24*

## Erreicht (v0.2.0)

- [x] Kern: `TxManager`, `TxContext`, `TxOptions` (Begin/Commit/Rollback, Deadline, Savepoints)
- [x] `RetryPolicy` — ORA-00054/00060/08177 → Wiederholversuch mit Backoff
- [x] `TaskController` — begrenzte Parallelität, Timeout-Wachhund, `TaskStats`
- [x] Saga-Engine + Journal (`TX_SAGA`, `TX_SAGA_STEP`) inkl. `recover()` (Wiederanlauf)
- [x] Oracle-DDL, Demo (`DemoMain`: Erfolgsfall, Fehlerfall mit Kompensation, parallele Tasks)
- [x] Build: `mvn test` grün (23 Tests), JAR baut
- [x] **Fachschicht Leistungsmanagement (SDN.sozial LMG)** — siehe [leistungsmanagement.md](leistungsmanagement.md):
      SGB-XII-Berechnung, Zahlungs-Workflow (Idempotenz, FOR UPDATE, Budget-Check, Journal),
      optimistisches Locking für Masken, JSON-API + Widget-Oberfläche, Fach-DDL + Seed,
      Unit-Tests für Berechnung/JSON/Formats

## Etappe 1 — Verifikation gegen echte Oracle-DB

- [x] Entwickler-DB: **Oracle 23ai Free in Docker** (`lmg-oracle`, PDB FREEPDB1,
      Nutzer tx_demo; siehe Schnellstart in der README)
- [x] `ddl/oracle_schema.sql` und `ddl/lmg_schema.sql` (+ `lmg_seed.sql`) eingespielt
- [x] `DemoMain` gegen echte DB (Saga inkl. Kompensation und Wiederanlauf)
- [x] `LmgServerMain` gegen echte DB + Fachabläufe live geprüft: Auszahlung bucht und
      mindert Restbudget (Trigger setzt VERSION hoch), gleiche `vorgangsNr` → Idempotenz,
      zweite Auszahlung → `BUDGET_ERSCHOEPFT`, kein Anspruch → `KEIN_ANSPRUCH`,
      Masken-Speichern mit alter VERSION → `KONKURRENZ` (HTTP 400 mit Fehlercode)
- [x] Guarded Integrationstest `EchteDbVerifikationTest` (läuft nur mit gesetztem
      `TX_URL`; ohne DB bleibt `mvn test` grün und überspringt ihn)
- [x] **Fehler gefunden und behoben**: Permit-Leak in `SimpleConnectionPool.release()`
      (Pool war nach maxSize Ausleihen tot — deshalb lief die Demo nie gegen echte DB),
      `TaskHandle.result()` wickelte Fachfehler zu Technisch-Fehlern um,
      `FachlicheException` erbt nun von `TxException` (typsicher durch den TxManager)
- [ ] Deadlock-Retry real auslösen: zwei Sessions, ORA-00060 provozieren,
      beobachten: 2. Versuch committet
- [ ] Testcontainers-Anbindung (Oracle-Free-Image) für die CI statt festem Docker-Container

## Etappe 2 — Produktionsbausteine

- [ ] `SimpleConnectionPool` durch **Oracle UCP** ersetzen (Vorgabe der Blueprint-Spec;
      HikariCP als Alternative)
- [ ] `TaskController.stats()` an **JMX** und/oder **Micrometer/Prometheus**
- [ ] Konfiguration externalisieren (Poolgröße, Timeouts, Retry-Versuche)
- [ ] Audit-Log für Commit/Rollback/Saga-Ereignisse

## Etappe 3 — Fachschicht Sozialleistungen

- [x] Ersten echten Geschäftsfall auf der Transaktionssteuerung abbilden:
      **Leistungsmanagement Grundsicherung (SGB XII)** — Bedarfsermittlung,
      Einkommensbereinigung, Zahlbarmachung (siehe [leistungsmanagement.md](leistungsmanagement.md))
- [x] Sperrverwaltung: pessimistisch (`SELECT … FOR UPDATE`) im Buchungsweg,
      optimistisch (VERSION-Spalte + Trigger) in den Masken
- [ ] Berechtigungsprüfung je Transaktion (wer darf welchen Vorgang führen) —
      aktuell nur Sachbearbeiter-Kürzel im Journal
- [ ] Regelbedarfsstufen 3–6 mit Beträgen des Bescheids hinterlegen
- [ ] Saga für mehrtägige Vorgänge nutzen (z. B. Antrag → Bescheid → Zahlung
      mit Trennung der Schritte), Zahlstatus UEBERGEBEN durch einen Batch/Job setzen

## Etappe 4 — später, bei Bedarf

- [ ] XA / Two-Phase-Commit, falls mehrere Datenbanken in eine logische Einheit müssen
- [ ] Wiederanlauf-Daemon: `SagaEngine.recover()` periodisch/automatisch beim Start
- [ ] Archivierung alter Journal-Einträge
- [ ] Konzept zur Migration bestehender Fachverfahren

## Offene Fragen

1. Welche Oracle-Version/Edition läuft bei uns (relevant für Testcontainers-Image und XA)?
2. Wie viele parallele Sachbearbeiter/Aufträge müssen dimensioniert werden?
3. Kommt ein Web-Frontend davor? Dann Session-Handling und Authentifizierung hier verankern.
