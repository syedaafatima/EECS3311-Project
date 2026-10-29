package ca.yorku.careeragent.parser;

import java.nio.file.Path;

/** Adapter target: application code does not depend on document-library APIs. */
public interface ResumeParser {
    String extractText(Path file);
}
