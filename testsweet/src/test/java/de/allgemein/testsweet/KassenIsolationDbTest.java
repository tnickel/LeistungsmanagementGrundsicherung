package de.allgemein.testsweet;

import de.allgemein.lmg.model.SozialFall;
import de.allgemein.lmg.service.Auszahlungsauftrag;
import de.allgemein.lmg.service.FachlicheException;
import de.allgemein.lmg.service.SozialLeistungsService;
import de.allgemein.tx.SimpleConnectionPool;
import de.allgemein.tx.ConnectionPool;
import de.allgemein.tx.TxManager;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class KassenIsolationDbTest {
    @Test void scopedKasseLaesstFremdeBereiteZahlungUndWildcardAehnlichesAktenzeichenUnberuehrt()
            throws Exception {
        String url = System.getenv("TX_URL");
        assumeTrue(url != null && !url.isBlank(), "TX_URL für Oracle-Verifikation erforderlich");
        String user = System.getenv().getOrDefault("TX_USER", "tx_demo");
        String pass = System.getenv().getOrDefault("TX_PASS", "tx_demo");
        Class.forName("oracle.jdbc.OracleDriver");
        List<Long> ids = new ArrayList<>();
        String token = UUID.randomUUID().toString().substring(0, 8);
        String kassenKennung = "sb-k-" + token;
        // % und _ sind absichtlich wörtlicher Teil des Präfixes, keine SQL-Wildcards.
        String scopedPrefix = "SWEET-K%_" + token + "-";
        try (var pool = new SimpleConnectionPool(url, user, pass, 3)) {
            var service = new SozialLeistungsService(new TxManager(pool));
            long ziel = legeBereiteZahlungAn(service, scopedPrefix + "1", ids);
            long fremd = legeBereiteZahlungAn(service, "FREMD-KASSE-" + token, ids);
            long aehnlich = legeBereiteZahlungAn(service, "SWEET-Kxx" + token + "-2", ids);
            var ergebnis = service.uebergebeBereiteZahlungen(kassenKennung, scopedPrefix);
            assertEquals(1, ergebnis.anzahl());
            assertEquals(0, ergebnis.summe().compareTo(new BigDecimal("563.00")));
            assertEquals("UEBERGEBEN", service.ladeDetail(ziel).zahlungen().getFirst().status());
            assertEquals("BEREIT", service.ladeDetail(fremd).zahlungen().getFirst().status());
            assertEquals("BEREIT", service.ladeDetail(aehnlich).zahlungen().getFirst().status());
            assertEquals(0, service.uebergebeBereiteZahlungen(kassenKennung, scopedPrefix).anzahl());
            // BEREIT-Storno wird gezielt ohne konkurrierende globale Kasse geprüft.
            long fremdeZahlung = service.ladeDetail(fremd).zahlungen().getFirst().id();
            assertEquals("STORNIERT", service.storniereZahlung(fremdeZahlung, "sb-review-a").status());
            assertEquals(0, new BigDecimal("2000").compareTo(
                    service.ladeDetail(fremd).bedarfsgemeinschaft().budgetRest()));
        } finally {
            try (var c = DriverManager.getConnection(url, user, pass)) {
                c.setAutoCommit(false);
                for (long id : ids) {
                    for (String tabelle : List.of("JOURNAL", "BESCHEID", "ZAHLUNG", "SOZIAL_FALL")) {
                        try (var st = c.prepareStatement("DELETE FROM " + tabelle + " WHERE BG_ID = ?")) {
                            st.setLong(1, id);
                            st.executeUpdate();
                        }
                    }
                    try (var st = c.prepareStatement("DELETE FROM BEDARFSGEMEINSCHAFT WHERE ID = ?")) {
                        st.setLong(1, id);
                        st.executeUpdate();
                    }
                }
                try (var st = c.prepareStatement("DELETE FROM JOURNAL WHERE BG_ID IS NULL AND SACHBEARBEITER_ID = ?")) {
                    st.setString(1, kassenKennung);
                    st.executeUpdate();
                }
                c.commit();
            }
        }
    }

    @Test void scopedKasseDarfNichtMitLeeremPraefixAufGesamtbestandZurueckfallen() {
        var service = new SozialLeistungsService(new TxManager(new ConnectionPool() {
            public java.sql.Connection getConnection() { throw new AssertionError("Kein DB-Zugriff zulässig"); }
            public void release(java.sql.Connection connection) { }
            public void close() { }
        }));
        for (String praefix : new String[] {null, "", " "}) {
            var e = assertThrows(FachlicheException.class,
                    () -> service.uebergebeBereiteZahlungen("sb-review-kasse", praefix));
            assertEquals(FachlicheException.VALIDATION, e.code());
        }
    }

    private static long legeBereiteZahlungAn(SozialLeistungsService service, String az, List<Long> ids) {
        var bg = service.legeBedarfsgemeinschaftAn(az, "Kassenisolation", new BigDecimal("2000"),
                null, "sb-review-a");
        ids.add(bg.id());
        var zero = BigDecimal.ZERO;
        service.legeSozialFallAn(bg.id(), new SozialFall(0, bg.id(), 1, false, false,
                0, 0, 0, false, zero, zero, zero, zero, zero, zero, zero, zero, zero, 1), "sb-review-a");
        var zahlung = service.ordneAuszahlungAn(new Auszahlungsauftrag(bg.id(), UUID.randomUUID().toString(),
                "2026-10", "sb-review-a"));
        service.freigebenZahlung(zahlung.zahlung().id(), "sb-review-b");
        return bg.id();
    }
}
