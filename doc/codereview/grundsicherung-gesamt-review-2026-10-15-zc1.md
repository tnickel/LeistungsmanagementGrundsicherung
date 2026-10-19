# Gesamt-Review SDN.Leistung — 2026-10-15-zc1

Prüfgegenstand ist der Arbeitsstand `37abfb6f3da841e440ab21a53fbffa7eadf7cd04` auf `main` **plus 46 uncommittierte Dateien** (Reparaturen auf das k4p9/Grok-Review, Stand 13:39 Uhr; Arbeitsbaum-Diff-Hash `47be93d3c8e8753f`, am Reviewende unverändert). Dies ist ein Review, keine Reparatur und keine Produktionsfreigabe. Kein Produktcode, kein bestehender Test und keine Rechtsgrundlage wurde geändert; eigene Probendaten wurden vollständig bereinigt (Nachweis unten).

## 1. Ergebnis

Der Review bestätigt **einen MAJOR-Fund (Betrieb) und sonst nur MINOR/INFO-Funde**; kein BLOCKER, kein KRITISCH.
Dynamisch belegt wurde: Der eigenständige Serverstart (`LmgServerMain`) wird durch eine nicht kompensierbare Alt-Saga dauerhaft blockiert — fail-closed wie nach FB-05 designed, aber ohne dokumentierten Auflösungsweg; die Blockade wurde hier manuell beseitigt (Sicherung im Belegeordner).
Die GR-01-Reparatur (Admin-Pflicht beim Storno) wurde über die echte HTTP→Service→Oracle-Kette nachgewiesen: Sachbearbeiter erhält bei vier ID-Schreibweisen konstant 403, Admin-Storno funktioniert.
Alle sieben Pflichtkombinationen sind am selben Quellstand vollständig verifiziert (1.392/1.392 Aufträge), beide Basissuiten sind vollständig grün (Hauptprojekt BUILD SUCCESS mit DB; testsweet 291/291 ohne Skip).
Die zehn GR- und sechzehn FB-Vorbefunde sind verifiziert behoben, bewusst als Modellgrenze dokumentiert (FB-04) oder als Sollzustand widerlegt-dokumentiert (FB-03); die morgendliche DB-Dialog-Reparatur (3c909c2) ist intakt.
Eine gegen denselben Stand laufende fremde Zertifikatssuite endete um 16:03 ohne grünes Zertifikat: 200/200 GLM-Fälle wurden beantwortet, die abschließende Build-Bindung verwarf die Belege wegen **paralleler Build-Aktivität** (Cursor-Testlauf ≈15:44 und das `mvn install` dieses Reviews 15:27) — Überschneidung offen gelegt, kein Codebefund; drei fachliche Findings stehen trotzdem im Protokoll (Journalzahl-Dokumentation, ein unverifizierter Modellbefund).
Grenzen: Laufzeit Temurin 25.0.4.1 (Compilerziel `--release 21`), Oracle 26ai Free (kein 19c-EE-Lauf), GLM-Kette nur anhand vorhandener Belege und der laufenden Fremdsuite geprüft (keine eigenen API-Aufrufe), BSG-Randnummern und WD-6-046/24-Wortlaut nicht selbst abgerufen.

| Bereich | Urteil im tatsächlich geprüften Umfang |
|---|---|
| Fachlichkeit | 35 unabhängige Sollrechnungen centgenau normkonform zur projekteigenen Auslegung; zwei MINOR (Listen-Vorschau Monat, Testlücke). |
| Geldfluss | Kein Fehler gefunden; Idempotenz, Monats-Eindeutigkeit, Vier-Augen, Kassenlauf-Blöcke, Storno-Rückbuchung statisch wie dynamisch (Suite-Läufe) stabil. |
| Transaktionskern | Breite Regression grün; Recovery fail-closed korrekt, aber Betriebsrisiko Alt-Saga (ZC-1) und Dokumentationswiderspruch Pool-Regel. |
| Sicherheit | GR-01 dynamisch verhalten; keine XSS-/Token-Schwäche gefunden; Objektzuständigkeit bleibt bewusste offene Modellgrenze (FB-04). |
| Tests/Zertifikate | 291/291 bzw. BUILD SUCCESS frisch; Zertifikatskette formal intakt (Hashes nachgerechnet); aktuell kein grünes Zertifikat am Stand (Überschneidung + offene GLM-Findings). |

## 2. Bestätigte Befunde

### ZC-1 — MAJOR (DYNAMISCH BELEGT) — Serverstart blockiert durch nicht kompensierbare Alt-Saga ohne Auflösungsweg

- **Ort:** `LmgServerMain.java:164-171` (`requireSuccessfulRecovery`), `SagaEngine.java:325-344` (namenloser Schritt → keine Kompensation, bleibt fehlerhaft), `SagaEngine.java:381-387` (all-PENDING-Erkennung nur bei unveränderter Schrittzahl, vgl. KA-3).
- **Vorbedingung/Auslöser (minimal, hier real eingetreten):** In der Entwickler-DB lag eine Saga vom 24.09.2026 (Typ `ANTRAGSVERARBEITUNG`, Kopf FAILED seit 13:50, Schritt 0 `PENDING`, `NAME` NULL — vor der FB-05-Namenspflege angelegt). Jeder Start von `LmgServerMain` bricht mit „Wiederanlauf beim Start fehlgeschlagen oder unvollständig; Server bleibt gestoppt" ab (Protokoll im Belegeordner, 15:50 Uhr). Der eingebettete Server der testsweet-Web-Läufe umgeht `requireSuccessfulRecovery` und startete deshalb um 14:17 normal — der Unterschied wurde erst durch diese Probe sichtbar.
- **Verletzte Regel:** Betriebsbereitschaft/Recovery: fail-closed ist richtig, aber ohne dokumentierte, wiederholbare Auflösungsprozedur wird der Schutz zur Dauerblockade (ursprüngliche GR-04-Wirkung, jetzt über den Alt-Daten-Pfad).
- **Auswirkung:** Produktiver Neustart auf einem Journal mit Altdaten aus Zeiten vor der Namenspflege ist so lange unmöglich, bis jemand manuell DB-Chirurgie betreibt. Reichweite: Betrieb/Katastrophenfall.
- **Gegenprüfung:** Die neuen Regressionen (`ProduktPendingRecoveryDbTest`, `SagaEngineTest`) decken frische PENDING-Fälle ab, nicht Alt-Journale mit `NAME` NULL; für letztere existiert keine Doku/kein Werkzeug.
- **Hier durchgeführt Auflösung (dokumentiert):** Sicherung der Zeilen in `zc1-belege/alt-saga-vor-loeschung.txt`, dann Löschen von `TX_SAGA_STEP`/`TX_SAGA` (nur diese Saga), danach Start problemlos. **Empfehlung:** Auflösungsprozedur (erkennen → sichern → terminieren) in doc/architektur.md dokumentieren oder als Admin-Werkzeug bereitstellen; Regression: Start mit eingepflanzter namenloser Alt-Saga + dokumentierter Prozedur.
- **Belege:** `zc1-belege/alt-saga-vor-loeschung.txt`, Startprotokoll 15:50 in `lmg-server-8098-fehlstart.log` (im Aufgabenprotokoll des Reviews konserviert), erneuter erfolgreicher Start 15:58.

### ZC-2 — MINOR (STATISCH) — Übersichtsliste rechnet weiter den JVM-Monat (FB-NEU-1)

`SozialLeistungsService.java:870,884-891` (`zahlbetragVorschau` mit `YearMonth.now()`): Die Vorgangsliste nennt Beträge ohne Monatsbezug und weicht damit von der monatsbindbaren Maske (FB-07-Reparatur) ab, sobald Entbindungsmonat/Satzstand monatsabhängig werden. Kein Buchungsfehler (Zahlung rechnet den Auftragsmonat). Empfehlung: Monat in der Listenantwort führen. Regression: feste Uhr Juli, Entbindung Juni — Liste mit Monatslabel vs. Maske.

### ZC-3 — MINOR (STATISCH) — Testlücke 12 %-Variante mit mehreren Kindern ohne 36 %-Konstellation (FB-NEU-2)

`LeistungsberechnungTest.java:237-255` deckt {U7=0, U16=1, mind=2} → 135,12 und {0,0,≥2} → 270,24 nicht ab; der Code (`Leistungsberechnung.java:246-248`) liefert laut unabhängiger Handprobe korrekt. Empfehlung: zwei Asserts ergänzen (Sollwerte vorab aus Norm).

### ZC-4 — MINOR (STATISCH) — Latente NPE im Auszahlungs-Mapping (WC-1)

`LmgServer.java:470-474`: `Map.of("bedarfsgemeinschaft", ergebnis.bedarfsgemeinschaft() == null ? null : bgMap(...))` — der kodierte Null-Zweig würde selbst NPE werfen. Heute unerreichbar (Service liefert stets non-null). Empfehlung: `LinkedHashMap` oder Zweig entfernen.

### ZC-5 — MINOR (STATISCH) — Bescheid-Druck mit abschließendem Schrägstrich → irreführendes 405 (WC-2)

`GET /api/bedarfsgemeinschaften/{id}/bescheid/druck/` fällt durch die Druck-Weiche und beantwortet eine GET-Anfrage mit „Methode GET … nicht unterstützt (erlaubt: GET)". Kein Sicherheitsrest (Token bleibt unberührt). Empfehlung: Trailing-Slash vor der Weiche normalisieren.

### ZC-6 — INFO (alle STATISCH)

- **KA-1:** Doku-Widerspruch Pool-Dimensionierung: `AGENTS.md` („2 × maxTasks") und `doc/architektur.md:194` vs. korrekte 3×-Regel (`LmgServerMain.java:60-64`, `architektur.md:121`). Angleichen.
- **KA-2:** Toter Zweig im Journal-Vorscan (`SagaJournal.java:246-248`) nach FB-12-Fix.
- **KA-3:** GR-04-Restfenster bei Definitionswechsel zwischen `journal.start` und erstem IN_FLIGHT (`SagaEngine.java:381-387`) — bewusst konservativ getestet; als dokumentierte Grenze führen.
- **KA-4:** 409 AUSGANG_UNBEKANNT auch für wirkungslose Lese-Tasks (`TaskHandle.java:75,93`) — konservativ, kein Falschaussagen-Risiko.
- **KA-5:** Manuelle TxManager-API ohne Produktionsaufrufer (nur Tests); Misuse-Fenster durch Pool-Idempotenz abgefedert.
- **WC-3:** Sidebar zeigt „Version 0.5.4" (`index.html:28`), Produkt 0.5.8.
- **WC-4:** AGENTS.md sagt „drei **identische** Sendeversuche"; Code hängt korrektur-anreicherte Wiederholungen an (`LlmReviewService.java:281-292`, in protocol.md ausgewiesen). Formulierung angleichen.
- **WC-5:** Doppelte Header-Setzes in `SweetServer`/`LmgServer.statisch` (Kosmetik).
- **WC-6:** `tools/evidence-sources.txt` erfasst übriges `doc/*.md` (rechtsgrundlagen.md, regressionstests.md, architektur.md) nicht — Änderungen dort entwerten keine Zertifikate; Nachvollziehbarkeitsgrenze benennen.

## 3. Offene Hypothesen, unaufgelöste Nachweise, Modellgrenzen

1. **TF29 (aus GLM-Lauf e5a829c7):** Modell bewertete Zahlung ohne Bescheid-Deckung als FAIL. Produktregel ist „Deckung nur **sobald die BG einen Bescheid hat**" — ohne jeden Bescheid also erlaubt. Kein bestätigter Fehler, aber der Prüfauftrag sollte diese Regel explizit lehren, damit das Modell sie nicht als Abweichung meldet. OFFENE HYPOTHESE (Dokumentationslücke im Prüfauftrag, kein Codefehler).
2. **TF68 (GLM + REFERENCE_2026 konform):** Erwartete Journalanzahl (3) ist aus den Falldaten nicht unabhängig herleitbar — welche Schritte Fachjournale erzeugen, ist im Prüfpaket nicht dokumentiert. Gleiche Klasse wie die morgendlichen Doku-Lücken (GR-05-Runde). OFFEN, blockiert künftige grüne Zertifikate.
3. **Vermögensverbrauchsfortschreibung bei positiver Zahlung** (FB-03-Würdigung): bleibt Produktannahme ohne umfassende rechtliche Verifizierbarkeit (BSG-Randnummern von Teilprüfer B nicht selbst abgerufen); Gegenposition ist dokumentiert.
4. **§ 30 Abs. 3 Günstigervergleich** beruht auf WD 6-046/24 (keine bindende Rechtsprechung); die engere Wortlautlesart ist in der README daneben gestellt — transparent, aber rechtlich UNVERIFIZIERT im strengen Sinne.
5. **FB-04 Objektzuständigkeit** bleibt als CR-035 B/C offen; für Betrieb mit getrennten Zuständigkeiten nicht ausreichend.
6. Suite 7b1ef5e5-Endverdict „Belege passen nicht mehr zu Build": durch parallele Builds dieser Review-Phase verursacht (eigenes `mvn install` 15:27, Cursor-Testlauf ≈15:44). **Kein Codebefund** — aber Beleg für eine Prozessregel: während einer Zertifikatssuite darf nicht parallel gebaut/ installiert werden (Empfehlung: Suite sperrt Build-Artefakte oder Dokumentation als Betriebsregel).

## 4. Abdeckungsmatrix (Kurzfassung, Details bei den Teilprüfberichten im Chatprotokoll)

| Prüffeld | Status | Evidenz |
|---|---|---|
| TxManager/Retry/Task/Saga/Recovery | GEPRÜFT (statisch voll, dynamisch via Suiten + ZC-1-Probe) | Teilprüfer A, Suite-Regressionen |
| Pools/DDL/Oracle | GEPRÜFT (statisch; kein 19c-EE-Lauf) | A |
| Fachnormen § 28/30/35/42a/44/82/90/94 | GEPRÜFT (35 eigenständige centgenaue Sollrechnungen, python) | B |
| Bescheid/Snapshot/Monatsplan/Änderung | GEPRÜFT (statisch + neue Tests) | B |
| Geldfluss-Statik (Idempotenz/Sperren/Vier-Augen/Kassenblöcke) | GEPRÜFT | B+A |
| Endpunktmatrix LmgServer/SweetServer | GEPRÜFT (vollständig abgeleitet; Auffälligkeiten ZC-4/5) | C |
| XSS/Token/Sessions/CSP | GEPRÜFT, keine Schwäche | C |
| Simulator/Verifikator/Prüfberichte | GEPRÜFT (statisch + 7 Läufe ausgewertet) | C+zentral |
| Zertifikatskette/BuildEvidence/GLM-Vertrag | GEPRÜFT (statisch; fremde Suite beobachtet; Belege nachgehasht) | C |
| GLM-API dynamisch | NICHT DYNAMISCH GEPRÜFT (keine eigenen Aufrufe; fremder Lauf 200/200 ausgewertet) | — |
| Java-21-Laufzeit / Oracle 19c EE | NICHT GEPRÜFT | — |

## 5. Nachprüfung historischer Befunde (konsolidiert)

| Vorbefund | Status | Kern-Evidenz |
|---|---|---|
| GR-20261015-01 Storno-Admin | **BEHOBEN** | statisch (`LmgServer.java:299-308,504-509`, `ProduktHttpSicherheitTest`) **und dynamisch** (dieser Review: 4 ID-Varianten → 403, Admin → 200, echte Oracle-Kette) |
| GR-02 Alleinerziehend ≥4 Kinder | **BEHOBEN** | Günstigervergleich + Deckel; Sollwerte 270,24/337,80 unabhängig bestätigt; Java/JS/Python-Referenzen synchron |
| GR-03 zentraler Warmwasserabzug | **BEHOBEN** | Abzug fest 0; Soll 1.063,00 bestätigt; Altparameter wirkungslos getestet |
| GR-04 PENDING-Saga blockiert Start | **BEHOBEN für frische Fälle; Restfenster ZC-1/KA-3** | all-PENDING → COMPENSATED; Alt-Journale ohne Namen bleiben fehlerhaft → ZC-1 |
| GR-05 Bescheid-Nachdruck Bankdaten | **BEHOBEN (neu)/TEILWEISE (Alt, dokumentiert)** | Snapshot v3 friert HTML ein; v1/v2 mit Hinweiskasten |
| GR-06 Widerspruchsbelehrung | **BEHOBEN** | § 84 Abs. 1 SGG, § 36a Abs. 2/2a SGB I, § 9a Abs. 5 OZG; gegen gesetze-im-internet.de geprüft |
| GR-07 Login-Sperrmap | **BEHOBEN** | getrennte Karten; Cleanup verdrängt keine aktive Sperre; Test 1024 Phantoms |
| GR-08 Drucktoken/Logout | **BEHOBEN** | Token an Sitzung gebunden, Logout widerruft |
| GR-09 Java/Node-Antwortvertrag | **BEHOBEN** | Paritätskorpus (41+ Fixtures) auf beiden Seiten grün |
| GR-10 SQLState 61000 Retry | **BEHOBEN** | nur ORA-54/60/8177; Negativ-Regression vorhanden |
| FB-01 README-Kurztabelle | **BEHOBEN** | Tabellen + WD-Hinweis angeglichen |
| FB-02 Abbruch nach committeter Wirkung | **BEHOBEN (Meldung)** | 409 AUSGANG_UNBEKANNT; physisches Fenster ehrlich offen |
| FB-03 deckendes Vermögen | **WIDERLEGT (Soll); Verhalten bewusst** | BSG-Kontra dokumentiert; Test fixiert Produktannahme |
| FB-04 Objektzuständigkeit | **NOCH VORHANDEN (Modellgrenze CR-035 B/C)** | — |
| FB-05 Schrittindex-Kompensation | **BEHOBEN** | Namen im Journal, Recovery über Namen |
| FB-06 IN-Liste | **BEHOBEN (19c-konservativ)** | 1000er-Blöcke, Mengenabgleich, echte 1001-Zeilen-Regression |
| FB-07 Vorschau-Monat | **BEHOBEN (Maske); Rest ZC-2 (Liste)** | `?monat=`, Validierung, UI-Reload |
| FB-08 Bescheid ersetzen | **BEHOBEN (technisch)** | atomar heben+neu erteilen unter Guards; Verfahrensrecht bewusst offen |
| FB-09 Kinderzähler | **BEHOBEN (Validierung)** | 0≤U7≤U16≤mind≤99; Mehrfachbeanspruchung → VALIDATION |
| FB-10 Kontensegment in Fehler | **BEHOBEN** | Meldungen nennen Position/Format/Rollen |
| FB-11 Write-ahead-Quittung | **BEHOBEN** | unklarer Ausgang → FAILED mit Payload, wiederanlauffähig |
| FB-12 Payload-Vorscan | **BEHOBEN** | Kopfzeilen ohne Payload; Lesen erst unter Claim |
| FB-13 leere Replay-Bankfelder | **KEIN FEHLER (bestätigt)** | Overrides optional; Oracle-Regression vorhanden |
| FB-14 Service-Storno ohne Rolle | **KEINE UMGEHUNG (bestätigt)** | HTTP prüft doppelt; direkter Java-Aufruf innerhalb Vertrauensgrenze |
| FB-15 Snapshot-Eingaben | **BEHOBEN (neu; Alt lesbar, Grenzen dokumentiert)** | Merkmale/Zähler/KdU/Absetzungen/Version roundtrip |
| FB-16 Frame-Schutz | **BEHOBEN** | `frame-ancestors 'none'` auf allen Antwortklassen |
| CR-035 D (Zugriffsprotokoll) | intakt | Listen ohne Bankdaten (C) |
| 3c909c2 DB-Dialog Suite-Start | **INTAKT** | Endpunkt + GUI-Auswertung unverändert (C) |

## 6. Priorisierte nächste Schritte

1. **ZC-1:** Auflösungsprozedur für nicht kompensierbare (Alt-)Sagas dokumentieren/Werkzeug schaffen — Betrieb, klein.
2. **GLM-Prüfpaket:** Journal-Erzeugungsregel (TF68) und Bescheid-Deckungsregel „ohne Bescheid" (TF29) in Prüfauftrag/Prüfmenge-Doku aufnehmen; dann Zertifikatssuite **ungestört** (keine parallelen Builds) neu fahren — das ist der nächste grüne Nachweis.
3. ZC-2/ZC-3/ZC-4/ZC-5 klein nachziehen; Doku-Angleichung (KA-1, WC-3, WC-4).
4. Prozessregel: während Zertifikatsläufen keine parallelen mvn-Installationen (heutige Überschneidung als Lehrstück dokumentieren).

## 7. Anhang — Prüfstand, Befehle, frische Laufresultate

- **Prüfstand:** Windows 11, Temurin **25.0.4.1**, Maven 3.9.6, Compilerziel `--release 21`; Docker `lmg-oracle` (Oracle AI Database 26ai Free, PDB FREEPDB1, Dev-Schema); Commit `37abfb6` + 46 uncommittierte Dateien (Diff-Hash `47be93d3c8e8753f` vor/nach identisch — nur die Review-Belege kamen unversioniert dazu). Konkurrierende Aktivität während des Reviews: Zertifikatssuite 7b1ef5e5 (14:14–16:03), Cursor-Testläufe (Reports bis 15:44), eigenes `mvn install` 15:27.
- **Basissuiten:** `mvn install` mit DB-Umgebung: **BUILD SUCCESS** (Exit 0; ≈351 Tests). `testsweet mvn test` mit DB: **291 Tests, 0 Fehler, 0 Skip** (ohne DB: 288/0/0, 26 DB-Skips). Ein zwischenzeitlich in fremden Reports sichtbarer `ProzessKillRecoveryDbTest`-Failure gehörte zum parallelen Cursor-Lauf (unter Lastflake, dort danach grün) — nicht dem Stand zurechenbar.
- **Pflichtkombinationen** (Läufe 14:17–14:25, an denselben Quellstand gebunden): WEB 100/100, 100/100, 30/30; FACH 500/500, 500/500, 120/120, 42/42 — **1.392/1.392 verifiziert**.
- **GR-01-Gegenprobe** (eigener Server 8098, echte DB): Anlage/Freigabe OK; SB-Storno 403 bei `64016`, `+64016`, `%2B64016`, `00064016`; Admin 200; Probendaten restlos bereinigt (`gr01-cleanup.sql`, Kontrollzählung 0). Belege: `zc1-belege/gr01-storno-probe*.txt/.sh`.
- **Alt-Saga:** Sicherung + Löschung dokumentiert in `zc1-belege/alt-saga-vor-loeschung.txt`; danach Start problemlos.
- **Fremd-Suite 7b1ef5e5:** Phasen bis GLM vollständig; GLM 200/200 beantwortet (Retry-Vertrag mit gezielten Korrekturanforderungen beobachtet); Endverdict UNVERIFIED („Belege passen nicht mehr zu Quelldateien oder Build") — Ursache: parallele Builds; 3 Findings ausgewertet (§ 3).
- **GLM-Vorbeleg 12:02** (llm-review-ec2fa5e3, PASS 18/18): alle Manifest-/Batch-/Request-Hashes von Teilprüfer C unabhängig nachgerechnet — formal konsistent, für den heutigen Stand korrekt entwertet.
- Teilprüfberichte A/B/C (Kern/Oracle, Fachrecht/Geldfluss, Web/Sicherheit/Zertifikat) liegen im Chatprotokoll dieses Reviews; Belege unter `grundsicherung-gesamt-review-2026-10-15-zc1-belege/`.
