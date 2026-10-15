# Fachlicher Teilreview SDN.Leistung, 15.10.2026

Prüfauftrag: aktueller Gesamt-Review-Prompt; keine Produkt-, Bestandsdokument-, Test- oder Konfigurationsänderung. Eigene Analyse von Berechnung, Monatsplan, Snapshot, Service, DAOs, Bescheiddruck und Antrags-Saga erfolgte vor der historischen Nachprüfung. Die zentrale Prüfung erfasst Commit, Arbeitsbaum und Gesamtinventar. Dieser Bericht bewertet den gelesenen Arbeitsstand; kein eigener Maven-, Oracle-, Simulator- oder GLM-Lauf.

## Methode und Grenzen

Isolierte Java-Proben kompilieren die aktuellen betroffenen Produktionsquellen direkt mit `javac --release 21 -encoding UTF-8` in `fach/classes`. Tatsächlicher Lauf: Java **25.0.4.1**, kein Nachweis unter Java 21. `FachProbe.java` enthält vor Ausführung festgelegte Sollwerte. `fachprobe-ergebnis.json` enthält die frischen Istwerte. Synthetische Rechenblöcke prüfen gezielt gemeinsame Annahmen der drei arithmetischen Referenzen; sie sind ausdrücklich **keine** Simulator-/SQL-/LLM-/Zertifikatsnachweise.

Amtliche Rechtsquellen wurden am 15.10.2026 online abgerufen. Die für 2026 geltenden RBS stehen ausdrücklich in der amtlichen Anlage. Für die übrigen Normen wurde der aktuell abrufbare konsolidierte Wortlaut geprüft, keine lückenlose Änderungshistorie für beliebige historische Bezugsmonate. Öffentlich bereitgestellte amtliche Fachanweisungen und die Wissenschaftlichen Dienste sind Auslegungshilfen, kein Ersatz für Gesetz oder gerichtliche Einzelfallentscheidung.

## Bestätigte Befundkandidaten

### F01 — Alleinerziehendenmehrbedarf bei vier oder mehr Kindern wird zu niedrig angesetzt

**Schwere: KRITISCH; rechnerisch dynamisch belegt, erreichbarer Gesamtpfad statisch belegt; Rechtsauslegung zusätzlich amtlich gegengeprüft.**

Ort: `src/main/java/de/allgemein/lmg/calc/Leistungsberechnung.java:238` und `:239`. Bei jedem U7-Kind oder zwei/drei U16-Kindern wird pauschal 36 Prozent gewählt. Die höhere Alternative für vier/fünf und mehr minderjährige Kinder wird dann nicht geprüft.

Vorbedingung: allein erziehende, nach § 41 leistungsberechtigte erwachsene Person RBS 1, vier Kinder (ein U7, zwei U16, vier minderjährig), kein abweichender Bedarf. Eingabe über Fallanlage/-änderung: `anzKinderUnter7=1`, `anzKinderUnter16=2`, `anzKinderMinderjaehrig=4`. Alle drei Werte sind innerhalb Service- und DDL-Grenzen. Service `pruefeUndNormalisiereFall` erhält sie; `ordneAuszahlungAn:294–301`, `erteileBescheid:610` und die Saga-Folgeschritte rechnen über dieselbe Produktionsfunktion. Kein nachgelagerter Guard korrigiert den Betrag. Ein neu erteilter Bescheid friert daher den gleichen falschen Betrag ein; Bescheidbindung verhindert diesen gemeinsamen Fehler nicht.

Soll: 563 × 48 Prozent = **270,24 EUR**; Ist: **202,68 EUR**, Unterbewilligung **67,56 EUR je Monat**, bei zwölf Monaten **810,72 EUR**. Zweite Probe: fünf Minderjährige, drei U16, kein U7: Soll 337,80, Ist 202,68; Differenz **135,12 EUR je Monat**, zwölf Monate **1.621,44 EUR**. Kindliche Bedarfe sind für diesen isolierten Elternmehrbedarf nicht erforderlich; in einer vollständigen Familienrechnung bleibt dieselbe Differenz bestehen.

Gegenposition: Der aktuelle Wortlaut von [§ 30 Abs. 3 SGB XII](https://www.gesetze-im-internet.de/sgb_12/__30.html) lässt beim isolierten Lesen eine ausschließliche Nr.-1-Auswahl vermuten. Die amtliche Auslegung spricht dagegen: [Wissenschaftliche Dienste, WD 6-046/24, S. 11 i.V.m. S. 7–10](https://www.bundestag.de/resource/blob/1019234/WD-6-046-24-pdf.pdf) verweist für SGB XII auf die günstigere Berechnung; die [Hamburger Fachanweisung, Archiv 2016, Abschnitt 2.3.2](https://www.hamburg.de/politik-und-verwaltung/behoerden/sozialbehoerde/rechtliche-grundlagen/infoline/archiv/archiv-2016/fa-sgbxii-30-427216) behandelt vier Kinder ausdrücklich auch bei einem U7-/zwei U16-Kindern mit 48 Prozent. Das Archivdatum ist offengelegt; es wird nicht als aktuelle allgemeine Verwaltungsbindung ausgegeben.

Beleg: `FachProbe.java`, `fachprobe-ergebnis.json`, IDs `AE4_1U7_2U16`, `AE5_3U16`. Vorhandene Tests prüfen vier/fünf Kinder ohne die gleichzeitig zutreffende 36-Prozent-Konstellation; sie schließen diesen Fehler nicht aus.

### F02 — Optionaler zentraler Warmwasserabzug kürzt gesetzlich anzuerkennenden Bedarf

**Schwere: MAJOR; dynamisch und statisch belegt; gesetzlicher Widerspruch direkt geprüft.**

Ort: `Leistungsberechnung.java:198–202`, `BescheidGenerator.java:356–361`; Konfiguration `LmgConfig.java:63`, `lmg.properties.example:38–44`. Der Konfigurationswert wird nur bei zentralem Warmwasser von den Heizkosten abgezogen. Gerade diese Kosten sind nach [§ 35 Abs. 5 SGB XII](https://www.gesetze-im-internet.de/sgb_12/__35.html) Bestandteil des Heizbedarfs. Ein anderer Bedarfsposten ersetzt sie im Code nicht.

Vorbedingung: zulässiger positiver Wert `lmg.kdu.warmwasserabzug=0.20`, angemessene tatsächliche Kaltmiete 400, Heizkosten einschließlich zentralem Warmwasser 100, RBS 1, Einkommen/Vermögen 0, keine Angemessenheitskürzung. Soll **1.063,00 EUR**, Ist **1.043,00 EUR**, Differenz **20,00 EUR/Monat**. API/Service/Saga und neu erteilter Snapshot verwenden diese Kürzung. Der Druck begründet sie ausdrücklich mit zentraler Warmwassererzeugung.

Gegenprüfung: Default 0 vermeidet die Fehlwirkung. Der dezentrale Mehrbedarf nach § 30 Abs. 7 ist bei dieser Konstellation nicht zusätzlich geschuldet; er rechtfertigt aber keinen Abzug der zentralen Kosten. Örtliche Angemessenheitsregeln erlauben keine beliebige doppelte Kürzung angemessener Aufwendungen. Eine Konfiguration als pauschale Heizbedarfsermittlung wäre eine andere Fachregel mit anderen Voraussetzungen; implementiert ist ausdrücklich ein zusätzlicher Warmwasserabzug.

Beleg: Probe-ID `ZENTRAL_WW_20PROZENT`. Der vorhandene Unit-Test `LeistungsberechnungTest.java:300–306` erwartet selbst die Kürzung und schützt diese falsche Fachannahme.

### F03 — Drei arithmetische Referenzen bestätigen dieselben falschen Fachannahmen

**Schwere: KRITISCH für die Aussagekraft der Prüfkette; dynamisch belegt.**

Orte: `testsweet/.../LlmCalculationReference.java:129,133–134`; `doc/testzertifikat-werkzeug/llm-reference.js:56,58`; `doc/testzertifikat-werkzeug/nachrechnen_100.py:36,44–45`. Alle drei verwenden dieselbe ausschließliche 36-Prozent-Auswahl sowie denselben zentralen Warmwasserabzug. `testsweet/.../Verifikator.java:109` ruft die Java-Referenz auf. Rechensprache und fehlender Produktionscodeaufruf schaffen keine Unabhängigkeit der rechtlichen Regelannahme.

Die zwei mit F01/F02 fachlich falschen synthetischen Blöcke werden frisch von allen Referenzen ohne arithmetische Beanstandung akzeptiert:

| Referenz | Vier Kinder, Elternmehrbedarf 202,68 statt 270,24 | Zentraler Bedarf 1043 statt 1063 |
|---|---|---|
| Java `verifyBlocks` | leere Fehlerliste | leere Fehlerliste |
| Node `requirement` | `sourceFindings=[]`, `missingInputs=[]` | identisch |
| Python Kontrollvergleich | leere Differenzliste | leere Differenzliste |

Belege: `synthetisch-ae4.txt`, `synthetisch-zentral.txt`, `ReferenceProbe.java`, `reference-probe.js`, `reference-probe.py`, `reference-{java,node,python}-ergebnis.json`. Die bewusst synthetische Formatzeile behauptet keine SQL-Ausführung. Kein echter GLM-Aufruf und kein erfolgreicher Gesamtexport wurden hier durchgeführt. Ein unabhängiges LLM könnte die Rechtsfehler erkennen; dies ist durch diese Probe weder bestätigt noch widerlegt. Die arithmetische Schutzschicht erkennt sie nachweislich nicht. Die Funde F01/F02 sind deshalb nicht durch grüne Zweitnachrechnungen entkräftet.

### F04 — Nachdruck verändert Zahlungsanweisung trotz identischem Bescheid/Snapshot

**Schwere: MAJOR; Generator dynamisch belegt, Web-/Servicepfad statisch belegt.**

Ort: `SozialLeistungsService.java:680–685`, `BescheidGenerator.java:97–102,234–239,279–281`. Bei gespeicherten v2-Snapshots werden heutige IBAN/BIC/Kontoinhaber des Vorgangs in den Nachdruck übernommen. Snapshot-Serialisierung speichert diese ursprünglichen Angaben nicht. Das hat mit der korrekt eingefrorenen Berechnung nichts zu tun: Die im Bescheid enthaltene Zahlungsanweisung ändert sich.

Erreichbarer Ablauf: Bescheid erteilen → Nachdruck mit ursprünglicher Bankverbindung → `PUT /api/bedarfsgemeinschaften/{id}` mit neuer gültiger Bankverbindung und korrekter Version → erneuter `GET .../bescheid/druck`. Das Service-Update erlaubt Bankänderungen, und der Druck verwendet den neuen BG-Kopf; keine Kennzeichnung als geänderte Zahlungsanweisung. Die Probe erzeugt bei identischer Bescheidnummer, Datum, Beträgen und Snapshot zwei verschiedene HTML-Dokumente; der zweite enthält `Neues Konto`.

Gegenprüfung: Die Bindung der **Zahlbeträge** bleibt erhalten. Eine Bankänderung für künftige Überweisungen ist fachlich zulässig; der ursprüngliche Nachdruck sollte dennoch den ursprünglichen Dokumentinhalt bewahren oder die aktuelle Zahlungsinformation sichtbar von ihm trennen. Es wird kein unberechtigter tatsächlicher Geldtransfer behauptet. Entsprechend MAJOR, kein BLOCKER.

Zusätzliche Grenze desselben Snapshotumfangs: heutige Rechtsbehelf-/Behördenkonfiguration wird beim Nachdruck erneut verwendet. Auch diese Texte sind nicht versioniert. Eine Änderung nach Erteilung kann deshalb eine historische Belehrung verändern.

### F05 — Widerspruchsbelehrung nennt die Gerichtsformnorm statt der Behördenformnorm

**Schwere: MAJOR; dynamisch/statisch belegt, genaue Rechtsfolge im Einzelfall offen.**

Ort: `BescheidGenerator.java:410`. Die Widerspruchsbelehrung verweist für die elektronische Form auf § 65a SGG. [§ 84 Abs. 1 SGG](https://www.gesetze-im-internet.de/sgg/__84.html) verweist hierfür ausdrücklich auf [§ 36a Abs. 2 und 2a SGB I](https://www.gesetze-im-internet.de/sgb_1/__36a.html) sowie § 9a Abs. 5 OZG. § 65a betrifft die gerichtliche elektronische Einreichung. Die Behördenschnittstelle und deren eröffnete Zugangswege können daraus nicht abgeleitet werden.

Trigger: jeder Bescheiddruck im Standardmodus WIDERSPRUCH. Die Probe bestätigt `verweistAuf65aSGG=true`, `verweistAuf36aSGBI=false`. Gegenprüfung: Im Modus KLAGE ist § 65a als gerichtliche Formnorm passend. Die Monatsfrist und postalische Vier-Tage-Fiktion wurden nicht als Fehler übernommen. Ob die konkrete Belehrung Fristverlängerungsfolgen auslöst, erfordert die Einzelfallprüfung und wird nicht allein aus dem falschen Zitat behauptet.

Zusätzliche bekannte Betriebsvoraussetzung: [§ 36 SGB X](https://www.gesetze-im-internet.de/sgb_10/__36.html) fordert Stelle/Gericht und Sitz. Defaults enthalten Platzhalter statt einer konkreten Anschrift. `BescheidGenerator.java:389–391` legt als gerichtlichen Default den Behördensitz zugrunde, obwohl bei natürlichen Personen grundsätzlich der Klägerwohnsitz nach [§ 57 Abs. 1 SGG](https://www.gesetze-im-internet.de/sgg/__57.html) maßgeblich ist. Mangels Wohnsitzdaten lässt sich dies nicht fallbezogen bestimmen. Diese Modell-/Konfigurationsgrenze wird nicht als zusätzlicher bestätigter Einzelfallfehler gezählt.

## Fachliche Modellgrenzen und Voraussetzungen

Diese Beobachtungen sind von Implementierungsfehlern getrennt. README bezeichnet das System ausdrücklich als Technologie-Showcase ohne behördlichen Produktiveinsatz. Die Einschränkungen verhindern weitergehende Rechtskonformitätsaussagen; sie belegen kein tatsächlich rechtswidrig beschiedenes reales Verfahren.

1. **Anspruchsstatus nicht modelliert.** [§ 41 SGB XII](https://www.gesetze-im-internet.de/sgb_12/__41.html) verlangt spezifische Alters-/Erwerbsminderungs- und weitere Voraussetzungen. `SozialFall` speichert weder Geburtsdatum noch Aufenthalts-/Erwerbsminderungsstatus. RBS 4–6 können dennoch in einem Bescheid nach dem vierten Kapitel mitbewilligt werden. Kinder können Teil eines Haushalts sein, sind deshalb aber nicht selbst grundsicherungsberechtigt nach § 41. Externe fachliche Vorprüfung ist zwingend; die pauschale Aussage im Druck, §-41-Voraussetzungen lägen vor, wird technisch nicht geprüft.
2. **Alleinerziehung nicht als Voraussetzung gespeichert.** Kinderzahlen lösen den Mehrbedarf automatisch aus; UI-Felder heißen lediglich Kinder im Haushalt. Es fehlt ein ausdrücklicher Nachweis-/Statusbezug für alleinige Pflege/Erziehung. Paarhaushalte mit identisch ausgefüllten Kinderzahlen sind technisch möglich. Kinderzahlen dürfen nur nach vorheriger Feststellung der Mehrbedarfsvoraussetzung verwendet werden; das ist keine technische Sicherung.
3. **RBS-Zuordnung vereinfacht und teils falsch beschrieben.** Die [amtliche Anlage](https://www.gesetze-im-internet.de/sgb_12/anlage.html) knüpft RBS 3 an § 27b, nicht generell an fehlende eigene Wirtschaftsführung in einer Wohnung. `doc/rechtsgrundlagen.md:32` und DDL-Kommentar beschreiben die veraltete Zuordnung. Für Erwachsene in einer Wohnung gilt regelmäßig RBS 1, sofern RBS 2 nicht einschlägig ist. Die Software lässt die Stufe frei wählen und prüft keine Wohnform. RBS ist kein verlässlicher Ersatz für Alter, Partnerschaft und Rechtsstatus.
4. **Haushalt und Einzelanspruch.** Globale BG-Summen für Einkommen/Vermögen bilden nicht automatisch die Anwendbarkeit nach [§ 43 Abs. 1 SGB XII](https://www.gesetze-im-internet.de/sgb_12/__43.html) ab. Es fehlen Personenbeziehungen, individuelles Vermögenseigentum, Partnerbedarf und Trennung von leistungsberechtigten und nur mitwohnenden Personen. Die Annahme einer gemeinsamen Einsatzgemeinschaft muss extern begründet werden.
5. **Schonvermögen.** Die Beträge 10000/500 sind amtlich bestätigt über den Suchindex zur [DVO § 1](https://www.gesetze-im-internet.de/bshg_88abs2dv_1988/__1.html), Fassung zuletzt geändert 16.12.2022. Wiederholte Direktabrufe der HTML-/PDF-Datei schlugen fehl; kein erfolgreicher Direktdownload wird behauptet. Die Norm nennt Rechtsstatus und überwiegenden Unterhalt, keine RBS-Grenze. `Leistungsberechnung.java:256–264` verwendet RBS<=3 und setzt bei null Erwachsenen den Freibetrag ungeachtet der Zahl alleinstehender Minderjähriger einmal auf 10000. Der Modellvertrag deckt die normgerechte Zuordnung nicht allgemein ab.
6. **Tatsächlicher Vermögensverbrauch.** `ordneAuszahlungAn:335–346` mindert das gespeicherte Vermögen schon bei ANGELEGT; Saga:402,467–480 ebenfalls. `Bewilligungsplan.java:69` simuliert den Verbrauch im Monatsplan. Keine dieser Operationen belegt tatsächliche Verwendung privaten Vermögens; es gibt keinen Buchungspfad zum Vermögenskonto. Beispiel bei tatsächlichem unverändertem Bestand 10500: Modell 363 im ersten/863 im zweiten Monat bei Bedarf 863. Der höhere Folgebetrag ist nur unter der Verbrauchsannahme begründet. [LSG NRW, 28.07.2008, L 20 SO 17/08, Rn. 42](https://nrwe.justiz.nrw.de/pdfdownload/downloadEntscheidung.php?entscheidung=%2Fnrwe%2Fsgs%2Flsg_nrw%2Fj2008%2FL_20_SO_17_08beschluss20080728.html) stellt auf tatsächliche Verhältnisse ab; fiktiver Verbrauch ist kein allgemeiner Ersatz. Eigene Probe bestätigt ausschließlich die Modellrechnung, keine tatsächliche Auszahlung und keinen realen Verbrauch. Umgekehrt ist fortdauernde Anrechnung nach **nachgewiesenem** tatsächlichem Verbrauch ebenfalls falsch. Der alte CR-015 formulierte pauschaler als rechtlich tragfähig.
7. **KdU-Verteilung.** Reihenfolgevariante ergibt gleiche BG-Summe 1712, aber Person 11 erhält je nach Reihenfolge 600 oder 300 KdU. DAO-ORDER-BY-ID macht dies deterministisch, nicht fachlich personenbegründet. [§ 42a Abs. 3–7](https://www.gesetze-im-internet.de/sgb_12/__42a.html) unterscheidet Haushalts-/Wohnformen und Verpflichtungen. Diese Merkmale fehlen. Keine zusätzliche Auszahlungssummendifferenz wurde gefunden. Karenz-/Absenkungs-/Todesfallschutz bleiben dokumentierte, rechtlich erhebliche Modellgrenzen.
8. **Beginn/Ende Schwangerschaft.** Das Entbindungsende ist korrekt monatsbezogen umgesetzt. Schwangerschaftsbeginn nach der zwölften Woche wird nur als Flag extern erfasst; keine zeitbezogene Historie. Ein später gesetztes Flag kann bei Rückrechnung schon Monate vor Schwangerschaft zum Mehrbedarf führen. Ohne erfassten Entbindungsmonat bleibt es für Folgezeiträume wirksam. Das ist vor Berechnung extern zu prüfen.
9. **Einkommensarten und Sonderregeln.** Nur Rente/Erwerb sowie vier Abzugssummen. Nicht modellierte Ausnahme-/Freibetragsregeln, Einkommenszufluss und Einzelfallabweichungen sind kein allgemeiner SGB-XII-Nachweis. Die dokumentierte Brutto-Basis ist durch eine [amtliche Hamburger Fachanweisung, Stand 01.01.2022, Abschnitt 5.6](https://www.hamburg.de/resource/blob/47000/ea264891acec0de7e9dcbff90c797c49/fa-sgbxii-82-84-00-einkommen-pdf-data.pdf) zusätzlich gestützt; die bloße Analogie zu SGB II in der Projektdoku genügt allein nicht als Begründung. Kein Brutto-/Nettofehler bestätigt.
10. **Änderungsbescheid und Monatsreihenfolge.** Aktuelle Betragsbindung weist nachträgliche Änderungen, veränderten Vermögensbestand oder überlappende Bewilligungen konservativ ab. Das verhindert falsche stille Überzahlungen, bietet aber keinen vollständigen fachlichen Korrekturworkflow. Dokumentation nennt dies ausdrücklich. Nullmonate im Plan werden zwar gespeichert, Anspruch im ersten Monat <=0 verhindert jedoch die Erteilung; später eintretende Hilfebedürftigkeit muss separat bearbeitet werden.

## Saga-Kompensation: fachliche Schnittstelle zum Kernreview

Statisch gelesen: `erstauszahlungKompensieren` behandelt UEBERGEBEN ausschließlich mit ALARM-Journal (`AntragsSagaProzess.java:549–559`) und kehrt ohne Fehler zurück. `bescheidKompensieren:347` prüft anschließend nur eigenen Marker/ERTEILT-Status; `vorgangKompensieren:221` prüft nur eigenen Marker/Aktivität. Beide kennen keinen Zahlungs-Guard. Der Kernagent prüft die Ausführung und den endgültigen Saga-Status zentral. Hier wird kein eigener dynamischer Crash-/Recovery-Nachweis behauptet.

Fachliche Auswirkung bei erreichbarer Reihenfolge: Eine übergebene, nicht zurückgeholte Zahlung kann weiter existieren, während Bescheid aufgehoben und BG historisiert werden. Dass Geld nicht blind gutgeschrieben wird, ist richtig. Ein bloßer ALARM ist aber kein Beleg abgeschlossener fachlicher Rückabwicklung. Bei **technisch** fehlgeschlagenem Zahlungsstorno haben nachgelagerte ältere Kompensationen denselben fehlenden fachlichen Guard. Der Befund ist mit dem Kernreview zusammenzuführen und nicht doppelt zu zählen.

## Norm- und Abdeckungsmatrix

GEPRÜFT bedeutet nur den bezeichneten Teil mit angegebener Evidenz, keine vollständige Normabdeckung.

| Norm / Bezugsstand | Fachregel / Modellannahme | Codepfad | Unabhängige Referenz / Nachweis | Urteil |
|---|---|---|---|---|
| §41, aktuell / 2026 | Personenkreis, Inland, Alter/EM | SozialFall, Service-Fallvalidierung, Druck | amtlicher Wortlaut; Strukturprüfung | TEILWEISE GEPRÜFT; Statusdaten fehlen |
| Anlage §28, Zeile 01.01.2026 | 563/506/451/471/390/357 | Satzstand, SatzstandDao, Seed | amtliche Beträge; Quellabgleich | GEPRÜFT für Beträge/Auswahllogik; kein eigener Oracle-Nachweis |
| Anlage §28 / §§27b,42a | richtige RBS-Zuordnung | freie RBS-Wahl, Doku | amtliche Beschreibung | TEILWEISE GEPRÜFT; RBS3-Doku falsch, Statusprüfung extern |
| §30 Abs1/2, aktuell |17%; Merkmal extern; Schwangerschaftsende | Leistungsberechnung; faelleFuerMonat; Service/Saga | 563×0,17=95,71; Oktober658,71/November563 | GEPRÜFT für Rechen-/Endelogik; Voraussetzungen/Taggrenzen nur teilweise |
| §30 Abs3, aktuell | günstigere Kinderalternative, RBS1-Basis | mehrbedarfAlleinerziehend |270,24/337,80 vorher festgelegt; Java-Probe; WD | GEPRÜFT; F01 |
| §30 Abs6/7, aktuell | Deckel Abs1–5; WW außerhalb; Stufensätze | berechneFall | amtlicher Wortlaut; vorhandene Tests gelesen | TEILWEISE GEPRÜFT; kein weiterer unabhängiger Vollgrenzenlauf |
| §§35 Abs5,42a, aktuell | zentrale WW-Kosten als Bedarf | kduWarmwasserAbzug |1063 vs1043, Java-Probe | GEPRÜFT; F02 |
| §§35 Abs1/3/7,42a Abs3–7 | Angemessenheit, Schutzfristen, Verteilung | kduRest, KduParameter | Reihenfolgeprobe total1712, Personen600/300 | TEILWEISE GEPRÜFT; erhebliche Modellgrenzen |
| §82 Abs2/3, aktuell | vier Abzüge; 30% Brutto; 50%-RBS1-Deckel | erwerbsfreibetrag, anrechenbar | 938,32/33/34 →281,50, Java-Probe; amtliche Auslegung | GEPRÜFT für diese Centgrenze; Ausnahmen nicht geprüft/abgebildet |
| §§43/90+DVO§1, aktuell | Eigentum/Partner/Unterhalt; tatsächlicher Bestand | schonvermoegen; BG-Summen; Verbrauch | gesetzliche Kategorien; Modell363/863 | TEILWEISE GEPRÜFT; Verbrauchsannahme kein Tatsachennachweis |
| §44 Abs1–4, aktuell | Antrag, Zeitraum, Vorausleistung | Service/Saga-Prüfung, Monatsplan | statische 1–12 Monate; Tests gelesen | TEILWEISE GEPRÜFT; Antrag/Rückwirkung/§44a nicht vollständig modelliert |
| §§35/36 SGBX, aktuell | Gründe/Belehrung | BescheidGenerator | Textvergleich, gesetzliche Form | TEILWEISE GEPRÜFT; F05, Konfigvoraussetzungen |
| §37 Abs2 SGBX, aktuell | vierter Tag Inlandpost, Zugangsausnahme | belehrungText | amtlicher Wortlaut; Textabgleich | GEPRÜFT für richtige Tageszahl; Ausnahmen nicht vollständig erläutert |
| §84 SGG, aktuell | Widerspruch; Behördenformnorm | belehrungText | amtliche Verweisung36a/OZG | GEPRÜFT; F05 |
| §§57/78/87/88/73 SGG | Zuständigkeit, Modus, Fristen, Vertretung | Rechtsbehelf-Konfig/Generator | amtliche78/57, Textvergleich; historische Doku | TEILWEISE GEPRÜFT; keine landesrechtliche Einzelfallprüfung |
| Snapshot-/Zahlungsbindung | exakt bewilligter Monatsbetrag, historische Berechnung | Snapshot; PaymentBinding; Service/Saga | Codepfad; eigene Snapshot-/Nachdruckprobe | GEPRÜFT statisch für Binding; F04 beim Dokumentinhalt |
| Geldfluss | aktiv/Idempotenz/Monatseindeutigkeit/atomarer Storno | Service; ZahlungDao; BGDao; DDL | vollständige statische Guards; zentrale DB-Prüfung separat | TEILWEISE GEPRÜFT, keine eigene Nebenläufigkeits-/DB-Probe |
| Referenzunabhängigkeit | Rechenübereinstimmung muss Fachannahmen prüfen | Java/Node/Python | zwei synthetische Falschwerte, alle drei frisch akzeptiert | GEPRÜFT; F03; echter GLM-Gesamtpfad NICHT GEPRÜFT |

## Historische Fundnachprüfung

| Historischer Fund | Aktueller Befund / Evidenz |
|---|---|
| CR-003 Saga liest Payload statt DB, ignoriert Vermögen/KdU | Folgeschritte lesen gesperrten aktuellen DB-Bestand und konfigurierte Parameter. Statisch behoben. Initiale Saga-Erfassung bleibt auf einfachen Einzelfall ohne Mehrbedarfs-/Abzugsfelder begrenzt; kein Nachweis vollständiger Eingabegleichheit komplexer Neuanträge. |
| CR-007 Saga ohne 12-Monate-Prüfung | Payload-Validierung und Bescheidschritt rufen aktuelle gemeinsame Prüfung; Plan erzwingt 1–12. Statisch behoben. |
| CR-008 Ende Schwangerschaft fehlt | faelleFuerMonat entfernt nur Schwangerschaftszuschlag nach Entbindungsmonat; Probe658,71/563. Dynamisch behoben für bekannten Endemonat. |
| CR-009 Historisierte BG zahlbar | Fallanlage/-änderung, Bescheid, Auszahlung, Freigabe, Kassenübergabe prüfen Aktivität unter BG-Sperre. Statisch behoben für gelesene Pfade. Keine neue Behauptung über alle Rennen. |
| CR-014 Snapshot-Race ohne BG-Sperre | erteileBescheid und Saga-Bescheidschritt sperren BG vor Fällen/Vermögen. Statisch behoben. Nicht identisch mit F04: dessen Fehler ist fehlender historischer Dokumentparameter. |
| CR-015 fehlender Verbrauchsfortschrieb | Modellfortschrieb und Storno umgesetzt. Rechtliche Aussage des alten Funds zu pauschal: tatsächlicher Verbrauch muss festgestellt sein; bloßes monatliches Anrechnen oder virtuelles Reduzieren ist jeweils kein eigener Tatsachennachweis. |
| CR-016 Jahreswechsel/hartcodierte RBS/fehlender Bescheidbezug | Produktionspfade verwenden DB-Satzstand nach Monat; Binding fordert eindeutigen erteilten Bescheid und exakten Monatsbetrag. Statisch behoben. Änderungsbescheid fehlt weiterhin dokumentiert; Referenzen bleiben ausdrücklich2026. |
| CR-037 Deckel in Begründung nicht erklärt | Generator erkennt ungekappten Betrag und ergänzt Kappungsgrund. Statisch behoben. |
| CR-038 elektronische Widerspruchsform fehlt | Form ergänzt, aber falsche Rechtsnorm übernommen; F05. Alte Empfehlung selbst war insoweit unzutreffend. |
| CR-039 §73 Absatzdifferenz | Druck verwendet Abs1. Rechtsgrundlagen-Verifikationsprotokoll enthält weiterhin Abs2-Zeile; geringfügige Dokumentationsinkonsistenz, kein eigenständiger Zahlungsfehler. |
| CR-040 Rechtsbehelf-Modus nicht validiert | LmgConfig:141–146 validiert WIDERSPRUCH/KLAGE; statisch behoben für regulären Start. |
| CR-041 Insert-Zeitbasis/mehrfaches Datum | Zahlung/Bescheid-DAO lesen eingefügte Zeile; Datum einmal an Erteilung berechnet. Statisch behoben. |
| N03 Monatsplan fehlt | Monatsplan v2 vorhanden; eigene Vermögens-/Schwangerschaftsprobe bestätigt Berechnungsmonate. Kein Beweis gesetzlich richtiger Verbrauchsannahme. |
| N05 Saga verarbeitet inaktive BG | lockActiveBg in Schritten2–4 vorhanden. Statisch behoben. |
| Eigener SQL-Syntaxverdacht FOR UPDATE ORDER BY | Zentraler Koordinator hat Oracle-Free-Parseprobe ohne ORA bestätigt. Verdacht für diese DB verworfen; hier kein bestätigter Befund. 19c nicht geprüft. |

## Offene Fragen ohne bestätigte Fehlerstatistik

- Kein eigener Oracle19c-Lauf und kein Lauf unter Java21.
- Keine eigene Zeitsteuerungs-/Netzwerkfehlerprobe der Auszahlungs-/Saga-Transaktionen; Kernreview liefert deren Evidenz.
- Kein Nachweis, wie echte GLM5.3-Antworten die falsch vorgegebenen Fachannahmen erkennen oder übernehmen. F03 beweist allein das Versagen aller drei arithmetischen Referenzen bei diesen Gegenbeispielen.
- Landesrechtlicher Entfall des Widerspruchsverfahrens, zuständige konkrete Behörde/Gericht, Einzelfallfolgen falscher Belehrung nicht abschließend rechtlich beurteilt.
- Vollständige Änderungs-, Aufhebungs-, Bekanntgabe- und Vermögensnachweisverfahren fehlen im Modell; daraus wird kein tatsächlich aufgetretener realer Fehlbescheid konstruiert.
