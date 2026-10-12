package de.allgemein.lmg.web;

import de.allgemein.lmg.service.FachlicheException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Zeitabhängiges Verhalten der Benutzerverwaltung mit stellbarer Uhr
 * (Codereview CR-026): Drosselung nach 5 Fehlversuchen, Freigabe nach Ablauf
 * der Sperrdauer und die Einmal-Druck-Token (CR-027).
 */
class BenutzerverwaltungZeitTest {

    private static final class BeweglicheUhr extends Clock {
        private Instant jetzt = Instant.parse("2026-10-12T10:00:00Z");

        void plus(Duration dauer) {
            jetzt = jetzt.plus(dauer);
        }

        @Override
        public Instant instant() {
            return jetzt;
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }

    private Benutzerverwaltung verwaltungMitUhr(BeweglicheUhr uhr) {
        Benutzerverwaltung verwaltung = new Benutzerverwaltung(
                "admin-01:admin:admin;sb-01:sb-01:sachbearbeiter", Duration.ofMinutes(60));
        verwaltung.setzeUhrFuerTests(uhr);
        return verwaltung;
    }

    @Test
    void fuenfFehlversucheSperrenDieKennung() {
        BeweglicheUhr uhr = new BeweglicheUhr();
        Benutzerverwaltung verwaltung = verwaltungMitUhr(uhr);
        for (int i = 0; i < 5; i++) {
            assertThrows(FachlicheException.class, () -> verwaltung.anmelden("sb-01", "falsch"));
        }
        // Sechster Versuch trifft auf die Sperre (andere Meldung als „falsch“)
        FachlicheException gesperrt = assertThrows(FachlicheException.class,
                () -> verwaltung.anmelden("sb-01", "sb-01"));
        assertTrue(gesperrt.getMessage().contains("Zu viele fehlgeschlagene"));
        // Andere Kennung bleibt unberührt
        assertTrue(verwaltung.anmelden("admin-01", "admin") != null);
    }

    @Test
    void sperreEndetNachAblaufDerSperrdauer() {
        BeweglicheUhr uhr = new BeweglicheUhr();
        Benutzerverwaltung verwaltung = verwaltungMitUhr(uhr);
        for (int i = 0; i < 5; i++) {
            assertThrows(FachlicheException.class, () -> verwaltung.anmelden("sb-01", "falsch"));
        }
        uhr.plus(Duration.ofSeconds(31));
        String token = verwaltung.anmelden("sb-01", "sb-01");
        assertTrue(verwaltung.zuToken(token).isPresent());
    }

    @Test
    void sitzungLaueftNachIdleTimeoutAb() {
        BeweglicheUhr uhr = new BeweglicheUhr();
        Benutzerverwaltung verwaltung = verwaltungMitUhr(uhr);
        String token = verwaltung.anmelden("sb-01", "sb-01");
        uhr.plus(Duration.ofMinutes(59));
        assertTrue(verwaltung.zuToken(token).isPresent(), "Sliding: within the timeout");
        uhr.plus(Duration.ofMinutes(61));
        assertFalse(verwaltung.zuToken(token).isPresent(), "after the timeout, invalid");
    }

    @Test
    void druckTokenGiltGenauEinmal() {
        BeweglicheUhr uhr = new BeweglicheUhr();
        Benutzerverwaltung verwaltung = verwaltungMitUhr(uhr);
        String druck = verwaltung.erzeugeDruckToken(new Benutzer("sb-01", "sachbearbeiter"));
        assertTrue(verwaltung.verbraucheDruckToken(druck).isPresent());
        assertFalse(verwaltung.verbraucheDruckToken(druck).isPresent(), "One-time: second call empty");
    }

    @Test
    void druckTokenEndetNachSechzigSekunden() {
        BeweglicheUhr uhr = new BeweglicheUhr();
        Benutzerverwaltung verwaltung = verwaltungMitUhr(uhr);
        String druck = verwaltung.erzeugeDruckToken(new Benutzer("sb-01", "sachbearbeiter"));
        uhr.plus(Duration.ofSeconds(61));
        assertFalse(verwaltung.verbraucheDruckToken(druck).isPresent(), "abgelaufen");
    }

    @Test
    void abmeldenBeendetDieSitzung() {
        BeweglicheUhr uhr = new BeweglicheUhr();
        Benutzerverwaltung verwaltung = verwaltungMitUhr(uhr);
        String token = verwaltung.anmelden("sb-01", "sb-01");
        verwaltung.abmelden(token);
        assertFalse(verwaltung.zuToken(token).isPresent());
    }
}
