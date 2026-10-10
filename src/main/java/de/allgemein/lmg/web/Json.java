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
            while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
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
                return objekt;
            }
            while (true) {
                ueberspringeLeerraum();
                String schluessel = leseString();
                ueberspringeLeerraum();
                erwarte(':');
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
                            aus.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
                            pos += 4;
                        }
                        default -> throw fehler("unbekannte Escape-Folge '\\" + esc + "'");
                    }
                } else {
                    aus.append(c);
                }
            }
        }

        private BigDecimal leseZahl() {
            int start = pos;
            if (!amEnde() && (text.charAt(pos) == '-')) {
                pos++;
            }
            boolean ziffernGefunden = false;
            while (!amEnde() && Character.isDigit(text.charAt(pos))) {
                pos++;
                ziffernGefunden = true;
            }
            if (!amEnde() && text.charAt(pos) == '.') {
                pos++;
                while (!amEnde() && Character.isDigit(text.charAt(pos))) {
                    pos++;
                    ziffernGefunden = true;
                }
            }
            if (!amEnde() && (text.charAt(pos) == 'e' || text.charAt(pos) == 'E')) {
                pos++;
                if (!amEnde() && (text.charAt(pos) == '+' || text.charAt(pos) == '-')) {
                    pos++;
                }
                while (!amEnde() && Character.isDigit(text.charAt(pos))) {
                    pos++;
                }
            }
            if (!ziffernGefunden) {
                pos = start;
                throw fehler("Zahl erwartet");
            }
            return new BigDecimal(text.substring(start, pos));
        }
    }
}
