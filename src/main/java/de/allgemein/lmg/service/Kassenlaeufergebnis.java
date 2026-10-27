package de.allgemein.lmg.service;

import java.math.BigDecimal;

/**
 * Kurz gesagt: Das ist das Ergebnis des Kassenlaufs als Zahl.
 * Wie viele Zahlungen wurden an die Kasse übergeben und wie viel Geld
 * war das in Summe? — zwei Zahlen, mehr nicht.
 *
 * Ergebnis eines Kassenlaufs: Anzahl und Summe der von BEREIT auf UEBERGEBEN
 * gesetzten Zahlungen (0, wenn nichts bereit war).
 */
public record Kassenlaeufergebnis(int anzahl, BigDecimal summe) {
}