package de.allgemein.tx;

/** Arbeitseinheit, die innerhalb einer Transaktion ausgeführt wird. */
@FunctionalInterface
public interface TxWork<T> {
    T run(TxContext ctx) throws Exception;
}
