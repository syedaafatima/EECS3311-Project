package ca.yorku.careeragent.domain;

import java.util.List;

/** Immutable source snapshot prevents approval after the posting has changed. */
public record JobAnalysis(String jobId, String sourceDescription, List<Requirement> requirements) {
    public JobAnalysis { requirements = List.copyOf(requirements); }
}
