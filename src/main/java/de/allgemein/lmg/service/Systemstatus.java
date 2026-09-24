package de.allgemein.lmg.service;

/**
 * Momentaufnahme des Systemzustands für den Adminbereich: Erreichbarkeit der
 * Datenbank samt Kennung und Antwortzeit, Bestandszahlen (inkl. Sagastände für
 * den Wiederanlauf), Statistik der Task-Steuerung und Laufzeitumgebung.
 * {@code anzahlOffeneSagas} zählt RUNNING/COMPENSATING (Wiederanlauf nötig),
 * {@code anzahlAbgebrocheneSagas} zählt FAILED (abgebrochen; hier ist der
 * Wiederanlauf bereits gelaufen oder es gab nichts zu kompensieren).
 * {@code dbFehler} ist nur gesetzt, wenn die Datenbank nicht erreichbar ist.
 */
public record Systemstatus(
        boolean dbErreichbar,
        String dbFehler,
        String dbProdukt,
        String dbVersion,
        long dbAntwortMillis,
        String dbZeitpunkt,
        long anzahlVorgaenge,
        long anzahlSozialFaelle,
        long anzahlZahlungen,
        long anzahlOffeneSagas,
        long anzahlAbgebrocheneSagas,
        long tasksAktiv,
        long tasksErfolgreich,
        long tasksFehlgeschlagen,
        long tasksAbgebrochen,
        long tasksAbgelehnt,
        double tasksDurchschnittMillis,
        String javaVersion,
        String serverStart,
        long serverLaufzeitSekunden) {
}
