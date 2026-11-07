package ca.yorku.careeragent.domain;

import ca.yorku.careeragent.llm.Json;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Editable output kept separately from original resumes and research evidence. */
public record CareerDocument(String id, Type type, String jobId, String sourceId, String content,
        List<String> evidence, String sourceFingerprint, boolean approved, String createdAt) {
    public enum Type { RESUME_SUGGESTIONS, COVER_LETTER, COMPANY_SUMMARY, CAREER_PLAN }
    public CareerDocument {
        if (id == null || !id.matches("D-[0-9a-f-]{36}")) throw new IllegalArgumentException("Invalid document ID.");
        if (content == null || content.isBlank() || content.length() > 40_000) throw new IllegalArgumentException("Document content must contain 1–40000 characters.");
        evidence = List.copyOf(evidence);
    }
    public static CareerDocument draft(Type type, String job, String source, String content, List<String> evidence, String fingerprint) {
        return new CareerDocument("D-" + UUID.randomUUID(), type, job, source, content, evidence, fingerprint, false, Instant.now().toString());
    }
    public CareerDocument approve(String editedContent) {
        return new CareerDocument(id, type, jobId, sourceId, editedContent, evidence, sourceFingerprint, true, createdAt);
    }
    public Map<String, Object> toRecord() {
        return Map.of("id", id, "type", type.name(), "jobId", jobId, "sourceId", sourceId, "content", content,
                "evidence", evidence, "sourceFingerprint", sourceFingerprint, "approved", approved, "createdAt", createdAt);
    }
    public static CareerDocument fromRecord(Map<?, ?> record) {
        return new CareerDocument(Json.text(record.get("id")), Type.valueOf(Json.text(record.get("type"))),
                (String) record.get("jobId"), (String) record.get("sourceId"), Json.text(record.get("content")),
                Json.array(record.get("evidence")).stream().map(Json::text).toList(), (String) record.get("sourceFingerprint"),
                Boolean.TRUE.equals(record.get("approved")), Json.text(record.get("createdAt")));
    }
    @Override public String toString() {
        return id + " | " + type + " | job=" + jobId + " | " + (approved ? "REVIEWED" : "DRAFT — NOT SAVED")
                + "\n" + content + "\nSource evidence (review candidate claims against these excerpts):\n" + String.join("\n", evidence);
    }
}
