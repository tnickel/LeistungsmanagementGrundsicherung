# Codereview-Auftrag: Neue testsweet-Features (ECHT, RECHNER, Testreport, Betrieb) — umfassende Gegenprüfung

Du bist ein adversarieller, unabhängiger Code- und Fachprüfer. Du prüfst NEUTRAL — du bist
weder dem Autor der Änderungen noch dem Projekt verpflichtet, sondern nur der Wahrheit.
Dein Bericht wird Grundlage für Nachbesserungen; übertreibe nichts, beschönige nichts,
widerlege falsche Verdachte ausdrücklich (ein widerlegter Fund ist ein wertvolles Ergebnis).

## Projekt-Kontext

- **Software:** SDN.Leistung (LMG) — Leistungsmanagement Grundsicherung nach SGB XII
  (Grundsicherung im Alter / bei Erwerbsminderung, NICHT SGB II). Java 21, Oracle,
  bewusst keine Frameworks. Eigene Transaktionssteuerung (Ersatz TP-Monitor).
- **testsweet:** Eigenständiger Mitarbeiter-Simulator (`testsweet/`, eigenes Maven-Modul):
  N simulierte Sachbearbeiter arbeiten parallel einen Auftragstopf ab — Modus 1 (Fachschicht,
  direkte Service-Aufrufe) oder Modus 2 (echter Chrome-Browser per Selenium). Je Lauf:
  Schritte spielen → SQL-Verifikation direkt auf den Oracle-Tabellen → PDF/Markdown-
  Testdokumentation. Kategorien bisher: POSITIV, NEGATIV, LAST, STRESS.
- **Namensregel:** Bezeichnung „SDN.Leistung"; der Name des Vorbild-Systems (KDN) darf in
  NEUEN Artefakten nicht auftauchen. Doku deutsch, Code-Bezeichner englisch (deutsche
  Fachbegriffe wie SozialFall/Bedarfsgemeinschaft bleiben erlaubt).
- **Fachanker:** `doc/rechtsgrundlagen.md` ist das maßgebliche Rechtsquellen-Verzeichnis.
  Amtliche GesetzesTexte: gesetze-im-internet.de (bei Webzugang dort verifizieren).
- **Rechenanker 2026:** RBS 1–6 = 563/506/451/471/390/357 € (Nullrunde, seit 2024
  unverändert), Schonvermögen 10.000 € je volljähriger Person (+500 € je weiterer
  unterhaltener Person), Kfz-Orientierung 7.500 €, G-Mehrbedarf 17 % der RBS 1 = 95,71 €,
  Erwerbsfreibetrag 30 % (gekappt bei 50 % der RBS 1 = 281,50 €).

## Was neu ist (Prüfgegenstand, Commits auf Branch `learning`)

- `35da248` Lernpfad: „Kurz gesagt"-JavaDoc-Kurzfassungen je Klasse + 4 package-info.java
  (nur Kommentare — stichprobenartig prüfen, kein Schwerpunkt)
- `0203134` Betrieb: start.bat wartet per echtem sqlplus-Login im Container statt auf den
  nicht vorhandenen Docker-Healthcheck (max. 300 s); NEU stop.bat/stop.ps1 (stoppt
  Fachanwendung auf LMG_PORT, mit Argument `db` zusätzlich den Container lmg-oracle;
  Prozessverifikation über Kommandozeile)
- `64ad36e` testsweet: **Kategorie ECHT** (5 belegte echte Fälle) und **Kategorie RECHNER**
  (12 mit externem Online-Rechner abgeglichene Fälle), hinter den Negativfällen; nur
  Fachschicht; Limits 5/12; Vorwörter je Kategorie im Testfälle-Dialog (Wozu/Erzeugung/
  Prüfung); URLs im Dialog klickbar; Bestandsfix Simulator.stoppe(); Belege + Recherche
  unter `doc/belege/` und `doc/testdaten-echte-faelle-recherche-2026-10-27.md`
- `8886ccb` testsweet: **Management-Testreport** — neues Werkzeug `doc/testreport-werkzeug/`
  (publish-report.js, runs.js) + GUI-Knopf + Endpunkte `/api/testreport`,
  `/api/testreport/pdf`; 12-Seiten-PDF mit Titelbild, Inhaltsverzeichnis mit echten
  Seitenzahlen (Zwei-Durchlauf über pdftotext), fixen Erklärtkapiteln, dynamischer
  Ergebnistabelle und Zertifikatsseite; Auslieferung nach `doc/SDN-Leistung-LMG-Testreport.{docx,pdf}`
- `1555854` testsweet-README: Dokumentation der neuen Kategorien

Kern-Dateien der Änderungen:
`testsweet/src/main/java/de/allgemein/testsweet/` → `SzenarioBibliothek.java` (erzeugeEcht,
erzeugeRechner, belegterFall, echtAbwehrFall, person), `Simulator.java` (Kategorie-Enum,
auftraegeLimit, erzeugeTopf, stoppe, webUnzulaessig), `SweetServer.java` (Guards +
/api/testreport*), `SweetMain.java` (CLI), `TestfallKatalog.java` (Vorwörter);
`testsweet/src/main/resources/sweet/` → `index.html`, `app.js`;
`testsweet/src/test/java/.../EchtRechnerFallTest.java`;
`doc/testreport-werkzeug/`, `doc/belege/`, `stop.bat`, `stop.ps1`, `start.bat`.

## Prüffelder

### A — ECHT-Fälle: fachliche Richtigkeit (nachrechnen, nicht glauben)

1. Rechne JEDE Handrechnung der fünf ECHT-Fälle selbst nach (mit den obigen Fachankern):
   ECHT-ANLAGE-KDU (erwartet 1.542,00 €), ECHT-BSG-VERMOEGEN (Abwehr; Restbedarf 123,00 €,
   verwertbar 34.500 €), ECHT-SCHONVERMOEGEN-800 (1.023,00 €, kein Einsatz),
   ECHT-LSG-KFZ-PAAR (Abwehr; verwertbar 11.000 €), ECHT-KDU-DECKEL (Kappung 700→590,
   1.153,00 €). Stimmt jede Summe einzeln? Passt die Schonvermögen-Anwendung (Paar =
   2 × 10.000 €)?
2. Prüfe die Abbildungstreue zu den Quellen: Wurden die Werte der Anlage zu § 28 SGB XII
   (563/506/…), des BSG-Urteils B 8 SO 4/24 R (44.500 € Vermögen), des LSG-Kfz-Falls
   (31.000 €), des Lebenshilfe-800-€-Beispiels und der Altenburger KdU-Richtlinie
   (Richtwerte 260/320/…, Betriebs-/Heizkostengrenzen, 1,5-fache Nettokaltmiete-Obergrenze
   390/480/… ab 01.07.2026) korrekt übernommen? Bei Webzugang: gegen die in
   `doc/belege/echt-und-rechner-faelle-2026-10-27.md` genannten Quellen verifizieren.
3. Sind Ergänzungen (z. B. Rente 900 € beim BSG-Fall, KdU der Richtlinie) je Fall klar als
   Ergänzung dokumentiert — oder werden sie unmarkiert als Teil der Quelle ausgegeben?
4. ECHT 5 nutzt als BG-Grenze die SUMME der Richtlinien-Obergrenzen (390+90+110 = 590 €),
   obwohl die Richtlinie nur die Nettokaltmiete deckelt (Produktlogik kappt die
   Gesamt-KdU-Summe). Ist diese Übertragung im Fall transparent begründet oder als
   „Richtlinien-Prüfung" unzulässig geschönt?

### B — RECHNER-Fälle: Unabhängigkeit und Nachrechnbarkeit

1. Die 12 Sollwerte (978,00 / 478,00 / 583,00 / 1.063,00 / 1.073,71 / 268,71 / 558,00 /
   768,00 / 1.427,00 / 727,00 / 227,00 / 1.077,00 €) je Konstellation selbst nachrechnen.
2. Bei Webzugang: mindestens 3 der Fälle selbst im externen Rechner
   (steuerschroeder.de/Steuerrechner/Grundsicherung.html, Rechtsstand 03.07.2026) eingeben
   und die Werte abgleichen. Ohne Webzugang: plausibilitätsprüfen und als
   „nicht extern verifiziert" kennzeichnen.
3. Der externe Rechner kennt weitere Regeln, die das Produkt nicht abbildet (§ 82a
   Grundrenten-Freibetrag, Witwenrente, Altersvorsorge nach § 82 Abs. 4). Prüfe, ob die
   Fälle diese bewusst meiden und ob die Eingrenzungen (Betriebskosten 0, zentrales
   Warmwasser, kein Vermögen) als solche dokumentiert sind — oder ob die Auswahl die
   Vergleichbarkeit schönt.
4. Ist die Aussage „alle zwölf Ergebnisse stimmten exakt" durch Belege gedeckt
   (Screenshot `doc/belege/rechner-steuerschroeder-fall12-2026-10-27.png` zeigt nur Fall 12 —
   reichen die im Code/Beleg-Doku je Fall dokumentierten Werte als Nachweis)? Bewerte die
   Beweislage für die anderen elf Fälle.

### C — Zirkelschluss-Behauptungen (der zentrale Qualitätsanspruch)

1. Prüfe je Kategorie, WO der erwartete Wert herkommt: ECHT (Handwerte im Code? oder doch
   Referenzrechnung?), RECHNER (externer Wert fest im Code?), POSITIV (Referenz + Goldwerte
   in doc/regressionstests.md), NEGATIV (erwarteter Fehlercode).
2. In `belegterFall`/`echtAbwehrFall` werden Rechenblöcke über die Produkt-Referenz
   (`Leistungsberechnung.berechne`) erzeugt und Nur-zum-Teil feste Handwerte geprüft
   (Zahlbetrag fest, Vermögenseinsatz/Endvermögen aus der Referenz). Ist die Dokumentation
   (README, Katalog, Beleg-Doku) dabei ehrlich über das Maß der Fremdbewertung — oder
   behauptet sie mehr Unabhängigkeit, als der Code prüft?
3. Der neue Test `EchtRechnerFallTest` vergleicht Referenz gegen externe Konstanten.
   Bewerte: Reicht das als „Bruch des Zirkelschlusses", und ist die Formulierung an allen
   Stellen (Katalog, README, Testreport Kapitel 3) entsprechend präzise?

### D — Kategorie-Integration und Guards

1. Kategorie-Enum, Limits (ECHT 5, RECHNER 12), Aktenzeichen-Präfixe (`-E-`, `-R-`),
   erzeugeTopf: konsistent? Was passiert bei anzahl < Topfgröße (subList) — sind die
   erwarteten Teiltöpfe sinnvoll und getestet?
2. Web-Sperre für ECHT/RECHNER auf ALLEN Ebenen prüfen: Simulator.starteWeb,
   laufWebAusfuehren, SweetServer (/api/start), SweetMain CLI (web), app.js. Gibt es einen
   erreichbaren Pfad, der ECHT/RECHNER im Web-Modus startet und dann unkontrolliert scheitert?
3. Testfallkatalog: Vorwörter je Kategorie — stimmen die behaupteten Zahlen mit dem Code
   überein (z. B. „zehn kuratierte Arten", „Aufträge 11–19", „neun Abwehrarten")? Prüfe
   an `SzenarioBibliothek.erzeuge/erzeugeNegativ`.
4.MdBericht/Testdokumentation/GUI-Status: Werden die neuen Kategorien überall korrekt
   benannt, oder gibt es Stellen, die stillschweigend POSITIV-Annahmen machen
   (z. B. Massentest-Berichtpfade LAST/STRESS-only)?

### E — Abwehrfälle (KEIN_ANSPRUCH) fachlich und technisch

1. ECHT 2/4 erwarten KEIN_ANSPRUCH mit Hinweistext „Einkommen/Vermögen übersteigen den
   Gesamtbedarf". Prüfe im Produkt (`SozialLeistungsService`, zwei Throw-Stellen), ob die
   Vermögens-Abwehr tatsächlich diese Meldung liefert — und ob der Abwehrgrund
   (Vermögen, nicht Einkommen) im Prüfkonzept sauber getrennt ausgewiesen wird.
2. Sind die Zustandserwartungen der Abwehrfälle vollständig (keine Zahlung, Budget
   unverändert, Vermögen unverändert, …)? Fehlt eine sinnvolle Erwartung (z. B. Journal)?
3. Der Testreport und der Katalog begründen die Abwehr mit „verwertbares Vermögen deckt
   den Bedarf". Deckt sich das mit der tatsächlichen Produktformel
   (Zahlbetrag = Bedarf − Einkommen − verwertbares Vermögen; bei negativ → Abwehr)?

### F — Management-Testreport-Werkzeug

1. Zwei-Durchlauf-TOC: Die Anker (`SUCHER`, zeilenverankerte umlautfreie Regex) —
   funktionsfähig und robust? Was passiert, wenn eine Kapitelüberschrift durch
   Schriftsatz/Umbuch auf eine andere Seite rutscht, wenn pdftotext fehlt, oder wenn ein
   Kapitelanker nie matcht („null" im TOC — gibt es eine Absicherung)?
2. Verify-Mechanik: `stand-report.json` + SHA-256 der PDF. Kann eine veraltete Report-PDF
   trotzdem ausgeliefert werden (Race zwischen Verify und Auslieferung in
   `/api/testreport/pdf`, TOCTOU)? Ist der Verify ausreichend, wenn zwischenzeitlich neue
   Läufe entstehen (Report „aktuell" laut Hash, aber inhaltlich veraltet)?
3. `runs.js`: LäuftAuswertung (jüngster Lauf je Kategorie nach Ordnername lexikalisch —
   korrekt bei mehreren Läufen pro Sekunde?), junitStand (Summieren ALLER surefire-XMLs
   im target — mischt das Überreste mehrerer Läufe?), ABGEBROCHEN-Klassifikation
   (verifiziert=0 && !erfolgreich) — kann ein echter FEHLGESCHLAGEN-Lauf fälschlich als
   ABGEBROCHEN gelten (oder umgekehrt)?
4. Report-Zertifikatsseite: Gesamturteil „BESTANDEN" iff alle ausgewerteten Läufe
   erfolgreich. Prüfe kritisch: Kann das Urteil zu leicht BESTANDEN lauten (z. B. wenn nur
   ECHT+RECHNER frisch sind und WEB nie lief — steht das then im Urteil/Kapitel 7
   sichtbar genug)? Ist die Abgrenzung zum formalen Testzertifikat überall klar
   (Report informiert / Zertifikat weist nach)?
5. Committete Ergebnis-PDFs/-DOCX (`doc/SDN-Leistung-LMG-Testreport.*`) im Repo: sinnvoll
   (Nachweischarakter) oder problematisch (veralten zwangsläufig — Verify regelt das)?
   Bewerte Repository-Hygiene inkl. `doc/belege/`-Screenshot.
6. Fixe Kapiteltexte: prüfe sachliche Richtigkeit und Übertreibung (z. B. „Zehntausende
   automatisch erzeugte Testfälle" — stimmt die Größenordnung mit den realen Topfgrößen
   und je-Lauf-Zahlen überein? „Deadlock mit echt erzeugtem Deadlock getestet" — existiert
   der Test wirklich noch und lief er zuletzt?).

### G — Server-Endpunkte und GUI

1. `/api/testreport` (POST): Busy-Guards (reportBusy, laufAktiv, Zertifikatssuite),
   Timeout 300 s im runProcess, Fehlerpfade (Werkzeug fehlt, node scheitert) — sauber?
   Vergleiche mit /api/zertifikat auf Systematiklücken.
2. `/api/testreport/pdf` (GET): Content-Type, no-store, inline-Disposition — konsistent
   mit /api/zertifikat/pdf?
3. app.js: `esc(e.text).replaceAll("\n","<br>")` + URL-Regex
   `/(https?:\/\/[^\s<]+)/g` NACH esc() — beweise oder widerlege eine Injection-Möglichkeit
   (Katalogtexte stammen aus TestfallKatalog.java — kann ein zukünftiger Text mit `<`,
   `"`, `'`, `)` oder Klammern eine attr-Escape erreichen?). target=_blank + rel=noopener ok?
4. GUI-Knöpfe: Statusrückmeldung, Doppelklick-Schutz (disabled), Verhalten bei
   Zertifikatssuite-Lauf (zertifikatSuiteGesperrt) — vollständig?
5. testreportlink wird bei laufender Suite NICHT wie zertifikatlink versteckt (11 Stellen).
   Bewerte: akzeptabel, weil Verify abweist — oder Inkonsistenz mit Beweis?

### H — Betrieb: start.bat / stop.bat / stop.ps1

1. start.bat: sqlplus-Login-Check (`echo 'select 1 from dual;' | sqlplus -S -L
   user/pass@localhost:1521/FREEPDB1`) — Verhalten bei Sonderzeichen im Passwort, bei
   nicht vorhandenem sqlplus im Image, bei 300-s-Überschreitung (Fortsetzung + Warnung)?
   Wird der VM-Pfad (1921) weiterhin korrekt behandelt?
2. stop.ps1 Verifikation (`Test-LmgOwner`): Können Regexes einen FREMDEN java-Prozess
   fälschlich treffen oder einen echten LmgServerMain verfehlen (direkter java-Start ohne
   Maven mit anderem -Dmaven.multiModuleProjectDirectory)? Identity-Recheck + /T-Kill:
   Nebenwirkungen? docker-stop-Zweig (`db`): ids/Status korrekt?
3. Werden die Skripte der Namens- und Sprachregel gerecht (deutsch, ASCII-Sicherheit für
   cmd, keine Geheimnisse)?

### I — Bestandsfix Simulator.stoppe()

Browser-Aufräumen jetzt nur bei nicht-leerem webMitarbeiter. Prüfe kontrafaktisch: Gibt es
einen realistischen Ablauf, in dem Chrome-Fenster existieren, webMitarbeiter aber leer ist
(Abfurt vor Worker-Anlage, Fehlschlag in starteWeb nach Treibererstellung,
CertificateSuite-Worker)? Was wäre die Folge (verwaiste Fenster)? Bewerte den Fix gegen
diese Fälle und gegen den ursprünglichen 8-s-Kostenbefund.

### J — Tests: Aussagekraft und Lücken

1. `EchtRechnerFallTest`: prüft der wirklich das, was er behauptet (externe Konstanten vs.
   Referenz vs. SQL-Sollwerte im Szenario)? Sind die BigDecimal-Vergleiche
   scale-sicher (equals vs. compareTo — Scale-Fallen)?
2. `TestfallKatalogTest`/`SimulatorBoundaryTest`-Anpassungen: Anzahl-Asserts (21/12/4/2/7/3)
   — sinnvolle Regressionssperren oder spröde? Vorwort-Test prüft nur String-Contains —
   reicht das?
3. Fehlende Tests benennen (L-Nummern): z. B. /api/testreport-Endpunkte (es gibt
   CertificateSuiteHttpTest als Vorbild), stop.ps1-Verifikation (nur manuell getestet),
   publish-report.js --verify (Fehlerpfad), runs.js-Parsing (Abgebrochen-Klassifikation),
   TOC-Anker-Robustheit.
4. LLM-Nachrechnung (GLM) und Zertifikatssuite (7 Kombinationen, Summe 1392) umfassen
   ECHT/RECHNER NICHT. Bewerte diese Lücke: bewusste Entscheidung (Nachweisdokument) oder
   versäumte Integration? Was müsste minimal angepasst werden?

### K — Dokumentation und Ehrlichkeit

1. `doc/belege/echt-und-rechner-faelle-2026-10-27.md`: stimmen Tabellen mit dem Code
   überein (Werte, Fälle, Quellen, Abrufdatum)? Ist der Hinweis auf die Grenzen
   (Überschlagsrechner, § 82a nicht abgebildet) ausreichend?
2. `doc/testdaten-echte-faelle-recherche-2026-10-27.md`: Behauptungen über die
   Nichtverfügbarkeit echter Falldaten — sachlich haltbar formuliert?
3. testsweet-README (Kapitel Testfallkategorien): korrekt und vollständig? Fehlt die
   Erwähnung in AGENTS.md („Wo nachschlagen"/testsweet-Absatz nennt Kategorien und
   Grenzen nicht mehr) — als Doku-Lücke bewerten.
4. Sprach- und Namensregel in allen neuen Dateien (inkl. Katalogtexte, Report-Kapitel,
  .ps1/.bat): „SDN.Leistung" ok, kein Vorbild-Name, Doku deutsch, Bezeichner-Konsistenz.

### L — Umfassende Querprüfung der testsweet (Bestand + Neues)

1. Haben die Änderungen STILLSCHWEIGEND Bestandsverhalten geändert (stoppe, erzeugeTopf-
   Präfixe, Katalog-Anzahlen, CLI-Meldungstexte, auf die Tests oder Doku reagieren)?
2. Rosinen-Pickung in der Report-Ergebnistabelle: nur der „jüngste Lauf je Kategorie"
   erscheint — können ältere FEHLGESCHLAGENE Läufe derselben Kategorie die Auswahl
   unzulässig dominieren (chronology.candidates im Zertifikatswerkzeug löst das streng —
   runs.js nicht)? Bewerte die Abweichung bewusst (Report informiert) oder kritisch.
3. Robustheit bei leeren/defekten Laufordnern (runs.js try/catch), fehlendem node,
   fehlendem soffice/pdftotext, vollem temp-Verzeichnis (mkdtemp + finally-rmSync?).

## Verifikationsmethodik (verpflichtend)

- **Beweispflicht:** Jede Aussage „X ist korrekt/falsch" belegt mit Datei und Zeile
  (`pfad:zeile`) bzw. Testname. Nicht belegbare Aussagen als Vermutung kennzeichnen.
- **Nachrechnen:** Alle in A/B genannten Werte selbst rechnen (Schrittfolge angeben).
  Kein „plausibel" ohne Rechnung.
- **Anti-Tautologie:** „Der Test prüft X" ist nur gültig, wenn du zeigst, WAS der Test
  bei Abweichung rot macht (Idealerweise: benenne die Zeile, die rot wird).
- **Web-Verifikation, wo möglich:** amtliche Quellen (gesetze-im-internet.de) und der
  externe Rechner. Ohne Webzugang: als nicht extern verifiziert markieren.
- **Widerlegte Verdichte ausformulieren** mit Begründung („Verdacht X trifft nicht zu,
  weil …").
- **Keine Gefälligkeit:** Wenn etwas gut ist, sag es kurz; der Wert des Berichts liegt
  in echten Funden und echten Entlastungen.

## Berichtsformat

Ablage als `doc/codereview/testsweet-neue-features-codereview-<Datum>.md` mit:

1. **Kopf:** Datum, geprüfter Stand (Commit-Hashes), Prüfumfang, Kurzverdict.
2. **Fundtabelle:** je Fund `CR-<Nr>` mit Schweregrad BLOCKER / KRITISCH / MITTEL /
   GERING / INFO, Fundort (Datei:Zeile), Beschreibung, Beweis, empfohlene Behebung.
3. **Entlastungen:** widerlegte Verdachte mit Begründung (eigene CR-Nummern mit „[widerlegt]").
4. **Testlücken:** `L-<Nr>` mit konkretem Vorschlag (Testklasse, Szenario).
5. **Fachliche Nachrechenprotokolle:** A und B mit vollständiger Rechnung je Fall.
6. **Verdict:** BESTANDEN / BEDINGT / NICHT BESTANDEN mit Begründung und den drei
   wichtigsten Empfehlungen.

Prüfe so gründlich, als müsste die Umsetzung deiner Funde dem Fachlichen und dem
Betrieb standhalten — nicht nur dem Compiler.
