package de.allgemein.testsweet;

import org.openqa.selenium.WebDriver;

import java.util.Map;

/**
 * Ein Arbeitsschritt im Weboberflächen-Modus (Modus 2): der simulierte
 * Sachbearbeiter bedient die echte SDN.Leistung-Oberfläche in einem sichtbaren
 * Chrome-Fenster. {@code funktion} erhält den Selenium-Treiber und den Kontext
 * (aktenzeichen, monat, kollege …) und liefert eine Detailzeile fürs Protokoll.
 */
public record WebAktion(String name, WebSchritt funktion) {

    @FunctionalInterface
    public interface WebSchritt {
        String ausfuehren(WebDriver treiber, Map<String, Object> kontext) throws Exception;
    }

    public static WebAktion schritt(String name, WebSchritt funktion) {
        return new WebAktion(name, funktion);
    }
}
