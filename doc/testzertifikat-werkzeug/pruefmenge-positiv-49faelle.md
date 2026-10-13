> Prüfmenge für die LLM-Nachrechnung — erzeugt am 2026-10-13 über die testsweet-GUI (Prüfpaket, 100 Fälle).
> Grundlage: testdokumentation.md des neuesten Laufs (..\testsweet\logs\run-20261013-112949\testdokumentation.md).
> Kürzung: Lauf- und Rechengrundlagen-Kopf vollständig + die ersten 49 Kombinationsfälle von insgesamt 49 (kuratierte Fälle und Kassenläufe sind NICHT enthalten).
> Mitgegeben werden außerdem vollständig: gesetzesgrundlagen-2026.md und llm-pruefauftrag.txt (beide im Werkzeugordner doc/testzertifikat-werkzeug/).

# testsweet Lauf

lauf_erfolgreich = true
globale_pruefung = Pruefung[beschreibung=Global: Anzahl SWEET-Vorgänge, erwartet=58, ist=58, ok=true, hinweis=null]
globale_pruefung = Pruefung[beschreibung=Global: keine Übergabe ohne Freigabevermerk, erwartet=0, ist=0, ok=true, hinweis=null]
produkt = SDN.Leistung LMG
modus = 1-fachschicht (SozialLeistungsService direkt)
kategorie = POSITIV
start = 2026-10-13T11:29:49.153395100
ende = 2026-10-13T11:29:50.999367700
dauer_s = 1
db = jdbc:oracle:thin:@//localhost:1521/FREEPDB1
db_user = tx_demo
mitarbeiter = 10
auftraege = 60
seed = 42
denkzeit_ms = 20
ergebnis.verifiziert = 60
ergebnis.verifikation_nio = 0
ergebnis.fehlgeschlagen = 0
ergebnis.gesamt = 60
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
bearbeiter = sb-sim-06
bg_id = 18224
dauer_ms = 284
ids = mitarbeiter=sb-sim-06, bgId=18224, kollege=sb-sim-01, letzteZahlungId=17011, vorgangsNr=411cd767-25c3-43ec-8737-c2038d7b18f3, fallId=27913, zahlungId=17000

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
    params = [18224]
    erwartet = 1 | ist = 1
V2: OK | finale Zahlung wirksam (Vier-Augen-Warteschlange je nach Kassenlauf)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [17011]
    erwartet = ANGELEGT|BEREIT|UEBERGEBEN | ist = ANGELEGT
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [17011]
    erwartet = 1058.71 | ist = 1058.71
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [18224]
    erwartet = 190.00 | ist = 190
V5: OK | zwei Zahlungen (stornierte + korrigierte)
    sql    = SELECT COUNT(*) FROM ZAHLUNG WHERE BG_ID = ?
    params = [18224]
    erwartet = 2 | ist = 2
V6: OK | erste Zahlung STORNIERT
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [17000]
    erwartet = STORNIERT | ist = STORNIERT
V7: OK | Fall in Version 2 (optimistisches Locking)
    sql    = SELECT VERSION FROM SOZIAL_FALL WHERE ID = ?
    params = [27913]
    erwartet = 2 | ist = 2
V8: OK | genau ein erteilter Bewilligungsbescheid
    sql    = SELECT COUNT(*) FROM BESCHEID WHERE BG_ID = ?
    params = [18224]
    erwartet = 1 | ist = 1
V9: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:29:49.740 sb-sim-06 | Vorgang anlegen (Single, Budget 1248.71 EUR) -> OK — Vorgang 18224
2026-10-13 11:29:49.779 sb-sim-06 | 1 SozialFall/Fälle erfassen -> OK — Single, KdU 420.00 EUR
2026-10-13 11:29:49.827 sb-sim-06 | Bewilligungsbescheid erteilen (2026-02 .. 2026-08) -> OK — Bescheid BESCH-SWEET-0011-2026-1870 mit Monatsplan gespeichert
2026-10-13 11:29:49.868 sb-sim-06 | Auszahlung anordnen (2026-03) -> OK — 958.71 EUR (ANGELEGT)
2026-10-13 11:29:49.910 sb-sim-06 | Anordnung sofort stornieren (Status ANGELEGT) -> OK — storniert — Monat wieder frei
2026-10-13 11:29:49.956 sb-sim-06 | Miete auf 330.00 korrigieren -> OK — Fall 27913 in Version 2
2026-10-13 11:29:49.990 sb-sim-06 | Korrigierte Auszahlung für denselben Monat -> OK — 1058.71 EUR

## TF 12 KOMBINATION SWEET-0012
status = VERIFIZIERT
hauptperson = Ismail, Stefan
bearbeiter = sb-sim-04
bg_id = 18228
dauer_ms = 145
ids = vorgangsNr=4ef82f32-380b-4658-ad7e-ff1739f8751c, zahlungId=17002, kollege=sb-sim-03, letzteZahlungId=17002, bgId=18228, mitarbeiter=sb-sim-04

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
    params = [18228]
    erwartet = 2 | ist = 2
V2: OK | finale Zahlung wirksam (Vier-Augen-Warteschlange je nach Kassenlauf)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [17002]
    erwartet = ANGELEGT|BEREIT|UEBERGEBEN | ist = ANGELEGT
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [17002]
    erwartet = 179.37 | ist = 179.37
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [18228]
    erwartet = 350.00 | ist = 350
V5: OK | genau eine Zahlung trotz Wiederholung
    sql    = SELECT COUNT(*) FROM ZAHLUNG WHERE BG_ID = ?
    params = [18228]
    erwartet = 1 | ist = 1
V6: OK | Vermögen nach Verbrauchsfortschreibung (§ 90)
    sql    = SELECT VERMOEGEN FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [18228]
    erwartet = 10500.00 | ist = 10500
V7: OK | dokumentierter Vermögenseinsatz an der Zahlung
    sql    = SELECT VERMOEGENSEINSATZ FROM ZAHLUNG WHERE ID = ?
    params = [17002]
    erwartet = 1300.00 | ist = 1300
V8: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:29:49.811 sb-sim-04 | Vorgang anlegen (Alleinerziehend mit 1 Kind(ern), Budget 529.37 EUR, Vermögen 11800.00 EUR) -> OK — Vorgang 18228
2026-10-13 11:29:49.855 sb-sim-04 | 2 SozialFall/Fälle erfassen -> OK — Alleinerziehend mit 1 Kind(ern), KdU 525.00 EUR
2026-10-13 11:29:49.887 sb-sim-04 | Auszahlung anordnen (2026-05) -> OK — 179.37 EUR (ANGELEGT)
2026-10-13 11:29:49.923 sb-sim-04 | Doppelklick: gleiche Vorgangsnummer erneut -> OK — korrekt als bereits gebucht erkannt

## TF 13 KOMBINATION SWEET-0013
status = VERIFIZIERT
hauptperson = Lindner, Gisela
bearbeiter = sb-sim-03
bg_id = 18225
dauer_ms = 176
ids = vorgangsNr=07866a32-1d35-4368-856b-472d5222d6ba, zahlungId=17007, kollege=sb-sim-06, letzteZahlungId=17007, bgId=18225, mitarbeiter=sb-sim-03

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
    params = [18225]
    erwartet = 3 | ist = 3
V2: OK | finale Zahlung wirksam (Vier-Augen-Warteschlange je nach Kassenlauf)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [17007]
    erwartet = ANGELEGT|BEREIT|UEBERGEBEN | ist = ANGELEGT
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [17007]
    erwartet = 130.28 | ist = 130.28
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [18225]
    erwartet = 140.00 | ist = 140
V5: OK | genau ein erteilter Bewilligungsbescheid
    sql    = SELECT COUNT(*) FROM BESCHEID WHERE BG_ID = ?
    params = [18225]
    erwartet = 1 | ist = 1
V6: OK | Vermögen nach Verbrauchsfortschreibung (§ 90)
    sql    = SELECT VERMOEGEN FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [18225]
    erwartet = 20500.00 | ist = 20500
V7: OK | dokumentierter Vermögenseinsatz an der Zahlung
    sql    = SELECT VERMOEGENSEINSATZ FROM ZAHLUNG WHERE ID = ?
    params = [17007]
    erwartet = 450.00 | ist = 450
V8: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:29:49.809 sb-sim-03 | Vorgang anlegen (Paar mit 1 Kind(ern), Budget 270.28 EUR, Vermögen 20950.00 EUR) -> OK — Vorgang 18225
2026-10-13 11:29:49.860 sb-sim-03 | 3 SozialFall/Fälle erfassen -> OK — Paar mit 1 Kind(ern), KdU 665.00 EUR
2026-10-13 11:29:49.905 sb-sim-03 | Bewilligungsbescheid erteilen (2026-03 .. 2026-09) -> OK — Bescheid BESCH-SWEET-0013-2026-1872 mit Monatsplan gespeichert
2026-10-13 11:29:49.947 sb-sim-03 | Auszahlung anordnen (2026-03) -> OK — 130.28 EUR (ANGELEGT)

## TF 14 KOMBINATION SWEET-0014
status = VERIFIZIERT
hauptperson = Quest, Elke
bearbeiter = sb-sim-05
bg_id = 18226
dauer_ms = 145
ids = vorgangsNr=d9533fdd-d2e2-458c-a28a-2dca6e2ff1ec, zahlungId=17003, kollege=sb-sim-04, letzteZahlungId=17003, bgId=18226, mitarbeiter=sb-sim-05

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
    params = [18226]
    erwartet = 2 | ist = 2
V2: OK | finale Zahlung freigegeben (oder bereits übergeben)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [17003]
    erwartet = BEREIT|UEBERGEBEN | ist = UEBERGEBEN
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [17003]
    erwartet = 1762.00 | ist = 1762
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [18226]
    erwartet = 260.00 | ist = 260
V5: OK | Freigabevermerk nennt den Kollegen (Vier-Augen)
    sql    = SELECT CASE WHEN FREIGEGEBEN_VON = ? THEN 'ok' ELSE 'abweichend' END FROM ZAHLUNG WHERE ID = ?
    params = [sb-sim-04, 17003]
    erwartet = ok | ist = ok
V6: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:29:49.811 sb-sim-05 | Vorgang anlegen (Paar (2 × RBS 2), Budget 2022.00 EUR) -> OK — Vorgang 18226
2026-10-13 11:29:49.853 sb-sim-05 | 2 SozialFall/Fälle erfassen -> OK — Paar (2 × RBS 2), KdU 870.00 EUR
2026-10-13 11:29:49.898 sb-sim-05 | Auszahlung anordnen (2026-11) -> OK — 1762.00 EUR (ANGELEGT)
2026-10-13 11:29:49.923 sb-sim-05 | Freigabe durch Kollegen (Vier-Augen) -> OK — freigegeben durch sb-sim-04

## TF 15 KOMBINATION SWEET-0015
status = VERIFIZIERT
hauptperson = Thiel, Ludwig
bearbeiter = sb-sim-02
bg_id = 18229
dauer_ms = 100
ids = vorgangsNr=729a438c-88de-4960-aa9c-520a046f5b09, zahlungId=17001, kollege=sb-sim-10, letzteZahlungId=17001, bgId=18229, mitarbeiter=sb-sim-02

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
    params = [18229]
    erwartet = 1 | ist = 1
V2: OK | finale Zahlung wirksam (Vier-Augen-Warteschlange je nach Kassenlauf)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [17001]
    erwartet = ANGELEGT|BEREIT|UEBERGEBEN | ist = ANGELEGT
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [17001]
    erwartet = 162.45 | ist = 162.45
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [18229]
    erwartet = 200.00 | ist = 200
V5: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:29:49.811 sb-sim-02 | Vorgang anlegen (Single, Budget 362.45 EUR) -> OK — Vorgang 18229
2026-10-13 11:29:49.843 sb-sim-02 | 1 SozialFall/Fälle erfassen -> OK — Single, KdU 550.00 EUR
2026-10-13 11:29:49.884 sb-sim-02 | Auszahlung anordnen (2026-09) -> OK — 162.45 EUR (ANGELEGT)

## TF 16 KOMBINATION SWEET-0016
status = VERIFIZIERT
hauptperson = Jung, Stefan
bearbeiter = sb-sim-10
bg_id = 18227
dauer_ms = 175
ids = vorgangsNr=b17ccce4-a0e1-4f5f-b8a8-2500382285e4, zahlungId=17005, kollege=sb-sim-04, letzteZahlungId=17005, bgId=18227, mitarbeiter=sb-sim-10

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
    params = [18227]
    erwartet = 2 | ist = 2
V2: OK | finale Zahlung wirksam (Vier-Augen-Warteschlange je nach Kassenlauf)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [17005]
    erwartet = ANGELEGT|BEREIT|UEBERGEBEN | ist = ANGELEGT
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [17005]
    erwartet = 586.50 | ist = 586.5
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [18227]
    erwartet = 80.00 | ist = 80
V5: OK | genau eine Zahlung trotz Wiederholung
    sql    = SELECT COUNT(*) FROM ZAHLUNG WHERE BG_ID = ?
    params = [18227]
    erwartet = 1 | ist = 1
V6: OK | genau ein erteilter Bewilligungsbescheid
    sql    = SELECT COUNT(*) FROM BESCHEID WHERE BG_ID = ?
    params = [18227]
    erwartet = 1 | ist = 1
V7: OK | Vermögen nach Verbrauchsfortschreibung (§ 90)
    sql    = SELECT VERMOEGEN FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [18227]
    erwartet = 20000.00 | ist = 20000
V8: OK | dokumentierter Vermögenseinsatz an der Zahlung
    sql    = SELECT VERMOEGENSEINSATZ FROM ZAHLUNG WHERE ID = ?
    params = [17005]
    erwartet = 150.00 | ist = 150
V9: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:29:49.811 sb-sim-10 | Vorgang anlegen (Paar (2 × RBS 2), Budget 666.50 EUR, Vermögen 20150.00 EUR) -> OK — Vorgang 18227
2026-10-13 11:29:49.849 sb-sim-10 | 2 SozialFall/Fälle erfassen -> OK — Paar (2 × RBS 2), KdU 765.00 EUR
2026-10-13 11:29:49.889 sb-sim-10 | Bewilligungsbescheid erteilen (2026-09 .. 2026-12) -> OK — Bescheid BESCH-SWEET-0016-2026-1871 mit Monatsplan gespeichert
2026-10-13 11:29:49.928 sb-sim-10 | Auszahlung anordnen (2026-11) -> OK — 586.50 EUR (ANGELEGT)
2026-10-13 11:29:49.954 sb-sim-10 | Doppelklick: gleiche Vorgangsnummer erneut -> OK — korrekt als bereits gebucht erkannt

## TF 17 KOMBINATION SWEET-0017
status = VERIFIZIERT
hauptperson = Brandt, Clara
bearbeiter = sb-sim-09
bg_id = 18230
dauer_ms = 222
ids = vorgangsNr=e8e7b271-b9fa-4ac2-a806-3ebb2aff296c, zahlungId=17010, kollege=sb-sim-08, letzteZahlungId=17010, bgId=18230, mitarbeiter=sb-sim-09

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
    params = [18230]
    erwartet = 4 | ist = 4
V2: OK | finale Zahlung wirksam (Vier-Augen-Warteschlange je nach Kassenlauf)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [17010]
    erwartet = ANGELEGT|BEREIT|UEBERGEBEN | ist = ANGELEGT
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [17010]
    erwartet = 111.57 | ist = 111.57
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [18230]
    erwartet = 270.00 | ist = 270
V5: OK | genau eine Zahlung trotz Wiederholung
    sql    = SELECT COUNT(*) FROM ZAHLUNG WHERE BG_ID = ?
    params = [18230]
    erwartet = 1 | ist = 1
V6: OK | genau ein erteilter Bewilligungsbescheid
    sql    = SELECT COUNT(*) FROM BESCHEID WHERE BG_ID = ?
    params = [18230]
    erwartet = 1 | ist = 1
V7: OK | Vermögen nach Verbrauchsfortschreibung (§ 90)
    sql    = SELECT VERMOEGEN FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [18230]
    erwartet = 21000.00 | ist = 21000
V8: OK | dokumentierter Vermögenseinsatz an der Zahlung
    sql    = SELECT VERMOEGENSEINSATZ FROM ZAHLUNG WHERE ID = ?
    params = [17010]
    erwartet = 200.00 | ist = 200
V9: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:29:49.822 sb-sim-09 | Vorgang anlegen (Paar mit 2 Kind(ern), Budget 381.57 EUR, Vermögen 21200.00 EUR) -> OK — Vorgang 18230
2026-10-13 11:29:49.891 sb-sim-09 | 4 SozialFall/Fälle erfassen -> OK — Paar mit 2 Kind(ern), KdU 520.00 EUR
2026-10-13 11:29:49.937 sb-sim-09 | Bewilligungsbescheid erteilen (2026-04 .. 2026-12) -> OK — Bescheid BESCH-SWEET-0017-2026-1874 mit Monatsplan gespeichert
2026-10-13 11:29:49.974 sb-sim-09 | Auszahlung anordnen (2026-06) -> OK — 111.57 EUR (ANGELEGT)
2026-10-13 11:29:50.014 sb-sim-09 | Doppelklick: gleiche Vorgangsnummer erneut -> OK — korrekt als bereits gebucht erkannt

## TF 18 KOMBINATION SWEET-0018
status = VERIFIZIERT
hauptperson = Brandt, Frank
bearbeiter = sb-sim-07
bg_id = 18231
dauer_ms = 145
ids = vorgangsNr=12325bb2-8762-40d5-b57c-0b950733fcb0, zahlungId=17008, kollege=sb-sim-10, letzteZahlungId=17008, bgId=18231, mitarbeiter=sb-sim-07

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
    params = [18231]
    erwartet = 1 | ist = 1
V2: OK | finale Zahlung wirksam (Vier-Augen-Warteschlange je nach Kassenlauf)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [17008]
    erwartet = ANGELEGT|BEREIT|UEBERGEBEN | ist = ANGELEGT
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [17008]
    erwartet = 376.66 | ist = 376.66
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [18231]
    erwartet = 370.00 | ist = 370
V5: OK | genau ein erteilter Bewilligungsbescheid
    sql    = SELECT COUNT(*) FROM BESCHEID WHERE BG_ID = ?
    params = [18231]
    erwartet = 1 | ist = 1
V6: OK | Vermögen nach Verbrauchsfortschreibung (§ 90)
    sql    = SELECT VERMOEGEN FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [18231]
    erwartet = 10000.00 | ist = 10000
V7: OK | dokumentierter Vermögenseinsatz an der Zahlung
    sql    = SELECT VERMOEGENSEINSATZ FROM ZAHLUNG WHERE ID = ?
    params = [17008]
    erwartet = 700.00 | ist = 700
V8: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:29:49.841 sb-sim-07 | Vorgang anlegen (Single, Budget 746.66 EUR, Vermögen 10700.00 EUR) -> OK — Vorgang 18231
2026-10-13 11:29:49.878 sb-sim-07 | 1 SozialFall/Fälle erfassen -> OK — Single, KdU 405.00 EUR
2026-10-13 11:29:49.915 sb-sim-07 | Bewilligungsbescheid erteilen (2026-03 .. 2026-04) -> OK — Bescheid BESCH-SWEET-0018-2026-1873 mit Monatsplan gespeichert
2026-10-13 11:29:49.960 sb-sim-07 | Auszahlung anordnen (2026-03) -> OK — 376.66 EUR (ANGELEGT)

## TF 19 KOMBINATION SWEET-0019
status = VERIFIZIERT
hauptperson = Dietrich, Ludwig
bearbeiter = sb-sim-01
bg_id = 18232
dauer_ms = 130
ids = vorgangsNr=2984b0b3-0323-4091-9739-ea27e2b34124, zahlungId=17006, kollege=sb-sim-09, letzteZahlungId=17006, bgId=18232, mitarbeiter=sb-sim-01

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
    params = [18232]
    erwartet = 4 | ist = 4
V2: OK | finale Zahlung wirksam (Vier-Augen-Warteschlange je nach Kassenlauf)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [17006]
    erwartet = ANGELEGT|BEREIT|UEBERGEBEN | ist = ANGELEGT
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [17006]
    erwartet = 2112.27 | ist = 2112.27
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [18232]
    erwartet = 210.00 | ist = 210
V5: OK | Wiedervorlagedatum exakt wie gesetzt
    sql    = SELECT COUNT(*) FROM BEDARFSGEMEINSCHAFT WHERE ID = ? AND WIEDERVORLAGE_AM = ?
    params = [18232, 2027-05-07]
    erwartet = 1 | ist = 1
V6: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:29:49.839 sb-sim-01 | Vorgang anlegen (Alleinerziehend mit 3 Kind(ern), Budget 2322.27 EUR) -> OK — Vorgang 18232
2026-10-13 11:29:49.912 sb-sim-01 | 4 SozialFall/Fälle erfassen -> OK — Alleinerziehend mit 3 Kind(ern), KdU 675.00 EUR
2026-10-13 11:29:49.945 sb-sim-01 | Auszahlung anordnen (2026-11) -> OK — 2112.27 EUR (ANGELEGT)

## TF 20 KOMBINATION SWEET-0020
status = VERIFIZIERT
hauptperson = Gross, Karin
bearbeiter = sb-sim-02
bg_id = 18233
dauer_ms = 121
ids = vorgangsNr=729bedbb-9a08-453f-9ae9-82b3cd089ded, zahlungId=17009, kollege=sb-sim-05, letzteZahlungId=17009, bgId=18233, mitarbeiter=sb-sim-02

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
    params = [18233]
    erwartet = 2 | ist = 2
V2: OK | finale Zahlung wirksam (Vier-Augen-Warteschlange je nach Kassenlauf)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [17009]
    erwartet = ANGELEGT|BEREIT|UEBERGEBEN | ist = ANGELEGT
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [17009]
    erwartet = 338.50 | ist = 338.5
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [18233]
    erwartet = 80.00 | ist = 80
V5: OK | genau eine Zahlung trotz Wiederholung
    sql    = SELECT COUNT(*) FROM ZAHLUNG WHERE BG_ID = ?
    params = [18233]
    erwartet = 1 | ist = 1
V6: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:29:49.907 sb-sim-02 | Vorgang anlegen (Paar (2 × RBS 2), Budget 418.50 EUR) -> OK — Vorgang 18233
2026-10-13 11:29:49.947 sb-sim-02 | 2 SozialFall/Fälle erfassen -> OK — Paar (2 × RBS 2), KdU 690.00 EUR
2026-10-13 11:29:49.972 sb-sim-02 | Auszahlung anordnen (2026-06) -> OK — 338.50 EUR (ANGELEGT)
2026-10-13 11:29:49.998 sb-sim-02 | Doppelklick: gleiche Vorgangsnummer erneut -> OK — korrekt als bereits gebucht erkannt

## TF 21 KOMBINATION SWEET-0021
status = VERIFIZIERT
hauptperson = Fischer, Olga
bearbeiter = sb-sim-08
bg_id = 18236
dauer_ms = 91
ids = vorgangsNr=957fede8-1f27-4099-b58f-fd11ae89ddf1, zahlungId=17012, kollege=sb-sim-02, letzteZahlungId=17012, bgId=18236, mitarbeiter=sb-sim-08

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
    params = [18236]
    erwartet = 1 | ist = 1
V2: OK | finale Zahlung wirksam (Vier-Augen-Warteschlange je nach Kassenlauf)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [17012]
    erwartet = ANGELEGT|BEREIT|UEBERGEBEN | ist = ANGELEGT
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [17012]
    erwartet = 525.95 | ist = 525.95
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [18236]
    erwartet = 50.00 | ist = 50
V5: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:29:49.952 sb-sim-08 | Vorgang anlegen (Single, Budget 575.95 EUR) -> OK — Vorgang 18236
2026-10-13 11:29:49.984 sb-sim-08 | 1 SozialFall/Fälle erfassen -> OK — Single, KdU 600.00 EUR
2026-10-13 11:29:50.021 sb-sim-08 | Auszahlung anordnen (2026-09) -> OK — 525.95 EUR (ANGELEGT)

## TF 22 KOMBINATION SWEET-0022
status = VERIFIZIERT
hauptperson = Thiel, Olga
bearbeiter = sb-sim-05
bg_id = 18235
dauer_ms = 153
ids = vorgangsNr=bdb20c11-33b9-49a3-9da5-41778f3472b1, zahlungId=17015, kollege=sb-sim-07, letzteZahlungId=17015, bgId=18235, mitarbeiter=sb-sim-05

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
    params = [18235]
    erwartet = 2 | ist = 2
V2: OK | finale Zahlung wirksam (Vier-Augen-Warteschlange je nach Kassenlauf)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [17015]
    erwartet = ANGELEGT|BEREIT|UEBERGEBEN | ist = ANGELEGT
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [17015]
    erwartet = 1134.84 | ist = 1134.84
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [18235]
    erwartet = 320.00 | ist = 320
V5: OK | genau ein erteilter Bewilligungsbescheid
    sql    = SELECT COUNT(*) FROM BESCHEID WHERE BG_ID = ?
    params = [18235]
    erwartet = 1 | ist = 1
V6: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:29:49.952 sb-sim-05 | Vorgang anlegen (Paar (2 × RBS 2), Budget 1454.84 EUR) -> OK — Vorgang 18235
2026-10-13 11:29:49.994 sb-sim-05 | 2 SozialFall/Fälle erfassen -> OK — Paar (2 × RBS 2), KdU 835.00 EUR
2026-10-13 11:29:50.026 sb-sim-05 | Bewilligungsbescheid erteilen (2026-03 .. 2026-12) -> OK — Bescheid BESCH-SWEET-0022-2026-1875 mit Monatsplan gespeichert
2026-10-13 11:29:50.065 sb-sim-05 | Auszahlung anordnen (2026-06) -> OK — 1134.84 EUR (ANGELEGT)

## TF 23 KOMBINATION SWEET-0023
status = VERIFIZIERT
hauptperson = Ismail, Marlene
bearbeiter = sb-sim-04
bg_id = 18234
dauer_ms = 122
ids = vorgangsNr=7ea7358d-b6e4-4b9f-96c8-64fdd0cfd32f, zahlungId=17014, kollege=sb-sim-08, letzteZahlungId=17014, bgId=18234, mitarbeiter=sb-sim-04

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
    params = [18234]
    erwartet = 3 | ist = 3
V2: OK | finale Zahlung wirksam (Vier-Augen-Warteschlange je nach Kassenlauf)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [17014]
    erwartet = ANGELEGT|BEREIT|UEBERGEBEN | ist = ANGELEGT
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [17014]
    erwartet = 372.59 | ist = 372.59
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [18234]
    erwartet = 140.00 | ist = 140
V5: OK | Vermögen nach Verbrauchsfortschreibung (§ 90)
    sql    = SELECT VERMOEGEN FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [18234]
    erwartet = 20500.00 | ist = 20500
V6: OK | dokumentierter Vermögenseinsatz an der Zahlung
    sql    = SELECT VERMOEGENSEINSATZ FROM ZAHLUNG WHERE ID = ?
    params = [17014]
    erwartet = 50.00 | ist = 50
V7: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:29:49.953 sb-sim-04 | Vorgang anlegen (Paar mit 1 Kind(ern), Budget 512.59 EUR, Vermögen 20550.00 EUR) -> OK — Vorgang 18234
2026-10-13 11:29:50.002 sb-sim-04 | 3 SozialFall/Fälle erfassen -> OK — Paar mit 1 Kind(ern), KdU 410.00 EUR
2026-10-13 11:29:50.035 sb-sim-04 | Auszahlung anordnen (2026-04) -> OK — 372.59 EUR (ANGELEGT)

## TF 24 KOMBINATION SWEET-0024
status = VERIFIZIERT
hauptperson = Vogt, Willi
bearbeiter = sb-sim-01
bg_id = 18237
dauer_ms = 107
ids = vorgangsNr=f2e5d09c-7550-40f0-99e2-97d560e06842, zahlungId=17013, kollege=sb-sim-03, letzteZahlungId=17013, bgId=18237, mitarbeiter=sb-sim-01

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
    params = [18237]
    erwartet = 1 | ist = 1
V2: OK | finale Zahlung wirksam (Vier-Augen-Warteschlange je nach Kassenlauf)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [17013]
    erwartet = ANGELEGT|BEREIT|UEBERGEBEN | ist = ANGELEGT
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [17013]
    erwartet = 443.71 | ist = 443.71
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [18237]
    erwartet = 270.00 | ist = 270
V5: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:29:49.966 sb-sim-01 | Vorgang anlegen (Single, Budget 713.71 EUR) -> OK — Vorgang 18237
2026-10-13 11:29:49.998 sb-sim-01 | 1 SozialFall/Fälle erfassen -> OK — Single, KdU 675.00 EUR
2026-10-13 11:29:50.034 sb-sim-01 | Auszahlung anordnen (2026-07) -> OK — 443.71 EUR (ANGELEGT)

## TF 25 KOMBINATION SWEET-0025
status = VERIFIZIERT
hauptperson = Dietrich, Norbert
bearbeiter = sb-sim-10
bg_id = 18239
dauer_ms = 156
ids = vorgangsNr=332458ce-1c4a-4800-b7ee-e95f7b85bda4, zahlungId=17016, kollege=sb-sim-08, letzteZahlungId=17016, bgId=18239, mitarbeiter=sb-sim-10

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
    params = [18239]
    erwartet = 4 | ist = 4
V2: OK | finale Zahlung freigegeben (oder bereits übergeben)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [17016]
    erwartet = BEREIT|UEBERGEBEN | ist = UEBERGEBEN
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [17016]
    erwartet = 2013.32 | ist = 2013.32
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [18239]
    erwartet = 150.00 | ist = 150
V5: OK | Freigabevermerk nennt den Kollegen (Vier-Augen)
    sql    = SELECT CASE WHEN FREIGEGEBEN_VON = ? THEN 'ok' ELSE 'abweichend' END FROM ZAHLUNG WHERE ID = ?
    params = [sb-sim-08, 17016]
    erwartet = ok | ist = ok
V6: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:29:49.982 sb-sim-10 | Vorgang anlegen (Alleinerziehend mit 3 Kind(ern), Budget 2163.32 EUR) -> OK — Vorgang 18239
2026-10-13 11:29:50.040 sb-sim-10 | 4 SozialFall/Fälle erfassen -> OK — Alleinerziehend mit 3 Kind(ern), KdU 765.00 EUR
2026-10-13 11:29:50.083 sb-sim-10 | Auszahlung anordnen (2026-07) -> OK — 2013.32 EUR (ANGELEGT)
2026-10-13 11:29:50.109 sb-sim-10 | Freigabe durch Kollegen (Vier-Augen) -> OK — freigegeben durch sb-sim-08

## TF 26 KOMBINATION SWEET-0026
status = VERIFIZIERT
hauptperson = Dietrich, Heinz
bearbeiter = sb-sim-03
bg_id = 18238
dauer_ms = 278
ids = mitarbeiter=sb-sim-03, bgId=18238, kollege=sb-sim-05, letzteZahlungId=17028, vorgangsNr=7f267d6c-7dd1-4444-a792-9083a8905afd, fallId=27944, zahlungId=17019

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
    params = [18238]
    erwartet = 3 | ist = 3
V2: OK | finale Zahlung wirksam (Vier-Augen-Warteschlange je nach Kassenlauf)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [17028]
    erwartet = ANGELEGT|BEREIT|UEBERGEBEN | ist = ANGELEGT
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [17028]
    erwartet = 1875.00 | ist = 1875
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [18238]
    erwartet = 240.00 | ist = 240
V5: OK | zwei Zahlungen (stornierte + korrigierte)
    sql    = SELECT COUNT(*) FROM ZAHLUNG WHERE BG_ID = ?
    params = [18238]
    erwartet = 2 | ist = 2
V6: OK | erste Zahlung STORNIERT
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [17019]
    erwartet = STORNIERT | ist = STORNIERT
V7: OK | Fall in Version 2 (optimistisches Locking)
    sql    = SELECT VERSION FROM SOZIAL_FALL WHERE ID = ?
    params = [27944]
    erwartet = 2 | ist = 2
V8: OK | genau ein erteilter Bewilligungsbescheid
    sql    = SELECT COUNT(*) FROM BESCHEID WHERE BG_ID = ?
    params = [18238]
    erwartet = 1 | ist = 1
V9: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:29:49.993 sb-sim-03 | Vorgang anlegen (Paar mit 1 Kind(ern), Budget 2115.00 EUR) -> OK — Vorgang 18238
2026-10-13 11:29:50.047 sb-sim-03 | 3 SozialFall/Fälle erfassen -> OK — Paar mit 1 Kind(ern), KdU 565.00 EUR
2026-10-13 11:29:50.084 sb-sim-03 | Bewilligungsbescheid erteilen (2026-06 .. 2026-09) -> OK — Bescheid BESCH-SWEET-0026-2026-1876 mit Monatsplan gespeichert
2026-10-13 11:29:50.119 sb-sim-03 | Auszahlung anordnen (2026-08) -> OK — 1815.00 EUR (ANGELEGT)
2026-10-13 11:29:50.156 sb-sim-03 | Anordnung sofort stornieren (Status ANGELEGT) -> OK — storniert — Monat wieder frei
2026-10-13 11:29:50.198 sb-sim-03 | Miete auf 490.00 korrigieren -> OK — Fall 27944 in Version 2
2026-10-13 11:29:50.236 sb-sim-03 | Korrigierte Auszahlung für denselben Monat -> OK — 1875.00 EUR

## TF 27 KOMBINATION SWEET-0027
status = VERIFIZIERT
hauptperson = Vogt, Ingrid
bearbeiter = sb-sim-07
bg_id = 18240
dauer_ms = 169
ids = vorgangsNr=2d5af0c4-b182-4a1d-95ea-9f2e105715a8, zahlungId=17018, kollege=sb-sim-08, letzteZahlungId=17018, bgId=18240, mitarbeiter=sb-sim-07

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
    params = [18240]
    erwartet = 3 | ist = 3
V2: OK | finale Zahlung wirksam (Vier-Augen-Warteschlange je nach Kassenlauf)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [17018]
    erwartet = ANGELEGT|BEREIT|UEBERGEBEN | ist = ANGELEGT
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [17018]
    erwartet = 946.32 | ist = 946.32
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [18240]
    erwartet = 140.00 | ist = 140
V5: OK | genau ein erteilter Bewilligungsbescheid
    sql    = SELECT COUNT(*) FROM BESCHEID WHERE BG_ID = ?
    params = [18240]
    erwartet = 1 | ist = 1
V6: OK | Vermögen nach Verbrauchsfortschreibung (§ 90)
    sql    = SELECT VERMOEGEN FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [18240]
    erwartet = 11000.00 | ist = 11000
V7: OK | dokumentierter Vermögenseinsatz an der Zahlung
    sql    = SELECT VERMOEGENSEINSATZ FROM ZAHLUNG WHERE ID = ?
    params = [17018]
    erwartet = 900.00 | ist = 900
V8: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:29:49.993 sb-sim-07 | Vorgang anlegen (Alleinerziehend mit 2 Kind(ern), Budget 1086.32 EUR, Vermögen 11900.00 EUR) -> OK — Vorgang 18240
2026-10-13 11:29:50.047 sb-sim-07 | 3 SozialFall/Fälle erfassen -> OK — Alleinerziehend mit 2 Kind(ern), KdU 430.00 EUR
2026-10-13 11:29:50.086 sb-sim-07 | Bewilligungsbescheid erteilen (2026-03 .. 2026-07) -> OK — Bescheid BESCH-SWEET-0027-2026-1877 mit Monatsplan gespeichert
2026-10-13 11:29:50.119 sb-sim-07 | Auszahlung anordnen (2026-03) -> OK — 946.32 EUR (ANGELEGT)

## TF 28 KOMBINATION SWEET-0028
status = VERIFIZIERT
hauptperson = Dietrich, Renate
bearbeiter = sb-sim-02
bg_id = 18242
dauer_ms = 186
ids = vorgangsNr=439a21c8-5cbe-4752-90da-0e01cba0105b, zahlungId=17023, kollege=sb-sim-10, letzteZahlungId=17023, bgId=18242, mitarbeiter=sb-sim-02

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
    params = [18242]
    erwartet = 3 | ist = 3
V2: OK | finale Zahlung freigegeben (oder bereits übergeben)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [17023]
    erwartet = BEREIT|UEBERGEBEN | ist = UEBERGEBEN
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [17023]
    erwartet = 1521.10 | ist = 1521.1
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [18242]
    erwartet = 260.00 | ist = 260
V5: OK | Freigabevermerk nennt den Kollegen (Vier-Augen)
    sql    = SELECT CASE WHEN FREIGEGEBEN_VON = ? THEN 'ok' ELSE 'abweichend' END FROM ZAHLUNG WHERE ID = ?
    params = [sb-sim-10, 17023]
    erwartet = ok | ist = ok
V6: OK | genau ein erteilter Bewilligungsbescheid
    sql    = SELECT COUNT(*) FROM BESCHEID WHERE BG_ID = ?
    params = [18242]
    erwartet = 1 | ist = 1
V7: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:29:50.028 sb-sim-02 | Vorgang anlegen (Alleinerziehend mit 2 Kind(ern), Budget 1781.10 EUR) -> OK — Vorgang 18242
2026-10-13 11:29:50.079 sb-sim-02 | 3 SozialFall/Fälle erfassen -> OK — Alleinerziehend mit 2 Kind(ern), KdU 460.00 EUR
2026-10-13 11:29:50.125 sb-sim-02 | Bewilligungsbescheid erteilen (2026-01 .. 2026-12) -> OK — Bescheid BESCH-SWEET-0028-2026-1878 mit Monatsplan gespeichert
2026-10-13 11:29:50.162 sb-sim-02 | Auszahlung anordnen (2026-03) -> OK — 1521.10 EUR (ANGELEGT)
2026-10-13 11:29:50.193 sb-sim-02 | Freigabe durch Kollegen (Vier-Augen) -> OK — freigegeben durch sb-sim-10

## TF 29 KOMBINATION SWEET-0029
status = VERIFIZIERT
hauptperson = Dietrich, Anna
bearbeiter = sb-sim-06
bg_id = 18241
dauer_ms = 146
ids = vorgangsNr=62e693be-fcf8-4d02-a26a-3e8f834aaa27, zahlungId=17017, kollege=sb-sim-02, letzteZahlungId=17017, bgId=18241, mitarbeiter=sb-sim-06

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
    params = [18241]
    erwartet = 2 | ist = 2
V2: OK | finale Zahlung freigegeben (oder bereits übergeben)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [17017]
    erwartet = BEREIT|UEBERGEBEN | ist = UEBERGEBEN
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [17017]
    erwartet = 917.80 | ist = 917.8
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [18241]
    erwartet = 390.00 | ist = 390
V5: OK | Freigabevermerk nennt den Kollegen (Vier-Augen)
    sql    = SELECT CASE WHEN FREIGEGEBEN_VON = ? THEN 'ok' ELSE 'abweichend' END FROM ZAHLUNG WHERE ID = ?
    params = [sb-sim-02, 17017]
    erwartet = ok | ist = ok
V6: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:29:50.028 sb-sim-06 | Vorgang anlegen (Paar (2 × RBS 2), Budget 1307.80 EUR) -> OK — Vorgang 18241
2026-10-13 11:29:50.069 sb-sim-06 | 2 SozialFall/Fälle erfassen -> OK — Paar (2 × RBS 2), KdU 420.00 EUR
2026-10-13 11:29:50.117 sb-sim-06 | Auszahlung anordnen (2026-04) -> OK — 917.80 EUR (ANGELEGT)
2026-10-13 11:29:50.143 sb-sim-06 | Freigabe durch Kollegen (Vier-Augen) -> OK — freigegeben durch sb-sim-02

## TF 30 KOMBINATION SWEET-0030
status = VERIFIZIERT
hauptperson = Egger, Frank
bearbeiter = sb-sim-09
bg_id = 18243
dauer_ms = 116
ids = vorgangsNr=41e0e2de-b176-4c87-bb7d-c7ee21f60e12, zahlungId=17020, kollege=sb-sim-02, letzteZahlungId=17020, bgId=18243, mitarbeiter=sb-sim-09

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
    params = [18243]
    erwartet = 3 | ist = 3
V2: OK | finale Zahlung wirksam (Vier-Augen-Warteschlange je nach Kassenlauf)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [17020]
    erwartet = ANGELEGT|BEREIT|UEBERGEBEN | ist = ANGELEGT
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [17020]
    erwartet = 650.96 | ist = 650.96
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [18243]
    erwartet = 330.00 | ist = 330
V5: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:29:50.044 sb-sim-09 | Vorgang anlegen (Alleinerziehend mit 2 Kind(ern), Budget 980.96 EUR) -> OK — Vorgang 18243
2026-10-13 11:29:50.093 sb-sim-09 | 3 SozialFall/Fälle erfassen -> OK — Alleinerziehend mit 2 Kind(ern), KdU 540.00 EUR
2026-10-13 11:29:50.133 sb-sim-09 | Auszahlung anordnen (2026-04) -> OK — 650.96 EUR (ANGELEGT)

## TF 31 KOMBINATION SWEET-0031
status = VERIFIZIERT
hauptperson = Uhl, Frank
bearbeiter = sb-sim-08
bg_id = 18244
dauer_ms = 171
ids = vorgangsNr=c8cd4d13-a135-4afd-ac7f-832b45aabe9b, zahlungId=17021, kollege=sb-sim-09, letzteZahlungId=17021, bgId=18244, mitarbeiter=sb-sim-08

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
    params = [18244]
    erwartet = 3 | ist = 3
V2: OK | finale Zahlung freigegeben (oder bereits übergeben)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [17021]
    erwartet = BEREIT|UEBERGEBEN | ist = UEBERGEBEN
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [17021]
    erwartet = 387.00 | ist = 387
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [18244]
    erwartet = 150.00 | ist = 150
V5: OK | genau eine Zahlung trotz Wiederholung
    sql    = SELECT COUNT(*) FROM ZAHLUNG WHERE BG_ID = ?
    params = [18244]
    erwartet = 1 | ist = 1
V6: OK | Freigabevermerk nennt den Kollegen (Vier-Augen)
    sql    = SELECT CASE WHEN FREIGEGEBEN_VON = ? THEN 'ok' ELSE 'abweichend' END FROM ZAHLUNG WHERE ID = ?
    params = [sb-sim-09, 17021]
    erwartet = ok | ist = ok
V7: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:29:50.042 sb-sim-08 | Vorgang anlegen (Paar mit 1 Kind(ern), Budget 537.00 EUR) -> OK — Vorgang 18244
2026-10-13 11:29:50.093 sb-sim-08 | 3 SozialFall/Fälle erfassen -> OK — Paar mit 1 Kind(ern), KdU 665.00 EUR
2026-10-13 11:29:50.133 sb-sim-08 | Auszahlung anordnen (2026-10) -> OK — 387.00 EUR (ANGELEGT)
2026-10-13 11:29:50.164 sb-sim-08 | Doppelklick: gleiche Vorgangsnummer erneut -> OK — korrekt als bereits gebucht erkannt
2026-10-13 11:29:50.193 sb-sim-08 | Freigabe durch Kollegen (Vier-Augen) -> OK — freigegeben durch sb-sim-09

## TF 32 KOMBINATION SWEET-0032
status = VERIFIZIERT
hauptperson = Brandt, Anna
bearbeiter = sb-sim-01
bg_id = 18246
dauer_ms = 155
ids = vorgangsNr=eb03fd82-5dfc-47e2-890b-60519abec756, zahlungId=17022, kollege=sb-sim-02, letzteZahlungId=17022, bgId=18246, mitarbeiter=sb-sim-01

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
    params = [18246]
    erwartet = 2 | ist = 2
V2: OK | finale Zahlung freigegeben (oder bereits übergeben)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [17022]
    erwartet = BEREIT|UEBERGEBEN | ist = UEBERGEBEN
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [17022]
    erwartet = 249.00 | ist = 249
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [18246]
    erwartet = 230.00 | ist = 230
V5: OK | Freigabevermerk nennt den Kollegen (Vier-Augen)
    sql    = SELECT CASE WHEN FREIGEGEBEN_VON = ? THEN 'ok' ELSE 'abweichend' END FROM ZAHLUNG WHERE ID = ?
    params = [sb-sim-02, 17022]
    erwartet = ok | ist = ok
V6: OK | Vermögen nach Verbrauchsfortschreibung (§ 90)
    sql    = SELECT VERMOEGEN FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [18246]
    erwartet = 20000.00 | ist = 20000
V7: OK | dokumentierter Vermögenseinsatz an der Zahlung
    sql    = SELECT VERMOEGENSEINSATZ FROM ZAHLUNG WHERE ID = ?
    params = [17022]
    erwartet = 900.00 | ist = 900
V8: OK | Wiedervorlagedatum exakt wie gesetzt
    sql    = SELECT COUNT(*) FROM BEDARFSGEMEINSCHAFT WHERE ID = ? AND WIEDERVORLAGE_AM = ?
    params = [18246, 2026-11-20]
    erwartet = 1 | ist = 1
V9: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:29:50.074 sb-sim-01 | Vorgang anlegen (Paar (2 × RBS 2), Budget 479.00 EUR, Vermögen 20900.00 EUR) -> OK — Vorgang 18246
2026-10-13 11:29:50.119 sb-sim-01 | 2 SozialFall/Fälle erfassen -> OK — Paar (2 × RBS 2), KdU 745.00 EUR
2026-10-13 11:29:50.149 sb-sim-01 | Auszahlung anordnen (2026-05) -> OK — 249.00 EUR (ANGELEGT)
2026-10-13 11:29:50.194 sb-sim-01 | Freigabe durch Kollegen (Vier-Augen) -> OK — freigegeben durch sb-sim-02

## TF 33 KOMBINATION SWEET-0033
status = VERIFIZIERT
hauptperson = Brandt, Clara
bearbeiter = sb-sim-04
bg_id = 18245
dauer_ms = 155
ids = vorgangsNr=3a1a7eb1-bff1-4662-bbaa-112cd3ff2d90, zahlungId=17025, kollege=sb-sim-01, letzteZahlungId=17025, bgId=18245, mitarbeiter=sb-sim-04

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
    params = [18245]
    erwartet = 3 | ist = 3
V2: OK | finale Zahlung wirksam (Vier-Augen-Warteschlange je nach Kassenlauf)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [17025]
    erwartet = ANGELEGT|BEREIT|UEBERGEBEN | ist = ANGELEGT
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [17025]
    erwartet = 1680.46 | ist = 1680.46
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [18245]
    erwartet = 200.00 | ist = 200
V5: OK | genau ein erteilter Bewilligungsbescheid
    sql    = SELECT COUNT(*) FROM BESCHEID WHERE BG_ID = ?
    params = [18245]
    erwartet = 1 | ist = 1
V6: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:29:50.077 sb-sim-04 | Vorgang anlegen (Paar mit 1 Kind(ern), Budget 1880.46 EUR) -> OK — Vorgang 18245
2026-10-13 11:29:50.128 sb-sim-04 | 3 SozialFall/Fälle erfassen -> OK — Paar mit 1 Kind(ern), KdU 770.00 EUR
2026-10-13 11:29:50.164 sb-sim-04 | Bewilligungsbescheid erteilen (2026-05 .. 2026-08) -> OK — Bescheid BESCH-SWEET-0033-2026-1879 mit Monatsplan gespeichert
2026-10-13 11:29:50.198 sb-sim-04 | Auszahlung anordnen (2026-08) -> OK — 1680.46 EUR (ANGELEGT)

## TF 34 KOMBINATION SWEET-0034
status = VERIFIZIERT
hauptperson = Zimmer, Ingrid
bearbeiter = sb-sim-05
bg_id = 18247
dauer_ms = 123
ids = vorgangsNr=b608d61a-1da3-42c1-9e09-6f20e4f0d513, zahlungId=17024, kollege=sb-sim-04, letzteZahlungId=17024, bgId=18247, mitarbeiter=sb-sim-05

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
    params = [18247]
    erwartet = 3 | ist = 3
V2: OK | finale Zahlung wirksam (Vier-Augen-Warteschlange je nach Kassenlauf)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [17024]
    erwartet = ANGELEGT|BEREIT|UEBERGEBEN | ist = ANGELEGT
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [17024]
    erwartet = 738.44 | ist = 738.44
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [18247]
    erwartet = 60.00 | ist = 60
V5: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:29:50.107 sb-sim-05 | Vorgang anlegen (Alleinerziehend mit 2 Kind(ern), Budget 798.44 EUR) -> OK — Vorgang 18247
2026-10-13 11:29:50.164 sb-sim-05 | 3 SozialFall/Fälle erfassen -> OK — Alleinerziehend mit 2 Kind(ern), KdU 430.00 EUR
2026-10-13 11:29:50.195 sb-sim-05 | Auszahlung anordnen (2026-07) -> OK — 738.44 EUR (ANGELEGT)

## TF 35 KOMBINATION SWEET-0035
status = VERIFIZIERT
hauptperson = Ismail, Willi
bearbeiter = sb-sim-10
bg_id = 18248
dauer_ms = 136
ids = vorgangsNr=435cdd13-20a7-4a9f-bd37-47f48c5dbdd6, zahlungId=17026, kollege=sb-sim-01, letzteZahlungId=17026, bgId=18248, mitarbeiter=sb-sim-10

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
    params = [18248]
    erwartet = 2 | ist = 2
V2: OK | finale Zahlung wirksam (Vier-Augen-Warteschlange je nach Kassenlauf)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [17026]
    erwartet = ANGELEGT|BEREIT|UEBERGEBEN | ist = ANGELEGT
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [17026]
    erwartet = 489.00 | ist = 489
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [18248]
    erwartet = 110.00 | ist = 110
V5: OK | genau eine Zahlung trotz Wiederholung
    sql    = SELECT COUNT(*) FROM ZAHLUNG WHERE BG_ID = ?
    params = [18248]
    erwartet = 1 | ist = 1
V6: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:29:50.141 sb-sim-10 | Vorgang anlegen (Paar (2 × RBS 2), Budget 599.00 EUR) -> OK — Vorgang 18248
2026-10-13 11:29:50.172 sb-sim-10 | 2 SozialFall/Fälle erfassen -> OK — Paar (2 × RBS 2), KdU 780.00 EUR
2026-10-13 11:29:50.216 sb-sim-10 | Auszahlung anordnen (2026-07) -> OK — 489.00 EUR (ANGELEGT)
2026-10-13 11:29:50.246 sb-sim-10 | Doppelklick: gleiche Vorgangsnummer erneut -> OK — korrekt als bereits gebucht erkannt

## TF 36 KOMBINATION SWEET-0036
status = VERIFIZIERT
hauptperson = Lindner, Volkmar
bearbeiter = sb-sim-07
bg_id = 18249
dauer_ms = 140
ids = vorgangsNr=b65ab115-4893-4968-967d-60f2e271c751, zahlungId=17030, kollege=sb-sim-10, letzteZahlungId=17030, bgId=18249, mitarbeiter=sb-sim-07

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
    params = [18249]
    erwartet = 3 | ist = 3
V2: OK | finale Zahlung wirksam (Vier-Augen-Warteschlange je nach Kassenlauf)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [17030]
    erwartet = ANGELEGT|BEREIT|UEBERGEBEN | ist = ANGELEGT
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [17030]
    erwartet = 726.99 | ist = 726.99
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [18249]
    erwartet = 210.00 | ist = 210
V5: OK | genau ein erteilter Bewilligungsbescheid
    sql    = SELECT COUNT(*) FROM BESCHEID WHERE BG_ID = ?
    params = [18249]
    erwartet = 1 | ist = 1
V6: OK | Vermögen nach Verbrauchsfortschreibung (§ 90)
    sql    = SELECT VERMOEGEN FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [18249]
    erwartet = 20500.00 | ist = 20500
V7: OK | dokumentierter Vermögenseinsatz an der Zahlung
    sql    = SELECT VERMOEGENSEINSATZ FROM ZAHLUNG WHERE ID = ?
    params = [17030]
    erwartet = 550.00 | ist = 550
V8: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:29:50.156 sb-sim-07 | Vorgang anlegen (Paar mit 1 Kind(ern), Budget 936.99 EUR, Vermögen 21050.00 EUR) -> OK — Vorgang 18249
2026-10-13 11:29:50.194 sb-sim-07 | 3 SozialFall/Fälle erfassen -> OK — Paar mit 1 Kind(ern), KdU 450.00 EUR
2026-10-13 11:29:50.223 sb-sim-07 | Bewilligungsbescheid erteilen (2026-01 .. 2026-05) -> OK — Bescheid BESCH-SWEET-0036-2026-1880 mit Monatsplan gespeichert
2026-10-13 11:29:50.267 sb-sim-07 | Auszahlung anordnen (2026-03) -> OK — 726.99 EUR (ANGELEGT)

## TF 37 KOMBINATION SWEET-0037
status = VERIFIZIERT
hauptperson = Brandt, Renate
bearbeiter = sb-sim-09
bg_id = 18250
dauer_ms = 100
ids = vorgangsNr=c6904d28-039c-4da7-aaf6-4754e9ef6086, zahlungId=17027, kollege=sb-sim-06, letzteZahlungId=17027, bgId=18250, mitarbeiter=sb-sim-09

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
    params = [18250]
    erwartet = 2 | ist = 2
V2: OK | finale Zahlung wirksam (Vier-Augen-Warteschlange je nach Kassenlauf)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [17027]
    erwartet = ANGELEGT|BEREIT|UEBERGEBEN | ist = ANGELEGT
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [17027]
    erwartet = 1656.07 | ist = 1656.07
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [18250]
    erwartet = 100.00 | ist = 100
V5: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:29:50.161 sb-sim-09 | Vorgang anlegen (Alleinerziehend mit 1 Kind(ern), Budget 1756.07 EUR) -> OK — Vorgang 18250
2026-10-13 11:29:50.198 sb-sim-09 | 2 SozialFall/Fälle erfassen -> OK — Alleinerziehend mit 1 Kind(ern), KdU 400.00 EUR
2026-10-13 11:29:50.234 sb-sim-09 | Auszahlung anordnen (2026-02) -> OK — 1656.07 EUR (ANGELEGT)

## TF 38 KOMBINATION SWEET-0038
status = VERIFIZIERT
hauptperson = Meyer, Frank
bearbeiter = sb-sim-06
bg_id = 18251
dauer_ms = 101
ids = vorgangsNr=27116dfb-0b85-4915-8bdd-f803d7d8de8c, zahlungId=17029, kollege=sb-sim-03, letzteZahlungId=17029, bgId=18251, mitarbeiter=sb-sim-06

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
    params = [18251]
    erwartet = 2 | ist = 2
V2: OK | finale Zahlung wirksam (Vier-Augen-Warteschlange je nach Kassenlauf)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [17029]
    erwartet = ANGELEGT|BEREIT|UEBERGEBEN | ist = ANGELEGT
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [17029]
    erwartet = 1781.98 | ist = 1781.98
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [18251]
    erwartet = 400.00 | ist = 400
V5: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:29:50.175 sb-sim-06 | Vorgang anlegen (Alleinerziehend mit 1 Kind(ern), Budget 2181.98 EUR) -> OK — Vorgang 18251
2026-10-13 11:29:50.221 sb-sim-06 | 2 SozialFall/Fälle erfassen -> OK — Alleinerziehend mit 1 Kind(ern), KdU 910.00 EUR
2026-10-13 11:29:50.250 sb-sim-06 | Auszahlung anordnen (2026-07) -> OK — 1781.98 EUR (ANGELEGT)

## TF 39 KOMBINATION SWEET-0039
status = VERIFIZIERT
hauptperson = Ismail, Clara
bearbeiter = sb-sim-02
bg_id = 18253
dauer_ms = 156
ids = vorgangsNr=a6493a47-53a6-4018-8243-3bd63bc62636, zahlungId=17035, kollege=sb-sim-09, letzteZahlungId=17035, bgId=18253, mitarbeiter=sb-sim-02

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
    params = [18253]
    erwartet = 2 | ist = 2
V2: OK | finale Zahlung wirksam (Vier-Augen-Warteschlange je nach Kassenlauf)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [17035]
    erwartet = ANGELEGT|BEREIT|UEBERGEBEN | ist = ANGELEGT
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [17035]
    erwartet = 98.02 | ist = 98.02
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [18253]
    erwartet = 200.00 | ist = 200
V5: OK | genau ein erteilter Bewilligungsbescheid
    sql    = SELECT COUNT(*) FROM BESCHEID WHERE BG_ID = ?
    params = [18253]
    erwartet = 1 | ist = 1
V6: OK | Vermögen nach Verbrauchsfortschreibung (§ 90)
    sql    = SELECT VERMOEGEN FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [18253]
    erwartet = 20000.00 | ist = 20000
V7: OK | dokumentierter Vermögenseinsatz an der Zahlung
    sql    = SELECT VERMOEGENSEINSATZ FROM ZAHLUNG WHERE ID = ?
    params = [17035]
    erwartet = 900.00 | ist = 900
V8: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:29:50.215 sb-sim-02 | Vorgang anlegen (Paar (2 × RBS 2), Budget 298.02 EUR, Vermögen 20900.00 EUR) -> OK — Vorgang 18253
2026-10-13 11:29:50.255 sb-sim-02 | 2 SozialFall/Fälle erfassen -> OK — Paar (2 × RBS 2), KdU 820.00 EUR
2026-10-13 11:29:50.300 sb-sim-02 | Bewilligungsbescheid erteilen (2026-10 .. 2026-11) -> OK — Bescheid BESCH-SWEET-0039-2026-1881 mit Monatsplan gespeichert
2026-10-13 11:29:50.335 sb-sim-02 | Auszahlung anordnen (2026-11) -> OK — 98.02 EUR (ANGELEGT)

## TF 40 KOMBINATION SWEET-0040
status = VERIFIZIERT
hauptperson = Brandt, Marlene
bearbeiter = sb-sim-08
bg_id = 18252
dauer_ms = 172
ids = vorgangsNr=5a69521d-6366-4987-8e32-0c3a9e26a320, zahlungId=17034, kollege=sb-sim-02, letzteZahlungId=17034, bgId=18252, mitarbeiter=sb-sim-08

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
    params = [18252]
    erwartet = 2 | ist = 2
V2: OK | finale Zahlung freigegeben (oder bereits übergeben)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [17034]
    erwartet = BEREIT|UEBERGEBEN | ist = BEREIT
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [17034]
    erwartet = 439.63 | ist = 439.63
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [18252]
    erwartet = 250.00 | ist = 250
V5: OK | Freigabevermerk nennt den Kollegen (Vier-Augen)
    sql    = SELECT CASE WHEN FREIGEGEBEN_VON = ? THEN 'ok' ELSE 'abweichend' END FROM ZAHLUNG WHERE ID = ?
    params = [sb-sim-02, 17034]
    erwartet = ok | ist = ok
V6: OK | genau ein erteilter Bewilligungsbescheid
    sql    = SELECT COUNT(*) FROM BESCHEID WHERE BG_ID = ?
    params = [18252]
    erwartet = 1 | ist = 1
V7: OK | Vermögen nach Verbrauchsfortschreibung (§ 90)
    sql    = SELECT VERMOEGEN FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [18252]
    erwartet = 10500.00 | ist = 10500
V8: OK | dokumentierter Vermögenseinsatz an der Zahlung
    sql    = SELECT VERMOEGENSEINSATZ FROM ZAHLUNG WHERE ID = ?
    params = [17034]
    erwartet = 600.00 | ist = 600
V9: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:29:50.215 sb-sim-08 | Vorgang anlegen (Alleinerziehend mit 1 Kind(ern), Budget 689.63 EUR, Vermögen 11100.00 EUR) -> OK — Vorgang 18252
2026-10-13 11:29:50.255 sb-sim-08 | 2 SozialFall/Fälle erfassen -> OK — Alleinerziehend mit 1 Kind(ern), KdU 580.00 EUR
2026-10-13 11:29:50.303 sb-sim-08 | Bewilligungsbescheid erteilen (2026-05 .. 2026-10) -> OK — Bescheid BESCH-SWEET-0040-2026-1882 mit Monatsplan gespeichert
2026-10-13 11:29:50.335 sb-sim-08 | Auszahlung anordnen (2026-08) -> OK — 439.63 EUR (ANGELEGT)
2026-10-13 11:29:50.356 sb-sim-08 | Freigabe durch Kollegen (Vier-Augen) -> OK — freigegeben durch sb-sim-02

## TF 41 KOMBINATION SWEET-0041
status = VERIFIZIERT
hauptperson = Petersen, Elke
bearbeiter = sb-sim-01
bg_id = 18255
dauer_ms = 172
ids = vorgangsNr=4df7d26c-3c03-4c46-885f-9d48cd9a4aa3, zahlungId=17036, kollege=sb-sim-02, letzteZahlungId=17036, bgId=18255, mitarbeiter=sb-sim-01

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
    params = [18255]
    erwartet = 2 | ist = 2
V2: OK | finale Zahlung wirksam (Vier-Augen-Warteschlange je nach Kassenlauf)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [17036]
    erwartet = ANGELEGT|BEREIT|UEBERGEBEN | ist = ANGELEGT
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [17036]
    erwartet = 580.14 | ist = 580.14
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [18255]
    erwartet = 210.00 | ist = 210
V5: OK | genau eine Zahlung trotz Wiederholung
    sql    = SELECT COUNT(*) FROM ZAHLUNG WHERE BG_ID = ?
    params = [18255]
    erwartet = 1 | ist = 1
V6: OK | genau ein erteilter Bewilligungsbescheid
    sql    = SELECT COUNT(*) FROM BESCHEID WHERE BG_ID = ?
    params = [18255]
    erwartet = 1 | ist = 1
V7: OK | Wiedervorlagedatum exakt wie gesetzt
    sql    = SELECT COUNT(*) FROM BEDARFSGEMEINSCHAFT WHERE ID = ? AND WIEDERVORLAGE_AM = ?
    params = [18255, 2027-05-02]
    erwartet = 1 | ist = 1
V8: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:29:50.231 sb-sim-01 | Vorgang anlegen (Paar (2 × RBS 2), Budget 790.14 EUR) -> OK — Vorgang 18255
2026-10-13 11:29:50.270 sb-sim-01 | 2 SozialFall/Fälle erfassen -> OK — Paar (2 × RBS 2), KdU 665.00 EUR
2026-10-13 11:29:50.316 sb-sim-01 | Bewilligungsbescheid erteilen (2026-02 .. 2026-06) -> OK — Bescheid BESCH-SWEET-0041-2026-1883 mit Monatsplan gespeichert
2026-10-13 11:29:50.346 sb-sim-01 | Auszahlung anordnen (2026-02) -> OK — 580.14 EUR (ANGELEGT)
2026-10-13 11:29:50.374 sb-sim-01 | Doppelklick: gleiche Vorgangsnummer erneut -> OK — korrekt als bereits gebucht erkannt

## TF 42 KOMBINATION SWEET-0042
status = VERIFIZIERT
hauptperson = Roth, Bernd
bearbeiter = sb-sim-04
bg_id = 18254
dauer_ms = 110
ids = vorgangsNr=406518d2-ae74-41c4-bdfb-b7298450edc5, zahlungId=17032, kollege=sb-sim-03, letzteZahlungId=17032, bgId=18254, mitarbeiter=sb-sim-04

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
    params = [18254]
    erwartet = 3 | ist = 3
V2: OK | finale Zahlung wirksam (Vier-Augen-Warteschlange je nach Kassenlauf)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [17032]
    erwartet = ANGELEGT|BEREIT|UEBERGEBEN | ist = ANGELEGT
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [17032]
    erwartet = 2109.93 | ist = 2109.93
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [18254]
    erwartet = 80.00 | ist = 80
V5: OK | Wiedervorlagedatum exakt wie gesetzt
    sql    = SELECT COUNT(*) FROM BEDARFSGEMEINSCHAFT WHERE ID = ? AND WIEDERVORLAGE_AM = ?
    params = [18254, 2027-03-07]
    erwartet = 1 | ist = 1
V6: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:29:50.231 sb-sim-04 | Vorgang anlegen (Alleinerziehend mit 2 Kind(ern), Budget 2189.93 EUR) -> OK — Vorgang 18254
2026-10-13 11:29:50.275 sb-sim-04 | 3 SozialFall/Fälle erfassen -> OK — Alleinerziehend mit 2 Kind(ern), KdU 665.00 EUR
2026-10-13 11:29:50.316 sb-sim-04 | Auszahlung anordnen (2026-11) -> OK — 2109.93 EUR (ANGELEGT)

## TF 43 KOMBINATION SWEET-0043
status = VERIFIZIERT
hauptperson = Petersen, Heinz
bearbeiter = sb-sim-05
bg_id = 18256
dauer_ms = 141
ids = vorgangsNr=36a0f5e0-5cd7-4517-988a-2294cb887a21, zahlungId=17031, kollege=sb-sim-10, letzteZahlungId=17031, bgId=18256, mitarbeiter=sb-sim-05

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
    params = [18256]
    erwartet = 2 | ist = 2
V2: OK | finale Zahlung freigegeben (oder bereits übergeben)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [17031]
    erwartet = BEREIT|UEBERGEBEN | ist = UEBERGEBEN
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [17031]
    erwartet = 438.64 | ist = 438.64
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [18256]
    erwartet = 80.00 | ist = 80
V5: OK | Freigabevermerk nennt den Kollegen (Vier-Augen)
    sql    = SELECT CASE WHEN FREIGEGEBEN_VON = ? THEN 'ok' ELSE 'abweichend' END FROM ZAHLUNG WHERE ID = ?
    params = [sb-sim-10, 17031]
    erwartet = ok | ist = ok
V6: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:29:50.231 sb-sim-05 | Vorgang anlegen (Paar (2 × RBS 2), Budget 518.64 EUR, Vermögen 20000.00 EUR) -> OK — Vorgang 18256
2026-10-13 11:29:50.270 sb-sim-05 | 2 SozialFall/Fälle erfassen -> OK — Paar (2 × RBS 2), KdU 505.00 EUR
2026-10-13 11:29:50.315 sb-sim-05 | Auszahlung anordnen (2026-11) -> OK — 438.64 EUR (ANGELEGT)
2026-10-13 11:29:50.342 sb-sim-05 | Freigabe durch Kollegen (Vier-Augen) -> OK — freigegeben durch sb-sim-10

## TF 44 KOMBINATION SWEET-0044
status = VERIFIZIERT
hauptperson = Egger, Gisela
bearbeiter = sb-sim-09
bg_id = 18257
dauer_ms = 95
ids = vorgangsNr=25f3b102-4014-4451-8553-1c69654b81bf, zahlungId=17033, kollege=sb-sim-05, letzteZahlungId=17033, bgId=18257, mitarbeiter=sb-sim-09

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
    params = [18257]
    erwartet = 2 | ist = 2
V2: OK | finale Zahlung wirksam (Vier-Augen-Warteschlange je nach Kassenlauf)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [17033]
    erwartet = ANGELEGT|BEREIT|UEBERGEBEN | ist = ANGELEGT
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [17033]
    erwartet = 1528.64 | ist = 1528.64
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [18257]
    erwartet = 360.00 | ist = 360
V5: OK | Vermögen nach Verbrauchsfortschreibung (§ 90)
    sql    = SELECT VERMOEGEN FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [18257]
    erwartet = 20000.00 | ist = 20000
V6: OK | dokumentierter Vermögenseinsatz an der Zahlung
    sql    = SELECT VERMOEGENSEINSATZ FROM ZAHLUNG WHERE ID = ?
    params = [17033]
    erwartet = 300.00 | ist = 300
V7: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:29:50.260 sb-sim-09 | Vorgang anlegen (Paar (2 × RBS 2), Budget 1888.64 EUR, Vermögen 20300.00 EUR) -> OK — Vorgang 18257
2026-10-13 11:29:50.303 sb-sim-09 | 2 SozialFall/Fälle erfassen -> OK — Paar (2 × RBS 2), KdU 805.00 EUR
2026-10-13 11:29:50.333 sb-sim-09 | Auszahlung anordnen (2026-10) -> OK — 1528.64 EUR (ANGELEGT)

## TF 45 KOMBINATION SWEET-0045
status = VERIFIZIERT
hauptperson = Egger, Ursula
bearbeiter = sb-sim-03
bg_id = 18258
dauer_ms = 172
ids = vorgangsNr=abe06cb5-c8bc-477d-a062-d5dc24e7d50e, zahlungId=17037, kollege=sb-sim-01, letzteZahlungId=17037, bgId=18258, mitarbeiter=sb-sim-03

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
    params = [18258]
    erwartet = 1 | ist = 1
V2: OK | finale Zahlung wirksam (Vier-Augen-Warteschlange je nach Kassenlauf)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [17037]
    erwartet = ANGELEGT|BEREIT|UEBERGEBEN | ist = ANGELEGT
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [17037]
    erwartet = 843.00 | ist = 843
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [18258]
    erwartet = 260.00 | ist = 260
V5: OK | genau eine Zahlung trotz Wiederholung
    sql    = SELECT COUNT(*) FROM ZAHLUNG WHERE BG_ID = ?
    params = [18258]
    erwartet = 1 | ist = 1
V6: OK | genau ein erteilter Bewilligungsbescheid
    sql    = SELECT COUNT(*) FROM BESCHEID WHERE BG_ID = ?
    params = [18258]
    erwartet = 1 | ist = 1
V7: OK | Vermögen nach Verbrauchsfortschreibung (§ 90)
    sql    = SELECT VERMOEGEN FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [18258]
    erwartet = 10000.00 | ist = 10000
V8: OK | dokumentierter Vermögenseinsatz an der Zahlung
    sql    = SELECT VERMOEGENSEINSATZ FROM ZAHLUNG WHERE ID = ?
    params = [17037]
    erwartet = 200.00 | ist = 200
V9: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:29:50.262 sb-sim-03 | Vorgang anlegen (Single, Budget 1103.00 EUR, Vermögen 10200.00 EUR) -> OK — Vorgang 18258
2026-10-13 11:29:50.294 sb-sim-03 | 1 SozialFall/Fälle erfassen -> OK — Single, KdU 480.00 EUR
2026-10-13 11:29:50.335 sb-sim-03 | Bewilligungsbescheid erteilen (2026-01 .. 2026-08) -> OK — Bescheid BESCH-SWEET-0045-2026-1884 mit Monatsplan gespeichert
2026-10-13 11:29:50.378 sb-sim-03 | Auszahlung anordnen (2026-03) -> OK — 843.00 EUR (ANGELEGT)
2026-10-13 11:29:50.405 sb-sim-03 | Doppelklick: gleiche Vorgangsnummer erneut -> OK — korrekt als bereits gebucht erkannt

## TF 46 KOMBINATION SWEET-0046
status = VERIFIZIERT
hauptperson = Thiel, Ursula
bearbeiter = sb-sim-06
bg_id = 18260
dauer_ms = 141
ids = vorgangsNr=e54ee011-b000-43bb-98e5-1dfe46eb05de, zahlungId=17038, kollege=sb-sim-10, letzteZahlungId=17038, bgId=18260, mitarbeiter=sb-sim-06

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
    params = [18260]
    erwartet = 2 | ist = 2
V2: OK | finale Zahlung wirksam (Vier-Augen-Warteschlange je nach Kassenlauf)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [17038]
    erwartet = ANGELEGT|BEREIT|UEBERGEBEN | ist = ANGELEGT
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [17038]
    erwartet = 60.14 | ist = 60.14
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [18260]
    erwartet = 250.00 | ist = 250
V5: OK | genau ein erteilter Bewilligungsbescheid
    sql    = SELECT COUNT(*) FROM BESCHEID WHERE BG_ID = ?
    params = [18260]
    erwartet = 1 | ist = 1
V6: OK | Vermögen nach Verbrauchsfortschreibung (§ 90)
    sql    = SELECT VERMOEGEN FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [18260]
    erwartet = 20000.00 | ist = 20000
V7: OK | dokumentierter Vermögenseinsatz an der Zahlung
    sql    = SELECT VERMOEGENSEINSATZ FROM ZAHLUNG WHERE ID = ?
    params = [17038]
    erwartet = 100.00 | ist = 100
V8: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:29:50.275 sb-sim-06 | Vorgang anlegen (Paar (2 × RBS 2), Budget 310.14 EUR, Vermögen 20100.00 EUR) -> OK — Vorgang 18260
2026-10-13 11:29:50.321 sb-sim-06 | 2 SozialFall/Fälle erfassen -> OK — Paar (2 × RBS 2), KdU 295.00 EUR
2026-10-13 11:29:50.348 sb-sim-06 | Bewilligungsbescheid erteilen (2026-08 .. 2026-12) -> OK — Bescheid BESCH-SWEET-0046-2026-1885 mit Monatsplan gespeichert
2026-10-13 11:29:50.392 sb-sim-06 | Auszahlung anordnen (2026-09) -> OK — 60.14 EUR (ANGELEGT)

## TF 47 KOMBINATION SWEET-0047
status = VERIFIZIERT
hauptperson = Wagner, Dieter
bearbeiter = sb-sim-10
bg_id = 18259
dauer_ms = 218
ids = vorgangsNr=893b7f1a-dcfe-41c3-928c-086e396903b8, zahlungId=17040, kollege=sb-sim-01, letzteZahlungId=17040, bgId=18259, mitarbeiter=sb-sim-10

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
    params = [18259]
    erwartet = 4 | ist = 4
V2: OK | finale Zahlung freigegeben (oder bereits übergeben)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [17040]
    erwartet = BEREIT|UEBERGEBEN | ist = BEREIT
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [17040]
    erwartet = 110.59 | ist = 110.59
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [18259]
    erwartet = 320.00 | ist = 320
V5: OK | Freigabevermerk nennt den Kollegen (Vier-Augen)
    sql    = SELECT CASE WHEN FREIGEGEBEN_VON = ? THEN 'ok' ELSE 'abweichend' END FROM ZAHLUNG WHERE ID = ?
    params = [sb-sim-01, 17040]
    erwartet = ok | ist = ok
V6: OK | genau ein erteilter Bewilligungsbescheid
    sql    = SELECT COUNT(*) FROM BESCHEID WHERE BG_ID = ?
    params = [18259]
    erwartet = 1 | ist = 1
V7: OK | Vermögen nach Verbrauchsfortschreibung (§ 90)
    sql    = SELECT VERMOEGEN FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [18259]
    erwartet = 21000.00 | ist = 21000
V8: OK | dokumentierter Vermögenseinsatz an der Zahlung
    sql    = SELECT VERMOEGENSEINSATZ FROM ZAHLUNG WHERE ID = ?
    params = [17040]
    erwartet = 250.00 | ist = 250
V9: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:29:50.275 sb-sim-10 | Vorgang anlegen (Paar mit 2 Kind(ern), Budget 430.59 EUR, Vermögen 21250.00 EUR) -> OK — Vorgang 18259
2026-10-13 11:29:50.335 sb-sim-10 | 4 SozialFall/Fälle erfassen -> OK — Paar mit 2 Kind(ern), KdU 830.00 EUR
2026-10-13 11:29:50.380 sb-sim-10 | Bewilligungsbescheid erteilen (2026-06 .. 2026-12) -> OK — Bescheid BESCH-SWEET-0047-2026-1887 mit Monatsplan gespeichert
2026-10-13 11:29:50.426 sb-sim-10 | Auszahlung anordnen (2026-09) -> OK — 110.59 EUR (ANGELEGT)
2026-10-13 11:29:50.465 sb-sim-10 | Freigabe durch Kollegen (Vier-Augen) -> OK — freigegeben durch sb-sim-01

## TF 48 KOMBINATION SWEET-0048
status = VERIFIZIERT
hauptperson = Brandt, Anna
bearbeiter = sb-sim-07
bg_id = 18261
dauer_ms = 171
ids = vorgangsNr=f99add67-5879-4b84-af68-b408f27c061b, zahlungId=17039, kollege=sb-sim-09, letzteZahlungId=17039, bgId=18261, mitarbeiter=sb-sim-07

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
    params = [18261]
    erwartet = 2 | ist = 2
V2: OK | finale Zahlung wirksam (Vier-Augen-Warteschlange je nach Kassenlauf)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [17039]
    erwartet = ANGELEGT|BEREIT|UEBERGEBEN | ist = ANGELEGT
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [17039]
    erwartet = 1019.66 | ist = 1019.66
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [18261]
    erwartet = 110.00 | ist = 110
V5: OK | genau eine Zahlung trotz Wiederholung
    sql    = SELECT COUNT(*) FROM ZAHLUNG WHERE BG_ID = ?
    params = [18261]
    erwartet = 1 | ist = 1
V6: OK | genau ein erteilter Bewilligungsbescheid
    sql    = SELECT COUNT(*) FROM BESCHEID WHERE BG_ID = ?
    params = [18261]
    erwartet = 1 | ist = 1
V7: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:29:50.292 sb-sim-07 | Vorgang anlegen (Paar (2 × RBS 2), Budget 1129.66 EUR) -> OK — Vorgang 18261
2026-10-13 11:29:50.335 sb-sim-07 | 2 SozialFall/Fälle erfassen -> OK — Paar (2 × RBS 2), KdU 860.00 EUR
2026-10-13 11:29:50.364 sb-sim-07 | Bewilligungsbescheid erteilen (2026-07 .. 2026-11) -> OK — Bescheid BESCH-SWEET-0048-2026-1886 mit Monatsplan gespeichert
2026-10-13 11:29:50.408 sb-sim-07 | Auszahlung anordnen (2026-10) -> OK — 1019.66 EUR (ANGELEGT)
2026-10-13 11:29:50.434 sb-sim-07 | Doppelklick: gleiche Vorgangsnummer erneut -> OK — korrekt als bereits gebucht erkannt

## TF 49 KOMBINATION SWEET-0049
status = VERIFIZIERT
hauptperson = Hoffmann, Gisela
bearbeiter = sb-sim-04
bg_id = 18262
dauer_ms = 183
ids = vorgangsNr=e93a63fc-bfe2-494a-9d72-abed322cef59, zahlungId=17045, kollege=sb-sim-06, letzteZahlungId=17045, bgId=18262, mitarbeiter=sb-sim-04

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
    params = [18262]
    erwartet = 2 | ist = 2
V2: OK | finale Zahlung freigegeben (oder bereits übergeben)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [17045]
    erwartet = BEREIT|UEBERGEBEN | ist = BEREIT
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [17045]
    erwartet = 511.16 | ist = 511.16
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [18262]
    erwartet = 150.00 | ist = 150
V5: OK | Freigabevermerk nennt den Kollegen (Vier-Augen)
    sql    = SELECT CASE WHEN FREIGEGEBEN_VON = ? THEN 'ok' ELSE 'abweichend' END FROM ZAHLUNG WHERE ID = ?
    params = [sb-sim-06, 17045]
    erwartet = ok | ist = ok
V6: OK | genau ein erteilter Bewilligungsbescheid
    sql    = SELECT COUNT(*) FROM BESCHEID WHERE BG_ID = ?
    params = [18262]
    erwartet = 1 | ist = 1
V7: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:29:50.340 sb-sim-04 | Vorgang anlegen (Paar (2 × RBS 2), Budget 661.16 EUR) -> OK — Vorgang 18262
2026-10-13 11:29:50.382 sb-sim-04 | 2 SozialFall/Fälle erfassen -> OK — Paar (2 × RBS 2), KdU 810.00 EUR
2026-10-13 11:29:50.425 sb-sim-04 | Bewilligungsbescheid erteilen (2026-05 .. 2026-08) -> OK — Bescheid BESCH-SWEET-0049-2026-1888 mit Monatsplan gespeichert
2026-10-13 11:29:50.470 sb-sim-04 | Auszahlung anordnen (2026-06) -> OK — 511.16 EUR (ANGELEGT)
2026-10-13 11:29:50.496 sb-sim-04 | Freigabe durch Kollegen (Vier-Augen) -> OK — freigegeben durch sb-sim-06

## TF 51 KOMBINATION SWEET-0051
status = VERIFIZIERT
hauptperson = Otto, Norbert
bearbeiter = sb-sim-05
bg_id = 18263
dauer_ms = 137
ids = vorgangsNr=200bb899-d982-4663-80f2-ca0a3d4813ef, zahlungId=17041, kollege=sb-sim-01, letzteZahlungId=17041, bgId=18263, mitarbeiter=sb-sim-05

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
    params = [18263]
    erwartet = 2 | ist = 2
V2: OK | finale Zahlung freigegeben (oder bereits übergeben)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [17041]
    erwartet = BEREIT|UEBERGEBEN | ist = BEREIT
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [17041]
    erwartet = 947.08 | ist = 947.08
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [18263]
    erwartet = 160.00 | ist = 160
V5: OK | Freigabevermerk nennt den Kollegen (Vier-Augen)
    sql    = SELECT CASE WHEN FREIGEGEBEN_VON = ? THEN 'ok' ELSE 'abweichend' END FROM ZAHLUNG WHERE ID = ?
    params = [sb-sim-01, 17041]
    erwartet = ok | ist = ok
V6: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:29:50.370 sb-sim-05 | Vorgang anlegen (Alleinerziehend mit 1 Kind(ern), Budget 1107.08 EUR, Vermögen 10500.00 EUR) -> OK — Vorgang 18263
2026-10-13 11:29:50.413 sb-sim-05 | 2 SozialFall/Fälle erfassen -> OK — Alleinerziehend mit 1 Kind(ern), KdU 570.00 EUR
2026-10-13 11:29:50.456 sb-sim-05 | Auszahlung anordnen (2026-04) -> OK — 947.08 EUR (ANGELEGT)
2026-10-13 11:29:50.480 sb-sim-05 | Freigabe durch Kollegen (Vier-Augen) -> OK — freigegeben durch sb-sim-01

## TF 52 KOMBINATION SWEET-0052
status = VERIFIZIERT
hauptperson = Lindner, Ursula
bearbeiter = sb-sim-02
bg_id = 18264
dauer_ms = 107
ids = vorgangsNr=15a77a10-8e4b-4360-a4fd-09c599764ee9, zahlungId=17044, kollege=sb-sim-06, letzteZahlungId=17044, bgId=18264, mitarbeiter=sb-sim-02

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
    params = [18264]
    erwartet = 2 | ist = 2
V2: OK | finale Zahlung wirksam (Vier-Augen-Warteschlange je nach Kassenlauf)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [17044]
    erwartet = ANGELEGT|BEREIT|UEBERGEBEN | ist = ANGELEGT
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [17044]
    erwartet = 206.34 | ist = 206.34
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [18264]
    erwartet = 270.00 | ist = 270
V5: OK | Vermögen nach Verbrauchsfortschreibung (§ 90)
    sql    = SELECT VERMOEGEN FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [18264]
    erwartet = 10500.00 | ist = 10500
V6: OK | dokumentierter Vermögenseinsatz an der Zahlung
    sql    = SELECT VERMOEGENSEINSATZ FROM ZAHLUNG WHERE ID = ?
    params = [17044]
    erwartet = 1550.00 | ist = 1550
V7: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:29:50.372 sb-sim-02 | Vorgang anlegen (Alleinerziehend mit 1 Kind(ern), Budget 476.34 EUR, Vermögen 12050.00 EUR) -> OK — Vorgang 18264
2026-10-13 11:29:50.413 sb-sim-02 | 2 SozialFall/Fälle erfassen -> OK — Alleinerziehend mit 1 Kind(ern), KdU 895.00 EUR
2026-10-13 11:29:50.456 sb-sim-02 | Auszahlung anordnen (2026-10) -> OK — 206.34 EUR (ANGELEGT)

## TF 53 KOMBINATION SWEET-0053
status = VERIFIZIERT
hauptperson = Egger, Paul
bearbeiter = sb-sim-08
bg_id = 18265
dauer_ms = 107
ids = vorgangsNr=8473c621-af0f-45d0-9ae5-be7e715ff599, zahlungId=17042, kollege=sb-sim-04, letzteZahlungId=17042, bgId=18265, mitarbeiter=sb-sim-08

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
    params = [18265]
    erwartet = 1 | ist = 1
V2: OK | finale Zahlung wirksam (Vier-Augen-Warteschlange je nach Kassenlauf)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [17042]
    erwartet = ANGELEGT|BEREIT|UEBERGEBEN | ist = ANGELEGT
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [17042]
    erwartet = 933.00 | ist = 933
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [18265]
    erwartet = 370.00 | ist = 370
V5: OK | Vermögen nach Verbrauchsfortschreibung (§ 90)
    sql    = SELECT VERMOEGEN FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [18265]
    erwartet = 10000.00 | ist = 10000
V6: OK | dokumentierter Vermögenseinsatz an der Zahlung
    sql    = SELECT VERMOEGENSEINSATZ FROM ZAHLUNG WHERE ID = ?
    params = [17042]
    erwartet = 200.00 | ist = 200
V7: OK | Wiedervorlagedatum exakt wie gesetzt
    sql    = SELECT COUNT(*) FROM BEDARFSGEMEINSCHAFT WHERE ID = ? AND WIEDERVORLAGE_AM = ?
    params = [18265, 2027-01-17]
    erwartet = 1 | ist = 1
V8: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:29:50.386 sb-sim-08 | Vorgang anlegen (Single, Budget 1303.00 EUR, Vermögen 10200.00 EUR) -> OK — Vorgang 18265
2026-10-13 11:29:50.421 sb-sim-08 | 1 SozialFall/Fälle erfassen -> OK — Single, KdU 570.00 EUR
2026-10-13 11:29:50.456 sb-sim-08 | Auszahlung anordnen (2026-04) -> OK — 933.00 EUR (ANGELEGT)

## TF 54 KOMBINATION SWEET-0054
status = VERIFIZIERT
hauptperson = Petersen, Karin
bearbeiter = sb-sim-09
bg_id = 18266
dauer_ms = 183
ids = mitarbeiter=sb-sim-09, bgId=18266, kollege=sb-sim-05, letzteZahlungId=17049, vorgangsNr=9f3211ef-b03f-480f-9f32-8ce5a7161ef6, fallId=28009, zahlungId=17043

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
    params = [18266]
    erwartet = 1 | ist = 1
V2: OK | finale Zahlung wirksam (Vier-Augen-Warteschlange je nach Kassenlauf)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [17049]
    erwartet = ANGELEGT|BEREIT|UEBERGEBEN | ist = ANGELEGT
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [17049]
    erwartet = 1653.00 | ist = 1653
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [18266]
    erwartet = 70.00 | ist = 70
V5: OK | zwei Zahlungen (stornierte + korrigierte)
    sql    = SELECT COUNT(*) FROM ZAHLUNG WHERE BG_ID = ?
    params = [18266]
    erwartet = 2 | ist = 2
V6: OK | erste Zahlung STORNIERT
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [17043]
    erwartet = STORNIERT | ist = STORNIERT
V7: OK | Fall in Version 2 (optimistisches Locking)
    sql    = SELECT VERSION FROM SOZIAL_FALL WHERE ID = ?
    params = [28009]
    erwartet = 2 | ist = 2
V8: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:29:50.386 sb-sim-09 | Vorgang anlegen (Single, Budget 1723.00 EUR) -> OK — Vorgang 18266
2026-10-13 11:29:50.421 sb-sim-09 | 1 SozialFall/Fälle erfassen -> OK — Single, KdU 970.00 EUR
2026-10-13 11:29:50.454 sb-sim-09 | Auszahlung anordnen (2026-03) -> OK — 1533.00 EUR (ANGELEGT)
2026-10-13 11:29:50.480 sb-sim-09 | Anordnung sofort stornieren (Status ANGELEGT) -> OK — storniert — Monat wieder frei
2026-10-13 11:29:50.514 sb-sim-09 | Miete auf 810.00 korrigieren -> OK — Fall 28009 in Version 2
2026-10-13 11:29:50.546 sb-sim-09 | Korrigierte Auszahlung für denselben Monat -> OK — 1653.00 EUR

## TF 55 KOMBINATION SWEET-0055
status = VERIFIZIERT
hauptperson = Zimmer, Ingrid
bearbeiter = sb-sim-01
bg_id = 18267
dauer_ms = 254
ids = mitarbeiter=sb-sim-01, bgId=18267, kollege=sb-sim-05, letzteZahlungId=17053, vorgangsNr=9f810225-8194-473d-ac88-693eb448fa23, fallId=28010, zahlungId=17047

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
    params = [18267]
    erwartet = 4 | ist = 4
V2: OK | finale Zahlung wirksam (Vier-Augen-Warteschlange je nach Kassenlauf)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [17053]
    erwartet = ANGELEGT|BEREIT|UEBERGEBEN | ist = ANGELEGT
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [17053]
    erwartet = 1449.77 | ist = 1449.77
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [18267]
    erwartet = 160.00 | ist = 160
V5: OK | zwei Zahlungen (stornierte + korrigierte)
    sql    = SELECT COUNT(*) FROM ZAHLUNG WHERE BG_ID = ?
    params = [18267]
    erwartet = 2 | ist = 2
V6: OK | erste Zahlung STORNIERT
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [17047]
    erwartet = STORNIERT | ist = STORNIERT
V7: OK | Fall in Version 2 (optimistisches Locking)
    sql    = SELECT VERSION FROM SOZIAL_FALL WHERE ID = ?
    params = [28010]
    erwartet = 2 | ist = 2
V8: OK | genau ein erteilter Bewilligungsbescheid
    sql    = SELECT COUNT(*) FROM BESCHEID WHERE BG_ID = ?
    params = [18267]
    erwartet = 1 | ist = 1
V9: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:29:50.402 sb-sim-01 | Vorgang anlegen (Alleinerziehend mit 3 Kind(ern), Budget 1609.77 EUR) -> OK — Vorgang 18267
2026-10-13 11:29:50.461 sb-sim-01 | 4 SozialFall/Fälle erfassen -> OK — Alleinerziehend mit 3 Kind(ern), KdU 950.00 EUR
2026-10-13 11:29:50.502 sb-sim-01 | Bewilligungsbescheid erteilen (2026-01 .. 2026-06) -> OK — Bescheid BESCH-SWEET-0055-2026-1889 mit Monatsplan gespeichert
2026-10-13 11:29:50.528 sb-sim-01 | Auszahlung anordnen (2026-02) -> OK — 1419.77 EUR (ANGELEGT)
2026-10-13 11:29:50.554 sb-sim-01 | Anordnung sofort stornieren (Status ANGELEGT) -> OK — storniert — Monat wieder frei
2026-10-13 11:29:50.594 sb-sim-01 | Miete auf 730.00 korrigieren -> OK — Fall 28010 in Version 2
2026-10-13 11:29:50.630 sb-sim-01 | Korrigierte Auszahlung für denselben Monat -> OK — 1449.77 EUR

## TF 56 KOMBINATION SWEET-0056
status = VERIFIZIERT
hauptperson = Wagner, Clara
bearbeiter = sb-sim-06
bg_id = 18268
dauer_ms = 121
ids = vorgangsNr=42da1827-da4f-4a31-b15c-7809d3388116, zahlungId=17046, kollege=sb-sim-07, letzteZahlungId=17046, bgId=18268, mitarbeiter=sb-sim-06

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
    params = [18268]
    erwartet = 1 | ist = 1
V2: OK | finale Zahlung freigegeben (oder bereits übergeben)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [17046]
    erwartet = BEREIT|UEBERGEBEN | ist = BEREIT
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [17046]
    erwartet = 299.50 | ist = 299.5
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [18268]
    erwartet = 210.00 | ist = 210
V5: OK | Freigabevermerk nennt den Kollegen (Vier-Augen)
    sql    = SELECT CASE WHEN FREIGEGEBEN_VON = ? THEN 'ok' ELSE 'abweichend' END FROM ZAHLUNG WHERE ID = ?
    params = [sb-sim-07, 17046]
    erwartet = ok | ist = ok
V6: OK | Vermögen nach Verbrauchsfortschreibung (§ 90)
    sql    = SELECT VERMOEGEN FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [18268]
    erwartet = 10000.00 | ist = 10000
V7: OK | dokumentierter Vermögenseinsatz an der Zahlung
    sql    = SELECT VERMOEGENSEINSATZ FROM ZAHLUNG WHERE ID = ?
    params = [17046]
    erwartet = 100.00 | ist = 100
V8: OK | Wiedervorlagedatum exakt wie gesetzt
    sql    = SELECT COUNT(*) FROM BEDARFSGEMEINSCHAFT WHERE ID = ? AND WIEDERVORLAGE_AM = ?
    params = [18268, 2026-12-29]
    erwartet = 1 | ist = 1
V9: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:29:50.418 sb-sim-06 | Vorgang anlegen (Single, Budget 509.50 EUR, Vermögen 10100.00 EUR) -> OK — Vorgang 18268
2026-10-13 11:29:50.450 sb-sim-06 | 1 SozialFall/Fälle erfassen -> OK — Single, KdU 405.00 EUR
2026-10-13 11:29:50.484 sb-sim-06 | Auszahlung anordnen (2026-10) -> OK — 299.50 EUR (ANGELEGT)
2026-10-13 11:29:50.510 sb-sim-06 | Freigabe durch Kollegen (Vier-Augen) -> OK — freigegeben durch sb-sim-07

## TF 57 KOMBINATION SWEET-0057
status = VERIFIZIERT
hauptperson = Fischer, Dieter
bearbeiter = sb-sim-03
bg_id = 18269
dauer_ms = 191
ids = vorgangsNr=5d9981df-8856-4635-b52e-0c27af72e8e0, zahlungId=17050, kollege=sb-sim-10, letzteZahlungId=17050, bgId=18269, mitarbeiter=sb-sim-03

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
    params = [18269]
    erwartet = 2 | ist = 2
V2: OK | finale Zahlung freigegeben (oder bereits übergeben)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [17050]
    erwartet = BEREIT|UEBERGEBEN | ist = BEREIT
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [17050]
    erwartet = 194.66 | ist = 194.66
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [18269]
    erwartet = 290.00 | ist = 290
V5: OK | Freigabevermerk nennt den Kollegen (Vier-Augen)
    sql    = SELECT CASE WHEN FREIGEGEBEN_VON = ? THEN 'ok' ELSE 'abweichend' END FROM ZAHLUNG WHERE ID = ?
    params = [sb-sim-10, 17050]
    erwartet = ok | ist = ok
V6: OK | genau ein erteilter Bewilligungsbescheid
    sql    = SELECT COUNT(*) FROM BESCHEID WHERE BG_ID = ?
    params = [18269]
    erwartet = 1 | ist = 1
V7: OK | Vermögen nach Verbrauchsfortschreibung (§ 90)
    sql    = SELECT VERMOEGEN FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [18269]
    erwartet = 20000.00 | ist = 20000
V8: OK | dokumentierter Vermögenseinsatz an der Zahlung
    sql    = SELECT VERMOEGENSEINSATZ FROM ZAHLUNG WHERE ID = ?
    params = [17050]
    erwartet = 1650.00 | ist = 1650
V9: OK | Wiedervorlagedatum exakt wie gesetzt
    sql    = SELECT COUNT(*) FROM BEDARFSGEMEINSCHAFT WHERE ID = ? AND WIEDERVORLAGE_AM = ?
    params = [18269, 2027-05-09]
    erwartet = 1 | ist = 1
V10: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:29:50.432 sb-sim-03 | Vorgang anlegen (Paar (2 × RBS 2), Budget 484.66 EUR, Vermögen 21650.00 EUR) -> OK — Vorgang 18269
2026-10-13 11:29:50.472 sb-sim-03 | 2 SozialFall/Fälle erfassen -> OK — Paar (2 × RBS 2), KdU 735.00 EUR
2026-10-13 11:29:50.512 sb-sim-03 | Bewilligungsbescheid erteilen (2026-07 .. 2026-07) -> OK — Bescheid BESCH-SWEET-0057-2026-1890 mit Monatsplan gespeichert
2026-10-13 11:29:50.548 sb-sim-03 | Auszahlung anordnen (2026-07) -> OK — 194.66 EUR (ANGELEGT)
2026-10-13 11:29:50.586 sb-sim-03 | Freigabe durch Kollegen (Vier-Augen) -> OK — freigegeben durch sb-sim-10

## TF 58 KOMBINATION SWEET-0058
status = VERIFIZIERT
hauptperson = Ismail, Karin
bearbeiter = sb-sim-07
bg_id = 18270
dauer_ms = 207
ids = mitarbeiter=sb-sim-07, bgId=18270, kollege=sb-sim-01, letzteZahlungId=17054, vorgangsNr=069d4130-cd00-4501-9aca-0d18055546bc, fallId=28017, zahlungId=17048

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
    params = [18270]
    erwartet = 2 | ist = 2
V2: OK | finale Zahlung wirksam (Vier-Augen-Warteschlange je nach Kassenlauf)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [17054]
    erwartet = ANGELEGT|BEREIT|UEBERGEBEN | ist = ANGELEGT
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [17054]
    erwartet = 544.27 | ist = 544.27
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [18270]
    erwartet = 330.00 | ist = 330
V5: OK | zwei Zahlungen (stornierte + korrigierte)
    sql    = SELECT COUNT(*) FROM ZAHLUNG WHERE BG_ID = ?
    params = [18270]
    erwartet = 2 | ist = 2
V6: OK | erste Zahlung STORNIERT
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [17048]
    erwartet = STORNIERT | ist = STORNIERT
V7: OK | Fall in Version 2 (optimistisches Locking)
    sql    = SELECT VERSION FROM SOZIAL_FALL WHERE ID = ?
    params = [28017]
    erwartet = 2 | ist = 2
V8: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:29:50.463 sb-sim-07 | Vorgang anlegen (Alleinerziehend mit 1 Kind(ern), Budget 874.27 EUR) -> OK — Vorgang 18270
2026-10-13 11:29:50.504 sb-sim-07 | 2 SozialFall/Fälle erfassen -> OK — Alleinerziehend mit 1 Kind(ern), KdU 450.00 EUR
2026-10-13 11:29:50.546 sb-sim-07 | Auszahlung anordnen (2026-07) -> OK — 394.27 EUR (ANGELEGT)
2026-10-13 11:29:50.571 sb-sim-07 | Anordnung sofort stornieren (Status ANGELEGT) -> OK — storniert — Monat wieder frei
2026-10-13 11:29:50.614 sb-sim-07 | Miete auf 420.00 korrigieren -> OK — Fall 28017 in Version 2
2026-10-13 11:29:50.646 sb-sim-07 | Korrigierte Auszahlung für denselben Monat -> OK — 544.27 EUR

## TF 59 KOMBINATION SWEET-0059
status = VERIFIZIERT
hauptperson = Dietrich, Heinz
bearbeiter = sb-sim-02
bg_id = 18271
dauer_ms = 145
ids = vorgangsNr=e460c73a-386f-4b07-8cc6-bed31b144374, zahlungId=17052, kollege=sb-sim-01, letzteZahlungId=17052, bgId=18271, mitarbeiter=sb-sim-02

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
    params = [18271]
    erwartet = 2 | ist = 2
V2: OK | finale Zahlung freigegeben (oder bereits übergeben)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [17052]
    erwartet = BEREIT|UEBERGEBEN | ist = BEREIT
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [17052]
    erwartet = 451.63 | ist = 451.63
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [18271]
    erwartet = 160.00 | ist = 160
V5: OK | Freigabevermerk nennt den Kollegen (Vier-Augen)
    sql    = SELECT CASE WHEN FREIGEGEBEN_VON = ? THEN 'ok' ELSE 'abweichend' END FROM ZAHLUNG WHERE ID = ?
    params = [sb-sim-01, 17052]
    erwartet = ok | ist = ok
V6: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:29:50.478 sb-sim-02 | Vorgang anlegen (Alleinerziehend mit 1 Kind(ern), Budget 611.63 EUR) -> OK — Vorgang 18271
2026-10-13 11:29:50.516 sb-sim-02 | 2 SozialFall/Fälle erfassen -> OK — Alleinerziehend mit 1 Kind(ern), KdU 820.00 EUR
2026-10-13 11:29:50.561 sb-sim-02 | Auszahlung anordnen (2026-02) -> OK — 451.63 EUR (ANGELEGT)
2026-10-13 11:29:50.586 sb-sim-02 | Freigabe durch Kollegen (Vier-Augen) -> OK — freigegeben durch sb-sim-01

## TF 60 KOMBINATION SWEET-0060
status = VERIFIZIERT
hauptperson = Jung, Clara
bearbeiter = sb-sim-08
bg_id = 18272
dauer_ms = 90
ids = vorgangsNr=829fe10b-a8a3-454e-9381-e252e61549f5, zahlungId=17051, kollege=sb-sim-03, letzteZahlungId=17051, bgId=18272, mitarbeiter=sb-sim-08

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
    params = [18272]
    erwartet = 2 | ist = 2
V2: OK | finale Zahlung wirksam (Vier-Augen-Warteschlange je nach Kassenlauf)
    sql    = SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?
    params = [17051]
    erwartet = ANGELEGT|BEREIT|UEBERGEBEN | ist = ANGELEGT
V3: OK | Auszahlungsbetrag entspricht der Berechnung
    sql    = SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?
    params = [17051]
    erwartet = 1231.66 | ist = 1231.66
V4: OK | Restbudget korrekt gemindert
    sql    = SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [18272]
    erwartet = 340.00 | ist = 340
V5: OK | Vermögen nach Verbrauchsfortschreibung (§ 90)
    sql    = SELECT VERMOEGEN FROM BEDARFSGEMEINSCHAFT WHERE ID = ?
    params = [18272]
    erwartet = 10500.00 | ist = 10500
V6: OK | dokumentierter Vermögenseinsatz an der Zahlung
    sql    = SELECT VERMOEGENSEINSATZ FROM ZAHLUNG WHERE ID = ?
    params = [17051]
    erwartet = 150.00 | ist = 150
V7: OK | Globale Laufverifikation
    sql    = ?
    params = ?
    erwartet = 0 Fehler | ist = 0 Fehler

### protokoll

2026-10-13 11:29:50.494 sb-sim-08 | Vorgang anlegen (Alleinerziehend mit 1 Kind(ern), Budget 1571.66 EUR, Vermögen 10650.00 EUR) -> OK — Vorgang 18272
2026-10-13 11:29:50.533 sb-sim-08 | 2 SozialFall/Fälle erfassen -> OK — Alleinerziehend mit 1 Kind(ern), KdU 835.00 EUR
2026-10-13 11:29:50.562 sb-sim-08 | Auszahlung anordnen (2026-03) -> OK — 1231.66 EUR (ANGELEGT)

