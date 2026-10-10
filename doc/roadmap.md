# Roadmap: Transaktionssteuerung

*Stand: 2026-10-10, Version 0.5.1 (externe Gegenprüfung eingearbeitet: Title-XSS, Betrags-Grenzwert, Jahresfenster, CSP-Vollständigkeit)*

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
      4. Erstauszahlung anweisen (Zahlung Status BEREIT — seit v0.5.0: ANGELEGT, Vier-Augen
         serverseitig erzwungen; Restbudget mindern)
      Inklusive automatischer Rückwärtskompensation bei Abbruch (Schritt 1 wird gelöscht,
      Journal-Audit protokolliert)
- [x] **Web-UI & API-Anbindung**: Neue Endpunkte `/api/bedarfsgemeinschaften/{id}/bescheid`,
      `/api/bedarfsgemeinschaften/{id}/bescheid/druck`, `/api/saga/antrag`;
      Druck-Button und E2E-Saga-Ausführung direkt im Frontend eingebunden

## Etappe 5 — Umsetzung des umfassenden Codereviews (v0.5.0)

### Fachrecht SGB XII vollständig ergänzt

- [x] **§ 30 Abs. 2 SGB XII** — Schwangeren-Mehrbedarf (17 % ab 12. SSW): Datenmodell,
      Berechnung, Maske, Tests
- [x] **§ 30 Abs. 3 SGB XII** — Alleinerziehenden-Mehrbedarf (36 % / 12 % je Kind,
      Deckel 60 % RBS 1): Kinder unter 7 / unter 16 im Datenmodell und in der Berechnung
- [x] **§ 30 Abs. 6 SGB XII** — Obergrenze: Mehrbedarfe übersteigen nie den Regelbedarf
- [x] **§ 30 Abs. 7 SGB XII korrigiert** — Warmwasser-Mehrbedarf stufenabhängig
      (2,3/2,3/–/1,4/1,2/0,8 % statt pauschal 2,3 %), mit Tests für Stufen 4–6
- [x] **§ 82 Abs. 2 SGB XII** — Absetzbeträge (Steuern, Sozialversicherung,
      Versicherungen, Erwerbsaufwendungen) vor dem Freibetrag des § 82 Abs. 3
- [x] **§ 42a i.V.m. § 35 SGB XII** — KdU-Angemessenheitsprüfung mit konfigurierbarer
      Grenze (Übernahme unter Vorbehalt, Bescheid-Kennzeichnung) und konfigurierbarem
      Warmwasser-Abzug von den Heizkosten
- [x] **§ 43 Abs. 3, § 90 SGB XII** — Vermögensanrechnung: Vermögen je BG,
      Schonvermögen 10.000 € + 750 € je Mitglied (§ 90 Abs. 2 Nr. 14), mindert Zahlbetrag
- [x] **§ 44 SGB XII** — Bewilligungszeitraum-Validierung (YYYY-MM, von ≤ bis),
      STATUS_AKTIV-Prüfung; Normzitationen im Bescheid korrigiert
      (Bewilligung § 44 SGB XII, Bekanntgabe § 37 SGB X statt pauschal „§ 37 SGB X“)
- [x] **§ 36 SGB X** — vollständige, konfigurierbare Rechtsbehelfsbelehrung
      (Widerspruch mit Behörde/Sitz oder direkte Klage zum Sozialgericht nach § 87 SGG),
      fallbezogene Begründung (§ 35), Unterschrifts-/Datumszeile

### Transaktionskern und Saga (Review-Funde)

- [x] `TxManager.finish()` repariert (Connection-Leak bei manueller API);
      Error (OOM/StackOverflow) führt zu Rollback statt implizitem Commit;
      toter Code entfernt
- [x] Beide Pools rollen vor `setAutoCommit(true)` zurück (kein implizites Commit);
      UCP-Release-Leak geschlossen; SimplePool mit Borrow-Validierung, idempotentem
      Release und Borrow-Timeout
- [x] TaskController: Wachhund misst ab Ausführungsbeginn, kein Leak in der
      Running-Map bei Abbruch, Interrupt-Flag-Räumung, geordneter Shutdown,
      `CancellationException` als TxException typisiert
- [x] Saga: Journal-Write-Ahead (`IN_FLIGHT` vor Ausführung, `DONE` in derselben
      Transaktion wie der Fachschritt), `recover()` über **alle** Saga-Typen mit
      `FOR UPDATE SKIP LOCKED`-Claim gegen parallele Wiederanläufe, Guards +
      geprüfte Update-Zäher im Journal, `markState(COMPLETED)`-Fehler löst keine
      Fehl-Kompensation aus, recover fehlertolerant je Saga
- [x] AntragsSagaProzess: Kompensation bucht das Restbudget zurück, storniert nur
      die eigene Zahlung (Korrelation über VORGANGS_NR), historisiert den Vorgang
      statt zu löschen (FK-Blocker aufgelöst, § 67 SGB X Nachvollziehbarkeit),
      Zahlung im Saga-Weg als `ANGELEGT` (Vier-Augen)
- [x] Neue Unit-Tests: `TaskControllerTest` (Wachhund/Queue-voll/Interrupt),
      `SimpleConnectionPoolTest` erweitert (Erschöpfung/Doppel-Release/Validierung),
      `SagaEngineTest` (Write-Ahead, Kompensationsreihenfolge, recover-Idempotenz)

### Web, Sicherheit, Betrieb

- [x] **Vier-Augen serverseitig erzwungen** — jede Zahlung startet als `ANGELEGT`
      (Request-Feld `vierAugenErforderlich` wird ignoriert); Admin-Sonderrolle über
      die Rolle statt über eine Kennung
- [x] **Storno-Race geschlossen** — Storno sperrt die Zahlungszeile, guarded
      Statuswechsel (`ANGELEGT/BEREIT → STORNIERT`) mit geprüfter Update-Zählung;
      Kassenlauf übergibt exakt die gesperrte Menge (Anzahl == Summe konsistent)
- [x] **XSS geschlossen** — zentrale `esc()`-Funktion für alle Serverdaten im
      Frontend, CSP-Header auf allen Antworten, Fehlertexte ohne Eingabe-Echo
- [x] Sessions mit Idle-Timeout (konfigurierbar), Anmelde-Drosselung,
      constant-time-Passwortvergleich; Token nur noch aus dem Authorization-Header
      (Ausnahme: Druck-Link)
- [x] `lmg.benutzer` über die volle Konfigurationskaskade verdrahtet (die Datei wurde
      zuvor still ignoriert); Body-Limit 1 MB; JSON-Tiefenlimit;
      interne Fehlerdetails nur noch im Server-Log; 403 für BERECHTIGUNG
- [x] Journal trägt die Kennung des angemeldeten Sachbearbeiters auch bei
      Maskenänderungen; Journal-Vorgangszuordnung aus dem Datenbestand (nicht Body)
- [x] Seed: drei ungültige Demo-IBANs korrigiert (Modulo-97 verifiziert);
      Showcase-Vorgang für Schwangeren-/Alleinerziehenden-Mehrbedarf + Vermögen
- [x] Doku konsistent: README (Endpunkte, Beispiel, Testzahl, keine Micrometer-/
      SEPA-XML-Erfindungen), architektur.md auf v0.5.0, properties.example mit
      Semikolon-Beispiel, AGENTS.md Stand

### Externe Gegenprüfung eingearbeitet (v0.5.1)

Ein unabhängiger Audit einer zweiten KI wurde Fund für Fund nachgeprüft; die
bestätigten Funde sind behoben, die widerlegten (u. a. „recover fehlt beim
Serverstart", „CICS-Kommentare verstoßen gegen Namensregel", numerische
app.js-Interpolationen als XSS) dokumentiert nicht umgesetzt:

- [x] **Title-XSS im Bescheid geschlossen** — `bescheidNr()` (enthält das
      Aktenzeichen) wird jetzt auch im `<title>` der Druckansicht escaped;
      Regressionstest mit `</title><script>`-Payload (`BescheidGeneratorTest`)
- [x] **Betrags-Grenzwert prüft nach der Rundung** — 99.999.999,999 wird jetzt
      fachlich abgewiesen, statt auf 100.000.000,00 aufzurunden und erst mit
      ORA-01438 in der DB zu scheitern (`SozialLeistungsServiceTest`)
- [x] **Zahlungsmonat mit Jahresfenster 1900–2099** — Extremjahre (0000, 9999)
      werden abgewiesen (`ZahlungsmonatTest`)
- [x] **CSP konsequent auf allen Antwortpfaden des Druck-Endpoints**
      (inkl. 400/500/401 mit `nosniff`)
- [x] Doku: README-API-Tabelle vervollständigt, Handbuch-Seitenzahl (42)
      korrigiert, Etappen-Historie mit v0.5.0-ANGELEGT-Klammer versehen
- [ ] Aus Audit übernommene, weitere Testideen (echter Prozess-Kill statt
      synthetischem Crash-Zustand, paralleler Wiederanlauf als Integrationstest,
      Session-Timeout-Timing) — siehe Etappe 4

## Etappe 4 — Betrieb & Weiterentwicklung

- [x] Wiederanlauf beim Serverstart (recover wird beim Hochfahren ausgeführt und per Admin-Button)
- [x] Revisionssicheres Audit-Log & Journal
- [ ] XA / Two-Phase-Commit, falls mehrere Datenbanken in eine logische Einheit müssen (aktuell nicht erforderlich)
- [ ] Archivierung historisierter Vorgänge und alter Journal-Einträge; Audit-Rotation
- [ ] LDAP/AD-Anbindung hinter TLS/HTTPS für den behördlichen Produktivbetrieb
- [ ] Bewilligungszeitraum-Default 12 Monate (§ 44 Abs. 3) und Rückwirkung auf den
      Monatsersten (§ 44 Abs. 2) nach Antragsdatum-Erweiterung des Datenmodells
- [ ] Ausgeweitete Stufen-Zuordnung (§ 27a Paar-Regelung automatisiert plausibilisieren)

## Offene Fragen

1. Welche Oracle-Version/Edition läuft in der Zielumgebung (relevant für XA/UCP-Feinkonfiguration)?
2. Wie viele parallele Sachbearbeiter/Aufträge müssen dimensioniert werden?
3. Web-Frontend ist umgesetzt (SPA + JSON-API); für Produktion: TLS-Terminierung und
   zentrale Sitzungen (aktuell in-memory) klären.
