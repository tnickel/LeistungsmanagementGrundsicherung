# Codereview-Bericht — SDN.Leistung LMG / Transaktionssteuerung / testsweet

*Datum: 2026-10-12 · Grundlage: `doc/codereview/codereviewPrompt.txt` (Prüffelder A–J, Spuren S-01…S-48, Testlücken L-01…L-25) · Projektversion 0.5.4 · Review-only — es wurde kein Code geändert.*

---

## 1. Executive Summary

Das Projekt ist insgesamt **sorgfältig gebaut**: Der Transaktions-/Saga-Kern hält seine
Kernversprechen (Rollback auch bei Error, Write-Ahead, idempotente Kompensationen,
settle-Gate), die Datenbank-Regression ist mit **136/136 Tests grün** gegen Docker-Oracle
bestätigt, alle vier testsweet-Kategorien laufen im Review verifiziert durch
(POSITIV 24/24, NEGATIV 12/12, LAST 120/120, STRESS 40/40), SQL-Injection und XSS sind
sauber geschlossen, und das Fachrecht der Bedarfsberechnung stimmt Zeile für Zeile mit
`doc/rechtsgrundlagen.md` überein.

Die Schwerstwiegenden Probleme liegen **nicht im Kern, sondern an den Rändern**:

1. **Sozialdaten (Name, IBAN, Einkommen) wandern ungefiltert ins Audit-Log** (Datei, Konsole, Ringpuffer) — CR-001, Verstoß gegen § 35 SGB I / Art. 9 DSGVO.
2. **testsweet schreibt Simulations-Passwörter in die maschinenlesbare Testdokumentation** und liefert sie ungeschützt über `GET /api/md` aus — CR-002.
3. **Der Saga-Fachweg rechnet systematisch anders als der manuelle Weg** (kein Vermögen, keine KdU-Grenze, Absetzbeträge/Schwangerschaft verloren, keine 12-Monate-Prüfung) — CR-003/CR-007: falsche Bescheid- und Zahlbeträge je nach Erteilungsweg.

Dazu kommen zwei handfeste Betriebsrisiken (Wildcard-Bindung 0.0.0.0 mit Demo-Konten,
ISO-8859-1-Mojibake in amtlichen Bescheiden) und ein methodisches Defizit des
Testwerkzeugs: Die erwarteten Beträge werden mit der Produktionsklasse berechnet
(Zirkelschluss) und mehrere im Report behauptete Prüfungen existieren nicht als
SQL-Erwartungen.

**Testlauf-Ergebnisse (dynamisch verifiziert):**

| Lauf | Ergebnis |
|---|---|
| `mvn test` mit `TX_URL` (Docker-Oracle, alle Suites) | **136 Tests, 0 Fehler, 0 Ausfälle, 1 übersprungen — BUILD SUCCESS** |
| testsweet Modus 1 POSITIV (8 MA / 24 Aufträge) | **24/24 verifiziert**, 0 fehlgeschlagen, 27.128,36 EUR Summe |
| testsweet Modus 1 NEGATIV (4 MA / 12 Aufträge) | **12/12 verifiziert**, 0 fehlgeschlagen, 0 EUR (DB unverändert) |
| testsweet Modus 1 LAST (10 MA / 120 Aufträge) | **120/120 verifiziert** in 1,2 s |
| testsweet Modus 1 STRESS (5 MA / 20 User × 2 Wellen) | **40/40 verifiziert** in 0,9 s |
| testsweet Modus 2 (Selenium) | **nicht ausgeführt** (Review-Umgebung CLI; statisch geprüft, siehe Prüffeld I) |
| Erster DB-Lauf vor Container-Start | ORA-12541 (Umgebungsproblem, kein Befund) |

**Befundzählung:** 3 BLOCKER · 9 KRITISCH · 24 MAJOR · 23 MINOR · 15 INFO (Doppelzählungen
über Prüffelder sind konsolidiert; MINOR/INFO in Kompaktlisten).

---

## 2. Befunde

### BLOCKER

---

**CR-001 — Sozialdaten im Audit-Log (Datei, Konsole, Ringpuffer)**
- Schweregrad: **BLOCKER** · Prüffeld: A10/F1 · Normbezug: § 35 SGB I (Sozialgeheimnis), Art. 9 DSGVO
- Ort: `src/main/java/de/allgemein/tx/audit/TxAuditLog.java:104-107` (`recordSagaStart` schreibt `"payload=" + payload`), `:133-166` (Ringpuffer + JUL + Datei), Aufrufer `SagaEngine.java:52`, Payload-Erzeugung `LmgServer.java:397-403` (kompletter Request-Body), Felder `AntragsSagaProzess.java:74-84, 447-482`.
- Beobachtung: Jeder `POST /api/saga/antrag` schreibt Name, IBAN, BIC, Einkommenswerte im Klartext in JUL (Konsole), in `lmg_audit.log` (Datei-Audit ist **per Default aktiv**, `LmgConfig.java:53`) und in einen 1000-Einträge-Ringpuffer im Heap. Auch `recordSagaCompensating/Failed` (`TxAuditLog.java:119-127`) geben `e.getMessage()` mit Aktenzeichen aus. Die Datei wird ohne Zugriffsbeschränkung/Rotation angelegt (siehe CR-012) und ist über CR-025 sogar vom testsweet-Server aus lesbar.
- Auswirkung: Massenhaft Sozialdaten für jeden zugriffsberechtigt, der Logdatei/Konsole/Arbeitsverzeichnis lesen kann; Log-Shipping/Backup als Verbreitungspfad.
- Empfehlung: Payload im Audit auf Korrelationsdaten kürzen (sagaId, Typ, ggf. pseudonymisiertes Aktenzeichen); Dateirechte + Rotation regeln; Fehlermeldungen ohne Klartext-Sozialdaten protokollieren.
- Absicherung: `TxAuditLogTest` erweitern — `recordSagaStart(...)` mit IBAN-Payload: weder Logzeile noch Datei darf „DE…“/Namen enthalten (L-04-verwandt).

**CR-002 — Passwörter in der testsweet-Testdokumentation**
- Schweregrad: **BLOCKER** · Prüffeld: I6/E7
- Ort: Kette `WebMitarbeiter.java:102-109` (legt `passwort`, `kollegePasswort`, `adminPasswort` in den Kontext) → `:126/:145` (`auftrag.kontext = Map.copyOf(kontext)`) → `MdBericht.java:109-118` (schreibt **alle** Kontext-Einträge als `ids = …`-Zeile); Werte aus `Simulator.java:474-486` (`sb-web-NN:sim`, `web-admin:sim`) bzw. `:393-402` (**echte Standardkonten** `sb-01:sb-01`, `admin-01:admin`); Abruf ohne Auth: `SweetServer.java:63, 240-252` (`GET /api/md`).
- Beobachtung: Artefakt-Beweis aus realen Läufen: `testsweet/logs/run-20261011-141516/testdokumentation.md:50` enthält `passwort=sim, kollegePasswort=sim` und Storno-Läufe `adminPasswort=sim`. PDF und Logdateien sind nicht betroffen; nur die maschinenlesbare MD-Fassung.
- Auswirkung: Credentials (inkl. Admin-Rolle mit Storno-Befugnis) in jedem weitergegebenen Laufordner — genau die Laufordner sind laut README für Nachprüfungen durch Dritte/KI bestimmt.
- Empfehlung: Passwort-Schlüssel beim MD-Export ausschließen (Allowlist: `bgId`, `zahlungId`, `fallId`, `vorgangErstellt`, `mitarbeiter`, `kollege`, `adminKonto`) oder Passwörter gar nicht in den Auftragskontext schreiben; externe Standard-Passwörter nie hardcodieren.
- Absicherung: Nach jedem Web-Lauf: `grep -iE "passwort" logs/run-*/testdokumentation.md` muss leer bleiben.

**CR-003 — Saga-Fachweg rechnet systematisch anders als der Service-Weg**
- Schweregrad: **BLOCKER** (falsche Geldbeträge möglich) · Prüffeld: D6/C · Normbezug: §§ 43, 90, 35 Abs. 7, 82 SGB XII
- Ort: `AntragsSagaProzess.java:165` (bedarfspruefung), `:208-209` (bescheidErteilen), `:295` (erstauszahlung) — alle konstruieren `new SozialFall(1L, …)` **aus dem Payload** über den Legacy-11-Arg-Konstruktor (`SozialFall.java:70-85`: schwanger=false, Kinder=0, Absetzbeträge=ZERO); `Leistungsberechnung.java:109-111` (2-Arg-Überladung = hart `KduParameter.AUS`, `vermoegen=0`); `Saga:88-89` (BG-Insert ohne VERMOEGEN, kein Payload-Feld); `Saga:228` (Snapshot mit `fallId=1L` statt echter SEQ_FALL-ID).
- Beobachtung: Der Saga-Weg kann Vermögen (§ 43/§ 90) **gar nicht** erfassen, bildet Schwangerschaft/Merkzeichen G/Kinder/Warmwasser/Absetzbeträge nie ab und ignoriert Falländerungen zwischen Schritt 1 und 4. Bei konfigurierter KdU-Angemessenheitsgrenze (§ 35 Abs. 7) weicht jede Saga-Auszahlung vom manuellen Weg ab. Nicht als Vereinfachung dokumentiert.
- Auswirkung: Überzahlung bei vorhandenem Vermögen immer möglich; Bescheid-Inhalt (Snapshot) weicht vom DB-Bestand ab; je nach Erteilungsweg unterschiedliche Beträge für denselben Fall.
- Empfehlung: Schritte 2–4 aus der DB lesen (wie der Service), `config.kduParameter()` und `bg.vermoegen()` verwenden; Payload um Vermögen/Mehrbedarfsmerkmale erweitern oder Saga ausdrücklich auf einfache Neuanträge beschränken und das dokumentieren.
- Absicherung: DB-Test — BG mit VERMOEGEN=10.500 einmal über Service, einmal über Saga: Zahlbeträge müssen identisch sein (aktuell nicht).

### KRITISCH

---

**CR-004 — Wildcard-Bindung 0.0.0.0 kombiniert mit Demo-Standardkonten**
- Prüffeld: E9 · Ort: `LmgServer.java:68` (`new InetSocketAddress(port)` = alle Interfaces), `Benutzerverwaltung.java:29` (`admin-01:admin; sb-01:sb-01; …` ohne Konfig aktiv), `webapp/index.html:37-38` (Konten öffentlich in der Login-Maske).
- Auswirkung: Ohne Firewall-Regel liest/ändert jeder Netzteilnehmer sämtliche Sozialdaten (mit CR-035: Vollbestand). Javadoc/Main suggerieren localhost.
- Empfehlung: Default `getLoopbackAddress()`, konfigurierbares `lmg.host`; Demo-Konten nur bei Loopback-Bindung aktivieren.
- Absicherung: Integrationstest — Server ohne Konfig von Nicht-Loopback-Adresse nicht erreichbar / Demo-Konten inaktiv.

**CR-005 — ISO-8859-1-Properties: Mojibake in amtlichen Bescheiden**
- Prüffeld: B4 · Ort: `LmgConfig.java:76, 84` (`Properties.load(InputStream)`), Verwendung `BescheidGenerator.java:126-127` (Briefkopf) und `:371-372` (Rechtsbehelfsbelehrung); die mitgelieferte `lmg.properties.example:50-53` ist UTF-8 mit Umlauten („Musterstraße“, „Amt für Soziales“) und triggert den Fehler sofort. Spur S-08.
- Auswirkung: Verstümmelte Behördenanschrift/Sozialgericht in einem amtlichen Dokument (§ 36 SGB X-Belehrung).
- Empfehlung: `Properties.load(Reader)` mit UTF-8; Test mit Umlaut-Datei (L-ergänzend).

**CR-006 — Exponenten-Bombe im eigenen JSON-Parser → OutOfMemoryError**
- Prüffeld: E4 · Ort: `Json.java:284-315` (`leseZahl` akzeptiert Exponenten bis int-Bereich, z. B. `1e2000000000`), `AntragsSagaProzess.java:485-507` (`setScale(2)` expandiert auf ~2 Mrd. Stellen); `LmgServer.java:104` fängt nur `Exception`, nicht `Error` → HTTP-Thread stirbt, wiederholbar (DoS). Das 1-MB-Body-Limit schützt nicht (22-Byte-Input).
- Empfehlung: Exponenten-/Ziffernlänge im Parser begrenzen und als IllegalArgumentException (→ 400) werfen; Grenze in `getBigDecimal` vor `setScale`.
- Absicherung: `JsonTest`: `assertThrows(…, () -> Json.parse("{\"a\":1e2147483647}"))` (L-08).

**CR-007 — Saga-Bescheid ohne 12-Monate-Prüfung (§ 44 Abs. 3 SGB XII)**
- Prüffeld: C11/D6 · Ort: `AntragsSagaProzess.java:213-215` (keine Prüfung), `:468-473` (nur Format + `bis >= von`); Service-Gegenstück `SozialLeistungsService.java:624-634` wird nie gerufen. Spur S-20.
- Auswirkung: Über `POST /api/saga/antrag` ist ein Bescheid mit beliebigem Zeitraum (z. B. 2026-01 bis 2040-12) möglich — normwidriges Dokument.
- Empfehlung: `pruefeBewilligungszeitraum` (static, paketsichtbar) in `validierePayload`/`bescheidErteilen` aufrufen. Absicherung: Saga-Payload mit +13 Monaten muss VALIDATION werfen.

**CR-008 — Schwangeren-Mehrbedarf ohne Normende (§ 30 Abs. 2 SGB XII)**
- Prüffeld: C3 · Ort: `ddl/lmg_schema.sql:45` (nur FLAG_SCHWANGER, kein Entbindungsfeld), `Leistungsberechnung.java:166-168`, `SozialLeistungsService.java:262-266` (Neuberechnung je Monat mit unverändertem Flag). Spur S-15.
- Auswirkung: Der Mehrbedarf (RBS 1: 95,71 €/Monat) läuft **unbegrenzt** weiter, bis jemand das Flag manuell löscht → Überzahlung ab dem Monat nach dem Entbindungsmonat (Rückforderung nach § 50 SGB X nötig). Das Verzeichnis dokumentiert nur die Beginn-Konvention (13. SSW), das Normende nirgends.
- Empfehlung: Feld `ENTBINDUNGSMONAT` (YYYY-MM) + Pflicht-Wiedervorlage zum Monatsende (Mechanismus existiert); mindestens Abschnitt 4 der Rechtsgrundlagen ergänzen.

**CR-009 — Historisierte Bedarfsgemeinschaft bleibt zahlbar**
- Prüffeld: D7 · Ort: `SozialLeistungsService.java:397-427` (freigebenZahlung) und `:429-446` (uebergebeBereiteZahlungen) ohne `STATUS_AKTIV`-Prüfung; `:136-154` (Fall-Edit ohne Check), `:157-217` (BG-Maskenspeichern kann STATUS_AKTIV 0→1 setzen — Reaktivierung der Historisierung ohne eigenen Vorgang).
- Auswirkung: Auszahlung für einen historisierten (erledigten/abgelehnten) Vorgang; Historisierung als Fachsperre unzuverlässig.
- Empfehlung: `statusAktiv`-Guard in Freigabe (unter Sperre) und im Kassenlauf-Select; Reaktivierung nur journalführend.

**CR-010 — SweetServer-Fehlerantworten senden immer HTTP 200**
- Prüffeld: I7 · Ort: `SweetServer.java:296-310` — `sendeFehler(exchange, code, …)` reicht `code` an `sendeJson` durch, das hart `200` setzt; 13 Aufrufstellen nennen 400/404/405/409/500, keiner wird gesetzt. Spur S-31.
- Auswirkung: Jeder Client außer der eigenen GUI (die `ergebnis.fehler` liest) sieht Erfolg; Automatisierung/Monitoring bricht.
- Empfehlung: `sendResponseHeaders(code, …)` vor dem Body; GUI zusätzlich `response.ok` prüfen.

**CR-011 — Zirkelschluss der testsweet-Betragserwartungen**
- Prüffeld: I2 · Ort: `SzenarioBibliothek.java:19-27, 1635-1642` und `WebHelfer.java:63-69` rufen die **Produktionsklasse** `Leistungsberechnung.berechne` für die Sollwerte; als Einschränkung eingeräumt in Klassenkopf und `README.md:88-90`. Spur S-48.
- Auswirkung: Ein Fehler in der Berechnung (falscher Prozentsatz, Rundung, Deckelung) erzeugt identisch falschen Sollwert → Lauf bleibt grün. testsweet prüft damit Persistierung/Parallelität, nicht Fachrichtigkeit — die Berichtstexte („Auszahlungsbetrag entspricht der Berechnung“) klingen stärker.
- Empfehlung: Kleine versionierte Handwertetabelle (unabhängig gerechnete Sollbeträge je Szenariofamilie) als zweite Erwartungsquelle; Kennzeichnung im Report („Soll = Produktivcode“).
- Absicherung: Mutationstest — 16 % statt 17 % Merkzeichen G muss mindestens einen Fall NIO machen (aktuell: keiner).

**CR-012 — Audit-Log ohne Rotation, Löschfrist und Schutz**
- Prüffeld: F4 · Ort: `TxAuditLog.java:29-36, 133-166`; Default-Pfad `lmg_audit.log` (`LmgConfig.java:53`). Als offen dokumentiert (architektur.md „Bekannte Grenzen“) — konsistent, aber revisionsrechtlich unzureichend, zumal CR-001 Sozialdaten in dieselbe Datei schreibt.
- Empfehlung: Rotation nach Größe/Datum, Aufbewahrungsfrist, Dateirechte; Status im Admin-Endpoint sichtbar machen.

### MAJOR

---

**CR-013 — `rs.wasNull()` auf falscher Spalte: NULL wird zu 0**
- Prüffeld: D4/G4 · Ort: `ZahlungDao.java:226-243` (wasNull nach anderem getLong → immer false; `fallId=0` statt null bei Mehrpersonen-/Saga-Zahlungen), `JournalDao.java:68-77` (`bgId=0` statt null bei Kassenlauf-Einträgen). Spur S-23 (bestätigt, mit Real-Bug).
- Empfehlung: `wasNull()` unmittelbar nach dem jeweiligen `getLong` auswerten. Absicherung: Mock-ResultSet-Test mit NULL-Zeilen.

**CR-014 — `erteileBescheid` ohne BG-Sperre (Snapshot-Race)**
- Prüffeld: D5 · Ort: `SozialLeistungsService.java:496-526` (ungesperrtes Lesen von Vermögen und Fällen; Buchungsweg sperrt korrekt in `:238`). Spur S-19.
- Auswirkung: Der „revisionssichere“ Snapshot kann einen nie atomar existierten Mischstand einfrieren.
- Empfehlung: `lockForUpdate` auch hier. Absicherung: Zwei-Thread-DB-Test Fall-Update ∥ Bescheiderteilung.

**CR-015 — Vermögensanrechnung ohne Verbrauchsfortschrieb: Voll-Anrechnung in jedem Monat**
- Prüffeld: C10 · Ort: `SozialLeistungsService.java:262-266` (jede Auszahlung mit unverändertem `bg.vermoegen()`), `Leistungsberechnung.java:253-257`. Bei 10.500 € Vermögen werden 12 × 500 € (6.000 €) statt einmalig 500 € abgezogen. Als Vereinfachung dokumentiert („kein Verbrauchsfortschrieb“), der Folgeschaden aber nicht.
- Empfehlung: Fortschreibung je Auszahlungsmonat oder wirksames Vermögen absenken; Doku-Folgehinweis.

**CR-016 — Jahreswechsel: hartkodierte Sätze + Zahlung ohne Bescheidbezug**
- Prüffeld: C1 · Ort: `Leistungsberechnung.java:53-98` (alle Beträge/Prozentsätze `static final`, kein Gültigkeitszeitraum), `SozialLeistungsService.java:262-266` (Zahlbetrag je Monat frisch, nicht aus dem Bescheid). Spur S-12.
- Auswirkung: Vor der Codeanpassung rechnet 2027 mit 2026er Sätzen; nach der Anpassung divergieren Folgezahlung und Bescheid-Snapshot ohne Änderungsbescheidprozess (§ 48 SGB X). Snapshot/Druck selbst bleiben konsistent (C13 — OK).
- Empfehlung: Versionierter Satzkatalog (GültigAb) + Zahlung im laufenden Zeitraum an Bescheid binden oder Änderungsbescheid-Prozess; Abschnitt 4 ergänzen.

**CR-017 — Kein Shutdown-Hook: geordneter Shutdown ist toter Code**
- Prüffeld: B5 · Ort: `LmgServerMain.java:38-79` — `Thread.currentThread().join()`; try-with-resources läuft nur bei Exception, `addShutdownHook` existiert nicht. Drain-Logik (`TaskController.java:150-170`) greift im einzigen realen Beendigungsszenario nicht.
- Empfehlung: Shutdown-Hook (Server → Tasks → Pool → Audit) oder Latch statt Selbst-Join.

**CR-018 — `TxContext.close()` ohne `finish()` verliert die Verbindung dauerhaft**
- Prüffeld: A2 · Ort: `TxContext.java:39-41, 76-86` (AutoCloseable, rollt zurück, gibt aber nichts an den Pool), `TxManager.java:131-137`; kein Leak-Detection in beiden Pools, UCP ohne `abandonedConnectionTimeout`.
- Auswirkung: Naheliegendes try-with-resources-Idiom erschöpft den Pool schleichend.
- Empfehlung: Javadoc-Warnung, UCP-Timeout setzen, Leak-Zähler im SimplePool. Absicherung: TxManager-Test begin+close ohne finish → zweites begin scheitert nach Borrow-Timeout (L-01).

**CR-019 — Kern-Testlücken: TxManager, manuelle API, Error-Pfad, Parallel-Recover, Backoff**
- Prüffeld: A1-A8/H1 · Ort: `src/test/java/de/allgemein/tx/` — keine `TxManagerTest`; `begin/commit/rollback/finish` und `catch (Error)` (`TxManager.java:73-80`) ungetestet; `sleepBeforeAttempt` (Backoff/Jitter/Interrupt), `resultOrCancelAfter`, konkurrierende `recoverWithReport`-Läufe (SKIP-LOCKED-Rennen) ohne Test. Spuren S-03/S-04/S-06.
- Empfehlung: siehe L-01…L-05 (Muster für Fake-Pools existiert in `TaskControllerTest`/`SimpleConnectionPoolTest`).

**CR-020 — Hängende JDBC-Calls ohne TaskController nicht begrenzt**
- Prüffeld: A3/B7 · Ort: `TxManager.java:45-46` (Deadline nur kooperativ nach `work()`), `SimpleConnectionPool.java:117` / `UcpConnectionPool.java:22-33` (keine `oracle.jdbc.ReadTimeout`/Connect-Timeout-Properties), Wiederanlauf ohne Watchdog (`LmgServerMain.java:70`). Spur S-11.
- Empfehlung: JDBC-Timeout-Properties konfigurierbar setzen; Doku „Grenzen der kooperativen Deadline“ ergänzen.

**CR-021 — TxAuditLog: synchrone Datei-I/O unter globalem Lock, pro Event neuer Writer**
- Prüffeld: A10 · Ort: `TxAuditLog.java:145-156`; Hot Path nach jedem Commit (`TxManager.java:50`); Ringpuffer-Trim über `ConcurrentLinkedDeque.size()` (O(n)). Spur S-10.
- Empfehlung: Dauerhaft offenen Writer oder asynchronen Appender mit Bounded Queue; Trim über Zähler.

**CR-022 — UCP ohne connectionWaitTimeout; Kommentar verspricht FCF/RAC**
- Prüffeld: B2 · Ort: `UcpConnectionPool.java:11-15` (Kommentar), `:22-33` (Konstruktor ohne Timeout/ONS), `:105-122` (destroy über JVM-Singleton-Manager). Spur S-09.
- Empfehlung: `setConnectionWaitTimeout` explizit setzen; Kommentar auf Konfiguriertes reduzieren; `close()`-Nebenwirkung dokumentieren.

**CR-023 — `datumWert()` → HTTP 500 statt 400**
- Prüffeld: E2 · Ort: `LmgServer.java:736-741` — `DateTimeParseException` fällt durch `api()`-Catches in den generischen 500-Pfad. Spur S-18. Empfehlung: try/catch → IllegalArgumentException. Absicherung: L-06-Integrationstest.

**CR-024 — `limit` ohne Bereichsprüfung → ORA → 500**
- Prüffeld: E2 · Ort: `LmgServer.java:743-755`, `ZahlungDao.java:191,201`, `JournalDao.java:44,54`. `?limit=-5` → 500. Empfehlung: Clamping [1..200] oder 400.

**CR-025 — Path-Traversal im SweetServer-Dateisystem-Fallback**
- Prüffeld: E6/I7 · Ort: `SweetServer.java:312-346` — Fallbacks `Path.of("testsweet/src/main/resources" + resource)` / `Path.of("testsweet" + pfad)` ohne Normalisierung; `GET /../lmg_audit.log` liefert die Audit-Datei aus CR-001 aus; auch `pom.xml` usw. lesbar. Mildernd: Bindung 127.0.0.1. Spur S-43a.
- Empfehlung: Fallback entfernen oder `normalize()` + `startsWith(root)`; Test mit `curl --path-as-is`.

**CR-026 — Anmelde-Drosselung je Kennung durch Dritte sperrbar; Fehlversuchs-Map unbegrenzt**
- Prüffeld: E8 · Ort: `Benutzerverwaltung.java:46, 80-97`. Empfehlung: IP-Zusatzlimit, Map-Cleanup/Obergrenze.

**CR-027 — Druck-Token im Query-String + Token in localStorage**
- Prüffeld: E3 · Ort: `LmgServer.java:116-131, 221-232`; `webapp/app.js:14, 139, 674`. Server loggt nur den Pfad (Query nicht) — gut; Token landet aber in Historie/Bookmarks. Spur S-26.
- Empfehlung: One-Time-Druck-Token (TTL/invalidiert nach Abruf), sessionStorage.

**CR-028 — STRESS+WEB über CLI: Guard erst nach Ressourcenstart**
- Prüffeld: I3 · Ort: Guard in `erzeugeTopf` (`Simulator.java:634-638`) — **nach** SweetLog-Anlage (`:374`), eingebettetem Server (`:392`) und WebDriverManager (`:404`); CLI (`SweetMain.java:36-49`) nimmt STRESS an, Meldung nennt es nicht (`:86-87`). Folge: offener Port 8080, Laufverzeichnis mit offenen Handles. Spur S-35.
- Empfehlung: Guard als erste Anweisung bzw. im CLI-Parsing; STRESS in die Meldung aufnehmen.

**CR-029 — SweetLog wird im Fehlerpfad nie geschlossen**
- Prüffeld: I3 · Ort: `log.close()` nur `Simulator.java:458-459/620-621` (Happy Path); Fehlerpfade `:262-268, 354-360`, CLI-Synchronvarianten `:236-248, 328-340` ohne catch/finally. Spur S-34. Empfehlung: try/finally.

**CR-030 — Kein Selenium-Treiber-Neustart nach Renderer-/Session-Tod**
- Prüffeld: I5 · Ort: `WebMitarbeiter.java:51-70, 116-143` — Fenster wird einmal erzeugt; stirbt der Renderer, scheitert jeder Folgeauftrag des Fensters im ersten Schritt. Bekannter Einzelfall 234154. Spur S-36.
- Empfehlung: Bei SessionNotFound/Timeout: Treiber schließen + neu erzeugen + Auftrag wiederholen (max. 1×).

**CR-031 — Im Report behauptete Prüfungen existieren nicht als SQL-Erwartung**
- Prüffeld: I9 · Ort: `SzenarioBibliothek.java:327-331` (Freigabevermerk ≠ Erfasser behauptet; `erwartungenStandard :1657-1678` prüft ihn nicht), `:494-497` (Wiedervorlage/Kinderzahlen), `:711-714` („EIN Auszahlungsvermerk im Journal“). Spur S-37.
- Empfehlung: Erwartungen ergänzen (`FREIGEGEBEN_VON`-Prüfung schließt zugleich die Vier-Augen-Identität). Absicherung: L-22.

**CR-032 — Journal-Ketten und Rollback-Sauberkeit praktisch ungeprüft**
- Prüffeld: I9 · Ort: Journal-Erwartung nur im KASSENLAUF-Szenario (`SzenarioBibliothek.java:901-906`); FEHLGESCHLAGEN-Aufträge werden übersprungen (`Verifikator.java:35-39`), während `LastBericht.java:131-134` „sauberes Zurückrollen ohne halbfertige User“ behauptet. Spuren S-38/S-39.
- Empfehlung: Minimale Journal-Konsistenzprüfung je Auftrag; Negativbilanz für FEHLGESCHLAGEN (keine Zahlung, Budget unverändert).

**CR-033 — TEXT_IN „BEREIT|UEBERGEBEN“ verdeckt Fehlübernahmen**
- Prüffeld: I1 · Ort: `Erwartung.java:16-18`, Nutzung `SzenarioBibliothek.java:1660-1665, 798-800` u. a. — eine ungebetene Freigabe oder Kassenlauf-Übergabe bleibt grün. Kompromiss ist im Code begründet (paralleler Kassenlauf im Topf).
- Empfehlung: Statushistorie aus dem JOURNAL je Zahlung prüfen; Alternativen nur in Szenarien mit eigenem Kassenlauf.

**CR-034 — Globale Log-Sperre + Zeilen-Flush verfälscht Lastmesswerte**
- Prüffeld: I4 · Ort: `SweetLog.java:54-120` (synchronized), `:149` (flush je Zeile); die gemessenen `dauerMs` enthalten Log-Wartezeit → Referenzwerte im Lastreport sind obere Schranken mit unbekanntem Loganteil. Spur S-42.
- Empfehlung: Asynchroner Writer oder Batch-Flush; Dauermessung vom Logging entkoppeln.

**CR-035 — Vollbestands-Sozialdaten an alle authentifizierten Nutzer**
- Prüffeld: E1/F2 · Ort: `LmgServer.java:488-518, 612-628` — Listen-Endpunkte liefern Namen, IBAN, Einkommen **aller** BGs an jeden SB; IDs sequenziell ratbar; keine Besitz-/Bereichsprüfung. Für eine kleine Behörden-Innenanwendung vertretbar, braucht aber eine dokumentierte Berechtigungsannahme (§ 35 SGB I, Art. 5 DSGVO Datenminimierung); mit CR-004 eskalierend.
- Empfehlung: Bereichs-/Rollenfilter oder dokumentierte Organisationsregel; IBAN nur im Fallkontext.

**CR-036 — Handbuch-Drift Vier-Augen/Admin + toter API-Parameter**
- Prüffeld: D2/J · Ort: `doc/handbuch-werkzeug/teil1.js:171`, `teil2.js:146` beschreiben eine nicht existente Vier-Augen-Checkbox („ordnet sofort BEREIT an“), die Admin-Ausnahme als „Sonderkennung“ statt Rolle und eine Budget-Nachprüfung bei der Freigabe, die es nicht gibt; `vierAugenErforderlich` wird gesetzt (`LmgServer.java:378`) aber nie gelesen. Code selbst korrekt. Spur S-25.
- Empfehlung: Handbuch-Generator korrigieren; toten Parameter entfernen.

### MINOR (Kompaktliste)

| ID | Ort | Befund | Empfehlung |
|---|---|---|---|
| CR-037 | `BescheidGenerator.java:308-323` | § 30 Abs. 6-Kappung: Begründung nennt ungekappte Einzel-Mehrbedarfe, Summe passt nicht zum Tabellenwert, Kappungsgrund ungenannt | Kappungssatz ergänzen (§-Zitat) |
| CR-038 | `BescheidGenerator.java:377-380` | Widerspruchsbelehrung ohne elektronische Form (§ 84 SGG i.V.m. § 65a SGG) | „schriftlich, elektronisch oder zur Niederschrift“ |
| CR-039 | `doc/rechtsgrundlagen.md:116` vs. `BescheidGenerator.java:368-369` | Doku sagt § 73 Abs. 2 SGG, Code zitiert (richtig) § 73 Abs. 1 | Verzeichnis korrigieren |
| CR-040 | `BescheidGenerator.java:54-58`, `LmgConfig.java:57` | Rechtsbehelf-Modus ohne Wertvalidierung (Tippfehler → still falsche Belehrung) | Beim Laden validieren |
| CR-041 | `ZahlungDao.java:177-179`, `BescheidDao.java:33-48`, `SozialLeistungsService.java:519+525` | JVM-Zeit im Rückgabeobjekt vs. SYSTIMESTAMP; Doppel-`LocalDate.now()` → BESCHEID_NR/Datum können über Mitternacht auseinanderlaufen (S-13/S-22) | Zeit einmalig je Tx ermitteln |
| CR-042 | `ZahlungDao.java:26-35, 84-89`, `BescheidDao.java:59-65, 77-83` | Tote DAO-Pfade; `aufheben` ohne Status-Guard | Entfernen oder sichern |
| CR-043 | `AntragsSagaProzess.java:87-98, 310-324` | Saga-Fachfehler als technische 500er (Längen, Monatskollision → ORA-00001 statt Fachhinweis) | Fachliche Vorprüfungen ergänzen |
| CR-044 | `ddl/lmg_schema.sql:85, 111-112` | ZAHLUNGSMONAT/BEWILLIGUNG ohne Format-CHECK | regexp_like-Guard nachrüsten |
| CR-045 | `ddl/oracle_schema.sql:30-40` | TX_SAGA_STEP-FK ohne Index (Recover = Full Scan je Saga) | Index anlegen |
| CR-046 | `SimpleConnectionPool.java:93-96` u. a. | Pool-Erschöpfung ohne operatives WARNING | Rate-limitiert loggen |
| CR-047 | `TaskController.java:208-210` | Watchdog-Zeitlimit gilt über alle Retry-Versuche inkl. Backoff; TxContext-Deadline je Versuch — Semantik-Divergenz undokumentiert | Festlegen + dokumentieren |
| CR-048 | `TxAuditLog.java:77-79` | `clearForTests()` öffentlich in Produktionsklasse | paket-privat |
| CR-049 | `TxMonitoring.java:81-84`, `LmgServerMain.java:39-42` | JMX-Register kann null liefern; Startmeldung druckt „aktiv (null …)“ | Null-Prüfung/Warnung |
| CR-050 | `LmgServerMain.java:55-74` | Wiederanlauf läuft **nach** `server.start()` (kurzes Fenster paralleler Anfragen während recover); Recovery-Fehlerzahl erscheint nicht | Reihenfolge tauschen/Readiness-Gate |
| CR-051 | `LmgServer.java:440-441` | Falsche Methode → 404 statt 405 (kein Allow-Header) | 405 + Allow |
| CR-052 | `LmgServer.java:757-766` | Kein `Cache-Control: no-store` auf Token-/API-Antworten; 404-Zweig ohne CSP | Header ergänzen |
| CR-053 | `webapp/app.js:465-500` u. a. | `value="${fall.kduMiete}"` etc. ohne `esc()` — heute typsicher (Server liefert Zahlen), latent riskant bei Typwechsel | konsequent esc() |
| CR-054 | `webapp/app.js:684` vs. `LmgServer.java:246` | Storno-Button für SB bei ANGELEGT sichtbar, Server antwortet 403 | UI-Bedingung angleichen |
| CR-055 | `Simulator.java:793-816` | Phasenbarriere als 25-ms-Busy-Wait (abbruchdicht, aber Polling) | Condition/Latch (S-41) |
| CR-056 | `Simulator.java:214-221`, `Auftrag.java:103-110` | Wanduhr-Zeit für Dauern — Zeitumstellung 25.10.2026 verfälscht Läufe | nanoTime/Instant (S-33) |
| CR-057 | `raeume-fenster.ps1:3-8`, `WebMitarbeiter.java:186-208` | Globaler Kill aller chromedriver/test-type-Chrome des Benutzers (trifft fremde Sessions) | Auf eigene Prozesse begrenzen (S-43b) |
| CR-058 | `Simulator.java:393-418` | Konten-Wiederverwendung >2 Fenster gegen externen Server → Vier-Augen-Kollisions-Kaskaden; harte Passwort-Annahmen über fremde Prozesse | Warnung/Kontenliste (S-46) |
| CR-059 | `SzenarioBibliothek.java:1703-1705` | Aktenzeichen-Duplikate ohne Erkennung (Zweitläufe ohne Aufräumen → ORA-01427/diffuse Fehler) | Duplikat-Check vor Laufstart (S-40) |
| CR-060 | `SweetPdf.java:39, 683-689` | CP1252: „→“ wird „?“ — Artefakt `run-20261011-102022/testdokumentation.pdf` enthält „BEREIT ? UEBERGEBEN“ | normalisiere um → erweitern (S-32) |

### INFO (Kompaktliste)

| ID | Ort | Feststellung |
|---|---|---|
| CR-061 | `Leistungsberechnung.java:163-168` | § 30 Abs. 1/2 ohne Personenkreis-Differenzierung (Viertes Kapitel pauschal) — dokumentationskonform, Plausibilitätswarnung optional |
| CR-062 | `Leistungsberechnung.java:241-250` | Schonvermögen-RBS-Zuordnung: Randfall „BG aus zwei Minderjährigen ohne Erwachsenen“ nur einmal 10.000 — von der dokumentierten Vereinfachung gedeckt; Doku ergänzen |
| CR-063 | `BescheidGenerator.java:361-362` | § 37 Abs. 2 SGB X: nur Postvariante belehrt (elektronische Variante im Verzeichnis genannt) — unschädlich |
| CR-064 | `TxAuditLog.java:35, 42-54` | `setAuditFilePath` nur zur Initialisierung sicher — Kontrakt dokumentieren |
| CR-065 | `TaskController.java:249-258` | Watchdog/Statistik-Randfall (timedOut-Flag trotz completed) — reine Kosmetik, settle-Gate verhindert Doppelzählung |
| CR-066 | `SagaJournal.java:236` | **S-05 widerlegt**: SKIP LOCKED funktioniert ab 11g/auf 19c (Ask TOM 2020); recover ist via EchteDbVerifikationTest gegen echte DB getestet und in roadmap.md dokumentiert — Mindest-DB-Version einmalig notieren |
| CR-067 | `RetryPolicy.java:15-16` | ORA-30006/04020 bewusst nicht retryable — vertretbar (nur blockierendes FOR UPDATE im eigenen SQL); Begründung im Javadoc wünschenswert |
| CR-068 | `LmgServer.java:140-142` | CSP-Setzung insgesamt vertretbar (unsafe-inline nur für Style im Bescheid, script-src strikt) |
| CR-069 | `Benutzerverwaltung.java` | Sessions im Einzelnen korrekt: UUIDv4/SecureRandom, Logout invalidiert, Sliding-Timeout; parallele Sessions einer Kennung unlimitiert |
| CR-070 | alle DAOs | SQL-Injection sauber geschlossen: durchgängig PreparedStatements, Konkatenation nur für Konstanten/hartkodierte Sequence-Namen, IN-Listen mit `?`-Bindung |
| CR-071 | `LmgServer.java:70` | 16er-HTTP-Pool vs. TaskController 8/100 konsistent (kein Deadlock möglich); Queue-Überlast → 500 statt 503/429 |
| CR-072 | `SozialLeistungsService.java:406-459` | FachlicheException-Texte ohne Namen/IBAN; technische Fehler generisch verborgen — vorbildlich |
| CR-073 | Sperrenordnung | Keine Deadlock-Inversion (Storno ZAHLUNG→BG, Kassenlauf ORDER BY ID, Freigabe nur ZAHLUNG); Oracle-Re-Prüfung nach Lock-Erwerb schließt zwischenzeitliche Storni aus |
| CR-074 | `ddl/lmg_schema.sql:191-194` | UQ_ZAHLUNG_BG_MONAT_AKTIV korrekt (CASE→NULL nur für Storni; deckt Service- und Saga-Pfade) |
| CR-075 | Schreibdisziplin | Alle Fach-Schreibpfade über TxManager.execute; Sequences race-frei im Tx; VERSION-Guards + geprüfte Zählungen durchgängig |

---

## 3. Spurenkatalog-Ergebnis

| Spur | Ergebnis | Verweis/Begründung (Kurzform) |
|---|---|---|
| S-01 | **BESTÄTIGT** | pom 0.5.4; architektur.md/leistungsmanagement.md/Blueprint 0.5.2, roadmap 0.5.3 → Doku-Befund (Abschnitt 5) |
| S-02 | **BESTÄTIGT (Grenzfall)** | Code/DDL/UI/Reports frei vom Vorbild-Namen (grep leer); historisches Blueprint-PDF trägt ihn noch im Dateinamen — per AGENTS-Regel als Alt-Dokument zulässig, Umbenennung optional |
| S-03 | **BESTÄTIGT** | keine TxManagerTest; manuelle API + Error-Pfad ungetestet (CR-019) |
| S-04 | **BESTÄTIGT** | RetryPolicyTest testet nur isRetryable (CR-019) |
| S-05 | **WIDERLEGT** | SKIP LOCKED ab 11g, auf 19c funktionsfähig; recover gegen echte DB getestet + dokumentiert (CR-066) |
| S-06 | **BESTÄTIGT** | resultOrCancelAfter/Parallel-Recover ohne Test (CR-019) |
| S-07 | **BESTÄTIGT** | → CR-001 |
| S-08 | **BESTÄTIGT** | → CR-005 |
| S-09 | **BESTÄTIGT** | → CR-022 |
| S-10 | **BESTÄTIGT** | → CR-021, CR-048 |
| S-11 | **BESTÄTIGT** | → CR-020 |
| S-12 | **BESTÄTIGT** | → CR-016 |
| S-13 | **BESTÄTIGT** | → CR-041 |
| S-14 | **BESTÄTIGT** | src/test/web enthält nur Json/Benutzerverwaltung-Unit-Tests; kein HttpServer-Test (→ L-06) |
| S-15 | **BESTÄTIGT** | → CR-008 |
| S-16 | **BESTÄTIGT** | → CR-003 |
| S-17 | **BESTÄTIGT** | → CR-003 (nicht als Vereinfachung dokumentiert) |
| S-18 | **BESTÄTIGT** | → CR-023, CR-024 |
| S-19 | **BESTÄTIGT** | → CR-014 |
| S-20 | **BESTÄTIGT** | → CR-007 |
| S-21 | **TEILWEISE** | Format-Inkonsistenz Service/Saga bestätigt (CR-042-Umfeld); UQ-Kollision im engineered Ablauf **widerlegt** — Wiederholung scheitert vorher an UQ_BG_AKTENZEICHEN, Schritt 3 läuft je Saga max. einmal (Action+DONE atomar) |
| S-22 | **BESTÄTIGT** | → CR-041 |
| S-23 | **BESTÄTIGT** | → CR-013 (mit echtem Mapping-Bug) |
| S-24 | **BESTÄTIGT** | → CR-042 |
| S-25 | **BESTÄTIGT** | Code korrekt + kommentiert; **Handbuch** beschreibt Altes → CR-036 |
| S-26 | **BESTÄTIGT** | → CR-027 |
| S-27 | **BESTÄTIGT** | dokumentierte Demo-Grenze; kein Passwort-Logging in Hauptprojekt (positiv verifiziert); Standardkonten → CR-004 |
| S-28 | **BESTÄTIGT** | → CR-006 + fehlende Tests |
| S-29 | **BESTÄTIGT** | → CR-004 (Wildcard + 16er-Executor) |
| S-30 | **BESTÄTIGT** | → CR-002 (mit Artefakt-Beweis) |
| S-31 | **BESTÄTIGT** | → CR-010 |
| S-32 | **BESTÄTIGT** | → CR-060 (mit Artefakt-Beweis) |
| S-33 | **BESTÄTIGT** | → CR-056 |
| S-34 | **BESTÄTIGT** | → CR-029 |
| S-35 | **BESTÄTIGT** | → CR-028 |
| S-36 | **BESTÄTIGT** | → CR-030 |
| S-37 | **BESTÄTIGT** | → CR-031 |
| S-38 | **BESTÄTIGT** | → CR-032 |
| S-39 | **BESTÄTIGT** | → CR-032 |
| S-40 | **BESTÄTIGT** | → CR-059 |
| S-41 | **BESTÄTIGT (minoriert)** | → CR-055; Abbruchdichte und Dekrement-auch-bei-Fehlschlag verifiziert OK |
| S-42 | **BESTÄTIGT** | → CR-034 |
| S-43 | **BESTÄTIGT** | → CR-025 (Traversal) + CR-057 (Kill-Muster) |
| S-44 | **ÜBERWIEGEND WIDERLEGT** | „<keine Zeile>“-Pfad ist gehärtet (BETRAG/GANZZAHL_MIN fangen NFE; Aggregationen gefiltert); echter Restfall: DB-NULL → NPE als „<SQL-Fehler>“ fehlklassifiziert (Verifikator.java:89-92) |
| S-45 | **BESTÄTIGT** | toter Code: mitarbeiterLatch, GANZZAHL_MIN, Altsignaturen, 5-Arg-Konstruktor, sinngleiche Ternary |
| S-46 | **BESTÄTIGT** | → CR-058 |
| S-47 | **BESTÄTIGT** | → CR-028 |
| S-48 | **BESTÄTIGT** | → CR-011 (im Werkzeug selbst eingeräumt) |

**Summe: 44 bestätigt, 1 widerlegt (S-05), 1 überwiegend widerlegt (S-44), 2 teilweise (S-02, S-21).**

---

## 4. Testlücken-Katalog (priorisiert)

Bewertung: Risiko = was unbemerkt kaputtgehen kann; Aufwand S (<½ Tag) / M (½–2 Tage) / L (>2 Tage).

| Prio | Lücke | Deckt | Risiko | Aufwand |
|---|---|---|---|---|
| **P1** | L-06 HTTP-Integrationstest LmgServer (Port 0): 401/403-Matrix, 400-Müll, >1 MB, Tiefe >128, CSP/nosniff, Druck-Query-Token gültig/fremd/abgelaufen, FachlicheException→400+Code | CR-004, CR-006, CR-023, CR-024, CR-027, CR-051, CR-052, CR-054 | höchstes ungedecktes Feld (größte Klasse, null Tests) | M |
| **P1** | L-01 TxManagerTest mit Fake-Pool: Retry-Schleife (ORA-60→OK, maxAttempts, Fachfehler nie wiederholt), Error→Rollback, manuelle API in allen Reihenfolgen (finish ohne commit, doppelt, nach Error) | CR-018, CR-019 | Datenkonsistenz-Pfade nur per Review gesichert | M |
| **P1** | L-25 Goldene Master-Werte (handgerechnete Sollbeträge je Szenariofamilie) in testsweet | CR-011 | Fachfehler in Leistungsberechnung bleiben grün | S |
| **P1** | L-22 Journal-Verifikation je Szenario + Vier-Augen-Identitätsbeweis (FREIGEBEN_VON ≠ ERFASST_VON) | CR-031, CR-032 | behauptete Nachweise ohne Beweis | S |
| **P2** | L-16 Storno-/Freigabe-/Kassenlauf-Rennen (3 Threads auf dieselbe Zahlung) + Historisierungs-Guards | CR-009 | Races im Statusautomat | M |
| **P2** | L-12 Paralleler recover (2 Threads, SKIP LOCKED) — Unit (FakeJournal) + echt | CR-019 | Claim-Rennen ungetestet | S |
| **P2** | L-08 Json-Grenzen: Tiefenbombe, Exponenten (1e2147483647), doppelte Keys, 1-MB-Grenze | CR-006 | DoS-Test fehlt komplett | S |
| **P2** | L-07 Benutzerverwaltung: Drosselung (versetzt/fremd), Idle-Timeout (Clock-Injektion), parallele Sessions | CR-026 | Auth-Verhalten ungetestet | M |
| **P2** | L-13 Prozess-Kill statt synthetischem Crash (Saga IN_FLIGHT real abreißen, dann recover) | CR-019 | Wiederanlauf nur synthetisch geprüft | M |
| **P2** | L-21 Positive Bescheidkette über die **Saga** (12 Monate exakt / +1 Tag) + Snapshot-Druck | CR-003, CR-007 | Saga-Bescheid-Pfad ohne fachliche Prüfung | S |
| **P2** | L-24 Rollback-Sauberkeit fehlgeschlagener Aufträge (DB unverändert) | CR-032 | zentrale Aussage unbewiesen | S |
| **P3** | L-02 Backoff/Jitter/Interrupt-Test; L-03 resultOrCancelAfter; L-04 AuditLog-Trim/Listener/Dateifehler; L-05 JMX-Fehlerpfad | CR-019, CR-021 | Randpfade | S gesamt |
| **P3** | L-09 validierePayload tabellarisch; L-10 Service-Workflow mit Fakes (empfohlener Umbau); L-11 Snapshot-Fremdversion/Rundreise | CR-003-Umfeld | Wartbarkeit | M |
| **P3** | L-14 Deadlock-Gezielttest Fachschema (vertauschte Sperrenreihenfolge) | CR-073 (Absicherung) | Änderungen der Sperrenordnung | S |
| **P3** | L-15 Fachliche Grenzfälle: Budget==Bedarf, Rente==Bedarf (KEIN_ANSPRUCH-Grenzfall), §-30-Abs.-6-Deckel exakt | CR-015/CR-016-Umfeld | Grenzwertfehler | S |
| **P3** | L-17 Crash/Recovery mitten im testsweet-Lauf (DB-Stop in Welle 1); L-18 Server-Restart Web-Modus; L-19 echte Rennbedingungen (2 Worker dieselbe Zahlung/derselbe Monat); L-20 Session-Ablauf Web | CR-030, CR-033 | Robustheit unter Störung | L |
| **P3** | L-23 Wiederholungslauf ohne Aufräumen als Negativtest | CR-059 | Duplikat-Fehlertypen | S |

Eigene Zusatzfunde (über L-01…L-25 hinaus): Mock-ResultSet-Test für NULL-Mapping (CR-013); Loopback-Bindungs-Test (CR-004); Umlaut-Properties-Test (CR-005); Zwei-Thread-Bescheid-Race-Test (CR-014); Passwort-Frei-Test der MD-Reports (CR-002); HTTP-Status-Test des SweetServer (CR-010).

---

## 5. Doku-Befunde (Prüffeld J)

| Stelle | Befund | Vorschlag |
|---|---|---|
| `doc/architektur.md`, `doc/leistungsmanagement.md`, Blueprint | „Stand v0.5.2“ vs. Code 0.5.4 (S-01 bestätigt) | Stände aktualisieren bzw. Version von Datei-Stand trennen |
| `doc/roadmap.md` | „Stand v0.5.3“ | angleichen |
| `doc/rechtsgrundlagen.md:116` | § 73 **Abs. 2** SGG behauptet, Code zitiert (richtig) Abs. 1 | Verzeichniszeile korrigieren (CR-039) |
| `doc/handbuch-werkzeug/teil1.js:171`, `teil2.js:146` | Vier-Augen-Checkbox (existiert nicht), Admin als „Sonderkennung“, Budget-Nachprüfung bei Freigabe (existiert nicht) | Handbuch neu generieren (CR-036) |
| `AGENTS.md` | „136 Tests grün“ — durch diesen Review erneut bestätigt (136/136); testsweet-Kategorie-/Stress-Beschreibung stimmt | unverändert korrekt |
| `testsweet/README.md:88-90` | Zirkelschluss-Einschränkung ist eingeräumt — Berichtstexte im PDF klingen stärker | Formulierung „Soll = Produktivcode“ in Reports |
| Namensregel | Code/DDL/UI/Reports sauber; ein historisches Alt-PDF trägt den Vorbild-Namen im **Dateinamen** (AGENTS-Regel erlaubt Alt-Dokumente; Umbenennung ist ein Housekeeping-Entscheid) | optional: Datei umbenennen oder in Unterordner „historisch“ |
| `rechtsgrundlagen.md` Abschnitt 4 | Nicht-Modellierungs-Liste stimmt mit dem Code überein (verifiziert); **Lücke:** Normende des Schwangeren-Mehrbedarfs und fehlender Verbrauchsfortschreib-Folgeschaden sind nicht als Vereinfachung genannt | zwei Zeilen ergänzen |

---

## 6. Anhang

### 6.1 Ausgeführte Befehle und Ergebnisse

```text
docker start lmg-oracle                                # Container up (Up 5 minutes)
mvn test (TX_URL=jdbc:oracle:thin:@//localhost:1521/FREEPDB1,
         TX_USER/TX_PASS=tx_demo)
  → Tests run: 136, Failures: 0, Errors: 0, Skipped: 1 — BUILD SUCCESS

testsweet (Modus 1, gegen Docker-Oracle, je `run <ma> <auf> 42 0 <kategorie>`):
  POSITIV 8/24  → fehlgeschlagen 0, verifiziert 24, Verifik.-fehler 0, Summe 27.128,36 EUR
  NEGATIV 4/12  → fehlgeschlagen 0, verifiziert 12, Verifik.-fehler 0, Summe 0 EUR
  LAST    10/120 → fehlgeschlagen 0, verifiziert 120, Verifik.-fehler 0, Summe 128.643 EUR
  STRESS  5/20* → fehlgeschlagen 0, verifiziert 40,  Verifik.-fehler 0, Summe 22.287 EUR
  (* GUI-Zahl = User, Aufträge = 2×N; Laufordner testsweet/logs/run-20261012-0850{28,56,1{14,32}})

Umgebungsstörungen (keine Code-Befunde): erster DB-Lauf ORA-12541 (Listener nicht
bereit); zweiter Lauf komplett gegen downed Docker-Daemon (npipe-Fehler) — nach
Containerstart alles grün. testsweet Modus 2 (Selenium) wurde nicht ausgeführt
(CLI-Review-Umgebung); Selenium-Code statisch geprüft (Prüffeld I).
```

### 6.2 Endpunkt-Matrix (Prüffeld E1)

| Methode/Pfad | Auth | Admin |
|---|---|---|
| POST /api/anmeldung | nein | – |
| POST /api/abmeldung · GET /api/werbin | Token | nein |
| GET/POST /api/bedarfsgemeinschaften · GET/PUT /api/bedarfsgemeinschaften/{id} | Token | nein |
| GET/POST /api/bedarfsgemeinschaften/{id}/bescheid | Token | nein |
| GET /api/bedarfsgemeinschaften/{id}/bescheid/druck | Token **Header oder Query** | nein |
| POST /api/bedarfsgemeinschaften/{id}/sozialfaelle · PUT /api/sozialfaelle/{id} | Token | nein |
| POST /api/auszahlung · GET /api/zahlungen · GET /api/journal | Token | nein |
| POST /api/zahlungen/{id}/freigabe | Token (Vier-Augen im Service) | nein |
| POST /api/zahlungen/{id}/storno · POST /api/kassenlauf · GET /api/system/status · POST /api/system/wiederanlauf | Token | **ja** |
| POST /api/saga/antrag | Token | nein |
| GET / · /app.js · /druck.js · /style.css (Whitelist) | nein | – |

Falsche Methode/unbekannter Pfad → 404 (CR-051). IDs sequenziell ratbar, keine Besitzprüfung (CR-035).

### 6.3 Normabgleich Kurzfassung (Prüffeld C — vollständig geprüft)

**Stimmt mit dem Verzeichnis überein:** § 28 RBS 1–6 + abgeleitete Werte; § 30 Abs. 1/3/5/6/7 (inkl. Deckelreichweite, stufenabhängige Warmwasser-Sätze, FLAG_WARMWASSER-Semantik durchgängig konsistent); §§ 35/42a (Dreiteilung, BG-Grenze deterministisch über `ORDER BY ID`, Vorbehalt im Bescheid, Warmwasser-Abzug nur zentral); § 82 Abs. 2/3 (exakt der dokumentierten Brutto-Lesart entsprechend, Fall-5-Nachrechnung deckungsgleich); § 90/DVO (RBS-Zuordnung wie dokumentiert, alleinstehende minderjährige Person korrekt 10.000); § 44 Abs. 4; § 35/36/37 SGB X (Vier-Tage-Fiktion korrekt, Regressionstest vorhanden); §§ 84/87/88 SGG (beide Modi, Fristen korrekt). BigDecimal SCALE 2/HALF_UP durchgängig, keine float-Arithmetik.

**Abweichungen:** § 44 Abs. 3 auf dem Saga-Pfad (CR-007); § 30 Abs. 2 Normende (CR-008); § 73 SGG Doku (CR-039); elektronische Form (CR-038); Kappungs-Darstellung (CR-037); Verbrauchsfortschreib-Folge (CR-015); Jahreswechsel (CR-016).

### 6.4 Testabdeckung je Klasse (Prüffeld H1, Kurzfassung)

- **Gut getestet:** Leistungsberechnung (~40 Tests inkl. Norm-Grenzfälle), BescheidGenerator (XSS/Title, Belehrungen), SepaValidator, Zahlungsmonat, Json (Basis), Benutzerverwaltung (Basis), RetryPolicy (Klassifikation), TaskController, SimpleConnectionPool, SagaEngine (8 Szenarien, Fake), TxAuditLog (Basis), TxMonitoring, BerechnungsSnapshot; DB: EchteDbVerifikationTest (13), SzenarioRegressionTest (10, SQL-Endzustände, handgerechnete Werte — zirkelfrei), DeadlockRetryEchtTest.
- **Nicht/kaum getestet:** LmgServer (HTTP-Ebene komplett offen), TxManager (keine eigene Klasse), SozialLeistungsService (nur static-Helfer ohne DB), AntragsSagaProzess.validierePayload, UcpConnectionPool (Release/Leak-Pfade), TaskHandle.resultOrCancelAfter, Drosselung/Idle-Timeout, Json-Grenzen; DB-Suiten laufen ohne TX_URL nicht (CI ohne Oracle ist blind auf den Workflow).
- **Tests als Gegenprobe:** Assertions durchweg scharf (exakte Werte, COUNT=1/2, Chronologie); BG-REG--Aufräumhygiene in @BeforeAll/@AfterAll; DB-Nachweise direkt per SQL, nicht über DAOs.

---

*Bericht Ende — Review-only, keine Codeänderungen. Grundlage: `doc/codereview/codereviewPrompt.txt`; Spuren S-01…S-48 vollständig abgearbeitet (Abschnitt 3).*
