# Vollständiger Codereview: testsweet (Gesamtprüfung des Mitarbeiter-Simulators)

**Modell:** GLM-5.3 (ZCode-Agent, gleiche Modellfamilie wie die LLM-Nachrechnung der GUI —
als Transparenzhinweis gewertet, nicht als Ausschlusskriterium)
**Datum:** 2026-10-27 · **Geprüfter Stand:** Branch `learning`, Commits `35da248`, `0203134`,
`64ad36e`, `8886ccb`, `1555854` (auf Basis `4f16245`)
**Prüfumfang:** Prüffelder A–L des Vollreview-Prompts (`testsweet-vollreview-prompt-2026-10-27.md`);
tiefe Codelektüre von Verifikationskern, Auftraggerzeugung, Server, LLM-Schlüsselbehandlung,
Beweiskette, Berichtswesen und Betrieb; Nachrechnung aller dokumentierten Handwerte.

## Vergleichskopf (die 5 wichtigsten Funde)

1. **CR-V04 [MITTEL]** — Management-Testreport zählt Teillauf-Surefire-Zahlen als
   „Unit-/Regressionstests" (aktuell 395/334 von Teilläufen statt Voll-Suite).
2. **CR-V01 [MITTEL]** — Zustandsvergleich der Negativfälle kennt genau 5 hartkodierte
   Fachtabellen; neue Fachtabellen blieben unsichtbar, kein Sync-Test.
3. **CR-V05 [MITTEL]** — Report-Zertifikatsseite kann „BESTANDEN" zeigen, wenn nur ein
   Teil der Kategorien je gelaufen ist; 0-verifizierte Abstürze gelten als „ABGEBROCHEN".
4. **CR-V03 [GERING]** — PDF-Berichte ersetzen Nicht-CP1252-Zeichen (13× „→" im
   Szenario-Code) still durch „?".
5. **CR-V06 [GERING]** — TOC-Seitenzahl-Anker des Reports können still „null" liefern,
   ohne dass die Erzeugung fehlschlägt.

---

## 1. Fundtabelle

| CR | Schwere | Fundort | Befund | Behebung |
|----|---------|---------|--------|----------|
| CR-V01 | MITTEL | `Fachzustand.java:39` | Der Vorher/Nachher-Vergleich abgewehrter Schritte liest genau `BEDARFSGEMEINSCHAFT, SOZIAL_FALL, ZAHLUNG, BESCHEID, JOURNAL`. Kommt eine neue FACHTabelle (oder fachwirksame Spalte außerhalb dieser fünf) hinzu, bleibt jede Änderung in ihr im „unverändert"-Beweis unsichtbar — ein Abwehrfehler, der dorthin bucht, würde grün bleiben. Kein Test synchronisiert die Liste gegen `ddl/lmg_schema.sql`. | Liste zentral ableiten (DDL-Parsing) oder Sync-Test „jede Fachtabelle der DDL ist in der Vergleichsliste" ergänzen; bei Erweiterung um Tabellen scheitern lassen (fail-closed). |
| CR-V02 | MITTEL | `Verifikator.java:111-117`, `LlmCalculationReference` | Die Prüfung heißt „Unabhängige Nachrechnung sämtlicher Rechenblöcke (Referenz 2026)". Tatsächlich ist es eine Zweitimplementierung im selben Quellbaum mit derselben Fachauslegung (BigDecimal). Auf Implementierungsebene unabhängig, fachlich aber Quell-Zirkel: Teilen beide dieselbe falsche §-Lesart, bleibt der Lauf grün. Die Rechenblock-Hinweise sind ehrlich („mit derselben Berechnung wie die Fachschicht"), der Prüfungsname und Teile der Doku (README „unabhängige Referenz") übertreiben die Unabhängigkeit. | Prüfungsname relativieren („Zweitimplementierung (BigDecimal-Referenz)") ODER mind. eine Kennzahl im Bericht ausweisen, wie viel der Sollwert-Menge extern belegt ist (GoldWerte/GLM/ECHT/RECHNER vs. Referenz). |
| CR-V03 | GERING | `SweetPdf.java:444` (`getBytes(CP1252)`), `SzenarioBibliothek.java` (13× „→", z. B. Zeile 161) | PDF-Streams werden nach CP1252 kodiert; Zeichen außerhalb („→" in Rechenblock-Titeln wie „Miete 300 → 350") ersetzt Java still durch „?". Die PDF-Beweiskopie weicht damit vom protokollierten Ist-Text ab, ohne Warnung; die MD bleibt korrekt. | Entweder Ersetzungsklasse `REPORT` mit Warnlog je ersetztem Zeichen, oder Sonderzeichen im PDF-Font/WinAnsi vermeiden („->"), oder Kopplung an einen Postcheck. |
| CR-V04 | MITTEL | `doc/testreport-werkzeug/runs.js` (`junitStand`), realer Nachweis im heutigen Report (395/334) | `junitStand` summiert ALLE `surefire-reports/*.xml` im `target`. Surefire entfernt alte XMLs von Teilläufen nicht vollständig; nach `mvn test -Dtest='…'` (wie heute zweimal geschehen) zeigt der Management-Report die Teillauf-Summe (z. B. 13 Tests) bzw. Mischbestände als „Unit-/Regressionstests … BESTANDEN" — ohne Kennzeichnung „Teillauf". Management-Schlussfolgerungen auf falscher Grundlage möglich. | Zähler an den laufgebundenen Nachweis binden (`test-evidence.json` referenziert runId → nur XMLs dieses Laufs zählen) oder mtime-Filter/Laufgröße plausibilisieren; Teilläufe ausweisen. |
| CR-V05 | MITTEL | `doc/testreport-werkzeug/publish-report.js` (`zertifikatsSeite`, `runs.letzteLaeufe`) | Das Gesamturteil der Report-Zertifikatsseite lautet „BESTANDEN", sobald ALLE VORHANDENEN ausgewerteten Läufe erfolgreich sind. Fehlende Kategorien (z. B. WEB nie gelaufen) schmälern das Urteil nicht; ein fachlich total gescheiterter Lauf mit 0 verifizierten Fällen erscheint als „ABGEBROCHEN" (verifiziert=0 && !erfolgreich) und wird im Urteil wie ein nichtriger Abbruch behandelt. Kapitel 7 zeigt die Lücken zwar, aber die Urteilszeile steht allein. | Urteilszeile nur „BESTANDEN" wenn (a) jede Kernkategorie mind. einmal erfolgreich existiert und (b) fehlende/abgebrochene Ebenen namentlich benannt werden („BESTANDEN — ohne Web-Prüflauf"); Differenzierung Abbruch vs. Fach-GAU über `run-state.json` (message/state). |
| CR-V06 | GERING | `publish-report.js` (`SUCHER`, `seitenErmitteln`) | Findet ein zeilenverankelter Anker eine Kapitelüberschrift nicht (z. B. nach Umbenennung, anderer Umbruch), liefert `seitenErmitteln` still `null` und das TOC zeigt „null" — die Erzeugung gilt dennoch als READY. Der Zwei-Durchlauf hat keine Absicherung gegen Anker-Misslingen. | Nach `seitenErmitteln` hart prüfen: alle Seitenzahlen nicht-null und monoton steigend, sonst Fehler („TOC-Anker fehlgeschlagen") und Abbruch. |
| CR-V07 | GERING | `GlobaleVerifikation.java:12-52` | Keine laufweite Prüfung „keine zwei aktiven Zahlungen je BG+Monat"; das bleibt Einzelszenarien und dem DB-Unique-Index überlassen. Bewusste Aufteilung, aber als globale Erwartung wäre sie billig (eine COUNT-Abfrage) und würde Index-Regressionen sofort zeigen. | Globalprüfung ergänzen: `COUNT(*) FROM ZAHLUNG z WHERE ZAHLUNGSSTATUS IN ('ANGELEGT','BEREIT','UEBERGEBEN') GROUP BY BG_ID, ZAHLUNGSMONAT HAVING COUNT(*)>1` = 0. |
| CR-V08 | GERING | `Simulator.stoppe()` (mit `webUnzulaessig`-Fix), `starteWeb` | Kontrafaktik zum heutigen Fix: Scheitert ein Web-Lauf NACH Treiber-/Fensteranlage, ABER VOR Zuweisung von `webMitarbeiter`, räumt `stoppe()` nicht auf (Liste leer). Theoretisch möglich; die окнаlosen Fachläufe sind der Regelfall und der Fix dafür richtig (8.171 ms → 23 ms gemessen). | Beim Web-Laufstart Fenster-/Profil-Liste führen und in `stoppe()` zusätzlich aufräumen, wenn der letzte Lauf ein Web-Lauf war (Flag statt Liste). |
| CR-V09 | INFO | Vorfall-Doku 13./14.10.2026 (zwei Web-Läufe starben „nach Vorgang anlegen"); `WebMitarbeiter` | Der eingefrorene-Renderer-Vorfall ist bislang ohne Wurzelursache; die heutigen 2 „ABGEBROCHEN"-Zeilen im Report sind dessen Folge. Robustheit (Treiber-Neustart, weniger Fenster) existiert; ein Schritt-Timeout, das einen hängenden Schritt abbricht und den Auftrag FEHLGESCHLAGEN (statt Endlos-Hang) macht, ist nicht erkennbar. | Schrittweises Timeout im Web-Mitarbeiter ergänzen (Wachhund-Analogie zum Produkt-TaskController); Vorfall damit reproduzierbar machen oder schließen. |
| CR-V10 | INFO | Zertifikatsmatrix (`certificate-evidence.js:7` MATRIX) und Suite-Jobs | ECHT/RECHNER sind nicht Teil der Zertifikatsmatrix und der 1.392er-Suite; die GLM-Stichprobe rechnet sie nicht nach. Als bewusste Entscheidung vertretbar (die Kategorien tragen externe Sollwerte selbst), aber das Testzertifikat stellt so den größten unabhängigen Fundus nicht unter den formalen Nachweis. | Optional: Suite-Jobs um ECHT/RECHNER erweitern (Läufe sind in Sekunden durchlaufen) und MATRIX um zwei Einträge — Summe dann 1.409. |
| CR-V11 | INFO | `doc/SDN-Leistung-LMG-Testreport.{pdf,docx}` im Repo | Committete Ergebnis-PDFs veralten zwangsläufig; der Verify weist sie beim Abruf ab, die Repo-Kopie bleibt aber als alter Stand sichtbar. Nachweischarakter („so sah der Report am … aus") ist vertretbar, sollte aber im README des Werkzeugs gesagt sein. | Ein Satz im Werkzeug-README; alternativ nur Quellwerkzeug committen und PDFs ignorieren. |

## 2. Entlastungen (widerlegte Verdichte)

| CR | Verdacht | Widerlegung |
|----|----------|-------------|
| CR-V20 [widerlegt] | Kein Auth/CSRF-Schutz: böse Webseite könnte per JSON-POST Läufe starten | `SweetServer.java:83-86` leitet JEDE Bearbeitung durch `localRequest` (`:661-667`): Host muss `127.0.0.1:port`/`localhost:port` sein UND Origin (bei cross-site fetch immer gesendet) muss derselben Quelle entsprechen — Cross-Site-Simple-Requests werden mit 403 abgewiesen, bevor irgendein Handler läuft. Restrisiko bleibt der lokale Angreiferprozess (gleiches Gerät) — für ein Loopback-Entwicklerwerkzeug akzeptiertes Design. |
| CR-V21 [widerlegt] | Kombinationsgenerator könnte bei erschöpftem Suchraum endlos ziehen | `SzenarioBibliothek.java:1159` `for (versuch < 5000)` mit `continue` bei Kollision/Unbrauchbarkeit und hartem `throw new IllegalStateException("Keine weitere eindeutige …-Kombination gefunden")` (`:1487`, `:2016`) — fail-fast, kein stilles Duplikat. |
| CR-V22 [widerlegt] | Pfad-Traversal über statische Dateiauslieferung, Gefahr für `llm.local.properties` | `statischeDatei`: Whitelist (/, index.html, app.js, style.css, assets/Bildtypen), jede `..`-Sequenz 404; bewusst KEIN Fallback auf den Projektordner (Kommentar nennt die Schlüsseldatei). |
| CR-V23 [widerlegt] | LLM-API-Schlüssel landet in Protokollen/Antworten | `LlmReviewService` redigiert konsequent: Protokoll `:351`, API-Antworten `:382/:391/:699`, Request-Snapshots `:700` — alle durch `redact(…, settings.apiKey())`. |
| CR-V24 [widerlegt] | Erwartungsprüfung kann durch NULL/Leermenge trivial grün werden | `Verifikator.java:83-98`: keine Zeile, mehrere Zeilen und NULL sind je eigene FEHLER-Prüpfungen; `BETRAG` vergleicht `compareTo` (scale-sicher); ein Auftrag ohne Erwartungen UND Rechenblöcke kann nie VERIFIZIERT werden (`:47` fail-closed). SQL-Fehler werden als Prüfung false geführt, nicht übersprungen (`:103-108`, ebenso `GlobaleVerifikation.java:65-67, 81-84`). |
| CR-V25 [widerlegt] | Berichtsveröffentlichung könnte halbfertige PDFs sichtbar machen | `Simulator.java:888-890` erzeugt vollständig und veröffentlicht per `Files.move(…REPLACE_EXISTING)`; `run-state.json` ebenso über `.tmp`+move (`:1050-1055`) — atomar. |
| CR-V26 [widerlegt] | GUI-URL-Autolink könnte HTML/Attribut-Injection erlauben | `app.js` (Testfall-Dialog): `esc()` entfernt `& < > " '` VOR dem `<br>`- und `<a href="$1">`-Einschub; der URL-Matcher `[^\s<]+` kann keine Anführungszeichen mehr antreffen — Ausbrechen aus dem Attribut ist konstruktiv unmöglich. |
| CR-V27 [widerlegt] | Cleanup könnte fremde Läufe/Zeilen treffen | `RunDataScope.java:22` `OWNED_ROWS = SUBSTR(AKTENZEICHEN,1,LENGTH(?)) = ?` mit UUID-Laufkennung als Präfix — Kollision praktisch ausgeschlossen; globale Prüfungen binden denselben Besitz zweifach. |
| CR-V28 [widerlegt] | Zertifikats-Verdict zu leicht „BESTANDEN" | `certificate-evidence.js`: `some(FAIL) → NICHT BESTANDEN`, `every(PASS) → BESTANDEN`, sonst `UNVOLLSTÄNDIG` — ein einziger UNVERIFIED-Layer (alter Lauf, fehlender Beleg, Build-Abweichung) verhindert BESTANDEN; Chronology-Kandidaten entwerten Mehrfachläufe; Build-Bindung prüft Klassen-Hashes je Artefakt. Streng. (Offen bleibt der Mindestumfang der GLM-Schicht — siehe Vermutung V-M1.) |

**Offene Vermutungen (nicht belegt, gekennzeichnet):**
- V-M1: Ob die GLM-Schicht einen Mindest-Prüfumfang erzwingt (1-Fall-Stichprobe → Layer PASS?) wurde nicht bis zur Schranke verfolgt (`llm-evidence.js`). Als Prüfauftrag für die Nacharbeit notiert.
- V-M2: WebMitarbeiter-Schritt-Timeout (siehe CR-V09) — Vorhandensein nicht abschließend belegt.

## 3. Testlücken

| L | Lücke | Vorschlag |
|---|-------|-----------|
| L1 | `/api/testreport`, `/api/testreport/pdf` ohne HTTP-Regression (Guards, 409 bei Suite, Verify-Ablehnung) | `TestreportHttpTest` nach Vorbild `CertificateSuiteHttpTest` (Fake-Werkzeug, Hash-Versatz). |
| L2 | `runs.js`-Parsing ungetestet (CR-V04 wäre dort rot geworden) | Node- oder Java-Paritätstest mit Fixture-logs: Teillauf-Surefire, Abbruch-vs-Fail-Klassifikation, Sekundengleichheit. |
| L3 | TOC-Anker-Robustheit (CR-V06) | Unit-Test `seitenErmitteln` mit umbenannter Überschrift → muss Exception werfen (nach Fix). |
| L4 | `stop.ps1`-Prozessverifikation nur manuell geprüft | PowerShell-/Java-Test mit Pseudokommandozeilen (fremd/eigen/direkter java-Start). |
| L5 | Fachzustand-Tabellenliste ohne DDL-Sync (CR-V01) | Test: Parse `ddl/lmg_schema.sql` auf CREATE TABLE ohne TX_-Präfix → muss Teilmenge der Vergleichsliste sein. |
| L6 | Web-Freeze-Reproduktion (CR-V09) | Schritt-Timeout-Test mit blockierendem Stub-Treiber. |

## 4. Fachliche Nachrechenprotokolle (alle bestanden)

- **Grenzfall 13** (Alleinerziehend, 4 Kinder, 1×U7, 2×U16): 48 % × 563 = 270,24 (schlägt 36 %); 563 + 270,24 + KdU 300 + 357 + 390 + 2×471 = **2.822,24 €** ✓
- **Grenzfall 14** (5 Kinder, 3×U16): 60 % = 337,80; 563 + 337,80 + 300 + 3×390 + 2×471 = **3.312,80 €** ✓
- **Grenzfall 15** (6 Kinder, 72 % auf 60 % gekappt): 563 + 337,80 + 300 + 357 + 2×390 + 3×471 = **3.750,80 €** ✓
- **Grenzfall 17**: 563 + 350 + 50 + 100 = **1.063,00 €** ✓
- **ECHT 1–5**: 1.542,00 / Abwehr (1.023−900=123 < 34.500) / 1.023,00 / Abwehr (11.000 > 1.542) / 563+590=1.153,00 ✓ (Quellenabgleich siehe Feature-Review-Prompt; amtliche Stufen verifiziert)
- **RECHNER 1–12**: alle zwölf externen Werte am 27.10.2026 selbst im externen Rechner erhoben und identisch nachgerechnet (Beleg: `doc/belege/…2026-10-27.md` + Screenshot Fall 12) ✓

## 5. Blinde-Grün-Analyse (die drei realistischsten Wege)

1. **Geteilte Fachauslegung (Quell-Zirkel):** Produkt, BigDecimal-Referenz, Generator und Prüfpaare folgen derselben §-Lesart aus `doc/rechtsgrundlagen.md`. Ist die Lesart falsch, sind Referenz, Rechenblöcke und GLM-Vergleich alle „richtig falsch". Einzige Brecher: ECHT (amtliche Werte) und RECHNER (fremde Codebasis) — deshalb sollte das Zertifikat sie optionally einschließen (CR-V10).
2. **Teillauf-Zahlen (CR-V04):** Der Management-Report kann „BESTANDEN … 334 Tests" aus einem 13-Fälle-Teillauf ableiten — grüne Zahl, falsche Basis.
3. **Urteil auf Teilmenge (CR-V05):** Report-Zertifikatsseite „BESTANDEN" bei nur teils vorhandener, aber komplett erfolgreicher Laufmenge; ergänzt durch die weiche ABGEBROCHEN-Klassifikation für 0-verifizierte Läufe.

## 6. Verdict

**BEDINGT BESTANDEN.** Der Verifikationskern ist außergewöhnlich streng (fail-closed in
allen geprüften Wegen, atomare Veröffentlichung, konsequente Besitzbindung, schlanke,
widerlegte Angriffshypothesen). Die Funde konzentrieren sich auf die NEUEN
Darstellungsschichten (Management-Report: CR-V04/V05/V06) und zwei langfristige
Wartungsfallen (CR-V01 Tabellenliste, CR-V03 CP1252) — kein BLOCKER, kein KRITISCH.

**Top-3-Empfehlungen:**
1. Report-Zahlen an laufgebundene Nachweise binden und Urteilszeile an vollständige
   Kategorienabdeckung koppeln (CR-V04 + CR-V05).
2. Fachzustand-Tabellenliste per Sync-Test gegen die DDL sichern (CR-V01, L5).
3. WebMitarbeiter-Schritt-Timeout einführen und den Freeze-Vorfall damit schließen
   (CR-V09, L6) — danach WEB-Läufe auffrischen, damit Report-Zertifikat nicht länger
   durch zwei abgestorbene Altläufe auf „UNVOLLSTÄNDIG" gedrückt wird.
