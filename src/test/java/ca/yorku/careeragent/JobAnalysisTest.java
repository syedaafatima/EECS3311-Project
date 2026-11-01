package ca.yorku.careeragent;

import ca.yorku.careeragent.controller.AgentController;
import ca.yorku.careeragent.domain.Job;
import ca.yorku.careeragent.llm.LLMClient;
import ca.yorku.careeragent.parser.DocumentParserAdapter;
import ca.yorku.careeragent.persistence.Repository;
import ca.yorku.careeragent.service.CareerFacade;
import ca.yorku.careeragent.service.ToolManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class JobAnalysisTest {
    @TempDir Path data;
    private static final String SOURCE = "Java required. SQL preferred. BSc degree. 2 years experience. Strong background.";
    private static final String RESPONSE = """
            {"requirements":[
              {"kind":"REQUIRED","text":"Java","evidence":"Java required."},
              {"kind":"PREFERRED","text":"SQL","evidence":"SQL preferred."},
              {"kind":"QUALIFICATION","text":"BSc degree","evidence":"BSc degree."},
              {"kind":"EXPERIENCE","text":"2 years","evidence":"2 years experience."},
              {"kind":"UNCERTAIN","text":"Strong background","evidence":"Strong background."},
              {"kind":"REQUIRED","text":"Docker","evidence":"Docker required."}]}
            """;

    private CareerFacade facade(LLMClient llm) {
        Repository repo = new Repository(data);
        CareerFacade facade = new CareerFacade(repo, new ToolManager(repo), new DocumentParserAdapter(), llm);
        facade.updateProfile("Candidate", "University", "", Set.of("Java"), Set.of(), "Toronto");
        return facade;
    }

    private Job job(CareerFacade facade) {
        return facade.importJob("Developer", "Example", SOURCE, "", "Toronto", null);
    }

    @Test void reviewsSeparateCategoriesPersistsEvidenceAndUnlocksExistingMatching() {
        CareerFacade facade = facade((instructions, context) -> {
            assertEquals(SOURCE, context);
            assertTrue(instructions.contains("QUALIFICATION"));
            return RESPONSE;
        });
        Job job = job(facade);
        AgentController controller = new AgentController(facade);
        String preview = controller.submitRequest("job.analyze", Map.of("jobId", job.getId()));
        assertTrue(preview.contains("5. UNCERTAIN"));
        assertFalse(preview.contains("Docker"));
        assertFalse(facade.getJob(job.getId()).isAnalyzed());
        assertTrue(controller.submitRequest("job.analysis.approve", Map.of("confirmed", "yes", "selected", "1,2,3,4,5")).startsWith("Reviewed"));
        Job loaded = new Repository(data).findJob(job.getId()).orElseThrow();
        assertEquals(Set.of("Java"), loaded.getRequiredSkills());
        assertEquals(Set.of("SQL"), loaded.getPreferredSkills());
        assertEquals(5, loaded.getAnalysisDetails().size());
        assertEquals("2 years experience.", loaded.getAnalysisDetails().get(3).evidence());
        assertEquals(70.0, facade.matchJobs("weighted").values().iterator().next(), 0.01);
        assertEquals(java.util.List.of("Java"), facade.identifyGaps(job.getId()).get("matched"));
        assertTrue(controller.submitRequest("job.show", Map.of("jobId", job.getId())).contains("Salary: Unknown"));
    }

    @Test void changedPostingRejectsStalePreviewAndInvalidatesEarlierAnalysis() {
        CareerFacade facade = facade((instructions, context) -> RESPONSE);
        Job job = job(facade);
        var preview = facade.analyzeJob(job.getId());
        facade.approveJobAnalysis(preview, Set.of(1));
        // Other fields may change without discarding the analysis.
        facade.importJob("Developer", "Example", SOURCE, "$60000", "Toronto", null);
        assertTrue(facade.getJob(job.getId()).isAnalyzed());
        facade.importJob("Developer", "Example", "Python required.", "", "Toronto", null);
        assertFalse(facade.getJob(job.getId()).isAnalyzed());
        assertTrue(facade.getJob(job.getId()).getAnalysisDetails().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> facade.approveJobAnalysis(preview, Set.of(1)));
    }

    @Test void cancelInvalidSelectionAndProviderFailureKeepReviewedDataIntact() {
        CareerFacade facade = facade((instructions, context) -> RESPONSE);
        Job job = job(facade);
        facade.saveManualRequirements(job.getId(), Set.of("Java"), Set.of());
        AgentController controller = new AgentController(facade);
        controller.submitRequest("job.analyze", Map.of("jobId", job.getId()));
        assertTrue(controller.submitRequest("job.analysis.approve", Map.of("confirmed", "yes", "selected", "99")).startsWith("ERROR:"));
        controller.submitRequest("job.analysis.approve", Map.of("confirmed", "no"));
        assertTrue(controller.submitRequest("job.analysis.approve", Map.of("confirmed", "yes", "selected", "1")).startsWith("ERROR:"));
        assertEquals(Set.of("Java"), facade.getJob(job.getId()).getRequiredSkills());
        CareerFacade offline = facade((instructions, context) -> { throw new IllegalStateException("Unavailable"); });
        assertTrue(new AgentController(offline).submitRequest("job.analyze", Map.of("jobId", job.getId())).startsWith("ERROR:"));
        assertEquals(Set.of("Java"), offline.getJob(job.getId()).getRequiredSkills());
    }

    @Test void malformedCategoryFailsAndManualFallbackWorksWithoutCallingLlm() {
        CareerFacade facade = facade((instructions, context) -> RESPONSE.replace("REQUIRED", "INVENTED"));
        Job job = job(facade);
        assertThrows(IllegalArgumentException.class, () -> facade.analyzeJob(job.getId()));
        AgentController controller = new AgentController(facade);
        assertTrue(controller.submitRequest("job.requirements", Map.of("jobId", job.getId(), "required", "Java", "preferred", "SQL")).startsWith("Manual"));
        assertEquals(70.0, facade.matchJobs("weighted").values().iterator().next(), 0.01);
        assertTrue(controller.submitRequest("job.requirements", Map.of("jobId", job.getId())).startsWith("ERROR:"));
    }
}
