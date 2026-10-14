// teil2.js — Teil II: Technische Dokumentation (Kapitel 13-20) + Glossar
"use strict";
const {
  P, h1, h2, h3, abs, punkt, schritt, hinweis, tabellenTitel, tabelle, abbild,
} = require("./lib");

const inhalt = [];

// ================================================================ 13 Architektur
inhalt.push(h1("13  Architekturüberblick"));
inhalt.push(h2("13.1  Leitprinzip: Die Datenbank trägt die Transaktionen"));
inhalt.push(abs("Das Projekt ersetzt einen klassischen Transaktionsmonitor durch eine Eigenentwicklung auf dem JDK. Das Leitprinzip lautet: Oracle liefert den transaktionalen Kern — ACID-Eigenschaften, Sperrverwaltung, Redo-Logging und Wiederanlauf nach Absturz kommen aus dem Datenbanksystem und werden nicht nachgebaut. Die Eigenentwicklung programmiert ausschließlich die Steuerungsschicht darüber: begrenzte Parallelität, Zeitüberwachung, Wiederholung bei Sperrkonflikten und die Orchestrierung längerer Geschäftsvorgänge als Saga. Bewusst wurden keine Frameworks eingesetzt (kein Spring, kein Applikationsserver); die Webschicht läuft auf dem in Java 21 enthaltenen HttpServer, JSON wird über eine eigene schlanke Klasse gelesen und geschrieben."));
inhalt.push(h2("13.2  Schichten und Bausteine"));
inhalt.push(abs("Die Anwendung ist in vier Schichten gegliedert (Abbildung 1 am Anfang des Handbuchs zeigt das Gesamtbild):"));
inhalt.push(punkt([{ t: "Oberfläche (webapp): ", fett: true }, { t: "Vanilla HTML/CSS/JavaScript als Single-Page-Anwendung ohne externe Abhängigkeiten; Widget-Dashboard, Vorgangsmaske, Journal, Adminbereich." }]));
inhalt.push(punkt([{ t: "Webschicht (de.allgemein.lmg.web): ", fett: true }, { t: "LmgServer auf dem JDK-HttpServer mit JSON-API und Berechtigungsprüfung; Json als eigener Parser/Writer; Benutzerverwaltung für Konten, Rollen und Sitzungs-Token." }]));
inhalt.push(punkt([{ t: "Fachschicht (de.allgemein.lmg): ", fett: true }, { t: "SozialLeistungsService mit den Fachdiensten, Leistungsberechnung als reine Funktion (ohne Datenbank, mit Unit-Tests geprüft), SepaValidator, BescheidGenerator und die DAOs." }]));
inhalt.push(punkt([{ t: "Transaktionssteuerung (de.allgemein.tx): ", fett: true }, { t: "TaskController, TxManager mit RetryPolicy, SagaEngine mit Journal und der Verbindungspool — die Kernbibliothek, die in Kapitel 14 bis 15 im Detail beschrieben wird." }]));
inhalt.push(tabellenTitel("Zentrale Bausteine der Kernbibliothek"));
inhalt.push(tabelle(
  ["Baustein", "Verantwortung"],
  [
    ["TxManager", "Führt Facharbeit in einer Oracle-Transaktion aus: Verbindung aus dem Pool, AutoCommit aus, Commit bei Erfolg, Rollback bei jedem Fehler, Wiederholung nur bei wiederholbaren Datenbank-Konflikten."],
    ["TxContext", "Repräsentiert die laufende Transaktion: Verbindung, Deadline (Start + Timeout), Status, Savepoints; prüft die Deadline zwischen Teilarbeiten."],
    ["TxOptions", "Wertobjekt: Isolationsebene (Standard READ COMMITTED), Timeout (Standard 30 s), maximale Versuche (Standard 3)."],
    ["RetryPolicy", "Klassifiziert SQLException: ORA-00054/00060/08177 und SQLState 40001/61000 gelten als wiederholbar; exponentieller Backoff mit Zufallsanteil."],
    ["TaskController", "Fester Thread-Pool mit begrenzter Warteschlange, Zeitlimit-Wachhund (Interrupt), Statistik (TaskStats)."],
    ["SagaEngine", "Führt SagaDefinition als Kette kurzer Transaktionen aus; kompensiert rückwärts; recover() beendet halbfertige Vorgänge nach Neustart."],
    ["SagaJournal", "DAO für TX_SAGA und TX_SAGA_STEP; Schrittmarker werden getrennt von den Fachtransaktionen bestätigt; eine gesperrte Kopfzeile sichert die exklusive Ausführung."],
    ["UcpConnectionPool", "Oracle Universal Connection Pool mit Validierung bei Ausleihe; ersatzweise SimpleConnectionPool (bewusst minimal)."],
    ["TxAuditLog", "Revisionssicheres Audit-Log: Ringpuffer (1.000 Einträge), Listener, optionaler Dateiausschrieb."],
    ["TxMonitoring", "JMX-MBeans für Task-Statistik und Pool-Auslastung (Domain de.allgemein.tx)."],
  ],
  [26, 74],
));

inhalt.push(h2("13.3  Web-Schicht und Fehlerabbildung"));
inhalt.push(abs("HTTP-Verarbeitung und Fach-Task-Steuerung sind getrennt begrenzt. Facharbeit läuft über den TaskController. Fachliche Eingabefehler liefern 400, fehlende Anmeldung 401, fehlende Rechte 403, unbekannte Ressourcen 404, eine falsche HTTP-Methode 405 und Überlast 503 mit UEBERLAST und Retry-After. Technische Fehler liefern 500. Zahlen für ganzzahlige Felder müssen exakt ganzzahlig und im Wertebereich sein; Wahrheitswerte müssen echte JSON-Booleans sein. Der Request-Body wird für zulässige Transaktionswiederholungen zwischengespeichert."));

// ================================================================ 14 Transaktionssteuerung
inhalt.push(h1("14  Die Transaktionssteuerung im Detail"));
inhalt.push(h2("14.1  Kurze Transaktionen als Grundbaustein"));
inhalt.push(abs("Alle Fachdienste — vom Speichern einer Maske bis zur Auszahlungsanordnung — laufen als kurze Transaktion über TxManager.execute. Der Ablauf (Abbildung 19): Verbindung aus dem Pool holen, AutoCommit ausschalten, Isolation setzen, die Facharbeit ausführen, bei Erfolg committen, bei jedem Fehler zurückrollen und die Verbindung in jedem Fall an den Pool zurückgeben. Fachliche Fehler brechen sofort ab; nur wiederholbare Datenbank-Konflikte führen zu einem zweiten und dritten Versuch mit steigender Wartezeit."));
inhalt.push(...abbild("d02_kurze_transaktion.png", "Ablaufdiagramm von TxManager.execute — Commit, Rollback, Retry mit Backoff"));
inhalt.push(h2("14.2  Wiederholung: RetryPolicy und ORA-Codes"));
inhalt.push(abs("Nur drei Fehlersituationen sind wiederholbar, weil sie ein vorübergehendes Sperrproblem ausdrücken — alle anderen Fehler werden nicht automatisch wiederholt:"));
inhalt.push(tabellenTitel("Wiederholbare Datenbank-Fehler (RetryPolicy)"));
inhalt.push(tabelle(
  ["Fehler", "Bedeutung", "Reaktion der Steuerung"],
  [
    ["ORA-00054", "Ressource belegt (Zeile von anderer Sitzung gesperrt)", "Rollback, Backoff, erneuter Versuch"],
    ["ORA-00060", "Deadlock erkannt — Oracle hat die eigene Sitzung als Opfer zurückgerollt", "Rollback, Backoff, erneuter Versuch (in der echten Deadlock-Testklasse verifiziert)"],
    ["ORA-08177", "Serialisierbarkeitskonflikt (nur bei SERIALIZABLE-Isolation relevant)", "Rollback, Backoff, erneuter Versuch"],
    ["SQLState 40001 / 61000", "Anbieterübergreifende Codes für Konflikt/Ressourcenlimit", "wie oben"],
  ],
  [22, 44, 34],
));
inhalt.push(abs("Die Suche nach diesen Codes durchläuft getNextException und Exception-Ursachen zyklensicher. Der Backoff startet bei 200 ms, verdoppelt sich je Fehlversuch (begrenzt auf 30 Sekunden Basisbackoff) und enthält einen Zufallsanteil bis zur Hälfte der Wartezeit, höchstens 15 Sekunden zusätzlich — dadurch entzerren sich konkurrierende Sitzungen statt sich gegenseitig erneut zu blockieren. Nach drei Versuchen bricht die Steuerung mit TxException ab."));
inhalt.push(h2("14.3  Deadline und Wachhund"));
inhalt.push(abs("Jede Transaktion erhält beim Start eine monotone Deadline auf Basis von System.nanoTime (Standard: 30 Sekunden, konfigurierbar je Aufruf über TxOptions). TxContext.checkDeadline() kann die Facharbeit zwischen Teilarbeiten aufrufen; zusätzlich prüft der TxManager vor dem Commit die Deadline — ist die Deadline bei dieser Prüfung abgelaufen, wird vor dem Commit abgebrochen. Über der Transaktionsebene wacht der TaskController: Jeder Task bekommt ein Zeitlimit; ein eigener Wachhund-Thread (tx-task-watchdog) bricht überschrittene Tasks mit Thread-Interrupt ab. Der Interrupt lässt den blockierenden JDBC-Aufruf fehlschlagen, wodurch die Transaktion zurückrollt. Ein Interrupt ist keine Garantie, dass beliebiger Fremdcode sofort endet. JDBC-Verbindungs- und Lesezeitlimits ergänzen den Wachhund. Beim Herunterfahren werden auch wartende Futures abgebrochen und genau einmal in der Task-Statistik abgeschlossen."));
inhalt.push(tabellenTitel("Standardwerte der Transaktionssteuerung"));
inhalt.push(tabelle(
  ["Parameter", "Standardwert", "Bedeutung"],
  [
    ["Isolationsebene", "READ COMMITTED", "Oracle-Standard; kein Serialisierungs-Overhead im Normalbetrieb"],
    ["Transaktions-Timeout", "30 Sekunden", "Deadline je Transaktion; danach kein Commit mehr"],
    ["Maximale Versuche", "3", "Erstversuch plus zwei Wiederholungen bei Sperrkonflikten"],
    ["Backoff-Start", "200 ms", "Wartezeit nach dem ersten Fehlversuch, verdoppelnd"],
    ["Task-Zeitlimit", "30.000 ms", "Wachhund-Interrupt für den ganzen Task"],
    ["Task-Parallelität", "8", "Größe des Thread-Pools (konfigurierbar, Kapitel 19)"],
    ["Warteschlange", "100", "Begrenzte Auftragswarteschlange; voll → Abweisung statt Überlastung"],
  ],
  [26, 22, 52],
));
inhalt.push(h2("14.4  Verbindungspools"));
inhalt.push(abs("Die Fachschicht hängt ausschließlich an der Schnittstelle getConnection/release — der Pool ist damit austauschbar, ohne eine Fachklasse zu berühren. Für den Betrieb steht UcpConnectionPool auf dem Oracle Universal Connection Pool: Verbindungvalidierung bei Ausleihe (validateConnectionOnBorrow), Mindestgröße 1, Anfangsbestand min(2, max), Maximum aus der Konfiguration (Standard 24 bei acht Tasks; bis zu drei Verbindungen pro aktivem Saga-Task). Der mitgelieferte SimpleConnectionPool (Semaphor plus Warteschlange) dient Demozwecken; für Produktion sind UCP oder HikariCP vorgesehen."));
inhalt.push(h2("14.5  Audit-Log und Monitoring"));
inhalt.push(abs("TxAuditLog schreibt für jede Transaktion und Saga ein Ereignis mit Zeitstempel, Thread, Kategorie (TX.COMMIT, TX.ROLLBACK, TX.RETRY, SAGA.*), Id, Versuch, Dauer und Fehler. Die Einträge landen in einem thread-sicheren Ringpuffer mit 1.000 Plätzen (für Diagnose und Oberfläche) und parallel in einer Datei (Standard lmg_audit.log, UTF-8, anhängend). Zeilenformat: Zeitstempel [Thread] KATEGORIE.EREIGNIS id=… attempt=n duration=… error=\"…\". TxMonitoring registriert zusätzlich JMX-MBeans (de.allgemein.tx:type=TaskController und :type=ConnectionPool) mit aktiven, erfolgreichen, fehlgeschlagenen, abgebrochenen und abgewiesenen Tasks sowie Pool-Auslastung — anschließbar an jede JMX-Konsole oder an Prometheus-Brücken."));

// ================================================================ 15 Saga
inhalt.push(h1("15  Saga-Muster, Journal und Wiederanlauf"));
inhalt.push(h2("15.1  Warum kein großes Rollback"));
inhalt.push(abs("Ein Bewilligungsvorgang besteht aus Fachentscheidungen, die jeweils für sich gültig sind: Ein angelegter Vorgang ist ein Fakt, ein erteilter Bescheid ein anderer. Würde man den ganzen Ablauf in eine einzige lange Transaktion spannen, würde man Zeilen minutenlang sperren, die Datenbank belasten und bei jedem kleinen Fehler alles verlieren. Das Saga-Muster zerlegt den Ablauf deshalb in kurze Transaktionen mit vorgesehenen Kompensationen: Jeder Schritt hat eine als Fachlogik implementierte Rückgängig-Aktion (eigenen Vorgang historisieren, eigenen Bescheid aufheben, eigene Zahlung stornieren). Scheitert Schritt 3, laufen die Kompensationen von Schritt 2 und 1 rückwärts. Das entspricht fachlich der Aufhebung eines Bescheids — nicht technisch dem Zurückrollen einer Datenbank."));
inhalt.push(h2("15.2  Die vier Schritte der Antrags-Saga"));
inhalt.push(abs("Der Antragsprozess (Klasse AntragsSagaProzess, Typ SGB_XII_ANTRAGSVERARBEITUNG) ist deklarativ definiert — je Schritt Aktion und Kompensation:"));
inhalt.push(tabellenTitel("Schritte der Antrags-Saga mit Aktion und Kompensation"));
inhalt.push(tabelle(
  ["Schritt", "Aktion (eigene Transaktion)", "Kompensation"],
  [
    ["1 · Vorgang erfassen", "Bedarfsgemeinschaft und ersten SozialFall anlegen", "Nur den selbst angelegten Vorgang historisieren; Zuordnung über Saga-Marker"],
    ["2 · Bedarfsprüfung", "SGB-XII-Berechnung; bei Zahlbetrag ≤ 0 Abbruch „Kein Leistungsanspruch“", "Journaleintrag „zurückgenommen“ — es gibt nichts Persistiertes zu zurückrollen"],
    ["3 · Bescheid erteilen", "BESCHEID mit Nummer BESCH-<Aktenzeichen>-<Jahr>-<ID> und Status ERTEILT anlegen", "Nur den über den Saga-Marker zugeordneten Bescheid auf AUFGEHOBEN setzen + Journal"],
    ["4 · Erstauszahlung anweisen", "Zahlung ANGELEGT anlegen (Vorgangsnummer SAGA-<UUID>), Restbudget mindern", "Zahlung auf STORNIERT setzen, Restbudget zurückbuchen + Journal"],
  ],
  [20, 42, 38],
));
inhalt.push(h2("15.3  Journal-Tabellen und Zustände"));
inhalt.push(abs("Die SagaEngine legt Kopf und Schrittzeilen in TX_SAGA und TX_SAGA_STEP an. Während der Ausführung hält sie eine exklusive Sperre auf der Kopfzeile. Fachtransaktionen und dauerhaft bestätigte IN_FLIGHT-/DONE-Schrittmarker laufen über weitere Verbindungen. Bei einem Fehler wird FAILED vor der Kompensation dauerhaft festgeschrieben; die Kompensation benötigt erneut die exklusive Übernahme. Hat inzwischen eine Recovery übernommen, startet die ursprüngliche Instanz keine zweite parallele Kompensation. Während notwendiger Fehlerbereinigung wird ein vorhandenes Interrupt-Flag kontrolliert aufgehoben und danach wiederhergestellt."));
inhalt.push(punkt([{ t: "Zustände der Saga: ", fett: true }, { t: "RUNNING → COMPLETED im Erfolgsweg; RUNNING → FAILED → COMPENSATED im Fehlerweg; scheitert die Kompensation, bleibt FAILED. COMPENSATING ist ein weiter akzeptierter Altzustand." }]));
inhalt.push(punkt([{ t: "Zustände je Schritt: ", fett: true }, { t: "PENDING → IN_FLIGHT → DONE → (bei Fehler) COMPENSATED. IN_FLIGHT ermöglicht die Klärung eines ungewissen Commit-Ausgangs." }]));
inhalt.push(punkt([{ t: "Kompensationen laufen unter dem Kennzeichen „saga-engine“ ", fett: true }, { t: "und sind bewusst idempotent gebaut: zustandsabhängige SQL-Updates, die mehrfach ausgeführt denselben Zustand erzeugen — „Bescheid aufheben, falls ERTEILT“ ist zweimal genauso richtig wie einmal." }]));
inhalt.push(h2("15.4  Wiederanlauf (recover)"));
inhalt.push(abs("Beim Serverstart wird recover() ausgeführt; ein administrativer Wiederanlauf berücksichtigt zusätzlich die konfigurierte Inaktivitätsgrenze. FOR UPDATE SKIP LOCKED verhindert die Übernahme einer noch von einer anderen Instanz ausgeführten Saga. Der Wiederanlauf verarbeitet DONE- und IN_FLIGHT-Schritte konservativ rückwärts und überspringt bereits kompensierte Schritte. Journal-Zeitwerte und Altersvergleiche verwenden dieselbe Datenbank-Server-Wanduhr (SYSTIMESTAMP beziehungsweise CAST(SYSTIMESTAMP AS TIMESTAMP)); die Session-Zeitzone beeinflusst den Vergleich nicht mehr. Ein Zeitzonen- oder Sommerzeitwechsel der DB-Host-Uhr kann das Alter weiterhin verzerren. Historisch aus einer fremden Zeitbasis importierte TIMESTAMP-Werte sind nicht sicher rekonstruierbar; eine echte absolute UTC-Altersemantik erfordert eine datenbewusste Migration."));

// ================================================================ 16 Datenmodell
inhalt.push(h1("16  Datenmodell und Persistenz"));
inhalt.push(h2("16.1  Fachschema"));
inhalt.push(abs("Das Fachschema liegt in ddl/lmg_schema.sql mit Demobestand in ddl/lmg_seed.sql. Alle IDs kommen aus Sequenzen (SEQ_BG, SEQ_FALL, SEQ_ZAHLUNG, SEQ_BESCHEID, SEQ_JOURNAL, jeweils START WITH 1000)."));
inhalt.push(tabellenTitel("Fachtabellen im Überblick"));
inhalt.push(tabelle(
  ["Tabelle", "Inhalt und wichtige Spalten"],
  [
    ["BEDARFSGEMEINSCHAFT", "Vorgangskopf: AKTENZEICHEN (eindeutig, VARCHAR2 50), HAUPTPERSON_NAME, STATUS_AKTIV (0/1), BUDGET_REST NUMBER(10,2) ≥ 0, WIEDERVORLAGE_AM, IBAN/BIC/KONTOINHABER, VERSION (optimistisches Locking)."],
    ["SOZIAL_FALL", "Leistungsberechtigter: BG_ID (FK), REGELBEDARFSSTUFE 1–6, FLAG_MERKZEICHEN_G, FLAG_WARMWASSER, KDU_MIETE/KDU_BETRIEBSKOSTEN/KDU_HEIZUNG, EINKOMMEN_RENTE/EINKOMMEN_ERWERB (alle ≥ 0), VERSION."],
    ["ZAHLUNG", "Angeordnete Auszahlung: VORGANGS_NR (eindeutig — Idempotenz), ZAHLUNGSMONAT JJJJ-MM, AUSZAHLUNGSBETRAG > 0, ZAHLUNGSSTATUS (ANGELEGT/BEREIT/UEBERGEBEN/STORNIERT, ASCII-stabil per CHECK), SACHBEARBEITER_ID, FREIGEGEBEN_VON, EMPFAENGER_IBAN/BIC/NAME."],
    ["BESCHEID", "Bewilligungsbescheid: BESCHEID_NR (eindeutig), BESCHEID_DATUM, BEWILLIGUNG_VON/BIS (JJJJ-MM), ZAHLBETRAG, STATUS (ERTEILT/AUFGEHOBEN), ERSTELLT_VON."],
    ["JOURNAL", "Revisionssicher: AKTION_TEXT (500), SACHBEARBEITER_ID, ERSTELLT_AM; BG_ID bewusst ohne Fremdschlüssel, damit Einträge Historisierung und Löschung überdauern."],
  ],
  [26, 74],
));
inhalt.push(h2("16.2  Optimistisches Locking mit VERSION-Spalten und Triggern"));
inhalt.push(abs("BEDARFSGEMEINSCHAFT und SOZIAL_FALL tragen eine VERSION-Spalte. Updates laufen als UPDATE … WHERE ID = ? AND VERSION = ?: Null betroffene Zeilen heißt, ein anderer Bearbeiter war schneller — die Fachschicht wirft KONKURRENZ, die Transaktion rollt zurück, die Oberfläche lädt den Neustand. Zwei Datenbank-Trigger (TRG_BG_VERSION, TRG_FALL_VERSION) erhöhen die Version bei jedem Update automatisch, sofern der Aufrufer sie nicht selbst erhöht hat; dadurch können parallele Buchungswege (Zahlbarmachung mindert das Restbudget) und Maskenbearbeitung nicht unentdeckt aneinander vorbeischreiben."));
inhalt.push(h2("16.3  Sperrverhalten im kritischen Buchungsweg"));
inhalt.push(abs("Ergänzend zum optimistischen Locking nutzt die Zahlbarmachung eine pessimistische Zeilensperre: SELECT … FOR UPDATE auf die Bedarfsgemeinschaft. Parallele Anordnungen auf denselben Vorgang serialisieren sich damit von selbst; ein eventuell entstehender Deadlock wird von der RetryPolicy erkannt und wiederholt (Kapitel 14.2, echte Verifikation durch die Testklasse DeadlockRetryEchtTest gegen Oracle)."));
inhalt.push(h2("16.4  Saga-Journal-Schema"));
inhalt.push(abs("Die Kernschema-Tabellen liegen in ddl/oracle_schema.sql: TX_SAGA (SAGA_ID VARCHAR2(36) als Schlüssel, SAGA_TYPE, STATE mit RUNNING/COMPLETED/FAILED/COMPENSATING/COMPENSATED, PAYLOAD, Zeitstempel) und TX_SAGA_STEP (Schlüssel SAGA_ID + IDX, NAME, STATE PENDING/IN_FLIGHT/DONE/COMPENSATED, Fremdschlüssel zur Kopfzeile, Index auf den Zustand)."));

// ================================================================ 17 Zahlungsworkflow
inhalt.push(h1("17  Der kritische Buchungsweg im Detail"));
inhalt.push(abs("Die Auszahlungsanordnung (SozialLeistungsService.ordneAuszahlungAn) ist der sicherheitskritischste Weg der Anwendung. Er kombiniert sechs Schritte zu einer einzigen kurzen Transaktion — entweder alles oder nichts:"));
inhalt.push(schritt(1, [{ t: "Auftragsprüfung: ", fett: true }, { t: "Vorgangsnummer (max. 100 Zeichen), Kennung (max. 50) und Zahlungsmonat im Format JJJJ-MM werden vor Transaktionsbeginn geprüft (VALIDATION)." }]));
inhalt.push(schritt(2, [{ t: "Idempotenz: ", fett: true }, { t: "Existiert die Vorgangsnummer bereits, wird die vorhandene Zahlung unverändert zurückgegeben (Flag „bereits gebucht“) — Doppelklicks und wiederholte Requests buchen nie zweimal. Die Eindeutigkeit erzwingt zusätzlich ein UNIQUE-Constraint in der Tabelle ZAHLUNG." }]));
inhalt.push(schritt(3, [{ t: "Pessimistische Sperre: ", fett: true }, { t: "SELECT … FOR UPDATE auf die Bedarfsgemeinschaft; historisierte Vorgänge brechen hier ab. Parallele Buchungen laufen nacheinander; ORA-00060-Deadlocks wiederholt die Steuerung." }]));
inhalt.push(schritt(4, [{ t: "Berechnung und Prüfung: ", fett: true }, { t: "Zahlbetrag frisch über alle SozialFälle berechnen; ≤ 0 → KEIN_ANSPRUCH, über Restbudget → BUDGET_ERSCHOEPFT. Sobald Bescheide vorhanden sind, werden eindeutige Monatsdeckung und exakter gespeicherter Monatsbetrag geprüft; bei Abweichung wird nicht gebucht." }]));
inhalt.push(schritt(5, [{ t: "SEPA-Prüfung und Zahlung: ", fett: true }, { t: "Empfängerdaten aus Auftrag, Vorgang oder Hauptperson; vorhandene IBAN mit Modulo-97, vorhandene BIC per Formatprüfung validiert (Kapitel 18.3); Zahlung entsteht immer im Status ANGELEGT, das Restbudget mindert sich." }]));
inhalt.push(schritt(6, [{ t: "Journal: ", fett: true }, { t: "revisionssicherer Eintrag mit der angemeldeten Kennung; erst danach committet die Transaktion." }]));
inhalt.push(...abbild("d04_zahlungsworkflow.png", "Statusweg einer Zahlung: Vier-Augen-Freigabe, Kassenlauf, Storno und Restbudget-Rückbuchung"));
inhalt.push(hinweis("Warum Restbudget?", "BUDGET_REST ist der rechnerische Deckel der Bewilligung: Jede Anordnung mindert ihn, jedes Storno bucht zurück. Damit kann die Sachbearbeitung ein Bewilligungsvolumen verwalten, auch wenn mehrere Monate und Teilbeträge zu-fließen. Die VERSION-Trigger stellen sicher, dass gleichzeitige Maskenbearbeiter die Budgetminderung nicht übersehen können."));

// ================================================================ 18 Sicherheit
inhalt.push(h1("18  Sicherheit: Anmeldung, Rollen und SEPA"));
inhalt.push(h2("18.1  Sitzungen und Token"));
inhalt.push(abs("Die Anmeldung erzeugt eine UUID als Sitzungs-Token und legt sie mit Kennung und Rolle in einem serverseitigen Speicher ab (Klasse Benutzerverwaltung). Jede weitere Anfrage trägt den Authorization-Header „Bearer <Token>“; die Druckansicht erhält ausnahmsweise ein eigens dafür erzeugtes Einmal-Token im URL-Parameter (POST /api/drucktoken, 60 Sekunden gültig, verbraucht sich beim ersten Abruf), damit das Druckfenster ohne Header auskommt, ohne dass das Sitzungs-Token in Adressen oder Browserverlauf gerät. Unbekannte oder abgelaufene Token führen zu HTTP 401 mit dem Code ANMELDUNG. Jede Einsicht in vollständige Falldaten — Vorgangsdetail, Bescheiddaten, Druckansicht — wird protokolliert: Das Journal hält fest, wer wann eingesehen hat (Zugriffsprotokoll nach § 35 SGB I / Art. 32 DSGVO); wiederholtes Aufrufen derselben Maske innerhalb einer Minute wird zu einem Eintrag zusammengefasst. Übersichtslisten (Vorgänge, Zahlungen) zeigen bewusst keine Bankverbindungsdaten — IBAN und BIC erscheinen nur im Detail des jeweiligen Falls. Konten kommen aus der Umgebung LMG_BENUTZER (Format kennung:passwort:rolle;…) oder aus den Demo-Standards; für den produktiven Betrieb ist an dieser Stelle ein Verzeichnisdienst hinter HTTPS vorgesehen — die Sitzungsverwaltung ist bewusst klein gehalten und austauschbar."));
inhalt.push(...abbild("d06_anmeldung_rolen.png", "Anmelde- und Berechtigungsfluss: Token-Prüfung, 401 ohne Sitzung, 403 ohne Admin-Rolle"));
inhalt.push(h2("18.2  Serverseitige Berechtigungsprüfung"));
inhalt.push(abs("Admin-pflichtige Endpunkte (alles unter /api/system/, POST /api/kassenlauf, POST /api/zahlungen/{id}/storno) prüfen die Rolle je Anfrage serverseitig; ohne Admin-Rolle antwortet die Schnittstelle mit 403 und dem Code BERECHTIGUNG. Die Kennung für das Journal kommt ausschließlich aus der Sitzung — Angaben im Anfragekörper werden ignoriert. Das Vier-Augen-Prinzip ist rechnerisch festgestellt: Die Freigabe prüft Erfasser gegen Freigebenden und wehrt die Identität ab. Einzige Ausnahme ist die Rolle „admin“ — eine Admin-Kennung darf die eigene Anordnung ausnahmsweise selbst feststellen (Sonderrolle über die Rolle, nicht über eine bestimmte Kennung)."));
inhalt.push(h2("18.3  SEPA-Validierung nach ISO 13616"));
inhalt.push(abs("Der SepaValidator prüft vorhandene IBAN und BIC nach den implementierten Format- und Prüfzifferregeln und normalisiert die Eingaben (Leerzeichen entfernt, Großbuchstaben):"));
inhalt.push(punkt([{ t: "IBAN: ", fett: true }, { t: "Format nach ISO 13616 (Ländercode A–Z, Prüfziffer, 11 bis 30 alphanumerische Zeichen; Gesamtlänge 15–34), länderspezifische Längenprüfung (DE 22, AT 20, CH 21) und die Modulo-97-Prüfziffer nach ISO 7064: Die ersten vier Zeichen wandern ans Ende, alle Zeichen werden numerisch kodiert (A=10 … Z=35), und die entstehende große Zahl muss bei Division durch 97 den Rest 1 lassen." }]));
inhalt.push(punkt([{ t: "BIC nach ISO 9362: ", fett: true }, { t: "8 oder 11 Zeichen — vierstelliger Bankcode, zweistelliger Ländercode, zweistelliger Ort, optional dreistellige Filiale." }]));
inhalt.push(punkt([{ t: "Hilfsfunktionen: ", fett: true }, { t: "Normalisierung und die 4er-Block-Formatierung für Bescheide und Druckansicht." }]));
inhalt.push(abs("Ungültige Angaben brechen die Anordnung mit VALIDATION und einer klaren Fehlermeldung ab — Leere Bankdaten bleiben im Demo-Modell zulässig. Der Kassenlauf erzeugt keine SEPA-Datei und führt keine Überweisung bei einer Bank aus."));

// ================================================================ 19 Konfiguration und Betrieb
inhalt.push(h1("19  Konfiguration, Start und Qualitätssicherung"));
inhalt.push(h2("19.1  Externe Konfiguration (12-Factor)"));
inhalt.push(abs("Die Anwendung liest ihre Konfiguration über LmgConfig in einer festen Prioritätskaskade: Java-System-Properties (-D…) schlagen Umgebungsvariablen, diese schlagen die Datei lmg.properties im Arbeitsverzeichnis, und erst dann gelten die Standardwerte. Plausibilitätsverletzungen (Port außerhalb 1–65535, Größen < 1) brechen den Start mit klarer Meldung ab."));
inhalt.push(abs("Konfigurationsdateien werden strikt als UTF-8 gelesen; ein UTF-8-BOM wird unterstützt. Eine vorhandene unlesbare oder beschädigte Datei führt zum Startabbruch, statt still Defaults oder Teilwerte zu verwenden. Pooltyp und Zahlenwerte werden geprüft. Scheitert der technische Saga-Wiederanlauf, startet der Server keinen HTTP-Listener und meldet keinen erfolgreichen Start; bereits geöffnete Ressourcen werden geschlossen."));
inhalt.push(tabellenTitel("Konfigurationsschlüssel und Standardwerte"));
inhalt.push(tabelle(
  ["Schlüssel (lmg.properties)", "Umgebungsvariable", "Standard", "Bedeutung"],
  [
    ["tx.url", "TX_URL", "jdbc:oracle:thin:@//localhost:1921/o19c1p1", "JDBC-Verbindungsziel"],
    ["tx.user / tx.pass", "TX_USER / TX_PASS", "tx_demo / tx_demo", "Datenbank-Konto"],
    ["lmg.port", "LMG_PORT", "8080", "HTTP-Port der Oberfläche"],
    ["lmg.host", "LMG_HOST", "127.0.0.1", "Bindeadresse; Demo-Konten außerhalb Loopback verweigert"],
    ["lmg.pool.type", "LMG_POOL_TYP", "ucp", "Verbindungspool (ucp oder simple)"],
    ["lmg.pool.size", "LMG_POOL_GROESSE", "24", "Maximale Poolgröße"],
    ["lmg.tasks.max", "LMG_TASKS", "8", "Parallelität der Task-Steuerung"],
    ["lmg.tasks.queue", "LMG_WARTESCHLANGE", "100", "Länge der Auftragswarteschlange"],
    ["lmg.audit.file", "LMG_AUDIT_FILE", "lmg_audit.log", "Audit-Datei; leere Konfigurationswerte verwenden den Default"],
    ["lmg.benutzer", "LMG_BENUTZER", "Demo-Konten", "Benutzerkonten kennung:passwort:rolle;…"],
    ["lmg.session.timeout.minuten", "LMG_SESSION_TIMEOUT", "60", "Inaktivitätsgrenze einer Sitzung in Minuten"],
    ["lmg.db.connectTimeoutMs", "LMG_DB_CONNECT_TIMEOUT_MS", "15000", "JDBC-Verbindungsaufbau in Millisekunden"],
    ["lmg.db.readTimeoutMs", "LMG_DB_READ_TIMEOUT_MS", "120000", "JDBC-Lesezeitlimit in Millisekunden"],
    ["lmg.kdu.warmwasserabzug", "LMG_KDU_WARMWASSERABZUG", "0", "Anteil zentraler Heizkosten; 0.075 bedeutet 7,5 %"],
    ["lmg.kdu.angemessenheitsgrenze", "LMG_KDU_ANGEMESSENHEITSGRENZE", "0", "Gesamtgrenze je BG in EUR; 0 deaktiviert"],
  ],
  [20, 24, 26, 30],
));
inhalt.push(abs("Der interne JDBC-Fallback zeigt auf die Zweitumgebung Oracle 19c unter Port 1921. Die mitgelieferte lmg.properties und start.bat verwenden die empfohlene Entwicklerdatenbank im Docker-Container lmg-oracle unter jdbc:oracle:thin:@//localhost:1521/FREEPDB1. Eine Umgebungsvariable oder Java-Property kann beide Angaben überschreiben. Vor jedem Test das tatsächliche Ziel kontrollieren."));
inhalt.push(abs("HTTP-Zeitlimits werden vor dem ersten JDK-HttpServer gesetzt. Die JVM-Properties sun.net.httpserver.maxReqTime, sun.net.httpserver.maxRspTime und sun.net.httpserver.idleInterval werden vom verwendeten JDK in Sekunden interpretiert; Defaults sind 30, 120 und 30. jdk.httpserver.maxConnections ist standardmäßig 256. Eigene Werte müssen innerhalb der geprüften Grenzen liegen: Anfrage 1–300 s, Antwort 1–600 s, Leerlauf 1–300 s, Verbindungen 16–4096. Ungültige oder unbeschränkte Werte verhindern den Start. Diese Properties werden als -D-Optionen gesetzt und gehören nicht zur LmgConfig-Kaskade."));
inhalt.push(h2("19.2  Start und Wiederanlauf"));
inhalt.push(abs("Für die Entwicklung startet start.bat die Umgebung bequem: Datenbankcontainer bei Bedarf, Kompilierung, Serverstart und Browseröffnung; start.bat demo führt die Saga-Demo aus. Der Serveraufruf ist „mvn compile exec:java -Dexec.mainClass=de.allgemein.lmg.web.LmgServerMain“. Beim Hochfahren führt der Server die Saga-Wiederanlauf-Funktion aus und protokolliert den Stand im Audit-Log. Sitzungen liegen im Speicher: Nach einem Neustart melden sich die Bearbeiter neu an; Daten sind davon unberührt."));
inhalt.push(h2("19.3  Qualitätssicherung"));
inhalt.push(abs("Die Qualitätssicherung umfasst JUnit-Tests im Hauptprojekt und in testsweet, echte Oracle-Regressionen, handgerechnete Goldwerte, unabhängige Referenzrechnungen, Simulatorläufe und eine zusätzliche GLM-Stichprobe. Ein grünes mvn test ohne TX_URL überspringt Datenbanktests und belegt die Datenbankschicht nicht. Maßgeblich sind die aktuellen Surefire-Belege mit Test-, Fehler- und Übersprungen-Zahlen sowie die Bindung an Quell- und Buildstand. Historische Testzahlen werden nicht als aktueller Nachweis übernommen. Kapitel 21 beschreibt die gesamte Zertifikatskette."));

// ================================================================ 20 API-Referenz
inhalt.push(h1("20  API-Referenz"));
inhalt.push(abs("Die JSON-API folgt festen Konventionen: Authentifizierung per Bearer-Token (401 ohne gültige Sitzung), fachliche Fehler als 400 mit {code, message}, unbekannte Ressourcen als 404, technische Fehler als 500. Admin-pflichtige Endpunkte antworten mit 403 (BERECHTIGUNG), wenn die Rolle nicht passt."));
inhalt.push(tabellenTitel("Endpunkte der JSON-API"));
inhalt.push(tabelle(
  ["Endpunkt", "Methode", "Rolle", "Wirkung"],
  [
    ["/api/anmeldung", "POST", "—", "Anmeldung; liefert Token, Kennung und Rolle"],
    ["/api/abmeldung", "POST", "angemeldet", "Sitzung beenden"],
    ["/api/werbin", "GET", "angemeldet", "Angemeldete Kennung und Rolle"],
    ["/api/bedarfsgemeinschaften", "GET", "angemeldet", "Fallübersicht inkl. Zahlbetrag-Vorschau"],
    ["/api/bedarfsgemeinschaften", "POST", "angemeldet", "Neuen Vorgang anlegen (Aktenzeichen eindeutig)"],
    ["/api/bedarfsgemeinschaften/{id}", "GET", "angemeldet", "Vorgangsdetail: SozialFälle, Berechnung, Zahlungen, Journal, Bescheid"],
    ["/api/bedarfsgemeinschaften/{id}", "PUT", "angemeldet", "Kopfdaten speichern (VERSION-Prüfung)"],
    ["/api/bedarfsgemeinschaften/{id}/sozialfaelle", "POST", "angemeldet", "Weiteren SozialFall anlegen"],
    ["/api/sozialfaelle/{id}", "PUT", "angemeldet", "SozialFall speichern (VERSION-Prüfung)"],
    ["/api/auszahlung", "POST", "angemeldet", "Auszahlung anordnen (Idempotenz über Vorgangsnummer)"],
    ["/api/zahlungen", "GET", "angemeldet", "Zahlungen über alle Vorgänge (Widgets)"],
    ["/api/zahlungen/{id}/freigabe", "POST", "zweite Kennung oder admin", "Freigabe ANGELEGT → BEREIT"],
    ["/api/zahlungen/{id}/storno", "POST", "admin", "Storno mit Restbudget-Rückbuchung"],
    ["/api/kassenlauf", "POST", "admin", "Alle BEREIT-Zahlungen auf ÜBERGEBEN setzen"],
    ["/api/bedarfsgemeinschaften/{id}/bescheid", "POST", "angemeldet", "Bewilligungsbescheid mit gespeichertem Monatsplan erteilen"],
    ["/api/drucktoken", "POST", "angemeldet", "Erzeugt ein 60 Sekunden gültiges Einmal-Token für den Druck-Link (verbraucht sich beim ersten Abruf)"],
    ["/api/bedarfsgemeinschaften/{id}/bescheid/druck", "GET", "angemeldet (Einmal-Druck-Token als URL-Parameter)", "Amtliche HTML-Druckansicht"],
    ["/api/journal", "GET", "angemeldet", "Journal-Einträge (Parameter limit, Standard 20)"],
    ["/api/saga/antrag", "POST", "angemeldet", "End-to-End-Antrags-Saga ausführen"],
    ["/api/system/status", "GET", "admin", "Systemdiagnose: DB, Fachschema, Komponenten, Task-Statistik"],
    ["/api/system/wiederanlauf", "POST", "admin", "Saga-Wiederanlauf anstoßen (idempotent)"],
  ],
  [36, 12, 22, 30],
));

// ================================================================ Glossar
inhalt.push(...require("./testsweet"));
inhalt.push(h1("Glossar"));
inhalt.push(tabellenTitel("Begriffe des Handbuchs"));
inhalt.push(tabelle(
  ["Begriff", "Bedeutung"],
  [
    ["Bedarfsgemeinschaft", "Vorgang der Grundsicherung mit eindeutigem Aktenzeichen, Hauptperson, Restbudget und Bankverbindung; fasst die zusammenlebenden Leistungsberechtigten zusammen."],
    ["SozialFall", "Ein Leistungsberechtigter innerhalb einer Bedarfsgemeinschaft mit Regelbedarfsstufe, Mehrbedarfen, Unterkunftskosten und Einkommen."],
    ["Regelbedarfsstufe (RBS)", "Staffelung des Regelbedarfs nach Personenkreis (Stufen 1–6, Werte 2026: 563,00 bis 357,00 EUR)."],
    ["KdU", "Kosten der Unterkunft und Heizung nach § 35 SGB XII; in der Anwendung als Kaltmiete, kalte Betriebskosten und Heizkosten erfasst."],
    ["Zahlbetrag", "Gesamtbedarf minus anrechenbarem Einkommen und noch einzusetzendem Vermögen; Basis von Auszahlung und Monatsbewilligung."],
    ["Vier-Augen-Prinzip", "Erfassung und Freigabe einer Zahlung durch verschiedene Kennungen. Die Rolle admin besitzt eine ausdrückliche Sonderberechtigung zur Selbstfreigabe."],
    ["Kassenlauf", "Betriebliche Aktion: alle freigegebenen (BEREIT) Zahlungen werden in einer Transaktion auf ÜBERGEBEN gesetzt — die fachliche Übergabe an Kasse/Zahlungsdienst."],
    ["Restbudget (BUDGET_REST)", "Verfügbarer Auszahlungsrahmen des Vorgangs; mindert sich bei Anordnung, wächst bei Storno."],
    ["Transaktionssteuerung", "Eigene Bibliothek (de.allgemein.tx), die kurze Oracle-Transaktionen steuert: Parallelität, Zeitlimit, Wiederholung bei Sperrkonflikten, Saga-Orchestrierung."],
    ["Saga", "Langer Geschäftsvorgang aus kurzen Transaktionen mit Journal und idempotenten Kompensationen; nach Absturz über recover() sauber beendbar."],
    ["Idempotenz", "Eigenschaft, mehrfach ausgeführt dasselbe Ergebnis zu erzeugen — Grundlage für Doppelklick-Schutz und gefahrlosen Wiederanlauf."],
    ["Optimistisches Locking", "Speicherschutz über VERSION-Spalten: parallele Änderung führt zu KONKURRENZ statt zum stillen Überschreiben."],
    ["Revisionssicheres Journal", "Unveränderliche Ereignisliste aller Fachaktionen mit Sachbearbeiter-Kennung; überdauert Historisierung."],
    ["SGB XII / SGB X", "Zwölftes Buch Sozialgesetzbuch (Grundsicherung im Alter und bei Erwerbsminderung) bzw. Zehntes Buch (Verwaltungsverfahren; Begründung § 35, Belehrung § 36, Bekanntgabe § 37)."],
  ],
  [26, 74],
));

module.exports = inhalt;
