package de.allgemein.testsweet;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;

/** Nicht aus Benutzereingaben abgeleiteter Namensraum eines einzelnen Simulatorlaufs. */
record RunDataScope(String id) {
    RunDataScope {
        if (id == null || !id.matches("[0-9a-f]{32}")) {
            throw new IllegalArgumentException("Ungültige Simulator-Laufkennung");
        }
    }

    static RunDataScope create() {
        return new RunDataScope(UUID.randomUUID().toString().replace("-", ""));
    }

    String prefix() { return "SWEET-" + id + "-"; }

    /** SUBSTR statt LIKE: Der Besitzfilter hat keine Wildcard-Semantik. */
    static final String OWNED_ROWS = "SUBSTR(AKTENZEICHEN,1,LENGTH(?)) = ?";

    void bind(java.sql.PreparedStatement statement) throws SQLException {
        statement.setString(1, prefix());
        statement.setString(2, prefix());
    }

    /** Nur vom Aufrufer selbst erzeugte Laufkennungen dürfen hier übergeben werden. */
    int delete(Connection connection) throws SQLException {
        if (connection.getAutoCommit()) {
            throw new IllegalStateException("Laufbereinigung benötigt eine explizite Transaktion");
        }
        String owned = "SELECT ID FROM BEDARFSGEMEINSCHAFT WHERE " + OWNED_ROWS;
        for (String table : java.util.List.of("JOURNAL", "BESCHEID", "ZAHLUNG", "SOZIAL_FALL")) {
            try (var statement = connection.prepareStatement("DELETE FROM " + table
                    + " WHERE BG_ID IN (" + owned + ")")) {
                bind(statement);
                statement.executeUpdate();
            }
        }
        try (var statement = connection.prepareStatement(
                "DELETE FROM BEDARFSGEMEINSCHAFT WHERE " + OWNED_ROWS)) {
            bind(statement);
            return statement.executeUpdate();
        }
        // BG_ID IS NULL enthält keine verlässliche Laufzuordnung und bleibt deshalb erhalten.
    }
}
