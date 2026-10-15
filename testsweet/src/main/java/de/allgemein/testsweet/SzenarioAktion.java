package de.allgemein.testsweet;

import de.allgemein.lmg.service.LeistungsService;
import de.allgemein.lmg.service.SozialLeistungsService;
import de.allgemein.lmg.service.FachlicheException;

import java.math.BigDecimal;
import java.util.Map;
import java.util.List;

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
public record SzenarioAktion(String name, String erwarteterFehler, Aktion funktion,
        List<String> erwarteteFehlergruende, List<Erwartung> zwischenpruefungen) {

    public SzenarioAktion(String name, String erwarteterFehler, Aktion funktion, List<String> erwarteteFehlergruende) {
        this(name, erwarteterFehler, funktion, erwarteteFehlergruende, List.of());
    }

    public SzenarioAktion(String name, String erwarteterFehler, Aktion funktion) {
        this(name, erwarteterFehler, funktion, List.of());
    }

    public SzenarioAktion {
        erwarteteFehlergruende = List.copyOf(erwarteteFehlergruende);
        zwischenpruefungen = List.copyOf(zwischenpruefungen);
        if (erwarteteFehlergruende.stream().anyMatch(String::isBlank))
            throw new IllegalArgumentException("Leere Fehlergrund-Fragmente sind keine Abwehrprüfung");
        if (erwarteterFehler != null && !zwischenpruefungen.isEmpty())
            throw new IllegalArgumentException("Zwischenstandsprüfung ist kein erwarteter Fachfehler");
    }

    public boolean akzeptiert(FachlicheException fehler) {
        return erwarteterFehler != null && erwarteterFehler.equals(fehler.code())
                && (erwarteteFehlergruende.isEmpty() || fehler.getMessage() != null
                && erwarteteFehlergruende.stream().anyMatch(fehler.getMessage()::contains));
    }

    /** Ein Negativschritt darf niemals durch eine normale Rückkehr bestehen. */
    public String execute(SozialLeistungsService service, String selbst, String kollege,
            Map<String, Object> kontext) throws Exception {
        String detail;
        try {
            detail = funktion.ausfuehren(service, selbst, kollege, kontext);
        } catch (FachlicheException fehler) {
            if (erwarteterFehler != null && erwarteterFehler.equals(fehler.code()) && !akzeptiert(fehler))
                throw new IllegalStateException("Fachfehler " + fehler.code() + " hat den falschen Grund: "
                        + fehler.getMessage() + "; erwartet mindestens einer von " + erwarteteFehlergruende, fehler);
            throw fehler;
        }
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

    /**
     * Tatsächliche synthetische SEPA-Eingaben eines Anlageversuchs. Der gemeinsame
     * Delegat erlaubt einen Service-Probeaufruf derselben Aktion ohne Datenbank.
     */
    public record SepaAnlage(String aktenzeichen, String hauptperson, BigDecimal budget,
            String iban, String bic) implements Aktion {
        @Override public String ausfuehren(SozialLeistungsService service, String selbst, String kollege,
                Map<String, Object> kontext) {
            return ausfuehrenMit(service, selbst);
        }
        String ausfuehrenMit(LeistungsService service, String selbst) {
            service.legeBedarfsgemeinschaftAn(aktenzeichen, hauptperson, budget, null,
                    iban, bic, hauptperson, selbst);
            return "unerwartet erfolgreich — ungültige SEPA-Daten wären durchgerutscht";
        }
    }

    /** Normaler Arbeitsschritt — Erfolg erwartet. */
    public static SzenarioAktion schritt(String name, Aktion funktion) {
        return new SzenarioAktion(name, null, funktion);
    }

    /** Tatsächlicher SQL-Zwischenstand; Mitarbeiter prüft vor dem nächsten Fachschritt. */
    public static SzenarioAktion pruefung(String name, List<Erwartung> erwartungen) {
        if (erwartungen.isEmpty()) throw new IllegalArgumentException("Zwischenstandsprüfung ohne Erwartungen");
        return new SzenarioAktion(name, null, (s, ich, kollege, ctx) -> "SQL-Zwischenstand geprüft",
                List.of(), erwartungen);
    }

    /** Schritt, dessen Scheitern mit genau diesem Fachfehler-Code erwartet wird. */
    public static SzenarioAktion schrittMitErwartetemFehler(
            String name, String fehlerCode, Aktion funktion) {
        return new SzenarioAktion(name, fehlerCode, funktion);
    }

    /** Code und einer der ausdrücklich zulässigen Gründe müssen gemeinsam zutreffen. */
    public static SzenarioAktion schrittMitErwartetemFehler(
            String name, String fehlerCode, List<String> erlaubteGruende, Aktion funktion) {
        if (erlaubteGruende.isEmpty()) throw new IllegalArgumentException("Ein Negativszenario braucht einen erwarteten Fehlergrund");
        return new SzenarioAktion(name, fehlerCode, funktion, erlaubteGruende);
    }
}
