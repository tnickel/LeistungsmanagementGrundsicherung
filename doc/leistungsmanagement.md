# Leistungsmanagement Grundsicherung (SDN.sozial LMG)

*Stand: 2026-10-09, Version 0.4.0 (SEPA, Vier-Augen-Prinzip, Bescheidwesen § 37 SGB X, Antrags-Saga)*

Fachschicht auf der eigenen Transaktionssteuerung: Bearbeitung von Grundsicherungs-
Leistungen im Alter und bei Erwerbsminderung nach **SGB XII** — von der Bedarfs-
ermittlung über die Einkommensbereinigung bis zur angeordneten Auszahlung mit
revisionssicherem Journal. Die Oberfläche ist eine Single-Page-Application im
Widget-Dashboard-Stil unter der Produktbezeichnung **SDN.sozial**.

## Namenskonventionen (wichtig)

- **SDN.sozial** ist die Produktbezeichnung der Oberfläche und des Moduls. Der Name
  des Vorbild-Umfelds darf in keinem neuen Artefakt (Code, DDL, UI, Doku) auftauchen —
  das Blueprint-PDF wird hier nur als „Blueprint-Spec" referenziert.
- **SozialFall** ist der zentrale Fachbegriff für einen Leistungsberechtigten
  innerhalb einer Bedarfsgemeinschaft (in der Blueprint-Spec: „Leistungsfall";
  Tabelle bewusst `SOZIAL_FALL` genannt).
- Fachbegriffe (Bedarfsgemeinschaft, SozialFall, Zahlungsmonat) bleiben durchgängig
  deutsch — DDL, Klassen und Oberfläche verwenden dieselbe Sprache.

## Fachliche Regeln (Referenzjahr 2026)

| Größe | Regel |
|---|---|
| Regelbedarf | RBS 1 = 563,00 EUR, RBS 2 = 506,00 EUR, RBS 3 = 451,00 EUR, RBS 4 = 471,00 EUR, RBS 5 = 390,00 EUR, RBS 6 = 357,00 EUR (Stufen 1–6 für 2026 vollständig hinterlegt). |
| Mehrbedarf Merkzeichen G | 17 % des Regelbedarfs (§ 30 Abs. 1 SGB XII); bei RBS 1 exakt 95,71 EUR, bei RBS 2 exakt 86,02 EUR. |
| Mehrbedarf dezentrale Warmwassererzeugung | 2,3 % des Regelbedarfs (§ 30 Abs. 7 SGB XII); bei RBS 1 exakt 12,95 EUR, bei RBS 2 exakt 11,64 EUR. |
| KdU (Dreiteilung) | anerkannte Kaltmiete + kalte Betriebskosten + Heizkosten, je SozialFall erfasst, BG-weit summiert. |
| Erwerbsfreibetrag | min(30 % Brutto-Erwerbseinkommen, 50 % der RBS 1 = 281,50 EUR) nach § 82 Abs. 3 SGB XII. |
| Anrechenbares Einkommen | Renteneinkünfte (voll anrechenbar) + Erwerbseinkommen − Freibetrag, mindestens 0. |
| Gesamtbedarf / Zahlbetrag | Summe über alle SozialFälle der Bedarfsgemeinschaft; Zahlbetrag = Gesamtbedarf − anrechenbares Einkommen. Zahlbetrag ≤ 0 → fachlicher Hinweis, kein Anspruch. |

Alle Berechnungen stehen als reine Funktion in
`de.allgemein.lmg.calc.Leistungsberechnung` und sind unit-getestet
(`LeistungsberechnungTest`, Referenzwerte aus der Blueprint-Spec).

## Datenmodell (`ddl/lmg_schema.sql`, Seed: `ddl/lmg_seed.sql`)

| Tabelle | Zweck |
|---|---|
| `BEDARFSGEMEINSCHAFT` | Vorgangskopf: Aktenzeichen (eindeutig), Hauptperson, Status aktiv/historisiert, `BUDGET_REST` (verfügbarer Restanspruch pro Monat, wird bei Auszahlung gemindert), `WIEDERVORLAGE_AM` (Fristen-Widget), Bankverbindung (`IBAN`, `BIC`, `KONTOINHABER`), `VERSION` (optimistisches Locking). |
| `SOZIAL_FALL` | Leistungsberechtigter: Regelbedarfsstufe 1–6, Merkzeichen G (`FLAG_MERKZEICHEN_G`), Warmwasser (`FLAG_WARMWASSER`), KdU (Kaltmiete `KDU_MIETE`, kalte Betriebskosten `KDU_BETRIEBSKOSTEN`, Heizung `KDU_HEIZUNG`), Einkommen (Rente/Erwerb), `VERSION`. |
| `ZAHLUNG` | angeordnete Auszahlung: eindeutige `VORGANGS_NR` (Idempotenz), Zahlungsmonat `YYYY-MM`, Betrag, Status `ANGELEGT`/`BEREIT`/`UEBERGEBEN`/`STORNIERT` (ASCII-stabil), Empfängerbank (`EMPFAENGER_IBAN`, `EMPFAENGER_BIC`, `EMPFAENGER_NAME`), Sachbearbeiter & Freigebender (`SACHBEARBEITER_ID`, `FREIGEGEBEN_VON`). |
| `BESCHEID` | Bewilligungsbescheid nach § 37 SGB X: Bescheidnummer (`BESCHEID_NR`), Bescheiddatum, Bewilligungszeitraum (`BEWILLIGUNG_VON`, `BEWILLIGUNG_BIS`), Zahlbetrag, Status (`ERTEILT`/`AUFGEHOBEN`), Erfasser. |
| `JOURNAL` | revisionssicheres Buchungsjournal: Aktionstext, Sachbearbeiter, logische BG-Referenz ohne FK (Einträge überleben Historisierung). |

IDs kommen aus den Sequences `SEQ_BG`, `SEQ_FALL`, `SEQ_ZAHLUNG`, `SEQ_BESCHEID`, `SEQ_JOURNAL`.
Die Trigger `TRG_BG_VERSION`/`TRG_FALL_VERSION` erhöhen die VERSION bei jedem
UPDATE automatisch, sofern der Aufrufer sie nicht selbst hochgesetzt hat.

## Der kritische Buchungsweg (`SozialLeistungsService.ordneAuszahlungAn`)

Eine einzige kurze Oracle-Transaktion über `TxManager.execute` (Commit/Rollback und
Wiederholung bei ORA-00054/00060/08177 übernimmt die Transaktionssteuerung):

1. **Idempotenz**: existiert die Vorgangsnummer bereits, wird die vorhandene Zahlung
   zurückgegeben und nichts gebucht (Schutz vor Doppelklick und wiederholten Requests).
2. **Pessimistische Sperre**: `SELECT … FOR UPDATE` auf die Bedarfsgemeinschaft —
   parallele Sachbearbeiter buchen nacheinander; Deadlocks wiederholt die Steuerung.
3. **Berechnung und Prüfung**: Zahlbetrag über alle SozialFälle; ≤ 0 →
   `KEIN_ANSPRUCH`; größer als `BUDGET_REST` → `BUDGET_ERSCHOEPFT`.
4. **Zahlung** mit Status `BEREIT` anlegen.
5. **Restbudget mindern** (der VERSION-Trigger stößt dabei parallele Maskenbearbeiter
   korrekt auf eine KONKURRENZ-Meldung).
6. **Journaleintrag** schreiben (Vier-Augen-Prinzip: Sachbearbeiter-Kürzel).

## Maskenänderung (optimistisches Locking)

SPEICHERN einer SozialFall-Maske oder des Vorgangskopfs läuft als
`UPDATE … WHERE ID = ? AND VERSION = ?`:
0 betroffene Zeilen heißt, ein anderer Sachbearbeiter war schneller — die
Transaktion rollt zurück, die Oberfläche meldet „zwischenzeitlich geändert" und
lädt neu (`FachlicheException` mit Code `KONKURRENZ`). Kein Lost Update.

## Webschicht

- **Server**: `de.allgemein.lmg.web.LmgServerMain` auf dem JDK-HttpServer
  (`com.sun.net.httpserver`) — keine zusätzliche Abhängigkeit. Port über
  `LMG_PORT` (Standard 8080), Verbindung wie gewohnt über `TX_URL`/`TX_USER`/`TX_PASS`.
- **Jede API-Anfrage ist ein Task** der Task-Steuerung (`TaskController.submit`):
  begrenzte Parallelität, Zeitlimit-Wachhund und Statistik gelten damit auch für
  den Webbetrieb.
- **JSON** über die schlanke Hilfsklasse `de.allgemein.lmg.web.Json` (ohne externe
  Bibliothek, parser und writer unit-getestet).
- **Fehlerabbildung**: fachliche Fehler → HTTP 400 mit `{code, message}`
  (KONKURRENZ, KEIN_ANSPRUCH, BUDGET_ERSCHOEPFT, VALIDATION, RBS_NICHT_KONFIGURIERT),
  unbekannte Vorgänge → 404, technische Fehler → 500.

| Endpunkt | Wirkung |
|---|---|
| `GET /api/bedarfsgemeinschaften` | Fallübersicht mit Zahlbetrag-Vorschau je Vorgang |
| `GET /api/bedarfsgemeinschaften/{id}` | Vorgangsdetail: SozialFälle samt Berechnung, Zahlungen, Journal |
| `PUT /api/bedarfsgemeinschaften/{id}` | Kopfdaten speichern (Status, Wiedervorlage; VERSION-Prüfung) |
| `PUT /api/sozialfaelle/{id}` | SozialFall speichern (VERSION-Prüfung) |
| `POST /api/auszahlung` | Auszahlung anordnen (Idempotenz über `vorgangsNr`) |
| `GET /api/zahlungen` · `GET /api/journal` | Widgets/Listen über alle Vorgänge |
| `GET /api/system/status` | Systemdiagnose: DB-Check, Bestandszahlen, Task-Statistik, Laufzeitumgebung *(nur Admin)* |
| `POST /api/system/wiederanlauf` | Saga-Wiederanlauf der Demo-Antragsverarbeitung anstoßen (idempotent) *(nur Admin)* |
| `POST /api/bedarfsgemeinschaften` | Neuen Vorgang anlegen (Aktenzeichen eindeutig, Startbudget) |
| `POST /api/bedarfsgemeinschaften/{id}/sozialfaelle` | SozialFall zu einem aktiven Vorgang anlegen |
| `POST /api/kassenlauf` | Alle BEREIT-Zahlungen auf ÜBERGEBEN setzen *(nur Admin)* |
| `POST /api/zahlungen/{id}/freigabe` | Vier-Augen-Freigabe (Status ANGELEGT → BEREIT, rechnerische Feststellung) |
| `POST /api/zahlungen/{id}/storno` | BEREIT-Zahlung stornieren, Restbudget zurückbuchen *(nur Admin)* |
| `GET /api/bedarfsgemeinschaften/{id}/bescheid` | Letzten Bewilligungsbescheid der Bedarfsgemeinschaft abrufen |
| `POST /api/bedarfsgemeinschaften/{id}/bescheid` | Bewilligungsbescheid nach § 37 SGB X erteilen |
| `GET /api/bedarfsgemeinschaften/{id}/bescheid/druck` | Amtliche HTML-Druckansicht mit `@media print` generieren |
| `POST /api/saga/antrag` | Vollständige End-to-End-Saga (Antragsverarbeitung) ausführen |
| `POST /api/anmeldung` · `POST /api/abmeldung` · `GET /api/werbin` | Anmeldung (Token) und angemeldete Kennung/Rolle |

## Anmeldung, Rollen und Betriebsfunktionen (v0.3.0)

- **Anmeldung**: die Oberfläche zeigt eine Login-Maske; die API prüft je Anfrage ein
  Bearer-Token aus der Anmeldung (Sitzungen im Speicher, nach Neustart neu anmelden).
  Konten kommen aus `LMG_BENUTZER` (Format `kennung:passwort:rolle;…`, Rollen
  `admin`/`sachbearbeiter`); ohne Variable gelten Demo-Standardkonten
  (`admin-01/admin`, `sb-01/sb-01`, `sb-02/sb-02`) mit Warnung auf der Konsole.
- **Rollen**: Admin sieht Adminbereich, Kassenlauf und Storno; Sachbearbeiter
  bearbeiten Vorgänge und ordnen Auszahlungen an (403 für die Admin-Funktionen,
  serverseitig erzwungen).
- **Identität**: die Sachbearbeiter-Kennung im Journal kommt ausschließlich aus der
  Anmeldung — Client-Angaben im Anfragekörper werden ignoriert (kein
  „Ehrensache-Kürzel" mehr).
- **Neuanlage**: Vorgang (Aktenzeichen eindeutig — Doppelprüfung läuft über
  ORA-00001 → fachlicher Hinweis) und beliebig viele SozialFälle je Vorgang; beides
  mit Journaleintrag und ohne Datenbank-Neustart direkt in der Berechnung.
- **Kassenlauf** (eine Transaktion): sperrt alle BEREIT-Zahlungen (`FOR UPDATE`),
  setzt sie auf ÜBERGEBEN und schreibt einen Sammel-Journaleintrag mit Anzahl und
  Summe. Das ist die fachliche Übergabe an Kasse/Zahlungsdienst — ein echter
  Zahlungsstrom (SEPA) bleibt außen vor.
- **Storno** (nur Status BEREIT): sperrt Zahlung und Vorgang, setzt STORNIERT,
  bucht das Restbudget zurück und journalisiert — alles atomar.
- **Wiederanlauf beim Start**: `LmgServerMain` führt `SagaEngine.recover()` beim
  Hochfahren aus (zusätzlich zum Button im Adminbereich).
- **Konfiguration**: `LMG_PORT`, `LMG_POOL_GROESSE`, `LMG_TASKS`,
  `LMG_WARTESCHLANGE` und `LMG_BENUTZER` sind Umgebungsvariablen.

## Adminbereich und Funktionsübersicht

Die Navigation enthält zusätzlich zwei Ansichten:

- **Adminbereich** — Systemdiagnose über `Systemdiagnose.status()`: eine kurze
  Transaktion mit hartem 5-s-Zeitlimit und genau einem Versuch prüft Datenbank
  (Erreichbarkeit, Produktkennung, Antwortzeit, DB-Zeit), Fachschema (Bestandszahlen),
  Wiederanlauf-Lage (offene vs. abgebrochene Sagas) und zeigt die Statistik der
  Task-Steuerung sowie Laufzeitumgebung (Java-Version, Server-Start, Laufzeit).
  Alle Prüfungen grün = „Datenbank ok und alles gestartet". Als Aktion steht der
  Saga-Wiederanlauf als Button bereit; offen (RUNNING/COMPENSATING) und abgebrochen
  (FAILED) werden bewusst getrennt gezählt, weil eine FAILED-Saga ohne committete
  Schritte nichts zu kompensieren hat.
- **Funktionsübersicht** — statische Übersicht aller Fach- und Kernfunktionen mit
  Bausteinen und Endpunkten (Bedarfsermittlung, Einkommensbereinigung, Zahlbarmachung,
  Sperrverfahren, Journal, TxManager, RetryPolicy, TaskController, SagaEngine).

Die Demo-Saga (Antrag → Bescheid → Auszahlung) ist für diesen Zweck aus `DemoMain`
in `de.allgemein.tx.demo.AntragsDemoProzess` ausgelagert, damit Adminbereich und
Demo dieselbe Saga-Definition nutzen.

## Oberfläche (`src/main/resources/webapp`)

Vanilla HTML/CSS/JS, keine CDN-Abhängigkeit. Widget-basierte **Fallübersicht**
(aktive Vorgänge, Zahlbetrag-Vorschau, Zahlungen bereit, Fristen/Wiedervorlage),
zentrales Suchfeld mit inkrementeller Filterung, **Vorgangsmaske** mit
Bedarfsermittlung (read-only berechnet), editierbaren SozialFall-Masken,
Zahlbarmachung (Monat, Sachbearbeiter-Kürzel, Button „Auszahlung anordnen" mit
Doppelklick-Schutz) und Zahlungshistorie sowie das **Buchungsjournal**.
Fehler und Sperrkonflikte erscheinen als Dialoge.

## Bewusste Abweichungen von der Blueprint-Spec

1. **TxManager statt Annotation/Proxy**: Die Blueprint-Spec beschreibt einen
   `@CustomTransactional`-Proxy mit `ThreadLocal<Connection>`. Wir verwenden den
   bestehenden `TxManager` (funktional, getestet) — derselbe Umfang
   (AutoCommit aus, Commit/Rollback, Retry bei ORA-00060 bis 3 Versuche), aber ein
   Baustein statt zweier. Für eine Annotation-Fassade wäre nur eine dünne Schicht
   über `TxManager` nötig.
2. **UCP statt SimpleConnectionPool in Produktion**: die Blueprint-Spec verlangt
   Oracle UCP; der Demo-Lauf nutzt den vorhandenen Pool, da alles nur an
   `getConnection`/`release` hängt (Tausch berührt keine Fachklasse).
3. **JDK-HttpServer statt eingebettetem Tomcat**: gleiche Endpunkt-Semantik
   (POST/PUT/GET, HTTP 200/400/404), aber null Abhängigkeiten.
4. **SozialFall statt „Leistungsfall"** und Produktname SDN.sozial (siehe oben).
5. **KdU je SozialFall, Zahlbetrag je Bedarfsgemeinschaft**: bei Paaren wird der
   Zahlbetrag über die SozialFälle summiert; die Zahlung referenziert die
   Bedarfsgemeinschaft (FALL_ID nur bei genau einem SozialFall).
6. **SEPA-Bankverbindung vollständig integriert (v0.4.0)**: Entgegen der ursprünglichen
   Blueprint-Spec sind `IBAN`, `BIC` und `KONTOINHABER` in `BEDARFSGEMEINSCHAFT`
   sowie `EMPFAENGER_IBAN`, `EMPFAENGER_BIC` und `EMPFAENGER_NAME` in `ZAHLUNG`
   vollständig persistiert, werden mit ISO 13616 Modulo 97 validiert und können
   in der Maske bearbeitet werden.
7. **Bescheidwesen und Saga (v0.4.0)**: Vollständige Umsetzung des Bescheidwesens
   nach § 37 SGB X inklusive amtlicher HTML-Druckansicht sowie 4-stufiger
   Antrags-Saga mit automatischer Rückwärtskompensation bei fehlendem Anspruch.

## Bedienung

```bat
:: bequemer Weg: DB-Container bei Bedarf starten, kompilieren, Server + Browser
start.bat
:: bzw. die Saga-Demo statt der Fachanwendung
start.bat demo
```

```bash
# Schema einspielen (einmalig, als Schema-Owner, nach ddl/oracle_schema.sql)
sqlplus tx_demo/… @ddl/lmg_schema.sql
sqlplus tx_demo/… @ddl/lmg_seed.sql

# Server starten (TX_URL/TX_USER/TX_PASS wie beim DemoMain)
mvn compile exec:java -Dexec.mainClass=de.allgemein.lmg.web.LmgServerMain
# → http://localhost:8080
```

Berechnungsregeln sind unabhängig von der Datenbank testbar: `mvn test`
(inkl. `LeistungsberechnungTest`, `JsonTest`, `ZahlungsmonatTest`).
