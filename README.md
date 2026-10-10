# Transaktionssteuerung (Java + Oracle)

Eigene Transaktionssteuerung als Ersatz für das lizenzierte IBM-Modul (klassisch:
CICS Transaction Server auf dem Großrechner, wie es auch hinter den
AKDN/KDN.sozial-Fachverfahren steckte). Die Erfahrung aus Jahrzehnten TP-Monitor
steckt in zwei Lehren:

1. **Der schwere Teil ist die Datenbank** — ACID, Sperrverwaltung, Redo-Logging und
   Wiederanlauf kann Oracle bereits. Das hier nachzuprogrammieren wäre doppelt und
   schlechter.
2. **Was man selbst programmiert, ist die Steuerung darüber** — genau das tut dieses
   Projekt: Transaktionsdurchführung mit Commit/Rollback, Wiederholung bei
   Sperren-Konflikten, begrenzte parallele Task-Ausführung, Statistik und ein
   Journal für lange Geschäftsvorgänge (Saga-Muster).

## Dokumentation

- [doc/projektbeschreibung.md](doc/projektbeschreibung.md) — Ausgangslage, Ziel, Leitprinzip, Scope
- [doc/architektur.md](doc/architektur.md) — Bausteine, Abläufe, Datenmodell, Architekturregeln
- [doc/leistungsmanagement.md](doc/leistungsmanagement.md) — Fachschicht: Leistungsmanagement Grundsicherung (SDN.sozial LMG)
- [doc/roadmap.md](doc/roadmap.md) — aktueller Stand und geplante Etappen

## Aufbau

```
transaktionssteuerung/
├── ddl/oracle_schema.sql          Journal- und Demo-Tabellen in Oracle
├── ddl/lmg_schema.sql             Fachschema Leistungsmanagement (+ lmg_seed.sql: Demo-Bestand)
└── src/
    ├── main/java/de/allgemein/tx/
    │   ├── TxManager.java         Kern: Begin/Commit/Rollback + Zeitlimit + Retry
    │   ├── TxContext.java         eine laufende Transaktion (Savepoints, Deadline)
    │   ├── TxOptions.java         Isolation, Zeitlimit, Anzahl Versuche
    │   ├── RetryPolicy.java       ORA-00054/00060/08177 → Wiederholversuch + Backoff
    │   ├── TaskController.java    Task-Steuerung: begrenzt parallel, Timeout-Wachhund
    │   ├── TaskHandle/TaskStats   Ergebnis-Handle und Statistik (CICS-artiges Monitoring)
    │   ├── SimpleConnectionPool   schmaler Pool (in Produktion: HikariCP/UCP)
    │   ├── saga/                  Saga-Engine + Journal für lange Geschäftsvorgänge
    │   └── demo/DemoMain.java     lauffähiges Beispiel (Antrag → Bescheid → Auszahlung)
    ├── main/java/de/allgemein/lmg/
    │   ├── calc/                  SGB-XII-Berechnung (reine Funktion, unit-getestet)
    │   ├── dao/                   PreparedStatements auf der TxContext-Verbindung
    │   ├── service/               Zahlungs-Workflow, Masken-Speicherung (Locking)
    │   └── web/                   JSON-API + Oberfläche (JDK-HttpServer, ohne Frameworks)
    ├── main/resources/webapp/     SDN.sozial-Oberfläche (Widget-Dashboard, Masken, Journal)
    └── test/java/…                Unit-Tests: Retry-Klassifikation, Berechnung, JSON, Formate
```

## Zuordnung: CICS-Funktion → eigener Baustein

| CICS-Funktion | Erledigt durch |
|---|---|
| Atomarität (SYNCPOINT, Rollback) | `TxManager.execute` — Oracle-Transaktion mit `commit()`/`rollback()` |
| Task-Steuerung (Scheduling, Parallelitätsbegrenzung) | `TaskController` — fester Thread-Pool + Warteschlange |
| Zeitlimit/Abbruch eines Tasks | `TxOptions.timeout` + Wachhund: `Task.cancel(true)` unterbricht, DB rollt zurück |
| Wiederholung nach Deadlock/Ressource belegt | `RetryPolicy` — ORA-00054/00060/08177, exponentieller Backoff |
| Journalling über lange Vorgänge | `saga.SagaJournal` — Tabelle `TX_SAGA`/`TX_SAGA_STEP` |
| Wiederanlauf (Emergency Restart) | `SagaEngine.recover` — kompensiert halbfertige Vorgänge beim Start |
| Monitoring/Statistik | `TaskController.stats()` → `TaskStats` |

## Schnellstart

### Der bequeme Weg: `start.bat`

```bat
start.bat          :: startet DB-Container bei Bedarf, kompiliert, startet den
                      Server auf http://localhost:8080 und öffnet den Browser
start.bat demo     :: startet stattdessen die Saga-Demo (DemoMain)
```

Konfiguration per Umgebungsvariablen (TX_URL, TX_USER, TX_PASS, LMG_PORT), siehe
Kopf der Datei.

### Manuelles Vorgehen

```bash
# 1. Schema anlegen (einmalig, als Schema-Owner)
sqlplus tx_demo/… @ddl/oracle_schema.sql

# 2. Verbindung konfigurieren
export TX_URL="jdbc:oracle:thin:@//rechner:1521/XEPDB1"
export TX_USER=tx_demo
export TX_PASS=…

# 3. Bauen, Tests, Demo
mvn test
mvn compile exec:java

# 4. Fachanwendung starten (Leistungsmanagement Grundsicherung, SDN.sozial-LMG)
sqlplus tx_demo/… @ddl/lmg_schema.sql
sqlplus tx_demo/… @ddl/lmg_seed.sql
mvn compile exec:java -Dexec.mainClass=de.allgemein.lmg.web.LmgServerMain
# → http://localhost:8080  (Port optional über LMG_PORT)
```

Anmeldung in der Oberfläche: Demo-Standardkonten `admin-01/admin` (Admin: Kassenlauf,
Storno, Systemdiagnose) sowie `sb-01/sb-01`, `sb-02/sb-02` (Sachbearbeiter). Eigene
Konten über `LMG_BENUTZER="kennung:passwort:rolle;…"` (Rollen: admin, sachbearbeiter).

### Datenbankumgebung: Oracle 19c (VirtualBox) oder Oracle 23ai Free (Docker)

Standardmäßig ist das Projekt auf die **Oracle Database 19c Enterprise Edition** in der VirtualBox-VM (`oracle-19c-adminwkshop`, PDB `o19c1p1`) vorkonfiguriert:

```bash
# Verbindung zur VirtualBox Oracle 19c (Standard)
export TX_URL="jdbc:oracle:thin:@//localhost:1921/o19c1p1"
export TX_USER=tx_demo
export TX_PASS=tx_demo
```

Alternativ kann eine Docker-Entwickler-DB (Oracle 23ai Free) genutzt werden:

```bash
docker run -d --name lmg-oracle -p 1521:1521 \
  -e ORACLE_PASSWORD=tx_demo \
  -e APP_USER=tx_demo -e APP_USER_PASSWORD=tx_demo \
  -v lmg-oracle-data:/opt/oracle/oradata \
  gvenzl/oracle-free:23-slim-faststart

export TX_URL="jdbc:oracle:thin:@//localhost:1521/FREEPDB1"
```

Die echten-DB-Tests laufen mit gesetzter Verbindung:

```bash
mvn test -Dtest=EchteDbVerifikationTest
```

### Nutzung im eigenen Code

Kurze Transaktion (der Normalfall):

```java
TxManager tx = new TxManager(pool);
tx.execute(TxOptions.DEFAULT, ctx -> {
    try (PreparedStatement ps = ctx.connection().prepareCall(
            "UPDATE KONTO SET SALDO = SALDO - ? WHERE ID = ?")) {
        ps.setBigDecimal(1, betrag);
        ps.setLong(2, kontoId);
        ps.executeUpdate();
    }
    return null;
}); // Commit bei Erfolg, Rollback + evtl. Retry bei Deadlock
```

Langer Geschäftsvorgang (mehrere Commits, absturfsicher):

```java
SagaDefinition vorgang = SagaDefinition.of("ANTRAGSVERARBEITUNG")
        .step("Antrag anlegen",  this::antragAnlegen,  this::antragEntfernen)
        .step("Bescheid erstellen", this::bescheid,    this::bescheidZurück)
        .step("Auszahlung buchen",  this::auszahlen,   this::auszahlungStorno)
        .build();

String sagaId = sagas.run(vorgang, payload);   // bei Fehler: Kompensation rückwärts
int n = sagas.recover(vorgang);                // beim Neustart: halbfertige Vorgänge beenden
```

## Verhaltensregeln der Steuerung

- **Kurze Transaktionen, lange Sagas.** Alles, was atomar sein muss, gehört in eine
  `TxManager.execute`-Transaktion (Oracle hält sie fest). Abläufe, die Minuten dauern
  oder auf Benutzereingaben warten, werden in Saga-Schritte zerlegt.
- **Wiederholbar heißt ganz wiederholen.** Nur Sperren-Konflikte (ORA-00054,
  ORA-00060, ORA-08177) führen zu einem erneuten Versuch der kompletten
  Arbeitseinheit; fachliche Fehler brechen sofort ab.
- **Kompensationen müssen idempotent sein.** Nach einem Absturz zwischen zwei
  Schritten fährt `recover()` die Kompensationen fort — ein Schritt kann also
  zweimal angestoßen werden und darf dann nicht doppelt wirken (die Beispiele
  löschen/aktualisieren deshalb zustandsabhängig).
- **Zeitlimit bricht ab, die Datenbank rollt zurück.** Der Wachhund unterbricht den
  Task-Thread; der JDBC-Aufruf fliegt, `TxManager` rollt die Transaktion zurück.

## Weg zur Produktion

1. `SimpleConnectionPool` durch **HikariCP** oder **Oracle UCP** ersetzen
   (nur zwei Methoden anzupassen).
2. `TaskController.stats()` an **Micrometer/Prometheus** oder JMX anbinden.
3. Für verteilte Transaktionen über **mehrere Datenbanken**: Oracle-XA
   (`OracleXADataSource`, Two-Phase-Commit) — erst wenn wirklich nötig; im
   Ein-Datenbank-Fall, der hier vorliegt, unnötig.
4. Journal-Zugriffe per Granted-Rollen absichern, Audit für Commit/Rollback ergänzen.

Kompiliert mit `--release 21`, läuft auf jedem aktuellen JDK (getestet mit JDK 25).
