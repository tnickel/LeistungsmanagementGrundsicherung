package de.allgemein.lmg.db;

import de.allgemein.lmg.service.SozialLeistungsService;
import de.allgemein.tx.SimpleConnectionPool;
import de.allgemein.tx.TxManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Zugriffsprotokoll (CR-035/Option D): Jede Einsicht in vollständige Falldaten
 * (Detail, Bescheiddaten, Druckansicht) durch eine Sitzung wird im Journal
 * festgehalten; Wiederholungen derselben Kennung werden binnen 60 Sekunden zu
 * einem Eintrag zusammengefasst; Batch-Aufrufe ohne Kennung bleiben unprotokolliert.
 */
@EnabledIfEnvironmentVariable(named = "TX_URL", matches = ".+")
class EinsichtsprotokollDbTest {

    private static SimpleConnectionPool pool;
    private static SozialLeistungsService service;
    private static long bgId;

    @BeforeAll
    static void aufbauen() throws Exception {
        Class.forName("oracle.jdbc.OracleDriver");
        pool = new SimpleConnectionPool(System.getenv("TX_URL"), System.getenv("TX_USER"),
                System.getenv("TX_PASS"), 4);
        service = new SozialLeistungsService(new TxManager(pool));
        aufraeumen();
        var bg = service.legeBedarfsgemeinschaftAn("BG-EINSICHT-1", "Erika Einsicht",
                new BigDecimal("900.00"), null, "sb-ep");
        service.legeSozialFallAn(bg.id(), de.allgemein.lmg.db.EinsichtsprotokollDbTest.fall(bg.id()),
                "sb-ep");
        bgId = bg.id();
    }

    private static de.allgemein.lmg.model.SozialFall fall(long bgId) {
        return new de.allgemein.lmg.model.SozialFall(1, bgId, 1, false, false,
                new BigDecimal("300.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, 1L);
    }

    @AfterAll
    static void aufraeumen() throws Exception {
        try (Connection c = DriverManager.getConnection(
                System.getenv("TX_URL"), System.getenv("TX_USER"), System.getenv("TX_PASS"))) {
            c.setAutoCommit(true);
            Statement st = c.createStatement();
            for (String tabelle : new String[]{"JOURNAL", "ZAHLUNG", "SOZIAL_FALL"}) {
                st.executeUpdate("DELETE FROM " + tabelle + " WHERE BG_ID IN "
                        + "(SELECT ID FROM BEDARFSGEMEINSCHAFT WHERE AKTENZEICHEN LIKE 'BG-EINSICHT-%')");
            }
            st.executeUpdate("DELETE FROM BESCHEID WHERE BG_ID IN "
                    + "(SELECT ID FROM BEDARFSGEMEINSCHAFT WHERE AKTENZEICHEN LIKE 'BG-EINSICHT-%')");
            st.executeUpdate("DELETE FROM BEDARFSGEMEINSCHAFT WHERE AKTENZEICHEN LIKE 'BG-EINSICHT-%'");
        }
    }

    private static int einsichten(String sachbearbeiter) throws Exception {
        try (Connection c = DriverManager.getConnection(System.getenv("TX_URL"),
                System.getenv("TX_USER"), System.getenv("TX_PASS"));
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM JOURNAL WHERE BG_ID = " + bgId
                     + " AND AKTION_TEXT LIKE 'Einsicht:%'"
                     + (sachbearbeiter == null ? "" : " AND SACHBEARBEITER_ID = '" + sachbearbeiter + "'"))) {
            rs.next();
            return rs.getInt(1);
        }
    }

    @Test
    void detailEinsichtWirdProtokolliert() throws Exception {
        service.ladeDetail(bgId, "sb-a");
        assertEquals(1, einsichten("sb-a"), "Die Einsicht steht mit Kennung im Journal");
    }

    @Test
    void wiederholungInnerhalbEinerMinuteWirdZusammengefasst() throws Exception {
        service.ladeDetail(bgId, "sb-b");
        service.ladeDetail(bgId, "sb-b");
        service.ladeDetail(bgId, "sb-b");
        assertEquals(1, einsichten("sb-b"),
                "Drei Detail-Reloads derselben Kennung ergeben EINEN Eintrag (60-s-Zusammenfassung)");
    }

    @Test
    void andereKennungUndAndererZweckZaehlenEinzel() throws Exception {
        service.ladeDetail(bgId, "sb-c");
        service.letzterBescheid(bgId, "sb-c"); // kein Bescheid vorhanden → kein Eintrag
        assertEquals(1, einsichten("sb-c"), "Ohne vorhandenen Bescheid gibt es keinen Bescheid-Eintrag");
        service.ladeDetail(bgId, "sb-d");
        assertEquals(1, einsichten("sb-d"));
    }

    @Test
    void aufrufOhneKennungWirdNichtProtokolliert() throws Exception {
        int vorher = einsichten(null);
        service.ladeDetail(bgId);
        service.letzterBescheid(bgId);
        assertEquals(vorher, einsichten(null),
                "Batch-/Testpfade ohne Kennung schreiben kein Einsichtsprotokoll");
    }
}
