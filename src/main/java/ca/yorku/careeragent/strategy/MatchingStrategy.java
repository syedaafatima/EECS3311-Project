package ca.yorku.careeragent.strategy;

import ca.yorku.careeragent.domain.CandidateProfile;
import ca.yorku.careeragent.domain.Job;

/** Strategy contract for interchangeable job-match policies. */
public interface MatchingStrategy {
    double calculateMatch(CandidateProfile profile, Job job);
    String getName();
}