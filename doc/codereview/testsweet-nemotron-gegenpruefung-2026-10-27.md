# testsweet: kritische Gegenprüfung des Nemotron-Reviews

Datum: 27.10.2026. Gegenstand ist der vom Nutzer übergebene Bericht von
`nvidia/nemotron-3.5-super-vl:free`, bezogen auf Commit `1555854`. Die Gegenprüfung
beginnt auf `fb1f70c`, berücksichtigt die dazwischenliegenden Korrekturen und prüft
die maßgeblichen Aussagen zusätzlich am ursprünglichen Reviewstand. Historische
Testzahlen sind kein Nachweis für diese Änderungen.

## Bewertung der acht Befunde

| Befund | Unabhängiges Ergebnis | Umsetzung / Begründung |
|---|---|---|
| CR-V01: gemeinsamer Produktrechner lässt Berechnungsfehler grün werden | In dieser Form widerlegt | Bereits `1555854` ruft im echten Verifikationspfad `LlmCalculationReference.verifyBlocks` auf und verknüpft das Ergebnis mit dem Auftragsstatus. Die Nachrechnung ist damit verpflichtend für alle vorhandenen Rechenblöcke. Neue Regressionen sichern diesen Zusammenhang gegen eine vollständig konsistente falsche Rechnung ab. Keine redundante dritte Rechenimplementierung ergänzt. |
| CR-V02: fiktiver zweiter Sachbearbeiter | Teilweise bestätigt und korrigiert | Der harte Ersatz `sb-sim-ersatz` ist entfernt. Auch bei einem Worker werden mindestens zwei vorbereitete simulierte Fachkennungen verwendet. Unbekannte oder fehlende Freigabeidentitäten scheitern; auch WEB erfindet kein Ersatzkonto mehr. Fachidentitätstrennung ist kein Anmeldenachweis und beweist keine zwei natürlichen Personen. Diese Grenze steht nun im Laufstatus und in Berichten. |
| CR-V03: lokale API ohne Anmeldung | Sachverhalt bestätigt, dokumentierte Betriebsgrenze | Der Server bleibt ausdrücklich ein Entwicklungswerkzeug auf `127.0.0.1`. Origin-lose lokale API-Clients sind zulässig. Eine Origin-Pflicht würde lokale Benutzer nicht authentifizieren. Die Vertrauensgrenze wird präzisiert; ein HTTP-Test bestätigt lokale Nutzung, blockierte fremde Host-/Origin-Werte und den Unterschied zwischen fehlendem Header und dem Textwert `null`. Keine vermeintliche Authentifizierung durch ein wirkungsloses Zusatz-Token eingeführt. |
| CR-V04: ECHT/RECHNER außerhalb des Zertifikats | Bestätigte Umfangsgrenze, Kennzeichnung verbessert | GUI, Zertifikatsdeckblatt und erfolgreicher GUI-Status nennen den begrenzten Umfang ausdrücklich. `scope` beschreibt sieben Simulator-Kombinationen, beide JUnit-Suiten, POSITIV-/NEGATIV-GLM und die ausgeschlossenen Kategorien; es gehört zum Fingerprint und zu neuen Exportmarkern. Die Matrix wird nicht still erweitert. Der Fremdbericht bezeichnet die sieben Kombinationen ungenau als Fachschichtkombinationen: tatsächlich sind es vier Fach- und drei Webkombinationen. |
| CR-V05: zu allgemeine Fehlerfragmente | Kein aktueller falsch positiver Ablauf belegt | Aktuelle Aufrufer prüfen spezifische Gründe zusätzlich zum exakten Fachfehlercode. Vorhandene Tests verwerfen den richtigen Sammelcode mit falschem Grund. Fragmentvergleich erlaubt variable IDs/Beträge. Javadoc präzisiert den Vertrag; eine beliebige Mindestlänge oder Stopwortliste wäre kein belastbarer Ersatz für semantisch passende Gründe. |
| CR-V06: freie Einsicht-Präfixe | Bestätigt und gehärtet | Nur vier bekannte vollständige Einsichttexte mit passender Bearbeiterkennung, vorhandener BG, vollständigem bekannten Spaltensatz, positiver numerischer ID und vorhandener Zeitangabe bleiben ausgenommen. Präfixfälschungen, unbekannte Typen, falsche Zuordnungen und abweichende Spalten schlagen fehl und verschwinden nicht aus der Prüfsumme. Bestehende Journalzeilen dürfen weiterhin überhaupt nicht verändert werden. |
| CR-V07: CLI-Tippfehler öffnet GUI | Bestätigt und behoben | Unbekannte Kommandos, überzählige Argumente und unvollständige Worker-Aufrufe enden vor Buildinitialisierung, Shutdown-Hook und Serverstart mit Exitcode 2. Ohne Argumente bleibt die GUI der dokumentierte Standard. |
| CR-V08: freie PDF-Bildpfade | Vorsorglich gehärtet, keine belegte externe Lücke | Alle bisherigen Aufrufer verwenden feste Reportgrafiken. Der Loader akzeptiert jetzt nur Bildnamen und bekannte Assetpräfixe; der Dateifallback bleibt innerhalb freigegebener Verzeichnisse und prüft reale Datei-/Verzeichnispfade. Die vorhandenen Grafiken bleiben tatsächlich ladbar. |

## Warum CR-V01 keine weitere Sollwertkopie rechtfertigt

Die Ausgangsbeobachtung stimmt: Viele SQL-Sollbeträge entstehen mit der
Produktberechnung. Die Schlussfolgerung übersieht jedoch die zweite verpflichtende
Prüfung in `Verifikator.verifiziereAlle`. `alleOk &= rechnung.ok()` verhindert den
Status `VERIFIZIERT`, sobald die unabhängige Referenz widerspricht. Diese Kette
existierte schon am angegebenen Reviewstand; sie ist keine erst jetzt eingebaute
Entkräftung.

`CalculationMutationRegressionTest` wählt einen tatsächlich generierten Erwerbsfall
und prüft zunächst eine erfolgreiche unveränderte Kontrolle. Danach verändert der
Test Freibetrag, anrechenbares Einkommen, Personenbetrag und Haushaltssummen
konsistent um einen Euro. Die kontrollierte SQL-Gegenstelle liefert gerade denselben
falschen Auszahlungsbetrag. Die SQL-Prüfung besteht, aber der echte Verifikationslauf
setzt den Auftrag wegen der unabhängigen Nachrechnung auf
`VERIFIKATION_FEHLGESCHLAGEN`. Damit wird genau der behauptete gemeinsame
Soll-/Ist-Fehler untersucht, statt nur eine offensichtlich falsche Summe zu testen.

Eine weitere Gegenprobe über Fach-/Web-Positiv-, Negativ-, Last- und Stressgeneratoren
verlangt nachrechenbare Rechenblöcke für Aufträge mit Auszahlungsbetragsprüfung.
Die Referenz prüft das dokumentierte Rechenmodell und seine Zwischenwerte. Sie
ersetzt keine unabhängige Ermittlung sämtlicher rechtlicher Eingaben oder eine
vollständige heutige Einzelfallentscheidung.

## Einordnung der vorgeschlagenen Testlücken

- **L-01:** Neue `ApprovalIdentityTest` prüft explizite Identitäten, Trennung der
  Workerzahl und Ablehnung unbekannter Kennungen. Der Oracle-Negativtest verwendet
  dieselben vorbereiteten Identitäten. Ein automatischer Test mit zwei Kennungen
  ist weiterhin kein Nachweis zweier menschlicher Prüfer.
- **L-02:** Die neue konsistente Mutation durchläuft den echten Verifikator mit
  kontrolliertem SQL. Eine unnötige Produktrechner-Umschaltung wird nicht eingeführt.
- **L-03:** `SzenarioAktionTest` existiert bereits; insbesondere wird derselbe
  Fachcode mit anderem Fehlergrund verworfen. Kein konkreter zusätzlicher
  Fehlfreigabepfad aus einem bestehenden Aufrufer nachgewiesen.
- **L-04:** `SweetServerLlmHttpTest` prüft ausdrücklich lokale Clients ohne Origin,
  fremde Origin-Werte einschließlich `null`, gültige eigene Herkunft und falschen
  Host. Das ist ein Herkunftstest, kein lokaler Benutzer-Login.
- **L-05:** `NegativZustandsTest` erweitert die Journal-Gegenproben; bestehende
  Oracle-Szenarien prüfen die strengere Ausnahme gegen reale Produkttexte.
- **L-06:** `SweetMainCommandTest` startet echte Kindprozesse mit fehlerhaften
  Argumenten und verlangt Exitcode 2 ohne Laufverzeichnis oder GUI-Start.
- **L-07:** Der pauschale Eindruck fehlender neuer HTTP-Tests trifft nicht zu:
  `CertificateSuiteHttpTest`, `SweetServerLlmHttpTest`,
  `SweetServerReportRegressionTest` und `TestreportHttpTest` prüfen bereits
  Methoden, Eingaben, Laufsperren, alte Belege und Exportfehler. Nicht jede mögliche
  Endpunkt-/Statuscode-Kombination ist ein eigener Test; daraus allein folgt kein
  reproduzierbarer Produktfehler.
- **L-08:** Die angeblich nicht auffindbare Datei
  `doc/belege/echt-und-rechner-faelle-2026-10-27.md` ist nicht nur heute vorhanden,
  sondern auch bereits in `1555854` enthalten (`git cat-file -e` erfolgreich).
  Sie wurde vorher in `64ad36e` angelegt und später präzisiert. Die Behauptung einer
  fehlenden Repository-Verankerung ist damit widerlegt. Die dort ausdrücklich
  dokumentierten Grenzen der Quellen bleiben bestehen.

## Verifikation und Grenzen

Die unabhängige Gegenprüfung des integrierten Diffs hat zusätzlich einen
Exportmarker-Randfall aufgedeckt: Ein nachträglich isoliert geändertes `scope`
durfte nicht mit unverändertem Fingerprint als gültiger Umfang weitergegeben
werden. Veröffentlichung und Abruf vergleichen deshalb den Umfang mit der frischen
Erhebung. Auch das Markerurteil muss dem frisch berechneten Urteil entsprechen.
Manipulierte oder fehlende Umfangsangaben und ein nachträglich auf BESTANDEN
gesetztes Urteil werden durch erweiterte Tamper-Regressionen abgewiesen.

Der integrierte Prüflauf hat außerdem einen Report-Zählfehler sichtbar gemacht:
Ein gesondert nachgeholter Konfigurationstest war bereits Teil des vollständig
bestätigten JUnit-Umfangs, während die Reportanzeige nur den letzten Teillauf
betrachtete. Aktuelle PASS-Zähler verwenden deshalb dieselbe vollständig geprüfte,
von Doppelzählungen bereinigte JUnit-Auswertung wie das Zertifikat. Der historische
Rückfall bleibt strikt an vollständige archivierte XML-Belege gebunden. Texte
unterscheiden den zusammengesetzten Prüfstand von einem einzelnen Prüflauf.

Die Änderungen wurden auf Temurin Java 25 mit Zielversion 21 und einem eigens
angelegten Schema in Docker-Oracle Free geprüft. Keine Daten einer laufenden
Fachanwendung oder fremder Simulatorläufe wurden bereinigt. Das temporäre Schema
und seine lokale Anmeldedatei wurden nach den Prüfungen entfernt.

| Prüfung auf dem eingefrorenen Endstand | Tatsächliches Ergebnis |
|---|---|
| Hauptprojekt `mvn install` | 395 Prüfungen, 0 Fehler; ein umgebungsbedingt ausgelassener Konfigurationstest wird anschließend im passenden Umfeld nachgeholt |
| Separat `mvn -Dtest=LmgConfigTest test` ohne TX-Umgebungsüberschreibungen | 2/2 erfolgreich; kanonischer aktueller Hauptprojekt-Nachweis danach 395/395, keine offenen Skips |
| `mvn -f testsweet/pom.xml test` mit eigener Oracle-DB | 361/361 erfolgreich, keine Fehler oder Skips; darunter die zwei Oracle-Negativtests mit 560 Szenarien und die neuen Regressionen |
| Sechs Node-Prüfdateien einschließlich Zertifikat, Referenz, Antwortparität, Report und beider Browserdateien | 178/178 erfolgreich, keine Skips |
| Tatsächlicher CLI-Fachlauf POSITIV mit einem Worker, 20 Aufträgen, Seed 42 und 0 ms Denkzeit | 20/20 SQL-verifiziert, PASS; explizite Kennungen `sb-sim-01` und `sb-sim-02`, keine zusätzlichen Worker |
| Unabhängige SQL-Nachkontrolle dieses Fachlaufs | 2 Zahlungen mit Erfasser `sb-sim-01` und Freigeber `sb-sim-02`; 20 weitere Zahlungszeilen noch ohne Freigabe; 0 Selbstfreigaben |
| Reportzähler nach dem gesonderten Konfigurationslauf | Vollständige gemeinsame Zählung: 756 erfasst, ausgeführt und erfolgreich; 0 offene Fehler/Skips |

Haupt- und testsweet-JUnit-Nachweise sowie der abschließende Simulatorlauf binden
an denselben Quellenprüfwert
`30088cce2307b77ca14c79360e075e45734c5c387509f4b111e5e220cd196df4`.
Die beiden Hauptprojekt-Aufrufe werden nicht als zusätzliche eindeutige Tests
addiert. Der letzte Simulatorbeleg liegt unter
`testsweet/logs/run-20261027-113221-5699857358672185905/`; Maven-, Node- und
SQL-Nachkontrollprotokolle unter `target/nemotron-review-20261027/`.

Kein externer GLM-Aufruf, kein neuer Sieben-Modi-Zertifikatsgesamtlauf und kein
neues grünes Gesamtzertifikat werden aus isolierten Regressionen abgeleitet.
Quellenänderungen entwerten ältere Report-/Zertifikatsnachweise regulär; ein
alter PDF-Erfolg wird nicht nachträglich auf diese Fassung übertragen.

Der Datei-Containment-Schutz prüft reale Pfade. Ein zusätzlicher Test mit neu
erzeugtem Windows-Symlink war hier mangels entsprechender Rechte nicht möglich;
die Standardprüfung erhält deshalb weder eine neue Administratorvoraussetzung
noch einen ausgeblendeten Erfolg durch einen Skip. Classpath-Laden, erlaubte
Dateiverzeichnisse sowie absolute und ausbrechende Pfade werden tatsächlich geprüft.
