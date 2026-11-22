package ca.yorku.careeragent;

import ca.yorku.careeragent.controller.AgentController;
import ca.yorku.careeragent.deadline.DeadlineReminder;
import ca.yorku.careeragent.domain.Application;
import ca.yorku.careeragent.domain.CareerDocument;
import ca.yorku.careeragent.domain.InterviewSession;
import ca.yorku.careeragent.parser.DocumentParserAdapter;
import ca.yorku.careeragent.persistence.Repository;
import ca.yorku.careeragent.service.CareerFacade;
import ca.yorku.careeragent.service.ToolManager;
import ca.yorku.careeragent.ui.JobSearchCLI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

/** Replays all 17 features with real parsers, persistence, CLI input and controlled model responses. */
class FeatureWorkflowTest {
    @TempDir Path data;

    private CareerFacade facade(WorkflowProvider model) {
        Repository repo = new Repository(data);
        return new CareerFacade(repo, new ToolManager(repo), new DocumentParserAdapter(), model);
    }

    private String ok(AgentController controller, String action, Map<String, String> input) {
        String result = controller.submitRequest(action, input);
        assertFalse(result.startsWith("ERROR:"), action + ": " + result);
        return result;
    }

    @Test void allSeventeenFeaturesCompleteAcrossCliRestartsAndInvalidModelResponses() {
        WorkflowProvider model = new WorkflowProvider();
        CareerFacade facade = facade(model);
        AgentController controller = new AgentController(facade);
        LocalDate today = LocalDate.now(ZoneId.of("America/Toronto"));

        // F01: save the approved profile.
        ok(controller, "profile.update", Map.of("name", "Demo Candidate", "education", "BSc Computer Science",
                "experience", "Java coursework and a task-tracker project", "skills", "Java, Git",
                "interests", "Software development", "location", "Toronto"));
        assertTrue(ok(controller, "profile.show", Map.of()).contains("Demo Candidate"));

        // F02: use the same PDF shipped for the demonstration, including cancellation.
        Map<String, String> pdf = Map.of("path", Path.of("examples", "demo-resume.pdf").toAbsolutePath().toString());
        ok(controller, "resume.preview", pdf);
        assertTrue(ok(controller, "resume.import", Map.of("confirmed", "no")).contains("cancelled"));
        assertTrue(facade.listResumes().isEmpty());
        ok(controller, "resume.preview", pdf);
        ok(controller, "resume.import", Map.of("confirmed", "yes"));
        String resume = facade.listResumes().get(0).getId();
        assertTrue(facade.listResumes().get(0).getText().contains("Used JUnit tests in the Java project."));

        // F03: only the approved extraction proposal is added.
        ok(controller, "resume.extract-skills", Map.of("resumeId", resume));
        ok(controller, "skills.approve", Map.of("confirmed", "yes", "selected", "1"));
        assertEquals(Set.of("Java", "Git", "JUnit"), facade.getProfile().orElseThrow().getSkills());

        // F04: two demo jobs plus the deliberately unreviewed validation posting.
        ok(controller, "job.import", Map.of("title", "Junior Developer", "company", "Example Company Demo",
                "description", "Java and SQL are required. Git is preferred.", "salary", "60000",
                "location", "Toronto", "deadline", today.plusDays(2).toString()));
        String first = facade.listJobs().get(0).getId();
        ok(controller, "job.update", Map.of("jobId", first, "title", "Junior Developer", "company", "Example Company Demo",
                "description", "Java and SQL are required. Git is preferred.", "salary", "65000",
                "location", "Toronto", "deadline", today.plusDays(2).toString()));
        ok(controller, "job.import", Map.of("title", "QA Analyst", "company", "Sample Labs Demo",
                "description", "Python and Git are required. SQL is preferred.", "salary", "", "location", "", "deadline", ""));
        String second = facade.listJobs().stream().filter(job -> job.getTitle().equals("QA Analyst")).findFirst().orElseThrow().getId();
        var practice = facade.importJob("Support Intern", "Practice Services Demo", "Communication is required.", "", "", null);
        assertEquals("65000", facade.getJob(first).getSalary());

        // F05: job-specific extraction followed by explicit review.
        for (String job : List.of(first, second)) {
            ok(controller, "job.analyze", Map.of("jobId", job));
            ok(controller, "job.analysis.approve", Map.of("confirmed", "yes", "selected", "1,2,3"));
        }

        // F06: reject unreviewed input and explain deterministic gaps.
        assertTrue(controller.submitRequest("job.gaps", Map.of("jobId", practice.getId())).startsWith("ERROR:"));
        String gaps = ok(controller, "job.gaps", Map.of("jobId", first));
        assertTrue(gaps.contains("Matched: [Java]"));
        assertTrue(gaps.contains("Missing: [SQL]"));
        assertTrue(ok(controller, "job.gaps.explain", Map.of("jobId", first)).contains("AI interpretation"));

        // F07-F08: both matching policies and comparison with unknown fields.
        String weighted = ok(controller, "job.match", Map.of("strategy", "weighted"));
        assertTrue(weighted.contains("65.0%"));
        assertTrue(weighted.contains("35.0%"));
        assertFalse(weighted.contains(practice.getId()));
        assertTrue(ok(controller, "job.match", Map.of("strategy", "required")).contains("50.0%"));
        assertTrue(ok(controller, "job.match.explain", Map.of("strategy", "weighted")).contains("AI interpretation"));
        assertTrue(controller.submitRequest("job.compare", Map.of("jobIds", first)).startsWith("ERROR:"));
        String comparison = ok(controller, "job.compare", Map.of("jobIds", first + "," + second, "strategy", "weighted"));
        assertTrue(comparison.contains("Unknown"));
        assertTrue(comparison.contains("AI-selected comparison highlights"));

        // F09-F10: separate reviewed documents, including correction of an unsupported model quote.
        assertTrue(controller.submitRequest("resume.suggest", Map.of("resumeId", resume, "jobId", practice.getId())).startsWith("ERROR:"));
        ok(controller, "resume.suggest", Map.of("resumeId", resume, "jobId", first));
        assertTrue(facade.listDocuments().isEmpty());
        ok(controller, "document.save", Map.of("confirmed", "yes"));
        ok(controller, "letter.draft", Map.of("resumeId", resume, "jobId", first, "tone", "professional"));
        assertEquals(2, model.calls("F10"));
        ok(controller, "document.save", Map.of("confirmed", "yes"));

        // F11: malformed JSON is corrected; only the valid question set is persisted.
        ok(controller, "interview.questions", Map.of("jobId", first, "count", "2"));
        assertEquals(2, model.calls("F11"));
        assertEquals(1, facade.listQuestionSets().size());
        String questionSet = facade.listQuestionSets().get(0).id();

        // F12: actual CLI parsing, multiline answers, pause and restart.
        ok(controller, "interview.start", Map.of("questionSetId", questionSet));
        String session = facade.listInterviews().get(0).id();
        new JobSearchCLI(controller, new Scanner("interview answer\n" + session
                + "\nI built a Java task tracker and used JUnit tests.\n.\ninterview pause\n" + session + "\nexit\n")).run();
        facade = facade(model);
        controller = new AgentController(facade);
        assertEquals(InterviewSession.Status.PAUSED, facade.getInterview(session).status());
        assertEquals(1, facade.getInterview(session).answers().size());
        new JobSearchCLI(controller, new Scanner("interview resume\n" + session + "\ninterview skip\n" + session
                + "\ninterview finish\n" + session + "\nexit\n")).run();
        assertEquals(InterviewSession.Status.FINISHED, facade.getInterview(session).status());

        // F13: wrong-answer source references are corrected without evaluating the skipped question.
        ok(controller, "interview.feedback", Map.of("sessionId", session));
        assertEquals(2, model.calls("F13"));
        assertEquals(Set.of(1), facade.getInterview(session).feedback().keySet());
        assertEquals(75, facade.getInterview(session).feedback().get(1).score());

        // F14: transition history survives updates; backward transitions are rejected.
        for (String status : List.of("SAVED", "APPLIED", "INTERVIEW")) {
            ok(controller, "application.add", Map.of("jobId", second, "submissionDate", today.toString(), "status", status));
        }
        assertTrue(controller.submitRequest("application.add", Map.of("jobId", second, "submissionDate", today.toString(),
                "status", "SAVED")).startsWith("ERROR:"));
        assertEquals(Application.Status.INTERVIEW, facade.listApplications().get(0).getStatus());
        assertTrue(ok(controller, "application.list", Map.of()).contains("Status history:"));

        // F15: upcoming/unknown deadlines and once-per-process delivery to an active Observer.
        facade = facade(model);
        controller = new AgentController(facade);
        List<DeadlineReminder> notifications = new ArrayList<>();
        facade.subscribeDeadlines(notifications::add);
        String deadlines = ok(controller, "deadlines.list", Map.of());
        assertTrue(deadlines.contains("UPCOMING"));
        assertTrue(deadlines.contains("UNKNOWN"));
        assertFalse(deadlines.contains(second + " |"));
        ok(controller, "deadlines.check", Map.of());
        ok(controller, "deadlines.check", Map.of());
        assertEquals(1, notifications.size());

        // F16: validate supplied URLs, correct an invalid citation, then require review.
        assertTrue(controller.submitRequest("company.notes", Map.of("jobId", first, "text", "Example Company Demo builds data products.",
                "sources", "ftp://example.com/about")).startsWith("ERROR:"));
        ok(controller, "company.notes", Map.of("jobId", first, "text", "Example Company Demo builds data products and values teamwork.",
                "sources", "https://example.com/about"));
        ok(controller, "company.summarize", Map.of("jobId", first));
        assertEquals(2, model.calls("F16"));
        ok(controller, "document.save", Map.of("confirmed", "yes"));

        // F17: reject invalid input without an API call; correct an over-budget generated plan.
        Map<String, String> plan = Map.of("jobIds", first, "goals", "Improve SQL for an entry-level Java developer role",
                "hours", "5", "budget", "0");
        assertTrue(controller.submitRequest("career.plan", Map.of("jobIds", first, "goals", plan.get("goals"),
                "hours", "0", "budget", "0")).startsWith("ERROR:"));
        assertEquals(0, model.calls("F17"));
        ok(controller, "career.plan", plan);
        assertEquals(2, model.calls("F17"));
        assertTrue(controller.pendingDocumentContent().contains("Allocated: 2 hours; cost 0"));
        ok(controller, "document.save", Map.of("confirmed", "yes"));
        CareerFacade restarted = facade(model);
        assertEquals(Set.of(CareerDocument.Type.RESUME_SUGGESTIONS, CareerDocument.Type.COVER_LETTER,
                        CareerDocument.Type.COMPANY_SUMMARY, CareerDocument.Type.CAREER_PLAN),
                restarted.listDocuments().stream().map(CareerDocument::type).collect(java.util.stream.Collectors.toSet()));
        assertEquals(InterviewSession.Status.FINISHED, restarted.getInterview(session).status());
        assertEquals(75, restarted.getInterview(session).feedback().get(1).score());
        assertEquals(Set.of("Java", "Git", "JUnit"), restarted.getProfile().orElseThrow().getSkills());
    }
}
