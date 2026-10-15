# Gesamt-Review: Korrekturen und Regressionen vom 15.10.2026

Ausgangspunkt ist der [Gesamt-Review zu 3c909c2](grundsicherung-gesamt-review-2026-10-15-3c909c2.md).
Die Änderungen liegen uncommittet auf `main`, weiterhin Version 0.5.8. Die zehn
bestätigten Funde wurden korrigiert; zusätzlich wurde eine im vorherigen
Zertifikatslauf sichtbar gewordene Scheinprüfung nach Storno ersetzt.

## Änderungen und dauerhafte Abdeckung

| Fund | Korrektur | Regression |
|---|---|---|
| GR-01 Storno-Berechtigung | Admin-Prüfung folgt derselben Pfadstruktur wie der Handler; zusätzliche Rollenprüfung unmittelbar vor dem Serviceaufruf. | Produkt-HTTP-Tests in Hauptprojekt und testsweet prüfen u.a. `+7`, kodiertes Plus, Unicode-Ziffern, führende Nullen und abschließende Schrägstriche. Kein unberechtigter Serviceaufruf. |
| GR-02 Alleinerziehendenbedarf | Vergleich der günstigeren Alternative, 12 % je minderjährigem Kind gegen den anwendbaren 36-%-Betrag, gedeckelt auf 60 %. Produkt sowie Java-/Node-/Python-Referenz korrigiert. | TF13–15 mit vier, fünf und sechs Kindern und sich überschneidenden Altersgruppen; vorab handgerechnete SQL-Sollbeträge. |
| GR-03 Zentrales Warmwasser | Kein Abzug von zentralen Warmwasserkosten. Altparameter wird weiter validiert, bleibt aber wirkungslos. | TF17 setzt ausdrücklich 0,20; RBS 563 + Miete 400 + Heizkosten 100 ergibt 1.063 EUR. Referenz- und Generatorregressionen ergänzt. |
| GR-04 PENDING-Absturzpunkt | Unter Claim wird eine vollständig PENDING gebliebene Saga als COMPENSATED abgeschlossen; Payload wird entfernt. Widersprüchliche Listen bleiben FAILED. | Haupttests und testsweet-Oracle-Test prüfen PENDING sowie den früher erzeugten FAILED/PENDING-Zustand, wiederholte Recovery und Startup-Guard. |
| GR-05 Historischer Bescheid | Snapshot v3 friert das vollständige Erteilungs-HTML einschließlich Bankdaten, Behörde und Belehrung in derselben Transaktion ein. Druck und Zahlung prüfen BG-/Betrags-/Monatsplan-Bindung; Ganzzahlen werden exakt gelesen. | Haupttests sowie zwei testsweet-Oracle-Fälle für Service- und Saga-Erteilung. Nach Bank- und Behördenwechsel bleibt der Nachdruck zeichengetreu identisch. |
| GR-06 Elektronischer Widerspruch | Behördenkommunikation verweist auf § 84 Abs. 1 SGG, § 36a Abs. 2/2a SGB I und § 9a Abs. 5 OZG. Gerichtlicher Klageweg behält § 65a SGG. | Dokumenttests prüfen die erteilte und persistierte Belehrung. |
| GR-07 Login-Drosselung | Konfigurierte Konten haben getrennte Fehlversuchszähler; volle Phantomlisten umgehen deren Sperren nicht. | Tests mit stellbarer Uhr und echter Produkt-HTTP-Anmeldung nach Füllen der Phantomliste. |
| GR-08 Drucktoken nach Logout | Token ist an die Herkunftssitzung und Ziel-BG gebunden; Logout, Idle-Ablauf und exakt erreichte 60 Sekunden entwerten ihn. | Haupt- und testsweet-HTTP-Tests einschließlich falscher BG, Ablauf, Logout und Einmalverwendung. UI sendet die beim Klick festgehaltene BG-ID. |
| GR-09 LLM-Vertragsabweichung | Java und Node verlangen vollständiges JSON und exakt Schema 2. Keine Codeblöcke oder Begleittexte; gleiche Grenzen für doppelte Schlüssel, Unicode und genaue Zahlen. | Gemeinsamer Rohbytes-Korpus mit 45 Fällen, jeweils in Java und Node. Auch gespeicherte Ergebnisse/Vertragsmetadaten werden strikt gelesen. |
| GR-10 Unzulässige Retries | Ausschließlich Oracle-Vendorcodes 54, 60 und 8177 erlauben Wiederholung; SQLState 61000 allein reicht nicht. | Haupttests und zwei testsweet-Produktkernfälle mit echtem ojdbc-Mapping; Ressourcenfehler werden nicht erneut ausgeführt. |

Der gemeinsame JSON-Korpus ist ausdrücklich in `tools/evidence-sources.txt`
eingetragen. Änderungen an diesen Erwartungen entwerten damit vorhandene
JUnit-, Simulator- und LLM-Nachweise. Nach Ergänzung dieser Bindung wurden Build
und Nachweise neu erzeugt; die davor erfolgreichen Läufe bleiben separat als
`pre-corpus-bind-*` erhalten.

Die Auswahl der Alleinerziehendenalternative ist begründet durch die
[Wissenschaftlichen Dienste, WD 6-046/24, S. 11 i.V.m. S. 8–10](https://www.bundestag.de/resource/blob/1019234/WD-6-046-24-pdf.pdf).
Die stärkste Gegenposition ist die ausschließliche Lesart des isolierten
[§ 30 Abs. 3 SGB XII](https://www.gesetze-im-internet.de/sgb_12/__30.html).
Die amtliche Ausarbeitung wird als Auslegungshilfe verwendet, nicht als bindende
gerichtliche Entscheidung. Für zentrale Warmwasserkosten ist der Einschluss in
die Heizkosten ausdrücklich in [§ 35 Abs. 5 SGB XII](https://www.gesetze-im-internet.de/sgb_12/__35.html)
geregelt. Die elektronische Behördenform folgt [§ 36a SGB I](https://www.gesetze-im-internet.de/sgb_1/__36a.html).

## Reale Storno-Zwischenstände

Die bisherigen TF23-Prüfungen V8/V9 lasen mit `SELECT ? FROM DUAL` zuvor gespeicherte
Werte zurück. Sie konnten einen falschen Datenbankzustand nicht erkennen. Die neuen
Arbeitsschritte fragen `VERMOEGEN` und `BUDGET_REST` anhand der BG-ID direkt aus
`BEDARFSGEMEINSCHAFT` ab: nach dem abgeschlossenen Storno und vor Mietkorrektur oder
erneuter Auszahlung. Beide Abfragen teilen eine READ-ONLY-Transaktion. Sollwerte
werden unabhängig im Szenario vorgegeben, SQL/Parameter/Soll/Ist werden protokolliert.
Abweichungen und SQL-Fehler beenden den Auftrag vor der nächsten Fachmutation.
Drei neue Tests prüfen Abfragen, Lesesnapshot, Rückgabe der Verbindung und den
Abbruchpfad. Die konkrete Ausführung auf Oracle wird zusätzlich im Simulator geprüft.

Der mitgelieferte Rechtstext trennt nun die gesetzliche Regel zur Bewilligungsdauer
von der strengeren Produktgrenze 1–12 Monate. Zulässige Stornostatus werden als
Produktworkflow beschrieben. Ein vollständiger Rechtsstatus-/Beziehungs- und
Änderungsbescheidworkflow ist weiterhin nicht modelliert.

## SEPA-Eingaben im Prüfbericht

Die erste GLM-Stichprobe nach den Korrekturen lieferte 17 PASS und ein UNVERIFIED:
NEGATIV TF07 beschrieb falsche IBAN-Prüfziffer und BIC-Länge, enthielt aber die
tatsächlich verwendeten Rohwerte nicht. Das ist eine Beleglücke, kein nachgewiesener
Fehler der SEPA-Validierung. Der unveränderte Modelllauf
`llm-review-f08a1759-76ee-4191-bcbc-5da986195f25` und dessen Befunde bleiben erhalten
(`sepa-input-findings.json`, `pre-sepa-input-llm-*`).

Kuratierte und generierte SEPA-Negativfälle dokumentieren nun pro Anlageversuch
beide synthetischen Rohwerte. Dieselben Variablen bilden Berichtstext und die
unveränderliche Anlageaktion. Zwei neue Regressionen zeichnen die tatsächlich
übergebenen Serviceargumente auf, delegieren in die echte Fachvalidierung ohne
DB-Ausleihe und vergleichen sämtliche Paare mit der gerenderten Fallgruppe.
Prüfziffer und BIC-Länge werden zusätzlich unabhängig kontrolliert. Die allgemeinen
Aktionssignaturen und die Produkt-API bleiben unverändert.

## Veraltete DOM-Zeile beim Browserlauf

Ein anschließender Browser-Positivlauf endete mit 59/60 erfolgreichen Aufträgen.
Auftrag 26 scheiterte nach dem Rollenwechsel bei `findChildElement` auf einer
inzwischen ersetzten Listenzeile, noch vor dem Öffnen-Klick. Der unveränderte Lauf
`run-20261015-112508-8066350426981593454`, `ui-stale-findings.json` und die
`pre-ui-stale-*`-Protokolle dokumentieren diesen tatsächlichen Fehlversuch.
Er wurde nicht als grün gewertet und stoppte den Gesamtlauf vor weiteren
Simulatoren und GLM.

Die Auswahl beim rein lesenden Öffnen eines Vorgangs wird deshalb anhand eines
frischen Button-Locators wiederholt, wenn die Liste den DOM-Knoten ersetzt.
Fachliche Schreibschritte werden dadurch nicht erneut ausgeführt. Drei Regressionen
erzwingen den DOM-Wechsel, prüfen die einmalige Öffnung bei langsamer Detailantwort
und lassen einen Stale-Fehler bei einer Freigabe ohne Schreibwiederholung propagieren.

## Verifikation

Der erste Abschlussvergleich mit dem Zertifikatsleser wies die neu verwendeten
JUnit-DynamicTests zurück: Surefire schrieb 45 identische XML-Testnamen. Die
Korpusprüfung wurde auf Parametertests mit eindeutigen Invocation-Indizes
umgestellt; Fälle und Erwartungen blieben unverändert. Der Duplikat-Guard wurde
nicht abgeschwächt. `pre-junit-names-certificate-layer-summary.json` bewahrt den
abgewiesenen Nachweis. Die erneute Prüfung kontrolliert beide JUnit-Module bereits
vor den Simulatorläufen mit demselben Zertifikatsleser.

Die [Laufskripte und Rohbelege](grundsicherung-fixes-2026-10-15-belege/) enthalten
Zeitpunkte, Exitcodes, Quellinventare und vollständige Protokolle. Alle eigenen
Datenbanktests verwenden das separate Schema `FIX_20261015` auf `lmg-oracle`.
Die laufende Benutzeroberfläche und deren Schema wurden für diese Fixprüfung nicht
als Testziel verwendet.

| Prüfung | Ergebnis |
|---|---|
| Hauptprojekt `mvn install` mit Oracle | 325 Tests, 0 Fehler; ein Konfigurationsfall im DB-Lauf bewusst ausgelassen |
| Konfiguration ohne DB-Umgebungsvariablen | 2/2; deckt den ausgelassenen Fall ab, alle 325 unterschiedlichen Haupttests damit geprüft |
| testsweet `mvn test` mit Oracle | 249/249, keine Fehler oder ausgelassenen Fälle; zuvor 180 Tests |
| Node einschließlich gemeinsamer Parität, Fachreferenz und UI | 130/130 |
| Python-Referenz | 11/11 |
| Fachsimulator positiv / negativ | 500/500 und 500/500 |
| Fachsimulator Last / Stress | 120/120 und 42/42 (21 Fälle in zwei Wellen, vier Sachbearbeiter) |
| Browser positiv / negativ / Last | 60/60, 60/60 und 30/30 |
| GLM-5.3, vollständige ausgewählte Fallgruppen | 18/18 PASS; sechs Gruppen zu drei Fällen, fünf beim ersten Versuch, eine nach einer Wiederholung |

Alle sieben Simulatorläufe umfassen zusammen **1.312 erfolgreiche Aufträge**.
`simulator-summary.json` weist die Laufverzeichnisse, vollständige Berichte und
deren erfolgreich geprüfte SHA-256 aus. Der persistierte Positivbericht enthält
die Handwerte 2.822,24 / 3.312,80 / 3.750,80 / 1.063,00 EUR sowie die tatsächlichen
SQL-Zwischenstandsabfragen; siehe `boundary-report-checks.json`.

Haupt- und testsweet-JUnit sowie alle sieben Simulatorläufe sind an denselben
Quellenfingerabdruck gebunden:
`4f15cb106deea026143a82b24ef6ffdb3cd8ab6398fe03381a3fcde4ea6ebfc2`.

Der erste testsweet-Versuch hatte zwei Fehler im neuen Testaufbau: ein Interface-Testpool
wurde in ein konkretes Poolfeld injiziert. Die Injektion wurde auf das vorhandene
Interfacefeld berichtigt; der vollständige erneute Lauf ist erfolgreich. Der
fehlgeschlagene Versuch bleibt als `testsweet-db-first-attempt.log` erhalten.

Der abschließende GLM-Lauf ist
`llm-review-ec2fa5e3-3336-441d-998f-cdf7cba3654f`. Ausgewählt wurden die festen
Fachgrenzfälle TF11–19 und neun Negativfälle aus den jeweils aktuellen
500er-Fachberichten. Transportbeleg, numerische Nachrechnung und Prüfabdeckung
sind vollständig verifiziert. Fallgruppe 2 lieferte zunächst eine formal ungültige
Antwort mit fehlenden numerischen Zwischenwerten; die protokollierte Wiederholung
mit Korrekturhinweis war gültig. Der ursprüngliche Versuch bleibt erhalten.
Die bestehende Steuerung ergänzt bei Wiederholungen einen Korrekturhinweis; die
Kurzbeschreibung „identische Sendeversuche“ in AGENTS.md war veraltet und wurde
nach Abschluss des Prüflaufs an dieses bereits bestehende Verhalten angepasst.
Diese reine Dokumentationsänderung ist in `documentation-final-change.json` belegt;
der Buildfingerabdruck blieb unverändert.

`llm-summary.json` hält Fallauswahl und Urteil fest. Request-/Response- und
Quellsnapshots liegen im angegebenen LLM-Verzeichnis. Das Modellurteil ist ein
zusätzlicher Stichprobenbeleg, keine Vollprüfung aller 1.000 Positiv-/Negativfälle
und keine amtliche Fachentscheidung.

Der unabhängige Zertifikatsleser akzeptiert beide JUnit-Module, alle sieben
Simulator-Kombinationen, GLM und Build jeweils mit PASS
(`certificate-layer-summary.json`). Sein Gesamturteil bleibt wegen des älteren
fehlgeschlagenen Zertifikatsgesamtlaufs NICHT BESTANDEN; dessen Nachweis wurde
nicht überschrieben. Ein neues Gesamtzertifikat wurde in dieser Fixprüfung nicht
exportiert.

Während des endgültigen Prüflaufs blieben alle 264 erfassten Dateien unverändert
(`source-drift-check.json`). Die SEPA-Rohwerte sind auch im PDF vollständig und
lesbar; Seite 27 wurde visuell geprüft, Seiten 27/28 sind als Bild-/Textbeleg
gesichert. Die Whitespaceprüfung ist fehlerfrei. Das eigene Oracle-Schema wurde
nach Prüfung auf null aktive Sitzungen wieder entfernt (`schema-cleanup.log`).

## Grenzen und Bestandsschutz

Verifiziert wird mit Temurin 25.0.4.1 und Java-Zielversion 21 sowie Oracle Free
23.26.3. Ein Lauf auf einer Java-21-Laufzeit oder Oracle 19c EE wird nicht behauptet.
Neue Bescheide speichern v3; v1/v2 bleiben lesbar und weisen sichtbar darauf hin,
dass Bankdaten, Behörde und Belehrung aus dem heutigen Stand ergänzt werden.
Fehlende historische Dokumentdaten lassen sich nicht nachträglich rekonstruieren.
Bereits erteilte falsche Bescheide werden durch die Codekorrektur nicht rückwirkend
geändert und benötigen eine fachliche Korrektur.

Der vorangegangene Zertifikatsgesamtlauf endete FAILED. Er wird als Fundquelle,
nicht als Nachweis für den korrigierten Stand verwendet. Diese Fixprüfung ist
ein separat protokollierter Lauf; sie ersetzt kein neu exportiertes Gesamtzertifikat.
