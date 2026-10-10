package de.allgemein.lmg.web;

import de.allgemein.lmg.service.FachlicheException;

import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Anmeldeverwaltung: Benutzerkonten aus der Konfigurationskaskade
 * (System-Property {@code lmg.benutzer} &gt; Umgebungsvariable {@code LMG_BENUTZER}
 * &gt; {@code lmg.properties}; Format {@code kennung:passwort:rolle;kennung:passwort:rolle},
 * Rollen "admin" und "sachbearbeiter"), Standardkonten für die Demo ohne Angabe.
 *
 * <p>Anmeldung liefert ein Zufalls-Token; die Webschicht prüft damit jede Anfrage.
 * Sitzungen liegen im Speicher und laufen nach einem Idle-Timeout ab (konfigurierbar
 * über {@code lmg.session.timeout.minuten}, Standard 60). Fehlgeschlagene Anmeldeversuche
 * werden gedrosselt (nach 5 Fehlversuchen 30 Sekunden Sperre je Kennung).
 * Für den echten Betrieb gehört hier ein Verzeichnisdienst (LDAP/AD) hinter
 * HTTPS; bewusst nicht selbst gebaut (Vorbild: CICS-Security hat der ESM).</p>
 */
public final class Benutzerverwaltung {

    public static final String STANDARD_KONTEN = "admin-01:admin:admin;sb-01:sb-01:sachbearbeiter;sb-02:sb-02:sachbearbeiter";

    private static final Duration STANDARD_IDLE_TIMEOUT = Duration.ofMinutes(60);
    private static final int MAX_FEHLVERSUCHE = 5;
    private static final Duration SPERRDAUER = Duration.ofSeconds(30);

    private record Konto(String passwort, String rolle) {
    }

    private record Sitzung(Benutzer benutzer, Instant zuletztAktiv) {
    }

    private record Fehlversuche(int anzahl, Instant gesperrtBis) {
    }

    private final Map<String, Konto> konten = new LinkedHashMap<>();
    private final Map<String, Sitzung> sitzungen = new ConcurrentHashMap<>();
    private final Map<String, Fehlversuche> fehlversuche = new ConcurrentHashMap<>();
    private final Duration idleTimeout;

    /** Historischer Einstieg: nur Umgebungsvariable, Standard-Idle-Timeout. */
    public static Benutzerverwaltung ausUmgebung() {
        String roh = System.getenv("LMG_BENUTZER");
        return new Benutzerverwaltung(roh == null || roh.isBlank() ? STANDARD_KONTEN : roh);
    }

    public Benutzerverwaltung(String definition) {
        this(definition, STANDARD_IDLE_TIMEOUT);
    }

    public Benutzerverwaltung(String definition, Duration idleTimeout) {
        this.idleTimeout = idleTimeout == null || idleTimeout.isNegative() || idleTimeout.isZero()
                ? STANDARD_IDLE_TIMEOUT : idleTimeout;
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
        String bereinigt = kennung == null ? "" : kennung.trim();
        Fehlversuche bisher = fehlversuche.get(bereinigt);
        if (bisher != null && bisher.gesperrtBis() != null && Instant.now().isBefore(bisher.gesperrtBis())) {
            throw new FachlicheException(FachlicheException.VALIDATION,
                    "Zu viele fehlgeschlagene Anmeldeversuche — bitte kurz warten");
        }
        Konto konto = bereinigt.isEmpty() ? null : konten.get(bereinigt);
        if (konto == null || !passwortGleich(konto.passwort(), passwort)) {
            fehlversuche.merge(bereinigt, new Fehlversuche(1, null), (alt, neu) -> {
                int anzahl = alt.anzahl() + 1;
                return anzahl >= MAX_FEHLVERSUCHE
                        ? new Fehlversuche(0, Instant.now().plus(SPERRDAUER))
                        : new Fehlversuche(anzahl, null);
            });
            // Identische Meldung für unbekannte Kennung und falsches Passwort
            throw new FachlicheException(FachlicheException.VALIDATION, "Kennung oder Passwort ist falsch");
        }
        fehlversuche.remove(bereinigt);
        String token = UUID.randomUUID().toString();
        sitzungen.put(token, new Sitzung(new Benutzer(bereinigt, konto.rolle()), Instant.now()));
        return token;
    }

    /** Constant-time-Vergleich, damit die Antwortzeit nicht verrät, wo das erste falsche Zeichen steht. */
    private static boolean passwortGleich(String erwartet, String gegeben) {
        byte[] a = (erwartet == null ? "" : erwartet).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] b = (gegeben == null ? "" : gegeben).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return MessageDigest.isEqual(a, b);
    }

    /** Liefert die Sitzung zum Token und verlängert sie; abgelaufene Sitzungen enden. */
    public Optional<Benutzer> zuToken(String token) {
        if (token == null) {
            return Optional.empty();
        }
        Sitzung sitzung = sitzungen.get(token);
        if (sitzung == null) {
            return Optional.empty();
        }
        if (Duration.between(sitzung.zuletztAktiv(), Instant.now()).compareTo(idleTimeout) > 0) {
            sitzungen.remove(token);
            return Optional.empty();
        }
        sitzungen.put(token, new Sitzung(sitzung.benutzer(), Instant.now()));
        return Optional.of(sitzung.benutzer());
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
