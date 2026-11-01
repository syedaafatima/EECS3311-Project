package ca.yorku.careeragent.deadline;

/** Implemented by each active view; the subject has no UI dependencies. */
@FunctionalInterface
public interface DeadlineObserver {
    void onDeadline(DeadlineReminder reminder);
}
