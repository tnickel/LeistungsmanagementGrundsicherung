package de.allgemein.testsweet;

import de.allgemein.lmg.calc.Leistungsberechnung;
import de.allgemein.lmg.web.Json;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Eigene Runtime je Zertifikatslauf; bestehende JUnit-Belege bleiben strikt gebunden. */
final class CertificateRuntimeSnapshot {
    private static final long CHECK_INTERVAL = TimeUnit.SECONDS.toNanos(15);
    private CertificateRuntimeSnapshot() { }

    /** Läuft ausdrücklich in einer frischen JVM nach beiden Builds. */
    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("Runtime-Snapshot benötigt Projekt, Suite und Classpath");
        Path root = Path.of(args[0]).toRealPath(), suite = checkedSuite(root, Path.of(args[1]));
        BuildEvidence.initialize();
        Map<String, Object> original = BuildEvidence.capture(root);
        if (!Boolean.TRUE.equals(original.get("sourceArtifactsMatch"))) {
            throw new IllegalStateException("Runtime-Kopie verweigert: frische JVM besitzt keinen gültigen Buildnachweis");
        }
        List<Map<String, Object>> guards = junitGuards(root);
        verifyGuards(guards);
        List<Path> source = Arrays.stream(args[2].split(java.util.regex.Pattern.quote(File.pathSeparator)))
                .map(Path::of).map(p -> p.toAbsolutePath().normalize()).distinct().toList();
        List<Map<String, Object>> entries = copyVerified(source, suite.resolve("runtime"));
        if (!BuildEvidence.matches(original, root)) {
            throw new IllegalStateException("Build wurde während der Runtime-Kopie ersetzt; kein Workerstart");
        }
        verifyGuards(guards);
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("schemaVersion", 1); manifest.put("kind", "CERTIFICATE_RUNTIME");
        manifest.put("createdAt", Instant.now().toString()); manifest.put("sourceSha256", original.get("sourceSha256"));
        manifest.put("entries", entries); manifest.put("originalBuildEvidence", original); manifest.put("guards", guards);
        Files.writeString(suite.resolve("runtime/manifest.json"), Json.write(manifest), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW);
        // Dieselben Prüfungen wie unmittelbar vor dem echten Workerstart.
        classpath(root, suite);
        System.out.println("Laufgebundene Runtime vollständig kopiert und verifiziert (" + entries.size() + " Classpath-Einträge)");
    }

    static Path checkedSuite(Path root, Path suite) throws IOException {
        Path base = root.resolve("testsweet/logs").toRealPath();
        Path actual = suite.toAbsolutePath().normalize();
        if (!actual.startsWith(base) || !actual.getFileName().toString().startsWith("certificate-suite-")
                || !actual.toRealPath().equals(actual)) throw new IOException("Ungültiges Runtime-Suiteverzeichnis");
        return actual;
    }

    /** CREATE_NEW und Vor-/Nachhash verhindern Annahme einer halben oder ersetzten Kopie. */
    static List<Map<String, Object>> copyVerified(List<Path> sources, Path destination) throws Exception {
        if (!destination.getParent().toRealPath().equals(destination.getParent().toAbsolutePath().normalize())) {
            throw new IOException("Runtime-Ziel enthält eine Verknüpfung");
        }
        Files.createDirectory(destination);
        List<Map<String, Object>> entries = new ArrayList<>();
        for (int i = 0; i < sources.size(); i++) {
            Path original = sources.get(i).toAbsolutePath().normalize();
            if (Files.isSymbolicLink(original)) throw new IOException("Verknüpfung im Runtime-Classpath");
            String before = fullHash(original);
            String name = String.format(Locale.ROOT, "entry-%03d%s", i, Files.isDirectory(original) ? "" : ".jar");
            Path copy = destination.resolve(name);
            if (Files.isDirectory(original)) {
                Files.createDirectory(copy);
                try (var files = Files.walk(original)) {
                    for (Path file : files.filter(p -> !p.equals(original)).sorted().toList()) {
                        if (Files.isSymbolicLink(file)) throw new IOException("Verknüpfung im Runtime-Verzeichnis");
                        Path target = copy.resolve(original.relativize(file));
                        if (Files.isDirectory(file)) Files.createDirectory(target);
                        else Files.copy(file, target);
                    }
                }
            } else Files.copy(original, copy);
            if (!before.equals(fullHash(original)) || !before.equals(fullHash(copy))) {
                throw new IOException("Runtime-Eintrag wurde während des Kopierens verändert: " + original);
            }
            entries.add(Map.of("source", original.toString(), "path", name, "sha256", before,
                    "kind", Files.isDirectory(original) ? "classes" : "jar"));
        }
        if (entries.isEmpty()) throw new IOException("Runtime-Classpath ist leer");
        return List.copyOf(entries);
    }

    private static String fullHash(Path location) throws Exception {
        if (Files.isSymbolicLink(location)) throw new IOException("Verknüpfung im Runtime-Bestand");
        if (Files.isRegularFile(location)) return BuildEvidence.hash(Files.readAllBytes(location));
        if (!Files.isDirectory(location)) throw new IOException("Runtime-Eintrag fehlt: " + location);
        List<Map<String, Object>> files = new ArrayList<>();
        try (var stream = Files.walk(location)) {
            for (Path file : stream.sorted(Comparator.comparing(p -> location.relativize(p).toString().replace('\\', '/'))).toList()) {
                if (Files.isSymbolicLink(file)) throw new IOException("Verknüpfung im Runtime-Bestand");
                if (Files.isRegularFile(file)) files.add(Map.of("path", location.relativize(file).toString().replace('\\', '/'),
                        "sha256", BuildEvidence.hash(Files.readAllBytes(file))));
            }
        }
        return BuildEvidence.inventoryHash(files);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> manifest(Path root, Path suite) throws Exception {
        Path runtime = checkedSuite(root, suite).resolve("runtime");
        if (Files.isSymbolicLink(runtime) || !runtime.toRealPath().equals(runtime)) throw new IOException("Verknüpfter Runtime-Pfad");
        Object parsed = Json.parse(Files.readString(runtime.resolve("manifest.json"), StandardCharsets.UTF_8));
        if (!(parsed instanceof Map<?, ?> map) || !"CERTIFICATE_RUNTIME".equals(map.get("kind"))
                || !(map.get("entries") instanceof List<?> list) || list.isEmpty()) throw new IOException("Runtime-Manifest fehlt/ist ungültig");
        if (!Objects.equals(map.get("sourceSha256"), BuildEvidence.inventoryHash(BuildEvidence.sourceInventory(root)))) {
            throw new IOException("Quellen seit Erstellung der Runtime-Kopie verändert; kein Workerstart");
        }
        for (Object raw : list) {
            Map<?, ?> entry = (Map<?, ?>) raw;
            Path file = runtime.resolve(String.valueOf(entry.get("path"))).normalize();
            if (!file.startsWith(runtime) || !file.toRealPath().equals(file)
                    || !Objects.equals(entry.get("sha256"), fullHash(file))) throw new IOException("Runtime-Kopie verändert: " + file);
        }
        return (Map<String, Object>) map;
    }

    static String classpath(Path root, Path suite) throws Exception {
        Map<String, Object> manifest = manifest(root, suite);
        verifyGuards((List<Map<String, Object>>) manifest.get("guards"));
        List<String> paths = new ArrayList<>();
        for (Object raw : (List<?>) manifest.get("entries")) paths.add(suite.resolve("runtime")
                .resolve(String.valueOf(((Map<?, ?>) raw).get("path"))).toString());
        return String.join(File.pathSeparator, paths);
    }

    static Guard workerGuard(Path root, Path suite) throws Exception {
        if(!Files.isRegularFile(suite.resolve("runtime/manifest.json"))) throw new IOException(
                "testsweet neu starten; der Steuerungsprozess verwendet einen älteren Build ohne laufgebundene Runtime");
        Map<String,Object> state=CertificateSuiteState.read(suite.resolve("status.json"));
        if(!(state.get("runtimeManifestSha256") instanceof String declared)||declared.isBlank()) throw new IOException(
                "testsweet neu starten; der Steuerungsprozess verwendet einen älteren Build ohne Runtime-Startnachweis");
        Map<String, Object> manifest = manifest(root, suite);
        if (!Objects.equals(state.get("runtimeManifestSha256"),BuildEvidence.hash(Files.readAllBytes(suite.resolve("runtime/manifest.json"))))) {
            throw new IOException("Runtime-Manifest stimmt nicht mit dem Suite-Startnachweis überein");
        }
        Path runtime = suite.resolve("runtime");
        for (Class<?> type : List.of(BuildEvidence.class, Leistungsberechnung.class)) {
            if (!BuildEvidence.location(type).startsWith(runtime)) {
                throw new IOException("Worker wurde nicht aus seiner laufgebundenen Runtime gestartet: " + type.getName());
            }
        }
        return new Guard(manifest, runtime, declared);
    }

    static final class Guard {
        private final List<Map<String, Object>> guards;
        private long lastCheck;
        @SuppressWarnings("unchecked") Guard(Map<String, Object> manifest, Path runtime, String manifestHash) {
            List<Map<String,Object>> all=new ArrayList<>((List<Map<String,Object>>)manifest.get("guards"));
            all.add(Map.of("path",runtime.resolve("manifest.json").toString(),"kind","file","sha256",manifestHash));
            for(Object raw:(List<?>)manifest.get("entries")) {
                Map<?,?> entry=(Map<?,?>)raw;
                all.add(Map.of("path",runtime.resolve(String.valueOf(entry.get("path"))).toString(),
                        "kind","runtime","sha256",entry.get("sha256")));
            }
            guards=List.copyOf(all);
        }
        synchronized void check(boolean force) throws Exception {
            long now = System.nanoTime();
            if (force || lastCheck == 0 || now - lastCheck >= CHECK_INTERVAL) {
                verifyGuards(guards); lastCheck = now;
            }
        }
    }

    private static List<Map<String, Object>> junitGuards(Path root) throws Exception {
        Map<String, Map<String, Object>> files = new TreeMap<>();
        for (String module : List.of("", "testsweet/")) {
            Path target = root.resolve(module + "target");
            for (String marker : List.of("test-evidence.json", "test-evidence-active.properties")) {
                addGuard(files, target.resolve(marker), "file", null);
            }
            Map<?, ?> marker = (Map<?, ?>) Json.parse(Files.readString(target.resolve("test-evidence.json"), StandardCharsets.UTF_8));
            if (!"COMPLETED".equals(marker.get("state")) || !(marker.get("buildEvidence") instanceof Map<?, ?> build)
                    || !Boolean.TRUE.equals(build.get("sourceArtifactsMatch"))) throw new IOException("JUnit-Buildnachweis fehlt/ist unvollständig");
            for (Object raw : (List<?>) build.get("artifacts")) {
                Map<?, ?> artifact = (Map<?, ?>) raw;
                addGuard(files, Path.of(String.valueOf(artifact.get("path"))), String.valueOf(artifact.get("kind")),
                        String.valueOf(artifact.get("sha256")));
            }
            for (String folder : List.of("test-evidence-history", "surefire-reports")) {
                try (var stream = Files.walk(target.resolve(folder))) {
                    for (Path file : stream.filter(Files::isRegularFile).toList()) addGuard(files, file, "file", null);
                }
            }
        }
        return List.copyOf(files.values());
    }

    private static void addGuard(Map<String, Map<String, Object>> files, Path path, String kind, String expected) throws Exception {
        path = path.toAbsolutePath().normalize();
        String current = kind.equals("classes") ? BuildEvidence.classesHash(path) : BuildEvidence.hash(Files.readAllBytes(path));
        if (expected != null && !expected.equals(current)) throw new IOException("JUnit-Artefakt ist vor der Runtime-Kopie verändert: " + path);
        files.put(path.toString(), Map.of("path", path.toString(), "kind", kind, "sha256", current));
    }

    static void verifyGuards(List<Map<String, Object>> guards) throws Exception {
        if (guards == null || guards.isEmpty()) throw new IOException("Ursprüngliche JUnit-/Buildbelege fehlen");
        for (Map<String, Object> guard : guards) {
            Path path = Path.of(String.valueOf(guard.get("path")));
            String actual;
            try { actual = "runtime".equals(guard.get("kind")) ? fullHash(path)
                    : "classes".equals(guard.get("kind")) ? BuildEvidence.classesHash(path)
                    : BuildEvidence.hash(Files.readAllBytes(path)); }
            catch (Exception missing) { throw new IOException("Ursprünglicher JUnit-/Buildbeleg wurde entfernt: " + path, missing); }
            if (!Objects.equals(guard.get("sha256"), actual)) throw new IOException(
                    "Ursprünglicher JUnit-/Buildbeleg wurde während des Zertifikatslaufs ersetzt: " + path
                            + ". Parallele Builds beenden und vollständigen Zertifikatslauf neu starten.");
        }
    }
}
