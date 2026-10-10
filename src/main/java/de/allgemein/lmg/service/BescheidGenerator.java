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
 * Erzeugung von rechtssicheren Bewilligungsbescheiden nach § 37 SGB X für das
 * Vierte Kapitel des SGB XII (Grundsicherung im Alter und bei Erwerbsminderung).
 * Enthält Verfügungssatz, Bedarfsaufschlüsselung, Zahlungsverbindung,
 * Begründung (§ 35 SGB X) und Rechtsbehelfsbelehrung (§ 36 SGB X).
 */
public final class BescheidGenerator {

    private static final DateTimeFormatter DATUM_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMAN);

    private BescheidGenerator() {
    }

    public static String generiereHtml(Bedarfsgemeinschaft bg, List<SozialFall> faelle,
                                       Leistungsberechnung.Berechnungsergebnis berechnung,
                                       Bescheid bescheid) {
        String datumText = bescheid.bescheidDatum().format(DATUM_FORMAT);
        String zahlbetragText = formatBetrag(bescheid.zahlbetrag());
        String ibanText = bg.iban() != null ? SepaValidator.formatiereIban(bg.iban()) : "nicht hinterlegt";
        String bicText = bg.bic() != null ? bg.bic() : "–";
        String inhaberText = bg.kontoinhaber() != null && !bg.kontoinhaber().isBlank()
                ? bg.kontoinhaber() : bg.hauptpersonName();

        StringBuilder fallZeilen = new StringBuilder();
        int index = 1;
        for (Leistungsberechnung.FallBerechnung fb : berechnung.faelle()) {
            SozialFall fall = faelle.stream().filter(f -> f.id() == fb.fallId()).findFirst().orElse(null);
            fallZeilen.append("<tr>")
                    .append("<td>Person ").append(index++).append(" (RBS ").append(fall != null ? fall.regelbedarfsstufe() : "-").append(")</td>")
                    .append("<td class='num'>").append(formatBetrag(fb.regelbedarf())).append(" €</td>")
                    .append("<td class='num'>").append(formatBetrag(fb.mehrbedarf())).append(" €</td>")
                    .append("<td class='num'>").append(formatBetrag(fb.kdu())).append(" €</td>")
                    .append("<td class='num'><strong>").append(formatBetrag(fb.gesamtbedarf())).append(" €</strong></td>")
                    .append("<td class='num'>").append(formatBetrag(fb.anrechenbaresEinkommen())).append(" €</td>")
                    .append("<td class='num'><strong>").append(formatBetrag(fb.zahlbetrag())).append(" €</strong></td>")
                    .append("</tr>");
        }

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
      <strong>Datum:</strong> %s<br>
      <strong>Sachbearbeiter:</strong> %s
    </div>
  </div>

  <div class="titel">Bewilligungsbescheid über Leistungen der Grundsicherung</div>

  <div class="tenor">
    <strong>Verfügungssatz (§ 37 SGB X):</strong><br>
    Auf Ihren Antrag werden für die oben genannte Bedarfsgemeinschaft Leistungen der Grundsicherung im Alter
    und bei Erwerbsminderung nach dem Vierten Kapitel des Zwölften Buches Sozialgesetzbuch (SGB XII)
    für den Bewilligungszeitraum <strong>%s bis %s</strong> in Höhe von monatlich <strong>%s EUR</strong> bewilligt.
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
    <p>Die Voraussetzungen für die Gewährung von Leistungen der Grundsicherung im Alter und bei Erwerbsminderung
    nach §§ 41 ff. SGB XII liegen vor. Die Bedarfe für den Lebensunterhalt sowie die Kosten der Unterkunft und
    Heizung (§ 35 SGB XII) übersteigen das nach § 82 SGB XII zu berücksichtigende Einkommen unter Abzug der
    gesetzlichen Freibeträge (§ 82 Abs. 3 SGB XII).</p>
  </div>

  <div class="abschnitt">
    <div class="abschnitt-titel">4. Rechtsbehelfsbelehrung (§ 36 SGB X)</div>
    <div class="belehrung">
      Gegen diesen Bescheid kann innerhalb eines Monats nach seiner Bekanntgabe schriftlich oder zur
      Niederschrift bei der im Briefkopf genannten Behörde Widerspruch erhoben werden.
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
                escapeHtml(inhaberText),
                escapeHtml(ibanText),
                escapeHtml(bicText));
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
