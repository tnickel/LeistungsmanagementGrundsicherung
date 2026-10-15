# Gesamt-Review SDN.Leistung – 15.10.2026

## Ergebnis

Der Review bestätigt **zehn Befunde: zwei KRITISCH und acht MAJOR**, ohne behaupteten BLOCKER oder Produktionsfreigabe.
Ein angemeldeter Sachbearbeiter kann die Adminpflicht beim Zahlungsstorno umgehen; dies wurde über HTTP, den echten Fachservice und Oracle bis zum persistierten Storno reproduziert.
Der Alleinerziehendenmehrbedarf wird bei bestimmten Kombinationen mit vier oder mehr Kindern zu niedrig berechnet.
Ein optionaler zentraler Warmwasserabzug kürzt ebenfalls anzuerkennenden Bedarf; alle drei arithmetischen Referenzen übernehmen beide falschen Fachannahmen.
Eine ausschließlich PENDING gebliebene Saga kann den Serverwiederanlauf dauerhaft verhindern; weitere Befunde betreffen Bescheidnachdruck, Rechtsbehelf, Authentifizierung, Retry und Validatorparität.
Die vorhandenen automatischen Suiten, sämtliche sieben Simulator-Kombinationen mit 1.312 Aufträgen und eine echte GLM-Stichprobe mit 18 Webfällen bestanden trotzdem.
Das belegt die ausgeführten Prüfungen, widerlegt aber keine außerhalb ihrer Assertions bzw. Fallauswahl liegenden Fehler.
Die Quellen und erfassten Bestandsdokumente blieben unverändert; Java 21 als tatsächliche Laufzeit, Oracle 19c und ein neuer vollständiger Publisher-/LibreOffice-Gesamtlauf wurden nicht verifiziert.
Ein bereits vor dem Review gestarteter anderer Zertifikatsgesamtlauf wurde erst spät erkannt; eigene Laufordner wurden deshalb nach Abschluss unverändert aus dem aktiven Laufverzeichnis archiviert, die Überschneidung und deren Grenzen sind unten offengelegt.

| Bereich | Urteil im tatsächlich geprüften Umfang |
|---|---|
| Fachlichkeit | Reproduzierte Rechenfehler; keine allgemeine Rechtskonformität. |
| Geldfluss | Bestehende DB-/Szenariotests bestanden; erreichbarer Autorisierungsfehler bei echtem Storno. |
| Transaktionskern | Breite bestehende Regression grün; persistierter PENDING-Wiederanlauf fehlerhaft. |
| Sicherheit | Admin-Storno umgehbar; zwei weitere bestätigte begrenzte Authentifizierungsprobleme. |
| Tests/Zertifikate | Nachweise im geprüften Umfang belastbar, fachliche Sollannahmen jedoch teilweise gemeinsam falsch. Kein neuer vollständiger Gesamtzertifikats-PASS behauptet. |

## Prüfstand, Methode und Belege

- Repository: `D:\AntiGravitySoftware\GitWorkspace\LeistungsmanagementGrundsicherung`.
- Commit: `3c909c29e22f90a7208f74e835614e1878056eff`, Branch `main`, Projektversion laut POM `0.5.8`.
- Zu Beginn keine Änderungen versionierter Dateien. Bereits vorhandene unversionierte Ordner/Dateien sind in `state-before.json` erfasst und wurden nicht als neue Reviewarbeit ausgegeben.
- Tatsächliche Laufzeit: Temurin **25.0.4.1**, Maven **3.9.6**, Windows 11; Compilerziel `--release 21`.
- Datenbank: Docker `lmg-oracle`, **Oracle AI Database 26ai Free**, Release **23.26.3.0.0**, PDB `FREEPDB1`.
- Basissuiten gegen die konfigurierte Entwicklerdatenbank; Simulator und neue schreibende Gegenproben ausschließlich im eigens angelegten Schema `REVIEW_20261015`. Dort war das breite SWEET-Cleanup auf eigene Daten beschränkt.
- 250 Quellen, Tests, DDL, Build-/Prüfwerkzeuge, Prüfprompt und relevante Dokumente vor und nach der Prüfung mit SHA-256 erfasst: **keine Abweichung**. Commit allein wird nicht als vollständiger Arbeitsbaumbeleg verwendet.
- Drei parallele Teilprüfungen für Kern/Oracle, Fach/Geldfluss und Web/Sicherheit; zentrale Prüfung von Simulator, LLM/Zertifikaten und Schnittstellen. Kritische Befunde wurden zentral am Code und durch Gegenproben nachgeprüft.
- Zuerst eigene Analyse, danach historischer Fundabgleich. Kein Produktcode, bestehender Test, Konfigurations- oder Rechtsgrundlagentext repariert.

Belegbasis: [Review-Belegordner](./grundsicherung-gesamt-review-2026-10-15-belege/), darin `baseline-results.json`, `simulator-matrix-results.json`, `llm-summary.json`, `inventory-before.json`, `inventory-after.json`, `drift-result.json` und die unten genannten Proben.

Die vollständigen Norm-/Endpunktmatrizen und ausführlichen Gegenprüfungen stehen zusätzlich in:

- [Fachlicher Teilbericht](./grundsicherung-gesamt-review-2026-10-15-belege/fach/teilbericht-fach.md).
- [Kernteilbericht](./grundsicherung-gesamt-review-2026-10-15-belege/kern/teilbericht-kern.md).
- [Webteilbericht einschließlich beider Endpunktmatrizen](./grundsicherung-gesamt-review-2026-10-15-belege/web/teilbericht-web.md).
- [Zusätzliche Gegenprüfung der Zertifikatskonsistenz](./grundsicherung-gesamt-review-2026-10-15-belege/kern/zusatz-zertifikatskonsistenz.md).

Diese Teilberichte enthalten auch Kandidaten/Einordnungen; **maßgeblich für Anzahl und Priorisierung ist die folgende konsolidierte Fundliste**. Insbesondere wird die gemeinsame falsche Referenzannahme nicht als zusätzlicher doppelter Geldbetragsfehler gezählt.

## Bestätigte Befunde

### GR-20261015-01 – KRITISCH: Sachbearbeiter erreicht adminpflichtigen Storno

**Evidenz:** DYNAMISCH BELEGT, vollständige HTTP → Service → Oracle-Kette. **Ort:** `LmgServer.java:209`, `:293`, `:478`; `SozialLeistungsService.java:542`.

Die vorgelagerte Adminprüfung erkennt `/api/zahlungen/\d+/storno`. Der eigentliche Handler akzeptiert dagegen jedes mittels `Long.parseLong` lesbare ID-Segment. Eine alternative positive ID-Schreibweise erreicht daher den Stornoservice mit der Rolle `sachbearbeiter`. Der Service selbst erhält keine Rolle.

**Trigger/Soll/Ist:** Im eigenen Reviewschema wurde eine BEREIT-Zahlung über 10 EUR mit Restbudget 90 EUR angelegt. Derselbe Sachbearbeiter erhielt für die kanonische Route 403, für die positive ID mit führendem Plus 200. Direktes SQL bestätigte anschließend `STORNIERT` und Restbudget 100 EUR. Separate HTTP-Proben bestätigten auch kodiertes Plus und eine Unicode-Ziffernvariante. Zahlungs-IDs sind für Sachbearbeiter verfügbar; weitere Privilegien sind nicht nötig.

**Gegenprüfung/Wirkung:** Sperren und guarded Statuswechsel schützen die Buchungskonsistenz, nicht die Adminpflicht. Bereits UEBERGEBEN bleibt durch den Statusguard geschützt. Bewiesen ist unzulässiger Storno angelegter/bereiter Zahlungen, kein beliebiger Geldtransfer. Bestehende Tests prüfen kanonische IDs und abschließende Slashes, nicht diesen Unterschied zwischen Rechteprüfung und Router.

**Empfehlung/Regression:** Rechte an die einmal aufgelöste Route binden und Admin zusätzlich im Stornohandler erzwingen. Kanonische und alternative ID-Schreibweisen als Sachbearbeiter dürfen den Service nicht erreichen; kanonischer Admin-Storno muss funktionieren.

**Belege:** `RealHttpStornoProbe.java`, `storno-real-http-oracle.json`, `storno-real-http-oracle.log`; ergänzend `web/WebReviewProbe.java` und `web/web-probe-output.txt`. Die zentrale Probe löschte ausschließlich ihre eigenen Fachzeilen wieder.

### GR-20261015-02 – KRITISCH: Mehrbedarf bei vier/fünf Kindern teilweise zu niedrig

**Evidenz:** Berechnung DYNAMISCH BELEGT, erreichbare Service-/Saga-/Bescheidpfade STATISCH BELEGT; Rechtsauslegung amtlich gegengeprüft. **Ort:** `Leistungsberechnung.java:238`, `:239`; Referenzen `LlmCalculationReference.java:129`, `llm-reference.js:56`, `nachrechnen_100.py:36`.

Liegt ein Kind unter sieben oder liegen zwei/drei Kinder unter sechzehn vor, wählt die Funktion stets 36 Prozent. Sie vergleicht nicht mit der höheren Alternative für vier oder mehr minderjährige Kinder. Zulässige Eingabe: RBS 1, ein U7-Kind, zwei U16-Kinder, insgesamt vier Minderjährige, alleinige Pflege/Erziehung und kein abweichender Bedarf.

**Soll/Ist:** Vier Kinder: 563 × 48 % = **270,24 EUR**, tatsächlich **202,68 EUR**; Differenz **67,56 EUR/Monat**. Fünf Kinder mit drei U16: Soll **337,80 EUR**, tatsächlich **202,68 EUR**; Differenz **135,12 EUR/Monat**. Alle Werte passieren die Fach-/DDL-Validierung. Dieselbe Funktion wird bei Serviceberechnung, Bescheiderteilung und Saga verwendet; ein Snapshot friert den Fehler ein.

**Rechtliche Gegenposition:** Der isolierte Wortlaut von [§ 30 Abs. 3 SGB XII](https://www.gesetze-im-internet.de/sgb_12/__30.html) kann die ausschließliche Auswahl nahelegen. Die [Wissenschaftlichen Dienste, WD 6-046/24, S. 11 in Verbindung mit S. 8–10](https://www.bundestag.de/resource/blob/1019234/WD-6-046-24-pdf.pdf), übertragen jedoch ausdrücklich den Vergleich der günstigeren Alternative auf SGB XII. Die archivierte amtliche Hamburger Fachanweisung bestätigt die Vier-Kinder-Konstellation. Diese Auslegungshilfen werden nicht als bindende gerichtliche Einzelfallentscheidung ausgegeben.

**Gegenprüfung/Testwirkung:** Vorhandene Vier-/Fünf-Kinder-Tests erfassen nicht die gleichzeitig erfüllte 36-Prozent-Konstellation. Java-, Node- und Python-Referenz akzeptieren die falschen 202,68 EUR ohne Beanstandung. Das ist unabhängig implementierte Arithmetik mit derselben fehlerhaften Fachannahme; kein Beleg eines fehlerhaften echten GLM-Gesamturteils zu genau diesem Fall.

**Empfehlung/Regression:** Beide gesetzlichen Alternativen fachlich vergleichen und die günstigere zulässige Variante wählen, einschließlich Deckel und Einzelfallöffnung. Goldfälle mit U7/U16 UND vier/fünf Minderjährigen vorab handrechnen; Produktions- und sämtliche Referenzregeln gegen diese Sollwerte prüfen.

**Belege:** `fach/FachProbe.java`, `fach/fachprobe-ergebnis.json`, `fach/synthetisch-ae4.txt`, `fach/reference-{java,node,python}-ergebnis.json`.

### GR-20261015-03 – MAJOR: Zentraler Warmwasserabzug mindert anerkennungsfähigen Bedarf

**Evidenz:** DYNAMISCH BELEGT und gesetzlicher Widerspruch direkt gegengeprüft. **Ort:** `Leistungsberechnung.java:198`, `BescheidGenerator.java:356`, `LmgConfig.java:63`; Java-/Node-/Python-Referenzen übernehmen dieselbe Kürzung.

**Trigger/Soll/Ist:** Konfigurierter Anteil `lmg.kdu.warmwasserabzug=0.20`, angemessene Kaltmiete 400 EUR, Heizkosten einschließlich zentralem Warmwasser 100 EUR, RBS 1 und kein Einkommen/Vermögen. Sollbedarf **1.063 EUR**, Istbedarf **1.043 EUR**. Die zentralen Kosten sind Bestandteil des Heizbedarfs nach [§ 35 Abs. 5 SGB XII](https://www.gesetze-im-internet.de/sgb_12/__35.html); der Code ersetzt den abgezogenen Betrag nicht anderweitig.

**Gegenprüfung/Wirkung:** Default 0 verhindert die Fehlwirkung. Der dezentrale Mehrbedarf ist bei zentraler Erzeugung nicht geschuldet; daraus folgt aber kein Abzug der zentralen Kosten. Eine rechtmäßige örtliche Pauschalierung oder Angemessenheitsprüfung wäre eine andere, hier nicht belegte Fachentscheidung. Fehler ist konfigurationsabhängige Unterbewilligung, nicht jeder Standardfall.

**Empfehlung/Regression:** Den zusätzlichen zentralen Abzug entfernen oder die tatsächlich beabsichtigte andere Fachregel mit ihren Voraussetzungen modellieren. Ein unabhängiger Sollfall muss 1.063 EUR verlangen; vorhandene Referenzen und der derzeit den Abzug erwartende Unit-Test dürfen nicht selbst den Sollwert legitimieren.

**Belege:** `fach/fachprobe-ergebnis.json`, Probe `ZENTRAL_WW_20PROZENT`, `fach/synthetisch-zentral.txt` und drei Referenzergebnisdateien. Keine eigene zusätzliche Fundnummer für das hierdurch versagende Rechenorakel.

### GR-20261015-04 – MAJOR: Wirkungslos gebliebene Saga verhindert dauerhaft Serverstart

**Evidenz:** DYNAMISCH BELEGT mit echtem SagaJournal und Oracle sowie echtem Startup-Guard. **Ort:** `SagaEngine.java:64`, `:293`, `:299`; `SagaJournal.java:203`; `LmgServerMain.java:81`, `:164`.

**Trigger:** Prozessende zwischen dem Commit von `journal.start` und dem ersten IN_FLIGHT-Marker. Es existieren dann nur PENDING-Schritte; nach dem Write-Ahead-Protokoll konnte noch kein Fachschritt wirken.

**Soll/Ist:** Eine wirkungslose abgebrochene Saga benötigt einen terminalen, erfolgreich behandelten Zustand. Recovery setzt stattdessen FAILED. FAILED wird weiterhin als unfertig gefunden. Die zentrale Oracle-Probe erzeugte genau den echten Journal-Commitzustand, führte zweimal die produktive Diagnose-Recovery aus und prüfte zweimal `requireSuccessfulRecovery`: jeweils ein Fehler, persistiert FAILED, Startup verweigert. Kein zusätzlicher Prozesskill wurde behauptet.

**Gegenprüfung/Wirkung:** Der Startup-Guard ist für unvollständig kompensierte Sagas berechtigt. Falsch ist hier die dauerhaft fehlerhafte Einordnung trotz fehlender Fachwirkung. Der Admin-Endpunkt hilft nicht, wenn der Listener deswegen gar nicht startet. Kein Geldverlust bewiesen; betroffen ist der automatische Wiederanlauf der Anwendung.

**Empfehlung/Regression:** Ausschließlich bekannte PENDING-Schritte unter Claim terminal abschließen, z. B. als wirkungslos kompensiert, und beschädigte/unbekannte Zustände separat behandeln. Journal-start-Commit → Abbruch vor IN_FLIGHT → mehrfaches Recovery → erfolgreicher Startup-Guard testen.

**Belege:** `PendingRecoveryOracleProbe.java`, `pending-recovery-oracle.json`, `pending-recovery-oracle.log`; ergänzend `kern/KernProbe.java`.

### GR-20261015-05 – MAJOR: Nachdruck desselben Bescheids übernimmt heutige Bankdaten

**Evidenz:** Generator DYNAMISCH BELEGT, Änderung über API/Service STATISCH BELEGT. **Ort:** `SozialLeistungsService.java:680`, `BescheidGenerator.java:234`, `:279`.

**Trigger/Soll/Ist:** Bescheid mit Snapshot erteilen, Bankverbindung mit korrekter Version ändern, denselben Bescheid nachdrucken. Die Berechnung bleibt eingefroren; der Bankabschnitt wird aber aus dem heutigen BG-Kopf erzeugt. Die Probe hält Bescheidnummer, Datum, Snapshot und Beträge identisch und erhält nach Bankänderung anderes HTML mit neuem Kontoinhaber.

**Gegenprüfung/Wirkung:** Eine Bankänderung für zukünftige Überweisungen ist zulässig. Sie rechtfertigt keinen unmarkiert veränderten historischen Dokumentinhalt. Es wird kein unberechtigter Geldtransfer behauptet. Auch heutige Behörden-/Rechtsbehelfskonfiguration wird neu eingesetzt; der Snapshot schützt nur Berechnungsdaten, nicht das vollständige erteilte Dokument.

**Empfehlung/Regression:** Erteilungsrelevante Dokumentparameter versionieren oder unveränderlichen Dokumentstand speichern. Aktuelle Zahlungsinformationen getrennt und sichtbar kennzeichnen. Nachdruck nach Änderungen von Bank-/Behörden-/Belehrungsparametern mit ursprünglichem Dokumentinhalt vergleichen.

**Belege:** `fach/FachProbe.java`, `fach/fachprobe-ergebnis.json`, Probe `NACHDRUCK_BANKDATEN`.

### GR-20261015-06 – MAJOR: Widerspruchsbelehrung nennt falsche elektronische Formnorm

**Evidenz:** DYNAMISCH/STATISCH BELEGT; amtliche Verweisung online geprüft. **Ort:** `BescheidGenerator.java:410`.

Der Standardmodus WIDERSPRUCH nennt § 65a SGG als elektronische Formnorm. [§ 84 Abs. 1 SGG](https://www.gesetze-im-internet.de/sgg/__84.html) verweist dafür auf [§ 36a Abs. 2/2a SGB I](https://www.gesetze-im-internet.de/sgb_1/__36a.html) und § 9a Abs. 5 OZG. Die Gerichtsformnorm beschreibt nicht die hier zuständige Behördenkommunikation.

**Gegenprüfung/Wirkung:** Im KLAGE-Modus ist § 65a passend. Frist und Vier-Tage-Angabe wurden nicht als Fehler übernommen. Belegt ist der falsche Formhinweis; eine bestimmte Fristverlängerung oder ein tatsächlich gescheiterter Widerspruch wird nicht ohne Einzelfallprüfung behauptet.

**Empfehlung/Regression:** Behörden- und Gerichtsform unterscheiden; tatsächlich eröffneten Behördenzugang korrekt beschreiben. Beide Modi gegen getrennte amtliche Solltexte und Verweisungen prüfen, ohne den generierten Text als eigene Erwartung zu übernehmen.

**Belege:** `fach/fachprobe-ergebnis.json`, Probe `WIDERSPRUCH_FORMNORM`, aktueller Generator und verlinkte amtliche Quellen.

### GR-20261015-07 – MAJOR: Gesättigte Login-Sperrmap verfolgt neue Zielkonten nicht mehr

**Evidenz:** Klassenpfad DYNAMISCH BELEGT mit fester Uhr; HTTP-Erreichbarkeit STATISCH BELEGT. **Ort:** `Benutzerverwaltung.java:122`, insbesondere `:125`.

**Trigger/Soll/Ist:** 1.024 aktive Sperren für bisher unbekannte Kennungen füllen die Map. Ein zuvor nicht erfasstes echtes Konto soll nach fünf Fehlversuchen gesperrt werden. Stattdessen kehrt die Registrierung bei voller Map ohne Zählen zurück; 30 weitere Fehlversuche wurden ohne eine einzige Drosselungsantwort reproduziert.

**Gegenprüfung/Wirkung:** Bereits vorhandene Sperren bleiben erhalten und die Schlüssel sind begrenzt. Die Probe misst nicht, ob die benötigten 5.120 vorbereitenden HTTP-Fehlversuche innerhalb des realen 30-Sekunden-Fensters auf dieser Maschine erreichbar sind. Bewiesen ist ein Schutzverlust unter Sättigung, keine Kontoübernahme. Die bestehende Regression schützt zuerst ein bereits gesperrtes Zielkonto und schneidet diesen Fall nicht.

**Empfehlung/Regression:** Reale konfigurierte Konten unabhängig vom Phantomkennungsbestand verfolgen oder bei Sättigung geschlossen/global begrenzen. Volle aktive Phantommap und anschließend ein neues reales Ziel testen; Speichergrenze und bestehende Sperren gleichzeitig erhalten.

**Belege:** `web/WebReviewProbe.java`, `web/web-probe-output.txt`, Zeile `THROTTLE_SATURATION`.

### GR-20261015-08 – MAJOR: Unbenutztes Drucktoken überlebt Logout

**Evidenz:** DYNAMISCH BELEGT auf Klassen- und HTTP-Ebene. **Ort:** `Benutzerverwaltung.java:50`, `:184`, `:193`, `:204`; `LmgServer.java:266`.

**Trigger/Soll/Ist:** Sitzung erzeugt Drucktoken und meldet sich vor dessen Verbrauch ab. Die Sitzung ist anschließend ungültig; ein Abruf mit dem Drucktoken liefert aber weiterhin 200 und den Bescheid. Erst Replay liefert 401.

**Gegenprüfung/Wirkung:** Einmaliger atomarer Verbrauch und TTL funktionieren. Der verbleibende Zugriff ist auf das höchstens 60 Sekunden lange Tokenfenster begrenzt und setzt Besitz des Links voraus. `no-store` widerruft kein ausgegebenes Token. Der Token speichert nur Benutzer/TTL und keine Herkunftssitzung; Logout beendet daher nicht alle daraus abgeleiteten Zugriffsmöglichkeiten.

**Empfehlung/Regression:** Herkunftssitzung, vorzugsweise zusätzlich Ziel-BG, binden; bei Logout widerrufen und beim Verbrauch Sitzungsablauf prüfen. Unbenutzte Token nach Logout/Idle-Ablauf dürfen die Bescheiddaten nicht mehr laden.

**Belege:** `web/web-probe-output.txt`, `LOGOUT` und `PRINT_AFTER_LOGOUT`.

### GR-20261015-09 – MAJOR: Java und Node akzeptieren unterschiedliche Modellantworten

**Evidenz:** DYNAMISCH BELEGT mit identischen synthetischen Bytes. **Ort:** `LlmAnswerValidator.java:16`, `:17`, `:111`; `llm-evidence.js:29`.

**Trigger/Soll/Ist:** Eine ansonsten gültige Antwort mit Markdown-Codezaun wird von Java entpackt und akzeptiert; Node parst die Rohantwort und lehnt sie ab. Java akzeptiert außerdem `schemaVersion=2.9` durch `intValue()==2`, Node verlangt exakt 2. Die Kontrollantwort ohne Wrapper mit Version 2 akzeptieren beide.

**Gegenprüfung/Wirkung:** Das erzeugt inkonsistente Java-/GUI-Prüfstände und spätere Nichtbestätigung durch den Zertifikatsvalidator. Der vollständige Suitepfad verlangt vor SUCCESS das Node-Zertifikat BESTANDEN; ein fälschlich grüner Gesamtexport wurde deshalb nicht bewiesen. Auch die echte 18-Fälle-Stichprobe erzeugte keinen dieser Formatfälle.

**Empfehlung/Regression:** Gemeinsame Normalisierung und exakt definierte Versionsprüfung. Eine geteilte Korpusregression muss Roh-JSON, Codezaun, Textumhüllung, Bruchversion, doppelte/fehlende Felder und Modellbefunde auf beiden Seiten identisch einstufen.

**Belege:** `ValidatorParityProbe.java`, `validator-parity.js`, `validator-fixture.json`, `validator-java-results.json`, `validator-node-results.json`.

### GR-20261015-10 – MAJOR: SQLState 61000 erlaubt Retry von Ressourcenfehlern

**Evidenz:** DYNAMISCH BELEGT mit tatsächlichem ojdbc11-23.5.0.24.07-JAR und TxManager-/JDBC-Mock. **Ort:** `RetryPolicy.java:19`, `:47`; `TxManager.java:60`.

Projektregel ist Retry nur für ORA-00054/00060/08177. Der pauschale SQLState-Fallback 61000 erfasst beim eingebundenen Treiber auch ORA-00018, ORA-00020 und ORA-04031. Die Probe ließ einen entsprechenden ORA-04031 dreimal durch `TxManager.execute` laufen.

**Gegenprüfung/Wirkung:** maxAttempts und Rollback begrenzen die Wirkung; FachlicheException wird nicht wiederholt. Kein Doppelcommit bewiesen. Konkrete Folge sind zusätzliche Last und verzögerte Antworten bei Ressourcenproblemen, entgegen dem engen Architekturvertrag. Der bestehende SQLState-Test schreibt diesen zu weiten Fallback fest.

**Empfehlung/Regression:** Vendor-Code-Freigabeliste durchsetzen; weitere Retry-Fälle nur nach begründeter Vertragsänderung. Ressourcenfehler negativ testen. Die tatsächliche Treiberzuordnung ist stärker als eine pauschale angenommene SQLState-Bedeutung.

**Belege:** `kern/KernProbe.java`, `kern/probe-output.txt`, `RETRY-EXECUTE: ORA-4031; attempts=3`; [Oracle ORA-04031](https://docs.oracle.com/en/error-help/db/ora-04031/).

## Offene Hypothesen und Modell-/Betriebsgrenzen

Diese Punkte gehören **nicht** zur bestätigten Zehner-Fundstatistik:

- Die manuelle API kann bei verspätetem doppeltem `finish` eine inzwischen erneut ausgeliehene Connection zurücksetzen/freigeben. Das Verhalten ist mit Produktionspool und JDBC-Mock belegt, ein solcher Aufrufer wurde im aktuellen Fach-/Webcode aber nicht gefunden; erlaubte manuelle Lebenszyklusfolge und Fehlbedienung sind getrennt.
- `Future.cancel` garantiert keinen Rollback. Eine Probe mit abgefangenem Interrupt committete vor ihrer Deadline trotz abgebrochenem Future. Javadoc/AGENTS formulieren stärker als technisch abgesichert; die aktuelle Anwendung benutzt keinen gefundenen frühen expliziten Cancelpfad. Kein neuer allgemeiner JDBC-Rollbackfehler daraus abgeleitet. [Java Future](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/Future.html) und [Oracle JDBC Coding Tips](https://docs.oracle.com/en/database/oracle/oracle-database/21/jjdbc/JDBC-coding-tips.html).
- Saga-Kompensation setzt ältere Schritte auch nach einem jüngeren Kompensationsfehler fort. Bei bereits übergebener Zahlung protokolliert die Fachkompensation ALARM; Bescheid-/Vorgangskompensation prüfen keinen Zahlungsstatus. Ein exakt erreichbares zusätzliches Crash-/Kassenlauf-Interleaving wurde nicht neu reproduziert. Kein bestätigter neuer Geldverlust oder doppelter Kern-/Fachfund.
- Tatsächlicher Anspruchsstatus, Beziehungen/Eigentum, alleinige Erziehung und Wohnformen sind nicht vollständig modelliert. Kinder/RBS sind kein ausreichender Ersatz für Rechtsstatus. Insbesondere RBS 3 ist in Teilen der Doku veraltet beschrieben; die [amtliche Anlage](https://www.gesetze-im-internet.de/sgb_12/anlage.html) knüpft sie an § 27b.
- Automatische Vermögensminderung in der Datenbank belegt keinen tatsächlichen privaten Vermögensverbrauch. Der Folgemonatsbetrag ist nur unter dieser Modellannahme begründet. Die pauschale alte Reviewempfehlung wird nicht als gesetzliche Wahrheit übernommen.
- Schwangerschaftsbeginn, fehlender Entbindungsmonat, Karenz-/Absenkungsregeln, Antrag/Rückwirkung, Änderungen/Aufhebung und einkommensrechtliche Sonderregeln bleiben erhebliche Grenzen. README bezeichnet das Projekt als Showcase; kein tatsächlich erteilter realer Fehlbescheid wird konstruiert.
- Personenreihenfolge kann bei einer BG-KdU-Grenze die Verteilung ändern; die unabhängig geprüfte BG-Summe blieb gleich. Kein zusätzlicher Summenfehler bestätigt.
- Audit und lokale Hashes beweisen weder Signatur noch externe unveränderliche Archivierung. Vollständiges transitive-JAR-/JDK-/Oracle-Byteinventar fehlt; Publisher-PID-Reuse und Audit-Error-Text sind offen/betrieblich begrenzt.
- Dokumentierter identischer LLM-Resend entspricht nicht dem aktuellen Code: dieser fügt einen rekonstruierten Korrekturzusatz an. Frühere unverifizierte Hinweise bleiben erhalten; kein verstecktes fachliches FAIL-Wegwiederholen bestätigt. Dokumentations-/Vertragsabgleich nötig.

## Abdeckungsmatrix

GEPRÜFT bezeichnet nur den konkret genannten Umfang, keine lückenlose Fehlerfreiheit. Normen- und Endpunktdetailtabellen stehen in den verlinkten Teilberichten.

| Prüffeld | Status | Frische Evidenz / Grenze |
|---|---|---|
| Prüfstand/Quellen/Build | GEPRÜFT | 250 Vor-/Nachhashes identisch; neue Maven-Installation, frische JVMs; JDK-/Oracle-Plattformgrenze separat. |
| Fachberechnung und Referenzannahmen | TEILWEISE GEPRÜFT | Unabhängige Sollwerte, Java-/Node-/Python-Gegenproben, amtlicher Normabgleich; keine vollständige Rechts-/Fassungshistorie aller Sonderfälle. |
| Satzstände, Monatsplan, Schwangerschaft/Vermögen | TEILWEISE GEPRÜFT | Basissuite mit DB, handgerechnete Monatsproben, Service/Saga-Abgleich; tatsächlicher Vermögensverbrauch kein Softwarebeweis. |
| Zahlungszustände/Idempotenz/Vier-Augen/Historisierung | TEILWEISE GEPRÜFT | Vorhandene reale DB-/Renn-/Szenariotests und sieben Läufe frisch; tatsächliche Storno-Berechtigung separat reproduziert. |
| Bescheid und Nachdruck | TEILWEISE GEPRÜFT | Snapshot-/PaymentBinding-Tests, Generatorgegenproben, Text-/Normprüfung; vollständiger historischer Dokumentinhalt nicht eingefroren. |
| TxManager/Pools/Retry | TEILWEISE GEPRÜFT | Aktuelle Unit-/DB-Suiten und gezielte isolierte Proben; kein neuer realer Ressourcenmangel oder verlorenes Commit-Acknowledgement. |
| Saga/Recovery/Startup | TEILWEISE GEPRÜFT | Vorhandene Prozesskill-/Paralleltests frisch in Hauptsuite; zusätzlicher echter Oracle-PENDING-Zustand; neue DB-Stop-Fault-Injection nicht durchgeführt. |
| HTTP/Authentifizierung/Objektzugriff | TEILWEISE GEPRÜFT | Vollständige statische Endpunktmatrizen, aktuelle HTTP-Tests, neue HTTP-/Auth-/JSON-Proben; kein Durchsatzbeweis für Sperrmap-Sättigung. |
| UI/XSS/Sozialdaten/Logs | TEILWEISE GEPRÜFT | Code-/Kontextprüfung, Seleniumläufe, Redaktion/Headers/Origin-Gegenproben; kein vollständiger Browser-Fuzzer oder betrieblicher Datenschutz-Audit. |
| DDL Neuinstallation | GEPRÜFT für aktuelles Free-Schema | Eigenes leeres Schema, DDL+Seed erfolgreich; SQLPlus-Kommentarbesonderheit unten beschrieben. |
| Bestehende Schemas/Upgrades/19c | NICHT VOLLSTÄNDIG GEPRÜFT | Kein neuer Upgrade-/Oracle19c-Lauf, kein Schluss aus Free-Erfolg auf andere Edition. |
| Simulator/Generator/Negativinvarianten | TEILWEISE GEPRÜFT | 500+500 Fachfälle, exakte SQL-Verifikation, Last/Stress/Web; semantische Vollkombinationsabdeckung nicht aus Signaturen abgeleitet. |
| PDF/MD/Logging | TEILWEISE GEPRÜFT | Alle sieben PDFs lesbar, Status-/Fallzahlen erfasst, ausgewählte Seiten textlich, zwei Seiten visuell geprüft; nicht sämtliche 2.192 PDF-Seiten visuell gelesen. |
| Echte GLM-Prüfung | GEPRÜFT für 18 Webfälle | 9 Positiv+9 Negativ, 6 Batches, je erster Versuch gültig, Java und Node bestätigten Transport/Rechnung/Abdeckung zum Prüfzeitpunkt. |
| Zertifikatszustände/Build-/Belegbindung | TEILWEISE GEPRÜFT | Statische zentrale Gegenprüfung, 76 frische Node-Tests inkl. UI, aktuelle CLI-/Validatorgegenprobe. Kein zusätzlicher falscher Gesamt-PASS bestätigt. |
| 13-Phasen-Suite/realer Publisherexport | TEILWEISE GEPRÜFT | Orchestrierung/Tests statisch und automatisierte Fehlerregressionen; eigener frischer vollständiger Suite-/LibreOffice-Publish nicht ausgeführt, Originalzertifikate nicht überschrieben. |
| Java21-Laufzeit | NICHT GEPRÜFT | JDK25 mit release21 ist kein Laufzeitnachweis21. |
| Web-STRESS | NICHT ANWENDBAR | Aktuell nicht unterstützt; Guard und Testinventar geprüft. |

## Historische Befunde und widerlegte Spuren

| Historische Gruppe | Ergebnis am aktuellen Stand |
|---|---|
| Saga berechnet aus Payload, ignoriert Vermögen/KdU; fehlende 12-Monate-Prüfung | In gelesenen produktiven Folgeschritten behoben; aktuelle DB-Bestände und Parameter werden verwendet. |
| Fehlender Monatsplan, fehlendes Schwangerschaftsende | Monatsplan v2 und Folgemonatslogik vorhanden; unabhängige Probe 658,71 → 563 EUR. |
| Ungesperrter Bescheid, Zahlung gegen historisierte BG | Sperren/erneute Aktivitätsguards vorhanden; nicht als alte Neufunde wiederholt. |
| Harte jährliche Produktionssätze | Produktive Service-/Sagawege lesen SATZSTAND; Referenzen bewusst auf 2026 begrenzt. |
| Audit-Payload, fehlende Rotation, fehlender Shutdown | Ursprüngliche Pfade korrigiert; externe Integrität/Aufbewahrung und Error-Sonderpfad bleiben getrennt. |
| Fehlende HTTP-/Kern-/Saga-/Simulator-Eigentests | Aktuelle Tests vorhanden und frisch gelaufen; alte Inventarbehauptung widerlegt. Neue Gegenbeispiele werden trotzdem nicht erfasst. |
| Vollbestandszugriff/Bankdaten in Listen | Listen entschärft und Einsichten protokolliert; Zuständigkeits-/Teamfilter weiterhin nicht umgesetzt. |
| Sitzungstoken in Druck-URL/localStorage | Einmal-Drucktoken und sessionStorage vorhanden; neuer Logout-Kopplungsfehler ist GR-08. |
| Alte Validator-/Exportschwächen | Vollständige Fall-/Prüfpunkt-/Buildbindung und READY-/Downloadprüfung vorhanden; GR-09 ist eine konkrete verbleibende Paritätsabweichung. |
| Falsche elektronische Belehrungsempfehlung | Frühere Ergänzung übernahm die falsche Norm; GR-06 korrigiert die Bewertung. |
| SKIP LOCKED angeblich erst ab21c | Alte Promptbehauptung anhand [Oracle19c SELECT-Dokumentation](https://docs.oracle.com/en/database/oracle/oracle-database/19/sqlrf/SELECT.html) verworfen. Kein tatsächlicher19c-Lauf behauptet. |
| Verdacht `FOR UPDATE ORDER BY` verursache zwingend ORA | Eigene Parseprobe wurde von aktuellem Oracle Free akzeptiert; für diese Umgebung widerlegt. |
| Claim allein durch Altersfrist geschützt | Aktueller Kopfclaim wird gehalten und vor/nach Fachaktionen geprüft; frühere reine Altersfristannahme widerlegt. |

Die Tabellen der Teilberichte nennen die einzelnen alten IDs und aktuelle Fundstellen. Nicht jede historische MINOR-/Stilspur wurde erneut als eigener Volltest ausgeführt; hierfür wird keine uneingeschränkte historische Gesamtfreigabe behauptet.

## Tatsächliche dynamische Ergebnisse

| Prüfung | Frisches Ergebnis |
|---|---|
| `mvn install` mit TX_URL | 306 Tests, 0 Failures, 0 Errors, 1 erwarteter Konfigurations-Skip. |
| `mvn -Dtest=LmgConfigTest test` ohne TX-Variablen | 2/2 bestanden, 0 Skips; der im DB-Lauf ausgelassene Test damit nachgeholt. Nicht als 308 verschiedene Tests ausgegeben. |
| `mvn -f testsweet/pom.xml test` mit TX_URL | 180/180, 0 Fehler/Skips. |
| Node Zertifikats-/UI-Tests | 76/76, 0 Fehler/Skips. |
| Python Referenz-Eigentests | 9/9. |
| Fachschicht POSITIV / NEGATIV | 500/500 + 500/500 verifiziert. |
| Fachschicht LAST / STRESS | 120/120 + 42/42 verifiziert; Stress21 Nutzer in zwei Wellen. |
| Web POSITIV / NEGATIV / LAST | 60/60 + 60/60 + 30/30 verifiziert. |
| GLM5.3 | 18/18, 6/6 Batches, keine Formatwiederholung; echte konfigurierte HTTPS-API, keine Konfigänderung. |
| Neue Gegenproben | Storno-HTTP/Oracle und Pending-Recovery/Oracle; isolierte Fach-, Referenz-, Auth-, Parser-, Retry- und Validatorparitätsproben. |

Simulatorseed **20261015**, Denkzeit0; Fachmitarbeiter8, Stress4, Web3. Diese kleinen Last-/Stresskonfigurationen belegen Funktion unter dieser Parallelität, keine Produktionskapazität oder allgemeine Stressfestigkeit. Alle Simulatorfälle sind synthetisch.

GLM verwendete die nach unserer Laufreihenfolge neuesten erfolgreichen **Web**-Positiv-/Negativberichte, jeweils TF01–TF09. Sie enthalten nicht die gezielt zusätzlich geprüften Alleinerziehenden-Grenzkombinationen. Ein PASS zu dieser Stichprobe ist keine Antwort auf GR-02/03. Gesendete Dokumente, Requests, Responses und Hashes liegen im archivierten LLM-Lauf; `llm-summary.json` hält den erfolgreichen Validierungsstand fest. Ein Nachweis interner Modellverarbeitung wird nicht behauptet.

Die lokale Zertifikatsauswertung zum Prüfzeitpunkt zeigte JUnit, alle sieben Kombinationen, GLM und Build auf PASS, den bereits laufenden fremden Gesamtlauf dagegen auf UNVERIFIED: Gesamturteil **UNVOLLSTÄNDIG**. Der vorhandene alte Download wurde bei `--verify` erwartungsgemäß als veraltet abgewiesen. Das wurde nicht als Produktfehler gezählt und kein Originalzertifikat ersetzt.

## Überschneidung mit bereits laufender Zertifikatssuite

Bereits vor unserer Bestandsaufnahme lief Suite `812c3da8-a716-46af-83d9-7b229630659d`, gestartet **15.10.2026, 09:51:08 Europe/Berlin**, mit bestehendem Controller und einer eigenen GLM-Prüfung über100 Fälle. Diese Aktivität wurde vor unseren Teststarts nicht geprüft. Das ist eine methodische Einschränkung dieses Reviews: Neue manuelle Laufordner können in der globalen Auswahl die zur Suite gehörenden älteren Läufe verdrängen, obwohl keine ihrer Dateien verändert wird.

Zur Begrenzung der Überschneidung wurden nach Abschluss **ausschließlich die sieben eigenen Simulatorlaufordner und der eigene fertige 18-Fälle-LLM-Ordner** in `grundsicherung-gesamt-review-2026-10-15-belege/archived-runtime-runs/` verschoben. Alle Quell-/Zielpfade wurden vorher auf das Workspace-/Belegverzeichnis und die exakt aufgezeichneten eigenen IDs geprüft. Die Originaldateien innerhalb der Ordner wurden nicht umgeschrieben. `archived-run-map.json` übersetzt die ursprünglichen absoluten Pfade; `archive-integrity-check.json` bestätigt unveränderte Statushashes aller acht Ordner.

Die Quellen und Statusdateien der bereits laufenden anderen GLM-Prüfung wurden anschließend gegen ihr ursprüngliches Manifest verglichen: beide Originalquellhashes und beide Originalstatushashes unverändert. Die andere Prüfung lief bei dieser Kontrolle weiter, 60/100 Fälle. Sie wurde nicht abgebrochen, neu gestartet oder als Bestandteil unserer Ergebnisse gezählt. Ob ihre endgültige Gesamtauswertung zusätzlich von den zeitgleichen JUnit-Läufen betroffen ist, wurde nicht abschließend festgestellt; ein unveränderter späterer Gesamterfolg wird ausdrücklich nicht zugesichert. Dafür sind ihre eigenen abschließenden Suite-/Exportbelege maßgeblich.

**Folge für unsere Belege:** Die archivierten JSONs behalten absichtlich ihre ursprünglichen Pfadverweise. Sie dokumentieren die erfolgreich validierte Ausführung zum aufgezeichneten Zeitpunkt, sind nach Archivierung aber keine automatisch aktuell gültigen Zertifikatsbelege unter `testsweet/logs`. Die Übersetzung und die eingefrorenen Request-/Response-/Quellsnapshots erlauben die inhaltliche Nachprüfung. Ein neuer aktueller Gesamtzertifikatsnachweis wurde nicht aus ihnen konstruiert.

## Priorisierte nächste Schritte

1. **GR-01** schließen: Adminprüfung an tatsächliche Route/Handler binden; reale HTTP-/DB-Regression für alternative ID-Schreibweisen.
2. **GR-02/03** fachlich korrigieren und die Referenzannahmen unabhängig neu festlegen. Erst dann Generator-/Goldwert-/GLM-Prüfmaterial aktualisieren; dieselben falschen Erwartungen nicht erneut gegenseitig bestätigen lassen.
3. **GR-04** terminale Behandlung wirkungsloser PENDING-Sagas ergänzen; Startup-Regression gegen Oracle.
4. **GR-05/06** historischen Bescheidinhalt und Behörden-/Gerichtsbelehrung korrigieren; historische Nachdrucke und beide Rechtsbehelfsmodi absichern.
5. **GR-07/08** Authentifizierung unter Sättigung und Sitzung/Drucktoken-Kopplung härten.
6. **GR-09/10** gemeinsamen Antwortvertrag und enge Retry-Freigabeliste durchsetzen.
7. Nach tatsächlichen Änderungen einen quellen-/buildgebundenen Gesamtlauf in exklusiv koordinierter Umgebung durchführen. Java21-/Oracle19c-Nachweise und behördliche Einsatzvoraussetzungen separat nachholen.

Keine dieser Reparaturen wurde im Review umgesetzt.

## Reproduktion und Prüfgrenzen

Neue Skripte im Belegordner dokumentieren die ausgeführten Befehle. Basissuiten: `run-baseline.ps1`; Simulatorparameter: `run-simulator-matrix.ps1` (setzt voraus, dass ein eigenes Reviewschema und dessen Zugang eingerichtet sind). Diese Skripte nicht unkoordiniert während einer anderen Zertifikatssuite starten. `RealHttpStornoProbe.java` und `PendingRecoveryOracleProbe.java` verweigern andere Schemanamen als das eigene Reviewschema.

Das leere Reviewschema wurde per DDL/Seed neu installiert. Ein erster SQLPlus-Stream brach an einem Semikolon in einem eingebetteten Kommentar ab; nach Entfernen vollständiger Kommentarzeilen ausschließlich aus dem temporären Eingabestream gelang die Installation. Keine DDL-Datei wurde geändert, und der fehlgeschlagene Prüfaufbau wird nicht als fachlicher Produktfehler gezählt. Keine Produktion, fremde Testdaten oder fremden Prozesse wurden zurückgesetzt/beendet.

Nach allen eigenen schreibenden Proben wurde das exakt identifizierte eigene Schema bei **null offenen Sitzungen** wieder entfernt. `schema-cleanup.log` belegt diese Bereinigung; der Entwicklerbestand `TX_DEMO` und die bereits laufende andere Suite wurden dafür nicht zurückgesetzt.

PDF-/Markdown-Prüfung: `report-inspection.json`, zwei mit Poppler gerenderte Beispielseiten und die zugehörigen PNGs. Die sieben PDFs umfassen1050/907/2/3/128/100/2 Seiten, insgesamt2192. Stichprobensichtprüfung ersetzt keine vollständige visuelle Prüfung jeder Seite.

Nicht erneut ausgeführt: echter Oracle19c-Lauf, Java21-Runtime, flächiger Fuzzer, umfassende Produktionslast, exklusiver DB-Stopp/Netzabbruch, zusätzlicher echter LibreOffice-Publisher-Gesamtexport und vollständige historische Rechtsfassungsverifikation. Ein Review kann diese Grenzen offen ausweisen; er kann sie nicht durch historische Testzahlen, bloßes Lesen oder ein LLM-PASS ersetzen.
