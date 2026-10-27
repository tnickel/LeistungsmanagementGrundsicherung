# Reviewbelege vom 27.10.2026

Gehören zum Bericht `../testsweet-neue-features-codereview-2026-10-27.md`.
Produktstand: `155585471145ad2490a8cf4c11152e41e22feaf1`.
Die Proben verändern keine Produktquellen oder vorhandenen Reportdateien.

- `junit-summary.json`, `maven-testsweet.txt`: frischer `mvn -q -f testsweet/pom.xml clean test`; 333 Tests, 37 Skips, keine Fehler. Keine TX_URL, daher kein frischer Oracle-Testnachweis.
- `node-ui-summary.json`, `node-ui.txt`: 41 bestehende UI-Tests bestanden.
- `report-probes.cjs`, `report-probes-result.json`: isolierte Gegenbeispiele am tatsächlichen Reportcode. Start aus dem Repository-Stamm mit `node doc/codereview/testsweet-neue-features-2026-10-27-belege/report-probes.cjs`. Die Probe braucht die bestehenden docx-Abhängigkeiten unter `doc/handbuch-werkzeug/node_modules`; sie veröffentlicht keinen Report.
- `gui-probes.cjs`, `gui-probes.json`, `parse-linkification.py`: produktives app.js in kontrolliertem VM-Kontext; anschließend Standard-HTML-Tokenizer. Aus dem Stamm das Node-Script nach gui-probes.json ausgeben und den Python-Prüfer starten. Erfolgreiche Reproduktion einer offenen UI-Sperre bedeutet nicht, dass der Produktstand korrekt ist.
- `ReviewHttpProbe.java`, `http-probes.txt`: in Paket `de.allgemein.testsweet` kompilieren, mit `testsweet/target/classes`, `target/classes` und testsweet-Mavenabhängigkeiten im Klassenpfad ausführen. Abhängigkeiten können per `mvn -q -f testsweet/pom.xml dependency:build-classpath -Dmdep.outputFile=<temporäre-Datei>` ermittelt werden. Eigener Loopback-Port, Temp-Repository und Fake-Suite; keine tatsächlichen Simulator-/Zertifikatsjobs. Nur der Datenbank-Verbindungscheck beim Fake-Suitestart ist echt; ohne erreichbare DB wird dort 503 statt des hier beobachteten 202 dokumentiert.
- `betrieb-probes.ps1`, `bash-passwort-probe.sh`, `betrieb-probes.stdout.txt`: sichere isolierte Prozesszuordnung und Dummy-Passwort-Expansion. Kein taskkill/Docker/echtes SQL*Plus. Aus dem Stamm: `powershell -NoProfile -File doc/codereview/testsweet-neue-features-2026-10-27-belege/betrieb-probes.ps1`.
- `rechner-fall{1,6,12}-eingabe.txt`, `rechner-fall{1,6,12}.txt`, zugehörige PNG: eigene interaktive Eingaben und sichtbare Ergebnisse des externen Rechners am 27.10.2026. Textauszüge enthalten ausschließlich den Rechnerblock. Screenshots sind unverändert. Sie bestätigen diese drei Gegenproben, nicht rückwirkend die frühere behauptete Zwölf-Fälle-Erhebung.

Kein externer GLM-Aufruf, keine neue Sieben-Modi-Zertifikatssuite, kein echter Start-/Stoppversuch.
