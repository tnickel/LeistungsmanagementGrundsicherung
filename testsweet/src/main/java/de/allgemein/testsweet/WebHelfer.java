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

    /** Basis-URL der SDN.Leistung-Weboberfläche — überschreibbar über das
     *  Systemproperty {@code sweet.baseurl} oder die Umgebungsvariable
     *  {@code SWEET_BASE_URL} (für einen externen Server statt des eingebetteten
     *  Standard-Ports). Initialisierung einmalig beim Laden der Klasse; ein
     *  abschließender Schrägstrich wird immer ergänzt. */
    public static volatile String basisUrl = liesBasisUrl();

    private static String liesBasisUrl() {
        String wert = expliziteBasisUrl();
        if (wert == null) wert = "http://localhost:8080";
        wert = wert.trim();
        return wert.endsWith("/") ? wert : wert + "/";
    }

    static String expliziteBasisUrl() {
        String wert = System.getProperty("sweet.baseurl");
        if (wert == null || wert.isBlank()) {
            wert = System.getenv("SWEET_BASE_URL");
        }
        return wert == null || wert.isBlank() ? null : wert.trim();
    }

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

    /** Erstellt einen Produkt-Rechenvorschlag und den vollständig prüfbaren Block.
     * SQL-Übereinstimmung allein bestätigt ihn nicht: Der abschließende Verifikator
     * verlangt zusätzlich die unabhängige arithmetische Nachrechnung. */
    public static BigDecimal berechne(List<Rechenblock> rechnungen, String titel,
            List<SozialFall> faelle, BigDecimal vermoegen) {
        var ergebnis = Leistungsberechnung.berechne(0, faelle,
                Leistungsberechnung.KduParameter.AUS, vermoegen);
        rechnungen.add(new Rechenblock(titel, "2026-10", faelle, vermoegen, BigDecimal.ZERO, ergebnis, List.of()));
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

        WebElement loginKennung = warteAufAnmeldemaske(treiber, wait);
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
        ctx.put("sitzungKennung", user);
        ctx.put("sitzungPasswort", pw);
        return "Erfolgreich angemeldet als " + user;
    }

    /** Seitenladen beendet nicht die asynchrone Sitzungsprüfung (/api/werbin).
     * Erst auf deren sichtbares Ergebnis warten, dann eine vorhandene Sitzung
     * kontrolliert abmelden. Ein einmaliger Blick auf den anfangs versteckten
     * Logout-Knopf würde die später wiederhergestellte Sitzung übersehen. */
    private static WebElement warteAufAnmeldemaske(WebDriver treiber, WebDriverWait wait) {
        WebElement bereit=wait.until(d->{
            for(WebElement element:d.findElements(By.id("btn-abmelden")))if(element.isDisplayed())return element;
            for(WebElement element:d.findElements(By.id("login-kennung")))if(element.isDisplayed())return element;
            return null;
        });
        if(treiber.findElements(By.id("btn-abmelden")).stream().anyMatch(WebElement::isDisplayed))klick(treiber,bereit);
        return wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("login-kennung")));
    }

    /** Neue Sitzung mit der zuletzt bestätigten Identität; die wiederholbare
     * Suchaktion öffnet anschließend den bereits persistierten Vorgang. */
    static void sitzungWiederherstellen(WebDriver treiber, Map<String, Object> ctx) {
        Map<String, Object> anmeldung = new java.util.HashMap<>(ctx);
        anmeldung.put("mitarbeiter", ctx.getOrDefault("sitzungKennung", ctx.get("mitarbeiter")));
        anmeldung.put("passwort", ctx.getOrDefault("sitzungPasswort", ctx.get("passwort")));
        anmeldung(treiber, anmeldung);
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
        wait.until(ExpectedConditions.textToBe(By.id("kopf-aktenzeichen"), aktenzeichen));
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

        erfolgsDialogPruefen(treiber, "SozialFall angelegt");

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

        erfolgsDialogPruefen(treiber, "Auszahlung erfasst");

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
        ctx.put("sitzungKennung", kollege);
        ctx.put("sitzungPasswort", kollegePw);
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

        // Die Übersicht kann nach dem Rollenwechsel noch asynchron ersetzt werden.
        // Deshalb den Knopf samt Zeilenbedingung bei jedem Versuch frisch suchen;
        // keine inzwischen abgelöste Tabellenzeile als WebElement behalten.
        By oeffnen = By.xpath("//table[@id='tabelle-bgs']//tr[td[contains(text(), '" + aktenzeichen
                + "')]]//button[contains(text(), 'Öffnen')]");
        wait.until(d -> {
            try {
                WebElement knopf = d.findElement(oeffnen);
                if (!knopf.isDisplayed() || !knopf.isEnabled()) return false;
                klick(d, knopf);
                return true; // Nach erfolgreichem Klick nur noch die Detailansicht abwarten.
            } catch (org.openqa.selenium.StaleElementReferenceException e) {
                // Ausschließlich der lesende Öffnungsschritt wird neu aufgelöst.
                // Anordnen, Freigeben und Storno werden hier nicht wiederholt.
                return false;
            }
        });

        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("view-vorgang")));
    }

    public static String freigeben(WebDriver treiber, Map<String, Object> ctx) {
        WebDriverWait wait = new WebDriverWait(treiber, TIMEOUT);
        autoConfirmAktivieren(treiber);

        klickAufButton(treiber, By.cssSelector("#tabelle-zahlungen button.freigeben"));

        erfolgsDialogPruefen(treiber, "Zahlung freigegeben");

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

        erfolgsDialogPruefen(treiber, "Storno gebucht");

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
        // Sitzung in sessionStorage kann nach get() erst asynchron wieder erscheinen.
        WebElement kennung = warteAufAnmeldemaske(treiber, wait);
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
        Object token = ((JavascriptExecutor) treiber).executeScript("return sessionStorage.getItem('lmgToken');");
        pruefeAnmeldeAbweisung(fehlerzeile.getDomAttribute("data-http-status"),
                fehlerzeile.getDomAttribute("data-error-code"), fehlerzeile.getText(), token);
        if (treiber.findElements(By.id("view-uebersicht")).stream().anyMatch(WebElement::isDisplayed)) {
            throw new IllegalStateException("Anmeldung mit falschem Passwort war erfolgreich — "
                    + "Anmeldeprüfung verletzt");
        }
        return "Anmeldung abgewehrt — Oberfläche meldet: " + fehlerzeile.getText();
    }

    static void pruefeAnmeldeAbweisung(String status, String code, String message, Object token) {
        // Die bestehende API meldet ungültige Anmeldedaten mit 400/VALIDATION;
        // 401 bezeichnet eine fehlende Sitzung und ist kein Beleg für die Passwortprüfung.
        if (!"400".equals(status) || !"VALIDATION".equals(code)
                || !"Kennung oder Passwort ist falsch".equals(message) || token != null) {
            throw new IllegalStateException("Keine kontrollierte Passwort-Abweisung: HTTP " + status
                    + ", Code " + code + ", Meldung " + message + (token == null ? "" : ", Sitzung wurde erzeugt"));
        }
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
        erfolgsDialogPruefen(treiber, "SozialFall angelegt");
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
        WebDriverWait wait = new WebDriverWait(treiber, TIMEOUT);
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("#dialog-rahmen .dialog.fehler")));
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

    /** Der Antwortdialog gehört zum Schreibschritt und muss auch bei langsamer Antwort bestätigt werden. */
    static void erfolgsDialogPruefen(WebDriver treiber, String titel) {
        WebDriverWait wait = new WebDriverWait(treiber, TIMEOUT);
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("dialog-rahmen")));
        String actual = treiber.findElement(By.id("dialog-titel")).getText();
        if (!titel.equals(actual)
                || !treiber.findElements(By.cssSelector("#dialog-rahmen .dialog.fehler")).isEmpty()) {
            throw new IllegalStateException("Erfolgsdialog '" + titel + "' erwartet, erhalten: " + actual
                    + " — " + treiber.findElement(By.id("dialog-text")).getText());
        }
        klick(treiber, treiber.findElement(By.id("dialog-ok")));
        wait.until(ExpectedConditions.invisibilityOfElementLocated(By.id("dialog-rahmen")));
    }
}
