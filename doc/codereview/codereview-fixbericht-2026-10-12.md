# Codereview-Fixbericht — Umsetzung der Befunde aus codereview-bericht-2026-10-12.md

*Datum: 2026-10-12 · Version 0.5.4 → **0.5.5** · Grundlage: 75 Befunde CR-001…CR-075 aus
[codereview-bericht-2026-10-12.md](codereview-bericht-2026-10-12.md) · Review-only-Phase
abgeschlossen, alle Fixes verifiziert durch Tests (siehe Ende).*

---

## 1. Ergebnis

| Kategorie | Befunde | Behoben | Verbleibend |
|---|---|---|---|
| BLOCKER | 3 | **3** | — |
| KRITISCH | 9 | **9** | — (CR-015/016/035 zählten als MAJOR/Doku) |
| MAJOR | 24 | **19** | CR-015, CR-016, CR-035, CR-036 (teilw.), L-12/L-16-Tests |
| MINOR | 23 | **19** | CR-047, CR-053, Handbuch-PDF-Regeneration |
| INFO | 15 | bewertet, keine Aktion nötig | — |

**Verifikation:** Hauptprojekt **195/195 Tests grün** (vorher 136; +59 neue, inkl. 4 neuer
DB-Regressionstests gegen Docker-Oracle). testsweet: eigene Unit-Tests grün, alle vier
Batchkategorien verifiziert (POSITIV 24/24, NEGATIV 12/12, LAST 120/120, STRESS 40/40).
MD-Reports der Neuläufe nachweislich **passwortfrei** (alte Läufe: 10 Treffer).

## 2. Behobene Befunde (mit Umsetzungsort)

### BLOCKER
- **CR-001 Sozialdaten im Audit-Log** — `TxAuditLog.recordSagaStart` protokolliert nur
  Typ + Payload-Länge (kein Inhalt mehr); dauerhaft offener Writer unter `FILE_LOCK`
  statt Writer-Churn (CR-021); **Rotation bei 10 MB** auf `.1` (CR-012); Ringpuffer-Trim
  über Zähler statt `size()`; `clearForTests` paket-privat (CR-048); `closeFile()` für den
  Shutdown-Hook. Test: `TxAuditLogRedaktionTest` (IBAN/Name/Einkommen weder in Ring noch Datei; Trim ≤ 1000).
- **CR-002 Passwörter in der testsweet-Testdokumentation** — `WebMitarbeiter` spiegelt
  Passwörter nicht mehr in `auftrag.kontext`; `MdBericht.kontextZeile` filtert
  „passwort“-Schlüssel (case-insensitive). Test: `MdBerichtFilterTest` + Beweis über
  Neuläufe (grep leer).
- **CR-003 Saga rechnet anders als der Service-Weg** — Schritte 2–4 lesen SozialFälle,
  Vermögen (neues Payload-Feld `vermoegen`, wird in Schritt 1 persistiert) und
  konfigurierte KdU-Parameter (`AntragsSagaProzess.setzeKduParameter`, von
  `LmgServerMain` injiziert) **aus der Datenbank**; Bescheidnummer-Format an den
  Service-Weg angeglichen; Snapshot mit echter FALL-ID; Zahlung mit korrekter FALL_ID
  bei Einpersonen-BG; Budget-/Monats-Fachfehler statt IllegalStateException/ORA (CR-043).
  Test: `CodereviewRegressionDbTest.sagaBeruecksichtigtVermoegen` (Bescheid UND Zahlung
  = 363,00 bei Vermögen 10.500 — vorher 863,00).

### KRITISCH
- **CR-004 Wildcard-Bindung + Demo-Konten** — `LmgServer` bindet Default **Loopback**
  (neuer InetAddress-Konstruktor); `lmg.host`/`LMG_HOST` konfigurierbar; Demo-Konten
  werden bei Nicht-Loopback-Bindung hart verweigert (`LmgServerMain`).
- **CR-005 ISO-8859-1-Properties** — `LmgConfig` liest Konfigurationsdateien als
  **UTF-8-Reader**; Umlaute in Behördenanschrift/Belehrung bleiben intakt.
- **CR-006 JSON-Exponentenbombe** — Parser begrenzt Exponent (±999) und Ziffern (64) →
  IllegalArgumentException (→ 400). Test: `JsonGrenzenTest`.
- **CR-007 Saga ohne 12-Monate-Prüfung** — `SozialLeistungsService.pruefeBewilligungszeitraum`
  ist public und wird in `validierePayload` + `bescheidErteilen` erzwungen. Test:
  `AntragsSagaValidationTest` (12 ok / 13 abgewiesen / Jahresgrenze ok).
- **CR-008 Schwangeren-Mehrbedarf ohne Ende** — neues Feld `ENTBINDUNGSMONAT`
  (SOZIAL_FALL, Modell, DAO, UI-Maske, Saga-Lesung; DDL-CHECK YYYY-MM): Auszahlung und
  Bescheid für Monate nach dem Entbindungsmonat werden abgewiesen. Test:
  `CodereviewRegressionDbTest.entbindungsmonatBeendetSchwangerenMehrbedarf`.
- **CR-009 Historisierte BG bleibt zahlbar** — Guards in `freigebenZahlung` (unter
  Zahlungs- UND BG-Sperre), `aktualisiereSozialFall`, Reaktivierungs-Verbot in beiden
  `aktualisiereBedarfsgemeinschaft`-Wegen, Kassenlauf-Select filtert auf aktive BG.
  Tests: `CodereviewRegressionDbTest.historisierteBgBleibtZahlbarGesperrt` +
  `.kassenlaufUeberspringtHistorisierte`.
- **CR-010 SweetServer HTTP 200** — `sendeFehler` setzt den übergebenen Statuscode.
- **CR-011 Zirkelschluss** — `GoldWerteTest`: 9 handgerechnete Sollbeträge aus
  doc/regressionstests.md (unabhängige Quelle) gegen `Leistungsberechnung`; Kennzeichnung
  im MD-Berichtskopf von testsweet.
- **CR-012 Audit-Rotation** — siehe CR-001 (10 MB, ein Vorgänger).

### MAJOR (Auswahl)
- **CR-013** `wasNull()` direkt nach `getLong` (ZahlungDao/JournalDao) — NULL bleibt NULL.
- **CR-014** `erteileBescheid` sperrt die BG (`lockForUpdate`) — Snapshot einfriert einen atomar existierten Stand.
- **CR-017** Shutdown-Hook in `LmgServerMain` (Server → Tasks → Pool → Audit-Datei); Latch statt Selbst-Join.
- **CR-018** Javadoc-Warnung `TxContext.close()` + `abandonedConnectionTimeout(300)` im UCP. Test: `TxManagerTest.closeOhneFinishLaesstDenPoolLeerlaufen`.
- **CR-019** neue `TxManagerTest` (7 Tests: Retry-Schleife, maxAttempts, Fachfehler nie wiederholt, Error→Rollback+Release, manuelle API, finish/close-Verhalten).
- **CR-020** JDBC-Timeouts: beide Pools setzen `oracle.net.CONNECT_TIMEOUT`/`oracle.jdbc.ReadTimeout` (konfigurierbar `lmg.db.*TimeoutMs`, Defaults 15 s/120 s).
- **CR-021/012** persistenter Audit-Writer (siehe CR-001).
- **CR-022** UCP `setConnectionWaitTimeout(30s)`, Kommentar auf Konfiguriertes reduziert, Singleton-Nebenwirkung im `close()`-Javadoc.
- **CR-023/024** `datumWert` (DateTimeParseException) und `limit` (Bereich 1–500) → 400 statt 500; IAE am Task-Rand wird Fachfehler. Tests: `LmgServerHttpTest`.
- **CR-025** SweetServer-Traversal: `..`-Pfade abgewiesen, Fallbacks normalisiert + Basis-Prüfung.
- **CR-026** Fehlversuchs-Map gedeckelt (1024) mit Aufräumlogik; Uhr injizierbar. Tests: `BenutzerverwaltungZeitTest`.
- **CR-027** Einmal-Druck-Token (`POST /api/drucktoken`, 60 s, verbraucht sich) statt Sitzungs-Token in der URL; Frontend `sessionStorage`. Test: `LmgServerHttpTest.druckTokenGiltGenauEinmal…`.
- **CR-028/029/030** testsweet: STRESS+WEB-Guard als erste Anweisung + CLI-Abweisung + Meldung; `log.close()` in finally überall; Treiber-Neustart (1×) bei totem Renderer.
- **CR-031/032/033** testsweet: FREIGEBEN_VON=Kollege-, DOPPELKICK-Journal-, FAMILIE-Wiedervorlage-Erwartungen; globale Checks „keine UEBERGEBEN ohne FREIGEGEBEN_VON“ und „keine STORNIERT zu FEHLGESCHLAGEN“; LastBericht-Aussage entschärft.
- **CR-034** SweetLog asynchron (Queue + Daemon-Writer, Batch/100-ms-Takt) — Lastmesswerte nicht mehr logverzerrt.
- **CR-036** Handbuch-GENERATOR-Quellen korrigiert (Vier-Augen immer aktiv, Admin-Rolle statt -kennung, behauptete Budget-Nachprüfung bei der Freigabe entfernt); toter Parameter `vierAugenErforderlich` nicht mehr ausgelesen.
- **CR-041** BescheidDao/Service: Datum einmalig ermitteln (Nr/Datum konsistent über Mitternacht).
- **CR-042** tote DAO-Pfade entfernt (`uebergebeBereite`, `existiertVorgangsNr`, `findeBescheidNr`, `aufheben`).
- **CR-044/045** DDL: Format-CHECKs (ZAHLUNGSMONAT, BEWILLIGUNG_VON/BIS, ENTBINDUNGSMONAT) + FK-Index `IX_TX_SAGA_STEP_SAGA`; Migration auf Docker-Oracle ausgeführt.

### MINOR (Auswahl)
CR-037 Kappungssatz § 30 Abs. 6 im Bescheid beschriftet; CR-038 elektronische Form in
der Widerspruchsbelehrung (§ 84 SGG i.V.m. § 65a); CR-039 Rechtsgrundlagen § 73 Abs. 1
korrigiert; CR-040 Rechtsbehelf-Modus wird validiert; CR-046 Pool-Erschöpfungs-Warnung
(rate-begrenzt); CR-049 JMX-null-Meldung; CR-050 Wiederanlauf vor `server.start()`;
CR-051 405 + erlaubte Methoden statt 404; CR-052 `Cache-Control: no-store` + CSP im
404; CR-054 Storno-Button-UI = Serverregel; CR-055 Latch statt Busy-Wait; CR-056
nanoTime-Laufdauern; CR-057 Fenster-Aufräumen auf eigene Prozesskette begrenzt;
CR-058 Konten-Warnung + `SWEET_BASE_URL`; CR-059 AZ-Duplikat-Schutz; CR-060 CP1252-
Ersetzungen (→, –, … usw.); S-44-Restfall NULL-Handhabung im Verifikator; S-45 toter
Code entfernt (Latch, GANZZAHL_MIN, Altsignaturen).

## 3. Neue Tests (insgesamt +59, Bestand 136 → 195)

| Testklasse | deckt |
|---|---|
| `lmg.calc.GoldWerteTest` (9) | CR-011 — handgerechnete Werte aus regressionstests.md |
| `tx.TxManagerTest` (7) + `tx.FakePool` | CR-018/019 — Retry, Error, manuelle API, Pool-Leak |
| `lmg.web.LmgServerHttpTest` (15) | CR-004/014/023/024/027/051/052 — erste HTTP-Integration der Webschicht |
| `lmg.web.JsonGrenzenTest` (7) | CR-006/028 — Exponenten, Tiefe, Escapes |
| `lmg.web.BenutzerverwaltungZeitTest` (6) | CR-026/027 — Drosselung, Idle, Druck-Token |
| `tx.audit.TxAuditLogRedaktionTest` (3) | CR-001 — Sozialdaten-Freiheit, Trim |
| `lmg.saga.AntragsSagaValidationTest` (8) | CR-007/043 — 12 Monate, Längen, Beträge |
| `lmg.db.CodereviewRegressionDbTest` (4, TX_URL) | CR-003/008/009 — Saga-Vermögen, Entbindungsmonat, Historisierung |
| testsweet `MdBerichtFilterTest` (2) | CR-002 — Passwortfilter |
| Erweiterte Erwartungen in testsweet-Szenarien | CR-031/032/033 — Vier-Augen-/Journal-/Statusprüfungen |

## 4. Bewusst nicht umgesetzt (mit Begründung)

- **CR-015 Verbrauchsfortschrieb Vermögen / CR-016 versionierter Satzkatalog** —
  Fach-Design-Entscheidungen; bis zur Entscheidung als dokumentierte Vereinfachung
  versehen (rechtsgrundlagen.md Abschnitt 4 mit Folgeschäden präzisiert: monatliche
  Voll-Anrechnung; keine Fortschreibung im laufenden Bewilligungszeitraum).
- **CR-035 Need-to-know-Filter** — organisatorische Regel dokumentiert (architektur.md
  Bekannte Grenzen); Rollenfilter wäre ein Fachprojekt.
- **CR-036 (Rest)** — Benutzerhandbuch-PDF/DOCX müssen aus den korrigierten Quellen neu
  generiert werden (`doc/handbuch-werkzeug`-Pipeline); Quellen sind fertig angepasst.
- **CR-047** Watchdog-/Retry-Budget-Semantik nur zu dokumentieren (MINOR) — offen.
- **CR-053** `esc()` um rein numerische `value=`-Interpolationen — latent, Zahlen sind
  serverseitig typisiert; offene Kleinigkeit.
- **Testlücken** (aus Bericht Abschnitt 4): L-12 paralleler recover, L-13 echter
  Prozess-Kill, L-16 Drei-Thread-Rennen, L-17–L-20 Störtests — empfohlene Folgearbeiten.

## 5. Verifikationsläufe (2026-10-12)

```text
mvn test (TX_URL gegen Docker-Oracle lmg-oracle)
  → Tests run: 202, Failures: 0, Errors: 0, Skipped: 1 — BUILD SUCCESS
mvn -f testsweet/pom.xml test → 2/2 grün
testsweet run 8/24  POSITIV → fehlgeschlagen 0, verifiziert 24,  Verifik.-fehler 0
testsweet run 4/12  NEGATIV → fehlgeschlagen 0, verifiziert 12,  Verifik.-fehler 0
testsweet run 10/120 LAST    → fehlgeschlagen 0, verifiziert 120, Verifik.-fehler 0
testsweet run 5/20* STRESS   → fehlgeschlagen 0, verifiziert 40,  Verifik.-fehler 0
Schema-Migration (ENTBINDUNGSMONAT, 4 CHECKs, FK-Index) auf FREEPDB1 ausgeführt.
grep -i passwort über neue MD-Reports: leer (alte Läufe: 10 Treffer).
```

## 6. Nachtrag (gleicher Tag, Abend): CR-047/CR-053, Testlücken, Handbuch

- **CR-047 behoben (Doku + festnagelnder Test):** Die Zeitlimit-Semantik ist jetzt
  explizit: Der Watchdog des TaskController misst die **Gesamtlaufzeit inklusive
  aller Retry-Versuche und Backoff-Pausen**, die TxContext-Deadline gilt **pro
  Versuch** — dokumentiert im TaskController- und TxOptions-Javadoc (mit
  Faustregel timeout ≥ maxAttempts × Einzellaufzeit). `TaskControllerWatchdogBudgetTest`
  nagelt beides fest: enge 250 ms + 200-ms-Backoff → Wachhund bricht ab
  (timedOut-Statistik); 5 s Budget → derselbe Konflikt committet im 2. Versuch.
- **CR-053 behoben:** Alle `${fall.…}`-Interpolationen der Fallmaske (12 value-,
  Titel, Versionsbadge, kdu-Titel) laufen jetzt konsequent durch `esc()` — ein
  künftiger Typwechsel eines Feldes von Zahl auf String kann keine XSS-Lücke mehr öffnen.
- **Testlücken geschlossen:**
  - **L-12** `SagaParallelRecoverTest`: zwei parallele recover-Läufe über ein
    Journal-Fake mit atomarem Claim (SKIP-LOCKED-Semantik) — jeder Schritt wird
    trotz Parallelität genau einmal kompensiert (4 Sagas × 3 Schritte = 12, nicht 24).
  - **L-13** `ProzessKillRecoveryDbTest` + `ProzessKillHelfer`: ECHTER harter
    Prozess-Kill — ein Kind-JVM beginnt eine Saga, schläft im IN_FLIGHT-Schritt und
    wird per destroyForcibly getötet (kein finally/Commit, wie ein Absturz); der
    Wiederanlauf historisiert den Vorgang (§ 67 SGB X) und setzt die Saga auf COMPENSATED.
  - **L-16/L-19** `ZahlungRennenDbTest`: 15 Runden Freigabe ∥ Storno ∥ Kassenlauf
    auf dieselbe Zahlung (Invarianten: gültiger Endstatus, keine Übergabe ohne
    Freigabe, Budget = Start − aktive Zahlungen), zwei parallele Stornos (genau ein
    Gewinner, Rückbuchung genau einmal) und zwei parallele Anordnungen desselben
    Monats (genau eine wirksame Zahlung).
  - L-20 (Session-Ablauf/Login-Sperre) ist über `BenutzerverwaltungZeitTest`
    abgedeckt; L-17/L-18 (DB-Stop/Server-Restart während eines testsweet-Laufs)
    bleiben bewusst manuelle Betriebs-Tests (Umgebungseingriff), Dokumentation im
    testsweet-README empfohlen.
- **Benutzerhandbuch neu generiert** (Version 0.5.5): Quellen um
  Schwangeren-Mehrbedarf mit Entbindungsmonat-Feld, Einmal-Druck-Token (Text +
  API-Tabelle `/api/drucktoken`) und die korrigierte Vier-Augen/Admin-Beschreibung
  ergänzt; DOCX (5,9 MB) und PDF (3,8 MB) über die Werkzeug-Pipeline
  (node generate.js → fuszeilen_patchen.py → soffice) erzeugt.

**Neuer Gesamtstand: 202 Tests** (136 vor dem Review → 195 nach der Hauptumsetzung
→ 202 mit diesem Nachtrag), alle grün gegen Docker-Oracle.

## 7. Nachtrag 2 (2026-10-12, Nacht): Fachentscheidungen CR-015/CR-016 umgesetzt — v0.5.6

Auf Fachentscheid des Betreibers wurden zwei der drei vertagten Punkte umgesetzt:

- **CR-015 Verbrauchsfortschreibung (§ 90 SGB XII) — BEHOBEN:** Der über dem
  Schonvermögen liegende Vermögensanteil verbraucht sich mit der ersten Auszahlung:
  `ordneAuszahlungAn` und Saga-Schritt 4 mindern `BEDARFSGEMEINSCHAFT.VERMOEGEN` in
  derselben Transaktion unter der BG-Sperre (Guard `VERMOEGEN >= Einsatz`); der Einsatz
  ist je Zahlung dokumentiert (neue Spalte `ZAHLUNG.VERMOEGENSEINSATZ`, DDL + Migration).
  Ab dem Folgemonat liegt der Rest im Schonvermögen — kein Abzug mehr. Der Storno bucht
  genau den dokumentierten Einsatz zurück (Service und Saga-Kompensation); das Journal
  weist jeden Einsatz aus. Fachlich entspricht das der Gesetzeslage: Das Schonvermögen
  bleibt unangetastet, der einsatzpflichtige Teil verzehrt sich. Tests:
  `VermoegensfortschriebDbTest` (Monat 1 gemindert/Monat 2 voll/Storno-Rückbuchung),
  erweiterte `CodereviewRegressionDbTest` (Saga dokumentiert Einsatz und schreibt fort),
  angepasste Erwartungen in Szenario-Regression Fall 6, doc/regressionstests.md und
  testsweet (VERMOEGEN-Szenario prüft jetzt den fortgeschriebenen Bestand).
- **CR-016 versionierter Satzkatalog — BEHOBEN (Satztabelle):** Neue Tabelle
  `SATZSTAND` (GUELTIG_AB YYYY-MM + RBS_1…RBS_6, CHECKs, Seed 2026-01 in
  ddl/lmg_seed.sql; Migration auf Docker-Oracle ausgeführt). Neuer `Satzstand`-Record
  und `SatzstandDao` (findeFuerMonat/findeNeuesten, TxContext- und
  Connection-Variante für die Saga). `Leistungsberechnung` rechnet mit übergebenem
  Satzstand; **Service und Saga lesen den zum Bezugsmonat gültigen Stand aus der
  Datenbank** — der Jahreswechsel ist ein INSERT in SATZSTAND, keine Codeänderung;
  fehlender Stand wird fachlich abgewiesen (RBS_NICHT_KONFIGURIERT). Die
  Kompatibilitätsüberladungen (und testsweet) nutzen die klar gekennzeichnete
  Seed-Referenz `Satzstand.REFERENZ_2026`. Abgeleitete Werte (Kappung 50 % RBS 1,
  RBS-1-Basis des Alleinerziehenden-Mehrbedarfs) folgen automatisch. Tests:
  `SatzstandDbTest` (Werte 2026, Abweisung vor erster Zeile, **Jahreswechsel per
  INSERT**: Dezember 863,00 vs. Januar 900,00 bei RBS 1 = 600).
- **Verbleibend offen** (dokumentiert in rechtsgrundlagen.md Abschnitt 4 Nr. 7):
  die Abstimmung laufender Bescheide mit einem Satzwechsel (§ 48 SGB X — Zahlung
  folgt Bescheid-Snapshot bis Änderungsbescheid?) als Verfahrensfrage, sowie
  CR-035 Need-to-know-Filter (wartet auf das Berechtigungsmodell der Fachseite).
- **Nebenbefund behoben:** testsweet/pom.xml pinnte die Hauptversion auf 0.5.4 —
  Läufe liefen still gegen ein veraltetes Jar (einziger Verifikationsfehler im
  Nachtlauf; Symptom: neue Erwartung, altes Verhalten). Pinning auf 0.5.6 korrigiert;
  beim nächsten Versionsschub mitziehen.
- **Benutzerhandbuch v0.5.6 regeneriert** (Satztabelle-Absatz, Vermögens-
  Verbrauchsfortschreibung, Versionskopf).

**Endstand: 207 Tests, alle grün** (136 → 195 → 202 → 207); testsweet gegen 0.5.6:
POSITIV 24/24 (erneut mit Fortschreibungs-Erwartungen), NEGATIV/LAST/STRESS grün.

## 8. Nachtrag 3 (2026-10-12, Nacht): CR-035 als Option D umgesetzt — v0.5.7

Auf Fachentscheid des Betreibers („Option D ist gut, alles protokollieren"):

- **Zugriffsprotokoll:** Der Webserver reicht die Sitzungs-Kennung an die neuen
  Service-Überladungen `ladeDetail(bgId, sb)`, `letzterBescheid(bgId, sb)` und
  `generiereBescheidHtml(bgId, sb)` weiter; jede Einsicht in vollständige Falldaten
  schreibt einen Journaleintrag „Einsicht: … durch sb-xx (Zugriffsprotokoll § 35 SGB I /
  Art. 32 DSGVO)". Wiederholte Einsichten derselben Kennung/Fall/Zweck bündelt der
  Service binnen 60 Sekunden (Masken-Reloads fluten das Journal nicht; Dedup-Zwischen-
  speicher auf 10 000 Einträge gedeckelt). Parameterlose Aufrufe (Batch, Tests,
  testsweet) bleiben bewusst unprotokolliert.
- **Entschärfte Übersichten:** `/api/zahlungen` liefert keine `empfaengerIban`/`empfaengerBic`
  mehr (neues kompakte Mapping); Bankverbindungsdaten erscheinen nur noch im
  protokollierten Einzel-Kontext (Vorgangsdetail, eigene Auszahlungsantwort).
- **Tests:** `EinsichtsprotokollDbTest` (4: Protokollierung, 60-s-Zusammenfassung,
  andere Kennung zählt separat, kennungslose Aufrufe still) und `LmgServerHttpTest`
  erweitert (Sammelübersicht ohne IBAN/BIC; Kennung-Weitergabe an den Service).
- Handbuch v0.5.7 regeneriert (Zugriffsprotokoll-Absatz in 18.1), Doku
  (AGENTS/architektur) aktualisiert. Option B/C bleiben dokumentierte Ausbaustufen.

**Endstand v0.5.7: 213 Tests, alle grün**; testsweet gegen 0.5.7: 24/12/120/40.
