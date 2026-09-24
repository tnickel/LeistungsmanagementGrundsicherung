package de.allgemein.lmg.service;

import de.allgemein.lmg.calc.Leistungsberechnung;
import de.allgemein.lmg.model.Journaleintrag;
import de.allgemein.lmg.model.SozialFall;
import de.allgemein.lmg.model.Zahlung;

import java.util.List;

/**
 * Vollständiger Lesezuschnitt für die Vorgangsmaske: Kopfdaten, alle SozialFälle
 * samt berechneter Bedarfsermittlung, Zahlungshistorie und Journaleinträge.
 * {@code berechnung} kann null sein, wenn die Berechnung nicht möglich ist
 * (z. B. Regelbedarfsstufe nicht konfiguriert) — die Maske zeigt dann einen Hinweis.
 */
public record Vorgangsdetail(
        de.allgemein.lmg.model.Bedarfsgemeinschaft bedarfsgemeinschaft,
        List<SozialFall> faelle,
        Leistungsberechnung.Berechnungsergebnis berechnung,
        List<Zahlung> zahlungen,
        List<Journaleintrag> journal) {
}
