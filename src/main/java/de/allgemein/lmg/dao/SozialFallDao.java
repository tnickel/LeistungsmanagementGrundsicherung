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
 * Datenzugriff für SOZIAL_FALL — die Parameter der Bedarfs- und Einkommensermittlung
 * (§§ 27a, 30, 82 SGB XII). Maskenänderungen laufen über {@link #aktualisiere} mit
 * VERSION-Prüfung (optimistisches Locking); der Buchungsweg liest im Bann der BG-Sperre.
 */
public final class SozialFallDao {

    private static final String COLUMNS =
            "ID, BG_ID, REGELBEDARFSSTUFE, FLAG_MERKZEICHEN_G, FLAG_SCHWANGER, "
                    + "ANZ_KINDER_U7, ANZ_KINDER_U16, FLAG_WARMWASSER, "
                    + "KDU_MIETE, KDU_BETRIEBSKOSTEN, KDU_HEIZUNG, "
                    + "EINKOMMEN_RENTE, EINKOMMEN_ERWERB, "
                    + "ABZ_STEUERN, ABZ_SV, ABZ_VERSICHERUNGEN, ABZ_ERWERBSAUSGABEN, VERSION, "
                    + "ANZ_KINDER_MINDERJ";

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
                "INSERT INTO SOZIAL_FALL (ID, BG_ID, REGELBEDARFSSTUFE, FLAG_MERKZEICHEN_G, FLAG_SCHWANGER, "
                        + "ANZ_KINDER_U7, ANZ_KINDER_U16, FLAG_WARMWASSER, "
                        + "KDU_MIETE, KDU_BETRIEBSKOSTEN, KDU_HEIZUNG, EINKOMMEN_RENTE, EINKOMMEN_ERWERB, "
                        + "ABZ_STEUERN, ABZ_SV, ABZ_VERSICHERUNGEN, ABZ_ERWERBSAUSGABEN, ANZ_KINDER_MINDERJ) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            stmt.setLong(1, id);
            stmt.setLong(2, neu.bgId());
            stmt.setInt(3, neu.regelbedarfsstufe());
            stmt.setInt(4, neu.merkzeichenG() ? 1 : 0);
            stmt.setInt(5, neu.schwanger() ? 1 : 0);
            stmt.setInt(6, neu.anzKinderUnter7());
            stmt.setInt(7, neu.anzKinderUnter16());
            stmt.setInt(8, neu.warmwasser() ? 1 : 0);
            stmt.setBigDecimal(9, neu.kduMiete());
            stmt.setBigDecimal(10, neu.kduBetriebskosten());
            stmt.setBigDecimal(11, neu.kduHeizung());
            stmt.setBigDecimal(12, neu.einkommenRente());
            stmt.setBigDecimal(13, neu.einkommenErwerb());
            stmt.setBigDecimal(14, neu.abzSteuern());
            stmt.setBigDecimal(15, neu.abzSozialversicherung());
            stmt.setBigDecimal(16, neu.abzVersicherungen());
            stmt.setBigDecimal(17, neu.abzErwerbsaufwendungen());
            stmt.setInt(18, neu.anzKinderMinderjaehrig());
            stmt.executeUpdate();
        }
        return new SozialFall(id, neu.bgId(), neu.regelbedarfsstufe(), neu.merkzeichenG(), neu.schwanger(),
                neu.anzKinderUnter7(), neu.anzKinderUnter16(), neu.anzKinderMinderjaehrig(), neu.warmwasser(),
                neu.kduMiete(), neu.kduBetriebskosten(), neu.kduHeizung(),
                neu.einkommenRente(), neu.einkommenErwerb(),
                neu.abzSteuern(), neu.abzSozialversicherung(), neu.abzVersicherungen(),
                neu.abzErwerbsaufwendungen(), 1L);
    }

    /**
     * Optimistisches Locking: UPDATE ... WHERE ID = ? AND VERSION = ?. Liefert 0
     * betroffene Zeilen, wenn ein anderer Sachbearbeiter zwischenzeitlich gespeichert
     * hat — dann rollt die Transaktion zurück und der Nutzer erhält einen Hinweis.
     */
    public int aktualisiere(TxContext ctx, SozialFall geaendert, long erwarteteVersion) throws SQLException {
        try (PreparedStatement stmt = ctx.connection().prepareStatement(
                "UPDATE SOZIAL_FALL SET REGELBEDARFSSTUFE = ?, FLAG_MERKZEICHEN_G = ?, FLAG_SCHWANGER = ?, "
                        + "ANZ_KINDER_U7 = ?, ANZ_KINDER_U16 = ?, FLAG_WARMWASSER = ?, "
                        + "KDU_MIETE = ?, KDU_BETRIEBSKOSTEN = ?, KDU_HEIZUNG = ?, "
                        + "EINKOMMEN_RENTE = ?, EINKOMMEN_ERWERB = ?, "
                        + "ABZ_STEUERN = ?, ABZ_SV = ?, ABZ_VERSICHERUNGEN = ?, ABZ_ERWERBSAUSGABEN = ?, "
                        + "ANZ_KINDER_MINDERJ = ? "
                        + "WHERE ID = ? AND VERSION = ?")) {
            stmt.setInt(1, geaendert.regelbedarfsstufe());
            stmt.setInt(2, geaendert.merkzeichenG() ? 1 : 0);
            stmt.setInt(3, geaendert.schwanger() ? 1 : 0);
            stmt.setInt(4, geaendert.anzKinderUnter7());
            stmt.setInt(5, geaendert.anzKinderUnter16());
            stmt.setInt(6, geaendert.warmwasser() ? 1 : 0);
            stmt.setBigDecimal(7, geaendert.kduMiete());
            stmt.setBigDecimal(8, geaendert.kduBetriebskosten());
            stmt.setBigDecimal(9, geaendert.kduHeizung());
            stmt.setBigDecimal(10, geaendert.einkommenRente());
            stmt.setBigDecimal(11, geaendert.einkommenErwerb());
            stmt.setBigDecimal(12, geaendert.abzSteuern());
            stmt.setBigDecimal(13, geaendert.abzSozialversicherung());
            stmt.setBigDecimal(14, geaendert.abzVersicherungen());
            stmt.setBigDecimal(15, geaendert.abzErwerbsaufwendungen());
            stmt.setInt(16, geaendert.anzKinderMinderjaehrig());
            stmt.setLong(17, geaendert.id());
            stmt.setLong(18, erwarteteVersion);
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
                rs.getInt(6),
                rs.getInt(7),
                rs.getInt(19),
                rs.getInt(8) == 1,
                nullSafeBetrag(rs.getBigDecimal(9)),
                nullSafeBetrag(rs.getBigDecimal(10)),
                nullSafeBetrag(rs.getBigDecimal(11)),
                nullSafeBetrag(rs.getBigDecimal(12)),
                nullSafeBetrag(rs.getBigDecimal(13)),
                nullSafeBetrag(rs.getBigDecimal(14)),
                nullSafeBetrag(rs.getBigDecimal(15)),
                nullSafeBetrag(rs.getBigDecimal(16)),
                nullSafeBetrag(rs.getBigDecimal(17)),
                rs.getLong(18));
    }

    private BigDecimal nullSafeBetrag(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2) : value;
    }
}
