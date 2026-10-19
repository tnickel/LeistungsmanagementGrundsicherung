package de.allgemein.lmg.calc;

import de.allgemein.lmg.model.SozialFall;
import de.allgemein.lmg.web.Json;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Serialisiert das Berechnungsergebnis einer Bescheiderteilung als versioniertes
 * JSON ("Snapshot") und liest es verlustfrei zurück. Der Snapshot wird mit dem
 * Bescheid in BESCHEID.BERECHNUNG gespeichert, damit der Nachdruck unabhängig von
 * späteren Falländerungen stets den Zustand des Erteilungszeitpunkts zeigt
 * (revisionssicherer Nachdruck).
 *
 * <p>Beträge werden als Zeichenketten mit fester Skala geführt, damit die
 * BigDecimal-Skalierung (zwei Nachkommastellen) den Roundtrip exakt überlebt.
 * Neben dem Ergebnis werden je Fall die Eingaben dokumentiert, die der
 * Bescheid-Druck zusätzlich braucht (Regelbedarfsstufe, Rente, Erwerbseinkommen),
 * sowie das bei der Berechnung angesetzte Gesamtvermögen der Bedarfsgemeinschaft.</p>
 */
public final class BerechnungsSnapshot {

    private static final int VERSION = 1;

    private BerechnungsSnapshot() {
    }

    /** Gelesener Snapshot: Ergebnis plus die dafür dokumentierten Eingabedaten. */
    public record Gelesen(
            Leistungsberechnung.Berechnungsergebnis ergebnis,
            List<SozialFall> faelle,
            BigDecimal vermoegen,
            List<Bewilligungsplan.Monat> monate,
            String dokumentHtml) {
        public Gelesen {
            faelle = List.copyOf(faelle);
            monate = List.copyOf(monate);
        }
        public Gelesen(Leistungsberechnung.Berechnungsergebnis ergebnis,
                       List<SozialFall> faelle, BigDecimal vermoegen) {
            this(ergebnis, faelle, vermoegen, List.of(), null);
        }
        public Gelesen(Leistungsberechnung.Berechnungsergebnis ergebnis,
                       List<SozialFall> faelle, BigDecimal vermoegen, List<Bewilligungsplan.Monat> monate) {
            this(ergebnis, faelle, vermoegen, monate, null);
        }
    }

    public static String schreibe(
            Leistungsberechnung.Berechnungsergebnis ergebnis,
            List<SozialFall> faelle,
            BigDecimal vermoegen) {
        return Json.write(alsObjekt(ergebnis, faelle, vermoegen));
    }

    /** Version 2 enthält jeden bewilligten Monat einschließlich seiner Berechnungsbasis. */
    public static String schreibe(Bewilligungsplan.Plan plan) {
        Bewilligungsplan.Monat erster = plan.ersterMonat();
        Map<String, Object> wurzel = alsObjekt(erster.ergebnis(), erster.faelle(), erster.vermoegen());
        wurzel.put("version", 2);
        List<Object> monate = new ArrayList<>();
        for (Bewilligungsplan.Monat monat : plan.monate()) {
            Map<String, Object> eintrag = alsObjekt(monat.ergebnis(), monat.faelle(), monat.vermoegen());
            eintrag.put("monat", monat.monat());
            monate.add(eintrag);
        }
        wurzel.put("monate", monate);
        return Json.write(wurzel);
    }

    /** Version 3 bewahrt zusätzlich das vollständig erzeugte Erteilungsdokument. */
    @SuppressWarnings("unchecked")
    public static String schreibe(Bewilligungsplan.Plan plan, String dokumentHtml) {
        if (dokumentHtml == null || dokumentHtml.isBlank()) {
            throw new IllegalArgumentException("Erteilungsdokument fehlt");
        }
        Map<String, Object> wurzel = new LinkedHashMap<>((Map<String, Object>) Json.parse(schreibe(plan)));
        wurzel.put("version", 3);
        wurzel.put("dokumentHtml", dokumentHtml);
        return Json.write(wurzel);
    }

    private static Map<String, Object> alsObjekt(
            Leistungsberechnung.Berechnungsergebnis ergebnis,
            List<SozialFall> faelle, BigDecimal vermoegen) {
        List<Object> fallEintraege = new ArrayList<>();
        for (Leistungsberechnung.FallBerechnung fb : ergebnis.faelle()) {
            SozialFall fall = faelle.stream()
                    .filter(f -> f.id() == fb.fallId()).findFirst().orElse(null);
            Map<String, Object> e = new LinkedHashMap<>();
            if (fall == null) throw new IllegalArgumentException("Eingabefall zum Berechnungsergebnis fehlt");
            Map<String, Object> eingaben = new LinkedHashMap<>();
            eingaben.put("merkzeichenG", fall.merkzeichenG());
            eingaben.put("schwanger", fall.schwanger());
            eingaben.put("anzKinderUnter7", fall.anzKinderUnter7());
            eingaben.put("anzKinderUnter16", fall.anzKinderUnter16());
            eingaben.put("anzKinderMinderjaehrig", fall.anzKinderMinderjaehrig());
            eingaben.put("warmwasser", fall.warmwasser());
            eingaben.put("kduMiete", betragText(fall.kduMiete()));
            eingaben.put("kduBetriebskosten", betragText(fall.kduBetriebskosten()));
            eingaben.put("kduHeizung", betragText(fall.kduHeizung()));
            eingaben.put("abzSteuern", betragText(fall.abzSteuern()));
            eingaben.put("abzSozialversicherung", betragText(fall.abzSozialversicherung()));
            eingaben.put("abzVersicherungen", betragText(fall.abzVersicherungen()));
            eingaben.put("abzErwerbsaufwendungen", betragText(fall.abzErwerbsaufwendungen()));
            eingaben.put("entbindungsmonat", fall.entbindungsmonat());
            eingaben.put("version", fall.version());
            e.put("eingaben", eingaben);
            e.put("fallId", fb.fallId());
            e.put("regelbedarfsstufe", fall != null ? fall.regelbedarfsstufe() : 0);
            e.put("einkommenRente", betragText(fall != null ? fall.einkommenRente() : BigDecimal.ZERO));
            e.put("einkommenErwerb", betragText(fall != null ? fall.einkommenErwerb() : BigDecimal.ZERO));
            e.put("regelbedarf", betragText(fb.regelbedarf()));
            e.put("mehrbedarfG", betragText(fb.mehrbedarfG()));
            e.put("mehrbedarfSchwangere", betragText(fb.mehrbedarfSchwangere()));
            e.put("mehrbedarfAlleinerziehend", betragText(fb.mehrbedarfAlleinerziehend()));
            e.put("mehrbedarfWarmwasser", betragText(fb.mehrbedarfWarmwasser()));
            e.put("mehrbedarf", betragText(fb.mehrbedarf()));
            e.put("kduWarmwasserAbzug", betragText(fb.kduWarmwasserAbzug()));
            e.put("kduMiete", betragText(fb.kduMiete()));
            e.put("kduBetriebskosten", betragText(fb.kduBetriebskosten()));
            e.put("kduHeizung", betragText(fb.kduHeizung()));
            e.put("kdu", betragText(fb.kdu()));
            e.put("kduUnangemessen", fb.kduUnangemessen());
            e.put("gesamtbedarf", betragText(fb.gesamtbedarf()));
            e.put("freibetragErwerb", betragText(fb.freibetragErwerb()));
            e.put("absetzbetaetrag", betragText(fb.absetzbetaetrag()));
            e.put("anrechenbaresEinkommen", betragText(fb.anrechenbaresEinkommen()));
            e.put("zahlbetrag", betragText(fb.zahlbetrag()));
            fallEintraege.add(e);
        }
        Map<String, Object> wurzel = new LinkedHashMap<>();
        wurzel.put("version", VERSION);
        wurzel.put("bgId", ergebnis.bgId());
        wurzel.put("vermoegen", betragText(vermoegen));
        wurzel.put("gesamtbedarf", betragText(ergebnis.gesamtbedarf()));
        wurzel.put("anrechenbaresEinkommen", betragText(ergebnis.anrechenbaresEinkommen()));
        wurzel.put("verwertbaresVermoegen", betragText(ergebnis.verwertbaresVermoegen()));
        wurzel.put("zahlbetrag", betragText(ergebnis.zahlbetrag()));
        wurzel.put("faelle", fallEintraege);
        return wurzel;
    }

    @SuppressWarnings("unchecked")
    public static Gelesen lese(String json) {
        Object geparst = Json.parse(json);
        if (!(geparst instanceof Map<?, ?> wurzelObj)) {
            throw new IllegalArgumentException("Berechnungs-Snapshot ist kein JSON-Objekt");
        }
        Map<String, Object> wurzel = (Map<String, Object>) wurzelObj;
        int version = zahlAlsInt(wurzel.get("version"), "version");
        if (version != VERSION && version != 2 && version != 3) {
            throw new IllegalArgumentException("Berechnungs-Snapshot hat Version " + version
                    + ", lesbar sind Version 1, 2 und 3");
        }
        if (version >= 2) {
            String dokument = null;
            if (version == 3) {
                if (!(wurzel.get("dokumentHtml") instanceof String html) || html.isBlank()) {
                    throw new IllegalArgumentException("Berechnungs-Snapshot enthält kein Erteilungsdokument");
                }
                dokument = html;
            }
            if (!(wurzel.get("monate") instanceof List<?> eintraege) || eintraege.isEmpty()) {
                throw new IllegalArgumentException("Berechnungs-Snapshot enthält keinen Monatsplan");
            }
            List<Bewilligungsplan.Monat> monate = new ArrayList<>();
            for (Object eintrag : eintraege) {
                if (!(eintrag instanceof Map<?, ?> monatObj)
                        || !(monatObj.get("monat") instanceof String monat)) {
                    throw new IllegalArgumentException("Berechnungs-Snapshot enthält ungültigen Monat");
                }
                java.time.YearMonth.parse(monat);
                if (zahlAlsInt(monatObj.get("version"), "version") != VERSION) {
                    throw new IllegalArgumentException("Ungültige Version der Monatsberechnung");
                }
                Gelesen gelesen = lese(Json.write(monatObj));
                monate.add(new Bewilligungsplan.Monat(monat, gelesen.ergebnis(), gelesen.faelle(), gelesen.vermoegen()));
            }
            Bewilligungsplan.Monat erster = monate.get(0);
            return new Gelesen(erster.ergebnis(), erster.faelle(), erster.vermoegen(), monate, dokument);
        }
        long bgId = zahlAlsLong(wurzel.get("bgId"), "bgId");
        List<Leistungsberechnung.FallBerechnung> fallBerechnungen = new ArrayList<>();
        List<SozialFall> faelle = new ArrayList<>();
        if (!(wurzel.get("faelle") instanceof List<?> eintraege)) {
            throw new IllegalArgumentException("Berechnungs-Snapshot enthält keine Fallliste");
        }
        for (Object eintrag : eintraege) {
            if (!(eintrag instanceof Map<?, ?>)) {
                throw new IllegalArgumentException("Berechnungs-Snapshot: Falleintrag ist kein Objekt");
            }
            Map<String, Object> e = (Map<String, Object>) eintrag;
            long fallId = zahlAlsLong(e.get("fallId"), "fallId");
            BigDecimal nullBetrag = BigDecimal.ZERO.setScale(2);
            fallBerechnungen.add(new Leistungsberechnung.FallBerechnung(
                    fallId,
                    betrag(e.get("regelbedarf")),
                    betrag(e.get("mehrbedarfG")),
                    betrag(e.get("mehrbedarfSchwangere")),
                    betrag(e.get("mehrbedarfAlleinerziehend")),
                    betrag(e.get("mehrbedarfWarmwasser")),
                    betrag(e.get("mehrbedarf")),
                    betrag(e.get("kduWarmwasserAbzug")),
                    betrag(e.get("kduMiete")),
                    betrag(e.get("kduBetriebskosten")),
                    betrag(e.get("kduHeizung")),
                    betrag(e.get("kdu")),
                    Boolean.TRUE.equals(e.get("kduUnangemessen")),
                    betrag(e.get("gesamtbedarf")),
                    betrag(e.get("freibetragErwerb")),
                    betrag(e.get("absetzbetaetrag")),
                    betrag(e.get("anrechenbaresEinkommen")),
                    betrag(e.get("zahlbetrag"))));
            if (e.get("eingaben") instanceof Map<?, ?> input) {
                Object entbindung = input.get("entbindungsmonat");
                if (entbindung != null && !(entbindung instanceof String)) {
                    throw new IllegalArgumentException("Ungültiger Entbindungsmonat im Snapshot");
                }
                if (entbindung instanceof String monat) java.time.YearMonth.parse(monat);
                faelle.add(new SozialFall(fallId, bgId,
                        zahlAlsInt(e.get("regelbedarfsstufe"), "regelbedarfsstufe"),
                        bool(input, "merkzeichenG"), bool(input, "schwanger"),
                        zahlAlsInt(input.get("anzKinderUnter7"), "anzKinderUnter7"),
                        zahlAlsInt(input.get("anzKinderUnter16"), "anzKinderUnter16"),
                        zahlAlsInt(input.get("anzKinderMinderjaehrig"), "anzKinderMinderjaehrig"),
                        bool(input, "warmwasser"), betrag(input.get("kduMiete")),
                        betrag(input.get("kduBetriebskosten")), betrag(input.get("kduHeizung")),
                        betrag(e.get("einkommenRente")), betrag(e.get("einkommenErwerb")),
                        betrag(input.get("abzSteuern")), betrag(input.get("abzSozialversicherung")),
                        betrag(input.get("abzVersicherungen")), betrag(input.get("abzErwerbsaufwendungen")),
                        (String) entbindung, zahlAlsLong(input.get("version"), "version")));
            } else if (!e.containsKey("eingaben")) {
                // Alt-Snapshots dokumentierten nur Stufe und Einkommen; fehlende Merkmale
                // sind nicht rekonstruierbar. Gespeichertes HTML und Ergebnis bleiben gültig.
                faelle.add(new SozialFall(fallId, bgId, zahlAlsInt(e.get("regelbedarfsstufe"), "regelbedarfsstufe"),
                        false, false, 0, 0, 0, false, nullBetrag, nullBetrag, nullBetrag,
                        betrag(e.get("einkommenRente")), betrag(e.get("einkommenErwerb")),
                        nullBetrag, nullBetrag, nullBetrag, nullBetrag, null, 1L));
            } else {
                throw new IllegalArgumentException("Snapshot-Eingaben sind kein Objekt");
            }
        }
        Leistungsberechnung.Berechnungsergebnis ergebnis = new Leistungsberechnung.Berechnungsergebnis(
                bgId, List.copyOf(fallBerechnungen),
                betrag(wurzel.get("gesamtbedarf")),
                betrag(wurzel.get("anrechenbaresEinkommen")),
                betrag(wurzel.get("verwertbaresVermoegen")),
                betrag(wurzel.get("zahlbetrag")));
        return new Gelesen(ergebnis, List.copyOf(faelle), betrag(wurzel.get("vermoegen")));
    }

    private static String betragText(BigDecimal betrag) {
        return betrag.toPlainString();
    }

    private static boolean bool(Map<?, ?> map, String key) {
        if (map.get(key) instanceof Boolean value) return value;
        throw new IllegalArgumentException("Snapshot-Eingabe '" + key + "' ist kein Wahrheitswert");
    }

    private static BigDecimal betrag(Object wert) {
        if (wert instanceof BigDecimal b) {
            return b;
        }
        if (wert instanceof Number n) {
            return new BigDecimal(n.toString());
        }
        if (wert instanceof String s) {
            return new BigDecimal(s);
        }
        throw new IllegalArgumentException("Berechnungs-Snapshot: Betrag fehlt oder unlesbar: " + wert);
    }

    private static long zahlAlsLong(Object wert, String feld) {
        if (wert instanceof Number n) {
            try {
                return new BigDecimal(n.toString()).longValueExact();
            } catch (ArithmeticException | NumberFormatException e) {
                throw new IllegalArgumentException("Berechnungs-Snapshot: Feld '" + feld + "' ist keine exakte Ganzzahl", e);
            }
        }
        throw new IllegalArgumentException("Berechnungs-Snapshot: Feld '" + feld + "' fehlt oder unlesbar: " + wert);
    }

    private static int zahlAlsInt(Object wert, String feld) {
        try {
            return Math.toIntExact(zahlAlsLong(wert, feld));
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("Berechnungs-Snapshot: Feld '" + feld + "' überschreitet den Wertebereich", e);
        }
    }
}
