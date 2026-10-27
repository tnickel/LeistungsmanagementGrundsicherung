# Codereview: testsweet — Gesamtprüfung des Mitarbeiter-Simulators

## Kopf

- **Prüfer/Modell:** nvidia/nemotron-3.5-super-vl:free (dieser Bericht steht für sich allein)
- **Prüfdatum:** 2026-10-27
- **Geprüfter Commit-Stand:** 1555854 ("testsweet-README: ECHT- und RECHNER-Kategorien mit Quellen dokumentiert"), Arbeitskopie unverändert; keine lokalen Änderungen.
- **Kurzverdict:** **BEDINGT** — testsweet hat starke Anti-Falsch-Grün-Kontrollen (globale SQL-Verifikation, Vorher/Nachher-Fachzustand, erwartete-Fehler-Verweigerung, build-hash-gebundene Zertifikate, statische Pfad-Traversal-Schutz, keine Passwörter in Berichten). Die zwei zentralen Restrisiken sind (1) produktionsrechner-abhängige Sollwerte für die meisten generierten Fälle (Berechnungsdefekt kann beide Seiten grün machen) und (2) ein fester "Ersatzkollege" im Ein-Mitarbeiter-Fachschicht-Pfad, der Vier-Augen-Fälle ohne echte Zweitperson grün lassen kann. Zertifikat deckt ECHT/RECHNER nicht ab (dokumentiert).

## Vergleichskopf (5 wichtigste Funde, je 1 Zeile)

1. CR-V01: Die meisten Sollwerte generierter Fälle kommen aus derselben `Leistungsberechnung`, die auch das Produkt rechnet — ein Berechnungsdefekt kann testsweet grün lassen.
2. CR-V02: Ein-Mitarbeiter-Fachschicht-Läufe nutzen den festen, nicht existenten Kollegen "sb-sim-ersatz" für Vier-Augen-Freigaben — keine echte Zweitperson geprüft.
3. CR-V03: SweetServer hat keine Authentifizierung und akzeptiert `Origin == null`; jeder lokale Prozess kann `/api/start`, `/api/stop`, `/api/zertifikat` etc. ansteuern (dokumentierte Lokalgrenze).
4. CR-V04: Das Zertifikat deckt bewusst keine ECHT- (5) und RECHNER-Fälle (12) ab; ein "BESTANDEN" zertifiziert diese nicht.
5. CR-V05: Kein Tautologie-Fund, keine verschluckte globale Verifikation, keine traversierbaren statischen Pfade, keine XSS in den geprüften UI-Pfaden.

## Fundtabelle (CR-V<NN>)

| # | Schweregrad | Fundort (Datei:Zeile) | Beschreibung | Beweis | Behebungsempfehlung |
|---|-------------|----------------------|--------------|--------|---------------------|
| CR-V01 | 🟠 KRITISCH | `testsweet/src/main/java/de/allgemein/testsweet/SzenarioBibliothek.java:17-20` (Klassendokumentation), `SzenarioBibliothek.java:1048-1144` (Generatorkörper), `WebHelfer.java:63-69` | Erwartete Zahlbeträge der meisten POSITIV/KOMBINATION-Fälle werden mit derselben `de.allgemein.lmg.calc.Leistungsberechnung` ermittelt, die auch der Produkt-Service nutzt (`SozialLeistungsService`). Ein systematischer Berechnungsdefekt tritt auf Erwartungs- und DB-Seite identisch auf und bleibt grün. Die unabhängige BigDecimal-Nachrechnung im Verifikator prüft nur dokumentierte Rechenblöcke, nicht jeden Fall. | `SzenarioBibliothek.java:17-20`: "Die erwarteten Zahlbeträge werden vorab mit derselben puren Berechnung … ermittelt, mit der später die Fachschicht rechnet". Hand-gerechnete Grenzfälle (13–15, 17) sind davon ausgenommen (`SzenarioBibliothek.java:1124-1133`), nicht aber Fälle 11, 12, 16, 18, 19 und der Zufallsgenerator (`default -> result.zahlbetrag()`). | Sollwerte für KOMBINATION/POSITIV-Fälle aus einer unabhängigen Referenzimplementierung (nicht `Leistungsberechnung`) oder zumindest aus festen Handwerten beziehen, analog zu den Grenzfällen. Alternativ: für jede Kategorie einen Anteil an hand-gerechneten Fällen verbindlich vorschreiben. |
| CR-V02 | 🟠 KRITISCH | `testsweet/src/main/java/de/allgemein/testsweet/Simulator.java:1367-1380` (`zufallsKollege`), `Simulator.java:1330-1340` (`zufallsWebKollege` analog), `SzenarioBibliothek.java:385-392` | Ein-Mitarbeiter-Fachschicht-Läufe geben für die Vier-Augen-Freigabe den fiktiven Kollegen "sb-sim-ersatz" zurück, statt eines echten zweiten Mitarbeiters. Wenn das Produkt nur prüft, dass Freigabe- und Anlegeperson differieren (und nicht, dass der Freigabegeber ein authentisierter, berechtigter Kollege ist), können Ein-Mitarbeiter-Fachschicht-Läufe Vier-Augen-Szenarien grün durchlaufen, ohne echte Zweitperson. | `Simulator.java:1369`: `return "sb-sim-ersatz"; // Einzelgänger: fester Ersatzkollege für Vier-Augen`. Der Web-Pfad nutzt `sb-02` als Ersatz, erstellt aber laut `WebSzenarioBibliothek` wenigstens zwei Konten; der Fachschicht-Pfad hat keinen zweiten echten Account. | Mindestens zwei echte Mitarbeiter-Accounts auch im Ein-Mitarbeiter-Fachschicht-Modus anlegen und einen zufälligen echten Kollegen wählen; andernfalls den Lauf als "nicht vier-augen-geprüft" kennzeichnen und im Bericht ausweisen. |
| CR-V03 | 🟡 MITTEL | `testsweet/src/main/java/de/allgemein/testsweet/SweetServer.java:65-73`, `SweetServer.java:656-666`, Klassenkopf `SweetServer.java:20-29` | Der SweetServer bindet an `127.0.0.1` ohne Authentifizierung/Autorisierung. Host/Origin-Schutz schützt vor fremden Browsern, akzeptiert aber `Origin == null`; jeder lokale Prozess oder lokal angemeldete Nutzer kann `/api/start`, `/api/stop`, `/api/llm/config`, `/api/zertifikat` und Berichts-Endpunkte aufrufen und auslesen. Das ist akzeptabel nur unter expliziter Lokal-Betriebsgrenze. | `SweetServer.java:664-666`: `Origin == null` wird akzeptiert; keine Sitzung/Token an Endpunkten. Doku: lokaler Entwicklungsserver ohne Login. | Einsatzgrenze dokumentieren (loopback-only, lokal vertrauenswürdig), optional einfache Token/Bindungsprüfung für Schreib-Endpunkte (`/api/start`, `/api/zertifikat`, LLM-Config) einführen. |
| CR-V04 | 🟡 MITTEL | `testsweet/src/main/java/de/allgemein/testsweet/CertificateSuiteWorker.java:12-16`, `AGENTS.md:26-29` und `44-49`, `testsweet/README.md:237-240` | Das Zertifikat deckt bewusst keine ECHT- (5 quellenbasierte Fälle) und RECHNER-Fälle (12 externe Vergleichsfälle) ab. Ein grünes "BESTANDEN" zertifiziert nur die sieben konfigurierten Fachschicht-Kombinationen plus eine POSITIV/NEGATIV-LLM-Stichprobe; das ist dokumentiert, aber operativ leicht falsch lesbar als Gesamtzertifikat. | `CertificateSuiteWorker.java:12-16`: Matrix aus sieben Jobs, ECHT/RECHNER explizit ausgenommen. `AGENTS.md`: "ECHT/RECHNER gehören nicht zur automatischen Zertifikatssuite". | Zertifikatsausgabe und GUI klar als "Teilzertifikat (ohne ECHT/RECHNER)" kennzeichnen; ggf. optionalen separaten Nachweis für ECHT/RECHNER ausweisen. |
| CR-V05 | ⚪ GERING | `testsweet/src/main/java/de/allgemein/testsweet/SzenarioAktion.java:44-47` | Die Grundübereinstimmung der erwarteten Fehlergründe ist substring-basiert (`contains`). Zu generische Erwartungsfragmente könnten zu leicht übereinstimmen und erwartete Fehler zu schnell als "abgewehrt" werten. | `SzenarioAktion.java:44-47`: `fehler.getMessage()::contains`. | Fragment-Matching dokumentiert als toleranter Vergleich; für sicherheitsrelevante Fehlergründe exakte Prüffelder bevorzugen oder Caller zwingen, spezifische Fragmente zu wählen. |
| CR-V06 | ⚪ GERING | `testsweet/src/main/java/de/allgemein/testsweet/Fachzustand.java:65-88` | Die Journal-Ausnahme für neue Einträge ist nur prefix-basiert auf `AKTION_TEXT` starting with `"Einsicht: "`. Ein künftiger Produkt-Code, der beliebige neue Journalzeilen unter diesem Präfix schreibt, würde die Negativfall-Unveränderlichkeitsprüfung umgehen. | `Fachzustand.java:80-87`: nur `AKTION_TEXT` beginnt mit `"Einsicht: "` wird als erlaubte neue Zeile akzeptiert. | Ausnahme auf eine explizite Journal-Aktion/Typ beschränken (nicht freier Text-Präfix), oder zusätzlichen Hash-Abgleich über erlaubte Audit-Spalten. |
| CR-V07 | ⚪ GERING | `testsweet/src/main/java/de/allgemein/testsweet/SweetMain.java:105-133` | Unbekannte erste CLI-Befehle fallen still in den GUI-Modus durch (z. B. `rnu` statt `run` startet den Server statt eines CLI-Fehlers). Kein falsch-grüner Pfad, aber operationell verwirrend. | `SweetMain.java:105-133`: nur `certificate-worker`, `run`, `web/run-web` erkannt, alles andere → GUI. | Unbekannte Befehle mit CLI-Nutzungshinweis und Exit-Code 2 ablehnen. |
| CR-V08 | ⚪ GERING | `testsweet/src/main/java/de/allgemein/testsweet/SweetPdf.java:206-253` | `ladeBildDaten()` akzeptiert beliebige relative Pfade. Aktuelle Aufrufer nutzen feste Report-Assets; bei zukünftigen nutzergesteuerten Aufrufern Pfad-Traversal möglich. | `SweetPdf.java:206-253`: direkter Schlüssel- und relativer-Pfad-Versuch ohne Container-Prüfung. | Bildlade-Pfade auf ein Allowlist/Verzeichnis beschränken, analog zu `SweetServer.statischeDatei` (`SweetServer.java:1009-1048`). |

## Entlastungen (widerlegte Verdachte)

| # | Verdacht | Begründung |
|---|----------|------------|
| [widerlegt] CR-002: Passwörter landen im Bericht | `MdBericht.java:319-333` filtert passwortähnliche Kontextschlüssel aus dem Markdown. Behoben. |
| [widerlegt] CR-010: HTTP-Fehlercodes werden verschluckt | `SweetServer.java:982-1006` reicht den übermittelten Statuscode an `sendResponseHeaders` weiter. Behoben. |
| [widerlegt] CR-025: Statische Dateiauslieferung ist traversierbar | `SweetServer.java:1009-1048` weist `..` zurück, nutzt Allowlist, Pfadnormalisierung und `toRealPath()`-Containment. Behoben. |
| [widerlegt] Tautologische Enderfolgprüfung | `laufErfolgreich()` (`Simulator.java:1094-1105`) verlangt `Phase.FERTIG`, keine Evidence-Fehler, alle Aufträge `VERIFIZIERT` und alle globalen Prüfungen erfolgreich. |
| [widerlegt] Globale Verifikation wird bloß geloggt | `GlobaleVerifikation.java:60-98` wandelt SQL-Exceptions in fehlgeschlagene Prüfungen um; `Simulator.java:1203-1232` propagiert globale Fehler in `VERIFIKATION_FEHLGESCHLAGEN`. |
| [widerlegt] Negativfall besteht bei normaler Rückgabe | `SzenarioAktion.java:49-66`: eine Negativ-Aktion, die normal zurückkehrt, wird explizit fehlgeschlagen; `schrittMitErwartetemFehler` erfordert `FachlicheException`. |
| [widerlegt] Stress-Barriere gibt Welle 2 früh frei | `Simulator.java:1298-1323`: `CountDownLatch`-Logik ist konservativ; Welle 2 wartet auf Abschluss von Welle 1. |
| [widerlegt] Browser-Prozess-Reinigung greift fremde Chrome-Profile an | `BrowserProzesse.java:26-44`, `110-133`: nur registrierte eigene Profile mit UUID-Marker und exaktem `--user-data-dir`; Fremd-Prozesse nicht betroffen. |

## Testlücken (L-<NN>)

| # | Vorgeschlagener Test | Klasse + Szenario |
|---|----------------------|-------------------|
| L-01 | Ein-Mitarbeiter-Fachschicht-Lauf mit Vier-Awen-Szenario schlägt fehl, wenn der Ersatzkollege nicht als echter, berechtigter Freigabegeber akzeptiert wird (oder Lauf wird als nicht-vier-augen-geprüft markiert). | `SimulatorTest` (neu), `SzenarioBibliothek` Ein-Mitarbeiter-Modus. |
| L-02 | Mutations-Test: Ein defekter `Leistungsberechnung`-Zweig (z. B. falscher Erwerbsfreibetrag) muss einen generierten POSITIV-Fall rot werden lassen, wenn Sollwerte unabhängig wären. Zeigt CR-V01. | `SzenarioBibliothekTest` (neu) mit gemocktem/ersetztem Rechner. |
| L-03 | `SzenarioAktion` erwartet substring-freundliche, aber zu generische Fehlergründe (z. B. nur "Fehler") werden als zu schwach abgelehnt oder zumindest markiert. | `SzenarioAktionTest` (neu). |
| L-04 | `SweetServer`: POST-Endpunkte ohne Origin/Host (`Origin == null`) und ohne Token werden von lokalem Prozess akzeptiert — Integrationsprüfung. | `SweetServerHttpTest` (erweitern). |
| L-05 | `Fachzustand`: neue Journalzeile mit Präfix `"Einsicht: "` aber fremdem Inhalt passiert die Unveränderlichkeitsprüfung. | `FachzustandTest` (neu). |
| L-06 | `SweetMain`: unbekannter CLI-Befehl gibt Exit-Code 2 statt GUI. | `SweetMainTest` (neu). |
| L-07 | Server-Endpunkte der neuen Features (LLM-Panel, Suite-Panel, Zertifikat-Export) fehlen systematische HTTP-Tests (400/404/405/409/503, Guards). | `SweetServerHttpTest` (neue Endpunkte). |

## Nachrechenprotokolle (SGB XII 2026, wie gefordert)

Die festen Grenzfälle 13–15 und 17 (`SzenarioBibliothek.java:1124-1133`) sind handgerechnet dokumentiert:

- **Fall 13:** `2822,24 €` = 563 (RBS) + 270,24 (Mehrbedarf?) + 300 + 357 + 390 + 2 × 471. Prüfsumme: 563 + 270,24 + 300 + 357 + 390 + 942 = 2822,24 ✓.
- **Fall 14:** `3312,80 €` = 563 + 337,80 + 300 + 3 × 390 + 2 × 471 = 563 + 337,80 + 300 + 1170 + 942 = 3312,80 ✓.
- **Fall 15:** `3750,80 €` = 563 + 337,80 + 300 + 357 + 2 × 390 + 3 × 471 = 563 + 337,80 + 300 + 357 + 780 + 1413 = 3750,80 ✓.
- **Fall 17:** `1063,00 €` = 563 + 350 + 50 + 100 = 1063,00 ✓ (Altparameter 20 % ohne Abzug).

Relevante SGB-XII-Konstanten 2026 (wie im Review-Auftrag): RBS-Stufen 563 / 506 / 451 / 471 / 390 / 357 €; G-Mehrbedarf 17 % = 95,71 € (von 563 €); Erwerbsfreibetrag 30 %, Deckel 281,50 €; Schonvermögen 10.000 € je Volljährigem.

**Hinweis:** Die Dokumentation verweist auf `doc/belege/echt-und-rechner-faelle-2026-10-27.md` für ECHT/RECHNER-Sollwerte. Diese Datei wurde im Repository nicht unter `doc/belege/` gefunden (glob lieferte keine Treffer unter `doc/belege`); die Quellenangaben (Anlage § 28 SGB XII, BSG B 8 SO 4/24 R, Altenburger KdU-Richtlinie 27.05.2026, steuerschroeder.de 03.07.2026) könnten nicht im Repo verankert sein — Empfehlung: Existenz und Zitiergenauigkeit der Belegdatei prüfen (L-08, optional).

## Blinde-Grün-Analyse (drei realistischste Wege)

1. **Produktionsrechner-Kopie (CR-V01):** Ein Defekt in `Leistungsberechnung` (z. B. falscher Erwerbsfreibetrag oder Mehrbedarf) wird identisch in Erwartung (`SzenarioBibliothek`) und DB-Ausgang reproduziert. Die unabhängige Nachrechnung prüft nur dokumentierte Blöcke, nicht jeden generierten Fall → Lauf grün, Fach falsch. Gegenmaßnahme: mehr hand-gerechnete Sollwerte / unabhängige Referenz.
2. **Fester Ersatzkollege (CR-V02):** Ein-Mitarbeiter-Fachschicht nutzt "sb-sim-ersatz" für Vier-Augen. Prüft das Produkt nur `ersteller <> freigeber`, passiert eine freigegebene Zahlung ohne echte Zweitperson und der Lauf bleibt grün. Gegenmaßnahme: zwei echte Accounts oder explizite Kennzeichnung.
3. **Zertifikat liest man als Gesamtzertifikat (CR-V04):** Ein "BESTANDEN"-Zertifikat deckt nur sieben Fachschicht-Kombinationen plus LLM-Stichprobe ab, nicht ECHT/RECHNER (17 Fälle). Operativ könnte man fälschlich schließen, das gesamte Leistungsspektrum sei zertifiziert. Gegenmaßnahme: klare "Teilzertifikat"-Kennzeichnung im Export.

## Verdict

**BEDINGT** — Der Simulator ist solide gegen falsch-grüne Läufe abgesichert (globale SQL-Verifikation, Vorher/Nachher, erwartete-Fehler-Verweigerung, Build-Hash-Bindung, keine Passwörter in Berichten). Die drei wichtigsten Empfehlungen:

1. **Sollwerte entkoppeln:** Erwartete Beträge der meisten generierten Fälle nicht aus derselben `Leistungsberechnung` ableiten wie das Produkt, sondern aus unabhängiger Referenz oder festen Handwerten (bekämpft CR-V01, das größte Restrisiko).
2. **Echte Vier-Augen-Personen:** Auch im Ein-Mitarbeiter-Fachschicht-Modus zwei echte Mitarbeiter-Accounts bereitstellen oder den Lauf als nicht-vier-augen-geprüft kennzeichnen (bekämpft CR-V02).
3. **Zertifikats-Scope sichtbar:** Zertifikat und GUI explizit als Teilzertifikat ohne ECHT/RECHNER kennzeichnen (bekämpft CR-V04).

Zusätzlich: SweetServer-Betriebsgrenze (loopback, lokal vertrauenswürdig) dokumentieren und `substring`-basierte Fehlergrund-Prüfung gegen spezifischere Felder absichern.
