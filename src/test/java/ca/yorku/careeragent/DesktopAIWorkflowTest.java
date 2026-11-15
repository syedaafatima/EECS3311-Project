package ca.yorku.careeragent;

import ca.yorku.careeragent.controller.AgentController;
import ca.yorku.careeragent.parser.DocumentParserAdapter;
import ca.yorku.careeragent.persistence.Repository;
import ca.yorku.careeragent.service.CareerFacade;
import ca.yorku.careeragent.service.ToolManager;
import ca.yorku.careeragent.ui.JobSearchGUI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises actual consent/review dialogs and saved results with an injected, fallible provider. */
@EnabledIfEnvironmentVariable(named = "DISPLAY", matches = ".+")
class DesktopAIWorkflowTest {
    @TempDir Path data;

    private static Component named(Container root, String name) {
        for (Component child : root.getComponents()) {
            if (name.equals(child.getName())) return child;
            if (child instanceof Container container) {
                Component found = named(container, name);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static <T> T component(Container root, Class<T> type) {
        for (Component child : root.getComponents()) {
            if (type.isInstance(child)) return type.cast(child);
            if (child instanceof Container container) {
                T found = component(container, type);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static void invoke(JFrame frame, JobSearchGUI view, String button, String output, String expected,
            boolean review) throws Exception {
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicReference<Timer> timer = new AtomicReference<>();
        AtomicInteger accepted = new AtomicInteger();
        AtomicReference<JTextComponent> result = new AtomicReference<>();
        DocumentListener listener = new DocumentListener() {
            private void check() { if (result.get().getText().contains(expected)) completed.countDown(); }
            @Override public void insertUpdate(DocumentEvent e) { check(); }
            @Override public void removeUpdate(DocumentEvent e) { check(); }
            @Override public void changedUpdate(DocumentEvent e) { check(); }
        };
        SwingUtilities.invokeAndWait(() -> {
            result.set((JTextComponent) named(view, output));
            result.get().getDocument().addDocumentListener(listener);
            Timer automation = new Timer(40, event -> {
                for (Window window : Window.getWindows()) {
                    if (!(window instanceof JDialog dialog) || !dialog.isShowing() || dialog.getOwner() != frame) continue;
                    JOptionPane pane = component(dialog, JOptionPane.class);
                    if (pane == null) continue;
                    try {
                        JTabbedPane tabs = component(dialog, JTabbedPane.class);
                        if (tabs != null) {
                            assertEquals("Edit content", tabs.getTitleAt(0));
                            assertEquals("Source evidence", tabs.getTitleAt(1));
                            assertFalse(component((Container) tabs.getComponentAt(0), JTextArea.class).getText().isBlank());
                            assertTrue(component((Container) tabs.getComponentAt(1), JTextArea.class).getText().contains("Source evidence"));
                        }
                        accepted.incrementAndGet();
                        pane.setValue(JOptionPane.OK_OPTION); // OK and Yes both have value 0.
                    } catch (Throwable error) {
                        failure.set(error);
                        pane.setValue(JOptionPane.CANCEL_OPTION);
                        completed.countDown();
                    }
                }
            });
            timer.set(automation);
            automation.start();
            SwingUtilities.invokeLater(() -> ((JButton) named(view, button)).doClick());
        });
        try {
            assertTrue(completed.await(12, TimeUnit.SECONDS), "Expected " + expected + " from " + button);
            if (failure.get() != null) throw new AssertionError(failure.get());
            assertTrue(accepted.get() >= (review ? 2 : 1), "Consent and required review must be shown");
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                timer.get().stop();
                result.get().getDocument().removeDocumentListener(listener);
                for (Window window : Window.getWindows()) {
                    if (window instanceof JDialog dialog && dialog.getOwner() == frame) dialog.dispose();
                }
            });
        }
    }

    @Test void nativeGenerationAndReviewCompleteForWritingInterviewsResearchAndPlanning() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeFalse(GraphicsEnvironment.isHeadless());
        WorkflowProvider provider = new WorkflowProvider();
        Repository repo = new Repository(data);
        CareerFacade facade = new CareerFacade(repo, new ToolManager(repo), new DocumentParserAdapter(), provider);
        facade.updateProfile("Demo Candidate", "BSc Computer Science", "Java coursework and a task-tracker project",
                Set.of("Java", "Git", "JUnit"), Set.of("Software development"), "Toronto");
        var resume = facade.importResume(facade.previewResume(Path.of("examples", "demo-resume.pdf")));
        var job = facade.importJob("Junior Developer", "Example Company Demo",
                "Java and SQL are required. Git is preferred.", "", "Toronto", null);
        facade.saveManualRequirements(job.getId(), Set.of("Java", "SQL"), Set.of("Git"));
        var advice = facade.suggestResume(resume.getId(), job.getId());
        facade.saveDocument(advice, advice.content());
        var session = facade.startInterview(facade.generateQuestions(job.getId(), 2).id());
        facade.answerInterview(session.id(), "I built a Java task tracker and used JUnit tests.", false);
        facade.answerInterview(session.id(), "", true);
        facade.transitionInterview(session.id(), "finish");
        facade.saveCompanyNotes(job.getId(), "Example Company Demo builds data products and values teamwork.",
                java.util.List.of("https://example.com/about"));
        AtomicReference<JFrame> frame = new AtomicReference<>();
        AtomicReference<JobSearchGUI> view = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            view.set(new JobSearchGUI(new AgentController(facade)));
            JFrame window = new JFrame("AI workflow verification");
            window.setContentPane(view.get());
            window.setSize(1180, 820);
            window.setVisible(true);
            frame.set(window);
            ((JSpinner) named(view.get(), "interview.count")).setValue(2);
            Map.of("plan.jobIds", job.getId(), "plan.goals", "Improve SQL for an entry-level Java developer role")
                    .forEach((name, value) -> ((JTextComponent) named(view.get(), name)).setText(value));
        });
        try {
            SwingUtilities.invokeAndWait(() -> ((JList<?>) named(view.get(), "workspace.navigation")).setSelectedIndex(4));
            invoke(frame.get(), view.get(), "writing.letter.draft", "writing.result", "Reviewed document saved:", true);
            SwingUtilities.invokeAndWait(() -> ((JList<?>) named(view.get(), "workspace.navigation")).setSelectedIndex(5));
            invoke(frame.get(), view.get(), "interview.generate", "interview.result", "2 questions", false);
            invoke(frame.get(), view.get(), "interview.feedback", "interview.result", "AI feedback (review):", false);
            SwingUtilities.invokeAndWait(() -> ((JList<?>) named(view.get(), "workspace.navigation")).setSelectedIndex(6));
            invoke(frame.get(), view.get(), "research.summarize", "research.result", "Reviewed document saved:", true);
            SwingUtilities.invokeAndWait(() -> ((JList<?>) named(view.get(), "workspace.navigation")).setSelectedIndex(7));
            invoke(frame.get(), view.get(), "plan.generate", "plan.result", "Reviewed document saved:", true);
            assertEquals(4, facade.listDocuments().size());
            assertEquals(2, facade.listQuestionSets().size());
            assertEquals(75, facade.getInterview(session.id()).feedback().get(1).score());
            java.nio.file.Files.createDirectories(Path.of("target", "desktop"));
            new Robot().waitForIdle();
            javax.imageio.ImageIO.write(new Robot().createScreenCapture(frame.get().getBounds()), "png",
                    Path.of("target", "desktop", "ai-workflows.png").toFile());
        } finally {
            SwingUtilities.invokeAndWait(() -> { view.get().close(); frame.get().dispose(); });
        }
    }
}
