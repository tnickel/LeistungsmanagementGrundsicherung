package de.allgemein.lmg.service;

import de.allgemein.lmg.calc.BerechnungsSnapshot;
import de.allgemein.lmg.calc.Leistungsberechnung;
import de.allgemein.lmg.dao.BescheidDao;
import de.allgemein.lmg.dao.BedarfsgemeinschaftDao;
import de.allgemein.lmg.dao.JournalDao;
import de.allgemein.lmg.dao.SozialFallDao;
import de.allgemein.lmg.dao.ZahlungDao;
import de.allgemein.lmg.model.Bedarfsgemeinschaft;
import de.allgemein.lmg.model.Bescheid;
import de.allgemein.lmg.model.Journaleintrag;
import de.allgemein.lmg.model.SozialFall;
import de.allgemein.lmg.model.VorgangslisteEintrag;
import de.allgemein.lmg.model.Zahlung;
import de.allgemein.tx.TxContext;
import de.allgemein.tx.TxException;
import de.allgemein.tx.TxManager;
import de.allgemein.tx.TxOptions;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Fachschicht der Grundsicherungs-Leistungsverarbeitung. Alle Aktionen laufen als
 * kurze Oracle-Transaktionen über {@link TxManager} — damit Commit/Rollback und
 * Wiederholung bei Sperren-Konflikten zentral von der Transaktionssteuerung kommen.
 *
 * <p>Der kritische Buchungsweg ({@link #ordneAuszahlungAn}) folgt dem Blueprint:
 * Idempotenz über die Vorgangsnummer, pessimistische Zeilensperre auf die
 * Bedarfsgemeinschaft (SELECT ... FOR UPDATE), Budget-Prüfung, Anlage der Zahlung
 * mit Status ANGELEGT (Vier-Augen-Prinzip: die Freigabe erfolgt immer durch eine
 * zweite Person über {@link #freigebenZahlung}), Minderung des Restbudgets und
 * revisionssicherer Journaleintrag — alles unteilbar in einer Transaktion.</p>
 *
 * <p>Bedarfs- und Einkommensermittlung: §§ 27a, 28, 30, 35, 42a, 43a, 44, 82, 90
 * SGB XII (Details in {@link Leistungsberechnung}). Die örtlichen KdU-Parameter
 * (Warmwasser-Abzug von den Heizkosten, Angemessenheitsgrenze) und die
 * Rechtsbehelfsbelehrung des Bescheids sind konfigurierbar (LmgConfig).</p>
 */
public final class SozialLeistungsService implements LeistungsService {

    private final TxManager txManager;
    private final BedarfsgemeinschaftDao bgDao = new BedarfsgemeinschaftDao();
    private final SozialFallDao fallDao = new SozialFallDao();
    private final ZahlungDao zahlungDao = new ZahlungDao();
    private final JournalDao journalDao = new JournalDao();
    private final de.allgemein.lmg.dao.BescheidDao bescheidDao = new de.allgemein.lmg.dao.BescheidDao();
    private final Leistungsberechnung.KduParameter kduParameter;
    private final BescheidGenerator.Rechtsbehelf rechtsbehelf;

    public SozialLeistungsService(TxManager txManager) {
        this(txManager, Leistungsberechnung.KduParameter.AUS, null);
    }

    public SozialLeistungsService(TxManager txManager,
            Leistungsberechnung.KduParameter kduParameter,
            BescheidGenerator.Rechtsbehelf rechtsbehelf) {
        this.txManager = Objects.requireNonNull(txManager, "txManager");
        this.kduParameter = Objects.requireNonNull(
                kduParameter == null ? Leistungsberechnung.KduParameter.AUS : kduParameter, "kduParameter");
        this.rechtsbehelf = rechtsbehelf;
    }

    // ------------------------------------------------------------------
    // Lesezugriffe (kurze READ-COMMITTED-Transaktionen)
    // ------------------------------------------------------------------

    @Override
    public List<VorgangslisteEintrag> listeBedarfsgemeinschaften() {
        return txManager.execute(TxOptions.DEFAULT, ctx -> {
            List<VorgangslisteEintrag> eintraege = new ArrayList<>();
            for (Bedarfsgemeinschaft bg : bgDao.listeAlle(ctx)) {
                eintraege.add(new VorgangslisteEintrag(
                        bg.id(), bg.aktenzeichen(), bg.hauptpersonName(), bg.statusAktiv(),
                        bg.budgetRest(), bg.wiedervorlageAm(), zahlbetragVorschau(ctx, bg)));
            }
            return eintraege;
        });
    }

    @Override
    public Vorgangsdetail ladeDetail(long bgId) {
        return txManager.execute(TxOptions.DEFAULT, ctx -> {
            Bedarfsgemeinschaft bg = bgDao.finde(ctx, bgId)
                    .orElseThrow(() -> new FachlicheException(FachlicheException.NICHT_GEFUNDEN,
                            "Bedarfsgemeinschaft " + bgId + " ist nicht vorhanden"));
            List<SozialFall> faelle = fallDao.listeFuerBg(ctx, bgId);
            de.allgemein.lmg.model.Bescheid bescheid = bescheidDao.letzteFuerBg(ctx, bgId, 1)
                    .stream().findFirst().orElse(null);
            return new Vorgangsdetail(bg, faelle, berechnungDefensiv(bg, faelle),
                    zahlungDao.letzteFuerBg(ctx, bgId, 10),
                    journalDao.letzteFuerBg(ctx, bgId, 20),
                    bescheid);
        });
    }

    @Override
    public List<Zahlung> letzteZahlungen(int limit) {
        return txManager.execute(TxOptions.DEFAULT, ctx -> zahlungDao.letzte(ctx, limit));
    }

    @Override
    public List<Journaleintrag> letztesJournal(int limit) {
        return txManager.execute(TxOptions.DEFAULT, ctx -> journalDao.letzte(ctx, limit));
    }

    // ------------------------------------------------------------------
    // Maskenänderung (optimistisches Locking)
    // ------------------------------------------------------------------

    @Override
    public SozialFall aktualisiereSozialFall(SozialFall geaendert, long erwarteteVersion) {
        return aktualisiereSozialFall(geaendert, erwarteteVersion, SYSTEM_SACHBEARBEITER);
    }

    @Override
    public SozialFall aktualisiereSozialFall(SozialFall geaendert, long erwarteteVersion, String sachbearbeiterId) {
        SozialFall normalisiert = pruefeUndNormalisiereFall(geaendert);
        return txManager.execute(TxOptions.DEFAULT, ctx -> {
            SozialFall vorhandener = fallDao.finde(ctx, geaendert.id())
                    .orElseThrow(() -> new FachlicheException(FachlicheException.NICHT_GEFUNDEN,
                            "SozialFall " + geaendert.id() + " ist nicht vorhanden"));
            int geaenderteZeilen = fallDao.aktualisiere(ctx, normalisiert, erwarteteVersion);
            if (geaenderteZeilen == 0) {
                throw new FachlicheException(FachlicheException.KONKURRENZ,
                        "Der SozialFall wurde zwischenzeitlich durch einen anderen Bearbeiter geändert. "
                                + "Bitte die Maske neu laden (Datensatz wurde nicht überschrieben).");
            }
            // Journal-Zuordnung aus dem Datenbestand, nicht aus dem Request-Body
            journalDao.einfuegen(ctx, vorhandener.bgId(),
                    "SozialFall " + geaendert.id() + " geändert (Bedarfs-/Einkommensermittlung)",
                    sachbearbeiterId);
            return fallDao.finde(ctx, geaendert.id()).orElseThrow();
        });
    }

    @Override
    public Bedarfsgemeinschaft aktualisiereBedarfsgemeinschaft(
            long id, boolean statusAktiv, LocalDate wiedervorlageAm, long erwarteteVersion) {
        return txManager.execute(TxOptions.DEFAULT, ctx -> {
            Bedarfsgemeinschaft vorhandene = bgDao.finde(ctx, id)
                    .orElseThrow(() -> new FachlicheException(FachlicheException.NICHT_GEFUNDEN,
                            "Bedarfsgemeinschaft " + id + " ist nicht vorhanden"));
            int geaenderteZeilen = bgDao.aktualisiere(ctx, id, statusAktiv, wiedervorlageAm, erwarteteVersion);
            if (geaenderteZeilen == 0) {
                throw new FachlicheException(FachlicheException.KONKURRENZ,
                        "Der Vorgang wurde zwischenzeitlich durch einen anderen Bearbeiter geändert. "
                                + "Bitte die Maske neu laden (Datensatz wurde nicht überschrieben).");
            }
            journalDao.einfuegen(ctx, id, "Vorgangsdaten geändert (Status/Wiedervorlage)", SYSTEM_SACHBEARBEITER);
            return bgDao.finde(ctx, id).orElseThrow();
        });
    }

    @Override
    public Bedarfsgemeinschaft aktualisiereBedarfsgemeinschaft(
            long id, boolean statusAktiv, LocalDate wiedervorlageAm,
            String iban, String bic, String kontoinhaber, long erwarteteVersion) {
        return aktualisiereBedarfsgemeinschaft(id, statusAktiv, wiedervorlageAm, iban, bic, kontoinhaber,
                null, erwarteteVersion, SYSTEM_SACHBEARBEITER);
    }

    @Override
    public Bedarfsgemeinschaft aktualisiereBedarfsgemeinschaft(
            long id, boolean statusAktiv, LocalDate wiedervorlageAm,
            String iban, String bic, String kontoinhaber, BigDecimal vermoegen,
            long erwarteteVersion, String sachbearbeiterId) {
        validiereBankverbindung(iban, bic);
        String normIban = iban != null && !iban.isBlank() ? SepaValidator.normalisiereIban(iban) : null;
        String normBic = bic != null && !bic.isBlank() ? SepaValidator.normalisiereBic(bic) : null;
        String inhaber = kontoinhaber != null && !kontoinhaber.isBlank() ? kontoinhaber.trim() : null;
        BigDecimal vermoegenNormalisiert = vermoegen == null
                ? BigDecimal.ZERO.setScale(2)
                : normiereBetrag(vermoegen, "Vermögen");
        return txManager.execute(TxOptions.DEFAULT, ctx -> {
            int geaenderteZeilen;
            if (vermoegen == null) {
                // Vermögen unverändert lassen (alte Maske ohne Vermögensfeld)
                geaenderteZeilen = bgDao.aktualisiereVermoegenUnveraendert(ctx, id, statusAktiv,
                        wiedervorlageAm, normIban, normBic, inhaber, erwarteteVersion);
            } else {
                geaenderteZeilen = bgDao.aktualisiere(ctx, id, statusAktiv, wiedervorlageAm,
                        normIban, normBic, inhaber, vermoegenNormalisiert, erwarteteVersion);
            }
            if (geaenderteZeilen == 0) {
                if (bgDao.finde(ctx, id).isEmpty()) {
                    throw new FachlicheException(FachlicheException.NICHT_GEFUNDEN,
                            "Bedarfsgemeinschaft " + id + " ist nicht vorhanden");
                }
                throw new FachlicheException(FachlicheException.KONKURRENZ,
                        "Der Vorgang wurde zwischenzeitlich durch einen anderen Bearbeiter geändert. "
                                + "Bitte die Maske neu laden (Datensatz wurde nicht überschrieben).");
            }
            journalDao.einfuegen(ctx, id,
                    "Vorgangsdaten geändert (Status/Wiedervorlage/Bankverbindung/Vermögen)", sachbearbeiterId);
            return bgDao.finde(ctx, id).orElseThrow();
        });
    }

    // ------------------------------------------------------------------
    // Buchungsweg: Auszahlung anordnen (eine unteilbare Transaktion)
    // ------------------------------------------------------------------

    @Override
    public Auszahlungsergebnis ordneAuszahlungAn(Auszahlungsauftrag auftrag) {
        pruefeAuftrag(auftrag);
        return txManager.execute(TxOptions.DEFAULT, ctx -> {
            // 1. Idempotenz (schneller Pfad ohne Sperre): dieselbe Vorgangsnummer
            //    wurde evtl. schon gebucht (Netzwerkproblem, Doppelklick).
            Optional<Zahlung> vorhanden = zahlungDao.findeVorgangsNr(ctx, auftrag.vorgangsNr());
            if (vorhanden.isPresent()) {
                Zahlung zahlung = vorhanden.get();
                Bedarfsgemeinschaft bg = bgDao.finde(ctx, zahlung.bgId()).orElse(null);
                return new Auszahlungsergebnis(zahlung, bg, true);
            }

            // 2. Pessimistische Zeilensperre: parallele Sachbearbeiter buchen
            //    nacheinander; Deadlocks (ORA-00060) wiederholt die Transaktionssteuerung.
            Bedarfsgemeinschaft bg = bgDao.lockForUpdate(ctx, auftrag.bgId())
                    .orElseThrow(() -> new FachlicheException(FachlicheException.NICHT_GEFUNDEN,
                            "Bedarfsgemeinschaft " + auftrag.bgId() + " ist nicht vorhanden"));
            if (!bg.statusAktiv()) {
                throw new FachlicheException(FachlicheException.VALIDATION,
                        "Der Vorgang ist historisiert; Auszahlungen sind nicht mehr möglich");
            }

            // 2b. Idempotenz unter der Sperre erneut prüfen: ein paralleler Request mit
            //     derselben Vorgangsnummer könnte zwischen 1 und der Sperre committet haben.
            vorhanden = zahlungDao.findeVorgangsNr(ctx, auftrag.vorgangsNr());
            if (vorhanden.isPresent()) {
                Zahlung zahlung = vorhanden.get();
                return new Auszahlungsergebnis(zahlung, bgDao.finde(ctx, zahlung.bgId()).orElse(bg), true);
            }

            // 2c. Pro Bedarfsgemeinschaft und Monat nur eine wirksame Zahlung: verhindert die
            //     Doppelzahlung durch zwei verschiedene Vorgangsnummern (Storno gibt den Monat frei).
            if (zahlungDao.existiertAktiveFuerMonat(ctx, bg.id(), auftrag.zahlungsmonat())) {
                throw new FachlicheException(FachlicheException.VALIDATION,
                        "Für " + auftrag.zahlungsmonat() + " existiert bereits eine nicht stornierte Zahlung "
                                + "dieser Bedarfsgemeinschaft; ggf. zuerst stornieren");
            }

            // 3. Bedarf ermitteln (§§ 27a ff., 42a, 82, 90 SGB XII) und prüfen
            List<SozialFall> faelle = fallDao.listeFuerBg(ctx, bg.id());
            Leistungsberechnung.Berechnungsergebnis berechnung = Leistungsberechnung.berechne(
                    bg.id(), faelle, kduParameter, bg.vermoegen());
            BigDecimal zahlbetrag = berechnung.zahlbetrag();
            if (zahlbetrag.signum() <= 0) {
                throw new FachlicheException(FachlicheException.KEIN_ANSPRUCH,
                        "Einkommen/Vermögen übersteigen den Gesamtbedarf — kein Anspruch "
                                + "(Zahlbetrag " + betragText(zahlbetrag) + " EUR)");
            }
            if (zahlbetrag.compareTo(bg.budgetRest()) > 0) {
                throw new FachlicheException(FachlicheException.BUDGET_ERSCHOEPFT,
                        "Der Zahlbetrag " + betragText(zahlbetrag) + " EUR übersteigt das verfügbare "
                                + "Restbudget von " + betragText(bg.budgetRest()) + " EUR");
            }

            // 4. SEPA-Bankdaten ermitteln & validieren
            String empfIban = auftrag.empfaengerIban() != null && !auftrag.empfaengerIban().isBlank()
                    ? SepaValidator.normalisiereIban(auftrag.empfaengerIban())
                    : (bg.iban() != null ? SepaValidator.normalisiereIban(bg.iban()) : null);
            String empfBic = auftrag.empfaengerBic() != null && !auftrag.empfaengerBic().isBlank()
                    ? SepaValidator.normalisiereBic(auftrag.empfaengerBic())
                    : (bg.bic() != null ? SepaValidator.normalisiereBic(bg.bic()) : null);
            String empfName = auftrag.empfaengerName() != null && !auftrag.empfaengerName().isBlank()
                    ? auftrag.empfaengerName().trim()
                    : (bg.kontoinhaber() != null && !bg.kontoinhaber().isBlank()
                            ? bg.kontoinhaber().trim()
                            : bg.hauptpersonName());
            if (empfName != null && empfName.length() > 100) {
                empfName = empfName.substring(0, 100);
            }
            validiereBankverbindung(empfIban, empfBic);

            // 5. Vier-Augen-Prinzip ist serverseitig erzwungen: jede Zahlung startet
            //    als ANGELEGT und wird erst durch die Freigabe eines zweiten
            //    Sachbearbeiters (freigebenZahlung) zur Kasse bereitgestellt.
            long zahlungId = zahlungDao.naechsteId(ctx);
            Long einzigerFall = faelle.size() == 1 ? faelle.get(0).id() : null;
            Zahlung zahlung = zahlungDao.einfuegen(ctx, zahlungId, bg.id(), einzigerFall,
                    auftrag.vorgangsNr(), auftrag.zahlungsmonat(), zahlbetrag, "ANGELEGT",
                    auftrag.sachbearbeiterId(), null, empfIban, empfBic, empfName);

            // 6. Restanspruch der Bedarfsgemeinschaft mindern
            bgDao.mindereBudget(ctx, bg.id(), zahlbetrag);

            // 7. Revisionssicherer Journal-Eintrag
            journalDao.einfuegen(ctx, bg.id(), String.format(Locale.GERMAN,
                    "Auszahlung über %,.2f EUR für %s erfasst (Status: ANGELEGT, wartet auf Freigabe)",
                    zahlbetrag, auftrag.zahlungsmonat()), auftrag.sachbearbeiterId());

            Bedarfsgemeinschaft aktualisiert = bgDao.finde(ctx, bg.id()).orElse(bg);
            return new Auszahlungsergebnis(zahlung, aktualisiert, false);
        });
    }

    // ------------------------------------------------------------------
    // Betriebsfunktionen: Neuanlage, Kassenlauf, Storno, Freigabe
    // ------------------------------------------------------------------

    @Override
    public Bedarfsgemeinschaft legeBedarfsgemeinschaftAn(String aktenzeichen, String hauptpersonName,
            BigDecimal startBudget, LocalDate wiedervorlageAm, String sachbearbeiterId) {
        return legeBedarfsgemeinschaftAn(aktenzeichen, hauptpersonName, startBudget,
                wiedervorlageAm, null, null, null, null, sachbearbeiterId);
    }

    @Override
    public Bedarfsgemeinschaft legeBedarfsgemeinschaftAn(String aktenzeichen, String hauptpersonName,
            BigDecimal startBudget, LocalDate wiedervorlageAm,
            String iban, String bic, String kontoinhaber, String sachbearbeiterId) {
        return legeBedarfsgemeinschaftAn(aktenzeichen, hauptpersonName, startBudget,
                wiedervorlageAm, iban, bic, kontoinhaber, null, sachbearbeiterId);
    }

    @Override
    public Bedarfsgemeinschaft legeBedarfsgemeinschaftAn(String aktenzeichen, String hauptpersonName,
            BigDecimal startBudget, LocalDate wiedervorlageAm,
            String iban, String bic, String kontoinhaber, BigDecimal vermoegen,
            String sachbearbeiterId) {
        pruefeText(aktenzeichen, "Aktenzeichen", 50);
        pruefeText(hauptpersonName, "Hauptperson", 100);
        BigDecimal budget = normiereBetrag(startBudget, "Startbudget");
        BigDecimal vermoegenNormalisiert = vermoegen == null
                ? BigDecimal.ZERO.setScale(2) : normiereBetrag(vermoegen, "Vermögen");
        validiereBankverbindung(iban, bic);
        String normIban = iban != null && !iban.isBlank() ? SepaValidator.normalisiereIban(iban) : null;
        String normBic = bic != null && !bic.isBlank() ? SepaValidator.normalisiereBic(bic) : null;
        String inhaber = kontoinhaber != null && !kontoinhaber.isBlank() ? kontoinhaber.trim() : null;

        try {
            return txManager.execute(TxOptions.DEFAULT, ctx -> {
                long id = bgDao.naechsteId(ctx);
                Bedarfsgemeinschaft bg = bgDao.einfuegen(ctx, id, aktenzeichen.trim(),
                        hauptpersonName.trim(), budget, wiedervorlageAm,
                        normIban, normBic, inhaber, vermoegenNormalisiert);
                journalDao.einfuegen(ctx, id, "Vorgang " + aktenzeichen.trim() + " angelegt", sachbearbeiterId);
                return bg;
            });
        } catch (TxException e) {
            if (e.oracleErrorCode() == 1) { // ORA-00001: Aktenzeichen bereits vorhanden
                throw new FachlicheException(FachlicheException.VALIDATION,
                        "Das Aktenzeichen '" + aktenzeichen.trim() + "' ist bereits vorhanden");
            }
            throw e;
        }
    }

    @Override
    public SozialFall legeSozialFallAn(long bgId, SozialFall entwurf, String sachbearbeiterId) {
        SozialFall normalisiert = pruefeUndNormalisiereFall(entwurf);
        return txManager.execute(TxOptions.DEFAULT, ctx -> {
            Bedarfsgemeinschaft bg = bgDao.finde(ctx, bgId)
                    .orElseThrow(() -> new FachlicheException(FachlicheException.NICHT_GEFUNDEN,
                            "Bedarfsgemeinschaft " + bgId + " ist nicht vorhanden"));
            if (!bg.statusAktiv()) {
                throw new FachlicheException(FachlicheException.VALIDATION,
                        "Der Vorgang ist historisiert; SozialFälle können nicht mehr angelegt werden");
            }
            long id = fallDao.naechsteId(ctx);
            fallDao.einfuegen(ctx, id, normalisiertMitId(bgId, id, normalisiert));
            journalDao.einfuegen(ctx, bgId, "SozialFall " + id + " angelegt", sachbearbeiterId);
            return fallDao.finde(ctx, id).orElseThrow();
        });
    }

    private SozialFall normalisiertMitId(long bgId, long id, SozialFall fall) {
        return new SozialFall(id, bgId, fall.regelbedarfsstufe(), fall.merkzeichenG(), fall.schwanger(),
                fall.anzKinderUnter7(), fall.anzKinderUnter16(), fall.anzKinderMinderjaehrig(), fall.warmwasser(),
                fall.kduMiete(), fall.kduBetriebskosten(), fall.kduHeizung(),
                fall.einkommenRente(), fall.einkommenErwerb(),
                fall.abzSteuern(), fall.abzSozialversicherung(), fall.abzVersicherungen(),
                fall.abzErwerbsaufwendungen(), 1L);
    }

    @Override
    public Zahlung freigebenZahlung(long zahlungId, String freigeberId) {
        return freigebenZahlung(zahlungId, freigeberId, false);
    }

    @Override
    public Zahlung freigebenZahlung(long zahlungId, String freigeberId, boolean freigeberIstAdmin) {
        pruefeText(freigeberId, "Freigebender", 50);
        return txManager.execute(TxOptions.DEFAULT, ctx -> {
            Zahlung zahlung = zahlungDao.lockForUpdate(ctx, zahlungId)
                    .orElseThrow(() -> new FachlicheException(FachlicheException.NICHT_GEFUNDEN,
                            "Zahlung " + zahlungId + " ist nicht vorhanden"));
            if (!"ANGELEGT".equals(zahlung.status())) {
                throw new FachlicheException(FachlicheException.VALIDATION,
                        "Nur angelegte Zahlungen können freigegeben werden (aktueller Status: "
                                + zahlung.status() + ")");
            }
            // Vier-Augen-Prinzip: Erfasser und Freigebender dürfen nicht identisch sein;
            // ein Administrator mit Rolle admin darf ausnahmsweise selbst feststellen
            // (Sonderrolle über die ROLLE, nicht über eine bestimmte Kennung).
            if (freigeberId.equalsIgnoreCase(zahlung.sachbearbeiterId()) && !freigeberIstAdmin) {
                throw new FachlicheException(FachlicheException.BERECHTIGUNG,
                        "Vier-Augen-Prinzip verletzt: Erfasser und Freigebender dürfen nicht identisch sein ("
                                + freigeberId + ")");
            }
            zahlungDao.freigeben(ctx, zahlungId, freigeberId);
            journalDao.einfuegen(ctx, zahlung.bgId(), String.format(Locale.GERMAN,
                    "Zahlung über %,.2f EUR (%s) durch %s freigegeben (rechnerisch festgestellt)",
                    zahlung.auszahlungsbetrag(), zahlung.zahlungsmonat(), freigeberId), freigeberId);
            return zahlungDao.finde(ctx, zahlungId).orElseThrow();
        });
    }

    @Override
    public Kassenlaeufergebnis uebergebeBereiteZahlungen(String sachbearbeiterId) {
        return txManager.execute(TxOptions.DEFAULT, ctx -> {
            List<Zahlung> bereite = zahlungDao.bereiteZahlungenGesperrt(ctx);
            if (bereite.isEmpty()) {
                return new Kassenlaeufergebnis(0, BigDecimal.ZERO.setScale(2));
            }
            BigDecimal summe = bereite.stream()
                    .map(Zahlung::auszahlungsbetrag)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            List<Long> ids = bereite.stream().map(Zahlung::id).toList();
            // Übergabe genau der gesperrten Menge: Anzahl und Summe bleiben konsistent
            int anzahl = zahlungDao.uebergebeGesperrte(ctx, ids);
            journalDao.einfuegen(ctx, null, String.format(Locale.GERMAN,
                    "Kassenlauf: %d Zahlungen über %,.2f EUR übergeben", anzahl, summe), sachbearbeiterId);
            return new Kassenlaeufergebnis(anzahl, summe);
        });
    }

    @Override
    public Zahlung storniereZahlung(long zahlungId, String sachbearbeiterId) {
        return txManager.execute(TxOptions.DEFAULT, ctx -> {
            // Zeilensperre auf die ZAHLUNG selbst: verhindert die Race mit einem
            // parallelen Kassenlauf (BEREIT -> UEBERGEBEN) oder einer parallelen Freigabe.
            Zahlung zahlung = zahlungDao.lockForUpdate(ctx, zahlungId)
                    .orElseThrow(() -> new FachlicheException(FachlicheException.NICHT_GEFUNDEN,
                            "Zahlung " + zahlungId + " ist nicht vorhanden"));
            if (!"BEREIT".equals(zahlung.status()) && !"ANGELEGT".equals(zahlung.status())) {
                throw new FachlicheException(FachlicheException.VALIDATION,
                        "Nur bereitgestellte oder angelegte Zahlungen können storniert werden (Status: "
                                + zahlung.status() + ")");
            }
            // Zeilensperre auch auf den Vorgang: kein Mischbetrieb mit einer laufenden Buchung
            bgDao.lockForUpdate(ctx, zahlung.bgId())
                    .orElseThrow(() -> new FachlicheException(FachlicheException.NICHT_GEFUNDEN,
                            "Bedarfsgemeinschaft " + zahlung.bgId() + " ist nicht vorhanden"));
            // Guarded Update: nur ANGELEGT/BEREIT -> STORNIERT; 0 Zeilen heißt Race verloren
            int geaendert = zahlungDao.aktualisiereStatus(ctx, zahlungId, "STORNIERT");
            if (geaendert == 0) {
                throw new FachlicheException(FachlicheException.KONKURRENZ,
                        "Die Zahlung wurde zwischenzeitlich geändert (z. B. an die Kasse übergeben); "
                                + "Storno abgebrochen, bitte neu prüfen");
            }
            bgDao.erhoehenBudget(ctx, zahlung.bgId(), zahlung.auszahlungsbetrag());
            journalDao.einfuegen(ctx, zahlung.bgId(), String.format(Locale.GERMAN,
                    "Auszahlung über %,.2f EUR (%s, Vorgang %s) storniert, Restbudget erhöht",
                    zahlung.auszahlungsbetrag(), zahlung.zahlungsmonat(), zahlung.vorgangsNr()),
                    sachbearbeiterId);
            return new Zahlung(zahlung.id(), zahlung.bgId(), zahlung.fallId(), zahlung.vorgangsNr(),
                    zahlung.zahlungsmonat(), zahlung.auszahlungsbetrag(), "STORNIERT", zahlung.erstelltAm(),
                    zahlung.sachbearbeiterId(), zahlung.freigegebenVon(),
                    zahlung.empfaengerIban(), zahlung.empfaengerBic(), zahlung.empfaengerName());
        });
    }

    @Override
    public Bescheid erteileBescheid(long bgId, String bewilligungVon, String bewilligungBis, String sachbearbeiterId) {
        pruefeMonat(bewilligungVon, "Bewilligung von");
        pruefeMonat(bewilligungBis, "Bewilligung bis");
        if (bewilligungVon.compareTo(bewilligungBis) > 0) {
            throw new FachlicheException(FachlicheException.VALIDATION,
                    "Bewilligungszeitraum ungültig: 'von' (" + bewilligungVon
                            + ") liegt nach 'bis' (" + bewilligungBis + ")");
        }
        pruefeBewilligungszeitraum(bewilligungVon, bewilligungBis);
        pruefeText(sachbearbeiterId, "Sachbearbeiter-Kürzel", 50);

        return txManager.execute(TxOptions.DEFAULT, ctx -> {
            Bedarfsgemeinschaft bg = bgDao.finde(ctx, bgId)
                    .orElseThrow(() -> new FachlicheException(FachlicheException.NICHT_GEFUNDEN,
                            "Bedarfsgemeinschaft " + bgId + " ist nicht vorhanden"));
            if (!bg.statusAktiv()) {
                throw new FachlicheException(FachlicheException.VALIDATION,
                        "Der Vorgang ist historisiert; es können keine Bescheide mehr erteilt werden");
            }
            List<SozialFall> faelle = fallDao.listeFuerBg(ctx, bgId);
            if (faelle.isEmpty()) {
                throw new FachlicheException(FachlicheException.VALIDATION,
                        "Bedarfsgemeinschaft hat keine Personen (SozialFälle)");
            }

            Leistungsberechnung.Berechnungsergebnis berechnung = Leistungsberechnung.berechne(
                    bgId, faelle, kduParameter, bg.vermoegen());
            if (berechnung.zahlbetrag().signum() <= 0) {
                throw new FachlicheException(FachlicheException.KEIN_ANSPRUCH,
                        "Kein Leistungsanspruch nach SGB XII: Einkommen/Vermögen decken den Gesamtbedarf "
                                + "(Zahlbetrag: " + betragText(berechnung.zahlbetrag()) + " EUR)");
            }

            long id = bescheidDao.naechsteId(ctx);
            String bescheidNr = "BESCH-" + bg.aktenzeichen() + "-" + LocalDate.now().getYear() + "-" + id;
            // BESCHEID_DATUM ist das Ausstellungsdatum; die Bekanntgabe (§ 37 SGB X), mit der die
            // Rechtsbehelfsfrist (1 Monat) beginnt, ist ein eigener, nicht erfasster Zeitpunkt.
            // Der Berechnungs-Snapshot hält den Stand der Erteilung fest: der Nachdruck bleibt
            // auch nach späteren Falländerungen beim erteilten Berechnungsstand.
            String snapshot = BerechnungsSnapshot.schreibe(berechnung, faelle, bg.vermoegen());
            Bescheid bescheid = bescheidDao.einfuegen(ctx, id, bgId, bescheidNr, LocalDate.now(),
                    bewilligungVon, bewilligungBis, berechnung.zahlbetrag(), sachbearbeiterId, snapshot);

            journalDao.einfuegen(ctx, bgId, String.format(Locale.GERMAN,
                    "Bewilligungsbescheid %s über %,.2f EUR monatlich (%s bis %s) erteilt",
                    bescheidNr, berechnung.zahlbetrag(), bewilligungVon, bewilligungBis), sachbearbeiterId);

            return bescheid;
        });
    }

    @Override
    public Optional<Bescheid> letzterBescheid(long bgId) {
        return txManager.execute(TxOptions.DEFAULT, ctx ->
                bescheidDao.letzteFuerBg(ctx, bgId, 1).stream().findFirst());
    }

    @Override
    public String generiereBescheidHtml(long bgId) {
        return txManager.execute(TxOptions.DEFAULT, ctx -> {
            Bedarfsgemeinschaft bg = bgDao.finde(ctx, bgId)
                    .orElseThrow(() -> new FachlicheException(FachlicheException.NICHT_GEFUNDEN,
                            "Bedarfsgemeinschaft " + bgId + " ist nicht vorhanden"));
            List<SozialFall> faelle = fallDao.listeFuerBg(ctx, bgId);
            Bescheid bescheid = bescheidDao.letzteFuerBg(ctx, bgId, 1).stream().findFirst()
                    .orElseThrow(() -> new FachlicheException(FachlicheException.NICHT_GEFUNDEN,
                            "Kein Bewilligungsbescheid für Bedarfsgemeinschaft " + bgId + " vorhanden"));

            // Snapshot aus dem Erteilungszeitpunkt: der Nachdruck zeigt ausschließlich den
            // gespeicherten Berechnungsstand, auch wenn die Fälle inzwischen geändert wurden.
            // Nur Bescheide ohne Snapshot (frühere Versionen) werden mit aktuellem Datenstand
            // neu berechnet — mit sichtbarem Hinweis im Dokument.
            if (bescheid.berechnung() != null) {
                BerechnungsSnapshot.Gelesen snapshot = BerechnungsSnapshot.lese(bescheid.berechnung());
                Bedarfsgemeinschaft bgZurBerechnung = new Bedarfsgemeinschaft(
                        bg.id(), bg.aktenzeichen(), bg.hauptpersonName(), bg.statusAktiv(),
                        bg.budgetRest(), bg.wiedervorlageAm(), bg.version(),
                        bg.iban(), bg.bic(), bg.kontoinhaber(), snapshot.vermoegen());
                return BescheidGenerator.generiereHtml(bgZurBerechnung, snapshot.faelle(),
                        snapshot.ergebnis(), bescheid, rechtsbehelf, false);
            }
            Leistungsberechnung.Berechnungsergebnis berechnung = berechnungDefensiv(bg, faelle);
            if (berechnung == null) {
                throw new FachlicheException(FachlicheException.VALIDATION,
                        "Für diese Bedarfsgemeinschaft kann keine Berechnung erstellt werden "
                                + "(keine oder ungültige Fälle)");
            }
            return BescheidGenerator.generiereHtml(bg, faelle, berechnung, bescheid, rechtsbehelf, true);
        });
    }

    // ------------------------------------------------------------------
    // Hilfsfunktionen
    // ------------------------------------------------------------------

    /** Sachbearbeiter-Kürzel für automatisch erzeugte Journal-Einträge. */
    public static final String SYSTEM_SACHBEARBEITER = "system";

    private BigDecimal zahlbetragVorschau(TxContext ctx, Bedarfsgemeinschaft bg) throws Exception {
        List<SozialFall> faelle = fallDao.listeFuerBg(ctx, bg.id());
        if (faelle.isEmpty()) {
            return null;
        }
        Leistungsberechnung.Berechnungsergebnis berechnung = berechnungDefensiv(bg, faelle);
        return berechnung == null ? null : berechnung.zahlbetrag();
    }

    /** Berechnung, die fachliche Fehlkonfiguration in "keine Vorschau" auflöst (Maske zeigt Hinweis). */
    private Leistungsberechnung.Berechnungsergebnis berechnungDefensiv(
            Bedarfsgemeinschaft bg, List<SozialFall> faelle) {
        try {
            return Leistungsberechnung.berechne(bg.id(), faelle, kduParameter,
                    bg.vermoegen());
        } catch (FachlicheException e) {
            return null;
        }
    }

    private void pruefeAuftrag(Auszahlungsauftrag auftrag) {
        pruefeText(auftrag.vorgangsNr(), "Vorgangsnummer", 100);
        pruefeText(auftrag.sachbearbeiterId(), "Sachbearbeiter-Kürzel", 50);
        if (!Zahlungsmonat.isValid(auftrag.zahlungsmonat())) {
            throw new FachlicheException(FachlicheException.VALIDATION,
                    "Zahlungsmonat muss im Format YYYY-MM angegeben sein");
        }
    }

    private void pruefeMonat(String wert, String feldname) {
        if (!Zahlungsmonat.isValid(wert)) {
            throw new FachlicheException(FachlicheException.VALIDATION,
                    feldname + " muss im Format YYYY-MM angegeben sein");
        }
    }

    /**
     * § 44 Abs. 3 SGB XII: Bewilligungszeitraum in der Regel zwölf Monate (bei
     * vorläufiger Entscheidung nach § 44a höchstens sechs). Längere Zeiträume sind
     * ausnahmsweise rechtlich möglich, hier aber bewusst nicht vorgesehen.
     */
    static void pruefeBewilligungszeitraum(String bewilligungVon, String bewilligungBis) {
        YearMonth von = YearMonth.parse(bewilligungVon);
        YearMonth bis = YearMonth.parse(bewilligungBis);
        int monate = (bis.getYear() - von.getYear()) * 12 + bis.getMonthValue() - von.getMonthValue() + 1;
        if (monate > 12) {
            throw new FachlicheException(FachlicheException.VALIDATION,
                    "Bewilligungszeitraum umfasst " + monate + " Monate (von " + bewilligungVon
                            + " bis " + bewilligungBis + "); der Regelfall des § 44 Abs. 3 SGB XII sind "
                            + "höchstens zwölf Monate (vorläufige Entscheidung nach § 44a: sechs)");
        }
    }

    /** Beträge und Mehrbedarfsmerkmale prüfen und auf Spaltenform normieren. */
    private SozialFall pruefeUndNormalisiereFall(SozialFall fall) {
        if (fall.regelbedarfsstufe() < 1 || fall.regelbedarfsstufe() > 6) {
            throw new FachlicheException(FachlicheException.VALIDATION,
                    "Regelbedarfsstufe muss zwischen 1 und 6 liegen");
        }
        if (fall.anzKinderUnter7() < 0 || fall.anzKinderUnter16() < 0 || fall.anzKinderMinderjaehrig() < 0
                || fall.anzKinderUnter7() > 99 || fall.anzKinderUnter16() > 99
                || fall.anzKinderMinderjaehrig() > 99) {
            throw new FachlicheException(FachlicheException.VALIDATION,
                    "Anzahl Kinder muss zwischen 0 und 99 liegen");
        }
        // Jede Altersgruppe enthält die engere: unter 7 ⊆ unter 16 ⊆ minderjährig
        int kinderU16 = Math.max(fall.anzKinderUnter16(), fall.anzKinderUnter7());
        int minderjaehrig = Math.max(fall.anzKinderMinderjaehrig(), kinderU16);
        return new SozialFall(fall.id(), fall.bgId(), fall.regelbedarfsstufe(),
                fall.merkzeichenG(), fall.schwanger(),
                fall.anzKinderUnter7(), kinderU16, minderjaehrig, fall.warmwasser(),
                normiereBetrag(fall.kduMiete(), "Kaltmiete"),
                normiereBetrag(fall.kduBetriebskosten(), "Kalte Betriebskosten"),
                normiereBetrag(fall.kduHeizung(), "Heizkosten"),
                normiereBetrag(fall.einkommenRente(), "Renteneinkünfte"),
                normiereBetrag(fall.einkommenErwerb(), "Erwerbseinkommen"),
                normiereBetrag(fall.abzSteuern(), "Absetzung Steuern"),
                normiereBetrag(fall.abzSozialversicherung(), "Absetzung Sozialversicherung"),
                normiereBetrag(fall.abzVersicherungen(), "Absetzung Versicherungen"),
                normiereBetrag(fall.abzErwerbsaufwendungen(), "Absetzung Erwerbsaufwendungen"),
                fall.version());
    }

    /**
     * Beträge: auf 2 Nachkommastellen normiert (NUMBER(10,2)), maximal 8 Vorkomma-
     * Stellen. Wichtig: Erst runden, dann die Grenze prüfen — sonst schlüpft z. B.
     * 99.999.999,999 durch die Prüfung und wird auf 100.000.000,00 aufgerundet
     * (ORA-01438 statt Fachfehler).
     */
    public static BigDecimal normiereBetrag(BigDecimal betrag, String feldname) {
        if (betrag == null) {
            return BigDecimal.ZERO.setScale(2);
        }
        if (betrag.signum() < 0) {
            throw new FachlicheException(FachlicheException.VALIDATION,
                    feldname + " darf nicht negativ sein");
        }
        BigDecimal normalisiert = betrag.setScale(2, RoundingMode.HALF_UP);
        if (normalisiert.precision() - normalisiert.scale() > 8) {
            throw new FachlicheException(FachlicheException.VALIDATION,
                    feldname + " ist zu groß (maximal 99.999.999,99 EUR)");
        }
        return normalisiert;
    }

    private void pruefeText(String wert, String feldname, int maxLaenge) {
        if (wert == null || wert.isBlank()) {
            throw new FachlicheException(FachlicheException.VALIDATION, feldname + " darf nicht leer sein");
        }
        if (wert.length() > maxLaenge) {
            throw new FachlicheException(FachlicheException.VALIDATION,
                    feldname + " darf höchstens " + maxLaenge + " Zeichen lang sein");
        }
    }

    private void validiereBankverbindung(String iban, String bic) {
        if (iban != null && !iban.isBlank()) {
            String normIban = SepaValidator.normalisiereIban(iban);
            if (!SepaValidator.isValidIban(normIban)) {
                throw new FachlicheException(FachlicheException.VALIDATION, "Ungültige IBAN");
            }
        }
        if (bic != null && !bic.isBlank()) {
            String normBic = SepaValidator.normalisiereBic(bic);
            if (!SepaValidator.isValidBic(normBic)) {
                throw new FachlicheException(FachlicheException.VALIDATION, "Ungültiger BIC");
            }
        }
    }

    private static String betragText(BigDecimal betrag) {
        return String.format(Locale.GERMAN, "%,.2f", betrag);
    }
}
