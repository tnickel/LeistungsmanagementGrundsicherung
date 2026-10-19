package de.allgemein.testsweet;

import java.io.*;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import javax.xml.parsers.DocumentBuilderFactory;

/** Startet Builds und Prüfungen außerhalb der alten GUI-JVM. Keine geladenen Klassen werden als Neubuild ausgegeben. */
public class CertificateSuiteService implements AutoCloseable {
    private final Path root;
    private final Map<String,String> environment;
    private final ExecutorService executor=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"certificate-suite-controller");t.setDaemon(true);return t;});
    private volatile Process activeProcess;
    private volatile RuntimeException loggingFailure;
    private volatile boolean running;
    private volatile Path directory;
    private FileChannel lockChannel;
    private FileLock lock;
    private boolean closed;
    @FunctionalInterface interface CommandRunner { void run(String label,List<String> command,Map<String,String> environment,Path directory) throws Exception; }
    private final CommandRunner commandRunner;
    private long checkedCertificateAt;
    private boolean checkedCertificateCurrent;
    public CertificateSuiteService(Path root){this(root,System.getenv());}
    public CertificateSuiteService(Path root,Simulator simulator){this(root,withDatabase(System.getenv(),simulator));}
    private static Map<String,String> withDatabase(Map<String,String> env,Simulator simulator){Map<String,String> copy=new HashMap<>(env);copy.put("TX_URL",simulator.dbUrl);copy.put("TX_USER",simulator.dbUser);copy.put("TX_PASS",simulator.dbPass);return copy;}
    CertificateSuiteService(Path root,Map<String,String> environment){this(root,environment,null);}
    CertificateSuiteService(Path root,Map<String,String> environment,CommandRunner runner){this.root=root.toAbsolutePath().normalize();this.environment=Map.copyOf(environment);this.commandRunner=runner;}
    public boolean running(){
        if(running)return true;
        try{Path marker=root.resolve("testsweet/logs/suite-current.json");if(!Files.isRegularFile(marker))return false;Path d=Path.of(String.valueOf(CertificateSuiteState.read(marker).get("directory"))).toAbsolutePath().normalize();if(!d.startsWith(root.resolve("testsweet/logs")))return false;Map<String,Object> data=CertificateSuiteState.read(d.resolve("status.json"));return Boolean.TRUE.equals(data.get("running"))&&controllerAlive(data);}
        catch(Exception ignored){return false;}
    }
    public synchronized Map<String,Object> start(){
        return start(0);
    }
    public synchronized Map<String,Object> start(int requestedCases){
        if(closed)throw new IllegalStateException("Zertifikatsdienst ist beendet");
        if(running)throw new IllegalStateException("Ein vollständiger Zertifikatslauf läuft bereits");
        try {
            acquireLock();
            if(!environment.getOrDefault("SWEET_BASE_URL","").isBlank()||!System.getProperty("sweet.baseurl","").isBlank())throw new IllegalStateException("Für Zertifikatstests darf kein externes Webziel (SWEET_BASE_URL/sweet.baseurl) gesetzt sein. Die Suite muss den frisch gebauten eigenen Server prüfen.");
            LlmConfig.Settings llm=new LlmConfig(root,environment).read();
            if(llm.apiKey().isBlank())throw new IllegalStateException("Zuerst den GLM-Zugang über das Zahnrad konfigurieren. Es wurde kein Test gestartet.");
            // Vorabprüfung: Ohne Testdatenbank endete jeder einzelne Suite-Lauf im Abbruch.
            // Besser der Gesamtablauf gar nicht erst anstoßen, sondern klar melden, was zu tun ist.
            DbErreichbarkeit.Ergebnis db=DbErreichbarkeit.pruefeAusUmgebung(environment);
            if(!db.erreichbar())throw new IllegalStateException(db.hinweis());
            if(requestedCases!=0&&(requestedCases<2||requestedCases>1000))throw new IllegalArgumentException("GLM-Prüffälle müssen zwischen 2 und 1000 liegen");
            String id=UUID.randomUUID().toString();directory=root.resolve("testsweet/logs/certificate-suite-"+id);Files.createDirectories(directory);
            Map<String,Object> initial=new LinkedHashMap<>();
            initial.put("schemaVersion",1);initial.put("jobId",id);initial.put("suiteId",id);initial.put("kind","CERTIFICATE_SUITE");
            initial.put("state","RUNNING");initial.put("running",true);initial.put("startedAt",Instant.now().toString());initial.put("controllerPid",ProcessHandle.current().pid());
            initial.put("controllerStartedAt",ProcessHandle.current().info().startInstant().orElseThrow().toString());
            initial.put("artifactDirectory",directory.toString());initial.put("logPath",directory.resolve("pipeline.log").toString());
            initial.put("sourceSha256",BuildEvidence.inventoryHash(BuildEvidence.sourceInventory(root)));initial.put("llmCases",requestedCases==0?llm.cases():requestedCases);
            new CertificateSuiteState(root,directory,initial).phase(0,"BUILD_MAIN","Hauptprogramm bauen und automatische Tests mit Testdatenbank ausführen");
            CertificateSuiteState.atomic(root.resolve("testsweet/logs/suite-current.json"),Map.of("schemaVersion",1,"jobId",id,"directory",directory.toString(),"startedAt",initial.get("startedAt")));
            running=true;executor.submit(this::execute);return status();
        }catch(Exception e){releaseLock();throw e instanceof IllegalStateException s?s:e instanceof IllegalArgumentException a?a:new IllegalStateException("Zertifikatslauf konnte nicht gestartet werden: "+e.getClass().getSimpleName(),e);}
    }
    public synchronized Map<String,Object> status(){
        try {
            Path d=directory;
            if(d==null){Path marker=root.resolve("testsweet/logs/suite-current.json");if(!Files.exists(marker))return Map.of("state","IDLE","running",false,"message","Noch kein vollständiger Zertifikatslauf gestartet");d=Path.of(String.valueOf(CertificateSuiteState.read(marker).get("directory"))).normalize();}
            if(!d.toAbsolutePath().startsWith(root.resolve("testsweet/logs")))throw new IOException("Ungültiger Laufpfad");
            Map<String,Object> data=CertificateSuiteState.read(d.resolve("status.json"));
            if(Boolean.TRUE.equals(data.get("running"))&&!running&&!controllerAlive(data)){
                data.putAll(Map.of("state","FAILED","running",false,"finishedAt",Instant.now().toString(),"message","Der Zertifikatslauf wurde durch einen Prozessabbruch unterbrochen. Vollständigen Lauf neu starten; Protokoll auswerten.","errorType","INTERRUPTED"));CertificateSuiteState.atomic(d.resolve("status.json"),data);
            }
            if("SUCCESS".equals(data.get("state"))&&!Objects.equals(data.get("sourceSha256"),BuildEvidence.inventoryHash(BuildEvidence.sourceInventory(root)))){
                data.put("certificateCurrent",false);data.put("message","Der erfolgreiche Lauf gehört zu älteren Quellen. Mache alle Zertifikatstests erneut.");
            }else if("SUCCESS".equals(data.get("state"))){
                if(System.nanoTime()-checkedCertificateAt>TimeUnit.SECONDS.toNanos(2)||checkedCertificateAt==0){checkedCertificateCurrent=currentCertificateMatches(data);checkedCertificateAt=System.nanoTime();}
                data.put("certificateCurrent",checkedCertificateCurrent);if(!checkedCertificateCurrent)data.put("message","Das frühere Zertifikat ist nicht mehr aktuell. Mache alle Zertifikatstests erneut.");
            }
            return CertificateSuiteState.publicView(data);
        }catch(Exception error){return Map.of("state","FAILED","running",false,"message","Zertifikatslaufstatus ist nicht lesbar. Protokoll auswerten.","errorType","STATUS_UNREADABLE");}
    }
    private boolean controllerAlive(Map<String,Object> data){Object pid=data.get("controllerPid");return pid instanceof Number n&&ProcessHandle.of(n.longValue()).filter(ProcessHandle::isAlive).flatMap(p->p.info().startInstant()).map(t->t.toString().equals(data.get("controllerStartedAt"))).orElse(false);}
    private boolean currentCertificateMatches(Map<String,Object> data){
        Path output=null;Process process=null;
        try {
            output=Files.createTempFile(root.resolve("testsweet/logs"),".suite-verify-",".json");
            process=new ProcessBuilder("node",root.resolve("doc/testzertifikat-werkzeug/publish-certificate.js").toString(),"--verify",root.toString()).directory(root.toFile()).redirectOutput(output.toFile()).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            if(!process.waitFor(30,TimeUnit.SECONDS)||process.exitValue()!=0)return false;
            Map<String,Object> current=CertificateSuiteState.read(output);return "READY".equals(current.get("state"))&&"BESTANDEN".equals(current.get("verdict"))&&Objects.equals(current.get("certificateId"),data.get("certificateId"));
        }catch(Exception e){return false;}finally{if(process!=null&&process.isAlive()){process.descendants().forEach(ProcessHandle::destroyForcibly);process.destroyForcibly();}if(output!=null)try{Files.deleteIfExists(output);}catch(IOException ignored){}}
    }
    public synchronized Map<String,Object> cancel(){
        if(!running)throw new IllegalStateException("Es läuft kein vollständiger Zertifikatslauf");
        try {Files.writeString(directory.resolve("cancel.request"),Instant.now().toString(),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);}catch(FileAlreadyExistsException ignored){}catch(IOException e){throw new IllegalStateException("Abbruch konnte nicht gespeichert werden",e);}
        stopOwnedProcess();
        try {CertificateSuiteState state=new CertificateSuiteState(root,directory,CertificateSuiteState.read(directory.resolve("status.json")));state.update(Map.of("state","CANCELLED","running",false,"finishedAt",Instant.now().toString(),"message","Zertifikatslauf abgebrochen. Es wurde kein gültiger Gesamtnachweis erstellt.","errorType","CANCELLED","failureKind","TECHNICAL"));}catch(IOException e){throw new IllegalStateException("Abbruchstatus nicht speicherbar",e);}
        return status();
    }
    private void execute(){
        try {
            CertificateSuiteState state=new CertificateSuiteState(root,directory,CertificateSuiteState.read(directory.resolve("status.json")));
            Map<String,String> db=new HashMap<>(environment);db.putIfAbsent("TX_URL",System.getProperty("tx.url","jdbc:oracle:thin:@//localhost:1521/FREEPDB1"));db.putIfAbsent("TX_USER",System.getProperty("tx.user","tx_demo"));db.putIfAbsent("TX_PASS",System.getProperty("tx.pass","tx_demo"));
            // Leere Variablen sind keine gültige DB-Konfiguration; niemals versehentlich DB-Tests überspringen.
            for(String key:List.of("TX_URL","TX_USER","TX_PASS"))if(db.get(key)==null||db.get(key).isBlank())throw new IllegalStateException("Testdatenbank-Zugang fehlt. Testdatenbank konfigurieren und vollständigen Lauf neu starten.");
            run(state,"Hauptprogramm: clean install",maven("-q","clean","install"),db,1800);verifyJUnit(root.resolve("target/surefire-reports"),true);
            state.phase(1,"CONFIG_TEST","Konfiguration ohne Umgebungsüberschreibungen prüfen");Map<String,String> isolated=new HashMap<>(environment);isolated.remove("TX_URL");isolated.remove("TX_USER");isolated.remove("TX_PASS");
            run(state,"Isolierter Konfigurationstest",maven("-q","-Dtest=LmgConfigTest","test"),isolated,600);verifyJUnit(root.resolve("target/surefire-reports"));
            state.phase(2,"BUILD_TESTSWEET","testsweet bauen und automatische Tests mit Testdatenbank ausführen");run(state,"testsweet: clean test",maven("-q","-f","testsweet/pom.xml","clean","test"),db,1800);verifyJUnit(root.resolve("testsweet/target/surefire-reports"));
            state.phase(3,"WORKER_BUILD","Frische Prüfumgebung starten");Path cp=directory.resolve("classpath.txt");run(state,"Worker-Classpath",maven("-q","-f","testsweet/pom.xml","dependency:build-classpath","-Dmdep.outputFile="+cp),db,600);
            state.checkSources();String originalClasspath=root.resolve("testsweet/target/classes")+File.pathSeparator+Files.readString(cp,StandardCharsets.UTF_8).trim();
            run(state,"Laufgebundene Runtime kopieren",List.of(javaExecutable(),"-cp",originalClasspath,
                    "de.allgemein.testsweet.CertificateRuntimeSnapshot",root.toString(),directory.toString(),originalClasspath),db,600);
            String classpath=CertificateRuntimeSnapshot.classpath(root,directory);
            state.update(Map.of("runtimeDirectory",directory.resolve("runtime").toString(),
                    "runtimeManifestSha256",BuildEvidence.hash(Files.readAllBytes(directory.resolve("runtime/manifest.json")))));
            run(state,"Zertifikatsprüfungen in laufgebundener frischer JVM",List.of(javaExecutable(),"-Djava.awt.headless=false","-cp",classpath,"de.allgemein.testsweet.SweetMain","certificate-worker",directory.toString()),db,14400);
            Map<String,Object> result=CertificateSuiteState.read(directory.resolve("status.json"));if(!"SUCCESS".equals(result.get("state")))throw new IllegalStateException("Zertifikatsworker hat keinen vollständigen Erfolgsnachweis geliefert");
        }catch(Exception e){
            try {Map<String,Object> data=CertificateSuiteState.read(directory.resolve("status.json"));if(!List.of("FAILED","CANCELLED").contains(data.get("state")))new CertificateSuiteState(root,directory,data).update(Map.of("state",Files.exists(directory.resolve("cancel.request"))?"CANCELLED":"FAILED","running",false,"finishedAt",Instant.now().toString(),"message",safeMessage(e),"errorType",e.getClass().getSimpleName(),"failureKind",junitFailureKind(Instant.parse(String.valueOf(data.get("startedAt"))))));}catch(IOException ignored){}
        }finally{activeProcess=null;running=false;releaseLock();}
    }
    private String safeMessage(Exception e){String text=e.getMessage();if(text==null||text.isBlank())text=e.getClass().getSimpleName();return redact(text);}
    private String redact(String text){for(String name:List.of("TX_PASS","GLM_API_KEY","SWEET_LLM_API_KEY")){String value=environment.get(name);if(value!=null&&!value.isBlank())text=text.replace(value,"[VERDECKT]");}try{String key=new LlmConfig(root,environment).read().apiKey();if(!key.isBlank())text=text.replace(key,"[VERDECKT]");}catch(Exception ignored){}return text;}
    private void run(CertificateSuiteState state,String label,List<String> command,Map<String,String> env,long timeout) throws Exception {
        state.checkSources();log(label+" gestartet");if(commandRunner!=null){commandRunner.run(label,List.copyOf(command),Map.copyOf(env),directory);state.checkSources();return;}ProcessBuilder builder=new ProcessBuilder(command).directory(root.toFile()).redirectErrorStream(true);builder.environment().clear();builder.environment().putAll(env);
        Process p=builder.start();activeProcess=p;loggingFailure=null;
        Thread output=Thread.ofPlatform().daemon().start(()->{try(var reader=p.inputReader(StandardCharsets.UTF_8)){String line;while((line=reader.readLine())!=null)log(redact(line));}catch(IOException e){loggingFailure=new UncheckedIOException(e);p.destroy();}catch(RuntimeException e){loggingFailure=e;p.destroy();}});
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(timeout);
        try {while(!p.waitFor(500,TimeUnit.MILLISECONDS)){state.checkSources();if(loggingFailure!=null)throw loggingFailure;if(System.nanoTime()-deadline>=0)throw new IllegalStateException(label+": Zeitlimit überschritten. Protokoll auswerten.");}output.join(5000);if(output.isAlive())throw new IllegalStateException("Prozessprotokoll wurde nicht vollständig geschlossen");if(loggingFailure!=null)throw loggingFailure;state.checkSources();if(p.exitValue()!=0)throw new IllegalStateException(label+" fehlgeschlagen (Exit "+p.exitValue()+"). Protokoll auswerten.");log(label+" bestanden");}
        finally {if(p.isAlive())stopOwnedProcess();activeProcess=null;}
    }
    private synchronized void log(String line){try{Files.writeString(directory.resolve("pipeline.log"),Instant.now()+" "+line+System.lineSeparator(),StandardCharsets.UTF_8,StandardOpenOption.CREATE,StandardOpenOption.APPEND);}catch(IOException e){throw new UncheckedIOException(e);}}
    private void acquireLock() throws IOException {Files.createDirectories(root.resolve("testsweet/logs"));lockChannel=FileChannel.open(root.resolve("testsweet/logs/certificate-suite.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);try{lock=lockChannel.tryLock();}catch(OverlappingFileLockException e){lock=null;}if(lock==null)throw new IllegalStateException("Ein vollständiger Zertifikatslauf läuft bereits in einer anderen Instanz");}
    private synchronized void releaseLock(){try{if(lock!=null)lock.release();}catch(IOException ignored){}try{if(lockChannel!=null)lockChannel.close();}catch(IOException ignored){}lock=null;lockChannel=null;}
    private void stopOwnedProcess(){Process p=activeProcess;if(p==null){BrowserProzesse.raeumeVerwaisteFensterAuf();return;}List<ProcessHandle> children=p.descendants().toList();children.reversed().forEach(ProcessHandle::destroy);p.destroy();for(ProcessHandle child:children)if(child.isAlive())child.destroyForcibly();if(p.isAlive())p.destroyForcibly();BrowserProzesse.raeumeVerwaisteFensterAuf();}
    static List<String> maven(String... args){List<String> command=new ArrayList<>();if(System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win"))command.addAll(List.of("cmd.exe","/d","/c","mvn.cmd"));else command.add("mvn");command.addAll(List.of(args));return command;}
    static String javaExecutable(){return Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")?"java.exe":"java").toString();}
    static void verifyJUnit(Path reports) throws Exception {
        verifyJUnit(reports,false);
    }
    private String junitFailureKind(Instant startedAt){
        return hasFreshJUnitFailure(root.resolve("target/surefire-reports"),startedAt)||hasFreshJUnitFailure(root.resolve("testsweet/target/surefire-reports"),startedAt)?"TEST_FAILURE":"TECHNICAL";
    }
    static boolean hasFreshJUnitFailure(Path reports,Instant startedAt){
        if(!Files.isDirectory(reports))return false;
        try(var files=Files.list(reports)){
            DocumentBuilderFactory factory=DocumentBuilderFactory.newInstance();factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);factory.setExpandEntityReferences(false);
            for(Path file:files.filter(p->p.getFileName().toString().startsWith("TEST-")&&p.toString().endsWith(".xml")).toList()){
                if(Files.getLastModifiedTime(file).toInstant().isBefore(startedAt))continue;
                try{var suite=factory.newDocumentBuilder().parse(file.toFile()).getDocumentElement();
                    if(Integer.parseInt(suite.getAttribute("failures"))>0||Integer.parseInt(suite.getAttribute("errors"))>0)return true;
                }catch(Exception ignored){/* Ungültige XML sind fehlende Belege, kein erfundener Fachbefund. */}
            }
        }catch(Exception ignored){/* Fehlender/defekter Nachweis bleibt technisch unbestätigt. */}
        return false;
    }
    private static void verifyJUnit(Path reports,boolean configurationRerunPending) throws Exception {
        DocumentBuilderFactory factory=DocumentBuilderFactory.newInstance();factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);factory.setExpandEntityReferences(false);int tests=0,files=0;
        try(var paths=Files.list(reports)){for(Path file:paths.filter(p->p.getFileName().toString().startsWith("TEST-")&&p.toString().endsWith(".xml")).toList()){
            var suite=factory.newDocumentBuilder().parse(file.toFile()).getDocumentElement();files++;tests+=Integer.parseInt(suite.getAttribute("tests"));for(String attr:List.of("failures","errors","skipped"))if(Integer.parseInt(suite.getAttribute(attr))!=0){
                boolean knownConfigurationSkip=configurationRerunPending&&"skipped".equals(attr)&&Integer.parseInt(suite.getAttribute(attr))==1&&"de.allgemein.lmg.config.LmgConfigTest".equals(suite.getAttribute("name"));
                if(!knownConfigurationSkip)throw new IllegalStateException("Automatische Tests sind fehlgeschlagen oder übersprungen. Testdatenbank prüfen und Protokoll auswerten.");
            }
        }}if(files==0||tests==0)throw new IllegalStateException("JUnit-Prüfnachweise fehlen");
    }
    @Override public synchronized void close(){closed=true;if(running)cancel();executor.shutdownNow();}
}
