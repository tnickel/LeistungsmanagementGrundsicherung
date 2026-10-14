package de.allgemein.lmg.web;

import de.allgemein.lmg.service.LeistungsService;
import de.allgemein.lmg.service.Systemdiagnose;
import de.allgemein.tx.FakePool;
import de.allgemein.tx.TaskController;
import de.allgemein.tx.TxManager;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** JDK liest Limits einmalig: frische JVM plus echte blockierte Sockets statt Properties-Scheinprüfung. */
class HttpServerLimitsTest {
    @Test void incompleteHeadersAndBodyAreTerminatedAndServerRemainsAvailable() throws Exception {
        runChild("request", "-Dsun.net.httpserver.maxReqTime=1");
    }

    @Test void stalledResponseIsTerminated() throws Exception {
        runChild("response", "-Dsun.net.httpserver.maxRspTime=1");
    }

    @Test void unsafeOrMalformedStartupLimitsFailClosed() throws Exception {
        for (String property : List.of("-Dsun.net.httpserver.maxReqTime=0",
                "-Dsun.net.httpserver.maxRspTime=-1", "-Dsun.net.httpserver.idleInterval=wrong",
                "-Djdk.httpserver.maxConnections=2147483648")) runChild("invalid", property);
    }

    private static void runChild(String mode, String property) throws Exception {
        var command = new ArrayList<String>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        command.add(property);
        command.addAll(List.of("-cp", System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")),
                Probe.class.getName(), mode));
        var process = new ProcessBuilder(command).redirectErrorStream(true).start();
        try {
            assertTrue(process.waitFor(15, TimeUnit.SECONDS), "HTTP-Grenzprüfung hängt");
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertEquals(0, process.exitValue(), output);
            assertTrue(output.contains("LIMIT_PROBE_OK"), output);
        } finally { if (process.isAlive()) process.destroyForcibly(); }
    }

    public static final class Probe {
        public static void main(String[] args) throws Exception {
            if (args[0].equals("invalid")) {
                try { HttpServerLimits.configure(); throw new AssertionError("Unsicheres Limit akzeptiert"); }
                catch (IllegalArgumentException expected) { System.out.println("LIMIT_PROBE_OK"); return; }
            }
            HttpServerLimits.configure();
            if (args[0].equals("response")) responseProbe(); else requestProbe();
            System.out.println("LIMIT_PROBE_OK");
        }

        private static void requestProbe() throws Exception {
            var tx = new TxManager(new FakePool(2));
            try (var tasks = new TaskController(tx, 1, 2)) {
                var service = (LeistungsService) Proxy.newProxyInstance(LeistungsService.class.getClassLoader(),
                        new Class<?>[]{LeistungsService.class}, (proxy, method, args) -> {
                            throw new AssertionError("Unvollständiger Request darf keinen Service erreichen");
                        });
                try (var server = new LmgServer(tasks, service, new Systemdiagnose(tx, tasks, null, Instant.now()),
                        new Benutzerverwaltung("sb:pass:sachbearbeiter", Duration.ofMinutes(1)), 0)) {
                    server.start();
                    int port = server.adresse().getPort();
                    for (String partial : List.of("GET /api/werbin HTTP/1.1\r\nHost: localhost\r\nX-Stalled:",
                            "POST /api/anmeldung HTTP/1.1\r\nHost: localhost\r\nContent-Length: 100\r\n\r\n{")) {
                        try (var socket = new Socket("127.0.0.1", port)) {
                            socket.setSoTimeout(6000);
                            socket.getOutputStream().write(partial.getBytes(StandardCharsets.US_ASCII));
                            if (socket.getInputStream().read() != -1) throw new AssertionError("Unvollständige Anfrage nicht geschlossen");
                        }
                    }
                    try (var socket = new Socket("127.0.0.1", port)) {
                        socket.setSoTimeout(3000);
                        socket.getOutputStream().write("GET /api/werbin HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n"
                                .getBytes(StandardCharsets.US_ASCII));
                        String response = new String(socket.getInputStream().readAllBytes(), StandardCharsets.US_ASCII);
                        if (!response.startsWith("HTTP/1.1 401")) throw new AssertionError("Server nach Timeout nicht erreichbar");
                    }
                }
            }
        }

        private static void responseProbe() throws Exception {
            var release = new CountDownLatch(1);
            var executor = java.util.concurrent.Executors.newSingleThreadExecutor();
            var server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(executor);
            server.createContext("/", exchange -> {
                exchange.getRequestBody().readAllBytes();
                exchange.sendResponseHeaders(200, 0);
                exchange.getResponseBody().write('x');
                exchange.getResponseBody().flush();
                try { release.await(10, TimeUnit.SECONDS); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                finally { exchange.close(); }
            });
            server.start();
            try (var socket = new Socket("127.0.0.1", server.getAddress().getPort())) {
                socket.setSoTimeout(6000);
                socket.getOutputStream().write("GET / HTTP/1.1\r\nHost: localhost\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
                String response = new String(socket.getInputStream().readAllBytes(), StandardCharsets.US_ASCII);
                if (!response.startsWith("HTTP/1.1 200")) throw new AssertionError("Antwortphase nicht erreicht");
            } finally { release.countDown(); server.stop(0); executor.shutdownNow(); }
        }
    }
}
