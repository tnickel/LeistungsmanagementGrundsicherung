# Belegverzeichnis: ECHT- und RECHNER-Testfälle der testsweet

**Stand:** 2026-10-27 · Diese Datei ist das Quellenverzeichnis der beiden
testsweet-Kategorien **ECHT** (5 belegte Fälle) und **RECHNER** (12 extern
abgeglichene Fälle). Jeder erwartete Zahlbetrag steht als fester Wert im Code
(`SzenarioBibliothek`) und wird per SQL gegen das Produktergebnis geprüft.

## Kategorie ECHT — belegte echte Konstellationen

| # | Auftrag | Quelle (Abruf 27.10.2026) | Erwartung (hergeleitet) |
|---|---------|---------------------------|--------------------------|
| 1 | ECHT-ANLAGE-KDU | Anlage zu § 28 SGB XII (https://www.gesetze-im-internet.de/sgb_12/anlage.html, RBS 2 = 506 €) + KdU-Richtlinie Landkreis Altenburger Land, Beschluss 27.05.2026 (2 Personen: Kaltmiete 320 / Betriebskosten 100 / Heizkosten 110 €) | Zahlbetrag 1.542,00 € = 506+506+530 |
| 2 | ECHT-BSG-VERMOEGEN | BSG, Urteil v. 27.05.2026, B 8 SO 4/24 R (Vermögen ~44.500 €; Rente/KdU im Urteil nicht genannt, ergänzt und dokumentiert) | Verwertbares Vermögen 34.500 € (44.500 − 10.000 Schonvermögen) deckt den Restbedarf 123,00 € weit über → KEIN_ANSPRUCH-Abwehr, keine Zahlung, Vermögen und Budget unverändert; Referenzblock dokumentiert 1.023 − 900 − 34.500 = −34.377 |
| 3 | ECHT-SCHONVERMOEGEN-800 | Lebenshilfe-Beispiel „A hat 800 Euro auf dem Konto" (https://www.lebenshilfe.de/…/grundsicherung-im-alter-und-bei-erwerbsminderung) | Zahlbetrag 1.023,00 €; Einsatz 0,00 €; Vermögen bleibt 800,00 € |
| 4 | ECHT-LSG-KFZ-PAAR | LSG-Urteil via Lebenshilfe (Ehepaar, zwei Kfz 31.000 €, kein Anspruch nach alter Vermögensgrenze) | Verwertbar 11.000 € (31.000 − 2×10.000) deckt Bedarf 1.542 € weit über → KEIN_ANSPRUCH-Abwehr wie im Urteil (im Ergebnis gleich, Begründung des Modellrechts 2026 dokumentiert); keine Zahlung, Vermögen unverändert |
| 5 | ECHT-KDU-DECKEL | KdU-Richtlinie Altenburger Land, Abschn. 3.3.3 (Nettokaltmiete-Obergrenze ab 01.07.2026 = 1,5× Richtwert, 1 Person 390 €) + 3.3.4/3.3.5 (90/110 €) | KdU 700 € → gekappt auf 590 €; Zahlbetrag 1.153,00 € (Monat 2026-07) |

## Kategorie RECHNER — extern abgeglichene Fälle

Alle 12 Konstellationen wurden am **27.10.2026** interaktiv über den
SGB-XII-Rechner **steuerschroeder.de** (Rechtsstand 03.07.2026) gerechnet:
https://www.steuerschroeder.de/Steuerrechner/Grundsicherung.html
Der je Fall angezeigte „Voraussichtliche monatliche Leistung"-Betrag wurde als
Erwartungswert übernommen. Betriebskosten bewusst 0,00 € (der Rechner fragt
sie nicht ab), zentrales Warmwasser, kein Vermögen.

| # | Haushalt | Miete/Heizung | Einkommen | Rechner-Ergebnis |
|---|----------|----------------|-----------|------------------|
| 1 | Single (563) | 345 / 70 | — | **978,00 €** |
| 2 | Single | 345 / 70 | Rente 500 | **478,00 €** |
| 3 | Single | 260 / 110 (Altenburg-Richtwerte) | Rente 350 | **583,00 €** |
| 4 | Single | 390 / 110 (Altenburg-Obergrenzen) | — | **1.063,00 €** |
| 5 | Single, Merkzeichen G (95,71) | 345 / 70 | — | **1.073,71 €** |
| 6 | Single, G | 320 / 90 | Rente 800 | **268,71 €** |
| 7 | Single | 345 / 70 | Erwerb 600 (→ 420 anrechenbar) | **558,00 €** |
| 8 | Single | 345 / 70 | Erwerb 300 (→ 210 anrechenbar) | **768,00 €** |
| 9 | Paar (2×506) | 345 / 70 | — | **1.427,00 €** |
| 10 | Paar | 345 / 70 | Rente 700 | **727,00 €** |
| 11 | Paar | 345 / 70 | Rente 1.200 | **227,00 €** |
| 12 | Paar | 345 / 70 | Erwerb 500 (→ 350 anrechenbar) | **1.077,00 €** |

Alle zwölf Rechner-Ergebnisse stimmten bei der Erhebung exakt mit der
Referenzrechnung des Produkts (Leistungsberechnung 2026) überein; der Lauf
sichert diese Übereinstimmung dauerhaft per SQL ab.

**Screenshot-Beleg:** `rechner-steuerschroeder-fall12-2026-10-27.png`
(neben dieser Datei) — Ergebnisansicht des Rechners für Fall 12.

## Grenzen

- Der externe Rechner ist eine vereinfachte Überschlagsrechnung des Anbieters
  (kein Bescheid-Ersatz); seine Freibeträge (§ 82a Grundrentenzeiten) und
  weitere Sonderregeln wurden bewusst nicht in die Fälle aufgenommen, weil das
  Produktmodell sie nicht abbildet.
- Echte Einzelfälle mit echten Bescheid-Ergebnissen sind wegen des
  Sozialdatenschutzes (§ 67 ff. SGB X) nicht öffentlich; die ECHT-Fälle sind
  die belegbaren Konstellationen aus Gesetz, Rechtsprechung, Richtlinie und
  Verbandsbeispielen (Hintergrund: doc/testdaten-echte-faelle-recherche-2026-10-27.md).
