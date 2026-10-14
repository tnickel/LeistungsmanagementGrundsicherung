package de.allgemein.testsweet;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Vollständiger persistierter Zustand eines Vorgangs vor/nach einem abgewehrten Schritt.
 * Vorbereitungen liegen vor dem ersten Snapshot. Neue Einsichts-Audits sind ausdrücklich
 * erlaubt; vorhandene Journalzeilen, Fachbuchungen und sämtliche Fachspalten sind unveränderlich.
 * Sequences, Sitzungen und das technische Transaktions-Audit gehören nicht zum Fachbestand. */
final class Fachzustand {
    private final Map<String, List<Map<String, String>>> tabellen;

    Fachzustand(Map<String, List<Map<String, String>>> tabellen) {
        this.tabellen = new LinkedHashMap<>();
        tabellen.forEach((table, rows) -> this.tabellen.put(table,
                rows.stream().map(row -> java.util.Collections.unmodifiableMap(new LinkedHashMap<>(row))).toList()));
    }

    static Fachzustand lese(String url, String user, String pass, String aktenzeichen) throws Exception {
        try (Connection connection = DriverManager.getConnection(url, user, pass)) {
            return lese(connection, aktenzeichen);
        }
    }

    /** Die geliehene Verbindung bleibt offen; ihr Pool übernimmt danach release/reset. */
    static Fachzustand lese(Connection connection, String aktenzeichen) throws Exception {
        Map<String, List<Map<String, String>>> tables = new LinkedHashMap<>();
        connection.setAutoCommit(false);
        try {
            // Alle Tabellen sehen denselben Oracle-Snapshot, auch bei parallelen Mitarbeitern.
            try (var statement = connection.createStatement()) {
                statement.execute("SET TRANSACTION READ ONLY");
            }
            for (String table : List.of("BEDARFSGEMEINSCHAFT", "SOZIAL_FALL", "ZAHLUNG", "BESCHEID", "JOURNAL")) {
                String where = table.equals("BEDARFSGEMEINSCHAFT") ? "AKTENZEICHEN = ?"
                        : "BG_ID IN (SELECT ID FROM BEDARFSGEMEINSCHAFT WHERE AKTENZEICHEN = ?)";
                List<Map<String, String>> rows = new ArrayList<>();
                try (var statement = connection.prepareStatement("SELECT * FROM " + table + " WHERE " + where + " ORDER BY ID")) {
                    statement.setString(1, aktenzeichen);
                    try (ResultSet result = statement.executeQuery()) {
                        var metadata = result.getMetaData();
                        while (result.next()) {
                            Map<String, String> row = new LinkedHashMap<>();
                            for (int column = 1; column <= metadata.getColumnCount(); column++) {
                                // getString liest auch den Bescheid-CLOB als Inhalt, nicht als Treiberobjekt.
                                row.put(metadata.getColumnLabel(column), result.getString(column));
                            }
                            rows.add(row);
                        }
                    }
                }
                tables.put(table, rows);
            }
        } finally {
            // Auch fehlerhafte SELECTs dürfen keine offene Read-only-Transaktion zurücklassen.
            connection.rollback();
        }
        return new Fachzustand(tables);
    }

    List<String> abweichungen(Fachzustand danach) {
        List<String> differences = new ArrayList<>();
        for (String table : tabellen.keySet()) {
            var before = tabellen.get(table);
            var after = danach.tabellen.get(table);
            if (after == null) { differences.add(table + ": Zustand fehlt"); continue; }
            if (!table.equals("JOURNAL")) {
                if (!before.equals(after)) differences.add(table + ": Fachbestand verändert");
                continue;
            }
            Map<String, Map<String, String>> existing = new LinkedHashMap<>();
            before.forEach(row -> existing.put(row.get("ID"), row));
            for (var row : after) {
                var old = existing.remove(row.get("ID"));
                if (old != null) {
                    if (!old.equals(row)) differences.add("JOURNAL: vorhandene Zeile verändert");
                } else if (row.get("AKTION_TEXT") == null || !row.get("AKTION_TEXT").startsWith("Einsicht: ")) {
                    differences.add("JOURNAL: unerlaubte neue Fachbuchung");
                }
            }
            if (!existing.isEmpty()) differences.add("JOURNAL: vorhandene Zeile gelöscht");
        }
        return List.copyOf(differences);
    }

    void verlangeUnveraendert(Fachzustand danach) {
        var differences = abweichungen(danach);
        if (!differences.isEmpty()) throw new IllegalStateException(
                "Abgewehrter Fehlversuch hat den Fachbestand verändert: " + String.join("; ", differences));
    }
}
