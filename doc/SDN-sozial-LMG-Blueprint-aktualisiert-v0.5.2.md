# Technischer Blueprint und Spezifikation: SDN.sozial LMG (Grundsicherung SGB XII)

*Aktualisierte Fassung, Stand 2026-10-10, Software-Version 0.5.2.
Ersetzt fachlich und technisch die Abschnitte 2–5 des ursprünglichen Blueprint-PDFs (Google-Docs-Export, im selben Ordner unverändert als Archiv).
Die Unternehmens- und Marktanalyse (Abschnitt 1 des Originals) bleibt inhaltlich unverändert gültig und wird hier nicht wiederholt.*

## 0. Änderungsübersicht gegenüber dem ursprünglichen Blueprint

| # | Abschnitt im Original | Änderung | Grund / Quelle |
|---|---|---|---|
| 1 | Mehrbedarfe: nur „17 % Merkzeichen G, 2,3 % Warmwasser" | Vollständig: Merkzeichen G und Schwangerschaft je 17 % (§ 30 Abs. 1, 2); Alleinerziehende 36 % bzw. 12 % je Kind, max. 60 %, Bezug **RBS 1** (§ 30 Abs. 3); Warmwasser **stufenabhängig** 2,3/2,3/–/1,4/1,2/0,8 % (§ 30 Abs. 7); Deckel nach Abs. 6 gilt nicht für Abs. 7 | [§ 30 SGB XII](https://www.gesetze-im-internet.de/sgb_12/__30.html) |
| 2 | KdU: Kaltmiete + Heizung | Dreiteilung Kaltmiete / kalte Betriebskosten / Heizung; Angemessenheitsgrenze (Gesamtangemessenheit); Warmwasser-Abzug bei zentraler Erzeugung; **Karenzzeit und Absenkungsfrist dokumentiert als nicht modelliert** | [§ 35 SGB XII](https://www.gesetze-im-internet.de/sgb_12/__35.html) (Abs. 1, 3, 5, 7) |
| 3 | Einkommen: „Rente voll anrechenbar, Freibetrag 30 % bis 281,50 EUR" | Zusätzlich Absetzbeträge (Steuern, SV, Versicherungen, Werbungskosten) **vor** dem Freibetrag; Freibetragsbasis ist das **Brutto**-Erwerbseinkommen. Nicht modelliert: Altersvorsorge-Freibetrag 100 EUR + 30 % (Abs. 4/5), WfbM-Sonderregel (Abs. 3 Satz 2) | [§ 82 SGB XII](https://www.gesetze-im-internet.de/sgb_12/__82.html) |
| 4 | Vermögen: nicht enthalten | Vermögensanrechnung mit Schonvermögen 10.000 EUR je volljähriger Person, 500 EUR je überwiegend unterhaltener Person | [§ 90 Abs. 2 Nr. 9 SGB XII](https://www.gesetze-im-internet.de/sgb_12/__90.html) + BarbetragsVO § 1 (Beträge laut [buzer.de](https://www.buzer.de/gesetz/2234/index.htm), Primärseite nicht abrufbar → vor Produktivbetrieb prüfen); § 43 Abs. 1 SGB XII |
| 5 | Zahlungsstatus `BEREIT`/`ÜBERGEBEN`/`STORNIERT` | `ANGELEGT` → `BEREIT` → `UEBERGEBEN` / `STORNIERT` (ASCII-stabil); Vier-Augen-Prinzip **serverseitig** erzwungen (Anlage immer `ANGELEGT`, Freigabe nur durch zweite Person) | Prüfbefund v0.5.0/0.5.1 |
| 6 | Auszahlung ohne Monatsprüfung | Pro Bedarfsgemeinschaft und Zahlungsmonat nur **eine** aktive Zahlung (zweite Vorgangsnummer für denselben Monat → `VALIDATION`) | Audit v0.5.1 (Doppelzahlung bei neuer Vorgangsnummer) |
| 7 | Kein Bescheid | Bewilligungsbescheid nach § 44 SGB XII mit Begründung (§ 35 SGB X) und Rechtsbehelfsbelehrung (§ 36 SGB X; §§ 84, 87, 88 SGG); `BESCHEID_DATUM` = Ausstellungsdatum, Bekanntgabe nach § 37 Abs. 2 SGB X (3-Tage-Fiktion) ist ein eigener Zeitpunkt | [§§ 84/87/88 SGG](https://www.gesetze-im-internet.de/sgg/__84.html), [§ 37 SGB X](https://www.gesetze-im-internet.de/sgb_10/__37.html) |
| 8 | Keine Saga | 4-stufige Antrags-Saga mit Rückwärtskompensation; bereits **übergebene** Zahlungen werden nicht automatisch storniert (ALARM-Journal, manuelle Rückholung), da das Geld die Behörde bereits verlassen hat | Audit v0.5.1 |
| 9 | Tomcat + Servlets + `@CustomTransactional`-Proxy | `TxManager` (funktional), JDK-HttpServer, Oracle UCP als Produktionspool; Annotation-Fassade nicht umgesetzt | Abschnitt 4 |
| 10 | Oberfläche | CSP ohne `unsafe-inline` auch auf der Druckseite (`druck.js`), Token nur im Authorization-Header, Anmelde-Drosselung, HTML-Escaping | Audit v0.5.0/0.5.1 |

## 1. Produktentscheidung

Nachgebaut wird das Modul **Leistungsmanagement (LMG)** für die **Grundsicherung im Alter und bei Erwerbsminderung (4. Kapitel SGB XII)**.
Gründe: (1) die Geschäftslogik ist bundesgesetzlich normiert und deterministisch abbildbar, (2) das Modul verlangt zwingend eine
eigene Transaktionssteuerung (Alles-oder-nichts bei Geldbewegungen), (3) es zeigt exemplarisch optimistisches und pessimistisches Locking.

Produktbezeichnung in Oberfläche und Artefakten: **SDN.sozial**; zentraler Fachbegriff **SozialFall** (Leistungsberechtigter innerhalb einer Bedarfsgemeinschaft).

## 2. Fachliche Spezifikation (Referenzjahr 2026)

### 2.1 Regelbedarfsstufen (Anlage zu § 28 SGB XII)

| RBS | 1 | 2 | 3 | 4 | 5 | 6 |
|---|---|---|---|---|---|---|
| EUR/Monat | 563 | 506 | 451 | 471 | 390 | 357 |

### 2.2 Mehrbedarfe (§ 30 SGB XII)

| Mehrbedarf | Regel |
|---|---|
| Merkzeichen G / volle Erwerbsminderung | 17 % des eigenen Regelbedarfs (RBS 1: 95,71 EUR) |
| Schwangerschaft ab 12. SSW | 17 % des eigenen Regelbedarfs |
| Alleinerziehende | Bezug RBS 1; 36 % bei einem Kind unter 7 oder zwei bis drei Kindern unter 16, sonst 12 % je minderjährigem Kind; höchstens 60 % (337,80 EUR); keine Kumulation |
| Dezentrale Warmwassererzeugung | RBS 1/2 2,3 %; RBS 4 1,4 %; RBS 5 1,2 %; RBS 6 0,8 %; RBS 3 kein Satz |
| Deckel | Summe der Mehrbedarfe nach Abs. 1–5 höchstens in Höhe des eigenen Regelbedarfs (Abs. 6); Warmwasser (Abs. 7) liegt außerhalb |

### 2.3 Kosten der Unterkunft und Heizung (§ 35 SGB XII)

- Erfassung je SozialFall: Kaltmiete, kalte Betriebskosten, Heizkosten; Summe je Bedarfsgemeinschaft.
- Anerkennung in tatsächlicher Höhe, soweit angemessen; Angemessenheitsgrenze konfigurierbar (`lmg.kdu.angemessenheitsgrenze`), Überschreitung wird im Bescheid mit * gekennzeichnet.
- Bei zentraler Warmwassererzeugung (Flag aus) wird ein konfigurierbarer Warmwasseranteil von den Heizkosten abgezogen (`lmg.kdu.warmwasserabzug`, Standard: deaktiviert; § 35 Abs. 5 – örtlich zu parametrisieren und fachlich zu prüfen); bei dezentraler Erzeugung stattdessen Mehrbedarf nach § 30 Abs. 7.
- **Nicht modelliert (Vereinfachung):** Karenzzeit von einem Jahr (Abs. 1 Satz 2–9), Absenkungsfrist in der Regel sechs Monate (Abs. 3 Satz 2), zwölf Monate Schutz nach Tod eines Haushaltsmitglieds (Abs. 3 Satz 4), Pauschalen (Abs. 4).

### 2.4 Einkommen (§ 82 SGB XII)

1. Rente und Erwerbseinkommen (brutto) je SozialFall.
2. Absetzbeträge (Abs. 2): Steuern, Pflichtbeiträge Sozialversicherung, angemessene/gesetzlich vorgeschriebene Versicherungen, notwendige Ausgaben.
3. Erwerbsfreibetrag (Abs. 3): `min(30 % × Brutto-Erwerbseinkommen, 50 % × RBS 1)` = höchstens 281,50 EUR.
4. Anrechenbar = Rente + Erwerb − Freibetrag − Absetzbeträge, mindestens 0.
5. Nicht modelliert: Altersvorsorge-Freibetrag (Abs. 4/5), Sonderregel Werkstattentgelt (Abs. 3 Satz 2), Ausnahmen vom Einkommensbegriff (Abs. 1 Satz 2).

### 2.5 Vermögen (§§ 43, 90 SGB XII, BarbetragsVO)

Schonvermögen 10.000 EUR je volljähriger Person (RBS 1–3) + 500 EUR je überwiegend unterhaltener Person (RBS 4–6).
Das darüber liegende Vermögen mindert den Zahlbetrag. Nicht modelliert: Erhöhung/Herabsetzung nach § 2 BarbetragsVO, Verbrauchsfortschrieb, § 43 Abs. 2 (Einkommensgrenze 100.000 EUR).

### 2.6 Gesamtbedarf und Zahlbetrag

```
Gesamtbedarf  = Σ(Regelbedarf + Mehrbedarfe) + anerkannte KdU
Zahlbetrag    = max(0, Gesamtbedarf − anrechenbares Einkommen − verwertbares Vermögen)
Zahlbetrag ≤ 0  → fachlicher Hinweis KEIN_ANSPRUCH, keine Zahlung
```

## 3. Prozesse

### 3.1 Zahlbarmachung (eine kurze Transaktion, `ordneAuszahlungAn`)

1. Idempotenz: existiert die Vorgangsnummer, wird die vorhandene Zahlung zurückgegeben (Prüfung nach Erhalt der Sperre wiederholt).
2. Pessimistische Sperre `SELECT … FOR UPDATE` auf die Bedarfsgemeinschaft.
3. Berechnung; ≤ 0 → `KEIN_ANSPRUCH`; größer als `BUDGET_REST` → `BUDGET_ERSCHOEPFT`.
4. **Monatsprüfung:** existiert für dieselbe Bedarfsgemeinschaft und denselben Zahlungsmonat eine nicht stornierte Zahlung → `VALIDATION`.
5. SEPA-Bankdaten bestimmen und validieren (ISO 13616, Modulo 97).
6. Zahlung mit Status `ANGELEGT` anlegen, Restbudget mindern, Journaleintrag schreiben; Commit.
7. Freigabe (`ANGELEGT` → `BEREIT`) nur durch einen zweiten Sachbearbeiter (Vier-Augen-Prinzip, serverseitig); Kassenlauf (`BEREIT` → `UEBERGEBEN`) und Storno nur Admin.

### 3.2 Bescheid (§ 44 SGB XII)

Verfügungssatz, Bedarfsaufschlüsselung (Regelbedarf, Mehrbedarfe, KdU, Warmwasser-Abzug, Einkommen, Absetzbeträge, Vermögen),
Vorbehalt bei gekürzter KdU, Begründung nach § 35 SGB X, Rechtsbehelfsbelehrung nach § 36 SGB X (Widerspruch binnen eines Monats, § 84 SGG, auch bei anderen inländischen Behörden
fristwahrend; Klage nach § 87 SGG; Untätigkeitsklage nach § 88 SGG; Vertretung § 73 Abs. 2 SGG). Ort, Behörde, Anschrift und Gericht kommen aus der Konfiguration, nichts ist fest verdrahtet.

### 3.3 Antrags-Saga

Antrag → Bedarfsprüfung → Bescheid → Erstauszahlung. Bei einem Fehler wird rückwärts kompensiert (idempotent, jede Kompensation in eigener Transaktion);
Vorgänge werden historisiert statt gelöscht. Eine bereits `UEBERGEBEN`e Erstauszahlung wird **nicht** automatisch storniert (das Geld ist bereits an die Kasse übergeben):
stattdessen entsteht ein ALARM-Journaleintrag zur manuellen Rückholung. Wiederanlauf nach Absturz (`recover`) claimt jede Saga mit `FOR UPDATE SKIP LOCKED`.

### 3.4 Maskenänderung

`UPDATE … WHERE ID = ? AND VERSION = ?`; 0 Zeilen → `KONKURRENZ` („zwischenzeitlich geändert"), Rollback, Neuladen.

## 4. Technisches Architekturkonzept

| Blueprint-Vorgabe (Original) | Umsetzung | Begründung |
|---|---|---|
| `@CustomTransactional` + `java.lang.reflect.Proxy` + `ThreadLocal<Connection>` | `TxManager.execute(TxWork)` mit explizitem `TxContext` | funktional, getestet, ein Baustein; dünne Annotation-Fassade wäre nachrüstbar |
| Oracle UCP | `UcpConnectionPool` (Produktion), `SimpleConnectionPool` (Tests) | austauschbar über `ConnectionPool`-Schnittstelle |
| Retry bei ORA-00060 (3 Versuche) | `RetryPolicy`: ORA-00054/00060/08177, SQLState 40001/61000, Backoff mit Zufallsanteil | erweitert |
| Eingebetteter Tomcat + Servlets + Jackson | JDK-HttpServer, eigener JSON-Parser/-Writer | keine Abhängigkeiten, gleiche Endpunkt-Semantik |
| Optimistisches Locking (`VERSION`) | Trigger `TRG_BG_VERSION`/`TRG_FALL_VERSION` + `WHERE VERSION = ?` | wie spezifiziert |
| Pessimistisches Locking | `SELECT … FOR UPDATE` (Bedarfsgemeinschaft, Zahlungszeile bei Storno/Kassenlauf, `SKIP LOCKED` bei Saga-Claim) | erweitert |
| — | `TaskController` (Thread-Pool, Warteschlange, Timeout-Wachhund), `SagaEngine`, `TxAuditLog`, JMX-Monitoring | zusätzlich |

Jede Web-Anfrage läuft als Task über den `TaskController`. Bekannter Restpunkt: ein vom Handle abgebrochener, noch laufender Task verschwindet aus der Running-Map, die Statistik `running` kann dadurch kurzzeitig zu niedrig ausfallen (Zähler bleiben über das `settle()`-Gate korrekt).

## 5. Relationales Datenmodell (Ist-Stand, `ddl/lmg_schema.sql`)

| Tabelle | Wichtige Spalten |
|---|---|
| `BEDARFSGEMEINSCHAFT` | `ID` (SEQ_BG), `AKTENZEICHEN` (UNIQUE), Hauptperson, Status aktiv/historisiert, `BUDGET_REST`, `VERMOEGEN`, `WIEDERVORLAGE_AM`, `IBAN`, `BIC`, `KONTOINHABER`, `VERSION` |
| `SOZIAL_FALL` | `ID`, `BG_ID`, `REGELBEDARFSSTUFE` 1–6, `FLAG_MERKZEICHEN_G`, `FLAG_SCHWANGER`, `ANZ_KINDER_U7`, `ANZ_KINDER_U16`, **`ANZ_KINDER_MINDERJ`**, `FLAG_WARMWASSER`, `KDU_MIETE`, `KDU_BETRIEBSKOSTEN`, `KDU_HEIZUNG`, Einkommen Rente/Erwerb, `ABZ_STEUERN`, `ABZ_SV`, `ABZ_VERSICHERUNGEN`, `ABZ_ERWERBSAUSGABEN`, `VERSION` |
| `ZAHLUNG` | `ID`, `BG_ID`, `VORGANGS_NR` (UNIQUE), `ZAHLUNGSMONAT` (YYYY-MM), Betrag, Status `ANGELEGT`/`BEREIT`/`UEBERGEBEN`/`STORNIERT`, Empfänger-Bankdaten, `SACHBEARBEITER_ID`, `FREIGEGEBEN_VON` |
| `BESCHEID` | `BESCHEID_NR` (UNIQUE), `BESCHEID_DATUM` (Ausstellungsdatum), `BEWILLIGUNG_VON`/`_BIS` (YYYY-MM), Zahlbetrag, Status `ERTEILT`/`AUFGEHOBEN` |
| `JOURNAL` | `ID`, logische `BG_ID` (ohne FK), `AKTION_TEXT`, `SACHBEARBEITER_ID`, `ERSTELLT_AM` |
| `TX_SAGA`, `TX_SAGA_STEP` | Saga-Journal (Status mit CHECK-Constraints) |

**Migration bestehender Datenbanken (v0.5.2):**
`ALTER TABLE SOZIAL_FALL ADD ANZ_KINDER_MINDERJ NUMBER(2) DEFAULT 0 NOT NULL;`

## 6. Phasenplan für die KI-gestützte Umsetzung (Ist-Stand)

| Phase | Ergebnis | Status |
|---|---|---|
| 1 | Oracle-DDL, Sequences, Versions-Trigger | umgesetzt (`ddl/`) |
| 2 | Connection-Pool (UCP + Simple) | umgesetzt |
| 3 | Transaktionssteuerung (`TxManager`, `RetryPolicy`, `TaskController`, `SagaEngine`) | umgesetzt, ohne Annotation-Proxy |
| 4 | DAOs mit ausschließlich `PreparedStatement`, Verbindung nur über `TxContext` | umgesetzt |
| 5 | Fachlogik `Leistungsberechnung`, `SozialLeistungsService`, `BescheidGenerator` | umgesetzt (Vereinfachungen siehe 2.3–2.5) |
| 6 | JSON-API + Single-Page-Oberfläche, Anmeldung mit Rollen | umgesetzt |
| 7 | Karenzzeit/Absenkungsfrist, § 82 Abs. 4/5, § 43 Abs. 2, § 44 Abs. 2/3, Nachzahlung/Aufrechnung | offen (Roadmap Etappe 4) |

## 7. Rechtsgrundlagen und Quellen

Vollständige Tabelle mit Umsetzungsstand: `doc/leistungsmanagement.md`, Abschnitt „Rechtsgrundlagen".

- SGB XII §§ 28, 30, 35, 43, 44, 82, 90: <https://www.gesetze-im-internet.de/sgb_12/>
- SGB X §§ 35, 36, 37: <https://www.gesetze-im-internet.de/sgb_10/>
- SGG §§ 73, 84, 87, 88: <https://www.gesetze-im-internet.de/sgg/>
- BarbetragsVO § 1/§ 2 (Beträge): <https://www.buzer.de/gesetz/2234/index.htm> — amtliche Primärfassung vor Produktivbetrieb gegenprüfen.
- Regelbedarfsstufen 2026: Anlage zu § 28 SGB XII / Regelbedarfsstufen-Fortschreibung; Abgleich mit dem Bundesanzeiger steht aus.

*Hinweis: Dies ist ein technisches Demonstrationssystem, keine Rechtsberatung und kein produktiv zugelassenes Fachverfahren.*
