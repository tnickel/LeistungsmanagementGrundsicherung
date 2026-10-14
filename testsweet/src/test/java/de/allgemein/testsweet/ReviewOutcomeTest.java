package de.allgemein.testsweet;

import de.allgemein.lmg.web.Json;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ReviewOutcomeTest {
    @TempDir Path temp;
    private Simulator successful() throws Exception {
        Simulator sim=new Simulator();Auftrag order=new Auftrag(1,"TEST","SWEET-RESULT","Test","",List.of(),List.of());order.status=Auftrag.Status.VERIFIZIERT;
        set(sim,"auftraege",List.of(order));set(sim,"phase",Simulator.Phase.FERTIG);set(sim,"laufStart",LocalDateTime.now());return sim;
    }
    private static void set(Object target,String field,Object value) throws Exception {var f=target.getClass().getDeclaredField(field);f.setAccessible(true);f.set(target,value);}
    private Map<String,Object> evidence(String state){return new LinkedHashMap<>(Map.of("state",state,"caseVerdict",state,"totalCases",1,"completedCases",1,
            "transportVerified",true,"calculationVerified",true,"reviewCoverageVerified",true,"transportChecks",List.of(Map.of("responseValidated",true,"documents",List.of(Map.of("name","data"))))));}
    @Test void sqlSuccessAloneIsNoOverallPass() throws Exception {assertEquals("UNVERIFIED",ReviewOutcome.evaluate(successful(),Map.of()).state());}
    @Test void knownFindingSurvivesLaterTechnicalErrorAndAppearsInReport() throws Exception {
        Map<String,Object> e=evidence("ERROR");e.put("caseVerdict","FAIL");e.put("hasFailures",true);
        e.put("cases",List.of(Map.of("caseId","POSITIV:TF1","verdict","FAIL","findings",List.of("Betrag 99 statt 100"),"reasoning","100 - 0 = 100")));
        Simulator sim=successful();assertEquals("FAIL",ReviewOutcome.evaluate(sim,e).state());
        Path md=temp.resolve("report.md");MdBericht.schreibe(md,sim,e);String content=Files.readString(md);
        assertTrue(content.contains("gesamtergebnis = FAIL"));assertTrue(content.contains("lauf_erfolgreich = false"));
        assertTrue(content.contains("automatik_bestanden = true"));assertTrue(content.contains("Betrag 99 statt 100"));
        assertTrue(content.contains("POSITIV:TF1"));assertTrue(content.contains("100 - 0 = 100"));
        SweetPdf pdf=new SweetPdf();var cover=Testdokumentation.class.getDeclaredMethod("deckblatt",SweetPdf.class,Simulator.class,Map.class);cover.setAccessible(true);cover.invoke(null,pdf,sim,e);
        String entries=pdf.eintraegeKopie().toString();assertTrue(entries.contains("GESAMTERGEBNIS: NICHT BESTANDEN"));
        assertFalse(entries.contains("GESAMTERGEBNIS: BESTANDEN"));
    }
    @Test void missingCoverageCannotProduceGreenOverall() throws Exception {
        Map<String,Object> e=evidence("PASS");assertTrue(ReviewOutcome.evaluate(successful(),e).passed());
        e.remove("reviewCoverageVerified");assertFalse(ReviewOutcome.evaluate(successful(),e).passed());
        e.put("reviewCoverageVerified",true);e.put("completedCases",0);assertFalse(ReviewOutcome.evaluate(successful(),e).passed());
    }
    @Test void technicalIncompleteWithNoKnownFindingNeverBecomesPass() throws Exception {assertEquals("UNVERIFIED",ReviewOutcome.evaluate(successful(),evidence("ERROR")).state());}
    @Test void invalidBuildDoesNotClaimThatCompletedSqlChecksWereAborted() throws Exception {
        Simulator sim=successful();
        var field=Simulator.class.getDeclaredField("nachweisFehler");field.setAccessible(true);
        @SuppressWarnings("unchecked") List<String> errors=(List<String>)field.get(sim);
        errors.add("Build-/Quellstand nicht nachgewiesen");
        ReviewOutcome outcome=ReviewOutcome.evaluate(sim,evidence("ERROR"));
        assertEquals("UNVERIFIED",outcome.state());
        assertTrue(outcome.errors().stream().anyMatch(s->s.contains("wegen fehlender oder ungültiger Nachweise")));
        assertFalse(outcome.errors().stream().anyMatch(s->s.contains("unvollständig oder abgebrochen")));
    }
}
