package de.allgemein.testsweet;

import de.allgemein.lmg.calc.Leistungsberechnung;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipFile;

/** Quellen- und Artefaktbindung; Compilerstempel entstehen durch tools/BuildStamp.java. */
final class BuildEvidence {
    // Eine laufende JVM darf einen später überschriebenen Build auf der Platte
    // niemals als ihren eigenen ausgeben. Initialisierung erfolgt am Prozesseinstieg.
    private static final Map<String,String> STARTUP_ARTIFACTS = startupArtifacts();
    private BuildEvidence() { }

    static void initialize() { /* Erzwingt den unveränderlichen Start-Snapshot. */ }
    private static Map<String,String> startupArtifacts() {
        try { return Map.of("product", locationHash(location(Leistungsberechnung.class)),
                "testsweet", locationHash(location(BuildEvidence.class))); }
        catch (Exception error) { return Map.of(); }
    }
    private static String locationHash(Path path) throws Exception {
        return Files.isRegularFile(path) ? hash(Files.readAllBytes(path)) : classesHash(path);
    }

    static Path currentRoot() {
        Path cwd=Path.of("").toAbsolutePath().normalize();
        for(Path p=cwd;p!=null;p=p.getParent())if(Files.isRegularFile(p.resolve("pom.xml"))&&Files.isDirectory(p.resolve("testsweet")))return p;
        return cwd;
    }
    static Map<String,Object> capture(Path root) {
        return capture(root,true);
    }
    private static Map<String,Object> capture(Path root,boolean provenance) {
        Path repo=root.toAbsolutePath().normalize();
        try {
            List<Map<String,Object>> sources=sourceInventory(repo),artifacts=List.of(
                artifact(repo,"product",location(Leistungsberechnung.class)),artifact(repo,"testsweet",location(BuildEvidence.class)));
            Map<String,Object> out=new LinkedHashMap<>();out.put("schemaVersion",1);out.put("sourceSha256",inventoryHash(sources));
            out.put("sourceInventory",sources);out.put("artifacts",artifacts);
            boolean sameProcessBuild = artifacts.size() == STARTUP_ARTIFACTS.size() && artifacts.stream()
                    .allMatch(a -> Objects.equals(a.get("sha256"),STARTUP_ARTIFACTS.get(a.get("name"))));
            out.put("processBuildUnchanged",sameProcessBuild);
            out.put("sourceArtifactsMatch",sameProcessBuild&&!sources.isEmpty()&&artifacts.stream().allMatch(a->Boolean.TRUE.equals(a.get("sourceMatch"))));
            if (!sameProcessBuild) out.put("message","Build wurde seit Prozessstart ersetzt; testsweet neu starten");
            if (provenance) { out.put("commit",git(repo,"rev-parse","HEAD").trim());out.put("dirty",!git(repo,"status","--porcelain").isBlank()); }
            return Map.copyOf(out);
        } catch(Exception e) {return Map.of("schemaVersion",1,"sourceArtifactsMatch",false,"sourceSha256","","message","Buildnachweis nicht lesbar: "+e.getClass().getSimpleName());}
    }
    static boolean matches(Map<?,?> evidence,Path root) {
        if(!Boolean.TRUE.equals(evidence.get("sourceArtifactsMatch")))return false;
        Map<String,Object> current=capture(root,false);
        if(!Boolean.TRUE.equals(current.get("sourceArtifactsMatch"))||!Objects.equals(evidence.get("sourceSha256"),current.get("sourceSha256")))return false;
        return artifactIds(evidence.get("artifacts")).equals(artifactIds(current.get("artifacts")));
    }
    private static Map<String,String> artifactIds(Object raw) {
        if(!(raw instanceof List<?> list))return Map.of();Map<String,String> ids=new TreeMap<>();
        for(Object row:list)if(row instanceof Map<?,?> a)ids.put(String.valueOf(a.get("name")),a.get("sha256")+":"+a.get("classesSha256"));
        return ids;
    }
    static List<Map<String,Object>> sourceInventory(Path root) throws Exception {
        Path rules=root.resolve("tools/evidence-sources.txt");if(!Files.isRegularFile(rules))throw new java.io.IOException("Quellinventar fehlt");
        return inventory(root,Files.readAllLines(rules,StandardCharsets.UTF_8));
    }
    static List<Map<String,Object>> moduleSourceInventory(Path root,String name) throws Exception {
        String prefix=name.equals("product")?"":"testsweet/";
        return inventory(root,List.of("dir:"+prefix+"src/main","file:"+prefix+"pom.xml","file:tools/BuildStamp.java","file:tools/evidence-sources.txt"));
    }
    private static List<Map<String,Object>> inventory(Path root,List<String> rules) throws Exception {
        SortedMap<String,Path> files=new TreeMap<>();
        for(String line:rules) {
            line=line.strip();if(line.isBlank()||line.startsWith("#"))continue;
            int split=line.indexOf(':');if(split<0)throw new java.io.IOException("Ungültige Quellregel");
            String kind=line.substring(0,split);Path entry=root.resolve(line.substring(split+1)).normalize();
            if(!entry.startsWith(root))throw new java.io.IOException("Quellpfad verlässt Repository");if(!Files.exists(entry))continue;
            if(Files.isRegularFile(entry))files.put(relative(root,entry),entry);
            else try(var found=Files.walk(entry)) {for(Path file:found.filter(Files::isRegularFile).toList()) {
                String rel=relative(root,file);if(rel.contains("/__pycache__/")||rel.endsWith(".pyc"))continue;
                String n=file.getFileName().toString();
                if(kind.equals("tool")&&!(n.endsWith(".js")||n.endsWith(".py")||n.endsWith(".bat")||Set.of("llm-pruefauftrag.txt","gesetzesgrundlagen-2026.md","llm-response-contract.json").contains(n)))continue;
                if(Files.isSymbolicLink(file)||!file.toRealPath().startsWith(root.toRealPath()))throw new java.io.IOException("Quellpfad verlässt Repository");files.put(rel,file);
            }}
        }
        List<Map<String,Object>> result=new ArrayList<>();for(var entry:files.entrySet())result.add(Map.of("path",entry.getKey(),"sha256",hash(Files.readAllBytes(entry.getValue()))));return result;
    }
    private static Path location(Class<?> type) throws Exception {return Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toAbsolutePath().normalize();}
    private static Map<String,Object> artifact(Path root,String name,Path location) throws Exception {
        boolean jar=Files.isRegularFile(location);String classes=jar?jarClassesHash(location):classesHash(location);
        Properties stamp=new Properties();
        if(jar)try(var zip=new ZipFile(location.toFile())) {var entry=zip.getEntry("META-INF/"+name+"-build.properties");if(entry!=null)try(var in=zip.getInputStream(entry)){stamp.load(in);}}
        else {Path file=location.resolve("META-INF/"+name+"-build.properties");if(Files.isRegularFile(file))try(var in=Files.newInputStream(file)){stamp.load(in);}}
        boolean match=inventoryHash(moduleSourceInventory(root,name)).equals(stamp.getProperty("sourceSha256"))&&classes.equals(stamp.getProperty("classesSha256"));
        return Map.of("name",name,"kind",jar?"jar":"classes","path",location.toString(),"sha256",jar?hash(Files.readAllBytes(location)):classes,"classesSha256",classes,"sourceMatch",match);
    }
    static boolean runtimeFile(String name) {
        return !name.equals("META-INF/MANIFEST.MF") && !name.startsWith("META-INF/maven/")
                && !name.matches("META-INF/(product|testsweet)-build\\.(json|properties)");
    }
    static String classesHash(Path directory) throws Exception {
        List<Map<String,Object>> entries=new ArrayList<>();if(Files.isDirectory(directory))try(var files=Files.walk(directory)) {for(Path f:files.filter(Files::isRegularFile).filter(p->runtimeFile(relative(directory,p))).sorted(Comparator.comparing(p->relative(directory,p))).toList())entries.add(Map.of("path",relative(directory,f),"sha256",hash(Files.readAllBytes(f))));}return inventoryHash(entries);
    }
    private static String jarClassesHash(Path file) throws Exception {
        List<Map<String,Object>> entries=new ArrayList<>();try(var zip=new ZipFile(file.toFile())) {for(var e:zip.stream().filter(x->!x.isDirectory()&&runtimeFile(x.getName())).sorted(Comparator.comparing(java.util.zip.ZipEntry::getName)).toList())try(var in=zip.getInputStream(e)){entries.add(Map.of("path",e.getName(),"sha256",hash(in.readAllBytes())));}}return inventoryHash(entries);
    }
    static String inventoryHash(List<Map<String,Object>> inventory) throws Exception {StringBuilder text=new StringBuilder();for(var e:inventory)text.append(e.get("path")).append('\0').append(e.get("sha256")).append('\n');return hash(text.toString().getBytes(StandardCharsets.UTF_8));}
    static String hash(byte[] raw) throws Exception {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw));}
    private static String relative(Path base,Path file) {return base.relativize(file.toAbsolutePath().normalize()).toString().replace('\\','/');}
    static String git(Path root,String...args) {
        List<String> command=new ArrayList<>(List.of("git","-C",root.toString()));command.addAll(List.of(args));
        Path output=null;Process process=null;
        try {
            output=Files.createTempFile("testsweet-git-", ".out");
            process=new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).redirectOutput(output.toFile()).start();
            if(!process.waitFor(5,TimeUnit.SECONDS))return "";
            return process.exitValue()==0?Files.readString(output,StandardCharsets.UTF_8):"";
        }catch(InterruptedException error){Thread.currentThread().interrupt();return "";}
        catch(Exception error){return "";}
        finally {
            if(process!=null&&process.isAlive()) { process.descendants().forEach(ProcessHandle::destroyForcibly);process.destroyForcibly(); }
            if(output!=null)try{Files.deleteIfExists(output);}catch(java.io.IOException ignored){ }
        }
    }
}
