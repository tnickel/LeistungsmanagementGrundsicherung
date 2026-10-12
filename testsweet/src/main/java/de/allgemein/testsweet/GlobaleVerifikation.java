package de.allgemein.testsweet;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Laufweite SQL-Prüfungen sind Teil des Ergebnisses, auch bei SQL-/Verbindungsfehlern. */
final class GlobaleVerifikation {
    @FunctionalInterface interface Verbindung { Connection oeffne() throws SQLException; }

    static List<Auftrag.Pruefung> pruefe(Verbindung verbindung, long erwarteteVorgaenge,
                                       List<Auftrag> auftraege) {
        List<Auftrag.Pruefung> result = new ArrayList<>();
        String bg = "SELECT ID FROM BEDARFSGEMEINSCHAFT WHERE AKTENZEICHEN LIKE 'SWEET-%'";
        result.add(anzahl(verbindung, "Global: Anzahl SWEET-Vorgänge",
                "SELECT COUNT(*) FROM BEDARFSGEMEINSCHAFT WHERE AKTENZEICHEN LIKE 'SWEET-%'",
                erwarteteVorgaenge, List.of()));
        result.add(anzahl(verbindung, "Global: keine Übergabe ohne Freigabevermerk",
                "SELECT COUNT(*) FROM ZAHLUNG WHERE ZAHLUNGSSTATUS = 'UEBERGEBEN' "
                        + "AND FREIGEGEBEN_VON IS NULL AND BG_ID IN (" + bg + ")", 0, List.of()));
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
