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
        int verifiziert = 0;
        try (Connection c = DriverManager.getConnection(url, user, pass)) {
            c.setAutoCommit(true);
            for (Auftrag auftrag : auftraege) {
                if (auftrag.status != Auftrag.Status.FERTIG_GESPIELT
                        && auftrag.status != Auftrag.Status.VERIFIKATION_FEHLGESCHLAGEN
                        && auftrag.status != Auftrag.Status.VERIFIZIERT) {
                    continue; // FEHLGESCHLAGEN oder abgebrochen: keine Aussagekraft
                }
                boolean alleOk = true;
                for (Erwartung erwartung : auftrag.erwartungen) {
                    Auftrag.Pruefung pruefung = pruefe(c, auftrag, erwartung);
                    auftrag.ergaenzePruefung(pruefung);
                    log.pruefung(auftrag, pruefung);
                    alleOk &= pruefung.ok();
                }
                auftrag.status = alleOk
                        ? Auftrag.Status.VERIFIZIERT
                        : Auftrag.Status.VERIFIKATION_FEHLGESCHLAGEN;
                if (alleOk) {
                    verifiziert++;
                }
            }
        }
        return verifiziert;
    }

    private Auftrag.Pruefung pruefe(Connection c, Auftrag auftrag, Erwartung erwartung) {
        Map<String, Object> ctx = auftrag.kontext;
        try (PreparedStatement stmt = c.prepareStatement(erwartung.sql())) {
            List<Object> parameter = erwartung.parameter().apply(ctx);
            for (int i = 0; i < parameter.size(); i++) {
                stmt.setObject(i + 1, parameter.get(i));
            }
            try (ResultSet rs = stmt.executeQuery()) {
                String ist = rs.next() ? stringVon(rs.getObject(1)) : "<keine Zeile>";
                if (ist == null) {
                    // DB-NULL ist eine Aussage: Prüfung sauber als false werten
                    // (mit deutlichem Kennzeichen) statt in einer NPE zu enden.
                    return new Auftrag.Pruefung(erwartung.beschreibung(),
                            erwartung.erwartet(), "<NULL>", false,
                            "Datenbank-NULL statt eines Werts");
                }
                return new Auftrag.Pruefung(erwartung.beschreibung(),
                        erwartung.erwartet(), ist,
                        vergleiche(erwartung, ist), null);
            }
        } catch (Exception e) {
            Auftrag.Pruefung fehlgeschlagen = new Auftrag.Pruefung(erwartung.beschreibung(),
                    erwartung.erwartet(), "<SQL-Fehler>",
                    false, e.getClass().getSimpleName() + ": " + e.getMessage());
            return fehlgeschlagen;
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
