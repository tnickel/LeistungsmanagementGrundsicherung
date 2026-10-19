package de.allgemein.lmg.db;

import de.allgemein.lmg.model.SozialFall;
import de.allgemein.lmg.service.Auszahlungsauftrag;
import de.allgemein.lmg.service.FachlicheException;
import de.allgemein.lmg.service.SozialLeistungsService;
import de.allgemein.lmg.web.Benutzerverwaltung;
import de.allgemein.lmg.web.Json;
import de.allgemein.lmg.web.LmgServer;
import de.allgemein.tx.ConnectionPool;
import de.allgemein.tx.RetryPolicy;
import de.allgemein.tx.SimpleConnectionPool;
import de.allgemein.tx.TaskController;
import de.allgemein.tx.TxCommitOutcomeUnknownException;
import de.allgemein.tx.TxManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Gegenproben zu CR-035: echte Rollbacks, Retry und Auszahlungs-Replay über HTTP/Oracle. */
@EnabledIfEnvironmentVariable(named = "TX_URL", matches = ".+")
class EinsichtsprotokollRegressionDbTest {
    private static SimpleConnectionPool pool;
    private SozialLeistungsService service;
    private long bgId;
    private String vorgangsNr;
    private static final String IBAN = "DE89370400440532013000";

    @BeforeAll
    static void verbinde() throws Exception {
        Class.forName("oracle.jdbc.OracleDriver");
        pool = new SimpleConnectionPool(System.getenv("TX_URL"), System.getenv("TX_USER"),
                System.getenv("TX_PASS"), 8);
    }

    @AfterAll
    static void schliesse() {
        if (pool != null) pool.close();
    }

    @BeforeEach
    void legeTestfallAn() {
        service = new SozialLeistungsService(new TxManager(pool));
        vorgangsNr = "REVIEW-EP-" + UUID.randomUUID();
        var bg = service.legeBedarfsgemeinschaftAn("BG-" + vorgangsNr, "Fiktive Reviewperson",
                new BigDecimal("10000"), null, IBAN, "COBADEFFXXX", "Fiktive Reviewperson",
                BigDecimal.ZERO, "sb-erfassung");
        bgId = bg.id();
        service.legeSozialFallAn(bgId, new SozialFall(0, bgId, 1, false, false,
                new BigDecimal("300"), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, 1), "sb-erfassung");
    }

    @AfterEach
    void entferneTestfall() throws Exception {
        try (Connection c = eigeneVerbindung()) {
            c.setAutoCommit(true);
            for (String t : List.of("JOURNAL", "ZAHLUNG", "BESCHEID", "SOZIAL_FALL")) {
                try (var p = c.prepareStatement("DELETE FROM " + t + " WHERE BG_ID = ?")) {
                    p.setLong(1, bgId);
                    p.executeUpdate();
                }
            }
            try (var p = c.prepareStatement("DELETE FROM BEDARFSGEMEINSCHAFT WHERE ID = ?")) {
                p.setLong(1, bgId);
                p.executeUpdate();
            }
        }
    }

    @Test
    void commitFehlerDarfErfolgreichenFolgezugriffNichtVerdecken() throws Exception {
        var faulty = new CommitFehlerPool(pool, false);
        var mitFehler = new SozialLeistungsService(new TxManager(faulty));
        assertThrows(TxCommitOutcomeUnknownException.class, () -> mitFehler.ladeDetail(bgId, "sb-rollback"));
        assertEquals(1, faulty.commitVersuche.get(), "Commitfehler darf keinen automatischen Neuversuch auslösen");
        assertEquals(0, einsichten("sb-rollback"));
        assertNotNull(mitFehler.ladeDetail(bgId, "sb-rollback"));
        assertEquals(1, einsichten("sb-rollback"), "Erfolgreicher Zugriff nach echtem Rollback ist protokolliert");
    }

    @Test
    void commitKonfliktBleibtUnbekanntUndStatuspruefungVerhindertDoppelteEinsicht() throws Exception {
        var faulty = new CommitFehlerPool(pool, true);
        var mitFehler = new SozialLeistungsService(new TxManager(faulty, new RetryPolicy(1)));
        var error = assertThrows(TxCommitOutcomeUnknownException.class,
                () -> mitFehler.ladeDetail(bgId, "sb-quittung"));
        assertFalse(error.isRetryable());
        assertEquals(1, faulty.commitVersuche.get(), "auch ORA-08177 im Commit wird nicht automatisch wiederholt");
        // Unabhängige SQL-Nachlese bestätigt, dass die gezielt verlorene Quittung
        // einer wirklich committeten Einsicht folgt. Erst danach erneut lesen.
        assertEquals(1, einsichten("sb-quittung"));
        assertNotNull(mitFehler.ladeDetail(bgId, "sb-quittung"));
        assertEquals(2, faulty.commitVersuche.get());
        assertEquals(1, einsichten("sb-quittung"), "erneutes Lesen nach Statusprüfung erzeugt keinen zweiten Eintrag");
    }

    @Test
    void retryVorCommitRolltEchteErsteEinsichtZurueckUndPersistiertNurDenFolgeversuch() throws Exception {
        var faulty = new JournalKonfliktPool(pool, bgId, "sb-retry-vor-commit");
        var mitRetry = new SozialLeistungsService(new TxManager(faulty, new RetryPolicy(1)));
        assertNotNull(mitRetry.ladeDetail(bgId, "sb-retry-vor-commit"));
        assertEquals(2, faulty.journalInserts.get(), "beide Versuche führen den echten Journal-INSERT aus");
        assertEquals(1, faulty.vorKonfliktSichtbar.get(), "erster INSERT muss vor dem injizierten Konflikt wirklich vorhanden sein");
        assertEquals(1, faulty.rollbacks.get(), "erster Versuch wird vor dem Retry tatsächlich zurückgerollt");
        assertEquals(0, faulty.nachRollbackSichtbar.get(), "unabhängige SQL-Nachlese nach Rollback sieht keinen ersten Eintrag");
        assertEquals(1, faulty.commits.get(), "nur der erfolgreiche Folgeversuch committet");
        assertEquals(1, einsichten("sb-retry-vor-commit"));
    }

    @Test
    void dedupGiltAuchUeberServiceInstanzenUndVerschiebtZeitstempelNicht() throws Exception {
        service.ladeDetail(bgId, "sb-fenster");
        try (Connection c = eigeneVerbindung(); var p = c.prepareStatement(
                "UPDATE JOURNAL SET ERSTELLT_AM = SYSTIMESTAMP - INTERVAL '30' SECOND "
                        + "WHERE BG_ID = ? AND SACHBEARBEITER_ID = 'sb-fenster' AND AKTION_TEXT LIKE 'Einsicht:%'")) {
            c.setAutoCommit(true);
            p.setLong(1, bgId);
            p.executeUpdate();
        }
        Timestamp vorher = letzterZeitpunkt("sb-fenster");
        var zweiterService = new SozialLeistungsService(new TxManager(pool));
        zweiterService.ladeDetail(bgId, "sb-fenster");
        assertEquals(1, einsichten("sb-fenster"));
        assertEquals(vorher, letzterZeitpunkt("sb-fenster"), "Unterdrückter Zugriff verlängert die Frist nicht");
        try (Connection c = eigeneVerbindung(); var p = c.prepareStatement(
                "UPDATE JOURNAL SET ERSTELLT_AM = SYSTIMESTAMP - INTERVAL '61' SECOND "
                        + "WHERE BG_ID = ? AND SACHBEARBEITER_ID = 'sb-fenster' AND AKTION_TEXT LIKE 'Einsicht:%'")) {
            c.setAutoCommit(true);
            p.setLong(1, bgId);
            p.executeUpdate();
        }
        zweiterService.ladeDetail(bgId, "sb-fenster");
        assertEquals(2, einsichten("sb-fenster"), "Neues Fenster beginnt nach dem persistierten Zugriff");
    }

    @Test
    void paralleleEinsichtenZweierInstanzenErzeugenNurEinenEintrag() throws Exception {
        var start = new CountDownLatch(1);
        var andererService = new SozialLeistungsService(new TxManager(pool));
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> { start.await(); return service.ladeDetail(bgId, "sb-parallel"); });
            var b = executor.submit(() -> { start.await(); return andererService.ladeDetail(bgId, "sb-parallel"); });
            start.countDown();
            assertNotNull(a.get(10, TimeUnit.SECONDS));
            assertNotNull(b.get(10, TimeUnit.SECONDS));
        }
        assertEquals(1, einsichten("sb-parallel"));
    }

    @Test
    void replayBindetVorgangsnummerAnBgMonatUndExpliziteBankdaten() throws Exception {
        service.ordneAuszahlungAn(auftrag(bgId, "2026-10", "sb-erfassung"));
        assertThrows(FachlicheException.class,
                () -> service.ordneAuszahlungAn(auftrag(-42, "2026-10", "sb-replay")));
        assertThrows(FachlicheException.class,
                () -> service.ordneAuszahlungAn(auftrag(bgId, "2026-11", "sb-replay")));
        for (Auszahlungsauftrag abweichend : List.of(
                new Auszahlungsauftrag(bgId, vorgangsNr, "2026-10", "sb-replay", false,
                        "DE75512108001245126199", null, null),
                new Auszahlungsauftrag(bgId, vorgangsNr, "2026-10", "sb-replay", false,
                        null, "MARKDEF1100", null),
                new Auszahlungsauftrag(bgId, vorgangsNr, "2026-10", "sb-replay", false,
                        null, null, "Anderer Empfänger"))) {
            assertThrows(FachlicheException.class, () -> service.ordneAuszahlungAn(abweichend));
        }
        assertEquals(0, einsichten("sb-replay"), "Abgewiesene Wiederholungen liefern keine Falldaten");
        var replay = service.ordneAuszahlungAn(new Auszahlungsauftrag(bgId, vorgangsNr, "2026-10",
                "sb-replay", false, "de89 3704 0044 0532 0130 00", "cobadeffxxx", "Fiktive Reviewperson"));
        assertTrue(replay.bereitsGebucht());
        assertEquals(1, einsichten("sb-replay"));
    }

    @Test
    void httpReplayWeistFremdeParameterAbUndProtokolliertErlaubtenAbruf() throws Exception {
        var angelegt = service.ordneAuszahlungAn(auftrag(bgId, "2026-10", "sb-erfassung"));
        try (var tasks = new TaskController(new TxManager(pool), 2, 8);
             var server = new LmgServer(tasks, service, null,
                     new Benutzerverwaltung("sb-http:test:sachbearbeiter"), 0)) {
            server.start();
            var client = HttpClient.newHttpClient();
            String base = "http://127.0.0.1:" + server.adresse().getPort();
            var login = client.send(HttpRequest.newBuilder(URI.create(base + "/api/anmeldung"))
                    .POST(HttpRequest.BodyPublishers.ofString("{\"kennung\":\"sb-http\",\"passwort\":\"test\"}"))
                    .build(), HttpResponse.BodyHandlers.ofString());
            String token = (String) ((Map<?, ?>) Json.parse(login.body())).get("token");
            assertEquals(400, sendeReplay(client, base, token, -42, "2026-10").statusCode());
            assertEquals(400, sendeReplay(client, base, token, bgId, "2026-11").statusCode());
            assertEquals(0, einsichten("sb-http"));
            var erlaubt = sendeReplay(client, base, token, bgId, "2026-10");
            assertEquals(200, erlaubt.statusCode(), erlaubt.body());
            assertTrue(erlaubt.body().contains(IBAN));
            assertTrue(erlaubt.body().contains("\"bereitsGebucht\":true"));
            assertEquals(1, einsichten("sb-http"));
            assertEquals(angelegt.bedarfsgemeinschaft().budgetRest(),
                    service.ladeDetail(bgId).bedarfsgemeinschaft().budgetRest(), "Replay bucht kein weiteres Budget");
        }
    }

    private Auszahlungsauftrag auftrag(long id, String monat, String sb) {
        return new Auszahlungsauftrag(id, vorgangsNr, monat, sb);
    }

    private static Connection eigeneVerbindung() throws SQLException {
        return DriverManager.getConnection(System.getenv("TX_URL"), System.getenv("TX_USER"), System.getenv("TX_PASS"));
    }

    private HttpResponse<String> sendeReplay(HttpClient client, String base, String token,
                                             long id, String monat) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(base + "/api/auszahlung"))
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofString(Json.write(Map.of(
                        "bgId", id, "vorgangsNr", vorgangsNr, "zahlungsmonat", monat))))
                .build(), HttpResponse.BodyHandlers.ofString());
    }

    private int einsichten(String sb) throws Exception {
        Connection c = pool.getConnection();
        try (var p = c.prepareStatement("SELECT COUNT(*) FROM JOURNAL WHERE BG_ID = ? "
                + "AND SACHBEARBEITER_ID = ? AND AKTION_TEXT LIKE 'Einsicht:%'")) {
            p.setLong(1, bgId);
            p.setString(2, sb);
            try (var r = p.executeQuery()) { r.next(); return r.getInt(1); }
        } finally { pool.release(c); }
    }

    private Timestamp letzterZeitpunkt(String sb) throws Exception {
        Connection c = pool.getConnection();
        try (var p = c.prepareStatement("SELECT MAX(ERSTELLT_AM) FROM JOURNAL WHERE BG_ID = ? "
                + "AND SACHBEARBEITER_ID = ? AND AKTION_TEXT LIKE 'Einsicht:%'")) {
            p.setLong(1, bgId);
            p.setString(2, sb);
            try (var r = p.executeQuery()) { r.next(); return r.getTimestamp(1); }
        } finally { pool.release(c); }
    }

    /** Fehler am JDBC-Commit: wahlweise vor oder nach echtem Oracle-Commit, nie automatisch wiederholen. */
    private static final class CommitFehlerPool implements ConnectionPool {
        private final ConnectionPool delegate;
        private final boolean commitVorFehler;
        private final AtomicBoolean ersterCommit = new AtomicBoolean(true);
        private final AtomicInteger commitVersuche = new AtomicInteger();
        private final Map<Connection, Connection> originals = Collections.synchronizedMap(new IdentityHashMap<>());

        CommitFehlerPool(ConnectionPool delegate, boolean commitVorFehler) {
            this.delegate = delegate;
            this.commitVorFehler = commitVorFehler;
        }

        @Override
        public Connection getConnection() throws SQLException {
            Connection real = delegate.getConnection();
            Connection proxy = (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                    new Class<?>[]{Connection.class}, (p, method, args) -> {
                        if (method.getName().equals("commit")) {
                            commitVersuche.incrementAndGet();
                            if (ersterCommit.getAndSet(false)) {
                                if (commitVorFehler) real.commit();
                                throw new SQLException("Injizierter Commitfehler", null, commitVorFehler ? 8177 : 0);
                            }
                        }
                        try { return method.invoke(real, args); }
                        catch (InvocationTargetException e) { throw e.getCause(); }
                    });
            originals.put(proxy, real);
            return proxy;
        }

        @Override
        public void release(Connection connection) {
            delegate.release(originals.remove(connection));
        }

        @Override
        public void close() { }
    }

    /** Konflikt erst NACH echtem Journal-INSERT, aber VOR JDBC-Commit; Oracle-Rollback wird separat nachgelesen. */
    private static final class JournalKonfliktPool implements ConnectionPool {
        private final ConnectionPool delegate;
        private final long ownBgId;
        private final String ownBearbeiter;
        private final AtomicBoolean firstInsert = new AtomicBoolean(true);
        private final AtomicInteger journalInserts = new AtomicInteger(), rollbacks = new AtomicInteger(), commits = new AtomicInteger();
        private final AtomicInteger vorKonfliktSichtbar = new AtomicInteger(-1), nachRollbackSichtbar = new AtomicInteger(-1);
        private final Map<Connection, Connection> originals = Collections.synchronizedMap(new IdentityHashMap<>());

        JournalKonfliktPool(ConnectionPool delegate, long ownBgId, String ownBearbeiter) {
            this.delegate = delegate; this.ownBgId = ownBgId; this.ownBearbeiter = ownBearbeiter;
        }

        @Override
        public Connection getConnection() throws SQLException {
            Connection real = delegate.getConnection();
            Connection proxy = (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
                    (p, method, args) -> {
                        try {
                            Object result = method.invoke(real, args);
                            if (method.getName().equals("commit")) commits.incrementAndGet();
                            if (method.getName().equals("rollback")) {
                                rollbacks.incrementAndGet();
                                try (var independent = eigeneVerbindung()) { nachRollbackSichtbar.set(count(independent)); }
                            }
                            if (method.getName().equals("prepareStatement") && args[0] instanceof String sql
                                    && sql.startsWith("INSERT INTO JOURNAL ")) {
                                PreparedStatement statement = (PreparedStatement) result;
                                return Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(), new Class<?>[]{PreparedStatement.class},
                                        (sp, operation, values) -> {
                                            try {
                                                Object changed = operation.invoke(statement, values);
                                                if (operation.getName().equals("executeUpdate")) {
                                                    assertEquals(1, changed, "echter Oracle-INSERT muss erfolgreich sein");
                                                    journalInserts.incrementAndGet();
                                                    if (firstInsert.compareAndSet(true, false)) {
                                                        vorKonfliktSichtbar.set(count(real));
                                                        throw new SQLException("Injizierter Konflikt nach Journal-INSERT vor Commit", "61000", 60);
                                                    }
                                                }
                                                return changed;
                                            } catch (InvocationTargetException e) { throw e.getCause(); }
                                        });
                            }
                            return result;
                        } catch (InvocationTargetException e) { throw e.getCause(); }
                    });
            originals.put(proxy, real); return proxy;
        }

        private int count(Connection c) throws SQLException {
            try (var st = c.prepareStatement("SELECT COUNT(*) FROM JOURNAL WHERE BG_ID = ? AND SACHBEARBEITER_ID = ? AND AKTION_TEXT LIKE 'Einsicht:%'")) {
                st.setLong(1, ownBgId); st.setString(2, ownBearbeiter);
                try (var rows = st.executeQuery()) { assertTrue(rows.next()); return rows.getInt(1); }
            }
        }
        @Override public void release(Connection connection) { delegate.release(originals.remove(connection)); }
        @Override public void close() { }
    }
}
