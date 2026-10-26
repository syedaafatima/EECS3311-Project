package ca.yorku.careeragent.ui;

import ca.yorku.careeragent.controller.AgentController;
import ca.yorku.careeragent.persistence.Repository;
import ca.yorku.careeragent.service.CareerFacade;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
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
            switch (command) {
                case "help" -> printHelp();
                case "profile edit" -> editProfile();
                case "profile show" -> System.out.println(controller.submitRequest("profile.show", Map.of()));
                case "job import" -> importJob();
                case "job list" -> System.out.println(controller.submitRequest("job.list", Map.of()));
                case "exit" -> { return; }
                case "" -> { }
                default -> System.out.println("Unknown command. Type 'help'.");
            }
        }
    }

    private void editProfile() {
        Map<String, String> input = new LinkedHashMap<>();
        input.put("name", ask("Name"));
        input.put("education", ask("Highest education level"));
        input.put("experience", ask("Experience"));
        input.put("skills", ask("Skills (comma-separated)"));
        input.put("interests", ask("Interests (comma-separated)"));
        input.put("location", ask("Preferred job location"));
        System.out.println(controller.submitRequest("profile.update", input));
    }

    private void importJob() {
        Map<String, String> input = new LinkedHashMap<>();
        input.put("title", ask("Job title"));
        input.put("company", ask("Company"));
        input.put("description", ask("Job description"));
        input.put("salary", ask("Salary (optional)"));
        input.put("location", ask("Location"));
        input.put("deadline", ask("Deadline YYYY-MM-DD (optional)"));
        System.out.println(controller.submitRequest("job.import", input));
    }

    private String ask(String label) {
        System.out.print(label + ": ");
        return scanner.nextLine();
    }

    private void printHelp() {
        System.out.println("profile edit  - create/update candidate profile");
        System.out.println("profile show  - display saved profile");
        System.out.println("job import    - import a job posting");
        System.out.println("job list      - list imported jobs");
        System.out.println("exit          - close the CLI");
    }

    public static void main(String[] args) {
        Repository repository = new Repository(Path.of("data"));
        CareerFacade facade = new CareerFacade(repository);
        AgentController controller = new AgentController(facade);
        new JobSearchCLI(controller, new Scanner(System.in)).run();
    }
}