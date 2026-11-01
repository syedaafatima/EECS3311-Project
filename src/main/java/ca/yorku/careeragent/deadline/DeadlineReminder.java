package ca.yorku.careeragent.deadline;

import java.time.LocalDate;
import java.time.ZoneId;

public record DeadlineReminder(String jobId, String title, String company, LocalDate deadline,
        State state, long daysRemaining, ZoneId timezone) {
    public enum State { OVERDUE, TODAY, UPCOMING, UNKNOWN }

    @Override public String toString() {
        return jobId + " | " + title + " | " + company + " | " + state + " | "
                + (deadline == null ? "Unknown deadline" : deadline + " (" + daysRemaining + " days)")
                + " | " + timezone;
    }
}
