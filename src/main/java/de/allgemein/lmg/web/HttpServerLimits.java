package de.allgemein.lmg.web;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Begrenzte JDK-HttpServer-Zeiten. JVM-weit vor dem ERSTEN HttpServer.create aufrufen.
 * OpenJDK 21 ServerImpl wandelt maxReqTime/maxRspTime (Sekunden) intern in Millisekunden um.
 * Die Werte werden vom JDK einmalig gelesen; Änderungen erfordern einen JVM-Neustart.
 */
public final class HttpServerLimits {
    private static Map<String, String> configured;

    private HttpServerLimits() { }

    public static synchronized void configure() {
        Map<String, String> settings = new LinkedHashMap<>();
        settings.put("sun.net.httpserver.maxReqTime", checked("sun.net.httpserver.maxReqTime", 30, 1, 300));
        settings.put("sun.net.httpserver.maxRspTime", checked("sun.net.httpserver.maxRspTime", 120, 1, 600));
        settings.put("sun.net.httpserver.idleInterval", checked("sun.net.httpserver.idleInterval", 30, 1, 300));
        settings.put("jdk.httpserver.maxConnections", checked("jdk.httpserver.maxConnections", 256, 16, 4096));
        if (configured != null && !configured.equals(settings)) {
            throw new IllegalStateException("HTTP-Limits dürfen nach der Initialisierung nicht geändert werden; JVM neu starten");
        }
        settings.forEach(System::setProperty);
        configured = Map.copyOf(settings);
    }

    private static String checked(String key, int fallback, int min, int max) {
        String value = System.getProperty(key);
        if (value == null) return Integer.toString(fallback);
        try {
            int number = Integer.parseInt(value);
            if (number >= min && number <= max) return Integer.toString(number);
        } catch (NumberFormatException ignored) { }
        throw new IllegalArgumentException(key + " muss eine ganze Zahl zwischen " + min + " und " + max + " sein");
    }
}
