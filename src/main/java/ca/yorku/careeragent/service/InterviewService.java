package ca.yorku.careeragent.service;

import ca.yorku.careeragent.domain.*;
import ca.yorku.careeragent.persistence.LocalRecords;
import java.util.List;
import java.util.UUID;

/** F11-F13: generation, durable session transitions and all-or-nothing feedback. */
public final class InterviewService {
    private final LocalRecords records;
    private final CareerAgent agent;
    public InterviewService(LocalRecords records, CareerAgent agent) { this.records = records; this.agent = agent; }
    public QuestionSet generateQuestions(String jobId, int count) {
        QuestionSet set = agent.generateQuestions(jobId, count);
        records.put("question-sets", set.id(), set.toRecord());
        return set;
    }
    public QuestionSet questions(String id) {
        return QuestionSet.fromRecord(records.get("question-sets", id).orElseThrow(() -> new IllegalArgumentException("Unknown question set: " + id)));
    }
    public List<QuestionSet> listQuestions() { return records.list("question-sets").stream().map(QuestionSet::fromRecord).toList(); }
    public InterviewSession start(String setId) {
        InterviewSession session = new InterviewSession("S-" + UUID.randomUUID(), questions(setId));
        save(session); return session;
    }
    public InterviewSession session(String id) {
        return InterviewSession.fromRecord(records.get("interviews", id).orElseThrow(() -> new IllegalArgumentException("Unknown interview: " + id)));
    }
    public List<InterviewSession> listSessions() { return records.list("interviews").stream().map(InterviewSession::fromRecord).toList(); }
    public InterviewSession answer(String id, String text, boolean skip) {
        InterviewSession session = session(id); session.answer(text, skip); save(session); return session;
    }
    public InterviewSession transition(String id, String action) {
        InterviewSession session = session(id);
        switch (action) {
            case "pause" -> session.pause();
            case "resume" -> session.resume();
            case "finish" -> session.finish();
            default -> throw new IllegalArgumentException("Unknown interview transition.");
        }
        save(session); return session;
    }
    public InterviewSession feedback(String id) {
        InterviewSession session = session(id);
        session.attachFeedback(agent.evaluateAnswers(session)); // No writes on any validation/provider failure.
        save(session); return session;
    }
    private void save(InterviewSession session) { records.put("interviews", session.id(), session.toRecord()); }
}
