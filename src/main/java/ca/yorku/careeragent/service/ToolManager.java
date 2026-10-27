package ca.yorku.careeragent.service;

import ca.yorku.careeragent.domain.CandidateProfile;
import ca.yorku.careeragent.domain.Job;
import ca.yorku.careeragent.persistence.Repository;
import ca.yorku.careeragent.strategy.MatchingStrategy;
import ca.yorku.careeragent.strategy.WeightedMatching;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Provides the bounded retrieval and skill-comparison tools from the Stage 1 agent design. */
public class ToolManager {
    private final Repository repository;
    private MatchingStrategy matchingStrategy;

    public ToolManager(Repository repository) {
        this(repository, new WeightedMatching());
    }

    public ToolManager(Repository repository, MatchingStrategy matchingStrategy) {
        this.repository = repository;
        this.matchingStrategy = matchingStrategy;
    }

    public CandidateProfile readProfile() {
        return repository.loadProfile()
                .orElseThrow(() -> new IllegalArgumentException("Candidate profile is required."));
    }

    public Job readJob(String jobId) {
        return repository.findJob(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown job: " + jobId));
    }

    public Map<String, List<String>> compareSkills(String jobId) {
        CandidateProfile profile = readProfile();
        Job job = readJob(jobId);
        if (!job.isAnalyzed()) {
            throw new IllegalArgumentException("Job " + jobId + " must be analyzed before skill comparison.");
        }

        List<String> matched = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        List<String> uncertain = new ArrayList<>();
        List<String> candidate = profile.getSkills().stream().map(ToolManager::normalize).toList();

        for (String requirement : job.getRequiredSkills()) {
            String normalizedRequirement = normalize(requirement);
            if (candidate.contains(normalizedRequirement)) {
                matched.add(requirement);
            } else if (candidate.stream().anyMatch(s -> s.contains(normalizedRequirement)
                    || normalizedRequirement.contains(s))) {
                uncertain.add(requirement);
            } else {
                missing.add(requirement);
            }
        }

        Map<String, List<String>> result = new LinkedHashMap<>();
        result.put("matched", matched);
        result.put("missing", missing);
        result.put("uncertain", uncertain);
        return result;
    }

    public double calculateMatch(String jobId) {
        return matchingStrategy.calculateMatch(readProfile(), readJob(jobId));
    }

    public void setMatchingStrategy(MatchingStrategy matchingStrategy) {
        if (matchingStrategy == null) throw new IllegalArgumentException("Matching strategy cannot be null.");
        this.matchingStrategy = matchingStrategy;
    }

    public String getMatchingStrategyName() {
        return matchingStrategy.getName();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}