# testsweet: kritische Gegenprüfung des GLM-5.3-Reviews

Datum: 27.10.2026. Geprüfter aktueller Stand: `2a7bc5e` auf `learning`.
Der Fremdbericht bezieht sich auf den älteren Stand bis `1555854`.
Die Originalberichte und Prüfaufträge bleiben als historische Quellen erhalten;
ihre Aufnahme ins Repository bestätigt nicht sämtliche darin enthaltenen Aussagen.

Diese Gegenprüfung war lesend. Sie dokumentiert offene Befunde und die Bewertung
früherer Aussagen; sie behauptet keine Umsetzung der unten genannten Korrekturen.

## Heute bestätigte offene Punkte

### P2: Browser-Cleanup liest vor der Prozessfrist bis EOF

`BrowserProzesse.raeumeVerwaisteFensterAuf` liest die Ausgabe des Aufräumprozesses
vollständig, bevor `waitFor(20, TimeUnit.SECONDS)` beginnt
(`BrowserProzesse.java:91–97`). Ein Prozess, der nicht endet und seinen Ausgabekanal
offen hält, blockiert damit schon vor der vorgesehenen Frist. Start oder Stopp
können unbegrenzt warten.

Eine isolierte Probe verwendete den echten Methodenaufruf mit ausschließlich
eigenem temporärem Arbeitsverzeichnis und einem Aufräumskript mit
`Start-Sleep -Seconds 23`. Gemessen wurden **23.976 ms**, Ergebnis 0; nach 20 Sekunden
erfolgte kein Abbruch. Echte Browser oder Benutzerprofile wurden nicht verwendet.
Die Probe wurde aufgeräumt.

Abhilfe: Prozessausgabe unabhängig von der Prozessfrist lesen, die Frist auch bei
offenem Ausgabekanal durchsetzen und nach dem Abbruch des eigenen Hilfsprozesses
dessen Ende abwarten. Dieser Befund beweist nicht die Ursache des historischen
Renderer-Vorfalls.

### P2: Management-Report übernimmt das Manifesturteil ungeprüft

`publish-report.js:133–140` prüft Eingabebindung und PDF-/DOCX-Hashes, gibt danach
aber `marker.verdict` ohne Abgleich mit dem frisch berechneten Urteil zurück.
Die eigene Manifestdatei gehört bewusst nicht zum Eingabefingerprint.

Isoliert reproduziert: tatsächlicher Prüfstand UNVOLLSTÄNDIG, passende Eingabe-
und Ausgabehashes; ausschließlich das Manifesturteil wird auf BESTANDEN geändert.
`pruefe()` liefert weiterhin READY mit BESTANDEN. Die sichtbare PDF bleibt dabei
unverändert. Betroffen ist die maschinenlesbare Verify-/API-Aussage, nicht eine
nachträgliche Änderung der PDF.

Abhilfe: Das Urteil beim Abruf mit `runs.reportStand(repo).verdict` vergleichen,
analog zur bereits gehärteten formalen Zertifikatsprüfung. Die vorhandenen
Reportregressionen decken diesen zusätzlichen Markerfall noch nicht ab.

### Zeichensatzgrenze: andere Unicode-Zeichen gehen weiter verloren

Der konkrete Pfeilvorwurf ist falsch: `escape()` ruft `normalisiere()` auf und
wandelt `→` bereits zu `->`. Das galt auch in `1555854`.
Die allgemeine WinAnsi-/CP1252-Grenze bleibt jedoch bestehen.

Eine isolierte Probe mit frisch für Java 21 kompiliertem `SweetPdf` ergab nach
PDF-Textextraktion:

- `Miete 300 → 350` wird korrekt als `Miete 300 -> 350` ausgegeben, auch im Footer.
- `Łukasz Иван` wird ohne Warnung zu `?ukasz ????`.
- `≤ ≥ − € ä` wird zu `<= >= - € ä`.

Die Ursache ist die verbleibende Ersetzung nicht darstellbarer Zeichen durch
`getBytes(CP1252)` (`SweetPdf.java:443`, Normalisierung ab 675).
Eine Unicode-fähige Schrift oder eine ausdrücklich sichtbare Ersatzdarstellung
wäre belastbarer als ein stilles Fragezeichen. Eine aktuell abgeschnittene
Standardzeile wurde nicht nachgewiesen.

## Bewertung der GLM-Befunde

| Punkt | Ergebnis am aktuellen Stand |
|---|---|
| CR-V01: fünf Fachtabellen, kein DDL-Abgleich | Wartungs-/Abdeckungslücke, kein heutiger fehlerhafter Abwehrpfad nachgewiesen. Neue Spalten innerhalb dieser Tabellen werden durch `SELECT *` und JDBC-Metadaten bereits erfasst. Die DDL enthält zusätzlich die globale Referenztabelle SATZSTAND, die im aktuellen Produkt nur gelesen wird und kein BG_ID besitzt. Ein pauschaler Test auf sämtliche Nicht-TX-Tabellen wäre deshalb falsch; sinnvoll ist eine ausdrückliche Klassifikation mit DDL-Abgleich. |
| CR-V02: gemeinsame Rechtsauslegung | Berechtigte Aussagegrenze. Arithmetische Implementierungsunabhängigkeit ist keine unabhängige vollständige Rechtsprüfung. Diese Grenze ist bereits dokumentiert; daraus folgt allein kein belegter Rechenfehler. |
| CR-V03: Pfeile würden zu Fragezeichen | Konkrete Behauptung widerlegt. Die allgemeinere Unicode-Grenze ist wie oben beschrieben reproduzierbar. |
| CR-V04: ungefilterte Surefire-Zähler | Am älteren Reviewstand berechtigt, inzwischen behoben. Aktuelle Zähler sind lauf-, quell- und buildgebunden; nachgeholte Teilprüfungen werden ohne Doppelzählung einbezogen. |
| CR-V05: BESTANDEN trotz fehlender Kategorien, 0-verifiziert als Abbruch | Am älteren Stand berechtigt, inzwischen behoben. Feste vollständige Matrix einschließlich JUnit/GLM; bekannte Fehler haben unabhängig von der Verifikationszahl Vorrang. Der oben beschriebene zusätzliche Manifestfall ist davon zu unterscheiden. |
| CR-V06: fehlender TOC-Anker liefert null | Am älteren Stand berechtigt, inzwischen behoben. Fehlende, doppelte oder falsch geordnete Anker sowie verschobene Kapitelstarts im zweiten Satz verhindern die Veröffentlichung. |
| CR-V07: keine allgemeine globale Doppelzahlungsabfrage | Zusätzliche Absicherung sinnvoll; Unique-Index und echte Parallel-Anordnungsregression bestehen bereits. Eine COUNT-Abfrage erkennt entstandene Duplikate, nicht einen bloß gelöschten Index ohne Duplikate. Eine Ergänzung müsste außerdem auf RunDataScope begrenzt sein und ein skalares äußeres COUNT über GROUP BY/HAVING verwenden. |
| CR-V08: Browser vor Zuweisung der Worker-Liste | Durch die tatsächliche Startreihenfolge widerlegt, auch am älteren Stand. Die Liste entsteht vor dem Start der Worker; Chrome wird erst in `WebMitarbeiter.run()` erzeugt. Heute besteht zusätzlich `webCleanupPending`. |
| CR-V09: fehlender Schritt-Wachhund erklärt Freeze | Eine eigene vollständige Aktionsdeadline fehlt; als historische Ursachenbehauptung unbelegt. Es bestehen WebDriver-Waits, Selenium-HTTP-Zeitlimits und eine Laufgrenze. Nach Interrupt ignorierenden Aktionen kann das Beenden weiter warten. Ein Wachhund allein beweist weder Rollback noch einen geklärten Schreibausgang. |
| CR-V10: ECHT/RECHNER außerhalb des Zertifikats | Bewusster, inzwischen ausdrücklich in GUI, Deckblatt und Metadaten genannter Umfang. Keine automatische Erweiterung der Matrix aus dieser Gegenprüfung. |
| CR-V11: Git-Kopien erzeugter PDFs veralten | Vertretbare Dokupräzisierung. Die Entwertung alter Momentaufnahmen ist bereits beschrieben. Historische Git-Dateien sind von einem aktuell verifizierten Abruf zu unterscheiden. |

V-M1 ist bereits am ursprünglichen Reviewstand widerlegt: Weniger als zwei
ausgewählte GLM-Fälle werden abgewiesen. Java verlangt 2–1000 Fälle und wählt
positive und negative Fälle quotiert aus. Ein höheres Minimum von 100 oder 200
Fällen ist keine allgemeine Pflicht; die tatsächlich gewählte Stichprobe muss
sichtbar bleiben.

Die pauschal genannten Testlücken L1–L4 treffen auf den aktuellen Stand nicht zu:
HTTP-, Report-, Chronologie-, Inhaltsverzeichnis- und Betriebsskriptregressionen
existieren. Das ersetzt weder einen noch fehlenden DDL-Abgleich noch eine
reproduzierte Ursachenanalyse des alten Browservorfalls.

## Nicht belegte externe Zwölf-Fälle-Prüfung

Die Behauptung, alle zwölf RECHNER-Werte seien durch vollständige externe
Eingabe-/Ergebnisbelege abgesichert, ist nicht nachgewiesen. Das aktuelle
Belegverzeichnis unterscheidet zwölf feste handrechenbare Erwartungen und eigene
dokumentierte Browsergegenproben ausschließlich für **1, 6 und 12**.

Der alte Screenshot `rechner-steuerschroeder-fall12-2026-10-27.png` wurde visuell
geprüft: Er zeigt Erläuterung, Regelsatztabelle und Cookiebox, keine Eingaben und
kein Rechenergebnis. Das widerlegt nicht, dass jemand außerhalb der gespeicherten
Belege weitere Vergleiche vorgenommen haben könnte; es trägt aber keinen
vollständigen externen Zwölf-Fälle-Nachweis.

## Verifikation dieser Gegenprüfung

Die 30 gezielten Reportregressionen bestehen. Die beschriebenen isolierten
Marker-, Unicode- und Cleanup-Proben verändern keine Repositoryquellen oder
Maven-Belege und erforderten keine echten Browser oder externen GLM-Aufrufe.
Ein neuer Gesamtzertifikatsnachweis wurde daraus nicht abgeleitet.
