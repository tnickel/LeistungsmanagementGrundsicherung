package de.allgemein.testsweet;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class LlmCalculationReferenceTest {
    @Test void goldValuesCoverPartnersIncomeAssetsAndNegativeEntitlement() {
        Map<?,?> block = block("block: Paar\nzahlungsmonat = 2026-10\nvermoegen = 21000.00\nschonvermoegen = 20000.00\nbudget_start = 1000.00\n"
                + input(1,2,false,false,"-",0,0,0,false,"410.00","105.00","145.00","0.00","1234.56","100.00")
                + input(2,2,false,false,"-",0,0,0,false,"0.00","0.00","0.00","200.00","0.00","0.00"));
        assertEquals("281.50", person(block,0).get("freibetrag_erwerb"));
        assertEquals("853.06", person(block,0).get("anrechenbar"));
        assertEquals("312.94", person(block,0).get("zahlbetrag"));
        assertEquals("0.00", person(block,1).get("freibetrag_erwerb"));
        assertEquals("306.00", person(block,1).get("zahlbetrag"));
        Map<?,?> bg = (Map<?,?>)block.get("household");
        assertEquals("1672.00", bg.get("bg_gesamtbedarf"));
        assertEquals("1053.06", bg.get("bg_anrechenbar"));
        assertEquals("1000.00", bg.get("bg_verwertbares_vermoegen"));
        assertEquals("-381.06", bg.get("bg_zahlbetrag"));
        assertEquals("1381.06", bg.get("budget_rest_hypothetisch"));
    }

    @Test void goldValuesCoverDeliveryMonthWarmWaterAndAllowanceRounding() {
        String in = input(1,1,true,true,"2026-10",1,1,1,true,"300.00","50.00","50.00","100.00","99.99","10.00");
        Map<?,?> current = person(block("block: Entbindung\nzahlungsmonat = 2026-10\nvermoegen = 0.00\nschonvermoegen = 10000.00\nbudget_start = -\n" + in),0);
        assertEquals("95.71", current.get("mehrbedarf_g")); assertEquals("95.71", current.get("mehrbedarf_schwanger"));
        assertEquals("202.68", current.get("mehrbedarf_alleinerziehend")); assertEquals("12.95", current.get("mehrbedarf_warmwasser"));
        assertEquals("1370.05", current.get("gesamtbedarf")); assertEquals("30.00", current.get("freibetrag_erwerb"));
        assertEquals("159.99", current.get("anrechenbar")); assertEquals("1210.06", current.get("zahlbetrag"));
        Map<?,?> next = person(block("block: Folgemonat\nzahlungsmonat = 2026-11\nvermoegen = 0.00\nschonvermoegen = 10000.00\nbudget_start = -\n" + in),0);
        assertEquals("0.00", next.get("mehrbedarf_schwanger")); assertEquals("1114.35", next.get("zahlbetrag"));
    }

    @Test void everyBlockPersonAndMissingInputIsExplicit() {
        String text = "block: A\nvermoegen = 0.00\nschonvermoegen = 10000.00\nbudget_start = -\n"
                + input(1,1,false,true,"2026-10",0,0,0,false,"0.00","0.00","0.00","0.00","0.00","0.00")
                + "block: B\nvermoegen = 0.00\nschonvermoegen = 10000.00\nbudget_start = -\n"
                + input(1,3,false,false,"-",0,0,0,false,"0.00","0.00","0.00","0.00","0.00","0.00");
        Map<String,Object> requirement = LlmCalculationReference.requirement("X",text);
        List<?> blocks = (List<?>)requirement.get("calculations");
        assertEquals(2,blocks.size()); assertEquals("B1",((Map<?,?>)blocks.get(0)).get("blockId")); assertEquals("B2",((Map<?,?>)blocks.get(1)).get("blockId"));
        assertTrue(requirement.get("missingInputs").toString().contains("Zahlungsmonat für Schwangerschaft fehlt"));
        assertFalse(LlmCalculationReference.coverage(requirement).toString().contains("563.00")); // Kein Sollwert-Leak im Modellauftrag.
    }

    @Test void halfUpAndPureMinorBaseHaveHandComputedValues() {
        Map<?,?> b = block("block: Zuschlagsdeckel\nzahlungsmonat = 2026-10\nvermoegen = 1000.00\nschonvermoegen = 10000.00\nbudget_start = -\n"
                + input(1,6,true,true,"-",0,0,8,true,"0.00","0.00","0.00","0.00","0.00","0.00"));
        Map<?,?> p = person(b,0);
        assertEquals("60.69",p.get("mehrbedarf_g")); assertEquals("337.80",p.get("mehrbedarf_alleinerziehend"));
        assertEquals("2.86",p.get("mehrbedarf_warmwasser")); assertEquals("716.86",p.get("gesamtbedarf"));
        assertEquals("10000.00",((Map<?,?>)b.get("household")).get("schonvermoegen"));
        assertEquals("0.00",((Map<?,?>)b.get("household")).get("bg_verwertbares_vermoegen"));
        assertEquals("716.86",((Map<?,?>)b.get("household")).get("bg_zahlbetrag"));
    }
    @Test void pureMinorHouseholdUsesOneBaseAmountRegardlessOfChildCount() {
        var b = block("block: Minderjährige\nzahlungsmonat = 2026-10\nvermoegen = 10500.00\nschonvermoegen = 10000.00\nbudget_start = -\n"
                + input(1,4,false,false,"-",0,0,0,false,"0","0","0","0","0","0")
                + input(2,6,false,false,"-",0,0,0,false,"0","0","0","0","0","0"));
        var bg = (Map<?,?>) b.get("household");
        assertEquals("10000.00",bg.get("schonvermoegen")); assertEquals("500.00",bg.get("bg_verwertbares_vermoegen"));
        assertEquals("328.00",bg.get("bg_zahlbetrag"));
    }
    @Test void localHeatingDeductionAndSharedHouseholdLimitHaveHandComputedValues() {
        var b = block("block: KdU\nzahlungsmonat = 2026-10\nvermoegen = 0.00\nschonvermoegen = 20000.00\nbudget_start = -\nkdu_warmwasser_abzug_anteil = 0.075\nkdu_bg_grenze = 600.00\n"
                + input(1,2,false,false,"-",0,0,0,false,"350","50","100","0","0","0")
                + input(2,2,false,false,"-",0,0,0,true,"350","50","100","0","0","0"));
        assertEquals("7.50",person(b,0).get("kdu_warmwasser_abzug"));
        assertEquals("492.50",person(b,0).get("kdu"));
        assertEquals("0.00",person(b,1).get("kdu_warmwasser_abzug"));
        assertEquals("107.50",person(b,1).get("kdu"));
        assertEquals("1623.64",((Map<?,?>)b.get("household")).get("bg_zahlbetrag"));
    }
    private static Map<?,?> block(String text) { return (Map<?,?>)((List<?>)LlmCalculationReference.requirement("GOLD",text).get("calculations")).getFirst(); }
    private static Map<?,?> person(Map<?,?> block,int index) { return (Map<?,?>)((Map<?,?>)((List<?>)block.get("persons")).get(index)).get("values"); }
    private static String input(int person,int rbs,boolean g,boolean pregnant,String delivery,int u7,int u16,int minor,boolean ww,
                                String rent,String operating,String heating,String pension,String employed,String tax) {
        return "p"+person+"_eingaben: rbs="+rbs+", merkzeichen_g="+g+", schwanger="+pregnant+", entbindungsmonat="+delivery
                +", kinder_u7="+u7+", kinder_u16="+u16+", kinder_minderjaehrig="+minor+", warmwasser="+ww
                +", kdu_miete="+rent+", kdu_betrieb="+operating+", kdu_heizung="+heating+", rente="+pension+", erwerb="+employed
                +", abz_steuern="+tax+", abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00\n";
    }
}
