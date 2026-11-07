package ca.yorku.careeragent.domain;

import ca.yorku.careeragent.llm.Json;
import java.util.List;
import java.util.Map;

/** F11 questions retain their original job context and evaluation rubric. */
public record QuestionSet(String id, String jobId, String jobContext, List<Question> questions) {
    public record Question(String kind, String text, String rubric, String evidence) {
        public Map<String, Object> toRecord() { return Map.of("kind", kind, "text", text, "rubric", rubric, "evidence", evidence); }
        static Question fromRecord(Map<?, ?> row) {
            return new Question(Json.text(row.get("kind")), Json.text(row.get("text")), Json.text(row.get("rubric")), Json.text(row.get("evidence")));
        }
    }
    public QuestionSet {
        if (id == null || !id.matches("Q-[0-9a-f-]{36}")) throw new IllegalArgumentException("Invalid question-set ID.");
        questions = List.copyOf(questions);
        if (questions.isEmpty() || questions.size() > 20) throw new IllegalArgumentException("Question count must be 1-20.");
    }
    public Map<String, Object> toRecord() {
        return Map.of("id", id, "jobId", jobId, "jobContext", jobContext, "questions", questions.stream().map(Question::toRecord).toList());
    }
    public static QuestionSet fromRecord(Map<?, ?> row) {
        return new QuestionSet(Json.text(row.get("id")), Json.text(row.get("jobId")), Json.text(row.get("jobContext")),
                Json.array(row.get("questions")).stream().map(value -> Question.fromRecord(Json.object(value))).toList());
    }
    @Override public String toString() {
        StringBuilder text = new StringBuilder(id + " | job=" + jobId + " | " + questions.size() + " questions");
        for (int i = 0; i < questions.size(); i++) {
            var q = questions.get(i);
            text.append("\n").append(i + 1).append(". ").append(q.kind()).append(": ").append(q.text())
                    .append("\nRubric: ").append(q.rubric()).append("\nJob evidence: ").append(q.evidence());
        }
        return text.toString();
    }
}
