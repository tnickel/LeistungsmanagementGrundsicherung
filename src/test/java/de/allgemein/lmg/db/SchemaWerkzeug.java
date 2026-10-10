package de.allgemein.lmg.db;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Entwicklungs-Werkzeug: spielt die DDL-Skripte (Schema zurücksetzen) auf der
 * Oracle aus der TX_URL/TX_USER/TX_PASS-Umgebung ein. Kein Teil der Tests —
 * Aufruf z. B.:
 *
 * <pre>
 * mvn test-compile exec:java -Dexec.mainClass=de.allgemein.lmg.db.SchemaWerkzeug \
 *   -Dexec.classpathScope=test TX_URL=... TX_USER=tx_demo TX_PASS=tx_demo
 * </pre>
 *
 * Führt pro Statement die Methode {@code resetSchema} aus: DROP aller
 * Fachtabellen/Sequences/Trigger (in kindlicher Reihenfolge), danach CREATE
 * aus ddl/lmg_schema.sql und optional Seed aus ddl/lmg_seed.sql
 * (Argument "mit-seed" oder Umgebungsvariable).
 */
public final class SchemaWerkzeug {

    private SchemaWerkzeug() {
    }

    public static void main(String[] args) throws Exception {
        String url = System.getenv("TX_URL");
        String user = System.getenv("TX_USER");
        String pass = System.getenv("TX_PASS");
        if (url == null || user == null || pass == null) {
            System.err.println("TX_URL/TX_USER/TX_PASS müssen gesetzt sein");
            System.exit(2);
        }
        Class.forName("oracle.jdbc.OracleDriver");
        try (Connection conn = DriverManager.getConnection(url, user, pass)) {
            System.out.println("Verbunden mit " + url + " als " + user);
            conn.setAutoCommit(true);
            dropFachschema(conn);
            fuehreAus(conn, Files.readString(Path.of("ddl/lmg_schema.sql"), StandardCharsets.UTF_8));
            System.out.println("lmg_schema.sql eingespielt.");
            if (args.length == 0 || !"ohne-seed".equals(args[0])) {
                fuehreAus(conn, Files.readString(Path.of("ddl/lmg_seed.sql"), StandardCharsets.UTF_8));
                System.out.println("lmg_seed.sql eingespielt.");
            }
            System.out.println("Fertig.");
        }
    }

    private static void dropFachschema(Connection conn) throws SQLException {
        // Kindtabellen zuerst; Existenzfehler ignorieren wir je Objekt.
        String[] drops = {
                "DROP TABLE JOURNAL",
                "DROP TABLE BESCHEID",
                "DROP TABLE ZAHLUNG",
                "DROP TABLE SOZIAL_FALL",
                "DROP TABLE BEDARFSGEMEINSCHAFT",
                "DROP SEQUENCE SEQ_JOURNAL",
                "DROP SEQUENCE SEQ_BESCHEID",
                "DROP SEQUENCE SEQ_ZAHLUNG",
                "DROP SEQUENCE SEQ_FALL",
                "DROP SEQUENCE SEQ_BG",
        };
        for (String drop : drops) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(drop);
            } catch (SQLException e) {
                // Objekt existiert nicht — beim Zurücksetzen egal
            }
        }
        System.out.println("Fachschema zurückgesetzt (DROPs ausgeführt).");
    }

    /** Führt ein SQL-Skript Statement für Statement aus; '/'-Trenner und '--'-Kommentare beachtend. */
    private static void fuehreAus(Connection conn, String skript) throws SQLException {
        for (String statement : zerlege(skript)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(statement);
            } catch (SQLException e) {
                throw new SQLException("Fehler bei: " + kompakt(statement) + " — " + e.getMessage(), e);
            }
        }
    }

    /**
     * Zerlegt ein Skript in Einzelstatements: '--'-Kommentare und Leerzeilen entfallen.
     * PL/SQL-Blöcke (CREATE OR REPLACE TRIGGER … BEGIN … END;) werden NICHT an ihren
     * inneren Semikolons getrennt, sondern laufen bis zum '/'-Trenner durch; das
     * abschließende Semikolon normaler Statements wird entfernt (JDBC duldet keins).
     */
    private static List<String> zerlege(String skript) {
        List<String> statements = new ArrayList<>();
        StringBuilder aktuell = new StringBuilder();
        for (String zeile : skript.split("\r?\n")) {
            String bereinigt = zeile.strip();
            if (bereinigt.startsWith("--") || bereinigt.isEmpty()) {
                continue;
            }
            if (bereinigt.equals("/")) {
                // Ende eines PL/SQL-Blocks: Der Block behält sein abschließendes
                // Semikolon (END;) — ohne es wäre der Trigger/ die Prozedur ungültig.
                if (!aktuell.toString().isBlank()) {
                    statements.add(aktuell.toString().stripTrailing());
                    aktuell.setLength(0);
                }
                continue;
            }
            aktuell.append(zeile).append('\n');
            boolean plsqlBlock = aktuell.indexOf("BEGIN") >= 0;
            if (!plsqlBlock && bereinigt.endsWith(";")) {
                statements.add(ohneAbschliessendesSemikolon(aktuell));
                aktuell.setLength(0);
            }
        }
        if (!aktuell.toString().isBlank()) {
            statements.add(ohneAbschliessendesSemikolon(aktuell));
        }
        return statements;
    }

    /** JDBC (anders als sqlplus) duldet kein abschließendes Semikolon (ORA-00922). */
    private static String ohneAbschliessendesSemikolon(StringBuilder statement) {
        String bereinigt = statement.toString().stripTrailing();
        while (bereinigt.endsWith(";")) {
            bereinigt = bereinigt.substring(0, bereinigt.length() - 1).stripTrailing();
        }
        return bereinigt;
    }

    private static String kompakt(String statement) {
        String eineZeile = statement.replaceAll("\\s+", " ");
        return eineZeile.length() > 80 ? eineZeile.substring(0, 80) + "…" : eineZeile;
    }
}
