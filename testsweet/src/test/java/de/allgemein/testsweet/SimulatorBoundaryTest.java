package de.allgemein.testsweet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

class SimulatorBoundaryTest {
    @TempDir Path temp;
    @Test void allCategoryLimitsAreEnforcedBeforeAWorkerCanStart() {
        for (var category : Simulator.Kategorie.values()) {
            int limit = Simulator.auftraegeLimit(category);
            assertDoesNotThrow(() -> Simulator.pruefeKonfiguration(category,1,1));
            assertDoesNotThrow(() -> Simulator.pruefeKonfiguration(category,50,limit));
            for (int workers : List.of(0,51)) assertThrows(IllegalStateException.class,() -> Simulator.pruefeKonfiguration(category,workers,1));
            for (int cases : List.of(0,limit+1)) assertThrows(IllegalStateException.class,() -> Simulator.pruefeKonfiguration(category,1,cases));
        }
    }
    @Test void allFourEntryPointsRejectASecondRunBeforeChangingState() throws Exception {
        Simulator simulator = new Simulator();
        var field=Simulator.class.getDeclaredField("laufAktiv");field.setAccessible(true);
        ((AtomicBoolean)field.get(simulator)).set(true);
        var configuration = new Simulator.LaufKonfiguration(1,1,42,0,false,Simulator.Kategorie.POSITIV);
        assertThrows(IllegalStateException.class,() -> simulator.starte(configuration));
        assertThrows(IllegalStateException.class,() -> simulator.starteWeb(configuration));
        assertThrows(IllegalStateException.class,() -> simulator.laufSynchron(configuration));
        assertThrows(IllegalStateException.class,() -> simulator.laufWebSynchron(configuration));
        assertTrue(simulator.laufAktiv());assertEquals(Simulator.Phase.BEREIT,simulator.phase());
        assertNull(simulator.letztesLogVerzeichnis());assertTrue(simulator.auftraege().isEmpty());
    }
    @Test void healthyStressBarrierDoesNotReleaseSecondWaveUntilEveryFirstWaveOrderEnds() throws Exception {
        Simulator simulator = new Simulator();
        var orders=List.of(order(1),order(2),order(3),order(4));
        set(simulator,"auftraege",orders);set(simulator,"kategorieLetzter",Simulator.Kategorie.STRESS);
        var init=Simulator.class.getDeclaredMethod("initialisiereStressBarriere");init.setAccessible(true);init.invoke(simulator);
        assertSame(orders.get(0),simulator.nimmAuftrag());assertSame(orders.get(1),simulator.nimmAuftrag());
        ExecutorService executor=Executors.newSingleThreadExecutor();CountDownLatch entered=new CountDownLatch(1);
        try {
            Future<Auftrag> waiting=executor.submit(() -> {entered.countDown();return simulator.nimmAuftrag();});
            assertTrue(entered.await(2,TimeUnit.SECONDS));
            assertThrows(TimeoutException.class,() -> waiting.get(100,TimeUnit.MILLISECONDS));
            simulator.auftragSpielenBeendet(orders.get(0));
            assertThrows(TimeoutException.class,() -> waiting.get(100,TimeUnit.MILLISECONDS));
            simulator.auftragSpielenBeendet(orders.get(1));
            assertSame(orders.get(2),waiting.get(2,TimeUnit.SECONDS));assertSame(orders.get(3),simulator.nimmAuftrag());
        } finally {executor.shutdownNow();assertTrue(executor.awaitTermination(2,TimeUnit.SECONDS));}
    }
    @Test void cliRejectsInvalidConfigurationWithExitTwoWithoutCreatingRunEvidence() throws Exception {
        String cp=System.getProperty("surefire.test.class.path",System.getProperty("java.class.path"));
        for (List<String> args : List.of(List.of("run","0","1"),List.of("run","1","501"),List.of("run","abc"),List.of("run","1","1","42","0","INVALID"),List.of("run","1","6","42","0","ECHT"),List.of("run","1","13","42","0","RECHNER"),List.of("web","1","1","42","0","STRESS"),List.of("web","1","1","42","0","ECHT"))) {
            var command=new java.util.ArrayList<>(List.of(Path.of(System.getProperty("java.home"),"bin","java").toString(),
                    "-Dfile.encoding=UTF-8","-Dstdout.encoding=UTF-8","-Dstderr.encoding=UTF-8","-cp",cp,SweetMain.class.getName()));command.addAll(args);
            Path output=temp.resolve("cli.log");Process process=new ProcessBuilder(command).directory(temp.toFile()).redirectErrorStream(true).redirectOutput(output.toFile()).start();
            assertTrue(process.waitFor(20,TimeUnit.SECONDS));assertEquals(2,process.exitValue(),Files.readString(output));
            assertFalse(Files.exists(temp.resolve("testsweet/logs")));
        }
    }
    @Test void certificateWebRunNeverFallsBackToAnUnverifiedExternalServerOnOccupiedPort() throws Exception {
        String cp=System.getProperty("surefire.test.class.path",System.getProperty("java.class.path"));
        try(var occupied=new java.net.ServerSocket(0)) {
            Path output=temp.resolve("occupied-web.log");
            var command=List.of(Path.of(System.getProperty("java.home"),"bin","java").toString(),
                "-Dfile.encoding=UTF-8","-Dstdout.encoding=UTF-8","-Dstderr.encoding=UTF-8",
                "-Dsweet.requireEmbedded=true","-Dlmg.port="+occupied.getLocalPort(),"-Dtx.url=jdbc:must-never-connect",
                "-cp",cp,SweetMain.class.getName(),"web","1","1","42","0","POSITIV");
            ProcessBuilder builder=new ProcessBuilder(command).directory(temp.toFile()).redirectErrorStream(true).redirectOutput(output.toFile());
            builder.environment().remove("SWEET_BASE_URL");
            Process process=builder.start();
            assertTrue(process.waitFor(20,TimeUnit.SECONDS));assertNotEquals(0,process.exitValue());
            String log=Files.readString(output);
            assertTrue(log.contains("Zertifikatstests benötigen einen eigenen frisch gebauten Webserver"),log);
            assertFalse(log.contains("Oracle-Pool initialisiert"),log);
            assertFalse(log.contains("ChromeDriver was started"),log);
        }
    }
    private static Auftrag order(int id) {return new Auftrag(id,"STRESS-TEST","SWEET-TEST", "", "",List.of(),List.of());}
    private static void set(Object target,String name,Object value)throws Exception{var field=target.getClass().getDeclaredField(name);field.setAccessible(true);field.set(target,value);}
}
