package ca.yorku.careeragent.ui;

import ca.yorku.careeragent.controller.AgentController;
import ca.yorku.careeragent.llm.Json;
import ca.yorku.careeragent.persistence.Repository;
import ca.yorku.careeragent.service.CareerFacade;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

/** Terminal entry point; batch requests use the same validation and services as both views. */
public final class CommandLine {
    private static final int MAX_INPUT_BYTES = 1_048_576;
    private static final Set<String> AI_ACTIONS = Set.of("ai.verify", "resume.extract-skills", "job.analyze",
            "job.gaps.explain", "job.match.explain", "job.compare", "resume.suggest", "letter.draft",
            "interview.questions", "interview.feedback", "company.summarize", "career.plan");

    private record Options(String input, Path data, boolean allowAI, boolean help) { }
    private record Task(String action, Map<String, String> input) { }

    private CommandLine() { }

    public static void main(String[] args) {
        int status = execute(args, System.in, System.out, System.err);
        if (status != 0) System.exit(status);
    }

    /** Returns 0 on success, 1 on a failed request, and 2 on usage/input errors. */
    public static int execute(String[] args, InputStream stdin, PrintStream out, PrintStream err) {
        Options options;
        List<Task> tasks;
        try {
            options = options(args);
            if (options.help()) {
                out.print(help());
                return 0;
            }
            if (options.input() == null) tasks = List.of();
            else {
                String text;
                if (options.input().equals("-")) text = read(stdin);
                else try (InputStream file = Files.newInputStream(Path.of(options.input()))) { text = read(file); }
                tasks = tasks(text);
            }
            if (!options.allowAI() && tasks.stream().anyMatch(t -> AI_ACTIONS.contains(t.action()))) {
                throw new IllegalArgumentException("This task includes AI requests. Add --allow-ai only after reviewing "
                        + "the records that will be sent to the configured provider.");
            }
        } catch (IOException | IllegalArgumentException e) {
            err.println(Json.encode(Map.of("ok", false, "error", "Cannot read options or task input: " + e.getMessage())));
            return 2;
        }

        List<Map<String, Object>> results = new ArrayList<>();
        try {
            AgentController controller = controller(options.data());
            if (options.input() == null) {
                new JobSearchCLI(controller, new Scanner(stdin, StandardCharsets.UTF_8)).run();
                return 0;
            }
            for (Task task : tasks) {
                String output = controller.submitRequest(task.action(), task.input());
                boolean ok = !output.startsWith("ERROR:") && !output.startsWith("Unknown request:");
                results.add(Map.of("action", task.action(), "ok", ok, "output", output));
                if (!ok) {
                    out.println(Json.encode(Map.of("ok", false, "results", results)));
                    return 1;
                }
            }
            out.println(Json.encode(Map.of("ok", true, "results", results)));
            return 0;
        } catch (IllegalArgumentException | IllegalStateException | NullPointerException e) {
            // Invalid request fields must produce a bounded CLI error, never a stack trace.
            out.println(Json.encode(Map.of("ok", false, "results", results,
                    "error", e instanceof NullPointerException ? "A required request input is missing."
                            : java.util.Objects.toString(e.getMessage(), "Request failed."))));
            return 1;
        }
    }

    private static AgentController controller(Path data) {
        return new AgentController(new CareerFacade(new Repository(data)));
    }

    private static Options options(String[] args) {
        String input = null;
        Path data = Path.of("data");
        boolean allowAI = false;
        boolean help = false;
        Set<String> seen = new java.util.HashSet<>();
        for (int i = 0; i < args.length; i++) {
            String option = args[i].equals("-input") ? "--input" : args[i];
            if (!seen.add(option)) throw new IllegalArgumentException("Repeated option: " + option);
            switch (option) {
                case "--help", "-h" -> help = true;
                case "--allow-ai" -> allowAI = true;
                case "--input", "--data-dir" -> {
                    if (++i == args.length || args[i].isBlank() || args[i].startsWith("--")) {
                        throw new IllegalArgumentException("A value is required for " + option + ".");
                    }
                    if (option.equals("--input")) input = args[i];
                    else data = Path.of(args[i]);
                }
                default -> throw new IllegalArgumentException("Unknown option. Use --help for supported arguments.");
            }
        }
        return new Options(input, data, allowAI, help);
    }

    private static String read(InputStream stream) throws IOException {
        byte[] bytes = stream.readNBytes(MAX_INPUT_BYTES + 1);
        if (bytes.length > MAX_INPUT_BYTES) throw new IllegalArgumentException("Task input must be at most 1 MiB.");
        String text = new String(bytes, StandardCharsets.UTF_8);
        return text.startsWith("\uFEFF") ? text.substring(1) : text;
    }

    private static List<Task> tasks(String text) {
        Object decoded;
        try { decoded = Json.parse(text); }
        catch (IllegalArgumentException e) { throw new IllegalArgumentException("Invalid task JSON. Use an action/input object or a requests array."); }
        if (!(decoded instanceof Map<?, ?> root)) throw new IllegalArgumentException("Task input must be a JSON object.");
        List<?> rows;
        if (root.containsKey("requests")) {
            if (root.size() != 1 || !(root.get("requests") instanceof List<?> list)) {
                throw new IllegalArgumentException("The batch object must contain only a requests array.");
            }
            rows = list;
        } else rows = List.of(root);
        if (rows.isEmpty() || rows.size() > 100) throw new IllegalArgumentException("Provide between 1 and 100 requests.");
        List<Task> tasks = new ArrayList<>();
        for (Object row : rows) {
            if (!(row instanceof Map<?, ?> request) || !Set.of("action", "input").containsAll(request.keySet())
                    || !(request.get("action") instanceof String action) || action.length() > 128
                    || !action.matches("[a-z]+(?:[.-][a-z]+)*")) {
                throw new IllegalArgumentException("Each request needs an action string and an optional input object.");
            }
            Object raw = request.containsKey("input") ? request.get("input") : Map.of();
            if (!(raw instanceof Map<?, ?> fields)) throw new IllegalArgumentException("Request input must be an object of string values.");
            Map<String, String> input = new LinkedHashMap<>();
            for (var field : fields.entrySet()) {
                if (!(field.getKey() instanceof String key) || !(field.getValue() instanceof String value)) {
                    throw new IllegalArgumentException("Request input values must be strings, including counts, dates and comma-separated selections.");
                }
                input.put(key, value);
            }
            tasks.add(new Task(action, Map.copyOf(input)));
        }
        return List.copyOf(tasks);
    }

    private static String help() {
        return """
                AI Job Search and Career Agent
                Usage: java -jar target/career-agent.jar [options]
                  (no --input)       Start the interactive CLI; type help for feature commands.
                  --input FILE      Run a UTF-8 JSON task file (-input is an alias).
                  --input -         Read a JSON task from standard input, without prompts.
                  --data-dir DIR    Store/read records in DIR (default: data).
                  --allow-ai        Consent to send relevant saved records for batch AI requests.
                  --help, -h        Show this help without opening data or contacting a provider.
                JSON: {"action":"profile.show","input":{}}
                Batch: {"requests":[{"action":"profile.show","input":{}}]}
                Inputs are strings; a batch runs in order and stops at its first failed request.
                Earlier successful requests remain saved. AI consent does not approve generated content.
                Credentials belong in private .env/environment variables, never in a task file.
                Exit status: 0 success; 1 request/provider error; 2 options/file/JSON error.
                Example: java -jar target/career-agent.jar --input examples/cli-demo.json --data-dir data/cli-demo
                """;
    }
}
