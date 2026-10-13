package de.allgemein.testsweet;

import de.allgemein.lmg.service.SozialLeistungsService;

import java.util.Map;

/**
 * Ein benannter Arbeitsschritt eines Szenarios über die Fachschicht.
 *
 * <p>{@code funktion} erhält den Service, das Kürzel des simulierten Mitarbeiters,
 * ein Kollegen-Kürzel (für Vier-Augen-Freigaben: der Kollege „gibt frei") und einen
 * Kontext zum Merken von Zwischenergebnissen (bgId, zahlungId, Fall-Version …).
 * Der Rückgabestring ist eine freiwillige Detailzeile fürs Protokoll.</p>
 *
 * <p>Wirft eine Aktion eine {@link de.allgemein.lmg.service.FachlicheException} mit
 * dem in {@code erwarteterFehler} angegebenen Code, gilt der Schritt als erwartetes
 * Ergebnis (z. B. KONKURRENZ beim Speichern eines veralteten Maskenstands) und das
 * Szenario läuft weiter. Jeder andere Fehler bricht den Auftrag als FEHLGESCHLAGEN ab.</p>
 */
public record SzenarioAktion(String name, String erwarteterFehler, Aktion funktion) {

    /** Ein Negativschritt darf niemals durch eine normale Rückkehr bestehen. */
    public String execute(SozialLeistungsService service, String selbst, String kollege,
            Map<String, Object> kontext) throws Exception {
        String detail = funktion.ausfuehren(service, selbst, kollege, kontext);
        if (erwarteterFehler != null) {
            throw new IllegalStateException("Erwarteter Fachfehler " + erwarteterFehler
                    + " blieb aus: " + name);
        }
        return detail;
    }

    @FunctionalInterface
    public interface Aktion {
        String ausfuehren(SozialLeistungsService service, String selbst, String kollege,
                Map<String, Object> kontext) throws Exception;
    }

    /** Normaler Arbeitsschritt — Erfolg erwartet. */
    public static SzenarioAktion schritt(String name, Aktion funktion) {
        return new SzenarioAktion(name, null, funktion);
    }

    /** Schritt, dessen Scheitern mit genau diesem Fachfehler-Code erwartet wird. */
    public static SzenarioAktion schrittMitErwartetemFehler(
            String name, String fehlerCode, Aktion funktion) {
        return new SzenarioAktion(name, fehlerCode, funktion);
    }
}
