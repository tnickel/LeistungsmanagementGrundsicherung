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

    static void raeumeVerwaisteFensterAuf() {
        Path basis = basis();
        if (!Files.isDirectory(basis)) return;
        // Windows stellt ProcessHandle.Info.arguments nicht zuverlässig bereit.
        // Das Projektskript liest deshalb die CommandLine über CIM; dessen
        // Auswahl bleibt ebenfalls auf die exakt registrierten Profile begrenzt.
        Path skript = basis.getParent().getParent().resolve("raeume-fenster.ps1");
        if (System.getProperty("os.name").startsWith("Windows") && Files.isRegularFile(skript)) {
            try {
                Process prozess = new ProcessBuilder("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass",
                        "-File", skript.toString()).redirectErrorStream(true)
                        .redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
                if (!prozess.waitFor(20, TimeUnit.SECONDS)) prozess.destroyForcibly();
                if (prozess.isAlive() || prozess.exitValue() == 0) return;
            } catch (IOException e) {
                System.err.println("Profil-Aufräumskript nicht verfügbar: " + e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        try (var ordner = Files.list(basis)) {
            List<Path> profile = ordner.filter(Files::isDirectory)
                    .filter(p -> Files.isRegularFile(p.resolve(".testsweet-profile"))).toList();
            ProcessHandle.allProcesses().forEach(p -> {
                var info = p.info();
                String command = info.command().orElse(null);
                List<String> args = List.of(info.arguments().orElse(new String[0]));
                if (profile.stream().anyMatch(profil -> gehoertZuProfil(command, args, profil))) {
                    // Chrome-Unterprozesse dieses exakt identifizierten Profilprozesses.
                    p.descendants().forEach(ProcessHandle::destroy);
                    p.destroy();
                }
            });
        } catch (IOException | SecurityException e) {
            System.err.println("Eigene Browserprofile konnten nicht aufgeräumt werden: " + e.getMessage());
        }
    }
}
