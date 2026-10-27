package de.allgemein.lmg.dao;

import de.allgemein.lmg.calc.Satzstand;
import de.allgemein.lmg.service.FachlicheException;
import de.allgemein.tx.TxContext;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Kurz gesagt: Hier werden die Geldsätze aus der Gesetzestabelle gelesen.
 * Die Regelbedarfsstufen stehen in der Tabelle SATZSTAND — für jeden Monat
 * gilt der Stand, der zuletzt gültig geworden war. Diese Klasse holt den
 * passenden Stand zum Bezugsmonat.
 *
 * Datenzugriff auf SATZSTAND — die je Jahr gepflegten Regelbedarfsstufen
 * (Anlage zu § 28 SGB XII). Für einen Bezugsmonat gilt der zuletzt wirksam
 * gewordenen Stand (größter GUELTIG_AB ≤ Monat): Neue Zeilen gelten ab ihrem
 * Gültigkeitsmonat, alte bleiben für zurückliegende Monate lesbar.
 */
public final class SatzstandDao {

    private static final String COLUMNS =
            "GUELTIG_AB, RBS_1, RBS_2, RBS_3, RBS_4, RBS_5, RBS_6";

    /** Für den Bezugsmonat gültiger Satzstand (letzter GUELTIG_AB ≤ Monat). */
    public Satzstand findeFuerMonat(TxContext ctx, String monat) throws SQLException {
        try (PreparedStatement stmt = ctx.connection().prepareStatement(
                "SELECT " + COLUMNS + " FROM SATZSTAND WHERE GUELTIG_AB <= ? "
                        + "ORDER BY GUELTIG_AB DESC FETCH FIRST 1 ROWS ONLY")) {
            stmt.setString(1, monat);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    throw fachfehler(monat);
                }
                return mappe(rs);
            }
        }
    }

    /** Variante für die Saga (Connection der laufenden Saga-Transaktion). */
    public Satzstand findeFuerMonat(Connection c, String monat) throws SQLException {
        try (PreparedStatement stmt = c.prepareStatement(
                "SELECT " + COLUMNS + " FROM SATZSTAND WHERE GUELTIG_AB <= ? "
                        + "ORDER BY GUELTIG_AB DESC FETCH FIRST 1 ROWS ONLY")) {
            stmt.setString(1, monat);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    throw fachfehler(monat);
                }
                return mappe(rs);
            }
        }
    }

    private static FachlicheException fachfehler(String monat) {
        return new FachlicheException(FachlicheException.RBS_NICHT_KONFIGURIERT,
                "Kein Satzstand für den Bezugsmonat " + monat + " gepflegt "
                        + "(Tabelle SATZSTAND benötigt eine Zeile mit GUELTIG_AB <= " + monat + ")");
    }

    private static Satzstand mappe(ResultSet rs) throws SQLException {
        return new Satzstand(rs.getString(1),
                nullable(rs.getBigDecimal(2)), nullable(rs.getBigDecimal(3)),
                nullable(rs.getBigDecimal(4)), nullable(rs.getBigDecimal(5)),
                nullable(rs.getBigDecimal(6)), nullable(rs.getBigDecimal(7)));
    }

    private static BigDecimal nullable(BigDecimal wert) {
        return wert == null ? BigDecimal.ZERO : wert;
    }
}