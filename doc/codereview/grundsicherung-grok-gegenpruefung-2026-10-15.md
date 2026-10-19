# Kritische Gegenprüfung und Reparaturen zum Grok-Review vom 15.10.2026

Ausgangspunkt ist `37abfb6` auf `main`. Die vorhandene Datei
[grundsicherung-gesamt-review-2026-10-15-k4p9.md](grundsicherung-gesamt-review-2026-10-15-k4p9.md)
enthält inzwischen **16 Befunde**; die mitgeteilte Kurzfassung mit einem Befund
beschreibt einen früheren Zwischenstand. Der Originalbericht bleibt unverändert.

Die Gegenprüfung führt zu **zwölf Korrekturen**, drei nicht bestätigten
Reparaturforderungen und einer weiterhin offenen Zuständigkeitsgrenze. Die
Bezeichnung „bestätigt“ im Fremdbericht wurde nicht als Beweis übernommen.
Kern, Fachlogik und HTTP/UI wurden parallel geprüft, Änderungen an gemeinsamen
Dateien abgestimmt und anschließend gemeinsam gebaut und getestet.

## Urteil zu allen Befunden

| ID | Gegenprüfung und Ergebnis |
|---|---|
| FB-01 README | **Behoben.** Schwangerschaft nach der zwölften Woche bis zum Ende des Entbindungsmonats; dokumentierter Günstigervergleich; Deckel nur für Abs. 1–5; kein zentraler Warmwasserabzug. Die Berechnung wurde dafür nicht erneut verändert. |
| FB-02 Task-Abbruch | **Bestätigt, falsche Ergebnismeldung korrigiert.** Abbruch/Deadline werden als unbekannter Ausgang gemeldet: `TaskOutcomeUnknownException`, HTTP `409 AUSGANG_UNBEKANNT`. Vor noch ausstehendem Commit wird auch der Interrupt geprüft. UI lädt nur lesend nach und behält die Vorgangsnummer je Benutzer/BG/Monat über Reloads. Weder Abbruch noch Nachladen beweisen einen Rollback bereits erfolgter innerer Commits. |
| FB-03 Deckendes Vermögen | **Der behauptete gesetzliche Sollzustand ist nicht bestätigt.** Eine abgelehnte Anordnung belegt keinen tatsächlichen Vermögensverbrauch. Kein automatisches Absenken durch Nullzahlungsversuche. Oracle-Regression prüft unveränderte Bestände bei wiederholter Ablehnung und eine erfolgreiche Zahlung erst nach erfasster Bestandsänderung. Die schon vorher vorhandene Einmalfortschreibung bei positiver Zahlung bleibt eine separate Produktannahme, siehe unten. |
| FB-04 Objektzuständigkeit | **Reale, weiterhin offene Modellgrenze.** Alle Sachbearbeiter erreichen den gemeinsamen Arbeitsbestand; Team-/Fallzuständigkeit ist nicht implementiert und war bereits als CR-035 B/C offen. Kein Beleg einer umgangenen vorhandenen Zuständigkeitsregel. Keine erfundene Eigentümerregel, die gemeinsame Bearbeitung und Vier-Augen-Freigabe verändert. Für einen Betrieb mit getrennten Zuständigkeiten reicht dieser Stand nicht. |
| FB-05 Saga-Schrittindex | **Behoben.** Namen werden bereits mit der PENDING-Liste gespeichert. Recovery wählt die Kompensation über den persistierten Namen in ursprünglicher Reihenfolge. Unbekannte/namenlose Wirkungen bleiben FAILED mit Payload; kein Rückfall auf den heutigen Listenindex. |
| FB-06 Große IN-Liste | **Für Oracle 19c behoben.** Höchstens 1.000 IDs pro Update, dieselbe Verbindung und Transaktion. Mengenabweichung erzwingt vollständigen Rollback. Die Behauptung derselben Grenze auf der aktuellen Free-Version ist zu weit: die aktuelle Oracle-Dokumentation nennt 65.535 Ausdrücke. |
| FB-07 Vorschau-Monat | **Behoben.** `GET /api/bedarfsgemeinschaften/{id}?monat=JJJJ-MM`, Serviceüberladung und Rückgabefeld `berechnungsmonat`. Monatswechsel lädt die Vorschau neu. Ungültige/mehrdeutige Monatsparameter werden vor dem Fachaufruf abgewiesen. |
| FB-08 Änderungsbescheid | **Technische Sackgasse behoben.** Expliziter PUT/Serviceweg ersetzt den alten Status und erteilt einen neuen vollständigen Monatsplan atomar unter BG-Sperre. Alte Snapshotdaten bleiben unverändert; frühere Monatswerte werden übernommen. Vorhandene Anordnungen im Änderungsabschnitt, fehlende historische Präfixeingaben und ungeklärter Präfix-Vermögenseinsatz sperren die automatische Änderung. Kein vollständiges Verwaltungsverfahren automatisiert. |
| FB-09 Kinderzähler | **Widersprüche und ungeklärte Mehrfachzuordnung abgesichert.** Vollständige Eingaben müssen `0 ≤ unter 7 ≤ unter 16 ≤ minderjährig ≤ 99` erfüllen. Mehrere beanspruchende Personen werden ohne Kinderzuordnung fachlich abgewiesen. Ein allgemeines gesetzliches Ein-Eltern-Verbot und die zwingende Mitgliedschaft aller Kinder in der Leistungs-BG sind nicht belegt. |
| FB-10 Kontensegment im Fehler | **Behoben.** Fehlermeldungen nennen nur Position, Format bzw. erlaubte Rollen. Auch ein durch verschobene Trennzeichen im Rollenfeld gelandetes Passwort wird nicht ausgegeben. |
| FB-11 Write-Ahead-Quittung | **Behoben.** Unklarer Write-Ahead-Ausgang bleibt FAILED mit erhaltenem Payload und ist wiederanlauffähig. Die Fachaktion beginnt erst nach bestätigtem Write-Ahead. |
| FB-12 Payload-Vorscan | **Behoben.** Ungesperrter Scan liest nur Kopfmetadaten. Payload, Schrittstatus und Namen werden erst unter exklusivem Claim gelesen. |
| FB-13 Leere Replay-Bankfelder | **Kein bestätigter Vertragsfehler.** Bankfelder sind optionale Overrides. Ohne Override wird dieselbe unveränderliche Zahlungszeile zurückgegeben; explizit abweichende Bankdaten werden weiterhin abgewiesen. Neue Oracle-Regression prüft dies auch nach Stammdatenwechsel. |
| FB-14 Service-Storno | **Keine bestätigte Umgehung des authentifizierten Eintritts.** HTTP verlangt Admin-Rolle vor dem Serviceaufruf und zusätzlich im Handler. Direkter Java-Code liegt innerhalb der bestehenden Vertrauensgrenze. Eine Kennung oder ein frei übergebenes Admin-Boolean authentifiziert keinen Aufrufer; ein gemeinsamer authentifizierter Servicekontext wäre ein Architekturausbau. |
| FB-15 Snapshot-Eingaben | **Für neue Snapshots behoben.** Merkmale, Kinderzähler, rohe KdU, Absetzungen, Entbindungsmonat und Version werden geschrieben und wieder gelesen. Das eingefrorene Druck-HTML bleibt unverändert. Alte Snapshots bleiben mit der bisherigen Minimalfallrekonstruktion lesbar; fehlende historische Merkmale sind daraus nicht rekonstruierbar. Solche Präfixmonate werden nicht automatisch in einen neuen Änderungsplan übernommen. |
| FB-16 Frame-Schutz | **Behoben.** `frame-ancestors 'none'` auf Oberfläche, Druck und darstellbaren Fehler-/API-Antworten. |

## Fachliche Gegenbelege und Grenzen

Die vier README-Widersprüche sind am amtlichen Text von
[§ 30 SGB XII](https://www.gesetze-im-internet.de/sgb_12/__30.html) und
[§ 35 Abs. 5 SGB XII](https://www.gesetze-im-internet.de/sgb_12/__35.html)
nachvollziehbar. § 30 wurde zusätzlich direkt als amtliche HTML-Fassung abgerufen,
nachdem der Browserabruf technisch scheiterte. Die
[Wissenschaftlichen Dienste, WD 6-046/24, Seiten 8–11](https://www.bundestag.de/resource/blob/1019234/WD-6-046-24-pdf.pdf)
übertragen den Günstigervergleich ausdrücklich auf § 30 Abs. 3. Die engere
Wortlautlesart wird in der README daneben genannt; die Ausarbeitung ist keine
bindende Gerichtsentscheidung. Keine erneute Änderung der Beträge 270,24/337,80 EUR.

Für FB-03 trägt [§ 90 SGB XII](https://www.gesetze-im-internet.de/sgb_12/__90.html)
den vorgeschlagenen fiktiven Vermögensverbrauch nicht.
[BSG, 20.09.2012, B 8 SO 20/11 R, Rn. 14–15](https://www.sozialgerichtsbarkeit.de/node/88668/pdf)
und [BSG, 25.04.2018, B 4 AS 29/17 R, Rn. 19–20](https://www.sozialgerichtsbarkeit.de/legacy/202126)
belegen die Gegenposition: tatsächlich noch vorhandenes Vermögen wird nicht allein
durch rechnerischen Bedarf verbraucht. Die schon vor dieser Reparatur bestehende
Verbrauchsfortschreibung bei positiver Zahlung ist damit nicht umfassend rechtlich
verifiziert. Eine nachweisgebundene Vermögenshistorie mit Monatsstichtagen fehlt.

Die neue Änderungsfunktion automatisiert weder Anhörung noch Bekanntgabe,
Vertrauensschutz oder Erstattung. Eine Gegenprüfung der Reparatur fand zusätzlich
den Fall 10.000,01 EUR mit einem ungeklärten Altmonat: dessen geplanter Einsatz
konnte im Restplan erneut erscheinen. Der konservative Guard und eine weitere
Oracle-Regression sichern diesen Pfad ab; nach Anordnung des Altmonats gelingt die
Änderung mit dem bereits geminderten Bestand.

Für FB-06 unterscheiden sich die offiziellen Grenzen:
[Oracle 19c: 1.000 Ausdrücke](https://docs.oracle.com/en/database/oracle/oracle-database/19/sqlrf/IN-Condition.html),
[Oracle 26ai: 65.535 Ausdrücke](https://docs.oracle.com/en/database/oracle/oracle-database/26/sqlrf/Expression-Lists.html).
Der Test simuliert die 19c-Parsergrenze separat und prüft den tatsächlichen
Kassenlauf mit 1.001 eigenen SQL-Fixture-Zeilen auf der verfügbaren Free-Datenbank.
Das ist kein fachlicher Anordnungs- oder Lastnachweis.

## Frisch ausgeführte Verifikation

Endgültiger gemeinsamer Prüflauf: **15.10.2026, 13:06–13:09 Uhr Europe/Berlin**.
Eigenes Schema `GROK_FIX_20261015` in Docker `lmg-oracle`, PDB `FREEPDB1`.
Keine fremden Developer-Tabellen zurückgesetzt und keine fremden Prozesse beendet.
Nach den Prüfungen wurden null aktive Sitzungen des eigenen Schemas festgestellt;
das Schema wurde entfernt und die lokale Zugangsdaten-Datei gelöscht.

| Prüfung | Ergebnis |
|---|---|
| `mvn -B install` mit Oracle | **351 Tests, 0 Fehler, 1 vorgesehener Skip** |
| `mvn -B -Dtest=LmgConfigTest test` ohne DB-Umgebung | **2/2 bestanden**, deckt den ausgelassenen Konfigurationsfall ab |
| `mvn -B -f testsweet/pom.xml test` mit Oracle | **260/260 bestanden, 0 Skips** |
| Node: Referenz, JSON-Korpus, Zertifikatsleser, LLM-UI, neue Produkt-UI-Prüfung | **136/136 bestanden** |
| Python-Referenztests | **11/11 bestanden** |
| Unabhängiger JUnit-Zertifikatsleser | **main PASS, testsweet PASS**, keine Ablehnungsgründe |

Gegenüber dem Ausgangsstand wurden **26 Haupttests, 11 testsweet-Java-Tests und
6 JavaScript-Tests ergänzt**. Ein vorhandener Snapshot-Roundtrip wurde verschärft.
Der erste Hauptlauf scheiterte an einem neuen skalenempfindlichen Geldvergleich
(`10563` gegen `10563.00`), ohne Betragsabweichung. Die Geldassertionen wurden
korrigiert; der fehlgeschlagene Lauf bleibt separat erhalten und wird nicht als
grüner Nachweis gewertet.

Alle drei finalen Java-Laufmarker enthalten denselben Quellenfingerabdruck
`2dfdb1b62704c68c854c1d0d8c4cb97494a311ec2ef6d92560d40f5e8322abb6`
und passende Buildartefakte. Die 254 Dateien des Zertifikats-Quellenmanifests wurden
anschließend nachgehasht: **0 Abweichungen**. Diese Manifestmenge ist nicht das
anders abgegrenzte 265-Dateien-Inventar des Originalreviews.

Prüfskript, Laufmarker, XML-/Quellhashprüfung und Zusammenfassung stehen unter
[grundsicherung-grok-gegenpruefung-2026-10-15-belege](grundsicherung-grok-gegenpruefung-2026-10-15-belege/).
Die ursprünglichen Protokolle und XML-Dateien bleiben lokale Laufbelege; ihre
Verweise sind keine Behauptung, dass sämtliche Rohdateien in Git enthalten sind.

**Nicht erneut ausgeführt:** sieben Simulator-Vollläufe, GLM-Aufruf,
Gesamtzertifikatsexport, Oracle 19c EE oder Java-21-Laufzeit. Verifiziert wurde
Temurin 25.0.4.1 mit Compilerziel 21 und Oracle AI Database 26ai Free 23.26.3.0.0.
Frühere Simulator-/GLM-Nachweise gelten nach den Quellenänderungen nicht als
aktueller Nachweis für diesen Stand.

## Detailprüfungen und Upgrade

- [Transaktionskern und Kassenlauf](grok-kern-gegenpruefung-2026-10-15.md)
- [Fachlogik und Gegenbelege](grok-fach-gegenpruefung-2026-10-15.md)
- [Web, Sicherheit und Vertrauensgrenzen](grok-web-gegenpruefung-2026-10-15.md)

Keine DDL-Migration erforderlich. Die bestehende Spalte `TX_SAGA_STEP.NAME` wird
früher befüllt. Alte namenlose IN_FLIGHT-Schritte bleiben zur Klärung FAILED;
eine mutmaßlich falsche Kompensation wird nicht ausgeführt. Schrittnamen müssen
über kompatible Definitionen hinweg dieselbe Wirkung identifizieren.

Die Änderungen liegen lokal vor. Ein Commit oder Push wurde in dieser Gegenprüfung
nicht vorgenommen.
