package de.allgemein.lmg.web;

import de.allgemein.lmg.service.FachlicheException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        String sitzung = verwaltung.anmelden("sb-01", "sb-01");
        String druck = verwaltung.erzeugeDruckToken(sitzung, 7);
        assertTrue(verwaltung.verbraucheDruckToken(druck, 7).isPresent());
        assertFalse(verwaltung.verbraucheDruckToken(druck, 7).isPresent(), "Einmaliger Abruf");
    }

    @Test
    void druckTokenEndetNachSechzigSekunden() {
        BeweglicheUhr uhr = new BeweglicheUhr();
        Benutzerverwaltung verwaltung = verwaltungMitUhr(uhr);
        String sitzung = verwaltung.anmelden("sb-01", "sb-01");
        String druck = verwaltung.erzeugeDruckToken(sitzung, 7);
        uhr.plus(Duration.ofSeconds(60));
        assertFalse(verwaltung.verbraucheDruckToken(druck, 7).isPresent(), "am Fristende abgelaufen");
    }

    @Test
    void aktiveSperreUeberlebtFlutMitPhantomKennungen() {
        BeweglicheUhr uhr = new BeweglicheUhr();
        Benutzerverwaltung verwaltung = verwaltungMitUhr(uhr);
        for (int i = 0; i < 5; i++) {
            assertThrows(FachlicheException.class, () -> verwaltung.anmelden("sb-01", "falsch"));
        }
        for (int i = 0; i < 3000; i++) {
            String name = "phantom-" + i;
            assertThrows(FachlicheException.class, () -> verwaltung.anmelden(name, "x"));
        }
        assertThrows(FachlicheException.class, () -> verwaltung.anmelden("x".repeat(100_000), "x"));
        FachlicheException gesperrt = assertThrows(FachlicheException.class,
                () -> verwaltung.anmelden("sb-01", "sb-01"));
        assertTrue(gesperrt.getMessage().contains("Zu viele fehlgeschlagene"),
                "die Sperre darf durch Phantom-Kennungen nicht verdrängt werden");
    }

    @Test
    void vollePhantomSperrmapVerhindertKeineNeueSperreFuerEinEchtesKonto() throws Exception {
        BeweglicheUhr uhr = new BeweglicheUhr();
        Benutzerverwaltung verwaltung = verwaltungMitUhr(uhr);
        for (int i = 0; i < 1024; i++) {
            String kennung = "phantom-" + i;
            for (int j = 0; j < 5; j++) {
                assertThrows(FachlicheException.class, () -> verwaltung.anmelden(kennung, "x"));
            }
        }
        for (int i = 0; i < 5; i++) {
            assertThrows(FachlicheException.class, () -> verwaltung.anmelden("sb-01", "falsch"));
        }
        FachlicheException gesperrt = assertThrows(FachlicheException.class,
                () -> verwaltung.anmelden("sb-01", "sb-01"));
        assertTrue(gesperrt.getMessage().contains("Zu viele fehlgeschlagene"));
        assertTrue(verwaltung.anmelden("admin-01", "admin") != null);
        assertTrue(assertThrows(FachlicheException.class,
                () -> verwaltung.anmelden("phantom-0", "x")).getMessage().contains("Zu viele"),
                "bestehende aktive Phantomsperren bleiben erhalten");
        var feld = Benutzerverwaltung.class.getDeclaredField("fehlversuche");
        feld.setAccessible(true);
        assertEquals(1024, ((java.util.Map<?, ?>) feld.get(verwaltung)).size(),
                "der Phantom-Speicher bleibt begrenzt");
        var kontofeld = Benutzerverwaltung.class.getDeclaredField("kontoFehlversuche");
        kontofeld.setAccessible(true);
        assertEquals(1, ((java.util.Map<?, ?>) kontofeld.get(verwaltung)).size(),
                "echte Zähler wachsen höchstens mit den konfigurierten Konten");
        uhr.plus(Duration.ofSeconds(30));
        assertTrue(verwaltung.anmelden("sb-01", "sb-01") != null, "Sperre endet regulär");
    }

    @Test
    void druckTokenIstAnZielBgGebundenUndFehlversuchVerbrauchtEs() {
        Benutzerverwaltung verwaltung = verwaltungMitUhr(new BeweglicheUhr());
        String sitzung = verwaltung.anmelden("sb-01", "sb-01");
        String druck = verwaltung.erzeugeDruckToken(sitzung, 7);
        assertFalse(verwaltung.verbraucheDruckToken(druck, 8).isPresent());
        assertFalse(verwaltung.verbraucheDruckToken(druck, 7).isPresent());
    }

    @Test
    void logoutWiderruftNurDruckTokenDerHerkunftssitzung() {
        Benutzerverwaltung verwaltung = verwaltungMitUhr(new BeweglicheUhr());
        String ersteSitzung = verwaltung.anmelden("sb-01", "sb-01");
        String zweiteSitzung = verwaltung.anmelden("sb-01", "sb-01");
        String ersterDruck = verwaltung.erzeugeDruckToken(ersteSitzung, 7);
        String zweiterDruck = verwaltung.erzeugeDruckToken(zweiteSitzung, 7);
        verwaltung.abmelden(ersteSitzung);
        verwaltung.anmelden("sb-01", "sb-01");
        assertFalse(verwaltung.verbraucheDruckToken(ersterDruck, 7).isPresent(),
                "erneute Anmeldung darf ein widerrufenes Token nicht reaktivieren");
        assertTrue(verwaltung.verbraucheDruckToken(zweiterDruck, 7).isPresent());
    }

    @Test
    void druckTokenPrueftIdleAuchVorAblaufSeinerEigenenFrist() {
        BeweglicheUhr uhr = new BeweglicheUhr();
        Benutzerverwaltung verwaltung = new Benutzerverwaltung("sb-01:sb-01:sachbearbeiter",
                Duration.ofSeconds(30));
        verwaltung.setzeUhrFuerTests(uhr);
        String sitzung = verwaltung.anmelden("sb-01", "sb-01");
        String druck = verwaltung.erzeugeDruckToken(sitzung, 7);
        uhr.plus(Duration.ofSeconds(30));
        assertFalse(verwaltung.verbraucheDruckToken(druck, 7).isPresent(),
                "Sitzung ist nach 30s idle, obwohl die Drucktokenfrist 60s beträgt");
        assertFalse(verwaltung.zuToken(sitzung).isPresent());
    }

    @Test
    void druckTokenErfordertAktiveSitzungUndPositiveBgId() {
        Benutzerverwaltung verwaltung = verwaltungMitUhr(new BeweglicheUhr());
        assertThrows(FachlicheException.class, () -> verwaltung.erzeugeDruckToken("unbekannt", 7));
        String sitzung = verwaltung.anmelden("sb-01", "sb-01");
        assertThrows(IllegalArgumentException.class, () -> verwaltung.erzeugeDruckToken(sitzung, 0));
        verwaltung.abmelden(sitzung);
        assertThrows(FachlicheException.class, () -> verwaltung.erzeugeDruckToken(sitzung, 7));
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
