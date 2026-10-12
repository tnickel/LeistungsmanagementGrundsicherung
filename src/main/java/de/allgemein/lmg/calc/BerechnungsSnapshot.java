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
            BigDecimal vermoegen) {
    }

    public static String schreibe(
            Leistungsberechnung.Berechnungsergebnis ergebnis,
            List<SozialFall> faelle,
            BigDecimal vermoegen) {
        List<Object> fallEintraege = new ArrayList<>();
        for (Leistungsberechnung.FallBerechnung fb : ergebnis.faelle()) {
            SozialFall fall = faelle.stream()
                    .filter(f -> f.id() == fb.fallId()).findFirst().orElse(null);
            Map<String, Object> e = new LinkedHashMap<>();
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
        return Json.write(wurzel);
    }

    @SuppressWarnings("unchecked")
    public static Gelesen lese(String json) {
        Object geparst = Json.parse(json);
        if (!(geparst instanceof Map<?, ?> wurzelObj)) {
            throw new IllegalArgumentException("Berechnungs-Snapshot ist kein JSON-Objekt");
        }
        Map<String, Object> wurzel = (Map<String, Object>) wurzelObj;
        int version = zahlAlsInt(wurzel.get("version"), "version");
        if (version != VERSION) {
            throw new IllegalArgumentException("Berechnungs-Snapshot hat Version " + version
                    + ", lesbar ist nur Version " + VERSION);
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
            // Minimaler SozialFall: nur die Felder, die der Bescheid-Druck aus der
            // Fallliste entnimmt (Stufe für die Zeilen, Rente/Erwerb für die Begründung)
            faelle.add(new SozialFall(fallId, bgId, zahlAlsInt(e.get("regelbedarfsstufe"), "regelbedarfsstufe"),
                    false, false, 0, 0, 0, false,
                    nullBetrag, nullBetrag, nullBetrag,
                    betrag(e.get("einkommenRente")), betrag(e.get("einkommenErwerb")),
                    nullBetrag, nullBetrag, nullBetrag, nullBetrag, null, 1L));
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
            return n.longValue();
        }
        throw new IllegalArgumentException("Berechnungs-Snapshot: Feld '" + feld + "' fehlt oder unlesbar: " + wert);
    }

    private static int zahlAlsInt(Object wert, String feld) {
        return (int) zahlAlsLong(wert, feld);
    }
}
