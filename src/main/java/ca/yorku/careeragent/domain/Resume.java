package ca.yorku.careeragent.domain;

import java.util.Objects;

/** Immutable imported resume version, matching the Stage 1 domain design. */
public final class Resume {
    private final String id;
    private final String originalPath;
    private final String text;
    private final String checksum;

    public Resume(String id, String originalPath, String text, String checksum) {
        if (id == null || !id.matches("R-[0-9a-f-]{36}")) {
            throw new IllegalArgumentException("Invalid resume ID.");
        }
        if (originalPath == null || originalPath.isBlank()) {
            throw new IllegalArgumentException("Resume source is required.");
        }
        if (checksum == null || !checksum.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Resume requires a SHA-256 checksum.");
        }
        this.id = id;
        this.originalPath = originalPath;
        this.text = Objects.requireNonNullElse(text, "").strip();
        this.checksum = checksum;
    }

    public boolean hasText() { return text.codePoints().anyMatch(Character::isLetterOrDigit); }
    public String getId() { return id; }
    public String getOriginalPath() { return originalPath; }
    public String getText() { return text; }
    public String getChecksum() { return checksum; }

    @Override
    public String toString() { return id + " | " + originalPath + System.lineSeparator() + text; }
}
