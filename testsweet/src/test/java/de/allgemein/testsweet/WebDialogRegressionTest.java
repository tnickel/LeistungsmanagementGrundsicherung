package de.allgemein.testsweet;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/** Simulierter DOM: verzögerte Antwortdialoge brauchen keine echte Browsersitzung. */
class WebDialogRegressionTest {
    @Test void langsamerErfolgsdialogWirdAbgewartetUndGeschlossen() {
        AtomicBoolean closed = new AtomicBoolean();
        WebDriver driver = driver("Auszahlung erfasst", false, Duration.ofMillis(900), closed);
        WebHelfer.erfolgsDialogPruefen(driver, "Auszahlung erfasst");
        assertTrue(closed.get());
    }

    @Test void fehlerdialogWirdNichtAlsErfolgreicheBuchungBestaetigt() {
        AtomicBoolean closed = new AtomicBoolean();
        WebDriver driver = driver("Auszahlung nicht möglich", true, Duration.ZERO, closed);
        assertThrows(IllegalStateException.class, () -> WebHelfer.erfolgsDialogPruefen(driver, "Auszahlung erfasst"));
        assertFalse(closed.get());
    }

    private static WebDriver driver(String title, boolean error, Duration delay, AtomicBoolean closed) {
        long visibleAt = System.nanoTime() + delay.toNanos();
        return (WebDriver) Proxy.newProxyInstance(WebDriver.class.getClassLoader(), new Class<?>[]{WebDriver.class},
                (object, method, args) -> switch (method.getName()) {
                    case "findElement" -> element(String.valueOf(args[0]), title, visibleAt, closed);
                    case "findElements" -> error ? List.of(element("error", title, visibleAt, closed)) : List.of();
                    case "toString" -> "DOM-Prüftreiber";
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private static WebElement element(String locator, String title, long visibleAt, AtomicBoolean closed) {
        return (WebElement) Proxy.newProxyInstance(WebElement.class.getClassLoader(), new Class<?>[]{WebElement.class},
                (object, method, args) -> switch (method.getName()) {
                    case "isDisplayed" -> System.nanoTime() >= visibleAt && !closed.get();
                    case "getText" -> locator.contains("dialog-titel") ? title : "Antwort zur aktuellen Buchung";
                    case "click" -> { closed.set(true); yield null; }
                    case "toString" -> locator;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}
