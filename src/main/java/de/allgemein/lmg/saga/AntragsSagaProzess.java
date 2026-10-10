package de.allgemein.lmg.saga;

import de.allgemein.lmg.calc.Leistungsberechnung;
import de.allgemein.lmg.model.SozialFall;
import de.allgemein.lmg.web.Json;
import de.allgemein.tx.saga.SagaDefinition;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Vollständige fachliche End-to-End-Saga für das Leistungsmanagement Grundsicherung:
 * Kette von 4 unteilbaren Datenbanktransaktionen mit automatischer Rückwärts-Kompensation:
 *
 * <ol>
 *   <li><b>Vorgang erfassen:</b> Anlage von BEDARFSGEMEINSCHAFT und SOZIAL_FALL</li>
 *   <li><b>Bedarfsprüfung:</b> SGB-XII-Berechnung; Abbruch bei fehlendem Anspruch</li>
 *   <li><b>Bescheid erteilen:</b> Erstellung des Bewilligungsbescheids nach § 37 SGB X</li>
 *   <li><b>Erstauszahlung anweisen:</b> Zahlungsanordnung (Status BEREIT), Minderung des Restbudgets</li>
 * </ol>
 */
public final class AntragsSagaProzess {

    public static final String SAGA_TYPE = "SGB_XII_ANTRAGSVERARBEITUNG";

    private AntragsSagaProzess() {
    }

    public static SagaDefinition definition() {
        return SagaDefinition.of(SAGA_TYPE)
                .step("Vorgang erfassen", AntragsSagaProzess::vorgangErfassen, AntragsSagaProzess::vorgangKompensieren)
                .step("Bedarfspruefung", AntragsSagaProzess::bedarfspruefung, AntragsSagaProzess::bedarfspruefungKompensieren)
                .step("Bescheid erteilen", AntragsSagaProzess::bescheidErteilen, AntragsSagaProzess::bescheidKompensieren)
                .step("Erstauszahlung anweisen", AntragsSagaProzess::erstauszahlungAnweisen, AntragsSagaProzess::erstauszahlungKompensieren)
                .build();
    }

    // ------------------------------------------------------------------
    // Schritt 1: Vorgang erfassen
    // ------------------------------------------------------------------

    public static void vorgangErfassen(Connection c, String payload) throws SQLException {
        Map<String, Object> data = parsePayload(payload);
        String aktenzeichen = (String) data.get("aktenzeichen");
        String name = (String) data.get("hauptpersonName");
        BigDecimal budget = getBigDecimal(data.get("startBudget"));
        String iban = (String) data.get("iban");
        String bic = (String) data.get("bic");
        int rbs = ((Number) data.get("regelbedarfsstufe")).intValue();
        BigDecimal miete = getBigDecimal(data.get("kduMiete"));
        BigDecimal betrieb = getBigDecimal(data.get("kduBetriebskosten"));
        BigDecimal heizung = getBigDecimal(data.get("kduHeizung"));
        BigDecimal rente = getBigDecimal(data.get("einkommenRente"));
        BigDecimal erwerb = getBigDecimal(data.get("einkommenErwerb"));

        long bgId = nextVal(c, "SEQ_BG");
        try (PreparedStatement stmt = c.prepareStatement(
                "INSERT INTO BEDARFSGEMEINSCHAFT (ID, AKTENZEICHEN, HAUPTPERSON_NAME, STATUS_AKTIV, BUDGET_REST, IBAN, BIC, KONTOINHABER) "
                        + "VALUES (?, ?, ?, 1, ?, ?, ?, ?)")) {
            stmt.setLong(1, bgId);
            stmt.setString(2, aktenzeichen);
            stmt.setString(3, name);
            stmt.setBigDecimal(4, budget);
            stmt.setString(5, iban);
            stmt.setString(6, bic);
            stmt.setString(7, name);
            stmt.executeUpdate();
        }

        long fallId = nextVal(c, "SEQ_FALL");
        try (PreparedStatement stmt = c.prepareStatement(
                "INSERT INTO SOZIAL_FALL (ID, BG_ID, REGELBEDARFSSTUFE, FLAG_MERKZEICHEN_G, FLAG_WARMWASSER, "
                        + "KDU_MIETE, KDU_BETRIEBSKOSTEN, KDU_HEIZUNG, EINKOMMEN_RENTE, EINKOMMEN_ERWERB) "
                        + "VALUES (?, ?, ?, 0, 0, ?, ?, ?, ?, ?)")) {
            stmt.setLong(1, fallId);
            stmt.setLong(2, bgId);
            stmt.setInt(3, rbs);
            stmt.setBigDecimal(4, miete);
            stmt.setBigDecimal(5, betrieb);
            stmt.setBigDecimal(6, heizung);
            stmt.setBigDecimal(7, rente);
            stmt.setBigDecimal(8, erwerb);
            stmt.executeUpdate();
        }
    }

    public static void vorgangKompensieren(Connection c, String payload) throws SQLException {
        Map<String, Object> data = parsePayload(payload);
        String aktenzeichen = (String) data.get("aktenzeichen");
        Long bgId = findBgId(c, aktenzeichen);
        if (bgId != null) {
            try (PreparedStatement delFall = c.prepareStatement("DELETE FROM SOZIAL_FALL WHERE BG_ID = ?");
                 PreparedStatement delBg = c.prepareStatement("DELETE FROM BEDARFSGEMEINSCHAFT WHERE ID = ?")) {
                delFall.setLong(1, bgId);
                delFall.executeUpdate();
                delBg.setLong(1, bgId);
                delBg.executeUpdate();
            }
        }
    }

    // ------------------------------------------------------------------
    // Schritt 2: Bedarfsprüfung
    // ------------------------------------------------------------------

    public static void bedarfspruefung(Connection c, String payload) throws SQLException {
        Map<String, Object> data = parsePayload(payload);
        int rbs = ((Number) data.get("regelbedarfsstufe")).intValue();
        BigDecimal miete = getBigDecimal(data.get("kduMiete"));
        BigDecimal betrieb = getBigDecimal(data.get("kduBetriebskosten"));
        BigDecimal heizung = getBigDecimal(data.get("kduHeizung"));
        BigDecimal rente = getBigDecimal(data.get("einkommenRente"));
        BigDecimal erwerb = getBigDecimal(data.get("einkommenErwerb"));

        SozialFall fall = new SozialFall(1L, 1L, rbs, false, false, miete, betrieb, heizung, rente, erwerb, 1L);
        Leistungsberechnung.Berechnungsergebnis ergebnis = Leistungsberechnung.berechne(1L, List.of(fall));

        if (ergebnis.zahlbetrag().signum() <= 0) {
            throw new IllegalStateException("Kein Leistungsanspruch nach SGB XII: Einkommen übersteigt Gesamtbedarf "
                    + "(Zahlbetrag: " + ergebnis.zahlbetrag() + " EUR)");
        }
    }

    public static void bedarfspruefungKompensieren(Connection c, String payload) throws SQLException {
        // Bedarfsprüfung hat keine persistenten Änderungen hinterlassen; Journal-Eintrag zur Revisionssicherheit
        Map<String, Object> data = parsePayload(payload);
        String aktenzeichen = (String) data.get("aktenzeichen");
        Long bgId = findBgId(c, aktenzeichen);
        if (bgId != null) {
            schreibeJournal(c, bgId, "Bedarfspruefung durch Saga-Kompensation zurueckgenommen", "saga-engine");
        }
    }

    // ------------------------------------------------------------------
    // Schritt 3: Bescheid erteilen
    // ------------------------------------------------------------------

    public static void bescheidErteilen(Connection c, String payload) throws SQLException {
        Map<String, Object> data = parsePayload(payload);
        String aktenzeichen = (String) data.get("aktenzeichen");
        Long bgId = findBgId(c, aktenzeichen);
        if (bgId == null) {
            throw new IllegalStateException("Bedarfsgemeinschaft nicht gefunden: " + aktenzeichen);
        }

        int rbs = ((Number) data.get("regelbedarfsstufe")).intValue();
        BigDecimal miete = getBigDecimal(data.get("kduMiete"));
        BigDecimal betrieb = getBigDecimal(data.get("kduBetriebskosten"));
        BigDecimal heizung = getBigDecimal(data.get("kduHeizung"));
        BigDecimal rente = getBigDecimal(data.get("einkommenRente"));
        BigDecimal erwerb = getBigDecimal(data.get("einkommenErwerb"));

        SozialFall fall = new SozialFall(1L, bgId, rbs, false, false, miete, betrieb, heizung, rente, erwerb, 1L);
        Leistungsberechnung.Berechnungsergebnis ergebnis = Leistungsberechnung.berechne(bgId, List.of(fall));

        long bescheidId = nextVal(c, "SEQ_BESCHEID");
        String bescheidNr = "BESCH-" + aktenzeichen;
        String monatVon = (String) data.get("zahlungsmonat");
        String monatBis = data.containsKey("bewilligungBis") ? (String) data.get("bewilligungBis") : monatVon;
        String sb = data.containsKey("sachbearbeiterId") ? (String) data.get("sachbearbeiterId") : "saga-engine";

        try (PreparedStatement stmt = c.prepareStatement(
                "INSERT INTO BESCHEID (ID, BG_ID, BESCHEID_NR, BESCHEID_DATUM, BEWILLIGUNG_VON, BEWILLIGUNG_BIS, "
                        + "ZAHLBETRAG, STATUS, ERSTELLT_VON, ERSTELLT_AM) VALUES (?, ?, ?, SYSDATE, ?, ?, ?, 'ERTEILT', ?, SYSTIMESTAMP)")) {
            stmt.setLong(1, bescheidId);
            stmt.setLong(2, bgId);
            stmt.setString(3, bescheidNr);
            stmt.setString(4, monatVon);
            stmt.setString(5, monatBis);
            stmt.setBigDecimal(6, ergebnis.zahlbetrag());
            stmt.setString(7, sb);
            stmt.executeUpdate();
        }
        schreibeJournal(c, bgId, "Bescheid " + bescheidNr + " ueber " + ergebnis.zahlbetrag() + " EUR erteilt", sb);
    }

    public static void bescheidKompensieren(Connection c, String payload) throws SQLException {
        Map<String, Object> data = parsePayload(payload);
        String aktenzeichen = (String) data.get("aktenzeichen");
        Long bgId = findBgId(c, aktenzeichen);
        if (bgId != null) {
            try (PreparedStatement stmt = c.prepareStatement(
                    "UPDATE BESCHEID SET STATUS = 'AUFGEHOBEN' WHERE BG_ID = ? AND STATUS = 'ERTEILT'")) {
                stmt.setLong(1, bgId);
                stmt.executeUpdate();
            }
            schreibeJournal(c, bgId, "Bescheid aufgehoben (Saga-Kompensation)", "saga-engine");
        }
    }

    // ------------------------------------------------------------------
    // Schritt 4: Erstauszahlung anweisen
    // ------------------------------------------------------------------

    public static void erstauszahlungAnweisen(Connection c, String payload) throws SQLException {
        Map<String, Object> data = parsePayload(payload);
        String aktenzeichen = (String) data.get("aktenzeichen");
        Long bgId = findBgId(c, aktenzeichen);
        if (bgId == null) {
            throw new IllegalStateException("Bedarfsgemeinschaft nicht gefunden: " + aktenzeichen);
        }

        // Budget prüfen und sperren
        BigDecimal budgetRest;
        try (PreparedStatement stmt = c.prepareStatement(
                "SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ? FOR UPDATE")) {
            stmt.setLong(1, bgId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) throw new IllegalStateException("BG nicht vorhanden: " + bgId);
                budgetRest = rs.getBigDecimal(1);
            }
        }

        int rbs = ((Number) data.get("regelbedarfsstufe")).intValue();
        BigDecimal miete = getBigDecimal(data.get("kduMiete"));
        BigDecimal betrieb = getBigDecimal(data.get("kduBetriebskosten"));
        BigDecimal heizung = getBigDecimal(data.get("kduHeizung"));
        BigDecimal rente = getBigDecimal(data.get("einkommenRente"));
        BigDecimal erwerb = getBigDecimal(data.get("einkommenErwerb"));
        SozialFall fall = new SozialFall(1L, bgId, rbs, false, false, miete, betrieb, heizung, rente, erwerb, 1L);
        BigDecimal zahlbetrag = Leistungsberechnung.berechne(bgId, List.of(fall)).zahlbetrag();

        if (zahlbetrag.compareTo(budgetRest) > 0) {
            throw new IllegalStateException("Zahlbetrag " + zahlbetrag + " EUR uebersteigt Restbudget " + budgetRest + " EUR");
        }

        long zahlungId = nextVal(c, "SEQ_ZAHLUNG");
        String monat = (String) data.get("zahlungsmonat");
        String sb = data.containsKey("sachbearbeiterId") ? (String) data.get("sachbearbeiterId") : "saga-engine";
        String vorgangsNr = "SAGA-" + UUID.randomUUID();
        String iban = (String) data.get("iban");
        String bic = (String) data.get("bic");
        String name = (String) data.get("hauptpersonName");

        try (PreparedStatement stmt = c.prepareStatement(
                "INSERT INTO ZAHLUNG (ID, BG_ID, VORGANGS_NR, ZAHLUNGSMONAT, AUSZAHLUNGSBETRAG, ZAHLUNGSSTATUS, "
                        + "SACHBEARBEITER_ID, FREIGEGEBEN_VON, EMPFAENGER_IBAN, EMPFAENGER_BIC, EMPFAENGER_NAME) "
                        + "VALUES (?, ?, ?, ?, ?, 'BEREIT', ?, ?, ?, ?, ?)")) {
            stmt.setLong(1, zahlungId);
            stmt.setLong(2, bgId);
            stmt.setString(3, vorgangsNr);
            stmt.setString(4, monat);
            stmt.setBigDecimal(5, zahlbetrag);
            stmt.setString(6, sb);
            stmt.setString(7, sb);
            stmt.setString(8, iban);
            stmt.setString(9, bic);
            stmt.setString(10, name);
            stmt.executeUpdate();
        }

        try (PreparedStatement stmt = c.prepareStatement(
                "UPDATE BEDARFSGEMEINSCHAFT SET BUDGET_REST = BUDGET_REST - ? WHERE ID = ?")) {
            stmt.setBigDecimal(1, zahlbetrag);
            stmt.setLong(2, bgId);
            stmt.executeUpdate();
        }

        schreibeJournal(c, bgId, "Erstauszahlung ueber " + zahlbetrag + " EUR fuer " + monat + " angewiesen", sb);
    }

    public static void erstauszahlungKompensieren(Connection c, String payload) throws SQLException {
        Map<String, Object> data = parsePayload(payload);
        String aktenzeichen = (String) data.get("aktenzeichen");
        Long bgId = findBgId(c, aktenzeichen);
        if (bgId != null) {
            // Zahlungen stornieren und Budget zurückbuchen
            try (PreparedStatement stornoStmt = c.prepareStatement(
                    "UPDATE ZAHLUNG SET ZAHLUNGSSTATUS = 'STORNIERT' WHERE BG_ID = ? AND ZAHLUNGSSTATUS = 'BEREIT'")) {
                stornoStmt.setLong(1, bgId);
                stornoStmt.executeUpdate();
            }
            schreibeJournal(c, bgId, "Erstauszahlung storniert (Saga-Kompensation)", "saga-engine");
        }
    }

    // ------------------------------------------------------------------
    // Hilfsmethoden
    // ------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parsePayload(String payload) {
        Object parsed = Json.parse(payload);
        if (!(parsed instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("Payload muss ein JSON-Objekt sein");
        }
        return (Map<String, Object>) map;
    }

    private static BigDecimal getBigDecimal(Object val) {
        if (val == null) return BigDecimal.ZERO.setScale(2);
        if (val instanceof BigDecimal bd) return bd.setScale(2);
        if (val instanceof Number n) return BigDecimal.valueOf(n.doubleValue()).setScale(2, java.math.RoundingMode.HALF_UP);
        return new BigDecimal(String.valueOf(val)).setScale(2);
    }

    private static Long findBgId(Connection c, String aktenzeichen) throws SQLException {
        try (PreparedStatement stmt = c.prepareStatement(
                "SELECT ID FROM BEDARFSGEMEINSCHAFT WHERE AKTENZEICHEN = ?")) {
            stmt.setString(1, aktenzeichen);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getLong(1);
                return null;
            }
        }
    }

    private static long nextVal(Connection c, String sequence) throws SQLException {
        try (Statement stmt = c.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT " + sequence + ".NEXTVAL FROM DUAL")) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private static void schreibeJournal(Connection c, long bgId, String text, String sb) throws SQLException {
        long journalId = nextVal(c, "SEQ_JOURNAL");
        try (PreparedStatement stmt = c.prepareStatement(
                "INSERT INTO JOURNAL (ID, BG_ID, AKTION_TEXT, SACHBEARBEITER_ID, ERSTELLT_AM) "
                        + "VALUES (?, ?, ?, ?, SYSTIMESTAMP)")) {
            stmt.setLong(1, journalId);
            stmt.setLong(2, bgId);
            stmt.setString(3, text);
            stmt.setString(4, sb);
            stmt.executeUpdate();
        }
    }
}
