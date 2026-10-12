package de.allgemein.lmg.service;

import de.allgemein.lmg.calc.Leistungsberechnung;
import de.allgemein.lmg.calc.BerechnungsSnapshot;
import de.allgemein.lmg.calc.Bewilligungsplan;
import de.allgemein.lmg.model.Bedarfsgemeinschaft;
import de.allgemein.lmg.model.Bescheid;
import de.allgemein.lmg.model.SozialFall;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Erzeugung von Bewilligungsbescheiden (§ 44 SGB XII) für das Vierte Kapitel des
 * SGB XII (Grundsicherung im Alter und bei Erwerbsminderung). Enthält Verfügungssatz,
 * fallbezogene Bedarfsaufschlüsselung, Zahlungsverbindung, Begründung mit
 * Hauptgründen (§ 35 SGB X) und vollständige Rechtsbehelfsbelehrung (§ 36 SGB X).
 * BESCHEID_DATUM ist das Datum des Bescheids; die Bekanntgabe (§ 37 SGB X) ist ein eigener,
 * hier nicht erfasster Zeitpunkt (bei Postversand: vierter Tag nach Aufgabe zur Post).
 */
public final class BescheidGenerator {

    private static final DateTimeFormatter DATUM_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMAN);

    private BescheidGenerator() {
    }

    /**
     * Konfiguration der Rechtsbehelfsbelehrung (§ 36 SGB X): Je nach Träger und
     * Landesrecht ist vor der Klage zum Sozialgericht ein Widerspruchsverfahren
     * durchzuführen (§ 78 SGG) oder direkte Klage geboten (§ 87 Abs. 1 SGG).
     *
     * @param modus               MODUS_WIDERSPRUCH oder MODUS_KLAGE
     * @param widerspruchsbehoerde vollständiger Name und Sitz der Widerspruchsbehörde
     *                             (leer = die Behörde, die den Bescheid erlassen hat)
     * @param sozialgericht       Name und Sitz des zuständigen Sozialgerichts
     * @param ort                 Ort der Behörde für die Unterschriftszeile (optional)
     * @param anschrift           Anschrift der erlassenden Behörde für Briefkopf und Belehrung (optional)
     */
    public record Rechtsbehelf(String modus, String widerspruchsbehoerde, String sozialgericht,
                               String ort, String anschrift) {

        public static final String MODUS_WIDERSPRUCH = "WIDERSPRUCH";
        public static final String MODUS_KLAGE = "KLAGE";

        public Rechtsbehelf(String modus, String widerspruchsbehoerde, String sozialgericht) {
            this(modus, widerspruchsbehoerde, sozialgericht, null, null);
        }

        public static Rechtsbehelf standard() {
            return new Rechtsbehelf(MODUS_WIDERSPRUCH, null, null);
        }

        public Rechtsbehelf {
            if (modus == null || modus.isBlank()) {
                modus = MODUS_WIDERSPRUCH;
            }
        }
    }

    public static String generiereHtml(Bedarfsgemeinschaft bg, List<SozialFall> faelle,
                                       Leistungsberechnung.Berechnungsergebnis berechnung,
                                       Bescheid bescheid) {
        return generiereHtml(bg, faelle, berechnung, bescheid, null);
    }

    public static String generiereHtml(Bedarfsgemeinschaft bg, List<SozialFall> faelle,
                                       Leistungsberechnung.Berechnungsergebnis berechnung,
                                       Bescheid bescheid, Rechtsbehelf rechtsbehelf) {
        return generiereHtml(bg, faelle, berechnung, bescheid, rechtsbehelf, false);
    }

    /**
     * @param neuberechnetOhneSnapshot true, wenn kein gespeicherter Berechnungs-Snapshot
     *        vorliegt und die Aufstellung mit dem aktuellen Datenstand neu erstellt wurde
     *        (ältere Bescheide) — das Dokument erhält dann einen sichtbaren Hinweiskasten.
     */
    public static String generiereHtml(Bedarfsgemeinschaft bg, List<SozialFall> faelle,
                                       Leistungsberechnung.Berechnungsergebnis berechnung,
                                       Bescheid bescheid, Rechtsbehelf rechtsbehelf,
                                       boolean neuberechnetOhneSnapshot) {
        List<Bewilligungsplan.Monat> monate = List.of();
        if (!neuberechnetOhneSnapshot && bescheid.berechnung() != null && !bescheid.berechnung().isBlank()) {
            monate = BerechnungsSnapshot.lese(bescheid.berechnung()).monate();
        }
        Rechtsbehelf belehrung = rechtsbehelf == null ? Rechtsbehelf.standard() : rechtsbehelf;
        String snapshotWarnung = neuberechnetOhneSnapshot
                ? "<div class='vorbehalt'>Hinweis: Für diesen Bescheid ist kein Berechnungs-Snapshot aus "
                        + "dem Erteilungszeitpunkt gespeichert (früherer Datenstand). Die Berechnungsaufstellung "
                        + "wurde mit dem aktuellen Datenstand neu erstellt; verbindlich bleibt der im "
                        + "Verfügungssatz bewilligte Betrag aus dem Erteilungszeitpunkt.</div>"
                : "";
        String datumText = bescheid.bescheidDatum().format(DATUM_FORMAT);
        String zahlbetragText = formatBetrag(bescheid.zahlbetrag());
        String ibanText = bg.iban() != null ? SepaValidator.formatiereIban(bg.iban()) : "nicht hinterlegt";
        String bicText = bg.bic() != null ? bg.bic() : "–";
        String inhaberText = bg.kontoinhaber() != null && !bg.kontoinhaber().isBlank()
                ? bg.kontoinhaber() : bg.hauptpersonName();
        boolean kduVorbehaltIrgendeinMonat = berechnung.faelle().stream()
                        .anyMatch(Leistungsberechnung.FallBerechnung::kduUnangemessen)
                || monate.stream().anyMatch(m -> m.ergebnis().faelle().stream()
                        .anyMatch(Leistungsberechnung.FallBerechnung::kduUnangemessen));
        String vorbehaltHinweis = kduVorbehaltIrgendeinMonat
                ? "<p class='vorbehalt'>* Die Aufwendungen für Unterkunft und Heizung übersteigen die als "
                        + "angemessen anerkannte Grenze (§ 42a i.V.m. § 35 Abs. 1 und 7 SGB XII). Der übersteigende "
                        + "Betrag wird bei der Berechnung nicht berücksichtigt; eine Absenkungsfrist nach § 35 Abs. 3 "
                        + "SGB XII wird nicht gewährt.</p>"
                : "";
        String vermoegenHinweis = berechnung.verwertbaresVermoegen().signum() > 0
                ? "<li>Vermögen (§ 43 Abs. 1 SGB XII): " + formatBetrag(bg.vermoegen()) + " € abzüglich Schonvermögen von "
                        + formatBetrag(Leistungsberechnung.schonvermoegen(faelle))
                        + " € (§ 90 Abs. 2 Nr. 9 SGB XII i.V.m. § 1 der Verordnung zu § 90 Abs. 2 Nr. 9 SGB XII) ergibt verwertbares "
                        + "Vermögen von " + formatBetrag(berechnung.verwertbaresVermoegen())
                        + " €; es wird auf den Bedarf angerechnet.</li>"
                : "";

        String begruendungPunkte = begruendungPunkte(faelle, berechnung, vermoegenHinweis);
        String verfuegungsBetrag = "in Höhe von monatlich <strong>" + zahlbetragText + " EUR</strong>";
        String berechnungsTabellen = berechnungsTabelle(faelle, berechnung, bescheid.zahlbetrag());
        if (!monate.isEmpty()) {
            StringBuilder betraege = new StringBuilder("in den folgenden verbindlichen Monatsbeträgen:<table>"
                    + "<thead><tr><th>Monat</th><th class='num'>Zahlbetrag</th></tr></thead><tbody>");
            StringBuilder tabellen = new StringBuilder();
            StringBuilder gruende = new StringBuilder();
            for (Bewilligungsplan.Monat monat : monate) {
                betraege.append("<tr><td>").append(escapeHtml(monat.monat())).append("</td><td class='num'><strong>")
                        .append(formatBetrag(monat.ergebnis().zahlbetrag())).append(" EUR</strong></td></tr>");
                tabellen.append("<h3>Berechnung für ").append(escapeHtml(monat.monat())).append("</h3>")
                        .append(berechnungsTabelle(monat.faelle(), monat.ergebnis(), monat.ergebnis().zahlbetrag()));
                String verm = monat.ergebnis().verwertbaresVermoegen().signum() > 0
                        ? "<li>Vermögen vor Einsatz: " + formatBetrag(monat.vermoegen())
                        + " EUR; verwertbares Vermögen (§ 90 SGB XII): "
                        + formatBetrag(monat.ergebnis().verwertbaresVermoegen()) + " EUR.</li>" : "";
                gruende.append("<li><strong>").append(escapeHtml(monat.monat())).append("</strong><ul>")
                        .append(begruendungPunkte(monat.faelle(), monat.ergebnis(), verm)).append("</ul></li>");
            }
            verfuegungsBetrag = betraege.append("</tbody></table>").toString();
            berechnungsTabellen = tabellen.toString();
            begruendungPunkte = gruende.toString();
        }
        String belehrungText = belehrungText(belehrung);
        String anschriftZeile = belehrung.anschrift() == null || belehrung.anschrift().isBlank() ? ""
                : "<div class=\"untertitel\">" + escapeHtml(belehrung.anschrift()) + "</div>";
        String ortUndDatum = belehrung.ort() == null || belehrung.ort().isBlank()
                ? datumText : escapeHtml(belehrung.ort()) + ", den " + datumText;

        return """
<!DOCTYPE html>
<html lang="de">
<head>
  <meta charset="UTF-8">
  <title>Bewilligungsbescheid %s</title>
  <style>
    @page { size: A4; margin: 20mm; }
    body {
      font-family: 'Segoe UI', -apple-system, BlinkMacSystemFont, Roboto, sans-serif;
      font-size: 11pt; line-height: 1.5; color: #1e293b; background: #fff; margin: 0; padding: 20px;
    }
    .briefkopf { border-bottom: 2px solid #0284c7; padding-bottom: 12px; margin-bottom: 24px; }
    .behoerde { font-size: 14pt; font-weight: bold; color: #0369a1; }
    .untertitel { font-size: 9pt; color: #64748b; text-transform: uppercase; letter-spacing: 0.05em; }
    .meta-gitter { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin-bottom: 24px; font-size: 10pt; }
    .meta-box { background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 6px; padding: 12px; }
    .titel { font-size: 15pt; font-weight: bold; margin: 20px 0 12px; color: #0f172a; }
    .tenor { background: #f0fdf4; border-left: 4px solid #16a34a; padding: 12px 16px; margin: 16px 0; font-size: 11pt; }
    table { width: 100%%; border-collapse: collapse; margin: 18px 0; font-size: 10pt; }
    th, td { border: 1px solid #cbd5e1; padding: 8px 10px; text-align: left; }
    th { background: #f1f5f9; font-weight: 600; }
    td.num, th.num { text-align: right; }
    .summenzeile { background: #f8fafc; font-weight: bold; }
    .abschnitt { margin-top: 24px; }
    .abschnitt-titel { font-weight: bold; font-size: 11pt; border-bottom: 1px solid #e2e8f0; padding-bottom: 4px; margin-bottom: 8px; color: #334155; }
    .belehrung { background: #fefce8; border: 1px solid #fef08a; border-radius: 6px; padding: 12px; font-size: 9.5pt; color: #713f12; }
    .vorbehalt { background: #fff7ed; border: 1px solid #fdba74; border-radius: 6px; padding: 10px; font-size: 9.5pt; }
    .unterschrift { margin-top: 48px; display: flex; justify-content: flex-end; text-align: center; }
    .unterschrift-inner { border-top: 1px solid #334155; padding-top: 6px; min-width: 260px; font-size: 10pt; }
    .druck-button {
      margin-bottom: 20px; padding: 10px 18px; font-size: 12pt; background: #0284c7; color: #fff;
      border: 0; border-radius: 6px; cursor: pointer;
    }
    @media print {
      .druck-button { display: none; }
      body { padding: 0; }
      tr { break-inside: avoid; }
      h3 { break-after: avoid; }
    }
  </style>
</head>
<body>
  <button id="druck-button" class="druck-button" type="button">🖨️ Diesen Bescheid drucken (PDF)</button>
  <script src="/druck.js"></script>

  <div class="briefkopf">
    <div class="behoerde">Amt für Soziales und Wohnen — Leistungsmanagement</div>
    <div class="untertitel">SDN.Leistung · Grundsicherung im Alter und bei Erwerbsminderung (SGB XII)</div>
    %s
  </div>

  <div class="meta-gitter">
    <div class="meta-box">
      <strong>Empfänger:</strong><br>
      %s<br>
      Bedarfsgemeinschaft
    </div>
    <div class="meta-box">
      <strong>Aktenzeichen:</strong> %s<br>
      <strong>Bescheid-Nr.:</strong> %s<br>
      <strong>Datum des Bescheids:</strong> %s<br>
      <strong>Sachbearbeiter:</strong> %s
    </div>
  </div>

  <div class="titel">Bewilligungsbescheid über Leistungen der Grundsicherung</div>
  %s

  <div class="tenor">
    <strong>Verfügungssatz:</strong><br>
    Auf Ihren Antrag werden für die oben genannte Bedarfsgemeinschaft Leistungen der Grundsicherung im Alter
    und bei Erwerbsminderung nach dem Vierten Kapitel des Zwölften Buches Sozialgesetzbuch (SGB XII)
    für den Bewilligungszeitraum <strong>%s bis %s</strong> (§ 44 SGB XII) %s
    bewilligt. Die Leistung wird jeweils monatlich im Voraus gezahlt (§ 44 Abs. 4 SGB XII).
  </div>

  <div class="abschnitt">
    <div class="abschnitt-titel">1. Berechnung des monatlichen Leistungsanspruchs</div>
    %s
    %s
  </div>

  <div class="abschnitt">
    <div class="abschnitt-titel">2. Zahlungsanweisung</div>
    <p>Die bewilligten Leistungen werden jeweils monatlich im Voraus auf die hinterlegte Bankverbindung angewiesen:</p>
    <ul>
      <li><strong>Kontoinhaber:</strong> %s</li>
      <li><strong>IBAN:</strong> %s</li>
      <li><strong>BIC:</strong> %s</li>
    </ul>
  </div>

  <div class="abschnitt">
    <div class="abschnitt-titel">3. Begründung (§ 35 SGB X)</div>
    <p>Die Voraussetzungen für die Gewährung von Leistungen der Grundsicherung im Alter und bei
    Erwerbsminderung (§ 41 SGB XII) liegen vor. Die wesentlichen Berechnungsgründe:</p>
    <ul>%s</ul>
  </div>

  <div class="abschnitt">
    <div class="abschnitt-titel">4. Rechtsbehelfsbelehrung (§ 36 SGB X)</div>
    <div class="belehrung">%s</div>
  </div>

  <div class="unterschrift">
    <div class="unterschrift-inner">
      %s<br>
      <strong>Amt für Soziales und Wohnen</strong><br>
      i. A. %s
    </div>
  </div>
</body>
</html>
""".formatted(
                // <title> enthält die Bescheidnummer samt Aktenzeichen (Nutzereingabe)
                // — auch hier escapen, sonst bricht "</title><script>…" aus dem Element aus.
                escapeHtml(bescheid.bescheidNr()),
                anschriftZeile,
                escapeHtml(bg.hauptpersonName()),
                escapeHtml(bg.aktenzeichen()),
                escapeHtml(bescheid.bescheidNr()),
                datumText,
                escapeHtml(bescheid.erstelltVon()),
                snapshotWarnung,
                escapeHtml(bescheid.bewilligungVon()),
                escapeHtml(bescheid.bewilligungBis()),
                verfuegungsBetrag,
                berechnungsTabellen,
                vorbehaltHinweis,
                escapeHtml(inhaberText),
                escapeHtml(ibanText),
                escapeHtml(bicText),
                begruendungPunkte,
                belehrungText,
                ortUndDatum,
                escapeHtml(bescheid.erstelltVon()));
    }

    private static String berechnungsTabelle(List<SozialFall> faelle,
                                             Leistungsberechnung.Berechnungsergebnis berechnung,
                                             BigDecimal zahlbetrag) {
        StringBuilder tabelle = new StringBuilder("<table><thead><tr><th>Person / Stufe</th>"
                + "<th class='num'>Regelbedarf</th><th class='num'>Mehrbedarf</th>"
                + "<th class='num'>Unterkunft/Heizung</th><th class='num'>Gesamtbedarf</th>"
                + "<th class='num'>Anrech. Einkommen</th><th class='num'>Zahlbetrag</th></tr></thead><tbody>");
        int index = 1;
        for (Leistungsberechnung.FallBerechnung fb : berechnung.faelle()) {
            SozialFall fall = faelle.stream().filter(f -> f.id() == fb.fallId()).findFirst().orElse(null);
            tabelle.append("<tr><td>Person ").append(index++).append(" (RBS ")
                    .append(fall == null ? "–" : fall.regelbedarfsstufe()).append(")</td>");
            int spalte = 0;
            for (BigDecimal betrag : List.of(fb.regelbedarf(), fb.mehrbedarf(), fb.kdu(), fb.gesamtbedarf(),
                    fb.anrechenbaresEinkommen(), fb.zahlbetrag())) {
                tabelle.append("<td class='num'>").append(formatBetrag(betrag))
                        .append(spalte++ == 2 && fb.kduUnangemessen() ? " € *" : " €").append("</td>");
            }
            tabelle.append("</tr>");
        }
        tabelle.append("<tr class='summenzeile'><td>Gesamtanspruch</td><td>–</td><td>–</td><td>–</td>")
                .append("<td class='num'>").append(formatBetrag(berechnung.gesamtbedarf())).append(" €</td>")
                .append("<td class='num'>").append(formatBetrag(berechnung.anrechenbaresEinkommen())).append(" €</td>")
                .append("<td class='num'>").append(formatBetrag(zahlbetrag)).append(" €</td></tr>");
        if (berechnung.verwertbaresVermoegen().signum() > 0) {
            tabelle.append("<tr><td colspan='6'>Vermögenseinsatz (§ 90 SGB XII)</td><td class='num'>")
                    .append(formatBetrag(berechnung.verwertbaresVermoegen())).append(" €</td></tr>");
        }
        return tabelle.append("</tbody></table>").toString();
    }

    /** Fallbezogene Hauptgründe der Begründung (§ 35 Abs. 1 SGB X). */
    private static String begruendungPunkte(List<SozialFall> faelle,
                                            Leistungsberechnung.Berechnungsergebnis berechnung,
                                            String vermoegenHinweis) {
        StringBuilder punkte = new StringBuilder();
        int person = 1;
        for (Leistungsberechnung.FallBerechnung fb : berechnung.faelle()) {
            SozialFall fall = faelle.stream().filter(f -> f.id() == fb.fallId()).findFirst().orElse(null);
            punkte.append("<li><strong>Person ").append(person++).append(":</strong> Regelbedarf");
            if (fall != null) {
                punkte.append(" der Regelbedarfsstufe ").append(fall.regelbedarfsstufe());
            }
            punkte.append(" nach der Anlage zu § 28 SGB XII: ").append(formatBetrag(fb.regelbedarf())).append(" €");
            if (fb.mehrbedarfG().signum() > 0) {
                punkte.append("; Mehrbedarf Merkzeichen G (§ 30 Abs. 1 SGB XII): ")
                        .append(formatBetrag(fb.mehrbedarfG())).append(" €");
            }
            if (fb.mehrbedarfSchwangere().signum() > 0) {
                punkte.append("; Mehrbedarf Schwangerschaft (§ 30 Abs. 2 SGB XII): ")
                        .append(formatBetrag(fb.mehrbedarfSchwangere())).append(" €");
            }
            if (fb.mehrbedarfAlleinerziehend().signum() > 0) {
                punkte.append("; Mehrbedarf Alleinerziehende (§ 30 Abs. 3 SGB XII): ")
                        .append(formatBetrag(fb.mehrbedarfAlleinerziehend())).append(" €");
            }
            if (fb.mehrbedarfWarmwasser().signum() > 0) {
                punkte.append("; Mehrbedarf dezentrale Warmwassererzeugung (§ 30 Abs. 7 SGB XII): ")
                        .append(formatBetrag(fb.mehrbedarfWarmwasser())).append(" €");
            }
            java.math.BigDecimal ungekappt = fb.mehrbedarfG().add(fb.mehrbedarfSchwangere())
                    .add(fb.mehrbedarfAlleinerziehend());
            if (ungekappt.compareTo(fb.mehrbedarf().subtract(fb.mehrbedarfWarmwasser())) > 0) {
                punkte.append("; die Summe der Mehrbedarfe nach § 30 Abs. 1 bis 5 SGB XII ist auf die "
                        + "Regelbedarfsstufe begrenzt worden (§ 30 Abs. 6 SGB XII)");
            }
            punkte.append("; Kosten der Unterkunft und Heizung (§ 42a i.V.m. § 35 SGB XII): ")
                    .append(formatBetrag(fb.kdu())).append(" €.");
            if (fb.kduWarmwasserAbzug().signum() > 0) {
                punkte.append(" Die Heizkosten enthalten Kosten der zentralen Warmwassererzeugung "
                        + "(§ 35 Abs. 5 SGB XII); ein örtlich festgelegter Anteil von ")
                        .append(formatBetrag(fb.kduWarmwasserAbzug()))
                        .append(" € wurde nicht als Heizbedarf berücksichtigt.");
            }
            punkte.append("</li>");
        }
        punkte.append("<li>Einkommen (§ 82 SGB XII): ");
        boolean erste = true;
        person = 1;
        for (Leistungsberechnung.FallBerechnung fb : berechnung.faelle()) {
            SozialFall fall = faelle.stream().filter(f -> f.id() == fb.fallId()).findFirst().orElse(null);
            if (!erste) {
                punkte.append("; ");
            }
            punkte.append("Person ").append(person++).append(": ");
            if (fall != null) {
                punkte.append("Rente ").append(formatBetrag(fall.einkommenRente()))
                        .append(" €, Erwerbseinkommen (brutto) ").append(formatBetrag(fall.einkommenErwerb())).append(" €, ");
            }
            punkte.append("Erwerbsfreibetrag (§ 82 Abs. 3 SGB XII) ").append(formatBetrag(fb.freibetragErwerb()))
                    .append(" €, Absetzbeträge (§ 82 Abs. 2 SGB XII) ").append(formatBetrag(fb.absetzbetaetrag()))
                    .append(" € → anrechenbar ").append(formatBetrag(fb.anrechenbaresEinkommen())).append(" €");
            erste = false;
        }
        punkte.append(".</li>");
        punkte.append(vermoegenHinweis);
        return punkte.toString();
    }

    /** Vollständige Rechtsbehelfsbelehrung nach § 36 SGB X (Frist, Form, Stelle, Gericht). */
    private static String belehrungText(Rechtsbehelf belehrung) {
        String gericht = belehrung.sozialgericht() != null && !belehrung.sozialgericht().isBlank()
                ? belehrung.sozialgericht() : "das für den Sitz der Behörde zuständige Sozialgericht";
        String bekanntgabe = " Der Bescheid gilt bei Übermittlung durch die Post im Inland am vierten Tag nach "
                + "der Aufgabe zur Post als bekannt gegeben (§ 37 Abs. 2 SGB X).";
        if (Rechtsbehelf.MODUS_KLAGE.equals(belehrung.modus())) {
            return """
                    Gegen diesen Bescheid kann innerhalb eines Monats nach seiner Bekanntgabe Klage bei %s erhoben
                    werden (§ 87 Abs. 1 SGG). Ein Vorverfahren (Widerspruchsverfahren) findet nicht statt.
                    Die Klage ist schriftlich, zur Niederschrift des Urkundsbeamten der Geschäftsstelle oder
                    elektronisch nach § 65a SGG zu erheben. Einer Vertretung durch einen Rechtsanwalt bedarf es
                    nicht — der Rechtsstreit kann selbst geführt werden (§ 73 Abs. 1 SGG).%s""".formatted(escapeHtml(gericht), bekanntgabe);
        }
        String stelle = belehrung.anschrift() != null && !belehrung.anschrift().isBlank()
                ? "der Behörde, die diesen Bescheid erlassen hat (" + escapeHtml(belehrung.anschrift()) + ")"
                : "der Behörde, die diesen Bescheid erlassen hat";
        String widerspruchsbehoerde = belehrung.widerspruchsbehoerde() != null
                && !belehrung.widerspruchsbehoerde().isBlank()
                ? escapeHtml(belehrung.widerspruchsbehoerde()) : "die zuständige Widerspruchsbehörde";
        return """
                Gegen diesen Bescheid kann innerhalb eines Monats nach seiner Bekanntgabe Widerspruch eingelegt
                werden. Der Widerspruch ist schriftlich, elektronisch oder zur Niederschrift bei %s einzulegen
                (§ 84 Abs. 1 SGG, für die elektronische Form § 65a SGG). Die Frist ist auch gewahrt, wenn der
                Widerspruch innerhalb der Frist bei einer anderen
                inländischen Behörde, einem Versicherungsträger oder einer deutschen Konsularbehörde eingeht
                (§ 84 Abs. 2 SGG).%s
                Hilft die Behörde dem Widerspruch nicht ab, entscheidet %s durch Widerspruchsbescheid. Gegen den
                Widerspruchsbescheid kann innerhalb eines Monats nach seiner Bekanntgabe Klage bei %s erhoben
                werden (§ 87 Abs. 2 SGG). Nach Ablauf von drei Monaten ohne Entscheidung über den Widerspruch ist die
                Untätigkeitsklage möglich (§ 88 SGG).""".formatted(stelle, bekanntgabe, widerspruchsbehoerde, escapeHtml(gericht));
    }

    private static String formatBetrag(BigDecimal betrag) {
        if (betrag == null) return "0,00";
        return String.format(Locale.GERMAN, "%,.2f", betrag);
    }

    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
