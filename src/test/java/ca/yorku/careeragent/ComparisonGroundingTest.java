package ca.yorku.careeragent;

import ca.yorku.careeragent.llm.Json;
import ca.yorku.careeragent.llm.LLMClient;
import ca.yorku.careeragent.parser.DocumentParserAdapter;
import ca.yorku.careeragent.persistence.Repository;
import ca.yorku.careeragent.service.CareerFacade;
import ca.yorku.careeragent.service.ToolManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ComparisonGroundingTest {
    @TempDir Path data;

    private CareerFacade facade(LLMClient model, String firstSalary, String secondSalary) {
        Repository repo = new Repository(data);
        CareerFacade facade = new CareerFacade(repo, new ToolManager(repo), new DocumentParserAdapter(), model);
        facade.updateProfile("Candidate", "BSc", "Java project", Set.of("Java", "SQL", "Git"), Set.of(), "Toronto");
        var first = facade.importJob("Junior Developer", "RBC", "Java and SQL required. Git preferred.",
                firstSalary, "Downtown", LocalDate.of(2026, 12, 12));
        var second = facade.importJob("QA Analyst", "TD", "Python and Git required. SQL preferred.",
                secondSalary, "Oakville", LocalDate.of(2026, 11, 30));
        facade.saveManualRequirements(first.getId(), Set.of("Java", "SQL"), Set.of("Git"));
        facade.saveManualRequirements(second.getId(), Set.of("Python", "Git"), Set.of("SQL"));
        return facade;
    }

    private String selectPairs(String instructions, String context) {
        Map<?, ?> facts = Json.object(Json.object(Json.parse(context)).get("verifiedFacts"));
        return Json.encode(Map.of("highlights", facts.keySet().stream().map(Object::toString)
                .filter(id -> id.startsWith("pair_")).limit(3).toList()));
    }

    @Test void wrongSalaryAndLivingCostProseIsRejectedEvenWithARealCitation() {
        AtomicInteger calls = new AtomicInteger();
        CareerFacade facade = facade((instructions, context) -> {
            assertTrue(context.contains("60000"));
            if (calls.incrementAndGet() == 1) {
                // The old excerpt-only validator accepted a true quote with a false conclusion.
                return Json.encode(Map.of("explanation", "J01 offers higher salary; Oakville is more affordable.",
                        "evidence", List.of("60000")));
            }
            return selectPairs(instructions, context);
        }, "60000", "75000");
        var report = facade.compareJobs(List.of("J01", "J02"), "weighted");
        assertEquals(2, calls.get());
        assertTrue(report.facts().contains("J02 has a higher recorded numeric salary (75000) than J01 (60000)."));
        assertTrue(report.explanation().contains("J02 has a higher recorded numeric salary (75000) than J01 (60000)."));
        assertTrue(report.explanation().contains("J01 has higher skill compatibility (100.0%) than J02 (65.0%)"));
        assertTrue(report.explanation().contains("J02 has an earlier recorded deadline (2026-11-30)"));
        assertFalse(report.toString().contains("affordable"));
        assertFalse(report.toString().contains("J01 offers higher salary"));
        assertEquals("60000", facade.getJob("J01").getSalary());
        assertEquals("75000", facade.getJob("J02").getSalary());
    }

    @Test void extraProseUnknownIdsDuplicatesAndMalformedJsonNeverReachTheSummary() {
        List<String> invalid = List.of(
                Json.encode(Map.of("highlights", List.of("pair_J01_J02_salary", "J01_gaps"),
                        "explanation", "Oakville is cheaper and J01 pays more.")),
                Json.encode(Map.of("highlights", List.of("pair_J01_J02_salary", "invented_cost_of_living"))),
                Json.encode(Map.of("highlights", List.of("pair_J01_J02_salary", "pair_J01_J02_salary"))),
                Json.encode(Map.of("highlights", List.of(Map.of("text", "J01 pays more"), "J01_gaps"))),
                "{\"highlights\":[");
        for (String response : invalid) {
            AtomicInteger calls = new AtomicInteger();
            CareerFacade facade = facade((i, c) -> { calls.incrementAndGet(); return response; }, "60000", "75000");
            var report = facade.compareJobs(List.of("J01", "J02"), "weighted");
            assertEquals(2, calls.get());
            assertTrue(report.explanation().contains("unavailable"));
            assertTrue(report.facts().contains("J02 has a higher recorded numeric salary (75000) than J01 (60000)."));
            assertFalse(report.toString().contains("cheaper"));
            assertFalse(report.toString().contains("J01 pays more"));
        }
    }

    @Test void salaryOrderingUsesNumbersAndPreservesUnclearUnitsAcrossSelectionOrder() {
        String[][] cases = {
                {"60000", "75000", "J02 has a higher recorded numeric salary"},
                {"100000", "95000", "J01 has a higher recorded numeric salary"},
                {"60,000.00", "75,000", "J02 has a higher recorded numeric salary"},
                {"75000", "60000", "J01 has a higher recorded numeric salary"},
                {"60000.00", "60000", "have equal recorded numeric salaries"},
                {"0", "1", "J02 has a higher recorded numeric salary"},
                {"", "75000", "Salary order is not established"},
                {"60000-80000", "75000", "Salary order is not established"},
                {"USD 60000", "CAD 75000", "Salary order is not established"},
                {"30/hour", "75000/year", "Salary order is not established"},
                {"60K", "75K", "Salary order is not established"},
                {"-100", "75000", "Salary order is not established"}
        };
        for (String[] entry : cases) {
            CareerFacade facade = facade(this::selectPairs, entry[0], entry[1]);
            for (List<String> ids : List.of(List.of("J01", "J02"), List.of("J02", "J01"))) {
                var report = facade.compareJobs(ids, "weighted");
                assertTrue(report.facts().contains(entry[2]), List.of(entry).toString());
                assertTrue(report.explanation().contains(entry[2]), List.of(entry).toString());
                assertFalse(report.explanation().contains("unavailable"));
            }
        }
    }

    @Test void unreviewedJobsAndMissingFieldsStayUnknown() {
        Repository repo = new Repository(data);
        CareerFacade facade = new CareerFacade(repo, new ToolManager(repo), new DocumentParserAdapter(), (i, c) -> {
            Map<?, ?> facts = Json.object(Json.object(Json.parse(c)).get("verifiedFacts"));
            assertFalse(facts.containsKey("pair_J01_J02_compatibility"));
            assertFalse(facts.containsKey("pair_J01_J02_deadline"));
            return Json.encode(Map.of("highlights", List.of("J02_compatibility", "J02_location", "J02_deadline", "J02_salary")));
        });
        facade.updateProfile("Candidate", "BSc", "", Set.of("Java"), Set.of(), "Toronto");
        facade.importJob("Developer", "First", "Java required", "60000", "Toronto", null);
        facade.importJob("Analyst", "Second", "SQL required", "", "", null);
        facade.saveManualRequirements("J01", Set.of("Java"), Set.of());
        var report = facade.compareJobs(List.of("J01", "J02"), "weighted");
        assertTrue(report.explanation().contains("Not analyzed"));
        assertTrue(report.explanation().contains("J02 recorded location: Unknown"));
        assertTrue(report.explanation().contains("J02 recorded deadline: Unknown"));
        assertTrue(report.explanation().contains("J02 recorded salary: Unknown"));
        assertFalse(report.explanation().contains("0.0%"));
        assertTrue(report.facts().contains("Salary order is not established"));
    }

    @Test void compatibilityHighlightsFollowTheSelectedPolicy() {
        CareerFacade facade = facade(this::selectPairs, "60000", "75000");
        var weighted = facade.compareJobs(List.of("J01", "J02"), "weighted");
        assertTrue(weighted.explanation().contains("J02 (65.0%) under the weighted policy"));
        var required = facade.compareJobs(List.of("J01", "J02"), "required");
        assertTrue(required.explanation().contains("J02 (50.0%) under the required policy"));
        assertFalse(required.explanation().contains("65.0%"));
    }

    @Test void onlySelectedJobsAreSentAndFourJobSalaryPairsRemainConsistent() {
        AtomicInteger requests = new AtomicInteger();
        CareerFacade facade = facade((i, c) -> {
            Map<?, ?> facts = Json.object(Json.object(Json.parse(c)).get("verifiedFacts"));
            if (requests.incrementAndGet() == 1) {
                assertFalse(c.contains("Excluded posting"));
                assertFalse(c.contains("999999"));
                assertTrue(facts.keySet().stream().noneMatch(id -> id.toString().contains("J03")));
            } else {
                assertEquals(6, facts.keySet().stream().filter(id -> id.toString().startsWith("pair_")
                        && id.toString().endsWith("_salary")).count());
            }
            return selectPairs(i, c);
        }, "60000", "75000");
        facade.importJob("Excluded posting", "Third", "Java required", "999999", "", null);
        facade.importJob("Fourth", "Fourth", "Java required", "50000", "", null);
        assertTrue(facade.compareJobs(List.of("J01", "J02"), "weighted").explanation().contains("AI-selected"));
        var four = facade.compareJobs(List.of("J01", "J02", "J03", "J04"), "weighted");
        assertTrue(four.facts().contains("J03 has a higher recorded numeric salary (999999) than J01 (60000)."));
        assertTrue(four.facts().contains("J02 has a higher recorded numeric salary (75000) than J04 (50000)."));
        assertTrue(four.explanation().contains("AI-selected"));
    }

    @Test void providerErrorsAreNotRetriedAndTheRecordedComparisonRemainsAvailable() {
        AtomicInteger calls = new AtomicInteger();
        CareerFacade facade = facade((i, c) -> {
            calls.incrementAndGet();
            throw new IllegalStateException("Provider rate limit");
        }, "60000", "75000");
        var report = facade.compareJobs(List.of("J01", "J02"), "weighted");
        assertEquals(1, calls.get());
        assertTrue(report.explanation().contains("unavailable"));
        assertTrue(report.facts().contains("J02 has a higher recorded numeric salary (75000) than J01 (60000)."));
        assertTrue(report.facts().contains("100.0%"));
        assertTrue(report.facts().contains("65.0%"));
    }
}
