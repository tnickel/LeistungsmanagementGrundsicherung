package de.allgemein.tx;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Das ist der einfache Verbindungspool für Demo und Übergang.
 * Er hält eine kleine Menge fertiger Datenbankverbindungen und verleiht sie.
 * Nach der Arbeit kommt die Verbindung zurück in den Pool.
 * Für den echten Betrieb ist der Oracle-Pool besser geeignet.
 *
 * Minimaler Verbindungspool für Demo und Übergangsphase. Für die Produktion besser
 * HikariCP oder Oracle UCP einsetzen — die Steuerung hängt nur an dieser schmalen
 * Schnittstelle (getConnection/release) und kann den Pool daher leicht tauschen.
 *
 * <p>Sicherheitsregeln dieses Pools:</p>
 * <ul>
 *   <li>{@code release()} committet <em>niemals</em> implizit: Vor dem Rückstellen in
 *       den Autocommit-Modus wird eine offene Transaktion zurückgerollt (JDBC sieht
 *       vor, dass {@code setAutoCommit(true)} eine laufende Transaktion committet —
 *       genau das darf beim Zurückgeben, z. B. nach Durchreichen eines
 *       {@code Error}s durch den TxManager, nicht passieren).</li>
 *   <li>{@code release()} ist idempotent: Ein Doppel-Release derselben Verbindung
 *       wird protokolliert und ignoriert, statt das Permit doppelt freizugeben und
 *       dadurch {@code maxSize} auszuhebeln.</li>
 *   <li>Beim Entleihen wird jede gepoolte Verbindung validiert ({@code isClosed()} /
 *       {@code isValid(1)}); nach einem Datenbank-Neustart werden tote Verbindungen
 *       still verworfen und übersprungen.</li>
 *   <li>Das Warten auf eine freie Verbindung ist auf {@code borrowTimeout} begrenzt
 *       (Standard 30 s); danach {@code SQLException} statt endlosem Blockieren.</li>
 * </ul>
 */
public final class SimpleConnectionPool implements ConnectionPool {

    private static final Logger LOG = Logger.getLogger(SimpleConnectionPool.class.getName());

    /** Standardzeit, die ein Aufrufer höchstens auf eine freie Verbindung wartet. */
    private static final Duration DEFAULT_BORROW_TIMEOUT = Duration.ofSeconds(30);

    /** Timeout in Sekunden für die Gültigkeitsprüfung beim Entleihen (isValid). */
    private static final int VALIDIERUNGS_TIMEOUT_SEKUNDEN = 1;

    private final String url;
    private final String user;
    private final String password;
    private final Duration borrowTimeout;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;
    private final BlockingQueue<Connection> idle;
    private final Semaphore permits;
    /** Momentan ausgeliehene Verbindungen — Grundlage für das idempotente release(). */
    private final Set<Connection> borrowed = ConcurrentHashMap.newKeySet();

    /** Rate-Begrenzung der Erschöpfungs-Warnung (CR-046): höchstens eine pro 10 s. */
    private final java.util.concurrent.atomic.AtomicLong letzteWarnungNano =
            new java.util.concurrent.atomic.AtomicLong();

    /**
     * Wie bisher: Pool mit Standard-Borrow-Timeout von 30 Sekunden
     * (bestehende Aufrufer bleiben unverändert lauffähig).
     */
    public SimpleConnectionPool(String url, String user, String password, int maxSize) {
        this(url, user, password, maxSize, DEFAULT_BORROW_TIMEOUT);
    }

    /**
     * Pool mit konfigurierbarem Borrow-Timeout: {@code getConnection()} wartet
     * höchstens {@code borrowTimeout} auf eine freie Verbindung und wirft danach
     * eine {@code SQLException} („Pool erschöpft“), statt endlos zu blockieren.
     *
     * @param borrowTimeout maximale Wartezeit auf eine freie Verbindung, muss positiv sein
     */
    public SimpleConnectionPool(String url, String user, String password, int maxSize, Duration borrowTimeout) {
        this(url, user, password, maxSize, borrowTimeout, 15_000, 120_000);
    }

    /**
     * @param connectTimeoutMs TCP-Aufbau-Timeout für neue Verbindungen
     *                         (oracle.net.CONNECT_TIMEOUT) — begrenzt hängende Verbindungsaufbauten
     * @param readTimeoutMs    Lese-Timeout für JDBC-Aufrufe (oracle.jdbc.ReadTimeout),
     *                         wirksam auch ohne TaskController-Wachhund
     */
    public SimpleConnectionPool(String url, String user, String password, int maxSize, Duration borrowTimeout,
            int connectTimeoutMs, int readTimeoutMs) {
        if (maxSize < 1) {
            throw new IllegalArgumentException("maxSize muss >= 1 sein");
        }
        Objects.requireNonNull(borrowTimeout, "borrowTimeout");
        if (borrowTimeout.isZero() || borrowTimeout.isNegative()) {
            throw new IllegalArgumentException("borrowTimeout muss positiv sein");
        }
        this.url = Objects.requireNonNull(url, "url");
        this.user = user;
        this.password = password;
        this.borrowTimeout = borrowTimeout;
        this.connectTimeoutMs = Math.max(100, connectTimeoutMs);
        this.readTimeoutMs = Math.max(100, readTimeoutMs);
        this.idle = new ArrayBlockingQueue<>(maxSize);
        this.permits = new Semaphore(maxSize);
    }

    @Override
    public Connection getConnection() throws SQLException {
        try {
            if (!permits.tryAcquire(borrowTimeout.toMillis(), TimeUnit.MILLISECONDS)) {
                warneErschoepfung();
                throw new SQLException("Pool erschöpft: innerhalb von " + borrowTimeout.toMillis()
                        + " ms keine freie Verbindung bekommen (maxSize erreicht)");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SQLException("Warten auf freie Verbindung unterbrochen", e);
        }
        boolean success = false;
        try {
            // Gepoolte Verbindungen validieren: Nach einem DB-Neustart können tote
            // Verbindungen im Pool liegen — sie werden still geschlossen und
            // übersprungen (die bereits aus der Queue genommene Referenz geht nie
            // verloren, das Permit bleibt bis zum Ende dieser Methode gehalten).
            Connection candidate;
            while ((candidate = idle.poll()) != null) {
                if (istGueltig(candidate)) {
                    borrowed.add(candidate);
                    success = true;
                    return candidate;
                }
                closeQuietly(candidate);
            }
            // Keine brauchbare gepoolte Verbindung: neue physische Verbindung öffnen
            // (mit Connect-/Read-Timeout gegen hängende Aufrufe, CR-020).
            java.util.Properties props = new java.util.Properties();
            if (user != null) {
                props.setProperty("user", user);
                if (password != null) {
                    props.setProperty("password", password);
                }
            }
            props.setProperty("oracle.net.CONNECT_TIMEOUT", String.valueOf(connectTimeoutMs));
            props.setProperty("oracle.jdbc.ReadTimeout", String.valueOf(readTimeoutMs));
            Connection frisch = DriverManager.getConnection(url, props);
            if (!istGueltig(frisch)) {
                closeQuietly(frisch);
                throw new SQLException("Neu aufgebaute Verbindung besteht die Gültigkeitsprüfung nicht: " + url);
            }
            borrowed.add(frisch);
            success = true;
            return frisch;
        } finally {
            if (!success) {
                permits.release();
            }
        }
    }

    /** Operative Warnung bei Erschöpfung, rate-begrenzt auf eine Meldung pro 10 Sekunden. */
    private void warneErschoepfung() {
        long jetzt = System.nanoTime();
        long letzte = letzteWarnungNano.get();
        if (jetzt - letzte > 10_000_000_000L && letzteWarnungNano.compareAndSet(letzte, jetzt)) {
            LOG.warning("Verbindungspool erschöpft — Aufrufer warten bis zu " + borrowTimeout.toMillis()
                    + " ms auf eine freie Verbindung (mögliche Ursache: vergessene release()/finish()-Aufrufe)");
        }
    }

    @Override
    public void release(Connection connection) {
        if (connection == null) {
            // Nichts ausgeliehen => es kann auch kein Permit verloren gehen.
            return;
        }
        if (!borrowed.remove(connection)) {
            // Doppel-Release oder fremde Verbindung: ignoriert BEWUSST ohne
            // Permit-Freigabe, sonst würde ein Doppel-Release maxSize überhöhen
            // und dieselbe Verbindung läge zweimal in der Queue.
            LOG.warning("release() ignoriert: Verbindung ist nicht (mehr) aus diesem Pool ausgeliehen "
                    + "(Doppel-Release oder fremde Instanz)");
            return;
        }
        try {
            if (connection.isClosed()) {
                return;
            }
            if (!connection.getAutoCommit()) {
                // JDBC-Spezifikation: setAutoCommit(true) committet eine offene
                // Transaktion implizit. Erst erfolgreich zurückrollen, dann in den
                // Autocommit-Modus schalten — release() darf keinen Teilstand
                // festschreiben (z. B. wenn der TxManager einen Error durchreicht).
                connection.rollback();
                connection.setAutoCommit(true);
            }
            if (!idle.offer(connection)) {
                closeQuietly(connection);
            }
        } catch (SQLException | RuntimeException e) {
            // Defekte Verbindung: verwerfen statt zurücklegen — das Permit wird
            // unten in jedem Fall genau einmal zurückgegeben.
            closeQuietly(connection);
        } finally {
            // Das Permit wird in jedem Pfad genau einmal zurückgegeben — auch wenn
            // die Verbindung defekt ist und verworfen wird, sonst leert der Pool
            // und getConnection blockt ewig.
            permits.release();
        }
    }

    @Override
    public void close() {
        List<Connection> remaining = new ArrayList<>();
        idle.drainTo(remaining);
        for (Connection connection : remaining) {
            closeQuietly(connection);
        }
    }

    /**
     * Prüft, ob eine Verbindung ausgeliefert werden darf. Fängt auch unerwartete
     * Laufzeitausnahmen (kaputte Treiber-/Proxy-Implementierungen), damit beim
     * Entleihen nie eine unvalidierbare Verbindung durchrutscht.
     */
    private boolean istGueltig(Connection connection) {
        try {
            return !connection.isClosed() && connection.isValid(VALIDIERUNGS_TIMEOUT_SEKUNDEN);
        } catch (SQLException | RuntimeException e) {
            LOG.fine(() -> "Verbindung besteht die Gültigkeitsprüfung nicht, wird verworfen: " + e);
            return false;
        }
    }

    private void closeQuietly(Connection connection) {
        try {
            connection.close();
        } catch (SQLException | RuntimeException ignored) {
            // nichts zu holen, Verbindung ist bereits weg
        }
    }
}