package de.allgemein.lmg.web;

import de.allgemein.lmg.service.FachlicheException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Anmeldeverwaltung: Kontendefinition, Anmeldung und Sitzungstoken. */
class BenutzerverwaltungTest {

    @Test
    void anmeldungLiefertTokenMitRolle() {
        var verwaltung = new Benutzerverwaltung("admin-01:geheim:admin;sb-01:wort:sachbearbeiter");

        String token = verwaltung.anmelden("sb-01", "wort");
        var benutzer = verwaltung.zuToken(token);
        assertTrue(benutzer.isPresent());
        assertEquals("sb-01", benutzer.get().kennung());
        assertEquals(Benutzer.ROLLE_SACHBEARBEITER, benutzer.get().rolle());
        assertFalse(benutzer.get().istAdmin());
    }

    @Test
    void falschesPasswortWirdFachlichAbgelehnt() {
        var verwaltung = new Benutzerverwaltung("admin-01:geheim:admin");
        assertThrows(FachlicheException.class, () -> verwaltung.anmelden("admin-01", "falsch"));
        assertThrows(FachlicheException.class, () -> verwaltung.anmelden("unbekannt", "geheim"));
    }

    @Test
    void abmeldenEntferntDieSitzung() {
        var verwaltung = new Benutzerverwaltung("admin-01:geheim:admin");
        String token = verwaltung.anmelden("admin-01", "geheim");
        assertTrue(verwaltung.zuToken(token).isPresent());
        verwaltung.abmelden(token);
        assertTrue(verwaltung.zuToken(token).isEmpty());
    }

    @Test
    void ungueltigeDefinitionWirdAbgelehnt() {
        assertThrows(IllegalArgumentException.class, () -> new Benutzerverwaltung("nur-zwei:felder"));
        assertThrows(IllegalArgumentException.class,
                () -> new Benutzerverwaltung("a:b:chefrolle"));
        assertThrows(IllegalArgumentException.class, () -> new Benutzerverwaltung("  "));
    }

    @Test
    void adminRolleWirdErkannt() {
        var verwaltung = new Benutzerverwaltung("admin-01:geheim:admin");
        var benutzer = verwaltung.zuToken(verwaltung.anmelden("admin-01", "geheim")).orElseThrow();
        assertTrue(benutzer.istAdmin());
    }

    @Test
    void ungueltigeKontendefinitionGibtWederPasswortNochKontensegmentPreis() {
        String geheimnis = "SYNTHETISCHES_PASSWORT_047";
        for (String fehlerhaft : java.util.List.of("konto:" + geheimnis,
                "konto:" + geheimnis + ":admin:zusatz", "konto:x:" + geheimnis,
                "konto:" + geheimnis + ":")) {
            var fehler = assertThrows(IllegalArgumentException.class,
                    () -> new Benutzerverwaltung("gueltig:x:admin;" + fehlerhaft));
            assertFalse(fehler.getMessage().contains(geheimnis), fehler.getMessage());
            assertFalse(fehler.toString().contains(fehlerhaft), fehler.toString());
            assertTrue(fehler.getMessage().contains("2"), "Eintragsposition bleibt diagnostizierbar");
            assertEquals(null, fehler.getCause());
        }
    }
}
