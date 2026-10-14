package de.allgemein.testsweet;

import java.lang.reflect.Proxy;
import java.nio.file.*;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ReviewEvidenceProbe {
    static void set(Object target, String name, Object value) throws Exception {
        var f=target.getClass().getDeclaredField(name); f.setAccessible(true); f.set(target,value);
    }
    public static void main(String[] args) throws Exception {
        Path base=Path.of(args[0]); Files.createDirectories(base);
        Path dir=Files.createTempDirectory(base,"probe-");
        var sim=new Simulator();
        var order=new Auftrag(1,"TEST","SWEET-REVIEW-PROBE","Test","",List.of(),List.of());
        order.status=Auftrag.Status.VERIFIZIERT;
        set(sim,"auftraege",List.of(order)); set(sim,"phase",Simulator.Phase.FERTIG);
        set(sim,"laufStart",LocalDateTime.now()); set(sim,"laufEnde",LocalDateTime.now());
        Map<String,Object> evidence=Map.of("state","FAIL","reviewId","controlled-review",
            "totalCases",1,"completedCases",1,"transportVerified",true,"transportChecks",List.of(
                Map.of("responseValidated",true,"documents",List.of(Map.of("name","probe.md")))),
            "cases",List.of(Map.of("caseId","TEST:1","verdict","FAIL","findings",List.of("CONTROLLED_FINDING_123"),"reasoning","Fehler erkannt")));
        Path md=dir.resolve("sql-pass-glm-fail.md"); MdBericht.schreibe(md,sim,evidence);
        String text=Files.readString(md);
        System.out.println("GLM_FAIL_MD: lauf_erfolgreich_true="+text.contains("lauf_erfolgreich = true")
            +", fachpruefung_false="+text.contains("fachpruefung_bestanden = false")
            +", finding_present="+text.contains("CONTROLLED_FINDING_123"));
        var cover=Testdokumentation.class.getDeclaredMethod("deckblatt",SweetPdf.class,Simulator.class); cover.setAccessible(true);
        var pdf=new SweetPdf(); cover.invoke(null,pdf,sim);
        System.out.println("GLM_FAIL_PDF_COVER: BESTANDEN="+pdf.eintraegeKopie().stream().map(Object::toString)
            .anyMatch(x->x.contains("GESAMTERGEBNIS: BESTANDEN")));

        AtomicInteger nextCalls=new AtomicInteger();
        ResultSet rs=(ResultSet)Proxy.newProxyInstance(ResultSet.class.getClassLoader(),new Class[]{ResultSet.class},(p,m,a)->switch(m.getName()){
            case "next" -> nextCalls.getAndIncrement()<2; case "getObject" -> "STORNIERT"; case "close" -> null; default -> throw new UnsupportedOperationException(m.getName());});
        PreparedStatement stmt=(PreparedStatement)Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(),new Class[]{PreparedStatement.class},(p,m,a)->switch(m.getName()){
            case "executeQuery" -> rs; case "close","setObject" -> null; default -> throw new UnsupportedOperationException(m.getName());});
        Connection c=(Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class[]{Connection.class},(p,m,a)->switch(m.getName()){
            case "prepareStatement" -> stmt; default -> throw new UnsupportedOperationException(m.getName());});
        var verify=Verifikator.class.getDeclaredMethod("pruefe",Connection.class,Auftrag.class,Erwartung.class); verify.setAccessible(true);
        var checked=(Auftrag.Pruefung)verify.invoke(new Verifikator("","",""),c,order,Erwartung.text("Zahlungsstatus","select ...",x->List.of(),"STORNIERT"));
        System.out.println("MULTIPLE_ROWS: accepted="+checked.ok()+", next_calls="+nextCalls.get());

        String hollowPass=de.allgemein.lmg.web.Json.write(Map.of("caseIds",List.of("TEST:1"),"cases",List.of(
            Map.of("caseId","TEST:1","verdict","PASS","findings",List.of(),"reasoning","Nicht nachgerechnet. Nur Sollwerte uebernommen."))));
        System.out.println("HOLLOW_LLM_PASS: accepted="+!LlmReviewService.validateAnswer(hollowPass,List.of("TEST:1")).isEmpty());

        Path old=dir.resolve("old-report.pdf"); Files.writeString(old,"old green report"); set(sim,"letztesPdf",old.toString());
        try(var log=new SweetLog(dir.resolve("logs"))) {
            set(sim,"log",log); Files.createDirectory(log.runVerzeichnis.resolve("testdokumentation.pdf"));
            var export=Simulator.class.getDeclaredMethod("schreibeTestdokumentation"); export.setAccessible(true); export.invoke(sim);
            System.out.println("REPORT_WRITE_FAILURE: lauf_erfolgreich="+sim.laufErfolgreich()+", old_report_retained="+old.toString().equals(sim.letztesPdf()));
        }
        try(var log=new SweetLog(dir.resolve("log-failure"))) {
            var writer=SweetLog.class.getDeclaredField("simulation"); writer.setAccessible(true);
            ((java.io.BufferedWriter)writer.get(log)).close();
            boolean propagated=false;
            try { log.ereignis("TEST","","kontrollierter Schreibfehler"); log.close(); }
            catch(RuntimeException error) { propagated=true; }
            System.out.println("LOG_WRITE_FAILURE: propagated="+propagated+", lauf_erfolgreich="+sim.laufErfolgreich());
        }
    }
}
