package ca.yorku.careeragent.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Stores an application, its current status, dates, and status history. */
public class Application {
    public enum Status {
        SAVED, APPLIED, INTERVIEW, OFFER, ACCEPTED, REJECTED, WITHDRAWN
    }

    private static final Map<Status, Set<Status>> ALLOWED_TRANSITIONS = Map.of(
            Status.SAVED, EnumSet.of(Status.APPLIED, Status.WITHDRAWN),
            Status.APPLIED, EnumSet.of(Status.INTERVIEW, Status.REJECTED, Status.WITHDRAWN),
            Status.INTERVIEW, EnumSet.of(Status.OFFER, Status.REJECTED, Status.WITHDRAWN),
            Status.OFFER, EnumSet.of(Status.ACCEPTED, Status.REJECTED, Status.WITHDRAWN),
            Status.ACCEPTED, EnumSet.noneOf(Status.class),
            Status.REJECTED, EnumSet.noneOf(Status.class),
            Status.WITHDRAWN, EnumSet.noneOf(Status.class));

    private final String id;
    private final String jobId;
    private final LocalDate submissionDate;
    private Status status;
    private final List<String> statusHistory = new ArrayList<>();

    public Application(String id, String jobId, LocalDate submissionDate, Status status) {
        this.id = requireText(id, "Application id");
        this.jobId = requireText(jobId, "Job id");
        this.submissionDate = Objects.requireNonNull(submissionDate, "Submission date cannot be null.");
        this.status = Objects.requireNonNull(status, "Status cannot be null.");
        statusHistory.add(historyEntry(status));
    }

    public void updateStatus(Status newStatus) {
        Objects.requireNonNull(newStatus, "Status cannot be null.");
        if (newStatus == status) return;
        if (!ALLOWED_TRANSITIONS.getOrDefault(status, Set.of()).contains(newStatus)) {
            throw new IllegalArgumentException("Invalid application status transition: " + status + " -> " + newStatus);
        }
        status = newStatus;
        statusHistory.add(historyEntry(newStatus));
    }

    public void restoreHistory(List<String> history) {
        statusHistory.clear();
        if (history != null) statusHistory.addAll(history);
        if (statusHistory.isEmpty()) statusHistory.add(historyEntry(status));
    }

    private static String historyEntry(Status status) {
        return LocalDateTime.now().withNano(0) + " | " + status;
    }

    private static String requireText(String value, String fieldName) {
        String cleaned = Objects.requireNonNullElse(value, "").trim();
        if (cleaned.isEmpty()) throw new IllegalArgumentException(fieldName + " cannot be blank.");
        return cleaned;
    }

    public String getId() { return id; }
    public String getJobId() { return jobId; }
    public LocalDate getSubmissionDate() { return submissionDate; }
    public Status getStatus() { return status; }
    public List<String> getStatusHistory() { return List.copyOf(statusHistory); }

    @Override
    public String toString() {
        return id + " | job=" + jobId + " | submitted=" + submissionDate + " | status=" + status;
    }
}
