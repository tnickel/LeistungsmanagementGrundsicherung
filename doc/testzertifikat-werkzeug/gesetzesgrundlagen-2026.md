# Gesetzesgrundlagen der Berechnung (Prüfgrundlage für KI-Nachrechnungen)

Stand der Verifikation: **2026-10-10** gegen gesetze-im-internet.de (SGB XII, SGB X)
und buzer.de (BarbetragsVO). Vollständiges Verzeichnis mit Herkunftsnachweisen:
`doc/rechtsgrundlagen.md` im Hauptprojekt. Diese Kurzfassung dient als
Rechengrundlage für LLM-Nachrechnungen der testdokumentation.md.

## Regelbedarfsstufen (Anlage zu § 28 SGB XII, Jahr 2026)

| Stufe | Betrag | Typische Person |
|---|---|---|
| 1 | 563,00 EUR | alleinstehende erwachsene Person |
| 2 | 506,00 EUR | Ehegatte/Lebenspartner (je Person im Paar) |
| 3 | 451,00 EUR | erwachsene Person ohne eigene Wohnung im Haushalt |
| 4 | 471,00 EUR | Jugendliche 14–17 Jahre |
| 5 | 390,00 EUR | Kinder 6–13 Jahre |
| 6 | 357,00 EUR | Kinder bis 6 Jahre |

## Mehrbedarfe (§ 30 SGB XII)

| Mehrbedarf | Satz | Bedingung |
|---|---|---|
| Merkzeichen G (Abs. 1) | 17 % des EIGENEN Regelbedarfs | erhebliche Gehbehinderung |
| Schwangerschaft (Abs. 2) | 17 % des EIGENEN Regelbedarfs | ab der 13. Schwangerschaftswoche; **entfällt automatisch, wenn der Zahlungsmonat nach dem Entbindungsmonat liegt** |
| Alleinerziehende (Abs. 3) | 36 % der **Stufe 1** bei mind. einem Kind unter 7 J. oder 2–3 Kindern unter 16 J.; sonst 12 % je minderjährigem Kind | Bemessungsgrundlage ist IMMER die Stufe 1 (2026: 36 % = 202,68 EUR; 12 % = 67,56 EUR); Deckel 60 % der Stufe 1 = 337,80 EUR |
| Obergrenze (Abs. 6) | Summe der Zuschläge nach Abs. 1–5 höchstens der eigene Regelbedarf | Kappung auf den Regelbedarf |
| Warmwasser dezentral (Abs. 7) | 2,3 % / 2,3 % / – / 1,4 % / 1,2 % / 0,8 % je Stufe 1–6 | eigener Boiler/Durchlauferhitzer; **zählt NICHT in den Abs.-6-Deckel** |

## Wohnkosten — Kosten der Unterkunft und Heizung (§ 42a i. V. m. § 35 SGB XII)

- Anerkannt: Kaltmiete + kalte Betriebskosten + Heizkosten; je Person erfasst,
  für die Bedarfsgemeinschaft summiert.
- Bei zentraler Warmwasserbereitung kann örtlich ein Warmwasseranteil von den Heizkosten
  abgezogen werden (parametrisierbar, Standard: aus); bei dezentraler Erzeugung gibt es
  stattdessen den §-30-Abs.-7-Zuschlag — niemals beides.
- Gesamtangemessenheitsgrenze (§ 35 Abs. 7, örtlich parametrisierbar, Standard: aus):
  Überschreitung wird nur bis zur Grenze anerkannt.

## Einkommensanrechnung (§ 82 SGB XII)

- Absetzbeträge (Abs. 2): Steuern, Pflichtbeiträge zur Sozialversicherung, angemessene
  Versicherungsbeiträge, Ausgaben für die Erzielung des Einkommens.
- Erwerbsfreibetrag (Abs. 3): min(30 % des Brutto-Erwerbseinkommens, 50 % der Stufe 1
  = 281,50 EUR). Renten erhalten KEINEN Erwerbsfreibetrag.
- Anrechenbares Einkommen = max(0, Rente + Erwerb − Freibetrag − Absetzbeträge).

## Vermögen (§ 43 Abs. 1, § 90 Abs. 2 Nr. 9 SGB XII i. V. m. § 1 BarbetragsVO)

- Schonvermögen: 10.000 EUR je volljähriger leistungsberechtigter Person (Stufen 1–3)
  + 500 EUR je weiterer, überwiegend unterhaltener Person (Stufen 4–6).
- Verwertbares Vermögen = max(Vermögen − Schonvermögen, 0); mindert den Zahlbetrag.
  Mit der ersten Auszahlung verbraucht es sich (Bestand sinkt auf Schonvermögen;
  Einsatz je Zahlung dokumentiert); ab dem Folgemonat kein Abzug mehr. Storno bucht den
  dokumentierten Einsatz zurück.

## Ergebnis und Verfahren

- **Zahlbetrag = Gesamtbedarf − anrechenbares Einkommen − verwertbares Vermögen**
  (je Bedarfsgemeinschaft, Summe über alle Personen). ≤ 0 ⇒ KEIN_ANSPRUCH (§ 43 Abs. 1).
- Bescheid (§ 44 SGB XII): Zeitraum 1–12 Monate, Format JJJJ-MM, von ≤ bis; sobald ein
  Bescheid existiert, sind Zahlungen nur in bescheidgedeckten Monaten zulässig
  (sonst VALIDATION-Ablehnung).
- Rundung: je Zuschlag kaufmännisch (HALF_UP) auf Cent; Währung EUR.
- Zahlungsstatus: ANGELEGT → BEREIT (Vier-Augen-Freigabe durch andere Person) → UEBERGEBEN
  (Kassenlauf); STORNIERT gibt den Zahlungsmonat frei und bucht das Budget zurück.
- Optimistisches Locking: Speichern mit veraltetem Versionsstand wird mit KONKURRENZ
  abgewiesen (kein Lost Update).

## Bewusste Vereinfachungen (kein Zertifikatsgegenstand, in Doku ausgewiesen)

Karenzzeit/Absenkungsfrist (§ 35 Abs. 1 S. 2–9, Abs. 3 S. 2–5), § 82 Abs. 4/5
(Altersvorsorgefreibetrag, Aufwandsentschädigungen), § 43 Abs. 2 (Kapitalerträge),
§ 94 Abs. 1a (Unterhaltsrückgriff), § 44 Abs. 2 (Rückwirkung).
