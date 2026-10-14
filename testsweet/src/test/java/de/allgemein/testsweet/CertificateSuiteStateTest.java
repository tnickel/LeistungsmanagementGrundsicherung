package de.allgemein.testsweet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.file.*;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class CertificateSuiteStateTest {
    @TempDir Path directory;
    @Test void temporaryWindowsSharingConflictRetriesThenReplacesCompleteSnapshot() throws Exception {
        Path target=directory.resolve("status.json");Files.writeString(target,"old-complete-snapshot");AtomicInteger attempts=new AtomicInteger(),pauses=new AtomicInteger();
        CertificateSuiteState.atomic(target,Map.of("state","RUNNING"),(source,destination,options)->{
            if(attempts.incrementAndGet()<3){assertEquals("old-complete-snapshot",Files.readString(target));throw new AccessDeniedException(source.toString(),destination.toString(),"sharing violation");}
            Files.move(source,destination,options);
        },millis->{assertEquals(100,millis);pauses.incrementAndGet();});
        assertEquals(3,attempts.get());assertEquals(2,pauses.get());assertEquals("RUNNING",CertificateSuiteState.read(target).get("state"));assertNoTemporaryFiles();
    }
    @Test void permanentAccessDenialHasBoundedRetriesAndPreservesOldTarget() throws Exception {
        Path target=directory.resolve("status.json");Files.writeString(target,"unchanged");AtomicInteger attempts=new AtomicInteger(),pauses=new AtomicInteger();
        IOException error=assertThrows(IOException.class,()->CertificateSuiteState.atomic(target,Map.of("state","SUCCESS"),(source,destination,options)->{attempts.incrementAndGet();throw new AccessDeniedException(source.toString());},millis->pauses.incrementAndGet()));
        assertEquals(CertificateSuiteState.MOVE_ATTEMPTS,attempts.get());assertEquals(CertificateSuiteState.MOVE_ATTEMPTS-1,pauses.get());assertEquals("unchanged",Files.readString(target));assertInstanceOf(AccessDeniedException.class,error.getCause());assertTrue(error.getMessage().contains("Erneut starten"));assertNoTemporaryFiles();
    }
    @Test void cancellationDuringRetryPreservesInterruptFlagAndTarget() throws Exception {
        Path target=directory.resolve("status.json");Files.writeString(target,"unchanged");AtomicInteger attempts=new AtomicInteger();
        try {
            assertThrows(InterruptedIOException.class,()->CertificateSuiteState.atomic(target,Map.of("state","SUCCESS"),(source,destination,options)->{attempts.incrementAndGet();throw new AccessDeniedException(source.toString());},millis->{throw new InterruptedException("cancel");}));
            assertTrue(Thread.currentThread().isInterrupted());assertEquals(1,attempts.get());assertEquals("unchanged",Files.readString(target));assertNoTemporaryFiles();
        } finally {Thread.interrupted();}
    }
    @Test void alreadyInterruptedWriteNeverAttemptsRenameOrChangesTarget() throws Exception {
        Path target=directory.resolve("status.json");Files.writeString(target,"unchanged");Thread.currentThread().interrupt();
        try {assertThrows(InterruptedIOException.class,()->CertificateSuiteState.atomic(target,Map.of("state","SUCCESS"),(source,destination,options)->fail("Kein Rename nach Abbruch"),millis->fail("Kein Warten nach Abbruch")));assertTrue(Thread.currentThread().isInterrupted());assertEquals("unchanged",Files.readString(target));assertNoTemporaryFiles();}
        finally {Thread.interrupted();}
    }
    @Test void unrelatedIoErrorIsNeverRetriedOrTurnedIntoDirectWrite() throws Exception {
        Path target=directory.resolve("status.json");Files.writeString(target,"unchanged");AtomicInteger attempts=new AtomicInteger();IOException original=new IOException("disk failure");
        IOException received=assertThrows(IOException.class,()->CertificateSuiteState.atomic(target,Map.of("state","SUCCESS"),(source,destination,options)->{attempts.incrementAndGet();throw original;},millis->fail("Nur AccessDeniedException wird wiederholt")));
        assertSame(original,received);assertEquals(1,attempts.get());assertEquals("unchanged",Files.readString(target));assertNoTemporaryFiles();
    }
    private void assertNoTemporaryFiles() throws IOException {try(var paths=Files.list(directory)){assertFalse(paths.anyMatch(file->file.getFileName().toString().startsWith(".suite-")));}}
}
