# Web-Gegenprüfung des Berichts 2026-10-15-k4p9

Die Prüfung bezieht sich auf den lokalen Bericht mit **16** Befunden. Die mitgeteilte
Kurzfassung mit nur einem bestätigten Befund beschreibt nicht dessen vollständigen Inhalt.
Die folgende Bewertung trennt implementierte Fehlerkorrekturen und offene Modellgrenzen.

## Bewertung

| Befund | Gegenprüfung | Maßnahme |
|---|---|---|
| FB-20261015-04 Objektzuständigkeit | Die fehlende Objekttrennung ist real: alle angemeldeten Sachbearbeiter können den gemeinsamen Arbeitsbestand erreichen. Das ist für voneinander getrennte Zuständigkeiten unzureichend. Ein Umgehungspfad einer bereits implementierten Zuständigkeitsregel ist jedoch nicht belegt; ein solches Modell existiert nicht. CR-035 führt Team-/Zuständigkeitsfilter bereits als offene Ausbaustufe. | Keine erfundene Owner-only-Regel: sie würde gemeinsame Bearbeitung und Vier-Augen-Freigabe beschädigen. Die Grenze bleibt ausdrücklich offen; daraus folgt keine Eignung für einen Betrieb mit getrennten Zuständigkeiten. |
| FB-20261015-10 Kontensegment in Ausnahme | Bestätigt. Bei falscher Feldanzahl wurde der gesamte Eintrag einschließlich Passwort ausgegeben. Auch eine Fehlermeldung mit dem unbekannten Rollenwert konnte bei verschobenen Trennzeichen ein Geheimnis enthalten. | Fehler nennen nur Eintragsposition und erwartetes Format bzw. erlaubte Rollen. Kein eingegebenes Feld wird in die Exception übernommen. |
| FB-20261015-14 Service-Storno ohne Rolle | Bestätigt als fehlende serviceinterne Autorisierung, aber kein neu belegter Zugriff über den authentifizierten Eintritt. Die Servicemethode bekommt eine Kennung, keine authentifizierte Identität. Direkte Java-Aufrufer gehören bereits zur vertrauenswürdigen Anwendung. Ein frei übergebbares Admin-Boolean würde diese Grenze nicht gegen bösartigen Code absichern. | Die HTTP-Adminprüfung bleibt vor dem Aufruf und zusätzlich im Stornohandler. Bestehende HTTP-Regressionsfälle prüfen auch alternative ID-Schreibweisen. Ein gemeinsamer authentifizierter Aufruferkontext für alle administrativen Services wäre ein eigener Architekturausbau. |
| FB-20261015-16 Einbettung | Bestätigt: `frame-ancestors` fehlte. | `frame-ancestors 'none'` ergänzt, auch im Druckdokument und in darstellbaren Fehlerantworten. |

## Integration weiterer Reparaturen

FB-20261015-02: Ein `TaskOutcomeUnknownException` ergibt HTTP 409 mit
`AUSGANG_UNBEKANNT`. Die Antwort behauptet keinen Rollback und verlangt die Prüfung
des Verarbeitungsstatus. Auch der allgemeine technische HTTP-500-Text fordert keine
blinde Wiederholung. Die Browserdatei lädt nach einer unklaren Fach-Schreibantwort nur
lesend nach. Eine automatische Schreibwiederholung findet nicht statt. Das gilt auch
für einen Verbindungsabbruch, bei dem keine HTTP-Antwort eingetroffen ist.

Für Auszahlungen bleibt die Vorgangsnummer je Sitzungskennung, Bedarfsgemeinschaft
und Monat in `sessionStorage`, bis eine erfolgreiche oder definitive fachliche
Antwort vorliegt. Ein erneuter bewusster Klick verwendet dieselbe Nummer, auch nach
einem Masken-/Browser-Reload. Technische Fehler und unklare Ausgänge löschen sie nicht.
Es werden dafür keine Bankdaten gespeichert. Ein unbekannter Saga-Ausgang behauptet
keine bereits abgeschlossene Kompensation mehr.

Das ist ausdrücklich kein Beleg dafür, dass ein JDBC-Interrupt immer zurückrollt.
Eine lesende Momentaufnahme kann einen noch laufenden Worker überholen. Die
unklare Antwort bleibt deshalb unklar; insbesondere entsteht daraus keine Freigabe
für eine neue Vorgangsnummer.

FB-20261015-07: `GET /api/bedarfsgemeinschaften/{id}?monat=JJJJ-MM` übergibt den
gewählten Bezugsmonat und die Sitzungskennung an die Fachschicht. Ungültige oder
doppelte Monatsparameter werden vor dem Fachaufruf abgelehnt. Die Oberfläche lädt
beim Monatswechsel die Vorschau neu und zeigt den zurückgelieferten Berechnungsmonat.

FB-20261015-08: Die Oberfläche bietet eine eigene Änderungsbescheid-Aktion an.
`PUT /api/bedarfsgemeinschaften/{id}/bescheid` übergibt Wirkungsbeginn, bisheriges
Bewilligungsende und die authentifizierte Kennung. Die atomare Ablösung des alten
Bescheids, der Erhalt vorheriger Monatswerte und die Behandlung bereits angeordneter
Zahlungen liegen in der getrennt geprüften Fachimplementierung.

## Regressionen und ausgeführte Prüfungen

- `GrokWebRegressionTest`: echter HTTP-Server für die Abbildung eines unklaren
  Task-Ausgangs, die Monatsweitergabe, ungültige/mehrdeutige Monatsparameter,
  Änderungsbescheid per PUT und Frame-Schutz. Fachbuchung und JDBC-Abbruch werden
  hier nicht simuliert oder als geprüft behauptet.
- `BenutzerverwaltungTest`: synthetische defekte Definitionen mit Passwort im
  falschen Feld prüfen, dass weder Kontensegment noch Geheimnis im Exceptiontext stehen.
- testsweet `ProduktHttpSicherheitTest`: zwei zusätzliche Produktprüfungen für
  Frame-Schutz und Geheimnisfreiheit von Konfigurationsfehlern. Der vorhandene
  Storno-Admin-Test läuft gegen den echten Produkt-HTTP-Router.
- testsweet `produkt-ui-review.test.js`: führt die vollständige Produkt-Browserdatei
  mit kontrolliertem DOM/HTTP aus. Sechs Fälle prüfen lesendes Nachladen ohne
  automatische Schreibwiederholung, Nummernerhalt einschließlich Browser-Reload,
  getrennte Nummern je BG/Monat, Monatsvorschau, Änderungsbescheid-PUT und Saga-Fehlertext.

Frisch ausgeführt: `node --test testsweet/src/test/js/produkt-ui-review.test.js`,
**6 Tests, 6 bestanden, 0 Fehler**. Java-/Oracle-Gesamtverifikation wird zentral
ausgeführt und im gemeinsamen Nachbesserungsbericht dokumentiert; sie wird durch
diesen Teilbericht nicht vorweggenommen. Keine Simulator-Vollläufe oder externen
LLM-Aufrufe in dieser Web-Teilprüfung.
