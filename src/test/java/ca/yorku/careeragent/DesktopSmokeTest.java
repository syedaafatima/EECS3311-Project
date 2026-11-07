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
            JobSearchGUI view = new JobSearchGUI(new AgentController(new CareerFacade(new Repository(data))));
            JFrame frame = new JFrame("Stage 2 desktop verification — sample workspace");
            frame.setContentPane(view); frame.setSize(1180, 820); frame.setVisible(true);
            frameRef.set(frame); viewRef.set(view);
        });
        try {
            SwingUtilities.invokeAndWait(() -> {
                for (Component component : viewRef.get().getComponents()) if (component instanceof JTabbedPane tabs) {
                    assertEquals(9, tabs.getTabCount());
                    for (int i = 0; i < tabs.getTabCount(); i++) { tabs.setSelectedIndex(i); frameRef.get().validate(); }
                    tabs.setSelectedIndex(1);
                }
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
            ImageIO.write(new Robot().createScreenCapture(frameRef.get().getBounds()), "png", Path.of("target", "desktop", "workspace.png").toFile());
        } finally { SwingUtilities.invokeAndWait(() -> { viewRef.get().close(); frameRef.get().dispose(); }); }
    }
    private static void cancelChooser(Container parent) {
        for (Component component : parent.getComponents()) {
            if (component instanceof JFileChooser chooser) { chooser.cancelSelection(); return; }
            if (component instanceof Container child) cancelChooser(child);
        }
    }
}
