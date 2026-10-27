package de.allgemein.testsweet;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ApprovalIdentityTest {
    @Test void einWorkerHatZweiExpliziteFachidentitaetenAberKeinenAnmeldenachweis() {
        Simulator simulator = new Simulator();
        simulator.initialisiereFachKennungen(1);
        assertEquals(List.of("sb-sim-01", "sb-sim-02"), simulator.fachFreigabeKennungen());
        assertEquals("sb-sim-02", simulator.zufallsKollege("sb-sim-01"));
        assertEquals("sb-sim-01", simulator.zufallsKollege("sb-sim-02"));
        assertTrue(simulator.mitarbeiterListe().isEmpty(), "Vorbereitete Kennungen sind keine zusätzlichen Worker");
        assertTrue(simulator.vierAugenPruefumfang().contains("keine Anmeldung oder Rollenprüfung"));
        assertTrue(simulator.vierAugenPruefumfang().contains("kein Nachweis zweier natürlicher Personen"));
    }

    @Test void fehlendeOderUnbekannteFachidentitaetErzeugtKeinenErsatzkollegen() {
        Simulator simulator = new Simulator();
        assertThrows(IllegalStateException.class, () -> simulator.zufallsKollege("sb-sim-01"));
        simulator.initialisiereFachKennungen(3);
        assertThrows(IllegalStateException.class, () -> simulator.zufallsKollege("nicht-konfiguriert"));
        for (String actor : simulator.fachFreigabeKennungen()) {
            String colleague = simulator.zufallsKollege(actor);
            assertNotEquals(actor, colleague);
            assertTrue(simulator.fachFreigabeKennungen().contains(colleague));
        }
    }

    @Test void webfreigabeVerlangtVorbereiteteKontenStattEinerFestKodiertenKennung() {
        Simulator simulator = new Simulator();
        assertThrows(IllegalStateException.class, () -> simulator.zufallsWebKollege("sb-01"));
        simulator.initialisiereWebKonten(1);
        assertThrows(IllegalStateException.class, () -> simulator.zufallsWebKollege("sb-01"));
        assertEquals("sb-web-02", simulator.zufallsWebKollege("sb-web-01"));
    }
}
