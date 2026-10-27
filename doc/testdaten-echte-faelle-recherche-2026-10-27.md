# Recherche: Echte Testfälle (Sozialfälle mit Ergebnis) aus öffentlichen Quellen

**Datum:** 2026-10-27 · **Anlass:** Wunsch, die testsweet um bis zu 100 „wahre" Testfälle
mit echten Berechnungsergebnissen zu erweitern (neue Kategorie hinter den Negativfällen),
statt ausschließlich selbst generierte (synthetische) Fälle zu verwenden.

## Ergebnis in einem Satz

**Öffentlich verfügbare echte Sozialfälle mit vollständiger Bedarfsberechnung und
Endergebnis existieren nicht — weder 100, noch 10 — und können wegen des
Sozialdatenschutzes (§ 67 ff. SGB X, § 35 SGB I, Art. 32 DSGVO) auch nicht
veröffentlicht werden.**

## Was gesucht und geprüft wurde

1. **Open-Data-/Falldatenbanken** (GovData, kommunale Portale, Forschungsdaten):
   kein Datensatz mit (anonymisierten) Leistungsbescheiden auffindbar. Bestätigt
   durch breite Suche; Sozialdaten unterliegen der Vertraulichkeit und werden —
   anders z. B. als schwedische Verwaltungsdaten — nicht als Open Data publiziert.
2. **Gerichtsentscheidungen** (beste echte Quelle, echte Beträge):
   - BSG, Urteil vom 27.05.2026, B 8 SO 4/24 R (Grundsicherung im Alter,
     Vermögensübergangsregelung): enthält als einzige Fallzahl ein Vermögen von
     rund 44.500 €; Einkommen, Rente, KdU, Heizung, Regelbedarf und Bewilligungsbetrag
     fehlen vollständig. Eine Nachrechnung ist unmöglich.
   - Tacheles-Rechtsprechungsticker (zentrale Sammelstelle, KW 10/2026): ~13
     Entscheidungen/Woche, davon ~1 zu SGB XII; Zusammenfassungen ohne vollständige
     Euro-Rechnungen (Leitsätze/Rechtsfragen).
   - LSG Baden-Württemberg, L 2 SO 80/26 (25.03.2026): Grundsicherung im Alter;
     keine vollständige Berechnung.
   - **Systematik:** Urteile klären Teilaspekte (Vermögensgrenze, Angemessenheit,
     Verfahrensfehler), nie die komplette Bedarfsberechnung. Bewilligungszeiträume
     in 2026er Urteilen sind typischerweise 2022/2023 → damalige (niedrigere) Sätze.
3. **Amtliche Merkblätter/Ratgeber** (DRV, Lebenshilfe, kommunale Ratgeber,
   Anwaltsportale): liefern Bausteine (Regelbedarfsstufen 2026, Schonvermögen,
   Absetzbeträge) und Teilbeispiele („A hat 800 € auf dem Konto"), aber keine
   vollständig durchgerechneten Fälle; teils veraltete Beträge.
4. **Externe Online-Rechner** (VdK, Stadt Düsseldorf, steuerschroeder.de,
   smart-rechner.de): rechnen Nutzereingaben, enthalten aber keine Fallmengen mit
   Ergebnissen; überschlägig; decken eigene Merkmale (Schwangerschaftsmehrbedarf,
    Vermögensfortschreibung, Kinderstaffelung, Entbindungsmonat) nicht ab.

## Bewertung

- Das Kriterium „von 2026" scheitert zusätzlich an der Natur der Quellen: Selbst
  ein 2026er Urteil rechnet Bescheide aus 2022/2023 mit damals geltenden Sätzen nach.
- Der berechtigte Kern der Idee — den Erwartungswert-Zirkelschluss („selbst
  ausgedacht, selbst ausgerechnet") zu brechen — wird im Projekt bereits durch
  unabhängige Erwartungswerte adressiert: handgerechnete GoldWerte
  (doc/regressionstests.md) und die GLM-Nachrechnung als externe Prüfinstanz.

## Realistische Alternativen (Vorschläge, noch nicht umgesetzt)

| Option | Inhalt | Gewinn | Aufwand |
|---|---|---|---|
| A | Kategorie „ECHT" mit den 3–10 belegbaren Teilfällen aus Urteilen (z. B. Vermögensfälle mit Aktenzeichen je Fall) | wenige, praxisnahe Grenzfälle | mittel (Generator/GUI/Simulator) |
| B | Externe Online-Rechner als weitere Gegeninstanz für einfach strukturierte Fälle (Alleinstehend/Paar + Rente + Miete) | Unabhängigkeit ohne echte Fälle | mittel, manuell |
| C | Echte Bausteine statt ausgedachter Eingaben: veröffentlichte kommunale KdU-Angemessenheitsrichtlinien und Mietspiegel (2026) sowie Destatis-Einkommensverteilungen als Generatorgrundlage | realitätsnahe Eingangsdaten, belastbar dokumentiert | gering–mittel |

## Quellen (Abruf 2026-10-27)

- BSG-Entscheidungsdatenbank: https://www.bsg.bund.de (B 8 SO 4/24 R, B 8 SO 5/24 R — 27.05.2026)
- Kommentierung: https://sozialrat.org/bsg-urteil-grundsicherung-alter-27-mai-2026-vermoegensuebergangsregel
- Tacheles-Rechtsprechungsticker: https://www.tacheles-sozialhilfe.de/newsticker/tacheles-rechtsprechungsticker-kw-10-2026.html
- LSG BW L 2 SO 80/26: https://www.landesrecht-bw.de/bsbw/document/NJRE001640760
- Regelbedarfe 2026 (Nullrunde): https://www.bundesregierung.de/breg-de/aktuelles/nullrunde-buergergeld-2383676
- Anlage Regelbedarfsstufen: https://www.gesetze-im-internet.de/sgb_12/anlage.html
- VdK-Grundsicherungsrechner: https://www.vdk.de/grundsicherungsrechner-anspruch-ermitteln
- Rechner Stadt Düsseldorf: https://www.duesseldorf.de/soziales/grundsicherung/online-rechner
- Beispielhafte Ratgeber mit Bausteinen: https://www.lebenshilfe.de/informieren/arbeiten/grundsicherung-im-alter-und-bei-erwerbsminderung
