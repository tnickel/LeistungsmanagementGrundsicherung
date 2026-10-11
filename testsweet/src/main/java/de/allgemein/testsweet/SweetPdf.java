package de.allgemein.testsweet;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimaler PDF-Generator für die automatische Testdokumentation — bewusst als
 * eigene schlanke Umsetzung ohne Fremd-Bibliothek (Projektstil). Unterstützt
 * A4-Seiten, Helvetica/Fett, Textfarben, Trennlinien, erzwungene Seitenumbrüche
 * (für Deckblatt), Marken mit Trocken-Pagination (für das Inhaltsverzeichnis)
 * sowie Wortumbruch; Sonderzeichen werden nach Windows-1252 (WinAnsiEncoding)
 * abgebildet, damit deutsche Umlaute korrekt erscheinen.
 */
final class SweetPdf {

    private static final float SEITEN_BREITE = 595f;
    private static final float SEITEN_HOEHE = 842f;
    private static final float RAND = 48f;

    private static final Charset CP1252 = Charset.forName("windows-1252");

    /** Textzeile oder (text == null) Trennlinie. */
    private record Zeile(String text, boolean fett, int farbe, float groesse, boolean linie) { }

    /** Farbiger Balken (Kasten) mit Text — für Titel- und Ergebnis-Leisten. */
    record Kasten(String text, boolean fett, int textfarbe, int fuellung, float groesse) { }

    /** Inhaltsverzeichniszeile: Label links, gepunktete Leiste, Seitenzahl rechts. */
    record TocZeile(String label, int seite, int farbe) { }

    private static final Object SEITEN_UMBRUCH = new Object();

    public static final int SCHWARZ = 0;
    public static final int GRUEN = 1;
    public static final int ROT = 2;
    public static final int BLAU = 3;
    public static final int GRAU = 4;
    public static final int WEISS = 5;
    /** Füllfarben (heller) für Kästen. */
    public static final int FUELL_BLAU = 10;
    public static final int FUELL_GRUEN = 11;
    public static final int FUELL_ROT = 12;
    public static final int FUELL_HELLBLAU = 13;
    public static final int FUELL_GRAU = 14;

    private final List<Object> eintraege = new ArrayList<>();
    private final Map<String, Integer> markenIndex = new LinkedHashMap<>();
    private String fusszeile = "";

    // ------------------------------------------------------------------
    // Aufbau
    // ------------------------------------------------------------------

    void ueberschrift(String text) {
        zeile("", SCHWARZ, 10);
        zeile(text, true, BLAU, 14);
        zeile("", SCHWARZ, 6);
    }

    void abschnittsTitel(String text) {
        zeile("", SCHWARZ, 5);
        zeile(text, true, SCHWARZ, 11);
        linie();
    }

    void text(String text) {
        text(text, SCHWARZ, 10);
    }

    void text(String text, int farbe, float groesse) {
        int maxZeichen = (int) ((SEITEN_BREITE - 2 * RAND) / (groesse * 0.52f));
        for (String teil : brichUm(text, maxZeichen)) {
            zeile(teil, false, farbe, groesse);
        }
    }

    /** Feste Zeile ohne Umbruch (z. B. Tabellenzeile des Inhaltsverzeichnisses). */
    void rohZeile(String text, int farbe, float groesse) {
        zeile(text, false, farbe, groesse);
    }

    /** Feste fette Zeile ohne Umbruch (z. B. Blocktitel im Rechenweg). */
    void fettZeile(String text, int farbe, float groesse) {
        zeile(text, true, farbe, groesse);
    }

    /** Farbige Titel-/Ergebnis-Leiste mit Text (einzeilig, einrückungsfrei).
     *  Der Kürzfaktor ist bewusst konservativ (Helvetica-Bold Ø 0,6 em), damit
     *  der Text sicher IN der Leiste bleibt. */
    void kasten(String text, boolean fett, int textfarbe, int fuellung, float groesse) {
        int maxZeichen = (int) ((SEITEN_BREITE - 2 * RAND - 14) / (groesse * (fett ? 0.62f : 0.56f)));
        String gekuerzt = text.length() > maxZeichen
                ? text.substring(0, maxZeichen - 1) + "…" : text;
        eintraege.add(new Kasten(gekuerzt, fett, textfarbe, fuellung, groesse));
    }

    /** Inhaltsverzeichniszeile: Label links, gepunktete Leiste, Seitenzahl rechtsbündig. */
    void tocZeile(String label, int seite, int farbe) {
        int maxZeichen = 78;
        eintraege.add(new TocZeile(label.length() > maxZeichen
                ? label.substring(0, maxZeichen - 1) + "…" : label, seite, farbe));
    }

    /** Fußzeile auf jeder Seite (z. B. Dokumentname; Seitenzahl hängt der Generator an). */
    void setzeFusszeile(String text) {
        fusszeile = text == null ? "" : text;
    }

    void paar(String label, String wert) {
        text(label + ": " + wert, GRAU, 9.5f);
    }

    void leereZeile() {
        zeile("", SCHWARZ, 8);
    }

    void linie() {
        eintraege.add(new Zeile(null, false, GRAU, 0.5f, true));
    }

    /** Erzwingt eine neue Seite (z. B. nach dem Deckblatt). */
    void seitenUmbruch() {
        eintraege.add(SEITEN_UMBRUCH);
    }

    /** Merkt die aktuelle Position; {@link #seitenDerMarken()} liefert dazu die Seite. */
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
    // Pagination (trocken) und Marken
    // ------------------------------------------------------------------

    /** Seitennummer je Marke bei der jetzigen Belegung (ohne Verschiebung) — fürs TOC. */
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

    /** Trockene Seitenzahl der bisherigen Einträge (ohne Datei zu schreiben). */
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

    /** Interne Eintragsliste als Kopie (zum Zusammenbau mehrerer Teildokumente). */
    List<Object> eintraegeKopie() {
        return new ArrayList<>(eintraege);
    }

    /** Übernimmt Einträge eines anderen Teildokuments vor die eigenen. */
    void uebernehmeVorn(List<Object> andere) {
        List<Object> bisher = new ArrayList<>(eintraege);
        eintraege.clear();
        eintraege.addAll(andere);
        eintraege.addAll(bisher);
    }

    // ------------------------------------------------------------------
    // Serialisierung
    // ------------------------------------------------------------------

    /** Schreibt die PDF-Datei; Rückgabe: Anzahl Seiten. */
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

        List<String> koerper = new ArrayList<>();
        List<Integer> pageObjektNummern = new ArrayList<>();
        int naechsteNummer = 5; // 1 Catalog, 2 Pages, 3+4 Fonts; ab 5 je Seite Page+Contents
        for (int s = 0; s < anzahlSeiten; s++) {
            pageObjektNummern.add(naechsteNummer);
            naechsteNummer += 2;
        }
        koerper.add("<< /Type /Catalog /Pages 2 0 R >>");
        StringBuilder kids = new StringBuilder();
        for (int i = 0; i < pageObjektNummern.size(); i++) {
            kids.append(i == 0 ? "" : " ").append(pageObjektNummern.get(i)).append(" 0 R");
        }
        koerper.add("<< /Type /Pages /Kids [" + kids + "] /Count " + anzahlSeiten + " >>");
        koerper.add("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>");
        koerper.add("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>");

        for (int s = 0; s < anzahlSeiten; s++) {
            int pageNummer = pageObjektNummern.get(s);
            int inhaltNummer = pageNummer + 1;
            koerper.add("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 "
                    + (int) SEITEN_BREITE + " " + (int) SEITEN_HOEHE + "] "
                    + "/Resources << /Font << /F1 3 0 R /F2 4 0 R >> >> /Contents "
                    + inhaltNummer + " 0 R >>");
            String stream = inhaltStream(seiten.get(s), s + 1, anzahlSeiten);
            koerper.add("<< /Length " + stream.getBytes(StandardCharsets.ISO_8859_1).length
                    + " >>\nstream\n" + stream + "\nendstream");
        }

        StringBuilder ausgabe = new StringBuilder("%PDF-1.4\n%\u00e2\u00e3\u00cf\u00d3\n");
        List<Integer> offsets = new ArrayList<>();
        for (int i = 0; i < koerper.size(); i++) {
            offsets.add(ausgabe.toString().getBytes(StandardCharsets.ISO_8859_1).length);
            ausgabe.append(i + 1).append(" 0 obj\n").append(koerper.get(i)).append("\nendobj\n");
        }
        int xrefStart = ausgabe.toString().getBytes(StandardCharsets.ISO_8859_1).length;
        int objektAnzahl = koerper.size() + 1;
        ausgabe.append("xref\n0 ").append(objektAnzahl).append("\n0000000000 65535 f \n");
        for (int offset : offsets) {
            ausgabe.append(String.format("%010d 00000 n \n", offset));
        }
        ausgabe.append("trailer\n<< /Size ").append(objektAnzahl)
                .append(" /Root 1 0 R >>\nstartxref\n").append(xrefStart).append("\n%%EOF");

        try (OutputStream out = Files.newOutputStream(datei)) {
            out.write(ausgabe.toString().getBytes(CP1252));
        }
        return anzahlSeiten;
    }

    private String inhaltStream(List<Object> seitenEintraege, int seitenNr, int gesamt) {
        StringBuilder sb = new StringBuilder();
        float y = SEITEN_HOEHE - RAND;
        String aktFont = "";
        String aktFarbe = "";
        for (Object eintrag : seitenEintraege) {
            if (eintrag instanceof Kasten k) {
                float hoehe = k.groesse() * 1.5f;
                float kastenOben = y + 2;
                sb.append("q ").append(farbeAlsPdf(k.fuellung()))
                        .append(' ').append(RAND).append(' ')
                        .append(kastenOben - hoehe).append(' ')
                        .append(SEITEN_BREITE - 2 * RAND).append(' ').append(hoehe)
                        .append(" re f Q\n");
                String font = "/" + (k.fett() ? "F2" : "F1") + " " + k.groesse() + " Tf";
                sb.append("BT\n").append(font).append('\n')
                        .append(farbeAlsPdf(k.textfarbe())).append('\n')
                        .append("1 0 0 1 ").append(RAND + 6).append(' ').append(kastenOben - hoehe + (hoehe - k.groesse()) / 2 + 1.5f)
                        .append(" Tm (").append(escape(k.text())).append(") Tj\nET\n");
                y -= hoehe + 8f;
                aktFont = "";
                aktFarbe = "";
                continue;
            }
            if (eintrag instanceof TocZeile toc) {
                float groesse = 9.5f;
                String seitenText = String.valueOf(toc.seite());
                // Ziffern in Helvetica sind 0,556 em breit → Rechtsbündigkeit reicht genau
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
                    sb.append("q [2 2] 0 d 0.5 w 0.55 0.55 0.55 RG ")
                            .append(linkeGrenze).append(' ').append(y + 2.6f).append(" m ")
                            .append(zahlX - 5).append(' ').append(y + 2.6f).append(" l S Q\n");
                }
                y -= 14f;
                continue;
            }
            Zeile zeile = (Zeile) eintrag;
            sb.append("BT\n");
            if (zeile.linie()) {
                sb.append("ET\nq 0.8 w 0.75 0.75 0.75 RG ")
                        .append(RAND).append(' ').append(y + 3).append(" m ")
                        .append(SEITEN_BREITE - RAND).append(' ').append(y + 3).append(" l S\nQ\n");
                y -= 10f;
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
            float groesse = 8.5f;
            float zahlBreite = 0.556f * groesse * rechts.length() + groesse * 0.28f * 3;
            sb.append("BT\n/F1 ").append(groesse).append(" Tf\n").append(farbeAlsPdf(GRAU)).append('\n')
                    .append("1 0 0 1 ").append(RAND).append(" 26 Tm (")
                    .append(escape(fusszeile)).append(") Tj\n")
                    .append("1 0 0 1 ").append(SEITEN_BREITE - RAND - zahlBreite)
                    .append(" 26 Tm (").append(escape(rechts)).append(") Tj\nET");
        }
        return sb.toString();
    }

    private static String farbeAlsPdf(int farbe) {
        return switch (farbe) {
            case GRUEN -> "0.00 0.45 0.15 rg";
            case ROT -> "0.75 0.10 0.10 rg";
            case BLAU -> "0.12 0.31 0.47 rg";
            case GRAU -> "0.40 0.40 0.40 rg";
            case WEISS -> "1.00 1.00 1.00 rg";
            case FUELL_BLAU -> "0.12 0.31 0.47 rg";
            case FUELL_GRUEN -> "0.18 0.44 0.24 rg";
            case FUELL_ROT -> "0.72 0.13 0.13 rg";
            case FUELL_HELLBLAU -> "0.86 0.91 0.96 rg";
            case FUELL_GRAU -> "0.93 0.93 0.93 rg";
            default -> "0.00 0.00 0.00 rg";
        };
    }

    /** Vertikaler Platzbedarf eines Eintrags (für die Pagination); große
     *  Schriftgrade brauchen mehr Abstand nach oben (Auf-/Unterlängen). */
    private static float platzBedarf(Object eintrag) {
        if (eintrag == SEITEN_UMBRUCH) {
            return 0f;
        }
        if (eintrag instanceof Kasten k) {
            return k.groesse() * 1.5f + 8f;
        }
        if (eintrag instanceof TocZeile) {
            return 14f;
        }
        Zeile zeile = (Zeile) eintrag;
        if (zeile.linie()) {
            return 10f;
        }
        return zeilenSchritt(zeile.groesse());
    }

    /** Zeilenabstand: Normalfall 1,25-fach; ab 16 pt deutlich luftiger (Titelseiten). */
    static float zeilenSchritt(float groesse) {
        return groesse > 16f ? groesse * 1.55f : Math.max(groesse * 1.25f, 7f);
    }

    /** Klammert PDF-Sonderzeichen; alle anderen Zeichen durchläuft unverändert —
     *  die CP1252-Ausgabeabbildung regelt die Bytecodierung (— – „ “ … × sind
     *  dort definiert, Unpassendes wird vom Charset zu '?' ). */
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

    /** Ersetzt nur Zeichen, die CP1252 wirklich nicht kennt. */
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
