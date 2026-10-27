package ca.yorku.careeragent.strategy;

import ca.yorku.careeragent.domain.CandidateProfile;
import ca.yorku.careeragent.domain.Job;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** Gives required skills 70% of the score and preferred skills 30%. */
public class WeightedMatching implements MatchingStrategy {
    @Override
    public double calculateMatch(CandidateProfile profile, Job job) {
        Set<String> candidate = normalized(profile.getSkills());
        double requiredCoverage = coverage(candidate, normalized(job.getRequiredSkills()));
        double preferredCoverage = coverage(candidate, normalized(job.getPreferredSkills()));

        if (job.getRequiredSkills().isEmpty() && job.getPreferredSkills().isEmpty()) {
            throw new IllegalArgumentException("Job " + job.getId() + " must be analyzed before matching.");
        }
        if (job.getPreferredSkills().isEmpty()) return requiredCoverage * 100.0;
        if (job.getRequiredSkills().isEmpty()) return preferredCoverage * 100.0;
        return (requiredCoverage * 0.70 + preferredCoverage * 0.30) * 100.0;
    }

    @Override
    public String getName() {
        return "weighted";
    }

    private static double coverage(Set<String> candidate, Set<String> requirements) {
        if (requirements.isEmpty()) return 0.0;
        long matched = requirements.stream().filter(candidate::contains).count();
        return (double) matched / requirements.size();
    }

    private static Set<String> normalized(Set<String> values) {
        return values.stream().map(s -> s.trim().toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
    }
}