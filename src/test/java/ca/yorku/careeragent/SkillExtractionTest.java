package ca.yorku.careeragent;

import ca.yorku.careeragent.controller.AgentController;
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
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class SkillExtractionTest {
    @TempDir Path data;

    private CareerFacade facade(LLMClient llm) {
        Repository repo = new Repository(data);
        CareerFacade facade = new CareerFacade(repo, new ToolManager(repo), new DocumentParserAdapter(), llm);
        facade.updateProfile("Candidate", "University", "", Set.of("Git"), Set.of(), "Toronto");
        return facade;
    }

    @Test void retrievesSavedSourceRejectsUnsupportedAndPartialTokensAndRequiresReview() {
        CareerFacade facade = facade((instructions, source) -> {
            assertEquals("Built Java and SQL projects; JavaScript frontend.", source);
            return """
                    {"skills":[
                      {"skill":"Java","evidence":"Built Java and SQL projects"},
                      {"skill":"java","evidence":"Built Java and SQL projects"},
                      {"skill":"SQL","evidence":"Built Java and SQL projects"},
                      {"skill":"Docker","evidence":"Docker deployments"},
                      {"skill":"Python","evidence":"Built Java and SQL projects"},
                      {"skill":"Script","evidence":"JavaScript frontend"}]}
                    """;
        });
        var resume = facade.importResume(facade.previewResumeText("Built Java and SQL projects; JavaScript frontend."));
        AgentController controller = new AgentController(facade);
        String preview = controller.submitRequest("resume.extract-skills", Map.of("resumeId", resume.getId()));
        assertTrue(preview.contains("1. Java"));
        assertTrue(preview.contains("2. SQL"));
        assertFalse(preview.contains("Docker"));
        assertFalse(preview.contains("Script"));
        assertEquals(Set.of("Git"), facade.getProfile().orElseThrow().getSkills());
        assertTrue(controller.submitRequest("skills.approve", Map.of("confirmed", "yes", "selected", "3")).startsWith("ERROR:"));
        assertEquals(Set.of("Git"), facade.getProfile().orElseThrow().getSkills());
        assertTrue(controller.submitRequest("skills.approve", Map.of("confirmed", "yes", "selected", "2")).startsWith("Approved"));
        assertEquals(Set.of("Git", "SQL"), new Repository(data).loadProfile().orElseThrow().getSkills());
        assertTrue(controller.submitRequest("skills.approve", Map.of("confirmed", "yes", "selected", "1")).startsWith("ERROR:"));
    }

    @Test void cancellingOrFailingExtractionCannotReuseAnEarlierPreview() {
        AtomicInteger calls = new AtomicInteger();
        CareerFacade facade = facade((instructions, source) -> {
            if (calls.incrementAndGet() == 2) throw new IllegalStateException("Provider unavailable");
            return "{\"skills\":[{\"skill\":\"Java\",\"evidence\":\"Java\"}]}";
        });
        var resume = facade.importResume(facade.previewResumeText("Java"));
        AgentController controller = new AgentController(facade);
        controller.submitRequest("resume.extract-skills", Map.of("resumeId", resume.getId()));
        assertTrue(controller.submitRequest("skills.approve", Map.of("confirmed", "no")).contains("cancelled"));
        assertTrue(controller.submitRequest("skills.approve", Map.of("confirmed", "yes", "selected", "1")).startsWith("ERROR:"));
        assertTrue(controller.submitRequest("resume.extract-skills", Map.of("resumeId", resume.getId())).startsWith("ERROR:"));
        assertTrue(controller.submitRequest("skills.approve", Map.of("confirmed", "yes", "selected", "1")).startsWith("ERROR:"));
        assertEquals(Set.of("Git"), facade.getProfile().orElseThrow().getSkills());
    }

    @Test void missingSourceAndMalformedResponsesLeaveProfileUnchanged() {
        for (String response : new String[]{"not JSON", "{\"skills\":null}", "{\"skills\":[{}]}", "{\"skills\":[]}"}) {
            CareerFacade facade = facade((instructions, source) -> response);
            var resume = facade.importResume(facade.previewResumeText("Java"));
            AgentController controller = new AgentController(facade);
            assertTrue(controller.submitRequest("resume.extract-skills", Map.of("resumeId", "missing")).startsWith("ERROR:"));
            controller.submitRequest("resume.extract-skills", Map.of("resumeId", resume.getId()));
            assertEquals(Set.of("Git"), facade.getProfile().orElseThrow().getSkills());
        }
    }
}
