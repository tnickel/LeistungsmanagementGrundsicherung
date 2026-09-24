package de.allgemein.lmg.web;

import de.allgemein.lmg.service.FachlicheException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Anmeldeverwaltung für den Demo-Betrieb: Benutzerkonten aus der Umgebungsvariablen
 * LMG_BENUTZER (Format {@code kennung:passwort:rolle;kennung:passwort:rolle},
 * Rollen "admin" und "sachbearbeiter"), Standardkonten für die Demo ohne Variable.
 *
 * <p>Anmeldung liefert ein Zufalls-Token; die Webschicht prüft damit jede Anfrage.
 * Sitzungen liegen im Speicher — nach einem Server-Neustart wird neu angemeldet.
 * Für den echten Betrieb gehört hier ein Verzeichnisdienst (LDAP/AD) hinter
 * HTTPS; bewusst nicht selbst gebaut (Vorbild: CICS-Security hat der ESM).</p>
 */
public final class Benutzerverwaltung {

    public static final String STANDARD_KONTEN = "admin-01:admin:admin;sb-01:sb-01:sachbearbeiter;sb-02:sb-02:sachbearbeiter";

    private record Konto(String passwort, String rolle) {
    }

    private final Map<String, Konto> konten = new LinkedHashMap<>();
    private final Map<String, Benutzer> sitzungen = new ConcurrentHashMap<>();

    public static Benutzerverwaltung ausUmgebung() {
        String roh = System.getenv("LMG_BENUTZER");
        return new Benutzerverwaltung(roh == null || roh.isBlank() ? STANDARD_KONTEN : roh);
    }

    public Benutzerverwaltung(String definition) {
        for (String teil : definition.split(";")) {
            String[] felder = teil.trim().split(":");
            if (felder.length != 3 || felder[0].isBlank() || felder[1].isBlank()) {
                throw new IllegalArgumentException(
                        "LMG_BENUTZER erwartet 'kennung:passwort:rolle' je Eintrag, erhalten: '" + teil + "'");
            }
            String rolle = felder[2].trim();
            if (!Benutzer.ROLLE_ADMIN.equals(rolle) && !Benutzer.ROLLE_SACHBEARBEITER.equals(rolle)) {
                throw new IllegalArgumentException("Unbekannte Rolle '" + rolle + "' (erlaubt: admin, sachbearbeiter)");
            }
            konten.put(felder[0].trim(), new Konto(felder[1], rolle));
        }
        if (konten.isEmpty()) {
            throw new IllegalArgumentException("Mindestens ein Benutzerkonto wird benötigt");
        }
    }

    /** Prüft Kennung und Passwort; liefert ein Sitzungs-Token oder bricht fachlich ab. */
    public String anmelden(String kennung, String passwort) {
        Konto konto = kennung == null ? null : konten.get(kennung.trim());
        if (konto == null || !konto.passwort().equals(passwort == null ? "" : passwort)) {
            throw new FachlicheException(FachlicheException.VALIDATION, "Kennung oder Passwort ist falsch");
        }
        String token = UUID.randomUUID().toString();
        sitzungen.put(token, new Benutzer(kennung.trim(), konto.rolle()));
        return token;
    }

    public Optional<Benutzer> zuToken(String token) {
        return token == null ? Optional.empty() : Optional.ofNullable(sitzungen.get(token));
    }

    public void abmelden(String token) {
        if (token != null) {
            sitzungen.remove(token);
        }
    }

    public int anzahlKonten() {
        return konten.size();
    }
}
