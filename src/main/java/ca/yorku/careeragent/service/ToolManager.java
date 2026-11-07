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

    public ca.yorku.careeragent.domain.Resume readResume(String resumeId) {
        return repository.findResume(resumeId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown resume: " + resumeId));
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

    public String candidateEvidence(String resumeId) {
        var profile = readProfile();
        return "Name: " + profile.getName() + "\nEducation: " + profile.getEducationLevel() + "\nExperience: " + profile.getExperience()
                + "\nApproved skills: " + profile.getSkills() + "\nResume:\n" + readResume(resumeId).getText();
    }

    public String writingFingerprint(String resumeId, String jobId) {
        Job job = readJob(jobId);
        return fingerprint(candidateEvidence(resumeId) + job.getDescription() + job.getRequiredSkills() + job.getPreferredSkills());
    }

    public static String fingerprint(String source) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(source.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 unavailable."); }
    }

}