# Zuarbeit: Kategorieintegration und Betrieb, 27.10.2026

Geprüft: Änderungen `0203134`, `64ad36e`, stichprobenartig `35da248` sowie aktueller Quellstand. Bereiche D/H/I/J/L des Auftrags. Keine Produktdatei geändert, keine echten Prozesse beendet, keine Datenbank- oder Mavenläufe durchgeführt. Die folgenden dynamischen Proben führen ausschließlich die aus dem PowerShell-AST isolierte Funktion `Test-LmgOwner` mit fiktiven Prozessobjekten bzw. eine Bash-Funktion namens `sqlplus` aus. Die Unterlagen zu Fachrechnung, Server/GUI und Report kommen aus den anderen Prüfteilen.

## Belastbare Funde

| Lokale Nummer | Schwere | Fundort | Beschreibung und Beweis | Behebung |
|---|---|---|---|---|
| BET-01 | MITTEL | `stop.ps1:10–19`, `pom.xml:18`, `stop.ps1:45` | Die Prozessverifikation akzeptiert Maven-`exec:java` ohne nachgewiesene LMG-Hauptklasse. Die Behauptung des Kommentars, das Projekt-POM habe `LmgServerMain` als Default, ist falsch: dort steht `DemoMain`. Zusätzlich umgeht ein vollständig gequotetes Argument `"-Dexec.mainClass=example.OtherServer"` die Abweisung in Zeile 13. Die isolierte echte Funktion liefert für beide fiktiven Befehlszeilen **True**. Ein solcher fremder Java-Listener am gewählten Port würde anschließend per `/T /F` beendet. Die Probe beweist die Falschauswahl; sie behauptet nicht, dass aktuell ein solcher Listener existiert. | Windows-Befehlszeile in Argumente zerlegen; bei Maven einen expliziten, exakt passenden `exec.mainClass`-Wert und das passende POM verlangen. Ohne Klassenbeleg konservativ ablehnen. Tests für gequotete Argumente und fremdes `-f`-POM ergänzen. |
| BET-02 | MITTEL | `start.bat:62–70` | `TX_USER`/`TX_PASS` werden roh in Bash-Quelltext eingebettet. Ein erlaubtes Passwort mit `$` wird deshalb verändert. Aus dem fiktiven `review/p$ssword@localhost:1521/FREEPDB1` wird nach Bash-Expansion tatsächlich `review/p@localhost:1521/FREEPDB1`. Folge: trotz ansonsten funktionsfähiger DB scheitert der neue Logincheck, wartet und warnt falsch. Shell-Metazeichen können darüber hinaus Befehlsstruktur statt Daten werden. | Zugangsdaten nicht in Shell-Quelltext interpolieren; als separate Argumente/Umgebungswerte übergeben und beim SQL*Plus-Aufruf korrekt behandeln. Dummywerte mit `$`, Leerzeichen, `&`, Anführungszeichen testen. |
| BET-03 | GERING | `start.bat:62–71` | Die angegebene Obergrenze von 300 Sekunden ist keine Obergrenze der tatsächlich verstrichenen Zeit. `warte` addiert nur fünf Sekunden pro Fehlversuch; Laufzeiten sämtlicher `docker exec`/SQL*Plus-Aufrufe kommen hinzu, und kein Aufruf hat ein externes Zeitlimit. Das folgt direkt aus der Kontrollstruktur; eine reale mehrminütige Hängeprobe wurde bewusst nicht durchgeführt. | Gesamtablauf an einer monotonen Deadline messen; jeden Loginversuch auf die verbleibende Zeit begrenzen. `warte` außerdem ausdrücklich initialisieren statt eine geerbte Umgebungsvariable zu verwenden. |
| BET-04 | GERING | `Simulator.java:440–450`, `612–620`, `892–912`, `1262–1266` | Der neue Performance-Guard prüft eine historische Liste, nicht den aktuellen Modus. Nach WEB→FACH bleibt `webMitarbeiter` gefüllt: weder Abschluss noch `setzeLaufZurueck` noch `laufAusfuehren` leert sie. Damit führt `stoppe()` auch im anschließenden Fachlauf weiterhin das langsame Browser-Cleanup aus. Zusätzlich bleibt das Cleanup beim normalen Ressourcenabschluss unbedingt. Das ist keine neue Datenkorruption und kein belegter Browser-Leak; die im Kommentar formulierte allgemeine Wirkung „nur nach Web-Läufen“ ist zu weit. | Worker nach gesichertem Webabschluss entfernen bzw. Browserbesitz des aktuellen Laufs ausdrücklich führen. Einen WEB→FACH→Stopp-Test mit injizierbarem Cleanup ergänzen. |
| BET-05 | GERING | `CertificateSuiteWorker.java:13–15`, `LlmReviewService.java:462`, `testsweet/README.md:98`, `442–444` | Die beiden neuen Kategorien fehlen in der automatischen Zertifikatsmatrix und in der GLM-Auswahl. Die sieben aufgezählten Kombinationen und 1.392 Aufträge sind für die bisherige Matrix korrekt; die Formulierung „aller sieben unterstützten Simulator-Kombinationen“ ist seit den beiden neuen Fachkategorien unvollständig. Die neuen JUnit-Konstantentests sind kein automatischer SQL-/GLM-Nachweis für ECHT/RECHNER. | Mindestens explizit sagen, dass diese Kategorien nicht Bestandteil des Gesamtlaufs sind. Bei Aufnahme zwei Fachjobs (5+12) ergänzen, Gesamtzahl auf 1.409, Fortschrittsphasen und Zertifikatspflichtmatrix anpassen. GLM-Erweiterung ist eine eigene Entscheidung; feste 100/100-Quote nicht still verändern. |

## Reproduzierbare sichere Proben

### Prozessselektion (BET-01)

Die Funktion wurde aus `stop.ps1` per `System.Management.Automation.Language.Parser.ParseInput` und `FunctionDefinitionAst` extrahiert und allein ausgeführt. Das restliche Skript, insbesondere `taskkill`, wurde **nicht** ausgeführt. Eingaben waren PSCustomObjects mit `Name='java.exe'`, fiktiver `CommandLine` und dem echten Projektpfad als `ProjectRoot`.

| Fiktive CommandLine (Projektpfad verkürzt als `<ROOT>`) | Ergebnis |
|---|---|
| `java -Dmaven.multiModuleProjectDirectory="<ROOT>" org.codehaus.plexus.classworlds.launcher.Launcher exec:java` | **True**, obwohl `pom.xml:18` `DemoMain` bestimmt |
| dieselbe Befehlszeile plus `-Dexec.mainClass=example.OtherServer` | False |
| dieselbe Befehlszeile plus `"-Dexec.mainClass=example.OtherServer"` | **True** |
| `java -cp "<ROOT>/target/classes" de.allgemein.lmg.web.LmgServerMain` | True |
| `java -cp C:/runtime/lmg.jar de.allgemein.lmg.web.LmgServerMain` | False |

Der letzte Fall ist eine konservative Einschränkung: ohne Projektpfad in der Befehlszeile erkennt das Skript einen direkten Start aus einem externen JAR nicht. Aus dem Arbeitsverzeichnis allein kann die Funktion die Zugehörigkeit nicht herleiten. Ein abweichendes `-Dmaven.multiModuleProjectDirectory` verhindert einen direkten Start dagegen **nicht**, sofern Projektpfad und Hauptklasse sonst sichtbar sind (`stop.ps1:9–10`).

### Passwort-Expansion (BET-02)

Ausführung mit `C:\Program Files\Git\bin\bash.exe -c` und folgendem unschädlichem Bash-Text:

```bash
sqlplus(){ printf "ARG=<%s>\n" "$@"; }
unset ssword
echo 'select 1 from dual;' | sqlplus -S -L review/p$ssword@localhost:1521/FREEPDB1
```

Tatsächliche Ausgabe:

```text
ARG=<-S>
ARG=<-L>
ARG=<review/p@localhost:1521/FREEPDB1>
```

Es wurden keine echten Zugangsdaten gelesen oder ausgegeben und keine Oracle-Verbindung geöffnet.

## Widerlegte Verdachte und präzise Grenzen

- **[widerlegt] ECHT/RECHNER können regulär einen Weblauf starten.** `Simulator.java:523–525` weist den asynchronen Aufruf vor Thread-/Loganlage ab; `552–556` schützt die gemeinsame interne Ausführung und damit auch `laufWebSynchron`. `SweetMain.java:75–83` weist CLI-Kategorien ab. Server/GUI werden getrennt geprüft. Die öffentliche synchrone Methode setzt zunächst Konfiguration/Startzeit zurück (`499–507`), bevor die interne Methode abweist; sie startet dabei aber weder DB noch Browser. Das ist eine kleine API-Konsistenzfrage, kein erreichbarer unkontrollierter Weblauf.
- **[widerlegt] Keine Teilmengen-Tests.** `EchtRechnerFallTest.echtTopfBegrenztAufAnzahl` prüft zwei Fälle sowie die Kappung von neun auf fünf (`42–44`); `rechnerTopfBegrenztAufAnzahl` prüft vier sowie die Kappung von 99 auf zwölf (`98–100`). Die Tests prüfen die Größe, nicht explizit die Identität/Reihenfolge jedes Teilbestands. Die Generatoren liefern deterministische Präfixmengen, weil sie zuerst die feste Gesamtliste bilden und dann `subList(0,min(...))` verwenden (`SzenarioBibliothek.java:143–151`, `166–200`).
- **[widerlegt] Neue Kategorien durchbrechen die alten Limitprüfungen.** Die Grenzen 5/12 werden zentral aus dem Enum-Switch abgeleitet (`Simulator.java:70–90`). `SimulatorBoundaryTest.allCategoryLimitsAreEnforcedBeforeAWorkerCanStart:13–19` läuft über alle Enum-Werte. Eine falsche neue Grenze oder fehlende Grenzabweisung macht dort `assertDoesNotThrow` bzw. `assertThrows` rot. Der CLI-Test prüft ausdrücklich ECHT 6 und RECHNER 13 (`SimulatorBoundaryTest.java:53–58`).
- **[widerlegt] Prefix-Umstellung verändert bestehende Kategorien.** Der Diff `git show 64ad36e -- Simulator.java` fügt nur E-/R-Zweige hinzu. POSITIV, NEGATIV, LAST und STRESS behalten exakt ihre bisherigen Kategorie-Suffixe (`Simulator.java:837–857`). Die UUID-Laufkennung bestand vorher. Die direkten Generator-Prefixtests stehen in `EchtRechnerFallTest.java:36,87`; ein Test des Simulator-Switches selbst fehlt noch.
- **[widerlegt] Leere neue `webMitarbeiter`-Liste kann schon gestartete Worker-Browser desselben Laufs enthalten.** Alle Workerobjekte werden vor dem ersten `executor.submit` in die Liste eingetragen (`Simulator.java:612–629`). Die Konstruktoren öffnen keinen Browser (`WebMitarbeiter.java:47–54`); `ChromeDriver` entsteht erst im Worker-`run` (`62`, `97–107`). Ein Abbruch vor Workeranlage hat daher keinen Browser dieses Laufs erzeugt. Fehler bei der Fensterplatzierung führen zu `quit()` (`121–125`); Worker-`finally` und Simulator-`finally` schließen zusätzlich (`84`, `Simulator.java:647–648,1266`); der Zertifikatsworker räumt abschließend unabhängig auf (`CertificateSuiteWorker.java:87`). Harte JVM-Kills und schon früher verwaiste Profile sind hiervon getrennt. Für sie bleiben Start-/Endcleanup und der explizite Cleanupknopf erhalten. Ein neuer Leak durch die Änderung an `stoppe` ist damit nicht belegt.
- **[widerlegt] Falscher Massentest-Bericht für neue Kategorien.** Nur LAST/STRESS nehmen den `LastBericht`-Zweig (`Simulator.java:867–874`). ECHT/RECHNER erhalten die detaillierte PDF/Markdown-Dokumentation. Kategorien werden generisch aus `letzteKategorie()`/`name()` übernommen (`MdBericht.java:68`, `Testdokumentation.java:121`, `Simulator.java:1009,1246`). Die veraltete JavaDoc-Aufzählung bei `letzteKategorie` (`461`) ist kosmetisch.
- **[widerlegt] Katalog-Grundanzahlen stimmen nicht.** `SzenarioBibliothek.erzeuge:77–95` hat zehn Grundarten, neun feste Fälle 11–19 und ab 20 Kombinationen mit Kassenlauf an jedem 50. Platz. Bei 500 Aufträgen: 500−19−10=471 Kombinationsfälle, wie `TestfallKatalog.java:212` sagt. NEGATIV hat neun Grundarten und ab 10 Kombinationen (`SzenarioBibliothek.java:114–127`), genau wie das Vorwort (`TestfallKatalog.java:267–270`).
- **[widerlegt] stop-Skript beendet jeden Portbesitzer blind.** Nicht-Java und fehlende Projektpfade werden abgewiesen (`stop.ps1:5,9`). Alle Listener werden vor dem ersten Kill geprüft und CreationDate/CommandLine erneut geprüft (`34–45`). Das begrenzt PID-Verwechslungsfehler; es heilt BET-01 nicht. `/T /F` ist ausdrücklich ein erzwungener Kill der bestätigten Java-Prozessfamilie, kein geordneter Java-Shutdown und kein Rücknahmebeleg für schon abgeschlossene Transaktionen.
- **[widerlegt] Docker-Zweig stoppt einen ähnlich benannten anderen Container.** `Select-String -SimpleMatch` in `stop.ps1:62` ist tatsächlich nur ein Teilstring-Test; der eigentliche `docker stop lmg-oracle` ist aber exakt benannt (`67`). Ähnlich benannte Container können nur eine unzutreffende Statusmeldung provozieren, keinen Stopp des falschen Containers. Exakter Namensvergleich wäre trotzdem klarer.
- **[widerlegt] SQL*Plus fehlt / 300s überschritten führt zu stillem Erfolg.** Fehlender Prozess/Login führt wieder in die Schleife; nach deren Zählergrenze wird ausdrücklich gewarnt und dann kompiliert/gestartet (`start.bat:62–70`). Die Fortsetzung ist dokumentierte Absicht, nur die tatsächliche Zeitgrenze ist falsch (BET-03). Der VM-Zweig 1921 bleibt separat und unverändert (`81–89`).
- **[Stichprobe] Lernpfad-Kommit:** `git show 35da248` für `TxManager`, `RetryPolicy`, `SozialLeistungsService` und `tx/saga/package-info.java` zeigt Kommentaränderungen und End-of-file-Newline-Änderungen, keine geänderte ausführbare Anweisung. Daraus folgt kein pauschaler Vollvergleich sämtlicher 76 Dateien. Die Kurzfassung „alles oder nichts“ im Service lässt den gesonderten unbekannten Commit-Ausgang im Kern unerwähnt; als Einstieg ist das eine Vereinfachung, kein nachgewiesener Verhaltensfehler.

## Konkrete zusätzliche Tests

- **L-BET-01:** Pester- oder vergleichbarer isolierter `Test-LmgOwner`-Test: echte LMG-CLI, unquotierter/gequoteter Fremdoverride, fehlender Override bei aktuellem POM-Default, fremdes `-f`-POM, ähnlicher Rootpfad, direkte JAR-Ausführung. `taskkill` vollständig mocken. Die beiden True-Fälle aus BET-01 müssen False erwarten und den jetzigen Stand rot machen.
- **L-BET-02:** Bereitschaftscheck mit gemocktem Docker/SQL*Plus und Dummycredentials einschließlich `$`, `&`, Leerzeichen, Quote; empfangene Zeichenfolge exakt vergleichen. Zusätzlich langsamer/hängender Check, fehlendes SQL*Plus, 300s-Deadline und separater VM-Pfad.
- **L-BET-03:** `SimulatorBoundaryTest` um ECHT/RECHNER jeweils für `starteWeb`, `laufWebSynchron` und CLI `web`/`run-web` ergänzen. Vorher/nachher Laufstatus, Logverzeichnis und Browserfabrik prüfen. Aktuell enthält der CLI-Test nur ECHT, STRESS, aber kein Web-RECHNER (`SimulatorBoundaryTest.java:53`).
- **L-BET-04:** Teilmengen parametrisiert für 1..5 und 1..12 prüfen: nicht nur Größe, sondern Szenariotyp, Reihenfolge, festes Soll und Prefix aus dem Simulator. Auftragszahl 1 ist ein schneller Smoke-Test, keine gleichwertige Abdeckung des ganzen Kategorienbestands.
- **L-BET-05:** Browser-Cleanup injizierbar machen; WEB→FACH→Stopp darf keinen unnötigen Browser-Scan auslösen. Fehler vor Workeranlage und nach Treiberkonstruktion getrennt prüfen. `BrowserProzesseTest` prüft nur `gehoertZuProfil` (`12–28`), nicht die Auswahl/Seiteneffekte des PowerShell-Gesamtskripts.
- **L-BET-06:** Katalogtests an erzeugte Szenariotypen koppeln. Die Anzahltests sind als bewusste Dokumentumfangssperre nützlich, aber können Falschbeschreibungen nicht erkennen. Der Vorworttest prüft nur drei Frage-Strings (`TestfallKatalogTest.java:53–58`); sachlich falscher Antworttext bleibt grün.

## Prüfgrenzen

Kein echter Start/Stopp und kein Docker-/Oracle-Kill, keine neuen Simulatorläufe. Die sichere AST-/Bash-Probe belegt Parser- und Expansionsfehler, nicht deren Auftreten auf diesem Rechner unter aktuellen Liveprozessen. Keine Aussagen über aktuell ausgeführte Maven-/Oracle-Tests; diese gehören in die Gesamtverifikation des Hauptprüfers.
