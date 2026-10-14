package de.allgemein.testsweet;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.sql.Statement;
import java.util.Locale;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Vorabprüfung der Testdatenbank vor dem Laufstart und Einordnung von
 * Verbindungsfehlern.
 *
 * Ohne diese Prüfung startete der Simulator einen Lauf, scheiterte am
 * ersten Verbindungsaufbau (z. B. ORA-12541, wenn der Docker-Container
 * „lmg-oracle“ gestoppt ist) und hinterließ einen abgebrochenen Lauf mit
 * generischem Nachweis-Dialog. Mit der Prüfung erhält der Aufrufer vor dem
 * Lauf eine verständliche Meldung mit Startanleitung und es wird gar kein
 * Laufverzeichnis mehr angelegt.
 */
public final class DbErreichbarkeit {

    private DbErreichbarkeit() {
    }

    /** Ergebnis der Vorabprüfung: erreichbar ja/nein plus Klartext-Hinweis. */
    public record Ergebnis(boolean erreichbar, String hinweis) {
    }

    /** Verbindungsversuch mit kurzer Wartezeit; SELECT 1 als Funktionsprobe. */
    public static Ergebnis pruefe(String url, String user, String pass) {
        // Der Preflight prüft die Verfügbarkeit der Oracle-Testdatenbank.
        // Andere URLs (etwa Test-Attrappen in JUnit-Umgebungen) sind keine
        // Verfügbarkeitsfrage — sie werden durchgereicht und scheitern im
        // Lauf selbst mit einer klaren Treibermeldung.
        if (url == null || !url.startsWith("jdbc:oracle:")) {
            return new Ergebnis(true, null);
        }
        // 5 s je Phase: lokal ist „Verbindung verweigert“ sofort da, der Cap
        // schützt die Oberfläche vor hängenden Klicks bei Routing-Problemen.
        Properties props = new Properties();
        props.setProperty("user", user);
        props.setProperty("password", pass);
        props.setProperty("oracle.net.CONNECT_TIMEOUT", "5000");
        props.setProperty("oracle.jdbc.ReadTimeout", "5000");
        try (Connection c = DriverManager.getConnection(url, props);
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT 1 FROM DUAL")) {
            if (!rs.next()) {
                return new Ergebnis(false, "Die Testdatenbank antwortete, lieferte aber keine Testabfrage ("
                        + zielAusUrl(url) + "). Lauf nicht gestartet — bitte die technischen Details auswerten lassen.");
            }
            return new Ergebnis(true, null);
        } catch (SQLTimeoutException e) {
            return new Ergebnis(false, "Die Testdatenbank " + zielAusUrl(url)
                    + " hat nicht rechtzeitig geantwortet (Zeitüberschreitung). Läuft der Docker-Container"
                    + " „lmg-oracle“ gerade erst hoch? Bitte 20–30 Sekunden warten und erneut starten."
                    + " Technische Ursache: " + meldung(e));
        } catch (SQLException e) {
            return new Ergebnis(false, hinweisZu(e, url));
        }
    }

    /** Vorabprüfung über TX_URL/TX_USER/TX_PASS (Umgebung oder Systemeigenschaft). */
    public static Ergebnis pruefeAusUmgebung(java.util.Map<String, String> umgebung) {
        String url = System.getProperty("tx.url", umgebung.getOrDefault("TX_URL",
                "jdbc:oracle:thin:@//localhost:1521/FREEPDB1"));
        String user = System.getProperty("tx.user", umgebung.getOrDefault("TX_USER", "tx_demo"));
        String pass = System.getProperty("tx.pass", umgebung.getOrDefault("TX_PASS", "tx_demo"));
        return pruefe(url, user, pass);
    }

    /**
     * Ist das ein Umgebungs-/Verbindungsproblem statt eines fachlichen
     * Fehlers? Prüft die ganze Ursachenkette, weil JDBC-Fehler oft in
     * RuntimeExceptions verpackt ankommen.
     */
    public static boolean istVerbindungsproblem(Throwable t) {
        for (Throwable lauf = t; lauf != null; lauf = lauf.getCause() == lauf ? null : lauf.getCause()) {
            if (lauf instanceof SQLException e && istVerbindungsfehler(e)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Verständliche Meldung für einen gescheiterten Verbindungsaufbau, mit
     * benannter Ursache (gestoppter Docker-Container, Datenbank noch im
     * Aufbau, Anmeldung abgelehnt) und Startanleitung.
     */
    public static String hinweisZu(SQLException e, String url) {
        int code = e.getErrorCode();
        String ziel = zielAusUrl(url);
        String technisch = " Technische Ursache: " + meldung(e);
        if (code == 1017) {
            return "Die Anmeldung an der Testdatenbank " + ziel + " wurde abgelehnt (ORA-01017)."
                    + " Bitte Benutzer und Kennwort prüfen (Umgebungsvariablen TX_USER/TX_PASS)."
                    + " Es wurde kein Testlauf ausgeführt." + technisch;
        }
        if (code == 12505 || code == 12514 || code == 12528 || code == 1033 || code == 1089 || code == 1034) {
            return "Die Testdatenbank " + ziel + " antwortet, ist aber noch nicht betriebsbereit (ORA-"
                    + code + "). Der Docker-Container „lmg-oracle“ wurde vermutlich gerade gestartet."
                    + " Bitte 20–30 Sekunden warten und den Lauf erneut starten. Es wurde kein Testlauf ausgeführt."
                    + technisch;
        }
        if (istVerbindungsfehler(e)) {
            return "Die Testdatenbank " + ziel + " ist nicht erreichbar (ORA-" + Math.abs(code) + ")."
                    + " Der Docker-Container „lmg-oracle“ mit der Testdatenbank läuft wahrscheinlich nicht."
                    + " Bitte starten: Docker Desktop öffnen und den Container „lmg-oracle“ hochfahren —"
                    + " oder im Projektordner start.bat ausführen (startet den Container bei Bedarf selbst)."
                    + " Nach dem Start 20–30 Sekunden warten und den Lauf erneut starten."
                    + " Es wurde kein Testlauf ausgeführt." + technisch;
        }
        return "Der Verbindungstest an der Testdatenbank " + ziel + " ist fehlgeschlagen."
                + " Bitte die Ursache auswerten lassen. Es wurde kein Testlauf ausgeführt." + technisch;
    }

    private static boolean istVerbindungsfehler(SQLException e) {
        int code = e.getErrorCode();
        // 12541 kein Listener, 17002 Netzwerk-/IO-Fehler, 12560 Protokollfehler,
        // 3113/3114 Verbindung während des Betriebs verloren, 12170 Zeitüberschreitung.
        if (code == 12541 || code == 17002 || code == 12560 || code == 3113 || code == 3114 || code == 12170) {
            return true;
        }
        String sqlState = e.getSQLState();
        if (sqlState != null && sqlState.startsWith("08")) {
            return true;
        }
        String meldung = e.getMessage() == null ? "" : e.getMessage().toLowerCase(Locale.ROOT);
        return meldung.contains("network adapter") || meldung.contains("connection refused")
                || meldung.contains("no listener") || meldung.contains("kein listener");
    }

    /** Host:Port aus einer JDBC-URL, z. B. „localhost:1521“ — für die Meldung. */
    static String zielAusUrl(String url) {
        Matcher m = Pattern.compile("//([^/:@\\s]+):(\\d+)").matcher(url == null ? "" : url);
        return m.find() ? m.group(1) + ":" + m.group(2) : "unbekanntes Ziel";
    }

    private static String meldung(SQLException e) {
        String text = e.getClass().getSimpleName() + ": " + e.getMessage();
        return text.length() > 300 ? text.substring(0, 300) + "…" : text;
    }
}
