> Prüfmenge für die LLM-Nachrechnung (GLM-5.3) am 2026-10-13:
> Grundlage: testdokumentation.md des Laufs testsweet/logs/run-20261013-112039 (POSITIV, 500/500 verifiziert).
> Kürzung: Lauf- und Rechengrundlagen-Kopf vollständig + die 100 Kombinationsfälle TF 11–112
> (TF 50 und TF 100 sind Kassenläufe und daher nicht in der Auswahl; die kuratierten Fälle
> TF 1–10 und die Kombinationsfälle TF 111/113–500 sind NICHT enthalten).
> Mitgegeben wurden außerdem vollständig: gesetzesgrundlagen-2026.md und llm-pruefauftrag.txt
> (beide im Werkzeugordner doc/testzertifikat-werkzeug/).
# testsweet Lauf

lauf_erfolgreich = true
globale_pruefung = Pruefung[beschreibung=Global: Anzahl SWEET-Vorgänge, erwartet=489, ist=489, ok=true, hinweis=null]
globale_pruefung = Pruefung[beschreibung=Global: keine Übergabe ohne Freigabevermerk, erwartet=0, ist=0, ok=true, hinweis=null]
produkt = SDN.Leistung LMG
modus = 1-fachschicht (SozialLeistungsService direkt)
kategorie = POSITIV
start = 2026-10-13T11:20:39.148037400
ende = 2026-10-13T11:20:43.520825400
dauer_s = 4
db = jdbc:oracle:thin:@//localhost:1521/FREEPDB1
db_user = tx_demo
mitarbeiter = 20
auftraege = 500
seed = 42
denkzeit_ms = 0
ergebnis.verifiziert = 500
ergebnis.verifikation_nio = 0
ergebnis.fehlgeschlagen = 0
ergebnis.gesamt = 500
hinweis: Soll-Beträge werden mit der Produktionsberechnung (Leistungsberechnung) ermittelt; unabhängige Goldwerte-Absicherung: GoldWerteTest im Hauptprojekt (Werte aus doc/regressionstests.md).

## pruefanleitung_fur_ki

Diese Datei ist das maschinenlesbare Protokoll eines testsweet-Laufs der Fachanwendung SDN.Leistung (Grundsicherung im Alter/bei Erwerbsminderung, SGB XII). Bitte unabhängig überprüfen:

1. BETRÄGE NACHRECHNEN (Positivfälle): Je Testfall stehen unter '### rechenwege' die Eingaben je Person (p1_eingaben, p2_eingaben, ...) und JEDE Zwischengröße (regelbedarf, mehrbedarfe, kdu, anrechenbar, bg_zahlbetrag, budget_rest_erwartet). Rechne mit den Formeln aus '## rechengrundlagen_2026' jeden Zwischenwert und den Zahlbetrag selbst nach und melde jede Abweichung.
2. ERWARTET/GEGEN IST HALTEN: Unter '### verifikation' steht je Prüfung V1..Vn das prüfende SQL mit konkreten Parametern sowie 'erwartet' und 'ist'. Prüfe, ob die erwarteten Werte fachlich aus den Eingaben folgen und ob ist = erwartet ist (NIO-Zeilen sind Fehler).
3. NEGATIVFÄLLE PLAUSIBILISIEREN: Schritte mit 'ERWARTETER FEHLER (CODE)' müssen fachlich zwingend abgewehrt werden (z. B. KEIN_ANSPRUCH wenn Einkommen/Vermögen den Bedarf übersteigt, VALIDATION bei Doppelanordnung je Monat oder Zahlungsmonat außerhalb des Bewilligungszeitraums, BERECHTIGUNG bei Selbstfreigabe, KONKURRENZ bei veraltetem Maskenstand, BUDGET_ERSCHOEPFT bei zu kleinem Startbudget). Prüfe, dass der Datenbank-Endzustand unverändert geblieben ist (keine Zahlung, Budget unangetastet).
4. GRENZEN: Last-/Stresstests erzeugen nur Aggregate (kein Einzelfall-Nachrechnen); Web-Läufe (modus 2-web) nutzen dieselbe Datenstruktur, dokumentieren aber UI-Schritte. Globale Prüfungen stehen im Kopf als globale_pruefung-Zeilen.
Antworte mit: VERDICT ok|fehlerfund je Testfall (nur Abweichungen im Detail) sowie einer Gesamteinschätzung.

## rechengrundlagen_2026

rbs_eur = {1: 563.00, 2: 506.00, 3: 451.00, 4: 471.00, 5: 390.00, 6: 357.00}  (Anlage zu § 28 SGB XII)
mehrbedarf_g = 0.17 * regelbedarf  (§ 30 Abs. 1)
mehrbedarf_schwanger = 0.17 * regelbedarf  (§ 30 Abs. 2, ab 13. SSW; entfaellt automatisch, wenn entbindungsmonat gesetzt und zahlungsmonat > entbindungsmonat)
mehrbedarf_alleinerziehend = 0.36 * rbs1 bei kind_u7>=1 oder 2<=kinder_u16<=3, sonst 0.12 * rbs1 je minderjaehrigem kind; deckel 0.60 * rbs1 = 337.80  (§ 30 Abs. 3)
mehrbedarf_obergrenze = summe(abs1..5) <= regelbedarf  (§ 30 Abs. 6)
mehrbedarf_warmwasser_anteil = {1: 0.023, 2: 0.023, 3: 0.0, 4: 0.014, 5: 0.012, 6: 0.008}  (§ 30 Abs. 7, zusaetzlich zu Abs. 6)
kdu = kaltmiete + betriebskosten + heizung  (§ 42a; angemessenheitspruefung AUS)
erwerbsfreibetrag = min(0.30 * erwerb_brutto, 0.50 * rbs1 = 281.50)  (§ 82 Abs. 3)
anrechenbar = rente + erwerb - freibetrag - absetzbetaetrag >= 0  (absetzbetraege: steuern, sv, versicherungen, erwerbsaufwendungen; § 82 Abs. 2)
schonvermoegen = 10000.00 je volljaehriger person (rbs 1-3) + 500.00 je weiterer person  (§ 90 Abs. 2 Nr. 9 i.V.m. § 1 BarbetragsVO)
zahlbetrag = gesamtbedarf - anrechenbares_einkommen - max(vermoegen - schonvermoegen, 0)  (<= 0 => KEIN_ANSPRUCH, § 43 Abs. 1)
rundung = HALF_UP auf 2 nachkommastellen; waehrung EUR
zahlstatus = ANGELEGT (wartet auf freigabe) -> BEREIT -> UEBERGEBEN; STORNIERT gibt monat frei

## TF 11 KOMBINATION SWEET-0011
status = VERIFIZIERT
hauptperson = Zimmer, Karin
bearbeiter = sb-sim-11
bg_id = 17730
dauer_ms = 298
ids = letzteZahlungId=16499, kollege=sb-sim-13, bgId=17730, mitarbeiter=sb-sim-11, zahlungId=16484, fallId=26763, vorgangsNr=ae28a158-6037-44fb-901b-2d52713582b8

### rechenwege

block: Kombinationsfall 11: Single
zahlungsmonat = 2026-03
vermoegen = 0.00
schonvermoegen = 10000.00
budget_start = 1248.71
p1_eingaben: rbs=1, merkzeichen_g=false, schwanger=true, entbindungsmonat=2026-06, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=230.00, kdu_betrieb=130.00, kdu_heizung=60.00, rente=120.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p1_regelbedarf=563.00
p1_mehrbedarf_g=0.00
p1_mehrbedarf_schwanger=95.71
p1_mehrbedarf_alleinerziehend=0.00
p1_mehrbedarf_warmwasser=0.00
p1_kdu=420.00
p1_gesamtbedarf=1078.71
p1_freibetrag_erwerb=0.00
p1_absetzbetaetrag=0.00
p1_anrechenbar=120.00
p1_zahlbetrag=958.71
bg_gesamtbedarf=1078.71
bg_anrechenbar=120.00
bg_verwertbares_vermoegen=0.00
bg_zahlbetrag=958.71
budget_rest_erwartet=290.00

block: Neuanordnung nach Mietekorrektur (Miete 230.00 → 330.00)
zahlungsmonat = 2026-03
vermoegen = 0.00
schonvermoegen = 10000.00
budget_start = 1248.71
p1_eingaben: rbs=1, merkzeichen_g=false, schwanger=true, entbindungsmonat=2026-06, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=330.00, kdu_betrieb=130.00, kdu_heizung=60.00, rente=120.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p1_regelbedarf=563.00
p1_mehrbedarf_g=0.00
p1_mehrbedarf_schwanger=95.71
p1_mehrbedarf_alleinerziehend=0.00
p1_mehrbedarf_warmwasser=0.00
p1_kdu=520.00
p1_gesamtbedarf=1178.71
p1_freibetrag_erwerb=0.00
p1_absetzbetaetrag=0.00
p1_anrechenbar=120.00
p1_zahlbetrag=1058.71
bg_gesamtbedarf=1178.71
bg_anrechenbar=120.00
bg_verwertbares_vermoegen=0.00
bg_zahlbetrag=1058.71
budget_rest_erwartet=190.00

### verifikation

V1: OK | alle Personen als eigene SozialFall-Zeilen
    sql    = SELECT COUNT(*) FROM SOZIAL_FALL WHERE BG_ID = ?
    params = [17730]
    erwartet = 1 | ist = 1
V2: OK | finale Zahlung wirksam (Vier-Augen-Warteschlange je nach Kassenlauf)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [16499]
    erwartet = ANGELEGT|BEREIT|UEBERGEBEN | ist = ANGELEGT
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [16499]
    erwartet = 1058.71 | ist = 1058.71
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17730]
    erwartet = 190.00 | ist = 190
V5: OK | zwei Zahlungen (stornierte + korrigierte)
    sql    = SELECT COUNT(*) FROM ZAHLUNG WHERE BG_ID = ?
    params = [17730]
    erwartet = 2 | ist = 2
V6: OK | erste Zahlung STORNIERT
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [16484]
    erwartet = STORNIERT | ist = STORNIERT
V7: OK | Fall in Version 2 (optimistisches Locking)
    sql    = SELECT VERSION FROM SOZIAL_FALL WHERE ID = ?
    params = [26763]
    erwartet = 2 | ist = 2
V8: OK | genau ein erteilter Bewilligungsbescheid
    sql    = SELECT COUNT(*) FROM BESCHEID WHERE BG_ID = ?
    params = [17730]
    erwartet = 1 | ist = 1
V9: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:20:39.748 sb-sim-11 | Vorgang anlegen (Single, Budget 1248.71 EUR) -> OK — Vorgang 17730
2026-10-13 11:20:39.792 sb-sim-11 | 1 SozialFall/Fälle erfassen -> OK — Single, KdU 420.00 EUR
2026-10-13 11:20:39.842 sb-sim-11 | Bewilligungsbescheid erteilen (2026-02 .. 2026-08) -> OK — Bescheid BESCH-SWEET-0011-2026-1724 mit Monatsplan gespeichert
2026-10-13 11:20:39.868 sb-sim-11 | Auszahlung anordnen (2026-03) -> OK — 958.71 EUR (ANGELEGT)
2026-10-13 11:20:39.881 sb-sim-11 | Anordnung sofort stornieren (Status ANGELEGT) -> OK — storniert — Monat wieder frei
2026-10-13 11:20:39.918 sb-sim-11 | Miete auf 330.00 korrigieren -> OK — Fall 26763 in Version 2
2026-10-13 11:20:39.940 sb-sim-11 | Korrigierte Auszahlung für denselben Monat -> OK — 1058.71 EUR

## TF 12 KOMBINATION SWEET-0012
status = VERIFIZIERT
hauptperson = Ismail, Stefan
bearbeiter = sb-sim-04
bg_id = 17738
dauer_ms = 221
ids = zahlungId=16480, vorgangsNr=5fa278d0-a2d8-4292-9d8c-c387dd72cf6a, mitarbeiter=sb-sim-04, bgId=17738, letzteZahlungId=16480, kollege=sb-sim-11

### rechenwege

block: Kombinationsfall 12: Alleinerziehend mit 1 Kind(ern)
zahlungsmonat = 2026-05
vermoegen = 11800.00
schonvermoegen = 10500.00
budget_start = 529.37
p1_eingaben: rbs=1, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=1, kinder_u16=1, kinder_minderjaehrig=1, warmwasser=false, kdu_miete=340.00, kdu_betrieb=55.00, kdu_heizung=130.00, rente=0.00, erwerb=420.00, abz_steuern=170.00, abz_sv=140.00, abz_vers=15.00, abz_erwerb=20.00
p1_regelbedarf=563.00
p1_mehrbedarf_g=0.00
p1_mehrbedarf_schwanger=0.00
p1_mehrbedarf_alleinerziehend=202.68
p1_mehrbedarf_warmwasser=0.00
p1_kdu=525.00
p1_gesamtbedarf=1290.68
p1_freibetrag_erwerb=126.00
p1_absetzbetaetrag=345.00
p1_anrechenbar=0.00
p1_zahlbetrag=1290.68
p2_eingaben: rbs=6, merkzeichen_g=true, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=0.00, kdu_betrieb=0.00, kdu_heizung=0.00, rente=290.00, erwerb=270.00, abz_steuern=40.00, abz_sv=160.00, abz_vers=50.00, abz_erwerb=0.00
p2_regelbedarf=357.00
p2_mehrbedarf_g=60.69
p2_mehrbedarf_schwanger=0.00
p2_mehrbedarf_alleinerziehend=0.00
p2_mehrbedarf_warmwasser=0.00
p2_kdu=0.00
p2_gesamtbedarf=417.69
p2_freibetrag_erwerb=81.00
p2_absetzbetaetrag=250.00
p2_anrechenbar=229.00
p2_zahlbetrag=188.69
bg_gesamtbedarf=1708.37
bg_anrechenbar=229.00
bg_verwertbares_vermoegen=1300.00
bg_zahlbetrag=179.37
budget_rest_erwartet=350.00

### verifikation

V1: OK | alle Personen als eigene SozialFall-Zeilen
    sql    = SELECT COUNT(*) FROM SOZIAL_FALL WHERE BG_ID = ?
    params = [17738]
    erwartet = 2 | ist = 2
V2: OK | finale Zahlung wirksam (Vier-Augen-Warteschlange je nach Kassenlauf)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [16480]
    erwartet = ANGELEGT|BEREIT|UEBERGEBEN | ist = ANGELEGT
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [16480]
    erwartet = 179.37 | ist = 179.37
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17738]
    erwartet = 350.00 | ist = 350
V5: OK | genau eine Zahlung trotz Wiederholung
    sql    = SELECT COUNT(*) FROM ZAHLUNG WHERE BG_ID = ?
    params = [17738]
    erwartet = 1 | ist = 1
V6: OK | Vermögen nach Verbrauchsfortschreibung (§ 90)
    sql    = SELECT VERMOEGEN FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17738]
    erwartet = 10500.00 | ist = 10500
V7: OK | dokumentierter Vermögenseinsatz an der Zahlung
    sql    = SELECT VERMOEGENSEINSATZ FROM ZAHLUNG WHERE ID = ?
    params = [16480]
    erwartet = 1300.00 | ist = 1300
V8: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:20:39.762 sb-sim-04 | Vorgang anlegen (Alleinerziehend mit 1 Kind(ern), Budget 529.37 EUR, Vermögen 11800.00 EUR) -> OK — Vorgang 17738
2026-10-13 11:20:39.818 sb-sim-04 | 2 SozialFall/Fälle erfassen -> OK — Alleinerziehend mit 1 Kind(ern), KdU 525.00 EUR
2026-10-13 11:20:39.849 sb-sim-04 | Auszahlung anordnen (2026-05) -> OK — 179.37 EUR (ANGELEGT)
2026-10-13 11:20:39.862 sb-sim-04 | Doppelklick: gleiche Vorgangsnummer erneut -> OK — korrekt als bereits gebucht erkannt

## TF 13 KOMBINATION SWEET-0013
status = VERIFIZIERT
hauptperson = Lindner, Gisela
bearbeiter = sb-sim-13
bg_id = 17740
dauer_ms = 247
ids = zahlungId=16487, vorgangsNr=874e1c93-560a-4450-837d-4307eab3c0da, mitarbeiter=sb-sim-13, bgId=17740, letzteZahlungId=16487, kollege=sb-sim-04

### rechenwege

block: Kombinationsfall 13: Paar mit 1 Kind(ern)
zahlungsmonat = 2026-03
vermoegen = 20950.00
schonvermoegen = 20500.00
budget_start = 270.28
p1_eingaben: rbs=2, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=true, kdu_miete=470.00, kdu_betrieb=40.00, kdu_heizung=155.00, rente=830.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p1_regelbedarf=506.00
p1_mehrbedarf_g=0.00
p1_mehrbedarf_schwanger=0.00
p1_mehrbedarf_alleinerziehend=0.00
p1_mehrbedarf_warmwasser=11.64
p1_kdu=665.00
p1_gesamtbedarf=1182.64
p1_freibetrag_erwerb=0.00
p1_absetzbetaetrag=0.00
p1_anrechenbar=830.00
p1_zahlbetrag=352.64
p2_eingaben: rbs=2, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=true, kdu_miete=0.00, kdu_betrieb=0.00, kdu_heizung=0.00, rente=470.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p2_regelbedarf=506.00
p2_mehrbedarf_g=0.00
p2_mehrbedarf_schwanger=0.00
p2_mehrbedarf_alleinerziehend=0.00
p2_mehrbedarf_warmwasser=11.64
p2_kdu=0.00
p2_gesamtbedarf=517.64
p2_freibetrag_erwerb=0.00
p2_absetzbetaetrag=0.00
p2_anrechenbar=470.00
p2_zahlbetrag=47.64
p3_eingaben: rbs=5, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=0.00, kdu_betrieb=0.00, kdu_heizung=0.00, rente=210.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p3_regelbedarf=390.00
p3_mehrbedarf_g=0.00
p3_mehrbedarf_schwanger=0.00
p3_mehrbedarf_alleinerziehend=0.00
p3_mehrbedarf_warmwasser=0.00
p3_kdu=0.00
p3_gesamtbedarf=390.00
p3_freibetrag_erwerb=0.00
p3_absetzbetaetrag=0.00
p3_anrechenbar=210.00
p3_zahlbetrag=180.00
bg_gesamtbedarf=2090.28
bg_anrechenbar=1510.00
bg_verwertbares_vermoegen=450.00
bg_zahlbetrag=130.28
budget_rest_erwartet=140.00

### verifikation

V1: OK | alle Personen als eigene SozialFall-Zeilen
    sql    = SELECT COUNT(*) FROM SOZIAL_FALL WHERE BG_ID = ?
    params = [17740]
    erwartet = 3 | ist = 3
V2: OK | finale Zahlung wirksam (Vier-Augen-Warteschlange je nach Kassenlauf)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [16487]
    erwartet = ANGELEGT|BEREIT|UEBERGEBEN | ist = ANGELEGT
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [16487]
    erwartet = 130.28 | ist = 130.28
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17740]
    erwartet = 140.00 | ist = 140
V5: OK | genau ein erteilter Bewilligungsbescheid
    sql    = SELECT COUNT(*) FROM BESCHEID WHERE BG_ID = ?
    params = [17740]
    erwartet = 1 | ist = 1
V6: OK | Vermögen nach Verbrauchsfortschreibung (§ 90)
    sql    = SELECT VERMOEGEN FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17740]
    erwartet = 20500.00 | ist = 20500
V7: OK | dokumentierter Vermögenseinsatz an der Zahlung
    sql    = SELECT VERMOEGENSEINSATZ FROM ZAHLUNG WHERE ID = ?
    params = [16487]
    erwartet = 450.00 | ist = 450
V8: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:20:39.762 sb-sim-13 | Vorgang anlegen (Paar mit 1 Kind(ern), Budget 270.28 EUR, Vermögen 20950.00 EUR) -> OK — Vorgang 17740
2026-10-13 11:20:39.826 sb-sim-13 | 3 SozialFall/Fälle erfassen -> OK — Paar mit 1 Kind(ern), KdU 665.00 EUR
2026-10-13 11:20:39.862 sb-sim-13 | Bewilligungsbescheid erteilen (2026-03 .. 2026-09) -> OK — Bescheid BESCH-SWEET-0013-2026-1726 mit Monatsplan gespeichert
2026-10-13 11:20:39.888 sb-sim-13 | Auszahlung anordnen (2026-03) -> OK — 130.28 EUR (ANGELEGT)

## TF 14 KOMBINATION SWEET-0014
status = VERIFIZIERT
hauptperson = Quest, Elke
bearbeiter = sb-sim-07
bg_id = 17737
dauer_ms = 218
ids = zahlungId=16478, vorgangsNr=3dedab20-ff7b-47ed-a174-0fd47fe7fff3, mitarbeiter=sb-sim-07, bgId=17737, letzteZahlungId=16478, kollege=sb-sim-19

### rechenwege

block: Kombinationsfall 14: Paar (2 × RBS 2)
zahlungsmonat = 2026-11
vermoegen = 0.00
schonvermoegen = 20000.00
budget_start = 2022.00
p1_eingaben: rbs=2, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=580.00, kdu_betrieb=125.00, kdu_heizung=165.00, rente=120.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p1_regelbedarf=506.00
p1_mehrbedarf_g=0.00
p1_mehrbedarf_schwanger=0.00
p1_mehrbedarf_alleinerziehend=0.00
p1_mehrbedarf_warmwasser=0.00
p1_kdu=870.00
p1_gesamtbedarf=1376.00
p1_freibetrag_erwerb=0.00
p1_absetzbetaetrag=0.00
p1_anrechenbar=120.00
p1_zahlbetrag=1256.00
p2_eingaben: rbs=2, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=0.00, kdu_betrieb=0.00, kdu_heizung=0.00, rente=0.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p2_regelbedarf=506.00
p2_mehrbedarf_g=0.00
p2_mehrbedarf_schwanger=0.00
p2_mehrbedarf_alleinerziehend=0.00
p2_mehrbedarf_warmwasser=0.00
p2_kdu=0.00
p2_gesamtbedarf=506.00
p2_freibetrag_erwerb=0.00
p2_absetzbetaetrag=0.00
p2_anrechenbar=0.00
p2_zahlbetrag=506.00
bg_gesamtbedarf=1882.00
bg_anrechenbar=120.00
bg_verwertbares_vermoegen=0.00
bg_zahlbetrag=1762.00
budget_rest_erwartet=260.00

### verifikation

V1: OK | alle Personen als eigene SozialFall-Zeilen
    sql    = SELECT COUNT(*) FROM SOZIAL_FALL WHERE BG_ID = ?
    params = [17737]
    erwartet = 2 | ist = 2
V2: OK | finale Zahlung freigegeben (oder bereits übergeben)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [16478]
    erwartet = BEREIT|UEBERGEBEN | ist = UEBERGEBEN
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [16478]
    erwartet = 1762.00 | ist = 1762
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17737]
    erwartet = 260.00 | ist = 260
V5: OK | Freigabevermerk nennt den Kollegen (Vier-Augen)
    sql    = SELECT CASE WHEN FREIGEGEBEN_VON = ? THEN 'ok' ELSE 'abweichend' END FROM ZAHLUNG WHERE ID = ?
    params = [sb-sim-19, 16478]
    erwartet = ok | ist = ok
V6: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:20:39.755 sb-sim-07 | Vorgang anlegen (Paar (2 × RBS 2), Budget 2022.00 EUR) -> OK — Vorgang 17737
2026-10-13 11:20:39.816 sb-sim-07 | 2 SozialFall/Fälle erfassen -> OK — Paar (2 × RBS 2), KdU 870.00 EUR
2026-10-13 11:20:39.846 sb-sim-07 | Auszahlung anordnen (2026-11) -> OK — 1762.00 EUR (ANGELEGT)
2026-10-13 11:20:39.860 sb-sim-07 | Freigabe durch Kollegen (Vier-Augen) -> OK — freigegeben durch sb-sim-19

## TF 15 KOMBINATION SWEET-0015
status = VERIFIZIERT
hauptperson = Thiel, Ludwig
bearbeiter = sb-sim-15
bg_id = 17727
dauer_ms = 193
ids = zahlungId=16471, vorgangsNr=7933f470-7b84-4ff8-bfb6-eb81ee60bf68, mitarbeiter=sb-sim-15, bgId=17727, letzteZahlungId=16471, kollege=sb-sim-05

### rechenwege

block: Kombinationsfall 15: Single
zahlungsmonat = 2026-09
vermoegen = 0.00
schonvermoegen = 10000.00
budget_start = 362.45
p1_eingaben: rbs=1, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=true, kdu_miete=420.00, kdu_betrieb=35.00, kdu_heizung=95.00, rente=390.00, erwerb=1330.00, abz_steuern=140.00, abz_sv=300.00, abz_vers=25.00, abz_erwerb=10.00
p1_regelbedarf=563.00
p1_mehrbedarf_g=0.00
p1_mehrbedarf_schwanger=0.00
p1_mehrbedarf_alleinerziehend=0.00
p1_mehrbedarf_warmwasser=12.95
p1_kdu=550.00
p1_gesamtbedarf=1125.95
p1_freibetrag_erwerb=281.50
p1_absetzbetaetrag=475.00
p1_anrechenbar=963.50
p1_zahlbetrag=162.45
bg_gesamtbedarf=1125.95
bg_anrechenbar=963.50
bg_verwertbares_vermoegen=0.00
bg_zahlbetrag=162.45
budget_rest_erwartet=200.00

### verifikation

V1: OK | alle Personen als eigene SozialFall-Zeilen
    sql    = SELECT COUNT(*) FROM SOZIAL_FALL WHERE BG_ID = ?
    params = [17727]
    erwartet = 1 | ist = 1
V2: OK | finale Zahlung wirksam (Vier-Augen-Warteschlange je nach Kassenlauf)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [16471]
    erwartet = ANGELEGT|BEREIT|UEBERGEBEN | ist = ANGELEGT
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [16471]
    erwartet = 162.45 | ist = 162.45
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17727]
    erwartet = 200.00 | ist = 200
V5: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:20:39.748 sb-sim-15 | Vorgang anlegen (Single, Budget 362.45 EUR) -> OK — Vorgang 17727
2026-10-13 11:20:39.793 sb-sim-15 | 1 SozialFall/Fälle erfassen -> OK — Single, KdU 550.00 EUR
2026-10-13 11:20:39.835 sb-sim-15 | Auszahlung anordnen (2026-09) -> OK — 162.45 EUR (ANGELEGT)

## TF 16 KOMBINATION SWEET-0016
status = VERIFIZIERT
hauptperson = Jung, Stefan
bearbeiter = sb-sim-16
bg_id = 17735
dauer_ms = 244
ids = zahlungId=16485, vorgangsNr=c9f89cdc-a9d4-4c94-a595-3e310a8de1d8, mitarbeiter=sb-sim-16, bgId=17735, letzteZahlungId=16485, kollege=sb-sim-03

### rechenwege

block: Kombinationsfall 16: Paar (2 × RBS 2) (Schwangerschafts-Mehrbedarf entfällt im Zahlungsmonat)
zahlungsmonat = 2026-11
vermoegen = 20150.00
schonvermoegen = 20000.00
budget_start = 666.50
p1_eingaben: rbs=2, merkzeichen_g=false, schwanger=false, entbindungsmonat=2026-08, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=600.00, kdu_betrieb=0.00, kdu_heizung=0.00, rente=460.00, erwerb=460.00, abz_steuern=30.00, abz_sv=270.00, abz_vers=30.00, abz_erwerb=0.00
p1_regelbedarf=506.00
p1_mehrbedarf_g=0.00
p1_mehrbedarf_schwanger=0.00
p1_mehrbedarf_alleinerziehend=0.00
p1_mehrbedarf_warmwasser=0.00
p1_kdu=600.00
p1_gesamtbedarf=1106.00
p1_freibetrag_erwerb=138.00
p1_absetzbetaetrag=330.00
p1_anrechenbar=452.00
p1_zahlbetrag=654.00
p2_eingaben: rbs=2, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=0.00, kdu_betrieb=100.00, kdu_heizung=65.00, rente=240.00, erwerb=970.00, abz_steuern=160.00, abz_sv=70.00, abz_vers=40.00, abz_erwerb=70.00
p2_regelbedarf=506.00
p2_mehrbedarf_g=0.00
p2_mehrbedarf_schwanger=0.00
p2_mehrbedarf_alleinerziehend=0.00
p2_mehrbedarf_warmwasser=0.00
p2_kdu=165.00
p2_gesamtbedarf=671.00
p2_freibetrag_erwerb=281.50
p2_absetzbetaetrag=340.00
p2_anrechenbar=588.50
p2_zahlbetrag=82.50
bg_gesamtbedarf=1777.00
bg_anrechenbar=1040.50
bg_verwertbares_vermoegen=150.00
bg_zahlbetrag=586.50
budget_rest_erwartet=80.00

### verifikation

V1: OK | alle Personen als eigene SozialFall-Zeilen
    sql    = SELECT COUNT(*) FROM SOZIAL_FALL WHERE BG_ID = ?
    params = [17735]
    erwartet = 2 | ist = 2
V2: OK | finale Zahlung wirksam (Vier-Augen-Warteschlange je nach Kassenlauf)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [16485]
    erwartet = ANGELEGT|BEREIT|UEBERGEBEN | ist = ANGELEGT
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [16485]
    erwartet = 586.50 | ist = 586.5
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17735]
    erwartet = 80.00 | ist = 80
V5: OK | genau eine Zahlung trotz Wiederholung
    sql    = SELECT COUNT(*) FROM ZAHLUNG WHERE BG_ID = ?
    params = [17735]
    erwartet = 1 | ist = 1
V6: OK | genau ein erteilter Bewilligungsbescheid
    sql    = SELECT COUNT(*) FROM BESCHEID WHERE BG_ID = ?
    params = [17735]
    erwartet = 1 | ist = 1
V7: OK | Vermögen nach Verbrauchsfortschreibung (§ 90)
    sql    = SELECT VERMOEGEN FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17735]
    erwartet = 20000.00 | ist = 20000
V8: OK | dokumentierter Vermögenseinsatz an der Zahlung
    sql    = SELECT VERMOEGENSEINSATZ FROM ZAHLUNG WHERE ID = ?
    params = [16485]
    erwartet = 150.00 | ist = 150
V9: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:20:39.749 sb-sim-16 | Vorgang anlegen (Paar (2 × RBS 2), Budget 666.50 EUR, Vermögen 20150.00 EUR) -> OK — Vorgang 17735
2026-10-13 11:20:39.817 sb-sim-16 | 2 SozialFall/Fälle erfassen -> OK — Paar (2 × RBS 2), KdU 765.00 EUR
2026-10-13 11:20:39.848 sb-sim-16 | Bewilligungsbescheid erteilen (2026-09 .. 2026-12) -> OK — Bescheid BESCH-SWEET-0016-2026-1725 mit Monatsplan gespeichert
2026-10-13 11:20:39.872 sb-sim-16 | Auszahlung anordnen (2026-11) -> OK — 586.50 EUR (ANGELEGT)
2026-10-13 11:20:39.885 sb-sim-16 | Doppelklick: gleiche Vorgangsnummer erneut -> OK — korrekt als bereits gebucht erkannt

## TF 17 KOMBINATION SWEET-0017
status = VERIFIZIERT
hauptperson = Brandt, Clara
bearbeiter = sb-sim-17
bg_id = 17731
dauer_ms = 280
ids = zahlungId=16489, vorgangsNr=6d093dac-66bf-4bc8-86aa-3969b87c7bad, mitarbeiter=sb-sim-17, bgId=17731, letzteZahlungId=16489, kollege=sb-sim-03

### rechenwege

block: Kombinationsfall 17: Paar mit 2 Kind(ern)
zahlungsmonat = 2026-06
vermoegen = 21200.00
schonvermoegen = 21000.00
budget_start = 381.57
p1_eingaben: rbs=2, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=300.00, kdu_betrieb=70.00, kdu_heizung=150.00, rente=670.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p1_regelbedarf=506.00
p1_mehrbedarf_g=0.00
p1_mehrbedarf_schwanger=0.00
p1_mehrbedarf_alleinerziehend=0.00
p1_mehrbedarf_warmwasser=0.00
p1_kdu=520.00
p1_gesamtbedarf=1026.00
p1_freibetrag_erwerb=0.00
p1_absetzbetaetrag=0.00
p1_anrechenbar=670.00
p1_zahlbetrag=356.00
p2_eingaben: rbs=2, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=0.00, kdu_betrieb=0.00, kdu_heizung=0.00, rente=580.00, erwerb=800.00, abz_steuern=160.00, abz_sv=130.00, abz_vers=70.00, abz_erwerb=80.00
p2_regelbedarf=506.00
p2_mehrbedarf_g=0.00
p2_mehrbedarf_schwanger=0.00
p2_mehrbedarf_alleinerziehend=0.00
p2_mehrbedarf_warmwasser=0.00
p2_kdu=0.00
p2_gesamtbedarf=506.00
p2_freibetrag_erwerb=240.00
p2_absetzbetaetrag=440.00
p2_anrechenbar=700.00
p2_zahlbetrag=-194.00
p3_eingaben: rbs=4, merkzeichen_g=true, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=0.00, kdu_betrieb=0.00, kdu_heizung=0.00, rente=0.00, erwerb=1400.00, abz_steuern=140.00, abz_sv=170.00, abz_vers=10.00, abz_erwerb=40.00
p3_regelbedarf=471.00
p3_mehrbedarf_g=80.07
p3_mehrbedarf_schwanger=0.00
p3_mehrbedarf_alleinerziehend=0.00
p3_mehrbedarf_warmwasser=0.00
p3_kdu=0.00
p3_gesamtbedarf=551.07
p3_freibetrag_erwerb=281.50
p3_absetzbetaetrag=360.00
p3_anrechenbar=758.50
p3_zahlbetrag=-207.43
p4_eingaben: rbs=6, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=0.00, kdu_betrieb=0.00, kdu_heizung=0.00, rente=0.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p4_regelbedarf=357.00
p4_mehrbedarf_g=0.00
p4_mehrbedarf_schwanger=0.00
p4_mehrbedarf_alleinerziehend=0.00
p4_mehrbedarf_warmwasser=0.00
p4_kdu=0.00
p4_gesamtbedarf=357.00
p4_freibetrag_erwerb=0.00
p4_absetzbetaetrag=0.00
p4_anrechenbar=0.00
p4_zahlbetrag=357.00
bg_gesamtbedarf=2440.07
bg_anrechenbar=2128.50
bg_verwertbares_vermoegen=200.00
bg_zahlbetrag=111.57
budget_rest_erwartet=270.00

### verifikation

V1: OK | alle Personen als eigene SozialFall-Zeilen
    sql    = SELECT COUNT(*) FROM SOZIAL_FALL WHERE BG_ID = ?
    params = [17731]
    erwartet = 4 | ist = 4
V2: OK | finale Zahlung wirksam (Vier-Augen-Warteschlange je nach Kassenlauf)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [16489]
    erwartet = ANGELEGT|BEREIT|UEBERGEBEN | ist = ANGELEGT
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [16489]
    erwartet = 111.57 | ist = 111.57
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17731]
    erwartet = 270.00 | ist = 270
V5: OK | genau eine Zahlung trotz Wiederholung
    sql    = SELECT COUNT(*) FROM ZAHLUNG WHERE BG_ID = ?
    params = [17731]
    erwartet = 1 | ist = 1
V6: OK | genau ein erteilter Bewilligungsbescheid
    sql    = SELECT COUNT(*) FROM BESCHEID WHERE BG_ID = ?
    params = [17731]
    erwartet = 1 | ist = 1
V7: OK | Vermögen nach Verbrauchsfortschreibung (§ 90)
    sql    = SELECT VERMOEGEN FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17731]
    erwartet = 21000.00 | ist = 21000
V8: OK | dokumentierter Vermögenseinsatz an der Zahlung
    sql    = SELECT VERMOEGENSEINSATZ FROM ZAHLUNG WHERE ID = ?
    params = [16489]
    erwartet = 200.00 | ist = 200
V9: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:20:39.748 sb-sim-17 | Vorgang anlegen (Paar mit 2 Kind(ern), Budget 381.57 EUR, Vermögen 21200.00 EUR) -> OK — Vorgang 17731
2026-10-13 11:20:39.850 sb-sim-17 | 4 SozialFall/Fälle erfassen -> OK — Paar mit 2 Kind(ern), KdU 520.00 EUR
2026-10-13 11:20:39.884 sb-sim-17 | Bewilligungsbescheid erteilen (2026-04 .. 2026-12) -> OK — Bescheid BESCH-SWEET-0017-2026-1727 mit Monatsplan gespeichert
2026-10-13 11:20:39.908 sb-sim-17 | Auszahlung anordnen (2026-06) -> OK — 111.57 EUR (ANGELEGT)
2026-10-13 11:20:39.922 sb-sim-17 | Doppelklick: gleiche Vorgangsnummer erneut -> OK — korrekt als bereits gebucht erkannt

## TF 18 KOMBINATION SWEET-0018
status = VERIFIZIERT
hauptperson = Brandt, Frank
bearbeiter = sb-sim-18
bg_id = 17744
dauer_ms = 224
ids = zahlungId=16482, vorgangsNr=b0df480a-15e9-4b51-a746-b377fa415ba2, mitarbeiter=sb-sim-18, bgId=17744, letzteZahlungId=16482, kollege=sb-sim-06

### rechenwege

block: Kombinationsfall 18: Single (Schwangerschafts-Mehrbedarf entfällt im Zahlungsmonat)
zahlungsmonat = 2026-03
vermoegen = 10700.00
schonvermoegen = 10000.00
budget_start = 746.66
p1_eingaben: rbs=1, merkzeichen_g=true, schwanger=false, entbindungsmonat=2026-01, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=true, kdu_miete=240.00, kdu_betrieb=70.00, kdu_heizung=95.00, rente=0.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p1_regelbedarf=563.00
p1_mehrbedarf_g=95.71
p1_mehrbedarf_schwanger=0.00
p1_mehrbedarf_alleinerziehend=0.00
p1_mehrbedarf_warmwasser=12.95
p1_kdu=405.00
p1_gesamtbedarf=1076.66
p1_freibetrag_erwerb=0.00
p1_absetzbetaetrag=0.00
p1_anrechenbar=0.00
p1_zahlbetrag=1076.66
bg_gesamtbedarf=1076.66
bg_anrechenbar=0.00
bg_verwertbares_vermoegen=700.00
bg_zahlbetrag=376.66
budget_rest_erwartet=370.00

### verifikation

V1: OK | alle Personen als eigene SozialFall-Zeilen
    sql    = SELECT COUNT(*) FROM SOZIAL_FALL WHERE BG_ID = ?
    params = [17744]
    erwartet = 1 | ist = 1
V2: OK | finale Zahlung wirksam (Vier-Augen-Warteschlange je nach Kassenlauf)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [16482]
    erwartet = ANGELEGT|BEREIT|UEBERGEBEN | ist = ANGELEGT
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [16482]
    erwartet = 376.66 | ist = 376.66
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17744]
    erwartet = 370.00 | ist = 370
V5: OK | genau ein erteilter Bewilligungsbescheid
    sql    = SELECT COUNT(*) FROM BESCHEID WHERE BG_ID = ?
    params = [17744]
    erwartet = 1 | ist = 1
V6: OK | Vermögen nach Verbrauchsfortschreibung (§ 90)
    sql    = SELECT VERMOEGEN FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17744]
    erwartet = 10000.00 | ist = 10000
V7: OK | dokumentierter Vermögenseinsatz an der Zahlung
    sql    = SELECT VERMOEGENSEINSATZ FROM ZAHLUNG WHERE ID = ?
    params = [16482]
    erwartet = 700.00 | ist = 700
V8: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:20:39.776 sb-sim-18 | Vorgang anlegen (Single, Budget 746.66 EUR, Vermögen 10700.00 EUR) -> OK — Vorgang 17744
2026-10-13 11:20:39.792 sb-sim-18 | 1 SozialFall/Fälle erfassen -> OK — Single, KdU 405.00 EUR
2026-10-13 11:20:39.837 sb-sim-18 | Bewilligungsbescheid erteilen (2026-03 .. 2026-04) -> OK — Bescheid BESCH-SWEET-0018-2026-1723 mit Monatsplan gespeichert
2026-10-13 11:20:39.866 sb-sim-18 | Auszahlung anordnen (2026-03) -> OK — 376.66 EUR (ANGELEGT)

## TF 19 KOMBINATION SWEET-0019
status = VERIFIZIERT
hauptperson = Dietrich, Ludwig
bearbeiter = sb-sim-19
bg_id = 17729
dauer_ms = 224
ids = zahlungId=16483, vorgangsNr=f27b0cbb-a0cd-456f-8dd5-765ea610b8ba, mitarbeiter=sb-sim-19, bgId=17729, letzteZahlungId=16483, kollege=sb-sim-02

### rechenwege

block: Kombinationsfall 19: Alleinerziehend mit 3 Kind(ern)
zahlungsmonat = 2026-11
vermoegen = 0.00
schonvermoegen = 11500.00
budget_start = 2322.27
p1_eingaben: rbs=1, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=3, kinder_minderjaehrig=3, warmwasser=false, kdu_miete=510.00, kdu_betrieb=25.00, kdu_heizung=140.00, rente=420.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p1_regelbedarf=563.00
p1_mehrbedarf_g=0.00
p1_mehrbedarf_schwanger=0.00
p1_mehrbedarf_alleinerziehend=202.68
p1_mehrbedarf_warmwasser=0.00
p1_kdu=675.00
p1_gesamtbedarf=1440.68
p1_freibetrag_erwerb=0.00
p1_absetzbetaetrag=0.00
p1_anrechenbar=420.00
p1_zahlbetrag=1020.68
p2_eingaben: rbs=6, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=0.00, kdu_betrieb=0.00, kdu_heizung=0.00, rente=200.00, erwerb=320.00, abz_steuern=190.00, abz_sv=290.00, abz_vers=80.00, abz_erwerb=0.00
p2_regelbedarf=357.00
p2_mehrbedarf_g=0.00
p2_mehrbedarf_schwanger=0.00
p2_mehrbedarf_alleinerziehend=0.00
p2_mehrbedarf_warmwasser=0.00
p2_kdu=0.00
p2_gesamtbedarf=357.00
p2_freibetrag_erwerb=96.00
p2_absetzbetaetrag=560.00
p2_anrechenbar=0.00
p2_zahlbetrag=357.00
p3_eingaben: rbs=4, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=true, kdu_miete=0.00, kdu_betrieb=0.00, kdu_heizung=0.00, rente=100.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p3_regelbedarf=471.00
p3_mehrbedarf_g=0.00
p3_mehrbedarf_schwanger=0.00
p3_mehrbedarf_alleinerziehend=0.00
p3_mehrbedarf_warmwasser=6.59
p3_kdu=0.00
p3_gesamtbedarf=477.59
p3_freibetrag_erwerb=0.00
p3_absetzbetaetrag=0.00
p3_anrechenbar=100.00
p3_zahlbetrag=377.59
p4_eingaben: rbs=6, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=0.00, kdu_betrieb=0.00, kdu_heizung=0.00, rente=0.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p4_regelbedarf=357.00
p4_mehrbedarf_g=0.00
p4_mehrbedarf_schwanger=0.00
p4_mehrbedarf_alleinerziehend=0.00
p4_mehrbedarf_warmwasser=0.00
p4_kdu=0.00
p4_gesamtbedarf=357.00
p4_freibetrag_erwerb=0.00
p4_absetzbetaetrag=0.00
p4_anrechenbar=0.00
p4_zahlbetrag=357.00
bg_gesamtbedarf=2632.27
bg_anrechenbar=520.00
bg_verwertbares_vermoegen=0.00
bg_zahlbetrag=2112.27
budget_rest_erwartet=210.00

### verifikation

V1: OK | alle Personen als eigene SozialFall-Zeilen
    sql    = SELECT COUNT(*) FROM SOZIAL_FALL WHERE BG_ID = ?
    params = [17729]
    erwartet = 4 | ist = 4
V2: OK | finale Zahlung wirksam (Vier-Augen-Warteschlange je nach Kassenlauf)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [16483]
    erwartet = ANGELEGT|BEREIT|UEBERGEBEN | ist = ANGELEGT
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [16483]
    erwartet = 2112.27 | ist = 2112.27
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17729]
    erwartet = 210.00 | ist = 210
V5: OK | Wiedervorlagedatum exakt wie gesetzt
    sql    = SELECT COUNT(*) FROM BEDARFSGEMEINSCHAFT WHERE ID = ? AND WIEDERVORLAGE_AM = ?
    params = [17729, 2027-05-07]
    erwartet = 1 | ist = 1
V6: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:20:39.748 sb-sim-19 | Vorgang anlegen (Alleinerziehend mit 3 Kind(ern), Budget 2322.27 EUR) -> OK — Vorgang 17729
2026-10-13 11:20:39.846 sb-sim-19 | 4 SozialFall/Fälle erfassen -> OK — Alleinerziehend mit 3 Kind(ern), KdU 675.00 EUR
2026-10-13 11:20:39.866 sb-sim-19 | Auszahlung anordnen (2026-11) -> OK — 2112.27 EUR (ANGELEGT)

## TF 20 KOMBINATION SWEET-0020
status = VERIFIZIERT
hauptperson = Gross, Karin
bearbeiter = sb-sim-20
bg_id = 17726
dauer_ms = 221
ids = zahlungId=16477, vorgangsNr=5a939e07-ace9-4d97-9782-0029575db9b0, mitarbeiter=sb-sim-20, bgId=17726, letzteZahlungId=16477, kollege=sb-sim-04

### rechenwege

block: Kombinationsfall 20: Paar (2 × RBS 2) (Schwangerschafts-Mehrbedarf entfällt im Zahlungsmonat)
zahlungsmonat = 2026-06
vermoegen = 0.00
schonvermoegen = 20000.00
budget_start = 418.50
p1_eingaben: rbs=2, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=480.00, kdu_betrieb=0.00, kdu_heizung=0.00, rente=110.00, erwerb=1370.00, abz_steuern=60.00, abz_sv=130.00, abz_vers=5.00, abz_erwerb=90.00
p1_regelbedarf=506.00
p1_mehrbedarf_g=0.00
p1_mehrbedarf_schwanger=0.00
p1_mehrbedarf_alleinerziehend=0.00
p1_mehrbedarf_warmwasser=0.00
p1_kdu=480.00
p1_gesamtbedarf=986.00
p1_freibetrag_erwerb=281.50
p1_absetzbetaetrag=285.00
p1_anrechenbar=913.50
p1_zahlbetrag=72.50
p2_eingaben: rbs=2, merkzeichen_g=false, schwanger=false, entbindungsmonat=2026-04, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=0.00, kdu_betrieb=40.00, kdu_heizung=170.00, rente=450.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p2_regelbedarf=506.00
p2_mehrbedarf_g=0.00
p2_mehrbedarf_schwanger=0.00
p2_mehrbedarf_alleinerziehend=0.00
p2_mehrbedarf_warmwasser=0.00
p2_kdu=210.00
p2_gesamtbedarf=716.00
p2_freibetrag_erwerb=0.00
p2_absetzbetaetrag=0.00
p2_anrechenbar=450.00
p2_zahlbetrag=266.00
bg_gesamtbedarf=1702.00
bg_anrechenbar=1363.50
bg_verwertbares_vermoegen=0.00
bg_zahlbetrag=338.50
budget_rest_erwartet=80.00

### verifikation

V1: OK | alle Personen als eigene SozialFall-Zeilen
    sql    = SELECT COUNT(*) FROM SOZIAL_FALL WHERE BG_ID = ?
    params = [17726]
    erwartet = 2 | ist = 2
V2: OK | finale Zahlung wirksam (Vier-Augen-Warteschlange je nach Kassenlauf)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [16477]
    erwartet = ANGELEGT|BEREIT|UEBERGEBEN | ist = ANGELEGT
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [16477]
    erwartet = 338.50 | ist = 338.5
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17726]
    erwartet = 80.00 | ist = 80
V5: OK | genau eine Zahlung trotz Wiederholung
    sql    = SELECT COUNT(*) FROM ZAHLUNG WHERE BG_ID = ?
    params = [17726]
    erwartet = 1 | ist = 1
V6: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:20:39.748 sb-sim-20 | Vorgang anlegen (Paar (2 × RBS 2), Budget 418.50 EUR) -> OK — Vorgang 17726
2026-10-13 11:20:39.816 sb-sim-20 | 2 SozialFall/Fälle erfassen -> OK — Paar (2 × RBS 2), KdU 690.00 EUR
2026-10-13 11:20:39.849 sb-sim-20 | Auszahlung anordnen (2026-06) -> OK — 338.50 EUR (ANGELEGT)
2026-10-13 11:20:39.862 sb-sim-20 | Doppelklick: gleiche Vorgangsnummer erneut -> OK — korrekt als bereits gebucht erkannt

## TF 21 KOMBINATION SWEET-0021
status = VERIFIZIERT
hauptperson = Fischer, Olga
bearbeiter = sb-sim-10
bg_id = 17745
dauer_ms = 61
ids = zahlungId=16479, vorgangsNr=c77aba05-0276-4590-b1f1-06eea315f1e8, mitarbeiter=sb-sim-10, bgId=17745, letzteZahlungId=16479, kollege=sb-sim-12

### rechenwege

block: Kombinationsfall 21: Single
zahlungsmonat = 2026-09
vermoegen = 0.00
schonvermoegen = 10000.00
budget_start = 575.95
p1_eingaben: rbs=1, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=true, kdu_miete=520.00, kdu_betrieb=50.00, kdu_heizung=30.00, rente=650.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p1_regelbedarf=563.00
p1_mehrbedarf_g=0.00
p1_mehrbedarf_schwanger=0.00
p1_mehrbedarf_alleinerziehend=0.00
p1_mehrbedarf_warmwasser=12.95
p1_kdu=600.00
p1_gesamtbedarf=1175.95
p1_freibetrag_erwerb=0.00
p1_absetzbetaetrag=0.00
p1_anrechenbar=650.00
p1_zahlbetrag=525.95
bg_gesamtbedarf=1175.95
bg_anrechenbar=650.00
bg_verwertbares_vermoegen=0.00
bg_zahlbetrag=525.95
budget_rest_erwartet=50.00

### verifikation

V1: OK | alle Personen als eigene SozialFall-Zeilen
    sql    = SELECT COUNT(*) FROM SOZIAL_FALL WHERE BG_ID = ?
    params = [17745]
    erwartet = 1 | ist = 1
V2: OK | finale Zahlung wirksam (Vier-Augen-Warteschlange je nach Kassenlauf)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [16479]
    erwartet = ANGELEGT|BEREIT|UEBERGEBEN | ist = ANGELEGT
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [16479]
    erwartet = 525.95 | ist = 525.95
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17745]
    erwartet = 50.00 | ist = 50
V5: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:20:39.794 sb-sim-10 | Vorgang anlegen (Single, Budget 575.95 EUR) -> OK — Vorgang 17745
2026-10-13 11:20:39.820 sb-sim-10 | 1 SozialFall/Fälle erfassen -> OK — Single, KdU 600.00 EUR
2026-10-13 11:20:39.846 sb-sim-10 | Auszahlung anordnen (2026-09) -> OK — 525.95 EUR (ANGELEGT)

## TF 22 KOMBINATION SWEET-0022
status = VERIFIZIERT
hauptperson = Thiel, Olga
bearbeiter = sb-sim-12
bg_id = 17746
dauer_ms = 97
ids = zahlungId=16493, vorgangsNr=1369f4bc-f506-48be-b772-31afd24ea2b6, mitarbeiter=sb-sim-12, bgId=17746, letzteZahlungId=16493, kollege=sb-sim-10

### rechenwege

block: Kombinationsfall 22: Paar (2 × RBS 2)
zahlungsmonat = 2026-06
vermoegen = 0.00
schonvermoegen = 20000.00
budget_start = 1454.84
p1_eingaben: rbs=2, merkzeichen_g=true, schwanger=true, entbindungsmonat=2026-11, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=true, kdu_miete=640.00, kdu_betrieb=0.00, kdu_heizung=0.00, rente=0.00, erwerb=1200.00, abz_steuern=90.00, abz_sv=80.00, abz_vers=35.00, abz_erwerb=20.00
p1_regelbedarf=506.00
p1_mehrbedarf_g=86.02
p1_mehrbedarf_schwanger=86.02
p1_mehrbedarf_alleinerziehend=0.00
p1_mehrbedarf_warmwasser=11.64
p1_kdu=640.00
p1_gesamtbedarf=1329.68
p1_freibetrag_erwerb=281.50
p1_absetzbetaetrag=225.00
p1_anrechenbar=693.50
p1_zahlbetrag=636.18
p2_eingaben: rbs=2, merkzeichen_g=false, schwanger=true, entbindungsmonat=2026-11, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=true, kdu_miete=0.00, kdu_betrieb=85.00, kdu_heizung=110.00, rente=300.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p2_regelbedarf=506.00
p2_mehrbedarf_g=0.00
p2_mehrbedarf_schwanger=86.02
p2_mehrbedarf_alleinerziehend=0.00
p2_mehrbedarf_warmwasser=11.64
p2_kdu=195.00
p2_gesamtbedarf=798.66
p2_freibetrag_erwerb=0.00
p2_absetzbetaetrag=0.00
p2_anrechenbar=300.00
p2_zahlbetrag=498.66
bg_gesamtbedarf=2128.34
bg_anrechenbar=993.50
bg_verwertbares_vermoegen=0.00
bg_zahlbetrag=1134.84
budget_rest_erwartet=320.00

### verifikation

V1: OK | alle Personen als eigene SozialFall-Zeilen
    sql    = SELECT COUNT(*) FROM SOZIAL_FALL WHERE BG_ID = ?
    params = [17746]
    erwartet = 2 | ist = 2
V2: OK | finale Zahlung wirksam (Vier-Augen-Warteschlange je nach Kassenlauf)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [16493]
    erwartet = ANGELEGT|BEREIT|UEBERGEBEN | ist = ANGELEGT
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [16493]
    erwartet = 1134.84 | ist = 1134.84
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17746]
    erwartet = 320.00 | ist = 320
V5: OK | genau ein erteilter Bewilligungsbescheid
    sql    = SELECT COUNT(*) FROM BESCHEID WHERE BG_ID = ?
    params = [17746]
    erwartet = 1 | ist = 1
V6: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:20:39.837 sb-sim-12 | Vorgang anlegen (Paar (2 × RBS 2), Budget 1454.84 EUR) -> OK — Vorgang 17746
2026-10-13 11:20:39.864 sb-sim-12 | 2 SozialFall/Fälle erfassen -> OK — Paar (2 × RBS 2), KdU 835.00 EUR
2026-10-13 11:20:39.897 sb-sim-12 | Bewilligungsbescheid erteilen (2026-03 .. 2026-12) -> OK — Bescheid BESCH-SWEET-0022-2026-1728 mit Monatsplan gespeichert
2026-10-13 11:20:39.922 sb-sim-12 | Auszahlung anordnen (2026-06) -> OK — 1134.84 EUR (ANGELEGT)

## TF 23 KOMBINATION SWEET-0023
status = VERIFIZIERT
hauptperson = Ismail, Marlene
bearbeiter = sb-sim-15
bg_id = 17747
dauer_ms = 75
ids = zahlungId=16490, vorgangsNr=45985afa-ed7e-4ac3-87b1-a6048e873f4a, mitarbeiter=sb-sim-15, bgId=17747, letzteZahlungId=16490, kollege=sb-sim-20

### rechenwege

block: Kombinationsfall 23: Paar mit 1 Kind(ern)
zahlungsmonat = 2026-04
vermoegen = 20550.00
schonvermoegen = 20500.00
budget_start = 512.59
p1_eingaben: rbs=2, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=200.00, kdu_betrieb=140.00, kdu_heizung=70.00, rente=1060.00, erwerb=710.00, abz_steuern=70.00, abz_sv=310.00, abz_vers=60.00, abz_erwerb=0.00
p1_regelbedarf=506.00
p1_mehrbedarf_g=0.00
p1_mehrbedarf_schwanger=0.00
p1_mehrbedarf_alleinerziehend=0.00
p1_mehrbedarf_warmwasser=0.00
p1_kdu=410.00
p1_gesamtbedarf=916.00
p1_freibetrag_erwerb=213.00
p1_absetzbetaetrag=440.00
p1_anrechenbar=1117.00
p1_zahlbetrag=-201.00
p2_eingaben: rbs=2, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=0.00, kdu_betrieb=0.00, kdu_heizung=0.00, rente=360.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p2_regelbedarf=506.00
p2_mehrbedarf_g=0.00
p2_mehrbedarf_schwanger=0.00
p2_mehrbedarf_alleinerziehend=0.00
p2_mehrbedarf_warmwasser=0.00
p2_kdu=0.00
p2_gesamtbedarf=506.00
p2_freibetrag_erwerb=0.00
p2_absetzbetaetrag=0.00
p2_anrechenbar=360.00
p2_zahlbetrag=146.00
p3_eingaben: rbs=4, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=true, kdu_miete=0.00, kdu_betrieb=0.00, kdu_heizung=0.00, rente=0.00, erwerb=200.00, abz_steuern=70.00, abz_sv=50.00, abz_vers=70.00, abz_erwerb=20.00
p3_regelbedarf=471.00
p3_mehrbedarf_g=0.00
p3_mehrbedarf_schwanger=0.00
p3_mehrbedarf_alleinerziehend=0.00
p3_mehrbedarf_warmwasser=6.59
p3_kdu=0.00
p3_gesamtbedarf=477.59
p3_freibetrag_erwerb=60.00
p3_absetzbetaetrag=210.00
p3_anrechenbar=0.00
p3_zahlbetrag=477.59
bg_gesamtbedarf=1899.59
bg_anrechenbar=1477.00
bg_verwertbares_vermoegen=50.00
bg_zahlbetrag=372.59
budget_rest_erwartet=140.00

### verifikation

V1: OK | alle Personen als eigene SozialFall-Zeilen
    sql    = SELECT COUNT(*) FROM SOZIAL_FALL WHERE BG_ID = ?
    params = [17747]
    erwartet = 3 | ist = 3
V2: OK | finale Zahlung wirksam (Vier-Augen-Warteschlange je nach Kassenlauf)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [16490]
    erwartet = ANGELEGT|BEREIT|UEBERGEBEN | ist = ANGELEGT
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [16490]
    erwartet = 372.59 | ist = 372.59
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17747]
    erwartet = 140.00 | ist = 140
V5: OK | Vermögen nach Verbrauchsfortschreibung (§ 90)
    sql    = SELECT VERMOEGEN FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17747]
    erwartet = 20500.00 | ist = 20500
V6: OK | dokumentierter Vermögenseinsatz an der Zahlung
    sql    = SELECT VERMOEGENSEINSATZ FROM ZAHLUNG WHERE ID = ?
    params = [16490]
    erwartet = 50.00 | ist = 50
V7: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:20:39.851 sb-sim-15 | Vorgang anlegen (Paar mit 1 Kind(ern), Budget 512.59 EUR, Vermögen 20550.00 EUR) -> OK — Vorgang 17747
2026-10-13 11:20:39.888 sb-sim-15 | 3 SozialFall/Fälle erfassen -> OK — Paar mit 1 Kind(ern), KdU 410.00 EUR
2026-10-13 11:20:39.910 sb-sim-15 | Auszahlung anordnen (2026-04) -> OK — 372.59 EUR (ANGELEGT)

## TF 24 KOMBINATION SWEET-0024
status = VERIFIZIERT
hauptperson = Vogt, Willi
bearbeiter = sb-sim-05
bg_id = 17748
dauer_ms = 48
ids = zahlungId=16486, vorgangsNr=49fb3004-d5a9-4668-965a-e8aac45afeba, mitarbeiter=sb-sim-05, bgId=17748, letzteZahlungId=16486, kollege=sb-sim-19

### rechenwege

block: Kombinationsfall 24: Single
zahlungsmonat = 2026-07
vermoegen = 0.00
schonvermoegen = 10000.00
budget_start = 713.71
p1_eingaben: rbs=1, merkzeichen_g=true, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=450.00, kdu_betrieb=115.00, kdu_heizung=110.00, rente=890.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p1_regelbedarf=563.00
p1_mehrbedarf_g=95.71
p1_mehrbedarf_schwanger=0.00
p1_mehrbedarf_alleinerziehend=0.00
p1_mehrbedarf_warmwasser=0.00
p1_kdu=675.00
p1_gesamtbedarf=1333.71
p1_freibetrag_erwerb=0.00
p1_absetzbetaetrag=0.00
p1_anrechenbar=890.00
p1_zahlbetrag=443.71
bg_gesamtbedarf=1333.71
bg_anrechenbar=890.00
bg_verwertbares_vermoegen=0.00
bg_zahlbetrag=443.71
budget_rest_erwartet=270.00

### verifikation

V1: OK | alle Personen als eigene SozialFall-Zeilen
    sql    = SELECT COUNT(*) FROM SOZIAL_FALL WHERE BG_ID = ?
    params = [17748]
    erwartet = 1 | ist = 1
V2: OK | finale Zahlung wirksam (Vier-Augen-Warteschlange je nach Kassenlauf)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [16486]
    erwartet = ANGELEGT|BEREIT|UEBERGEBEN | ist = ANGELEGT
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [16486]
    erwartet = 443.71 | ist = 443.71
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17748]
    erwartet = 270.00 | ist = 270
V5: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:20:39.849 sb-sim-05 | Vorgang anlegen (Single, Budget 713.71 EUR) -> OK — Vorgang 17748
2026-10-13 11:20:39.863 sb-sim-05 | 1 SozialFall/Fälle erfassen -> OK — Single, KdU 675.00 EUR
2026-10-13 11:20:39.884 sb-sim-05 | Auszahlung anordnen (2026-07) -> OK — 443.71 EUR (ANGELEGT)

## TF 25 KOMBINATION SWEET-0025
status = VERIFIZIERT
hauptperson = Dietrich, Norbert
bearbeiter = sb-sim-03
bg_id = 17749
dauer_ms = 95
ids = zahlungId=16494, vorgangsNr=5217b25d-e468-43cd-87cd-6396c728d863, mitarbeiter=sb-sim-03, bgId=17749, letzteZahlungId=16494, kollege=sb-sim-11

### rechenwege

block: Kombinationsfall 25: Alleinerziehend mit 3 Kind(ern)
zahlungsmonat = 2026-07
vermoegen = 0.00
schonvermoegen = 11500.00
budget_start = 2163.32
p1_eingaben: rbs=1, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=3, kinder_minderjaehrig=3, warmwasser=false, kdu_miete=470.00, kdu_betrieb=130.00, kdu_heizung=165.00, rente=970.00, erwerb=230.00, abz_steuern=200.00, abz_sv=110.00, abz_vers=15.00, abz_erwerb=40.00
p1_regelbedarf=563.00
p1_mehrbedarf_g=0.00
p1_mehrbedarf_schwanger=0.00
p1_mehrbedarf_alleinerziehend=202.68
p1_mehrbedarf_warmwasser=0.00
p1_kdu=765.00
p1_gesamtbedarf=1530.68
p1_freibetrag_erwerb=69.00
p1_absetzbetaetrag=365.00
p1_anrechenbar=766.00
p1_zahlbetrag=764.68
p2_eingaben: rbs=4, merkzeichen_g=true, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=true, kdu_miete=0.00, kdu_betrieb=0.00, kdu_heizung=0.00, rente=150.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p2_regelbedarf=471.00
p2_mehrbedarf_g=80.07
p2_mehrbedarf_schwanger=0.00
p2_mehrbedarf_alleinerziehend=0.00
p2_mehrbedarf_warmwasser=6.59
p2_kdu=0.00
p2_gesamtbedarf=557.66
p2_freibetrag_erwerb=0.00
p2_absetzbetaetrag=0.00
p2_anrechenbar=150.00
p2_zahlbetrag=407.66
p3_eingaben: rbs=4, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=0.00, kdu_betrieb=0.00, kdu_heizung=0.00, rente=0.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p3_regelbedarf=471.00
p3_mehrbedarf_g=0.00
p3_mehrbedarf_schwanger=0.00
p3_mehrbedarf_alleinerziehend=0.00
p3_mehrbedarf_warmwasser=0.00
p3_kdu=0.00
p3_gesamtbedarf=471.00
p3_freibetrag_erwerb=0.00
p3_absetzbetaetrag=0.00
p3_anrechenbar=0.00
p3_zahlbetrag=471.00
p4_eingaben: rbs=5, merkzeichen_g=true, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=true, kdu_miete=0.00, kdu_betrieb=0.00, kdu_heizung=0.00, rente=0.00, erwerb=830.00, abz_steuern=80.00, abz_sv=340.00, abz_vers=30.00, abz_erwerb=40.00
p4_regelbedarf=390.00
p4_mehrbedarf_g=66.30
p4_mehrbedarf_schwanger=0.00
p4_mehrbedarf_alleinerziehend=0.00
p4_mehrbedarf_warmwasser=4.68
p4_kdu=0.00
p4_gesamtbedarf=460.98
p4_freibetrag_erwerb=249.00
p4_absetzbetaetrag=490.00
p4_anrechenbar=91.00
p4_zahlbetrag=369.98
bg_gesamtbedarf=3020.32
bg_anrechenbar=1007.00
bg_verwertbares_vermoegen=0.00
bg_zahlbetrag=2013.32
budget_rest_erwartet=150.00

### verifikation

V1: OK | alle Personen als eigene SozialFall-Zeilen
    sql    = SELECT COUNT(*) FROM SOZIAL_FALL WHERE BG_ID = ?
    params = [17749]
    erwartet = 4 | ist = 4
V2: OK | finale Zahlung freigegeben (oder bereits übergeben)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [16494]
    erwartet = BEREIT|UEBERGEBEN | ist = UEBERGEBEN
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [16494]
    erwartet = 2013.32 | ist = 2013.32
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17749]
    erwartet = 150.00 | ist = 150
V5: OK | Freigabevermerk nennt den Kollegen (Vier-Augen)
    sql    = SELECT CASE WHEN FREIGEGEBEN_VON = ? THEN 'ok' ELSE 'abweichend' END FROM ZAHLUNG WHERE ID = ?
    params = [sb-sim-11, 16494]
    erwartet = ok | ist = ok
V6: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:20:39.850 sb-sim-03 | Vorgang anlegen (Alleinerziehend mit 3 Kind(ern), Budget 2163.32 EUR) -> OK — Vorgang 17749
2026-10-13 11:20:39.902 sb-sim-03 | 4 SozialFall/Fälle erfassen -> OK — Alleinerziehend mit 3 Kind(ern), KdU 765.00 EUR
2026-10-13 11:20:39.922 sb-sim-03 | Auszahlung anordnen (2026-07) -> OK — 2013.32 EUR (ANGELEGT)
2026-10-13 11:20:39.933 sb-sim-03 | Freigabe durch Kollegen (Vier-Augen) -> OK — freigegeben durch sb-sim-11

## TF 26 KOMBINATION SWEET-0026
status = VERIFIZIERT
hauptperson = Dietrich, Heinz
bearbeiter = sb-sim-02
bg_id = 17750
dauer_ms = 159
ids = letzteZahlungId=16518, kollege=sb-sim-10, bgId=17750, mitarbeiter=sb-sim-02, zahlungId=16501, fallId=26799, vorgangsNr=116275e5-c080-4ba8-af35-4d70a06792ae

### rechenwege

block: Kombinationsfall 26: Paar mit 1 Kind(ern) (Schwangerschafts-Mehrbedarf entfällt im Zahlungsmonat)
zahlungsmonat = 2026-08
vermoegen = 0.00
schonvermoegen = 20500.00
budget_start = 2115.00
p1_eingaben: rbs=2, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=430.00, kdu_betrieb=0.00, kdu_heizung=0.00, rente=0.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p1_regelbedarf=506.00
p1_mehrbedarf_g=0.00
p1_mehrbedarf_schwanger=0.00
p1_mehrbedarf_alleinerziehend=0.00
p1_mehrbedarf_warmwasser=0.00
p1_kdu=430.00
p1_gesamtbedarf=936.00
p1_freibetrag_erwerb=0.00
p1_absetzbetaetrag=0.00
p1_anrechenbar=0.00
p1_zahlbetrag=936.00
p2_eingaben: rbs=2, merkzeichen_g=false, schwanger=false, entbindungsmonat=2026-05, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=0.00, kdu_betrieb=95.00, kdu_heizung=40.00, rente=0.00, erwerb=400.00, abz_steuern=10.00, abz_sv=310.00, abz_vers=20.00, abz_erwerb=20.00
p2_regelbedarf=506.00
p2_mehrbedarf_g=0.00
p2_mehrbedarf_schwanger=0.00
p2_mehrbedarf_alleinerziehend=0.00
p2_mehrbedarf_warmwasser=0.00
p2_kdu=135.00
p2_gesamtbedarf=641.00
p2_freibetrag_erwerb=120.00
p2_absetzbetaetrag=360.00
p2_anrechenbar=0.00
p2_zahlbetrag=641.00
p3_eingaben: rbs=6, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=0.00, kdu_betrieb=0.00, kdu_heizung=0.00, rente=0.00, erwerb=770.00, abz_steuern=190.00, abz_sv=160.00, abz_vers=40.00, abz_erwerb=30.00
p3_regelbedarf=357.00
p3_mehrbedarf_g=0.00
p3_mehrbedarf_schwanger=0.00
p3_mehrbedarf_alleinerziehend=0.00
p3_mehrbedarf_warmwasser=0.00
p3_kdu=0.00
p3_gesamtbedarf=357.00
p3_freibetrag_erwerb=231.00
p3_absetzbetaetrag=420.00
p3_anrechenbar=119.00
p3_zahlbetrag=238.00
bg_gesamtbedarf=1934.00
bg_anrechenbar=119.00
bg_verwertbares_vermoegen=0.00
bg_zahlbetrag=1815.00
budget_rest_erwartet=300.00

block: Neuanordnung nach Mietekorrektur (Miete 430.00 → 490.00)
zahlungsmonat = 2026-08
vermoegen = 0.00
schonvermoegen = 20500.00
budget_start = 2115.00
p1_eingaben: rbs=2, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=490.00, kdu_betrieb=0.00, kdu_heizung=0.00, rente=0.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p1_regelbedarf=506.00
p1_mehrbedarf_g=0.00
p1_mehrbedarf_schwanger=0.00
p1_mehrbedarf_alleinerziehend=0.00
p1_mehrbedarf_warmwasser=0.00
p1_kdu=490.00
p1_gesamtbedarf=996.00
p1_freibetrag_erwerb=0.00
p1_absetzbetaetrag=0.00
p1_anrechenbar=0.00
p1_zahlbetrag=996.00
p2_eingaben: rbs=2, merkzeichen_g=false, schwanger=false, entbindungsmonat=2026-05, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=0.00, kdu_betrieb=95.00, kdu_heizung=40.00, rente=0.00, erwerb=400.00, abz_steuern=10.00, abz_sv=310.00, abz_vers=20.00, abz_erwerb=20.00
p2_regelbedarf=506.00
p2_mehrbedarf_g=0.00
p2_mehrbedarf_schwanger=0.00
p2_mehrbedarf_alleinerziehend=0.00
p2_mehrbedarf_warmwasser=0.00
p2_kdu=135.00
p2_gesamtbedarf=641.00
p2_freibetrag_erwerb=120.00
p2_absetzbetaetrag=360.00
p2_anrechenbar=0.00
p2_zahlbetrag=641.00
p3_eingaben: rbs=6, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=0.00, kdu_betrieb=0.00, kdu_heizung=0.00, rente=0.00, erwerb=770.00, abz_steuern=190.00, abz_sv=160.00, abz_vers=40.00, abz_erwerb=30.00
p3_regelbedarf=357.00
p3_mehrbedarf_g=0.00
p3_mehrbedarf_schwanger=0.00
p3_mehrbedarf_alleinerziehend=0.00
p3_mehrbedarf_warmwasser=0.00
p3_kdu=0.00
p3_gesamtbedarf=357.00
p3_freibetrag_erwerb=231.00
p3_absetzbetaetrag=420.00
p3_anrechenbar=119.00
p3_zahlbetrag=238.00
bg_gesamtbedarf=1994.00
bg_anrechenbar=119.00
bg_verwertbares_vermoegen=0.00
bg_zahlbetrag=1875.00
budget_rest_erwartet=240.00

### verifikation

V1: OK | alle Personen als eigene SozialFall-Zeilen
    sql    = SELECT COUNT(*) FROM SOZIAL_FALL WHERE BG_ID = ?
    params = [17750]
    erwartet = 3 | ist = 3
V2: OK | finale Zahlung wirksam (Vier-Augen-Warteschlange je nach Kassenlauf)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [16518]
    erwartet = ANGELEGT|BEREIT|UEBERGEBEN | ist = ANGELEGT
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [16518]
    erwartet = 1875.00 | ist = 1875
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17750]
    erwartet = 240.00 | ist = 240
V5: OK | zwei Zahlungen (stornierte + korrigierte)
    sql    = SELECT COUNT(*) FROM ZAHLUNG WHERE BG_ID = ?
    params = [17750]
    erwartet = 2 | ist = 2
V6: OK | erste Zahlung STORNIERT
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [16501]
    erwartet = STORNIERT | ist = STORNIERT
V7: OK | Fall in Version 2 (optimistisches Locking)
    sql    = SELECT VERSION FROM SOZIAL_FALL WHERE ID = ?
    params = [26799]
    erwartet = 2 | ist = 2
V8: OK | genau ein erteilter Bewilligungsbescheid
    sql    = SELECT COUNT(*) FROM BESCHEID WHERE BG_ID = ?
    params = [17750]
    erwartet = 1 | ist = 1
V9: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:20:39.860 sb-sim-02 | Vorgang anlegen (Paar mit 1 Kind(ern), Budget 2115.00 EUR) -> OK — Vorgang 17750
2026-10-13 11:20:39.899 sb-sim-02 | 3 SozialFall/Fälle erfassen -> OK — Paar mit 1 Kind(ern), KdU 565.00 EUR
2026-10-13 11:20:39.922 sb-sim-02 | Bewilligungsbescheid erteilen (2026-06 .. 2026-09) -> OK — Bescheid BESCH-SWEET-0026-2026-1729 mit Monatsplan gespeichert
2026-10-13 11:20:39.944 sb-sim-02 | Auszahlung anordnen (2026-08) -> OK — 1815.00 EUR (ANGELEGT)
2026-10-13 11:20:39.961 sb-sim-02 | Anordnung sofort stornieren (Status ANGELEGT) -> OK — storniert — Monat wieder frei
2026-10-13 11:20:39.988 sb-sim-02 | Miete auf 490.00 korrigieren -> OK — Fall 26799 in Version 2
2026-10-13 11:20:40.005 sb-sim-02 | Korrigierte Auszahlung für denselben Monat -> OK — 1875.00 EUR

## TF 27 KOMBINATION SWEET-0027
status = VERIFIZIERT
hauptperson = Vogt, Ingrid
bearbeiter = sb-sim-09
bg_id = 17752
dauer_ms = 106
ids = zahlungId=16503, vorgangsNr=212c304a-98b3-4a13-b13b-bef2c7eb171c, mitarbeiter=sb-sim-09, bgId=17752, letzteZahlungId=16503, kollege=sb-sim-07

### rechenwege

block: Kombinationsfall 27: Alleinerziehend mit 2 Kind(ern)
zahlungsmonat = 2026-03
vermoegen = 11900.00
schonvermoegen = 11000.00
budget_start = 1086.32
p1_eingaben: rbs=1, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=2, kinder_minderjaehrig=2, warmwasser=true, kdu_miete=200.00, kdu_betrieb=95.00, kdu_heizung=135.00, rente=170.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p1_regelbedarf=563.00
p1_mehrbedarf_g=0.00
p1_mehrbedarf_schwanger=0.00
p1_mehrbedarf_alleinerziehend=202.68
p1_mehrbedarf_warmwasser=12.95
p1_kdu=430.00
p1_gesamtbedarf=1208.63
p1_freibetrag_erwerb=0.00
p1_absetzbetaetrag=0.00
p1_anrechenbar=170.00
p1_zahlbetrag=1038.63
p2_eingaben: rbs=6, merkzeichen_g=true, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=0.00, kdu_betrieb=0.00, kdu_heizung=0.00, rente=0.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p2_regelbedarf=357.00
p2_mehrbedarf_g=60.69
p2_mehrbedarf_schwanger=0.00
p2_mehrbedarf_alleinerziehend=0.00
p2_mehrbedarf_warmwasser=0.00
p2_kdu=0.00
p2_gesamtbedarf=417.69
p2_freibetrag_erwerb=0.00
p2_absetzbetaetrag=0.00
p2_anrechenbar=0.00
p2_zahlbetrag=417.69
p3_eingaben: rbs=5, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=0.00, kdu_betrieb=0.00, kdu_heizung=0.00, rente=0.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p3_regelbedarf=390.00
p3_mehrbedarf_g=0.00
p3_mehrbedarf_schwanger=0.00
p3_mehrbedarf_alleinerziehend=0.00
p3_mehrbedarf_warmwasser=0.00
p3_kdu=0.00
p3_gesamtbedarf=390.00
p3_freibetrag_erwerb=0.00
p3_absetzbetaetrag=0.00
p3_anrechenbar=0.00
p3_zahlbetrag=390.00
bg_gesamtbedarf=2016.32
bg_anrechenbar=170.00
bg_verwertbares_vermoegen=900.00
bg_zahlbetrag=946.32
budget_rest_erwartet=140.00

### verifikation

V1: OK | alle Personen als eigene SozialFall-Zeilen
    sql    = SELECT COUNT(*) FROM SOZIAL_FALL WHERE BG_ID = ?
    params = [17752]
    erwartet = 3 | ist = 3
V2: OK | finale Zahlung wirksam (Vier-Augen-Warteschlange je nach Kassenlauf)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [16503]
    erwartet = ANGELEGT|BEREIT|UEBERGEBEN | ist = ANGELEGT
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [16503]
    erwartet = 946.32 | ist = 946.32
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17752]
    erwartet = 140.00 | ist = 140
V5: OK | genau ein erteilter Bewilligungsbescheid
    sql    = SELECT COUNT(*) FROM BESCHEID WHERE BG_ID = ?
    params = [17752]
    erwartet = 1 | ist = 1
V6: OK | Vermögen nach Verbrauchsfortschreibung (§ 90)
    sql    = SELECT VERMOEGEN FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17752]
    erwartet = 11000.00 | ist = 11000
V7: OK | dokumentierter Vermögenseinsatz an der Zahlung
    sql    = SELECT VERMOEGENSEINSATZ FROM ZAHLUNG WHERE ID = ?
    params = [16503]
    erwartet = 900.00 | ist = 900
V8: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:20:39.858 sb-sim-09 | Vorgang anlegen (Alleinerziehend mit 2 Kind(ern), Budget 1086.32 EUR, Vermögen 11900.00 EUR) -> OK — Vorgang 17752
2026-10-13 11:20:39.898 sb-sim-09 | 3 SozialFall/Fälle erfassen -> OK — Alleinerziehend mit 2 Kind(ern), KdU 430.00 EUR
2026-10-13 11:20:39.925 sb-sim-09 | Bewilligungsbescheid erteilen (2026-03 .. 2026-07) -> OK — Bescheid BESCH-SWEET-0027-2026-1730 mit Monatsplan gespeichert
2026-10-13 11:20:39.953 sb-sim-09 | Auszahlung anordnen (2026-03) -> OK — 946.32 EUR (ANGELEGT)

## TF 28 KOMBINATION SWEET-0028
status = VERIFIZIERT
hauptperson = Dietrich, Renate
bearbeiter = sb-sim-10
bg_id = 17751
dauer_ms = 118
ids = zahlungId=16504, vorgangsNr=b357d804-7c22-444a-b8e7-48b3753a15b9, mitarbeiter=sb-sim-10, bgId=17751, letzteZahlungId=16504, kollege=sb-sim-15

### rechenwege

block: Kombinationsfall 28: Alleinerziehend mit 2 Kind(ern)
zahlungsmonat = 2026-03
vermoegen = 0.00
schonvermoegen = 11000.00
budget_start = 1781.10
p1_eingaben: rbs=1, merkzeichen_g=true, schwanger=true, entbindungsmonat=2026-04, kinder_u7=1, kinder_u16=2, kinder_minderjaehrig=2, warmwasser=false, kdu_miete=310.00, kdu_betrieb=70.00, kdu_heizung=80.00, rente=300.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p1_regelbedarf=563.00
p1_mehrbedarf_g=95.71
p1_mehrbedarf_schwanger=95.71
p1_mehrbedarf_alleinerziehend=202.68
p1_mehrbedarf_warmwasser=0.00
p1_kdu=460.00
p1_gesamtbedarf=1417.10
p1_freibetrag_erwerb=0.00
p1_absetzbetaetrag=0.00
p1_anrechenbar=300.00
p1_zahlbetrag=1117.10
p2_eingaben: rbs=4, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=0.00, kdu_betrieb=0.00, kdu_heizung=0.00, rente=500.00, erwerb=510.00, abz_steuern=200.00, abz_sv=350.00, abz_vers=20.00, abz_erwerb=10.00
p2_regelbedarf=471.00
p2_mehrbedarf_g=0.00
p2_mehrbedarf_schwanger=0.00
p2_mehrbedarf_alleinerziehend=0.00
p2_mehrbedarf_warmwasser=0.00
p2_kdu=0.00
p2_gesamtbedarf=471.00
p2_freibetrag_erwerb=153.00
p2_absetzbetaetrag=580.00
p2_anrechenbar=277.00
p2_zahlbetrag=194.00
p3_eingaben: rbs=5, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=0.00, kdu_betrieb=0.00, kdu_heizung=0.00, rente=180.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p3_regelbedarf=390.00
p3_mehrbedarf_g=0.00
p3_mehrbedarf_schwanger=0.00
p3_mehrbedarf_alleinerziehend=0.00
p3_mehrbedarf_warmwasser=0.00
p3_kdu=0.00
p3_gesamtbedarf=390.00
p3_freibetrag_erwerb=0.00
p3_absetzbetaetrag=0.00
p3_anrechenbar=180.00
p3_zahlbetrag=210.00
bg_gesamtbedarf=2278.10
bg_anrechenbar=757.00
bg_verwertbares_vermoegen=0.00
bg_zahlbetrag=1521.10
budget_rest_erwartet=260.00

### verifikation

V1: OK | alle Personen als eigene SozialFall-Zeilen
    sql    = SELECT COUNT(*) FROM SOZIAL_FALL WHERE BG_ID = ?
    params = [17751]
    erwartet = 3 | ist = 3
V2: OK | finale Zahlung freigegeben (oder bereits übergeben)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [16504]
    erwartet = BEREIT|UEBERGEBEN | ist = UEBERGEBEN
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [16504]
    erwartet = 1521.10 | ist = 1521.1
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17751]
    erwartet = 260.00 | ist = 260
V5: OK | Freigabevermerk nennt den Kollegen (Vier-Augen)
    sql    = SELECT CASE WHEN FREIGEGEBEN_VON = ? THEN 'ok' ELSE 'abweichend' END FROM ZAHLUNG WHERE ID = ?
    params = [sb-sim-15, 16504]
    erwartet = ok | ist = ok
V6: OK | genau ein erteilter Bewilligungsbescheid
    sql    = SELECT COUNT(*) FROM BESCHEID WHERE BG_ID = ?
    params = [17751]
    erwartet = 1 | ist = 1
V7: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:20:39.858 sb-sim-10 | Vorgang anlegen (Alleinerziehend mit 2 Kind(ern), Budget 1781.10 EUR) -> OK — Vorgang 17751
2026-10-13 11:20:39.894 sb-sim-10 | 3 SozialFall/Fälle erfassen -> OK — Alleinerziehend mit 2 Kind(ern), KdU 460.00 EUR
2026-10-13 11:20:39.929 sb-sim-10 | Bewilligungsbescheid erteilen (2026-01 .. 2026-12) -> OK — Bescheid BESCH-SWEET-0028-2026-1731 mit Monatsplan gespeichert
2026-10-13 11:20:39.953 sb-sim-10 | Auszahlung anordnen (2026-03) -> OK — 1521.10 EUR (ANGELEGT)
2026-10-13 11:20:39.964 sb-sim-10 | Freigabe durch Kollegen (Vier-Augen) -> OK — freigegeben durch sb-sim-15

## TF 29 KOMBINATION SWEET-0029
status = VERIFIZIERT
hauptperson = Dietrich, Anna
bearbeiter = sb-sim-06
bg_id = 17753
dauer_ms = 71
ids = zahlungId=16488, vorgangsNr=a9e65d30-a685-45ed-b27c-10abe914f1ae, mitarbeiter=sb-sim-06, bgId=17753, letzteZahlungId=16488, kollege=sb-sim-15

### rechenwege

block: Kombinationsfall 29: Paar (2 × RBS 2)
zahlungsmonat = 2026-04
vermoegen = 0.00
schonvermoegen = 20000.00
budget_start = 1307.80
p1_eingaben: rbs=2, merkzeichen_g=true, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=true, kdu_miete=310.00, kdu_betrieb=0.00, kdu_heizung=0.00, rente=0.00, erwerb=660.00, abz_steuern=190.00, abz_sv=320.00, abz_vers=35.00, abz_erwerb=20.00
p1_regelbedarf=506.00
p1_mehrbedarf_g=86.02
p1_mehrbedarf_schwanger=0.00
p1_mehrbedarf_alleinerziehend=0.00
p1_mehrbedarf_warmwasser=11.64
p1_kdu=310.00
p1_gesamtbedarf=913.66
p1_freibetrag_erwerb=198.00
p1_absetzbetaetrag=565.00
p1_anrechenbar=0.00
p1_zahlbetrag=913.66
p2_eingaben: rbs=2, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=true, kdu_miete=0.00, kdu_betrieb=35.00, kdu_heizung=75.00, rente=0.00, erwerb=1220.00, abz_steuern=80.00, abz_sv=110.00, abz_vers=25.00, abz_erwerb=100.00
p2_regelbedarf=506.00
p2_mehrbedarf_g=0.00
p2_mehrbedarf_schwanger=0.00
p2_mehrbedarf_alleinerziehend=0.00
p2_mehrbedarf_warmwasser=11.64
p2_kdu=110.00
p2_gesamtbedarf=627.64
p2_freibetrag_erwerb=281.50
p2_absetzbetaetrag=315.00
p2_anrechenbar=623.50
p2_zahlbetrag=4.14
bg_gesamtbedarf=1541.30
bg_anrechenbar=623.50
bg_verwertbares_vermoegen=0.00
bg_zahlbetrag=917.80
budget_rest_erwartet=390.00

### verifikation

V1: OK | alle Personen als eigene SozialFall-Zeilen
    sql    = SELECT COUNT(*) FROM SOZIAL_FALL WHERE BG_ID = ?
    params = [17753]
    erwartet = 2 | ist = 2
V2: OK | finale Zahlung freigegeben (oder bereits übergeben)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [16488]
    erwartet = BEREIT|UEBERGEBEN | ist = UEBERGEBEN
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [16488]
    erwartet = 917.80 | ist = 917.8
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17753]
    erwartet = 390.00 | ist = 390
V5: OK | Freigabevermerk nennt den Kollegen (Vier-Augen)
    sql    = SELECT CASE WHEN FREIGEGEBEN_VON = ? THEN 'ok' ELSE 'abweichend' END FROM ZAHLUNG WHERE ID = ?
    params = [sb-sim-15, 16488]
    erwartet = ok | ist = ok
V6: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:20:39.860 sb-sim-06 | Vorgang anlegen (Paar (2 × RBS 2), Budget 1307.80 EUR) -> OK — Vorgang 17753
2026-10-13 11:20:39.885 sb-sim-06 | 2 SozialFall/Fälle erfassen -> OK — Paar (2 × RBS 2), KdU 420.00 EUR
2026-10-13 11:20:39.906 sb-sim-06 | Auszahlung anordnen (2026-04) -> OK — 917.80 EUR (ANGELEGT)
2026-10-13 11:20:39.922 sb-sim-06 | Freigabe durch Kollegen (Vier-Augen) -> OK — freigegeben durch sb-sim-15

## TF 30 KOMBINATION SWEET-0030
status = VERIFIZIERT
hauptperson = Egger, Frank
bearbeiter = sb-sim-14
bg_id = 17754
dauer_ms = 74
ids = zahlungId=16497, vorgangsNr=4b88a55e-30ff-46b9-a17d-dc6369317b96, mitarbeiter=sb-sim-14, bgId=17754, letzteZahlungId=16497, kollege=sb-sim-16

### rechenwege

block: Kombinationsfall 30: Alleinerziehend mit 2 Kind(ern)
zahlungsmonat = 2026-04
vermoegen = 0.00
schonvermoegen = 11000.00
budget_start = 980.96
p1_eingaben: rbs=1, merkzeichen_g=true, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=2, kinder_minderjaehrig=2, warmwasser=false, kdu_miete=380.00, kdu_betrieb=125.00, kdu_heizung=35.00, rente=0.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p1_regelbedarf=563.00
p1_mehrbedarf_g=95.71
p1_mehrbedarf_schwanger=0.00
p1_mehrbedarf_alleinerziehend=202.68
p1_mehrbedarf_warmwasser=0.00
p1_kdu=540.00
p1_gesamtbedarf=1401.39
p1_freibetrag_erwerb=0.00
p1_absetzbetaetrag=0.00
p1_anrechenbar=0.00
p1_zahlbetrag=1401.39
p2_eingaben: rbs=4, merkzeichen_g=true, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=0.00, kdu_betrieb=0.00, kdu_heizung=0.00, rente=110.00, erwerb=1260.00, abz_steuern=90.00, abz_sv=140.00, abz_vers=60.00, abz_erwerb=0.00
p2_regelbedarf=471.00
p2_mehrbedarf_g=80.07
p2_mehrbedarf_schwanger=0.00
p2_mehrbedarf_alleinerziehend=0.00
p2_mehrbedarf_warmwasser=0.00
p2_kdu=0.00
p2_gesamtbedarf=551.07
p2_freibetrag_erwerb=281.50
p2_absetzbetaetrag=290.00
p2_anrechenbar=798.50
p2_zahlbetrag=-247.43
p3_eingaben: rbs=6, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=0.00, kdu_betrieb=0.00, kdu_heizung=0.00, rente=860.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p3_regelbedarf=357.00
p3_mehrbedarf_g=0.00
p3_mehrbedarf_schwanger=0.00
p3_mehrbedarf_alleinerziehend=0.00
p3_mehrbedarf_warmwasser=0.00
p3_kdu=0.00
p3_gesamtbedarf=357.00
p3_freibetrag_erwerb=0.00
p3_absetzbetaetrag=0.00
p3_anrechenbar=860.00
p3_zahlbetrag=-503.00
bg_gesamtbedarf=2309.46
bg_anrechenbar=1658.50
bg_verwertbares_vermoegen=0.00
bg_zahlbetrag=650.96
budget_rest_erwartet=330.00

### verifikation

V1: OK | alle Personen als eigene SozialFall-Zeilen
    sql    = SELECT COUNT(*) FROM SOZIAL_FALL WHERE BG_ID = ?
    params = [17754]
    erwartet = 3 | ist = 3
V2: OK | finale Zahlung wirksam (Vier-Augen-Warteschlange je nach Kassenlauf)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [16497]
    erwartet = ANGELEGT|BEREIT|UEBERGEBEN | ist = ANGELEGT
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [16497]
    erwartet = 650.96 | ist = 650.96
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17754]
    erwartet = 330.00 | ist = 330
V5: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:20:39.862 sb-sim-14 | Vorgang anlegen (Alleinerziehend mit 2 Kind(ern), Budget 980.96 EUR) -> OK — Vorgang 17754
2026-10-13 11:20:39.906 sb-sim-14 | 3 SozialFall/Fälle erfassen -> OK — Alleinerziehend mit 2 Kind(ern), KdU 540.00 EUR
2026-10-13 11:20:39.925 sb-sim-14 | Auszahlung anordnen (2026-04) -> OK — 650.96 EUR (ANGELEGT)

## TF 31 KOMBINATION SWEET-0031
status = VERIFIZIERT
hauptperson = Uhl, Frank
bearbeiter = sb-sim-01
bg_id = 17755
dauer_ms = 108
ids = zahlungId=16496, vorgangsNr=6a5facc9-1ac9-4c47-ad9f-f2c750219fcc, mitarbeiter=sb-sim-01, bgId=17755, letzteZahlungId=16496, kollege=sb-sim-20

### rechenwege

block: Kombinationsfall 31: Paar mit 1 Kind(ern) (Schwangerschafts-Mehrbedarf entfällt im Zahlungsmonat)
zahlungsmonat = 2026-10
vermoegen = 0.00
schonvermoegen = 20500.00
budget_start = 537.00
p1_eingaben: rbs=2, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=610.00, kdu_betrieb=20.00, kdu_heizung=35.00, rente=830.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p1_regelbedarf=506.00
p1_mehrbedarf_g=0.00
p1_mehrbedarf_schwanger=0.00
p1_mehrbedarf_alleinerziehend=0.00
p1_mehrbedarf_warmwasser=0.00
p1_kdu=665.00
p1_gesamtbedarf=1171.00
p1_freibetrag_erwerb=0.00
p1_absetzbetaetrag=0.00
p1_anrechenbar=830.00
p1_zahlbetrag=341.00
p2_eingaben: rbs=2, merkzeichen_g=false, schwanger=false, entbindungsmonat=2026-07, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=0.00, kdu_betrieb=0.00, kdu_heizung=0.00, rente=850.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p2_regelbedarf=506.00
p2_mehrbedarf_g=0.00
p2_mehrbedarf_schwanger=0.00
p2_mehrbedarf_alleinerziehend=0.00
p2_mehrbedarf_warmwasser=0.00
p2_kdu=0.00
p2_gesamtbedarf=506.00
p2_freibetrag_erwerb=0.00
p2_absetzbetaetrag=0.00
p2_anrechenbar=850.00
p2_zahlbetrag=-344.00
p3_eingaben: rbs=5, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=0.00, kdu_betrieb=0.00, kdu_heizung=0.00, rente=0.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p3_regelbedarf=390.00
p3_mehrbedarf_g=0.00
p3_mehrbedarf_schwanger=0.00
p3_mehrbedarf_alleinerziehend=0.00
p3_mehrbedarf_warmwasser=0.00
p3_kdu=0.00
p3_gesamtbedarf=390.00
p3_freibetrag_erwerb=0.00
p3_absetzbetaetrag=0.00
p3_anrechenbar=0.00
p3_zahlbetrag=390.00
bg_gesamtbedarf=2067.00
bg_anrechenbar=1680.00
bg_verwertbares_vermoegen=0.00
bg_zahlbetrag=387.00
budget_rest_erwartet=150.00

### verifikation

V1: OK | alle Personen als eigene SozialFall-Zeilen
    sql    = SELECT COUNT(*) FROM SOZIAL_FALL WHERE BG_ID = ?
    params = [17755]
    erwartet = 3 | ist = 3
V2: OK | finale Zahlung freigegeben (oder bereits übergeben)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [16496]
    erwartet = BEREIT|UEBERGEBEN | ist = UEBERGEBEN
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [16496]
    erwartet = 387.00 | ist = 387
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17755]
    erwartet = 150.00 | ist = 150
V5: OK | genau eine Zahlung trotz Wiederholung
    sql    = SELECT COUNT(*) FROM ZAHLUNG WHERE BG_ID = ?
    params = [17755]
    erwartet = 1 | ist = 1
V6: OK | Freigabevermerk nennt den Kollegen (Vier-Augen)
    sql    = SELECT CASE WHEN FREIGEGEBEN_VON = ? THEN 'ok' ELSE 'abweichend' END FROM ZAHLUNG WHERE ID = ?
    params = [sb-sim-20, 16496]
    erwartet = ok | ist = ok
V7: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:20:39.864 sb-sim-01 | Vorgang anlegen (Paar mit 1 Kind(ern), Budget 537.00 EUR) -> OK — Vorgang 17755
2026-10-13 11:20:39.904 sb-sim-01 | 3 SozialFall/Fälle erfassen -> OK — Paar mit 1 Kind(ern), KdU 665.00 EUR
2026-10-13 11:20:39.925 sb-sim-01 | Auszahlung anordnen (2026-10) -> OK — 387.00 EUR (ANGELEGT)
2026-10-13 11:20:39.944 sb-sim-01 | Doppelklick: gleiche Vorgangsnummer erneut -> OK — korrekt als bereits gebucht erkannt
2026-10-13 11:20:39.963 sb-sim-01 | Freigabe durch Kollegen (Vier-Augen) -> OK — freigegeben durch sb-sim-20

## TF 32 KOMBINATION SWEET-0032
status = VERIFIZIERT
hauptperson = Brandt, Anna
bearbeiter = sb-sim-07
bg_id = 17756
dauer_ms = 74
ids = zahlungId=16492, vorgangsNr=0677a965-02c2-4934-a890-1acf27757ef1, mitarbeiter=sb-sim-07, bgId=17756, letzteZahlungId=16492, kollege=sb-sim-06

### rechenwege

block: Kombinationsfall 32: Paar (2 × RBS 2) (Schwangerschafts-Mehrbedarf entfällt im Zahlungsmonat)
zahlungsmonat = 2026-05
vermoegen = 20900.00
schonvermoegen = 20000.00
budget_start = 479.00
p1_eingaben: rbs=2, merkzeichen_g=false, schwanger=false, entbindungsmonat=2026-03, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=520.00, kdu_betrieb=55.00, kdu_heizung=170.00, rente=1010.00, erwerb=240.00, abz_steuern=130.00, abz_sv=330.00, abz_vers=50.00, abz_erwerb=60.00
p1_regelbedarf=506.00
p1_mehrbedarf_g=0.00
p1_mehrbedarf_schwanger=0.00
p1_mehrbedarf_alleinerziehend=0.00
p1_mehrbedarf_warmwasser=0.00
p1_kdu=745.00
p1_gesamtbedarf=1251.00
p1_freibetrag_erwerb=72.00
p1_absetzbetaetrag=570.00
p1_anrechenbar=608.00
p1_zahlbetrag=643.00
p2_eingaben: rbs=2, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=0.00, kdu_betrieb=0.00, kdu_heizung=0.00, rente=0.00, erwerb=220.00, abz_steuern=150.00, abz_sv=90.00, abz_vers=10.00, abz_erwerb=20.00
p2_regelbedarf=506.00
p2_mehrbedarf_g=0.00
p2_mehrbedarf_schwanger=0.00
p2_mehrbedarf_alleinerziehend=0.00
p2_mehrbedarf_warmwasser=0.00
p2_kdu=0.00
p2_gesamtbedarf=506.00
p2_freibetrag_erwerb=66.00
p2_absetzbetaetrag=270.00
p2_anrechenbar=0.00
p2_zahlbetrag=506.00
bg_gesamtbedarf=1757.00
bg_anrechenbar=608.00
bg_verwertbares_vermoegen=900.00
bg_zahlbetrag=249.00
budget_rest_erwartet=230.00

### verifikation

V1: OK | alle Personen als eigene SozialFall-Zeilen
    sql    = SELECT COUNT(*) FROM SOZIAL_FALL WHERE BG_ID = ?
    params = [17756]
    erwartet = 2 | ist = 2
V2: OK | finale Zahlung freigegeben (oder bereits übergeben)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [16492]
    erwartet = BEREIT|UEBERGEBEN | ist = UEBERGEBEN
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [16492]
    erwartet = 249.00 | ist = 249
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17756]
    erwartet = 230.00 | ist = 230
V5: OK | Freigabevermerk nennt den Kollegen (Vier-Augen)
    sql    = SELECT CASE WHEN FREIGEGEBEN_VON = ? THEN 'ok' ELSE 'abweichend' END FROM ZAHLUNG WHERE ID = ?
    params = [sb-sim-06, 16492]
    erwartet = ok | ist = ok
V6: OK | Vermögen nach Verbrauchsfortschreibung (§ 90)
    sql    = SELECT VERMOEGEN FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17756]
    erwartet = 20000.00 | ist = 20000
V7: OK | dokumentierter Vermögenseinsatz an der Zahlung
    sql    = SELECT VERMOEGENSEINSATZ FROM ZAHLUNG WHERE ID = ?
    params = [16492]
    erwartet = 900.00 | ist = 900
V8: OK | Wiedervorlagedatum exakt wie gesetzt
    sql    = SELECT COUNT(*) FROM BEDARFSGEMEINSCHAFT WHERE ID = ? AND WIEDERVORLAGE_AM = ?
    params = [17756, 2026-11-20]
    erwartet = 1 | ist = 1
V9: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:20:39.870 sb-sim-07 | Vorgang anlegen (Paar (2 × RBS 2), Budget 479.00 EUR, Vermögen 20900.00 EUR) -> OK — Vorgang 17756
2026-10-13 11:20:39.896 sb-sim-07 | 2 SozialFall/Fälle erfassen -> OK — Paar (2 × RBS 2), KdU 745.00 EUR
2026-10-13 11:20:39.922 sb-sim-07 | Auszahlung anordnen (2026-05) -> OK — 249.00 EUR (ANGELEGT)
2026-10-13 11:20:39.934 sb-sim-07 | Freigabe durch Kollegen (Vier-Augen) -> OK — freigegeben durch sb-sim-06

## TF 33 KOMBINATION SWEET-0033
status = VERIFIZIERT
hauptperson = Brandt, Clara
bearbeiter = sb-sim-20
bg_id = 17758
dauer_ms = 90
ids = zahlungId=16505, vorgangsNr=a3fd5433-aa30-481b-b23e-d95c4ab4b824, mitarbeiter=sb-sim-20, bgId=17758, letzteZahlungId=16505, kollege=sb-sim-11

### rechenwege

block: Kombinationsfall 33: Paar mit 1 Kind(ern)
zahlungsmonat = 2026-08
vermoegen = 0.00
schonvermoegen = 20500.00
budget_start = 1880.46
p1_eingaben: rbs=2, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=true, kdu_miete=620.00, kdu_betrieb=115.00, kdu_heizung=35.00, rente=0.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p1_regelbedarf=506.00
p1_mehrbedarf_g=0.00
p1_mehrbedarf_schwanger=0.00
p1_mehrbedarf_alleinerziehend=0.00
p1_mehrbedarf_warmwasser=11.64
p1_kdu=770.00
p1_gesamtbedarf=1287.64
p1_freibetrag_erwerb=0.00
p1_absetzbetaetrag=0.00
p1_anrechenbar=0.00
p1_zahlbetrag=1287.64
p2_eingaben: rbs=2, merkzeichen_g=true, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=true, kdu_miete=0.00, kdu_betrieb=0.00, kdu_heizung=0.00, rente=330.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p2_regelbedarf=506.00
p2_mehrbedarf_g=86.02
p2_mehrbedarf_schwanger=0.00
p2_mehrbedarf_alleinerziehend=0.00
p2_mehrbedarf_warmwasser=11.64
p2_kdu=0.00
p2_gesamtbedarf=603.66
p2_freibetrag_erwerb=0.00
p2_absetzbetaetrag=0.00
p2_anrechenbar=330.00
p2_zahlbetrag=273.66
p3_eingaben: rbs=4, merkzeichen_g=true, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=true, kdu_miete=0.00, kdu_betrieb=0.00, kdu_heizung=0.00, rente=0.00, erwerb=1060.00, abz_steuern=0.00, abz_sv=220.00, abz_vers=30.00, abz_erwerb=90.00
p3_regelbedarf=471.00
p3_mehrbedarf_g=80.07
p3_mehrbedarf_schwanger=0.00
p3_mehrbedarf_alleinerziehend=0.00
p3_mehrbedarf_warmwasser=6.59
p3_kdu=0.00
p3_gesamtbedarf=557.66
p3_freibetrag_erwerb=281.50
p3_absetzbetaetrag=340.00
p3_anrechenbar=438.50
p3_zahlbetrag=119.16
bg_gesamtbedarf=2448.96
bg_anrechenbar=768.50
bg_verwertbares_vermoegen=0.00
bg_zahlbetrag=1680.46
budget_rest_erwartet=200.00

### verifikation

V1: OK | alle Personen als eigene SozialFall-Zeilen
    sql    = SELECT COUNT(*) FROM SOZIAL_FALL WHERE BG_ID = ?
    params = [17758]
    erwartet = 3 | ist = 3
V2: OK | finale Zahlung wirksam (Vier-Augen-Warteschlange je nach Kassenlauf)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [16505]
    erwartet = ANGELEGT|BEREIT|UEBERGEBEN | ist = ANGELEGT
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [16505]
    erwartet = 1680.46 | ist = 1680.46
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17758]
    erwartet = 200.00 | ist = 200
V5: OK | genau ein erteilter Bewilligungsbescheid
    sql    = SELECT COUNT(*) FROM BESCHEID WHERE BG_ID = ?
    params = [17758]
    erwartet = 1 | ist = 1
V6: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:20:39.874 sb-sim-20 | Vorgang anlegen (Paar mit 1 Kind(ern), Budget 1880.46 EUR) -> OK — Vorgang 17758
2026-10-13 11:20:39.912 sb-sim-20 | 3 SozialFall/Fälle erfassen -> OK — Paar mit 1 Kind(ern), KdU 770.00 EUR
2026-10-13 11:20:39.931 sb-sim-20 | Bewilligungsbescheid erteilen (2026-05 .. 2026-08) -> OK — Bescheid BESCH-SWEET-0033-2026-1732 mit Monatsplan gespeichert
2026-10-13 11:20:39.953 sb-sim-20 | Auszahlung anordnen (2026-08) -> OK — 1680.46 EUR (ANGELEGT)

## TF 34 KOMBINATION SWEET-0034
status = VERIFIZIERT
hauptperson = Zimmer, Ingrid
bearbeiter = sb-sim-04
bg_id = 17757
dauer_ms = 65
ids = zahlungId=16498, vorgangsNr=5e6c6a5e-ffac-4883-9b06-94ff0d0f8d00, mitarbeiter=sb-sim-04, bgId=17757, letzteZahlungId=16498, kollege=sb-sim-16

### rechenwege

block: Kombinationsfall 34: Alleinerziehend mit 2 Kind(ern)
zahlungsmonat = 2026-07
vermoegen = 0.00
schonvermoegen = 11000.00
budget_start = 798.44
p1_eingaben: rbs=1, merkzeichen_g=false, schwanger=true, entbindungsmonat=2026-12, kinder_u7=1, kinder_u16=2, kinder_minderjaehrig=2, warmwasser=false, kdu_miete=350.00, kdu_betrieb=20.00, kdu_heizung=60.00, rente=0.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p1_regelbedarf=563.00
p1_mehrbedarf_g=0.00
p1_mehrbedarf_schwanger=95.71
p1_mehrbedarf_alleinerziehend=202.68
p1_mehrbedarf_warmwasser=0.00
p1_kdu=430.00
p1_gesamtbedarf=1291.39
p1_freibetrag_erwerb=0.00
p1_absetzbetaetrag=0.00
p1_anrechenbar=0.00
p1_zahlbetrag=1291.39
p2_eingaben: rbs=5, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=0.00, kdu_betrieb=0.00, kdu_heizung=0.00, rente=620.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p2_regelbedarf=390.00
p2_mehrbedarf_g=0.00
p2_mehrbedarf_schwanger=0.00
p2_mehrbedarf_alleinerziehend=0.00
p2_mehrbedarf_warmwasser=0.00
p2_kdu=0.00
p2_gesamtbedarf=390.00
p2_freibetrag_erwerb=0.00
p2_absetzbetaetrag=0.00
p2_anrechenbar=620.00
p2_zahlbetrag=-230.00
p3_eingaben: rbs=6, merkzeichen_g=true, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=true, kdu_miete=0.00, kdu_betrieb=0.00, kdu_heizung=0.00, rente=430.00, erwerb=1170.00, abz_steuern=110.00, abz_sv=350.00, abz_vers=25.00, abz_erwerb=90.00
p3_regelbedarf=357.00
p3_mehrbedarf_g=60.69
p3_mehrbedarf_schwanger=0.00
p3_mehrbedarf_alleinerziehend=0.00
p3_mehrbedarf_warmwasser=2.86
p3_kdu=0.00
p3_gesamtbedarf=420.55
p3_freibetrag_erwerb=281.50
p3_absetzbetaetrag=575.00
p3_anrechenbar=743.50
p3_zahlbetrag=-322.95
bg_gesamtbedarf=2101.94
bg_anrechenbar=1363.50
bg_verwertbares_vermoegen=0.00
bg_zahlbetrag=738.44
budget_rest_erwartet=60.00

### verifikation

V1: OK | alle Personen als eigene SozialFall-Zeilen
    sql    = SELECT COUNT(*) FROM SOZIAL_FALL WHERE BG_ID = ?
    params = [17757]
    erwartet = 3 | ist = 3
V2: OK | finale Zahlung wirksam (Vier-Augen-Warteschlange je nach Kassenlauf)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [16498]
    erwartet = ANGELEGT|BEREIT|UEBERGEBEN | ist = ANGELEGT
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [16498]
    erwartet = 738.44 | ist = 738.44
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17757]
    erwartet = 60.00 | ist = 60
V5: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:20:39.874 sb-sim-04 | Vorgang anlegen (Alleinerziehend mit 2 Kind(ern), Budget 798.44 EUR) -> OK — Vorgang 17757
2026-10-13 11:20:39.910 sb-sim-04 | 3 SozialFall/Fälle erfassen -> OK — Alleinerziehend mit 2 Kind(ern), KdU 430.00 EUR
2026-10-13 11:20:39.928 sb-sim-04 | Auszahlung anordnen (2026-07) -> OK — 738.44 EUR (ANGELEGT)

## TF 35 KOMBINATION SWEET-0035
status = VERIFIZIERT
hauptperson = Ismail, Willi
bearbeiter = sb-sim-19
bg_id = 17759
dauer_ms = 78
ids = zahlungId=16495, vorgangsNr=d74da52b-3b73-4ef0-ac2f-c0b5018fadf5, mitarbeiter=sb-sim-19, bgId=17759, letzteZahlungId=16495, kollege=sb-sim-08

### rechenwege

block: Kombinationsfall 35: Paar (2 × RBS 2) (Schwangerschafts-Mehrbedarf entfällt im Zahlungsmonat)
zahlungsmonat = 2026-07
vermoegen = 0.00
schonvermoegen = 20000.00
budget_start = 599.00
p1_eingaben: rbs=2, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=630.00, kdu_betrieb=90.00, kdu_heizung=60.00, rente=320.00, erwerb=540.00, abz_steuern=60.00, abz_sv=100.00, abz_vers=15.00, abz_erwerb=40.00
p1_regelbedarf=506.00
p1_mehrbedarf_g=0.00
p1_mehrbedarf_schwanger=0.00
p1_mehrbedarf_alleinerziehend=0.00
p1_mehrbedarf_warmwasser=0.00
p1_kdu=780.00
p1_gesamtbedarf=1286.00
p1_freibetrag_erwerb=162.00
p1_absetzbetaetrag=215.00
p1_anrechenbar=483.00
p1_zahlbetrag=803.00
p2_eingaben: rbs=2, merkzeichen_g=false, schwanger=false, entbindungsmonat=2026-06, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=0.00, kdu_betrieb=0.00, kdu_heizung=0.00, rente=820.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p2_regelbedarf=506.00
p2_mehrbedarf_g=0.00
p2_mehrbedarf_schwanger=0.00
p2_mehrbedarf_alleinerziehend=0.00
p2_mehrbedarf_warmwasser=0.00
p2_kdu=0.00
p2_gesamtbedarf=506.00
p2_freibetrag_erwerb=0.00
p2_absetzbetaetrag=0.00
p2_anrechenbar=820.00
p2_zahlbetrag=-314.00
bg_gesamtbedarf=1792.00
bg_anrechenbar=1303.00
bg_verwertbares_vermoegen=0.00
bg_zahlbetrag=489.00
budget_rest_erwartet=110.00

### verifikation

V1: OK | alle Personen als eigene SozialFall-Zeilen
    sql    = SELECT COUNT(*) FROM SOZIAL_FALL WHERE BG_ID = ?
    params = [17759]
    erwartet = 2 | ist = 2
V2: OK | finale Zahlung wirksam (Vier-Augen-Warteschlange je nach Kassenlauf)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [16495]
    erwartet = ANGELEGT|BEREIT|UEBERGEBEN | ist = ANGELEGT
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [16495]
    erwartet = 489.00 | ist = 489
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17759]
    erwartet = 110.00 | ist = 110
V5: OK | genau eine Zahlung trotz Wiederholung
    sql    = SELECT COUNT(*) FROM ZAHLUNG WHERE BG_ID = ?
    params = [17759]
    erwartet = 1 | ist = 1
V6: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:20:39.877 sb-sim-19 | Vorgang anlegen (Paar (2 × RBS 2), Budget 599.00 EUR) -> OK — Vorgang 17759
2026-10-13 11:20:39.904 sb-sim-19 | 2 SozialFall/Fälle erfassen -> OK — Paar (2 × RBS 2), KdU 780.00 EUR
2026-10-13 11:20:39.925 sb-sim-19 | Auszahlung anordnen (2026-07) -> OK — 489.00 EUR (ANGELEGT)
2026-10-13 11:20:39.944 sb-sim-19 | Doppelklick: gleiche Vorgangsnummer erneut -> OK — korrekt als bereits gebucht erkannt

## TF 36 KOMBINATION SWEET-0036
status = VERIFIZIERT
hauptperson = Lindner, Volkmar
bearbeiter = sb-sim-18
bg_id = 17760
dauer_ms = 111
ids = zahlungId=16507, vorgangsNr=091c11f6-e573-4095-9130-015a254c69f2, mitarbeiter=sb-sim-18, bgId=17760, letzteZahlungId=16507, kollege=sb-sim-02

### rechenwege

block: Kombinationsfall 36: Paar mit 1 Kind(ern)
zahlungsmonat = 2026-03
vermoegen = 21050.00
schonvermoegen = 20500.00
budget_start = 936.99
p1_eingaben: rbs=2, merkzeichen_g=true, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=true, kdu_miete=290.00, kdu_betrieb=0.00, kdu_heizung=0.00, rente=350.00, erwerb=440.00, abz_steuern=180.00, abz_sv=310.00, abz_vers=25.00, abz_erwerb=30.00
p1_regelbedarf=506.00
p1_mehrbedarf_g=86.02
p1_mehrbedarf_schwanger=0.00
p1_mehrbedarf_alleinerziehend=0.00
p1_mehrbedarf_warmwasser=11.64
p1_kdu=290.00
p1_gesamtbedarf=893.66
p1_freibetrag_erwerb=132.00
p1_absetzbetaetrag=545.00
p1_anrechenbar=113.00
p1_zahlbetrag=780.66
p2_eingaben: rbs=2, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=true, kdu_miete=0.00, kdu_betrieb=120.00, kdu_heizung=40.00, rente=0.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p2_regelbedarf=506.00
p2_mehrbedarf_g=0.00
p2_mehrbedarf_schwanger=0.00
p2_mehrbedarf_alleinerziehend=0.00
p2_mehrbedarf_warmwasser=11.64
p2_kdu=160.00
p2_gesamtbedarf=677.64
p2_freibetrag_erwerb=0.00
p2_absetzbetaetrag=0.00
p2_anrechenbar=0.00
p2_zahlbetrag=677.64
p3_eingaben: rbs=6, merkzeichen_g=true, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=0.00, kdu_betrieb=0.00, kdu_heizung=0.00, rente=880.00, erwerb=220.00, abz_steuern=110.00, abz_sv=210.00, abz_vers=65.00, abz_erwerb=50.00
p3_regelbedarf=357.00
p3_mehrbedarf_g=60.69
p3_mehrbedarf_schwanger=0.00
p3_mehrbedarf_alleinerziehend=0.00
p3_mehrbedarf_warmwasser=0.00
p3_kdu=0.00
p3_gesamtbedarf=417.69
p3_freibetrag_erwerb=66.00
p3_absetzbetaetrag=435.00
p3_anrechenbar=599.00
p3_zahlbetrag=-181.31
bg_gesamtbedarf=1988.99
bg_anrechenbar=712.00
bg_verwertbares_vermoegen=550.00
bg_zahlbetrag=726.99
budget_rest_erwartet=210.00

### verifikation

V1: OK | alle Personen als eigene SozialFall-Zeilen
    sql    = SELECT COUNT(*) FROM SOZIAL_FALL WHERE BG_ID = ?
    params = [17760]
    erwartet = 3 | ist = 3
V2: OK | finale Zahlung wirksam (Vier-Augen-Warteschlange je nach Kassenlauf)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [16507]
    erwartet = ANGELEGT|BEREIT|UEBERGEBEN | ist = ANGELEGT
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [16507]
    erwartet = 726.99 | ist = 726.99
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17760]
    erwartet = 210.00 | ist = 210
V5: OK | genau ein erteilter Bewilligungsbescheid
    sql    = SELECT COUNT(*) FROM BESCHEID WHERE BG_ID = ?
    params = [17760]
    erwartet = 1 | ist = 1
V6: OK | Vermögen nach Verbrauchsfortschreibung (§ 90)
    sql    = SELECT VERMOEGEN FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17760]
    erwartet = 20500.00 | ist = 20500
V7: OK | dokumentierter Vermögenseinsatz an der Zahlung
    sql    = SELECT VERMOEGENSEINSATZ FROM ZAHLUNG WHERE ID = ?
    params = [16507]
    erwartet = 550.00 | ist = 550
V8: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:20:39.880 sb-sim-18 | Vorgang anlegen (Paar mit 1 Kind(ern), Budget 936.99 EUR, Vermögen 21050.00 EUR) -> OK — Vorgang 17760
2026-10-13 11:20:39.922 sb-sim-18 | 3 SozialFall/Fälle erfassen -> OK — Paar mit 1 Kind(ern), KdU 450.00 EUR
2026-10-13 11:20:39.953 sb-sim-18 | Bewilligungsbescheid erteilen (2026-01 .. 2026-05) -> OK — Bescheid BESCH-SWEET-0036-2026-1733 mit Monatsplan gespeichert
2026-10-13 11:20:39.978 sb-sim-18 | Auszahlung anordnen (2026-03) -> OK — 726.99 EUR (ANGELEGT)

## TF 37 KOMBINATION SWEET-0037
status = VERIFIZIERT
hauptperson = Brandt, Renate
bearbeiter = sb-sim-05
bg_id = 17761
dauer_ms = 60
ids = zahlungId=16500, vorgangsNr=30af40ea-f02a-4796-8f39-0167e91986cb, mitarbeiter=sb-sim-05, bgId=17761, letzteZahlungId=16500, kollege=sb-sim-13

### rechenwege

block: Kombinationsfall 37: Alleinerziehend mit 1 Kind(ern)
zahlungsmonat = 2026-02
vermoegen = 0.00
schonvermoegen = 10500.00
budget_start = 1756.07
p1_eingaben: rbs=1, merkzeichen_g=false, schwanger=true, entbindungsmonat=2026-06, kinder_u7=1, kinder_u16=1, kinder_minderjaehrig=1, warmwasser=false, kdu_miete=300.00, kdu_betrieb=40.00, kdu_heizung=60.00, rente=0.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p1_regelbedarf=563.00
p1_mehrbedarf_g=0.00
p1_mehrbedarf_schwanger=95.71
p1_mehrbedarf_alleinerziehend=202.68
p1_mehrbedarf_warmwasser=0.00
p1_kdu=400.00
p1_gesamtbedarf=1261.39
p1_freibetrag_erwerb=0.00
p1_absetzbetaetrag=0.00
p1_anrechenbar=0.00
p1_zahlbetrag=1261.39
p2_eingaben: rbs=5, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=true, kdu_miete=0.00, kdu_betrieb=0.00, kdu_heizung=0.00, rente=0.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p2_regelbedarf=390.00
p2_mehrbedarf_g=0.00
p2_mehrbedarf_schwanger=0.00
p2_mehrbedarf_alleinerziehend=0.00
p2_mehrbedarf_warmwasser=4.68
p2_kdu=0.00
p2_gesamtbedarf=394.68
p2_freibetrag_erwerb=0.00
p2_absetzbetaetrag=0.00
p2_anrechenbar=0.00
p2_zahlbetrag=394.68
bg_gesamtbedarf=1656.07
bg_anrechenbar=0.00
bg_verwertbares_vermoegen=0.00
bg_zahlbetrag=1656.07
budget_rest_erwartet=100.00

### verifikation

V1: OK | alle Personen als eigene SozialFall-Zeilen
    sql    = SELECT COUNT(*) FROM SOZIAL_FALL WHERE BG_ID = ?
    params = [17761]
    erwartet = 2 | ist = 2
V2: OK | finale Zahlung wirksam (Vier-Augen-Warteschlange je nach Kassenlauf)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [16500]
    erwartet = ANGELEGT|BEREIT|UEBERGEBEN | ist = ANGELEGT
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [16500]
    erwartet = 1656.07 | ist = 1656.07
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17761]
    erwartet = 100.00 | ist = 100
V5: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:20:39.894 sb-sim-05 | Vorgang anlegen (Alleinerziehend mit 1 Kind(ern), Budget 1756.07 EUR) -> OK — Vorgang 17761
2026-10-13 11:20:39.922 sb-sim-05 | 2 SozialFall/Fälle erfassen -> OK — Alleinerziehend mit 1 Kind(ern), KdU 400.00 EUR
2026-10-13 11:20:39.944 sb-sim-05 | Auszahlung anordnen (2026-02) -> OK — 1656.07 EUR (ANGELEGT)

## TF 38 KOMBINATION SWEET-0038
status = VERIFIZIERT
hauptperson = Meyer, Frank
bearbeiter = sb-sim-16
bg_id = 17762
dauer_ms = 60
ids = zahlungId=16502, vorgangsNr=5e897d42-6d49-4d13-91ce-8ce2e8f78dd1, mitarbeiter=sb-sim-16, bgId=17762, letzteZahlungId=16502, kollege=sb-sim-18

### rechenwege

block: Kombinationsfall 38: Alleinerziehend mit 1 Kind(ern)
zahlungsmonat = 2026-07
vermoegen = 0.00
schonvermoegen = 10500.00
budget_start = 2181.98
p1_eingaben: rbs=1, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=1, kinder_u16=1, kinder_minderjaehrig=1, warmwasser=false, kdu_miete=720.00, kdu_betrieb=55.00, kdu_heizung=135.00, rente=350.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p1_regelbedarf=563.00
p1_mehrbedarf_g=0.00
p1_mehrbedarf_schwanger=0.00
p1_mehrbedarf_alleinerziehend=202.68
p1_mehrbedarf_warmwasser=0.00
p1_kdu=910.00
p1_gesamtbedarf=1675.68
p1_freibetrag_erwerb=0.00
p1_absetzbetaetrag=0.00
p1_anrechenbar=350.00
p1_zahlbetrag=1325.68
p2_eingaben: rbs=5, merkzeichen_g=true, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=0.00, kdu_betrieb=0.00, kdu_heizung=0.00, rente=0.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p2_regelbedarf=390.00
p2_mehrbedarf_g=66.30
p2_mehrbedarf_schwanger=0.00
p2_mehrbedarf_alleinerziehend=0.00
p2_mehrbedarf_warmwasser=0.00
p2_kdu=0.00
p2_gesamtbedarf=456.30
p2_freibetrag_erwerb=0.00
p2_absetzbetaetrag=0.00
p2_anrechenbar=0.00
p2_zahlbetrag=456.30
bg_gesamtbedarf=2131.98
bg_anrechenbar=350.00
bg_verwertbares_vermoegen=0.00
bg_zahlbetrag=1781.98
budget_rest_erwartet=400.00

### verifikation

V1: OK | alle Personen als eigene SozialFall-Zeilen
    sql    = SELECT COUNT(*) FROM SOZIAL_FALL WHERE BG_ID = ?
    params = [17762]
    erwartet = 2 | ist = 2
V2: OK | finale Zahlung wirksam (Vier-Augen-Warteschlange je nach Kassenlauf)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [16502]
    erwartet = ANGELEGT|BEREIT|UEBERGEBEN | ist = ANGELEGT
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [16502]
    erwartet = 1781.98 | ist = 1781.98
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17762]
    erwartet = 400.00 | ist = 400
V5: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:20:39.897 sb-sim-16 | Vorgang anlegen (Alleinerziehend mit 1 Kind(ern), Budget 2181.98 EUR) -> OK — Vorgang 17762
2026-10-13 11:20:39.925 sb-sim-16 | 2 SozialFall/Fälle erfassen -> OK — Alleinerziehend mit 1 Kind(ern), KdU 910.00 EUR
2026-10-13 11:20:39.946 sb-sim-16 | Auszahlung anordnen (2026-07) -> OK — 1781.98 EUR (ANGELEGT)

## TF 39 KOMBINATION SWEET-0039
status = VERIFIZIERT
hauptperson = Ismail, Clara
bearbeiter = sb-sim-13
bg_id = 17763
dauer_ms = 86
ids = zahlungId=16506, vorgangsNr=1bf2d088-a33a-4ada-bce9-6ddcab983f04, mitarbeiter=sb-sim-13, bgId=17763, letzteZahlungId=16506, kollege=sb-sim-19

### rechenwege

block: Kombinationsfall 39: Paar (2 × RBS 2) (Schwangerschafts-Mehrbedarf entfällt im Zahlungsmonat)
zahlungsmonat = 2026-11
vermoegen = 20900.00
schonvermoegen = 20000.00
budget_start = 298.02
p1_eingaben: rbs=2, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=640.00, kdu_betrieb=115.00, kdu_heizung=65.00, rente=0.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p1_regelbedarf=506.00
p1_mehrbedarf_g=0.00
p1_mehrbedarf_schwanger=0.00
p1_mehrbedarf_alleinerziehend=0.00
p1_mehrbedarf_warmwasser=0.00
p1_kdu=820.00
p1_gesamtbedarf=1326.00
p1_freibetrag_erwerb=0.00
p1_absetzbetaetrag=0.00
p1_anrechenbar=0.00
p1_zahlbetrag=1326.00
p2_eingaben: rbs=2, merkzeichen_g=true, schwanger=false, entbindungsmonat=2026-09, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=0.00, kdu_betrieb=0.00, kdu_heizung=0.00, rente=920.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p2_regelbedarf=506.00
p2_mehrbedarf_g=86.02
p2_mehrbedarf_schwanger=0.00
p2_mehrbedarf_alleinerziehend=0.00
p2_mehrbedarf_warmwasser=0.00
p2_kdu=0.00
p2_gesamtbedarf=592.02
p2_freibetrag_erwerb=0.00
p2_absetzbetaetrag=0.00
p2_anrechenbar=920.00
p2_zahlbetrag=-327.98
bg_gesamtbedarf=1918.02
bg_anrechenbar=920.00
bg_verwertbares_vermoegen=900.00
bg_zahlbetrag=98.02
budget_rest_erwartet=200.00

### verifikation

V1: OK | alle Personen als eigene SozialFall-Zeilen
    sql    = SELECT COUNT(*) FROM SOZIAL_FALL WHERE BG_ID = ?
    params = [17763]
    erwartet = 2 | ist = 2
V2: OK | finale Zahlung wirksam (Vier-Augen-Warteschlange je nach Kassenlauf)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [16506]
    erwartet = ANGELEGT|BEREIT|UEBERGEBEN | ist = ANGELEGT
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [16506]
    erwartet = 98.02 | ist = 98.02
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17763]
    erwartet = 200.00 | ist = 200
V5: OK | genau ein erteilter Bewilligungsbescheid
    sql    = SELECT COUNT(*) FROM BESCHEID WHERE BG_ID = ?
    params = [17763]
    erwartet = 1 | ist = 1
V6: OK | Vermögen nach Verbrauchsfortschreibung (§ 90)
    sql    = SELECT VERMOEGEN FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17763]
    erwartet = 20000.00 | ist = 20000
V7: OK | dokumentierter Vermögenseinsatz an der Zahlung
    sql    = SELECT VERMOEGENSEINSATZ FROM ZAHLUNG WHERE ID = ?
    params = [16506]
    erwartet = 900.00 | ist = 900
V8: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:20:39.899 sb-sim-13 | Vorgang anlegen (Paar (2 × RBS 2), Budget 298.02 EUR, Vermögen 20900.00 EUR) -> OK — Vorgang 17763
2026-10-13 11:20:39.925 sb-sim-13 | 2 SozialFall/Fälle erfassen -> OK — Paar (2 × RBS 2), KdU 820.00 EUR
2026-10-13 11:20:39.947 sb-sim-13 | Bewilligungsbescheid erteilen (2026-10 .. 2026-11) -> OK — Bescheid BESCH-SWEET-0039-2026-1734 mit Monatsplan gespeichert
2026-10-13 11:20:39.975 sb-sim-13 | Auszahlung anordnen (2026-11) -> OK — 98.02 EUR (ANGELEGT)

## TF 40 KOMBINATION SWEET-0040
status = VERIFIZIERT
hauptperson = Brandt, Marlene
bearbeiter = sb-sim-15
bg_id = 17764
dauer_ms = 102
ids = zahlungId=16511, vorgangsNr=957fe9bc-b949-48bc-8a2a-de8c2e4abcbe, mitarbeiter=sb-sim-15, bgId=17764, letzteZahlungId=16511, kollege=sb-sim-05

### rechenwege

block: Kombinationsfall 40: Alleinerziehend mit 1 Kind(ern)
zahlungsmonat = 2026-08
vermoegen = 11100.00
schonvermoegen = 10500.00
budget_start = 689.63
p1_eingaben: rbs=1, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=1, kinder_u16=1, kinder_minderjaehrig=1, warmwasser=true, kdu_miete=360.00, kdu_betrieb=140.00, kdu_heizung=80.00, rente=120.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p1_regelbedarf=563.00
p1_mehrbedarf_g=0.00
p1_mehrbedarf_schwanger=0.00
p1_mehrbedarf_alleinerziehend=202.68
p1_mehrbedarf_warmwasser=12.95
p1_kdu=580.00
p1_gesamtbedarf=1358.63
p1_freibetrag_erwerb=0.00
p1_absetzbetaetrag=0.00
p1_anrechenbar=120.00
p1_zahlbetrag=1238.63
p2_eingaben: rbs=4, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=0.00, kdu_betrieb=0.00, kdu_heizung=0.00, rente=670.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p2_regelbedarf=471.00
p2_mehrbedarf_g=0.00
p2_mehrbedarf_schwanger=0.00
p2_mehrbedarf_alleinerziehend=0.00
p2_mehrbedarf_warmwasser=0.00
p2_kdu=0.00
p2_gesamtbedarf=471.00
p2_freibetrag_erwerb=0.00
p2_absetzbetaetrag=0.00
p2_anrechenbar=670.00
p2_zahlbetrag=-199.00
bg_gesamtbedarf=1829.63
bg_anrechenbar=790.00
bg_verwertbares_vermoegen=600.00
bg_zahlbetrag=439.63
budget_rest_erwartet=250.00

### verifikation

V1: OK | alle Personen als eigene SozialFall-Zeilen
    sql    = SELECT COUNT(*) FROM SOZIAL_FALL WHERE BG_ID = ?
    params = [17764]
    erwartet = 2 | ist = 2
V2: OK | finale Zahlung freigegeben (oder bereits übergeben)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [16511]
    erwartet = BEREIT|UEBERGEBEN | ist = UEBERGEBEN
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [16511]
    erwartet = 439.63 | ist = 439.63
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17764]
    erwartet = 250.00 | ist = 250
V5: OK | Freigabevermerk nennt den Kollegen (Vier-Augen)
    sql    = SELECT CASE WHEN FREIGEGEBEN_VON = ? THEN 'ok' ELSE 'abweichend' END FROM ZAHLUNG WHERE ID = ?
    params = [sb-sim-05, 16511]
    erwartet = ok | ist = ok
V6: OK | genau ein erteilter Bewilligungsbescheid
    sql    = SELECT COUNT(*) FROM BESCHEID WHERE BG_ID = ?
    params = [17764]
    erwartet = 1 | ist = 1
V7: OK | Vermögen nach Verbrauchsfortschreibung (§ 90)
    sql    = SELECT VERMOEGEN FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17764]
    erwartet = 10500.00 | ist = 10500
V8: OK | dokumentierter Vermögenseinsatz an der Zahlung
    sql    = SELECT VERMOEGENSEINSATZ FROM ZAHLUNG WHERE ID = ?
    params = [16511]
    erwartet = 600.00 | ist = 600
V9: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:20:39.922 sb-sim-15 | Vorgang anlegen (Alleinerziehend mit 1 Kind(ern), Budget 689.63 EUR, Vermögen 11100.00 EUR) -> OK — Vorgang 17764
2026-10-13 11:20:39.947 sb-sim-15 | 2 SozialFall/Fälle erfassen -> OK — Alleinerziehend mit 1 Kind(ern), KdU 580.00 EUR
2026-10-13 11:20:39.976 sb-sim-15 | Bewilligungsbescheid erteilen (2026-05 .. 2026-10) -> OK — Bescheid BESCH-SWEET-0040-2026-1735 mit Monatsplan gespeichert
2026-10-13 11:20:40.001 sb-sim-15 | Auszahlung anordnen (2026-08) -> OK — 439.63 EUR (ANGELEGT)
2026-10-13 11:20:40.012 sb-sim-15 | Freigabe durch Kollegen (Vier-Augen) -> OK — freigegeben durch sb-sim-05

## TF 41 KOMBINATION SWEET-0041
status = VERIFIZIERT
hauptperson = Petersen, Elke
bearbeiter = sb-sim-08
bg_id = 17765
dauer_ms = 103
ids = zahlungId=16519, vorgangsNr=5a8edf31-ca92-48aa-a732-fec33b75e383, mitarbeiter=sb-sim-08, bgId=17765, letzteZahlungId=16519, kollege=sb-sim-01

### rechenwege

block: Kombinationsfall 41: Paar (2 × RBS 2) (Schwangerschafts-Mehrbedarf entfällt im Zahlungsmonat)
zahlungsmonat = 2026-02
vermoegen = 0.00
schonvermoegen = 20000.00
budget_start = 790.14
p1_eingaben: rbs=2, merkzeichen_g=false, schwanger=false, entbindungsmonat=2026-01, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=490.00, kdu_betrieb=120.00, kdu_heizung=55.00, rente=0.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p1_regelbedarf=506.00
p1_mehrbedarf_g=0.00
p1_mehrbedarf_schwanger=0.00
p1_mehrbedarf_alleinerziehend=0.00
p1_mehrbedarf_warmwasser=0.00
p1_kdu=665.00
p1_gesamtbedarf=1171.00
p1_freibetrag_erwerb=0.00
p1_absetzbetaetrag=0.00
p1_anrechenbar=0.00
p1_zahlbetrag=1171.00
p2_eingaben: rbs=2, merkzeichen_g=false, schwanger=false, entbindungsmonat=2026-01, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=true, kdu_miete=0.00, kdu_betrieb=0.00, kdu_heizung=0.00, rente=510.00, erwerb=1090.00, abz_steuern=0.00, abz_sv=70.00, abz_vers=40.00, abz_erwerb=100.00
p2_regelbedarf=506.00
p2_mehrbedarf_g=0.00
p2_mehrbedarf_schwanger=0.00
p2_mehrbedarf_alleinerziehend=0.00
p2_mehrbedarf_warmwasser=11.64
p2_kdu=0.00
p2_gesamtbedarf=517.64
p2_freibetrag_erwerb=281.50
p2_absetzbetaetrag=210.00
p2_anrechenbar=1108.50
p2_zahlbetrag=-590.86
bg_gesamtbedarf=1688.64
bg_anrechenbar=1108.50
bg_verwertbares_vermoegen=0.00
bg_zahlbetrag=580.14
budget_rest_erwartet=210.00

### verifikation

V1: OK | alle Personen als eigene SozialFall-Zeilen
    sql    = SELECT COUNT(*) FROM SOZIAL_FALL WHERE BG_ID = ?
    params = [17765]
    erwartet = 2 | ist = 2
V2: OK | finale Zahlung wirksam (Vier-Augen-Warteschlange je nach Kassenlauf)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [16519]
    erwartet = ANGELEGT|BEREIT|UEBERGEBEN | ist = ANGELEGT
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [16519]
    erwartet = 580.14 | ist = 580.14
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17765]
    erwartet = 210.00 | ist = 210
V5: OK | genau eine Zahlung trotz Wiederholung
    sql    = SELECT COUNT(*) FROM ZAHLUNG WHERE BG_ID = ?
    params = [17765]
    erwartet = 1 | ist = 1
V6: OK | genau ein erteilter Bewilligungsbescheid
    sql    = SELECT COUNT(*) FROM BESCHEID WHERE BG_ID = ?
    params = [17765]
    erwartet = 1 | ist = 1
V7: OK | Wiedervorlagedatum exakt wie gesetzt
    sql    = SELECT COUNT(*) FROM BEDARFSGEMEINSCHAFT WHERE ID = ? AND WIEDERVORLAGE_AM = ?
    params = [17765, 2027-05-02]
    erwartet = 1 | ist = 1
V8: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:20:39.929 sb-sim-08 | Vorgang anlegen (Paar (2 × RBS 2), Budget 790.14 EUR) -> OK — Vorgang 17765
2026-10-13 11:20:39.961 sb-sim-08 | 2 SozialFall/Fälle erfassen -> OK — Paar (2 × RBS 2), KdU 665.00 EUR
2026-10-13 11:20:39.988 sb-sim-08 | Bewilligungsbescheid erteilen (2026-02 .. 2026-06) -> OK — Bescheid BESCH-SWEET-0041-2026-1737 mit Monatsplan gespeichert
2026-10-13 11:20:40.007 sb-sim-08 | Auszahlung anordnen (2026-02) -> OK — 580.14 EUR (ANGELEGT)
2026-10-13 11:20:40.022 sb-sim-08 | Doppelklick: gleiche Vorgangsnummer erneut -> OK — korrekt als bereits gebucht erkannt

## TF 42 KOMBINATION SWEET-0042
status = VERIFIZIERT
hauptperson = Roth, Bernd
bearbeiter = sb-sim-12
bg_id = 17767
dauer_ms = 72
ids = zahlungId=16510, vorgangsNr=fb40bda2-b4be-484b-a409-23ae1e6f359f, mitarbeiter=sb-sim-12, bgId=17767, letzteZahlungId=16510, kollege=sb-sim-16

### rechenwege

block: Kombinationsfall 42: Alleinerziehend mit 2 Kind(ern)
zahlungsmonat = 2026-11
vermoegen = 0.00
schonvermoegen = 11000.00
budget_start = 2189.93
p1_eingaben: rbs=1, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=2, kinder_u16=2, kinder_minderjaehrig=2, warmwasser=true, kdu_miete=480.00, kdu_betrieb=75.00, kdu_heizung=110.00, rente=0.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p1_regelbedarf=563.00
p1_mehrbedarf_g=0.00
p1_mehrbedarf_schwanger=0.00
p1_mehrbedarf_alleinerziehend=202.68
p1_mehrbedarf_warmwasser=12.95
p1_kdu=665.00
p1_gesamtbedarf=1443.63
p1_freibetrag_erwerb=0.00
p1_absetzbetaetrag=0.00
p1_anrechenbar=0.00
p1_zahlbetrag=1443.63
p2_eingaben: rbs=5, merkzeichen_g=true, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=0.00, kdu_betrieb=0.00, kdu_heizung=0.00, rente=0.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p2_regelbedarf=390.00
p2_mehrbedarf_g=66.30
p2_mehrbedarf_schwanger=0.00
p2_mehrbedarf_alleinerziehend=0.00
p2_mehrbedarf_warmwasser=0.00
p2_kdu=0.00
p2_gesamtbedarf=456.30
p2_freibetrag_erwerb=0.00
p2_absetzbetaetrag=0.00
p2_anrechenbar=0.00
p2_zahlbetrag=456.30
p3_eingaben: rbs=5, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=0.00, kdu_betrieb=0.00, kdu_heizung=0.00, rente=180.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p3_regelbedarf=390.00
p3_mehrbedarf_g=0.00
p3_mehrbedarf_schwanger=0.00
p3_mehrbedarf_alleinerziehend=0.00
p3_mehrbedarf_warmwasser=0.00
p3_kdu=0.00
p3_gesamtbedarf=390.00
p3_freibetrag_erwerb=0.00
p3_absetzbetaetrag=0.00
p3_anrechenbar=180.00
p3_zahlbetrag=210.00
bg_gesamtbedarf=2289.93
bg_anrechenbar=180.00
bg_verwertbares_vermoegen=0.00
bg_zahlbetrag=2109.93
budget_rest_erwartet=80.00

### verifikation

V1: OK | alle Personen als eigene SozialFall-Zeilen
    sql    = SELECT COUNT(*) FROM SOZIAL_FALL WHERE BG_ID = ?
    params = [17767]
    erwartet = 3 | ist = 3
V2: OK | finale Zahlung wirksam (Vier-Augen-Warteschlange je nach Kassenlauf)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [16510]
    erwartet = ANGELEGT|BEREIT|UEBERGEBEN | ist = ANGELEGT
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [16510]
    erwartet = 2109.93 | ist = 2109.93
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17767]
    erwartet = 80.00 | ist = 80
V5: OK | Wiedervorlagedatum exakt wie gesetzt
    sql    = SELECT COUNT(*) FROM BEDARFSGEMEINSCHAFT WHERE ID = ? AND WIEDERVORLAGE_AM = ?
    params = [17767, 2027-03-07]
    erwartet = 1 | ist = 1
V6: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:20:39.931 sb-sim-12 | Vorgang anlegen (Alleinerziehend mit 2 Kind(ern), Budget 2189.93 EUR) -> OK — Vorgang 17767
2026-10-13 11:20:39.975 sb-sim-12 | 3 SozialFall/Fälle erfassen -> OK — Alleinerziehend mit 2 Kind(ern), KdU 665.00 EUR
2026-10-13 11:20:39.994 sb-sim-12 | Auszahlung anordnen (2026-11) -> OK — 2109.93 EUR (ANGELEGT)

## TF 43 KOMBINATION SWEET-0043
status = VERIFIZIERT
hauptperson = Petersen, Heinz
bearbeiter = sb-sim-17
bg_id = 17766
dauer_ms = 78
ids = zahlungId=16509, vorgangsNr=958ded7d-dcc1-4693-9bbe-44cb7567c99e, mitarbeiter=sb-sim-17, bgId=17766, letzteZahlungId=16509, kollege=sb-sim-03

### rechenwege

block: Kombinationsfall 43: Paar (2 × RBS 2)
zahlungsmonat = 2026-11
vermoegen = 20000.00
schonvermoegen = 20000.00
budget_start = 518.64
p1_eingaben: rbs=2, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=true, kdu_miete=340.00, kdu_betrieb=135.00, kdu_heizung=30.00, rente=1090.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p1_regelbedarf=506.00
p1_mehrbedarf_g=0.00
p1_mehrbedarf_schwanger=0.00
p1_mehrbedarf_alleinerziehend=0.00
p1_mehrbedarf_warmwasser=11.64
p1_kdu=505.00
p1_gesamtbedarf=1022.64
p1_freibetrag_erwerb=0.00
p1_absetzbetaetrag=0.00
p1_anrechenbar=1090.00
p1_zahlbetrag=-67.36
p2_eingaben: rbs=2, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=0.00, kdu_betrieb=0.00, kdu_heizung=0.00, rente=0.00, erwerb=940.00, abz_steuern=200.00, abz_sv=350.00, abz_vers=15.00, abz_erwerb=100.00
p2_regelbedarf=506.00
p2_mehrbedarf_g=0.00
p2_mehrbedarf_schwanger=0.00
p2_mehrbedarf_alleinerziehend=0.00
p2_mehrbedarf_warmwasser=0.00
p2_kdu=0.00
p2_gesamtbedarf=506.00
p2_freibetrag_erwerb=281.50
p2_absetzbetaetrag=665.00
p2_anrechenbar=0.00
p2_zahlbetrag=506.00
bg_gesamtbedarf=1528.64
bg_anrechenbar=1090.00
bg_verwertbares_vermoegen=0.00
bg_zahlbetrag=438.64
budget_rest_erwartet=80.00

### verifikation

V1: OK | alle Personen als eigene SozialFall-Zeilen
    sql    = SELECT COUNT(*) FROM SOZIAL_FALL WHERE BG_ID = ?
    params = [17766]
    erwartet = 2 | ist = 2
V2: OK | finale Zahlung freigegeben (oder bereits übergeben)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [16509]
    erwartet = BEREIT|UEBERGEBEN | ist = UEBERGEBEN
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [16509]
    erwartet = 438.64 | ist = 438.64
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17766]
    erwartet = 80.00 | ist = 80
V5: OK | Freigabevermerk nennt den Kollegen (Vier-Augen)
    sql    = SELECT CASE WHEN FREIGEGEBEN_VON = ? THEN 'ok' ELSE 'abweichend' END FROM ZAHLUNG WHERE ID = ?
    params = [sb-sim-03, 16509]
    erwartet = ok | ist = ok
V6: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:20:39.931 sb-sim-17 | Vorgang anlegen (Paar (2 × RBS 2), Budget 518.64 EUR, Vermögen 20000.00 EUR) -> OK — Vorgang 17766
2026-10-13 11:20:39.963 sb-sim-17 | 2 SozialFall/Fälle erfassen -> OK — Paar (2 × RBS 2), KdU 505.00 EUR
2026-10-13 11:20:39.988 sb-sim-17 | Auszahlung anordnen (2026-11) -> OK — 438.64 EUR (ANGELEGT)
2026-10-13 11:20:40.001 sb-sim-17 | Freigabe durch Kollegen (Vier-Augen) -> OK — freigegeben durch sb-sim-03

## TF 44 KOMBINATION SWEET-0044
status = VERIFIZIERT
hauptperson = Egger, Gisela
bearbeiter = sb-sim-06
bg_id = 17768
dauer_ms = 66
ids = zahlungId=16508, vorgangsNr=967152ec-f5c2-43e2-8130-5957c65fc0cb, mitarbeiter=sb-sim-06, bgId=17768, letzteZahlungId=16508, kollege=sb-sim-03

### rechenwege

block: Kombinationsfall 44: Paar (2 × RBS 2)
zahlungsmonat = 2026-10
vermoegen = 20300.00
schonvermoegen = 20000.00
budget_start = 1888.64
p1_eingaben: rbs=2, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=550.00, kdu_betrieb=0.00, kdu_heizung=0.00, rente=0.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p1_regelbedarf=506.00
p1_mehrbedarf_g=0.00
p1_mehrbedarf_schwanger=0.00
p1_mehrbedarf_alleinerziehend=0.00
p1_mehrbedarf_warmwasser=0.00
p1_kdu=550.00
p1_gesamtbedarf=1056.00
p1_freibetrag_erwerb=0.00
p1_absetzbetaetrag=0.00
p1_anrechenbar=0.00
p1_zahlbetrag=1056.00
p2_eingaben: rbs=2, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=true, kdu_miete=0.00, kdu_betrieb=90.00, kdu_heizung=165.00, rente=0.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p2_regelbedarf=506.00
p2_mehrbedarf_g=0.00
p2_mehrbedarf_schwanger=0.00
p2_mehrbedarf_alleinerziehend=0.00
p2_mehrbedarf_warmwasser=11.64
p2_kdu=255.00
p2_gesamtbedarf=772.64
p2_freibetrag_erwerb=0.00
p2_absetzbetaetrag=0.00
p2_anrechenbar=0.00
p2_zahlbetrag=772.64
bg_gesamtbedarf=1828.64
bg_anrechenbar=0.00
bg_verwertbares_vermoegen=300.00
bg_zahlbetrag=1528.64
budget_rest_erwartet=360.00

### verifikation

V1: OK | alle Personen als eigene SozialFall-Zeilen
    sql    = SELECT COUNT(*) FROM SOZIAL_FALL WHERE BG_ID = ?
    params = [17768]
    erwartet = 2 | ist = 2
V2: OK | finale Zahlung wirksam (Vier-Augen-Warteschlange je nach Kassenlauf)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [16508]
    erwartet = ANGELEGT|BEREIT|UEBERGEBEN | ist = ANGELEGT
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [16508]
    erwartet = 1528.64 | ist = 1528.64
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17768]
    erwartet = 360.00 | ist = 360
V5: OK | Vermögen nach Verbrauchsfortschreibung (§ 90)
    sql    = SELECT VERMOEGEN FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17768]
    erwartet = 20000.00 | ist = 20000
V6: OK | dokumentierter Vermögenseinsatz an der Zahlung
    sql    = SELECT VERMOEGENSEINSATZ FROM ZAHLUNG WHERE ID = ?
    params = [16508]
    erwartet = 300.00 | ist = 300
V7: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:20:39.932 sb-sim-06 | Vorgang anlegen (Paar (2 × RBS 2), Budget 1888.64 EUR, Vermögen 20300.00 EUR) -> OK — Vorgang 17768
2026-10-13 11:20:39.962 sb-sim-06 | 2 SozialFall/Fälle erfassen -> OK — Paar (2 × RBS 2), KdU 805.00 EUR
2026-10-13 11:20:39.988 sb-sim-06 | Auszahlung anordnen (2026-10) -> OK — 1528.64 EUR (ANGELEGT)

## TF 45 KOMBINATION SWEET-0045
status = VERIFIZIERT
hauptperson = Egger, Ursula
bearbeiter = sb-sim-14
bg_id = 17769
dauer_ms = 87
ids = zahlungId=16514, vorgangsNr=1f89a5e7-c4d9-4d69-b473-a93eea79a9b4, mitarbeiter=sb-sim-14, bgId=17769, letzteZahlungId=16514, kollege=sb-sim-08

### rechenwege

block: Kombinationsfall 45: Single
zahlungsmonat = 2026-03
vermoegen = 10200.00
schonvermoegen = 10000.00
budget_start = 1103.00
p1_eingaben: rbs=1, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=240.00, kdu_betrieb=100.00, kdu_heizung=140.00, rente=0.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p1_regelbedarf=563.00
p1_mehrbedarf_g=0.00
p1_mehrbedarf_schwanger=0.00
p1_mehrbedarf_alleinerziehend=0.00
p1_mehrbedarf_warmwasser=0.00
p1_kdu=480.00
p1_gesamtbedarf=1043.00
p1_freibetrag_erwerb=0.00
p1_absetzbetaetrag=0.00
p1_anrechenbar=0.00
p1_zahlbetrag=1043.00
bg_gesamtbedarf=1043.00
bg_anrechenbar=0.00
bg_verwertbares_vermoegen=200.00
bg_zahlbetrag=843.00
budget_rest_erwartet=260.00

### verifikation

V1: OK | alle Personen als eigene SozialFall-Zeilen
    sql    = SELECT COUNT(*) FROM SOZIAL_FALL WHERE BG_ID = ?
    params = [17769]
    erwartet = 1 | ist = 1
V2: OK | finale Zahlung wirksam (Vier-Augen-Warteschlange je nach Kassenlauf)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [16514]
    erwartet = ANGELEGT|BEREIT|UEBERGEBEN | ist = ANGELEGT
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [16514]
    erwartet = 843.00 | ist = 843
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17769]
    erwartet = 260.00 | ist = 260
V5: OK | genau eine Zahlung trotz Wiederholung
    sql    = SELECT COUNT(*) FROM ZAHLUNG WHERE BG_ID = ?
    params = [17769]
    erwartet = 1 | ist = 1
V6: OK | genau ein erteilter Bewilligungsbescheid
    sql    = SELECT COUNT(*) FROM BESCHEID WHERE BG_ID = ?
    params = [17769]
    erwartet = 1 | ist = 1
V7: OK | Vermögen nach Verbrauchsfortschreibung (§ 90)
    sql    = SELECT VERMOEGEN FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17769]
    erwartet = 10000.00 | ist = 10000
V8: OK | dokumentierter Vermögenseinsatz an der Zahlung
    sql    = SELECT VERMOEGENSEINSATZ FROM ZAHLUNG WHERE ID = ?
    params = [16514]
    erwartet = 200.00 | ist = 200
V9: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:20:39.937 sb-sim-14 | Vorgang anlegen (Single, Budget 1103.00 EUR, Vermögen 10200.00 EUR) -> OK — Vorgang 17769
2026-10-13 11:20:39.953 sb-sim-14 | 1 SozialFall/Fälle erfassen -> OK — Single, KdU 480.00 EUR
2026-10-13 11:20:39.982 sb-sim-14 | Bewilligungsbescheid erteilen (2026-01 .. 2026-08) -> OK — Bescheid BESCH-SWEET-0045-2026-1736 mit Monatsplan gespeichert
2026-10-13 11:20:40.003 sb-sim-14 | Auszahlung anordnen (2026-03) -> OK — 843.00 EUR (ANGELEGT)
2026-10-13 11:20:40.014 sb-sim-14 | Doppelklick: gleiche Vorgangsnummer erneut -> OK — korrekt als bereits gebucht erkannt

## TF 46 KOMBINATION SWEET-0046
status = VERIFIZIERT
hauptperson = Thiel, Ursula
bearbeiter = sb-sim-04
bg_id = 17770
dauer_ms = 81
ids = zahlungId=16520, vorgangsNr=f4267dbe-191e-45f7-93d8-da41fbb8a6f5, mitarbeiter=sb-sim-04, bgId=17770, letzteZahlungId=16520, kollege=sb-sim-10

### rechenwege

block: Kombinationsfall 46: Paar (2 × RBS 2)
zahlungsmonat = 2026-09
vermoegen = 20100.00
schonvermoegen = 20000.00
budget_start = 310.14
p1_eingaben: rbs=2, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=220.00, kdu_betrieb=0.00, kdu_heizung=0.00, rente=450.00, erwerb=1010.00, abz_steuern=200.00, abz_sv=150.00, abz_vers=70.00, abz_erwerb=60.00
p1_regelbedarf=506.00
p1_mehrbedarf_g=0.00
p1_mehrbedarf_schwanger=0.00
p1_mehrbedarf_alleinerziehend=0.00
p1_mehrbedarf_warmwasser=0.00
p1_kdu=220.00
p1_gesamtbedarf=726.00
p1_freibetrag_erwerb=281.50
p1_absetzbetaetrag=480.00
p1_anrechenbar=698.50
p1_zahlbetrag=27.50
p2_eingaben: rbs=2, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=true, kdu_miete=0.00, kdu_betrieb=30.00, kdu_heizung=45.00, rente=460.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p2_regelbedarf=506.00
p2_mehrbedarf_g=0.00
p2_mehrbedarf_schwanger=0.00
p2_mehrbedarf_alleinerziehend=0.00
p2_mehrbedarf_warmwasser=11.64
p2_kdu=75.00
p2_gesamtbedarf=592.64
p2_freibetrag_erwerb=0.00
p2_absetzbetaetrag=0.00
p2_anrechenbar=460.00
p2_zahlbetrag=132.64
bg_gesamtbedarf=1318.64
bg_anrechenbar=1158.50
bg_verwertbares_vermoegen=100.00
bg_zahlbetrag=60.14
budget_rest_erwartet=250.00

### verifikation

V1: OK | alle Personen als eigene SozialFall-Zeilen
    sql    = SELECT COUNT(*) FROM SOZIAL_FALL WHERE BG_ID = ?
    params = [17770]
    erwartet = 2 | ist = 2
V2: OK | finale Zahlung wirksam (Vier-Augen-Warteschlange je nach Kassenlauf)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [16520]
    erwartet = ANGELEGT|BEREIT|UEBERGEBEN | ist = ANGELEGT
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [16520]
    erwartet = 60.14 | ist = 60.14
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17770]
    erwartet = 250.00 | ist = 250
V5: OK | genau ein erteilter Bewilligungsbescheid
    sql    = SELECT COUNT(*) FROM BESCHEID WHERE BG_ID = ?
    params = [17770]
    erwartet = 1 | ist = 1
V6: OK | Vermögen nach Verbrauchsfortschreibung (§ 90)
    sql    = SELECT VERMOEGEN FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17770]
    erwartet = 20000.00 | ist = 20000
V7: OK | dokumentierter Vermögenseinsatz an der Zahlung
    sql    = SELECT VERMOEGENSEINSATZ FROM ZAHLUNG WHERE ID = ?
    params = [16520]
    erwartet = 100.00 | ist = 100
V8: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:20:39.940 sb-sim-04 | Vorgang anlegen (Paar (2 × RBS 2), Budget 310.14 EUR, Vermögen 20100.00 EUR) -> OK — Vorgang 17770
2026-10-13 11:20:39.964 sb-sim-04 | 2 SozialFall/Fälle erfassen -> OK — Paar (2 × RBS 2), KdU 295.00 EUR
2026-10-13 11:20:39.990 sb-sim-04 | Bewilligungsbescheid erteilen (2026-08 .. 2026-12) -> OK — Bescheid BESCH-SWEET-0046-2026-1738 mit Monatsplan gespeichert
2026-10-13 11:20:40.010 sb-sim-04 | Auszahlung anordnen (2026-09) -> OK — 60.14 EUR (ANGELEGT)

## TF 47 KOMBINATION SWEET-0047
status = VERIFIZIERT
hauptperson = Wagner, Dieter
bearbeiter = sb-sim-03
bg_id = 17771
dauer_ms = 127
ids = zahlungId=16529, vorgangsNr=bf53e5f2-4fb0-4f33-b402-851276405522, mitarbeiter=sb-sim-03, bgId=17771, letzteZahlungId=16529, kollege=sb-sim-18

### rechenwege

block: Kombinationsfall 47: Paar mit 2 Kind(ern)
zahlungsmonat = 2026-09
vermoegen = 21250.00
schonvermoegen = 21000.00
budget_start = 430.59
p1_eingaben: rbs=2, merkzeichen_g=true, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=580.00, kdu_betrieb=0.00, kdu_heizung=0.00, rente=950.00, erwerb=500.00, abz_steuern=190.00, abz_sv=340.00, abz_vers=15.00, abz_erwerb=10.00
p1_regelbedarf=506.00
p1_mehrbedarf_g=86.02
p1_mehrbedarf_schwanger=0.00
p1_mehrbedarf_alleinerziehend=0.00
p1_mehrbedarf_warmwasser=0.00
p1_kdu=580.00
p1_gesamtbedarf=1172.02
p1_freibetrag_erwerb=150.00
p1_absetzbetaetrag=555.00
p1_anrechenbar=745.00
p1_zahlbetrag=427.02
p2_eingaben: rbs=2, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=0.00, kdu_betrieb=135.00, kdu_heizung=115.00, rente=800.00, erwerb=1130.00, abz_steuern=90.00, abz_sv=180.00, abz_vers=35.00, abz_erwerb=80.00
p2_regelbedarf=506.00
p2_mehrbedarf_g=0.00
p2_mehrbedarf_schwanger=0.00
p2_mehrbedarf_alleinerziehend=0.00
p2_mehrbedarf_warmwasser=0.00
p2_kdu=250.00
p2_gesamtbedarf=756.00
p2_freibetrag_erwerb=281.50
p2_absetzbetaetrag=385.00
p2_anrechenbar=1263.50
p2_zahlbetrag=-507.50
p3_eingaben: rbs=5, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=0.00, kdu_betrieb=0.00, kdu_heizung=0.00, rente=0.00, erwerb=130.00, abz_steuern=80.00, abz_sv=130.00, abz_vers=15.00, abz_erwerb=60.00
p3_regelbedarf=390.00
p3_mehrbedarf_g=0.00
p3_mehrbedarf_schwanger=0.00
p3_mehrbedarf_alleinerziehend=0.00
p3_mehrbedarf_warmwasser=0.00
p3_kdu=0.00
p3_gesamtbedarf=390.00
p3_freibetrag_erwerb=39.00
p3_absetzbetaetrag=285.00
p3_anrechenbar=0.00
p3_zahlbetrag=390.00
p4_eingaben: rbs=4, merkzeichen_g=true, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=0.00, kdu_betrieb=0.00, kdu_heizung=0.00, rente=500.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p4_regelbedarf=471.00
p4_mehrbedarf_g=80.07
p4_mehrbedarf_schwanger=0.00
p4_mehrbedarf_alleinerziehend=0.00
p4_mehrbedarf_warmwasser=0.00
p4_kdu=0.00
p4_gesamtbedarf=551.07
p4_freibetrag_erwerb=0.00
p4_absetzbetaetrag=0.00
p4_anrechenbar=500.00
p4_zahlbetrag=51.07
bg_gesamtbedarf=2869.09
bg_anrechenbar=2508.50
bg_verwertbares_vermoegen=250.00
bg_zahlbetrag=110.59
budget_rest_erwartet=320.00

### verifikation

V1: OK | alle Personen als eigene SozialFall-Zeilen
    sql    = SELECT COUNT(*) FROM SOZIAL_FALL WHERE BG_ID = ?
    params = [17771]
    erwartet = 4 | ist = 4
V2: OK | finale Zahlung freigegeben (oder bereits übergeben)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [16529]
    erwartet = BEREIT|UEBERGEBEN | ist = UEBERGEBEN
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [16529]
    erwartet = 110.59 | ist = 110.59
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17771]
    erwartet = 320.00 | ist = 320
V5: OK | Freigabevermerk nennt den Kollegen (Vier-Augen)
    sql    = SELECT CASE WHEN FREIGEGEBEN_VON = ? THEN 'ok' ELSE 'abweichend' END FROM ZAHLUNG WHERE ID = ?
    params = [sb-sim-18, 16529]
    erwartet = ok | ist = ok
V6: OK | genau ein erteilter Bewilligungsbescheid
    sql    = SELECT COUNT(*) FROM BESCHEID WHERE BG_ID = ?
    params = [17771]
    erwartet = 1 | ist = 1
V7: OK | Vermögen nach Verbrauchsfortschreibung (§ 90)
    sql    = SELECT VERMOEGEN FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17771]
    erwartet = 21000.00 | ist = 21000
V8: OK | dokumentierter Vermögenseinsatz an der Zahlung
    sql    = SELECT VERMOEGENSEINSATZ FROM ZAHLUNG WHERE ID = ?
    params = [16529]
    erwartet = 250.00 | ist = 250
V9: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:20:39.944 sb-sim-03 | Vorgang anlegen (Paar mit 2 Kind(ern), Budget 430.59 EUR, Vermögen 21250.00 EUR) -> OK — Vorgang 17771
2026-10-13 11:20:40.001 sb-sim-03 | 4 SozialFall/Fälle erfassen -> OK — Paar mit 2 Kind(ern), KdU 830.00 EUR
2026-10-13 11:20:40.026 sb-sim-03 | Bewilligungsbescheid erteilen (2026-06 .. 2026-12) -> OK — Bescheid BESCH-SWEET-0047-2026-1741 mit Monatsplan gespeichert
2026-10-13 11:20:40.048 sb-sim-03 | Auszahlung anordnen (2026-09) -> OK — 110.59 EUR (ANGELEGT)
2026-10-13 11:20:40.060 sb-sim-03 | Freigabe durch Kollegen (Vier-Augen) -> OK — freigegeben durch sb-sim-18

## TF 48 KOMBINATION SWEET-0048
status = VERIFIZIERT
hauptperson = Brandt, Anna
bearbeiter = sb-sim-07
bg_id = 17772
dauer_ms = 93
ids = zahlungId=16521, vorgangsNr=6cf47ad7-fe14-41fc-bcad-a9a5d135422b, mitarbeiter=sb-sim-07, bgId=17772, letzteZahlungId=16521, kollege=sb-sim-09

### rechenwege

block: Kombinationsfall 48: Paar (2 × RBS 2)
zahlungsmonat = 2026-10
vermoegen = 0.00
schonvermoegen = 20000.00
budget_start = 1129.66
p1_eingaben: rbs=2, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=true, kdu_miete=710.00, kdu_betrieb=0.00, kdu_heizung=0.00, rente=0.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p1_regelbedarf=506.00
p1_mehrbedarf_g=0.00
p1_mehrbedarf_schwanger=0.00
p1_mehrbedarf_alleinerziehend=0.00
p1_mehrbedarf_warmwasser=11.64
p1_kdu=710.00
p1_gesamtbedarf=1227.64
p1_freibetrag_erwerb=0.00
p1_absetzbetaetrag=0.00
p1_anrechenbar=0.00
p1_zahlbetrag=1227.64
p2_eingaben: rbs=2, merkzeichen_g=true, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=0.00, kdu_betrieb=60.00, kdu_heizung=90.00, rente=950.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p2_regelbedarf=506.00
p2_mehrbedarf_g=86.02
p2_mehrbedarf_schwanger=0.00
p2_mehrbedarf_alleinerziehend=0.00
p2_mehrbedarf_warmwasser=0.00
p2_kdu=150.00
p2_gesamtbedarf=742.02
p2_freibetrag_erwerb=0.00
p2_absetzbetaetrag=0.00
p2_anrechenbar=950.00
p2_zahlbetrag=-207.98
bg_gesamtbedarf=1969.66
bg_anrechenbar=950.00
bg_verwertbares_vermoegen=0.00
bg_zahlbetrag=1019.66
budget_rest_erwartet=110.00

### verifikation

V1: OK | alle Personen als eigene SozialFall-Zeilen
    sql    = SELECT COUNT(*) FROM SOZIAL_FALL WHERE BG_ID = ?
    params = [17772]
    erwartet = 2 | ist = 2
V2: OK | finale Zahlung wirksam (Vier-Augen-Warteschlange je nach Kassenlauf)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [16521]
    erwartet = ANGELEGT|BEREIT|UEBERGEBEN | ist = ANGELEGT
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [16521]
    erwartet = 1019.66 | ist = 1019.66
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17772]
    erwartet = 110.00 | ist = 110
V5: OK | genau eine Zahlung trotz Wiederholung
    sql    = SELECT COUNT(*) FROM ZAHLUNG WHERE BG_ID = ?
    params = [17772]
    erwartet = 1 | ist = 1
V6: OK | genau ein erteilter Bewilligungsbescheid
    sql    = SELECT COUNT(*) FROM BESCHEID WHERE BG_ID = ?
    params = [17772]
    erwartet = 1 | ist = 1
V7: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:20:39.946 sb-sim-07 | Vorgang anlegen (Paar (2 × RBS 2), Budget 1129.66 EUR) -> OK — Vorgang 17772
2026-10-13 11:20:39.976 sb-sim-07 | 2 SozialFall/Fälle erfassen -> OK — Paar (2 × RBS 2), KdU 860.00 EUR
2026-10-13 11:20:39.998 sb-sim-07 | Bewilligungsbescheid erteilen (2026-07 .. 2026-11) -> OK — Bescheid BESCH-SWEET-0048-2026-1739 mit Monatsplan gespeichert
2026-10-13 11:20:40.017 sb-sim-07 | Auszahlung anordnen (2026-10) -> OK — 1019.66 EUR (ANGELEGT)
2026-10-13 11:20:40.028 sb-sim-07 | Doppelklick: gleiche Vorgangsnummer erneut -> OK — korrekt als bereits gebucht erkannt

## TF 49 KOMBINATION SWEET-0049
status = VERIFIZIERT
hauptperson = Hoffmann, Gisela
bearbeiter = sb-sim-11
bg_id = 17773
dauer_ms = 92
ids = zahlungId=16522, vorgangsNr=a4ab1ae7-9757-4c70-824f-2e775bdbc0bd, mitarbeiter=sb-sim-11, bgId=17773, letzteZahlungId=16522, kollege=sb-sim-02

### rechenwege

block: Kombinationsfall 49: Paar (2 × RBS 2)
zahlungsmonat = 2026-06
vermoegen = 0.00
schonvermoegen = 20000.00
budget_start = 661.16
p1_eingaben: rbs=2, merkzeichen_g=false, schwanger=true, entbindungsmonat=2026-09, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=true, kdu_miete=700.00, kdu_betrieb=0.00, kdu_heizung=0.00, rente=110.00, erwerb=1340.00, abz_steuern=0.00, abz_sv=130.00, abz_vers=40.00, abz_erwerb=20.00
p1_regelbedarf=506.00
p1_mehrbedarf_g=0.00
p1_mehrbedarf_schwanger=86.02
p1_mehrbedarf_alleinerziehend=0.00
p1_mehrbedarf_warmwasser=11.64
p1_kdu=700.00
p1_gesamtbedarf=1303.66
p1_freibetrag_erwerb=281.50
p1_absetzbetaetrag=190.00
p1_anrechenbar=978.50
p1_zahlbetrag=325.16
p2_eingaben: rbs=2, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=0.00, kdu_betrieb=50.00, kdu_heizung=60.00, rente=430.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p2_regelbedarf=506.00
p2_mehrbedarf_g=0.00
p2_mehrbedarf_schwanger=0.00
p2_mehrbedarf_alleinerziehend=0.00
p2_mehrbedarf_warmwasser=0.00
p2_kdu=110.00
p2_gesamtbedarf=616.00
p2_freibetrag_erwerb=0.00
p2_absetzbetaetrag=0.00
p2_anrechenbar=430.00
p2_zahlbetrag=186.00
bg_gesamtbedarf=1919.66
bg_anrechenbar=1408.50
bg_verwertbares_vermoegen=0.00
bg_zahlbetrag=511.16
budget_rest_erwartet=150.00

### verifikation

V1: OK | alle Personen als eigene SozialFall-Zeilen
    sql    = SELECT COUNT(*) FROM SOZIAL_FALL WHERE BG_ID = ?
    params = [17773]
    erwartet = 2 | ist = 2
V2: OK | finale Zahlung freigegeben (oder bereits übergeben)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [16522]
    erwartet = BEREIT|UEBERGEBEN | ist = UEBERGEBEN
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [16522]
    erwartet = 511.16 | ist = 511.16
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17773]
    erwartet = 150.00 | ist = 150
V5: OK | Freigabevermerk nennt den Kollegen (Vier-Augen)
    sql    = SELECT CASE WHEN FREIGEGEBEN_VON = ? THEN 'ok' ELSE 'abweichend' END FROM ZAHLUNG WHERE ID = ?
    params = [sb-sim-02, 16522]
    erwartet = ok | ist = ok
V6: OK | genau ein erteilter Bewilligungsbescheid
    sql    = SELECT COUNT(*) FROM BESCHEID WHERE BG_ID = ?
    params = [17773]
    erwartet = 1 | ist = 1
V7: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:20:39.953 sb-sim-11 | Vorgang anlegen (Paar (2 × RBS 2), Budget 661.16 EUR) -> OK — Vorgang 17773
2026-10-13 11:20:39.982 sb-sim-11 | 2 SozialFall/Fälle erfassen -> OK — Paar (2 × RBS 2), KdU 810.00 EUR
2026-10-13 11:20:40.002 sb-sim-11 | Bewilligungsbescheid erteilen (2026-05 .. 2026-08) -> OK — Bescheid BESCH-SWEET-0049-2026-1740 mit Monatsplan gespeichert
2026-10-13 11:20:40.022 sb-sim-11 | Auszahlung anordnen (2026-06) -> OK — 511.16 EUR (ANGELEGT)
2026-10-13 11:20:40.032 sb-sim-11 | Freigabe durch Kollegen (Vier-Augen) -> OK — freigegeben durch sb-sim-02

## TF 51 KOMBINATION SWEET-0051
status = VERIFIZIERT
hauptperson = Otto, Norbert
bearbeiter = sb-sim-05
bg_id = 17774
dauer_ms = 71
ids = zahlungId=16515, vorgangsNr=b02a1718-635d-4ead-a972-6695761313af, mitarbeiter=sb-sim-05, bgId=17774, letzteZahlungId=16515, kollege=sb-sim-19

### rechenwege

block: Kombinationsfall 51: Alleinerziehend mit 1 Kind(ern)
zahlungsmonat = 2026-04
vermoegen = 10500.00
schonvermoegen = 10500.00
budget_start = 1107.08
p1_eingaben: rbs=1, merkzeichen_g=false, schwanger=true, entbindungsmonat=2026-08, kinder_u7=1, kinder_u16=1, kinder_minderjaehrig=1, warmwasser=false, kdu_miete=360.00, kdu_betrieb=115.00, kdu_heizung=95.00, rente=0.00, erwerb=1020.00, abz_steuern=90.00, abz_sv=320.00, abz_vers=75.00, abz_erwerb=40.00
p1_regelbedarf=563.00
p1_mehrbedarf_g=0.00
p1_mehrbedarf_schwanger=95.71
p1_mehrbedarf_alleinerziehend=202.68
p1_mehrbedarf_warmwasser=0.00
p1_kdu=570.00
p1_gesamtbedarf=1431.39
p1_freibetrag_erwerb=281.50
p1_absetzbetaetrag=525.00
p1_anrechenbar=213.50
p1_zahlbetrag=1217.89
p2_eingaben: rbs=6, merkzeichen_g=true, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=0.00, kdu_betrieb=0.00, kdu_heizung=0.00, rente=0.00, erwerb=1160.00, abz_steuern=0.00, abz_sv=100.00, abz_vers=20.00, abz_erwerb=70.00
p2_regelbedarf=357.00
p2_mehrbedarf_g=60.69
p2_mehrbedarf_schwanger=0.00
p2_mehrbedarf_alleinerziehend=0.00
p2_mehrbedarf_warmwasser=0.00
p2_kdu=0.00
p2_gesamtbedarf=417.69
p2_freibetrag_erwerb=281.50
p2_absetzbetaetrag=190.00
p2_anrechenbar=688.50
p2_zahlbetrag=-270.81
bg_gesamtbedarf=1849.08
bg_anrechenbar=902.00
bg_verwertbares_vermoegen=0.00
bg_zahlbetrag=947.08
budget_rest_erwartet=160.00

### verifikation

V1: OK | alle Personen als eigene SozialFall-Zeilen
    sql    = SELECT COUNT(*) FROM SOZIAL_FALL WHERE BG_ID = ?
    params = [17774]
    erwartet = 2 | ist = 2
V2: OK | finale Zahlung freigegeben (oder bereits übergeben)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [16515]
    erwartet = BEREIT|UEBERGEBEN | ist = UEBERGEBEN
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [16515]
    erwartet = 947.08 | ist = 947.08
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17774]
    erwartet = 160.00 | ist = 160
V5: OK | Freigabevermerk nennt den Kollegen (Vier-Augen)
    sql    = SELECT CASE WHEN FREIGEGEBEN_VON = ? THEN 'ok' ELSE 'abweichend' END FROM ZAHLUNG WHERE ID = ?
    params = [sb-sim-19, 16515]
    erwartet = ok | ist = ok
V6: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:20:39.958 sb-sim-05 | Vorgang anlegen (Alleinerziehend mit 1 Kind(ern), Budget 1107.08 EUR, Vermögen 10500.00 EUR) -> OK — Vorgang 17774
2026-10-13 11:20:39.988 sb-sim-05 | 2 SozialFall/Fälle erfassen -> OK — Alleinerziehend mit 1 Kind(ern), KdU 570.00 EUR
2026-10-13 11:20:40.005 sb-sim-05 | Auszahlung anordnen (2026-04) -> OK — 947.08 EUR (ANGELEGT)
2026-10-13 11:20:40.016 sb-sim-05 | Freigabe durch Kollegen (Vier-Augen) -> OK — freigegeben durch sb-sim-19

## TF 52 KOMBINATION SWEET-0052
status = VERIFIZIERT
hauptperson = Lindner, Ursula
bearbeiter = sb-sim-16
bg_id = 17775
dauer_ms = 62
ids = zahlungId=16517, vorgangsNr=b202bd12-e254-40a2-b49d-8e18e29c9f00, mitarbeiter=sb-sim-16, bgId=17775, letzteZahlungId=16517, kollege=sb-sim-12

### rechenwege

block: Kombinationsfall 52: Alleinerziehend mit 1 Kind(ern)
zahlungsmonat = 2026-10
vermoegen = 12050.00
schonvermoegen = 10500.00
budget_start = 476.34
p1_eingaben: rbs=1, merkzeichen_g=true, schwanger=false, entbindungsmonat=-, kinder_u7=1, kinder_u16=1, kinder_minderjaehrig=1, warmwasser=true, kdu_miete=630.00, kdu_betrieb=100.00, kdu_heizung=165.00, rente=0.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p1_regelbedarf=563.00
p1_mehrbedarf_g=95.71
p1_mehrbedarf_schwanger=0.00
p1_mehrbedarf_alleinerziehend=202.68
p1_mehrbedarf_warmwasser=12.95
p1_kdu=895.00
p1_gesamtbedarf=1769.34
p1_freibetrag_erwerb=0.00
p1_absetzbetaetrag=0.00
p1_anrechenbar=0.00
p1_zahlbetrag=1769.34
p2_eingaben: rbs=6, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=0.00, kdu_betrieb=0.00, kdu_heizung=0.00, rente=370.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p2_regelbedarf=357.00
p2_mehrbedarf_g=0.00
p2_mehrbedarf_schwanger=0.00
p2_mehrbedarf_alleinerziehend=0.00
p2_mehrbedarf_warmwasser=0.00
p2_kdu=0.00
p2_gesamtbedarf=357.00
p2_freibetrag_erwerb=0.00
p2_absetzbetaetrag=0.00
p2_anrechenbar=370.00
p2_zahlbetrag=-13.00
bg_gesamtbedarf=2126.34
bg_anrechenbar=370.00
bg_verwertbares_vermoegen=1550.00
bg_zahlbetrag=206.34
budget_rest_erwartet=270.00

### verifikation

V1: OK | alle Personen als eigene SozialFall-Zeilen
    sql    = SELECT COUNT(*) FROM SOZIAL_FALL WHERE BG_ID = ?
    params = [17775]
    erwartet = 2 | ist = 2
V2: OK | finale Zahlung wirksam (Vier-Augen-Warteschlange je nach Kassenlauf)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [16517]
    erwartet = ANGELEGT|BEREIT|UEBERGEBEN | ist = ANGELEGT
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [16517]
    erwartet = 206.34 | ist = 206.34
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17775]
    erwartet = 270.00 | ist = 270
V5: OK | Vermögen nach Verbrauchsfortschreibung (§ 90)
    sql    = SELECT VERMOEGEN FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17775]
    erwartet = 10500.00 | ist = 10500
V6: OK | dokumentierter Vermögenseinsatz an der Zahlung
    sql    = SELECT VERMOEGENSEINSATZ FROM ZAHLUNG WHERE ID = ?
    params = [16517]
    erwartet = 1550.00 | ist = 1550
V7: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:20:39.961 sb-sim-16 | Vorgang anlegen (Alleinerziehend mit 1 Kind(ern), Budget 476.34 EUR, Vermögen 12050.00 EUR) -> OK — Vorgang 17775
2026-10-13 11:20:39.989 sb-sim-16 | 2 SozialFall/Fälle erfassen -> OK — Alleinerziehend mit 1 Kind(ern), KdU 895.00 EUR
2026-10-13 11:20:40.007 sb-sim-16 | Auszahlung anordnen (2026-10) -> OK — 206.34 EUR (ANGELEGT)

## TF 53 KOMBINATION SWEET-0053
status = VERIFIZIERT
hauptperson = Egger, Paul
bearbeiter = sb-sim-09
bg_id = 17776
dauer_ms = 47
ids = zahlungId=16513, vorgangsNr=9aae07bd-ab68-4609-92eb-6a7c7237849a, mitarbeiter=sb-sim-09, bgId=17776, letzteZahlungId=16513, kollege=sb-sim-08

### rechenwege

block: Kombinationsfall 53: Single
zahlungsmonat = 2026-04
vermoegen = 10200.00
schonvermoegen = 10000.00
budget_start = 1303.00
p1_eingaben: rbs=1, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=430.00, kdu_betrieb=60.00, kdu_heizung=80.00, rente=0.00, erwerb=640.00, abz_steuern=130.00, abz_sv=310.00, abz_vers=20.00, abz_erwerb=90.00
p1_regelbedarf=563.00
p1_mehrbedarf_g=0.00
p1_mehrbedarf_schwanger=0.00
p1_mehrbedarf_alleinerziehend=0.00
p1_mehrbedarf_warmwasser=0.00
p1_kdu=570.00
p1_gesamtbedarf=1133.00
p1_freibetrag_erwerb=192.00
p1_absetzbetaetrag=550.00
p1_anrechenbar=0.00
p1_zahlbetrag=1133.00
bg_gesamtbedarf=1133.00
bg_anrechenbar=0.00
bg_verwertbares_vermoegen=200.00
bg_zahlbetrag=933.00
budget_rest_erwartet=370.00

### verifikation

V1: OK | alle Personen als eigene SozialFall-Zeilen
    sql    = SELECT COUNT(*) FROM SOZIAL_FALL WHERE BG_ID = ?
    params = [17776]
    erwartet = 1 | ist = 1
V2: OK | finale Zahlung wirksam (Vier-Augen-Warteschlange je nach Kassenlauf)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [16513]
    erwartet = ANGELEGT|BEREIT|UEBERGEBEN | ist = ANGELEGT
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [16513]
    erwartet = 933.00 | ist = 933
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17776]
    erwartet = 370.00 | ist = 370
V5: OK | Vermögen nach Verbrauchsfortschreibung (§ 90)
    sql    = SELECT VERMOEGEN FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17776]
    erwartet = 10000.00 | ist = 10000
V6: OK | dokumentierter Vermögenseinsatz an der Zahlung
    sql    = SELECT VERMOEGENSEINSATZ FROM ZAHLUNG WHERE ID = ?
    params = [16513]
    erwartet = 200.00 | ist = 200
V7: OK | Wiedervorlagedatum exakt wie gesetzt
    sql    = SELECT COUNT(*) FROM BEDARFSGEMEINSCHAFT WHERE ID = ? AND WIEDERVORLAGE_AM = ?
    params = [17776, 2027-01-17]
    erwartet = 1 | ist = 1
V8: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:20:39.963 sb-sim-09 | Vorgang anlegen (Single, Budget 1303.00 EUR, Vermögen 10200.00 EUR) -> OK — Vorgang 17776
2026-10-13 11:20:39.978 sb-sim-09 | 1 SozialFall/Fälle erfassen -> OK — Single, KdU 570.00 EUR
2026-10-13 11:20:40.001 sb-sim-09 | Auszahlung anordnen (2026-04) -> OK — 933.00 EUR (ANGELEGT)

## TF 54 KOMBINATION SWEET-0054
status = VERIFIZIERT
hauptperson = Petersen, Karin
bearbeiter = sb-sim-20
bg_id = 17777
dauer_ms = 95
ids = letzteZahlungId=16530, kollege=sb-sim-03, bgId=17777, mitarbeiter=sb-sim-20, zahlungId=16512, fallId=26859, vorgangsNr=96c3f48d-d76d-48a9-9ac3-44b81df4cacc

### rechenwege

block: Kombinationsfall 54: Single
zahlungsmonat = 2026-03
vermoegen = 0.00
schonvermoegen = 10000.00
budget_start = 1723.00
p1_eingaben: rbs=1, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=690.00, kdu_betrieb=125.00, kdu_heizung=155.00, rente=0.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p1_regelbedarf=563.00
p1_mehrbedarf_g=0.00
p1_mehrbedarf_schwanger=0.00
p1_mehrbedarf_alleinerziehend=0.00
p1_mehrbedarf_warmwasser=0.00
p1_kdu=970.00
p1_gesamtbedarf=1533.00
p1_freibetrag_erwerb=0.00
p1_absetzbetaetrag=0.00
p1_anrechenbar=0.00
p1_zahlbetrag=1533.00
bg_gesamtbedarf=1533.00
bg_anrechenbar=0.00
bg_verwertbares_vermoegen=0.00
bg_zahlbetrag=1533.00
budget_rest_erwartet=190.00

block: Neuanordnung nach Mietekorrektur (Miete 690.00 → 810.00)
zahlungsmonat = 2026-03
vermoegen = 0.00
schonvermoegen = 10000.00
budget_start = 1723.00
p1_eingaben: rbs=1, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=810.00, kdu_betrieb=125.00, kdu_heizung=155.00, rente=0.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p1_regelbedarf=563.00
p1_mehrbedarf_g=0.00
p1_mehrbedarf_schwanger=0.00
p1_mehrbedarf_alleinerziehend=0.00
p1_mehrbedarf_warmwasser=0.00
p1_kdu=1090.00
p1_gesamtbedarf=1653.00
p1_freibetrag_erwerb=0.00
p1_absetzbetaetrag=0.00
p1_anrechenbar=0.00
p1_zahlbetrag=1653.00
bg_gesamtbedarf=1653.00
bg_anrechenbar=0.00
bg_verwertbares_vermoegen=0.00
bg_zahlbetrag=1653.00
budget_rest_erwartet=70.00

### verifikation

V1: OK | alle Personen als eigene SozialFall-Zeilen
    sql    = SELECT COUNT(*) FROM SOZIAL_FALL WHERE BG_ID = ?
    params = [17777]
    erwartet = 1 | ist = 1
V2: OK | finale Zahlung wirksam (Vier-Augen-Warteschlange je nach Kassenlauf)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [16530]
    erwartet = ANGELEGT|BEREIT|UEBERGEBEN | ist = ANGELEGT
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [16530]
    erwartet = 1653.00 | ist = 1653
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17777]
    erwartet = 70.00 | ist = 70
V5: OK | zwei Zahlungen (stornierte + korrigierte)
    sql    = SELECT COUNT(*) FROM ZAHLUNG WHERE BG_ID = ?
    params = [17777]
    erwartet = 2 | ist = 2
V6: OK | erste Zahlung STORNIERT
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [16512]
    erwartet = STORNIERT | ist = STORNIERT
V7: OK | Fall in Version 2 (optimistisches Locking)
    sql    = SELECT VERSION FROM SOZIAL_FALL WHERE ID = ?
    params = [26859]
    erwartet = 2 | ist = 2
V8: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:20:39.962 sb-sim-20 | Vorgang anlegen (Single, Budget 1723.00 EUR) -> OK — Vorgang 17777
2026-10-13 11:20:39.976 sb-sim-20 | 1 SozialFall/Fälle erfassen -> OK — Single, KdU 970.00 EUR
2026-10-13 11:20:39.998 sb-sim-20 | Auszahlung anordnen (2026-03) -> OK — 1533.00 EUR (ANGELEGT)
2026-10-13 11:20:40.010 sb-sim-20 | Anordnung sofort stornieren (Status ANGELEGT) -> OK — storniert — Monat wieder frei
2026-10-13 11:20:40.030 sb-sim-20 | Miete auf 810.00 korrigieren -> OK — Fall 26859 in Version 2
2026-10-13 11:20:40.048 sb-sim-20 | Korrigierte Auszahlung für denselben Monat -> OK — 1653.00 EUR

## TF 55 KOMBINATION SWEET-0055
status = VERIFIZIERT
hauptperson = Zimmer, Ingrid
bearbeiter = sb-sim-01
bg_id = 17778
dauer_ms = 159
ids = letzteZahlungId=16556, kollege=sb-sim-07, bgId=17778, mitarbeiter=sb-sim-01, zahlungId=16537, fallId=26866, vorgangsNr=bb5a90ed-1e91-4a5c-bd88-c52d9dfdbec1

### rechenwege

block: Kombinationsfall 55: Alleinerziehend mit 3 Kind(ern) (Schwangerschafts-Mehrbedarf entfällt im Zahlungsmonat)
zahlungsmonat = 2026-02
vermoegen = 0.00
schonvermoegen = 11500.00
budget_start = 1609.77
p1_eingaben: rbs=1, merkzeichen_g=false, schwanger=false, entbindungsmonat=2026-01, kinder_u7=2, kinder_u16=3, kinder_minderjaehrig=3, warmwasser=false, kdu_miete=700.00, kdu_betrieb=95.00, kdu_heizung=155.00, rente=0.00, erwerb=560.00, abz_steuern=190.00, abz_sv=210.00, abz_vers=30.00, abz_erwerb=20.00
p1_regelbedarf=563.00
p1_mehrbedarf_g=0.00
p1_mehrbedarf_schwanger=0.00
p1_mehrbedarf_alleinerziehend=202.68
p1_mehrbedarf_warmwasser=0.00
p1_kdu=950.00
p1_gesamtbedarf=1715.68
p1_freibetrag_erwerb=168.00
p1_absetzbetaetrag=450.00
p1_anrechenbar=0.00
p1_zahlbetrag=1715.68
p2_eingaben: rbs=6, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=0.00, kdu_betrieb=0.00, kdu_heizung=0.00, rente=0.00, erwerb=350.00, abz_steuern=200.00, abz_sv=310.00, abz_vers=15.00, abz_erwerb=0.00
p2_regelbedarf=357.00
p2_mehrbedarf_g=0.00
p2_mehrbedarf_schwanger=0.00
p2_mehrbedarf_alleinerziehend=0.00
p2_mehrbedarf_warmwasser=0.00
p2_kdu=0.00
p2_gesamtbedarf=357.00
p2_freibetrag_erwerb=105.00
p2_absetzbetaetrag=525.00
p2_anrechenbar=0.00
p2_zahlbetrag=357.00
p3_eingaben: rbs=4, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=0.00, kdu_betrieb=0.00, kdu_heizung=0.00, rente=960.00, erwerb=540.00, abz_steuern=10.00, abz_sv=110.00, abz_vers=15.00, abz_erwerb=20.00
p3_regelbedarf=471.00
p3_mehrbedarf_g=0.00
p3_mehrbedarf_schwanger=0.00
p3_mehrbedarf_alleinerziehend=0.00
p3_mehrbedarf_warmwasser=0.00
p3_kdu=0.00
p3_gesamtbedarf=471.00
p3_freibetrag_erwerb=162.00
p3_absetzbetaetrag=155.00
p3_anrechenbar=1183.00
p3_zahlbetrag=-712.00
p4_eingaben: rbs=4, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=true, kdu_miete=0.00, kdu_betrieb=0.00, kdu_heizung=0.00, rente=0.00, erwerb=1010.00, abz_steuern=140.00, abz_sv=80.00, abz_vers=60.00, abz_erwerb=30.00
p4_regelbedarf=471.00
p4_mehrbedarf_g=0.00
p4_mehrbedarf_schwanger=0.00
p4_mehrbedarf_alleinerziehend=0.00
p4_mehrbedarf_warmwasser=6.59
p4_kdu=0.00
p4_gesamtbedarf=477.59
p4_freibetrag_erwerb=281.50
p4_absetzbetaetrag=310.00
p4_anrechenbar=418.50
p4_zahlbetrag=59.09
bg_gesamtbedarf=3021.27
bg_anrechenbar=1601.50
bg_verwertbares_vermoegen=0.00
bg_zahlbetrag=1419.77
budget_rest_erwartet=190.00

block: Neuanordnung nach Mietekorrektur (Miete 700.00 → 730.00)
zahlungsmonat = 2026-02
vermoegen = 0.00
schonvermoegen = 11500.00
budget_start = 1609.77
p1_eingaben: rbs=1, merkzeichen_g=false, schwanger=false, entbindungsmonat=2026-01, kinder_u7=2, kinder_u16=3, kinder_minderjaehrig=3, warmwasser=false, kdu_miete=730.00, kdu_betrieb=95.00, kdu_heizung=155.00, rente=0.00, erwerb=560.00, abz_steuern=190.00, abz_sv=210.00, abz_vers=30.00, abz_erwerb=20.00
p1_regelbedarf=563.00
p1_mehrbedarf_g=0.00
p1_mehrbedarf_schwanger=0.00
p1_mehrbedarf_alleinerziehend=202.68
p1_mehrbedarf_warmwasser=0.00
p1_kdu=980.00
p1_gesamtbedarf=1745.68
p1_freibetrag_erwerb=168.00
p1_absetzbetaetrag=450.00
p1_anrechenbar=0.00
p1_zahlbetrag=1745.68
p2_eingaben: rbs=6, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=0.00, kdu_betrieb=0.00, kdu_heizung=0.00, rente=0.00, erwerb=350.00, abz_steuern=200.00, abz_sv=310.00, abz_vers=15.00, abz_erwerb=0.00
p2_regelbedarf=357.00
p2_mehrbedarf_g=0.00
p2_mehrbedarf_schwanger=0.00
p2_mehrbedarf_alleinerziehend=0.00
p2_mehrbedarf_warmwasser=0.00
p2_kdu=0.00
p2_gesamtbedarf=357.00
p2_freibetrag_erwerb=105.00
p2_absetzbetaetrag=525.00
p2_anrechenbar=0.00
p2_zahlbetrag=357.00
p3_eingaben: rbs=4, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=0.00, kdu_betrieb=0.00, kdu_heizung=0.00, rente=960.00, erwerb=540.00, abz_steuern=10.00, abz_sv=110.00, abz_vers=15.00, abz_erwerb=20.00
p3_regelbedarf=471.00
p3_mehrbedarf_g=0.00
p3_mehrbedarf_schwanger=0.00
p3_mehrbedarf_alleinerziehend=0.00
p3_mehrbedarf_warmwasser=0.00
p3_kdu=0.00
p3_gesamtbedarf=471.00
p3_freibetrag_erwerb=162.00
p3_absetzbetaetrag=155.00
p3_anrechenbar=1183.00
p3_zahlbetrag=-712.00
p4_eingaben: rbs=4, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=true, kdu_miete=0.00, kdu_betrieb=0.00, kdu_heizung=0.00, rente=0.00, erwerb=1010.00, abz_steuern=140.00, abz_sv=80.00, abz_vers=60.00, abz_erwerb=30.00
p4_regelbedarf=471.00
p4_mehrbedarf_g=0.00
p4_mehrbedarf_schwanger=0.00
p4_mehrbedarf_alleinerziehend=0.00
p4_mehrbedarf_warmwasser=6.59
p4_kdu=0.00
p4_gesamtbedarf=477.59
p4_freibetrag_erwerb=281.50
p4_absetzbetaetrag=310.00
p4_anrechenbar=418.50
p4_zahlbetrag=59.09
bg_gesamtbedarf=3051.27
bg_anrechenbar=1601.50
bg_verwertbares_vermoegen=0.00
bg_zahlbetrag=1449.77
budget_rest_erwartet=160.00

### verifikation

V1: OK | alle Personen als eigene SozialFall-Zeilen
    sql    = SELECT COUNT(*) FROM SOZIAL_FALL WHERE BG_ID = ?
    params = [17778]
    erwartet = 4 | ist = 4
V2: OK | finale Zahlung wirksam (Vier-Augen-Warteschlange je nach Kassenlauf)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [16556]
    erwartet = ANGELEGT|BEREIT|UEBERGEBEN | ist = ANGELEGT
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [16556]
    erwartet = 1449.77 | ist = 1449.77
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17778]
    erwartet = 160.00 | ist = 160
V5: OK | zwei Zahlungen (stornierte + korrigierte)
    sql    = SELECT COUNT(*) FROM ZAHLUNG WHERE BG_ID = ?
    params = [17778]
    erwartet = 2 | ist = 2
V6: OK | erste Zahlung STORNIERT
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [16537]
    erwartet = STORNIERT | ist = STORNIERT
V7: OK | Fall in Version 2 (optimistisches Locking)
    sql    = SELECT VERSION FROM SOZIAL_FALL WHERE ID = ?
    params = [26866]
    erwartet = 2 | ist = 2
V8: OK | genau ein erteilter Bewilligungsbescheid
    sql    = SELECT COUNT(*) FROM BESCHEID WHERE BG_ID = ?
    params = [17778]
    erwartet = 1 | ist = 1
V9: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:20:39.976 sb-sim-01 | Vorgang anlegen (Alleinerziehend mit 3 Kind(ern), Budget 1609.77 EUR) -> OK — Vorgang 17778
2026-10-13 11:20:40.028 sb-sim-01 | 4 SozialFall/Fälle erfassen -> OK — Alleinerziehend mit 3 Kind(ern), KdU 950.00 EUR
2026-10-13 11:20:40.050 sb-sim-01 | Bewilligungsbescheid erteilen (2026-01 .. 2026-06) -> OK — Bescheid BESCH-SWEET-0055-2026-1743 mit Monatsplan gespeichert
2026-10-13 11:20:40.068 sb-sim-01 | Auszahlung anordnen (2026-02) -> OK — 1419.77 EUR (ANGELEGT)
2026-10-13 11:20:40.078 sb-sim-01 | Anordnung sofort stornieren (Status ANGELEGT) -> OK — storniert — Monat wieder frei
2026-10-13 11:20:40.102 sb-sim-01 | Miete auf 730.00 korrigieren -> OK — Fall 26866 in Version 2
2026-10-13 11:20:40.122 sb-sim-01 | Korrigierte Auszahlung für denselben Monat -> OK — 1449.77 EUR

## TF 56 KOMBINATION SWEET-0056
status = VERIFIZIERT
hauptperson = Wagner, Clara
bearbeiter = sb-sim-10
bg_id = 17779
dauer_ms = 57
ids = zahlungId=16516, vorgangsNr=25a17e83-343c-478a-a825-a26e84864a92, mitarbeiter=sb-sim-10, bgId=17779, letzteZahlungId=16516, kollege=sb-sim-07

### rechenwege

block: Kombinationsfall 56: Single
zahlungsmonat = 2026-10
vermoegen = 10100.00
schonvermoegen = 10000.00
budget_start = 509.50
p1_eingaben: rbs=1, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=300.00, kdu_betrieb=55.00, kdu_heizung=50.00, rente=110.00, erwerb=1000.00, abz_steuern=20.00, abz_sv=170.00, abz_vers=20.00, abz_erwerb=50.00
p1_regelbedarf=563.00
p1_mehrbedarf_g=0.00
p1_mehrbedarf_schwanger=0.00
p1_mehrbedarf_alleinerziehend=0.00
p1_mehrbedarf_warmwasser=0.00
p1_kdu=405.00
p1_gesamtbedarf=968.00
p1_freibetrag_erwerb=281.50
p1_absetzbetaetrag=260.00
p1_anrechenbar=568.50
p1_zahlbetrag=399.50
bg_gesamtbedarf=968.00
bg_anrechenbar=568.50
bg_verwertbares_vermoegen=100.00
bg_zahlbetrag=299.50
budget_rest_erwartet=210.00

### verifikation

V1: OK | alle Personen als eigene SozialFall-Zeilen
    sql    = SELECT COUNT(*) FROM SOZIAL_FALL WHERE BG_ID = ?
    params = [17779]
    erwartet = 1 | ist = 1
V2: OK | finale Zahlung freigegeben (oder bereits übergeben)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [16516]
    erwartet = BEREIT|UEBERGEBEN | ist = UEBERGEBEN
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [16516]
    erwartet = 299.50 | ist = 299.5
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17779]
    erwartet = 210.00 | ist = 210
V5: OK | Freigabevermerk nennt den Kollegen (Vier-Augen)
    sql    = SELECT CASE WHEN FREIGEGEBEN_VON = ? THEN 'ok' ELSE 'abweichend' END FROM ZAHLUNG WHERE ID = ?
    params = [sb-sim-07, 16516]
    erwartet = ok | ist = ok
V6: OK | Vermögen nach Verbrauchsfortschreibung (§ 90)
    sql    = SELECT VERMOEGEN FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17779]
    erwartet = 10000.00 | ist = 10000
V7: OK | dokumentierter Vermögenseinsatz an der Zahlung
    sql    = SELECT VERMOEGENSEINSATZ FROM ZAHLUNG WHERE ID = ?
    params = [16516]
    erwartet = 100.00 | ist = 100
V8: OK | Wiedervorlagedatum exakt wie gesetzt
    sql    = SELECT COUNT(*) FROM BEDARFSGEMEINSCHAFT WHERE ID = ? AND WIEDERVORLAGE_AM = ?
    params = [17779, 2026-12-29]
    erwartet = 1 | ist = 1
V9: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:20:39.976 sb-sim-10 | Vorgang anlegen (Single, Budget 509.50 EUR, Vermögen 10100.00 EUR) -> OK — Vorgang 17779
2026-10-13 11:20:39.988 sb-sim-10 | 1 SozialFall/Fälle erfassen -> OK — Single, KdU 405.00 EUR
2026-10-13 11:20:40.007 sb-sim-10 | Auszahlung anordnen (2026-10) -> OK — 299.50 EUR (ANGELEGT)
2026-10-13 11:20:40.022 sb-sim-10 | Freigabe durch Kollegen (Vier-Augen) -> OK — freigegeben durch sb-sim-07

## TF 57 KOMBINATION SWEET-0057
status = VERIFIZIERT
hauptperson = Fischer, Dieter
bearbeiter = sb-sim-19
bg_id = 17780
dauer_ms = 86
ids = zahlungId=16531, vorgangsNr=5d315a00-fb24-4565-ba8c-a78a004c8869, mitarbeiter=sb-sim-19, bgId=17780, letzteZahlungId=16531, kollege=sb-sim-02

### rechenwege

block: Kombinationsfall 57: Paar (2 × RBS 2) (Schwangerschafts-Mehrbedarf entfällt im Zahlungsmonat)
zahlungsmonat = 2026-07
vermoegen = 21650.00
schonvermoegen = 20000.00
budget_start = 484.66
p1_eingaben: rbs=2, merkzeichen_g=false, schwanger=false, entbindungsmonat=2026-05, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=true, kdu_miete=500.00, kdu_betrieb=70.00, kdu_heizung=165.00, rente=0.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p1_regelbedarf=506.00
p1_mehrbedarf_g=0.00
p1_mehrbedarf_schwanger=0.00
p1_mehrbedarf_alleinerziehend=0.00
p1_mehrbedarf_warmwasser=11.64
p1_kdu=735.00
p1_gesamtbedarf=1252.64
p1_freibetrag_erwerb=0.00
p1_absetzbetaetrag=0.00
p1_anrechenbar=0.00
p1_zahlbetrag=1252.64
p2_eingaben: rbs=2, merkzeichen_g=true, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=0.00, kdu_betrieb=0.00, kdu_heizung=0.00, rente=0.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p2_regelbedarf=506.00
p2_mehrbedarf_g=86.02
p2_mehrbedarf_schwanger=0.00
p2_mehrbedarf_alleinerziehend=0.00
p2_mehrbedarf_warmwasser=0.00
p2_kdu=0.00
p2_gesamtbedarf=592.02
p2_freibetrag_erwerb=0.00
p2_absetzbetaetrag=0.00
p2_anrechenbar=0.00
p2_zahlbetrag=592.02
bg_gesamtbedarf=1844.66
bg_anrechenbar=0.00
bg_verwertbares_vermoegen=1650.00
bg_zahlbetrag=194.66
budget_rest_erwartet=290.00

### verifikation

V1: OK | alle Personen als eigene SozialFall-Zeilen
    sql    = SELECT COUNT(*) FROM SOZIAL_FALL WHERE BG_ID = ?
    params = [17780]
    erwartet = 2 | ist = 2
V2: OK | finale Zahlung freigegeben (oder bereits übergeben)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [16531]
    erwartet = BEREIT|UEBERGEBEN | ist = UEBERGEBEN
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [16531]
    erwartet = 194.66 | ist = 194.66
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17780]
    erwartet = 290.00 | ist = 290
V5: OK | Freigabevermerk nennt den Kollegen (Vier-Augen)
    sql    = SELECT CASE WHEN FREIGEGEBEN_VON = ? THEN 'ok' ELSE 'abweichend' END FROM ZAHLUNG WHERE ID = ?
    params = [sb-sim-02, 16531]
    erwartet = ok | ist = ok
V6: OK | genau ein erteilter Bewilligungsbescheid
    sql    = SELECT COUNT(*) FROM BESCHEID WHERE BG_ID = ?
    params = [17780]
    erwartet = 1 | ist = 1
V7: OK | Vermögen nach Verbrauchsfortschreibung (§ 90)
    sql    = SELECT VERMOEGEN FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17780]
    erwartet = 20000.00 | ist = 20000
V8: OK | dokumentierter Vermögenseinsatz an der Zahlung
    sql    = SELECT VERMOEGENSEINSATZ FROM ZAHLUNG WHERE ID = ?
    params = [16531]
    erwartet = 1650.00 | ist = 1650
V9: OK | Wiedervorlagedatum exakt wie gesetzt
    sql    = SELECT COUNT(*) FROM BEDARFSGEMEINSCHAFT WHERE ID = ? AND WIEDERVORLAGE_AM = ?
    params = [17780, 2027-05-09]
    erwartet = 1 | ist = 1
V10: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:20:39.986 sb-sim-19 | Vorgang anlegen (Paar (2 × RBS 2), Budget 484.66 EUR, Vermögen 21650.00 EUR) -> OK — Vorgang 17780
2026-10-13 11:20:40.013 sb-sim-19 | 2 SozialFall/Fälle erfassen -> OK — Paar (2 × RBS 2), KdU 735.00 EUR
2026-10-13 11:20:40.030 sb-sim-19 | Bewilligungsbescheid erteilen (2026-07 .. 2026-07) -> OK — Bescheid BESCH-SWEET-0057-2026-1742 mit Monatsplan gespeichert
2026-10-13 11:20:40.048 sb-sim-19 | Auszahlung anordnen (2026-07) -> OK — 194.66 EUR (ANGELEGT)
2026-10-13 11:20:40.060 sb-sim-19 | Freigabe durch Kollegen (Vier-Augen) -> OK — freigegeben durch sb-sim-02

## TF 58 KOMBINATION SWEET-0058
status = VERIFIZIERT
hauptperson = Ismail, Karin
bearbeiter = sb-sim-13
bg_id = 17781
dauer_ms = 103
ids = letzteZahlungId=16541, kollege=sb-sim-16, bgId=17781, mitarbeiter=sb-sim-13, zahlungId=16523, fallId=26869, vorgangsNr=a9b74fd9-e370-43aa-b270-b29994e51bc8

### rechenwege

block: Kombinationsfall 58: Alleinerziehend mit 1 Kind(ern)
zahlungsmonat = 2026-07
vermoegen = 0.00
schonvermoegen = 10500.00
budget_start = 874.27
p1_eingaben: rbs=1, merkzeichen_g=true, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=1, kinder_minderjaehrig=1, warmwasser=false, kdu_miete=270.00, kdu_betrieb=115.00, kdu_heizung=65.00, rente=1040.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p1_regelbedarf=563.00
p1_mehrbedarf_g=95.71
p1_mehrbedarf_schwanger=0.00
p1_mehrbedarf_alleinerziehend=67.56
p1_mehrbedarf_warmwasser=0.00
p1_kdu=450.00
p1_gesamtbedarf=1176.27
p1_freibetrag_erwerb=0.00
p1_absetzbetaetrag=0.00
p1_anrechenbar=1040.00
p1_zahlbetrag=136.27
p2_eingaben: rbs=6, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=0.00, kdu_betrieb=0.00, kdu_heizung=0.00, rente=390.00, erwerb=320.00, abz_steuern=110.00, abz_sv=350.00, abz_vers=35.00, abz_erwerb=20.00
p2_regelbedarf=357.00
p2_mehrbedarf_g=0.00
p2_mehrbedarf_schwanger=0.00
p2_mehrbedarf_alleinerziehend=0.00
p2_mehrbedarf_warmwasser=0.00
p2_kdu=0.00
p2_gesamtbedarf=357.00
p2_freibetrag_erwerb=96.00
p2_absetzbetaetrag=515.00
p2_anrechenbar=99.00
p2_zahlbetrag=258.00
bg_gesamtbedarf=1533.27
bg_anrechenbar=1139.00
bg_verwertbares_vermoegen=0.00
bg_zahlbetrag=394.27
budget_rest_erwartet=480.00

block: Neuanordnung nach Mietekorrektur (Miete 270.00 → 420.00)
zahlungsmonat = 2026-07
vermoegen = 0.00
schonvermoegen = 10500.00
budget_start = 874.27
p1_eingaben: rbs=1, merkzeichen_g=true, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=1, kinder_minderjaehrig=1, warmwasser=false, kdu_miete=420.00, kdu_betrieb=115.00, kdu_heizung=65.00, rente=1040.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p1_regelbedarf=563.00
p1_mehrbedarf_g=95.71
p1_mehrbedarf_schwanger=0.00
p1_mehrbedarf_alleinerziehend=67.56
p1_mehrbedarf_warmwasser=0.00
p1_kdu=600.00
p1_gesamtbedarf=1326.27
p1_freibetrag_erwerb=0.00
p1_absetzbetaetrag=0.00
p1_anrechenbar=1040.00
p1_zahlbetrag=286.27
p2_eingaben: rbs=6, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=0.00, kdu_betrieb=0.00, kdu_heizung=0.00, rente=390.00, erwerb=320.00, abz_steuern=110.00, abz_sv=350.00, abz_vers=35.00, abz_erwerb=20.00
p2_regelbedarf=357.00
p2_mehrbedarf_g=0.00
p2_mehrbedarf_schwanger=0.00
p2_mehrbedarf_alleinerziehend=0.00
p2_mehrbedarf_warmwasser=0.00
p2_kdu=0.00
p2_gesamtbedarf=357.00
p2_freibetrag_erwerb=96.00
p2_absetzbetaetrag=515.00
p2_anrechenbar=99.00
p2_zahlbetrag=258.00
bg_gesamtbedarf=1683.27
bg_anrechenbar=1139.00
bg_verwertbares_vermoegen=0.00
bg_zahlbetrag=544.27
budget_rest_erwartet=330.00

### verifikation

V1: OK | alle Personen als eigene SozialFall-Zeilen
    sql    = SELECT COUNT(*) FROM SOZIAL_FALL WHERE BG_ID = ?
    params = [17781]
    erwartet = 2 | ist = 2
V2: OK | finale Zahlung wirksam (Vier-Augen-Warteschlange je nach Kassenlauf)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [16541]
    erwartet = ANGELEGT|BEREIT|UEBERGEBEN | ist = ANGELEGT
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [16541]
    erwartet = 544.27 | ist = 544.27
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17781]
    erwartet = 330.00 | ist = 330
V5: OK | zwei Zahlungen (stornierte + korrigierte)
    sql    = SELECT COUNT(*) FROM ZAHLUNG WHERE BG_ID = ?
    params = [17781]
    erwartet = 2 | ist = 2
V6: OK | erste Zahlung STORNIERT
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [16523]
    erwartet = STORNIERT | ist = STORNIERT
V7: OK | Fall in Version 2 (optimistisches Locking)
    sql    = SELECT VERSION FROM SOZIAL_FALL WHERE ID = ?
    params = [26869]
    erwartet = 2 | ist = 2
V8: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:20:39.988 sb-sim-13 | Vorgang anlegen (Alleinerziehend mit 1 Kind(ern), Budget 874.27 EUR) -> OK — Vorgang 17781
2026-10-13 11:20:40.010 sb-sim-13 | 2 SozialFall/Fälle erfassen -> OK — Alleinerziehend mit 1 Kind(ern), KdU 450.00 EUR
2026-10-13 11:20:40.028 sb-sim-13 | Auszahlung anordnen (2026-07) -> OK — 394.27 EUR (ANGELEGT)
2026-10-13 11:20:40.040 sb-sim-13 | Anordnung sofort stornieren (Status ANGELEGT) -> OK — storniert — Monat wieder frei
2026-10-13 11:20:40.060 sb-sim-13 | Miete auf 420.00 korrigieren -> OK — Fall 26869 in Version 2
2026-10-13 11:20:40.078 sb-sim-13 | Korrigierte Auszahlung für denselben Monat -> OK — 544.27 EUR

## TF 59 KOMBINATION SWEET-0059
status = VERIFIZIERT
hauptperson = Dietrich, Heinz
bearbeiter = sb-sim-18
bg_id = 17782
dauer_ms = 61
ids = zahlungId=16524, vorgangsNr=cb025862-5b4c-4c86-afbc-e7a9c714ef25, mitarbeiter=sb-sim-18, bgId=17782, letzteZahlungId=16524, kollege=sb-sim-09

### rechenwege

block: Kombinationsfall 59: Alleinerziehend mit 1 Kind(ern)
zahlungsmonat = 2026-02
vermoegen = 0.00
schonvermoegen = 10500.00
budget_start = 611.63
p1_eingaben: rbs=1, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=1, kinder_minderjaehrig=1, warmwasser=false, kdu_miete=700.00, kdu_betrieb=70.00, kdu_heizung=50.00, rente=850.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p1_regelbedarf=563.00
p1_mehrbedarf_g=0.00
p1_mehrbedarf_schwanger=0.00
p1_mehrbedarf_alleinerziehend=67.56
p1_mehrbedarf_warmwasser=0.00
p1_kdu=820.00
p1_gesamtbedarf=1450.56
p1_freibetrag_erwerb=0.00
p1_absetzbetaetrag=0.00
p1_anrechenbar=850.00
p1_zahlbetrag=600.56
p2_eingaben: rbs=4, merkzeichen_g=true, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=0.00, kdu_betrieb=0.00, kdu_heizung=0.00, rente=700.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p2_regelbedarf=471.00
p2_mehrbedarf_g=80.07
p2_mehrbedarf_schwanger=0.00
p2_mehrbedarf_alleinerziehend=0.00
p2_mehrbedarf_warmwasser=0.00
p2_kdu=0.00
p2_gesamtbedarf=551.07
p2_freibetrag_erwerb=0.00
p2_absetzbetaetrag=0.00
p2_anrechenbar=700.00
p2_zahlbetrag=-148.93
bg_gesamtbedarf=2001.63
bg_anrechenbar=1550.00
bg_verwertbares_vermoegen=0.00
bg_zahlbetrag=451.63
budget_rest_erwartet=160.00

### verifikation

V1: OK | alle Personen als eigene SozialFall-Zeilen
    sql    = SELECT COUNT(*) FROM SOZIAL_FALL WHERE BG_ID = ?
    params = [17782]
    erwartet = 2 | ist = 2
V2: OK | finale Zahlung freigegeben (oder bereits übergeben)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [16524]
    erwartet = BEREIT|UEBERGEBEN | ist = UEBERGEBEN
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [16524]
    erwartet = 451.63 | ist = 451.63
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17782]
    erwartet = 160.00 | ist = 160
V5: OK | Freigabevermerk nennt den Kollegen (Vier-Augen)
    sql    = SELECT CASE WHEN FREIGEGEBEN_VON = ? THEN 'ok' ELSE 'abweichend' END FROM ZAHLUNG WHERE ID = ?
    params = [sb-sim-09, 16524]
    erwartet = ok | ist = ok
V6: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:20:39.988 sb-sim-18 | Vorgang anlegen (Alleinerziehend mit 1 Kind(ern), Budget 611.63 EUR) -> OK — Vorgang 17782
2026-10-13 11:20:40.012 sb-sim-18 | 2 SozialFall/Fälle erfassen -> OK — Alleinerziehend mit 1 Kind(ern), KdU 820.00 EUR
2026-10-13 11:20:40.028 sb-sim-18 | Auszahlung anordnen (2026-02) -> OK — 451.63 EUR (ANGELEGT)
2026-10-13 11:20:40.040 sb-sim-18 | Freigabe durch Kollegen (Vier-Augen) -> OK — freigegeben durch sb-sim-09

## TF 60 KOMBINATION SWEET-0060
status = VERIFIZIERT
hauptperson = Jung, Clara
bearbeiter = sb-sim-06
bg_id = 17783
dauer_ms = 57
ids = zahlungId=16528, vorgangsNr=b557c9ab-453c-4237-afca-bea956b349b1, mitarbeiter=sb-sim-06, bgId=17783, letzteZahlungId=16528, kollege=sb-sim-14

### rechenwege

block: Kombinationsfall 60: Alleinerziehend mit 1 Kind(ern)
zahlungsmonat = 2026-03
vermoegen = 10650.00
schonvermoegen = 10500.00
budget_start = 1571.66
p1_eingaben: rbs=1, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=1, kinder_u16=1, kinder_minderjaehrig=1, warmwasser=false, kdu_miete=640.00, kdu_betrieb=50.00, kdu_heizung=145.00, rente=260.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p1_regelbedarf=563.00
p1_mehrbedarf_g=0.00
p1_mehrbedarf_schwanger=0.00
p1_mehrbedarf_alleinerziehend=202.68
p1_mehrbedarf_warmwasser=0.00
p1_kdu=835.00
p1_gesamtbedarf=1600.68
p1_freibetrag_erwerb=0.00
p1_absetzbetaetrag=0.00
p1_anrechenbar=260.00
p1_zahlbetrag=1340.68
p2_eingaben: rbs=5, merkzeichen_g=true, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=true, kdu_miete=0.00, kdu_betrieb=0.00, kdu_heizung=0.00, rente=420.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p2_regelbedarf=390.00
p2_mehrbedarf_g=66.30
p2_mehrbedarf_schwanger=0.00
p2_mehrbedarf_alleinerziehend=0.00
p2_mehrbedarf_warmwasser=4.68
p2_kdu=0.00
p2_gesamtbedarf=460.98
p2_freibetrag_erwerb=0.00
p2_absetzbetaetrag=0.00
p2_anrechenbar=420.00
p2_zahlbetrag=40.98
bg_gesamtbedarf=2061.66
bg_anrechenbar=680.00
bg_verwertbares_vermoegen=150.00
bg_zahlbetrag=1231.66
budget_rest_erwartet=340.00

### verifikation

V1: OK | alle Personen als eigene SozialFall-Zeilen
    sql    = SELECT COUNT(*) FROM SOZIAL_FALL WHERE BG_ID = ?
    params = [17783]
    erwartet = 2 | ist = 2
V2: OK | finale Zahlung wirksam (Vier-Augen-Warteschlange je nach Kassenlauf)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [16528]
    erwartet = ANGELEGT|BEREIT|UEBERGEBEN | ist = ANGELEGT
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [16528]
    erwartet = 1231.66 | ist = 1231.66
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17783]
    erwartet = 340.00 | ist = 340
V5: OK | Vermögen nach Verbrauchsfortschreibung (§ 90)
    sql    = SELECT VERMOEGEN FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17783]
    erwartet = 10500.00 | ist = 10500
V6: OK | dokumentierter Vermögenseinsatz an der Zahlung
    sql    = SELECT VERMOEGENSEINSATZ FROM ZAHLUNG WHERE ID = ?
    params = [16528]
    erwartet = 150.00 | ist = 150
V7: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:20:40.001 sb-sim-06 | Vorgang anlegen (Alleinerziehend mit 1 Kind(ern), Budget 1571.66 EUR, Vermögen 10650.00 EUR) -> OK — Vorgang 17783
2026-10-13 11:20:40.026 sb-sim-06 | 2 SozialFall/Fälle erfassen -> OK — Alleinerziehend mit 1 Kind(ern), KdU 835.00 EUR
2026-10-13 11:20:40.046 sb-sim-06 | Auszahlung anordnen (2026-03) -> OK — 1231.66 EUR (ANGELEGT)

## TF 61 KOMBINATION SWEET-0061
status = VERIFIZIERT
hauptperson = Sauber, Volkmar
bearbeiter = sb-sim-12
bg_id = 17784
dauer_ms = 37
ids = zahlungId=16525, vorgangsNr=67f7356d-cb91-4258-a6b5-ed63dfd19d89, mitarbeiter=sb-sim-12, bgId=17784, letzteZahlungId=16525, kollege=sb-sim-01

### rechenwege

block: Kombinationsfall 61: Single
zahlungsmonat = 2026-07
vermoegen = 0.00
schonvermoegen = 10000.00
budget_start = 934.00
p1_eingaben: rbs=1, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=350.00, kdu_betrieb=65.00, kdu_heizung=160.00, rente=180.00, erwerb=820.00, abz_steuern=160.00, abz_sv=90.00, abz_vers=0.00, abz_erwerb=60.00
p1_regelbedarf=563.00
p1_mehrbedarf_g=0.00
p1_mehrbedarf_schwanger=0.00
p1_mehrbedarf_alleinerziehend=0.00
p1_mehrbedarf_warmwasser=0.00
p1_kdu=575.00
p1_gesamtbedarf=1138.00
p1_freibetrag_erwerb=246.00
p1_absetzbetaetrag=310.00
p1_anrechenbar=444.00
p1_zahlbetrag=694.00
bg_gesamtbedarf=1138.00
bg_anrechenbar=444.00
bg_verwertbares_vermoegen=0.00
bg_zahlbetrag=694.00
budget_rest_erwartet=240.00

### verifikation

V1: OK | alle Personen als eigene SozialFall-Zeilen
    sql    = SELECT COUNT(*) FROM SOZIAL_FALL WHERE BG_ID = ?
    params = [17784]
    erwartet = 1 | ist = 1
V2: OK | finale Zahlung wirksam (Vier-Augen-Warteschlange je nach Kassenlauf)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [16525]
    erwartet = ANGELEGT|BEREIT|UEBERGEBEN | ist = ANGELEGT
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [16525]
    erwartet = 694.00 | ist = 694
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17784]
    erwartet = 240.00 | ist = 240
V5: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:20:40.004 sb-sim-12 | Vorgang anlegen (Single, Budget 934.00 EUR) -> OK — Vorgang 17784
2026-10-13 11:20:40.016 sb-sim-12 | 1 SozialFall/Fälle erfassen -> OK — Single, KdU 575.00 EUR
2026-10-13 11:20:40.033 sb-sim-12 | Auszahlung anordnen (2026-07) -> OK — 694.00 EUR (ANGELEGT)

## TF 62 KOMBINATION SWEET-0062
status = VERIFIZIERT
hauptperson = Egger, Bernd
bearbeiter = sb-sim-09
bg_id = 17785
dauer_ms = 38
ids = zahlungId=16526, vorgangsNr=ce5b1458-3cc8-402d-a537-9ecfb4d9c35d, mitarbeiter=sb-sim-09, bgId=17785, letzteZahlungId=16526, kollege=sb-sim-06

### rechenwege

block: Kombinationsfall 62: Single
zahlungsmonat = 2026-04
vermoegen = 0.00
schonvermoegen = 10000.00
budget_start = 395.95
p1_eingaben: rbs=1, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=true, kdu_miete=250.00, kdu_betrieb=85.00, kdu_heizung=105.00, rente=790.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p1_regelbedarf=563.00
p1_mehrbedarf_g=0.00
p1_mehrbedarf_schwanger=0.00
p1_mehrbedarf_alleinerziehend=0.00
p1_mehrbedarf_warmwasser=12.95
p1_kdu=440.00
p1_gesamtbedarf=1015.95
p1_freibetrag_erwerb=0.00
p1_absetzbetaetrag=0.00
p1_anrechenbar=790.00
p1_zahlbetrag=225.95
bg_gesamtbedarf=1015.95
bg_anrechenbar=790.00
bg_verwertbares_vermoegen=0.00
bg_zahlbetrag=225.95
budget_rest_erwartet=170.00

### verifikation

V1: OK | alle Personen als eigene SozialFall-Zeilen
    sql    = SELECT COUNT(*) FROM SOZIAL_FALL WHERE BG_ID = ?
    params = [17785]
    erwartet = 1 | ist = 1
V2: OK | finale Zahlung wirksam (Vier-Augen-Warteschlange je nach Kassenlauf)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [16526]
    erwartet = ANGELEGT|BEREIT|UEBERGEBEN | ist = ANGELEGT
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [16526]
    erwartet = 225.95 | ist = 225.95
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17785]
    erwartet = 170.00 | ist = 170
V5: OK | Wiedervorlagedatum exakt wie gesetzt
    sql    = SELECT COUNT(*) FROM BEDARFSGEMEINSCHAFT WHERE ID = ? AND WIEDERVORLAGE_AM = ?
    params = [17785, 2027-03-02]
    erwartet = 1 | ist = 1
V6: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:20:40.010 sb-sim-09 | Vorgang anlegen (Single, Budget 395.95 EUR) -> OK — Vorgang 17785
2026-10-13 11:20:40.022 sb-sim-09 | 1 SozialFall/Fälle erfassen -> OK — Single, KdU 440.00 EUR
2026-10-13 11:20:40.040 sb-sim-09 | Auszahlung anordnen (2026-04) -> OK — 225.95 EUR (ANGELEGT)

## TF 63 KOMBINATION SWEET-0063
status = VERIFIZIERT
hauptperson = Jung, Ursula
bearbeiter = sb-sim-17
bg_id = 17786
dauer_ms = 45
ids = zahlungId=16527, vorgangsNr=91291b6b-d298-448f-946d-ac66f648d3c2, mitarbeiter=sb-sim-17, bgId=17786, letzteZahlungId=16527, kollege=sb-sim-11

### rechenwege

block: Kombinationsfall 63: Single (Schwangerschafts-Mehrbedarf entfällt im Zahlungsmonat)
zahlungsmonat = 2026-07
vermoegen = 10150.00
schonvermoegen = 10000.00
budget_start = 508.00
p1_eingaben: rbs=1, merkzeichen_g=false, schwanger=false, entbindungsmonat=2026-06, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=590.00, kdu_betrieb=125.00, kdu_heizung=50.00, rente=1030.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p1_regelbedarf=563.00
p1_mehrbedarf_g=0.00
p1_mehrbedarf_schwanger=0.00
p1_mehrbedarf_alleinerziehend=0.00
p1_mehrbedarf_warmwasser=0.00
p1_kdu=765.00
p1_gesamtbedarf=1328.00
p1_freibetrag_erwerb=0.00
p1_absetzbetaetrag=0.00
p1_anrechenbar=1030.00
p1_zahlbetrag=298.00
bg_gesamtbedarf=1328.00
bg_anrechenbar=1030.00
bg_verwertbares_vermoegen=150.00
bg_zahlbetrag=148.00
budget_rest_erwartet=360.00

### verifikation

V1: OK | alle Personen als eigene SozialFall-Zeilen
    sql    = SELECT COUNT(*) FROM SOZIAL_FALL WHERE BG_ID = ?
    params = [17786]
    erwartet = 1 | ist = 1
V2: OK | finale Zahlung wirksam (Vier-Augen-Warteschlange je nach Kassenlauf)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [16527]
    erwartet = ANGELEGT|BEREIT|UEBERGEBEN | ist = ANGELEGT
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [16527]
    erwartet = 148.00 | ist = 148
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17786]
    erwartet = 360.00 | ist = 360
V5: OK | Vermögen nach Verbrauchsfortschreibung (§ 90)
    sql    = SELECT VERMOEGEN FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17786]
    erwartet = 10000.00 | ist = 10000
V6: OK | dokumentierter Vermögenseinsatz an der Zahlung
    sql    = SELECT VERMOEGENSEINSATZ FROM ZAHLUNG WHERE ID = ?
    params = [16527]
    erwartet = 150.00 | ist = 150
V7: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:20:40.010 sb-sim-17 | Vorgang anlegen (Single, Budget 508.00 EUR, Vermögen 10150.00 EUR) -> OK — Vorgang 17786
2026-10-13 11:20:40.026 sb-sim-17 | 1 SozialFall/Fälle erfassen -> OK — Single, KdU 765.00 EUR
2026-10-13 11:20:40.046 sb-sim-17 | Auszahlung anordnen (2026-07) -> OK — 148.00 EUR (ANGELEGT)

## TF 64 KOMBINATION SWEET-0064
status = VERIFIZIERT
hauptperson = Brandt, Elke
bearbeiter = sb-sim-02
bg_id = 17787
dauer_ms = 47
ids = zahlungId=16533, vorgangsNr=fad6b336-c5c9-456a-8740-48a24546bbd0, mitarbeiter=sb-sim-02, bgId=17787, letzteZahlungId=16533, kollege=sb-sim-16

### rechenwege

block: Kombinationsfall 64: Paar (2 × RBS 2)
zahlungsmonat = 2026-04
vermoegen = 20250.00
schonvermoegen = 20000.00
budget_start = 122.18
p1_eingaben: rbs=2, merkzeichen_g=false, schwanger=true, entbindungsmonat=2026-08, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=690.00, kdu_betrieb=100.00, kdu_heizung=125.00, rente=980.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p1_regelbedarf=506.00
p1_mehrbedarf_g=0.00
p1_mehrbedarf_schwanger=86.02
p1_mehrbedarf_alleinerziehend=0.00
p1_mehrbedarf_warmwasser=0.00
p1_kdu=915.00
p1_gesamtbedarf=1507.02
p1_freibetrag_erwerb=0.00
p1_absetzbetaetrag=0.00
p1_anrechenbar=980.00
p1_zahlbetrag=527.02
p2_eingaben: rbs=2, merkzeichen_g=true, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=true, kdu_miete=0.00, kdu_betrieb=0.00, kdu_heizung=0.00, rente=490.00, erwerb=1110.00, abz_steuern=150.00, abz_sv=320.00, abz_vers=0.00, abz_erwerb=40.00
p2_regelbedarf=506.00
p2_mehrbedarf_g=86.02
p2_mehrbedarf_schwanger=0.00
p2_mehrbedarf_alleinerziehend=0.00
p2_mehrbedarf_warmwasser=11.64
p2_kdu=0.00
p2_gesamtbedarf=603.66
p2_freibetrag_erwerb=281.50
p2_absetzbetaetrag=510.00
p2_anrechenbar=808.50
p2_zahlbetrag=-204.84
bg_gesamtbedarf=2110.68
bg_anrechenbar=1788.50
bg_verwertbares_vermoegen=250.00
bg_zahlbetrag=72.18
budget_rest_erwartet=50.00

### verifikation

V1: OK | alle Personen als eigene SozialFall-Zeilen
    sql    = SELECT COUNT(*) FROM SOZIAL_FALL WHERE BG_ID = ?
    params = [17787]
    erwartet = 2 | ist = 2
V2: OK | finale Zahlung wirksam (Vier-Augen-Warteschlange je nach Kassenlauf)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [16533]
    erwartet = ANGELEGT|BEREIT|UEBERGEBEN | ist = ANGELEGT
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [16533]
    erwartet = 72.18 | ist = 72.18
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17787]
    erwartet = 50.00 | ist = 50
V5: OK | Vermögen nach Verbrauchsfortschreibung (§ 90)
    sql    = SELECT VERMOEGEN FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17787]
    erwartet = 20000.00 | ist = 20000
V6: OK | dokumentierter Vermögenseinsatz an der Zahlung
    sql    = SELECT VERMOEGENSEINSATZ FROM ZAHLUNG WHERE ID = ?
    params = [16533]
    erwartet = 250.00 | ist = 250
V7: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:20:40.014 sb-sim-02 | Vorgang anlegen (Paar (2 × RBS 2), Budget 122.18 EUR, Vermögen 20250.00 EUR) -> OK — Vorgang 17787
2026-10-13 11:20:40.036 sb-sim-02 | 2 SozialFall/Fälle erfassen -> OK — Paar (2 × RBS 2), KdU 915.00 EUR
2026-10-13 11:20:40.054 sb-sim-02 | Auszahlung anordnen (2026-04) -> OK — 72.18 EUR (ANGELEGT)

## TF 65 KOMBINATION SWEET-0065
status = VERIFIZIERT
hauptperson = Wagner, Bernd
bearbeiter = sb-sim-16
bg_id = 17788
dauer_ms = 86
ids = zahlungId=16543, vorgangsNr=ae1aa510-a28e-4c6a-bedc-55def9e1f1be, mitarbeiter=sb-sim-16, bgId=17788, letzteZahlungId=16543, kollege=sb-sim-08

### rechenwege

block: Kombinationsfall 65: Alleinerziehend mit 2 Kind(ern)
zahlungsmonat = 2026-02
vermoegen = 12600.00
schonvermoegen = 11000.00
budget_start = 882.68
p1_eingaben: rbs=1, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=2, kinder_minderjaehrig=2, warmwasser=false, kdu_miete=560.00, kdu_betrieb=95.00, kdu_heizung=50.00, rente=0.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p1_regelbedarf=563.00
p1_mehrbedarf_g=0.00
p1_mehrbedarf_schwanger=0.00
p1_mehrbedarf_alleinerziehend=202.68
p1_mehrbedarf_warmwasser=0.00
p1_kdu=705.00
p1_gesamtbedarf=1470.68
p1_freibetrag_erwerb=0.00
p1_absetzbetaetrag=0.00
p1_anrechenbar=0.00
p1_zahlbetrag=1470.68
p2_eingaben: rbs=4, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=0.00, kdu_betrieb=0.00, kdu_heizung=0.00, rente=120.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p2_regelbedarf=471.00
p2_mehrbedarf_g=0.00
p2_mehrbedarf_schwanger=0.00
p2_mehrbedarf_alleinerziehend=0.00
p2_mehrbedarf_warmwasser=0.00
p2_kdu=0.00
p2_gesamtbedarf=471.00
p2_freibetrag_erwerb=0.00
p2_absetzbetaetrag=0.00
p2_anrechenbar=120.00
p2_zahlbetrag=351.00
p3_eingaben: rbs=4, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=0.00, kdu_betrieb=0.00, kdu_heizung=0.00, rente=0.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p3_regelbedarf=471.00
p3_mehrbedarf_g=0.00
p3_mehrbedarf_schwanger=0.00
p3_mehrbedarf_alleinerziehend=0.00
p3_mehrbedarf_warmwasser=0.00
p3_kdu=0.00
p3_gesamtbedarf=471.00
p3_freibetrag_erwerb=0.00
p3_absetzbetaetrag=0.00
p3_anrechenbar=0.00
p3_zahlbetrag=471.00
bg_gesamtbedarf=2412.68
bg_anrechenbar=120.00
bg_verwertbares_vermoegen=1600.00
bg_zahlbetrag=692.68
budget_rest_erwartet=190.00

### verifikation

V1: OK | alle Personen als eigene SozialFall-Zeilen
    sql    = SELECT COUNT(*) FROM SOZIAL_FALL WHERE BG_ID = ?
    params = [17788]
    erwartet = 3 | ist = 3
V2: OK | finale Zahlung wirksam (Vier-Augen-Warteschlange je nach Kassenlauf)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [16543]
    erwartet = ANGELEGT|BEREIT|UEBERGEBEN | ist = ANGELEGT
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [16543]
    erwartet = 692.68 | ist = 692.68
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17788]
    erwartet = 190.00 | ist = 190
V5: OK | genau eine Zahlung trotz Wiederholung
    sql    = SELECT COUNT(*) FROM ZAHLUNG WHERE BG_ID = ?
    params = [17788]
    erwartet = 1 | ist = 1
V6: OK | Vermögen nach Verbrauchsfortschreibung (§ 90)
    sql    = SELECT VERMOEGEN FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17788]
    erwartet = 11000.00 | ist = 11000
V7: OK | dokumentierter Vermögenseinsatz an der Zahlung
    sql    = SELECT VERMOEGENSEINSATZ FROM ZAHLUNG WHERE ID = ?
    params = [16543]
    erwartet = 1600.00 | ist = 1600
V8: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:20:40.020 sb-sim-16 | Vorgang anlegen (Alleinerziehend mit 2 Kind(ern), Budget 882.68 EUR, Vermögen 12600.00 EUR) -> OK — Vorgang 17788
2026-10-13 11:20:40.064 sb-sim-16 | 3 SozialFall/Fälle erfassen -> OK — Alleinerziehend mit 2 Kind(ern), KdU 705.00 EUR
2026-10-13 11:20:40.084 sb-sim-16 | Auszahlung anordnen (2026-02) -> OK — 692.68 EUR (ANGELEGT)
2026-10-13 11:20:40.095 sb-sim-16 | Doppelklick: gleiche Vorgangsnummer erneut -> OK — korrekt als bereits gebucht erkannt

## TF 66 KOMBINATION SWEET-0066
status = VERIFIZIERT
hauptperson = Neumann, Olga
bearbeiter = sb-sim-04
bg_id = 17789
dauer_ms = 39
ids = zahlungId=16532, vorgangsNr=200b80e1-4cad-49a6-b213-4f0516371473, mitarbeiter=sb-sim-04, bgId=17789, letzteZahlungId=16532, kollege=sb-sim-06

### rechenwege

block: Kombinationsfall 66: Single
zahlungsmonat = 2026-06
vermoegen = 10000.00
schonvermoegen = 10000.00
budget_start = 767.45
p1_eingaben: rbs=1, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=true, kdu_miete=220.00, kdu_betrieb=110.00, kdu_heizung=110.00, rente=350.00, erwerb=1080.00, abz_steuern=200.00, abz_sv=160.00, abz_vers=70.00, abz_erwerb=90.00
p1_regelbedarf=563.00
p1_mehrbedarf_g=0.00
p1_mehrbedarf_schwanger=0.00
p1_mehrbedarf_alleinerziehend=0.00
p1_mehrbedarf_warmwasser=12.95
p1_kdu=440.00
p1_gesamtbedarf=1015.95
p1_freibetrag_erwerb=281.50
p1_absetzbetaetrag=520.00
p1_anrechenbar=628.50
p1_zahlbetrag=387.45
bg_gesamtbedarf=1015.95
bg_anrechenbar=628.50
bg_verwertbares_vermoegen=0.00
bg_zahlbetrag=387.45
budget_rest_erwartet=380.00

### verifikation

V1: OK | alle Personen als eigene SozialFall-Zeilen
    sql    = SELECT COUNT(*) FROM SOZIAL_FALL WHERE BG_ID = ?
    params = [17789]
    erwartet = 1 | ist = 1
V2: OK | finale Zahlung wirksam (Vier-Augen-Warteschlange je nach Kassenlauf)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [16532]
    erwartet = ANGELEGT|BEREIT|UEBERGEBEN | ist = ANGELEGT
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [16532]
    erwartet = 387.45 | ist = 387.45
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17789]
    erwartet = 380.00 | ist = 380
V5: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:20:40.022 sb-sim-04 | Vorgang anlegen (Single, Budget 767.45 EUR, Vermögen 10000.00 EUR) -> OK — Vorgang 17789
2026-10-13 11:20:40.033 sb-sim-04 | 1 SozialFall/Fälle erfassen -> OK — Single, KdU 440.00 EUR
2026-10-13 11:20:40.050 sb-sim-04 | Auszahlung anordnen (2026-06) -> OK — 387.45 EUR (ANGELEGT)

## TF 67 KOMBINATION SWEET-0067
status = VERIFIZIERT
hauptperson = Thiel, Gisela
bearbeiter = sb-sim-15
bg_id = 17790
dauer_ms = 75
ids = zahlungId=16545, vorgangsNr=bb4d752b-5095-49b9-93b4-8ac53b60547b, mitarbeiter=sb-sim-15, bgId=17790, letzteZahlungId=16545, kollege=sb-sim-09

### rechenwege

block: Kombinationsfall 67: Alleinerziehend mit 3 Kind(ern)
zahlungsmonat = 2026-06
vermoegen = 0.00
schonvermoegen = 11500.00
budget_start = 801.63
p1_eingaben: rbs=1, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=2, kinder_u16=3, kinder_minderjaehrig=3, warmwasser=true, kdu_miete=480.00, kdu_betrieb=115.00, kdu_heizung=170.00, rente=0.00, erwerb=710.00, abz_steuern=160.00, abz_sv=290.00, abz_vers=80.00, abz_erwerb=70.00
p1_regelbedarf=563.00
p1_mehrbedarf_g=0.00
p1_mehrbedarf_schwanger=0.00
p1_mehrbedarf_alleinerziehend=202.68
p1_mehrbedarf_warmwasser=12.95
p1_kdu=765.00
p1_gesamtbedarf=1543.63
p1_freibetrag_erwerb=213.00
p1_absetzbetaetrag=600.00
p1_anrechenbar=0.00
p1_zahlbetrag=1543.63
p2_eingaben: rbs=5, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=0.00, kdu_betrieb=0.00, kdu_heizung=0.00, rente=430.00, erwerb=1280.00, abz_steuern=110.00, abz_sv=100.00, abz_vers=5.00, abz_erwerb=60.00
p2_regelbedarf=390.00
p2_mehrbedarf_g=0.00
p2_mehrbedarf_schwanger=0.00
p2_mehrbedarf_alleinerziehend=0.00
p2_mehrbedarf_warmwasser=0.00
p2_kdu=0.00
p2_gesamtbedarf=390.00
p2_freibetrag_erwerb=281.50
p2_absetzbetaetrag=275.00
p2_anrechenbar=1153.50
p2_zahlbetrag=-763.50
p3_eingaben: rbs=5, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=0.00, kdu_betrieb=0.00, kdu_heizung=0.00, rente=0.00, erwerb=680.00, abz_steuern=180.00, abz_sv=260.00, abz_vers=5.00, abz_erwerb=40.00
p3_regelbedarf=390.00
p3_mehrbedarf_g=0.00
p3_mehrbedarf_schwanger=0.00
p3_mehrbedarf_alleinerziehend=0.00
p3_mehrbedarf_warmwasser=0.00
p3_kdu=0.00
p3_gesamtbedarf=390.00
p3_freibetrag_erwerb=204.00
p3_absetzbetaetrag=485.00
p3_anrechenbar=0.00
p3_zahlbetrag=390.00
p4_eingaben: rbs=5, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=0.00, kdu_betrieb=0.00, kdu_heizung=0.00, rente=530.00, erwerb=960.00, abz_steuern=20.00, abz_sv=230.00, abz_vers=80.00, abz_erwerb=60.00
p4_regelbedarf=390.00
p4_mehrbedarf_g=0.00
p4_mehrbedarf_schwanger=0.00
p4_mehrbedarf_alleinerziehend=0.00
p4_mehrbedarf_warmwasser=0.00
p4_kdu=0.00
p4_gesamtbedarf=390.00
p4_freibetrag_erwerb=281.50
p4_absetzbetaetrag=390.00
p4_anrechenbar=818.50
p4_zahlbetrag=-428.50
bg_gesamtbedarf=2713.63
bg_anrechenbar=1972.00
bg_verwertbares_vermoegen=0.00
bg_zahlbetrag=741.63
budget_rest_erwartet=60.00

### verifikation

V1: OK | alle Personen als eigene SozialFall-Zeilen
    sql    = SELECT COUNT(*) FROM SOZIAL_FALL WHERE BG_ID = ?
    params = [17790]
    erwartet = 4 | ist = 4
V2: OK | finale Zahlung wirksam (Vier-Augen-Warteschlange je nach Kassenlauf)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [16545]
    erwartet = ANGELEGT|BEREIT|UEBERGEBEN | ist = ANGELEGT
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [16545]
    erwartet = 741.63 | ist = 741.63
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17790]
    erwartet = 60.00 | ist = 60
V5: OK | Wiedervorlagedatum exakt wie gesetzt
    sql    = SELECT COUNT(*) FROM BEDARFSGEMEINSCHAFT WHERE ID = ? AND WIEDERVORLAGE_AM = ?
    params = [17790, 2026-12-16]
    erwartet = 1 | ist = 1
V6: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:20:40.022 sb-sim-15 | Vorgang anlegen (Alleinerziehend mit 3 Kind(ern), Budget 801.63 EUR) -> OK — Vorgang 17790
2026-10-13 11:20:40.071 sb-sim-15 | 4 SozialFall/Fälle erfassen -> OK — Alleinerziehend mit 3 Kind(ern), KdU 765.00 EUR
2026-10-13 11:20:40.088 sb-sim-15 | Auszahlung anordnen (2026-06) -> OK — 741.63 EUR (ANGELEGT)

## TF 68 KOMBINATION SWEET-0068
status = VERIFIZIERT
hauptperson = Egger, Norbert
bearbeiter = sb-sim-14
bg_id = 17791
dauer_ms = 53
ids = zahlungId=16534, vorgangsNr=41f2e8f5-aa61-4e1e-81f2-ebaa66b84957, mitarbeiter=sb-sim-14, bgId=17791, letzteZahlungId=16534, kollege=sb-sim-11

### rechenwege

block: Kombinationsfall 68: Single
zahlungsmonat = 2026-05
vermoegen = 10150.00
schonvermoegen = 10000.00
budget_start = 498.00
p1_eingaben: rbs=1, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=330.00, kdu_betrieb=75.00, kdu_heizung=40.00, rente=750.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p1_regelbedarf=563.00
p1_mehrbedarf_g=0.00
p1_mehrbedarf_schwanger=0.00
p1_mehrbedarf_alleinerziehend=0.00
p1_mehrbedarf_warmwasser=0.00
p1_kdu=445.00
p1_gesamtbedarf=1008.00
p1_freibetrag_erwerb=0.00
p1_absetzbetaetrag=0.00
p1_anrechenbar=750.00
p1_zahlbetrag=258.00
bg_gesamtbedarf=1008.00
bg_anrechenbar=750.00
bg_verwertbares_vermoegen=150.00
bg_zahlbetrag=108.00
budget_rest_erwartet=390.00

### verifikation

V1: OK | alle Personen als eigene SozialFall-Zeilen
    sql    = SELECT COUNT(*) FROM SOZIAL_FALL WHERE BG_ID = ?
    params = [17791]
    erwartet = 1 | ist = 1
V2: OK | finale Zahlung freigegeben (oder bereits übergeben)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [16534]
    erwartet = BEREIT|UEBERGEBEN | ist = UEBERGEBEN
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [16534]
    erwartet = 108.00 | ist = 108
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17791]
    erwartet = 390.00 | ist = 390
V5: OK | Freigabevermerk nennt den Kollegen (Vier-Augen)
    sql    = SELECT CASE WHEN FREIGEGEBEN_VON = ? THEN 'ok' ELSE 'abweichend' END FROM ZAHLUNG WHERE ID = ?
    params = [sb-sim-11, 16534]
    erwartet = ok | ist = ok
V6: OK | Vermögen nach Verbrauchsfortschreibung (§ 90)
    sql    = SELECT VERMOEGEN FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17791]
    erwartet = 10000.00 | ist = 10000
V7: OK | dokumentierter Vermögenseinsatz an der Zahlung
    sql    = SELECT VERMOEGENSEINSATZ FROM ZAHLUNG WHERE ID = ?
    params = [16534]
    erwartet = 150.00 | ist = 150
V8: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:20:40.026 sb-sim-14 | Vorgang anlegen (Single, Budget 498.00 EUR, Vermögen 10150.00 EUR) -> OK — Vorgang 17791
2026-10-13 11:20:40.038 sb-sim-14 | 1 SozialFall/Fälle erfassen -> OK — Single, KdU 445.00 EUR
2026-10-13 11:20:40.056 sb-sim-14 | Auszahlung anordnen (2026-05) -> OK — 108.00 EUR (ANGELEGT)
2026-10-13 11:20:40.068 sb-sim-14 | Freigabe durch Kollegen (Vier-Augen) -> OK — freigegeben durch sb-sim-11

## TF 69 KOMBINATION SWEET-0069
status = VERIFIZIERT
hauptperson = Gross, Marlene
bearbeiter = sb-sim-05
bg_id = 17792
dauer_ms = 41
ids = zahlungId=16535, vorgangsNr=658ce595-60c4-4001-949c-774aee7de54d, mitarbeiter=sb-sim-05, bgId=17792, letzteZahlungId=16535, kollege=sb-sim-08

### rechenwege

block: Kombinationsfall 69: Single (Schwangerschafts-Mehrbedarf entfällt im Zahlungsmonat)
zahlungsmonat = 2026-11
vermoegen = 10050.00
schonvermoegen = 10000.00
budget_start = 1102.45
p1_eingaben: rbs=1, merkzeichen_g=false, schwanger=false, entbindungsmonat=2026-08, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=true, kdu_miete=500.00, kdu_betrieb=95.00, kdu_heizung=135.00, rente=0.00, erwerb=1190.00, abz_steuern=80.00, abz_sv=260.00, abz_vers=15.00, abz_erwerb=100.00
p1_regelbedarf=563.00
p1_mehrbedarf_g=0.00
p1_mehrbedarf_schwanger=0.00
p1_mehrbedarf_alleinerziehend=0.00
p1_mehrbedarf_warmwasser=12.95
p1_kdu=730.00
p1_gesamtbedarf=1305.95
p1_freibetrag_erwerb=281.50
p1_absetzbetaetrag=455.00
p1_anrechenbar=453.50
p1_zahlbetrag=852.45
bg_gesamtbedarf=1305.95
bg_anrechenbar=453.50
bg_verwertbares_vermoegen=50.00
bg_zahlbetrag=802.45
budget_rest_erwartet=300.00

### verifikation

V1: OK | alle Personen als eigene SozialFall-Zeilen
    sql    = SELECT COUNT(*) FROM SOZIAL_FALL WHERE BG_ID = ?
    params = [17792]
    erwartet = 1 | ist = 1
V2: OK | finale Zahlung wirksam (Vier-Augen-Warteschlange je nach Kassenlauf)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [16535]
    erwartet = ANGELEGT|BEREIT|UEBERGEBEN | ist = ANGELEGT
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [16535]
    erwartet = 802.45 | ist = 802.45
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17792]
    erwartet = 300.00 | ist = 300
V5: OK | Vermögen nach Verbrauchsfortschreibung (§ 90)
    sql    = SELECT VERMOEGEN FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17792]
    erwartet = 10000.00 | ist = 10000
V6: OK | dokumentierter Vermögenseinsatz an der Zahlung
    sql    = SELECT VERMOEGENSEINSATZ FROM ZAHLUNG WHERE ID = ?
    params = [16535]
    erwartet = 50.00 | ist = 50
V7: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:20:40.028 sb-sim-05 | Vorgang anlegen (Single, Budget 1102.45 EUR, Vermögen 10050.00 EUR) -> OK — Vorgang 17792
2026-10-13 11:20:40.040 sb-sim-05 | 1 SozialFall/Fälle erfassen -> OK — Single, KdU 730.00 EUR
2026-10-13 11:20:40.058 sb-sim-05 | Auszahlung anordnen (2026-11) -> OK — 802.45 EUR (ANGELEGT)

## TF 70 KOMBINATION SWEET-0070
status = VERIFIZIERT
hauptperson = Jung, Frank
bearbeiter = sb-sim-10
bg_id = 17794
dauer_ms = 39
ids = zahlungId=16536, vorgangsNr=5bfca17f-3308-4324-94f9-b19ce5b57e78, mitarbeiter=sb-sim-10, bgId=17794, letzteZahlungId=16536, kollege=sb-sim-07

### rechenwege

block: Kombinationsfall 70: Single
zahlungsmonat = 2026-09
vermoegen = 0.00
schonvermoegen = 10000.00
budget_start = 1353.00
p1_eingaben: rbs=1, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=200.00, kdu_betrieb=120.00, kdu_heizung=90.00, rente=0.00, erwerb=120.00, abz_steuern=160.00, abz_sv=150.00, abz_vers=5.00, abz_erwerb=80.00
p1_regelbedarf=563.00
p1_mehrbedarf_g=0.00
p1_mehrbedarf_schwanger=0.00
p1_mehrbedarf_alleinerziehend=0.00
p1_mehrbedarf_warmwasser=0.00
p1_kdu=410.00
p1_gesamtbedarf=973.00
p1_freibetrag_erwerb=36.00
p1_absetzbetaetrag=395.00
p1_anrechenbar=0.00
p1_zahlbetrag=973.00
bg_gesamtbedarf=973.00
bg_anrechenbar=0.00
bg_verwertbares_vermoegen=0.00
bg_zahlbetrag=973.00
budget_rest_erwartet=380.00

### verifikation

V1: OK | alle Personen als eigene SozialFall-Zeilen
    sql    = SELECT COUNT(*) FROM SOZIAL_FALL WHERE BG_ID = ?
    params = [17794]
    erwartet = 1 | ist = 1
V2: OK | finale Zahlung wirksam (Vier-Augen-Warteschlange je nach Kassenlauf)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [16536]
    erwartet = ANGELEGT|BEREIT|UEBERGEBEN | ist = ANGELEGT
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [16536]
    erwartet = 973.00 | ist = 973
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17794]
    erwartet = 380.00 | ist = 380
V5: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:20:40.032 sb-sim-10 | Vorgang anlegen (Single, Budget 1353.00 EUR) -> OK — Vorgang 17794
2026-10-13 11:20:40.046 sb-sim-10 | 1 SozialFall/Fälle erfassen -> OK — Single, KdU 410.00 EUR
2026-10-13 11:20:40.062 sb-sim-10 | Auszahlung anordnen (2026-09) -> OK — 973.00 EUR (ANGELEGT)

## TF 71 KOMBINATION SWEET-0071
status = VERIFIZIERT
hauptperson = Wagner, Gisela
bearbeiter = sb-sim-08
bg_id = 17793
dauer_ms = 58
ids = zahlungId=16542, vorgangsNr=b5ef2f02-7f41-4bf5-88f1-a27b6e1db1cc, mitarbeiter=sb-sim-08, bgId=17793, letzteZahlungId=16542, kollege=sb-sim-05

### rechenwege

block: Kombinationsfall 71: Paar mit 1 Kind(ern)
zahlungsmonat = 2026-02
vermoegen = 20900.00
schonvermoegen = 20500.00
budget_start = 224.16
p1_eingaben: rbs=2, merkzeichen_g=true, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=true, kdu_miete=360.00, kdu_betrieb=0.00, kdu_heizung=0.00, rente=750.00, erwerb=1400.00, abz_steuern=100.00, abz_sv=220.00, abz_vers=10.00, abz_erwerb=80.00
p1_regelbedarf=506.00
p1_mehrbedarf_g=86.02
p1_mehrbedarf_schwanger=0.00
p1_mehrbedarf_alleinerziehend=0.00
p1_mehrbedarf_warmwasser=11.64
p1_kdu=360.00
p1_gesamtbedarf=963.66
p1_freibetrag_erwerb=281.50
p1_absetzbetaetrag=410.00
p1_anrechenbar=1458.50
p1_zahlbetrag=-494.84
p2_eingaben: rbs=2, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=0.00, kdu_betrieb=140.00, kdu_heizung=120.00, rente=290.00, erwerb=660.00, abz_steuern=140.00, abz_sv=310.00, abz_vers=65.00, abz_erwerb=30.00
p2_regelbedarf=506.00
p2_mehrbedarf_g=0.00
p2_mehrbedarf_schwanger=0.00
p2_mehrbedarf_alleinerziehend=0.00
p2_mehrbedarf_warmwasser=0.00
p2_kdu=260.00
p2_gesamtbedarf=766.00
p2_freibetrag_erwerb=198.00
p2_absetzbetaetrag=545.00
p2_anrechenbar=207.00
p2_zahlbetrag=559.00
p3_eingaben: rbs=5, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=0.00, kdu_betrieb=0.00, kdu_heizung=0.00, rente=0.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p3_regelbedarf=390.00
p3_mehrbedarf_g=0.00
p3_mehrbedarf_schwanger=0.00
p3_mehrbedarf_alleinerziehend=0.00
p3_mehrbedarf_warmwasser=0.00
p3_kdu=0.00
p3_gesamtbedarf=390.00
p3_freibetrag_erwerb=0.00
p3_absetzbetaetrag=0.00
p3_anrechenbar=0.00
p3_zahlbetrag=390.00
bg_gesamtbedarf=2119.66
bg_anrechenbar=1665.50
bg_verwertbares_vermoegen=400.00
bg_zahlbetrag=54.16
budget_rest_erwartet=170.00

### verifikation

V1: OK | alle Personen als eigene SozialFall-Zeilen
    sql    = SELECT COUNT(*) FROM SOZIAL_FALL WHERE BG_ID = ?
    params = [17793]
    erwartet = 3 | ist = 3
V2: OK | finale Zahlung wirksam (Vier-Augen-Warteschlange je nach Kassenlauf)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [16542]
    erwartet = ANGELEGT|BEREIT|UEBERGEBEN | ist = ANGELEGT
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [16542]
    erwartet = 54.16 | ist = 54.16
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17793]
    erwartet = 170.00 | ist = 170
V5: OK | Vermögen nach Verbrauchsfortschreibung (§ 90)
    sql    = SELECT VERMOEGEN FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17793]
    erwartet = 20500.00 | ist = 20500
V6: OK | dokumentierter Vermögenseinsatz an der Zahlung
    sql    = SELECT VERMOEGENSEINSATZ FROM ZAHLUNG WHERE ID = ?
    params = [16542]
    erwartet = 400.00 | ist = 400
V7: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:20:40.030 sb-sim-08 | Vorgang anlegen (Paar mit 1 Kind(ern), Budget 224.16 EUR, Vermögen 20900.00 EUR) -> OK — Vorgang 17793
2026-10-13 11:20:40.063 sb-sim-08 | 3 SozialFall/Fälle erfassen -> OK — Paar mit 1 Kind(ern), KdU 620.00 EUR
2026-10-13 11:20:40.081 sb-sim-08 | Auszahlung anordnen (2026-02) -> OK — 54.16 EUR (ANGELEGT)

## TF 72 KOMBINATION SWEET-0072
status = VERIFIZIERT
hauptperson = Lindner, Karin
bearbeiter = sb-sim-07
bg_id = 17795
dauer_ms = 61
ids = zahlungId=16540, vorgangsNr=b6ef5fdd-66b7-4484-af37-c58f29205497, mitarbeiter=sb-sim-07, bgId=17795, letzteZahlungId=16540, kollege=sb-sim-10

### rechenwege

block: Kombinationsfall 72: Single
zahlungsmonat = 2026-11
vermoegen = 0.00
schonvermoegen = 10000.00
budget_start = 514.50
p1_eingaben: rbs=1, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=440.00, kdu_betrieb=95.00, kdu_heizung=65.00, rente=460.00, erwerb=1000.00, abz_steuern=110.00, abz_sv=50.00, abz_vers=40.00, abz_erwerb=60.00
p1_regelbedarf=563.00
p1_mehrbedarf_g=0.00
p1_mehrbedarf_schwanger=0.00
p1_mehrbedarf_alleinerziehend=0.00
p1_mehrbedarf_warmwasser=0.00
p1_kdu=600.00
p1_gesamtbedarf=1163.00
p1_freibetrag_erwerb=281.50
p1_absetzbetaetrag=260.00
p1_anrechenbar=918.50
p1_zahlbetrag=244.50
bg_gesamtbedarf=1163.00
bg_anrechenbar=918.50
bg_verwertbares_vermoegen=0.00
bg_zahlbetrag=244.50
budget_rest_erwartet=270.00

### verifikation

V1: OK | alle Personen als eigene SozialFall-Zeilen
    sql    = SELECT COUNT(*) FROM SOZIAL_FALL WHERE BG_ID = ?
    params = [17795]
    erwartet = 1 | ist = 1
V2: OK | finale Zahlung freigegeben (oder bereits übergeben)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [16540]
    erwartet = BEREIT|UEBERGEBEN | ist = UEBERGEBEN
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [16540]
    erwartet = 244.50 | ist = 244.5
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17795]
    erwartet = 270.00 | ist = 270
V5: OK | Freigabevermerk nennt den Kollegen (Vier-Augen)
    sql    = SELECT CASE WHEN FREIGEGEBEN_VON = ? THEN 'ok' ELSE 'abweichend' END FROM ZAHLUNG WHERE ID = ?
    params = [sb-sim-10, 16540]
    erwartet = ok | ist = ok
V6: OK | genau ein erteilter Bewilligungsbescheid
    sql    = SELECT COUNT(*) FROM BESCHEID WHERE BG_ID = ?
    params = [17795]
    erwartet = 1 | ist = 1
V7: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:20:40.036 sb-sim-07 | Vorgang anlegen (Single, Budget 514.50 EUR) -> OK — Vorgang 17795
2026-10-13 11:20:40.048 sb-sim-07 | 1 SozialFall/Fälle erfassen -> OK — Single, KdU 600.00 EUR
2026-10-13 11:20:40.060 sb-sim-07 | Bewilligungsbescheid erteilen (2026-11 .. 2026-11) -> OK — Bescheid BESCH-SWEET-0072-2026-1744 mit Monatsplan gespeichert
2026-10-13 11:20:40.078 sb-sim-07 | Auszahlung anordnen (2026-11) -> OK — 244.50 EUR (ANGELEGT)
2026-10-13 11:20:40.090 sb-sim-07 | Freigabe durch Kollegen (Vier-Augen) -> OK — freigegeben durch sb-sim-10

## TF 73 KOMBINATION SWEET-0073
status = VERIFIZIERT
hauptperson = Sauber, Jens
bearbeiter = sb-sim-12
bg_id = 17796
dauer_ms = 143
ids = letzteZahlungId=16571, kollege=sb-sim-20, bgId=17796, mitarbeiter=sb-sim-12, zahlungId=16557, fallId=26896, vorgangsNr=dc3cc86e-e9f9-4330-86cd-43e494390ff2

### rechenwege

block: Kombinationsfall 73: Alleinerziehend mit 3 Kind(ern)
zahlungsmonat = 2026-09
vermoegen = 0.00
schonvermoegen = 11500.00
budget_start = 1743.62
p1_eingaben: rbs=1, merkzeichen_g=true, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=3, kinder_minderjaehrig=3, warmwasser=false, kdu_miete=630.00, kdu_betrieb=80.00, kdu_heizung=130.00, rente=0.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p1_regelbedarf=563.00
p1_mehrbedarf_g=95.71
p1_mehrbedarf_schwanger=0.00
p1_mehrbedarf_alleinerziehend=202.68
p1_mehrbedarf_warmwasser=0.00
p1_kdu=840.00
p1_gesamtbedarf=1701.39
p1_freibetrag_erwerb=0.00
p1_absetzbetaetrag=0.00
p1_anrechenbar=0.00
p1_zahlbetrag=1701.39
p2_eingaben: rbs=6, merkzeichen_g=true, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=0.00, kdu_betrieb=0.00, kdu_heizung=0.00, rente=600.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p2_regelbedarf=357.00
p2_mehrbedarf_g=60.69
p2_mehrbedarf_schwanger=0.00
p2_mehrbedarf_alleinerziehend=0.00
p2_mehrbedarf_warmwasser=0.00
p2_kdu=0.00
p2_gesamtbedarf=417.69
p2_freibetrag_erwerb=0.00
p2_absetzbetaetrag=0.00
p2_anrechenbar=600.00
p2_zahlbetrag=-182.31
p3_eingaben: rbs=6, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=true, kdu_miete=0.00, kdu_betrieb=0.00, kdu_heizung=0.00, rente=950.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p3_regelbedarf=357.00
p3_mehrbedarf_g=0.00
p3_mehrbedarf_schwanger=0.00
p3_mehrbedarf_alleinerziehend=0.00
p3_mehrbedarf_warmwasser=2.86
p3_kdu=0.00
p3_gesamtbedarf=359.86
p3_freibetrag_erwerb=0.00
p3_absetzbetaetrag=0.00
p3_anrechenbar=950.00
p3_zahlbetrag=-590.14
p4_eingaben: rbs=5, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=true, kdu_miete=0.00, kdu_betrieb=0.00, kdu_heizung=0.00, rente=0.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p4_regelbedarf=390.00
p4_mehrbedarf_g=0.00
p4_mehrbedarf_schwanger=0.00
p4_mehrbedarf_alleinerziehend=0.00
p4_mehrbedarf_warmwasser=4.68
p4_kdu=0.00
p4_gesamtbedarf=394.68
p4_freibetrag_erwerb=0.00
p4_absetzbetaetrag=0.00
p4_anrechenbar=0.00
p4_zahlbetrag=394.68
bg_gesamtbedarf=2873.62
bg_anrechenbar=1550.00
bg_verwertbares_vermoegen=0.00
bg_zahlbetrag=1323.62
budget_rest_erwartet=420.00

block: Neuanordnung nach Mietekorrektur (Miete 630.00 → 690.00)
zahlungsmonat = 2026-09
vermoegen = 0.00
schonvermoegen = 11500.00
budget_start = 1743.62
p1_eingaben: rbs=1, merkzeichen_g=true, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=3, kinder_minderjaehrig=3, warmwasser=false, kdu_miete=690.00, kdu_betrieb=80.00, kdu_heizung=130.00, rente=0.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p1_regelbedarf=563.00
p1_mehrbedarf_g=95.71
p1_mehrbedarf_schwanger=0.00
p1_mehrbedarf_alleinerziehend=202.68
p1_mehrbedarf_warmwasser=0.00
p1_kdu=900.00
p1_gesamtbedarf=1761.39
p1_freibetrag_erwerb=0.00
p1_absetzbetaetrag=0.00
p1_anrechenbar=0.00
p1_zahlbetrag=1761.39
p2_eingaben: rbs=6, merkzeichen_g=true, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=0.00, kdu_betrieb=0.00, kdu_heizung=0.00, rente=600.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p2_regelbedarf=357.00
p2_mehrbedarf_g=60.69
p2_mehrbedarf_schwanger=0.00
p2_mehrbedarf_alleinerziehend=0.00
p2_mehrbedarf_warmwasser=0.00
p2_kdu=0.00
p2_gesamtbedarf=417.69
p2_freibetrag_erwerb=0.00
p2_absetzbetaetrag=0.00
p2_anrechenbar=600.00
p2_zahlbetrag=-182.31
p3_eingaben: rbs=6, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=true, kdu_miete=0.00, kdu_betrieb=0.00, kdu_heizung=0.00, rente=950.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p3_regelbedarf=357.00
p3_mehrbedarf_g=0.00
p3_mehrbedarf_schwanger=0.00
p3_mehrbedarf_alleinerziehend=0.00
p3_mehrbedarf_warmwasser=2.86
p3_kdu=0.00
p3_gesamtbedarf=359.86
p3_freibetrag_erwerb=0.00
p3_absetzbetaetrag=0.00
p3_anrechenbar=950.00
p3_zahlbetrag=-590.14
p4_eingaben: rbs=5, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=true, kdu_miete=0.00, kdu_betrieb=0.00, kdu_heizung=0.00, rente=0.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p4_regelbedarf=390.00
p4_mehrbedarf_g=0.00
p4_mehrbedarf_schwanger=0.00
p4_mehrbedarf_alleinerziehend=0.00
p4_mehrbedarf_warmwasser=4.68
p4_kdu=0.00
p4_gesamtbedarf=394.68
p4_freibetrag_erwerb=0.00
p4_absetzbetaetrag=0.00
p4_anrechenbar=0.00
p4_zahlbetrag=394.68
bg_gesamtbedarf=2933.62
bg_anrechenbar=1550.00
bg_verwertbares_vermoegen=0.00
bg_zahlbetrag=1383.62
budget_rest_erwartet=360.00

### verifikation

V1: OK | alle Personen als eigene SozialFall-Zeilen
    sql    = SELECT COUNT(*) FROM SOZIAL_FALL WHERE BG_ID = ?
    params = [17796]
    erwartet = 4 | ist = 4
V2: OK | finale Zahlung wirksam (Vier-Augen-Warteschlange je nach Kassenlauf)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [16571]
    erwartet = ANGELEGT|BEREIT|UEBERGEBEN | ist = ANGELEGT
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [16571]
    erwartet = 1383.62 | ist = 1383.62
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17796]
    erwartet = 360.00 | ist = 360
V5: OK | zwei Zahlungen (stornierte + korrigierte)
    sql    = SELECT COUNT(*) FROM ZAHLUNG WHERE BG_ID = ?
    params = [17796]
    erwartet = 2 | ist = 2
V6: OK | erste Zahlung STORNIERT
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [16557]
    erwartet = STORNIERT | ist = STORNIERT
V7: OK | Fall in Version 2 (optimistisches Locking)
    sql    = SELECT VERSION FROM SOZIAL_FALL WHERE ID = ?
    params = [26896]
    erwartet = 2 | ist = 2
V8: OK | genau ein erteilter Bewilligungsbescheid
    sql    = SELECT COUNT(*) FROM BESCHEID WHERE BG_ID = ?
    params = [17796]
    erwartet = 1 | ist = 1
V9: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:20:40.042 sb-sim-12 | Vorgang anlegen (Alleinerziehend mit 3 Kind(ern), Budget 1743.62 EUR) -> OK — Vorgang 17796
2026-10-13 11:20:40.088 sb-sim-12 | 4 SozialFall/Fälle erfassen -> OK — Alleinerziehend mit 3 Kind(ern), KdU 840.00 EUR
2026-10-13 11:20:40.104 sb-sim-12 | Bewilligungsbescheid erteilen (2026-08 .. 2026-10) -> OK — Bescheid BESCH-SWEET-0073-2026-1746 mit Monatsplan gespeichert
2026-10-13 11:20:40.125 sb-sim-12 | Auszahlung anordnen (2026-09) -> OK — 1323.62 EUR (ANGELEGT)
2026-10-13 11:20:40.138 sb-sim-12 | Anordnung sofort stornieren (Status ANGELEGT) -> OK — storniert — Monat wieder frei
2026-10-13 11:20:40.158 sb-sim-12 | Miete auf 690.00 korrigieren -> OK — Fall 26896 in Version 2
2026-10-13 11:20:40.176 sb-sim-12 | Korrigierte Auszahlung für denselben Monat -> OK — 1383.62 EUR

## TF 74 KOMBINATION SWEET-0074
status = VERIFIZIERT
hauptperson = Thiel, Ludwig
bearbeiter = sb-sim-11
bg_id = 17797
dauer_ms = 71
ids = zahlungId=16546, vorgangsNr=827859bc-0e91-4d7d-9198-26635963869e, mitarbeiter=sb-sim-11, bgId=17797, letzteZahlungId=16546, kollege=sb-sim-10

### rechenwege

block: Kombinationsfall 74: Paar mit 1 Kind(ern)
zahlungsmonat = 2026-03
vermoegen = 20600.00
schonvermoegen = 20500.00
budget_start = 721.28
p1_eingaben: rbs=2, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=true, kdu_miete=380.00, kdu_betrieb=0.00, kdu_heizung=0.00, rente=690.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p1_regelbedarf=506.00
p1_mehrbedarf_g=0.00
p1_mehrbedarf_schwanger=0.00
p1_mehrbedarf_alleinerziehend=0.00
p1_mehrbedarf_warmwasser=11.64
p1_kdu=380.00
p1_gesamtbedarf=897.64
p1_freibetrag_erwerb=0.00
p1_absetzbetaetrag=0.00
p1_anrechenbar=690.00
p1_zahlbetrag=207.64
p2_eingaben: rbs=2, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=true, kdu_miete=0.00, kdu_betrieb=130.00, kdu_heizung=120.00, rente=0.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p2_regelbedarf=506.00
p2_mehrbedarf_g=0.00
p2_mehrbedarf_schwanger=0.00
p2_mehrbedarf_alleinerziehend=0.00
p2_mehrbedarf_warmwasser=11.64
p2_kdu=250.00
p2_gesamtbedarf=767.64
p2_freibetrag_erwerb=0.00
p2_absetzbetaetrag=0.00
p2_anrechenbar=0.00
p2_zahlbetrag=767.64
p3_eingaben: rbs=6, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=0.00, kdu_betrieb=0.00, kdu_heizung=0.00, rente=570.00, erwerb=530.00, abz_steuern=30.00, abz_sv=240.00, abz_vers=40.00, abz_erwerb=50.00
p3_regelbedarf=357.00
p3_mehrbedarf_g=0.00
p3_mehrbedarf_schwanger=0.00
p3_mehrbedarf_alleinerziehend=0.00
p3_mehrbedarf_warmwasser=0.00
p3_kdu=0.00
p3_gesamtbedarf=357.00
p3_freibetrag_erwerb=159.00
p3_absetzbetaetrag=360.00
p3_anrechenbar=581.00
p3_zahlbetrag=-224.00
bg_gesamtbedarf=2022.28
bg_anrechenbar=1271.00
bg_verwertbares_vermoegen=100.00
bg_zahlbetrag=651.28
budget_rest_erwartet=70.00

### verifikation

V1: OK | alle Personen als eigene SozialFall-Zeilen
    sql    = SELECT COUNT(*) FROM SOZIAL_FALL WHERE BG_ID = ?
    params = [17797]
    erwartet = 3 | ist = 3
V2: OK | finale Zahlung wirksam (Vier-Augen-Warteschlange je nach Kassenlauf)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [16546]
    erwartet = ANGELEGT|BEREIT|UEBERGEBEN | ist = ANGELEGT
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [16546]
    erwartet = 651.28 | ist = 651.28
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17797]
    erwartet = 70.00 | ist = 70
V5: OK | genau eine Zahlung trotz Wiederholung
    sql    = SELECT COUNT(*) FROM ZAHLUNG WHERE BG_ID = ?
    params = [17797]
    erwartet = 1 | ist = 1
V6: OK | Vermögen nach Verbrauchsfortschreibung (§ 90)
    sql    = SELECT VERMOEGEN FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17797]
    erwartet = 20500.00 | ist = 20500
V7: OK | dokumentierter Vermögenseinsatz an der Zahlung
    sql    = SELECT VERMOEGENSEINSATZ FROM ZAHLUNG WHERE ID = ?
    params = [16546]
    erwartet = 100.00 | ist = 100
V8: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:20:40.042 sb-sim-11 | Vorgang anlegen (Paar mit 1 Kind(ern), Budget 721.28 EUR, Vermögen 20600.00 EUR) -> OK — Vorgang 17797
2026-10-13 11:20:40.074 sb-sim-11 | 3 SozialFall/Fälle erfassen -> OK — Paar mit 1 Kind(ern), KdU 630.00 EUR
2026-10-13 11:20:40.092 sb-sim-11 | Auszahlung anordnen (2026-03) -> OK — 651.28 EUR (ANGELEGT)
2026-10-13 11:20:40.104 sb-sim-11 | Doppelklick: gleiche Vorgangsnummer erneut -> OK — korrekt als bereits gebucht erkannt

## TF 75 KOMBINATION SWEET-0075
status = VERIFIZIERT
hauptperson = Fischer, Anna
bearbeiter = sb-sim-09
bg_id = 17799
dauer_ms = 52
ids = zahlungId=16539, vorgangsNr=4bfc0ae3-2fc1-4d89-97c5-0fb8a1c354fb, mitarbeiter=sb-sim-09, bgId=17799, letzteZahlungId=16539, kollege=sb-sim-15

### rechenwege

block: Kombinationsfall 75: Single
zahlungsmonat = 2026-07
vermoegen = 10150.00
schonvermoegen = 10000.00
budget_start = 700.95
p1_eingaben: rbs=1, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=true, kdu_miete=610.00, kdu_betrieb=90.00, kdu_heizung=85.00, rente=760.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p1_regelbedarf=563.00
p1_mehrbedarf_g=0.00
p1_mehrbedarf_schwanger=0.00
p1_mehrbedarf_alleinerziehend=0.00
p1_mehrbedarf_warmwasser=12.95
p1_kdu=785.00
p1_gesamtbedarf=1360.95
p1_freibetrag_erwerb=0.00
p1_absetzbetaetrag=0.00
p1_anrechenbar=760.00
p1_zahlbetrag=600.95
bg_gesamtbedarf=1360.95
bg_anrechenbar=760.00
bg_verwertbares_vermoegen=150.00
bg_zahlbetrag=450.95
budget_rest_erwartet=250.00

### verifikation

V1: OK | alle Personen als eigene SozialFall-Zeilen
    sql    = SELECT COUNT(*) FROM SOZIAL_FALL WHERE BG_ID = ?
    params = [17799]
    erwartet = 1 | ist = 1
V2: OK | finale Zahlung freigegeben (oder bereits übergeben)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [16539]
    erwartet = BEREIT|UEBERGEBEN | ist = UEBERGEBEN
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [16539]
    erwartet = 450.95 | ist = 450.95
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17799]
    erwartet = 250.00 | ist = 250
V5: OK | Freigabevermerk nennt den Kollegen (Vier-Augen)
    sql    = SELECT CASE WHEN FREIGEGEBEN_VON = ? THEN 'ok' ELSE 'abweichend' END FROM ZAHLUNG WHERE ID = ?
    params = [sb-sim-15, 16539]
    erwartet = ok | ist = ok
V6: OK | Vermögen nach Verbrauchsfortschreibung (§ 90)
    sql    = SELECT VERMOEGEN FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17799]
    erwartet = 10000.00 | ist = 10000
V7: OK | dokumentierter Vermögenseinsatz an der Zahlung
    sql    = SELECT VERMOEGENSEINSATZ FROM ZAHLUNG WHERE ID = ?
    params = [16539]
    erwartet = 150.00 | ist = 150
V8: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:20:40.048 sb-sim-09 | Vorgang anlegen (Single, Budget 700.95 EUR, Vermögen 10150.00 EUR) -> OK — Vorgang 17799
2026-10-13 11:20:40.060 sb-sim-09 | 1 SozialFall/Fälle erfassen -> OK — Single, KdU 785.00 EUR
2026-10-13 11:20:40.081 sb-sim-09 | Auszahlung anordnen (2026-07) -> OK — 450.95 EUR (ANGELEGT)
2026-10-13 11:20:40.092 sb-sim-09 | Freigabe durch Kollegen (Vier-Augen) -> OK — freigegeben durch sb-sim-15

## TF 76 KOMBINATION SWEET-0076
status = VERIFIZIERT
hauptperson = Egger, Anna
bearbeiter = sb-sim-18
bg_id = 17798
dauer_ms = 40
ids = zahlungId=16538, vorgangsNr=5d58dfd3-1ef6-46cc-9410-871f8782340b, mitarbeiter=sb-sim-18, bgId=17798, letzteZahlungId=16538, kollege=sb-sim-04

### rechenwege

block: Kombinationsfall 76: Single
zahlungsmonat = 2026-03
vermoegen = 0.00
schonvermoegen = 10000.00
budget_start = 903.00
p1_eingaben: rbs=1, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=220.00, kdu_betrieb=135.00, kdu_heizung=85.00, rente=420.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p1_regelbedarf=563.00
p1_mehrbedarf_g=0.00
p1_mehrbedarf_schwanger=0.00
p1_mehrbedarf_alleinerziehend=0.00
p1_mehrbedarf_warmwasser=0.00
p1_kdu=440.00
p1_gesamtbedarf=1003.00
p1_freibetrag_erwerb=0.00
p1_absetzbetaetrag=0.00
p1_anrechenbar=420.00
p1_zahlbetrag=583.00
bg_gesamtbedarf=1003.00
bg_anrechenbar=420.00
bg_verwertbares_vermoegen=0.00
bg_zahlbetrag=583.00
budget_rest_erwartet=320.00

### verifikation

V1: OK | alle Personen als eigene SozialFall-Zeilen
    sql    = SELECT COUNT(*) FROM SOZIAL_FALL WHERE BG_ID = ?
    params = [17798]
    erwartet = 1 | ist = 1
V2: OK | finale Zahlung wirksam (Vier-Augen-Warteschlange je nach Kassenlauf)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [16538]
    erwartet = ANGELEGT|BEREIT|UEBERGEBEN | ist = ANGELEGT
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [16538]
    erwartet = 583.00 | ist = 583
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17798]
    erwartet = 320.00 | ist = 320
V5: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:20:40.048 sb-sim-18 | Vorgang anlegen (Single, Budget 903.00 EUR) -> OK — Vorgang 17798
2026-10-13 11:20:40.060 sb-sim-18 | 1 SozialFall/Fälle erfassen -> OK — Single, KdU 440.00 EUR
2026-10-13 11:20:40.081 sb-sim-18 | Auszahlung anordnen (2026-03) -> OK — 583.00 EUR (ANGELEGT)

## TF 77 KOMBINATION SWEET-0077
status = VERIFIZIERT
hauptperson = Hoffmann, Jens
bearbeiter = sb-sim-06
bg_id = 17800
dauer_ms = 71
ids = zahlungId=16554, vorgangsNr=f0cfad89-1714-4c87-a8c3-58b0ce843ea7, mitarbeiter=sb-sim-06, bgId=17800, letzteZahlungId=16554, kollege=sb-sim-01

### rechenwege

block: Kombinationsfall 77: Paar mit 2 Kind(ern)
zahlungsmonat = 2026-05
vermoegen = 0.00
schonvermoegen = 21000.00
budget_start = 640.88
p1_eingaben: rbs=2, merkzeichen_g=true, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=740.00, kdu_betrieb=0.00, kdu_heizung=0.00, rente=0.00, erwerb=990.00, abz_steuern=70.00, abz_sv=160.00, abz_vers=15.00, abz_erwerb=10.00
p1_regelbedarf=506.00
p1_mehrbedarf_g=86.02
p1_mehrbedarf_schwanger=0.00
p1_mehrbedarf_alleinerziehend=0.00
p1_mehrbedarf_warmwasser=0.00
p1_kdu=740.00
p1_gesamtbedarf=1332.02
p1_freibetrag_erwerb=281.50
p1_absetzbetaetrag=255.00
p1_anrechenbar=453.50
p1_zahlbetrag=878.52
p2_eingaben: rbs=2, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=0.00, kdu_betrieb=140.00, kdu_heizung=75.00, rente=0.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p2_regelbedarf=506.00
p2_mehrbedarf_g=0.00
p2_mehrbedarf_schwanger=0.00
p2_mehrbedarf_alleinerziehend=0.00
p2_mehrbedarf_warmwasser=0.00
p2_kdu=215.00
p2_gesamtbedarf=721.00
p2_freibetrag_erwerb=0.00
p2_absetzbetaetrag=0.00
p2_anrechenbar=0.00
p2_zahlbetrag=721.00
p3_eingaben: rbs=6, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=true, kdu_miete=0.00, kdu_betrieb=0.00, kdu_heizung=0.00, rente=450.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p3_regelbedarf=357.00
p3_mehrbedarf_g=0.00
p3_mehrbedarf_schwanger=0.00
p3_mehrbedarf_alleinerziehend=0.00
p3_mehrbedarf_warmwasser=2.86
p3_kdu=0.00
p3_gesamtbedarf=359.86
p3_freibetrag_erwerb=0.00
p3_absetzbetaetrag=0.00
p3_anrechenbar=450.00
p3_zahlbetrag=-90.14
p4_eingaben: rbs=5, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=0.00, kdu_betrieb=0.00, kdu_heizung=0.00, rente=910.00, erwerb=1140.00, abz_steuern=180.00, abz_sv=60.00, abz_vers=70.00, abz_erwerb=10.00
p4_regelbedarf=390.00
p4_mehrbedarf_g=0.00
p4_mehrbedarf_schwanger=0.00
p4_mehrbedarf_alleinerziehend=0.00
p4_mehrbedarf_warmwasser=0.00
p4_kdu=0.00
p4_gesamtbedarf=390.00
p4_freibetrag_erwerb=281.50
p4_absetzbetaetrag=320.00
p4_anrechenbar=1448.50
p4_zahlbetrag=-1058.50
bg_gesamtbedarf=2802.88
bg_anrechenbar=2352.00
bg_verwertbares_vermoegen=0.00
bg_zahlbetrag=450.88
budget_rest_erwartet=190.00

### verifikation

V1: OK | alle Personen als eigene SozialFall-Zeilen
    sql    = SELECT COUNT(*) FROM SOZIAL_FALL WHERE BG_ID = ?
    params = [17800]
    erwartet = 4 | ist = 4
V2: OK | finale Zahlung wirksam (Vier-Augen-Warteschlange je nach Kassenlauf)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [16554]
    erwartet = ANGELEGT|BEREIT|UEBERGEBEN | ist = ANGELEGT
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [16554]
    erwartet = 450.88 | ist = 450.88
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17800]
    erwartet = 190.00 | ist = 190
V5: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:20:40.056 sb-sim-06 | Vorgang anlegen (Paar mit 2 Kind(ern), Budget 640.88 EUR) -> OK — Vorgang 17800
2026-10-13 11:20:40.099 sb-sim-06 | 4 SozialFall/Fälle erfassen -> OK — Paar mit 2 Kind(ern), KdU 955.00 EUR
2026-10-13 11:20:40.118 sb-sim-06 | Auszahlung anordnen (2026-05) -> OK — 450.88 EUR (ANGELEGT)

## TF 78 KOMBINATION SWEET-0078
status = VERIFIZIERT
hauptperson = Zimmer, Ingrid
bearbeiter = sb-sim-17
bg_id = 17801
dauer_ms = 86
ids = zahlungId=16550, vorgangsNr=63c4903a-51a0-4cd8-9eaa-df2b978b0224, mitarbeiter=sb-sim-17, bgId=17801, letzteZahlungId=16550, kollege=sb-sim-10

### rechenwege

block: Kombinationsfall 78: Paar mit 1 Kind(ern) (Schwangerschafts-Mehrbedarf entfällt im Zahlungsmonat)
zahlungsmonat = 2026-08
vermoegen = 20600.00
schonvermoegen = 20500.00
budget_start = 193.50
p1_eingaben: rbs=2, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=530.00, kdu_betrieb=60.00, kdu_heizung=65.00, rente=1050.00, erwerb=1150.00, abz_steuern=80.00, abz_sv=350.00, abz_vers=35.00, abz_erwerb=90.00
p1_regelbedarf=506.00
p1_mehrbedarf_g=0.00
p1_mehrbedarf_schwanger=0.00
p1_mehrbedarf_alleinerziehend=0.00
p1_mehrbedarf_warmwasser=0.00
p1_kdu=655.00
p1_gesamtbedarf=1161.00
p1_freibetrag_erwerb=281.50
p1_absetzbetaetrag=555.00
p1_anrechenbar=1363.50
p1_zahlbetrag=-202.50
p2_eingaben: rbs=2, merkzeichen_g=false, schwanger=false, entbindungsmonat=2026-06, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=0.00, kdu_betrieb=0.00, kdu_heizung=0.00, rente=0.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p2_regelbedarf=506.00
p2_mehrbedarf_g=0.00
p2_mehrbedarf_schwanger=0.00
p2_mehrbedarf_alleinerziehend=0.00
p2_mehrbedarf_warmwasser=0.00
p2_kdu=0.00
p2_gesamtbedarf=506.00
p2_freibetrag_erwerb=0.00
p2_absetzbetaetrag=0.00
p2_anrechenbar=0.00
p2_zahlbetrag=506.00
p3_eingaben: rbs=4, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=0.00, kdu_betrieb=0.00, kdu_heizung=0.00, rente=1010.00, erwerb=330.00, abz_steuern=190.00, abz_sv=290.00, abz_vers=30.00, abz_erwerb=60.00
p3_regelbedarf=471.00
p3_mehrbedarf_g=0.00
p3_mehrbedarf_schwanger=0.00
p3_mehrbedarf_alleinerziehend=0.00
p3_mehrbedarf_warmwasser=0.00
p3_kdu=0.00
p3_gesamtbedarf=471.00
p3_freibetrag_erwerb=99.00
p3_absetzbetaetrag=570.00
p3_anrechenbar=671.00
p3_zahlbetrag=-200.00
bg_gesamtbedarf=2138.00
bg_anrechenbar=2034.50
bg_verwertbares_vermoegen=100.00
bg_zahlbetrag=3.50
budget_rest_erwartet=190.00

### verifikation

V1: OK | alle Personen als eigene SozialFall-Zeilen
    sql    = SELECT COUNT(*) FROM SOZIAL_FALL WHERE BG_ID = ?
    params = [17801]
    erwartet = 3 | ist = 3
V2: OK | finale Zahlung freigegeben (oder bereits übergeben)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [16550]
    erwartet = BEREIT|UEBERGEBEN | ist = UEBERGEBEN
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [16550]
    erwartet = 3.50 | ist = 3.5
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17801]
    erwartet = 190.00 | ist = 190
V5: OK | genau eine Zahlung trotz Wiederholung
    sql    = SELECT COUNT(*) FROM ZAHLUNG WHERE BG_ID = ?
    params = [17801]
    erwartet = 1 | ist = 1
V6: OK | Freigabevermerk nennt den Kollegen (Vier-Augen)
    sql    = SELECT CASE WHEN FREIGEGEBEN_VON = ? THEN 'ok' ELSE 'abweichend' END FROM ZAHLUNG WHERE ID = ?
    params = [sb-sim-10, 16550]
    erwartet = ok | ist = ok
V7: OK | Vermögen nach Verbrauchsfortschreibung (§ 90)
    sql    = SELECT VERMOEGEN FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17801]
    erwartet = 20500.00 | ist = 20500
V8: OK | dokumentierter Vermögenseinsatz an der Zahlung
    sql    = SELECT VERMOEGENSEINSATZ FROM ZAHLUNG WHERE ID = ?
    params = [16550]
    erwartet = 100.00 | ist = 100
V9: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:20:40.056 sb-sim-17 | Vorgang anlegen (Paar mit 1 Kind(ern), Budget 193.50 EUR, Vermögen 20600.00 EUR) -> OK — Vorgang 17801
2026-10-13 11:20:40.092 sb-sim-17 | 3 SozialFall/Fälle erfassen -> OK — Paar mit 1 Kind(ern), KdU 655.00 EUR
2026-10-13 11:20:40.110 sb-sim-17 | Auszahlung anordnen (2026-08) -> OK — 3.50 EUR (ANGELEGT)
2026-10-13 11:20:40.122 sb-sim-17 | Doppelklick: gleiche Vorgangsnummer erneut -> OK — korrekt als bereits gebucht erkannt
2026-10-13 11:20:40.133 sb-sim-17 | Freigabe durch Kollegen (Vier-Augen) -> OK — freigegeben durch sb-sim-10

## TF 79 KOMBINATION SWEET-0079
status = VERIFIZIERT
hauptperson = Ismail, Jens
bearbeiter = sb-sim-20
bg_id = 17802
dauer_ms = 53
ids = zahlungId=16544, vorgangsNr=69b477f7-1d31-4578-b3b6-586d8cda98a1, mitarbeiter=sb-sim-20, bgId=17802, letzteZahlungId=16544, kollege=sb-sim-01

### rechenwege

block: Kombinationsfall 79: Single
zahlungsmonat = 2026-09
vermoegen = 10150.00
schonvermoegen = 10000.00
budget_start = 607.45
p1_eingaben: rbs=1, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=true, kdu_miete=700.00, kdu_betrieb=70.00, kdu_heizung=70.00, rente=130.00, erwerb=1290.00, abz_steuern=60.00, abz_sv=190.00, abz_vers=40.00, abz_erwerb=70.00
p1_regelbedarf=563.00
p1_mehrbedarf_g=0.00
p1_mehrbedarf_schwanger=0.00
p1_mehrbedarf_alleinerziehend=0.00
p1_mehrbedarf_warmwasser=12.95
p1_kdu=840.00
p1_gesamtbedarf=1415.95
p1_freibetrag_erwerb=281.50
p1_absetzbetaetrag=360.00
p1_anrechenbar=778.50
p1_zahlbetrag=637.45
bg_gesamtbedarf=1415.95
bg_anrechenbar=778.50
bg_verwertbares_vermoegen=150.00
bg_zahlbetrag=487.45
budget_rest_erwartet=120.00

### verifikation

V1: OK | alle Personen als eigene SozialFall-Zeilen
    sql    = SELECT COUNT(*) FROM SOZIAL_FALL WHERE BG_ID = ?
    params = [17802]
    erwartet = 1 | ist = 1
V2: OK | finale Zahlung wirksam (Vier-Augen-Warteschlange je nach Kassenlauf)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [16544]
    erwartet = ANGELEGT|BEREIT|UEBERGEBEN | ist = ANGELEGT
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [16544]
    erwartet = 487.45 | ist = 487.45
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17802]
    erwartet = 120.00 | ist = 120
V5: OK | genau eine Zahlung trotz Wiederholung
    sql    = SELECT COUNT(*) FROM ZAHLUNG WHERE BG_ID = ?
    params = [17802]
    erwartet = 1 | ist = 1
V6: OK | Vermögen nach Verbrauchsfortschreibung (§ 90)
    sql    = SELECT VERMOEGEN FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17802]
    erwartet = 10000.00 | ist = 10000
V7: OK | dokumentierter Vermögenseinsatz an der Zahlung
    sql    = SELECT VERMOEGENSEINSATZ FROM ZAHLUNG WHERE ID = ?
    params = [16544]
    erwartet = 150.00 | ist = 150
V8: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:20:40.058 sb-sim-20 | Vorgang anlegen (Single, Budget 607.45 EUR, Vermögen 10150.00 EUR) -> OK — Vorgang 17802
2026-10-13 11:20:40.071 sb-sim-20 | 1 SozialFall/Fälle erfassen -> OK — Single, KdU 840.00 EUR
2026-10-13 11:20:40.089 sb-sim-20 | Auszahlung anordnen (2026-09) -> OK — 487.45 EUR (ANGELEGT)
2026-10-13 11:20:40.102 sb-sim-20 | Doppelklick: gleiche Vorgangsnummer erneut -> OK — korrekt als bereits gebucht erkannt

## TF 80 KOMBINATION SWEET-0080
status = VERIFIZIERT
hauptperson = Quest, Dieter
bearbeiter = sb-sim-04
bg_id = 17803
dauer_ms = 97
ids = zahlungId=16559, vorgangsNr=1be09551-f68b-415f-8b47-5a05877c8782, mitarbeiter=sb-sim-04, bgId=17803, letzteZahlungId=16559, kollege=sb-sim-03

### rechenwege

block: Kombinationsfall 80: Paar mit 1 Kind(ern)
zahlungsmonat = 2026-04
vermoegen = 0.00
schonvermoegen = 20500.00
budget_start = 1183.30
p1_eingaben: rbs=2, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=true, kdu_miete=670.00, kdu_betrieb=105.00, kdu_heizung=160.00, rente=0.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p1_regelbedarf=506.00
p1_mehrbedarf_g=0.00
p1_mehrbedarf_schwanger=0.00
p1_mehrbedarf_alleinerziehend=0.00
p1_mehrbedarf_warmwasser=11.64
p1_kdu=935.00
p1_gesamtbedarf=1452.64
p1_freibetrag_erwerb=0.00
p1_absetzbetaetrag=0.00
p1_anrechenbar=0.00
p1_zahlbetrag=1452.64
p2_eingaben: rbs=2, merkzeichen_g=true, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=true, kdu_miete=0.00, kdu_betrieb=0.00, kdu_heizung=0.00, rente=360.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p2_regelbedarf=506.00
p2_mehrbedarf_g=86.02
p2_mehrbedarf_schwanger=0.00
p2_mehrbedarf_alleinerziehend=0.00
p2_mehrbedarf_warmwasser=11.64
p2_kdu=0.00
p2_gesamtbedarf=603.66
p2_freibetrag_erwerb=0.00
p2_absetzbetaetrag=0.00
p2_anrechenbar=360.00
p2_zahlbetrag=243.66
p3_eingaben: rbs=6, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=0.00, kdu_betrieb=0.00, kdu_heizung=0.00, rente=1010.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p3_regelbedarf=357.00
p3_mehrbedarf_g=0.00
p3_mehrbedarf_schwanger=0.00
p3_mehrbedarf_alleinerziehend=0.00
p3_mehrbedarf_warmwasser=0.00
p3_kdu=0.00
p3_gesamtbedarf=357.00
p3_freibetrag_erwerb=0.00
p3_absetzbetaetrag=0.00
p3_anrechenbar=1010.00
p3_zahlbetrag=-653.00
bg_gesamtbedarf=2413.30
bg_anrechenbar=1370.00
bg_verwertbares_vermoegen=0.00
bg_zahlbetrag=1043.30
budget_rest_erwartet=140.00

### verifikation

V1: OK | alle Personen als eigene SozialFall-Zeilen
    sql    = SELECT COUNT(*) FROM SOZIAL_FALL WHERE BG_ID = ?
    params = [17803]
    erwartet = 3 | ist = 3
V2: OK | finale Zahlung freigegeben (oder bereits übergeben)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [16559]
    erwartet = BEREIT|UEBERGEBEN | ist = UEBERGEBEN
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [16559]
    erwartet = 1043.30 | ist = 1043.3
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17803]
    erwartet = 140.00 | ist = 140
V5: OK | Freigabevermerk nennt den Kollegen (Vier-Augen)
    sql    = SELECT CASE WHEN FREIGEGEBEN_VON = ? THEN 'ok' ELSE 'abweichend' END FROM ZAHLUNG WHERE ID = ?
    params = [sb-sim-03, 16559]
    erwartet = ok | ist = ok
V6: OK | genau ein erteilter Bewilligungsbescheid
    sql    = SELECT COUNT(*) FROM BESCHEID WHERE BG_ID = ?
    params = [17803]
    erwartet = 1 | ist = 1
V7: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:20:40.058 sb-sim-04 | Vorgang anlegen (Paar mit 1 Kind(ern), Budget 1183.30 EUR) -> OK — Vorgang 17803
2026-10-13 11:20:40.092 sb-sim-04 | 3 SozialFall/Fälle erfassen -> OK — Paar mit 1 Kind(ern), KdU 935.00 EUR
2026-10-13 11:20:40.113 sb-sim-04 | Bewilligungsbescheid erteilen (2026-02 .. 2026-06) -> OK — Bescheid BESCH-SWEET-0080-2026-1747 mit Monatsplan gespeichert
2026-10-13 11:20:40.133 sb-sim-04 | Auszahlung anordnen (2026-04) -> OK — 1043.30 EUR (ANGELEGT)
2026-10-13 11:20:40.147 sb-sim-04 | Freigabe durch Kollegen (Vier-Augen) -> OK — freigegeben durch sb-sim-03

## TF 81 KOMBINATION SWEET-0081
status = VERIFIZIERT
hauptperson = Neumann, Volkmar
bearbeiter = sb-sim-02
bg_id = 17804
dauer_ms = 62
ids = zahlungId=16552, vorgangsNr=b2d2c582-bb32-40f4-ba4c-ff8a3d5484c6, mitarbeiter=sb-sim-02, bgId=17804, letzteZahlungId=16552, kollege=sb-sim-01

### rechenwege

block: Kombinationsfall 81: Paar mit 1 Kind(ern) (Schwangerschafts-Mehrbedarf entfällt im Zahlungsmonat)
zahlungsmonat = 2026-02
vermoegen = 0.00
schonvermoegen = 20500.00
budget_start = 552.68
p1_eingaben: rbs=2, merkzeichen_g=true, schwanger=false, entbindungsmonat=2026-01, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=true, kdu_miete=350.00, kdu_betrieb=0.00, kdu_heizung=0.00, rente=970.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p1_regelbedarf=506.00
p1_mehrbedarf_g=86.02
p1_mehrbedarf_schwanger=0.00
p1_mehrbedarf_alleinerziehend=0.00
p1_mehrbedarf_warmwasser=11.64
p1_kdu=350.00
p1_gesamtbedarf=953.66
p1_freibetrag_erwerb=0.00
p1_absetzbetaetrag=0.00
p1_anrechenbar=970.00
p1_zahlbetrag=-16.34
p2_eingaben: rbs=2, merkzeichen_g=true, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=0.00, kdu_betrieb=65.00, kdu_heizung=125.00, rente=0.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p2_regelbedarf=506.00
p2_mehrbedarf_g=86.02
p2_mehrbedarf_schwanger=0.00
p2_mehrbedarf_alleinerziehend=0.00
p2_mehrbedarf_warmwasser=0.00
p2_kdu=190.00
p2_gesamtbedarf=782.02
p2_freibetrag_erwerb=0.00
p2_absetzbetaetrag=0.00
p2_anrechenbar=0.00
p2_zahlbetrag=782.02
p3_eingaben: rbs=6, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=0.00, kdu_betrieb=0.00, kdu_heizung=0.00, rente=850.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p3_regelbedarf=357.00
p3_mehrbedarf_g=0.00
p3_mehrbedarf_schwanger=0.00
p3_mehrbedarf_alleinerziehend=0.00
p3_mehrbedarf_warmwasser=0.00
p3_kdu=0.00
p3_gesamtbedarf=357.00
p3_freibetrag_erwerb=0.00
p3_absetzbetaetrag=0.00
p3_anrechenbar=850.00
p3_zahlbetrag=-493.00
bg_gesamtbedarf=2092.68
bg_anrechenbar=1820.00
bg_verwertbares_vermoegen=0.00
bg_zahlbetrag=272.68
budget_rest_erwartet=280.00

### verifikation

V1: OK | alle Personen als eigene SozialFall-Zeilen
    sql    = SELECT COUNT(*) FROM SOZIAL_FALL WHERE BG_ID = ?
    params = [17804]
    erwartet = 3 | ist = 3
V2: OK | finale Zahlung wirksam (Vier-Augen-Warteschlange je nach Kassenlauf)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [16552]
    erwartet = ANGELEGT|BEREIT|UEBERGEBEN | ist = ANGELEGT
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [16552]
    erwartet = 272.68 | ist = 272.68
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17804]
    erwartet = 280.00 | ist = 280
V5: OK | Wiedervorlagedatum exakt wie gesetzt
    sql    = SELECT COUNT(*) FROM BEDARFSGEMEINSCHAFT WHERE ID = ? AND WIEDERVORLAGE_AM = ?
    params = [17804, 2026-11-24]
    erwartet = 1 | ist = 1
V6: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:20:40.062 sb-sim-02 | Vorgang anlegen (Paar mit 1 Kind(ern), Budget 552.68 EUR) -> OK — Vorgang 17804
2026-10-13 11:20:40.096 sb-sim-02 | 3 SozialFall/Fälle erfassen -> OK — Paar mit 1 Kind(ern), KdU 540.00 EUR
2026-10-13 11:20:40.117 sb-sim-02 | Auszahlung anordnen (2026-02) -> OK — 272.68 EUR (ANGELEGT)

## TF 82 KOMBINATION SWEET-0082
status = VERIFIZIERT
hauptperson = Vogt, Volkmar
bearbeiter = sb-sim-05
bg_id = 17805
dauer_ms = 52
ids = zahlungId=16551, vorgangsNr=412ff43e-a6a7-4277-9b61-22a75f091584, mitarbeiter=sb-sim-05, bgId=17805, letzteZahlungId=16551, kollege=sb-sim-20

### rechenwege

block: Kombinationsfall 82: Single
zahlungsmonat = 2026-02
vermoegen = 10000.00
schonvermoegen = 10000.00
budget_start = 688.00
p1_eingaben: rbs=1, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=450.00, kdu_betrieb=30.00, kdu_heizung=75.00, rente=820.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p1_regelbedarf=563.00
p1_mehrbedarf_g=0.00
p1_mehrbedarf_schwanger=0.00
p1_mehrbedarf_alleinerziehend=0.00
p1_mehrbedarf_warmwasser=0.00
p1_kdu=555.00
p1_gesamtbedarf=1118.00
p1_freibetrag_erwerb=0.00
p1_absetzbetaetrag=0.00
p1_anrechenbar=820.00
p1_zahlbetrag=298.00
bg_gesamtbedarf=1118.00
bg_anrechenbar=820.00
bg_verwertbares_vermoegen=0.00
bg_zahlbetrag=298.00
budget_rest_erwartet=390.00

### verifikation

V1: OK | alle Personen als eigene SozialFall-Zeilen
    sql    = SELECT COUNT(*) FROM SOZIAL_FALL WHERE BG_ID = ?
    params = [17805]
    erwartet = 1 | ist = 1
V2: OK | finale Zahlung wirksam (Vier-Augen-Warteschlange je nach Kassenlauf)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [16551]
    erwartet = ANGELEGT|BEREIT|UEBERGEBEN | ist = ANGELEGT
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [16551]
    erwartet = 298.00 | ist = 298
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17805]
    erwartet = 390.00 | ist = 390
V5: OK | genau ein erteilter Bewilligungsbescheid
    sql    = SELECT COUNT(*) FROM BESCHEID WHERE BG_ID = ?
    params = [17805]
    erwartet = 1 | ist = 1
V6: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:20:40.068 sb-sim-05 | Vorgang anlegen (Single, Budget 688.00 EUR, Vermögen 10000.00 EUR) -> OK — Vorgang 17805
2026-10-13 11:20:40.081 sb-sim-05 | 1 SozialFall/Fälle erfassen -> OK — Single, KdU 555.00 EUR
2026-10-13 11:20:40.092 sb-sim-05 | Bewilligungsbescheid erteilen (2026-02 .. 2026-02) -> OK — Bescheid BESCH-SWEET-0082-2026-1745 mit Monatsplan gespeichert
2026-10-13 11:20:40.110 sb-sim-05 | Auszahlung anordnen (2026-02) -> OK — 298.00 EUR (ANGELEGT)

## TF 83 KOMBINATION SWEET-0083
status = VERIFIZIERT
hauptperson = Jung, Olga
bearbeiter = sb-sim-03
bg_id = 17806
dauer_ms = 36
ids = zahlungId=16547, vorgangsNr=f314ce3d-2ccd-4f40-863b-fbf911787bdd, mitarbeiter=sb-sim-03, bgId=17806, letzteZahlungId=16547, kollege=sb-sim-01

### rechenwege

block: Kombinationsfall 83: Single
zahlungsmonat = 2026-10
vermoegen = 10000.00
schonvermoegen = 10000.00
budget_start = 1290.95
p1_eingaben: rbs=1, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=true, kdu_miete=550.00, kdu_betrieb=55.00, kdu_heizung=40.00, rente=0.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p1_regelbedarf=563.00
p1_mehrbedarf_g=0.00
p1_mehrbedarf_schwanger=0.00
p1_mehrbedarf_alleinerziehend=0.00
p1_mehrbedarf_warmwasser=12.95
p1_kdu=645.00
p1_gesamtbedarf=1220.95
p1_freibetrag_erwerb=0.00
p1_absetzbetaetrag=0.00
p1_anrechenbar=0.00
p1_zahlbetrag=1220.95
bg_gesamtbedarf=1220.95
bg_anrechenbar=0.00
bg_verwertbares_vermoegen=0.00
bg_zahlbetrag=1220.95
budget_rest_erwartet=70.00

### verifikation

V1: OK | alle Personen als eigene SozialFall-Zeilen
    sql    = SELECT COUNT(*) FROM SOZIAL_FALL WHERE BG_ID = ?
    params = [17806]
    erwartet = 1 | ist = 1
V2: OK | finale Zahlung wirksam (Vier-Augen-Warteschlange je nach Kassenlauf)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [16547]
    erwartet = ANGELEGT|BEREIT|UEBERGEBEN | ist = ANGELEGT
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [16547]
    erwartet = 1220.95 | ist = 1220.95
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17806]
    erwartet = 70.00 | ist = 70
V5: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:20:40.071 sb-sim-03 | Vorgang anlegen (Single, Budget 1290.95 EUR, Vermögen 10000.00 EUR) -> OK — Vorgang 17806
2026-10-13 11:20:40.081 sb-sim-03 | 1 SozialFall/Fälle erfassen -> OK — Single, KdU 645.00 EUR
2026-10-13 11:20:40.096 sb-sim-03 | Auszahlung anordnen (2026-10) -> OK — 1220.95 EUR (ANGELEGT)

## TF 84 KOMBINATION SWEET-0084
status = VERIFIZIERT
hauptperson = Petersen, Anna
bearbeiter = sb-sim-19
bg_id = 17807
dauer_ms = 90
ids = letzteZahlungId=16564, kollege=sb-sim-04, bgId=17807, mitarbeiter=sb-sim-19, zahlungId=16548, fallId=26917, vorgangsNr=d029823a-3752-4736-b3e3-29fd4e1cf1e7

### rechenwege

block: Kombinationsfall 84: Single
zahlungsmonat = 2026-10
vermoegen = 0.00
schonvermoegen = 10000.00
budget_start = 513.00
p1_eingaben: rbs=1, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=330.00, kdu_betrieb=140.00, kdu_heizung=140.00, rente=910.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p1_regelbedarf=563.00
p1_mehrbedarf_g=0.00
p1_mehrbedarf_schwanger=0.00
p1_mehrbedarf_alleinerziehend=0.00
p1_mehrbedarf_warmwasser=0.00
p1_kdu=610.00
p1_gesamtbedarf=1173.00
p1_freibetrag_erwerb=0.00
p1_absetzbetaetrag=0.00
p1_anrechenbar=910.00
p1_zahlbetrag=263.00
bg_gesamtbedarf=1173.00
bg_anrechenbar=910.00
bg_verwertbares_vermoegen=0.00
bg_zahlbetrag=263.00
budget_rest_erwartet=250.00

block: Neuanordnung nach Mietekorrektur (Miete 330.00 → 460.00)
zahlungsmonat = 2026-10
vermoegen = 0.00
schonvermoegen = 10000.00
budget_start = 513.00
p1_eingaben: rbs=1, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=460.00, kdu_betrieb=140.00, kdu_heizung=140.00, rente=910.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p1_regelbedarf=563.00
p1_mehrbedarf_g=0.00
p1_mehrbedarf_schwanger=0.00
p1_mehrbedarf_alleinerziehend=0.00
p1_mehrbedarf_warmwasser=0.00
p1_kdu=740.00
p1_gesamtbedarf=1303.00
p1_freibetrag_erwerb=0.00
p1_absetzbetaetrag=0.00
p1_anrechenbar=910.00
p1_zahlbetrag=393.00
bg_gesamtbedarf=1303.00
bg_anrechenbar=910.00
bg_verwertbares_vermoegen=0.00
bg_zahlbetrag=393.00
budget_rest_erwartet=120.00

### verifikation

V1: OK | alle Personen als eigene SozialFall-Zeilen
    sql    = SELECT COUNT(*) FROM SOZIAL_FALL WHERE BG_ID = ?
    params = [17807]
    erwartet = 1 | ist = 1
V2: OK | finale Zahlung wirksam (Vier-Augen-Warteschlange je nach Kassenlauf)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [16564]
    erwartet = ANGELEGT|BEREIT|UEBERGEBEN | ist = ANGELEGT
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [16564]
    erwartet = 393.00 | ist = 393
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17807]
    erwartet = 120.00 | ist = 120
V5: OK | zwei Zahlungen (stornierte + korrigierte)
    sql    = SELECT COUNT(*) FROM ZAHLUNG WHERE BG_ID = ?
    params = [17807]
    erwartet = 2 | ist = 2
V6: OK | erste Zahlung STORNIERT
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [16548]
    erwartet = STORNIERT | ist = STORNIERT
V7: OK | Fall in Version 2 (optimistisches Locking)
    sql    = SELECT VERSION FROM SOZIAL_FALL WHERE ID = ?
    params = [26917]
    erwartet = 2 | ist = 2
V8: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:20:40.071 sb-sim-19 | Vorgang anlegen (Single, Budget 513.00 EUR) -> OK — Vorgang 17807
2026-10-13 11:20:40.082 sb-sim-19 | 1 SozialFall/Fälle erfassen -> OK — Single, KdU 610.00 EUR
2026-10-13 11:20:40.099 sb-sim-19 | Auszahlung anordnen (2026-10) -> OK — 263.00 EUR (ANGELEGT)
2026-10-13 11:20:40.110 sb-sim-19 | Anordnung sofort stornieren (Status ANGELEGT) -> OK — storniert — Monat wieder frei
2026-10-13 11:20:40.132 sb-sim-19 | Miete auf 460.00 korrigieren -> OK — Fall 26917 in Version 2
2026-10-13 11:20:40.150 sb-sim-19 | Korrigierte Auszahlung für denselben Monat -> OK — 393.00 EUR

## TF 85 KOMBINATION SWEET-0085
status = VERIFIZIERT
hauptperson = Meyer, Jens
bearbeiter = sb-sim-10
bg_id = 17808
dauer_ms = 66
ids = zahlungId=16549, vorgangsNr=a0906d80-834c-414c-b531-f73b16270f99, mitarbeiter=sb-sim-10, bgId=17808, letzteZahlungId=16549, kollege=sb-sim-16

### rechenwege

block: Kombinationsfall 85: Single
zahlungsmonat = 2026-06
vermoegen = 10250.00
schonvermoegen = 10000.00
budget_start = 1048.00
p1_eingaben: rbs=1, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=650.00, kdu_betrieb=110.00, kdu_heizung=155.00, rente=390.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p1_regelbedarf=563.00
p1_mehrbedarf_g=0.00
p1_mehrbedarf_schwanger=0.00
p1_mehrbedarf_alleinerziehend=0.00
p1_mehrbedarf_warmwasser=0.00
p1_kdu=915.00
p1_gesamtbedarf=1478.00
p1_freibetrag_erwerb=0.00
p1_absetzbetaetrag=0.00
p1_anrechenbar=390.00
p1_zahlbetrag=1088.00
bg_gesamtbedarf=1478.00
bg_anrechenbar=390.00
bg_verwertbares_vermoegen=250.00
bg_zahlbetrag=838.00
budget_rest_erwartet=210.00

### verifikation

V1: OK | alle Personen als eigene SozialFall-Zeilen
    sql    = SELECT COUNT(*) FROM SOZIAL_FALL WHERE BG_ID = ?
    params = [17808]
    erwartet = 1 | ist = 1
V2: OK | finale Zahlung freigegeben (oder bereits übergeben)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [16549]
    erwartet = BEREIT|UEBERGEBEN | ist = UEBERGEBEN
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [16549]
    erwartet = 838.00 | ist = 838
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17808]
    erwartet = 210.00 | ist = 210
V5: OK | genau eine Zahlung trotz Wiederholung
    sql    = SELECT COUNT(*) FROM ZAHLUNG WHERE BG_ID = ?
    params = [17808]
    erwartet = 1 | ist = 1
V6: OK | Freigabevermerk nennt den Kollegen (Vier-Augen)
    sql    = SELECT CASE WHEN FREIGEGEBEN_VON = ? THEN 'ok' ELSE 'abweichend' END FROM ZAHLUNG WHERE ID = ?
    params = [sb-sim-16, 16549]
    erwartet = ok | ist = ok
V7: OK | Vermögen nach Verbrauchsfortschreibung (§ 90)
    sql    = SELECT VERMOEGEN FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17808]
    erwartet = 10000.00 | ist = 10000
V8: OK | dokumentierter Vermögenseinsatz an der Zahlung
    sql    = SELECT VERMOEGENSEINSATZ FROM ZAHLUNG WHERE ID = ?
    params = [16549]
    erwartet = 250.00 | ist = 250
V9: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:20:40.072 sb-sim-10 | Vorgang anlegen (Single, Budget 1048.00 EUR, Vermögen 10250.00 EUR) -> OK — Vorgang 17808
2026-10-13 11:20:40.084 sb-sim-10 | 1 SozialFall/Fälle erfassen -> OK — Single, KdU 915.00 EUR
2026-10-13 11:20:40.102 sb-sim-10 | Auszahlung anordnen (2026-06) -> OK — 838.00 EUR (ANGELEGT)
2026-10-13 11:20:40.117 sb-sim-10 | Doppelklick: gleiche Vorgangsnummer erneut -> OK — korrekt als bereits gebucht erkannt
2026-10-13 11:20:40.128 sb-sim-10 | Freigabe durch Kollegen (Vier-Augen) -> OK — freigegeben durch sb-sim-16

## TF 86 KOMBINATION SWEET-0086
status = VERIFIZIERT
hauptperson = Zimmer, Heinz
bearbeiter = sb-sim-14
bg_id = 17809
dauer_ms = 60
ids = zahlungId=16555, vorgangsNr=7857e9c4-7dfd-407d-b14a-ea5f15291814, mitarbeiter=sb-sim-14, bgId=17809, letzteZahlungId=16555, kollege=sb-sim-08

### rechenwege

block: Kombinationsfall 86: Paar (2 × RBS 2)
zahlungsmonat = 2026-09
vermoegen = 0.00
schonvermoegen = 20000.00
budget_start = 278.00
p1_eingaben: rbs=2, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=390.00, kdu_betrieb=0.00, kdu_heizung=0.00, rente=590.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p1_regelbedarf=506.00
p1_mehrbedarf_g=0.00
p1_mehrbedarf_schwanger=0.00
p1_mehrbedarf_alleinerziehend=0.00
p1_mehrbedarf_warmwasser=0.00
p1_kdu=390.00
p1_gesamtbedarf=896.00
p1_freibetrag_erwerb=0.00
p1_absetzbetaetrag=0.00
p1_anrechenbar=590.00
p1_zahlbetrag=306.00
p2_eingaben: rbs=2, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=0.00, kdu_betrieb=110.00, kdu_heizung=125.00, rente=610.00, erwerb=820.00, abz_steuern=100.00, abz_sv=60.00, abz_vers=45.00, abz_erwerb=90.00
p2_regelbedarf=506.00
p2_mehrbedarf_g=0.00
p2_mehrbedarf_schwanger=0.00
p2_mehrbedarf_alleinerziehend=0.00
p2_mehrbedarf_warmwasser=0.00
p2_kdu=235.00
p2_gesamtbedarf=741.00
p2_freibetrag_erwerb=246.00
p2_absetzbetaetrag=295.00
p2_anrechenbar=889.00
p2_zahlbetrag=-148.00
bg_gesamtbedarf=1637.00
bg_anrechenbar=1479.00
bg_verwertbares_vermoegen=0.00
bg_zahlbetrag=158.00
budget_rest_erwartet=120.00

### verifikation

V1: OK | alle Personen als eigene SozialFall-Zeilen
    sql    = SELECT COUNT(*) FROM SOZIAL_FALL WHERE BG_ID = ?
    params = [17809]
    erwartet = 2 | ist = 2
V2: OK | finale Zahlung freigegeben (oder bereits übergeben)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [16555]
    erwartet = BEREIT|UEBERGEBEN | ist = UEBERGEBEN
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [16555]
    erwartet = 158.00 | ist = 158
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17809]
    erwartet = 120.00 | ist = 120
V5: OK | Freigabevermerk nennt den Kollegen (Vier-Augen)
    sql    = SELECT CASE WHEN FREIGEGEBEN_VON = ? THEN 'ok' ELSE 'abweichend' END FROM ZAHLUNG WHERE ID = ?
    params = [sb-sim-08, 16555]
    erwartet = ok | ist = ok
V6: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:20:40.076 sb-sim-14 | Vorgang anlegen (Paar (2 × RBS 2), Budget 278.00 EUR) -> OK — Vorgang 17809
2026-10-13 11:20:40.099 sb-sim-14 | 2 SozialFall/Fälle erfassen -> OK — Paar (2 × RBS 2), KdU 625.00 EUR
2026-10-13 11:20:40.118 sb-sim-14 | Auszahlung anordnen (2026-09) -> OK — 158.00 EUR (ANGELEGT)
2026-10-13 11:20:40.128 sb-sim-14 | Freigabe durch Kollegen (Vier-Augen) -> OK — freigegeben durch sb-sim-08

## TF 87 KOMBINATION SWEET-0087
status = VERIFIZIERT
hauptperson = Petersen, Ludwig
bearbeiter = sb-sim-13
bg_id = 17810
dauer_ms = 40
ids = zahlungId=16553, vorgangsNr=6613aece-837c-4581-8c78-d327b51002e5, mitarbeiter=sb-sim-13, bgId=17810, letzteZahlungId=16553, kollege=sb-sim-09

### rechenwege

block: Kombinationsfall 87: Single
zahlungsmonat = 2026-06
vermoegen = 10050.00
schonvermoegen = 10000.00
budget_start = 1645.95
p1_eingaben: rbs=1, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=true, kdu_miete=600.00, kdu_betrieb=120.00, kdu_heizung=130.00, rente=150.00, erwerb=200.00, abz_steuern=120.00, abz_sv=270.00, abz_vers=70.00, abz_erwerb=50.00
p1_regelbedarf=563.00
p1_mehrbedarf_g=0.00
p1_mehrbedarf_schwanger=0.00
p1_mehrbedarf_alleinerziehend=0.00
p1_mehrbedarf_warmwasser=12.95
p1_kdu=850.00
p1_gesamtbedarf=1425.95
p1_freibetrag_erwerb=60.00
p1_absetzbetaetrag=510.00
p1_anrechenbar=0.00
p1_zahlbetrag=1425.95
bg_gesamtbedarf=1425.95
bg_anrechenbar=0.00
bg_verwertbares_vermoegen=50.00
bg_zahlbetrag=1375.95
budget_rest_erwartet=270.00

### verifikation

V1: OK | alle Personen als eigene SozialFall-Zeilen
    sql    = SELECT COUNT(*) FROM SOZIAL_FALL WHERE BG_ID = ?
    params = [17810]
    erwartet = 1 | ist = 1
V2: OK | finale Zahlung wirksam (Vier-Augen-Warteschlange je nach Kassenlauf)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [16553]
    erwartet = ANGELEGT|BEREIT|UEBERGEBEN | ist = ANGELEGT
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [16553]
    erwartet = 1375.95 | ist = 1375.95
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17810]
    erwartet = 270.00 | ist = 270
V5: OK | Vermögen nach Verbrauchsfortschreibung (§ 90)
    sql    = SELECT VERMOEGEN FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17810]
    erwartet = 10000.00 | ist = 10000
V6: OK | dokumentierter Vermögenseinsatz an der Zahlung
    sql    = SELECT VERMOEGENSEINSATZ FROM ZAHLUNG WHERE ID = ?
    params = [16553]
    erwartet = 50.00 | ist = 50
V7: OK | Wiedervorlagedatum exakt wie gesetzt
    sql    = SELECT COUNT(*) FROM BEDARFSGEMEINSCHAFT WHERE ID = ? AND WIEDERVORLAGE_AM = ?
    params = [17810, 2027-05-19]
    erwartet = 1 | ist = 1
V8: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:20:40.088 sb-sim-13 | Vorgang anlegen (Single, Budget 1645.95 EUR, Vermögen 10050.00 EUR) -> OK — Vorgang 17810
2026-10-13 11:20:40.099 sb-sim-13 | 1 SozialFall/Fälle erfassen -> OK — Single, KdU 850.00 EUR
2026-10-13 11:20:40.118 sb-sim-13 | Auszahlung anordnen (2026-06) -> OK — 1375.95 EUR (ANGELEGT)

## TF 88 KOMBINATION SWEET-0088
status = VERIFIZIERT
hauptperson = Petersen, Gisela
bearbeiter = sb-sim-18
bg_id = 17811
dauer_ms = 57
ids = zahlungId=16562, vorgangsNr=f92deee9-c816-49c4-9d56-a5a9d319f16c, mitarbeiter=sb-sim-18, bgId=17811, letzteZahlungId=16562, kollege=sb-sim-11

### rechenwege

block: Kombinationsfall 88: Single
zahlungsmonat = 2026-11
vermoegen = 10000.00
schonvermoegen = 10000.00
budget_start = 897.45
p1_eingaben: rbs=1, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=true, kdu_miete=270.00, kdu_betrieb=110.00, kdu_heizung=170.00, rente=0.00, erwerb=1130.00, abz_steuern=20.00, abz_sv=50.00, abz_vers=70.00, abz_erwerb=100.00
p1_regelbedarf=563.00
p1_mehrbedarf_g=0.00
p1_mehrbedarf_schwanger=0.00
p1_mehrbedarf_alleinerziehend=0.00
p1_mehrbedarf_warmwasser=12.95
p1_kdu=550.00
p1_gesamtbedarf=1125.95
p1_freibetrag_erwerb=281.50
p1_absetzbetaetrag=240.00
p1_anrechenbar=608.50
p1_zahlbetrag=517.45
bg_gesamtbedarf=1125.95
bg_anrechenbar=608.50
bg_verwertbares_vermoegen=0.00
bg_zahlbetrag=517.45
budget_rest_erwartet=380.00

### verifikation

V1: OK | alle Personen als eigene SozialFall-Zeilen
    sql    = SELECT COUNT(*) FROM SOZIAL_FALL WHERE BG_ID = ?
    params = [17811]
    erwartet = 1 | ist = 1
V2: OK | finale Zahlung wirksam (Vier-Augen-Warteschlange je nach Kassenlauf)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [16562]
    erwartet = ANGELEGT|BEREIT|UEBERGEBEN | ist = ANGELEGT
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [16562]
    erwartet = 517.45 | ist = 517.45
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17811]
    erwartet = 380.00 | ist = 380
V5: OK | genau ein erteilter Bewilligungsbescheid
    sql    = SELECT COUNT(*) FROM BESCHEID WHERE BG_ID = ?
    params = [17811]
    erwartet = 1 | ist = 1
V6: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:20:40.089 sb-sim-18 | Vorgang anlegen (Single, Budget 897.45 EUR, Vermögen 10000.00 EUR) -> OK — Vorgang 17811
2026-10-13 11:20:40.102 sb-sim-18 | 1 SozialFall/Fälle erfassen -> OK — Single, KdU 550.00 EUR
2026-10-13 11:20:40.120 sb-sim-18 | Bewilligungsbescheid erteilen (2026-10 .. 2026-11) -> OK — Bescheid BESCH-SWEET-0088-2026-1748 mit Monatsplan gespeichert
2026-10-13 11:20:40.138 sb-sim-18 | Auszahlung anordnen (2026-11) -> OK — 517.45 EUR (ANGELEGT)

## TF 89 KOMBINATION SWEET-0089
status = VERIFIZIERT
hauptperson = Ismail, Volkmar
bearbeiter = sb-sim-08
bg_id = 17812
dauer_ms = 69
ids = zahlungId=16560, vorgangsNr=3d059e02-cd61-4662-9b57-226cd25ac41c, mitarbeiter=sb-sim-08, bgId=17812, letzteZahlungId=16560, kollege=sb-sim-16

### rechenwege

block: Kombinationsfall 89: Paar (2 × RBS 2) (Schwangerschafts-Mehrbedarf entfällt im Zahlungsmonat)
zahlungsmonat = 2026-08
vermoegen = 20250.00
schonvermoegen = 20000.00
budget_start = 419.64
p1_eingaben: rbs=2, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=260.00, kdu_betrieb=105.00, kdu_heizung=130.00, rente=390.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p1_regelbedarf=506.00
p1_mehrbedarf_g=0.00
p1_mehrbedarf_schwanger=0.00
p1_mehrbedarf_alleinerziehend=0.00
p1_mehrbedarf_warmwasser=0.00
p1_kdu=495.00
p1_gesamtbedarf=1001.00
p1_freibetrag_erwerb=0.00
p1_absetzbetaetrag=0.00
p1_anrechenbar=390.00
p1_zahlbetrag=611.00
p2_eingaben: rbs=2, merkzeichen_g=false, schwanger=false, entbindungsmonat=2026-05, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=true, kdu_miete=0.00, kdu_betrieb=0.00, kdu_heizung=0.00, rente=660.00, erwerb=520.00, abz_steuern=200.00, abz_sv=50.00, abz_vers=55.00, abz_erwerb=50.00
p2_regelbedarf=506.00
p2_mehrbedarf_g=0.00
p2_mehrbedarf_schwanger=0.00
p2_mehrbedarf_alleinerziehend=0.00
p2_mehrbedarf_warmwasser=11.64
p2_kdu=0.00
p2_gesamtbedarf=517.64
p2_freibetrag_erwerb=156.00
p2_absetzbetaetrag=355.00
p2_anrechenbar=669.00
p2_zahlbetrag=-151.36
bg_gesamtbedarf=1518.64
bg_anrechenbar=1059.00
bg_verwertbares_vermoegen=250.00
bg_zahlbetrag=209.64
budget_rest_erwartet=210.00

### verifikation

V1: OK | alle Personen als eigene SozialFall-Zeilen
    sql    = SELECT COUNT(*) FROM SOZIAL_FALL WHERE BG_ID = ?
    params = [17812]
    erwartet = 2 | ist = 2
V2: OK | finale Zahlung freigegeben (oder bereits übergeben)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [16560]
    erwartet = BEREIT|UEBERGEBEN | ist = UEBERGEBEN
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [16560]
    erwartet = 209.64 | ist = 209.64
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17812]
    erwartet = 210.00 | ist = 210
V5: OK | Freigabevermerk nennt den Kollegen (Vier-Augen)
    sql    = SELECT CASE WHEN FREIGEGEBEN_VON = ? THEN 'ok' ELSE 'abweichend' END FROM ZAHLUNG WHERE ID = ?
    params = [sb-sim-16, 16560]
    erwartet = ok | ist = ok
V6: OK | Vermögen nach Verbrauchsfortschreibung (§ 90)
    sql    = SELECT VERMOEGEN FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17812]
    erwartet = 20000.00 | ist = 20000
V7: OK | dokumentierter Vermögenseinsatz an der Zahlung
    sql    = SELECT VERMOEGENSEINSATZ FROM ZAHLUNG WHERE ID = ?
    params = [16560]
    erwartet = 250.00 | ist = 250
V8: OK | Wiedervorlagedatum exakt wie gesetzt
    sql    = SELECT COUNT(*) FROM BEDARFSGEMEINSCHAFT WHERE ID = ? AND WIEDERVORLAGE_AM = ?
    params = [17812, 2027-01-26]
    erwartet = 1 | ist = 1
V9: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:20:40.089 sb-sim-08 | Vorgang anlegen (Paar (2 × RBS 2), Budget 419.64 EUR, Vermögen 20250.00 EUR) -> OK — Vorgang 17812
2026-10-13 11:20:40.117 sb-sim-08 | 2 SozialFall/Fälle erfassen -> OK — Paar (2 × RBS 2), KdU 495.00 EUR
2026-10-13 11:20:40.136 sb-sim-08 | Auszahlung anordnen (2026-08) -> OK — 209.64 EUR (ANGELEGT)
2026-10-13 11:20:40.150 sb-sim-08 | Freigabe durch Kollegen (Vier-Augen) -> OK — freigegeben durch sb-sim-16

## TF 90 KOMBINATION SWEET-0090
status = VERIFIZIERT
hauptperson = Jung, Ursula
bearbeiter = sb-sim-15
bg_id = 17813
dauer_ms = 68
ids = zahlungId=16558, vorgangsNr=1561706c-1d70-437b-a295-76094edfb4a8, mitarbeiter=sb-sim-15, bgId=17813, letzteZahlungId=16558, kollege=sb-sim-18

### rechenwege

block: Kombinationsfall 90: Single
zahlungsmonat = 2026-02
vermoegen = 0.00
schonvermoegen = 10000.00
budget_start = 719.50
p1_eingaben: rbs=1, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=290.00, kdu_betrieb=50.00, kdu_heizung=125.00, rente=0.00, erwerb=1030.00, abz_steuern=70.00, abz_sv=70.00, abz_vers=0.00, abz_erwerb=60.00
p1_regelbedarf=563.00
p1_mehrbedarf_g=0.00
p1_mehrbedarf_schwanger=0.00
p1_mehrbedarf_alleinerziehend=0.00
p1_mehrbedarf_warmwasser=0.00
p1_kdu=465.00
p1_gesamtbedarf=1028.00
p1_freibetrag_erwerb=281.50
p1_absetzbetaetrag=200.00
p1_anrechenbar=548.50
p1_zahlbetrag=479.50
bg_gesamtbedarf=1028.00
bg_anrechenbar=548.50
bg_verwertbares_vermoegen=0.00
bg_zahlbetrag=479.50
budget_rest_erwartet=240.00

### verifikation

V1: OK | alle Personen als eigene SozialFall-Zeilen
    sql    = SELECT COUNT(*) FROM SOZIAL_FALL WHERE BG_ID = ?
    params = [17813]
    erwartet = 1 | ist = 1
V2: OK | finale Zahlung freigegeben (oder bereits übergeben)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [16558]
    erwartet = BEREIT|UEBERGEBEN | ist = UEBERGEBEN
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [16558]
    erwartet = 479.50 | ist = 479.5
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17813]
    erwartet = 240.00 | ist = 240
V5: OK | genau eine Zahlung trotz Wiederholung
    sql    = SELECT COUNT(*) FROM ZAHLUNG WHERE BG_ID = ?
    params = [17813]
    erwartet = 1 | ist = 1
V6: OK | Freigabevermerk nennt den Kollegen (Vier-Augen)
    sql    = SELECT CASE WHEN FREIGEGEBEN_VON = ? THEN 'ok' ELSE 'abweichend' END FROM ZAHLUNG WHERE ID = ?
    params = [sb-sim-18, 16558]
    erwartet = ok | ist = ok
V7: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:20:40.096 sb-sim-15 | Vorgang anlegen (Single, Budget 719.50 EUR) -> OK — Vorgang 17813
2026-10-13 11:20:40.109 sb-sim-15 | 1 SozialFall/Fälle erfassen -> OK — Single, KdU 465.00 EUR
2026-10-13 11:20:40.128 sb-sim-15 | Auszahlung anordnen (2026-02) -> OK — 479.50 EUR (ANGELEGT)
2026-10-13 11:20:40.142 sb-sim-15 | Doppelklick: gleiche Vorgangsnummer erneut -> OK — korrekt als bereits gebucht erkannt
2026-10-13 11:20:40.156 sb-sim-15 | Freigabe durch Kollegen (Vier-Augen) -> OK — freigegeben durch sb-sim-18

## TF 91 KOMBINATION SWEET-0091
status = VERIFIZIERT
hauptperson = Ismail, Norbert
bearbeiter = sb-sim-07
bg_id = 17814
dauer_ms = 96
ids = zahlungId=16568, vorgangsNr=efc68e7a-69ae-40b5-a704-951ffc42b3f9, mitarbeiter=sb-sim-07, bgId=17814, letzteZahlungId=16568, kollege=sb-sim-17

### rechenwege

block: Kombinationsfall 91: Paar mit 1 Kind(ern)
zahlungsmonat = 2026-11
vermoegen = 20650.00
schonvermoegen = 20500.00
budget_start = 740.40
p1_eingaben: rbs=2, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=370.00, kdu_betrieb=0.00, kdu_heizung=0.00, rente=0.00, erwerb=1370.00, abz_steuern=180.00, abz_sv=120.00, abz_vers=20.00, abz_erwerb=20.00
p1_regelbedarf=506.00
p1_mehrbedarf_g=0.00
p1_mehrbedarf_schwanger=0.00
p1_mehrbedarf_alleinerziehend=0.00
p1_mehrbedarf_warmwasser=0.00
p1_kdu=370.00
p1_gesamtbedarf=876.00
p1_freibetrag_erwerb=281.50
p1_absetzbetaetrag=340.00
p1_anrechenbar=748.50
p1_zahlbetrag=127.50
p2_eingaben: rbs=2, merkzeichen_g=true, schwanger=true, entbindungsmonat=2026-12, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=0.00, kdu_betrieb=135.00, kdu_heizung=160.00, rente=740.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p2_regelbedarf=506.00
p2_mehrbedarf_g=86.02
p2_mehrbedarf_schwanger=86.02
p2_mehrbedarf_alleinerziehend=0.00
p2_mehrbedarf_warmwasser=0.00
p2_kdu=295.00
p2_gesamtbedarf=973.04
p2_freibetrag_erwerb=0.00
p2_absetzbetaetrag=0.00
p2_anrechenbar=740.00
p2_zahlbetrag=233.04
p3_eingaben: rbs=6, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=true, kdu_miete=0.00, kdu_betrieb=0.00, kdu_heizung=0.00, rente=0.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p3_regelbedarf=357.00
p3_mehrbedarf_g=0.00
p3_mehrbedarf_schwanger=0.00
p3_mehrbedarf_alleinerziehend=0.00
p3_mehrbedarf_warmwasser=2.86
p3_kdu=0.00
p3_gesamtbedarf=359.86
p3_freibetrag_erwerb=0.00
p3_absetzbetaetrag=0.00
p3_anrechenbar=0.00
p3_zahlbetrag=359.86
bg_gesamtbedarf=2208.90
bg_anrechenbar=1488.50
bg_verwertbares_vermoegen=150.00
bg_zahlbetrag=570.40
budget_rest_erwartet=170.00

### verifikation

V1: OK | alle Personen als eigene SozialFall-Zeilen
    sql    = SELECT COUNT(*) FROM SOZIAL_FALL WHERE BG_ID = ?
    params = [17814]
    erwartet = 3 | ist = 3
V2: OK | finale Zahlung freigegeben (oder bereits übergeben)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [16568]
    erwartet = BEREIT|UEBERGEBEN | ist = UEBERGEBEN
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [16568]
    erwartet = 570.40 | ist = 570.4
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17814]
    erwartet = 170.00 | ist = 170
V5: OK | Freigabevermerk nennt den Kollegen (Vier-Augen)
    sql    = SELECT CASE WHEN FREIGEGEBEN_VON = ? THEN 'ok' ELSE 'abweichend' END FROM ZAHLUNG WHERE ID = ?
    params = [sb-sim-17, 16568]
    erwartet = ok | ist = ok
V6: OK | genau ein erteilter Bewilligungsbescheid
    sql    = SELECT COUNT(*) FROM BESCHEID WHERE BG_ID = ?
    params = [17814]
    erwartet = 1 | ist = 1
V7: OK | Vermögen nach Verbrauchsfortschreibung (§ 90)
    sql    = SELECT VERMOEGEN FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17814]
    erwartet = 20500.00 | ist = 20500
V8: OK | dokumentierter Vermögenseinsatz an der Zahlung
    sql    = SELECT VERMOEGENSEINSATZ FROM ZAHLUNG WHERE ID = ?
    params = [16568]
    erwartet = 150.00 | ist = 150
V9: OK | Wiedervorlagedatum exakt wie gesetzt
    sql    = SELECT COUNT(*) FROM BEDARFSGEMEINSCHAFT WHERE ID = ? AND WIEDERVORLAGE_AM = ?
    params = [17814, 2026-11-29]
    erwartet = 1 | ist = 1
V10: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:20:40.099 sb-sim-07 | Vorgang anlegen (Paar mit 1 Kind(ern), Budget 740.40 EUR, Vermögen 20650.00 EUR) -> OK — Vorgang 17814
2026-10-13 11:20:40.134 sb-sim-07 | 3 SozialFall/Fälle erfassen -> OK — Paar mit 1 Kind(ern), KdU 665.00 EUR
2026-10-13 11:20:40.154 sb-sim-07 | Bewilligungsbescheid erteilen (2026-10 .. 2026-12) -> OK — Bescheid BESCH-SWEET-0091-2026-1749 mit Monatsplan gespeichert
2026-10-13 11:20:40.174 sb-sim-07 | Auszahlung anordnen (2026-11) -> OK — 570.40 EUR (ANGELEGT)
2026-10-13 11:20:40.186 sb-sim-07 | Freigabe durch Kollegen (Vier-Augen) -> OK — freigegeben durch sb-sim-17

## TF 92 KOMBINATION SWEET-0092
status = VERIFIZIERT
hauptperson = Lindner, Ludwig
bearbeiter = sb-sim-09
bg_id = 17815
dauer_ms = 98
ids = zahlungId=16572, vorgangsNr=566a921a-c985-4cc1-8f4c-66da317336e7, mitarbeiter=sb-sim-09, bgId=17815, letzteZahlungId=16572, kollege=sb-sim-17

### rechenwege

block: Kombinationsfall 92: Paar mit 1 Kind(ern)
zahlungsmonat = 2026-07
vermoegen = 0.00
schonvermoegen = 20500.00
budget_start = 2569.34
p1_eingaben: rbs=2, merkzeichen_g=false, schwanger=true, entbindungsmonat=2026-12, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=460.00, kdu_betrieb=100.00, kdu_heizung=125.00, rente=0.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p1_regelbedarf=506.00
p1_mehrbedarf_g=0.00
p1_mehrbedarf_schwanger=86.02
p1_mehrbedarf_alleinerziehend=0.00
p1_mehrbedarf_warmwasser=0.00
p1_kdu=685.00
p1_gesamtbedarf=1277.02
p1_freibetrag_erwerb=0.00
p1_absetzbetaetrag=0.00
p1_anrechenbar=0.00
p1_zahlbetrag=1277.02
p2_eingaben: rbs=2, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=true, kdu_miete=0.00, kdu_betrieb=0.00, kdu_heizung=0.00, rente=0.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p2_regelbedarf=506.00
p2_mehrbedarf_g=0.00
p2_mehrbedarf_schwanger=0.00
p2_mehrbedarf_alleinerziehend=0.00
p2_mehrbedarf_warmwasser=11.64
p2_kdu=0.00
p2_gesamtbedarf=517.64
p2_freibetrag_erwerb=0.00
p2_absetzbetaetrag=0.00
p2_anrechenbar=0.00
p2_zahlbetrag=517.64
p3_eingaben: rbs=5, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=true, kdu_miete=0.00, kdu_betrieb=0.00, kdu_heizung=0.00, rente=0.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p3_regelbedarf=390.00
p3_mehrbedarf_g=0.00
p3_mehrbedarf_schwanger=0.00
p3_mehrbedarf_alleinerziehend=0.00
p3_mehrbedarf_warmwasser=4.68
p3_kdu=0.00
p3_gesamtbedarf=394.68
p3_freibetrag_erwerb=0.00
p3_absetzbetaetrag=0.00
p3_anrechenbar=0.00
p3_zahlbetrag=394.68
bg_gesamtbedarf=2189.34
bg_anrechenbar=0.00
bg_verwertbares_vermoegen=0.00
bg_zahlbetrag=2189.34
budget_rest_erwartet=380.00

### verifikation

V1: OK | alle Personen als eigene SozialFall-Zeilen
    sql    = SELECT COUNT(*) FROM SOZIAL_FALL WHERE BG_ID = ?
    params = [17815]
    erwartet = 3 | ist = 3
V2: OK | finale Zahlung freigegeben (oder bereits übergeben)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [16572]
    erwartet = BEREIT|UEBERGEBEN | ist = UEBERGEBEN
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [16572]
    erwartet = 2189.34 | ist = 2189.34
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17815]
    erwartet = 380.00 | ist = 380
V5: OK | Freigabevermerk nennt den Kollegen (Vier-Augen)
    sql    = SELECT CASE WHEN FREIGEGEBEN_VON = ? THEN 'ok' ELSE 'abweichend' END FROM ZAHLUNG WHERE ID = ?
    params = [sb-sim-17, 16572]
    erwartet = ok | ist = ok
V6: OK | genau ein erteilter Bewilligungsbescheid
    sql    = SELECT COUNT(*) FROM BESCHEID WHERE BG_ID = ?
    params = [17815]
    erwartet = 1 | ist = 1
V7: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:20:40.102 sb-sim-09 | Vorgang anlegen (Paar mit 1 Kind(ern), Budget 2569.34 EUR) -> OK — Vorgang 17815
2026-10-13 11:20:40.138 sb-sim-09 | 3 SozialFall/Fälle erfassen -> OK — Paar mit 1 Kind(ern), KdU 685.00 EUR
2026-10-13 11:20:40.158 sb-sim-09 | Bewilligungsbescheid erteilen (2026-04 .. 2026-07) -> OK — Bescheid BESCH-SWEET-0092-2026-1750 mit Monatsplan gespeichert
2026-10-13 11:20:40.176 sb-sim-09 | Auszahlung anordnen (2026-07) -> OK — 2189.34 EUR (ANGELEGT)
2026-10-13 11:20:40.191 sb-sim-09 | Freigabe durch Kollegen (Vier-Augen) -> OK — freigegeben durch sb-sim-17

## TF 93 KOMBINATION SWEET-0093
status = VERIFIZIERT
hauptperson = Brandt, Frank
bearbeiter = sb-sim-16
bg_id = 17816
dauer_ms = 55
ids = zahlungId=16563, vorgangsNr=59a6e4ad-2256-4d08-8708-1f2a51edf2c6, mitarbeiter=sb-sim-16, bgId=17816, letzteZahlungId=16563, kollege=sb-sim-08

### rechenwege

block: Kombinationsfall 93: Paar (2 × RBS 2)
zahlungsmonat = 2026-09
vermoegen = 0.00
schonvermoegen = 20000.00
budget_start = 2256.30
p1_eingaben: rbs=2, merkzeichen_g=true, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=true, kdu_miete=700.00, kdu_betrieb=50.00, kdu_heizung=75.00, rente=0.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p1_regelbedarf=506.00
p1_mehrbedarf_g=86.02
p1_mehrbedarf_schwanger=0.00
p1_mehrbedarf_alleinerziehend=0.00
p1_mehrbedarf_warmwasser=11.64
p1_kdu=825.00
p1_gesamtbedarf=1428.66
p1_freibetrag_erwerb=0.00
p1_absetzbetaetrag=0.00
p1_anrechenbar=0.00
p1_zahlbetrag=1428.66
p2_eingaben: rbs=2, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=true, kdu_miete=0.00, kdu_betrieb=0.00, kdu_heizung=0.00, rente=0.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p2_regelbedarf=506.00
p2_mehrbedarf_g=0.00
p2_mehrbedarf_schwanger=0.00
p2_mehrbedarf_alleinerziehend=0.00
p2_mehrbedarf_warmwasser=11.64
p2_kdu=0.00
p2_gesamtbedarf=517.64
p2_freibetrag_erwerb=0.00
p2_absetzbetaetrag=0.00
p2_anrechenbar=0.00
p2_zahlbetrag=517.64
bg_gesamtbedarf=1946.30
bg_anrechenbar=0.00
bg_verwertbares_vermoegen=0.00
bg_zahlbetrag=1946.30
budget_rest_erwartet=310.00

### verifikation

V1: OK | alle Personen als eigene SozialFall-Zeilen
    sql    = SELECT COUNT(*) FROM SOZIAL_FALL WHERE BG_ID = ?
    params = [17816]
    erwartet = 2 | ist = 2
V2: OK | finale Zahlung wirksam (Vier-Augen-Warteschlange je nach Kassenlauf)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [16563]
    erwartet = ANGELEGT|BEREIT|UEBERGEBEN | ist = ANGELEGT
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [16563]
    erwartet = 1946.30 | ist = 1946.3
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17816]
    erwartet = 310.00 | ist = 310
V5: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:20:40.104 sb-sim-16 | Vorgang anlegen (Paar (2 × RBS 2), Budget 2256.30 EUR) -> OK — Vorgang 17816
2026-10-13 11:20:40.128 sb-sim-16 | 2 SozialFall/Fälle erfassen -> OK — Paar (2 × RBS 2), KdU 825.00 EUR
2026-10-13 11:20:40.150 sb-sim-16 | Auszahlung anordnen (2026-09) -> OK — 1946.30 EUR (ANGELEGT)

## TF 94 KOMBINATION SWEET-0094
status = VERIFIZIERT
hauptperson = Roth, Anna
bearbeiter = sb-sim-03
bg_id = 17817
dauer_ms = 63
ids = zahlungId=16561, vorgangsNr=d97bd3e6-7154-484d-8f58-5f0547d73b8a, mitarbeiter=sb-sim-03, bgId=17817, letzteZahlungId=16561, kollege=sb-sim-04

### rechenwege

block: Kombinationsfall 94: Single
zahlungsmonat = 2026-10
vermoegen = 0.00
schonvermoegen = 10000.00
budget_start = 788.71
p1_eingaben: rbs=1, merkzeichen_g=false, schwanger=true, entbindungsmonat=2026-11, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=360.00, kdu_betrieb=40.00, kdu_heizung=100.00, rente=650.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p1_regelbedarf=563.00
p1_mehrbedarf_g=0.00
p1_mehrbedarf_schwanger=95.71
p1_mehrbedarf_alleinerziehend=0.00
p1_mehrbedarf_warmwasser=0.00
p1_kdu=500.00
p1_gesamtbedarf=1158.71
p1_freibetrag_erwerb=0.00
p1_absetzbetaetrag=0.00
p1_anrechenbar=650.00
p1_zahlbetrag=508.71
bg_gesamtbedarf=1158.71
bg_anrechenbar=650.00
bg_verwertbares_vermoegen=0.00
bg_zahlbetrag=508.71
budget_rest_erwartet=280.00

### verifikation

V1: OK | alle Personen als eigene SozialFall-Zeilen
    sql    = SELECT COUNT(*) FROM SOZIAL_FALL WHERE BG_ID = ?
    params = [17817]
    erwartet = 1 | ist = 1
V2: OK | finale Zahlung freigegeben (oder bereits übergeben)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [16561]
    erwartet = BEREIT|UEBERGEBEN | ist = UEBERGEBEN
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [16561]
    erwartet = 508.71 | ist = 508.71
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17817]
    erwartet = 280.00 | ist = 280
V5: OK | genau eine Zahlung trotz Wiederholung
    sql    = SELECT COUNT(*) FROM ZAHLUNG WHERE BG_ID = ?
    params = [17817]
    erwartet = 1 | ist = 1
V6: OK | Freigabevermerk nennt den Kollegen (Vier-Augen)
    sql    = SELECT CASE WHEN FREIGEGEBEN_VON = ? THEN 'ok' ELSE 'abweichend' END FROM ZAHLUNG WHERE ID = ?
    params = [sb-sim-04, 16561]
    erwartet = ok | ist = ok
V7: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:20:40.104 sb-sim-03 | Vorgang anlegen (Single, Budget 788.71 EUR) -> OK — Vorgang 17817
2026-10-13 11:20:40.118 sb-sim-03 | 1 SozialFall/Fälle erfassen -> OK — Single, KdU 500.00 EUR
2026-10-13 11:20:40.136 sb-sim-03 | Auszahlung anordnen (2026-10) -> OK — 508.71 EUR (ANGELEGT)
2026-10-13 11:20:40.150 sb-sim-03 | Doppelklick: gleiche Vorgangsnummer erneut -> OK — korrekt als bereits gebucht erkannt
2026-10-13 11:20:40.160 sb-sim-03 | Freigabe durch Kollegen (Vier-Augen) -> OK — freigegeben durch sb-sim-04

## TF 95 KOMBINATION SWEET-0095
status = VERIFIZIERT
hauptperson = Lindner, Anna
bearbeiter = sb-sim-20
bg_id = 17818
dauer_ms = 52
ids = zahlungId=16565, vorgangsNr=52bce1fc-baa4-48d8-97b2-5ab16d912e71, mitarbeiter=sb-sim-20, bgId=17818, letzteZahlungId=16565, kollege=sb-sim-15

### rechenwege

block: Kombinationsfall 95: Paar (2 × RBS 2)
zahlungsmonat = 2026-08
vermoegen = 20500.00
schonvermoegen = 20000.00
budget_start = 1543.64
p1_eingaben: rbs=2, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=570.00, kdu_betrieb=0.00, kdu_heizung=0.00, rente=0.00, erwerb=290.00, abz_steuern=0.00, abz_sv=110.00, abz_vers=15.00, abz_erwerb=90.00
p1_regelbedarf=506.00
p1_mehrbedarf_g=0.00
p1_mehrbedarf_schwanger=0.00
p1_mehrbedarf_alleinerziehend=0.00
p1_mehrbedarf_warmwasser=0.00
p1_kdu=570.00
p1_gesamtbedarf=1076.00
p1_freibetrag_erwerb=87.00
p1_absetzbetaetrag=215.00
p1_anrechenbar=0.00
p1_zahlbetrag=1076.00
p2_eingaben: rbs=2, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=true, kdu_miete=0.00, kdu_betrieb=60.00, kdu_heizung=110.00, rente=0.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p2_regelbedarf=506.00
p2_mehrbedarf_g=0.00
p2_mehrbedarf_schwanger=0.00
p2_mehrbedarf_alleinerziehend=0.00
p2_mehrbedarf_warmwasser=11.64
p2_kdu=170.00
p2_gesamtbedarf=687.64
p2_freibetrag_erwerb=0.00
p2_absetzbetaetrag=0.00
p2_anrechenbar=0.00
p2_zahlbetrag=687.64
bg_gesamtbedarf=1763.64
bg_anrechenbar=0.00
bg_verwertbares_vermoegen=500.00
bg_zahlbetrag=1263.64
budget_rest_erwartet=280.00

### verifikation

V1: OK | alle Personen als eigene SozialFall-Zeilen
    sql    = SELECT COUNT(*) FROM SOZIAL_FALL WHERE BG_ID = ?
    params = [17818]
    erwartet = 2 | ist = 2
V2: OK | finale Zahlung wirksam (Vier-Augen-Warteschlange je nach Kassenlauf)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [16565]
    erwartet = ANGELEGT|BEREIT|UEBERGEBEN | ist = ANGELEGT
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [16565]
    erwartet = 1263.64 | ist = 1263.64
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17818]
    erwartet = 280.00 | ist = 280
V5: OK | Vermögen nach Verbrauchsfortschreibung (§ 90)
    sql    = SELECT VERMOEGEN FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17818]
    erwartet = 20000.00 | ist = 20000
V6: OK | dokumentierter Vermögenseinsatz an der Zahlung
    sql    = SELECT VERMOEGENSEINSATZ FROM ZAHLUNG WHERE ID = ?
    params = [16565]
    erwartet = 500.00 | ist = 500
V7: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:20:40.112 sb-sim-20 | Vorgang anlegen (Paar (2 × RBS 2), Budget 1543.64 EUR, Vermögen 20500.00 EUR) -> OK — Vorgang 17818
2026-10-13 11:20:40.134 sb-sim-20 | 2 SozialFall/Fälle erfassen -> OK — Paar (2 × RBS 2), KdU 740.00 EUR
2026-10-13 11:20:40.154 sb-sim-20 | Auszahlung anordnen (2026-08) -> OK — 1263.64 EUR (ANGELEGT)

## TF 96 KOMBINATION SWEET-0096
status = VERIFIZIERT
hauptperson = Quest, Elke
bearbeiter = sb-sim-11
bg_id = 17819
dauer_ms = 82
ids = zahlungId=16569, vorgangsNr=e0e5da7f-7e20-4ada-87d0-a4277d9190b3, mitarbeiter=sb-sim-11, bgId=17819, letzteZahlungId=16569, kollege=sb-sim-17

### rechenwege

block: Kombinationsfall 96: Alleinerziehend mit 2 Kind(ern)
zahlungsmonat = 2026-03
vermoegen = 0.00
schonvermoegen = 11000.00
budget_start = 1110.87
p1_eingaben: rbs=1, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=1, kinder_u16=2, kinder_minderjaehrig=2, warmwasser=false, kdu_miete=370.00, kdu_betrieb=60.00, kdu_heizung=65.00, rente=0.00, erwerb=520.00, abz_steuern=90.00, abz_sv=280.00, abz_vers=75.00, abz_erwerb=60.00
p1_regelbedarf=563.00
p1_mehrbedarf_g=0.00
p1_mehrbedarf_schwanger=0.00
p1_mehrbedarf_alleinerziehend=202.68
p1_mehrbedarf_warmwasser=0.00
p1_kdu=495.00
p1_gesamtbedarf=1260.68
p1_freibetrag_erwerb=156.00
p1_absetzbetaetrag=505.00
p1_anrechenbar=0.00
p1_zahlbetrag=1260.68
p2_eingaben: rbs=4, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=0.00, kdu_betrieb=0.00, kdu_heizung=0.00, rente=0.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p2_regelbedarf=471.00
p2_mehrbedarf_g=0.00
p2_mehrbedarf_schwanger=0.00
p2_mehrbedarf_alleinerziehend=0.00
p2_mehrbedarf_warmwasser=0.00
p2_kdu=0.00
p2_gesamtbedarf=471.00
p2_freibetrag_erwerb=0.00
p2_absetzbetaetrag=0.00
p2_anrechenbar=0.00
p2_zahlbetrag=471.00
p3_eingaben: rbs=6, merkzeichen_g=true, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=0.00, kdu_betrieb=0.00, kdu_heizung=0.00, rente=560.00, erwerb=1360.00, abz_steuern=90.00, abz_sv=180.00, abz_vers=80.00, abz_erwerb=90.00
p3_regelbedarf=357.00
p3_mehrbedarf_g=60.69
p3_mehrbedarf_schwanger=0.00
p3_mehrbedarf_alleinerziehend=0.00
p3_mehrbedarf_warmwasser=0.00
p3_kdu=0.00
p3_gesamtbedarf=417.69
p3_freibetrag_erwerb=281.50
p3_absetzbetaetrag=440.00
p3_anrechenbar=1198.50
p3_zahlbetrag=-780.81
bg_gesamtbedarf=2149.37
bg_anrechenbar=1198.50
bg_verwertbares_vermoegen=0.00
bg_zahlbetrag=950.87
budget_rest_erwartet=160.00

### verifikation

V1: OK | alle Personen als eigene SozialFall-Zeilen
    sql    = SELECT COUNT(*) FROM SOZIAL_FALL WHERE BG_ID = ?
    params = [17819]
    erwartet = 3 | ist = 3
V2: OK | finale Zahlung wirksam (Vier-Augen-Warteschlange je nach Kassenlauf)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [16569]
    erwartet = ANGELEGT|BEREIT|UEBERGEBEN | ist = ANGELEGT
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [16569]
    erwartet = 950.87 | ist = 950.87
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17819]
    erwartet = 160.00 | ist = 160
V5: OK | genau eine Zahlung trotz Wiederholung
    sql    = SELECT COUNT(*) FROM ZAHLUNG WHERE BG_ID = ?
    params = [17819]
    erwartet = 1 | ist = 1
V6: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:20:40.117 sb-sim-11 | Vorgang anlegen (Alleinerziehend mit 2 Kind(ern), Budget 1110.87 EUR) -> OK — Vorgang 17819
2026-10-13 11:20:40.156 sb-sim-11 | 3 SozialFall/Fälle erfassen -> OK — Alleinerziehend mit 2 Kind(ern), KdU 495.00 EUR
2026-10-13 11:20:40.173 sb-sim-11 | Auszahlung anordnen (2026-03) -> OK — 950.87 EUR (ANGELEGT)
2026-10-13 11:20:40.186 sb-sim-11 | Doppelklick: gleiche Vorgangsnummer erneut -> OK — korrekt als bereits gebucht erkannt

## TF 97 KOMBINATION SWEET-0097
status = VERIFIZIERT
hauptperson = Vogt, Paul
bearbeiter = sb-sim-05
bg_id = 17820
dauer_ms = 84
ids = zahlungId=16567, vorgangsNr=68ed4cf3-aa10-4fb3-a9e5-998d5830ee3b, mitarbeiter=sb-sim-05, bgId=17820, letzteZahlungId=16567, kollege=sb-sim-01

### rechenwege

block: Kombinationsfall 97: Alleinerziehend mit 2 Kind(ern)
zahlungsmonat = 2026-05
vermoegen = 0.00
schonvermoegen = 11000.00
budget_start = 1906.20
p1_eingaben: rbs=1, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=2, kinder_minderjaehrig=2, warmwasser=true, kdu_miete=610.00, kdu_betrieb=75.00, kdu_heizung=115.00, rente=0.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p1_regelbedarf=563.00
p1_mehrbedarf_g=0.00
p1_mehrbedarf_schwanger=0.00
p1_mehrbedarf_alleinerziehend=202.68
p1_mehrbedarf_warmwasser=12.95
p1_kdu=800.00
p1_gesamtbedarf=1578.63
p1_freibetrag_erwerb=0.00
p1_absetzbetaetrag=0.00
p1_anrechenbar=0.00
p1_zahlbetrag=1578.63
p2_eingaben: rbs=4, merkzeichen_g=true, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=0.00, kdu_betrieb=0.00, kdu_heizung=0.00, rente=0.00, erwerb=1340.00, abz_steuern=120.00, abz_sv=70.00, abz_vers=5.00, abz_erwerb=40.00
p2_regelbedarf=471.00
p2_mehrbedarf_g=80.07
p2_mehrbedarf_schwanger=0.00
p2_mehrbedarf_alleinerziehend=0.00
p2_mehrbedarf_warmwasser=0.00
p2_kdu=0.00
p2_gesamtbedarf=551.07
p2_freibetrag_erwerb=281.50
p2_absetzbetaetrag=235.00
p2_anrechenbar=823.50
p2_zahlbetrag=-272.43
p3_eingaben: rbs=5, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=0.00, kdu_betrieb=0.00, kdu_heizung=0.00, rente=0.00, erwerb=150.00, abz_steuern=160.00, abz_sv=230.00, abz_vers=80.00, abz_erwerb=10.00
p3_regelbedarf=390.00
p3_mehrbedarf_g=0.00
p3_mehrbedarf_schwanger=0.00
p3_mehrbedarf_alleinerziehend=0.00
p3_mehrbedarf_warmwasser=0.00
p3_kdu=0.00
p3_gesamtbedarf=390.00
p3_freibetrag_erwerb=45.00
p3_absetzbetaetrag=480.00
p3_anrechenbar=0.00
p3_zahlbetrag=390.00
bg_gesamtbedarf=2519.70
bg_anrechenbar=823.50
bg_verwertbares_vermoegen=0.00
bg_zahlbetrag=1696.20
budget_rest_erwartet=210.00

### verifikation

V1: OK | alle Personen als eigene SozialFall-Zeilen
    sql    = SELECT COUNT(*) FROM SOZIAL_FALL WHERE BG_ID = ?
    params = [17820]
    erwartet = 3 | ist = 3
V2: OK | finale Zahlung freigegeben (oder bereits übergeben)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [16567]
    erwartet = BEREIT|UEBERGEBEN | ist = UEBERGEBEN
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [16567]
    erwartet = 1696.20 | ist = 1696.2
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17820]
    erwartet = 210.00 | ist = 210
V5: OK | genau eine Zahlung trotz Wiederholung
    sql    = SELECT COUNT(*) FROM ZAHLUNG WHERE BG_ID = ?
    params = [17820]
    erwartet = 1 | ist = 1
V6: OK | Freigabevermerk nennt den Kollegen (Vier-Augen)
    sql    = SELECT CASE WHEN FREIGEGEBEN_VON = ? THEN 'ok' ELSE 'abweichend' END FROM ZAHLUNG WHERE ID = ?
    params = [sb-sim-01, 16567]
    erwartet = ok | ist = ok
V7: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:20:40.120 sb-sim-05 | Vorgang anlegen (Alleinerziehend mit 2 Kind(ern), Budget 1906.20 EUR) -> OK — Vorgang 17820
2026-10-13 11:20:40.154 sb-sim-05 | 3 SozialFall/Fälle erfassen -> OK — Alleinerziehend mit 2 Kind(ern), KdU 800.00 EUR
2026-10-13 11:20:40.172 sb-sim-05 | Auszahlung anordnen (2026-05) -> OK — 1696.20 EUR (ANGELEGT)
2026-10-13 11:20:40.182 sb-sim-05 | Doppelklick: gleiche Vorgangsnummer erneut -> OK — korrekt als bereits gebucht erkannt
2026-10-13 11:20:40.195 sb-sim-05 | Freigabe durch Kollegen (Vier-Augen) -> OK — freigegeben durch sb-sim-01

## TF 98 KOMBINATION SWEET-0098
status = VERIFIZIERT
hauptperson = Jung, Clara
bearbeiter = sb-sim-02
bg_id = 17821
dauer_ms = 79
ids = zahlungId=16576, vorgangsNr=4578a14b-69d3-42b3-a1f9-e5482318a619, mitarbeiter=sb-sim-02, bgId=17821, letzteZahlungId=16576, kollege=sb-sim-05

### rechenwege

block: Kombinationsfall 98: Paar mit 2 Kind(ern) (Schwangerschafts-Mehrbedarf entfällt im Zahlungsmonat)
zahlungsmonat = 2026-08
vermoegen = 0.00
schonvermoegen = 21000.00
budget_start = 1624.16
p1_eingaben: rbs=2, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=460.00, kdu_betrieb=95.00, kdu_heizung=145.00, rente=740.00, erwerb=1080.00, abz_steuern=190.00, abz_sv=240.00, abz_vers=15.00, abz_erwerb=90.00
p1_regelbedarf=506.00
p1_mehrbedarf_g=0.00
p1_mehrbedarf_schwanger=0.00
p1_mehrbedarf_alleinerziehend=0.00
p1_mehrbedarf_warmwasser=0.00
p1_kdu=700.00
p1_gesamtbedarf=1206.00
p1_freibetrag_erwerb=281.50
p1_absetzbetaetrag=535.00
p1_anrechenbar=1003.50
p1_zahlbetrag=202.50
p2_eingaben: rbs=2, merkzeichen_g=false, schwanger=false, entbindungsmonat=2026-05, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=0.00, kdu_betrieb=0.00, kdu_heizung=0.00, rente=120.00, erwerb=840.00, abz_steuern=200.00, abz_sv=310.00, abz_vers=55.00, abz_erwerb=50.00
p2_regelbedarf=506.00
p2_mehrbedarf_g=0.00
p2_mehrbedarf_schwanger=0.00
p2_mehrbedarf_alleinerziehend=0.00
p2_mehrbedarf_warmwasser=0.00
p2_kdu=0.00
p2_gesamtbedarf=506.00
p2_freibetrag_erwerb=252.00
p2_absetzbetaetrag=615.00
p2_anrechenbar=93.00
p2_zahlbetrag=413.00
p3_eingaben: rbs=4, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=0.00, kdu_betrieb=0.00, kdu_heizung=0.00, rente=170.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p3_regelbedarf=471.00
p3_mehrbedarf_g=0.00
p3_mehrbedarf_schwanger=0.00
p3_mehrbedarf_alleinerziehend=0.00
p3_mehrbedarf_warmwasser=0.00
p3_kdu=0.00
p3_gesamtbedarf=471.00
p3_freibetrag_erwerb=0.00
p3_absetzbetaetrag=0.00
p3_anrechenbar=170.00
p3_zahlbetrag=301.00
p4_eingaben: rbs=4, merkzeichen_g=true, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=true, kdu_miete=0.00, kdu_betrieb=0.00, kdu_heizung=0.00, rente=0.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p4_regelbedarf=471.00
p4_mehrbedarf_g=80.07
p4_mehrbedarf_schwanger=0.00
p4_mehrbedarf_alleinerziehend=0.00
p4_mehrbedarf_warmwasser=6.59
p4_kdu=0.00
p4_gesamtbedarf=557.66
p4_freibetrag_erwerb=0.00
p4_absetzbetaetrag=0.00
p4_anrechenbar=0.00
p4_zahlbetrag=557.66
bg_gesamtbedarf=2740.66
bg_anrechenbar=1266.50
bg_verwertbares_vermoegen=0.00
bg_zahlbetrag=1474.16
budget_rest_erwartet=150.00

### verifikation

V1: OK | alle Personen als eigene SozialFall-Zeilen
    sql    = SELECT COUNT(*) FROM SOZIAL_FALL WHERE BG_ID = ?
    params = [17821]
    erwartet = 4 | ist = 4
V2: OK | finale Zahlung wirksam (Vier-Augen-Warteschlange je nach Kassenlauf)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [16576]
    erwartet = ANGELEGT|BEREIT|UEBERGEBEN | ist = ANGELEGT
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [16576]
    erwartet = 1474.16 | ist = 1474.16
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17821]
    erwartet = 150.00 | ist = 150
V5: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:20:40.125 sb-sim-02 | Vorgang anlegen (Paar mit 2 Kind(ern), Budget 1624.16 EUR) -> OK — Vorgang 17821
2026-10-13 11:20:40.175 sb-sim-02 | 4 SozialFall/Fälle erfassen -> OK — Paar mit 2 Kind(ern), KdU 700.00 EUR
2026-10-13 11:20:40.196 sb-sim-02 | Auszahlung anordnen (2026-08) -> OK — 1474.16 EUR (ANGELEGT)

## TF 99 KOMBINATION SWEET-0099
status = VERIFIZIERT
hauptperson = Neumann, Ingrid
bearbeiter = sb-sim-06
bg_id = 17822
dauer_ms = 85
ids = letzteZahlungId=16578, kollege=sb-sim-18, bgId=17822, mitarbeiter=sb-sim-06, zahlungId=16566, fallId=26946, vorgangsNr=4ca5ae4a-86bb-46dc-b70a-408bc052acfa

### rechenwege

block: Kombinationsfall 99: Single
zahlungsmonat = 2026-02
vermoegen = 0.00
schonvermoegen = 10000.00
budget_start = 1573.00
p1_eingaben: rbs=1, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=610.00, kdu_betrieb=25.00, kdu_heizung=85.00, rente=0.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p1_regelbedarf=563.00
p1_mehrbedarf_g=0.00
p1_mehrbedarf_schwanger=0.00
p1_mehrbedarf_alleinerziehend=0.00
p1_mehrbedarf_warmwasser=0.00
p1_kdu=720.00
p1_gesamtbedarf=1283.00
p1_freibetrag_erwerb=0.00
p1_absetzbetaetrag=0.00
p1_anrechenbar=0.00
p1_zahlbetrag=1283.00
bg_gesamtbedarf=1283.00
bg_anrechenbar=0.00
bg_verwertbares_vermoegen=0.00
bg_zahlbetrag=1283.00
budget_rest_erwartet=290.00

block: Neuanordnung nach Mietekorrektur (Miete 610.00 → 730.00)
zahlungsmonat = 2026-02
vermoegen = 0.00
schonvermoegen = 10000.00
budget_start = 1573.00
p1_eingaben: rbs=1, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=730.00, kdu_betrieb=25.00, kdu_heizung=85.00, rente=0.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p1_regelbedarf=563.00
p1_mehrbedarf_g=0.00
p1_mehrbedarf_schwanger=0.00
p1_mehrbedarf_alleinerziehend=0.00
p1_mehrbedarf_warmwasser=0.00
p1_kdu=840.00
p1_gesamtbedarf=1403.00
p1_freibetrag_erwerb=0.00
p1_absetzbetaetrag=0.00
p1_anrechenbar=0.00
p1_zahlbetrag=1403.00
bg_gesamtbedarf=1403.00
bg_anrechenbar=0.00
bg_verwertbares_vermoegen=0.00
bg_zahlbetrag=1403.00
budget_rest_erwartet=170.00

### verifikation

V1: OK | alle Personen als eigene SozialFall-Zeilen
    sql    = SELECT COUNT(*) FROM SOZIAL_FALL WHERE BG_ID = ?
    params = [17822]
    erwartet = 1 | ist = 1
V2: OK | finale Zahlung wirksam (Vier-Augen-Warteschlange je nach Kassenlauf)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [16578]
    erwartet = ANGELEGT|BEREIT|UEBERGEBEN | ist = ANGELEGT
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [16578]
    erwartet = 1403.00 | ist = 1403
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17822]
    erwartet = 170.00 | ist = 170
V5: OK | zwei Zahlungen (stornierte + korrigierte)
    sql    = SELECT COUNT(*) FROM ZAHLUNG WHERE BG_ID = ?
    params = [17822]
    erwartet = 2 | ist = 2
V6: OK | erste Zahlung STORNIERT
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [16566]
    erwartet = STORNIERT | ist = STORNIERT
V7: OK | Fall in Version 2 (optimistisches Locking)
    sql    = SELECT VERSION FROM SOZIAL_FALL WHERE ID = ?
    params = [26946]
    erwartet = 2 | ist = 2
V8: OK | Wiedervorlagedatum exakt wie gesetzt
    sql    = SELECT COUNT(*) FROM BEDARFSGEMEINSCHAFT WHERE ID = ? AND WIEDERVORLAGE_AM = ?
    params = [17822, 2027-03-28]
    erwartet = 1 | ist = 1
V9: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:20:40.125 sb-sim-06 | Vorgang anlegen (Single, Budget 1573.00 EUR) -> OK — Vorgang 17822
2026-10-13 11:20:40.138 sb-sim-06 | 1 SozialFall/Fälle erfassen -> OK — Single, KdU 720.00 EUR
2026-10-13 11:20:40.156 sb-sim-06 | Auszahlung anordnen (2026-02) -> OK — 1283.00 EUR (ANGELEGT)
2026-10-13 11:20:40.166 sb-sim-06 | Anordnung sofort stornieren (Status ANGELEGT) -> OK — storniert — Monat wieder frei
2026-10-13 11:20:40.186 sb-sim-06 | Miete auf 730.00 korrigieren -> OK — Fall 26946 in Version 2
2026-10-13 11:20:40.204 sb-sim-06 | Korrigierte Auszahlung für denselben Monat -> OK — 1403.00 EUR

## TF 101 KOMBINATION SWEET-0101
status = VERIFIZIERT
hauptperson = Otto, Karin
bearbeiter = sb-sim-01
bg_id = 17823
dauer_ms = 83
ids = zahlungId=16575, vorgangsNr=33d4bc08-667e-4582-b0ea-2251645187fd, mitarbeiter=sb-sim-01, bgId=17823, letzteZahlungId=16575, kollege=sb-sim-02

### rechenwege

block: Kombinationsfall 101: Paar mit 1 Kind(ern)
zahlungsmonat = 2026-04
vermoegen = 0.00
schonvermoegen = 20500.00
budget_start = 1051.33
p1_eingaben: rbs=2, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=true, kdu_miete=650.00, kdu_betrieb=60.00, kdu_heizung=160.00, rente=870.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p1_regelbedarf=506.00
p1_mehrbedarf_g=0.00
p1_mehrbedarf_schwanger=0.00
p1_mehrbedarf_alleinerziehend=0.00
p1_mehrbedarf_warmwasser=11.64
p1_kdu=870.00
p1_gesamtbedarf=1387.64
p1_freibetrag_erwerb=0.00
p1_absetzbetaetrag=0.00
p1_anrechenbar=870.00
p1_zahlbetrag=517.64
p2_eingaben: rbs=2, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=0.00, kdu_betrieb=0.00, kdu_heizung=0.00, rente=0.00, erwerb=700.00, abz_steuern=110.00, abz_sv=190.00, abz_vers=80.00, abz_erwerb=100.00
p2_regelbedarf=506.00
p2_mehrbedarf_g=0.00
p2_mehrbedarf_schwanger=0.00
p2_mehrbedarf_alleinerziehend=0.00
p2_mehrbedarf_warmwasser=0.00
p2_kdu=0.00
p2_gesamtbedarf=506.00
p2_freibetrag_erwerb=210.00
p2_absetzbetaetrag=480.00
p2_anrechenbar=10.00
p2_zahlbetrag=496.00
p3_eingaben: rbs=6, merkzeichen_g=true, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=0.00, kdu_betrieb=0.00, kdu_heizung=0.00, rente=630.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p3_regelbedarf=357.00
p3_mehrbedarf_g=60.69
p3_mehrbedarf_schwanger=0.00
p3_mehrbedarf_alleinerziehend=0.00
p3_mehrbedarf_warmwasser=0.00
p3_kdu=0.00
p3_gesamtbedarf=417.69
p3_freibetrag_erwerb=0.00
p3_absetzbetaetrag=0.00
p3_anrechenbar=630.00
p3_zahlbetrag=-212.31
bg_gesamtbedarf=2311.33
bg_anrechenbar=1510.00
bg_verwertbares_vermoegen=0.00
bg_zahlbetrag=801.33
budget_rest_erwartet=250.00

### verifikation

V1: OK | alle Personen als eigene SozialFall-Zeilen
    sql    = SELECT COUNT(*) FROM SOZIAL_FALL WHERE BG_ID = ?
    params = [17823]
    erwartet = 3 | ist = 3
V2: OK | finale Zahlung freigegeben (oder bereits übergeben)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [16575]
    erwartet = BEREIT|UEBERGEBEN | ist = UEBERGEBEN
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [16575]
    erwartet = 801.33 | ist = 801.33
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17823]
    erwartet = 250.00 | ist = 250
V5: OK | Freigabevermerk nennt den Kollegen (Vier-Augen)
    sql    = SELECT CASE WHEN FREIGEGEBEN_VON = ? THEN 'ok' ELSE 'abweichend' END FROM ZAHLUNG WHERE ID = ?
    params = [sb-sim-02, 16575]
    erwartet = ok | ist = ok
V6: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:20:40.133 sb-sim-01 | Vorgang anlegen (Paar mit 1 Kind(ern), Budget 1051.33 EUR) -> OK — Vorgang 17823
2026-10-13 11:20:40.172 sb-sim-01 | 3 SozialFall/Fälle erfassen -> OK — Paar mit 1 Kind(ern), KdU 870.00 EUR
2026-10-13 11:20:40.194 sb-sim-01 | Auszahlung anordnen (2026-04) -> OK — 801.33 EUR (ANGELEGT)
2026-10-13 11:20:40.206 sb-sim-01 | Freigabe durch Kollegen (Vier-Augen) -> OK — freigegeben durch sb-sim-02

## TF 102 KOMBINATION SWEET-0102
status = VERIFIZIERT
hauptperson = Quest, Elke
bearbeiter = sb-sim-10
bg_id = 17824
dauer_ms = 77
ids = zahlungId=16573, vorgangsNr=bb2f1be1-bd7a-4bff-b9df-4289e6e2faac, mitarbeiter=sb-sim-10, bgId=17824, letzteZahlungId=16573, kollege=sb-sim-04

### rechenwege

block: Kombinationsfall 102: Alleinerziehend mit 2 Kind(ern)
zahlungsmonat = 2026-04
vermoegen = 11350.00
schonvermoegen = 11000.00
budget_start = 454.72
p1_eingaben: rbs=1, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=2, kinder_minderjaehrig=2, warmwasser=false, kdu_miete=500.00, kdu_betrieb=80.00, kdu_heizung=30.00, rente=180.00, erwerb=560.00, abz_steuern=40.00, abz_sv=60.00, abz_vers=5.00, abz_erwerb=0.00
p1_regelbedarf=563.00
p1_mehrbedarf_g=0.00
p1_mehrbedarf_schwanger=0.00
p1_mehrbedarf_alleinerziehend=202.68
p1_mehrbedarf_warmwasser=0.00
p1_kdu=610.00
p1_gesamtbedarf=1375.68
p1_freibetrag_erwerb=168.00
p1_absetzbetaetrag=105.00
p1_anrechenbar=467.00
p1_zahlbetrag=908.68
p2_eingaben: rbs=6, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=true, kdu_miete=0.00, kdu_betrieb=0.00, kdu_heizung=0.00, rente=320.00, erwerb=1320.00, abz_steuern=170.00, abz_sv=140.00, abz_vers=70.00, abz_erwerb=100.00
p2_regelbedarf=357.00
p2_mehrbedarf_g=0.00
p2_mehrbedarf_schwanger=0.00
p2_mehrbedarf_alleinerziehend=0.00
p2_mehrbedarf_warmwasser=2.86
p2_kdu=0.00
p2_gesamtbedarf=359.86
p2_freibetrag_erwerb=281.50
p2_absetzbetaetrag=480.00
p2_anrechenbar=878.50
p2_zahlbetrag=-518.64
p3_eingaben: rbs=5, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=true, kdu_miete=0.00, kdu_betrieb=0.00, kdu_heizung=0.00, rente=240.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p3_regelbedarf=390.00
p3_mehrbedarf_g=0.00
p3_mehrbedarf_schwanger=0.00
p3_mehrbedarf_alleinerziehend=0.00
p3_mehrbedarf_warmwasser=4.68
p3_kdu=0.00
p3_gesamtbedarf=394.68
p3_freibetrag_erwerb=0.00
p3_absetzbetaetrag=0.00
p3_anrechenbar=240.00
p3_zahlbetrag=154.68
bg_gesamtbedarf=2130.22
bg_anrechenbar=1585.50
bg_verwertbares_vermoegen=350.00
bg_zahlbetrag=194.72
budget_rest_erwartet=260.00

### verifikation

V1: OK | alle Personen als eigene SozialFall-Zeilen
    sql    = SELECT COUNT(*) FROM SOZIAL_FALL WHERE BG_ID = ?
    params = [17824]
    erwartet = 3 | ist = 3
V2: OK | finale Zahlung freigegeben (oder bereits übergeben)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [16573]
    erwartet = BEREIT|UEBERGEBEN | ist = UEBERGEBEN
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [16573]
    erwartet = 194.72 | ist = 194.72
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17824]
    erwartet = 260.00 | ist = 260
V5: OK | Freigabevermerk nennt den Kollegen (Vier-Augen)
    sql    = SELECT CASE WHEN FREIGEGEBEN_VON = ? THEN 'ok' ELSE 'abweichend' END FROM ZAHLUNG WHERE ID = ?
    params = [sb-sim-04, 16573]
    erwartet = ok | ist = ok
V6: OK | Vermögen nach Verbrauchsfortschreibung (§ 90)
    sql    = SELECT VERMOEGEN FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17824]
    erwartet = 11000.00 | ist = 11000
V7: OK | dokumentierter Vermögenseinsatz an der Zahlung
    sql    = SELECT VERMOEGENSEINSATZ FROM ZAHLUNG WHERE ID = ?
    params = [16573]
    erwartet = 350.00 | ist = 350
V8: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:20:40.138 sb-sim-10 | Vorgang anlegen (Alleinerziehend mit 2 Kind(ern), Budget 454.72 EUR, Vermögen 11350.00 EUR) -> OK — Vorgang 17824
2026-10-13 11:20:40.172 sb-sim-10 | 3 SozialFall/Fälle erfassen -> OK — Alleinerziehend mit 2 Kind(ern), KdU 610.00 EUR
2026-10-13 11:20:40.195 sb-sim-10 | Auszahlung anordnen (2026-04) -> OK — 194.72 EUR (ANGELEGT)
2026-10-13 11:20:40.206 sb-sim-10 | Freigabe durch Kollegen (Vier-Augen) -> OK — freigegeben durch sb-sim-04

## TF 103 KOMBINATION SWEET-0103
status = VERIFIZIERT
hauptperson = Gross, Ingrid
bearbeiter = sb-sim-14
bg_id = 17825
dauer_ms = 91
ids = zahlungId=16577, vorgangsNr=035d8864-651e-4d66-9bf5-c3cd199ad36f, mitarbeiter=sb-sim-14, bgId=17825, letzteZahlungId=16577, kollege=sb-sim-13

### rechenwege

block: Kombinationsfall 103: Alleinerziehend mit 2 Kind(ern) (Schwangerschafts-Mehrbedarf entfällt im Zahlungsmonat)
zahlungsmonat = 2026-04
vermoegen = 11000.00
schonvermoegen = 11000.00
budget_start = 549.22
p1_eingaben: rbs=1, merkzeichen_g=false, schwanger=false, entbindungsmonat=2026-02, kinder_u7=2, kinder_u16=2, kinder_minderjaehrig=2, warmwasser=true, kdu_miete=330.00, kdu_betrieb=140.00, kdu_heizung=120.00, rente=940.00, erwerb=640.00, abz_steuern=150.00, abz_sv=300.00, abz_vers=45.00, abz_erwerb=80.00
p1_regelbedarf=563.00
p1_mehrbedarf_g=0.00
p1_mehrbedarf_schwanger=0.00
p1_mehrbedarf_alleinerziehend=202.68
p1_mehrbedarf_warmwasser=12.95
p1_kdu=590.00
p1_gesamtbedarf=1368.63
p1_freibetrag_erwerb=192.00
p1_absetzbetaetrag=575.00
p1_anrechenbar=813.00
p1_zahlbetrag=555.63
p2_eingaben: rbs=4, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=0.00, kdu_betrieb=0.00, kdu_heizung=0.00, rente=1080.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p2_regelbedarf=471.00
p2_mehrbedarf_g=0.00
p2_mehrbedarf_schwanger=0.00
p2_mehrbedarf_alleinerziehend=0.00
p2_mehrbedarf_warmwasser=0.00
p2_kdu=0.00
p2_gesamtbedarf=471.00
p2_freibetrag_erwerb=0.00
p2_absetzbetaetrag=0.00
p2_anrechenbar=1080.00
p2_zahlbetrag=-609.00
p3_eingaben: rbs=4, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=true, kdu_miete=0.00, kdu_betrieb=0.00, kdu_heizung=0.00, rente=0.00, erwerb=650.00, abz_steuern=40.00, abz_sv=260.00, abz_vers=70.00, abz_erwerb=10.00
p3_regelbedarf=471.00
p3_mehrbedarf_g=0.00
p3_mehrbedarf_schwanger=0.00
p3_mehrbedarf_alleinerziehend=0.00
p3_mehrbedarf_warmwasser=6.59
p3_kdu=0.00
p3_gesamtbedarf=477.59
p3_freibetrag_erwerb=195.00
p3_absetzbetaetrag=380.00
p3_anrechenbar=75.00
p3_zahlbetrag=402.59
bg_gesamtbedarf=2317.22
bg_anrechenbar=1968.00
bg_verwertbares_vermoegen=0.00
bg_zahlbetrag=349.22
budget_rest_erwartet=200.00

### verifikation

V1: OK | alle Personen als eigene SozialFall-Zeilen
    sql    = SELECT COUNT(*) FROM SOZIAL_FALL WHERE BG_ID = ?
    params = [17825]
    erwartet = 3 | ist = 3
V2: OK | finale Zahlung freigegeben (oder bereits übergeben)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [16577]
    erwartet = BEREIT|UEBERGEBEN | ist = UEBERGEBEN
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [16577]
    erwartet = 349.22 | ist = 349.22
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17825]
    erwartet = 200.00 | ist = 200
V5: OK | genau eine Zahlung trotz Wiederholung
    sql    = SELECT COUNT(*) FROM ZAHLUNG WHERE BG_ID = ?
    params = [17825]
    erwartet = 1 | ist = 1
V6: OK | Freigabevermerk nennt den Kollegen (Vier-Augen)
    sql    = SELECT CASE WHEN FREIGEGEBEN_VON = ? THEN 'ok' ELSE 'abweichend' END FROM ZAHLUNG WHERE ID = ?
    params = [sb-sim-13, 16577]
    erwartet = ok | ist = ok
V7: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:20:40.138 sb-sim-14 | Vorgang anlegen (Alleinerziehend mit 2 Kind(ern), Budget 549.22 EUR, Vermögen 11000.00 EUR) -> OK — Vorgang 17825
2026-10-13 11:20:40.176 sb-sim-14 | 3 SozialFall/Fälle erfassen -> OK — Alleinerziehend mit 2 Kind(ern), KdU 590.00 EUR
2026-10-13 11:20:40.196 sb-sim-14 | Auszahlung anordnen (2026-04) -> OK — 349.22 EUR (ANGELEGT)
2026-10-13 11:20:40.209 sb-sim-14 | Doppelklick: gleiche Vorgangsnummer erneut -> OK — korrekt als bereits gebucht erkannt
2026-10-13 11:20:40.220 sb-sim-14 | Freigabe durch Kollegen (Vier-Augen) -> OK — freigegeben durch sb-sim-13

## TF 104 KOMBINATION SWEET-0104
status = VERIFIZIERT
hauptperson = Petersen, Ludwig
bearbeiter = sb-sim-17
bg_id = 17826
dauer_ms = 43
ids = zahlungId=16570, vorgangsNr=383cc501-3450-41b0-8728-57050128849b, mitarbeiter=sb-sim-17, bgId=17826, letzteZahlungId=16570, kollege=sb-sim-11

### rechenwege

block: Kombinationsfall 104: Single
zahlungsmonat = 2026-04
vermoegen = 0.00
schonvermoegen = 10000.00
budget_start = 1586.66
p1_eingaben: rbs=1, merkzeichen_g=true, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=true, kdu_miete=570.00, kdu_betrieb=65.00, kdu_heizung=100.00, rente=180.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p1_regelbedarf=563.00
p1_mehrbedarf_g=95.71
p1_mehrbedarf_schwanger=0.00
p1_mehrbedarf_alleinerziehend=0.00
p1_mehrbedarf_warmwasser=12.95
p1_kdu=735.00
p1_gesamtbedarf=1406.66
p1_freibetrag_erwerb=0.00
p1_absetzbetaetrag=0.00
p1_anrechenbar=180.00
p1_zahlbetrag=1226.66
bg_gesamtbedarf=1406.66
bg_anrechenbar=180.00
bg_verwertbares_vermoegen=0.00
bg_zahlbetrag=1226.66
budget_rest_erwartet=360.00

### verifikation

V1: OK | alle Personen als eigene SozialFall-Zeilen
    sql    = SELECT COUNT(*) FROM SOZIAL_FALL WHERE BG_ID = ?
    params = [17826]
    erwartet = 1 | ist = 1
V2: OK | finale Zahlung wirksam (Vier-Augen-Warteschlange je nach Kassenlauf)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [16570]
    erwartet = ANGELEGT|BEREIT|UEBERGEBEN | ist = ANGELEGT
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [16570]
    erwartet = 1226.66 | ist = 1226.66
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17826]
    erwartet = 360.00 | ist = 360
V5: OK | Wiedervorlagedatum exakt wie gesetzt
    sql    = SELECT COUNT(*) FROM BEDARFSGEMEINSCHAFT WHERE ID = ? AND WIEDERVORLAGE_AM = ?
    params = [17826, 2027-03-16]
    erwartet = 1 | ist = 1
V6: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:20:40.147 sb-sim-17 | Vorgang anlegen (Single, Budget 1586.66 EUR) -> OK — Vorgang 17826
2026-10-13 11:20:40.158 sb-sim-17 | 1 SozialFall/Fälle erfassen -> OK — Single, KdU 735.00 EUR
2026-10-13 11:20:40.175 sb-sim-17 | Auszahlung anordnen (2026-04) -> OK — 1226.66 EUR (ANGELEGT)

## TF 105 KOMBINATION SWEET-0105
status = VERIFIZIERT
hauptperson = Meyer, Clara
bearbeiter = sb-sim-18
bg_id = 17827
dauer_ms = 77
ids = zahlungId=16581, vorgangsNr=a948ddab-1359-4486-b306-61814189661e, mitarbeiter=sb-sim-18, bgId=17827, letzteZahlungId=16581, kollege=sb-sim-11

### rechenwege

block: Kombinationsfall 105: Paar (2 × RBS 2) (Schwangerschafts-Mehrbedarf entfällt im Zahlungsmonat)
zahlungsmonat = 2026-08
vermoegen = 20200.00
schonvermoegen = 20000.00
budget_start = 1497.64
p1_eingaben: rbs=2, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=true, kdu_miete=550.00, kdu_betrieb=0.00, kdu_heizung=0.00, rente=0.00, erwerb=780.00, abz_steuern=10.00, abz_sv=200.00, abz_vers=50.00, abz_erwerb=40.00
p1_regelbedarf=506.00
p1_mehrbedarf_g=0.00
p1_mehrbedarf_schwanger=0.00
p1_mehrbedarf_alleinerziehend=0.00
p1_mehrbedarf_warmwasser=11.64
p1_kdu=550.00
p1_gesamtbedarf=1067.64
p1_freibetrag_erwerb=234.00
p1_absetzbetaetrag=300.00
p1_anrechenbar=246.00
p1_zahlbetrag=821.64
p2_eingaben: rbs=2, merkzeichen_g=false, schwanger=false, entbindungsmonat=2026-07, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=0.00, kdu_betrieb=25.00, kdu_heizung=125.00, rente=0.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p2_regelbedarf=506.00
p2_mehrbedarf_g=0.00
p2_mehrbedarf_schwanger=0.00
p2_mehrbedarf_alleinerziehend=0.00
p2_mehrbedarf_warmwasser=0.00
p2_kdu=150.00
p2_gesamtbedarf=656.00
p2_freibetrag_erwerb=0.00
p2_absetzbetaetrag=0.00
p2_anrechenbar=0.00
p2_zahlbetrag=656.00
bg_gesamtbedarf=1723.64
bg_anrechenbar=246.00
bg_verwertbares_vermoegen=200.00
bg_zahlbetrag=1277.64
budget_rest_erwartet=220.00

### verifikation

V1: OK | alle Personen als eigene SozialFall-Zeilen
    sql    = SELECT COUNT(*) FROM SOZIAL_FALL WHERE BG_ID = ?
    params = [17827]
    erwartet = 2 | ist = 2
V2: OK | finale Zahlung wirksam (Vier-Augen-Warteschlange je nach Kassenlauf)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [16581]
    erwartet = ANGELEGT|BEREIT|UEBERGEBEN | ist = ANGELEGT
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [16581]
    erwartet = 1277.64 | ist = 1277.64
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17827]
    erwartet = 220.00 | ist = 220
V5: OK | genau ein erteilter Bewilligungsbescheid
    sql    = SELECT COUNT(*) FROM BESCHEID WHERE BG_ID = ?
    params = [17827]
    erwartet = 1 | ist = 1
V6: OK | Vermögen nach Verbrauchsfortschreibung (§ 90)
    sql    = SELECT VERMOEGEN FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17827]
    erwartet = 20000.00 | ist = 20000
V7: OK | dokumentierter Vermögenseinsatz an der Zahlung
    sql    = SELECT VERMOEGENSEINSATZ FROM ZAHLUNG WHERE ID = ?
    params = [16581]
    erwartet = 200.00 | ist = 200
V8: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:20:40.150 sb-sim-18 | Vorgang anlegen (Paar (2 × RBS 2), Budget 1497.64 EUR, Vermögen 20200.00 EUR) -> OK — Vorgang 17827
2026-10-13 11:20:40.173 sb-sim-18 | 2 SozialFall/Fälle erfassen -> OK — Paar (2 × RBS 2), KdU 700.00 EUR
2026-10-13 11:20:40.195 sb-sim-18 | Bewilligungsbescheid erteilen (2026-07 .. 2026-10) -> OK — Bescheid BESCH-SWEET-0105-2026-1751 mit Monatsplan gespeichert
2026-10-13 11:20:40.216 sb-sim-18 | Auszahlung anordnen (2026-08) -> OK — 1277.64 EUR (ANGELEGT)

## TF 106 KOMBINATION SWEET-0106
status = VERIFIZIERT
hauptperson = Lindner, Ingrid
bearbeiter = sb-sim-04
bg_id = 17828
dauer_ms = 72
ids = zahlungId=16579, vorgangsNr=a708a759-4588-4277-8cba-a6d6f234b413, mitarbeiter=sb-sim-04, bgId=17828, letzteZahlungId=16579, kollege=sb-sim-10

### rechenwege

block: Kombinationsfall 106: Alleinerziehend mit 2 Kind(ern)
zahlungsmonat = 2026-05
vermoegen = 11500.00
schonvermoegen = 11000.00
budget_start = 1137.00
p1_eingaben: rbs=1, merkzeichen_g=false, schwanger=true, entbindungsmonat=2026-07, kinder_u7=2, kinder_u16=2, kinder_minderjaehrig=2, warmwasser=true, kdu_miete=490.00, kdu_betrieb=135.00, kdu_heizung=110.00, rente=0.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p1_regelbedarf=563.00
p1_mehrbedarf_g=0.00
p1_mehrbedarf_schwanger=95.71
p1_mehrbedarf_alleinerziehend=202.68
p1_mehrbedarf_warmwasser=12.95
p1_kdu=735.00
p1_gesamtbedarf=1609.34
p1_freibetrag_erwerb=0.00
p1_absetzbetaetrag=0.00
p1_anrechenbar=0.00
p1_zahlbetrag=1609.34
p2_eingaben: rbs=5, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=0.00, kdu_betrieb=0.00, kdu_heizung=0.00, rente=540.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p2_regelbedarf=390.00
p2_mehrbedarf_g=0.00
p2_mehrbedarf_schwanger=0.00
p2_mehrbedarf_alleinerziehend=0.00
p2_mehrbedarf_warmwasser=0.00
p2_kdu=0.00
p2_gesamtbedarf=390.00
p2_freibetrag_erwerb=0.00
p2_absetzbetaetrag=0.00
p2_anrechenbar=540.00
p2_zahlbetrag=-150.00
p3_eingaben: rbs=4, merkzeichen_g=true, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=true, kdu_miete=0.00, kdu_betrieb=0.00, kdu_heizung=0.00, rente=610.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p3_regelbedarf=471.00
p3_mehrbedarf_g=80.07
p3_mehrbedarf_schwanger=0.00
p3_mehrbedarf_alleinerziehend=0.00
p3_mehrbedarf_warmwasser=6.59
p3_kdu=0.00
p3_gesamtbedarf=557.66
p3_freibetrag_erwerb=0.00
p3_absetzbetaetrag=0.00
p3_anrechenbar=610.00
p3_zahlbetrag=-52.34
bg_gesamtbedarf=2557.00
bg_anrechenbar=1150.00
bg_verwertbares_vermoegen=500.00
bg_zahlbetrag=907.00
budget_rest_erwartet=230.00

### verifikation

V1: OK | alle Personen als eigene SozialFall-Zeilen
    sql    = SELECT COUNT(*) FROM SOZIAL_FALL WHERE BG_ID = ?
    params = [17828]
    erwartet = 3 | ist = 3
V2: OK | finale Zahlung freigegeben (oder bereits übergeben)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [16579]
    erwartet = BEREIT|UEBERGEBEN | ist = UEBERGEBEN
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [16579]
    erwartet = 907.00 | ist = 907
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17828]
    erwartet = 230.00 | ist = 230
V5: OK | Freigabevermerk nennt den Kollegen (Vier-Augen)
    sql    = SELECT CASE WHEN FREIGEGEBEN_VON = ? THEN 'ok' ELSE 'abweichend' END FROM ZAHLUNG WHERE ID = ?
    params = [sb-sim-10, 16579]
    erwartet = ok | ist = ok
V6: OK | Vermögen nach Verbrauchsfortschreibung (§ 90)
    sql    = SELECT VERMOEGEN FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17828]
    erwartet = 11000.00 | ist = 11000
V7: OK | dokumentierter Vermögenseinsatz an der Zahlung
    sql    = SELECT VERMOEGENSEINSATZ FROM ZAHLUNG WHERE ID = ?
    params = [16579]
    erwartet = 500.00 | ist = 500
V8: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:20:40.156 sb-sim-04 | Vorgang anlegen (Alleinerziehend mit 2 Kind(ern), Budget 1137.00 EUR, Vermögen 11500.00 EUR) -> OK — Vorgang 17828
2026-10-13 11:20:40.191 sb-sim-04 | 3 SozialFall/Fälle erfassen -> OK — Alleinerziehend mit 2 Kind(ern), KdU 735.00 EUR
2026-10-13 11:20:40.210 sb-sim-04 | Auszahlung anordnen (2026-05) -> OK — 907.00 EUR (ANGELEGT)
2026-10-13 11:20:40.220 sb-sim-04 | Freigabe durch Kollegen (Vier-Augen) -> OK — freigegeben durch sb-sim-10

## TF 107 KOMBINATION SWEET-0107
status = VERIFIZIERT
hauptperson = Meyer, Heinz
bearbeiter = sb-sim-08
bg_id = 17829
dauer_ms = 66
ids = zahlungId=16583, vorgangsNr=0564fad6-374d-43cf-ab60-b16a9e35b28d, mitarbeiter=sb-sim-08, bgId=17829, letzteZahlungId=16583, kollege=sb-sim-15

### rechenwege

block: Kombinationsfall 107: Alleinerziehend mit 2 Kind(ern)
zahlungsmonat = 2026-06
vermoegen = 0.00
schonvermoegen = 11000.00
budget_start = 703.13
p1_eingaben: rbs=1, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=1, kinder_u16=2, kinder_minderjaehrig=2, warmwasser=true, kdu_miete=300.00, kdu_betrieb=85.00, kdu_heizung=105.00, rente=300.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p1_regelbedarf=563.00
p1_mehrbedarf_g=0.00
p1_mehrbedarf_schwanger=0.00
p1_mehrbedarf_alleinerziehend=202.68
p1_mehrbedarf_warmwasser=12.95
p1_kdu=490.00
p1_gesamtbedarf=1268.63
p1_freibetrag_erwerb=0.00
p1_absetzbetaetrag=0.00
p1_anrechenbar=300.00
p1_zahlbetrag=968.63
p2_eingaben: rbs=4, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=0.00, kdu_betrieb=0.00, kdu_heizung=0.00, rente=420.00, erwerb=670.00, abz_steuern=110.00, abz_sv=170.00, abz_vers=0.00, abz_erwerb=70.00
p2_regelbedarf=471.00
p2_mehrbedarf_g=0.00
p2_mehrbedarf_schwanger=0.00
p2_mehrbedarf_alleinerziehend=0.00
p2_mehrbedarf_warmwasser=0.00
p2_kdu=0.00
p2_gesamtbedarf=471.00
p2_freibetrag_erwerb=201.00
p2_absetzbetaetrag=350.00
p2_anrechenbar=539.00
p2_zahlbetrag=-68.00
p3_eingaben: rbs=4, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=0.00, kdu_betrieb=0.00, kdu_heizung=0.00, rente=500.00, erwerb=1040.00, abz_steuern=60.00, abz_sv=280.00, abz_vers=30.00, abz_erwerb=20.00
p3_regelbedarf=471.00
p3_mehrbedarf_g=0.00
p3_mehrbedarf_schwanger=0.00
p3_mehrbedarf_alleinerziehend=0.00
p3_mehrbedarf_warmwasser=0.00
p3_kdu=0.00
p3_gesamtbedarf=471.00
p3_freibetrag_erwerb=281.50
p3_absetzbetaetrag=390.00
p3_anrechenbar=868.50
p3_zahlbetrag=-397.50
bg_gesamtbedarf=2210.63
bg_anrechenbar=1707.50
bg_verwertbares_vermoegen=0.00
bg_zahlbetrag=503.13
budget_rest_erwartet=200.00

### verifikation

V1: OK | alle Personen als eigene SozialFall-Zeilen
    sql    = SELECT COUNT(*) FROM SOZIAL_FALL WHERE BG_ID = ?
    params = [17829]
    erwartet = 3 | ist = 3
V2: OK | finale Zahlung wirksam (Vier-Augen-Warteschlange je nach Kassenlauf)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [16583]
    erwartet = ANGELEGT|BEREIT|UEBERGEBEN | ist = ANGELEGT
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [16583]
    erwartet = 503.13 | ist = 503.13
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17829]
    erwartet = 200.00 | ist = 200
V5: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:20:40.158 sb-sim-08 | Vorgang anlegen (Alleinerziehend mit 2 Kind(ern), Budget 703.13 EUR) -> OK — Vorgang 17829
2026-10-13 11:20:40.196 sb-sim-08 | 3 SozialFall/Fälle erfassen -> OK — Alleinerziehend mit 2 Kind(ern), KdU 490.00 EUR
2026-10-13 11:20:40.216 sb-sim-08 | Auszahlung anordnen (2026-06) -> OK — 503.13 EUR (ANGELEGT)

## TF 108 KOMBINATION SWEET-0108
status = VERIFIZIERT
hauptperson = Jung, Paul
bearbeiter = sb-sim-16
bg_id = 17830
dauer_ms = 79
ids = zahlungId=16584, vorgangsNr=2052bc74-3115-4ae0-8000-64f7e4f14f6f, mitarbeiter=sb-sim-16, bgId=17830, letzteZahlungId=16584, kollege=sb-sim-02

### rechenwege

block: Kombinationsfall 108: Single (Schwangerschafts-Mehrbedarf entfällt im Zahlungsmonat)
zahlungsmonat = 2026-03
vermoegen = 10050.00
schonvermoegen = 10000.00
budget_start = 938.00
p1_eingaben: rbs=1, merkzeichen_g=false, schwanger=false, entbindungsmonat=2026-01, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=230.00, kdu_betrieb=30.00, kdu_heizung=115.00, rente=350.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p1_regelbedarf=563.00
p1_mehrbedarf_g=0.00
p1_mehrbedarf_schwanger=0.00
p1_mehrbedarf_alleinerziehend=0.00
p1_mehrbedarf_warmwasser=0.00
p1_kdu=375.00
p1_gesamtbedarf=938.00
p1_freibetrag_erwerb=0.00
p1_absetzbetaetrag=0.00
p1_anrechenbar=350.00
p1_zahlbetrag=588.00
bg_gesamtbedarf=938.00
bg_anrechenbar=350.00
bg_verwertbares_vermoegen=50.00
bg_zahlbetrag=538.00
budget_rest_erwartet=400.00

### verifikation

V1: OK | alle Personen als eigene SozialFall-Zeilen
    sql    = SELECT COUNT(*) FROM SOZIAL_FALL WHERE BG_ID = ?
    params = [17830]
    erwartet = 1 | ist = 1
V2: OK | finale Zahlung freigegeben (oder bereits übergeben)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [16584]
    erwartet = BEREIT|UEBERGEBEN | ist = UEBERGEBEN
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [16584]
    erwartet = 538.00 | ist = 538
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17830]
    erwartet = 400.00 | ist = 400
V5: OK | Freigabevermerk nennt den Kollegen (Vier-Augen)
    sql    = SELECT CASE WHEN FREIGEGEBEN_VON = ? THEN 'ok' ELSE 'abweichend' END FROM ZAHLUNG WHERE ID = ?
    params = [sb-sim-02, 16584]
    erwartet = ok | ist = ok
V6: OK | genau ein erteilter Bewilligungsbescheid
    sql    = SELECT COUNT(*) FROM BESCHEID WHERE BG_ID = ?
    params = [17830]
    erwartet = 1 | ist = 1
V7: OK | Vermögen nach Verbrauchsfortschreibung (§ 90)
    sql    = SELECT VERMOEGEN FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17830]
    erwartet = 10000.00 | ist = 10000
V8: OK | dokumentierter Vermögenseinsatz an der Zahlung
    sql    = SELECT VERMOEGENSEINSATZ FROM ZAHLUNG WHERE ID = ?
    params = [16584]
    erwartet = 50.00 | ist = 50
V9: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:20:40.158 sb-sim-16 | Vorgang anlegen (Single, Budget 938.00 EUR, Vermögen 10050.00 EUR) -> OK — Vorgang 17830
2026-10-13 11:20:40.172 sb-sim-16 | 1 SozialFall/Fälle erfassen -> OK — Single, KdU 375.00 EUR
2026-10-13 11:20:40.196 sb-sim-16 | Bewilligungsbescheid erteilen (2026-01 .. 2026-07) -> OK — Bescheid BESCH-SWEET-0108-2026-1753 mit Monatsplan gespeichert
2026-10-13 11:20:40.218 sb-sim-16 | Auszahlung anordnen (2026-03) -> OK — 538.00 EUR (ANGELEGT)
2026-10-13 11:20:40.230 sb-sim-16 | Freigabe durch Kollegen (Vier-Augen) -> OK — freigegeben durch sb-sim-02

## TF 109 KOMBINATION SWEET-0109
status = VERIFIZIERT
hauptperson = Brandt, Ingrid
bearbeiter = sb-sim-19
bg_id = 17831
dauer_ms = 87
ids = zahlungId=16588, vorgangsNr=66ac43b9-b1e8-4c9f-bafc-0f3055a762ca, mitarbeiter=sb-sim-19, bgId=17831, letzteZahlungId=16588, kollege=sb-sim-01

### rechenwege

block: Kombinationsfall 109: Paar mit 1 Kind(ern)
zahlungsmonat = 2026-05
vermoegen = 0.00
schonvermoegen = 20500.00
budget_start = 464.02
p1_eingaben: rbs=2, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=220.00, kdu_betrieb=80.00, kdu_heizung=100.00, rente=890.00, erwerb=700.00, abz_steuern=50.00, abz_sv=330.00, abz_vers=60.00, abz_erwerb=80.00
p1_regelbedarf=506.00
p1_mehrbedarf_g=0.00
p1_mehrbedarf_schwanger=0.00
p1_mehrbedarf_alleinerziehend=0.00
p1_mehrbedarf_warmwasser=0.00
p1_kdu=400.00
p1_gesamtbedarf=906.00
p1_freibetrag_erwerb=210.00
p1_absetzbetaetrag=520.00
p1_anrechenbar=860.00
p1_zahlbetrag=46.00
p2_eingaben: rbs=2, merkzeichen_g=true, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=0.00, kdu_betrieb=0.00, kdu_heizung=0.00, rente=380.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p2_regelbedarf=506.00
p2_mehrbedarf_g=86.02
p2_mehrbedarf_schwanger=0.00
p2_mehrbedarf_alleinerziehend=0.00
p2_mehrbedarf_warmwasser=0.00
p2_kdu=0.00
p2_gesamtbedarf=592.02
p2_freibetrag_erwerb=0.00
p2_absetzbetaetrag=0.00
p2_anrechenbar=380.00
p2_zahlbetrag=212.02
p3_eingaben: rbs=6, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=0.00, kdu_betrieb=0.00, kdu_heizung=0.00, rente=380.00, erwerb=530.00, abz_steuern=40.00, abz_sv=320.00, abz_vers=50.00, abz_erwerb=50.00
p3_regelbedarf=357.00
p3_mehrbedarf_g=0.00
p3_mehrbedarf_schwanger=0.00
p3_mehrbedarf_alleinerziehend=0.00
p3_mehrbedarf_warmwasser=0.00
p3_kdu=0.00
p3_gesamtbedarf=357.00
p3_freibetrag_erwerb=159.00
p3_absetzbetaetrag=460.00
p3_anrechenbar=291.00
p3_zahlbetrag=66.00
bg_gesamtbedarf=1855.02
bg_anrechenbar=1531.00
bg_verwertbares_vermoegen=0.00
bg_zahlbetrag=324.02
budget_rest_erwartet=140.00

### verifikation

V1: OK | alle Personen als eigene SozialFall-Zeilen
    sql    = SELECT COUNT(*) FROM SOZIAL_FALL WHERE BG_ID = ?
    params = [17831]
    erwartet = 3 | ist = 3
V2: OK | finale Zahlung wirksam (Vier-Augen-Warteschlange je nach Kassenlauf)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [16588]
    erwartet = ANGELEGT|BEREIT|UEBERGEBEN | ist = ANGELEGT
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [16588]
    erwartet = 324.02 | ist = 324.02
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17831]
    erwartet = 140.00 | ist = 140
V5: OK | genau ein erteilter Bewilligungsbescheid
    sql    = SELECT COUNT(*) FROM BESCHEID WHERE BG_ID = ?
    params = [17831]
    erwartet = 1 | ist = 1
V6: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:20:40.158 sb-sim-19 | Vorgang anlegen (Paar mit 1 Kind(ern), Budget 464.02 EUR) -> OK — Vorgang 17831
2026-10-13 11:20:40.194 sb-sim-19 | 3 SozialFall/Fälle erfassen -> OK — Paar mit 1 Kind(ern), KdU 400.00 EUR
2026-10-13 11:20:40.220 sb-sim-19 | Bewilligungsbescheid erteilen (2026-03 .. 2026-10) -> OK — Bescheid BESCH-SWEET-0109-2026-1754 mit Monatsplan gespeichert
2026-10-13 11:20:40.238 sb-sim-19 | Auszahlung anordnen (2026-05) -> OK — 324.02 EUR (ANGELEGT)

## TF 110 KOMBINATION SWEET-0110
status = VERIFIZIERT
hauptperson = Roth, Clara
bearbeiter = sb-sim-13
bg_id = 17832
dauer_ms = 55
ids = zahlungId=16574, vorgangsNr=cdc67070-98c6-4bb1-8108-1be4f6538d51, mitarbeiter=sb-sim-13, bgId=17832, letzteZahlungId=16574, kollege=sb-sim-05

### rechenwege

block: Kombinationsfall 110: Single
zahlungsmonat = 2026-09
vermoegen = 10000.00
schonvermoegen = 10000.00
budget_start = 1028.00
p1_eingaben: rbs=1, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=750.00, kdu_betrieb=40.00, kdu_heizung=65.00, rente=630.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p1_regelbedarf=563.00
p1_mehrbedarf_g=0.00
p1_mehrbedarf_schwanger=0.00
p1_mehrbedarf_alleinerziehend=0.00
p1_mehrbedarf_warmwasser=0.00
p1_kdu=855.00
p1_gesamtbedarf=1418.00
p1_freibetrag_erwerb=0.00
p1_absetzbetaetrag=0.00
p1_anrechenbar=630.00
p1_zahlbetrag=788.00
bg_gesamtbedarf=1418.00
bg_anrechenbar=630.00
bg_verwertbares_vermoegen=0.00
bg_zahlbetrag=788.00
budget_rest_erwartet=240.00

### verifikation

V1: OK | alle Personen als eigene SozialFall-Zeilen
    sql    = SELECT COUNT(*) FROM SOZIAL_FALL WHERE BG_ID = ?
    params = [17832]
    erwartet = 1 | ist = 1
V2: OK | finale Zahlung freigegeben (oder bereits übergeben)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [16574]
    erwartet = BEREIT|UEBERGEBEN | ist = UEBERGEBEN
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [16574]
    erwartet = 788.00 | ist = 788
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17832]
    erwartet = 240.00 | ist = 240
V5: OK | Freigabevermerk nennt den Kollegen (Vier-Augen)
    sql    = SELECT CASE WHEN FREIGEGEBEN_VON = ? THEN 'ok' ELSE 'abweichend' END FROM ZAHLUNG WHERE ID = ?
    params = [sb-sim-05, 16574]
    erwartet = ok | ist = ok
V6: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:20:40.158 sb-sim-13 | Vorgang anlegen (Single, Budget 1028.00 EUR, Vermögen 10000.00 EUR) -> OK — Vorgang 17832
2026-10-13 11:20:40.172 sb-sim-13 | 1 SozialFall/Fälle erfassen -> OK — Single, KdU 855.00 EUR
2026-10-13 11:20:40.195 sb-sim-13 | Auszahlung anordnen (2026-09) -> OK — 788.00 EUR (ANGELEGT)
2026-10-13 11:20:40.206 sb-sim-13 | Freigabe durch Kollegen (Vier-Augen) -> OK — freigegeben durch sb-sim-05

## TF 111 KOMBINATION SWEET-0111
status = VERIFIZIERT
hauptperson = Neumann, Olga
bearbeiter = sb-sim-20
bg_id = 17833
dauer_ms = 76
ids = zahlungId=16585, vorgangsNr=bc887d1e-c5ae-4373-821f-937b29ff0b6e, mitarbeiter=sb-sim-20, bgId=17833, letzteZahlungId=16585, kollege=sb-sim-10

### rechenwege

block: Kombinationsfall 111: Single
zahlungsmonat = 2026-06
vermoegen = 0.00
schonvermoegen = 10000.00
budget_start = 1633.00
p1_eingaben: rbs=1, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=650.00, kdu_betrieb=100.00, kdu_heizung=100.00, rente=0.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00
p1_regelbedarf=563.00
p1_mehrbedarf_g=0.00
p1_mehrbedarf_schwanger=0.00
p1_mehrbedarf_alleinerziehend=0.00
p1_mehrbedarf_warmwasser=0.00
p1_kdu=850.00
p1_gesamtbedarf=1413.00
p1_freibetrag_erwerb=0.00
p1_absetzbetaetrag=0.00
p1_anrechenbar=0.00
p1_zahlbetrag=1413.00
bg_gesamtbedarf=1413.00
bg_anrechenbar=0.00
bg_verwertbares_vermoegen=0.00
bg_zahlbetrag=1413.00
budget_rest_erwartet=220.00

### verifikation

V1: OK | alle Personen als eigene SozialFall-Zeilen
    sql    = SELECT COUNT(*) FROM SOZIAL_FALL WHERE BG_ID = ?
    params = [17833]
    erwartet = 1 | ist = 1
V2: OK | finale Zahlung freigegeben (oder bereits übergeben)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [16585]
    erwartet = BEREIT|UEBERGEBEN | ist = UEBERGEBEN
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [16585]
    erwartet = 1413.00 | ist = 1413
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17833]
    erwartet = 220.00 | ist = 220
V5: OK | Freigabevermerk nennt den Kollegen (Vier-Augen)
    sql    = SELECT CASE WHEN FREIGEGEBEN_VON = ? THEN 'ok' ELSE 'abweichend' END FROM ZAHLUNG WHERE ID = ?
    params = [sb-sim-10, 16585]
    erwartet = ok | ist = ok
V6: OK | genau ein erteilter Bewilligungsbescheid
    sql    = SELECT COUNT(*) FROM BESCHEID WHERE BG_ID = ?
    params = [17833]
    erwartet = 1 | ist = 1
V7: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:20:40.161 sb-sim-20 | Vorgang anlegen (Single, Budget 1633.00 EUR) -> OK — Vorgang 17833
2026-10-13 11:20:40.173 sb-sim-20 | 1 SozialFall/Fälle erfassen -> OK — Single, KdU 850.00 EUR
2026-10-13 11:20:40.198 sb-sim-20 | Bewilligungsbescheid erteilen (2026-05 .. 2026-10) -> OK — Bescheid BESCH-SWEET-0111-2026-1752 mit Monatsplan gespeichert
2026-10-13 11:20:40.218 sb-sim-20 | Auszahlung anordnen (2026-06) -> OK — 1413.00 EUR (ANGELEGT)
2026-10-13 11:20:40.230 sb-sim-20 | Freigabe durch Kollegen (Vier-Augen) -> OK — freigegeben durch sb-sim-10

## TF 112 KOMBINATION SWEET-0112
status = VERIFIZIERT
hauptperson = Wagner, Waltraud
bearbeiter = sb-sim-15
bg_id = 17834
dauer_ms = 52
ids = zahlungId=16580, vorgangsNr=0d2a0d56-61b8-4d64-95bf-226320b6ffe9, mitarbeiter=sb-sim-15, bgId=17834, letzteZahlungId=16580, kollege=sb-sim-07

### rechenwege

block: Kombinationsfall 112: Paar (2 × RBS 2)
zahlungsmonat = 2026-02
vermoegen = 0.00
schonvermoegen = 20000.00
budget_start = 646.64
p1_eingaben: rbs=2, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=false, kdu_miete=730.00, kdu_betrieb=0.00, kdu_heizung=0.00, rente=0.00, erwerb=1110.00, abz_steuern=140.00, abz_sv=90.00, abz_vers=50.00, abz_erwerb=60.00
p1_regelbedarf=506.00
p1_mehrbedarf_g=0.00
p1_mehrbedarf_schwanger=0.00
p1_mehrbedarf_alleinerziehend=0.00
p1_mehrbedarf_warmwasser=0.00
p1_kdu=730.00
p1_gesamtbedarf=1236.00
p1_freibetrag_erwerb=281.50
p1_absetzbetaetrag=340.00
p1_anrechenbar=488.50
p1_zahlbetrag=747.50
p2_eingaben: rbs=2, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser=true, kdu_miete=0.00, kdu_betrieb=105.00, kdu_heizung=95.00, rente=430.00, erwerb=1220.00, abz_steuern=180.00, abz_sv=50.00, abz_vers=60.00, abz_erwerb=30.00
p2_regelbedarf=506.00
p2_mehrbedarf_g=0.00
p2_mehrbedarf_schwanger=0.00
p2_mehrbedarf_alleinerziehend=0.00
p2_mehrbedarf_warmwasser=11.64
p2_kdu=200.00
p2_gesamtbedarf=717.64
p2_freibetrag_erwerb=281.50
p2_absetzbetaetrag=320.00
p2_anrechenbar=1048.50
p2_zahlbetrag=-330.86
bg_gesamtbedarf=1953.64
bg_anrechenbar=1537.00
bg_verwertbares_vermoegen=0.00
bg_zahlbetrag=416.64
budget_rest_erwartet=230.00

### verifikation

V1: OK | alle Personen als eigene SozialFall-Zeilen
    sql    = SELECT COUNT(*) FROM SOZIAL_FALL WHERE BG_ID = ?
    params = [17834]
    erwartet = 2 | ist = 2
V2: OK | finale Zahlung wirksam (Vier-Augen-Warteschlange je nach Kassenlauf)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [16580]
    erwartet = ANGELEGT|BEREIT|UEBERGEBEN | ist = ANGELEGT
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [16580]
    erwartet = 416.64 | ist = 416.64
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [17834]
    erwartet = 230.00 | ist = 230
V5: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:20:40.165 sb-sim-15 | Vorgang anlegen (Paar (2 × RBS 2), Budget 646.64 EUR) -> OK — Vorgang 17834
2026-10-13 11:20:40.190 sb-sim-15 | 2 SozialFall/Fälle erfassen -> OK — Paar (2 × RBS 2), KdU 930.00 EUR
2026-10-13 11:20:40.208 sb-sim-15 | Auszahlung anordnen (2026-02) -> OK — 416.64 EUR (ANGELEGT)

