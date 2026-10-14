package de.allgemein.lmg.web;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Rundreise- und Robustheitstests für die schlanke JSON-Unterstützung. */
class JsonTest {

    @Test
    void objektRundreiseErhaeltWerte() {
        Map<String, Object> original = new LinkedHashMap<>();
        original.put("name", "Meier, Anna");
        original.put("anzahl", 3);
        original.put("betrag", new BigDecimal("1198.71"));
        original.put("aktiv", true);
        original.put("leer", null);
        original.put("liste", List.of("a", "b"));

        Map<String, Object> geparst = (Map<String, Object>) Json.parse(Json.write(original));

        assertEquals("Meier, Anna", geparst.get("name"));
        assertEquals(new BigDecimal("3"), geparst.get("anzahl"));
        assertEquals(new BigDecimal("1198.71"), geparst.get("betrag"));
        assertEquals(Boolean.TRUE, geparst.get("aktiv"));
        assertEquals(null, geparst.get("leer"));
        assertEquals(List.of("a", "b"), geparst.get("liste"));
    }

    @Test
    void leereObjekteZaehlenNichtGegenDasTiefenlimit() {
        String viele = "[" + "{},".repeat(500) + "{}]";
        assertEquals(501, ((List<?>) Json.parse(viele)).size());
    }

    @Test
    void unicodeEscapeAkzeptiertNurHexziffern() {
        assertEquals("A", Json.parse("\"\\u0041\""));
        assertThrows(IllegalArgumentException.class, () -> Json.parse("\"\\u+041\""));
        assertThrows(IllegalArgumentException.class, () -> Json.parse("\"\\u００４１\""));
        assertThrows(IllegalArgumentException.class, () -> Json.parse("\"\\u٠٠٤١\""));
    }

    @Test
    void zahlengrammatikAkzeptiertNurAsciiUndVollstaendigeBestandteile() {
        for (String number : List.of("01", "-01", "00", ".5", "-.5", "1.", "1.e2", "+1", "-", "1e", "1e+", "1e-", "1e.2", "٠", "۱", "２", "1.١", "1e١", "NaN", "Infinity", "0x10")) {
            assertThrows(IllegalArgumentException.class, () -> Json.parse(number), number);
            assertThrows(IllegalArgumentException.class, () -> Json.parse("[" + number + "]"), number);
        }
        for (String number : List.of("0", "-0", "-0.00", "0e0", "1.0", "1E+2", "-1.25e-2", "123.456e007"))
            assertEquals(new BigDecimal(number), Json.parse(number), number);
    }

    @Test
    void nurDieVierJsonLeerraumzeichenWerdenUebersprungen() {
        assertEquals(new BigDecimal("1"), Json.parse(" \t\r\n1\n\r\t "));
        for (char whitespace : new char[]{'\f', '\u000b', '\u001c', '\u0085', '\u00a0', '\u1680', '\u2000', '\u2028', '\u2029', '\ufeff'}) {
            assertThrows(IllegalArgumentException.class, () -> Json.parse(whitespace + "1"));
            assertThrows(IllegalArgumentException.class, () -> Json.parse("1" + whitespace));
        }
        // Innerhalb eines Strings sind diese Unicode-Zeichen reguläre Nutzdaten.
        assertEquals("\u2028\u2029", Json.parse("\"\u2028\u2029\""));
    }

    @Test
    void roheSteuerzeichenWerdenAbgewiesenEscapedNulUndAlleKontrollenBleibenErlaubt() {
        for (char control = 0; control < 0x20; control++) {
            String raw = "a" + control + "b";
            assertThrows(IllegalArgumentException.class, () -> Json.parse("\"" + raw + "\""));
            assertThrows(IllegalArgumentException.class, () -> Json.parse("{\"" + raw + "\":1}"));
            assertEquals(String.valueOf(control), Json.parse("\"" + String.format("\\u%04x", (int) control) + "\""));
            assertEquals(raw, Json.parse(Json.write(raw)));
        }
        assertEquals("\0", Json.parse("\"\\u0000\""), "Escaped NUL ist gemäß RFC 8259 zulässig");
    }

    @Test
    void doppelteDekodierteSchluesselWerdenAuchNachNullNichtUeberschrieben() {
        for (String object : List.of("{\"betrag\":1,\"betrag\":2}", "{\"a\":null,\"a\":false}", "{\"a\":1,\"\\u0061\":2}", "{\"x\":{\"id\":1,\"id\":2}}"))
            assertThrows(IllegalArgumentException.class, () -> Json.parse(object), object);
        assertEquals(new BigDecimal("2"), ((Map<?, ?>) ((Map<?, ?>) Json.parse("{\"id\":1,\"x\":{\"id\":2}}")).get("x")).get("id"));
    }

    @Test
    void unicodePaareBleibenErhaltenUngepaarteSurrogatesWerdenAbgewiesen() {
        String music = new String(Character.toChars(0x1d11e));
        assertEquals(music, Json.parse("\"\\uD834\\uDD1E\""));
        assertEquals(music, Json.parse("\"" + music + "\""));
        assertEquals(music, Json.parse(Json.write(music)));
        for (String escaped : List.of("\\uD834", "\\uDD1E", "\\uD834x", "\\uD834\\u0041", "\\uDD1E\\uD834", "\\uD834\\uD834"))
            assertThrows(IllegalArgumentException.class, () -> Json.parse("\"" + escaped + "\""), escaped);
        for (String raw : List.of(String.valueOf((char) 0xd834), String.valueOf((char) 0xdd1e))) {
            assertThrows(IllegalArgumentException.class, () -> Json.parse("\"" + raw + "\""));
            assertThrows(IllegalArgumentException.class, () -> Json.write(raw));
        }
        assertThrows(IllegalArgumentException.class, () -> Json.parse("{\"\\uD834\":1}"));
    }

    @Test
    void sonderzeichenWerdenEscaped() {
        String text = Json.write(Map.of("text", "Zeile1\nZeile2 \"mit Anführungszeichen\" \\ und Backslash"));
        Map<String, Object> geparst = (Map<String, Object>) Json.parse(text);
        assertEquals("Zeile1\nZeile2 \"mit Anführungszeichen\" \\ und Backslash", geparst.get("text"));
    }

    @Test
    void steuerzeichenWerkenAlsUnicodeEscapesGeschrieben() {
        String text = Json.write(Map.of("wert", "a\u0001b"));
        assertEquals("{\"wert\":\"a\\u0001b\"}", text);
    }

    @Test
    void verschachtelteStrukturenWerdenGeparst() {
        Object ergebnis = Json.parse("{\"a\":{\"b\":[1,{\"c\":-2.5e2}]}}");
        Map<?, ?> a = assertInstanceOf(Map.class, ((Map<?, ?>) ergebnis).get("a"));
        List<?> b = assertInstanceOf(List.class, a.get("b"));
        Map<?, ?> inner = assertInstanceOf(Map.class, b.get(1));
        assertEquals(0, ((BigDecimal) inner.get("c")).compareTo(new BigDecimal("-250")));
    }

    @Test
    void fehlerhafteEingabeWirdAbgelehnt() {
        assertThrows(IllegalArgumentException.class, () -> Json.parse("{nicht: json}"));
        assertThrows(IllegalArgumentException.class, () -> Json.parse("{\"offen\": 1"));
        assertThrows(IllegalArgumentException.class, () -> Json.parse(""));
    }

    @Test
    void unbekannterTypBrichtMitHinweisAb() {
        assertThrows(IllegalArgumentException.class, () -> Json.write(new Object()));
    }
}
