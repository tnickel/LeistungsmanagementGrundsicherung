# Zertifikatsabbruch: Saga-Testfixture auf bestehendem Schema

Der Zertifikatslauf `912fe3ab-a9d2-4a0e-af53-250875e2fb40` brach am
15.10.2026 um 13:44 Uhr Europe/Berlin in `BUILD_TESTSWEET` korrekt ab:
288 Tests, keine Assertionfehler, fünf SQL-Ausnahmen. Alle fünf betrafen
`ProduktSagaJournalRegressionDbTest` und `ORA-00904: SAGA_ID`.

## Ursache und Korrektur

Die bestehende Tabelle `TX_DEMO.TX_DEMO_ANTRAG` hat die Spalten ID, NAME,
STATUS, BETRAG und CREATED_AT. Das aktuelle Neuinstallations-DDL enthält
zusätzlich SAGA_ID. Die neuen Tests setzten diese zusätzliche Demo-Spalte
unnötig voraus. Die frühere Prüfung auf dem frisch angelegten Schema
`SWEET_COV_20261015` konnte diesen Unterschied nicht erkennen.

Vier der fünf Journaltests berühren die Demo-Tabelle jetzt überhaupt nicht.
Nur der Test einer zurückgerollten Fachwirkung legt eine Demo-Zeile an;
INSERT, Nachprüfung und Cleanup verwenden deren vorher registrierten,
eindeutigen UUID-Namen als gebundenen Parameter. Journal-Cleanup bleibt auf
exakte Saga-ID und Saga-Typ begrenzt. Es erfolgt kein Schema-Upgrade.

Zusätzlich muss der Namensguard-Test beweisen, dass der INSERT erfolgreich
war, `stepDone` erreicht wurde und die erwartete Ausnahme vom Journalguard
stammt. Eine frühzeitige SQL-Ausnahme kann den Test damit nicht scheinbar
erfüllen.

Drei neue `SagaFixtureSchemaRegressionTest`-Tests prüfen das historische
Demo-Schema ohne SAGA_ID, den Erhalt fremder Demo-Zeilen und das Weiterreichen
anderer JDBC-Fehler. Diese Tests laufen auch ohne Datenbankvariable.

## Ausgeführte Prüfung

Am 15.10.2026, **14:03:45–14:05:12 Uhr Europe/Berlin**, wurde die zuvor
gescheiterte Phase erneut ausgeführt:

```text
mvn -B -f testsweet/pom.xml clean test
Tests run: 291, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Verwendet wurde ausdrücklich das bestehende Schema `TX_DEMO` in
`FREEPDB1`, Docker `lmg-oracle`. Die Demo-Spalte SAGA_ID fehlt weiterhin.
Alle fünf Oracle-Journaltests und die drei neuen Regressionen bestanden.

Die fünf durch den fehlgeschlagenen Cleanup hinterlassenen eigenen
Journalvorgänge wurden anhand ihrer exakten IDs, Typen, Zustände,
Erstellungszeiten und synthetischen Payloads geprüft und entfernt.
Nachkontrolle: keine dieser fünf Kopfzeilen, keine neuen Saga-Fixtures
des Prüflaufs und keine UUID-Demozeilen des Namensguard-Tests übrig.

Quellenfingerabdruck des neuen testsweet-Nachweises:
`ec1f2804ac49249ae61675ef6eb0dd647368fedeb44d4a592a1d01add8a89ebd`.
Kompakter Nachweis: [zertifikat-saga-testkorrektur-2026-10-15-beleg.json](zertifikat-saga-testkorrektur-2026-10-15-beleg.json).
Vollständige lokale Logs liegen unter `tmp/certificate-saga-fix-20261015/`.

Der vollständige Zertifikatslauf mit sieben Simulator-Kombinationen, GLM und
Export wurde nicht erneut gestartet. Der ursprüngliche Zertifikatslauf bleibt
als fehlgeschlagen erhalten. Die Korrektur ist lokal, noch nicht committet.
