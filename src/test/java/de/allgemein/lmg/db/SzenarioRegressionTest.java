package de.allgemein.lmg.db;

import de.allgemein.lmg.model.SozialFall;
import de.allgemein.lmg.service.Auszahlungsauftrag;
import de.allgemein.lmg.service.Auszahlungsergebnis;
import de.allgemein.lmg.service.FachlicheException;
import de.allgemein.lmg.service.SozialLeistungsService;
import de.allgemein.tx.SimpleConnectionPool;
import de.allgemein.tx.TxManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Szenario-Regression: zehn Nutzer-Szenarien durchspielen die Arbeitsabläufe der
 * Fachschicht so, wie sie Sachbearbeiter über die Oberfläche auslösen — verschiedene
 * Sachbearbeiter erfassen unterschiedliche Bedarfsgemeinschaften und SozialFälle,
 * ändern Daten, erteilen Bescheide, ordnen Auszahlungen an, geben frei, stornieren
 * und historisieren. Verifiziert wird am Ende jedes Szenarios der tatsächlich
 * persistierte Zustand per SQL direkt auf den Oracle-Tabellen (nicht über den
 * Service zurückgelesen) — einschließlich Beträgen, Statuswerten, Sachbearbeiter-
 * Zuordnungen und Journal-Chronologie.
 *
 * <p>Läuft nur, wenn TX_URL gesetzt ist (wie {@link EchteDbVerifikationTest});
 * der normale {@code mvn test} bleibt ohne DB grün:</p>
 *
 * <pre>
 *   TX_URL=jdbc:oracle:thin:@//localhost:1921/o19c1p1 \
 *   TX_USER=tx_demo TX_PASS=tx_demo \
 *   mvn test -Dtest=SzenarioRegressionTest
 * </pre>
 *
 * <p>Datenhygiene: alle Szenarien legen Aktenzeichen mit Präfix {@code BG-REG-}
 * an; @BeforeAll/@AfterAll räumen diese Vorgänge komplett auf (Kindtabellen
 * zuerst). Die Szenarien sind unabhängig voneinander und dürfen in beliebiger
 * Reihenfolge laufen.</p>
 *
 * <p>Erwartete Zahlbeträge sind nach der Referenzberechnung 2026 handgerechnet
 * (Regelbedarfsstufen 563/506/451/471/390/357; Mehrbedarfe § 30 SGB XII;
 * Erwerbsfreibetrag § 82 Abs. 3; Schonvermögen § 90 Abs. 2 Nr. 9); die KdU-
 * Angemessenheitsprüfung ist im Standard des Service deaktiviert.</p>
 */
@EnabledIfEnvironmentVariable(named = "TX_URL", matches = ".+")
class SzenarioRegressionTest {

    private static SimpleConnectionPool pool;
    private static SozialLeistungsService service;

    @BeforeAll
    static void aufbauen() throws Exception {
        Class.forName("oracle.jdbc.OracleDriver");
        pool = new SimpleConnectionPool(
                System.getenv("TX_URL"), System.getenv("TX_USER"), System.getenv("TX_PASS"), 4);
        service = new SozialLeistungsService(new TxManager(pool));
        entferneSzenarioDaten();
    }

    @AfterAll
    static void aufraeumen() throws Exception {
        entferneSzenarioDaten();
        if (pool != null) {
            pool.close();
        }
    }

    /** Löscht alle Vorgänge dieses Testlaufes (Aktenzeichen-Präfix BG-REG-), Kindtabellen zuerst. */
    private static void entferneSzenarioDaten() throws Exception {
        try (Connection c = DriverManager.getConnection(
                System.getenv("TX_URL"), System.getenv("TX_USER"), System.getenv("TX_PASS"))) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                st.executeUpdate("DELETE FROM JOURNAL WHERE BG_ID IN "
                        + "(SELECT ID FROM BEDARFSGEMEINSCHAFT WHERE AKTENZEICHEN LIKE 'BG-REG-%')");
                st.executeUpdate("DELETE FROM BESCHEID WHERE BG_ID IN "
                        + "(SELECT ID FROM BEDARFSGEMEINSCHAFT WHERE AKTENZEICHEN LIKE 'BG-REG-%')");
                st.executeUpdate("DELETE FROM ZAHLUNG WHERE BG_ID IN "
                        + "(SELECT ID FROM BEDARFSGEMEINSCHAFT WHERE AKTENZEICHEN LIKE 'BG-REG-%')");
                st.executeUpdate("DELETE FROM SOZIAL_FALL WHERE BG_ID IN "
                        + "(SELECT ID FROM BEDARFSGEMEINSCHAFT WHERE AKTENZEICHEN LIKE 'BG-REG-%')");
                st.executeUpdate("DELETE FROM BEDARFSGEMEINSCHAFT WHERE AKTENZEICHEN LIKE 'BG-REG-%'");
                c.commit();
            }
        }
    }

    // ==================================================================
    // Szenario 1: Standardrente — Erfasser und Freigebender (Vier-Augen)
    // ==================================================================

    /**
     * Sachbearbeiter Müller erfasst einen alleinstehenden Rentner (RBS 1 mit
     * KdU 350/60/80) und ordnet die Auszahlung an; die Selbstfreigabe wird
     * abgewehrt, Kollege Kowalski gibt frei. In der DB: Zahlung BEREIT mit
     * Erfasser/Freigebender getrennt, Budget 1.200 − 1.053,00 = 146... 147,00 Rest.
     */
    @Test
    void szenario01StandardrenteZweiBearbeiterVierAugen() throws Exception {
        String az = aktenzeichen("01");
        var bg = service.legeBedarfsgemeinschaftAn(az, "Rentner, Rudolf",
                new BigDecimal("1200.00"), null, "sb-mueller");
        service.legeSozialFallAn(bg.id(), entwurf(1, false, false, 0, 0, false,
                "350.00", "60.00", "80.00", "0.00", "0.00", "0.00", "0.00"), "sb-mueller");

        // Bedarf: 563,00 RBS 1 + 490,00 KdU = 1.053,00 EUR
        var zahlung = service.ordneAuszahlungAn(
                new Auszahlungsauftrag(bg.id(), UUID.randomUUID().toString(), "2026-10", "sb-mueller"))
                .zahlung();

        // Selbstfreigabe abwehren, dann Freigabe durch die Zweitprüferin
        var selbst = assertThrows(FachlicheException.class,
                () -> service.freigebenZahlung(zahlung.id(), "sb-mueller"));
        assertEquals(FachlicheException.BERECHTIGUNG, selbst.code());
        service.freigebenZahlung(zahlung.id(), "sb-kowalski");

        try (Connection c = verbindung()) {
            assertEquals("BEREIT", text(c,
                    "SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?", zahlung.id()),
                    "Zahlung muss nach Freigabe durch den Zweitprüfer BEREIT sein");
            assertBetrag("Zahlbetrag RBS 1 + KdU", "1053.00", betrag(c,
                    "SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?", zahlung.id()));
            assertEquals("sb-mueller", text(c,
                    "SELECT SACHBEARBEITER_ID FROM ZAHLUNG WHERE ID = ?", zahlung.id()),
                    "Erfasser muss Müller bleiben");
            assertEquals("sb-kowalski", text(c,
                    "SELECT FREIGEGEBEN_VON FROM ZAHLUNG WHERE ID = ?", zahlung.id()),
                    "Freigebender muss Kowalski sein (Vier-Augen-Prinzip)");
            assertBetrag("Budget muss um den Zahlbetrag gemindert sein", "147.00", betrag(c,
                    "SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?", bg.id()));
            assertTrue(ganzeZahl(c, "SELECT COUNT(*) FROM JOURNAL WHERE BG_ID = ? "
                            + "AND SACHBEARBEITER_ID = 'sb-kowalski'", bg.id()) >= 1,
                    "Freigabe muss im Journal Kowalski zuordenbar sein");
        }
    }

    // ==================================================================
    // Szenario 2: zwei Sachbearbeiter, zwei unabhängige Vorgänge
    // ==================================================================

    /**
     * Kowalski erfasst ein Ehepaar (2 × RBS 2 mit KdU auf dem ersten Fall),
     * Yilmaz zeitversetzt einen alleinstehenden Schüler (RBS 4 ohne KdU). Beide
     * ordnen Auszahlungen an. In der DB: je Vorgang genau eine Zahlung mit dem
     * korrekten Betrag (1.662,00 bzw. 471,00), getrennte Budgets und Journale;
     * FALL_ID nur beim Ein-Personen-Vorgang gesetzt.
     */
    @Test
    void szenario02ZweiSachbearbeiterUnabhaengigeVorgaenge() throws Exception {
        String azA = aktenzeichen("02A");
        var bgA = service.legeBedarfsgemeinschaftAn(azA, "Paar, Paul",
                new BigDecimal("2000.00"), null, "sb-kowalski");
        service.legeSozialFallAn(bgA.id(), entwurf(2, false, false, 0, 0, false,
                "500.00", "80.00", "70.00", "0.00", "0.00", "0.00", "0.00"), "sb-kowalski");
        service.legeSozialFallAn(bgA.id(), entwurf(2, false, false, 0, 0, false,
                "0.00", "0.00", "0.00", "0.00", "0.00", "0.00", "0.00"), "sb-kowalski");

        String azB = aktenzeichen("02B");
        var bgB = service.legeBedarfsgemeinschaftAn(azB, "Schueler, Sinan",
                new BigDecimal("500.00"), null, "sb-yilmaz");
        service.legeSozialFallAn(bgB.id(), entwurf(4, false, false, 0, 0, false,
                "0.00", "0.00", "0.00", "0.00", "0.00", "0.00", "0.00"), "sb-yilmaz");

        // A: 506 + 506 + 650 KdU = 1.662,00 — B: 471,00
        service.ordneAuszahlungAn(
                new Auszahlungsauftrag(bgA.id(), UUID.randomUUID().toString(), "2026-10", "sb-kowalski"));
        service.ordneAuszahlungAn(
                new Auszahlungsauftrag(bgB.id(), UUID.randomUUID().toString(), "2026-10", "sb-yilmaz"));

        try (Connection c = verbindung()) {
            assertEquals(1L, ganzeZahl(c,
                    "SELECT COUNT(*) FROM ZAHLUNG WHERE BG_ID = ?", bgA.id()));
            assertEquals(1L, ganzeZahl(c,
                    "SELECT COUNT(*) FROM ZAHLUNG WHERE BG_ID = ?", bgB.id()));
            assertBetrag("Zahlbetrag Vorgang A (Paar RBS 2 + KdU)", "1662.00", betrag(c,
                    "SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE BG_ID = ?", bgA.id()));
            assertBetrag("Zahlbetrag Vorgang B (RBS 4)", "471.00", betrag(c,
                    "SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE BG_ID = ?", bgB.id()));
            assertBetrag("Restbudget A", "338.00", betrag(c,
                    "SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?", bgA.id()));
            assertBetrag("Restbudget B", "29.00", betrag(c,
                    "SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?", bgB.id()));
            // Verknüpfung zum SozialFall nur eindeutig, wenn die BG genau einen Fall hat
            assertNull(text(c, "SELECT FALL_ID FROM ZAHLUNG WHERE BG_ID = ?", bgA.id()),
                    "Mehrpersonen-Vorgang darf keine Fallzuordnung in der Zahlung haben");
            assertNotNull(text(c, "SELECT FALL_ID FROM ZAHLUNG WHERE BG_ID = ?", bgB.id()),
                    "Einpersonen-Vorgang muss den Fall referenzieren");
            // Journalzuordnung: jeder Vorgang trägt nur die Aktionen seines Bearbeiters
            assertTrue(ganzeZahl(c, "SELECT COUNT(*) FROM JOURNAL WHERE BG_ID = ? "
                    + "AND SACHBEARBEITER_ID = 'sb-kowalski'", bgA.id()) >= 3);
            assertTrue(ganzeZahl(c, "SELECT COUNT(*) FROM JOURNAL WHERE BG_ID = ? "
                    + "AND SACHBEARBEITER_ID = 'sb-yilmaz'", bgB.id()) >= 3);
            assertEquals(0L, ganzeZahl(c, "SELECT COUNT(*) FROM JOURNAL WHERE BG_ID = ? "
                    + "AND SACHBEARBEITER_ID = 'sb-yilmaz'", bgA.id()),
                    "Vorgang A darf keine Journal-Einträge von Yilmaz haben");
        }
    }

    // ==================================================================
    // Szenario 3: schwangere Leistungsberechtigte mit Merkzeichen G
    // ==================================================================

    /**
     * Berger erfasst eine schwangere Leistungsberechtigte (ab 13. SSW) mit
     * Merkzeichen G und dezentraler Warmwassererzeugung. In der DB: die drei
     * Merkmals-Flags, Zahlbetrag 767,37 (563 + 95,71 G + 95,71 Schwangerschaft
     * + 12,95 Warmwasser § 30 Abs. 7).
     */
    @Test
    void szenario03SchwangereMerkzeichenGWarmwasser() throws Exception {
        String az = aktenzeichen("03");
        var bg = service.legeBedarfsgemeinschaftAn(az, "Schwangere, Sabine",
                new BigDecimal("800.00"), null, "sb-berger");
        service.legeSozialFallAn(bg.id(), entwurf(1, true, true, 0, 0, true,
                "0.00", "0.00", "0.00", "0.00", "0.00", "0.00", "0.00"), "sb-berger");

        var zahlung = service.ordneAuszahlungAn(
                new Auszahlungsauftrag(bg.id(), UUID.randomUUID().toString(), "2026-10", "sb-berger"))
                .zahlung();

        try (Connection c = verbindung()) {
            assertEquals(1L, ganzeZahl(c,
                    "SELECT COUNT(*) FROM SOZIAL_FALL WHERE BG_ID = ? AND FLAG_MERKZEICHEN_G = 1 "
                            + "AND FLAG_SCHWANGER = 1 AND FLAG_WARMWASSER = 1", bg.id()),
                    "Alle drei Mehrbedarfs-Merkmale müssen persistiert sein");
            assertBetrag("Zahlbetrag inkl. aller drei Mehrbedarfe (§ 30 Abs. 1, 2 und 7)",
                    "767.37", betrag(c,
                            "SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?", zahlung.id()));
            assertEquals("ANGELEGT", text(c,
                    "SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?", zahlung.id()));
            assertBetrag("Restbudget", "32.63", betrag(c,
                    "SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?", bg.id()));
        }
    }

    // ==================================================================
    // Szenario 4: alleinerziehende Mutter mit zwei Kindern
    // ==================================================================

    /**
     * Yilmaz erfasst eine alleinerziehende Mutter (RBS 1, zwei Kinder unter 16
     * im Haushalt, KdU auf der Mutter) mit zwei Kindern als eigene Fälle
     * (RBS 6) und einer Wiedervorlage. In der DB: drei SozialFall-Zeilen,
     * Kinderzahlen, Wiedervorlage-Datum und der Gesamtzahlbetrag 2.189,68
     * (563 + 202,68 Alleinerziehenden-Mehrbedarf § 30 Abs. 3 Nr. 1
     * + 710 KdU + 2 × 357).
     */
    @Test
    void szenario04AlleinerziehendeMitZweiKindern() throws Exception {
        String az = aktenzeichen("04");
        var wiedervorlage = LocalDate.of(2027, 1, 15);
        var bg = service.legeBedarfsgemeinschaftAn(az, "Mutter, Miriam",
                new BigDecimal("2500.00"), wiedervorlage, "sb-yilmaz");
        service.legeSozialFallAn(bg.id(), entwurf(1, false, false, 0, 2, false,
                "500.00", "90.00", "120.00", "0.00", "0.00", "0.00", "0.00"), "sb-yilmaz");
        service.legeSozialFallAn(bg.id(), entwurf(6, false, false, 0, 0, false,
                "0.00", "0.00", "0.00", "0.00", "0.00", "0.00", "0.00"), "sb-yilmaz");
        service.legeSozialFallAn(bg.id(), entwurf(6, false, false, 0, 0, false,
                "0.00", "0.00", "0.00", "0.00", "0.00", "0.00", "0.00"), "sb-yilmaz");

        var zahlung = service.ordneAuszahlungAn(
                new Auszahlungsauftrag(bg.id(), UUID.randomUUID().toString(), "2026-10", "sb-yilmaz"))
                .zahlung();

        try (Connection c = verbindung()) {
            assertEquals(3L, ganzeZahl(c,
                    "SELECT COUNT(*) FROM SOZIAL_FALL WHERE BG_ID = ?", bg.id()),
                    "Mutter und zwei Kinder müssen als drei SozialFälle liegen");
            assertEquals(1L, ganzeZahl(c,
                    "SELECT COUNT(*) FROM SOZIAL_FALL WHERE BG_ID = ? AND REGELBEDARFSSTUFE = 1 "
                            + "AND ANZ_KINDER_U16 = 2 AND ANZ_KINDER_MINDERJ = 2", bg.id()),
                    "Kinderzahlen müssen an der Mutter persistiert sein");
            assertEquals(2L, ganzeZahl(c,
                    "SELECT COUNT(*) FROM SOZIAL_FALL WHERE BG_ID = ? AND REGELBEDARFSSTUFE = 6",
                    bg.id()), "beide Kinder in Stufe 6");
            assertEquals(1L, ganzeZahl(c,
                    "SELECT COUNT(*) FROM BEDARFSGEMEINSCHAFT WHERE ID = ? AND WIEDERVORLAGE_AM = ?",
                    bg.id(), wiedervorlage), "Wiedervorlage muss im Vorgang stehen");
            assertNull(text(c, "SELECT FALL_ID FROM ZAHLUNG WHERE ID = ?", zahlung.id()),
                    "Bei drei Fällen ist keine eindeutige Fallzuordnung möglich");
            assertBetrag("Gesamtzahlbetrag Familie", "2189.68", betrag(c,
                    "SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?", zahlung.id()));
            assertBetrag("Restbudget", "310.32", betrag(c,
                    "SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?", bg.id()));
        }
    }

    // ==================================================================
    // Szenario 5: erwerbstätiger Leistungsberechtigter (§ 82 SGB XII)
    // ==================================================================

    /**
     * Schneider erfasst einen erwerbstätigen Leistungsberechtigten (RBS 1,
     * Brutto 1.200, Steuern 150, Sozialversicherung 200). In der DB: Erwerbs-
     * einkommen und Absetzbeträge persistiert; Zahlbetrag 554,50 — Erwerbs-
     * freibetrag § 82 Abs. 3 auf 281,50 gekappt, danach Absetzbeträge
     * (§ 82 Abs. 2): 1.123,00 Bedarf − 568,50 anrechenbares Einkommen.
     */
    @Test
    void szenario05ErwerbstaetigerFreibetragUndAbsetzbetraege() throws Exception {
        String az = aktenzeichen("05");
        var bg = service.legeBedarfsgemeinschaftAn(az, "Werktätiger, Willi",
                new BigDecimal("700.00"), null, "sb-schneider");
        service.legeSozialFallAn(bg.id(), entwurf(1, false, false, 0, 0, false,
                "400.00", "70.00", "90.00", "0.00", "1200.00", "150.00", "200.00"),
                "sb-schneider");

        var zahlung = service.ordneAuszahlungAn(
                new Auszahlungsauftrag(bg.id(), UUID.randomUUID().toString(), "2026-10", "sb-schneider"))
                .zahlung();

        try (Connection c = verbindung()) {
            assertBetrag("Erwerbseinkommen persistiert", "1200.00", betrag(c,
                    "SELECT EINKOMMEN_ERWERB FROM SOZIAL_FALL WHERE BG_ID = ?", bg.id()));
            assertBetrag("Absetzung Steuern persistiert", "150.00", betrag(c,
                    "SELECT ABZ_STEUERN FROM SOZIAL_FALL WHERE BG_ID = ?", bg.id()));
            assertBetrag("Absetzung Sozialversicherung persistiert", "200.00", betrag(c,
                    "SELECT ABZ_SV FROM SOZIAL_FALL WHERE BG_ID = ?", bg.id()));
            assertBetrag("Freibetrag gekappt auf 281,50, Absetzbeträge erst danach (§ 82 Abs. 2/3)",
                    "554.50", betrag(c,
                            "SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?", zahlung.id()));
            assertBetrag("Restbudget", "145.50", betrag(c,
                    "SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?", bg.id()));
        }
    }

    // ==================================================================
    // Szenario 6: Vermögen über dem Schonvermögen (§ 90 SGB XII)
    // ==================================================================

    /**
     * Berger erfasst einen Vermögensfall: Einzelperson (RBS 1) mit 10.500 EUR
     * Vermögen — 10.000 EUR Schonvermögen bleiben frei, 500 EUR werden
     * angerechnet. Zahlbetrag 363,00 (863,00 Bedarf − 500,00 verwertbares
     * Vermögen); die Verbrauchsfortschreibung (§ 90, seit v0.5.6) senkt den
     * Bestand auf das Schonvermögen und dokumentiert den Einsatz an der Zahlung.
     */
    @Test
    void szenario06VermoegenUeberSchonvermoegen() throws Exception {
        String az = aktenzeichen("06");
        var bg = service.legeBedarfsgemeinschaftAn(az, "Sparer, Sieglinde",
                new BigDecimal("500.00"), null, null, null, null,
                new BigDecimal("10500.00"), "sb-berger");
        service.legeSozialFallAn(bg.id(), entwurf(1, false, false, 0, 0, false,
                "300.00", "0.00", "0.00", "0.00", "0.00", "0.00", "0.00"), "sb-berger");

        var zahlung = service.ordneAuszahlungAn(
                new Auszahlungsauftrag(bg.id(), UUID.randomUUID().toString(), "2026-10", "sb-berger"))
                .zahlung();

        try (Connection c = verbindung()) {
            assertBetrag("Vermögen nach Verbrauchsfortschreibung = Schonvermögen",
                    "10000.00", betrag(c,
                            "SELECT VERMOEGEN FROM BEDARFSGEMEINSCHAFT WHERE ID = ?", bg.id()));
            assertBetrag("Dokumentierter Vermögenseinsatz der Zahlung", "500.00", betrag(c,
                    "SELECT VERMOEGENSEINSATZ FROM ZAHLUNG WHERE ID = ?", zahlung.id()));
            assertBetrag("10.000 EUR Schonvermögen abgezogen, 500 EUR verwertbar angerechnet",
                    "363.00", betrag(c,
                            "SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?", zahlung.id()));
            assertBetrag("Restbudget", "137.00", betrag(c,
                    "SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?", bg.id()));
        }
    }

    // ==================================================================
    // Szenario 7: Doppelklick / Netzwerk-Wiederholung (Idempotenz)
    // ==================================================================

    /**
     * Berger ordnet eine Auszahlung an; der Request wird (Doppelklick oder
     * Netzwerk-Wiederholung) mit derselben Vorgangsnummer erneut gesendet.
     * In der DB: genau EINE Zahlung, EIN Auszahlungs-Journaleintrag, Budget
     * nur einmal gemindert (700 − 563 = 137,00).
     */
    @Test
    void szenario07DoppelkbugIdempotenz() throws Exception {
        String az = aktenzeichen("07");
        var bg = service.legeBedarfsgemeinschaftAn(az, "Klick, Karin",
                new BigDecimal("700.00"), null, "sb-berger");
        service.legeSozialFallAn(bg.id(), entwurf(1, false, false, 0, 0, false,
                "0.00", "0.00", "0.00", "0.00", "0.00", "0.00", "0.00"), "sb-berger");

        String vorgangsNr = UUID.randomUUID().toString();
        Auszahlungsergebnis erste = service.ordneAuszahlungAn(
                new Auszahlungsauftrag(bg.id(), vorgangsNr, "2026-10", "sb-berger"));
        Auszahlungsergebnis wiederholt = service.ordneAuszahlungAn(
                new Auszahlungsauftrag(bg.id(), vorgangsNr, "2026-10", "sb-berger"));

        assertTrue(wiederholt.bereitsGebucht(), "Wiederholung muss als bereits gebucht erkannt werden");
        assertEquals(erste.zahlung().id(), wiederholt.zahlung().id());

        try (Connection c = verbindung()) {
            assertEquals(1L, ganzeZahl(c,
                    "SELECT COUNT(*) FROM ZAHLUNG WHERE VORGANGS_NR = ?", vorgangsNr),
                    "Trotz Wiederholung darf nur eine Zahlung existieren");
            assertEquals(1L, ganzeZahl(c, "SELECT COUNT(*) FROM JOURNAL WHERE BG_ID = ? "
                    + "AND AKTION_TEXT LIKE 'Auszahlung über%'", bg.id()),
                    "Auch im Journal darf die Auszahlung nur einmal stehen");
            assertBetrag("Budget darf nur einmal gemindert sein", "137.00", betrag(c,
                    "SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?", bg.id()));
        }
    }

    // ==================================================================
    // Szenario 8: Storno und korrigierte Neuauszahlung im selben Monat
    // ==================================================================

    /**
     * Yilmaz zahlt einen Bedarf mit 200 EUR Miete aus (763,00), Berger gibt
     * frei. Der Fehler fällt auf: Yilmaz korrigiert die Miete auf 250 EUR
     * (Version steigt), storniert die alte Zahlung und ordnet für denselben
     * Monat neu an. In der DB: zwei Zahlungen (STORNIERT 763,00 / ANGELEGT
     * 813,00), Budget 1.000 − 813 = 187,00, Fall in Version 2.
     */
    @Test
    void szenario08StornoUndNeueZahlungImSelbenMonat() throws Exception {
        String az = aktenzeichen("08");
        var bg = service.legeBedarfsgemeinschaftAn(az, "Korrektur, Kurt",
                new BigDecimal("1000.00"), null, "sb-yilmaz");
        service.legeSozialFallAn(bg.id(), entwurf(1, false, false, 0, 0, false,
                "200.00", "0.00", "0.00", "0.00", "0.00", "0.00", "0.00"), "sb-yilmaz");

        var alteZahlung = service.ordneAuszahlungAn(
                new Auszahlungsauftrag(bg.id(), UUID.randomUUID().toString(), "2026-09", "sb-yilmaz"))
                .zahlung();
        service.freigebenZahlung(alteZahlung.id(), "sb-berger");
        service.storniereZahlung(alteZahlung.id(), "sb-yilmaz");

        // Fachliche Korrektur: Miete 200 -> 250 (Version 1 -> 2)
        SozialFall geladen = service.ladeDetail(bg.id()).faelle().get(0);
        SozialFall korrigiert = new SozialFall(geladen.id(), geladen.bgId(),
                geladen.regelbedarfsstufe(), geladen.merkzeichenG(), geladen.schwanger(),
                geladen.anzKinderUnter7(), geladen.anzKinderUnter16(), geladen.anzKinderMinderjaehrig(),
                geladen.warmwasser(), new BigDecimal("250.00"), geladen.kduBetriebskosten(),
                geladen.kduHeizung(), geladen.einkommenRente(), geladen.einkommenErwerb(),
                geladen.abzSteuern(), geladen.abzSozialversicherung(), geladen.abzVersicherungen(),
                geladen.abzErwerbsaufwendungen(), geladen.version());
        service.aktualisiereSozialFall(korrigiert, geladen.version(), "sb-yilmaz");

        var neueZahlung = service.ordneAuszahlungAn(
                new Auszahlungsauftrag(bg.id(), UUID.randomUUID().toString(), "2026-09", "sb-yilmaz"))
                .zahlung();

        try (Connection c = verbindung()) {
            assertEquals("STORNIERT", text(c,
                    "SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?", alteZahlung.id()));
            assertBetrag("Storno-Betrag", "763.00", betrag(c,
                    "SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?", alteZahlung.id()));
            assertEquals("ANGELEGT", text(c,
                    "SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?", neueZahlung.id()),
                    "nach Storno ist derselbe Monat wieder buchbar");
            assertBetrag("korrigierter Zahlbetrag", "813.00", betrag(c,
                    "SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?", neueZahlung.id()));
            assertBetrag("Storno muss das Budget zurückbuchen, Neuauszahlung neu mindern",
                    "187.00", betrag(c,
                            "SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?", bg.id()));
            assertEquals(2L, ganzeZahl(c,
                    "SELECT VERSION FROM SOZIAL_FALL WHERE ID = ?", geladen.id()),
                    "Fallkorrektur muss die Version hochsetzen");
        }
    }

    // ==================================================================
    // Szenario 9: Bescheid durch Prüferin, Auszahlung durch Erfasserin
    // ==================================================================

    /**
     * Schneider erfasst Fall und Bankverbindung (Kontoinhaber weicht von der
     * Hauptperson ab); Prüferin Perez erteilt den Bewilligungsbescheid für
     * 2026-01 bis 2026-12, Schneider ordnet die Auszahlung an. In der DB:
     * Bescheid mit ERSTELLT_VON = sb-perez und gefülltem Berechnungs-Snapshot
     * (CLOB), Zahlung mit Erfasserin sb-schneider und den SEPA-Daten aus dem
     * Vorgang (Fallback), Beträge je 1.183,00.
     */
    @Test
    void szenario09BescheidDurchPrueferZahlungDurchErfasserin() throws Exception {
        String az = aktenzeichen("09");
        var bg = service.legeBedarfsgemeinschaftAn(az, "Wolke, Walter",
                new BigDecimal("1500.00"), null,
                "DE89370400440532013000", "MARKDEF1100", "Wolke, Wilma", "sb-schneider");
        service.legeSozialFallAn(bg.id(), entwurf(1, false, false, 0, 0, false,
                "450.00", "90.00", "80.00", "0.00", "0.00", "0.00", "0.00"), "sb-schneider");

        var bescheid = service.erteileBescheid(bg.id(), "2026-01", "2026-12", "sb-perez");
        var zahlung = service.ordneAuszahlungAn(
                new Auszahlungsauftrag(bg.id(), UUID.randomUUID().toString(), "2026-10", "sb-schneider"))
                .zahlung();

        try (Connection c = verbindung()) {
            assertBetrag("Bescheid-Zahlbetrag", "1183.00", betrag(c,
                    "SELECT ZAHLBETRAG FROM BESCHEID WHERE ID = ?", bescheid.id()));
            assertEquals("sb-perez", text(c,
                    "SELECT ERSTELLT_VON FROM BESCHEID WHERE ID = ?", bescheid.id()),
                    "Bescheid muss der Prüferin zugeordnet sein");
            assertEquals("ERTEILT", text(c,
                    "SELECT STATUS FROM BESCHEID WHERE ID = ?", bescheid.id()));
            assertTrue(text(c, "SELECT BESCHEID_NR FROM BESCHEID WHERE ID = ?", bescheid.id())
                            .contains(az),
                    "Bescheidnummer muss das Aktenzeichen tragen");
            assertTrue(ganzeZahl(c, "SELECT DBMS_LOB.GETLENGTH(BERECHNUNG) FROM BESCHEID WHERE ID = ?",
                    bescheid.id()) > 0, "Berechnungs-Snapshot (CLOB) muss gefüllt sein");
            assertEquals("{", text(c,
                    "SELECT DBMS_LOB.SUBSTR(BERECHNUNG, 1, 1) FROM BESCHEID WHERE ID = ?", bescheid.id()),
                    "Snapshot muss JSON sein");
            assertEquals("sb-schneider", text(c,
                    "SELECT SACHBEARBEITER_ID FROM ZAHLUNG WHERE ID = ?", zahlung.id()));
            assertBetrag("Auszahlungsbetrag", "1183.00", betrag(c,
                    "SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?", zahlung.id()));
            assertEquals("DE89370400440532013000", text(c,
                    "SELECT EMPFAENGER_IBAN FROM ZAHLUNG WHERE ID = ?", zahlung.id()));
            assertEquals("Wolke, Wilma", text(c,
                    "SELECT EMPFAENGER_NAME FROM ZAHLUNG WHERE ID = ?", zahlung.id()),
                    "Empfängername muss vom Kontoinhaber des Vorgangs kommen");
            assertEquals(1L, ganzeZahl(c, "SELECT COUNT(*) FROM JOURNAL WHERE BG_ID = ? "
                            + "AND SACHBEARBEITER_ID = 'sb-perez' AND AKTION_TEXT LIKE 'Bewilligungsbescheid%'",
                    bg.id()), "Bescheiderteilung muss im Journal Perez zuordnen");
        }
    }

    // ==================================================================
    // Szenario 10: kompletter Lebenszyklus eines Vorgangs
    // ==================================================================

    /**
     * Voller Ablauf über vier Beteiligte: Berger erfasst Vorgang und Fall;
     * Kowalski korrigiert die Miete; Bergers Speicherversuch mit veraltetem
     * Stand wird als Konkurrenz abgewehrt (KdU bleibt 180, Version 2);
     * Perez erteilt den Bescheid (743,00); Kowalski ordnet die Auszahlung an;
     * Berger gibt frei; der Kassenlauf (sb-kasse) übernimmt; zuletzt wird der
     * Vorgang historisiert. In der DB: Endzustände aller Tabellen plus
     * vollständige Journal-Chronologie in der richtigen Reihenfolge.
     */
    @Test
    void szenario10KompletterLebenszyklus() throws Exception {
        String az = aktenzeichen("10");
        var bg = service.legeBedarfsgemeinschaftAn(az, "Lebenszyklus, Ludwig",
                new BigDecimal("1500.00"), null, "sb-berger");
        service.legeSozialFallAn(bg.id(), entwurf(1, false, false, 0, 0, false,
                "100.00", "0.00", "0.00", "0.00", "0.00", "0.00", "0.00"), "sb-berger");

        // Kowalski korrigiert die Miete 100 -> 180
        SozialFall geladen = service.ladeDetail(bg.id()).faelle().get(0);
        service.aktualisiereSozialFall(new SozialFall(geladen.id(), geladen.bgId(),
                geladen.regelbedarfsstufe(), geladen.merkzeichenG(), geladen.schwanger(),
                geladen.anzKinderUnter7(), geladen.anzKinderUnter16(), geladen.anzKinderMinderjaehrig(),
                geladen.warmwasser(), new BigDecimal("180.00"), geladen.kduBetriebskosten(),
                geladen.kduHeizung(), geladen.einkommenRente(), geladen.einkommenErwerb(),
                geladen.abzSteuern(), geladen.abzSozialversicherung(), geladen.abzVersicherungen(),
                geladen.abzErwerbsaufwendungen(), geladen.version()),
                geladen.version(), "sb-kowalski");

        // Berger speichert parallel seinen (veralteten) Maskenstand -> Konkurrenz
        var konflikt = assertThrows(FachlicheException.class, () -> service.aktualisiereSozialFall(
                geladen, geladen.version(), "sb-berger"));
        assertEquals(FachlicheException.KONKURRENZ, konflikt.code());

        service.erteileBescheid(bg.id(), "2026-01", "2026-12", "sb-perez");
        var zahlung = service.ordneAuszahlungAn(
                new Auszahlungsauftrag(bg.id(), UUID.randomUUID().toString(), "2026-10", "sb-kowalski"))
                .zahlung();
        service.freigebenZahlung(zahlung.id(), "sb-berger");
        var kassenlauf = service.uebergebeBereiteZahlungen("sb-kasse", az);

        // Historisierung des Vorgangs (Teamleitung)
        var detail = service.ladeDetail(bg.id());
        service.aktualisiereBedarfsgemeinschaft(bg.id(), false, null,
                detail.bedarfsgemeinschaft().version());

        try (Connection c = verbindung()) {
            // Konkurrenz: Kowalskis Korrektur steht, Bergers Altstand wurde verworfen
            assertBetrag("korrigierte Miete muss stehen bleiben", "180.00", betrag(c,
                    "SELECT KDU_MIETE FROM SOZIAL_FALL WHERE ID = ?", geladen.id()));
            assertEquals(2L, ganzeZahl(c,
                    "SELECT VERSION FROM SOZIAL_FALL WHERE ID = ?", geladen.id()));
            // Zahlungsweg inkl. Kassenlauf
            assertEquals(1, kassenlauf.anzahl(), "Kassenlauf muss genau die eigene Zahlung übernehmen");
            assertEquals("UEBERGEBEN", text(c,
                    "SELECT ZAHLUNGSSTATUS FROM ZAHLUNG WHERE ID = ?", zahlung.id()));
            assertBetrag("Zahlbetrag nach Mietkorrektur", "743.00", betrag(c,
                    "SELECT AUSZAHLUNGSBETRAG FROM ZAHLUNG WHERE ID = ?", zahlung.id()));
            assertBetrag("Restbudget", "757.00", betrag(c,
                    "SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?", bg.id()));
            // Bescheid und Historisierung
            assertEquals("ERTEILT", text(c,
                    "SELECT STATUS FROM BESCHEID WHERE BG_ID = ?", bg.id()));
            assertEquals(0L, ganzeZahl(c,
                    "SELECT STATUS_AKTIV FROM BEDARFSGEMEINSCHAFT WHERE ID = ?", bg.id()),
                    "Vorgang muss am Ende historisiert sein");
            // Kassenlauf-Journal ist vorgangsübergreifend (BG_ID leer), aber dem
            // Kassenläufer zugeordnet
            assertTrue(ganzeZahl(c, "SELECT COUNT(*) FROM JOURNAL WHERE BG_ID IS NULL "
                    + "AND SACHBEARBEITER_ID = 'sb-kasse' AND AKTION_TEXT LIKE 'Kassenlauf%'") >= 1);
            // Chronologie im Vorgangsjournal: Anlage -> Fallkorrektur -> Bescheid
            // -> Auszahlung -> Freigabe -> Historisierung
            List<String> journal = texte(c,
                    "SELECT AKTION_TEXT FROM JOURNAL WHERE BG_ID = ? ORDER BY ERSTELLT_AM, ID", bg.id());
            int[] positionen = {
                    position(journal, " angelegt"),
                    position(journal, "Bedarfs-/Einkommensermittlung"),
                    position(journal, "Bewilligungsbescheid"),
                    position(journal, "Auszahlung über"),
                    position(journal, "freigegeben"),
                    position(journal, "Status/Wiedervorlage")};
            for (int p : positionen) {
                assertTrue(p >= 0, "Journal muss alle Stationen enthalten: " + journal);
            }
            for (int i = 0; i < positionen.length - 1; i++) {
                assertTrue(positionen[i] < positionen[i + 1],
                        "Journal-Chronologie verletzt bei Position " + i + ": " + journal);
            }
        }
    }

    // ==================================================================
    // Hilfsfunktionen
    // ==================================================================

    /**
     * Betragsvergleich unabhängig vom Scale: Oracle NUMBER(10,2) liefert Werte
     * ohne Nachkomma-Nullen (1053 statt 1053.00), equals() wäre scale-sensitiv.
     */
    private static void assertBetrag(String meldung, String erwartet, BigDecimal ist) {
        assertNotNull(ist, meldung + " — DB hat keinen Wert geliefert");
        assertEquals(0, new BigDecimal(erwartet).compareTo(ist),
                meldung + " ==> erwartet " + erwartet + " EUR, war " + ist.toPlainString() + " EUR");
    }

    private static String aktenzeichen(String szenarioNr) {
        return "BG-REG-" + szenarioNr + "-" + (System.nanoTime() % 100000);
    }

    /**
     * Fall-Entwurf mit allen fachlichen Feldern; Versicherungs- und Erwerbs-
     * aufwendungen bleiben in den Szenarien null. bgId/id/version vergibt
     * der Service beim Anlegen.
     */
    private static SozialFall entwurf(int rbs, boolean merkzeichenG, boolean schwanger,
            int kinderU7, int kinderU16, boolean warmwasser,
            String kduMiete, String kduBetrieb, String kduHeizung,
            String einkommenRente, String einkommenErwerb, String abzSteuern, String abzSv) {
        return new SozialFall(0, 0, rbs, merkzeichenG, schwanger, kinderU7, kinderU16,
                Math.max(kinderU7, kinderU16), warmwasser,
                new BigDecimal(kduMiete), new BigDecimal(kduBetrieb), new BigDecimal(kduHeizung),
                new BigDecimal(einkommenRente), new BigDecimal(einkommenErwerb),
                new BigDecimal(abzSteuern), new BigDecimal(abzSv),
                BigDecimal.ZERO, BigDecimal.ZERO, 1);
    }

    private static Connection verbindung() throws Exception {
        Connection c = DriverManager.getConnection(
                System.getenv("TX_URL"), System.getenv("TX_USER"), System.getenv("TX_PASS"));
        c.setAutoCommit(true);
        return c;
    }

    /** Erster Textwert der Abfrage (null, wenn keine Zeile); Parameter werden typsicher gesetzt. */
    private static String text(Connection c, String sql, Object... parameter) throws Exception {
        try (PreparedStatement stmt = c.prepareStatement(sql)) {
            fuelle(stmt, parameter);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    /** Erster Betrag der Abfrage (null, wenn keine Zeile). */
    private static BigDecimal betrag(Connection c, String sql, Object... parameter) throws Exception {
        try (PreparedStatement stmt = c.prepareStatement(sql)) {
            fuelle(stmt, parameter);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getBigDecimal(1) : null;
            }
        }
    }

    /** Erster Zahlenwert der Abfrage (COUNT, VERSION, Status-Flags …). */
    private static long ganzeZahl(Connection c, String sql, Object... parameter) throws Exception {
        try (PreparedStatement stmt = c.prepareStatement(sql)) {
            fuelle(stmt, parameter);
            try (ResultSet rs = stmt.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    /** Alle Textwerte einer einspaltigen Abfrage in Trefferreihenfolge. */
    private static List<String> texte(Connection c, String sql, Object... parameter) throws Exception {
        List<String> werte = new ArrayList<>();
        try (PreparedStatement stmt = c.prepareStatement(sql)) {
            fuelle(stmt, parameter);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    werte.add(rs.getString(1));
                }
            }
        }
        return werte;
    }

    private static void fuelle(PreparedStatement stmt, Object... parameter) throws Exception {
        for (int i = 0; i < parameter.length; i++) {
            Object p = parameter[i];
            if (p instanceof String s) {
                stmt.setString(i + 1, s);
            } else if (p instanceof Long l) {
                stmt.setLong(i + 1, l);
            } else if (p instanceof Integer n) {
                stmt.setInt(i + 1, n);
            } else if (p instanceof BigDecimal b) {
                stmt.setBigDecimal(i + 1, b);
            } else if (p instanceof LocalDate d) {
                stmt.setDate(i + 1, java.sql.Date.valueOf(d));
            } else {
                throw new IllegalArgumentException("Nicht unterstützter Parametertyp: " + p);
            }
        }
    }

    /** Position des ersten Journaleintrags, der das Muster enthält; −1, wenn keines. */
    private static int position(List<String> journal, String muster) {
        for (int i = 0; i < journal.size(); i++) {
            if (journal.get(i).contains(muster)) {
                return i;
            }
        }
        return -1;
    }
}
