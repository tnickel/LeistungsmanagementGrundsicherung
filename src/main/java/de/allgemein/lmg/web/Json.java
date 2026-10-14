package de.allgemein.lmg.web;

import java.math.BigDecimal;
import java.time.temporal.Temporal;
import java.util.List;
import java.util.Map;

/**
 * Minimale JSON-Unterstützung für die Webschicht — bewusst ohne zusätzliche
 * Bibliothek (Projektregel: keine Frameworks, schmale Abhängigkeiten).
 * Schreiben: Maps/Listen/String/BigDecimal/Boolean/null. Lesen: vollständiger
 * JSON-Parser (Objekte → LinkedHashMap, Arrays → ArrayList, Zahlen → BigDecimal).
 * Zahlen, Leerraum und Escapes folgen RFC 8259. Zusätzlich verlangt diese
 * Anwendung eindeutige Objektschlüssel und wohlgeformte Unicode-Zeichenfolgen;
 * doppelte Schlüssel und ungepaarte UTF-16-Surrogates werden abgewiesen.
 */
public final class Json {

    private Json() {
    }

    // ------------------------------------------------------------------
    // Schreiben
    // ------------------------------------------------------------------

    public static String write(Object value) {
        StringBuilder out = new StringBuilder();
        writeValue(out, value);
        return out.toString();
    }

    public static String stringify(Object value) {
        return write(value);
    }

    private static void writeValue(StringBuilder out, Object value) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof String s) {
            writeString(out, s);
        } else if (value instanceof Boolean b) {
            out.append(b);
        } else if (value instanceof BigDecimal n) {
            out.append(n.toPlainString());
        } else if (value instanceof Number n) {
            double d = n.doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                throw new IllegalArgumentException("JSON kann NaN/Unendlich nicht schreiben");
            }
            out.append(value);
        } else if (value instanceof Temporal t) {
            writeString(out, t.toString());
        } else if (value instanceof Map<?, ?> map) {
            out.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                writeString(out, String.valueOf(entry.getKey()));
                out.append(':');
                writeValue(out, entry.getValue());
            }
            out.append('}');
        } else if (value instanceof Iterable<?> liste) {
            out.append('[');
            boolean first = true;
            for (Object element : liste) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                writeValue(out, element);
            }
            out.append(']');
        } else if (value instanceof Object[] array) {
            writeValue(out, List.of(array));
        } else {
            throw new IllegalArgumentException("JSON kann keinen Wert vom Typ "
                    + value.getClass().getName() + " schreiben");
        }
    }

    private static void writeString(StringBuilder out, String s) {
        pruefeUnicode(s);
        out.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }

    /** Strengere Interoperabilitätsregel als die JSON-ABNF (RFC 8259, 8.2). */
    private static void pruefeUnicode(CharSequence value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (i + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(i + 1)))
                    throw new IllegalArgumentException("Ungepaartes UTF-16-Surrogate in JSON-Zeichenfolge");
                i++;
            } else if (Character.isLowSurrogate(c)) {
                throw new IllegalArgumentException("Ungepaartes UTF-16-Surrogate in JSON-Zeichenfolge");
            }
        }
    }

    // ------------------------------------------------------------------
    // Lesen
    // ------------------------------------------------------------------

    /** Parst ein JSON-Dokument: Objekt, Array oder Skalar. */
    public static Object parse(String text) {
        Parser parser = new Parser(text);
        Object result = parser.leseWert();
        parser.ueberspringeLeerraum();
        if (!parser.amEnde()) {
            throw parser.fehler("unerwartetes Zeichen nach dem JSON-Wert");
        }
        return result;
    }

    private static final class Parser {

        /** Verschachtelungstiefe-Limit: schützt den Stack vor tief verschachtelten Bomben. */
        private static final int MAX_TIEFE = 128;

        private final String text;
        private int pos;
        private int tiefe;

        Parser(String text) {
            this.text = text;
        }

        boolean amEnde() {
            return pos >= text.length();
        }

        IllegalArgumentException fehler(String meldung) {
            return new IllegalArgumentException("JSON-Formatfehler an Position " + pos + ": " + meldung);
        }

        void ueberspringeLeerraum() {
            while (pos < text.length()) {
                char c = text.charAt(pos);
                if (c != ' ' && c != '\t' && c != '\n' && c != '\r') break;
                pos++;
            }
        }

        char aktuell() {
            if (amEnde()) {
                throw fehler("unerwartetes Ende");
            }
            return text.charAt(pos);
        }

        void erwarte(char c) {
            if (amEnde() || text.charAt(pos) != c) {
                throw fehler("'" + c + "' erwartet");
            }
            pos++;
        }

        Object leseWert() {
            ueberspringeLeerraum();
            return switch (aktuell()) {
                case '{' -> leseObjekt();
                case '[' -> leseArray();
                case '"' -> leseString();
                case 't' -> leseWort("true", Boolean.TRUE);
                case 'f' -> leseWort("false", Boolean.FALSE);
                case 'n' -> leseWort("null", null);
                default -> leseZahl();
            };
        }

        private Object leseWort(String wort, Object wert) {
            if (!text.startsWith(wort, pos)) {
                throw fehler("'" + wort + "' erwartet");
            }
            pos += wort.length();
            return wert;
        }

        private Map<String, Object> leseObjekt() {
            erwarte('{');
            tiefeErhoehen();
            Map<String, Object> objekt = new java.util.LinkedHashMap<>();
            ueberspringeLeerraum();
            if (aktuell() == '}') {
                pos++;
                tiefe--;
                return objekt;
            }
            while (true) {
                ueberspringeLeerraum();
                String schluessel = leseString();
                ueberspringeLeerraum();
                erwarte(':');
                // RFC 8259 empfiehlt eindeutige Namen; für Fachanfragen darf
                // ein zweiter Wert denselben Schlüssel niemals still ersetzen.
                if (objekt.containsKey(schluessel)) throw fehler("doppelter Objektschlüssel");
                objekt.put(schluessel, leseWert());
                ueberspringeLeerraum();
                char c = aktuell();
                if (c == ',') {
                    pos++;
                } else if (c == '}') {
                    pos++;
                    tiefe--;
                    return objekt;
                } else {
                    throw fehler("',' oder '}' erwartet");
                }
            }
        }

        private List<Object> leseArray() {
            erwarte('[');
            tiefeErhoehen();
            List<Object> liste = new java.util.ArrayList<>();
            ueberspringeLeerraum();
            if (aktuell() == ']') {
                pos++;
                tiefe--;
                return liste;
            }
            while (true) {
                liste.add(leseWert());
                ueberspringeLeerraum();
                char c = aktuell();
                if (c == ',') {
                    pos++;
                } else if (c == ']') {
                    pos++;
                    tiefe--;
                    return liste;
                } else {
                    throw fehler("',' oder ']' erwartet");
                }
            }
        }

        private void tiefeErhoehen() {
            if (++tiefe > MAX_TIEFE) {
                throw fehler("JSON zu tief verschachtelt (maximal " + MAX_TIEFE + " Ebenen)");
            }
        }

        private String leseString() {
            erwarte('"');
            StringBuilder aus = new StringBuilder();
            while (true) {
                if (amEnde()) {
                    throw fehler("String nicht abgeschlossen");
                }
                char c = text.charAt(pos++);
                if (c == '"') {
                    try { pruefeUnicode(aus); }
                    catch (IllegalArgumentException invalid) { throw fehler(invalid.getMessage()); }
                    return aus.toString();
                }
                if (c == '\\') {
                    if (amEnde()) {
                        throw fehler("unvollständige Escape-Folge am String-Ende");
                    }
                    char esc = text.charAt(pos++);
                    switch (esc) {
                        case '"' -> aus.append('"');
                        case '\\' -> aus.append('\\');
                        case '/' -> aus.append('/');
                        case 'n' -> aus.append('\n');
                        case 'r' -> aus.append('\r');
                        case 't' -> aus.append('\t');
                        case 'b' -> aus.append('\b');
                        case 'f' -> aus.append('\f');
                        case 'u' -> {
                            if (pos + 4 > text.length()) {
                                throw fehler("unvollständige \\u-Escape-Folge");
                            }
                            int codeUnit = 0;
                            for (int i = 0; i < 4; i++) {
                                char hex = text.charAt(pos + i);
                                int ziffer = hex >= '0' && hex <= '9' ? hex - '0'
                                        : hex >= 'a' && hex <= 'f' ? hex - 'a' + 10
                                        : hex >= 'A' && hex <= 'F' ? hex - 'A' + 10 : -1;
                                if (ziffer < 0) {
                                    throw fehler("ungültige \\u-Escape-Folge");
                                }
                                codeUnit = codeUnit * 16 + ziffer;
                            }
                            aus.append((char) codeUnit);
                            pos += 4;
                        }
                        default -> throw fehler("unbekannte Escape-Folge '\\" + esc + "'");
                    }
                } else {
                    if (c < 0x20) throw fehler("rohes Steuerzeichen in String");
                    aus.append(c);
                }
            }
        }

        private BigDecimal leseZahl() {
            int start = pos;
            if (!amEnde() && (text.charAt(pos) == '-')) {
                pos++;
            }
            int ziffern = 0;
            if (amEnde() || !istZiffer(text.charAt(pos))) throw fehler("Zahl erwartet");
            if (text.charAt(pos) == '0') {
                pos++;
                ziffern++;
                if (!amEnde() && istZiffer(text.charAt(pos))) throw fehler("führende Null in Zahl");
            } else {
                while (!amEnde() && istZiffer(text.charAt(pos))) {
                    pos++;
                    ziffern++;
                }
            }
            if (!amEnde() && text.charAt(pos) == '.') {
                pos++;
                if (amEnde() || !istZiffer(text.charAt(pos))) throw fehler("Nachkommastelle erwartet");
                while (!amEnde() && istZiffer(text.charAt(pos))) {
                    pos++;
                    ziffern++;
                }
            }
            long exponent = 0;
            if (!amEnde() && (text.charAt(pos) == 'e' || text.charAt(pos) == 'E')) {
                pos++;
                boolean negativ = false;
                if (!amEnde() && (text.charAt(pos) == '+' || text.charAt(pos) == '-')) {
                    negativ = text.charAt(pos) == '-';
                    pos++;
                }
                if (amEnde() || !istZiffer(text.charAt(pos))) throw fehler("Exponentziffer erwartet");
                while (!amEnde() && istZiffer(text.charAt(pos))) {
                    exponent = Math.min(exponent * 10 + (text.charAt(pos) - '0'), 10_000L);
                    pos++;
                }
                if (negativ) {
                    exponent = -exponent;
                }
            }
            // Begrenzung gegen Exponenten-/Scale-Bomben: Riesen-Exponenten (z. B. 1e2000000000)
            // expandieren erst bei setScale/toPlainString zu Gigabyte-Strings (OutOfMemoryError,
            // der vom HTTP-Fehlerpfad nicht mehr gefangen wird). Geldbeträge brauchen weit
            // weniger Spielraum.
            if (ziffern > 64) {
                pos = start;
                throw fehler("Zahl mit mehr als 64 Ziffern abgewiesen");
            }
            if (Math.abs(exponent) > 999) {
                pos = start;
                throw fehler("Exponent außerhalb des erlaubten Bereichs ±999");
            }
            return new BigDecimal(text.substring(start, pos));
        }

        private static boolean istZiffer(char c) { return c >= '0' && c <= '9'; }
    }
}
