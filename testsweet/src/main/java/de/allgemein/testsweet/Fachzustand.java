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
    private static final java.util.Set<String> READ_AUDIT_COLUMNS = java.util.Set.of(
            "ID", "BG_ID", "AKTION_TEXT", "SACHBEARBEITER_ID", "ERSTELLT_AM");
    private static final List<String> READ_AUDIT_TYPES = List.of("Vorgangsdaten (Detail)",
            "Bescheiddaten", "Bescheid-Druckansicht", "Auszahlungsdaten (Wiederholung)");
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
                } else if (!isAllowedReadAudit(row)) {
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

    /** Ohne eigenen Journaltyp wird nur der vollständige bekannte Produktvertrag freigestellt.
     * Neue Texte, zusätzliche Spalten und fremde BGs sind ausdrücklich keine Einsichts-Ausnahme. */
    private boolean isAllowedReadAudit(Map<String, String> row) {
        if (!row.keySet().equals(READ_AUDIT_COLUMNS) || row.get("ID") == null
                || !row.get("ID").matches("[1-9][0-9]*") || row.get("ERSTELLT_AM") == null
                || row.get("ERSTELLT_AM").isBlank()) return false;
        String actor = row.get("SACHBEARBEITER_ID"), bg = row.get("BG_ID");
        if (actor == null || actor.isBlank() || !actor.equals(actor.trim()) || actor.length() > 50
                || actor.indexOf('\n') >= 0 || actor.indexOf('\r') >= 0 || bg == null
                || tabellen.getOrDefault("BEDARFSGEMEINSCHAFT", List.of()).stream()
                    .noneMatch(value -> bg.equals(value.get("ID")))) return false;
        return READ_AUDIT_TYPES.stream().anyMatch(type -> ("Einsicht: " + type + " durch " + actor
                + " (Zugriffsprotokoll § 35 SGB I / Art. 32 DSGVO)").equals(row.get("AKTION_TEXT")));
    }

    /**
     * Fachliche Prüfsumme (SHA-256, auf 16 Hex-Zeichen gekürzt) über alle Tabellen
     * ohne die erlaubten Einsichts-Audits im Journal. Gleiche Prüfsumme zweier
     * Zustände belegt denselben fachlichen Bestand — die Nachrechnung kann die
     * Vorher-/Nachher-Werte selbst vergleichen, statt der Aussage zu vertrauen.
     */
    String pruefsumme() {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            for (var tabelle : tabellen.entrySet()) {
                digest.update((tabelle.getKey() + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
                for (var zeile : tabelle.getValue()) {
                    if (tabelle.getKey().equals("JOURNAL") && isAllowedReadAudit(zeile)) continue;
                    digest.update((new java.util.TreeMap<>(zeile) + "\n")
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
            }
            return java.util.HexFormat.of().formatHex(digest.digest()).substring(0, 16);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 nicht verfügbar", e);
        }
    }
}
