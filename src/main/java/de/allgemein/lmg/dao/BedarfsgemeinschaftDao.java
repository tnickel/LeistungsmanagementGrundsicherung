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
            "ID, AKTENZEICHEN, HAUPTPERSON_NAME, STATUS_AKTIV, BUDGET_REST, WIEDERVORLAGE_AM, VERSION, "
                    + "IBAN, BIC, KONTOINHABER, VERMOEGEN";

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

    /** Lookup über das eindeutige Aktenzeichen (Unique-Index UQ_BG_AKTENZEICHEN). */
    public Optional<Bedarfsgemeinschaft> findeNachAktenzeichen(TxContext ctx,
            String aktenzeichen) throws SQLException {
        try (PreparedStatement stmt = ctx.connection().prepareStatement(
                "SELECT " + COLUMNS + " FROM BEDARFSGEMEINSCHAFT WHERE AKTENZEICHEN = ?")) {
            stmt.setString(1, aktenzeichen);
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
                            String iban, String bic, String kontoinhaber, BigDecimal vermoegen,
                            long erwarteteVersion) throws SQLException {
        try (PreparedStatement stmt = ctx.connection().prepareStatement(
                "UPDATE BEDARFSGEMEINSCHAFT SET STATUS_AKTIV = ?, WIEDERVORLAGE_AM = ?, "
                        + "IBAN = ?, BIC = ?, KONTOINHABER = ?, VERMOEGEN = ? "
                        + "WHERE ID = ? AND VERSION = ?")) {
            stmt.setInt(1, statusAktiv ? 1 : 0);
            if (wiedervorlageAm != null) {
                stmt.setDate(2, Date.valueOf(wiedervorlageAm));
            } else {
                stmt.setNull(2, java.sql.Types.DATE);
            }
            stmt.setString(3, iban);
            stmt.setString(4, bic);
            stmt.setString(5, kontoinhaber);
            stmt.setBigDecimal(6, vermoegen);
            stmt.setLong(7, id);
            stmt.setLong(8, erwarteteVersion);
            return stmt.executeUpdate();
        }
    }

    /** Wie aktualisiere, aber ohne VERMOEGEN anzufassen (alte Maske ohne Vermögensfeld). */
    public int aktualisiereVermoegenUnveraendert(TxContext ctx, long id, boolean statusAktiv,
                            LocalDate wiedervorlageAm, String iban, String bic, String kontoinhaber,
                            long erwarteteVersion) throws SQLException {
        try (PreparedStatement stmt = ctx.connection().prepareStatement(
                "UPDATE BEDARFSGEMEINSCHAFT SET STATUS_AKTIV = ?, WIEDERVORLAGE_AM = ?, "
                        + "IBAN = ?, BIC = ?, KONTOINHABER = ? "
                        + "WHERE ID = ? AND VERSION = ?")) {
            stmt.setInt(1, statusAktiv ? 1 : 0);
            if (wiedervorlageAm != null) {
                stmt.setDate(2, Date.valueOf(wiedervorlageAm));
            } else {
                stmt.setNull(2, java.sql.Types.DATE);
            }
            stmt.setString(3, iban);
            stmt.setString(4, bic);
            stmt.setString(5, kontoinhaber);
            stmt.setLong(6, id);
            stmt.setLong(7, erwarteteVersion);
            return stmt.executeUpdate();
        }
    }

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

    /** Legt den Vorgang an (ID über SEQ_BG); Aktenzeichen-Eindeutigkeit prüft die DB. */
    public Bedarfsgemeinschaft einfuegen(TxContext ctx, long id, String aktenzeichen, String hauptpersonName,
                                         BigDecimal startBudget, LocalDate wiedervorlageAm,
                                         String iban, String bic, String kontoinhaber) throws SQLException {
        return einfuegen(ctx, id, aktenzeichen, hauptpersonName, startBudget, wiedervorlageAm,
                iban, bic, kontoinhaber, BigDecimal.ZERO);
    }

    public Bedarfsgemeinschaft einfuegen(TxContext ctx, long id, String aktenzeichen, String hauptpersonName,
                                         BigDecimal startBudget, LocalDate wiedervorlageAm,
                                         String iban, String bic, String kontoinhaber,
                                         BigDecimal vermoegen) throws SQLException {
        try (PreparedStatement stmt = ctx.connection().prepareStatement(
                "INSERT INTO BEDARFSGEMEINSCHAFT (ID, AKTENZEICHEN, HAUPTPERSON_NAME, STATUS_AKTIV, "
                        + "BUDGET_REST, WIEDERVORLAGE_AM, IBAN, BIC, KONTOINHABER, VERMOEGEN) "
                        + "VALUES (?, ?, ?, 1, ?, ?, ?, ?, ?, ?)")) {
            stmt.setLong(1, id);
            stmt.setString(2, aktenzeichen);
            stmt.setString(3, hauptpersonName);
            stmt.setBigDecimal(4, startBudget);
            if (wiedervorlageAm != null) {
                stmt.setDate(5, Date.valueOf(wiedervorlageAm));
            } else {
                stmt.setNull(5, java.sql.Types.DATE);
            }
            stmt.setString(6, iban);
            stmt.setString(7, bic);
            stmt.setString(8, kontoinhaber);
            stmt.setBigDecimal(9, vermoegen);
            stmt.executeUpdate();
        }
        return new Bedarfsgemeinschaft(id, aktenzeichen, hauptpersonName, true, startBudget,
                wiedervorlageAm, 1L, iban, bic, kontoinhaber, vermoegen);
    }

    public Bedarfsgemeinschaft einfuegen(TxContext ctx, long id, String aktenzeichen, String hauptpersonName,
                                         BigDecimal startBudget, LocalDate wiedervorlageAm) throws SQLException {
        return einfuegen(ctx, id, aktenzeichen, hauptpersonName, startBudget, wiedervorlageAm, null, null, null);
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

    /** Erhöht das Restbudget (Storno); wird nur innerhalb der BG-Sperre aufgerufen. */
    public void erhoehenBudget(TxContext ctx, long id, BigDecimal betrag) throws SQLException {
        try (PreparedStatement stmt = ctx.connection().prepareStatement(
                "UPDATE BEDARFSGEMEINSCHAFT SET BUDGET_REST = BUDGET_REST + ? WHERE ID = ?")) {
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
        BigDecimal vermoegen = rs.getBigDecimal(11);
        return new Bedarfsgemeinschaft(
                rs.getLong(1),
                rs.getString(2),
                rs.getString(3),
                rs.getInt(4) == 1,
                rs.getBigDecimal(5),
                wiedervorlage == null ? null : wiedervorlage.toLocalDate(),
                rs.getLong(7),
                rs.getString(8),
                rs.getString(9),
                rs.getString(10),
                vermoegen == null ? BigDecimal.ZERO.setScale(2) : vermoegen);
    }
}
