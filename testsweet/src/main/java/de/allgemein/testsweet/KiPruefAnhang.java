package de.allgemein.testsweet;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Getrennte Nachweise für Dateiübergabe und fachliche KI-Prüfung. */
final class KiPruefAnhang {
    private KiPruefAnhang() { }

    static void pdf(SweetPdf pdf, Map<String, Object> evidence) {
        pdf.ueberschrift("Anhang: KI-Prüfung und Dateiübergabe");
        boolean transported = transportVerified(evidence);
        boolean passed = fachlichBestanden(evidence);
        pdf.kasten("Dateiübergabe geprüft: " + (transported ? "BESTÄTIGT" : "AUSSTEHEND / NICHT BESTÄTIGT"),
                true, SweetPdf.WEISS, transported ? SweetPdf.FUELL_GRUEN : SweetPdf.FUELL_GRAU, 10f);
        pdf.leereZeile(5f);
        pdf.kasten("Fachprüfung: " + (passed ? "BESTANDEN" : value(evidence, "state", "AUSSTEHEND")),
                true, SweetPdf.WEISS, passed ? SweetPdf.FUELL_GRUEN
                        : "FAIL".equals(evidence.get("state")) ? SweetPdf.FUELL_ROT : SweetPdf.FUELL_GRAU, 10f);
        pdf.leereZeile(6f);
        lines(evidence, text -> pdfLine(pdf, text));
    }

    static void md(StringBuilder md, Map<String, Object> evidence) {
        md.append("\n## ki_pruefung_dateiuebergabe\n\n");
        md.append("dateiuebergabe_geprueft = ").append(transportVerified(evidence)).append('\n');
        md.append("fachpruefung_bestanden = ").append(fachlichBestanden(evidence)).append('\n');
        lines(evidence, text -> md.append(text).append('\n'));
        md.append('\n');
    }

    static boolean transportVerified(Map<String, Object> evidence) {
        List<?> checks = list(evidence.get("transportChecks"));
        return Boolean.TRUE.equals(evidence.get("transportVerified")) && !checks.isEmpty()
                && checks.stream().allMatch(check -> check instanceof Map<?, ?> map
                && Boolean.TRUE.equals(map.get("responseValidated"))
                && !list(map.get("documents")).isEmpty());
    }

    static boolean fachlichBestanden(Map<String, Object> evidence) {
        long total = number(evidence.get("totalCases"));
        return "PASS".equals(evidence.get("state")) && transportVerified(evidence)
                && !Boolean.TRUE.equals(evidence.get("running")) && total > 0
                && number(evidence.get("completedCases")) == total;
    }

    private static void lines(Map<String, Object> evidence, Consumer<String> out) {
        if (evidence.isEmpty()) {
            out.accept("Noch keine KI-Prüfung: Dateiübergabe und Fachprüfung sind ausstehend.");
            return;
        }
        out.accept("reviewId = " + value(evidence, "reviewId", "-") + " | Modell = " + value(evidence, "model", "-"));
        out.accept("Fachstatus = " + value(evidence, "state", "UNVERIFIED") + " | geprüfte Fälle = "
                + number(evidence.get("completedCases")) + " / " + number(evidence.get("totalCases")));
        out.accept("Der Transportnachweis bestätigt die protokollierten Anfrage-/Antwortdateien und ihre Inhalte. "
                + "Er ersetzt keine fachlich erfolgreiche Prüfung oder Aussage über nicht geprüfte Fälle.");
        for (Object raw : list(evidence.get("transportChecks"))) {
            if (!(raw instanceof Map<?, ?> check)) continue;
            out.accept("Batch " + value(check, "batch", "-") + " | API-Request-ID = " + value(check, "requestId", "-")
                    + " | HTTP = " + value(check, "httpStatus", "-") + " | Antwort validiert = " + value(check, "responseValidated", "false"));
            out.accept("Angefragtes Modell = " + value(check, "requestedModel", "-")
                    + " | zurückgegebenes Modell = " + value(check, "returnedModel", "-"));
            out.accept("Anfrage = " + value(check, "requestFile", "-") + " | SHA-256 = " + value(check, "requestSha256", "-"));
            out.accept("Antwort = " + value(check, "responseFile", "-") + " | SHA-256 = " + value(check, "responseSha256", "-"));
            out.accept("Fall-IDs = " + value(check, "caseIds", "-") + " | Abschlussgrund = " + value(check, "finishReason", "-"));
            for (Object rawDocument : list(check.get("documents"))) {
                if (!(rawDocument instanceof Map<?, ?> document)) continue;
                out.accept("Gesendete Datei = " + value(document, "name", "-") + " | Bytes = "
                        + value(document, "bytes", "-") + " | SHA-256 = " + value(document, "sha256", "-"));
            }
        }
        for (Object raw : list(evidence.get("sources"))) {
            if (raw instanceof Map<?, ?> source) {
                out.accept("Quellnachweis = " + value(source, "path", "-") + " | SHA-256 = "
                        + value(source, "sha256", "-") + " | Lauf = " + value(source, "runId", "-")
                        + " | Kategorie = " + value(source, "category", "-"));
            }
        }
        out.accept("Gesicherte Anfragen und Antworten = " + value(evidence, "artifactDirectory", "-"));
    }

    private static String value(Map<?, ?> map, String key, String fallback) {
        Object value = map.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    private static void pdfLine(SweetPdf pdf, String line) {
        // SHA-256-Werte und Fall-IDs enthalten oft keine Leerzeichen. SweetPdf
        // bricht solche Wörter nicht selbst um; feste Teilstücke halten sie im Satzspiegel.
        while (line.length() > 88) {
            int split = line.lastIndexOf(' ', 88);
            if (split < 44) split = 88;
            pdf.text(line.substring(0, split), SweetPdf.SCHWARZ, 8.5f);
            line = line.substring(split).stripLeading();
        }
        pdf.text(line, SweetPdf.SCHWARZ, 8.5f);
    }

    private static long number(Object value) {
        return value instanceof Number number ? number.longValue() : 0;
    }

    private static List<?> list(Object value) {
        return value instanceof List<?> list ? list : List.of();
    }
}
