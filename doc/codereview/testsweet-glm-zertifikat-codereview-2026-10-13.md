# Codereview testsweet + automatische Tests + GLM-Anbindung + Testzertifikat (2026-10-13, abends)

**Auftrag:** Vollständige Beobachtungsprüfung (keine Code-Änderungen) der gesamten testsweet
inkl. automatischen Tests, der echten LLM/GLM-Prüfkette und der Zertifikatserzeugung.
**Leitfrage:** Kann irgendwo ein echter Fehler der Software als „OK"/„bestanden" durchrutschen?
Müssen LLM-Funde im Zertifikat sichtbar werden und als dicker Fehler aufpoppen?

- **Stand:** HEAD `1641727` („testsweet: Review-Fixes, GLM-Prüfung und Testzertifikat", 2026-10-13 17:43).
  Der frühere Review `doc/codereview/testsweet-codereview-2026-10-13.md` ist in diesem Commit
  bereits eingearbeitet; dieser Bericht ist eine unabhängige Neuaufnahme des Ergebnisstands.
- **Methode:** Vier Prüfungen desselben Stands — (1) Verifikations-/Simulator-/LLM-/Zertifikats-Kette
  vollständig im Direktlesen, (2) Szenario-Fabriken, (3) Web-Modus, (4) Berichte/GUI/Exit-Codes.
  Zeilenangaben beziehen sich auf diesen Stand.
- **Konsolidierung:** Widersprüche zwischen den Teilprüfungen wurden am Quellcode entschieden
  (siehe „Widerlegte Verdachte").

---

## 1. Kurzfassung (Executive Summary)

Die **Auskette-Engine ist bemerkenswert streng und fail-closed**: Verifikator vergleicht ohne
Toleranz (DB-NULL, leere Resultsets und SQL-Fehler werten zu ok=false), ein Lauf ist nur bei
100 % VERIFIZIERT plus grünen globalen Prüfungen FERTIG, Spielabbrüche und Verifikationsfehler
sind getrennt und beide machen den Lauf rot, Exit-Codes sind korrekt (1 = Lauf rot, 2 =
Konfiguration), Abbrüche verifizieren gar nicht erst. Die LLM-Kette ist technisch außergewöhnlich
gut abgesichert (strenge Antwortvalidierung, FAIL-dominante Aggregation, SHA-256-Bindung aller
Belege, Downgrade PASS→UNVERIFIED bei beschädigten/veralteten Belegen, Test der Manipulations-
und Restart-Pfade). Das Zertifikat hebt bei jedem automatisch prüfbaren Problem auf BEDINGT ab
statt BESTANDEN zu zeigen.

**Trotzdem bleiben fünf echte False-Pass-Kanäle (CR-101 bis CR-105)** und eine Reihe
schwächerer Befunde. Die beiden wichtigsten Antworten auf die Leitfrage:

1. **Rechenfehler findet testsweet prinzipbedingt nicht selbst** (CR-101): Die erwarteten
   Beträge werden mit derselben `Leistungsberechnung` erzeugt, die das System nutzt. Die
   vorhandenen Gegenmaßnahmen (GoldWerteTest, nachrechnen_100.py, LLM-Nachrechnung) liegen
   außerhalb des automatischen Laufs bzw. sind Stichproben — die Aussage „500/500 verifiziert"
   bedeutet Persistenz-/Workflow-Verifikation, nicht Rechenverifikation.
2. **LLM-Funde erscheinen nicht im Zertifikat** (CR-104): Bei FAIL zeigt das Zertifikat nur
   eine generische Warnzeile („Fachliche Befunde oder ungeprüfte Fälle"); die konkreten
   findings (Fall-IDs, Abweichungen, eigene Rechnung des Modells) stehen ausschließlich in
   den Artefaktdateien. FAIL, UNVERIFIED und „kein Protokoll" sind im Warnblock nicht
   unterscheidbar, und das Verdict heißt in allen drei Fällen nur „BEDINGT" — kein dicker,
   roter Fehler. Zusätzlich sperrt die GUI die Erzeugung bei FAIL komplett (409), während
   der CLI-Weg (erstelle-zertifikat.bat) ein BEDINGT-Zertifikat erzeugt und ungeachtet der
   Warnungen nach doc/ als offizielle Datei kopiert (CR-106).

---

## 2. Fundliste — KRITISCH / HOCH

### CR-101 — KRITISCH (konzeptionell) — Erwartete Beträge aus derselben Rechnung wie das System (Zirkelschluss)
- **Ort:** `SzenarioBibliothek.java:2512-2519` (`berechne()`), `:1015-1034` (Kombination),
  `:1363-1364` (Negativ-Basis); Klassendokumentation `:23-28` gesteht es ein.
- **Beleg:** `var ergebnis = Leistungsberechnung.berechne(0, faelle, KduParameter.AUS, vermoegen);`
  — dieselbe Kette wie `SozialLeistungsService.java:306-310`.
- **Auswirkung:** Jeder Rechenfehler in `Leistungsberechnung`/`Bewilligungsplan` (falscher
  §-30-Prozentsatz, falsche Abs.-6-Kappung, Rundung, §-82-Reihenfolge, Schonvermögen,
  Off-by-One beim Entbindungsmonat) erzeugt Erwartung und Ist identisch falsch → Lauf grün.
  testsweet weist damit Persistenz und Workflow nach, nicht Fachwerte.
- **Vorhandene Kompensation (nicht Teil des automatischen Laufs):** `GoldWerteTest`
  (handgerechnete Werte), `doc/testzertifikat-werkzeug/nachrechnen_100.py` (echte, unabhängige
  Zweitimplementierung mit eigenen Konstanten, exit 1 bei Abweichung — geprüft und solide),
  LLM-Nachrechnung (Stichprobe).
- **Fix-Richtung:** Eigene Erwartungs-Rechenklasse in testsweet (Port des Python-Kerns mit
  eigenen Konstanten) oder fester Sollwert-Katalog je Signatur; Berichte/Zertifikat sprechen
  die Aussagekraft explizit aus („Persistenzverifikation, nicht Rechenverifikation").

### CR-102 — HOCH — Kassenlauf-Auftrag besteht auch bei null Übergaben
- **Ort:** `SzenarioBibliothek.java:926-931`.
- **Beleg:** `CASE WHEN ? = 0 THEN 'ok' WHEN (SELECT COUNT(*) FROM JOURNAL ...) >= 1 THEN 'ok' ...`
  — `?` = `kassenAnzahl` des Aufrufs.
- **Auswirkung:** Ein Kassenlauf, der nichts findet/übergibt (kaputte Mengenermittlung,
  falsches Präfix-Filter, Early-Return-Fehler), ist immer „ok". Keine Erwartung prüft
  `anzahl`/`summe` des Aufrufs gegen die DB; Freigabe-Szenarien akzeptieren `BEREIT|UEBERGEBEN`;
  der Global-Check „keine Übergabe ohne Freigabevermerk" ist bei null Übergaben trivial erfüllt.
  Ein total versagender Kassenlauf würde einen POSITIV-Lauf komplett grün durchlaufen.
- **Fix-Richtung:** Globale Prüfung „keine Zahlung bleibt BEREIT hängen" (COUNT … STATUS='BEREIT'
  … SWEET-% = 0 nach dem letzten Kassenlauf-Auftrag) und/oder anzahl/summe per SQL gegen die
  UEBERGEBEN-Menge desselben Journal-Eintrags abgleichen.

### CR-103 — HOCH — Kassenlauf-Journal-Check zählt fremde/alte Journale desselben Users mit
- **Ort:** `SzenarioBibliothek.java:926-931` (Subquery `SACHBEARBEITER_ID = ? … >= 1`);
  verschärfend `Simulator.java:826-828` (Cleanup löscht BG-loser Kassenlauf-Journale nur für
  `sb-sim-%`, nicht für Web-Kennungen).
- **Auswirkung:** Läuft derselbe Mitarbeiter mehrere Kassenlauf-Aufträge (bis zu 11 im
  500er-Topf), erfüllt der Journal-Eintrag des ersten die `>= 1`-Bedingung — der zweite darf
  seinen eigenen Eintrag verfehlen. In WEB-Läufen bleiben BG-loser Kassenlauf-Journale
  zusätzlich im Cleanup außen vor.
- **Fix-Richtung:** Journal auf den aktuellen Lauf begrenzen (Zeitstempel ≥ Laufstart) oder
  eindeutiges Merkmal je Kassenlauf-Auftrag mit exakt `= 1` prüfen; Cleanup auf Web-Kennungen
  ausweiten.

### CR-104 — HOCH — LLM-Funde sind im Zertifikat nicht sichtbar; kein „dicker Fehler" bei FAIL
- **Ort:** `doc/testzertifikat-werkzeug/generate.js:113-116` (Warnzeile), `:176-197` (Verdict
  BESTANDEN/BEDINGT), `:239-246` (Warnblock); `doc/testzertifikat-werkzeug/llm-evidence.js:122-131`
  (`reason` ist für FAIL und UNVERIFIED identisch generisch: „Fachliche Befunde oder ungeprüfte Fälle",
  `meta` enthält keine findings).
- **Auswirkung:** Findet das LLM echte Abweichungen (state=FAIL mit findings je Fall), zeigt
  das Zertifikat nur eine gelbe Warnung des Typs, der auch bei „Protokoll fehlt" erscheint.
  Die konkreten Befunde (Fall-IDs, Größen, eigene Rechnung vs. Dokumentwert) stehen nur in
  `testsweet/logs/llm-review-*/result.json` bzw. `protocol.md` — das Zertifikat deckt sie
  nicht ab. Verdict „BEDINGT" + „!"-Haken ist kein dicker Fehler; rot ist im Zertifikat
  überhaupt nicht vorgesehen (P.rot wird für Verdicts nicht verwendet).
- **Fix-Richtung:** (a) Bei state=FAIL Verdict auf ein eigenes, rot hinterlegtes
  „NICHT BESTANDEN — LLM hat fachliche Abweichungen gefunden" mit Anzahl FAIL-Fällen und
  je Fall caseId + findings als eigene Block/Zeilengruppe; (b) UNVERIFIED/ERROR/fehlend
  getrennt formulieren; (c) `llm-evidence.js` liefert findings/Fall-IDs im `meta` mit.

### CR-105 — HOCH — Web-Modus: Anmeldungs-Abwehr wird ohne Semantikprüfung als Erfolg gebucht
- **Ort:** `WebHelfer.java:365-373` (Negativ-Anmeldung: beliebiger nicht-leerer Text in
  `#login-fehler` genügt); die Web-App schreibt in `#login-fehler` auch HTTP-/Netzwerkfehler
  (webapp app.js `catch`-Zweig).
- **Auswirkung:** Würde die Anmeldung statt der kontrollierten 401-Abweisung einen 500er
  werfen (echter Softwarefehler), verbucht der Test „Anmeldung abgewehrt" als Erfolg; die
  DB-Erwartung (COUNT BG = 0) bleibt dabei wahr. Alle anderen Web-Negativschritte prüfen
  erwartete Dialog-Teiltexte — nur der Login-Abwehr-Schritt tut es nicht.
- **Fix-Richtung:** Erwarteten Textschnitt fordern (z. B. enthält „Passwort"/konkrete Meldung)
  und in der Web-App technische Fehler von Fachabweisungen unterscheidbar machen (eigene
  Fehlerkennung/Präfix oder DOM-Element).

---

## 3. Fundliste — MITTEL

### CR-106 — Zertifikat: GUI-Sperre und CLI-Weg widersprechen sich; BEDINGT wird trotzdem veröffentlicht
- **Ort:** `SweetServer.java:527-529` (GUI verweigert bei nicht-PASS mit 409) vs.
  `doc/testzertifikat-werkzeug/erstelle-zertifikat.bat` (ruft generate.js direkt, keine LLM-Pforte);
  `SweetServer.java:541-544` (kopiert das erzeugte Zertifikat unabhängig vom Verdict nach doc/).
- **Auswirkung:** Nach einem FAIL kann die GUI kein dokumentierendes Zertifikat erzeugen
  (nur ein Alert-Fenster), während der CLI-Weg ein BEDINGT-Zertifikat baut und als
  offizielle Datei ablegt. Kein Weg erzeugt das von CR-104 geforderte FAIL-Zertifikat.
- **Fix-Richtung:** Einheitliche Semantik: Erzeugung immer erlauben, aber Verdict-scharf
  (BESTANDEN / BEDINGT / NICHT BESTANDEN) und bei nicht-BESTANDEN kein stilles Kopieren nach
  doc/ (oder nur mit deutlichem Namens-/Stempelzusatz).

### CR-107 — Gecrashte Läufe sind für das Zertifikat unsichtbar
- **Ort:** `generate.js:36-38` (`liesLaufzahlen` überspringt Laufordner ohne `zusammenfassung.txt`
  kommentarlos).
- **Auswirkung:** Stürzt der Server/Prozess vor `log.zusammenfassung(...)` ab (Crash, Kill,
  OOM), existiert keine zusammenfassung.txt — der ältere grüne Lauf bleibt „neuester Lauf"
  ohne Warnung. Das Zertifikat behauptet dann „neueste Läufe einschließlich Fehlern",
  obwohl ein neuerer, unvollständiger Lauf existiert.
- **Fix-Richtung:** Laufordner ohne zusammenfassung.txt, die neuer sind als der gewählte
  Kandidat, als Warnung ausweisen („neuerer Lauf ohne Zusammenfassung — Lauf prüfen").

### CR-108 — TestfallKatalog verspricht Prüfungen, die der Code nicht enthält
- **Ort:** `TestfallKatalog.java:43-50` vs. `SzenarioBibliothek.java:300-339` (`standardrente`
  ohne Selbstfreigabe-Schritt und ohne Journal-Erwartung; Gewichtsangaben ~22 % passen nicht
  zum Round-Robin-Verhältnis bei 500 Aufträgen).
- **Auswirkung:** Der Katalog ist GUI-„Testfälle im Detail"-Quelle und Zertifikatsbasis —
  er behauptet Abdeckung, die der Fachschicht-Code nicht liefert.
- **Fix-Richtung:** Katalogtext an Code angleichen oder (besser) den Selbstfreigabe-Schritt +
  Journal-Erwartung in `standardrente` ergänzen.

### CR-109 — negDoppelfreigabe prüft den Freigabevermerk nicht
- **Ort:** `SzenarioBibliothek.java:2152-2158` (nur Status `BEREIT|UEBERGEBEN` + Budget);
  die Kombinations-Variante (`:1708-1711`) prüft `FREIGEGEBEN_VON = kollege` — der kuratierte
  Fall ist schwächer als sein Kombinations-Pendant.
- **Fix-Richtung:** CASE-Erwartung auf `FREIGEGEBEN_VON` + Journal-Anzahl ergänzen.

### CR-110 — Kuratierte Negativfälle prüfen die Journal-Unverändertheit nicht
- **Ort:** z. B. `SzenarioBibliothek.java:1852-1858` (negKeinAnspruch), `:1926-1932` (negBudget),
  `:2363-2366` (negBescheidZeitraum prüft nur Bescheid-COUNT).
- **Auswirkung:** Die dokumentierte Negativ-Invariante „keine zusätzlichen Journal-Einträge"
  wird nur in `kombinationNegativ` geprüft (dort vorbildlich exakt) — schreibt ein abgewehrter
  Schritt dennoch ein Fach-Journal, bleibt das in kuratierten Fällen unentdeckt.
- **Fix-Richtung:** Journal-Anzahl-Erwartung in alle kuratierten Negativfälle übernehmen.

### CR-111 — Storno mit Vermögenseinsatz ist im gesamten Testbestand unbelegt
- **Ort:** `SzenarioBibliothek.java:1039` (`mitStornoNeu` nur bei `verwertbar == 0`);
  kuratiertes `stornoMitKorrektur` (`:744 ff.`) ohne Vermögen.
- **Auswirkung:** Die v0.5.6-Invariante „Storno bucht genau den dokumentierten Vermögenseinsatz
  zurück (VERMOEGENSEINSATZ + Bestand)" wird von keinem Szenario geprüft — Abdeckungslücke
  gegenüber der „volle Kombinationsabdeckung"-Behauptung.
- **Fix-Richtung:** `mitStornoNeu` auch mit `verwertbar > 0` zulassen; Erwartungen für
  Vermögens-Rückbuchung + VERMOEGENSEINSATZ beider Zahlungen ergänzen.

### CR-112 — Bescheid-Zeitraum wird bei Kombinationsfällen nur gezählt, nicht inhaltlich geprüft
- **Ort:** `SzenarioBibliothek.java:1244-1248` (COUNT BESCHEID = 1, ohne VON/BIS).
- **Fix-Richtung:** `COUNT WHERE BG_ID=? AND BEWILLIGUNG_VON=? AND BEWILLIGUNG_BIS=? = 1`.

### CR-113 — GUI liefert bei Schreibfehler des Reports den Report des VORLÄUFS unter „erfolgreich generiert"
- **Ort:** `Simulator.java:203-242` (`neuesteDatei`-Fallback), `:751-771` (Schreibfehler der
  Testdokumentation wird nur geloggt), `index.html:39-47` (Banner „erfolgreich generiert").
- **Auswirkung:** Schlägt das Schreiben der Doku im aktuellen Lauf fehl, zeigt der PDF-Knopf
  den Report eines älteren Laufs mit Erfolg-Banner — ein alter BESTANDEN-Stand kann für das
  aktuelle Ergebnis gehalten werden. (Zusätzlich: `neuesteDatei` vergleicht Pfade lexikographisch
  über den Zufalls-Suffix von `createTempDirectory`; entscheidend ist das Sekunden-Präfix, zwei
  Läufe in derselben Sekunde sind durch die Laufsperre ausgeschlossen — Restrisiko gering.)
- **Fix-Richtung:** Fallback an `letztesLogVerzeichnis` binden bzw. 404 liefern, wenn der
  aktuelle Lauf keinen Report schrieb; Banner an Lauf-Outcome koppeln.

### CR-114 — Prüfungs-Hinweise (SQL-Fehlerursachen) fehlen in den maschinenlesbaren V-Zeilen
- **Ort:** `MdBericht.java:186-195` und `Testdokumentation.java:413-418` (nur erwartet/ist,
  kein `hinweis()`); die Ursache steht nur in db-verifikation.log.
- **Auswirkung:** Das MD ist die Grundlage der LLM-Nachrechnung — die KI sieht
  `ist = <SQL-FEHLER>` ohne Ursache und kann die Zeile nur als Befund werten, nicht
  einordnen. Kein False-Pass, aber Diagnoseverlust genau im KI-Prüfpfad.
- **Fix-Richtung:** `hinweis` in beide Ausgaben aufnehmen.

### CR-115 — Treiber-Neustart maskiert die Ursache
- **Ort:** `WebMitarbeiter.java:182-197, 233-246` (erfolgreicher Neustart + Wiederholung
  lässt die Original-Exception verschwinden; `istToterTreiber` matcht auch
  „timed out receiving message" bei lebender Session → Fehlklassifikation).
- **Auswirkung:** Wiederholte Renderer-Abstürze der getesteten Seite (echtes Symptom!)
  werden still absorbiert (max. 1×/Auftrag) und sind im Bericht nicht erkennbar.
  Kein False-Pass (Schreibschritte werden nie wiederholt), aber Ursachen-Maskierung.
- **Fix-Richtung:** Ursachen-Exception als Schritt-Notiz protokollieren; Todeserkennung
  nur mit `invalid session id`/`session deleted` kombinieren.

### CR-116 — GUI zeigt Phase ABGEBROCHEN als „Abgeschlossen"
- **Ort:** `sweet/app.js:378-383` (Ternary-Kette ohne ABGEBROCHEN-Zweig).
- **Auswirkung:** Ein abgebrochener (ungeprüfter!) Lauf wirkt im großen Fortschritts-Label
  optisch „fertig". Phase-Chip und zusammenfassung.txt sind korrekt.
- **Fix-Richtung:** Expliziten Zweig ergänzen („Lauf abgebrochen — keine Verifikation").

### CR-117 — KiPruefAnhang-PDF zeigt wörtlich „Fachprüfung: PASS" in grauer Box, wenn transportVerified fehlt
- **Ort:** `KiPruefAnhang.java:18-20` (`value(evidence,"state",…)` = roher Zustand).
- **Auswirkung:** Bei state=PASS ohne vollständigen Transportnachweis steht „PASS" nur grau
  unterlegt — wer die Farbe nicht beachtet, liest PASS. Die MD-Zeile
  (`fachpruefung_bestanden = false`) ist korrekt; PDF und MD widersprechen sich optisch.
- **Fix-Richtung:** Text bei nicht bestanden immer „NICHT BESTANDEN/UNVOLLSTÄNDIG (state=…)".

### CR-118 — Hartkodierte Häkchen ohne „manuell gepflegt"-Kennzeichnung
- **Ort:** `generate.js:141-148` (Goldwerte „9 von Hand gerechnete Fälle", Szenario-Regression
  „10 Nutzer-Szenarien") und `:163-173` (Code-Reviews) sind feste ✓-Zeilen; nur die mvn-Zeile
  (`:136-140`) ist als manuell gekennzeichnet.
- **Auswirkung:*** Ein Leser hält diese Schichten für automatisch verifiziert. Sie sind
  Behauptungen mit Quellenangabe.
- **Fix-Richtung:** Einheitlich „(manuell gepflegt)" je hartcodierter Zeile.

### CR-119 — Globale Vorgangsanzahl ist laufabgeleitet, nicht szenariofest
- **Ort:** `Simulator.java:947-953` (Fachschicht: Erwartung = Anzahl Aufträge mit bgId im
  Kontext; WEB: Anzahl `vorgangErstellt`-Flags). Realer Lauf belegt es: 120 Aufträge,
  erwartete Vorgänge 117.
- **Auswirkung:** Bleibt eine Anlage aus, ohne dass der Schritt fehlschlägt (z. B. bgId wird
  bei Erfolg nicht in den Kontext gelegt), sinkt die Erwartung mit — der Global-Check
  verifiziert Konsistenz zwischen Kontext und DB, nicht den Soll-Umfang des Szenarios.
  Schwächster Kanal der Reihe, aber strukturell ein False-Pass-Risiko.
- **Fix-Richtung:** Solle Anzahl je Szenarioart im Auftrag mitführen und global gegen die
  Summe prüfen; mindestens WEB-`vorgangErstellt`-Pflege testen.

### CR-120 — MITTEL (Dokumentation) — LLM-Prüfqualität ist nur so gut wie das Modell ehrlich rechnet
- **Ort:** `LlmReviewService.validateAnswer` prüft Struktur strikt (IDs exakt, verdict
  erlaubt, PASS ⇔ findings leer, reasoning nicht leer) — die inhaltliche Qualität des
  reasoning ist maschinell nicht prüfbar; Temperatur 0.1, System- und Prüfauftrag wirken
  dagegen an.
- **Auswirkung:** Ein „faules" Modell könnte mit dünnem Rechenweg flächig PASS liefern;
  die technische Belegkette würde das durchlassen. Bekanntes, dokumentiertes Restrisiko
  jeder LLM-Verifikation.
- **Fix-Richtung:** Im Zertifikat als Grenze ausweisen (ist teilweise vorhanden: „dokumentierte
  Stichprobe"); optional Stichproben-Auditis der reasoning-Texte oder Mindestanforderungen
  an Rechenweg-Bestandteile je Fallart.

---

## 4. Fundliste — NIEDRIG (zusammengefasst)

- **S-9** `SzenarioBibliothek.java:984,1002` — toter Ternary `kduSplit ? miete : miete` (Split
  betrifft nur Betrieb/Heizung; irreführend, fachlich folgenlos).
- **S-10** `:1070-1072` — tote Arithmetik in der Bescheid-Fensterberechnung (Ergebnis korrekt).
- **S-11** `:129-143` — verwaister JavaDoc-Block (erzeugeLast/erzeugeStress).
- **S-12** `:195-201` vs. `:185-192` — Stress-Welle-1-Doku verspricht Version-/Budget-Prüfung,
  die erst Welle 2 leistet (Zertifikatsrelevanz).
- **S-13** — Satzstand-Kopplung: testsweet rechnet mit `Satzstand.REFERENZ_2026`, der Service
  mit SATZSTAND-Tabelle; Divergenz → false-FAIL; Jahreswechsel 2027 bricht den Topf (hart
  kodierte MONATE_2026 / „2026-10").
- **S-14** — Positiv-Topf ohne Regressionstest gegen Signatur-Erschöpfung (Negativ-Seite hat einen).
- **S-15** — Fachfehler nur codegenau, nicht grundgenau geprüft (VALIDATION ist Sammelcode;
  Meldung wird protokolliert, nicht verglichen).
- **S-16** `:2080-2082` — COUNT per TEXT-Vergleich statt ganzeZahl (fragil, inkonsistent).
- **S-17** `:456,505-507` — Familien-Doku suggeriert 36 %-Pauschale, Generator liefert oft
  fachlich korrekte 12 %-Je-Kind-Werte (Beschreibung irreführend).
- **W-5** `WebHelfer.java:285-292` — Vorgang über Suche ohne Kopf-Aktenzeichen-Bestätigung
  (Fehltreffer würde von SQL-Erwartung als false-FAIL aufgefangen).
- **W-6** `:486-494` — Negativ-Beweis „keine Zahlung" mit nur 1,5 s Fenster (SQL fängt es).
- **W-7** `SweetLog.java:174-180,222-229` — Protokoll-IO-Fehler im Erfolgszweig bucht Schritt
  als FEHLGESCHLAGEN (false-FAIL-Richtung); verlorene Zeilen im Direkt-Fallback nur auf stderr.
- **W-8** `:159-163` — synchroner Schreib-Fallback nicht synchronisiert (praktisch unerreichbar).
- **W-9** `BrowserProzesse.java:26-43,65-102` — Profil-Verzeichnisse abgestürzter Läufe bleiben
  liegen; Fallback kennt nur `chrome.exe` (Plattform-Lücke außerhalb Windows).
- **W-10** `WebHelfer.java:53-73` — toter Catch-Zweig in klick(); JS-Klick auf deaktivierten
  Knopf ist stilles No-Op (false-FAIL über Folge-Warten).
- **W-11** `Mitarbeiter.java:34-47` — Worker-Tod ohne catch lässt Auftrag IN_BEARBEITUNG und
  (STRESS) die Phasenbarriere bis zum 30-Min-Abbruch hängen (false-FAIL, aber Laufzeitfalle).
- **W-12** `Simulator.java:549,412-31` — IO-Fehler in `log.zusammenfassung()` nach stehendem
  FERTIG überschreibt die Phase mit ABGEBROCHEN (false-abort, Verdict geht verloren).
- **W-13** `Simulator.java:477` — Web-Fenster werden vor Fachschicht-Läufen nicht geschlossen
  (Kosmetik).
- **B-4** `LastBericht.java:334` — Dezimalkomma im maschinenlesbaren MD (`8,4` statt `8.4`).
- **B-5** `:73-88` — inkonsistente Zählbasen für Dauer-Statistiken (Abschnitt 2 vs. 3/4).
- **B-6** `MdBericht.java:293-314` — SQL/Parameter-Zuordnung per Beschreibungs-Gleichheit
  (erste Treffer-Strategie; bei künftigen Doppelbenennungen zeigt die zweite V-Zeile SQL der ersten).
- **B-7** `:316-319` — `z()` mappt null auf „0.00" (nicht berechnet ≡ echter Null).
- **B-8** `SweetMain.java:114-124` — stille Port-Ersatzung; CLI-Zahlenfehler als Stacktrace;
  Erfolg ohne Report → Exit 0 („Erfolg ohne Nachweis" für Skripte; fehlgeschlagene Läufe
  kommen nie auf Exit 0).
- **B-9** `Testdokumentation.java:307-313` — „Vollständig verifiziert" bei Abweichung nur blau;
  OFFEN/IN_BEARBEITUNG-Reste un erklärt.
- **B-10** `index.html:97-130` — hartcodierte Mengenangaben widersprechen den Limits
  (serverseitige Abwehr existiert).
- **B-11/B-12** `SweetPdf.java:121-137,708-737` — kasten() kappt Überlänge mit „…";
  brichUm() bricht lange Tokens nicht (Fehlerzeilen am Rand unleserlich möglich).
- **Z-5** — `LlmReviewService.restoreLatest:536` kann bei beschädigten Belegen ein FAIL zu
  UNVERIFIED degradieren (noch immer nicht-PASS, aber FAIL-Information geht verloren);
  Zertifikats-Staging-Ordner (testsweet/logs/certificate-*) akkumulieren.
- **Z-7** — Stress-Erwartungen: Simulator rechnet `ceil(Größe/2)` Vorgänge,
  `GlobaleVerifikation:23` `Größe/2` (floor) — bei ungerader Auftragszahl widersprüchliche
  Erwartungen → garantierter Global-FAIL (false-FAIL). `erzeugeStress` erzeugt paarige
  2×n-Listen, `pruefeKonfiguration` lässt aber ungerade Zahlen zu.
- **Z-8** — WEB-Kassenlauf-Journale mit BG_ID NULL und Kennungen sb-web-*/web-admin werden
  von `raeumeAltdatenAuf` (nur `sb-sim-%`) nie gelöscht (Müll-Akkumulation).

---

## 5. Widerlegte Verdachte (bitte NICHT „reparieren")

- **„Ein erwarteter Fachfehler, der ausbleibt, wird als OK gebucht" — FALSCH.**
  `SzenarioAktion.execute` (`SzenarioAktion.java:26-29`) wirft bei normaler Rückkehr eines
  Negativschritts `IllegalStateException("Erwarteter Fachfehler … blieb aus")` → Auftrag
  FEHLGESCHLAGEN. Der Web-Modus ist über `fehlerDialogPruefen` analog streng. Der umgekehrte
  Verdacht (Fehlercode stimmt, Grund falsch) bleibt als S-15 bestehen.
- **„Bei FEHLGESCHLAGENen Aufträgen könnte der Lauf grün sein" — FALSCH.**
  `Simulator.laufErfolgreich()/aktualisiereAbschlussphase()` (`Simulator.java:840-852`)
  verlangen alle Aufträge VERIFIZIERT; FEHLGESCHLAGENE Aufträge werden von der Verifikation
  ausgeschlossen (Verifikator.java:35-38) und scheitern den Lauf.
- **„LLM-Belege könnte man nachträglich zu PASS fälschen" — praktisch FALSCH.**
  `llm-evidence.js` und `LlmReviewService.validateStored` binden result.json per SHA-256 ans
  Manifest, rekonstruieren den gesendeten Auftrag zeichengenau und leiten das Verdict aus
  den Roh-Antworten neu ab; Manipulation → UNVERIFIED (durch Tests abgedeckt:
  `findingsKeepTransportGreenButNeverProducePass`).

---

## 6. Ausdrücklich geprüft und GUT befunden (Gegenprüfungs-Anker)

1. **Verifikator** (Verifikator.java): strikte Vergleiche ohne Toleranz (BETRAG über
   compareTo, GANZZAHL exakt), DB-NULL/„keine Zeile"/SQL-Fehler → ok=false mit Kennzeichen.
2. **Laufstatus-Maschine** (Simulator.java:840-852, 956-979): FERTIG nur bei 100 %
   VERIFIZIERT + globalen Prüfungen ok; globale Fehler degradieren bereits verifizierte
   Aufträge zurück; ABGEBROCHEN verifiziert nicht; leere Auftragsliste ⇒ FEHLGESCHLAGEN.
3. **Erwartete-Fehler-Logik** (SzenarioAktion.java + Mitarbeiter.java:78-93): codegenau,
   fail-safe, kein Durchlauf trotz fehlender Abwehr.
4. **Zirkelkritische Gegenmittel existieren**: GoldWerteTest (handgerechnet), nachrechnen_100.py
   (unabhängige Zweitimplementierung; test_nachrechnen.py täuscht gezielt falsche Werte und
   erwartet exit 1), LLM-Stichprobe.
5. **LLM-Kette** (LlmReviewService.java, 692 Zeilen): Antwortvalidierung strikt (exakte
   Fallabdeckung, verdict-Alphabet, PASS ⇔ findings leer, nichtleere reasoning), Verdict
   FAIL > UNVERIFIED > PASS, transportVerified nur bei vollständiger Belegkette, Neustart-
   und Manipulationspfade geprüft, API-Schlüssel nie in Status/Dateien (redact, getestet),
   Antwortgröße/Timeouts begrenzt, HTTP nur HTTPS (lokal Ausnahme), Konfiguration atomar
   validiert. JS-Gegenstück llm-evidence.js prüft dieselbe Kette unabhängig nach — der
   Zertifikats-LLM-Haken ist nur bei vollständig belegtem PASS grün.
6. **Automatische Tests (11 Klassen, 1246 Zeilen)**: LlmReviewServiceTest deckt die
   entscheidenden Fehlerpfade (FAIL nie PASS, missing/truncated/wrong-model → ERROR +
   retryExhausted, identische Wiederholungsrequests, Restart, Timeout, Key-Leak,
   Cross-Check gegen llm-evidence.js per node-Subprozess); GlobaleVerifikationTest deckt
   SQL-Ausfall → Lauf rot; ReviewLaufRegressionTest pinnt Einzel-Fail → Phase rot,
   ABGEBROCHEN → rotes Deckblatt, Budget-Hypothese-vs-Workflow-Trennung; KassenIsolation
   (DB) prüft Präfix-Scoping und Wildcard-Aktenzeichen; MdBerichtFilterTest pinnt
   Passwort-Freiheit; KiPruefAnhangTest pinnt Transport/Fach-Trennung.
7. **Kontaminationsschutz**: pruefeKeineAltenAktenzeichen, transaktionaler Cleanup,
   präfix-scoped Kassenlauf, DB-Identitätsabgleich bei externem Webziel.
8. **MdBericht als KI-Grundlage**: keine NIO-Filterung, Soll/Ist mit SQL und Parametern,
   eingebettete Selbst-Anweisung „NIO-Zeilen sind Fehler", Passwort-Freiheit zweischichtig.
9. **Exit-Codes**: fehlgeschlagener Lauf nie Exit 0 (SweetMain), Konfigurationsfehler 2.
10. **Doppelklick/Idempotie**: mehrschichtig (Flag + COUNT + Journal + Budget + keine
    Freigabe); negDoppelterMonat nutzt die NEUE Vorgangsnummer (schwerster Fall).
11. **Vier-Augen**: FREIGEGEBEN_VON = kollege komplett in SQL; Global-Check „keine Übergabe
    ohne Freigabevermerk"; Web-Neustart kann keine Selbstfreigabe erzeugen (Kollegen-Identität
    wird wiederhergestellt).
12. **Signatur-/Dedup-Logik**: fachlich wirksame Eingaben vollständig in beiden Signaturen;
    keine Crash-Pfade im Generator gefunden.

---

## 7. Empfohlene Fix-Reihenfolge (Vorschlag)

1. CR-104 + CR-106 + CR-117 (Zertifikat spricht LLM-Befunde aus, deutliches FAIL-Bild,
   einheitliche Gaten-Semantik) — direkt auf die Nutzeranforderung.
2. CR-102 + CR-103 (Kassenlauf-Erwartungen schärfen; WEB-Cleanup).
3. CR-101 (Erwartungsberechnung vom Systemcode entkoppeln — größter Hebel, größter Aufwand;
   bis dahin Formulierungen in Bericht/Zertifikat anpassen).
4. CR-105 (Login-Abwehr semantisch prüfen), CR-113 (Report-Fallback), CR-116 (GUI-Label).
5. CR-107 bis CR-112, CR-114, CR-115, CR-118 bis CR-120 (MITTEL-Block).
6. NIEDRIG-Block nach Belieben; Z-7 (ungerade Stress-Zahlen) als eine Zeile in
   pruefeKonfiguration (nur gerade zulassen) schnell erledigt.

**Ende des Berichts.** Erzeugt am 2026-10-13 (Abend) als reine Beobachtung; keine Code-Änderungen
vorgenommen. Geeignet zur Übergabe an eine andere KI für Fix-Umsetzung und Gegenprüfung.
