package de.allgemein.lmg.web;

import de.allgemein.lmg.service.FachlicheException;

import java.security.MessageDigest;
import java.time.Clock;
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
    /** Deckelung der Fehlversuchs-Map gegen Speicher-DoS über Phantom-Kennungen (CR-026). */
    private static final int MAX_FEHLVERSUCH_EINTRAEGE = 1024;
    private static final int MAX_KENNUNG_LAENGE = 64;
    /** Gültigkeit des Einmal-Tokens für den Bescheid-Druck (CR-027). */
    private static final Duration DRUCK_TOKEN_GUELTIG = Duration.ofSeconds(60);

    private record Konto(String passwort, String rolle) {
    }

    private record Sitzung(Benutzer benutzer, Instant zuletztAktiv) {
    }

    private record Fehlversuche(int anzahl, Instant gesperrtBis) {
    }

    private record DruckToken(String sitzungsToken, long bgId, Instant faelltAb) {
    }

    private final Map<String, Konto> konten = new LinkedHashMap<>();
    private final Map<String, Sitzung> sitzungen = new ConcurrentHashMap<>();
    /** Phantomkennungen sind gedeckelt; echte Konten haben davon unabhängige Zähler. */
    private final Map<String, Fehlversuche> fehlversuche = new ConcurrentHashMap<>();
    private final Map<String, Fehlversuche> kontoFehlversuche = new ConcurrentHashMap<>();
    private final Map<String, DruckToken> druckTokens = new ConcurrentHashMap<>();
    private final Duration idleTimeout;

    /** Uhr für Timeout-Berechnungen — injizierbar für Tests (Standard: Systemuhr). */
    private volatile Clock uhr = Clock.systemDefaultZone();

    /** Erlaubt Tests, die Zeit zu verstellen (Drosselung/Idle-Timeout ohne echte Wartezeiten). */
    void setzeUhrFuerTests(Clock uhr) {
        this.uhr = uhr == null ? Clock.systemDefaultZone() : uhr;
    }

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
        if (definition == null) {
            throw new IllegalArgumentException("Benutzerkonten müssen konfiguriert sein");
        }
        int eintrag = 0;
        for (String teil : definition.split(";")) {
            eintrag++;
            String[] felder = teil.trim().split(":", -1);
            if (felder.length != 3 || felder[0].isBlank() || felder[1].isBlank()) {
                throw new IllegalArgumentException(
                        "Ungültiger Benutzerkonteneintrag " + eintrag
                                + ": erwartet wird 'kennung:passwort:rolle'");
            }
            String rolle = felder[2].trim();
            if (!Benutzer.ROLLE_ADMIN.equals(rolle) && !Benutzer.ROLLE_SACHBEARBEITER.equals(rolle)) {
                // Auch das Rollenfeld kann bei einem verschobenen Trennzeichen ein Passwort enthalten.
                throw new IllegalArgumentException("Ungültige Rolle im Benutzerkonteneintrag " + eintrag
                        + " (erlaubt: admin, sachbearbeiter)");
            }
            konten.put(felder[0].trim(), new Konto(felder[1], rolle));
        }
        if (konten.isEmpty()) {
            throw new IllegalArgumentException("Mindestens ein Benutzerkonto wird benötigt");
        }
    }

    /** Prüft Kennung und Passwort; liefert ein Sitzungs-Token oder bricht fachlich ab. */
    public synchronized String anmelden(String kennung, String passwort) {
        String bereinigt = kennung == null ? "" : kennung.trim();
        // Schlüssel der Drosselung begrenzen: beliebig lange Eingaben dürfen den Speicher nicht füllen
        String schluessel = bereinigt.length() > MAX_KENNUNG_LAENGE
                ? bereinigt.substring(0, MAX_KENNUNG_LAENGE) : bereinigt;
        Konto konto = bereinigt.isEmpty() ? null : konten.get(bereinigt);
        Map<String, Fehlversuche> zaehler = konto == null ? fehlversuche : kontoFehlversuche;
        // Konfigurierte Konten nicht auf einen Phantomschlüssel verkürzen.
        if (konto != null) schluessel = bereinigt;
        Fehlversuche bisher = zaehler.get(schluessel);
        if (bisher != null && bisher.gesperrtBis() != null && uhr.instant().isBefore(bisher.gesperrtBis())) {
            throw new FachlicheException(FachlicheException.VALIDATION,
                    "Zu viele fehlgeschlagene Anmeldeversuche — bitte kurz warten");
        }
        if (konto == null || !passwortGleich(konto.passwort(), passwort)) {
            registriereFehlversuch(schluessel, zaehler);
            // Identische Meldung für unbekannte Kennung und falsches Passwort
            throw new FachlicheException(FachlicheException.VALIDATION, "Kennung oder Passwort ist falsch");
        }
        zaehler.remove(schluessel);
        raeumeSitzungenAuf();
        String token = UUID.randomUUID().toString();
        sitzungen.put(token, new Sitzung(new Benutzer(bereinigt, konto.rolle()), uhr.instant()));
        return token;
    }

    /** Aufruf unter dem Anmeldemonitor: Deckel und Zähler gelten auch bei parallelen Requests. */
    private void registriereFehlversuch(String schluessel, Map<String, Fehlversuche> zaehler) {
        if (zaehler == fehlversuche && !fehlversuche.containsKey(schluessel)
                && fehlversuche.size() >= MAX_FEHLVERSUCH_EINTRAEGE) {
            raumeFehlversucheAuf();
            if (fehlversuche.size() >= MAX_FEHLVERSUCH_EINTRAEGE) {
                return; // nur unbekannte Kennungen; echte Konten werden separat immer gezählt
            }
        }
        zaehler.compute(schluessel, (k, alt) -> {
            Instant jetzt = uhr.instant();
            int anzahl = alt == null ? 1 : alt.anzahl() + 1;
            if (anzahl >= MAX_FEHLVERSUCHE) {
                return new Fehlversuche(0, jetzt.plus(SPERRDAUER));
            }
            return new Fehlversuche(anzahl, alt == null ? null : alt.gesperrtBis());
        });
    }

    /** Entfernt Zähler ohne aktive Sperre und abgelaufene Sperren — nie eine laufende Sperre. */
    private void raumeFehlversucheAuf() {
        Instant jetzt = uhr.instant();
        fehlversuche.entrySet().removeIf(e -> e.getValue().gesperrtBis() == null
                || !jetzt.isBefore(e.getValue().gesperrtBis()));
    }

    /** Entfernt abgelaufene Sitzungen, damit die Map nicht unbegrenzt wächst. */
    private void raeumeSitzungenAuf() {
        Instant jetzt = uhr.instant();
        sitzungen.entrySet().removeIf(e ->
                Duration.between(e.getValue().zuletztAktiv(), jetzt).compareTo(idleTimeout) >= 0);
        druckTokens.entrySet().removeIf(e -> !sitzungen.containsKey(e.getValue().sitzungsToken())
                || !jetzt.isBefore(e.getValue().faelltAb()));
    }

    /** Constant-time-Vergleich, damit die Antwortzeit nicht verrät, wo das erste falsche Zeichen steht. */
    private static boolean passwortGleich(String erwartet, String gegeben) {
        byte[] a = (erwartet == null ? "" : erwartet).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] b = (gegeben == null ? "" : gegeben).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return MessageDigest.isEqual(a, b);
    }

    /** Liefert die Sitzung zum Token und verlängert sie; abgelaufene Sitzungen enden. */
    public synchronized Optional<Benutzer> zuToken(String token) {
        if (token == null) {
            return Optional.empty();
        }
        Sitzung sitzung = sitzungen.get(token);
        if (sitzung == null) {
            return Optional.empty();
        }
        if (Duration.between(sitzung.zuletztAktiv(), uhr.instant()).compareTo(idleTimeout) >= 0) {
            abmelden(token);
            return Optional.empty();
        }
        // computeIfPresent: eine parallel abgemeldete Sitzung wird nicht wiederbelebt
        Sitzung verlaengert = sitzungen.computeIfPresent(token,
                (t, alt) -> new Sitzung(alt.benutzer(), uhr.instant()));
        return verlaengert == null ? Optional.empty() : Optional.of(verlaengert.benutzer());
    }

    /**
     * Einmal-Token für den Bescheid-Druck (CR-027): window.open kann keinen
     * Authorization-Header setzen; statt des Sitzungs-Tokens in der URL erhält der
     * Druck-Link ein Kurzzeit-Token, das sich beim ersten Abruf verbraucht.
     */
    public synchronized String erzeugeDruckToken(String sitzungsToken, long bgId) {
        if (bgId <= 0) throw new IllegalArgumentException("bgId muss positiv sein");
        raeumeSitzungenAuf();
        if (zuToken(sitzungsToken).isEmpty()) {
            throw new FachlicheException(FachlicheException.BERECHTIGUNG,
                    "Drucktoken erfordert eine aktive Sitzung");
        }
        String token = UUID.randomUUID().toString();
        druckTokens.put(token, new DruckToken(sitzungsToken, bgId,
                uhr.instant().plus(DRUCK_TOKEN_GUELTIG)));
        return token;
    }

    /** Einmaliger Abruf nur für die Ziel-BG und eine weiterhin aktive Herkunftssitzung. */
    public synchronized Optional<Benutzer> verbraucheDruckToken(String token, long bgId) {
        if (token == null || token.isBlank()) return Optional.empty();
        DruckToken druck = druckTokens.remove(token);
        if (druck == null || druck.bgId() != bgId || !uhr.instant().isBefore(druck.faelltAb())) {
            return Optional.empty();
        }
        return zuToken(druck.sitzungsToken());
    }

    public synchronized void abmelden(String token) {
        if (token != null) {
            sitzungen.remove(token);
            druckTokens.entrySet().removeIf(e -> token.equals(e.getValue().sitzungsToken()));
        }
    }

    public int anzahlKonten() {
        return konten.size();
    }
}
