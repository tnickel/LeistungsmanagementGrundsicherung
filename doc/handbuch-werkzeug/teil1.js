// teil1.js — Teil I: Anwenderdokumentation (Kapitel 1-12)
"use strict";
const { Paragraph, TextRun, PageBreak, AlignmentType } = require("docx");
const {
  P, FONT, h1, h2, h3, abs, punkt, schritt, hinweis, tabellenTitel, tabelle, abbild, teilSeite,
} = require("./lib");

const inhalt = [];

// ================================================================ 1 Einleitung
inhalt.push(h1("1  Einleitung"));
inhalt.push(h2("1.1  Zweck dieses Handbuchs"));
inhalt.push(abs("Dieses Benutzerhandbuch beschreibt die Fachanwendung SDN.sozial Leistungsmanagement Grundsicherung (kurz: SDN.sozial LMG) in allen Teilen, die für den Arbeitsalltag relevant sind. Es richtet sich in Teil I an Sachbearbeiterinnen und Sachbearbeiter sowie Administratorinnen und Administratoren der Sozialverwaltung, die mit dem Modul Grundsicherungsleistungen im Alter und bei Erwerbsminderung nach dem Zwölften Buch Sozialgesetzbuch (SGB XII) bearbeiten. Teil II wendet sich an technisch interessierte Leserinnen und Leser: Er erklärt die darunterliegende eigene Transaktionssteuerung, ihre Bausteine und die Art und Weise, wie die Fachfunktionen darauf realisiert sind."));
inhalt.push(abs("Das Handbuch verbindet beide Perspektiven bewusst in einem Dokument: Wer Leistungen sachbearbeitet, versteht nach der Lektüre von Teil I, welche fachlichen Regeln das System durchsetzt und warum Meldungen wie „KONKURRENZ“ oder „KEIN_ANSPRUCH“ auftreten. Wer die Anwendung betreibt oder weiterentwickelt, findet in Teil II die Begründung der technischen Entscheidungen bis hinunter zu den Datenbank-Tabellen und den Fehlerbehandlungsstrategien."));

inhalt.push(h2("1.2  Das System in einem Satz"));
inhalt.push(abs("SDN.sozial LMG ist eine Web-Anwendung, mit der Bedarfsgemeinschaften erfasst, der monatliche Leistungsanspruch nach SGB XII rechnerisch ermittelt, der Anspruch durch einen Bewilligungsbescheid nach § 37 SGB X verbindlich gemacht und die Auszahlung mit Vier-Augen-Kontrolle, revisionssicherem Journal und automatischer Kassenlauf-Übergabe angeordnet wird. Alle schreibenden Vorgänge laufen über eine eigens entwickelte Transaktionssteuerung, die die Datenbank-Transaktionen von Oracle steuert und absichert."));
inhalt.push(...abbild("d01_architektur.png", "Bausteine und Schichten der Lösung — vom Browser bis zur Oracle-Datenbank"));

inhalt.push(h2("1.3  Zielgruppen und Lesepfad"));
inhalt.push(tabellenTitel("Zielgruppen und empfohlene Lesepfade"));
inhalt.push(tabelle(
  ["Zielgruppe", "Relevante Kapitel", "Ziel des Lesens"],
  [
    ["Sachbearbeiter/in Grundsicherung", "Kapitel 2 bis 9 und 12", "Vorgänge sicher bearbeiten, Fehlermeldungen einordnen"],
    ["Teamleitung / Administrator", "Kapitel 3, 7, 10 bis 12, 19", "Rollen, Freigaben, Kassenlauf und Wiederanlauf beherrschen"],
    ["Technische Beratung / Entwicklung", "Teil II (Kapitel 13 bis 20)", "Architektur, Transaktionssteuerung und Datenmodell verstehen"],
    ["Betrieb / IT-Verwaltung", "Kapitel 11 und 19", "Konfiguration, Start, Monitoring und Wiederanlauf"],
  ],
  [28, 30, 42],
));

inhalt.push(h2("1.4  Konventionen dieses Handbuchs"));
inhalt.push(punkt([{ t: "Fachbegriffe wie ", }, { t: "Bedarfsgemeinschaft", fett: true }, { t: " oder ", }, { t: "SozialFall", fett: true }, { t: " werden genau so geschrieben, wie sie auch in der Oberfläche und im Datenmodell erscheinen — Bedarfsgemeinschaft ist der Vorgang, SozialFall der Leistungsberechtigte innerhalb dieses Vorgangs." }]));
inhalt.push(punkt([{ t: "Oberflächenelemente stehen in Anführungszeichen, z. B. Schaltfläche „Auszahlung anordnen“ oder Karte „Zahlbarmachung“." }]));
inhalt.push(punkt([{ t: "Technische Bezeichner (Tabellen, Klassen, Endpunkte) erscheinen in Nichtproportionalschrift, z. B. TX_SAGA, TxManager, POST /api/auszahlung." }]));
inhalt.push(punkt([{ t: "Gesetzeszitate beziehen sich auf SGB XII, sofern nicht ausdrücklich SGB X genannt ist." }]));
inhalt.push(abs([{ t: "Alle Beträge, Prozentsätze und Fristen in diesem Handbuch entsprechen dem ", }, { t: "Referenzjahr 2026", fett: true }, { t: " und dem Stand der Software-Version 0.4.0 (Oktober 2026)." }]));

inhalt.push(h2("1.5  Demo-Zugänge"));
inhalt.push(abs("Die Auslieferung enthält drei vorkonfigurierte Demo-Konten (Kapitel 3). Für eine Produktivnutzung werden die Konten über die Umgebungsvariable LMG_BENUTZER beziehungsweise den Konfigurationsschlüssel des Servers gesetzt (Kapitel 19)."));
inhalt.push(tabellenTitel("Vorkonfigurierte Demo-Konten"));
inhalt.push(tabelle(
  ["Kennung", "Passwort", "Rolle", "Typische Aufgaben"],
  [
    ["admin-01", "admin", "admin", "Systemstatus, Kassenlauf, Storno bereit-Buchungen, Wiederanlauf"],
    ["sb-01", "sb-01", "sachbearbeiter", "Vorgänge bearbeiten, Auszahlungen anordnen, Freigaben"],
    ["sb-02", "sb-02", "sachbearbeiter", "wie sb-01 — zweite Kennung für das Vier-Augen-Prinzip"],
  ],
  [16, 14, 22, 48],
));

// ================================================================ 2 Fachliche Grundlagen
inhalt.push(h1("2  Fachliche und rechtliche Grundlagen (SGB XII)"));

inhalt.push(h2("2.1  Leistungen der Grundsicherung im Alter und bei Erwerbsminderung"));
inhalt.push(abs("Die Grundsicherung im Alter und bei Erwerbsminderung ist in den §§ 41 bis 46b SGB XII geregelt. Sie steht Personen zu, die die Altersgrenze erreicht haben oder dauerhaft voll erwerbsgemindert sind und ihren notwendigen Lebensunterhalt nicht aus eigenen Mitteln und Einkünften bestreiten können. Anders als beim Arbeitslosengeld II steht diese Leistung nicht zur Arbeitsmarktvermittlung, sondern zur existenzsichernden Sicherung des Lebensunterhalts. Der Anspruch umfasst den Regelbedarf zum Leben, die Kosten der Unterkunft und Heizung sowie Mehrbedarfe in besonderen Lebenslagen; Einkommen und Vermögen werden nach §§ 82 und 90 SGB XII angerechnet."));
inhalt.push(abs("SDN.sozial LMG bildet genau diese Prüfung rechnerisch ab: Die Anwendung ermittelt je Leistungsberechtigtem (SozialFall) den Bedarf, summiert ihn auf die Bedarfsgemeinschaft, rechnet das zu berücksichtigende Einkommen gegen und weist den verbleibenden Zahlbetrag aus. Die rechtliche Verbindlichkeit erhält das Ergebnis durch den Bewilligungsbescheid nach § 37 SGB X (Kapitel 8)."));

inhalt.push(h2("2.2  Regelbedarfe und Mehrbedarfe"));
inhalt.push(abs("Der Regelbedarf deckt den Lebensunterhalt mit Nahrung, Kleidung, Körperpflege, Hausrat, Haushaltsenergie (ohne Heizung) und persönlichen Bedürfnissen des Tages. Er ist nach Regelbedarfsstufen (RBS) gestaffelt, die sich nach dem Personenkreis richten. Für das Referenzjahr 2026 sind in der Anwendung alle sechs Stufen hinterlegt:"));
inhalt.push(tabellenTitel("Regelbedarfsstufen 2026 (hinterlegte Werte der Anwendung)"));
inhalt.push(tabelle(
  ["Stufe", "Betrag 2026", "Typischer Personenkreis"],
  [
    ["RBS 1", "563,00 EUR", "alleinstehende volljährige Personen (Hauptfall)"],
    ["RBS 2", "506,00 EUR", "Ehegatten/Lebenspartner in Bedarfsgemeinschaft"],
    ["RBS 3", "451,00 EUR", "volljährige Personen ohne eigenen Haushalt (sonstige BG-Mitglieder)"],
    ["RBS 4", "471,00 EUR", "Jugendliche von 14 bis 17 Jahren"],
    ["RBS 5", "390,00 EUR", "Kinder von 6 bis 13 Jahren"],
    ["RBS 6", "357,00 EUR", "Kinder bis 5 Jahre"],
  ],
  [12, 20, 68],
));
inhalt.push(abs("Zu dem Regelbedarf treten Mehrbedarfe nach § 30 SGB XII. Die Anwendung unterstützt die beiden automatisierbaren Fallgruppen:"));
inhalt.push(punkt([{ t: "Merkzeichen G (§ 30 Abs. 1 SGB XII): ", fett: true }, { t: "17 % des Regelbedarfs für behinderungsbedingte Mehrkosten bei gehinderter Fortbewegung — bei RBS 1 genau 95,71 EUR, bei RBS 2 genau 86,02 EUR." }]));
inhalt.push(punkt([{ t: "Dezentrale Warmwassererzeugung (§ 30 Abs. 7 SGB XII): ", fett: true }, { t: "2,3 % des Regelbedarfs, wenn Warmwasser nicht zentral bereitgestellt wird — bei RBS 1 genau 12,95 EUR, bei RBS 2 genau 11,64 EUR." }]));
inhalt.push(hinweis("Fachliche Sorgfalt", "Die Anwendung berechnet Mehrbedarfe nur, wenn sie in der SozialFall-Maske ausdrücklich angekreuzt sind. Die Prüfung, ob ein Merkzeichen nachweislich vorliegt oder Warmwasser tatsächlich dezentral erzeugt wird, bleibt Aufgabe der Sachbearbeitung — das System verhindert keine Überprüfung, es rechnet korrekt."));

inhalt.push(h2("2.3  Kosten der Unterkunft und Heizung"));
inhalt.push(abs("Die Kosten der Unterkunft (KdU) nach § 35 SGB XII werden nach der „Dreiteilung“ erfasst: anerkannte Kaltmiete, kalte Betriebskosten und Heizkosten. Die Anwendung erfasst alle drei Beträge je SozialFall und summiert sie bedarfsgemeinschaftsweit. Die Angemessenheit wird nur über eine konfigurierbare Obergrenze je Bedarfsgemeinschaft geprüft (§ 35 Abs. 7 SGB XII); darüber hinausgehende Kosten werden nicht anerkannt. Karenzzeit und Absenkungsfrist sind nicht abgebildet und bleiben der Fachprüfung vorbehalten."));

inhalt.push(h2("2.4  Einkommensbereinigung nach § 82 SGB XII"));
inhalt.push(abs("Dem Bedarf wird das Einkommen gegen gerechnet. Die Anwendung behandelt die beiden typischen Einkommensarten der Zielgruppe unterschiedlich: Renteneinkünfte werden voll angerechnet; aus brutto Erwerbseinkommen wird ein Erwerbsfreibetrag abgezogen, der dem Gesetzestext von § 82 Abs. 3 SGB XII folgt: 30 % des Brutto-Erwerbseinkommens, begrenzt auf die Hälfte der Regelbedarfsstufe 1 — im Referenzjahr also höchstens 281,50 EUR. Beispiel: Wer 1.500,00 EUR brutto verdient, behält 281,50 EUR anrechnungsfrei; wer 800,00 EUR verdient, behält 30 % davon, also 240,00 EUR, weil der Betrag unter der Kappungsgrenze liegt."));
inhalt.push(abs("Der Zahlbetrag ergibt sich als Gesamtbedarf der Bedarfsgemeinschaft minus anrechenbarem Einkommen. Ist das Ergebnis null oder negativ, besteht kein Leistungsanspruch — die Anwendung meldet in diesem Fall KEIN_ANSPRUCH und lässt keine Auszahlung zu (Abbildung 11)."));
inhalt.push(...abbild("d05_bedarfsberechnung.png", "Berechnungsfluss von Regelbedarf, Mehrbedarf, KdU und Einkommen zum Zahlbetrag"));

inhalt.push(h2("2.5  Der Vorgang: Bedarfsgemeinschaft und SozialFall"));
inhalt.push(abs([{ t: "Die Anwendung arbeitet mit zwei zentralen Fachbegriffen: Die ", }, { t: "Bedarfsgemeinschaft", fett: true }, { t: " ist der Vorgang — sie trägt ein eindeutiges Aktenzeichen, eine Hauptperson, ein Restbudget für die laufende Bewilligung und die SEPA-Bankverbindung. Der ", }, { t: "SozialFall", fett: true }, { t: " ist ein einzelner Leistungsberechtigter innerhalb der Bedarfsgemeinschaft mit seiner Regelbedarfsstufe, seinen Mehrbedarf-Merkzeichen, seinen Unterkunftskosten und seinem Einkommen. Ein Paar in einer gemeinsamen Wohnung ist ein Vorgang mit zwei SozialFällen; der Zahlbetrag wird über beide Fälle summiert und auf die Bedarfsgemeinschaft ausgezahlt." }]));
inhalt.push(hinweis("Namenskonvention", "Die Begriffe Bedarfsgemeinschaft und SozialFall durchdringen Oberfläche, Datenmodell und Dokumentation einheitlich (Tabelle SOZIAL_FALL). Wer aus anderen Fachverfahren „Leistungsfall“ oder „BG“ gewohnt ist, findet beides wieder: „BG“ steht in Aktenzeichen wie BG-2026-0001."));

// ================================================================ 3 Anmeldung und Rollen
inhalt.push(h1("3  Anmeldung und Rollen"));
inhalt.push(h2("3.1  Anmelden"));
inhalt.push(abs("Die Oberfläche zeigt vor der ersten Aktion eine Anmeldemaske. Anmelden mit der Behörden-Kennung und dem zugehörigen Passwort; die Schaltfläche „Anmelden“ bestätigt. Nach erfolgreicher Anmeldung zeigt die Seitenleiste unten die angemeldete Kennung mit der Rolle, zum Beispiel „Angemeldet: admin-01 (Admin)“."));
inhalt.push(...abbild("01_anmeldung.png", "Anmeldemaske der SDN.sozial-Oberfläche"));
inhalt.push(abs("Technisch stellt die Anmeldung ein Sitzungs-Token aus, das der Browser zu jeder weiteren Anfrage mitsendet. Sitzungen liegen im Speicher des Servers: Nach einem Server-Neustart ist eine neue Anmeldung erforderlich. Das ist bewusst so gebaut — für den echten Betrieb gehört an diese Stelle ein Verzeichnisdienst (LDAP/Active Directory) hinter HTTPS, das Handbuch von Teil II (Kapitel 18) beschreibt die serverseitige Prüfung im Detail."));

inhalt.push(h2("3.2  Rollen und Rechte"));
inhalt.push(abs("Die Anwendung kennt zwei Rollen. Die Rechteprüfung findet ausschließlich auf dem Server statt — die Oberfläche blendet Funktionen lediglich aus, ein Umgehen über die Schnittstelle ist dadurch wirkungslos:"));
inhalt.push(tabellenTitel("Rollen und Berechtigungen im Überblick"));
inhalt.push(tabelle(
  ["Funktion", "sachbearbeiter", "admin"],
  [
    ["Fallübersicht, Vorgänge öffnen und speichern", "ja", "ja"],
    ["SozialFälle anlegen und ändern", "ja", "ja"],
    ["Auszahlung anordnen (Zahlbarmachung)", "ja", "ja"],
    ["Zahlung freigeben (Vier-Augen, als zweite Person)", "ja", "ja (mit Ausnahme bei Kennung „admin“)"],
    ["Buchungsjournal einsehen", "ja", "ja"],
    ["Systemstatus (Adminbereich) einsehen", "nein (403)", "ja"],
    ["Kassenlauf ausführen", "nein (403)", "ja"],
    ["Storno einer freigegebenen (BEREIT) Zahlung", "nein (403)", "ja"],
    ["Saga-Wiederanlauf anstoßen", "nein (403)", "ja"],
  ],
  [56, 20, 24],
));
inhalt.push(abs([{ t: "Wichtig für die Praxis: ", fett: true }, { t: "die Sachbearbeiter-Kennung, die im Buchungsjournal erscheint, kommt ausschließlich aus der Anmeldung. Eine Kennung im Anfragekörper wird ignoriert — jedes Journal-Ereignis ist damit der angemeldeten Person zugeordnet." }]));
inhalt.push(h2("3.3  Abmelden und Sitzungsende"));
inhalt.push(abs("Über „Abmelden“ in der Seitenleiste endet die Sitzung; das Token wird serverseitig verworfen. Weil Sitzungen im Speicher liegen, enden sie auch beim Neustart des Servers. In beiden Fällen zeigt die Oberfläche beim nächsten Aufruf wieder die Anmeldemaske."));

// ================================================================ 4 Fallübersicht
inhalt.push(h1("4  Die Fallübersicht"));
inhalt.push(h2("4.1  Aufbau der Übersicht"));
inhalt.push(abs("Nach der Anmeldung landet man in der Fallübersicht. Sie kombiniert vier Kennzahlen-Karten mit der Vorgangstabelle:"));
inhalt.push(punkt([{ t: "Aktive Vorgänge: ", fett: true }, { t: "Anzahl der Bedarfsgemeinschaften im Status „aktiv“ (historisierte zählen nicht)." }]));
inhalt.push(punkt([{ t: "Zahlbetrag-Vorschau (Summe): ", fett: true }, { t: "Summe der aktuell berechneten Zahlbeträge aller aktiven Vorgänge — die unmittelbare Monatslast-Vorschau." }]));
inhalt.push(punkt([{ t: "Zahlungen bereit (30 Tage): ", fett: true }, { t: "Anzahl und Umfang der Zahlungen im Status BEREIT, die beim nächsten Kassenlauf übergeben werden." }]));
inhalt.push(punkt([{ t: "Fristen / Wiedervorlage: ", fett: true }, { t: "Anzahl der Vorgänge, deren Wiedervorlagedatum erreicht ist oder bevorsteht." }]));
inhalt.push(...abbild("02_falluebersicht.png", "Fallübersicht mit Kennzahlen-Karten, Suchfeld und Vorgangstabelle"));
inhalt.push(abs("Die Vorgangstabelle listet Aktenzeichen, Hauptperson, Status (Chip „aktiv“ oder „historisiert“), Restbudget, Zahlbetrag-Vorschau und Wiedervorlagedatum. Mit „Öffnen“ gelangt man in die Vorgangsmaske (Kapitel 5). Das Suchfeld rechts oben filtert inkrementell nach Aktenzeichen oder Name — bereits beim Tippen."));

inhalt.push(h2("4.2  Neuen Vorgang anlegen"));
inhalt.push(abs("Über die Schaltfläche „+ Neuer Vorgang“ klappt die Anlagemaske auf. Pflichtangaben sind ein eindeutiges Aktenzeichen und der Name der Hauptperson; empfohlen sind Startbudget, Wiedervorlage und die SEPA-Bankverbindung (IBAN, BIC, Kontoinhaber), die als Standard für spätere Auszahlungen dient."));
inhalt.push(...abbild("03_neuer_vorgang.png", "Anlagemaske „Neuen Vorgang anlegen“ über der Fallübersicht"));
inhalt.push(abs([{ t: "Zu beachten: ", fett: true }, { t: "das Aktenzeichen wird auf Eindeutigkeit geprüft — ein Doppeldatum bricht mit dem fachlichen Hinweis VALIDATION ab (die Datenbank erzwingt die Eindeutigkeit über einen UNIQUE-Schlüssel). Das Startbudget begrenzt die Auszahlungen der laufenden Bewilligung: Bei jeder Anordnung mindert das System es, bei Storno bucht es zurück. Wer das Budget auf 0 lässt, kann zunächst keine Auszahlungen anordnen (BUDGET_ERSCHOEPFT)." }]));
inhalt.push(abs("Nach dem Anlegen öffnet sich die Vorgangsmaske; der erste SozialFall lässt sich direkt im Bereich „SozialFälle bearbeiten“ hinzufügen. Beide Aktionen erscheinen sofort revisionssicher im Buchungsjournal."));

// ================================================================ 5 Vorgangsbearbeitung
inhalt.push(h1("5  Vorgang bearbeiten"));
inhalt.push(h2("5.1  Aufbau der Vorgangsmaske"));
inhalt.push(abs("Die Vorgangsmaske bündelt alles zu einem Vorgang: oben die Bedarfsermittlung, darunter die editierbaren SozialFall-Masken, dann Zahlbarmachung, Bewilligungsbescheid und Zahlungshistorie; rechts stehen der Vorgangskopf und das Journal des Vorgangs."));
inhalt.push(...abbild("05_vorgang_bedarfsermittlung.png", "Vorgangsmaske mit Bedarfsermittlung, SozialFall-Maske, Zahlbarmachung, Vorgangskopf und Journal"));

inhalt.push(h2("5.2  Bedarfsermittlung lesen"));
inhalt.push(abs("Der Bereich „Bedarfsermittlung (SGB XII)“ ist rein lesend. Die Tabelle zeigt je SozialFall den Regelbedarf, die Mehrbedarfe (mit Aufschlüsselung als Hinweistext), die Kosten der Unterkunft, den Gesamtbedarf, den Erwerbsfreibetrag, das anrechenbare Einkommen und den Zahlbetrag des Falls. Der Ergebnisstreifen darunter fasst zusammen: Gesamtbedarf, anrechenbares Einkommen und Auszahlungsbetrag der ganzen Bedarfsgemeinschaft. Jede Änderung an einem SozialFall aktualisiert diese Werte unmittelbar."));
inhalt.push(abs([{ t: "Ein negativer Auszahlungsbetrag ist keine Fehlfunktion, sondern das fachliche Signal „Einkommen übersteigt den Bedarf“: ", fett: true }, { t: "die Auszahlungsanordnung wird dann abgewiesen (Kapitel 7, Abbildung 15)." }]));

inhalt.push(h2("5.3  SozialFall anlegen und ändern"));
inhalt.push(abs("Über „+ Weiteren SozialFall anlegen“ erhält der Vorgang einen weiteren Leistungsberechtigten — typisch für Paare. Die Fallmaske enthält Regelbedarfsstufe (Stufen 1 bis 6), die drei KdU-Komponenten, Renten- und Erwerbseinkommen sowie die beiden Mehrbedarf-Ankreuzfelder. „Speichern“ schreibt die Änderung mit einer Journalzeile fort."));
inhalt.push(abs([{ t: "Sperrschutz beim Speichern: ", fett: true }, { t: "jede Fallmaske trägt eine Versionsnummer. Speichern zwei Bearbeiter gleichzeitig, gewinnt der erste; der zweite erhält die Meldung „Änderung durch anderen Bearbeiter“ (Fachcode KONKURRENZ), und die Maske lädt den neuen Stand automatisch neu. Es kann also nie jemand unabsichtlich die Änderung eines Kollegen überschreiben (kein „Lost Update“). Einfach erneut bearbeiten und wieder speichern." }]));

inhalt.push(h2("5.4  Vorgangskopf: Status, Wiedervorlage, Bankverbindung"));
inhalt.push(abs("Im Vorgangskopf stehen Aktenzeichen, Hauptperson, Restbudget und Version als Leseinfos; änderbar sind Status und Wiedervorlagedatum sowie die Standard-Bankverbindung. Der Statuswechsel von „aktiv“ auf „historisiert“ schließt den Vorgang: Historisierte Vorgänge akzeptieren keine neuen Auszahlungen mehr, bleiben aber samt Journal lesbar. Die Standard-Bankverbindung greift, wenn eine Auszahlung ohne abweichende Empfängerangabe angeordnet wird — sie sollte daher stets gepflegt sein (Kapitel 8 zeigt den Bescheid mit dieser Verbindung)."));
inhalt.push(abs("Das Wiedervorlagedatum steuert das Fristen-Widget der Fallübersicht — übliche Nutzung ist die Wiederholungsbearbeitung zum Bewilligungsende oder eine Rückfrage-Frist."));

// ================================================================ 6 Zahlbarmachung
inhalt.push(h1("6  Zahlbarmachung: Auszahlung anordnen"));
inhalt.push(h2("6.1  Die Maske"));
inhalt.push(abs("Der Bereich „Zahlbarmachung“ der Vorgangsmaske ordnet die Auszahlung an: Empfänger/Kontoinhaber, IBAN, BIC und Zahlungsmonat (Monat des Anspruchs, nicht des Zahlungslaufs). Vorbefüllt sind der Hauptperson-Name und der laufende Monat."));
inhalt.push(...abbild("06_zahlbarmachung_bescheid.png", "Zahlbarmachung mit Vier-Augen-Checkbox, Bewilligungsbescheid-Bereich und Zahlungshistorie"));

inhalt.push(h2("6.2  Was beim Anordnen passiert"));
inhalt.push(abs("Hinter der Schaltfläche „Auszahlung anordnen“ läuft eine einzige kurze Datenbank-Transaktion ab, die mehrere Schutzmechanismen in festen Reihenfolge kombiniert (Teil II, Kapitel 17, zeigt die technische Seite):"));
inhalt.push(schritt(1, [{ t: "Idempotenz: ", fett: true }, { t: "Die Vorgangsnummer der Zahlung ist eindeutig. Wird dieselbe Anordnung ein zweites Mal geschickt (Doppelklick, Wiederholung), antwortet das System mit „Bereits gebucht“ und bucht nichts erneut." }]));
inhalt.push(schritt(2, [{ t: "Sperre: ", fett: true }, { t: "Der Vorgang wird für die Dauer der Buchung exklusiv gesperrt; parallele Anordnungen zweier Sachbearbeiter laufen nacheinander ab." }]));
inhalt.push(schritt(3, [{ t: "Anspruchsprüfung: ", fett: true }, { t: "Der Zahlbetrag wird frisch berechnet. Er muss größer null sein (sonst KEIN_ANSPRUCH) und darf das Restbudget nicht übersteigen (sonst BUDGET_ERSCHOEPFT)." }]));
inhalt.push(schritt(4, [{ t: "SEPA-Prüfung: ", fett: true }, { t: "IBAN und BIC werden nach ISO 13616 (Modulo-97-Prüfziffer) geprüft; ungültige Verbindungen brechen mit VALIDATION ab." }]));
inhalt.push(schritt(5, [{ t: "Buchung: ", fett: true }, { t: "Die Zahlung entsteht im Status ANGELEGT (mit Vier-Augen) oder BEREIT (ohne), das Restbudget mindert sich, und der Journaleintrag entsteht unter der eigenen Kennung." }]));

inhalt.push(h2("6.3  Die Vier-Augen-Checkbox"));
inhalt.push(abs("Standardmäßig ist „Vier-Augen-Prüfung erforderlich“ angekreuzt. Die Zahlung entsteht dann im Status ANGELEGT und wartet auf die Freigabe durch eine zweite Person (Kapitel 7). Nur wer die Checkbox bewusst entfernt, ordnet sofort im Status BEREIT an — der Erfasser erscheint dann zugleich als Freigebender, was nur für die Sonderkennung „admin“ zulässig ist, wenn später doch eine zweite Freigabe erforderlich wird."));
inhalt.push(...abbild("07_auszahlung_dialog.png", "Bestätigungsdialog nach der Anordnung — Status „angelegt“, neues Restbudget"));

// ================================================================ 7 Vier Augen
inhalt.push(h1("7  Vier-Augen-Freigabe, Storno und Fehlerfälle"));
inhalt.push(h2("7.1  Der Freigabe-Schritt"));
inhalt.push(abs("Eine ANGELEGT-Zahlung erscheint in der Zahlungshistorie mit den Schaltflächen „Freigeben“ und „Storno“. Die Freigabe ist die rechnerische Feststellung durch den Zweitsachbearbeiter: Das System prüft beim Freigeben erneut Anspruch und Budget und stellt dadurch sicher, dass zwischen Erfassung und Freigabe keine Änderung unbemerkt bleibt. Nach der Freigabe steht die Zahlung im Status BEREIT für den Kassenlauf."));
inhalt.push(abs([{ t: "Selbstfreigabe ist ausgeschlossen: ", fett: true }, { t: "Wer eine Zahlung selbst erfasst hat, kann sie nicht selbst freigeben — das System antwortet mit „Vier-Augen-Prinzip verletzt: Erfasser und Freigebender dürfen nicht identisch sein“ (Abbildung 9). Die Freigabe gehört also zwingend einer zweiten Kennung." }]));
inhalt.push(...abbild("08_selbstfreigabe_abgewehrt.png", "Abwehr der Selbstfreigabe — der Erfasser kann seine eigene Zahlung nicht freigeben"));
inhalt.push(abs("Abbildung 10 zeigt dieselbe Zahlung aus der Sicht der zweiten Person (sb-01): Die Freigabe wird nach der Sicherheitsabfrage vollzogen, der Status-Chip wechselt auf „bereit“, die Spalte „Freigegeben“ trägt die Kennung des Freigebenden, und das Journal vermerkt die Freigabe als „rechnerisch festgestellt“."));
inhalt.push(...abbild("09_freigabe_sb01_bereit.png", "Freigabe durch die zweite Person — Status „bereit“, Journal-Eintrag mit „rechnerisch festgestellt“"));

inhalt.push(h2("7.2  Storno"));
inhalt.push(abs("Zahlungen im Status ANGELEGT kann jede Sachbearbeiterkennung stornieren; im Status BEREIT ist das der Administration vorbehalten (übergebene Zahlungen im Status ÜBERGEBEN sind endgültig). Beim Storno setzt das System den Status auf STORNIERT, bucht den Betrag auf das Restbudget zurück und schreibt einen Journaleintrag — alles atomar in einer Transaktion. Die Sicherheitsabfrage weist ausdrücklich auf die Rückbuchung hin."));

inhalt.push(h2("7.3  Typische Fehlermeldungen beim Anordnen"));
inhalt.push(abs("Die Anwendung formuliert fachliche Abweisungen verständlich und nennt im Dialog stets den Grund. Die beiden häufigsten Fälle bei der Anordnung sind:"));
inhalt.push(punkt([{ t: "KEIN_ANSPRUCH: ", fett: true }, { t: "Das anrechenbare Einkommen übersteigt den Gesamtbedarf — der Zahlbetrag ist null oder negativ. Prüfen Sie Einkommens- und Bedarfsangaben; bei rechtmäßiger Rechnung besteht keine Leistung zu bewilligen." }]));
inhalt.push(punkt([{ t: "BUDGET_ERSCHOEPFT: ", fett: true }, { t: "Der Zahlbetrag übersteigt das Restbudget des Vorgangs. Restbudget prüfen und entweder das Startbudget anheben oder eine ältere Zahlung stornieren, um Raum zu schaffen." }]));
inhalt.push(abs("Abbildung 11 zeigt die Abweisung am Demovorgang BG-2026-0003 (Yilmaz, Cem): Die Bedarfsermittlung weist einen negativen Auszahlungsbetrag von −195,50 EUR aus, der Dialog begründet die Abweisung mit dem negativen Zahlbetrag — gebucht wurde in diesem Fall nichts:", { keepNext: true }));
inhalt.push(...abbild("15_kein_anspruch.png", "Fachliche Abweisung KEIN_ANSPRUCH — das anrechenbare Einkommen übersteigt den Gesamtbedarf"));
inhalt.push(abs("Die vollständige Liste aller Fachcodes mit Bedeutung und typischer Lösung steht in Kapitel 12.3."));

// ================================================================ 8 Bescheid
inhalt.push(h1("8  Bewilligungsbescheid (§ 37 SGB X)"));
inhalt.push(h2("8.1  Bescheid erteilen"));
inhalt.push(abs("Der Bereich „Bewilligungsbescheid (§ 37 SGB X)“ macht den rechnerischen Anspruch rechtlich verbindlich. Bewilligungszeitraum festlegen (Vorbefüllung: laufender Monat bis zwölf Monate später) und „Bescheid erteilen“ drücken. Das System erzeugt eine eindeutige Bescheidnummer nach dem Muster BESCH-<Aktenzeichen>-<Monat>-<Nummer>, übernimmt den aktuell berechneten Zahlbetrag als monatlichen Bewilligungsbetrag und journalisiert die Erteilung."));
inhalt.push(...abbild("10_bescheid_erteilt.png", "Erteilter Bewilligungsbescheid mit Nummer, Zeitraum und monatlichem Zahlbetrag"));

inhalt.push(h2("8.2  Aufbau und Druck des Bescheids"));
inhalt.push(abs("Der amtliche Bescheid enthält alle Pflichtelemente des § 37 SGB X: den Verfügungssatz (was wird für welchen Zeitraum in welcher Höhe bewilligt), die Berechnung des monatlichen Leistungsanspruchs je Person, die Zahlungsanweisung mit der hinterlegten SEPA-Verbindung, die Begründung nach § 35 SGB X (Verweis auf die Anspruchsgrundlagen der §§ 41 ff. SGB XII, die KdU-Berücksichtigung nach § 35 SGB XII und die Einkommensanrechnung nach § 82 SGB XII) sowie die Rechtsbehelfsbelehrung nach § 36 SGB X (Widerspruch binnen eines Monats nach Bekanntgabe)."));
inhalt.push(...abbild("11_bescheid_druck.png", "Amtliche Druckansicht des Bewilligungsbescheids — Briefkopf, Verfügungssatz, Berechnung, Begründung, Rechtsbehelfsbelehrung"));
inhalt.push(abs("Über „Amtlichen Bescheid drucken (PDF)“ öffnet sich die Druckansicht in einem eigenen Browserfenster; der Druckdialog des Browsers erzeugt daraus das PDF für die Akte. Ein Vorgang trägt jeweils den letzten erteilten Bescheid; Aufhebung (etwa durch eine Saga-Kompensation, Kapitel 10) setzt den Status auf AUFGEHOBEN."));

// ================================================================ 9 Journal
inhalt.push(h1("9  Das Buchungsjournal"));
inhalt.push(abs("Das Buchungsjournal ist das revisionssichere Gedächtnis der Anwendung. Jede fachlich relevante Aktion erzeugt einen unveränderlichen Eintrag mit Zeitpunkt, Vorgangsbezug, Klartext der Aktion und der zuständigen Sachbearbeiter-Kennung aus der Anmeldung. Systemisch ausgelöste Änderungen (etwa die Versions-Erhöhung beim Speichern) erscheinen mit der Kennung „system“."));
inhalt.push(...abbild("12_buchungsjournal.png", "Buchungsjournal mit zeitreversibler Ereignisliste — Kassenläufe, Anordnungen, Freigaben, Bescheide"));
inhalt.push(punkt([{ t: "Überlebt Historisierung: ", fett: true }, { t: "das Journal referenziert Vorgänge logisch (ohne Fremdschlüssel-Abhängigkeit). Auch ein gelöschter oder historisierter Vorgang lässt seine Geschichte weiterlesen." }]));
inhalt.push(punkt([{ t: "Vier-Augen sichtbar: ", fett: true }, { t: "Erfassung und Freigabe erscheinen als getrennte Einträge unterschiedlicher Kennungen — revisionssicher nachvollziehbar." }]));
inhalt.push(punkt([{ t: "Kassenlauf-Sammelsatz: ", fett: true }, { t: "der Kassenlauf schreibt eine einzige Zeile mit Anzahl und Summe (z. B. „Kassenlauf: 4 Zahlungen über 3.117,71 EUR übergeben“)." }]));
inhalt.push(abs("Zusätzlich zeigt die Vorgangsmaske rechts unten das „Journal (Vorgang)“ — dieselbe Historie gefiltert auf den geöffneten Vorgang."));

// ================================================================ 10 Saga aus Anwendersicht
inhalt.push(h1("10  Die Antrags-Saga aus Anwendersicht"));
inhalt.push(h2("10.1  Wozu eine Saga gut ist"));
inhalt.push(abs("Der komplette Antragseingang — Vorgang erfassen, Bedarfsprüfung, Bescheid erteilen, Erstauszahlung anweisen — besteht aus vier voneinander unabhängigen Schritten. Kein Datenbanksystem kann vier getrennte Fachentscheidungen in einem einzigen unteilbaren Schritt abwickeln, wenn zwischen ihnen gewartet oder gerechnet wird. Genau dafür ist die Saga da: Jeder Schritt ist eine eigene kurze Transaktion und wird im Saga-Journal festgehalten; scheitert ein Schritt, laufen die bereits abgeschlossenen Schritte rückwärts zurück (Kompensation). Es bleibt nie ein halbfertiger Vorgang liegen."));
inhalt.push(...abbild("04_antrags_saga_maske.png", "Maske „End-to-End-Antrag als Saga ausführen“ mit den vier Transaktionsschritten"));
inhalt.push(h2("10.2  Ablauf und Ergebnis"));
inhalt.push(abs("Die Maske „⚡ Antrags-Saga (E2E)“ in der Fallübersicht führt den vierstufigen Ablauf mit einen Klick aus: Vorgang erfassen (Bedarfsgemeinschaft plus erster SozialFall), Bedarfsprüfung (bricht bei fehlendem Anspruch ab), Bescheid erteilen und Erstauszahlung anweisen. Der Erfolgsdialog zeigt die Saga-ID; der neue Vorgang erscheint anschließend in der Fallübersicht."));
inhalt.push(...abbild("d03_saga.png", "Ablauf der Antrags-Saga mit den vier Schritten und ihren Kompensationen"));
inhalt.push(abs([{ t: "Bei fehlendem Anspruch (Zahlbetrag ≤ 0) bricht Schritt 2 ab: ", fett: true }, { t: "die Saga kompensiert automatisch rückwärts — ein angelegter Bescheid wird aufgehoben, ein angelegter Vorgang gelöscht, die Erstauszahlung storniert. Die Fehlermeldung weist auf die erfolgte Rückwärtskompensation hin; im Journal ist die ganze Geschichte nachlesbar. Aus Sicht der Sachbearbeitung entsteht kein Müll-Datensatz." }]));
inhalt.push(hinweis("Halbfertige Sagas nach einem Absturz", "Stürzt der Server mitten in einer Saga ab, bleibt das Journal unverändert bestehen. Beim nächsten Start führt das System automatisch einen Wiederanlauf durch; zusätzlich steht im Adminbereich die Schaltfläche „Wiederanlauf ausführen“ bereit. Mehrfaches Ausführen ist unschädlich — Kompensationen sind idempotent gebaut (Kapitel 15)."));

// ================================================================ 11 Adminbereich
inhalt.push(h1("11  Adminbereich und Betriebsfunktionen"));
inhalt.push(h2("11.1  Systemstatus"));
inhalt.push(abs("Der Adminbereich (nur Rolle admin) prüft mit einem Klick auf „Status prüfen“ die fünf Säulen des Betriebs: Datenbank (Erreichbarkeit, Produktkennung, Antwortzeit, Datenbank-Zeit), Fachschema (Bestandszahlen der Vorgänge, SozialFälle und Zahlungen), Transaktionssteuerung (TxManager aktiv, Retry-Verhalten), Task-Steuerung (Parallelität, Statistik inkl. mittlerer Dauer) und Wiederanlauf-Lage (offene versus abgebrochene Sagas). Grün in allen Zeilen bedeutet: „Datenbank ok und alles gestartet“."));
inhalt.push(...abbild("14_adminbereich.png", "Adminbereich mit Systemstatus, Laufzeitumgebung und Betriebsaktionen"));
inhalt.push(h2("11.2  Kassenlauf"));
inhalt.push(abs("Der Kassenlauf ist die fachliche Übergabe an die Kasse beziehungsweise den Zahlungsdienst: Er setzt alle Zahlungen im Status BEREIT in einer einzigen Transaktion auf ÜBERGEBEN, sperrt sie dabei gegen zwischenzeitliche Änderung und schreibt einen Sammel-Journaleintrag mit Anzahl und Summe. Übergebene Zahlungen sind endgültig und erscheinen danach im Widget „Zahlungen bereit (30 Tage)“ nicht mehr."));
inhalt.push(abs("Üblicher Rhythmus: Freigaben durch die zweite Person im Tagesverlauf, Kassenlauf zum Tagesabschluss durch die Administration. Ein leerer Kassenlauf ist kein Fehler — das System meldet dann 0 Zahlungen über 0,00 EUR."));
inhalt.push(h2("11.3  Wiederanlauf"));
inhalt.push(abs("Die Schaltfläche „Wiederanlauf ausführen“ beendet halbfertige Sagas nach einem Absturz: laufende Vorgänge werden sauber kompensiert, abgeschlossene bleiben unberührt. Der Adminbereich trennt bewusst „offen“ und „abgebrochen“: Eine abgebrochene Saga ohne committete Schritte hat nichts zu kompensieren und braucht den Wiederanlauf nicht."));
inhalt.push(h2("11.4  Funktionsübersicht"));
inhalt.push(abs("Die Ansicht „Funktionsübersicht“ dokumentiert alle Fach- und Kernfunktionen mit ihren technischen Bausteinen und Schnittstellen-Endpunkten — als eingebaute Kurzreferenz der Anwendung."));
inhalt.push(...abbild("13_funktionsuebersicht.png", "Eingebaute Funktionsübersicht mit Fachfunktionen und Transaktionssteuerung"));

// ================================================================ 12 Hinweise
inhalt.push(h1("12  Hinweise für den sicheren Betrieb der Sacharbeit"));
inhalt.push(h2("12.1  Arbeitsorganisation mit vier Augen"));
inhalt.push(punkt("Ordne Auszahlungen grundsätzlich mit aktiver Vier-Augen-Checkbox an; den Direktweg ohne Prüfung nur in begründeten Ausnahmen."));
inhalt.push(punkt("Nutze zwei unterscheidbare Kennungen (z. B. sb-01 und sb-02), damit Erfassung und Freigabe nachvollziehbar verschiedenen Personen zugeordnet sind."));
inhalt.push(punkt("Führe den Kassenlauf erst nach Abschluss aller Freigaben des Tages durch; danach sind übergebene Zahlungen endgültig."));
inhalt.push(punkt("Pflege die Standard-Bankverbindung des Vorgangs sofort bei der Neuanlage — der Bescheid und die Standard-Auszahlung greifen darauf zurück."));
inhalt.push(h2("12.2  Gleichzeitige Bearbeitung"));
inhalt.push(abs("Arbeiten mehrere Personen am selben Vorgang, ist die Meldung „Änderung durch anderen Bearbeiter“ normal und kein Fehler: Der Speichern-Schutz verhindert verlorengegangene Änderungen. Vorgehen: Ansicht neu laden (geschieht automatisch), die eigenen Anpassungen erneut eintragen, speichern. Nachträglich angeordnete Auszahlungen anderer Bearbeiter sieht man anhand der Zahlungshistorie und des Journals."));
inhalt.push(h2("12.3  Fehlercodes und ihre Bedeutung"));
inhalt.push(abs("Alle fachlichen Meldungen der Oberfläche tragen einen stabilen Code. Die Tabelle ordnet ihn Bedeutung und üblicher Lösung zu:"));
inhalt.push(tabellenTitel("Fachliche Fehlercodes der Anwendung"));
inhalt.push(tabelle(
  ["Code", "Bedeutung", "Übliche Lösung"],
  [
    ["VALIDATION", "Eingabefehler: Pflichtfeld leer, Format verletzt (Zahlungsmonat, IBAN/BIC), doppeltes Aktenzeichen, historisierter Vorgang", "Eingabe prüfen und korrigieren; Aktenzeichen ändern; historisierte Vorgänge nicht mehr buchen"],
    ["KEIN_ANSPRUCH", "Zahlbetrag ≤ 0: anrechenbares Einkommen übersteigt den Gesamtbedarf", "Einkommens- und Bedarfsangaben prüfen; bei rechtmäßigem Ergebnis keine Leistung bewilligen"],
    ["BUDGET_ERSCHOEPFT", "Zahlbetrag übersteigt das Restbudget des Vorgangs", "Restbudget prüfen, ggf. Startbudget erhöhen oder ältere Zahlung stornieren"],
    ["KONKURRENZ", "Ein anderer Bearbeiter hat den Vorgang/SozialFall zwischenzeitlich geändert", "Neustand automatisch geladen — Änderung erneut eintragen und speichern"],
    ["BERECHTIGUNG", "Funktion erfordert die Admin-Rolle oder verletzt das Vier-Augen-Prinzip", "Admin-Funktion durch Administrator ausführen lassen; Freigabe durch zweite Person vornehmen"],
    ["RBS_NICHT_KONFIGURIERT", "Eine Regelbedarfsstufe außerhalb 1–6 ist gesetzt", "Regelbedarfsstufe des SozialFalls korrigieren"],
    ["ANMELDUNG", "Nicht angemeldet oder Sitzung abgelaufen (Server-Neustart)", "Erneut anmelden"],
    ["NICHT_GEFUNDEN", "Vorgang/Zahlung existiert nicht (mehr)", "Übersicht aktualisieren; Verweis prüfen"],
  ],
  [33, 36, 31],
));
inhalt.push(h2("12.4  Was die Anwendung bewusst nicht tut"));
inhalt.push(abs("Die Abgrenzung gehört zu einem ehrlichen Handbuch: Die Anwendung kennt keine ortsspezifische Mietobergrenze (nur eine konfigurierbare Gesamtgrenze) und berücksichtigt Vermögen nur pauschal als Summe je Bedarfsgemeinschaft nach Abzug des Schonvermögens (§ 90 Abs. 2 Nr. 9 SGB XII); sie archiviert keine Belege und versendet keinen echten SEPA-Zahlungsstrom — der Kassenlauf ist die fachliche Übergabe an das nachgelagerte Zahlungssystem. Merkzeichen-G-Einträge und Warmwasser-Mehrbedarf werden nicht gegen Nachweise validiert. Diese Prüfungen bleiben der Fachkraft; die Anwendung stellt sicher, dass alles Erfaßte rechnerisch korrekt, revisionssicher und im Vier-Augen-Wege zur Auszahlung gelangt."));

module.exports = inhalt;
