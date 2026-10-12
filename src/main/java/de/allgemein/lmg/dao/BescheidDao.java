package de.allgemein.lmg.dao;

import de.allgemein.lmg.model.Bescheid;
import de.allgemein.tx.TxContext;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Datenzugriff für BESCHEID.
 */
public final class BescheidDao {

    private static final String COLUMNS =
            "ID, BG_ID, BESCHEID_NR, BESCHEID_DATUM, BEWILLIGUNG_VON, BEWILLIGUNG_BIS, "
                    + "ZAHLBETRAG, STATUS, ERSTELLT_VON, ERSTELLT_AM, BERECHNUNG";

    public Bescheid einfuegen(TxContext ctx, long id, long bgId, String bescheidNr,
                              LocalDate bescheidDatum, String bewilligungVon, String bewilligungBis,
                              BigDecimal zahlbetrag, String erstelltVon, String berechnung) throws SQLException {
        // Datum einmalig ermitteln: Nummern-Jahr und BESCHEID_DATUM müssen zusammenpassen,
        // auch wenn der Insert über Mitternacht fällt.
        LocalDate datum = bescheidDatum != null ? bescheidDatum : LocalDate.now();
        try (PreparedStatement stmt = ctx.connection().prepareStatement(
                "INSERT INTO BESCHEID (ID, BG_ID, BESCHEID_NR, BESCHEID_DATUM, BEWILLIGUNG_VON, "
                        + "BEWILLIGUNG_BIS, ZAHLBETRAG, STATUS, ERSTELLT_VON, ERSTELLT_AM, BERECHNUNG) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, 'ERTEILT', ?, SYSTIMESTAMP, ?)")) {
            stmt.setLong(1, id);
            stmt.setLong(2, bgId);
            stmt.setString(3, bescheidNr);
            stmt.setDate(4, Date.valueOf(datum));
            stmt.setString(5, bewilligungVon);
            stmt.setString(6, bewilligungBis);
            stmt.setBigDecimal(7, zahlbetrag);
            stmt.setString(8, erstelltVon);
            stmt.setString(9, berechnung);
            stmt.executeUpdate();
        }
        return new Bescheid(id, bgId, bescheidNr, datum,
                bewilligungVon, bewilligungBis, zahlbetrag, "ERTEILT", erstelltVon, Instant.now(),
                berechnung);
    }

    public Optional<Bescheid> finde(TxContext ctx, long id) throws SQLException {
        try (PreparedStatement stmt = ctx.connection().prepareStatement(
                "SELECT " + COLUMNS + " FROM BESCHEID WHERE ID = ?")) {
            stmt.setLong(1, id);
            return leseEinzeln(stmt.executeQuery());
        }
    }

    public List<Bescheid> letzteFuerBg(TxContext ctx, long bgId, int limit) throws SQLException {
        try (PreparedStatement stmt = ctx.connection().prepareStatement(
                "SELECT " + COLUMNS + " FROM BESCHEID WHERE BG_ID = ? "
                        + "ORDER BY ERSTELLT_AM DESC, ID DESC FETCH FIRST ? ROWS ONLY")) {
            stmt.setLong(1, bgId);
            stmt.setInt(2, limit);
            return leseListe(stmt.executeQuery());
        }
    }

    public long naechsteId(TxContext ctx) throws SQLException {
        return BedarfsgemeinschaftDao.naechsteSequence(ctx, "SEQ_BESCHEID");
    }

    private List<Bescheid> leseListe(ResultSet rs) throws SQLException {
        List<Bescheid> liste = new ArrayList<>();
        while (rs.next()) {
            liste.add(mappe(rs));
        }
        return liste;
    }

    private Optional<Bescheid> leseEinzeln(ResultSet rs) throws SQLException {
        if (rs.next()) {
            return Optional.of(mappe(rs));
        }
        return Optional.empty();
    }

    private Bescheid mappe(ResultSet rs) throws SQLException {
        Date datum = rs.getDate(4);
        Timestamp erstellt = rs.getTimestamp(10);
        return new Bescheid(
                rs.getLong(1),
                rs.getLong(2),
                rs.getString(3),
                datum == null ? null : datum.toLocalDate(),
                rs.getString(5),
                rs.getString(6),
                rs.getBigDecimal(7),
                rs.getString(8),
                rs.getString(9),
                erstellt == null ? null : erstellt.toInstant(),
                rs.getString(11));
    }
}
