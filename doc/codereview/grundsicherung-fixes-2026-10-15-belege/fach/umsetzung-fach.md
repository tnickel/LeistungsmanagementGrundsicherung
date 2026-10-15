# Fachkorrekturen und testsweet-Erweiterung

## Umgesetzt

- GR-02: Standardberechnung vergleicht die erfüllte 36-Prozent-Alternative mit zwölf Prozent je minderjährigem Kind und berücksichtigt den 60-Prozent-Deckel. Produktionsrechnung sowie unabhängige Java-, Node- und Python-Referenzen korrigiert.
- GR-03: Zentrale Warmwasserkosten bleiben Heizbedarf. Historischer `KduParameter.warmwasserAbzugHeizungProzent` wird weiterhin angenommen und validiert, erzeugt aber keinen Abzug mehr. Angemessenheitsgrenze und dezentrale Mehrbedarfe bleiben getrennte Rechenregeln.
- Feste Simulatorfälle TF13–TF15 kombinieren vier/fünf/sechs Minderjährige mit gleichzeitig erfüllter U7-/U16-Alternative und passenden Kinderstufen. TF17 prüft zentrale Warmwasserkosten mit historischem Parameter 20 Prozent. Die vier Auszahlungs-SQL-Sollwerte sind vorab handgerechnete Konstanten, keine Ableitung aus dem Produktrechner. Herleitung: `handwerte-vor-umsetzung.md`.
- Testsweet-Rechenmaterial, PDF-/Markdown-Erklärung und übermitteltes Gesetzesgrundlagen-Dokument beschreiben die korrigierten Fachregeln.
- Zusätzlicher Befund der vorbestehenden echten GLM-Prüfung: Storno-Zwischenstände werden unmittelbar nach dem committeten Storno und vor Mietekorrektur/Neuauszahlung in einer eigenen Oracle-READ-ONLY-Transaktion gelesen. Zwei echte Abfragen gegen `BEDARFSGEMEINSCHAFT` teilen denselben Snapshot und werden mit den vorab bekannten Anlagewerten verglichen. SQL, BG-ID, Soll und Ist erscheinen als tatsächliche Auftrag-Prüfpunkte. NIO/SQL-Fehler beendet den Worker-Auftrag vor weiterer Fachmutation. Die vorherigen `SELECT ? FROM DUAL`-Scheinnachweise des Zwischenstands und beobachteten Servicewerte aus dem Kontext sind entfernt.
- § 44 Abs. 3 und die Storno-Regel sind ausdrücklich getrennt: Gesetz regelt den zwölfmonatigen Regelfall bzw. bei vorläufigen Entscheidungen eine Soll-Verkürzung auf höchstens sechs Monate; das Produkt begrenzt eng auf 1–12 Monate. ANGELEGT/BEREIT-Storno, UEBERGEBEN-Abwehr und Rückbuchung sind dokumentierter Produktworkflow, keine erfundenen gesetzlichen Statusnormen.

## Zusätzliche Regressionen

- `LeistungsberechnungTest`: Vergleichskonstellationen 4/1/2, 5/0/3, 6/1/3; zentraler Heizbedarf 1.063 EUR statt 1.043 EUR.
- `LlmCalculationReferenceTest`: dieselben Handwerte und zentrale Warmwasserkosten trotz 20-Prozent-Altparameter; gemeinsame BG-Grenze mit 500/100 EUR statt 492,50/107,50 EUR.
- `SzenarioAbdeckungTest`: ausdrückliche Kindermerkmale, feste SQL-Sollwerte und Zurückweisung der früheren falschen Fachwerte.
- `StornoZwischenstandTest`: zeitliche Position und Tabellenabfragen, gemeinsamer Lesesnapshot, protokollierte ID/Soll/Ist und Abbruch vor weiterer Mutation bei falschem Persistenzwert oder SQL-Fehler.
- Neue eigenständige `llm-reference.test.js`: neun Tests einschließlich Beanstandung der früheren 202,68-EUR-Mehrbedarfe sowie 20/480/1.043-EUR-Warmwasserwerte.
- Python-Eigentests: zwei zusätzliche Testmethoden, aktualisierte Altparameter-/BG-Grenzen-Regression.

## Bisher tatsächlich ausgeführt

15.10.2026, nach Quellenfreigabe durch den koordinierenden Agenten:

- `node --test doc/testzertifikat-werkzeug/llm-reference.test.js`: **9/9 bestanden**, keine Skips.
- `python -m unittest discover -s doc/testzertifikat-werkzeug -p test_nachrechnen.py -v`: **11/11 bestanden**, keine Skips.
- `git diff --check`: keine Whitespacefehler.

Kein eigener Maven-/DB-/Simulator-/GLM-Lauf wurde gestartet. Die Java- und tatsächliche Oracle-Verifikation ist Aufgabe des zentral koordinierten Gesamtlaufs. Diese Datei behauptet dafür noch kein Ergebnis.

## Quellen und Grenzen

Am 15.10.2026 frisch online gegengeprüft: [§ 30 SGB XII](https://www.gesetze-im-internet.de/sgb_12/__30.html), [§ 35 SGB XII](https://www.gesetze-im-internet.de/sgb_12/__35.html), [§ 44 SGB XII](https://www.gesetze-im-internet.de/sgb_12/__44.html) und [WD 6-046/24, S. 8–11](https://www.bundestag.de/resource/blob/1019234/WD-6-046-24-pdf.pdf). Die Ausarbeitung ist eine Auslegungshilfe, keine bindende Einzelfallentscheidung. Tatsächliche alleinige Betreuung, abweichender Einzelfallbedarf und Wechselmodell sind nicht eigenständig modelliert. Kinderzahlen allein beweisen diese Voraussetzungen nicht.

Die zuerst vorbereiteten 13 Kopien samt Quellhashmanifest dokumentieren die Einhaltung der anfänglichen Quellenänderungssperre. Spätere Änderungen der echten Quellen einschließlich zusätzlicher Storno-Zwischenprüfung wurden erst nach ausdrücklicher Freigabe vorgenommen. Die vorbereiteten Kopien sind deshalb kein identischer Snapshot des endgültigen Reparaturstands.
