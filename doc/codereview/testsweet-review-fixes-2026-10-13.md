# Gegenprüfung und Nachbesserung der testsweet, 2026-10-13

## Nachtrag: verständliche Fehlerdialoge und Startnachweis

Nach dem unten dokumentierten vollständigen Prüfstand wurde auf Nutzerhinweis der
Fehlerdialog überarbeitet. Der historische Screenshot-Lauf
`run-20261013-213720-12981985943204521198` hatte 60 verifizierte Fälle ohne
Fach-/SQL-Abweichung, aber unverändert geladene Artefakte bei sechs inzwischen
geänderten Quelldateien. Das war ein technischer Nachweisfehler; daraus folgten
weder ein bestätigter Rechenfehler noch ein GLM-Ausfall. Die KI hatte veraltete
Eingangsbelege abgelehnt. Die frühere dreifache Wiederholung und die pauschale
Abbruchbeschreibung waren unverständlich beziehungsweise unpräzise.

Der Dialog benennt jetzt Fehlerart, Bedeutung, nummerierte nächste Schritte,
Protokollpfad und ausklappbare deduplizierte Diagnosen. Er unterscheidet tatsächliche
Buildabweichung, alte KI-Eingangsbelege, Testabweichung, drei erfolglose KI-Versuche
und Abbruch. Bekannte Testfehler werden durch zusätzliche technische Probleme nicht
verharmlost. Meldungen werden als Text gerendert; der initiale Fokus liegt auch im
kleinen Fenster auf dem Titel. Schließen quittiert nur die Meldung.

Zusätzlich korrigiert: `testsweet/start.bat` verwendet in GUI und Batch
`process-classes exec:java`, damit der Compilerstempel nach Kompilierung und
Ressourcen-Kopie erzeugt wird. Das bisherige `compile exec:java` übersprang diese
Phase. Änderungen am Hauptprodukt benötigen weiterhin dessen Neuinstallation.
Der ReviewOutcome-Text unterscheidet ungültige Nachweise von einem Abbruch.

Verifikation dieses Nachtrags: **17/17** Node-Oberflächen-Gegenproben und
**123/123** testsweet-JUnit-Tests mit Oracle, 0 Fehler und 0 Skips. Normaler
`start.bat`-Start separat auf Port 8107 geprüft; aktuelle UI-Ressourcen mit
`no-store`, altes Zertifikat korrekt HTTP 409. Dialog mit nachgestelltem historischem
Fehler im echten Browser geprüft; Titel, Schritte und technische Details lesbar.

**Aktualitätsgrenze:** Die folgende vollständige Verifikation und das dort genannte
Zertifikat beziehen sich auf den Stand vor diesem Nachtrag. Die Popup-Änderung hat
die Quellenbindung geändert; ein neues Gesamtzertifikat benötigt erneut aktuelle
Pflichtbelege. Der Nachtrag behauptet keine erneute vollständige Simulator-/GLM-
Verifikation. Historische Belege wurden nicht auf den neuen Stand umetikettiert.

Ausgangspunkt: `1641727`. Grundlage sind der eigene Gesamtbericht und der parallel
erstellte Beobachtungsbericht `testsweet-glm-zertifikat-codereview-2026-10-13.md`.
Der Fremdbericht wurde gegen den jeweiligen aktuellen Code geprüft; seine
Bewertungen und Fixvorschläge wurden nicht ungeprüft übernommen.

## Sachliche Korrekturen am Fremdbericht

- CR-101 beschreibt einen echten Zirkelschluss der ursprünglich erzeugten Sollbeträge.
  Die Behauptung, die gesamte Testlandschaft könne keine Rechenfehler erkennen, geht
  zu weit: unabhängige Goldwerte und eine Python-Gegenrechnung bestanden bereits.
  Jetzt prüft der Simulator zusätzlich sämtliche erzeugten Rechenblöcke unabhängig.
- CR-102/103: Der alte isolierte Kassenauftrag war zu schwach. Daraus folgt nicht,
  dass ein völlig kaputter Kassenlauf jeden positiven und negativen Workflow bestehen
  würde. Der Ersatz bereitet seine eigene BEREIT-Zahlung vor und prüft exakt diese
  Übergabe. Eine globale Forderung „keine BEREIT-Zahlung mehr vorhanden“ wäre bei
  parallel später freigegebenen Zahlungen fehlerhaft.
- CR-105: Falsche Zugangsdaten liefern im aktuellen Produktvertrag
  `400 / VALIDATION / Kennung oder Passwort ist falsch`. `401` steht für die fehlende
  Sitzung. Die neue Prüfung verlangt den tatsächlichen Vertrag einschließlich
  fehlendem Token; beliebige 500-/503-/Netzwerkfehler bestehen nicht.
- CR-111: Die Lücke lag im Simulator. Im Hauptprojekt existierte bereits
  `VermoegensfortschriebDbTest.stornoBuchtDenDokumentiertenEinsatzZurueck` einschließlich
  anschließender neuer Anordnung. „Im gesamten Testbestand unbelegt“ war falsch.
- Z-7 ist widerlegt: Die Stress-Eingabe zählt Nutzer. Drei Nutzer erzeugen sechs
  Aufträge, beide Wellen haben drei Aufträge. Gerade Nutzerzahlen zu verlangen wäre
  eine Verschlechterung.
- Die lokale SHA-256-Kette erkennt Inkonsistenzen und nachträgliche Teiländerungen.
  Sie ist keine digitale Signatur und schützt nicht gegen einen Angreifer, der
  sämtliche lokalen Belege neu erzeugen kann. Sie beweist auch keine interne
  Verarbeitung einzelner Tokens beim Modellanbieter.

## Umsetzung der bestätigten Befunde

| Befund | Korrektur und Prüfung |
|---|---|
| CR-101 | Eigenständige BigDecimal-Referenz ohne Produktionsrechner; alle Rechenblöcke aller Aufträge werden im Verifikator geprüft. Fehlende Eingaben oder Abweichungen machen den Auftrag rot. Handgerechnete Goldfälle und Mutationstests sichern die Referenz. |
| CR-102/103 | Eigene vorbereitete Zahlung, genau eine Übergabe, exakte Summe und Status, eigene eindeutige Journalkennung und exakte Journalzahl. Kein Mitsammeln konkurrierender fremder Zahlungen. |
| CR-104/106 | Konkrete Fall-IDs, Findings, Personen-/BG-Rechnungen und Prüfpunkte im Zertifikat; rote Fehlerdialoge auch bei fachlichem FAIL. GUI und Batch verwenden denselben Publisher. Negative und unvollständige Zertifikate bleiben erstellbar. |
| CR-107 | Atomare Start-, Zwischen- und Abschlussbelege für Simulator und GLM. Neuere gestartete/unvollständige Läufe verdrängen ältere Erfolge. |
| CR-108 | Katalog benennt tatsächliche Szenarien, Prüfungen und Auffüllung statt unzutreffender Prozentgewichte. |
| CR-109 | Legitimer Freigabevermerk, genau ein Freigabejournal und vollständiger Fachzustand nach erneuter Abweisung geprüft. |
| CR-110 | Vollständiger Fachzustand vor/nach jedem erwarteten Fehler; Budget, Vermögen, Versionen, Zahlungen, Bescheide und Fachjournal unverändert. Neue Einsichts-Audits sind ausdrücklich erlaubt. |
| CR-111 | Simulator prüft Vermögens-Storno, Rückbuchungs-Zwischenzustand, Neuanordnung und beide dokumentierten Vermögenseinsätze. |
| CR-112 | Bescheid von/bis wird mit den geplanten Monaten abgeglichen. |
| CR-113 | Alte Reportpfade sofort entwertet; PDF/MD gemeinsam erst nach vollständiger Erstellung veröffentlicht. Log-/Exportfehler bleiben im Laufurteil. |
| CR-114 | Diagnosehinweise in PDF und Markdown; tatsächliches SQL und tatsächlich gebundene Parameter stehen direkt am Prüfergebnis. Doppelte Beschreibungen können keine falsche SQL-Zuordnung mehr erzeugen. |
| CR-115 | Browser-Recovery erhält einen roten technischen Befund. Allgemeine Renderer-Timeouts gelten nicht als tote Session. |
| CR-116 | Abbruch wird als Abbruch/unvollständig angezeigt. Offene und ungeprüfte Aufträge werden ausgewiesen. |
| CR-117 | Unbestätigtes Roh-PASS wird nicht als bestandene Fachprüfung beschriftet. Transport, Fachurteil, unabhängige Zahlenprüfung und Prüfumfang bleiben getrennt. |
| CR-118 | Statische grüne Review-/Maven-Aussagen entfernt. Zertifikat liest echte aktuelle JUnit-XML-Ergebnisse samt Fehlern, Skips und vollständigem Klassen-/Methodenbestand. |
| CR-119 | Globale Vorgangsanzahl stammt aus dem Szenarioplan, nicht aus erfolgreichen Ist-Kontext-Einträgen. |
| CR-120 | GLM-Schema 2 verlangt alle Personen-/Rechenblock-/Zahlen-/V-/S-Prüfpunkte. Java und JavaScript prüfen die Zahlen unabhängig. Nichtleerer Freitext allein kann kein PASS erzeugen. |

Die eigenen Befunde R01–R16 werden damit ebenfalls adressiert: gemeinsames Urteil
für SQL und KI, aktuelle JUnit-Nachweise, getrennte Modus-/Kategorie-Matrix,
dauerhafte Laufzustände und Befunde, vollständige Negativzustände, echte skalare
SQL-Ergebnisse, beständige Logfehler und auf eigene Aktenzeichen begrenzte
Kassenläufe in den Hauptprojekt-DB-Tests.

Zusätzlich durch die Gegenprüfung gefunden und korrigiert: Ein Neubuild auf der
Platte darf den geladenen Stand einer laufenden JVM nicht ersetzen; ein
unveränderlicher Start-Snapshot verlangt dann Neustart. Produkt-/Prüferartefakte
einschließlich Ressourcen müssen über JUnit, Simulator und GLM übereinstimmen.
Ein neuer GLM-Lauf entwertet sofort alte geprüfte Reports. Fehler beim Statusabruf
nehmen alte grüne Bestätigungen zurück. API, PDF/MD und Clientdateien setzen
`Cache-Control: no-store`.

Ein zusätzlicher Fehler im statischen Dateiserver wurde geschlossen: Der
Entwicklungs-Fallback konnte Projektdateien einschließlich der lokalen
GLM-Konfiguration ausliefern. Er liefert jetzt ausschließlich öffentliche
UI-Dateien und Bilder; ein HTTP-Test prüft die Abweisung privater Pfade.
Es wurde kein tatsächlicher Fremdzugriff festgestellt; ein solcher lässt sich
aus der bloßen Existenz des Fehlers nicht ableiten.

Auch ein parsebares FAIL zu einer angeforderten Fall-ID aus einer formal
ungültigen Modellantwort bleibt als unverifizierter Hinweis erhalten. Eine
spätere gültige PASS-Antwort kann diesen Hinweis nicht unsichtbar machen; das
Gesamturteil bleibt mindestens UNVOLLSTÄNDIG. Solche Hinweise werden ausdrücklich
nicht als gesicherte Softwarefehler ausgegeben. Die Offline-Prüfer rekonstruieren
sie aus den protokollierten Fehlversuchen.

Der reale 500-Negativfall-Lauf deckte anschließend eine weitere Ressourcenlücke
auf: Die neuen Zustandsvergleiche erzeugten pro Snapshot eine neue physische
Verbindung; Oracle lehnte zeitweise Verbindungsaufbau mit ORA-12516 ab. Der Lauf
erhielt korrekt FAIL, nicht PASS. Snapshots und SQL-Verifikation verwenden jetzt
begrenzte vorhandene Pools. Ein Ressourcentest prüft 1.000 Ausleihen/Rückgaben,
1.000 Read-only-Transaktionen/Rollbacks und kein physisches Schließen geliehener
Verbindungen. Datenbanklimits wurden nicht verändert. Die Diagnose belegt keine
Ausschöpfung des Oracle-Prozesslimits.

Die abschließende echte GLM-Prüfung deckte außerdem einen Status-Latenzfehler auf:
Git-Metadaten wurden bei jeder Integritätsnachprüfung erneut ermittelt, und der
Prozess wartete vor dem Lesen einer potentiell großen Pipe-Ausgabe. Git-Ausgaben
werden jetzt in eine temporäre Datei geleitet; regelmäßige Hash-/Buildprüfungen
starten überhaupt keine Git-Prozesse. Eine Regression mit mehr als 60 KB
Git-Statusausgabe prüft den früher blockierenden Pfad.

Ein echter GUI-Export zeigte abschließend eine weitere Windows-Abhängigkeit:
LibreOffice stürzte mit dem tief im Projekt liegenden Profil ab; derselbe
Node-Aufruf mit kurzem Temp-Profil exportierte korrekt. Der erzeugte lange
Profilbaum enthielt Pfade über 260 Zeichen. Der Publisher verwendet jetzt ein
eigenes kurzes Temp-Profil je Export und räumt ausschließlich dieses Profil
auf. Native Exit-Codes, neue Ausgabedateien und Aktualität werden weiterhin
geprüft. Der gescheiterte Export hatte korrekt den alten PDF-Nachweis gesperrt.

Die Aktualitätsprüfung unmittelbar nach einem erfolgreichen Export fand noch
eine zu breite Dateiauswahl: Chrome-Komponenten legten im Browserprofil weitere
`manifest.json`-Dateien an. Diese waren versehentlich Teil des Prüfbeleg-Hashes.
Alle elf Prüfschichten und sämtliche Quellhashes blieben dabei unverändert;
nur Browser-Hintergrunddateien änderten sich. Das Nachweisinventar wird auf die
tatsächlichen gestarteten Simulator-/GLM-Prüfungen begrenzt. Änderungen echter
Start-, Status-, Manifest- und Ergebnisbelege entwerten das Zertifikat weiterhin.

Eine weitere Gegenprobe betrifft die neue JUnit-Belegkette: Bei einem
Surefire-Fehler läuft der normale Maven-Abschlusshook nicht. Ein nachfolgender
Teiltest durfte diesen Fehler nicht aus der Historie verdrängen. Der nächste
Lauf archiviert deshalb zuerst die XML-Dateien des unterbrochenen Vorgängers.
Ein bestandener anderer Test ersetzt keinen fehlgeschlagenen Test; ein Lauf
ohne vollständige Belege wird erst durch erneut ausgeführten vollständigen
Umfang aufgelöst.

Auch S-15 war ein echter falscher Erfolgskanal: Der Sammelcode `VALIDATION`
allein unterscheidet die Ursache nicht. Alle 29 erzeugten negativen Fachaktionen
verlangen jetzt zusätzlich mindestens einen ausdrücklich zugelassenen
Fehlergrund. Derselbe Code mit anderer Ursache scheitert im echten Runner.
Für S-14 prüfen sieben Seeds jeweils 500 Positivfälle und die fachliche
Eindeutigkeit aller 471 Kombinationsfälle; Fallnummern zählen dabei nicht als
unterschiedliche fachliche Eingaben.

## Weitere Gegenprüfung von Kern, Fachschicht, Web und Handbuch

Auch der zusätzliche Copilot-Bericht wurde gegen den Arbeitsbaum geprüft. Eine
Review-Vermutung wurde nicht allein wegen ihrer Einstufung als Fehler übernommen.
Bestätigte Lücken und zusätzliche durch Regressionen belegte Fehler wurden behoben:

- **Bescheid und Zahlung:** Die Anordnung verlangt unter der BG-Sperre genau einen
  passenden aktiven Bescheid und vergleicht die aktuelle Berechnung mit dessen
  Monatsplan. Überlappende aktive Bescheide, mehrdeutige Zuordnungen und beschädigte
  Snapshots werden abgewiesen. Gültige v1-Snapshots bleiben lesbar; ein beschädigter
  Druck-Snapshot führt zu einer kontrollierten Fehlermeldung statt HTTP 500.
  Kompensationen greifen ausschließlich auf nachweislich selbst erteilte Bescheide zu.
- **Saga und Abschaltung:** Ein ausgeführter Saga-Vorgang hält einen Datenbank-Claim;
  Recovery einer zweiten Instanz darf ihn nicht übernehmen. Kompensation erhält die
  nötige Ausführungszeit trotz Interrupt und stellt den Interrupt danach wieder her.
  Wartende Tasks werden beim Schließen abgebrochen und genau einmal statistisch
  abgeschlossen. Wachhund-Timer werden entfernt; Deadlines verwenden monotone Zeit.
- **Transaktionen und Pools:** Schlägt Rollback fehl, wird die Verbindung verworfen.
  Sie wird keinesfalls durch `setAutoCommit(true)` versehentlich committed. Fehler
  beim Audit-Nachlauf dürfen einen bereits erfolgreichen Commit nicht nachträglich
  als gescheiterte Fachtransaktion melden. Retry erkennt auch verkettete SQL-Fehler
  und begrenzt Wartezeiten ohne Überlauf. JMX-Instanzen überschreiben einander nicht.
- **HTTP und Konfiguration:** Begrenzungen für Verbindungen sowie Request-/Response-
  Zeiten werden vor der ersten HttpServer-Erzeugung gesetzt und mit echten Sockets
  geprüft. Ungültige Begrenzungen und vorhandene beschädigte Konfigurationsdateien
  verhindern den Start. Fehlgeschlagene Start-Recovery öffnet keinen HTTP-Listener.
  Überlast liefert 503 mit Retry-After. HEAD, erlaubte Methoden, strikte JSON-Typen
  und Zahlengrammatik sind geprüft. Stille Umwandlungen von Boolean-/Ganzzahlwerten
  sind beseitigt.
- **Gespeicherte Daten:** Insert-DAOs liefern die tatsächlich gespeicherte Zeile
  einschließlich DB-Zeitstempel und Cent-Skalierung zurück. Saga-Altersvergleiche
  verwenden konsistent die DB-Serverzeit; Sitzungseinstellungen dürfen sie nicht
  verschieben. Damit wird keine nachträgliche UTC-Semantik für alte TIMESTAMP-Werte
  erfunden.
- **Oberfläche und Simulator:** Die Voreinstellung eines Bewilligungszeitraums
  umfasst zwölf statt versehentlich dreizehn Monate, auch an Monats-/Jahresgrenzen.
  Feste Szenarien TF11–TF19 decken Folgemonate nach Entbindung/Vermögenseinsatz,
  vier bis sechs Kinder, tatsächlich greifende Mehrbedarfsobergrenzen, zentralen
  Warmwasserabzug, BG-KdU-Grenzen und Haushalte ohne Erwachsene ab. KdU-Parameter
  bleiben je Szenario unveränderlich und sind in Java-, JS- und Python-Gegenrechnung
  enthalten. Die Stop-Funktion prüft Projekt, Prozessidentität und Startzeit.
- **Handbuch:** Version, Abläufe, Rollen, Monatspläne, Konfiguration, testsweet,
  GLM-Status, Wiederholungen und Zertifikatsgrenzen wurden überarbeitet. Das
  Inhaltsverzeichnis enthält mit der PDF abgeglichene Seitenzahlen; Aufzählungen
  stehen als eigene Absätze. Historische Bildschirmbilder sind ausdrücklich als
  Orientierung gekennzeichnet. DOCX und PDF umfassen 48 Seiten und wurden vollständig
  gerendert und visuell geprüft.

### Tatsächlich geprüfter negativer GLM-Ausgang

Der Lauf `812eb4a5-65ce-4a5b-a28a-ef154fc2c332` lieferte trotz formalem Modell-PASS
einen falschen Personenbetrag für TF12/B1: 363,00 statt 863,00 EUR. Die 500,00 EUR
verwertbares Vermögen werden erst auf BG-Ebene abgezogen; der BG-Zahlbetrag 363,00
war richtig. Die unabhängige Referenz erkannte den Fehler und verweigerte PASS.
Das exportierte Zertifikat `c7b71d18-04f7-4559-ac80-ac46be0743ec` trug deshalb
**UNVOLLSTÄNDIG** einschließlich konkreter Abweichung. Das belegt eine fehlerhafte
Modellrechnung, keinen bestätigten Fehler des Produktionsrechners.

Prompt v4 stellt die Trennung von Personenbetrag und BG-Vermögensabzug sowie die
Pflicht zu allen V-/S-Prüfpunkten ausdrücklich klar. Nach dieser Quelländerung und
den letzten DAO-Korrekturen wurden beide Maven-Module, alle sieben Simulatorläufe
und die vollständige neue GLM-Stichprobe erneut ausgeführt. Alte Belege wurden
nicht für den neuen Stand freigegeben. Fehlversuche und der negative Export bleiben
im Laufarchiv erhalten.

## Grenzen der Aussage

Die unabhängige Arithmetik prüft den ausdrücklich dokumentierten Rechenstand 2026.
Sie beweist weder die juristische Vollständigkeit aller Regeln noch Fehlerfreiheit
aller möglichen Softwarepfade. Die GLM-Prüfung bleibt eine explizit ausgewiesene
Stichprobe. Ein bestandener Fall ersetzt keinen fehlenden Pflichtlauf.

Ein Gesamtzertifikat erfordert aktuelle Belege beider JUnit-Module, die sieben
unterstützten Modus-/Kategorie-Läufe, konsistente Builds und die nachgewiesene
GLM-Stichprobe. Bekannte Fehler dominieren fehlende Nachweise: NICHT BESTANDEN.
Ohne Fehler, aber bei fehlender Abdeckung: UNVOLLSTÄNDIG. Historische Rohurteile
werden ausdrücklich als unverifiziert beschriftet.

Browserprofile und alte Zertifikats-Stagingverzeichnisse sind lokale
Entwicklungsartefakte. Ihre Aufbewahrung ist kein bestandener Testnachweis.
Ein Protokoll-I/O-Fehler wird absichtlich als fehlender Nachweis gewertet und
nicht zugunsten eines grünen Ergebnisses ignoriert.

Ein Änderungs-/Aufhebungsbescheid-Verfahren ist weiterhin eine fachliche Ausbaustufe;
bei unpassender Bewilligung wird jetzt konservativ abgewiesen. Die im Fremdbericht
angesprochene Alleinerziehenden-Auslegung bleibt ausdrücklich fachlich zu klären;
es wurde keine ungesicherte Rechtsinterpretation in die Formel eingebaut. Optionale
Bankdaten sind im bisherigen Demonstrationsumfang kein belegter Fehler; ein echter
SEPA-Export mit Bankdatenpflicht ist nicht implementiert. Altdaten mit zonenlosen
Zeitstempeln benötigen für eine UTC-Migration eine geklärte Herkunft und Migration.


## Verifikation

Endgültiger gemeinsamer Quellenstand (219 gebundene Dateien):
`e59b70e255741264067f02ec2ac64dace5d9a19464b8319908e91228339a440b`.
Basis ist `1641727` mit lokalen Änderungen. Diese Nachbesserung ist noch nicht
committed oder gepusht. Maschinenlesbarer Abschluss:
`doc/codereview/testsweet-review-verifikation-2026-10-13.json`.

- Hauptprojekt: **306/306** JUnit-Tests bestanden, einschließlich echter Oracle-
  Prüfungen; 0 Fehler, 0 Skips. Der Konfigurationstest wurde ohne TX-Overrides
  zusätzlich isoliert ausgeführt; die vollständige Belegkette berücksichtigt dies.
- testsweet: **122/122** JUnit-Tests bestanden, 0 Fehler, 0 Skips.
- Zertifikatswerkzeug: **38/38** Node-Regressionen bestanden.
- Unabhängige Python-Nachrechnung: **9/9** Regressionen bestanden.
- Sichere Stop-Funktion: **9/9** synthetische PowerShell-Prüfungen, ohne reale
  fremde Prozesse zu beenden.
- Zusätzlich **4/4** gezielte UI-Prüfungen für Monats-/Jahresgrenzen und die
  Warnung bei beschädigten Bescheid-Snapshots.

| Pflichtlauf | Aktuell verifiziert |
|---|---:|
| Fachschicht Positiv | 500/500 |
| Fachschicht Negativ | 500/500 |
| Fachschicht Last | 120/120 |
| Fachschicht Stress | 42/42 Aufträge für 21 Nutzer |
| Weboberfläche Positiv | 60/60 |
| Weboberfläche Negativ | 60/60 |
| Weboberfläche Last | 30/30 |

Die sieben aktuellen Pflichtläufe umfassen **1.312 Aufträge**. Sämtliche darin
erfassten Rechenblöcke werden unabhängig nachgerechnet. Workflow-/Abwehrfälle ohne
Rechenblock werden nicht als eigene Zahlenrechnung ausgegeben. Positivfälle bestehen
aus 29 kuratierten Fällen und 471 unterschiedlichen Kombinationen, Negativfälle aus
neun kuratierten Arten und 491 unterschiedlichen Kombinationen. Web-Stress ist nicht
unterstützt und im Zertifikat ausdrücklich als nicht anwendbar ausgewiesen.

Echte GLM-5.3-Prüfung über die konfigurierte Z.ai-API:
`cd9f428b-73c9-4e06-bd76-5546d5dd353c`, **18/18 vollständige Fälle, 6/6 Anfragen**.
Ausgewählt wurden aus den frischen Fachschichtberichten die festen Positivfälle
TF11–TF19 und die Negativfälle TF01–TF09. Transport, vollständige strukturierte
Prüfabdeckung und unabhängige Zahlenprüfung sind bestätigt. Kein strukturiertes Finding und kein
unverifizierter Fehlerhinweis bleibt offen. Das ist eine ausgewiesene Stichprobe,
keine Behauptung einer LLM-Prüfung aller 1.312 Aufträge.

Die abschließende unabhängige Belegkontrolle bestätigt alle sechs unveränderten
Requests mit den drei vollständigen benannten Dokumenttexten sowie die rohen
HTTP-200-Antworten. Akzeptierte Ergebnisse stimmen mit den Rohantworten überein.
Die Stichprobe enthält 17 Rechenblöcke, 39 Personenberechnungen, 570 Zahlenwerte
und 204 SQL-/Workflow-Prüfpunkte; alle wurden vollständig bestätigt. Dieser Lauf
benötigte sechs Sendeversuche für sechs Anfragen, also keine Wiederholung.

Die Sichtprüfung fand eine falsche Formulierung im unverändert übernommenen
Modellfreitext von TF18: Der Endsplit 500/100 EUR wird dort auch als „50%/50%“
bezeichnet. Tatsächlich wird die BG-Restgrenze seriell ausgeschöpft; 500/100 EUR,
die Personenbeträge 1006/606 EUR und die BG-Summe 1612 EUR sind korrekt. Dieser
Freitextfehler bleibt hier ausdrücklich dokumentiert. Das strukturierte Prüf-PASS
ist keine semantische Verifikation jedes freien Modellsatzes. Weder die Rohantwort
noch das transportgebundene Ergebnis wurden nachträglich umgeschrieben.

Zertifikat `75892a8d-0b86-45ed-b769-b7b003e3d511`: **BESTANDEN**, alle elf Pflichtschichten
PASS. Native LibreOffice-Ausgabe und anschließende lokale Hashprüfung waren
erfolgreich. Der PDF-Download über `/api/zertifikat/pdf` liefert 200 und `no-store`;
der private Konfigurationspfad liefert 404 und `no-store`. Status- und PDF-Abrufe
lassen das gültige Zertifikat gültig.

- Zertifikat: `doc/SDN-Leistung-LMG-Testzertifikat.pdf`, **20 Seiten**, vollständig
  gerendert und visuell geprüft; lesbare Kontraste und vollständige Prüfpunkte.
- PDF-SHA-256: `4b3998630315936658f6b8c2f0e00a118c24ff395a188d7bd3b30551be532951`.
- Nachweis-Fingerprint: `989f205235a215fe1bae9d61e497cd0fedefe021ba7d144bb69c9eac59b7339a`.
- Handbuch: `doc/SDN-Leistung-LMG-Benutzerhandbuch.docx` und `.pdf`, **48 Seiten**.

Ausgeführt unter **Temurin/OpenJDK 25.0.4.1**, kompiliert mit `--release 21`, gegen
die lokale **Docker-Oracle-Free**-Entwicklungsdatenbank. Die Oracle-19c-Zweitumgebung
und ein eigener Java-21-Lauf wurden in dieser Nachprüfung nicht erneut ausgeführt.
Die Ergebnisse sind keine pauschale Garantie vollständiger Softwarefehlerfreiheit
oder einer abschließend juristisch geprüften Berechnung.

## Nachtrag 2026-10-14 — Zertifikatstests mit einem Klick

Der neue Button **„Mache alle Zertifikatstests“** führt die vollständige Pflichtkette
asynchron aus: Hauptprojekt mit Testdatenbank und isolierter Konfigurationsprüfung,
testsweet mit Testdatenbank, alle sieben unterstützten Simulator-Kombinationen,
anschließend die konfigurierte GLM-Stichprobe und automatisch den Zertifikatsexport.
Die sieben Simulatorläufe umfassen 1.312 Aufträge. Jeder Weblauf startet eine eigene
frisch gebaute Anwendung auf einem freien lokalen Port; ein bereits laufender alter
Server wird nicht ersatzweise verwendet.

Die Oberfläche zeigt abgeschlossene Phasen, den aktuellen Schritt, tatsächliche
Fallzahlen und verstrichene Zeit. Maven-Schritte ohne belastbare Prozentangabe zeigen
eine laufende Aktivitätsanzeige. Es gibt keine erfundene Restzeit. Browser-Neuladen
übernimmt den aktiven Lauf wieder. Der Ablauf lässt sich ausdrücklich abbrechen;
der GUI-Server muss währenddessen weiterlaufen.

Ein Fehler stoppt die Kette. Bestätigte Test-/GLM-Befunde und technische Fehler werden
unterschieden, die Meldung nennt das weitere Vorgehen und verweist auf das Protokoll.
Ein bestätigter Befund geht durch einen späteren technischen Fehler nicht verloren.
Während des Gesamtablaufs sind manuelle Starts, Konfigurationsänderungen und alte
Berichtsdownloads gesperrt. Auch verspätete alte Statusantworten können keinen alten
grünen Bericht mehr einblenden.

Der Zertifikatsnachweis bindet zusätzlich den vollständigen aktuellen Gesamtlauf:
Alle automatischen Einzeltests, sieben Simulatorverzeichnisse und der GLM-Lauf
müssen frisch und dem aktuellen Quellstand zugeordnet sein. Bereits beim Beginn
eines neuen Gesamtlaufs wird das vorige Zertifikat ungültig. Ein abgebrochener oder
unvollständiger Lauf kann kein grünes Zertifikat erzeugen. Der Zustandswechsel beim
Export verändert die stabile Nachweisbindung nicht.

Gezielte zusätzliche Prüfungen: 30 Browserlogik-Tests, 46 Zertifikatswerkzeug-Tests
und 9 Python-Regressionen bestanden. Die reale Oberfläche wurde außerdem auf
Abbrechen, Wiederaufnahme der Anzeige nach Neuladen und 409-Sperren für konkurrierende
Änderungen bzw. alte Zertifikatsdownloads geprüft. Ein erster Entwicklungsdurchlauf
brach beim Selenium-Start wegen eines fälschlich gesetzten Headless-Modus ab; der
Worker wurde korrigiert. Zwei weitere Entwicklungsdurchläufe wurden bewusst zur
Abbruchprüfung und vor abschließenden Nachbesserungen gestoppt. Diese historischen
Fehlschläge/Abbrüche wurden nicht umgeschrieben und erzeugten kein Zertifikat.

Der endgültige durchgehende Validierungslauf und seine Ergebnisse werden nach
seinem Abschluss im folgenden Abschnitt sowie in einem eigenen datierten
maschinellen Nachweis dokumentiert; die älteren Ergebnisse oben sind historische
Momentaufnahmen und belegen den neuen Quellstand nicht.

Im Durchlauf `17f17d52-20a6-422f-a455-3553d2aefa9d` bestanden die automatischen
Suiten (306 Haupttests, 146 testsweet-Tests, jeweils ohne Fehler/Skips) sowie beide
Web-Positiv-/Negativläufe mit je 60 Fällen. Der Web-Lastlauf stoppte bei 29 von
30 angelegten Vorgängen. Screenshot und Quellablauf belegen eine Race-Bedingung
im Selenium-Anmeldehelfer: Der Abmeldeknopf wurde einmal geprüft, bevor die
asynchrone Wiederherstellung einer bestehenden Sitzung fertig war. Anschließend
wartete der Helfer auf eine verdeckte Loginmaske. Der Gesamtlauf blieb korrekt
FAILED; GLM und Export wurden nicht ausgeführt. Das ist kein belegter Fehler der
fachlichen Berechnung. Die Wartebedingung wird kausal korrigiert und mit einem
gezielten Regressionstest abgesichert; danach ist ein neuer vollständiger Lauf nötig.

Der folgende Durchlauf `8db14038-a6cd-4f0d-822a-c9caa0b5a037` bestätigte die
Anmeldekorrektur: 306/306 Haupttests, 150/150 testsweet-Tests und alle sieben
Simulatorläufe mit 1.312/1.312 Aufträgen bestanden. Bei 6/18 GLM-Fällen brach der
Worker wegen einer Windows-`AccessDeniedException` beim atomischen Ersetzen von
`status.json` ab. Die Kette blieb korrekt technisch fehlgeschlagen; es gab keinen
Zertifikatsexport. Für kurzzeitige Dateisperren wird ausschließlich das atomische
Ersetzen begrenzt wiederholt. Ein dauerhafter Schreibfehler bleibt fatal. Der
historische Lauf und seine abgebrochene GLM-Prüfung bleiben unverändert erhalten.

Die Windows-Korrektur ist mit fünf gezielten Fehler-Injektionen abgesichert:
kurzzeitige Sperre mit anschließendem Erfolg, dauerhafte Sperre mit begrenztem
Abbruch und unveränderter alter Zieldatei, Interrupt während der Pause, bereits
bestehender Interrupt sowie andere I/O-Fehler ohne Wiederholung. Der atomische
Replace wird höchstens zehnmal versucht, mit neun Pausen von je 100 ms; nur
`AccessDeniedException` ist wiederholbar. Dieselbe Funktion schützt den GLM-Status,
ohne Request-/Antwortbytes zu ändern. Die 46 gezielten Suite-/LLM-Tests bestanden
mit null Skips. Die GUI wurde vor dem anschließenden vollständigen Lauf neu gestartet.

### Abschließender vollständiger Lauf — 2026-10-14

Gesamtlauf `43ae02a2-c623-48d6-9f53-c1ea8ede1ef0` wurde über den neuen Button in
der tatsächlichen Oberfläche gestartet und ohne weiteren Eingriff vollständig
abgeschlossen. Status **SUCCESS**, Zertifikat **BESTANDEN**, `certificateCurrent=true`.
Der Quellstand blieb während des ganzen Laufs unverändert:
`2c216dff27fe3cd641bf138c3a36594af2dabf939cc09e135ef6083ba7c81ff0`.

- Hauptprogramm: **306/306**, testsweet: **155/155**; null Fehler und null Skips.
  Sämtliche JUnit-Einzelfälle wurden nach Beginn dieses Gesamtlaufs ausgeführt.
- Fachschicht Positiv **500/500**, Negativ **500/500**, Last **120/120**, Stress
  **42/42**; Web Positiv **60/60**, Negativ **60/60**, Last **30/30**. Zusammen
  **1.312/1.312** Aufträge, mit gültigen aktuellen Build-/PDF-/MD-Nachweisen.
- GLM `0adb1d1a-8bdd-47be-a7c1-b91050acc801`: **18/18 Fälle, 6/6 Fallgruppen**,
  vollständige strukturierte Zahlen- und Prüfpunktabdeckung, kein bestätigtes oder
  provisorisches Finding. Enthalten sind 17 Rechenblöcke, 39 Personenberechnungen
  und 204 SQL-/Workflow-Prüfpunkte.
- **Acht Sendeversuche**: Batch 2 wurde wegen einer nicht unmittelbar als JSON
  lesbaren Antwort, Batch 5 wegen unvollständiger/doppelter Prüfabdeckung einmal
  verworfen. Jeweils nach 30 Sekunden vollständiges erneutes Senden; zweite
  Antwort vollständig bestätigt. Die beiden Wiederholungsrequests sind per
  SHA-256 bytegleich zum jeweiligen ersten Request. Keine fehlerhafte Antwort
  wurde als bestätigt gezählt. Alle acht Requests enthalten die drei jeweiligen
  benannten Dokumenttexte vollständig und bytegenau.
- Nach Export bleibt die Freigabe gültig: FINALIZING im eingefrorenen Exportbeleg
  und SUCCESS im aktuellen Status besitzen dieselbe stabile Nachweisbindung.
  Alle fünf veröffentlichten Ausgabedateihashes stimmen.

Zertifikat `7ef42a76-49a2-40cb-a93d-41dd2292d6ff`, **21 Seiten**, vollständig
gerendert und visuell geprüft. Der Kopf und die Tabellen sind kontrastreich,
Prüfpunkte stehen untereinander; keine abgeschnittenen Inhalte. Der neue
vollständige Zertifikatsprüflauf ist als eigene grüne Pflichtschicht enthalten.

- PDF-SHA-256: `364f621d1559de3982ccfc0adff59b1f4d1a40a29f649ea324bfa200f2e2897a`.
- Nachweis-Fingerprint: `a4da38c36f9571ac56e229a014ca44b3faccc9dd0b7f1b97f3218021febb260a`.
- PDF-Download: HTTP **200**, `application/pdf`, `Cache-Control: no-store`.
- Privater Konfigurationspfad: HTTP **404**. Status-/PDF-Abrufe erhalten die Freigabe.
- Aktueller maschineller Nachweis:
  `doc/codereview/testsweet-zertifikatslauf-verifikation-2026-10-14.json`.
- Tatsächliche Fortschrittsanzeige:
  `tmp/certificate-suite-live-progress.png`; Abschlussanzeige:
  `tmp/certificate-suite-success.png`.

**Grenze des GLM-Freitextes:** Im unverändert gespeicherten Erklärungstext von
NEGATIV TF06 schreibt das Modell einmal `400+90+60=500`. Korrekt ist **550**;
der strukturierte Rechenblock enthält korrekt KdU **550** und Gesamtbedarf
**1056**. Das ist ein Fehler in der Modellformulierung, kein belegter Fehler des
Hauptprogramms. Er wird ausdrücklich dokumentiert und nicht durch Umschreiben
der gebundenen Rohantwort verdeckt. Das strukturierte Prüf-PASS bestätigt Zahlen
und Prüfabdeckung, nicht die semantische Richtigkeit jedes freien Modellsatzes.

Die GLM-Prüfung bleibt eine Stichprobe. Dieser Lauf wurde unter Temurin/OpenJDK
25.0.4.1 mit Ziel `--release 21` gegen die konfigurierte Docker-Oracle-Free-
Testdatenbank ausgeführt; Oracle 19c und eine eigene Java-21-Laufzeit wurden
nicht erneut geprüft. Hashwerte belegen lokale Konsistenz von Requests,
Antworten und Dateien, keine digitale Signatur oder interne Verarbeitung beim
Modellanbieter und keine allgemeine Fehlerfreiheit der Software.
