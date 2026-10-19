# testsweet: Review-Abdeckung und Standard 100/100

Stand: 15.10.2026, aufbauend auf der
[Grok-Gegenprüfung](grundsicherung-grok-gegenpruefung-2026-10-15.md).
Die bisherigen 260 testsweet-Java-Tests deckten mehrere Korrekturen nur über reine
Berechnungsklassen ab. Service-/Oracle-Prüfungen für Monatsvorschau und
Änderungsbescheid sowie ein echter Nach-Commit-Taskabbruch fehlten dort. Auch die
Produkt-UI-Gegenproben liefen bisher nur im separaten Node-Aufruf.

Die Lücken wurden durch **28 zusätzliche testsweet-Java-Ausführungen** und
**3 zusätzliche JavaScript-Tests** geschlossen. Die Produkt-UI-Prüfung läuft nun
auch über `SweetUiRegressionTest` im normalen Mavenlauf und damit im automatischen
Zertifikatsablauf. Das ist keine Behauptung vollständiger Pfad- oder Rechtsabdeckung.

## Neue Standardumfänge

| Eintritt | Neuer Standard |
|---|---|
| GUI-Start und Standardpreset POSITIV / NEGATIV | jeweils **100 Aufträge** |
| CLI `run` / `web`, wenn die Auftragszahl fehlt | **100 Aufträge** |
| HTTP-Start ohne Auftragszahl | **100 Aufträge** |
| GLM-Konfiguration, Formular und Fallback | **200 Prüffälle: genau 100 positiv und 100 negativ** |
| Zertifikatsworker WEB POSITIV / NEGATIV | jeweils **100**, zuvor 60 |
| Zertifikatsworker FACH POSITIV / NEGATIV | weiterhin jeweils **500** |
| Gesamte sieben Zertifikats-Simulatorläufe | **1.392 Aufträge**, zuvor 1.312 |

Java verwendet gemeinsame Konstanten in `SimulatorDefaults`. Ausdrücklich gewählte
andere Umfänge bleiben erhalten. Die lokale bisherige GLM-Fallzahl 100 wurde gezielt
auf 200 geändert; andere Konfigurationswerte und Zugangsdaten bleiben erhalten.

Die Fallauswahl prüft die Quote **je Kategorie**, nicht nur die Gesamtsumme. Bei
200 angeforderten Fällen werden 100 Positiv- und 100 Negativfälle benötigt.
99/101 wird abgewiesen. Bei ungeraden individuellen Fallzahlen erhält die
Positivgruppe den zusätzlichen Fall. Priorisierte Grenzfälle bleiben innerhalb
der Positivgruppe vorn; fehlende Fälle werden weder heimlich gekürzt noch durch
die andere Kategorie ersetzt. Das Manifest nennt beide ausgewählten Mengen.

## Abdeckung aller 16 Grok-Befunde

Die Klassen liegen unter `testsweet/src/test/java`; die Browser-Gegenproben unter
`testsweet/src/test/js`. Die Namen bezeichnen die tatsächlich ausgeführten Tests,
nicht lediglich geplante Szenarien.

| Befund | testsweet-Abdeckung und Grenze |
|---|---|
| FB-01 README | Dokumentationskorrektur, manuell gegen Code und Quellen gegengeprüft. Kein automatischer Rechtsnachweis durch einen Wortlauttest. |
| FB-02 Task-Abbruch | **Neu:** `ProduktTaskAusgangDbTest` mit vier Oracle-Tests: echter Servicecommit vor tatsächlichem Wachhund, HTTP 409, unabhängige SQL-Zahlungs-/Budgetprüfung während der Worker noch blockiert, Replay mit gleicher Nummer und Abweisung einer zweiten Monatsanordnung. Weitere Fälle prüfen Deadline sowie Interrupt vor automatischem und manuellem Commit. `SweetUiRegressionTest` startet zusätzlich die Produkt-JS-Proben für lesendes Nachladen und Nummernerhalt über Browser-Reload. |
| FB-03 Deckendes Vermögen | **Neu:** `GrokFachServiceDbTest.nullanordnungUndVorschauVerbrauchenKeinVermoegenOhneTatsaechlicheBestandsaenderung`; dazu bestehende Produkt-Planprüfung. Ablehnung bleibt ohne Bestandsänderung, Zahlung erst nach erfasstem tatsächlichem Bestand. Keine Legalisierung eines fiktiven Verbrauchs oder der gesamten bestehenden Vermögensmodellierung. |
| FB-04 Objektzuständigkeit | **Weiterhin offen.** Ein Zuständigkeitsmodell ist nicht implementiert. Kein Test behauptet eine tatsächlich nicht vorhandene Team-/Fallisolation. |
| FB-05 Saga-Namensbindung | `ProduktSagaJournalRegressionDbTest` erweitert: fehlender/umbenannter Name bleibt FAILED mit Payload, mehrfacher Wiederanlauf führt keine heutige Indexkompensation aus; ein vollständiger alter DONE-Ablauf wird nach Erweiterung nur abgeschlossen. Falsches DONE-Namensmapping rollt einen echten fachlichen INSERT zurück. Bestehender Definitionswechseltest prüft die richtige Kompensation. |
| FB-06 Kassenblöcke | `KassenMassenlaufDbTest` verschärft: tatsächliche Oracle-Updates **1000 + 1**, unabhängige Zeilen-/Summenprüfung und zweiter Lauf ohne weitere Übergabe. Ein JDBC-Guard simuliert ausdrücklich das 19c-Parserlimit; die aktuelle Free-Datenbank allein hätte den alten Fehler nicht aufgedeckt. Im zweiten Test trifft der zweite echte UPDATE keine Zeile; alle ersten 1.000 Änderungen und der Journaleffekt müssen zurückgerollt werden. |
| FB-07 Monatsvorschau | **Neu:** `GrokFachServiceDbTest.bezugsmonatStimmtInServiceVorschauBescheidUndPersistierterZahlungUeberein` prüft Juni/Juli bei Schwangerschaftsende mit Service, Snapshot und SQL-Zahlungsbetrag. Die Produkt-UI-Prüfung kontrolliert den gewählten Monatsparameter. Der HTTP-Router wird zusätzlich durch `GrokWebRegressionTest` der Hauptsuite geprüft. |
| FB-08 Änderungsbescheid | **Neu:** `GrokFachServiceDbTest`: unveränderter Altplan/Alt-Snapshot, neue Monatsbeträge, Zahlungssperre im Änderungsabschnitt, Rollback nach tatsächlich begonnener Aufhebung, fehlende Präfixeingaben in alten v2/v3-Snapshots sowie noch nicht angeordneter Präfix-Vermögenseinsatz mit 10.000,01 und 10.500 EUR. Die Produkt-UI-Prüfung kontrolliert die explizite PUT-Anfrage. Kein vollständiger verwaltungsrechtlicher Verfahrensnachweis. |
| FB-09 Kinderzähler | Bestehende `GrokFachProduktRegressionTest` plus **neue Service-/Oracle-Prüfung**: widersprüchliche vollständige Zähler werden vor Persistenz abgewiesen, ungeklärte Mehrfachzuordnung vor Buchung. Vollständige eigene Tabellenzustände bleiben bei Ablehnung unverändert. Keine Pflicht, sämtliche Kinder als leistungsberechtigte SozialFälle zu erfassen. |
| FB-10 Geheimnis im Fehler | Bestehend: `ProduktHttpSicherheitTest.defektesKontensegmentVeroeffentlichtKeinPasswortInAusnahmen`, einschließlich Geheimnis im verschobenen Rollenfeld. |
| FB-11 Write-Ahead-Quittung | Bestehend: `ProduktSagaJournalRegressionDbTest.serverCommitOhneWriteAheadQuittungBleibtBisRecoveryPayloadgebunden`: echter Oracle-Commit, danach künstlicher Quittungsfehler, keine gestartete Fachaktion; FAILED/Payload bis Recovery, danach korrekter Abschluss. Kein real injizierter Netzabriss. |
| FB-12 Payload-Vorscan | Bestehend: `ProduktSagaJournalRegressionDbTest.namensbindungUndPayloadfreierVorscanBleibenNachDefinitionswechselWirksam`: Vorscan ohne Payload/Schritte, Claim mit vollständigen Daten. |
| FB-13 Optionale Replay-Bankdaten | **Neu:** `GrokFachServiceDbTest.replayOhneBankoverrideBehaeltOriginalauftragAuchNachStammdatenwechsel`, jeweils mit und ohne explizite Bankfelder im Erstauftrag. Gleiche gespeicherte Zahlung bleibt erhalten; explizit abweichende Overrides werden ohne Schreibwirkung abgewiesen. |
| FB-14 Service-Storno | Bestehend: `ProduktHttpSicherheitTest.sachbearbeiterKannStornoMitKeinerIdSchreibweiseAusloesen` prüft den authentifizierten Eintritt. Ein nicht vorhandener authentifizierter Service-Aufruferkontext wird dadurch nicht behauptet. |
| FB-15 Snapshot-Eingaben | Bestehend: `GrokFachProduktRegressionTest.snapshotV3ErhaeltAlleMerkmaleUndRohbeträgeAuchBeiKduKappung` und Alt-Snapshot-Lesbarkeit. **Neu:** Service-/Oracle-Vergleiche der Monatsmerkmale, unveränderte historische Dokumente und Verweigerung unvollständiger Altpräfixe. |
| FB-16 Frame-Schutz | Bestehend: `ProduktHttpSicherheitTest.produktOberflaecheUndDruckVerweigernEinbettung` am echten Produkt-HTTP-Server. |

Die neue `GrokFachServiceDbTest` hat zwölf Oracle-Ausführungen. Ablehnungen werden
über einen konsistenten READ-ONLY-Snapshot sämtlicher eigener Zeilen in
`BEDARFSGEMEINSCHAFT`, `SOZIAL_FALL`, `ZAHLUNG`, `BESCHEID` und `JOURNAL` geprüft.
Fach-Sollbeträge und persistierte Werte werden getrennt verglichen. Die elf
Kern-Oracle-Methoden verteilen sich auf Task (4), Saga (5) und Kassenlauf (2).

Die Standardänderung ist durch `SimulatorDefaultsTest` (5), zwei zusätzliche
Prüfpakettests in `LlmReviewServiceTest` und drei neue Browserdatei-Tests abgesichert.
Das umfasst echten HTTP-Konfigurationsabruf, CLI-/HTTP-Parser, Presets/Formulare,
Zertifikatsmatrix, 100/100-Paketerstellung und Ablehnung eines 99/101-Fehlbestands.

## Tatsächlich ausgeführte Prüfung

Endgültiger Lauf am **15.10.2026, 13:32–13:35 Uhr Europe/Berlin** auf eigenem Schema
`SWEET_COV_20261015` in Docker `lmg-oracle`, PDB `FREEPDB1`. Temurin 25.0.4.1,
Compilerziel 21, Oracle AI Database 26ai Free 23.26.3.0.0.

| Prüfung | Ergebnis |
|---|---|
| Hauptprojekt `mvn -B install` | 351 Tests, 0 Fehler, 1 vorgesehener Skip |
| Konfiguration ohne DB-Umgebung | 2/2 bestanden; zusammen 351 unterschiedliche erfolgreiche Haupttests |
| testsweet `mvn -B -f testsweet/pom.xml test` | **288/288 bestanden, 0 Skips** |
| Node-Gesamtprüfung | **139/139 bestanden**, einschließlich 33 testsweet-UI- und 6 Produkt-UI-Fällen |
| Positiver Fachsimulator `SweetMain run`, ohne Auftragszahl | **100/100 verifiziert**, zeigt den tatsächlich verwendeten neuen CLI-Default |
| Negativer Fachsimulator, 100 angefordert | **100/100 erwartete Abweisungsfälle verifiziert**, 0 globale SQL-Prüffehler |
| Lokales GLM-Prüfpaket aus genau diesen Berichten | **100 positiv + 100 negativ, 200 eindeutige Fall-IDs**, Manifest-/Buildbindung geprüft |
| Unabhängiger JUnit-Zertifikatsleser | Hauptprojekt PASS, testsweet PASS |

Der erste testsweet-Lauf fand nur eine veraltete bestehende Sollsumme von 1.312
statt der jetzt erforderlichen 1.392 Zertifikatsaufträge. Der Vertragstest wurde
auf den neuen Umfang angepasst. Der Fehlversuch bleibt getrennt erhalten;
anschließend wurden beide Module auf dem endgültigen gemeinsamen Stand neu geprüft.

Quellenfingerabdruck aller drei finalen Java-Nachweise und beider Simulatorläufe:
`85522ae99eedcf243663850c046c259dca2b656ff9138ec4ef8c7457740599ed`.
258 Dateien des Zertifikatsmanifests nachgehasht: **0 Abweichungen**.
Prüfskripte, Laufmarker und Zusammenfassung:
[testsweet-abdeckung-2026-10-15-belege](testsweet-abdeckung-2026-10-15-belege/).
Nach Prüfung auf null aktive Sitzungen wurde ausschließlich das eigene Testschema
entfernt; die temporäre Zugangsdaten-Datei ist gelöscht. Keine fremden Developer-
Tabellen zurückgesetzt oder fremden Prozesse beendet.

**Grenzen:** Die 100 Standardfälle ersetzen die gezielten JUnit-Regressionen nicht.
Es liefen zwei Fachsimulatoren, kein vollständiger neuer Sieben-Modi-Lauf.
Das lokale Prüfpaket wurde nicht an GLM gesendet; kein neues GLM-Urteil oder
Gesamtzertifikat. Java-21-Laufzeit und Oracle 19c EE wurden nicht ausgeführt.
Eine schon laufende testsweet-JVM muss für die neuen Java-Klassen neu gestartet
werden. Änderungen sind lokal, noch nicht committet oder gepusht.

## Nachtrag: bestehendes Developer-Schema

Der anschließende GUI-Zertifikatslauf fand eine zusätzliche Testfixture-Lücke:
Fünf Saga-Tests setzten die neue Demo-Spalte SAGA_ID voraus, die im bestehenden
Developer-Schema fehlt. Der obige 288/288-Nachweis gilt für das ausdrücklich
genannte Neuinstallationsschema. Die unnötige Abhängigkeit ist entfernt und
durch drei neue Regressionen abgesichert. Die vollständige testsweet-Suite
bestand danach mit **291/291, ohne Skips**, auf dem bestehenden `TX_DEMO`-Schema.
Ursache, Nachprüfung und Grenzen stehen in
[zertifikat-saga-testkorrektur-2026-10-15.md](zertifikat-saga-testkorrektur-2026-10-15.md).
