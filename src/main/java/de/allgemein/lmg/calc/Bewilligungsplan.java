package de.allgemein.lmg.calc;

import de.allgemein.lmg.model.SozialFall;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Kurz gesagt: Das ist der Bewilligungs-Zeitraum aufgeteilt in Monate.
 * Eine Bewilligung kann mehrere Monate dauern — diese Klasse rechnet für jeden
 * Monat einzeln: Wurden die Sätze erhöht? Ist der Schwangerschafts-Mehrbedarf
 * ausgelaufen? So entsteht ein Plan mit einem Betrag pro Monat.
 *
 * Monatsbezogene Bewilligung mit bekannten Satzwechseln und Bedarfsänderungen.
 */
public final class Bewilligungsplan {
    private Bewilligungsplan() { }

    @FunctionalInterface
    public interface SatzstandResolver {
        Satzstand finde(String monat) throws SQLException;
    }

    public record Monat(String monat, Leistungsberechnung.Berechnungsergebnis ergebnis,
                        List<SozialFall> faelle, BigDecimal vermoegen) {
        public Monat { faelle = List.copyOf(faelle); }
    }

    public record Plan(List<Monat> monate) {
        public Plan {
            monate = List.copyOf(monate);
            if (monate.isEmpty()) throw new IllegalArgumentException("Bewilligungsplan ist leer");
        }
        public Monat ersterMonat() { return monate.get(0); }
    }

    /** Verändert keine Falldaten; nur der bereits beendete Mehrbedarf entfällt. */
    public static List<SozialFall> faelleFuerMonat(List<SozialFall> faelle, String monat) {
        YearMonth bezug = YearMonth.parse(monat);
        return faelle.stream().map(f -> {
            if (!f.schwanger() || f.entbindungsmonat() == null
                    || !bezug.isAfter(YearMonth.parse(f.entbindungsmonat()))) return f;
            return new SozialFall(f.id(), f.bgId(), f.regelbedarfsstufe(), f.merkzeichenG(), false,
                    f.anzKinderUnter7(), f.anzKinderUnter16(), f.anzKinderMinderjaehrig(), f.warmwasser(),
                    f.kduMiete(), f.kduBetriebskosten(), f.kduHeizung(), f.einkommenRente(), f.einkommenErwerb(),
                    f.abzSteuern(), f.abzSozialversicherung(), f.abzVersicherungen(), f.abzErwerbsaufwendungen(),
                    f.entbindungsmonat(), f.version());
        }).toList();
    }

    /** Simuliert den Vermögenseinsatz, ohne den gespeicherten Vermögensbestand zu ändern. */
    public static Plan erstelle(long bgId, List<SozialFall> faelle,
                               Leistungsberechnung.KduParameter kdu, BigDecimal vermoegen,
                               String von, String bis, SatzstandResolver saetze) throws SQLException {
        YearMonth start = YearMonth.parse(von), ende = YearMonth.parse(bis);
        long anzahl = ChronoUnit.MONTHS.between(start, ende) + 1;
        if (anzahl < 1 || anzahl > 12) {
            throw new IllegalArgumentException("Bewilligungszeitraum muss ein bis zwölf Monate umfassen");
        }
        List<Monat> monate = new ArrayList<>();
        BigDecimal bestand = vermoegen;
        for (int i = 0; i < anzahl; i++) {
            String monat = start.plusMonths(i).toString();
            List<SozialFall> monatsFaelle = faelleFuerMonat(faelle, monat);
            var berechnung = Leistungsberechnung.berechne(bgId, monatsFaelle, kdu, bestand, saetze.finde(monat));
            if (berechnung.zahlbetrag().signum() < 0) {
                berechnung = new Leistungsberechnung.Berechnungsergebnis(bgId, berechnung.faelle(),
                        berechnung.gesamtbedarf(), berechnung.anrechenbaresEinkommen(),
                        berechnung.verwertbaresVermoegen(), BigDecimal.ZERO.setScale(2));
            }
            monate.add(new Monat(monat, berechnung, monatsFaelle, bestand));
            if (berechnung.zahlbetrag().signum() > 0) {
                bestand = bestand.subtract(berechnung.verwertbaresVermoegen());
            }
        }
        return new Plan(monate);
    }
}