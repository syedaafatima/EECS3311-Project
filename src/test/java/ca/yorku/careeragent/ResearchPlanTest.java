package ca.yorku.careeragent;

import ca.yorku.careeragent.domain.CareerDocument;
import ca.yorku.careeragent.llm.Json;
import ca.yorku.careeragent.llm.LLMClient;
import ca.yorku.careeragent.parser.DocumentParserAdapter;
import ca.yorku.careeragent.persistence.Repository;
import ca.yorku.careeragent.service.CareerFacade;
import ca.yorku.careeragent.service.ToolManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class ResearchPlanTest {
    @TempDir Path data;
    private CareerFacade facade(LLMClient llm) {
        var repo = new Repository(data);
        var facade = new CareerFacade(repo, new ToolManager(repo), new DocumentParserAdapter(), llm);
        facade.updateProfile("Candidate", "University", "Java projects", Set.of("Java"), Set.of(), "Toronto");
        var job = facade.importJob("Developer", "Example", "Java and SQL required", "", "Toronto", null);
        facade.saveManualRequirements(job.getId(), Set.of("Java", "SQL"), Set.of());
        return facade;
    }
    private String summary() { return Json.encode(Map.of("summary", "Example builds data products.",
            "citations", List.of("https://example.com/about"), "evidence", List.of("Builds data products"))); }
    private String plan(Object hours, Object cost, String gap) { return Json.encode(Map.of("activities", List.of(
            Map.of("gap", gap, "activity", "Practice SQL joins using a local dataset.", "hours", hours, "cost", cost,
                    "milestone", "Write and explain three join queries.")))); }
    @Test void summaryReferencesPreserveOriginalNotesAndSuppliedUrls() {
        String notes = "Builds data products.\nZoë leads collaborative development.";
        String url = "https://example.com/about?topic=Java%20SQL";
        AtomicInteger calls = new AtomicInteger();
        var facade = facade((instructions, context) -> {
            calls.incrementAndGet();
            Map<?, ?> request = Json.object(Json.parse(context));
            assertEquals("Builds data products.", Json.object(request.get("noteEvidence")).get("N1"));
            assertEquals(url, Json.object(request.get("sourceUrls")).get("U1"));
            return Json.encode(Map.of("summary", "The notes describe data products and collaborative development.",
                    "citationIds", List.of("U1"), "evidenceIds", List.of("N1", "N2")));
        });
        facade.saveCompanyNotes("J01", notes, List.of(url));
        var draft = facade.summarizeCompanyNotes("J01");
        assertEquals(1, calls.get());
        assertEquals(List.of("Builds data products.", "Zoë leads collaborative development."), draft.evidence());
        assertTrue(draft.content().contains(url));
        var saved = facade.saveDocument(draft, draft.content());
        assertEquals(draft.evidence(), new CareerFacade(new Repository(data)).getDocument(saved.id()).evidence());
    }
    @Test void unknownSummaryReferencesAndProviderFailuresPreserveReviewedDocuments() {
        var valid = facade((i, c) -> summary());
        valid.saveCompanyNotes("J01", "Builds data products", List.of("https://example.com/about"));
        var saved = valid.saveDocument(valid.summarizeCompanyNotes("J01"), "Reviewed company notes");
        for (String invalidResponse : List.of(
                Json.encode(Map.of("summary", "Invalid source.", "citationIds", List.of("U999"), "evidenceIds", List.of("N1"))),
                Json.encode(Map.of("summary", "Invalid notes.", "citationIds", List.of("U1"), "evidenceIds", List.of("N999"))))) {
            AtomicInteger calls = new AtomicInteger();
            var invalid = facade((i, c) -> { calls.incrementAndGet(); return invalidResponse; });
            assertThrows(IllegalArgumentException.class, () -> invalid.summarizeCompanyNotes("J01"));
            assertEquals(2, calls.get());
            assertEquals("Reviewed company notes", invalid.getDocument(saved.id()).content());
            assertEquals(1, invalid.listDocuments().size());
        }
        AtomicInteger calls = new AtomicInteger();
        var offline = facade((i, c) -> { calls.incrementAndGet(); throw new IllegalStateException("Provider unavailable"); });
        assertThrows(IllegalStateException.class, () -> offline.summarizeCompanyNotes("J01"));
        assertEquals(1, calls.get());
        assertEquals("Reviewed company notes", offline.getDocument(saved.id()).content());
    }
    @Test void malformedOrOverBudgetPlansAreCorrectedOnceWithinTheOriginalLimits() {
        for (String firstResponse : List.of("{\"activities\":[", plan(6, 0, "SQL"))) {
            AtomicInteger calls = new AtomicInteger();
            var facade = facade((instructions, context) -> {
                if (calls.incrementAndGet() == 1) return firstResponse;
                assertTrue(instructions.contains("previous response failed validation"));
                return plan(new BigDecimal("2.5"), 0, "SQL");
            });
            assertThrows(IllegalArgumentException.class, () -> facade.generateCareerPlan(
                    List.of("J01"), "Prepare for a developer role", 0, BigDecimal.ZERO));
            assertEquals(0, calls.get());
            var draft = facade.generateCareerPlan(List.of("J01"), "Prepare for a developer role", 5, BigDecimal.ZERO);
            assertEquals(2, calls.get());
            assertTrue(draft.content().contains("Allocated: 2.5 hours; cost 0"));
            assertTrue(facade.listDocuments().isEmpty());
        }
    }
    @Test void notesSourcesSummaryAndReviewedDocumentSurviveRestart() {
        var facade = facade((i, c) -> summary());
        facade.saveCompanyNotes("J01", "Builds data products", List.of("https://example.com/about"));
        var draft = facade.summarizeCompanyNotes("J01");
        assertEquals(CareerDocument.Type.COMPANY_SUMMARY, draft.type());
        assertTrue(facade.listDocuments().isEmpty());
        var saved = facade.saveDocument(draft, draft.content());
        var restarted = new CareerFacade(new Repository(data));
        assertEquals(List.of("https://example.com/about"), restarted.getCompanyNotes("J01").sources());
        assertTrue(restarted.getDocument(saved.id()).content().contains("https://example.com/about"));
    }
    @Test void invalidSourcesUnsupportedCitationsAndStaleNotesCannotBeSavedAsSummary() {
        var facade = facade((i, c) -> summary());
        assertThrows(IllegalArgumentException.class, () -> facade.saveCompanyNotes("J01", "Notes", List.of("file:///secret")));
        assertThrows(IllegalArgumentException.class, () -> facade.saveCompanyNotes("J01", "Notes", List.of("https://user:password@example.com")));
        facade.saveCompanyNotes("J01", "Builds data products", List.of("https://example.com/about"));
        var draft = facade.summarizeCompanyNotes("J01");
        facade.saveCompanyNotes("J01", "Changed notes", List.of("https://example.com/about"));
        assertThrows(IllegalArgumentException.class, () -> facade.saveDocument(draft, draft.content()));
        var invalid = facade((i, c) -> summary().replace("https://example.com/about", "https://unknown.example"));
        assertThrows(IllegalArgumentException.class, () -> invalid.summarizeCompanyNotes("J01"));
        assertEquals("Changed notes", invalid.getCompanyNotes("J01").text());
    }
    @Test void careerPlanChecksActualTotalsAndPersistsOnlyAfterReview() {
        var facade = facade((i, c) -> {
            assertTrue(i.startsWith("F17")); assertTrue(c.contains("SQL"));
            return plan(new BigDecimal("2.5"), 0, "SQL");
        });
        var draft = facade.generateCareerPlan(List.of("J01"), "Prepare for a Java developer role", 5, BigDecimal.ZERO);
        assertTrue(draft.content().contains("Allocated: 2.5 hours; cost 0"));
        assertTrue(facade.listDocuments().isEmpty());
        var saved = facade.saveDocument(draft, "My reviewed plan: " + draft.content());
        assertEquals(CareerDocument.Type.CAREER_PLAN, new CareerFacade(new Repository(data)).getDocument(saved.id()).type());
    }
    @Test void overBudgetOverTimeUnknownGapsAndVagueGoalsAreRejected() {
        for (String response : List.of(plan(6, 0, "SQL"), plan(2, 10, "SQL"), plan(2, 0, "Invented skill"), plan(0, 0, "SQL"))) {
            var facade = facade((i, c) -> response);
            assertThrows(IllegalArgumentException.class, () -> facade.generateCareerPlan(List.of("J01"), "Prepare for a developer role", 5, BigDecimal.ZERO));
            assertTrue(facade.listDocuments().isEmpty());
        }
        var facade = facade((i, c) -> plan(1, 0, "SQL"));
        assertThrows(IllegalArgumentException.class, () -> facade.generateCareerPlan(List.of("J01"), "job", 5, BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> facade.generateCareerPlan(List.of("J01"), "Prepare for a developer role", 0, BigDecimal.ZERO));
    }
    @Test void priorReviewedAdviceIsRetrievedAsMemoryAndSourceChangesInvalidatePendingPlan() {
        var facade = facade((i, c) -> {
            assertTrue(c.contains("Prior reviewed resume advice (not candidate facts)"));
            return plan(2, 0, "SQL");
        });
        var advice = CareerDocument.draft(CareerDocument.Type.RESUME_SUGGESTIONS, "J01", "", "Explain your Java project clearly", List.of("Java projects"), "");
        facade.saveDocument(advice, advice.content());
        var plan = facade.generateCareerPlan(List.of("J01"), "Prepare for a developer role", 5, BigDecimal.ZERO);
        facade.updateProfile("Candidate", "University", "Java projects", Set.of("Java", "SQL"), Set.of(), "Toronto");
        assertThrows(IllegalArgumentException.class, () -> facade.saveDocument(plan, plan.content()));
    }
}
