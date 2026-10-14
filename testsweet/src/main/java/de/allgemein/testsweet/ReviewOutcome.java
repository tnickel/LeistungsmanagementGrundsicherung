package de.allgemein.testsweet;

import java.util.*;

/** Gemeinsames Urteil für API, Oberfläche und Report; Befunde überleben technische Abbrüche. */
record ReviewOutcome(String state, String automatedState, String llmState,
                     boolean transportVerified, List<String> errors) {
    static ReviewOutcome evaluate(Simulator simulator, Map<String,Object> evidence) {
        boolean automatedFailure=simulator.auftraege().stream().anyMatch(a->a.status==Auftrag.Status.FEHLGESCHLAGEN||a.status==Auftrag.Status.VERIFIKATION_FEHLGESCHLAGEN)
                ||simulator.globalePruefungen().stream().anyMatch(p->!p.ok());
        String automated=automatedFailure?"FAIL":simulator.laufErfolgreich()?"PASS":"UNVERIFIED";
        String llm=String.valueOf(evidence.getOrDefault("state","UNVERIFIED"));
        boolean knownFailure=automatedFailure||"FAIL".equals(llm)||"FAIL".equals(evidence.get("caseVerdict"))
                ||Boolean.TRUE.equals(evidence.get("hasFailures"))||cases(evidence).stream().anyMatch(c->"FAIL".equals(c.get("verdict")));
        boolean transport=KiPruefAnhang.transportVerified(evidence);
        boolean llmPassed=KiPruefAnhang.fachlichBestanden(evidence);
        List<String> reasons=new ArrayList<>(simulator.nachweisFehler());
        if(automatedFailure)reasons.add("Automatische Fach-/SQL-Prüfung hat Fehler festgestellt.");
        else if(!"PASS".equals(automated))reasons.add(simulator.nachweisFehler().isEmpty()
                ? "Automatische Fach-/SQL-Prüfung ist unvollständig oder abgebrochen."
                : "Automatische Fach-/SQL-Prüfung ist wegen fehlender oder ungültiger Nachweise nicht bestätigt.");
        if(evidence.isEmpty())reasons.add("KI-Prüfung ist ausstehend.");
        else if(!llmPassed)reasons.add("KI-Prüfung: "+llm+". "+evidence.getOrDefault("message","Prüfung nicht vollständig bestätigt."));
        for(Map<?,?> c:cases(evidence))if(!"PASS".equals(c.get("verdict"))) {
            Object raw=c.get("findings");if(raw instanceof List<?> findings)for(Object finding:findings)reasons.add(c.get("caseId")+": "+finding);
        }
        String overall=knownFailure?"FAIL":"PASS".equals(automated)&&llmPassed?"PASS":"UNVERIFIED";
        return new ReviewOutcome(overall,automated,llm,transport,List.copyOf(new LinkedHashSet<>(reasons)));
    }
    boolean passed(){return state.equals("PASS");}
    String label(){return switch(state){case "PASS"->"BESTANDEN im ausgewiesenen Prüfumfang";case "FAIL"->"NICHT BESTANDEN";default->"UNVOLLSTÄNDIG";};}
    Map<String,Object> asMap(){return Map.of("state",state,"label",label(),"automatedState",automatedState,"llmState",llmState,"transportVerified",transportVerified,"errors",errors);}
    private static List<Map<?,?>> cases(Map<String,Object> evidence){if(!(evidence.get("cases") instanceof List<?> list))return List.of();List<Map<?,?>> out=new ArrayList<>();for(Object row:list)if(row instanceof Map<?,?> map)out.add(map);return out;}
}
