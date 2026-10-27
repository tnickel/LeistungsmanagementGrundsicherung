package de.allgemein.testsweet;

import de.allgemein.lmg.model.SozialFall;
import de.allgemein.lmg.service.Auszahlungsauftrag;
import de.allgemein.lmg.service.FachlicheException;
import de.allgemein.lmg.service.SozialLeistungsService;
import de.allgemein.tx.SimpleConnectionPool;
import de.allgemein.tx.TxManager;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.DriverManager;
import java.util.List;
import java.util.UUID;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class NegativZustandsDbTest {
    @org.junit.jupiter.api.io.TempDir Path temp;

    @Test void positiveUndNegativeSzenarienPruefenKasseVermoegensStornoUndVorherNachherZustand() throws Exception {
        String url = System.getenv("TX_URL");
        assumeTrue(url != null && !url.isBlank(), "TX_URL für Oracle-Nachweis erforderlich");
        String user = System.getenv().getOrDefault("TX_USER", "tx_demo"), pass = System.getenv().getOrDefault("TX_PASS", "tx_demo");
        String prefix = "SWEET-STATE-" + UUID.randomUUID().toString().substring(0, 8) + "-";
        String employee = "sb-state-" + UUID.randomUUID().toString().substring(0, 8);
        Simulator simulator = new Simulator();
        WorkerIdentityFixture.prepare(simulator, employee);
        var orders = new java.util.ArrayList<>(new SzenarioBibliothek(42).erzeuge(60, prefix + "P-"));
        orders.addAll(new SzenarioBibliothek(20261013).erzeugeNegativ(500, prefix + "N-"));
        try (var pool = new SimpleConnectionPool(url, user, pass, 3); var log = new SweetLog(temp)) {
            var manager = new TxManager(pool);
            var service = new SozialLeistungsService(manager);
            set(simulator, "service", service);
            set(simulator, "fachTxManager", manager);
            set(simulator, "pool", pool);
            set(simulator, "log", log);
            set(simulator, "auftraege", orders);
            new Mitarbeiter(simulator, employee, 0).run();
            assertTrue(orders.stream().allMatch(o -> o.status == Auftrag.Status.FERTIG_GESPIELT),
                    () -> orders.stream().filter(o -> o.status != Auftrag.Status.FERTIG_GESPIELT)
                            .map(o -> o.szenarioTyp + ": " + o.fehlerText).toList().toString());
            assertEquals(560, new Verifikator(url, user, pass).verifiziereAlle(orders, log),
                    () -> orders.stream().filter(o -> o.status != Auftrag.Status.VERIFIZIERT)
                            .map(o -> o.aktenzeichen + ": " + o.pruefungen().stream().filter(p -> !p.ok()).toList()).toList().toString());
            assertTrue(orders.stream().filter(o -> o.szenarioTyp.startsWith("NEG-")).allMatch(o -> o.pruefungen().stream()
                    .anyMatch(p -> p.beschreibung().startsWith("Fehlversuch ohne Fachänderung:"))));
            assertEquals("BEREIT", service.ladeDetail(orders.getFirst().bgId).zahlungen().getFirst().status(),
                    "Kassen-Szenarien dürfen eine fremde vorbereitete Freigabe nicht übernehmen");
        } finally {
            try (var connection = DriverManager.getConnection(url, user, pass)) {
                connection.setAutoCommit(false);
                for (String table : List.of("JOURNAL", "BESCHEID", "ZAHLUNG", "SOZIAL_FALL", "BEDARFSGEMEINSCHAFT")) {
                    String where = table.equals("BEDARFSGEMEINSCHAFT") ? "AKTENZEICHEN LIKE ?"
                            : "BG_ID IN (SELECT ID FROM BEDARFSGEMEINSCHAFT WHERE AKTENZEICHEN LIKE ?)";
                    try (var statement = connection.prepareStatement("DELETE FROM " + table + " WHERE " + where)) {
                        statement.setString(1, prefix + "%"); statement.executeUpdate();
                    }
                }
                try (var statement = connection.prepareStatement("DELETE FROM JOURNAL WHERE BG_ID IS NULL AND SACHBEARBEITER_ID = ?")) {
                    statement.setString(1, employee); statement.executeUpdate();
                    for (Auftrag order : orders) if (order.kontext.get("kassenKennung") instanceof String actor) {
                        statement.setString(1, actor); statement.executeUpdate();
                    }
                }
                connection.commit();
            }
        }
    }

    @Test void oracleAbwehrBleibtUnveraendertUndJedePersistierteMutationWirdErkannt() throws Exception {
        String url = System.getenv("TX_URL");
        assumeTrue(url != null && !url.isBlank(), "TX_URL für Oracle-Nachweis erforderlich");
        String user = System.getenv().getOrDefault("TX_USER", "tx_demo"), pass = System.getenv().getOrDefault("TX_PASS", "tx_demo");
        Class.forName("oracle.jdbc.OracleDriver");
        String az = "SWEET-STATE-" + UUID.randomUUID().toString().substring(0, 12);
        long id = 0;
        try (var pool = new SimpleConnectionPool(url, user, pass, 3)) {
            var service = new SozialLeistungsService(new TxManager(pool));
            var bg = service.legeBedarfsgemeinschaftAn(az, "Zustandsprüfung", new BigDecimal("2000"), null, "sb-state-a");
            id = bg.id();
            var zero = BigDecimal.ZERO;
            service.legeSozialFallAn(id, new SozialFall(0, id, 1, false, false, 0, 0, 0,
                    false, zero, zero, zero, zero, zero, zero, zero, zero, zero, 1), "sb-state-a");
            var payment = service.ordneAuszahlungAn(new Auszahlungsauftrag(id, UUID.randomUUID().toString(), "2026-10", "sb-state-a"));
            Fachzustand before = Fachzustand.lese(url, user, pass, az);
            var rejected = assertThrows(FachlicheException.class, () -> service.freigebenZahlung(payment.zahlung().id(), "sb-state-a"));
            assertEquals(FachlicheException.BERECHTIGUNG, rejected.code());
            before.verlangeUnveraendert(Fachzustand.lese(url, user, pass, az));
            for (String sql : List.of("UPDATE BEDARFSGEMEINSCHAFT SET BUDGET_REST = BUDGET_REST - 1 WHERE ID = ?",
                    "UPDATE BEDARFSGEMEINSCHAFT SET VERMOEGEN = VERMOEGEN + 1 WHERE ID = ?",
                    "UPDATE SOZIAL_FALL SET VERSION = VERSION + 1 WHERE BG_ID = ?",
                    "UPDATE ZAHLUNG SET AUSZAHLUNGSBETRAG = AUSZAHLUNGSBETRAG + 1 WHERE BG_ID = ?",
                    "UPDATE JOURNAL SET AKTION_TEXT = 'CONTROLLED_MUTATION' WHERE BG_ID = ?")) {
                Fachzustand original = Fachzustand.lese(url, user, pass, az);
                try (var connection = DriverManager.getConnection(url, user, pass); var statement = connection.prepareStatement(sql)) {
                    statement.setLong(1, id);
                    assertTrue(statement.executeUpdate() > 0);
                }
                var mutated = Fachzustand.lese(url, user, pass, az);
                assertThrows(IllegalStateException.class, () -> original.verlangeUnveraendert(mutated), sql);
            }
        } finally {
            if (id != 0) try (var connection = DriverManager.getConnection(url, user, pass)) {
                connection.setAutoCommit(false);
                for (String table : List.of("JOURNAL", "BESCHEID", "ZAHLUNG", "SOZIAL_FALL", "BEDARFSGEMEINSCHAFT")) {
                    try (var statement = connection.prepareStatement("DELETE FROM " + table + " WHERE "
                            + (table.equals("BEDARFSGEMEINSCHAFT") ? "ID" : "BG_ID") + " = ?")) {
                        statement.setLong(1, id); statement.executeUpdate();
                    }
                }
                connection.commit();
            }
        }
    }

    private static void set(Object object, String name, Object value) throws Exception {
        var field = object.getClass().getDeclaredField(name); field.setAccessible(true); field.set(object, value);
    }
}
