package ca.yorku.careeragent.ui;

import ca.yorku.careeragent.controller.AgentController;
import ca.yorku.careeragent.persistence.Repository;
import ca.yorku.careeragent.service.CareerFacade;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Scanner;

/** Command-line view for the same application workflows used by the GUI. */
public class JobSearchCLI {
    private final AgentController controller;
    private final Scanner scanner;

    public JobSearchCLI(AgentController controller, Scanner scanner) {
        this.controller = controller;
        this.scanner = scanner;
    }

    public void run() {
        System.out.println("AI Job Search and Career Agent - CLI");
        System.out.println("Type 'help' for commands.");
        while (true) {
            System.out.print("career-agent> ");
            if (!scanner.hasNextLine()) break;
            String command = scanner.nextLine().trim();
            try {
                if (command.startsWith("resume import ")) {
                    importResume(command.substring("resume import ".length()).trim());
                    continue;
                }
                switch (command) {
                    case "help" -> printHelp();
                    case "profile edit" -> editProfile();
                    case "profile show" -> print("profile.show", Map.of());
                    case "resume import" -> importResume(ask("PDF/DOCX resume path"));
                    case "resume paste" -> pasteResume();
                    case "resume list" -> print("resume.list", Map.of());
                    case "resume show" -> print("resume.show", Map.of("resumeId", ask("Resume id")));
                    case "job import" -> importJob();
                    case "job list" -> print("job.list", Map.of());
                    case "job gaps" -> print("job.gaps", Map.of("jobId", ask("Job id")));
                    case "job match" -> print("job.match", Map.of("strategy", ask("Strategy [weighted/required]")));
                    case "application add" -> addApplication();
                    case "application update" -> updateApplication();
                    case "application list" -> print("application.list", Map.of());
                    case "exit" -> { return; }
                    case "" -> { }
                    default -> System.out.println("Unknown command. Type 'help'.");
                }
            } catch (NoSuchElementException e) {
                controller.submitRequest("resume.cancel", Map.of());
                System.out.println("Input ended. Current command cancelled.");
                return;
            }
        }
    }

    private void importResume(String path) {
        if (path.length() >= 2 && path.startsWith("\"") && path.endsWith("\"")) {
            path = path.substring(1, path.length() - 1);
        }
        reviewResume("resume.preview", Map.of("path", path));
    }

    private void pasteResume() {
        System.out.println("Paste resume text; finish with a line containing only .");
        StringBuilder text = new StringBuilder();
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            if (line.equals(".")) {
                reviewResume("resume.paste", Map.of("text", text.toString()));
                return;
            }
            text.append(line).append('\n');
        }
        print("resume.cancel", Map.of());
    }

    private void reviewResume(String action, Map<String, String> input) {
        String preview = controller.submitRequest(action, input);
        System.out.println(preview);
        if (preview.startsWith("ERROR:")) return;
        System.out.print("Save this resume? [yes/no]: ");
        String confirmation = scanner.hasNextLine() ? scanner.nextLine().trim() : "no";
        print("resume.import", Map.of("confirmed", confirmation));
    }

    private void editProfile() {
        Map<String, String> input = new LinkedHashMap<>();
        input.put("name", ask("Name"));
        input.put("education", ask("Highest education level"));
        input.put("experience", ask("Experience"));
        input.put("skills", ask("Skills (comma-separated)"));
        input.put("interests", ask("Interests (comma-separated)"));
        input.put("location", ask("Preferred job location"));
        print("profile.update", input);
    }

    private void importJob() {
        Map<String, String> input = new LinkedHashMap<>();
        input.put("title", ask("Job title"));
        input.put("company", ask("Company"));
        input.put("description", ask("Job description"));
        input.put("salary", ask("Salary (optional)"));
        input.put("location", ask("Location"));
        input.put("deadline", ask("Deadline YYYY-MM-DD (optional)"));
        print("job.import", input);
    }

    private void addApplication() {
        Map<String, String> input = new LinkedHashMap<>();
        input.put("jobId", ask("Job id (example J01)"));
        input.put("submissionDate", ask("Submission date YYYY-MM-DD"));
        input.put("status", ask("Status [SAVED/APPLIED/INTERVIEW/OFFER/ACCEPTED/REJECTED/WITHDRAWN]"));
        print("application.add", input);
    }

    private void updateApplication() {
        Map<String, String> input = new LinkedHashMap<>();
        input.put("applicationId", ask("Application id (example A01)"));
        input.put("status", ask("New status"));
        print("application.update", input);
    }

    private void print(String action, Map<String, String> input) {
        System.out.println(controller.submitRequest(action, input));
    }

    private String ask(String label) {
        System.out.print(label + ": ");
        return scanner.nextLine();
    }

    private void printHelp() {
        System.out.println("resume import [path] - preview PDF/DOCX and confirm import");
        System.out.println("resume paste       - paste text from a scanned resume and review");
        System.out.println("resume list        - list saved resume versions");
        System.out.println("resume show        - display a saved resume's text");
        System.out.println("profile edit       - create/update candidate profile");
        System.out.println("profile show       - display saved profile");
        System.out.println("job import         - import a job posting");
        System.out.println("job list           - list imported jobs");
        System.out.println("job gaps           - compare profile skills with a reviewed job analysis");
        System.out.println("job match          - rank reviewed jobs using a Strategy implementation");
        System.out.println("application add    - track a job application");
        System.out.println("application update - update application status");
        System.out.println("application list   - list tracked applications");
        System.out.println("exit               - close the CLI");
    }

    public static void main(String[] args) {
        Repository repository = new Repository(Path.of("data"));
        CareerFacade facade = new CareerFacade(repository);
        AgentController controller = new AgentController(facade);
        new JobSearchCLI(controller, new Scanner(System.in)).run();
    }
}