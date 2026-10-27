package de.allgemein.lmg.dao;

import de.allgemein.lmg.model.Zahlung;
import de.allgemein.tx.TxContext;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Kurz gesagt: Das ist der einzige Weg zur Tabelle ZAHLUNG.
 * Hier werden Auszahlungen angelegt, gelesen, freigegeben, an die Kasse
 * übergeben und storniert. Gegen Doppelbuchungen schützt die einmalige
 * Vorgangsnummer, die nur hier geprüft wird.
 *
 * Datenzugriff für ZAHLUNG. Die VORGANGS_NR ist unique — sie ist der
 * Idempotenz-Anker gegen Doppelbuchungen bei wiederholten Requests.
 */
public final class ZahlungDao {

    private static final String COLUMNS =
            "ID, BG_ID, FALL_ID, VORGANGS_NR, ZAHLUNGSMONAT, AUSZAHLUNGSBETRAG, ZAHLUNGSSTATUS, ERSTELLT_AM, "
                    + "SACHBEARBEITER_ID, FREIGEGEBEN_VON, EMPFAENGER_IBAN, EMPFAENGER_BIC, EMPFAENGER_NAME, "
                    + "VERMOEGENSEINSATZ";



    /** Gibt es für die Bedarfsgemeinschaft und den Monat schon eine nicht stornierte Zahlung? */
    public boolean existiertAktiveFuerMonat(TxContext ctx, long bgId, String zahlungsmonat) throws SQLException {
        try (PreparedStatement stmt = ctx.connection().prepareStatement(
                "SELECT COUNT(*) FROM ZAHLUNG WHERE BG_ID = ? AND ZAHLUNGSMONAT = ? AND ZAHLUNGSSTATUS <> 'STORNIERT'")) {
            stmt.setLong(1, bgId);
            stmt.setString(2, zahlungsmonat);
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

    /** Alle bereitgestellten Zahlungen aktiver Vorgänge mit Zeilensperre — der Kassenlauf übergibt sie gesammelt. */
    public List<Zahlung> bereiteZahlungenGesperrt(TxContext ctx) throws SQLException {
        return bereiteZahlungenGesperrt(ctx, null);
    }

    /** Optional auf ein wörtliches Aktenzeichen-Präfix begrenzte, gesperrte Menge. */
    public List<Zahlung> bereiteZahlungenGesperrt(TxContext ctx, String aktenzeichenPraefix)
            throws SQLException {
        try (PreparedStatement stmt = ctx.connection().prepareStatement(
                "SELECT z.ID, z.BG_ID, z.FALL_ID, z.VORGANGS_NR, z.ZAHLUNGSMONAT, z.AUSZAHLUNGSBETRAG, "
                        + "z.ZAHLUNGSSTATUS, z.ERSTELLT_AM, z.SACHBEARBEITER_ID, z.FREIGEGEBEN_VON, "
                        + "z.EMPFAENGER_IBAN, z.EMPFAENGER_BIC, z.EMPFAENGER_NAME, z.VERMOEGENSEINSATZ "
                        + "FROM ZAHLUNG z WHERE z.ZAHLUNGSSTATUS = 'BEREIT' "
                        + "AND EXISTS (SELECT 1 FROM BEDARFSGEMEINSCHAFT b "
                        + "WHERE b.ID = z.BG_ID AND b.STATUS_AKTIV = 1"
                        + (aktenzeichenPraefix == null ? "" : " AND SUBSTR(b.AKTENZEICHEN, 1, ?) = ?")
                        + ") "
                        + "FOR UPDATE ORDER BY z.ID")) {
            if (aktenzeichenPraefix != null) {
                stmt.setInt(1, aktenzeichenPraefix.length());
                stmt.setString(2, aktenzeichenPraefix);
            }
            return leseListe(stmt.executeQuery());
        }
    }

    /**
     * Übergibt genau die zuvor gesperrten Zahlungen (Kassenlauf): nur die IDs der
     * per FOR UPDATE gesperrten Menge — Anzahl und Summe bleiben konsistent.
     */
    public int uebergebeGesperrte(TxContext ctx, List<Long> ids) throws SQLException {
        if (ids.isEmpty()) {
            return 0;
        }
        int anzahl = 0;
        // Oracle 19c erlaubt höchstens 1000 Ausdrücke pro IN-Liste. Alle Blöcke
        // bleiben auf derselben Verbindung in derselben Transaktion gesperrt.
        for (int offset = 0; offset < ids.size(); offset += 1000) {
            List<Long> block = ids.subList(offset, Math.min(offset + 1000, ids.size()));
            String platzhalter = String.join(",", java.util.Collections.nCopies(block.size(), "?"));
            try (PreparedStatement stmt = ctx.connection().prepareStatement(
                "UPDATE ZAHLUNG SET ZAHLUNGSSTATUS = 'UEBERGEBEN' "
                        + "WHERE ID IN (" + platzhalter + ") AND ZAHLUNGSSTATUS = 'BEREIT'")) {
                for (int i = 0; i < block.size(); i++) {
                    stmt.setLong(i + 1, block.get(i));
                }
                int geaendert = stmt.executeUpdate();
                if (geaendert != block.size()) {
                    throw new SQLException("Kassenübergabe stimmt nicht mit der gesperrten Menge überein");
                }
                anzahl += geaendert;
            }
        }
        return anzahl;
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

    /**
     * Statuswechsel nur aus erlaubten Quell-Status (Guard gegen Race Conditions, z. B.
     * Storno gegen einen parallelen Kassenlauf). Liefert die Anzahl geänderter Zeilen —
     * 0 bedeutet: Zustand hat sich unter der Sperre geändert.
     */
    public int aktualisiereStatusWennIn(TxContext ctx, long id, String zielStatus,
            List<String> erlaubteQuellStatus) throws SQLException {
        String platzhalter = String.join(",", java.util.Collections.nCopies(erlaubteQuellStatus.size(), "?"));
        try (PreparedStatement stmt = ctx.connection().prepareStatement(
                "UPDATE ZAHLUNG SET ZAHLUNGSSTATUS = ? "
                        + "WHERE ID = ? AND ZAHLUNGSSTATUS IN (" + platzhalter + ")")) {
            stmt.setString(1, zielStatus);
            stmt.setLong(2, id);
            for (int i = 0; i < erlaubteQuellStatus.size(); i++) {
                stmt.setString(3 + i, erlaubteQuellStatus.get(i));
            }
            return stmt.executeUpdate();
        }
    }

    /**
     * Setzt den Status einer einzelnen Zahlung; Aufruf nur unter Zahlungssperre
     * (lockForUpdate). Guard: nur ANGELEGT/BEREIT dürfen wechseln — ein paralleler
     * Kassenlauf (UEBERGEBEN) oder Doppel-Storno wird so erkannt (Rückgabe 0).
     */
    public int aktualisiereStatus(TxContext ctx, long id, String neuerStatus) throws SQLException {
        return aktualisiereStatusWennIn(ctx, id, neuerStatus, List.of("ANGELEGT", "BEREIT"));
    }

    /** Fügt die Zahlung ein; id im Aufruf vorher über die Sequence gezogen. */
    public Zahlung einfuegen(TxContext ctx, long id, long bgId, Long fallId, String vorgangsNr,
                             String zahlungsmonat, BigDecimal betrag, String status,
                             String sachbearbeiterId, String freigegebenVon,
                             String iban, String bic, String empfaengerName) throws SQLException {
        return einfuegen(ctx, id, bgId, fallId, vorgangsNr, zahlungsmonat, betrag, status,
                sachbearbeiterId, freigegebenVon, iban, bic, empfaengerName, BigDecimal.ZERO);
    }

    /**
     * Fügt die Zahlung einschließlich des bei der Anordnung verbrauchten verwertbaren
     * Vermögens ein (§ 90 SGB XII Verbrauchsfortschreibung — der Storno bucht genau
     * diesen Betrag ins Vermögen zurück).
     */
    public Zahlung einfuegen(TxContext ctx, long id, long bgId, Long fallId, String vorgangsNr,
                             String zahlungsmonat, BigDecimal betrag, String status,
                             String sachbearbeiterId, String freigegebenVon,
                             String iban, String bic, String empfaengerName,
                             BigDecimal vermoegenseinsatz) throws SQLException {
        BigDecimal einsatz = vermoegenseinsatz == null ? BigDecimal.ZERO : vermoegenseinsatz;
        try (PreparedStatement stmt = ctx.connection().prepareStatement(
                "INSERT INTO ZAHLUNG (ID, BG_ID, FALL_ID, VORGANGS_NR, ZAHLUNGSMONAT, AUSZAHLUNGSBETRAG, ZAHLUNGSSTATUS, "
                        + "SACHBEARBEITER_ID, FREIGEGEBEN_VON, EMPFAENGER_IBAN, EMPFAENGER_BIC, EMPFAENGER_NAME, "
                        + "VERMOEGENSEINSATZ) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
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
            stmt.setBigDecimal(13, einsatz);
            stmt.executeUpdate();
        }
        // DB-Default und Spaltenpräzision sind maßgeblich, auch für ERSTELLT_AM.
        return finde(ctx, id).orElseThrow(() -> new SQLException(
                "Eingefügte Zahlung ist innerhalb der Transaktion nicht lesbar"));
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
        boolean fallIstNull = rs.wasNull(); // unmittelbar nach dem zugehörigen getLong auswerten
        Timestamp erstellt = rs.getTimestamp(8);
        return new Zahlung(
                rs.getLong(1),
                rs.getLong(2),
                fallIstNull ? null : fallId,
                rs.getString(4),
                rs.getString(5),
                rs.getBigDecimal(6).setScale(2, java.math.RoundingMode.UNNECESSARY),
                rs.getString(7),
                erstellt == null ? null : erstellt.toInstant(),
                rs.getString(9),
                rs.getString(10),
                rs.getString(11),
                rs.getString(12),
                rs.getString(13),
                rs.getBigDecimal(14).setScale(2, java.math.RoundingMode.UNNECESSARY));
    }
}