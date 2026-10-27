/**
 * JMX-Monitoring für die Transaktionssteuerung.
 *
 * <p>Dieses Paket meldet die Steuerung an die JVM-Verwaltungsstelle (JMX) an.
 * Damit kann man im Betrieb von außen nachsehen: Wie viele Aufträge laufen,
 * wie groß ist die Schlange, wie sieht der Verbindungspool aus?</p>
 *
 * <ul>
 *   <li><b>{@link de.allgemein.tx.monitoring.TxMonitoring}</b> — Anmeldung und
 *       Abmeldung der überwachten Objekte bei der Laufzeit.</li>
 *   <li><b>*Adapter</b> — die Brücke vom Fachobjekt zu den Werten, die nach
 *       außen sichtbar sein sollen.</li>
 *   <li><b>*MBean</b> — die Schnittstellen, die JMX-Tools wie JConsole lesen.</li>
 * </ul>
 */
package de.allgemein.tx.monitoring;
