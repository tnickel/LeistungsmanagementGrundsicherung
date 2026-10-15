package de.allgemein.testsweet;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;

/**
 * Prüft nach dem Lauf die Erwartungshaltung jedes Auftrags direkt per SQL gegen
 * die Oracle-Datenbank (dieselbe Sichtweise wie die Szenario-Regression der
 * Testsuite; bewusst NICHT über den Service, damit Persistierungsfehler sichtbar
 * werden). Ergebnis jeder Prüfung landet im Auftrag, im Log und in der GUI.
 */
final class Verifikator {

    private final String url;
    private final String user;
    private final String pass;

    Verifikator(String url, String user, String pass) {
        this.url = url;
        this.user = user;
        this.pass = pass;
    }

    /** Verifiziert alle Aufträge sequenziell; Rückgabe: Anzahl verifizierter (ok) Aufträge. */
    int verifiziereAlle(List<Auftrag> auftraege, SweetLog log) throws Exception {
        try (Connection c = DriverManager.getConnection(url, user, pass)) {
            return verifiziereAlle(c, auftraege, log);
        }
    }

    /** Wiederverwendet eine bereits verfügbare Poolverbindung, ohne sie physisch zu schließen. */
    int verifiziereAlle(Connection c, List<Auftrag> auftraege, SweetLog log) throws Exception {
        int verifiziert = 0;
        if (!c.getAutoCommit()) c.rollback();
        c.setAutoCommit(true);
            for (Auftrag auftrag : auftraege) {
                if (auftrag.status != Auftrag.Status.FERTIG_GESPIELT
                        && auftrag.status != Auftrag.Status.VERIFIKATION_FEHLGESCHLAGEN
                        && auftrag.status != Auftrag.Status.VERIFIZIERT) {
                    continue; // FEHLGESCHLAGEN oder abgebrochen: keine Aussagekraft
                }
                boolean alleOk = !auftrag.erwartungen.isEmpty() && auftrag.pruefungen().stream().allMatch(Auftrag.Pruefung::ok);
                for (Erwartung erwartung : auftrag.erwartungen) {
                    Auftrag.Pruefung pruefung = pruefe(c, auftrag, erwartung);
                    auftrag.ergaenzePruefung(pruefung);
                    log.pruefung(auftrag, pruefung);
                    alleOk &= pruefung.ok();
                }
                if (!auftrag.rechenbloecke.isEmpty()) {
                    Auftrag.Pruefung rechnung = pruefeRechnung(auftrag);
                    auftrag.ergaenzePruefung(rechnung);
                    log.pruefung(auftrag, rechnung);
                    alleOk &= rechnung.ok();
                }
                auftrag.status = alleOk
                        ? Auftrag.Status.VERIFIZIERT
                        : Auftrag.Status.VERIFIKATION_FEHLGESCHLAGEN;
                if (alleOk) {
                    verifiziert++;
                }
            }
        return verifiziert;
    }

    private Auftrag.Pruefung pruefe(Connection c, Auftrag auftrag, Erwartung erwartung) {
        return pruefe(c, auftrag.kontext, erwartung);
    }

    /** Verwendet den aktuellen Worker-Kontext auch vor Abschluss des gesamten Auftrags. */
    Auftrag.Pruefung pruefe(Connection c, Map<String, Object> ctx, Erwartung erwartung) {
        List<Object> parameter = List.of();
        try (PreparedStatement stmt = c.prepareStatement(erwartung.sql())) {
            parameter = erwartung.parameter().apply(ctx);
            for (int i = 0; i < parameter.size(); i++) {
                stmt.setObject(i + 1, parameter.get(i));
            }
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return new Auftrag.Pruefung(erwartung.beschreibung(), erwartung.erwartet(),
                            "<keine Zeile>", false, "Skalarprüfung verlangt exakt eine Ergebniszeile", erwartung.sql(), parameter);
                }
                String ist = stringVon(rs.getObject(1));
                if (rs.next()) {
                    return new Auftrag.Pruefung(erwartung.beschreibung(), erwartung.erwartet(),
                            "<mehrere Zeilen>", false, "Skalarprüfung verlangt exakt eine Ergebniszeile", erwartung.sql(), parameter);
                }
                if (ist == null) {
                    // DB-NULL ist eine Aussage: Prüfung sauber als false werten
                    // (mit deutlichem Kennzeichen) statt in einer NPE zu enden.
                    return new Auftrag.Pruefung(erwartung.beschreibung(),
                            erwartung.erwartet(), "<NULL>", false,
                            "Datenbank-NULL statt eines Werts", erwartung.sql(), parameter);
                }
                return new Auftrag.Pruefung(erwartung.beschreibung(),
                        erwartung.erwartet(), ist,
                        vergleiche(erwartung, ist), null, erwartung.sql(), parameter);
            }
        } catch (Exception e) {
            Auftrag.Pruefung fehlgeschlagen = new Auftrag.Pruefung(erwartung.beschreibung(),
                    erwartung.erwartet(), "<SQL-Fehler>",
                    false, e.getClass().getSimpleName() + ": " + e.getMessage(), erwartung.sql(), parameter);
            return fehlgeschlagen;
        }
    }

    static Auftrag.Pruefung pruefeRechnung(Auftrag auftrag) {
        try {
            List<String> errors = LlmCalculationReference.verifyBlocks(MdBericht.rechenbloecke(auftrag.rechenbloecke));
            return new Auftrag.Pruefung("Unabhängige Nachrechnung sämtlicher Rechenblöcke (Referenz 2026)",
                    "0 Abweichungen", errors.isEmpty() ? "0 Abweichungen" : errors.size() + " Abweichungen", errors.isEmpty(),
                    errors.isEmpty() ? auftrag.rechenbloecke.size() + " Rechenblöcke mit unabhängigen Konstanten und BigDecimal geprüft"
                            : String.join("; ", errors), "-- INDEPENDENT_CALCULATION_2026: BigDecimal-Zweitimplementierung; sämtliche Personen und Zwischenwerte", List.of());
        } catch (Exception e) {
            return new Auftrag.Pruefung("Unabhängige Nachrechnung sämtlicher Rechenblöcke (Referenz 2026)",
                    "0 Abweichungen", "<Referenzfehler>", false, e.getClass().getSimpleName() + ": " + e.getMessage(),
                    "-- INDEPENDENT_CALCULATION_2026: BigDecimal-Zweitimplementierung", List.of());
        }
    }

    boolean vergleiche(Erwartung erwartung, String ist) {
        return switch (erwartung.art()) {
            case TEXT -> erwartung.erwartet().equals(ist);
            case BETRAG -> {
                try {
                    yield new BigDecimal(erwartung.erwartet()).compareTo(new BigDecimal(ist)) == 0;
                } catch (NumberFormatException e) {
                    yield false;
                }
            }
            case GANZZAHL -> {
                try {
                    yield new BigDecimal(erwartung.erwartet()).toBigIntegerExact()
                            .equals(new BigDecimal(ist).toBigIntegerExact());
                } catch (NumberFormatException | ArithmeticException e) {
                    yield false;
                }
            }
            case TEXT_IN -> {
                boolean treffer = false;
                for (String alternative : erwartung.erwartet().split("\\|")) {
                    treffer |= alternative.equals(ist);
                }
                yield treffer;
            }
        };
    }

    private static String stringVon(Object wert) {
        return wert == null ? null : String.valueOf(wert);
    }
}
