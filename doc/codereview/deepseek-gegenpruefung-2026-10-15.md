# DeepSeek-Review r7w2: Gegenprüfung und Nachbesserungen

Prüfdatum: 2026-10-15. Geprüft wurde der aktuelle Arbeitsbaum auf
`37abfb6f3da841e440ab21a53fbffa7eadf7cd04`, einschließlich der vorherigen Grok-,
Antigravity- und GLM-Nachbesserungen. Der Fremdbericht beschreibt einen älteren
Arbeitsstand; seine Testzahlen und Quellhashes sind historische Nachweise.
Die vorhandenen Änderungen wurden erhalten. Keine Commits oder Pushes.

## Befundentscheidungen

| Befund | Gegenprüfung und Ergebnis |
|---|---|
| R7W2-01: HTTP 500 nach Commit | Teilweise bestätigt. Ein Fehler der Commit-Quittung konnte als gewöhnlicher TxException-/500-Fehler und sogar als Rollback erscheinen. Dieser Restpfad wird als unbekannter Commit-Ausgang behandelt, ohne Wiederholung. Die Timeoutprobe P3 lieferte bereits im eigenen Originalprotokoll des Reviews `TaskOutcomeUnknownException` und HTTP 409; der Bericht vermischt P2 mit P3. |
| R7W2-02: Deckendes Vermögen automatisch verbrauchen | Reparaturvorschlag abgelehnt. Die Probe zeigt das bestehende Verhalten, beweist aber keinen tatsächlichen Verbrauch. Ein lediglich rechnerischer Vermögensabbau ist keine gesetzliche Rechtsfolge; Einzelheiten und Primärquellen unten. Die positive automatische Fortschreibung bleibt ebenso eine bestehende, ausdrücklich begrenzte Demo-Annahme. |
| R7W2-03: Fehlende Objektzuständigkeit | Tatsächlich vorhandene und bereits dokumentierte Grenze. Der Nutzer hat am 15.10.2026 ausdrücklich entschieden, das bestehende Demo-Modell beizubehalten. Team-/Fallzuständigkeit und ein strenger Modus mit Bescheidpflicht sind damit bewusst zurückgestellte Modellerweiterungen. Der fehlende Team-/Mandantenfilter bleibt eine vorhandene Modellgrenze und gilt nicht als behoben. |
| R7W2-04: Durchsatz und Lastbehauptung | Bestätigt. Millisekunden statt abgeschnittener Sekunden, p50/p95/p99 und Stichprobengröße ergänzt. Reports, Status, UI, README und Zertifikatsvorlage bezeichnen die vorhandenen LAST-/STRESS-Läufe als begrenzte funktionale Parallelprüfungen. Sie liefern keinen Dauerlast-, Sättigungs- oder Kapazitätsnachweis. |
| R7W2-05: Zahlung ohne Datenbank-Bescheid | Das Verhalten ist bestätigt und bereits als Demopfad dokumentiert; die angeführte rechtliche Begründung und der behauptete neue Implementierungsfehler tragen nicht. Das frühere GLM-FAIL beruhte auf einem unvollständig erläuterten Produktvertrag, der in der Antigravity-Nachbesserung ergänzt wurde. Der bestehende Demopfad bleibt entsprechend der Nutzerentscheidung vom 15.10.2026 erhalten; ein strenger Betriebsmodus ist bewusst zurückgestellt. |
| R7W2-06: Retry muss identisch sein | Kein aktueller Restfehler. Die geltende Dokumentation erlaubt unveränderte Dokumenttexte plus formalen Korrekturhinweis; Requestbytes, Dokumenthashes und die vollständige Auftragsrekonstruktion sind geprüft. Vorläufige FAILs bleiben erhalten und können durch spätere PASSs keinen grünen Nachweis erzeugen. |
| R7W2-07: Globales SWEET-Cleanup | Bestätigt und behoben. Jeder Lauf erhält einen zufälligen UUID-Namensraum. Bereinigt werden ausschließlich frühere, von derselben Simulatorinstanz selbst erzeugte Laufkennungen. Globale Prüfungen beziehen sich auf den aktuellen Namensraum. Fremde und historische Daten sowie nicht sicher zuordenbare Null-BG-Journale bleiben erhalten. |
| R7W2-08: Stille Nullwerte/Rundung | Teilweise bestätigt. Unbekannte JSON-Schlüssel und tatsächliche Subcentbeträge werden jetzt abgewiesen. Fehlende optionale Geldfelder waren bereits ausdrücklich als null/0-Vertrag getestet und werden nicht nachträglich zu Pflichtfeldern erklärt. `12.3400` ist centgenau und zulässig; `12.345` wird abgewiesen. |

Zusätzlich ist C-03 geschlossen: Die unabhängigen Node-Prüfer für
Zertifikatsbelege, Rechenreferenz und Antwortparität gehören jetzt zur
verpflichtenden testsweet-Maven-Suite. Die beiden Browserprüfer waren bereits
enthalten. Der neue Gate prüft vorhandene Testdateien, Prozessende, Exitcode,
nichtleere TAP-Zusammenfassung und den vollständigen Erfolg ohne Skip, Abbruch
oder TODO. Die testsweet-README ist jetzt ebenfalls quellengebunden und durch
Java-/Node-Hashregressionen abgedeckt.

## Was die rechtliche Gegenprüfung ergibt

### Vermögen

Der amtliche [§ 90 SGB XII](https://www.gesetze-im-internet.de/sgb_12/__90.html)
verlangt den Einsatz verwertbaren Vermögens. Er ordnet keinen automatischen
buchhalterischen Verbrauch für einen lediglich berechneten Deckungsmonat an.
Das [BSG, 20.09.2012, B 8 SO 20/11 R, Rn. 14–15](https://www.sozialgerichtsbarkeit.de/node/88668/pdf)
schließt einen fiktiven Vermögensverbrauch ausdrücklich aus; vorhandenes
verwertbares Vermögen bleibt relevant, solange es tatsächlich vorhanden ist.
Der Primärtext wurde in dieser Gegenprüfung abgerufen und geprüft, ebenso die
Bestätigung in [BSG, 25.04.2018, B 4 AS 29/17 R, Rn. 19–20](https://www.sozialgerichtsbarkeit.de/legacy/202126).

Deshalb ist das im Fremdbericht vorgeschlagene automatische Absenken von
10.563 EUR auf 10.000 EUR allein wegen einer Nullzahlung keine belastbare
Reparatur. Bei tatsächlich belegtem Verbrauch muss der neue Bestand erfasst
werden. Das bisherige automatische Verbrauchsmodell bei positiven Zahlungen
ist dadurch ebenfalls nicht rechtlich bestätigt und wird nicht als solches
zertifiziert. Eine nachweisgebundene Vermögenshistorie bleibt eine eigenständige
Facherweiterung.

### Bescheidpflicht

[§ 44 SGB X](https://www.gesetze-im-internet.de/sgb_10/__44.html) regelt die
Rücknahme rechtswidriger nicht begünstigender Verwaltungsakte;
[§ 66 SGB I](https://www.gesetze-im-internet.de/sgb_1/__66.html) regelt Folgen
fehlender Mitwirkung. Diese Normen belegen die im Fremdbericht behauptete
allgemeine Auszahlungssperre ohne gespeicherten Bescheid nicht.
[§ 33 Abs. 2 SGB X](https://www.gesetze-im-internet.de/sgb_10/__33.html) lässt
unterschiedliche Formen des Verwaltungsakts zu. Eine fehlende BESCHEID-Zeile
beweist daher allein noch nicht, dass es keinen Verwaltungsakt gibt.
[§ 44 SGB XII](https://www.gesetze-im-internet.de/sgb_12/__44.html) regelt Antrag,
Bewilligungszeitraum und Leistungserbringung.

Die stärkste Gegenposition bleibt berechtigt: Für den vorgesehenen Betrieb muss
die Zahlungsgrundlage nachweisbar sein. Das gegenwärtige Demo-Modell bildet
abweichende Zahlungsgrundlagen nicht vollständig ab. Ein strenger Modus mit
Bescheidpflicht kann fachlich gewünscht sein, folgt aber nicht aus dem zitierten
GLM-Urteil oder den im Review genannten Vorschriften. Diese Gegenprüfung erklärt
Zahlungen im Demo-Modell nicht pauschal für rechtmäßigen Produktivvollzug.

## Technische Umsetzung und Nachweise

Die neuen Commit-Regressionen prüfen einen tatsächlich ausgeführten Commit mit
synthetisch verlorener Quittung, unveränderte Wirkung bei Wiederholungsschutz,
den HTTP-409-Pfad und die manuelle Transaktions-API abdecken. Ein best-effort
Rollback nach einem Commitfehler darf nicht als Beweis ausgegeben werden, dass
der vorherige Commit wirkungslos war.

Die Geldwert-Regressionen prüfen alle neun SozialFall-Geldfelder, BG-Anlage und
Änderung sowie Saga-Eingaben. Echte Oracle-/HTTP-Gegenproben verlangen bei
unzulässigen Anfragen unveränderte Beträge, Versionen und Journalzeilen sowie
keine zusätzliche Anlage. Optionale Nullwerte und verlustfreie Centdarstellung
bleiben mit positiven Kontrollen abgesichert.

Die Cleanup-Regression legt eigene und fremde UUID-Fixtures einschließlich
Fällen, Zahlungen, Bescheiden und Journalen an. Nach Bereinigung müssen die
fremden Zeilen vollständig erhalten bleiben. Die Scope-Kennung stammt nicht aus
Anfragedaten; der SQL-Filter verwendet Parameter und einen exakten Präfixvergleich
ohne Wildcards. Fremde Logverzeichnisse sind kein Besitznachweis. Ein globaler
DELETE gegen `SWEET-%` oder `sb-sim-%` existiert im Aufräumweg nicht mehr.

Die Kennzahlregression fixiert 120 Aufträge in 2.100 ms auf 3.428,57/min sowie
einen positiven Durchsatz unter einer Sekunde. Perzentile verwenden nearest-rank
auf tatsächlich abgeschlossenen Aufträgen und nennen ihren Stichprobenumfang.
Eine erzwungene Mindestdauer oder Sättigungsstufe wurde nicht erfunden; ein grüner
Funktionslauf ist kein Kapazitätszertifikat.

Der erste Hauptlauf führte 394 Tests aus und fand genau eine inkompatible alte
Testannahme: `retryNachCommitKonfliktPersistiertEinsichtGenauEinmal` injizierte einen
Fehler während `commit()` und verlangte eine Wiederholung. Die neue Regel erlaubt
bei unbekanntem Commit-Ausgang gerade keinen Retry, auch bei einem Konfliktcode.
Dieser fehlgeschlagene Erstlauf samt XML und Marker bleibt unter
`tmp/review-deepseek-fixes-20261015/attempt-1/` erhalten. Die Regression wird in
Commit-UNKNOWN und legitimen Konflikt-Retry vor dem Commit getrennt; die
Produktionsregel wird nicht für eine alte Testerwartung abgeschwächt.

Zentral frisch ausgeführt, 18:42–18:46 Uhr MESZ:

| Prüfung | Ergebnis |
|---|---|
| Hauptprojekt `clean install`, eigene Oracle-DB | 395 Tests, 0 Fehler, 1 erwarteter Skip |
| Konfiguration ohne Datenbankvariable | 2/2, kein Skip |
| testsweet `clean test`, frisches Produkt-JAR | 329/329, kein Skip |
| Unabhängige Node-Werkzeug-/Browserprüfungen | 142/142; Werkzeugprüfer zusätzlich verpflichtend innerhalb Maven grün |
| Unabhängige Python-Referenz | 11/11 |
| Runtime-Kopie und frische Prüf-JVM | PASS; 75 Einträge nachgehasht, tatsächliche Produkt-/testsweet-CodeSources in der Kopie, ursprüngliche JUnit-Guards gültig |
| FACH POSITIV / NEGATIV | 100/100 und 100/100 verifiziert |
| FACH LAST / STRESS | 120/120 und 42/42 verifiziert; vier verschiedene UUID-Namensräume über alle Fachläufe |
| Lokales GLM-Prüfpaket | 100 Positiv + 100 Negativ, 200 unterschiedliche Fall-IDs, kein API-Aufruf |
| Quellen- und Belegbindung | 288 Dateien, keine Hash-Abweichung; beide unabhängigen JUnit-Leser PASS |

Gegenüber der vorigen GLM-Gegenprüfung sind das **20 zusätzliche Haupttests und
16 zusätzliche testsweet-Ausführungen**. Alle zwölf finalen Build- und
Nachprüfungsschritte sind grün. Die LAST-/STRESS-Berichte enthalten
Millisekundendauer, p50/p95/p99 und ausdrücklich `capacity_evidence = false`.
Gemeinsamer Quellenfingerprint:
`167b3d8f0cc9affb4446dc167452832cfba12ed87c12903dde4944a02badafbe`.

Prüfumgebung: Temurin 25.0.4.1 mit Compilerziel 21, Oracle AI Database 26ai Free
23.26.3.0.0, FREEPDB1. Verwendet wurden das eigene Schema `DS_FIX_20261015` und
ein eigenes Maven-Repository. Anschließend wurde ausschließlich dieses Schema
bei null aktiven Sitzungen entfernt; die temporäre Zugangsdaten-Datei ist gelöscht.
Die Entwicklerdaten wurden nicht zurückgesetzt.

Belege: [Verifikationsübersicht](deepseek-gegenpruefung-2026-10-15-belege/verification-summary.json),
JUnit-Marker und ausgewählte XML-Regressionen sowie der gesicherte fehlgeschlagene
Erstlauf im selben Verzeichnis. Die ursprünglichen Protokolle werden nicht in
grüne Nachweise umgeschrieben.

## Grenzen

Die offenen Hypothesen H-01 bis H-06 werden nicht allein durch ihre Nennung zu
bestätigten Fehlern. Sie wurden hier nicht vollständig neu dynamisch geprüft.
Historische Reviews und Modellurteile ersetzen keine
gezielten Gegenproben. Insbesondere wurden keine gemeinsame Datenbank gestoppt,
keine realen Sozialdaten verwendet und keine fremden Simulatorbestände gelöscht.

Diese Gegenprüfung startet keinen bezahlten GLM-Aufruf und erzeugt keinen neuen
vollständigen Zertifikatslauf mit allen sieben Kombinationen. Die vorhandenen
100/100-Standards bleiben erhalten. Keine neue Prüfung auf Oracle 19c EE oder
einer Java-21-Laufzeit; das Compilerziel bleibt Java 21. Ein neues Gesamtzertifikat
erfordert nach Übernahme der Änderungen einen Neustart der Anwendungen und einen
frischen Gesamtlauf.
