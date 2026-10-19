package de.allgemein.testsweet;

/** Gemeinsame Standardumfänge; größere ausdrücklich gewählte Läufe bleiben erhalten. */
final class SimulatorDefaults {
    static final int ORDERS_PER_CATEGORY = 100;
    static final int LLM_CASES = 2 * ORDERS_PER_CATEGORY;

    private SimulatorDefaults() { }
}
