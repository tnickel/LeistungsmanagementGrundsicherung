# Gesamtprüfung der testsweet vom 13 Oktober 2026

**Reviewurteil: NICHT FREIGEGEBEN.** Die aktuelle Ergebnis- und Zertifikatskette kann bekannte Fehler verdecken oder einen widersprüchlich grünen Gesamtreport ausgeben. Bestätigt sind **16 Befunde: 13 mit Priorität P1 und 3 mit Priorität P2**. Die Befunde sind offen; dieser Review verändert keinen Produktcode und stellt kein neues Zertifikat aus.

Geprüfter Versionsstand: Commit `16417272a194d2c8f12914148c3efae1f6bf0fd8`. Gegenstand sind der gesamte testsweet-Simulator einschließlich Fach- und Webszenarien, Verifikation und Reporterzeugung, seine automatischen Tests, die GLM-Anbindung mit Wiederholungen und Nachweisen sowie die Zertifikatserzeugung über GUI und Batchskript. Die im Zertifikat angeführten Hauptprojekttests wurden ebenfalls ausgeführt und ihre Einbindung geprüft. Rechtsgrundlagen wurden als bereitgestellter Projektstand behandelt; eine erneute externe Rechtsverifikation gehört nicht zu diesem Code-Review.

P1 bedeutet: vor einer belastbaren Freigabe dieser Prüfkette zu beheben. P2 bedeutet: zusätzliche belegte Lücke bei Nachweisführung oder Fehlererkennung. Die Priorität bewertet die Prüfkette; sie bedeutet nicht, dass jeder hypothetisch unerkannte Produktfehler aktuell in der Fachanwendung vorliegt.

## Ausgeführte Prüfungen

| Prüfung | Tatsächliches Ergebnis | Aussage |
|---|---|---|
| Hauptprojekt `mvn test` mit Docker-Oracle und gesetztem `TX_URL` | 249 Tests: 247 bestanden, 1 fehlgeschlagen, 1 übersprungen | Der fehlgeschlagene Kassenlauf-Test ist nicht gegenüber fremden Daten isoliert; siehe R16. Der Konfigurationsdateitest überspringt sich bei gesetztem `TX_URL`. |
| `LmgConfigTest` anschließend ohne Oracle-Umgebungsvariablen | 2/2 bestanden | Prüft die sonst übersprungene Konfigurationskaskade separat. Es ist kein zusätzlicher bestandener Hauptprojekt-Gesamtlauf. |
| Eigenständiges testsweet-Maven-Projekt mit Docker-Oracle | 56/56 bestanden, keine übersprungenen Tests | Einschließlich der vorhandenen LLM-HTTP-, Retry-, Report- und Kassenisolationstests. Diese Tests erkennen die unten reproduzierten Lücken nicht. |
| Python-Regressionen `test_nachrechnen.py` | 7/7 bestanden | Begrenzte deterministische Arithmetikprüfung. |
| Python-Zweitimplementierung auf `run-20261013-170451-16952134072093421681/testdokumentation.md` | 116 Rechenblöcke aus 108 Kombinationsfällen, 0 Abweichungen | Keine Vollprüfung der 120 Fälle: kuratierte Fälle und reine Workflowfälle werden vom Skript nicht vollständig erfasst. SQL, Fehlercodes und Workflow-Endzustand werden damit nicht unabhängig bewiesen. |
| Java-Gegenproben auf produktiven Klassen | Sechs Ausgaben bestätigen falsches Grün, fehlende Befunde, fehlende Kardinalitätsprüfung und verschluckte I/O-Fehler | Siehe `testsweet-gesamtpruefung-2026-10-13/ReviewEvidenceProbe.java` und `java-counterexamples.txt`. |
| Zertifikats-Gegenproben in isolierter Node-VM | 5/5 Fehlerpfade reproduziert | Produktiver Auswahl-/Aggregationscode mit virtuellem Dateisystem; kein echtes Zertifikat verändert. Grenzen der jeweiligen Probe stehen im JSON. |

Die Testausführung gegen Oracle hat im Entwicklungsschema durch den globalen Kassenlauf-Test fremde vorbereitete Zahlungen in den Status `UEBERGEBEN` versetzt. Das ist Bestandteil des Isolationsbefunds R16. Es war keine externe Bankübertragung. Ein vollständiger realer 100-Fälle-GLM-Lauf wurde für diesen Review nicht neu gestartet. Der vorhandene letzte Lauf steht auf `ERROR`, 9/100 akzeptierte Fälle und 3/34 akzeptierte Fallgruppen.

## Bestätigte Befunde

### R01 Geprüfter Gesamtbericht bleibt trotz GLM Befund grün

**P1.** [Testdokumentation.java:139](D:/AntiGravitySoftware/GitWorkspace/LeistungsmanagementGrundsicherung/testsweet/src/main/java/de/allgemein/testsweet/Testdokumentation.java:139) berechnet den Text `GESAMTERGEBNIS: BESTANDEN` ausschließlich aus `simulator.laufErfolgreich()`. Die Überladung mit KI-Nachweis hängt diesen nur hinten an und verwendet dasselbe Deckblatt. [MdBericht.java:51](D:/AntiGravitySoftware/GitWorkspace/LeistungsmanagementGrundsicherung/testsweet/src/main/java/de/allgemein/testsweet/MdBericht.java:51) bleibt bei `lauf_erfolgreich = true`.

**Reproduziert:** Ein SQL-erfolgreicher Simulatorlauf mit gültigem `FAIL`-Nachweis erzeugt vorne einen grünen BESTANDEN-Kasten. Die Markdown-Datei enthält gleichzeitig `lauf_erfolgreich = true` und `fachpruefung_bestanden = false`. Ein technischer Teilstatus darf erfolgreich bleiben; das ausdrücklich als Gesamtergebnis bezeichnete Urteil muss jedoch alle erforderlichen Prüfungen einbeziehen.

**Absicherung:** Parameterisierte Kombinationen von Simulator-PASS mit LLM-FAIL, UNVERIFIED und ERROR müssen in PDF und Markdown ein eindeutiges negatives oder unvollständiges Gesamturteil erzeugen. Einzelne SQL-Zeilen dürfen weiterhin korrekt als bestanden ausgewiesen werden.

### R02 Automatische Testergebnisse gehen nicht in das Zertifikat ein

**P1.** [generate.js:99](D:/AntiGravitySoftware/GitWorkspace/LeistungsmanagementGrundsicherung/doc/testzertifikat-werkzeug/generate.js:99) enthält eine fest eingetragene Maven-Testzahl und ein festes Datum. Haupttests, Goldwerte und Szenario-Regression erhalten ab Zeile 137 grundsätzlich grüne Haken. Surefire-Ergebnisse, Fehler, übersprungene DB-Tests und ausgefallene Testläufe werden nicht gelesen. Die eigenen testsweet-JUnit-Tests fehlen als Zertifikatsschicht vollständig.

**Reproduziert:** Ohne jegliche JUnit-Belege bleiben diese Zeilen grün und die Aggregation kann `BESTANDEN` liefern. Der in diesem Review tatsächlich rote Hauptprojekttest verändert diese statischen Angaben ebenfalls nicht. Die Formulierung „manuell gepflegt“ verhindert keinen falschen aktuellen Freigabestatus. Eine zusätzliche Robustheitsprobe akzeptiert sogar einen widersprüchlichen Simulatorbeleg `0/0` mit behauptetem Erfolg.

**Absicherung:** Aktuelle strukturierte Ergebnisse beider Maven-Projekte einlesen und an den getesteten Build binden. Fehlende Resultate, unvollständige Testinventare und erforderliche übersprungene Tests dürfen kein PASS ergeben. Ein vorhandener Testfehler muss das Gesamturteil zwingend auf nicht bestanden setzen.

### R03 Ergebnisse unterschiedlicher Modi verdrängen einander

**P1.** [generate.js:48](D:/AntiGravitySoftware/GitWorkspace/LeistungsmanagementGrundsicherung/doc/testzertifikat-werkzeug/generate.js:48) speichert den neuesten Lauf nur je Kategorie. [generate.js:83](D:/AntiGravitySoftware/GitWorkspace/LeistungsmanagementGrundsicherung/doc/testzertifikat-werkzeug/generate.js:83) bewertet diese Kategorie ohne getrennten Modus. Die Webreferenz `60/60 + 60/60` ist an Zeile 154 weiterhin fest grün.

**Reproduziert:** WEB/POSITIV schlägt fehl; danach besteht FACH/POSITIV. Bei ansonsten erfüllten Voraussetzungen lautet das Zertifikatsurteil `BESTANDEN` ohne Warnung. Der Webfehler ist aus der Aggregation verschwunden. Umgekehrt kann ein Weblauf den Fachlauf verdrängen; Web-Lasttests werden ebenfalls nicht als eigene Pflichtschicht geführt.

**Absicherung:** Eine ausdrücklich definierte Matrix der unterstützten Modi und Kategorien bewerten. Neuere Fachläufe dürfen Webfehler nicht löschen. Nicht unterstützte Kombinationen wie Web-Stress sind als nicht anwendbar auszuweisen, nicht als bestanden.

### R04 Gestartete abgebrochene Prüfungen können hinter älteren Erfolgen verschwinden

**P1.** [generate.js:38](D:/AntiGravitySoftware/GitWorkspace/LeistungsmanagementGrundsicherung/doc/testzertifikat-werkzeug/generate.js:38) überspringt Simulatorordner ohne `zusammenfassung.txt`. Der Ausnahmeabbruch in [Simulator.java:303](D:/AntiGravitySoftware/GitWorkspace/LeistungsmanagementGrundsicherung/testsweet/src/main/java/de/allgemein/testsweet/Simulator.java:303) schreibt nicht verlässlich einen terminalen Laufnachweis. Ebenso berücksichtigen [LlmReviewService.java:527](D:/AntiGravitySoftware/GitWorkspace/LeistungsmanagementGrundsicherung/testsweet/src/main/java/de/allgemein/testsweet/LlmReviewService.java:527) und [llm-evidence.js:114](D:/AntiGravitySoftware/GitWorkspace/LeistungsmanagementGrundsicherung/doc/testzertifikat-werkzeug/llm-evidence.js:114) nur Reviews mit fertiger `result.json`.

**Reproduziert:** Ein neuer unvollständiger Simulatorordner wird ignoriert; die Aggregation bleibt bei älteren Erfolgen grün. Die isolierte GLM-Auswahlprobe nimmt trotz neuerem unterbrochenem Review den alten PASS. Beim GLM-Start entstehen Manifest und Anfragen vor dem Endergebnis. Stirbt der Prozess dazwischen, kann selbst ein bereits empfangener fachlicher Befund beim Neustart aus der aktuellen Sicht verschwinden.

**Absicherung:** Den tatsächlichen Start persistent markieren, Zwischenstände und terminale Abbrüche atomar schreiben und den neuesten begonnenen Lauf berücksichtigen. Vorschaupakete müssen von wirklich gestarteten Prüfungen unterscheidbar sein. Prozessabbruch vor dem ersten Aufruf, nach einer Antwort und vor dem Abschluss gezielt testen.

### R05 Fehlerzertifikat wird verhindert während altes PDF verfügbar bleibt

**P1.** [SweetServer.java:527](D:/AntiGravitySoftware/GitWorkspace/LeistungsmanagementGrundsicherung/testsweet/src/main/java/de/allgemein/testsweet/SweetServer.java:527) verweigert bei FAIL, UNVERIFIED oder ERROR die neue Zertifikatserstellung. [SweetServer.java:586](D:/AntiGravitySoftware/GitWorkspace/LeistungsmanagementGrundsicherung/testsweet/src/main/java/de/allgemein/testsweet/SweetServer.java:586) liefert ein vorhandenes Zertifikat ohne Abgleich mit dem aktuellen Prüfstatus weiter aus. Der separate Generator kennt an [generate.js:194](D:/AntiGravitySoftware/GitWorkspace/LeistungsmanagementGrundsicherung/doc/testzertifikat-werkzeug/generate.js:194) nur BESTANDEN oder BEDINGT; echte fachliche Fehler werden mit fehlenden Belegen in einer Warnungsliste vermischt.

**Trigger:** Nach einem erfolgreichen Zertifikat findet ein neuer GLM-Lauf einen Fehler. Ein aktualisiertes negatives Zertifikat kann über die GUI nicht entstehen; das alte möglicherweise grüne PDF bleibt abrufbar. Auch der alternative Generator stellt einen echten FAIL nur als „BEDINGT“ dar.

**Absicherung:** Auch negative und unvollständige Nachweise erzeugen. Echte Befunde benötigen ein rotes `NICHT BESTANDEN`, fehlende oder abgebrochene Pflichtprüfungen ein eindeutiges `UNVOLLSTÄNDIG`. Historische Zertifikate brauchen ihre unverwechselbare Lauf-/Versionsbindung und dürfen nicht als aktueller Nachweis erscheinen.

### R06 Fachliche GLM Fehler lösen kein auffälliges Popup aus

**P1.** [app.js:674](D:/AntiGravitySoftware/GitWorkspace/LeistungsmanagementGrundsicherung/testsweet/src/main/resources/sweet/app.js:674) zeigt den GLM-Fehlerdialog nur bei `state === "ERROR" && retryExhausted === true`. Ein gültiges fachliches FAIL, UNVERIFIED oder ein anderer technischer Fehler erfüllt diese Bedingung nicht. Es erscheint lediglich eine Statuszeile.

**Absicherung:** Ein fachlicher Befund muss sofort auffällig erscheinen, mit Fall-ID, Befund und Logpfad. Die Anzeige einmal je Review/Befund auslösen und auch nach Neuladen korrekt wiederherstellen. Tests müssen fachliches FAIL, teilweisen FAIL mit späterem API-Ausfall und ausgeschöpfte Format-Retries getrennt prüfen.

### R07 Konkrete GLM Befunde fehlen im ausgehändigten Report

**P1.** [LlmReviewService.java:286](D:/AntiGravitySoftware/GitWorkspace/LeistungsmanagementGrundsicherung/testsweet/src/main/java/de/allgemein/testsweet/LlmReviewService.java:286) übernimmt keine Einzelbewertungen, Findings oder Rechenbegründungen in den Status. [KiPruefAnhang.java:48](D:/AntiGravitySoftware/GitWorkspace/LeistungsmanagementGrundsicherung/testsweet/src/main/java/de/allgemein/testsweet/KiPruefAnhang.java:48) druckt Status, Zähler und Transportbelege, aber keine fachlichen Befunde. Der Zertifikatshelfer verdichtet sie ebenfalls auf einen generischen Text.

**Reproduziert:** Ein kontrollierter Befund `CONTROLLED_FINDING_123` steht im übergebenen Nachweis, fehlt aber im erzeugten Markdown. Tritt nach einem gültigen FAIL später ein API-Fehler auf, wird der Gesamtstatus ERROR; der zuvor erkannte Softwarefehler ist nur noch in den Rohdateien `result.json` und `protocol.md` sichtbar.

**Absicherung:** Bekannte fachliche Befunde unabhängig von technischen Abschlusszuständen dauerhaft erhalten. Befundliste und Rechenbegründung mit Fall-/Block-/Quellbezug in den Reportanhang aufnehmen; die Fehlerzusammenfassung gehört auch auf das Deckblatt und in das Zertifikat.

### R08 Ein nichtleerer LLM Freitext belegt keine vollständige Nachrechnung

**P1.** [LlmReviewService.java:424](D:/AntiGravitySoftware/GitWorkspace/LeistungsmanagementGrundsicherung/testsweet/src/main/java/de/allgemein/testsweet/LlmReviewService.java:424) und [llm-evidence.js:29](D:/AntiGravitySoftware/GitWorkspace/LeistungsmanagementGrundsicherung/doc/testzertifikat-werkzeug/llm-evidence.js:29) prüfen, ob `reasoning` ein nichtleerer String ist. Rechenblock-, Personen- und Zahlenabdeckung werden nicht geprüft. Der Prompt fordert vollständige Nachrechnung, der Validator belegt nur die Antwortstruktur und die Fall-IDs.

**Reproduziert:** `verdict=PASS`, `findings=[]`, `reasoning="Nicht nachgerechnet. Nur Sollwerte uebernommen."` werden akzeptiert. Das ist eine zur Prüfanforderung widersprüchliche Freigabe. Auch die vorhandenen Mock-Antworten verwenden denselben vorgegebenen Rechenfreitext für unterschiedliche Fixturefälle.

**Absicherung:** Rechenblöcke, Personen, Zwischenwerte und Workflowprüfungen strukturiert zurückfordern und ihre Abdeckung gegen die gelieferten Eingaben prüfen. Numerische Werte mit einer unabhängigen Referenzberechnung vergleichen. Eine plausible LLM-Begründung allein kann keine fachliche Fehlerfreiheit garantieren; ein Zertifikat muss den tatsächlich geprüften Umfang nennen.

### R09 Nachweise sind nicht an den tatsächlich getesteten Softwarestand gebunden

**P1.** [testsweet/pom.xml:29](D:/AntiGravitySoftware/GitWorkspace/LeistungsmanagementGrundsicherung/testsweet/pom.xml:29) lädt die lokal installierte Fachschicht `transaktionssteuerung:0.5.8`. [LlmReviewService.java:344](D:/AntiGravitySoftware/GitWorkspace/LeistungsmanagementGrundsicherung/testsweet/src/main/java/de/allgemein/testsweet/LlmReviewService.java:344) bindet Berichte, Prompt und Gesetze, aber weder geladenes Produktartefakt noch Prüfcode oder Commit. Alte Nachweise bleiben bei späteren Codeänderungen gültig, solange die Berichtdateien unverändert sind. Der Generator nennt trotzdem aktuelle Arbeitskopie-Änderungen.

**Abgrenzung:** Der Hash der aktuell installierten `Leistungsberechnung.class` stimmte bei dieser Prüfung mit dem aktuellen Hauptprojektbuild überein. Eine aktuell veraltete Calculator-JAR ist damit nicht belegt. Belegt ist das fehlende Verfahren, das eine solche Abweichung erkennt oder vorhandene Nachweise nach Codeänderungen entwertet.

**Absicherung:** Getestetes Produktartefakt, Testartefakt, relevante Build-/Quellhashes und Testlauf zusammen binden. Ein neuer Code-/Buildstand muss einen älteren Nachweis als historischen Stand ausweisen. Dirty-Working-Tree, unveränderte Versionsnummer bei neuem Build und veraltete lokal installierte JAR gezielt testen.

### R10 Exportfehler lassen den Lauf grün und können alte Reports ausliefern

**P1.** [Simulator.java:767](D:/AntiGravitySoftware/GitWorkspace/LeistungsmanagementGrundsicherung/testsweet/src/main/java/de/allgemein/testsweet/Simulator.java:767) protokolliert PDF-/Markdown-Ausnahmefehler nur. Die zuvor erfolgreiche Phase bleibt erhalten. `letztesPdf` und `letztesMd` werden beim nächsten Laufstart nicht entwertet; ihre Getter bevorzugen die alten Pfade.

**Reproduziert:** Ein absichtlich nicht beschreibbares PDF-Ziel lässt `laufErfolgreich()` auf true und liefert weiter den vorherigen PDF-Pfad. Die spätere Zusammenfassung kann entsprechend `Lauf erfolgreich: true` enthalten; die CLI signalisiert Erfolg, obwohl der erforderliche neue Nachweis fehlt.

**Absicherung:** Fach-/SQL-Ergebnis und Vollständigkeit der Nachweise getrennt erfassen; ein Gesamterfolg benötigt beide. Beide neuen Dateien in einem frischen Verzeichnis vollständig erzeugen und erst dann veröffentlichen. Endpunkte müssen an die aktuelle Lauf-ID gebunden sein. Reportfehler nach vorherigem grünen Lauf und beim Serverneustart prüfen.

### R11 Batch Export kann ein altes grünes Zertifikat erneut kopieren

**P1.** [erstelle-zertifikat.bat:24](D:/AntiGravitySoftware/GitWorkspace/LeistungsmanagementGrundsicherung/doc/testzertifikat-werkzeug/erstelle-zertifikat.bat:24) prüft den LibreOffice-Exitcode nicht und kontrolliert nur, ob eine PDF-Datei existiert. Bei normalem Gebrauch existiert die PDF schon vor dem Export. Das Skript kopiert diese anschließend erneut nach `doc`, selbst wenn die neue DOCX-Ausgabe einen Fehlerstatus enthält und der PDF-Export gescheitert ist.

**Absicherung:** Frisches Staging-Verzeichnis, erfolgreiche Prozesse und neu erzeugte Ausgaben verlangen. Kopierfehler ebenfalls als Fehler behandeln. Eine kontrollierte LibreOffice-Ausfallprobe mit vorhandener alter grüner PDF muss Veröffentlichung verhindern und einen Fehlerstatus liefern.

### R12 Negativfälle prüfen den behaupteten unveränderten Zustand nur teilweise

**P1.** [WebSzenarioBibliothek.java:254](D:/AntiGravitySoftware/GitWorkspace/LeistungsmanagementGrundsicherung/testsweet/src/main/java/de/allgemein/testsweet/WebSzenarioBibliothek.java:254) prüft bei WEB-NEG-OHNE-MONAT nur Vorgangsexistenz und keine Zahlung. Budget, Vermögen, Fallinhalt/-version und Fachjournal fehlen. [SzenarioBibliothek.java:2076](D:/AntiGravitySoftware/GitWorkspace/LeistungsmanagementGrundsicherung/testsweet/src/main/java/de/allgemein/testsweet/SzenarioBibliothek.java:2076) prüft bei NEG-SELBSTFREIGABE nur Status und fehlenden Freigabevermerk.

**Konkreter Fehler, den diese Assertions nicht erkennen:** Die Fachanwendung zeigt den erwarteten Fehler, verändert aber vorher das Budget. Die bestehenden Erwartungen können weiterhin sämtlich bestehen. Umfangreichere Kombinationsfälle schließen diese Lücke in den kuratierten Fällen nicht.

**Absicherung:** Den fachlichen Zustand vor und nach jedem erwarteten Fehlversuch vergleichen. Erlaubte Vorbereitungen und ausdrücklich erlaubte Audit-Einträge müssen getrennt festgelegt sein. Mutationstests für Budget, Vermögen, Zahlung/Betrag, Fallversion und Fachjournal müssen jeweils rot werden.

### R13 SQL Skalarprüfungen ignorieren zusätzliche Zeilen

**P2.** [Verifikator.java:65](D:/AntiGravitySoftware/GitWorkspace/LeistungsmanagementGrundsicherung/testsweet/src/main/java/de/allgemein/testsweet/Verifikator.java:65) liest genau die erste Ergebniszeile und prüft keine zweite. Die Web-Zahlungsqueries an [WebSzenarioBibliothek.java:102](D:/AntiGravitySoftware/GitWorkspace/LeistungsmanagementGrundsicherung/testsweet/src/main/java/de/allgemein/testsweet/WebSzenarioBibliothek.java:102) wählen nach BG und Monat ohne eindeutige Zahlungs-ID oder Gesamtzahl.

**Reproduziert:** Ein ResultSet mit zwei passenden Zeilen wird nach genau einem `next()` als OK akzeptiert. Das Schema verhindert doppelte aktive Zahlungen, erlaubt aber mehrere stornierte Zeilen. Ein fehlerhaftes WEB-STORNO mit zusätzlicher stornierter Zahlung kann deshalb bei passendem erstem Betrag und Budget unentdeckt bleiben.

**Absicherung:** Skalarprüfungen müssen exakt eine Zeile verlangen. Zahlungsszenarien müssen die Gesamtzahl der erwarteten Zahlungszeilen und deren Identitäten prüfen, einschließlich stornierter Zeilen.

### R14 Protokollverlust bleibt außerhalb des Laufurteils

**P2.** [SweetLog.java:215](D:/AntiGravitySoftware/GitWorkspace/LeistungsmanagementGrundsicherung/testsweet/src/main/java/de/allgemein/testsweet/SweetLog.java:215) und Zeile 226 melden Flush-/Schreibfehler nur auf stderr; Closefehler werden ignoriert. Der Simulator erhält keinen dauerhaften Fehlerstatus.

**Reproduziert:** Ein kontrolliert geschlossener interner Writer erzeugt I/O-Fehler, ohne diese an den Aufrufer weiterzugeben; der erfolgreiche Simulatorstatus bleibt bestehen. Gerade LAST/STRESS verweisen für Einzelnachweise ausschließlich auf diese Protokolle.

**Absicherung:** Ein dauerhafter Logfehler muss die Nachweisvollständigkeit auf fehlgeschlagen setzen. Abschließenden Flush bestätigen, bevor ein vollständiger Erfolg veröffentlicht wird. Dateisystemfehler nach erfolgreichem Öffnen und während des Abschlusses testen.

### R15 Grüne Dateiübergabe nach fachlichem FAIL wird nicht erneut validiert

**P2.** [LlmReviewService.java:112](D:/AntiGravitySoftware/GitWorkspace/LeistungsmanagementGrundsicherung/testsweet/src/main/java/de/allgemein/testsweet/LlmReviewService.java:112) prüft gespeicherte Belege bei Statusabfragen nur für PASS. Nach einem vollständig transportierten fachlichen FAIL kann `transportVerified=true` bleiben, obwohl eine Response-Datei anschließend gelöscht oder verändert wurde. Nach einem Neustart wird derselbe Zustand dagegen neu geprüft.

**Absicherung:** Die Integritätsprüfung muss unabhängig vom fachlichen Urteil erfolgen. Ein gültiger Transport mit fachlichem FAIL ist zulässig; ein nachträglich beschädigter Transportnachweis muss auch bei FAIL seine grüne Bestätigung verlieren. Dateiänderung und Löschung für PASS, FAIL und UNVERIFIED testen.

### R16 Hauptprojekt Kassenlauftests greifen auf fremde Entwicklungsdaten zu

**P1.** [CodereviewRegressionDbTest.java:173](D:/AntiGravitySoftware/GitWorkspace/LeistungsmanagementGrundsicherung/src/test/java/de/allgemein/lmg/db/CodereviewRegressionDbTest.java:173) ruft den globalen Kassenlauf auf und erwartet anschließend null Übergaben. Der Test präpariert nur seine eigene historisierte BG; andere aktive BEREIT-Zahlungen im Schema sind damit nicht ausgeschlossen. Weitere Hauptprojekttests verwenden ebenfalls den globalen Aufruf, etwa `EchteDbVerifikationTest`, `HistorisierungSperrenDbTest`, `ZahlungRennenDbTest` und `SzenarioRegressionTest`.

**Tatsächlich beobachtet:** Im Docker-Entwicklungsschema gab der Aufruf 14 zurück, der Test wurde rot und fremde vorbereitete Zahlungen wurden auf UEBERGEBEN gesetzt. Das belegt einen Testisolationsfehler, nicht die Übergabe der historisierten Zielzahlung. Die Assertion auf deren Status liegt nach dem fehlgeschlagenen Mengenvergleich und wurde deshalb nicht mehr ausgeführt.

**Absicherung:** Haupttests in einem dedizierten Testschema ausführen oder den bereits vorhandenen Präfix-begrenzten Serviceaufruf verwenden. Eine absichtlich vorhandene fremde BEREIT-Zahlung muss unverändert bleiben; das Ergebnis des eigenen Testlaufs muss reproduzierbar sein. Nicht nur diesen einen Test, sondern alle globalen Kassenlaufaufrufe im Testcode prüfen.

## Grenzen und bereits belegte Funktionen

Die gespeicherten GLM-Requests enthalten tatsächlich die drei vollständigen Dokumenttexte für die jeweils ausgewählten Fälle. Es werden nicht nur lokale Dateipfade gesendet. Fall-IDs, angefordertes und zurückgegebenes Modell, Antwortabschluss sowie Request-/Response- und Dokumenthashes werden geprüft. Formatfehler lösen höchstens drei identische Sendeversuche mit 30 Sekunden Pause aus; erst ein akzeptierter Versuch erhöht den Fortschritt. Das sind belastbare Transport- und Formatnachweise, keine Garantie fachlich vollständiger Modellarbeit.

Erwartete Fachfehler werden im Mitarbeiterpfad bereits auf den tatsächlich geworfenen Code geprüft. Ausbleibende oder falsche Fehler werden nicht einfach akzeptiert. Globale SQL-Fehler werden in der regulären Verifikationskette bereits als nicht bestanden gewertet. Diese funktionierenden Schutzmechanismen beseitigen die hier nachgewiesenen Lücken bei Assertions, Gesamturteil und Nachweisveröffentlichung nicht.

Ein Zertifikat kann den definierten Prüfumfang und seine belegten Ergebnisse bestätigen. Es kann weder die Fehlerfreiheit der gesamten Software beweisen noch eine kleine GLM-Stichprobe als Vollprüfung ausgeben. Aktuell berechnet testsweet seine meisten Sollbeträge mit derselben Produktionsklasse wie die Fachanwendung. Unabhängige Goldwerte, die Python-Gegenrechnung und strukturierte LLM-Nachrechnung sind deshalb getrennt auszuweisen und an ihre tatsächliche Abdeckung zu binden.

## Reihenfolge für die Korrektur

1. Ein gemeinsames Ergebnisobjekt für Fach-/SQL-Tests, automatische Tests, LLM-Befunde, technische Vollständigkeit, Transportintegrität und Reporterstellung schaffen. Ein bekannter fachlicher Fehler darf durch einen späteren technischen Fehler nicht verschwinden. Gesamtstatus aus den erforderlichen Schichten ableiten.
2. Laufbeginn, Zwischenstand und Abschluss dauerhaft speichern. Auswertung anhand von Lauf-ID, Modus/Kategorie und getesteten Artefakten; ältere Erfolge dürfen neuere Abbrüche nicht verdecken.
3. PDF, Markdown, Zertifikat und Popup aus demselben Ergebnisobjekt erzeugen. Negative Ergebnisse müssen ausstellbar und auffällig sein. Die tatsächlichen Befunde gehören in die ausgehändigten Dokumente.
4. Surefire-Ergebnisse beider Projekte, Testinventar und Skips automatisch einlesen; Buildbindung und vollständige Modus-/Kategoriematrix ergänzen. Statische grüne Prüfschichten entfernen.
5. GLM-Antworten strukturiert nach Personen/Rechenblöcken/Prüfungen validieren, Negativfälle mit Zustandsvergleich absichern und SQL-Kardinalität prüfen. I/O-Fehler und alle Exportwege nachweisbar schließen.
6. Die Gegenproben als Regressionen übernehmen. Freigabekriterium: jeder absichtlich eingespeiste fachliche, technische oder Nachweisfehler erzeugt den passenden roten beziehungsweise unvollständigen Gesamtstatus in API, Oberfläche, Report und Zertifikat. Erst danach einen vollständigen Oracle-/Web-/GLM-Verifikationslauf für einen eingefrorenen Build ausführen.

## Reproduktion

Die Belege liegen im Unterordner `testsweet-gesamtpruefung-2026-10-13` neben diesem Bericht. `certificate-counterexamples.js` führt ausschließlich produktiven Auswahl-/Aggregationscode in einer virtuellen Dateisystemansicht aus. Der für diese Proben vorausgesetzte gültige LLM-Vorgängernachweis ist ausdrücklich ein Stub; die Probe behauptet keine gefälschte API-Kommunikation zu erkennen. `ReviewEvidenceProbe.java` ruft die produktiven Java-Klassen auf und injiziert kontrollierte Zustände beziehungsweise ein ResultSet. Die I/O-Probe verwendet einen absichtlich geschlossenen internen Writer, keine echte defekte Festplatte.

```powershell
node doc/codereview/testsweet-gesamtpruefung-2026-10-13/certificate-counterexamples.js
mvn -q -f testsweet/pom.xml dependency:build-classpath "-Dmdep.outputFile=target/review-classpath.txt"
$reviewCp = "testsweet/target/classes;target/classes;" + (Get-Content testsweet/target/review-classpath.txt -Raw).Trim()
javac -encoding UTF-8 -cp $reviewCp -d tmp/review-20261013-gesamt/classes doc/codereview/testsweet-gesamtpruefung-2026-10-13/ReviewEvidenceProbe.java
java -cp ("tmp/review-20261013-gesamt/classes;" + $reviewCp) de.allgemein.testsweet.ReviewEvidenceProbe tmp/review-20261013-gesamt/probe-output
python -m unittest discover -s doc/testzertifikat-werkzeug -p test_nachrechnen.py
```

Die Java-Probe benötigt zuvor kompilierte Hauptprojekt- und testsweet-Klassen. Sie erzeugt ausschließlich kontrollierte Probeberichte unter `tmp`; es wird kein Simulatorlauf gegen Oracle gestartet. Die regulären Oracle-Testprotokolle dieser Prüfung liegen lokal unter `tmp/review-20261013-gesamt/main-tests-oracle.log`, `testsweet-tests-oracle.log` und `config-tests-isolated.log`. Keine Zugangsdaten oder vollständigen Laufprotokolle sind Bestandteil des neuen Reviewberichts.
