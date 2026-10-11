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
- `doc/leistungsmanagement.md` — Fachschicht Leistungsmanagement (SDN.Leistung LMG)
- `doc/rechtsgrundlagen.md` — Rechtsquellen-Verzeichnis mit Herkunftsnachweisen und
  Verifikationsprotokoll (maßgeblich für Fach-Reviews; Stand 2026-10-10)
- `doc/regressionstests.md` — die zehn Testfälle der Szenario-Regression mit Akteuren,
  Abläufen und erwartetem Datenbank-Endzustand (Stand 2026-10-11)
- `testsweet/` — Mitarbeiter-Simulator (keine Unit-Tests): N Sachbearbeiter arbeiten
  parallel einen Auftragstopf ab, je Lauf in **Modus 1 (Fachschicht)** oder
  **Modus 2 (Weboberfläche/Selenium)** und je **Testfallkategorie** (Positivfälle,
  Negativfälle = erwartete Fachfehler mit unverändertem DB-Endzustand, Lasttest =
  schlanke Massendurchläufe mit gekürztem Gesamtreport, Stresstest = zwei Wellen über
  dieselben User mit Phasenbarriere, nur Fachschicht); GUI auf Port 8095, Logfiles
  je Lauf, Testdokumentation als PDF (lesbar) und Markdown (KI-nachprüfbar)
  (Details: testsweet/README.md)
- `doc/roadmap.md` — aktueller Stand und geplante Etappen

## Namenskonventionen für die Fachschicht (wichtig)

- Die Oberfläche und das Fachmodul tragen die Bezeichnung **SDN.Leistung** (Leistungs-
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
- **Entwickler-/Test-Datenbank ist der Docker-Container `lmg-oracle`** (Oracle Free,
  `jdbc:oracle:thin:@//localhost:1521/FREEPDB1`, tx_demo/tx_demo; `start.bat` startet
  ihn bei Bedarf). Die VirtualBox-VM mit Oracle 19c EE (`localhost:1921/o19c1p1`)
  bleibt als Zweitumgebung für EE-spezifische Verifikation — Testläufe sind dort
  rund 30–90× langsamer (Kaltstart des Verbindungsaufbaus in der VM).
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

## Stand (Stand: 2026-10-10, Version 0.5.4)

Kernbibliothek und Fachschicht vollständig umgesetzt, umfassendem Codereview unterzogen
und auf echter Oracle 19c Enterprise Edition (VirtualBox, Port 1921/o19c1p1) sowie
23ai Free verifiziert:

- **v0.5.4 — Rechtsquellen & revisionssicherer Bescheid-Druck**: neues maßgebliches
  Rechtsquellen-Verzeichnis `doc/rechtsgrundlagen.md` (alle Normen mit Herkunfts-
  nachweisen, amtlich verifiziert gegen gesetze-im-internet.de am 2026-10-10; u. a.
  klargestellt: § 37 Abs. 2 SGB X Vier-Tage-Fiktion, 100.000-EUR-Grenze aus
  § 94 Abs. 1a statt § 43, Schonvermögen aus der DVO zu § 90 Abs. 2 Nr. 9).
  Fachreview-Umsetzung: Belehrung § 73 Abs. 1 SGG, Schwangeren-Mehrbedarf ab der
  13. SSW, Bewilligungszeitraum serverseitig auf zwölf Monate begrenzt (§ 44 Abs. 3),
  KdU-Vorbehalt zitiert § 35 Abs. 1 und 7 mit offenem Fristverzicht. Bescheid-Druck
  zeigt ausschließlich den versionierten JSON-Snapshot aus `BESCHEID.BERECHNUNG`
  (Erteilungszeitpunkt; Fallback Neuberechnung mit Hinweiskasten); neue Klasse
  `BerechnungsSnapshot`. 126 Unit-Tests grün; DB-Lauf + Schema (`ALTER TABLE BESCHEID
  ADD (BERECHNUNG CLOB)`) nachziehen, sobald die Oracle-VM wieder läuft.
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
- **Fachschicht Leistungsmanagement (SDN.Leistung LMG)**: SGB-XII-Bedarfsberechnung
  vollständig (RBS 1–6, Mehrbedarfe § 30 Abs. 1/2/3/7 mit Obergrenze Abs. 6 und
  stufenabhängigem Warmwasser-Satz, KdU-Dreiteilung mit § 35-Angemessenheitsgrenze je Bedarfsgemeinschaft
  und Warmwasser-Abzug nur bei zentraler Erzeugung, Absetzbeträge § 82 Abs. 2, Erwerbsfreibetrag
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
- **Bescheidwesen (§ 44 SGB XII; Datum des Bescheids)**: Tabelle `BESCHEID`,
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
- **Szenario-Regression (`SzenarioRegressionTest`, läuft mit `TX_URL`; Testfälle
  dokumentiert in [doc/regressionstests.md](doc/regressionstests.md))**: zehn
  Nutzer-Szenarien mit mehreren Sachbearbeitern (Erfassung, Vier-Augen-Freigabe,
  Doppelklick/Idempotenz, Storno + korrigierte Neuauszahlung im selben Monat, Vermögens-,
  Erwerbs- und Mehrbedarfsfälle, Bescheid mit Snapshot, Konkurrenz beim Masken speichern,
  Kassenlauf, Historisierung). Verifiziert wird je Szenario der persistierte Endzustand
  per SQL direkt auf den Tabellen (Beträge, Status, Sachbearbeiter-Zuordnungen,
  Journal-Chronologie); Datenhygiene über Aktenzeichen-Präfix `BG-REG-`.
