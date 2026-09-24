package de.allgemein.lmg.service;

import java.math.BigDecimal;

/**
 * Ergebnis eines Kassenlaufs: Anzahl und Summe der von BEREIT auf UEBERGEBEN
 * gesetzten Zahlungen (0, wenn nichts bereit war).
 */
public record Kassenlaeufergebnis(int anzahl, BigDecimal summe) {
}
