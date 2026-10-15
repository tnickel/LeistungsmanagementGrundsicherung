package de.allgemein.testsweet;

import de.allgemein.lmg.web.Json;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Gemeinsame strikte Antwortprüfung für Empfang und spätere Integritätskontrolle. */
final class LlmAnswerValidator {
    static List<Map<String, Object>> validate(String content, List<String> expected, List<Map<String, Object>> requirements) {
        Object parsed;
        try { parsed = Json.parse(content); } catch (RuntimeException e) { throw new IllegalArgumentException("Modellantwort ist kein vollständiges JSON"); }
        if (!(parsed instanceof Map<?, ?> object) || !schemaVersion2(object.get("schemaVersion"))
                || !(object.get("caseIds") instanceof List<?> ids) || !(object.get("cases") instanceof List<?> cases))
            throw new IllegalArgumentException("Modellantwort enthält keine vollständige Fallliste nach Schema 2");
        sameIds(ids, expected, "Fall-ID-Liste unvollständig, doppelt oder unerwartet");
        Set<String> wanted = new HashSet<>(expected), seen = new HashSet<>();
        List<Map<String, Object>> checked = new ArrayList<>();
        for (Object row : cases) {
            if (!(row instanceof Map<?, ?> c) || !(c.get("caseId") instanceof String id) || !wanted.contains(id) || !seen.add(id))
                throw new IllegalArgumentException("Unvollständige oder doppelte Fallbewertung");
            verdict(c);
            if (!(c.get("calculations") instanceof List<?> calculations) || !(c.get("checks") instanceof List<?> checks))
                throw new IllegalArgumentException("Strukturierte Rechenblöcke oder Workflowprüfungen fehlen");
            Map<String, Object> requirement = requirements.stream().filter(r -> id.equals(r.get("caseId"))).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Keine quellengebundene Abdeckungsanforderung für " + id));
            List<?> expectedBlocks = (List<?>) requirement.get("calculations");
            sameIds(ids(calculations, "blockId"), ids(expectedBlocks, "blockId"), "Rechenblockabdeckung unvollständig/doppelt");
            for (Object value : calculations) {
                Map<?, ?> block = object(value), expectedBlock = find(expectedBlocks, "blockId", block.get("blockId"));
                List<?> persons = list(block.get("persons")), expectedPersons = list(expectedBlock.get("persons"));
                sameIds(ids(persons, "personId"), ids(expectedPersons, "personId"), "Personenabdeckung unvollständig/doppelt");
                for (Object p : persons) {
                    Map<?, ?> person = object(p), expectedPerson = find(expectedPersons, "personId", person.get("personId"));
                    numericFields(object(person.get("values")), object(expectedPerson.get("values")));
                }
                numericFields(object(block.get("household")), object(expectedBlock.get("household")));
            }
            sameIds(ids(checks, "checkId"), list(requirement.get("checkIds")), "SQL-/Workflowabdeckung unvollständig/doppelt");
            for (Object value : checks) {
                Map<?, ?> check = object(value); status(check.get("verdict"));
                nonblank(check.get("reasoning"));
            }
            Map<String, Object> copy = new LinkedHashMap<>(); c.forEach((k,v) -> copy.put(String.valueOf(k), v)); checked.add(copy);
        }
        if (!seen.equals(wanted)) throw new IllegalArgumentException("Nicht alle angeforderten Fälle wurden einzeln geprüft");
        return List.copyOf(checked);
    }

    static boolean schemaVersion2(Object value) {
        return value instanceof BigDecimal version && version.compareTo(BigDecimal.valueOf(2)) == 0;
    }

    static List<Map<String, Object>> referenceChecks(List<Map<String, Object>> cases, List<Map<String, Object>> requirements) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> c : cases) {
            Map<?, ?> requirement = find(requirements, "caseId", c.get("caseId"));
            List<String> arithmetic = new ArrayList<>(), workflowFindings = new ArrayList<>(), workflowUnverified = new ArrayList<>();
            for (Object value : list(c.get("calculations"))) {
                Map<?, ?> block = object(value), expected = find(list(requirement.get("calculations")), "blockId", block.get("blockId"));
                for (Object p : list(block.get("persons"))) {
                    Map<?, ?> person = object(p), ep = find(list(expected.get("persons")), "personId", person.get("personId"));
                    compareValues(object(person.get("values")), object(ep.get("values")), block.get("blockId") + "." + person.get("personId"), arithmetic);
                }
                compareValues(object(block.get("household")), object(expected.get("household")), String.valueOf(block.get("blockId")), arithmetic);
            }
            for (Object value : list(c.get("checks"))) {
                Map<?, ?> check = object(value);
                if ("FAIL".equals(check.get("verdict"))) workflowFindings.add(check.get("checkId") + ": " + check.get("reasoning"));
                if ("UNVERIFIED".equals(check.get("verdict"))) workflowUnverified.add(check.get("checkId") + ": " + check.get("reasoning"));
            }
            if ("PASS".equals(c.get("verdict")) && !list(c.get("findings")).isEmpty())
                workflowFindings.add("PASS widerspricht nichtleerer Befundliste; Befunde bleiben als Fehler erhalten");
            List<?> findings = list(requirement.get("sourceFindings")), missing = list(requirement.get("missingInputs"));
            String state = !findings.isEmpty() || !workflowFindings.isEmpty() ? "FAIL" : !arithmetic.isEmpty() || !missing.isEmpty() || !workflowUnverified.isEmpty() ? "UNVERIFIED" : "PASS";
            out.add(Map.of("caseId", c.get("caseId"), "state", state, "findings", findings, "missingInputs", missing,
                    "arithmeticIssues", arithmetic, "workflowFindings", workflowFindings, "workflowUnverified", workflowUnverified));
        }
        return out;
    }
    static String overall(List<Map<String, Object>> cases, List<Map<String, Object>> reference) {
        if (cases.stream().anyMatch(c -> "FAIL".equals(c.get("verdict"))) || reference.stream().anyMatch(c -> "FAIL".equals(c.get("state")))) return "FAIL";
        if (cases.stream().anyMatch(c -> "UNVERIFIED".equals(c.get("verdict"))) || reference.stream().anyMatch(c -> "UNVERIFIED".equals(c.get("state")))) return "UNVERIFIED";
        return "PASS";
    }
    private static void compareValues(Map<?, ?> actual, Map<?, ?> expected, String prefix, List<String> issues) {
        expected.forEach((key, value) -> { if (number(actual.get(key)).compareTo(number(value)) != 0) issues.add(prefix + "." + key + ": Modell " + actual.get(key) + ", Referenz " + value); });
    }
    private static void numericFields(Map<?, ?> actual, Map<?, ?> expected) {
        if (!actual.keySet().equals(expected.keySet())) throw new IllegalArgumentException("Numerische Zwischenwerte fehlen oder sind unerwartet");
        for (Object value : actual.values()) number(value);
    }
    private static BigDecimal number(Object value) {
        if (!(value instanceof String) && !(value instanceof Number)) throw new IllegalArgumentException("Rechenwert ist keine Zahl");
        try { if (!String.valueOf(value).matches("-?\\d{1,18}(?:\\.\\d{1,2})?")) throw new IllegalArgumentException(); return new BigDecimal(String.valueOf(value)); }
        catch (RuntimeException e) { throw new IllegalArgumentException("Ungültiger EUR-Rechenwert"); }
    }
    private static void verdict(Map<?, ?> c) {
        String verdict = status(c.get("verdict")); nonblank(c.get("reasoning"));
        List<?> findings = list(c.get("findings"));
        if (!verdict.equals("PASS") && findings.isEmpty()) throw new IllegalArgumentException("Widersprüchlicher Befundstatus in Modellantwort");
        for (Object f : findings) nonblank(f);
    }
    private static String status(Object status) { if (!(status instanceof String s) || !Set.of("PASS", "FAIL", "UNVERIFIED").contains(s)) throw new IllegalArgumentException("Ungültiges Prüfurteil"); return s; }
    private static void nonblank(Object value) { if (!(value instanceof String s) || s.isBlank()) throw new IllegalArgumentException("Prüfbegründung/Befund fehlt"); }
    private static List<?> list(Object value) { if (!(value instanceof List<?> list)) throw new IllegalArgumentException("Prüfliste fehlt"); return list; }
    private static Map<?, ?> object(Object value) { if (!(value instanceof Map<?, ?> object)) throw new IllegalArgumentException("Prüfobjekt fehlt"); return object; }

    private static List<?> ids(List<?> rows, String key) { return rows.stream().map(r -> object(r).get(key)).toList(); }
    private static Map<?, ?> find(List<?> rows, String key, Object value) { return rows.stream().map(LlmAnswerValidator::object).filter(r -> value.equals(r.get(key))).findFirst().orElseThrow(() -> new IllegalArgumentException("Prüfbezug fehlt: " + value)); }
    private static void sameIds(List<?> actual, List<?> expected, String error) {
        if (actual.size() != expected.size() || new HashSet<>(expected).size() != expected.size() || new HashSet<>(actual).size() != actual.size()
                || actual.stream().anyMatch(x -> !(x instanceof String)) || !new HashSet<>(actual).equals(new HashSet<>(expected))) throw new IllegalArgumentException(error);
    }
}
