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
 *   mvn -f testsweet/pom.xml compile exec:java
 * </pre>
 *
 * Voraussetzung: das Hauptprojekt wurde einmalig mit {@code mvn install} ins lokale
 * Maven-Repository installiert (Details: testsweet/README.md).
 */
public final class SweetMain {

    public static void main(String[] args) throws Exception {
        int port = ganzeZahlAusUmgebung("SWEET_PORT", 8095);
        Simulator simulator = new Simulator();
        SweetServer server = new SweetServer(simulator, port);
        server.start();

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

    private static int ganzeZahlAusUmgebung(String name, int standard) {
        String wert = System.getenv(name);
        if (wert == null || wert.isBlank()) {
            return standard;
        }
        try {
            return Integer.parseInt(wert.trim());
        } catch (NumberFormatException e) {
            return standard;
        }
    }

    private SweetMain() {
    }
}
