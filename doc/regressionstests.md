# Szenario-Regression: Testfälle (SDN.Leistung LMG)

*Stand: 2026-10-11. Umsetzung: `src/test/java/de/allgemein/lmg/db/SzenarioRegressionTest.java`*

## Ziel und Verifikationsprinzip

Die Szenario-Regression spielt zehn Nutzer-Szenarien nach, wie sie Sachbearbeiter
über die Oberfläche auslösen würden: verschiedene Sachbearbeiter erfassen
unterschiedliche Bedarfsgemeinschaften und SozialFälle, ändern Daten, erteilen
Bescheide, ordnen Auszahlungen an, geben frei, stornieren und historisieren.

Verifiziert wird am Ende **jedes** Szenarios der tatsächlich persistierte Zustand
per SQL direkt auf den Oracle-Tabellen (`BEDARFSGEMEINSCHAFT`, `SOZIAL_FALL`,
`ZAHLUNG`, `BESCHEID`, `JOURNAL`) — nicht über den Service zurückgelesen. Damit
deckt die Suite die volle Kette Service → DAO → Transaktion → Datenbank ab und
fängt Regressionen in der Persistierung (Spalten, Statuswerte, Sachbearbeiter-
Zuordnungen, Beträge, Journal-Chronologie), nicht nur in der Fachlogik.

Erwartete Zahlbeträge sind nach der Referenzberechnung 2026 handgerechnet
(Regelbedarfsstufen 563/506/451/471/390/357 EUR; Mehrbedarfe § 30 SGB XII;
Erwerbsfreibetrag § 82 Abs. 3; Schonvermögen § 90 Abs. 2 Nr. 9; KdU-
Angemessenheitsprüfung im Service-Standard deaktiviert). Betragsvergleiche
erfolgen scale-unabhängig (`compareTo`), weil Oracle `NUMBER(10,2)` Werte ohne
Nachkomma-Nullen zurückliefert (1053 statt 1053.00).

## Ausführung

Läuft nur mit gesetzter `TX_URL` (wie `EchteDbVerifikationTest`); der normale
`mvn test` bleibt ohne Datenbank grün und überspringt die Suite:

```bash
TX_URL=jdbc:oracle:thin:@//localhost:1921/o19c1p1 \
TX_USER=tx_demo TX_PASS=tx_demo \
mvn test -Dtest=SzenarioRegressionTest
```

Datenhygiene: alle Szenarien legen Aktenzeichen mit Präfix `BG-REG-` an;
`@BeforeAll`/`@AfterAll` löschen diese Vorgänge vollständig (Kindtabellen
zuerst). Die Szenarien sind unabhängig voneinander und dürfen in beliebiger
Reihenfolge laufen; der Seed-Bestand bleibt unberührt.

## Übersicht der Testfälle

| Nr | Szenario | Beteiligte Sachbearbeiter | Verifizierte Regeln (Auszug) |
|----|----------|---------------------------|------------------------------|
| 1 | Standardrente, Vier-Augen-Kette | sb-mueller (Erfasser), sb-kowalski (Freigabe) | Zahlbetrag RBS 1 + KdU; Selbstfreigabe abgewehrt; SACHBEARBEITER_ID ≠ FREIGEGEBEN_VON; Budgetminderung |
| 2 | Zwei unabhängige Vorgänge parallel | sb-kowalski, sb-yilmaz | Paar-Berechnung 2 × RBS 2; RBS 4; getrennte Budgets/Journale; FALL_ID nur bei Einpersonen-Vorgang |
| 3 | Schwangere mit Merkzeichen G und Warmwasser | sb-berger | § 30 Abs. 1/2/7 (17 % + 17 % + 2,3 %); Merkmals-Flags persistiert |
| 4 | Alleinerziehende mit zwei Kindern | sb-yilmaz | § 30 Abs. 3 Nr. 1 (36 % RBS 1); Kinder als eigene Fälle RBS 6; Wiedervorlage-Datum |
| 5 | Erwerbstätiger mit Absetzbeträgen | sb-schneider | § 82 Abs. 3 Freibetragskappung (281,50); § 82 Abs. 2 Absetzbeträge nach Freibetrag |
| 6 | Vermögen über Schonvermögen | sb-berger | § 90 Abs. 2 Nr. 9: 10.000 EUR Schonvermögen, Rest angerechnet; VERMOEGEN-Spalte |
| 7 | Doppelklick / Netzwerk-Wiederholung | sb-berger | Idempotenz über VORGANGS_NR: 1 Zahlung, 1 Journaleintrag, 1× Budgetminderung |
| 8 | Storno und korrigierte Neuauszahlung im selben Monat | sb-yilmaz (Anordnung/Storno), sb-berger (Freigabe) | Monatsregel; Budget-Rückbuchung; Fall-Version (optimistisches Locking) |
| 9 | Bescheid durch Prüferin, Auszahlung durch Erfasserin | sb-schneider (Erfassung/Auszahlung), sb-perez (Bescheid) | § 44-Bescheid mit Berechnungs-Snapshot (CLOB); ERSTELLT_VON-Trennung; SEPA-Empfänger-Fallback |
| 10 | Kompletter Lebenszyklus | sb-berger, sb-kowalski, sb-perez, sb-kasse | Konkurrenz-Abwehr; Kassenlauf UEBERGEBEN; Historisierung; Journal-Chronologie |

## Die Testfälle im Detail

### Fall 1 — Standardrente mit Vier-Augen-Kette

**Akteure.** Müller erfasst; Müller ordnet an; Müller versucht (irrtümlich) die
Selbstfreigabe; Kowalski gibt frei.

**Ablauf.** Anlage Vorgang (Budget 1.200,00) und SozialFall: RBS 1, KdU aus
Kaltmiete 350,00 + Betriebskosten 60,00 + Heizung 80,00 = 490,00. Auszahlung
für 2026-10 anordnen; Freigabe-Versuch durch den Erfasser selbst muss als
Fachfehler `BERECHTIGUNG` abgewehrt werden; Freigabe durch Kowalski.

**Erwarteter DB-Endzustand.**

| Tabelle | Nachweis |
|---------|----------|
| `ZAHLUNG` | genau eine Zeile: Status `BEREIT`, Betrag **1.053,00** (563,00 + 490,00), `SACHBEARBEITER_ID = sb-mueller`, `FREIGEGEBEN_VON = sb-kowalski` |
| `BEDARFSGEMEINSCHAFT` | `BUDGET_REST = 147,00` (1.200,00 − 1.053,00) |
| `JOURNAL` | mind. ein Eintrag von sb-kowalski (Freigabe revisionssicher zugeordnet) |

### Fall 2 — Zwei Sachbearbeiter, zwei unabhängige Vorgänge

**Akteure.** Kowalski erfasst das Ehepaar (Vorgang A); Yilmaz zeitversetzt
einen alleinstehenden Leistungsberechtigten (Vorgang B); beide ordnen je eine
Auszahlung an.

**Ablauf.** Vorgang A: zwei SozialFälle RBS 2 (Ehepaar), KdU 650,00 am ersten
Fall, Budget 2.000,00. Vorgang B: ein SozialFall RBS 4 ohne KdU, Budget 500,00.
Auszahlung je Vorgang für 2026-10.

**Erwarteter DB-Endzustand.**

| Tabelle | Nachweis |
|---------|----------|
| `ZAHLUNG` | je Vorgang genau eine Zeile; A: **1.662,00** (506 + 506 + 650), B: **471,00** |
| `ZAHLUNG.FALL_ID` | Vorgang A: `NULL` (kein eindeutiger Fall bei zwei Personen); Vorgang B: gesetzt |
| `BEDARFSGEMEINSCHAFT` | Restbudgets getrennt: A 338,00, B 29,00 |
| `JOURNAL` | je Vorgang nur Aktionen des eigenen Bearbeiters (Kowalski ≥ 3 in A, Yilmaz ≥ 3 in B, Yilmaz 0 in A) |

### Fall 3 — Schwangere mit Merkzeichen G und dezentralem Warmwasser

**Akteure.** Berger erfasst und ordnet an.

**Ablauf.** SozialFall RBS 1 mit Merkzeichen G (§ 30 Abs. 1), Schwangerschaft
ab der 13. SSW (§ 30 Abs. 2) und dezentraler Warmwassererzeugung (§ 30 Abs. 7),
ohne KdU, Budget 800,00; Auszahlung 2026-10.

**Erwarteter DB-Endzustand.**

| Tabelle | Nachweis |
|---------|----------|
| `SOZIAL_FALL` | `FLAG_MERKZEICHEN_G = 1`, `FLAG_SCHWANGER = 1`, `FLAG_WARMWASSER = 1` |
| `ZAHLUNG` | Betrag **767,37** = 563,00 + 95,71 (G) + 95,71 (Schwangerschaft) + 12,95 (Warmwasser 2,3 % von RBS 1); Status `ANGELEGT` |
| `BEDARFSGEMEINSCHAFT` | `BUDGET_REST = 32,63` |

### Fall 4 — Alleinerziehende mit zwei Kindern

**Akteure.** Yilmaz erfasst die Familie und ordnet an.

**Ablauf.** Mutter RBS 1 mit zwei Kindern unter 16 im Haushalt und KdU 710,00
(Miete 500,00 + Betriebskosten 90,00 + Heizung 120,00); beide Kinder als
eigene SozialFälle RBS 6 ohne KdU; Wiedervorlage 15.01.2027; Budget 2.500,00;
Auszahlung 2026-10.

**Erwarteter DB-Endzustand.**

| Tabelle | Nachweis |
|---------|----------|
| `SOZIAL_FALL` | drei Zeilen; Mutter: RBS 1, `ANZ_KINDER_U16 = 2`, `ANZ_KINDER_MINDERJ = 2`; Kinder: 2 × RBS 6 |
| `BEDARFSGEMEINSCHAFT` | `WIEDERVORLAGE_AM = 15.01.2027` |
| `ZAHLUNG` | Betrag **2.189,68** = 563,00 + 202,68 (§ 30 Abs. 3 Nr. 1: 36 % der RBS 1 bei 2–3 Kindern unter 16) + 710,00 + 2 × 357,00; `FALL_ID = NULL` (drei Personen) |
| `BEDARFSGEMEINSCHAFT` | `BUDGET_REST = 310,32` |

### Fall 5 — Erwerbstätiger mit Freibetrag und Absetzbeträgen

**Akteure.** Schneider erfasst und ordnet an.

**Ablauf.** SozialFall RBS 1, KdU 560,00 (400/70/90), Brutto-Erwerbseinkommen
1.200,00, Steuern 150,00, Sozialversicherung 200,00; Budget 700,00.

**Erwarteter DB-Endzustand.**

| Tabelle | Nachweis |
|---------|----------|
| `SOZIAL_FALL` | `EINKOMMEN_ERWERB = 1200,00`, `ABZ_STEUERN = 150,00`, `ABZ_SV = 200,00` |
| `ZAHLUNG` | Betrag **554,50**: Bedarf 1.123,00 − anrechenbar 568,50; Freibetrag 360,00 auf 281,50 (50 % RBS 1) gekappt, Absetzbeträge 350,00 erst danach (§ 82 Abs. 2/3) |
| `BEDARFSGEMEINSCHAFT` | `BUDGET_REST = 145,50` |

### Fall 6 — Vermögen über dem Schonvermögen

**Akteure.** Berger erfasst und ordnet an.

**Ablauf.** Vorgang mit Vermögen 10.500,00 (Einzelperson, RBS 1, KdU 300,00),
Budget 500,00; Auszahlung 2026-10.

**Erwarteter DB-Endzustand.**

| Tabelle | Nachweis |
|---------|----------|
| `BEDARFSGEMEINSCHAFT` | `VERMOEGEN = 10000,00` — Verbrauchsfortschreibung (§ 90): der Einsatz senkt den Bestand bis zum Schonvermögen; ab dem Folgemonat wird nichts mehr abgezogen |
| `ZAHLUNG` | Betrag **363,00** = 863,00 Bedarf − 500,00 verwertbares Vermögen (10.500,00 − 10.000,00 Schonvermögen § 90 Abs. 2 Nr. 9 i.V.m. § 1 BarbetragsVO); `VERMOEGENSEINSATZ = 500,00` dokumentiert |
| `BEDARFSGEMEINSCHAFT` | `BUDGET_REST = 137,00` |

### Fall 7 — Doppelklick / Netzwerk-Wiederholung (Idempotenz)

**Akteure.** Berger ordnet an; derselbe Request wiederholt sich (Doppelklick
oder Netzwerk-Wiederholung) mit identischer Vorgangsnummer.

**Ablauf.** SozialFall RBS 1 ohne KdU, Budget 700,00. Zwei Aufrufe von
`ordneAuszahlungAn` mit derselben `VORGANGS_NR`; der zweite muss als
`bereitsGebucht` erkannt und dieselbe Zahlungs-ID zurückgegeben werden.

**Erwarteter DB-Endzustand.**

| Tabelle | Nachweis |
|---------|----------|
| `ZAHLUNG` | `COUNT(*) = 1` für die VORGANGS_NR (keine Doppelbuchung) |
| `JOURNAL` | genau ein `Auszahlung über …`-Eintrag |
| `BEDARFSGEMEINSCHAFT` | `BUDGET_REST = 137,00` (563,00 nur einmal abgezogen) |

### Fall 8 — Storno und korrigierte Neuauszahlung im selben Monat

**Akteure.** Yilmaz ordnet an und storniert; Berger gibt frei; Yilmaz korrigiert
den Fall und ordnet neu an.

**Ablauf.** SozialFall RBS 1, Miete 200,00 (Bedarf 763,00), Budget 1.000,00.
Auszahlung 2026-09 → Freigabe → Storno. Fachliche Korrektur: Miete auf 250,00
(Version 1 → 2). Erneute Auszahlung für denselben Monat 2026-09 muss zulässig
sein (Storno gibt den Monat frei).

**Erwarteter DB-Endzustand.**

| Tabelle | Nachweis |
|---------|----------|
| `ZAHLUNG` | zwei Zeilen: alte `STORNIERT` mit 763,00; neue `ANGELEGT` mit **813,00** (563,00 + 250,00) |
| `BEDARFSGEMEINSCHAFT` | `BUDGET_REST = 187,00` (Storno bucht 763,00 zurück, Neuauszahlung mindert 813,00) |
| `SOZIAL_FALL` | `VERSION = 2` (Korrektur über optimistisches Locking) |

### Fall 9 — Bescheid durch die Prüferin, Auszahlung durch die Erfasserin

**Akteure.** Schneider erfasst Vorgang, Fall und Bankverbindung; Perez erteilt
den Bewilligungsbescheid; Schneider ordnet die Auszahlung an.

**Ablauf.** Vorgang mit SEPA-Bankverbindung (Kontoinhaber weicht von der
Hauptperson ab: „Wolke, Wilma" statt „Wolke, Walter"); SozialFall RBS 1,
KdU 620,00 (450/90/80), Budget 1.500,00. Bescheid für 2026-01 bis 2026-12
(zwölf Monate, § 44 Abs. 3); Auszahlung 2026-10 ohne eigene Empfängerangaben
(SEPA-Daten müssen aus dem Vorgang kommen).

**Erwarteter DB-Endzustand.**

| Tabelle | Nachweis |
|---------|----------|
| `BESCHEID` | `ZAHLBETRAG = 1183,00`; `STATUS = ERTEILT`; `ERSTELLT_VON = sb-perez`; `BESCHEID_NR` enthält das Aktenzeichen; `BERECHNUNG`-CLOB gefüllt und JSON (`{` …) |
| `ZAHLUNG` | Betrag **1.183,00**; `SACHBEARBEITER_ID = sb-schneider`; `EMPFAENGER_IBAN`/`EMPFAENGER_NAME` aus dem Vorgang übernommen (Name = Kontoinhaber) |
| `JOURNAL` | genau ein `Bewilligungsbescheid …`-Eintrag, Perez zugeordnet |

### Fall 10 — Kompletter Lebenszyklus eines Vorgangs

**Akteure.** Berger (Erfassung, veralteter Speicherversuch, Freigabe),
Kowalski (Fallkorrektur, Auszahlung), Perez (Bescheid), sb-kasse (Kassenlauf),
Historisierung.

**Ablauf.** Anlage RBS 1 mit Miete 100,00, Budget 1.500,00. Kowalski korrigiert
die Miete auf 180,00. Bergers paralleler Speicherversuch mit dem veralteten
Maskenstand (Version 1) muss als `KONKURRENZ` abgewehrt werden, ohne die
Korrektur zu überschreiben. Bescheid 2026-01 bis 2026-12; Auszahlung 2026-10
durch Kowalski; Freigabe durch Berger; Kassenlauf; Historisierung des Vorgangs.

**Erwarteter DB-Endzustand.**

| Tabelle | Nachweis |
|---------|----------|
| `SOZIAL_FALL` | `KDU_MIETE = 180,00` (Kowalskis Korrektur steht), `VERSION = 2` |
| `ZAHLUNG` | Status `UEBERGEBEN` (Kassenlauf), Betrag **743,00** (563,00 + 180,00) |
| `BEDARFSGEMEINSCHAFT` | `BUDGET_REST = 757,00`; `STATUS_AKTIV = 0` (historisiert, nicht gelöscht) |
| `BESCHEID` | `STATUS = ERTEILT` |
| `JOURNAL` | Chronologie in korrekter Reihenfolge: Vorgang angelegt → Fall geändert (Bedarfs-/Einkommensermittlung) → Bewilligungsbescheid → Auszahlung → freigegeben → Status/Wiedervorlage; Kassenlauf-Eintrag vorgangsübergreifend (`BG_ID` leer), aber dem Kassenläufer zugeordnet |

## Abgrenzung und Pflege

- Die Suite ergänzt `EchteDbVerifikationTest` (der verifikationsorientiert einzelne
  Mechanismen prüft: Budget-erschöpft, Monatsprüfung, Saga-Recover usw.) um die
  nutzerbezogene End-to-End-Sicht mit mehreren Sachbearbeitern pro Fall.
- Neue Fachregeln, die einen sichtbaren DB-Endzustand erzeugen, sollten als
  weiteres Szenario ergänzt werden (Muster: Akteure → Ablauf → erwarteter
  Endzustand; Aktenzeichen-Präfix `BG-REG-` beibehalten).
- Bei Umbauten an `ddl/lmg_schema.sql` (Spalten, Constraints) müssen die
  SQL-Nachweise dieser Datei mitangepasst werden.
