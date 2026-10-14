package de.allgemein.tx;

import oracle.ucp.jdbc.ValidConnection;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UcpConnectionPoolTest {

    @Test
    void rollbackfehlerInvalidiertProxyOhneImplizitenCommit() throws Exception {
        List<String> calls = new ArrayList<>();
        Connection connection = proxy(calls, false);
        try (var pool = new UcpConnectionPool("jdbc:oracle:thin:@//localhost:1521/dummy", "u", "p", 1)) {
            pool.release(connection);
        }
        assertEquals(List.of("rollback", "setInvalid", "close"), calls);
    }

    @Test
    void runtimeFehlerBeimStatuslesenSchliesstProxyTrotzdem() throws Exception {
        List<String> calls = new ArrayList<>();
        Connection connection = proxy(calls, true);
        try (var pool = new UcpConnectionPool("jdbc:oracle:thin:@//localhost:1521/dummy", "u", "p", 1)) {
            pool.release(connection);
        }
        assertEquals(List.of("setInvalid", "close"), calls);
    }

    private static Connection proxy(List<String> calls, boolean brokenStatus) {
        return (Connection) Proxy.newProxyInstance(UcpConnectionPoolTest.class.getClassLoader(),
                new Class<?>[]{Connection.class, ValidConnection.class}, (p, method, args) -> {
                    return switch (method.getName()) {
                        case "isClosed" -> {
                            if (brokenStatus) throw new IllegalStateException("Treiberstatus defekt");
                            yield false;
                        }
                        case "getAutoCommit" -> false;
                        case "rollback" -> {
                            calls.add("rollback");
                            throw new SQLException("Rollback fehlgeschlagen");
                        }
                        case "setInvalid", "close", "setAutoCommit" -> {
                            calls.add(method.getName());
                            yield null;
                        }
                        default -> null;
                    };
                });
    }
}
