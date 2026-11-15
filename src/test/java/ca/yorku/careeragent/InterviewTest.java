package ca.yorku.careeragent;

import ca.yorku.careeragent.controller.AgentController;
import ca.yorku.careeragent.domain.InterviewSession;
import ca.yorku.careeragent.llm.Json;
import ca.yorku.careeragent.llm.LLMClient;
import ca.yorku.careeragent.parser.DocumentParserAdapter;
import ca.yorku.careeragent.persistence.Repository;
import ca.yorku.careeragent.service.CareerFacade;
import ca.yorku.careeragent.service.ToolManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class InterviewTest {
    @TempDir Path data;
    private String questions() { return Json.encode(Map.of("questions", List.of(
            Map.of("kind", "TECHNICAL", "text", "How does Java manage memory?", "rubric", "Explain garbage collection and references.", "evidence", "Java required"),
            Map.of("kind", "BEHAVIOURAL", "text", "Describe a team project using Java.", "rubric", "Explain situation, action and result.", "evidence", "Java required")))); }
    private String feedback(int score, int number) { return Json.encode(Map.of("feedback", List.of(
            Map.of("questionNumber", number, "score", score, "strengths", List.of("Names garbage collection"),
                    "weaknesses", List.of("Needs an example"), "improvements", List.of("Add an example of references becoming unreachable"),
                    "answerEvidence", "garbage collection")))); }
    private String referencedQuestions(String context) {
        Map<?, ?> request = Json.object(Json.parse(context));
        assertEquals(2, Json.integer(request.get("count"), 1, 20));
        assertEquals(List.of("TECHNICAL", "BEHAVIOURAL"), request.get("questionPlan"));
        Map<?, ?> sources = Json.object(request.get("jobEvidence"));
        String id = sources.entrySet().stream().filter(entry -> "Java required".equals(entry.getValue()))
                .map(entry -> entry.getKey().toString()).findFirst().orElseThrow();
        return Json.encode(Map.of("questions", List.of(
                Map.of("kind", "technical", "text", "How does Java manage memory?", "rubric", "Explain garbage collection.", "evidenceId", id),
                Map.of("kind", "behavioral", "text", "Describe teamwork on a Java project.", "rubric", "Explain actions and results.", "evidenceId", id))));
    }
    @Test void referencedQuestionsPreserveOriginalJobEvidenceAndNormalizeCategorySpelling() {
        AtomicInteger calls = new AtomicInteger();
        var facade = facade((instructions, context) -> {
            calls.incrementAndGet();
            return referencedQuestions(context);
        });
        var set = facade.generateQuestions("J01", 2);
        assertEquals(1, calls.get());
        assertEquals(List.of("TECHNICAL", "BEHAVIOURAL"), set.questions().stream().map(q -> q.kind()).toList());
        assertTrue(set.questions().stream().allMatch(q -> q.evidence().equals("Java required")));
        var loaded = new CareerFacade(new Repository(data)).getQuestionSet(set.id());
        assertEquals(set, loaded);
        assertTrue(loaded.jobContext().contains("Java required"));
    }
    @Test void malformedJsonAndUnsupportedQuestionQuotesAreCorrectedOnce() {
        for (String invalid : List.of("{\"questions\":[", questions().replace("Java required", "SQL required"))) {
            AtomicInteger calls = new AtomicInteger();
            var facade = facade((instructions, context) -> {
                if (calls.incrementAndGet() == 1) return invalid;
                assertTrue(instructions.contains("previous response failed validation"));
                return referencedQuestions(context);
            });
            int before = facade.listQuestionSets().size();
            var set = facade.generateQuestions("J01", 2);
            assertEquals(2, calls.get());
            assertEquals(before + 1, facade.listQuestionSets().size());
            assertEquals(2, set.questions().size());
        }
    }
    @Test void repeatedInvalidQuestionResponsesNeverSavePartialSets() {
        for (String invalid : List.of(
                "{\"questions\":[",
                questions().replace("Describe a team project using Java.", "How does Java manage memory!"),
                questions().replace("BEHAVIOURAL", "TECHNICAL"),
                questions().replace("BEHAVIOURAL", "UNKNOWN"),
                questions().replace("Explain situation, action and result.", ""),
                questions().replace("\"evidence\":\"Java required\"", "\"evidenceId\":\"J999\""))) {
            AtomicInteger calls = new AtomicInteger();
            var facade = facade((instructions, context) -> { calls.incrementAndGet(); return invalid; });
            assertThrows(IllegalArgumentException.class, () -> facade.generateQuestions("J01", 2));
            assertEquals(2, calls.get());
            assertTrue(facade.listQuestionSets().isEmpty());
        }
    }
    @Test void badQuestionInputsDoNotCallProviderAndRateLimitsAreNotRetried() {
        AtomicInteger calls = new AtomicInteger();
        var facade = facade((instructions, context) -> {
            calls.incrementAndGet();
            throw new IllegalStateException("LLM request failed (HTTP 429). Groq quota/rate limit reached.");
        });
        assertThrows(IllegalArgumentException.class, () -> facade.generateQuestions("J01", 0));
        assertThrows(IllegalArgumentException.class, () -> facade.generateQuestions("J01", 21));
        var unreviewed = facade.importJob("Analyst", "Other", "SQL required", "", "", null);
        assertThrows(IllegalArgumentException.class, () -> facade.generateQuestions(unreviewed.getId(), 2));
        assertEquals(0, calls.get());
        assertThrows(IllegalStateException.class, () -> facade.generateQuestions("J01", 2));
        assertEquals(1, calls.get());
        assertTrue(facade.listQuestionSets().isEmpty());
    }
    @Test void referencedFeedbackRecoversFromMalformedJsonAndKeepsOnlyAnsweredQuestionEvidence() {
        AtomicInteger feedbackCalls = new AtomicInteger();
        String answer = "Java uses garbage collection. Zoë tested unreachable objects.";
        var facade = facade((instructions, context) -> {
            if (instructions.startsWith("F11")) return referencedQuestions(context);
            if (feedbackCalls.incrementAndGet() == 1) return "{\"feedback\":";
            Map<?, ?> sources = Json.object(Json.object(Json.object(Json.parse(context)).get("answerSources")).get("1"));
            assertEquals(answer, sources.get("A1_1"));
            return feedback(75, 1).replace("\"answerEvidence\":\"garbage collection\"", "\"answerEvidenceId\":\"A1_1\"");
        });
        var session = facade.startInterview(facade.generateQuestions("J01", 2).id());
        facade.answerInterview(session.id(), answer, false);
        facade.answerInterview(session.id(), "", true);
        facade.transitionInterview(session.id(), "finish");
        var result = facade.interviewFeedback(session.id());
        assertEquals(2, feedbackCalls.get());
        assertEquals(Set.of(1), result.feedback().keySet());
        assertEquals(answer, result.feedback().get(1).answerEvidence());
        assertEquals(result.feedback(), new CareerFacade(new Repository(data)).getInterview(session.id()).feedback());
    }
    @Test void feedbackCannotCiteAnotherAnswersSourceOrOverwriteEarlierFeedback() {
        var valid = facade((i, c) -> i.startsWith("F11") ? questions() : feedback(75, 1));
        var session = valid.startInterview(valid.generateQuestions("J01", 2).id());
        valid.answerInterview(session.id(), "Java uses garbage collection", false);
        valid.answerInterview(session.id(), "", true);
        valid.interviewFeedback(session.id());
        AtomicInteger calls = new AtomicInteger();
        var invalid = facade((i, c) -> {
            calls.incrementAndGet();
            return feedback(50, 1).replace("\"answerEvidence\":\"garbage collection\"", "\"answerEvidenceId\":\"A2_1\"");
        });
        assertThrows(IllegalArgumentException.class, () -> invalid.interviewFeedback(session.id()));
        assertEquals(2, calls.get());
        assertEquals(75, invalid.getInterview(session.id()).feedback().get(1).score());
    }
    private CareerFacade facade(LLMClient llm) {
        var repo = new Repository(data);
        var facade = new CareerFacade(repo, new ToolManager(repo), new DocumentParserAdapter(), llm);
        var job = facade.importJob("Developer", "Example", "Java required", "", "Toronto", null);
        facade.saveManualRequirements(job.getId(), Set.of("Java"), Set.of());
        return facade;
    }
    @Test void questionsSessionAnswersPauseResumeSkipAndFeedbackPersistAcrossRestart() {
        var facade = facade((i, c) -> i.startsWith("F11") ? questions() : feedback(75, 1));
        var set = facade.generateQuestions("J01", 2);
        var session = facade.startInterview(set.id());
        assertThrows(IllegalArgumentException.class, () -> facade.answerInterview(session.id(), " ", false));
        facade.answerInterview(session.id(), "Java uses garbage collection for unreachable objects.", false);
        facade.transitionInterview(session.id(), "pause");
        assertThrows(IllegalArgumentException.class, () -> facade.answerInterview(session.id(), "Another answer", false));
        var restarted = facade((i, c) -> feedback(75, 1));
        assertEquals(InterviewSession.Status.PAUSED, restarted.getInterview(session.id()).status());
        assertEquals(2, restarted.getInterview(session.id()).nextQuestion());
        restarted.transitionInterview(session.id(), "resume");
        restarted.answerInterview(session.id(), "", true);
        restarted.transitionInterview(session.id(), "finish");
        var reviewed = restarted.interviewFeedback(session.id());
        assertEquals(InterviewSession.Status.FINISHED, reviewed.status());
        assertEquals(Set.of(1), reviewed.feedback().keySet());
        assertEquals(75, reviewed.feedback().get(1).score());
        assertEquals(75, restarted.getInterview(session.id()).feedback().get(1).score());
        assertThrows(IllegalArgumentException.class, () -> restarted.answerInterview(session.id(), "Late answer", false));
    }
    @Test void incorrectCountsDuplicatesAndUnreviewedJobsNeverSaveQuestionSets() {
        var facade = facade((i, c) -> questions());
        assertThrows(IllegalArgumentException.class, () -> facade.generateQuestions("J01", 0));
        assertThrows(IllegalArgumentException.class, () -> facade.generateQuestions("J01", 21));
        assertThrows(IllegalArgumentException.class, () -> facade.generateQuestions("J01", 1));
        var duplicate = facade((i, c) -> questions().replace("Describe a team project using Java.", "How does Java manage memory!"));
        assertThrows(IllegalArgumentException.class, () -> duplicate.generateQuestions("J01", 2));
        assertTrue(duplicate.listQuestionSets().isEmpty());
    }
    @Test void invalidFeedbackOrProviderFailurePreservesPreviouslySavedFeedback() {
        var facade = facade((i, c) -> i.startsWith("F11") ? questions() : feedback(75, 1));
        var session = facade.startInterview(facade.generateQuestions("J01", 2).id());
        facade.answerInterview(session.id(), "Java uses garbage collection", false);
        facade.answerInterview(session.id(), "", true);
        facade.interviewFeedback(session.id());
        for (String response : List.of(feedback(101, 1), feedback(50, 2), feedback(50, 1).replace("garbage collection", "fabricated answer"))) {
            var invalid = facade((i, c) -> response);
            assertThrows(IllegalArgumentException.class, () -> invalid.interviewFeedback(session.id()));
            assertEquals(75, invalid.getInterview(session.id()).feedback().get(1).score());
        }
        var offline = facade((i, c) -> { throw new IllegalStateException("Offline"); });
        assertTrue(new AgentController(offline).submitRequest("interview.feedback", Map.of("sessionId", session.id())).startsWith("ERROR:"));
        assertEquals(75, offline.getInterview(session.id()).feedback().get(1).score());
    }
    @Test void missingAnswersAndPrematureFinishAreRejectedWithoutApiCalls() {
        var facade = facade((i, c) -> questions());
        var session = facade.startInterview(facade.generateQuestions("J01", 2).id());
        assertThrows(IllegalArgumentException.class, () -> facade.interviewFeedback(session.id()));
        assertThrows(IllegalArgumentException.class, () -> facade.transitionInterview(session.id(), "finish"));
        assertEquals(InterviewSession.Status.IN_PROGRESS, facade.getInterview(session.id()).status());
    }
}
