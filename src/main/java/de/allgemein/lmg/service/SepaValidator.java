package de.allgemein.lmg.service;

import java.math.BigInteger;
import java.util.regex.Pattern;

/**
 * Kurz gesagt: Das ist der IBAN-/BIC-Prüfer.
 * Er säubert Bankverbindungen (Leerzeichen raus, Großbuchstaben) und prüft,
 * ob eine IBAN oder ein BIC richtig aufgebaut ist — inklusive der
 * Prüfziffer, damit Tippfehler auffallen, bevor Geld an die falsche Stelle geht.
 *
 * Validierung und Formatierung von SEPA-Bankverbindungen (IBAN nach ISO 13616 / ISO 7064,
 * BIC nach ISO 9362).
 */
public final class SepaValidator {

    private static final Pattern IBAN_FORMAT = Pattern.compile("^[A-Z]{2}[0-9]{2}[A-Z0-9]{11,30}$");
    private static final Pattern BIC_FORMAT = Pattern.compile("^[A-Z]{4}[A-Z]{2}[A-Z0-9]{2}([A-Z0-9]{3})?$");

    private SepaValidator() {
    }

    /** Bereinigt Leerzeichen und konvertiert in Großbuchstaben. */
    public static String normalisiereIban(String iban) {
        if (iban == null) {
            return null;
        }
        return iban.replaceAll("\\s+", "").toUpperCase();
    }

    /** Bereinigt Leerzeichen und konvertiert in Großbuchstaben. */
    public static String normalisiereBic(String bic) {
        if (bic == null) {
            return null;
        }
        return bic.replaceAll("\\s+", "").toUpperCase();
    }

    /**
     * Prüft eine IBAN auf syntaktische Gültigkeit und korrekte Modulo-97-Prüfziffer.
     */
    public static boolean isValidIban(String rawIban) {
        String iban = normalisiereIban(rawIban);
        if (iban == null || !IBAN_FORMAT.matcher(iban).matches()) {
            return false;
        }

        // Länder-spezifische Längenprüfung für häufige SEPA-Länder
        String land = iban.substring(0, 2);
        if ("DE".equals(land) && iban.length() != 22) {
            return false;
        }
        if ("AT".equals(land) && iban.length() != 20) {
            return false;
        }
        if ("CH".equals(land) && iban.length() != 21) {
            return false;
        }

        // Modulo 97 (ISO 7064): Erste 4 Zeichen ans Ende verschieben
        String umgestellt = iban.substring(4) + iban.substring(0, 4);
        StringBuilder numerisch = new StringBuilder(umgestellt.length() * 2);
        for (char c : umgestellt.toCharArray()) {
            if (Character.isDigit(c)) {
                numerisch.append(c);
            } else if (Character.isLetter(c)) {
                numerisch.append(Character.getNumericValue(c));
            } else {
                return false;
            }
        }

        BigInteger nummer = new BigInteger(numerisch.toString());
        return nummer.mod(BigInteger.valueOf(97)).intValue() == 1;
    }

    /**
     * Prüft einen BIC/SWIFT-Code (8 oder 11 Zeichen).
     */
    public static boolean isValidBic(String rawBic) {
        String bic = normalisiereBic(rawBic);
        if (bic == null) {
            return false;
        }
        return BIC_FORMAT.matcher(bic).matches();
    }

    /**
     * Formatiert eine IBAN in 4er-Blöcke zur lesbaren Anzeige.
     */
    public static String formatiereIban(String rawIban) {
        String iban = normalisiereIban(rawIban);
        if (iban == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < iban.length(); i++) {
            if (i > 0 && i % 4 == 0) {
                sb.append(' ');
            }
            sb.append(iban.charAt(i));
        }
        return sb.toString();
    }
}