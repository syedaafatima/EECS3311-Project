package ca.yorku.careeragent;

import ca.yorku.careeragent.domain.Application;
import ca.yorku.careeragent.domain.Job;
import ca.yorku.careeragent.persistence.Repository;
import ca.yorku.careeragent.service.CareerFacade;

import java.nio.file.Files;
import java.time.LocalDate;
import java.util.Set;

/** Lightweight verification runner that uses only the Java standard library. */
public class Period1Verification {
    public static void main(String[] args) throws Exception {
        var temp = Files.createTempDirectory("career-agent-period1-");
        Repository repository = new Repository(temp);
        CareerFacade facade = new CareerFacade(repository);

        facade.updateProfile("Test Candidate", "University", "Java project experience",
                Set.of("Java", "SQL", "Git"), Set.of("Software"), "Toronto");
        assert repository.loadProfile().orElseThrow().getSkills().contains("Java");

        Job job = facade.importJob("Junior Developer", "Example Co", "Build Java applications",
                "$60,000", "Toronto", LocalDate.now().plusDays(10));
        job.updateAnalysis(Set.of("Java", "SQL"), Set.of("Docker"));
        repository.save(job);

        var gaps = facade.identifyGaps(job.getId());
        assert gaps.get("matched").containsAll(Set.of("Java", "SQL"));
        assert gaps.get("missing").isEmpty();

        double score = facade.matchJobs("weighted").values().iterator().next();
        assert score >= 69.9 && score <= 70.1 : "Expected 70% weighted score, got " + score;

        Application app = facade.trackApplication(job.getId(), LocalDate.now(), Application.Status.APPLIED);
        facade.updateApplicationStatus(app.getId(), Application.Status.INTERVIEW);
        assert repository.findApplication(app.getId()).orElseThrow().getStatus() == Application.Status.INTERVIEW;

        System.out.println("Period 1 verification passed.");
    }
}