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
2. **Weboberfläche / Selenium** (Schritt 2, noch nicht implementiert): Browser
   werden per Selenium bedient. In der GUI anwählbar, startet noch nicht.

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

### Option B: Headless Batch-Lauf (für CI/CD und automatisierte Abnahme)
Ausführung direkt über CLI ohne Browser:
```cmd
testsweet\start.bat run <mitarbeiter> <auftraege> [seed] [denkzeitMs]
```
Beispiel (10 parallele Mitarbeiter, 60 Aufträge, Seed 42, 0 ms Denkzeit):
```cmd
testsweet\start.bat run 10 60 42 0
```
*Ablauf: Bereinigt Altdaten, startet die Worker, führt die Transaktionen aus, verifiziert 100 % per SQL gegen Oracle und schreibt die vollständigen Logfiles inklusive druckfertigem PDF und maschinenlesbarem Markdown nach `testsweet/logs/run-.../`.*

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

## Logfiles (der eigentliche Zweck)

Je Lauf ein Verzeichnis `testsweet/logs/run-<Zeitstempel>/`:

| Datei | Inhalt |
|-------|--------|
| `simulation.log` | alle Ereignisse chronologisch (Millisekunden, Kanal, Mitarbeiter) |
| `mitarbeiter-<kürzel>.log` | alles eines simulierten Sachbearbeiters |
| `auftrag-<id>-<aktenzeichen>.log` | Schrittprotokoll eines Auftrags |
| `db-verifikation.log` | Erwartung vs. Ist je Prüfung (OK/ABW) |
| `fehler.log` | nur Fehler und Abweichungen |
| `zusammenfassung.txt` | Kenndaten des Laufs |
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
