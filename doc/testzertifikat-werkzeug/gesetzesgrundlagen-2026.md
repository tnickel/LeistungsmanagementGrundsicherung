# Gesetzesgrundlagen der Berechnung (Prüfgrundlage für KI-Nachrechnungen)

Stand der Verifikation: **2026-10-10** gegen gesetze-im-internet.de (SGB XII, SGB X)
und buzer.de (BarbetragsVO). Vollständiges Verzeichnis mit Herkunftsnachweisen:
`doc/rechtsgrundlagen.md` im Hauptprojekt. Diese Kurzfassung dient als
Rechengrundlage für LLM-Nachrechnungen der testdokumentation.md.

Nachprüfung **2026-10-15**: [§ 30 Abs. 3 SGB XII](https://www.gesetze-im-internet.de/sgb_12/__30.html),
[§ 35 Abs. 5 SGB XII](https://www.gesetze-im-internet.de/sgb_12/__35.html) und
[Wissenschaftliche Dienste WD 6-046/24, S. 8–11](https://www.bundestag.de/resource/blob/1019234/WD-6-046-24-pdf.pdf).
Die Ausarbeitung erläutert den Vergleich der günstigeren Alternative und überträgt ihn
auf § 30 Abs. 3; sie ist eine Auslegungshilfe, keine bindende Einzelfallentscheidung.

## Regelbedarfsstufen (Anlage zu § 28 SGB XII, Jahr 2026)

| Stufe | Betrag | Typische Person |
|---|---|---|
| 1 | 563,00 EUR | alleinstehende erwachsene Person |
| 2 | 506,00 EUR | Ehegatte/Lebenspartner (je Person im Paar) |
| 3 | 451,00 EUR | erwachsene Person mit Lebensunterhalt in stationärer Einrichtung (§ 27b) |
| 4 | 471,00 EUR | Jugendliche 14–17 Jahre |
| 5 | 390,00 EUR | Kinder 6–13 Jahre |
| 6 | 357,00 EUR | Kinder bis 6 Jahre |

## Mehrbedarfe (§ 30 SGB XII)

| Mehrbedarf | Satz | Bedingung |
|---|---|---|
| Merkzeichen G (Abs. 1) | 17 % des EIGENEN Regelbedarfs | erhebliche Gehbehinderung |
| Schwangerschaft (Abs. 2) | 17 % des EIGENEN Regelbedarfs | ab der 13. Schwangerschaftswoche; **entfällt automatisch, wenn der Zahlungsmonat nach dem Entbindungsmonat liegt** |
| Alleinerziehende (Abs. 3) | Günstigere Alternative: 36 % der **Stufe 1** bei mind. einem Kind unter 7 J. oder 2–3 Kindern unter 16 J., verglichen mit 12 % je minderjährigem Kind | Bemessungsgrundlage ist IMMER die Stufe 1 (2026: 36 % = 202,68 EUR; 12 % = 67,56 EUR); Deckel 60 % der Stufe 1 = 337,80 EUR |
| Obergrenze (Abs. 6) | Summe der Zuschläge nach Abs. 1–5 höchstens der eigene Regelbedarf | Kappung auf den Regelbedarf |
| Warmwasser dezentral (Abs. 7) | 2,3 % / 2,3 % / – / 1,4 % / 1,2 % / 0,8 % je Stufe 1–6 | eigener Boiler/Durchlauferhitzer; **zählt NICHT in den Abs.-6-Deckel** |

Die Standardrechnung setzt alleinige Pflege und Erziehung sowie keinen abweichenden
Einzelfallbedarf voraus. Kinderzahlen allein beweisen diese Voraussetzungen nicht;
die Öffnungsklausel und wechselnde Betreuung sind nicht eigenständig modelliert.
Vorab handgerechnete Kontrollwerte bei RBS 1 = 563 EUR: vier Minderjährige mit
U7/U16-Konstellation 48 % = 270,24 EUR, fünf 60 % = 337,80 EUR, sechs auf
60 % = 337,80 EUR gekappt. Auch wenn zugleich die 36-Prozent-Alternative erfüllt ist,
wird der höhere zulässige Betrag angesetzt.

## Wohnkosten — Kosten der Unterkunft und Heizung (§ 42a i. V. m. § 35 SGB XII)

- Anerkannt: Kaltmiete + kalte Betriebskosten + Heizkosten; je Person erfasst,
  für die Bedarfsgemeinschaft summiert.
- Zentrale Warmwasserkosten gehören zu den Heizkosten (§ 35 Abs. 5). Kein zusätzlicher
  Abzug; der frühere Abzugsparameter bleibt zur Kompatibilität einlesbar, wirkt aber nicht.
  Bei dezentraler Erzeugung wird stattdessen der §-30-Abs.-7-Mehrbedarf erfasst.
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
- Gesondert ausgewiesener **Produktvertrag**, keine zusätzliche behauptete Norm:
  In einem Haushalt ohne Erwachsene wird der Grundbetrag einmal mit 10.000 EUR angesetzt.
  Java-, JavaScript- und Python-Referenzen müssen diese implementierte Regel konsistent
  nachrechnen; ihre juristische Tragfähigkeit ist damit nicht unabhängig verifiziert.
- Verwertbares Vermögen = max(Vermögen − Schonvermögen, 0); mindert den Zahlbetrag.
  Mit der ersten Auszahlung verbraucht es sich (Bestand sinkt auf Schonvermögen;
  Einsatz je Zahlung dokumentiert); ab dem Folgemonat kein Abzug mehr. Storno bucht den
  dokumentierten Einsatz zurück.

## Ergebnis und gesetzliche Verfahrensregeln

- **Zahlbetrag = Gesamtbedarf − anrechenbares Einkommen − verwertbares Vermögen**
  (je Bedarfsgemeinschaft, Summe über alle Personen). ≤ 0 ⇒ KEIN_ANSPRUCH (§ 43 Abs. 1).
- [§ 44 Abs. 3 SGB XII](https://www.gesetze-im-internet.de/sgb_12/__44.html), online
  nachgeprüft am 15.10.2026: Regelbewilligungszeitraum zwölf Kalendermonate.
  Bei vorläufiger Entscheidung nach § 44a soll er auf höchstens sechs Monate verkürzt
  werden. Der gesetzliche Regelfall ist keine ausnahmslose Höchstfrist für jeden Einzelfall.
- Rundung: je Zuschlag kaufmännisch (HALF_UP) auf Cent; Währung EUR.

## Produkt- und Workflowregeln des Prüfgegenstands

Diese technischen Regeln beschreiben das implementierte Modell. Die Statusnamen,
Fehlercodes und die enge Storno-Abwehr sind keine eigenständigen Gesetzeszitate.

- Bescheiderteilung im Produkt: Zeitraum **1–12 Monate einschließlich beider
  Randmonate**, Format JJJJ-MM, von ≤ bis; Überschreitung, falsches Format oder
  umgekehrte Reihenfolge führen zu **VALIDATION**. Die feste Produktgrenze orientiert
  sich am Regelfall des § 44 Abs. 3; vorläufige Entscheidungen und gesetzlich mögliche
  Ausnahmezeiträume sind nicht gesondert modelliert. Ein 22-Monats-Auftrag muss daher
  als Produktvalidierung unverändert abgewiesen werden.
- Sobald ein Bescheid existiert, sind Zahlungen nur in bescheidgedeckten Monaten
  zulässig (sonst VALIDATION-Ablehnung).
- Zahlungsstatus: ANGELEGT → BEREIT (Vier-Augen-Freigabe durch andere Person) → UEBERGEBEN
  (Kassenlauf). **Storno ist nur aus ANGELEGT oder BEREIT zulässig**. Für UEBERGEBEN
  (oder bereits STORNIERT) wird Storno mit **VALIDATION** ohne Fachänderung abgewiesen;
  nach Kassenübergabe ist ein getrenntes Korrekturverfahren nötig, das hier nicht
  implementiert ist. Erfolgreicher Storno setzt STORNIERT, gibt den Zahlungsmonat
  für eine neue Vorgangsnummer frei und bucht Budget sowie den dokumentierten
  Vermögenseinsatz in derselben Transaktion zurück. Der vorherige Zahlbetrag darf
  durch einen Storno-Replay nicht erneut zurückgebucht werden.
- Optimistisches Locking: Speichern mit veraltetem Versionsstand wird mit KONKURRENZ
  abgewiesen (kein Lost Update).

## Bewusste Vereinfachungen (kein Zertifikatsgegenstand, in Doku ausgewiesen)

Karenzzeit/Absenkungsfrist (§ 35 Abs. 1 S. 2–9, Abs. 3 S. 2–5), § 82 Abs. 4/5
(Altersvorsorgefreibetrag, Aufwandsentschädigungen), § 43 Abs. 2 (Kapitalerträge),
§ 94 Abs. 1a (Unterhaltsrückgriff), § 44 Abs. 2 (Rückwirkung).
