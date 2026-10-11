# testsweet — Mitarbeiter-Simulator für SDN.Leistung LMG

Eigenständiger Simulator (**keine Unit-Tests**, nicht Teil von `mvn test` im
Hauptprojekt): N simulierte Sachbearbeiter arbeiten parallel einen Pool von
Anwenderszenarien ab; danach wird geprüft, ob das Erwartete in der Oracle-
Datenbank angekommen ist. Alles wird für die **manuelle Auswertung** (zusammen
mit dem Nutzer) mitgeloggt.

## Modi

1. **Fachschicht / Transaktionstest** (implementiert): Die simulierten
   Mitarbeiter rufen direkt `SozialLeistungsService` → `TxManager` → Oracle auf.
   Belastet Sperren, Deadlock-Wiederholung, Budget-Führung, Vier-Augen-Logik.
2. **Weboberfläche / Selenium** (implementiert): Die simulierten Sachbearbeiter
   steuern jeweils ein eigenes sichtbares Chrome-Fenster über Selenium WebDriver.
   Sie bedienen interaktiv die echte SDN.Leistung-Weboberfläche (Anmeldung,
   Vorgang anlegen, SozialFall erfassen, Auszahlung anordnen, Vier-Augen-Freigabe
   durch Kollegen, Storno), fertigen für jeden Schritt Screenshots an und verifizieren
   nach dem Lauf den Zustand der Oracle-Datenbank per SQL. Die Fenster werden
   im Bildschirm-Raster nebeneinander angeordnet und mit verkleinerter
   Darstellung (`--force-device-scale-factor=0.8`) betrieben, damit die Seiten
   auch in den kleinen Fenstern vollständig sichtbar bleiben. Der Storno einer
   freigegebenen (BEREIT-)Zahlung erfordert die Admin-Rolle — das Storno-Szenario
   wechselt dafür auf das Administrationskonto `web-admin` (eingebetteter Server)
   bzw. `admin-01` (externer Server).

## Start & Ausführung

Voraussetzung (einmalig und nach Änderungen am Hauptprojekt):

```bash
mvn install          # im Hauptprojekt (installiert transaktionssteuerung-0.5.4 ins lokale Repo)
```

### Option A: Interaktive Weboberfläche (Standard)
Unter Windows einfach starten:
```cmd
testsweet\start.bat
```
oder plattformunabhängig per Maven:
```bash
mvn -f testsweet/pom.xml compile exec:java
```
Die Oberfläche öffnet sich automatisch unter <http://127.0.0.1:8095/> (Port über `SWEET_PORT`).
Dort kann zwischen **Modus 1 (Fachschicht)** und **Modus 2 (Weboberfläche/Selenium)** gewählt
werden — und darüber hinaus über die Reiterleiste **Testfallkategorie** (zwischen Moduswahl und
Laufkonfiguration) zwischen **Positivfällen**, **Negativfällen** und **Lasttest** (siehe unten).

### Option B: Headless Batch-Lauf (Fachschicht, für CI/CD und automatisierte Abnahme)
Ausführung direkt über CLI ohne Browser:
```cmd
testsweet\start.bat run <mitarbeiter> <auftraege> [seed] [denkzeitMs] [kategorie]
```
Beispiel (10 parallele Mitarbeiter, 60 Aufträge, Seed 42, 0 ms Denkzeit, Positivfälle):
```cmd
testsweet\start.bat run 10 60 42 0
```
Kategorie ist optional (`POSITIV` Standard, `NEGATIV`, `LAST`), z. B. 600 Lastdurchläufe:
```cmd
testsweet\start.bat run 10 600 42 0 LAST
```

### Option C: Weboberflächen-Lauf über Selenium (Modus 2)
Ausführung über CLI mit parallelen sichtbaren Chrome-Fenstern im Bildschirmraster:
```cmd
testsweet\start.bat web <mitarbeiter> <auftraege> [seed] [denkzeitMs] [kategorie]
```
Beispiel (2 parallele Browserfenster, 4 Aufträge, Seed 42, 200 ms Denkzeit):
```cmd
testsweet\start.bat web 2 4 42 200
```
*Ablauf: Startet bei Bedarf automatisch den SDN.Leistung-Server auf Port 8080, ordnet die Chrome-Fenster nebeneinander an, spielt die Vorgänge mit Screenshot-Erfassung durch, verifiziert 100 % per SQL gegen Oracle und schreibt PDF (`/api/pdf`) sowie Markdown (`/api/md`).*

## Konfiguration

- GUI: Mitarbeiterzahl, Anzahl Aufträge, Seed (0 = zufällig; fester Seed =
  reproduzierbare Szenarien), Denkzeit je Schritt, Altdaten-Aufräumatik.
- Datenbank: `TX_URL` / `TX_USER` / `TX_PASS` (Standard: Docker-DB
  `jdbc:oracle:thin:@//localhost:1521/FREEPDB1`, tx_demo/tx_demo).
- Port: `SWEET_PORT` (Standard 8095).

## Szenario-Arten (Auftragstopf)

STANDARDRENTE (Vier-Augen-Kette), PAAR, MEHRBEDARF (§ 30), FAMILIE (Kinder),
ERWERB (§ 82), VERMOEGEN (§ 90 — inkl. Variante mit erwartetem KEIN_ANSPRUCH),
DOPPELKICK (Idempotenz), STORNO_KORREKTUR (Storno + Neuauszahlung im selben
Monat), KONKURRENZ (erwarteter Lost-Update-Schutz), KASSENLAUF (läuft parallel
zu Freigaben). Die **Erwartungshaltung** (Beträge, Status, Budgets, Versionen)
steht in jedem Szenario und wird vorab mit derselben reinen Berechnung
(`Leistungsberechnung`) ermittelt, die auch die Fachschicht nutzt.

Im **Modus 2** kommen Web-Varianten hinzu: WEB-STANDARDRENTE und WEB-MEHRBEDARF
(je mit Vier-Augen-Freigabe durch einen Kollegen) sowie WEB-STORNO (Anordnung,
Freigabe, dann Storno durch die Administration mit vollständiger Budget-Rückbuchung).

## Testfallkategorien (Positiv / Negativ / Lasttest)

Zwischen Moduswahl und Laufkonfiguration wählt die GUI die **Testfallkategorie**;
das Aktenzeichen-Präfix unterscheidet die Datenmengen in der Datenbank:

| Kategorie | Fachschicht (Modus 1) | Weboberfläche (Modus 2) | Report |
|-----------|----------------------|-------------------------|--------|
| **POSITIV** (Standard) | `SWEET-` — die 10 Szenario-Arten oben | `SWEET-W-` | `testdokumentation.pdf` / `.md` je Fall |
| **NEGATIV** | `SWEET-N-` | `SWEET-NW-` | `testdokumentation.pdf` / `.md` je Fall |
| **LAST** | `SWEET-L-` | `SWEET-LW-` | **gekürzter** `lastreport.pdf` / `lastreport.md` (nur Aggregate) |
| **STRESS** | `SWEET-S-` | — (bewusst nur Fachschicht) | **gekürzter** `lastreport.pdf` / `lastreport.md` |

**Negativfälle** (Fachschicht, 9 Arten): NEG-KEINANSPRUCH (Rente über Bedarf,
§ 43 Abs. 1), NEG-BUDGET (Startbudget unter Zahlbetrag), NEG-DOPPELMONAT
(Doppelanordnung je Monat), NEG-SELBSTFREIGABE (Vier-Augen-Verletzung),
NEG-DOPPELFREIGABE (Freigabe einer BEREIT-Zahlung), NEG-STORNO-KASSE (Storno nach
Kassenübergabe), NEG-BANKVERBINDUNG (ungültige IBAN-Prüfziffer / BIC),
NEG-BESCHEID-ZEITRAUM (umgekehrter und 13-Monats-Zeitraum, § 44 Abs. 3),
NEG-NICHTGEFUNDEN (Operationen auf IDs, die nicht existieren). Der erwartete
Fachfehler ist jeweils Bestandteil des Szenarios; geprüft wird zusätzlich, dass
die Datenbank **unverändert** bleibt (keine Zahlung, kein Bescheid, Budget unangetastet).

**Negativfälle** (Weboberfläche, 7 Arten): WEB-NEG-LOGIN (falsches Passwort),
WEB-NEG-BANKVERBINDUNG, WEB-NEG-OHNE-MONAT (Anordnung ohne Zahlungsmonat),
WEB-NEG-KEINANSPRUCH, WEB-NEG-BUDGET, WEB-NEG-DOPPELMONAT und
WEB-NEG-STORNO-SB (Beweis, dass die Oberfläche den Storno-Knopf einer
freigegebenen Zahlung der Sachbearbeiter-Rolle vorenthält). Bewiesen wird
jeweils zweifach: Fehlerdialog mit erwartetem Text **und** unveränderter
Datenbank-Endzustand per SQL.

**Stresstest** (nur Fachschicht-Modus; GUI und API wehren Modus 2 ab): **zwei
Wellen über dieselben User**. Welle 1 (`STRESS-ANLEGEN`) legt je User Vorgang und
SozialFall an — noch ohne Zahlung. Welle 2 (`STRESS-BEARBEITEN`) findet den User über
das Aktenzeichen wieder, ändert die Miete (optimistisches Locking, Version 1 → 2) und
ordnet die Auszahlung an (Betrag nach NEUER Miete). Die zweite Welle startet erst,
wenn die erste vollständig abgeschlossen ist (Phasenbarriere im Simulator). Das
GUI-Feld „Aufträge" ist die **User-Zahl**; je User laufen 2 Tests — Standard-Preset:
20 Mitarbeiter / **2000 User = 4000 Transaktionsblöcke** (Referenzlauf: 4000/4000
verifiziert in 13 s; Datenbank-Endzustand per SQL bestätigt: 2000 Vorgänge, alle
Fälle exakt Version 2, 2000 Zahlungen ANGELEGT, Summe deckungsgleich mit dem
Gesamtreport, 8000 Journaleinträge = 4 je User).

**Lasttest**: schlanke Durchläufe (Vorgang + SozialFall + Anordnung, ohne
Freigabe/Storno) in hoher Stückzahl gegen Transaktionssteuerung und Datenbank.
Da bei 600+ Fällen eine Einzelfall-Dokumentation ausufern würde, schreibt der
Simulator bewusst nur einen **Gesamtreport** (`lastreport.pdf`/`lastreport.md`)
mit Aggregaten: Kennzahlen, Durchsatz (Aufträge/min), Dauer je Auftrag
(min/Ø/max), Summe der verifizierten Auszahlungen, Verteilung je Szenario-Typ
und je Sachbearbeiter. Die Einzelnachweise stehen vollständig in den
Protokolldateien des Lauf-Verzeichnisses. GUI-Presets: Lasttest klein
(10 MA/120), Standard **20 MA / 600 Fälle**, Dauerlast (30 MA/1200);
Referenzlauf: 600/600 verifiziert in 3 s gegen die Docker-Oracle.

## Logfiles (der eigentliche Zweck)

Je Lauf ein Verzeichnis `testsweet/logs/run-<Zeitstempel>/`:

| Datei | Inhalt |
|-------|--------|
| `simulation.log` | alle Ereignisse chronologisch (Millisekunden, Kanal, Mitarbeiter) |
| `mitarbeiter-<kürzel>.log` | alles eines simulierten Sachbearbeiters |
| `auftrag-<id>-<aktenzeichen>.log` | Schrittprotokoll eines Auftrags |
| `db-verifikation.log` | Erwartung vs. Ist je Prüfung (OK/ABW) |
| `fehler.log` | nur Fehler und Abweichungen |
| `zusammenfassung.txt` | Kenndaten des Laufs (Modus, Mitarbeiter, Verifikationsquote) |
| `screenshots/` | Modus 2: Screenshot je UI-Schritt (`tf-NN-schritt-MM.png`) |
| `lastreport.pdf` / `lastreport.md` | LAST-Kategorie: gekürzter Gesamtreport (Aggregate statt Einzelfälle) |
| `testdokumentation.pdf` | hochprofessionelle Testdokumentation (repräsentatives Deckblatt mit Titelbild, Fachflussdiagramme zu Architektur, SGB-XII-Rechenkaskade und Vier-Augen-Workflow, Inhaltsverzeichnis mit exakter Pagination, je Testfall nachrechenbarer Rechenweg + Erwartung/Ist-SQL-Prüfung + Ablauf); in der GUI per Button abrufbar (`/api/pdf`) |
| `testdokumentation.md` | maschinenlesbare Fassung derselben Daten inkl. der vergebenen DB-IDs und der Prüf-SQLs — zum unabhängigen Nachprüfen durch KI/Dritte (`/api/md`) |

## Grafik- und Diagramm-Generierung

Die Testdokumentation und Weboberfläche nutzen ein automatisiertes Grafiksystem:
- **Titelbild (`titelbild.jpg`)**: Repräsentatives Deckblatt-Titelbild in `testsweet/assets/` bzw. `testsweet/src/main/resources/sweet/assets/`.
- **Fach- und Ablaufdiagramme**: Vektorscharfe Ablaufdiagramme (200 dpi), die automatisiert über `python testsweet/generate_diagrams.py` generiert werden:
  1. `simulator_zyklus.png` / `.jpg`: Ablauf & Testzyklus des Simulators
  2. `bedarfsberechnung_sgb12.png` / `.jpg`: Gesetzliche Berechnungskaskade (SGB XII, Stand 2026)
  3. `vier_augen_workflow.png` / `.jpg`: Auszahlungs-Workflow, Vier-Augen-Freigabe & Storno
  4. `transaktionsarchitektur.png` / `.jpg`: Transaktionssteuerung & Oracle-ACID-Kern
  5. `stresstest_zwei_wellen.png` / `.jpg`: Stresstest-Szenario (Welle 1 → Phasenbarriere → Welle 2, Oracle-Kern, SQL-Endprüfung) — eingebettet in den Stresstest-Gesamtreport
- **Eingebauter PDF-Generator (`SweetPdf`)**: Vollständige Eigenentwicklung ohne Drittbibliotheken; bindet Grafiken als native PDF-1.4 XObjects (`/Filter /DCTDecode`) ein und optimiert die Seitenumbruchslogik, sodass Testfälle exakt auf Einzelseiten formatiert werden.
- **Weboberfläche (Port 8095)**: Modernes Dashboard mit interaktiver Diagramm-Showcase-Galerie, Live-Statusindikator, Schnell-Presets (15 / 60 / 120 Fälle) und Volltext-Such- und Filterleiste.

Die Auswertung erfolgt bewusst **manuell und gemeinsam** — der Simulator
liefert nur das Rohmaterial.

## Datenhygiene

Alle Vorgänge tragen das Aktenzeichen-Präfix `SWEET-`; der optionale Aufräum-
Schritt vor dem Lauf (GUI-Checkbox, Standard an) entfernt Reste früherer Läufe
komplett (Kindtabellen zuerst). Der Seed-Bestand der Fachdatenbank bleibt
unberührt.

## Sicherheitshinweis

Die Oberfläche lauscht nur auf 127.0.0.1, hat keine Anmeldung und erlaubt
Löschungen (Aufräumen) — ausschließlich als lokales Entwicklungswerkzeug
betreiben.
