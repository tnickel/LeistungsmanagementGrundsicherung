package de.allgemein.lmg.service;

import de.allgemein.lmg.calc.Leistungsberechnung;
import de.allgemein.lmg.dao.BedarfsgemeinschaftDao;
import de.allgemein.lmg.dao.JournalDao;
import de.allgemein.lmg.dao.SozialFallDao;
import de.allgemein.lmg.dao.ZahlungDao;
import de.allgemein.lmg.model.Bedarfsgemeinschaft;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Fachschicht der Grundsicherungs-Leistungsverarbeitung. Alle Aktionen laufen als
 * kurze Oracle-Transaktionen über {@link TxManager} — damit Commit/Rollback und
 * Wiederholung bei Sperren-Konflikten zentral von der Transaktionssteuerung kommen.
 *
 * <p>Der kritische Buchungsweg ({@link #ordneAuszahlungAn}) folgt dem Blueprint:
 * Idempotenz über die Vorgangsnummer, pessimistische Zeilensperre auf die
 * Bedarfsgemeinschaft (SELECT ... FOR UPDATE), Budget-Prüfung, Anlage der Zahlung
 * mit Status BEREIT, Minderung des Restbudgets und revisionssicherer Journaleintrag —
 * alles unteilbar in einer Transaktion.</p>
 */
public final class SozialLeistungsService implements LeistungsService {

    private final TxManager txManager;
    private final BedarfsgemeinschaftDao bgDao = new BedarfsgemeinschaftDao();
    private final SozialFallDao fallDao = new SozialFallDao();
    private final ZahlungDao zahlungDao = new ZahlungDao();
    private final JournalDao journalDao = new JournalDao();

    public SozialLeistungsService(TxManager txManager) {
        this.txManager = txManager;
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
            return new Vorgangsdetail(bg, faelle, berechnungDefensiv(bgId, faelle),
                    zahlungDao.letzteFuerBg(ctx, bgId, 10),
                    journalDao.letzteFuerBg(ctx, bgId, 20));
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
        SozialFall normalisiert = pruefeUndNormalisiereFall(geaendert);
        return txManager.execute(TxOptions.DEFAULT, ctx -> {
            int geaenderteZeilen = fallDao.aktualisiere(ctx, normalisiert, erwarteteVersion);
            if (geaenderteZeilen == 0) {
                if (fallDao.finde(ctx, geaendert.id()).isEmpty()) {
                    throw new FachlicheException(FachlicheException.NICHT_GEFUNDEN,
                            "SozialFall " + geaendert.id() + " ist nicht vorhanden");
                }
                throw new FachlicheException(FachlicheException.KONKURRENZ,
                        "Der SozialFall wurde zwischenzeitlich durch einen anderen Bearbeiter geändert. "
                                + "Bitte die Maske neu laden (Datensatz wurde nicht überschrieben).");
            }
            journalDao.einfuegen(ctx, geaendert.bgId(),
                    "SozialFall " + geaendert.id() + " geändert (Bedarfs-/Einkommensermittlung)",
                    SYSTEM_SACHBEARBEITER);
            return fallDao.finde(ctx, geaendert.id()).orElseThrow();
        });
    }

    @Override
    public Bedarfsgemeinschaft aktualisiereBedarfsgemeinschaft(
            long id, boolean statusAktiv, LocalDate wiedervorlageAm, long erwarteteVersion) {
        return txManager.execute(TxOptions.DEFAULT, ctx -> {
            int geaenderteZeilen = bgDao.aktualisiere(ctx, id, statusAktiv, wiedervorlageAm, erwarteteVersion);
            if (geaenderteZeilen == 0) {
                if (bgDao.finde(ctx, id).isEmpty()) {
                    throw new FachlicheException(FachlicheException.NICHT_GEFUNDEN,
                            "Bedarfsgemeinschaft " + id + " ist nicht vorhanden");
                }
                throw new FachlicheException(FachlicheException.KONKURRENZ,
                        "Der Vorgang wurde zwischenzeitlich durch einen anderen Bearbeiter geändert. "
                                + "Bitte die Maske neu laden (Datensatz wurde nicht überschrieben).");
            }
            journalDao.einfuegen(ctx, id, "Vorgangsdaten geändert (Status/Wiedervorlage)", SYSTEM_SACHBEARBEITER);
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
            // 1. Idempotenz: dieselbe Vorgangsnummer wurde evtl. schon gebucht
            //    (Netzwerkproblem, Doppelklick). Dann nichts mehr tun.
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

            // 3. Betrag ermitteln und prüfen
            List<SozialFall> faelle = fallDao.listeFuerBg(ctx, bg.id());
            Leistungsberechnung.Berechnungsergebnis berechnung = Leistungsberechnung.berechne(bg.id(), faelle);
            BigDecimal zahlbetrag = berechnung.zahlbetrag();
            if (zahlbetrag.signum() <= 0) {
                throw new FachlicheException(FachlicheException.KEIN_ANSPRUCH,
                        "Das anrechenbare Einkommen übersteigt den Gesamtbedarf — kein Anspruch "
                                + "(Zahlbetrag " + betragText(zahlbetrag) + " EUR)");
            }
            if (zahlbetrag.compareTo(bg.budgetRest()) > 0) {
                throw new FachlicheException(FachlicheException.BUDGET_ERSCHOEPFT,
                        "Der Zahlbetrag " + betragText(zahlbetrag) + " EUR übersteigt das verfügbare "
                                + "Restbudget von " + betragText(bg.budgetRest()) + " EUR");
            }

            // 4. Zahlungsdatensatz (Status BEREIT) anlegen
            long zahlungId = zahlungDao.naechsteId(ctx);
            Long einzigerFall = faelle.size() == 1 ? faelle.get(0).id() : null;
            Zahlung zahlung = zahlungDao.einfuegen(ctx, zahlungId, bg.id(), einzigerFall,
                    auftrag.vorgangsNr(), auftrag.zahlungsmonat(), zahlbetrag);

            // 5. Restanspruch der Bedarfsgemeinschaft mindern
            bgDao.mindereBudget(ctx, bg.id(), zahlbetrag);

            // 6. Revisionssicherer Journal-Eintrag (Vier-Augen-Prinzip)
            journalDao.einfuegen(ctx, bg.id(), String.format(Locale.GERMAN,
                    "Auszahlung über %,.2f EUR für %s angeordnet", zahlbetrag, auftrag.zahlungsmonat()),
                    auftrag.sachbearbeiterId());

            Bedarfsgemeinschaft aktualisiert = bgDao.finde(ctx, bg.id()).orElse(bg);
            return new Auszahlungsergebnis(zahlung, aktualisiert, false);
        });
    }

    // ------------------------------------------------------------------
    // Betriebsfunktionen: Neuanlage, Kassenlauf, Storno
    // ------------------------------------------------------------------

    @Override
    public Bedarfsgemeinschaft legeBedarfsgemeinschaftAn(String aktenzeichen, String hauptpersonName,
            BigDecimal startBudget, LocalDate wiedervorlageAm, String sachbearbeiterId) {
        pruefeText(aktenzeichen, "Aktenzeichen", 50);
        pruefeText(hauptpersonName, "Hauptperson", 100);
        BigDecimal budget = normiereBetrag(startBudget, "Startbudget");
        try {
            return txManager.execute(TxOptions.DEFAULT, ctx -> {
                long id = bgDao.naechsteId(ctx);
                Bedarfsgemeinschaft bg = bgDao.einfuegen(ctx, id, aktenzeichen.trim(),
                        hauptpersonName.trim(), budget, wiedervorlageAm);
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
            SozialFall fall = new SozialFall(id, bgId, normalisiert.regelbedarfsstufe(),
                    normalisiert.merkzeichenG(), normalisiert.kduMiete(), normalisiert.kduHeizung(),
                    normalisiert.einkommenRente(), normalisiert.einkommenErwerb(), 1L);
            fallDao.einfuegen(ctx, id, fall);
            journalDao.einfuegen(ctx, bgId, "SozialFall " + id + " angelegt", sachbearbeiterId);
            return fall;
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
            int anzahl = zahlungDao.uebergebeBereite(ctx);
            journalDao.einfuegen(ctx, null, String.format(Locale.GERMAN,
                    "Kassenlauf: %d Zahlungen über %,.2f EUR übergeben", anzahl, summe), sachbearbeiterId);
            return new Kassenlaeufergebnis(anzahl, summe);
        });
    }

    @Override
    public Zahlung storniereZahlung(long zahlungId, String sachbearbeiterId) {
        return txManager.execute(TxOptions.DEFAULT, ctx -> {
            Zahlung zahlung = zahlungDao.finde(ctx, zahlungId)
                    .orElseThrow(() -> new FachlicheException(FachlicheException.NICHT_GEFUNDEN,
                            "Zahlung " + zahlungId + " ist nicht vorhanden"));
            if (!"BEREIT".equals(zahlung.status())) {
                throw new FachlicheException(FachlicheException.VALIDATION,
                        "Nur bereitgestellte Zahlungen können storniert werden (Status: "
                                + zahlung.status() + ")");
            }
            // Zeilensperre auf den Vorgang: kein Mischbetrieb mit einer laufenden Buchung
            bgDao.lockForUpdate(ctx, zahlung.bgId())
                    .orElseThrow(() -> new FachlicheException(FachlicheException.NICHT_GEFUNDEN,
                            "Bedarfsgemeinschaft " + zahlung.bgId() + " ist nicht vorhanden"));
            zahlungDao.aktualisiereStatus(ctx, zahlungId, "STORNIERT");
            bgDao.erhoehenBudget(ctx, zahlung.bgId(), zahlung.auszahlungsbetrag());
            journalDao.einfuegen(ctx, zahlung.bgId(), String.format(Locale.GERMAN,
                    "Auszahlung über %,.2f EUR (%s, Vorgang %s) storniert, Restbudget erhöht",
                    zahlung.auszahlungsbetrag(), zahlung.zahlungsmonat(), zahlung.vorgangsNr()),
                    sachbearbeiterId);
            return new Zahlung(zahlung.id(), zahlung.bgId(), zahlung.fallId(), zahlung.vorgangsNr(),
                    zahlung.zahlungsmonat(), zahlung.auszahlungsbetrag(), "STORNIERT", zahlung.erstelltAm());
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
        return berechnungDefensiv(bg.id(), faelle).zahlbetrag();
    }

    /** Berechnung, die fachliche Fehlkonfiguration in "keine Vorschau" auflöst (Maske zeigt Hinweis). */
    private Leistungsberechnung.Berechnungsergebnis berechnungDefensiv(long bgId, List<SozialFall> faelle) {
        try {
            return Leistungsberechnung.berechne(bgId, faelle);
        } catch (FachlicheException e) {
            return null;
        }
    }

    private void pruefeAuftrag(Auszahlungsauftrag auftrag) {
        pruefeText(auftrag.vorgangsNr(), "Vorgangsnummer", 100);
        pruefeText(auftrag.sachbearbeiterId(), "Sachbearbeiter-Kürzel", 50);
        if (!Zahlungsmonat.isValid(auftrag.zahlungsmonat())) {
            throw new FachlicheException(FachlicheException.VALIDATION,
                    "Zahlungsmonat muss im Format YYYY-MM angegeben sein (erhalten: '"
                            + auftrag.zahlungsmonat() + "')");
        }
    }

    /** Beträge prüfen und auf NUMBER(10,2) normieren (2 Nachkommastellen, max. 8 Vorkommastellen). */
    private SozialFall pruefeUndNormalisiereFall(SozialFall fall) {
        if (fall.regelbedarfsstufe() < 1 || fall.regelbedarfsstufe() > 6) {
            throw new FachlicheException(FachlicheException.VALIDATION,
                    "Regelbedarfsstufe muss zwischen 1 und 6 liegen");
        }
        return new SozialFall(fall.id(), fall.bgId(), fall.regelbedarfsstufe(), fall.merkzeichenG(),
                normiereBetrag(fall.kduMiete(), "Kaltmiete"),
                normiereBetrag(fall.kduHeizung(), "Heizkosten"),
                normiereBetrag(fall.einkommenRente(), "Renteneinkünfte"),
                normiereBetrag(fall.einkommenErwerb(), "Erwerbseinkommen"),
                fall.version());
    }

    /** Beträge: maximal 8 Vorkomma-Stellen, auf 2 Nachkommastellen normiert (NUMBER(10,2)). */
    public static BigDecimal normiereBetrag(BigDecimal betrag, String feldname) {
        if (betrag == null) {
            return BigDecimal.ZERO.setScale(2);
        }
        if (betrag.signum() < 0) {
            throw new FachlicheException(FachlicheException.VALIDATION,
                    feldname + " darf nicht negativ sein");
        }
        if (betrag.precision() - betrag.scale() > 8) {
            throw new FachlicheException(FachlicheException.VALIDATION,
                    feldname + " ist zu groß (maximal 99.999.999,99 EUR)");
        }
        return betrag.setScale(2, RoundingMode.HALF_UP);
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

    private static String betragText(BigDecimal betrag) {
        return String.format(Locale.GERMAN, "%,.2f", betrag);
    }
}
