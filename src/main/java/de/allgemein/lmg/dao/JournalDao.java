package de.allgemein.lmg.dao;

import de.allgemein.lmg.model.Journaleintrag;
import de.allgemein.tx.TxContext;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Datenzugriff für das Buchungsjournal (JOURNAL). Jede fachlich relevante Aktion
 * schreibt hier einen revisionsicheren Eintrag — in derselben Transaktion wie
 * die Änderung selbst (atomar, siehe Zahlungs-Workflow).
 */
public final class JournalDao {

    private static final String COLUMNS = "ID, BG_ID, AKTION_TEXT, SACHBEARBEITER_ID, ERSTELLT_AM";

    /** Fügt einen Journaleintrag ein; BG_ID darf null sein (logische Referenz). */
    public void einfuegen(TxContext ctx, Long bgId, String aktionText, String sachbearbeiterId)
            throws SQLException {
        long id = BedarfsgemeinschaftDao.naechsteSequence(ctx, "SEQ_JOURNAL");
        try (PreparedStatement stmt = ctx.connection().prepareStatement(
                "INSERT INTO JOURNAL (ID, BG_ID, AKTION_TEXT, SACHBEARBEITER_ID) VALUES (?, ?, ?, ?)")) {
            stmt.setLong(1, id);
            if (bgId != null) {
                stmt.setLong(2, bgId);
            } else {
                stmt.setNull(2, java.sql.Types.NUMERIC);
            }
            stmt.setString(3, aktionText);
            stmt.setString(4, sachbearbeiterId);
            stmt.executeUpdate();
        }
    }

    public List<Journaleintrag> letzteFuerBg(TxContext ctx, long bgId, int limit) throws SQLException {
        try (PreparedStatement stmt = ctx.connection().prepareStatement(
                "SELECT " + COLUMNS + " FROM JOURNAL WHERE BG_ID = ? "
                        + "ORDER BY ERSTELLT_AM DESC, ID DESC FETCH FIRST ? ROWS ONLY")) {
            stmt.setLong(1, bgId);
            stmt.setInt(2, limit);
            return leseListe(stmt.executeQuery());
        }
    }

    public List<Journaleintrag> letzte(TxContext ctx, int limit) throws SQLException {
        try (PreparedStatement stmt = ctx.connection().prepareStatement(
                "SELECT " + COLUMNS + " FROM JOURNAL "
                        + "ORDER BY ERSTELLT_AM DESC, ID DESC FETCH FIRST ? ROWS ONLY")) {
            stmt.setInt(1, limit);
            return leseListe(stmt.executeQuery());
        }
    }

    private List<Journaleintrag> leseListe(ResultSet rs) throws SQLException {
        List<Journaleintrag> liste = new ArrayList<>();
        while (rs.next()) {
            liste.add(mappe(rs));
        }
        return liste;
    }

    private Journaleintrag mappe(ResultSet rs) throws SQLException {
        long bgId = rs.getLong(2);
        Timestamp erstellt = rs.getTimestamp(5);
        return new Journaleintrag(
                rs.getLong(1),
                rs.wasNull() ? null : bgId,
                rs.getString(3),
                rs.getString(4),
                erstellt == null ? null : erstellt.toInstant());
    }
}
