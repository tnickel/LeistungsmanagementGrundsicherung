package de.allgemein.lmg.dao;

import de.allgemein.tx.*;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.*;
import java.util.stream.LongStream;
import static org.junit.jupiter.api.Assertions.*;

/** Simuliert gezielt die Oracle-19c-Parsergrenze, keine echte Oracle-Ausführung. */
class KassenBlockTest {
    @Test
    void zweitausendeinsIdsBleibenVollstaendigInDerselbenTransaktion() {
        var pool = new InLimitPool(false);
        List<Long> ids = LongStream.rangeClosed(1, 2001).boxed().toList();
        int count = new TxManager(pool).execute(TxOptions.DEFAULT,
                ctx -> new ZahlungDao().uebergebeGesperrte(ctx, ids));
        assertEquals(2001, count);
        assertEquals(List.of(1000, 1000, 1), pool.blocks.stream().map(List::size).toList());
        assertEquals(ids, pool.blocks.stream().flatMap(List::stream).toList());
        assertEquals(1, pool.commits);
        assertEquals(0, pool.rollbacks);
        assertEquals(1, pool.borrows);
    }

    @Test
    void fehlendeZeileImZweitenBlockRolltAuchDenErstenBlockZurueck() {
        var pool = new InLimitPool(true);
        assertThrows(TxException.class, () -> new TxManager(pool).execute(TxOptions.DEFAULT,
                ctx -> new ZahlungDao().uebergebeGesperrte(ctx,
                        LongStream.rangeClosed(1, 1001).boxed().toList())));
        assertEquals(0, pool.commits);
        assertEquals(1, pool.rollbacks);
        assertEquals(List.of(1000, 1), pool.blocks.stream().map(List::size).toList());
    }

    private static final class InLimitPool implements ConnectionPool {
        final List<List<Long>> blocks = new ArrayList<>();
        final boolean missLast;
        int commits, rollbacks, borrows;
        InLimitPool(boolean missLast) { this.missLast = missLast; }
        public Connection getConnection() {
            borrows++;
            return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Connection.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "setAutoCommit", "setTransactionIsolation", "close" -> null;
                        case "commit" -> { commits++; yield null; }
                        case "rollback" -> { rollbacks++; yield null; }
                        case "prepareStatement" -> statement((String) args[0]);
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
        }
        private PreparedStatement statement(String sql) throws SQLException {
            int count = (int) sql.chars().filter(c -> c == '?').count();
            if (count > 1000) throw new SQLException("ORA-01795", "42000", 1795);
            List<Long> values = new ArrayList<>(Collections.nCopies(count, null));
            return (PreparedStatement) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[]{PreparedStatement.class}, (proxy, method, args) -> switch (method.getName()) {
                        case "setLong" -> { values.set((Integer) args[0] - 1, (Long) args[1]); yield null; }
                        case "executeUpdate" -> {
                            blocks.add(List.copyOf(values));
                            yield missLast && blocks.size() == 2 ? values.size() - 1 : values.size();
                        }
                        case "close" -> null;
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
        }
        public void release(Connection connection) { }
        public void close() { }
    }
}
