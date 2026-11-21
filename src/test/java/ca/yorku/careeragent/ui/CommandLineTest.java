package ca.yorku.careeragent.ui;

import ca.yorku.careeragent.llm.Json;
import ca.yorku.careeragent.persistence.Repository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CommandLineTest {
    @TempDir Path directory;
    private final ByteArrayOutputStream output = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errors = new ByteArrayOutputStream();

    private int run(String input, String... args) {
        return CommandLine.execute(args, new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)),
                new PrintStream(output, true, StandardCharsets.UTF_8), new PrintStream(errors, true, StandardCharsets.UTF_8));
    }

    private Path task(Object value) throws Exception {
        Path file = directory.resolve("task.json");
        Files.writeString(file, Json.encode(value));
        return file;
    }

    private Map<String, Object> profile(String name) {
        return Map.of("action", "profile.update", "input", Map.of("name", name, "education", "BSc Computer Science",
                "experience", "Java coursework", "skills", "Java, Git", "interests", "Software development", "location", "Toronto"));
    }

    @Test void helpDoesNotOpenDataOrCallAProvider() {
        Path data = directory.resolve("unused");
        assertEquals(0, run("", "--help", "--data-dir", data.toString()));
        assertTrue(output.toString(StandardCharsets.UTF_8).contains("--input -"));
        assertFalse(Files.exists(data));
        assertEquals(0, errors.size());
    }

    @Test void shippedExampleCompletesAndPersistsFromAnEmptyDirectory() {
        Path data = directory.resolve("records");
        assertEquals(0, run("", "--input", "examples/cli-demo.json", "--data-dir", data.toString()));
        Map<?, ?> response = Json.object(Json.parse(output.toString(StandardCharsets.UTF_8)));
        assertEquals(true, response.get("ok"));
        assertEquals(5, Json.array(response.get("results")).size());
        assertTrue(output.toString(StandardCharsets.UTF_8).contains("65.0%"));
        assertTrue(output.toString(StandardCharsets.UTF_8).contains("Missing: [SQL]"));
        Repository repository = new Repository(data);
        assertEquals("Demo Candidate", repository.loadProfile().orElseThrow().getName());
        assertEquals(1, repository.listJobs().size());
        assertEquals(java.util.Set.of("Java", "SQL"), repository.findJob("J01").orElseThrow().getRequiredSkills());
    }

    @Test void standardInputSupportsUtf8BomAndSingleRequests() {
        Path data = directory.resolve("stdin-data");
        assertEquals(0, run("\uFEFF" + Json.encode(profile("Démo Candidate")), "--input", "-", "--data-dir", data.toString()));
        assertTrue(output.toString(StandardCharsets.UTF_8).contains("Démo Candidate"));
        assertEquals("Démo Candidate", new Repository(data).loadProfile().orElseThrow().getName());
    }

    @Test void entireTaskShapeIsValidatedBeforeAnyRecordIsWritten() throws Exception {
        Path data = directory.resolve("not-created");
        Path file = task(Map.of("requests", List.of(profile("First"),
                Map.of("action", "job.show", "input", Map.of("jobId", List.of("invalid"))))));
        assertEquals(2, run("", "-input", file.toString(), "--data-dir", data.toString()));
        assertFalse(Files.exists(data));
        assertTrue(errors.toString(StandardCharsets.UTF_8).contains("must be strings"));
    }

    @Test void firstFailedRequestStopsLaterMutationsAndReturnsJson() throws Exception {
        Path data = directory.resolve("partial");
        Path file = task(Map.of("requests", List.of(profile("First"),
                Map.of("action", "unsupported.request"), profile("Must not be saved"))));
        assertEquals(1, run("", "--input", file.toString(), "--data-dir", data.toString()));
        Map<?, ?> response = Json.object(Json.parse(output.toString(StandardCharsets.UTF_8)));
        assertEquals(false, response.get("ok"));
        assertEquals(2, Json.array(response.get("results")).size());
        assertEquals("First", new Repository(data).loadProfile().orElseThrow().getName());
    }

    @Test void aiRequiresConsentBeforeEarlierBatchActionsCanWrite() throws Exception {
        Path data = directory.resolve("no-consent");
        Path file = task(Map.of("requests", List.of(profile("Not saved"), Map.of("action", "ai.verify"))));
        assertEquals(2, run("", "--input", file.toString(), "--data-dir", data.toString()));
        assertFalse(Files.exists(data));
        assertTrue(errors.toString(StandardCharsets.UTF_8).contains("--allow-ai"));
    }

    @Test void invalidOptionsMissingFilesAndMalformedJsonHaveUsageExitStatus() {
        for (String[] args : List.of(new String[]{"--unknown"}, new String[]{"--input"},
                new String[]{"--input", directory.resolve("missing.json").toString()},
                new String[]{"--input", "-", "--input", "-"}, new String[]{"--input", "-"})) {
            assertEquals(2, run("{broken", args));
        }
        assertEquals(0, output.size());
        assertFalse(errors.toString(StandardCharsets.UTF_8).contains("Exception"));
        assertEquals(2, run("", "gsk_do_not_echo"));
        assertEquals(2, run("{\"action\":\"gsk_do_not_echo\"}", "--input", "-"));
        assertFalse(errors.toString(StandardCharsets.UTF_8).contains("gsk_do_not_echo"));
    }

    @Test void oversizedInputIsRejectedWithoutCreatingRecords() {
        Path data = directory.resolve("too-large");
        assertEquals(2, run("x".repeat(1_048_577), "--input", "-", "--data-dir", data.toString()));
        assertFalse(Files.exists(data));
        assertTrue(errors.toString(StandardCharsets.UTF_8).contains("1 MiB"));
    }
}
