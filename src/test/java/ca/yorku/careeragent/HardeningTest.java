package ca.yorku.careeragent;

import ca.yorku.careeragent.controller.AgentController;
import ca.yorku.careeragent.domain.Application;
import ca.yorku.careeragent.llm.Json;
import ca.yorku.careeragent.llm.OpenAIAdapter;
import ca.yorku.careeragent.parser.DocumentParserAdapter;
import ca.yorku.careeragent.persistence.Repository;
import ca.yorku.careeragent.service.CareerFacade;
import ca.yorku.careeragent.service.ToolManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class HardeningTest {
    @TempDir Path data;
    @Test void reviewedDocumentsRemainEditableInASeparateJvmWithDifferentCollectionOrdering() throws Exception {
        Repository repo = new Repository(data);
        CareerFacade facade = new CareerFacade(repo, new ToolManager(repo), new DocumentParserAdapter(), (instructions, context) -> {
            if (instructions.startsWith("F09")) return Json.encode(Map.of("suggestions", List.of(Map.of("suggestion", "Explain your Java projects.", "evidence", "Java projects"))));
            return Json.encode(Map.of("summary", "The company builds products.", "citations", List.of("https://example.com/about"), "evidence", List.of("Builds products")));
        });
        facade.updateProfile("Candidate", "University", "Java projects", Set.of("Java", "SQL", "Git", "C++", "Python"), Set.of("Data", "Software", "Research"), "Toronto");
        var resume = facade.importResume(facade.previewResumeText("Java projects using SQL and Git"));
        var job = facade.importJob("Developer", "Example", "Java required. SQL and Git preferred.", "", "Toronto", null);
        facade.saveManualRequirements(job.getId(), Set.of("Java", "C++"), Set.of("SQL", "Git"));
        var writing = facade.saveDocument(facade.suggestResume(resume.getId(), job.getId()), "Reviewed advice");
        facade.saveCompanyNotes(job.getId(), "Builds products", List.of("https://example.com/about"));
        var research = facade.saveDocument(facade.summarizeCompanyNotes(job.getId()), "Reviewed research");
        Path output = data.resolve("restart-probe.txt");
        Process child = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-cp",
                System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")), RestartProbe.class.getName(),
                data.toString(), writing.id(), research.id()).redirectErrorStream(true).redirectOutput(output.toFile()).start();
        assertTrue(child.waitFor(15, TimeUnit.SECONDS));
        assertEquals(0, child.exitValue(), Files.readString(output));
        assertEquals("Reviewed advice", facade.getDocument(writing.id()).content());
    }
    @Test void bracketsQuotesAndLongArrayValuesRoundTripWithoutTruncationOrStackOverflow() {
        Repository repo = new Repository(data);
        String longValue = "x".repeat(100_000) + "]";
        CareerFacade facade = new CareerFacade(repo);
        Set<String> skills = Set.of("C[++]", "SQL \"advanced\"", "Java\\tools", longValue);
        facade.updateProfile("Candidate", "University", "", skills, Set.of("Data [science]"), "Toronto");
        assertEquals(skills, new Repository(data).loadProfile().orElseThrow().getSkills());
        assertEquals(Set.of("Data [science]"), repo.loadProfile().orElseThrow().getInterests());
    }
    @Test void invalidIdsAndFutureDatesReturnUsefulErrorsWithoutCreatingApplications() {
        CareerFacade facade = new CareerFacade(new Repository(data));
        facade.importJob("Developer", "Example", "Java required", "", "", null);
        AgentController controller = new AgentController(facade);
        assertTrue(controller.submitRequest("application.add", Map.of("jobId", "J01", "status", "APPLIED",
                "submissionDate", LocalDate.now(ZoneId.of("America/Toronto")).plusDays(1).toString())).contains("future"));
        assertTrue(controller.submitRequest("job.show", Map.of("jobId", "../../secret")).startsWith("ERROR:"));
        assertTrue(controller.submitRequest("job.import", Map.of()).startsWith("ERROR:"));
        assertTrue(facade.listApplications().isEmpty());
        facade.trackApplication("J01", LocalDate.now(ZoneId.of("America/Toronto")), Application.Status.APPLIED);
        assertEquals(1, facade.listApplications().size());
    }
    @Test void setupStatusNeverEchoesLocalKeyAndConnectivityCheckUsesNoPersonalRecords() throws Exception {
        Path env = Files.writeString(data.resolve(".env"), "OPENAI_API_KEY=private-test-value\n");
        String status = new OpenAIAdapter(env, Map.of(), java.net.URI.create("http://127.0.0.1:1/chat")).configurationStatus();
        assertTrue(status.contains("configured"));
        assertFalse(status.contains("private-test-value"));
        var repo = new Repository(data.resolve("records"));
        CareerFacade facade = new CareerFacade(repo, new ToolManager(repo), new DocumentParserAdapter(), (instructions, context) -> {
            assertTrue(context.contains("No candidate or job data"));
            return "{\"status\":\"ok\"}";
        });
        assertTrue(new AgentController(facade).submitRequest("ai.verify", Map.of()).contains("succeeded"));
    }
}
