# testsweet — Mitarbeiter-Simulator für SDN.sozial LMG

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

## Start

Voraussetzung (einmalig und nach Änderungen am Hauptprojekt):

```bash
mvn install          # im Hauptprojekt (installiert transaktionssteuerung-0.5.4 ins lokale Repo)
```

Dann im Projektordner:

```bash
mvn -f testsweet/pom.xml compile exec:java
```

oder unter Windows einfach `testsweet\start.bat`. Die Oberfläche öffnet sich
unter <http://127.0.0.1:8095/> (Port über `SWEET_PORT`).

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
| `testdokumentation.pdf` | ausführliche Testdokumentation (Deckblatt, Einleitung mit Rechengrundlagen, Inhaltsverzeichnis, je Testfall einfache Beschreibung + nachrechenbarer Rechenweg + Erwartung/Ist + Protokoll); in der GUI per Button abrufbar (`/api/pdf`) |
| `testdokumentation.md` | maschinenlesbare Fassung derselben Daten inkl. der vergebenen DB-IDs und der Prüf-SQLs — zum unabhängigen Nachprüfen durch KI/Dritte (`/api/md`) |

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
