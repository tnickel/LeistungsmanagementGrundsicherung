import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;

/** JDK-Werkzeug ohne Zusatzbibliothek: bindet Builds und JUnit-Läufe an ihre Quellen. */
public class BuildStamp {
    public static void main(String[] args) throws Exception {
        if (args.length < 3) throw new IllegalArgumentException("stamp|test-begin|test-finish repo main|testsweet [Maven-Repository]");
        String action=args[0], module=args[2];
        Path repo=Path.of(args[1]).toAbsolutePath().normalize();
        Path moduleRoot=module.equals("main")?repo:repo.resolve("testsweet");
        Path target=moduleRoot.resolve("target"); Files.createDirectories(target);
        Path mavenRepo=args.length>3?Path.of(args[3]):Path.of(System.getProperty("user.home"),".m2","repository");
        switch(action) {
            case "stamp" -> stamp(repo,module);
            case "test-begin" -> begin(repo,module,mavenRepo);
            case "test-finish" -> finish(repo,module,mavenRepo);
            default -> throw new IllegalArgumentException("Unbekannte Aktion");
        }
    }
    static void stamp(Path repo,String module) throws Exception {
        Path moduleRoot=module.equals("main")?repo:repo.resolve("testsweet");
        String name=module.equals("main")?"product":"testsweet";
        Path classes=moduleRoot.resolve("target/classes"); Files.createDirectories(classes.resolve("META-INF"));
        List<Map<String,Object>> sources=moduleSources(repo,module);
        Map<String,Object> stamp=new LinkedHashMap<>();
        stamp.put("schemaVersion",1); stamp.put("name",name); stamp.put("sourceSha256",digest(sources));
        stamp.put("sourceInventory",sources); stamp.put("classesSha256",classesHash(classes));
        stamp.put("compiledAt",Instant.now().toString());
        atomic(classes.resolve("META-INF/"+name+"-build.json"),json(stamp));
        Properties props=new Properties(); props.setProperty("sourceSha256",digest(sources));
        props.setProperty("classesSha256",String.valueOf(stamp.get("classesSha256")));
        try(var out=Files.newOutputStream(classes.resolve("META-INF/"+name+"-build.properties"))) { props.store(out,"Compiler-Nachweis"); }
    }
    static void begin(Path repo,String module,Path mavenRepo) throws Exception {
        Path target=target(repo,module), reports=target.resolve("surefire-reports");
        Path previousActive=target.resolve("test-evidence-active.properties");
        if(Files.isRegularFile(previousActive)) {
            Properties previousRun=new Properties();
            try(var in=Files.newInputStream(previousActive)) {previousRun.load(in);}
            // Surefire-Fehler/Prozessabbruch erreichen den FINISH-Hook nicht.
            // Ihre XML-Belege müssen vor dem nächsten Lauf dauerhaft erhalten
            // bleiben, auch wenn anschließend nur ein anderer Einzeltest läuft.
            if(!Boolean.parseBoolean(previousRun.getProperty("finished","false")))
                finish(repo,module,mavenRepo,true);
        }
        String id=UUID.randomUUID().toString(), started=Instant.now().toString();
        Properties active=new Properties(); active.setProperty("runId",id); active.setProperty("startedAt",started);
        List<Map<String,Object>> previous=new ArrayList<>();
        if(Files.isDirectory(reports)) try(var files=Files.list(reports)) {
            for(Path file:files.filter(BuildStamp::isReport).sorted().toList()) {
                String key=file.getFileName().toString(), state=Files.getLastModifiedTime(file).toMillis()+":"+hash(Files.readAllBytes(file));
                active.setProperty("report."+key,state); previous.add(Map.of("name",key,"signature",state));
            }
        }
        List<Map<String,Object>> inventory=sources(repo);
        active.setProperty("sourceSha256",digest(inventory));
        try(var out=Files.newOutputStream(target.resolve("test-evidence-active.properties"))) { active.store(out,"JUnit Laufbeginn"); }
        Map<String,Object> marker=baseMarker(repo,module,id,started);
        marker.put("state","RUNNING"); marker.put("sourceSha256",digest(inventory));
        marker.put("sourceInventory",inventory); marker.put("previousReports",previous);
        marker.put("liveReportsDirectory",relative(repo,reports));
        writeMarker(repo,module,id,marker);
    }
    static void finish(Path repo,String module,Path mavenRepo) throws Exception {
        finish(repo,module,mavenRepo,false);
    }
    static void finish(Path repo,String module,Path mavenRepo,boolean interrupted) throws Exception {
        Path target=target(repo,module), reports=target.resolve("surefire-reports");
        Properties active=new Properties();
        try(var in=Files.newInputStream(target.resolve("test-evidence-active.properties"))) { active.load(in); }
        String id=active.getProperty("runId"), started=active.getProperty("startedAt");
        Path snapshots=target.resolve("test-evidence-history").resolve(id); Files.createDirectories(snapshots);
        List<Map<String,Object>> results=new ArrayList<>();
        if(Files.isDirectory(reports)) try(var files=Files.list(reports)) {
            for(Path file:files.filter(BuildStamp::isReport).sorted().toList()) {
                String name=file.getFileName().toString(), hash=hash(Files.readAllBytes(file));
                String signature=Files.getLastModifiedTime(file).toMillis()+":"+hash;
                if(signature.equals(active.getProperty("report."+name))) continue;
                Path copy=snapshots.resolve(name); Files.copy(file,copy,StandardCopyOption.REPLACE_EXISTING);
                results.add(Map.of("path",relative(repo,copy),"sha256",hash));
            }
        }
        List<Map<String,Object>> inventory=sources(repo);
        boolean unchanged=digest(inventory).equals(active.getProperty("sourceSha256"));
        Map<String,Object> marker=baseMarker(repo,module,id,started);
        marker.put("completedAt",Instant.now().toString());
        marker.put("state",unchanged&&!results.isEmpty()?(interrupted?"INTERRUPTED":"COMPLETED"):"UNVERIFIED");
        marker.put("interrupted",interrupted);
        marker.put("sourceSha256",digest(inventory)); marker.put("sourceInventory",inventory); marker.put("reports",results);
        marker.put("buildEvidence",capture(repo,module,mavenRepo));
        if(!unchanged) marker.put("message","Quellen wurden während der Testausführung verändert");
        else if(results.isEmpty()) marker.put("message","Keine neuen JUnit-Berichte; Tests ausgelassen oder nicht ausgeführt");
        writeMarker(repo,module,id,marker);
        active.setProperty("finished","true");
        try(var out=Files.newOutputStream(target.resolve("test-evidence-active.properties"))) {active.store(out,"JUnit Lauf archiviert");}
    }
    static Map<String,Object> baseMarker(Path repo,String module,String id,String started) throws Exception {
        Map<String,Object> out=new LinkedHashMap<>();
        out.put("schemaVersion",1); out.put("kind","JUNIT_RUN"); out.put("module",module); out.put("runId",id);
        out.put("startedAt",started); out.put("expectedClasses",expectedClasses(repo,module)); return out;
    }
    static void writeMarker(Path repo,String module,String id,Map<String,Object> marker) throws Exception {
        Path target=target(repo,module),history=target.resolve("test-evidence-history"); Files.createDirectories(history);
        atomic(history.resolve(id+".json"),json(marker)); atomic(target.resolve("test-evidence.json"),json(marker));
    }
    static Map<String,Object> capture(Path repo,String module,Path mavenRepo) throws Exception {
        List<Map<String,Object>> inventory=sources(repo),artifacts=new ArrayList<>();
        boolean matches=true;
        if(module.equals("main")) artifacts.add(artifact(repo,"product",repo.resolve("target/classes")));
        else {
            String version=version(repo.resolve("pom.xml"));
            artifacts.add(artifact(repo,"product",mavenRepo.resolve("de/allgemein/transaktionssteuerung/"+version+"/transaktionssteuerung-"+version+".jar")));
            artifacts.add(artifact(repo,"testsweet",repo.resolve("testsweet/target/classes")));
        }
        for(var a:artifacts) matches&=Boolean.TRUE.equals(a.get("sourceMatch"));
        Map<String,Object> out=new LinkedHashMap<>(); out.put("schemaVersion",1); out.put("sourceSha256",digest(inventory));
        out.put("sourceInventory",inventory); out.put("artifacts",artifacts); out.put("sourceArtifactsMatch",matches);
        return out;
    }
    static Map<String,Object> artifact(Path repo,String name,Path path) throws Exception {
        boolean jar=path.toString().endsWith(".jar");
        Map<String,Object> out=new LinkedHashMap<>(); out.put("name",name);out.put("path",path.toAbsolutePath().normalize().toString());out.put("kind",jar?"jar":"classes");
        if(!Files.exists(path)) {out.put("sha256","");out.put("classesSha256","");out.put("sourceMatch",false);return out;}
        String classes=jar?jarClassesHash(path):classesHash(path);
        Properties stamp=new Properties();
        if(jar) try(var zip=new ZipFile(path.toFile())) {var entry=zip.getEntry("META-INF/"+name+"-build.properties");if(entry!=null)try(var in=zip.getInputStream(entry)){stamp.load(in);}}
        else {Path file=path.resolve("META-INF/"+name+"-build.properties");if(Files.isRegularFile(file))try(var in=Files.newInputStream(file)){stamp.load(in);}}
        out.put("sha256",jar?hash(Files.readAllBytes(path)):classes);out.put("classesSha256",classes);
        out.put("sourceMatch",digest(moduleSources(repo,name.equals("product")?"main":"testsweet")).equals(stamp.getProperty("sourceSha256"))&&classes.equals(stamp.getProperty("classesSha256")));
        return out;
    }
    static String version(Path pom) throws Exception {
        var m=Pattern.compile("<version>([^<]+)</version>").matcher(Files.readString(pom));
        if(!m.find())throw new IOException("Projektversion fehlt");return m.group(1).trim();
    }
    static List<String> expectedClasses(Path repo,String module) throws Exception {
        Path testRoot=(module.equals("main")?repo:repo.resolve("testsweet")).resolve("src/test/java");
        if(!Files.isDirectory(testRoot))return List.of();
        List<String> names=new ArrayList<>();
        try(var files=Files.walk(testRoot)) {for(Path f:files.filter(Files::isRegularFile).filter(p->p.toString().endsWith(".java")).sorted().toList()) {
            String name=f.getFileName().toString().replaceFirst("\\.java$","");
            if(!(name.startsWith("Test")||name.endsWith("Test")||name.endsWith("Tests")||name.endsWith("TestCase")))continue;
            var m=Pattern.compile("(?m)^package\\s+([^;]+);").matcher(Files.readString(f));names.add((m.find()?m.group(1)+".":"")+name);
        }} return names;
    }
    static List<Map<String,Object>> moduleSources(Path repo,String module) throws Exception {
        String prefix=module.equals("main")?"":"testsweet/";
        return inventory(repo,List.of("dir:"+prefix+"src/main","file:"+prefix+"pom.xml","file:tools/BuildStamp.java","file:tools/evidence-sources.txt"));
    }
    static List<Map<String,Object>> sources(Path repo) throws Exception {
        Path config=repo.resolve("tools/evidence-sources.txt");
        if(!Files.isRegularFile(config))throw new IOException("Quellinventar fehlt");
        return inventory(repo,Files.readAllLines(config,StandardCharsets.UTF_8));
    }
    static List<Map<String,Object>> inventory(Path repo,List<String> rules) throws Exception {
        SortedMap<String,Path> files=new TreeMap<>();
        for(String line:rules) {
            line=line.strip();if(line.isBlank()||line.startsWith("#"))continue;
            int colon=line.indexOf(':');if(colon<0)throw new IOException("Ungültige Quellregel");
            String kind=line.substring(0,colon),name=line.substring(colon+1);Path entry=repo.resolve(name).normalize();
            if(!entry.startsWith(repo))throw new IOException("Quellregel verlässt Repository");
            if(!Files.exists(entry))continue;
            if(Files.isRegularFile(entry))files.put(relative(repo,entry),entry);
            else try(var found=Files.walk(entry)) {for(Path f:found.filter(Files::isRegularFile).toList()) {
                String rel=relative(repo,f);if(rel.contains("/__pycache__/")||rel.endsWith(".pyc"))continue;
                if(kind.equals("tool")&&!toolSource(f.getFileName().toString()))continue;
                if(Files.isSymbolicLink(f)||!f.toRealPath().startsWith(repo.toRealPath()))throw new IOException("Quellpfad verlässt Repository");
                files.put(rel,f);
            }}
        }
        List<Map<String,Object>> out=new ArrayList<>();for(var e:files.entrySet())out.add(Map.of("path",e.getKey(),"sha256",hash(Files.readAllBytes(e.getValue()))));return out;
    }
    static boolean toolSource(String name) {return name.endsWith(".js")||name.endsWith(".py")||name.endsWith(".bat")||Set.of("llm-pruefauftrag.txt","gesetzesgrundlagen-2026.md","llm-response-contract.json").contains(name);}
    static boolean runtimeFile(String name) {
        return !name.equals("META-INF/MANIFEST.MF") && !name.startsWith("META-INF/maven/")
                && !name.matches("META-INF/(product|testsweet)-build\\.(json|properties)");
    }
    static String classesHash(Path directory) throws Exception {
        List<Map<String,Object>> entries=new ArrayList<>();if(Files.isDirectory(directory))try(var files=Files.walk(directory)) {for(Path f:files.filter(Files::isRegularFile).filter(p->runtimeFile(relative(directory,p))).sorted(Comparator.comparing(p->relative(directory,p))).toList())entries.add(Map.of("path",relative(directory,f),"sha256",hash(Files.readAllBytes(f))));}return digest(entries);
    }
    static String jarClassesHash(Path file) throws Exception {
        List<Map<String,Object>> entries=new ArrayList<>();try(var zip=new ZipFile(file.toFile())) {for(var e:zip.stream().filter(x->!x.isDirectory()&&runtimeFile(x.getName())).sorted(Comparator.comparing(java.util.zip.ZipEntry::getName)).toList())try(var in=zip.getInputStream(e)){entries.add(Map.of("path",e.getName(),"sha256",hash(in.readAllBytes())));}}return digest(entries);
    }
    static String digest(List<Map<String,Object>> inventory) throws Exception {StringBuilder text=new StringBuilder();for(var e:inventory)text.append(e.get("path")).append('\0').append(e.get("sha256")).append('\n');return hash(text.toString().getBytes(StandardCharsets.UTF_8));}
    static String hash(byte[] bytes) throws Exception {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    static boolean isReport(Path p) {String n=p.getFileName().toString();return Files.isRegularFile(p)&&n.startsWith("TEST-")&&n.endsWith(".xml");}
    static Path target(Path repo,String module) {return (module.equals("main")?repo:repo.resolve("testsweet")).resolve("target");}
    static String relative(Path base,Path file) {return base.relativize(file.toAbsolutePath().normalize()).toString().replace('\\','/');}
    static void atomic(Path path,String content) throws Exception {Files.createDirectories(path.getParent());Path tmp=Files.createTempFile(path.getParent(),".evidence-",".tmp");try{Files.writeString(tmp,content,StandardCharsets.UTF_8);try{Files.move(tmp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(tmp,path,StandardCopyOption.REPLACE_EXISTING);}}finally{Files.deleteIfExists(tmp);}}
    static String json(Object value) {
        if(value==null)return "null";if(value instanceof Boolean||value instanceof Number)return value.toString();
        if(value instanceof Map<?,?> m){List<String> parts=new ArrayList<>();for(var e:m.entrySet())parts.add(json(e.getKey().toString())+":"+json(e.getValue()));return "{"+String.join(",",parts)+"}";}
        if(value instanceof Collection<?> c)return "["+String.join(",",c.stream().map(BuildStamp::json).toList())+"]";
        StringBuilder s=new StringBuilder("\"");for(char ch:value.toString().toCharArray())switch(ch){case '"'->s.append("\\\"");case '\\'->s.append("\\\\");case '\n'->s.append("\\n");case '\r'->s.append("\\r");case '\t'->s.append("\\t");default->{if(ch<32)s.append(String.format("\\u%04x",(int)ch));else s.append(ch);}}return s.append('"').toString();
    }
}
