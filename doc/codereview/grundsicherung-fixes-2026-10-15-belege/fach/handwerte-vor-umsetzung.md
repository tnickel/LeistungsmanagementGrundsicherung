# Fachkorrekturen GR-02/03: vorab festgelegte Sollwerte

15.10.2026. Diese Werte wurden aus den Fachregeln festgelegt, bevor Produktquellen geändert oder Tests gestartet wurden. Keine Produktberechnung wurde zur Festlegung der Beträge aufgerufen.

## Normen und Reichweite

[§ 30 Abs. 3 SGB XII](https://www.gesetze-im-internet.de/sgb_12/__30.html) und [WD 6-046/24, S. 8–11](https://www.bundestag.de/resource/blob/1019234/WD-6-046-24-pdf.pdf), frisch online geprüft. Der Wortlaut der Norm allein enthält eine einschränkende Formulierung zur zweiten Alternative. Die Ausarbeitung der Wissenschaftlichen Dienste erklärt dagegen den Vergleich des günstigeren Betrags und überträgt ihn auf § 30 Abs. 3. Sie ist eine amtliche Auslegungshilfe, keine bindende Einzelfallentscheidung. Standardannahmen hier: alleinige Pflege und Erziehung, kein abweichender Einzelfallbedarf. Diese Voraussetzungen werden durch Kinderzahlen nicht bewiesen. Wechselnde Betreuung und Einzelfallbedarf bleiben eigenständig nicht modelliert.

[§ 35 Abs. 5 SGB XII](https://www.gesetze-im-internet.de/sgb_12/__35.html), frisch online geprüft: Zentrale Warmwasserkosten sind Heizbedarf. Ein dezentraler Mehrbedarf nach § 30 Abs. 7 setzt voraus, dass kein entsprechender zentraler Warmwasserbedarf anerkannt wird. Daraus folgt kein zusätzlicher zentraler Heizkostenabzug. Keine örtliche Pauschalierungsregel wird behauptet.

## Mehrbedarf ohne Einkommen/Vermögen

RBS 1 = 563 EUR. Verglichen werden die erfüllte 36-Prozent-Alternative und 12 Prozent je minderjährigem Kind, Deckel 60 Prozent:

| Minderjährige/U7/U16 | 36 % | Kindbezogen | Maßgeblich |
|---|---:|---:|---:|
| 4/1/2 | 202,68 | 270,24 | 270,24 EUR |
| 5/0/3 | 202,68 | 337,80 | 337,80 EUR |
| 6/1/3 | 202,68 | 405,36, Deckel 337,80 | 337,80 EUR |

Produkt-, Java-, Node- und Python-Referenztests verwenden genau diese Kombinationen. Die Node- und Simulatorregressionen müssen die früheren falschen 202,68 EUR beanstanden.

## Feste Simulatorfälle mit vollständigem Haushalt

Die Kinderstufen folgen passenden Beispielaltern: RBS 6 für unter sechsjährige U7-Kinder, RBS 5 für die übrigen unter sechzehnjährigen Kinder (hier sechs bis dreizehn), RBS 4 für Sechzehn-/Siebzehnjährige.

| Fall | Rechnung | SQL-Sollbetrag |
|---|---|---:|
| TF13 | 563 + 270,24 + 300 KdU + 357 + 390 + 2 × 471 | 2.822,24 EUR |
| TF14 | 563 + 337,80 + 300 KdU + 3 × 390 + 2 × 471 | 3.312,80 EUR |
| TF15 | 563 + 337,80 + 300 KdU + 357 + 2 × 390 + 3 × 471 | 3.750,80 EUR |
| TF17 | 563 + 350 Miete + 50 Betriebskosten + 100 Heizkosten | 1.063,00 EUR |

TF17 nutzt den akzeptierten historischen Konfigurationsanteil 0,20. Erwarteter Warmwasserabzug ist 0 EUR und kein dezentraler Mehrbedarf. Die alten 1.043 EUR sind falsch. Die KdU-Gesamtgrenze wird weiterhin getrennt geprüft. Bei zwei RBS-2-Personen mit je 500 EUR Wohnkosten und Grenze 600 EUR sind die anerkannten Anteile 500/100 EUR, ohne zusätzliche Warmwasserkürzung; dezentrales Warmwasser der zweiten Person ergänzt 11,64 EUR, BG-Summe somit 1.623,64 EUR.

Die vier reparierten Simulatorfälle erhalten feste SQL-Sollkonstanten. Produktberechnung und referenzierte Rechenblöcke können damit nicht gemeinsam einen falschen Auszahlungsbetrag legitimieren.
