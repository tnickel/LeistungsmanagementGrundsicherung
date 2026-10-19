# DeepSeek r7w2 — Gegenprüfung des Transaktionskerns

Prüfgegenstand: R7W2-01 und das Originalprotokoll
`grundsicherung-gesamt-review-2026-10-15-r7w2-belege/gegenproben/gegenprobe-kern-ProbeA.log`
gegen den aktuellen, noch nicht committeten Arbeitsbaum. Keine Maven-/Oracle-Läufe
in dieser Teilprüfung; ausgeführte Gesamtverifikation ergänzt die zentrale Gegenprüfung.

## Befundentscheidung

**P2/P2b bestätigt:** `TxManager.execute` behandelte eine Exception aus dem
bereits aufgerufenen JDBC-Commit wie einen normalen Transaktionsfehler. Das konnte
nach wirklichem Commit zu `TxException`, HTTP 500 und einem irreführenden
`TX.ROLLBACK`-Ereignis führen. Ein nachfolgender erfolgreicher Rollback betrifft
höchstens eine noch offene Transaktion; er beweist nicht, dass der vorherige
Commit fehlgeschlagen ist. Bei einem als Sperrenkonflikt klassifizierten
Commitfehler war zudem ein automatischer Neuversuch möglich.

**P2c bestätigt:** Die manuelle API ließ den Kontext nach fehlender Commit-Quittung
ACTIVE und setzte ihn durch `rollback` auf ROLLED_BACK. Damit konnte der Status
eine schon persistierte Wirkung falsch als zurückgerollt darstellen.

**P3 als Restfehler des HTTP-Mappings widerlegt:** Bereits das eigene
Originalprotokoll nennt für P3 `TaskOutcomeUnknownException` und
`409 AUSGANG_UNBEKANNT`. Der Bericht vermischt hier P2b mit P3. Der Wachhund kann
einen laufenden Commit weiterhin nicht sicher rückgängig machen; genau deshalb
ist die unbekannte Antwort richtig. `timedOut=1` zählt den Taskabbruch und ist
kein Nachweis eines fachlichen Rollbacks. Ein Deadline-/Interrupt-Guard vor dem
Commit schützt nur den dort noch nicht begonnenen Commit dieser Verbindung;
er nimmt keine vorher committete innere Fachtransaktion zurück.

## Korrektur

- `TxCommitOutcomeUnknownException` ist nicht wiederholbar und erhält die
  ursprüngliche Ursache samt Oracle-Vendorcode. SQLException oder RuntimeException
  während des JDBC-Commits werden ausdrücklich als unbekannter Ausgang behandelt.
  Ein fataler Error bleibt über `TxCommitOutcomeUnknownError` ein Error mit der
  ursprünglichen Ursache, erhält aber ebenfalls keinen Rollbackbeleg. Der Marker
  entsteht ausschließlich beim JDBC-Commit; Errors aus der Facharbeit behalten
  ihr vorheriges Verhalten.
- `TxManager.execute` protokolliert `TX.OUTCOME_UNKNOWN` und startet keinen Retry.
  Best effort Rollback/Poolfreigabe dienen nur dem Aufräumen.
- Die manuelle API verwendet `TxStatus.COMMIT_OUTCOME_UNKNOWN`. Dieser Zustand
  bleibt nach `rollback`, `close` und `finish` bestehen; weitere Facharbeit über
  `ctx.connection()` ist gesperrt.
- Beide Ergebniswege von `TaskHandle` übersetzen den Commitfehler in die bereits
  vorhandene `TaskOutcomeUnknownException`; das gilt auch für den typisierten
  fatalen Commit-Error. Dadurch erreicht der vorhandene
  Produkt-HTTP-Pfad 409 mit `AUSGANG_UNBEKANNT`, Statusnachlesen und bestehender
  Vorgangsnummer. `LmgServer` und normative Fachdokumente wurden nicht verändert.
- Ein normal bestätigter JDBC-Commit bleibt bestätigt, auch wenn danach das
  Interrupt-Flag gesetzt ist. Es gibt keinen nachträglichen Deadline-Rollback.

## Neue Regressionen und Grenzen

`TxCommitOutcomeTest` prüft verlorene Quittung, einen Retry-Vendorcode in der
Commitphase, RuntimeException/Error, korrekte Auditart, erhaltenen manuellen
UNKNOWN-Zustand, beide Task-Ergebniswege einschließlich fatalem Commit-Error und
einen Interrupt nach bestätigtem Commit. Ein separater Fall hält unverändertes
Error-Verhalten vor dem Commit fest. Diese Tests verwenden einen Fake-Pool und
behaupten keine echte Geldwirkung.

`TxCommitOutcomeDbTest` ergänzt tatsächliche Oracle-Commits mit anschließend
  synthetisch verlorener Quittung für `execute` und die manuelle API. Ein eigener
UUID-Demomarker wird aus unabhängiger Verbindung nachgelesen. Ein weiterer Fall
injiziert einen Error erst nach echtem Commit und verlangt den typisierten
Commit-Error sowie genau eine persistierte Wirkung. Eine weitere Probe
hält den JDBC-Commit gezielt bis nach dem Wachhund zurück und simuliert explizit
einen Interrupt ignorierenden Treiber; danach committet die echte Verbindung.
Dies ist kein Beleg, dass der eingesetzte Oracle-Treiber generell Interrupts ignoriert.

`testsweet/ProduktCommitAusgangDbTest` prüft das installierte Produkt-JAR mit
echtem Service, Bescheid, Zahlung, Oracle und Produkt-HTTP: erst tatsächlicher
Fachcommit, dann synthetischer Quittungsverlust per SQLException oder Error;
erwartet werden in beiden Fällen HTTP 409,
nachlesbare genau eine Zahlung und passendes Restbudget. Replay mit derselben
Nummer bleibt erfolgreich, eine neue Nummer für denselben aktiven Monat wird
abgewiesen. Ein weiterer Fall prüft die manuelle API gegen ein wirklich committetes
Budgetupdate. Alle Bereinigungen betreffen ausschließlich eigene registrierte
UUID-/BG-Zeilen; kein Datenbankreset und keine fremden Altjournale.

Die Quellprüfung und `git diff --check` ersetzen keine Testausführung. Zentraler
Neubuild und beide Suiten sind vor einer Aussage über den verifizierten Endstand nötig.
