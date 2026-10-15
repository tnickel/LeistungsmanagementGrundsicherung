# Teilreview Web, Berechtigungen und Schnittstellen — 2026-10-15

Prüfer: Subagent `web_review`. Dieser Teilbericht ist ein Beleg für den zentralen Gesamtbericht und enthält keine Produktionsfreigabe. Produkt-, Bestands-Test- und Konfigurationsdateien wurden nicht geändert. Alle zusätzlichen Quellen, Klassen und Ausgaben liegen ausschließlich in diesem Belegordner. Keine Oracle-Verbindung, kein Maven-Lauf, kein Simulatorlauf, kein externer Modellaufruf und keine Veröffentlichung wurden durch diesen Subagent gestartet.

## Ergebnis

Drei Fehler sind bestätigt: Die Adminpflicht beim Zahlungsstorno ist mit alternativen positiven ID-Schreibweisen umgehbar; bereits erteilte Drucktoken bleiben nach dem Logout verwendbar; bei vollständig mit aktiven Phantom-Sperren gefülltem Fehlversuchsspeicher wird für bisher nicht erfasste echte Konten keine Drosselung mehr aufgebaut. Der erste Fehler wurde über einen echten neu gestarteten HTTP-Server mit Service- und JDBC-Fakes reproduziert; die Fachservice- und SQL-Wirkung wurde ergänzend statisch verfolgt. Der zweite Fehler wurde sowohl auf Klassenebene als auch über denselben HTTP-Server reproduziert. Der dritte wurde deterministisch auf Klassenebene reproduziert; der zum Befüllen erforderliche HTTP-Durchsatz innerhalb der realen 30-Sekunden-Frist ist nicht gemessen. Eine zusätzliche frische SweetServer-Probe bestätigt den lokalen Origin-Guard, die Ablehnung wichtiger mutierender Endpunkte bei GET, die Geheimdatei-Abschirmung und die fehlende Methodenprüfung mehrerer Lesepfade. Achtzehn gezielte JSON-Grenzproben lieferten das jeweils vorab festgelegte Ergebnis. Es wurden keine bestehenden JUnit-Tests durch diesen Subagent ausgeführt. Die LLM-, Simulator- und Zertifikatsinnereien sowie Datenbanktests gehören zum zentral koordinierten Review; hier sind deren HTTP-Schnittstellen erfasst. Das bestehende Berechtigungsmodell erlaubt authentifizierten Sachbearbeitern weiterhin Zugriff auf den gesamten Fachbestand; Einsichtsprotokollierung begrenzt diesen Zugriff nicht.

## Bestätigte Befunde

### GRW-WEB-01 — Adminpflicht für Storno durch alternative ID-Schreibweisen umgehbar

- **Schwere:** KRITISCH. **Sicherheit:** DYNAMISCH BELEGT für die HTTP-Autorisierungsumgehung; STATISCH BELEGT für den anschließend erreichbaren produktiven Stornopfad.
- **Fundstellen:** `src/main/java/de/allgemein/lmg/web/LmgServer.java:209` / `:293–296` / `:478–480`; `src/main/java/de/allgemein/lmg/service/SozialLeistungsService.java:542–578`. Der historische Fix für abschließende Schrägstriche (`LmgServer.java:114–116`) betrifft dieselbe Schutzgrenze, erfasst diesen Fall aber nicht.
- **Verletzte Regel:** Die Webschicht erklärt Storno ausdrücklich für adminpflichtig; `brauchtAdmin` und der tatsächliche Router müssen denselben Pfadraum behandeln.
- **Vorbedingung:** Gültige Sitzung mit Rolle `sachbearbeiter`; ID einer `ANGELEGT`- oder `BEREIT`-Zahlung. Zahlungs-IDs erscheinen bereits in der für Sachbearbeiter freigegebenen Zahlungsliste.
- **Minimaler Ablauf:** `POST /api/zahlungen/+7/storno` mit Sachbearbeiter-Bearer. Ebenso funktionieren `%2B7` und die UTF-8-Kodierung der Vollbreitziffer `７` (`%EF%BC%97`).
- **Soll/Ist:** `/api/zahlungen/7/storno` liefert 403 und erreicht den Service nicht. `brauchtAdmin` benutzt `\d+`; die tatsächliche Bearbeitung kontrolliert nur Segmentzahl/-namen und benutzt `Long.parseLong`, das die alternativen Schreibweisen als dieselbe positive ID akzeptiert. Die drei Alternativen liefern in der Probe jeweils 200 und erreichen `storniereZahlung(7, "sb")`.
- **Wirkung:** Jeder angemeldete Sachbearbeiter kann die ausdrücklich Admins vorbehaltene Rücknahme beliebiger angelegter/bereiter Zahlungen auslösen. Der produktive Service setzt den Status auf STORNIERT und bucht Budget und dokumentierten Vermögenseinsatz zurück. Bereits übergebene Zahlungen bleiben durch den Statusguard geschützt; eine direkte beliebige Betragsänderung ist damit nicht belegt.
- **Gegenprüfung:** `SozialLeistungsService.storniereZahlung` erhält keine Rolle und prüft keine Adminberechtigung. Sperren, Statusguard und guarded Update schützen Konsistenz, nicht Autorisierung. UI-Ausblendung (`webapp/app.js:710`) schützt direkte API-Aufrufe nicht. Bestehender HTTP-Test prüft nur ASCII-ID und zusätzliche Schrägstriche (`LmgServerHttpTest.java:270–276`). Die ROUTEN-Liste wird erst im Fehlerpfad ausgewertet und validiert den erfolgreichen Handler nicht.
- **Evidenz:** `WebReviewProbe.java`, `web-probe-output.txt`. Regulärer Pfad: 403 / 0 Serviceaufrufe; `+7`: 200 / 1; `%2B7`: 200 / 2; `%EF%BC%97`: 200 / 3. Keine Datenbank wurde angesprochen.
- **Kleine Reparatur:** Pfad und ID einmal strikt kanonisch validieren und Rollenprüfung an die aufgelöste Route binden, statt unabhängige Regex- und Segmentrouter zu pflegen. Als zusätzliche Absicherung direkt im Stornohandler die Adminrolle prüfen.
- **Regression:** Sachbearbeiteranfragen mit ASCII-ID, Pluszeichen, kodiertem Plus, Unicode-Ziffern und abschließendem Slash müssen 403 oder vor Autorisierung 400/404 liefern und dürfen den Stornoservice nie erreichen; kanonischer Adminpfad muss weiterhin funktionieren.

### GRW-WEB-02 — Logout widerruft unbenutzte Drucktoken nicht

- **Schwere:** MAJOR; Umfang auf unbenutzte ausgegebene Token innerhalb von höchstens 60 Sekunden begrenzt. **Sicherheit:** DYNAMISCH BELEGT.
- **Fundstellen:** `src/main/java/de/allgemein/lmg/web/Benutzerverwaltung.java:50` (DruckToken-Datensatz), `:199–228` (Erzeugung/Verbrauch/Logout); `LmgServer.java:134–157`, `:266–278`, `:319–323`.
- **Vorbedingung:** Ein authentifizierter Benutzer erzeugt ein Drucktoken; dieses ist beim Logout noch nicht verbraucht. Der nachfolgende Aufrufer besitzt dieses Bearer-Token, etwa über den bereits erzeugten Link.
- **Minimaler Ablauf:** Drucktoken erzeugen → Sitzung abmelden → `GET /api/bedarfsgemeinschaften/99/bescheid/druck?token=<drucktoken>` ohne Authorization-Header.
- **Soll/Ist:** Nach Abmeldung ist `zuToken(session)` leer, aber das vorher ausgestellte Drucktoken liefert weiterhin den gespeicherten Benutzer; der HTTP-Druck liefert 200. Erst der zweite Abruf liefert 401.
- **Wirkung:** Die Abmeldung beendet nicht alle von der Sitzung abgeleiteten Zugriffsrechte. Innerhalb des verbleibenden Kurzzeitfensters lässt sich ein vollständiger Bescheid weiterhin abrufen. Das Token ist zusätzlich nicht auf eine BG gebunden; im derzeitigen Vollbestandsmodell erweitert dies die bereits gewährten Objektberechtigungen nicht, würde aber ein späteres Zuständigkeitsmodell umgehen, falls dieses nur bei Tokenausgabe geprüft würde.
- **Gegenprüfung:** Atomisches `remove` verhindert Replay und TTL verhindert lange Nutzung; beides ist funktionsfähig. `Cache-Control: no-store` schützt gegen Antwortcache, widerruft aber kein noch unbenutztes Token. `abmelden` entfernt ausschließlich aus `sitzungen`; `DruckToken` speichert `Benutzer` statt Herkunftssitzung. Bestehende Tests prüfen Logout und Drucktoken getrennt.
- **Evidenz:** `LOGOUT sessionValid=false issuedPrintTokenValid=true`; `PRINT_AFTER_LOGOUT attempt=1 status=200 cache=no-store`; zweiter Abruf 401 in `web-probe-output.txt`.
- **Kleine Reparatur:** Drucktoken an die ausstellende Sitzung und vorzugsweise die gewünschte BG binden; beim Verbrauch Sitzung erneut prüfen und beim Logout ihre Drucktoken invalidieren. Die API sollte dazu die Sitzungskorrelation statt eines bloßen Benutzerobjekts übergeben.
- **Regression:** Vor Logout erteiltes, noch unbenutztes Drucktoken nach Logout und nach Idle-Ablauf muss 401 liefern und darf `generiereBescheidHtml` nicht aufrufen; parallel nur ein erfolgreicher Verbrauch.

### GRW-WEB-03 — Voller Drosselungsspeicher schaltet Schutz neuer Zielkonten aus

- **Schwere:** MAJOR; verschlechtert die zugesicherte Login-Drosselung, belegt allein keine Übernahme eines Kontos. **Sicherheit:** DYNAMISCH BELEGT für den deterministischen Klassenpfad, STATISCH BELEGT für dessen öffentliche Erreichbarkeit über `/api/anmeldung`.
- **Fundstellen:** `src/main/java/de/allgemein/lmg/web/Benutzerverwaltung.java:98–151`, insbesondere `:129–135`; öffentliche Anbindung `LmgServer.java:304–310`.
- **Vorbedingung:** Die 1024 Fehlversuchseinträge sind aktive Sperren, und das angegriffene echte Konto hat bisher keinen Eintrag. Um dies im Realbetrieb zu erzeugen, müssen 5120 falsche Anmeldungen für 1024 Phantomkennungen so eintreffen, dass die Sperren beim Zielangriff noch aktiv sind. Der konkrete HTTP-Durchsatz wurde nicht gemessen; die Klasse wurde mit fixer Uhr geprüft.
- **Ablauf:** Je fünf falsche Anmeldungen für `phantom0` bis `phantom1023`; danach 30 falsche Anmeldungen für `target`.
- **Soll/Ist:** Nach fünf Fehlversuchen muss auch `target` gesperrt werden. Bei voller Map entfernt die Aufräumlogik zu Recht keine aktiven Sperren, aber `registriereFehlversuch` kehrt dann ohne Registrierung neuer Schlüssel zurück. Alle 30 Zielversuche liefern weiterhin „Kennung oder Passwort ist falsch“, keiner „Zu viele …“.
- **Wirkung:** Bei Sättigung werden bisher nicht erfasste reale Konten überhaupt nicht mehr gedrosselt; der Zustand ist ohne gültige Anmeldung erzeugbar. Eine bereits vorhandene aktive Sperre bleibt bestehen. Der Schutz kann durch wiederholtes Befüllen aufrechterhalten werden, sofern der dafür nötige Durchsatz möglich ist.
- **Gegenprüfung:** Schlüsselbegrenzung, atomisches `compute` und Schutz bestehender Sperren funktionieren, verhindern diesen `fail open`-Pfad aber nicht. `aktiveSperreUeberlebtFlutMitPhantomKennungen` legt zuerst die Zielkontosperre an und erzeugt nur einen Fehlversuch je Phantom; er prüft deshalb einen anderen Fall.
- **Evidenz:** `THROTTLE_SATURATION wrongPasswordAnswers=30 throttledAnswers=0` in `web-probe-output.txt`; separate Benutzerverwaltung ohne HTTP-/DB-Nebenwirkung.
- **Kleine Reparatur:** Schutzstatus real konfigurierter Konten unabhängig von der begrenzten Phantommap führen; alternativ bei Sättigung global begrenzen/geschlossen ablehnen. Keine aktive Sperre durch neue Phantomkennungen verdrängen.
- **Regression:** Volle Map aus aktiven unbekannten Kennungen, anschließend zuerst unregistriertes reales Konto angreifen; spätestens der sechste Versuch muss gedrosselt sein. Zusätzlich dauerhafte Speichergrenze und bestehende Sperren assertieren.

## Abdeckung und Grenzen

| Prüffeld | Status | Evidenz und Grenze |
|---|---|---|
| LmgServer-Endpunkt-/Rollenmatrix | GEPRÜFT | Alle Handler, Rolle, Parser und Fehlerzuordnung statisch erfasst; gezielte frische HTTP-Fakes für Autorisierungsumgehung und Logout/Druck. Kein vollständiger neuer HTTP-Regressionslauf. |
| SweetServer-Endpunktmatrix | GEPRÜFT | Alle switch-Zweige und Dateipfade statisch; gezielte echte Serverprobe ohne Laufstart. Export-/LLM-/Suite-Innereien zentral. |
| Sessions, Logout, Drucktoken, Drosselung | GEPRÜFT | Vollständige Klassenpfade und gezielte deterministische Gegenproben; reale Durchsatzlast bei 30-Sekunden-Sperren nicht gemessen. |
| Objektberechtigungen/Need-to-know | GEPRÜFT | Kein Zuständigkeits-/Team-/Objektfilter vorhanden; Protokollierung erkennbar. Eignung der organisatorischen Vollbestandsannahme nicht nachgewiesen. |
| JSON-Syntax und Parsergrenzen | TEILWEISE GEPRÜFT | Alle Parserpfade gelesen, 18 gezielte neue Proben; bestehende Tests zentral. Kein Fuzzer, keine allgemeine RFC-Konformitätsbescheinigung. |
| Requestgrößen und Retry-Body | GEPRÜFT (statisch) | LMG 1.000.000 Bytes, Sweet 1.048.576; pro-Exchange-Cache plus finally-remove in LMG. Bestehende Tests vorhanden, nicht erneut durch diesen Subagent ausgeführt. |
| XSS/Frontend/Druckkontexte | TEILWEISE GEPRÜFT | Serverdaten an untersuchten innerHTML-Stellen escaped oder numerisch/Enum; LLM-Findings via textContent/createTextNode. Kein browsergestützter kompletter Maskentest, keine Inhalts-Security-Fuzzprobe. |
| CSP/no-store/nosniff, Origin/Host | TEILWEISE GEPRÜFT | LMG Headerpfade statisch; Sweet Origin dynamisch abgewiesen, JSON-Antworten no-store; Sweet CSP/nosniff fehlen. Browser-Clickjacking nicht dynamisch geprüft. |
| Pfadzugriffe / Geheimdateien | GEPRÜFT (ausgewählte Gegenproben) | Sweet Positivliste, normalize/startsWith und toRealPath für statische Fallbacks; Geheim-/Traversalpfade dynamisch 404. Kein freier HTTP-Dateiparameter. |
| SQL-Injection im Webeingang | TEILWEISE GEPRÜFT | IDs numerisch, Fachwerte an Service weitergereicht; DAO-SQL gehört zum zentralen/DB-Teilreview. |
| Bootstrap, Konfiguration, Recovery-Startgrenze | TEILWEISE GEPRÜFT | Main/config/Limits vollständig gelesen; explizite Fail-closed-Startpfade/UTF-8-BOM/UTF-8-Fehlerbehandlung vorhanden. Recovery-/Pool-Wirkung zentral, kein eigener DB-Start. |
| Sozialdaten in Logs/Exporte/LLM | TEILWEISE GEPRÜFT | LMG HTTP-Fehler benutzen reduzierte Auditbeschreibung; Sweet Status liefert synthetische Fall-/Logdaten. Tiefere Berichts-/LLM-Belegkette zentral. Revisionsschutz nicht hier bestätigt. |
| Java 21 / Oracle 19c | NICHT DYNAMISCH GEPRÜFT | Isolierte Klassen mit --release 21 kompiliert, aber auf installiertem JDK 25 ausgeführt; keine Oracle-Ausführung. |

### Betriebs-/Modellgrenzen und offene Hypothesen

1. **Vollbestand für alle Fachkonten (historisch CR-035):** `LmgServer` prüft Anmeldung und wenige Adminpfade; `ladeDetail`, Bescheid, Druck, Updates und Zahlungen prüfen keine fallbezogene Zuständigkeit. Listen sind hinsichtlich IBAN/BIC reduziert; Einzelkontext und einzelne Aktionsantworten enthalten Bankdaten. Das ist eine weiterhin vorhandene explizite Modellgrenze. Eine rechtliche Freigabe einer beliebigen Behördenorganisation folgt daraus nicht; das tatsächliche Rollen-/Zuständigkeitskonzept fehlt als Nachweis.
2. **Lokales Entwicklungswerkzeug:** SweetServer bindet ausdrücklich nur `127.0.0.1`, prüft exakten Host mit eigenem Port sowie vorhandenen Origin. Kein Login: andere lokale Prozesse und Benutzer mit Zugriff auf den Port können weiterhin Testläufe/LLM-Kosten/Konfiguration auslösen. Für echten Mehrbenutzerbetrieb ist dieses Werkzeug damit nicht autorisiert abgesichert. Nicht als neu entdeckter Remote-Zugriff gewertet.
3. **SweetServer-Methoden:** Mehrere Lesepfade akzeptieren beliebige Methoden (in der Matrix mit `*`). `DELETE /api/status`, `/api/auftraege`, `/api/testfaelle`, `/api/llm/status`, `/api/llmpruefung` lieferten frisch 200. Dabei wurde kein unerwarteter Fach-Schreibeffekt belegt. Dies bleibt eine geringfügige Protokollinkonsistenz, keine weitere kritische Autorisierungslücke.
4. **SweetServer-Header/Framing:** CSP und nosniff fehlen auch auf JSON-Antworten; es gibt keinen frame-ancestors-/X-Frame-Options-Schutz. Die direkte Cross-Origin-HTTP-Probe wird korrekt mit 403 abgewiesen. Eine Clickjacking-Ausnutzung der lokalen GUI wurde nicht ausgeführt und ist OFFENE HYPOTHESE, kein bestätigter Angriff.
5. **Synchroner Export gegen HTTP-Zeitlimit:** SweetServer wartet beim manuellen Zertifikatsexport bis 390 Sekunden, HTTP-maxRspTime steht standardmäßig auf 120 Sekunden. Für einen Export über 120 Sekunden ist ein verlorener HTTP-Erfolg plausibel; das tatsächliche JDK-Timeout-Verhalten und ein entsprechend langsamer Export wurden hier nicht nachgestellt. Zentrale Prüfung bleibt offen.
6. **UTF-8 am HTTP-Eingang:** Beide Server konstruieren den JSON-Text mit `new String(bytes, UTF_8)`; ungültige Bytefolgen können dabei ersetzt werden, während Json erst UTF-16-Zeichenfolgen validiert. Ein praktisch relevanter Identitäts-/Geldfehler wurde damit nicht nachgewiesen. Keine bestätigte neue Fundstatistik daraus.
7. **Session-/Drucktokenressourcen:** Abgelaufene Sitzungen werden bei Anmeldung, abgelaufene Drucktoken bei Ausgabe bereinigt. Aktive Tokens sind nicht pro Benutzer mengenbegrenzt. Eine Ressourcenerschöpfung wurde nicht ausgeführt. Die UUID-Token sind nicht sinnvoll ratbar; keine Tokenvorhersagehypothese.
8. **Transportschutz:** Der reine JDK-HttpServer bietet hier HTTP; bei expliziter Nicht-Loopback-Bindung braucht der Betrieb einen geeigneten vorgelagerten HTTPS-/Netzschutz. Die Standard-Demobindung ist Loopback, fehlende Benutzerkonfiguration wird außerhalb Loopback hart abgewiesen. Explizit konfigurierte schwache Konten und organisatorischer Passwortschutz bleiben Betriebsverantwortung.

## Vollständige Endpunktmatrix LmgServer

Alle API-Pfade außer Anmeldung benötigen gültigen Bearer-Header; die Druckroute erlaubt alternativ ein einmaliges 60-Sekunden-Querytoken. A=Admin, SB=Sachbearbeiter oder Admin. Eine Objektzuständigkeit wird für keinen Fachendpunkt modelliert. Standardfehler: 401 ohne Sitzung, 403 bei Adminregel, 400 Validierung/Fachfehler/Konkurrenz, 404 NICHT_GEFUNDEN, 405 bekannte Route/falsche Methode, 500 Technik, 503 Tasküberlast mit Retry-After=2. Fehler- und API-Antworten setzen no-store/CSP/nosniff. Erfolgreiche JSON-Antworten sind 200, auch Neuanlagen.

| Methode / Pfad | Anmeldung / Rolle / Objektzugriff | Validierung | Schreibwirkung | Status / Protokollierung |
|---|---|---|---|---|
| POST `/api/anmeldung` | öffentlich | JSON-Objekt, Textkennung/-passwort, Drosselung | Sitzung im Speicher | 200 Token; 400 falsche Angaben/Sperre; Task-/Tx-Audit, Passwort nicht bewusst geloggt |
| POST `/api/abmeldung` | Bearer / SB | Sitzung | Sitzung entfernen | 200; Drucktokenwiderruf fehlt (WEB-02); Task-/Tx-Audit |
| GET `/api/werbin` | Bearer / SB | Sitzung | Idle-Aktivität | 200 Identität; Task-/Tx-Audit |
| POST `/api/drucktoken` | Bearer / SB | Sitzung | Drucktoken aus Benutzer, ohne BG-/Sitzungsbindung | 200; Task-/Tx-Audit |
| GET `/api/bedarfsgemeinschaften` | Bearer / SB / Vollbestand | keine Suchparameter | Vorschau-/Datenlesen | 200; reduzierte Liste ohne Bankdaten; Task-/Tx-Audit, keine individuelle Detail-Einsicht |
| POST `/api/bedarfsgemeinschaften` | Bearer / SB | Pflichttexte, Beträge, Datum, Bankfelder; weitergehende Servicevalidierung | BG anlegen | 200; Fachjournal mit Sitzungskennung + Tx-Audit |
| GET `/api/bedarfsgemeinschaften/{id}` | Bearer / SB / beliebige BG | Long-ID (nicht strikt kanonisch) | Detail lesen + Einsichtsjournal | 200/404; Kennung an service.ladeDetail |
| PUT `/api/bedarfsgemeinschaften/{id}` | Bearer / SB / beliebige BG | Long-ID, Pflichtbool, Datum, Bankdaten, Betrag, exakte Long-Version | Kopf/Bank/Vermögen/Historisierung | 200/400/404; Fachjournal + Tx-Audit |
| POST `/api/bedarfsgemeinschaften/{id}/sozialfaelle` | Bearer / SB / beliebige BG | Long-ID, exakte int-Zahlen, bool, Geldzahlen, Entbindungsmonat; Serviceguards | SozialFall anlegen | 200/400/404; Fachjournal + Tx-Audit |
| PUT `/api/sozialfaelle/{id}` | Bearer / SB / beliebiger Fall | wie Anlage + Long-Version; BG aus Bestand | SozialFall ändern | 200/400/404; Fachjournal + Tx-Audit |
| GET `/api/bedarfsgemeinschaften/{id}/bescheid` | Bearer / SB / beliebige BG | Long-ID; Snapshot robust eingelesen | Bescheid lesen + Einsichtsjournal | 200 mit leerem Objekt ohne Bescheid, ggf.404 BG; Snapshot INVALID sichtbar |
| POST `/api/bedarfsgemeinschaften/{id}/bescheid` | Bearer / SB / beliebige BG | Long-ID, von/bis Text; Service Monats-/12-Monats-/BG-Prüfung | Bescheid/Snapshot schreiben | 200/400/404; Fachjournal + Tx-Audit |
| GET `/api/bedarfsgemeinschaften/{id}/bescheid/druck` | Bearer oder Drucktoken / SB / beliebige BG | ausschließlich ASCII-ID 1–18 Ziffern; spezieller Router | Drucklesen + Einsichtsjournal; Querytoken verbrauchen | 200 HTML; 401/400/500/503; no-store auch Fehler; WEB-02 |
| POST `/api/auszahlung` | Bearer / SB / beliebige BG | exakte Long-BG-ID, Textvorgang/Monat, optionale Banktexte; Servicevalidierung/Idempotenz | Zahlung, Budget/Vermögen, Journal | 200 auch Replay; 400/404; Sachbearbeiter ausschließlich aus Sitzung; Status ANGELEGT |
| POST `/api/zahlungen/{id}/freigabe` | Bearer / SB / beliebige Zahlung | Long-ID; Servicestatus-/Vier-Augen-/Aktivitätsguards; Adminrolle explizit übergeben | ANGELEGT→BEREIT | 200/400/403/404; Fachjournal + Tx-Audit |
| POST `/api/zahlungen/{id}/storno` | Bearer / beabsichtigt A / beliebige Zahlung | Rechte-Regex ASCII-Ziffern, Handler Long-ID; Servicestatusguard | ANGELEGT/BEREIT→STORNIERT, Budget-/Vermögensrückbuchung | 200/400/403/404; Autorisierungsumgehung WEB-01; Fachjournal mit SB-Kennung |
| POST `/api/kassenlauf` | Bearer / A / globale Menge | Service sperrt/prüft konkrete Zahlungen | BEREIT→UEBERGEBEN | 200 Anzahl/Summe; Fachjournal + Tx-Audit |
| POST `/api/saga/antrag` | Bearer / SB | JSON-Objekt, Payloadvalidator; SB-Payload durch Sitzung ersetzt | Saga + Fachschritte/Kompensation | 200, 404 ohne Engine, 400/500; Saga-/Fach-/Tx-Journal |
| GET `/api/zahlungen?limit=` | Bearer / SB / Vollbestand | limit int 1–500 | Lesen | 200; keine IBAN/BIC, Empfängername enthalten; Task-/Tx-Audit |
| GET `/api/journal?limit=` | Bearer / SB / Vollbestand | limit int 1–500 | Lesen | 200; Task-/Tx-Audit |
| GET `/api/system/status` | Bearer / A | Route | Diagnose | 200; Task-/Tx-Audit |
| GET `/api/system/db-identity` | Bearer / A | Route | DB-Identität lesen | 200; Task-/Tx-Audit |
| POST `/api/system/wiederanlauf` | Bearer / A | Route; Recovery-Mindestinaktivität in Main | Recovery/Kompensation | 200 Anzahl; Task-/Saga-/Tx-Audit |
| GET/HEAD `/`, `/index.html`, `/app.js`, `/druck.js`, `/style.css` | öffentlich | exakte Ressourcenpositivliste | Ressourcenlesen | 200/404, andere Methoden405 Allow=GET,HEAD; CSP/nosniff; kein Fachaudit |
| übrige `/api/*` / falsche Methode | Sitzung zuerst; Adminmuster ggf.vor Routing | ROUTEN-Liste nur im Fehlerpfad | keine Fachwirkung | 404/405; API-405 ohne Allow-Header; Task-/Tx-Audit |

## Vollständige Endpunktmatrix SweetServer

SweetServer besitzt keine Benutzerrollen. **L** bedeutet lokale Loopbackbindung plus exakter Host (`127.0.0.1:<port>` oder `localhost:<port>`) und bei vorhandenem Origin exakt `http://<host>`. Jeder Matrixeintrag besitzt diesen vorgelagerten Guard (sonst 403). `*` bedeutet im aktuellen Router keine Methodeneinschränkung. Gemeinsame Fehler: IllegalArgumentException=400, IllegalStateException=409, andere Exception=500 mit Klassenname; sendende JSON-/Dateipfade setzen no-store. Keine personenbezogene HTTP-Zugriffsprotokollierung vorhanden; Ausführungs-/Prüfprotokolle sind werkzeugbezogen.

| Methode / Pfad | Zugriff | Validierung/Guards | Schreibwirkung | Status / Protokollierung |
|---|---|---|---|---|
| GET `/api/zertifikat/tests/status` | L | Methode | Status/Abgleich | 200/405; Suitezustand |
| GET `/api/zertifikat/tests/glm-protokoll` | L | Methode; Suite-Artefaktpfad normalisiert unter testsweet/logs; protocol.md vorhanden | Dateilesen | 200/404/405; keine zusätzliche Einsichtsprotokollierung |
| GET `/api/llm/grundlagen` | L | Methode; feste Datei | Dateilesen | 200/404/405 |
| GET `/api/llm/pruefauftrag` | L | Methode; feste Datei | Dateilesen | 200/404/405 |
| GET `/api/llm/pruefmenge` | L | Methode; LLM-Artefaktpfad unter logs oder letzter llm-review-Unterordner | Dateilesen | 200/404/405 |
| POST `/api/zertifikat/tests/start` | L | Methode; operationLock; kein manueller Lauf/LLM/Export; JSON-Objekt <=1MiB; faelle=0 oder2–1000; DB-Vorabtest | Gesamtsuite asynchron starten, Berichtscache entwerten | 202/400/405/409/503 DB_NICHT_ERREICHBAR; Suiteverzeichnis/-status |
| POST `/api/zertifikat/tests/stop` | L | Methode; operationLock | Suite abbrechen | 200/405; Suitestatus |
| * `/api/status` | L | keine Methode | Status lesen | 200; ReviewOutcome + Automatik/Belegstatus getrennt; URLs/User, Logs, synthetische Fallmetriken |
| POST `/api/start` | L | Methode; operationLock; Suite/LLM/Export ausgeschlossen; Modus/Kategorie; exakte Ganzzahlen; DB-Vorabtest; Simulatorgrenzen | Simulatorlauf starten, ggf.eigene Testdaten | 200/400/405/409/503; Simulatorbelege |
| POST `/api/stop` | L | Methode; operationLock; keine Suite | Simulator abbrechen | 200/405/409; Laufstatus |
| * `/api/pdf` | L | Suite darf nicht laufen; Bericht vorhanden; ggf.geprüften Bericht aktualisieren | ggf. separate Anhänge/Report schreiben, PDF lesen | 200/404/409/500 |
| * `/api/md` | L | wie PDF; letzter Bericht | ggf.geprüften Bericht aktualisieren, MD lesen | 200/404/409/500 |
| POST `/api/zertifikat` | L | Methode; operationLock; Suite/Simulator/LLM/Export ausgeschlossen; Werkzeug vorhanden | publish-certificate-Prozess bis390s; atomare Veröffentlichung im Werkzeug | 200 auch bei negativem fachlichem Verdict, 404/405/409/500; READY explizit geprüft |
| * `/api/zertifikat/pdf` | L | Suite/Export ausgeschlossen; Datei vorhanden; publish-certificate --verify bis30s | Verifikation, PDF lesen | 200/404/409; kein alter positiver Nachweis allein aufgrund Dateiexistenz |
| * `/api/auftraege` | L | keine Methode | Liste lesen | 200; synthetische Fallnamen/Status/Fehler |
| * `/api/auftrag/{id}` | L | Integer-ID; Auftrag im aktuellen Simulator vorhanden | Detail lesen | 200/400/404; Schritte/Prüferwartungen/Istwerte |
| * `/api/testfaelle?kategorie=&modus=` | L | Katalogparameter dekodiert; Modus2 Web | Katalog lesen | 200/400 |
| GET `/api/llm/config` | L | Methode | lokale Konfiguration lesen, Schlüssel nur configured-bool | 200/405; kein Schlüsselwert |
| POST `/api/llm/config` | L | Methode; operationLock; keine Suite; JSON-Objekt <=1MiB; LlmConfig URL/Modell/Schlüssel-/Zahlgrenzen; Service-running-Guard | lokale Schlüssel-/Modellkonfiguration atomar speichern | 200/400/405/409/500; publicView redigiert |
| POST `/api/llm/test` | L | Methode; operationLock; keine Suite; Settings/Schlüssel | externer Testaufruf | 200/405/409/500; Ergebnis durch LlmReviewService |
| POST `/api/llm/start` | L | Methode; operationLock; keine Suite/Simulator/Export; JSON-Objekt; exakte Fallzahl; Service-running-Guard | GLM-Prüfung asynchron, lokale Belege | 202/400/405/409/500; Reviewverzeichnis/-status |
| * `/api/llm/status` | L | keine Methode | LLM-Status lesen | 200; Transport/Fachurteil/Belege im Status |
| * `/api/llm/report/pdf` | L | keine Suite; abgeschlossene LLM-Artefakte; refreshReviewedReport mit Pfadguard | ggf.Anhang schreiben, PDF lesen | 200/404/409/500 |
| * `/api/llmpruefung` | L | keine Methode; refreshReviewedReport-Guards | ggf.Anhang/Report schreiben, Status/Prompt lesen | 200/500; publicConfig ohne Schlüssel |
| POST `/api/llmpruefung/paket` | L | Methode; operationLock; keine Suite; JSON-Objekt; Fallzahl/Serviceguard | lokales Prüfpaket erzeugen | 200/400/405/409/500 |
| GET `/`, `/index.html`, `/app.js`, `/style.css`, `/assets/<erlaubt>.(png/jpg/jpeg/svg)` | L | Ressourcenpositivliste; keine `..`; Fallback normalize+startsWith+toRealPath | Ressourcenlesen | 200/404; no-store, kein CSP/nosniff |
| sonstige Pfade | L | GET statischer404-Pfad; andere Methode unbekannt404 | keine vorgesehene Fachwirkung | 404; bekannte Lesepfade mit `*` bleiben erreichbar |

## Historische Nachprüfung nach eigener Analyse

| Vorbefund | Urteil am aktuellen Stand | Aktuelle Evidenz / Rest |
|---|---|---|
| CR-004 Wildcard + Standardkonten | BEHOBEN für Default/fehlende Konfiguration | Loopback-Konstruktor; Main verweigert Demo bei Nicht-Loopback; nicht als Garantie für explizit schwache Konfiguration verstanden. |
| CR-010 SweetServer immer200 | BEHOBEN | `sendeJson(exchange, code, ...)` nutzt code; frische GET-Fehlmethoden405, Origin403 und Pfad404. |
| CR-023 Datumfehler500 | BEHOBEN (statisch) | datumWert wandelt Parsefehler in IAE; API-Task IAE→FachlicheException→400. |
| CR-024 limit ungeprüft | BEHOBEN (statisch) | Integer +1–500, IAE→400. |
| CR-025 Sweet-Traversal | BEHOBEN für ursprünglichen Pfad | Positivliste, normalize/startsWith/toRealPath; frische ausgewählte Geheim-/Traversalpfade404. |
| CR-026 Drosselung/Map | TEILWEISE BEHOBEN | Schlüssel/Map gedeckelt, aktive bestehende Sperren geschützt; neu bestätigter Sättigungsfall WEB-03. Per-Kennung-Sperrung durch Dritte bleibt als bekannte Eigenschaft. |
| CR-027 Sitzungstoken in URL/localStorage | BEHOBEN für ursprünglichen Befund | Sessiontoken nur Header/sessionStorage; Query akzeptiert nur Drucktoken; TTL und einmaliger Verbrauch. Neuer Logout-Kopplungsfehler WEB-02. |
| CR-035 Vollbestandszugriff | TEILWEISE BEHOBEN | IBAN/BIC aus Listen und Einsichtsjournal umgesetzt; Objekt-/Teamfilter weiterhin nicht vorhanden. Protokollierung ersetzt keine Objektberechtigung. |
| CR-051 falsche Methode404 | TEILWEISE BEHOBEN | LMG bekannte Routen405, jedoch kein Allow im API-405; Sweet wichtige Mutationen405, mehrere Lesepfade ohne Methodenprüfung. |
| CR-052 no-store/CSP | BEHOBEN für ursprüngliche LMG-API-/Druckantwortpfade | API-/Druckfehler und Druckerfolg no-store; LMG CSP/nosniff; Sweet no-store frisch bestätigt, Sweet CSP/nosniff weiterhin nicht vorhanden. |
| S-14 fehlende HTTP-Tests | BEHOBEN als Testinventarlücke | LmgServerHttpTest/Boundary/Monatsplan/Limits und Sweet-HTTP-Tests vorhanden; neue Gegenbeispiele werden von bisherigen Assertions nicht erfasst. |
| Historischer GUI-Export bei negativem Verdict verweigert / alter Download ungeprüft | BEHOBEN an HTTP-Schnittstelle (statisch) | Export erlaubt negative/unvollständige Dokumentation; READY-Marker; Download ruft --verify auf. Beleg-/Generatorinnereien werden zentral geprüft. |

## Reproduktion und Belege

- `WebReviewProbe.java`: eigene Benutzerverwaltung, feste Uhr nur für deterministische Auth-Prüfung, echter JDK-HttpServer Port0, dynamischer Servicefake und JDBC-Proxy; sourcepath aus aktuellen Produktquellen. Ausgabe `web-probe-output.txt`.
- `SweetReviewProbe.java`: echter SweetServer Port0 mit Simulator ohne Laufstart und synthetischem leerem Repository ausschließlich in diesem Ordner. Keine DB-, Modell- oder Exportaktion. Ausgabe `sweet-probe-output.txt`.
- `JsonReviewProbe.java`: 18 vorab definierte Parserannahmen, Assertion bei Abweichung. Ausgabe `json-probe-output.txt`.
- Kompilierung: `javac --release 21 -encoding UTF-8 -sourcepath src/main/java ...`; für Sweet zusätzlich `testsweet/src/main/java`. Klassen ausschließlich `web/classes/`. Bestehende Maven-Repository-Dependencies wurden gelesen; kein JAR wurde installiert/verändert. Der bestehende `tmp/review-fixes-classpath.txt` wurde nur als Dependency-Pfadliste verwendet; das enthaltene alte Hauptprodukt-JAR wurde ausdrücklich ausgeschlossen, damit die Produktquellen neu kompiliert wurden.
- Tatsächliche Laufzeit: `C:/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot`; Compilerziel21 ist kein Java-21-Laufnachweis.
- Die probeweisen HTTP-Server/Executoren wurden per try-with-resources geschlossen. Keine fremden Prozesse beendet. Die erzeugten `.class`-Dateien sind isolierte Reviewartefakte und nicht als produktiver Build-/Zertifikatsnachweis verwendbar.
- Ein globales Prüfstand-/Driftinventar wird zentral geführt. Das ergänzende `source-hashes.json` in diesem Ordner hält die für diesen Teil gelesenen wesentlichen Quellen nach der Gegenprobe fest.
