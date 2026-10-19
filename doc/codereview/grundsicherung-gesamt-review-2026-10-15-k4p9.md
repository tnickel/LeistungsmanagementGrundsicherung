# Gesamt-Review SDN.Leistung — 2026-10-15-k4p9

Prüfgegenstand ist der Arbeitsstand `37abfb6f3da841e440ab21a53fbffa7eadf7cd04` auf `main`. Versionierte Quellen waren zu Beginn und am Ende unverändert (265 Dateien, 0 Hash-Abweichungen). Dies ist ein Review, keine Reparatur und keine Produktionsfreigabe.

## 1. Ergebnis

Nach der zentralen Gegenprüfung der drei Teilberichte sind **16 Befunde** bestätigt, darunter drei mit Schwere **KRITISCH**. Ein **BLOCKER** ist nicht bestätigt. Alle Belege dieser Nachträge sind statisch, soweit ein Befund nichts anderes sagt. Die grünen Suites dieses Stands bleiben gültig und schließen die neuen Pfade nicht aus.

Der schwerste bestätigte Pfad ist ein Task-Abbruch: Der HTTP-Aufrufer erhält HTTP 500 mit der Aufforderung, es erneut zu versuchen, während die innere Fachtransaktion noch committen kann (FB-20261015-02). Eine zweite aktive Zahlung desselben Monats verhindert der Unique-Index. Deckt das verwertbare Vermögen den Monatsbedarf, bleibt der Bestand stehen und jeder Folgemonat wird erneut abgelehnt (FB-20261015-03); ein Cent darunter wird fortgeschrieben und danach voll gezahlt. Jede angemeldete Sitzung kann jede Bedarfsgemeinschaft lesen und ändern (FB-20261015-04). Weitere bestätigte Punkte: Kompensation offener Sagas über den aktuellen Schrittindex, Kassenlauf über 1000 Zahlungen in einer IN-Liste, Maskenvorschau am JVM-Monat, fehlender Änderungsbescheid bei gesperrter Neufestsetzung, doppelte Alleinerziehendenzähler, Klartext eines fehlerhaften Kontensegments, sowie kleinere Journal-, Snapshot- und Einbettungslücken. Die README-Kurztabelle bleibt FB-20261015-01.

Der Alleinerziehenden-Günstigervergleich ist als Implementierungsfehler **nicht** bestätigt. Der Fach-Teilbericht hat WD 6-046/24 als unverifiziert gewertet. Die zentrale Prüfung hat die Ausarbeitung gelesen: Sie überträgt den Vergleich von § 21 Abs. 3 SGB II auf § 30 Abs. 3 SGB XII. Die Spannung zum Ausschließlichkeitssatz bleibt die offene Auslegung aus Abschnitt 3.

Getrennte Urteile: Die geprüften Normkerne (Regelbedarfsstufen 2026, § 30 Abs. 2, 6 und 7, § 35 Abs. 5, § 82 Abs. 3, BarbetragsVO-Grundbeträge) folgen dem amtlichen Text oder der genannten Auslegung. Der Geldfluss ist bei Vier-Augen, Monats-Eindeutigkeit, Idempotenz und dokumentierter Vermögensrückbuchung stimmig; die Vermögensfortschreibung bricht ab, sobald der Einsatz den Monat vollständig deckt, und ein erteilter Bescheid kann in der Sachbearbeitung nicht durch einen Änderungsbescheid ersetzt werden. Der Transaktionskern wiederholt nur die Vendorcodes 54, 60 und 8177; der Wachhund beweist den Rollback einer bereits committeten inneren Fachtransaktion nicht. Die frühere Storno-Umgehung ist im Router geschlossen; eine objektbezogene Zugriffsberechtigung fehlt. Die automatischen Tests dieses Stands sind grün und belegen weder die sieben Simulator-Vollläufe, noch einen GLM-Aufruf, noch Oracle 19c EE, noch eine Laufzeit unter Java 21.

Frische Läufe: Hauptprojekt `mvn -B test` mit `TX_URL` auf `FREEPDB1`, 12:34:30–12:35:18, Exit 0, **325 Tests, 0 Fehler, 1 beabsichtigter Skip**. Derselbe `LmgConfigTest` ohne `TX_URL`, 12:38:29, Exit 0, **2/2**. `mvn -B install -DskipTests` danach nur zur Artefaktinstallation des bereits getesteten Stands. testsweet `mvn -B -f testsweet/pom.xml test`, Ende 12:37:47, Exit 0, **249/249, 0 Skips**. Node-Werkzeug 100/100, UI-Skript 30/30. Oracle AI Database 26ai Free **23.26.3.0.0**, PDB `FREEPDB1`. Laufzeit **Temurin 25.0.4.1**, Compiler `--release 21`.

Die Teilberichte von [Transaktionskern und Oracle](58963d01-05a1-4e41-a4ca-1ef7f1a67f29), [Fachrecht und Geldfluss](a20e90c8-5a12-4c18-bac4-476084d24abf) und [Web, Sicherheit und Zertifikat](fa5c30a9-91d2-4147-a384-d40e88e2fe93) wurden zentral gegen den Quellstand gelesen. Übernommen ist nur, was Aufrufer, Sperren und DDL tragen. Die Zuordnung steht in Abschnitt 5.

## 2. Bestätigte Befunde

### FB-20261015-01 — README-Kurztabelle widerspricht Norm und Rechenweg

- **Schweregrad:** MINOR
- **Belegsicherheit:** STATISCH BELEGT
- **Komponente:** `README.md:137-141`. Der Rechenweg selbst steht in `Leistungsberechnung.java:63-64`, `:189-198`, `:231-244` und in `doc/rechtsgrundlagen.md` Abschnitt 1.2 sowie `doc/testzertifikat-werkzeug/gesetzesgrundlagen-2026.md`.
- **Verletzte Regel:** [§ 30 Abs. 2, 3, 6 und 7 SGB XII](https://www.gesetze-im-internet.de/sgb_12/__30.html) und [§ 35 Abs. 5 SGB XII](https://www.gesetze-im-internet.de/sgb_12/__35.html), abgerufen am 2026-10-15. Die Kurztabelle ist die öffentlich lesbare Fachbeschreibung.
- **Auslöser:** Lesen der Management-Tabelle, ohne den Code oder `rechtsgrundlagen.md` daneben zu legen.
- **Soll:** Schwangerschaftsmehrbedarf nach der zwölften Schwangerschaftswoche, also ab der 13. SSW, bis zum Ende des Entbindungsmonats. Alleinerziehendenmehrbedarf im Produkt: Günstigervergleich von 36 Prozent und 12 Prozent je Kind, Deckel 60 Prozent der Stufe 1 (Auslegung WD 6-046/24). Dezentrales Warmwasser liegt außerhalb des Deckels nach Abs. 6. Zentrale Warmwasserkosten bleiben Heizkosten; `lmg.kdu.warmwasserabzug` mindert nichts.
- **Ist:**
  1. Zeile 137: „ab der 12. Schwangerschaftswoche“.
  2. Zeile 138: „36 %, sonst 12 % je minderjähriges Kind“. Das Wort „sonst“ beschreibt die ausschließliche Alternative. Der Code nimmt bei erfüllter 36-Prozent-Konstellation das Maximum mit 12 Prozent je Kind (`Leistungsberechnung.java:237-239`).
  3. Zeile 140: „Summe der Mehrbedarfe höchstens der Regelbedarf“, ohne den gesetzlichen Ausschluss von Abs. 7. Der Code deckelt nur Abs. 1–5 und addiert Warmwasser danach.
  4. Zeile 141: „Warmwasser-Abzug nur bei zentraler Erzeugung konfigurierbar“. Der Parameter wird nur noch validiert und danach als 0,00 EUR angesetzt.
- **Auswirkung:** Kein eigener Rechenfehler im Service. Wer die README als Spezifikation benutzt, erwartet eine andere Schwangerschaftswoche, eine niedrigere Alleinerziehendenleistung bei vier und mehr Kindern mit zugleich erfüllter Nr. 1, einen zu weiten Mehrbedarfsdeckel und einen wirksamen zentralen Warmwasserabzug. Die Beträge 95,71 EUR und 563/506/451/471/390/357 EUR in derselben Tabelle stimmen mit [§ 2 RBSFV 2026](https://www.gesetze-im-internet.de/rbsfv_2026/__2.html) und 17 Prozent von 563,00 EUR überein.
- **Evidenz:** Dateivergleich README gegen den am 2026-10-15 abgerufenen Wortlaut von § 30 und gegen die genannten Codezeilen. Die Node-Referenztests erwarten für die Vier-Kinder-Konstellation 270,24 EUR und für zentrales Warmwasser 1.063,00 EUR; sie sind in diesem Lauf 100/100 grün und bestätigen den Code, nicht die README-Sätze.
- **Gegenprüfung:** `doc/rechtsgrundlagen.md` und `gesetzesgrundlagen-2026.md` formulieren die vier Punkte bereits im Sinne des Codes. Unit-Tests sichern den Code, nicht die README-Tabelle. Deshalb bleibt der Befund auf die Kurztabelle begrenzt.
- **Reparatur:** Die vier Sätze in `README.md` an § 30, § 35 Abs. 5 und den bestehenden Rechenweg anpassen. Keine Änderung der Berechnung, solange der Günstigervergleich die gewollte Auslegung bleibt.
- **Regression:** Ein Texttest, der in `README.md` „nach der zwölften“ oder „13. SSW“, den Günstigervergleich, den Abs.-7-Ausschluss vom Deckel und die Wirkungslosigkeit des Altparameters verlangt und die Formulierungen „ab der 12. Schwangerschaftswoche“, „sonst 12 %“ und „Warmwasser-Abzug nur bei zentraler Erzeugung konfigurierbar“ ausschließt.

### FB-20261015-02 — Abbruch meldet Fehlschlag nach bereits committeter Fachwirkung

- **Schweregrad:** KRITISCH
- **Belegsicherheit:** STATISCH BELEGT
- **Komponente:** `TaskController.java:232-235` und `:273-281`, `TaskHandle.java:68-73`, `TxManager.java:45-47`, `LmgServer.java:225-250`, `webapp/app.js:636`. Teilbericht KR-20261015-01.
- **Verletzte Regel:** Ein Fehler nach erfolgreichem Commit ist kein Rollbackbeweis. Der Wachhund-Kommentar in `TaskController.java:38-50` beschreibt nur das Fenster des äußeren JDBC-Commits.
- **Auslöser:** Jede HTTP-Schreibanfrage läuft als Task mit `TxOptions.DEFAULT` (30 Sekunden ab Ausführungsbeginn, vor dem Borrow). `bearbeiteApi` verwendet die äußere Verbindung nicht. Service und Saga committen auf eigenen Verbindungen. `future.cancel(true)` lässt `TaskHandle.result()` sofort mit Abbruch zurück, während der Worker einen JDBC-Aufruf fortsetzen kann. `TxManager.execute` prüft das Interrupt-Flag vor `commit()` nicht.
- **Soll:** Die HTTP-Antwort nennt den Erfolg, sobald die Fachwirkung committed ist. Eine Wiederholung zeigt die vorhandene Buchung.
- **Ist:** Der Client erhält HTTP 500, Text „Technischer Fehler — bitte erneut versuchen“. Der nächste Klick erzeugt eine neue Vorgangsnummer. Dieselbe Bedarfsgemeinschaft und derselbe Monat werden durch `UQ_ZAHLUNG_BG_MONAT_AKTIV` und `existiertAktiveFuerMonat` abgewiesen. Die Maske lädt den Vorgang im Fehlerzweig nicht neu.
- **Auswirkung:** Zahlung, Bescheid oder Saga-Schritt können dauerhaft sein, während die Sachbearbeitung einen Fehlschlag sieht. Eine zweite aktive Monatszahlung entsteht dadurch nicht. Schreibpfade ohne natürliche Idempotenz können bei veränderten Eingaben erneut wirken. Reichweite: jeder Lauf, der das 30-Sekunden-Budget erreicht, solange der Treiber den Interrupt nicht als Abbruch der laufenden Anweisung auswertet. Das Treiberverhalten selbst ist nicht ausgeführt.
- **Evidenz:** Der äußere `checkDeadline` liegt nach `work.run`. Die Fach-SQL liegt in der inneren `execute`-Transaktion. `CancellationException` wird als nicht wiederholbare `TxException` gemeldet und in `LmgServer` auf 500 mit Wiederholungsaufforderung abgebildet.
- **Gegenprüfung:** Monatsindex, Status-Guards von Freigabe, Storno und Kassenlauf sowie `UQ_BG_AKTENZEICHEN` verhindern die naheliegende Doppelbuchung. `TaskControllerWatchdogBudgetTest` unterbricht einen Sleep auf einem Fake-Pool und prüft nicht, dass der Client den Erfolg einer inneren, bereits committeten Transaktion sieht.
- **Reparatur:** Die HTTP-Antwort erst nach dem Ende des Workers senden. Bei Ablauf den Vorgang neu lesen und einen unbekannten Ausgang ausdrücklich so benennen, ohne zur blinden Wiederholung aufzufordern. Die Vorgangsnummer der laufenden Anordnung bis zur Antwort beibehalten.
- **Regression:** Innere Transaktion committet, der Worker blockiert danach, der Wachhund feuert. Assertion: die Zahlungszeile existiert, die HTTP-Antwort fordert keine Neuordnung mit neuer Vorgangsnummer an, ein zweites POST mit neuer Nummer legt keine zweite aktive Zeile an.

### FB-20261015-03 — Deckendes Vermögen wird nicht fortgeschrieben

- **Schweregrad:** KRITISCH
- **Belegsicherheit:** DYNAMISCH BELEGT für den Monatsplan, STATISCH BELEGT für die Buchung
- **Komponente:** `Bewilligungsplan.java:62-70`, `SozialLeistungsService.java:301-305` und `:343-346`. Teilbericht Fach, dort als FB-20261015-04 geführt. Der Plan wurde in jener Prüfung mit `jshell` gegen `target/classes` ausgeführt.
- **Verletzte Regel:** [§ 90 SGB XII](https://www.gesetze-im-internet.de/sgb_12/__90.html), abgerufen am 2026-10-15: das verwertbare Vermögen ist einzusetzen. Die implementierte Einmalfortschreibung bucht den Einsatz nur zusammen mit einer positiven Zahlung.
- **Auslöser:** Schonvermögen 10.000,00 EUR, Bestand 10.563,00 EUR, Regelbedarf Stufe 1 ohne anderes Einkommen: verwertbar 563,00 EUR, ungedeckter Bedarf 563,00 EUR, Zahlbetrag 0,00 EUR. Dieselben Beträge wurden vor dem Vergleich mit dem Code festgehalten.
- **Soll:** Der deckende Einsatz mindert den Bestand um 563,00 EUR. Der Folgemonat sieht 10.000,00 EUR und einen Zahlbetrag von 563,00 EUR.
- **Ist:** `zahlbetrag <= 0` wirft `KEIN_ANSPRUCH`, bevor `mindereVermoegen` läuft. Der Plan zieht den Bestand nur ab, wenn `zahlbetrag > 0`. Der Folgemonat startet wieder bei 10.563,00 EUR und wird erneut abgelehnt. Der knappe Fall 10.000,01 EUR zahlt 562,99 EUR und schreibt fort; ab dem Folgemonat sind es 563,00 EUR.
- **Auswirkung:** Ein Cent zusätzliches verwertbares Vermögen, der den Monat gerade deckt, sperrt die Leistung für alle folgenden Monate, bis jemand den Bestand von Hand senkt. Haushalte darunter erhalten ab dem Folgemonat die volle Leistung. Reichweite: jede Bedarfsgemeinschaft, deren verwertbares Vermögen den ungedeckten Monatsbedarf erreicht oder übersteigt.
- **Evidenz:** Die beiden `if`-Zweige. Der ausgeführte Plan lässt 10.563,00 EUR in beiden Monaten stehen und schreibt 10.000,01 EUR auf 562,99 EUR, danach 563,00 EUR fort (`teilbericht-fach.md`, Abschnitt 3c). Die Buchung selbst, die vor `mindereVermoegen` abbricht, ist am Service gelesen und nicht noch einmal auf Oracle ausgeführt. Die bestehende Suite erwartet die Fortschreibung des knappen Falls und den Abbruch bei nicht positivem Zahlbetrag.
- **Gegenprüfung:** Die Ablehnung einer Nullzahlung ist für sich stimmig, weil eine Zahlung mit Betrag 0 in der Statusmaschine keinen Sinn ergibt. Sie ersetzt die fehlende Bestandsminderung nicht. Die Dokumentation der Einmalanrechnung beschreibt dieses Verhalten und macht die Klippe nicht zu einer gesetzlich tragenden Verbrauchsregel.
- **Reparatur:** Deckt das verwertbare Vermögen den Monat, den eingesetzten Betrag in derselben fachlichen Entscheidung fortschreiben und den Monat als durch Vermögen gedeckt protokollieren, ohne eine Nullzahlung anzulegen. Der Folgemonat rechnet mit dem geminderten Bestand.
- **Regression:** Bestand 10.563,00 EUR, zwei Monate, Stufe 1, kein Einkommen. Assertion: Monat 1 ohne Zahlungszeile und Bestand 10.000,00 EUR; Monat 2 Zahlbetrag 563,00 EUR. Gegenprobe 10.000,01 EUR bleibt 562,99 EUR im ersten Monat.

### FB-20261015-04 — Jede Sitzung erreicht jede Bedarfsgemeinschaft

- **Schweregrad:** KRITISCH
- **Belegsicherheit:** STATISCH BELEGT
- **Komponente:** `LmgServer.java` Lesen und Schreiben von Bedarfsgemeinschaft, SozialFall, Bescheid, Zahlung und Journal ohne Objektprüfung; `SozialLeistungsService` prüft die Existenz, nicht die Zuständigkeit. Teilbericht WB-20261015-01. Historisch CR-035 Option B/C, bisher nur als Betriebsgrenze geführt.
- **Verletzte Regel:** Eine protokollierte Einsicht ersetzt die bedarfsgerechte Zugriffsberechtigung nicht (§ 35 SGB I, Art. 32 DSGVO, im Produkt bereits als Protokollzweck genannt).
- **Auslöser:** Gültige Sitzung der Rolle Sachbearbeiter oder Admin und eine bekannte Bedarfsgemeinschafts-ID.
- **Soll:** Vollständige Fall-, Bank- und Bescheiddaten nur für einen zuständigen Bearbeiter.
- **Ist:** Dieselbe Sitzung liest und ändert jede ID, ordnet Auszahlungen an und erteilt Bescheide. Admin-Pflicht gilt für Diagnose, Kassenlauf und Storno. Die Detail-Einsicht wird aufgezeichnet und zugelassen. Listen unter `/api/bedarfsgemeinschaften` und `/api/journal` liefern Namen und Aktenzeichen.
- **Auswirkung:** Sozial- und Bankdaten aller Vorgänge der Datenbank sind für jedes angemeldete Konto erreichbar. Reichweite: der gesamte Datenbestand dieser Instanz.
- **Evidenz:** Die Routen verlangen eine Sitzung und für die genannten Sonderpfade die Admin-Rolle. Eine Zuordnung Konto zu Bedarfsgemeinschaft gibt es im Modell nicht.
- **Gegenprüfung:** `LmgServerHttpTest` prüft 401/403 auf Admin-Routen. Ein zweites Konto gegen einen fremden Vorgang ist dort kein Prüffall. Vier-Augen verhindert die Selbstfreigabe und die Objektfreiheit nicht.
- **Reparatur:** Vor vollständigen Falldaten eine Zuständigkeits- oder Teamprüfung. Listen auf denselben Umfang begrenzen.
- **Regression:** Zweites Sachbearbeiterkonto, `GET` und `POST` auf eine fremde ID, Assertion HTTP 403 und unveränderte Zahlungs- und Bescheidzeilen.

### FB-20261015-05 — Offene Sagas werden über den aktuellen Schrittindex kompensiert

- **Schweregrad:** MAJOR
- **Belegsicherheit:** STATISCH BELEGT
- **Komponente:** `SagaEngine.java:316-329`, `AntragsSagaProzess.java:75-81`, `ddl/oracle_schema.sql` (`SAGA_TYPE` ohne Definitionsversion). Teilbericht KR-20261015-02.
- **Verletzte Regel:** Die Kompensation muss die persistierte Wirkung derselben Saga treffen.
- **Auslöser:** Eine Saga dieses Typs steht auf RUNNING, FAILED oder COMPENSATING. Vor dem nächsten Start wird ein Schritt eingefügt, entfernt oder umsortiert, der Typname bleibt. `requireSuccessfulRecovery` kompensiert unfertige Sagas beim Start.
- **Soll:** Kompensation über den persistierten Schrittnamen oder eine gespeicherte Definitionsversion.
- **Ist:** `TX_SAGA_STEP` erhält den Namen erst in `stepDone`. Die Anlage speichert Index und PENDING. Der Wiederanlauf nimmt `definition.steps().get(i)` der aktuell geladenen Klasse.
- **Auswirkung:** Beim Deploy mit offenen Sagas läuft die falsche Kompensation, oder ein bekannter Index jenseits der neuen Länge bleibt FAILED. Eine frische Installation ohne Alt-Sagas trifft das nicht. Die heutige Definition hat vier feste Schritte; der Fehler wird erst durch eine geänderte Schrittliste erreichbar.
- **Evidenz:** Schleife `for (int i = steps.size() - 1; …)` und `SagaJournal`-Insert ohne Name im PENDING-Zustand.
- **Gegenprüfung:** Der Journal-Marker der Antrags-Saga verhindert die Kompensation fremder Bedarfsgemeinschaften und eine verschobene Schrittliste nicht. Ein Upgrade-Skript, das offene Sagas dieses Typs vor dem Tausch sperrt, liegt nicht bei.
- **Reparatur:** Den Schrittnamen bereits bei der Anlage schreiben und die Kompensation darüber wählen. Bei unbekannter Version den Wiederanlauf für diesen Typ ablehnen, statt Index 0 der neuen Liste auszuführen.
- **Regression:** Journal mit DONE auf Index 0 unter dem alten Namen, neue Definition mit einem vorgeschalteten Schritt. Assertion: es kompensiert der alte Name, nicht die neue Funktion an Index 0.

### FB-20261015-06 — Kassenlauf schreibt die gesperrte Menge in eine IN-Liste

- **Schweregrad:** MAJOR
- **Belegsicherheit:** STATISCH BELEGT
- **Komponente:** `ZahlungDao.java:95-107`, `SozialLeistungsService.java:516-537`. Teilbericht KR-20261015-03.
- **Verletzte Regel:** Die Übergabe soll die gesperrte Menge fortschreiben. Oracle begrenzt eine IN-Liste auf 1000 Ausdrücke (ORA-01795). Diese Grenze wurde in diesem Review nicht ausgeführt; sie gilt in 19c und in 23ai.
- **Auslöser:** Mehr als 1000 Zahlungen im Status BEREIT. `bereiteZahlungenGesperrt` sperrt ohne Zeilenobergrenze.
- **Soll:** Dieselbe Transaktion übergibt die gesperrte Menge, gegebenenfalls in Blöcken unterhalb der Parsergrenze, mit gleicher Anzahl und Summe.
- **Ist:** `WHERE ID IN (?,?,…)` enthält einen Platzhalter je ID. Die Exception rollt die Transaktion zurück. Alle Zeilen bleiben BEREIT. Jeder erneute Lauf nimmt denselben Weg.
- **Auswirkung:** Ein Rückstand über 1000 bereite Zahlungen lässt sich über diesen Pfad nicht übergeben. Eine Teilübergabe entsteht nicht, weil der Fehler vor dem Commit liegt. Reichweite: ein Kassenlauf ohne Aktenzeichenpräfix über den gesamten Bestand.
- **Evidenz:** Aufbau der Platzhalterliste und ein einzelnes `executeUpdate`.
- **Gegenprüfung:** Die Sperre `ORDER BY z.ID` hält die Menge stabil. Sie macht die Anweisung für mehr als 1000 IDs nicht ausführbar. Tests mit wenigen Zeilen bleiben grün.
- **Reparatur:** Die ID-Liste in Blöcke von höchstens 1000 aufteilen und in derselben Transaktion ausführen. `anzahl` mit der gesperrten Menge vergleichen.
- **Regression:** 1001 gesperrte BEREIT-Zeilen, ein Kassenlauf. Assertion: alle 1001 sind UEBERGEBEN, Summe und Anzahl stimmen, ein zweiter Lauf übergibt 0.

### FB-20261015-07 — Vorgangsmaske rechnet den JVM-Monat

- **Schweregrad:** MAJOR
- **Belegsicherheit:** STATISCH BELEGT
- **Komponente:** `SozialLeistungsService.java:759-764`. Buchung, Saga und Bescheidplan verwenden den Auftragsmonat. Teilbericht Fach, dort FB-20261015-03.
- **Verletzte Regel:** Dieselbe Konstellation und derselbe Bezugsmonat müssen in Vorschau und Zahlung übereinstimmen.
- **Auslöser:** Die Maske öffnet einen Vorgang, dessen maßgebender Monat nicht `YearMonth.now()` ist. Beispiel Entbindung 2026-06: ab Juli entfallen 95,71 EUR Schwangerschaftsmehrbedarf. Die Liste zeigt den JVM-Monat.
- **Soll:** Die angezeigte Vorschau nennt den Monat, für den sie gilt, und die Anordnung desselben Monats denselben Zahlbetrag.
- **Ist:** `berechnungDefensiv` setzt `monat = YearMonth.now()`. Die Auszahlung rechnet `auftrag.zahlungsmonat()`.
- **Auswirkung:** Die Sachbearbeitung sieht einen anderen Betrag als den, den eine Anordnung für einen anderen Monat bucht. Die Buchung selbst verwendet Satzstand und Merkmale des Auftragsmonats. Reichweite: jede Anzeige außerhalb des aktuellen JVM-Monats, sobald Schwangerschaft, Satzstand oder Vermögensbestand monatsabhängig sind.
- **Evidenz:** Die eine Zuweisung `YearMonth.now()` gegenüber `Bewilligungsplan.faelleFuerMonat` im Zahlpfad.
- **Gegenprüfung:** Die Zahlung übernimmt den Maskenbetrag nicht als Eingabe. Sie verhindert die abweichende Anzeige nicht.
- **Reparatur:** Die Vorschau an den gewählten Bezugsmonat binden und den Monat in der Antwort nennen.
- **Regression:** Feste Uhr auf 2026-07, Entbindung 2026-06, Vorschau Juli 563,00 EUR, Anordnung Juni 658,71 EUR. Dieselbe Konstellation mit Bezugsmonat Juni in der Vorschau ergibt 658,71 EUR.

### FB-20261015-08 — Erteilter Bescheid kann in der Sachbearbeitung nicht ersetzt werden

- **Schweregrad:** MAJOR
- **Belegsicherheit:** STATISCH BELEGT
- **Komponente:** `BescheidPaymentBinding.java:19-36` und `:95-100`. Aufhebung eines Bescheids nur in der Saga-Kompensation (`AntragsSagaProzess`). Teilbericht Fach, dort FB-20261015-02. Offener Punkt § 48 SGB X aus dem Projektstand.
- **Verletzte Regel:** [§ 48 SGB X](https://www.gesetze-im-internet.de/sgb_10/__48.html): eine wesentliche Änderung wird durch einen neuen Verwaltungsakt umgesetzt. Bis dahin gilt der erteilte Bescheid. Der Zahlpfad hält den erteilten Betrag fest und lehnt zugleich einen überschneidenden neuen Bescheid ab.
- **Auslöser:** Eine Bedarfsgemeinschaft hat einen Bescheid mit Status ERTEILT. Einkommen, Miete oder Satzstand ändern sich so, dass die aktuelle Rechnung vom gespeicherten Monatsbetrag abweicht.
- **Soll:** Der alte Bescheid bleibt zahlbar, bis ein Änderungs- oder Aufhebungsbescheid den Zeitraum neu festsetzt. Dieser Verwaltungsakt ist in der Sachbearbeitung erreichbar.
- **Ist:** `verify` lehnt die abweichende Zahlung ab. `rejectOverlap` lehnt einen zweiten erteilten Bescheid für denselben Zeitraum ab. Eine Aufhebung in der Maske gibt es nicht.
- **Auswirkung:** Der Geldfluss dieser Monate stoppt. Der gespeicherte Bescheid wird nicht still neu berechnet. Reichweite: jede Bedarfsgemeinschaft mit Bescheid und später geänderten Berechnungsgrundlagen.
- **Evidenz:** Die beiden Ablehnungen und das Fehlen eines Aufhebungsendpunkts neben der Saga-Kompensation.
- **Gegenprüfung:** Die Bindung an den Snapshot verhindert eine stille Neuauszahlung und ist insoweit richtig. Sie wird zum Stillstand, weil der vorgesehene Korrekturbeschluss nicht angelegt werden kann.
- **Reparatur:** Einen Aufhebungs- oder Änderungsbescheid vorsehen, der den alten Status unter Sperre auf AUFGEHOBEN setzt und danach einen neuen Zeitraum erlaubt. Bis dahin die Zahlung des gespeicherten Monatsbetrags nicht zusätzlich an die heutige Neuberechnung koppeln, wenn der Bescheid weiter gelten soll.
- **Regression:** Erteilter Bescheid 563,00 EUR, danach höheres Einkommen. Assertion: Zahlung des alten Betrags bleibt möglich oder ein Änderungsbescheid ersetzt ihn; ein stiller neuer Zahlbetrag ohne neuen Bescheid wird abgelehnt.

### FB-20261015-09 — Alleinerziehendenmehrbedarf je SozialFall ohne Abgleich der Kinder

- **Schweregrad:** MAJOR
- **Belegsicherheit:** STATISCH BELEGT
- **Komponente:** `Leistungsberechnung.java:231-244`, aufgerufen je SozialFall. Teilbericht Fach, dort FB-20261015-05.
- **Verletzte Regel:** [§ 30 Abs. 3 SGB XII](https://www.gesetze-im-internet.de/sgb_12/__30.html) setzt voraus, dass diese Person allein für Pflege und Erziehung sorgt. Dieselben Kinder begründen den Mehrbedarf einmal.
- **Auslöser:** Zwei Mitglieder derselben Bedarfsgemeinschaft tragen dieselben Kinderzähler. Die Zähler werden nicht mit den Mitgliedern und nicht mit einem einzelnen Elternteil abgeglichen. `Math.max` hebt einen kleineren äußeren Zähler auf den inneren an; widersprüchliche Zähler werden nicht abgelehnt.
- **Soll:** Höchstens ein Elternteil erhält den Mehrbedarf, und die Zähler passen zu den erfassten minderjährigen Mitgliedern.
- **Ist:** Jeder SozialFall mit gesetzten Zählern erhält den Betrag, bis zur Obergrenze 337,80 EUR je Person.
- **Auswirkung:** Eine zweite Erfassung derselben Kinder erhöht den Bewilligungsbetrag um bis zu 337,80 EUR je Monat. Eine fachlich richtige Erfassung an genau einem Elternteil bleibt unverändert. Reichweite: Bedarfsgemeinschaften mit doppelter Zählereingabe.
- **Evidenz:** Die Funktion liest nur die Zähler des übergebenen Falls. Ein Haushaltsabgleich der Zähler liegt im gelesenen Pfad nicht.
- **Gegenprüfung:** Die Unit-Tests setzen einen Fall mit stimmigen Zählern. Sie lehnen den zweiten Erwachsenen mit denselben Zählern nicht ab.
- **Reparatur:** Die Kinderzähler gegen die Mitglieder prüfen und den Mehrbedarf höchstens einem SozialFall der Bedarfsgemeinschaft zuordnen. Widerspruch zwischen „unter 7“, „unter 16“ und „minderjährig“ ablehnen.
- **Regression:** Zwei Erwachsene, vier Kinder, Zähler nur beim ersten Elternteil: ein Mehrbedarf. Dieselben Zähler bei beiden: fachliche Ablehnung oder genau ein Mehrbedarf.

### FB-20261015-10 — Fehlerhaftes Kontensegment steht in der Ausnahme

- **Schweregrad:** MAJOR
- **Belegsicherheit:** STATISCH BELEGT
- **Komponente:** `Benutzerverwaltung.java:85-86`. Die Startzeile bei Demo-Konten nennt die Standardkennungen in `LmgServerMain.java:121-125`. Teilbericht WB-20261015-02. Die konkreten Geheimnisse werden hier nicht wiederholt.
- **Verletzte Regel:** Zugangsgeheimnisse gehören nicht in Ausnahme- oder Starttexte, die Logger und Dienstwrapper übernehmen.
- **Auslöser:** `LMG_BENUTZER` enthält ein Segment, das nicht aus genau drei nicht leeren Feldern besteht. Der Konstruktor wirft `IllegalArgumentException` und setzt das gesamte Segment in die Meldung.
- **Soll:** Die Meldung nennt das erwartete Format und nicht den übergebenen Wert.
- **Ist:** Die Meldung lautet sinngemäß, das Format sei falsch, und hängt das empfangene Segment an. Im Demo-Betrieb auf Loopback nennt die Startzeile zusätzlich die mitgelieferten Konten.
- **Auswirkung:** Prozesslogger, Konsolenmitschnitt und ein Dienstwrapper erhalten das Klartextsegment, sobald die Konfiguration formal ungültig ist. Die Loopback-Sperre verhindert den Demo-Betrieb an einer Netzwerkschnittstelle und das Mitschneiden der lokalen Startzeile nicht. Reichweite: jeder Start mit fehlerhafter Kontendefinition; die Demo-Zeile nur bei den mitgelieferten Konten.
- **Evidenz:** Die Stringverkettung im `throw`.
- **Gegenprüfung:** Tests prüfen den Abbruch bei Nicht-Loopback und die erfolgreiche Anmeldung. Sie prüfen nicht, ob die Ausnahmemeldung frei von dem übergebenen Segment bleibt.
- **Reparatur:** Die Ausnahme auf das Format beschränken. Die Demo-Startzeile auf die Anzahl der Konten begrenzen.
- **Regression:** Ungültiges Segment mit einem erkennbaren Marker. Assertion: die Exception-Message enthält den Marker nicht. Demo-Startausgabe enthält kein Passwortfeld.

### FB-20261015-11 — Verlorene Bestätigung des Write-ahead

- **Schweregrad:** MINOR
- **Belegsicherheit:** STATISCH BELEGT für den Kontrollfluss. Der mehrdeutige Ausgang von `commit()` ist nicht ausgeführt.
- **Komponente:** `SagaJournal.java:465-481`, `SagaEngine.java:89-91` und `:141-148`. Teilbericht KR-20261015-04.
- **Verletzte Regel:** `IN_FLIGHT` bedeutet für den Wiederanlauf „möglicherweise ausgeführt“. Ein terminaler Kopf darf diesen Schritt nicht der weiteren Klärung entziehen, solange die Write-ahead-Transaktion auf dem Server durchgegangen sein kann.
- **Auslöser:** `commit()` des Write-ahead ist auf dem Server wirksam, der Client erhält eine `SQLException`. `imFlug` wird erst nach der Rückkehr gesetzt. Die Fachaktion dieses Index hat nicht begonnen. Die Laufzeitkompensation zählt den Index nicht mit, schließt den Kopf mit COMPENSATED und löscht den Payload.
- **Soll:** Der Schritt bleibt einem späteren Wiederanlauf zugänglich, oder der Kopf bleibt FAILED, solange ein IN_FLIGHT ohne bestätigte Fachaktion offen ist.
- **Ist:** Recovery liest COMPENSATED nicht erneut. Eine zweite Fachwirkung entsteht nicht, weil die Aktion nicht gestartet wurde.
- **Auswirkung:** Das Journal behält einen IN_FLIGHT-Schritt unter einem abgeschlossenen, payloadlosen Kopf. Die Diagnose dieses Schritts endet. Reichweite: verlorene Commit-Quittung genau dieser Write-ahead-Transaktion.
- **Evidenz:** `txReturn` ruft `rollback()` auch nach einem Commit, dessen Rückkehr fehlschlägt; der Rollback ist dann leer. `schrittCommittet` prüft `isStepDone` nur, wenn `imFlug` schon gesetzt ist.
- **Gegenprüfung:** Die Klärung über `isStepDone` gilt für den Fachschritt, nicht für das Write-ahead. `RetryPolicy` wiederholt diesen Journalfehler nicht.
- **Reparatur:** Nach einer Exception aus `markStepInFlight` den Kopf nicht als vollständig kompensiert behandeln, bevor der Schrittstatus neu gelesen wurde.
- **Regression:** Write-ahead-Commit wirft nach dem erfolgreichen Server-Commit. Assertion: der Kopf ist nicht COMPENSATED mit gelöschtem Payload, solange der Schritt IN_FLIGHT ist, und die Fachaktion lief nicht.

### FB-20261015-12 — Der Übersichtsscan liest den Saga-Payload

- **Schweregrad:** MINOR
- **Belegsicherheit:** STATISCH BELEGT
- **Komponente:** `SagaJournal.java:199-213` und `:226`, Kommentar `TxSagaJournal.java:73-74`. Teilbericht KR-20261015-05.
- **Verletzte Regel:** Der Schnittstellenkommentar sagt, der Payload werde in `unfinished(Duration)` nicht geladen.
- **Auslöser:** Startup- oder Admin-Wiederanlauf ruft den ungesperrten Überblick auf.
- **Soll:** Der Scan liefert Identität, Typ, Zustand und Schrittstatus. Den Payload liest erst der exklusive Claim.
- **Ist:** Das SELECT enthält `s.PAYLOAD`, und `leseUnfinished` legt ihn in `UnfinishedSaga` ab.
- **Auswirkung:** Sozialdaten unfertiger Sagas stehen für den gesamten Scan im Speicher, auch für übersprungene Typen. Die Löschung bei COMPLETED und COMPENSATED bleibt im Claim-Update erhalten. Reichweite: jede unfertige Saga zum Scanzeitpunkt.
- **Evidenz:** Die SELECT-Liste und `rs.getString(3)`.
- **Gegenprüfung:** `claimUnfinished` liest den Payload danach erneut unter Sperre. Das macht die vorherige Kopie nicht überflüssig.
- **Reparatur:** `PAYLOAD` aus diesem SELECT entfernen und im Überblick leer lassen.
- **Regression:** Eine unfertige Saga mit gesetztem Payload. Assertion: die Liste aus `unfinished` trägt keinen Payload, der anschließende Claim schon.

### FB-20261015-13 — Leere Bankfelder gelten bei der Wiederholung als unverändert

- **Schweregrad:** MINOR
- **Belegsicherheit:** STATISCH BELEGT
- **Komponente:** `SozialLeistungsService.java:711-727`. Teilbericht Fach, dort FB-20261015-08.
- **Verletzte Regel:** Die Wiederholung ist an Bedarfsgemeinschaft, Monat, Vorgangsnummer und die ausdrücklich übergebenen Bankdaten gebunden.
- **Auslöser:** Dieselbe Vorgangsnummer wird ohne IBAN, BIC und Namen wiederholt. Die gespeicherte Zahlung trägt eine Bankverbindung.
- **Soll:** Fehlende Bankdaten werden als „nicht abweichend“ nur dann behandelt, wenn der Aufrufer das ausdrücklich als Wiederholung ohne Bankvergleich kennzeichnet. Ein Aufruf, der Bankdaten setzen wollte und sie verliert, wird abgelehnt oder als unvollständig angezeigt.
- **Ist:** Leere Felder überspringen den Vergleich. Eine zweite Zahlung entsteht nicht. Die vorhandene Zeile wird zurückgegeben.
- **Auswirkung:** Eine verlorene Antwort ohne Bankdaten wird als dieselbe Buchung akzeptiert, auch wenn der Aufrufer eine andere Verbindung senden wollte und die Felder unterwegs leer ankamen. Reichweite: Wiederholungen, die Bankfelder auslassen.
- **Evidenz:** Die drei Bedingungen prüfen nur nicht leere Auftragsfelder.
- **Gegenprüfung:** Abweichende, tatsächlich gesendete Bankdaten werden abgelehnt. Der Unique-Index bleibt wirksam.
- **Reparatur:** Eine Wiederholung ohne Bankdaten nur akzeptieren, wenn der ursprüngliche Auftrag dieselben leeren Felder hatte, oder die Wiederholung als solche in der Antwort kennzeichnen.
- **Regression:** Gespeicherte IBAN A, Wiederholung derselben Vorgangsnummer mit leerer IBAN. Assertion: keine zweite Zeile; die Antwort sagt, dass der Bankvergleich ausgelassen wurde, oder die Wiederholung wird abgelehnt.

### FB-20261015-14 — Storno im Service ohne Rollenprüfung

- **Schweregrad:** MINOR
- **Belegsicherheit:** STATISCH BELEGT
- **Komponente:** `SozialLeistungsService.java:542-568`. Der HTTP-Handler prüft `istAdmin()` (`LmgServer`, Storno-Zweig). Teilbericht Fach, dort FB-20261015-09.
- **Verletzte Regel:** Storno ist eine Admin-Aktion. Die Regel ist am HTTP-Rand umgesetzt und in der Servicemethode nicht wiederholt.
- **Auslöser:** Ein anderer Aufrufer der Servicemethode übergibt eine Sachbearbeiterkennung ohne Admin-Rolle. Die Methode sperrt die Bedarfsgemeinschaft und bucht Budget sowie den dokumentierten Vermögenseinsatz auch bei einem historisierten Vorgang zurück.
- **Soll:** Dieselbe Rollenprüfung wie am HTTP-Rand, bevor Budget und Vermögen verändert werden.
- **Ist:** Die Methode prüft Status und Sperren und nicht die Rolle. Über den Webpfad bleibt der Storno auf Admins beschränkt.
- **Auswirkung:** Ein direkter Service-Aufruf außerhalb des Routers storniert ohne Admin-Rolle. Der heutige HTTP-Pfad ist geschlossen. Reichweite: Batch, Test und künftige Aufrufer der öffentlichen Servicemethode.
- **Evidenz:** Kein Rollenparameter in `storniereZahlung(long, String)`.
- **Gegenprüfung:** `brauchtAdmin` und der Handler lehnen Nicht-Admins ab. Das schützt die Methode selbst nicht.
- **Reparatur:** Die Rolle an der Servicemethode prüfen oder die Methode für externe Aufrufer unzugänglich machen.
- **Regression:** Servicestorno mit Sachbearbeiterkennung ohne Admin-Rolle wirft einen Berechtigungsfehler, Budget und Vermögen bleiben unverändert.

### FB-20261015-15 — Snapshot-JSON stellt die Eingabemerkmale nicht wieder her

- **Schweregrad:** MINOR
- **Belegsicherheit:** STATISCH BELEGT
- **Komponente:** `BerechnungsSnapshot.java:203-207`. Teilbericht Fach, dort FB-20261015-10.
- **Verletzte Regel:** Der Nachdruck gibt den erteilten Bescheid wieder. Eine Rekonstruktion aus dem strukturierten JSON braucht die Merkmale, die den Betrag erklären.
- **Auslöser:** Lesen eines Snapshot v3 über `BerechnungsSnapshot.lese`.
- **Soll:** Merkzeichen, Schwangerschaft, Kinderzähler, Warmwasser und Entbindungsmonat bleiben im strukturierten Stand erhalten. Der Nachdruck verwendet das gespeicherte HTML.
- **Ist:** Der Nachdruck gibt das gespeicherte HTML unverändert zurück. Das strukturierte JSON setzt die genannten Merkmale beim Lesen auf leer und übernimmt Stufe, Rente und Erwerb.
- **Auswirkung:** Eine Prüfung allein aus dem JSON rekonstruiert die Mehrbedarfsvoraussetzungen nicht. Der ausgegebene Bescheidtext bleibt der gespeicherte. Version 2 kennzeichnet den Altstand. Reichweite: jede Auswertung, die v3-JSON statt HTML liest.
- **Evidenz:** Die Lesefunktion füllt die genannten Felder nicht aus dem gespeicherten Objekt.
- **Gegenprüfung:** Der HTML-Pfad tauscht den Bescheid nicht still gegen eine heutige Neuberechnung aus.
- **Reparatur:** Dieselben Felder im JSON lesen und schreiben, die der Plan für den Monat verwendet hat.
- **Regression:** Snapshot mit Entbindungsmonat und Merkzeichen G. Assertion: `lese` liefert beide Werte, der Nachdruck-HTML-String ist bytegleich zum gespeicherten HTML.

### FB-20261015-16 — Oberfläche ohne Frame-Schutz

- **Schweregrad:** MINOR
- **Belegsicherheit:** STATISCH BELEGT
- **Komponente:** `LmgServer.java` Content-Security-Policy ohne `frame-ancestors`, kein `X-Frame-Options`. Teilbericht WB-20261015-04.
- **Verletzte Regel:** `default-src` deckt `frame-ancestors` nicht ab. Eine Einbettung der angemeldeten Oberfläche soll unterbunden sein.
- **Auslöser:** Eine Seite auf demselben Rechner bindet die Loopback-Oberfläche in einen Frame ein.
- **Soll:** `frame-ancestors 'none'` oder `X-Frame-Options: DENY`.
- **Ist:** Die CSP setzt `default-src 'self'` und lässt die Einbettung durch eine lokale Seite zu. Öffentliche Seiten scheitern an der Private-Network-Access-Schranke aktueller Chromium-Browser, solange der Server auf Loopback bleibt.
- **Auswirkung:** Eine lokale Seite kann die bereits angemeldete Oberfläche einbetten. Reichweite: derselbe Rechner, bestehende Sitzung.
- **Evidenz:** Der gesetzte CSP-Header ohne `frame-ancestors`.
- **Gegenprüfung:** Header-Tests erwarten CSP und nosniff und nicht den Frame-Schutz.
- **Reparatur:** `frame-ancestors 'none'` ergänzen.
- **Regression:** Antwortheader der Oberfläche enthält `frame-ancestors 'none'`.

## 3. Offene Hypothesen, fehlende Nachweise, Modell- und Betriebsgrenzen

### Nicht als Fehler bestätigt

**Alleinerziehenden-Günstigervergleich.** Der isolierte Wortlaut von § 30 Abs. 3 Nr. 2 SGB XII gewährt 12 Prozent je Kind nur, „wenn die Voraussetzungen nach Nummer 1 nicht vorliegen“. Für ein Kind unter 7 und insgesamt vier Minderjährige wären das 36 Prozent = 202,68 EUR, der Code zahlt 48 Prozent = 270,24 EUR; bei fünf Minderjährigen mit drei Kindern unter 16 sind es 202,68 EUR gegen 337,80 EUR. [WD 6-046/24, S. 8–11](https://www.bundestag.de/resource/blob/1019234/WD-6-046-24-pdf.pdf), abgeschlossen am 19.08.2024, überträgt den Günstigervergleich von § 21 Abs. 3 SGB II ausdrücklich auf § 30 Abs. 3 SGB XII und rechnet das Sechs-Kinder-Beispiel mit 337,80 EUR. Eine bindende Gerichtsentscheidung zu genau dieser SGB-XII-Konstellation wurde hier nicht verifiziert. Der Vorbefund GR-20261015-02 verlangte diesen Vergleich; der Code setzt ihn um. Daraus folgt kein neuer bestätigter Implementierungsfehler. Eine Behörde, die den Ausschließlichkeitssatz wörtlich anwendet, käme zu niedrigeren Bescheiden.

**Vermögen, das einen Monat deckt.** Der frühere Eintrag ist als FB-20261015-03 bestätigt. Ein Verbrauch über mehrere Monate hinweg, wenn der Überschuss mehrere Monatsbedarfe deckt und nur ein Teil je Monat eingesetzt werden soll, ist darüber hinaus nicht implementiert. Die Einmalbuchung des gesamten Überschusses beim ersten positiven Zahlbetrag bleibt die vorhandene Modellregel für den knappen Fall.

**Haushalt ohne Regelbedarfsstufe 1–3.** `schonvermoegen` setzt dann pauschal 10.000 EUR, auch wenn mehrere Personen der Stufen 4–6 vorhanden sind. Ob jede minderjährige Person nach § 1 BarbetragsVO als alleinstehend 10.000 EUR oder als überwiegend unterhalten 500 EUR erhält, hängt von Tatsachen ab, die das Datenmodell nicht erfasst. Die Pauschale ist eine Modellgrenze, kein in diesem Lauf dynamisch widerlegter Einzelfall.

**Merkzeichen G.** § 30 Abs. 1 verlangt Altersgrenze oder volle Erwerbsminderung und einen Nachweis. Der Code rechnet 17 Prozent, sobald das Flag gesetzt ist. Die Sachbearbeitung trägt die Voraussetzung.

**Weitere nicht modellierte Normen,** in `rechtsgrundlagen.md` bereits genannt: Karenzzeit und Absenkung nach § 35, Ernährungsmehrbedarf, § 82 Abs. 4–6, abweichender Einzelfallbedarf, Antrag und Rückwirkung über den erfassten Zeitraum hinaus.

### Nicht dynamisch geprüft

| Gegenstand | Status | Grund |
|---|---|---|
| Sieben Simulator-Kombinationen (Fach POSITIV/NEGATIV/LAST/STRESS, Web POSITIV/NEGATIV/LAST) | NICHT DYNAMISCH GEPRÜFT | Gemeinsame Developer-DB ohne Exklusivität. Ein Lauf mit Aufräumen würde fremde `SWEET-*`-Daten löschen. Die Simulator-Unit- und Oracle-Tests in der 249er-Suite sind kein Lastnachweis. |
| GLM-/API-Stichprobe | NICHT DYNAMISCH GEPRÜFT | Kein API-Aufruf in diesem Review. Historische LLM-JSON-Dateien sind kein Nachweis für diesen Lauf. |
| Oracle 19c Enterprise Edition | NICHT GEPRÜFT | Nur 26ai Free 23.26.3.0.0 im Container `lmg-oracle`. |
| Laufzeit Java 21 | NICHT GEPRÜFT | Build `--release 21`, Ausführung Temurin 25.0.4.1. |
| JDBC-Abbruch durch Thread-Interrupt | NICHT DYNAMISCH GEPRÜFT | Statisch: der Wachhund unterbricht den Task-Thread. Ob ojdbc 23.5 dadurch jede laufende Anweisung abbricht, wurde nicht mit einer blockierten Anweisung gemessen. |
| Crash genau zwischen Commit und Client-Bestätigung | NICHT DYNAMISCH GEPRÜFT | Statisch kompensiert die Saga einen als DONE sichtbaren Schritt. Ein Verbindungsabriss nach erfolgreichem Server-Commit wurde nicht injiziert; die gemeinsame DB ist dafür ungeeignet. |
| Web-STRESS | NICHT ANWENDBAR | Im Worker und in `SweetMain` ausgeschlossen. |

### Betriebsgrenzen

Die fehlende Objektberechtigung ist FB-20261015-04. Der Admin-Kassenlauf ohne Aktenzeichenpräfix übergibt alle gesperrten `BEREIT`-Zahlungen, auch solche anderer Testläufe in derselben Datenbank. Der testsweet-Pfad kann ein Präfix setzen; die Produktroute `/api/kassenlauf` tut das nicht. Ab mehr als 1000 Zeilen scheitert derselbe Lauf an FB-20261015-06.

TLS ist Voraussetzung, sobald `lmg.host` nicht Loopback ist und konfigurierte Konten verwendet werden. Der Server ist ein `HttpServer`. Der Default bleibt Loopback; Demo-Konten sind außerhalb davon verweigert. Das ist eine Betriebsvoraussetzung und hier kein eigener Befund des Loopback-Betriebs.

Der Simulator auf Loopback nimmt lokale Aufrufe ohne Anmeldung und ohne `Origin` an. Eine fremde Browser-Origin scheitert an Host und Origin. Das ist der vorgesehene lokale Werkzeugpfad. Das Einmal-Drucktoken steht bis zum Abruf in der URL, ist 60 Sekunden gültig, an die Bedarfsgemeinschaft gebunden und beim zweiten Abruf verbraucht. Ein lokaler SHA-256 vergleicht Marker und Dateibaum; er belegt keine Urheberschaft durch einen Dritten.

## 4. Abdeckungsmatrix

Legende: G = gelesen und am konkreten Pfad nachvollzogen, T = nur über grüne Tests dieses Laufs, N = nicht geprüft, D = in diesem Lauf ausgeführt.

| Feld | Status | Evidenz |
|---|---|---|
| § 28 / RBSFV 2026 Beträge 563/506/451/471/390/357 | G, amtlich | [Anlage zu § 28](https://www.gesetze-im-internet.de/sgb_12/anlage.html), [§ 2 RBSFV 2026](https://www.gesetze-im-internet.de/rbsfv_2026/__2.html); Seed `lmg_seed.sql` |
| § 30 Abs. 1 Merkzeichen G | G als Flag, Voraussetzung nicht erzwungen | `Leistungsberechnung.java:178-180` |
| § 30 Abs. 2 Schwangerschaft, Entbindungsmonat, Folgemonat | G | `Bewilligungsplan.faelleFuerMonat`: Mehrbedarf entfällt, wenn der Bezugsmonat nach dem Entbindungsmonat liegt |
| § 30 Abs. 3 Alleinerziehend | G, Auslegung offen gegen den Ausschließlichkeitssatz | siehe Abschnitt 3; Code `:231-244` |
| § 30 Abs. 6 / Abs. 7 | G, normgleich | Deckel nur Abs. 1–5; Stufenprozente 2,3 / 2,3 / 0 / 1,4 / 1,2 / 0,8 |
| § 35 Abs. 5 zentrale Warmwasserkosten | G | Abzug fest 0,00 EUR; Altparameter nur validiert |
| § 35 Karenz und Absenkung, § 42a Mischhaushalt im Detail | N über die dokumentierte Vereinfachung hinaus | KdU-Summe `min(Summe, Grenze)` ist für nichtnegative Beträge reihenfolgeunabhängig |
| § 82 Abs. 2 und 3 | G für die Pauschale 30 Prozent, Deckel 50 Prozent der Stufe 1 | [§ 82](https://www.gesetze-im-internet.de/sgb_12/__82.html); Abs. 4–6 nicht modelliert |
| § 90 / BarbetragsVO § 1 | G für 10.000 / 500 über RBS-Proxy | [§ 1 VO](https://www.gesetze-im-internet.de/bshg_88abs2dv_1988/__1.html); Sonderfall ohne Stufe 1–3 siehe Abschnitt 3 |
| § 44 Zeitraum 1–12 Monate | G | `Bewilligungsplan` und Service lehnen mehr als zwölf Monate ab |
| Rechtsbehelfsbelehrung | G am Generatortext | Widerspruch: § 84 Abs. 1 SGG, § 36a Abs. 2/2a SGB I, § 9a Abs. 5 OZG. Klage: § 65a SGG. Absatzfassung von SGB I/OZG/SGG in diesem Lauf nicht erneut online geöffnet |
| Vorschau, Service, Saga, Snapshot | T/G | Dieselbe `Leistungsberechnung`; Zahlung gegen Monatsplan in `BescheidPaymentBinding.verify`. Kein eigener End-to-End-Vergleich einer Konstellation über alle vier Eingänge in diesem Lauf |
| Idempotenz, Monatssperre, Storno, Vier-Augen | G + D über Suite | `SozialLeistungsService.ordneAuszahlungAn`, `wiederholeAuszahlung`, `freigebenZahlung`, `storniereZahlung`; Unique-Index `UQ_ZAHLUNG_BG_MONAT_AKTIV`; Suite enthält `ZahlungRennenDbTest`, `VermoegensfortschriebDbTest` |
| Storno-Rolle | G | `brauchtAdmin` und Handler nutzen `istZahlungsAktion`; Handler prüft `istAdmin()` erneut |
| TxManager, Retry, manuelle API | G + D | `RetryPolicy` nur Errorcodes 54, 60, 8177; `TxManagerTest` in der 325er-Suite grün |
| Saga-Claim, PENDING-Recovery, IN_FLIGHT | G, Befunde FB-20261015-05, FB-20261015-11, FB-20261015-12 | `SagaEngine.wiederanlauf`; Crash-Injektion nicht wiederholt. Indexkompensation nur bei geänderter Schrittliste erreichbar |
| Pools, TaskController | G, Befund FB-20261015-02 | Timeouts und Rollback vor AutoCommit in den vorhandenen Tests grün. Der Abbruch nach innerem Commit ist statisch belegt und nicht ausgeführt |
| LmgServer-Schreibpfade Auszahlung, Freigabe, Storno, Bescheid, Fall | G, Befund FB-20261015-04 | Sachbearbeiterkennung aus der Sitzung. Keine Objektzuordnung. Storno-Rolle am HTTP-Rand, nicht in der Servicemethode (FB-20261015-14) |
| SweetServer-Endpunktmatrix vollständig | T/G ausschnittsweise | Teilbericht Web. Loopback ohne Anmeldung als Betriebsgrenze, kein eigener Produktbefund. Zertifiktskette im Teilbericht ohne bestätigten falschen PASS |
| testsweet-Verifikator und Generator | T | 249 Tests grün, darunter Generator-, Negativzustands- und Zertifikatszustandstests. Kein neuer 500er-Lauf |
| Zertifikatskette statisch | G ausschnittsweise | Neuester Lauf mit `run-state` ungleich PASS wirft in `LlmReviewService.newestSource`. Worker bricht bei Simulator- oder GLM-FAIL ab. Drei formale Versuche, 30 s, Korrekturhinweis; fachlicher FAIL nach gültiger Antwort wird nicht erneut gesendet |
| GLM-Transport live | N | — |

## 5. Nachprüfung historischer Befunde

Maßstab ist der Bericht `grundsicherung-gesamt-review-2026-10-15-3c909c2.md` und der Fixbericht vom selben Tag. Alte Zeilennummern wurden nicht übernommen.

| ID | Urteil | Aktuelle Evidenz |
|---|---|---|
| GR-20261015-01 Storno ohne Admin | BEHOBEN | `LmgServer.brauchtAdmin` und der Storno-Handler verwenden dieselbe `istZahlungsAktion`-Struktur. Der Handler lehnt Nicht-Admins zusätzlich ab. Produkt-HTTP-Tests der grünen Suites. Eine eigene Plus-/Unicode-ID-Probe wurde in diesem Lauf nicht wiederholt. |
| GR-20261015-02 Alleinerziehend zu niedrig | BEHOBEN im Sinne des Vorbefunds | Code, Java-, Node- und Python-Referenz vergleichen beide Sätze und nehmen den höheren, Deckel 337,80 EUR. Node-Tests erwarten 270,24 / 337,80 / 337,80 und lehnen 202,68 als Referenzfehler ab. Die Primärtextspannung steht in Abschnitt 3, nicht als neuer Defekt. |
| GR-20261015-03 Zentraler Warmwasserabzug | BEHOBEN | Abzug 0,00 EUR. Node-Test: 563 + 400 + 100 = 1.063 EUR bei Altparameter 0,20. |
| GR-20261015-04 Leere PENDING-Saga blockiert Start | BEHOBEN, statisch und über Suite | `alleSchrittePending` schließt unter Claim mit COMPENSATED ab. Kein neuer Prozessabbruch in diesem Lauf. |
| GR-20261015-05 Nachdruck mit aktuellen Bankdaten | BEHOBEN soweit gelesen | Druck verwendet den gespeicherten Snapshot, Fallback nur ohne Snapshot und mit Hinweis im Codekommentar des Service. Kein eigener Bankwechsel-Nachdruck gegen Oracle in diesem Lauf. |
| GR-20261015-06 Elektronische Widerspruchsform | BEHOBEN am Text | Generator trennt Behördenkommunikation und § 65a SGG. Online-Gegenlesung von SGG/SGB I/OZG in diesem Lauf nicht wiederholt. |
| GR-20261015-07 Login-Sperre verdrängt echte Konten | BEHOBEN, statisch | Konfigurierte Konten haben `kontoFehlversuche`; der Phantomdeckel gilt nur für unbekannte Kennungen. |
| GR-20261015-08 Drucktoken nach Logout | BEHOBEN soweit gelesen | `DruckToken` speichert Sitzungstoken und BG-ID. Verbrauch prüft die BG. Kein eigener Logout-HTTP-Ablauf in diesem Lauf; zugehörige Tests der Suite waren grün. |
| GR-20261015-09 Java/Node-Antwortvertrag | BEHOBEN für den getesteten Korpus | `llm-answer-parity.test.js` im 100er-Lauf grün. |
| GR-20261015-10 SQLState 61000 als Retry | BEHOBEN | `RetryPolicy.isRetryable` akzeptiert nur die drei Vendorcodes. |

Widerlegte Spur in diesem Lauf: Der Günstigervergleich ist nicht als gesetzeswidriger Geldfehler bestätigt, weil WD 6-046/24 S. 11 die Übernahme auf § 30 Abs. 3 ausdrücklich vornimmt. Die README-Formulierung „sonst 12 %“ beschreibt die ausschließliche Lesart und ist Teil von FB-20261015-01.

### Gegenprüfung der Teilberichte

| Teilbericht | Urteil der zentralen Prüfung |
|---|---|
| KR-20261015-01 Abbruch nach Fach-Commit | Bestätigt als FB-20261015-02. Doppelzahlung desselben Monats ist durch den Unique-Index ausgeschlossen. |
| KR-20261015-02 Schrittindex | Bestätigt als FB-20261015-05. Erreichbar beim Deploy mit geänderter Schrittliste und offenen Sagas. |
| KR-20261015-03 IN-Liste | Bestätigt als FB-20261015-06. ORA-01795 wurde nicht ausgeführt. |
| KR-20261015-04 Write-ahead | Bestätigt als FB-20261015-11. Keine zweite Fachwirkung. |
| KR-20261015-05 Payload-Scan | Bestätigt als FB-20261015-12. |
| Fach, Günstigervergleich als KRITISCH | Nicht bestätigt. WD 6-046/24 wurde zentral gelesen. Offene Auslegung bleibt Abschnitt 3. Die README-Sätze sind FB-20261015-01. |
| Fach, Änderungsbescheid | Bestätigt als FB-20261015-08. |
| Fach, JVM-Monat der Vorschau | Bestätigt als FB-20261015-07. Die Buchung verwendet den Auftragsmonat. |
| Fach, deckendes Vermögen | Bestätigt als FB-20261015-03. |
| Fach, doppelte Kinderzähler | Bestätigt als FB-20261015-09. Eine einfache, stimmige Erfassung bleibt unverändert. |
| Fach, leere Bankfelder, Service-Storno, Snapshot-JSON | Bestätigt als FB-20261015-13 bis FB-20261015-15. |
| WB-20261015-01 Objektberechtigung | Bestätigt als FB-20261015-04. |
| WB-20261015-02 Geheimnis in der Ausnahme | Bestätigt als FB-20261015-10. |
| WB-20261015-03 kein TLS | Betriebsvoraussetzung außerhalb von Loopback, kein eigener Befund des Default-Betriebs. |
| WB-20261015-04 Einbettung | Bestätigt als FB-20261015-16. |
| WB-20261015-05 Simulator ohne Anmeldung | Loopback-Werkzeug, kein Produktbefund. |
| WB-20261015-06 Drucktoken in der URL | Begrenztes Einmal-Token, als Rest in Abschnitt 3. |
| ZT-20261015-01 lokaler Hash | Beobachtung in Abschnitt 3. Ein falsches BESTANDEN durch gemeinsames Umschreiben wurde nicht als erreichter Zertifikatspfad belegt. |

Die offenen Hypothesen der Teilberichte zu Claim-Verlust zwischen `checkAlive` und `commit`, zu Vendorcodes aus `Connection.commit()` selbst und zu einer Idle-Firewall bleiben offen. Sie wurden nicht ausgeführt. Die gemeinsame Developer-Datenbank ist dafür ungeeignet.

## 6. Nächste Schritte

1. HTTP-Antwort und Wiederholungsaufforderung an den tatsächlichen Commit der inneren Fachtransaktion binden (FB-20261015-02). Danach den deckenden Vermögensfall fortschreiben (FB-20261015-03) und die Objektberechtigung vor vollständigen Falldaten prüfen (FB-20261015-04). Diese drei ändern Geldwirkung oder Datenzugriff.
2. Kassenlauf in Blöcken unter 1000 IDs (FB-20261015-06) und Saga-Kompensation über den persistierten Schrittnamen (FB-20261015-05). Der zweite Punkt wirkt erst beim nächsten Definitionswechsel.
3. Vorschau an den Bezugsmonat binden (FB-20261015-07) und einen Änderungsbescheid ermöglichen, bevor die Zahlung an die Neuberechnung gekoppelt bleibt (FB-20261015-08). Kinderzähler gegen die Mitglieder prüfen (FB-20261015-09). Kontensegmente aus Ausnahmemeldungen entfernen (FB-20261015-10).
4. README-Kurztabelle anpassen (FB-20261015-01) und den Günstigervergleich dort als Auslegung neben den Ausschließlichkeitssatz stellen. Die Betragsdifferenz der engen Lesart bleibt 67,56 EUR bzw. 135,12 EUR je Monat.
5. Einen exklusiven Oracle-Lauf der sieben Simulator-Kombinationen und einen GLM-Lauf nur mit synthetischen Fällen, wenn eine Datenbank ohne fremde `SWEET-*`-Bestände bereitsteht. Dieselbe Suite einmal unter einer Java-21-Laufzeit und, falls die EE-Umgebung verfügbar ist, gegen Oracle 19c. Der jetzige Nachweis gilt für Temurin 25 und 26ai Free.

## 7. Anhang

### Prüfstand

| Angabe | Wert |
|---|---|
| Datum | 2026-10-15. Erste Konsolidierung etwa 12:40 Uhr, Nachtrag nach Gegenprüfung der drei Teilberichte anschließend. Produktquellen unverändert |
| Branch / Commit | `main` / `37abfb6f3da841e440ab21a53fbffa7eadf7cd04` |
| Commit-Zeit | 2026-10-15T12:12:09+02:00, Betreff „Grundsicherung: Review-Funde beheben und testsweet erweitern“ |
| Versionierte Änderungen | keine (`git status --porcelain -uno` leer, vorher und nach den Läufen) |
| Unversioniert, nicht Prüfgegenstand | unter anderem `tmp/` (LibreOffice-Profile, Diagnose) und einzelne Lern-Dokumente |
| Produktversion | 0.5.8, testsweet 0.1.0 |
| Java | OpenJDK Temurin 25.0.4.1+1, Laufzeit und Maven-JVM |
| Compiler | `maven.compiler.release` 21 |
| Maven | 3.9.6 |
| Oracle-Treiber / UCP | 23.5.0.24.07 |
| JUnit | 5.10.2, Surefire 3.2.5 |
| Node | v22.12.0 |
| Oracle | Docker `lmg-oracle`, Image `gvenzl/oracle-free:23-slim-faststart`, Banner „Oracle AI Database 26ai Free Release 23.26.3.0.0“, PDB `FREEPDB1`, Service `freepdb1`, Port 1521. Abfrage als Anwendungsuser, ohne Zugangsdaten im Protokoll |
| Testkonfiguration | `TX_URL=jdbc:oracle:thin:@//localhost:1521/FREEPDB1`, Benutzer der Developer-DB. Passwort nicht protokolliert. `LMG_PORT` und `LMG_HOST` in der Shell nicht gesetzt |
| Quelleninventar | `grundsicherung-gesamt-review-2026-10-15-k4p9-belege/quellen-sha256.txt`, 265 Dateien, Datei-SHA-256 `ACC521BEFB07E831C970F18A99EC00E7265FED2F4B30108E3A543A707862E3D6`. Nachkontrolle: 265 geprüft, 0 abweichend, 0 fehlend |

### Frische Läufe

| Befehl | Ordner | Zeit | Exit | Ergebnis |
|---|---|---|---|---|
| `mvn -B test` mit gesetzter Test-DB | Projektwurzel | 12:34:30–12:35:18 | 0 | 325, Failures 0, Errors 0, Skipped 1 (`LmgConfigTest.laedtWerteAusPropertiesDatei`, weil `TX_URL` gesetzt ist) |
| `mvn -B -Dtest=LmgConfigTest test` ohne `TX_URL` | Projektwurzel | Ende 12:38:29 | 0 | 2, Failures 0, Skipped 0 |
| `mvn -B install -DskipTests` | Projektwurzel | zwischen den beiden Testläufen | 0 | Nur Installation des bereits mit `mvn test` gebauten Stands. Kein Ersatz des Pflichtlaufs |
| `mvn -B -f testsweet/pom.xml test` mit Test-DB | Projektwurzel | Ende 12:37:47, Dauer 1:14 min | 0 | 249, Failures 0, Errors 0, Skipped 0 |
| `node --test llm-reference.test.js llm-answer-parity.test.js certificate-evidence.test.js` | `doc/testzertifikat-werkzeug` | etwa 12:34–12:35 | 0 | 100/100 |
| `node --test src/test/js/llm-ui.test.js` | `testsweet` | danach | 0 | 30/30 |

Protokolle: `grundsicherung-gesamt-review-2026-10-15-k4p9-belege/mvn-test-main.log`, `mvn-install.log`, `mvn-test-testsweet.log`.

Die Datenbanktests dieses Laufs haben ihre eigenen Aktenzeichen in `FREEPDB1` geschrieben und nach den vorhandenen Tests wieder entfernt. Es gab keinen pauschalen Daten-Reset und keinen Abbruch fremder Java- oder Chrome-Prozesse.

### Unabhängige Sollwerte vor dem Codevergleich

Stufe 1 = 563,00 EUR (RBSFV 2026).

| Konstellation | Rechnung | Betrag |
|---|---|---|
| 17 Prozent von 563,00 | 563 × 0,17 | 95,71 EUR |
| 36 Prozent der Stufe 1 | 563 × 0,36 | 202,68 EUR |
| Vier Kinder × 12 Prozent | 563 × 0,48 | 270,24 EUR |
| Fünf × 12 Prozent, Deckel 60 Prozent | min(337,80; 563 × 0,60) | 337,80 EUR |
| Sechs × 12 Prozent | 563 × 0,72, Deckel | 337,80 EUR |
| Zentrale Heizung ohne Abzug | 563 + 400 + 100 | 1.063,00 EUR |
| Erwerbsfreibetrag-Deckel | 563 × 0,50 | 281,50 EUR |

Der Code trifft 270,24 / 337,80 / 337,80 und 1.063,00. Der Ausschließlichkeitswortlaut träfe 202,68 für die ersten drei Zeilen, soweit Nr. 1 erfüllt ist. Diese zweite Spalte ist die offene Auslegung aus Abschnitt 3, nicht das Ist-Verhalten.

### Amtliche Fassungen, abgerufen 2026-10-15

- § 30 SGB XII: https://www.gesetze-im-internet.de/sgb_12/__30.html
- § 35 SGB XII: https://www.gesetze-im-internet.de/sgb_12/__35.html
- § 82 SGB XII: https://www.gesetze-im-internet.de/sgb_12/__82.html
- § 90 SGB XII: https://www.gesetze-im-internet.de/sgb_12/__90.html
- Anlage zu § 28 SGB XII: https://www.gesetze-im-internet.de/sgb_12/anlage.html
- § 2 RBSFV 2026: https://www.gesetze-im-internet.de/rbsfv_2026/__2.html
- § 1 der Verordnung zu § 90 Abs. 2 Nr. 9 SGB XII: https://www.gesetze-im-internet.de/bshg_88abs2dv_1988/__1.html
- WD 6-046/24: https://www.bundestag.de/resource/blob/1019234/WD-6-046-24-pdf.pdf
