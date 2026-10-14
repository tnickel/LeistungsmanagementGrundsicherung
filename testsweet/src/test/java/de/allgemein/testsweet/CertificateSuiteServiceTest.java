package de.allgemein.testsweet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CertificateSuiteServiceTest {
    @TempDir Path root;
    @Test void constructionAndStatusNeverStartCommandsOrCreateFiles() {
        try(var service=new CertificateSuiteService(root,Map.of())){
            assertEquals("IDLE",service.status().get("state"));assertFalse(service.running());assertFalse(Files.exists(root.resolve("testsweet")));
        }
    }
    @Test void missingKeyStopsBeforeJobOrEvidenceStarts() throws Exception {
        try(var service=new CertificateSuiteService(root,Map.of())){
            assertThrows(IllegalStateException.class,service::start);assertFalse(service.running());assertFalse(Files.exists(root.resolve("testsweet/logs/suite-current.json")));
        }
    }
    @Test void junitSkipsFailuresErrorsAndMissingReportsCannotPass() throws Exception {
        Path reports=Files.createDirectory(root.resolve("reports")),xml=reports.resolve("TEST-fixture.xml");
        assertThrows(IllegalStateException.class,()->CertificateSuiteService.verifyJUnit(reports));
        for(String attr:List.of("skipped","failures","errors")){
            Files.writeString(xml,"<testsuite tests=\"3\" skipped=\""+(attr.equals("skipped")?1:0)+"\" failures=\""+(attr.equals("failures")?1:0)+"\" errors=\""+(attr.equals("errors")?1:0)+"\"/>");
            assertThrows(IllegalStateException.class,()->CertificateSuiteService.verifyJUnit(reports));
        }
        Files.writeString(xml,"<testsuite tests=\"3\" skipped=\"0\" failures=\"0\" errors=\"0\"/>");assertDoesNotThrow(()->CertificateSuiteService.verifyJUnit(reports));
    }
    @Test void junitRejectsExternalEntities() throws Exception {
        Path reports=Files.createDirectory(root.resolve("reports"));Files.writeString(reports.resolve("TEST-xxe.xml"),"<!DOCTYPE testsuite [<!ENTITY x SYSTEM 'file:///never-read'>]><testsuite tests=\"1\" skipped=\"0\" failures=\"0\" errors=\"0\">&x;</testsuite>");
        assertThrows(Exception.class,()->CertificateSuiteService.verifyJUnit(reports));
    }
    @Test void allSevenModesAreFixedAndWebRunsBeforeGlmInputSources() {
        assertEquals(7,CertificateSuiteWorker.JOBS.size());assertEquals(List.of("WEB","WEB","WEB","FACH","FACH","FACH","FACH"),CertificateSuiteWorker.JOBS.stream().map(CertificateSuiteWorker.Job::mode).toList());
        assertEquals(1312,CertificateSuiteWorker.JOBS.stream().mapToInt(j->j.category()==Simulator.Kategorie.STRESS?j.orders()*2:j.orders()).sum());
        assertEquals(Simulator.Kategorie.STRESS,CertificateSuiteWorker.JOBS.getLast().category());
    }
    @Test void simulatorTechnicalAbortIsNotInventedSoftwareFinding() {
        assertEquals("TECHNICAL",CertificateSuiteWorker.simulatorFailureKind(true,Simulator.Phase.ABGEBROCHEN,false));
        assertEquals("TECHNICAL",CertificateSuiteWorker.simulatorFailureKind(false,Simulator.Phase.ABGEBROCHEN,false));
        assertEquals("TECHNICAL",CertificateSuiteWorker.simulatorFailureKind(false,Simulator.Phase.FEHLGESCHLAGEN,false));
        assertEquals("TEST_FAILURE",CertificateSuiteWorker.simulatorFailureKind(false,Simulator.Phase.FEHLGESCHLAGEN,true));
    }
    @Test void knownLlmFailureSurvivesLaterTechnicalErrorButProvisionalFindingDoesNotBecomeConfirmed(){
        assertTrue(CertificateSuiteWorker.llmHasConfirmedFailure(Map.of("state","ERROR","hasFailures",true)));
        assertTrue(CertificateSuiteWorker.llmHasConfirmedFailure(Map.of("state","ERROR","caseVerdict","FAIL")));
        assertTrue(CertificateSuiteWorker.llmHasConfirmedFailure(Map.of("state","ERROR","cases",List.of(Map.of("caseId","TF01B1","verdict","FAIL")))));
        assertFalse(CertificateSuiteWorker.llmHasConfirmedFailure(Map.of("state","ERROR","hasUnverifiedFindings",true,"provisionalFailures",List.of(Map.of("caseId","TF01B1","verdict","FAIL")))));
    }
    @Test void atomicProgressHasRealCountsAndNeverFinishesEarly() throws Exception {
        Path directory=root.resolve("testsweet/logs/certificate-suite-test");CertificateSuiteState state=new CertificateSuiteState(root,directory,Map.of("startedAt",Instant.now().toString()));
        state.phase(11,"GLM_REVIEW","GLM");state.update(Map.of("completedItems",3,"totalItems",18,"phaseFraction",3.0/18));Map<String,Object> values=CertificateSuiteState.read(directory.resolve("status.json"));
        assertEquals(13,((Number)values.get("totalSteps")).intValue());assertEquals(3,((Number)values.get("phaseCompleted")).intValue());assertTrue(((Number)values.get("percent")).doubleValue()<100);
        state.update(Map.of("state","SUCCESS","completedPhases",13,"phaseFraction",0));assertEquals(100.0,((Number)CertificateSuiteState.read(directory.resolve("status.json")).get("percent")).doubleValue());
    }
    @Test void cancellationCannotBeOverwrittenBySuccessOrRunningProgress() throws Exception {
        Path directory=Files.createDirectories(root.resolve("suite"));CertificateSuiteState state=new CertificateSuiteState(root,directory,Map.of());Files.writeString(directory.resolve("cancel.request"),"cancel");
        assertThrows(java.io.InterruptedIOException.class,()->state.update(Map.of("state","SUCCESS")));assertThrows(java.io.InterruptedIOException.class,()->state.phase(5,"SIMULATOR","Simulator"));
        assertDoesNotThrow(()->state.update(Map.of("state","CANCELLED","running",false)));assertEquals("CANCELLED",CertificateSuiteState.read(directory.resolve("status.json")).get("state"));
    }
    @Test void deadOrReusedControllerIdentityCancelsWorker() throws Exception {
        CertificateSuiteState state=new CertificateSuiteState(root,root,Map.of("controllerPid",ProcessHandle.current().pid(),"controllerStartedAt","2000-01-01T00:00:00Z"));assertThrows(InterruptedException.class,state::checkCancelled);
    }
    @Test void orphanedPersistedJobIsFailureAfterServerRestart() throws Exception {
        Path directory=Files.createDirectories(root.resolve("testsweet/logs/certificate-suite-orphan"));CertificateSuiteState.atomic(directory.resolve("status.json"),Map.of("state","RUNNING","running",true,"controllerPid",-1,"startedAt",Instant.now().toString()));
        CertificateSuiteState.atomic(root.resolve("testsweet/logs/suite-current.json"),Map.of("directory",directory.toString()));try(var service=new CertificateSuiteService(root,Map.of())){assertEquals("FAILED",service.status().get("state"));assertEquals(false,service.status().get("running"));}
    }
    private Map<String,String> runnableFixture() throws Exception {
        Files.createDirectories(root.resolve("tools"));Files.writeString(root.resolve("tools/evidence-sources.txt"),"dir:tools\n");
        return Map.of("SWEET_LLM_API_KEY","fixture-secret","TX_URL","jdbc:fixture","TX_USER","fixture-user","TX_PASS","fixture-password");
    }
    private void awaitTerminal(CertificateSuiteService service) throws Exception {
        long until=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(5);while(service.running()&&System.nanoTime()<until)Thread.sleep(10);assertFalse(service.running(),"Controller beendet seine Phase");
    }
    @Test void commandFailureStopsImmediatelyWithoutSimulatorGlmOrExport() throws Exception {
        var commands=new java.util.concurrent.CopyOnWriteArrayList<String>();Map<String,String> env=runnableFixture();
        try(var service=new CertificateSuiteService(root,env,(label,command,environment,directory)->{commands.add(label);throw new java.io.IOException("fixture-secret fixture-password build failed");})){
            service.start();awaitTerminal(service);assertEquals(List.of("Hauptprogramm: clean install"),commands);assertEquals("FAILED",service.status().get("state"));
            String message=String.valueOf(service.status().get("message"));assertFalse(message.contains("fixture-secret"));assertFalse(message.contains("fixture-password"));assertEquals("TECHNICAL",service.status().get("failureKind"));
        }
    }
    @Test void sourceChangeDuringCommandStopsBeforeNextCommand() throws Exception {
        Map<String,String> env=runnableFixture();var commands=new java.util.concurrent.CopyOnWriteArrayList<String>();
        try(var service=new CertificateSuiteService(root,env,(label,command,environment,directory)->{commands.add(label);Files.writeString(root.resolve("tools/changed.txt"),"changed");})){
            service.start();awaitTerminal(service);assertEquals(1,commands.size());assertEquals("FAILED",service.status().get("state"));assertTrue(String.valueOf(service.status().get("message")).contains("Quelldateien wurden"));
        }
    }
    @Test void mavenFailureWithFreshAssertionEvidenceRemainsTestFailure() throws Exception {
        Map<String,String> env=runnableFixture();
        try(var service=new CertificateSuiteService(root,env,(label,command,environment,directory)->{
            Path reports=Files.createDirectories(root.resolve("target/surefire-reports"));Files.writeString(reports.resolve("TEST-fixture.xml"),"<testsuite tests=\"1\" failures=\"1\" errors=\"0\" skipped=\"0\"><testcase name=\"wrongAmount\"><failure message=\"expected 100 but got 200\"/></testcase></testsuite>");throw new IllegalStateException("Maven Exit 1");
        })){service.start();awaitTerminal(service);assertEquals("FAILED",service.status().get("state"));assertEquals("TEST_FAILURE",service.status().get("failureKind"));}
    }
    @Test void compileFailureCannotAdoptStaleAssertionFailureFromEarlierRun() throws Exception {
        Map<String,String> env=runnableFixture();Path reports=Files.createDirectories(root.resolve("target/surefire-reports")),xml=reports.resolve("TEST-old.xml");Files.writeString(xml,"<testsuite tests=\"1\" failures=\"1\" errors=\"0\" skipped=\"0\"/>");Files.setLastModifiedTime(xml,java.nio.file.attribute.FileTime.from(Instant.parse("2000-01-01T00:00:00Z")));
        try(var service=new CertificateSuiteService(root,env,(label,command,environment,directory)->{throw new IllegalStateException("Compilation failed before tests");})){service.start();awaitTerminal(service);assertEquals("FAILED",service.status().get("state"));assertEquals("TECHNICAL",service.status().get("failureKind"));}
    }
    @Test void duplicateStartsAcrossControllersAreRejectedByOwnedLock() throws Exception {
        Map<String,String> env=runnableFixture();var entered=new java.util.concurrent.CountDownLatch(1);var release=new java.util.concurrent.CountDownLatch(1);
        try(var first=new CertificateSuiteService(root,env,(label,command,environment,directory)->{entered.countDown();release.await();throw new java.io.IOException("finish fixture");});var second=new CertificateSuiteService(root,env)){
            first.start();assertTrue(entered.await(3,java.util.concurrent.TimeUnit.SECONDS));assertThrows(IllegalStateException.class,first::start);assertThrows(IllegalStateException.class,second::start);
            assertTrue(second.running(),"Andere GUI-Instanz erkennt laufende globale Suite");assertThrows(IllegalStateException.class,second::cancel);
            first.cancel();release.countDown();awaitTerminal(first);assertEquals("CANCELLED",first.status().get("state"));
        }finally{release.countDown();}
    }
    @Test void externalWebTargetCannotStartCertificateSuite() throws Exception {
        Map<String,String> env=new HashMap<>(runnableFixture());env.put("SWEET_BASE_URL","http://127.0.0.1:8080");
        try(var service=new CertificateSuiteService(root,env)){assertThrows(IllegalStateException.class,service::start);assertFalse(Files.exists(root.resolve("testsweet/logs/suite-current.json")));}
    }
}
