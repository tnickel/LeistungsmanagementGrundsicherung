package de.allgemein.lmg.web;

import de.allgemein.lmg.model.SozialFall;
import de.allgemein.lmg.service.*;
import de.allgemein.tx.*;
import de.allgemein.tx.saga.SagaEngine;
import de.allgemein.tx.saga.SagaJournal;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.*;
import java.sql.*;
import java.time.Duration;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** Produkt-HTTP, echter Service und unabhängiger Oracle-Zustandsvergleich; nur eigene UUID-Daten. */
@EnabledIfEnvironmentVariable(named = "TX_URL", matches = ".+")
class ProduktGeldEingabeHttpDbTest {
    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private static final List<String> MONEY_FIELDS = List.of("kduMiete", "kduBetriebskosten", "kduHeizung",
            "einkommenRente", "einkommenErwerb", "abzSteuern", "abzSozialversicherung",
            "abzVersicherungen", "abzErwerbsaufwendungen");
    private SimpleConnectionPool pool;
    private SozialLeistungsService service;
    private TaskController tasks;
    private LmgServer server;
    private HttpClient client;
    private String base, token;
    private final String marker = "HTTP-" + UUID.randomUUID();
    private long bgId;
    private SozialFall person;

    @BeforeEach void create() throws Exception {
        pool = new SimpleConnectionPool(System.getenv("TX_URL"), System.getenv("TX_USER"), System.getenv("TX_PASS"), 6);
        var tx = new TxManager(pool);
        service = new SozialLeistungsService(tx);
        bgId = service.legeBedarfsgemeinschaftAn(marker, "Synthetischer Eingabetest", new BigDecimal("10000.00"),
                null, "DE89370400440532013000", "COBADEFFXXX", "Testkonto", new BigDecimal("12.34"), "sb-input").id();
        person = service.legeSozialFallAn(bgId, new SozialFall(0, bgId, 1, false, false, 0, 0, 0, false,
                new BigDecimal("300.00"), new BigDecimal("25.00"), new BigDecimal("70.00"),
                new BigDecimal("100.00"), ZERO, ZERO, ZERO, ZERO, ZERO, null, 1), "sb-input");
        tasks = new TaskController(tx, 2, 10);
        var users = new Benutzerverwaltung("sb-input:pass:sachbearbeiter");
        server = new LmgServer(tasks, service, null, new SagaEngine(tx, new SagaJournal(pool)), users, 0);
        server.start();
        base = "http://127.0.0.1:" + server.adresse().getPort();
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        var login = send("POST", "/api/anmeldung", Map.of("kennung", "sb-input", "passwort", "pass"));
        assertEquals(200, login.statusCode(), login.body());
        token = String.valueOf(((Map<?,?>)Json.parse(login.body())).get("token"));
    }

    @AfterEach void clean() throws Exception {
        if (server != null) server.close();
        if (tasks != null) tasks.close();
        try {
            if (bgId != 0) try (var c = connect()) {
                c.setAutoCommit(false);
                for (String table : List.of("JOURNAL", "BESCHEID", "ZAHLUNG", "SOZIAL_FALL")) {
                    try (var s = c.prepareStatement("DELETE FROM " + table + " WHERE BG_ID=?")) {
                        s.setLong(1, bgId); s.executeUpdate();
                    }
                }
                try (var s = c.prepareStatement("DELETE FROM BEDARFSGEMEINSCHAFT WHERE ID=?")) {
                    s.setLong(1, bgId); s.executeUpdate();
                }
                c.commit();
            }
        } finally { if (pool != null) pool.close(); }
    }

    @Test void fallTyposSubcentwerteUndFehlendePflichtangabenAendernWederFallNochJournal() throws Exception {
        var before = databaseState();
        for (String method : List.of("POST", "PUT")) {
            String path = method.equals("POST") ? "/api/bedarfsgemeinschaften/" + bgId + "/sozialfaelle"
                    : "/api/sozialfaelle/" + person.id();
            var typo = fallInput(); typo.put("kdumiete", 400);
            rejected(method, path, typo, before);
            for (String field : MONEY_FIELDS) {
                var body = fallInput(); body.put(field, new BigDecimal("12.345"));
                rejected(method, path, body, before);
            }
            var wrongType = fallInput(); wrongType.put("kduMiete", "12.34");
            rejected(method, path, wrongType, before);
            var missingRequired = fallInput(); missingRequired.remove("regelbedarfsstufe");
            rejected(method, path, missingRequired, before);
        }
        var missingVersion = fallInput(); missingVersion.remove("version");
        rejected("PUT", "/api/sozialfaelle/" + person.id(), missingVersion, before);
    }

    @Test void kopfUndSagaWeisenFehlerVorJederPersistenzAb() throws Exception {
        var before = databaseState();
        for (String badField : List.of("vermoegen", "vermoegenn")) {
            var body = new LinkedHashMap<String,Object>(Map.of("statusAktiv", true, "version", 1));
            body.put(badField, new BigDecimal("12.345"));
            rejected("PUT", "/api/bedarfsgemeinschaften/" + bgId, body, before);
        }
        for (String badField : List.of("startBudget", "vermoegen", "startBuget")) {
            var body = new LinkedHashMap<String,Object>(Map.of("aktenzeichen", marker + "-N", "hauptpersonName", "Test"));
            body.put(badField, new BigDecimal("12.345"));
            rejected("POST", "/api/bedarfsgemeinschaften", body, before);
        }
        for (String badField : List.of("startBudget", "vermoegen", "kduMiete", "kduBetriebskosten",
                "kduHeizung", "einkommenRente", "einkommenErwerb", "kdumiete")) {
            var body = new LinkedHashMap<String,Object>(Map.of("aktenzeichen", marker + "-N", "hauptpersonName", "Test",
                    "regelbedarfsstufe", 1, "zahlungsmonat", "2026-10", "startBudget", 10000));
            body.put(badField, new BigDecimal("12.345"));
            rejected("POST", "/api/saga/antrag", body, before);
        }
        try (var c = connect(); var s = c.prepareStatement("SELECT COUNT(*) FROM BEDARFSGEMEINSCHAFT WHERE AKTENZEICHEN=?")) {
            s.setString(1, marker + "-N"); try (var rows = s.executeQuery()) { assertTrue(rows.next()); assertEquals(0, rows.getInt(1)); }
        }
        try (var c = connect(); var s = c.prepareStatement("SELECT COUNT(*) FROM TX_SAGA WHERE PAYLOAD LIKE ?")) {
            s.setString(1, "%" + marker + "-N%"); try (var rows = s.executeQuery()) { assertTrue(rows.next()); assertEquals(0, rows.getInt(1)); }
        }
    }

    @Test void optionaleNullwerteBleibenErlaubtUndCentwerteWerdenVerlustfreiGespeichert() throws Exception {
        var input = fallInput(); input.put("kduMiete", new BigDecimal("12.3400")); input.put("einkommenRente", null);
        var response = send("PUT", "/api/sozialfaelle/" + person.id(), input);
        assertEquals(200, response.statusCode(), response.body());
        try (var c = connect(); var s = c.prepareStatement("SELECT KDU_MIETE,KDU_HEIZUNG,EINKOMMEN_RENTE,VERSION FROM SOZIAL_FALL WHERE ID=?")) {
            s.setLong(1, person.id()); try (var rows = s.executeQuery()) {
                assertTrue(rows.next()); assertEquals(0, new BigDecimal("12.34").compareTo(rows.getBigDecimal(1)));
                assertEquals(0, rows.getBigDecimal(2).signum()); assertEquals(0, rows.getBigDecimal(3).signum());
                assertEquals(person.version() + 1, rows.getLong(4));
            }
        }
        var before = databaseState();
        var invalid = new SozialFall(person.id(), bgId, 1, false, false, 0, 0, 0, false,
                new BigDecimal("12.345"), ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, null, person.version() + 1);
        var error = assertThrows(FachlicheException.class,
                () -> service.aktualisiereSozialFall(invalid, invalid.version(), "sb-input"));
        assertEquals(FachlicheException.VALIDATION, error.code()); assertEquals(before, databaseState());
    }

    private Map<String,Object> fallInput() {
        return new LinkedHashMap<>(Map.of("regelbedarfsstufe", 1, "version", person.version()));
    }
    private void rejected(String method, String path, Map<String,Object> input, List<String> before) throws Exception {
        var response = send(method, path, input);
        assertEquals(400, response.statusCode(), method + " " + path + " " + response.body());
        assertEquals("VALIDATION", ((Map<?,?>)Json.parse(response.body())).get("code"));
        assertEquals(before, databaseState(), "Abweisung darf Fachbestand, Versionen und Journal nicht verändern");
    }
    private HttpResponse<String> send(String method, String path, Map<String,?> input) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(Json.write(input)));
        if (token != null) request.header("Authorization", "Bearer " + token);
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
    private List<String> databaseState() throws SQLException {
        var result = new ArrayList<String>();
        try (var c = connect()) {
            for (String sql : List.of("SELECT VERSION,BUDGET_REST,VERMOEGEN,STATUS_AKTIV FROM BEDARFSGEMEINSCHAFT WHERE ID=?",
                    "SELECT ID,VERSION,REGELBEDARFSSTUFE,KDU_MIETE,KDU_BETRIEBSKOSTEN,KDU_HEIZUNG,EINKOMMEN_RENTE,EINKOMMEN_ERWERB,ABZ_STEUERN,ABZ_SV,ABZ_VERSICHERUNGEN,ABZ_ERWERBSAUSGABEN FROM SOZIAL_FALL WHERE BG_ID=? ORDER BY ID",
                    "SELECT COUNT(*) FROM JOURNAL WHERE BG_ID=?", "SELECT COUNT(*) FROM BESCHEID WHERE BG_ID=?", "SELECT COUNT(*) FROM ZAHLUNG WHERE BG_ID=?")) {
                result.add(sql);
                try (var s = c.prepareStatement(sql)) {
                    s.setLong(1, bgId); try (var rows = s.executeQuery()) {
                        while (rows.next()) for (int i = 1; i <= rows.getMetaData().getColumnCount(); i++) result.add(rows.getString(i));
                    }
                }
            }
        }
        return result;
    }
    private Connection connect() throws SQLException {
        var p = new Properties(); p.setProperty("user", System.getenv("TX_USER")); p.setProperty("password", System.getenv("TX_PASS"));
        p.setProperty("oracle.net.CONNECT_TIMEOUT", "5000"); p.setProperty("oracle.jdbc.ReadTimeout", "10000");
        return DriverManager.getConnection(System.getenv("TX_URL"), p);
    }
}
