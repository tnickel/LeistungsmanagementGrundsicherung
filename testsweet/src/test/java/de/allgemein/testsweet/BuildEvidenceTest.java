package de.allgemein.testsweet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BuildEvidenceTest {
    @TempDir Path root;
    private void write(String name,String text)throws Exception{Path p=root.resolve(name);Files.createDirectories(p.getParent());Files.writeString(p,text);}
    @Test void sameSizeSourceMutationChangesFingerprintEvenWithSameTimestamp() throws Exception {
        write("tools/evidence-sources.txt","dir:src/main\nfile:pom.xml\n");write("pom.xml","<project/>");write("src/main/java/A.java","class A { int a=1; }");
        var before=BuildEvidence.sourceInventory(root);Path source=root.resolve("src/main/java/A.java");var time=Files.getLastModifiedTime(source);
        Files.writeString(source,"class A { int a=2; }");Files.setLastModifiedTime(source,time);
        assertNotEquals(BuildEvidence.inventoryHash(before),BuildEvidence.inventoryHash(BuildEvidence.sourceInventory(root)));
    }
    @Test void authoritativeProjectDocumentsInvalidateEvidenceButGeneratedReviewsDoNot() throws Exception {
        write("tools/evidence-sources.txt",Files.readString(BuildEvidence.currentRoot().resolve("tools/evidence-sources.txt")));
        var documents=List.of("README.md","testsweet/README.md","doc/projektbeschreibung.md","doc/architektur.md",
                "doc/leistungsmanagement.md","doc/rechtsgrundlagen.md","doc/regressionstests.md");
        for(String document:documents)write(document,"Fachstand A\n");
        var inventory=BuildEvidence.sourceInventory(root);
        for(String document:documents)assertTrue(inventory.stream().anyMatch(row->document.equals(row.get("path"))),document);
        String baseline=BuildEvidence.inventoryHash(inventory);
        for(String document:documents) {
            Path file=root.resolve(document);var time=Files.getLastModifiedTime(file);
            Files.writeString(file,"Fachstand B\n");Files.setLastModifiedTime(file,time);
            assertNotEquals(baseline,BuildEvidence.inventoryHash(BuildEvidence.sourceInventory(root)),document);
            Files.writeString(file,"Fachstand A\n");Files.setLastModifiedTime(file,time);
        }
        write("doc/codereview/eigener-nachweis.md","Generierter Review\n");
        write("testsweet/logs/run-owned/run-state.json","{}");
        assertEquals(baseline,BuildEvidence.inventoryHash(BuildEvidence.sourceInventory(root)));
    }
    @Test void ignoredLogsCannotChangeSourceEvidenceAndMissingCompilerStampFailsClosed() throws Exception {
        write("tools/evidence-sources.txt","dir:src/main\ntool:doc/testzertifikat-werkzeug\n");write("src/main/java/A.java","class A {}");
        write("doc/testzertifikat-werkzeug/generate.js","return 1");String before=BuildEvidence.inventoryHash(BuildEvidence.sourceInventory(root));
        write("testsweet/logs/run-failed/result.json","{}");write("doc/testzertifikat-werkzeug/stand.json","{}");
        write("doc/testzertifikat-werkzeug/__pycache__/x.pyc","cache");
        assertEquals(before,BuildEvidence.inventoryHash(BuildEvidence.sourceInventory(root)));
        assertEquals(false,BuildEvidence.capture(root).get("sourceArtifactsMatch"));assertFalse(BuildEvidence.matches(BuildEvidence.capture(root),root));
    }

    @Test void replacingBuildOnDiskCannotChangeIdentityOfLoadedProcess() throws Exception {
        Path classes=root.resolve("loaded-classes"), target=classes.resolve("de/allgemein/testsweet/BuildEvidence.class");
        Files.createDirectories(target.getParent());
        try(var input=BuildEvidence.class.getResourceAsStream("BuildEvidence.class")){Files.copy(input,target);}
        String cp=classes+java.io.File.pathSeparator+System.getProperty("surefire.test.class.path",System.getProperty("java.class.path"));
        Process process=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java").toString(),"-cp",cp,
                LoadedBuildProbe.class.getName(),BuildEvidence.currentRoot().toString(),target.toString())
                .redirectErrorStream(true).redirectOutput(root.resolve("probe.log").toFile()).start();
        assertTrue(process.waitFor(30,java.util.concurrent.TimeUnit.SECONDS));
        assertEquals(0,process.exitValue(),Files.readString(root.resolve("probe.log")));
    }
    public static class LoadedBuildProbe {
        public static void main(String[] args) throws Exception {
            Path repo=Path.of(args[0]),file=Path.of(args[1]);
            BuildEvidence.initialize();
            if(!Boolean.TRUE.equals(BuildEvidence.capture(repo).get("processBuildUnchanged")))throw new AssertionError("Startbuild fehlt");
            Files.write(file,new byte[]{42},StandardOpenOption.APPEND);
            Map<String,Object> changed=BuildEvidence.capture(repo);
            if(!Boolean.FALSE.equals(changed.get("processBuildUnchanged")) || Boolean.TRUE.equals(changed.get("sourceArtifactsMatch")))
                throw new AssertionError("Nachträglich ersetzter Build darf kein geladener Build sein");
        }
    }
    @Test void largeGitStatusOutputCannotBlockTheIntegrityCheck() throws Exception {
        Process init=new ProcessBuilder("git","init","-q",root.toString()).redirectErrorStream(true).start();
        assertTrue(init.waitFor(10,java.util.concurrent.TimeUnit.SECONDS));assertEquals(0,init.exitValue());
        String suffix="x".repeat(150)+".txt";
        for(int n=0;n<400;n++)Files.writeString(root.resolve(String.format("%04d-",n)+suffix),"test");
        String status=BuildEvidence.git(root,"status","--porcelain");
        assertTrue(status.length()>60000,"Git-Ausgabe darf am Pipepuffer nicht verloren gehen");
        assertTrue(status.contains("0000-"));assertTrue(status.contains("0399-"));
    }
}
