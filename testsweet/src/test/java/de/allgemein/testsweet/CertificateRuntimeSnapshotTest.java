package de.allgemein.testsweet;

import de.allgemein.lmg.calc.Leistungsberechnung;
import de.allgemein.lmg.web.Json;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

/** Laufkopie, tatsächliche CodeSources und unverändert konservative Belegprüfung. */
class CertificateRuntimeSnapshotTest {
    @TempDir Path temporary;

    @Test
    void workerKlassenLadenAusKopieAuchWennOriginaleDanachUeberschriebenWerden() throws Exception {
        Path original = temporary.resolve("original");
        var originals = CertificateRuntimeSnapshot.copyVerified(List.of(BuildEvidence.location(BuildEvidence.class),
                BuildEvidence.location(Leistungsberechnung.class)), original);
        List<Path> originalPaths = originals.stream().map(e -> original.resolve((String)e.get("path"))).toList();
        Path snapshot = temporary.resolve("snapshot");
        var copies = CertificateRuntimeSnapshot.copyVerified(originalPaths, snapshot);
        String classes = String.join(File.pathSeparator, copies.stream().map(e -> snapshot.resolve((String)e.get("path")).toString()).toList());
        String productBefore = BuildEvidence.locationHash(snapshot.resolve((String)copies.get(1).get("path")));
        // Ausschließlich temporäre Original-Attrappen; niemals echte .m2-/target-Dateien ändern.
        for (Path location : originalPaths) {
            if (Files.isRegularFile(location)) Files.writeString(location, "ersetztes Original-JAR");
            else try(var files=Files.walk(location)) {
                Path compiled=files.filter(p->p.toString().endsWith(".class")).findFirst().orElseThrow();
                Files.write(compiled,new byte[]{42},StandardOpenOption.APPEND);
            }
        }
        Path result = temporary.resolve("probe-evidence.json"), log = temporary.resolve("probe.log");
        String cp = classes + File.pathSeparator + BuildEvidence.location(SnapshotCodeSourceProbe.class);
        Process probe = new ProcessBuilder(CertificateSuiteService.javaExecutable(), "-cp", cp,
                SnapshotCodeSourceProbe.class.getName(), snapshot.toString(), BuildEvidence.currentRoot().toString(), result.toString())
                .redirectErrorStream(true).redirectOutput(log.toFile()).start();
        assertTrue(probe.waitFor(30, TimeUnit.SECONDS), "CodeSource-Probe beendet sich");
        assertEquals(0, probe.exitValue(), Files.readString(log));
        assertEquals(productBefore, BuildEvidence.locationHash(snapshot.resolve((String)copies.get(1).get("path"))));
        Map<?,?> evidence = (Map<?,?>) Json.parse(Files.readString(result));
        assertEquals(Boolean.TRUE, evidence.get("sourceArtifactsMatch"));
        for (Object raw : (List<?>)evidence.get("artifacts")) {
            assertTrue(Path.of((String)((Map<?,?>)raw).get("path")).startsWith(snapshot));
        }
        // Der unveränderte unabhängige Node-Reader akzeptiert die tatsächlichen Snapshotpfade.
        Path reader = BuildEvidence.currentRoot().resolve("doc/testzertifikat-werkzeug/certificate-evidence.js");
        Process node = new ProcessBuilder("node", "-e",
                "const fs=require('fs'),e=require(process.argv[1]);if(!e.buildValid(process.argv[2],JSON.parse(fs.readFileSync(process.argv[3],'utf8'))))process.exit(1)",
                reader.toString(), BuildEvidence.currentRoot().toString(), result.toString())
                .redirectErrorStream(true).redirectOutput(temporary.resolve("node.log").toFile()).start();
        assertTrue(node.waitFor(20, TimeUnit.SECONDS));
        assertEquals(0, node.exitValue(), Files.readString(temporary.resolve("node.log")));
    }

    public static class SnapshotCodeSourceProbe {
        public static void main(String[] args) throws Exception {
            Path snapshot=Path.of(args[0]);
            BuildEvidence.initialize();
            for (Class<?> type : List.of(BuildEvidence.class, Leistungsberechnung.class, CertificateSuiteWorker.class)) {
                if (!BuildEvidence.location(type).startsWith(snapshot)) throw new AssertionError("CodeSource außerhalb der Runtime-Kopie");
            }
            Map<String,Object> evidence=BuildEvidence.capture(Path.of(args[1]));
            if (!Boolean.TRUE.equals(evidence.get("processBuildUnchanged"))) throw new AssertionError("Snapshotprozess verlor seine Buildidentität");
            Files.writeString(Path.of(args[2]),Json.write(evidence),StandardOpenOption.CREATE_NEW);
        }
    }

    @Test
    void kopieUeberschreibtKeinBestehendesLaufverzeichnis() throws Exception {
        Path source=temporary.resolve("source.jar");Files.writeString(source,"fixture");
        Path target=temporary.resolve("runtime");Files.createDirectory(target);Files.writeString(target.resolve("bestehend"),"erhalten");
        assertThrows(FileAlreadyExistsException.class,()->CertificateRuntimeSnapshot.copyVerified(List.of(source),target));
        assertEquals("erhalten",Files.readString(target.resolve("bestehend")));
    }

    @Test
    void guardUnterscheidetErsetzteBuilddateiUndFehlendenJUnitbeleg() throws Exception {
        Path file=temporary.resolve("original.jar");Files.writeString(file,"vorher");
        var expected=List.<Map<String,Object>>of(Map.of("path",file.toString(),"kind","jar",
                "sha256",BuildEvidence.hash(Files.readAllBytes(file))));
        assertDoesNotThrow(()->CertificateRuntimeSnapshot.verifyGuards(expected));
        Files.writeString(file,"nachher");
        var changed=assertThrows(IOException.class,()->CertificateRuntimeSnapshot.verifyGuards(expected));
        assertTrue(changed.getMessage().contains("während des Zertifikatslaufs ersetzt"));
        assertTrue(changed.getMessage().contains(file.toString()));
        Files.delete(file);
        assertTrue(assertThrows(IOException.class,()->CertificateRuntimeSnapshot.verifyGuards(expected)).getMessage().contains("entfernt"));
    }

    @Test
    void veraenderteOriginalquelleVerhindertWeiterhinDenWorkerstart() throws Exception {
        Path root=temporary.resolve("repo"),suite=root.resolve("testsweet/logs/certificate-suite-fixture");
        Files.createDirectories(suite.resolve("runtime"));Files.createDirectories(root.resolve("tools"));
        Files.writeString(root.resolve("tools/evidence-sources.txt"),"dir:tools\n");
        Files.writeString(root.resolve("tools/source.txt"),"alt");
        String expected=BuildEvidence.inventoryHash(BuildEvidence.sourceInventory(root));
        Files.writeString(suite.resolve("runtime/dummy.jar"),"fixture");
        Files.writeString(suite.resolve("runtime/manifest.json"),Json.write(Map.of("kind","CERTIFICATE_RUNTIME",
                "sourceSha256",expected,"entries",List.of(Map.of("path","dummy.jar","sha256",
                        BuildEvidence.hash(Files.readAllBytes(suite.resolve("runtime/dummy.jar"))))))));
        Files.writeString(root.resolve("tools/source.txt"),"neu");
        assertTrue(assertThrows(IOException.class,()->CertificateRuntimeSnapshot.classpath(root,suite))
                .getMessage().contains("Quellen seit Erstellung"));
    }

    @Test
    void alterSteuerungsprozessErhaeltKlareNeustartmeldung() throws Exception {
        Path root=temporary.resolve("repo"),suite=root.resolve("testsweet/logs/certificate-suite-old-controller");
        Files.createDirectories(suite);
        assertTrue(assertThrows(IOException.class,()->CertificateRuntimeSnapshot.workerGuard(root,suite))
                .getMessage().contains("testsweet neu starten"));
        Files.createDirectory(suite.resolve("runtime"));Files.writeString(suite.resolve("runtime/manifest.json"),"{}");
        Files.writeString(suite.resolve("status.json"),"{}");
        assertTrue(assertThrows(IOException.class,()->CertificateRuntimeSnapshot.workerGuard(root,suite))
                .getMessage().contains("älteren Build ohne Runtime-Startnachweis"));
    }

    @Test
    void runtimeManifestDarfKeinenPfadAusSeinemVerzeichnisLaden() throws Exception {
        Path root=temporary.resolve("repo"),suite=root.resolve("testsweet/logs/certificate-suite-path");
        Files.createDirectories(suite.resolve("runtime"));Files.createDirectories(root.resolve("tools"));
        Files.writeString(root.resolve("tools/evidence-sources.txt"),"dir:tools\n");
        Path outside=suite.resolve("outside.jar");Files.writeString(outside,"fixture");
        Files.writeString(suite.resolve("runtime/manifest.json"),Json.write(Map.of("kind","CERTIFICATE_RUNTIME",
                "sourceSha256",BuildEvidence.inventoryHash(BuildEvidence.sourceInventory(root)),
                "entries",List.of(Map.of("path","../outside.jar","sha256",BuildEvidence.hash(Files.readAllBytes(outside)))))));
        assertTrue(assertThrows(IOException.class,()->CertificateRuntimeSnapshot.classpath(root,suite))
                .getMessage().contains("Runtime-Kopie verändert"));
    }

    @Test
    void exporterMutationWirdSowohlBeimWartenAlsAuchNachSchnellemProzessendeAbgewiesen() throws Exception {
        for(boolean delayed:List.of(true,false)) {
            Path root=temporary.resolve("export-"+delayed),runtime=root.resolve("runtime");
            Files.createDirectories(runtime);Files.createDirectories(root.resolve("tools"));
            Files.writeString(root.resolve("tools/evidence-sources.txt"),"dir:tools\n");
            Path guarded=root.resolve("original-junit-marker.json");Files.writeString(guarded,"vorher");
            Path manifestFile=runtime.resolve("manifest.json");Files.writeString(manifestFile,"{}");
            var guard=new CertificateRuntimeSnapshot.Guard(Map.of("guards",List.of(Map.of("path",guarded.toString(),
                    "kind","file","sha256",BuildEvidence.hash(Files.readAllBytes(guarded)))),"entries",List.of()),
                    runtime,BuildEvidence.hash(Files.readAllBytes(manifestFile)));
            var state=new CertificateSuiteState(root,root,Map.of("sourceSha256",
                    BuildEvidence.inventoryHash(BuildEvidence.sourceInventory(root))));
            Process exporter=new ProcessBuilder(CertificateSuiteService.javaExecutable(),"-cp",
                    System.getProperty("surefire.test.class.path",System.getProperty("java.class.path")),
                    ExportMutationProbe.class.getName(),guarded.toString(),String.valueOf(delayed))
                    .redirectErrorStream(true).redirectOutput(root.resolve("export.log").toFile()).start();
            try {
                // Der schnelle Exporter ist schon fertig: Die zwingende Schlussprüfung muss greifen.
                if(!delayed)assertTrue(exporter.waitFor(15,TimeUnit.SECONDS));
                else {
                    long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
                    while(Files.readString(guarded).equals("vorher")&&System.nanoTime()<deadline)Thread.sleep(20);
                    assertNotEquals("vorher",Files.readString(guarded),"Exporter hat die Guarddatei geändert");
                }
                IOException failure=assertThrows(IOException.class,()->CertificateSuiteWorker.awaitExport(exporter,state,guard));
                assertTrue(failure.getMessage().contains("während des Zertifikatslaufs ersetzt"));
                assertTrue(failure.getMessage().contains(guarded.toString()));
                if(delayed)assertTrue(exporter.isAlive(),"Drift beendet das Warten vor dem Exporter");
            } finally {
                if(exporter.isAlive())exporter.destroyForcibly();
                assertTrue(exporter.waitFor(10,TimeUnit.SECONDS));
            }
        }
    }

    public static class ExportMutationProbe {
        public static void main(String[] args) throws Exception {
            Files.writeString(Path.of(args[0]),"ersetzt während Export");
            if(Boolean.parseBoolean(args[1]))Thread.sleep(15000);
        }
    }
}
