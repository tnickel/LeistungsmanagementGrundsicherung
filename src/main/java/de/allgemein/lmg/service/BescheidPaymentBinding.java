package de.allgemein.lmg.service;

import de.allgemein.lmg.calc.BerechnungsSnapshot;
import de.allgemein.lmg.dao.BescheidDao;
import de.allgemein.lmg.model.Bescheid;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.YearMonth;

/**
 * Nur eine Kontrollklasse.
 * Sie rechnet nichts neu. Vor der Zahlung prüft sie nur,
 * ob der Betrag zum erteilten Bescheid dieses Monats passt.
 */
public final class BescheidPaymentBinding {
    private static final BescheidDao DAO = new BescheidDao();

    private BescheidPaymentBinding() { }

    /** Aufruf ausschließlich unter der BG-Sperre; ohne jeden Bescheid bleibt der Demoweg offen. */
    public static void verify(Connection connection, long bgId, String month, BigDecimal actual)
            throws SQLException {
        if (DAO.zaehleFuerBg(connection, bgId) == 0) return;
        var decisions = DAO.erteilteFuerMonat(connection, bgId, month);
        if (decisions.isEmpty()) {
            throw invalid("Für den Zahlungsmonat liegt kein erteilter Bewilligungsbescheid vor; "
                    + "Bewilligungszeitraum fachlich prüfen");
        }
        if (decisions.size() > 1) {
            throw invalid("Für den Zahlungsmonat fehlt ein eindeutiger erteilter Bewilligungsbescheid: "
                    + "mehrere überlappende Altbescheide fachlich prüfen");
        }
        BigDecimal approved = approvedAmount(decisions.getFirst(), month);
        if (actual == null || approved.compareTo(actual) != 0) {
            throw invalid("Aktuelle Berechnung stimmt nicht mit dem bewilligten Monatsbetrag überein; "
                    + "vor der Auszahlung ist eine fachliche Bescheidkorrektur erforderlich");
        }
    }

    /** V1/Altbescheid: konstanter bewilligter Betrag; V2/V3: vollständig gebundener Monatsplan. */
    public static BigDecimal approvedAmount(Bescheid decision, String month) {
        try {
            YearMonth from = YearMonth.parse(decision.bewilligungVon());
            YearMonth to = YearMonth.parse(decision.bewilligungBis());
            YearMonth target = YearMonth.parse(month);
            if (!"ERTEILT".equals(decision.status()) || from.isAfter(to)
                    || target.isBefore(from) || target.isAfter(to) || to.isAfter(from.plusMonths(11))) {
                throw new IllegalArgumentException("Ungültiger Bewilligungszeitraum");
            }
            BigDecimal first = decision.zahlbetrag();
            if (first == null || first.signum() < 0) throw new IllegalArgumentException("Betrag fehlt");
            // Bei snapshotlosen Altbescheiden ist der bewilligte skalare Betrag die einzige Quelle.
            if (decision.berechnung() == null) return first;
            var snapshot = readSnapshot(decision);
            if (snapshot.monate().isEmpty()) return snapshot.ergebnis().zahlbetrag();
            for (var entry : snapshot.monate()) {
                if (entry.monat().equals(target.toString())) return entry.ergebnis().zahlbetrag();
            }
            throw new IllegalArgumentException("Monatsplan deckt Zielmonat nicht ab");
        } catch (IllegalArgumentException | NullPointerException e) {
            throw invalid("Gespeicherter Bewilligungsbescheid oder Berechnungs-Snapshot ist ungültig; "
                    + "fachliche Prüfung erforderlich, keine Neuberechnung historischer Inhalte");
        }
    }

    public static BerechnungsSnapshot.Gelesen readSnapshot(Bescheid decision) {
        try {
            var snapshot = BerechnungsSnapshot.lese(decision.berechnung());
            YearMonth from = YearMonth.parse(decision.bewilligungVon());
            YearMonth to = YearMonth.parse(decision.bewilligungBis());
            if (from.isAfter(to) || to.isAfter(from.plusMonths(11))
                    || decision.zahlbetrag() == null || decision.zahlbetrag().signum() < 0
                    || snapshot.ergebnis().bgId() != decision.bgId()
                    || snapshot.ergebnis().zahlbetrag().compareTo(decision.zahlbetrag()) != 0) {
                throw new IllegalArgumentException("Snapshot passt nicht zum Bescheid");
            }
            if (!snapshot.monate().isEmpty()) {
                YearMonth expected = from;
                for (var entry : snapshot.monate()) {
                    if (expected.isAfter(to) || !entry.monat().equals(expected.toString())
                            || entry.ergebnis().bgId() != decision.bgId()
                            || entry.ergebnis().zahlbetrag().signum() < 0) {
                        throw new IllegalArgumentException("Monatsplan ist unvollständig oder widersprüchlich");
                    }
                    expected = expected.plusMonths(1);
                }
                if (!expected.equals(to.plusMonths(1))) {
                    throw new IllegalArgumentException("Monatsplan deckt Bewilligungszeitraum nicht ab");
                }
            }
            return snapshot;
        } catch (IllegalArgumentException | NullPointerException e) {
            throw invalid("Gespeicherter Berechnungs-Snapshot ist beschädigt; fachliche Prüfung erforderlich");
        }
    }

    public static void rejectOverlap(Connection connection, long bgId, String from, String to)
            throws SQLException {
        if (DAO.ueberlapptErteilterZeitraum(connection, bgId, from, to)) {
            throw invalid("Bewilligungszeitraum überschneidet einen erteilten Bescheid; "
                    + "bestehende Bewilligung zuerst fachlich klären");
        }
    }

    private static FachlicheException invalid(String message) {
        return new FachlicheException(FachlicheException.VALIDATION, message);
    }
}