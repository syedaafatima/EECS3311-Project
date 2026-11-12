package ca.yorku.careeragent;

import ca.yorku.careeragent.controller.AgentController;
import ca.yorku.careeragent.deadline.DeadlineObserver;
import ca.yorku.careeragent.deadline.DeadlineReminder;
import ca.yorku.careeragent.persistence.Repository;
import ca.yorku.careeragent.service.CareerFacade;
import ca.yorku.careeragent.ui.JobSearchGUI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;
import java.awt.Component;
import java.awt.Container;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** Headless tests drive real Swing forms/buttons on the EDT and verify persistent results. */
class JobSearchGUITest {
    @TempDir Path data;

    private JobSearchGUI gui(AgentController controller) throws Exception {
        AtomicReference<JobSearchGUI> view = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> view.set(new JobSearchGUI(controller)));
        return view.get();
    }

    private static Component named(Container parent, String name) {
        for (Component component : parent.getComponents()) {
            if (name.equals(component.getName())) return component;
            if (component instanceof Container child) {
                Component found = named(child, name);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static void fill(JobSearchGUI gui, Map<String, String> values) throws Exception {
        SwingUtilities.invokeAndWait(() -> values.forEach((name, value) ->
                ((JTextComponent) named(gui, name)).setText(value)));
    }

    private static void select(JobSearchGUI view, String name, String id) {
        JComboBox<?> choices = (JComboBox<?>) named(view, name);
        for (int i = 0; i < choices.getItemCount(); i++) {
            if (choices.getItemAt(i).toString().startsWith(id + " |")) {
                choices.setSelectedIndex(i);
                return;
            }
        }
        fail("Missing record " + id + " in " + name);
    }

    private static void selected(JobSearchGUI view, String name, String id) {
        Object value = ((JComboBox<?>) named(view, name)).getSelectedItem();
        assertNotNull(value, name);
        assertTrue(value.toString().startsWith(id + " |"), "Expected " + id + " selected in " + name + ", got " + value);
    }

    private static void clickAndAwait(JobSearchGUI gui, String button, String output, String expected) throws Exception {
        CountDownLatch changed = new CountDownLatch(1);
        AtomicReference<JTextComponent> text = new AtomicReference<>();
        DocumentListener listener = new DocumentListener() {
            private void check() { if (text.get().getText().contains(expected)) changed.countDown(); }
            @Override public void insertUpdate(DocumentEvent e) { check(); }
            @Override public void removeUpdate(DocumentEvent e) { check(); }
            @Override public void changedUpdate(DocumentEvent e) { check(); }
        };
        SwingUtilities.invokeAndWait(() -> {
            text.set((JTextComponent) named(gui, output));
            text.get().getDocument().addDocumentListener(listener);
            ((JButton) named(gui, button)).doClick();
        });
        assertTrue(changed.await(8, TimeUnit.SECONDS), "Expected " + expected + " in " + output);
        SwingUtilities.invokeAndWait(() -> text.get().getDocument().removeDocumentListener(listener));
    }

    @Test void formsValidateSaveAndShareProfileJobsApplicationsAndDeadlinesWithCliController() throws Exception {
        Repository repo = new Repository(data);
        AgentController controller = new AgentController(new CareerFacade(repo));
        JobSearchGUI view = gui(controller);
        try {
            clickAndAwait(view, "profile.save", "profile.result", "ERROR:");
            assertTrue(repo.loadProfile().isEmpty());
            fill(view, Map.of("profile.name", "Candidate", "profile.education", "University", "profile.skills", "Java"));
            clickAndAwait(view, "profile.save", "profile.result", "Profile saved");
            fill(view, Map.of("job.title", "Developer", "job.company", "Example", "job.description", "Java required",
                    "job.deadline", LocalDate.now(ZoneId.of("America/Toronto")).plusDays(2).toString()));
            clickAndAwait(view, "job.save", "job.result", "Job saved");
            assertEquals(1, repo.listJobs().size());
            clickAndAwait(view, "application.save", "application.result", "Application saved");
            assertEquals(1, repo.listApplications().size());
            clickAndAwait(view, "deadline.list", "application.result", "UPCOMING");
            AgentController cliController = new AgentController(new CareerFacade(new Repository(data)));
            assertTrue(cliController.submitRequest("profile.show", Map.of()).contains("Candidate"));
            assertTrue(cliController.submitRequest("application.list", Map.of()).contains("SAVED"));
        } finally { SwingUtilities.invokeAndWait(view::close); }
    }

    @Test void guiDisplaysReviewedRequirementsGapsAndBothMatchingPolicies() throws Exception {
        CareerFacade facade = new CareerFacade(new Repository(data));
        facade.updateProfile("Candidate", "University", "", java.util.Set.of("Java"), java.util.Set.of(), "Toronto");
        var job = facade.importJob("Developer", "Example", "Java and SQL", "", "Toronto", null);
        facade.saveManualRequirements(job.getId(), java.util.Set.of("Java"), java.util.Set.of("SQL"));
        JobSearchGUI view = gui(new AgentController(facade));
        try {
            clickAndAwait(view, "job.show", "job.result", "Required skills:");
            clickAndAwait(view, "job.gaps", "job.result", "Matched: [Java]");
            clickAndAwait(view, "job.match", "job.result", "70.0%");
            SwingUtilities.invokeAndWait(() -> ((JComboBox<?>) named(view, "job.strategy")).setSelectedItem("required"));
            clickAndAwait(view, "job.match", "job.result", "100.0%");
            assertEquals(java.util.Set.of("Java"), facade.getProfile().orElseThrow().getSkills());
        } finally { SwingUtilities.invokeAndWait(view::close); }
    }

    @Test void slowRequestsKeepEventThreadResponsiveAndPreventOverlappingWrites() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AgentController slow = new AgentController(new CareerFacade(new Repository(data))) {
            @Override public String submitRequest(String action, Map<String, String> input) {
                if (action.equals("job.match")) {
                    entered.countDown();
                    try { release.await(5, TimeUnit.SECONDS); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                    return "No analyzed jobs";
                }
                return super.submitRequest(action, input);
            }
        };
        JobSearchGUI view = gui(slow);
        try {
            SwingUtilities.invokeAndWait(() -> ((JButton) named(view, "job.match")).doClick());
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            CountDownLatch responsive = new CountDownLatch(1);
            SwingUtilities.invokeLater(() -> {
                assertFalse(named(view, "profile.save").isEnabled());
                responsive.countDown();
            });
            assertTrue(responsive.await(2, TimeUnit.SECONDS), "The event thread must remain responsive");
        } finally { release.countDown(); SwingUtilities.invokeAndWait(view::close); }
    }

    @Test void observerRegistersOnStartAndStopsUpdatingAfterWindowClose() throws Exception {
        AtomicReference<DeadlineObserver> observer = new AtomicReference<>();
        AgentController controller = new AgentController(new CareerFacade(new Repository(data))) {
            @Override public void subscribeDeadlines(DeadlineObserver value) { observer.set(value); }
            @Override public void unsubscribeDeadlines(DeadlineObserver value) { observer.compareAndSet(value, null); }
        };
        JobSearchGUI view = gui(controller);
        SwingUtilities.invokeAndWait(view::start);
        assertSame(view, observer.get());
        var reminder = new DeadlineReminder("J01", "Developer", "Example", LocalDate.of(2026, 11, 1),
                DeadlineReminder.State.TODAY, 0, ZoneId.of("America/Toronto"));
        view.onDeadline(reminder);
        SwingUtilities.invokeAndWait(() -> assertTrue(((JTextArea) named(view, "deadline.reminders")).getText().contains("J01")));
        SwingUtilities.invokeAndWait(view::close);
        assertNull(observer.get());
        view.onDeadline(new DeadlineReminder("J02", "Other", "Example", null, DeadlineReminder.State.UNKNOWN, 0,
                ZoneId.of("America/Toronto")));
        SwingUtilities.invokeAndWait(() -> assertFalse(((JTextArea) named(view, "deadline.reminders")).getText().contains("J02")));
    }

    @Test void interviewAndResearchFormsInvokeTheSharedPersistenceWorkflows() throws Exception {
        Repository repo = new Repository(data);
        CareerFacade facade = new CareerFacade(repo, new ca.yorku.careeragent.service.ToolManager(repo),
                new ca.yorku.careeragent.parser.DocumentParserAdapter(), (i, c) -> ca.yorku.careeragent.llm.Json.encode(Map.of("questions", java.util.List.of(
                        Map.of("kind", "TECHNICAL", "text", "Explain Java memory management", "rubric", "Explain garbage collection", "evidence", "Java required"),
                        Map.of("kind", "BEHAVIOURAL", "text", "Describe Java teamwork", "rubric", "Describe actions and results", "evidence", "Java required")))));
        var job = facade.importJob("Developer", "Example", "Java required", "", "Toronto", null);
        facade.saveManualRequirements(job.getId(), java.util.Set.of("Java"), java.util.Set.of());
        facade.generateQuestions(job.getId(), 2);
        JobSearchGUI view = gui(new AgentController(facade));
        try {
            clickAndAwait(view, "interview.start", "interview.result", "Next question: 1");
            fill(view, Map.of("interview.answerText", "Java uses garbage collection"));
            clickAndAwait(view, "interview.answer", "interview.result", "Next question: 2");
            clickAndAwait(view, "interview.pause", "interview.result", "PAUSED");
            clickAndAwait(view, "interview.resume", "interview.result", "IN_PROGRESS");
            clickAndAwait(view, "interview.skip", "interview.result", "[SKIPPED]");
            clickAndAwait(view, "interview.finish", "interview.result", "FINISHED");
            assertEquals(1, facade.listInterviews().get(0).answers().size());
            fill(view, Map.of("research.text", "Builds data products", "research.sources", "https://example.com/about"));
            clickAndAwait(view, "research.save", "research.result", "Builds data products");
            fill(view, Map.of("research.text", ""));
            clickAndAwait(view, "research.load", "research.result", "Provided sources");
            SwingUtilities.invokeAndWait(() -> assertEquals("Builds data products", ((JTextComponent) named(view, "research.text")).getText()));
        } finally { SwingUtilities.invokeAndWait(view::close); }
    }

    @Test void newRecordsAreSelectedAcrossWorkspacesAndRoutineRefreshKeepsExplicitChoices() throws Exception {
        Repository repo = new Repository(data);
        CareerFacade facade = new CareerFacade(repo, new ca.yorku.careeragent.service.ToolManager(repo),
                new ca.yorku.careeragent.parser.DocumentParserAdapter(), (i, c) -> ca.yorku.careeragent.llm.Json.encode(Map.of("questions", java.util.List.of(
                        Map.of("kind", "TECHNICAL", "text", "Explain Java memory management", "rubric", "Explain garbage collection", "evidence", "Java required"),
                        Map.of("kind", "BEHAVIOURAL", "text", "Describe Java teamwork", "rubric", "Describe actions and results", "evidence", "Java required")))));
        facade.updateProfile("Candidate", "University", "Java projects", java.util.Set.of("Java"), java.util.Set.of(), "Toronto");
        var firstJob = facade.importJob("Developer", "Example", "Java required", "", "Toronto", null);
        facade.saveManualRequirements(firstJob.getId(), java.util.Set.of("Java"), java.util.Set.of());
        var firstResume = facade.importResume(facade.previewResumeText("Original Java project resume"));
        var firstQuestions = facade.generateQuestions(firstJob.getId(), 2);
        var firstSession = facade.startInterview(firstQuestions.id());
        AgentController controller = new AgentController(facade);
        JobSearchGUI view = gui(controller);
        try {
            var secondJob = facade.importJob("QA Analyst", "Other", "Git required", "", "", null);
            var secondResume = facade.importResume(facade.previewResumeText("Updated Java and Git project resume"));
            var secondQuestions = facade.generateQuestions(firstJob.getId(), 2);
            var secondSession = facade.startInterview(secondQuestions.id());
            SwingUtilities.invokeAndWait(() -> {
                ((JButton) named(view, "job.refresh")).doClick();
                for (String selector : java.util.List.of("job.selected", "application.job", "writing.job", "interview.job", "research.job")) {
                    selected(view, selector, secondJob.getId());
                    select(view, selector, firstJob.getId());
                }
                for (String selector : java.util.List.of("resume.selected", "writing.resume")) {
                    selected(view, selector, secondResume.getId());
                    select(view, selector, firstResume.getId());
                }
                selected(view, "interview.questionSets", secondQuestions.id());
                selected(view, "interview.sessions", secondSession.id());
                select(view, "interview.questionSets", firstQuestions.id());
                select(view, "interview.sessions", firstSession.id());
            });
            controller.submitRequest("resume.paste", Map.of("text", "Cancelled resume preview"));
            assertEquals("Resume import cancelled.", controller.submitRequest("resume.import", Map.of("confirmed", "no")));
            SwingUtilities.invokeAndWait(() -> {
                ((JButton) named(view, "resume.refresh")).doClick();
                for (String selector : java.util.List.of("job.selected", "application.job", "writing.job", "interview.job", "research.job")) {
                    selected(view, selector, firstJob.getId());
                }
                selected(view, "resume.selected", firstResume.getId());
                selected(view, "writing.resume", firstResume.getId());
                selected(view, "interview.questionSets", firstQuestions.id());
                selected(view, "interview.sessions", firstSession.id());
            });
            assertEquals(2, repo.listResumes().size());
        } finally { SwingUtilities.invokeAndWait(view::close); }
    }

    @Test void editingPostingKeepsIdentityApplicationLinksAndCorrectAnalysisInvalidation() throws Exception {
        Repository repo = new Repository(data);
        CareerFacade facade = new CareerFacade(repo);
        var deadline = LocalDate.now(ZoneId.of("America/Toronto")).plusDays(2);
        var first = facade.importJob("Developer", "Example", "Java required", "60000", "Toronto", deadline);
        facade.saveManualRequirements(first.getId(), java.util.Set.of("Java"), java.util.Set.of());
        var other = facade.importJob("QA Analyst", "Other", "Git required", "", "", null);
        var application = facade.trackApplication(first.getId(), LocalDate.now(ZoneId.of("America/Toronto")),
                ca.yorku.careeragent.domain.Application.Status.SAVED);
        JobSearchGUI view = gui(new AgentController(facade));
        try {
            SwingUtilities.invokeAndWait(() -> select(view, "job.selected", first.getId()));
            clickAndAwait(view, "job.edit", "job.result", "Required skills:");
            SwingUtilities.invokeAndWait(() -> {
                assertEquals("Developer", ((JTextComponent) named(view, "job.title")).getText());
                assertEquals("Example", ((JTextComponent) named(view, "job.company")).getText());
                assertEquals("Java required", ((JTextComponent) named(view, "job.description")).getText());
                assertEquals(deadline.toString(), ((JTextComponent) named(view, "job.deadline")).getText());
                assertTrue(((JTextComponent) named(view, "job.editorMode")).getText().contains(first.getId()));
            });
            fill(view, Map.of("job.title", "Senior Developer", "job.company", "Renamed Co", "job.salary", "70000", "job.location", "Remote"));
            clickAndAwait(view, "job.save", "job.result", "Job updated:");
            assertEquals(2, repo.listJobs().size());
            assertEquals("Senior Developer", repo.findJob(first.getId()).orElseThrow().getTitle());
            assertEquals(java.util.Set.of("Java"), repo.findJob(first.getId()).orElseThrow().getRequiredSkills());
            assertEquals(first.getId(), repo.findApplication(application.getId()).orElseThrow().getJobId());
            SwingUtilities.invokeAndWait(() -> selected(view, "writing.job", first.getId()));
            fill(view, Map.of("job.title", " QA Analyst ", "job.company", " Other "));
            clickAndAwait(view, "job.save", "job.result", "ERROR:");
            assertEquals("Senior Developer", repo.findJob(first.getId()).orElseThrow().getTitle());
            assertEquals("QA Analyst", repo.findJob(other.getId()).orElseThrow().getTitle());
            assertEquals(2, repo.listJobs().size());
            fill(view, Map.of("job.title", "Senior Developer", "job.company", "Renamed Co", "job.description", "Python required"));
            clickAndAwait(view, "job.save", "job.result", "Job updated:");
            assertFalse(repo.findJob(first.getId()).orElseThrow().isAnalyzed());
            assertEquals(first.getId(), repo.findApplication(application.getId()).orElseThrow().getJobId());
        } finally { SwingUtilities.invokeAndWait(view::close); }
    }

    @Test void newPostingClearsEditModeAndCreatesASeparateRecord() throws Exception {
        Repository repo = new Repository(data);
        CareerFacade facade = new CareerFacade(repo);
        var original = facade.importJob("Developer", "Example", "Java required", "", "Toronto", null);
        JobSearchGUI view = gui(new AgentController(facade));
        try {
            clickAndAwait(view, "job.edit", "job.result", "Required skills:");
            SwingUtilities.invokeAndWait(() -> {
                ((JButton) named(view, "job.new")).doClick();
                for (String field : java.util.List.of("title", "company", "description", "salary", "location", "deadline")) {
                    assertEquals("", ((JTextComponent) named(view, "job." + field)).getText());
                }
            });
            fill(view, Map.of("job.title", "QA Analyst", "job.company", "Other", "job.description", "Git required"));
            clickAndAwait(view, "job.save", "job.result", "Job saved:");
            assertEquals(2, repo.listJobs().size());
            assertEquals("Developer", repo.findJob(original.getId()).orElseThrow().getTitle());
            var added = repo.findJobByTitleAndCompany("QA Analyst", "Other").orElseThrow();
            assertNotEquals(original.getId(), added.getId());
            SwingUtilities.invokeAndWait(() -> {
                selected(view, "job.selected", added.getId());
                selected(view, "application.job", added.getId());
                ((JButton) named(view, "job.reset")).doClick();
                assertTrue(((JTextComponent) named(view, "job.editorMode")).getText().startsWith("New posting."));
                assertEquals("", ((JTextComponent) named(view, "job.title")).getText());
            });
            assertEquals(2, repo.listJobs().size());
        } finally { SwingUtilities.invokeAndWait(view::close); }
    }

}
