package de.allgemein.testsweet;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import java.lang.reflect.Proxy;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Austausch der asynchron geladenen Übersicht zwischen Suchen und Öffnen, ohne Browser/DB. */
class WebVorgangSuchenRegressionTest {
    private static final String AKTENZEICHEN = "SWEET-W-0026";

    @Test
    void ersetzteListenzeileOderKnopfWirdVorDemLesendenOeffnenFrischGesucht() {
        for (boolean staleBeimSuchen : new boolean[]{true, false}) {
            Dom dom = new Dom(staleBeimSuchen, 0);
            String result = WebHelfer.vorgangSuchen(dom.driver, new HashMap<>(), AKTENZEICHEN);
            assertTrue(result.contains(AKTENZEICHEN));
            assertEquals(AKTENZEICHEN, dom.suchtext);
            assertEquals(2, dom.knopfSuchen, "Stale löst eine neue Locator-Suche aus");
            assertEquals(0, dom.zeilenSuchen, "keine Tabellenzeile für spätere Kindzugriffe behalten");
            assertEquals(1, dom.erfolgreicheOeffnungen);
            assertEquals(0, dom.schreibKlicks);
        }
    }

    @Test
    void bestaetigterOeffnenKlickWirdBeimWartenAufLangsameDetailansichtNichtWiederholt() {
        Dom dom = new Dom(false, 3);
        WebHelfer.vorgangSuchen(dom.driver, new HashMap<>(), AKTENZEICHEN);
        assertEquals(1, dom.erfolgreicheOeffnungen, "nach Dispatch nur die Antwortansicht abwarten");
        assertEquals(2, dom.knopfSuchen, "nur der erste, explizit stale Versuch wird ersetzt");
        assertTrue(dom.detailPruefungen > 3);
    }

    @Test
    void staleBeiEinerFreigabeWirdNichtAlsSuchwiederholungBehandelt() {
        Dom dom = new Dom(false, 0);
        assertThrows(StaleElementReferenceException.class,
                () -> WebHelfer.freigeben(dom.driver, new HashMap<>()));
        assertEquals(1, dom.schreibKlicks, "keine Wiederholung einer schreibenden Fachaktion");
        assertEquals(0, dom.erfolgreicheOeffnungen);
        assertEquals(0, dom.knopfSuchen);
    }

    private static final class Dom {
        final WebDriver driver;
        final boolean staleBeimSuchen;
        final int detailVerzoegerung;
        String suchtext = "";
        int knopfSuchen, zeilenSuchen, erfolgreicheOeffnungen, detailPruefungen, schreibKlicks;

        Dom(boolean staleBeimSuchen, int detailVerzoegerung) {
            this.staleBeimSuchen = staleBeimSuchen;
            this.detailVerzoegerung = detailVerzoegerung;
            driver = (WebDriver) Proxy.newProxyInstance(WebDriver.class.getClassLoader(),
                    new Class<?>[]{WebDriver.class, JavascriptExecutor.class}, (proxy, method, args) -> {
                        if (method.getName().equals("executeScript")) return null;
                        if (method.getName().equals("toString")) return "Asynchron ersetzte Vorgangsliste";
                        if (!method.getName().equals("findElement"))
                            throw new UnsupportedOperationException(method.getName());
                        String locator = String.valueOf(args[0]);
                        if (locator.contains("suchfeld")) return element("suchfeld", false);
                        if (locator.contains("view-vorgang")) return element("detail", false);
                        if (locator.contains("button.freigeben")) return element("schreiben", false);
                        if (locator.contains("tabelle-bgs")) {
                            assertTrue(locator.contains(AKTENZEICHEN));
                            if (!locator.contains("//button")) {
                                // Originalfehler: eine eben gefundene Zeile ist beim Kindzugriff schon ersetzt.
                                zeilenSuchen++;
                                return element("alte-zeile", true);
                            }
                            knopfSuchen++;
                            if (knopfSuchen == 1 && staleBeimSuchen)
                                throw new StaleElementReferenceException("Übersicht beim Locator ersetzt");
                            return element("oeffnen", knopfSuchen == 1);
                        }
                        throw new UnsupportedOperationException(locator);
                    });
        }

        private WebElement element(String art, boolean stale) {
            return (WebElement) Proxy.newProxyInstance(WebElement.class.getClassLoader(),
                    new Class<?>[]{WebElement.class}, (proxy, method, args) -> switch (method.getName()) {
                        case "clear" -> { suchtext = ""; yield null; }
                        case "sendKeys" -> { suchtext = String.join("", (CharSequence[]) args[0]); yield null; }
                        case "isDisplayed" -> !art.equals("detail")
                                || erfolgreicheOeffnungen > 0 && ++detailPruefungen > detailVerzoegerung;
                        case "isEnabled" -> true;
                        case "findElement" -> throw new StaleElementReferenceException("Listenzeile vor Kindzugriff ersetzt");
                        case "click" -> {
                            if (art.equals("schreiben")) {
                                schreibKlicks++;
                                throw new StaleElementReferenceException("Schreibknopf ersetzt");
                            }
                            if (stale) throw new StaleElementReferenceException("Öffnenknopf vor Klick ersetzt");
                            erfolgreicheOeffnungen++;
                            yield null;
                        }
                        case "toString" -> art;
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
        }
    }
}
