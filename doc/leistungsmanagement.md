# Leistungsmanagement Grundsicherung (SDN.sozial LMG)

*Stand: 2026-10-10, Version 0.5.2 (Fachrecht §§ 30/35/82/90 nachgeprüft, Rechtsgrundlagen-Tabelle, Bescheid mit § 84/87/88 SGG)*

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
| Regelbedarf | RBS 1 = 563,00 EUR, RBS 2 = 506,00 EUR, RBS 3 = 451,00 EUR, RBS 4 = 471,00 EUR, RBS 5 = 390,00 EUR, RBS 6 = 357,00 EUR (Stufen 1–6 für 2026 vollständig hinterlegt; absolute Beträge nach Anlage zu § 28 SGB XII). |
| Mehrbedarf Merkzeichen G | 17 % des Regelbedarfs (§ 30 Abs. 1 SGB XII); bei RBS 1 exakt 95,71 EUR, bei RBS 2 exakt 86,02 EUR. |
| Mehrbedarf Schwangerschaft | 17 % des Regelbedarfs ab der 12. Schwangerschaftswoche (§ 30 Abs. 2 SGB XII), erfasst als Kennzeichen je SozialFall. |
| Mehrbedarf Alleinerziehende | Bemessungsgrundlage ist stets die RBS 1 (§ 30 Abs. 3 SGB XII): 36 % bei mindestens einem Kind unter 7 oder zwei/drei Kindern unter 16, sonst 12 % je minderjährigem Kind (Feld `ANZ_KINDER_MINDERJ`); gedeckelt auf 60 % der RBS 1 = 337,80 EUR. Keine Kumulation von 36 %- und 12 %-Satz (§ 30 Abs. 3 verlangt die höhere der beiden Varianten). |
| Mehrbedarf dezentrale Warmwassererzeugung | stufenabhängig (§ 30 Abs. 7 SGB XII): RBS 1/2 = 2,3 % (12,95/11,64 EUR), RBS 4 = 1,4 % (6,59 EUR), RBS 5 = 1,2 % (4,68 EUR), RBS 6 = 0,8 % (2,86 EUR); für RBS 3 sieht das Gesetz keinen Satz vor (0 EUR). |
| Obergrenze der Mehrbedarfe | Summe der Mehrbedarfe je Person übersteigt nie den eigenen Regelbedarf (§ 30 Abs. 6 SGB XII, Kappung). |
| KdU (Dreiteilung) | anerkannte Kaltmiete + kalte Betriebskosten + Heizkosten, je SozialFall erfasst, BG-weit summiert (§ 42a i.V.m. § 35 SGB XII). Angemessenheitsgrenze (§ 35 Abs. 7): überschreitet die KdU der gesamten Bedarfsgemeinschaft die konfigurierbare Grenze (`lmg.kdu.angemessenheitsgrenze`), wird nur bis zur Grenze anerkannt (Reihenfolge der Fälle) und im Bescheid mit * gekennzeichnet. Bei ZENTRALER Warmwassererzeugung (Flag aus) wird ein örtlich konfigurierbarer Warmwasseranteil von den Heizkosten abgezogen (`lmg.kdu.warmwasserabzug`, § 35 Abs. 5); bei dezentraler Erzeugung stattdessen der Mehrbedarf nach § 30 Abs. 7. |
| Absetzbeträge | vom Einkommen sind abzusetzen: Steuern, Pflichtbeiträge zur Sozialversicherung, gesetzlich vorgeschriebene/angemessene Versicherungsbeiträge, mit der Erzielung verbundene Ausgaben (§ 82 Abs. 2 SGB XII) — je SozialFall erfasst, vor dem Freibetrag abgezogen. |
| Erwerbsfreibetrag | min(30 % Brutto-Erwerbseinkommen, 50 % der RBS 1 = 281,50 EUR) nach § 82 Abs. 3 SGB XII. |
| Anrechenbares Einkommen | Renteneinkünfte (voll anrechenbar) + Erwerbseinkommen − Freibetrag − Absetzbeträge, mindestens 0. |
| Vermögensanrechnung | Vermögen der BG abzüglich Schonvermögen: 10.000 EUR je volljähriger Person (RBS 1–3) + 500 EUR je überwiegend unterhaltener Person (RBS 4–6) nach § 90 Abs. 2 Nr. 9 SGB XII i.V.m. § 1 Barbetragsverordnung; das verwertbare Vermögen mindert den Zahlbetrag (§ 43 Abs. 1 SGB XII). Vereinfachung: kein Verbrauchsfortschrieb über Monate. |
| Gesamtbedarf / Zahlbetrag | Summe über alle SozialFälle der Bedarfsgemeinschaft; Zahlbetrag = Gesamtbedarf − anrechenbares Einkommen − verwertbares Vermögen. Zahlbetrag ≤ 0 → fachlicher Hinweis, kein Anspruch. |

Alle Berechnungen stehen als reine Funktion in
`de.allgemein.lmg.calc.Leistungsberechnung` und sind unit-getestet
(`LeistungsberechnungTest` — Referenzwerte aus Gesetz/Blueprint-Spec, inklusive
Stufen 4–6 des Warmwasser-Mehrbedarfs, Kinder-/Schwangeren-Mehrbedarfen,
Obergrenze, Absetzbeträgen und Vermögensanrechnung).

## Rechtsgrundlagen (Stand der Prüfung: Oktober 2026)

Alle Normen im Wortlaut gegen gesetze-im-internet.de geprüft, soweit nicht anders vermerkt.

| Norm | Inhalt | Umsetzung |
|---|---|---|
| § 28 + Anlage SGB XII | Regelbedarfsstufen 1–6 (2026: 563/506/451/471/390/357 EUR) | `Leistungsberechnung` |
| § 30 Abs. 1, 2 SGB XII | Mehrbedarf Merkzeichen G und Schwangerschaft: je 17 % | umgesetzt |
| § 30 Abs. 3 SGB XII | Alleinerziehende: Bezug **RBS 1**; 36 % (1 Kind < 7 oder 2–3 Kinder < 16) bzw. 12 % je minderjährigem Kind, max. 60 % | umgesetzt (neues Feld `ANZ_KINDER_MINDERJ`) |
| § 30 Abs. 6 SGB XII | Deckel gilt nur für Abs. 1–5 | umgesetzt |
| § 30 Abs. 7 SGB XII | dezentrale Warmwassererzeugung, stufenabhängig 2,3/2,3/–/1,4/1,2/0,8 %; liegt **außerhalb** des Deckels | umgesetzt |
| § 35 Abs. 1, 3, 5, 7 SGB XII | KdU in tatsächlicher Höhe, soweit angemessen; zentrale Warmwasserversorgung ist Teil der Heizkosten; Gesamtangemessenheitsgrenze | umgesetzt (Grenze und Abzug konfigurierbar) |
| § 35 Abs. 1 Satz 2–9, Abs. 3 Satz 2–5 SGB XII | **Karenzzeit** (1 Jahr, seit 2023), Absenkungsfrist (i. d. R. 6 Monate), 12-Monats-Schutz nach Tod eines Haushaltsmitglieds | **nicht modelliert** (Vereinfachung) |
| § 82 Abs. 2 SGB XII | Absetzbeträge | umgesetzt |
| § 82 Abs. 3 SGB XII | Erwerbsfreibetrag 30 % des **Bruttoeinkommens**, max. 50 % der RBS 1 (281,50 EUR) | umgesetzt (Basis Brutto) |
| § 82 Abs. 3 Satz 2, Abs. 4, 5 SGB XII | WfbM-Sonderregel; Freibetrag zusätzliche Altersvorsorge (100 EUR + 30 %, max. 50 % RBS 1) | **nicht modelliert** |
| § 90 Abs. 2 Nr. 9 SGB XII + BarbetragsVO § 1 | Schonvermögen 10.000 EUR je volljähriger/alleinstehender minderjähriger Person, 500 EUR je überwiegend unterhaltener Person | umgesetzt; § 2 (Erhöhung/Herabsetzung) nicht modelliert |
| § 43 Abs. 1 SGB XII | Einsatz des Vermögens in der Grundsicherung; (§ 43 Abs. 2: Einkommensgrenze 100.000 EUR nicht modelliert) | umgesetzt |
| § 44 SGB XII | Bewilligungszeitraum, Beginn zum Monatsersten | teilweise (Roadmap Etappe 4) |
| § 35 SGB X | Begründung des Verwaltungsakts | `BescheidGenerator` |
| § 36 SGB X | Rechtsbehelfsbelehrung | `BescheidGenerator` |
| § 37 Abs. 2 SGB X | Bekanntgabefiktion am 3. Tag nach Aufgabe zur Post; `BESCHEID_DATUM` ist das **Ausstellungsdatum** | im Bescheid benannt |
| § 84 SGG | Widerspruch binnen 1 Monat; fristwahrend auch bei anderer inländischer Behörde, Versicherungsträger oder deutscher Konsularbehörde | Belehrung |
| § 87 SGG | Klage ohne Widerspruch bzw. nach Widerspruchsbescheid | Belehrung (`modus`) |
| § 88 SGG | Untätigkeitsklage nach 3 Monaten | Belehrung |
| § 73 Abs. 2 SGG | Vertretungsbefugte Bevollmächtigte | Belehrung |

Quellen: <https://www.gesetze-im-internet.de/sgb_12/> (§§ 30, 35, 43, 82, 90), <https://www.gesetze-im-internet.de/sgg/> (§§ 73, 84, 87, 88),
<https://www.gesetze-im-internet.de/sgb_10/> (§§ 35–37). Die BarbetragsVO-Beträge stammen aus der Fassung auf buzer.de
(<https://www.buzer.de/gesetz/2234/index.htm>); die BMJ-Primärseite war nicht abrufbar — **vor Produktivbetrieb gegen den amtlichen Text prüfen**.
Die Regelbedarfsstufen 2026 stützen sich auf die Regelbedarfsstufen-Fortschreibungsverordnung; ein Abgleich gegen den Bundesanzeiger steht aus.

## Datenmodell (`ddl/lmg_schema.sql`, Seed: `ddl/lmg_seed.sql`)

| Tabelle | Zweck |
|---|---|
| `BEDARFSGEMEINSCHAFT` | Vorgangskopf: Aktenzeichen (eindeutig), Hauptperson, Status aktiv/historisiert, `BUDGET_REST` (verfügbarer Restanspruch pro Monat, wird bei Auszahlung gemindert), `VERMOEGEN` (§ 90 SGB XII), `WIEDERVORLAGE_AM` (Fristen-Widget), Bankverbindung (`IBAN`, `BIC`, `KONTOINHABER`), `VERSION` (optimistisches Locking). |
| `SOZIAL_FALL` | Leistungsberechtigter: Regelbedarfsstufe 1–6, Merkzeichen G (`FLAG_MERKZEICHEN_G`), Schwangerschaft ab 12. SSW (`FLAG_SCHWANGER`, § 30 Abs. 2), Kinder im Haushalt (`ANZ_KINDER_U7`, `ANZ_KINDER_U16`, `ANZ_KINDER_MINDERJ`, § 30 Abs. 3), Warmwasser (`FLAG_WARMWASSER`), KdU (Kaltmiete `KDU_MIETE`, kalte Betriebskosten `KDU_BETRIEBSKOSTEN`, Heizung `KDU_HEIZUNG`), Einkommen (Rente/Erwerb), Absetzbeträge (`ABZ_STEUERN`, `ABZ_SV`, `ABZ_VERSICHERUNGEN`, `ABZ_ERWERBSAUSGABEN`, § 82 Abs. 2), `VERSION`. |
| `ZAHLUNG` | angeordnete Auszahlung: eindeutige `VORGANGS_NR` (Idempotenz), Zahlungsmonat `YYYY-MM`, Betrag, Status `ANGELEGT`/`BEREIT`/`UEBERGEBEN`/`STORNIERT` (ASCII-stabil), Empfängerbank (`EMPFAENGER_IBAN`, `EMPFAENGER_BIC`, `EMPFAENGER_NAME`), Sachbearbeiter & Freigebender (`SACHBEARBEITER_ID`, `FREIGEGEBEN_VON`). |
| `BESCHEID` | Bewilligungsbescheid (§ 44 SGB XII): Bescheidnummer (`BESCHEID_NR`, eindeutig), `BESCHEID_DATUM` = Datum des Bescheids (die Bekanntgabe nach § 37 SGB X ist ein eigener, nicht erfasster Zeitpunkt), Bewilligungszeitraum (`BEWILLIGUNG_VON`/`BEWILLIGUNG_BIS`, Format `YYYY-MM`, von ≤ bis), Zahlbetrag, Status (`ERTEILT`/`AUFGEHOBEN`), Erfasser. |
| `JOURNAL` | revisionssicheres Buchungsjournal: Aktionstext, Sachbearbeiter, logische BG-Referenz ohne FK (Einträge überleben Historisierung). |

IDs kommen aus den Sequences `SEQ_BG`, `SEQ_FALL`, `SEQ_ZAHLUNG`, `SEQ_BESCHEID`, `SEQ_JOURNAL`.
Die Trigger `TRG_BG_VERSION`/`TRG_FALL_VERSION` erhöhen die VERSION bei jedem
UPDATE automatisch, sofern der Aufrufer sie nicht selbst hochgesetzt hat.

## Der kritische Buchungsweg (`SozialLeistungsService.ordneAuszahlungAn`)

Eine einzige kurze Oracle-Transaktion über `TxManager.execute` (Commit/Rollback und
Wiederholung bei ORA-00054/00060/08177 übernimmt die Transaktionssteuerung):

1. **Idempotenz**: existiert die Vorgangsnummer bereits, wird die vorhandene Zahlung
   zurückgegeben und nichts gebucht (Schutz vor Doppelklick und wiederholten Requests);
   nach Erhalt der Sperre wird die Prüfung erneut wiederholt (Race gegen einen
   parallelen Request mit derselben Nummer).
2. **Pessimistische Sperre**: `SELECT … FOR UPDATE` auf die Bedarfsgemeinschaft —
   parallele Sachbearbeiter buchen nacheinander; Deadlocks wiederholt die Steuerung.
3. **Berechnung und Prüfung**: Zahlbetrag über alle SozialFälle (inkl. Vermögen);
   ≤ 0 → `KEIN_ANSPRUCH`; größer als `BUDGET_REST` → `BUDGET_ERSCHOEPFT`.
4. **SEPA-Bankdaten ermitteln und validieren** (Auftragsangaben oder Standardkonto
   der BG; ISO 13616 Modulo-97).
5. **Zahlung immer mit Status `ANGELEGT` anlegen** — das Vier-Augen-Prinzip ist
   serverseitig erzwungen; die Freigabe (`ANGELEGT` → `BEREIT`) erfolgt ausschließlich
   über den Freigabe-Endpunkt durch einen zweiten Sachbearbeiter.
6. **Restbudget mindern** (der VERSION-Trigger stößt dabei parallele Maskenbearbeiter
   korrekt auf eine KONKURRENZ-Meldung).
7. **Journaleintrag** schreiben (Vier-Augen-Prinzip: Sachbearbeiter-Kürzel).

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
| `POST /api/system/wiederanlauf` | Saga-Wiederanlauf **aller registrierten Saga-Typen** anstoßen (idempotent) *(nur Admin)* |
| `POST /api/bedarfsgemeinschaften` | Neuen Vorgang anlegen (Aktenzeichen eindeutig, Startbudget) |
| `POST /api/bedarfsgemeinschaften/{id}/sozialfaelle` | SozialFall zu einem aktiven Vorgang anlegen |
| `POST /api/kassenlauf` | Alle BEREIT-Zahlungen auf ÜBERGEBEN setzen *(nur Admin)* |
| `POST /api/zahlungen/{id}/freigabe` | Vier-Augen-Freigabe (Status ANGELEGT → BEREIT, rechnerische Feststellung) |
| `POST /api/zahlungen/{id}/storno` | BEREIT-Zahlung stornieren, Restbudget zurückbuchen *(nur Admin)* |
| `GET /api/bedarfsgemeinschaften/{id}/bescheid` | Letzten Bewilligungsbescheid der Bedarfsgemeinschaft abrufen |
| `POST /api/bedarfsgemeinschaften/{id}/bescheid` | Bewilligungsbescheid (§ 44 SGB XII) erteilen (Bescheiddatum = heute) |
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
- **Storno** (Status `BEREIT` oder `ANGELEGT`): sperrt die **Zahlungszeile**
  (`FOR UPDATE`, Race gegen parallelen Kassenlauf) und den Vorgang, führt den
  Statuswechsel als guarded Update aus (`WHERE ZAHLUNGSSTATUS IN ('ANGELEGT','BEREIT')`
  mit geprüfter Update-Zählung — 0 Zeilen → KONKURRENZ), setzt STORNIERT, bucht das
  Restbudget zurück und journalisiert — alles atomar.
- **Wiederanlauf beim Start**: `LmgServerMain` führt `SagaEngine.recover()` über
  **alle registrierten Saga-Definitionen** (Fach-Antragsverarbeitung und Demo) beim
  Hochfahren aus (zusätzlich zum Button im Adminbereich); jede Saga wird mit
  `FOR UPDATE SKIP LOCKED` geclaimt — parallele Wiederanläufe sind unschädlich.
- **Konfiguration**: volle 12-Factor-Kaskade über `LmgConfig`
  (System-Property > Umgebungsvariable > `lmg.properties` > Standard): u. a.
  `lmg.port`, `lmg.pool.type`/`lmg.pool.size`, `lmg.tasks.max`/`lmg.tasks.queue`,
  `lmg.audit.file`, `lmg.benutzer`, `lmg.session.timeout.minuten`,
  `lmg.kdu.warmwasserabzug`, `lmg.kdu.angemessenheitsgrenze`,
  `lmg.rechtsbehelf.modus`/`behoerde`/`sozialgericht`
  (Umgebungsvariablen Entsprechungen: `LMG_PORT`, `LMG_POOL_GROESSE`, `LMG_TASKS`,
  `LMG_WARTESCHLANGE`, `LMG_BENUTZER`, …).
- **Sicherheit der Webschicht** (v0.5.0): Sitzungs-Token nur aus dem
  Authorization-Header (Ausnahme: Druck-Link, da `window.open` keinen Header setzen
  kann); Sitzungen laufen nach Idle-Timeout ab; Anmelde-Drosselung nach 5
  Fehlversuchen; Request-Body auf 1 MB begrenzt; JSON-Parser mit Tiefenlimit;
  Content-Security-Policy auf allen Antworten; interne Fehlerdetails nur im
  Server-Log; alle Serverdaten werden im Frontend HTML-escaped (XSS-Schutz).

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
7. **Bescheidwesen und Saga (v0.4.0/0.5.0)**: Bewilligungsbescheid nach § 44 SGB XII
   (Bescheiddatum; Bekanntgabe nach § 37 SGB X separat) mit fallbezogener Begründung (§ 35 SGB X) und
   konfigurierbarer, vollständiger Rechtsbehelfsbelehrung (§ 36 SGB X — Widerspruch
   mit Behörde/Sitz oder direkte Klage zum Sozialgericht nach Landesrecht);
   4-stufige Antrags-Saga mit idempotenter Rückwärtskompensation (Vorgang wird
   historisiert statt gelöscht, Budget-Rückbuchung, Korrelation über VORGANGS_NR).
8. **Dokumentierte Vereinfachungen des Fachrechts (v0.5.2)**: keine Karenzzeit
   (§ 35 Abs. 1 Satz 2, 1 Jahr) und keine Absenkungsfrist (§ 35 Abs. 3, i. d. R. 6 Monate)
   bei der KdU-Angemessenheit; kein § 82 Abs. 4/5 (Aufwandsentschädigungen, Altersvorsorge),
   kein § 43 Abs. 2 (Einkommensgrenze 100.000 EUR), keine Nachzahlungen/Aufrechnung;
   kein Vermögensverbrauchs-Fortschrieb über Monate (verwertbares Vermögen mindert jeden Monat bis zur
   Meldung); § 44 Abs. 2 (Rückwirkung auf den Monatsersten) und Abs. 3 (12-Monats-
   Standardzeitraum) noch nicht automatisiert (offen in roadmap.md Etappe 4);
   Warmwasser-KdU-Abzug und Angemessenheitsgrenze sind örtlich zu parametrisieren
   (Standard: deaktiviert).
9. **Aktualisierter Blueprint (v0.5.2)**: Das ursprüngliche Blueprint-PDF (Google-Docs-Export)
   bleibt als Archiv unverändert; der fortgeschriebene Stand steht in
   `doc/SDN-sozial-LMG-Blueprint-aktualisiert-v0.5.2.md` (und `.pdf`).

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

# alternativ ohne sqlplus (setzt Fachschema zurück und spielt Seed ein):
mvn test-compile exec:java -Dexec.mainClass=de.allgemein.lmg.db.SchemaWerkzeug \
  -Dexec.classpathScope=test
# (TX_URL/TX_USER/TX_PASS müssen gesetzt sein; Argument "ohne-seed" lässt den Seed weg)

# Server starten (TX_URL/TX_USER/TX_PASS wie beim DemoMain)
mvn compile exec:java -Dexec.mainClass=de.allgemein.lmg.web.LmgServerMain
# → http://localhost:8080
```

Berechnungsregeln sind unabhängig von der Datenbank testbar: `mvn test`
(Unit-Tests ohne DB; die echten DB-Tests — `EchteDbVerifikationTest`,
`DeadlockRetryEchtTest` — laufen automatisch mit, wenn `TX_URL` gesetzt ist).
