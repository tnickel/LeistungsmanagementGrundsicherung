package de.allgemein.testsweet;

import org.junit.jupiter.api.Test;
import java.sql.SQLException;
import static org.junit.jupiter.api.Assertions.*;

class DbErreichbarkeitTest {

    @Test void keinListenerWirdAlsVerbindungsproblemEingestuft() {
        SQLException e = new SQLException("ORA-12541: Verbindung kann nicht hergestellt werden."
                + " Kein Listener bei host localhost port 1521", "08006", 12541);
        assertTrue(DbErreichbarkeit.istVerbindungsproblem(e));
        String hinweis = DbErreichbarkeit.hinweisZu(e, "jdbc:oracle:thin:@//localhost:1521/FREEPDB1");
        assertTrue(hinweis.contains("nicht erreichbar"));
        assertTrue(hinweis.contains("lmg-oracle"), "Container-Name muss in der Anleitung stehen");
        assertTrue(hinweis.contains("start.bat"), "Startanleitung muss start.bat nennen");
        assertTrue(hinweis.contains("localhost:1521"), "Ziel muss benannt werden");
        assertTrue(hinweis.contains("Es wurde kein Testlauf ausgeführt"));
    }

    @Test void verpackterNetzwerkfehlerWirdUeberDieUrsachenketteGefunden() {
        SQLException ursache = new SQLException("IO Error: The Network Adapter could not"
                + " establish the connection", "08006", 17002);
        RuntimeException verpackt = new IllegalStateException("Lauf fehlgeschlagen", ursache);
        assertTrue(DbErreichbarkeit.istVerbindungsproblem(verpackt));
    }

    @Test void datenbankNochImAufbauBekommtWarteHinweis() {
        SQLException e = new SQLException("ORA-12514: Der Listener kennt den Dienst nicht", "66000", 12514);
        assertFalse(DbErreichbarkeit.istVerbindungsproblem(e), "12514 ist kein reines Verbindungsproblem mehr");
        String hinweis = DbErreichbarkeit.hinweisZu(e, "jdbc:oracle:thin:@//localhost:1521/FREEPDB1");
        assertTrue(hinweis.contains("noch nicht betriebsbereit"));
        assertTrue(hinweis.contains("warten"));
    }

    @Test void fachlicheFehlerSindKeinVerbindungsproblem() {
        SQLException fachlich = new SQLException("ORA-20001: Fachregel verletzt", "20000", 20001);
        assertFalse(DbErreichbarkeit.istVerbindungsproblem(fachlich));
        assertFalse(DbErreichbarkeit.istVerbindungsproblem(
                new IllegalStateException("Zertifikatsdienst ist beendet")));
        String hinweis = DbErreichbarkeit.hinweisZu(fachlich, "jdbc:oracle:thin:@//localhost:1521/FREEPDB1");
        assertTrue(hinweis.contains("fehlgeschlagen"));
        assertFalse(hinweis.contains("lmg-oracle"), "Falscher Klartext wäre irreführend");
    }

    @Test void falscheAnmeldungWirdBenannt() {
        SQLException e = new SQLException("ORA-01017: Benutzername/Kennwort ungültig", "72000", 1017);
        String hinweis = DbErreichbarkeit.hinweisZu(e, "jdbc:oracle:thin:@//localhost:1521/FREEPDB1");
        assertTrue(hinweis.contains("ORA-01017"));
        assertTrue(hinweis.contains("TX_USER"));
    }

    @Test void zielWirdAusDerUrlGelesen() {
        assertEquals("localhost:1521", DbErreichbarkeit.zielAusUrl(
                "jdbc:oracle:thin:@//localhost:1521/FREEPDB1"));
        assertEquals("192.168.56.10:1921", DbErreichbarkeit.zielAusUrl(
                "jdbc:oracle:thin:@//192.168.56.10:1921/o19c1p1"));
        assertEquals("unbekanntes Ziel", DbErreichbarkeit.zielAusUrl("keine URL"));
    }
}
