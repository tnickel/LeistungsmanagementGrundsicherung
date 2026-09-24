package de.allgemein.lmg.dao;

import de.allgemein.lmg.model.Bedarfsgemeinschaft;
import de.allgemein.tx.TxContext;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Datenzugriff für BEDARFSGEMEINSCHAFT. Alle Methoden laufen auf der Verbindung
 * der laufenden Transaktion ({@link TxContext}) — DAOs öffnen und schließen
 * bewusst keine eigenen Verbindungen, sonst brächen die Transaktionsgrenzen.
 */
public final class BedarfsgemeinschaftDao {

    private static final String COLUMNS =
            "ID, AKTENZEICHEN, HAUPTPERSON_NAME, STATUS_AKTIV, BUDGET_REST, WIEDERVORLAGE_AM, VERSION";

    public List<Bedarfsgemeinschaft> listeAlle(TxContext ctx) throws SQLException {
        try (PreparedStatement stmt = ctx.connection().prepareStatement(
                "SELECT " + COLUMNS + " FROM BEDARFSGEMEINSCHAFT ORDER BY AKTENZEICHEN")) {
            return leseListe(stmt.executeQuery());
        }
    }

    public Optional<Bedarfsgemeinschaft> finde(TxContext ctx, long id) throws SQLException {
        try (PreparedStatement stmt = ctx.connection().prepareStatement(
                "SELECT " + COLUMNS + " FROM BEDARFSGEMEINSCHAFT WHERE ID = ?")) {
            stmt.setLong(1, id);
            return leseEinzeln(stmt.executeQuery());
        }
    }

    /**
     * Pessimistisches Locking für den Buchungsweg: exclusive Zeilensperre, damit
     * parallele Sachbearbeiter nacheinander buchen statt sich gegenseitig
     * überschreiben ("Lost Update"). Wartet, bis die Sperre frei ist.
     */
    public Optional<Bedarfsgemeinschaft> lockForUpdate(TxContext ctx, long id) throws SQLException {
        try (PreparedStatement stmt = ctx.connection().prepareStatement(
                "SELECT " + COLUMNS + " FROM BEDARFSGEMEINSCHAFT WHERE ID = ? FOR UPDATE")) {
            stmt.setLong(1, id);
            return leseEinzeln(stmt.executeQuery());
        }
    }

    /**
     * Maskenspeicherung mit optimistischem Locking: die WHERE-Klausel prüft die
     * VERSION, die der Sachbearbeiter im Browser hatte. Liefert die Anzahl
     * betroffener Zeilen — 0 heißt "zwischenzeitlich geändert".
     */
    public int aktualisiere(TxContext ctx, long id, boolean statusAktiv, LocalDate wiedervorlageAm,
                            long erwarteteVersion) throws SQLException {
        try (PreparedStatement stmt = ctx.connection().prepareStatement(
                "UPDATE BEDARFSGEMEINSCHAFT SET STATUS_AKTIV = ?, WIEDERVORLAGE_AM = ? "
                        + "WHERE ID = ? AND VERSION = ?")) {
            stmt.setInt(1, statusAktiv ? 1 : 0);
            if (wiedervorlageAm != null) {
                stmt.setDate(2, Date.valueOf(wiedervorlageAm));
            } else {
                stmt.setNull(2, java.sql.Types.DATE);
            }
            stmt.setLong(3, id);
            stmt.setLong(4, erwarteteVersion);
            return stmt.executeUpdate();
        }
    }

    /** Mindert das Restbudget; wird nur innerhalb der BG-Sperre (lockForUpdate) aufgerufen. */
    public void mindereBudget(TxContext ctx, long id, BigDecimal betrag) throws SQLException {
        try (PreparedStatement stmt = ctx.connection().prepareStatement(
                "UPDATE BEDARFSGEMEINSCHAFT SET BUDGET_REST = BUDGET_REST - ? WHERE ID = ?")) {
            stmt.setBigDecimal(1, betrag);
            stmt.setLong(2, id);
            stmt.executeUpdate();
        }
    }

    public long naechsteId(TxContext ctx) throws SQLException {
        return naechsteSequence(ctx, "SEQ_BG");
    }

    static long naechsteSequence(TxContext ctx, String sequence) throws SQLException {
        try (Statement stmt = ctx.connection().createStatement();
             ResultSet rs = stmt.executeQuery("SELECT " + sequence + ".NEXTVAL FROM DUAL")) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private List<Bedarfsgemeinschaft> leseListe(ResultSet rs) throws SQLException {
        List<Bedarfsgemeinschaft> liste = new ArrayList<>();
        while (rs.next()) {
            liste.add(mappe(rs));
        }
        return liste;
    }

    private Optional<Bedarfsgemeinschaft> leseEinzeln(ResultSet rs) throws SQLException {
        if (rs.next()) {
            return Optional.of(mappe(rs));
        }
        return Optional.empty();
    }

    private Bedarfsgemeinschaft mappe(ResultSet rs) throws SQLException {
        Date wiedervorlage = rs.getDate(6);
        return new Bedarfsgemeinschaft(
                rs.getLong(1),
                rs.getString(2),
                rs.getString(3),
                rs.getInt(4) == 1,
                rs.getBigDecimal(5),
                wiedervorlage == null ? null : wiedervorlage.toLocalDate(),
                rs.getLong(7));
    }
}
