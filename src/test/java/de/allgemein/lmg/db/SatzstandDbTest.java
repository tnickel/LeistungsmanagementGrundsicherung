package de.allgemein.lmg.db;

import de.allgemein.lmg.calc.Satzstand;
import de.allgemein.lmg.dao.SatzstandDao;
import de.allgemein.lmg.model.SozialFall;
import de.allgemein.lmg.service.Auszahlungsauftrag;
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
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Satztabelle SATZSTAND (Codereview CR-016): Die Regelbedarfsstufen stehen je
 * Jahr in der Datenbank — die Berechnung des Produktionspfads wählt den zum
 * Bezugsmonat gültigen Stand. Der Jahreswechsel ist ein INSERT, keine
 * Codeänderung; ohne passenden Stand wird fachlich abgewiesen.
 */
@EnabledIfEnvironmentVariable(named = "TX_URL", matches = ".+")
class SatzstandDbTest {

    private static final String TEST_ZEILE = "2027-01-TEST";

    private static SimpleConnectionPool pool;
    private static SatzstandDao satzstandDao;
    private static SozialLeistungsService service;
    private static TxManager txManager;

    @BeforeAll
    static void aufbauen() throws Exception {
        Class.forName("oracle.jdbc.OracleDriver");
        pool = new SimpleConnectionPool(System.getenv("TX_URL"), System.getenv("TX_USER"),
                System.getenv("TX_PASS"), 4);
        txManager = new TxManager(pool);
        satzstandDao = new SatzstandDao();
        service = new SozialLeistungsService(txManager);
        aufraeumen();
        try (Connection c = DriverManager.getConnection(
                System.getenv("TX_URL"), System.getenv("TX_USER"), System.getenv("TX_PASS"));
             Statement st = c.createStatement()) {
            st.executeUpdate("DELETE FROM SATZSTAND WHERE GUELTIG_AB = '2027-01'");
            st.executeUpdate("DELETE FROM JOURNAL WHERE BG_ID IN "
                    + "(SELECT ID FROM BEDARFSGEMEINSCHAFT WHERE AKTENZEICHEN LIKE 'BG-SZ-%')");
            st.executeUpdate("DELETE FROM ZAHLUNG WHERE BG_ID IN "
                    + "(SELECT ID FROM BEDARFSGEMEINSCHAFT WHERE AKTENZEICHEN LIKE 'BG-SZ-%')");
            st.executeUpdate("DELETE FROM SOZIAL_FALL WHERE BG_ID IN "
                    + "(SELECT ID FROM BEDARFSGEMEINSCHAFT WHERE AKTENZEICHEN LIKE 'BG-SZ-%')");
            st.executeUpdate("DELETE FROM BEDARFSGEMEINSCHAFT WHERE AKTENZEICHEN LIKE 'BG-SZ-%'");
        }
    }

    @AfterAll
    static void aufraeumen() throws Exception {
        try (Connection c = DriverManager.getConnection(
                System.getenv("TX_URL"), System.getenv("TX_USER"), System.getenv("TX_PASS"));
             Statement st = c.createStatement()) {
            st.executeUpdate("DELETE FROM SATZSTAND WHERE GUELTIG_AB = '2027-01'");
            for (String tabelle : new String[]{"JOURNAL", "ZAHLUNG", "SOZIAL_FALL"}) {
                st.executeUpdate("DELETE FROM " + tabelle + " WHERE BG_ID IN "
                        + "(SELECT ID FROM BEDARFSGEMEINSCHAFT WHERE AKTENZEICHEN LIKE 'BG-SZ-%')");
            }
            st.executeUpdate("DELETE FROM BEDARFSGEMEINSCHAFT WHERE AKTENZEICHEN LIKE 'BG-SZ-%'");
        }
    }

    @Test
    void regelbedarfe2026AusDerDatenbank() throws Exception {
        Satzstand stand = txManager.execute(de.allgemein.tx.TxOptions.DEFAULT,
                ctx -> satzstandDao.findeFuerMonat(ctx, "2026-10"));
        assertEquals(0, stand.regelbedarf(1).compareTo(new BigDecimal("563.00")));
        assertEquals(0, stand.regelbedarf(6).compareTo(new BigDecimal("357.00")));
        assertEquals("2026-01", stand.gueltigAb());
    }

    @Test
    void monatVorErsterZeileWirdFachlichAbgewiesen() throws Exception {
        txManager.execute(de.allgemein.tx.TxOptions.DEFAULT, ctx -> {
            assertThrows(de.allgemein.lmg.service.FachlicheException.class,
                    () -> satzstandDao.findeFuerMonat(ctx, "2025-12"));
            return null;
        });
    }

    @Test
    void jahreswechselIstEinInsertKeineCodeaenderung() throws Exception {
        // Neuer Stand ab 2027-01 (beispielhafte Fortschreibung): RBS 1 = 600,00
        try (Connection c = DriverManager.getConnection(
                System.getenv("TX_URL"), System.getenv("TX_USER"), System.getenv("TX_PASS"));
             Statement st = c.createStatement()) {
            st.executeUpdate("INSERT INTO SATZSTAND (GUELTIG_AB, RBS_1, RBS_2, RBS_3, RBS_4, RBS_5, RBS_6) "
                    + "VALUES ('2027-01', 600.00, 540.00, 480.00, 500.00, 415.00, 380.00)");
        }

        // Dezember 2026 bleibt beim alten Stand, Januar 2027 rechnet mit dem neuen:
        Satzstand dezember = txManager.execute(de.allgemein.tx.TxOptions.DEFAULT,
                ctx -> satzstandDao.findeFuerMonat(ctx, "2026-12"));
        Satzstand januar = txManager.execute(de.allgemein.tx.TxOptions.DEFAULT,
                ctx -> satzstandDao.findeFuerMonat(ctx, "2027-01"));
        assertEquals(0, dezember.regelbedarf(1).compareTo(new BigDecimal("563.00")));
        assertEquals(0, januar.regelbedarf(1).compareTo(new BigDecimal("600.00")));

        // Und der Service-Pfad folgt: gleicher Fall, zwei Monate, zwei Beträge
        var bg = service.legeBedarfsgemeinschaftAn("BG-SZ-A", "Sandra Satz",
                new BigDecimal("2000.00"), null, "sb-sz");
        SozialFall fall = new SozialFall(1, bg.id(), 1, false, false,
                new BigDecimal("300.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, 1L);
        service.legeSozialFallAn(bg.id(), fall, "sb-sz");
        var alt = service.ordneAuszahlungAn(new Auszahlungsauftrag(
                bg.id(), "SZ-A-2026", "2026-12", "sb-sz", false, null, null, null)).zahlung();
        var neu = service.ordneAuszahlungAn(new Auszahlungsauftrag(
                bg.id(), "SZ-A-2027", "2027-01", "sb-sz", false, null, null, null)).zahlung();
        assertEquals(0, alt.auszahlungsbetrag().compareTo(new BigDecimal("863.00")),
                "563 + 300 KdU");
        assertEquals(0, neu.auszahlungsbetrag().compareTo(new BigDecimal("900.00")),
                "600 + 300 KdU — Fortschreibung allein aus der DB-Zeile");
    }
}
