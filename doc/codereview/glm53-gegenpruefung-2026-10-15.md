# GLM-5.3-Review: kritische Gegenprüfung und Korrekturen

Prüfdatum: 2026-10-15. Grundlage ist das übergebene Review
`grundsicherung-gesamt-review-2026-10-15-zc1.md`. Geprüft und korrigiert wurde der
Arbeitsbaum auf `37abfb6`, einschließlich der bereits vorhandenen Grok- und
Antigravity-Nachbesserungen. Historische Testergebnisse des Fremdberichts sind
keine Nachweise für diese neuen Änderungen. Es wurde nichts committet oder
gepusht.

## Ergebnis und Einordnung

Die konkreten kleinen Befunde sind berechtigt und behoben. Beim Betriebsbefund
ZC-1 ist die Ursache genauer zu unterscheiden: Der Startschutz ist richtig;
fehlende Schrittnamen allein erklärten die damalige Blockade noch nicht. Der
gesicherte Altfall enthielt nur eine PENDING-Zeile, die aktuelle Demo-Definition
dagegen drei Schritte. Ein sicherer Auflösungsweg fehlte tatsächlich.

Die Gegenprüfung hat zusätzlich einen Integritätsfehler bei Altjournalen
bestätigt: Der historische Engine-Stand `41f23eba31cac2daf808923c527ff8290914e580`
committete die Fachaktion vor dem getrennten `stepDone`. Ein namenloses PENDING
beweist daher keine Wirkungslosigkeit. Vor der Korrektur konnte der Wiederanlauf
eine vollständige solche Liste als COMPENSATED schließen. Bei einem gemischten
Journal konnte er bekannte DONE-Schritte kompensieren, die hinter PENDING
verborgene Wirkung überspringen und anschließend trotzdem den Payload löschen.
Diese Fälle bleiben jetzt FAILED mit erhaltenem Payload; es erfolgt keine
automatische Teilkompensation oder Erfolgsmeldung.

Die im Fremdreview dokumentierte Löschung des ursprünglichen Altfalls wurde hier
weder wiederholt noch rückgängig gemacht. Tests verwenden ausschließlich eigene
UUIDs und ein eigenes Oracle-Schema.

## Befundentscheidungen

| Befund | Entscheidung und Umsetzung |
|---|---|
| ZC-1: Alt-Saga blockiert Start ohne Auflösungsweg | Betriebsbefund bestätigt. Offline-Werkzeug `SagaJournalRepairMain` und Wartungsanleitung ergänzt; Startschutz bleibt wirksam. Historische namenlose PENDINGs werden konservativ geschützt. |
| ZC-2: Listen-Vorschau ohne Monatsbezug | Bestätigt. Listen-API akzeptiert `?monat=YYYY-MM`, liefert `berechnungsmonat` je Zeile und rechnet alle Zeilen mit demselben gewählten Monat. Monatsauswahl in der Übersicht und Detailmaske verbunden; verspätete Antworten des alten Monats werden verworfen. |
| ZC-3: Fehlende Mehrbedarfsfälle | Testlücke bestätigt; kein neuer Berechnungsfehler. Je sechs feste, unabhängig vorgegebene Sollfälle in Hauptprojekt und testsweet ergänzt. |
| ZC-4: Null-Zweig in `Map.of` | Latenter Fehler bestätigt. Auszahlungsmapping verträgt ausdrücklich eine fehlende BG. Die produktiven Servicepfade liefern derzeit eine BG; ein erreichbarer Buchungsfehler war damit nicht nachgewiesen. |
| ZC-5: Druck-URL mit abschließendem Schrägstrich | Bestätigt. Normalisierung erfolgt vor Druckrouting und Methoden-/Rollenprüfung. Einmaligkeit, Sitzungsbindung, Logout-Widerruf und Ablehnung innerer Doppelschrägstriche bleiben getestet. |
| KA-1: Pool-Regel widersprüchlich | Veraltete Stellen in AGENTS.md und Architektur auf Default 24 sowie mindestens drei Verbindungen je gleichzeitigem Saga-Task korrigiert. |
| KA-2: Toter Vorscan-Zweig | Entfernt. Der Vorscan liest bereits genau eine Kopfzeile je Primärschlüssel; die alte Schrittdedup-Verzweigung hatte keine Funktion mehr. |
| KA-3: Definitionswechsel bei PENDING | Konservative Grenze bleibt dokumentiert. Fehlende oder abweichende Schrittanzahl wird nicht automatisch als wirkungslos ausgegeben. Für nachweislich wirkungslose all-PENDING-Altfälle existiert jetzt der kontrollierte Offline-Weg. |
| KA-4: Unbekannter Ausgang auch bei Lese-Tasks | Kein bestätigter Fehler. Eine konservative 409-Antwort ist keine falsche Zusage über den Verarbeitungsstatus; unverändert. |
| KA-5: Missbrauch manueller TxManager-API | Kein neuer konkreter Produktionspfad belegt; die manuelle API hat dort keine Aufrufer. Kein behaupteter Fix eines hypothetischen Zugriffsfehlers. |
| WC-3: Sidebar-Version | Auf Produktversion 0.5.8 korrigiert. |
| WC-4: „identische“ GLM-Wiederholungen | Bereits vor dieser Gegenprüfung in den vorhandenen Änderungen berichtigt: höchstens drei Versuche, mit gezieltem Korrekturhinweis bei formal ungültiger Antwort. |
| WC-5: Doppelte Header-Anweisungen | Redundante Setzungen bereinigt. |
| WC-6: Fach-/Architekturdokumente nicht quellengebunden | Bestätigt und erweitert: README sowie Projektbeschreibung, Architektur, Leistungsmanagement, Rechtsgrundlagen und Szenario-Regression gehören jetzt zum SHA-256-Quellenbestand. Java- und Node-Regressionen verwenden die echten Regeln und erkennen auch Inhaltsänderungen bei gleicher Dateigröße und unverändertem Zeitstempel. |

Die pauschale ZC-3-Formulierung `{0,0,≥2} → 270,24 EUR` im Fremdbericht ist
falsch. Beim vorhandenen Günstigervergleich auf Grundlage von 563 EUR ergeben
sich für zwei, drei, vier, fünf und sechs minderjährige Kinder ohne
36-Prozent-Konstellation **135,12 / 202,68 / 270,24 / 337,80 / 337,80 EUR**.
Der sechste Test betrifft zwei minderjährige Kinder, davon eines unter 16 und
keines unter sieben Jahren: ebenfalls 135,12 EUR. Die Rechenformel wurde in dieser
Gegenprüfung nicht verändert. Die bereits dokumentierte engere Gesetzeslesart
bleibt eine rechtliche Auslegungsfrage; diese Tests entscheiden sie nicht.

## Kontrollierte Klärung von Alt-Sagas

Das neue Werkzeug ist ein Offline-Betriebsverfahren, keine automatische
Reparatur beliebiger Altdaten:

1. Alle Schreiber müssen gestoppt sein. `inspect` sperrt genau die angegebene Saga
   und ihre Schritte mit NOWAIT, sichert Kopf, Payload, Namen, Zustände und
   Zeitstempel und rollt ohne Änderung zurück.
2. Die tatsächliche Wirkung muss anhand historischer Definition, Fachzeilen und
   Betriebsbelege unabhängig geprüft werden. Fehlende historische
   SAGA_ID-Korrelationen oder ein PENDING-Marker reichen nicht als Nachweis.
3. `resolve-pending` verlangt den unveränderten Snapshot samt Hash, einen
   getrennten gehashten Wirkungsnachweis, Betreiber, Begründung sowie ausdrückliche
   Wartungs- und Nullwirkungsbestätigung. Nur ein FAILED-Kopf mit nichtleerer,
   zusammenhängender all-PENDING-Liste ist technisch zulässig.
4. Snapshot, Wirkungsbeleg und Auflösungsabsicht werden vor dem DB-Commit neu
   geschrieben, auf Datenträger geflusht und nachgehasht. Vorhandene Dateien werden
   nicht überschrieben. Erst danach wird genau dieser Kopf als COMPENSATED
   abgeschlossen und sein Payload entfernt; sämtliche Schrittzeilen bleiben
   erhalten. Das ist der dokumentierte Abschluss eines unabhängig als wirkungslos
   geprüften Vorgangs.
5. Ein verlorenes Commit-Ergebnis bleibt ausdrücklich COMMIT_OUTCOME_UNKNOWN.
   Es erzeugt keinen Erfolgsnachweis und darf nicht blind wiederholt werden.

Der Betreiberbeleg wird technisch gebunden, sein fachlicher Inhalt wird vom
Werkzeug nicht automatisch verifiziert. Bei vorhandener oder ungeklärter Wirkung,
gemischten Zuständen oder fehlender Korrelation ist eine gesonderte fachliche
Klärung erforderlich. Sicherungen können Sozialdaten enthalten; sie gehören in
geschützte lokale Ablage, nicht in Git. Die vollständigen Befehle und Grenzen
stehen in [der Architektur](../architektur.md#alt-saga-vor-dem-serverstart-kontrolliert-klären-zc-1--ka-3).

## Neue Regressionen und aktuelle Verifikation

Die Alt-Saga-Regressionen decken unvollständige und vollständige namenlose
PENDING-Listen, echte vorcommittete Oracle-Fachwirkungen, gemischte Zustände,
Startguard, kontrollierten wirkungslosen Abschluss, unzulässige Zustände,
veraltete Hashes, lebende Claims, Überschreibschutz und den Verlust einer echten
Commit-Quittung ab. Kritische Pfade werden in testsweet nochmals gegen das
tatsächlich installierte Produkt-JAR geprüft.

Die fachlichen Oracle-Regressionen stellen die Uhr auf Juli und den
Entbindungsmonat auf Juni: Liste und Detail stimmen für den jeweils ausdrücklich
gewählten Monat überein; die Vorschau schreibt keine Daten, eine ausdrücklich für
Juni angeordnete Zahlung bleibt eine Juni-Zahlung. Ergänzt sind die sechs
Mehrbedarfs-Sollfälle, Null-Mapping, Druck-URL, Monatsauswahl und verspätete
Browserantworten sowie die Dokumentbindung des Zertifikats.

Zentral frisch ausgeführt, 18:09–18:13 Uhr MESZ:

| Prüfung | Ergebnis |
|---|---|
| Hauptprojekt `clean install` auf eigener Oracle-DB | 375 Tests, 0 Fehler, 1 beabsichtigter Skip |
| Konfigurationstest ohne Datenbankvariable | 2/2, kein Skip |
| testsweet `clean test` gegen frisch installiertes Produkt-JAR | 313/313, kein Skip |
| Node-Werkzeug und UI-Skripte | 142/142 |
| Unabhängige Python-Referenztests | 11/11 |
| Frische JVM aus vollständig kopiertem Runtime-Classpath | PASS; 75 Einträge nachgehasht, beide echten CodeSources in der Kopie, ursprüngliche JUnit-Guards gültig |
| Fachsimulator mit Standard-Positivtopf | 100/100 verifiziert |
| Fachsimulator mit Negativtopf | 100/100 verifiziert, erwartete Fachfehler und unveränderter DB-Endzustand |
| Lokales GLM-Prüfpaket | 100 Positiv + 100 Negativ, 200 unterschiedliche Fall-IDs; kein API-Aufruf |
| Quellen-/Belegbindung | 274 Dateien, 0 Hash-Abweichungen; beide unabhängigen JUnit-Leser PASS |

Das sind gegenüber der vorherigen Antigravity-Gegenprüfung (353/301) **22 zusätzliche
JUnit-Ausführungen im Hauptprojekt und zwölf in testsweet**. Alle zehn Build- und
Nachprüfungsschritte endeten mit Exit 0. Gemeinsamer Quellenfingerprint:
`076255b73566972fac137423b53d5ead8386f6cb70b2c700f4c23cfe49828831`.

Laufzeit: Temurin 25.0.4.1, Compilerziel Java 21; Oracle AI Database 26ai Free
23.26.3.0.0, PDB FREEPDB1. Verwendet wurden ausschließlich das eigene Schema
`GLM_FIX_20261015` und ein eigenes Maven-Repository. Nach den Prüfungen wurde das
Schema bei nachweislich null aktiven Sitzungen entfernt; die temporäre
Zugangsdaten-Datei wurde gelöscht. Das Entwickler-Schema wurde nicht zurückgesetzt.
Keine neue Java-21-Laufzeitprüfung und kein Oracle-19c-EE-Lauf.

Nachweise: [Verifikationsübersicht](glm53-gegenpruefung-2026-10-15-belege/verification-summary.json),
JUnit-Marker und ausgewählte XML-Regressionen im selben Belegverzeichnis.

## Bereits erledigte Punkte und Grenzen

Die GLM-Prüfpaketlücken TF29 und TF68 sowie die Isolation der Prüf-JVM wurden
bereits in der [Antigravity-Gegenprüfung](antigravity-gegenpruefung-2026-10-15.md)
korrigiert. Der frühere fehlgeschlagene Zertifikatslauf wird dadurch nicht
nachträglich grün. Die erweiterten Standardtöpfe bleiben bei 100 Positiv- und
100 Negativfällen; das lokale GLM-Prüfpaket enthält entsprechend 200 Fälle.

Generierte Review-Berichte und Laufprotokolle sind absichtlich nicht Teil des
normativen Quelleninventars, damit das Erzeugen eines Nachweises ihn nicht selbst
entwertet. Änderungen an den jetzt aufgenommenen sechs Grundlagen-Dokumenten
entwerten hingegen bestehende Nachweise und erfordern neue Tests.

Kein neuer externer GLM-Aufruf und kein vollständiger Zertifikatslauf mit sieben
Simulator-Kombinationen wurden in dieser Gegenprüfung ausgeführt. Vorhandene
Modellgrenzen bei Objektzuständigkeiten, Vermögensverbrauch und verfahrensrechtlich
vollständigen Änderungsentscheidungen bleiben ausdrücklich bestehen. Die
Korrekturen stellen keine Produktionsfreigabe und keine neue rechtliche
Zertifizierung dar. Der laufende testsweet-Controller muss nach Übernahme der
Java-Änderungen neu gestartet werden; ein danach gewünschtes grünes Zertifikat
benötigt einen neuen Gesamtlauf.
