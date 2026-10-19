package de.allgemein.testsweet;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class RunDataScopeDbTest {
    @Test void eigeneBereinigungUndGlobalePruefungLassenFremdeLaeufeUndKassenjournaleUnveraendert() throws Exception {
        String url = System.getenv("TX_URL");
        assumeTrue(url != null && !url.isBlank(), "TX_URL für Oracle-Nachweis erforderlich");
        String user = System.getenv().getOrDefault("TX_USER", "tx_demo");
        String pass = System.getenv().getOrDefault("TX_PASS", "tx_demo");
        RunDataScope own = RunDataScope.create(), foreign = RunDataScope.create();
        long foreignBg = 0, globalJournal = 0;
        try (Connection connection = DriverManager.getConnection(url, user, pass)) {
            connection.setAutoCommit(false);
            try {
                createRows(connection, own.prefix() + "0001", false);
                foreignBg = createRows(connection, foreign.prefix() + "0001", true);
                globalJournal = sequence(connection, "SEQ_JOURNAL");
                update(connection, "INSERT INTO JOURNAL (ID,AKTION_TEXT,SACHBEARBEITER_ID) VALUES (?, ?, ?)",
                        globalJournal, "Kassenlauf: fremder Lauf", "sb-sim-" + UUID.randomUUID().toString().substring(0, 8));
                connection.commit();
                var before = GlobaleVerifikation.pruefe(() -> DriverManager.getConnection(url, user, pass), 1, List.of(), own);
                assertTrue(before.stream().allMatch(Auftrag.Pruefung::ok), before.toString());
                assertEquals(1, own.delete(connection));
                connection.commit();
                for (String table : List.of("JOURNAL", "BESCHEID", "ZAHLUNG", "SOZIAL_FALL")) {
                    assertEquals(1, count(connection, "SELECT COUNT(*) FROM " + table + " WHERE BG_ID = ?", foreignBg), table);
                }
                assertEquals(1, count(connection, "SELECT COUNT(*) FROM BEDARFSGEMEINSCHAFT WHERE ID = ?", foreignBg));
                assertEquals(1, count(connection, "SELECT COUNT(*) FROM JOURNAL WHERE ID = ?", globalJournal));
                var after = GlobaleVerifikation.pruefe(() -> DriverManager.getConnection(url, user, pass), 0, List.of(), own);
                assertTrue(after.stream().allMatch(Auftrag.Pruefung::ok), after.toString());
                assertEquals(0, own.delete(connection), "Eigene Bereinigung ist idempotent");
                connection.commit();
            } finally {
                connection.rollback();
                own.delete(connection);
                foreign.delete(connection);
                if (globalJournal != 0) update(connection, "DELETE FROM JOURNAL WHERE ID = ?", globalJournal);
                connection.commit();
            }
        }
    }

    private static long createRows(Connection connection, String az, boolean inconsistentForeign) throws Exception {
        long bg = sequence(connection, "SEQ_BG"), fall = sequence(connection, "SEQ_FALL");
        update(connection, "INSERT INTO BEDARFSGEMEINSCHAFT (ID,AKTENZEICHEN,HAUPTPERSON_NAME) VALUES (?,?,?)", bg, az, "Eigene Testfixture");
        update(connection, "INSERT INTO SOZIAL_FALL (ID,BG_ID,REGELBEDARFSSTUFE) VALUES (?,?,1)", fall, bg);
        update(connection, "INSERT INTO ZAHLUNG (ID,BG_ID,FALL_ID,VORGANGS_NR,ZAHLUNGSMONAT,AUSZAHLUNGSBETRAG,ZAHLUNGSSTATUS) VALUES (?,?,?,?, '2026-10',1,?)",
                sequence(connection, "SEQ_ZAHLUNG"), bg, fall, UUID.randomUUID().toString(), inconsistentForeign ? "UEBERGEBEN" : "ANGELEGT");
        update(connection, "INSERT INTO BESCHEID (ID,BG_ID,BESCHEID_NR,BEWILLIGUNG_VON,BEWILLIGUNG_BIS,ZAHLBETRAG,ERSTELLT_VON) VALUES (?,?,?,'2026-10','2026-10',1,'sb-scope')",
                sequence(connection, "SEQ_BESCHEID"), bg, UUID.randomUUID().toString());
        update(connection, "INSERT INTO JOURNAL (ID,BG_ID,AKTION_TEXT,SACHBEARBEITER_ID) VALUES (?,?,?,'sb-scope')",
                sequence(connection, "SEQ_JOURNAL"), bg, "Eigene Testfixture");
        return bg;
    }

    private static long sequence(Connection connection, String sequence) throws Exception {
        try (var statement = connection.createStatement(); var result = statement.executeQuery("SELECT " + sequence + ".NEXTVAL FROM DUAL")) {
            assertTrue(result.next());
            return result.getLong(1);
        }
    }

    private static long count(Connection connection, String sql, long id) throws Exception {
        try (var statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (var result = statement.executeQuery()) { assertTrue(result.next()); return result.getLong(1); }
        }
    }

    private static void update(Connection connection, String sql, Object... values) throws Exception {
        try (var statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]);
            assertEquals(1, statement.executeUpdate());
        }
    }
}
