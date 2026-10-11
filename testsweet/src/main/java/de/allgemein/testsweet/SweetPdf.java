package de.allgemein.testsweet;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Minimaler, eigenständiger PDF-Generator für die automatische Testdokumentation —
 * bewusst als schlanke Eigenentwicklung ohne Drittbibliotheken (Projektstil).
 * Unterstützt:
 * - A4-Seiten mit millimetergenauem Satz
 * - Typografie in Helvetica / Helvetica-Bold (WinAnsiEncoding für deutsche Umlaute und Sonderzeichen)
 * - Farbige Boxen, Karten, Trennlinien und Inhaltsverzeichnis mit gepunkteten Leitern
 * - Trocken-Pagination mit Markenindex
 * - Einbettung hochauflösender Rastergrafiken (JPEG / PNG / Diagramme) als PDF-1.4 XObjects
 * - Automatische Seitenumbruchs-Optimierung gegen verwaiste Zeilen
 */
final class SweetPdf {

    static final float SEITEN_BREITE = 595f;
    static final float SEITEN_HOEHE = 842f;
    static final float RAND = 48f;
    static final float NUTZBARE_BREITE = SEITEN_BREITE - 2 * RAND; // 499 pt

    private static final Charset CP1252 = Charset.forName("windows-1252");

    /** Textzeile oder (text == null) Trennlinie. */
    private record Zeile(String text, boolean fett, int farbe, float groesse, boolean linie) { }

    /** Farbiger Balken (Kasten) mit Text — für Titel- und Ergebnis-Leisten. */
    record Kasten(String text, boolean fett, int textfarbe, int fuellung, float groesse) { }

    /** Inhaltsverzeichniszeile: Label links, gepunktete Leiste, Seitenzahl rechts. */
    record TocZeile(String label, int seite, int farbe) { }

    /** Bildeintrag im Inhaltsstrom. */
    record BildEintrag(String schluessel, BildDaten daten, float renderBreite, float renderHoehe,
                       boolean zentriert, String titel) { }

    /** Bilddaten im Speicher (JPEG-kodiert für /DCTDecode). */
    record BildDaten(byte[] jpegDaten, int pixelBreite, int pixelHoehe) { }

    /** PDF-Objekt im Ausgabestrom. */
    private record PdfObjekt(int nummer, byte[] dictBytes, byte[] streamBytes) { }

    private static final Object SEITEN_UMBRUCH = new Object();
    private static final Map<String, BildDaten> BILD_CACHE = new ConcurrentHashMap<>();

    public static final int SCHWARZ = 0;
    public static final int GRUEN = 1;
    public static final int ROT = 2;
    public static final int BLAU = 3;
    public static final int GRAU = 4;
    public static final int WEISS = 5;
    public static final int DUNKELBLAU = 6;
    public static final int BERNSTEIN = 7;

    /** Füllfarben (heller) für Kästen und Karten. */
    public static final int FUELL_BLAU = 10;
    public static final int FUELL_GRUEN = 11;
    public static final int FUELL_ROT = 12;
    public static final int FUELL_HELLBLAU = 13;
    public static final int FUELL_GRAU = 14;
    public static final int FUELL_BERNSTEIN = 15;
    public static final int FUELL_WEISS = 16;

    private final List<Object> eintraege = new ArrayList<>();
    private final Map<String, Integer> markenIndex = new LinkedHashMap<>();
    private String fusszeile = "";
    private String kopfzeile = "";

    // ------------------------------------------------------------------
    // Aufbau: Texte, Titel & Trenner
    // ------------------------------------------------------------------

    void ueberschrift(String text) {
        zeile("", SCHWARZ, 8);
        zeile(text, true, BLAU, 15);
        zeile("", SCHWARZ, 5);
    }

    void abschnittsTitel(String text) {
        zeile("", SCHWARZ, 5);
        zeile(text, true, SCHWARZ, 10.5f);
        linie();
    }

    void text(String text) {
        text(text, SCHWARZ, 9.5f);
    }

    void text(String text, int farbe, float groesse) {
        int maxZeichen = (int) (NUTZBARE_BREITE / (groesse * 0.52f));
        for (String teil : brichUm(text, maxZeichen)) {
            zeile(teil, false, farbe, groesse);
        }
    }

    void rohZeile(String text, int farbe, float groesse) {
        zeile(text, false, farbe, groesse);
    }

    void fettZeile(String text, int farbe, float groesse) {
        zeile(text, true, farbe, groesse);
    }

    void kasten(String text, boolean fett, int textfarbe, int fuellung, float groesse) {
        float charBreite = groesse * (fett ? 0.58f : 0.52f);
        float maxBreite = NUTZBARE_BREITE - 14;
        float benoetigt = text.length() * charBreite;
        float effGroesse = groesse;
        if (benoetigt > maxBreite) {
            float skaliert = maxBreite / (text.length() * (fett ? 0.55f : 0.50f));
            if (skaliert >= 7.5f) {
                effGroesse = skaliert;
            } else {
                int maxZeichen = (int) (maxBreite / (7.5f * (fett ? 0.55f : 0.50f)));
                text = text.length() > maxZeichen ? text.substring(0, maxZeichen - 1) + "…" : text;
                effGroesse = 7.5f;
            }
        }
        eintraege.add(new Kasten(text, fett, textfarbe, fuellung, effGroesse));
    }

    void tocZeile(String label, int seite, int farbe) {
        int maxZeichen = 78;
        eintraege.add(new TocZeile(label.length() > maxZeichen
                ? label.substring(0, maxZeichen - 1) + "…" : label, seite, farbe));
    }

    void setzeFusszeile(String text) {
        fusszeile = text == null ? "" : text;
    }

    void setzeKopfzeile(String text) {
        kopfzeile = text == null ? "" : text;
    }

    void paar(String label, String wert) {
        text(label + ": " + wert, GRAU, 9.0f);
    }

    void leereZeile() {
        zeile("", SCHWARZ, 6);
    }

    void leereZeile(float hoehe) {
        zeile("", SCHWARZ, hoehe);
    }

    void linie() {
        eintraege.add(new Zeile(null, false, GRAU, 0.5f, true));
    }

    void seitenUmbruch() {
        eintraege.add(SEITEN_UMBRUCH);
    }

    void marke(String id) {
        markenIndex.put(id, eintraege.size());
    }

    private void zeile(String text, boolean fett, int farbe, float groesse) {
        eintraege.add(new Zeile(text, fett, farbe, groesse, false));
    }

    private void zeile(String text, int farbe, float groesse) {
        zeile(text, false, farbe, groesse);
    }

    // ------------------------------------------------------------------
    // Bilder & Diagramme
    // ------------------------------------------------------------------

    /**
     * Fügt ein Bild oder Diagramm zentriert ein.
     * Erkennt Ressourcen im Classpath (/sweet/assets/...) oder Dateipfade.
     * Ist die Renderhöhe <= 0, wird sie proportional aus den Pixelmaßen berechnet.
     */
    void bildZentriert(String pfadOderRessource, float renderBreite, float renderHoehe, String titel) {
        BildDaten daten = ladeBildDaten(pfadOderRessource);
        if (daten == null) {
            zeile("[Grafik nicht geladen: " + pfadOderRessource + "]", false, ROT, 8.5f);
            return;
        }
        float breite = Math.min(renderBreite, NUTZBARE_BREITE);
        float hoehe = renderHoehe > 0 ? renderHoehe
                : breite * ((float) daten.pixelHoehe() / daten.pixelBreite());
        eintraege.add(new BildEintrag(pfadOderRessource, daten, breite, hoehe, true, titel));
    }

    void bildZentriert(String pfadOderRessource, float renderBreite) {
        bildZentriert(pfadOderRessource, renderBreite, 0, null);
    }

    void bildZentriert(String pfadOderRessource, float renderBreite, String titel) {
        bildZentriert(pfadOderRessource, renderBreite, 0, titel);
    }

    void bild(String pfadOderRessource, float renderBreite, float renderHoehe, String titel) {
        BildDaten daten = ladeBildDaten(pfadOderRessource);
        if (daten == null) {
            zeile("[Grafik nicht geladen: " + pfadOderRessource + "]", false, ROT, 8.5f);
            return;
        }
        float breite = Math.min(renderBreite, NUTZBARE_BREITE);
        float hoehe = renderHoehe > 0 ? renderHoehe
                : breite * ((float) daten.pixelHoehe() / daten.pixelBreite());
        eintraege.add(new BildEintrag(pfadOderRessource, daten, breite, hoehe, false, titel));
    }

    private static BildDaten ladeBildDaten(String pfadOderRessource) {
        if (pfadOderRessource == null || pfadOderRessource.isBlank()) {
            return null;
        }
        return BILD_CACHE.computeIfAbsent(pfadOderRessource, key -> {
            // 1. Classpath-Ressource versuchen
            String[] resourcePfade = {
                    key.startsWith("/") ? key : "/" + key,
                    "/sweet/assets/" + key,
                    "/sweet/" + key,
                    "/assets/" + key
            };
            for (String rPfad : resourcePfade) {
                try (InputStream in = SweetPdf.class.getResourceAsStream(rPfad)) {
                    if (in != null) {
                        return leseBildStream(in);
                    }
                } catch (Exception ignored) {
                }
            }

            // 2. Dateisystem versuchen
            String[] dateiPfade = {
                    key,
                    "testsweet/src/main/resources/sweet/assets/" + key,
                    "testsweet/assets/" + key,
                    "doc/manual-bilder/" + key
            };
            for (String dPfad : dateiPfade) {
                Path p = Path.of(dPfad);
                if (Files.exists(p)) {
                    try (InputStream in = Files.newInputStream(p)) {
                        return leseBildStream(in);
                    } catch (Exception ignored) {
                    }
                }
            }
            return null;
        });
    }

    private static BildDaten leseBildStream(InputStream in) throws IOException {
        BufferedImage img = ImageIO.read(in);
        if (img == null) {
            throw new IOException("Bildformat nicht lesbar");
        }
        int w = img.getWidth();
        int h = img.getHeight();
        // Auf weißem Hintergrund rendern, um Transparenzen sauber in RGB abzubilden
        BufferedImage rgb = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        g.drawImage(img, 0, 0, null);
        g.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(rgb, "jpg", baos);
        return new BildDaten(baos.toByteArray(), w, h);
    }

    // ------------------------------------------------------------------
    // Pagination & Marken
    // ------------------------------------------------------------------

    Map<String, Integer> seitenDerMarken() {
        Map<String, Integer> seiten = new LinkedHashMap<>();
        int seite = 1;
        float rest = SEITEN_HOEHE - 2 * RAND;
        for (int i = 0; i < eintraege.size(); i++) {
            Object eintrag = eintraege.get(i);
            float platz = platzBedarf(eintrag);
            if (eintrag == SEITEN_UMBRUCH) {
                seite++;
                rest = SEITEN_HOEHE - 2 * RAND;
                continue;
            }
            if (rest - platz < 0) {
                seite++;
                rest = SEITEN_HOEHE - 2 * RAND;
            }
            rest -= platz;
            for (Map.Entry<String, Integer> marke : markenIndex.entrySet()) {
                if (marke.getValue() == i) {
                    seiten.put(marke.getKey(), seite);
                }
            }
        }
        return seiten;
    }

    int seitenAnzahl() {
        int seite = 1;
        float rest = SEITEN_HOEHE - 2 * RAND;
        for (Object eintrag : eintraege) {
            float platz = platzBedarf(eintrag);
            if (eintrag == SEITEN_UMBRUCH) {
                seite++;
                rest = SEITEN_HOEHE - 2 * RAND;
                continue;
            }
            if (rest - platz < 0) {
                seite++;
                rest = SEITEN_HOEHE - 2 * RAND;
            }
            rest -= platz;
        }
        return seite;
    }

    List<Object> eintraegeKopie() {
        return new ArrayList<>(eintraege);
    }

    void uebernehmeVorn(List<Object> andere) {
        List<Object> bisher = new ArrayList<>(eintraege);
        eintraege.clear();
        eintraege.addAll(andere);
        eintraege.addAll(bisher);
    }

    // ------------------------------------------------------------------
    // Serialisierung: Vollständiges PDF-1.4 Schreiben
    // ------------------------------------------------------------------

    int schreiben(Path datei) throws IOException {
        List<List<Object>> seiten = new ArrayList<>();
        List<Object> aktuell = new ArrayList<>();
        float rest = SEITEN_HOEHE - 2 * RAND;
        for (Object eintrag : eintraege) {
            float platz = platzBedarf(eintrag);
            if (eintrag == SEITEN_UMBRUCH) {
                seiten.add(aktuell);
                aktuell = new ArrayList<>();
                rest = SEITEN_HOEHE - 2 * RAND;
                continue;
            }
            if (rest - platz < 0) {
                seiten.add(aktuell);
                aktuell = new ArrayList<>();
                rest = SEITEN_HOEHE - 2 * RAND;
            }
            aktuell.add(eintrag);
            rest -= platz;
        }
        seiten.add(aktuell);
        if (seiten.isEmpty()) {
            seiten.add(List.of());
        }
        int anzahlSeiten = seiten.size();

        // 1. Alle eindeutigen Bilder sammeln und XObject-Nummern vergeben
        Map<String, Integer> bildObjektNummern = new LinkedHashMap<>();
        Map<String, String> bildAliases = new LinkedHashMap<>();
        int bildZaehler = 1;
        for (List<Object> s : seiten) {
            for (Object obj : s) {
                if (obj instanceof BildEintrag b) {
                    if (!bildAliases.containsKey(b.schluessel())) {
                        String alias = "Im" + bildZaehler++;
                        bildAliases.put(b.schluessel(), alias);
                    }
                }
            }
        }

        int naechsteNummer = 5; // 1 Catalog, 2 Pages, 3+4 Fonts
        for (String schluessel : bildAliases.keySet()) {
            bildObjektNummern.put(schluessel, naechsteNummer++);
        }

        List<Integer> pageObjektNummern = new ArrayList<>();
        for (int s = 0; s < anzahlSeiten; s++) {
            pageObjektNummern.add(naechsteNummer);
            naechsteNummer += 2; // Page + Content
        }

        List<PdfObjekt> koerper = new ArrayList<>();

        // Obj 1: Catalog
        koerper.add(new PdfObjekt(1, "<< /Type /Catalog /Pages 2 0 R >>".getBytes(StandardCharsets.ISO_8859_1), null));

        // Obj 2: Pages
        StringBuilder kids = new StringBuilder();
        for (int i = 0; i < pageObjektNummern.size(); i++) {
            kids.append(i == 0 ? "" : " ").append(pageObjektNummern.get(i)).append(" 0 R");
        }
        koerper.add(new PdfObjekt(2, ("<< /Type /Pages /Kids [" + kids + "] /Count " + anzahlSeiten + " >>")
                .getBytes(StandardCharsets.ISO_8859_1), null));

        // Obj 3 + 4: Fonts
        koerper.add(new PdfObjekt(3, "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>"
                .getBytes(StandardCharsets.ISO_8859_1), null));
        koerper.add(new PdfObjekt(4, "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>"
                .getBytes(StandardCharsets.ISO_8859_1), null));

        // Obj 5..K: Image XObjects
        for (Map.Entry<String, Integer> entry : bildObjektNummern.entrySet()) {
            String schluessel = entry.getKey();
            int objNum = entry.getValue();
            BildDaten daten = BILD_CACHE.get(schluessel);
            String dict = "<< /Type /XObject /Subtype /Image /Width " + daten.pixelBreite()
                    + " /Height " + daten.pixelHoehe()
                    + " /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length "
                    + daten.jpegDaten().length + " >>";
            koerper.add(new PdfObjekt(objNum, dict.getBytes(StandardCharsets.ISO_8859_1), daten.jpegDaten()));
        }

        // XObject-Ressourcen-String für Pages bauen
        StringBuilder xObjDict = new StringBuilder();
        if (!bildAliases.isEmpty()) {
            xObjDict.append(" /XObject << ");
            for (Map.Entry<String, String> entry : bildAliases.entrySet()) {
                int objNum = bildObjektNummern.get(entry.getKey());
                xObjDict.append('/').append(entry.getValue()).append(' ').append(objNum).append(" 0 R ");
            }
            xObjDict.append(">> ");
        }

        // Seiten & Inhaltsströme
        for (int s = 0; s < anzahlSeiten; s++) {
            int pageNummer = pageObjektNummern.get(s);
            int inhaltNummer = pageNummer + 1;
            String pageDict = "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 "
                    + (int) SEITEN_BREITE + " " + (int) SEITEN_HOEHE + "] "
                    + "/Resources << /Font << /F1 3 0 R /F2 4 0 R >>" + xObjDict + ">> /Contents "
                    + inhaltNummer + " 0 R >>";
            koerper.add(new PdfObjekt(pageNummer, pageDict.getBytes(StandardCharsets.ISO_8859_1), null));

            String streamText = inhaltStream(seiten.get(s), s + 1, anzahlSeiten, bildAliases);
            byte[] streamBytes = streamText.getBytes(CP1252);
            String contentDict = "<< /Length " + streamBytes.length + " >>";
            koerper.add(new PdfObjekt(inhaltNummer, contentDict.getBytes(StandardCharsets.ISO_8859_1), streamBytes));
        }

        // PDF als Byte-Array schreiben
        ByteArrayOutputStream baos = new ByteArrayOutputStream(512 * 1024);
        baos.write("%PDF-1.4\n%\u00e2\u00e3\u00cf\u00d3\n".getBytes(StandardCharsets.ISO_8859_1));
        List<Integer> offsets = new ArrayList<>();

        for (PdfObjekt obj : koerper) {
            offsets.add(baos.size());
            baos.write((obj.nummer() + " 0 obj\n").getBytes(StandardCharsets.ISO_8859_1));
            baos.write(obj.dictBytes());
            if (obj.streamBytes() != null) {
                baos.write("\nstream\n".getBytes(StandardCharsets.ISO_8859_1));
                baos.write(obj.streamBytes());
                baos.write("\nendstream".getBytes(StandardCharsets.ISO_8859_1));
            }
            baos.write("\nendobj\n".getBytes(StandardCharsets.ISO_8859_1));
        }

        int xrefStart = baos.size();
        int objektAnzahl = koerper.size() + 1;
        baos.write(("xref\n0 " + objektAnzahl + "\n0000000000 65535 f \n").getBytes(StandardCharsets.ISO_8859_1));
        for (int offset : offsets) {
            baos.write(String.format(Locale.ROOT, "%010d 00000 n \n", offset).getBytes(StandardCharsets.ISO_8859_1));
        }
        baos.write(("trailer\n<< /Size " + objektAnzahl + " /Root 1 0 R >>\nstartxref\n"
                + xrefStart + "\n%%EOF\n").getBytes(StandardCharsets.ISO_8859_1));

        if (datei.getParent() != null) {
            Files.createDirectories(datei.getParent());
        }
        Files.write(datei, baos.toByteArray());
        return anzahlSeiten;
    }

    private String inhaltStream(List<Object> seitenEintraege, int seitenNr, int gesamt,
                                Map<String, String> bildAliases) {
        StringBuilder sb = new StringBuilder();
        float y = SEITEN_HOEHE - RAND;
        String aktFont = "";
        String aktFarbe = "";

        // Kopfzeile auf Seiten > 1
        if (seitenNr > 1 && !kopfzeile.isBlank()) {
            sb.append("BT\n/F1 8 Tf\n").append(farbeAlsPdf(GRAU)).append('\n')
                    .append("1 0 0 1 ").append(RAND).append(' ').append(SEITEN_HOEHE - 28)
                    .append(" Tm (").append(escape(kopfzeile)).append(") Tj\nET\n");
            sb.append("q 0.5 w 0.85 0.85 0.85 RG ")
                    .append(RAND).append(' ').append(SEITEN_HOEHE - 32).append(" m ")
                    .append(SEITEN_BREITE - RAND).append(' ').append(SEITEN_HOEHE - 32).append(" l S Q\n");
        }

        for (Object eintrag : seitenEintraege) {
            if (eintrag instanceof BildEintrag b) {
                String alias = bildAliases.get(b.schluessel());
                if (alias != null) {
                    float w = b.renderBreite();
                    float h = b.renderHoehe();
                    float x = b.zentriert() ? (SEITEN_BREITE - w) / 2f : RAND;
                    float bildY = y - h;

                    sb.append("q ")
                            .append(String.format(Locale.ROOT, "%.2f 0 0 %.2f %.2f %.2f cm\n", w, h, x, bildY))
                            .append('/').append(alias).append(" Do\n")
                            .append("Q\n");

                    // Subtiler Rahmen um das Bild
                    sb.append("q 0.5 w 0.80 0.83 0.88 RG ")
                            .append(String.format(Locale.ROOT, "%.2f %.2f %.2f %.2f re S Q\n", x, bildY, w, h));

                    if (b.titel() != null && !b.titel().isBlank()) {
                        float groesse = 8.5f;
                        float titelY = bildY - 12f;
                        sb.append("BT\n/F1 ").append(groesse).append(" Tf\n")
                                .append(farbeAlsPdf(GRAU)).append('\n')
                                .append("1 0 0 1 ").append(x).append(' ').append(titelY)
                                .append(" Tm (").append(escape(b.titel())).append(") Tj\nET\n");
                        y = titelY - 14f;
                    } else {
                        y = bildY - 12f;
                    }
                    aktFont = "";
                    aktFarbe = "";
                }
                continue;
            }

            if (eintrag instanceof Kasten k) {
                float hoehe = k.groesse() * 1.4f;
                float kastenOben = y + 1.5f;
                sb.append("q ").append(farbeAlsPdf(k.fuellung()))
                        .append(' ').append(RAND).append(' ')
                        .append(kastenOben - hoehe).append(' ')
                        .append(NUTZBARE_BREITE).append(' ').append(hoehe)
                        .append(" re f Q\n");
                String font = "/" + (k.fett() ? "F2" : "F1") + " " + k.groesse() + " Tf";
                sb.append("BT\n").append(font).append('\n')
                        .append(farbeAlsPdf(k.textfarbe())).append('\n')
                        .append("1 0 0 1 ").append(RAND + 5).append(' ')
                        .append(kastenOben - hoehe + (hoehe - k.groesse()) / 2 + 1.2f)
                        .append(" Tm (").append(escape(k.text())).append(") Tj\nET\n");
                y -= hoehe + 4.5f; // Kompakter als vorher (spart Platz)
                aktFont = "";
                aktFarbe = "";
                continue;
            }

            if (eintrag instanceof TocZeile toc) {
                float groesse = 9.0f;
                String seitenText = String.valueOf(toc.seite());
                float zahlBreite = 0.556f * groesse * seitenText.length();
                float zahlX = SEITEN_BREITE - RAND - zahlBreite;
                float labelBreite = toc.label().length() * groesse * 0.50f;
                sb.append("BT\n/F1 ").append(groesse).append(" Tf\n")
                        .append(farbeAlsPdf(toc.farbe())).append('\n')
                        .append("1 0 0 1 ").append(RAND).append(' ').append(y)
                        .append(" Tm (").append(escape(toc.label())).append(") Tj\n")
                        .append("1 0 0 1 ").append(zahlX).append(' ').append(y)
                        .append(" Tm (").append(escape(seitenText)).append(") Tj\nET\n");
                float linkeGrenze = RAND + labelBreite + 6;
                if (linkeGrenze < zahlX - 6) {
                    sb.append("q [2 2] 0 d 0.5 w 0.60 0.60 0.60 RG ")
                            .append(linkeGrenze).append(' ').append(y + 2.4f).append(" m ")
                            .append(zahlX - 5).append(' ').append(y + 2.4f).append(" l S Q\n");
                }
                y -= 13f;
                continue;
            }

            Zeile zeile = (Zeile) eintrag;
            sb.append("BT\n");
            if (zeile.linie()) {
                sb.append("ET\nq 0.7 w 0.80 0.80 0.80 RG ")
                        .append(RAND).append(' ').append(y + 2).append(" m ")
                        .append(SEITEN_BREITE - RAND).append(' ').append(y + 2).append(" l S\nQ\n");
                y -= 8f;
                continue;
            }
            String font = "/" + (zeile.fett() ? "F2" : "F1") + " " + zeile.groesse() + " Tf";
            if (!font.equals(aktFont)) {
                sb.append(font).append('\n');
                aktFont = font;
            }
            String farbe = farbeAlsPdf(zeile.farbe());
            if (!farbe.equals(aktFarbe)) {
                sb.append(farbe).append('\n');
                aktFarbe = farbe;
            }
            sb.append("1 0 0 1 ").append(RAND).append(' ').append(y).append(" Tm (")
                    .append(escape(zeile.text())).append(") Tj\nET\n");
            y -= zeilenSchritt(zeile.groesse());
        }

        // Fußzeile: Dokumentname links, "Seite N von M" rechts
        if (!fusszeile.isBlank() || gesamt > 1) {
            String rechts = "Seite " + seitenNr + " von " + gesamt;
            float groesse = 8.0f;
            float zahlBreite = 0.556f * groesse * rechts.length() + groesse * 0.28f * 3;
            sb.append("BT\n/F1 ").append(groesse).append(" Tf\n").append(farbeAlsPdf(GRAU)).append('\n')
                    .append("1 0 0 1 ").append(RAND).append(" 24 Tm (")
                    .append(escape(fusszeile)).append(") Tj\n")
                    .append("1 0 0 1 ").append(SEITEN_BREITE - RAND - zahlBreite)
                    .append(" 24 Tm (").append(escape(rechts)).append(") Tj\nET");
        }
        return sb.toString();
    }

    private static String farbeAlsPdf(int farbe) {
        return switch (farbe) {
            case GRUEN -> "0.08 0.50 0.20 rg";
            case ROT -> "0.78 0.12 0.12 rg";
            case BLAU -> "0.12 0.31 0.58 rg";
            case DUNKELBLAU -> "0.06 0.15 0.35 rg";
            case BERNSTEIN -> "0.75 0.42 0.05 rg";
            case GRAU -> "0.38 0.42 0.48 rg";
            case WEISS -> "1.00 1.00 1.00 rg";
            case FUELL_BLAU -> "0.12 0.31 0.58 rg";
            case FUELL_GRUEN -> "0.12 0.52 0.24 rg";
            case FUELL_ROT -> "0.75 0.15 0.15 rg";
            case FUELL_HELLBLAU -> "0.91 0.94 0.98 rg";
            case FUELL_GRAU -> "0.94 0.95 0.96 rg";
            case FUELL_BERNSTEIN -> "0.99 0.95 0.88 rg";
            case FUELL_WEISS -> "1.00 1.00 1.00 rg";
            default -> "0.05 0.09 0.16 rg";
        };
    }

    private static float platzBedarf(Object eintrag) {
        if (eintrag == SEITEN_UMBRUCH) {
            return 0f;
        }
        if (eintrag instanceof BildEintrag b) {
            return b.renderHoehe() + (b.titel() != null && !b.titel().isBlank() ? 26f : 12f);
        }
        if (eintrag instanceof Kasten k) {
            return k.groesse() * 1.4f + 4.5f;
        }
        if (eintrag instanceof TocZeile) {
            return 13f;
        }
        Zeile zeile = (Zeile) eintrag;
        if (zeile.linie()) {
            return 8f;
        }
        return zeilenSchritt(zeile.groesse());
    }

    static float zeilenSchritt(float groesse) {
        return groesse > 16f ? groesse * 1.40f : Math.max(groesse * 1.20f, 6.5f);
    }

    private static String escape(String text) {
        StringBuilder sb = new StringBuilder();
        for (char c : normalisiere(text).toCharArray()) {
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '(' -> sb.append("\\(");
                case ')' -> sb.append("\\)");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String normalisiere(String text) {
        return text
                .replace("\u2264", "<=")
                .replace("\u2265", ">=")
                .replace("\u2286", "in")
                .replace("\u2212", "-");
    }

    private static List<String> brichUm(String text, int maxZeichen) {
        List<String> ergebnis = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            ergebnis.add("");
            return ergebnis;
        }
        for (String absatz : text.split("\n", -1)) {
            if (absatz.isBlank()) {
                ergebnis.add("");
                continue;
            }
            String[] woerter = absatz.split(" ");
            StringBuilder aktuelle = new StringBuilder();
            for (String wort : woerter) {
                if (aktuelle.length() == 0) {
                    aktuelle.append(wort);
                } else if (aktuelle.length() + 1 + wort.length() <= maxZeichen) {
                    aktuelle.append(' ').append(wort);
                } else {
                    ergebnis.add(aktuelle.toString());
                    aktuelle.setLength(0);
                    aktuelle.append(wort);
                }
            }
            if (aktuelle.length() > 0) {
                ergebnis.add(aktuelle.toString());
            }
        }
        return ergebnis;
    }
}
