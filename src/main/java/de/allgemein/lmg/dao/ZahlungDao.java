package de.allgemein.lmg.dao;

import de.allgemein.lmg.model.Zahlung;
import de.allgemein.tx.TxContext;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Datenzugriff für ZAHLUNG. Die VORGANGS_NR ist unique — sie ist der
 * Idempotenz-Anker gegen Doppelbuchungen bei wiederholten Requests.
 */
public final class ZahlungDao {

    private static final String COLUMNS =
            "ID, BG_ID, FALL_ID, VORGANGS_NR, ZAHLUNGSMONAT, AUSZAHLUNGSBETRAG, ZAHLUNGSSTATUS, ERSTELLT_AM";

    public boolean existiertVorgangsNr(TxContext ctx, String vorgangsNr) throws SQLException {
        try (PreparedStatement stmt = ctx.connection().prepareStatement(
                "SELECT COUNT(*) FROM ZAHLUNG WHERE VORGANGS_NR = ?")) {
            stmt.setString(1, vorgangsNr);
            try (ResultSet rs = stmt.executeQuery()) {
                rs.next();
                return rs.getLong(1) > 0;
            }
        }
    }

    public Optional<Zahlung> findeVorgangsNr(TxContext ctx, String vorgangsNr) throws SQLException {
        try (PreparedStatement stmt = ctx.connection().prepareStatement(
                "SELECT " + COLUMNS + " FROM ZAHLUNG WHERE VORGANGS_NR = ?")) {
            stmt.setString(1, vorgangsNr);
            return leseEinzeln(stmt.executeQuery());
        }
    }

    /** Fügt die Zahlung mit Status BEREIT ein; id im Aufruf vorher über die Sequence gezogen. */
    public Zahlung einfuegen(TxContext ctx, long id, long bgId, Long fallId, String vorgangsNr,
                             String zahlungsmonat, BigDecimal betrag) throws SQLException {
        try (PreparedStatement stmt = ctx.connection().prepareStatement(
                "INSERT INTO ZAHLUNG (ID, BG_ID, FALL_ID, VORGANGS_NR, ZAHLUNGSMONAT, AUSZAHLUNGSBETRAG, ZAHLUNGSSTATUS) "
                        + "VALUES (?, ?, ?, ?, ?, ?, 'BEREIT')")) {
            stmt.setLong(1, id);
            stmt.setLong(2, bgId);
            if (fallId != null) {
                stmt.setLong(3, fallId);
            } else {
                stmt.setNull(3, java.sql.Types.NUMERIC);
            }
            stmt.setString(4, vorgangsNr);
            stmt.setString(5, zahlungsmonat);
            stmt.setBigDecimal(6, betrag);
            stmt.executeUpdate();
        }
        return new Zahlung(id, bgId, fallId, vorgangsNr, zahlungsmonat, betrag, "BEREIT", Instant.now());
    }

    public List<Zahlung> letzteFuerBg(TxContext ctx, long bgId, int limit) throws SQLException {
        try (PreparedStatement stmt = ctx.connection().prepareStatement(
                "SELECT " + COLUMNS + " FROM ZAHLUNG WHERE BG_ID = ? "
                        + "ORDER BY ERSTELLT_AM DESC, ID DESC FETCH FIRST ? ROWS ONLY")) {
            stmt.setLong(1, bgId);
            stmt.setInt(2, limit);
            return leseListe(stmt.executeQuery());
        }
    }

    public List<Zahlung> letzte(TxContext ctx, int limit) throws SQLException {
        try (PreparedStatement stmt = ctx.connection().prepareStatement(
                "SELECT " + COLUMNS + " FROM ZAHLUNG "
                        + "ORDER BY ERSTELLT_AM DESC, ID DESC FETCH FIRST ? ROWS ONLY")) {
            stmt.setInt(1, limit);
            return leseListe(stmt.executeQuery());
        }
    }

    public long naechsteId(TxContext ctx) throws SQLException {
        return BedarfsgemeinschaftDao.naechsteSequence(ctx, "SEQ_ZAHLUNG");
    }

    private List<Zahlung> leseListe(ResultSet rs) throws SQLException {
        List<Zahlung> liste = new ArrayList<>();
        while (rs.next()) {
            liste.add(mappe(rs));
        }
        return liste;
    }

    private Optional<Zahlung> leseEinzeln(ResultSet rs) throws SQLException {
        if (rs.next()) {
            return Optional.of(mappe(rs));
        }
        return Optional.empty();
    }

    private Zahlung mappe(ResultSet rs) throws SQLException {
        long fallId = rs.getLong(3);
        Timestamp erstellt = rs.getTimestamp(8);
        return new Zahlung(
                rs.getLong(1),
                rs.getLong(2),
                rs.wasNull() ? null : fallId,
                rs.getString(4),
                rs.getString(5),
                rs.getBigDecimal(6),
                rs.getString(7),
                erstellt == null ? null : erstellt.toInstant());
    }
}
