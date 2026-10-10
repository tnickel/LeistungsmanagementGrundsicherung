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
            "ID, BG_ID, REGELBEDARFSSTUFE, FLAG_MERKZEICHEN_G, FLAG_WARMWASSER, "
                    + "KDU_MIETE, KDU_BETRIEBSKOSTEN, KDU_HEIZUNG, "
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

    /** Legt den SozialFall an (ID über SEQ_FALL); Version startet bei 1. */
    public SozialFall einfuegen(TxContext ctx, long id, SozialFall neu) throws SQLException {
        try (PreparedStatement stmt = ctx.connection().prepareStatement(
                "INSERT INTO SOZIAL_FALL (ID, BG_ID, REGELBEDARFSSTUFE, FLAG_MERKZEICHEN_G, FLAG_WARMWASSER, "
                        + "KDU_MIETE, KDU_BETRIEBSKOSTEN, KDU_HEIZUNG, EINKOMMEN_RENTE, EINKOMMEN_ERWERB) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            stmt.setLong(1, id);
            stmt.setLong(2, neu.bgId());
            stmt.setInt(3, neu.regelbedarfsstufe());
            stmt.setInt(4, neu.merkzeichenG() ? 1 : 0);
            stmt.setInt(5, neu.warmwasser() ? 1 : 0);
            stmt.setBigDecimal(6, neu.kduMiete());
            stmt.setBigDecimal(7, neu.kduBetriebskosten());
            stmt.setBigDecimal(8, neu.kduHeizung());
            stmt.setBigDecimal(9, neu.einkommenRente());
            stmt.setBigDecimal(10, neu.einkommenErwerb());
            stmt.executeUpdate();
        }
        return new SozialFall(id, neu.bgId(), neu.regelbedarfsstufe(), neu.merkzeichenG(), neu.warmwasser(),
                neu.kduMiete(), neu.kduBetriebskosten(), neu.kduHeizung(), neu.einkommenRente(), neu.einkommenErwerb(), 1L);
    }

    /**
     * Optimistisches Locking: UPDATE ... WHERE ID = ? AND VERSION = ?. Liefert 0
     * betroffene Zeilen, wenn ein anderer Sachbearbeiter zwischenzeitlich gespeichert
     * hat — dann rollt die Transaktion zurück und der Nutzer erhält einen Hinweis.
     */
    public int aktualisiere(TxContext ctx, SozialFall geaendert, long erwarteteVersion) throws SQLException {
        try (PreparedStatement stmt = ctx.connection().prepareStatement(
                "UPDATE SOZIAL_FALL SET REGELBEDARFSSTUFE = ?, FLAG_MERKZEICHEN_G = ?, FLAG_WARMWASSER = ?, "
                        + "KDU_MIETE = ?, KDU_BETRIEBSKOSTEN = ?, KDU_HEIZUNG = ?, "
                        + "EINKOMMEN_RENTE = ?, EINKOMMEN_ERWERB = ? "
                        + "WHERE ID = ? AND VERSION = ?")) {
            stmt.setInt(1, geaendert.regelbedarfsstufe());
            stmt.setInt(2, geaendert.merkzeichenG() ? 1 : 0);
            stmt.setInt(3, geaendert.warmwasser() ? 1 : 0);
            stmt.setBigDecimal(4, geaendert.kduMiete());
            stmt.setBigDecimal(5, geaendert.kduBetriebskosten());
            stmt.setBigDecimal(6, geaendert.kduHeizung());
            stmt.setBigDecimal(7, geaendert.einkommenRente());
            stmt.setBigDecimal(8, geaendert.einkommenErwerb());
            stmt.setLong(9, geaendert.id());
            stmt.setLong(10, erwarteteVersion);
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
                rs.getInt(5) == 1,
                nullSafeBetrag(rs.getBigDecimal(6)),
                nullSafeBetrag(rs.getBigDecimal(7)),
                nullSafeBetrag(rs.getBigDecimal(8)),
                nullSafeBetrag(rs.getBigDecimal(9)),
                nullSafeBetrag(rs.getBigDecimal(10)),
                rs.getLong(11));
    }

    private BigDecimal nullSafeBetrag(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2) : value;
    }
}
