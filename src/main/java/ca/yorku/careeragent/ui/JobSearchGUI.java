package ca.yorku.careeragent.ui;

import ca.yorku.careeragent.controller.AgentController;
import ca.yorku.careeragent.deadline.DeadlineObserver;
import ca.yorku.careeragent.deadline.DeadlineReminder;
import ca.yorku.careeragent.domain.Application;
import ca.yorku.careeragent.persistence.Repository;
import ca.yorku.careeragent.service.CareerFacade;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

/** Swing view of the shared controller. Slow provider calls run outside the event dispatch thread. */
public final class JobSearchGUI extends JPanel implements DeadlineObserver, AutoCloseable {
    private record Choice(String id, String label) {
        @Override public String toString() { return label; }
    }

    private final AgentController controller;
    private final List<JComboBox<Choice>> jobChoices = new ArrayList<>();
    private final JComboBox<Choice> resumes = new JComboBox<>();
    private final JLabel status = new JLabel("Ready. Local data is shared with the CLI.");
    private final JTextArea reminders = output("deadline.reminders");
    private final Map<String, JTextComponent> profileFields = new LinkedHashMap<>();
    private final Timer deadlineTimer;
    private SwingWorker<String, Void> worker;
    private boolean busy;
    private boolean closed;

    public JobSearchGUI(AgentController controller) {
        super(new BorderLayout(12, 12));
        this.controller = controller;
        setBorder(BorderFactory.createEmptyBorder(16, 20, 12, 20));
        JLabel title = new JLabel("Career Agent");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 25f));
        add(title, BorderLayout.NORTH);
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Skills profile", profileTab());
        tabs.addTab("Resumes", resumeTab());
        tabs.addTab("Jobs & matching", jobsTab());
        tabs.addTab("Applications & deadlines", applicationsTab());
        add(tabs, BorderLayout.CENTER);
        add(status, BorderLayout.SOUTH);
        deadlineTimer = new Timer(60_000, e -> {
            if (!busy) execute("deadlines.check", Map.of(), null, null);
        });
        refreshChoices();
        loadProfileFields();
    }

    public void start() {
        controller.subscribeDeadlines(this);
        deadlineTimer.setInitialDelay(0);
        deadlineTimer.start();
    }

    private JPanel profileTab() {
        JPanel form = form();
        field(form, profileFields, "profile", "name", "Name", false);
        field(form, profileFields, "profile", "education", "Highest education", false);
        field(form, profileFields, "profile", "experience", "Experience", true);
        field(form, profileFields, "profile", "skills", "Approved skills (comma-separated)", false);
        field(form, profileFields, "profile", "interests", "Interests (comma-separated)", false);
        field(form, profileFields, "profile", "location", "Preferred location", false);
        JTextArea result = output("profile.result");
        result.setText("Edit your profile, then save. Approved skills are used for job matching.");
        JPanel actions = actions();
        button(actions, "Save profile", "profile.save", () -> execute("profile.update", values(profileFields), result, null));
        button(actions, "Reload profile", "profile.reload", () -> execute("profile.show", Map.of(), result, s -> loadProfileFields()));
        form.add(actions);
        return split(form, result);
    }

    private void loadProfileFields() {
        controller.getProfile().ifPresent(profile -> {
            profileFields.get("name").setText(profile.getName());
            profileFields.get("education").setText(profile.getEducationLevel());
            profileFields.get("experience").setText(profile.getExperience());
            profileFields.get("skills").setText(String.join(", ", profile.getSkills()));
            profileFields.get("interests").setText(String.join(", ", profile.getInterests()));
            profileFields.get("location").setText(profile.getLocationPreference());
        });
    }

    private JPanel resumeTab() {
        JPanel form = form();
        form.add(new JLabel("Import a PDF/DOCX or paste text from a scanned resume."));
        form.add(new JLabel("You review extracted text before it is saved."));
        JTextArea result = output("resume.result");
        result.setText("Import or select a resume to review its text. Skill extraction requires a local API key.");
        JPanel imports = actions();
        button(imports, "Import PDF / DOCX…", "resume.browse", () -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileFilter(new FileNameExtensionFilter("Resume documents", "pdf", "docx"));
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                previewResume("resume.preview", Map.of("path", chooser.getSelectedFile().toPath().toString()), result);
            }
        });
        button(imports, "Paste resume text…", "resume.paste", () -> {
            JTextArea text = new JTextArea(15, 55);
            text.setLineWrap(true);
            text.setWrapStyleWord(true);
            if (JOptionPane.showConfirmDialog(this, new JScrollPane(text), "Paste resume text",
                    JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
                previewResume("resume.paste", Map.of("text", text.getText()), result);
            }
        });
        form.add(imports);
        form.add(new JLabel("Saved resume versions"));
        resumes.setName("resume.selected");
        form.add(resumes);
        JPanel actions = actions();
        button(actions, "Show selected", "resume.show", () -> withChoice(resumes, id ->
                execute("resume.show", Map.of("resumeId", id), result, null)));
        button(actions, "Extract skills…", "resume.skills", () -> withChoice(resumes, id -> {
            if (!allowAI("the selected resume's text")) return;
            execute("resume.extract-skills", Map.of("resumeId", id), result, response -> {
                if (response.startsWith("Proposed skills")) reviewNumbers(response, "skills.approve", result);
            });
        }));
        button(actions, "Refresh versions", "resume.refresh", this::refreshChoices);
        form.add(actions);
        return split(form, result);
    }

    private void previewResume(String action, Map<String, String> input, JTextArea result) {
        execute(action, input, result, response -> {
            boolean confirmed = JOptionPane.showConfirmDialog(this, scrollPreview(response), "Review resume before saving",
                    JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION;
            execute("resume.import", Map.of("confirmed", confirmed ? "yes" : "no"), result, null);
        });
    }

    private JPanel jobsTab() {
        JPanel form = form();
        Map<String, JTextComponent> fields = new LinkedHashMap<>();
        field(form, fields, "job", "title", "Job title", false);
        field(form, fields, "job", "company", "Company", false);
        field(form, fields, "job", "description", "Job description", true);
        field(form, fields, "job", "salary", "Salary (optional)", false);
        field(form, fields, "job", "location", "Location", false);
        field(form, fields, "job", "deadline", "Deadline YYYY-MM-DD (optional)", false);
        JTextArea result = output("job.result");
        result.setText("Import or select a posting. Review its requirements before comparing skills or ranking jobs.");
        JPanel imports = actions();
        button(imports, "Save / update posting", "job.save", () -> execute("job.import", values(fields), result, null));
        form.add(imports);
        form.add(new JLabel("Same title and company update an existing posting."));
        JComboBox<Choice> jobs = jobSelector(form, "job.selected");
        JPanel analysis = actions();
        button(analysis, "Show posting", "job.show", () -> withChoice(jobs, id -> execute("job.show", Map.of("jobId", id), result, null)));
        button(analysis, "Analyze…", "job.analyze", () -> withChoice(jobs, id -> {
            if (!allowAI("the selected job description")) return;
            execute("job.analyze", Map.of("jobId", id), result, response -> {
                if (response.startsWith("Proposed requirements")) reviewNumbers(response, "job.analysis.approve", result);
            });
        }));
        button(analysis, "Manual requirements…", "job.manual", () -> withChoice(jobs, id -> {
            JTextField required = new JTextField(30);
            JTextField preferred = new JTextField(30);
            JPanel input = form();
            input.add(new JLabel("Required skills (comma-separated)")); input.add(required);
            input.add(new JLabel("Preferred skills (comma-separated)")); input.add(preferred);
            if (JOptionPane.showConfirmDialog(this, input, "Review manual requirements",
                    JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
                execute("job.requirements", Map.of("jobId", id, "required", required.getText(),
                        "preferred", preferred.getText()), result, null);
            }
        }));
        button(analysis, "Skill gaps", "job.gaps", () -> withChoice(jobs, id -> execute("job.gaps", Map.of("jobId", id), result, null)));
        form.add(analysis);
        JPanel matching = actions();
        JComboBox<String> strategy = new JComboBox<>(new String[]{"weighted", "required"});
        strategy.setName("job.strategy");
        matching.add(new JLabel("Matching policy")); matching.add(strategy);
        button(matching, "Rank jobs", "job.match", () -> execute("job.match", Map.of("strategy", (String) strategy.getSelectedItem()), result, null));
        button(matching, "Refresh jobs", "job.refresh", this::refreshChoices);
        form.add(matching);
        return split(form, result);
    }

    private JPanel applicationsTab() {
        JPanel form = form();
        JComboBox<Choice> jobs = jobSelector(form, "application.job");
        JTextField date = new JTextField(java.time.LocalDate.now(java.time.ZoneId.of("America/Toronto")).toString());
        date.setName("application.date");
        form.add(new JLabel("Submission / saved date YYYY-MM-DD")); form.add(date);
        JComboBox<Application.Status> appStatus = new JComboBox<>(Application.Status.values());
        appStatus.setName("application.status");
        form.add(new JLabel("Requested status")); form.add(appStatus);
        JTextArea result = output("application.result");
        result.setText("Track a saved job, update its status, or review outstanding deadlines.");
        JPanel tracking = actions();
        button(tracking, "Track / update selected job", "application.save", () -> withChoice(jobs, id ->
                execute("application.add", Map.of("jobId", id, "submissionDate", date.getText(),
                        "status", appStatus.getSelectedItem().toString()), result, null)));
        button(tracking, "List applications", "application.list", () -> execute("application.list", Map.of(), result, null));
        form.add(tracking);
        JPanel deadlines = actions();
        button(deadlines, "List deadlines", "deadline.list", () -> execute("deadlines.list", Map.of(), result, null));
        button(deadlines, "Check reminders", "deadline.check", () -> execute("deadlines.check", Map.of(), result, null));
        form.add(deadlines);
        form.add(new JLabel("Active reminders · America/Toronto · checked every minute"));
        JScrollPane reminderScroll = new JScrollPane(reminders);
        reminderScroll.setPreferredSize(new Dimension(420, 160));
        form.add(reminderScroll);
        return split(form, result);
    }

    private JComboBox<Choice> jobSelector(JPanel form, String name) {
        JComboBox<Choice> selector = new JComboBox<>();
        selector.setName(name);
        jobChoices.add(selector);
        form.add(new JLabel("Saved job")); form.add(selector);
        return selector;
    }

    private void refreshChoices() {
        var jobs = controller.getJobs();
        for (JComboBox<Choice> selector : jobChoices) {
            String previous = selectedId(selector);
            selector.removeAllItems();
            jobs.forEach(job -> selector.addItem(new Choice(job.getId(), job.toString())));
            restoreSelection(selector, previous);
        }
        String previous = selectedId(resumes);
        resumes.removeAllItems();
        controller.listResumes().forEach(resume -> resumes.addItem(new Choice(resume.getId(), resume.getId() + " | " + resume.getOriginalPath())));
        restoreSelection(resumes, previous);
    }

    private static String selectedId(JComboBox<Choice> choices) {
        return choices.getSelectedItem() instanceof Choice choice ? choice.id() : null;
    }

    private static void restoreSelection(JComboBox<Choice> choices, String id) {
        for (int i = 0; i < choices.getItemCount(); i++) {
            if (choices.getItemAt(i).id().equals(id)) { choices.setSelectedIndex(i); return; }
        }
    }

    private void withChoice(JComboBox<Choice> choices, Consumer<String> action) {
        String id = selectedId(choices);
        if (id == null) { status.setText("Import and select a saved record first."); return; }
        action.accept(id);
    }

    private boolean allowAI(String source) {
        return JOptionPane.showConfirmDialog(this, "Send " + source + " to the configured OpenAI model?\n"
                + "This uses your local API key and may incur provider charges.", "AI request",
                JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION;
    }

    private void reviewNumbers(String preview, String action, JTextArea result) {
        JTextField selected = new JTextField(30);
        JPanel review = new JPanel(new BorderLayout(8, 8));
        review.add(scrollPreview(preview), BorderLayout.CENTER);
        JPanel input = form();
        input.add(new JLabel("Numbers to accept (comma-separated); all other proposals are discarded"));
        input.add(selected);
        review.add(input, BorderLayout.SOUTH);
        boolean confirmed = JOptionPane.showConfirmDialog(this, review, "Review proposals",
                JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION && !selected.getText().isBlank();
        execute(action, Map.of("confirmed", confirmed ? "yes" : "no", "selected", selected.getText()), result, null);
    }

    private static JScrollPane scrollPreview(String value) {
        JTextArea preview = output("preview");
        preview.setText(value);
        preview.setCaretPosition(0);
        JScrollPane scroll = new JScrollPane(preview);
        scroll.setPreferredSize(new Dimension(680, 340));
        return scroll;
    }

    private void execute(String action, Map<String, String> input, JTextArea result, Consumer<String> after) {
        if (busy || closed) return;
        busy = true;
        enableControls(this, false);
        status.setText("Working…");
        worker = new SwingWorker<>() {
            @Override protected String doInBackground() { return controller.submitRequest(action, input); }
            @Override protected void done() {
                if (closed) return;
                busy = false;
                enableControls(JobSearchGUI.this, true);
                try {
                    String response = get();
                    if (result != null) { result.setText(response); result.setCaretPosition(0); }
                    status.setText(response.startsWith("ERROR:") ? response : "Ready.");
                    if (!response.startsWith("ERROR:")) {
                        refreshChoices();
                        if (response.startsWith("Approved skills saved:")) loadProfileFields();
                        if (after != null) after.accept(response);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); status.setText("Request interrupted.");
                } catch (ExecutionException | RuntimeException e) {
                    status.setText("Could not complete the request. Check local data and configuration.");
                }
            }
        };
        worker.execute();
    }

    private static void enableControls(Container parent, boolean enabled) {
        for (Component child : parent.getComponents()) {
            if (child instanceof AbstractButton || child instanceof JComboBox<?> || child instanceof JTextComponent text && text.isEditable()) {
                child.setEnabled(enabled);
            }
            if (child instanceof Container container) enableControls(container, enabled);
        }
    }

    @Override public void onDeadline(DeadlineReminder reminder) {
        SwingUtilities.invokeLater(() -> {
            if (!closed) reminders.append(reminder + System.lineSeparator());
        });
    }

    @Override public void close() {
        closed = true;
        deadlineTimer.stop();
        controller.unsubscribeDeadlines(this);
        if (worker != null) worker.cancel(true);
    }

    private static Map<String, String> values(Map<String, JTextComponent> fields) {
        Map<String, String> values = new LinkedHashMap<>();
        fields.forEach((name, component) -> values.put(name, component.getText()));
        return values;
    }

    private static JPanel form() {
        return new JPanel(new GridBagLayout()) {
            private int row;
            @Override protected void addImpl(Component component, Object constraints, int index) {
                GridBagConstraints cell = new GridBagConstraints();
                cell.gridx = 0;
                cell.gridy = row++;
                cell.weightx = 1;
                cell.fill = GridBagConstraints.HORIZONTAL;
                cell.anchor = GridBagConstraints.NORTHWEST;
                cell.insets = new Insets(0, 0, 6, 0);
                super.addImpl(component, cell, index);
            }
        };
    }

    private static JPanel actions() { return new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6)); }

    private static void field(JPanel form, Map<String, JTextComponent> fields, String prefix,
            String key, String label, boolean multiline) {
        JTextComponent input = multiline ? new JTextArea(4, 30) : new JTextField(30);
        input.setName(prefix + "." + key);
        JLabel caption = new JLabel(label);
        caption.setLabelFor(input);
        form.add(caption);
        fields.put(key, input);
        if (input instanceof JTextArea area) { area.setLineWrap(true); area.setWrapStyleWord(true); }
        form.add(multiline ? new JScrollPane(input) : input);
    }

    private static JTextArea output(String name) {
        JTextArea output = new JTextArea(15, 40);
        output.setEditable(false);
        output.setLineWrap(true);
        output.setWrapStyleWord(true);
        output.setName(name);
        output.setMargin(new Insets(12, 12, 12, 12));
        return output;
    }

    private static void button(JPanel parent, String label, String name, Runnable action) {
        JButton button = new JButton(label);
        button.setName(name);
        button.addActionListener(e -> action.run());
        parent.add(button);
    }

    /** Keep forms within the viewport when the split pane or window is resized. */
    private static final class FormBody extends JPanel implements Scrollable {
        FormBody() { super(new BorderLayout()); }
        @Override public Dimension getPreferredScrollableViewportSize() { return new Dimension(500, 650); }
        @Override public int getScrollableUnitIncrement(Rectangle r, int orientation, int direction) { return 24; }
        @Override public int getScrollableBlockIncrement(Rectangle r, int orientation, int direction) { return 200; }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }

    private static JPanel split(JPanel form, JTextArea output) {
        JPanel left = new FormBody();
        left.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        left.add(form, BorderLayout.NORTH);
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JScrollPane(left), new JScrollPane(output));
        split.setResizeWeight(0.5);
        split.setDividerLocation(530);
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(split);
        return panel;
    }

    public static void main(String[] args) {
        if (GraphicsEnvironment.isHeadless()) {
            System.err.println("The GUI requires a desktop display. Run mvn exec:java for the CLI on this machine.");
            return;
        }
        SwingUtilities.invokeLater(() -> {
            try {
                AgentController controller = new AgentController(new CareerFacade(new Repository(Path.of("data"))));
                JobSearchGUI view = new JobSearchGUI(controller);
                JFrame frame = new JFrame("AI Job Search and Career Agent");
                frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
                frame.addWindowListener(new WindowAdapter() {
                    @Override public void windowClosed(WindowEvent e) { view.close(); }
                });
                frame.setContentPane(view);
                frame.setSize(1180, 820);
                frame.setMinimumSize(new Dimension(900, 650));
                frame.setLocationRelativeTo(null);
                frame.setVisible(true);
                view.start();
            } catch (RuntimeException e) {
                JOptionPane.showMessageDialog(null, "Could not load local data. Check the data directory.",
                        "Startup error", JOptionPane.ERROR_MESSAGE);
            }
        });
    }
}
