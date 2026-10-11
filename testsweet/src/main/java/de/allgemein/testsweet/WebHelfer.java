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

    /** Klick mit JavaScript-Fallback: überlappt bei schmalen Fenstern ein Menü
     *  den Zielknopf, klickt Selenium direkt im DOM — robust in der Fenster-Rasterung. */
    private static void klick(WebDriver d, WebElement element) {
        try {
            element.click();
        } catch (org.openqa.selenium.ElementClickInterceptedException
                | org.openqa.selenium.TimeoutException e) {
            ((org.openqa.selenium.JavascriptExecutor) d).executeScript(
                    "arguments[0].click()", element);
        }
    }

    /** Wartet nur auf das VORHANDENSEIN eines Knopfes, scrollt ihn ins Sichtfeld und
     *  klickt mit JS-Fallback. Bewusst kein elementToBeClickable: In den kleinen
     *  Rasterfenstern ist der Knopf oft außerhalb des sichtbaren Ausschnitts, obwohl
     *  er im DOM längst da ist — der DOM-Klick arbeitet davon unabhängig. */
    private static void klickAufButton(WebDriver d, By locator) {
        WebDriverWait wait = new WebDriverWait(d, TIMEOUT);
        WebElement element = wait.until(ExpectedConditions.presenceOfElementLocated(locator));
        ((JavascriptExecutor) d).executeScript(
                "arguments[0].scrollIntoView({block: 'center'});", element);
        klick(d, element);
    }

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
                klick(treiber, abmelden);
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

        klick(treiber, treiber.findElement(By.id("btn-login")));

        // Warten bis Fallübersicht sichtbar ist
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("view-uebersicht")));
        autoConfirmAktivieren(treiber);
        return "Erfolgreich angemeldet als " + user;
    }

    /** Öffnet die Neuanlage-Karte nur, wenn sie noch zu ist — der Knopf ist ein
     *  Toggle: Nach einem abgewehrten Versuch bleibt die Karte offen, ein zweiter
     *  Klick würde sie einklappen und der Sichtbarkeits-Wartetimmer liefe ins Leere. */
    private static void oeffneNeuVorgangKarte(WebDriver treiber, WebDriverWait wait) {
        if (!treiber.findElement(By.id("karte-neu-vorgang")).isDisplayed()) {
            klick(treiber, wait.until(ExpectedConditions.elementToBeClickable(By.id("btn-neu-vorgang"))));
            wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("karte-neu-vorgang")));
        }
    }

    public static String vorgangAnlegen(WebDriver treiber, Map<String, Object> ctx,
            String aktenzeichen, String name, BigDecimal budget) {
        WebDriverWait wait = new WebDriverWait(treiber, TIMEOUT);
        autoConfirmAktivieren(treiber);

        oeffneNeuVorgangKarte(treiber, wait);

        setzeWert(treiber, By.id("neu-aktenzeichen"), aktenzeichen);
        setzeWert(treiber, By.id("neu-name"), name);
        setzeWert(treiber, By.id("neu-budget"), budget.toPlainString());
        setzeWert(treiber, By.id("neu-iban"), "DE89 3704 0044 0532 0130 00");
        setzeWert(treiber, By.id("neu-bic"), "MARKDEF1100");
        setzeWert(treiber, By.id("neu-kontoinhaber"), name);

        klick(treiber, treiber.findElement(By.id("btn-vorgang-speichern")));

        // Warten bis Vorgangsmaske geladen ist
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("view-vorgang")));
        schliesseModalFallsOffen(treiber);
        ctx.put("vorgangErstellt", true); // Marker für den globalen Konsistenzcheck
        return "Vorgang " + aktenzeichen + " (" + name + ") mit Startbudget "
                + budget.toPlainString() + " EUR angelegt";
    }

    public static String fallAnlegen(WebDriver treiber, Map<String, Object> ctx,
            int rbs, boolean merkzeichenG, boolean warmwasser,
            BigDecimal miete, BigDecimal betrieb, BigDecimal heizung) {
        WebDriverWait wait = new WebDriverWait(treiber, TIMEOUT);
        autoConfirmAktivieren(treiber);

        WebElement neuFallBtn = wait.until(ExpectedConditions.elementToBeClickable(By.id("btn-neu-fall")));
        klick(treiber, neuFallBtn);

        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("fall-neu")));

        Select rbsSelect = new Select(treiber.findElement(By.id("neu-fall-rbs")));
        rbsSelect.selectByValue(String.valueOf(rbs));

        setzeWert(treiber, By.id("neu-fall-miete"), miete.toPlainString());
        setzeWert(treiber, By.id("neu-fall-betriebskosten"), betrieb.toPlainString());
        setzeWert(treiber, By.id("neu-fall-heizung"), heizung.toPlainString());

        WebElement chkG = treiber.findElement(By.id("neu-fall-g"));
        if (chkG.isSelected() != merkzeichenG) {
            klick(treiber, chkG);
        }

        WebElement chkWw = treiber.findElement(By.id("neu-fall-warmwasser"));
        if (chkWw.isSelected() != warmwasser) {
            klick(treiber, chkWw);
        }

        klick(treiber, treiber.findElement(By.id("btn-fall-speichern")));

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
        // Monatsfeld per JavaScript setzen: Chrome nimmt Tastatureingaben (sendKeys)
        // auf <input type="month"> nicht zuverlässig an, das Feld bliebe leer.
        ((JavascriptExecutor) treiber).executeScript(
                "arguments[0].value = arguments[1];"
                + "arguments[0].dispatchEvent(new Event('input', {bubbles: true}));"
                + "arguments[0].dispatchEvent(new Event('change', {bubbles: true}));",
                monatFeld, monat);

        klickAufButton(treiber, By.id("btn-auszahlung"));

        schliesseModalFallsOffen(treiber);

        // Erfolg beweisen: nur eine wirklich angeordnete Zahlung erzeugt die
        // Aktionsknöpfe — die Platzhalterzeile «Noch keine Auszahlungen angeordnet»
        // ist zwar auch eine Tabellenzeile, aber kein Beleg für eine Buchung.
        wait.until(ExpectedConditions.presenceOfElementLocated(
                By.cssSelector("#tabelle-zahlungen button.freigeben")));
        return "Monatliche Auszahlung für " + monat + " angeordnet (Vier-Augen-Warteschlange: ANGELEGT)";
    }

    public static String kollegenLogin(WebDriver treiber, Map<String, Object> ctx) {
        String kollege = (String) ctx.getOrDefault("kollege", "sb-02");
        String kollegePw = (String) ctx.getOrDefault("kollegePasswort", kollege);
        meldeAnAls(treiber, kollege, kollegePw);
        return "Vier-Augen-Wechsel: angemeldet als Kollege " + kollege;
    }

    /** Meldet die aktuelle Sitzung ab und als anderem Benutzer wieder an —
     *  der Rollenwechsel im selben Fenster bildet Vier-Augen-Abläufe nach. */
    private static void meldeAnAls(WebDriver treiber, String kennung, String passwort) {
        WebDriverWait wait = new WebDriverWait(treiber, TIMEOUT);
        autoConfirmAktivieren(treiber);

        WebElement abmelden = wait.until(ExpectedConditions.elementToBeClickable(By.id("btn-abmelden")));
        klick(treiber, abmelden);

        WebElement loginKennung = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("login-kennung")));
        loginKennung.clear();
        loginKennung.sendKeys(kennung);

        WebElement passwortFeld = treiber.findElement(By.id("login-passwort"));
        passwortFeld.clear();
        passwortFeld.sendKeys(passwort);

        klick(treiber, treiber.findElement(By.id("btn-login")));

        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("view-uebersicht")));
        autoConfirmAktivieren(treiber);
    }

    public static String vorgangSuchen(WebDriver treiber, Map<String, Object> ctx, String aktenzeichen) {
        oeffneUeberSuche(treiber, aktenzeichen);
        return "Vorgang " + aktenzeichen + " über Suchfeld ermittelt und geöffnet";
    }

    /** Findet den Vorgang über das Suchfeld der Übersicht und öffnet ihn. */
    private static void oeffneUeberSuche(WebDriver treiber, String aktenzeichen) {
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
        klick(treiber, oeffnenBtn);

        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("view-vorgang")));
        schliesseModalFallsOffen(treiber);
    }

    public static String freigeben(WebDriver treiber, Map<String, Object> ctx) {
        WebDriverWait wait = new WebDriverWait(treiber, TIMEOUT);
        autoConfirmAktivieren(treiber);

        klickAufButton(treiber, By.cssSelector("#tabelle-zahlungen button.freigeben"));

        schliesseModalFallsOffen(treiber);

        // Warten bis der Status-Chip 'bereit' anzeigt
        wait.until(ExpectedConditions.presenceOfElementLocated(
                By.cssSelector("#tabelle-zahlungen .chip.bereit")));
        return "Zahlung rechnerisch festgestellt und freigegeben (Status BEREIT)";
    }

    /**
     * Storno einer freigegebenen (BEREIT) Zahlung: Die Oberfläche bietet den
     * Storno-Knopf dafür nur der Admin-Rolle an — der Schritt wechselt daher
     * erst auf ein Administrationskonto, öffnet den Vorgang erneut und bucht
     * dann den Storno.
     */
    public static String stornoWechsel(WebDriver treiber, Map<String, Object> ctx,
            String aktenzeichen) {
        WebDriverWait wait = new WebDriverWait(treiber, TIMEOUT);
        String admin = (String) ctx.getOrDefault("adminKonto", "admin-01");
        String adminPw = (String) ctx.getOrDefault("adminPasswort", admin);
        meldeAnAls(treiber, admin, adminPw);
        oeffneUeberSuche(treiber, aktenzeichen);

        klickAufButton(treiber, By.cssSelector("#tabelle-zahlungen button.storno"));

        schliesseModalFallsOffen(treiber);

        wait.until(ExpectedConditions.presenceOfElementLocated(
                By.cssSelector("#tabelle-zahlungen .chip.storniert")));
        return "Als " + admin + " (Admin-Rolle) Storno gebucht (Status STORNIERT, "
                + "Restbudget zurückgebucht)";
    }

    // ------------------------------------------------------------------
    // Negativ-Schritte: die ABWEHR eines Fehlers ist das erwartete Ergebnis.
    // Der Beweis steht zweifach: Fehlerdialog/Fehlerzeile mit erwartetem Text
    // UND (per Erwartung des Auftrags) der unveränderte Datenbank-Endzustand.
    // ------------------------------------------------------------------

    /** Meldet sich mit einem bewusst FALSCHEN Passwort an und prüft, dass die
     *  Oberfläche die Anmeldung abwehrt (Fehlerzeile gefüllt, keine Übersicht). */
    public static String anmeldungFehlschlag(WebDriver treiber, Map<String, Object> ctx) {
        String url = (String) ctx.getOrDefault("baseUrl", basisUrl);
        treiber.get(url);
        WebDriverWait wait = new WebDriverWait(treiber, TIMEOUT);
        autoConfirmAktivieren(treiber);
        WebElement kennung = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("login-kennung")));
        kennung.clear();
        kennung.sendKeys((String) ctx.getOrDefault("mitarbeiter", "sb-01"));
        WebElement passwort = treiber.findElement(By.id("login-passwort"));
        passwort.clear();
        passwort.sendKeys((String) ctx.getOrDefault("falschesPasswort", "total-falsch"));
        klick(treiber, treiber.findElement(By.id("btn-login")));

        WebElement fehlerzeile = wait.until(d -> {
            WebElement el = d.findElement(By.id("login-fehler"));
            return el.getText() != null && !el.getText().isBlank() ? el : null;
        });
        if (treiber.findElements(By.id("view-uebersicht")).stream().anyMatch(WebElement::isDisplayed)) {
            throw new IllegalStateException("Anmeldung mit falschem Passwort war erfolgreich — "
                    + "Anmeldeprüfung verletzt");
        }
        return "Anmeldung abgewehrt — Oberfläche meldet: " + fehlerzeile.getText();
    }

    /** Legt einen Vorgang mit FEHLERHAFTEN Bankverbindungsdaten an und prüft, dass
     *  die Neuanlage mit Fehlerdialog abgewehrt wird. */
    public static String vorgangAnlegenFehlschlag(WebDriver treiber, Map<String, Object> ctx,
            String aktenzeichen, String name, BigDecimal budget, String iban, String bic,
            String... erwarteteTeiltexte) {
        WebDriverWait wait = new WebDriverWait(treiber, TIMEOUT);
        autoConfirmAktivieren(treiber);
        oeffneNeuVorgangKarte(treiber, wait);
        setzeWert(treiber, By.id("neu-aktenzeichen"), aktenzeichen);
        setzeWert(treiber, By.id("neu-name"), name);
        setzeWert(treiber, By.id("neu-budget"), budget.toPlainString());
        setzeWert(treiber, By.id("neu-iban"), iban);
        setzeWert(treiber, By.id("neu-bic"), bic);
        setzeWert(treiber, By.id("neu-kontoinhaber"), name);
        klick(treiber, treiber.findElement(By.id("btn-vorgang-speichern")));

        String text = fehlerDialogPruefen(treiber, erwarteteTeiltexte);
        if (treiber.findElements(By.id("view-vorgang")).stream().anyMatch(WebElement::isDisplayed)) {
            throw new IllegalStateException("Vorgang wurde trotz ungültiger Bankverbindung angelegt");
        }
        return "Neuanlage abgewehrt — Fehlerdialog: " + text;
    }

    /** Klickt „Auszahlung anordnen“ OHNE Zahlungsmonat — die Oberfläche muss das
     *  mit einem Fehlerdialog abwehren. */
    public static String auszahlungOhneMonat(WebDriver treiber, Map<String, Object> ctx) {
        autoConfirmAktivieren(treiber);
        // Das Monatsfeld ist mit dem aktuellen Monat vorbelegt (app.js) — für den
        // Negativfall bewusst leeren, damit die UI-Prüfung "Bitte den Zahlungsmonat
        // wählen" überhaupt anspringen kann.
        WebElement monatFeld = treiber.findElement(By.id("zahlungsmonat"));
        ((JavascriptExecutor) treiber).executeScript(
                "arguments[0].value = '';"
                + "arguments[0].dispatchEvent(new Event('input', {bubbles: true}));"
                + "arguments[0].dispatchEvent(new Event('change', {bubbles: true}));",
                monatFeld);
        klickAufButton(treiber, By.id("btn-auszahlung"));
        String text = fehlerDialogPruefen(treiber, "Zahlungsmonat");
        beweiseKeineZahlung(treiber, true);
        return "Anordnung ohne Zahlungsmonat abgewehrt — Fehlerdialog: " + text;
    }

    /** Setzt den Zahlungsmonat, ordnet an und erwartet den ABGEWEHRTEN Fachfehler
     *  (z. B. KEIN_ANSPRUCH, BUDGET_ERSCHOEPFT, doppelte Anordnung). */
    public static String auszahlungFehlschlag(WebDriver treiber, Map<String, Object> ctx,
            String monat, boolean keineZahlungErwartet, String... erwarteteTeiltexte) {
        WebDriverWait wait = new WebDriverWait(treiber, TIMEOUT);
        autoConfirmAktivieren(treiber);
        WebElement monatFeld = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("zahlungsmonat")));
        ((JavascriptExecutor) treiber).executeScript(
                "arguments[0].value = arguments[1];"
                + "arguments[0].dispatchEvent(new Event('input', {bubbles: true}));"
                + "arguments[0].dispatchEvent(new Event('change', {bubbles: true}));",
                monatFeld, monat);
        klickAufButton(treiber, By.id("btn-auszahlung"));
        String text = fehlerDialogPruefen(treiber, erwarteteTeiltexte);
        beweiseKeineZahlung(treiber, keineZahlungErwartet);
        return "Anordnung abgewehrt — Fehlerdialog: " + text;
    }

    /** Beweist, dass einem Sachbearbeiter der Storno-Knopf für eine freigegebene
     *  (BEREIT-)Zahlung NICHT angeboten wird — die Admin-Beschränkung. */
    public static String pruefeKeinStornoFuerSachbearbeiter(WebDriver treiber, Map<String, Object> ctx) {
        WebDriverWait wait = new WebDriverWait(treiber, TIMEOUT);
        wait.until(ExpectedConditions.presenceOfElementLocated(
                By.cssSelector("#tabelle-zahlungen .chip.bereit")));
        int storno = treiber.findElements(By.cssSelector("#tabelle-zahlungen button.storno")).size();
        if (storno > 0) {
            throw new IllegalStateException("Storno-Knopf für Sachbearbeiter sichtbar — "
                    + "Admin-Beschränkung für freigegebene Zahlungen verletzt");
        }
        return "Storno-Knopf für Sachbearbeiter korrekt ausgeblendet (nur Admin-Rolle)";
    }

    /** SozialFall mit Renteneinkommen erfassen (für KEIN_ANSPRUCH-Varianten):
     *  die Rente wird bewusst VOR dem Speichern in die Maske geschrieben. */
    public static String fallAnlegenMitRente(WebDriver treiber, Map<String, Object> ctx,
            int rbs, boolean merkzeichenG, boolean warmwasser,
            BigDecimal miete, BigDecimal betrieb, BigDecimal heizung, BigDecimal rente) {
        WebDriverWait wait = new WebDriverWait(treiber, TIMEOUT);
        autoConfirmAktivieren(treiber);
        klick(treiber, wait.until(ExpectedConditions.elementToBeClickable(By.id("btn-neu-fall"))));
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("fall-neu")));
        new Select(treiber.findElement(By.id("neu-fall-rbs"))).selectByValue(String.valueOf(rbs));
        setzeWert(treiber, By.id("neu-fall-miete"), miete.toPlainString());
        setzeWert(treiber, By.id("neu-fall-betriebskosten"), betrieb.toPlainString());
        setzeWert(treiber, By.id("neu-fall-heizung"), heizung.toPlainString());
        setzeWert(treiber, By.id("neu-fall-rente"), rente.toPlainString());
        WebElement chkG = treiber.findElement(By.id("neu-fall-g"));
        if (chkG.isSelected() != merkzeichenG) {
            klick(treiber, chkG);
        }
        WebElement chkWw = treiber.findElement(By.id("neu-fall-warmwasser"));
        if (chkWw.isSelected() != warmwasser) {
            klick(treiber, chkWw);
        }
        klick(treiber, treiber.findElement(By.id("btn-fall-speichern")));
        schliesseModalFallsOffen(treiber);
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#tabelle-bedarf tbody tr")));
        return "SozialFall (RBS " + rbs + ", KdU " + miete.add(betrieb).add(heizung).toPlainString()
                + " EUR, Rente " + rente.toPlainString() + " EUR) gespeichert";
    }

    /** Zusatzbeweis nach abgewehrter Anordnung: kein Freigabe-Knopf = keine Zahlung.
     *  Nur fahren, wenn keine Zahlung existieren darf (bei NEG-DOPPELMONAT existiert
     *  die erste Zahlung legitim — dort ist der Beweis false). */
    private static void beweiseKeineZahlung(WebDriver treiber, boolean keineZahlungErwartet) {
        if (!keineZahlungErwartet) {
            return;
        }
        WebDriverWait waitKurz = new WebDriverWait(treiber, Duration.ofMillis(1500));
        try {
            waitKurz.until(ExpectedConditions.presenceOfElementLocated(
                    By.cssSelector("#tabelle-zahlungen button.freigeben")));
            throw new IllegalStateException("Trotz abgewehrter Anordnung existiert eine Zahlung "
                    + "(Freigabe-Knopf ist sichtbar)");
        } catch (org.openqa.selenium.TimeoutException erwartet) {
            // gut: kein Freigabe-Knopf, also keine Zahlung
        }
    }

    /** Liest den Fehlerdialog, prüft ihn auf einen der erwarteten Teilausschnitte
     *  und schließt ihn. Ohne Treffer: IllegalStateException — der Schritt scheitert. */
    private static String fehlerDialogPruefen(WebDriver treiber, String... erwarteteTeiltexte) {
        WebDriverWait wait = new WebDriverWait(treiber, Duration.ofSeconds(5));
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("dialog-rahmen")));
        String text = treiber.findElement(By.id("dialog-text")).getText();
        boolean passt = erwarteteTeiltexte.length == 0;
        for (String teil : erwarteteTeiltexte) {
            passt |= text != null && text.contains(teil);
        }
        if (!passt) {
            throw new IllegalStateException("Fehlerdialog erwartet, aber Text war: «" + text
                    + "» — erwartet wurde einer der Teilausschnitte "
                    + java.util.Arrays.toString(erwarteteTeiltexte));
        }
        klick(treiber, treiber.findElement(By.id("dialog-ok")));
        return text;
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
                klick(treiber, okBtn);
            }
        } catch (Exception ignored) {}
    }
}
