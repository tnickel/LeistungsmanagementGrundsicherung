package de.allgemein.tx.demo;

import de.allgemein.tx.saga.SagaDefinition;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Die Saga der Demo (Antrag → Bescheid → Auszahlung) samt Schritten auf den
 * Demo-Tabellen TX_DEMO_ANTRAG/TX_DEMO_ZAHLUNG. Ausgelagert aus DemoMain, damit
 * auch der Adminbereich (Wiederanlauf-Aktion) dieselbe Definition nutzen kann.
 */
public final class AntragsDemoProzess {

    private AntragsDemoProzess() {
    }

    public static SagaDefinition definition() {
        return SagaDefinition.of("ANTRAGSVERARBEITUNG")
                .step("Antrag anlegen", AntragsDemoProzess::antragAnlegen, AntragsDemoProzess::antragEntfernen)
                .step("Bescheid erstellen", AntragsDemoProzess::bescheidErstellen, AntragsDemoProzess::bescheidZuruecknehmen)
                .step("Auszahlung buchen", AntragsDemoProzess::auszahlungBuchen, AntragsDemoProzess::auszahlungStornieren)
                .build();
    }

    // payload-Format: "Name;Betrag"

    public static void antragAnlegen(Connection c, String payload) throws SQLException {
        String[] parts = payload.split(";");
        try (PreparedStatement stmt = c.prepareStatement(
                "INSERT INTO TX_DEMO_ANTRAG (NAME, STATUS, BETRAG) VALUES (?, 'NEU', ?)")) {
            stmt.setString(1, parts[0]);
            stmt.setBigDecimal(2, new BigDecimal(parts[1]));
            stmt.executeUpdate();
        }
    }

    public static void antragEntfernen(Connection c, String payload) throws SQLException {
        try (PreparedStatement stmt = c.prepareStatement(
                "DELETE FROM TX_DEMO_ANTRAG WHERE NAME = ? AND STATUS = 'NEU'")) {
            stmt.setString(1, payload.split(";")[0]);
            stmt.executeUpdate();
        }
    }

    public static void bescheidErstellen(Connection c, String payload) throws SQLException {
        try (PreparedStatement stmt = c.prepareStatement(
                "UPDATE TX_DEMO_ANTRAG SET STATUS = 'BESCHIEDEN' WHERE NAME = ? AND STATUS = 'NEU'")) {
            stmt.setString(1, payload.split(";")[0]);
            stmt.executeUpdate();
        }
    }

    public static void bescheidZuruecknehmen(Connection c, String payload) throws SQLException {
        try (PreparedStatement stmt = c.prepareStatement(
                "UPDATE TX_DEMO_ANTRAG SET STATUS = 'NEU' WHERE NAME = ? AND STATUS = 'BESCHIEDEN'")) {
            stmt.setString(1, payload.split(";")[0]);
            stmt.executeUpdate();
        }
    }

    public static void auszahlungBuchen(Connection c, String payload) throws SQLException {
        String[] parts = payload.split(";");
        BigDecimal betrag = new BigDecimal(parts[1]);
        if (betrag.compareTo(new BigDecimal("10000")) > 0) {
            throw new IllegalStateException(
                    "Betrag " + betrag + " überschreitet das Limit von 10000 — Vorgang wird abgebrochen");
        }
        try (PreparedStatement update = c.prepareStatement(
                "UPDATE TX_DEMO_ANTRAG SET STATUS = 'AUSGEZAHLT' WHERE NAME = ? AND STATUS = 'BESCHIEDEN'");
             PreparedStatement zahlung = c.prepareStatement(
                "INSERT INTO TX_DEMO_ZAHLUNG (ANTRAG_ID, BETRAG) SELECT ID, ? FROM TX_DEMO_ANTRAG WHERE NAME = ?")) {
            zahlung.setBigDecimal(1, betrag);
            zahlung.setString(2, parts[0]);
            zahlung.executeUpdate();
            update.setString(1, parts[0]);
            update.executeUpdate();
        }
    }

    public static void auszahlungStornieren(Connection c, String payload) throws SQLException {
        try (PreparedStatement zahlung = c.prepareStatement(
                "DELETE FROM TX_DEMO_ZAHLUNG WHERE ANTRAG_ID IN (SELECT ID FROM TX_DEMO_ANTRAG WHERE NAME = ?)");
             PreparedStatement update = c.prepareStatement(
                "UPDATE TX_DEMO_ANTRAG SET STATUS = 'BESCHIEDEN' WHERE NAME = ? AND STATUS = 'AUSGEZAHLT'")) {
            zahlung.setString(1, payload.split(";")[0]);
            zahlung.executeUpdate();
            update.setString(1, payload.split(";")[0]);
            update.executeUpdate();
        }
    }
}
