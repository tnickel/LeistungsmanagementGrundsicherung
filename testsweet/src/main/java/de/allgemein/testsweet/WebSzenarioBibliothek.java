package de.allgemein.testsweet;

import de.allgemein.lmg.model.SozialFall;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Auftragstopf für den Weboberflächen-Modus (Modus 2): Jeder Auftrag wird von
 * einem simulierten Sachbearbeiter in einem sichtbaren Chrome-Fenster über die
 * echte SDN.Leistung-Oberfläche durchgespielt (Anmeldung, Neuanlage, Fall,
 * Auszahlung anordnen, Vier-Augen-Freigabe, Storno). Die Erwartungshaltung
 * wird — wie in Modus 1 — nach Laufende per SQL geprüft; da die Weboberfläche
 * die Vorgangs-ID nicht sichtbar macht, greifen die Prüf-SQLs über das
 * eindeutige Aktenzeichen.
 */
public final class WebSzenarioBibliothek {

    private final Random random;

    public WebSzenarioBibliothek(long seed) {
        this.random = new Random(seed);
    }

    public List<Auftrag> erzeuge(int anzahl, String praefix) {
        List<Auftrag> auftraege = new ArrayList<>(anzahl);
        for (int i = 1; i <= anzahl; i++) {
            int wahl = random.nextInt(10);
            if (wahl < 4) {
                auftraege.add(standardrente(i, praefix));
            } else if (wahl < 7) {
                auftraege.add(mehrbedarf(i, praefix));
            } else {
                auftraege.add(storno(i, praefix));
            }
        }
        return auftraege;
    }

    // ------------------------------------------------------------------

    private Auftrag standardrente(int nr, String praefix) {
        return basis(nr, praefix, "WEB-STANDARDRENTE", false, false, "mit Vier-Augen-Freigabe",
                "WEB-STANDARDRENTE", true);
    }

    private Auftrag mehrbedarf(int nr, String praefix) {
        return basis(nr, praefix, "WEB-MEHRBEDARF", random.nextBoolean(), random.nextBoolean(),
                "mit Merkzeichen G und/oder Warmwasser-Zuschlag", "WEB-MEHRBEDARF", true);
    }

    private Auftrag storno(int nr, String praefix) {
        return basis(nr, praefix, "WEB-STORNO", false, false,
                "Anordnung, Freigabe und anschließender Storno", "WEB-STORNO", false);
    }

    private Auftrag basis(int nr, String praefix, String typ, boolean merkzeichenG,
            boolean warmwasser, String variante, String einfachTyp, boolean mitFreigabeEnde) {
        int rbs = 1 + random.nextInt(3);
        BigDecimal miete = BigDecimal.valueOf(250 + 10 * random.nextInt(31)).setScale(2);
        BigDecimal betrieb = BigDecimal.valueOf(30 + 5 * random.nextInt(19)).setScale(2);
        BigDecimal heizung = BigDecimal.valueOf(50 + 5 * random.nextInt(19)).setScale(2);
        List<SozialFall> faelle = List.of(WebHelfer.fallEntwurf(rbs, merkzeichenG, warmwasser,
                miete, betrieb, heizung));
        List<Rechenblock> rechnungen = new ArrayList<>();
        BigDecimal zahlbetrag = WebHelfer.berechne(rechnungen, "Anordnung über die Weboberfläche",
                faelle, BigDecimal.ZERO);
        BigDecimal budget = zahlbetrag.add(BigDecimal.valueOf(100 + 10 * random.nextInt(21)));
        rechnungen.replaceAll(b -> b.mitBudget(budget));
        String aktenzeichen = String.format("%s%04d", praefix, nr);
        String monat = "2026-10";

        List<WebAktion> schritte = new ArrayList<>(List.of(
                WebAktion.schritt("Anmeldung an der Weboberfläche", WebHelfer::anmeldung),
                WebAktion.schritt("Neuen Vorgang anlegen (Aktenzeichen " + aktenzeichen + ")",
                        (d, ctx) -> WebHelfer.vorgangAnlegen(d, ctx, aktenzeichen,
                                "Web, Willi " + nr, budget)),
                WebAktion.schritt("SozialFall erfassen (RBS " + rbs + ", KdU "
                        + miete.add(betrieb).add(heizung).toPlainString() + " EUR)",
                        (d, ctx) -> WebHelfer.fallAnlegen(d, ctx, rbs, merkzeichenG, warmwasser,
                                miete, betrieb, heizung)),
                WebAktion.schritt("Auszahlung für " + monat + " anordnen",
                        (d, ctx) -> WebHelfer.auszahlungAnordnen(d, ctx, monat)),
                WebAktion.schritt("Abmelden und als Kollegin/Kollege anmelden (Vier-Augen)",
                        WebHelfer::kollegenLogin),
                WebAktion.schritt("Vorgang über die Suche finden und öffnen",
                        (d, ctx) -> WebHelfer.vorgangSuchen(d, ctx, aktenzeichen)),
                WebAktion.schritt("Zahlung freigeben (rechnerisch feststellen)",
                        WebHelfer::freigeben)));
        if (!mitFreigabeEnde) {
            schritte.add(WebAktion.schritt("Erneut wechseln und Storno buchen",
                    WebHelfer::stornoWechsel));
        }

        List<Erwartung> erwartungen = new ArrayList<>();
        erwartungen.add(Erwartung.text(mitFreigabeEnde
                        ? "Zahlung über die Oberfläche freigegeben (BEREIT)"
                        : "Zahlung nach Storno über die Oberfläche (STORNIERT)",
                "SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ZAHLUNGSMONAT = ? AND BG_ID = "
                        + "(SELECT ID FROM BEDARFSGEMEINSCHAFT WHERE AKTENZEICHEN = ?)",
                ctx -> List.of(monat, aktenzeichen),
                mitFreigabeEnde ? "BEREIT" : "STORNIERT"));
        erwartungen.add(Erwartung.betrag("Auszahlungsbetrag entspricht der Berechnung",
                "SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ZAHLUNGSMONAT = ? AND BG_ID = "
                        + "(SELECT ID FROM BEDARFSGEMEINSCHAFT WHERE AKTENZEICHEN = ?)",
                ctx -> List.of(monat, aktenzeichen), zahlbetrag.toPlainString()));
        erwartungen.add(Erwartung.betrag(mitFreigabeEnde
                        ? "Restbudget korrekt gemindert"
                        : "Restbudget nach Storno vollständig zurückgebucht",
                "SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE AKTENZEICHEN = ?",
                ctx -> List.of(aktenzeichen),
                (mitFreigabeEnde ? budget.subtract(zahlbetrag) : budget).toPlainString()));
        erwartungen.add(Erwartung.ganzeZahl("SozialFall mit Stufe persistiert",
                "SELECT COUNT(*) FROM SOZIAL_FALL WHERE REGELBEDARFSSTUFE = ? AND BG_ID = "
                        + "(SELECT ID FROM BEDARFSGEMEINSCHAFT WHERE AKTENZEICHEN = ?)",
                ctx -> List.of(rbs, aktenzeichen), 1));

        return new Auftrag(nr, typ, aktenzeichen, "Web, Willi " + nr,
                "Weboberflächen-Durchspiel " + variante, List.of(), erwartungen)
                .mitWebAktionen(schritte)
                .dokumentiere("Der simulierte Sachbearbeiter bedient die echte SDN.Leistung-"
                        + "Weboberfläche im eigenen sichtbaren Browserfenster: Er meldet sich an, "
                        + "legt den Vorgang und den SozialFall an, ordnet die monatliche Auszahlung "
                        + "an und übergibt per Ab-/Anmeldung an eine zweite Kraft, die die Zahlung "
                        + "freigibt (Vier-Augen-Prinzip). "
                        + (mitFreigabeEnde
                        ? "Geprüft wird, ob Status, Betrag und Budget in der Datenbank stimmen."
                        : "Anschließend wird die Zahlung wieder storniert — geprüft wird Status "
                        + "STORNIERT inklusive vollständiger Budget-Rückbuchung.")
                        + " Jeder Schritt wird als Screenshot im Laufprotokoll festgehalten.",
                        rechnungen);
    }
}
