package de.allgemein.lmg.service;

import de.allgemein.lmg.calc.BerechnungsSnapshot;
import de.allgemein.lmg.calc.Bewilligungsplan;
import de.allgemein.lmg.model.Bedarfsgemeinschaft;
import de.allgemein.lmg.model.Bescheid;
import java.time.Instant;
import java.time.LocalDate;

/** Erteilungsdokument und Berechnung werden gemeinsam in derselben Fachtx gespeichert. */
public final class BescheidDokument {
    private BescheidDokument() { }

    public static String snapshot(Bewilligungsplan.Plan plan, Bedarfsgemeinschaft bg,
                                  long id, String nummer, LocalDate datum, String von, String bis,
                                  String sachbearbeiter, BescheidGenerator.Rechtsbehelf rechtsbehelf) {
        // ERSTELLT_AM wird nicht gedruckt; alle gedruckten Bescheiddaten stehen vor dem INSERT fest.
        var entwurf = new Bescheid(id, bg.id(), nummer, datum, von, bis,
                plan.ersterMonat().ergebnis().zahlbetrag(), "ERTEILT", sachbearbeiter,
                Instant.EPOCH, BerechnungsSnapshot.schreibe(plan));
        String html = BescheidGenerator.generiereHtml(bg, plan.ersterMonat().faelle(),
                plan.ersterMonat().ergebnis(), entwurf, rechtsbehelf);
        return BerechnungsSnapshot.schreibe(plan, html);
    }

    /** Alte Snapshots enthalten keine ursprünglichen Bank-/Behörden-/Belehrungstexte. */
    public static String kennzeichneAltstand(String html) {
        return html.replace("<body>", "<body><div class='vorbehalt'>Hinweis zum Altbescheid: "
                + "Die Berechnung stammt aus dem Erteilungszeitpunkt. Ein vollständiger historischer "
                + "Dokumentstand wurde damals nicht gespeichert; Bankdaten, Behördenangaben und "
                + "Rechtsbehelfsbelehrung werden aus dem heutigen Datenstand ergänzt.</div>");
    }
}
