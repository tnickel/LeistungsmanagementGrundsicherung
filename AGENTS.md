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

## Stand (Stand: 2026-09-24, Version 0.2.0)

Kernbibliothek fertig und getestet: TxManager/TxContext/TxOptions/RetryPolicy,
TaskController (begrenzte Parallelität, Timeout-Wachhund, Statistik), Saga-Engine
mit Journal-Tabellen (`TX_SAGA`, `TX_SAGA_STEP`), Demo. Fachschicht **Leistungs-
management Grundsicherung (SDN.sozial LMG)**: SGB-XII-Berechnung, Zahlungs-Workflow
(Idempotenz über Vorgangsnummer, `SELECT … FOR UPDATE`, Budget-Prüfung, Journal),
optimistisches Locking über VERSION-Spalten + Trigger, JSON-API und Widget-
Oberfläche mit Adminbereich (Systemdiagnose: DB-Check, „alles gestartet"-Prüfung,
Saga-Wiederanlauf) und Funktionsübersicht. **Verifiziert gegen echte Oracle (23ai
Free in Docker, Etappe 1 weitgehend abgehakt)**; 35 Tests grün (davon 5 echten-DB-
Tests, die nur mit gesetztem `TX_URL` laufen). Dabei gefundene und behobene Fehler:
Permit-Leak in `SimpleConnectionPool`, Fachfehler-Wicklung in `TaskHandle.result()`
(FachlicheException erbt jetzt von TxException, damit Typ/Code die
Transaktionsgrenze überleben), offenes Datei-Handle in der Statischauslieferung.
Nächste Schritte: siehe `doc/roadmap.md` (Deadlock-Retry real, Pool-Tausch auf UCP,
Monitoring, Berechtigungsprüfung).
