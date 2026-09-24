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

    /** Vorgangsmaske: Kopfdaten, SozialFälle, Berechnung, Zahlungen, Journal. */
    Vorgangsdetail ladeDetail(long bgId);

    /** Auszahlung anordnen (idempotent über die Vorgangsnummer). */
    Auszahlungsergebnis ordneAuszahlungAn(Auszahlungsauftrag auftrag);

    /** SozialFall speichern (optimistisches Locking über erwartete VERSION). */
    SozialFall aktualisiereSozialFall(SozialFall geaendert, long erwarteteVersion);

    /** Vorgangskopf speichern: Status und Wiedervorlage (optimistisches Locking). */
    Bedarfsgemeinschaft aktualisiereBedarfsgemeinschaft(
            long id, boolean statusAktiv, LocalDate wiedervorlageAm, long erwarteteVersion);

    /** Letzte Zahlungen über alle Vorgänge (Widget/Journalansicht). */
    List<Zahlung> letzteZahlungen(int limit);

    /** Letzte Journaleinträge über alle Vorgänge. */
    List<Journaleintrag> letztesJournal(int limit);

    // ----------------------------------------------------------------
    // Betriebsfunktionen: Neuanlage, Kassenlauf, Storno
    // ----------------------------------------------------------------

    /** Legt einen Vorgang an; das Aktenzeichen muss eindeutig sein. */
    Bedarfsgemeinschaft legeBedarfsgemeinschaftAn(String aktenzeichen, String hauptpersonName,
            BigDecimal startBudget, LocalDate wiedervorlageAm, String sachbearbeiterId);

    /** Legt einen weiteren SozialFall zu einem aktiven Vorgang an. */
    SozialFall legeSozialFallAn(long bgId, SozialFall entwurf, String sachbearbeiterId);

    /** Kassenlauf: alle BEREIT-Zahlungen auf UEBERGEBEN setzen (eine Transaktion). */
    Kassenlaeufergebnis uebergebeBereiteZahlungen(String sachbearbeiterId);

    /** Storniert eine BEREIT-Zahlung und bucht das Restbudget zurück. */
    Zahlung storniereZahlung(long zahlungId, String sachbearbeiterId);
}
