package de.allgemein.lmg.service;

import java.time.YearMonth;
import java.util.regex.Pattern;

/** Zahlungsmonat: Abrechnungszeitraum im Format YYYY-MM (siehe Spalte ZAHLUNG.ZAHLUNGSMONAT). */
public final class Zahlungsmonat {

    private static final Pattern MUSTER = Pattern.compile("\\d{4}-(0[1-9]|1[0-2])");

    private Zahlungsmonat() {
    }

    public static boolean isValid(String wert) {
        return wert != null && MUSTER.matcher(wert).matches();
    }

    /** aktueller Monat im Zahlungsmonat-Format, als Vorgabe für die Maske. */
    public static String aktuellerMonat() {
        return YearMonth.now().toString();
    }
}
