package de.allgemein.lmg.saga;

import de.allgemein.lmg.calc.BerechnungsSnapshot;
import de.allgemein.lmg.calc.Leistungsberechnung;
import de.allgemein.lmg.config.LmgConfig;
import de.allgemein.lmg.model.SozialFall;
import de.allgemein.lmg.service.FachlicheException;
import de.allgemein.lmg.service.SozialLeistungsService;
import de.allgemein.lmg.service.Zahlungsmonat;
import de.allgemein.lmg.web.Json;
import de.allgemein.tx.saga.SagaDefinition;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Vollständige fachliche End-to-End-Saga für das Leistungsmanagement Grundsicherung:
 * Kette von 4 unteilbaren Datenbanktransaktionen mit automatischer Rückwärts-Kompensation:
 *
 * <ol>
 *   <li><b>Vorgang erfassen:</b> Anlage von BEDARFSGEMEINSCHAFT und SOZIAL_FALL</li>
 *   <li><b>Bedarfsprüfung:</b> SGB-XII-Berechnung; Abbruch bei fehlendem Anspruch</li>
 *   <li><b>Bescheid erteilen:</b> Erstellung des Bewilligungsbescheids nach § 44 SGB XII</li>
 *   <li><b>Erstauszahlung anweisen:</b> Zahlung im Status ANGELEGT (wartet auf die
 *       Vier-Augen-Freigabe durch einen zweiten Sachbearbeiter), Minderung des Restbudgets</li>
 * </ol>
 *
 * <p><b>Berechnungsbasis (Codereview CR-003):</b> Die Schritte 2–4 rechnen ausschließlich
 * auf dem Datenbestand, den Schritt 1 geschrieben hat (SozialFälle, Vermögen) — nicht auf
 * dem Payload. Damit sind manuelle Korrekturen zwischen den Schritten wirksam, und Bescheid
 * wie Erstauszahlung verwenden dieselbe Basis wie der manuelle Buchungsweg, inklusive der
 * konfigurierten KdU-Parameter (§ 35 Abs. 7 SGB XII) und der Vermögensanrechnung
 * (§ 43 Abs. 1, § 90 SGB XII; optionales Payload-Feld {@code vermoegen}).</p>
 *
 * <p>Konventionen für die Kompensationen (Voraussetzung für den Wiederanlauf, der
 * IN_FLIGHT-Schritte konservativ als „möglicherweise committet" mit kompensiert):</p>
 * <ul>
 *   <li>Alle Kompensationen sind <b>idempotent</b> — wiederholte Ausführung ist harmlos.</li>
 *   <li>Die Zahlung der Saga wird über die deterministische VORGANGS_NR
 *       {@code "SAGA-" + sagaId} korreliert — fremde Zahlungen derselben Bedarfsgemeinschaft
 *       bleiben unberührt.</li>
 *   <li>Der Vorgang wird beim Rückwärtslauf <b>historisiert</b> (STATUS_AKTIV = 0), nicht
 *       gelöscht (§ 67 SGB X: Nachvollziehbarkeit der Ablehnung).</li>
 * </ul>
 */
public final class AntragsSagaProzess {

    private static final Logger LOG = Logger.getLogger(AntragsSagaProzess.class.getName());

    public static final String SAGA_TYPE = "SGB_XII_ANTRAGSVERARBEITUNG";

    /** Präfix der VORGANGS_NR für Saga-Zahlungen: deterministisch aus der sagaId ableitbar. */
    public static final String VORGANGS_NR_PRAEFIX = "SAGA-";

    /** Konfigurierte KdU-Parameter, einmalig lazy geladen (Injektion für Tests möglich). */
    private static volatile Leistungsberechnung.KduParameter kduParameterCache = null;

    /** Liest die je Jahr gepflegten Regelbedarfsstufen (Tabelle SATZSTAND) je Bezugsmonat. */
    private static final de.allgemein.lmg.dao.SatzstandDao SATZSTAND_DAO = new de.allgemein.lmg.dao.SatzstandDao();

    private AntragsSagaProzess() {
    }

    public static SagaDefinition definition() {
        return SagaDefinition.of(SAGA_TYPE)
                .step("Vorgang erfassen", AntragsSagaProzess::vorgangErfassen, AntragsSagaProzess::vorgangKompensieren)
                .step("Bedarfspruefung", AntragsSagaProzess::bedarfspruefung, AntragsSagaProzess::bedarfspruefungKompensieren)
                .step("Bescheid erteilen", AntragsSagaProzess::bescheidErteilen, AntragsSagaProzess::bescheidKompensieren)
                .step("Erstauszahlung anweisen", AntragsSagaProzess::erstauszahlungAnweisen, AntragsSagaProzess::erstauszahlungKompensieren)
                .build();
    }

    /** Deterministische VORGANGS_NR der Saga-Zahlung (Korrelation für die Kompensation). */
    public static String vorgangsNrFuer(String sagaId) {
        return VORGANGS_NR_PRAEFIX + sagaId;
    }

    /** KdU-Parameter der Konfiguration (lazy, einmalig); Tests können injizieren. */
    static Leistungsberechnung.KduParameter kduParameter() {
        Leistungsberechnung.KduParameter parameter = kduParameterCache;
        if (parameter != null) {
            return parameter;
        }
        try {
            parameter = LmgConfig.load().kduParameter();
        } catch (RuntimeException e) {
            LOG.warning("KdU-Parameter nicht konfigurierbar — Berechnung ohne Angemessenheitsgrenze: "
                    + e.getMessage());
            parameter = Leistungsberechnung.KduParameter.AUS;
        }
        kduParameterCache = parameter;
        return parameter;
    }

    /** Injiziert die KdU-Parameter (Aufruf durch LmgServerMain beim Start bzw. Tests). */
    public static void setzeKduParameter(Leistungsberechnung.KduParameter parameter) {
        kduParameterCache = parameter;
    }

    // ------------------------------------------------------------------
    // Schritt 1: Vorgang erfassen
    // ------------------------------------------------------------------

    public static void vorgangErfassen(Connection c, String payload, String sagaId) throws SQLException {
        Map<String, Object> data = parsePayload(payload);
        String aktenzeichen = (String) data.get("aktenzeichen");
        String name = (String) data.get("hauptpersonName");
        BigDecimal budget = getBigDecimal(data.get("startBudget"));
        BigDecimal vermoegen = getBigDecimal(data.get("vermoegen"));
        String iban = (String) data.get("iban");
        String bic = (String) data.get("bic");
        int rbs = ((Number) data.get("regelbedarfsstufe")).intValue();
        BigDecimal miete = getBigDecimal(data.get("kduMiete"));
        BigDecimal betrieb = getBigDecimal(data.get("kduBetriebskosten"));
        BigDecimal heizung = getBigDecimal(data.get("kduHeizung"));
        BigDecimal rente = getBigDecimal(data.get("einkommenRente"));
        BigDecimal erwerb = getBigDecimal(data.get("einkommenErwerb"));

        long bgId = nextVal(c, "SEQ_BG");
        try (PreparedStatement stmt = c.prepareStatement(
                "INSERT INTO BEDARFSGEMEINSCHAFT (ID, AKTENZEICHEN, HAUPTPERSON_NAME, STATUS_AKTIV, BUDGET_REST, "
                        + "VERMOEGEN, IBAN, BIC, KONTOINHABER) "
                        + "VALUES (?, ?, ?, 1, ?, ?, ?, ?, ?)")) {
            stmt.setLong(1, bgId);
            stmt.setString(2, aktenzeichen);
            stmt.setString(3, name);
            stmt.setBigDecimal(4, budget);
            stmt.setBigDecimal(5, vermoegen);
            stmt.setString(6, iban);
            stmt.setString(7, bic);
            stmt.setString(8, name);
            stmt.executeUpdate();
        }

        long fallId = nextVal(c, "SEQ_FALL");
        try (PreparedStatement stmt = c.prepareStatement(
                "INSERT INTO SOZIAL_FALL (ID, BG_ID, REGELBEDARFSSTUFE, FLAG_MERKZEICHEN_G, FLAG_WARMWASSER, "
                        + "KDU_MIETE, KDU_BETRIEBSKOSTEN, KDU_HEIZUNG, EINKOMMEN_RENTE, EINKOMMEN_ERWERB) "
                        + "VALUES (?, ?, ?, 0, 0, ?, ?, ?, ?, ?)")) {
            stmt.setLong(1, fallId);
            stmt.setLong(2, bgId);
            stmt.setInt(3, rbs);
            stmt.setBigDecimal(4, miete);
            stmt.setBigDecimal(5, betrieb);
            stmt.setBigDecimal(6, heizung);
            stmt.setBigDecimal(7, rente);
            stmt.setBigDecimal(8, erwerb);
            stmt.executeUpdate();
        }
    }

    /**
     * Kompensation von Schritt 1: Der Vorgang wird HISTORISIERT (STATUS_AKTIV = 0), nicht
     * gelöscht. Gründe (Fund K3):
     * <ul>
     *   <li>BESCHEID (AUFGEHOBEN) und ZAHLUNG (STORNIERT) halten Fremdschlüssel auf die
     *       Bedarfsgemeinschaft — ein DELETE scheitert an ORA-02292, die Saga bliebe dauerhaft
     *       FAILED und bei jedem Wiederanlauf kaputt.</li>
     *   <li>Sozialdaten sind revisionssicher zu führen: Nach § 67 SGB X muss die Ablehnung
     *       eines Antrags nachvollziehbar bleiben. Die Bedarfsgemeinschaft und ihre
     *       Sozialfälle bleiben daher erhalten, werden aber inaktiv gesetzt und sind damit
     *       für alle Fachoperationen (Anlage, Auszahlung, Bescheid) gesperrt.</li>
     *   <li>Das Aktenzeichen (UQ) bleibt belegt — fachlich korrekt, da der Vorgang real
     *       existiert hat und nicht „ungeschehen" gemacht werden darf.</li>
     * </ul>
     * Idempotent: bereits historisierte Vorgänge werden nicht erneut geändert.
     */
    public static void vorgangKompensieren(Connection c, String payload, String sagaId) throws SQLException {
        Map<String, Object> data = parsePayload(payload);
        String aktenzeichen = (String) data.get("aktenzeichen");
        Long bgId = findBgId(c, aktenzeichen);
        if (bgId == null) {
            return; // Vorgang wurde nie angelegt — nichts zu kompensieren
        }
        try (PreparedStatement stmt = c.prepareStatement(
                "UPDATE BEDARFSGEMEINSCHAFT SET STATUS_AKTIV = 0 WHERE ID = ? AND STATUS_AKTIV = 1")) {
            stmt.setLong(1, bgId);
            int geaendert = stmt.executeUpdate();
            if (geaendert == 1) {
                schreibeJournal(c, bgId, "Vorgang historisiert (Saga-Kompensation): Antrag abgelehnt bzw. "
                        + "abgebrochen — Sozialdaten bleiben aus Gründen der Nachvollziehbarkeit "
                        + "(§ 67 SGB X) erhalten", "saga-engine");
            }
        }
    }

    // ------------------------------------------------------------------
    // Schritt 2: Bedarfsprüfung
    // ------------------------------------------------------------------

    public static void bedarfspruefung(Connection c, String payload, String sagaId) throws SQLException {
        Map<String, Object> data = parsePayload(payload);
        Long bgId = findeBgIdOderFehler(c, (String) data.get("aktenzeichen"));
        List<SozialFall> faelle = leseFaelle(c, bgId);
        BigDecimal vermoegen = leseVermoegen(c, bgId);
        de.allgemein.lmg.calc.Satzstand satzstand =
                SATZSTAND_DAO.findeFuerMonat(c, (String) data.get("zahlungsmonat"));

        Leistungsberechnung.Berechnungsergebnis ergebnis =
                Leistungsberechnung.berechne(bgId, faelle, kduParameter(), vermoegen, satzstand);

        if (ergebnis.zahlbetrag().signum() <= 0) {
            throw new FachlicheException(FachlicheException.KEIN_ANSPRUCH,
                    "Kein Leistungsanspruch nach SGB XII: Einkommen/Vermögen übersteigt Gesamtbedarf "
                            + "(Zahlbetrag: " + ergebnis.zahlbetrag() + " EUR)");
        }
    }

    /**
     * Kompensation von Schritt 2: Die Bedarfsprüfung selbst hinterlässt keine persistenten
     * Fachdaten; der Journal-Eintrag dokumentiert die Rücknahme. Eine wiederholte Ausführung
     * (IN_FLIGHT gilt beim Wiederanlauf als möglicherweise committet) erzeugt höchstens einen
     * weiteren rein dokumentierenden Journaleintrag — keine Fachwirkung.
     */
    public static void bedarfspruefungKompensieren(Connection c, String payload, String sagaId) throws SQLException {
        Map<String, Object> data = parsePayload(payload);
        String aktenzeichen = (String) data.get("aktenzeichen");
        Long bgId = findBgId(c, aktenzeichen);
        if (bgId != null) {
            schreibeJournal(c, bgId, "Bedarfspruefung durch Saga-Kompensation zurueckgenommen", "saga-engine");
        }
    }

    // ------------------------------------------------------------------
    // Schritt 3: Bescheid erteilen
    // ------------------------------------------------------------------

    public static void bescheidErteilen(Connection c, String payload, String sagaId) throws SQLException {
        Map<String, Object> data = parsePayload(payload);
        String aktenzeichen = (String) data.get("aktenzeichen");
        Long bgId = findeBgIdOderFehler(c, aktenzeichen);

        List<SozialFall> faelle = leseFaelle(c, bgId);
        BigDecimal vermoegen = leseVermoegen(c, bgId);
        String monatVon = (String) data.get("zahlungsmonat");
        String monatBis = data.containsKey("bewilligungBis") ? (String) data.get("bewilligungBis") : monatVon;
        // § 44 Abs. 3 SGB XII: zwölf Monate — dieselbe Prüfung wie auf dem Service-Weg.
        SozialLeistungsService.pruefeBewilligungszeitraum(monatVon, monatBis);
        de.allgemein.lmg.calc.Satzstand satzstand = SATZSTAND_DAO.findeFuerMonat(c, monatVon);
        Leistungsberechnung.Berechnungsergebnis ergebnis =
                Leistungsberechnung.berechne(bgId, faelle, kduParameter(), vermoegen, satzstand);

        long bescheidId = nextVal(c, "SEQ_BESCHEID");
        String sb = data.containsKey("sachbearbeiterId") ? (String) data.get("sachbearbeiterId") : "saga-engine";
        LocalDate bescheidDatum = LocalDate.now();
        String bescheidNr = "BESCH-" + aktenzeichen + "-" + bescheidDatum.getYear() + "-" + bescheidId;

        try (PreparedStatement stmt = c.prepareStatement(
                "INSERT INTO BESCHEID (ID, BG_ID, BESCHEID_NR, BESCHEID_DATUM, BEWILLIGUNG_VON, BEWILLIGUNG_BIS, "
                        + "ZAHLBETRAG, STATUS, ERSTELLT_VON, ERSTELLT_AM, BERECHNUNG) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, 'ERTEILT', ?, SYSTIMESTAMP, ?)")) {
            stmt.setLong(1, bescheidId);
            stmt.setLong(2, bgId);
            stmt.setString(3, bescheidNr);
            stmt.setDate(4, java.sql.Date.valueOf(bescheidDatum));
            stmt.setString(5, monatVon);
            stmt.setString(6, monatBis);
            stmt.setBigDecimal(7, ergebnis.zahlbetrag());
            stmt.setString(8, sb);
            stmt.setString(9, BerechnungsSnapshot.schreibe(ergebnis, faelle, vermoegen));
            stmt.executeUpdate();
        }
        schreibeJournal(c, bgId, "Bescheid " + bescheidNr + " ueber " + ergebnis.zahlbetrag() + " EUR erteilt", sb);
    }

    /**
     * Kompensation von Schritt 3: Bescheid aufheben (Guard nur ERTEILT → AUFGEHOBEN,
     * idempotent). Der aufgehobene Bescheid bleibt aus Gründen der Nachvollziehbarkeit
     * (§ 67 SGB X) erhalten.
     */
    public static void bescheidKompensieren(Connection c, String payload, String sagaId) throws SQLException {
        Map<String, Object> data = parsePayload(payload);
        String aktenzeichen = (String) data.get("aktenzeichen");
        Long bgId = findBgId(c, aktenzeichen);
        if (bgId == null) {
            return;
        }
        try (PreparedStatement stmt = c.prepareStatement(
                "UPDATE BESCHEID SET STATUS = 'AUFGEHOBEN' WHERE BG_ID = ? AND STATUS = 'ERTEILT'")) {
            stmt.setLong(1, bgId);
            int geaendert = stmt.executeUpdate();
            if (geaendert > 0) {
                schreibeJournal(c, bgId, "Bescheid aufgehoben (Saga-Kompensation)", "saga-engine");
            }
        }
    }

    // ------------------------------------------------------------------
    // Schritt 4: Erstauszahlung anweisen
    // ------------------------------------------------------------------

    /**
     * Schritt 4: Zahlung im Status ANGELEGT anlegen und das Restbudget mindern.
     *
     * <p>Vier-Augen-Prinzip (fachliche Korrektur): Die Saga legt die Zahlung bewusst nur
     * als ANGELEGT an und trägt KEINEN Freigeber ein — die Freigabe (ANGELEGT → BEREIT)
     * erfolgt über den normalen Endpunkt durch einen zweiten Sachbearbeiter, derselben
     * Regel folgend wie der manuelle Buchungsweg. Der Kassenlauf nimmt weiterhin nur
     * BEREIT-Zahlungen. Die VORGANGS_NR ist deterministisch aus der sagaId abgeleitet,
     * damit die Kompensation gezielt NUR die eigene Zahlung der Saga erfasst (Fund M1).</p>
     */
    public static void erstauszahlungAnweisen(Connection c, String payload, String sagaId) throws SQLException {
        Map<String, Object> data = parsePayload(payload);
        String aktenzeichen = (String) data.get("aktenzeichen");
        Long bgId = findeBgIdOderFehler(c, aktenzeichen);
        String monat = (String) data.get("zahlungsmonat");

        // Budget prüfen und sperren
        BigDecimal budgetRest;
        try (PreparedStatement stmt = c.prepareStatement(
                "SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ? FOR UPDATE")) {
            stmt.setLong(1, bgId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) throw new IllegalStateException("BG nicht vorhanden: " + bgId);
                budgetRest = rs.getBigDecimal(1);
            }
        }

        List<SozialFall> faelle = leseFaelle(c, bgId);
        BigDecimal vermoegen = leseVermoegen(c, bgId);
        de.allgemein.lmg.calc.Satzstand satzstand = SATZSTAND_DAO.findeFuerMonat(c, monat);
        Leistungsberechnung.Berechnungsergebnis ergebnis =
                Leistungsberechnung.berechne(bgId, faelle, kduParameter(), vermoegen, satzstand);
        BigDecimal zahlbetrag = ergebnis.zahlbetrag();
        BigDecimal vermoegenseinsatz = ergebnis.verwertbaresVermoegen().max(BigDecimal.ZERO);

        if (zahlbetrag.compareTo(budgetRest) > 0) {
            throw new FachlicheException(FachlicheException.BUDGET_ERSCHOEPFT,
                    "Zahlbetrag " + zahlbetrag + " EUR uebersteigt Restbudget " + budgetRest + " EUR");
        }

        // Fachregel als Vorprüfung (die Unique-Indizes UQ_ZAHLUNG_BG_MONAT_AKTIV und
        // UQ_ZAHLUNG_VORGANG bleiben die letzte Verteidigungslinie): keine zweite wirksame
        // Zahlung für dieselbe BG im selben Monat — als Fachfehler statt ORA-00001/500er.
        try (PreparedStatement stmt = c.prepareStatement(
                "SELECT COUNT(*) FROM ZAHLUNG WHERE BG_ID = ? AND ZAHLUNGSMONAT = ? "
                        + "AND ZAHLUNGSSTATUS <> 'STORNIERT'")) {
            stmt.setLong(1, bgId);
            stmt.setString(2, monat);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) {
                    throw new FachlicheException(FachlicheException.VALIDATION,
                            "Für diesen Monat existiert bereits eine wirksame Zahlung dieser Bedarfsgemeinschaft");
                }
            }
        }

        long zahlungId = nextVal(c, "SEQ_ZAHLUNG");
        String sb = data.containsKey("sachbearbeiterId") ? (String) data.get("sachbearbeiterId") : "saga-engine";
        String vorgangsNr = vorgangsNrFuer(sagaId);
        String iban = (String) data.get("iban");
        String bic = (String) data.get("bic");
        String name = (String) data.get("hauptpersonName");
        Long fallId = faelle.size() == 1 ? faelle.get(0).id() : null;

        try (PreparedStatement stmt = c.prepareStatement(
                "INSERT INTO ZAHLUNG (ID, BG_ID, FALL_ID, VORGANGS_NR, ZAHLUNGSMONAT, AUSZAHLUNGSBETRAG, "
                        + "ZAHLUNGSSTATUS, SACHBEARBEITER_ID, FREIGEGEBEN_VON, EMPFAENGER_IBAN, EMPFAENGER_BIC, "
                        + "EMPFAENGER_NAME, VERMOEGENSEINSATZ) "
                        + "VALUES (?, ?, ?, ?, ?, ?, 'ANGELEGT', ?, NULL, ?, ?, ?, ?)")) {
            stmt.setLong(1, zahlungId);
            stmt.setLong(2, bgId);
            if (fallId != null) {
                stmt.setLong(3, fallId);
            } else {
                stmt.setNull(3, java.sql.Types.NUMERIC);
            }
            stmt.setString(4, vorgangsNr);
            stmt.setString(5, monat);
            stmt.setBigDecimal(6, zahlbetrag);
            stmt.setString(7, sb);
            stmt.setString(8, iban);
            stmt.setString(9, bic);
            stmt.setString(10, name);
            stmt.setBigDecimal(11, vermoegenseinsatz);
            stmt.executeUpdate();
        }

        try (PreparedStatement stmt = c.prepareStatement(
                "UPDATE BEDARFSGEMEINSCHAFT SET BUDGET_REST = BUDGET_REST - ? WHERE ID = ?")) {
            stmt.setBigDecimal(1, zahlbetrag);
            stmt.setLong(2, bgId);
            stmt.executeUpdate();
        }

        // Verbrauchsfortschreibung (§ 90 SGB XII): der eingesetzte verwertbare
        // Vermögensanteil verbraucht sich mit dieser Zahlung.
        if (vermoegenseinsatz.signum() > 0) {
            try (PreparedStatement stmt = c.prepareStatement(
                    "UPDATE BEDARFSGEMEINSCHAFT SET VERMOEGEN = VERMOEGEN - ? "
                            + "WHERE ID = ? AND VERMOEGEN >= ?")) {
                stmt.setBigDecimal(1, vermoegenseinsatz);
                stmt.setLong(2, bgId);
                stmt.setBigDecimal(3, vermoegenseinsatz);
                if (stmt.executeUpdate() != 1) {
                    throw new IllegalStateException("Vermögensfortschreibung fehlgeschlagen für BG " + bgId);
                }
            }
        }

        schreibeJournal(c, bgId, "Erstauszahlung ueber " + zahlbetrag + " EUR fuer " + monat
                + " erfasst (Status: ANGELEGT, wartet auf Freigabe)"
                + (vermoegenseinsatz.signum() > 0
                        ? "; Vermögenseinsatz " + vermoegenseinsatz + " EUR verbucht (§ 90 SGB XII)" : ""),
                sb);
    }

    /**
     * Kompensation von Schritt 4 (Fund K2/H3/M1): Storniert NUR die eigene Zahlung der Saga
     * (Korrelation über die deterministische VORGANGS_NR) und bucht das Restbudget in
     * derselben Transaktion wieder hoch. Doppel-Gutschriften sind ausgeschlossen: Es zählen
     * nur Zeilen, die in DIESER Ausführung tatsächlich von ANGELEGT/BEREIT/UEBERGEBEN auf
     * STORNIERT gewandelt wurden (SELECT … FOR UPDATE, Summe merken, bedingtes UPDATE mit
     * Guard, erst danach Budget-Update). Eine bereits (z. B. manuell) stornierte Zahlung
     * führt zu einer No-Operation. War eine Zahlung bereits an die Kasse übergeben
     * (UEBERGEBEN), wird sie NICHT automatisch storniert und das Budget nicht gutgeschrieben:
     * Das Geld kann bereits ausgezahlt sein (ein Status AUSGEZOHLT existiert im Modell nicht).
     * Stattdessen entsteht ein ALARM-Journaleintrag zur manuellen Klärung mit der Kasse.
     */
    public static void erstauszahlungKompensieren(Connection c, String payload, String sagaId) throws SQLException {
        Map<String, Object> data = parsePayload(payload);
        String aktenzeichen = (String) data.get("aktenzeichen");
        Long bgId = findBgId(c, aktenzeichen);
        if (bgId == null) {
            return; // Vorgang wurde nie angelegt — nichts zu stornieren
        }
        String vorgangsNr = vorgangsNrFuer(sagaId);

        // 1. Eigene Zahlungen der Saga sperren und lesen (nur diese — niemals
        //    WHERE BG_ID = ?, sonst träfen fremde Zahlungen derselben BG).
        List<Long> ids = new ArrayList<>();
        List<BigDecimal> betraege = new ArrayList<>();
        List<BigDecimal> einsaetze = new ArrayList<>();
        List<String> statusse = new ArrayList<>();
        try (PreparedStatement stmt = c.prepareStatement(
                "SELECT ID, ZAHLUNGSSTATUS, AUSZAHLUNGSBETRAG, VERMOEGENSEINSATZ FROM ZAHLUNG "
                        + "WHERE VORGANGS_NR = ? AND ZAHLUNGSSTATUS IN ('ANGELEGT', 'BEREIT', 'UEBERGEBEN') "
                        + "FOR UPDATE")) {
            stmt.setString(1, vorgangsNr);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getLong(1));
                    statusse.add(rs.getString(2));
                    betraege.add(rs.getBigDecimal(3));
                    einsaetze.add(rs.getBigDecimal(4));
                }
            }
        }

        // 2. Bedingtes Storno je Zeile (Guard über den gelesenen Status) — nur tatsächlich
        //    gewandelte Zeilen gehen in die Gutschrift ein (Budget UND Vermögenseinsatz).
        BigDecimal gutzuschreiben = BigDecimal.ZERO;
        BigDecimal vermoegenZurueck = BigDecimal.ZERO;
        boolean warUebergeben = false;
        try (PreparedStatement storno = c.prepareStatement(
                "UPDATE ZAHLUNG SET ZAHLUNGSSTATUS = 'STORNIERT' WHERE ID = ? AND ZAHLUNGSSTATUS = ?")) {
            for (int i = 0; i < ids.size(); i++) {
                if ("UEBERGEBEN".equals(statusse.get(i))) {
                    warUebergeben = true;
                    continue;
                }
                storno.setLong(1, ids.get(i));
                storno.setString(2, statusse.get(i));
                if (storno.executeUpdate() == 1) {
                    gutzuschreiben = gutzuschreiben.add(betraege.get(i));
                    vermoegenZurueck = vermoegenZurueck.add(
                            einsaetze.get(i) == null ? BigDecimal.ZERO : einsaetze.get(i));
                }
            }
        }

        if (warUebergeben) {
            // Bei einem erneuten Wiederanlauf (erste Kompensation fehlgeschlagen) wird dieser
            // ALARM bewusst erneut journalisiert — ohne Dedup, damit jeder Versuch sichtbar
            // bleibt; die Fachwirkung (kein Storno, keine Gutschrift) bleibt idempotent.
            schreibeJournal(c, bgId, "ALARM: Die Zahlung (" + vorgangsNr
                    + ") der abgebrochenen Antrags-Saga wurde bereits an die Kasse uebergeben und NICHT storniert — "
                    + "manuelle Klaerung mit der Kasse erforderlich (Rueckholung bzw. Rueckforderung)",
                    "saga-engine");
        }
        if (gutzuschreiben.signum() <= 0) {
            return; // nichts zu stornieren (bereits storniert) — idempotente No-Operation
        }

        // 3. Budget-Rückbuchung in derselben Transaktion, BG-Zeile sperren (Fund K2).
        try (PreparedStatement lock = c.prepareStatement(
                "SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ? FOR UPDATE")) {
            lock.setLong(1, bgId);
            try (ResultSet rs = lock.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalStateException("BG nicht vorhanden: " + bgId);
                }
            }
        }
        try (PreparedStatement stmt = c.prepareStatement(
                "UPDATE BEDARFSGEMEINSCHAFT SET BUDGET_REST = BUDGET_REST + ? WHERE ID = ?")) {
            stmt.setBigDecimal(1, gutzuschreiben);
            stmt.setLong(2, bgId);
            stmt.executeUpdate();
        }
        if (vermoegenZurueck.signum() > 0) {
            try (PreparedStatement stmt = c.prepareStatement(
                    "UPDATE BEDARFSGEMEINSCHAFT SET VERMOEGEN = VERMOEGEN + ? WHERE ID = ?")) {
                stmt.setBigDecimal(1, vermoegenZurueck);
                stmt.setLong(2, bgId);
                stmt.executeUpdate();
            }
        }

        schreibeJournal(c, bgId, "Erstauszahlung storniert (Saga-Kompensation), Restbudget um "
                + gutzuschreiben + " EUR erhoeht"
                + (vermoegenZurueck.signum() > 0
                        ? ", Vermögenseinsatz " + vermoegenZurueck + " EUR zurueckgebucht" : ""),
                "saga-engine");
    }

    // ------------------------------------------------------------------
    // Hilfsmethoden
    // ------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parsePayload(String payload) {
        Object parsed = Json.parse(payload);
        if (!(parsed instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("Payload muss ein JSON-Objekt sein");
        }
        return (Map<String, Object>) map;
    }

    /**
     * Prüft die Eingabedaten vor dem Start der Saga, damit fehlerhafte Anträge als
     * Fachfehler (HTTP 400) abgewiesen werden statt als halb ausgeführte, kompensierte Saga.
     * Einschließlich Feldlängen passend zu ddl/lmg_schema.sql und der Zwölf-Monate-Prüfung
     * des § 44 Abs. 3 SGB XII.
     */
    public static void validierePayload(Map<String, Object> data) {
        for (String feld : List.of("aktenzeichen", "hauptpersonName")) {
            if (!(data.get(feld) instanceof String text) || text.isBlank()) {
                throw new FachlicheException(FachlicheException.VALIDATION, "Pflichtfeld fehlt: " + feld);
            }
        }
        pruefeLaenge(data, "aktenzeichen", 50);
        pruefeLaenge(data, "hauptpersonName", 100);
        pruefeLaenge(data, "iban", 34);
        pruefeLaenge(data, "bic", 11);
        pruefeLaenge(data, "sachbearbeiterId", 50);
        for (String feld : List.of("iban", "bic")) {
            if (data.get(feld) != null && !(data.get(feld) instanceof String)) {
                throw new FachlicheException(FachlicheException.VALIDATION, feld + " muss ein Text sein");
            }
        }
        if (!(data.get("regelbedarfsstufe") instanceof Number rbs)
                || rbs.doubleValue() != rbs.intValue() || rbs.intValue() < 1 || rbs.intValue() > 6) {
            throw new FachlicheException(FachlicheException.VALIDATION,
                    "Regelbedarfsstufe muss eine ganze Zahl zwischen 1 und 6 sein");
        }
        Object von = data.get("zahlungsmonat");
        if (!(von instanceof String vonText) || !Zahlungsmonat.isValid(vonText)) {
            throw new FachlicheException(FachlicheException.VALIDATION,
                    "Zahlungsmonat muss im Format YYYY-MM angegeben sein");
        }
        Object bis = data.get("bewilligungBis");
        if (bis != null && (!(bis instanceof String bisText) || !Zahlungsmonat.isValid(bisText)
                || bisText.compareTo(vonText) < 0)) {
            throw new FachlicheException(FachlicheException.VALIDATION,
                    "Bewilligung bis muss im Format YYYY-MM angegeben sein und darf nicht vor dem Zahlungsmonat liegen");
        }
        // § 44 Abs. 3 SGB XII — zwölf Monate, auf allen Bescheid-Pfaden erzwungen.
        String bisText = bis instanceof String s ? s : vonText;
        SozialLeistungsService.pruefeBewilligungszeitraum(vonText, bisText);
        for (String feld : List.of("startBudget", "vermoegen", "kduMiete", "kduBetriebskosten", "kduHeizung",
                "einkommenRente", "einkommenErwerb")) {
            try {
                getBigDecimal(data.get(feld));
            } catch (IllegalArgumentException e) {
                throw new FachlicheException(FachlicheException.VALIDATION, feld + ": " + e.getMessage());
            }
        }
    }

    private static void pruefeLaenge(Map<String, Object> data, String feld, int maxZeichen) {
        if (data.get(feld) instanceof String text && text.length() > maxZeichen) {
            throw new FachlicheException(FachlicheException.VALIDATION,
                    feld + " ist mit " + text.length() + " Zeichen zu lang (maximal " + maxZeichen + ")");
        }
    }

    /** Betrag mit zwei Nachkommastellen (kaufmännisch gerundet); negativ, zu groß oder keine Zahl → Fehler. */
    private static BigDecimal getBigDecimal(Object val) {
        if (val == null) return BigDecimal.ZERO.setScale(2);
        BigDecimal betrag;
        try {
            if (val instanceof BigDecimal bd) {
                betrag = bd;
            } else if (val instanceof Number n) {
                betrag = new BigDecimal(String.valueOf(n));
            } else {
                betrag = new BigDecimal(String.valueOf(val).trim());
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("ist keine gültige Zahl");
        }
        if (betrag.signum() < 0) {
            throw new IllegalArgumentException("darf nicht negativ sein");
        }
        BigDecimal gerundet = betrag.setScale(2, java.math.RoundingMode.HALF_UP);
        if (gerundet.precision() - gerundet.scale() > 8) {
            throw new IllegalArgumentException("ist zu groß (maximal 99.999.999,99 EUR)");
        }
        return gerundet;
    }

    private static Long findBgId(Connection c, String aktenzeichen) throws SQLException {
        try (PreparedStatement stmt = c.prepareStatement(
                "SELECT ID FROM BEDARFSGEMEINSCHAFT WHERE AKTENZEICHEN = ?")) {
            stmt.setString(1, aktenzeichen);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getLong(1);
                return null;
            }
        }
    }

    private static Long findeBgIdOderFehler(Connection c, String aktenzeichen) throws SQLException {
        Long bgId = findBgId(c, aktenzeichen);
        if (bgId == null) {
            throw new IllegalStateException("Bedarfsgemeinschaft nicht gefunden: " + aktenzeichen);
        }
        return bgId;
    }

    /** Liest alle SozialFälle der BG aus dem Datenbestand (Berechnungsbasis der Schritte 2–4). */
    private static List<SozialFall> leseFaelle(Connection c, long bgId) throws SQLException {
        List<SozialFall> faelle = new ArrayList<>();
        try (PreparedStatement stmt = c.prepareStatement(
                "SELECT ID, BG_ID, REGELBEDARFSSTUFE, FLAG_MERKZEICHEN_G, FLAG_SCHWANGER, ANZ_KINDER_U7, "
                        + "ANZ_KINDER_U16, ANZ_KINDER_MINDERJ, FLAG_WARMWASSER, KDU_MIETE, KDU_BETRIEBSKOSTEN, "
                        + "KDU_HEIZUNG, EINKOMMEN_RENTE, EINKOMMEN_ERWERB, ABZ_STEUERN, ABZ_SV, ABZ_VERSICHERUNGEN, "
                        + "ABZ_ERWERBSAUSGABEN, ENTBINDUNGSMONAT, VERSION FROM SOZIAL_FALL WHERE BG_ID = ? ORDER BY ID")) {
            stmt.setLong(1, bgId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    faelle.add(new SozialFall(
                            rs.getLong(1), rs.getLong(2), rs.getInt(3),
                            rs.getInt(4) == 1, rs.getInt(5) == 1,
                            rs.getInt(6), rs.getInt(7), rs.getInt(8), rs.getInt(9) == 1,
                            rs.getBigDecimal(10), rs.getBigDecimal(11), rs.getBigDecimal(12),
                            rs.getBigDecimal(13), rs.getBigDecimal(14),
                            rs.getBigDecimal(15), rs.getBigDecimal(16), rs.getBigDecimal(17), rs.getBigDecimal(18),
                            rs.getString(19),
                            rs.getLong(20)));
                }
            }
        }
        if (faelle.isEmpty()) {
            throw new FachlicheException(FachlicheException.VALIDATION,
                    "Die Bedarfsgemeinschaft " + bgId + " hat keinen SozialFall");
        }
        return faelle;
    }

    /** Liest das Gesamtvermögen der BG aus dem Datenbestand (§ 43 Abs. 1, § 90 SGB XII). */
    private static BigDecimal leseVermoegen(Connection c, long bgId) throws SQLException {
        try (PreparedStatement stmt = c.prepareStatement(
                "SELECT VERMOEGEN FROM BEDARFSGEMEINSCHAFT WHERE ID = ?")) {
            stmt.setLong(1, bgId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    BigDecimal wert = rs.getBigDecimal(1);
                    return wert == null ? BigDecimal.ZERO : wert;
                }
                return BigDecimal.ZERO;
            }
        }
    }

    private static long nextVal(Connection c, String sequence) throws SQLException {
        try (Statement stmt = c.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT " + sequence + ".NEXTVAL FROM DUAL")) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private static void schreibeJournal(Connection c, long bgId, String text, String sb) throws SQLException {
        long journalId = nextVal(c, "SEQ_JOURNAL");
        try (PreparedStatement stmt = c.prepareStatement(
                "INSERT INTO JOURNAL (ID, BG_ID, AKTION_TEXT, SACHBEARBEITER_ID, ERSTELLT_AM) "
                        + "VALUES (?, ?, ?, ?, SYSTIMESTAMP)")) {
            stmt.setLong(1, journalId);
            stmt.setLong(2, bgId);
            stmt.setString(3, text);
            stmt.setString(4, sb);
            stmt.executeUpdate();
        }
    }
}
