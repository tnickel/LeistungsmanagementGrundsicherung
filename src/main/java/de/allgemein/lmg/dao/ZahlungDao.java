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
            "ID, BG_ID, FALL_ID, VORGANGS_NR, ZAHLUNGSMONAT, AUSZAHLUNGSBETRAG, ZAHLUNGSSTATUS, ERSTELLT_AM, "
                    + "SACHBEARBEITER_ID, FREIGEGEBEN_VON, EMPFAENGER_IBAN, EMPFAENGER_BIC, EMPFAENGER_NAME";

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

    public Optional<Zahlung> finde(TxContext ctx, long id) throws SQLException {
        try (PreparedStatement stmt = ctx.connection().prepareStatement(
                "SELECT " + COLUMNS + " FROM ZAHLUNG WHERE ID = ?")) {
            stmt.setLong(1, id);
            return leseEinzeln(stmt.executeQuery());
        }
    }

    public Optional<Zahlung> lockForUpdate(TxContext ctx, long id) throws SQLException {
        try (PreparedStatement stmt = ctx.connection().prepareStatement(
                "SELECT " + COLUMNS + " FROM ZAHLUNG WHERE ID = ? FOR UPDATE")) {
            stmt.setLong(1, id);
            return leseEinzeln(stmt.executeQuery());
        }
    }

    /** Alle bereitgestellten Zahlungen mit Zeilensperre — der Kassenlauf übergibt sie gesammelt. */
    public List<Zahlung> bereiteZahlungenGesperrt(TxContext ctx) throws SQLException {
        try (PreparedStatement stmt = ctx.connection().prepareStatement(
                "SELECT " + COLUMNS + " FROM ZAHLUNG WHERE ZAHLUNGSSTATUS = 'BEREIT' FOR UPDATE "
                        + "ORDER BY ID")) {
            return leseListe(stmt.executeQuery());
        }
    }

    /** Setzt alle BEREIT-Zahlungen auf UEBERGEBEN; liefert die Anzahl betroffener Zeilen. */
    public int uebergebeBereite(TxContext ctx) throws SQLException {
        try (PreparedStatement stmt = ctx.connection().prepareStatement(
                "UPDATE ZAHLUNG SET ZAHLUNGSSTATUS = 'UEBERGEBEN' WHERE ZAHLUNGSSTATUS = 'BEREIT'")) {
            return stmt.executeUpdate();
        }
    }

    /** Rechnerische Feststellung (Vier-Augen-Freigabe): Status ANGELEGT -> BEREIT. */
    public int freigeben(TxContext ctx, long id, String freigegebenVon) throws SQLException {
        try (PreparedStatement stmt = ctx.connection().prepareStatement(
                "UPDATE ZAHLUNG SET ZAHLUNGSSTATUS = 'BEREIT', FREIGEGEBEN_VON = ? "
                        + "WHERE ID = ? AND ZAHLUNGSSTATUS = 'ANGELEGT'")) {
            stmt.setString(1, freigegebenVon);
            stmt.setLong(2, id);
            return stmt.executeUpdate();
        }
    }

    /** Setzt den Status einer einzelnen Zahlung (Storno); Aufruf nur unter Zahlungs-/BG-Sperre. */
    public void aktualisiereStatus(TxContext ctx, long id, String neuerStatus) throws SQLException {
        try (PreparedStatement stmt = ctx.connection().prepareStatement(
                "UPDATE ZAHLUNG SET ZAHLUNGSSTATUS = ? WHERE ID = ?")) {
            stmt.setString(1, neuerStatus);
            stmt.setLong(2, id);
            stmt.executeUpdate();
        }
    }

    /** Fügt die Zahlung ein; id im Aufruf vorher über die Sequence gezogen. */
    public Zahlung einfuegen(TxContext ctx, long id, long bgId, Long fallId, String vorgangsNr,
                             String zahlungsmonat, BigDecimal betrag, String status,
                             String sachbearbeiterId, String freigegebenVon,
                             String iban, String bic, String empfaengerName) throws SQLException {
        try (PreparedStatement stmt = ctx.connection().prepareStatement(
                "INSERT INTO ZAHLUNG (ID, BG_ID, FALL_ID, VORGANGS_NR, ZAHLUNGSMONAT, AUSZAHLUNGSBETRAG, ZAHLUNGSSTATUS, "
                        + "SACHBEARBEITER_ID, FREIGEGEBEN_VON, EMPFAENGER_IBAN, EMPFAENGER_BIC, EMPFAENGER_NAME) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
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
            stmt.setString(7, status != null ? status : "BEREIT");
            stmt.setString(8, sachbearbeiterId);
            stmt.setString(9, freigegebenVon);
            stmt.setString(10, iban);
            stmt.setString(11, bic);
            stmt.setString(12, empfaengerName);
            stmt.executeUpdate();
        }
        return new Zahlung(id, bgId, fallId, vorgangsNr, zahlungsmonat, betrag,
                status != null ? status : "BEREIT", Instant.now(),
                sachbearbeiterId, freigegebenVon, iban, bic, empfaengerName);
    }

    public Zahlung einfuegen(TxContext ctx, long id, long bgId, Long fallId, String vorgangsNr,
                             String zahlungsmonat, BigDecimal betrag) throws SQLException {
        return einfuegen(ctx, id, bgId, fallId, vorgangsNr, zahlungsmonat, betrag, "BEREIT",
                null, null, null, null, null);
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
                erstellt == null ? null : erstellt.toInstant(),
                rs.getString(9),
                rs.getString(10),
                rs.getString(11),
                rs.getString(12),
                rs.getString(13));
    }
}
