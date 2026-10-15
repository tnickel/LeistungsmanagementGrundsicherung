package de.allgemein.testsweet;

import de.allgemein.lmg.service.FachlicheException;
import de.allgemein.lmg.service.SozialLeistungsService;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Ein simulierter Sachbearbeiter: arbeitet in seinem eigenen Thread Aufträge aus
 * dem Pool ab — einer nach dem anderen, mit kurzer Denkzeit zwischen den
 * Arbeitsschritten (realistische Überlappung der Mitarbeiter unter Last).
 */
final class Mitarbeiter implements Runnable {

    final String kuerzel;
    private final Simulator simulator;
    private final long denkzeitMs;

    /** Wird vom Simulator für die GUI geführt. */
    volatile String aktuellerAuftrag;
    volatile int erledigt;
    volatile boolean aktiv;

    Mitarbeiter(Simulator simulator, String kuerzel, long denkzeitMs) {
        this.simulator = simulator;
        this.kuerzel = kuerzel;
        this.denkzeitMs = denkzeitMs;
    }

    @Override
    public void run() {
        aktiv = true;
        try {
            while (!simulator.abgebrochen()) {
                Auftrag auftrag = simulator.nimmAuftrag();
                if (auftrag == null) {
                    break; // Pool leer
                }
                try {
                    spiele(auftrag);
                } catch (Throwable failure) {
                    simulator.meldeWorkerAbsturz(auftrag, failure);
                    break;
                }
            }
        } finally {
            aktiv = false;
        }
    }

    /** Spielt alle Aktionen eines Auftrags durch und protokolliert jeden Schritt. */
    private void spiele(Auftrag auftrag) {
        aktuellerAuftrag = "#" + auftrag.id + " " + auftrag.aktenzeichen;
        auftrag.status = Auftrag.Status.IN_BEARBEITUNG;
        auftrag.bearbeiter = kuerzel;
        auftrag.startZeit = LocalDateTime.now();
        auftrag.startNano = System.nanoTime();
        Map<String, Object> kontext = new HashMap<>();
        SozialLeistungsService service = simulator.service(auftrag);
        String kollege = simulator.zufallsKollege(kuerzel);
        kontext.put("mitarbeiter", kuerzel);
        kontext.put("kollege", kollege);

        simulator.log().mitarbeiterEreignis(kuerzel,
                "nimmt Auftrag " + auftrag.id + " (" + auftrag.szenarioTyp
                        + ", " + auftrag.aktenzeichen + ") — Kollege für Freigaben: " + kollege);

        boolean fehlgeschlagen = false;
        for (SzenarioAktion aktion : auftrag.aktionen) {
            if (simulator.abgebrochen()) {
                schritt(auftrag, aktion.name(), "ABGEBROCHEN (Lauf wurde gestoppt)");
                fehlgeschlagen = true;
                break;
            }
            try {
                String detail = ausfuehrenMitZustandspruefung(auftrag, aktion, service, kollege, kontext);
                schritt(auftrag, aktion.name(), "OK" + (detail == null || detail.isBlank()
                        ? "" : " — " + detail));
                denkzeit();
            } catch (FachlicheException e) {
                if (aktion.akzeptiert(e)) {
                    schritt(auftrag, aktion.name(),
                            "ERWARTETER FEHLER (" + e.code() + ") — " + e.getMessage());
                    denkzeit();
                } else {
                    schritt(auftrag, aktion.name(),
                            "FEHLGESCHLAGEN (" + e.code() + ") — " + e.getMessage());
                    auftrag.fehlerText = e.code() + " in Schritt «" + aktion.name() + "»: " + e.getMessage();
                    simulator.log().fehler("MITARBEITER " + kuerzel,
                            "Auftrag " + auftrag.id + " (" + auftrag.aktenzeichen
                                    + ") fehlgeschlagen: " + auftrag.fehlerText);
                    fehlgeschlagen = true;
                    break;
                }
            } catch (Exception e) {
                schritt(auftrag, aktion.name(),
                        "FEHLGESCHLAGEN (" + e.getClass().getSimpleName() + ") — " + e.getMessage());
                auftrag.fehlerText = e.getClass().getSimpleName() + " in Schritt «"
                        + aktion.name() + "»: " + e.getMessage();
                simulator.log().fehler("MITARBEITER " + kuerzel,
                        "Auftrag " + auftrag.id + " (" + auftrag.aktenzeichen
                                + ") fehlgeschlagen: " + auftrag.fehlerText);
                fehlgeschlagen = true;
                break;
            }
        }

        auftrag.bgId = (Long) kontext.get("bgId");
        auftrag.kontext = Map.copyOf(kontext);
        auftrag.endeZeit = LocalDateTime.now();
        auftrag.endeNano = System.nanoTime();
        auftrag.status = fehlgeschlagen ? Auftrag.Status.FEHLGESCHLAGEN : Auftrag.Status.FERTIG_GESPIELT;
        if (!fehlgeschlagen) {
            erledigt++;
        }
        simulator.auftragSpielenBeendet(auftrag); // STRESS-Phasenbarriere
        aktuellerAuftrag = null;
        simulator.log().mitarbeiterEreignis(kuerzel,
                "Auftrag " + auftrag.id + " abgeschlossen: " + auftrag.status
                        + " (" + auftrag.dauerMs() + " ms)");
    }

    private void schritt(Auftrag auftrag, String aktion, String ergebnis) {
        Auftrag.Schritt schritt = new Auftrag.Schritt(LocalDateTime.now(), kuerzel, aktion, ergebnis);
        auftrag.protokolliere(schritt);
        simulator.log().auftragSchritt(auftrag, schritt);
    }

    private String ausfuehrenMitZustandspruefung(Auftrag auftrag, SzenarioAktion aktion,
            SozialLeistungsService service, String kollege, Map<String, Object> kontext) throws Exception {
        if (aktion.erwarteterFehler() == null) return aktion.execute(service, kuerzel, kollege, kontext);
        Fachzustand vorher = simulator.sichereFachzustand(auftrag);
        try {
            return aktion.execute(service, kuerzel, kollege, kontext);
        } finally {
            Fachzustand danach = simulator.sichereFachzustand(auftrag);
            var abweichungen = vorher.abweichungen(danach);
            var pruefung = new Auftrag.Pruefung("Fehlversuch ohne Fachänderung: " + aktion.name(),
                    "unverändert", abweichungen.isEmpty() ? "unverändert" : String.join("; ", abweichungen),
                    abweichungen.isEmpty(),
                    "Vorher-Prüfsumme " + vorher.pruefsumme() + ", Nachher-Prüfsumme " + danach.pruefsumme()
                            + " (SHA-256 über alle Fachtabellen, erlaubte Einsichts-Audits ausgeklammert; "
                            + "gleiche Prüfsumme = unverändert)",
                    "-- STATE_SNAPSHOT: SOZIAL_FALL, BEDARFSGEMEINSCHAFT, ZAHLUNG, BESCHEID, JOURNAL; vollständiger Vorher/Nachher-Vergleich", java.util.List.of());
            auftrag.ergaenzePruefung(pruefung);
            simulator.log().pruefung(auftrag, pruefung);
            vorher.verlangeUnveraendert(danach);
        }
    }

    private void denkzeit() {
        if (denkzeitMs <= 0) {
            return;
        }
        try {
            Thread.sleep(ThreadLocalRandom.current().nextLong(denkzeitMs / 2, denkzeitMs + 1));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
