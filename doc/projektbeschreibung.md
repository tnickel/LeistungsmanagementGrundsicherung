# Projektbeschreibung: Transaktionssteuerung

*Stand: 2026-09-24*

## Ausgangslage

In Verwaltungs-Fachverfahren — unser Kontext ist die kommunale Sozialverwaltung
(Umfeld von KDN.sozial: Jobcenter nach SGB II, Jugendämter nach SGB VIII,
Sozialämter nach SGB XII/AsylbLG) — bearbeiten Tausende Sachbearbeiter gleichzeitig
dieselben Datenbestände. Auf dem IBM-Großrechner übernimmt das die
**Transaktionssteuerung** des *CICS Transaction Server*: ein TP-Monitor (Transaction
Processing Monitor), der alle Online-Vorgänge startet, begrenzt, überwacht und deren
Änderungen atomar fest­schreibt. Dieses IBM-Modul wird pro Rechenleistung lizenziert
und kostet ordentlich Geld.

## Was wir vorhaben

Dieses Projekt ersetzt das Modul durch eine **Eigenentwicklung in Java 21 auf
Oracle**. Nicht als vollständigen CICS-Ersatz, sondern mit genau den Funktionen,
die wir brauchen:

1. **Transaktionen atomar ausführen** — jede Arbeitseinheit wird ganz committet oder
   ganz zurückgerollt (CICS-Pendant: SYNCPOINT).
2. **Wiederholversuche bei Sperren-Konflikten** — Deadlock (ORA-00060), belegte
   Ressource (ORA-00054), Serialisierbarkeitskonflikt (ORA-08177) führen zu einem
   kontrollierten zweiten/dritten Versuch.
3. **Task-Steuerung** — parallele Bearbeitungsvorgänge werden begrenzt, überwacht,
   bei Zeitlimit-Überschreitung abgebrochen (CICS-Pendant: Task-Control).
4. **Lange Geschäftsvorgänge absturzsicher führen** — Abläufe wie
   *Antrag → Bescheid → Auszahlung* laufen als Kette kurzer Datenbanktransaktionen
   mit Journal und Kompensation (Saga-Muster; CICS-Pendant: Journalling und
   Wiederanlauf).
5. **Monitoring** — Statistik über laufende, erfolgreiche, fehlgeschlagene und
   abgebrochene Tasks (CICS-Pendant: CICS Statistics/Monitoring).

## Leitprinzip

1. **Der schwere Teil ist die Datenbank.** ACID-Eigenschaften, Sperrverwaltung,
   Redo-Logging und Wiederanlauf kann Oracle bereits — das nachzuprogrammieren wäre
   doppelt und schlechter.
2. **Die Steuerung programmieren wir selbst.** Begin/Commit/Rollback, Retry,
   Scheduling, Zeitlimits, Journal und Statistik liegen in diesem Projekt und sind
   damit lizenzfrei, verständlich und testbar.

## Scope

**Im Scope:** Transaktionsdurchführung, Retry, Task-Steuerung, Saga/Journal,
Statistik, Verbindungspool-Abstraktion.

**Außerhalb des Scope (bewusst):** Bildschirm-/Terminalverwaltung (3270-Terminals),
COBOL/PL/I-Host-Umgebung, verteilte Transaktionen über mehrere Datenbanken (XA) —
erst bei echtem Bedarf, Batch-Scheduling, voller TP-Monitor-Funktionsumfang.

## Nutzen

- Wegfall der Lizenzkosten für das IBM-Modul
- Moderner, dokumentierter und testbarer Stack (Java, SQL, Maven)
- Volle Kontrolle über Verhalten (Retry, Timeouts, Kompensationen)

## Referenzen

- CICS: <https://de.wikipedia.org/wiki/Customer_Information_Control_System>,
  <https://www.ibm.com/products/cics-transaction-server>
- KDN.sozial (Kontext der Fachverfahren): <https://kdn-sozial.de>
- Details zur Umsetzung: [architektur.md](architektur.md)
- Geplante Etappen: [roadmap.md](roadmap.md)
