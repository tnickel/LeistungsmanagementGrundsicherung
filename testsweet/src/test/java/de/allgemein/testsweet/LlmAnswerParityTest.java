package de.allgemein.testsweet;

import de.allgemein.lmg.web.Json;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Dieselben Rohantworten und unabhängigen Erwartungen werden auch von Node geprüft. */
class LlmAnswerParityTest {
    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("antwortKorpus")
    void gemeinsameRohbytesErzeugenDasselbeFormatUndFachurteil(String name, String content, boolean accepted,
            String expectedState, List<String> expectedIds, List<Map<String, Object>> requirements) {
        if (!accepted) {
            assertThrows(IllegalArgumentException.class,
                    () -> LlmAnswerValidator.validate(content, expectedIds, requirements));
        } else {
            var cases = LlmAnswerValidator.validate(content, expectedIds, requirements);
            var reference = LlmAnswerValidator.referenceChecks(cases, requirements);
            assertEquals(expectedState, LlmAnswerValidator.overall(cases, reference));
        }
    }

    @SuppressWarnings("unchecked")
    static Stream<Arguments> antwortKorpus() throws Exception {
        Path file = Path.of("../doc/testzertifikat-werkzeug/llm-answer-parity-corpus.json");
        if (!Files.isRegularFile(file)) file = Path.of("doc/testzertifikat-werkzeug/llm-answer-parity-corpus.json");
        Map<String, Object> corpus = (Map<String, Object>) Json.parse(Files.readString(file));
        var expectedIds = (List<String>) corpus.get("expectedCaseIds");
        var defaults = (List<Map<String, Object>>) corpus.get("requirements");
        return ((List<Map<String, Object>>) corpus.get("fixtures")).stream().map(row ->
                Arguments.of(row.get("name"), row.get("content"), Boolean.TRUE.equals(row.get("accepted")),
                        row.get("state"), expectedIds, row.getOrDefault("requirements", defaults)));
    }
}
