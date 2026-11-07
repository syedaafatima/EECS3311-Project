package ca.yorku.careeragent.domain;

import ca.yorku.careeragent.llm.Json;
import java.util.*;

/** Persisted sequential interview state; skipped answers never receive feedback. */
public final class InterviewSession {
    public enum Status { IN_PROGRESS, PAUSED, FINISHED }
    private final String id;
    private final QuestionSet questionSet;
    private final Map<Integer, String> answers = new TreeMap<>();
    private final Set<Integer> skipped = new TreeSet<>();
    private final Map<Integer, AnswerFeedback> feedback = new TreeMap<>();
    private Status status = Status.IN_PROGRESS;

    public InterviewSession(String id, QuestionSet questionSet) {
        if (id == null || !id.matches("S-[0-9a-f-]{36}")) throw new IllegalArgumentException("Invalid interview ID.");
        this.id = id; this.questionSet = Objects.requireNonNull(questionSet);
    }
    public int nextQuestion() {
        for (int i = 1; i <= questionSet.questions().size(); i++) if (!answers.containsKey(i) && !skipped.contains(i)) return i;
        return 0;
    }
    public void answer(String text, boolean skip) {
        if (status != Status.IN_PROGRESS) throw new IllegalArgumentException("Resume an active interview before answering.");
        int index = nextQuestion();
        if (index == 0) throw new IllegalArgumentException("All questions are answered or skipped; finish the interview.");
        if (skip) skipped.add(index);
        else {
            if (text == null || text.isBlank() || text.length() > 12_000) throw new IllegalArgumentException("Answer must contain 1-12000 characters, or explicitly skip.");
            answers.put(index, text.strip());
        }
    }
    public void pause() {
        if (status != Status.IN_PROGRESS) throw new IllegalArgumentException("Only an active interview can be paused.");
        status = Status.PAUSED;
    }
    public void resume() {
        if (status != Status.PAUSED) throw new IllegalArgumentException("Only a paused interview can be resumed.");
        status = Status.IN_PROGRESS;
    }
    public void finish() {
        if (status != Status.IN_PROGRESS || nextQuestion() != 0) throw new IllegalArgumentException("Answer or explicitly skip every question before finishing an active session.");
        status = Status.FINISHED;
    }
    public void attachFeedback(Map<Integer, AnswerFeedback> reviewed) {
        if (!reviewed.keySet().equals(answers.keySet())) throw new IllegalArgumentException("Feedback must cover exactly the answered questions.");
        feedback.clear(); feedback.putAll(reviewed);
    }
    public String id() { return id; }
    public QuestionSet questionSet() { return questionSet; }
    public Status status() { return status; }
    public Map<Integer, String> answers() { return Map.copyOf(answers); }
    public Map<Integer, AnswerFeedback> feedback() { return Map.copyOf(feedback); }
    public Map<String, Object> toRecord() {
        Map<String, String> answerRows = new TreeMap<>(); answers.forEach((i, text) -> answerRows.put(i.toString(), text));
        Map<String, Object> feedbackRows = new TreeMap<>(); feedback.forEach((i, value) -> feedbackRows.put(i.toString(), value.toRecord()));
        return Map.of("id", id, "questionSet", questionSet.toRecord(), "status", status.name(), "answers", answerRows,
                "skipped", skipped, "feedback", feedbackRows);
    }
    public static InterviewSession fromRecord(Map<?, ?> row) {
        InterviewSession session = new InterviewSession(Json.text(row.get("id")), QuestionSet.fromRecord(Json.object(row.get("questionSet"))));
        session.status = Status.valueOf(Json.text(row.get("status")));
        Json.object(row.get("answers")).forEach((key, value) -> session.answers.put(Integer.parseInt(key.toString()), Json.text(value)));
        Json.array(row.get("skipped")).forEach(value -> session.skipped.add(Json.integer(value, 1, session.questionSet.questions().size())));
        Json.object(row.get("feedback")).forEach((key, value) -> session.feedback.put(Integer.parseInt(key.toString()), AnswerFeedback.fromRecord(Json.object(value))));
        return session;
    }
    @Override public String toString() {
        StringBuilder text = new StringBuilder(id + " | job=" + questionSet.jobId() + " | " + status);
        for (int i = 1; i <= questionSet.questions().size(); i++) {
            var question = questionSet.questions().get(i - 1);
            text.append("\n").append(i).append(". ").append(question.text()).append("\nRubric: ").append(question.rubric())
                    .append("\nAnswer: ").append(skipped.contains(i) ? "[SKIPPED]" : answers.getOrDefault(i, "[NOT ANSWERED]"));
            if (feedback.containsKey(i)) text.append("\nAI feedback (review): ").append(feedback.get(i));
        }
        if (nextQuestion() > 0) text.append("\nNext question: ").append(nextQuestion());
        else text.append("\nAll questions answered or skipped.");
        return text.toString();
    }
}
