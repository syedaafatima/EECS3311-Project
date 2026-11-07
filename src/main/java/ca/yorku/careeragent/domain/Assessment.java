package ca.yorku.careeragent.domain;

/** Deterministic facts remain usable even when an optional AI interpretation fails. */
public record Assessment(String feature, String facts, String explanation) {
    @Override public String toString() {
        return feature + System.lineSeparator() + facts + System.lineSeparator() + explanation;
    }
}
