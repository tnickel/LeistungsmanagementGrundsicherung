# AGENTS.md — Projekt Transaktionssteuerung

## Was dieses Projekt ist

Eigene **Transaktionssteuerung** (Ersatz für einen TP-Monitor) für **Java 21 + Oracle**.
Hintergrund: Das IBM-Modul, das diese Aufgabe klassisch übernimmt (CICS Transaction
Server auf dem Großrechner, wie es auch im AKDN/KDN.sozial-Umfeld der Sozialverwaltung
eingesetzt wird), ist teuer lizenziert. Wir ersetzen es durch eine Eigenentwicklung.
Leitprinzip: **Oracle liefert den transaktionalen Kern** (ACID, Sperrverwaltung,
Redo-Logging, Recovery) — wir programmieren nur die Steuerungsschicht darüber.

## Wo nachschlagen

- `doc/projektbeschreibung.md` — Ausgangslage, Ziel, Leitprinzip, Scope
- `doc/architektur.md` — Bausteine, Transaktions-/Saga-Abläufe, Datenmodell, Regeln
- `doc/leistungsmanagement.md` — Fachschicht Leistungsmanagement (SDN.sozial LMG)
- `doc/roadmap.md` — aktueller Stand und geplante Etappen

## Namenskonventionen für die Fachschicht (wichtig)

- Die Oberfläche und das Fachmodul tragen die Bezeichnung **SDN.sozial** (Leistungs-
  management Grundsicherung). Der Name des Vorbild-Systems darf in neuen Artefakten
  (Code, DDL, UI, Doku) **nicht** auftauchen — nur als historischer Kontext in den
  älteren Dokumenten.
- Der zentrale Fachbegriff für einen Leistungsberechtigten ist **SozialFall**
  (Tabelle `SOZIAL_FALL`, Klasse `SozialFall`); ein Vorgang heißt
  **Bedarfsgemeinschaft**.

## Technik und Befehle

- Java 21 (`--release 21`), Maven, bewusst **keine** Frameworks; Oracle-Treiber
  (ojdbc11) ist Laufzeit-Abhängigkeit. Webschicht auf dem JDK-HttpServer, JSON über
  `de.allgemein.lmg.web.Json` (eigene, schlanke Umsetzung).
- Build + Tests: `mvn test`
- Start (bequem): `start.bat` — DB-Container bei Bedarf hochfahren, kompilieren,
  Server auf Port 8080 starten, Browser öffnen; `start.bat demo` läuft die Saga-Demo.
- Demo: `mvn compile exec:java` (Verbindung über `TX_URL`, `TX_USER`, `TX_PASS`;
  vorher `ddl/oracle_schema.sql` im Oracle-Schema einspielen)
- Fachanwendung (Weboberfläche): `mvn compile exec:java
  -Dexec.mainClass=de.allgemein.lmg.web.LmgServerMain` (Port über `LMG_PORT`,
  Standard 8080; vorher zusätzlich `ddl/lmg_schema.sql` + `ddl/lmg_seed.sql` einspielen)
- Sprache: Doku und Kommentare auf Deutsch, Code-Bezeichner auf Englisch
  (Fachbegriffe wie SozialFall/Bedarfsgemeinschaft bleiben deutsch).

## Architekturregeln

1. Kurze Transaktionen laufen über `TxManager.execute` (Commit/Rollback/Retry);
   lange Geschäftsvorgänge als Saga über `SagaEngine` mit **idempotenten**
   Kompensationen (nach Absturz können Kompensationen erneut angestoßen werden).
2. Nur Sperren-Konflikte werden wiederholt: ORA-00054, ORA-00060, ORA-08177
   (siehe `RetryPolicy`); fachliche Fehler brechen sofort ab.
3. `SimpleConnectionPool` ist bewusst minimal — in Produktion durch HikariCP oder
   Oracle UCP ersetzen; alles hängt nur an `getConnection`/`release`.
4. Keine verteilten Transaktionen (XA) im Kern — erst einführen, wenn wirklich
   mehrere Datenbanken in einer logischen Einheit nötig sind.
5. Der Task-Wachhund unterbricht Tasks nach `TxOptions.timeout`; der Interrupt
   lässt den JDBC-Aufruf fehlschlagen, wodurch die DB zurückrollt.

## Stand (Stand: 2026-10-10, Version 0.5.0)

Kernbibliothek und Fachschicht vollständig umgesetzt, umfassendem Codereview unterzogen
und auf echter Oracle 19c Enterprise Edition (VirtualBox, Port 1921/o19c1p1) sowie
23ai Free verifiziert:
- **Kern**: `TxManager` (Rollback auch bei Error/OOM — nie implizites Commit; manuelle
  API `begin/commit/finish` repariert), `TxContext`, `TxOptions`, `RetryPolicy`
  (Wiederholung bei ORA-00054/60/8177 inkl. echtem Deadlock-Test `DeadlockRetryEchtTest`),
  `TaskController` (Wachhund ab Ausführungsbeginn, settle-Gate gegen Statistik-Leaks,
  geordneter Shutdown, eigene Tests in `TaskControllerTest`), `SagaEngine` mit
  Journal-Tabellen (`TX_SAGA`, `TX_SAGA_STEP`) und Write-Ahead (`IN_FLIGHT`),
  `recover()` über alle Saga-Typen mit `FOR UPDATE SKIP LOCKED`.
- **Produktion**: Oracle UCP (`UcpConnectionPool`, Release-Leak geschlossen), beide Pools
  rollen vor `setAutoCommit(true)` zurück; `SimpleConnectionPool` mit Borrow-Validierung,
  idempotentem Release und Borrow-Timeout; JMX-Monitoring (`TxMonitoring`),
  externalisierte Konfiguration (`LmgConfig` inkl. `lmg.benutzer`-Kaskade — Datei wird
  jetzt genutzt), revisionssicheres Audit-Log (`TxAuditLog`).
- **Fachschicht Leistungsmanagement (SDN.sozial LMG)**: SGB-XII-Bedarfsberechnung
  vollständig (RBS 1–6, Mehrbedarfe § 30 Abs. 1/2/3/7 mit Obergrenze Abs. 6 und
  stufenabhängigem Warmwasser-Satz, KdU-Dreiteilung mit § 42a-Angemessenheitsprüfung
  und konfigurierbarem Warmwasser-Abzug, Absetzbeträge § 82 Abs. 2, Erwerbsfreibetrag
  § 82 Abs. 3, Vermögensanrechnung § 90 mit Schonvermögen), Zahlungs-Workflow
  (Idempotenz inkl. Recheck unter Sperre, `SELECT … FOR UPDATE`, Budget-Prüfung,
  Zahlung immer `ANGELEGT` — Vier-Augen serverseitig erzwungen), optimistisches Locking
  über VERSION-Spalten + Trigger, JSON-API und Widget-Oberfläche (HTML-escaped, CSP).
- **Betrieb & Sicherheit**: Anmeldung mit Rollen (admin/sachbearbeiter, serverseitige
  Berechtigungsprüfung; Admin-Sonderrolle über Rolle statt Kennung), Sitzungs-Idle-Timeout
  und Anmelde-Drosselung, Token nur aus dem Header (Ausnahme Druck-Link), Body-Limit,
  JSON-Tiefenlimit, Neuanlage, Kassenlauf (übergibt exakt die gesperrte Menge), Storno
  mit Zahlungssperre + guarded Statuswechsel und Restbudget-Rückbuchung, Wiederanlauf
  beim Serverstart über alle Saga-Typen.
- **SEPA-Bankverbindung & Vier-Augen-Prinzip**: IBAN, BIC, Kontoinhaber mit ISO 13616
  Modulo-97-Prüfziffernvalidierung (`SepaValidator`), Vier-Augen-Freigabe-Workflow
  (`ANGELEGT` → `BEREIT` → `UEBERGEBEN`) mit Rechnerischer Feststellung und Abwehr
  von Selbstfreigaben; Saga-Zahlungen gleichfalls `ANGELEGT` (keine Vier-Augen-Umgehung).
- **Bescheidwesen (§ 44 SGB XII; Bekanntgabe § 37 SGB X)**: Tabelle `BESCHEID`,
  `BescheidDao`, Bewilligungsbescheid-Erteilung im Service (Zeitraum-Validierung
  YYYY-MM, von ≤ bis) und Druck-Generator (`BescheidGenerator`) mit amtlichem Layout,
  Verfügungssatz, Bedarfsaufschlüsselung mit KdU-Vorbehalt, fallbezogener § 35-Begründung,
  konfigurierbarer vollständiger § 36-Rechtsbehelfsbelehrung und `@media print`-Stylesheet.
- **End-to-End-Saga (`AntragsSagaProzess`)**: 4-stufiger Geschäftsvorgang
  (Vorgang erfassen → Bedarfsprüfung → Bescheid erteilen → Erstauszahlung) mit
  idempotenter Rückwärtskompensation (Budget-Rückbuchung, Korrelation über VORGANGS_NR,
  Historisierung statt Löschung).
- **Qualitätssicherung**: Unit-Tests ohne DB grün (`mvn test`; DB-abhängige Tests
  `EchteDbVerifikationTest` + `DeadlockRetryEchtTest` laufen automatisch mit gesetzter
  `TX_URL`); neue Tests: `TaskControllerTest`, `SagaEngineTest`, erweiterte
  `SimpleConnectionPoolTest`/`LeistungsberechnungTest` (Warmwasser-Stufen 4–6,
  Kinder-/Schwangeren-Mehrbedarf, Obergrenze, Absetzbeträge, Vermögen).
