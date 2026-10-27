# testsweet — Mitarbeiter-Simulator für SDN.Leistung LMG

Eigenständiger Szenariosimulator mit eigenem Maven-Projekt und eigenen Tests
(nicht Teil von `mvn test` im Hauptprojekt): N simulierte Sachbearbeiter arbeiten parallel einen Pool von
Anwenderszenarien ab; danach wird geprüft, ob das Erwartete in der Oracle-
Datenbank angekommen ist. SQL-Prüfung, deterministische Nachrechnung und echte
LLM-Nachprüfung liefern getrennte Nachweise; die Protokolle bleiben zur fachlichen
Auswertung verfügbar.

## Modi

1. **Fachschicht / Transaktionstest** (implementiert): Die simulierten
   Mitarbeiter rufen direkt `SozialLeistungsService` → `TxManager` → Oracle auf.
   Prüft persistierte Beträge, Budget-Führung, Vier-Augen-Logik und ausgewählte
   Konkurrenz-/Idempotenzfälle. Last- und Stressläufe erzwingen keinen Deadlock.
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

### Alle Zertifikatstests mit einem Klick

In Abschnitt 7 auf **„Mache alle Zertifikatstests“** klicken. Derselbe Knopf steht
auch im Dialog für fehlende oder veraltete Prüfbelege. Der Ablauf erledigt:

1. Hauptprogramm neu bauen, installieren und mit der eingerichteten Testdatenbank
   testen; die Konfigurationsprüfung zusätzlich ohne DB-Umgebungsüberschreibungen.
2. testsweet neu bauen und seine automatischen Tests mit der Testdatenbank ausführen.
3. Die sieben Pflichtkombinationen prüfen: Web Positiv 100, Negativ 100, Last 30;
   Fachschicht Positiv 500, Negativ 500, Last 120, Stress 21 Nutzer in zwei Wellen
   (42 Aufträge). Insgesamt sind das 1.392 Simulatoraufträge.
4. GLM mit der im Feld „Maximale Prüffälle“ gewählten Fallzahl auf den frischen
   Positiv-/Negativberichten starten. Dies bleibt eine ausgewiesene Stichprobe.
5. Bei vollständigem Erfolg das Zertifikat automatisch erstellen und dessen PDF
   zum Öffnen anbieten.

Benötigt werden Maven/Java, die erreichbare Testdatenbank, Chrome für Selenium
und der über das Zahnrad gespeicherte GLM-Zugang. Der Ablauf verwendet einen
frisch gebauten separaten Prüfprozess und eigene lokale Webserver-Ports;
eine parallel laufende Hauptanwendung auf Port 8080 muss dafür nicht beendet
werden. Ein ausdrücklich konfiguriertes externes Webziel wird abgewiesen, weil
dessen Programmstand nicht durch den lokalen Neubuild belegt wird.

Die Fortschrittsanzeige zeigt 13 Phasen, tatsächliche Fälle innerhalb der
Simulator-/GLM-Phase und die bisherige Laufzeit. Bei Maven ist der Teilfortschritt
unbestimmt. Prozentwerte beschreiben erledigte Phasen, keine garantierte Restzeit.
Die Browserseite darf geschlossen oder neu geladen werden; der testsweet-Server
muss weiterlaufen. **„Ablauf abbrechen“** beendet den eigenen Prüfprozess.

Während des Gesamtlaufs sind Einzelstarts, Konfigurationsänderungen und manueller
Export gesperrt. Fehler oder Quellenänderungen stoppen den Ablauf; GLM und Export
werden dann nicht einfach übersprungen. Ein neu gestarteter, fehlgeschlagener oder
abgebrochener Gesamtlauf sperrt ältere grüne Zertifikate. Der Dialog nennt den
betroffenen Schritt und den Protokollpfad unter
`testsweet/logs/certificate-suite-<ID>/`. Vor einem erneuten Start die Ursache
beheben; alte Belege werden nicht nachträglich gültig.

### Einzelne Läufe manuell starten

Voraussetzung (einmalig und nach Änderungen am Hauptprojekt):

```bash
mvn install          # im Hauptprojekt (installiert transaktionssteuerung-0.5.8 ins lokale Repo)
```

### Option A: Interaktive Weboberfläche (Standard)
Unter Windows einfach starten:
```cmd
testsweet\start.bat
```
oder plattformunabhängig per Maven:
```bash
mvn -f testsweet/pom.xml process-classes exec:java
```

`process-classes` ist beim Start erforderlich: Erst diese Maven-Phase erstellt nach
Kompilierung und Ressourcen-Kopie den Buildstempel. `compile exec:java` allein kann
nach Änderungen einen veralteten oder fehlenden Nachweis hinterlassen. Änderungen
am Hauptprodukt müssen weiterhin zuerst im Hauptordner mit `mvn clean install`
gebaut und installiert werden; anschließend testsweet neu starten.

Fehlerdialoge unterscheiden Testabweichung, technischen Nachweisfehler, KI-Fehler
nach drei Sendeversuchen und Abbruch. Sie nennen Bedeutung, nächste Schritte und
Protokollpfad. Identische Diagnosezeilen werden nur einmal angezeigt. Ein
Build-/Quellstandfehler beweist allein keinen Rechenfehler oder GLM-Ausfall; er
sperrt den Erfolgsnachweis. Neubuild und Neustart machen alte Testbelege nicht
nachträglich gültig. Für ein vollständiges Zertifikat sind aktuelle Nachweise
beider automatischer Testsuiten, der sieben festgelegten Simulator-Kombinationen
und der POSITIV-/NEGATIV-GLM-Stichprobe nötig. ECHT und RECHNER gehören nicht
zu diesem automatischen Gesamtumfang; ihre eigenständigen Fachläufe sind separat nachzuweisen.
Die Oberfläche öffnet sich automatisch unter <http://127.0.0.1:8095/> (Port über `SWEET_PORT`).
Dort kann zwischen **Modus 1 (Fachschicht)** und **Modus 2 (Weboberfläche/Selenium)** gewählt
werden — und darüber hinaus über die Reiterleiste **Testfallkategorie** (zwischen Moduswahl und
Laufkonfiguration) zwischen **Positivfällen**, **Negativfällen**, **ECHT**, **RECHNER**, **Lasttest**
und **Stresstest** (siehe unten).

### Option B: Headless Batch-Lauf (Fachschicht, für CI/CD und automatisierte Abnahme)
Ausführung direkt über CLI ohne Browser:
```cmd
testsweet\start.bat run <mitarbeiter> <auftraege> [seed] [denkzeitMs] [kategorie]
```
Beispiel (10 parallele Mitarbeiter, 100 Aufträge, Seed 42, 0 ms Denkzeit, Positivfälle):
```cmd
testsweet\start.bat run 10 100 42 0
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
*Ablauf: Startet bei Bedarf den eingebetteten SDN.Leistung-Server auf dem konfigurierten `LMG_PORT` (Standard 8080), ordnet die Chrome-Fenster nebeneinander an, spielt die Vorgänge mit Screenshots durch, prüft die Aufträge per SQL gegen Oracle und schreibt PDF (`/api/pdf`) sowie Markdown (`/api/md`). Ein eigener Freigabebenutzer steht auch bei nur einem Browserarbeiter zur Verfügung.*

## Konfiguration

- GUI: Mitarbeiterzahl, Anzahl Aufträge, Seed (0 = zufällig; fester Seed =
  reproduzierbare Szenarien), Denkzeit je Schritt, Bereinigung eigener früherer Läufe.
- Datenbank: `TX_URL` / `TX_USER` / `TX_PASS` (Standard: Docker-DB
  `jdbc:oracle:thin:@//localhost:1521/FREEPDB1`, tx_demo/tx_demo); Systemproperties
  `tx.url` / `tx.user` / `tx.pass` haben Vorrang. Eingebetteter Webserver und
  SQL-Verifikation verwenden dieselbe Simulator-Datenbank.
- Port: `SWEET_PORT` (Standard 8095).
- Externer Webserver: `sweet.baseurl` beziehungsweise `SWEET_BASE_URL`. Vor Cleanup
  und Browserstart werden Datenbank, Container, Schema und Service über den
  adminpflichtigen Endpunkt `/api/system/db-identity` abgeglichen. Fehlender Endpunkt,
  fehlgeschlagene Anmeldung oder abweichende Identität verhindern den Lauf. Dieser
  Pfad verwendet die Standardkonten des externen SDN.Leistung-Servers.
- LLM: Zahnrad oben rechts; Konfiguration und Nachweismodell siehe Abschnitt 7 unten.

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

**Kombinationsgenerator (KOMBINATION / NEG-KOMBINATION):** Über die kuratierten
Arten hinaus füllt der Simulator den Auftragstopf mit systematisch gestreuten
Kombinationsfällen — bei POSITIV zufällige Haushalte (Single, Paar, Alleinerziehende
mit Kindern, Paar mit Kindern) mit je Person gezogener Regelbedarfsstufe, Merkzeichen G,
Schwangerschaft (mit/ohne Entbindungsmonat — der Mehrbedarf entfällt dann im
Zahlungsmonat automatisch), dezentralem Warmwasser, Wohnkosten (am ersten Fall oder
aufgeteilt), Renten-/Erwerbseinkommen mit Absetzbeträgen, Vermögen im und über dem
Schonvermögen sowie zufällig gewählten Wegen (Bescheid mit Zeitraum, Vier-Augen-
Freigabe, Doppelklick-Wiederholung, Storno mit Mietekorrektur und Neu-Anordnung).
Bei NEGATIV werden Verstöße aus vier Gruppen (Anlage/SEPA, Anordnung inkl. Zahlungsmonat
außerhalb des Bescheids, Workflow/Vier-Augen, Daten wie Konkurrenz und unbekannte IDs)
zu verträglichen Primär-/Sekundär-Kombinationen gesetzt. Eine Signatur-Menge stellt
sicher, dass **keine Konstellation zweimal vorkommt**; die Erwartungshaltung entsteht
weiterhin mit derselben reinen Berechnung wie die Fachschicht (inkl. Entbindungsmonat-
Regel und Vermögensverbrauch). Historische Referenzläufe vor der Nachbesserung
(2026-10-13, Docker-Oracle):
500 Positiv (480 Kombinationen) und 500 Negativ (491 Kombinationen) — jeweils
500/500 verifiziert in 4 bzw. 3 Sekunden; GUI-Preset „Breite 500“ setzt beide ein.

## Testfallkategorien

Zwischen Moduswahl und Laufkonfiguration wählt die GUI die **Testfallkategorie**;
das Aktenzeichen-Präfix unterscheidet die Datenmengen in der Datenbank:

| Kategorie | Fachschicht (Modus 1) | Weboberfläche (Modus 2) | Report |
|-----------|----------------------|-------------------------|--------|
| **POSITIV** (Standard) | `SWEET-<Laufkennung>-` — die 10 Szenario-Arten oben | `SWEET-<Laufkennung>-W-` | `testdokumentation.pdf` / `.md` je Fall |
| **NEGATIV** | `SWEET-<Laufkennung>-N-` | `SWEET-<Laufkennung>-NW-` | `testdokumentation.pdf` / `.md` je Fall |
| **ECHT** | `SWEET-<Laufkennung>-E-` — 5 quellenbasierte Modellfälle | — (bewusst nur Fachschicht) | `testdokumentation.pdf` / `.md` je Fall |
| **RECHNER** | `SWEET-<Laufkennung>-R-` — 12 feste Vergleichsfälle; externe Stichprobe 1/6/12 belegt | — (bewusst nur Fachschicht) | `testdokumentation.pdf` / `.md` je Fall |
| **LAST** | `SWEET-<Laufkennung>-L-` | `SWEET-<Laufkennung>-LW-` | **gekürzter** `lastreport.pdf` / `lastreport.md` (nur Aggregate) |
| **STRESS** | `SWEET-<Laufkennung>-S-` | — (bewusst nur Fachschicht) | **gekürzter** `lastreport.pdf` / `lastreport.md` |

**Auftrags-Obergrenzen je Kategorie** (Feld „Aufträge", 1–Limit; GUI begrenzt das
Eingabefeld, Simulator und CLI weisen Überschreitungen mit Klartext-Meldung ab):
**500** Positiv- und **500** Negativ-Kombinationen (unser vereinbarter Testfallbestand),
**5** ECHT-Modellfälle, **12** Rechner-Vergleichsfälle (fester Bestand),
**1200** Lastdurchläufe, **3000** Stress-User (je User zwei Wellen).

**Quellenbasierte Modellfälle** (Kategorie ECHT, Fachschicht, 5 Aufträge):
Gesetz, Richtlinie und veröffentlichte Fallberichte liefern einzelne Werte oder Regeln;
die vollständigen Eingaben werden ausdrücklich ergänzt. Das sind keine vollständig
verifizierten öffentlichen Einzelbescheide. Die Zahlbeträge 1.542/1.023/1.153 EUR,
Vermögenseinsatz und Endvermögen der Erfolgsfälle stehen als feste SQL-Erwartungen
im Code. Die dokumentierten Rechenblöcke verwenden die Produktklasse; eine getrennte
arithmetische Referenz prüft diese zusätzlich, ersetzt aber keine rechtliche Einzelfallprüfung.

Die beiden großen Vermögensvarianten verwenden historische Summen von 44.500 bzw.
31.000 EUR. Individuelle Vermögensprivilegierungen und der historische Übergangstatbestand
sind nicht modelliert; insbesondere bildet die Kfz-Variante den heutigen Schutz nach
§ 90 Abs. 2 Nr. 10 SGB XII nicht ab. 34.500/11.000 EUR sind deshalb Modellwerte,
keine bestätigten heutigen Einsatzbeträge. Sie prüfen technische KEIN_ANSPRUCH-Abwehr
mit unverändertem Fachbestand. ECHT 5 prüft einen gewählten **Gesamtdeckel von 590 EUR**;
die kommunalen 90/110 EUR sind Nichtprüfungsgrenzen mit Einzelfallprüfung. Der
Gesamtdeckel ist keine vollständige oder gleichwertige Umsetzung der Richtlinie.
Eindeutige Quellenlinks und Ergänzungen: [Belegverzeichnis](../doc/belege/echt-und-rechner-faelle-2026-10-27.md).

**Rechner-Vergleichsfälle** (RECHNER, Fachschicht, 12 Aufträge): feste,
unabhängig handrechenbare Zahlbeträge **227,00 bis 1.427,00 EUR** für Single/Paar,
Rente, Erwerbseinkommen und Merkzeichen G. Die ursprüngliche Behauptung einer
vollständigen externen Zwölf-Fälle-Erhebung ist nicht vollständig belegt. Der alte
als Ergebnisbeleg bezeichnete Screenshot zeigt kein Rechenergebnis. Eigene Browserbelege
vom 27.10.2026 bestätigen die Fälle **1, 6 und 12** im
[externen SGB-XII-Rechner](https://www.steuerschroeder.de/Steuerrechner/Grundsicherung.html)
(Rechtsstand der Seite 03.07.2026); Eingaben und Ergebnisse sind im Belegverzeichnis verlinkt.
Die übrigen neun sind handgerechnet, nicht durch diese neue Browserstichprobe bestätigt.

Betriebskosten sind 0,00 EUR, Warmwasser zentral, Vermögen 0; kein § 82a-Freibetrag,
keine zusätzliche Altersvorsorge nach § 82 Abs. 4 und keine Witwenrenten-Sonderregeln.
Damit ist nur die dokumentierte Schnittmenge vergleichbar. `EchtRechnerFallTest`
prüft sowohl Produkt-Rechenblöcke als auch die tatsächlich verwendeten SQL-Sollwerte
gegen separate Konstanten. Er erkennt Ergebnisabweichungen dieser Fälle, nicht jede
beliebige Änderung der Berechnung. Auch Einsatz/Endvermögen und Monatsparameter sind
zusätzlich fest geprüft. Die arithmetische Übereinstimmung ist kein Bescheid-Ersatz.

**Automatischer Nachweisumfang:** ECHT und RECHNER sind eigenständige Fachläufe und
bewusst **nicht Teil der Zertifikatssuite oder GLM-Auswahl**. Die bestehende Suite
umfasst sieben festgelegte Kombinationen und 1.392 Aufträge; GLM wählt POSITIV/NEGATIV.
Ein grüner Gesamtlauf ist daher kein automatischer SQL-/GLM-Nachweis der 17 neuen Fälle.

**Negativfälle** (Fachschicht, 9 Arten): NEG-KEINANSPRUCH (Rente über Bedarf,
§ 43 Abs. 1), NEG-BUDGET (Startbudget unter Zahlbetrag), NEG-DOPPELMONAT
(Doppelanordnung je Monat), NEG-SELBSTFREIGABE (Vier-Augen-Verletzung),
NEG-DOPPELFREIGABE (Freigabe einer BEREIT-Zahlung), NEG-STORNO-KASSE (Storno nach
Kassenübergabe), NEG-BANKVERBINDUNG (ungültige IBAN-Prüfziffer / BIC),
NEG-BESCHEID-ZEITRAUM (umgekehrter und 13-Monats-Zeitraum, § 44 Abs. 3),
NEG-NICHTGEFUNDEN (Operationen auf IDs, die nicht existieren). Der erwartete
Fachfehler ist jeweils Bestandteil des Szenarios. Der Fehlversuch muss den zuvor
vorbereiteten Zustand unverändert lassen: etwa keine zusätzliche Zahlung oder
Budgetminderung. Rechtmäßige Vorbereitungen, beispielsweise eine erste Zahlung
vor einer Doppelanordnung, dürfen bestehen bleiben.

**Negativfälle** (Weboberfläche, 7 Arten): WEB-NEG-LOGIN (falsches Passwort),
WEB-NEG-BANKVERBINDUNG, WEB-NEG-OHNE-MONAT (Anordnung ohne Zahlungsmonat),
WEB-NEG-KEINANSPRUCH, WEB-NEG-BUDGET, WEB-NEG-DOPPELMONAT und
WEB-NEG-STORNO-SB (Beweis, dass die Oberfläche den Storno-Knopf einer
freigegebenen Zahlung der Sachbearbeiter-Rolle vorenthält). Bewiesen wird
jeweils über die erwartete UI-Abwehr und den Datenbank-Endzustand per SQL;
beim Storno-Rollentest ist die Abwehr der fehlende Storno-Knopf.

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
(min/Ø/max sowie p50/p95/p99 nach nearest-rank), Summe der verifizierten Auszahlungen, Verteilung je Szenario-Typ
und je Sachbearbeiter. Die Einzelnachweise stehen vollständig in den
Protokolldateien des Lauf-Verzeichnisses. GUI-Presets: Lasttest klein
(10 MA/120), Standard **20 MA / 600 Fälle**, Lasttest groß (30 MA/1200);
Referenzlauf: 600/600 verifiziert in 3 s gegen die Docker-Oracle.

LAST und STRESS sind begrenzte **funktionale Paralleltests**. Die Auftragstöpfe
definieren weder eine Mindestlastdauer noch Sättigungsstufen; auch ein grüner
Lauf ist deshalb kein Kapazitäts-, Dauerlast- oder Produktionslastnachweis.
Die historischen Sekundenangaben oben sind keine Zusage für aktuelle Quellen
oder andere Umgebungen. Der Durchsatz verwendet die monotone Laufdauer in
Millisekunden, zählt abgeschlossene Aufträge und umfasst Vorbereitung sowie
SQL-Endverifikation. Auftragsperzentile verwenden die Worker-Bearbeitungszeit
ohne Wartezeit im Auftragstopf; n wird mit ausgewiesen. Für einen eigenständigen
Kapazitätsnachweis sind ein vereinbartes Lastprofil, Messdauer, mehrere
Parallelitätsstufen und Ressourcen-/Fehlergrenzen erforderlich.

## Datenbesitz und Aufräumen

Jeder Simulatorlauf erzeugt eine zufällige 128-Bit-Laufkennung. Seine Aktenzeichen
beginnen mit `SWEET-<Laufkennung>-`; Modus, Kategorie und Auftragsnummer folgen.
Seed und fachliche Eingaben bleiben reproduzierbar, die Aktenzeichen werden je
Lauf neu vergeben. `run-state.json` und die Laufzusammenfassung nennen diesen
Datennamensraum. Globale SQL-Prüfungen beziehen sich ausschließlich auf den
aktuellen Lauf, sodass andere Läufe weder mitgezählt noch als eigene Fehler
interpretiert werden.

Die Option „Eigene frühere Läufe … aufräumen“ bereinigt nur Laufkennungen, die
**dieselbe laufende Simulatorinstanz selbst erzeugt** hat. Der Filter ist
parameterisiert und verwendet keine Wildcards. Beim ersten Lauf und nach einem
Neustart gibt es keine übernommene Bereinigungsberechtigung. Fremde/historische
`SWEET-*`-Daten und Journale ohne BG-Zuordnung bleiben erhalten; ein gemeinsames
Präfix oder fremde Logdateien beweisen keinen Besitz. Historische Testdaten können
separat nach Prüfung ihres Ursprungs gewartet werden. Es gibt keinen globalen
Löschlauf auf `SWEET-%` oder `sb-sim-%` mehr.

## Logfiles (der eigentliche Zweck)

Je Lauf ein atomar neu angelegtes Verzeichnis `testsweet/logs/run-<Zeitstempel>-<eindeutiger Suffix>/`:

| Datei | Inhalt |
|-------|--------|
| `simulation.log` | alle Ereignisse chronologisch (Millisekunden, Kanal, Mitarbeiter) |
| `mitarbeiter-<kürzel>.log` | alles eines simulierten Sachbearbeiters |
| `auftrag-<id>-<aktenzeichen>.log` | Schrittprotokoll eines Auftrags |
| `db-verifikation.log` | Erwartung vs. Ist je Prüfung (OK/ABW) |
| `fehler.log` | nur Fehler und Abweichungen |
| `run-state.json` | Start, Zwischenstände, terminales Urteil, geplante Fallzahl, aktuelle Quell-/Buildbindung und Reporthashes; ein jüngerer abgebrochener Lauf verdrängt ältere Erfolge. |
| `zusammenfassung.txt` | Kenndaten des Laufs (Modus, Mitarbeiter, Verifikationsquote) |
| `screenshots/` | Modus 2: Screenshot je UI-Schritt (`tf-NN-schritt-MM.png`) |
| `lastreport.pdf` / `lastreport.md` | LAST-Kategorie: gekürzter Gesamtreport (Aggregate statt Einzelfälle) |
| `testdokumentation.pdf` | Testdokumentation mit Deckblatt, Fachflussdiagrammen, Inhaltsverzeichnis, Rechenwegen, SQL-Soll/Ist und Ablauf; KI-Anhang zunächst ausstehend (`/api/pdf`) |
| `testdokumentation.md` | maschinenlesbare Fassung derselben Daten inkl. der vergebenen DB-IDs und der Prüf-SQLs — zum unabhängigen Nachprüfen durch KI/Dritte (`/api/md`). Seit 2026-10-13 trägt die Datei ihren Prüfkontext in sich: Block „pruefanleitung_fur_ki" (Nachrechen-Anweisung), je Rechenweg `zahlungsmonat` und je Person `entbindungsmonat` (Regel: Mehrbedarf Schwangerschaft entfällt, wenn Zahlungsmonat nach Entbindungsmonat liegt), je Testfall `erwartete_fachfehler` (aggregierte Codes der abgewehrten Schritte) sowie für Positiv-/Negativläufe mit Storno-Korrektur einen zweiten Rechenweg für die Neuanordnung. Die Datei kann einer KI als Ganzes zur Überprüfung übergeben werden (nur Positiv/Negativ — Last/Stress liefern bewusst nur Aggregate). |
| `testdokumentation-geprueft.pdf` / `.md` | Neue Fassung des gebundenen Reports mit KI-Prüfnachweis; die Originaldateien bleiben unverändert. Hypothetisches Restbudget und expliziter Buchungsendzustand werden getrennt ausgewiesen. |


## Qualitätssicherung: LLM-Nachrechnung & Testzertifikat (GUI-Abschnitt 7)

Die Anwendung ruft das konfigurierte Modell tatsächlich über eine Chat-Completions-API
auf. Standard sind **GLM-5.3** (`glm-5.3`) und die Basisadresse
`https://api.z.ai/api/coding/paas/v4`; ein anderer kompatibler Endpunkt ist konfigurierbar.

1. **Quellläufe erstellen:** erfolgreiche vollständige POSITIV- und NEGATIV-Berichte
   müssen vorliegen. Die Auswahl wechselt zwischen beiden Kategorien in Berichtreihenfolge
   und berücksichtigt auch kuratierte Arten. LAST/STRESS enthalten nur Aggregate und
   gehören nicht zur LLM-Fallnachrechnung.
2. **Zahnrad konfigurieren:** Endpunkt, Modell, API-Schlüssel, Zeitlimit (5–900 s),
   Antwortlimit (1024–131072 Tokens), **1–10 Fälle je Anfrage** und **2–1000 Prüffälle**.
   Standard: 180 s, 16000 Tokens, 3 Fälle je Anfrage und **200 Prüffälle: genau
   100 Positiv- und 100 Negativfälle**. Simulator-Standard, CLI- und HTTP-Fallback
   verwenden jeweils **100 Fälle pro Kategorie**; die breite Fachprüfung bleibt
   bei 500 Positiv- und 500 Negativfällen. Ein leeres
   Schlüsselfeld behält den vorhandenen Schlüssel; die Checkbox entfernt den lokal
   gespeicherten Wert. „Verbindung zu GLM testen“ führt einen echten kleinen API-Aufruf aus,
   ersetzt aber keine Fachprüfung.
3. **Prüfung starten:** Abschnitt 7 zeigt die gewählte Fallzahl, den Prüfauftrag und
   „Prüfpaket ansehen“. „GLM-5.3-Prüfung starten“ sendet je Batch den vollständigen
   Prüfauftrag, die vollständigen Gesetzesgrundlagen und die vollständigen ausgewählten
   Fälle als drei **benannte Dokumenttexte im Request**. Es werden keine bloßen Dateipfade
   übermittelt. Reicht eine Kategoriequote nicht aus, wird abgebrochen; es gibt keine stille
   Kürzung oder einseitige Auffüllung. Gerade Fallzahlen werden hälftig aufgeteilt,
   ungerade erhalten einen zusätzlichen Positivfall. Die Auswahl ist eine dokumentierte
   Stichprobe, kein Nachweis aller Regelzweige.
   Alle API-Aufrufe verlangen JSON über `response_format: {"type":"json_object"}` und
   senden `thinking: {"type":"disabled"}`. Auch der Verbindungstest erwartet ein
   JSON-Objekt. Die API kann trotz `disabled` Reasoning-Tokens melden; diese Einstellung
   garantiert weder vollständig ausgeschaltetes Reasoning noch fehlerfreie Antworten.
   Die lokale Prüfung aller Fall-/Prüfpunkt-IDs und Rechenwerte bleibt verpflichtend.
   Ist eine Modellantwort formal ungültig (zum Beispiel falsche Fall-IDs, unvollständiges
   JSON, falsches Modell oder abgebrochene Ausgabe), wird dieselbe Fallgruppe mit allen
   drei Dokumenttexten nach **30 Sekunden** erneut gesendet. Pro Fallgruppe gibt es
   höchstens **drei Sendeversuche insgesamt**. Nur eine vollständig validierte Antwort
   erhöht den angezeigten Fall- und Fallgruppenfortschritt. Nach dem dritten Fehlversuch
   endet die gesamte Prüfung mit `ERROR`; ein fachliches `FAIL`-Urteil ist dagegen eine
   gültige Antwort und löst keine Wiederholung aus. HTTP-/Verbindungs- und
   Konfigurationsfehler werden nicht als formale Modellantworten wiederholt.
4. **Nachweise prüfen:** `testsweet/logs/llm-review-<UUID>/` enthält `manifest.json`,
   `source-POSITIV.md`, `source-NEGATIV.md`, Prüfauftrag, Gesetzesgrundlagen, Gesamtprüfmenge,
   `pruefmenge-batch-N.md`, die gesendeten `request-N-attempt-M.json`, empfangenen
   `response-N-attempt-M.json`, `result.json` und `protocol.md`. Fehlversuche stehen
   mit Ursache und Dateinamen in `protocol.md` und strukturiert unter `attemptChecks`
   in `result.json`; bei ausgeschöpften Versuchen steht dort `retryExhausted: true`.
   SHA-256 bindet Quellen, Dokumente, Anfragen und Antworten;
   Review-ID, Modell, Batch-/Fall-IDs und API-Request-IDs machen die Übergabe nachvollziehbar.
   Etwaige Schlüssel-Echos werden vor der Ablage bereinigt; Authentifizierungsheader werden
   nicht als Teil der Anfrageprotokolle gespeichert.
5. **Ergebnisse getrennt bewerten:** „Dateiübergabe geprüft“ bestätigt den belegten
   Transport und die validierten Antworten. Fachliches **PASS** verlangt zusätzlich eine
   vollständige Einzelantwort für jede ausgewählte Fall-ID, passende Modellkennung,
   vollständigen Antwortabschluss, vollständige strukturierte Personen-/Rechenblock-/V-/S-Abdeckung,
   unabhängige centgenaue Zahlenprüfung und keine Befunde oder ungeprüften Fälle. Freitext allein
   ergibt kein PASS. `FAIL`,
   `UNVERIFIED`, `ERROR` oder veränderte Quellen ergeben keine fachliche Freigabe.
   Eine erfolgreiche Dateiübergabe allein bestätigt keine Berechnung. Konkrete Befunde bleiben
   auch nach einem technischen Folgefehler erhalten und lösen einen roten Dialog aus.
6. **Report und Zertifikat öffnen:** Der KI-Nachweis erscheint im separaten Reportanhang
   beziehungsweise in einer neuen `testdokumentation-geprueft.pdf`/`.md`. Das ursprüngliche
   Quell-MD bleibt wegen seiner Hashbindung unverändert. Nach einem Serverneustart ist der
   gesicherte Nachweis auch als eigenständiger Anhang verfügbar (`/api/llm/report/pdf`).
   „Testzertifikat erstellen“ wertet die aktuellen Laufresultate und strukturierten echten
   API-Nachweise aus; fehlende, veraltete oder fehlgeschlagene Prüfungen erhalten kein
   grünes Gesamturteil. Auch negative und unvollständige Zertifikate lassen sich erzeugen;
   konkrete Fälle, Rechnungen und Befunde werden im Anhang ausgewiesen. GUI und Batch verwenden
   denselben Exportweg mit Staging, geprüftem Prozessabschluss und abschließendem READY-Marker.
   Ein veraltetes oder verändertes Zertifikat wird über die API nicht ausgeliefert. Details: [Werkzeug-README](../doc/testzertifikat-werkzeug/README.md).

Die lokale Datei `testsweet/llm.local.properties` und ihre temporären Schreibdateien sind
Git-ignoriert. Alternativ liefern `SWEET_LLM_API_KEY` oder `GLM_API_KEY` den Schlüssel
(in dieser Reihenfolge vor dem gespeicherten Wert). Die Konfigurations-API gibt nur zurück,
ob ein Schlüssel vorhanden ist. Die lokale Datei ist keine verschlüsselte Schlüsselablage.

**Historischer Verifikationsstand vor dem erneuten Gesamt-Review:** 55 testsweet-Tests grün. Zusätzlich wurde GLM-5.3 real mit
**6 ausgewählten Positiv-/Negativfällen in 2 Batches** geprüft: `PASS`, Dateiübergabe bestätigt;
lokale Review-ID `9f7fb043-be1b-4c33-aae4-4c417fd50da4`. Der vollständige HTTP-Lauf
lieferte außerdem den Report `run-20261013-164334-16012917256956894678/testdokumentation-geprueft.pdf`
mit grünem Hash-Anhang. Das belegt genau diese Stichprobe
und den damaligen Quellenstand. Die historische Nachrechnung **100 Fälle / 108 Rechenblöcke**
war eine deterministische Python-Zweitimplementierung und **kein GLM-API-Nachweis**.
Historische Freitextprotokolle mit einer Modellbezeichnung werden nicht als solche anerkannt.

### Automatische Rechenprüfung, Buildbindung und Zertifikatsumfang

Jeder erzeugte Rechenblock wird im Verifikator zusätzlich mit einer eigenständigen
BigDecimal-Referenz für den dokumentierten Rechenstand 2026 nachgerechnet. Diese Referenz
verwendet die Produktionsberechnung nicht. Fehlende Eingaben oder abweichende Ergebnisse
machen den Auftrag fehlerhaft. Die unabhängigen Goldwerttests bleiben zusätzlich erhalten.
Das prüft die implementierten Regeln; deren fachliche Vollständigkeit ist damit nicht bewiesen.

Positivfälle **TF11–TF19** sind feste, seedunabhängige Grenzfälle: Entbindungsmonat und
Folgezahlung, Vermögensverbrauch über zwei Monate, vier/fünf/sechs minderjährige Kinder,
Mehrbedarfskappung nach Abs. 6, zentraler Heizkostenabzug, gemeinsame KdU-Grenze und ein
Haushalt ohne Erwachsene. Bei 500 Positivfällen verbleiben 471 unterschiedliche
Kombinationsfälle. Die KdU-Fälle verwenden jeweils eine eigene unveränderliche
Servicekonfiguration am selben Transaktionsmanager; parallele Standardfälle behalten
ihre Parameter. Jeder Rechenblock nennt Abzugsanteil, BG-Grenze und tatsächlich
anerkannten Betrag. Fehlende Parameter historischer Blöcke bedeuten ausschließlich
den damaligen Standard AUS; ein teilweise dokumentierter Parametersatz ist ungültig.
Die Web-Szenarien decken diese beiden aktiven lokalen KdU-Konfigurationen nicht ab.
Synthetische Merkmalskombinationen beweisen technische Rechenzweige, keine juristische
Zulässigkeit sämtlicher Merkmale oder vollständige Rechtsprüfung.

`stop.bat` verwendet `SWEET_PORT` (Standard 8095), prüft Java-/SweetMain- bzw.
Maven-Projektidentität und beendet nur die verifizierte Prozesskette dieses Projekts.
Ein fremder Portinhaber wird nicht beendet; der Aufruf benötigt keine Tastatureingabe.
`powershell -NoProfile -ExecutionPolicy Bypass -File testsweet/stop.test.ps1` prüft die
Eigentumsentscheidung mit synthetischen Prozessdaten und beendet keine Prozesse.

Die GLM-Auswahl priorisiert vorhandene feste Fach-Grenzfälle, anschließend weitere
Fälle in Berichtreihenfolge, weiterhin abwechselnd positiv/negativ. **18 ausgewählte Fälle**
aus aktuellen Fachquellen mit mindestens 19 Positivfällen umfassen alle neun festen
Grenzfälle und neun Negativfälle. Kleinere Stichproben decken nur einen Teil davon ab.
Die Auswahl und sämtliche Fall-IDs stehen im Manifest. Laufzeitstempel werden als
eindeutige UTC-Instants gespeichert; lokale Altzeitstempel sind unbestätigt. Bei einer
mehrdeutigen Reihenfolge darf ein alter PASS einen möglicherweise neueren FAIL nicht
verdrängen. Ein eindeutig neuer vollständiger Lauf kann den historischen Konflikt lösen.

Maven schreibt Start- und Abschlussbelege der JUnit-Läufe einschließlich tatsächlicher
XML-Ergebnisse, Quellinventar und Buildhashes. Hauptprojekt: `mvn install` mit Tests;
anschließend `mvn -f testsweet/pom.xml test`. Ein übersprungener Test ist kein Erfolg.
DB-Tests benötigen `TX_URL`, `TX_USER`, `TX_PASS`; der Konfigurationsdatei-Test braucht einen
separaten Lauf ohne diese Umgebungsüberschreibungen. `mvn compile` oder `-DskipTests` erzeugt
keinen vollständigen Testnachweis. Nach einem Neubuild muss der testsweet-Prozess neu starten:
der Start-Snapshot verhindert, dass geladene alte Klassen als neuer Build ausgegeben werden.

Das Zertifikat verlangt aktuelle Belege beider JUnit-Module, die sieben Kombinationen
Fachschicht POSITIV/NEGATIV/LAST/STRESS sowie WEB POSITIV/NEGATIV/LAST und die ausgewiesene
POSITIV-/NEGATIV-GLM-Stichprobe. ECHT/RECHNER werden dabei bewusst nicht ausgewählt;
ihre SQL-/Fachnachweise bleiben getrennte eigenständige Läufe. WEB/STRESS wird nicht angeboten. Jedes bekannte FAIL ergibt NICHT BESTANDEN;
fehlende oder unvollständige Nachweise ergeben UNVOLLSTÄNDIG. Manueller Text ersetzt keine
grüne Prüfschicht. Die SHA-256-Kette belegt lokale Konsistenz von Dokumenten und
Anfrage-/Antwortprotokollen; sie beweist keine interne Verarbeitung beim Anbieter und
ist keine digitale Signatur gegen einen Angreifer, der sämtliche lokalen Belege ersetzen kann.

GUI, Deckblatt und maschinenlesbarer Export nennen diesen Umfang ausdrücklich als
**festgelegte Prüfmatrix ohne ECHT/RECHNER**. Ein BESTANDEN ist kein Nachweis für sämtliche
verfügbaren Kategorien und kein vollständiges Fachrechtsgutachten.

Im Fachschichtmodus sind Worker und Freigabeidentitäten getrennte Größen: Auch bei einem
Worker werden mindestens zwei ausdrücklich vorbereitete simulierte Kennungen verwendet.
Nur registrierte, voneinander verschiedene Kennungen dürfen anlegen und freigeben. Dieser
Modus prüft die fachliche Identitätstrennung, keine Anmeldung echter Personen. Der Webmodus
verwendet getrennte angemeldete Testkonten. Der jeweilige Vier-Augen-Prüfumfang steht im
Laufstatus sowie in PDF-/Markdown-Berichten; zusätzliche Kennungen erhöhen nicht die Workerzahl.

## Grafik- und Diagramm-Generierung

Die Testdokumentation und Weboberfläche nutzen ein automatisiertes Grafiksystem:
- **Titelbild (`titelbild.jpg`)**: Repräsentatives Deckblatt-Titelbild in `testsweet/assets/` bzw. `testsweet/src/main/resources/sweet/assets/`.
- **Fach- und Ablaufdiagramme**: Ablaufdiagramme (200 dpi), die automatisiert über `python testsweet/generate_diagrams.py` generiert werden:
  1. `simulator_zyklus.png` / `.jpg`: Ablauf & Testzyklus des Simulators
  2. `bedarfsberechnung_sgb12.png` / `.jpg`: Gesetzliche Berechnungskaskade (SGB XII, Stand 2026)
  3. `vier_augen_workflow.png` / `.jpg`: Auszahlungs-Workflow, Vier-Augen-Freigabe & Storno
  4. `transaktionsarchitektur.png` / `.jpg`: Transaktionssteuerung & Oracle-ACID-Kern
  5. `stresstest_zwei_wellen.png` / `.jpg`: Stresstest-Szenario (Welle 1 → Phasenbarriere → Welle 2, Oracle-Kern, SQL-Endprüfung) — eingebettet in den Stresstest-Gesamtreport
- **Eingebauter PDF-Generator (`SweetPdf`)**: Eigenentwicklung ohne Drittbibliotheken; bindet Grafiken als PDF-1.4 XObjects (`/Filter /DCTDecode`) ein und berechnet automatische Seitenumbrüche.
- **Weboberfläche (Port 8095)**: Modernes Dashboard mit interaktiver Diagramm-Showcase-Galerie, Live-Statusindikator, Positiv-/Negativ-Presets (15 / 100 / 500 Fälle) und Volltext-Such- und Filterleiste.

Automatische SQL-, Rechen- und LLM-Ergebnisse stehen zusammen mit den Rohprotokollen
zur fachlichen Auswertung zur Verfügung; ihre unterschiedliche Abdeckung bleibt erkennbar.

## Datenhygiene

Der optionale Aufräumschritt entfernt ausschließlich frühere, von derselben
Simulatorinstanz erzeugte Laufdaten (Kindtabellen zuerst). Fremde und historische
Laufdaten werden erhalten. Die genauen Besitz- und Neustartregeln stehen oben
unter „Datenbesitz und Aufräumen“.

## Sicherheitshinweis

Die Oberfläche lauscht nur auf 127.0.0.1, hat keine Anmeldung und erlaubt
Löschungen (Aufräumen) — ausschließlich als lokales Entwicklungswerkzeug
betreiben.

Host-/Origin-Prüfungen begrenzen fremde Browserherkunft; sie sind keine
Authentifizierung lokaler Benutzer oder Prozesse. Lokale API-Clients ohne Origin-Header
sind zugelassen und können auch Läufe, Abbruch, Konfiguration und Exporte auslösen.
Deshalb setzt der Betrieb einen vertrauenswürdigen lokalen Rechner und vertrauenswürdige
lokale Benutzer voraus. Loopback allein bietet keine Trennung mehrerer Benutzer dieses
Rechners; Weiterleitung oder öffentliche Bereitstellung liegt außerhalb dieser Betriebsgrenze.

### Wiederaufnahme und globale Prüfergebnisse

Jedes Selenium-Fenster bekommt ein zufälliges registriertes Chrome-Profil unter
`testsweet/logs/browser-profile/`. Das Aufräumskript beendet ausschließlich Chrome
mit einem exakt passenden registrierten Profilpfad. Alte Fenster ohne diese
Registrierung werden bewusst nicht automatisch zugeordnet.

Nach einem Sitzungstod werden bestätigte Arbeitsschritte nicht erneut ausgeführt.
Einmalig wiederholt werden nur ausdrücklich sichere Anmelde-/Suchschritte, mit
neuer Sitzung unter der zuletzt bestätigten Kennung. Stirbt die Sitzung während
einer schreibenden Aktion, ist der Commit-Ausgang unklar: Der Auftrag schlägt mit
diesem Hinweis fehl; der Schreibschritt wird nicht blind wiederholt. Ein neuer
Treiber steht danach für Folgeaufträge bereit.

Globale Datenbankprüfungen gehören zum Laufresultat. Abweichungen und SQL-Fehler
setzen den Lauf auf `FEHLGESCHLAGEN` und verhindern ein erfolgreiches Ergebnis in
GUI, PDF und Markdown. Bereits verifizierte Aufträge werden als Verifikation mit
Abweichung markiert. Batch-/Web-CLI-Läufe liefern bei erfolglosem Resultat Exitcode 1.
Die Storno-Spurenprüfung ist weiterhin keine vollständige Rollback-Bilanz eines
mehrschrittigen Auftrags.

## Regressionen aus dem Gesamt-Review vom 15.10.2026

Die Gegenprüfung des Grok-Berichts ergänzt Produktregressionen für monatsbezogene
Vorschauen, atomare Änderungsbescheide, eindeutige Kinderzähler und vollständigere
Snapshot-Eingaben. Oracle-Tests prüfen die Zuordnung von Saga-Kompensationen nach
Schrittnamen, eine künstlich verlorene Write-Ahead-Commitquittung nach echtem Commit
und einen Kassenlauf mit 1.001 eigenen BEREIT-Zeilen. Die JavaScript-Prüfung
`node --test testsweet/src/test/js/produkt-ui-review.test.js` führt die vollständige
Produktdatei mit kontrolliertem DOM/HTTP aus: Ein unbekannter Schreibausgang löst nur
lesendes Nachladen aus, und eine Auszahlung behält ihre Vorgangsnummer auch nach
Browser-Reload. Diese gezielten Regressionen ersetzen weder die sieben Simulator-
Vollläufe noch ein neues Gesamtzertifikat.

`SweetUiRegressionTest` startet beide Browserdatei-Gegenproben (`llm-ui.test.js`
und `produkt-ui-review.test.js`) auch im normalen Mavenlauf. Die neue
`GrokFachServiceDbTest` prüft das installierte Produkt mit echten Service-Commits
und konsistenten SQL-Snapshots der fünf Fachtabellen, insbesondere Änderungsbescheid,
Rollback, ungeklärten Altmonats-Vermögenseinsatz, Legacy-Eingaben und Replay.
`ProduktTaskAusgangDbTest` verbindet echten Fachcommit, Task-Wachhund, HTTP 409
und unabhängige SQL-Nachkontrolle. Der Kassenlauf-Test erzwingt die 19c-Parsergrenze
zusätzlich per JDBC-Guard, weil die aktuelle Free-Version 1.001 IDs bereits ohne
Blockaufteilung akzeptiert. Mengenfehler im zweiten Block müssen sämtliche echten
Oracle-Updates zurückrollen. Abdeckung und Grenzen je Review-Befund stehen in
[doc/codereview/testsweet-abdeckung-2026-10-15.md](../doc/codereview/testsweet-abdeckung-2026-10-15.md).

Die eigene Maven-Suite prüft zusätzlich den tatsächlichen Produkt-HttpServer auf alternative numerische Storno-IDs, Rollenprüfung, Login-Drosselung bei voller Phantomliste und Drucktoken nach Logout bzw. BG-Wechsel. Produktkern-Regressionen prüfen echte Oracle-Fehlercodes sowie den PENDING-Absturzpunkt und Wiederanlauf auf Oracle. Bescheid-Datenbanktests prüfen Service- und Saga-Erteilung und identischen Nachdruck nach Bank-/Behördenwechsel.

TF13–15 enthalten Haushalte mit vier, fünf und sechs minderjährigen Kindern und überschneidenden Altersgruppen. TF17 setzt den alten zentralen Warmwasser-Abzugsparameter ausdrücklich ungleich null. Die SQL-Sollbeträge sind vorab handgerechnet; Java-, Node- und Python-Referenzen wurden anhand derselben Rechtsquellen separat berichtigt.

Storno-Szenarien prüfen Vermögen und Restbudget unmittelbar nach dem abgeschlossenen Storno und vor jeder Folgeänderung. Beide Werte werden in einer eigenen READ-ONLY-Transaktion direkt aus der BG-Zeile gelesen. SQL, BG-ID, Soll- und Istwert erscheinen im Auftrag und im Bericht. Abweichungen und SQL-Fehler stoppen den Auftrag. Gespeicherte Serviceantworten ersetzen diese Datenbankprüfung nicht.

Java und Node lesen denselben Korpus aus `doc/testzertifikat-werkzeug/llm-answer-parity-corpus.json` mit 45 formal gültigen und ungültigen Antworten. Der Node-Testaufruf muss neben `certificate-evidence.test.js` auch `llm-answer-parity.test.js` und `llm-reference.test.js` einschließen. Der Antwortvertrag verlangt ausschließlich JSON, keine Markdown-Codeblöcke oder Einleitung, und den exakten Zahlenwert 2 als Schema-Version.

SEPA-Negativfälle geben außerdem die tatsächlich verwendeten synthetischen IBAN-/BIC-Rohwerte je Anlageversuch an. Zwei Eigentests vergleichen diese Berichtsangaben mit den real an die Fachvalidierung übergebenen Argumenten. Damit lässt sich die behauptete ungültige Prüfziffer oder Länge unabhängig nachrechnen.
