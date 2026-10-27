package de.allgemein.testsweet;

import de.allgemein.lmg.web.Json;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/** Frische JVM: sieben definierte Matrixläufe, GLM für POSITIV/NEGATIV und Zertifikat;
 *  die separaten Kategorien ECHT/RECHNER gehören nicht zu diesem Gesamtumfang. */
final class CertificateSuiteWorker {
    record Job(String mode,Simulator.Kategorie category,int employees,int orders) { }
    static final List<Job> JOBS=List.of(
        new Job("WEB",Simulator.Kategorie.POSITIV,3,SimulatorDefaults.ORDERS_PER_CATEGORY),new Job("WEB",Simulator.Kategorie.NEGATIV,3,SimulatorDefaults.ORDERS_PER_CATEGORY),new Job("WEB",Simulator.Kategorie.LAST,3,30),
        new Job("FACH",Simulator.Kategorie.POSITIV,8,500),new Job("FACH",Simulator.Kategorie.NEGATIV,8,500),new Job("FACH",Simulator.Kategorie.LAST,8,120),new Job("FACH",Simulator.Kategorie.STRESS,4,21));
    static void run(Path directory) throws Exception {
        Path root=BuildEvidence.currentRoot();directory=directory.toAbsolutePath().normalize();
        if(!directory.startsWith(root.resolve("testsweet/logs"))||!directory.getFileName().toString().startsWith("certificate-suite-"))throw new IllegalArgumentException("Ungültiges Zertifikatslaufverzeichnis");
        Map<String,Object> initial=CertificateSuiteState.read(directory.resolve("status.json"));CertificateSuiteState state=new CertificateSuiteState(root,directory,initial);
        try {
            CertificateRuntimeSnapshot.Guard runtimeGuard=CertificateRuntimeSnapshot.workerGuard(root,directory);
            runtimeGuard.check(true);
            if(WebHelfer.expliziteBasisUrl()!=null)throw new IllegalStateException("Zertifikatstests benötigen den frisch gebauten eingebetteten Server. Externes Webziel entfernen und alle Zertifikatstests neu starten.");
            System.setProperty("sweet.requireEmbedded","true");
            state.checkSources();if(!Boolean.TRUE.equals(BuildEvidence.capture(root).get("sourceArtifactsMatch")))throw new IllegalStateException("Frischer Worker besitzt keinen gültigen Build-/Quellnachweis");
            state.update(Map.of("workerPid",ProcessHandle.current().pid()));List<Map<String,Object>> runs=new ArrayList<>();
            for(int n=0;n<JOBS.size();n++){
                Job job=JOBS.get(n);state.checkSources();runtimeGuard.check(true);state.phase(4+n,"SIMULATOR_"+job.mode+"_"+job.category,"Simulator "+job.mode+" · "+job.category);
                if("WEB".equals(job.mode)) {
                    // Eigener lokaler Testport: eine parallel laufende Hauptanwendung
                    // bleibt unberührt. Eine mögliche Bind-Race darf wegen
                    // sweet.requireEmbedded niemals auf ein fremdes Ziel ausweichen.
                    try(var port=new java.net.ServerSocket(0,1,java.net.InetAddress.getLoopbackAddress())) {
                        System.setProperty("lmg.port",Integer.toString(port.getLocalPort()));
                    }
                }
                Simulator simulator=new Simulator();Simulator.LaufKonfiguration config=new Simulator.LaufKonfiguration(job.employees,job.orders,20261014,0,true,job.category);
                var runtimeFailure=new java.util.concurrent.atomic.AtomicReference<Exception>();
                ScheduledExecutorService progress=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"certificate-simulator-progress");t.setDaemon(true);return t;});
                progress.scheduleWithFixedDelay(()->{
                    try {runtimeGuard.check(false);}catch(Exception drift){runtimeFailure.compareAndSet(null,drift);simulator.stoppe();return;}
                    try {state.checkCancelled();long completed=simulator.auftraege().stream().filter(a->a.status!=Auftrag.Status.OFFEN&&a.status!=Auftrag.Status.IN_BEARBEITUNG).count();int total=job.category==Simulator.Kategorie.STRESS?job.orders*2:job.orders;state.update(Map.of("completedItems",completed,"totalItems",total,"phaseFraction",Math.min(.98,(double)completed/total),"message","Simulator "+job.mode+" · "+job.category+" · "+completed+"/"+total+" Aufträge · "+simulator.phase()));}catch(Exception ignored){}
                },0,1,TimeUnit.SECONDS);
                try {if("WEB".equals(job.mode))simulator.laufWebSynchron(config);else simulator.laufSynchron(config);}
                catch(Exception simulationError){if(runtimeFailure.get()!=null)throw runtimeFailure.get();throw simulationError;}
                finally {progress.shutdownNow();progress.awaitTermination(5,TimeUnit.SECONDS);}
                if(runtimeFailure.get()!=null)throw runtimeFailure.get();
                state.checkSources();runtimeGuard.check(true);runs.add(Map.of("mode",job.mode,"category",job.category.name(),"directory",simulator.letztesLogVerzeichnis()));state.update(Map.of("runDirectories",List.copyOf(runs)));
                if(!simulator.laufErfolgreich()||!simulator.nachweiseVollstaendig()) {
                    boolean confirmedFailure=simulator.auftraege().stream().anyMatch(a->a.status==Auftrag.Status.FEHLGESCHLAGEN||a.status==Auftrag.Status.VERIFIKATION_FEHLGESCHLAGEN)
                        ||simulator.globalePruefungen().stream().anyMatch(p->!p.ok());
                    String message="Simulator "+job.mode+" · "+job.category+" fehlgeschlagen. Kein grünes Zertifikat. "
                        +Objects.toString(simulator.letzteFehlermeldung(),"")+" Laufprotokoll: "+simulator.letztesLogVerzeichnis();
                    if("TEST_FAILURE".equals(simulatorFailureKind(simulator.abgebrochen(),simulator.phase(),confirmedFailure)))throw new TestFailure(message);
                    throw new IllegalStateException("Technischer Abbruch oder unvollständiger Prüfnachweis: "+message);
                }
            }
            state.phase(11,"GLM_REVIEW","GLM prüft die vollständigen ausgewählten Fälle");
            runtimeGuard.check(true);
            try(LlmReviewService llm=new LlmReviewService(root)){
                llm.start(((Number)initial.get("llmCases")).intValue());Map<String,Object> review;
                do {state.checkSources();runtimeGuard.check(false);review=llm.status();int done=((Number)review.getOrDefault("completedCases",0)).intValue(),total=((Number)review.getOrDefault("totalCases",initial.get("llmCases"))).intValue();
                    state.update(Map.of("completedItems",done,"totalItems",total,"phaseFraction",total>0?Math.min(.99,(double)done/total):0,"message",String.valueOf(review.getOrDefault("message","GLM-Prüfung läuft")),"glmReviewId",String.valueOf(review.getOrDefault("reviewId","")),"glmDirectory",String.valueOf(review.getOrDefault("artifactDirectory","")),"retryStatistik",review.getOrDefault("retryStatistik",Map.of()),"findingsCount",((java.util.Collection<?>)review.getOrDefault("findings",List.of())).size()));
                    if(Boolean.TRUE.equals(review.get("running")))Thread.sleep(1000);
                }while(Boolean.TRUE.equals(review.get("running")));
                state.checkSources();runtimeGuard.check(true);Map<String,Object> summary=new LinkedHashMap<>();for(String key:List.of("reviewId","state","totalCases","completedCases","totalBatches","completedBatches","transportVerified","calculationVerified","reviewCoverageVerified","findings","artifactDirectory","message","hasFailures","caseVerdict","hasUnverifiedFindings","retryStatistik"))if(review.containsKey(key))summary.put(key,review.get(key));state.update(Map.of("llmSummary",summary));
                if(llmHasConfirmedFailure(review)||!"PASS".equals(review.get("state"))||!Boolean.TRUE.equals(review.get("transportVerified"))||!Boolean.TRUE.equals(review.get("calculationVerified"))||!Boolean.TRUE.equals(review.get("reviewCoverageVerified"))) {
                    if(llmHasConfirmedFailure(review))throw new TestFailure("GLM hat fachliche Abweichungen festgestellt. Kein grünes Zertifikat. GLM-Protokoll auswerten: "+review.get("artifactDirectory"));
                    throw new IllegalStateException("GLM-Prüfung nicht vollständig bestätigt ("+review.get("state")+"). Kein grünes Zertifikat. GLM-Protokoll auswerten: "+review.get("artifactDirectory"));
                }
            }
            state.phase(12,"CERTIFICATE_EXPORT","Testzertifikat automatisch erstellen");
            runtimeGuard.check(true);
            state.update(Map.of("state","FINALIZING","running",true));state.checkSources();
            Path export=directory.resolve("certificate-export.json");Process process=new ProcessBuilder("node",root.resolve("doc/testzertifikat-werkzeug/publish-certificate.js").toString(),"--publish",root.toString()).directory(root.toFile()).redirectError(directory.resolve("certificate-export-error.log").toFile()).redirectOutput(export.toFile()).start();
            try {
                if(awaitExport(process,state,runtimeGuard)!=0)throw new IllegalStateException("Zertifikatsexport fehlgeschlagen. Exportprotokoll auswerten.");
            }finally{if(process.isAlive()){process.descendants().forEach(ProcessHandle::destroyForcibly);process.destroyForcibly();}}
            Map<String,Object> certificate=CertificateSuiteState.read(root.resolve("doc/testzertifikat-werkzeug/current-certificate.json"));
            if(!"READY".equals(certificate.get("state"))||!"BESTANDEN".equals(certificate.get("verdict")))throw new IllegalStateException("Gesamtzertifikat ist nicht BESTANDEN. Zertifikat und Protokoll auswerten.");
            state.checkSources();runtimeGuard.check(true);state.update(Map.of("state","SUCCESS","running",false,"finishedAt",Instant.now().toString(),"completedPhases",CertificateSuiteState.PHASES,"phaseFraction",0,"message","Alle Zertifikatstests bestanden. Das aktuelle Zertifikat ist erstellt.","verdict","BESTANDEN","certificateId",certificate.get("certificateId"),"certificatePdf","/api/zertifikat/pdf","certificateCurrent",true));
        }catch(Exception error){
            boolean cancelled=error instanceof InterruptedException||Files.exists(directory.resolve("cancel.request"));
            state.update(Map.of("state",cancelled?"CANCELLED":"FAILED","running",false,"finishedAt",Instant.now().toString(),"message",cancelled?"Zertifikatslauf abgebrochen. Kein gültiger Gesamtnachweis erstellt.":safeMessage(root,error),"errorType",error.getClass().getSimpleName(),"failureKind",error instanceof TestFailure?"TEST_FAILURE":"TECHNICAL"));
            throw error;
        }finally{
            BrowserProzesse.raeumeVerwaisteFensterAuf();
        }
    }
    /** Derselbe Guard gilt bis zum Ende des Exporters, auch bei sehr schnellem Prozessende. */
    static int awaitExport(Process process,CertificateSuiteState state,CertificateRuntimeSnapshot.Guard runtimeGuard) throws Exception {
        while(!process.waitFor(500,TimeUnit.MILLISECONDS)) {
            state.checkSources();runtimeGuard.check(false);
        }
        state.checkSources();runtimeGuard.check(true);
        return process.exitValue();
    }
    static String simulatorFailureKind(boolean aborted,Simulator.Phase phase,boolean confirmedFailure){
        return !aborted&&phase!=Simulator.Phase.ABGEBROCHEN&&confirmedFailure?"TEST_FAILURE":"TECHNICAL";
    }
    static boolean llmHasConfirmedFailure(Map<String,Object> review){
        if("FAIL".equals(review.get("state"))||"FAIL".equals(review.get("caseVerdict"))||Boolean.TRUE.equals(review.get("hasFailures")))return true;
        if(review.get("cases") instanceof List<?> cases)for(Object item:cases)if(item instanceof Map<?,?> row&&"FAIL".equals(row.get("verdict")))return true;
        return false;
    }
    private static String safeMessage(Path root,Exception error){String text=Objects.toString(error.getMessage(),error.getClass().getSimpleName());for(String name:List.of("TX_PASS","GLM_API_KEY","SWEET_LLM_API_KEY")){String value=System.getenv(name);if(value!=null&&!value.isBlank())text=text.replace(value,"[VERDECKT]");}try{String key=new LlmConfig(root,System.getenv()).read().apiKey();if(!key.isBlank())text=text.replace(key,"[VERDECKT]");}catch(Exception ignored){}return text;}
    private static final class TestFailure extends IllegalStateException{TestFailure(String message){super(message);}}
    private CertificateSuiteWorker(){}
}
