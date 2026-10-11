package de.allgemein.testsweet;

import de.allgemein.lmg.model.SozialFall;
import de.allgemein.lmg.calc.Leistungsberechnung;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Automatisierungshelfer für den Weboberflächen-Modus (Modus 2):
 * Steuert die SDN.Leistung-Oberfläche per Selenium WebDriver über
 * standardisierte und robuste Wartezeiten.
 */
public final class WebHelfer {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    public static volatile String basisUrl = "http://localhost:8080/";

    private WebHelfer() {}

    /** Vollständiger Fall-Entwurf für die Weboberfläche. */
    public static SozialFall fallEntwurf(int rbs, boolean g, boolean ww,
            BigDecimal miete, BigDecimal betrieb, BigDecimal heizung) {
        return new SozialFall(0, 0, rbs, g, false, 0, 0, 0, ww,
                miete, betrieb, heizung, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 1);
    }

    /** Berechnet den erwarteten Zahlbetrag vorab für die Testdokumentation. */
    public static BigDecimal berechne(List<Rechenblock> rechnungen, String titel,
            List<SozialFall> faelle, BigDecimal vermoegen) {
        var ergebnis = Leistungsberechnung.berechne(0, faelle,
                Leistungsberechnung.KduParameter.AUS, vermoegen);
        rechnungen.add(new Rechenblock(titel, faelle, vermoegen, BigDecimal.ZERO, ergebnis, List.of()));
        return ergebnis.zahlbetrag();
    }

    // ------------------------------------------------------------------
    // Weboberflächen-Schritte
    // ------------------------------------------------------------------

    public static String anmeldung(WebDriver treiber, Map<String, Object> ctx) {
        String url = (String) ctx.getOrDefault("baseUrl", basisUrl);
        treiber.get(url);
        WebDriverWait wait = new WebDriverWait(treiber, TIMEOUT);

        // Bestätigungsdialoge automatisch zustimmen
        autoConfirmAktivieren(treiber);

        // Falls bereits angemeldet, abmelden
        try {
            WebElement abmelden = treiber.findElement(By.id("btn-abmelden"));
            if (abmelden.isDisplayed()) {
                abmelden.click();
            }
        } catch (Exception ignored) {}

        WebElement loginKennung = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("login-kennung")));
        loginKennung.clear();
        String user = (String) ctx.getOrDefault("mitarbeiter", "sb-01");
        loginKennung.sendKeys(user);

        WebElement passwort = treiber.findElement(By.id("login-passwort"));
        passwort.clear();
        String pw = (String) ctx.getOrDefault("passwort", user);
        passwort.sendKeys(pw);

        treiber.findElement(By.id("btn-login")).click();

        // Warten bis Fallübersicht sichtbar ist
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("view-uebersicht")));
        autoConfirmAktivieren(treiber);
        return "Erfolgreich angemeldet als " + user;
    }

    public static String vorgangAnlegen(WebDriver treiber, Map<String, Object> ctx,
            String aktenzeichen, String name, BigDecimal budget) {
        WebDriverWait wait = new WebDriverWait(treiber, TIMEOUT);
        autoConfirmAktivieren(treiber);

        WebElement neuBtn = wait.until(ExpectedConditions.elementToBeClickable(By.id("btn-neu-vorgang")));
        neuBtn.click();

        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("karte-neu-vorgang")));

        setzeWert(treiber, By.id("neu-aktenzeichen"), aktenzeichen);
        setzeWert(treiber, By.id("neu-name"), name);
        setzeWert(treiber, By.id("neu-budget"), budget.toPlainString());
        setzeWert(treiber, By.id("neu-iban"), "DE89 3704 0044 0532 0130 00");
        setzeWert(treiber, By.id("neu-bic"), "MARKDEF1100");
        setzeWert(treiber, By.id("neu-kontoinhaber"), name);

        treiber.findElement(By.id("btn-vorgang-speichern")).click();

        // Warten bis Vorgangsmaske geladen ist
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("view-vorgang")));
        schliesseModalFallsOffen(treiber);
        return "Vorgang " + aktenzeichen + " (" + name + ") mit Startbudget "
                + budget.toPlainString() + " EUR angelegt";
    }

    public static String fallAnlegen(WebDriver treiber, Map<String, Object> ctx,
            int rbs, boolean merkzeichenG, boolean warmwasser,
            BigDecimal miete, BigDecimal betrieb, BigDecimal heizung) {
        WebDriverWait wait = new WebDriverWait(treiber, TIMEOUT);
        autoConfirmAktivieren(treiber);

        WebElement neuFallBtn = wait.until(ExpectedConditions.elementToBeClickable(By.id("btn-neu-fall")));
        neuFallBtn.click();

        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("fall-neu")));

        Select rbsSelect = new Select(treiber.findElement(By.id("neu-fall-rbs")));
        rbsSelect.selectByValue(String.valueOf(rbs));

        setzeWert(treiber, By.id("neu-fall-miete"), miete.toPlainString());
        setzeWert(treiber, By.id("neu-fall-betriebskosten"), betrieb.toPlainString());
        setzeWert(treiber, By.id("neu-fall-heizung"), heizung.toPlainString());

        WebElement chkG = treiber.findElement(By.id("neu-fall-g"));
        if (chkG.isSelected() != merkzeichenG) {
            chkG.click();
        }

        WebElement chkWw = treiber.findElement(By.id("neu-fall-warmwasser"));
        if (chkWw.isSelected() != warmwasser) {
            chkWw.click();
        }

        treiber.findElement(By.id("btn-fall-speichern")).click();

        schliesseModalFallsOffen(treiber);

        // Kurz warten bis Berechnungstabelle aktualisiert wurde
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#tabelle-bedarf tbody tr")));
        return "SozialFall (RBS " + rbs + ", KdU " + miete.add(betrieb).add(heizung).toPlainString()
                + " EUR) gespeichert";
    }

    public static String auszahlungAnordnen(WebDriver treiber, Map<String, Object> ctx, String monat) {
        WebDriverWait wait = new WebDriverWait(treiber, TIMEOUT);
        autoConfirmAktivieren(treiber);

        WebElement monatFeld = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("zahlungsmonat")));
        monatFeld.clear();
        monatFeld.sendKeys(monat);

        treiber.findElement(By.id("btn-auszahlung")).click();

        schliesseModalFallsOffen(treiber);

        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#tabelle-zahlungen tbody tr")));
        return "Monatliche Auszahlung für " + monat + " angeordnet (Vier-Augen-Warteschlange: ANGELEGT)";
    }

    public static String kollegenLogin(WebDriver treiber, Map<String, Object> ctx) {
        WebDriverWait wait = new WebDriverWait(treiber, TIMEOUT);
        autoConfirmAktivieren(treiber);

        WebElement abmelden = wait.until(ExpectedConditions.elementToBeClickable(By.id("btn-abmelden")));
        abmelden.click();

        WebElement loginKennung = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("login-kennung")));
        loginKennung.clear();
        String kollege = (String) ctx.getOrDefault("kollege", "sb-02");
        loginKennung.sendKeys(kollege);

        WebElement passwort = treiber.findElement(By.id("login-passwort"));
        passwort.clear();
        String kollegePw = (String) ctx.getOrDefault("kollegePasswort", kollege);
        passwort.sendKeys(kollegePw);

        treiber.findElement(By.id("btn-login")).click();

        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("view-uebersicht")));
        autoConfirmAktivieren(treiber);
        return "Vier-Augen-Wechsel: angemeldet als Kollege " + kollege;
    }

    public static String vorgangSuchen(WebDriver treiber, Map<String, Object> ctx, String aktenzeichen) {
        WebDriverWait wait = new WebDriverWait(treiber, TIMEOUT);
        autoConfirmAktivieren(treiber);

        WebElement suchfeld = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("suchfeld")));
        suchfeld.clear();
        suchfeld.sendKeys(aktenzeichen);

        // Suche filtert clientseitig; warte auf gefilterte Zeile in der Tabelle
        WebElement zeile = wait.until(ExpectedConditions.presenceOfElementLocated(
                By.xpath("//table[@id='tabelle-bgs']//tr[td[contains(text(), '" + aktenzeichen + "')]]")));

        // Button "Öffnen" in dieser Zeile klicken
        WebElement oeffnenBtn = zeile.findElement(By.xpath(".//button[contains(text(), 'Öffnen')]"));
        oeffnenBtn.click();

        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("view-vorgang")));
        schliesseModalFallsOffen(treiber);
        return "Vorgang " + aktenzeichen + " über Suchfeld ermittelt und geöffnet";
    }

    public static String freigeben(WebDriver treiber, Map<String, Object> ctx) {
        WebDriverWait wait = new WebDriverWait(treiber, TIMEOUT);
        autoConfirmAktivieren(treiber);

        WebElement freigebenBtn = wait.until(ExpectedConditions.elementToBeClickable(
                By.cssSelector("#tabelle-zahlungen button.freigeben")));
        freigebenBtn.click();

        schliesseModalFallsOffen(treiber);

        // Warten bis der Status-Chip 'bereit' anzeigt
        wait.until(ExpectedConditions.presenceOfElementLocated(
                By.cssSelector("#tabelle-zahlungen .chip.bereit")));
        return "Zahlung rechnerisch festgestellt und freigegeben (Status BEREIT)";
    }

    public static String stornoWechsel(WebDriver treiber, Map<String, Object> ctx) {
        WebDriverWait wait = new WebDriverWait(treiber, TIMEOUT);
        autoConfirmAktivieren(treiber);

        WebElement stornoBtn = wait.until(ExpectedConditions.elementToBeClickable(
                By.cssSelector("#tabelle-zahlungen button.storno")));
        stornoBtn.click();

        schliesseModalFallsOffen(treiber);

        wait.until(ExpectedConditions.presenceOfElementLocated(
                By.cssSelector("#tabelle-zahlungen .chip.storniert")));
        return "Zahlung storniert (Status STORNIERT, Restbudget zurückgebucht)";
    }

    // ------------------------------------------------------------------
    // Hilfsfunktionen
    // ------------------------------------------------------------------

    private static void setzeWert(WebDriver treiber, By locator, String wert) {
        WebElement el = treiber.findElement(locator);
        el.clear();
        el.sendKeys(wert);
    }

    private static void autoConfirmAktivieren(WebDriver treiber) {
        try {
            if (treiber instanceof JavascriptExecutor js) {
                js.executeScript("window.confirm = function() { return true; };");
            }
        } catch (Exception ignored) {}
    }

    private static void schliesseModalFallsOffen(WebDriver treiber) {
        try {
            WebDriverWait waitKurz = new WebDriverWait(treiber, Duration.ofMillis(800));
            WebElement okBtn = waitKurz.until(ExpectedConditions.elementToBeClickable(By.id("dialog-ok")));
            if (okBtn.isDisplayed()) {
                okBtn.click();
            }
        } catch (Exception ignored) {}
    }
}
