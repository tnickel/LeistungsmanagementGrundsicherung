# Codereview testsweet nach testsweet-codereviewPrompt.txt — Fix-Verifikation + Gesetzesmatrix (CR-201 ff.)

**Datum:** 2026-10-13, Abend (zweite Review-Runde des Tages)
**Prüfer:** ZCode (GLM-5.3), rein lesend — **keine Code-Änderungen, keine dynamischen Prüfungen**
(bewusst keine Tests/Zertifikatstrockenläufe gestartet, um die parallel laufende Arbeit einer
anderen KI nicht zu stören; stattdessen statische Verifikation am Live-Stand).

## Review-Stand (wichtig)

- **HEAD:** `1641727` („testsweet: Review-Fixes, GLM-Prüfung und Testzertifikat").
- **Arbeitskopie:** umfangreich uncommittet verändert — **eine andere KI arbeitet parallel an
  den Fixes** (34 Dateien, +1777/−1147 Zeilen, 5 neue Hauptklassen, 13 neue Testklassen).
  Dieser Review liest den **Live-Arbeitsstand**; alle Zeilenangaben beziehen sich auf den
  beim Lesen vorgefundenen Stand und können sich bis zum Commit noch verschieben.
- Grundlage: `doc/codereview/testsweet-codereviewPrompt.txt` (Prüffelder A–J, AKE-1..4,
  Spuren S-01..S-10) und als Vorbefunde `doc/codereview/testsweet-glm-zertifikat-codereview-2026-10-13.md`
  (CR-101…CR-120).

## Antworten auf die Akzeptanzkriterien (AKE)

- **AKE-1 (kein falsches OK): überwiegend erfüllt.** Die Auskette ist durchgängig fail-closed:
  Verifikator wertet DB-NULL/„keine Zeile"/„mehrere Zeilen"/SQL-Fehler zu ok=false, leere
  Erwartungslisten gelten als Fehlschlag (Verifikator.java:40), ReviewOutcome aggregiert
  FAIL-dominant über Automatik+LLM, Exit-Code-Logik unverändert korrekt. Restpunkte: CR-206
  (tautologische `SELECT ?`-Erwartungen, harmlos), CR-119 (laufabgeleitete Soll-Vorgangszahl).
- **AKE-2 (LLM-Orakel): erfüllt — deutlich stärker als in der Vorbefund-Runde.** Ein „OK"
  bzgl. Fachwerten setzt jetzt voraus: vollständige Fallabdeckung, Schema-2-Antwort mit je
  Fall allen Rechenblöcken (Personenwerte + BG-Werte) und je V-/S-Prüfpunkt einem Urteil
  (LlmAnswerValidator), **Zahlenabgleich des Modells gegen die unabhängige Referenz**
  (referenceChecks; Abweichungen → FAIL/UNVERIFIED), intakte Belegkette (Manifest/Started/
  Response-Contract/Build-Bindung), und im Zertifikat druckt die GLM-Schicht je Fall die
  findings **rot und fett** mit Rechenweg und Referenzbefunden (generate.js:60-79). Ein
  LLM-FAIL macht das Zertifikat „NICHT BESTANDEN" (rot).
- **AKE-3 (Nachvollziehbarkeit): erfüllt.** V-Zeilen mit SQL, Parametern und Hinweis
  (MdBericht.java:198-207), Lebenszyklusdateien je Lauf (run-state.json mit buildEvidence,
  reports-Hashes, logComplete, evidenceErrors), je LLM-Prüfung started/status/result/protocol,
  SweetLog.vollstaendig(), UNZUGEORDNETE_LAEUFE-Schicht macht nicht zuordenbare neuere Läufe
  sichtbar (certificate-evidence.js:216-217).
- **AKE-4 (Gesetzesabdeckung): teilweise erfüllt** — Matrix unten; vier echte Abdeckungslücken
  (CR-201…CR-204), davon zwei fachlich gewichtig (§-35-Prüfseiten, Folgemonats-Verbrauch).

## Executive Summary

Die parallels laufende Fix-Welle ist **substanziell und qualitativ hochwertig**: Alle fünf
KRITISCH/HOCH-Vorbefunde (CR-101 Zirkelschluss, CR-102/103 Kassenlauf, CR-104 LLM-Funde im
Zertifikat, CR-105 Login-Semantik) sind adressiert — teilweise durch Neubau (unabhängige
BigDecimal/BigInt-Referenz in Java **und** JS, deterministischer Kassenlauf mit eindeutiger
Kennung, Belags-Zertifikat mit Build-/JUnit-/Lauf-Bindung, Response-Vertrag, Fachzustands-
Vollabgleich bei jedem erwarteten Fachfehler). Wichtige early warnings bleiben: Die neue
Belag-Kette ist betriebsseitig anspruchsvoll (Maven-Hooks, frische Builds — CR-207/210), und
die neue Zertifikatslogik wurde in dieser Runde nur statisch, nicht dynamisch geprüft
(Tests + Trockenlauf nach Abschluss der Parallelarbeit zwingend nachziehen). Neue Funde sind
überwiegend Abdeckungslücken in der Gesetzesmatrix und Randbeobachtungen (CR-201…CR-213).

---

## Fundliste (neu, CR-201 ff.)

### CR-201 — MITTEL — Entbindungsmonat: Grenzmonat selbst wird nie generiert
- **Ort:** SzenarioBibliothek.kombiFall, Zeilen ~1318–1326.
- **Beleg:** `int idx = Math.max(0, monatIdx - 1 - random.nextInt(3));` (strikt vor dem
  Zahlungsmonat) bzw. `int idx = Math.min(11, monatIdx + 1 + random.nextInt(6));` (strikt danach).
- **Auswirkung:** Die fachlich heikle Kante „Zahlungsmonat **==** Entbindungsmonat → Mehrbedarf
  bleibt noch" (§ 30 Abs. 2: bis zum Ende des Entbindungsmonats) wird vom Laufbestand nie
  geschnitten; nur der Referenztest LlmCalculationReferenceTest deckt sie („2026-10/2026-10").
  Ein Off-by-One genau auf dieser Kante bliebe in Läufen unentdeckt (Referenz/LLM fingen ihn).
- **Fix:** In kombiFall mit kleiner Wahrscheinlichkeit `entbindungsmonat = monat` ziehen.

### CR-202 — MITTEL — § 35/42a-Prüfseiten im testsweet-Bestand komplett aus
- **Ort:** SzenarioBibliothek.berechne() (~2550–2557: überall `KduParameter.AUS`);
  MdBericht.grundlagen() („angemessenheitspruefung AUS").
- **Auswirkung:** Weder die Gesamtangemessenheitsgrenze (§ 35 Abs. 7) noch der Warmwasser-
  Abzug von Heizkosten bei zentraler Erzeugung (§ 35 Abs. 5, `lmg.kdu.warmwasserabzug`)
  werden von irgendeinem testsweet-Fall geprüft; KdU ist reine Summe Miete+Betrieb+Heizung.
  Die Normen sind als Modellgrenzen in doc/rechtsgrundlagen.md ausgewiesen — das Zertifikat
  nennt „nicht modellierte Rechtsregeln bleiben außerhalb" (generate.js:90), **nicht aber
  explizit diese beiden implementierten, aber ungetesteten Schalter**. Wer die Grenze
  konfiguriert (Konfiguration ist möglich!), hat keinen Testfall-Schutz.
- **Fix:** Mindestens zwei kuratierte Fälle mit konfigurierter Grenze bzw. zentralem
  Warmwasser (Kürzung + Bescheid-Kennzeichnung prüfen) — oder Zertifikats-Grenzentext
  erweitern („KdU-Angemessenheit und zentraler Warmwasserabzug im Testbestand ausgeschaltet").

### CR-203 — MITTEL — §-90-Verbrauchsfortschreibung: Folgemonat ungedeckt im Laufbestand
- **Ort:** kombinationPositiv erzeugt je Fall genau einen Zahlungsmonat (Storno+Neu im selben
  Monat); kein Szenario ordnet im Folgemonat erneut an.
- **Auswirkung:** Die v0.5.6-Invariante „ab dem Folgemonat liegt der Verbrauchsrest im
  Schonvermögen und wird nicht erneut abgezogen" prüft nur der Hauptprojekt-Test
  (VermoegensfortschriebDbTest), nicht der Simulator-Bestand bzw. die LLM-Stichprobe der
  Laufberichte. Kombiniert mit CR-201: die zeitabhängigen Regeln sind im Bestand dünn.
- **Fix:** Kombinationsvariante „Zweitmonat" (Anordnung in monat und monat+1, Erwartung:
  zweite Zahlung ohne Vermögensabzug, VERMOEGENSEINSATZ nur an der ersten).

### CR-204 — NIEDRIG — 60-%-Deckel-Kante des § 30 Abs. 3 wird nie geschnitten
- **Ort:** kombiFall kinder 1–3 (`1 + nextInt(3)`, Alleinerziehend) bzw. 1–2 (Paar+Kinder);
  kuratiert familie 1–2 Kinder.
- **Auswirkung:** 12 % × 5 Kinder = 60 % und die Kappung bei >5 Kindern kommen im Laufbestand
  nie vor (nur Referenz/Python-Rechnung decken die Formel). Zusätzlich prüft kein Fall die
  Kombination „4 Kinder unter 16 ohne U7" (48 %) gegen die 36-%-Pauschale-Abgrenzung.
- **Fix:** kinder-Spanne gelegentlich auf 4–5 erweitern.

### CR-205 — NIEDRIG — Kassenlauf-Erwartungen teils tautologisch (Service-Rückgabe gegen Soll)
- **Ort:** kassenlauf, ~940–943: `SELECT ? FROM DUAL` mit `kassenAnzahl`/`kassenSumme`.
- **Bewertung:** Anzahl=1 und Summe=vorab berechnet sind echte Sollwerte gegen die
  Service-Antwort; die DB-Kontrollen (UEBERGEBEN, FREIGEBEN_VON, Journal exakt 1, keine
  Zusatzzahlung) folgen separat. Kein False-Pass — aber die `SELECT ?`-Erwartungen prüfen
  keinen Datenbankzustand und könnten_missverstanden werden. Empfehlung: Beschreibung
  klar als „Kassenantwort" kennzeichnen (ist geschehen) oder against JOURNAL-Menge prüfen
  (geschieht fürs Journal bereits).

### CR-206 — NIEDRIG — Belag-Kette ist betriebsseitig anspruchsvoll (JUnit-Marker)
- **Ort:** certificate-evidence.js.junit() (target/test-evidence.json + test-evidence-history,
  Inventar aus Quellen, per @Test-Methodenliste) und Poms (exec-Hooks initialize/process-classes/test).
- **Auswirkung:** Wer Tests nur per IDE (ohne Maven) läuft, bekommt die JUnit-Schicht
  UNVERIFIED (konservativ — korrekt, aber unerwartet für Nutzer). Dafür fängt die Kette
  gelöschte/umbenannte Tests und „späterer Skip beweist keinen Fix" sauber ab. Beobachtung,
  kein Fehler; Betriebshandbuch-Hinweis empfohlen.

### CR-207 — NIEDRIG — Textkopplung der Login-Abwehrprüfung
- **Ort:** WebHelfer.pruefeAnmeldeAbweisung (~380–386): verlangt wörtlich
  „Kennung oder Passwort ist falsch" plus data-http-status=400 und data-error-code=VALIDATION.
- **Bewertung:** Sehr starke Semantikprüfung (löscht CR-105 vollständig — 500/Netzwerkfehler
  können nicht mehr als Abwehr durchgehen, Token-Abwesenheit wird geprüft). Kehrseite:
  Ändert jemand den Meldungstext der Web-App, schlägt der Test fehl (false-FAIL-Richtung,
  akzeptabel). Hinweis für künftige Textänderungen.

### CR-208 — NIEDRIG — Simulator-Globals „erwartete Vorgänge" bleibt laufabgeleitet (CR-119-Rest)
- **Ort:** Simulator.globalerKonsistenzcheck (wie Vorbefund; nicht Teil dieser Diff-Welle).
- **Bewertung:** Durch run-state.json/UNZUGEORDNETE_LAEUFE ist das Zertifikat gegen
  Crash-Verdeckung abgesichert; die globale Soll-Anzahl selbst bleibt aber kontextgezählt.
  Restrisiko klein (je-Auftrag-Erwartungen prüfen den eigentlichen Sollzustand), offengehalten.

### CR-209 — NIEDRIG — Verifikator akkumuliert Prüfungen über Re-Verifikationen hinweg
- **Ort:** Verifikator.verifiziereAlle Zeile 40 kombiniert `pruefungen().stream().allMatch`
  mit den neuen Prüfungen — einmal fehlgeschlagene Prüfungen bleiben in der Liste.
- **Bewertung:** Konservativ („einmal rot, immer rot" innerhalb eines Laufs) — gewollt
  oder akzeptabel; nur dokumentieren, damit es niemand „aufräumt".

### CR-210 — NIEDRIG — Build-Bindung erfordert frischen Klassenstand
- **Ort:** BuildEvidence/BuildStamp + certificate-evidence.buildValid/classHash.
- **Bewertung:** Veraltetes target/ → UNVERIFIED (korrekt konservativ). Betriebsaufwand:
  vor der Zertifikatserstellung `mvn install`/compile beider Module frisch ausführen;
  empfiehlt sich als Skriptzeile in erstelle-zertifikat.bat/publish flow.

### Nicht abschließend geprüft (Parallelarbeit; für Gegenprüfung vorgemerkt)
- **CR-113-Rest:** Bindung des GUI-Report-Fallbacks (`neuesteDatei`) an den aktuellen Lauf —
  app.js zeigt jetzt outcome-gesteuerte Warnungen (405–411), der Server-seitige Fallback
  wurde in dieser Runde nicht vollständig nachverfolgt.
- **CR-115-Rest (W-2):** Ursachen-Notiz beim Treiber-Neustart (WebMitarbeiter-Diff +36 Z.,
  nicht im Detail gelesen).
- **Z-8-Rest:** WEB-Kassenlauf-Journal-Cleanup für sb-web-%/web-admin-Kennungen.
- **S-12-Rest:** Stress-Welle-1-Doku vs. Erwartungen.

---

## Prüffeld J — Status der Vorbefunde CR-101…CR-120 (Live-Stand)

| Vorbefund | Status | Beleg (Live-Stand) |
|---|---|---|
| CR-101 Zirkelschluss Erwartungswerte | **BEHOBEN (Konzept)** | Unabhängige Referenz `LlmCalculationReference` prüft im Verifikator JE Rechenblock (pruefeRechnung) und in der LLM-Kette alle Modellzahlen; JS-Pendant llm-reference.js; Tests LlmCalculationReferenceTest mit Goldwerten inkl. Grenzkanten. Rest: Orakel für Workflow/SQL bleibt die Dokumentation+LLM — ausgewiesen. |
| CR-102 Kassenlauf 0 Übergaben | **BEHOBEN** | kassenlauf neu: eigene BEREIT-Zahlung, anzahl==1 und summe==Soll erzwingen UEBERGEBEN exakt einmal; Journal exakt 1 unter UUID-Kennung. |
| CR-103 Kassenlauf-Journal zählt Alte mit | **BEHOBEN** | Eindeutige Kennung `sb-sim-k-<uuid20>` + `AKTION_TEXT=?` exakt + „keine zusätzliche Buchung derselben Kennung" = 1. |
| CR-104 LLM-Funde nicht im Zertifikat | **BEHOBEN** | generate.js GLM-Schicht druckt je Fall verdict/findings (rot, fett), Rechenblöcke, V-/S-Checks und Referenzbefunde; Verdict dreistufig mit „NICHT BESTANDEN" rot. |
| CR-105 Login-Abwehr semantikfrei | **BEHOBEN** | pruefeAnmeldeAbweisung: 400 + VALIDATION + Wortlaut + kein Token; Abmelde-Handling für Alt-Sitzungen. |
| CR-106 Gate-Asymmetrie GUI/CLI | **BEHOBEN (neues Konzept)** | GUI erlaubt dokumentierendes Zertifikat (kein PASS-Gate mehr), publish-certificate.js mit READY-Marker; „erfolgreiche Dateierzeugung ist keine Freigabe"; Ausliefern prüft Aktualität (409 „kein aktueller Nachweis"). |
| CR-107 Gecrashte Läufe unsichtbar | **BEHOBEN** | run-state.json-Zustände (ABORTED/ERROR → Gründe), UNZUGEORDNETE_LAEUFE blockieren, newestSource wirft bei fehlgeschlagenem neuestem Lauf. |
| CR-108 Katalog-Overclaim | **BEHOBEN** | standardrente enthält jetzt Selbstfreigabe-Schritt + Freigabejournal-Erwartung; Katalogtext entsprechend („ausdrücklich abgewehrt … genau ein Freigabejournal"). |
| CR-109 negDoppelfreigabe ohne Vermerk-Prüfung | **BEHOBEN** | FREIGEGEBEN_VON=kollege AND SACHBEARBEITER_ID=mitarbeiter, Journal=1, Fachjournal gesamt=4. |
| CR-110 Kuratierte Negativfälle ohne Journal-Prüfung | **BEHOBEN (stärker)** | Fachzustand-Vollabgleich aller 5 Fachtabellen vor/nach JEDEM schrittMitErwartetemFehler (Mitarbeiter.ausfuehrenMitZustandspruefung) + klassische Erwartungen. |
| CR-111 Storno-mit-Vermögen unbelegt | **BEHOBEN** | VERMOEGENSEINSATZ an beiden Zahlungen, Vermögen/Budget sofort nach Storno zurückgebucht (vermoegenNachStorno/budgetNachStorno aus ladeDetail). |
| CR-112 Bescheid nur gezählt | **BEHOBEN** | COUNT mit BEWILLIGUNG_VON/BIS + STATUS='ERTEILT'. |
| CR-113 Alt-Report unter Erfolg-Banner | **vermutlich behoben** | GUI-Warnung outcome-gesteuert; Server-Fallback nicht vollständig nachverfolgt (s. o.). |
| CR-114 V-Zeilen ohne hinweis | **BEHOBEN** | sql/params/hinweis je V-Zeile; LlmCalculationReference.requirement prüft deren Vollständigkeit (missing → finding). |
| CR-115 Treiber-Neustart maskiert Ursache | **offen/geprüft-rest** | Nicht im Detail verifiziert. |
| CR-116 ABGEBROCHEN als „Abgeschlossen" | **BEHOBEN** | app.js:390 expliziter Zweig + Warnbanner (403–411). |
| CR-117 „PASS" grau | **BEHOBEN** | „NICHT BESTANDEN"/„UNVOLLSTÄNDIG / NICHT BESTÄTIGT"; fachlichBestanden verlangt zusätzlich caseVerdict=PASS. |
| CR-118 Hartkodierte ✓ | **BEHOBEN (Konzept)** | Zertifikat leitet alles aus Belegen ab; Reviews sind jetzt nur noch ergänzender Hinweistext ohne ✓-Zeile. |
| CR-119 Globale Vorgangsanzahl laufabgeleitet | **TEILWEISE** | s. CR-208. |
| CR-120 LLM-Prüfqualität Grenzen | **verbessert** | Modell-Zahlen werden gegen Referenz geprüft („faules PASS" mit falschen Zahlen fliegt auf); Grenzentext im Zertifikat; reasoning-Qualität bleibt Stichprobe. |
| NIEDRIG S-9/S-16/B-4/B-7/W-11/Z-5 | **BEHOBEN** | echter KdU-Split; ganzeZahl; Locale.ROOT; NICHT_BERECHNET; catch Throwable→meldeWorkerAbsturz; knownFindings→FAIL im Zertifikat selbst bei defekter Belegkette. |
| Z-7 Stress ceil/floor | **kein Fehler** | erzeugeStress liefert immer paarige 2n-Liste; ceil/floor identisch. (Korrektur der Vorbefund-Bewertung.) |

## Prüffeld C — Gesetzesmatrix (modellierte Normen vs. Testbestand)

Legende: ✔ gedeckt (kuratiert/kombinatorisch, Positiv und/oder Negativ), ◐ teilweise, ✘ Lücke.
Quellen: SzenarioBibliothek (Live-Stand), GoldWerteTest/SzenarioRegressionTest (Hauptprojekt),
llm-pruefauftrag/gesetzesgrundlagen (LLM), LlmCalculationReference (unabh. Referenz).

| Norm | Fachregel | testsweet-Deckung | Lücke |
|---|---|---|---|
| § 28 Anlage | RBS 1–6, Beträge 2026 | ✔ kuratiert (1–3 Erwachsene), Kombination (Kinder RBS 4–6), Referenz testet alle | — |
| § 30 Abs. 1 | Merkzeichen G 17 % eigener RB | ✔ mehrbedarf + kombiFall (30 %) | — |
| § 30 Abs. 2 | Schwangerschaft 17 %; Entfallen ab Folgemonat | ◐ kombiFall vor/nach Entbindungsmonat; **Grenzmonat selbst nie** | **CR-201** |
| § 30 Abs. 3 | 36 %/12 % je Kind, Basis RBS 1 | ✔ kuratiert familie (12 %), kombi (36 %/12 %) | 4–5-Kinder-Kante: CR-204 |
| § 30 Abs. 3 Deckel | max 60 % RBS 1 | ◐ nur Referenz/Tests, kein Lauffall | CR-204 |
| § 30 Abs. 6 | Kappung Abs. 1–5 auf RB | ✔ mehrbedarf-Kombinationen schneiden Kappung; GoldWerte 188,94; Referenz | — |
| § 30 Abs. 7 | Warmwasser dezentral je Stufe, außerhalb Deckel | ✔ mehrbedarf + kombi (35 %), RBS-3-Rate 0 mitgeprüft | — |
| §§ 35/42a (Summe) | KdU-Dreiteilung, BG-Summe, Split | ✔ kuratiert + KdU-Split-Kombination | — |
| § 35 Abs. 5 | zentraler Warmwasserabzug von Heizung | ✘ **ausgeschaltet im gesamten Bestand** | **CR-202** |
| § 35 Abs. 7 | Gesamtangemessenheitsgrenze je BG | ✘ **ausgeschaltet** (KduParameter.AUS überall) | **CR-202** |
| § 82 Abs. 2 | Absetzbeträge (4 Positionen) | ✔ erwerbstaetiger + kombiFall | — |
| § 82 Abs. 3 | 30 %/max 281,50; Rente ohne Freibetrag | ✔ Kappungskante geschnitten (Erwerb bis 1600); Referenztest 281,50 exakt | — |
| § 90/DVO § 1 | Schonvermögen 10 000/500; Verbrauch; Storno-Rückbuchung | ✔ vermoegensfall, kombi (Einsatz an Zahlung, Bestand sinkt), Storno+Neu bucht zurück | **Folgemonat fehlt: CR-203** |
| § 43 Abs. 1 | KEIN_ANSPRUCH | ✔ kuratiert + 3 Verstoßarten | — |
| § 44 Abs. 3 | ≤ 12 Monate, von≤bis; Deckungsmonat | ✔ negBescheidZeitraum/zuLang + kombi ausserhalbBescheid + inhaltliche VON/BIS-Prüfung | — |
| Vier-Augen/Workflow | ANGELEGT→BEREIT→UEBERGEBEN, FREIGEGEBEN_VON | ✔ reichhaltig (Standard, Negativ, Kombi, Kasse, Idempotenz, Konkurrenz, Storno) | — |
| Nicht modellierte Normen (§ 35 Abs. 1 S. 2–9/1,5×, Abs. 3, § 82 Abs. 3 S. 2, Abs. 4/5, § 43 Abs. 2, § 44a) | — | als Modellgrenzen in rechtsgrundlagen.md + Zertifikat („außerhalb des zertifizierten Umfangs") ausgewiesen; CR-202 ergänzt die beiden implementierten, aber ungetesteten Schalter | — |

## Positiv-Bestätigungen (mit Beleg)

1. **Unabhängigkeit ist jetzt dreifach:** Java-Referenz (Verifikator, je Auftrag), JS-Referenz
   (llm-reference.js, LLM-Belegprüfung + Zertifikat), Python (nachrechnen_100.py) — alle ohne
   Produktionsberechnung, mit eigenen Konstanten; Grenzkanten (Entbindungsmonat ==, 281,50,
   60-%-Deckel) im LlmCalculationReferenceTest gepinnt.
2. **Fachzustand-Vollabgleich** (5 Tabellen, READ-ONLY-Snapshot je Verbindung) bei jedem
   erwarteten Fachfehler — inkl. verlangeUnveraendert als harte Durchsetzung.
3. **LLM-Kette:** Schema-2-Vertrag (llm-response-contract.json hashgebunden), vorläufige
   Befunde aus ungültigen Antworten überleben und erzwingen UNVERIFIED („Spätere PASS
   entkräften sie nicht"), restoreLatest markiert unterbrochene Läufe ERROR,knownFindings
   machen das Zertifikat sogar bei defekter Belegkette rot (llm-evidence.js:163–174).
4. **Belags-Zertifikat:** JUnit-Inventar aus dem Quellbestand (gelöschte Tests → rot),
   „Skip beweist keinen Fix", DTD/Entity-abweisend, Zähler-gegen-Einzeltests-Kreuzcheck,
   Lauf-/Build-Bindung über Klassen-Hashes, UNZUGEORDNETE_LAEUFE, Fingerprint.
5. **Verifikator:** Mehrfach-Zeilen-Erkennung, Leererwartungen = Fehlschlag, SQL+Parameter
   je Pruefung mit in die MD/PDF-Berichte.

## Widerlegte / korrigierte Verdachte

- **Z-7 (Stress ceil/floor): kein Fehler** — erzeugeStress erzeugt immer eine paarige Liste;
  die beiden Formeln fallen zusammen. (Bewertungskorrektur zum Vorbefund.)
- **S-01…S-10 aus dem Prompt:** S-01 (Kassenlauf 0 Übergaben) durch Neubau behoben; S-03
  (unabhängige Erwartung) durch Referenz behoben; S-04 (Zertifikatstext bei einem FAIL)
  erfüllt — findings rot mit Fall-Bezug; S-05 (Begründung je Fall auffindbar) erfüllt
  (V-Zeilen, Rechenblöcke, Artefaktordner); S-06 (Entwertung nach Neustart/Änderung)
  erfüllt (newestSource wirft, buildValid, Aktualitäts-409 beim Ausliefern); S-08
  („nicht geprüft" ≠ „geprüft und ok"): durch dreistufiges Verdict + NA-Zeile erfüllt;
  S-09: dünnste Norm = § 35 Abs. 5/7 (CR-202).

## Empfohlene Reihenfolge der Nacharbeiten

1. Nach Abschluss der Parallelarbeit: **dynamische Verifikation** — beide Maven-Suiten,
   Referenzlauf je Kategorie, LLM-Prüfung, Zertifikatstrockenlauf (auch FAIL-/Abbruch-Szenario).
2. Matrix-Lücken schließen: CR-201 (Grenzmonat), CR-203 (Folgemonat), CR-204 (5 Kinder),
   CR-202 (KdU-Schalter testen oder Grenzentext schärfen).
3. Offene Reste nachziehen: CR-113/115-Rest, Z-8-Rest (WEB-Journal-Cleanup), S-12-Rest.
4. Betriebs-Hinweise: CR-206/210 (Maven-Hooks und frischer Build vor Zertifikat) ins
   README/Erneuerungs-Kapitel aufnehmen.

**Ende des Berichts.** Reine Beobachtung; keine Datei außer diesem Bericht wurde angefasst.
Geeignet als Übergabe an die parallel arbeitende KI (Fix-Verifikation) und die nächste
Gegenprüfung.
