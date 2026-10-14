package de.allgemein.testsweet;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Unabhängige arithmetische Referenz 2026; ruft keine Produktionsberechnung auf.
 * Rechtslage, Workflow und SQL werden zusätzlich vom Modell anhand der Dokumente geprüft. */
final class LlmCalculationReference {
    private static final String[] RBS = {"0", "563", "506", "451", "471", "390", "357"};
    private static final String[] WW = {"0", ".023", ".023", "0", ".014", ".012", ".008"};
    private static final BigDecimal ZERO = new BigDecimal("0.00");

    /** Für sämtliche Simulatorfälle: ausschließlich die Arithmetik aller vollständigen Blöcke prüfen. */
    static List<String> verifyBlocks(String serializedBlocks) {
        List<String> findings = new ArrayList<>(), missing = new ArrayList<>();
        int index = 0;
        for (String part : serializedBlocks.split("(?m)(?=^block: )")) {
            if (!part.startsWith("block: ")) continue;
            String id = "B" + ++index;
            try { calculate(id, part, findings, missing); }
            catch (RuntimeException e) { missing.add(id + ": unvollständige Recheneingaben (" + e.getMessage() + ")"); }
        }
        if (index == 0) missing.add("Keine Rechenblöcke für unabhängige arithmetische Prüfung übergeben");
        findings.addAll(missing); return List.copyOf(findings);
    }

    static Map<String, Object> requirement(String id, String text) {
        List<Map<String, Object>> blocks = new ArrayList<>();
        List<String> missing = new ArrayList<>(), findings = new ArrayList<>(), checks = new ArrayList<>();
        Matcher verification = Pattern.compile("(?m)^(V\\d+): (OK|NIO) ([^\\r\\n]+)\\r?\\n([\\s\\S]*?)(?=^V\\d+: |^### |\\z)").matcher(text);
        while (verification.find()) {
            String checkId = verification.group(1), details = verification.group(4);
            checks.add(checkId);
            if (verification.group(2).equals("NIO")) findings.add(checkId + ": dokumentierte NIO-Prüfung");
            Matcher values = Pattern.compile("(?m)^\\s*erwartet = ([^\\r\\n]+) \\| ist = ([^\\r\\n]+)").matcher(details);
            if (!values.find()) missing.add(checkId + ": SQL-Soll-/Ist-Nachweis fehlt");
            else {
                String expected = values.group(1), actual = values.group(2);
                boolean equal;
                try { equal = new BigDecimal(expected).compareTo(new BigDecimal(actual)) == 0; }
                catch (NumberFormatException notNumeric) { equal = List.of(expected.split("\\|", -1)).contains(actual); }
                if (!equal) findings.add(checkId + ": erwartet " + expected + ", ist " + actual);
            }
            boolean global = verification.group(3).contains("Globale Laufverifikation") && details.contains("erwartet = 0 Fehler | ist = 0 Fehler");
            if (!Pattern.compile("(?m)^\\s*sql\\s*=\\s*.+").matcher(details).find()
                    || !Pattern.compile("(?m)^\\s*params\\s*=\\s*.+").matcher(details).find()
                    || !global && Pattern.compile("(?m)^\\s*(?:sql|params)\\s*=\\s*\\?\\s*$").matcher(details).find())
                missing.add(checkId + ": SQL oder Parameter nicht vollständig dokumentiert");
        }
        Matcher steps = Pattern.compile("(?m)^\\d{4}-\\d\\d-\\d\\d [^\\r\\n]+ \\| [^\\r\\n]+ -> ([^\\r\\n]+)").matcher(text);
        int step = 0;
        while (steps.find()) {
            checks.add("S" + ++step);
            if (!steps.group(1).startsWith("OK") && !steps.group(1).startsWith("ERWARTETER FEHLER"))
                findings.add("S" + step + ": " + steps.group(1));
        }
        int blockIndex = 0;
        for (String part : text.split("(?m)(?=^block: )")) {
            if (!part.startsWith("block: ")) continue;
            String blockId = "B" + ++blockIndex;
            try { blocks.add(calculate(blockId, part, findings, missing)); }
            catch (RuntimeException e) { missing.add(blockId + ": unvollständige Recheneingaben (" + e.getMessage() + ")"); }
        }
        if (checks.isEmpty()) missing.add("Keine SQL-Prüfpunkte oder dokumentierten Ablaufschritte");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("caseId", id); out.put("calculations", blocks); out.put("checkIds", checks);
        out.put("missingInputs", missing); out.put("sourceFindings", findings);
        return out;
    }

    static Map<String, Object> coverage(Map<?, ?> requirement) {
        List<Map<String, Object>> blocks = new ArrayList<>();
        for (Object value : (List<?>) requirement.get("calculations")) {
            Map<?, ?> block = (Map<?, ?>) value;
            blocks.add(Map.of("blockId", block.get("blockId"), "personIds", ((List<?>) block.get("persons")).stream()
                    .map(p -> ((Map<?, ?>) p).get("personId")).toList(), "householdFields", ((Map<?, ?>) block.get("household")).keySet().stream().toList()));
        }
        return Map.of("caseId", requirement.get("caseId"), "calculations", blocks, "checkIds", requirement.get("checkIds"));
    }

    private static Map<String, Object> calculate(String blockId, String text, List<String> findings, List<String> missing) {
        List<Map<String, Object>> persons = new ArrayList<>();
        Matcher inputs = Pattern.compile("(?m)^p(\\d+)_eingaben: ([^\\r\\n]+)").matcher(text);
        BigDecimal total = ZERO, income = ZERO, protectedAssets = ZERO;
        int adults = 0;
        String rateValue = scalar(text, "kdu_warmwasser_abzug_anteil", false), limitValue = scalar(text, "kdu_bg_grenze", false);
        if ((rateValue == null) != (limitValue == null)) throw new IllegalArgumentException("KdU-Parameter unvollständig");
        BigDecimal deductionRate = rateValue == null ? ZERO : d(rateValue), limit = limitValue == null ? ZERO : d(limitValue);
        if (deductionRate.signum() < 0 || deductionRate.compareTo(BigDecimal.ONE) > 0 || limit.signum() < 0)
            throw new IllegalArgumentException("KdU-Parameter außerhalb zulässiger Grenzen");
        BigDecimal remainingKdu = limit.signum() > 0 ? limit : null;
        Map<String, String> documented = new LinkedHashMap<>();
        while (inputs.find()) {
            String personId = "p" + inputs.group(1);
            if (!personId.equals("p" + (persons.size() + 1))) throw new IllegalArgumentException("Personen nicht eindeutig/lückenlos");
            Map<String, String> in = new LinkedHashMap<>();
            for (String field : inputs.group(2).split(", ")) { String[] kv = field.split("=", 2); if (kv.length != 2 || in.put(kv[0], kv[1]) != null) throw new IllegalArgumentException("Eingabefeld doppelt/ungültig"); }
            int rbs = Integer.parseInt(required(in, "rbs"));
            if (rbs < 1 || rbs > 6) throw new IllegalArgumentException("RBS außerhalb Referenz 2026");
            BigDecimal rb = d(RBS[rbs]), g = yes(in, "merkzeichen_g") ? cents(rb.multiply(d(".17"))) : ZERO;
            boolean pregnant = yes(in, "schwanger");
            String delivery = required(in, "entbindungsmonat"), month = scalar(text, "zahlungsmonat", false);
            if (pregnant && !delivery.equals("-")) {
                if (month == null || !month.matches("\\d{4}-\\d{2}")) { missing.add(blockId + "." + personId + ": Zahlungsmonat für Schwangerschaft fehlt"); }
                else if (month.compareTo(delivery) > 0) pregnant = false;
            }
            BigDecimal pregnancy = pregnant ? cents(rb.multiply(d(".17"))) : ZERO;
            int u7 = Integer.parseInt(required(in, "kinder_u7")), u16 = Integer.parseInt(required(in, "kinder_u16"));
            int minor = Math.max(Math.max(u7, u16), Integer.parseInt(required(in, "kinder_minderjaehrig")));
            BigDecimal single = minor == 0 ? ZERO : cents(d("563").multiply(u7 > 0 || u16 >= 2 && u16 <= 3 ? d(".36") : d(".12").multiply(d(Integer.toString(minor))))).min(d("337.80"));
            boolean decentralized = yes(in, "warmwasser");
            BigDecimal ww = decentralized ? cents(rb.multiply(d(WW[rbs]))) : ZERO;
            BigDecimal kduDeduction = decentralized ? ZERO : cents(d(required(in, "kdu_heizung")).multiply(deductionRate));
            BigDecimal kdu = sum(in, "kdu_miete", "kdu_betrieb", "kdu_heizung").subtract(kduDeduction).max(ZERO);
            if (remainingKdu != null) { kdu = kdu.min(remainingKdu.max(ZERO)); remainingKdu = remainingKdu.subtract(kdu); }
            BigDecimal need = rb.add(g.add(pregnancy).add(single).min(rb)).add(ww).add(kdu);
            BigDecimal employed = d(required(in, "erwerb")), allowance = cents(employed.multiply(d(".30"))).min(d("281.50"));
            BigDecimal deduct = sum(in, "abz_steuern", "abz_sv", "abz_vers", "abz_erwerb");
            BigDecimal countable = cents(d(required(in, "rente")).add(employed).subtract(allowance).subtract(deduct).max(ZERO));
            Map<String, String> values = new LinkedHashMap<>();
            put(values, "regelbedarf", rb); put(values, "mehrbedarf_g", g); put(values, "mehrbedarf_schwanger", pregnancy);
            put(values, "mehrbedarf_alleinerziehend", single); put(values, "mehrbedarf_warmwasser", ww);
            put(values, "kdu", kdu); put(values, "gesamtbedarf", need); put(values, "freibetrag_erwerb", allowance);
            put(values, "kdu_warmwasser_abzug", kduDeduction);
            put(values, "absetzbetaetrag", deduct); put(values, "anrechenbar", countable); put(values, "zahlbetrag", need.subtract(countable));
            for (var entry : values.entrySet()) compare(text, personId + "_" + entry.getKey(), personId + "." + entry.getKey(), entry.getValue(), blockId, documented, findings, missing);
            persons.add(Map.of("personId", personId, "values", values));
            total = total.add(need); income = income.add(countable); protectedAssets = protectedAssets.add(d(rbs <= 3 ? "10000" : "500"));
            if (rbs <= 3) adults++;
        }
        if (persons.isEmpty()) throw new IllegalArgumentException("Personeneingaben fehlen");
        if (adults == 0) protectedAssets = d("10000.00");
        BigDecimal assets = d(scalar(text, "vermoegen", true)), usable = assets.subtract(protectedAssets).max(ZERO), payout = total.subtract(income).subtract(usable);
        Map<String, String> household = new LinkedHashMap<>();
        put(household, "schonvermoegen", protectedAssets); put(household, "bg_gesamtbedarf", total);
        put(household, "bg_anrechenbar", income); put(household, "bg_verwertbares_vermoegen", usable); put(household, "bg_zahlbetrag", payout);
        String budget = scalar(text, "budget_start", true);
        if (!budget.equals("-")) put(household, "budget_rest_hypothetisch", d(budget).subtract(payout));
        for (var entry : household.entrySet()) compare(text, entry.getKey(), entry.getKey(), entry.getValue(), blockId, documented, findings, missing);
        return Map.of("blockId", blockId, "persons", persons, "household", household, "documented", documented);
    }
    private static void compare(String text, String key, String field, String expected, String blockId, Map<String, String> documented, List<String> findings, List<String> missing) {
        String value = scalar(text, key, false);
        if (value == null) { missing.add(blockId + "." + field + ": Dokumentwert fehlt"); return; }
        documented.put(field, value);
        if (d(value).compareTo(d(expected)) != 0) findings.add(blockId + "." + field + ": Referenz " + expected + " EUR, Dokument " + value + " EUR");
    }
    private static String scalar(String text, String key, boolean required) {
        Matcher matcher = Pattern.compile("(?m)^" + Pattern.quote(key) + "\\s*=\\s*([^\\r\\n]+)").matcher(text);
        if (!matcher.find()) { if (required) throw new IllegalArgumentException(key + " fehlt"); return null; }
        String value = matcher.group(1).trim(); if (matcher.find()) throw new IllegalArgumentException(key + " doppelt"); return value;
    }
    private static String required(Map<String, String> values, String key) { String value = values.get(key); if (value == null) throw new IllegalArgumentException(key + " fehlt"); return value; }
    private static boolean yes(Map<String, String> values, String key) { String value = required(values, key); if (!value.equals("true") && !value.equals("false")) throw new IllegalArgumentException(key + " ungültig"); return value.equals("true"); }
    private static BigDecimal sum(Map<String, String> values, String... keys) { BigDecimal out = ZERO; for (String key : keys) out = out.add(d(required(values, key))); return out; }
    private static BigDecimal d(String value) { return new BigDecimal(value); }
    private static BigDecimal cents(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_UP); }
    private static void put(Map<String, String> target, String key, BigDecimal value) { target.put(key, cents(value).toPlainString()); }
}
