package de.allgemein.lmg.service;

import de.allgemein.lmg.model.Bedarfsgemeinschaft;
import de.allgemein.lmg.model.Journaleintrag;
import de.allgemein.lmg.model.SozialFall;
import de.allgemein.lmg.model.VorgangslisteEintrag;
import de.allgemein.lmg.model.Zahlung;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Fachdienste der Leistungsverarbeitung — die Schnittstelle zwischen
 * Webschicht und Transaktionssteuerung. Jede Methode ist eine abgeschlossene
 * Arbeitseinheit (kurze Oracle-Transaktion, siehe {@link SozialLeistungsService}).
 */
public interface LeistungsService {

    /** Fallübersicht: alle Vorgänge mit Zahlbetrag-Vorschau. */
    List<VorgangslisteEintrag> listeBedarfsgemeinschaften();

    /** Fallübersicht mit Vorschau für den ausdrücklich gewählten Bezugsmonat. */
    default List<VorgangslisteEintrag> listeBedarfsgemeinschaften(String bezugsmonat) {
        return listeBedarfsgemeinschaften();
    }

    /** Vorgangsmaske: Kopfdaten, SozialFälle, Berechnung, Zahlungen, Journal. */
    Vorgangsdetail ladeDetail(long bgId);

    /**
     * Vorgangsmaske mit Zugriffsprotokoll (CR-035/Option D): Der Webserver ruft
     * diese Variante mit der Kennung der Sitzung — jede Einsicht in die
     * vollständigen Falldaten wird journalisiert (60-s-Zusammenfassung).
     */
    Vorgangsdetail ladeDetail(long bgId, String sachbearbeiterId);

    /** Vorschau für den ausdrücklich gewählten Bezugsmonat; verändert keinen Bestand. */
    default Vorgangsdetail ladeDetail(long bgId, String sachbearbeiterId, String bezugsmonat) {
        return ladeDetail(bgId, sachbearbeiterId);
    }

    /** Findet einen Vorgang über sein eindeutiges Aktenzeichen (lesend). */
    java.util.Optional<de.allgemein.lmg.model.Bedarfsgemeinschaft>
            findeBedarfsgemeinschaftNachAktenzeichen(String aktenzeichen);

    /** Auszahlung anordnen (idempotent über die Vorgangsnummer). */
    Auszahlungsergebnis ordneAuszahlungAn(Auszahlungsauftrag auftrag);

    /** SozialFall speichern (optimistisches Locking über erwartete VERSION). */
    SozialFall aktualisiereSozialFall(SozialFall geaendert, long erwarteteVersion);

    /** SozialFall speichern mit Sachbearbeiter-Kennung für das Journal (revisionssicher). */
    SozialFall aktualisiereSozialFall(SozialFall geaendert, long erwarteteVersion, String sachbearbeiterId);

    /** Vorgangskopf speichern: Status und Wiedervorlage (optimistisches Locking). */
    Bedarfsgemeinschaft aktualisiereBedarfsgemeinschaft(
            long id, boolean statusAktiv, LocalDate wiedervorlageAm, long erwarteteVersion);

    /** Vorgangskopf inklusive Bankverbindung speichern (optimistisches Locking). */
    Bedarfsgemeinschaft aktualisiereBedarfsgemeinschaft(
            long id, boolean statusAktiv, LocalDate wiedervorlageAm,
            String iban, String bic, String kontoinhaber, long erwarteteVersion);

    /** Vorgangskopf inklusive Bankverbindung und Vermögen (§ 90 SGB XII) speichern. */
    Bedarfsgemeinschaft aktualisiereBedarfsgemeinschaft(
            long id, boolean statusAktiv, LocalDate wiedervorlageAm,
            String iban, String bic, String kontoinhaber, java.math.BigDecimal vermoegen,
            long erwarteteVersion, String sachbearbeiterId);

    /** Letzte Zahlungen über alle Vorgänge (Widget/Journalansicht). */
    List<Zahlung> letzteZahlungen(int limit);

    /** Letzte Journaleinträge über alle Vorgänge. */
    List<Journaleintrag> letztesJournal(int limit);

    // ----------------------------------------------------------------
    // Betriebsfunktionen: Neuanlage, Kassenlauf, Storno, Freigabe
    // ----------------------------------------------------------------

    /** Legt einen Vorgang an; das Aktenzeichen muss eindeutig sein. */
    Bedarfsgemeinschaft legeBedarfsgemeinschaftAn(String aktenzeichen, String hauptpersonName,
            BigDecimal startBudget, LocalDate wiedervorlageAm, String sachbearbeiterId);

    /** Legt einen Vorgang inklusive Bankverbindung an. */
    Bedarfsgemeinschaft legeBedarfsgemeinschaftAn(String aktenzeichen, String hauptpersonName,
            BigDecimal startBudget, LocalDate wiedervorlageAm,
            String iban, String bic, String kontoinhaber, String sachbearbeiterId);

    /** Legt einen Vorgang inklusive Bankverbindung und Vermögen (§ 90 SGB XII) an. */
    Bedarfsgemeinschaft legeBedarfsgemeinschaftAn(String aktenzeichen, String hauptpersonName,
            BigDecimal startBudget, LocalDate wiedervorlageAm,
            String iban, String bic, String kontoinhaber, BigDecimal vermoegen, String sachbearbeiterId);

    /** Legt einen weiteren SozialFall zu einem aktiven Vorgang an. */
    SozialFall legeSozialFallAn(long bgId, SozialFall entwurf, String sachbearbeiterId);

    /** Rechnerische Feststellung / Vier-Augen-Freigabe einer Zahlung (Status ANGELEGT -> BEREIT). */
    Zahlung freigebenZahlung(long zahlungId, String freigeberId);

    /**
     * Vier-Augen-Freigabe mit Rolleninformation: ein Administrator (Rolle admin)
     * darf ausnahmsweise die eigene Anordnung rechnerisch feststellen.
     */
    Zahlung freigebenZahlung(long zahlungId, String freigeberId, boolean freigeberIstAdmin);

    /** Kassenlauf: alle BEREIT-Zahlungen auf UEBERGEBEN setzen (eine Transaktion). */
    Kassenlaeufergebnis uebergebeBereiteZahlungen(String sachbearbeiterId);

    /** Storniert eine BEREIT- oder ANGELEGT-Zahlung und bucht das Restbudget zurück. */
    Zahlung storniereZahlung(long zahlungId, String sachbearbeiterId);

    /** Erteilt einen Bewilligungsbescheid (§ 44 SGB XII); das Bescheiddatum ist der Ausstellungstag. */
    de.allgemein.lmg.model.Bescheid erteileBescheid(long bgId, String bewilligungVon, String bewilligungBis, String sachbearbeiterId);

    /** Ändert die bestehende Bewilligung ab dem angegebenen Monat atomar. */
    default de.allgemein.lmg.model.Bescheid aendereBescheid(long bgId, String bewilligungVon,
            String bewilligungBis, String sachbearbeiterId) {
        throw new UnsupportedOperationException("Änderungsbescheide werden nicht unterstützt");
    }

    /** Liefert den aktuellen/letzten Bescheid der Bedarfsgemeinschaft, falls vorhanden. */
    java.util.Optional<de.allgemein.lmg.model.Bescheid> letzterBescheid(long bgId);

    /** Bescheid-Abruf mit Zugriffsprotokoll (CR-035/Option D). */
    java.util.Optional<de.allgemein.lmg.model.Bescheid> letzterBescheid(long bgId, String sachbearbeiterId);

    /** Generiert das amtliche, druckfertige Bescheid-Dokument (HTML, Bewilligungsbescheid nach § 44 SGB XII). */
    String generiereBescheidHtml(long bgId);

    /** Bescheid-Druck mit Zugriffsprotokoll (CR-035/Option D). */
    String generiereBescheidHtml(long bgId, String sachbearbeiterId);
}
