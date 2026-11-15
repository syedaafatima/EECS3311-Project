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
import java.util.concurrent.atomic.AtomicInteger;
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

    private String sourceId(Map<?, ?> sources, String excerpt) {
        return sources.entrySet().stream().filter(entry -> excerpt.equals(entry.getValue()))
                .map(entry -> entry.getKey().toString()).findFirst().orElseThrow();
    }

    private String referencedLetter(String context) {
        Map<?, ?> sources = Json.object(Json.object(Json.parse(context)).get("candidateEvidence"));
        return Json.encode(Map.of("paragraphs", List.of(
                Map.of("text", "My Java project experience is relevant to this role.",
                        "evidenceIds", List.of(sourceId(sources, "Experience: Java projects"))),
                Map.of("text", "I would welcome a conversation about this position.",
                        "evidenceIds", List.of(sourceId(sources, "Name: Candidate"))))));
    }

    @Test void coverLetterReferencesAttachOriginalUnicodeAndMultilineResumePassages() {
        String resumeText = "Built a Java task tracker using Git.\r\nUsed JUnit tests with Zoë’s project.";
        AtomicInteger calls = new AtomicInteger();
        var facade = facade((instructions, context) -> {
            calls.incrementAndGet();
            assertTrue(instructions.contains("evidenceIds"));
            Map<?, ?> request = Json.object(Json.parse(context));
            assertEquals("professional", request.get("tone"));
            assertEquals("Java required", Json.object(request.get("job")).get("description"));
            Map<?, ?> sources = Json.object(request.get("candidateEvidence"));
            return Json.encode(Map.of("paragraphs", List.of(
                    Map.of("text", "I built a Java task tracker using Git.",
                            "evidenceIds", List.of(sourceId(sources, "Built a Java task tracker using Git."))),
                    Map.of("text", "I used JUnit tests in the project.",
                            "evidenceIds", List.of(sourceId(sources, "Used JUnit tests with Zoë’s project."))))));
        });
        var resume = facade.importResume(facade.previewResumeText(resumeText));
        var draft = facade.draftLetter(resume.getId(), "J01", "professional");
        assertEquals(1, calls.get());
        assertEquals(List.of("Built a Java task tracker using Git.", "Used JUnit tests with Zoë’s project."), draft.evidence());
        assertTrue(draft.content().endsWith("Sincerely,\nCandidate"));
        assertFalse(draft.approved());
        assertTrue(facade.listDocuments().isEmpty());
        var saved = facade.saveDocument(draft, draft.content());
        assertEquals(draft.evidence(), new CareerFacade(new Repository(data)).getDocument(saved.id()).evidence());
        assertEquals(resumeText, new Repository(data).findResume(resume.getId()).orElseThrow().getText());
    }

    @Test void coverLetterCorrectsAnUnsupportedQuotationOnceBeforeHumanReview() {
        AtomicInteger calls = new AtomicInteger();
        var facade = facade((instructions, context) -> {
            if (calls.incrementAndGet() == 1) {
                return Json.encode(Map.of("paragraphs", List.of(
                        Map.of("text", "I have worked on Java projects.", "evidence", "I completed Java projects"),
                        Map.of("text", "I welcome a conversation.", "evidence", "Name: Candidate"))));
            }
            assertTrue(instructions.contains("previous response failed validation"));
            return referencedLetter(context);
        });
        var resume = facade.importResume(facade.previewResumeText("Java projects"));
        var controller = new AgentController(facade);
        assertTrue(controller.submitRequest("letter.draft", Map.of("resumeId", resume.getId(), "jobId", "J01",
                "tone", "professional")).contains("DRAFT"));
        assertEquals(2, calls.get());
        assertTrue(facade.listDocuments().isEmpty());
        assertTrue(controller.submitRequest("document.save", Map.of("confirmed", "yes")).startsWith("Reviewed document saved:"));
        assertEquals(List.of("Experience: Java projects", "Name: Candidate"), facade.listDocuments().get(0).evidence());
    }

    @Test void unknownCoverLetterSourceIdsCannotFallBackToAQuoteOrOverwriteReviewedDocuments() {
        var valid = facade(this::model);
        var resume = valid.importResume(valid.previewResumeText("Java projects"));
        var saved = valid.saveDocument(valid.draftLetter(resume.getId(), "J01", "warm"), "Reviewed letter");
        AtomicInteger calls = new AtomicInteger();
        var repo = new Repository(data);
        var invalid = new CareerFacade(repo, new ToolManager(repo), new DocumentParserAdapter(), (instructions, context) -> {
            calls.incrementAndGet();
            return Json.encode(Map.of("paragraphs", List.of(
                    Map.of("text", "I have Java project experience.", "evidenceIds", List.of("C999"), "evidence", "Java projects"),
                    Map.of("text", "I welcome a conversation.", "evidenceIds", List.of("J01")))));
        });
        var controller = new AgentController(invalid);
        controller.submitRequest("document.show", Map.of("documentId", saved.id()));
        String error = controller.submitRequest("letter.draft", Map.of("resumeId", resume.getId(), "jobId", "J01"));
        assertTrue(error.contains("unknown candidate source ID"), error);
        assertEquals(2, calls.get());
        assertTrue(controller.submitRequest("document.save", Map.of("confirmed", "yes")).startsWith("ERROR:"));
        assertEquals(1, invalid.listDocuments().size());
        assertEquals("Reviewed letter", invalid.getDocument(saved.id()).content());
    }

    @Test void legacyInventedAndJobOnlyQuotationsRemainRejectedAfterTheCorrectionAttempt() {
        for (String unsupported : List.of("Ten years at Example", "Java required")) {
            AtomicInteger calls = new AtomicInteger();
            var facade = facade((instructions, context) -> {
                calls.incrementAndGet();
                return Json.encode(Map.of("paragraphs", List.of(
                        Map.of("text", "An unsupported claim.", "evidence", unsupported),
                        Map.of("text", "I welcome a conversation.", "evidence", "Name: Candidate"))));
            });
            var resume = facade.importResume(facade.previewResumeText("Java projects"));
            var error = assertThrows(IllegalArgumentException.class, () -> facade.draftLetter(resume.getId(), "J01", "concise"));
            assertTrue(error.getMessage().contains("unsupported source evidence"));
            assertEquals(2, calls.get());
            assertTrue(facade.listDocuments().isEmpty());
        }
    }

    @Test void coverLetterSourcesPreserveLongLinesWithoutBreakingSupplementaryCharacters() {
        String resumeText = "x".repeat(1999) + "\uD83D\uDE80" + "Java projects";
        var facade = facade((instructions, context) -> {
            Map<?, ?> sources = Json.object(Json.object(Json.parse(context)).get("candidateEvidence"));
            for (Object value : sources.values()) {
                String excerpt = value.toString();
                assertTrue(excerpt.length() <= 2000);
                assertFalse(Character.isLowSurrogate(excerpt.charAt(0)));
                assertFalse(Character.isHighSurrogate(excerpt.charAt(excerpt.length() - 1)));
            }
            assertTrue(sources.containsValue("x".repeat(1999)));
            assertTrue(sources.containsValue("\uD83D\uDE80Java projects"));
            return referencedLetter(context);
        });
        var resume = facade.importResume(facade.previewResumeText(resumeText));
        assertNotNull(facade.draftLetter(resume.getId(), "J01", "professional"));
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
        AtomicInteger calls = new AtomicInteger();
        var offline = facade((i, c) -> { calls.incrementAndGet(); throw new IllegalStateException("Provider unavailable"); });
        var controller = new AgentController(offline);
        controller.submitRequest("document.show", Map.of("documentId", saved.id()));
        assertTrue(controller.submitRequest("letter.draft", Map.of("resumeId", resume.getId(), "jobId", "J01", "tone", "warm")).startsWith("ERROR:"));
        assertEquals(1, calls.get());
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
