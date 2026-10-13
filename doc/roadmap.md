# Roadmap: Transaktionssteuerung

*Stand: 2026-10-13, Version 0.5.8*

## testsweet: Review und echte Modell-Nachprüfung (2026-10-13)

- [x] Simulator-Prüfungen nachgebessert: erwartete Fachfehler, Mengen-/Ganzzahlvergleiche,
      wirksame Szenariovariation, Status-/Budgetnachweise und auf Testdaten begrenzter Kassenlauf.
- [x] Webläufe mit einem Mitarbeiter, gemeinsamem Web-/DB-Ziel, angewendeter Denkzeit,
      geprüften Antwortdialogen und geordnetem Worker-Abbruch; eindeutige Laufverzeichnisse.
- [x] Echte GLM-5.3-Nachprüfung über konfigurierbare API; Zahnrad für Modell, Endpunkt,
      lokalen Zugang, Zeit-/Antwortlimit, 2–1000 ausgewählte Fälle und 1–10 Fälle je Batch.
      Auswahl aus erfolgreichen Positiv-/Negativberichten einschließlich kuratierter Fälle.
- [x] Vollständige benannte Dokumenttexte je Anfrage, Manifest und Quell-/Request-/Response-
      Snapshots mit SHA-256. Dateiübergabe und fachliches `PASS` werden getrennt ausgewiesen;
      ungültige/veraltete Belege erhalten kein grünes Gesamturteil.
- [x] Neuer KI-Reportanhang ohne Änderung des originalen hashgebundenen Quellberichts.
      Historische Freitext-/Python-Protokolle gelten nicht als Modellnachweis.
- [x] Verifiziert: **55/55 testsweet-Tests**, zusätzlich **6 echte GLM-5.3-Fälle in 2 Batches**
      mit `PASS` und bestätigter Dateiübergabe. Dies ist eine dokumentierte Stichprobe,
      keine vollständige Regelabdeckung; die frühere Python-Nachrechnung 100/108 bleibt getrennt.

Bedienung und Nachweisformat: `testsweet/README.md`,
`doc/testzertifikat-werkzeug/README.md`. Ausgangsreview:
`doc/codereview/testsweet-codereview-2026-10-13.md`.

## Nachprüfung der Codereview-Fixes v0.5.8

- [x] Monatspläne für Bescheide mit Snapshot-Version 2 (Vermögen, Schwangerschaftsende, bekannte Satzwechsel).
- [x] Einheitliche Monatsberechnung und Aktivitäts-/Anspruchsprüfung in Service und Saga.
- [x] BG-Sperren für Falländerung/-anlage und erneute Aktivitätsprüfung vor Kassenübergabe.
- [x] Parameterbindung und Einsichtsprotokoll für Auszahlungs-Replays; transaktionale Journal-Deduplizierung.
- [x] Druckantworten mit `no-store`, Vorschau mit aktuell gültigem Satzstand.
- [x] testsweet: eigene Browserprofile, sichere Wiederaufnahme, globale Prüffehler im Laufresultat.

Prüfbericht mit Reproduktionsbefehlen und Gegenproben:
`doc/codereview/codereview-nachbesserungsbericht-2026-10-12.md`.

## Gegenprüfung v0.5.2 → Nacharbeiten (v0.5.3)

Die Fachrechts-Korrekturen von v0.5.2 (§ 30 Abs. 3 auf RBS-1-Basis, Abs. 6 Deckel nur
für Abs. 1–5, Schonvermögen 10.000/500 EUR nach BarbetragsVO, §§ 84/87/88-SGG-Belehrung)
wurden Norm für Norm gegen die amtlichen Texte verifiziert und bestätigt. Nacharbeiten:

- [x] **Bekanntgabe-Fiktion korrigiert** — § 37 Abs. 2 SGB X: schriftliche Bescheide gelten
      bei Postübermittlung im Inland am **vierten** Tag nach Aufgabe zur Post als bekannt
      gegeben (nicht am dritten); Bescheidtext, Javadoc korrigiert (falsche Belehrung
      verlängerte die Klagefrist auf ein Jahr, § 66 Abs. 2 SGG)
- [x] **Monatsregel zusätzlich auf Datenbankebene** — function-based Unique-Index
      UQ_ZAHLUNG_BG_MONAT_AKTIV (BG+Monat eindeutig für nicht stornierte Zahlungen;
      STORNIERT-Zeilen via CASE zu NULL ausgenommen) als letzte Verteidigungslinie für
      künftige Schreibpfade; Service-Prüfung unter BG-Sperre bleibt die erste
- [x] ALARM-Wiederholung bei erneuter Kompensation im Code dokumentiert
      (bewusst ohne Dedup — Fachwirkung bleibt idempotent)
- [x] Veraltete Warmwasser-2,3 %-Angaben korrigiert: Diagramm-Skript,
      Handbuch-Quelltext; **Benutzerhandbuch DOCX/PDF und alle Diagramme neu
      generiert** (Version 0.5.3, stufenabhängige Warmwasser-Sätze)

## Erreicht (v0.2.0 – v0.3.0)

- [x] Kern: `TxManager`, `TxContext`, `TxOptions` (Begin/Commit/Rollback, Deadline, Savepoints)
- [x] `RetryPolicy` — ORA-00054/00060/08177 → Wiederholversuch mit Backoff
- [x] `TaskController` — begrenzte Parallelität, Timeout-Wachhund, `TaskStats`
- [x] Saga-Engine + Journal (`TX_SAGA`, `TX_SAGA_STEP`) inkl. `recover()` (Wiederanlauf)
- [x] Oracle-DDL, Demo (`DemoMain`: Erfolgsfall, Fehlerfall mit Kompensation, parallele Tasks)
- [x] **Fachschicht Leistungsmanagement (SDN.Leistung LMG)** — siehe [leistungsmanagement.md](leistungsmanagement.md):
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

- [x] **§ 30 Abs. 2 SGB XII** — Schwangeren-Mehrbedarf (17 % ab der 13. SSW,
      „nach der zwölften Schwangerschaftswoche"): Datenmodell,
      Berechnung, Maske, Tests
- [x] **§ 30 Abs. 3 SGB XII** — Alleinerziehenden-Mehrbedarf (36 % / 12 % je Kind,
      Deckel 60 % RBS 1, Basis RBS 1): Kinder unter 7 / unter 16 / minderjährig insgesamt
- [x] **§ 30 Abs. 6 SGB XII** — Obergrenze: Mehrbedarfe nach Abs. 1–5 übersteigen nie den Regelbedarf (Abs. 7 liegt außerhalb der Kappung)
- [x] **§ 30 Abs. 7 SGB XII korrigiert** — Warmwasser-Mehrbedarf stufenabhängig
      (2,3/2,3/–/1,4/1,2/0,8 % statt pauschal 2,3 %), mit Tests für Stufen 4–6
- [x] **§ 82 Abs. 2 SGB XII** — Absetzbeträge (Steuern, Sozialversicherung,
      Versicherungen, Erwerbsaufwendungen) vor dem Freibetrag des § 82 Abs. 3
- [x] **§ 42a i.V.m. § 35 SGB XII** — KdU-Angemessenheitsprüfung mit konfigurierbarer
      Grenze (Übernahme unter Vorbehalt, Bescheid-Kennzeichnung) und konfigurierbarem
      Warmwasser-Abzug von den Heizkosten
- [x] **§ 43 Abs. 1, § 90 SGB XII** — Vermögensanrechnung: Vermögen je BG,
      Schonvermögen 10.000 € je Erwachsenem + 500 € je unterhaltener Person
      (§ 90 Abs. 2 Nr. 9 i.V.m. § 1 BarbetragsVO), mindert Zahlbetrag
- [x] **§ 44 SGB XII** — Bewilligungszeitraum-Validierung (YYYY-MM, von ≤ bis),
      STATUS_AKTIV-Prüfung; Normzitationen im Bescheid korrigiert
      (Bewilligung § 44 SGB XII; Bescheiddatum statt Bekanntgabe)
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

## Szenario-Regression (2026-10-11)

- [x] **Zehn Nutzer-Szenarien mit mehreren Sachbearbeitern** als neue DB-Suite
      `SzenarioRegressionTest` (läuft mit `TX_URL`): verschiedene Sachbearbeiter
      erfassen unterschiedliche SozialFälle (RBS-Kombinationen, Mehrbedarfe §§ 30/82/90,
      Vermögen, Erwerb), Vier-Augen-Freigabe inkl. abgewehrter Selbstfreigabe,
      Doppelklick-Idempotenz, Storno + korrigierte Neuauszahlung im selben Monat,
      Bescheid mit Berechnungs-Snapshot, Konkurrenz-Abwehr beim Masken speichern,
      Kassenlauf, Historisierung. Verifiziert wird je Szenario der persistierte
      Endzustand per SQL direkt auf den Tabellen (Beträge, Status, Sachbearbeiter-
      Zuordnungen, Journal-Chronologie). Testfälle im Detail:
      [regressionstests.md](regressionstests.md)
- [x] Gegen VirtualBox-Oracle verifiziert: Suite 10/10 grün; Gesamtlauf 136 Tests,
      0 Fehler; keine Test-Rückstände (Cleanup-Präfix `BG-REG-`)

## Mitarbeiter-Simulator „testsweet" (2026-10-11, Modus 1 & Visualisierung)

- [x] **Eigenständiges Werkzeug `testsweet/`** (keine Unit-Tests, eigenes Maven-Projekt auf dem
      Hauptartefakt): N simulierte Sachbearbeiter (Threads mit Kürzel und Denkzeit) ziehen Aufträge
      atomar aus einem Pool und spielen 10 Szenario-Arten über die Fachschicht (SozialLeistungsService
      → TxManager → Oracle): Standardrente mit Vier-Augen-Freigabe, Paar, § 30-Mehrbedarfe, Familie,
      § 82-Erwerbsfälle, § 90-Vermögensfälle inkl. erwartetem KEIN_ANSPRUCH, Doppelklick-Idempotenz,
      Storno + korrigierte Neuauszahlung im selben Monat, Konkurrenz mit erwartetem KONKURRENZ-Fehler,
      Kassenlauf parallel zu Freigaben
- [x] **Erwartungshaltung in jedem Szenario** (vorab mit derselben reinen `Leistungsberechnung`
      berechnet), Verifikation nach Laufende per SQL direkt auf den Tabellen; strukturierte Logfiles
      je Lauf (simulation.log, je Auftrag, je Mitarbeiter, db-verifikation.log, fehler.log,
      zusammenfassung.txt) für die manuelle Auswertung
- [x] **Lokale GUI & Web-Dashboard** (Port 8095): Modernisiertes Behörden-Dashboard mit Live-Mitarbeiterkarten,
      pulsierenden Status-Indikatoren, interaktiver Diagramm-Showcase-Galerie (4 Fachdiagramme),
      Sofort-Volltextsuche, Statusfiltern (*Alle*, *Nur Verifiziert*, *Auffälligkeiten*) und 1-Klick-Presets
      (15 / 60 / 120 Fälle)
- [x] **Revisionssichere Testdokumentation & Pure Java PDF-Generator (`SweetPdf`)**:
      Vollständige Eigenentwicklung ohne Fremdbibliotheken mit nativer Einbettung von PDF-1.4-XObjects
      (`/Filter /DCTDecode`), repräsentativem Deckblatt mit Titelbild (`titelbild.jpg`), 4 hochauflösenden
      Fachflussdiagrammen (Simulator-Zyklus, SGB-XII-Berechnungskaskade, Vier-Augen-Workflow, Transaktionsarchitektur),
      paginiertem Inhaltsverzeichnis und seitenbündigen Einzelfallnachweisen (Schritt-für-Schritt-Rechenweg +
      SQL-Erwartung/Ist-Vergleich + Protokollauszug) ohne Waisenüberläufe; zusätzlich schlanke maschinenlesbare
      Markdown-Fassung (`testdokumentation.md`) mit DB-IDs und copy-paste-fähigen SQLs für KI-Audits
- [x] **Automatisiertes Grafiksystem & CLI-Batchlauf**: `python testsweet/generate_diagrams.py` generiert
      die Vektor-Diagramme (200 dpi); `testsweet\start.bat run <workers> <cases> [seed] [denkzeitMs]` ermöglicht
      den autonomen Headless-Batchlauf für CI/CD-Pipelines
- [x] Gegen Docker-Oracle verifiziert: 10 Mitarbeiter / 60 Aufträge, 60/60 verifiziert in ~1,5 s;
      Batch-Lauf 5/10 verifiziert in < 1 s (100 % Erfolgsquote)
- [x] **Modus 2: Weboberfläche per Selenium bedienen** (Selenium WebDriver steuert parallele Chrome-Fenster im Bildschirmraster; bedient SDN.Leistung interaktiv mit Screenshot-Erfassung und vollständiger SQL-Verifikation; in GUI und CLI `testsweet\start.bat web` integriert)
- [x] **Testfallkategorien Positiv/Negativ/Lasttest** (2026-10-11 abends): GUI-Reiter
      zwischen Moduswahl und Laufkonfiguration; Negativfälle als erwartete Fachfehler
      (Fachschicht 9 Arten, Web 7 Arten inkl. Fehlerdialog-Beweis) mit Prüfung des
      unveränderten DB-Endzustands; Lasttest mit schlanken Massendurchläufen und
      gekürztem Gesamtreport (`lastreport.pdf`/`.md`) statt Einzelfall-Seiten.
      Aktenzeichen-Präfixe je Kategorie: SWEET-/SWEET-W- (Positiv), SWEET-N-/SWEET-NW-
      (Negativ), SWEET-L-/SWEET-LW- (Last). Verifiziert: Fachschicht 60/60 Positiv,
      18/18 Negativ (zwei Seeds, alle 9 Arten), 600/600 Last in 3 s (12.000 Aufträge/min,
      Aggregate-Report); Web 6/6 Negativ, 4/4 Last, 2/2 Positiv. Dazu harte Laufsperre
      gegen doppelte Starts (AtomicBoolean vor Thread-Start, API antwortet 409).
- [x] **Stresstest-Kategorie (2026-10-11 spät, 4. GUI-Reiter)**: zwei Wellen über
      dieselben User — Welle 1 legt N User an (Vorgang + SozialFall), Welle 2 findet
      sie per Aktenzeichen wieder (neue Service-Methode
      `findeBedarfsgemeinschaftNachAktenzeichen`), ändert die Miete (optimistisches
      Locking) und ordnet die Auszahlung an; Phasenbarriere im Simulator gibt Welle 2
      erst nach Abschluss von Welle 1 frei. Nur Fachschicht (API/GUI wehren Modus 2 ab);
      Report als gekürzter `lastreport`. Referenzlauf 20 MA / 2000 User × 2 =
      **4000/4000 verifiziert in 13 s** (18.461 Aufträge/min, max. 1,19 s Sperrwarte,
      0 Retries); unabhängige SQL-Endprüfung: 2000 Vorgänge aktiv, alle Fälle exakt
      Version 2, 2000 Zahlungen ANGELEGT für 2026-10, Summe 2.266.017,00 EUR
      deckungsgleich, 8000 Journaleinträge (4 je User), 0 negative Budgets.
      Hauptprojekt-Testsuite danach 136/136 grün.

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
