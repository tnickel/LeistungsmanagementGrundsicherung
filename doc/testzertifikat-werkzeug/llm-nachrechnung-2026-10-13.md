# LLM-Nachrechnung der testsweet-Testdokumentation

- **Geprüft von:** GLM (ZCode-Agent) als unabhängiger Prüfrechner
- **Datum:** 2026-10-13
- **Grundlage:** `testdokumentation.md` der Läufe `run-20261013-112039` (POSITIV, 500/500
  verifiziert) und `run-20261013-111840` (NEGATIV, 500/500 verifiziert)
- **Methode:** Rechnung ausschließlich nach den Gesetzestexten (Anlage zu § 28 SGB XII;
  §§ 30, 42a, 82, 90 SGB XII i. V. m. § 1 BarbetragsVO) — nicht nach dem Java-Code.
  Arithmetik mit exakter Dezimalrechnung, Rundung HALF_UP auf Cent je Zuschlag.
- **Stichprobe:** 7 Fälle (6 Rechenblöcke Positiv + 2 Abwehrfälle Negativ), so ausgewählt,
  dass jeder Regel-Ast einmal trifft: Alleinerziehend 36 %, Merkzeichen G am Kind (RBS 6),
  Schwangerschaft (bleibend), Schwangerschaft mit Entbindungsmonat vor dem Zahlungsmonat
  (Mehrbedarf entfällt), Warmwasser-Zuschläge Stufen 2/4, Erwerbsfreibetrag unter/über der
  Kappung, Absetzbeträge über Erwerb (anrechenbar 0), Vermögensverbrauch in drei
  Schonvermögen-Konstellationen, KdU-Split beim Paar, Storno + Mietekorrektur + Neu-Anordnung
  (zwei Blöcke), Zahlung außerhalb des Bewilligungszeitraums, veralteter Maskenstand.
- **Ergebnis: 7/7 Fälle vollständig bestätigt — 0 Abweichungen** (56 Zwischenwerte auf
  Personenebene, 16 Werte auf Bedarfsgemeinschaftsebene, 24 SQL-Erwartungen als fachlich
  richtig bestätigt).

## Rechenregeln (wie angewendet)

```
Regelbedarf            = Stufe 1–6: 563,00 / 506,00 / 451,00 / 471,00 / 390,00 / 357,00 EUR
Merkzeichen G          = 17 % des eigenen Regelbedarfs (§ 30 Abs. 1)
Schwangerschaft        = 17 %, ab 13. SSW; entfällt, wenn Zahlungsmonat > Entbindungsmonat
Alleinerziehend        = 36 % der Stufe 1 bei Kind < 7 J. oder 2–3 Kindern < 16 J.,
                         sonst 12 % je minderjährigem Kind; Deckel 60 % der Stufe 1 = 337,80
Deckel § 30 Abs. 6     = Summe der Zuschläge nach Abs. 1–5 höchstens der eigene Regelbedarf
Warmwasser dezentral   = 2,3 / 2,3 / – / 1,4 / 1,2 / 0,8 % je Stufe (§ 30 Abs. 7, außerhalb Deckel)
KdU                    = Kaltmiete + kalte Betriebskosten + Heizung (§ 42a)
Erwerbsfreibetrag      = min(30 % Brutto-Erwerb, 50 % der Stufe 1 = 281,50) (§ 82 Abs. 3)
Anrechenbares Einkommen= max(0, Rente + Erwerb − Freibetrag − Absetzbeträge) (§ 82 Abs. 2)
Schonvermögen          = 10.000 je Person Stufe 1–3 + 500 je Person Stufe 4–6 (§ 90 Abs. 2 Nr. 9)
Zahlbetrag             = Gesamtbedarf − anrechenbares Einkommen − max(Vermögen − Schonvermögen, 0)
                         (≤ 0 ⇒ KEIN_ANSPRUCH, § 43 Abs. 1); Zahlung nur in bescheidgedeckten
                         Monaten, sobald ein Bescheid existiert (§ 44 Abs. 3)
```

## Fall für Fall

### TF 11 — Single, schwanger (Entbindung 2026-06 nach Zahlungsmonat 2026-03), Storno + Neu
| Größe | selbst gerechnet | MD | ✓ |
|---|---|---|---|
| Regelbedarf | 563,00 | 563,00 | ✓ |
| Mehrbedarf Schwangerschaft 17 % | 95,71 | 95,71 | ✓ |
| KdU Block 1 / Block 2 (Miete 230→330) | 420,00 / 520,00 | 420,00 / 520,00 | ✓ |
| Gesamtbedarf | 1.078,71 / 1.178,71 | 1.078,71 / 1.178,71 | ✓ |
| Anrechenbar (Rente, kein Freibetrag) | 120,00 | 120,00 | ✓ |
| Zahlbetrag | 958,71 / 1.058,71 | 958,71 / 1.058,71 | ✓ |
| Restbudget (1.248,71 −) | 290,00 / 190,00 | 290,00 / 190,00 | ✓ |

### TF 12 — Alleinerziehend, 1 Kind unter 7 (36 %-Satz), Vermögen 11.800
| Größe | selbst gerechnet | MD | ✓ |
|---|---|---|---|
| Mehrbedarf Mutter (36 % × 563) | 202,68 | 202,68 | ✓ |
| Kind (RBS 6, Merkzeichen G 17 %) | 60,69 | 60,69 | ✓ |
| Mutter: anrechenbar (420 Erwerb − 126 Frei − 345 Abz < 0) | 0,00 | 0,00 | ✓ |
| Kind: anrechenbar (290+270−81−250) | 229,00 | 229,00 | ✓ |
| Schonvermögen (1 × 10.000 + 1 × 500) | 10.500,00 | 10.500,00 | ✓ |
| Verwertbar (11.800 − 10.500) | 1.300,00 | 1.300,00 | ✓ |
| Zahlbetrag (1.708,37 − 229 − 1.300) | 179,37 | 179,37 | ✓ |
| Restbudget (529,37 −) | 350,00 | 350,00 | ✓ |

### TF 13 — Paar mit 1 Kind, Warmwasser Stufe 2, Vermögen 20.950
| Größe | selbst gerechnet | MD | ✓ |
|---|---|---|---|
| Warmwasser 2,3 % × 506 (je Partner) | 11,64 | 11,64 | ✓ |
| Gesamtbedarf (1.182,64 + 517,64 + 390,00) | 2.090,28 | 2.090,28 | ✓ |
| Anrechenbar | 1.510,00 | 1.510,00 | ✓ |
| Schonvermögen (2 × 10.000 + 500) | 20.500,00 | 20.500,00 | ✓ |
| Zahlbetrag (2.090,28 − 1.510 − 450) | 130,28 | 130,28 | ✓ |
| Restbudget (270,28 −) | 140,00 | 140,00 | ✓ |

### TF 16 — Paar, KdU-Split, Entbindungsmonat 2026-08 vor Zahlungsmonat 2026-11
| Größe | selbst gerechnet | MD | ✓ |
|---|---|---|---|
| Schwangerschafts-Mehrbedarf im Zahlungsmonat | 0,00 (entfallen) | 0,00 | ✓ |
| Freibeträge (460 → 138,00; 970 → gekappt 281,50) | 138,00 / 281,50 | 138,00 / 281,50 | ✓ |
| Anrechenbar gesamt | 1.040,50 | 1.040,50 | ✓ |
| Schonvermögen (2 Erwachsene) / verwertbar | 20.000,00 / 150,00 | 20.000,00 / 150,00 | ✓ |
| Zahlbetrag (1.777,00 − 1.040,50 − 150) | 586,50 | 586,50 | ✓ |
| Restbudget (666,50 −) | 80,00 | 80,00 | ✓ |

### TF 19 — Alleinerziehend mit 3 Kindern (u16 = 3 → 36 %-Satz), Kind mit Erwerb und Warmwasser
| Größe | selbst gerechnet | MD | ✓ |
|---|---|---|---|
| Mehrbedarf Mutter (36 %) | 202,68 | 202,68 | ✓ |
| Warmwasser Kind RBS 4 (1,4 % × 471) | 6,59 | 6,59 | ✓ |
| Kind RBS 6 mit Erwerb: anrechenbar | 0,00 | 0,00 | ✓ |
| Gesamtbedarf (1.440,68 + 357 + 477,59 + 357) | 2.632,27 | 2.632,27 | ✓ |
| Schonvermögen (10.000 + 3 × 500) | 11.500,00 | 11.500,00 | ✓ |
| Zahlbetrag (2.632,27 − 520) | 2.112,27 | 2.112,27 | ✓ |
| Restbudget (2.322,27 −) | 210,00 | 210,00 | ✓ |

### TF 01 (Negativ) — Rente 5.000 über Bedarf → KEIN_ANSPRUCH
Bedarf selbst gerechnet: 451,00 + 270 + 65 + 100 = **886,00**; Rente voll anrechenbar
(kein Erwerbsfreibetrag für Renten) → Zahlbetrag 886,00 − 5.000,00 = **−4.114,00 ≤ 0**
⇒ Abweisung mit KEIN_ANSPRUCH (§ 43 Abs. 1) fachlich zwingend. DB-Endzustand bestätigt:
0 Zahlungen, Budget unverändert (1.000,00). ✓

### TF 20 (Negativ) — Zahlung außerhalb des Bescheids + veralteter Maskenstand
Bescheid gültig 2026-01..2026-06; Anordnung für **2026-07** liegt außerhalb ⇒ VALIDATION
ist zwingend (Zahlung nur in bescheidgedeckten Monaten, sobald ein Bescheid existiert).
Veralteter Maskenstand (Version 1 nach Speichern von Version 2) ⇒ KONKURRENZ zwingend
(optimistisches Locking). DB-Endzustand bestätigt: 0 Zahlungen, Budget 2.000,00 unverändert,
genau 1 Bescheid, Miete = Neuwert, Fall-Version 2. ✓

## Grenzen der Prüfung

- Stichprobe, keine Vollprüfung aller 1.000 Fälle (Vollprüfung jederzeit möglich: die MD
  enthält je Fall alle Eingaben und Zwischenwerte; jede dritte KI kann denselben Gang gehen).
- Die Web-Modus-Läufe (Modus 2) wurden zu früheren Zeitpunkten verifiziert (Referenzläufe
  2026-10-11/12); heute nicht erneut gefahren.
- Arithmetik maschinell exakt (Decimal), Herleitung der Schritte durch den Prüfrechner.
