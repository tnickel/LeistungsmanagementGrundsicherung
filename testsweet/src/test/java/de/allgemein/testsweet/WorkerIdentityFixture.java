package de.allgemein.testsweet;

import java.util.List;
import java.util.Map;

/** Explizite Laufidentitäten für direkte Worker-Proben ohne vollständigen Laufstart. */
final class WorkerIdentityFixture {
    private WorkerIdentityFixture() { }

    @SuppressWarnings("unchecked")
    static void prepare(Simulator simulator, String actor) throws Exception {
        var identities = Simulator.class.getDeclaredField("fachFreigabeKennungen");
        identities.setAccessible(true);
        identities.set(simulator, List.of(actor, actor + "-kollege"));
        var accounts = Simulator.class.getDeclaredField("webKonten");
        accounts.setAccessible(true);
        var configured = (Map<String, String>) accounts.get(simulator);
        configured.put(actor, "test");
        configured.put(actor + "-kollege", "test");
    }
}
