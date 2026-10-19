# Gegenprüfung Grok-Bericht: Transaktionskern und Kassenlauf

Prüfdatum: 2026-10-15. Ausgangspunkt: `37abfb6`. Gegenstand sind FB-20261015-02, -05, -06, -11 und -12 aus `grundsicherung-gesamt-review-2026-10-15-k4p9.md`. Die Aussagen des Fremdberichts wurden gegen Aufrufer, Journal, DAO und vorhandene Regressionen gelesen. Ein grüner Ausgangstest ist kein Gegenbeweis für einen bislang ungeprüften Pfad.

## Urteil und Korrektur

| Befund | Gegenprüfung | Korrektur |
|---|---|---|
| 02 Task-Abbruch nach innerem Commit | Bestätigt. `Future.cancel(true)` bestätigt keinen Worker-Rollback. Die eigene äußere Transaktion kann einen früheren inneren Commit nicht zurücknehmen. | `TaskHandle` meldet Abbruch, Warteinterrupt und äußere Transaktionsdeadline als nicht wiederholbare `TaskOutcomeUnknownException`. `TxContext.checkDeadline` prüft zusätzlich das Interrupt-Flag vor dem JDBC-Commit; die manuelle Commit-API verwendet denselben Guard. HTTP/Maskenbehandlung wird in der Web-Gegenprüfung beschrieben. Ein schon bestätigter Commit wird nicht als zurückgerollt bezeichnet. |
| 05 Wiederanlauf nach Definitionswechsel | Bestätigt. Der aktuelle Listenindex identifiziert eine alte Wirkung nicht. | Der Produktjournalpfad speichert alle stabilen Namen bereits atomar mit der PENDING-Liste. Recovery liest die Namen unter dem Claim und kompensiert in der ursprünglichen Indexreihenfolge über den passenden Namen. Eingefügte/umsortierte Schritte verschieben dadurch keine Kompensation. Unbekannte oder fehlende Namen bleiben FAILED mit Nutzdaten; kein Rückfall auf den aktuellen Index. Leere/mehrdeutige Namen werden vor Anlage abgewiesen. Vollständig DONE geschriebene alte Abläufe werden auch nach Einfügen neuer Definitionsschritte nur abgeschlossen. |
| 06 IN-Liste über 1000 IDs | Als Oracle-19c-Portabilitätsfehler bestätigt; die pauschale Aussage für die aktuell eingesetzte Free-Version ist falsch. | `uebergebeGesperrte` zerlegt die gesperrten IDs in maximal 1000 Werte pro Anweisung, auf derselben Verbindung und in derselben Transaktion. Ein Unterschied zwischen aktualisierter und gesperrter Anzahl wirft und rollt alle Blöcke zurück. |
| 11 Write-Ahead ohne Commit-Quittung | Bestätigt als Journal-/Recovery-Fehler, ohne zweite Fachwirkung. | Ein Fehler während des Write-Ahead lässt den Kopf FAILED und behält den Payload. Eine mögliche IN_FLIGHT-Markierung bleibt damit für den nächsten exklusiven Wiederanlauf erreichbar. Die Fachaktion wird vor bestätigtem Write-Ahead nicht begonnen. |
| 12 Payload im ungesperrten Vorscan | Bestätigt. Der bisherige SELECT lud den Payload für jede Schrittzeile erneut; der Kommentar versprach das Gegenteil. | Der Vorscan liest ausschließlich Saga-ID, Typ und Kopfstatus. Payload, Schrittnamen und Schrittstatus werden erst nach dem exklusiven Claim gelesen. |

Oracle nennt für [19c-IN-Listen maximal 1000 Ausdrücke](https://docs.oracle.com/en/database/oracle/oracle-database/19/sqlrf/IN-Condition.html). Die aktuell erreichbare [26ai-Dokumentation nennt 65.535 Ausdrücke für eine einfache Liste](https://docs.oracle.com/en/database/oracle/oracle-database/26/sqlrf/Expression-Lists.html); die 23er-Dokumentadresse wird auf diese Fassung weitergeleitet. Aus dem 19c-Limit folgt daher kein Nachweis eines ORA-01795 bei 1001 IDs auf der vorhandenen 26ai-Free-Datenbank.

## Regressionen und Aussagegrenzen

Neue Haupttests: zwei Interrupt-/Commit-Guards in `TxManagerTest`, zwei Task-Ausgangstests in `TaskOutcomeRegressionTest`, fünf Wiederanlauf-/Write-Ahead-/Namensregressionen in `SagaEngineTest` und zwei Block-/Rollback-Regressionen in `KassenBlockTest`. Die letzte Klasse simuliert die 19c-Parsergrenze gezielt, sie führt keine echte Oracle-19c-Anweisung aus.

Neue testsweet-Oracle-Regressionen: `ProduktSagaJournalRegressionDbTest` prüft den installierten Produktkern mit echten Journalzeilen, den payloadfreien Vorscan, einen Definitionswechsel und eine verlorene Write-Ahead-Quittung. Bei letzterem läuft der echte Oracle-Commit durch, anschließend wirft ein JDBC-Proxy einen synthetischen Quittungsfehler; dies ist keine reale Netzwerkstörung. `KassenMassenlaufDbTest` erstellt 1001 ausschließlich eigene synthetische BEREIT-Zeilen, ruft den begrenzten Service-Kassenlauf auf und prüft 1001 übergebene Zeilen, 10,01 EUR Summe sowie null neue Übergaben beim zweiten Aufruf. Die Fixture-Anlage per SQL ist kein fachlicher Zahlungsanordnungs- oder Lastnachweis.

Die Ausführung und die aktuellen Gesamtzahlen werden von der zentralen Integration nach Abschluss der parallelen Änderungen dokumentiert. Zum Zeitpunkt dieser Teilberichtserstellung wurden keine eigenen Maven-Läufe gestartet, um gemeinsame Buildverzeichnisse nicht parallel zu verändern.

## Upgrade-Grenzen

Keine DDL-Erweiterung: Die bestehende Spalte `TX_SAGA_STEP.NAME` wird früher befüllt. Bereits vorhandene DONE-Schritte tragen im alten Produktpfad ihren Namen. Alte IN_FLIGHT-Schritte können dagegen namenlos sein: Sie bleiben zur kontrollierten Klärung FAILED mit Payload, statt eine mutmaßlich falsche Kompensation auszuführen. Ein Name ist ein stabiler Vertrag; eine inkompatibel geänderte Kompensation braucht einen neuen Namen oder Saga-Typ, und die alte Kompensation muss für offene Journale verfügbar bleiben.

Ein Task-Abbruch kann weiterhin nach einer inneren Fachwirkung eintreten; behoben sind die falsche Rollbackinterpretation und die blinde Wiederholungsaufforderung. Weder ein Interruptguard noch HTTP 409 beweisen rückwirkend einen Rollback eines bereits abgesendeten JDBC-Commits. Ein exklusiver neuer Gesamtzertifikatslauf, Simulator-Vollläufe, Oracle 19c EE und Java-21-Laufzeit werden durch diese Regressionen nicht ersetzt.
