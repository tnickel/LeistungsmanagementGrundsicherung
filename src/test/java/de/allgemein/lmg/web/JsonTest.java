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
