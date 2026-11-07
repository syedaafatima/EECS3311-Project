package ca.yorku.careeragent.domain;

import ca.yorku.careeragent.llm.Json;
import java.util.List;
import java.util.Map;

public record AnswerFeedback(int score, List<String> strengths, List<String> weaknesses,
        List<String> improvements, String answerEvidence) {
    public AnswerFeedback {
        if (score < 0 || score > 100) throw new IllegalArgumentException("Feedback score must be 0-100.");
        strengths = List.copyOf(strengths); weaknesses = List.copyOf(weaknesses); improvements = List.copyOf(improvements);
    }
    public Map<String, Object> toRecord() { return Map.of("score", score, "strengths", strengths, "weaknesses", weaknesses,
            "improvements", improvements, "answerEvidence", answerEvidence); }
    public static AnswerFeedback fromRecord(Map<?, ?> row) {
        return new AnswerFeedback(Json.integer(row.get("score"), 0, 100), strings(row.get("strengths")),
                strings(row.get("weaknesses")), strings(row.get("improvements")), Json.text(row.get("answerEvidence")));
    }
    private static List<String> strings(Object values) { return Json.array(values).stream().map(Json::text).toList(); }
}
