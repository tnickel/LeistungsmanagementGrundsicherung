package de.allgemein.testsweet;

import de.allgemein.lmg.web.Json;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

/** Dauerhafter Fortschritt; atomare Snapshots sind auch nach Browser-/Serverneustart lesbar. */
final class CertificateSuiteState {
    static final int PHASES = 13;
    static final int MOVE_ATTEMPTS = 10;
    private static final long MOVE_RETRY_MILLIS = 100;
    @FunctionalInterface interface FileMover { void move(Path source,Path target,CopyOption... options) throws IOException; }
    @FunctionalInterface interface RetryPause { void pause(long millis) throws InterruptedException; }
    final Path root, directory;
    private final Map<String,Object> values;
    CertificateSuiteState(Path root, Path directory, Map<String,Object> values) {
        this.root=root; this.directory=directory; this.values=new LinkedHashMap<>(values);
    }
    static Map<String,Object> read(Path file) throws IOException {
        if (!(Json.parse(Files.readString(file,StandardCharsets.UTF_8)) instanceof Map<?,?> raw)) throw new IOException("Ungültiger Zertifikatslaufstatus");
        Map<String,Object> result=new LinkedHashMap<>(); raw.forEach((k,v)->result.put(String.valueOf(k),v)); return result;
    }
    static void atomic(Path file, Map<String,Object> data) throws IOException {
        atomic(file,data,Files::move,Thread::sleep);
    }
    static void atomic(Path file,Map<String,Object> data,FileMover mover,RetryPause pause) throws IOException {
        interrupted(file);
        Files.createDirectories(file.getParent()); Path temp=Files.createTempFile(file.getParent(),".suite-",".json");
        IOException failure=null;
        try {
            Files.writeString(temp,Json.write(data),StandardCharsets.UTF_8);
            boundedReplace(temp,file,mover,pause);
        } catch(IOException error){failure=error;throw error;}
        finally {
            try {Files.deleteIfExists(temp);}catch(IOException cleanup){if(failure!=null)failure.addSuppressed(cleanup);else throw cleanup;}
        }
    }
    /** Windows-Leser/Virenscanner können einen Rename kurz blockieren. Nur dieser
     * konkrete Fehler wird begrenzt wiederholt; Ziel niemals löschen/trunkieren. */
    static void boundedReplace(Path temporary,Path target) throws IOException {
        boundedReplace(temporary,target,Files::move,Thread::sleep);
    }
    private static void boundedReplace(Path temporary,Path target,FileMover mover,RetryPause pause) throws IOException {
        for(int attempt=1;attempt<=MOVE_ATTEMPTS;attempt++) {
            interrupted(target);
            try {
                try {mover.move(temporary,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
                catch(AtomicMoveNotSupportedException e){mover.move(temporary,target,StandardCopyOption.REPLACE_EXISTING);}
                return;
            } catch(AccessDeniedException denied) {
                if(attempt==MOVE_ATTEMPTS)throw new IOException("Technischer Dateizugriffsfehler: Windows blockiert die Prüfstatusdatei auch nach "+MOVE_ATTEMPTS+" Versuchen. Der Lauf kann nicht bestätigt werden. Erneut starten; bei Wiederholung das Protokoll auswerten. Statusdatei: "+target,denied);
                try {pause.pause(MOVE_RETRY_MILLIS);}
                catch(InterruptedException cancelled){Thread.currentThread().interrupt();throw interruptedFailure(target,cancelled);}
            }
        }
    }
    private static void interrupted(Path file) throws java.io.InterruptedIOException {
        if(Thread.currentThread().isInterrupted())throw interruptedFailure(file,null);
    }
    private static java.io.InterruptedIOException interruptedFailure(Path file,Exception cause){
        var error=new java.io.InterruptedIOException("Speichern des Zertifikatslaufstatus wurde abgebrochen. Kein gültiger Gesamtnachweis. Statusdatei: "+file);if(cause!=null)error.initCause(cause);return error;
    }
    synchronized void update(Map<String,Object> fields) throws IOException {
        if(Files.exists(directory.resolve("cancel.request"))&&!Set.of("CANCELLED","FAILED").contains(fields.getOrDefault("state","RUNNING")))throw new java.io.InterruptedIOException("Zertifikatslauf wurde abgebrochen");
        values.putAll(fields); values.put("updatedAt",Instant.now().toString());
        int done=((Number)values.getOrDefault("completedPhases",0)).intValue();
        double part=((Number)values.getOrDefault("phaseFraction",0)).doubleValue();
        values.put("percent",Math.min("SUCCESS".equals(values.get("state"))?100:99,Math.floor(100.0*(done+Math.min(.99,part))/PHASES)));
        values.put("totalPhases",PHASES); values.put("completedSteps",done);values.put("totalSteps",PHASES);
        values.put("phaseCompleted",values.getOrDefault("completedItems",0));values.put("phaseTotal",values.getOrDefault("totalItems",0));
        atomic(directory.resolve("status.json"),values);
    }
    void phase(int completed,String phase,String label) throws IOException {
        update(Map.of("state","RUNNING","running",true,"completedPhases",completed,"phase",phase,"phaseLabel",label,"message",label,"phaseFraction",0,"completedItems",0,"totalItems",0));
    }
    void checkSources() throws Exception {
        checkCancelled();
        if(!Objects.equals(values.get("sourceSha256"),BuildEvidence.inventoryHash(BuildEvidence.sourceInventory(root)))) throw new IllegalStateException("Quelldateien wurden während des Zertifikatslaufs geändert. Den vollständigen Lauf nach Abschluss der Änderungen neu starten.");
    }
    void checkCancelled() throws InterruptedException {
        if(Thread.currentThread().isInterrupted()||Files.exists(directory.resolve("cancel.request")))throw new InterruptedException("Zertifikatslauf abgebrochen");
        if(values.get("controllerPid") instanceof Number pid&&values.get("controllerStartedAt") instanceof String expected){
            boolean alive=ProcessHandle.of(pid.longValue()).filter(ProcessHandle::isAlive).flatMap(p->p.info().startInstant()).map(t->t.toString().equals(expected)).orElse(false);
            if(!alive)throw new InterruptedException("Steuerprozess des Zertifikatslaufs wurde beendet");
        }
    }
    static Map<String,Object> publicView(Map<String,Object> data) {
        Map<String,Object> result=new LinkedHashMap<>(data);
        try {long secs=java.time.Duration.between(Instant.parse(String.valueOf(data.get("startedAt"))),Instant.parse(String.valueOf(data.getOrDefault("finishedAt",Instant.now().toString())))).toSeconds(); result.put("elapsedSeconds",Math.max(0,secs));}
        catch(RuntimeException ignored){result.put("elapsedSeconds",0);}
        result.remove("phaseFraction");return result;
    }
}
