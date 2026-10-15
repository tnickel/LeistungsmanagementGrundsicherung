import de.allgemein.lmg.calc.*;
import de.allgemein.lmg.model.*;
import de.allgemein.lmg.service.BescheidGenerator;
import de.allgemein.lmg.web.Json;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** Isolierte Reviewprobe: Sollwerte vorher unabhängig festgelegt, keine Datenbank. */
public class FachProbe {
    static BigDecimal e(String v) { return new BigDecimal(v); }
    static final BigDecimal Z = e("0.00");
    static SozialFall f(long id, int rbs, boolean pregnant, int u7, int u16, int minors,
                         boolean decentralized, String rent, String heating, String earning,
                         String deductions, String delivery) {
        return new SozialFall(id, 7, rbs, false, pregnant, u7, u16, minors, decentralized,
                e(rent), Z, e(heating), Z, e(earning), e(deductions), Z, Z, Z, delivery, 1);
    }
    static Map<String,Object> check(String id, String expected, BigDecimal actual) {
        return Map.of("id", id, "soll", expected, "ist", actual.toPlainString(),
                "gleich", actual.compareTo(e(expected)) == 0);
    }
    public static void main(String[] args) throws Exception {
        List<Object> out = new ArrayList<>();
        var four = f(1,1,false,1,2,4,false,"0.00","0.00","0.00","0.00",null);
        var five = f(2,1,false,0,3,5,false,"0.00","0.00","0.00","0.00",null);
        // 563 * 0,48 = 270,24; 563 * 0,60 = 337,80 (WD 6-046/24, S. 11).
        out.add(check("AE4_1U7_2U16", "270.24", Leistungsberechnung.berechneFall(four).mehrbedarfAlleinerziehend()));
        out.add(check("AE5_3U16", "337.80", Leistungsberechnung.berechneFall(five).mehrbedarfAlleinerziehend()));
        var central = f(3,1,false,0,0,0,false,"400.00","100.00","0.00","0.00",null);
        // 563 + 400 + 100 = 1063,00; zentrale Warmwasserkosten gehoeren zu den Heizbedarfen.
        out.add(check("ZENTRAL_WW_20PROZENT", "1063.00", Leistungsberechnung.berechne(7,List.of(central),
                new Leistungsberechnung.KduParameter(e("0.20"),Z),Z,Satzstand.REFERENZ_2026).zahlbetrag()));
        var pregnant = f(4,1,true,0,0,0,false,"0.00","0.00","0.00","0.00","2026-10");
        var plan = Bewilligungsplan.erstelle(7,List.of(pregnant),Leistungsberechnung.KduParameter.AUS,Z,
                "2026-10","2026-11", m->Satzstand.REFERENZ_2026);
        out.add(check("ENTBINDUNGSMONAT", "658.71",plan.monate().get(0).ergebnis().zahlbetrag()));
        out.add(check("FOLGEMONAT", "563.00",plan.monate().get(1).ergebnis().zahlbetrag()));
        var capitalCase = f(5,1,false,0,0,0,false,"300.00","0.00","0.00","0.00",null);
        var capitalPlan = Bewilligungsplan.erstelle(7,List.of(capitalCase),Leistungsberechnung.KduParameter.AUS,
                e("10500.00"),"2026-10","2026-11",m->Satzstand.REFERENZ_2026);
        // Reine Modellrechnung: Verbrauch vorausgesetzt, keine Behauptung eines realen Verbrauchs.
        out.add(check("VERMOEGEN_MODELL_M1", "363.00",capitalPlan.monate().get(0).ergebnis().zahlbetrag()));
        out.add(check("VERMOEGEN_MODELL_M2", "863.00",capitalPlan.monate().get(1).ergebnis().zahlbetrag()));
        for (String earning : List.of("938.32","938.33","938.34")) {
            var earned = f(6,1,false,0,0,0,false,"300.00","0.00",earning,"200.00",null);
            out.add(check("FREIBETRAG_"+earning,"281.50",Leistungsberechnung.berechneFall(earned).freibetragErwerb()));
        }
        var a = f(11,2,false,0,0,0,false,"600.00","0.00","0.00","0.00",null);
        var b = f(12,2,false,0,0,0,false,"400.00","0.00","0.00","0.00",null);
        var cap = new Leistungsberechnung.KduParameter(Z,e("700.00"));
        var ab = Leistungsberechnung.berechne(7,List.of(a,b),cap,Z,Satzstand.REFERENZ_2026);
        var ba = Leistungsberechnung.berechne(7,List.of(b,a),cap,Z,Satzstand.REFERENZ_2026);
        out.add(check("KDU_REIHENFOLGE_AB_SUMME","1712.00",ab.zahlbetrag()));
        out.add(check("KDU_REIHENFOLGE_BA_SUMME","1712.00",ba.zahlbetrag()));
        out.add(Map.of("id","KDU_REIHENFOLGE_PERSON11","AB",ab.faelle().get(0).kdu().toPlainString(),
                "BA",ba.faelle().get(1).kdu().toPlainString(),"urteil","Gesamtsumme invariant; Personenzuordnung nicht invariant"));
        String snapshot = BerechnungsSnapshot.schreibe(capitalPlan);
        var decision = new Bescheid(1,7,"B-REVIEW",LocalDate.of(2026,10,15),"2026-10","2026-11",
                e("363.00"),"ERTEILT","sb-review",Instant.parse("2026-10-15T12:00:00Z"),snapshot);
        var oldBg = new Bedarfsgemeinschaft(7,"BG-REVIEW","Testperson",true,e("10000.00"),null,1,
                "DE89370400440532013000","COBADEFFXXX","Testperson",e("10500.00"));
        var newBg = new Bedarfsgemeinschaft(7,"BG-REVIEW","Testperson",true,e("10000.00"),null,2,
                "DE12500105170648489890","INGDDEFFXXX","Neues Konto",e("10500.00"));
        var stored = BerechnungsSnapshot.lese(snapshot);
        String oldHtml = BescheidGenerator.generiereHtml(oldBg,stored.faelle(),stored.ergebnis(),decision);
        String newHtml = BescheidGenerator.generiereHtml(newBg,stored.faelle(),stored.ergebnis(),decision);
        out.add(Map.of("id","NACHDRUCK_BANKDATEN","gleicherBescheidUndSnapshot",true,
                "htmlUnveraendert",oldHtml.equals(newHtml),"neuerKontoinhaberImNachdruck",newHtml.contains("Neues Konto")));
        out.add(Map.of("id","WIDERSPRUCH_FORMNORM","verweistAuf65aSGG",oldHtml.contains("elektronische Form § 65a SGG"),
                "verweistAuf36aSGBI",oldHtml.contains("36a")));
        System.out.println(Json.write(Map.of("runtime",System.getProperty("java.version"),
                "compilerTarget",21,"datenbank",false,"proben",out)));
    }
}
