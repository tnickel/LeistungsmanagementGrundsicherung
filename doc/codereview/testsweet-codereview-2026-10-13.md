# Codereview testsweet

Prüfdatum: 13.10.2026. Geprüfter Commit: b77a05e296362d586a72c0009c2c9cf3680c4851.

testsweet ist als Simulator mit SQL-Endzustandsprüfung brauchbar. Seine grünen Ergebnisse und insbesondere das Testzertifikat sind aber derzeit kein belastbarer Nachweis einer vollständigen unabhängigen Fachprüfung. Es gibt drei hoch priorisierte Fehler: Erwartete Exceptions werden nicht zwingend verlangt, die automatische Python-Nachrechnung wird als LLM-Prüfung ausgegeben, und erkannte Rechenabweichungen verhindern ein bestandenes Zertifikat nicht.

**20 bestätigte Befunde: 3 × P1, 15 × P2, 2 × P3.** P1 bedeutet vorrangig zu beheben; P2 bedeutet regulär zu beheben; P3 betrifft begrenzte Vergleichs- oder Protokollfehler. Bestätigt heißt: durch ausgeführten Lauf, isolierte Gegenprobe oder einen eindeutigen statischen Datenfluss belegt. Nicht jeder Befund ist ein Fehler der Fachanwendung: Mehrere betreffen ausdrücklich die Aussagekraft ihrer Prüfung.

Produktionscode, vorhandene Nachrechnprotokolle und Zertifikate wurden nicht verändert. Neue Simulatorläufe liefen in einem eigens angelegten Oracle-Schema; die vorhandenen SWEET-Daten im Entwicklerschema wurden nicht aufgeräumt. Das Review-Schema wurde danach entfernt; die 2000 vorhandenen SWEET-Vorgänge im Entwicklerschema sind erhalten. Dieser Bericht ist eine Prüfung, keine Umsetzung der Korrekturen.

## Ausgeführte Prüfungen

Java-Laufzeit: 25.0.4.1; Kompilierung mit release 21. Maven 3.9.6. Datenbank: vorhandener Docker-Container lmg-oracle, FREEPDB1, separates Review-Schema. Selenium 4.27.0, Chrome und ChromeDriver 155.0.8059.39.

| Prüfung | Ergebnis |
|---|---|
| Hauptprojekt, mvn install ohne TX_URL | 249 registrierte Tests, 0 Fehler, 61 DB-Tests übersprungen |
| Hauptprojekt, mvn test mit separatem Oracle-Schema | 249 registrierte Tests, 0 Fehler, 1 Konfigurationstest wegen gesetzter TX_URL übersprungen |
| Ergänzung zum übersprungenen Konfigurationstest | Im ersten Lauf ohne TX_URL beide LmgConfigTests bestanden; damit alle 249 unterschiedlichen Haupttests über beide Umgebungen erfolgreich ausgeführt |
| testsweet, clean test | 16/16 bestanden, nichts übersprungen |
| Fachschicht POSITIV, 20 Mitarbeiter, 500 Aufträge, Seed 42 | 500/500 verifiziert, Exit 0 |
| Fachschicht NEGATIV, 20 Mitarbeiter, 500 Aufträge, Seed 42 | 500/500 verifiziert, Exit 0 |
| Fachschicht LAST, 20 Mitarbeiter, 600 Aufträge, Seed 42 | 600/600 verifiziert, Exit 0 |
| Fachschicht STRESS, 20 Mitarbeiter, 2000 User, Seed 42 | 4000/4000 Ergebnisse verifiziert, Exit 0 |
| Fachschicht NEGATIV, 20 Mitarbeiter, 500 Aufträge, Seed 17 | 499/500 verifiziert, Exit 1; falsche Budget-Testdaten in TF 443 |
| Echte Selenium-Browser, 2 Mitarbeiter, POSITIV | 3/3 verifiziert |
| Echte Selenium-Browser, 2 Mitarbeiter, NEGATIV | 7/7 verifiziert |
| Echte Selenium-Browser, 1 Mitarbeiter, POSITIV | 0/1, Fehler beim Kollegenlogin; Phase trotzdem FERTIG |
| Kassenisolation mit zusätzlichem Vorgang außerhalb SWEET | Simulator 10/10 grün; fremde bereite Zahlung wurde auf UEBERGEBEN gesetzt |
| Separate Decimal-Nachrechnung des POSITIV-500-Berichts | 488 Fälle mit Rechnung, 524 Rechenblöcke, 1233 Personeneinträge, 16707 Zahlenvergleiche, 0 Abweichungen |

Die separate Nachrechnung importiert weder Java-Berechnung noch das vorhandene nachrechnen_100.py. Sie vergleicht alle elf exportierten Zahlen je Person sowie Schonvermögen, BG-Summen, Vermögenseinsatz und das rechnerische Restbudget, soweit im Block vorhanden. Die 12 übrigen Positivfälle haben keinen Rechenblock. Die geprüften Eingaben betreffen alle sechs Regelbedarfsstufen, Mehrbedarfe, Einkommen und Vermögenseinsatz; die tatsächliche Kappung nach § 30 Absatz 6 tritt in diesen 524 Blöcken nicht ein.

Das Ergebnis bestätigt die Arithmetik der exportierten Eingaben unter den dokumentierten Modellregeln. Es beweist keine vollständige SGB-XII-Anspruchsprüfung. Beispielsweise sind Schwangerschaftsmerkmale im Export teilweise bereits durch dieselbe Produktionsfunktion monatsbezogen gefiltert, und KdU-Parameter sowie Satzstand sind hier auf den Teststandard 2026 beschränkt.

## Bestätigte Befunde

### TS01 P1 Erwartete Fachfehler werden bei normaler Rückkehr als Erfolg gewertet

Fundstelle: testsweet/src/main/java/de/allgemein/testsweet/Mitarbeiter.java:73–83; SzenarioBibliothek.java:2358–2373.

Nach normaler Rückkehr einer Aktion schreibt der Runner immer OK. Eine deklarierte erwartete Exception wird ausschließlich im catch-Zweig ausgewertet. Ein Schritt mit erwarteter VALIDATION, der normal zurückkehrt, endet in der isolierten Java-Probe als FERTIG_GESPIELT. Bei Operationen auf unbekannte IDs kann eine fehlerhafte No-op-Implementierung deshalb vollständig grün bleiben: Der Schritt meldet lediglich „unerwartet erfolgreich“, die unveränderte Datenbank erfüllt aber weiterhin alle SQL-Prüfungen.

Abhilfe: Auch den Erfolgszweig gegen die Fehlererwartung prüfen. Fehlt eine zwingend erwartete Exception, muss der Auftrag sofort fehlschlagen. Zusätzlich jeden deklarierten Negativschritt unabhängig vom DB-Endzustand verifizieren.

### TS02 P1 Python-Protokolle behaupten einen LLM-Lauf und mitgegebene Dateien

Fundstelle: doc/testzertifikat-werkzeug/nachrechnen_100.py:95 und :169–173; testsweet/src/main/java/de/allgemein/testsweet/SweetServer.java:475–477; doc/testzertifikat-werkzeug/generate.js:173.

Der Zertifikatsknopf startet ein lokales Python-Programm. Dieses liest nur die übergebene Markdown-Datei und verwendet fest programmierte Regeln. Es ruft kein Modell auf und liest weder Gesetzesgrundlagen noch Prüfauftrag. Trotzdem schreibt es GLM-5.3 als Modell sowie vollständig mitgegebene Gesetze und Prompt in den Protokollkopf. Der Zertifikatsgenerator übernimmt dies als durchgeführte KI-Nachrechnung.

Eine frühere KI kann das Python-Programm erstellt haben; das macht dessen späteren lokalen Lauf nicht zu einem neuen LLM-Aufruf. Für die Herkunft früherer manuell abgelegter Protokolle liefert das Repository allein keinen unabhängigen Nachweis.

Abhilfe: Automatische Python-Prüfung und externe LLM-Prüfung getrennt benennen und erfassen. Modell-/Dateiprovenienz ausschließlich aus tatsächlich ausgeführten Vorgängen übernehmen.

### TS03 P1 Rechenabweichungen verhindern BESTANDEN nicht

Fundstelle: doc/testzertifikat-werkzeug/nachrechnen_100.py:136–181; testsweet/src/main/java/de/allgemein/testsweet/SweetServer.java:481–490; doc/testzertifikat-werkzeug/generate.js:138–144 und :180.

Python meldet Abweichungen, endet aber weiterhin mit Exit 0. Java prüft nur Exitcode und Protokollexistenz. Der Generator vergibt für jede vorhandene Protokolldatei einen grünen Haken und bildet das Gesamturteil ausschließlich aus seiner Warnungsliste.

Gegenprobe: Absichtlich verfälschte BG-Gesamtbedarfe wurden von Python erkannt, der Prozess endete dennoch erfolgreich. Eine isolierte Ausführung der originalen Generator-Auswertung mit „0/1 bestätigt · 1 Abweichungen“ ergab bei vorhandenen Kategorien warnungen=[] und BESTANDEN. Es wurde dafür kein echtes Zertifikat überschrieben.

Abhilfe: Maschinenlesbares fachliches Prüfergebnis einführen. Abweichungen, unvollständige Prüfung und technische Fehler müssen das Zertifikat zwingend sperren beziehungsweise als fehlgeschlagen kennzeichnen.

### TS04 P2 Die Nachrechnung bestätigt fehlende und falsche Zwischenwerte

Fundstelle: doc/testzertifikat-werkzeug/nachrechnen_100.py:104–140.

Mehrbedarfe werden nur als Summe verglichen; bei dokumentierter Summe null überspringt „if mb_ist“ sogar diesen Vergleich. Die meisten Zahlen werden ausschließlich geprüft, falls das Feld vorhanden ist. Personenzahlbeträge werden nicht verglichen. Ein Fall ohne erkannten Rechenblock wird trotzdem als OK protokolliert.

Gegenproben am vorhandenen TF 11: Alle Mehrbedarfe auf 0 gesetzt → 0 Abweichungen trotz notwendiger 95,71 EUR; alle Personenzahlbeträge auf 999999,99 gesetzt → 0 Abweichungen; „block:“ umbenannt → TF 11 OK und 0/0 Blöcke bestätigt.

Abhilfe: Eingabeschema und Pflichtfelder zuerst vollständig validieren; jeden Zuschlag, Personenzahlbetrag und BG-Wert prüfen. Null ist ein regulärer Vergleichswert. Ein ausgewählter Rechenfall ohne Block ist ein Fehler.

### TS05 P2 Protokoll und Zertifikat sind nicht zuverlässig an dieselben Daten gebunden

Fundstelle: doc/testzertifikat-werkzeug/generate.js:69–76 und :143–144; testsweet/src/main/java/de/allgemein/testsweet/SweetServer.java:285–287 und :450–467.

Der Generator verwirft datenstand beim Einlesen und vergleicht ausschließlich Kalendertage. Ein Protokoll für einen alten Lauf kann deshalb einen neueren Lauf desselben Tages ohne Warnung abdecken. Vorhandener Beleg: stand.json nennt ein Protokoll zu run-20261013-112949, aber MD-Datenstand run-20261013-115159; warnungen ist leer. Die Auswahlregeln für „neuestes Protokoll“ unterscheiden sich außerdem zwischen Generator/Status und automatischer Nachrechnung: Dateinamenlänge einerseits, Suffixsortierung andererseits.

Der Wiederverwendungscheck der automatischen Nachrechnung berücksichtigt nur den Laufordner, nicht geänderte Datei-, Prompt-, Regel- oder Prüferstände.

Abhilfe: Lauf-ID, Hashes der tatsächlich geprüften Dateien, Prüfer-/Regelversion und Zeitstempel speichern und einheitlich auswählen. Das Zertifikat muss genau diese Evidenz referenzieren.

### TS06 P2 Fehlerhafte Fälle und Rechenblöcke werden falsch verrechnet

Fundstelle: doc/testzertifikat-werkzeug/nachrechnen_100.py:107, :137 und :174.

geprueft zählt Blöcke, abweichungen enthält einmal je fehlerhaftem Fall einen Eintrag. Die Ausgabe subtrahiert die Fallanzahl von der Blockanzahl. In einer Gegenprobe mit zwei fehlerhaften Blöcken desselben Falls meldete das Protokoll „1/2 Rechenblöcke bestätigt“, obwohl beide Blöcke fehlerhaft waren.

Abhilfe: Ergebnisse getrennt je Fall, Block und Zahlenvergleich erfassen und jeweils mit derselben Einheit aggregieren.

### TS07 P2 Prüfungen erlauben Freigabe ohne Freigabeschritt

Fundstelle: testsweet/src/main/java/de/allgemein/testsweet/SzenarioBibliothek.java:240, :1211 und :2483; WebSzenarioBibliothek.java:542–547; GlobaleVerifikation.java:20–22.

Ohne eigenen Freigabeschritt werden trotzdem ANGELEGT, BEREIT und UEBERGEBEN akzeptiert. Bei LAST und STRESS enthält der Topf überhaupt keine Freigabeschritte. Der als Begründung genannte Kassenlauf gibt ANGELEGT-Zahlungen nicht frei. Die globale Prüfung erkennt lediglich UEBERGEBEN ohne Freigabevermerk; eine unerlaubte automatische Umstellung auf BEREIT bleibt unentdeckt. Die isolierte Vergleichsprobe bestätigt, dass der LAST-Sollwert BEREIT akzeptiert.

Abhilfe: Ohne Freigabeschritt exakt ANGELEGT und einen leeren Freigabevermerk verlangen. Zulässige Statusalternativen nur aus tatsächlich möglichen Aktionen ableiten. Dieser Befund belegt eine Prüflücke, keine beobachtete Umgehung im Produktionsservice.

### TS08 P2 Der Budget-Negativgenerator kann ausreichendes Budget erzeugen

Fundstelle: testsweet/src/main/java/de/allgemein/testsweet/SzenarioBibliothek.java:1406–1408.

Die Untergrenze von zehn Euro kann das vermeintlich zu kleine Budget über den Anspruch heben. Im echten Oracle-Lauf NEGATIV/500/Seed 17 hat TF 443 Anspruch 6,00 EUR und Budget 10,00 EUR, erwartet aber BUDGET_ERSCHOEPFT. Die korrekte Anwendung ordnet sechs Euro an und lässt vier Euro übrig. Ergebnis: 499/500 verifiziert, Exit 1. Der Runner bezeichnet den unerwarteten Erfolg wegen TS01 zunächst sogar als OK.

Abhilfe: Generatorinvariante 0 ≤ Budget < Anspruch erzwingen; zu geringe Ansprüche gegebenenfalls neu ziehen. Zusätzliche isolierte Treffer: Seed 37/Fall 135, 65/99, 85/394, 97/496.

### TS09 P2 Generierte überlange Bescheide prüfen die falsche Validierung

Fundstelle: testsweet/src/main/java/de/allgemein/testsweet/SzenarioBibliothek.java:1416–1418 und :1502–1507.

Die gezogene Länge beträgt 13–24 Monate. Tatsächlich wird aber immer von 2026-12 bis 2026-01 aufgerufen. Damit reicht die Prüfung von > bis zum Bestehen; die Zwölfmonatsgrenze wird nicht erreicht. Seed 42 erzeugt 108 entsprechend beschriftete Schritte. Der vorhandene Bericht zeigt beispielsweise beim angeblichen 15-Monats-Fall die Abweisung wegen umgekehrter Grenzen.

Abhilfe: Chronologisch gültige Grenzen mit YearMonth.plusMonths erzeugen und die inklusive Dauer als Testdateninvariante kontrollieren. Der kuratierte Negativfall mit echtem überlangem Zeitraum ist vorhanden; deshalb ist die Zwölfmonatsprüfung nicht vollständig ungetestet.

### TS10 P2 Unterschiedliche Signaturen garantieren keine unterschiedlichen Negativkonstellationen

Fundstelle: testsweet/src/main/java/de/allgemein/testsweet/SzenarioBibliothek.java:1420–1424 und :1436–1467.

Die Signatur enthält Zufallswerte, die der ANLAGE-Zweig anschließend nicht verwendet. Dieser Zweig führt stets dieselben zwei SEPA-Aktionen aus und variiert tatsächlich nur drei ungültige IBANs und drei ungültige BICs. Beim Topf NEGATIV/500/Seed 42 stehen 45 solche Fälle nur neun unterschiedlichen fachlichen IBAN/BIC-Paaren gegenüber. Beispielsweise prüfen die IDs 11, 98, 172, 317, 430 und 493 dieselbe Variante.

Abhilfe: Eindeutigkeit aus den tatsächlich ausgeführten Eingaben und Aktionen bilden; Identifikatoren und verworfene Zufallsdaten nicht als fachliche Variation zählen. Die dokumentierte Garantie „keine Konstellation zweimal“ ist in dieser Form falsch.

### TS11 P2 Ein zugelassener Einmitarbeiter-Weblauf hat kein Freigabekonto

Fundstelle: testsweet/src/main/java/de/allgemein/testsweet/Simulator.java:350–352 und :546–557; WebSzenarioBibliothek.java:86–91.

Ein Mitarbeiter ist erlaubt. Der eingebettete Server legt dann nur sb-web-01 und das Administrationskonto an. Die Kollegenwahl gibt aber sb-02 zurück, das nicht angelegt wurde. Der echte Selenium-Lauf WEB/POSITIV/1 Mitarbeiter/1 Auftrag persistierte Vorgang, Fall und Zahlung, scheiterte danach beim Kollegenlogin und verifizierte 0/1. Mit zwei Mitarbeitern funktionierten dieselben Abläufe.

Abhilfe: Ein unabhängiges Freigabekonto unabhängig von der Zahl der Browserarbeiter bereitstellen oder diese Konfiguration vor Beginn explizit abweisen.

### TS12 P2 Webziel und eingebettete Serverkonfiguration können auseinanderlaufen

Fundstelle: testsweet/src/main/java/de/allgemein/testsweet/Simulator.java:539–575; WebHelfer.java:33–44.

Die Entscheidung für einen eingebetteten Server prüft fest Port 8080. Der tatsächliche Server verwendet dagegen config.port(). Die Browser-Basis-URL wird nochmals separat aus sweet.baseurl beziehungsweise SWEET_BASE_URL ermittelt und bleibt sonst localhost:8080. Mit beispielsweise LMG_PORT=18080 startet der Server auf 18080, während die Browser weiter 8080 öffnen. Bei einer externen Basis-URL kann zugleich fälschlich die Kontoauswahl für den eingebetteten Server gelten. Die SQL-Verifikation verwendet wiederum die Simulator-DB-Konfiguration.

Abhilfe: Ein gemeinsames Zielprofil aus URL, Servermodus, DB und Konten verwenden und vor mutierenden Aktionen dessen Konsistenz prüfen. Dieser Befund folgt direkt aus dem statischen Datenfluss; eine weitere Portkonfiguration wurde im Review nicht gestartet.

### TS13 P2 Der Simulator-Kassenlauf verändert auch Nichttestdaten

Fundstelle: testsweet/src/main/java/de/allgemein/testsweet/SzenarioBibliothek.java:911–912; src/main/java/de/allgemein/lmg/dao/ZahlungDao.java:67–75.

Der Simulator ruft den unbeschränkten produktiven Kassenlauf auf. Dessen Auswahl enthält keine SWEET- oder Laufbegrenzung. Eine bereits bereite Zahlung zu einem anderen aktiven Vorgang wird daher ebenfalls übergeben. Das widerspricht der Zusicherung, den Seed-Bestand unberührt zu lassen.

Echte Gegenprobe ausschließlich im separaten Review-Schema: Zusätzlicher Vorgang BG-REVIEW-FREMD mit einer BEREIT-Zahlung, anschließend POSITIV/10/Seed 42. Der Simulator meldete 10/10 und Exit 0; die fremde Zahlung stand danach auf UEBERGEBEN.

Abhilfe: Simulator nur gegen ein ausschließlich für den Lauf vorgesehenes Schema betreiben oder einen explizit auf den Testbestand begrenzten Kassenpfad bereitstellen. Der produktive Kassenlauf darf seine beabsichtigte Gesamtbestandssemantik behalten.

### TS14 P2 Schnelle Folgeläufe überschreiben ihre Vorgängerprotokolle

Fundstelle: testsweet/src/main/java/de/allgemein/testsweet/SweetLog.java:65–67 und :230–232.

Laufverzeichnisse haben nur sekundengenaue Namen. Dateien werden mit TRUNCATE_EXISTING geöffnet. Zwei unmittelbar nacheinander erzeugte SweetLog-Instanzen verwendeten in der isolierten Probe dasselbe Verzeichnis; der Marker der ersten Instanz war anschließend gelöscht. Das betrifft beispielsweise schnelle Fehlstarts und mehrere Simulatorprozesse, unabhängig von der Laufsperre innerhalb einer Instanz.

Abhilfe: Eine eindeutige Lauf-ID verwenden und das Verzeichnis atomar neu erzeugen. Bestehende Nachweise niemals für einen neuen Lauf öffnen.

### TS15 P2 Abgebrochene Läufe können ein grünes PDF-Gesamtergebnis erhalten

Fundstelle: testsweet/src/main/java/de/allgemein/testsweet/Testdokumentation.java:124–129; LastBericht.java:267–275.

Die Farbe und der Erfolgstext berücksichtigen nur explizite Fehlerstatus. OFFEN, IN_BEARBEITUNG und FERTIG_GESPIELT werden nicht als unvollständig berücksichtigt. Isolierte Probe mit einem offenen Auftrag und Phase ABGEBROCHEN: laufErfolgreich=false, aber grüner Kasten mit „0 von 1 … keine Abweichungen (100 % Konformität)“.

Abhilfe: Gesamtergebnis aus laufErfolgreich(), Abbruchstatus und vollständiger Statusbilanz ableiten. Ungeprüft ist kein Erfolg. Eine später im Bericht korrekte Fehlermeldung beseitigt den Widerspruch auf dem Deckblatt nicht.

### TS16 P2 Exportierte Budgeterwartungen widersprechen abgewehrten Buchungen

Fundstelle: testsweet/src/main/java/de/allgemein/testsweet/MdBericht.java:247–249; Testdokumentation.java:513–517.

Rechenblöcke geben das erwartete Restbudget immer als Startbudget minus Anspruch aus. Bei abgewiesener Anordnung bleibt das echte Sollbudget jedoch unverändert. Vorhandener Beleg: testsweet/logs/run-20261013-111840/testdokumentation.md, TF 28: Budget 261,00, Anspruch 301,00, budget_rest_erwartet=-40,00; die SQL-Prüfung erwartet und findet dagegen 261,00. Der Fall ist VERIFIZIERT. Der Rechenblock ist zwar als Nachweis der Abwehr betitelt, das ausdrücklich als erwartet bezeichnete Budgetfeld und die allgemeine KI-Prüfanweisung unterscheiden diese Semantik aber nicht.

Abhilfe: Hypothetischen Anspruch und erwarteten Buchungsendzustand getrennt ausgeben; Restbudget aus dem Szenario-/Buchungsergebnis ableiten. Eine Nachrechnung darf einen korrekt abgewiesenen Fall nicht wegen eines hypothetischen negativen Restbudgets als fehlerhaft bewerten.

### TS17 P2 Konfigurierte Web-Denkzeit bleibt wirkungslos

Fundstelle: testsweet/src/main/java/de/allgemein/testsweet/Simulator.java:489–490; WebMitarbeiter.java:53–198; SweetMain.java:45.

Die CLI nimmt eine Web-Denkzeit an, standardmäßig 200 ms, und der Bericht bezeichnet sie als Zeit je Schritt. WebMitarbeiter erhält diese Einstellung aber nicht und verwendet weder eine entsprechende Pause noch die Laufkonfiguration. Die beobachtete Browserzeit wird dadurch ausschließlich von Selenium, Antwortzeiten und Screenshots bestimmt.

Abhilfe: Die konfigurierte Denkzeit auch im Webarbeiter anwenden und zwischen Denk-, Aktions- und Wartezeiten unterscheiden. Der statische Datenfluss belegt die fehlende Verwendung; ein zusätzlicher Zeitbenchmark ist hierfür nicht erforderlich.

### TS18 P2 Fehlgeschlagene Aufträge können die Laufphase FERTIG ergeben

Fundstelle: testsweet/src/main/java/de/allgemein/testsweet/Simulator.java:520–521 und :685–686; testsweet/src/main/resources/sweet/app.js:397–402.

Die Schlussphase hängt nur von den globalen SQL-Prüfungen ab. Einzelfehler gehen nicht in diese Entscheidung ein. Der echte Einmitarbeiter-Weblauf meldete REVIEW_SUCCESS=false und zugleich PHASE=FERTIG. Die GUI verwendet diese Phase unmittelbar für ihre Statusdarstellung. Auch der Seed-17-Lauf besitzt erfolgreiche globale Prüfungen trotz fehlgeschlagenem Einzelresultat.

Abhilfe: Alle Auftragsresultate und globale Prüfungen gemeinsam auswerten. Positiv: laufErfolgreich() und die normale CLI erkennen den Misserfolg bereits korrekt; es handelt sich um eine widersprüchliche Statusdarstellung.

### TS19 P3 Ganzzahlvergleich verwirft Nachkommastellen

Fundstelle: testsweet/src/main/java/de/allgemein/testsweet/Verifikator.java:96.

Der Vergleich entfernt den Dezimalanteil statt numerisch zu vergleichen. In der isolierten Gegenprobe bestand Erwartet=2 bei Ist=2.9. Die vorhandenen COUNT-/Versionsabfragen liefern normalerweise ganzzahlige Werte, weshalb die unmittelbare Auswirkung begrenzt ist.

Abhilfe: Exakten numerischen Vergleich und Ganzzahligkeit des Istwertes verlangen.

### TS20 P3 Das Abschlusslog meldet eine falsche Bezugszahl

Fundstelle: testsweet/src/main/java/de/allgemein/testsweet/Simulator.java:680–688.

Die Zahl erfolgreich gespielter Aufträge wird erst nach der SQL-Verifikation anhand des inzwischen abgelösten Status FERTIG_GESPIELT ermittelt. Im tatsächlichen Positivlauf steht deshalb „500 von 0 erfolgreich gespielten Aufträgen vollständig verifiziert“.

Abhilfe: Die Bezugszahl vor dem Statuswechsel bestimmen oder aus der vollständigen Ergebnismenge berechnen.

## LLM Konfiguration Prompt und Dateien

Die überprüfte Oberfläche bietet die Anzahl der Fälle für eine externe Prüfmenge und die Anzeige des Prüfauftrags. Es gibt in diesem Pfad keine Anbieter-, Modell-, API-Key- oder Endpunktkonfiguration. README und HTML sagen ausdrücklich, dass die KI außerhalb der Anwendung läuft. Die Annahme einer bereits eingebundenen, automatisch aufgerufenen KI ist damit für diesen Stand falsch.

POST /api/llmpruefung/paket schreibt eine gekürzte positive Markdown-Prüfmenge und liefert die Pfade zu Prüfmenge, Gesetzesgrundlagen und Prüfauftrag zurück. Es übermittelt diese Dateien an keinen Anbieter und erzeugt kein tatsächlich versandtes mehrteiliges Anfragepaket. Die Anzeige der Namen und Dateigrößen ist kein Nachweis einer Übergabe.

Der Zertifikatsknopf ist ein anderer Pfad: Er wählt einen Bericht mit KOMBINATION-Fällen, startet Python mit genau diesem MD-Pfad und fest --max 100 und ruft danach den DOCX-/PDF-Generator auf. Die im GUI-Feld gewählte Paketgröße steuert diese automatische Nachrechnung nicht. Positive Kombinationen werden geprüft, NEG-KOMBINATION und kuratierte Fälle durch das Python-Filter ausgeschlossen.

Der Prompt ist für eine manuelle Prüfung überwiegend brauchbar: Er verlangt unabhängige Zwischenrechnungen, Vergleich mit SQL-Istwerten, Negativfallprüfung und die Benennung abgedeckter Regeln. Vier Punkte müssen präzisiert werden:

- Die Kopf-Prüfanweisung in MdBericht verlangt im Wesentlichen Abweichungen im Detail; llm-pruefauftrag.txt verlangt vollständige Rechenwege. Ein einheitliches Ergebnisformat fehlt.
- Ohne ausdrückliche Auswahl erlaubt der Prompt eine Stichprobe. Ein Paket mit 100 Fällen garantiert deshalb noch keine Prüfung sämtlicher 100 Fälle.
- Das geforderte Antwortformat enthält die vom Status-/Zertifikatsparser benötigten Metadaten wie datenstand, modell und ergebnis nicht als verbindliches Schema.
- Fehlende Dateien, abgeschnittene Fälle, unbekannte Regeln, nicht gelesene Quellen und nicht geprüfte Fälle brauchen ausdrückliche Fehlerzustände; sie dürfen keine Bestätigung erzeugen.

Die als vollständige Gesetzesgrundlagen beworbene Datei ist tatsächlich eine kurze Zusammenfassung mit Tabellen und Modellregeln. Sie bezeichnet sich selbst als Kurzfassung und enthält keine vollständigen Normtexte. Ein LLM kann damit die Projektregeln nachrechnen, aber keine unabhängige Vollprüfung aller gesetzlichen Voraussetzungen vornehmen.

## Aussagekraft der Berechnungen und Testabdeckung

Die zentrale Stärke ist die direkte SQL-Verifikation auf Oracle. Beträge werden mit BigDecimal scale-unabhängig verglichen; SQL-Fehler werden als Abweichung erfasst; globale Prüffehler beeinflussen das Laufresultat; die CLI liefert bei erfolglosen regulär abgeschlossenen Läufen Exit 1. Fachfehler führen im Produkt nicht zu blindem Transaktionsretry. Die Wiederaufnahme vermeidet nachweislich die Wiederholung bestätigter Schreibschritte und weist unklaren Commit-Ausgang als Fehler aus.

Die Sollwerte der Simulatorfälle entstehen jedoch aus derselben Leistungsberechnung wie die Fachanwendung. Auch die Monatsfilterung benutzt dieselbe Bewilligungsplan-Funktion. Damit prüft ein grüner Endzustandsvergleich überwiegend Persistierung und Ablauf, nicht unabhängig die zugrunde liegende Formel. Die neun GoldWerteTests sind eine vorhandene Gegenmaßnahme, ersetzen aber keine breite unabhängige Nachrechnung. Das gilt ebenso für eine Python-Zweitimplementierung, deren Regeln selbst unvollständig oder falsch sein könnten.

Die numerische Review-Nachrechnung fand in den tatsächlich exportierten 524 Blöcken keine Abweichungen. Die überprüften gesetzlichen Parameter wurden zusätzlich gegen amtliche Quellen abgeglichen: Regelbedarfsbeträge 2026 aus der [Anlage zu § 28 SGB XII](https://www.gesetze-im-internet.de/sgb_12/anlage.html), die hier verwendeten Mehrbedarfsregeln einschließlich Schwangerschaftsende aus [§ 30 SGB XII](https://www.gesetze-im-internet.de/sgb_12/__30.html), Erwerbsfreibetrag und Kappung aus [§ 82 SGB XII](https://www.gesetze-im-internet.de/sgb_12/__82.html) sowie die Grundbeträge des Schonvermögens aus [§ 1 der Durchführungsverordnung](https://www.gesetze-im-internet.de/bshg_88abs2dv_1988/BJNR001500988.html). Die gesetzliche Bestimmung des Schonvermögens setzt personenbezogene Voraussetzungen voraus, die das Projekt vereinfachend aus den Regelbedarfsstufen ableitet; diese Modellannahme wurde nicht als vollständige Rechtsprüfung bestätigt.

Weitere belegte Grenzen der Suite:

- Alle Simulator-Sollrechnungen verwenden KduParameter.AUS und Referenzsätze 2026. Konfigurierte KdU-Grenzen, Warmwasserabzüge und Satzwechsel werden dadurch nicht unabhängig abgedeckt; dafür existieren teilweise gesonderte Tests im Hauptprojekt.
- Der breite positive Generator erzeugt höchstens drei Kinder bei Alleinerziehenden. Die maximale Alleinerziehendenkappung und ein tatsächlich wirksamer Absatz-6-Deckel werden durch die ausgewertete breite Prüfmenge nicht nachgewiesen.
- NEGATIV prüft ausgewählte Endzustandswerte. Unverändertes Vermögen, Versionen und Journalbestand werden nicht für alle Abweisungen vollständig bilanziert. Eine unveränderte gesamte Datenbank wird damit nicht bewiesen.
- KONKURRENZ simuliert vorwiegend einen sequenziell veralteten Maskenstand. Der Stresstest verwendet zwei Wellen mit Phasenbarriere und regelmäßig einen eigenen Vorgang je User. Das beweist keine reproduzierbare Konkurrenz mehrerer Worker um denselben Vorgang.
- GlobaleVerifikation prüft Vorgangsanzahl, Übergabe ohne Freigabevermerk und gegebenenfalls Stornospuren. Die im Katalog behaupteten zusätzlichen globalen Stressprüfungen von Zahlungsanzahl, Zahlungssumme gegen Budgetminderung und vier Journalzeilen je User fehlen dort.
- LastBericht.java:128–175 nennt feste Referenzwerte mit 20 Threads und 2000 Usern und deutet eine Auftragsdauer als Sperrwartezeit. Die Suite erhebt keine entsprechende Lock-Warte-/Retry-Metrik. Aus Auftragsdauer allein folgt diese Ursache nicht.
- Der Zertifikatsgenerator übernimmt weiterhin manuell gepflegte Unit-/Web-Zahlen und historische erfolgreiche Kategorien. Fehlgeschlagene neuere Läufe werden bei der Referenzauswahl übersprungen. Ohne Code-/Buildbindung belegen ältere große Referenzläufe keinen unverändert korrekten heutigen Stand.
- Die 16 Simulator-Unit-Tests betreffen Katalog, Passwortfilter, globale Prüfungen, Profilzuordnung und kontrollierte Wiederaufnahme. Generatorinvarianten, fehlende erwartete Exceptions, Nachrechnschema, Zertifikatsurteil, Report-Abbruchstatus und eindeutige Laufordner besitzen keine entsprechende Absicherung.

## Weitere prüfbedürftige Fehlerpfade

Diese Punkte sind nicht in die 20 bestätigten Befunde eingerechnet, weil ihre vollständigen Auslöser nicht dynamisch durchgespielt wurden.

WebHelfer.java:532–539 wartet nur 800 ms auf einen Erfolgsdialog und verschluckt anschließend Exceptions. Ein verzögert eintreffender alter Dialog kann dadurch bei nachfolgenden Negativaktionen als vermeintliche Antwort der neuen Anfrage gelesen werden. Die normale Browserprüfung bestand; gezielte Antwortverzögerung und Dialogkorrelation stehen noch aus.

BrowserProzesse.java:98–103 beendet im Java-Fallback chromedriver.exe allein aufgrund des Pfadfragments testsweet. Dieser Zweig verlangt keine registrierte Profilzuordnung. Der normale Windows-Pfad verwendet zunächst das genauere PowerShell-Skript. Die Auswahl eines fremden entsprechend abgelegten Treibers ist statisch nachvollziehbar; ein realer fremder Prozess wurde dafür nicht gestartet oder beendet.

Nach dem 30-Minuten-Limit verwendet Simulator shutdownNow(), wartet aber nicht erneut auf tatsächlich beendete Worker, bevor Ressourcen und Logs geschlossen werden. Ob ein laufender JDBC-/Selenium-Schritt danach noch nachwirkt, wurde nicht durch einen 30-Minuten-Abbruchtest untersucht.

## Reproduktion und Nachweise

Die folgenden Standardbefehle räumen SWEET-Daten auf und können wegen TS13 auch andere bereite Zahlungen erfassen. Deshalb ausschließlich in einem isolierten Testschema mit den drei aktuellen DDL-/Seed-Dateien ausführen.

~~~powershell
# TX_URL, TX_USER und TX_PASS zuvor auf das isolierte Testschema setzen.
mvn install
mvn -f testsweet/pom.xml clean test
mvn -f testsweet/pom.xml compile exec:java '-Dexec.args=run 20 500 42 0 POSITIV'
mvn -f testsweet/pom.xml compile exec:java '-Dexec.args=run 20 500 42 0 NEGATIV'
mvn -f testsweet/pom.xml compile exec:java '-Dexec.args=run 20 600 42 0 LAST'
mvn -f testsweet/pom.xml compile exec:java '-Dexec.args=run 20 2000 42 0 STRESS'
mvn -f testsweet/pom.xml compile exec:java '-Dexec.args=run 20 500 17 0 NEGATIV'
~~~

Im Review wurden die Simulatoren direkt mit dem durch Maven ermittelten Classpath gestartet und user.dir auf ein separates temporäres Nachweisverzeichnis gesetzt. Die Browserläufe verwendeten einen temporären Wrapper, der dieselbe laufWebSynchron-Methode aufruft und anschließend die eigenen Testbrowser schließt. So bleiben die vorhandenen Laufordner und die Auswahlbasis der laufenden testsweet-GUI unverändert.

Lokale Nachweise:

- C:/Users/tnickel/AppData/Local/Temp/testsweet-review-runs-20261013/testsweet/logs/run-20261013-141634 — POSITIV 500.
- …/run-20261013-141640 — NEGATIV 500, Seed 42.
- …/run-20261013-141645 — LAST 600.
- …/run-20261013-141649 — STRESS 4000.
- …/run-20261013-141841 — NEGATIV 500, Seed 17; TF 443.
- …/run-20261013-141959 — Selenium POSITIV 3.
- …/run-20261013-142050 — Selenium NEGATIV 7.
- …/run-20261013-142251 — Selenium Einmitarbeiterfehler.
- …/run-20261013-142341 — Kassenisolation.
- C:/Users/tnickel/AppData/Local/Temp/testsweet-review-main-db-20261013.log — Haupttests mit Oracle.
- C:/Users/tnickel/AppData/Local/Temp/testsweet-review-unit-20261013.log — 16 Simulator-Unit-Tests.

Die separat erstellte numerische Nachrechnung und ihr Ergebnis liegen dauerhaft im Unterordner testsweet-review-2026-10-13 dieses Berichts. Dort liegen auch gegenproben.py und gegenproben-ergebnis.json zur Reproduktion der vier falschen Python-Prüfergebnisse. Sie sind Review-Nachweise, kein neuer produktiver Prüfpfad und kein LLM-Protokoll. Gegenproben am bestehenden Python-Prüfer und an der originalen Generator-Auswertung wurden mit temporären Eingaben durchgeführt; echte Zertifikate und stand.json blieben unangetastet.

## Reihenfolge der Nachbesserung

1. TS01 bis TS03 schließen: Negativschritte müssen scheitern, wenn die Exception fehlt; Provenienz korrekt benennen; Abweichungen müssen das Zertifikat blockieren.
2. TS04 bis TS10 schließen: vollständige Nachrechnvalidierung, Artefaktbindung, korrekte Zähler, strikte Vier-Augen-Sollwerte und gültige eindeutige Generatorfälle.
3. TS11 bis TS18 schließen: Web-Konfiguration, Datenisolation, eindeutige Laufnachweise und konsistente Report-/GUI-Ergebnisse.
4. Die kleinen Vergleichs-/Logfehler korrigieren und danach die genannten konkreten Gegenproben als gezielte Regressionen übernehmen.

Die regulären Rechenwerte und Hauptabläufe bestehen die ausgeführten Läufe. Die bestätigten Prüfer- und Nachweisfehler reichen jedoch aus, um die derzeitige Aussage „unabhängig vollständig geprüft und zertifiziert“ zurückzuweisen.

## Nachbesserung und erneute Prüfung am 13.10.2026

Der Bericht bis hierher dokumentiert den Zustand **vor** der Korrektur. Die 20 Befunde wurden anschließend in der Arbeitskopie behoben und mit gezielten Gegenproben geprüft. Die ursprünglichen Fehlernachweise bleiben stehen, damit Ursache und Änderung nachvollziehbar sind.

| Befunde | Korrektur und Gegenprobe |
|---|---|
| TS01 | Der Runner verlangt die deklarierte Exception auch dann, wenn der Fachaufruf normal zurückkehrt. Ein gezielter Negativtest schlägt bei fehlender Exception fehl. |
| TS02–TS05 | Der historische Python-Prüfer gibt sich nicht mehr als LLM aus und meldet Abweichungen mit Fehlercode. Die neue GLM-5.3-Anbindung speichert exakte Quelldateien, Prüfauftrag, Gesetzesgrundlagen, Batch-Requests und Antworten mit SHA-256. Der Zertifikatsgenerator nimmt nur validierte strukturierte API-Belege an; veränderte Quellen oder Antworten sperren den grünen LLM-Punkt. |
| TS06 | Fall-, Block- und Zahlenvergleichszähler sind getrennt; fehlende Pflichtfelder, Nullwerte und Personenzahlbeträge werden geprüft. Sieben Python-Gegenproben bestanden. |
| TS07–TS10 | Status ohne Freigabeschritt ist genau ANGELEGT, Budgetfälle sind fachlich echte Abweisungen, überlange Bescheidzeiträume werden tatsächlich erzeugt und die Kombinationssignatur bildet fachliche Werte ab. Der zuvor fehlerhafte Negativlauf mit Seed 17 besteht jetzt mit 500/500. |
| TS11–TS13 | Ein Browser-Worker bekommt einen zweiten Freigabe-Account; eingebettete Webserver nutzen den konfigurierten Port und dieselbe Datenbank. Externe Ziele müssen vor jeder Mutation ihre DB-Identität über die Admin-API belegen. Der Kassenlauf begrenzt sich auf den laufenden SWEET-Bestand. Ein Ein-Worker-Weblauf bestand mit 2/2 Positivfällen und 7/7 Negativfällen; ein Datenbanktest prüft, dass fremde Zahlungen unverändert bleiben. |
| TS14–TS20 | Laufordner sind kollisionssicher, abgebrochene Reports erscheinen nicht mehr grün, hypothetische Budgets werden von echten Erwartungen unterschieden, die Web-Denkzeit wird umgesetzt, Fehlerphasen bleiben ABGEBROCHEN, Dezimalwerte werden ohne Integer-Trunkierung verglichen und Nenner/Zähler stimmen im Gesamtbericht. |

Zusätzlich wurden die drei weiteren Fehlerpfade aus diesem Bericht nachgebessert: Dialoge werden auf den aktuellen Web-Schritt bezogen; der unsichere chromedriver-Pfadfallback wurde entfernt; nach einem Timeout wird auf tatsächlich beendete Worker gewartet, bevor Logs und Ressourcen geschlossen werden. Die dynamischen Browserproben prüften echte Chrome-Fenster, ersetzten aber keine künstlich provozierte 30-Minuten-Unterbrechung.

**Erneute Verifikation:** 55/55 testsweet-Java-Tests, darunter Oracle- und lokale HTTP-Tests; im Hauptprojekt 248 Tests mit Oracle bestanden und der dort wegen `TX_URL` übersprungene Konfigurationstest separat ohne `TX_URL` bestanden (249 unterschiedliche Tests). Im isolierten Oracle-Schema liefen 500/500 Positiv-, 500/500 Negativfälle mit Seeds 42 und 17, 600/600 Lastfälle und 4000/4000 Stressphasen. Der komplette HTTP-Pfad bestand erneut mit 500 Positiv- und 500 Negativfällen, echtem Verbindungstest zu GLM-5.3, sechs ausgewählten Fällen in zwei API-Batches sowie dem aktualisierten PDF- und Markdown-Report. Der Anhang auf der letzten PDF-Seite zeigt getrennt grün „Dateiübergabe geprüft“ und „Fachprüfung bestanden“, inklusive Request-, Response-, Dokument- und Quellhashes. Das Modell hat **sechs ausgewählte Fälle** beantwortet; dies ist keine unabhängige Vollprüfung aller 1000 Fälle.

Das kontrastkorrigierte Testzertifikat wurde als vierseitiges PDF gerendert und visuell geprüft. Es nennt die reale Stichprobe, die manuell gepflegten Web-Referenzläufe und die zwei Testumgebungen für die 249 Haupttests. Die Homepage-Kopie hat denselben SHA-256-Wert wie `doc/SDN-Leistung-LMG-Testzertifikat.pdf`; der Wert wird bei der Veröffentlichung geprüft.
