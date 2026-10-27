package ca.yorku.careeragent.strategy;

import ca.yorku.careeragent.domain.CandidateProfile;
import ca.yorku.careeragent.domain.Job;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** Scores only required-skill coverage, making required skills the policy focus. */
public class RequiredSkillMatching implements MatchingStrategy {
    @Override
    public double calculateMatch(CandidateProfile profile, Job job) {
        if (job.getRequiredSkills().isEmpty()) {
            throw new IllegalArgumentException("Job " + job.getId() + " has no reviewed required skills yet.");
        }
        Set<String> candidate = normalized(profile.getSkills());
        Set<String> required = normalized(job.getRequiredSkills());
        long matched = required.stream().filter(candidate::contains).count();
        return ((double) matched / required.size()) * 100.0;
    }

    @Override
    public String getName() {
        return "required";
    }

    private static Set<String> normalized(Set<String> values) {
        return values.stream().map(s -> s.trim().toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
    }
}