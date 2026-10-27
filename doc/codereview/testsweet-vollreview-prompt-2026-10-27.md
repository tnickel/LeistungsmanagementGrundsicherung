# Vollständiger Codereview-Auftrag: testsweet — Gesamtprüfung des Mitarbeiter-Simulators

Du bist ein unabhängiger, adversarieller Prüfer. Dieser Auftrag wird MEHREREN KI-Modellen
unabhängig voneinander gestellt; dein Bericht steht für sich allein und wird mit den
Berichten der anderen Modelle verglichen. Prüfe neutral und ohne Gefälligkeit: Du bist
weder dem Autor noch dem Projekt verpflichtet — nur der Wahrheit. Ein widerlegter Verdacht
ist ein wertvolles Ergebnis; ein behaupteter Fund ohne Beleg ist wertlos.

**Kennung:** Beginne deinen Bericht mit deinem Modellnamen und dem Prüfdatum. Ablage als
`doc/codereview/testsweet-vollreview-<Modellname>-<Datum>.md`.

## 1. Was testsweet ist — und warum sein eigener Fehler doppelt wiegt

testsweet ist das **Prüfinstrument** für die Fachsoftware SDN.Leistung (LMG,
Leistungsmanagement Grundsicherung nach SGB XII — Grundsicherung im Alter/bei
Erwerbsminderung, Java 21, Oracle, eigene Transaktionssteuerung als TP-Monitor-Ersatz).
testsweet simuliert Sachbearbeiter: N parallele Mitarbeiter arbeiten einen Auftragstopf ab,
je Lauf in **Modus 1 (Fachschicht** — direkte Service-Aufrufe) oder **Modus 2 (Web** —
Selenium steuert echte Chrome-Fenster gegen die LMG-Weboberfläche) und je **Kategorie**
(POSITIV, NEGATIV, ECHT, RECHNER, LAST, STRESS). Nach jedem Lauf wird der
Datenbank-Endzustand per SQL verifiziert; PDF/Markdown-Testdokumentationen entstehen je
Lauf. Dazu: LLM-Nachrechnung (GLM als unabhängige Rechen-Instanz), ein
Zertifikats-Gesamtlauf mit Build-Bindung, ein Management-Testreport und ein GUI-Server
auf Port 8095.

**Meta-Prinzip:** Ein Fehler im Prüfinstrument erzeugt falsches Vertrauen im geprüften
Produkt (blinde grüne Läufe, unbelegt starke Zertifikate). Prüfe deshalb primär:
*Kann testsweet etwas grün oder „bestanden" ausgeben, das falsch ist?* — und erst
sekundär klassische Codequalität.

## 2. Landkarte des Codes (alles unter `testsweet/`, eigenes Maven-Modul)

- **Laufkern:** `Simulator` (Phasen, Kategorien, Limits, Laufsperre, Stress-Phasenbarriere),
  `Mitarbeiter` (Fachschicht-Worker), `WebMitarbeiter` + `WebAktion`/`WebHelfer`
  (Selenium), `Auftrag`, `SzenarioAktion` (inkl. schrittMitErwartetemFehler),
  `Erwartung` (SQL-Erwartungen), `Fachzustand` (Vorher/Nachher-Zustandsgriff),
  `Verifikator`, `GlobaleVerifikation`, `RunMetrics`
- **Aufträge:** `SzenarioBibliothek` (kuratierte Szenarien, feste Grenzfälle,
  Positiv-/Negativ-Kombinationsgeneratoren mit Signatur-Eindeutigkeit, ECHT-/RECHNER-Töpfe),
  `WebSzenarioBibliothek`, `TestfallKatalog` (GUI-Katalogtexte inkl. Vorwörter)
- **Beweiskette:** `RunDataScope` (Laufkennung/Besitz), `RunChronology`
  (Laufreihenfolge-Kandidaten), `BuildEvidence` (Quellen-/Build-Bindung),
  `NachweisIntegritaetTest`-Logik, `SweetLog`
- **Berichte:** `Testdokumentation` (atomar veröffentlichen), `SweetPdf` (cp1252-Erzeugung),
  `MdBericht` (KI-lesbar), `LastBericht` (Aggregate), `Rechenblock` (dokumentierte Rechenwege)
- **Server/GUI:** `SweetServer` (alle /api-Endpunkte, statische Dateien), `SweetMain`
  (CLI run/web/GUI), `DbErreichbarkeit` (DB-Preflight), `BrowserProzesse` (Profil-Besitz),
  Ressourcen `src/main/resources/sweet/` (index.html, app.js, style.css)
- **LLM-Prüfung:** `LlmReviewService`, `LlmAnswerValidator` (Antwortvertrag),
  `LlmCalculationReference`, `LlmConfig`, `KiPruefAnhang`, Prüfpaket-/Dokumenterzeugung
- **Zertifikat:** `CertificateSuiteService/State/Worker`, `CertificateRuntimeSnapshot`,
  `SimulatorDefaults`; Werkzeuge außerhalb des Moduls: `doc/testzertifikat-werkzeug/`
  (strenger Nachweis) und `doc/testreport-werkzeug/` (Management-Report)
- **Eigene Tests:** `testsweet/src/test/java/` (333 Tests, DB-abhängige nur mit TX_URL)

Verschaffe dir selbst ein Bild: Lies die Klassen, die du prüfst — dieser Prompt ersetzt
kein Codelesen. `testsweet/README.md` und `AGENTS.md` (Projektroot) beschreiben Anspruch
und Grenzen; `doc/rechtsgrundlagen.md` ist der Fachanker (amtliche Quellen:
gesetze-im-internet.de — bei Webzugang dort verifizieren).

## 3. Prüfgrundsätze (verpflichtend)

1. **Beweispflicht:** Jede Aussage mit Datei und Zeile belegen (`pfad:zeile`) oder als
   ausdrückliche Vermutung kennzeichnen.
2. **Nachrechnen:** Fachwerte selbst rechnen (RBS 2026: 563/506/451/471/390/357 €;
   G-Mehrbedarf 17 % = 95,71 €; Erwerbsfreibetrag 30 %, Deckel 281,50 €; Schonvermögen
   10.000 € je Volljährigem). „Plausibel" ohne Rechnung ist kein Befund.
3. **Anti-Tautologie:** „Der Test/Sicherung X verhindert Y" gilt nur, wenn du zeigst,
   WIE er bei Y rot/rotz wird. Suche nach Sicherungen, die nur behauptet sind.
4. **Blinde-Grün-Suche:** Konstruiere konkret (als Gedankenspiel mit Belegstellen), wie
   ein fachlich falsches Verhalten dennoch einen grünen Lauf erzeugen könnte.
5. **Widerlegungen ausformulieren** mit Begründung — sie schützen vor Doppelarbeit.
6. **Historie respektieren:** Frühere Reviews existieren (u. a. CR-101..120, 2026-10-13:
   Erwartungswert-Zirkelschluss, Kassenlauf-0-Übergaben, LLM-Findings im Zertifikat,
   Login-Abwehr; behoben in den Commits bis `4f16245`; neue Kategorien in `64ad36e`,
   Testreport in `8886ccb`). Rapportiere längst Behobenes nicht als neuen Fund — prüfe
   aber, ob die damaligen Fixes WIRKLICH halten (Fix-Regression).

## 4. Prüffelder

### A — Verifikationsstrenge (Kernfeld)

1. `Erwartung`-Arten (betrag/ganzeZahl/text/einsVon) und die SQL-Auswertung im
   `Verifikator`: Kann eine Erwartung trivial erfüllt sein (z. B. leere Ergebnismenge,
   NULL-Vergleich, falsche Parameterbindung)? Prüfe je Erwartungsart an echten Beispielen.
2. Basisraten-Falle: Welche Erwartungen sind so formuliert, dass sie bei fast jedem
   Datenbankzustand grün wären? Zähle und bewerte sie.
3. Vorher/Nachher-Vergleich bei Negativfällen (SHA-256 über alle Fachtabellen, erlaubte
   Ausnahmen): Ist die Tabellenliste vollständig (neue Tabellen/Spalten mitgedacht)?
   Kann ein Abwehrfall etwas verändern, das der Vergleich nicht sieht?
4. `GlobaleVerifikation`: Decken die globalen Prüfungen die läufigen Lücken (Übergabe ohne
   Freigabe, Budget-Negativität, Doppelzahlungen je Monat/BG)? Was fehlt?
5. Timing: Werden Erwartungen gegen den ENDAUSLESE-Zustand geprüft (nach Commit aller
   Worker) — oder können Race-Fenster grüne Läufe fälschen?

### B — Auftraggerzeugung und Fachlichkeit

1. Kuratierte Szenarien (`SzenarioBibliothek`): Fachregeln und §-Zitate korrekt
   (§ 30 Abs. 1/2/3/6/7, § 35, § 82 Abs. 2/3, § 90, § 43, § 44)? Handgerechnete
   Grenzfälle (Aufträge 11–19: 2.822,24 / 3.312,80 / 3.750,80 / 1.063,00 …) selbst
   nachrechnen.
2. Kombinationsgeneratoren: Ist die Signatur-Eindeutigkeit (kein Fall doppelt)
   wirklich garantiert — bei 500 Fällen, bei Seed-Wiederholung, bei Überlauf des
   Suchraums? Verteilungsschiefen, die Struktur systematisch ausblenden?
3. ECHT/RECHNER-Töpfe: Stimmen die festen Sollwerte mit den dokumentierten Quellen
   (`doc/belege/echt-und-rechner-faelle-2026-10-27.md`) überein, und sind die Quellen
   korrekt zitiert (Anlage § 28 SGB XII, BSG B 8 SO 4/24 R, Altenburger KdU-Richtlinie
   27.05.2026, externer Rechner steuerschroeder.de Rechtsstand 03.07.2026)?
4. Wo genau kommen Sollwerte her (Referenzrechnung vs. Handwert vs. externer Wert)?
   Wird an JEDER Stelle (Code-Kommentar, Katalog, README, Berichte) ehrlich
   unterschieden — oder irgendwo mehr Unabhängigkeit behauptet als geprüft wird?

### C — Konkurrenz, Isolation, Ressourcen

1. Laufsperre (`laufAktiv` AtomicBoolean), Phasenbarriere des Stresstests, Worker-Pool:
   Deadlock-/Leak-Szenarien? Doppelstart über CLI und GUI gleichzeitig?
2. `RunDataScope`-Besitz: Kann ein Lauf fremde Daten aufräumen oder eigene verlieren
   (UUID-Laufkennung, Präfix-Bildung, „alte Daten bleiben nach GUI-Neustart")?
3. DB-Pool im Simulator: Verbindungsrückgabe in allen Fehlerpfaden, Pool-Erschöpfung
   unter LAST/STRESS, Timeout-/Denkzeitbehandlung.
4. Kassenlauf-Isolation (eigene BEREIT-Zahlung je Kassenauftrag): Kann parallele
   Fremdfreigabe die Sollmenge verfälschen — ist das explizit gesichert?

### D — Web-Modus (Selenium)

1. `WebMitarbeiter`/`WebAktion`: Robustheit (Treiber-Neustart, Renderer-Freeze — ein
   reales Vorfall vom 13./14.10.2025/2026 existiert: zwei Web-Läufe starben
   „nach Vorgang anlegen" ohne Timeout), Screenshot-Beweiskette je Schritt.
2. Vergleichbarkeit Web vs. Fachschicht: Werden dieselben Endzustands-Erwartungen
   geprüft — oder schwächere?
3. `BrowserProzesse`: Löscht/killt der nur Prozesse/Profile nachweislich eigenen Besitzes
   (`.testsweet-profile`-Marker, Elternprozess-Prüfung)? Gefahr für Nutzer-Chrome?
4. `stoppe()`-Verhalten: Browser-Aufräumen nur noch nach Web-Läufen — kontrafaktisch
   prüfen (Web-Lauf, der VOR Worker-Anlage scheitert: verwaiste Fenster möglich?).

### E — SweetServer (API und statische Dateien)

1. Alle Endpunkte systematisch: Fehlercodes (400/404/405/409/503), Guards (Laufsperre,
   Suite-Sperre, Busy-Flags), Timeouts, JSON-Body-Handling. Fehlende Guards?
2. Authentifizierung/Bindung: Hört der Server auf Loopback? Was kann ein lokaler
   anderer Prozess (oder eine böse Webseite via Browser) über die API anstoßen
   (CSRF-Überlegung: FormData/Content-Type-Prüfung bei POST-Endpunkten)?
3. Statische Dateiauslieferung (`statischeDatei`): Pfad-Traversal sicher? Content-Types?
4. Pflicht-Sicherheit: `/api/start` mit DB-Preflight (503 statt Laufordner), Doppelstart
   409 — vollständig auf alle startenden Ressourcen angewandt?

### F — GUI (index.html / app.js / style.css)

1. XSS: Kommt ALLE Serverdaten-Interpolation durch `esc()`? Suche systematisch nach
   Ausnahmen (Template-Literals, innerHTML, neuere Codepfade wie Testfall-Dialog,
   LLM-Panel, Suite-Panel).
2. URL-Autolink im Testfall-Dialog (`esc(...)` dann `replace(/https?.../)` + `<br>`):
   Injection widerlegen oder belegen (klammern/quotes im URL-Matcher).
3. Zustandslogik: Polling, Knopf-Zustände bei Fehlern, Wiedereinstieg nach Reload.

### G — LLM-Nachrechnung (Abschnitt 7 der GUI)

1. Antwortvertrag (`LlmAnswerValidator`): Was passiert bei formal ungültiger, teilweise
   gültiger, „über-eifriger" (zusätzlich ungefragter) Modellantwort? Sind die Regeln
   bei Java UND Node (Paritätskorpus!) identisch definiert?
2. Wiederholungslogik (max. 3 Sendeversuche, 30 s Pause, gezielter Korrekturhinweis,
   dann Abbruch): passt zur Doku? Identisches Resend bei Temperatur-Entropie sinnvoll?
3. Prüfpaket: Sind Prüfauftrag, Gesetzesgrundlagen und Fallgruppen vollständig und
   manipulationsfrei unterwegs (Dokumenttexte als Datei mit SHA-256, Transport belegt)?
   **Prompt-Injektion:** Fallinhalte stammen aus dem Generator — kann ein generierter
   Falltext (Namen, Aktenzeichen, Beträge) den Prüfauftrag sinnlich verändern?
4. Schlüssel: lokale Datei git-ignoriert? Landet der Schlüssel nie in Logs/Berichten/
   Antworten (`LlmConfig`, Statusendpunkte)?

### H — Zertifikats-Gesamtlauf und Beweiskette

1. `CertificateSuiteWorker`-Jobs (7 Kombinationen, Summe 1392): Decken sie das
   behauptete Spektrum? Bewusst NICHT enthalten: ECHT/RECHNER — Lücke oder
   dokumentierte Entscheidung?
2. `BuildEvidence`/Runtime-Kopie (gehasste Kopie): Kann ein Lauf gegen veränderte
   Klassen „grün" bleiben? Wann veraltet ein Beleg (Kriterien prüfen)?
3. `RunChronology`: Wann gilt ein Lauf als „jüngster/maßgeblicher" — Mehrfachläufe,
   Sekundengleichheit, abgebrochene Läufe?
4. Verdict-Logik (BESTANDEN/BEDINGT/NICHT BESTANDEN) des Zertifikatswerkzeugs
   (`doc/testzertifikat-werkzeug/certificate-evidence.js`): Welche Kombination von
   Belegen erzwingt welches Verdict? Konstruiere eine schwächste Beleglage, die
   trotzdem „BESTANDEN" ergibt — ist das vertretbar?

### I — Berichtswesen

1. `Testdokumentation`-Atomarität (erst fertige Dateien, dann veröffentlichen):
   Bewiesen oder nur angestrebt? Verhalten bei Plattenfehler mittendrin.
2. `SweetPdf` (cp1252!): Zeichensatz-Fallen (€, §, Umlaute, „…"): systematisch suchen;
   Stream-Marker-Beweise für PDF-Inhalte („Beweis per Marker") — fälschbar?
3. `MdBericht`: wirklich frei von Passwörtern/Sozialdaten? KI-Nachprüfbarkeit gegeben
   (alle Rechenblöcke vollständig)? Werden neue Kategorien korrekt ausgewiesen?
4. Fälschungsschutz: Kann jemand (oder ein Bug) einen „grünen" Laufordner nachträglich
   so verändern, dass Zertifikat/Report ihn weiterhin zählen (Hashes, Verify-Logik)?

### J — Betrieb

1. `DbErreichbarkeit`-Preflight: Fehlertexte, jdbc:fixture-Ausnahmen, kein Laufordner
   bei DB-aus — vollständig?
2. `SweetMain` CLI: Exit-Codes, Argumentvalidierung, Kategorie-/Modus-Guards
   (ECHT/RECHNER/STRESS nur Fachschicht) auf CLI- UND Server-Pfad.
3. Ports: SweetServer 8095 vs. LMG-Server 8080 — Kollisionsverhalten, Zertifikatslauf
   mit eigenen Ports (`sweet.requireEmbedded`), belegter-Port-Pfad.
4. start.bat/stop.bat/stop.ps1 (Root): sqlplus-Wartelogik, Prozessverifikation
   (kein Fremdkill), docker-stop-Zweig.

### K — testsweet-eigene Tests (333)

1. Welche Tests sind stark (würden einen echten Regression brechen) und welche
   tautologisch/schwach? Nenne die 5 schwächsten mit Begründung.
2. Flaky-Risiken: Zeitabhängige (assertTimeoutPreemptively 2 s), umgebungsempfindliche
   (offene Chrome-Prozesse, DB-Kaltstart) — Systematik statt Einzelfälle.
3. Fehlende Tests als L-<Nr> mit konkretem Vorschlag (Klasse + Szenario), insbesondere
   für die Server-Endpunkte der neuen Features, stop.ps1, Berichtswerkzeuge.

### L — Querschnitt

1. Namens-/Sprachregeln: „SDN.Leistung" in neuen Artefakten, kein Vorbild-Name, Doku
   deutsch/Bezeichner englisch — über ALLE neuen Dateien der letzten Commits.
2. Doku-Wahrheit: README/Katalog/Vorwörter/AGENTS.md vs. Code-Realität (Zahlen,
   Grenzen, „kein Kapazitätsnachweis"-Ehrlichkeit).
3. Config/`SimulatorDefaults`: sinnvolle Vorgaben, dokumentiert?

## 5. Berichtsformat

`doc/codereview/testsweet-vollreview-<Modellname>-<Datum>.md`, Aufbau:

1. **Kopf:** Modellname, Datum, geprüfter Commit-Stand, Kurzverdict.
2. **Fundtabelle:** `CR-V<NN>` je Fund mit Schweregrad BLOCKER / KRITISCH / MITTEL /
   GERING / INFO, Fundort (Datei:Zeile), Beschreibung, Beweis, Behebungsempfehlung.
3. **Entlastungen:** widerlegte Verdachte als eigene Einträge „[widerlegt]" mit Begründung.
4. **Testlücken:** `L-<NN>` mit konkretem Testvorschlag.
5. **Nachrechenprotokolle:** die in B geforderten Rechnungen vollständig darstellen.
6. **Blinde-Grün-Analyse:** die drei realistischsten Wege, wie testsweet etwas Falsches
   grün/zertifiziert ausgeben könnte — mit den zugehörigen Codestellen.
7. **Verdict:** BESTANDEN / BEDINGT / NICHT BESTANDEN + die drei wichtigsten
   Empfehlungen.
8. **Vergleichskopf** (für den Auftraggeber): die 5 wichtigsten Funde als je 1-Zeilen-
   Kurzfassung am Berichtsanfang, damit mehrere KI-Berichte schnell vergleichbar sind.

Prüfe so gründlich, als würde die Zuverlässigkeit jedes grünen Häkchens dieses
Projekts von deinem Bericht abhängen — denn genau das ist der Fall.
