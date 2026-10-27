# Belegverzeichnis: ECHT- und RECHNER-Modellfälle der testsweet

**Stand:** 27.10.2026. ECHT enthält fünf quellenbasierte Modellfälle; RECHNER
zwölf feste Vergleichsfälle. Veröffentlichte Quellen liefern Bausteine, nicht
vollständige rechtlich geprüfte Bescheide der zusammengesetzten Konstellationen.

Die Zahlbeträge und Vermögenswerte der Erfolgsfälle sind feste SQL-Erwartungen.
Ihr Startbudget ist die Summe fester Zahlbeträge plus 100 EUR; Restbudget-Soll ist
100 EUR. Dokumentierte Rechenblöcke stammen aus der Produktklasse; zusätzlich
prüft eine getrennte arithmetische Referenz ihre Rechnung. Das ersetzt keine
Prüfung ausgelassener Fachregeln. `EchtRechnerFallTest` vergleicht Rechenblöcke
und die tatsächlich verwendeten SQL-Sollwerte mit separat festgehaltenen Konstanten.

## Quellen und Status

Abruf/Prüfung am 27.10.2026:

- [Anlage zu § 28 SGB XII](https://www.gesetze-im-internet.de/sgb_12/anlage.html):
  amtliche Regelbedarfsstufen 2026.
- [Altenburger Richtlinienanlage](https://sessionnet.altenburgerland.de/bi/getfile.php?id=52833&type=do),
  [Beschluss Nr. 139](https://sessionnet.altenburgerland.de/bi/getfile.php?id=53008&type=do),
  [Sitzung 27.05.2026](https://sessionnet.altenburgerland.de/bi/si0057.php?__ksinr=2244):
  Richtwerte und Nettokaltmietdeckel; Betriebs-/Heizwerte sind Nichtprüfungsgrenzen
  mit Einzelfallprüfung. Deckblatt der Sitzungsanlage hat einen Datumsplatzhalter;
  der gesonderte Beschluss belegt die Beschlussfassung.
- [Lebenshilfe-Ratgeber](https://www.lebenshilfe.de/informieren/arbeiten/grundsicherung-im-alter-und-bei-erwerbsminderung):
  800-EUR-Beispiel und historischer Kfz-Fall **LSG Hamburg, 03.04.2020, L 4 SO 46/18**.
  Die Prüfung bezieht sich auf den Verbandsbeleg; Gerichts-Volltext nicht erreicht.
- BSG-Verfahren B 8 SO 4/24 R:
  [amtliche Rechtsfragenübersicht](https://www.bsg.bund.de/SharedDocs/Downloads/DE/Rechtsfragenuebersichten/Rechtsfragenuebersicht_08_Senat.pdf?__blob=publicationFile&v=60)
  und [Sekundärbericht mit 44.500 EUR](https://sozialrat.org/bsg-urteil-grundsicherung-alter-27-mai-2026-vermoegensuebergangsregel/).
  Der amtliche Urteilsvolltext war nicht verlässlich erreichbar. Aus dem hier
  verfügbaren Beleg folgt kein Nachweis, welche Angaben im Volltext fehlen.

## Kategorie ECHT — ergänzte Modellkonstellationen

| Nr. | Auftrag | Quellenbaustein / freie Ergänzung | Feste Erwartung und Grenze |
|---|---|---|---|
| 1 | ECHT-ANLAGE-KDU | Amtliche RBS 2 = 506 EUR, Altenburger 2-Personen-Werte 320/100/110. Haushalt ohne Einkommen/Vermögen gewählt. | 506+506+320+100+110 = **1.542,00 EUR**, Einsatz/Endvermögen 0. Kein vollständiger Einzelbescheid aus der Quelle. |
| 2 | ECHT-BSG-VERMOEGEN | Historische Gesamtvermögenszahl 44.500 EUR aus Sekundärbericht. Rente 900 EUR frei ergänzt; KdU 260/90/110 aus Richtwerten. | Bedarf 1.023−Rente 900=123; pauschaler Modelleinsatz 44.500−10.000=34.500; 123−34.500=−34.377 → technische **KEIN_ANSPRUCH**-Abwehr. Keine Zahlung; Budget 1.000/Vermögen 44.500 unverändert. Individuelle Privilegierungen und historischer Übergangstatbestand nicht modelliert; 34.500 ist kein bestätigter heutiger Verwertbarkeitswert. |
| 3 | ECHT-SCHONVERMOEGEN-800 | Verbandsbeispiel mit 800 EUR Kontoguthaben; Bedarf/KdU frei ergänzt aus RBS/Richtwerten. | 563+260+90+110 = **1.023,00 EUR**; max(800−10.000,0)=0 Einsatz; Endvermögen **800,00 EUR**. |
| 4 | ECHT-LSG-KFZ-PAAR | Historischer Gesamtwert zweier Kfz 31.000 EUR aus Lebenshilfe-Bericht zu LSG Hamburg. Haushalt/KdU zur Modellrechnung ergänzt. | 31.000−2×10.000=11.000 pauschaler Modelleinsatz; Bedarf 1.542−11.000=−9.458 → technische **KEIN_ANSPRUCH**-Abwehr. Keine Zahlung; Budget 1.000/Vermögen 31.000 unverändert. Heutiger Kfz-Schutz, Einzelwerte und Angemessenheit nicht modelliert; keine heutige rechtliche Abweisung bewiesen. |
| 5 | ECHT-KDU-DECKEL | Nettokaltmietdeckel 390 und Nichtprüfungsgrenzen 90/110 werden zu einem **gewählten Modell-Gesamtdeckel** addiert. | min(500+90+110,590)+563 = **1.153,00 EUR**, Juli 2026. Keine vollständige Richtlinienprüfung: Gesamtdeckel ersetzt weder komponentenweise Entscheidung noch Einzelfallprüfung. |

Die [heutige Vermögensnorm](https://www.gesetze-im-internet.de/sgb_12/__90.html)
sieht zusätzliche Privilegierungen vor. Deshalb dürfen die beiden historischen
Gesamtsummen nicht ohne vorgelagerte Fachentscheidung als heutiges bereinigtes
Vermögen ausgegeben werden. Die Szenarien prüfen ausdrücklich nur die angegebenen
pauschalen Modellannahmen. Beispiel für die KdU-Grenze: Miete 300, Betrieb 90,
Heizung 300 ergibt 690 EUR tatsächliche KdU; der gewählte Gesamtdeckel erkennt
590 an. Ob höhere Heizkosten anzuerkennen sind, erfordert eine separate Entscheidung;
die bloße Summe von Richtlinienwerten beantwortet sie nicht.

## Kategorie RECHNER — feste Vergleichswerte und externe Stichprobe

[Rechner des Anbieters](https://www.steuerschroeder.de/Steuerrechner/Grundsicherung.html),
Seiten-Rechtsstand 03.07.2026. Die ursprüngliche Angabe einer interaktiven
Zwölf-Fälle-Erhebung am 27.10.2026 ist **nicht vollständig belegt**. Die Tabelle
enthält zwölf unabhängig handrechenbare feste Erwartungen. Eigene dokumentierte
Browsergegenproben bestätigen nur die Fälle **1, 6 und 12**.

Gemeinsam: Betriebskosten 0, zentrale Warmwasserbereitung, Vermögen 0. Kein
§ 82a-Grundrentenfreibetrag, keine zusätzliche Altersvorsorge (§ 82 Abs. 4),
keine Witwenrenten-Sonderregeln oder weiteren Absetzungen.

| Nr. | Haushalt | Miete/Heizung EUR | Einkommen EUR | Fester Zahlbetrag EUR | Externer Nachweis dieser Gegenprüfung |
|---|---|---|---|---:|---|
| 1 | Single, RBS 1 | 345/70 | 0 | **978,00** | eigene Browsergegenprobe belegt |
| 2 | Single | 345/70 | Rente 500 | **478,00** | nur Handrechnung; kein neuer Browserbeleg |
| 3 | Single | 260/110 | Rente 350 | **583,00** | nur Handrechnung; kein neuer Browserbeleg |
| 4 | Single | 390/110 | 0 | **1.063,00** | nur Handrechnung; kein neuer Browserbeleg |
| 5 | Single, G | 345/70 | 0 | **1.073,71** | nur Handrechnung; kein neuer Browserbeleg |
| 6 | Single, G | 320/90 | Rente 800 | **268,71** | eigene Browsergegenprobe belegt |
| 7 | Single | 345/70 | Erwerb 600 → 420 anrechenbar | **558,00** | nur Handrechnung; kein neuer Browserbeleg |
| 8 | Single | 345/70 | Erwerb 300 → 210 anrechenbar | **768,00** | nur Handrechnung; kein neuer Browserbeleg |
| 9 | Paar, 2×RBS 2 | 345/70 | 0 | **1.427,00** | nur Handrechnung; kein neuer Browserbeleg |
| 10 | Paar | 345/70 | Rente 700 | **727,00** | nur Handrechnung; kein neuer Browserbeleg |
| 11 | Paar | 345/70 | Rente 1.200 | **227,00** | nur Handrechnung; kein neuer Browserbeleg |
| 12 | Paar | 345/70 | Erwerb 500 → 350 anrechenbar | **1.077,00** | eigene Browsergegenprobe belegt |

### Konkrete Browserbelege vom 27.10.2026

| Fall | Eingaben | Ergebnistext | Screenshot |
|---|---|---|---|
| 1 | [Formular](rechner-steuerschroeder-fall1-gegenprobe-2026-10-27-eingabe.txt) | [Ergebnis](rechner-steuerschroeder-fall1-gegenprobe-2026-10-27.txt) | [Bild](rechner-steuerschroeder-fall1-gegenprobe-2026-10-27.png) |
| 6 | [Formular](rechner-steuerschroeder-fall6-gegenprobe-2026-10-27-eingabe.txt) | [Ergebnis](rechner-steuerschroeder-fall6-gegenprobe-2026-10-27.txt) | [Bild](rechner-steuerschroeder-fall6-gegenprobe-2026-10-27.png) |
| 12 | [Formular](rechner-steuerschroeder-fall12-gegenprobe-2026-10-27-eingabe.txt) | [Ergebnis](rechner-steuerschroeder-fall12-gegenprobe-2026-10-27.txt) | [Bild](rechner-steuerschroeder-fall12-gegenprobe-2026-10-27.png) |

Die neuen Belege sind unveränderte Kopien der eigenen Reviewgegenproben.
Das vorhandene `rechner-steuerschroeder-fall12-2026-10-27.png` wurde ursprünglich
falsch als Ergebnisansicht bezeichnet: Es zeigt Text/Regelsatztabelle und Cookiebox,
**keine Eingaben und kein Rechenergebnis**. Es bleibt als historisches Artefakt erhalten,
ist aber kein Vergleichsnachweis. Tabellenwerte und Codekonstanten allein beweisen
keine vorherigen externen Eingabevorgänge.

## Nachweisumfang und verbleibende Grenzen

- ECHT und RECHNER sind eigenständige Fachläufe und **nicht Bestandteil der
  automatischen Zertifikatssuite oder GLM-Auswahl**. Ein grünes Gesamtzertifikat
  der sieben bestehenden Kombinationen bestätigt deren 17 SQL-Läufe nicht mit.
- Der externe Rechner ist eine Überschlagsrechnung. Die Stichprobe belegt die
  dokumentierte Schnittmenge; weder vollständige Rechtsdeckung noch einen Bescheid.
- In den recherchierten Quellen wurde kein geeigneter vollständiger öffentlicher
  Fallbestand gefunden. Das ist kein Nachweis genereller Nichtexistenz. Zulässigkeit
  einer Veröffentlichung hängt unter anderem von Personenbezug, wirksamer Anonymisierung
  und konkreter Rechtsgrundlage ab; siehe [§ 67](https://www.gesetze-im-internet.de/sgb_10/__67.html)
  und [§ 67b SGB X](https://www.gesetze-im-internet.de/sgb_10/__67b.html).
