package de.allgemein.lmg.db;

import de.allgemein.lmg.dao.BedarfsgemeinschaftDao;
import de.allgemein.lmg.dao.BescheidDao;
import de.allgemein.lmg.dao.ZahlungDao;
import de.allgemein.tx.SimpleConnectionPool;
import de.allgemein.tx.TxContext;
import de.allgemein.tx.TxManager;
import de.allgemein.tx.TxOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** DAO-Rückgabe muss exakt der Oracle-Zeile entsprechen; alle Testdaten werden zurückgerollt. */
@EnabledIfEnvironmentVariable(named = "TX_URL", matches = ".+")
class DaoInsertTimestampDbTest {

    @Test
    void zahlungsRueckgabeEntsprichtFrischGelesenerZeileMitIdentischemTimestamp() throws Exception {
        try (var fixture = new RollbackFixture()) {
            long bgId = createBg(fixture.context);
            var dao = new ZahlungDao();
            long id = dao.naechsteId(fixture.context);
            var inserted = dao.einfuegen(fixture.context, id, bgId, null, UUID.randomUUID().toString(),
                    "2026-10", new BigDecimal("123.00"), "ANGELEGT", "sb-dao-time", null,
                    "DE89370400440532013000", "COBADEFFXXX", "DAO-Zeitprüfung", new BigDecimal("7.00"));
            var stored = dao.finde(fixture.context, id).orElseThrow();
            assertNotNull(inserted.erstelltAm());
            assertEquals(new BigDecimal("123.00"), inserted.auszahlungsbetrag(), "Cent-Skalierung auch bei vollen Euro erhalten");
            assertEquals(new BigDecimal("7.00"), inserted.vermoegenseinsatz());
            assertEquals(stored.erstelltAm(), inserted.erstelltAm(), "kein separat erzeugtes JVM-Instant");
            assertEquals(stored, inserted, "einschließlich DB-Skalierung und Null-Mapping");
        }
    }

    @Test
    void bescheidRueckgabeEntsprichtFrischGelesenerZeileMitIdentischemTimestamp() throws Exception {
        try (var fixture = new RollbackFixture()) {
            long bgId = createBg(fixture.context);
            var dao = new BescheidDao();
            long id = dao.naechsteId(fixture.context);
            LocalDate date = LocalDate.of(2026, 10, 13);
            var inserted = dao.einfuegen(fixture.context, id, bgId, "DAO-TIME-" + UUID.randomUUID(),
                    date, "2026-10", "2026-11", new BigDecimal("123.00"), "sb-dao-time", "{\"test\":true}");
            var stored = dao.finde(fixture.context, id).orElseThrow();
            assertNotNull(inserted.erstelltAm());
            assertEquals(date, inserted.bescheidDatum(), "vorgegebenes Datum bleibt unverändert");
            assertEquals(new BigDecimal("123.00"), inserted.zahlbetrag());
            assertEquals(stored.erstelltAm(), inserted.erstelltAm(), "Oracle-SYSTIMESTAMP maßgeblich");
            assertEquals(stored, inserted, "auch Snapshot und übrige Spalten müssen übereinstimmen");
        }
    }

    private static long createBg(TxContext context) throws SQLException {
        var dao = new BedarfsgemeinschaftDao();
        long id = dao.naechsteId(context);
        dao.einfuegen(context, id, "BG-DAO-TIME-" + UUID.randomUUID(), "DAO-Zeitprüfung",
                new BigDecimal("1000.00"), null);
        return id;
    }

    private static final class RollbackFixture implements AutoCloseable {
        private final SimpleConnectionPool pool;
        private final TxManager manager;
        private final TxContext context;

        RollbackFixture() throws SQLException {
            pool = new SimpleConnectionPool(System.getenv("TX_URL"), System.getenv("TX_USER"),
                    System.getenv("TX_PASS"), 1);
            manager = new TxManager(pool);
            try {
                context = manager.begin(TxOptions.DEFAULT);
            } catch (SQLException | RuntimeException | Error error) {
                pool.close();
                throw error;
            }
        }

        @Override
        public void close() {
            try {
                manager.finish(context); // nie committen; vollständiger Rollback auch nach AssertionError
            } finally {
                pool.close();
            }
        }
    }
}
