# Rechtsgrundlagen SDN.Leistung LMG — Verzeichnis mit Herkunftsnachweisen

**Zweck:** Dies ist das maßgebliche Verzeichnis aller im Projekt umgesetzten Fach- und
Verfahrensnormen. Jeder Eintrag nennt die Norm, ihren Inhalt nach amtlichem Wortlaut,
die Umsetzung im Code und die bezogene Quelle. Code-Reviews und Fachprüfungen prüfen
gegen dieses Verzeichnis, nicht gegen freie Auslegung.

**Stand der Verifikation: 2026-10-10.** Alle Normen wurden an diesem Tag gegen die
amtliche Bekanntmachung auf <https://www.gesetze-im-internet.de> im Live-Abruf geprüft
(Einzelprotokoll in Abschnitt 5). Die Verordnung zu § 90 Abs. 2 Nr. 9 SGB XII wurde
zusätzlich gegen den buzer.de-Spiegel samt BGBl-Fundstelle verifiziert; die
Regelbedarfsstufen 2026 zusätzlich gegen die Verlautbarung der Bundesregierung.

**Gültigkeit der Jahreswerte:** Die Beträge der Regelbedarfsstufen gelten je
Gültigkeitszeitraum ab dem 1. Januar eines Jahres und stehen in der Tabelle
`SATZSTAND` (seit v0.5.6; Start 2026-01). Sie sind jährlich per Verordnung
fortzuschreiben — als neue Tabellenzeile (Abschnitt 6 Änderungsdienst).

---

## 1. Fachrecht SGB XII — Bedarfsberechnung

### 1.1 Regelbedarfsstufen (§ 28 SGB XII i.V.m. Anlage)

§ 28 SGB XII („Ermittlung der Regelbedarfe") bestimmt in Abs. 5, dass die
hoch- bzw. abgerundeten Summenbeträge die Regelbedarfsstufen ergeben (**Anlage** zum Gesetz).

| Stufe | Personenkreis | Betrag 2026 (EUR/Monat) |
|---|---|---|
| 1 | Alleinstehende/Alleinerziehende, erwachsene Personen einer Wohnung | 563,00 |
| 2 | Volljährige Partner in einer Bedarfsgemeinschaft (je Person) | 506,00 |
| 3 | Volljährige ohne eigene Wirtschaftführung im Haushalt | 451,00 |
| 4 | Jugendliche 14 bis unter 18 Jahre | 471,00 |
| 5 | Kinder 6 bis unter 14 Jahre | 390,00 |
| 6 | Kinder bis zur Vollendung des 6. Lebensjahres | 357,00 |

- **Herkunft:** [§ 28 SGB XII](https://www.gesetze-im-internet.de/sgb_12/__28.html)
  (Anlage dort verlinkt); Werte 2026 bestätigt durch
  [bundesregierung.de — Regelbedarfe 2026](https://www.bundesregierung.de) („Regelsätze
  bei Bürgergeld und Sozialhilfe unverändert") und die Tabelle der Regelbedarfe auf
  sozialpolitik-aktuell.de. Die frühere Prüflücke „Abgleich Bundesanzeiger" (Blueprint
  v0.5.2) gilt damit als geschlossen.
- **Umsetzung:** Tabelle `SATZSTAND` (eine Zeile je Gültigkeitsmonat, Startwert
  2026-01: 563/506/451/471/390/357 EUR in `ddl/lmg_seed.sql`); der Produktionspfad
  (Service und Saga) liest den zum Bezugsmonat gültigen Stand über `SatzstandDao`
  — die jährliche Fortschreibung ist ein INSERT, keine Codeänderung. Die
  Seed-Referenz `Satzstand.REFERENZ_2026` dient nur Übergangs-/Testpfaden.

### 1.2 Mehrbedarfe (§ 30 SGB XII)

| Norm | Amtlicher Inhalt (Kurzfassung, verifiziert) | Umsetzung |
|---|---|---|
| § 30 Abs. 1 | Personen, die die Altersgrenze erreicht haben oder vor Erreichen voll erwerbsgemindert sind (Nachweis des **Merkzeichens G** durch Bescheid/Ausweis): Mehrbedarf **17 %** der maßgebenden Regelbedarfsstufe | `FLAG_MERKZEICHEN_G` in `SOZIAL_FALL` |
| § 30 Abs. 2 | **Schwangere ab der 13. Schwangerschaftswoche** (Gesetz: „nach der zwölften Schwangerschaftswoche") bis zum Ende des Entbindungsmonats: **17 %** | `FLAG_SCHWANGER` mit optionalem `ENTBINDUNGSMONAT` (YYYY-MM): Seit v0.5.8 entfällt in späteren Bezugsmonaten automatisch nur dieser Mehrbedarf, in Service und Saga; ohne erfassten Monat liegt die Einhaltung in der Erfassungsverantwortung |
| § 30 Abs. 3 | **Alleinerziehende**, bezogen auf die **Regelbedarfsstufe 1**: 36 % bei einem Kind unter 7 oder zwei/drei Kindern unter 16; sonst 12 % je minderjährigem Kind; **maximal 60 %** der RBS 1 | `MEHRBEDARF_*`-Logik in `Leistungsberechnung` |
| § 30 Abs. 6 | Deckel: Die Summe des nach **Abs. 1 bis 5** anzuerkennenden Mehrbedarfs darf die maßgebende Regelbedarfsstufe nicht übersteigen | umgesetzt; Abs. 7 bewusst außerhalb des Deckels (Wortlaut: „Absätze 1 bis 5") |
| § 30 Abs. 7 | **Dezentrale Warmwassererzeugung**, je Regelbedarfsstufe der Person: **2,3 % (Stufe 1), 2,3 % (Stufe 2), – (Stufe 3), 1,4 % (Stufe 4), 1,2 % (Stufe 5), 0,8 % (Stufe 6)**; Höheres nur mit separatem Messnachweis | `MEHRBEDARF_WARMWASSER_*` in `Leistungsberechnung` |

- **Herkunft:** [§ 30 SGB XII](https://www.gesetze-im-internet.de/sgb_12/__30.html)
  (amtlicher Wortlaut am 2026-10-10 geprüft: alle Prozentsätze, RBS-1-Bezug in Abs. 3,
  Deckelreichweite in Abs. 6, Stufenprozente in Abs. 7 wörtlich bestätigt).

### 1.3 Kosten der Unterkunft und Heizung (§§ 35, 42a SGB XII)

§ 42a SGB XII („Bedarfe für Unterkunft und Heizung", Viertes Kapitel — Grundsicherung im
Alter/bei Erwerbsminderung) verweist für die Einzelheiten auf § 35 SGB XII
(„Bedarfe für Unterkunft und Heizung").

| Norm | Amtlicher Inhalt (Kurzfassung, verifiziert) | Umsetzung |
|---|---|---|
| § 35 Abs. 1 | Bedarfe für Unterkunft und Heizung in Höhe der **tatsächlichen Aufwendungen, soweit angemessen**; Karenzzeit 1 Jahr; Obergrenze: Nicht anerkannt wird, was das **Anderthalbfache** der abstrakt angemessenen Aufwendungen übersteigt | Grundanerkennung umgesetzt; **nicht modelliert:** Karenzzeit (Abs. 1 S. 2–9) und 1,5×-Obergrenze (bewusste Vereinfachung) |
| § 35 Abs. 3 | Absenkungsfrist: unangemessen hohe Aufwendungen i. d. R. längstens **6 Monate**; nach Tod eines Haushaltsmitglieds mindestens **12 Monate** Zumutbarkeits-Schutz | **nicht modelliert** (dokumentierte Vereinfachung) |
| § 35 Abs. 5 | Bedarfe für Heizung umfassen **auch Aufwendungen für zentrale Warmwasserversorgung** | umgesetzt: bei zentraler Erzeugung Warmwasseranteil-Abzug von den Heizkosten (`lmg.kdu.warmwasserabzug`) |
| § 35 Abs. 7 | Bildung einer **Gesamtangemessenheitsgrenze** ist zulässig | umgesetzt: `lmg.kdu.angemessenheitsgrenze` je Bedarfsgemeinschaft, Kürzung mit Bescheid-Kennzeichnung |

- **Herkunft:** [§ 35 SGB XII](https://www.gesetze-im-internet.de/sgb_12/__35.html),
  [§ 42a SGB XII](https://www.gesetze-im-internet.de/sgb_12/__42a.html).
- **Umsetzung:** Dreiteilung Kaltmiete/kalte Betriebskosten/Heizung je `SOZIAL_FALL`,
  BG-weite Summierung und Grenzprüfung in `Leistungsberechnung`.

### 1.4 Einkommensanrechnung (§ 82 SGB XII)

| Norm | Amtlicher Inhalt (Kurzfassung, verifiziert) | Umsetzung |
|---|---|---|
| § 82 Abs. 2 | Vom Einkommen abzusetzen: 1. **Steuern**, 2. **Pflichtbeiträge zur Sozialversicherung** inkl. Arbeitsförderung, 3. **Beiträge zu Versicherungen** (gesetzlich vorgeschrieben oder angemessen), 4. **mit der Erzielung verbundene notwendige Ausgaben** | umgesetzt: `ABZ_STEUERN`, `ABZ_SV`, `ABZ_VERSICHERUNGEN`, `ABZ_ERWERBSAUSGABEN` in `SOZIAL_FALL` |
| § 82 Abs. 3 | Absetzbar: **30 %** des Einkommens aus selbständiger/nichtselbständiger Tätigkeit, **höchstens 50 % der Regelbedarfsstufe 1** (2026: 281,50 EUR) | umgesetzt (`ERWERBSFREIBETRAG_*`). **Dokumentierte Auslegung:** Freibetragsbasis ist das **Brutto**-Erwerbseinkommen (analog § 11b Abs. 3 SGB II „Bruttoeinkommen"); die Absetzbeträge nach Abs. 2 werden zusätzlich gezogen |
| § 82 Abs. 3 S. 2 | Sonderregel Entgelt aus **Werkstätten für behinderte Menschen** (1/8 der RBS 1 + 50 % des übersteigenden Entgelts) | **nicht modelliert** |
| § 82 Abs. 4, 5 | Freibetrag zusätzliche Altersvorsorge (100 EUR + 30 %) | **nicht modelliert** |

- **Herkunft:** [§ 82 SGB XII](https://www.gesetze-im-internet.de/sgb_12/__82.html).

### 1.5 Vermögenseinsatz und Schonvermögen (§ 90 SGB XII, § 43 SGB XII, DVO)

| Norm | Amtlicher Inhalt (Kurzfassung, verifiziert) | Umsetzung |
|---|---|---|
| § 90 Abs. 2 Nr. 9 SGB XII | Schonung **kleinerer Barbeträge oder sonstiger Geldwerte** unter Berücksichtigung einer besonderen Notlage; die Beträge bestimmt die Rechtsverordnung (unten) | i.V.m. DVO § 1 umgesetzt |
| DVO zu § 90 Abs. 2 Nr. 9 SGB XII (Verordnung zur Durchführung des § 90 Abs. 2 Nr. 9 des Zwölften Buches Sozialgesetzbuch vom 11.02.1988, BGBl. I S. 150, zuletzt geändert durch Art. 9 Bürgergeld-Gesetz vom 16.12.2022, BGBl. I S. 2328, in Kraft seit 01.01.2023) — **§ 1** | Verschont: **10.000 EUR** für Personen nach § 19 Abs. 3, § 27 Abs. 1–2, **§ 41** (Grundsicherung im Alter/bei Erwerbsminderung) und § 43 Abs. 1 S. 2 SGB XII sowie für jede alleinstehende minderjährige Person; **zusätzlich 500 EUR für jede überwiegend unterhaltene Person** | umgesetzt: `SCHONVERMOEGEN_GRUNDBETRAG` (10.000) und `SCHONVERMOEGEN_UNTERHALTENE_PERSON` (500) **mit Verbrauchsfortschreibung** (seit v0.5.6): Der über dem Schonvermögen liegende Anteil verbraucht sich mit der ersten Auszahlung (Bestand sinkt in derselben Transaktion, Einsatz an der Zahlung dokumentiert — `ZAHLUNG.VERMOEGENSEINSATZ`); Storno bucht genau den dokumentierten Einsatz zurück. Ab dem Folgemonat liegt der Rest im Schonvermögen und mindert die Leistung nicht mehr. Projekt-Vereinfachung bleibt die Zuordnung über RBS (10.000 je Person RBS 1–3, 500 je unterhaltener Person RBS 4–6) |
| DVO § 2 | Erhöhung bei besonderer Notlage; Herabsetzung in Fällen der §§ 103, 94 SGB XII | **nicht modelliert** |
| § 43 Abs. 1 SGB XII („Einsatz von Einkommen und Vermögen") | §§ 82–84 (Einkommen) und §§ 90, 91 (Vermögen) gelten entsprechend; Einkommen/Vermögen des nicht getrennt lebenden Ehegatten/Lebenspartners einbezogen | umgesetzt (Vermögen mindert den Zahlbetrag) |
| § 43 Abs. 2 SGB XII | Anrechnungsfrei bleiben **Einnahmen aus Kapitalvermögen bis 26 EUR im Kalenderjahr** | **nicht modelliert**. *Hinweis:* Die frühere Doku-Zeile „§ 43 Abs. 2: Einkommensgrenze 100.000 EUR" war falsch — die 100.000-EUR-Grenze steht heute in **§ 94 Abs. 1a SGB XII** (Unterhaltsansprüche gegen Kinder/Eltern nur oberhalb 100.000 EUR Jahresgesamteinkommen, eingefügt durch das Angehörigen-Entlastungsgesetz zum 01.01.2020) und betrifft den Unterhaltsrückgriff, nicht die Bedarfsberechnung; **nicht modelliert** |

- **Herkunft:** [§ 90 SGB XII](https://www.gesetze-im-internet.de/sgb_12/__90.html),
  [§ 43 SGB XII](https://www.gesetze-im-internet.de/sgb_12/__43.html),
  [§ 94 SGB XII](https://www.gesetze-im-internet.de/sgb_12/__94.html),
  DVO-Wortlaut incl. Fundstelle: [buzer.de/gesetz/2234](https://www.buzer.de/gesetz/2234/index.htm).
  Die frühere Prüflücke „BMJ-Primärseite nicht abrufbar" (Blueprint v0.5.2) ist damit
  geschlossen: Beträge und Fundstelle sind verifiziert.

## 2. Bescheid- und Verfahrensrecht

| Norm | Amtlicher Inhalt (Kurzfassung, verifiziert) | Umsetzung |
|---|---|---|
| § 44 SGB XII („Antragserfordernis, Erbringung von Geldleistungen, Bewilligungszeitraum") | Bewilligungszeitraum i. d. R. **12 Monate** (Abs. 3); bei vorläufiger Entscheidung nach § 44a Verkürzung auf höchstens 6 Monate; Antrag wirkt auf den **Ersten des Antragsmonats** zurück (Abs. 2); wiederkehrende Geldleistungen monatlich im Voraus (Abs. 4) | umgesetzt: Zeitraum `BEWILLIGUNG_VON/BIS` mit Format-, Reihenfolge- und 12-Monats-Prüfung, Vorauszahlung im Verfügungssatz; offen: Abs. 2 Rückwirkung, § 44a vorläufige Entscheidung |
| § 35 SGB X („Begründung des Verwaltungsaktes") | Schriftliche Verwaltungsakte sind zu begründen; **wesentliche tatsächliche und rechtliche Gründe** mitteilen; Ermessensgesichtspunkte erkennbar | `BescheidGenerator`: Verfügungssatz, Bedarfsaufschlüsselung, fallbezogene Begründung |
| § 36 SGB X („Rechtsbehelfsbelehrung") | Belehrung über Rechtsbehelf, zuständige Stelle/Gericht, **Sitz, Frist und Form** | `BescheidGenerator`: konfigurierbare Belehrung |
| § 37 Abs. 2 SGB X („Bekanntgabe des Verwaltungsaktes") | Schriftlicher Verwaltungsakt, der im Inland durch die Post übermittelt wird, gilt **am vierten Tag nach der Aufgabe zur Post** als bekannt gegeben (elektronisch: am vierten Tag nach Absendung); Behörde trägt Nachweislast für späteren Zugang. *Amtlicher Wortlaut 2026-10-10; die frühere „Dritte-Tage-Fiktion" ist überholt* | Bescheidtext korrekt („am vierten Tag", seit v0.5.3); `BESCHEID_DATUM` bleibt Ausstellungsdatum, Bekanntgabe ist ein eigener, nicht erfasster Zeitpunkt |
| § 84 SGG („Einlegung des Widerspruchs") | Widerspruch **binnen eines Monats** ab Bekanntgabe (im Ausland: 3 Monate); fristwahrend auch bei **anderer inländischer Behörde, Versicherungsträger oder deutscher Konsularbehörde** (Abs. 2) | Belehrung |
| § 87 SGG („Klagefrist") | Klage binnen eines Monats — ohne Widerspruch ab Bekanntgabe des Verwaltungsakts, **nach Vorverfahren ab Bekanntgabe des Widerspruchsbescheids** (Abs. 2) | Belehrung (`modus`) |
| § 88 SGG („Untätigkeitsklage") | **Abs. 1:** Klage gegen nicht beschiedenen **Antrag** frühestens nach **sechs Monaten**. **Abs. 2:** Über einen **Widerspruch** soll binnen **drei Monaten** entschieden sein — danach Untätigkeitsklage möglich | Belehrung korrekt auf den Widerspruchsfall bezogen („Nach Ablauf von drei Monaten ohne Entscheidung über den Widerspruch …"); Doku-Kurzform „nach 3 Monaten" ist nur mit diesem Bezug zulässig |
| § 73 Abs. 1 SGG | Vor den Gerichten der Sozialgerichtsbarkeit besteht kein Vertretungszwang (Beklagtenvertretung abweichend) | Belehrung („Einer Vertretung … bedarf es nicht", korrekt zitiert als Abs. 1; die Vertretungs<b>befugnisse</b> des Abs. 2 sind nicht Teil der Belehrung) |

- **Herkunft:** [§ 35 SGB X](https://www.gesetze-im-internet.de/sgb_10/__35.html),
  [§ 36 SGB X](https://www.gesetze-im-internet.de/sgb_10/__36.html),
  [§ 37 SGB X](https://www.gesetze-im-internet.de/sgb_10/__37.html),
  [§ 84 SGG](https://www.gesetze-im-internet.de/sgg/__84.html),
  [§ 87 SGG](https://www.gesetze-im-internet.de/sgg/__87.html),
  [§ 88 SGG](https://www.gesetze-im-internet.de/sgg/__88.html),
  [§ 73 SGG](https://www.gesetze-im-internet.de/sgg/__73.html).

## 3. Betrags- und Parameterverzeichnis (hart codierte Werte)

| Wert | Betrag/Satz | Normherkunft | Code-Fundstelle |
|---|---|---|---|
| RBS 1–6 (je Jahr) | 2026: 563,00 / 506,00 / 451,00 / 471,00 / 390,00 / 357,00 EUR | Anlage zu § 28 SGB XII (s. o.) | Tabelle `SATZSTAND` (Seed 2026-01) |
| Mehrbedarf Merkzeichen G | 17 % der maßgebenden RBS | § 30 Abs. 1 SGB XII | `Leistungsberechnung` |
| Mehrbedarf Schwangerschaft | 17 % | § 30 Abs. 2 SGB XII | `Leistungsberechnung` |
| Mehrbedarf Alleinerziehende | 36 % / 12 %, Deckel 60 % der RBS 1 | § 30 Abs. 3 SGB XII | `MEHRBEDARF_ALLEINERZIEHEND_*` |
| Mehrbedarf Warmwasser (dezentral) | 2,3 / 2,3 / – / 1,4 / 1,2 / 0,8 % je RBS 1–6 | § 30 Abs. 7 SGB XII | `MEHRBEDARF_WARMWASSER_*` |
| Erwerbsfreibetrag | 30 % des Brutto-Erwerbseinkommens | § 82 Abs. 3 SGB XII | `ERWERBSFREIBETRAG_ANTEIL` |
| Freibetragskappung | 50 % der RBS 1 (= 281,50 EUR in 2026) | § 82 Abs. 3 SGB XII | `ERWERBSFREIBETRAG_KAPPUNG_RBS1` |
| Schonvermögen Grundbetrag | 10.000 EUR je Person | DVO § 1 zu § 90 Abs. 2 Nr. 9 SGB XII | `SCHONVERMOEGEN_GRUNDBETRAG` |
| Schonvermögen je unterhaltene Person | 500 EUR | DVO § 1 (ebda.) | `SCHONVERMOEGEN_UNTERHALTENE_PERSON` |
| KdU-Angemessenheitsgrenze | konfigurierbar (`lmg.kdu.angemessenheitsgrenze`) | § 35 Abs. 7 SGB XII | `LmgConfig`/`KduParameter` |
| Warmwasser-Abzug Heizkosten | konfigurierbar (`lmg.kdu.warmwasserabzug`), nur bei zentraler Erzeugung | § 35 Abs. 5 SGB XII | `LmgConfig`/`KduParameter` |

## 4. Bewusst nicht modellierte Normen (Abgrenzungsverzeichnis)

Krisenfeste Dokumentation heißt auch festzuhalten, was bewusst **nicht** gilt:

1. § 35 Abs. 1 S. 2–9, Abs. 3 S. 2–5 SGB XII — Karenzzeit (1 Jahr), Absenkungsfrist
   (6 Monate), 12-Monats-Schutz nach Tod eines Haushaltsmitglieds, 1,5×-Obergrenze.
2. § 82 Abs. 2 Satz 2 (Taschengelder aus Freiwilligendiensten bis 250 EUR/Monat),
   Abs. 3 S. 2 (WfbM-Sonderregel), Abs. 4, Abs. 5 SGB XII.
3. § 43 Abs. 2 SGB XII (Kapitalerträge 26 EUR/Jahr); § 43 Abs. 3–5.
4. § 94 Abs. 1a SGB XII (100.000-EUR-Grenze beim Unterhaltsrückgriff gegen Eltern/Kinder).
5. DVO § 2 zu § 90 Abs. 2 Nr. 9 SGB XII (Erhöhung/Herabsetzung des Schonvermögens).
   Die Verbrauchsfortschreibung ist seit v0.5.6 umgesetzt (s. Abschnitt 1.5); die
   Maske darf den Bestand weiterhin fortschreiben (Deklaration ersetzt die
   automatische Fortschreibung).
6. § 44a SGB XII (vorläufige Entscheidung, verkürzter Bewilligungszeitraum);
   Nachzahlungen/Aufrechnung nach § 43a SGB XII.
7. Änderungen nach Bescheiderteilung: Neue Bescheide speichern seit v0.5.8
   einen Monatsplan mit den bei Erteilung bereits bekannten Satzständen,
   dem Schwangerschaftsende und dem simulierten Vermögenseinsatz. Der Nachdruck
   zeigt genau diesen Snapshot. Nachträgliche Änderungen von Sätzen, Vermögen,
   Einkommen oder Fällen werden nicht automatisch durch einen Änderungsbescheid
   (§ 48 SGB X) aufgearbeitet. Zahlungen rechnen weiterhin mit dem Datenbestand
   des Zahlungszeitpunkts; die verfahrensrechtliche Abstimmung solcher späteren
   Änderungen und die Korrektur bereits erteilter alter Bescheide bleiben offen.

## 5. Verifikationsprotokoll (2026-10-10)

Methode: Live-Abruf der amtlichen Einzelnormen auf gesetze-im-internet.de über die
Web-Oberfläche des Projekts (kein veraltetes Zitat aus Sekundärquellen); DVO zusätzlich
über buzer.de mit BGBl-Nachweis; Regelbedarfsstufen 2026 kreuzgeprüft gegen die
Verlautbarung der Bundesregierung und sozialpolitik-aktuell.de.

| Norm | Ergebnis |
|---|---|
| § 28 + Anlage SGB XII | **bestätigt** (Beträge 2026, unverändert zum Vorjahr) |
| § 30 Abs. 1–3, 6, 7 SGB XII | **bestätigt** (alle Sätze und Prozentsätze wörtlich) |
| § 35 Abs. 1, 3, 5, 7 SGB XII | **bestätigt**; Detail 1,5×-Obergrenze ergänzt |
| § 42a SGB XII | **bestätigt** (Verweisgefüge auf § 35) |
| § 43 Abs. 1 SGB XII | **bestätigt** |
| § 43 Abs. 2 SGB XII | **korrigiert**: Inhalt ist die 26-EUR-Kapitalertragsgrenze; die alte Doku-Angabe „Einkommensgrenze 100.000 EUR" war falsch (richtige Norm: § 94 Abs. 1a SGB XII, nicht modelliert) |
| § 44 SGB XII | **bestätigt** (12 Monate Regelzeitraum; 6 Monate nur vorläufig nach § 44a; Monatsersten-Rückwirkung) |
| § 82 Abs. 2, 3 SGB XII | **bestätigt**; Brutto-Lesart des Freibetrags als dokumentierte Auslegung ausgewiesen |
| § 90 Abs. 2 Nr. 9 SGB XII + DVO § 1 | **bestätigt** (10.000/500 EUR; DVO 1988, idF 2023); Namenshinweis: maßgeblich ist die *DVO zu § 90 Abs. 2 Nr. 9*, nicht die SGB-VIII-Taschengeld-VO „Barbetragsverordnung" |
| § 94 Abs. 1a SGB XII | **neu aufgenommen** (100.000-EUR-Grenze, Angehörigen-Entlastungsgesetz) |
| §§ 35, 36 SGB X | **bestätigt** |
| § 37 Abs. 2 SGB X | **korrigiert**: amtlich „**am vierten Tag** nach der Aufgabe zur Post als bekannt gegeben". Code (Bescheidtext) war bereits korrekt; die Kurzbeschreibung in `leistungsmanagement.md` sagte noch „3. Tag" und wurde mit diesem Dokument berichtigt |
| §§ 84, 87 SGG | **bestätigt** |
| § 88 SGG | **präzisiert**: 6 Monate (Antrag, Abs. 1) / 3 Monate (Widerspruch, Abs. 2); Bescheidtext korrekt, da auf den Widerspruch bezogen |
| § 73 Abs. 2 SGG | **bestätigt** |

Offene Restpunkte: keine für die umgesetzten Funktionen. Für Nicht-modelliertes gilt
Abschnitt 4.

## 6. Änderungsdienst

- **Jährlich (Dezember/Januar):** Fortschreibung der Regelbedarfsstufen prüfen
  (Verordnung zum Folgejahr). Bei Änderung genügt seit v0.5.6 eine neue Zeile in
  der Tabelle `SATZSTAND` (GUELTIG_AB = Januar des Folgejahres) — Code, Tests und
  Seed bleiben unberührt; `SatzstandDbTest` deckt die Auswahl je Bezugsmonat ab.
  Die abgeleiteten Werte (Kappung 50 % RBS 1, RBS-1-Bezug des
  Alleinerziehenden-Mehrbedarfs) folgen automatisch aus der Tabelle.
- **Bei Gesetzesänderungen:** betroffene Norm gegen gesetze-im-internet.de prüfen,
  Abschnitt 5 um eine Zeile ergänzen (Datum, Ergebnis), Code/Doku nachziehen.
- **Verantwortlich:** wechselndes Fach-Review; jede Änderung dieses Dokuments mit
  Commit verknüpfen, damit der Nachweis der Prüfung revisionssicher bleibt.
