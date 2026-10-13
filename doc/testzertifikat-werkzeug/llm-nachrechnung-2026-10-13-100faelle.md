# LLM-Nachrechnung — 100 Testfälle einzeln (GLM-5.3)

pruefumfang = 100 Fälle einzeln nachgerechnet (TF 11–112, Kombinationsfälle des Positiv-Laufs; inkl. 8 Storno-Korrektur-Fällen mit je 2 Rechenblöcken = 108 Rechenblöcke)
mitgegeben = gesetzesgrundlagen-2026.md (vollständig) · llm-pruefauftrag.txt · pruefmenge-positiv-100faelle.md (= testdokumentation.md des Laufs run-20261013-112039, auf die 100 Fälle gekürzt)
modell = GLM-5.3 (ZCode-Agent)
methode = Rechenkette ausschließlich aus den mitgegebenen Gesetzestexten eigenständig neu umgesetzt (Python/Decimal, Rundung HALF_UP je Zuschlag — Skript: nachrechnen_100.py); zusätzlich 5 Fälle vollständig schriftlich ausgerechnet (unten)
ergebnis = 108/108 Rechenblöcke bestätigt · 0 Abweichungen · 712 Personen-Zwischenwerte + 540 BG-Werte verglichen
datenstand = 2026-10-13 (Lauf run-20261013-112039, POSITIV, 500/500 verifiziert)
abdeckung = je Regel-Ast mehrfach vertreten: Regelbedarfsstufen 1–6 · Merkzeichen G · Schwangerschaft bleibend UND entfallend (Entbindungsmonat vor Zahlungsmonat) · Alleinerziehend 36 %/12 % mit 60-%-Deckel · §-30-Abs.-6-Deckel · Warmwasser Stufen 1/2/4/5/6 · KdU-Split · Erwerbsfreibetrag unter/über Kappung · Absetzbeträge bis anrechenbar 0 · Vermögen über Schonvermögen (inkl. Verbrauchswert) · Storno + Mietekorrektur + Neu-Anordnung (Zweitblock)

## Was wurde geprüft und womit

Die vollständig mitgegebenen Dokumente (siehe Kopfzeilen oben) wurden durchgearbeitet und
die Rechenkette allein daraus neu errichtet — ohne Blick auf den Java-Code des Projekts.
Für **jeden** der 100 Fälle wurden berechnet und mit der Prüfmenge verglichen:

- je Person: Regelbedarf (Anlage § 28), Merkzeichen-G-Zuschlag (§ 30 Abs. 1),
  Schwangerschaftszuschlag mit Entbindungsmonat-Regel (§ 30 Abs. 2),
  Alleinerziehenden-Zuschlag auf Basis der Stufe 1 mit 60-%-Deckel (§ 30 Abs. 3),
  Summen-Deckel (§ 30 Abs. 6), Warmwasser-Zuschlag außerhalb des Deckels (§ 30 Abs. 7),
  Kosten der Unterkunft (§ 42a), Erwerbsfreibetrag mit Kappung auf 281,50 EUR (§ 82 Abs. 3),
  anrechenbares Einkommen (§ 82 Abs. 2), Gesamtbedarf;
- je Bedarfsgemeinschaft: Schonvermögen (§ 90 Abs. 2 Nr. 9 i. V. m. § 1 BarbetragsVO),
  verwertbares Vermögen (§ 43 Abs. 1), Zahlbetrag, erwartetes Restbudget.

Ergebnis des Vergleichslaufs (`python nachrechnen_100.py`):

```
Gepruefte Rechenbloecke: 108 in 100 Faellen
Abweichungen: 0
```

## Schriftlich ausgerechnete Beispielfälle (aus derselben Prüfmenge)

### TF 11 — Single, schwanger (Entbindung 2026-06, Zahlungsmonat 2026-03: Zuschlag bleibt), Storno + Neu
- Regelbedarf Stufe 1 = 563,00; Schwangerschaft 17 % = 95,71; KdU 230+130+60 = 420,00
- Gesamtbedarf 1.078,71; anrechenbar = Rente 120,00 (kein Freibetrag für Renten)
- **Zahlbetrag 958,71**; Budget 1.248,71 − 958,71 = **Rest 290,00** ✓
- Zweitblock nach Mietekorrektur (330): KdU 520 → Gesamtbedarf 1.178,71,
  **Zahlbetrag 1.058,71, Rest 190,00** ✓

### TF 12 — Alleinerziehend, 1 Kind unter 7, Vermögen 11.800
- Mutter: 36 % der Stufe 1 (Kind < 7) = 202,68; KdU 525 → 1.290,68;
  Erwerb 420 − Freibetrag 126 − Absetzungen 345 < 0 ⇒ anrechenbar 0
- Kind (Stufe 6, Merkzeichen G): 357,00 + 60,69; anrechenbar 290+270−81−250 = 229,00
- Schonvermögen 10.000 + 500 = 10.500; verwertbar 1.300
- **Zahlbetrag 1.708,37 − 229 − 1.300 = 179,37; Rest 350,00** ✓

### TF 13 — Paar mit 1 Kind, Warmwasser Stufe 2 (je Partner 2,3 % × 506 = 11,64), Vermögen 20.950
- Bedarf 1.182,64 + 517,64 + 390,00 = 2.090,28; anrechenbar 830 + 470 + 210 = 1.510,00
- Schonvermögen 2 × 10.000 + 500 = 20.500 ⇒ verwertbar 450
- **Zahlbetrag 130,28; Rest 140,00** ✓

### TF 16 — Paar, KdU-Split, Entbindungsmonat 2026-08 vor Zahlungsmonat 2026-11 (Zuschlag entfällt)
- Partner 1: Freibetrag 30 % × 460 = 138,00; Partner 2: 30 % × 970 = 291 → gekappt 281,50
- anrechenbar 452,00 + 588,50 = 1.040,50; Bedarf 1.106,00 + 671,00 = 1.777,00
- Schonvermögen 20.000 ⇒ verwertbar 150 → **Zahlbetrag 586,50; Rest 80,00** ✓

### TF 19 — Alleinerziehend mit 3 Kindern (u16 = 3 ⇒ 36 %-Satz), Kind mit Erwerb/Warmwasser
- Mutter 563 + 202,68 + 675 = 1.440,68; Kind RBS 4 mit Warmwasser 1,4 % = 6,59 → 477,59;
  Kind RBS 6 mit Erwerb: 290+270−81−250 ⇒ anrechenbar 0 → 357,00; viertes Kind 357,00
- Gesamtbedarf 2.632,27; anrechenbar 420 + 0 + 100 + 0 = 520,00; Schonvermögen 11.500
- **Zahlbetrag 2.112,27; Rest 210,00** ✓

## Grenzen

- Die 100 Fälle sind eine vergrößerte, regelast-gestreute Stichprobe aus dem 500er-Lauf —
  nicht alle 500. Die übrigen 400 lassen sich mit derselben Prüfmenge-Methode jederzeit
  nachreichen (Kürzungsskripte liegen im Ordner bzw. sind in nachrechnen_100.py adaptierbar).
- Die Dokumentwerte, gegen die verglichen wurde, sind die Erwartungshaltung des
  Simulator-Laufs; deren Übereinstimmung mit der Datenbank wurde vom Lauf selbst per SQL
  geprüft (500/500 verifiziert). Die vorliegende Nachrechnung prüft die Fachlogik der
  Erwartung — nicht die SQL-Auswertung selbst.
- Arithmetik maschinell exakt (Decimal); Herleitung der Schritte durch den Prüfrechner
  aus den Gesetzestexten. Unabhängige zweite Umsetzung der Rechenkette (Python), kein
  Bezug zum Java-Code.
