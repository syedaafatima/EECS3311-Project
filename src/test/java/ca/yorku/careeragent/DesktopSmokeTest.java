package ca.yorku.careeragent;

import ca.yorku.careeragent.controller.AgentController;
import ca.yorku.careeragent.persistence.Repository;
import ca.yorku.careeragent.service.CareerFacade;
import ca.yorku.careeragent.ui.JobSearchGUI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

/** Optional native-window smoke test; DISPLAY must point to an actual desktop/X server. */
@EnabledIfEnvironmentVariable(named = "DISPLAY", matches = ".+")
class DesktopSmokeTest {
    @TempDir Path data;
    private static Component named(Container parent, String name) {
        for (Component component : parent.getComponents()) {
            if (name.equals(component.getName())) return component;
            if (component instanceof Container child) { Component found = named(child, name); if (found != null) return found; }
        }
        return null;
    }
    @Test void actualWindowRendersEveryTabAndCancelsTheNativeFilePicker() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "A desktop JRE and X display are required");
        AtomicReference<JFrame> frameRef = new AtomicReference<>();
        AtomicReference<JobSearchGUI> viewRef = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            CareerFacade facade = new CareerFacade(new Repository(data));
            facade.updateProfile("Demo Candidate", "BSc Computer Science", "Java coursework and a task-tracker project",
                    java.util.Set.of("Java", "Git", "JUnit"), java.util.Set.of("Software development"), "Toronto");
            var job = facade.importJob("Junior Developer", "Example Company",
                    "Java and SQL are required. Git is preferred.", "", "Toronto", null);
            facade.saveManualRequirements(job.getId(), java.util.Set.of("Java", "SQL"), java.util.Set.of("Git"));
            JobSearchGUI view = new JobSearchGUI(new AgentController(facade));
            JFrame frame = new JFrame("Stage 2 desktop verification — sample workspace");
            frame.setContentPane(view); frame.setSize(1180, 820); frame.setVisible(true);
            frameRef.set(frame); viewRef.set(view);
        });
        try {
            SwingUtilities.invokeAndWait(() -> {
                JList<?> navigation = (JList<?>) named(viewRef.get(), "workspace.navigation");
                Container pages = (Container) named(viewRef.get(), "workspace.pages");
                assertEquals(9, navigation.getModel().getSize());
                for (int i = 0; i < navigation.getModel().getSize(); i++) {
                    navigation.setSelectedIndex(i);
                    frameRef.get().validate();
                    assertTrue(pages.getComponent(i).isVisible(), "Navigation must open the selected workspace");
                }
                navigation.setSelectedIndex(1);
            });
            CountDownLatch cancelled = new CountDownLatch(1);
            SwingUtilities.invokeLater(() -> {
                ((JButton) named(viewRef.get(), "resume.browse")).doClick();
                cancelled.countDown();
            });
            // A Swing Timer runs in the file chooser's modal event loop and cancels the actual chooser.
            SwingUtilities.invokeAndWait(() -> {
                Timer timer = new Timer(100, null);
                timer.addActionListener(e -> {
                    for (Window window : Window.getWindows()) if (window instanceof JDialog dialog && dialog.isShowing()) {
                        cancelChooser(dialog); timer.stop();
                    }
                });
                timer.start();
            });
            assertTrue(cancelled.await(8, TimeUnit.SECONDS));
            java.nio.file.Files.createDirectories(Path.of("target", "desktop"));
            new Robot().waitForIdle();
            ImageIO.write(new Robot().createScreenCapture(frameRef.get().getBounds()), "png", Path.of("target", "desktop", "workspace.png").toFile());
            for (Dimension size : java.util.List.of(new Dimension(900, 650), new Dimension(1280, 860))) {
                SwingUtilities.invokeAndWait(() -> {
                    frameRef.get().setSize(size);
                    ((JList<?>) named(viewRef.get(), "workspace.navigation")).setSelectedIndex(2);
                    ((JTabbedPane) named(viewRef.get(), "job.sections")).setSelectedIndex(1);
                    frameRef.get().validate();
                });
                new Robot().waitForIdle();
                SwingUtilities.invokeAndWait(() -> {
                    Component description = named(viewRef.get(), "job.description");
                    assertTrue(description.getHeight() >= 72, "Multiline inputs must stay readable after resizing");
                    Component button = named(viewRef.get(), "job.save");
                    assertTrue(button.getWidth() >= button.getPreferredSize().width,
                            "Posting button label must fit at " + size.width + "px");
                    ((JTabbedPane) named(viewRef.get(), "job.sections")).setSelectedIndex(0);
                    frameRef.get().validate();
                });
                new Robot().waitForIdle();
                SwingUtilities.invokeAndWait(() -> {
                    for (String action : java.util.List.of("job.manual", "job.match.explain")) {
                        Component button = named(viewRef.get(), action);
                        assertTrue(button.getWidth() >= button.getPreferredSize().width,
                                action + " label must fit at " + size.width + "px");
                    }
                });
                ImageIO.write(new Robot().createScreenCapture(frameRef.get().getBounds()), "png",
                        Path.of("target", "desktop", "jobs-" + size.width + ".png").toFile());
            }
            CountDownLatch ranked = new CountDownLatch(1);
            AtomicReference<Timer> rankCheck = new AtomicReference<>();
            SwingUtilities.invokeAndWait(() -> {
                Timer timer = new Timer(50, event -> {
                    if (((JTextArea) named(viewRef.get(), "job.result")).getText().contains("65.0%")) {
                        ((Timer) event.getSource()).stop();
                        ranked.countDown();
                    }
                });
                rankCheck.set(timer);
                timer.start();
                ((JButton) named(viewRef.get(), "job.match")).doClick();
            });
            try { assertTrue(ranked.await(8, TimeUnit.SECONDS), "The visible ranking action must display its result"); }
            finally { SwingUtilities.invokeAndWait(() -> rankCheck.get().stop()); }
            new Robot().waitForIdle();
            ImageIO.write(new Robot().createScreenCapture(frameRef.get().getBounds()), "png",
                    Path.of("target", "desktop", "jobs-ranked.png").toFile());
        } finally { SwingUtilities.invokeAndWait(() -> { viewRef.get().close(); frameRef.get().dispose(); }); }
    }
    private static void cancelChooser(Container parent) {
        for (Component component : parent.getComponents()) {
            if (component instanceof JFileChooser chooser) { chooser.cancelSelection(); return; }
            if (component instanceof Container child) cancelChooser(child);
        }
    }
}
