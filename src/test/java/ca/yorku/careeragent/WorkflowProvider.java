package ca.yorku.careeragent;

import ca.yorku.careeragent.llm.Json;
import ca.yorku.careeragent.llm.LLMClient;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Test-only provider: exercises source-reference contracts and a failed first response. */
final class WorkflowProvider implements LLMClient {
    private final Map<String, AtomicInteger> calls = new ConcurrentHashMap<>();

    int calls(String feature) { return calls.getOrDefault(feature, new AtomicInteger()).get(); }

    private static String id(Map<?, ?> sources, String excerpt) {
        return sources.entrySet().stream().filter(entry -> entry.getValue().toString().contains(excerpt))
                .map(entry -> entry.getKey().toString()).findFirst().orElseThrow();
    }

    @Override public String complete(String instructions, String context) {
        String feature = instructions.startsWith("Extract only") ? "F03"
                : instructions.startsWith("Analyze the saved") ? "F05" : instructions.substring(0, 3);
        int call = calls.computeIfAbsent(feature, ignored -> new AtomicInteger()).incrementAndGet();
        if (feature.equals("F03")) {
            return Json.encode(Map.of("skills", List.of(Map.of("skill", "JUnit",
                    "evidence", "Used JUnit tests in the Java project."))));
        }
        if (feature.equals("F05")) {
            boolean qa = context.contains("Python");
            return Json.encode(Map.of("requirements", List.of(
                    Map.of("kind", "REQUIRED", "text", qa ? "Python" : "Java", "evidence", context),
                    Map.of("kind", "REQUIRED", "text", qa ? "Git" : "SQL", "evidence", context),
                    Map.of("kind", "PREFERRED", "text", qa ? "SQL" : "Git", "evidence", context))));
        }
        if (feature.equals("F08")) {
            Map<?, ?> facts = Json.object(Json.object(Json.parse(context)).get("verifiedFacts"));
            return Json.encode(Map.of("highlights", facts.keySet().stream().limit(3).toList()));
        }
        if (List.of("F06", "F07").contains(feature)) {
            return Json.encode(Map.of("explanation", "Review the recorded compatibility and skill gaps.",
                    "evidence", List.of(context.lines().reduce((first, next) -> next).orElseThrow())));
        }
        if (feature.equals("F09")) {
            return Json.encode(Map.of("suggestions", List.of(Map.of("suggestion", "Describe the Java project clearly.",
                    "evidence", "Java coursework and a task-tracker project"))));
        }
        Map<?, ?> request = feature.equals("F17") ? Map.of() : Json.object(Json.parse(context));
        if (feature.equals("F10")) {
            if (call == 1) return Json.encode(Map.of("paragraphs", List.of(
                    Map.of("text", "An invalid response.", "evidence", "Unsupported invented qualification"),
                    Map.of("text", "I welcome a conversation.", "evidence", "Name: Demo Candidate"))));
            Map<?, ?> sources = Json.object(request.get("candidateEvidence"));
            return Json.encode(Map.of("paragraphs", List.of(
                    Map.of("text", "My Java coursework and task-tracker project are relevant to this role.",
                            "evidenceIds", List.of(id(sources, "Java coursework and a task-tracker project"))),
                    Map.of("text", "I would welcome a conversation about this role.",
                            "evidenceIds", List.of(id(sources, "Name: Demo Candidate"))))));
        }
        if (feature.equals("F11")) {
            if (call == 1) return "{\"questions\":[";
            Map<?, ?> sources = Json.object(request.get("jobEvidence"));
            String source = id(sources, "Java and SQL are required.");
            return Json.encode(Map.of("questions", List.of(
                    Map.of("kind", "TECHNICAL", "text", "How would you test a Java task tracker?",
                            "rubric", "Explain concrete JUnit test cases.", "evidenceId", source),
                    Map.of("kind", "BEHAVIORAL", "text", "Describe teamwork on a Java project.",
                            "rubric", "Describe the situation, your action and result.", "evidenceId", source))));
        }
        if (feature.equals("F13")) {
            return Json.encode(Map.of("feedback", List.of(Map.of("questionNumber", 1, "score", 75,
                    "strengths", List.of("Mentions Java and JUnit"), "weaknesses", List.of("Needs a concrete example"),
                    "improvements", List.of("Explain an individual test case."),
                    "answerEvidenceId", call == 1 ? "A2_1" : "A1_1"))));
        }
        if (feature.equals("F16")) {
            Map<?, ?> sources = Json.object(request.get("noteEvidence"));
            Map<?, ?> urls = Json.object(request.get("sourceUrls"));
            return Json.encode(Map.of("summary", "The supplied notes describe a collaborative data-products team.",
                    "citationIds", List.of(call == 1 ? "U999" : urls.keySet().iterator().next()),
                    "evidenceIds", List.of(id(sources, "builds data products"))));
        }
        if (feature.equals("F17")) {
            return Json.encode(Map.of("activities", List.of(Map.of("gap", "SQL",
                    "activity", "Practice SQL joins with a local dataset.", "hours", call == 1 ? 6 : 2,
                    "cost", 0, "milestone", "Write and explain three join queries."))));
        }
        throw new AssertionError("Unexpected model request: " + feature);
    }
}
