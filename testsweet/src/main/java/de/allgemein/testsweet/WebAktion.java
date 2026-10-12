package de.allgemein.testsweet;

import org.openqa.selenium.WebDriver;

import java.util.Map;

/**
 * Ein Arbeitsschritt im Weboberflächen-Modus (Modus 2): der simulierte
 * Sachbearbeiter bedient die echte SDN.Leistung-Oberfläche in einem sichtbaren
 * Chrome-Fenster. {@code funktion} erhält den Selenium-Treiber und den Kontext
 * (aktenzeichen, monat, kollege …) und liefert eine Detailzeile fürs Protokoll.
 */
public record WebAktion(String name, WebSchritt funktion, boolean wiederholbar) {

    public WebAktion(String name, WebSchritt funktion) {
        this(name, funktion, false);
    }

    @FunctionalInterface
    public interface WebSchritt {
        String ausfuehren(WebDriver treiber, Map<String, Object> kontext) throws Exception;
    }

    public static WebAktion schritt(String name, WebSchritt funktion) {
        return new WebAktion(name, funktion);
    }

    /** Nur Navigation/Anmeldung; schreibende Aktionen bleiben im Zweifel gesperrt. */
    public static WebAktion wiederholbar(String name, WebSchritt funktion) {
        return new WebAktion(name, funktion, true);
    }
}
