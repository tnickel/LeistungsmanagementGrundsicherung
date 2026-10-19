package de.allgemein.testsweet;

import de.allgemein.lmg.model.SozialFall;
import de.allgemein.lmg.service.*;
import de.allgemein.lmg.web.*;
import de.allgemein.tx.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.lang.reflect.*;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.*;
import java.sql.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/** Produkt-JAR, echter Service, echtes Oracle und Produkt-HTTP; nur die Pause nach Commit wird injiziert. */
@EnabledIfEnvironmentVariable(named = "TX_URL", matches = ".+")
class ProduktTaskAusgangDbTest {
    @Test
    void echterServiceCommitBleibtNachWachhundMitHttp409NachlesbarUndMonatEindeutig() throws Exception {
        try (Fixture own = new Fixture();
             TaskController apiTasks = new TaskController(own.tx, 2, 10);
             TaskController boundedTasks = new TaskController(own.tx, 1, 2)) {
            AtomicBoolean first = new AtomicBoolean(true);
            CountDownLatch committed = new CountDownLatch(1), release = new CountDownLatch(1);
            // Der HTTP-Task behält sein Produktbudget. Der Dekorator verkürzt nur
            // das Budget des ebenfalls echten inneren TaskController, damit die
            // Regression nicht auf den 30-Sekunden-Default warten muss.
            LeistungsService delayed = (LeistungsService) Proxy.newProxyInstance(
                    LeistungsService.class.getClassLoader(), new Class<?>[]{LeistungsService.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("ordneAuszahlungAn") && first.compareAndSet(true, false)) {
                            Auszahlungsauftrag order = (Auszahlungsauftrag) args[0];
                            return boundedTasks.submit("nach-echtem-servicecommit",
                                    TxOptions.DEFAULT.withTimeout(Duration.ofSeconds(3)), ctx -> {
                                        var result = own.service.ordneAuszahlungAn(order);
                                        committed.countDown();
                                        boolean interrupted = false;
                                        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                                        while (release.getCount() != 0 && System.nanoTime() < until) {
                                            try { release.await(100, TimeUnit.MILLISECONDS); }
                                            catch (InterruptedException e) { interrupted = true; }
                                        }
                                        if (interrupted) Thread.currentThread().interrupt();
                                        return result;
                                    }).result();
                        }
                        try { return method.invoke(own.service, args); }
                        catch (InvocationTargetException e) { throw e.getCause(); }
                    });
            var users = new Benutzerverwaltung("sb-task:testpass:sachbearbeiter");
            try (var server = new LmgServer(apiTasks, delayed, null, users, 0)) {
                server.start();
                String base = "http://127.0.0.1:" + server.adresse().getPort();
                var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
                var login = send(client, base, "/api/anmeldung", Map.of("kennung", "sb-task", "passwort", "testpass"), null);
                assertEquals(200, login.statusCode(), login.body());
                String token = String.valueOf(((Map<?, ?>) Json.parse(login.body())).get("token"));
                String number = UUID.randomUUID().toString(), different = UUID.randomUUID().toString();
                Map<String,Object> order = Map.of("bgId", own.bgId, "vorgangsNr", number, "zahlungsmonat", "2026-10");
                try {
                    var unknown = send(client, base, "/api/auszahlung", order, token);
                    assertEquals(0, committed.getCount(), "echter Fachcommit muss vor dem Abbruch bestätigt sein");
                    assertEquals(409, unknown.statusCode(), unknown.body());
                    assertTrue(unknown.body().contains("AUSGANG_UNBEKANNT"), unknown.body());
                    assertTrue(unknown.body().contains("Status neu laden"), unknown.body());
                    assertFalse(unknown.body().contains("bitte erneut versuchen"), unknown.body());
                    assertEquals(1, boundedTasks.stats().timedOut(), "echter Wachhund, keine direkt geworfene Ausnahme");
                    assertEquals(1, release.getCount(), "Worker bleibt für den SQL-Nachweis nach der Antwort blockiert");
                    assertPayment(own.bgId, number, 1, new BigDecimal("9437.00"));

                    var replay = send(client, base, "/api/auszahlung", order, token);
                    assertEquals(200, replay.statusCode(), replay.body());
                    assertEquals(Boolean.TRUE, ((Map<?,?>) Json.parse(replay.body())).get("bereitsGebucht"));
                    var second = send(client, base, "/api/auszahlung", Map.of("bgId", own.bgId,
                            "vorgangsNr", different, "zahlungsmonat", "2026-10"), token);
                    assertEquals(400, second.statusCode(), second.body());
                    assertTrue(second.body().contains("bereits eine nicht stornierte Zahlung"), second.body());
                    assertPayment(own.bgId, number, 1, new BigDecimal("9437.00"));
                    try (var c = connect(); var st = c.prepareStatement("SELECT COUNT(*) FROM ZAHLUNG WHERE VORGANGS_NR = ?")) {
                        st.setString(1,different); try (var rs=st.executeQuery()) { assertTrue(rs.next()); assertEquals(0,rs.getInt(1)); }
                    }
                } finally { release.countDown(); }
            }
        }
    }

    @Test
    void interruptNachEchtemOracleUpdateVerhindertExecuteCommitUndRolltZurueck() throws Exception {
        try (Fixture own = new Fixture()) {
            try {
                assertThrows(TxTimeoutException.class, () -> own.tx.execute(TxOptions.DEFAULT, ctx -> {
                    reduceBudget(ctx.connection(), own.bgId);
                    Thread.currentThread().interrupt();
                    return null;
                }));
                assertTrue(Thread.currentThread().isInterrupted(), "Guard darf das Abbruchsignal nicht konsumieren");
            } finally { Thread.interrupted(); }
            assertPayment(own.bgId, null, 0, new BigDecimal("10000.00"));
        }
    }

    @Test
    void abgelaufeneDeadlineNachEchtemOracleUpdateVerhindertCommitOhneInterrupt() throws Exception {
        try (Fixture own = new Fixture()) {
            AtomicBoolean updated = new AtomicBoolean();
            assertThrows(TxTimeoutException.class, () -> own.tx.execute(
                    TxOptions.DEFAULT.withTimeout(Duration.ofMillis(200)), ctx -> {
                        reduceBudget(ctx.connection(), own.bgId);
                        updated.set(true);
                        Thread.sleep(250);
                        return null;
                    }));
            assertTrue(updated.get(), "Abbruch muss nach dem echten SQL-Update liegen");
            assertFalse(Thread.currentThread().isInterrupted(), "Deadline wird ohne Wachhund/Interrupt geprüft");
            assertPayment(own.bgId, null, 0, new BigDecimal("10000.00"));
        }
    }

    @Test
    void interruptNachEchtemOracleUpdateVerhindertAuchManuellenCommit() throws Exception {
        try (Fixture own = new Fixture()) {
            TxContext ctx = own.tx.begin(TxOptions.DEFAULT);
            try {
                reduceBudget(ctx.connection(), own.bgId);
                Thread.currentThread().interrupt();
                assertThrows(TxTimeoutException.class, () -> own.tx.commit(ctx));
                assertEquals(TxStatus.ACTIVE, ctx.status(), "noch kein bestätigter Commit");
            } finally {
                Thread.interrupted();
                own.tx.finish(ctx);
            }
            assertEquals(TxStatus.ABORTED, ctx.status());
            assertPayment(own.bgId, null, 0, new BigDecimal("10000.00"));
        }
    }

    private static void reduceBudget(Connection c, long id) throws SQLException {
        try (var st=c.prepareStatement("UPDATE BEDARFSGEMEINSCHAFT SET BUDGET_REST = BUDGET_REST - 1 WHERE ID = ?")) {
            st.setLong(1,id); assertEquals(1,st.executeUpdate());
        }
    }
    private static HttpResponse<String> send(HttpClient client, String base, String path, Map<String,?> body, String token)
            throws Exception {
        var request=HttpRequest.newBuilder(URI.create(base+path)).timeout(Duration.ofSeconds(15))
                .header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(Json.write(body)));
        if (token!=null) request.header("Authorization","Bearer "+token);
        return client.send(request.build(),HttpResponse.BodyHandlers.ofString());
    }
    private static void assertPayment(long id, String number, int count, BigDecimal budget) throws SQLException {
        try (var c=connect(); var st=c.prepareStatement("SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?")) {
            st.setLong(1,id); try (var rs=st.executeQuery()) { assertTrue(rs.next()); assertEquals(0,budget.compareTo(rs.getBigDecimal(1))); }
            try (var p=c.prepareStatement("SELECT VORGANGS_NR, AUSZAHLUNGSBETRAG, ZAHLUNGSSTATUS FROM ZAHLUNG WHERE BG_ID = ?")) {
                p.setLong(1,id); try (var rs=p.executeQuery()) {
                    int found=0;
                    while(rs.next()) {
                        found++; assertEquals(number,rs.getString(1));
                        assertEquals(0,new BigDecimal("563.00").compareTo(rs.getBigDecimal(2)));
                        assertEquals("ANGELEGT",rs.getString(3));
                    }
                    assertEquals(count,found);
                }
            }
        }
    }
    private static Connection connect() throws SQLException {
        return DriverManager.getConnection(System.getenv("TX_URL"),System.getenv("TX_USER"),System.getenv("TX_PASS"));
    }
    private static final class Fixture implements AutoCloseable {
        final SimpleConnectionPool pool;
        final TxManager tx;
        final SozialLeistungsService service;
        final long bgId;
        Fixture() throws Exception {
            pool=new SimpleConnectionPool(System.getenv("TX_URL"),System.getenv("TX_USER"),System.getenv("TX_PASS"),
                    5,Duration.ofSeconds(5),5000,10000);
            // Verbindungsaufbau gehört nicht zum gezielt geprüften Nach-Commit-Fenster.
            List<Connection> warm=new ArrayList<>();
            try { for(int i=0;i<5;i++) warm.add(pool.getConnection()); }
            finally { warm.forEach(pool::release); }
            tx=new TxManager(pool); service=new SozialLeistungsService(tx);
            bgId=service.legeBedarfsgemeinschaftAn("SWEET-TASK-"+UUID.randomUUID(),"Synthetische Taskprüfung",
                    new BigDecimal("10000.00"),null,"DE89370400440532013000","COBADEFFXXX","Synthetischer Empfänger","sb-task").id();
            var zero=BigDecimal.ZERO;
            service.legeSozialFallAn(bgId,new SozialFall(0,bgId,1,false,false,zero,zero,zero,zero,zero,1),"sb-task");
        }
        public void close() throws Exception {
            pool.close();
            try (var c=connect()) {
                c.setAutoCommit(false);
                for(String table:List.of("JOURNAL","BESCHEID","ZAHLUNG","SOZIAL_FALL","BEDARFSGEMEINSCHAFT")) {
                    try(var st=c.prepareStatement("DELETE FROM "+table+" WHERE "+(table.equals("BEDARFSGEMEINSCHAFT")?"ID":"BG_ID")+" = ?")) {
                        st.setLong(1,bgId); st.executeUpdate();
                    }
                }
                c.commit();
            }
        }
    }
}
