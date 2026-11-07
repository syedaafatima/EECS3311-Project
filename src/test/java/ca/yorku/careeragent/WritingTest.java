package ca.yorku.careeragent;

import ca.yorku.careeragent.controller.AgentController;
import ca.yorku.careeragent.domain.CareerDocument;
import ca.yorku.careeragent.llm.Json;
import ca.yorku.careeragent.llm.LLMClient;
import ca.yorku.careeragent.parser.DocumentParserAdapter;
import ca.yorku.careeragent.persistence.LocalRecords;
import ca.yorku.careeragent.persistence.Repository;
import ca.yorku.careeragent.service.CareerFacade;
import ca.yorku.careeragent.service.ToolManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class WritingTest {
    @TempDir Path data;
    private CareerFacade facade(LLMClient llm) {
        var repo = new Repository(data);
        var facade = new CareerFacade(repo, new ToolManager(repo), new DocumentParserAdapter(), llm);
        facade.updateProfile("Candidate", "University", "Java projects", Set.of("Java"), Set.of(), "Toronto");
        var job = facade.importJob("Developer", "Example", "Java required", "", "Toronto", null);
        facade.saveManualRequirements(job.getId(), Set.of("Java"), Set.of());
        return facade;
    }
    private String model(String instructions, String context) {
        assertTrue(context.contains("Java projects"));
        return instructions.startsWith("F09") ? Json.encode(Map.of("suggestions", List.of(
                Map.of("suggestion", "Emphasize your Java project work.", "evidence", "Java projects"))))
                : Json.encode(Map.of("paragraphs", List.of(
                        Map.of("text", "My Java projects are relevant to this role.", "evidence", "Java projects"),
                        Map.of("text", "I would welcome a conversation about this position.", "evidence", "Name: Candidate"))));
    }
    @Test void reviewAndEditingSaveSeparateDocumentsAcrossRestartWithoutChangingResume() {
        var facade = facade(this::model);
        var resume = facade.importResume(facade.previewResumeText("Candidate: Java projects"));
        var controller = new AgentController(facade);
        assertTrue(controller.submitRequest("resume.suggest", Map.of("resumeId", resume.getId(), "jobId", "J01")).contains("DRAFT"));
        assertTrue(facade.listDocuments().isEmpty());
        assertTrue(controller.submitRequest("document.save", Map.of("confirmed", "no")).contains("cancelled"));
        controller.submitRequest("letter.draft", Map.of("resumeId", resume.getId(), "jobId", "J01", "tone", "professional"));
        assertTrue(controller.pendingDocumentContent().startsWith("Dear Hiring Team,"));
        assertTrue(controller.submitRequest("document.save", Map.of("confirmed", "yes", "content", "My reviewed letter.\nCandidate")).startsWith("Reviewed document saved"));
        var saved = facade.listDocuments().get(0);
        assertEquals(CareerDocument.Type.COVER_LETTER, saved.type());
        assertTrue(saved.approved());
        assertEquals("My reviewed letter.\nCandidate", new CareerFacade(new Repository(data)).getDocument(saved.id()).content());
        assertEquals(resume.getText(), new Repository(data).findResume(resume.getId()).orElseThrow().getText());
        assertTrue(controller.submitRequest("document.save", Map.of("confirmed", "yes")).startsWith("ERROR:"));
    }
    @Test void unsupportedEvidenceMalformedToneAndStaleSourcesCannotBeApproved() {
        var facade = facade((i, c) -> "{\"suggestions\":[{\"suggestion\":\"Invented\",\"evidence\":\"Ten years at Example\"}]}");
        var resume = facade.importResume(facade.previewResumeText("Java projects"));
        assertThrows(IllegalArgumentException.class, () -> facade.suggestResume(resume.getId(), "J01"));
        assertThrows(IllegalArgumentException.class, () -> facade.draftLetter(resume.getId(), "J01", "invent qualifications"));
        var valid = facade(this::model);
        var draft = valid.suggestResume(resume.getId(), "J01");
        valid.updateProfile("Candidate", "University", "Updated experience", Set.of("Java"), Set.of(), "Toronto");
        assertThrows(IllegalArgumentException.class, () -> valid.saveDocument(draft, draft.content()));
        assertTrue(valid.listDocuments().isEmpty());
    }
    @Test void failedGenerationLeavesEarlierReviewedDraftsIntact() {
        var facade = facade(this::model);
        var resume = facade.importResume(facade.previewResumeText("Java projects"));
        var saved = facade.saveDocument(facade.suggestResume(resume.getId(), "J01"), "Reviewed suggestions");
        var offline = facade((i, c) -> { throw new IllegalStateException("Provider unavailable"); });
        var controller = new AgentController(offline);
        controller.submitRequest("document.show", Map.of("documentId", saved.id()));
        assertTrue(controller.submitRequest("letter.draft", Map.of("resumeId", resume.getId(), "jobId", "J01", "tone", "warm")).startsWith("ERROR:"));
        assertTrue(controller.submitRequest("document.save", Map.of("confirmed", "yes")).startsWith("ERROR:"));
        assertEquals("Reviewed suggestions", offline.getDocument(saved.id()).content());
    }
    @Test void recordStoreRejectsTraversalAndFailedWritesDoNotDestroyExistingRecords() throws Exception {
        LocalRecords records = new LocalRecords(data);
        records.put("documents", "D-example", Map.of("id", "D-example", "content", "Zoë \"SQL\"\nJava"));
        assertEquals("Zoë \"SQL\"\nJava", records.get("documents", "D-example").orElseThrow().get("content"));
        assertThrows(IllegalArgumentException.class, () -> records.get("documents", "../../secret"));
        assertThrows(IllegalArgumentException.class, () -> records.put("documents", "D-example", Map.of("id", "different")));
        Files.writeString(data.resolve("interviews"), "blocked directory");
        assertThrows(IllegalStateException.class, () -> records.put("interviews", "S-example", Map.of("id", "S-example")));
        assertEquals(1, records.list("documents").size());
    }

    @Test void interruptedCliDocumentEditingCancelsThePendingReviewWithoutSaving() {
        var facade = facade(this::model);
        var resume = facade.importResume(facade.previewResumeText("Java projects"));
        var controller = new AgentController(facade);
        String commands = "resume suggest\n" + resume.getId() + "\nJ01\nyes\nedit\nIncomplete edit\n";
        new ca.yorku.careeragent.ui.JobSearchCLI(controller, new java.util.Scanner(commands)).run();
        assertTrue(facade.listDocuments().isEmpty());
        assertTrue(controller.submitRequest("document.save", Map.of("confirmed", "yes")).startsWith("ERROR:"));
    }

}
