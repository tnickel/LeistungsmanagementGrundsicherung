# Roadmap: Transaktionssteuerung

*Stand: 2026-10-09, Version 0.4.0 (SEPA, Vier-Augen-Prinzip, Bescheidwesen § 37 SGB X, Antrags-Saga)*

## Erreicht (v0.2.0 – v0.3.0)

- [x] Kern: `TxManager`, `TxContext`, `TxOptions` (Begin/Commit/Rollback, Deadline, Savepoints)
- [x] `RetryPolicy` — ORA-00054/00060/08177 → Wiederholversuch mit Backoff
- [x] `TaskController` — begrenzte Parallelität, Timeout-Wachhund, `TaskStats`
- [x] Saga-Engine + Journal (`TX_SAGA`, `TX_SAGA_STEP`) inkl. `recover()` (Wiederanlauf)
- [x] Oracle-DDL, Demo (`DemoMain`: Erfolgsfall, Fehlerfall mit Kompensation, parallele Tasks)
- [x] **Fachschicht Leistungsmanagement (SDN.sozial LMG)** — siehe [leistungsmanagement.md](leistungsmanagement.md):
      SGB-XII-Berechnung, Zahlungs-Workflow (Idempotenz, FOR UPDATE, Budget-Check, Journal),
      optimistisches Locking für Masken, JSON-API + Widget-Oberfläche, Fach-DDL + Seed,
      Unit-Tests für Berechnung/JSON/Formats
- [x] Neuanlage von Vorgängen und SozialFällen, Kassenlauf (BEREIT → ÜBERGEBEN),
      Storno mit Restbudget-Rückbuchung
- [x] Berechtigungsprüfung: Anmeldung mit Token, Rollen admin/sachbearbeiter,
      serverseitige Rechteerzwingung, Journal-Identität aus der Anmeldung
- [x] Regelbedarfsstufen 1–6 (2026 vollständig hinterlegt: 563 €, 506 €, 451 €, 471 €, 390 €, 357 €)
- [x] Dezentrale Warmwassererzeugung (2,3 % nach § 30 Abs. 7 SGB XII) und KdU-Dreiteilung
      (Kaltmiete + kalte Betriebskosten + Heizkosten) vollständig in DB, Modell, DAO, Berechnung und UI

## Etappe 1 — Verifikation gegen echte Oracle-DB (19c EE & 23ai Free)

- [x] Entwickler-DB: **Oracle 19c Enterprise Edition in VirtualBox** (`localhost:1921/o19c1p1`, PDB)
      sowie Oracle 23ai Free
- [x] `ddl/oracle_schema.sql` und `ddl/lmg_schema.sql` (+ `lmg_seed.sql`) vollständig eingespielt
- [x] `DemoMain` gegen echte DB (Saga inkl. Kompensation und Wiederanlauf)
- [x] `LmgServerMain` gegen echte DB + Fachabläufe live geprüft: Auszahlung bucht und
      mindert Restbudget (Trigger setzt VERSION hoch), gleiche `vorgangsNr` → Idempotenz,
      zweite Auszahlung → `BUDGET_ERSCHOEPFT`, kein Anspruch → `KEIN_ANSPRUCH`,
      Masken-Speichern mit alter VERSION → `KONKURRENZ` (HTTP 400 mit Fehlercode)
- [x] Guarded Integrationstest `EchteDbVerifikationTest` (läuft gegen echte DB bei gesetztem `TX_URL`,
      11/11 Tests grün)
- [x] **Fehler gefunden und behoben**: Permit-Leak in `SimpleConnectionPool.release()`,
      `TaskHandle.result()` wickelte Fachfehler zu Technisch-Fehlern um,
      `FachlicheException` erbt nun von `TxException` (typsicher durch den TxManager)
- [x] Deadlock-Retry real auslösen: zwei Sessions, ORA-00060 provozieren,
      beobachten: 2. Versuch committet (verifiziert in `DeadlockRetryEchtTest`)

## Etappe 2 — Produktionsbausteine (v0.4.0)

- [x] `SimpleConnectionPool` durch **Oracle UCP** ergänzt (`ConnectionPool`-Schnittstelle,
      `UcpConnectionPool` via `com.oracle.database.jdbc:ucp`, konfigurierbar über `LMG_POOL_TYP`)
- [x] `TaskController.stats()` und `UcpConnectionPool` an **JMX** angebunden
      (`TaskControllerMBean`, `UcpConnectionPoolMBean`, zentrale Registrierung über `TxMonitoring`,
      verifiziert in `TxMonitoringTest`)
- [x] Konfiguration externalisiert (`LmgConfig`: System-Properties, Umgebungsvariablen,
      Konfigurationsdatei `lmg.properties`, Defaults nach 12-Factor-Prinzip)
- [x] Revisionssicheres Audit-Log (`TxAuditLog` für Commit/Rollback/Retry/Timeout/Saga-Ereignisse,
      Ringpuffer, Listener, optionales Datei-Log `lmg_audit.log`, verifiziert in `TxAuditLogTest`)

## Etappe 3 — Erweiterte Fachfunktionen Sozialleistungen (v0.4.0)

- [x] **SEPA-Bankverbindungsverwaltung**: IBAN, BIC und Kontoinhaber auf Vorgangsebene und Zahlungsebene;
      ISO 13616 Modulo-97-Prüfziffernvalidierung in `SepaValidator` (getestet in `SepaValidatorTest` und `EchteDbVerifikationTest`)
- [x] **Vier-Augen-Prinzip & Freigabe-Workflow**: Erfassung von Auszahlungen im Status `ANGELEGT`;
      rechnerische Feststellung & Freigabe (`ANGELEGT` → `BEREIT`) durch Zweitsachbearbeiter;
      Selbstfreigabe wird serverseitig mit Fehlercode `BERECHTIGUNG` abgewehrt
- [x] **Bescheidwesen nach § 37 SGB X**: Tabelle `BESCHEID`, `BescheidDao`, `Bescheid`-Modell,
      Erteilung im Service `SozialLeistungsService.erteileBescheid()`, Generierung einer amtlichen,
      druckfertigen HTML-Druckansicht (`BescheidGenerator`) mit Verfügungssatz, Bedarfsaufschlüsselung,
      Begründung (§ 35 SGB X), Rechtsbehelfsbelehrung (§ 36 SGB X) und `@media print`-Stylesheet
- [x] **End-to-End-Saga für Geschäftsvorgänge (`AntragsSagaProzess`)**: Kette von 4 unteilbaren Schritten:
      1. Vorgang erfassen (Bedarfsgemeinschaft + SozialFall anlegen)
      2. Bedarfsprüfung (SGB-XII-Berechnung; Abbruch bei fehlendem Anspruch)
      3. Bescheid erteilen (Bewilligungsbescheid anlegen und journalisieren)
      4. Erstauszahlung anweisen (Zahlung Status BEREIT, Restbudget mindern)
      Inklusive automatischer Rückwärtskompensation bei Abbruch (Schritt 1 wird gelöscht,
      Journal-Audit protokolliert)
- [x] **Web-UI & API-Anbindung**: Neue Endpunkte `/api/bedarfsgemeinschaften/{id}/bescheid`,
      `/api/bedarfsgemeinschaften/{id}/bescheid/druck`, `/api/saga/antrag`;
      Druck-Button und E2E-Saga-Ausführung direkt im Frontend eingebunden

## Etappe 4 — Betrieb & Weiterentwicklung

- [x] Wiederanlauf beim Serverstart (recover wird beim Hochfahren ausgeführt und per Admin-Button)
- [x] Revisionssicheres Audit-Log & Journal
- [ ] XA / Two-Phase-Commit, falls mehrere Datenbanken in eine logische Einheit müssen (aktuell nicht erforderlich)
- [ ] Archivierung historisierter Vorgänge und alter Journal-Einträge
- [ ] LDAP/AD-Anbindung hinter TLS/HTTPS für den behördlichen Produktivbetrieb

## Offene Fragen

1. Welche Oracle-Version/Edition läuft bei uns (relevant für Testcontainers-Image und XA)?
2. Wie viele parallele Sachbearbeiter/Aufträge müssen dimensioniert werden?
3. Kommt ein Web-Frontend davor? Dann Session-Handling und Authentifizierung hier verankern.
