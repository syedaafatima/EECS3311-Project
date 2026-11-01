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
}
