package de.allgemein.testsweet;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Eine erwartete Eigenschaft des Datenbank-Endzustands eines Auftrags — die
 * Erwartungshaltung steht damit im Szenario selbst (Wunsch des Konzepts).
 *
 * <p>Die SQL-Parameter werden erst bei der Verifikation aufgelöst, aus dem beim
 * Spielen gesammelten Kontext des Auftrags (bgId, zahlungId …), damit dieselbe
 * Erwartung mit den konkreten, erst zur Laufzeit vergebenen IDs arbeiten kann.</p>
 *
 * <p>Vergleichsarten: TEXT (exakt), BETRAG (scale-unabhängig, Oracle NUMBER ohne
 * Nachkomma-Nullen), GANZZAHL (exakt), GANZZAHL_MIN (mind.), TEXT_IN (eins aus
 * Alternativen, '|' getrennt — z. B. BEREIT|UEBERGEBEN, weil ein Kassenlauf-Auftrag
 * einer bereits freigegebenen Zahlung dazwischenfunken kann).</p>
 */
public record Erwartung(
        String beschreibung,
        String sql,
        Function<Map<String, Object>, List<Object>> parameter,
        Art art,
        String erwartet) {

    public enum Art { TEXT, BETRAG, GANZZAHL, GANZZAHL_MIN, TEXT_IN }

    public static Erwartung text(String beschreibung, String sql,
            Function<Map<String, Object>, List<Object>> parameter, String erwartet) {
        return new Erwartung(beschreibung, sql, parameter, Art.TEXT, erwartet);
    }

    public static Erwartung betrag(String beschreibung, String sql,
            Function<Map<String, Object>, List<Object>> parameter, String erwartet) {
        return new Erwartung(beschreibung, sql, parameter, Art.BETRAG, erwartet);
    }

    public static Erwartung ganzeZahl(String beschreibung, String sql,
            Function<Map<String, Object>, List<Object>> parameter, long erwartet) {
        return new Erwartung(beschreibung, sql, parameter, Art.GANZZAHL, String.valueOf(erwartet));
    }

    public static Erwartung ganzeZahlMindestens(String beschreibung, String sql,
            Function<Map<String, Object>, List<Object>> parameter, long minimum) {
        return new Erwartung(beschreibung, sql, parameter, Art.GANZZAHL_MIN, String.valueOf(minimum));
    }

    public static Erwartung einsVon(String beschreibung, String sql,
            Function<Map<String, Object>, List<Object>> parameter, String alternativen) {
        return new Erwartung(beschreibung, sql, parameter, Art.TEXT_IN, alternativen);
    }
}
