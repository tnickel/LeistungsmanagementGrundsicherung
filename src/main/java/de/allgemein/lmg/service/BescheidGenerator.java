package de.allgemein.lmg.service;

import de.allgemein.lmg.calc.Leistungsberechnung;
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
 * BESCHEID_DATUM ist das Bekanntgabedatum (§ 37 SGB X).
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
     *                             (leer = die im Briefkopf genannte Behörde)
     * @param sozialgericht       Name und Sitz des zuständigen Sozialgerichts
     */
    public record Rechtsbehelf(String modus, String widerspruchsbehoerde, String sozialgericht) {

        public static final String MODUS_WIDERSPRUCH = "WIDERSPRUCH";
        public static final String MODUS_KLAGE = "KLAGE";

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
        Rechtsbehelf belehrung = rechtsbehelf == null ? Rechtsbehelf.standard() : rechtsbehelf;
        String datumText = bescheid.bescheidDatum().format(DATUM_FORMAT);
        String zahlbetragText = formatBetrag(bescheid.zahlbetrag());
        String ibanText = bg.iban() != null ? SepaValidator.formatiereIban(bg.iban()) : "nicht hinterlegt";
        String bicText = bg.bic() != null ? bg.bic() : "–";
        String inhaberText = bg.kontoinhaber() != null && !bg.kontoinhaber().isBlank()
                ? bg.kontoinhaber() : bg.hauptpersonName();
        String vorbehaltHinweis = berechnung.faelle().stream().anyMatch(Leistungsberechnung.FallBerechnung::kduUnangemessen)
                ? "<p class='vorbehalt'>Die Kosten der Unterkunft übersteigen die ortsübliche Angemessenheitsgrenze. "
                        + "Sie werden vorläufig in voller Höhe unter dem Vorbehalt der Rückforderung übernommen "
                        + "(§ 42a i.V.m. § 35 SGB XII); eine Senkung ist möglich, wenn die unangemessenen Aufwendungen "
                        + "nicht gesenkt werden.</p>"
                : "";
        String vermoegenHinweis = berechnung.verwertbaresVermoegen().signum() > 0
                ? "<li>Verwertbares Vermögen nach Abzug des Schonvermögens (§ 90 Abs. 2 Nr. 14 SGB XII): "
                        + formatBetrag(berechnung.verwertbaresVermoegen()) + " € — der Bedarf wird gemindert "
                        + "(§ 43 Abs. 3 SGB XII).</li>"
                : "";

        StringBuilder fallZeilen = new StringBuilder();
        int index = 1;
        for (Leistungsberechnung.FallBerechnung fb : berechnung.faelle()) {
            SozialFall fall = faelle.stream().filter(f -> f.id() == fb.fallId()).findFirst().orElse(null);
            fallZeilen.append("<tr>")
                    .append("<td>Person ").append(index++).append(" (RBS ").append(fall != null ? fall.regelbedarfsstufe() : "-").append(")</td>")
                    .append("<td class='num'>").append(formatBetrag(fb.regelbedarf())).append(" €</td>")
                    .append("<td class='num'>").append(formatBetrag(fb.mehrbedarf())).append(" €</td>")
                    .append("<td class='num'>").append(formatBetrag(fb.kdu())).append(fb.kduUnangemessen() ? " € *" : " €").append("</td>")
                    .append("<td class='num'><strong>").append(formatBetrag(fb.gesamtbedarf())).append(" €</strong></td>")
                    .append("<td class='num'>").append(formatBetrag(fb.anrechenbaresEinkommen())).append(" €</td>")
                    .append("<td class='num'><strong>").append(formatBetrag(fb.zahlbetrag())).append(" €</strong></td>")
                    .append("</tr>");
        }

        String begruendungPunkte = begruendungPunkte(berechnung, vermoegenHinweis);
        String belehrungText = belehrungText(belehrung);

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
    }
  </style>
</head>
<body>
  <button class="druck-button" onclick="window.print()">🖨️ Diesen Bescheid drucken (PDF)</button>

  <div class="briefkopf">
    <div class="behoerde">Amt für Soziales und Wohnen — Leistungsmanagement</div>
    <div class="untertitel">SDN.sozial · Grundsicherung im Alter und bei Erwerbsminderung (SGB XII)</div>
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
      <strong>Bekanntgegeben am:</strong> %s (§ 37 SGB X)<br>
      <strong>Sachbearbeiter:</strong> %s
    </div>
  </div>

  <div class="titel">Bewilligungsbescheid über Leistungen der Grundsicherung</div>

  <div class="tenor">
    <strong>Verfügungssatz:</strong><br>
    Auf Ihren Antrag werden für die oben genannte Bedarfsgemeinschaft Leistungen der Grundsicherung im Alter
    und bei Erwerbsminderung nach dem Vierten Kapitel des Zwölften Buches Sozialgesetzbuch (SGB XII)
    für den Bewilligungszeitraum <strong>%s bis %s</strong> (§ 44 SGB XII) in Höhe von monatlich
    <strong>%s EUR</strong> bewilligt. Die Leistung wird jeweils monatlich im Voraus gezahlt (§ 44 Abs. 4 SGB XII).
  </div>

  <div class="abschnitt">
    <div class="abschnitt-titel">1. Berechnung des monatlichen Leistungsanspruchs</div>
    <table>
      <thead>
        <tr>
          <th>Person / Stufe</th>
          <th class="num">Regelbedarf</th>
          <th class="num">Mehrbedarf</th>
          <th class="num">Unterkunft/Heizung</th>
          <th class="num">Gesamtbedarf</th>
          <th class="num">Anrech. Einkommen</th>
          <th class="num">Zahlbetrag</th>
        </tr>
      </thead>
      <tbody>
        %s
        <tr class="summenzeile">
          <td>Gesamtanspruch</td>
          <td class="num">–</td>
          <td class="num">–</td>
          <td class="num">–</td>
          <td class="num">%s €</td>
          <td class="num">%s €</td>
          <td class="num" style="color:#047857; font-size:11pt;">%s €</td>
        </tr>
      </tbody>
    </table>
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
      %s, den %s<br>
      <strong>Amt für Soziales und Wohnen</strong><br>
      i. A. %s
    </div>
  </div>
</body>
</html>
""".formatted(
                bescheid.bescheidNr(),
                escapeHtml(bg.hauptpersonName()),
                escapeHtml(bg.aktenzeichen()),
                escapeHtml(bescheid.bescheidNr()),
                datumText,
                escapeHtml(bescheid.erstelltVon()),
                escapeHtml(bescheid.bewilligungVon()),
                escapeHtml(bescheid.bewilligungBis()),
                zahlbetragText,
                fallZeilen.toString(),
                formatBetrag(berechnung.gesamtbedarf()),
                formatBetrag(berechnung.anrechenbaresEinkommen()),
                zahlbetragText,
                vorbehaltHinweis,
                escapeHtml(inhaberText),
                escapeHtml(ibanText),
                escapeHtml(bicText),
                begruendungPunkte,
                belehrungText,
                "Musterstadt",
                datumText,
                escapeHtml(bescheid.erstelltVon()));
    }

    /** Fallbezogene Hauptgründe der Begründung (§ 35 Abs. 1 SGB X). */
    private static String begruendungPunkte(Leistungsberechnung.Berechnungsergebnis berechnung,
                                            String vermoegenHinweis) {
        StringBuilder punkte = new StringBuilder();
        int person = 1;
        for (Leistungsberechnung.FallBerechnung fb : berechnung.faelle()) {
            punkte.append("<li><strong>Person ").append(person++).append(":</strong> Regelbedarf Stufe ")
                    .append("nach der Anlage zu § 28 SGB XII: ").append(formatBetrag(fb.regelbedarf())).append(" €");
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
            punkte.append("; Kosten der Unterkunft und Heizung (§ 42a i.V.m. § 35 SGB XII): ")
                    .append(formatBetrag(fb.kdu())).append(" €.");
            if (fb.kduWarmwasserAbzug().signum() > 0) {
                punkte.append(" Der Warmwasseranteil ist in den Heizkosten enthalten und wurde um ")
                        .append(formatBetrag(fb.kduWarmwasserAbzug()))
                        .append(" € gemindert (Abgeltung über § 30 Abs. 7 SGB XII).");
            }
            punkte.append("</li>");
        }
        punkte.append("<li>Einkommensberücksichtigung: Erwerbsfreibetrag nach § 82 Abs. 3 SGB XII ");
        boolean erste = true;
        for (Leistungsberechnung.FallBerechnung fb : berechnung.faelle()) {
            if (!erste) {
                punkte.append(", ");
            }
            punkte.append(formatBetrag(fb.freibetragErwerb())).append(" €");
            erste = false;
        }
        punkte.append("; Absetzbeträge nach § 82 Abs. 2 SGB XII (Steuern, Sozialversicherung, "
                + "Versicherungen, Erwerbsaufwendungen).</li>");
        punkte.append(vermoegenHinweis);
        return punkte.toString();
    }

    /** Vollständige Rechtsbehelfsbelehrung nach § 36 SGB X (Frist, Form, Stelle, Gericht). */
    private static String belehrungText(Rechtsbehelf belehrung) {
        String gericht = belehrung.sozialgericht() != null && !belehrung.sozialgericht().isBlank()
                ? belehrung.sozialgericht() : "das für den Sitz der Behörde zuständige Sozialgericht";
        if (Rechtsbehelf.MODUS_KLAGE.equals(belehrung.modus())) {
            return """
                    Gegen diesen Bescheid kann innerhalb eines Monats nach seiner Bekanntgabe Klage bei %s erhoben
                    werden (§ 87 Abs. 1 SGG). Ein Vorverfahren (Widerspruchsverfahren) findet nicht statt.
                    Die Klage ist schriftlich, zur Niederschrift des Urkundsbeamten der Geschäftsstelle oder
                    elektronisch nach § 65a SGG zu erheben. Einer Vertretung durch einen Rechtsanwalt bedarf es
                    nicht (§ 73 Abs. 2 SGG).""".formatted(escapeHtml(gericht));
        }
        String behoerde = belehrung.widerspruchsbehoerde() != null && !belehrung.widerspruchsbehoerde().isBlank()
                ? escapeHtml(belehrung.widerspruchsbehoerde())
                : "der im Briefkopf genannten Behörde";
        return """
                Gegen diesen Bescheid kann innerhalb eines Monats nach seiner Bekanntgabe schriftlich oder zur
                Niederschrift bei %s Widerspruch eingelegt werden. Der Widerspruch kann auch zur Niederschrift
                bei jeder anderen den Widerspruch entgegennehmenden Behörde eingelegt werden.
                Hilft der Widerspruchsbehörde dem Widerspruch nicht ab, ergeht ein Widerspruchsbescheid. Dagegen
                kann innerhalb eines Monats nach seiner Bekanntgabe Klage bei %s erhoben werden
                (§§ 78, 85 SGG). Nach Ablauf von drei Monaten ohne Entscheidung über den Widerspruch ist die
                Untätigkeitsklage möglich (§ 88 SGG).""".formatted(behoerde, escapeHtml(gericht));
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
