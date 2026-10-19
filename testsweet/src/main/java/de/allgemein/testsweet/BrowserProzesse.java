package de.allgemein.testsweet;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Eigene Browser werden über ein zufälliges, registriertes Profil identifiziert.
 * Keine Zuordnung über Ahnenprozesse oder bloße Chrome-/Treibernamen. */
final class BrowserProzesse {
    static Path basis() {
        Path cwd = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        return (cwd.getFileName().toString().equalsIgnoreCase("testsweet") ? cwd : cwd.resolve("testsweet"))
                .resolve("logs/browser-profile");
    }

    static Path neuesProfil() throws IOException {
        Path profil = basis().resolve(UUID.randomUUID().toString()).toAbsolutePath().normalize();
        Files.createDirectories(profil);
        Files.writeString(profil.resolve(".testsweet-profile"), "SDN.Leistung testsweet");
        return profil;
    }

    static boolean gehoertZuProfil(String command, List<String> arguments, Path profil) {
        if (command == null || !Path.of(command).getFileName().toString().equalsIgnoreCase("chrome.exe")) {
            return false;
        }
        for (int i = 0; i < arguments.size(); i++) {
            String arg = arguments.get(i);
            String wert = arg.startsWith("--user-data-dir=") ? arg.substring(16)
                    : arg.equals("--user-data-dir") && i + 1 < arguments.size() ? arguments.get(++i) : null;
            if (wert != null) {
                try {
                    if (Path.of(wert).toAbsolutePath().normalize().equals(profil.toAbsolutePath().normalize())) {
                        return true;
                    }
                } catch (RuntimeException ungueltigerPfad) { /* kein eigenes Profil */ }
            }
        }
        return false;
    }

    /** Löscht ein beendetes Profil-Verzeichnis (Aufruf nach treiber.quit()). */
    static void loescheProfil(Path profil) {
        if (profil == null) return;
        try {
            if (Files.isRegularFile(profil.resolve(".testsweet-profile"))) {
                try (var walk = Files.walk(profil)) {
                    walk.sorted(java.util.Comparator.reverseOrder()).forEach(datei -> {
                        try {
                            Files.deleteIfExists(datei);
                        } catch (IOException ignoriert) {
                            // gesperrte Restdateien bleiben liegen, Chromeprozess ist beendet
                        }
                    });
                }
            }
        } catch (IOException | RuntimeException ignoriert) {
            // Profil durfte bereits weg sein oder nicht uns gehören
        }
    }


    static int raeumeVerwaisteFensterAuf() {
        return raeumeVerwaisteFensterAuf(false);
    }

    static int raeumeAlleVerwaistenProzesseAuf() {
        return raeumeVerwaisteFensterAuf(true);
    }

    static int raeumeVerwaisteFensterAuf(boolean allDrivers) {
        Path basis = basis();
        if (!allDrivers && !Files.isDirectory(basis)) return 0;
        Path skript = basis.getParent().getParent().resolve("raeume-fenster.ps1");
        int beendet = 0;
        if (System.getProperty("os.name").startsWith("Windows") && Files.isRegularFile(skript)) {
            try {
                java.util.List<String> cmd = new java.util.ArrayList<>(java.util.List.of(
                        "powershell", "-NoProfile", "-ExecutionPolicy", "Bypass",
                        "-File", skript.toString()));
                if (allDrivers) {
                    cmd.add("-AllDrivers");
                }
                Process prozess = new ProcessBuilder(cmd)
                        .redirectErrorStream(true)
                        .start();
                String output = "";
                try (var reader = prozess.inputReader(java.nio.charset.StandardCharsets.UTF_8)) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output = line.trim();
                    }
                }
                if (!prozess.waitFor(20, TimeUnit.SECONDS)) prozess.destroyForcibly();
                if (!output.isEmpty()) {
                    try { beendet = Integer.parseInt(output); } catch (NumberFormatException ignored) {}
                }
                if (prozess.exitValue() == 0) return beendet;
            } catch (IOException e) {
                System.err.println("Profil-Aufräumskript nicht verfügbar: " + e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return beendet;
            }
        }
        if (Files.isDirectory(basis)) {
            try (var ordner = Files.list(basis)) {
                List<Path> profile = ordner.filter(Files::isDirectory)
                        .filter(p -> Files.isRegularFile(p.resolve(".testsweet-profile"))).toList();
                for (ProcessHandle p : ProcessHandle.allProcesses().toList()) {
                    var info = p.info();
                    String command = info.command().orElse(null);
                    List<String> args = List.of(info.arguments().orElse(new String[0]));
                    if (profile.stream().anyMatch(profil -> gehoertZuProfil(command, args, profil))) {
                        // Chrome-Unterprozesse dieses exakt identifizierten Profilprozesses.
                        p.descendants().forEach(ProcessHandle::destroyForcibly);
                        p.destroyForcibly();
                        beendet++;
                    } else if (command != null && Path.of(command).getFileName().toString().equalsIgnoreCase("chromedriver.exe")) {
                        if (allDrivers || p.parent().isEmpty() || !p.parent().get().isAlive()) {
                            p.descendants().forEach(ProcessHandle::destroyForcibly);
                            p.destroyForcibly();
                            beendet++;
                        }
                    }
                }
            } catch (IOException | SecurityException e) {
                System.err.println("Eigene Browserprofile konnten nicht aufgeräumt werden: " + e.getMessage());
            }
        }
        return beendet;
    }
}
