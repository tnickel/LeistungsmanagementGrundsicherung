package de.allgemein.testsweet;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Laufweite SQL-Prüfungen sind Teil des Ergebnisses, auch bei SQL-/Verbindungsfehlern. */
final class GlobaleVerifikation {
    @FunctionalInterface interface Verbindung { Connection oeffne() throws SQLException; }

    static List<Auftrag.Pruefung> pruefe(Verbindung verbindung, long erwarteteVorgaenge,
                                       List<Auftrag> auftraege, RunDataScope scope) {
        List<Auftrag.Pruefung> result = new ArrayList<>();
        String bg = "SELECT ID FROM BEDARFSGEMEINSCHAFT WHERE " + RunDataScope.OWNED_ROWS;
        List<String> scopeParameters = List.of(scope.prefix(), scope.prefix());
        result.add(anzahl(verbindung, "Global: Anzahl Vorgänge dieses Laufs",
                "SELECT COUNT(*) FROM BEDARFSGEMEINSCHAFT WHERE " + RunDataScope.OWNED_ROWS,
                erwarteteVorgaenge, scopeParameters));
        result.add(anzahl(verbindung, "Global: keine Übergabe ohne Freigabevermerk",
                "SELECT COUNT(*) FROM ZAHLUNG WHERE ZAHLUNGSSTATUS = 'UEBERGEBEN' "
                        + "AND FREIGEGEBEN_VON IS NULL AND BG_ID IN (" + bg + ")", 0, scopeParameters));
        if (!auftraege.isEmpty() && auftraege.stream().allMatch(a -> a.szenarioTyp.startsWith("STRESS-"))) {
            long users = auftraege.size() / 2;
            result.add(anzahl(verbindung, "Global Stress: exakt eine angelegte Zahlung je User",
                    "SELECT COUNT(*) FROM ZAHLUNG WHERE ZAHLUNGSSTATUS='ANGELEGT' AND FREIGEGEBEN_VON IS NULL "
                            + "AND BG_ID IN (" + bg + ")", users, scopeParameters));
            result.add(anzahl(verbindung, "Global Stress: keine zusätzlichen Zahlungen",
                    "SELECT COUNT(*) FROM ZAHLUNG WHERE BG_ID IN (" + bg + ")", users, scopeParameters));
            result.add(anzahl(verbindung, "Global Stress: alle Fälle exakt Version 2",
                    "SELECT COUNT(*) FROM SOZIAL_FALL WHERE VERSION=2 AND BG_ID IN (" + bg + ")", users, scopeParameters));
            result.add(anzahl(verbindung, "Global Stress: vier Journalzeilen je User",
                    "SELECT COUNT(*) FROM BEDARFSGEMEINSCHAFT b WHERE b.ID IN (" + bg + ") "
                            + "AND (SELECT COUNT(*) FROM JOURNAL j WHERE j.BG_ID=b.ID) <> 4", 0, scopeParameters));
            result.add(anzahl(verbindung, "Global Stress: keine negativen Budgets",
                    "SELECT COUNT(*) FROM BEDARFSGEMEINSCHAFT WHERE BUDGET_REST < 0 AND ID IN (" + bg + ")", 0, scopeParameters));
            java.math.BigDecimal expectedAmount = auftraege.stream().flatMap(a -> a.erwartungen.stream())
                    .filter(e -> e.beschreibung().equals("Auszahlungsbetrag nach geänderter Miete"))
                    .map(e -> new java.math.BigDecimal(e.erwartet()))
                    .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
            result.add(betrag(verbindung, "Global Stress: Zahlungssumme entspricht allen erwarteten Anordnungen",
                    "SELECT NVL(SUM(AUSZAHLUNGSBETRAG),0) FROM ZAHLUNG WHERE BG_ID IN (" + bg + ")", expectedAmount, scopeParameters));
        }
        // Einzelparameter vermeiden das Oracle-IN-Limit bei großen Fehlerwellen.
        auftraege.stream().filter(a -> a.status == Auftrag.Status.FEHLGESCHLAGEN)
                .map(a -> a.aktenzeichen).filter(az -> az != null && !az.isBlank() && !az.equals("-"))
                .distinct().forEach(az -> result.add(anzahl(verbindung,
                        "Global: keine Storno-Spuren zu fehlgeschlagenem Auftrag " + az,
                        "SELECT COUNT(*) FROM ZAHLUNG WHERE ZAHLUNGSSTATUS = 'STORNIERT' AND BG_ID IN "
                                + "(SELECT ID FROM BEDARFSGEMEINSCHAFT WHERE AKTENZEICHEN = ?)",
                        0, List.of(az))));
        return List.copyOf(result);
    }

    private static Auftrag.Pruefung betrag(Verbindung verbindung, String name, String sql,
                                           java.math.BigDecimal expected, List<String> parameter) {
        try (Connection c = verbindung.oeffne(); var st = c.prepareStatement(sql)) {
            for (int i = 0; i < parameter.size(); i++) st.setString(i + 1, parameter.get(i));
            try (var rs = st.executeQuery()) {
                if (!rs.next() || rs.getBigDecimal(1) == null) throw new SQLException("Summenabfrage ohne Betrag");
                java.math.BigDecimal actual = rs.getBigDecimal(1);
                return new Auftrag.Pruefung(name, expected.toPlainString(), actual.toPlainString(),
                        expected.compareTo(actual) == 0, null);
            }
        } catch (Exception e) {
            return new Auftrag.Pruefung(name, expected.toPlainString(), "<SQL-FEHLER>", false, e.getClass().getSimpleName());
        }
    }

    private static Auftrag.Pruefung anzahl(Verbindung verbindung, String name, String sql,
                                           long erwartet, List<String> parameter) {
        try (Connection c = verbindung.oeffne(); var st = c.prepareStatement(sql)) {
            for (int i = 0; i < parameter.size(); i++) st.setString(i + 1, parameter.get(i));
            try (var rs = st.executeQuery()) {
                if (!rs.next()) throw new SQLException("COUNT-Abfrage lieferte keine Zeile");
                long ist = rs.getLong(1);
                if (rs.wasNull()) throw new SQLException("COUNT-Abfrage lieferte NULL");
                return new Auftrag.Pruefung(name, String.valueOf(erwartet), String.valueOf(ist),
                        ist == erwartet, null);
            }
        } catch (Exception e) {
            return new Auftrag.Pruefung(name, String.valueOf(erwartet), "<SQL-FEHLER>", false,
                    e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }
}
