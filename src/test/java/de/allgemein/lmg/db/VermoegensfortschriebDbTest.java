package de.allgemein.lmg.db;

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
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verbrauchsfortschreibung des Vermögens (§ 90 SGB XII, Codereview CR-015):
 * Der über dem Schonvermögen liegende Anteil verbraucht sich mit der ersten
 * Auszahlung — ab dem Folgemonat wird nichts mehr abgezogen; der Storno bucht
 * den dokumentierten Einsatz der Zeile zurück.
 */
@EnabledIfEnvironmentVariable(named = "TX_URL", matches = ".+")
class VermoegensfortschriebDbTest {

    private static final BigDecimal BEDARF = new BigDecimal("863.00"); // RBS 1 + 300 KdU

    private static SimpleConnectionPool pool;
    private static SozialLeistungsService service;

    @BeforeAll
    static void aufbauen() throws Exception {
        Class.forName("oracle.jdbc.OracleDriver");
        pool = new SimpleConnectionPool(System.getenv("TX_URL"), System.getenv("TX_USER"),
                System.getenv("TX_PASS"), 4);
        service = new SozialLeistungsService(new TxManager(pool));
        aufraeumen();
    }

    @AfterAll
    static void aufraeumen() throws Exception {
        try (Connection c = DriverManager.getConnection(
                System.getenv("TX_URL"), System.getenv("TX_USER"), System.getenv("TX_PASS"))) {
            c.setAutoCommit(true);
            Statement st = c.createStatement();
            for (String tabelle : new String[]{"JOURNAL", "ZAHLUNG", "SOZIAL_FALL"}) {
                st.executeUpdate("DELETE FROM " + tabelle + " WHERE BG_ID IN "
                        + "(SELECT ID FROM BEDARFSGEMEINSCHAFT WHERE AKTENZEICHEN LIKE 'BG-VF-%')");
            }
            st.executeUpdate("DELETE FROM BEDARFSGEMEINSCHAFT WHERE AKTENZEICHEN LIKE 'BG-VF-%'");
        }
    }

    private static de.allgemein.lmg.model.Bedarfsgemeinschaft legeVorgangAn(String az) throws Exception {
        var bg = service.legeBedarfsgemeinschaftAn(az, "Vera Fortschrieb",
                new BigDecimal("1500.00"), null, null, null, null,
                new BigDecimal("10500.00"), "sb-vf");
        SozialFall fall = new SozialFall(1, bg.id(), 1, false, false,
                new BigDecimal("300.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, 1L);
        service.legeSozialFallAn(bg.id(), fall, "sb-vf");
        return bg;
    }

    @Test
    void ersterMonatZiehtAbZweiterMonatNichtMehr() throws Exception {
        var bg = legeVorgangAn("BG-VF-A");

        // Monat 1: Bedarf 863 − verwertbares Vermögen 500 = 363; Vermögen fällt auf 10.000
        var erste = service.ordneAuszahlungAn(new Auszahlungsauftrag(
                bg.id(), "VF-A-1", "2026-11", "sb-vf", false, null, null, null));
        assertEquals(0, erste.zahlung().auszahlungsbetrag().compareTo(new BigDecimal("363.00")));
        assertEquals(0, erste.zahlung().vermoegenseinsatz().compareTo(new BigDecimal("500.00")));
        assertEquals(0, vermoegen("BG-VF-A").compareTo(new BigDecimal("10000.00")),
                "Einsatz verbraucht sich: Bestand = Schonvermögen");

        // Monat 2: KEIN Abzug mehr — der Rest liegt im Schonvermögen
        var zweite = service.ordneAuszahlungAn(new Auszahlungsauftrag(
                bg.id(), "VF-A-2", "2026-12", "sb-vf", false, null, null, null));
        assertEquals(0, zweite.zahlung().auszahlungsbetrag().compareTo(BEDARF),
                "Ab dem Folgemonat wird der volle Bedarf gezahlt");
        assertEquals(0, zweite.zahlung().vermoegenseinsatz().compareTo(BigDecimal.ZERO));
        assertEquals(0, vermoegen("BG-VF-A").compareTo(new BigDecimal("10000.00")),
                "Kein weiterer Vermögensverzehr");
    }

    @Test
    void stornoBuchtDenDokumentiertenEinsatzZurueck() throws Exception {
        var bg = legeVorgangAn("BG-VF-B");
        var zahlung = service.ordneAuszahlungAn(new Auszahlungsauftrag(
                bg.id(), "VF-B-1", "2026-11", "sb-vf", false, null, null, null)).zahlung();
        assertEquals(0, zahlung.auszahlungsbetrag().compareTo(new BigDecimal("363.00")));

        service.freigebenZahlung(zahlung.id(), "sb-zweit");
        service.storniereZahlung(zahlung.id(), "sb-admin");

        assertEquals(0, vermoegen("BG-VF-B").compareTo(new BigDecimal("10500.00")),
                "Storno stellt den dokumentierten Einsatz (500) wieder her");
        // Nach dem Storno ist der Monat frei: eine neue Anordnung zieht wieder ab
        var erneut = service.ordneAuszahlungAn(new Auszahlungsauftrag(
                bg.id(), "VF-B-2", "2026-11", "sb-vf", false, null, null, null)).zahlung();
        assertEquals(0, erneut.auszahlungsbetrag().compareTo(new BigDecimal("363.00")));
        assertEquals(0, vermoegen("BG-VF-B").compareTo(new BigDecimal("10000.00")));
    }

    private static BigDecimal vermoegen(String aktenzeichen) throws Exception {
        try (Connection c = DriverManager.getConnection(System.getenv("TX_URL"),
                System.getenv("TX_USER"), System.getenv("TX_PASS"));
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT VERMOEGEN FROM BEDARFSGEMEINSCHAFT "
                     + "WHERE AKTENZEICHEN = '" + aktenzeichen + "'")) {
            rs.next();
            return rs.getBigDecimal(1);
        }
    }
}
