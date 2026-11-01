package ca.yorku.careeragent.domain;

import java.util.Objects;

/** Source-backed job requirement. Uncertain statements never become scored skills. */
public record Requirement(Kind kind, String text, String evidence) {
    public enum Kind { REQUIRED, PREFERRED, QUALIFICATION, EXPERIENCE, UNCERTAIN }

    public Requirement {
        Objects.requireNonNull(kind);
        if (text == null || text.isBlank() || evidence == null || evidence.isBlank()) {
            throw new IllegalArgumentException("Requirement text and evidence are required.");
        }
    }
}
