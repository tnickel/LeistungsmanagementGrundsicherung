package de.allgemein.testsweet;

import java.awt.Desktop;
import java.net.URI;

/**
 * Einstieg des Mitarbeiter-Simulators „testsweet": startet die lokale Oberfläche
 * (Standard: http://127.0.0.1:8095, Port über SWEET_PORT konfigurierbar; 8090–8093
 * sind auf dem Entwicklungssystem belegt) und öffnet den Browser. Aufruf aus dem
 * Projektordner:
 *
 * <pre>
 *   mvn -f testsweet/pom.xml process-classes exec:java
 * </pre>
 *
 * Voraussetzung: das Hauptprojekt wurde einmalig mit {@code mvn install} ins lokale
 * Maven-Repository installiert (Details: testsweet/README.md).
 */
public final class SweetMain {

    public static void main(String[] args) throws Exception {
        BuildEvidence.initialize();
        try { run(args); }
        catch (IllegalArgumentException error) {
            System.err.println("Ungültige Konfiguration: " + error.getMessage());
            System.exit(2);
        }
    }

    private static void run(String[] args) throws Exception {
        if(args.length>0&&"certificate-worker".equals(args[0])) {
            if(args.length!=2)throw new IllegalArgumentException("Zertifikatsworker benötigt genau ein Laufverzeichnis");
            CertificateSuiteWorker.run(java.nio.file.Path.of(args[1]));return;
        }
        if (args.length > 0 && "run".equalsIgnoreCase(args[0])) {
            int ma = args.length > 1 ? Integer.parseInt(args[1]) : 10;
            int auf = args.length > 2 ? Integer.parseInt(args[2]) : 60;
            int seed = args.length > 3 ? Integer.parseInt(args[3]) : 42;
            int denkzeit = args.length > 4 ? Integer.parseInt(args[4]) : 20;
            Simulator.Kategorie kategorie = liesKategorie(args);
            try {
                Simulator.pruefeKonfiguration(kategorie, ma, auf);
            } catch (IllegalStateException e) {
                System.err.println(e.getMessage());
                System.exit(2);
            }
            Simulator simulator = new Simulator();
            System.out.println("testsweet — Starte Batch-Lauf (" + ma + " Mitarbeiter, " + auf
                    + " Aufträge, Seed " + seed + ", Kategorie " + kategorie + ") auf " + simulator.dbUrl);
            simulator.laufSynchron(new Simulator.LaufKonfiguration(ma, auf, seed, denkzeit, true, kategorie));
            System.out.println("Lauf beendet. PDF: " + simulator.letztesPdf());
            System.out.println("Markdown: " + simulator.letztesMd());
            if (!simulator.laufErfolgreich()) System.exit(1);
            return;
        }
        if (args.length > 0 && ("web".equalsIgnoreCase(args[0]) || "run-web".equalsIgnoreCase(args[0]))) {
            int ma = args.length > 1 ? Integer.parseInt(args[1]) : 2;
            int auf = args.length > 2 ? Integer.parseInt(args[2]) : 4;
            int seed = args.length > 3 ? Integer.parseInt(args[3]) : 42;
            int denkzeit = args.length > 4 ? Integer.parseInt(args[4]) : 200;
            Simulator.Kategorie kategorie = liesKategorie(args);
            if (kategorie == Simulator.Kategorie.STRESS) {
                System.err.println("Der Stresstest (User anlegen + nachbearbeiten) ist nur im "
                        + "Fachschicht-Modus möglich — bitte Kommando 'run' verwenden");
                System.exit(2);
            }
            try {
                Simulator.pruefeKonfiguration(kategorie, ma, auf);
            } catch (IllegalStateException e) {
                System.err.println(e.getMessage());
                System.exit(2);
            }
            Simulator simulator = new Simulator();
            System.out.println("testsweet — Starte Weboberflächen-Lauf (Selenium) mit " + ma
                    + " Browserfenstern, " + auf + " Aufträgen (Seed " + seed + ", Kategorie "
                    + kategorie + ") auf " + simulator.dbUrl);
            simulator.laufWebSynchron(new Simulator.LaufKonfiguration(ma, auf, seed, denkzeit, true, kategorie));
            System.out.println("Web-Lauf beendet. PDF: " + simulator.letztesPdf());
            System.out.println("Markdown: " + simulator.letztesMd());
            if (!simulator.laufErfolgreich()) System.exit(1);
            return;
        }

        int port = ganzeZahlAusUmgebung("SWEET_PORT", 8095);
        if (port < 1 || port > 65535) throw new IllegalArgumentException("SWEET_PORT muss zwischen 1 und 65535 liegen");
        Simulator simulator = new Simulator();
        SweetServer server = new SweetServer(simulator, port);
        server.start();
        Runtime.getRuntime().addShutdownHook(new Thread(server::close, "testsweet-shutdown"));

        String url = "http://127.0.0.1:" + port + "/";
        System.out.println("testsweet — Mitarbeiter-Simulator läuft auf " + url);
        System.out.println("Datenbank: " + simulator.dbUrl + " (überschreibbar über TX_URL/TX_USER/TX_PASS)");
        System.out.println("Beenden: Strg+C");

        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(new URI(url));
            } else {
                Runtime.getRuntime().exec(new String[]{"cmd", "/c", "start", "", url});
            }
        } catch (Exception e) {
            System.out.println("Browser konnte nicht geöffnet werden: " + e.getMessage()
                    + " — bitte selbst " + url + " öffnen.");
        }

        // Hauptschlange halten; Server-Threads sind Daemon-los, aber der
        // Executor hält die JVM ohnehin am Leben.
        Thread.currentThread().join();
    }

    /** Optionaler 6. CLI-Parameter: Testfallkategorie (POSITIV/NEGATIV/LAST/STRESS;
     *  STRESS nur im Fachschicht-Modus 'run'). */
    private static Simulator.Kategorie liesKategorie(String[] args) {
        if (args.length <= 5) {
            return Simulator.Kategorie.POSITIV;
        }
        try {
            return Simulator.Kategorie.valueOf(args[5].toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            System.err.println("Unbekannte Testfallkategorie '" + args[5]
                    + "' — zulaessig: POSITIV, NEGATIV, LAST, STRESS (nur Fachschicht)");
            System.exit(2);
            return Simulator.Kategorie.POSITIV; // nicht erreichbar
        }
    }

    private static int ganzeZahlAusUmgebung(String name, int standard) {
        String wert = System.getenv(name);
        if (wert == null || wert.isBlank()) {
            return standard;
        }
        try {
            return Integer.parseInt(wert.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(name + " muss eine Ganzzahl sein");
        }
    }

    private SweetMain() {
    }
}
