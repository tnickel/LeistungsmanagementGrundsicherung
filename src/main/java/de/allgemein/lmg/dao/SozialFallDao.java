package de.allgemein.lmg.dao;

import de.allgemein.lmg.model.SozialFall;
import de.allgemein.tx.TxContext;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Datenzugriff für SOZIAL_FALL — die Parameter der Bedarfs- und Einkommensermittlung.
 * Maskenänderungen laufen über {@link #aktualisiere} mit VERSION-Prüfung
 * (optimistisches Locking); der Buchungsweg liest im Bann der BG-Sperre.
 */
public final class SozialFallDao {

    private static final String COLUMNS =
            "ID, BG_ID, REGELBEDARFSSTUFE, FLAG_MERKZEICHEN_G, KDU_MIETE, KDU_HEIZUNG, "
                    + "EINKOMMEN_RENTE, EINKOMMEN_ERWERB, VERSION";

    public List<SozialFall> listeFuerBg(TxContext ctx, long bgId) throws SQLException {
        try (PreparedStatement stmt = ctx.connection().prepareStatement(
                "SELECT " + COLUMNS + " FROM SOZIAL_FALL WHERE BG_ID = ? ORDER BY ID")) {
            stmt.setLong(1, bgId);
            return leseListe(stmt.executeQuery());
        }
    }

    public Optional<SozialFall> finde(TxContext ctx, long id) throws SQLException {
        try (PreparedStatement stmt = ctx.connection().prepareStatement(
                "SELECT " + COLUMNS + " FROM SOZIAL_FALL WHERE ID = ?")) {
            stmt.setLong(1, id);
            return leseEinzeln(stmt.executeQuery());
        }
    }

    /**
     * Optimistisches Locking: UPDATE ... WHERE ID = ? AND VERSION = ?. Liefert 0
     * betroffene Zeilen, wenn ein anderer Sachbearbeiter zwischenzeitlich gespeichert
     * hat — dann rollt die Transaktion zurück und der Nutzer erhält einen Hinweis.
     */
    public int aktualisiere(TxContext ctx, SozialFall geaendert, long erwarteteVersion) throws SQLException {
        try (PreparedStatement stmt = ctx.connection().prepareStatement(
                "UPDATE SOZIAL_FALL SET REGELBEDARFSSTUFE = ?, FLAG_MERKZEICHEN_G = ?, "
                        + "KDU_MIETE = ?, KDU_HEIZUNG = ?, EINKOMMEN_RENTE = ?, EINKOMMEN_ERWERB = ? "
                        + "WHERE ID = ? AND VERSION = ?")) {
            stmt.setInt(1, geaendert.regelbedarfsstufe());
            stmt.setInt(2, geaendert.merkzeichenG() ? 1 : 0);
            stmt.setBigDecimal(3, geaendert.kduMiete());
            stmt.setBigDecimal(4, geaendert.kduHeizung());
            stmt.setBigDecimal(5, geaendert.einkommenRente());
            stmt.setBigDecimal(6, geaendert.einkommenErwerb());
            stmt.setLong(7, geaendert.id());
            stmt.setLong(8, erwarteteVersion);
            return stmt.executeUpdate();
        }
    }

    public long naechsteId(TxContext ctx) throws SQLException {
        return BedarfsgemeinschaftDao.naechsteSequence(ctx, "SEQ_FALL");
    }

    private List<SozialFall> leseListe(ResultSet rs) throws SQLException {
        List<SozialFall> liste = new ArrayList<>();
        while (rs.next()) {
            liste.add(mappe(rs));
        }
        return liste;
    }

    private Optional<SozialFall> leseEinzeln(ResultSet rs) throws SQLException {
        if (rs.next()) {
            return Optional.of(mappe(rs));
        }
        return Optional.empty();
    }

    private SozialFall mappe(ResultSet rs) throws SQLException {
        return new SozialFall(
                rs.getLong(1),
                rs.getLong(2),
                rs.getInt(3),
                rs.getInt(4) == 1,
                nullSafeBetrag(rs.getBigDecimal(5)),
                nullSafeBetrag(rs.getBigDecimal(6)),
                nullSafeBetrag(rs.getBigDecimal(7)),
                nullSafeBetrag(rs.getBigDecimal(8)),
                rs.getLong(9));
    }

    private BigDecimal nullSafeBetrag(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2) : value;
    }
}
