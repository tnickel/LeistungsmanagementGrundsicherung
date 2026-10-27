# testsweet: Behebung der Review-Befunde vom 27.10.2026

**Ausgangsstand:** `155585471145ad2490a8cf4c11152e41e22feaf1`, Branch `learning`.
**Bezug:** [Reviewbericht](testsweet-neue-features-codereview-2026-10-27.md), bestätigte Befunde CR-01 bis CR-19.
**Ergebnis:** Die bestätigten Implementierungs- und Darstellungsfehler sind korrigiert. Fachliche Modellgrenzen werden ausdrücklich ausgewiesen; es wurden keine zusätzlichen gesetzlichen Regeln oder eine erweiterte GLM-/Zertifikatsmatrix erfunden. Die widerlegten Verdachte CR-20 bis CR-32 erforderten keine Fehlerkorrektur.

## Änderungen je Befund

| Befund | Umsetzung | Regression / Beleg |
|---|---|---|
| CR-01 | Report bewertet neun Simulator-Ebenen, zwei laufgebundene JUnit-Ebenen und die GLM-Stichprobe. Bekannter Fehler hat Vorrang; fehlende/laufende/veraltete Pflichtnachweise verhindern BESTANDEN. Positive Pauschalbestätigungen wurden durch belegabhängige Aussagen ersetzt. Schlussseite ist Gesamtbewertung, kein zweites formales Zertifikat. | `doc/testreport-werkzeug/report-tools.test.js`: ECHT-PASS neben JUnit-FAIL; fehlende Ebenen; vollständige positive Bewertung; DOCX-Inhalt ohne falsche Erfolgsbestätigung. |
| CR-02 | Manifestversion 2 bindet Quellen, Reportwerkzeug, Fachbelege, Lauf-/JUnit-/GLM-Belege und relevante Buildartefakte. Verify liefert den PDF-Hash. HTTP liest PDF einmal und vergleicht genau diese Bytes. Suite-/Simulator-/GLM-Aktivität sperrt alte Reportdownloads; GUI entwertet/versteckt alte Links. | Node-Verify-/Inputänderungstests; `TestreportHttpTest.downloadRejectsReplacedBytesMissingProofAndWrongMethods`; Suite-HTTP- und UI-Tests. |
| CR-03 | Präzise Startzeit mit Offset ersetzt lexikalischen Zufallssuffixvergleich. Unklare Reihenfolge wird konservativ ausgewiesen und bewahrt bekannte Fehler. | Node: jüngerer FAIL derselben Sekunde; gleiche Zeit/Mehrdeutigkeit. |
| CR-04 | Lifecycle und bekannte Fach-/SQL-Fehler entscheiden vor Abbruchklassifikation. 0 erfolgreiche Verifikationen machen aus FAIL keinen Abbruch. | Node: FAIL mit 0 Verifikationen; Abbruch/RUNNING; alte grüne Textzusammenfassung gegen FAIL-Status. |
| CR-05 | Dieselbe strikte JUnit-Sammlung wie beim formalen Zertifikat ersetzt Summieren beliebiger target-XML. Prüfmarker, Klasseninventar, Hashes und Buildbindung werden geprüft. Skips sind kein Nachweis ausgeführter Pflichtprüfungen. | Node: alte XML neben neuem Lauf; RUNNING; Skips; widersprüchliche Zähler. |
| CR-06 | Jeder TOC-Anker muss eindeutig und geordnet vorhanden sein; Einrückung ist erlaubt. Der fertige zweite Satz wird erneut extrahiert und muss dieselben Kapitelstartseiten haben. Prozess-/Satzfehler verhindern Veröffentlichung. | Node: fehlende/doppelte/vertauschte Anker; verschobene Seiten; ENOENT; veränderter zweiter Satz. |
| CR-07 | Gemeinsame gegenseitige Reservierung für Report, Zertifikat, Suite, Simulator und GLM. Reporteingaben können über die HTTP-Routen nicht mehr während des Exports verändert werden. Busy-Status wird an andere GUI-Tabs gemeldet; lokale Exportflags verhindern Doppelklick und falsche Freigabe durch späte Antworten. | `TestreportHttpTest` und `CertificateSuiteHttpTest`; UI: Doppelklick, neue Suite während ausstehender Antwort, Fehlerfreigabe, veralteter Busy-Poll. |
| CR-08 | Historischer Fall-12-Screenshot bleibt als ursprüngliche Quelle erhalten, ist aber ausdrücklich als ungeeigneter Ergebnisnachweis bezeichnet. Die drei tatsächlich belegten neuen Browsergegenproben 1/6/12 sind mit Eingaben, Ergebnissen und unveränderten PNGs verlinkt. Keine Behauptung eines belegten externen Vollvergleichs aller zwölf Fälle mehr. | `doc/belege/echt-und-rechner-faelle-2026-10-27.md`, neun neue Belegdateien; SHA-identische Übernahme der Reviewbelege. |
| CR-09 | ECHT 5 bezeichnet 590 EUR ausdrücklich als gewählten Modell-Gesamtdeckel. Nettokaltmietobergrenze und kommunale Nichtprüfungsgrenzen werden unterschieden; keine vollständige Richtlinienumsetzung behauptet. | `EchtRechnerFallTest`: Gegenfall mit anderer KdU-Aufteilung; passende Katalog-/README-Texte. |
| CR-10 | 44.500-/31.000-EUR-Konstellationen sind historische Zahlen in synthetischen Vermögensvarianten. Fehlende Prüfung heutiger Vermögensprivilegierungen, insbesondere Kfz, ist offengelegt. 34.500/11.000 EUR werden als Modellwerte bezeichnet. | Feste Abwehr- und SQL-Erwartungen; Codekommentare, Katalog, README und Belegverzeichnis. |
| CR-11 | Absolute Nichtverfügbarkeit/Veröffentlichungsverbote wurden durch den tatsächlichen Recherchebefund ersetzt. Anonymisierung und konkrete Rechtsgrundlage werden nicht pauschal vorentschieden. | Recherchebericht und Katalog-/Belegtexte. |
| CR-12 | Windows-Befehlszeile wird mit CommandLineToArgvW zerlegt. Maven-Zuordnung verlangt genau eine passende explizite Hauptklasse und ein absolutes passendes -f/--file-POM; fremde/mehrdeutige Parameter werden abgewiesen. start.bat liefert diesen Beleg jetzt ausdrücklich. Identitätsrecheck vor Kill bleibt erhalten. | `tools/tests/operating-scripts.test.ps1`: gequotete/unquotierte Fremdklasse, fehlende Klasse/POM, fremdes POM, ähnliche Pfade, Klassenpfadüberschattung. Keine echten Listener beendet. |
| CR-13 | Zugangsdaten erscheinen weder in Bash-Quelltext noch in Prozessargumenten. SQL*Plus /nolog erhält den CONNECT-Text über stdin, SQL-Substitution ist vorher aus. $, &, Leerzeichen, einfaches Quote und @ bleiben erhalten. Nicht unterstützte Credentials mit doppeltem Quote/Zeilenumbruch/NUL werden ausdrücklich abgewiesen, nicht umgedeutet. | PowerShell-Argument-/UTF8-/stdin-Gegenproben; echter SQL*Plus-Login mit eigenem Testschema. |
| CR-14 | Monotone Gesamtfrist und begrenzte Einzelprozesse inklusive stdin-Schreibvorgang und Ausgabeerfassung. SQL*Plus im Container hat ebenfalls ein Zeitlimit; eigene Prozesse laufen ohne sichtbares Fenster. | Gemockte Deadline-/Dockerfälle und reale kurzlebige Hilfsprozesse mit blockiertem stdin; Unicode-Maximalpasswort. |
| CR-15 | Aktueller Browserbesitz ist von der historischen Worker-Liste getrennt. Nach abgeschlossenem Webcleanup löst ein anschließender Fachlauf keinen weiteren Browser-Scan aus. Vorzeitige Ablehnung unzulässiger synchroner Webkategorien mutiert keinen Laufstatus mehr. | `SimulatorBoundaryTest.completedWebWorkersDoNotTriggerBrowserScansInTheNextFachRun` und `unsupportedWebCategoriesNeverMutateStatusOrCreateWorkers`. |
| CR-16 | Unveränderte siebenfache Zertifikatsmatrix/GLM-Auswahl ist ausdrücklich als eigener Umfang dokumentiert. ECHT/RECHNER gehören nicht dazu; GUI, README, AGENTS und Workerkommentar sagen das. Der Managementreport erklärt seinen zusätzlichen Umfang separat. | `SimulatorDefaultsTest` sperrt die bewusst unveränderte Matrix; neue Kategorien separat mit Oracle geprüft. |
| CR-17 | Spannweite auf 227,00 bis 1.427,00 EUR korrigiert. | Katalogtests vergleichen Beschreibung und tatsächliche feste Erwartungen. |
| CR-18 | Zahlbeträge, Vermögenseinsatz, Endvermögen und Startbudget der neuen Erfolgsfälle sind jetzt feste Erwartungen. Produkt-Rechenblock und zusätzliche unabhängige arithmetische Kontrolle bleiben getrennt beschrieben. SQL-Konstanten werden zusätzlich gegen separate Testkonstanten geprüft. | `EchtRechnerFallTest`: tatsächliche SQL-Erwartungen und Anfangs-/Endwerte, Monatsparameter und sämtliche Präfix-Teiltöpfe. |
| CR-19 | Direkte Quellenlinks, Gericht/Datum/Aktenzeichen und klare Quellenstatus ergänzt; AGENTS nennt Kategorien/Limits und deren Nachweisgrenzen. | Lokale Dokumentlinks und Belegkopien geprüft. |

Neue Node-Reporttests und sichere PowerShell-Regressionen gehören über JUnit-Wrapper zum Mavenlauf. Das Quellinventar bindet auch Reportwerkzeug und Start-/Stoppskripte. Exportmarker werden zuletzt veröffentlicht; ein nachweislich beendeter Exportprozess blockiert den Wiederanlauf nicht dauerhaft. Unlesbare oder nicht eindeutig verwaiste Sperren werden konservativ mit Diagnose abgewiesen.

## Frische Verifikation

Alle Zahlen beziehen sich auf den korrigierten Arbeitsstand. Belege: [aktueller Belegordner](testsweet-neue-features-fixes-2026-10-27-belege/).

| Ausführung | Ergebnis |
|---|---|
| Hauptprojekt `mvn -q clean install` | Exit 0; **395 Tests, 101 Skips, 294 ausgeführt**, keine Fehler/Failures. Laufmarker COMPLETED. |
| testsweet `mvn -q -f testsweet/pom.xml clean test` | Exit 0; **346 Tests, 37 Skips, 309 ausgeführt**, keine Fehler/Failures. Laufmarker COMPLETED. |
| Reportwerkzeug | **24/24 Node-Regressionen** bestanden und in Maven eingebunden. |
| Produkt-/Simulator-UI | **47/47 Node-Tests** bestanden und in Maven eingebunden. |
| Betriebsskripte | **61/61 sichere PowerShell-Checks** bestanden und in Maven eingebunden. Ausschließlich fiktive Prozessidentitäten, gemocktes Docker und eigene kurzlebige Hilfsprozesse. |
| ECHT-Fachsimulator auf eigener Oracle Free | **5/5 PASS** einschließlich SQL-Endzustand. Lauf `run-20261027-101222-1390714291352317868`. |
| RECHNER-Fachsimulator auf eigener Oracle Free | **12/12 PASS** einschließlich SQL-Endzustand. Lauf `run-20261027-101226-17481924151665281028`. |
| Neuer DB-Bereitschaftshelfer, echter Login | **PASS**, 7,172 Sekunden bei 15 Sekunden Frist, eigener Dummyaccount; Containerstart/-stopp gesperrt. |
| Echter Managementreport-Export und Verify | **READY**, Kennung `D9F999A2`, PDF/DOCX neu erzeugt, 12 Seiten. Gesamtergebnis korrekt **UNVOLLSTÄNDIG**, weil weitere aktuelle Pflichtnachweise fehlen. PDF-Hash `04db0ae6c04b62f46fe56e4b773593feefe93226c26e0b7c1e886b36cd8d18fb`. |
| PDF-Layout / Inhaltsverzeichnis | Alle zwölf Seiten gerendert und als Gesamtübersicht geprüft; Seiten 2/10/12 zusätzlich einzeln kontrolliert. Tabellen und Texte lesbar, keine abgeschnittenen Inhalte; Kapitelstartseiten entsprechen dem Inhaltsverzeichnis. |

Die beiden Oracle-Läufe haben vollständige Logs/Reports, keine Nachweisfehler und passende Quellen-/Buildbindung; gemeinsamer Quellhash `4b17c5ef7021915d3610774dd1bb145341d1f596e0d1973eb5c699eb2fe702c1`. Zusätzliche direkte SQL-Snapshots sichern Zahlbeträge, Status, Budget und Vermögen. Das neu angelegte Schema `TSFIX_913BAFB1E3D04610` wurde anhand exaktem Namen und DB-Herkunftsmarker geprüft und wieder entfernt; anschließender Existenzzähler 0. Die Probe veränderte keine Facharbeitsdaten des bestehenden tx_demo-Schemas. Private temporäre Zugangsdaten wurden entfernt.

Ein erster Hauptlauf hatte zwar keine Testfehler, aber währenddessen änderte sich noch ein Betriebsskript. Der Nachweismarker stand korrekt auf UNVERIFIED. Dieser Lauf wurde nicht als gültiger Nachweis verwendet; nach Quellabschluss wurde der Hauptlauf vollständig wiederholt. Die gültigen Zahlen und Marker stehen in `junit-summary.json`. Übersprungene DB-Tests werden ausdrücklich nicht als ausgeführt gezählt.

Kein neuer externer GLM-Aufruf, kein vollständiger Sieben-Modi-Zertifikatslauf und keine neue externe Zwölf-Fälle-Rechnererhebung. Diese Grenzen bleiben sichtbar. Deshalb darf der Managementreport trotz erfolgreich verifizierter Korrekturen derzeit kein grünes Gesamtzertifikat ersetzen.

## Betriebshinweise

- Laufende Instanzen müssen neu gestartet werden, damit die geänderten Java- und GUI-Ressourcen gemeinsam geladen werden. Bestehende Anwendungen wurden für diese Korrektur nicht zwangsweise beendet.
- Alte manuelle Mavenstarts ohne absoluten POM-Beleg werden von stop.ps1 konservativ abgewiesen. Der korrigierte Standardstarter setzt `-f "%~dp0pom.xml"`; alte unklare Instanzen gegebenenfalls im ursprünglichen Fenster mit Strg+C beenden.
- Alte Reportmarker ohne Eingabebindung werden zurückgewiesen. Nach Änderungen an Quellen, Tests, Läufen oder Belegen muss der Report neu erzeugt werden.
- KdU-Komponentenprüfung und individuelle Vermögensprivilegierungen bleiben fachliche Modellgrenzen. Die ECHT-Konstellationen sind ausdrücklich keine vollständigen Nachrechnungen realer aktueller Bescheide.

Keine Commits, PRs oder Änderungen an laufenden Fremdprozessen durchgeführt. Die ursprünglichen Reviewunterlagen bleiben als historische Befundaufnahme erhalten.
